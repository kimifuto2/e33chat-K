package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.render.Appearance;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.util.math.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 指令候选列表的配色与选中态。
 *
 * <p>原版 {@code ChatInputSuggestor$SuggestionWindow.render} 里：
 * <ul>
 *   <li>普通候选文字写死 {@code -5592406}（0xFFAAAAAA 灰）；</li>
 *   <li>选中项只把文字改成 {@code -256}（0xFFFF00 黄），没有整行背景；</li>
 *   <li>文字用 {@code drawTextWithShadow}（像素字体下像重影）。</li>
 * </ul>
 *
 * <p>本模组候选窗背景是主题强调色，所以：文字按 WCAG 选黑/白；普通项与选中项**同色**，
 * 选中态改由「整行高亮条」表达——因为只换文字色在单色底上很难看出（用户反馈不明显）。
 * 高亮条方向与文字相反（黑字→提亮底色，白字→压暗底色），保证选中行文字仍高对比。
 */
@Mixin(ChatInputSuggestor.SuggestionWindow.class)
public class SuggestionWindowColorMixin {

    @Shadow @Final private Rect2i area;
    @Shadow private int inWindowIndex;
    @Shadow private int selection;

    @ModifyConstant(method = "render", constant = @Constant(intValue = -5592406))
    private int e33chat$normalSuggestionColor(int original) {
        return e33chat$baseTextColor();
    }

    /** 选中项文字与普通项同色，选中态由下面的整行高亮条表达。 */
    @ModifyConstant(method = "render", constant = @Constant(intValue = -256))
    private int e33chat$selectedSuggestionColor(int original) {
        return e33chat$baseTextColor();
    }

    /**
     * 候选项文字原版用 drawTextWithShadow（像素字体下像重影）→ 改无阴影；
     * 同时在这一行的文字之前画选中高亮条（渲染顺序：行背景 → 文字，所以不会盖字）。
     */
    @Redirect(method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"))
    private void e33chat$noShadowSuggestion(DrawContext context, TextRenderer textRenderer, String text,
                                            int x, int y, int color) {
        if (area != null) {
            int cell = (y - area.getY() - 2) / 12;
            if (cell >= 0 && inWindowIndex + cell == selection) {
                int accent = Appearance.snapshot().quoteBar() & 0xFFFFFF;
                int base = e33chat$baseTextColor() & 0xFFFFFF;
                // 黑字 → 提亮底色；白字 → 压暗底色（0xFFFFFF / 0x000000 为混合目标）
                int toward = base == 0 ? 0xFFFFFF : 0x000000;
                int band = ChatBubbleTheme.mix(accent, toward, 0.5) & 0xFFFFFF;
                int top = area.getY() + 12 * cell;
                context.fill(area.getX(), top, area.getX() + area.getWidth(), top + 12, 0xFF000000 | band);
            }
        }
        context.drawText(textRenderer, text, x, y, color, false);
    }

    /** 原版候选框由两条矩形 fill 拼成；换成覆盖整块列表的一个圆角底。 */
    @Redirect(method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 0))
    private void e33chat$roundedSuggestionBoxTop(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        if (area != null) {
            RoundRectRenderer.fill(context, area.getX(), area.getY() - 1,
                area.getX() + area.getWidth(), area.getY() + area.getHeight() + 1,
                6f, color);
        } else {
            context.fill(x1, y1, x2, y2, color);
        }
    }

    /** 第二条底部填充已经并入上面的圆角底，跳过。 */
    @Redirect(method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V", ordinal = 1))
    private void e33chat$roundedSuggestionBoxBottom(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        // no-op: the first fill above covers the full rounded box
    }

    /** 在强调色底上对比度更高的白或黑（WCAG 相对亮度）。 */
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

    private static double contrast(int a, int b) {
        double l1 = luminance(a), l2 = luminance(b);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }
}
