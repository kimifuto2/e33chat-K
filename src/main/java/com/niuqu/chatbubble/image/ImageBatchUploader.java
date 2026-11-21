package com.niuqu.chatbubble.image;

import com.niuqu.chatbubble.ChatBubbleClientSetup;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 上报一批图片（最多 9 张）到服务端媒体或第三方图床，全部成功后才回调。 */
public final class ImageBatchUploader {
    private ImageBatchUploader() {}

    public static void upload(List<File> files,
                              java.util.function.Consumer<List<String>> onDone,
                              Runnable onFailed) {
        List<File> copy = new ArrayList<>(files);
        ImageLoader.executor().execute(() -> {
            List<String> urls = new ArrayList<>(copy.size());
            try {
                for (File f : copy) {
                    LocalImageSource.PreparedImage prep = LocalImageSource.fromFile(f);
                    if (prep == null) { fail(onFailed); return; }
                    String url = MediaClient.serverEnabled()
                        ? MediaClient.upload(prep.bytes(), "image/png")
                        : null;
                    if (url == null) {
                        var cfg = ChatBubbleClientSetup.config();
                        url = ImageUploader.upload(prep.bytes(), prep.fileName(),
                            cfg.uploadUrl(), cfg.uploadField(), cfg.uploadExtra(), cfg.uploadResponse());
                    }
                    if (url == null) { fail(onFailed); return; }
                    urls.add(url);
                }
                MinecraftClient.getInstance().execute(() -> onDone.accept(urls));
            } catch (Throwable t) {
                com.mojang.logging.LogUtils.getLogger().warn("[e33chat] image batch upload failed", t);
                fail(onFailed);
            }
        });
    }

    private static void fail(Runnable onFailed) {
        MinecraftClient.getInstance().execute(onFailed);
    }
}
