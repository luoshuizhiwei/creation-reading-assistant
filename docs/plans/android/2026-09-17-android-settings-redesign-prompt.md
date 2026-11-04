# 重做移动端「设置」与「阅读设置」

## 0. 先说清楚：我要你做什么

这是「创作阅读助手」的**原生 Android 阅读端**（Kotlin + Jetpack Compose + Material 3），
**不是桌面端**。

- 项目根：`D:\develop\Code\Codex\creation-reading-assistant`
- 移动端代码：`android/app/src/main/java/com/creationreadingassistant/`

我要你**重新设计整个设置区**，范围覆盖：

1. **设置首页**（「我的」那一整页：`ui/screen/profile/ProfileHomeScreen.kt`）
2. **全部 17 个设置子页**（`ui/screen/profile/` 下的各个 `*SubPage.kt`，**每一页内部也一起重做**）
3. **阅读设置的三套入口**（看书时面板 / 我的→阅读设置 / 主题按钮面板）

**这一轮你不要改代码。** 我要你先出一份**完整**的设计方案（17 个子页全部覆盖），
我确认之后你再一次性实现。

**只动 `android/`**，不要碰 `src/`、`electron/`（桌面端）和 `archives/`（历史存档）。

---

## 1. 现状

### 1.1 设置首页（`ui/screen/profile/ProfileHomeScreen.kt`，443 行）

顶部一张同步状态卡（点进去是阅读档案），下面是 **4 个 `MenuGroup`**：

| 分组 | 入口 |
|---|---|
| （顶部卡片，不属于任何 MenuGroup） | 创作阅读助手 / 我的阅读档案 |
| 阅读与外观 | 阅读设置、应用外观、我的阅读、选区与查词、目标 |
| 数据与存储 | 存储管理、标签管理、分类管理、书单管理 |
| 同步与工具 | 局域网同步、WebDAV 设置、AI 助手 |
| 帮助与关于 | 日志与诊断、隐私安全、关于 |

`ProfileSubPage` 枚举（`ProfileUiState.kt` 17–23 行）共 **17 个子页**：
`SYNC, WEBDAV, APPEARANCE, READER, AI, GOAL, TAGS, CATEGORIES, SHELVES, READING,
NOTES, SELECTION, STORAGE, PRIVACY, ABOUT, DIAGNOSTICS`

子页文件行数（合计约 8000 行）：

| 文件 | 行数 |
|---|---:|
| `LibrarySubPage.kt` | 865 |
| `SyncSubPage.kt` | 859 |
| `DiagnosticsSubPage.kt` | 781 |
| `GoalSubPage.kt` | 752 |
| `StorageSubPage.kt` | 728 |
| `WebDavSubPage.kt` | 692 |
| `ReaderSubPage.kt` | 639 |
| `AboutSubPage.kt` | 589 |
| `AiSubPage.kt` | 551 |
| `ProfileRoute.kt` | 513 |
| `AppearanceSubPage.kt` | 506 |
| `QrPairingScreen.kt` | 491 |
| `SelectionSubPage.kt` | 461 |
| `ProfileHomeScreen.kt` | 443 |
| `AnnotationEntryCard.kt` | 426 |
| `ReadingArchiveSection.kt` | 275 |
| `AnnotationFilterSection.kt` | 266 |
| `ReadingNotesSubPage.kt` | 255 |
| `ProfileUiState.kt` | 219 |
| `PrivacySubPage.kt` | 213 |

### 1.2 阅读设置：三套入口（本次重点）

**入口 A：看书时的设置面板** `ui/screen/reader/sheets/ReaderSettingsSheet.kt`（324 行）

看书时点底部菜单的「设置」按钮唤起。结构 **Root + 5 个二级页**
（`AnimatedContent` 淡入淡出切换，支持返回键回 Root）：

- **Root**：「本书 / 全局」作用范围切换、本书覆盖明细 + 预设 + 恢复默认、
  快捷排版微岛（字号+粗体、行距胶囊、翻页效果胶囊、翻页速度滑块）、
  5 个二级页的导航入口、「查看书籍信息」
- `TYPOGRAPHY` → `ReaderTypographySettings.kt`（509 行）
- `PAGING` → `ReaderPagingSettings.kt`（156 行）
- `DISPLAY` → `ReaderDisplaySettings.kt`（114 行）
- `EYE_CARE` → `ReaderEyeCareSettings.kt`（180 行）
- `ADVANCED` → `ReaderAdvancedSettings.kt`（57 行）

