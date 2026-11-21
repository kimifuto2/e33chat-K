package com.niuqu.chatbubble.texture;

import com.niuqu.chatbubble.render.UiCanvas;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * 带整体透明度的纹理渲染：纹理色 × 白 (1,1,1,alpha)。
 * 用于动态 alpha 的元素（面板开屏淡入、滚动条淡入淡出）——普通 drawTexture 无法携带运行时透明度。
 *
 * <p>26.x 适配：绘制入口是 {@link UiCanvas}（包装 GuiGraphicsExtractor）。
 * 主题系统新增的代码皮肤（{@code skin/<theme>/<element>}）由 {@link SkinRenderer} 接管。
 */
public final class ColoredTextureRenderer {

    private ColoredTextureRenderer() {}

    private static int argbAlpha(float alpha) {
        int a = (int) (alpha * 255f);
        if (a > 255) a = 255;
        if (a < 0) a = 0;
        return (a << 24) | 0x00FFFFFF;
    }

    public static void drawWithAlpha(UiCanvas g, Identifier tex,
                                     int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        if (SkinRenderer.draw(g, tex, x, y, w, h, alpha)) return;
        g.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, w, h, w, h, argbAlpha(alpha));
    }

    public static void drawTinted(UiCanvas g, Identifier tex,
                                  int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        g.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, w, h, w, h, argb);
    }

    public static void drawTinted(UiCanvas g, Identifier tex,
                                  int x, int y, int w, int h,
                                  float u, float v, int regionW, int regionH,
                                  int texW, int texH, int argb) {
        if (w <= 0 || h <= 0) return;
        g.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y,
            u, v, w, h, regionW, regionH, texW, texH, argb);
    }

    public static void drawWithAlpha(UiCanvas g, Identifier tex,
                                     int x, int y, int w, int h,
                                     float u, float v, int regionW, int regionH,
                                     int texW, int texH, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        g.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y,
            u, v, w, h, regionW, regionH, texW, texH, argbAlpha(alpha));
    }
}
