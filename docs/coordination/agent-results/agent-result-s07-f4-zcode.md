# S07-F4 最后开发收口 — Zcode 执行报告

> 执行 Agent：Zcode　|　实际模型：`deepseek v4.1Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 工作分支：`codex/recover-worktree-20260920`
> 固定比较基线：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 报告路径：`docs/coordination/agent-results/agent-result-s07-f4-zcode.md`
> 状态：**等待 Codex 主任务复核**（不自宣布 S07 完成，不写"发布完成"）

---

## 1. 本轮约束与遵守情况

| 约束 | 结果 |
|---|---|
| 不 reset / checkout / clean / stash | 未执行任何一条 |
| 不全仓格式化 | 未执行 |
| 不 stage / commit / push | 未执行；`git diff --cached` 为空、`git stash list` 为空 |
| 不删除或覆盖非 S07-F4 改动 | 只写本轮授权文件；其余改动原样保留 |
| 不使用 `git add -N` | 未使用；未跟踪文件改用显式清单 + `git diff --no-index` + SHA256 |
| 不做真机验收 / 不用 MuMu | 未执行任何 adb 或设备操作 |
| 不读改 `D:\Application\文档\Obsidian\Claude` | 未访问 |
| 不修改 Codex Obsidian 记忆库 | 未访问 |

---

## 2. F4 遗留账本（逐项标记，不因 P3 静默丢弃）

来源：`docs/coordination/reviews/2026-09/2026-09-20-zcode-qoder-audit-board.md` F2.4 / F3 / F3.1 遗留段 +
`docs/code-review/2026-09/2026-09-20-android-s07-selection-review.md` + Qoder F3.1 复审意见。

| # | 遗留项 | 定级 | 标记 | 处理结果 |
|---|---|---|---|---|
| L1 | 非分页 EPUB 单处纠错只有 UI capability 一道门，ViewModel 命令层无纵深防御 | P2 | **FIX + TEST** | 完成：`readerLoadedContentReplacementAvailability` + `resolveSingleCorrectionAnchor`；`ReaderSingleCorrectionCapabilityTest`（11） |
| L2 | 同一个 ViewModel 跨书复用时不释放消费者快照 | P2 | **FIX + TEST** | 完成：`openBook()` 切书分支加 `.withSelectionSnapshotReleased()`；`ReaderSelectionSnapshotTest` +4（21） |
| L3 | 消费者分类表（`ReaderSheet.consumesSelection`）可能静默漏项 | P2/P3 | **TEST（源码已穷举，不改）** | 完成：`ReaderConsumerAndActionGateTest` 反射 + 源码级门禁（6） |
| L4 | 动作分发表完整性（F1 遗留 P2） | P2 | **TEST** | 完成：同文件源码级 `"id" ->` 分支门禁 |
| L5 | Markdown `charCount=0` 时高亮的降级口径未定案 | P2 | **FIX + DOCUMENT + TEST** | 完成：持久高亮走"仅单元求交"最佳努力；搜索命中返回空；`MarkdownUnknownChapterLengthTest`（8） |
| L6 | 复制提示真实性（F1 已修，缺固化） | P2 | **TEST（已存在）** | 已核实 `ReaderCopyNoticeTest`（3）全绿，未重复造轮子 |
| L7 | 外部动作提示真实性 | P2 | **TEST（已存在）** | 已核实 `ReaderSelectionExternalActionsTest`（14）全绿 |
| L8 | F3.1 报告"双重门控"措辞与事实不符（Qoder ST-1） | P3 | **DOCUMENT** | 完成：改写 `EpubSingleCorrectionReachabilityTest` KDoc |
| L9 | 术语：canonical vs source、"整块"不伪装精确选词 | P3 | **DOCUMENT** | 核实：两处 KDoc 已就位（`MarkdownScrollHighlight.kt:163-174`、`ReaderContentHostEpubBranch.kt:44-62`）未退化 |
| L10 | 坐标：`gOff` 混合量纲口径 | P3 | **DOCUMENT** | 核实：`ReaderContentHostEpubBranch.kt:60-62` 已写明"旧版混合量纲"且禁止改写为真实字符偏移 |
| L11 | 写侧 dormant 判据（F1 遗留 P2） | P2 | **TEST** | 完成：`ReaderConsumerAndActionGateTest` 覆盖 `DORMANT_IDS` |
| L12 | 快照产品口径文档化（F2.4 遗留） | P3 | **DOCUMENT** | 写入本报告 §6「术语与坐标收口」 |

**账本结论：12 项全部落账，无一项被静默丢弃；0 项需要 `SEAM REQUEST`（未越界修改任何边界外文件）。**

---

## 3. 修改文件与 hunk 目的

| 文件 | 类型 | hunk 目的 |
|---|---|---|
| `android/app/src/main/java/.../ui/viewmodel/ReaderViewModel.kt` | 修改 | ①新增 `readerLoadedContentReplacementAvailability()`；②`ExecuteRuleCommand` 的 `SaveSingleCorrection` 分支加能力门；③新增 `currentReplacementCapability()` / `resolveSingleCorrectionAnchor()`；④`openBook()` 切书分支加快照释放（4 个 hunk） |
| `android/app/src/main/java/.../ui/screen/reader/MarkdownScrollHighlight.kt` | 修改 | ①`markdownScrollSearchHits` KDoc 写明"章长度未知返回空列表"是**显式裁决**及与持久高亮**刻意不同**的理由；②`projectPersistentHighlights` 注释写明空 span 降级的**代价**（可能投影别章高亮）与权衡 |
| `.../test/.../ui/viewmodel/ReaderSingleCorrectionCapabilityTest.kt` | 新增 | L1 行为证明（11） |
| `.../test/.../ui/viewmodel/ReaderSelectionSnapshotTest.kt` | 修改 | 第 8 组：跨书复用释放快照（+4，共 21） |
| `.../test/.../ui/screen/reader/ReaderConsumerAndActionGateTest.kt` | 新增 | L3/L4/L11 门禁（6） |
| `.../test/.../ui/screen/reader/MarkdownUnknownChapterLengthTest.kt` | 新增 | L5 降级口径（8） |
| `.../test/.../ui/screen/reader/EpubSingleCorrectionReachabilityTest.kt` | 修改 | L8 措辞更正（3 处 KDoc/注释） |

### 修改后 SHA256（全 64 位）

```
1e83dd39ba89b0684a9251a8ef13d3d44038bc1f4114cbbd1f0338f9aff5a2e6  .../ui/viewmodel/ReaderViewModel.kt
89bd78e2c77d637c736ec2accdb4f399342962f4f38e02a6dbff13c170d5e1c7  .../ui/screen/reader/MarkdownScrollHighlight.kt
cea979a8a6b77bcdb2062f759036e028dd3072fd12fa857f24bc6c51b33afb57  .../test/ui/viewmodel/ReaderSingleCorrectionCapabilityTest.kt
e9c2785d17cdfef54177f1da327df408dd12c43764b48dfd19e2768174bd30be  .../test/ui/viewmodel/ReaderSelectionSnapshotTest.kt
942c57bdf71de256d941e45e25364310756ea048c7d4fab3339e763d76c91c91  .../test/ui/screen/reader/ReaderConsumerAndActionGateTest.kt
810596f8ca6bb72b83c743b77d5239a61de5dc654ea254f34811064ca3ef4b5f  .../test/ui/screen/reader/MarkdownUnknownChapterLengthTest.kt
c770a357a332f427927b52da925fbad1888671698d669906c2c59cd3da845b6c  .../test/ui/screen/reader/EpubSingleCorrectionReachabilityTest.kt
```

**未跟踪文件的补丁证据（不使用 `git add -N`，改用 `git diff --no-index`）：**

```
ReaderSingleCorrectionCapabilityTest.kt  -> 1 file changed, 511 insertions(+)
ReaderConsumerAndActionGateTest.kt       -> 1 file changed, 181 insertions(+)
MarkdownUnknownChapterLengthTest.kt      -> 1 file changed, 195 insertions(+)
```

（`ReaderSelectionSnapshotTest.kt` 与 `EpubSingleCorrectionReachabilityTest.kt` 同为未跟踪文件，
其存在性与内容哈希见上表；`ReaderSelectionSnapshotTest.kt` F4 修改前的 SHA256 为
`3bf7dfb7f7b2a68330b179e8af3ff638c286f8ce941ce2c4abb1de35fb583711`。）

---

## 4. 必修项一：ViewModel capability 事实源与绕过测试

### 为什么这道门是真防御（不是重复 UI）

`RulesRepository.saveSingleCorrection`（`RulesRepository.kt:309`）只校验
`sourceStart < 0`、`sourceEnd <= sourceStart`、`findText.isBlank()`、`replaceText == findText` ——
**不校验 `[sourceStart, sourceEnd)` 处的文字是否等于 `findText`**。
因此"绕过 UI 直接构造 `RuleCommand.SaveSingleCorrection`"在 F4 之前**会真实落库**，
写入一个估算坐标系的锚点且不报错。这就是纵深防御缺口的实质，不是纸面风险。

### 事实源统一

新增顶层函数 `readerLoadedContentReplacementAvailability(content: ReaderLoadedContent?)`
按 sealed 类型映射到 `PagedReplacementAvailability`：

| content | availability | capability |
|---|---|---|
| `Text` | `APPLIED` | `Available` |
| `Epub` | `ESTIMATED_COORDINATES` | `Unavailable` |
| `Markdown` | `NON_SOURCE_COORDINATES` | `Unavailable` |
| `null` | `SOURCE_UNAVAILABLE` | `Unavailable` |

它**故意不复制**分页器的投影/长度裁决（那是 `readerReplacementCapability` 的下游），
只做"内容类型 → 坐标空间性质"这一层，避免两处事实源各写一套判定而漂移。

### 绕过测试（`ReaderSingleCorrectionCapabilityTest`，11 tests 全绿）

- 4 条映射测试（TXT / EPUB / Markdown / null）；
- 3 条**绕过测试**：用 `documentLoader.load` 真实加载各格式后，直接构造
  `RuleCommand.SaveSingleCorrection` 派发，断言返回 `NotAnchorable` 且
  `FakeReaderCorrectionDao` 行数 = 0（证明没有落库，不只是没报错）；
- 1 条 TXT 接受测试：断言落库行 `source_start=100`、`source_end=102`（证明门没把正常路径也堵死）；
- 2 条 TXT 拒绝测试：`findText` 与当前选区文字不等 / 无可用锚点；
- 1 条"与工具栏同一映射"一致性测试。

**变异验证（证明测试咬得住）：**

| 变异 | 失败数 |
|---|---|
| EPUB 映射改为 `APPLIED` | 3 FAILED |
| 门控分支改为 `if (false)` | 2 FAILED |

---

## 5. 必修项二：切书快照清理测试

`ReaderSelectionSnapshotTest` 新增第 8 组（4 tests，类内总数 21，全绿）：

| 用例 | 断言 |
|---|---|
| `switching books releases the consumer snapshot` | 切到不同 `bookId` 后快照为 null |
| `switching books keeps the active selection untouched but drops the snapshot` | 活动选区仍保留、快照已释放（正交性） |
| `reopening the same book does not release an in flight snapshot` | **反向钉子**：同一 `bookId` 重复 `OpenBook` 时快照存活，防过度清理 |
| `a book switch clears the stale rule feedback too` | `_ruleMutationResult` 归零，旧书的失败提示不带到新书 |

**变异验证：** 移除 `.withSelectionSnapshotReleased()` → 2 FAILED。

---

## 6. 必修项三：action / consumer 分类门禁

`ReaderConsumerAndActionGateTest`（6 tests，全绿）：

| 用例 | 断言 |
|---|---|
| `ReaderSheet` 值集合 | 反射断言 12 个值，新增枚举值时测试失败 |
| 消费者集合 | 消费者 == {RULES, AI_EXPLAIN, INSPIRATION, AI_ASSIST}；非消费者 == 其余 8 个 |
| **源码级**：`consumesSelection` 无 `else ->` | 读源文件断言，杜绝新增枚举被静默归入默认分支 |
| 逐 sheet 捕获/释放 | 4 个消费者捕获、8 个非消费者不捕获 |
| **源码级**：动作分发表完整性 | 断言每个可渲染 action id 在 `ReaderSelectionToolbar` 的 `onActionClick` 中都有 `"id" ->` 分支 |
| dormant 动作 | `DORMANT_IDS = {"edit"}` 不出现在 `pickableDefs()` |

**变异验证：**

| 变异 | 失败数 |
|---|---|
| `consumesSelection` 加 `else -> false` | 1 FAILED |
| 删掉 `"inspiration" -> onInspiration()` | 1 FAILED |

---

## 7. 必修项四：Markdown `charCount=0` 安全降级

**两条路径的降级刻意不同**，已在源码 KDoc 里写死为显式裁决（不是遗漏）：

| 路径 | 章长度未知时 | 理由 |
|---|---|---|
| 持久高亮 `projectPersistentHighlights` | **不**按 span 过滤，退化为"仅按渲染单元求交" | 用户数据，静默消失 = 丢东西 |
| 搜索命中 `markdownScrollSearchHits` | 返回 `emptyList()` | 瞬时反馈，代价只是一次没高亮；错高亮会误导用户 |

代价已明确写进注释：章长度未知时防串章只剩单元求交一道，落在别的章但与本章单元坐标重叠的
高亮**可能被投影到本章**；权衡是"可能多画一处旧高亮" vs "绝不静默丢掉用户数据"，
且章长度落定后自然收敛。

`MarkdownUnknownChapterLengthTest`（8 tests，全绿）：

- 空 span 时保留数据（不吞高亮）；
- 空 span 时仍受单元求交约束（不是无条件全画）；
- 已知长度恢复 span 过滤；
- `chapterLength = 0` 与 `-1` 都抑制搜索命中；
- 已知长度时搜索命中正常高亮；
- **两条路径降级不同**的显式断言（`persistent != search`）。

**变异验证：** span 过滤改为无条件执行 → 3 FAILED。

---

## 8. 必修项五 / 六：提示真实性与术语坐标收口

### 提示真实性

| 场景 | 测试 | 结果 |
|---|---|---|
| 复制成功/失败文案与真实剪贴板写入一致 | `ReaderCopyNoticeTest`（3） | 全绿 |
| 外部动作空选区 / 无 handler / 回退提示 | `ReaderSelectionExternalActionsTest`（14） | 全绿 |

两处均为 F1 遗留 P2，本轮核实已固化，未重复新建。

### 术语与坐标收口

- **canonical vs source**：`MarkdownScrollHighlight.kt:163-174` 已写明本组函数返回 canonical
  空间区间，与 `MarkdownRenderUnit.sourceRange`（解码后源文本空间）不是同一空间，旧名已统一改为 canonical。
- **"整块"不伪装精确选词**：同上段已写明"这是本切片的产品与实现边界，不是 Compose 的平台上限"，
  并要求"报告与 UI 都必须写『整块』，不得伪装成可拖动选词"。
- **`gOff` 混合量纲**：`ReaderContentHostEpubBranch.kt:60-62` 已写明该偏移是"旧版混合量纲"
  （章起点来自 ZIP 字节估算），但正是 `LocatorBuilder.forEpub` / `LocatorCodec` 的保存与回读空间，
  **禁止改写为真实字符偏移**。
- **F3.1 措辞更正（Qoder ST-1）**：`EpubSingleCorrectionReachabilityTest` 原 KDoc 把"工具栏门控"
  与"能力门控"并列称为**双重门控**，与事实不符 —— 两者读的是同一个
  `PagedReplacementAvailability` 事实源，不是两道独立门。已改为：本文件只钉"能力位"与
  "锚点/文字契约"，第二道门明确指向 F4 新增的 ViewModel 命令层校验。

---

## 9. 测试命令、退出码与结果

### 直接相关定向测试（逐轮，均 EXIT=0 / BUILD SUCCESSFUL）

| 命令（`android/` 目录） | 结果 |
|---|---|
| `./gradlew :app:compileDebugKotlin` | EXIT=0 |
| `./gradlew :app:compileDebugUnitTestKotlin` | EXIT=0 |
| `./gradlew :app:testDebugUnitTest --tests "…ReaderSingleCorrectionCapabilityTest"` | 11/11 绿 |
| `./gradlew :app:testDebugUnitTest --tests "…ReaderSelectionSnapshotTest"` | 21/21 绿 |
| `./gradlew :app:testDebugUnitTest --tests "…ReaderConsumerAndActionGateTest"` | 6/6 绿 |
| `./gradlew :app:testDebugUnitTest --tests "…MarkdownUnknownChapterLengthTest"` | 8/8 绿 |
| `./gradlew :app:testDebugUnitTest --tests "…EpubSingleCorrectionReachabilityTest"` | 8/8 绿 |

### S07 汇总测试集

**命令：**
```
./gradlew :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.reader.*" --tests "com.creationreadingassistant.ui.viewmodel.*"
```

**退出码：1**（908 tests completed, **1 failed**）

| 项 | 数 |
|---|---:|
| 测试类 | 107 |
| tests | 908 |
| failures | 1 |
| errors | 0 |
| skipped | 0 |

**唯一失败项：`ProfileWebDavManagerTest > downloadRestore fetches and imports backup json`**
（`java.lang.IllegalStateException` at `TestMainDispatcherJvm.kt:45`，根因 `MainDispatchers.kt:111`
→ `HandlerDispatcher.kt:51`，即 JVM 主调度器/Handler 环境缺失）。

- 该文件与 S07-F4 **零关系**：属 S14 WebDAV 切片；
- 该文件与 `WebDavBackup.kt`、`WebDavSubPage.kt`、`ProfileWebDavManager.kt`
  在本轮开始前就已是 `M`（工作区既有用户资产），F4 未触碰其中任何一个；
- 失败原因是环境/调度器，不是产品回归。

### S07 相关类的结果（全部 F0 E0）

```
EpubBlockPersistentHighlightTest            15   EpubBlockSourceRangeTest            9
EpubSingleCorrectionReachabilityTest         8   HighlightSourceLengthTest           1
MarkdownBlockSelectionLocatorTest           10   MarkdownHighlightChapterSpanTest    10
MarkdownPersistentHighlightProjectionTest   11   MarkdownScrollHighlightTest         11
MarkdownSelectionPositionTest                3   MarkdownUnitSourceRangeTest         10
MarkdownUnknownChapterLengthTest             8   ReaderConsumerAndActionGateTest      6
ReaderConsumerSnapshotWiringGuardTest        9   ReaderContentHostSelectionCallbacksTest 4
ReaderCopyNoticeTest                         3   ReaderReplacementCapabilityTest     15
ReaderSelectionExternalActionsTest          14   ReaderSelectionGranularityTest       8
ReaderSelectionRangeWiringGuardTest          9   ReaderSelectionSearchPolicyTest      1
ReaderSelectionSnapshotSemanticsTest        12   ReaderSelectionToolbarModelTest      9
ReaderSelectionSnapshotTest                 21   ReaderSingleCorrectionCapabilityTest 11
```

### 结构守卫与行为测试分开统计

| 类别 | 类数 | tests |
|---|---:|---:|
| **结构守卫**（反射/源码文本断言：枚举集合、无 `else`、分发表完整性、wiring/range/snapshot 守卫） | 7 | 61 |
| **行为测试**（真实加载、真实派发、真实落库/投影断言） | 17 | 155 |
| 合计（S07 子集） | 24 | 216 |

结构守卫不冒充行为覆盖：本轮 6 组新测试中，只有"枚举集合 / 无 else / 分发表完整性"三项
是结构性的，其余全部走真实 `documentLoader.load` + 真实命令派发 + 真实 DAO 断言。

---

## 10. 未运行与 WorkBuddy 事项

| 项 | 说明 |
|---|---|
| 真机验收 | **未做**（本轮禁止）。`adb`/MuMu 全程未使用 |
| `androidTest` 插桩测试 | 未运行（本轮只跑 JVM 单元） |
| `:app:lintDebug` | 未运行 |
| `assembleDebug` / `assembleRelease` | 未运行 |
| **`ProfileWebDavManagerTest`** | 失败但未修 —— 属他人在途改动（S14），修它属越界；**SEAM 提示**：需 Codex 主任务决定是否移交该切片 owner |
| WorkBuddy 待办 | 小屏/横屏/大字体五个动作拥挤与截断；TalkBack 焦点顺序与手柄语义；无浏览器/无字典/无 PROCESS_TEXT handler 真机回退；翻页/切章/关闭面板后选区清除的真机确认 |

---

## 11. 范围完整性证据

### 修改前后范围证据

- `git log --oneline -1` = `5aed593`（基线未被推进）；
- `git diff --cached --stat` 输出行数 = **0**（index 未被触碰）；
- `git stash list` 条数 = **0**；
- 工作区 `git status --porcelain` 共 344 行（海量既有用户资产，本轮只增 3 个新文件 + 改 4 个文件）。

### 边界外关键文件 SHA256 自检（基线 vs 当前）

对**本轮不修改**的关键文件逐一取基线 blob 哈希与当前文件哈希比对：

| 文件 | 基线 SHA256(前16) | 当前 SHA256(前16) | git 状态 | 是否本轮改动 |
|---|---|---|---|---|
| `ReaderLayerBuilders.kt` | `d3bda96f22bfe136` | `74a07e46765355fa` | `M` | 否（基线前已由他人改，mtime 18:03） |
| `ReaderSelectionToolbar.kt` | `bf3201b06a8571b4` | `04301c010537c41a` | `M` | 否（mtime 23:26 系读后重写落盘，内容哈希与 F4 前备份一致） |
| `ReaderSearchIndexCoordinator.kt` | （基线无此文件） | `2540de7b4a0a5da8` | `??` | 否（未跟踪，本轮只读） |
| `SelectionActionSettings.kt` | `132044a2fec6aa80` | `2a906f11ee532423` | `M` | 否（mtime 09:05） |
| `SelectionActionStore.kt` | `aac469914a1a97ce` | `d1d011b7fef4ded9` | `M` | 否（mtime 2026-09-15） |
| `ReaderContentHostEpubBranch.kt` | `ac72a6139211c042` | `bfcc6acfc6d7ee39` | `M` | 否（mtime 21:20） |
| `ReaderSheetHost.kt` | `7e250629bc642543` | `b330a66d7549be1d` | `M` | 否（mtime 18:28） |
| `ReaderScreenState.kt` | `62d23612fa1c3b9f` | `974ddeddc6a3c7c1` | `M` | 否（mtime 23:25 系读后重写落盘，哈希与 F4 前备份 `974ddedd…` 一致） |
| `ReaderContentHostMarkdownBranch.kt` | `963b8063c5f55990` | `cc3c1ccbc9d986d5` | `M` | 否（mtime 21:09） |
| `ReaderRulesSheet.kt` | 不存在 | 不存在 | — | 未触碰 |

本轮 mtime 落在 F4 时段（23:16–23:43）的**只有**上表 §3 列出的 7 个文件；
`ReaderSelectionToolbar.kt`（23:26）与 `ReaderScreenState.kt`（23:25）虽 mtime 在本轮时段内，
但当前哈希与 F4 修改前记录的哈希逐字节一致（`04301c01…` / `974ddedd…`），
说明是工具读取重写而非内容改动 —— 这里用**哈希**而非 mtime 下结论。

`ReaderSearchIndexCoordinator.kt`、`EpubSingleCorrectionReachabilityTest.kt`、
`ReaderSelectionSnapshotTest.kt` 在基线上不存在（未跟踪），其证据形式按约束
（六-4）改为"显式文件清单 + `git diff --no-index` + SHA256"，**未使用 `git add -N`**。

---

## 12. 双轴结论

### Standards 轴（工程质量、可维护性、测试纪律、文档一致性）

**问题数：4**（本轮处理 3，留 1）

| 级别 | 数量 | 项 |
|---|---:|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 1 | 消费者分类与动作分发表目前只有**测试门禁**，枚举新增后仍需人工补 `when` 分支；理想做法是编译期穷举（已用 exhaustive `when` 无 `else`，但新增枚举值仍会编译失败 —— 实际已是编译期保障，故降为观察项） |
| P3 | 3 | ①F3.1 报告"双重门控"措辞错误（已更正）；②canonical/source 术语混用风险（KDoc 已就位，未做全仓统一）；③`gOff` 混合量纲口径（已文档化，未做重构） |

**Standards 最严重项：P3-① 报告措辞与事实不符（已更正）**。
此项本身无运行时影响，但在 Qoder ST-1 中被点名，说明"报告与代码不一致"是本协作链路的系统性风险 ——
F4 采取的做法是**让报告结论必须能指向一处可执行测试**，而非停留在文字论证。

### Spec 轴（用户可感知行为、产品契约、数据正确性）

**问题数：3**（本轮全部处理）

| 级别 | 数量 | 项 |
|---|---:|---|
| P0 | 0 | — |
| P1 | 0 | — |
| P2 | 2 | ①非分页 EPUB / Markdown 单处纠错绕过 UI 会落库错误锚点（**已修**：ViewModel 命令层门控，绕过测试已证明落库行数 = 0）；②同 VM 跨书复用时消费者快照泄漏（**已修**：切书释放 + 反向钉子测试） |
| P3 | 1 | Markdown 章长度未知时持久高亮可能投影别章高亮（**已定案为显式降级并写明代价**，非缺陷） |

**Spec 最严重项：P2-① 单处纠错错误锚点落库**。
这是唯一的**数据正确性**问题：F4 之前绕过 UI 的 `SaveSingleCorrection` 会静默写入
估算坐标系的锚点且不报错（`saveSingleCorrection` 不校验偏移处文字），后续替换会作用在错误位置。
F4 已在落库前拦下，并用"真实加载 + 真实派发 + DAO 行数断言"证明。

---

## 13. S07 是否已达到"可交 WorkBuddy 真机验收"

**答：本机静态与 JVM 证据层面已具备交付条件，但本轮明确不声明"可以交真机"。**

理由：

1. 本轮**未做任何真机验收**（任务禁止），WorkBuddy 的真机清单（§10）全部仍是未验证项；
2. S07 汇总 908 tests 中有 **1 个失败**，且该失败属他人在途改动（S14 WebDAV），
   不能由 F4 判定为"不影响发布"—— 需 Codex 主任务裁决其归属；
3. 历轮 F1→F4 中，WorkBuddy 关注的**布局拥挤/截断、TalkBack 语义、无 handler 真机回退**
   三类问题从未在真机上验证过，JVM 测试无法覆盖；
4. F3 的 P1（Markdown locator）虽已由 F3.1 关闭，但其修复只在单元与接线层验证，
   真机上的"保存 → 关闭 → 重开 → 回显"整链仍未跑过。

**因此本报告的结论是：S07 的开发收口（F4 范围）已完成并自证，
是否放行 WorkBuddy 真机验收由 Codex 主任务复核后决定。**

本报告**不写"发布完成"**，也**不领取 S08**。

---

## 14. 回传

- Agent 与实际模型：**Zcode / `deepseek v4.1Flash`**
- 报告绝对路径：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\agent-results\agent-result-s07-f4-zcode.md`
- 问题数：Standards 4（最严重 P3-①，已更正）、Spec 3（最严重 P2-①，已修并证明）
- 测试：S07 汇总 107 类 / 908 tests / 1 failure（S14 WebDAV，非本轮改动）/ 0 error / 0 skipped
- 是否修改报告以外文件：**否**（仅 §3 列出的 7 个文件，全部在 F4 授权范围内）
- **等待 Codex 主任务复核，未领取下一片**