共用组件在 `ReaderSettingsCommon.kt`（**994 行**）。

**入口 B：我的 → 阅读设置** `ui/screen/profile/ReaderSubPage.kt`（639 行）

不看书时的完整设置页。**没有作用范围概念**（只能改全局，但界面上没说明）。
分区方式与 A 不同，6 个 `SectionCard`：排版设置 / 阅读模式与纸张 / 翻页与手势 /
夜读沉浸与定时 / 引擎与排版兼容 / 重置偏好。

**入口 C：看书时的「主题」按钮面板** `ui/screen/reader/sheets/ReaderThemeSheet.kt`（404 行）

看书时底部菜单五个按钮之一（目录 / 听书 / 灵感 / **主题** / 设置，见
`ReaderChrome.kt` 480 行）。目前**只能换纸张颜色**——9 种纸色，每种带文艺副标题
（如「温润米黄 · 舒缓暖纸」「水墨幽深 · 墨青夜读」）。
同一个纸张选择在阅读设置的「排版」页里也能改（那里叫「固定阅读纸张」/
「跟随应用外观纸张」）。

### 1.3 数据层

`data/settings/SettingsStore.kt` 167 行起是 `data class ReaderSettings`，**40+ 字段**：
阅读模式、两个分页引擎开关、翻页效果与速度、点击区域与动作预设、屏幕方向、字号、
自定义字体路径、行距、段距、页边距、纸张背景、日夜纸张记忆、沉浸模式、
页眉页脚（4 位置 + 4 自定义文案）、中文排版、繁体显示、菜单自动隐藏秒数、屏幕常亮、
进度条、粗体、亮度、音量键（2 开关 + 反转）、自动翻页速度、护眼（提醒周期 / 滤镜开关 /
色温 / 强度 / 定时起止 / OLED 兼容 / 色卡预览同步）、阅读节奏提醒（开关 + 周期）、
TTS（音高 / 音量 / 音色 / 定时停止 / 引擎）。

**本书覆盖机制**：`data/settings/ReaderOverride.kt` 的 `ReaderOverrideKey` 枚举，
**只有 19 项可被本书覆盖**——字号、行距、段间距、页边距、纸张、粗体、中文排版、
繁体显示、自定义字体、阅读模式、翻页效果、沉浸模式、页眉页脚、进度条、
菜单自动隐藏秒数、四个页眉页脚位置。
其余（亮度、TTS、点击区域、翻页速度、护眼、兼容引擎等）**永远只属于全局**。

**落盘路由**：`data/settings/ReaderSettingsRouter.kt` 有一份纯函数路由，5 条不变量：

1. 不可覆盖项永远进全局，无论作用范围选了什么
2. 作用范围为 BOOK 时，**所有**可覆盖项都不进全局（否则本书覆盖值会污染其它书）
3. 作用范围为 GLOBAL 时，只有**本次真正改动**的可覆盖项进全局
4. 选「全局」时，本次改动的可覆盖项会**同时清除本书覆盖**（否则新全局值被覆盖遮住，
   表现为「改了没反应」）
5. 未被本次编辑触碰的本书覆盖**一律保留**

### 1.4 组件库：两套并存

- `ui/components/SettingsComponents.kt`（340 行）：`SectionCard` / `SettingSliderRow` /
  `SettingSwitchRow` / `SettingSegmentedRow` / `SectionDivider` 等，**Profile 子页用**
- `ui/components/CompactSettingComponents.kt`（567 行）：`MicroSettingsSection` /
  `MicroOptionPillRow` / `MicroTrackSliderRow` / `CompactSettingRow` 等微岛变体，
  **看书面板用**

仓库里有一份现成的复用审计报告：
`docs/research/2026-09-15-android-compose-component-reuse-audit.md`
（结论：40 个公共 Composable 中 7 个零调用、11 个调用 ≤2 次）。**动手前先读它**，
避免再制造一批没人用的组件。

### 1.5 我认为的问题

1. **阅读设置三套入口，同一件事三个地方做，能力不一致。** A 有「本书/全局」，
   B 完全没有且界面上不说清楚；A 和 B 分区方式也不同；C 和 A/B 里的纸张选择是同一件事。
