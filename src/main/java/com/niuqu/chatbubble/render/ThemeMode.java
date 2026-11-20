package com.niuqu.chatbubble.render;

/**
 * 主题明暗模式：只决定「文字对比方向」与「图标剪影基色」。
 * 与配色（{@link ThemePalette}）和纹理资源（{@link ThemeAssets}）解耦。
 */
public enum ThemeMode {
    DARK,
    LIGHT;

    public static ThemeMode parse(String s) {
        return (s != null && s.equalsIgnoreCase("light")) ? LIGHT : DARK;
    }

    public String key() {
        return name().toLowerCase();
    }
}
