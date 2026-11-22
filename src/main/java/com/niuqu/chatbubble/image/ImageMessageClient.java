package com.niuqu.chatbubble.image;

import com.niuqu.chatbubble.network.ImageMessagePayload;
import com.niuqu.chatbubble.network.ImageMessageRelayPayload;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.store.GroupStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * P2/P3 客户端桥接：把图片消息载荷落成普通图片消息（复用现有气泡/图片/历史管线）。
 *
 * <p>发送者本地先回显，服务端只转发给其他接收者，所以不需要 msgHash 去重表。
 */
public final class ImageMessageClient {
    private ImageMessageClient() {}

    /** 发送成功后，把服务端将发出去的同一份内容落成自己的本地消息。 */
    public static ChatMessageStore.ChatMessage addLocal(ImageMessagePayload payload) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || payload == null || !payload.isValid()) return null;
        UUID self = mc.player.getUuid();
        String rawName = mc.player.getName().getString();
        Text own = ChatMessageStore.ownDisplayName();
        Text content = Text.literal(payload.content());
        String hash = payload.msgHash() == null || payload.msgHash().isBlank()
            ? String.valueOf(payload.content().hashCode()) : payload.msgHash();
        return switch (payload.scope()) {
            case ImageMessagePayload.SCOPE_GROUP ->
                ChatMessageStore.addGroupMessage(parseGroupId(payload.groupId()), self, own,
                    content, rawName, true, hash);
            case ImageMessagePayload.SCOPE_PRIVATE -> ChatMessageStore.addMessage(content, self, own,
                false, rawName, true, payload.targetName(), true);
            default -> ChatMessageStore.addMessage(content, self, own,
                false, rawName, false, null, true);
        };
    }

    /** S2C：把别人发来的图片消息落进对应会话。 */
    public static void receive(ImageMessageRelayPayload payload) {
        if (payload == null || !payload.isValid()) return;
        Text content = Text.literal(payload.content());
        switch (payload.scope()) {
            case ImageMessagePayload.SCOPE_GROUP -> GroupStore.receiveImage(payload);
            case ImageMessagePayload.SCOPE_PRIVATE -> ChatMessageStore.addMessage(content,
                payload.senderUUID(), Text.literal(payload.senderName()),
                false, payload.senderName(), true, payload.senderName(), false);
            default -> ChatMessageStore.addMessage(content, payload.senderUUID(),
                Text.literal(payload.senderName()), false, payload.senderName(), false, null, false);
        }
    }

    private static UUID parseGroupId(String id) {
        try {
            return id == null ? null : UUID.fromString(id);
        } catch (Throwable t) {
            return null;
        }
    }
}