2. **17 个子页、约 8000 行，缺少统一规范。** 每页的分组、控件选择、文案风格可能各自为政。
3. **组件两套并存**，风格一致性靠人维护，容易漂离。
4. **视觉可以更精致**：滑块、胶囊单选、纸张选择、字体选择都有提升空间。

---

## 2. 我已经拍板的事情

以下是**已决定的方向**，请在此基础上设计。如果你有强烈反对意见，可以提，但要说清楚理由。

### 2.1 范围：整个设置区，17 个子页内部一起重做

全部子页都要做，**没有低优先级**。其中我特别关心：
**阅读设置**（三套入口）、**应用外观**（`AppearanceSubPage.kt` 506 行）、
以及其余所有页面。

### 2.2 交付方式：一次性设计完，再一起实现

这一轮把 17 个子页的设计方案**全部出完**，我一次看完确认，然后你一次性实现。

### 2.3 「主题」按钮升级成「外观快捷面板」

看书时底部菜单的「主题」按钮，**保留这个入口**，但把它从「只能换纸张颜色」
升级成一个**外观快捷面板**，内容包含三项：

- **纸张颜色**（保留现有 9 种及文艺副标题）
- **字号**
- **亮度**（含「跟随系统」/「固定亮度」切换，现有 `brightness` 字段：
  `-1` 跟随系统，`0..100` 固定；`lastFixedBrightness` 记上次固定值）

**不要**在这个面板里放行距 / 段距 / 页边距——那些留给完整设置页。

### 2.4 「我的 → 阅读设置」＝完整页，改的是全局

- **全部 40+ 项都能改**
- **必须在界面上明确标注「这里改的是全局，影响所有书」**
- 不在这个页里做「挑一本书单独设置」的能力

### 2.5 「本书 / 全局」作用范围：保留并改进，小幅扩大可覆盖范围

- **保留**这个能力（看书时的设置面板里）
- **做得更显眼、更好懂**：现在藏得深、说法不够白话。改进呈现——比如顶部明显的
  两个按钮选项，并在被本书单独改过的项旁边标注「本书」
- **小幅扩大可覆盖范围**：现在 19 项可覆盖。你判断哪些项值得纳入，
  但**不要扩太多**——只加那些"不同书可能需要不同值"的项（比如亮度是否该按书记忆？
  你来论证）。扩太多会让路由规则和界面复杂度失控。

### 2.6 设置首页：分组菜单 + 顶部搜索框

保留分组菜单列表形式，**在顶部加一个搜索框**，
**要能搜到具体设置项**（输入「字号」直接跳到字号那一行并高亮）。

这意味着需要给几百个设置项建索引——请在方案里说明索引怎么组织、
是硬编码维护还是从 UI 结构自动派生、怎么保证不随改版失效。

### 2.7 组件库：统一成一套，用参数控制紧凑 / 普通

把 `SettingsComponents.kt` 和 `CompactSettingComponents.kt` 合并成一套，
用参数（如 `compact: Boolean` 或 density 枚举）控制密度。
请说明：统一后的组件签名、现有调用点怎么迁移、那 7 个零调用组件怎么处理。

### 2.8 视觉：在现有微岛风格上做精致化

**保留** Material 3 + 微岛语言（圆角卡片、`IconPedestal` 图标底座、
胶囊单选 `SelectablePill`、微导轨滑块），以及现有设计 token
（`ui/theme/`，`ui/layout/LayoutTokens.kt`）。**不要推翻重来**。

要升级的控件（逐个给出方案）：

- **滑块**：拖动时数值跟随、刻度感、更好的触感反馈
- **胶囊单选**：行距「紧凑/标准/宽松」这类三选一，能否用**真实排版预览**
  （三个胶囊各自用对应行距渲染一行示例文字）？请评估在 Compose 里的实现方式和性能
- **纸张 / 背景选择**：让色卡更直观（真实纸色 + 叠加护眼滤镜效果）。
  现有 `ReaderPaperPalette`（`ui/theme/ReaderPaperPalette.kt`）和
  `ReaderPaperOptions`、`paperPalette()`
- **字体选择**（`ReaderFontPickerRow`）：用字体本身渲染字体名预览

### 2.9 为未来能力预留位置

我还没想好要加什么，但希望新结构**预留扩展位**。可能方向（**不是要求你现在实现**）：

- 护眼增强（现在已有滤镜、色温、定时、OLED 兼容）
- 手势自定义（现在 `tapZoneMode` 三区/五区、`tapActionPreset` standard/left_hand/both_next，
  是否做成完全可自定义）
