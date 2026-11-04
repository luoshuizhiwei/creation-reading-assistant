# S05-④「导入完成后查看」定位错误修复（窗口 B）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`（未变）
- HEAD：`5aed5939600e491843e3c44453921bd6273a233c`（未变，40 位；本行曾误记为 42 位，见 §8.7）
- 日期：2026-09-23
- 范围：仅导入成功后「查看」的定位语义；**未运行 Gradle / ADB，未做真机验收**

---

## 0. 结论速览

| 项 | 状态 |
|---|---|
| 块 1「只定位本批」 | **已修**（新增本批成功 id 接缝，Route 只取该批） |
| 块 2「筛掉时不静默、不留过期 focus」 | **已修**（Screen 回执 `reached`，Route 清状态 + 提示） |
| 编译 / 测试 | **尚未运行**（并行纪律要求：写入期间不跑 Gradle，由集成者串行执行） |

---

## 1. 修复前的实际代码证据

问题在**两处**，各自独立，都属 S05-④。

### 证据 A：定位目标来自**跨批次**的持久集合

`ui/screen/shelf/ShelfRoute.kt:173`（修改前）：

```kotlin
if (result == SnackbarResult.ActionPerformed) {
    focusNewBookId = newBookIds.firstOrNull()
}
```

`newBookIds` 的来源是 `ShelfViewModel.newBookIds` → `ShelfPrefs.newBookIds`
（`data/settings/ShelfPrefs.kt:79`），语义是**跨批次的持久「未查看」集合**：

- 写入：`ShelfViewModel.kt:761` `importer.onBookImported = { bookId -> … addNewBookIds(listOf(bookId)) }`
- 移除：`clearNewBookId(id)`，**只有用户真正打开过那本书才会移除**（`ShelfPrefs.kt:112`）；
- 还有 `take(NEW_BOOK_IDS_MAX)` 上限淘汰。

⇒ 若上一批导入了一本书而用户始终没打开它，它的 id 会一直留在这个集合里。
下一批导入完成后点「查看」，`firstOrNull()` 可能返回**上一批那本书** ——
用户看到的是「刚导入的书找不到，反而跳到一本旧书」。

### 证据 B：目标被筛掉时静默返回，且 focus 残留

`ui/screen/shelf/ShelfScreen.kt:117-122`（修改前）：

```kotlin
LaunchedEffect(focusBookId, state.filtered.size, state.viewMode) {
    val target = focusBookId ?: return@LaunchedEffect
    val index = shelfGridIndexOfBook(state, target) ?: return@LaunchedEffect
    gridState.animateScrollToItem(index)
    onFocusHandled()
}
```

`onFocusHandled()` 只出现在**成功路径**上。当 `shelfGridIndexOfBook` 返回 `null`
（目标被当前搜索 / 状态 / 格式 / 书单 / 分类 / 标签筛选排除）时直接 `return`：

1. **没有任何反馈** —— 用户点了「查看」，界面毫无变化，无法判断是「卡了」还是「本来就没有」；
2. **focus 状态不清** —— `focusNewBookId` 仍持有那个 id，而 effect 的 key 含
   `state.filtered.size`，于是用户**之后任意一次筛选 / 导入 / 排序变化**只要改变了列表长度，
   这个陈旧请求就会突然生效，把列表滚到一本与本次导入无关的旧书上。

（`shelfGridIndexOfBook` 见 `ui/screen/shelf/ShelfModels.kt:166`，三处返回 `null` 的语义
在修复中被区分开，见 §3.2。）

### 当前工作树核对

两处缺陷在改动前**确实存在**（上文代码逐字取自修复前的工作树，非 HEAD 的旧快照）。
按任务要求「如果问题已修，不重复改」——已核对，**未修**，故实施修复。
未触碰 S05 其余条目（①③⑤⑥⑦）与 `agent-stage-b-core-audit.md` 中标记为「需先裁决」的项。

---

## 2. 精确改动文件与局部边界

