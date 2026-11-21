package com.niuqu.chatbubble.render;

/** Exact approved palettes for the six non-neutral themes. */
final class ThemePreset {
    private ThemePreset() {}

    static ChatBubbleTheme.Colors colors(ChatBubbleTheme theme) {
        int[] p = switch (theme) {
            case BLUE -> rgb("19263B", "22334F", "22334F", "131E31", "263752", "1E2D45",
                "F3F6FC", "B9C5D8", "8998AE", "65A8FF", "355681", "2B405D", "33455F");
            case GREEN -> rgb("1B2B22", "243A2C", "243A2C", "14231B", "26372C", "203328",
                "F1F7F2", "B9CCBE", "889D8E", "79D69A", "355844", "2B4335", "344B3B");
            case CYAN -> rgb("192C31", "213B42", "213B42", "13252A", "243B40", "1D343A",
                "EFFBFC", "B5CED1", "849EA2", "5FD4E0", "31616A", "29474D", "355157");
            case PINK -> rgb("E7B6C7", "F0C1D0", "F0C1D0", "D49AAF", "EFC0D0", "F3D0DA",
                "402631", "654251", "5E404C", "B94F78", "C87996", "D995AD", "BD8799");
            case ORANGE -> rgb("F0BE87", "F6CB99", "F6CB99", "DDA66F", "F2C48E", "F5D1A5",
                "3F2A1A", "68482F", "5E4426", "BB672C", "CF8A4F", "DCA56F", "C58D58");
            default -> throw new IllegalArgumentException("No preset for " + theme);
        };
        ChatBubbleTheme.Colors b = theme.isLight() ? ChatBubbleTheme.BEIGE.colors() : ChatBubbleTheme.DARK.colors();
        int panel=p[0], title=p[1], bar=p[2], side=p[3], input=p[4], popup=p[5];
        int primary=p[6], secondary=p[7], muted=p[8], accent=p[9], selected=p[10], hover=p[11], divider=p[12];
        return new ChatBubbleTheme.Colors(
            preserveAlpha(b.panelBg(), panel), opaque(title), opaque(bar),
            opaque(side), opaque(hover), opaque(selected), opaque(divider),
            opaque(divider), opaque(input),
            opaque(primary), opaque(secondary), opaque(muted),
            opaque(accent), opaque(muted),
            preserveAlpha(b.popupBg(), popup), preserveAlpha(b.popupHover(), hover), opaque(primary),
            preserveAlpha(b.contextBg(), popup), preserveAlpha(b.contextHover(), hover), opaque(primary),
            opaque(accent),
            opaque(accent), b.whisperBar(),
            preserveAlpha(b.toastBg(), panel), opaque(primary),
            // 滑块直接使用主题强调色（与其它强调色元素一致），悬停略提亮
            opaque(accent),
            opaque(mix(accent, primary, 0.25)),
            opaque(divider), opaque(hover), opaque(primary),
            opaque(secondary), opaque(accent), opaque(accent),
            b.redDot(), b.redDotMention(),
            opaque(primary), opaque(accent), opaque(secondary), preserveAlpha(b.configBg(), panel),
            preserveAlpha(b.bannerBg(), title), opaque(primary), opaque(accent)
        );
    }

    private static int[] rgb(String... values) {
        int[] colors = new int[values.length];
        for (int i = 0; i < values.length; i++) colors[i] = (int) (0xFF000000L | Long.parseLong(values[i], 16));
        return colors;
    }

    private static int mix(int a, int b, double f) {
        int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * f) << 24) | ((int) (ar + (br - ar) * f) << 16)
            | ((int) (ag + (bg - ag) * f) << 8) | (int) (ab + (bb - ab) * f);
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    private static int preserveAlpha(int reference, int color) {
        return (reference & 0xFF000000) | (color & 0x00FFFFFF);
    }
}
