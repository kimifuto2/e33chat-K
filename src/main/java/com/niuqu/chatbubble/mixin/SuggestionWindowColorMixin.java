package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.render.Appearance;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

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