| 文件 | 改动边界 |
|---|---|
| `feature/library/ShelfImporter.kt` | **仅本批成功 id 接缝**：新增 `_lastBatchSucceededBookIds` 私有流 + 公开 `lastBatchSucceededBookIds`；`runImportBatch` 开头清空、成功分支追加；`ImportOutcome.Success` 由 `data object` 改为 `data class Success(val bookId: String)`（私有 sealed interface，仅本文件使用）。**未改**导入、来源引用、哈希、`onBookImported` 回调语义 |
| `ui/screen/shelf/ShelfRoute.kt` | **仅导入后定位局部**：新增 `batchSucceededBookIds` 收集 + `rememberUpdatedState`；`focusNewBookId: String?` → `focusNewBookIds: List<String>`；snackbar 动作改为把本批 id 交给 Screen；`onFocusHandled` 改为带 `reached` 回执并在此处提示。新增 `rememberUpdatedState` import。**未改**其它任何弹层、Launcher、删除确认、分类学逻辑 |
| `ui/screen/shelf/ShelfScreen.kt` | **仅 `focusBookId`/`onFocusHandled` 两个参数 + 一个 LaunchedEffect + 文件末尾新增一个纯函数**。**未改**其它 UI 结构、网格、筛选条、卡片 |
| `ui/viewmodel/ShelfImporterTest.kt` | **仅追加 3 个用例**（接缝行为）。既有用例一行未改 |
| `ui/screen/shelf/ShelfImportFocusTargetTest.kt` | **新建**，纯 JVM，覆盖 `focusImportTarget` |

**未修改**：`ShelfUiModels.kt`、`ShelfViewModel.kt`、`ShelfModels.kt`、`ShelfPrefs.kt`、
`BookRepository`、任何数据库 / schema / 迁移 / 主题 / 翻页 / 滑杆文件，以及其它窗口的报告与任务账本。

> 接缝为何能落在授权文件内：`ShelfViewModel.kt:90` 已有 `val importer: ShelfImporter = shelfImporter`
> （public），因此 `ShelfRoute` 可直接读 `viewModel.importer.lastBatchSucceededBookIds`，
> **不需要改 ViewModel 或 `ShelfUiModels.kt`**。故本轮**无 SEAM REQUEST**。

---

## 3. 修复逻辑

### 3.1 只定位本批（`ShelfImporter` 接缝）

```kotlin
private val _lastBatchSucceededBookIds = MutableStateFlow<List<String>>(emptyList())
val lastBatchSucceededBookIds: StateFlow<List<String>> = _lastBatchSucceededBookIds.asStateFlow()
```

- **清空**：`runImportBatch` 开头 `_lastBatchSucceededBookIds.value = emptyList()`
  （`importFiles` 与 `importFolder` 都走这里，两条入口一并覆盖）；
- **追加**：与 `succeeded` 计数**同一个分支**（`is ImportOutcome.Success ->`），
  保证「计数」与「列表」不可能分叉：
  ```kotlin
  is ImportOutcome.Success -> {
      succeededBookIds += outcome.bookId
      _lastBatchSucceededBookIds.value = succeededBookIds.toList()
      state.copy(completed = …, succeeded = state.succeeded + 1)
  }
  ```
- 口径与 `ImportBatchUiState.succeeded` 一致：重复 / 跳过 / 失败**不计入**。

代价：`ImportOutcome.Success` 从 `data object` 变为 `data class`（多带一个 bookId）。
它是 `private sealed interface`，全仓 grep 确认只在 `ShelfImporter.kt` 内使用，
对外行为零变化。

### 3.2 筛掉时不静默、不留过期（`ShelfScreen` + `ShelfRoute`）

Screen 侧：

```kotlin
LaunchedEffect(focusBookIds, state.showSkeleton, state.filtered.size, state.viewMode) {
    if (focusBookIds.isEmpty()) return@LaunchedEffect
    if (state.showSkeleton) return@LaunchedEffect          // ← 见下「骨架期」
    val target = focusImportTarget(focusBookIds, state.filtered.map { it.bookId }.toSet())
    val index = target?.let { shelfGridIndexOfBook(state, it) }
    if (index == null) {
        onFocusHandled(false)                              // ← 清状态 + 通知，不再静默 return
        return@LaunchedEffect
    }
    gridState.animateScrollToItem(index)
    onFocusHandled(true)
}
```

三处关键决定：

