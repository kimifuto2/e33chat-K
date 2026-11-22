package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.render.Appearance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 输入框选中背景改成主题「选中」色。
 *
 * <p>原理（javap 取证）：
 * <ul>
 *   <li>TextFieldWidget.renderWidget 的绘制顺序：先画文字 → 再 drawSelection → 再画光标；</li>
 *   <li>DrawContext.drawSelection = GUI_INVERT（INVERT blend）+ GUI_TEXT_HIGHLIGHT（<b>ADDITIVE</b> blend）；</li>
 *   <li>ADDITIVE 叠加在浅色主题上会加到饱和 = 纯白，所以主题色不能塞进原版这两条管线。</li>
 * </ul>
 *
 * <p>做法：在 renderWidget 的 HEAD（文字之前）用普通 fill 画主题色选中矩形，
 * 然后吞掉原版 drawSelection。文字随后画在选中色之上，永远清晰。
 */
@Mixin(TextFieldWidget.class)
public abstract class TextFieldWidgetSelectionMixin {
    @Shadow private int selectionStart;
    @Shadow private int selectionEnd;
    @Shadow private int textX;
    @Shadow private int textY;
    @Shadow private int firstCharacterIndex;
    @Shadow private String text;
    @Shadow @Final private TextRenderer textRenderer;

    /**
     * 选中背景必须在「背景之后、文字之前」画：
     * TextFieldWidget.renderWidget 先画 drawsBackground 的按钮背景（配置屏的输入框是 true），
     * 再画正文，最后才画原版选中。若注入在 HEAD，配置屏里会被随后的背景盖住。
     * 正文的第一次 drawText(..., OrderedText, ...) 正好在背景之后，选它作为注入点。
     */
    @Inject(method = "renderWidget",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
            ordinal = 0, shift = At.Shift.BEFORE))
    private void e33chat$drawThemedSelection(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (selectionStart == selectionEnd || text == null || text.isEmpty()) return;
        int start = Math.max(0, Math.min(selectionStart, selectionEnd));
        int end = Math.min(text.length(), Math.max(selectionStart, selectionEnd));
        if (end <= firstCharacterIndex) return;
        int from = Math.max(firstCharacterIndex, start);
        String before = text.substring(firstCharacterIndex, Math.min(from, text.length()));
        String selected = text.substring(Math.min(from, text.length()), end);
        int x1 = textX + textRenderer.getWidth(before);
        int x2 = textX + textRenderer.getWidth(before + selected);
        // 用输入框自己的屏幕边界夹住选中条（长指令/URL 全选时 textX 可能已横向滚动）。
        int left = ((TextFieldWidget) (Object) this).getX();
        int right = left + ((TextFieldWidget) (Object) this).getWidth();
        x1 = Math.max(left, Math.min(right, x1));
        x2 = Math.max(left, Math.min(right, x2));
        if (x2 <= x1) return;
        // 与原版 drawSelection 的矩形一致：textY - 1 .. textY + 9
        // 与消息区选中一致：选中色与强调色各半（暗色主题的纯选中色近黑，会像黑块）
        int color = 0xFF000000 | (com.niuqu.chatbubble.render.ChatBubbleTheme.mix(
            Appearance.snapshot().sidebarItemSelected(), Appearance.snapshot().quoteBar(), 0.5) & 0xFFFFFF);
        context.fill(x1, textY - 1, x2, textY + 9, color);
    }

    @Redirect(method = "renderWidget",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawSelection(IIIIZ)V"))
    private void e33chat$skipVanillaSelection(DrawContext context, int x1, int y1, int x2, int y2, boolean invert) {
        // 选中背景已在 HEAD 用主题色画好，这里吞掉原版的反色 + 加色叠加层
    }

    /**
     * 补全预览后缀（TextFieldWidget 内部用 String drawText 画）原版硬编码灰色；
     * 当光标位于文本中间时，右侧预览会被看成“光标后的正文变灰”。这里在聊天屏内
     * 把它改成主题主文字色，与正文一致。
     */
    @Redirect(method = "renderWidget",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)V"))
    private void e33chat$suggestionPrimaryColor(DrawContext context, TextRenderer textRenderer,
                                                String text, int x, int y, int color, boolean shadow) {
        if (MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
            color = 0xFF000000 | (Appearance.snapshot().textPrimary() & 0xFFFFFF);
        }
        context.drawText(textRenderer, text, x, y, color, shadow);
    }
}
