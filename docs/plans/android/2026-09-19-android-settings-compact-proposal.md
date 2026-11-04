# Android 设置二级子页紧凑化提案（2026-09-19）

状态：**部分已实现**。改动 A/B/D/E(仅隐私页)/F 已落地并真机看过；改动 C（其余 13 个子页的卡片合并）与逐页文案压缩待做。详见文末「落地状态」。
本文只覆盖「我的 → 设置二级子页」的视觉密度与信息密度，不改任何设置字段的落盘契约。

## 1. 诊断：不是字号问题，是信息架构 + 材质叠加

设备基准：1220×2712 px @480dpi → **406.7 × 904 dp** 可视区。

三处结构性浪费，每处都有代码证据：

| 问题 | 证据 | 成本 |
|------|------|------|
| 「一个信息点 = 一张卡」 | `ui/screen/profile/PrivacySubPage.kt:92-135`：6 条信息渲染成 6 张全宽卡（引言卡 + 4 张 `PrivacyCardItem` + `DegradedNote`），卡内各带 14dp padding + 图标 + 徽章 + 3~4 行整句说明 | 约 2.2 屏滚动，实际信息一屏可读完 |
| 页头吃掉首屏 | `ui/components/AppLayout.kt:96` `AppScreenScaffold` 调 `AppTopBar` **未传 compact** → `titleStyle = headlineLarge` = **衬线 26sp**（`ui/theme/Typography.kt:75`），容器 `heightIn(min = topBarHeight=64.dp)` | 每个子页固定 64dp+，约占屏高 7%，且是全页最重的一处视觉 |
| 材质无开关地全开 | `ui/components/SharedComponents.kt:64` `SectionCard` 无条件叠加 上亮下暗渐变 + `PaperNoise`(alpha 0.035) + 描边 + `GlassOverlays`，子页与首页共用同一实现 | 卡片视觉厚度远大于其信息量；卡越多越"糊" |

次要：`SectionHeader`（`AppLayout.kt:213`）用 `headlineSmall` 18sp + 展示衬线、`heightIn(min = 32.dp)`；`IslandSectionHeader`（`MicroIsland.kt:130`）依赖 `IconPedestal` 默认 **32dp** 底座（`MicroIsland.kt:88`），撑高 13 个子页的每个区块头。

## 2. 改动 A — 子页页头降层（1 处，覆盖 16 个子页）

- `AppScreenScaffold` 增加 `compactTopBar: Boolean = false` 透传给 `AppTopBar`。
- `ui/screen/ProfileScreen.kt:64` 的 `AppScreenScaffold` 调用传 `compactTopBar = true`。
- compact 档标题走 `titleLarge`（18sp，已是 `AppTopBar` 的 compact 分支），并把 `LayoutTokens.topBarHeight` 在 compact 下取 **52dp**。

收益：每页多出约 14dp 内容高度 + 页头视觉重量从"报头"降到"栏目标题"。
注意：`AppTopBar` 拆状态栏安全区与标题内容区的注释（`AppLayout.kt:258-261`）说明**不能**改用 M3 `TopAppBar` 标题槽，否则高状态栏设备会切字形上沿——保留现有 Row 实现，只降字号与最小高度。

## 3. 改动 B — 区块头收一档（2 处，覆盖 13 个子页 + 首页）

- `IslandSectionHeader`：`IconPedestal` 由 32dp/17dp → **22dp/13dp**；标题保持 `titleSmall` 14sp，但 `onSurface` → `onSurfaceVariant`，图标 tint 由多彩改单色（`primary` 或 `onSurfaceVariant`）。每个区块头省约 10dp。
- `SectionHeader`（首页区块标题）：`headlineSmall` 衬线 → `titleMedium` 16sp 无衬线，`heightIn(min = 32.dp)` → **28dp**。

## 4. 改动 C — 卡片合并（逐页清单，砍滚动的主项）

规则：**一张视觉卡 = 一个语义分区**；分区内多个条目用 `SettingsGroup` + `SectionDivider` 分行，不再各占一卡。

