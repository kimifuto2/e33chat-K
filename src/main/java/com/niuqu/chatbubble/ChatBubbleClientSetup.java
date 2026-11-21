package com.niuqu.chatbubble;
import com.niuqu.chatbubble.config.ChatBubbleConfigScreen;
import com.niuqu.chatbubble.render.ChatBubbleHudOverlay;
import com.niuqu.chatbubble.render.RoundRectRenderer;

import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.config.ServerConfigScreen;
import com.niuqu.chatbubble.config.ConfigManager;
import com.niuqu.chatbubble.image.ImageLoader;
import com.niuqu.chatbubble.network.ChatMetaPayload;
import com.niuqu.chatbubble.network.ConfigSyncPayload;
import com.niuqu.chatbubble.network.ConfigSyncV2Payload;
import com.niuqu.chatbubble.network.HistoryPayload;
import com.niuqu.chatbubble.network.MediaCapPayload;
import com.niuqu.chatbubble.network.MediaResponsePayload;
import com.niuqu.chatbubble.network.MediaUploadAckPayload;
import com.niuqu.chatbubble.network.ServerConfigScreenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.PackType;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;

public class ChatBubbleClientSetup implements ClientModInitializer {
    private static ChatBubbleConfig config = ChatBubbleConfig.defaults();
    private static Path configPath;
    private static boolean leftWasDown;

    /** Main-thread heartbeat, read by the stall watchdog below. */
    private static volatile long lastTickMs;
    private static final long STALL_LOG_MS = 2000L;
    private static boolean watchdogStarted;

    /** Milliseconds since the last client tick (diagnostics only). */
    public static long lastTickAgeMs() {
        long t = lastTickMs;
        return t == 0L ? -1L : System.currentTimeMillis() - t;
    }

    /** 开关文件名：任何情况下都不声明自定义通道。 */
    private static final String NO_CUSTOM_CHANNELS_FLAG = "no-custom-channels.flag";

    // ── 自定义载荷通道的「按连接」注册状态 ────────────────────────────────────
    // 旧实现用一个进程级一次性闩锁（注册过就永不再注销），单机/LAN 注册过的接收器会残留到
    // 之后的每一个连接；连 Paper 时 Fabric 会把它们塞进 minecraft:register 声明出去，被
    // "Invalid custom payload payload!" 踢掉。现在改为：进入配置阶段先全部注销，再按本次
    // 连接的目标决定是否注册；离开世界/服务器时注销。activeChannelIds = 本连接真正注册的 id。
    /** 本连接已注册的 GlobalReceiver id（注销后清空）；所有读写都在类锁内，见下面两个方法。 */
    private static final java.util.Set<Identifier> activeChannelIds = new java.util.LinkedHashSet<>();
    /** 有一次连接正在进行（配置阶段开始 ~ 落地成 PLAY 之前）：此时 world 为 null 但**不是**退世界。 */
    private static volatile boolean connectionPending;
    /** 已经成功 JOIN 过（真的进过世界/服务器），用于识别「退世界没收到 DISCONNECT」的兜底。 */
    private static volatile boolean playSessionSeen;

    // ── 「服务端是否也装了本模组」的探测状态 ──────────────────────────────────
    // JOIN 时用 ClientPlayNetworking.canSend(QuoteSyncPayload.ID) 判定，连接边界重置。
    // 私聊「增强功能不可用」的提示只在确认为 ABSENT 时出现：UNKNOWN（还没探测 / 探测
    // 抛异常 / 单机）一律不提示 —— 宁可晚一点，也不要误报。
    private enum ServerModState { UNKNOWN, PRESENT, ABSENT }

    private static volatile ServerModState serverModState = ServerModState.UNKNOWN;

    /** 本次连接内已经提示过「服务器没装 E33Chat」的私聊对象（归一化后的名字）。 */
    private static final java.util.Set<String> whisperUnavailableNotices =
        new java.util.LinkedHashSet<>();

