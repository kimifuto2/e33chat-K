package com.niuqu.chatbubble.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * C2S：群主把群主身份转让给当前成员。转让后原群主仍留在成员表内，身份降为普通成员。
 *
 * <p>线格式：{@code String groupId; UUID target}。
 */
public record GroupTransferPayload(String groupId, UUID target) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GroupTransferPayload> ID =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("e33chat", "group_transfer"));

    public static final StreamCodec<FriendlyByteBuf, GroupTransferPayload> CODEC = StreamCodec.ofMember(
        (value, buf) -> {
            GroupWire.writeString(buf, value.groupId);
            buf.writeUUID(value.target == null ? new UUID(0, 0) : value.target);
        },
        buf -> {
            try {
                String groupId = GroupWire.readString(buf);
                UUID target = buf.isReadable() ? buf.readUUID() : null;
                return new GroupTransferPayload(groupId, target);
            } catch (Throwable t) {
                return new GroupTransferPayload("", null);
            }
        }
    );

    public boolean isValid() {
        return GroupWire.parseUuid(groupId) != null && target != null;
    }

    @Override
    public Type<GroupTransferPayload> type() { return ID; }

    @Override
    public String toString() {
        return "GroupTransferPayload{group=" + groupId + ", target=" + target + "}";
    }
}