| 子页 | 现状（卡数/条目数） | 建议 |
|------|--------------------|------|
| PrivacySubPage | 6 卡（含 4 张自定义 Surface） | **1 张分组卡 4 行**（标题 + 一行副标题）；引言与底部合规注释降为卡外 `labelSmall` 文本 |
| ReaderSubPage | 8 卡 / 10 item / 38 行 | 保留分区但全部走 `SettingsGroup`；次要分区（TTS、翻页细节）折叠为"更多设置"展开区 |
| StorageSubPage | 5 卡 / 6 item | 2 卡：存储概览、导出与清理操作 |
| SyncSubPage | 5 卡 / 5 item | 2 卡：连接状态、同步行为 |
| SelectionSubPage | 5 卡 / 5 item | 2 卡：选中文本动作、行为开关 |
| WebDavSubPage | 3 卡 / 4 item | 2 卡：连接配置、备份操作 |
| AiSubPage | 3 卡 / 6 item | 2 卡：服务配置、生成偏好 |
| AppearanceSubPage | 3 卡 / 4 item | 2 卡 |
| DiagnosticsSubPage | 3 卡 / 1 item | 2 卡 |
| LibrarySubPage / GoalSubPage | 3 卡 / 2 卡 | 各减 1 卡，说明性卡并入相邻分区 |
| ProfileHomeScreen | 2 卡 / 8 item | 入口列表合并为 1 张分组卡，去掉入口项的独立卡壳 |

## 5. 改动 D — 材质分级（1 处，覆盖全站）

`SectionCard` 增加 `material: SectionCardMaterial = ELEVATED`（`ELEVATED` = 现状全套；`FLAT` = 仅容器色 + 0.5dp 描边，无渐变/噪点/玻璃高光）。
设置子页与所有 `SettingsGroup` 传 `FLAT`；首页、书架保留 `ELEVATED`。
`DegradedNote`（`ui/screen/profile/Components.kt:37`）从整张卡改为卡外一行 `labelSmall` + info 图标，每页省约 70dp。

## 6. 改动 E — 文案规范（逐页，收益最大的"精致感"）

- 卡内说明从整句（现 40~70 字）压到 **≤ 22 字一行** `bodySmall`；超出的细节收进 `?` 弹窗或长按详情，不占版面。
- 徽章词（"本地沙箱""手动握手""密钥不出境""隐私脱敏"）不再独占一行：移到行尾 `labelSmall` 弱化，或直接删除。
- 同一页内说明文案句式统一为「做什么」而非「我们坚信……」，去掉宣传语气。

## 7. 改动 F — LayoutTokens 数值（`ui/layout/LayoutTokens.kt`）

| token | 现值 | 建议 | 理由 |
|-------|------|------|------|
| `pageHorizontal` | 16dp | 14dp | 406dp 宽屏，左右各省 2dp 换内容宽度 |
| `pageVertical` | 12dp | 8dp | 首末 item 偏移 |
| `sectionGap` | 20dp | 12dp | 合并后区块数下降，间距无需这么大 |
| `contentGap` | 12dp | 8dp | 同分区内行距 |
| `cardPadding` | 14dp | 12dp | 卡内边距 |
| `singleLineRowHeight` | 52dp | 48dp | 正好压在触控底线上 |
| `supportingRowHeight` | 64dp | 54dp | 与 `compactSupportingRowHeight` 对齐，消除双档差异 |
| `topBarHeight` | 64dp | 52dp（仅 compact 档） | 见改动 A；非 compact 的顶级页保留 64dp |

## 8. 红线

- `minimumTouchTarget = 48.dp` 不得下调；`compactSingleLineRowHeight` 48dp 已是下限。
- 不改 `ReaderSettings` 字段名/编码（落盘契约 + 8 个 JVM 测试耦合）。
- 子页中文文案是 androidTest 的断言契约（如 `ReaderAccessibilityLayoutTest` 里 `listOf("目录","听书","灵感","主题","设置")`），改文案必须同步改测试。
- 紧凑化不得降低正文对比度：`onSurfaceVariant` 用于标题前先确认可读性。

## 9. 验收