    // ── 群聊可用性探测 ────────────────────────────────────────────────────────
    // JOIN 后判 ClientPlayNetworking.canSend(GroupCreatePayload.ID)：false = 服务端没装
    // E33Chat，或装的是不支持群聊的旧版（没有注册群聊 C2S 接收器）。这时公屏插一条本地
    // 系统提示（不走横幅、不计未读），**每次连接仅一次**。
    // 为什么要等几个 tick 再判：服务端把自己的可收通道（minecraft:register）在玩家刚落地
    // 时发出，客户端 JOIN 事件只比它早一两个 tick —— 立刻判会拿到假阴性。1 秒（20 tick）
    // 足够，且玩家此时才刚进世界，来不及点「+」。
    private static final int GROUP_SUPPORT_GRACE_TICKS = 20;
    /** 本次连接是否还要探测群聊支持（JOIN 置 true，探测出结果后置 false）。 */
    private static volatile boolean groupSupportPending;
    /** JOIN 之后经过的 tick 数（只在 {@link #groupSupportPending} 时有意义）。 */
    private static int groupSupportTicks;

    /** 服务端是否也装了本模组（联动功能可用）。**未探测时返回 false** —— 想判「服务端
     *  没装」必须走 {@link #claimWhisperUnavailableNotice(String)}（它还要求状态已确认为
     *  ABSENT），不要写成 {@code !serverHasE33Chat()}，否则首次入服会误报。 */
    public static boolean serverHasE33Chat() {
        return serverModState == ServerModState.PRESENT;
    }

    /** 探测是否已有结果（false = 尚未探测 / 探测失败）。 */
    public static boolean serverModStateKnown() {
        return serverModState != ServerModState.UNKNOWN;
    }

    /** 连接边界（JOIN 开头 / DISCONNECT）重置：探测结果失效，私聊提示去重表清空，
     *  群聊探测重新开始（每条连接最多一条「服务端不支持群聊」提示）。 */
    private static synchronized void resetConnectionState() {
        serverModState = ServerModState.UNKNOWN;
        whisperUnavailableNotices.clear();
        groupSupportPending = false;
        groupSupportTicks = 0;
    }

    /** JOIN 后探测群聊支持（由 END_CLIENT_TICK 每 tick 调，自己在内部计数）。
     *
     *  <p>{@link com.niuqu.chatbubble.store.GroupStore#available()} 就是
     *  {@code ClientPlayNetworking.canSend(GroupCreatePayload.ID)}：服务端只有在注册了群聊
     *  C2S 接收器时才会把该通道宣告给客户端，所以 false 同时覆盖「没装服务端 E33Chat」与
     *  「装的是不支持群聊的旧版」两种情况。
     *
     *  <p>单机 / 自己是局域网主机（集成服务器）跑的就是本 jar 的服务端入口，必然支持群聊，
     *  直接跳过（配置阶段拿不到可靠的 canSend 结果）。
     *  群聊开关在配置里关掉时也不提示 —— 那是用户自己的选择，不是服务端缺功能。 */
    private static void pollGroupSupport(Minecraft client) {
        if (!groupSupportPending) return;
        if (client == null || client.player == null || client.level == null) return;
        if (++groupSupportTicks < GROUP_SUPPORT_GRACE_TICKS) return;
        groupSupportPending = false;          // 只提示一次：本连接不再探测
        try {
            boolean supported = client.getSingleplayerServer() != null
                || com.niuqu.chatbubble.store.GroupStore.available();
            if (supported) return;
            if (!config().groupChatEnabledResolved()) return;
            ChatMessageStore.addLocalNotice(
                net.minecraft.network.chat.Component.translatable("e33chat.group.unavailable"), null);
            ChatMessageStore.debugLog(
                "[e33chat] group support probe | server has no group channels -> notice shown");
        } catch (Throwable t) {
            ChatMessageStore.debugLog("[e33chat] group support probe failed | " + t);
        }
    }

    /** 玩家名归一化：去掉 § 色码（§ 与其后紧跟的 1 个字符）、首尾空白，统一小写。 */
    static String normalizePlayerName(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\u00a7') { i++; continue; }   // § 后紧跟的格式码一起丢掉
            sb.append(c);
        }
        return sb.toString().trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 私聊「增强功能不可用」提示的闸门：**本次连接内**首次进入某个私聊会话、且已确认
     * 服务端没装 E33Chat 时返回 true（并记为已提示）。未探测 / 服务端有模组 / 名字为空 → false。
     */
    public static synchronized boolean claimWhisperUnavailableNotice(String partner) {
        String key = normalizePlayerName(partner);
        if (key.isEmpty()) return false;
        if (serverModState != ServerModState.ABSENT) return false;
        return whisperUnavailableNotices.add(key);
    }

