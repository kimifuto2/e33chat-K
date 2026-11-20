package com.niuqu.chatbubble.render;

/**
 * 一套主题可选的纹理资源集。
 *
 * @param dir      纹理目录名（assets/e33chat/textures/gui/&lt;dir&gt;/）
 * @param iconTint 图标剪影的染色（白色剪影直接 tint，不需要为新配色重画图标）
 */
public record ThemeAssets(String dir, int iconTint) {

    public static ThemeAssets of(String dir) {
        return new ThemeAssets(dir, 0xFFFFFFFF);
    }
}
