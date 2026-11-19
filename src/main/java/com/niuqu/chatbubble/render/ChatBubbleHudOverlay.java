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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.widget.ButtonWidget;

public class ChatBubbleHudOverlay {

    private static final int ICON_S = 16;
    private static final int SRC_U = 6;
    private static final int SRC_V = 6;
    private static final int SRC_S = 4;
    private static final int TIP_DISP = 4;

    /** 睡觉时在 HUD 层画的那颗「起床」按钮（尺寸变化时重建）。 */
    private static ButtonWidget leaveBedButton;
    private static int leaveBedW = -1, leaveBedH = -1;

    private static ChatBubbleConfig cfg() { return ChatBubbleClientSetup.config(); }

    private static Identifier chatIconTex() {
        String theme = Appearance.resolvedTheme().name().toLowerCase();
        return Identifier.of("e33chat", "textures/gui/" + theme + "/chat_icon.png");
    }

    private static ChatBubbleTheme theme() {
        return Appearance.resolvedTheme();
    }

    private static ChatBubbleTheme.Colors c() { return Appearance.snapshot(); }

    public static void render(DrawContext g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;
        // F1 隐藏 HUD 时，本模组的聊天图标/提示条/通知横幅也一起隐藏（与原生 HUD 行为一致）
        if (mc.options.hudHidden) return;

        g.getMatrices().pushMatrix();
        g.getMatrices().translate(0, 0);

        MentionNotificationBanner.INSTANCE.tick();
        boolean chatPanelOpen = mc.currentScreen instanceof ChatBubbleScreen;

        // 睡觉时那颗原版「起床」按钮：画在 **HUD 层**（在屏幕内容之前绘制）。
        // 于是它「一直显示」（打开聊天面板也不消失），而任何界面（聊天面板 / BedScreen）
        // 都画在它上面 —— 面板永远压着它，不会被它盖住。
        drawLeaveBedButton(g, mc);

        if (mc.currentScreen == null) {
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight());
        } else if (chatPanelOpen) {
            // 聊天面板打开时：常驻音乐岛画在 HUD 层（在屏幕内容之前绘制），
            // 于是它落在面板**下方**、被面板盖住，不再遮挡聊天内容；
            // @ 等瞬时通知横幅仍由 renderBannerForScreen 叠在面板最上层。
            MusicIslandBanner.INSTANCE.render(g,
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight());
        }

        // 睡觉时（BedScreen）也显示左下角的聊天图标 / [T] 提示（用户要求），
        // 面板打开时仍按原样隐藏。
        if (mc.currentScreen != null && !(mc.currentScreen instanceof com.niuqu.chatbubble.ui.BedScreen)) {
            g.getMatrices().popMatrix(); return;
        }

        String keyName = mc.options.chatKey.getBoundKeyLocalizedText().getString();
        int screenH = mc.getWindow().getScaledHeight();
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
            int keyW = mc.textRenderer.getWidth(keyDisplay);
            int keyX = keyW > ICON_S ? x : x + (ICON_S - keyW) / 2;
            g.drawText(mc.textRenderer, keyDisplay, keyX, textY, 0xFFFFFFFF, false);
        }

        g.getMatrices().popMatrix();
    }

    // Fabric's HUD layer draws behind the screen batch; screens that render over
    // it re-invoke this so the banner stays visible on top.
    // 音乐岛不在这里画：面板打开时它已改由 HUD 层画在面板下方（见 render），
    // 否则会挡住聊天内容。@ 等瞬时横幅仍叠在最上层。
    public static void renderBannerForScreen(DrawContext g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;
        if (mc.currentScreen instanceof ChatBubbleScreen) {
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight(),
                false);
        }
    }

    /**
     * 睡觉时在 HUD 层画一颗原版「起床」按钮（与 {@code SleepingChatScreen} 里那颗同款、同坐标）。
     *
     * <p>HUD 层在屏幕内容**之前**绘制，所以这颗按钮「一直存在」：打开聊天面板不会让它消失，
     * 而任何界面（聊天面板 / 床上的 BedScreen）都画在它上面 —— 面板永远压在它上面。
     * 这里只负责显示，点击由当前界面的按钮处理（在床上界面是 BedScreen 自己那颗）。
     */
    private static void drawLeaveBedButton(DrawContext g, MinecraftClient mc) {
        if (mc.player == null || !mc.player.isSleeping()) return;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        if (leaveBedButton == null || leaveBedW != w || leaveBedH != h) {
            leaveBedW = w;
            leaveBedH = h;
            leaveBedButton = ButtonWidget.builder(Text.translatable("multiplayer.stopSleeping"), b -> { })
                .dimensions(w / 2 - 100, h - 40, 200, 20).build();
        }
        int mx = (int) (mc.mouse.getX() * (double) w / Math.max(1, mc.getWindow().getWidth()));
        int my = (int) (mc.mouse.getY() * (double) h / Math.max(1, mc.getWindow().getHeight()));
        leaveBedButton.render(g, mx, my, 0f);
    }

    public static boolean isMouseOverIcon(double mx, double my) {
        if (cfg().hideChatIcon()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null && !(mc.currentScreen instanceof com.niuqu.chatbubble.ui.BedScreen)) return false;
        int screenH = mc.getWindow().getScaledHeight();
        int iconY = screenH - ICON_S - 20;
        return mx >= 3 && mx <= 3 + ICON_S && my >= iconY && my <= iconY + ICON_S + mc.textRenderer.fontHeight + 2;
    }


    private static void drawIcon(DrawContext g, int x, int y) {
        // getTexture 无缓存时自动 new ResourceTexture 懒加载（资源包可覆盖，F3+T 即时生效）
        g.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, chatIconTex(), x, y, 0.0F, 0.0F, ICON_S, ICON_S, ICON_S, ICON_S);
    }

    private static void drawScaledTip(DrawContext g, int x, int y, int disp) {
        Identifier tex = ChatBubbleScreen.iconTex("private_tip");
        g.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, tex, x, y, (float) SRC_U, (float) SRC_V, disp, disp, SRC_S, SRC_S, 16, 16);
    }
}