    /**
     * 配置阶段的通道生命周期：INIT 与 START 共用（幂等）。先无条件注销上一次连接的残留，
     * 再为本连接注册 E33Chat 的全部联动接收器。
     *
     * <p>不再按服务器地址做白名单门控：E33Chat 只有 14 条通道，白名单最多只能撤掉这 14 条，
     * 管不了其它模组，无法保证 Paper 的 127 条总上限；真正裁总量的是通道节食
     * （{@code ChannelRegistrationDietMixin}）。这里始终注册，首次进服即可用联动；
     * 只有硬关文件 {@code no-custom-channels.flag} 能阻止声明。
     */
    private static void decideConfigurationChannels(Minecraft client, String phase) {
        try {
            unregisterCustomChannels("configuration " + phase);
            boolean off = flagFileExists(NO_CUSTOM_CHANNELS_FLAG);
            if (!off) registerCustomChannels();
            boolean integrated = client != null && client.getSingleplayerServer() != null;
            com.mojang.logging.LogUtils.getLogger().info(
                "[e33chat] custom payload channels | phase={} | integrated={} | channels={} | forceOff={}",
                phase, integrated ? "yes" : "no", channelsActive() ? "on" : "off", off ? "yes" : "no");
        } catch (Throwable t) {
            ChatMessageStore.debugLog("[e33chat] channel decision at configuration " + phase + " failed | " + t);
        }
    }

