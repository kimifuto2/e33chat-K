package com.niuqu.chatbubble.render;

/**
 * 一套完整的主题配色：明暗模式 + 42 个颜色字段 + 可选纹理资源 + 可选默认气泡色。
 *
 * <p>颜色是**全量给出**的，不再从 dark/light 混合派生；{@code assets == null}
 * 表示这套主题不依赖烘焙纹理（由 {@link SkinRenderer} 代码绘制）。
 *
 * <p>{@code bubbles} 只作为「主题默认气泡色」：当玩家没有自定义气泡颜色时生效，
 * 玩家自己改过的颜色优先（见 {@link Appearance#bubbles()}）。
 */
public record ThemePalette(
        String id,
        ThemeMode mode,
        String displayKey,
        int sortOrder,
        ChatBubbleTheme.Colors colors,
        ThemeAssets assets,
        BubbleColors bubbles
) {
    /** 气泡默认色（自己/对方 的背景与文字）。 */
    public record BubbleColors(int ownBg, int ownText, int otherBg, int otherText) {}

    public ThemePalette(String id, ThemeMode mode, String displayKey, int sortOrder,
                        ChatBubbleTheme.Colors colors, ThemeAssets assets) {
        this(id, mode, displayKey, sortOrder, colors, assets, null);
    }

    /** 是否有自己的纹理集（null = 纯代码绘制）。 */
    public boolean usesTextures() {
        return assets != null;
    }
}
