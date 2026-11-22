package com.niuqu.chatbubble.server;

import com.niuqu.chatbubble.network.GroupWire;
import com.niuqu.chatbubble.network.ImageMessagePayload;
import com.niuqu.chatbubble.network.ImageMessageRelayPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * P2：图片消息服务端中继。一条载荷最多 9 张图，不再受原版聊天 256 字符长度限制。
 *
 * <p>发送者本地先回显；服务端只发给范围内的**其他**在线玩家，避免重复气泡。
 */
public final class ImageMessageServer {
    private ImageMessageServer() {}

    public static void handle(MinecraftServer server, ServerPlayerEntity sender, ImageMessagePayload payload) {
        if (server == null || sender == null || payload == null || !payload.isValid()) {
            log("ignored | invalid payload | sender=" + name(sender) + " | " + payload);
            return;
        }
        String content = GroupWire.clamp(payload.content(), ImageMessagePayload.MAX_CONTENT);
        if (content.isBlank()) return;
        String msgHash = GroupWire.clamp(payload.msgHash(), 64);
        if (msgHash.isEmpty() || !msgHash.chars().allMatch(c -> (c >= '0' && c <= '9') || c == '-')) {
            msgHash = String.valueOf(content.hashCode());
        }
        ImageMessageRelayPayload relay = new ImageMessageRelayPayload(
            payload.scope(), payload.groupId(), sender.getUuid(),
            sender.getName().getString(), content, msgHash);

        switch (payload.scope()) {
            case ImageMessagePayload.SCOPE_PUBLIC -> {
                int n = 0;
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if (p == sender) continue;
                    if (send(p, relay)) n++;
                }
                log("public | from=" + name(sender) + " | recipients=" + n + " | hash=" + msgHash);
            }
            case ImageMessagePayload.SCOPE_PRIVATE -> {
                ServerPlayerEntity target = server.getPlayerManager().getPlayer(payload.targetName());
                if (target == null || target == sender) {
                    log("private ignored | target offline/self | from=" + name(sender)
                        + " | target=" + payload.targetName());
                    return;
                }
                send(target, relay);
                log("private | from=" + name(sender) + " | target=" + name(target) + " | hash=" + msgHash);
            }
            case ImageMessagePayload.SCOPE_GROUP ->
                GroupRegistry.relayImage(server, sender, payload);
            default -> log("ignored | unknown scope=" + payload.scope());
        }
    }

    private static boolean send(ServerPlayerEntity player, ImageMessageRelayPayload relay) {
        if (player == null) return false;
        try {
            ServerPlayNetworking.send(player, relay);
            return true;
        } catch (Throwable t) {
            log("send failed | player=" + name(player) + " | " + t);
            return false;
        }
    }

    private static String name(ServerPlayerEntity p) {
        return p == null ? "?" : p.getName().getString();
    }

    private static void log(String message) {
        com.mojang.logging.LogUtils.getLogger().info("[e33chat] image_message | " + message);
    }
}
