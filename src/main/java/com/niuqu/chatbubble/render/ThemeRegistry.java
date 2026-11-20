package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.ChatBubbleClientSetup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主题注册表：id → {@link ThemePalette}。
 *
 * <p>阶段 1 只注册 dark/light 两条（行为与改造前完全一致）；后续阶段可继续
 * {@link #register} 新的配色，配置里存的就是 palette id。
 * 未知 id 回退到按明暗模式匹配的默认主题（保持旧配置兼容）。
 */
public final class ThemeRegistry {

    private static final Map<String, ThemePalette> PALETTES = new LinkedHashMap<>();

    static {
        register(new ThemePalette("dark", ThemeMode.DARK, "e33chat.theme.dark", 0,
                ChatBubbleTheme.DARK.colors(), ThemeAssets.of("dark")));
        register(new ThemePalette("light", ThemeMode.LIGHT, "e33chat.theme.light", 1,
                ChatBubbleTheme.LIGHT.colors(), ThemeAssets.of("light")));
    }

    private ThemeRegistry() {}

    public static void register(ThemePalette palette) {
        if (palette != null && palette.id() != null) {
            PALETTES.put(palette.id(), palette);
        }
    }

    /** 当前配置的主题；未知 id（含旧值的大小写变体）按明暗模式回退。 */
    public static ThemePalette current() {
        return byId(ChatBubbleClientSetup.config().theme());
    }

    public static ThemePalette byId(String id) {
        if (id != null) {
            ThemePalette p = PALETTES.get(id);
            if (p != null) return p;
        }
        String modeKey = ThemeMode.parse(id).key();
        ThemePalette fallback = PALETTES.get(modeKey);
        return fallback != null ? fallback : PALETTES.get("dark");
    }

    public static List<ThemePalette> all() {
        List<ThemePalette> list = new ArrayList<>(PALETTES.values());
        list.sort(Comparator.comparingInt(ThemePalette::sortOrder));
        return list;
    }
}
