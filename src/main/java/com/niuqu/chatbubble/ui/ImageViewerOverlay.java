package com.niuqu.chatbubble.ui;

import com.niuqu.chatbubble.image.FullImageLoader;
import com.niuqu.chatbubble.image.ImageEntry;
import com.niuqu.chatbubble.compat.NativeFileDialog;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import com.niuqu.chatbubble.render.UiCanvas;

import net.minecraft.util.Util;

/** 全屏图片查看器：双击/单击图片打开；滚轮缩放、拖动平移、双击复位、Esc/背景关闭。 */
public final class ImageViewerOverlay {
    public boolean visible;
    private String url;
    private float scale;
    private float panX, panY;
    private float fitScale;
    private boolean needInit;
    private boolean startActual;
    private boolean dragging;
    private double dragX, dragY;
    private float dragPanX, dragPanY;
    private int drawX, drawY, drawW, drawH;
    private int imageW, imageH;
    private int actionW = 80;

    public void open(String url, boolean actualSize) {
        this.url = url;
        this.visible = true;
        this.scale = 0f;
        this.panX = 0f;
        this.panY = 0f;
        this.fitScale = 0f;
        this.needInit = true;
        this.startActual = actualSize;
        this.dragging = false;
    }

    public void close() {
        visible = false;
        dragging = false;
        url = null;
    }

    private boolean insideImage(double mx, double my) {
        return drawW > 0 && drawH > 0 && mx >= drawX && mx <= drawX + drawW
            && my >= drawY && my <= drawY + drawH;
    }

    private void applyFit(int screenW, int screenH, ImageEntry entry) {
        float fit = Math.min((screenW * 0.90f) / entry.width(), (screenH * 0.82f) / entry.height());
        fit = Math.max(0.01f, fit);
        fitScale = fit;
        if (needInit) {
            scale = startActual ? 1f : fit;
            needInit = false;
        }
    }

    private void clampPan(int screenW, int screenH) {
        if (drawW <= 0 || drawH <= 0) return;
        float maxX = Math.max(0, (drawW - screenW) / 2f + 32f);
        float maxY = Math.max(0, (drawH - screenH) / 2f + 32f);
        if (drawW <= screenW) maxX = Math.max(32f, drawW / 2f);
        if (drawH <= screenH) maxY = Math.max(32f, drawH / 2f);
        panX = Math.max(-maxX, Math.min(maxX, panX));
        panY = Math.max(-maxY, Math.min(maxY, panY));
    }