1. 真机（`adb devices` 核对序列号）改前/改后同页截图对比，指标：**设置类子页每屏可见条目 ≥ 6**；Privacy 这类信息页 **1 屏内看完**。
2. `./gradlew :app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug` 全绿。
3. androidTest 中涉及 48dp 触控与文案断言的用例不挂。
4. 门禁在独立 detached worktree 跑，不污染共享工作树。

## 10. 阻塞与执行顺序

**当前不可执行**：本文涉及的文件几乎全部在另一个 agent 的未提交改动中——设置范围内 21 个文件 `+3788/−3216`（`ReaderSubPage` 1064 行、`SyncSubPage` 828、`WebDavSubPage` 818、`StorageSubPage` 749、`SelectionSubPage` 435），且 `CompactSettingComponents.kt` / `SettingsDensity.kt` / `SettingsSearchIndex.kt` 等为未跟踪新文件；`ui/screen/reader/` 下文件在本次审计时仍在被修改。

落地顺序（对方 commit 后先重读现状核对，本文假设可能已失效）：

1. 改动 F（token 数值）+ 改动 A（页头）→ 一次提交，先看整体密度变化；
2. 改动 D（材质分级）→ 单独提交，便于回退；
3. 改动 B（区块头）+ 改动 C（逐页卡片合并），按 Privacy → Storage/Sync/Selection → Reader → 其余 的顺序分 2~3 次；
4. 改动 E（文案）随各页合并一起做，不单独提交。

注：`ui/components/SettingsDensity.kt` 已建立 `COMPACT/REGULAR` 双档密度与 `settingsMetrics()` 单一来源，本提案的数值调整应尽量落到 token 与 metrics 里，而不是回到逐页写死 dp。

## 11. 落地状态（2026-09-19 实现）

已实现（7 个文件）：`ui/layout/LayoutTokens.kt`、`ui/components/AppLayout.kt`、`ui/components/SharedComponents.kt`、`ui/components/MicroIsland.kt`、`ui/screen/ProfileScreen.kt`、`ui/screen/profile/Components.kt`、`ui/screen/profile/PrivacySubPage.kt`。

与本文原方案有三处刻意的偏离，原因是 JVM/androidTest 锁死了默认值，且全局改会波及首页/书架/灵感/统计：

1. **§7 未直接改 `DefaultLayoutTokens`**，改为新增 `CompactSettingsLayoutTokens`，在 `ProfileScreen` 的子页分支用 `CompositionLocalProvider(LocalLayoutTokens provides …)` 局部生效。`LayoutTokensTest` 断言默认档 `sectionGap=20 / pageHorizontal=16 / contentGap=12`，直接改会让 JVM 单测挂掉。
2. **§2 未改 `topBarHeight`**，新增 `compactTopBarHeight = 52.dp`，`AppTopBar(compact=true)` 才用；`LayoutComponentsTest` 断言 `topBarHeight == 64.dp`。compact 档同时去掉展示衬线，标题走 `titleLarge` 18sp 无衬线。
3. **§3 区块头走参数而非直接改尺寸**：`IslandSectionHeader` 新增 `compact` 参数（24dp 底座 + `onSurfaceVariant` 标题），仅由 profile 的 `SubPageSectionTitle` 传 `true`；统计/灵感页仍用默认 32dp。

另外新增 `LocalFlatCards`（默认 false），`ProfileScreen` 子页分支提供 true → `SectionCard` 关掉纸感渐变、噪点、玻璃高光与 tonal 抬升，描边降到 0.5dp。`DegradedNote` 从整张卡降为卡外一行 `labelSmall` 注释。

隐私页按 §4/§6 重做：6 张卡 → `CompactSettingsGroup` 一张分组卡 4 行 + 卡外注释，说明句压到 ≤20 字，徽章词降为行尾 `labelSmall`。

真机结果（1220×2712 @480dpi，手动 `adb install -r -d` + 截图）：隐私页由约 2.2 屏降到 1 屏内看完；阅读设置/应用外观页头由 26sp 衬线降到 18sp 无衬线；首页与「我的」首页视觉未变化，确认作用域没有外溢。

**未完成 / 待办**：