1. **候选是列表不是单个 id**：本批可能导入多本，`focusImportTarget` 取**第一本当前可见的**。
   否则第一本恰好被筛掉时整批都定位不到（审计里点名的「优先取其中可见的一本」）。
2. **`showSkeleton` 必须进 key 且单独早退**：骨架期列表数据还没到，此时的「找不到」
   **不代表**被筛选隐藏。若不早退就直接判定，刚导入就点「查看」会收到错误的「被筛选排除」提示；
   若不把 `showSkeleton` 放进 key，等它翻转时 effect 不会重跑（`filtered.size` 可能始终是 0，
   不构成变化），focus 会被永久挂住 —— 反而制造出新的「过期请求」。
3. **`reached = false` 也清 focus**：这是本条的**核心**。旧代码正是靠「不清」把状态留了下来。

Route 侧：

```kotlin
onFocusHandled = { reached ->
    focusNewBookIds = emptyList()                 // 无论成败都清，杜绝过期请求
    if (!reached) showMessage("新导入的书不在当前筛选结果里，可调整筛选后再查看")
}
```

另加一处**竞态防护**：snackbar 的 `showSnackbar` 会**挂起等待用户点按**（可能数秒），
若直接读 effect 启动时捕获的 `batchSucceededBookIds`，可能读到「本批 id 还没发射」的空列表，
导致点了「查看」却什么都不发生 —— 正是要修的症状之一。故用
`rememberUpdatedState(batchSucceededBookIds)`，保证读的是**点按那一刻**的值。

**刻意未做**（避免越权扩大产品语义）：
- 未自动清除「会排除该书的筛选」。这类自动改用户筛选状态属于产品决定，
  本轮只做「清状态 + 明确告知」，把选择权留给用户。
  （审计原文给了两选项：「清除会排除它的筛选**或**提示原因」，本轮选后者。）

---

## 4. 测试设计

### 4.1 `ShelfImporterTest.kt`（+3 条，既有用例零改动）

| 用例 | 断言 | 能区分旧/新行为吗 |
|---|---|---|
| `batch succeeded ids contain exactly the newly imported books` | 导入 1 本后，`lastBatchSucceededBookIds` 与真实 `bookDao.upsert` 落库的 id 一致且长度 1 | 新接缝专有；旧实现根本没有「本批」概念 |
| `batch succeeded ids exclude duplicate entries in the same batch` | 同批同文件导两次（1 成功 + 1 重复）后，列表长度 == `batch.succeeded` 且只含成功那本 | 钉住「与 `succeeded` 同口径」，防止把重复也算成本批新书 |
| **`a new batch clears the previous batch succeeded ids`** | 批次 1 成功 → 列表 1 项；批次 2 全是重复（0 成功）→ 列表**必须为空** | **核心判别用例**：旧实现走全局 `newBookIds`，第二批判重后那本书**仍在**集合里，点「查看」会定位到它；新实现为空 |

第 3 条是这次修复的**行为分界**：它断言的是一个旧实现**不可能满足**的性质
（旧实现里跨批次集合不会因「本批无新书」而变空）。

### 4.2 `ShelfImportFocusTargetTest.kt`（新建，6 条纯 JVM 用例）

覆盖 `focusImportTarget(candidates, visibleBookIds)`：

1. 取第一个可见候选；
2. 第一本被筛掉 → 退到第二本（不是整批失败）；
3. 候选全被筛掉 → `null`（调用方据此清 focus + 告知）；
4. 本批无新书（空候选）→ `null`；
5. 没有任何可见书 → `null`；
6. **回归护栏**：`candidates=[thisBatch]`、`visible=[staleFromPreviousBatch]` → `null`；
   两者都可见时 → 返回 `thisBatch`。若有人把实现改回「从全局可见集合里挑」，
   这条会红 —— 它直接编码了「不得误用以前批次 ID」。

### 4.3 为何不给 Screen/Route 加 Compose 测试

`ShelfScreen` 的定位逻辑依赖 `LazyGridState` 的实际滚动，属 androidTest（需真机）。
本轮按并行纪律**不跑设备**；把其中**可判定**的那一步（候选 × 可见集合 → 目标）
抽成 `focusImportTarget` 纯函数，取得同等判别力而不引入设备依赖。
`androidTest/ShelfScreenComposeTest.kt` 的 5 处 `ShelfScreen(state = …, onAction = …)`
调用只用到默认参数，**签名变更不影响其编译**（已 grep 确认无 `focusBookId` / `onFocusHandled` 引用）。

