# E33Chat UI 架构与「要改颜色的部分」清单

> 状态：主题改造已全部回退（见 commit `revert(theme)`）。本文是**改造前的真实结构**，用于先理清再动手。
> 分支：main(1.21.11) / mc26.3 / mc26.2 —— 三个分支结构一致，仅 API 名称不同。

---

## 一、渲染分层（谁盖谁）

| 层 | 类 | 说明 |
|---|---|---|
| 游戏 HUD 层 | `render/ChatBubbleHudOverlay.java` | 左下角聊天图标、`[T]` 提示；HUD 先画，任何 Screen 都盖在它上面 |
| HUD 层 | `render/PublicChatOverlay.java` | 公屏消息小窗（A/B/C/D 四样式） |
| Screen 层 | `ChatBubbleScreen.java`（5345 行） | **主聊天面板**：侧边栏 + 消息区 + 标题栏 + 输入栏 + 所有子面板 |
| Screen 层 | `ui/BedScreen.java` | 睡觉时的简化界面（只有「起床」按钮） |
| 屏内浮层 | `chat/notification/MentionNotificationBanner.java`<br>`chat/notification/MusicIslandBanner.java` | @提醒横幅 / 音乐灵动岛（要浮在聊天面板之上） |
| 独立 Screen | `config/ChatBubbleConfigScreen.java`（1252 行）<br>`config/ServerConfigScreen.java`（732 行）<br>`config/ChannelDietScreen.java`（457 行） | 设置界面；用**原版控件**（ButtonWidget×12、TextFieldWidget×5）+ 代码绘制 |
| 面板内子面板 | `ui/ChatEmojiPanel` / `ChatQuickChatPanel` / `ChatSearchPanel` / `ChatSettingsMenu` / `PlayerActionMenu` / `PlayerActionCustomPopup` | 不是独立 Screen，由 `ChatBubbleScreen` 在面板内绘制并自行处理输入 |

## 二、ChatBubbleScreen 的绘制顺序（`render()`，L2609 起）

```
renderSidebar()        L735   侧边栏（含玩家/群聊行、头像、红点、月亮）
PANEL_BG 纹理          L2647  主面板底
renderTitleBar()       L2729  标题栏（菜单、月亮、标题、时间、关闭）
renderMessages()       L3192  消息区
  ├ renderBubble()             L3547  消息气泡（圆角、引用条、合并 xN、头像）
  ├ renderTimeSeparator()      L3429  时间分隔线
  ├ renderNotificationBar()    L4215  “有新消息”提示条
  └ renderScrollbar()          L3395  消息区滚动条
renderBottomBar()            输入框 + 齿轮/主题/表情/发送按钮
renderContextMenu()    L4240  右键菜单
renderActionMenu()     L4284  动作菜单
renderActionCustom()   L4293  自定义动作弹窗
renderPopupWithAnim()  L1467  子面板（表情/常用语/搜索/设置菜单）
```

## 三、元素 → 绘制来源 → 取色来源

| 元素 | 绘制来源 | 取色来源 |
|---|---|---|
| 主面板底 | 纹理 `panel_bg` | 纹理自带（`Colors.panelBg` 只在部分 alpha 计算里用） |
| 标题栏 | 纹理 `title_bar` | 纹理自带（`barBg` 同） |
| 底栏 / 输入栏 | 纹理 `panel_bg`/`bottom_bar` | 纹理自带 |
| 侧边栏底 | 纹理 `sidebar_bg` | 纹理自带 |
| 侧边栏行选中/悬停 | 纹理 `sidebar_selected`/`sidebar_hover` | 纹理自带（`Colors.sidebarItemSelected/Hover` 仅用于个别代码态） |
| 分割线 | 纹理 `divider` | 纹理自带 |
| 输入框本体 | **代码**：`RoundRectRenderer.fill` 圆角 + 描边 | `Colors.inputBg` |
| 消息气泡（自己/别人） | **代码**：`RoundRectRenderer.fill` 圆角 | `Colors.ownBubbleColor/otherBubbleColor`（配置项 hex） |
| 气泡内文字 | 代码 `drawText` | `Colors.ownTextColor/otherTextColor` |
| 发送者名 / 时间 | 代码 `drawText` | `Colors.nameColor` / `Colors.timeColor` |
| 系统消息 / 引用条 / 合并 xN | 代码 | `Colors.systemText` / `quoteBar` / `duplicateLabel` |
| 右键菜单 / 动作菜单 / 弹窗背景 | **代码**：圆角填充 | `Colors.contextBg` / `contextHover` / `contextText` |
| 弹层（表情/常用语/搜索/设置菜单） | 纹理 `popup_bg` + **代码**圆角行高亮 | 纹理 + `Colors.popupBg/popupHover/popupText` |
| 滚动条 | 纹理 `scrollbar_track`/`scrollbar_thumb` | 纹理自带（`Colors.scrollbar/Hover` 只用于部分代码态） |
| Toast | 纹理 `toast_bg` + 代码文字 | 纹理 + `Colors.toastBg/toastText` |
| 引用条 / 弱提示条 | 纹理 `whisper_bar` | 纹理 + `Colors.whisperBar` |
| 聊天图标、菜单/搜索/表情/发送/主题/月亮等图标 | **图标 PNG**（每套主题 11+ 个） | 纹理自带（白色剪影/线条，**可整体 tint**） |
| 头像 | 玩家皮肤（`SkinResolver`）+ 代码圆角遮罩 | 皮肤贴图 |
| 表情 | 原版字体 emoji / 贴图（`EmoteStore`） | 资源 |
| 图片消息 | 下载图片 + 代码缩放 | 图片自身 |
| 配置屏 / 服务器配置 / 通道节食屏 | 原版 `ButtonWidget`/`TextFieldWidget` + 代码填充 + `config_bg` 纹理 | 原版 + `Colors.config*` |
| @提醒横幅 / 音乐灵动岛 | **纯代码**（圆角 + 渐变条 + 文字） | 部分 `Colors`，其余**硬编码** |
| 公屏小窗 A/B/C/D | **纯代码** | 主题 `Colors`（`popupBg`/`textPrimary`/`nameColor`） |

