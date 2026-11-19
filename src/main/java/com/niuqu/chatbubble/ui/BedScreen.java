package com.niuqu.chatbubble.ui;
import com.niuqu.chatbubble.ChatBubbleScreen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class BedScreen extends Screen {

    private static Screen screenBeforeSleep;
    /** 「起床」后的一小段时间内不再自动弹出床屏（起床包要一个往返才生效）。 */
    private static long suppressUntil;

    public static void suppressFor(long millis) { suppressUntil = Util.getMeasuringTimeMs() + millis; }

    public static boolean isSuppressed() { return Util.getMeasuringTimeMs() < suppressUntil; }

    public BedScreen() {
        super(Text.translatable("multiplayer.stopSleeping"));
    }

    public static void setScreenBeforeSleep(Screen screen) {
        screenBeforeSleep = screen;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("multiplayer.stopSleeping"), b -> sendWakeUp())
            .dimensions(width / 2 - 100, height - 40, 200, 20).build());
    }

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void tick() {
        if (client == null || client.player == null || !client.player.isSleeping()) {
            client.setScreen(null);
            if (screenBeforeSleep instanceof ChatBubbleScreen) {
                client.setScreen(screenBeforeSleep);
            }
            screenBeforeSleep = null;
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            sendWakeUp();
            return true;
        }
        if (client.options.chatKey.matchesKey(input)) {
            client.setScreen(new ChatBubbleScreen(""));
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void sendWakeUp() {
        if (client != null && client.player != null) {
            client.player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(client.player, ClientCommandC2SPacket.Mode.STOP_SLEEPING));
        }
    }
}
