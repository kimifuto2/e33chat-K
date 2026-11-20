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
        register(new ThemePalette("sakura", ThemeMode.DARK, "e33chat.theme.sakura", 2,
                sakuraColors(), null,
                new ThemePalette.BubbleColors(0xFF8E2F80, 0xFFFFFFFF, 0xFFE7BFDF, 0xFF3A1234)));
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
            0xFFC757B6, // panelBg
            0xFFCE63BE, // titleBg
            0xFFB94AA8, // barBg
            0xFFC251B2, // sidebarBg
            0xFFD06BC1, // sidebarItemHover
            0xFFDC86D0, // sidebarItemSelected
            0xFFA94099, // sidebarDivider
            0xFFA94099, // divider
            0xFFD877CB, // inputBg
            0xFFFFFFFF, // textPrimary
            0xFFFBE9F7, // textSecondary
            0xFFF0C8E8, // textMuted
            0xFFFFE9A8, // nameColor
            0xFFEFC4E6, // timeColor
            0xFFC757B6, // popupBg
            0xFFD46FC4, // popupHover
            0xFFFFFFFF, // popupText
            0xFFC757B6, // contextBg
            0xFFD46FC4, // contextHover
            0xFFFFFFFF, // contextText
            0xFFD46FC4, // iconHover
            0xFFFFE9A8, // notificationText
            0xFFB94AA8, // whisperBar
            0xFF5A2A50, // toastBg
            0xFFFFF0FB, // toastText
            0xFFB94AA8, // scrollbar
            0xFFE28FD6, // scrollbarHover
            0xFFB94AA8, // closeBg
            0xFFE28FD6, // closeHoverBg
            0xFFFFFFFF, // closeText
            0xFFFBE3F4, // systemText
            0xFFFFE9A8, // quoteBar
            0xFFFFE9A8, // duplicateLabel
            0xFFFF3D6E, // redDot
            0xFFFF2D6E, // redDotMention
            0xFFFFFFFF, // configTitle
            0xFFFFE9A8, // configSection
            0xFFFBE9F7, // configLabel
            0xFFC757B6, // configBg
            0xFFB94AA8, // bannerBg
            0xFFFFFFFF, // bannerText
            0xFFFFE9A8  // bannerBar
        );
    }
}
