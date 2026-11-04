# S10 展示语义决策材料：卷标题契约 + 双 Picker 收敛（2026-09-28，只读）

> Codex 2026-09-28 只读复核：两份 `HeaderFooterPicker` 的折叠文案分叉和 `ReaderDisplaySettings` 六处同包调用均与当前源码相符，尚未运行 UI 测试。卷标题的 raw index 存储/目录分组双语义有代码依据；显示层跳卷计章、自动跳过卷页及扩入「篇/辑/册」仍是产品选择，不视为获批修复。优先安排无需产品裁决的双 Picker 小修。

- 仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`｜分支 `codex/recover-worktree-20260920`｜本轮零源码/测试/账本改动，未运行 Gradle/ADB/MuMu；唯一写入 = 本报告。
- 输入：`AGENTS.md`、`docs/handoff/current.md`、[B 窗口读者风险增量](../../legacy-stages/2026-09/2026-09-28-stage-b-reader-risk-delta.md)、[S10 调查（9/22 快照）](../../investigations/agent-investigation-s10-zcode.md)。**旧报告行号已全部在当前源码重核**，下文只引用本轮验证过的位置。
- 分栏口径：【已确认】= 本轮读码证实；【推测】= 合理推断未运行；【待裁决】= 用户产品选择；【缺口】= 验证缺失。

## 一、分叉一：卷标题在目录是分组、在消费端是普通章

### 1.1 数据层事实（当前源码）

- 【已确认】「卷」没有数据模型字段，唯一判定是整行正则 `VOLUME_HEADER`（`ReaderTocModels.kt:87-92`：第X卷/部、卷[之]?N、上中下卷/部、vol/volume/part + ≤10 字副标题 + 24 字上限）；**不含 篇/辑/册**。
- 【已确认】`isVolumeHeader` 生产调用点**只有一个**：`readerTocEntries`（`ReaderTocModels.kt:53`）——卷行被丢弃、其标题成为后续条目的 `volume` 组名，但**条目保留原始 `index`**（`:57-65`）。`groupChaptersByVolume`（`:71`，注释自称"保留给单元测试和兼容调用"）无生产调用方。
- 【已确认】卷行在 `chapters`/titles 列表里**占一个真实章节索引位**。这是全部兼容性约束的根源：进度、书签、已读、TTS、备份都按原始索引持久化。

### 1.2 八个消费端逐个追到当前调用点

| # | 消费端 | 当前调用点 | 用户实际看到 | 口径 |
|---|---|---|---|---|
| 1 | 目录分组 | `ReaderSheetHost.kt:304` → `readerTocEntries` → `ReaderTocSheet.kt:120-140`（`groupBy { it.volume }`，卷行渲染为**可折叠分组头**） | 卷名是分组标题，**不可点、无反应**（目录里不存在该 entry） | 已确认 |
| 2 | 目录内章号搜索 | `ReaderTocSheet.kt` `filteredEntries`：输入数字按 `entry.index`/`index+1` 匹配 | 章号**含卷行计数**：一本 2 卷 120 章的书，"第 3 章"按原始索引算可能是显示上的第 1 个章节条目 | 已确认 |
| 3 | 点击跳转 | `onPick(entry.index)` → `sheetCallbacks.onPickChapter` → `goToChapter`（`ReaderActions.kt:318-336`） | 章节正常跳；索引空间含卷行 | 已确认 |
| 4 | 页眉/页脚 `{ChapterTitle}` | `CustomTipPlaceholder.kt:96-98`（`source.chapterTitle(chapterIndexFor(absOffset))`） | 翻到 EPUB 卷章（近空页）时，页眉显示卷名本身 | 已确认 |
| 5 | `{ChapterIndex}` / `{ChapterSize}` | `CustomTipPlaceholder.kt:117`（+1）、`chapterCount`（枚举 `:22`"总章数"） | 卷行**计入**：2 卷 120 章显示"122 章"，卷后首章序号偏移 +卷数 | 已确认 |
| 6 | 搜索结果章名 | `ReaderSearchLogic.kt:85`（`chapterTitles[chIdx]`，raw titles） | 结果只带章名、不带卷上下文；命中卷章本身（近空）也会列出 | 已确认（命中卷章概率低为推测） |
| 7 | 进度 / 已读 | `ReaderProgressActions.kt:213/232/290`（`chapterIndexFor`，raw 空间）；已读存储 `ChapterReadRepository.kt:22`（bookId+chapterIndex） | EPUB 顺序翻页**必经**卷章 → 卷行被标已读但目录里不可见；已读集合含隐形条目 | 已确认（具体统计 UI 是否显示"已读 X 章"未逐一验证 = 推测） |
| 8 | TTS / 书签 | `ReaderTtsEffect.kt:52/60/101` 以 chapterIndex 推进；书签按 bookId+章索引/偏移（S09-A-F1 关闭的 bookId 过滤即此链，`ReaderTocBookmarkList.kt`） | TTS 序列里卷行是一个真实条目（近空章）；书签可落在卷章页，恢复按原始索引 | 推测（未运行） |

### 1.3 处理方案对比

| 方案 | 做法 | 数据影响 |
|---|---|---|
| A 维持现状 | 卷是真章，仅目录分组显示 | 零风险；用户怪象 = 章号偏移（消费端 2/5）、隐形已读（7）、EPUB 空卷页（4） |
| **B 显示层换算（推荐）** | 新增**单一**"可读章口径"换算纯函数（raw index ↔ 可读序号），接入消费端 2/5/7 的**显示**处 | **存储不动**：进度/书签/已读/TTS/备份仍用 raw 索引 → 完全兼容存量书籍数据 |
| C 数据层剔除卷行 | 重排 chapters 索引 | **不可行**：破坏存量进度、书签、已读、`JsonBridge` 备份（章节维度数据），明确否决 |

### 1.4 推荐契约（保持现有书籍数据兼容）

> **存储真源不变：原始章节索引（含卷行）是唯一持久化口径。** 目录分组保持现状（卷行=不可点折叠分组头）。显示层由**一个**换算纯函数统一"卷行计章 → 可读章"口径，仅接入章号类显示（目录章号搜索、`{ChapterIndex}`/`{ChapterSize}`、已读统计若有）；`{ChapterTitle}` 在卷章页显示卷名（现状保持）；书签/进度/TTS 存储与恢复零改动。

**必须由用户作产品选择（实现前锁定）：**
- **P-1 章计数口径**：接受 B（显示层跳卷）还是 A（卷算一章）？影响消费端 2/5/7 的可见数字。
- **P-2 顺序路径是否跳过卷行**：翻页"下一章"、TTS 跨章、末章翻页遇近空卷章时，是停留（现状）还是自动越过？影响 EPUB 用户是否会看到空卷页。
- **P-3 判定口径**：`isVolumeHeader` 是否扩入 篇/辑/册（对齐 `TxtChapterDetector`，消除 SP-1 双真源——同一本书「第一卷」分组、「第一篇」不分组）？推荐扩这三个字、其余（序章/番外等）仍按章。
- 【缺口】`VolumeHeaderTest` 现有 5 项仅覆盖假阳性；「第一篇」「第IV卷」等假阴性零用例；`ReaderTocSheetTest` 8 项为 androidTest、无运行记录；跨消费端口径一致性无任何测试。

## 二、分叉二：双 `HeaderFooterPicker` 中文回显 vs 原始 token

### 2.1 当前事实（行号为本轮重核）

| | 我的 → 阅读设置 | 书内 → 显示设置 |
|---|---|---|
| 入口 | `ReaderSubPage.kt:550-590`（6 处），import `ui.components.HeaderFooterPicker`（`:58`） | `ReaderDisplaySettings.kt:103-128`（6 处），同包命中 `ReaderSettingsCommon.kt:783` |
| 折叠行 | `describeTemplate(template).ifBlank { "无" }` → **「章节名 · 当前时间」**（`CustomTipDialog.kt:254`，注释 `:252-253` 明示设计意图） | `template.ifBlank { "无" }` → **`{ChapterTitle} · {Time}`**（`ReaderSettingsCommon.kt:814`） |
| 编辑对话框 | **同一个共享 `CustomTipDialog`**（两份 picker 点开后渲染的都是它：`CustomTipDialog.kt:271` / `ReaderSettingsCommon.kt:831`） | 同左 |

- 【已确认】状态读写殊途同归：书内入口走 `ReaderSettingsRouter`，但六槽位 `customTip*` 不在 `ReaderOverrideKey` → 写**全局**；"我的"入口直接写全局。与 D-2-B 页级文案声明一致，非缺陷。
- 【已确认】预览链：`describeTemplate` 仅 `CustomTipDialog.kt:254` 一处消费；token 校验/插入/示例在共享对话框内，两入口完全一致。**唯一分叉就是折叠行那 4 行。**
- 【已确认】两份 legacy 重载（`CustomTipDialog.kt:287-305`、`ReaderSettingsCommon.kt:844-859`）零生产调用（所有调用点均传 `template =`），属迁移残留。

### 2.2 最小收敛方案（不扩大到页码/热区）

1. `ReaderDisplaySettings.kt` 六处调用改为 import 并使用 `ui.components.HeaderFooterPicker`（该版本多一个可选 `modifier` 参数，签名兼容）。
2. 删除 `ReaderSettingsCommon.kt:783-841`（template 版）与 `:844-859`（legacy 版）——**不触碰** `:743-780` 的 D-2-B `displayValue` 与 scope notice。
3. 删除 `CustomTipDialog.kt:287-305`（legacy 版）。
4. 净效果 ≈ 60 行删除 + 1 条 import；两个入口折叠行统一为中文回显。

**SEAM REQUEST**：上述 3 文件中，`ReaderSettingsCommon.kt` 与 D-2-B（已收尾 9/22）共享文件、不同函数；若窗口 1/2 正持有任一文件，请 Codex 按 SEAM 划转，本报告只登记所有权请求，不直接实施。

## 三、可直接交付实现窗口的任务卡：S10-B1 折叠行文案收敛

| 项 | 内容 |
|---|---|
| **独占文件** | `ui/screen/reader/sheets/ReaderSettingsCommon.kt`（仅删两个 `HeaderFooterPicker`，不碰 `displayValue`/notice）、`ui/components/CustomTipDialog.kt`（仅删 legacy 重载）、`ui/screen/reader/sheets/ReaderDisplaySettings.kt`（改 6 处调用 + import） |
| **依赖** | 无产品裁决依赖（折叠行统一是缺陷修复，`CustomTipDialog.kt:252-253` 注释即设计意图）；仅需 Codex 确认 3 文件无在途窗口（SEAM 上表） |
| **最小变更** | §2.2 四步；**禁止**顺带改页码模型、热区、槽位作用域、`describeTemplate` 本体 |
| **定向 JVM** | 现有 `CustomTipTemplateDescribeTest`（describeTemplate 中文回显）与 `ReaderSettingsScopeNoticeTest`（守卫同文件另一内容）必须保持绿；`compileDebugKotlin` 过 |
| **MuMu 限定验收** | qahost 隔离包：① 现有 `ReaderSettingsSheetTest` 6 项复跑仍绿；② 同一模板（如 `{ChapterTitle} · {Time}`）在「我的→阅读设置」与书内显示设置两处折叠行 dump/截图**显示一致**（均为中文回显）；③ 全程不打开既有书。**结论只写「模拟器证据」，不得表述为实体手机结果** |
| **关闭项** | ST-1（P2）、ST-3（P3）；卷标题侧（SP-1/SP-2）**不在本卡**——待 P-1/P-2/P-3 裁决后另立实现包 |

## 四、边界声明

本轮只新增本报告；未修改产品代码/测试/账本/他人报告；未运行 Gradle/ADB/MuMu，未触碰真实手机与正式书库；未进入原仓库 `D:\develop\Code\Codex\creation-reading-assistant`；未 commit/push/reset/checkout；未使用真实书名。卷标题实现包与渲染级测试补齐（调查报告 S10-B2/ST-2）均待裁决后另行立卡。落盘即停，交总指挥复核。
