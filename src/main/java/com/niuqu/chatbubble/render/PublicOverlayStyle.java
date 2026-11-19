package com.niuqu.chatbubble.render;

/**
 * 公屏消息小窗的四种视觉样式（用户在配置屏里自选，默认 C）。
 *
 * <ul>
 *   <li>{@link #A} 纯文字：无底板，白字 + 深色描边阴影，最贴近原版聊天；</li>
 *   <li>{@link #B} 半透明面板：一整块半透明圆角底板常驻 N 行；</li>
 *   <li>{@link #C} 每行气泡：每行一个独立的小圆角气泡，错落堆叠；</li>
 *   <li>{@link #D} 单行跑马灯：只显示最新 1 条，新消息替换并淡入。</li>
 * </ul>
 */
public enum PublicOverlayStyle {
    A("a"),
    B("b"),
    C("c"),
    D("d");

    private final String key;

    PublicOverlayStyle(String key) {
        this.key = key;
    }

    /** 配置里存的字符串（小写单个字母），也是 lang key 的后缀。 */
    public String key() {
        return key;
    }

    /** 大小写不敏感解析；非法/缺省一律回落 C（用户指定的默认效果）。 */
    public static PublicOverlayStyle parse(String s) {
        if (s == null) return C;
        return switch (s.trim().toLowerCase()) {
            case "a" -> A;
            case "b" -> B;
            case "d" -> D;
            default -> C;
        };
    }
}
