package com.niuqu.chatbubble.mixin;

import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestions.class)
public interface ChatInputSuggestorAccessor {
    @Accessor("suggestions")
    CommandSuggestions.SuggestionsList getWindow();

    @Accessor("commandUsagePosition")
    void e33chat$setUsageX(int x);

    @Accessor("commandUsageWidth")
    void e33chat$setUsageWidth(int w);

    /** 当前用法/报错提示行；空 = 没有提示行。 */
    @Accessor("commandUsage")
    java.util.List<net.minecraft.util.FormattedCharSequence> getCommandUsage();
}
