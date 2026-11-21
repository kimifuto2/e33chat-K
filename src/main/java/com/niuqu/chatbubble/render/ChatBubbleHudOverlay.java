package com.niuqu.chatbubble.render;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.render.Appearance;

import com.mojang.blaze3d.systems.RenderSystem;
import com.niuqu.chatbubble.chat.notification.MentionNotificationBanner;
import com.niuqu.chatbubble.chat.notification.MusicIslandBanner;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ChatBubbleHudOverlay {

    private static final int ICON_S = 16;
    private static final int SRC_U = 6;
    private static final int SRC_V = 6;
    private static final int SRC_S = 4;
    private static final int TIP_DISP = 4;

    /** 睡觉时在 HUD 层画的那颗「起床」按钮（尺寸变化时重建）。 */
    private static Button leaveBedButton;
    private static int leaveBedW = -1, leaveBedH = -1;

    private static ChatBubbleConfig cfg() { return ChatBubbleClientSetup.config(); }

    private static Identifier chatIconTex() {
        return com.niuqu.chatbubble.texture.UiTextureManager.png("chat_icon", Appearance.resolvedTheme());
    }

    private static ChatBubbleTheme theme() {
        return Appearance.resolvedTheme();
    }

    private static ChatBubbleTheme.Colors c() { return Appearance.snapshot(); }

    public static void render(GuiGraphicsExtractor raw) {
        UiCanvas g = UiCanvas.of(raw);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) return;
        // F1 隐藏 HUD 时，本模组的聊天图标/提示条/通知横幅也一起隐藏（与原生 HUD 行为一致）
        if (mc.gui.hud.isHidden()) return;

        g.getMatrices().pushMatrix();
        g.getMatrices().translate(0, 0);

        MentionNotificationBanner.INSTANCE.tick();
        boolean chatPanelOpen = mc.gui.screen() instanceof ChatBubbleScreen;

        // 睡觉时那颗原版「起床」按钮：画在 **HUD 层**（屏幕内容之前）。于是它「一直显示」
        // （打开聊天面板也不消失），而任何界面（聊天面板 / BedScreen）都画在它上面。
        drawLeaveBedButton(raw, mc);

        if (mc.gui.screen() == null) {
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        } else if (chatPanelOpen) {
            // 聊天面板打开时：常驻音乐横幅画在 HUD 层。Gui.extractRenderState 里先提取
            // hud、后提取 screen，所以 HUD 层的画在面板**下方**、被面板盖住（与 1.21.11 一致）；
            // 不能再走 ScreenEvents.afterExtract 那条路径，那会浮到面板之上。
            MusicIslandBanner.INSTANCE.render(g,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        }

        // 睡觉时（BedScreen）也显示左下角的聊天图标 / [T] 提示（用户要求）；
        // 聊天面板打开时仍按原样隐藏。
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof com.niuqu.chatbubble.ui.BedScreen)) {
            g.getMatrices().popMatrix(); return;
        }

        String keyName = mc.options.keyChat.getTranslatedKeyMessage().getString();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x = 3;
        int iconY = screenH - ICON_S - 20;
        int textY = iconY + ICON_S + 1;

        // 公屏消息小窗：只有聊天面板关闭时才画（上面已对 currentScreen 早退，F1 也在入口早退），
        // 位置钉在聊天图标正上方、与图标左对齐，先画面板再画图标，两者不会互相遮挡
        PublicChatOverlay.render(g, x, iconY);

        if (!cfg().hideChatIcon()) {
            drawIcon(g, x, iconY);

            if (cfg().redDotEnabled() && ChatMessageStore.getUnreadCount() > 0) {
                double wave = Math.abs(Math.sin(System.currentTimeMillis() / 300.0)) * 3;
                int tipX = x + ICON_S - TIP_DISP / 2;
                int tipY = iconY - TIP_DISP / 2 + (int) wave;
                drawScaledTip(g, tipX, tipY, TIP_DISP);
            }

            String keyDisplay = "[" + keyName + "]";
            int keyW = mc.font.width(keyDisplay);
            int keyX = keyW > ICON_S ? x : x + (ICON_S - keyW) / 2;
            g.drawText(mc.font, keyDisplay, keyX, textY, hintColor(), false);
        }

        g.getMatrices().popMatrix();
    }

    // Fabric's HUD layer draws behind the screen batch; screens that render over
    // it re-invoke this so the banner stays visible on top
    public static void renderBannerForScreen(GuiGraphicsExtractor raw) {
        UiCanvas g = UiCanvas.of(raw);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) return;
        if (mc.gui.screen() instanceof ChatBubbleScreen) {
            // 只画 @ 横幅：音乐横幅已由 HUD 层画在面板下方，这里再画一遍就会浮到面板之上
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), false);
        }
    }

    /**
     * 睡觉时在 HUD 层画一颗原版「起床」按钮（与 {@code InBedChatScreen} 里那颗同款、同坐标）。
     *
     * <p>HUD 层在屏幕内容**之前**绘制，所以这颗按钮「一直存在」：打开聊天面板不会让它消失，
     * 而任何界面（聊天面板 / 床上的 BedScreen）都画在它上面 —— 面板永远压在它上面。
     * 这里只负责显示，点击由当前界面自己的按钮处理。
     */
    private static void drawLeaveBedButton(GuiGraphicsExtractor raw, Minecraft mc) {
        if (mc.player == null || !mc.player.isSleeping()) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        if (leaveBedButton == null || leaveBedW != w || leaveBedH != h) {
            leaveBedW = w;
            leaveBedH = h;
            leaveBedButton = Button.builder(Component.translatable("multiplayer.stopSleeping"), b -> { })
                .bounds(w / 2 - 100, h - 40, 200, 20).build();
        }
        int mx = (int) (mc.mouseHandler.xpos() * (double) w / Math.max(1, mc.getWindow().getWidth()));
        int my = (int) (mc.mouseHandler.ypos() * (double) h / Math.max(1, mc.getWindow().getHeight()));
        leaveBedButton.extractRenderState(raw, mx, my, 0f);
    }

    public static boolean isMouseOverIcon(double mx, double my) {
        if (cfg().hideChatIcon()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof com.niuqu.chatbubble.ui.BedScreen)) return false;
        int screenH = mc.getWindow().getGuiScaledHeight();
        int iconY = screenH - ICON_S - 20;
        return mx >= 3 && mx <= 3 + ICON_S && my >= iconY && my <= iconY + ICON_S + mc.font.lineHeight + 2;
    }


    private static int hintColor() {
        return 0xFF000000 | (c().quoteBar() & 0xFFFFFF);
    }

    private static void drawIcon(UiCanvas g, int x, int y) {
        // 白色剪影 PNG × 主题强调色 tint（资源包仍可覆盖 PNG，F3+T 即时生效）
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawTinted(g, chatIconTex(), x, y, ICON_S, ICON_S, hintColor());
    }

    private static void drawScaledTip(UiCanvas g, int x, int y, int disp) {
        Identifier tex = ChatBubbleScreen.iconTex("private_tip");
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawTinted(g, tex, x, y, disp, disp,
            (float) SRC_U, (float) SRC_V, SRC_S, SRC_S, 16, 16, hintColor());
    }
}
