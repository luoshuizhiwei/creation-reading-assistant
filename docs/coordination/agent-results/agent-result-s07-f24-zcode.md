# S07-F2.4 执行结果 — Zcode（deepseek v4.1Flash）

- 任务：修复长生命周期弹层在 ReadingUnit dispose 后丢失选区文字、草稿和定位信息的问题
- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`
- 基线：`origin/main = 5aed5939600e491843e3c44453921bd6273a233c`
- 日期：2026-09-20
- 边界遵守：未 reset / checkout / clean / stash / commit / push；未操作设备；未使用 MuMu；未触碰原仓库 `D:\develop\Code\Codex\creation-reading-assistant`

---

## 1. 消费者清单（Task A）

审计范围：所有会打开 sheet / dialog 并在其生命周期内读取 `selectedText` 或 locator 的入口。判定依据是"打开后是否持有选区引用直到用户操作（保存/关闭）"，而非"打开瞬间是否读了一次"。

### 1.1 长生命周期消费者（必须改为快照）

| 消费者 | 打开入口 | 选区文字用途 | 定位/坐标用途 | F2.4 前 | F2.4 后快照字段 | 释放时机 |
|---|---|---|---|---|---|---|
| 笔记 `ReaderNoteDialog` | 选区工具栏 → 笔记（`SetNoteOpen(true)`） | 摘录 `selectedText` | `computeLocatorJson` 生成笔记定位 | 实时读 live | `selectionSnapshot` 全字段 | `SetNoteOpen(false)` 与保存后 |
| 规则 `RULES` | 选区工具栏 → 规则（`OpenSheet(RULES)`） | `initialReplaceText`（替换规则预填） | 不直接用坐标，靠 `replacementCapability` | 实时读 live | `snapshot.text` 作为 `initialReplaceText` | `CloseSheet` / 切到非消费 sheet |
| AI 解释 `AI_EXPLAIN` | 选区工具栏 → AI 解释 | 待解释文本 `selectedText` | 无 | 实时读 live | `snapshot.text` | 同上 |
| 灵感 `INSPIRATION` | 选区工具栏 或 顶栏 | 摘录 `excerpt` | `buildInspirationPayload(..., locatorJson=)` | 实时读 live | `snapshot.text` + 同源 locator | 同上 |
| AI 助读 `AI_ASSIST` | 选区工具栏 或 顶栏 | `contextText` | 无 | 实时读 live | `consumerSnapshot?.text ?: 整章回落` | 同上 |
| 灵感保存（AI 解释 → 灵感） | sheet 内保存 | `excerpt` | `locatorJson` | 实时 `selectedText` + 实时 `computeLocatorJson()` | 与打开时快照同源 | 保存完成 |
| 灵感保存（灵感 sheet） | sheet 内保存 | `excerpt` | `locatorJson` | 同上 | 同上 | 保存完成 |

### 1.2 已确认的"非消费者"（打开时即已快照，不依赖 live 选区）

| 入口 | 为什么不是消费者 | 证据 |
|---|---|---|
| `SEARCH` | 查询词在 `OpenSheet(SEARCH)` 之前已写入 `searchQuery`，sheet 不再读 `selectedText` | 打开动作顺序 |
| `DICTIONARY` | `dictionaryWord` 在 `ReaderScreen.kt:611` 已经先快照，`:612` 才 `OpenSheet(DICTIONARY)` | 源码顺序 |
| `NOTES` | 依赖 `pageIndexManager` 的页码位置，不读选区 | 源码 |
| `TOC` / `SETTINGS` / `PROGRESS` / `BOOK_INFO` / `THEME` | 全程不读选区字段 | 源码审计 |

### 1.3 刻意保持实时读取的入口（短生命周期，不在本任务范围）

选区工具栏上的 **复制 / 浏览器搜索 / 查词典 / 高亮**：这些是一次性即时动作，点击即消费，不存在"打开后长时间存活"的窗口。按任务书第 10 条"不改变 ReadingUnit dispose 清空语义"和第 2 条"正文活动选区仍按原规则清空"，这类动作**不改**，仍然读实时选区。它们不受本次修复影响，也不应被快照化（快照化反而会让"清空后再点复制"复制到旧文本）。

---

## 2. 采用的快照生命周期与释放规则（Task B）

### 2.1 数据结构

`ReaderSelectionSnapshot`（新增于 `ReaderScreenState.kt`），四个字段全部 `val`，不可变：

- `text`（对应 `selectedText`）
- `rangeStart`（对应 `selectedRangeStart`）
- `globalOffset`（对应 `selectedGlobalOffset`）
- `sourceEnd`（对应 `selectedSourceEnd`）

`ReaderScreenState` 新增字段 `val selectionSnapshot: ReaderSelectionSnapshot? = null`。

### 2.2 三个正交操作

| 操作 | 位置 | 写快照 | 动活动选区 |
|---|---|---|---|
| `withSelectionCaptured()` | `ReaderScreenState.kt` | 是（整体替换） | 否 |
| `withSelectionCleared()` | `ReaderViewModel.kt`（**未改动**） | 否 | 是 |
| `withSelectionSnapshotReleased()` | `ReaderScreenState.kt` | 置 null | 否 |

三条正交性由 `ReaderSelectionSnapshotSemanticsTest`（12 个纯函数用例）和 `ReaderConsumerSnapshotWiringGuardTest` 的最后一条结构断言双向锁定。

### 2.3 捕获与释放规则

| 事件 | 行为 | 对应需求 |
|---|---|---|
| `OpenSheet(sheet)` 且 `sheet.consumesSelection()` | 原子捕获四字段（需求 1、2） | 1 |
| `OpenSheet(sheet)` 且非消费 sheet | **主动释放**（需求 8、9） | 8/9 |
| `CloseSheet` | 释放 | 8 |
| `SetNoteOpen(true)` | 捕获 | 1 |
| `SetNoteOpen(false)` | 释放 | 8 |
| `SetSelectedText` | **不动**快照（需求 2） | 2 |
| `ClearSelection` / 翻页 / 换章 / ReadingUnit dispose | **不动**快照（需求 2） | 2 |
| 从顶栏直接开灵感（无选区） | 捕获空 → `null`，不复用旧快照（需求 9） | 9 |

消费分类 `ReaderSheet.consumesSelection()`：`RULES`、`AI_EXPLAIN`、`INSPIRATION`、`AI_ASSIST` = `true`；`TOC`、`NOTES`、`SETTINGS`、`PROGRESS`、`SEARCH`、`BOOK_INFO`、`THEME`、`DICTIONARY` = `false`。

### 2.4 locator 同源（需求 7）

新增 `computeLocatorJsonForSnapshot(epubBook, chapterStartOffsets, snapshot)`：内部复用 `computeLocatorJson`，但四个入参**全部**取自同一份 snapshot。禁止"文字取快照、坐标取已清空的 live 状态"。灵感保存路径改为由 `ReaderSheetHost` 把 `consumerSnapshot?.text` 与 `snapshotLocator()` **成对**下发到 `ReaderLayerBuilders`，两条保存路径（`onSaveAiExplainInspiration`、`onSaveInspiration`）都不再回读 `selectedText` / `computeLocatorJson()`。

### 2.5 规则页稳定化（需求 4）

`ReaderRulesSheet` 原来把 `initialReplaceText` 直接当 `remember` key，它来自实时活动选区，dispose 清空后 key 变化 → `editing` / `tab` 重建 → 草稿丢失 + 跳回目录页。改为：

```kotlin
val pinnedReplaceText = remember { initialReplaceText }
val initialReplaceDraft = remember(pinnedReplaceText, replacementAvailable) { ... }
var tab by remember(pinnedReplaceText, replacementAvailable) { ... }
var editing by remember(pinnedReplaceText, replacementAvailable) { ... }
allowCorrection = pinnedReplaceText != null
```

`pinnedReplaceText` 在宿主生命周期内恒定，三个 `remember` key 因此永不变，`editing`（用户草稿）与 `tab` 不会被重建。

### 2.6 未做的事（需求 10 遵守情况）

- 未新增任何数据库字段
- 未构造 pending-clear 状态机
- 未暂停 TTS
- 未修改 ReadingUnit dispose 的清空语义（`ReaderContentHostPlainTextBranch.kt` 的 dispose 块仅在 Task C 的变异测试中临时改动过，已按 SHA-256 逐字节还原）
- 未把显示偏移量写入持久化；源坐标契约（`LocatorBuilder.forEpub/forPlain`、`LocatorCodec`）保持原样

---

## 3. 改动文件与符号

### 3.1 产品文件（全部在授权的 8 个之内，无 SEAM REQUEST）

| 文件 | 新增/修改符号 |
|---|---|
| `ui/screen/reader/ReaderScreenState.kt` | `data class ReaderSelectionSnapshot`；`ReaderScreenState.selectionSnapshot` 字段；`ReaderSheet.consumesSelection()`；`ReaderScreenState.withSelectionCaptured()`；`ReaderScreenState.withSelectionSnapshotReleased()` |
| `ui/viewmodel/ReaderViewModel.kt` | `OpenSheet` / `CloseSheet` / `SetNoteOpen` reducer 接入捕获与释放；新增 3 处 import（`consumesSelection`、`withSelectionCaptured`、`withSelectionSnapshotReleased`）。`withSelectionCleared()` **未改动** |
| `ui/screen/reader/ReaderSheetHost.kt` | `val consumerSnapshot = inputs.screenState.selectionSnapshot`；`fun snapshotLocator()`；`onSaveAiExplainInspiration` / `onSaveInspiration` 回调签名加 `excerpt`、`locatorJson` |
| `ui/screen/reader/ReaderLayerBuilders.kt` | 两条灵感保存 lambda 改为接收并使用 `excerpt` + `locatorJson`，删除旧的 `val snapshotText = selectedText` / `val locatorJson = computeLocatorJson()` |
| `ui/screen/reader/ReaderScreen.kt` | 笔记弹层改为 `val noteSelection = screenState.selectionSnapshot`，摘录与 `nav.computeLocatorJsonForSnapshot(noteSelection)` 均取快照 |
| `ui/screen/reader/ReaderActions.kt` | `ReaderNavActions` 新增 `val computeLocatorJsonForSnapshot: (ReaderSelectionSnapshot?) -> String?`，并在构造处装配 |
| `ui/screen/reader/ReaderProgressActions.kt` | 新增 `internal fun computeLocatorJsonForSnapshot(epubBook, chapterStartOffsets, snapshot)` |
| `ui/screen/reader/sheets/ReaderRulesSheet.kt` | 新增 `pinnedReplaceText`；三处 `remember` key 与 `allowCorrection` 改为使用 pin 值 |

**没有需要越界修改的文件，因此本次未产生 SEAM REQUEST。**

### 3.2 测试文件

| 文件 | 用例数 | 性质 |
|---|---|---|
| `ui/viewmodel/ReaderSelectionSnapshotTest.kt`（新增） | 17 | ViewModel 级语义 |
| `ui/screen/reader/ReaderSelectionSnapshotSemanticsTest.kt`（新增） | 12 | 纯函数语义 |
| `ui/screen/reader/ReaderNoteSnapshotSaveTest.kt`（新增） | 6 | 真实 `saveReaderNote` 落库断言 |
| `ui/screen/reader/ReaderConsumerSnapshotWiringGuardTest.kt`（新增） | 9 | 源码结构守卫 |
| `ui/screen/reader/ReadingUnitDisposeWiringGuardTest.kt`（重写） | 8 | 源码结构守卫 |

### 3.3 需要主动披露的越界事件

在任务早期，我曾短暂编辑过 **`ui/screen/reader/ReaderScaffold.kt`**（不在授权的 8 个文件内）。该文件已被完整还原，本次改动对它**零残留**。说明：该工作副本相对 `origin/main` 存在大量**既有**改动，属于其他 Agent / 用户的资产，与本任务无关，我没有回滚、格式化或整理它们。

---

## 4. RED / GREEN 命令与退出码

JVM 测试工作目录为 `android/`（模块根）。

### 4.1 RED（先失败）

RED 阶段的失败来自两个来源：

1. **新测试在产品改动前编写**：`ReaderSelectionSnapshotTest`、`ReaderSelectionSnapshotSemanticsTest`、`ReaderNoteSnapshotSaveTest`、`ReaderConsumerSnapshotWiringGuardTest` 在产品代码尚未落地时无法编译（`Unresolved reference`），表现为编译失败而非断言失败。

2. **本次补做的、可复现的行为级 RED 证据**（为报告留证，已做并已还原）：临时把 `ReaderViewModel.kt:358` 的捕获语句 `if (action.sheet.consumesSelection()) opened.withSelectionCaptured()` 替换为不捕获的形式：

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.viewmodel.ReaderSelectionSnapshotTest" \
  --tests "com.creationreadingassistant.ui.screen.reader.ReaderSelectionSnapshotSemanticsTest"
```

