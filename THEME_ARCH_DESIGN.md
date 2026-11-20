# E33Chat 主题架构重构设计稿（Mode / Palette / Assets 拆分）

> 基线：已回退全部主题改造（main `d50c480` / mc26.3 `927d493` / mc26.2 `1d1a1dc`）。
> 本文只描述**设计与迁移步骤**，不改代码。目标：让「加一套配色」不再等于「重画 39 张纹理」。

---

## 1. 现状（回退后的真实结构）

```
配置 theme: "dark" | "light"
        │
        ├─ Appearance.resolvedTheme() ──► ChatBubbleTheme.DARK / LIGHT
        │                                   ├─ colors() 42 字段（全量写死）
        │                                   └─ 纹理目录 textures/gui/{dark,light}/（39 张）
        │
        ├─ Appearance.snapshot()
        │     resolve(mode, accent, themeCustom)
        │       = base.colors()
        │         → 强调色混合（约 10 个字段）
        │         → themeCustom 逐字段覆盖（42 键，最后生效）
        │
        └─ UiTextureManager.rl(el) / iconTex(name)
              路径固定拼 theme.name().toLowerCase()
```

**核心问题**：`ColorMode` 一个枚举同时承担三种职责

| 职责 | 现在由谁承担 | 后果 |
|---|---|---|
| 明暗语义（浅底深字 / 深底浅字） | `ColorMode` | 加第 3 种配色被迫选边 |
| 资源选择（纹理目录、图标剪影） | `ColorMode` | 每套配色必须复用 dark 或 light 纹理 |
| 配置值（`theme` 字符串） | `ColorMode` | 值域被写死成两个 |

`Accent`（6 色）与 `themeCustom`（42 键覆盖）已存在，但它们是**在同一套纹理上的颜色微调**，解决不了“换皮肤”。

---

## 2. 目标架构

### 2.1 三个概念拆开

```
ThemeMode    LIGHT | DARK           ← 只决定：文字对比方向、图标剪影基色
ThemePalette 42 色 + mode + 元数据   ← 任意多套，互不继承
ThemeAssets  可选纹理集（目录/tint）  ← null = 纯代码绘制（推荐给新主题）
Theme        = ThemePalette (+ ThemeAssets)
```

### 2.2 类结构（示意，包名沿用 `render/`）

```java
public enum ThemeMode { LIGHT, DARK }

/** 可选纹理集：目录名 + 图标 tint（图标为白色剪影，直接染色即可）。 */
public record ThemeAssets(String dir, int iconTint) {}

/** 一套完整主题：颜色 + 明暗模式 + 可选纹理集。 */
public record ThemePalette(
        String id,                 // "dark" / "light" / "midnight" / 用户自定义 id
        ThemeMode mode,
        String displayKey,         // lang key：e33chat.palette.<id>
        int sortOrder,
        ChatBubbleTheme.Colors colors,   // 42 字段，直接给全量，不再“从 dark 混出来”
        ThemeAssets assets               // null = 代码皮肤
) {}

/** 主题注册表：内置 + 用户 JSON（阶段 4 再加）。 */
public final class ThemeRegistry {
    public static ThemePalette current();      // 读配置，未知 id 回退 dark
    public static ThemePalette byId(String id);
    public static java.util.List<ThemePalette> all();
    static void register(ThemePalette p);      // 内置注册（阶段 1：dark/light 两条）
}
```

### 2.3 渲染唯一入口：`SkinRenderer`

所有背景类元素只经过一个方法（替代现在分散在 3 个文件、25 处的纹理调用）：

```java
public final class SkinRenderer {
    /** 画一个 UI 元素背景：有 assets → 纹理；assets == null → 代码绘制。 */
    public static void draw(DrawContext g, UiElement el,
                            int x, int y, int w, int h, float alpha);

    /** 图标：白色剪影 × 强调/tint 色；套用 palette.assets.iconTint()。 */
    public static void drawIcon(DrawContext g, String icon,
                                int x, int y, int size, float alpha, int tint);
}
```

代码绘制分支内部使用已有的 `RoundRectRenderer`（抗锯齿圆角遮罩）+ `g.fill` 组合，由一张 `SurfaceStyle` 表控制每个元素的观感：

