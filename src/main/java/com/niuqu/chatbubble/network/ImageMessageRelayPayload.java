package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * S2C：服务端把一条图片消息转发给范围内的接收者（**不含发送者**，发送者本地回显）。
 *
 * <p>线格式与 C2S 的 {@link ImageMessagePayload} 对应，额外带发送者身份：
 * <pre>
 *   String scope
 *   String groupId
 *   UUID senderUUID
 *   String senderName   // 原始玩家名（不做颜色/前缀解析）
 *   String content
 *   String msgHash
 * </pre>
 */
public record ImageMessageRelayPayload(String scope, String groupId, UUID senderUUID,
                                       String senderName, String content, String msgHash) implements CustomPayload {
    public static final CustomPayload.Id<ImageMessageRelayPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "image_message_relay"));

    public static final PacketCodec<PacketByteBuf, ImageMessageRelayPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            GroupWire.writeString(buf, GroupWire.clamp(value.scope, 16));
            GroupWire.writeString(buf, GroupWire.clamp(value.groupId, 64));
            buf.writeUuid(value.senderUUID == null ? new UUID(0, 0) : value.senderUUID);
            GroupWire.writeString(buf, GroupWire.clamp(value.senderName, ImageMessagePayload.MAX_NAME));
            GroupWire.writeString(buf, GroupWire.clamp(value.content, ImageMessagePayload.MAX_CONTENT));
            GroupWire.writeString(buf, GroupWire.clamp(value.msgHash, 64));
        },
        buf -> {
            try {
                return new ImageMessageRelayPayload(
                    GroupWire.readString(buf), GroupWire.readString(buf),
                    buf.isReadable() ? buf.readUuid() : null,
                    GroupWire.readString(buf), GroupWire.readString(buf), GroupWire.readString(buf));
            } catch (Throwable t) {
                return new ImageMessageRelayPayload("", "", null, "", "", "");
            }
        }
    );

    public boolean isValid() {
        return senderUUID != null && senderName != null && content != null
            && !content.isBlank() && content.length() <= ImageMessagePayload.MAX_CONTENT;
    }

    @Override
    public Id<ImageMessageRelayPayload> getId() { return ID; }

    @Override
    public String toString() {
        return "ImageMessageRelayPayload{scope=" + scope + ", group=" + groupId
            + ", sender=" + senderName + ", len=" + (content == null ? 0 : content.length()) + "}";
    }
}