- 退出码 **1**，`BUILD FAILED`，`29 tests completed, 9 failed`
- 失败 9 条（全部属于 `ReaderSelectionSnapshotTest`，纯语义类不受影响，正好证明两类测试分工正确）：
  - `opening a consumer sheet captures all four selection fields atomically`
  - `every audited consumer sheet captures the snapshot`
  - `clear selection after capture clears the active fields but keeps the snapshot`
  - `a page turn after capture clears the active fields but keeps the snapshot`
  - `a chapter change after capture clears the active fields but keeps the snapshot`
  - `establishing a new body selection after capture leaves the snapshot untouched`
  - `closing the consumer releases the snapshot`
  - `switching to a non consuming sheet releases the snapshot`
  - `a top bar entry opened without selection does not reuse the previous snapshot`

还原后 `sha256 = fb13c6b58ad8db034af81ae4957d31f3919021ff8395a9bbed539be860fc836c`，与变异前逐字节一致；重跑退出码 **0**，全部 GREEN。

### 4.2 变异测试（证明测试真的能抓住回归）

共 6 个变异，全部被测试捕获，全部按 SHA-256 逐字节还原：

| # | 变异 | 被哪条测试抓住 |
|---|---|---|
| 1 | `withSelectionCleared()` 顺手清快照 | 正交性语义用例 + 结构守卫 |
| 2 | 非消费 sheet 不释放快照 | `switching to a non consuming sheet releases the snapshot` |
| 3 | dispose 块改用**合法的** `val ownsCurrentSelection = remember { ... }` | `ReadingUnitDisposeWiringGuardTest`（旧守卫抓不到，新守卫抓到） |
| 4 | 归属判断提到 `DisposableEffect` 之外 | 同上（新守卫抓到） |
| 5 | 灵感保存回读实时 `selectedText` | `inspiration saves use the snapshot text and snapshot locator together` |
| 6 | 笔记保存回读实时选区 | `note save uses the snapshot locator helper not the live one` |

