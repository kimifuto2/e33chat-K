package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.texture.UiElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 主题纹理重着色：把 jar 内置的深/浅两套 UI 纹理按**当前预设的色相/饱和度**在内存里重绘一遍，
 * 注册成动态纹理。这样面板/标题栏/底栏/侧边栏不是"叠一层滤镜"，而是纹理本身换色
 * （圆角、描边、明暗层次都保留）。
 *
 * <p>只在 DARK/LIGHT 之外的预设生效；DARK/LIGHT 直接返回原纹理 ID（零开销、原观感）。
 * 生成结果按「预设 + 原纹理路径」缓存，同一预设第二次进入世界不再重算。
 *
 * <p>必须在渲染线程调用（{@code TextureManager.registerTexture} 会创建 GL 纹理）。
 */
public final class ThemeTextureTinter {

    /** key = "<preset>|<original identifier>" → 动态纹理 ID。 */
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private ThemeTextureTinter() {}

    /** 把元素纹理解析成当前主题应使用的 ID（可能被重着色）。 */
    public static Identifier resolve(UiElement el) {
        ChatBubbleTheme theme = Appearance.resolvedTheme();
        return resolve(el.rl(theme));
    }

    /** 指定原纹理 ID 的重着色解析（渲染线程）。 */
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

    /** 主题切换后清空缓存（下次按新预设重新生成；旧纹理留在 TextureManager 里无害）。 */
    public static void clear() {
        CACHE.clear();
    }

    private static Identifier build(Identifier original, ThemePreset preset) {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            Optional<net.minecraft.resource.Resource> res = mc.getResourceManager().getResource(original);
            if (res.isEmpty()) return original;
            NativeImage img;
            try (InputStream in = res.get().getInputStream()) {
                img = NativeImage.read(in);
            }
            int w = img.getWidth();
            int h = img.getHeight();
            int[] pixels = img.copyPixelsArgb();
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = preset.recolor(pixels[i]);
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    img.setColorArgb(x, y, pixels[y * w + x]);
                }
            }
            String name = original.getPath();
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
            name = name.replace(".png", "");
            Identifier id = Identifier.of("e33chat", "dynamic/theme/" + preset.key() + "/" + name + ".png");
            mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> id.toString(), img));
            return id;
        } catch (Exception e) {
            // 生成失败就退回原纹理，绝不让主题把界面画崩
            return original;
        }
    }
}
