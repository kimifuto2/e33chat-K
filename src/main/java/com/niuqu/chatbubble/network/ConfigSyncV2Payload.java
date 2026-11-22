package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.network.GroupWire;

import com.niuqu.chatbubble.store.ChatMessageStore;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Server -> client sync of server-side settings (v2: adds message-format templates). */
public record ConfigSyncV2Payload(boolean useTpa, List<String> chatTemplates,
                                  List<String> whisperTemplates, boolean templateDebug)
        implements CustomPayload {

    public static final CustomPayload.Id<ConfigSyncV2Payload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "config_sync_v2"));

    public static final PacketCodec<PacketByteBuf, ConfigSyncV2Payload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeBoolean(value.useTpa);
            writeList(buf, value.chatTemplates);
            writeList(buf, value.whisperTemplates);
            buf.writeBoolean(value.templateDebug);
        },
        buf -> new ConfigSyncV2Payload(
            buf.readBoolean(),
            readList(buf),
            readList(buf),
            buf.readBoolean()
        )
    );

    public static final int MAX_LIST = 64;
    public static final int MAX_ITEM = 512;

    static List<String> readList(PacketByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > MAX_LIST) {
            throw new IllegalArgumentException("bad template count: " + count);
        }
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(GroupWire.readString(buf, MAX_ITEM));
        return out;
    }

    static void writeList(PacketByteBuf buf, List<String> list) {
        List<String> src = list == null ? List.of() : list;
        int n = Math.min(src.size(), MAX_LIST);
        buf.writeInt(n);
        for (int i = 0; i < n; i++) GroupWire.writeString(buf, src.get(i), MAX_ITEM);
    }

    @Override
    public Id<ConfigSyncV2Payload> getId() { return ID; }

    public static void handle(ConfigSyncV2Payload payload) {
        ChatMessageStore.setServerConfig(
            payload.useTpa(), payload.chatTemplates(), payload.whisperTemplates(), payload.templateDebug());
    }
}
