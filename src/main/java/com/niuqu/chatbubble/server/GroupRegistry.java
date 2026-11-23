package com.niuqu.chatbubble.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.niuqu.chatbubble.network.GroupCreatePayload;
import com.niuqu.chatbubble.network.GroupDisbandPayload;
import com.niuqu.chatbubble.network.GroupEventPayload;
import com.niuqu.chatbubble.network.GroupInviteEventPayload;
import com.niuqu.chatbubble.network.GroupInvitePayload;
import com.niuqu.chatbubble.network.GroupInviteRespondPayload;
import com.niuqu.chatbubble.network.GroupKickPayload;
import com.niuqu.chatbubble.network.GroupLeavePayload;
import com.niuqu.chatbubble.network.GroupMessagePayload;
import com.niuqu.chatbubble.network.GroupMessageRelayPayload;
import com.niuqu.chatbubble.network.GroupRenamePayload;
import com.niuqu.chatbubble.network.GroupSyncPayload;
import com.niuqu.chatbubble.network.GroupTransferPayload;
import com.niuqu.chatbubble.network.GroupWire;
import com.niuqu.chatbubble.network.ImageMessagePayload;
import com.niuqu.chatbubble.network.ImageMessageRelayPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端群聊注册表（方案 A：**所有群消息都走服务端中继**）。
 *
 * <p>职责：
 * <ul>
 *   <li>保存「群 = 名字 + 成员表（按入群顺序）」；成员表带名字，离线成员也能显示昵称。</li>
 *   <li>校验每个 C2S 操作（发送者必须在群里；踢人还要「目标不是创建者」），
 *       任何异常只记日志、**绝不影响连接**（调用方全 try/catch）。</li>
 *   <li>把变更以 {@link GroupSyncPayload} 广播给全体在线成员；把消息以
 *       {@link GroupMessageRelayPayload} 转发给全体在线成员（含发送者，客户端按 msgHash 去重）。</li>
 *   <li>玩家登录时把「他所属的全部群」逐个 {@code GroupSync} 补发 —— 客户端不需要自己存成员表。</li>
 * </ul>
 *
 * <p>状态落盘在 <b>&lt;world&gt;/serverconfig/e33chat-groups.json</b>（与本项目其它服务端配置同目录、
 * 同 per-world 语义）。落盘失败只在日志里告警，不影响功能（当次运行内仍然是内存态正确）。
 *
 * <p>已知限制（MVP，见 README）：成员表只在服务端，客户端以服务端下发的快照为准；离线玩家收不到
 * 群消息（无离线补发）；跨服/代理不共享状态；群人数上限 {@link GroupWire#MAX_LIST}。
 *
 * <p><b>第 4 轮：进群邀请（pendingInvites）</b>
 * <ul>
 *   <li>建群 / 添加玩家都**不再直接把对方拉进群**：只把被邀请者记进
 *       {@code pendingInvites}（groupId → 待回应者集合），并给他发
 *       {@link GroupInviteEventPayload#KIND_INVITE}，客户端在「邀请者 ↔ 被邀请者」的私聊里
 *       落一条带「接受 / 拒绝」按钮的消息。</li>
 *   <li>被邀请者回 {@link GroupInviteRespondPayload}：接受才真正进群（广播 GroupSync +
 *       给自己发 INVITED 事件），拒绝只回执给创建者，群不变。</li>
 *   <li>**只有创建者能邀请**（{@link #invite} 里校验 sender == creator），普通成员无邀请权；
 *       违规请求回一条 {@link GroupEventPayload#KIND_INVITE_DENIED} 回执，群与其状态都不动。</li>
 *   <li>pendingInvites 是**纯内存态**：服务端重启即丢（被邀请者拿不到按钮响应 = 邀请失效），
 *       这是 MVP 可接受的取舍（需求明确允许）；被邀请者离线时直接忽略，不排离线队列。</li>
 * </ul>
 */
public final class GroupRegistry {
    private GroupRegistry() {}

    /** 群名默认显示值由客户端渲染，这里存空串表示「未命名」。 */
    public static final int MAX_MEMBERS = GroupWire.MAX_LIST;
    public static final int MAX_GROUPS = 4096;
    public static final int MAX_GROUPS_PER_PLAYER = 20;
    public static final long CREATE_COOLDOWN_MS = 10_000L;
    public static final long OWNER_INACTIVE_MS = 60L * 24 * 60 * 60 * 1000;
    public static final long PENDING_INVITE_TTL_MS = 10L * 60 * 1000;
    private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;

    /** 一个群：id、名字（空串=未命名）、成员（UUID→名字，LinkedHashMap 保序）、创建者。 */
    public static final class Group {
        final UUID id;
        String name;
        final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
        UUID creator;
        long createdAt;
        long ownerLastSeen;

        Group(UUID id, String name, UUID creator) {
            this(id, name, creator, System.currentTimeMillis(), System.currentTimeMillis());
        }

        Group(UUID id, String name, UUID creator, long createdAt, long ownerLastSeen) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.creator = creator;
            this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
            this.ownerLastSeen = ownerLastSeen > 0 ? ownerLastSeen : this.createdAt;
        }

        public UUID id() { return id; }
        public String name() { return name; }
        public UUID creator() { return creator; }
        public long createdAt() { return createdAt; }
        public long ownerLastSeen() { return ownerLastSeen; }
        public boolean hasMember(UUID uuid) { return uuid != null && members.containsKey(uuid); }
        public Collection<UUID> memberIds() { return new LinkedHashSet<>(members.keySet()); }
        public int size() { return members.size(); }

        @Override
        public String toString() {
            return "Group{id=" + id + ", name='" + name + "', members=" + members.size() + ", creator=" + creator + "}";
        }
    }

    private static final Map<UUID, Group> groups = new LinkedHashMap<>();
    /** groupId → 还没回应的被邀请者（第 4 轮邀请流程；纯内存态，见类注释）。 */
    private static final Map<UUID, LinkedHashSet<UUID>> pendingInvites = new LinkedHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Path storePath;
    private static boolean loaded;
    private static boolean persistenceReadOnly;
    private static final Map<UUID, Long> createCooldown = new LinkedHashMap<>();
    private static final Map<UUID, Map<UUID, Long>> pendingInviteAt = new LinkedHashMap<>();

    private static void log(String msg) {
        LogUtils.getLogger().info("[e33chat] srv | group | " + msg);
    }

    // ===== 生命周期 =====

    /** 首次 JOIN 时按当前世界的 serverconfig 目录把群状态读进内存（只读一次）。 */
    public static synchronized void ensureLoaded(MinecraftServer server) {
        if (loaded) return;
        loaded = true;
        persistenceReadOnly = false;
        try {
            storePath = server.getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig").resolve("e33chat-groups.json");
            if (Files.exists(storePath) && Files.size(storePath) > MAX_FILE_BYTES) {
                persistenceReadOnly = true;
                log("read-only | group store exceeds " + MAX_FILE_BYTES + " bytes | file=" + storePath);
                return;
            }
            loadFromDisk();
        } catch (Throwable t) {
            log("load failed | " + t);
        }
    }

    /** 服务器停止：先保存再清空，确保 cleanupInactive 更新的 ownerLastSeen 落盘。 */
    public static synchronized void shutdown() {
        saveToDisk();
        reset();
    }

    /** 服务器停止：清空内存态，让下一个世界按自己的文件重新加载。 */
    public static synchronized void reset() {
        groups.clear();
        pendingInvites.clear();
        pendingInviteAt.clear();
        createCooldown.clear();
        loaded = false;
        persistenceReadOnly = false;
        storePath = null;
    }

    @SuppressWarnings("unchecked")
    private static void loadFromDisk() {
        if (storePath == null || !Files.exists(storePath)) return;
        try (Reader r = Files.newBufferedReader(storePath, StandardCharsets.UTF_8)) {
            Map<String, Object> root = GSON.fromJson(r, new TypeToken<Map<String, Object>>() {}.getType());
            if (root == null) return;
            Object listObj = root.get("groups");
            boolean migrated = false;
            if (!(listObj instanceof List<?> list)) {
                persistenceReadOnly = true;
                log("read-only | group store root has no groups array | file=" + storePath);
                return;
            }
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                try {
                    UUID id = UUID.fromString(String.valueOf(m.get("id")));
                    String name = m.get("name") == null ? "" : String.valueOf(m.get("name"));
                    UUID creator = null;
                    try {
                        creator = UUID.fromString(String.valueOf(m.get("creator")));
                    } catch (Throwable ignored) {
                    }
                    boolean missingTimestamp = m.get("createdAt") == null || m.get("ownerLastSeen") == null;
                    long createdAt = readLong(m.get("createdAt"), System.currentTimeMillis());
                    long ownerLastSeen = readLong(m.get("ownerLastSeen"), createdAt);
                    if (missingTimestamp) migrated = true;
                    Group g = new Group(id, name, creator, createdAt, ownerLastSeen);
                    Object memObj = m.get("members");
                    if (memObj instanceof List<?> mems) {
                        for (Object mo : mems) {
                            if (!(mo instanceof Map<?, ?> mm)) continue;
                            try {
                                UUID mid = UUID.fromString(String.valueOf(mm.get("uuid")));
                                String mname = mm.get("name") == null ? "" : String.valueOf(mm.get("name"));
                                if (g.members.size() >= MAX_MEMBERS) break;
                                g.members.put(mid, mname);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    if (g.members.isEmpty()) {
                        log("warn | skip empty group | id=" + id);
                        persistenceReadOnly = true;
                        continue;
                    }
                    if (creator == null || !g.members.containsKey(creator)) {
                        // 不自动猜测新群主：标记为无效群，交给服务器管理员处理。
                        log("warn | invalid creator not in members | id=" + id + " | creator=" + creator);
                        g.creator = null;
                    }
                    groups.put(id, g);
                } catch (Throwable t) {
                    // 单条损坏：记录 ID 并进入只读，避免后续保存把原文件覆盖成“只剩好群”。
                    log("warn | corrupt group record | id=" + (m.get("id")) + " | " + t);
                    persistenceReadOnly = true;
                }
            }
            if (groups.size() > MAX_GROUPS) log("warn | group count exceeds limit | groups=" + groups.size());
            log("loaded | groups=" + groups.size() + " | file=" + storePath);
            if (migrated && !persistenceReadOnly) saveToDisk();
        } catch (Throwable t) {
            persistenceReadOnly = true;
            log("read-only | group store read failed | file=" + storePath + " | " + t);
        }
    }

    private static long readLong(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static synchronized void saveToDisk() {
        if (storePath == null || persistenceReadOnly) return;
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Group g : groups.values()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", g.id.toString());
                m.put("name", g.name);
                m.put("creator", g.creator == null ? "" : g.creator.toString());
                m.put("createdAt", g.createdAt);
                m.put("ownerLastSeen", g.ownerLastSeen);
                List<Map<String, Object>> mems = new ArrayList<>();
                for (Map.Entry<UUID, String> e : g.members.entrySet()) {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    mm.put("uuid", e.getKey().toString());
                    mm.put("name", e.getValue());
                    mems.add(mm);
                }
                m.put("members", mems);
                list.add(m);
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("version", 2);
            root.put("groups", list);
            String json = GSON.toJson(root);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
                log("save skipped | serialized group store exceeds " + MAX_FILE_BYTES + " bytes");
                persistenceReadOnly = true;
                return;
            }
            Files.createDirectories(storePath.getParent());
            Path tmp = storePath.resolveSibling(storePath.getFileName() + ".tmp");
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(tmp, storePath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, storePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException t) {
            log("save failed | file=" + storePath + " | " + t);
        }
    }

    // ===== 查询 =====

    public static synchronized Group byId(String groupId) {
        UUID id = GroupWire.parseUuid(groupId);
        return id == null ? null : groups.get(id);
    }

    /** 某个玩家所属的全部群（按创建顺序）。 */
    public static synchronized List<Group> groupsOf(UUID player) {
        List<Group> out = new ArrayList<>();
        if (player == null) return out;
        for (Group g : groups.values()) if (g.hasMember(player)) out.add(g);
        return out;
    }

    public static synchronized int groupCount() { return groups.size(); }

    // ===== 发送工具 =====

    private static void sendSync(ServerPlayer player, Group g) {
        if (player == null || g == null) return;
        List<GroupSyncPayload.Member> members = new ArrayList<>(g.members.size());
        for (Map.Entry<UUID, String> e : g.members.entrySet()) {
            members.add(new GroupSyncPayload.Member(e.getKey(), e.getValue()));
        }
        GroupSyncPayload payload = new GroupSyncPayload(g.id.toString(),
            g.name == null ? "" : g.name,
            g.creator == null ? "" : g.creator.toString(),
            members);
        try {
            // 无条件发送：客户端没注册接收器时 Fabric 的安全路径是「丢弃」（返回 false，不进
            // 原版处理器），不会断开连接。这里不用 canSend 守卫 —— 集成服务器/局域网下
            // canSend 的时序依赖客户端注册声明，漏发会让单机群聊直接不可用。
            ServerPlayNetworking.send(player, payload);
        } catch (Throwable t) {
            log("sendSync failed | player=" + player.getName().getString() + " | " + payload + " | " + t);
        }
    }

    private static void sendEvent(ServerPlayer player, Group g, String kind, String text) {
        if (player == null || g == null) return;
        GroupEventPayload payload = new GroupEventPayload(g.id.toString(), kind, text == null ? "" : text);
        try {
            ServerPlayNetworking.send(player, payload);
        } catch (Throwable t) {
            log("sendEvent failed | player=" + player.getName().getString() + " | " + payload + " | " + t);
        }
    }

    /** 发一条进群邀请事件（kind = INVITE / ACCEPTED / DECLINED）。 */
    private static void sendInviteEvent(ServerPlayer player, Group g, String kind,
                                        String inviterName, String actorName) {
        if (player == null || g == null) return;
        GroupInviteEventPayload payload = new GroupInviteEventPayload(g.id.toString(),
            g.name == null ? "" : g.name, inviterName == null ? "" : inviterName,
            kind == null ? "" : kind, actorName == null ? "" : actorName);
        try {
            ServerPlayNetworking.send(player, payload);
        } catch (Throwable t) {
            log("sendInviteEvent failed | player=" + player.getName().getString()
                + " | " + payload + " | " + t);
        }
    }

    /**
     * 把一批玩家登记成「待回应的被邀请者」并逐个发邀请（第 4 轮）。
     *
     * <p>规则：跳过 null / 邀请者自己 / 已是成员 / 已经在 pending 里的人；**离线玩家直接忽略**
     * （不进 pending、不排队，MVP 明确接受）；人数上限按「成员 + pending」一起算。
     *
     * @return 真正发出邀请的玩家（在线且成功登记）
     */
    private static List<UUID> deliverInvites(MinecraftServer server, Group g, ServerPlayer inviter,
                                             Collection<UUID> targets) {
        List<UUID> sent = new ArrayList<>();
        if (server == null || g == null || targets == null) return sent;
        LinkedHashSet<UUID> pending =
            pendingInvites.computeIfAbsent(g.id, k -> new LinkedHashSet<>());
        Map<UUID, Long> expiry = pendingInviteAt.computeIfAbsent(g.id, k -> new LinkedHashMap<>());
        long now = System.currentTimeMillis();
        for (UUID old : new ArrayList<>(pending)) {
            Long exp = expiry.get(old);
            if (exp != null && now > exp) {
                pending.remove(old);
                expiry.remove(old);
            }
        }
        UUID inviterId = inviter == null ? null : inviter.getUUID();
        String inviterName = inviter == null ? "" : inviter.getName().getString();
        for (UUID uuid : targets) {
            if (uuid == null || uuid.equals(inviterId)) continue;
            if (g.hasMember(uuid)) continue;
            if (pending.contains(uuid)) continue;
            if (g.size() + pending.size() >= MAX_MEMBERS) break;
            ServerPlayer target = server.getPlayerList().getPlayer(uuid);
            if (target == null) {
                // 离线：忽略（不发送、不排队）—— 需求允许的 MVP 取舍
                log("invite skipped (offline) | group=" + g.id + " | target=" + uuid);
                continue;
            }
            pending.add(uuid);
            expiry.put(uuid, now + PENDING_INVITE_TTL_MS);
            sendInviteEvent(target, g, GroupInviteEventPayload.KIND_INVITE, inviterName,
                target.getName().getString());
            sent.add(uuid);
        }
        log("invites dispatched | group=" + g.id + " | inviter=" + inviterName
            + " | sent=" + sent.size() + " | pending=" + pending.size());
        return sent;
    }

    /** 把群快照广播给全体**在线**成员。 */
    private static void broadcastSync(MinecraftServer server, Group g) {
        if (server == null || g == null) return;
        int n = 0;
        for (UUID uuid : g.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                sendSync(p, g);
                n++;
            }
        }
        log("broadcast sync | " + g + " | online=" + n);
    }

    /** 玩家名字：在线用当前名字，离线沿用成员表里最后已知的名字。 */
    private static String nameOf(MinecraftServer server, UUID uuid, String fallback) {
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(uuid);
        if (p != null) return p.getName().getString();
        String f = fallback == null ? "" : fallback.trim();
        return f.isEmpty() ? "?" : f;
    }

    // ===== 操作 =====

    /**
     * 建群：发起者自动进群并成为创建者；其余是客户端勾选的人 —— 但**不再直接拉进群**，
     * 一律走「进群邀请」（第 4 轮）。群创建后立刻只含创建者，被邀请者接受后才陆续进来。
     */
    public static synchronized void create(MinecraftServer server, ServerPlayer creator,
                                           GroupCreatePayload payload) {
        if (server == null || creator == null || payload == null) return;
        if (groups.size() >= MAX_GROUPS) {
            creator.sendSystemMessage(net.minecraft.network.chat.Component.translatable("e33chat.group.create_limit"));
            log("create denied | global limit reached | groups=" + groups.size());
            return;
        }
        int owned = 0;
        for (Group existing : groups.values()) {
            if (creator.getUUID().equals(existing.creator)) owned++;
        }
        if (owned >= MAX_GROUPS_PER_PLAYER) {
            creator.sendSystemMessage(net.minecraft.network.chat.Component.translatable("e33chat.group.create_limit_player"));
            log("create denied | player limit | player=" + creator.getName().getString() + " | owned=" + owned);
            return;
        }
        long now = System.currentTimeMillis();
        Long lastCreate = createCooldown.get(creator.getUUID());
        if (lastCreate != null && now - lastCreate < CREATE_COOLDOWN_MS) {
            creator.sendSystemMessage(net.minecraft.network.chat.Component.translatable("e33chat.group.create_cooldown"));
            log("create denied | cooldown | player=" + creator.getName().getString());
            return;
        }
        UUID id = UUID.randomUUID();
        String name = GroupWire.clamp(payload.groupName(), GroupWire.MAX_NAME).trim();
        Group g = new Group(id, name, creator.getUUID(), now, now);
        createCooldown.put(creator.getUUID(), now);
        g.members.put(creator.getUUID(), creator.getName().getString());
        groups.put(id, g);
        saveToDisk();
        log("create | creator=" + creator.getName().getString() + " | " + g);

        List<UUID> sent = deliverInvites(server, g, creator, payload.members());
        log("create | invites sent=" + sent.size() + " | " + g);
        broadcastSync(server, g);
    }

    public static synchronized void rename(MinecraftServer server, ServerPlayer actor,
                                           GroupRenamePayload payload) {
        Group g = byId(payload == null ? null : payload.groupId());
        if (g == null) { log("rename ignored | unknown group"); return; }
        if (actor == null || !g.hasMember(actor.getUUID())) {
            log("rename denied | not a member | actor=" + name(actor));
            return;
        }
        g.name = GroupWire.clamp(payload.name(), GroupWire.MAX_NAME).trim();
        saveToDisk();
        log("rename | actor=" + name(actor) + " | " + g);
        broadcastSync(server, g);
        for (UUID uuid : g.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) sendEvent(p, g, GroupEventPayload.KIND_RENAMED, g.name);
        }
    }

    /**
     * 邀请玩家进群（第 4 轮）：**只有创建者能邀请**，普通成员无邀请权。
     *
     * <p>不再直接把人并进成员表 —— 只登记 pending 并发 {@link GroupInviteEventPayload#KIND_INVITE}，
     * 等对方在私聊里点「接受」才真正进群（见 {@link #respondInvite}）。
     * 权限不符时回一条 {@link GroupEventPayload#KIND_INVITE_DENIED} 给操作者，群与其状态都不动。
     */
    public static synchronized void invite(MinecraftServer server, ServerPlayer actor,
                                            GroupInvitePayload payload) {
        Group g = byId(payload == null ? null : payload.groupId());
        if (g == null) { log("invite ignored | unknown group"); return; }
        if (actor == null || !g.hasMember(actor.getUUID())) {
            log("invite denied | not a member | actor=" + name(actor));
            return;
        }
        // 权限：只有创建者能邀请（需求 3 明确：群成员无邀请权）
        if (g.creator == null || !g.creator.equals(actor.getUUID())) {
            log("invite denied | actor is not creator | actor=" + name(actor) + " | " + g);
            sendEvent(actor, g, GroupEventPayload.KIND_INVITE_DENIED, g.name);
            return;
        }
        List<UUID> sent = deliverInvites(server, g, actor, payload.members());
        if (sent.isEmpty()) log("invite no-op | actor=" + name(actor) + " | " + g);
    }

    /**
     * 被邀请者回应邀请（第 4 轮）：{@code accept=true} 才真正进群。
     *
     * <p>校验链（任一不满足都只记日志、不改任何状态）：群存在 → 我**不是**成员（幂等，已进群
     * 就当重复包丢弃）→ 我确实在 pendingInvites 里。因此客户端伪造 accept 也进不来别人的群。
     */
    public static synchronized void respondInvite(MinecraftServer server, ServerPlayer player,
                                                   GroupInviteRespondPayload payload) {
        if (server == null || player == null || payload == null) return;
        Group g = byId(payload.groupId());
        if (g == null) { log("invite_respond ignored | unknown group | " + payload); return; }
        UUID me = player.getUUID();
        LinkedHashSet<UUID> pending = pendingInvites.get(g.id);
        Map<UUID, Long> expiry = pendingInviteAt.get(g.id);
        if (expiry != null && expiry.containsKey(me)
            && System.currentTimeMillis() > expiry.get(me)) {
            if (pending != null) pending.remove(me);
            expiry.remove(me);
            log("invite_respond ignored | expired | player=" + name(player) + " | " + g.id);
            return;
        }
        if (g.hasMember(me)) {
            if (pending != null) pending.remove(me);
            log("invite_respond ignored | already member | player=" + name(player) + " | " + g.id);
            return;
        }
        if (pending == null || !pending.remove(me)) {
            log("invite_respond ignored | not invited | player=" + name(player) + " | " + payload);
            return;
        }
        if (expiry != null) expiry.remove(me);
        String inviterName = nameOf(server, g.creator, "");
        ServerPlayer creator = g.creator == null ? null
            : server.getPlayerList().getPlayer(g.creator);

        if (!payload.accept()) {
            log("invite declined | player=" + name(player) + " | group=" + g.id
                + " | pending=" + pending.size());
            if (creator != null) {
                sendInviteEvent(creator, g, GroupInviteEventPayload.KIND_DECLINED,
                    inviterName, player.getName().getString());
            }
            return;
        }

        if (g.size() >= MAX_MEMBERS) {
            log("invite accept ignored | group full | player=" + name(player) + " | " + g);
            return;
        }
        g.members.put(me, player.getName().getString());
        saveToDisk();
        log("invite accepted | player=" + name(player) + " | " + g);
        broadcastSync(server, g);
        // 给加入者自己一条「你已被加入群聊 xxx」（复用既有 KIND_INVITED 路径）
        sendEvent(player, g, GroupEventPayload.KIND_INVITED, g.name);
        if (creator != null) {
            sendInviteEvent(creator, g, GroupInviteEventPayload.KIND_ACCEPTED,
                inviterName, player.getName().getString());
        }
    }

    public static synchronized void kick(MinecraftServer server, ServerPlayer actor,
                                          GroupKickPayload payload) {
        Group g = byId(payload == null ? null : payload.groupId());
        if (g == null) { log("kick ignored | unknown group"); return; }
        if (actor == null || g.creator == null || !g.creator.equals(actor.getUUID())) {
            log("kick denied | actor is not creator | actor=" + name(actor));
            return;
        }
        UUID target = payload.target();
        if (target == null || !g.hasMember(target)) { log("kick no-op | target not in group"); return; }
        if (target.equals(g.creator)) {
            // 创建者不可被踢（否则群会失去归属）；想让他走只能他自己退出或解散
            log("kick denied | target is creator | " + g);
            return;
        }
        g.members.remove(target);
        saveToDisk();
        log("kick | actor=" + name(actor) + " | target=" + target + " | " + g);
        ServerPlayer kicked = server.getPlayerList().getPlayer(target);
        if (kicked != null) sendEvent(kicked, g, GroupEventPayload.KIND_KICKED, g.name);
        if (g.members.isEmpty()) {
            groups.remove(g.id);
            pendingInvites.remove(g.id);
            pendingInviteAt.remove(g.id);
            saveToDisk();
            log("kick -> group empty, removed | " + g.id);
            return;
        }
        broadcastSync(server, g);
    }

    public static synchronized void leave(MinecraftServer server, ServerPlayer actor,
                                           GroupLeavePayload payload) {
        Group g = byId(payload == null ? null : payload.groupId());
        if (g == null || actor == null) return;
        if (!g.hasMember(actor.getUUID())) return;
        if (g.creator != null && g.creator.equals(actor.getUUID())) {
            log("leave denied | owner must transfer or disband | actor=" + name(actor) + " | " + g);
            actor.sendSystemMessage(net.minecraft.network.chat.Component.translatable("e33chat.group.owner_must_transfer"));
            return;
        }
        g.members.remove(actor.getUUID());
        log("leave | actor=" + name(actor) + " | " + g);
        sendEvent(actor, g, GroupEventPayload.KIND_LEFT, g.name);
        if (g.members.isEmpty()) {
            groups.remove(g.id);
            pendingInvites.remove(g.id);
            pendingInviteAt.remove(g.id);
            saveToDisk();
            log("leave -> group empty, removed | " + g.id);
            return;
        }
        saveToDisk();
        broadcastSync(server, g);
    }

    public static synchronized void disband(MinecraftServer server, ServerPlayer actor,
                                             GroupDisbandPayload payload) {
        Group g = byId(payload == null ? null : payload.groupId());
        if (g == null) { log("disband ignored | unknown group"); return; }
        if (actor == null || !g.hasMember(actor.getUUID())) {
            log("disband denied | not a member | actor=" + name(actor));
            return;
        }
        // 只有创建者能解散（其它成员请用 group_leave 退出）：creator 缺失/非法时同样拒绝。
        if (g.creator == null || !g.creator.equals(actor.getUUID())) {
            log("disband denied | actor is not creator | actor=" + name(actor) + " | " + g);
            return;
        }
        groups.remove(g.id);
        pendingInvites.remove(g.id);
        pendingInviteAt.remove(g.id);
        saveToDisk();
        log("disband | actor=" + name(actor) + " | " + g);
        for (UUID uuid : g.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) sendEvent(p, g, GroupEventPayload.KIND_DISBANDED, g.name);
        }
    }

    /** 群主主动转让群主；原群主保留成员身份并降为普通成员。 */
    public static synchronized void transfer(MinecraftServer server, ServerPlayer actor,
                                              GroupTransferPayload payload) {
        Group g = byId(payload == null ? null : payload.groupId());
        if (g == null || actor == null) return;
        if (g.creator == null || !g.creator.equals(actor.getUUID())) {
            log("transfer denied | actor is not creator | actor=" + name(actor) + " | " + g);
            actor.sendSystemMessage(net.minecraft.network.chat.Component.translatable("e33chat.group.transfer_denied"));
            return;
        }
        UUID target = payload.target();
        if (target == null || target.equals(g.creator) || !g.hasMember(target)) {
            log("transfer denied | target invalid | actor=" + name(actor) + " | target=" + target);
            actor.sendSystemMessage(net.minecraft.network.chat.Component.translatable("e33chat.group.transfer_denied"));
            return;
        }
        g.creator = target;
        g.ownerLastSeen = System.currentTimeMillis();
        pendingInvites.remove(g.id);
        pendingInviteAt.remove(g.id);
        saveToDisk();
        log("transfer | actor=" + name(actor) + " | target=" + target + " | " + g);
        broadcastSync(server, g);
        ServerPlayer oldOwner = server.getPlayerList().getPlayer(actor.getUUID());
        if (oldOwner != null) sendSync(oldOwner, g);
    }

    public static synchronized void markLogin(UUID playerId) {
        if (playerId == null) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Group g : groups.values()) {
            if (playerId.equals(g.creator) && g.ownerLastSeen < now) {
                g.ownerLastSeen = now;
                changed = true;
            }
        }
        if (changed) saveToDisk();
    }

    public static synchronized int cleanupInactive(MinecraftServer server, long now) {
        List<Group> removed = new ArrayList<>();
        for (Group g : new ArrayList<>(groups.values())) {
            if (g.creator == null) {
                log("warn | skip cleanup, invalid creator | id=" + g.id);
                continue;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(g.creator);
            if (online != null) {
                g.ownerLastSeen = now;
                continue;
            }
            if (g.ownerLastSeen > 0 && now - g.ownerLastSeen > OWNER_INACTIVE_MS) {
                removed.add(g);
            }
        }
        for (Group g : removed) {
            groups.remove(g.id);
            pendingInvites.remove(g.id);
            pendingInviteAt.remove(g.id);
            for (UUID uuid : g.members.keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null) sendEvent(p, g, GroupEventPayload.KIND_DISBANDED, g.name);
            }
            log("cleanup | disband inactive group | " + g);
        }
        if (!removed.isEmpty()) saveToDisk();
        return removed.size();
    }

    public static synchronized boolean adminDelete(MinecraftServer server, String groupId) {
        if (persistenceReadOnly) return false;
        Group g = byId(groupId);
        if (g == null) return false;
        groups.remove(g.id);
        pendingInvites.remove(g.id);
        pendingInviteAt.remove(g.id);
        saveToDisk();
        if (server != null) {
            for (UUID uuid : g.members.keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null) sendEvent(p, g, GroupEventPayload.KIND_DISBANDED, g.name);
            }
        }
        log("admin delete | " + g);
        return true;
    }

    public static synchronized List<Group> allGroups() {
        return new ArrayList<>(groups.values());
    }

    /** 转发一条群消息给全体在线成员（含发送者）。 */
    public static synchronized void relayMessage(MinecraftServer server, ServerPlayer sender,
                                                  GroupMessagePayload payload) {
        Group g = byId(payload == null ? null : payload.groupId());
        if (g == null) { log("message ignored | unknown group | group=" + (payload == null ? "null" : payload.groupId())); return; }
        if (sender == null || !g.hasMember(sender.getUUID())) {
            log("message denied | not a member | sender=" + name(sender) + " | " + g);
            return;
        }
        String content = GroupWire.clamp(payload.content(), GroupWire.MAX_CONTENT);
        if (content.isBlank()) return;
        String msgHash = GroupWire.clamp(payload.msgHash(), 64);
        // 客户端可能送明文（旧/别家实现）：不是十进制串就自己哈希，保证两端算法一致
        if (msgHash.isEmpty() || !msgHash.chars().allMatch(c -> (c >= '0' && c <= '9') || c == '-')) {
            msgHash = String.valueOf(content.hashCode());
        }
        GroupMessageRelayPayload relay = new GroupMessageRelayPayload(g.id.toString(),
            sender.getUUID(), sender.getName().getString(), content, msgHash);
        int n = 0;
        for (UUID uuid : g.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null) continue;
            try {
                ServerPlayNetworking.send(p, relay);
                n++;
            } catch (Throwable t) {
                log("relay failed | player=" + p.getName().getString() + " | " + t);
            }
        }
        log("message | from=" + sender.getName().getString() + " | " + g
            + " | recipients=" + n + " | hash=" + msgHash
            + " | preview=" + preview(content));
    }

    /** P2：把一条图片消息转发给群内**其他**在线成员（发送者本地回显）。 */
    public static synchronized void relayImage(MinecraftServer server, ServerPlayer sender,
                                               ImageMessagePayload payload) {
        Group g = byId(payload == null ? null : payload.groupId());
        if (g == null) { log("image ignored | unknown group | group=" + (payload == null ? "null" : payload.groupId())); return; }
        if (sender == null || !g.hasMember(sender.getUUID())) {
            log("image denied | not a member | sender=" + name(sender) + " | " + g);
            return;
        }
        String content = GroupWire.clamp(payload.content(), ImageMessagePayload.MAX_CONTENT);
        if (content.isBlank()) return;
        String msgHash = GroupWire.clamp(payload.msgHash(), 64);
        if (msgHash.isEmpty() || !msgHash.chars().allMatch(c -> (c >= '0' && c <= '9') || c == '-')) {
            msgHash = String.valueOf(content.hashCode());
        }
        ImageMessageRelayPayload relay = new ImageMessageRelayPayload(
            ImageMessagePayload.SCOPE_GROUP, g.id.toString(), sender.getUUID(),
            sender.getName().getString(), content, msgHash);
        int n = 0;
        for (UUID uuid : g.members.keySet()) {
            if (uuid.equals(sender.getUUID())) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p == null) continue;
            try {
                ServerPlayNetworking.send(p, relay);
                n++;
            } catch (Throwable t) {
                log("image relay failed | player=" + p.getName().getString() + " | " + t);
            }
        }
        log("image | from=" + sender.getName().getString() + " | " + g
            + " | recipients=" + n + " | hash=" + msgHash);
    }

    /** 登录补发：把该玩家所属的全部群逐个同步下去（客户端因此不需要本地保存成员表）。 */
    public static synchronized void syncAllTo(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return;
        ensureLoaded(server);
        List<Group> mine = groupsOf(player.getUUID());
        for (Group g : mine) sendSync(player, g);
        if (!mine.isEmpty()) {
            log("join sync | player=" + player.getName().getString() + " | groups=" + mine.size());
        }
    }

    private static String name(ServerPlayer p) {
        return p == null ? "?" : p.getName().getString();
    }

    private static String preview(String s) {
        if (s == null || s.isEmpty()) return "";
        String one = s.replace('\n', ' ').replace('\r', ' ');
        return one.length() <= 40 ? one : one.substring(0, 40) + "...";
    }
}