```java
record SurfaceStyle(int radius, int borderColorAlpha, float gradientStrength, int insetShadowAlpha) {}
// 例：panelBg  radius=0  border=panelBg 亮 12%  gradient=0.06  shadow=0.10
//     inputBg  radius=5  border=divider     gradient=0     shadow=0
```

### 2.4 颜色解析链（保持兼容）

```
palette.colors()                    // 全量 42 字段（不再从 dark/light 混）
  → Accent 混合（约 10 个字段，沿用现有 mix 逻辑）
  → themeCustom 逐字段覆盖（42 键，最后生效）
= Appearance.snapshot()
```

`themeAccent`、`themeCustom`、`Colors` record 全部保留 —— 阶段 1~3 不动它们，只换“base”的来源。

---

## 3. 迁移步骤（每阶段可独立构建/验证/回滚）

### 阶段 0 · 基线（已完成）
- 回退主题改造，冻结当前 `dark/light` 观感；
- 保存两张基准截图（暗色、浅色全屏）作为后续像素对比基准。

### 阶段 1 · 引入注册表（**不做任何视觉改动**）
- 新增 `ThemeMode`、`ThemePalette`、`ThemeAssets`、`ThemeRegistry`；
- `ThemeRegistry` 先注册两条：`dark`（colors = `DARK.colors()`，assets = `("dark", 0xFFFFFF)`）、`light`（同理）；
- `Appearance.resolvedTheme()` → `ThemeRegistry.current().mode()` 映射回 `ChatBubbleTheme.DARK/LIGHT`（纹理路径不变）；
- `Appearance.snapshot()` → `ThemeRegistry.current().colors()` 走原 `resolve` 链；
- `theme` 配置值仍是 `"dark"/"light"`，配置屏 UI 不变。

**验收**：三支构建通过；暗/浅两套截图与基准**逐像素一致**；配置文件读写往返一致。

### 阶段 2 · 代码皮肤入口 + 5 大面（第一个真正的新主题）
- 新增 `SkinRenderer` + `SurfaceStyle`，把 `ThemeSkin` 时期的 25 处调用统一收口；
- 先只代码化 5 个最大面：`panel_bg / title_bar / bottom_bar / sidebar_bg / divider`；
- `dark/light` 的 `assets != null` → 仍走纹理（观感不变）；
- 新增 2 个 pilot 主题（建议 `midnight`、`sakura`），`assets = null`，`colors` 直接给全量 42 字段（不再混色）；
- 配置屏「主题」从 `Ref<ChatBubbleTheme>` 改为 `Ref<String>`（palette id），按 `ThemeRegistry.all()` 循环；
- lang：`e33chat.palette.dark|light|midnight|sakura`。

**验收**：dark/light 仍与基准一致；pilot 主题的面板/标题/底栏/侧边栏是代码绘制且颜色正确；`theme` 写入/读取的是 id。

### 阶段 3 · 其余 17 个背景元素代码化
- `popup_bg / context_menu_bg / toast_bg / whisper_bar / config_bg / content_bg / hover_bg / sidebar_selected / sidebar_hover / context_hover / close_bg / close_hover / input_bg / scrollbar_track / scrollbar_thumb / quick_*`；
- 图标改为 `drawIcon(...)` 白色剪影 + tint（不新增 PNG）；
- 达到状态：**任何 `assets = null` 的主题完全不依赖纹理**；`dark/light` 仍可保留纹理（也可后续切代码化，二选一）。

**验收**：pilot 主题在配置屏/弹层/滚动条/右键菜单全部一致；无 “missing texture” 日志。

### 阶段 4 · 自定义调色板 + 导入导出
- 配置屏加「自定义配色」页：12 个关键色（`panelBg/titleBg/barBg/sidebarBg/inputBg/textPrimary/textSecondary/textMuted/nameColor/quoteBar/duplicateLabel/panelBg` 等）+ 高级页展开 42 字段；
- 每行 hex 编辑 + 单行重置 + 重置全部 + 实时预览小面板 + 对比度提示（<4.5 警告）；
- 主题存 `config/e33chat/themes/<id>.json`（id/displayKey/mode/42 色），支持导入/导出/删除；
- `themeCustom` 保留为“当前主题的临时覆盖”（最后生效），或迁移进 palette 文件（二选一，建议保留，避免破坏已有配置）。

### 阶段 5（可选）· 资源包主题
- 约定 `assets/e33chat/themes/<id>/`（icons + textures），资源包可给某个 palette 提供自己的 `ThemeAssets`；
- 这样“独立设计”变成可分发资源包，而不是内置 39 张图 × N。