---

## 5. 静态检查结果（未运行 Gradle）

已做的**只读静态核对**：

| 检查 | 结果 |
|---|---|
| 重命名残留 | `grep -rn "focusNewBookId\b"` / `"focusBookId\b"` 于 `android/app/src` → **0 命中** |
| 新符号闭环 | `focusBookIds` 仅出现在 ShelfRoute（传入）与 ShelfScreen（接收）；`focusImportTarget` 仅定义处 + 新测试引用 |
| 接缝可见性 | `ShelfViewModel.kt:90` `val importer` 为 public → Route 可直达，无需改 ViewModel |
| `ImportOutcome` 影响面 | 全仓 grep：除 `ShelfImporter.kt` 外仅 2 处 KDoc 文本提及（`SearchIndexRepository.kt:644`、新测试注释），**无代码引用** |
| androidTest 影响面 | `grep` `focusBookId\|onFocusHandled\|focusImportTarget` 于 `androidTest` → **0 命中**；`ShelfScreenComposeTest` 只传 `state`/`onAction`，默认参数兜住 |
| 关键符号存在性 | `bookDao.upsert`（TXT 导入路径 `ShelfImporter.kt:859`）确认被调用 → 用例中的 `capture` 能取到落库书；`ShelfBookItem.bookId`（`ShelfModels.kt:167` 已在用）；`mockk.capture` 是 `MockKMatcherScope` 成员，既有用例已在同种位置使用 |
| 未用变量 | `newBookIds` 在 Route 中仍被 `ShelfUiState` 构造使用，不会产生 unused 警告 |
| 边界外文件 | 本轮 `git status` 中我只落 5 个文件（见 §2），其它条目属并行窗口 |

**尚未运行**：Kotlin 编译、KSP（Room/Hilt）、`testDebugUnitTest`、`lintDebug`、
androidTest 编译。**因此本文所有测试结论均为「设计意图」，不是执行结果。**

---

## 6. 尚未运行的验证与剩余风险

1. **全部验证未执行**。按并行纪律（任务书第 2 条）写入期间不跑 Gradle；
   编译、JVM 定向测试、Lint、androidTest 编译、Debug APK 由**集成者串行**执行。
   集成者需要跑：
   `:app:testDebugUnitTest --tests "…ui.viewmodel.ShelfImporterTest"`、
   `--tests "…ui.screen.shelf.ShelfImportFocusTargetTest"`、`:app:compileDebugKotlin`、`:app:lintDebug`。
2. **`focusImportTarget` 的命名参数写法未编译验证**（`candidates = …` / `visibleBookIds = …`），
   签名与调用点靠人工比对。
3. **`ImportOutcome.Success` 由 object 改 data class** 属结构性改动：
   静态核对未发现外部引用，但**未编译验证**。
4. **「新导入书尚未出现在列表流」的瞬态未处理**。若用户在书进入 `filtered` 之前就点「查看」，
   会被判为「不在当前筛选结果里」并给出提示（而非滚动）。考虑到导入落库先于批次结束、
   点按又需数百毫秒，实际概率低，但**未在真机验证**。
   备选加固（本轮**未做**，避免超范围）：在 Screen 侧做有界重试后再判定为不可达。
5. **「本批多本、部分可见」的真机手感未验证**：只验证了纯函数选中的是「第一本可见的」，
   未验证滚动落点与视觉锚点（S05-⑤ 的「无高亮锚点」不在本轮范围）。
6. **未做变异测试**：未实际把实现改回旧写法跑一遍确认新用例会红；
   §4 的「能区分旧/新行为」是**基于断言的静态判断**。
7. **未覆盖**：S05-①③⑤⑥⑦、S04 哈希、S06 启动镜像 / 目录可见选择 / 旧 READABLE 兼容
   —— 均按任务要求排除。

---

## 7. 边界声明

