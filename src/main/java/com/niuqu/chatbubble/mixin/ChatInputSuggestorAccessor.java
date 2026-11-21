package com.niuqu.chatbubble.mixin;

import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatInputSuggestor.class)
public interface ChatInputSuggestorAccessor {
    @Accessor("window")
    ChatInputSuggestor.SuggestionWindow getWindow();

    /** 提示行/候选窗的横向基准（原版只在初始化时算一次，需要每帧同步）。 */
    @Accessor("x")
    void e33chat$setSuggestX(int x);

    @Accessor("width")
    void e33chat$setSuggestWidth(int width);
}
