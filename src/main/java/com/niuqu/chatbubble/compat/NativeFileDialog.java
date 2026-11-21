package com.niuqu.chatbubble.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import javax.swing.SwingUtilities;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AWT FileDialog-based image picker, mirroring the modal picker pattern:
 * runs on a worker thread + EDT so the render thread never blocks; MC key/mouse
 * state is released while the dialog owns input and the render thread resumes normally.
 */
public final class NativeFileDialog {
    private static boolean open;

    private NativeFileDialog() {}

    /** Opens a save dialog and writes image bytes. */
    public static void saveImage(byte[] bytes, String suggestedName) {
        if (bytes == null || bytes.length == 0 || open) return;
        open = true;
        Thread t = new Thread(() -> {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    FileDialog fd = new FileDialog((Frame) null, "Save image", FileDialog.SAVE);
                    String ext = ".png";
                    if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) ext = ".jpg";
                    else if (bytes.length > 3 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') ext = ".gif";
                    String name = suggestedName == null || suggestedName.isBlank() ? "e33chat-image" : suggestedName;
                    int dot = name.lastIndexOf('.');
                    if (dot > 0) name = name.substring(0, dot);
                    fd.setFile(name + ext);
                    fd.setVisible(true);
                    String dir = fd.getDirectory();
                    String file = fd.getFile();
                    if (dir != null && file != null) {
                        if (!file.toLowerCase().endsWith(ext)) file += ext;
                        try {
                            java.nio.file.Files.write(new File(dir, file).toPath(), bytes);
                        } catch (Exception e) {
                            com.mojang.logging.LogUtils.getLogger().warn("[e33chat] save image failed", e);
                        }
                    }
                    fd.dispose();
                });
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("[e33chat] save dialog failed", e);
            } finally {
                open = false;
            }
        }, "e33chat-save-image");
        t.setDaemon(true);
        t.start();
    }

    /** Opens a multi-select image picker; result is an empty list on cancel. */
    public static void pickImages(java.util.function.Consumer<List<File>> callback) {
        if (open) return;
        open = true;
        KeyBinding.unpressAll();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.mouse != null) ((com.niuqu.chatbubble.mixin.MouseHandlerAccessor) mc.mouse).e33chat$setActiveButton(null);

        Thread t = new Thread(() -> {
            java.util.concurrent.atomic.AtomicReference<List<File>> picked = new java.util.concurrent.atomic.AtomicReference<>(List.of());
            try {
                SwingUtilities.invokeAndWait(() -> {
                    FileDialog fd = new FileDialog((Frame) null, "Select image(s)");
                    fd.setMultipleMode(true);
                    fd.setFilenameFilter((dir, name) -> {
                        String n = name.toLowerCase();
                        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                            || n.endsWith(".gif") || n.endsWith(".bmp");
                    });
                    fd.setVisible(true);
                    File[] files = fd.getFiles();
                    if (files != null && files.length > 0) picked.set(java.util.Arrays.asList(files));
                    fd.dispose();
                });
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("[e33chat] File dialog failed", e);
            } finally {
                open = false;
            }
            List<File> result = picked.get();
            mc.execute(() -> callback.accept(result));
        }, "e33chat-file-dialog-images");
        t.setDaemon(true);
        t.start();
    }

    /** Opens a modal image picker; the result (or null on cancel) is delivered on the render thread. */
    public static void pickImage(java.util.function.Consumer<File> callback) {
        if (open) return;
        open = true;
        KeyBinding.unpressAll();
        MinecraftClient mc = MinecraftClient.getInstance();
        // MC keeps thinking the button is held while the dialog grabs input;
        // clear it so release state restores cleanly after the dialog closes
        if (mc.mouse != null) ((com.niuqu.chatbubble.mixin.MouseHandlerAccessor) mc.mouse).e33chat$setActiveButton(null);

        Thread t = new Thread(() -> {
            AtomicReference<File> picked = new AtomicReference<>();
            try {
                SwingUtilities.invokeAndWait(() -> {
                    FileDialog fd = new FileDialog((Frame) null, "Select emote image");
                    fd.setFilenameFilter((dir, name) -> {
                        String n = name.toLowerCase();
                        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
                    });
                    fd.setVisible(true);
                    File[] files = fd.getFiles();
                    if (files != null && files.length > 0) picked.set(files[0]);
                    fd.dispose();
                });
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("[e33chat] File dialog failed", e);
            } finally {
                open = false;
            }
            File result = picked.get();
            mc.execute(() -> callback.accept(result));
        }, "e33chat-file-dialog");
        t.setDaemon(true);
        t.start();
    }
}
