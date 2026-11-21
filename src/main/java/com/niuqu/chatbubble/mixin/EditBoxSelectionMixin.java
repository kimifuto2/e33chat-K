package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.render.ChatTextSelection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.render.Appearance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 输入框选中背景改为主题实色。
 *
 * <p>26.3 的 {@code EditBox.extractWidgetRenderState} 顺序是：
 * 背景 sprite → 正文 text(...) → 建议后缀 → {@code textHighlight(...)}（原版选中）→ 光标。
 * 原版 textHighlight 是 INVERT + ADDITIVE 混合，浅色主题会被加成纯白。
 *
 * <p>因此：在**第一次正文 text 调用之前**画实色选中矩形（此时背景已画完、文字还没画，
 * 既不会被背景盖住，也不会盖住文字），并把原版 textHighlight 整个吞掉。
 */
@Mixin(EditBox.class)
public class EditBoxSelectionMixin {

    @Shadow @Final private Font font;
    @Shadow private String value;
    @Shadow private int cursorPos;
    @Shadow private int highlightPos;
    @Shadow private int displayPos;
    @Shadow private int textX;
    @Shadow private int textY;

    @Inject(method = "extractWidgetRenderState",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            ordinal = 0, shift = At.Shift.BEFORE))
    private void e33chat$drawSelection(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int start = Math.min(cursorPos, highlightPos);
        int end = Math.max(cursorPos, highlightPos);
        if (start == end) return;
        if (start < displayPos) start = displayPos;
        int len = value.length();
        if (start > len) start = len;
        if (end > len) end = len;
        if (end <= start) return;
        int x1 = textX + font.width(value.substring(displayPos, start));
        int x2 = textX + font.width(value.substring(displayPos, end));
        g.fill(x1, textY - 1, x2, textY + 9, ChatTextSelection.selectionBg());
    }

    /**
     * 补全预览后缀：原版和正文共用 EditBox.textColor（主题主色）→ 和正文同色，看不出是“预览”。
     * 第二次 text(Font, FormattedCharSequence, IIIZ) 就是 EditBox 画 suggestion 的那次
     * （第一次是正文，javap 顺序：251 正文 / 406 预览），只把它改成主题次要文字色。
     */
    @Redirect(method = "extractWidgetRenderState",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            ordinal = 1))
    private void e33chat$suggestionColor(GuiGraphicsExtractor g, Font font, net.minecraft.util.FormattedCharSequence text,
                                          int x, int y, int color, boolean shadow) {
        int drawColor = color;
        if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof ChatBubbleScreen) {
            drawColor = 0xFF000000 | (Appearance.snapshot().textMuted() & 0xFFFFFF);
        }
        g.text(font, text, x, y, drawColor, shadow);
    }

    @Redirect(method = "extractWidgetRenderState",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textHighlight(IIIIZ)V"))
    private void e33chat$skipVanillaHighlight(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, boolean invert) {
        // 选中背景已由 e33chat$drawSelection 画好
    }
}
