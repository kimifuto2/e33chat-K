package com.niuqu.chatbubble.render;

/**
 * 一套完整的主题配色：明暗模式 + 42 个颜色字段 + 可选纹理资源。
 *
 * <p>颜色是**全量给出**的，不再从 dark/light 混合派生；{@code assets == null}
 * 表示这套主题不依赖烘焙纹理（由 SkinRenderer 代码绘制）。
 */
public record ThemePalette(
        String id,
        ThemeMode mode,
        String displayKey,
        int sortOrder,
        ChatBubbleTheme.Colors colors,
        ThemeAssets assets
) {
    /** 是否有自己的纹理集（null = 纯代码绘制）。 */
    public boolean usesTextures() {
        return assets != null;
    }
}
