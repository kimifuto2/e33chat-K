package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.texture.UiElement;

/**
 * 背景类 UI 元素的唯一绘制入口（26.x）。
 * 有纹理集 → 原烘焙纹理；无纹理集（如 sakura）→ 调色板颜色代码绘制。
 */
public final class SkinRenderer {

    private SkinRenderer() {}

    public static void draw(UiCanvas g, UiElement el, int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        // 配置/菜单类大背景：所有主题统一用深色半透明幕布（与暗色观感一致）
        if (el == UiElement.CONFIG_BG || el == UiElement.CONTENT_BG) {
            int a = (int) (0xFF * alpha);
            g.fill(x, y, x + w, y + h, ChatBubbleTheme.alphaBlend(0xFF12131A, Math.max(0, Math.min(255, a))));
            return;
        }
        ThemePalette palette = ThemeRegistry.current();
        if (palette.usesTextures()) {
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
}