### 4.3 GREEN（最终）

```bash
./gradlew :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.screen.reader.*" \
  --tests "com.creationreadingassistant.ui.viewmodel.*"
```

- 退出码 **0**，`BUILD SUCCESSFUL`

其他校验：

- `:app:compileDebugKotlin` 退出码 0
- `:app:compileDebugUnitTestKotlin` 退出码 0
- `:app:compileDebugAndroidTestKotlin` 退出码 0（androidTest 源码需随产品签名变更一起编译）

未执行完整 JVM 全量、Lint、APK 打包与真机验收（按任务书要求）。

---

## 5. XML 测试统计

来源：`android/app/build/test-results/testDebugUnitTest/*.xml`，逐文件解析 `tests/failures/errors/skipped` 属性求和。

**总计：96 个类 / 797 个用例 / 0 failures / 0 errors / 0 skipped。**

### 5.1 F1–F2.3 核心回归类（全部有匹配，无零匹配）

| 类 | tests | fail | err | skip |
|---|---:|---:|---:|---:|
| `ReaderViewModelTest` | 72 | 0 | 0 | 0 |
| `RuleEditorDraftTest` | 36 | 0 | 0 | 0 |
| `SearchHitNavigatorTest` | 16 | 0 | 0 | 0 |
| `ReaderSourcePositionTest` | 14 | 0 | 0 | 0 |
| `ReaderSelectionExternalActionsTest` | 14 | 0 | 0 | 0 |
| `ReadingUnitSelectionOwnershipTest` | 10 | 0 | 0 | 0 |
| `ReaderSelectionToolbarModelTest` | 9 | 0 | 0 | 0 |
| `ReadingUnitDisposeWiringGuardTest` | 8 | 0 | 0 | 0 |
| `TxtScrollChapterTargetTest` | 7 | 0 | 0 | 0 |
| `ReaderContentHostSelectionCallbacksTest` | 4 | 0 | 0 | 0 |
| `HighlightSourceLengthTest` | 1 | 0 | 0 | 0 |

