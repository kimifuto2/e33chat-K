package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.render.UiCanvas;

/**
 * 主题皮肤绘制（26.x）：DARK/LIGHT 用内置烘焙纹理（原观感），其余预设用设计好的
 * 调色板颜色平铺 —— 不再重着色纹理，避免把描边/阴影层次洗掉。
 */
public final class ThemeSkin {

    private ThemeSkin() {}

    public static void draw(UiCanvas g, UiElement el, int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        if (!ThemePreset.current().recolorsTextures()) {
            ColoredTextureRenderer.drawWithAlpha(g, el.rl(Appearance.resolvedTheme()), x, y, w, h, alpha);
            return;
        }
        ChatBubbleTheme.Colors c = Appearance.snapshot();
        int base = colorOf(el, c);
        int a = (int) (((base >>> 24) & 0xFF) * alpha);
        g.fill(x, y, x + w, y + h, ChatBubbleTheme.alphaBlend(base, Math.max(0, Math.min(255, a))));
    }

    private static int colorOf(UiElement el, ChatBubbleTheme.Colors c) {
        return switch (el) {
            case PANEL_BG -> c.panelBg();
            case TITLE_BAR -> c.titleBg();
            case BOTTOM_BAR -> c.barBg();
            case SIDEBAR_BG -> c.sidebarBg();
            case DIVIDER -> c.divider();
            case INPUT_BG -> c.inputBg();
            case SCROLLBAR_TRACK, QUICK_SCROLLBAR_TRACK -> c.scrollbar();
            case SCROLLBAR_THUMB, QUICK_SCROLLBAR_THUMB -> c.scrollbarHover();
            case CONTEXT_MENU_BG -> c.contextBg();
            case POPUP_BG -> c.popupBg();
            case TOAST_BG -> c.toastBg();
            case WHISPER_BAR -> c.whisperBar();
            case CONFIG_BG, CONTENT_BG -> c.configBg();
            case HOVER_BG -> c.iconHover();
            case SIDEBAR_SELECTED -> c.sidebarItemSelected();
            case SIDEBAR_HOVER -> c.sidebarItemHover();
            case CONTEXT_HOVER -> c.contextHover();
            case CLOSE_BG -> c.closeBg();
            case CLOSE_HOVER -> c.closeHoverBg();
        };
    }

    public static boolean isSolidSkin() {
        return ThemePreset.current().recolorsTextures();
    }
}
