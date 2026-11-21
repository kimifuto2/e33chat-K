package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.render.Appearance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatInputSuggestor.class, priority = 500)
public class ChatInputSuggestorMixin {

    @Shadow @Final private Screen owner;

    /** 原版命令高亮里「未被参数占用的文字」用的灰色样式（Formatting.GRAY）。 */
    @Shadow @Final private static Style INFO_STYLE;

    /**
     * 指令输入框里**实际输入的文字**颜色。
     *
     * <p>javap 取证：命令模式下 ChatInputSuggestor 给输入框注册了一个 Formatter，
     * {@code highlight(...)} 把「不是参数」的片段统一套上 {@code INFO_STYLE}（Formatting.GRAY），
     * 所以已输入的命令永远是灰色，输入框的 editableColor 根本轮不到生效。
     * 这里把 INFO_STYLE 换成当前主题的主文字色；参数高亮色（HIGHLIGHT_STYLES）保持不变。
     */
    @Redirect(method = "highlight",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/text/OrderedText;styledForwardsVisitedString(Ljava/lang/String;Lnet/minecraft/text/Style;)Lnet/minecraft/text/OrderedText;"))
    private static OrderedText e33chat$themeTypedCommandText(String string, Style style) {
        if (style == INFO_STYLE && MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
            style = style.withColor(TextColor.fromRgb(Appearance.snapshot().textPrimary()));
        }
        return OrderedText.styledForwardsVisitedString(string, style);
    }

    // 不再取消 renderMessages（原版的「命令用法 / 报错」提示行），
    // 否则本屏只剩候选窗口、没有用法提示。

    /**
     * 原版 renderMessages 把「命令用法 / 报错」提示行画在 height-27 起（贴屏幕底），
     * 本模组底栏高 26px，提示行会盖住底栏与输入框。
     * 这里在渲染前后临时上移 pose，只对本模组的聊天屏生效。
     */
    @Inject(method = "renderMessages", at = @At("HEAD"))
    private void e33chat$liftHintHead(DrawContext context, CallbackInfo ci) {
        if (owner instanceof ChatBubbleScreen s && s.commandHintLift() > 0) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0f, -s.commandHintLift());
        }
    }

    @Inject(method = "renderMessages", at = @At("RETURN"))
    private void e33chat$liftHintReturn(DrawContext context, CallbackInfo ci) {
        if (owner instanceof ChatBubbleScreen s && s.commandHintLift() > 0) {
            context.getMatrices().popMatrix();
        }
    }

    /** 命令用法/报错提示行也用 drawTextWithShadow：同样去掉 1px 硬阴影（像素字会看成重影）。 */
    @Redirect(method = "renderMessages",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V"))
    private void e33chat$noShadowHint(DrawContext context, TextRenderer textRenderer, OrderedText text,
                                      int x, int y, int color) {
        context.drawText(textRenderer, text, x, y, color, false);
    }

    @Inject(method = "show(Z)V", at = @At("TAIL"))
    private void afterShow(CallbackInfo ci) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen)) return;
        ChatInputSuggestor.SuggestionWindow window = ((ChatInputSuggestorAccessor) this).getWindow();
        if (window == null) return;
        Rect2i area = ((SuggestionWindowAccessor) window).getArea();
        if (area == null) return;
        int newY = ChatBubbleScreen.suggestionWindowBottom() - area.getHeight();
        if (area.getY() != newY) area.setY(newY);
        if (area.getX() < ChatBubbleScreen.getInputX()) area.setX(ChatBubbleScreen.getInputX());
    }
}