### 5.2 本次新增/修改的类

| 类 | tests | fail | err | skip |
|---|---:|---:|---:|---:|
| `ReaderSelectionSnapshotTest` | 17 | 0 | 0 | 0 |
| `ReaderSelectionSnapshotSemanticsTest` | 12 | 0 | 0 | 0 |
| `ReaderNoteSnapshotSaveTest` | 6 | 0 | 0 | 0 |
| `ReaderConsumerSnapshotWiringGuardTest` | 9 | 0 | 0 | 0 |
| `ReadingUnitDisposeWiringGuardTest`（重写） | 8 | 0 | 0 | 0 |

---

## 6. 结构守卫与语义测试分开统计

**这两类不可混为一谈。结构守卫读源码文本做断言，验证的是"代码长这样"；语义测试跑真实逻辑，验证的是"行为是这样"。前者不是 Compose 运行时行为测试，不能用来替代后者。**

### 6.1 源码结构守卫（共 17 个用例）—— 静态，非运行时行为

| 类 | 用例数 | 守卫对象 |
|---|---:|---|
| `ReadingUnitDisposeWiringGuardTest` | 8 | ReadingUnit dispose 归属接线 |
| `ReaderConsumerSnapshotWiringGuardTest` | 9 | 消费者快照接线 |
| **小计** | **17** | |