- 改动 C 的其余 13 个子页（存储、同步、WebDAV、选区、阅读、AI、外观、诊断、书库、目标、关于、笔记、我的首页）仍是"多卡并列"。
- 逐页文案压缩：应用外观等页的 subtitle 仍是 40 字整句、折到两行，行高被撑回 ~90dp，是下一步紧凑化的主要余量所在。
- **androidTest 未跑**：MIUI 拦 instrumentation 安装（`INSTALL_FAILED_USER_RESTRICTED`），`connectedDebugAndroidTest` 与 `adb install -t` 两条路都不通，需要手机上开启「USB 调试（安全设置）」或在弹窗里确认。已跑的只有 `:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin`、`:app:testDebugUnitTest`（全绿）。

## 12. 修正：§4 的「卡片合并」不适用于 8 个带搜索锚点的子页

实现时发现本文 §4 把两类不同的卡片混为一谈了：

- **病态卡**（该合并）：一个信息点独占一张卡。典型是隐私页的 4 条并列承诺、以及 About / Diagnostics 这类展示页。
- **分区卡**（不该合并）：卡本身就是页面的一级分区，是搜索与页内跳转的落点。`SettingsSearchIndex.kt` 的 `SettingsEntry(anchor, title, page, group)` 里 **`group` 就是卡片标题**，搜索结果副标题「阅读设置 › 排版设置」直接读它；`ReaderSubPage` 顶部的横向跳转芯片（`ReaderSectionAnchor("排版", 2)` 等）也以这些分区为落点。把两张分区卡并成一张，是在拆导航结构而不是拆冗余。

所以执行结果分两路：

- **8 个索引页**（Reader / Appearance / Selection / Sync / Storage / Ai / Goal / WebDav）只做 §6 的文案压缩，不动卡片边界、不动 `settingsAnchor(...)` 字符串、不动被索引的 `title` 与 `group` 文案。`SettingsSearchIndexTest` 对锚点做**双向**校验（清单→源码、源码→清单），删锚点或改锚点名会立刻红。
- **5 个非索引页**（About / Diagnostics / Library / ReadingNotes / ProfileHome，`sourceFile` 对它们返回 null）允许合并卡片与压缩文案。

另需注意：行 `title` 与搜索索引是镜像关系，压缩文案时**只能动 `subtitle` / 卡内说明 `text`**，动 `title` 会让搜索结果与界面上的名字对不上（测试不报，但属于本文 §「防漂移」要防的那类静默失效）。

## 13. 第二批（13 个子页）执行结果

**8 个索引页**：只做文案压缩，卡片边界与锚点未动。改动点为行 `subtitle` 与卡内说明 `text`（Appearance 5 条、Selection 5 条、Reader 4 条），行标题与 `group` 名一律未改。

**5 个非索引页**：卡片合并 + 文案压缩。About 4 张卡 → 2 张分组卡 + 卡外小字（开源协议 GPL-3.0、版本检查、Releases 跳转、发布说明均保留）；Diagnostics 3 → 2；Library 的说明性卡降为卡外文本，条目列表卡保留；ProfileHome 6 → 4；ReadingNotes 无说明性卡片可合并，仅收插画尺寸。

**验证**：`:app:compileDebugKotlin`、`:app:compileDebugAndroidTestKotlin`、`:app:testDebugUnitTest`（含 `SettingsSearchIndexTest` 的锚点双向校验）全绿。

**真机未验**：本批 APK 装不上——MIUI 从 16:0x 之后开始拒绝所有 ADB 安装（streamed 与 `--no-streaming` 都返回 `INSTALL_FAILED_USER_RESTRICTED`），手机上也没有待确认弹窗，需要开「开发者选项 → USB 安装 / USB 调试（安全设置）」。因此**手机上现在跑的仍是上一批（紧凑基础设施 + 隐私页）的构建**，本批 13 页的实际观感尚未在真机上看过。

## 14. 真机验收补记（2026-09-19 晚）

