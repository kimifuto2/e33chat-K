package com.niuqu.chatbubble.render;

import java.util.Map;

/**
 * 主题预设（外观配色方案）。配置项 {@code theme} 存的就是这里的 {@link #key()}。
 *
 * <p>预设只改变**颜色**，不改变纹理：纹理目录仍由 {@link #mode()}（深/浅）决定。
 * 每个预设都从 {@link ChatBubbleTheme} 的深浅基准调色板派生 —— 把「背景族」往
 * {@link #bgTint} 混、把「文字族」往 {@link #fgTint} 混，得到同一套对比结构下的
 * 不同色相，避免手工写 44 个颜色字面量带来的不一致。
 *
 * <p>颜色优先级链固定为：**预设 base → 强调色混合（约 10 个字段）→ themeCustom 覆盖**，
 * 因此玩家自定义的单字段颜色永远能盖住预设。
 */
public enum ThemePreset {
    /** 经典暗色（原 dark）。 */
    DARK("dark", ChatBubbleTheme.ColorMode.DARK, 0x000000, 0.00, 0xFFFFFF, 0.00),
    /** 米色浅色（原 light）。 */
    LIGHT("light", ChatBubbleTheme.ColorMode.LIGHT, 0x000000, 0.00, 0x2D2D1F, 0.00),
    /** 午夜蓝：深海军蓝底 + 冷白字。 */
    MIDNIGHT("midnight", ChatBubbleTheme.ColorMode.DARK, 0xFF17243C, 0.60, 0xFFE6EEFF, 0.28),
    /** 森野绿：深林绿底 + 微绿白字。 */
    FOREST("forest", ChatBubbleTheme.ColorMode.DARK, 0xFF16241A, 0.60, 0xFFE9F4E3, 0.28),
    /** 深海青：墨青底 + 冰蓝白字。 */
    OCEAN("ocean", ChatBubbleTheme.ColorMode.DARK, 0xFF102530, 0.62, 0xFFE2F4F8, 0.26),
    /** 极简灰：中性深灰底 + 纯白字（低饱和）。 */
    MONO("mono", ChatBubbleTheme.ColorMode.DARK, 0xFF262626, 0.45, 0xFFFFFFFF, 0.30),
    /** 樱花粉：藕粉浅底 + 深梅字。 */
    SAKURA("sakura", ChatBubbleTheme.ColorMode.LIGHT, 0xFFF7E4EA, 0.58, 0xFF3B2430, 0.30),
    /** 秋褐橙：暖沙浅底 + 深褐字。 */
    AUTUMN("autumn", ChatBubbleTheme.ColorMode.LIGHT, 0xFFF4E6D4, 0.58, 0xFF3A2A18, 0.30);

    private final String key;
    private final ChatBubbleTheme.ColorMode mode;
    private final int bgTint;
    private final double bgMix;
    private final int fgTint;
    private final double fgMix;
    private ChatBubbleTheme.Colors cached;

    ThemePreset(String key, ChatBubbleTheme.ColorMode mode, int bgTint, double bgMix,
                int fgTint, double fgMix) {
        this.key = key;
        this.mode = mode;
        this.bgTint = bgTint;
        this.bgMix = bgMix;
        this.fgTint = fgTint;
        this.fgMix = fgMix;
    }

    /** 配置里存的值，也是 lang key 后缀（e33chat.theme.&lt;key&gt;）。 */
    public String key() {
        return key;
    }

    /** 深/浅模式：决定纹理目录与「浅色主题」分支逻辑。 */
    public ChatBubbleTheme.ColorMode mode() {
        return mode;
    }

