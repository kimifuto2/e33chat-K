package com.niuqu.chatbubble.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record HistoryPayload(List<HistoryPayload.HistoryEntry> entries)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HistoryPayload> ID =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("e33chat", "chat_history"));

    public record HistoryEntry(
        UUID senderUUID,
        String senderName,
        String content,
        long time,
        boolean isSystem,
        String replyContent,
        String replySender
    ) {}

    /** 历史条目数的读侧上限（DoS 防护）。 */
    public static final int MAX_ENTRIES = 50;
    public static final int MAX_TEXT = 8192;

    public static final StreamCodec<FriendlyByteBuf, HistoryPayload> CODEC = StreamCodec.ofMember(
        (value, buf) -> {
            List<HistoryEntry> out = value.entries != null ? value.entries : List.of();
            int n = Math.min(out.size(), MAX_ENTRIES);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                HistoryEntry e = out.get(i);
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
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_ENTRIES) return new HistoryPayload(List.of());
                List<HistoryEntry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
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
    public Type<HistoryPayload> type() { return ID; }
}