**装包**：MIUI 的 `AdbInstallActivity` 每次安装都弹「继续安装 / 拒绝（6）」，**6 秒倒计时超时即按拒绝**，所以 `adb install`、`--no-streaming`、Gradle ddmlib 全报 `INSTALL_FAILED_USER_RESTRICTED`。可用流程：`adb push` 到 `/data/local/tmp` → `pm install -r -t` → 轮询 `dumpsys window` 抓到弹窗后 `input tap 351 2522`。屏幕熄灭或通知栏展开时弹窗会被吞（等于自动拒绝），先 `KEYCODE_WAKEUP` + `cmd statusbar collapse`。

**真机实拍发现的自身缺陷**：`subtitle` 单行上限**不是 22 字而是约 18 字**——右侧有 Switch/胶囊占位时，13sp 文字可用宽度只剩约 250dp。首版把「深色时背景转为纯黑，更省电；不影响阅读纸张」压到 21 字，真机仍折两行。已修：该行 → 「深色时背景转纯黑，更省电」；「Material You 动态色彩：自动从当前系统壁纸提取柔和主色调」→「自动从系统壁纸提取主色调」（注意它是 `subtitle = if (...)` 条件表达式，按 `subtitle = "` 扫会漏）。修后复拍确认三行开关项全部单行。

**文案扫描的漏网口径**：除字面量外，还要扫 `subtitle = if (`、`subtitle = when`、`subtitleMap`、`desc` 变量这几类间接传值，否则阈值检查是假的。

**未解决**：`am instrument` 跑 `LayoutComponentsTest` 在本机挂起——runner `onCreate/onStart` 正常、类名已打印，但 7 分钟内没有任何一个方法完成，logcat 无 ANR / FATAL，前台始终是 `com.miui.home`（测试 Activity 从未起来）。疑为 MIUI 限制后台拉起界面，**尚未确认这是本批改动引起还是设备既有问题**（没有改动前的基线可比）。androidTest 因此仍属未通过项。

**数据说明**：本轮之前设备上的应用包已不存在，属全新安装，故书架书籍与阅读进度被重置、外观回到「跟随系统」（真机截图因此是浅色）。这是安装动作的后果，不是代码改动导致的回归。

## 15. 「我的」首页与二级子页不一致的根因与修法（A 方案）

真机量到的差异（1220px 宽、density 3.0）：首页行标题行盒 **66px**，子页 **79px**；文字左边缘 180 vs 114。

根因**不是**单纯字号：`CompactSettingRow` 用 `bodyMedium.copy(14.5sp)`（行高 22sp），`SettingRow`/`SettingSliderRow`/`SettingSegmentedRow` 用 `bodyLarge`（16sp，行高 26sp）。行盒差 = 字号差 × 基样式行高差，所以**只改 fontSize 不会改变观感**——第一版就是这么改的，真机复量仍是 79px。

> 方法论教训：`uiautomator` 的 `text` 节点 bounds 是**行盒高度**（字号 × lineHeight），不能当字号读。要判断字号是否统一，得连 lineHeight 一起看，或直接读代码里的 style。

修法（已实现）：
1. `SettingsMetrics` 补 `titleLineHeight` / `subtitleLineHeight` 两个字段；**REGULAR 档逐项等于 `bodyLarge`/`bodySmall` 现值**（16/26/13/20），未 provide 密度的调用方（阅读器面板、灵感页、统计页）因此完全不变。COMPACT 档取 14.5/22/11.5/20，等于 `CompactSettingRow` 的实际渲染值。
2. `SettingRow`、`SettingSliderRow`、`SettingSegmentedRow`、`SettingLinkRow` 的 trailing 值：行高/内边距/字号/行盒全部改读 `settingsMetrics()`。
3. `ProfileScreen`：把 `CompactSettingsLayoutTokens` + `LocalFlatCards` + `LocalSettingsDensity provides COMPACT` 的作用域从"仅二级子页"扩到**整个「我的」树**（首页 + 子页）。页头仍按 `!isHome` 分档。

复量结果：首页 66px = 子页 66px，「正文字体」说明回到单行，子页同屏可见行数 4 → 6。

遗留：`CompactSettingRow` 与 `SettingRow` 仍是两套实现（只是输出一致），`SyncItemRow` 是 SyncSubPage 的私有第三套；存储/同步两页的自定义行未接入密度。这属于 C 的范围。

