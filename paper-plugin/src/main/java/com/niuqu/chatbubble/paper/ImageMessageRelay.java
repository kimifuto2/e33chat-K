package com.niuqu.chatbubble.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** P3：Paper 侧图片消息转发（公开 / 群聊 / 私聊），不含发送者。 */
final class ImageMessageRelay {
    private ImageMessageRelay() {}

    static void handle(Plugin plugin, Player sender, ImageMessageWire.Inbound payload) {
        if (plugin == null || sender == null || payload == null || !payload.isValid()) return;
        String content = GroupWire.clamp(payload.content(), ImageMessageWire.MAX_CONTENT);
        if (content.isBlank()) return;
        String hash = GroupWire.normalizeHash(payload.msgHash(), content);
        byte[] relay = ImageMessageWire.relay(payload.scope(), payload.groupId(),
            sender.getUniqueId(), sender.getName(), content, hash);
        switch (payload.scope()) {
            case "public" -> {
                int n = 0;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.equals(sender)) continue;
                    if (send(plugin, p, relay)) n++;
                }
                plugin.getLogger().info("[E33Chat] image_message public | from=" + sender.getName()
                    + " | recipients=" + n + " | hash=" + hash);
            }
            case "private" -> {
                Player target = Bukkit.getPlayerExact(payload.targetName());
                if (target == null || target.equals(sender)) {
                    plugin.getLogger().info("[E33Chat] image_message private ignored | from="
                        + sender.getName() + " | target=" + payload.targetName());
                    return;
                }
                send(plugin, target, relay);
                plugin.getLogger().info("[E33Chat] image_message private | from=" + sender.getName()
                    + " | target=" + target.getName() + " | hash=" + hash);
            }
            case "group" -> GroupRegistry.relayImage(plugin, sender, payload, relay, hash);
            default -> plugin.getLogger().info("[E33Chat] image_message ignored | scope=" + payload.scope());
        }
    }

    private static boolean send(Plugin plugin, Player player, byte[] payload) {
        try {
            player.sendPluginMessage(plugin, E33Channels.IMAGE_MESSAGE_RELAY, payload);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[E33Chat] image_message send failed | player="
                + player.getName() + " | " + t);
            return false;
        }
    }
}
