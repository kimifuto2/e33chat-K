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
 * <p>dark/light 保留原有纹理观感；{@code sakura} 是第一个「代码皮肤」主题
 * （assets = null，颜色全部手写，不依赖烘焙纹理）。
 * 未知配置 id 回退到按明暗模式匹配的默认主题（兼容旧配置）。
 */
public final class ThemeRegistry {

    private static final Map<String, ThemePalette> PALETTES = new LinkedHashMap<>();

    static {
        register(new ThemePalette("dark", ThemeMode.DARK, "e33chat.theme.dark", 0,
                ChatBubbleTheme.DARK.colors(), ThemeAssets.of("dark")));
        register(new ThemePalette("light", ThemeMode.LIGHT, "e33chat.theme.light", 1,
                ChatBubbleTheme.LIGHT.colors(), ThemeAssets.of("light")));
        register(new ThemePalette("sakura", ThemeMode.LIGHT, "e33chat.theme.sakura", 2,
                sakuraColors(), null,
                new ThemePalette.BubbleColors(0xFFC2407A, 0xFFFFF5F8, 0xFFF6E1E7, 0xFF3A2830)));
    }

    private ThemeRegistry() {}

    public static void register(ThemePalette palette) {
        if (palette != null && palette.id() != null) {
            PALETTES.put(palette.id(), palette);
        }
    }

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

    /** 樱花粉：浅色底 + 粉调面板，文字用深玫瑰灰保证对比度。 */
    private static ChatBubbleTheme.Colors sakuraColors() {
        return new ChatBubbleTheme.Colors(
            0xFFFDF5F7, // panelBg
            0xFFFFF9FB, // titleBg
            0xFFFAEEF2, // barBg
            0xFFFCF3F6, // sidebarBg
            0xFFF7E6EC, // sidebarItemHover
            0xFFF2D4DF, // sidebarItemSelected
            0xFFF0DEE5, // sidebarDivider
            0xFFF0DEE5, // divider
            0xFFFFFCFD, // inputBg
            0xFF3A2830, // textPrimary
            0xFF6B4E5A, // textSecondary
            0xFF9C7C88, // textMuted
            0xFFC2407A, // nameColor
            0xFFA9848F, // timeColor
            0xFFFFF8FA, // popupBg
            0xFFF8E7ED, // popupHover
            0xFF3A2830, // popupText
            0xFFFFF8FA, // contextBg
            0xFFF6E3EA, // contextHover
            0xFF3A2830, // contextText
            0xFFF4DEE6, // iconHover
            0xFFB85C86, // notificationText
            0xFFF2DBE3, // whisperBar
            0xFF4A2A36, // toastBg
            0xFFFBEDF1, // toastText
            0xFFE7C8D4, // scrollbar
            0xFFD9B0C0, // scrollbarHover
            0xFFF2DCE4, // closeBg
            0xFFE6C3D0, // closeHoverBg
            0xFF3A2830, // closeText
            0xFF8A6A76, // systemText
            0xFFC2407A, // quoteBar
            0xFFC2407A, // duplicateLabel
            0xFFE23D6D, // redDot
            0xFFFF2D6E, // redDotMention
            0xFFB85C86, // configTitle
            0xFFAA5A80, // configSection
            0xFF3A2830, // configLabel
            0xFFFDF5F7, // configBg
            0xFFF7E4EB, // bannerBg
            0xFF3A2830, // bannerText
            0xFFC2407A  // bannerBar
        );
    }
}
