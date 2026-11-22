package com.niuqu.chatbubble.paper;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

/**
 * P3：Paper 端的图片消息线格式，与 Fabric 的 ImageMessagePayload / Relay 逐字节一致。
 *
 * <pre>
 * C2S e33chat:image_message
 *   String scope; String groupId; String targetName; String content; String msgHash
 * S2C e33chat:image_message
 *   String scope; String groupId; UUID senderUUID; String senderName; String content; String msgHash
 * </pre>
 */
final class ImageMessageWire {
    static final int MAX_CONTENT = 8192;
    static final int MAX_NAME = 64;

    private ImageMessageWire() {}

    record Inbound(String scope, String groupId, String targetName, String content, String msgHash) {
        boolean isValid() {
            if (content == null || content.isBlank() || content.length() > MAX_CONTENT) return false;
            return switch (GroupWire.str(scope)) {
                case "public" -> true;
                case "group" -> GroupWire.parseUuid(groupId) != null;
                case "private" -> targetName != null && !targetName.isBlank()
                    && targetName.length() <= MAX_NAME;
                default -> false;
            };
        }
    }

    static Inbound decode(byte[] data) {
        try {
            McCodec.Reader in = new McCodec.Reader(data);
            return new Inbound(in.readString(), in.readString(), in.readString(),
                in.readString(), in.readString());
        } catch (Throwable t) {
            return new Inbound("", "", "", "", "");
        }
    }

    static byte[] relay(String scope, String groupId, UUID senderUUID, String senderName,
                        String content, String msgHash) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(128);
        McCodec.writeString(out, GroupWire.clamp(scope, 16));
        McCodec.writeString(out, GroupWire.clamp(groupId, 64));
        McCodec.writeUuid(out, senderUUID);
        McCodec.writeString(out, GroupWire.clamp(senderName, MAX_NAME));
        McCodec.writeString(out, GroupWire.clamp(content, MAX_CONTENT));
        McCodec.writeString(out, GroupWire.clamp(msgHash, 64));
        return out.toByteArray();
    }
}
