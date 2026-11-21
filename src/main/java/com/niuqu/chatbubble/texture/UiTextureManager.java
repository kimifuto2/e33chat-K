package com.niuqu.chatbubble.texture;

import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.render.Appearance;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import net.minecraft.resources.Identifier;

/**
 * UI 纹理管理：仅负责按当前主题拼纹理 ID。
 * 渲染处 drawTexture 直接引用 Identifier，TextureManager.getTexture 无缓存时自动 new ResourceTexture
 * 从资源栈（用户资源包 > mod jar 内置 16×16 PNG）懒加载；F3+T 重载后自动重读新 PNG。
 */
public final class UiTextureManager {

    private UiTextureManager() {}

    /** 当前配置主题下元素的纹理 ID。 */
    public static Identifier rl(UiElement el) {
        // 走两参数重载，午夜蓝等“代码皮肤”主题才能返回 skin/* 而不是不存在的 gui/midnight/*.png
        return rl(el, currentTheme());
    }

    /** 指定主题下元素的纹理 ID（配置界面等固定主题场景使用）。*/
    public static Identifier rl(UiElement el, ChatBubbleTheme theme) {
        if (theme != ChatBubbleTheme.DARK && theme != ChatBubbleTheme.BEIGE) {
            return Identifier.fromNamespaceAndPath("e33chat", "skin/" + theme.name().toLowerCase() + "/" + el.name().toLowerCase());
        }
        return el.rl(theme);
    }

    /** Physical PNG shared by the themes with the same contrast direction. */
    public static Identifier png(String name, ChatBubbleTheme theme) {
        return Identifier.fromNamespaceAndPath("e33chat", "textures/gui/" + theme.textureDirectory() + "/" + name + ".png");
    }

    /** 当前主题的物理 PNG（白色剪影 × tint 的动态着色元素用它，不要用 {@link #rl(UiElement)}——后者对彩色主题返回代码绘制路径）。 */
    public static Identifier png(String name) {
        return png(name, currentTheme());
    }

    public static ChatBubbleTheme currentTheme() {
        return Appearance.resolvedTheme();
    }
}
