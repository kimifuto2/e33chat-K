package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import com.niuqu.chatbubble.render.UiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;
import com.niuqu.chatbubble.render.Appearance;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CommandSuggestions.class, priority = 500)
public class ChatInputSuggestorMixin {

    @Shadow private int commandUsageWidth;
    @Shadow private int commandUsagePosition;
    @Shadow private List<FormattedCharSequence> commandUsage;
    /** 当前帧是否已经画过合并后的圆角用法底；多行 fill 只取第一次。 */
    private boolean e33chat$usageBoxDrawn;

    // 注意：这里**不能**再取消 extractRenderState。
    // 26.x 的 ChatInputSuggestorMixin 曾经 @Inject(HEAD, cancellable) 把
    // CommandSuggestions.extractRenderState 整个掐掉，而 ChatBubbleScreen 正是靠
    // 手动调用 commandSuggestions.extractRenderState(...) 来画候选列表的 ——
    // 结果是「输入框里的灰色补全有、上方的候选列表永远不显示」（3.1.4 及以前）。
    // 候选列表的位置仍需在 showSuggestions 之后调整，见下面的 afterShow。

    /** 命令用法/报错提示行：上移到输入框上方，并去掉像素字体阴影。 */
    @Inject(method = "extractUsage", at = @At("HEAD"))
    private void e33chat$liftUsage(GuiGraphicsExtractor g, CallbackInfo ci) {
        e33chat$usageBoxDrawn = false;
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof ChatBubbleScreen s) {
            g.pose().pushMatrix();
            g.pose().translate(0, -s.commandHintLift());
        }
    }

    /** 原版每行各画一条矩形底；合并成覆盖全部提示行的单个圆角底。 */
    @Redirect(method = "extractUsage",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void e33chat$roundedUsageBox(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof ChatBubbleScreen) {
            if (!e33chat$usageBoxDrawn) {
                int lines = Math.max(1, commandUsage == null ? 1 : commandUsage.size());
                // 原版从最下面一行开始画，越往后的行 y 越小；圆角底要向上扩展。
                int top = y1 - 12 * (lines - 1);
                RoundRectRenderer.fill(UiCanvas.of(g), x1, top, x2, y1 + 12, 6f, color);
                e33chat$usageBoxDrawn = true;
            }
            return;
        }
        g.fill(x1, y1, x2, y2, color);
    }

    @Inject(method = "extractUsage", at = @At("RETURN"))
    private void e33chat$unliftUsage(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof ChatBubbleScreen) {
            g.pose().popMatrix();
        }
    }

    @Redirect(method = "extractUsage",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"))
    private void e33chat$noShadowUsage(GuiGraphicsExtractor g, Font font, FormattedCharSequence text, int x, int y, int color) {
        // 用法/报错提示整行居中在底框里，而不是贴左边缘。
        if (Minecraft.getInstance().gui.screen() instanceof ChatBubbleScreen) {
            x = commandUsagePosition + (commandUsageWidth - font.width(text)) / 2;
        }
        g.text(font, text, x, y, color, false);
    }

    /**
     * 输入框里的**指令正文**原版用 LITERAL_STYLE（灰 #AAAAAA）着色，而补全预览后缀走 EditBox 的
     * textColor（主题主文字色）——用户看到的是“输入的指令灰、预览黑”，正好反了。
     * 这里只替换 formatText 里的灰色 LITERAL_STYLE 为主题主文字色；参数高亮色（ARGUMENT_STYLES）
     * 与未解析红色（UNPARSED_STYLE）保持原样。
     */
    @Redirect(method = "formatText",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/FormattedCharSequence;forward(Ljava/lang/String;Lnet/minecraft/network/chat/Style;)Lnet/minecraft/util/FormattedCharSequence;"))
    private static FormattedCharSequence e33chat$literalPrimary(String text, Style style) {
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof ChatBubbleScreen) {
            TextColor color = style.getColor();
            if (color != null && (color.getValue() & 0xFFFFFF) == 0xAAAAAA) {
                return FormattedCharSequence.forward(text,
                    style.withColor(TextColor.fromRgb(Appearance.snapshot().textPrimary() & 0xFFFFFF)));
            }
        }
        return FormattedCharSequence.forward(text, style);
    }

    @Inject(method = "showSuggestions(Z)V", at = @At("TAIL"))
    private void afterShow(CallbackInfo ci) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ChatBubbleScreen)) return;
        CommandSuggestions.SuggestionsList window = ((ChatInputSuggestorAccessor) this).getWindow();
        if (window == null) return;
        Rect2i area = ((SuggestionWindowAccessor) window).getArea();
        if (area == null) return;
        int newY = ChatBubbleScreen.suggestionWindowBottom() - area.getHeight();
        if (area.getY() != newY) area.setY(newY);
        if (area.getX() < ChatBubbleScreen.getInputX()) area.setX(ChatBubbleScreen.getInputX());
    }
}
