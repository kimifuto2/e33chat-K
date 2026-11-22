package com.niuqu.chatbubble.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S：一次发送最多 9 张图片 + 文字说明，走专用载荷而不是聊天文本。
 *
 * <p>这是 P2 的核心：聊天文本有 256 字符上限，9 个图片 URL 很容易超长并被拆成多条；
 * 本载荷把整条图片消息一次性交给服务端，由服务端按 scope 转发，不再受聊天长度限制。
 * Paper 插件实现同一线格式（P3）。
 *
 * <p>字段顺序固定、只允许末尾追加：
 * <pre>
 *   String scope       // public / group / private
 *   String groupId     // group 时有效
 *   String targetName  // private 时有效（在线玩家名）
 *   String content     // 说明 + [[CICode,url=...]] 图片代码
 *   String msgHash     // 明文 String.hashCode() 十进制串
 * </pre>
 */
public record ImageMessagePayload(String scope, String groupId, String targetName,
                                  String content, String msgHash) implements CustomPacketPayload {
    public static final String SCOPE_PUBLIC = "public";
    public static final String SCOPE_GROUP = "group";
    public static final String SCOPE_PRIVATE = "private";
    public static final int MAX_CONTENT = 8192;
    public static final int MAX_NAME = 64;

    public static final CustomPacketPayload.Type<ImageMessagePayload> ID =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("e33chat", "image_message"));

    public static final StreamCodec<FriendlyByteBuf, ImageMessagePayload> CODEC = StreamCodec.ofMember(
        (value, buf) -> {
            GroupWire.writeString(buf, GroupWire.clamp(value.scope, 16));
            GroupWire.writeString(buf, GroupWire.clamp(value.groupId, 64));
            GroupWire.writeString(buf, GroupWire.clamp(value.targetName, MAX_NAME));
            GroupWire.writeString(buf, GroupWire.clamp(value.content, MAX_CONTENT), MAX_CONTENT);
            GroupWire.writeString(buf, GroupWire.clamp(value.msgHash, 64));
        },
        buf -> {
            try {
                return new ImageMessagePayload(
                    GroupWire.readString(buf), GroupWire.readString(buf), GroupWire.readString(buf),
                    GroupWire.readString(buf, ImageMessagePayload.MAX_CONTENT), GroupWire.readString(buf));
            } catch (Throwable t) {
                return new ImageMessagePayload("", "", "", "", "");
            }
        }
    );

    public boolean isValid() {
        if (scope == null || content == null || content.isBlank()
            || content.length() > MAX_CONTENT) return false;
        return switch (scope) {
            case SCOPE_PUBLIC -> true;
            case SCOPE_GROUP -> GroupWire.parseUuid(groupId) != null;
            case SCOPE_PRIVATE -> targetName != null && !targetName.isBlank()
                && targetName.length() <= MAX_NAME;
            default -> false;
        };
    }

    @Override
    public Type<ImageMessagePayload> type() { return ID; }

    @Override
    public String toString() {
        return "ImageMessagePayload{scope=" + scope + ", group=" + groupId
            + ", target=" + targetName + ", len=" + (content == null ? 0 : content.length()) + "}";
    }
}
