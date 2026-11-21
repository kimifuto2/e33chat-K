package com.niuqu.chatbubble.image;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 查看器专用的高清图片加载：与聊天列表的 512px 缩略图分开，最高解码 2048×2048。
 * 只缓存最近 4 张，关闭/淘汰时销毁 GPU 纹理；不参与聊天图片的限流队列（用户主动双击才触发）。
 */
public final class FullImageLoader {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, ImageEntry> CACHE = new HashMap<>();
    private static final Deque<String> LRU = new ArrayDeque<>();
    private static final int CACHE_CAP = 4;
    private static final int MAX_W = 2048;
    private static final int MAX_H = 2048;
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final long FAILED_RETRY_MS = 120_000L;

    private FullImageLoader() {}

    public static ImageEntry getOrLoad(String url) {
        if (url == null || url.isBlank() || !ImageLoader.isUsableUrl(url)) return null;
        synchronized (CACHE) {
            ImageEntry entry = CACHE.get(url);
            if (entry != null) {
                if (entry.state() == ImageEntry.State.FAILED
                    && System.currentTimeMillis() - entry.failedAtMillis() > FAILED_RETRY_MS) {
                    CACHE.remove(url);
                    entry = null;
                } else {
                    touch(url);
                    return entry;
                }
            }
            entry = new ImageEntry(url);
            CACHE.put(url, entry);
            touch(url);
            startLoad(url, entry);
            return entry;
        }
    }

    private static void touch(String url) {
        synchronized (CACHE) {
            LRU.remove(url);
            LRU.addLast(url);
            while (LRU.size() > CACHE_CAP) {
                String oldest = LRU.peekFirst();
                ImageEntry e = CACHE.get(oldest);
                if (e != null && e.state() == ImageEntry.State.LOADING) {
                    // 正在加载的留在队尾，优先淘汰已完成/失败项
                    LRU.remove(oldest);
                    LRU.addLast(oldest);
                    boolean removed = false;
                    Iterator<String> it = LRU.iterator();
                    while (it.hasNext()) {
                        String u = it.next();
                        ImageEntry x = CACHE.get(u);
                        if (x != null && x.state() != ImageEntry.State.LOADING) {
                            it.remove();
                            CACHE.remove(u);
                            destroy(x.textureId());
                            removed = true;
                            break;
                        }
                    }
                    if (!removed) break;
                } else {
                    LRU.removeFirst();
                    CACHE.remove(oldest);
                    if (e != null) destroy(e.textureId());
                }
            }
        }
    }

    private static void startLoad(String url, ImageEntry entry) {
        CompletableFuture.runAsync(() -> fetch(url, entry), ImageLoader.executor());
    }

    private static void fetch(String url, ImageEntry entry) {
        try {
            byte[] body;
            if (url.startsWith("e33chat://")) {
                body = MediaClient.fetch(url.substring("e33chat://media/".length()));
            } else {
                HttpResponse<byte[]> resp = ImageLoader.client().send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    entry.markFailed("http " + resp.statusCode());
                    return;
                }
                body = resp.body();
            }
            if (body == null || body.length == 0 || body.length > MAX_BYTES) {
                entry.markFailed("empty or too large");
                return;
            }
            RasterImageDecoder.DecodedImage decoded = RasterImageDecoder.decode(body);
            if (decoded == null) {
                entry.markFailed("unsupported format");
                return;
            }
            int[] sc = scaled(decoded.width(), decoded.height());
            if (sc[0] != decoded.width() || sc[1] != decoded.height()) {
                NativeImage resized = new NativeImage(NativeImage.Format.RGBA, sc[0], sc[1], false);
                try {
                    decoded.image().resizeSubRectTo(0, 0, decoded.width(), decoded.height(), resized);
                } catch (Throwable t) {
                    resized.close();
                    decoded.image().close();
                    entry.markFailed("scale: " + t);
                    return;
                }
                decoded.image().close();
                decoded = new RasterImageDecoder.DecodedImage(resized, sc[0], sc[1]);
            }
            final RasterImageDecoder.DecodedImage uploaded = decoded;
            Minecraft.getInstance().execute(() -> {
                if (entry.state() != ImageEntry.State.LOADING) {
                    uploaded.image().close();
                    return;
                }
                try {
                    Identifier id = Identifier.fromNamespaceAndPath("e33chat", "img_full/" + Math.abs(url.hashCode()));
                    TextureManager tm = Minecraft.getInstance().getTextureManager();
                    tm.release(id);
                    tm.register(id, new DynamicTexture(() -> "e33chat_full", uploaded.image()));
                    entry.markLoaded(id, uploaded.image());
                } catch (Throwable t) {
                    uploaded.image().close();
                    entry.markFailed("upload: " + t);
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("[e33chat] full image load failed: {}", url, t);
            entry.markFailed(t.toString());
        }
    }

    /** 下载原图字节（保存图片按钮用）；回调在渲染线程。 */
    public static void downloadBytes(String url, java.util.function.Consumer<byte[]> callback) {
        if (url == null || url.isBlank() || !ImageLoader.isUsableUrl(url)) {
            callback.accept(null);
            return;
        }
        ImageLoader.executor().execute(() -> {
            byte[] body = null;
            try {
                if (url.startsWith("e33chat://")) {
                    body = MediaClient.fetch(url.substring("e33chat://media/".length()));
                } else {
                    HttpResponse<byte[]> resp = ImageLoader.client().send(
                        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                    if (resp.statusCode() >= 200 && resp.statusCode() < 300) body = resp.body();
                }
            } catch (Throwable t) {
                LOGGER.warn("[e33chat] image save fetch failed: {}", url, t);
            }
            byte[] out = body;
            Minecraft.getInstance().execute(() -> callback.accept(out));
        });
    }

    private static int[] scaled(int w, int h) {
        if (w <= 0 || h <= 0) return new int[]{1, 1};
        if (w <= MAX_W && h <= MAX_H) return new int[]{w, h};
        double s = Math.min((double) MAX_W / w, (double) MAX_H / h);
        return new int[]{Math.max(1, (int) (w * s)), Math.max(1, (int) (h * s))};
    }

    private static void destroy(Identifier id) {
        if (id == null) return;
        Minecraft.getInstance().execute(() ->
            Minecraft.getInstance().getTextureManager().release(id));
    }
}
