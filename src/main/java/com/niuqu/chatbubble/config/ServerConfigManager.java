package com.niuqu.chatbubble.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class ServerConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private ServerConfigManager() {}

    public static ServerConfig load(Path path) {
        if (Files.exists(path)) {
            try (Reader r = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                ServerConfig loaded = GSON.fromJson(r, ServerConfig.class);
                if (loaded != null) return loaded;
            } catch (Exception e) {
                try {
                    Path backup = path.resolveSibling(path.getFileName() + ".corrupt-"
                        + System.currentTimeMillis());
                    Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                    // 保留原文件；下面写默认值仍可能覆盖，失败时 load 会再次回退
                }
            }
        }
        ServerConfig def = ServerConfig.defaults();
        save(path, def);
        return def;
    }

    public static void save(Path path, ServerConfig config) {
        if (path == null || config == null) return;
        Path tmp = null;
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            tmp = path.resolveSibling(path.getFileName() + ".tmp");
            byte[] bytes = GSON.toJson(config).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignored) { }
            }
        }
    }
}
