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
    DARK("dark", ChatBubbleTheme.ColorMode.DARK, 0x000000, 0.00, 0xFFFFFF, 0.00, 0, 0.00),
    /** 米色浅色（原 light）。 */
    LIGHT("light", ChatBubbleTheme.ColorMode.LIGHT, 0x000000, 0.00, 0x2D2D1F, 0.00, 0, 0.00),
    /** 午夜蓝：深海军蓝底 + 冷白字。 */
    MIDNIGHT("midnight", ChatBubbleTheme.ColorMode.DARK, 0xFF17243C, 0.60, 0xFFE6EEFF, 0.28, 222, 0.42),
    /** 森野绿：深林绿底 + 微绿白字。 */
    FOREST("forest", ChatBubbleTheme.ColorMode.DARK, 0xFF16241A, 0.60, 0xFFE9F4E3, 0.28, 110, 0.30),
    /** 深海青：墨青底 + 冰蓝白字。 */
    OCEAN("ocean", ChatBubbleTheme.ColorMode.DARK, 0xFF102530, 0.62, 0xFFE2F4F8, 0.26, 192, 0.38),
    /** 极简灰：中性深灰底 + 纯白字（低饱和）。 */
    MONO("mono", ChatBubbleTheme.ColorMode.DARK, 0xFF262626, 0.45, 0xFFFFFFFF, 0.30, 0, 0.04),
    /** 樱花粉：藕粉浅底 + 深梅字。 */
    SAKURA("sakura", ChatBubbleTheme.ColorMode.LIGHT, 0xFFF7E4EA, 0.58, 0xFF3B2430, 0.30, 342, 0.30),
    /** 秋褐橙：暖沙浅底 + 深褐字。 */
    AUTUMN("autumn", ChatBubbleTheme.ColorMode.LIGHT, 0xFFF4E6D4, 0.58, 0xFF3A2A18, 0.30, 32, 0.34);

    private final String key;
    private final ChatBubbleTheme.ColorMode mode;
    private final int bgTint;
    private final double bgMix;
    private final int fgTint;
    private final double fgMix;
    /** 纹理重着色用的目标色相（度）与饱和度；DARK/LIGHT 不重着色。 */
    private final int hue;
    private final double sat;
    private ChatBubbleTheme.Colors cached;

    ThemePreset(String key, ChatBubbleTheme.ColorMode mode, int bgTint, double bgMix,
                int fgTint, double fgMix, int hue, double sat) {
        this.key = key;
        this.mode = mode;
        this.bgTint = bgTint;
        this.bgMix = bgMix;
        this.fgTint = fgTint;
        this.fgMix = fgMix;
        this.hue = hue;
        this.sat = sat;
    }

    /** 配置里存的值，也是 lang key 后缀（e33chat.theme.&lt;key&gt;）。 */
    public String key() {
        return key;
    }

    /** 深/浅模式：决定纹理目录与「浅色主题」分支逻辑。 */
    public ChatBubbleTheme.ColorMode mode() {
        return mode;
    }

    /** 是否需要把 UI 纹理重着色（DARK/LIGHT 用原纹理，其余预设按色相/饱和度重绘）。 */
    public boolean recolorsTextures() {
        return this != DARK && this != LIGHT;
    }

    /**
     * 单个像素的重着色：保留原明度与 alpha，只把色相换成预设色相、饱和度拉到预设值。
     * 这样面板纹理的圆角/描边/明暗层次全部保留，但颜色是**真的变了**（不是叠一层滤镜）。
     */
    public int recolor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return argb;
        double r = ((argb >>> 16) & 0xFF) / 255.0;
        double g = ((argb >>> 8) & 0xFF) / 255.0;
        double b = (argb & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2.0;
        double d = max - min;
        double s0 = (d == 0.0) ? 0.0 : d / (1.0 - Math.abs(2.0 * l - 1.0));
        // 保留一点原饱和度差异（同一张纹理内部仍有层次），其余拉到预设饱和度
        double s = Math.min(1.0, s0 * 0.25 + sat * 0.75);
        // 深色系的纹理本身很暗（l≈0.12），色相变化会被压得几乎看不见：整体提一点明度，
        // 让「午夜蓝/森野绿」这类预设真正看得出颜色（浅色系不动，本来就是高明度）。
        if (mode == ChatBubbleTheme.ColorMode.DARK) {
            l = Math.min(0.92, l * 1.22 + 0.015);
            s = Math.min(1.0, s * 1.10);
        }
        return hslToArgb(a, hue / 360.0, s, l);
    }

    private static int hslToArgb(int a, double h, double s, double l) {
        double c = (1.0 - Math.abs(2.0 * l - 1.0)) * s;
        double hp = h * 6.0;
        double x = c * (1.0 - Math.abs(hp % 2.0 - 1.0));
        double r1 = 0, g1 = 0, b1 = 0;
        if (hp < 1) { r1 = c; g1 = x; }
        else if (hp < 2) { r1 = x; g1 = c; }
        else if (hp < 3) { g1 = c; b1 = x; }
        else if (hp < 4) { g1 = x; b1 = c; }
        else if (hp < 5) { r1 = x; b1 = c; }
        else { r1 = c; b1 = x; }
        double m = l - c / 2.0;
        int r = (int) Math.round(Math.max(0, Math.min(255, (r1 + m) * 255)));
        int g = (int) Math.round(Math.max(0, Math.min(255, (g1 + m) * 255)));
        int b = (int) Math.round(Math.max(0, Math.min(255, (b1 + m) * 255)));
        return (a << 24) | (r << 16) | (g << 8) | b;
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
