package com.niuqu.chatbubble.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Config persistence contract: zero-valued settings survive a round trip,
 * missing keys fall back to defaults, corrupt files are preserved for recovery. */
class ConfigManagerTest {

    @TempDir
    Path dir;

    @Test
    void zeroValuesSurviveRoundTrip() throws Exception {
        Path p = dir.resolve("client.json");
        ChatBubbleConfig defaults = ChatBubbleConfig.defaults();
        // 用默认配置构造一份只把 5 个零值字段置 0 的配置；避免测试随 record 字段增加而漂移。
        java.lang.reflect.RecordComponent[] components = ChatBubbleConfig.class.getRecordComponents();
        Object[] args = new Object[components.length];
        try {
            for (int i = 0; i < components.length; i++) {
                args[i] = components[i].getAccessor().invoke(defaults);
            }
            String[] zeroFields = {"panelOpacity", "soundVolume", "bannerCornerRadius",
                "mentionBannerDuration", "bannerOpacity"};
            for (int i = 0; i < components.length; i++) {
                for (String field : zeroFields) {
                    if (field.equals(components[i].getName())) args[i] = 0;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        ChatBubbleConfig c = (ChatBubbleConfig) ChatBubbleConfig.class.getDeclaredConstructors()[0].newInstance(args);
        ConfigManager.save(p, c);

        ChatBubbleConfig loaded = ConfigManager.load(p);
        assertEquals(0, loaded.panelOpacity(), "panelOpacity 0 (fully transparent) must survive");
        assertEquals(0, loaded.soundVolume(), "soundVolume 0 (muted) must survive");
        assertEquals(0, loaded.bannerCornerRadius(), "bannerCornerRadius 0 (square) must survive");
        assertEquals(0, loaded.mentionBannerDuration(), "mentionBannerDuration 0 must survive");
        assertEquals(0, loaded.bannerOpacity(), "bannerOpacity 0 (invisible) must survive");
    }

    @Test
    void missingKeysFallBackToDefaults() throws Exception {
        Path p = dir.resolve("client.json");
        // Hand-written legacy file without the new nullable keys.
        Files.writeString(p, "{"
            + "\"enabled\":true,\"theme\":\"dark\",\"panelOpacity\":40,\"soundVolume\":60"
            + "}", StandardCharsets.UTF_8);

        ChatBubbleConfig loaded = ConfigManager.load(p);
        assertEquals(40, loaded.panelOpacity(), "explicit panelOpacity must load");
        assertEquals(60, loaded.soundVolume(), "explicit soundVolume must load");
        assertEquals(4, loaded.bannerCornerRadius(), "missing corner radius falls back to default");
        assertEquals(4, loaded.mentionBannerDuration(), "missing duration falls back to default");
        assertFalse(loaded.closeChatOnSend(), "missing close_chat_on_send falls back to default (off)");
        assertEquals(100, loaded.bannerOpacity(), "missing banner opacity falls back to default 100");
        assertEquals(3, loaded.bannerMaxStack(), "missing banner max stack falls back to default 3");
    }

    @Test
    void corruptFileIsPreservedAsBackup() throws Exception {
        Path p = dir.resolve("client.json");
        Files.writeString(p, "{ this is not json", StandardCharsets.UTF_8);

        ChatBubbleConfig loaded = ConfigManager.load(p);
        assertNotNull(loaded);
        assertTrue(loaded.enabled(), "corrupt file falls back to defaults");
        assertTrue(Files.exists(dir.resolve("client.json.bak")), "corrupt file kept for manual recovery");
        assertTrue(Files.exists(p), "fresh default file written");
    }
}
