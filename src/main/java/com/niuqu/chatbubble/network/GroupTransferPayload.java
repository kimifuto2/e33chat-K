package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * C2S：群主把群主身份转让给当前成员。转让后原群主仍留在成员表内，身份降为普通成员。
 *
 * <p>线格式：{@code String groupId; UUID target}。
 */
public record GroupTransferPayload(String groupId, UUID target) implements CustomPayload {

    public static final CustomPayload.Id<GroupTransferPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "group_transfer"));

    public static final PacketCodec<PacketByteBuf, GroupTransferPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            GroupWire.writeString(buf, value.groupId);
            buf.writeUuid(value.target == null ? new UUID(0, 0) : value.target);
        },
        buf -> {
            try {
                String groupId = GroupWire.readString(buf);
                UUID target = buf.isReadable() ? buf.readUuid() : null;
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
    public Id<GroupTransferPayload> getId() { return ID; }

    @Override
    public String toString() {
        return "GroupTransferPayload{group=" + groupId + ", target=" + target + "}";
    }
}