- 只在恢复仓库工作；**未**进入或修改 `D:\develop\Code\Codex\creation-reading-assistant`。
- **未** reset / checkout / clean / stash / stage / commit / push；**未**全仓格式化。
- **未**运行 Gradle、ADB、真机或 MuMu；**未**接触用户书库数据。
- 现有未提交改动（含其它窗口）一律保留，本轮只改自己明确拥有的局部。
- 无 SEAM REQUEST（接缝落在授权文件内，理由见 §2 注）。

完成后停止，等总指挥复核。

---

# 复核后补强（2026-09-23，第二轮）

> 本节**取代**前文以下描述，其余部分仍然有效：
> - §2 表格里 `ShelfImporter.kt` 一行的「新增 `lastBatchSucceededBookIds`」→ 已换为批次绑定的
>   `batchSuccesses`（见 §8.1）；
> - §3.1 的接缝 API、§4.1 与 §4.2 的测试清单与条数 → 已按本节更新
>   （`ShelfImportFocusTargetTest` 由 6 条增至 15 条；`ShelfImporterTest` 仍为 3 条，其中 1 条升级）；
> - 前文提到的 `rememberUpdatedState(batchSucceededBookIds)` 方案 → **已整体移除**（见 §8.2）。

复核指出的三个时序缺口都已确认成立，逐条补强如下。**仍未运行任何 Gradle / ADB / 真机。**

## 8.1 缺口一：定位目标必须与「提示对应的批次」一致

**复核意见**：`latestBatchSucceededBookIds` 只是 Compose 最近一次已收集的值，
不能保证与 `importBatch.succeeded > 0` 的批次结果原子同步；批次变化时可能误用新批次的 ID。

**确认成立**。原实现有两层异步：`collectAsStateWithLifecycle` 的收集协程、
以及 recomposition 的批量应用。二者都不提供「这个 id 列表属于这条提示」的保证。

**补强**：把「批次 id」与「成功 id 列表」绑成**一个不可变对象**，一次赋值发布：

```kotlin
// feature/library/ShelfImporter.kt（顶层类型）
data class ImportBatchSuccesses(
    val batchId: String = "",
    val bookIds: List<String> = emptyList(),
)

private val _batchSuccesses = MutableStateFlow(ImportBatchSuccesses())
val batchSuccesses: StateFlow<ImportBatchSuccesses> = _batchSuccesses.asStateFlow()
```

- 批次开始：`_batchSuccesses.value = ImportBatchSuccesses(batchId = batchId)`（整体重置）
- 每本成功：`_batchSuccesses.value = ImportBatchSuccesses(batchId, succeededBookIds.toList())`
  ——与 `succeeded` 计数同一分支

Route 侧**不再收集这个流**，改为在点按那一刻同步读 `.value` 并按批次核对：

```kotlin
// ui/screen/shelf/ShelfRoute.kt
val successes = viewModel.importer.batchSuccesses.value   // 同步读，无收集延迟
val ids = focusIdsForBatch(successes, importBatch.id)
focusNewBookIds = ids
if (ids.isEmpty()) { /* 明确反馈，见 §8.3 */ }
```

```kotlin
// ui/screen/shelf/ShelfRoute.kt（顶层纯函数）
internal fun focusIdsForBatch(
    successes: ImportBatchSuccesses,
    batchId: String,
): List<String> = if (successes.batchId == batchId) successes.bookIds else emptyList()
```

「不得定位新批次的书」落在 `batchId` 相等这一点上：批次已换代 ⇒ 返回空 ⇒ 不定位，
且给出提示（§8.3）。`rememberUpdatedState(batchSucceededBookIds)` 已从 Route 整体删除。

## 8.2 缺口二：成功数 > 0 但 ID 为空时不得静默结束

**确认成立**。原实现 `focusNewBookIds = latestBatchSucceededBookIds` 若拿到空列表，
Screen 会因 `focusBookIds.isEmpty()` 直接 `return` —— 用户点了「查看」毫无反应。

**补强**：Route 在 `ids.isEmpty()` 时**立即给出明确反馈**，并按原因区分文案：

| 情况 | 提示 |
|---|---|
| `successes.batchId != importBatch.id`（批次已换代） | 「这一批已不是最近一次导入，请在书架上按「新」角标查找」 |
| 批次一致但列表异常为空 | 「暂时定位不到这一批新书，请在书架上按「新」角标查找」 |

