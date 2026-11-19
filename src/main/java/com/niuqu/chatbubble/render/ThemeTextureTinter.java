package com.niuqu.chatbubble.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.niuqu.chatbubble.texture.UiElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主题纹理重着色（26.x）：把 jar 内置的深/浅两套 UI 纹理按当前预设的色相/饱和度在内存里
 * 重绘一遍，注册成动态纹理 —— 面板等是纹理本身换色，而不是叠一层滤镜。
 * DARK/LIGHT 之外的预设才生效，结果按「预设 + 原纹理路径」缓存。
 */
public final class ThemeTextureTinter {

    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private ThemeTextureTinter() {}

    public static Identifier resolve(UiElement el) {
        return resolve(el.rl(Appearance.resolvedTheme()));
    }

    public static Identifier resolve(Identifier original) {
        ThemePreset preset = ThemePreset.current();
        if (preset == null || !preset.recolorsTextures()) return original;
        String key = preset.key() + "|" + original;
        Identifier cached = CACHE.get(key);
        if (cached != null) return cached;
        Identifier built = build(original, preset);
        CACHE.put(key, built);
        return built;
    }

    public static void clear() {
        CACHE.clear();
    }

    private static Identifier build(Identifier original, ThemePreset preset) {
        Minecraft mc = Minecraft.getInstance();
        try {
            List<Resource> stack = mc.getResourceManager().getResourceStack(original);
            if (stack.isEmpty()) return original;
            NativeImage img;
            try (InputStream in = stack.get(0).open()) {
                img = NativeImage.read(in);
            }
            int w = img.getWidth();
            int h = img.getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = abgrToArgb(img.getPixel(x, y));
                    img.setPixel(x, y, argbToAbgr(preset.recolor(argb)));
                }
            }
            String name = original.getPath();
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
            name = name.replace(".png", "");
            Identifier id = Identifier.fromNamespaceAndPath("e33chat",
                "dynamic/theme/" + preset.key() + "/" + name + ".png");
            mc.getTextureManager().register(id, new DynamicTexture(() -> id.toString(), img));
            return id;
        } catch (Exception e) {
            return original;
        }
    }

    private static int abgrToArgb(int abgr) {
        return (abgr & 0xFF00FF00) | ((abgr & 0x00FF0000) >>> 16) | ((abgr & 0x000000FF) << 16);
    }

    private static int argbToAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
    }
}
