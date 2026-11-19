package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.texture.UiElement;
import net.minecraft.client.gui.DrawContext;

/**
 * 主题皮肤绘制：DARK/LIGHT 用 jar 内置的烘焙纹理（原观感），其余预设改用**纯色填充**。
 *
 * <p>为什么不再重着色纹理：这两套纹理里同时编码了底色、描边、内阴影与高光，
 * 盲目按色相重绘会把这些层次一起洗掉（画面发灰、对比度变差）。预设主题直接使用
 * 设计好的调色板颜色平铺（{@link ChatBubbleTheme.Colors} 中各元素色），既保证
 * 与文字颜色的对比，也避免"滤镜感"。
 */
public final class ThemeSkin {

    private ThemeSkin() {}

    /** 画一个 UI 元素背景：暗/浅主题用纹理，预设主题用对应调色板颜色。 */
    public static void draw(DrawContext g, UiElement el, int x, int y, int w, int h, float alpha) {
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

    /** 是否走纯色皮肤（预设主题）。UI 里需要额外描边/渐变的地方可据此分支。 */
    public static boolean isSolidSkin() {
        return ThemePreset.current().recolorsTextures();
    }
}
