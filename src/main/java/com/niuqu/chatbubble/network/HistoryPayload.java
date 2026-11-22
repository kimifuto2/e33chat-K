package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.network.GroupWire;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record HistoryPayload(List<HistoryPayload.HistoryEntry> entries)
        implements CustomPayload {

    public static final CustomPayload.Id<HistoryPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "chat_history"));

    public record HistoryEntry(
        UUID senderUUID,
        String senderName,
        String content,
        long time,
        boolean isSystem,
        String replyContent,
        String replySender
    ) {}

    public static final int MAX_ENTRIES = 50;
    public static final int MAX_TEXT = 8192;

    public static final PacketCodec<PacketByteBuf, HistoryPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            List<HistoryEntry> entries = value.entries == null ? List.of() : value.entries;
            int n = Math.min(entries.size(), MAX_ENTRIES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                HistoryEntry e = entries.get(i);
                GroupWire.writeString(buf, e.senderUUID() == null ? "" : e.senderUUID().toString(), 64);
                GroupWire.writeString(buf, e.senderName(), 64);
                GroupWire.writeString(buf, e.content(), MAX_TEXT);
                buf.writeLong(e.time());
                buf.writeBoolean(e.isSystem());
                GroupWire.writeString(buf, e.replyContent() != null ? e.replyContent() : "", MAX_TEXT);
                GroupWire.writeString(buf, e.replySender() != null ? e.replySender() : "", 64);
            }
        },
        buf -> {
            try {
                int n = buf.readVarInt();
                if (n < 0 || n > MAX_ENTRIES) return new HistoryPayload(List.of());
                List<HistoryEntry> entries = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    entries.add(new HistoryEntry(
                        UUID.fromString(GroupWire.readString(buf, 64)),
                        GroupWire.readString(buf, 64),
                        GroupWire.readString(buf, MAX_TEXT),
                        buf.readLong(),
                        buf.readBoolean(),
                        nullOrEmpty(GroupWire.readString(buf, MAX_TEXT)),
                        nullOrEmpty(GroupWire.readString(buf, 64))
                    ));
                }
                return new HistoryPayload(entries);
            } catch (Throwable t) {
                return new HistoryPayload(List.of());
            }
        }
    );

    private static String nullOrEmpty(String s) { return s == null || s.isEmpty() ? null : s; }

    @Override
    public Id<HistoryPayload> getId() { return ID; }
}