## 四、颜色来源分三类（改造面的关键）

### A. `Colors` 调色板字段（42 个，受主题影响）
定义在 `render/ChatBubbleTheme.java` 的 `Colors` record；当前被引用次数（全项目）：

```
textMuted 27   textPrimary 13   textSecondary 11   configTitle 11   configLabel 10
inputBg 7      popupHover 6     nameColor 4        scrollbar 3      panelBg 3
notificationText 3   contextHover 3   toastText 2   timeColor 2   sidebarItemSelected 2
iconHover 2    duplicateLabel 2  configSection 2  closeText 2   toastBg 1
sidebarItemHover 1  popupBg 1  divider 1  closeHoverBg 1  barBg 1  …
```

分组（42 字段全名）：
- 背景：`panelBg, titleBg, barBg, sidebarBg, sidebarItemHover, sidebarItemSelected, sidebarDivider, divider, inputBg`
- 文字：`textPrimary, textSecondary, textMuted, nameColor, timeColor, popupText, contextText, notificationText, toastText, closeText, systemText, configTitle, configSection, configLabel`
- 弹层/菜单：`popupBg, popupHover, contextBg, contextHover, iconHover`
- 组件：`scrollbar, scrollbarHover, closeBg, closeHoverBg, redDot, redDotMention, quoteBar, duplicateLabel, whisperBar, toastBg, configBg, bannerBg, bannerText, bannerBar`

### B. 纹理自带颜色（不受 `Colors` 影响，改色必须换纹理或改绘制方式）
每个主题 39 张 PNG（`assets/e33chat/textures/gui/{dark,light}/`）：
- 背景/组件 22 个：`panel_bg, title_bar, bottom_bar, sidebar_bg, divider, input_bg, scrollbar_track, scrollbar_thumb, context_menu_bg, popup_bg, toast_bg, whisper_bar, config_bg, content_bg, quick_scrollbar_track, quick_scrollbar_thumb, hover_bg, sidebar_selected, sidebar_hover, context_hover, close_bg, close_hover`
- 图标 17 个：`menu, search, quick_chat, settings, theme, emoji, send, dnd, quote, copy, public_icon, private_tip, no_online, chat_icon, tp, block, whisper`

### C. 硬编码颜色字面量（完全不走主题，共 29 处）
| 文件 | 处数 | 典型内容 |
|---|---|---|
| `ChatBubbleScreen.java` | 15 | 悬停高亮、红点、引用条、动作按钮强调色等 |
| `MentionNotificationBanner.java` | 5 | @提醒横幅底色/描边/绿色强调 |
| `MusicIslandBanner.java` | 5 | 音乐岛底色/进度条/绿色 |
| `ChatQuickChatPanel.java` | 2 | 输入框/分隔线颜色 |
| `ChatSearchPanel.java` | 1 | 提示文字颜色 |
| `ChatBubbleHudOverlay.java` | 1 | 图标提示文字颜色 |

## 五、三条改造路径与影响面

| 路径 | 工作量 | 效果 | 影响文件 |
|---|---|---|---|
| ① 只改 `Colors` 引用 | 小 | 只有文字/气泡/输入框/部分菜单变色；面板仍是原纹理 | `ChatBubbleTheme`（改值）+ 少量引用点 |
| ② 把 22 个背景纹理改成代码绘制 | 中（约 1 轮） | 任何配色即时生效，可加圆角/描边/渐变 | `ChatBubbleScreen`、`ui/*`、`config/*` 的背景绘制点 |
| ③ 每套主题各画 39 张纹理 | 大（美术） | 观感最好、完全可控 | 仅资源文件 |

**路径 ② 的落点（按优先级）**
1. 主面板底 / 标题栏 / 底栏 / 侧边栏 / 分割线 —— 5 个元素，覆盖 >90% 的视觉面积
2. 弹层底 / 右键菜单底 / 滚动条 —— 6 个元素
3. 图标：全部为白色剪影，**可直接用颜色 tint 上色**，不需要重画
4. 29 处硬编码颜色：逐个替换成 `Colors` 字段（可能需新增 2~4 个字段，如 `hoverHighlight`、`musicAccent`）
5. 文字/气泡/输入框：已经走 `Colors`，无需改

## 六、待确认的问题

1. 改造范围：只做面板类（路径 ② 第 1 组，改动小、观感提升大），还是把弹层/滚动条也换（第 2 组）？
2. 是否允许把 29 处硬编码颜色收编进 `Colors`（需要新增 2~4 个字段，`Colors` 构造点 12 处要同步）？
3. 面板代码化后要不要保留原纹理的「渐变 + 1px 描边 + 内阴影」质感（代码实现约多 50 行）？
