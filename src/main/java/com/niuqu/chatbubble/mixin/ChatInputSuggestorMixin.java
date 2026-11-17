package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommandSuggestions.class, priority = 500)
public class ChatInputSuggestorMixin {

    // 注意：这里**不能**再取消 extractRenderState。
    // 26.x 的 ChatInputSuggestorMixin 曾经 @Inject(HEAD, cancellable) 把
    // CommandSuggestions.extractRenderState 整个掐掉，而 ChatBubbleScreen 正是靠
    // 手动调用 commandSuggestions.extractRenderState(...) 来画候选列表的 ——
    // 结果是「输入框里的灰色补全有、上方的候选列表永远不显示」（3.1.4 及以前）。
    // 候选列表的位置仍需在 showSuggestions 之后调整，见下面的 afterShow。

    @Inject(method = "showSuggestions(Z)V", at = @At("TAIL"))
    private void afterShow(CallbackInfo ci) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ChatBubbleScreen)) return;
        CommandSuggestions.SuggestionsList window = ((ChatInputSuggestorAccessor) this).getWindow();
        if (window == null) return;
        Rect2i area = ((SuggestionWindowAccessor) window).getArea();
        if (area == null) return;
        int newY = ChatBubbleScreen.getInputY() - area.getHeight() - 4;
        if (area.getY() != newY) area.setY(newY);
        if (area.getX() < ChatBubbleScreen.getInputX()) area.setX(ChatBubbleScreen.getInputX());
    }
}