两种情况都保持 `focusNewBookIds` 为空（请求已清理），不存在残留。

## 8.3 缺口三：列表流未显示时不得立即断言「被筛选排除」

**确认成立**。导入写库与书架列表流（Room → Flow → Compose）之间有延迟，
原实现在 `filtered` 尚未包含新书时就判定不可达，属误报。

**补强**：有界等待。新增纯函数（`ui/screen/shelf/ShelfScreen.kt` 顶层）：

```kotlin
internal const val FOCUS_VISIBILITY_ATTEMPTS = 12
internal const val FOCUS_VISIBILITY_RETRY_MS = 50L   // 12 × 50ms ≈ 600ms 上界

internal suspend fun awaitFocusTarget(
    candidates: List<String>,
    visibleBookIds: () -> Set<String>,               // 每次轮询重新读，不吃快照
    maxAttempts: Int = FOCUS_VISIBILITY_ATTEMPTS,
    retryDelayMillis: Long = FOCUS_VISIBILITY_RETRY_MS,
): String? {
    if (candidates.isEmpty()) return null
    repeat(maxAttempts) { attempt ->
        focusImportTarget(candidates, visibleBookIds())?.let { return it }
        if (attempt < maxAttempts - 1) delay(retryDelayMillis)
    }
    return null
}
```

配套两处必须一起改，否则等待是假等待：

1. **effect 体读 `currentState`（`rememberUpdatedState(state)`）而非捕获的 `state`**。
   `LaunchedEffect` 体在 `delay` 中挂起时，组合期捕获的 `state` 是旧快照 —— 拿它轮询
   永远读不到新数据，会把「还没进列表」误判成「被筛掉」，等于没修。
2. **effect 的 key 里去掉 `state.filtered.size`**。带它时每来一次数据就重启 effect、
   把轮询计数清零，极端情况下能一直重启，等于变相无限等待；去掉后单次运行的计数严格单调，
   上界是硬的。新数据由 50ms 一次的轮询接住（不需要靠重启刷新）。
   `showSkeleton` 保留在 key 里：骨架期这一轮直接返回、不判定也不清理，需要它翻转来触发重跑。

## 8.4 新增/调整的测试

`ui/screen/shelf/ShelfImportFocusTargetTest.kt`（纯 JVM，6 → **15** 条）：

| 组 | 用例 | 钉住的性质 |
|---|---|---|
| `focusImportTarget`（原有 6 条） | 取第一本可见、跳过被筛的、全不可见返回 null、空候选、无可见、**不得选中候选外的书** | 只认本批 + 必须可见 |
| `focusIdsForBatch`（新增 4 条） | 批次一致返回 id；**批次已换代返回空**；批次一致但列表空返回空；从未跑过批次返回空 | §8.1 / §8.2 的判别核心 |
| `awaitFocusTarget`（新增 5 条） | 第 3 次轮询才出现 → 等到并返回；**永远不出现 → 恰好轮询 `maxAttempts` 次后返回 null**；已可见只轮询 1 次；空候选 0 次轮询；**每次重新读可见集合而非缓存快照** | §8.3「有界、不挂起、不误报」 |

其中「恰好 4 次后返回 null」是**可证伪的防挂起护栏**：若实现改成不限次数，
`runTest` 的虚拟时间会一直推进、计数断言必不成立。

`ui/viewmodel/ShelfImporterTest.kt`（保持 3 条，全部改用新 API，其中 1 条升级）：

| 用例 | 钉住的性质 |
|---|---|
| `batch succeeded ids contain exactly the newly imported books` | 列表 == 真实落库书 id、长度 == `succeeded` |
| **`batch successes are tagged with the batch id they belong to`**（**由上一轮的 `a new batch clears the previous batch succeeded ids` 升级而来**） | 批次 id 与成功 id **一起**换代；第二批全重复时 bookIds 必须为空。比原用例多钉住「批次 id 同步」这一面 |
| `batch succeeded ids exclude duplicate entries in the same batch` | 同批判重不计入 |

## 8.5 本节未执行的验证

