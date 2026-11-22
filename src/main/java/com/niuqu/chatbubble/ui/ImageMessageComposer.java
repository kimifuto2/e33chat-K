package com.niuqu.chatbubble.ui;

import com.niuqu.chatbubble.image.ImageEntry;
import com.niuqu.chatbubble.image.ImageLoader;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 图片消息附件条：最多 9 张，显示在输入框上方。
 *
 * <p>只负责「待发送图片列表 + 缩略图 + 删除 / 继续添加」；文字说明复用主聊天输入框，
 * 发送逻辑在 {@code ChatBubbleScreen.sendMessage()}：输入框里已有文字时作为 caption。
 */
public final class ImageMessageComposer {
    public static final int MAX_IMAGES = 9;
    public static final int CLICK_NONE = -1;
    public static final int CLICK_ADD = -2;

    private static final int GAP = 4;

    private final List<File> files = new ArrayList<>();
    /** 复制聊天里的图片后直接粘贴：保留远端 URL，不把它退化成文字代码。 */
    private final List<String> remoteUrls = new ArrayList<>();

    /** 是否还有待发送图片（本地选择 + 远端 URL）。 */
    public boolean hasImages() { return !files.isEmpty() || !remoteUrls.isEmpty(); }
    public boolean hasFiles() { return !files.isEmpty(); }
    public boolean isFull() { return size() >= MAX_IMAGES; }
    public int size() { return files.size() + remoteUrls.size(); }
    public List<File> files() { return new ArrayList<>(files); }
    public List<String> urls() { return new ArrayList<>(remoteUrls); }

    /** 粘贴 CICode 时加入远端图片；只接受 http/https，去重并受 9 张上限约束。 */
    public boolean addUrl(String url) {
        if (url == null || isFull()) return false;
        String u = url.trim();
        if (!(u.startsWith("http://") || u.startsWith("https://")
                || u.startsWith("e33chat://media/"))) return false;
        if (remoteUrls.contains(u) || files.stream().anyMatch(f -> u.equals(f.getPath()))) return false;
        remoteUrls.add(u);
        return true;
    }

    public int addAllUrls(List<String> incoming) {
        int n = 0;
        if (incoming == null) return 0;
        for (String u : incoming) if (addUrl(u)) n++;
        return n;
    }

    public boolean add(File f) {
        if (f == null || !f.isFile() || isFull() || !isImage(f)) return false;
        for (File old : files) {
            if (old.equals(f)) return false;
        }
        files.add(f);
        return true;
    }

    public int addAll(List<File> incoming) {
        int n = 0;
        if (incoming == null) return 0;
        for (File f : incoming) if (add(f)) n++;
        return n;
    }

    public void remove(int index) {
        if (index < 0) return;
        if (index < files.size()) files.remove(index);
        else {
            int r = index - files.size();
            if (r < remoteUrls.size()) remoteUrls.remove(r);
        }
    }

    public void clear() { files.clear(); remoteUrls.clear(); }