- 阅读统计与目标（已有 `GoalSubPage.kt` 752 行、`ui/screen/stats/`）

---

## 3. 可以参考的对象：legado-with-MD3

本地有完整源码：`D:\Application\Desktop\legado-with-MD3-main`

重点看这几处，**借鉴信息架构和交互思路**（不是照搬代码）：

| 文件 | 看什么 |
|---|---|
| `app/src/main/java/io/legado/app/ui/book/read/sheet/ReadStyleSheet.kt` | 阅读样式面板的 **Tab 分页**：底部 `CardTabRow` 四个 Tab，前两个（全局主题 / 菜单系统）是面板内 `HorizontalPager` 翻页，后两个（页眉页脚 / 更多设置）**直接打开二级页**。还有 `rememberPagerAnimatedHeight` 做高度动画 |
| `app/src/main/java/io/legado/app/ui/book/read/sheet/GlobalThemePage.kt` | 主题 / 背景 / 字体的组织方式 |
| `app/src/main/java/io/legado/app/ui/config/readConfig/ReadConfigScreen.kt`（431 行） | 完整阅读设置的分组：`SplicedColumnGroup` 分成「屏幕设置」「翻页控制」「其他」等语义组 |
| `app/src/main/java/io/legado/app/ui/book/read/sheet/HeaderFooterPage.kt`、`EyeProtectionConfigSheet.kt`、`BgTextConfigSheet.kt` | 二级配置页：主面板只放高频项，细节收进二级页 |

本项目自己的文档：
`docs/research/legado-with-md3-analysis.md`、
`docs/research/2026-09-15-android-legado-md3-improvement-proposal.md`、
`docs/research/2026-09-15-android-compose-component-reuse-audit.md`、
`docs/plans/legado-feature-backlog.md`。

---

## 4. 约束

### 4.1 硬约束

**不能让现有功能失效。** 现有每一个设置项都要还能改、还能存、还能生效。
你可以折叠低频项、改变呈现方式，但不能让设置项消失或失效。
你可以**补充**新功能，但不能删掉现有的。

### 4.2 允许彻底重构数据层，但有两条红线

我允许你按新设计**彻底重构 `ReaderSettings`**（增删字段、重新分组）。
但必须守住：

**红线一：老用户数据必须能迁移。**
现有用户在 DataStore 里已经存了设置。重构后不能让他们的设置丢失或被静默重置。
`data/settings/ReaderDefaultsMigration.kt` 已有迁移机制，请说明你怎么扩展它、
以及回退方案（如果用户从新版退回旧版会怎样）。

**红线二：`ReaderSettingsRouter` 的 5 条不变量不能破坏。**
那是防止本书覆盖和全局互相污染的落盘规则，改错会导致**本书覆盖和全局静默互相污染**
（表现为：改了 A 书的字号，B 书跟着变；或者改了全局却没反应）。
如果你要扩大可覆盖范围或重构字段，请先说清楚路由规则怎么变、不变量还成不成立。

### 4.3 其他

- 现有参数取值范围（字号 12–40sp、行距 1.5/1.85/2.1、页边距 10–42dp、
  翻页速度 150–800ms 等）如果要改，说明理由。
- 不限制引入新依赖，但说明理由，并确认与现有 Material 3 配置共存。

---

## 5. 我要你交出什么（本轮只交方案，不写代码）

### 5.1 现状复核

先把第 1 节提到的文件读一遍，确认我描述得对不对。
**如果我哪里说错了，直接指出来**，不要顺着我的错误描述往下做。
特别是：三套入口的重叠程度、两套组件库的使用边界、17 个子页当前的实际状态。

### 5.2 整体设计规范

先给出**通用规范**，后面各页遵循它：

- 分组方式（什么时候用 `SectionCard`、什么时候分组、标题怎么写）
- 控件选择规则（什么类型的设置用什么控件）
- 文案规范（标题、副标题的写法、语气）
- 统一后的组件库设计（签名、密度参数、迁移路径）

### 5.3 设置首页方案

- 新的分组方案：分几组、每组叫什么、放哪些入口、副标题怎么写
- 17 个子页是否需要合并 / 拆分 / 重新归类
- 顶部卡片和搜索框的设计
- **搜索索引方案**：怎么组织、硬编码还是自动派生、怎么保证不随改版失效

