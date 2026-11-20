# E33Chat 主题架构 · 修改后的实际结构（阶段 1 已落地）

> 对应提交：main `cea9b53` / mc26.3 `71fd948` / mc26.2 `4d96952`
> 本阶段**不改变任何观感**，只把「明暗 / 配色 / 纹理」三个概念拆开并对接好。
> 设计稿见 [THEME_ARCH_DESIGN.md](THEME_ARCH_DESIGN.md)。

---

## 一、文件结构变化

```
render/
  Appearance.java        ← 改：resolvedTheme()/snapshot() 改走注册表
  ChatBubbleTheme.java   ← 改：新增 resolveFrom(Colors base, Accent, custom)；旧 resolve 委托
  ThemeMode.java         ← 新：LIGHT | DARK（只决定对比方向/图标剪影）
  ThemeAssets.java       ← 新：record(dir, iconTint)，null 表示代码皮肤
  ThemePalette.java      ← 新：record(id, mode, displayKey, sortOrder, colors, assets)
  ThemeRegistry.java     ← 新：id → palette；current()/byId()/all()/register()
  （以下未动）
  UiLayout / ChatScrollbar / RoundRectRenderer / ChatBubbleHudOverlay / PublicChatOverlay …
```

配置类（`ChatBubbleConfig`）**未改动**：`theme`、`themeAccent`、`themeCustom` 三个字段原样保留，
旧配置（`"dark"` / `"light"`）继续有效。

---

## 二、修改后的调用链

```
配置 theme = "dark" | "light" | (未来: 任意 palette id)
        │
        ▼
ThemeRegistry.current()
        │  byId(theme) → 命中则用；未知 id → 按 ThemeMode 回退 dark/light
        ▼
ThemePalette { id, mode, displayKey, sortOrder, Colors, ThemeAssets }
        │
        ├─ mode ──────────────► Appearance.resolvedTheme() → ChatBubbleTheme.DARK/LIGHT
        │                          └─ UiTextureManager.rl(el) / iconTex(name)（纹理路径不变）
        │
        └─ colors ────────────► Appearance.snapshot()
                                   ChatBubbleTheme.resolveFrom(colors, accent, custom)
                                     = palette 42 色
                                       → 强调色混合（约 10 个字段）
                                       → themeCustom 逐字段覆盖（最后生效）
                                   = ChatBubbleTheme.Colors（42 字段）
```

与改造前的差别只有一处：**基色来源从“写死的 DARK/LIGHT.colors()”变成“注册表里的 palette.colors()”**。
阶段 1 注册表里只有两条 palette（dark/light，颜色就是原来的 42 字段），因此输出完全一致。

---

## 三、四个新类的职责

| 类 | 职责 | 关键方法 |
|---|---|---|
| `ThemeMode` | 明暗语义（只影响文字对比、图标剪影基色） | `parse(String)`、`key()` |
| `ThemeAssets` | 可选纹理集：目录名 + 图标 tint | `of(dir)`、`dir()`、`iconTint()` |
| `ThemePalette` | 一套主题：42 色 + mode + 元数据 + 可选 assets | `usesTextures()` |
| `ThemeRegistry` | 主题表：注册/查找/列举/当前 | `register(p)`、`byId(id)`、`current()`、`all()` |

`ChatBubbleTheme` 保留：`Colors` record（42 字段）、`ColorMode`（给旧调用/纹理路径用）、`Accent`、`resolve`（旧签名委托到 `resolveFrom`）。

---

## 四、现在加一套主题有多简单（阶段 2 的形态）

```java
// 在 ThemeRegistry 的静态块里（或后续的 JSON 加载里）追加一条即可
register(new ThemePalette(
    "midnight", ThemeMode.DARK, "e33chat.palette.midnight", 2,
    new ChatBubbleTheme.Colors(/* 42 个颜色，直接给全量 */),
    null                       // ← null = 不依赖纹理，将来由 SkinRenderer 代码绘制
));
```

阶段 1 之后这段代码**已经能编译、能解析、能进注册表**；但面板仍按 dark/light 纹理绘制，
所以要在阶段 2 把 `SkinRenderer` 接上，新主题才会真的出现在界面上。

---

## 五、配置与兼容

| 项 | 修改后 |
|---|---|
| `theme` 字段 | 仍是字符串；值域从 `dark|light` 扩展为任意 palette id |
| 旧配置 `"dark"/"light"` | 命中注册表，行为完全不变 |
| 未知 id / 大小写变体 | `ThemeRegistry.byId` 按 `ThemeMode.parse` 回退到 dark 或 light |
| `themeAccent` | 不变，palette 之后混合 |
| `themeCustom` | 不变，最后逐字段覆盖（42 键） |
| 配置屏「主题」 | **阶段 1 暂未改**，仍循环 DARK/LIGHT；阶段 2 改成按 `ThemeRegistry.all()` 循环 |

---

## 六、阶段 1 的验收结果

```
[x] main/mc26.3/mc26.2 三支 BUILD SUCCESSFUL
[x] 产物 jar：698,351 / 702,451 / 702,416 B（新增 4 个类）
[x] 1.21.11 已部署「测试」实例（旧件 .bak-p1registry）
[x] JAR\manifest.txt 已更新（大小/SHA256/commit）
[x] 观感：无任何绘制代码改动 → dark/light 与回退版一致
```

## 七、下一步（阶段 2）

1. 新增 `SkinRenderer`（唯一背景/图标入口），先代码化 5 大面：
   `panel_bg / title_bar / bottom_bar / sidebar_bg / divider`；
2. 注册 `midnight` + `sakura` 两条 palette（`assets = null`），配置屏主题选择器改成按注册表循环；
3. dark/light 继续用纹理（`assets != null`），所以老观感不会变；
4. 验收：暗/浅截图与基线一致 + pilot 主题真变色 + 配置文件往返。