`ReadingUnitDisposeWiringGuardTest` 按 Task C 收紧的内容：

1. 不再只查"关键字符串出现在文件某处"，改为先用括号配对**真实提取** `DisposableEffect(content.sourceStartAbs)` 块，再在其中提取 `onDispose` 块，断言**只在该块内**成立。
2. 块内断言：归属判断参与 dispose 决策、清空调用被归属判断门控（非无条件发出）。
3. 归属声明用正则解析 `ownsCurrentSelection` 的声明右值，断言必须来自 `rememberUpdatedState`，而不是靠"文件里出现过这串字"。
4. 删除/replace 原来那条检查不可编译形式 `val ownsCurrentSelection by remember(` 的空负向断言。
5. 新增 5 条显式危险形式清单（合法但危险的快照写法，如 `= remember { ... }`），不依赖单条无效字符串。
6. 断言清空信号仍是空文本 + 三个 `-1`。
7. 断言两处归属检查共用同一个被测谓词。

### 6.2 语义/行为测试（共 35 个用例）—— 真实执行

| 类 | 用例数 | 层次 |
|---|---:|---|
| `ReaderSelectionSnapshotTest` | 17 | ViewModel 真实状态机 |
| `ReaderSelectionSnapshotSemanticsTest` | 12 | 纯状态函数 |
| `ReaderNoteSnapshotSaveTest` | 6 | 真实 `saveReaderNote` + `LocatorCodec.decode` 断言 |
| **小计** | **35** | |

### 6.3 需求 → 用例覆盖

