package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.render.Appearance;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import com.niuqu.chatbubble.render.UiCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 指令候选列表配色 + 选中行高亮（26.3）。
 *
 * <p>原版 {@code SuggestionsList.extractRenderState} 写死：
 * 普通项 -5592406 灰、选中项 -256 黄，且用带阴影的 {@code text(Font,String,III)}。
 * 这里改成 WCAG 黑/白、选中行整行高亮条、无阴影。
 */
@Mixin(CommandSuggestions.SuggestionsList.class)
public class SuggestionWindowColorMixin {

    @Shadow @Final private Rect2i rect;
    @Shadow private int offset;
    @Shadow private int current;

    @ModifyConstant(method = "extractRenderState", constant = @Constant(intValue = -5592406))
    private int e33chat$normalColor(int original) {
        return e33chat$baseTextColor();
    }

    @ModifyConstant(method = "extractRenderState", constant = @Constant(intValue = -256))
    private int e33chat$selectedColor(int original) {
        return e33chat$baseTextColor();
    }

    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void e33chat$noShadowSuggestion(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
        if (rect != null) {
            int cell = (y - rect.getY() - 2) / 12;
            if (cell >= 0 && offset + cell == current) {
                int accent = Appearance.snapshot().quoteBar() & 0xFFFFFF;
                int base = e33chat$baseTextColor() & 0xFFFFFF;
                int toward = base == 0 ? 0xFFFFFF : 0x000000;
                int band = ChatBubbleTheme.mix(accent, toward, 0.5) & 0xFFFFFF;
                int top = rect.getY() + 12 * cell;
                g.fill(rect.getX(), top, rect.getX() + rect.getWidth(), top + 12, 0xFF000000 | band);
            }
        }
        g.text(font, text, x, y, color, false);
    }

    /** 当前帧已处理的候选列表 fill 数：第一次画圆角底，其余滚动条/渐变 fill 一并跳过。 */
    private int e33chat$bgFillCount;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void e33chat$resetRoundedBg(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
        e33chat$bgFillCount = 0;
    }

    /** 原版候选框由多条矩形 fill 拼成；合并成覆盖整块列表的一个圆角底。 */
    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void e33chat$roundedSuggestionBox(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (rect == null) {
            g.fill(x1, y1, x2, y2, color);
            return;
        }
        if (e33chat$bgFillCount++ == 0) {
            RoundRectRenderer.fill(UiCanvas.of(g), rect.getX(), rect.getY() - 1,
                rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight() + 1,
                6f, color);
        }
        // 后续 fill 是滚动提示/阴影，跳过；否则会把圆角切成直角。
    }

    private static int e33chat$baseTextColor() {
        int rgb = Appearance.snapshot().quoteBar() & 0xFFFFFF;
        double l = luminance(rgb);
        double white = 1.05 / (l + 0.05);
        double black = (l + 0.05) / 0.05;
        return black >= white ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static double luminance(int rgb) {
        double r = ((rgb >> 16) & 255) / 255.0;
        double g = ((rgb >> 8) & 255) / 255.0;
        double b = (rgb & 255) / 255.0;
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }
}