---

## 4. 文件级改动清单

| 阶段 | 新增 | 修改 |
|---|---|---|
| 1 | `render/ThemeMode.java`、`ThemePalette.java`、`ThemeAssets.java`、`ThemeRegistry.java` | `Appearance.java`（resolvedTheme/snapshot 改走注册表）、`ChatBubbleConfigScreen.java`（仅当阶段 2 需要） |
| 2 | `render/SkinRenderer.java`、`render/SurfaceStyle.java` | `ChatBubbleScreen.java`（25 处背景调用 → `SkinRenderer.draw`）、`render/PublicChatOverlay.java`、`ui/*`、`config/*`、`UiElement.java`（加 style 映射）、lang×2、`ChatBubbleConfigScreen.java`（theme 选择器） |
| 3 | — | 同上（剩余元素）、`texture/UiTextureManager.java`（仅当保留纹理时） |
| 4 | `config/ThemeStore.java`（JSON 读写）、`config/CustomPaletteScreen.java`（或并入配置屏） | `ChatBubbleConfigScreen.java`、`ThemeRegistry.java`、lang×2 |
| 5 | — | `ThemeRegistry.java`、`SkinRenderer.java`、资源包加载（可选 `ResourceLoader`） |

## 5. 风险与对策

| 风险 | 对策 |
|---|---|
| 阶段 1 改出观感差异 | 只换“数据来源”不改绘制；用像素对比（dark/light 截图）作硬门槛 |
| 代码绘制与原纹理质感差距 | `SurfaceStyle` 补一个极轻的渐变 + 1px 内描边；先在 pilot 主题上看效果再推广 |
| 26.2/26.3 绘制 API 不同 | `SkinRenderer` 每支一个实现（`DrawContext` vs `UiCanvas`），其余逻辑复用；**不要整文件互拷 ChatBubbleScreen** |
| 主题 id 兼容 | `theme` 旧值 `dark/light` 继续有效；未知 id 回退 `dark`；`ConfigManager.mergeWithDefaults` 不需改（字段类型不变） |
| `themeCustom` 与新 palette 冲突 | 固定顺序：palette → accent → custom，custom 永远最后生效 |
| 性能 | 代码绘制每帧多几次 `fill`/`RoundRectRenderer`（有纹理缓存），可接受；`snapshot()` 结果按「配置版本号」缓存，避免每帧重算 42 字段 |
| 用户资源包覆盖旧纹理路径 | 阶段 1~3 保留旧路径；阶段 5 再引入 `themes/<id>`，不破坏现有包 |

## 6. 验收清单（每阶段结束都跑）

```
[ ] 三支构建 BUILD SUCCESSFUL（main/mc26.3/mc26.2）
[ ] 产物 jar 内类清单核对（新增类确实进 jar）
[ ] dark/light 观感：截图与基线对比（阶段 1 必须 0 像素差）
[ ] 配置读写：theme 值往返、未知 id 回退、旧配置文件升级
[ ] 主题切换：配置屏选项 → 关闭重开仍生效
[ ] 无 missing texture / 渲染异常日志
[ ] 1.21.11 部署到「测试」实例（旧件 .bak-<说明>）
[ ] JAR\manifest.txt 更新（大小/SHA256/commit）
```

## 7. 工作量（按轮次估算，不含美术）

| 阶段 | 估时 | 说明 |
|---|---|---|
| 1 注册表 | 0.5 轮 | 纯重构，无观感变化 |
| 2 代码皮肤 + 5 面 + 2 pilot | 1 轮 | 能看到“真主题”的第一版 |
| 3 其余 17 元素 | 1 轮 | 弹层/滚动条/配置屏统一 |
| 4 自定义调色板 + 导入导出 | 1.5 轮 | 含 UI 预览与校验 |
| 5 资源包主题（可选） | 0.5 轮 | 可延后 |

---

## 8. 待你拍板

1. 新主题的 `assets` 默认用 **null（代码皮肤）** 还是允许“也可以指定纹理”？（建议允许，默认 null）
2. 阶段 2 的 pilot 主题选 `midnight` + `sakura` 还是别的？
3. 阶段 3 之后，`dark/light` 是否也切代码绘制（统一维护），还是永久保留纹理？