    /** 该预设的基准调色板（未套强调色、未套自定义覆盖）。 */
    public ChatBubbleTheme.Colors baseColors() {
        if (cached != null) return cached;
        ChatBubbleTheme.Colors b = (mode == ChatBubbleTheme.ColorMode.LIGHT)
            ? ChatBubbleTheme.LIGHT.colors() : ChatBubbleTheme.DARK.colors();
        if (bgMix <= 0.0 && fgMix <= 0.0) {
            cached = b;
            return b;
        }
        // 背景族：面板/标题/底栏/侧边栏/输入框/弹层/横幅/提示条 —— 往 bgTint 混
        // 文字族：正文/次要/弱化/名字/时间/弹层字/横幅字 —— 往 fgTint 混
        // 标记色（红点、引用条、合并计数、滚动条、关闭按钮底色）保持原预设语义，不参与混色。
        ChatBubbleTheme.Colors out = new ChatBubbleTheme.Colors(
            mix(b.panelBg(), bgTint, bgMix), mix(b.titleBg(), bgTint, bgMix * 0.7), mix(b.barBg(), bgTint, bgMix * 0.7),
            mix(b.sidebarBg(), bgTint, bgMix * 0.9), mix(b.sidebarItemHover(), bgTint, bgMix * 0.8),
            mix(b.sidebarItemSelected(), bgTint, bgMix * 0.8), b.sidebarDivider(),
            b.divider(), mix(b.inputBg(), bgTint, bgMix * 0.8),
            mix(b.textPrimary(), fgTint, fgMix), mix(b.textSecondary(), fgTint, fgMix * 0.8),
            mix(b.textMuted(), fgTint, fgMix * 0.6),
            mix(b.nameColor(), fgTint, fgMix * 0.7), mix(b.timeColor(), fgTint, fgMix * 0.6),
            mix(b.popupBg(), bgTint, bgMix), mix(b.popupHover(), bgTint, bgMix * 0.8),
            mix(b.popupText(), fgTint, fgMix),
            mix(b.contextBg(), bgTint, bgMix), mix(b.contextHover(), bgTint, bgMix * 0.8),
            mix(b.contextText(), fgTint, fgMix),
            mix(b.iconHover(), bgTint, bgMix * 0.8),
            mix(b.notificationText(), fgTint, fgMix), mix(b.whisperBar(), bgTint, bgMix * 0.5),
            mix(b.toastBg(), bgTint, bgMix), mix(b.toastText(), fgTint, fgMix),
            b.scrollbar(), b.scrollbarHover(),
            mix(b.closeBg(), bgTint, bgMix), mix(b.closeHoverBg(), bgTint, bgMix * 0.8),
            mix(b.closeText(), fgTint, fgMix),
            mix(b.systemText(), fgTint, fgMix * 0.8), b.quoteBar(), b.duplicateLabel(),
            b.redDot(), b.redDotMention(),
            mix(b.configTitle(), fgTint, fgMix), mix(b.configSection(), fgTint, fgMix * 0.9),
            mix(b.configLabel(), fgTint, fgMix), mix(b.configBg(), bgTint, bgMix * 0.9),
            mix(b.bannerBg(), bgTint, bgMix), mix(b.bannerText(), fgTint, fgMix * 0.5),
            b.bannerBar()
        );
        cached = out;
        return out;
    }

    private static int mix(int base, int tint, double f) {
        return ChatBubbleTheme.mix(base, tint, Math.max(0.0, Math.min(1.0, f)));
    }

    /** 大小写不敏感解析；未知/缺省回退 {@link #DARK}（兼容旧配置 "dark"/"light"）。 */
    public static ThemePreset parse(String s) {
        if (s == null) return DARK;
        String v = s.trim().toLowerCase();
        for (ThemePreset p : values()) {
            if (p.key.equals(v)) return p;
        }
        return DARK;
    }

    /** 循环切换到下一个预设（聊天窗口主题按钮用）。 */
    public ThemePreset next() {
        ThemePreset[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** 当前配置的预设（便捷入口，避免各处重复 parse 配置字符串）。 */
    public static ThemePreset current() {
        return parse(com.niuqu.chatbubble.ChatBubbleClientSetup.config().theme());
    }

    /** 与 {@link #baseColors()} 相同的调色板，但已套强调色与自定义覆盖（供预览/测试用）。 */
    public ChatBubbleTheme.Colors resolve(ChatBubbleTheme.Accent accent, Map<String, Integer> custom) {
        return ChatBubbleTheme.resolve(this, accent, custom);
    }
}
