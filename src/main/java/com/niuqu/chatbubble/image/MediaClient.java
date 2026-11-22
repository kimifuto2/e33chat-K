package com.niuqu.chatbubble.image;

import com.mojang.logging.LogUtils;
import com.niuqu.chatbubble.network.MediaRequestPayload;
import com.niuqu.chatbubble.network.MediaResponsePayload;
import com.niuqu.chatbubble.network.MediaUploadAckPayload;
import com.niuqu.chatbubble.network.MediaUploadPayload;
import com.niuqu.chatbubble.server.DiskMediaStore;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Client side of the server media hosting feature (2.3.13).
 *
 * Capability: the server advertises media hosting in ConfigSyncV2
 * (mediaEnabled). When enabled, chat image uploads go to the server
 * (e33chat://media/<id>, permanent) instead of the third-party host; when
 * disabled or absent, callers fall back to the existing ImageUploader path.
 *
 * Both upload and fetch are blocking with a 30s timeout and must be called on
 * a worker thread (not the render thread). Packets are sent on the render
 * thread via execute() to stay thread-safe; replies are matched by uploadId /
 * mediaId futures.
 */
public final class MediaClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TIMEOUT_SECONDS = 30;

    private static volatile boolean serverEnabled;
    private static final Map<Long, CompletableFuture<String>> UPLOADS = new ConcurrentHashMap<>();
    private static final Map<String, FetchState> FETCHES = new ConcurrentHashMap<>();

    private static final class FetchState {
        final CompletableFuture<byte[]> future = new CompletableFuture<>();
        byte[][] chunks;
        int received;
        FetchState(int total) { chunks = total > 0 ? new byte[total][] : null; }
    }

    private MediaClient() {}

    public static void setServerEnabled(boolean b) { serverEnabled = b; }
    public static boolean serverEnabled() { return serverEnabled; }

    /** 连接边界：清空本连接的媒体能力与所有在途 future，避免换服后沿用旧状态。 */
    public static void resetConnectionState() {
        serverEnabled = false;
        RuntimeException reset = new RuntimeException("media connection reset");
        for (CompletableFuture<String> f : UPLOADS.values()) f.completeExceptionally(reset);
        UPLOADS.clear();
        for (FetchState s : FETCHES.values()) s.future.completeExceptionally(reset);
        FETCHES.clear();
    }

    /** Client-side receivers; registered from ChatBubbleClientSetup. */
    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(MediaUploadAckPayload.ID, (payload, context) -> {
            CompletableFuture<String> f = UPLOADS.remove(payload.uploadId());
            if (f != null) {
                f.complete(payload.error() == null ? payload.mediaId() : null);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(MediaResponsePayload.ID, (payload, context) -> {
            String id = payload.mediaId();
            if (id == null) return;
            if (payload.totalChunks() == 1 && payload.chunk().length == 0) {
                FetchState state = FETCHES.remove(id);
                if (state != null) state.future.completeExceptionally(
                    new RuntimeException("media not found: " + id));
                return;
            }
            FetchState state = FETCHES.get(id);
            if (state == null) return;
            int total = payload.totalChunks();
            if (total <= 0 || payload.index() < 0 || payload.index() >= total) return;
            if (state.chunks == null || state.chunks.length != total) state.chunks = new byte[total][];
            if (state.chunks[payload.index()] != null) return;
            state.chunks[payload.index()] = payload.chunk();
            state.received++;
            if (state.received == total) {
                FETCHES.remove(id, state);
                int size = 0;
                boolean complete = true;
                for (byte[] c : state.chunks) {
                    if (c == null) { complete = false; break; }
                    size += c.length;
                }
                if (!complete) {
                    state.future.completeExceptionally(new RuntimeException("media chunk missing: " + id));
                } else {
                    byte[] all = new byte[size];
                    int off = 0;
                    for (byte[] c : state.chunks) {
                        System.arraycopy(c, 0, all, off, c.length);
                        off += c.length;
                    }
                    state.future.complete(all);
                }
            }
        });
    }

    /**
     * Upload bytes to the server. Worker-thread only. Returns the
     * e33chat://media/<id> URL, or null on any failure (caller falls back).
     */
    public static String upload(byte[] bytes, String contentType) {
        if (!serverEnabled || bytes == null || bytes.length == 0) return null;
        if (!ClientPlayNetworking.canSend(MediaUploadPayload.ID)) return null;
        long uploadId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        int totalChunks = DiskMediaStore.totalChunksFor(bytes.length);
        CompletableFuture<String> done = new CompletableFuture<>();
        UPLOADS.put(uploadId, done);
        for (int i = 0; i < totalChunks; i++) {
            int from = i * DiskMediaStore.CHUNK_BYTES;
            int len = Math.min(DiskMediaStore.CHUNK_BYTES, bytes.length - from);
            byte[] chunk = new byte[len];
            System.arraycopy(bytes, from, chunk, 0, len);
            final int idx = i;
            MinecraftClient.getInstance().execute(() -> {
                try {
                    ClientPlayNetworking.send(new MediaUploadPayload(uploadId, idx, totalChunks,
                        bytes.length, contentType, chunk));
                } catch (Throwable t) {
                    UPLOADS.remove(uploadId);
                    done.complete(null);
                }
            });
        }
        try {
            String mediaId = done.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return mediaId != null ? "e33chat://media/" + mediaId : null;
        } catch (Exception e) {
            UPLOADS.remove(uploadId);
            LOGGER.info("[e33chat] server media upload timed out after {}s", TIMEOUT_SECONDS);
            return null;
        }
    }

    /** Download a server-hosted file. Worker-thread only. Returns raw bytes or null. */
    public static byte[] fetch(String mediaId) {
        if (!DiskMediaStore.isValidMediaId(mediaId)) return null;
        if (!ClientPlayNetworking.canSend(MediaRequestPayload.ID)) return null;
        FetchState created = new FetchState(1);
        FetchState previous = FETCHES.putIfAbsent(mediaId, created);
        final FetchState state = previous != null ? previous : created;
        if (previous == null) {
            MinecraftClient.getInstance().execute(() -> {
                try {
                    ClientPlayNetworking.send(new MediaRequestPayload(mediaId));
                } catch (Throwable t) {
                    FETCHES.remove(mediaId, state);
                    state.future.completeExceptionally(t);
                }
            });
        }
        try {
            return state.future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            FETCHES.remove(mediaId, state);
            state.future.completeExceptionally(e);
            LOGGER.info("[e33chat] server media fetch {} timed out after {}s", mediaId, TIMEOUT_SECONDS);
            return null;
        }
    }
}