### 5.4 17 个子页，逐页给出方案

**每一页都要给**，不能只给重点页。每页至少说明：

- 现在的分组 / 项有什么问题
- 新的分组与项的顺序
- 哪些项该折叠进「高级」、哪些该提到显眼位置
- 需要新增或删除的项（删除要说明理由，并注意 4.1 硬约束）

重点页（**阅读设置、应用外观**）请给更多细节。

### 5.5 阅读设置三入口方案（重点）

- 三入口（看书面板 / 我的→阅读设置 / 主题按钮面板）**最终收口成什么结构**，
  画结构图或列表说明
- **外观快捷面板**（原「主题」按钮）的具体设计：纸张 + 字号 + 亮度怎么排布
- 看书面板 Root 页和 5 个二级页的切分是否合理，哪些项该上提 / 下沉
- 「本书 / 全局」作用范围怎么呈现才够显眼、够白话
- **可覆盖范围要不要扩、扩哪几项**（按 2.5 节要求：小幅、只加"不同书可能需要不同值"的项）
- 三入口共用组件的方式

### 5.6 控件设计

逐个说明：滑块、胶囊单选（重点评估「真实排版预览」的可行性与性能）、
纸张 / 背景选择、字体选择。

### 5.7 数据层重构方案（如果确实需要）

- `ReaderSettings` 要不要重构、怎么重构
- 迁移方案（含回退）
- 路由规则怎么变、5 条不变量还成不成立

### 5.8 风险与取舍

- 哪些改动风险高
- **现有测试**：`android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderSettingsSheetTest.kt`
  （225 行，6 个用例，依赖 `settings-root` 等 testTag）
  你的方案会不会打破它们？如果会，怎么迁移？
  另外 `androidTest` 下还有 `ReaderScreenTest.kt`、`ReaderAccessibilityLayoutTest.kt`、
  `reader/ReaderBottomActionsTest.kt`、`reader/ReaderSelectionToolbarTest.kt`、
  `reader/sheets/` 下的 `ReaderRulesSheetTest.kt` / `ReaderSearchSheetTest.kt` /
  `ReaderTocSheetTest.kt`，以及 `ui/screen/profile/LibrarySortControlsTest.kt`，一并评估。
  注意：Profile 的 17 个子页目前**几乎没有 Compose 测试覆盖**，
  如果你打算为重构后的设置页补测试，请说明补到什么程度。
- 建议分几步实现、第一步做哪块
- 有哪些你拿不准、需要我拍板的点，单独列出来问我

---

## 6. 项目约定（避免你踩坑）

- **只动 `android/`。** `src/`、`electron/` 是桌面端，`archives/` 是历史存档，都不要碰。
- 构建与检查（在 `android/` 目录下）：
  - `./gradlew :app:testDebugUnitTest`（JVM 单元测试）
  - `./gradlew :app:lintDebug`
  - `./gradlew :app:assembleDebug`
- **真实设备验证**：`android/` 的 UI 只在已连接的真机上验证，**不用 MuMu 模拟器**。
  先 `adb devices` 核对设备，再用 `adb -s <serial>`。
  测试时可临时启用 `stay_on_while_plugged_in`，**结束时必须恢复原值**。
- 项目路径已改为 ASCII（`creation-reading-assistant`），中文路径会导致 Gradle 测试编码失败，
  **不要改回中文路径**。
- `gradle.properties` 的 `kotlin.daemon.jvmargs` 必须带 `-Dfile.encoding=UTF-8`，
  **不要删**（否则中文 Windows 上按 GBK 读源码，中文字面量变乱码）。
- 全部 UI 文案用**中文**。
- 尊重 `rememberReducedMotion`（减少动效）和 `rememberHaptic`（触感反馈）的现有用法。
- 测试报告、TODO、截图说明、交接记录只写「测试 EPUB」「测试 TXT」等中性名称，
  不记录真实测试书名。
- 工作区里的未提交改动是用户资产，不要为了清空状态执行 reset / checkout / 全仓格式化。
- 未经我要求，不要 commit、不要 push。

---

## 7. 最后

**不要写代码，不要改文件。** 只出方案，等我确认。

方案要**完整**——17 个子页每一个都要覆盖到，不要只写重点页然后把其余标为「同理」。

如果有什么地方你觉得我的想法不对、或者有更好的做法，直接说。
我要的是能用的设计，不是对我每句话的附和。