    /** config/e33chat/<name> 是否存在（诊断/兼容开关，见 {@link #registerCustomChannels()}）。 */
    private static boolean flagFileExists(String name) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDirectory == null) return false;
            return Files.exists(mc.gameDirectory.toPath().resolve("config/e33chat").resolve(name));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 我们为「服务器联动」注册的全部 play 阶段通道：引用同步、消息元数据、入服历史回填、
     * 配置下发（V1/V2）、服务器配置界面、服务器图床（上传回执 / 分块下载）、图床能力。
     *
     * <p>顺序 = 注册顺序，注销时按同一张表来，方便日志对照。注意 {@code MediaClient} 的两个
     * 通道是在它自己的 {@code registerReceivers()} 里注册的，但 id 归这里统一管 ——
     * 注销必须能覆盖到它们，否则图床通道会残留（同样会被 Paper 踢）。
     */
    private static java.util.List<Identifier> channelIds() {
        return java.util.List.of(
            ChatMetaPayload.ID.id(),
            HistoryPayload.ID.id(),
            ConfigSyncPayload.ID.id(),
            ConfigSyncV2Payload.ID.id(),
            ServerConfigScreenPayload.ID.id(),
            MediaUploadAckPayload.ID.id(),
            MediaResponsePayload.ID.id(),
            MediaCapPayload.ID.id(),
            // 群聊（S2C 四条）：群快照 / 消息转发 / 事件通知 / 进群邀请与回应回执。
            // C2S 方向客户端不注册接收器。
            com.niuqu.chatbubble.network.GroupSyncPayload.ID.id(),
            com.niuqu.chatbubble.network.GroupMessageRelayPayload.ID.id(),
            com.niuqu.chatbubble.network.GroupEventPayload.ID.id(),
            com.niuqu.chatbubble.network.GroupInviteEventPayload.ID.id(),
            // 玩家互动动作（S2C 一条）：服务端校验后按会话范围转发的动作消息。
            // C2S（player_action）方向客户端不注册接收器。
            com.niuqu.chatbubble.network.PlayerActionRelayPayload.ID.id());
    }

    /** 本连接当前是否注册着我们的接收器。 */
    private static synchronized boolean channelsActive() {
        return !activeChannelIds.isEmpty();
    }

    /**
     * 注册「服务器联动」用的自定义载荷接收器（引用同步、服务器配置界面、服务器图床、
     * 入服历史回填、消息元数据、配置下发）。
     *
     * <p>Fabric 会把 {@code ClientPlayNetworking.getGlobalReceivers()}（即这里注册的集合）
     * 声明给服务器：对 Fabric 服务端是配置阶段收到 {@code c:register} 后回一份 PLAY 相位的
     * {@code c:register}；对 Paper/vanilla 是 PLAY 阶段刚拿到 GameJoin 时发一条
     * {@code minecraft:register}。Paper 这类严格校验自定义载荷的服务端会直接以
     * "Invalid custom payload" 踢人。客户端在配置阶段无法得知服务端装了什么模组，因此
     * "Invalid custom payload" 踢人。这里不再按服务器地址做白名单门控：E33Chat 自己的
     * 14 条通道管不了其它模组，也保证不了 Paper 的 127 条总上限；总量交给通道节食
     * （{@code ChannelRegistrationDietMixin}）。通道在每个连接边界统一注册/注销，
     * 首次进服即可用联动。
     * <p><b>幂等</b>：本次连接已经注册过就直接返回；注册失败会回滚（把已登记的 id 全部注销），
     * 绝不让半套接收器留下来。
     */
    private static synchronized void registerCustomChannels() {
        if (!activeChannelIds.isEmpty()) return;
        // 先登记 id 再逐个注册：万一中途某个 registerGlobalReceiver 抛异常，注销路径也能
        // 把我们登记过的 id 全部清干净（unregisterGlobalReceiver 对没注册过的 id 只是返回 null）。
        activeChannelIds.addAll(channelIds());
        try {
            ClientPlayNetworking.registerGlobalReceiver(ChatMetaPayload.ID, (payload, context) -> {
                // 版本不一致/截断时解码器返回占位载荷（senderUUID=null）：整条忽略，只记诊断日志。
                // 绝不能让半解码的 meta 进 store（会 NPE 或把引用块贴到错的气泡上）。
                if (!payload.isValid()) {
                    ChatMessageStore.debugLog("[e33chat] chat_meta ignored | undecodable payload (version mismatch?)");
                    return;
                }
                context.client().execute(() -> ChatMessageStore.applyChatMeta(
                    payload.senderUUID(), payload.senderName(), payload.messageHash(),
                    payload.quoteSender(), payload.quoteContent(), payload.mentionTargets(),
                    payload.quotedHash()));
            });
            ClientPlayNetworking.registerGlobalReceiver(HistoryPayload.ID, (payload, context) -> {
                context.client().execute(() -> ChatMessageStore.addHistoryMessages(payload.entries()));
            });
            ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, (payload, context) -> {
                context.client().execute(() -> ConfigSyncPayload.handle(payload));
            });
            ClientPlayNetworking.registerGlobalReceiver(ConfigSyncV2Payload.ID, (payload, context) -> {
                context.client().execute(() -> ConfigSyncV2Payload.handle(payload));
            });
            // Server-config GUI: opened on the client only (server never loads the Screen)
            ClientPlayNetworking.registerGlobalReceiver(ServerConfigScreenPayload.ID, (payload, context) -> {
                context.client().execute(() -> Minecraft.getInstance().gui.setScreen(new ServerConfigScreen(
                    Minecraft.getInstance().gui.screen(),
                    payload.useTpa(), payload.historyEnabled(), payload.templateDebug(),
                    payload.mediaEnabled(), payload.mediaAutoClean(),
                    payload.chatTemplates(), payload.whisperTemplates())));
            });
            com.niuqu.chatbubble.image.MediaClient.registerReceivers();
            ClientPlayNetworking.registerGlobalReceiver(MediaCapPayload.ID, (payload, context) -> {
                context.client().execute(() -> MediaCapPayload.handle(payload));
            });
            // ── 群聊（方案 A：服务端中继）─────────────────────────────────────────
            // 三条 S2C 都在客户端线程处理：GroupSync 覆盖群名/成员表，Relay 落成气泡，
            // Event 处理「被拉群 / 被踢 / 解散 / 改名」。任何异常只记日志 —— 群聊坏掉
            // 不能连累聊天本体。
            ClientPlayNetworking.registerGlobalReceiver(
                com.niuqu.chatbubble.network.GroupSyncPayload.ID, (payload, context) ->
                    context.client().execute(() -> {
                        try {
                            com.niuqu.chatbubble.store.GroupStore.applySync(payload);
                        } catch (Throwable t) {
                            ChatMessageStore.debugLog("[e33chat] group_sync handler failed | " + t);
                        }
                    }));
            ClientPlayNetworking.registerGlobalReceiver(
                com.niuqu.chatbubble.network.GroupMessageRelayPayload.ID, (payload, context) ->
                    context.client().execute(() -> {
                        try {
                            com.niuqu.chatbubble.store.GroupStore.receiveMessage(payload);
                        } catch (Throwable t) {
                            ChatMessageStore.debugLog("[e33chat] group_relay handler failed | " + t);
                        }
                    }));
            ClientPlayNetworking.registerGlobalReceiver(
                com.niuqu.chatbubble.network.GroupEventPayload.ID, (payload, context) ->
                    context.client().execute(() -> {
                        try {
                            com.niuqu.chatbubble.store.GroupStore.applyEvent(payload);
                        } catch (Throwable t) {
                            ChatMessageStore.debugLog("[e33chat] group_event handler failed | " + t);
                        }
                    }));
            // 第 4 轮：进群邀请（被邀请者）/ 接受·拒绝回执（创建者）
            ClientPlayNetworking.registerGlobalReceiver(
                com.niuqu.chatbubble.network.GroupInviteEventPayload.ID, (payload, context) ->
                    context.client().execute(() -> {
                        try {
                            com.niuqu.chatbubble.store.GroupStore.applyInviteEvent(payload);
                        } catch (Throwable t) {
                            ChatMessageStore.debugLog("[e33chat] group_invite_event handler failed | " + t);
                        }
                    }));
            // 玩家互动动作：按 scope 插进对应会话（公屏 / 群聊 / 私聊），系统信息样式一行。
            ClientPlayNetworking.registerGlobalReceiver(
                com.niuqu.chatbubble.network.PlayerActionRelayPayload.ID, (payload, context) ->
                    context.client().execute(() -> {
                        try {
                            com.niuqu.chatbubble.chat.action.ActionMessages.receive(payload);
                        } catch (Throwable t) {
                            ChatMessageStore.debugLog("[e33chat] player_action_relay handler failed | " + t);
                        }
                    }));
            ChatMessageStore.debugLog("[e33chat] channels registered for this connection | count="
                + activeChannelIds.size());
        } catch (Throwable t) {
            ChatMessageStore.debugLog("[e33chat] channel registration failed, rolling back | " + t);
            unregisterCustomChannels("registration failed");
        }
    }

    /**
     * 注销我们注册过的全部接收器并清空登记表。**幂等**：没有登记过就直接返回（重复调用、
     * 对已注销的连接再调一次都不会抛异常）；单个 id 注销失败只记日志，继续处理其余 id。
     *
     * <p>每次调用都是一条 {@code debugLog}，方便排查「连 Paper 时通道到底有没有残留」。
     */
    private static synchronized void unregisterCustomChannels(String reason) {
        if (activeChannelIds.isEmpty()) {
            ChatMessageStore.debugLog("[e33chat] channels unregistered | reason=" + reason + " | none registered");
            return;
        }
        int requested = activeChannelIds.size();
        int removed = 0;
        for (Identifier id : activeChannelIds) {
            try {
                if (ClientPlayNetworking.unregisterGlobalReceiver(id) != null) removed++;
            } catch (Throwable t) {
                ChatMessageStore.debugLog("[e33chat] channel unregister failed | id=" + id + " | " + t);
            }
        }
        activeChannelIds.clear();
        ChatMessageStore.debugLog("[e33chat] channels unregistered | reason=" + reason
            + " | count=" + requested + " | removed=" + removed);
    }

    /**
     * 退世界后的兜底清理：{@code ClientPlayConnectionEvents.DISCONNECT} 由 Fabric 的
     * {@code ConnectionMixin} 注入到 {@code ClientConnection.channelInactive} /
     * {@code handleDisconnection}，单机退世界要等本地连接真的关闭才会到（异步）。万一没到，
     * 这里在「进过世界、世界已经没了、也没有正在进行的连接」时补一刀。
     *
     * <p>空闲时每帧只做一次空集合判断，开销可忽略。
     */
    private static synchronized void cleanupChannelsAfterWorldExit(Minecraft client) {
        if (activeChannelIds.isEmpty()) return;
        if (connectionPending || !playSessionSeen) return;   // 正在连接 / 还没进过世界
        if (client.level != null || client.getConnection() != null) return;  // 还在世界里
        playSessionSeen = false;
        unregisterCustomChannels("left world (tick fallback)");
    }

    /** Logs when the client (main) thread stops ticking — the symptom behind
     *  "server reset the connection" reports: while the client is stuck it stops
     *  answering the server, which then drops it. */
    private static void startStallWatchdog() {
        if (watchdogStarted) return;
        watchdogStarted = true;
        Thread t = new Thread(() -> {
            long lastReport = 0L;
            while (true) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.currentTimeMillis();
                long gap = now - lastTickMs;
                if (lastTickMs != 0L && gap >= STALL_LOG_MS && now - lastReport >= STALL_LOG_MS) {
                    lastReport = now;
                    com.mojang.logging.LogUtils.getLogger().warn(
                        "[e33chat] MAIN THREAD STALL | no client tick for " + gap + " ms");
                }
            }
        }, "e33chat-watchdog");
        t.setDaemon(true);
        t.start();
    }

    public static ChatBubbleConfig config() { return config; }

    public static void saveConfig(ChatBubbleConfig newConfig) {
        config = newConfig;
        com.mojang.logging.LogUtils.getLogger().info("[e33chat] Saving config | soundPublic=" + newConfig.soundPublic() + " | soundSystem=" + newConfig.soundSystem());
        ConfigManager.save(configPath, config);
    }

    @Override
    public void onInitializeClient() {
        Path configDir = Minecraft.getInstance().gameDirectory.toPath().resolve("config/e33chat");
        configPath = configDir.resolve("e33chat-client.json");
        // Migration chain for the client config path (most recent first):
        // config/e33chat/client.json -> config/e33chat/e33chat-client.json
        // config/e33chat-client.json (2.3.1+) and config/e33chat.json (legacy) also move here.
        var logger = com.mojang.logging.LogUtils.getLogger();
        Path recentDirPath = configDir.resolve("client.json");
        Path legacyPath = Minecraft.getInstance().gameDirectory.toPath().resolve("config/e33chat-client.json");
        Path oldFlatPath = Minecraft.getInstance().gameDirectory.toPath().resolve("config/e33chat.json");
        if (!Files.exists(configPath)) {
            if (Files.exists(recentDirPath)) {
                try {
                    Files.move(recentDirPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("[e33chat] Migrated config from config/e33chat/client.json to config/e33chat/e33chat-client.json");
                } catch (Exception e) {
                    logger.warn("[e33chat] Config migration failed", e);
                }
            } else if (Files.exists(legacyPath)) {
                try {
                    Files.createDirectories(configDir);
                    Files.move(legacyPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("[e33chat] Migrated config from config/e33chat-client.json to config/e33chat/e33chat-client.json");
                } catch (Exception e) {
                    logger.warn("[e33chat] Config migration failed", e);
                }
            } else if (Files.exists(oldFlatPath)) {
                config = ConfigManager.load(oldFlatPath);
                ConfigManager.save(configPath, config);
                try {
                    Files.delete(oldFlatPath);
                } catch (Exception e) {
                    logger.warn("[e33chat] Legacy config cleanup failed", e);
                }
                logger.info("[e33chat] Migrated config from config/e33chat.json to config/e33chat/e33chat-client.json");
            }
        }
        // Load is unconditional once the new path exists — the static field
        // starts as defaults() (non-null), so a null check can never trigger.
        if (Files.exists(configPath)) {
            config = ConfigManager.load(configPath);
        } else if (Files.exists(recentDirPath)) {
            // Migration move failed earlier (locked/IO) — read in place so
            // settings are never silently replaced by defaults.
            config = ConfigManager.load(recentDirPath);
        } else if (Files.exists(legacyPath)) {
            config = ConfigManager.load(legacyPath);
        } else if (Files.exists(oldFlatPath)) {
            config = ConfigManager.load(oldFlatPath);
        } else {
            config = ConfigManager.load(configPath);
        }

        // 自定义载荷通道 = 服务端联动功能的前提，但 Fabric 会把「客户端能收哪些通道」的声明
        // 发给服务端（Fabric 服：配置阶段收到 c:register 后回一份 PLAY 相位的 c:register；
        // Paper/vanilla：PLAY 阶段刚拿到 GameJoin 时发 minecraft:register）。Paper 这类严格
        // 校验的服务端会直接以 "Invalid custom payload" 踢人。E33Chat 自己的 14 条通道不再按
        // 服务器地址做白名单门控：白名单无法控制其它模组的通道，也保证不了 Paper 的 127 条
        // 总上限；总量由通道节食（ChannelRegistrationDietMixin）负责。
        //   config/e33chat/no-custom-channels.flag -> 任何情况下都不声明（硬关）
        //
        // 关键：声明集合是**进程级全局的**（ClientPlayNetworking.getGlobalReceivers()），所以
        // 必须在每个连接边界先把自己注册的东西全部注销，否则单机/LAN 注册过的接收器会残留到
        // 之后的 Paper 连接里 → 被踢。下面三个钩子配合完成「按连接」的生命周期：
        //   START   : 先无条件注销，再按本次目标决定是否注册（幂等）
        //   DISCONNECT（PLAY）: 注销
        //   帧兜底  : 单机退世界时 DISCONNECT 是异步的，没等到就在这里补注销
        boolean forceOff = flagFileExists(NO_CUSTOM_CHANNELS_FLAG);
        if (forceOff) {
            com.mojang.logging.LogUtils.getLogger().info(
                "[e33chat] custom payload channels: OFF (no-custom-channels.flag)");
        } else {
            // 进入配置阶段的第一时刻（配置网络处理器刚构造完，**早于任何配置包**）：远程连接先把
            // 上一次连接残留的接收器清掉，并立刻注册本次连接的接收器。它不依赖服务端是否发包，
            // 因此比 START 更早、更确定。
            // 单机/LAN 必须放过：SERVER_STARTING 的注册早于这里（集成服务器先启动再连接），
            // 清了会把单机联动自己关掉（config START 会再补回来，但没必要先砸一次）。
            net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.INIT
                .register((handler, client) -> {
                    try {
                        connectionPending = true;
                        // 群聊状态按连接隔离：上一次连接的群快照/成员表在这里清掉，
                        // 本次连接的服务端会在 JOIN 时用 GroupSync 重建（不在 JOIN 清，
                        // 因为服务端的 JOIN 同步包**早于**客户端 JOIN 事件到达）。
                        try {
                            com.niuqu.chatbubble.store.GroupStore.clear("configuration init");
                        } catch (Throwable ignored) {
                        }
                        if (client.getSingleplayerServer() == null) {
                            decideConfigurationChannels(client, "init");
                        }
                    } catch (Throwable t) {
                        ChatMessageStore.debugLog("[e33chat] channel cleanup at configuration init failed | " + t);
                    }
                });
            // 单机 / 局域网：集成服务器启动即注册，联动功能全保留（同一个 JVM，配置阶段就生效）。
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING
                .register(server -> {
                    connectionPending = true;
                    registerCustomChannels();
                });
            // 配置阶段 START：**先无条件注销**（幂等，重复注销不抛异常），再决定本连接是否注册。
            // 时序（已按 Fabric API 5.1.6 字节码核实）：START 由配置阶段的 minecraft:brand
            // 或 c:version 触发，而本模组的通道申报包只在收到服务端 c:register 时由
            // ClientConfigurationNetworkAddon.receiveRegistration 里的
            // sendInitialChannelRegistrationPacket() 发出 —— 服务端先发 version 再发 register
            // （CommonPacketsImpl.init 里两个 Task 的注册顺序），所以 START 的决定一定早于申报。
            net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.START
                .register((handler, client) -> decideConfigurationChannels(client, "start"));
            com.mojang.logging.LogUtils.getLogger().info(
                "[e33chat] custom payload channels | always registered | per-connection lifecycle");

        }

        // 服务器也装了 E33Chat 时会为我们的 C2S 通道注册接收器，canSend() 因此为 true。
        // 首次进服就能拿到该探测结果；通道本身在配置阶段已经注册，不再依赖白名单记忆。

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
            (handler, sender, client) -> {
                // 连接边界：探测结果与「私聊提示已经出过」的去重表都重新开始
                // （换服 / 重连后要重新判断，提示也重新计一次）。
                resetConnectionState();
                // 群聊可用性：本条连接重新开始探测（见 pollGroupSupport，每次连接最多一条提示）。
                groupSupportPending = true;
                groupSupportTicks = 0;
                // 已经落地成 PLAY：连接不再「pending」，并且本机确实进过世界（帧兜底用）。
                // 注意 Fabric 是在 onServerReady() 里先发 JOIN、再发 minecraft:register 的，
                // 所以这里（以及 START 里的决定）一定早于通道声明被组装。
                connectionPending = false;
                playSessionSeen = true;
                // 探测服务端是否也装了 E33Chat（canSend = 服务端为 QuoteSyncPayload 注册了接收器）。
                // 该探测与通道是否声明无关：即使 forceOff 也要拿到结果，否则私聊提示永远不出现。
                try {
                    boolean integrated = client.getSingleplayerServer() != null;
                    boolean serverHasE33 = integrated
                        || net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                            com.niuqu.chatbubble.network.QuoteSyncPayload.ID);
                    serverModState = serverHasE33 ? ServerModState.PRESENT : ServerModState.ABSENT;
                } catch (Throwable ignored) {
                    // best effort: never disturb the connection
                }
            });

        // 断开（回主菜单 / 换服 / 退出单机世界）时：
        //   ① 注销本次连接注册的接收器 —— 否则会残留到下一次连接；连 Paper 时这些通道会被
        //      塞进声明里，正是 "Invalid custom payload" 踢人的来源。
        //   ② 清掉连接级状态：探测结果作废、私聊提示去重表清空。
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> {
                try {
                    unregisterCustomChannels("play disconnect");
                } catch (Throwable t) {
                    ChatMessageStore.debugLog("[e33chat] channel cleanup at disconnect failed | " + t);
                } finally {
                    connectionPending = false;
                    playSessionSeen = false;
                    resetConnectionState();
                    try {
                        com.niuqu.chatbubble.store.GroupStore.clear("play disconnect");
                    } catch (Throwable ignored) {
                    }
                }
            });

        // 配置阶段连接失败（被拒 / 握手失败）：同样要清干净，否则为这次连接注册的通道会留到
        // 下一个服务端。（成功的配置→PLAY 切换不会触发这个事件，只会触发上面的 PLAY 事件。）
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.DISCONNECT
            .register((handler, client) -> {
                try {
                    unregisterCustomChannels("configuration disconnect");
                } catch (Throwable t) {
                    ChatMessageStore.debugLog("[e33chat] channel cleanup at configuration disconnect failed | " + t);
                } finally {
                    connectionPending = false;
                    playSessionSeen = false;
                }
            });

        startStallWatchdog();

        ChatMessageStore.setMessageEffectObserver(
            new com.niuqu.chatbubble.chat.notification.ChatMessageEffects());

        // 26.3: HudRenderCallback 已删除，HUD 改由 fabric-rendering-v1 的 HudElementRegistry 注册。
        // addLast = 排到所有原版 HUD 元素之后，与旧 HudRenderCallback.EVENT（HUD 绘制末尾）等价。
        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath(ChatBubbleMod.MOD_ID, "chat_icon"),
            (drawContext, deltaTracker) -> {
                if (!config.enabled()) return;
                ChatBubbleHudOverlay.render(drawContext);
            });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            lastTickMs = System.currentTimeMillis();
            // 退世界兜底：DISCONNECT 要等本地连接真正关闭（异步），没等到就在这里注销。
            cleanupChannelsAfterWorldExit(client);
            ImageLoader.tick();
            // 纹理全部走 drawTexture(Identifier) 懒加载（getTexture 自动 new ResourceTexture），F3+T 重载后自动重读资源包新 PNG
            if (!config.enabled()) return;
            // 群聊可用性探测：JOIN 后 canSend(群聊 C2S) 为 false → 公屏一条本地系统提示（仅一次）
            pollGroupSupport(client);

            String key;
            if (client.level == null || client.player == null) {
                key = null;
            } else if (client.getSingleplayerServer() != null) {
                key = "SP:" + client.getSingleplayerServer().getWorldData().getLevelName();
            } else if (client.getCurrentServer() != null) {
                key = "MP:" + client.getCurrentServer().name;
            } else {
                key = "world";
            }
            ChatMessageStore.setCurrentWorld(key);
            ChatMessageStore.maybeAutoSave();

            // 睡觉时（BedScreen）左下角同样画着聊天图标，这里也要接受点击
            if (client.gui.screen() == null || client.gui.screen() instanceof com.niuqu.chatbubble.ui.BedScreen) {
                boolean leftDown = client.mouseHandler.isLeftPressed();
                if (leftDown && !leftWasDown) {
                    double mx = client.mouseHandler.xpos() * (double)client.getWindow().getGuiScaledWidth() / (double)client.getWindow().getWidth();
                    double my = client.mouseHandler.ypos() * (double)client.getWindow().getGuiScaledHeight() / (double)client.getWindow().getHeight();
                    if (ChatBubbleHudOverlay.isMouseOverIcon(mx, my)) {
                        client.gui.setScreen(new ChatBubbleScreen(""));
                    }
                }
                leftWasDown = leftDown;
            } else {
                leftWasDown = false;
            }
        });

        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) ->
            ScreenEvents.afterExtract(screen).register((scr, g, mouseX, mouseY, delta) -> {
                if (config.enabled()) ChatBubbleHudOverlay.renderBannerForScreen(g);
            })
        );

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override
                public Identifier getFabricId() {
                    return Identifier.fromNamespaceAndPath(ChatBubbleMod.MOD_ID, "shader_reload");
                }
                @Override
                public void onResourceManagerReload(ResourceManager manager) {
                    RoundRectRenderer.resetShader();
                }
            }
        );
    }
}