| 需求 | 覆盖位置 |
|---|---|
| 1 原子捕获四字段 | `opening a consumer sheet captures all four selection fields atomically` / `every audited consumer sheet captures the snapshot` |
| 2 捕获后清选区/翻页/换章/dispose/新选区均不改快照 | 4 条同族用例 + `establishing a new body selection after capture leaves the snapshot untouched` |
| 3 笔记展示/保存/定位用快照 | `ReaderNoteSnapshotSaveTest` 全 6 条 |
| 4 规则页 `initialReplaceText` 用快照，清选区后不重建 `editing`/`tab`/草稿 | 规则页 pin 结构守卫 + `ReaderSelectionSnapshotTest` 关闭/切换用例 |
| 5 AI 解释、灵感等长生命周期消费者用快照 | 消费者分支守卫 + `opening the note dialog captures the snapshot` |
| 6 AI 助读不退化 | `AI assist never degrades to whole chapter text while a snapshot exists` |
| 7 locator 同源 | `snapshot locator is derived from the snapshot not from live state`、`note save stores a locator derived from the same snapshot`、灵感保存守卫 |
| 8 关闭/保存完成/切非消费 sheet 释放 | `closing the consumer releases the snapshot`、`switching to a non consuming sheet releases the snapshot`、`closing the note dialog releases the snapshot...` |
| 9 顶栏无选区开灵感不复用旧快照 | `a top bar entry opened without selection does not reuse the previous snapshot`、`opening a consumer without a selection keeps the snapshot null` |
| 10/11 不改 DB、不改 dispose 语义、不写显示偏移 | `the active clear helper never writes the snapshot field` + 代码审计（见 §2.6） |

---

## 7. 补丁前后 SHA-256 与每文件增量

### 7.1 整包补丁

| | 文件 | 行数 | SHA-256 |
|---|---|---:|---|
| 改动前 | `/tmp/s07f24-before.patch` | 1273 | `06b575e8290c316507c660edfb7530e62d4ae3762fbda0a972f14da7dfc7515a` |
| 改动后 | `/tmp/s07f24-after.patch` | 1668 | `5e7bda5e3d5f5f5a1657334a79939083bece761db2323f0b449c755a36d7c7ef` |

改动前补丁只含 6 个文件（`ReaderScreenState.kt` 与 `ReaderRulesSheet.kt` 当时相对 `origin/main` 无差异）；改动后补丁含全部 8 个授权文件。

### 7.2 每文件增量（起点 = 我接手时的状态，非 origin/main）

起点状态由「`origin/main` 基线 blob + 改动前补丁」重建得到，因此下表是**真正属于 F2.4 的增量**，不含工作区既有的其他 Agent / 用户改动。

| 文件 | 起点 SHA-256(前16) | 终点 SHA-256(前16) | 行数 Δ | + | − | 起点状态 |
|---|---|---|---:|---:|---:|---|
| `ReaderScreenState.kt` | `62d23612fa1c3b9f` | `974ddeddc6a3c7c1` | +82 | 82 | 0 | = origin/main |
| `ReaderSheetHost.kt` | `0f99072c54285c48` | `b330a66d7549be1d` | +54 | 63 | 9 | 含既有改动 |
| `ReaderProgressActions.kt` | `a8bba1cd87623822` | `933513e1400b852f` | +21 | 21 | 0 | 含既有改动 |
| `ReaderViewModel.kt` | `92a9b3c2d96c016f` | `fb13c6b58ad8db03` | +19 | 22 | 3 | 含既有改动 |
| `ReaderRulesSheet.kt` | `4f90cd6de5f84bd5` | `0d63026fdb25ce42` | +10 | 15 | 5 | = origin/main |
| `ReaderActions.kt` | `149a6047000fd848` | `9e6441c704d9264c` | +8 | 8 | 0 | 含既有改动 |
| `ReaderScreen.kt` | `feddeb206172e196` | `0eb7c904ef2ee337` | +6 | 9 | 3 | 含既有改动 |
| `ReaderLayerBuilders.kt` | `c4c930dd8e907cee` | `74a07e46765355fa` | +3 | 9 | 6 | 含既有改动 |
| **合计** | | | **+203** | **229** | **26** | |

### 7.3 仓库状态

- `git diff --check`（授权 8 文件）：**退出码 0**，无空白错误、无冲突标记
- **暂存（staged）文件数：0**
- **未提交、未推送、未操作设备**
- 本次我改动的文件：
  - 产品 8 个（授权清单内，全部为 unstaged modified）
  - 测试 5 个（4 个新增 untracked + 1 个重写 untracked）
- 工作区中其余 200+ 个 unstaged 文件为其他 Agent / 用户的既有改动，我**未**回滚、格式化或整理

