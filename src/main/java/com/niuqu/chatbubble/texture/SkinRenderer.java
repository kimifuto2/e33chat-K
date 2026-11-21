package com.niuqu.chatbubble.texture;

import com.niuqu.chatbubble.render.Appearance;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.render.UiCanvas;
import net.minecraft.resources.Identifier;

/** Code-drawn surfaces used by texture-free themes. */
final class SkinRenderer {
    private SkinRenderer() {}

    static boolean draw(UiCanvas g, Identifier id, int x, int y, int w, int h, float alpha) {
        String path = id.getPath();
        if (!path.startsWith("skin/") || w <= 0 || h <= 0 || alpha <= 0.003f) return false;
        String[] parts = path.split("/", 3);
        if (parts.length != 3) return false;
        ChatBubbleTheme theme;
        try {
            theme = ChatBubbleTheme.valueOf(parts[1].toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        ChatBubbleTheme.Colors c = Appearance.snapshot(theme);
        int base = color(parts[2], c);
        int a = Math.max(0, Math.min(255, (int) (((base >>> 24) & 255) * alpha)));
        int fill = (a << 24) | (base & 0xFFFFFF);
        int highlight = ChatBubbleTheme.alphaBlend(ChatBubbleTheme.mix(base, 0xFFFFFFFF, 0.06), a);
        int edge = ChatBubbleTheme.alphaBlend(ChatBubbleTheme.mix(base, 0xFF000000, 0.18), a);
        g.fill(x, y, x + w, y + h, fill);
        if (h > 2) g.fill(x, y, x + w, y + 1, highlight);
        if (w > 1 && h > 1) g.fill(x, y + h - 1, x + w, y + h, edge);
        return true;
    }

    private static int color(String element, ChatBubbleTheme.Colors c) {
        return switch (element) {
            case "panel_bg", "content_bg", "config_bg" -> c.panelBg();
            case "title_bar" -> c.titleBg();
            case "bottom_bar" -> c.barBg();
            case "sidebar_bg" -> c.sidebarBg();
            case "sidebar_selected" -> c.sidebarItemSelected();
            case "sidebar_hover", "hover_bg", "context_hover", "close_hover" -> c.sidebarItemHover();
            case "divider", "scrollbar_track", "quick_scrollbar_track" -> c.divider();
            case "scrollbar_thumb", "quick_scrollbar_thumb" -> c.scrollbar();
            case "input_bg" -> c.inputBg();
            case "context_menu_bg" -> c.contextBg();
            case "popup_bg" -> c.popupBg();
            case "toast_bg" -> c.toastBg();
            case "whisper_bar" -> c.whisperBar();
            case "close_bg" -> c.closeBg();
            default -> c.panelBg();
        };
    }
}
