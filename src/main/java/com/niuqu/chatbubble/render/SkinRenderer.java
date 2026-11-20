package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.texture.UiElement;
import net.minecraft.client.gui.DrawContext;

/**
 * 背景类 UI 元素的唯一绘制入口。
 *
 * <p>当前主题有纹理集（{@link ThemePalette#usesTextures()}）→ 走原烘焙纹理，观感不变；
 * 没有纹理集（如 sakura）→ 用调色板颜色代码绘制。这样新增主题不必再准备 39 张 PNG。
 */
public final class SkinRenderer {

    private SkinRenderer() {}

    /** 画一个 UI 元素背景。alpha 为整体透明度（与纹理路径的 alpha 语义一致）。 */
    public static void draw(DrawContext g, UiElement el, int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        // 配置/菜单类大背景：所有主题统一用深色半透明幕布（与暗色观感一致），
        // 避免浅色主题的奶油底把游戏画面挡成一大块亮色。
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

    /** 元素 → 调色板颜色。没有专门映射的元素退回面板底色。 */
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