---

## 8. 已确认事实 / 静态推断 / 未验证的运行时行为

### 8.1 已确认（有测试或命令输出支撑）

- 打开消费型 sheet 会原子捕获四个字段；捕获后 `ClearSelection`、翻页、换章、建立新正文选区均不改变快照内容。
- 关闭、切到非消费 sheet、关闭笔记弹层会释放快照；无选区时捕获得到 `null`。
- 笔记保存的摘录与 locator 均来自快照；locator 经 `LocatorCodec.decode` 断言偏移与指纹正确。
- 灵感两条保存路径不再回读实时 `selectedText` / `computeLocatorJson()`。
- `withSelectionCleared()` 不写 `selectionSnapshot`（源码断言 + 语义用例双重锁定）。
- 规则页三个 `remember` key 由 pin 值构成，恒定不变。
- 96 类 / 797 用例全绿；11 个指定核心回归类均有匹配、非零匹配。
- 6 个变异全部被捕获，且全部按 SHA-256 逐字节还原。

### 8.2 静态推断（代码可读出，但没有运行时断言）

- `pinReplaceText` 使 `editing`（用户草稿）与 `tab` 在活动选区被清空后不被重建 —— 这是 Compose `remember` key 语义的直接推论，本次**没有**用 Compose 运行时测试验证。
- AI 助读的「快照优先、无快照才回落整章」顺序保证不退化 —— 由源码结构守卫断言表达式形状，未做运行时降级观测。
- 非消费 sheet（SEARCH / DICTIONARY / NOTES 等）打开时主动释放快照，可避免"上一次的旧快照残留"—— 由 VM 级用例断言状态，未覆盖全部 UI 路径。

### 8.3 未验证的运行时行为（诚实标注）

- **未做真机 / 设备验收**（任务书禁止操作设备，也未使用 MuMu）。TTS 跟读 → `scrollToItem` → ReadingUnit dispose 这一真实触发链路**没有在设备上跑过**。
- **未做 Compose UI 运行时测试**（`androidTest` 仅做了编译，未执行）。
- 因此，「清空后再保存笔记仍带摘录和定位」在**状态机与保存函数层**已被证明，在**真实 UI 交互链路**上仍是推断。
- 未执行 Lint、未打包 APK、未跑全量 JVM 测试。

---

## 9. 遗留风险

1. **真实触发链路未端到端验证**：本次验证停在状态机与纯函数层。TTS 跟读滚动导致 dispose 的完整链路需要在真机上跑一次才能确认为修复完成。
2. **`consumesSelection()` 是手工维护的分类表**：将来新增一个消费选区的 `ReaderSheet` 枚举值而忘记加入该表，就会静默退回 live 读取。已有 `ReaderConsumerSnapshotWiringGuardTest` 断言"每个消费分支都读 `consumerSnapshot`"，但新枚举若未进入消费集合，守卫不会报错。建议后续把分类改成 `ReaderSheet` 自身的属性，让新增枚举强制作答。
3. **灵感保存的 `excerpt` / `locatorJson` 是成对下发的**：当前由调用点保证同源。若将来有人在 `ReaderLayerBuilders` 里重新引入实时 `computeLocatorJson()`，只有那条结构守卫会拦住它。
4. **工具栏短生命周期动作仍读实时选区**：这是刻意设计，但意味着"选区被清空后再点复制"会复制空文本。若产品期望这类动作也有兜底，需要单独提需求，不在本任务范围。
5. **`ReaderScreenState` 增加了一个字段**：所有构造点都走 `copy`，无破坏性；但若将来有代码直接构造 `ReaderScreenState(...)` 全参，需要留意默认值。
6. **工作区存在大量其他 Agent / 用户的未提交改动**：本次改动与之共存。集成者合并时需注意 `ReaderViewModel.kt` 等 6 个文件本身就有 F2.4 之外的既有 diff。

---

## 10. 最终状态

**等待 Qoder/Codex 复核。**

未提交、未推送、未操作设备、未使用 MuMu、未越界修改授权清单外的产品文件。不自行宣布 S07 完成，也不自动领取 F3、S08 或其他任务。