    public static boolean isImage(File f) {
        if (f == null) return false;
        String n = f.getName().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
            || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    private static int thumbSize(int panelW) {
        // 9 张 + 8 个间隙 + 左右内边距；窄面板自动缩小，绝不溢出。
        int avail = Math.max(80, panelW - 4 - 16);
        return Math.max(5, Math.min(28, (avail - (MAX_IMAGES - 1) * GAP) / MAX_IMAGES));
    }

    private int stripH() { return 46; }
    private int stripY(int barTop) { return Math.max(2, barTop - stripH() - 4); }

    public boolean isInsideStrip(double mx, double my, int panelX, int panelW, int barTop) {
        return hasImages() && mx >= panelX + 2 && mx <= panelX + panelW - 2
            && my >= stripY(barTop) && my <= stripY(barTop) + stripH();
    }

    public void render(DrawContext g, int mouseX, int mouseY,
                       TextRenderer font, ChatBubbleTheme.Colors c,
                       int panelX, int panelW, int barTop, float alpha) {
        if (!hasImages()) return;
        int a255 = (int) (255 * alpha);
        int y = stripY(barTop);
        int x = panelX + 2;
        int w = panelW - 4;
        int h = stripH();
        RoundRectRenderer.panel(g, x, y, w, h, 9f,
            ChatBubbleTheme.alphaBlend(c.popupBg(), a255),
            ChatBubbleTheme.alphaBlend(c.divider(), a255));

        int ts = thumbSize(panelW);
        int total = size() + (isFull() ? 0 : 1);
        int contentW = total * ts + (total - 1) * GAP;
        int startX = panelX + (panelW - contentW) / 2;
        int ty = y + (h - ts) / 2;
        for (int i = 0; i < size(); i++) {
            int ex = startX + i * (ts + GAP);
            boolean hover = mouseX >= ex && mouseX <= ex + ts && mouseY >= ty && mouseY <= ty + ts;
            if (hover) {
                RoundRectRenderer.fill(g, ex - 1, ty - 1, ex + ts + 1, ty + ts + 1, 4f,
                    ChatBubbleTheme.alphaBlend(c.popupHover(), a255));
            }
            Identifier tex;
            if (i < files.size()) {
                tex = EmoteStore.texture(files.get(i));
            } else {
                ImageEntry entry = ImageLoader.getOrLoad(remoteUrls.get(i - files.size()));
                tex = entry.state() == ImageEntry.State.LOADED ? entry.textureId() : null;
            }
            if (tex != null) {
                ColoredTextureRenderer.drawWithAlpha(g, tex, ex, ty, ts, ts, alpha);
            } else {
                RoundRectRenderer.fill(g, ex, ty, ex + ts, ty + ts, 3f,
                    ChatBubbleTheme.alphaBlend(c.inputBg(), a255));
                g.drawText(font, "?", ex + ts / 2 - 3, ty + (ts - font.fontHeight) / 2,
                    ChatBubbleTheme.alphaBlend(c.textMuted(), a255), false);
            }
            if (hover) {
                // 右上角删除键：与其它图标按钮同构的 1px 主题描边 + 内层圆角底，
                // 字形按 font.getWidth/fontHeight 真居中（不能用固定 +4 偏移）。
                int bs = 10;
                int bx = ex + ts - bs, by = ty;
                RoundRectRenderer.fill(g, bx, by, bx + bs, by + bs, 4f,
                    ChatBubbleTheme.alphaBlend(c.textMuted(), a255));
                RoundRectRenderer.fill(g, bx + 1, by + 1, bx + bs - 1, by + bs - 1, 3f,
                    ChatBubbleTheme.alphaBlend(c.inputBg(), a255));
                if (hover && mouseX >= bx && mouseX <= bx + bs && mouseY >= by && mouseY <= by + bs) {
                    RoundRectRenderer.fill(g, bx + 1, by + 1, bx + bs - 1, by + bs - 1, 3f,
                        ChatBubbleTheme.alphaBlend(c.popupHover(), a255));
                }
                g.getMatrices().pushMatrix();
                g.getMatrices().translate(bx + bs / 2f, by + bs / 2f);
                g.drawText(font, "✕", -font.getWidth("✕") / 2, -font.fontHeight / 2,
                    ChatBubbleTheme.alphaBlend(c.closeText(), a255), false);
                g.getMatrices().popMatrix();
            }
        }
        if (!isFull()) {
            int ex = startX + size() * (ts + GAP);
            boolean hover = mouseX >= ex && mouseX <= ex + ts && mouseY >= ty && mouseY <= ty + ts;
            if (hover) {
                RoundRectRenderer.fill(g, ex - 1, ty - 1, ex + ts + 1, ty + ts + 1, 4f,
                    ChatBubbleTheme.alphaBlend(c.popupHover(), a255));
            }
            RoundRectRenderer.fill(g, ex, ty, ex + ts, ty + ts, 4f,
                ChatBubbleTheme.alphaBlend(c.inputBg(), a255));
            g.getMatrices().pushMatrix();
            g.getMatrices().translate(ex + ts / 2f, ty + ts / 2f);
            g.drawText(font, "+", -font.getWidth("+") / 2, -font.fontHeight / 2,
                ChatBubbleTheme.alphaBlend(c.textPrimary(), a255), false);
            g.getMatrices().popMatrix();
        }
    }

    /** @return 0..8 = 删除该下标；{@link #CLICK_ADD} = 继续添加；{@link #CLICK_NONE} = 无操作。 */
    public int handleClick(double mx, double my, int panelX, int panelW, int barTop) {
        if (!hasImages()) return CLICK_NONE;
        int y = stripY(barTop);
        int h = stripH();
        if (my < y || my > y + h) return CLICK_NONE;
        int ts = thumbSize(panelW);
        int total = size() + (isFull() ? 0 : 1);
        int contentW = total * ts + (total - 1) * GAP;
        int startX = panelX + (panelW - contentW) / 2;
        int ty = y + (h - ts) / 2;
        for (int i = 0; i < size(); i++) {
            int ex = startX + i * (ts + GAP);
            // 删除命中区比视觉 ✕ 略大，避免 1px 难点
            if (mx >= ex + ts - 12 && mx <= ex + ts && my >= ty && my <= ty + 12) return i;
        }
        if (!isFull()) {
            int ex = startX + size() * (ts + GAP);
            if (mx >= ex && mx <= ex + ts && my >= ty && my <= ty + ts) return CLICK_ADD;
        }
        return CLICK_NONE;
    }
}