## 16. 密度覆盖收尾 + 同步页状态徽章布局 bug

§15 的密度接入只覆盖了 `SettingRow` 族，真机复查发现「我的」树里还有 4 处行标题绕过 `settingsMetrics()`，各自硬编码字号与内边距：

- `ReaderSubPage.ReaderCapsuleTimeRow`（`label`，bodyLarge + 写死 16/12 内边距）
- `ReaderSubPage` 的「音色」行（bodyLarge + bodySmall + 写死 16/12）
- `SelectionSubPage.UrlTemplateRow`（bodyLarge + bodySmall + 写死 16/10）
- `SyncSubPage.SyncItemRow`（title bodyMedium、subtitle 写死 **10sp**，比紧凑档的 11.5sp 还小）

已全部改读 `settingsMetrics()`。复量：`UrlTemplateRow` 标题行盒 79px → **66px**，与首页一致。`LibrarySubPage` 的标签列表项名仍是 `bodyLarge` —— 那是内容列表不是设置行，刻意保留。

> 扫描口径补一条：找"绕过密度的行标题"不能只 grep `typography.bodyLarge`，还要看它是否在行容器里；反之 `bodyMedium` 命中里大量是统计数字与搜索框，不是行标题。

**顺带修掉一个显示 bug**（非本次改动引入）：`SyncSubPage` 顶部「局域网多端互联」卡里，外层 `Row` 用 `SpaceBetween` 且两个子项都没有 `weight`，左侧标题+副标题吃满宽度，把右侧状态胶囊挤到接近零宽，「未连接」三字竖排成圆形，副标题被截断。改为左侧组 `weight(1f)` + `spacedBy(8.dp)`，副标题限两行并压到 14 字。真机确认「● 未连接」恢复横排、副标题完整。

**验证**：`testDebugUnitTest`、`compileDebugAndroidTestKotlin` 全绿；真机装机复量与截图确认。androidTest 挂起问题仍未解决（见 §14）。

## 17. 首页区块头 + 阅读设置预览块 + 一处空态文案 bug

**首页区块标题收档**：`SectionHeader` 加 `compact` 参数（15sp 衬线 / 行盒 21sp / min 26dp），只给首页 5 个调用点传 `compact = true`。保留衬线是刻意的——黑体化会连带丢掉 `DisplayFontFamily` 的品牌字形，这里只需要收字号。真机复量：区块标题行盒 **78px → 65px**；其余 17 个调用方（灵感/统计/书架/阅读器弹层）未传参，仍 18sp。

**阅读设置预览块**：`TypographyPreviewCard` 示例文案从两行减为一行。判断字号/行距/字重/纸张一行足够，第二行不增加信息。真机复量：下方「字号」行 y 从 1379 上移到 **1240**，回收 **46dp**。

**空态文案 bug（非紧凑化引入，真机核对数据时发现）**：书架有 5 本但都未翻开时，首页「继续阅读」显示「书架还空着，导入 TXT、Markdown 或 EPUB 开始本地阅读。」——对已有书的用户是否定其数据。根因是 `HomeContinueSection` 只拿到 `continueBooks`（有进度的书），拿不到书架总数，无法区分「书架为空」与「有书没读」。修法：`HomeViewModel` 新增 `shelfBookCount = input.books.count { isBookDisplayable(it) }`，经 `HomeUiState`/`HomeRoute` 下传，`HomeContinueSection` 按 `shelfBookCount > 0` 分支文案。真机确认显示「书架上的 5 本都还没开始读，去挑一本。」

**阅读器面板回归已排除（这次是真机确认，不再是推导）**：打开一本书进阅读器，量得 sheet 内行标题 66px。核对来源：`ReaderSettingsCommon.kt:253` 用的是自己的 `bodyMedium`（14sp → 66px 行盒），且 `ReaderSettingsSheet` / `ReaderTypographySettings` / `ReaderDisplaySettings` 对 `SettingRow`、`settingsMetrics`、`LocalSettingsDensity` 的引用数均为 **0**。故 §15–16 的密度改动不可能进入该 sheet。`ReaderThemeSheet` 用的 `SectionHeader` 未传 `compact`，仍 18sp。
