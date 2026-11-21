package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import com.niuqu.chatbubble.render.Appearance;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CommandSuggestions.class, priority = 500)
public class ChatInputSuggestorMixin {

    // 注意：这里**不能**再取消 extractRenderState。
    // 26.x 的 ChatInputSuggestorMixin 曾经 @Inject(HEAD, cancellable) 把
    // CommandSuggestions.extractRenderState 整个掐掉，而 ChatBubbleScreen 正是靠
    // 手动调用 commandSuggestions.extractRenderState(...) 来画候选列表的 ——
    // 结果是「输入框里的灰色补全有、上方的候选列表永远不显示」（3.1.4 及以前）。
    // 候选列表的位置仍需在 showSuggestions 之后调整，见下面的 afterShow。

    /** 命令用法/报错提示行：上移到输入框上方，并去掉像素字体阴影。 */
    @Inject(method = "extractUsage", at = @At("HEAD"))
    private void e33chat$liftUsage(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof ChatBubbleScreen s) {
            g.pose().pushMatrix();
            g.pose().translate(0, -s.commandHintLift());
        }
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