    public void render(UiCanvas g, Font font, int mouseX, int mouseY,
                       int screenW, int screenH, ChatBubbleTheme.Colors c) {
        if (!visible) return;
        g.fill(0, 0, screenW, screenH, ChatBubbleTheme.alphaBlend(c.panelBg(), 235));
        ImageEntry entry = FullImageLoader.getOrLoad(url);
        if (entry != null && entry.state() == ImageEntry.State.LOADED && entry.textureId() != null
            && entry.width() > 0 && entry.height() > 0) {
            applyFit(screenW, screenH, entry);
            imageW = entry.width();
            imageH = entry.height();
            drawW = Math.max(1, (int) (imageW * scale));
            drawH = Math.max(1, (int) (imageH * scale));
            drawX = (int) (screenW / 2f + panX - drawW / 2f);
            drawY = (int) (screenH / 2f + panY - drawH / 2f);
            g.drawTexture(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, entry.textureId(),
                drawX, drawY, 0f, 0f, drawW, drawH, entry.width(), entry.height(), entry.width(), entry.height());
        } else {
            drawW = drawH = 0;
            String txt = entry != null && entry.state() == ImageEntry.State.FAILED
                ? "图片加载失败" : "图片加载中…";
            g.drawText(font, txt, screenW / 2 - font.width(txt) / 2, screenH / 2,
                ChatBubbleTheme.alphaBlend(c.textSecondary(), 255), false);
        }

        int closeSize = 22;
        int cx = screenW - closeSize - 10, cy = 10;
        boolean hoverClose = mouseX >= cx && mouseX <= cx + closeSize && mouseY >= cy && mouseY <= cy + closeSize;
        RoundRectRenderer.panel(g, cx, cy, closeSize, closeSize, 6f,
            ChatBubbleTheme.alphaBlend(hoverClose ? c.popupHover() : c.popupBg(), 255),
            ChatBubbleTheme.alphaBlend(c.divider(), 255));
        g.drawText(font, "✕", cx + closeSize / 2 - font.width("✕") / 2, cy + 7,
            ChatBubbleTheme.alphaBlend(c.closeText(), 255), false);

        if (url != null) {
            String action = (url.startsWith("http://") || url.startsWith("https://"))
                ? "在浏览器打开" : "保存图片";
            actionW = font.width(action) + 16;
            int ox = 10, oy = screenH - 28;
            boolean hoverOpen = mouseX >= ox && mouseX <= ox + actionW && mouseY >= oy && mouseY <= oy + 18;
            RoundRectRenderer.panel(g, ox, oy, actionW, 18, 6f,
                ChatBubbleTheme.alphaBlend(hoverOpen ? c.popupHover() : c.popupBg(), 255),
                ChatBubbleTheme.alphaBlend(c.divider(), 255));
            g.drawText(font, action, ox + 8, oy + 4,
                ChatBubbleTheme.alphaBlend(c.textPrimary(), 255), false);
        }
    }

    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick, int screenW, int screenH) {
        if (!visible) return false;
        int closeSize = 22;
        int cx = screenW - closeSize - 10, cy = 10;
        if (click.x() >= cx && click.x() <= cx + closeSize && click.y() >= cy && click.y() <= cy + closeSize) {
            close();
            return true;
        }
        if (url != null) {
            int ow = actionW, ox = 10, oy = screenH - 28;
            if (click.x() >= ox && click.x() <= ox + ow && click.y() >= oy && click.y() <= oy + 18) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(url));
                } else {
                    FullImageLoader.downloadBytes(url, bytes -> {
                        if (bytes != null) NativeFileDialog.saveImage(bytes, "e33chat-image");
                    });
                }
                return true;
            }
        }
        if (!insideImage(click.x(), click.y())) {
            close();
            return true;
        }
        // 不因 doubleClick 拦截：双击不再承担缩放，任何一次按在图片上的拖动都应生效。
        dragging = true;
        dragX = click.x();
        dragY = click.y();
        dragPanX = panX;
        dragPanY = panY;
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent click, int screenW, int screenH) {
        if (!visible) return false;
        if (!dragging) {
            if (!insideImage(click.x(), click.y())) return false;
            dragging = true;
            dragX = click.x();
            dragY = click.y();
            dragPanX = panX;
            dragPanY = panY;
            return true;
        }
        // 拖动保持 1:1 跟手，不做边界 clamp —— 否则在高倍率/边缘会被吸回，表现为“拖不动/闪回”。
        panX = dragPanX + (float) (click.x() - dragX);
        panY = dragPanY + (float) (click.y() - dragY);
        return true;
    }

    public boolean mouseReleased() {
        if (!visible) return false;
        dragging = false;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount, int screenW, int screenH) {
        if (!visible || scale <= 0 || imageW <= 0 || imageH <= 0) return false;
        float old = scale;
        float next = (float) (scale * (amount > 0 ? 1.15 : 1 / 1.15));
        next = Math.max(fitScale * 0.25f, Math.min(8f, next));
        if (next == old) return true;

        // 只有鼠标在图片上时才以鼠标为锚点；在图片外滚动则围绕图片中心缩放，
        // 避免因为锚点落在图片外而把整张图突然甩到一边。
        if (insideImage(mouseX, mouseY)) {
            float sourceX = (float) ((mouseX - drawX) / old);
            float sourceY = (float) ((mouseY - drawY) / old);
            scale = next;
            drawW = Math.max(1, (int) (imageW * scale));
            drawH = Math.max(1, (int) (imageH * scale));
            float newX = (float) mouseX - sourceX * scale;
            float newY = (float) mouseY - sourceY * scale;
            panX = newX - screenW / 2f + drawW / 2f;
            panY = newY - screenH / 2f + drawH / 2f;
            drawX = (int) newX;
            drawY = (int) newY;
        } else {
            scale = next;
            drawW = Math.max(1, (int) (imageW * scale));
            drawH = Math.max(1, (int) (imageH * scale));
            // 保持图片中心不动，不再做边界 clamp，避免缩放瞬间跳位
            drawX = (int) (screenW / 2f + panX - drawW / 2f);
            drawY = (int) (screenH / 2f + panY - drawH / 2f);
        }
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        if (keyCode == 256) { // GLFW ESC
            close();
            return true;
        }
        return false;
    }
}