**完全没有运行**：Kotlin 编译、KSP（Room / Hilt）、`testDebugUnitTest`、`lintDebug`、
androidTest 编译、Debug APK。按并行纪律（C 仍在开发、共享构建目录未交付集成）本轮不碰 Gradle。
本节所有结论均为**代码与测试设计**，不是执行结果。集成者需串行执行：

```
:app:testDebugUnitTest --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest"
:app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.shelf.ShelfImportFocusTargetTest"
:app:compileDebugKotlin
:app:lintDebug
```

已做的**只读静态核对**：`grep` 确认 `lastBatchSucceededBookIds` 全仓 0 残留、
`rememberUpdatedState` 已从 `ShelfRoute.kt` 移除、`ImportBatchSuccesses` / `focusIdsForBatch` /
`awaitFocusTarget` 的定义与调用点一一对应、import 无重复。

## 8.6 本节剩余风险

1. **全部未编译**：`ImportBatchSuccesses` 的新类型、`focusIdsForBatch` 的命名参数调用、
   `awaitFocusTarget` 的 `repeat` + `return` 非局部返回写法，均**未经编译器验证**。
2. **`delay` 在 `LaunchedEffect` 中的真实行为未实测**：JVM 测试走 `runTest` 虚拟时间，
   真机上 50ms × 12 的实际观感（以及减少动画设置下的表现）未验证。
3. **600ms 上界是拍的**：若某些设备上「导入落库 → 书架流发射」超过 600ms，
   仍会误报一次。该阈值**未在任何真机上测过**。
4. **去掉 `filtered.size` 这个 key 之后**，列表在等待期间变化不会让 effect 提前重启，
   最坏多等约 50ms（一轮轮询间隔）；这个取舍未做真机手感验证。
5. **`onFocusHandled(false)` 与用户可见提示之间的先后**未验证：两条 snackbar
   （完成提示已消失、新提示出现）在真机上的叠加观感未测。
6. **未做变异测试**：未把实现改回旧写法确认新用例会红；判别力是静态判断。
7. **本节未触碰**来源更新、哈希、数据库、schema、迁移，也未改 S05 其他条目。

## 8.7 HEAD 更正说明（含我方上一轮复核失误的归因）

- **真实 HEAD（本轮重新执行只读 `git rev-parse HEAD` 取得，40 位）**：
  `5aed5939600e491843e3c44453921bd6273a233c`
- **原误记（42 位，错）**：`5aed5939600e491843e3c3c44453921bd6273a233c`
  —— 在 `…1843e3c` 之后多出了一段 `3c`。第 5 行与该串是同一处错误，已一并更正。

**上一轮为什么没查出来（如实归因，不抹去差错）**：上一轮的「逐字符复核」是**循环论证**。
我把一个**自己手打的 42 位字符串**当作「真值」写进校验脚本，再拿报告去和它比 ——
两边都是同一个我打错的串，当然相等，于是得出「完全一致（42/42）」。
校验基准本应是 `git rev-parse HEAD` 的**实际输出**，而不是我复述的字符串。
本轮改为直接取命令输出并测量长度，才发现真实值是 40 位。

**本轮更正方式与旁证**：重新执行只读 `git rev-parse HEAD`，得到
`5aed5939600e491843e3c44453921bd6273a233c`（40 位），据此更正第 5 行与本节。
另对 `docs/` 全量扫描比对：以 `5aed…` 开头的 40 位串出现在**其余大量文档**中，
**只有本报告这一份是 42 位错串** —— 旁证正确值就是 40 位那一串。
（更正范围仅限本报告的这两处，未改其他文档的哈希。）

全文其余十六进制串扫描结果只有路径/日期（`20260919` / `20260920`），无其他哈希。

## 8.8 边界声明（本节）

- 改动文件仍限于**已授权**的 `ShelfImporter.kt`、`ShelfRoute.kt`、`ShelfScreen.kt`
  与对应测试 `ShelfImporterTest.kt`、`ShelfImportFocusTargetTest.kt`，外加本报告。
- **未** reset / checkout / clean / stash / stage / commit / push；**未**运行 Gradle / ADB / 真机；
  既有未提交改动（含其它窗口与 C 的开发中改动）全部保留。
- 无 SEAM REQUEST。

完成后停止，等总指挥再次复核。
