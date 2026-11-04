# 阶段 E — M-BUG-03「来源更新确认」候选边界调查（只读，2026-09-29）

> **窗口**：M-BUG-03「来源更新确认」候选边界调查（只读）。
> **工作仓库（只读）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`。
> **HEAD 已核对 = `513891d6a66a9b78e072ba759d34ed580d377ecf`**；`origin/main` 仍为 `5aed5939600e491843e3c44453921bd6273a233c`；暂存区空。
> **与窗口 1 的边界**：窗口 1 正独占 **B1 + B2 + 库浏览器**联合候选。本报告**只读**，**未**修改其文件，**未**触碰其 E 盘目录（`stage-e-b1-isolated-20260929`、`stage-e-b2-cache-isolated-20260929` 等一律未读未写）。§九 登记了 M-BUG-03 与窗口 1 的**文件级共享**，任何后续实现必须与窗口 1 串行。
> **输入**：[AGENTS.md](../../../../AGENTS.md)、[当前交接](../../../handoff/current.md)、[HOLD 16 行逐项裁决](../../legacy-stages/2026-09/2026-09-29-stage-e-hold-16-adjudication.md)、[Stage-E UI HOLD 逐 hunk 报告](../../legacy-stages/2026-09/2026-09-29-stage-e-hold-ui.md)、[数据/同步 HOLD 判定](../../legacy-stages/2026-09/2026-09-29-stage-e-hold-data-sync.md)、[集成决策材料](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md)、`E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-full-coverage-matrix.csv`（376 行）、`E:\creation-reading-assistant\stage-e-incremental-20260929\hold-180-row-mapping-updated.csv`（180 行）。
> **纪律**：共享仓库内**唯一写入 = 本文件**。**未**改源码 / 测试 / CSV / 公共配置 / 其他报告；**未**构建、**未**操作设备、**未** stage / commit / push / reset / checkout、**未**迁目录、**未**触碰原始仓库。
> **结论性质**：以下全部为**静态可复核结论**（`git diff` / `git show` / `git grep` / 全仓 ripgrep 实测）。**不宣称任何候选已可编译**——本窗口未运行任何 Gradle 命令。

---

## 一、结论摘要

| 项 | 值 |
|---|---|
| M-BUG-03 域文件 | **9 个生产文件 + 5 个测试文件**（其中 2 个生产/测试文件为**未跟踪新文件**） |
| 归 M-BUG-03 的 hunk | **35 个**（分布在 8 个已跟踪文件中；另有 2 个整文件新增） |
| 其中**混入他批、`git add -p` 无法拆分**的 hunk | **8 个**（ShelfImporter×2、ShelfRoute×2、ShelfUiState×1、BookGrid×2、ShelfScreen×1） |
| 另有**跨 hunk 联动**（同一次方法重写被切成 4 个 `-U0` hunk） | **1 处**（`ShelfViewModel.refresh()`，hunk 13–16，必须同取） |
| 测试文件内**连续新增块**需手工拆 | **2 处**（ShelfImporterTest 的 `+500,448` 块、ShelfViewModelTest 的 `+439,228` 块） |
| 需手工处理的处数合计 | **11 处**（§四 A-1..A-9 + B-1/B-2） |
| M-BUG-03 测试总数 | **34 条**（6 新 + 4 + 5 + 5 + 14） |
| 未跟踪新文件在矩阵中的状态 | **2 个核心新文件在 376 行总矩阵中标 `EXCLUDED`** ⇒ 在 **180 行 HOLD CSV 中完全没有行**（无 `matrix_id` / 无 `mapping` / 无 `rule_provenance`） |
| 矩阵归属错误 | `ShelfSourceRefreshFeedbackTest.kt`（H180）被标 **B2**，实测依赖 **M-BUG-03**（ShelfViewModel 的汇总文案纯函数） |
| 与窗口 1 的关系 | **7 个文件与 B1/B2 候选物理同文件**（§九 SR-7）⇒ 写入必须串行 |
| 最小编译闭包 | §六（9 生产 + 5 测试），**未验证** |

**一句话结论**：M-BUG-03 是一条**横跨 8 个已跟踪文件 + 2 个未跟踪新文件**、且在 **8 个 hunk 与 2 个测试块上与 B1 / S04 / S05 / B6 / NBB / M-BUG-09 物理交织**的功能链；它的**生产符号被 B1 与 S04 的测试反向依赖**（`ShelfImporter.refreshFromSource`），因此**不可能**作为「只取自己那几行」的孤立补丁落地——必须先与 B1 窗口协商 `ShelfImporter.kt` / `ShelfImporterTest.kt` 的切分口径。

---

## 二、输入与基线核对

| 项 | 实测 | 结论 |
|---|---|---|
| HEAD | `513891d6a66a9b78e072ba759d34ed580d377ecf` | 与任务给定一致 |
| 分支 | `codex/recover-worktree-20260920` | — |
| `origin/main` | `5aed5939600e491843e3c44453921bd6273a233c` | 未变 |
| 暂存区 | 空 | 未变 |
| 376 行总矩阵 | 可读，376 行，列 `file_path,status,group,sub_group,hunk_detail,reason,lines_changed`；`group` 分布：HOLD 180 / G8 103 / EXCLUDED 56 / G4 9 / G5 7 / G6 6 / G0 5 / G2 4 / G3 3 / G7 3 | — |
| 180 行 HOLD CSV | 可读，180 行；`mapping=保留 HOLD` 122 行 | — |

**工作树变更与矩阵的覆盖差**：`android/**` 工作树变更 **366** 项，其中**只有 1 项**不在 376 行总矩阵内——`feature/log/AppLog.kt`（与 M-BUG-03 无关，仅登记）。⇒ **M-BUG-03 域文件全部在 376 行总矩阵内有行**；问题不在「缺行」，而在**行的 `group` 标错**（§五）。

**方法学提醒（本轮实测发现，影响一切「消费者穷举」结论）**：`git grep` **只搜索已跟踪文件**。`ShelfSourceUpdateChecker.kt` 是未跟踪新文件，`git grep -n 'sourceDocumentUriOf' -- android` **不会**命中它（实测：只返回 `LibrarySourceIndex.kt` 定义与 `ShelfImporter.kt` 调用）。凡以 `git grep` 得出的「消费者集合」都会**系统性漏掉未跟踪新文件**。本报告的全部消费者结论改用**全仓 ripgrep（含未跟踪文件）**复核。

---

## 三、M-BUG-03 域文件清单与逐 hunk 归属

判定口径：**只看该 hunk 引入/消费的符号归属**，不按文件名或目录前缀归类。hunk 编号与行号一律用 `git diff -U0`（`-U0` 下每个 hunk 就是一个**极大连续变更块**；块内多特征 ⇒ `git add -p` 不可拆，见 §四）。

### 3.1 `feature/library/LibrarySourceIndex.kt`（` M`，`+66/-0`，2 hunk；**全部属 M-BUG-03**）

| # | new 行 | 内容 | 归属 |
|---|---|---|---|
| 1 | `3` | `+import android.content.ContentResolver` | **M-BUG-03** |
| 2 | `48..112` | `+sourceDocumentUriOf(...)`（`:61`）、`+querySourceMetadata(...)`（`:84`）、`+SOURCE_METADATA_PROJECTION`（`:108`） | **M-BUG-03** |

**消费者穷举（ripgrep，含未跟踪文件）**：

| 符号 | 消费者 | 是否全部属 M-BUG-03 |
|---|---|---|
| `sourceDocumentUriOf` | `ShelfSourceUpdateChecker.kt:85`（新）、`ShelfImporter.kt:279`（M-BUG-03） | ✅ 是 |
| `querySourceMetadata` | `ShelfSourceUpdateChecker.kt:86`（新）、`ShelfImporter.kt:354`（M-BUG-03） | ✅ 是 |

**HEAD 基线**：`git grep HEAD 'sourceDocumentUriOf\|querySourceMetadata'` → **0 命中**（确为新符号）。`LibrarySourceIndex` 在 HEAD 已是 `@Singleton class … @Inject constructor` ⇒ **无需任何 Hilt module 改动**。`snapshot()` / `markObserved()` 在 HEAD 已存在（`git show HEAD:…:135/148`）。

### 3.2 `feature/library/ShelfSourceUpdateChecker.kt`（`??` 未跟踪新文件，93 行；**整文件属 M-BUG-03**）

| 内容 | 行 | 说明 |
|---|---|---|
| `isSourceContentUpdated(baselineSize, baselineLastModified, observedSize, observedLastModified)` | `:23` | 顶层 `internal` 纯函数，无 Android 依赖 |
| `@Singleton class ShelfSourceUpdateChecker @Inject constructor(@ApplicationContext context, rootStore: LibraryRootStore)` | `:53-57` | 构造注入；`LibraryRootStore` 在 HEAD 已存在 |
| `suspend fun checkUpdated(refs: List<LibrarySourceRef>): Set<String>` | `:65` | 逐本串行 stat；调 `sourceDocumentUriOf` / `querySourceMetadata` / `isSourceContentUpdated` |

**消费者穷举**：`ShelfSourceUpdateChecker` 作为类型只被 `ShelfViewModel.kt:24`（import）与 `:160`（构造参数）引用，外加 `ShelfViewModelTest.kt:21/142/492/519/552/591/642`。**无 Hilt module 注册**（构造注入）。`isSourceContentUpdated` 只被本文件 `:87` 与其测试引用。

### 3.3 `feature/library/ShelfImporter.kt`（` M`，`+387/-24`，31 hunk；**M-BUG-03 = 9 个**）

| # | new 行 | 内容 | 归属 | 说明 |
|---|---|---|---|---|
| **1** | `6` | `+import android.provider.DocumentsContract` | **M-BUG-03** | 唯一使用点 `:277`（`getTreeDocumentId`，在 `refreshFromSource` 内） |
| 2 | `18` | `+import SearchIndexBookRefreshCoordinator` | **B1** | 排除 |
| **3** | `30` | `+import kotlinx.coroutines.CancellationException` | **M-BUG-03** | 唯一使用点 `:264/:271/:327/:400`，**四处全在 M-BUG-03 代码内** |
| **4** | `41..76` | `SourceRefreshResult`（`41-59`）+ `ImportBatchSuccesses`（`61-75`） | **MIXED**：M-BUG-03 + S05-④ | 手工拆点 `L60` |
| 5 | `96` | `+private val indexRefreshCoordinator: SearchIndexBookRefreshCoordinator` | **B1** | 排除 |
| 6 | `99` | `+var onBookImported: (String) -> Unit` | NBB / S05-④ | 排除 |
| 7 | `106..114` | `_batchSuccesses` | S05-④ | 排除 |
| 8 | `121..138` | `batchSuccesses` | S05-④ | 排除 |
| **9** | `236..296` | `refreshFromSource`（`236-283`）+ **B1 的 `indexRefreshCoordinator.request(bookId)`（`284-287`）** + `return`（`288`） | **MIXED**：M-BUG-03 + **B1** | 手工拆点：**删除 `L284..287`** |
| **10** | `298` | `repairFileTyped(bookId, uri): RepairOutcome` 签名 | **M-BUG-03** | `repairFile` 改为 `= repairFileTyped(...).message` |
| **11** | `326..330` | `RepairOutcome(true, …)` + `catch (e: CancellationException)` | **M-BUG-03** | |
| **12** | `337..405` | `repairFileTyped` 尾部 + `refreshSourceBaseline` | **M-BUG-03 / S04-3/4 共享** | 同一方法的两个面，**不可再分** |
| **13** | `409..494` | `refreshSourceBaseline` 尾部 + `findInternalFile` / `computeFileHash` / `computeCandidateFingerprint` | **M-BUG-03 / S04-3 共享** | 同上 |
| 14–21 | `514..527`、`539`、`543`、`545..554`、`573..575`、`581`、`596..601`、`699..700` | 批处理进度节流 / `ImportOutcome.Success(bookId)` / `onBookImported` | S05-④ / NBB | 排除 |
| 22–28、30–31 | `954..977`、`984..989`、`995`、`1007..1011`、`1020`、`1043..1054`、`1056..1057`、`1188..1189`、`1196..1198` | 崩溃残留守卫、替换回滚阶段分流、`books.content_hash` 同步、`BATCH_EMIT_THROTTLE_MS` | **S04 / S04-3 / S05-④** | 排除 |
| **29** | `1168..1170` | `private data class RepairOutcome(ok, message)` | **M-BUG-03** | |

**HEAD 基线**：`git grep HEAD 'refreshFromSource\|SourceRefreshResult\|repairFileTyped\|RepairOutcome'` → **0 命中**。HEAD 的 `ShelfImporter` 构造参数**已含** `sourceIndex: LibrarySourceIndex` 与 `rootStore: LibraryRootStore`（R3 落地）⇒ M-BUG-03 **不需要**新增构造参数（新增的两个参数分属 B1 与 NBB）。

### 3.4 `ui/viewmodel/ShelfViewModel.kt`（` M`，`+316/-9`，19 hunk；**M-BUG-03 = 10 个**）

| # | new 行 | 内容 | 归属 |
|---|---|---|---|
| **1** | `22` | `+import LibrarySourceIndex` | **M-BUG-03** |
| **2** | `24..25` | `+import ShelfSourceUpdateChecker`、`+import SourceRefreshResult` | **M-BUG-03** |
| 3 | `56` | `+import kotlinx.coroutines.flow.onEach` | M-BUG-09（使用点 `:324`） |
| **4** | `59` | `+import kotlinx.coroutines.Job` | **M-BUG-03**（唯一使用点 `:795 sourceUpdateJob: Job?`） |
| **5** | `64..137` | 来源刷新汇总文案：`SOURCE_REFRESH_SUMMARY_MAX_TITLES` / `…_REASON_MAX_CHARS` / `…_TITLE_MAX_CHARS` / `…_RESIDUE_MARKER` / `SourceRefreshFailureUi` / `truncateForSummary` / `summarizeSourceRefreshFailureReason`（`:98`）/ `buildSourceRefreshSummary`（`:117`） | **M-BUG-03 / S04-5 共享**（同一「来源刷新」域，无他批交织） |
| **6** | `159..160` | `+private val sourceIndex`、`+private val sourceUpdateChecker` | **M-BUG-03** |
| 7 | `183..210` | `init { shelfPrefs.sortMode.collect … }` | M-FEAT-01 |
| 8 | `308..322` | `libraryLoaded` KDoc | M-BUG-09 |
| 9 | `324` | `.onEach { _libraryLoaded.value = true }` | M-BUG-09 |
| 10 | `427..430` | `books` 流 `SharingStarted.Eagerly` | M-BUG-09 |
| 11 | `439..449` | `newBookIds` | NBB |
| 12 | `546..565` | `markFinished` / `markReading` / 批量 | S05 |
| **13** | `677..691` | `-fun refresh() {` → `+_pendingSourceRefreshIds` / `+pendingSourceRefreshIds` / `+KDoc` / `+fun refresh(onResult: (String) -> Unit = {}) {` | **M-BUG-03 / S04-1** |
| **14** | `695..700` | `refresh()` 体：`-runCatching { … }` → `+try { +runCatching { … }` | **M-BUG-03 / S04-1** |
| **15** | `702..775` | `refresh()` 尾（`+delay(350)` / `+checkUpdatedSources(onResult)` / `+} finally { _isRefreshing.value = false }`）+ `checkUpdatedSources`（`:713`）+ `confirmSourceRefresh`（`:754`，至 `:775` 止） | **M-BUG-03 / S04-1** |
| **16** | `777..827` | `-delay(350)` / `-_isRefreshing.value = false`（旧尾）→ `+}` / `+refreshSourceUpdates()` / `+onResult(buildSourceRefreshSummary(…))` / `+}`；再接 `cancelSourceRefresh`（`:785`）+ `_contentUpdatedBookIds` / `contentUpdatedBookIds`（`:793-794`）+ `sourceUpdateJob`（`:795`）+ `refreshSourceUpdates()`（`:806`） | **M-BUG-03** |
| 17 | `833..835` | `importer.onBookImported = …` | NBB |
| 18 | `844..846` | `ShelfSortMode.IMPORTED` 兜底 | M-FEAT-01 |
| **19** | `853..856` | `init` 内 `refreshSourceUpdates()` | **M-BUG-03** |

**HEAD 基线**：`ShelfViewModel` 在 HEAD **没有** `indexRefreshCoordinator` 构造参数（工作树也没有）⇒ ShelfViewModel **不依赖 B1**；M-BUG-03 新增的两个构造参数是 `sourceIndex`（`:159`）与 `sourceUpdateChecker`（`:160`），二者均为 M-BUG-03。

### 3.5 `ui/screen/shelf/ShelfRoute.kt`（` M`，`+190/-10`，24 hunk；**M-BUG-03 = 7 个**）

| # | new 行 | 内容 | 归属 | 手工拆点 |
|---|---|---|---|---|
| 1 | `7..8` | `+import navigationBarsPadding`（general，用于 `:396`）、`+import SnackbarDuration`（NBB，用于 `:222`） | general + NBB | — |
| 2 | `11` | `+import SnackbarResult` | NBB | — |
| 3 | `29` | `+import ImportBatchSuccesses` | S05-④ | — |
| 4 | `(删除 import delay)` | M-BUG-09 | — |
| **5** | `41..85` | `focusIdsForBatch`（`41-56`，S05-④）+ `SOURCE_REFRESH_PROGRESS_NOTE`（`77`）+ `sourceRefreshConfirmBody`（`84-85`，M-BUG-03） | **MIXED**：S05-④ + M-BUG-03（+ M-BUG-09 的 `-SkeletonMinDisplayMillis`） | **M-BUG-03 = `L58..85`** |
| 6 | `156..160` | `libraryLoaded` 取代 `firstLoad` | M-BUG-09 | — |
| 7 | `194..204` | `newBookIds` collect | NBB | — |
| 8 | `214..245` | 「查看」snackbar（`focusIdsForBatch` + `focusNewBookIds`） | S05-④ + NBB（**无 M-BUG-03**） | — |
| **9** | `257..262` | `contentUpdatedBookIds` + `pendingSourceRefreshIds` collect | **M-BUG-03** | 整块 |
| **10** | `270..271` | `contentUpdatedBookIds = …`（`270`，M-BUG-03）+ `newBookIds = …`（`271`，NBB） | **MIXED** | **M-BUG-03 = `L270`** |
| 11 | `286` | `showSkeleton = !libraryLoaded` | M-BUG-09 | — |
| **12** | `296` | `confirmSourceRefreshIds = pendingSourceRefreshIds,` | **M-BUG-03** | 整行 |
| 13 | `311..319` | `focusBookIds = focusNewBookIds` | NBB | — |
| 14 | `324..326` | `viewModel.clearNewBookId(...)` | NBB | — |
| 15 | `396..397` | `.navigationBarsPadding()` | general | — |
| 16 | `430..433` | `onMarkFinished` / `onMarkReading` | S05 | — |
| **17** | `660..690` | 来源更新确认 `GlassAlertDialog`（`sourceRefreshConfirmBody` 调用点 `:672`、`confirmSourceRefresh` `:678`、`cancelSourceRefresh` `:668/:685`） | **M-BUG-03** | 整块 |
| 18 | `775..777` | `clearNewBookId` | NBB | — |
| 19 | `788` | `clearNewBookId` | NBB | — |
| 20 | `800..813` | `MarkFinished` 分支 | S05 | — |
| 21 | `824..833` | `BatchMarkFinished` 分支 | S05 | — |
| 22 | `882` | `clearNewBookId` | NBB | — |
| **23** | `909..910` | `CancelSourceRefreshConfirm` / `ConfirmSourceRefresh` 分支 | **M-BUG-03** | 整块 |
| **24** | `914..916` | `TriggerRefresh -> viewModel.refresh { showMessage(it) }` | **M-BUG-03** | 整块 |

**HEAD 基线**：`GlassAlertDialog` 在 HEAD 已 import（`git grep HEAD` 命中 `ShelfRoute.kt:31`）⇒ M-BUG-03 hunk 17 不引入新的 UI 组件依赖。

### 3.6 `ui/screen/shelf/ShelfAction.kt`（` M`，`+16/-0`，3 hunk；**M-BUG-03 = 1 个**）

| # | new 行 | 内容 | 归属 |
|---|---|---|---|
| 1 | `56..63` | `MarkFinished` / `MarkReading` / `ShelveBook` | S05 |
| 2 | `73..76` | `BatchMarkFinished` / `BatchMarkReading` | S05 |
| **3** | `111..114` | `CancelSourceRefreshConfirm` / `ConfirmSourceRefresh` | **M-BUG-03** |

### 3.7 `ui/screen/shelf/ShelfUiState.kt`（` M`，`+26/-0`，3 hunk；**M-BUG-03 = 2 个**）

| # | new 行 | 内容 | 归属 | 手工拆点 |
|---|---|---|---|---|
| **1** | `37..50` | `contentUpdatedBookIds`（`37-41`，M-BUG-03）+ `newBookIds`（`43-50`，NBB） | **MIXED** | **M-BUG-03 = `L37..42`** |
| **2** | `86` | `confirmSourceRefreshIds: List<String>? = null` | **M-BUG-03** | 整行 |
| 3 | `104..114` | `statusCounts` KDoc | S05 | — |

### 3.8 `ui/screen/shelf/BookGrid.kt`（` M`，`+153/-33`，23 hunk；**M-BUG-03 = 3 个**）

| # | new 行 | 内容 | 归属 | 手工拆点 |
|---|---|---|---|---|
| 1 | `39` | `+import remember` | general | — |
| 2–7 | `155..188` | `StatusRail` 状态分组条（`counts` / `modifier` / `ShelfStatusGroupOptions` / 计数） | S05 | — |
| **8** | `254..261` | `contentUpdated`（`254-255`，M-BUG-03）+ `isNew`（`256-257`，NBB）+ `chapterLabel`（`258-259`，smart-search）+ `completionState`（`260-261`，S05） | **MIXED** | **M-BUG-03 = `L254..255`** |
| 9 | `268..269` | `displayFinished` | S05 | — |
| 10–11 | `295..322` | `isSelected` / `primaryColor` / 边框 | general | — |
| 12 | `331` | `percent = if (displayFinished) percent else null` | S05 | — |
| **13** | `396..434` | 「新」角标块（`396-415`，NBB）+ 「可更新」角标块（`416-434`，M-BUG-03） | **MIXED** | **M-BUG-03 = `L416..434`** |
| 14 | `461` | 注释 | S05 | — |
| 15–16 | `467..475` | `label` if-else 链（S05 + smart-search，**块内不可再分**） | MIXED（非 M-BUG-03） | — |
| 17–18 | `478..515` | `maxLines` / 边框 | general | — |
| 19 | `522` | `percent = if (displayFinished) percent else null` | S05 | — |
| **20** | `544..557` | 列表模式 `if (contentUpdated) { 圆点 }` | **M-BUG-03** | 整块 |
| 21–23 | `568..619` | 「新」角标（NBB）+ 进度串（S05 + smart-search，**块内不可再分**） | MIXED（非 M-BUG-03） | — |

**HEAD 基线**：HEAD 的 `BookTile` 签名**没有** `contentUpdated` / `isNew` / `chapterLabel` / `completionState`（`git show HEAD:…BookGrid.kt` 实测）⇒ 取 M-BUG-03 只需新增 `contentUpdated: Boolean = false` 一个参数。

### 3.9 `ui/screen/shelf/ShelfScreen.kt`（` M`，`+145/-3`，17 hunk；**M-BUG-03 = 1 个**）

| # | new 行 | 内容 | 归属 |
|---|---|---|---|
| 1–10 | `24..396` | `LazyGridState` / `rememberLazyGridState` / `LaunchedEffect` / `focusBookIds` / `gridState` 提升 | S05-④ / NBB |
| 11 | `414..424` | 稳定化回调 `onOpenBookStable` 等 | general |
| 12 | `439` | `state = gridState` | S05-④ |
| 13–15 | `464..533` | 状态分组条常驻 + `progressEntity` + `chapterLabel` + `completionState` | S05 / smart-search（hunk 14 单行被 S05 与 smart-search 共用） |
| **16** | `539..545` | 3 处删除（旧内联 lambda，general）+ 7 处新增：`contentUpdated`（`539-540`，M-BUG-03）+ `isNew`（`541-542`，NBB）+ 3 个稳定回调（`543-545`，general） | **MIXED** | 
| 17 | `621..672` | `awaitFocusTarget` / `shelfGridIndexOfBook` 纯函数 | S05-④ / NBB |

**M-BUG-03 在 ShelfScreen.kt 的全部落点只有 1 处**：`L540 contentUpdated = item.bookId in state.contentUpdatedBookIds,`（+ `L539` 注释）。手工拆点：只取 `L539..540`，保留 3 行旧内联 lambda 不动。

### 3.10 测试文件

| 文件 | 状态 | M-BUG-03 内容 | 依赖的生产符号 | 归属问题 |
|---|---|---|---|---|
| `feature/library/ShelfSourceUpdateCheckerTest.kt` | `??` 新（88 行） | **6 条**纯函数用例（`:16..87`） | `isSourceContentUpdated`（同包） | 无；**无 Android 依赖，纯 JVM** |
| `ui/viewmodel/ShelfImporterTest.kt` | ` M`（`+939/-3`） | **L880..946 共 4 条**：`refresh without a source ref reports no source`（`883`）、`…without a locatable document id…`（`893`）、`…advances the source baseline after a successful repair`（`903`）、`…failure keeps the old baseline`（`928`） | `ShelfImporter.refreshFromSource` / `SourceRefreshResult` | 与 S04 / S04-3 **同一连续块**（§四 B-1） |
| `ui/viewmodel/ShelfViewModelTest.kt` | ` M`（`+276/-6`） | **L487..666 共 5 条**：`refresh prompts confirmation…`（`490`）、`cancelling source refresh clears confirmation…`（`517`）、`confirming source refresh executes refresh…`（`550`）、`confirming source refresh keeps failure reason…`（`587`）、`refresh without source updates completes directly…`（`640`） | `ShelfViewModel.pendingSourceRefreshIds` / `contentUpdatedBookIds` / `confirmSourceRefresh` / `cancelSourceRefresh` / `ShelfSourceUpdateChecker` | 与 M-BUG-09 **同一连续块**（§四 B-2） |
| `ui/screen/shelf/ShelfSourceRefreshCopyTest.kt` | `??` 新（71 行） | **5 条**弹窗文案用例 | `SOURCE_REFRESH_PROGRESS_NOTE` / `sourceRefreshConfirmBody`（ShelfRoute hunk 5 的 M-BUG-03 部分） | 无（矩阵 H174 已标「保留 HOLD」） |
| `ui/viewmodel/ShelfSourceRefreshFeedbackTest.kt` | `??` 新（188 行） | **14 条**汇总文案用例 | `summarizeSourceRefreshFailureReason` / `buildSourceRefreshSummary`（ShelfViewModel hunk 5） | ⚠ **矩阵 H180 标为「候选分组 B2」——实测依赖 M-BUG-03，见 §五** |

**M-BUG-03 测试合计 = 6 + 4 + 5 + 5 + 14 = 34 条。**

---

## 四、`git add -p` 无法拆分的连续块（精确行 + 依赖）

`-U0` 下每个 hunk 即极大连续变更块；**块内多特征时 `git add -p` 只能整体取/整体弃**（`e` 编辑模式或手工补丁属于「手工拆分」）。以下 **11 处**必须手工处理。

### A. 生产代码（9 处）

| # | 文件 | 块（new 行） | 块内特征 | **M-BUG-03 精确提取** | 提取后的依赖 |
|---|---|---|---|---|---|
| A-1 | `ShelfImporter.kt` | `41..76` | M-BUG-03 `41-59` + S05-④ `61-75` | 取 `L41..59`（`SourceRefreshResult` 全接口） | 无（自包含 sealed interface） |
| A-2 | `ShelfImporter.kt` | `236..296` | M-BUG-03 `236-283`、`288` + **B1 `284-287`** | 取 `L236..283` 与 `L288`，**删除 `L284..287`**（`// 正文替换成功后…` 三行注释 + `indexRefreshCoordinator.request(bookId)`） | 删除后 `refreshFromSource` 仍以 `return SourceRefreshResult.Success(outcome.message)` 收尾，**不引用任何 B1 符号**；同时可**不取** B1 的 hunk 2（`import`）与 hunk 5（构造参数） |
| A-3 | `ShelfRoute.kt` | `41..85` | S05-④ `41-56` + M-BUG-03 `58-85` + M-BUG-09 的删除行 | 取 `L58..85` | `SOURCE_REFRESH_PROGRESS_NOTE` / `sourceRefreshConfirmBody` 自包含；被 hunk 17 与 `ShelfSourceRefreshCopyTest` 消费 |
| A-4 | `ShelfRoute.kt` | `270..271` | M-BUG-03 `270` + NBB `271` | 取 `L270` | 需 `ShelfUiState.contentUpdatedBookIds`（§3.7 hunk 1 的 M-BUG-03 部分） |
| A-5 | `ShelfUiState.kt` | `37..50` | M-BUG-03 `37-42` + NBB `43-50` | 取 `L37..42` | 无 |
| A-6 | `BookGrid.kt` | `254..261` | M-BUG-03 `254-255` + NBB `256-257` + smart-search `258-259` + S05 `260-261` | 取 `L254..255` | 无（新增带默认值的参数，HEAD 调用点不受影响） |
| A-7 | `BookGrid.kt` | `396..434` | NBB `396-415` + M-BUG-03 `416-434` | 取 `L416..434`（含 `L416-417` 两行注释） | 需 `contentUpdated` 参数（A-6） |
| A-8 | `ShelfScreen.kt` | `539..545` | M-BUG-03 `539-540` + NBB `541-542` + general `543-545`（并删除 3 行旧 lambda） | 取 `L539..540`，**保留 3 行旧内联 lambda** | 需 `ShelfUiState.contentUpdatedBookIds`（A-5）+ `BookTile.contentUpdated`（A-6） |
| A-9 | `ShelfViewModel.kt` | hunk `13`(`677..691`)、`14`(`695..700`)、`15`(`702..775`)、`16`(`777..827`) | 四者共同构成 **`refresh()` 的一次整体重写**：hunk 13 改签名、hunk 14 加 `try {`、hunk 15 加新尾（`delay(350)` + `checkUpdatedSources(onResult)` + `finally`）、hunk 16 **删除旧尾两行**（`-delay(350)`、`-_isRefreshing.value = false`）。净效果是把旧尾两行重新缩进进 `try` 块，故四者在**括号与语句平衡上互相依赖** | **必须四块同取**（不是 4 个独立 hunk） | 缺 hunk 16 的删除会留下重复的 `delay(350)` / `_isRefreshing.value = false`；缺 hunk 13 的签名则 `ShelfRoute` hunk 24 的 `viewModel.refresh { … }` 无法编译。**仅编译可省去 13–16，但那样 `checkUpdatedSources` 永不被调用，`ShelfViewModelTest` 的 5 条 S04-1/M-BUG-03 用例必然失败** ⇒ **功能上不可分** |

> **A-2 是本报告最关键的一条**：M-BUG-03 的 `refreshFromSource` 尾部内嵌 B1 的调用，且该调用与 M-BUG-03 代码在**同一 `-U0` 块内**。若按窗口 1 的 B1 候选口径「`git add -p` 只取协调器相关 hunk」，在 `ShelfImporter.kt` 上**不成立**（该结论已在 [HOLD 16 行逐项裁决](../../legacy-stages/2026-09/2026-09-29-stage-e-hold-16-adjudication.md) §五 SR-1 登记；本报告补充了**反向**事实：M-BUG-03 侧同样无法整块取用）。

### B. 测试代码（2 处）

| # | 文件 | 块（new 行） | 块内段落（实测行号） | **M-BUG-03 精确提取** | 提取后的依赖 |
|---|---|---|---|---|---|
| B-1 | `ShelfImporterTest.kt` | `500..947`（`@@ -456,0 +500,448 @@`，448 行连续新增） | `500` TXT 回滚阶段分流（S04）；`611` 崩溃残留守卫（S04）；`753` `computeFileHash`（S04-3）；**`820`/`864` 两条 S04-3 用例已调用 `refreshFromSource`**；`880` **M-BUG-03 段**；`948` 起为 HEAD 既有「来源引用（R3）」段 | 取 `L880..946`（4 条用例） | 需 `sourceRef()` 助手（见下）+ `ShelfImporter.refreshFromSource`；**但 `L820`/`L864` 的 S04-3 用例也调用 `refreshFromSource`** ⇒ 若 M-BUG-03 不落地，S04-3 测试**编译失败**（反向依赖） |
| B-2 | `ShelfViewModelTest.kt` | `439..666`（`@@ -396,0 +439,228 @@`，228 行连续新增） | `440..470` M-BUG-09 用例；`472..485` `sourceRef()` 助手；**`487` 起 M-BUG-03 / S04-1 段** | 取 `L487..666`（5 条用例） | 需 `L472..485` 的 `sourceRef()` 助手 + `L151..153` 的 `shelfImporter ?: ShelfImporter(` 测试接缝 + M-BUG-03 的 import（`L18-19`、`L21-22`）+ 构造实参（`L178..180`） |

### C. 跨文件的共享测试助手

| 助手 | 位置 | 被谁使用 |
|---|---|---|
| `ShelfImporterTest.sourceRef(...)` | `ShelfImporterTest.kt:196..215`（hunk `@@ -173,0 +196,21 @@`） | **S04-3**（`L820`、`L864`）+ **M-BUG-03**（`L894`、`L912`、`L936`）+ **B1**（`L1069`、`L1090`） |
| `ShelfViewModelTest.sourceRef(...)` | `ShelfViewModelTest.kt:472..485` | M-BUG-03 / S04-1 用例 |

⇒ 这两个助手属**三方共享**，任何一方取用时都必须同批纳入，否则其余两方的测试编译失败。

---

## 五、未跟踪新文件是否遗漏在 180 行矩阵之外

**结论：是，而且是最核心的两个文件。**

| 文件 | 376 行总矩阵 | 180 行 HOLD CSV | 问题 |
|---|---|---|---|
| `feature/library/ShelfSourceUpdateChecker.kt` | **有行**，`status=??`，`group=EXCLUDED`，`sub_group/reason = "shelf source update feature"` | **无行** | 180 行 CSV 只抽取 `group=HOLD` 的行，因此该文件在 HOLD 映射表里**没有 `matrix_id`、没有 `mapping`、没有 `rule_provenance`**——任何只遍历 180 行 CSV 的对账都会**静默丢掉 M-BUG-03 的生产核心** |
| `feature/library/ShelfSourceUpdateCheckerTest.kt` | **有行**，`status=??`，`group=EXCLUDED`，同上 | **无行** | 同上 |

**且该 `EXCLUDED` 判定已被证伪**：[数据/同步 HOLD 判定](../../legacy-stages/2026-09/2026-09-29-stage-e-hold-data-sync.md) §2.7 实测「排除 ⇒ `ShelfImporter` 与 `ShelfViewModel` 无法编译」，并列入其 §4 修正建议 #7（「5 个新文件判 `EXCLUDED` → `EXCLUDED` 不成立」）。本报告独立复核该结论：`ShelfViewModel.kt:160` 以**非空构造参数**注入 `ShelfSourceUpdateChecker`，`ShelfSourceUpdateChecker.kt:85-86` 调用 `LibrarySourceIndex` 的两个新函数 ⇒ 三者构成**强连通闭包**。

**其余核查（排除其他遗漏）**：

- 376 行总矩阵共 **56 个 `EXCLUDED`** 行（其中 50 个 `??`），本域只占 2 个。
- `android/**` 工作树变更 366 项中**仅 1 项**不在 376 行总矩阵内：`feature/log/AppLog.kt`（**与 M-BUG-03 无关**，仅登记；如需处理属另一窗口）。
- 本域其余 8 个已跟踪文件与 2 个未跟踪测试文件**均在 180 行 HOLD CSV 内有行**：`LibrarySourceIndex.kt`=H025、`ShelfImporter.kt`=H026、`ShelfViewModel.kt`=H112、`ShelfRoute.kt`=H085、`ShelfAction.kt`=H083、`ShelfUiState.kt`=H089、`BookGrid.kt`=H080、`ShelfScreen.kt`=H086、`ShelfImporterTest.kt`=H139、`ShelfViewModelTest.kt`=H140、`ShelfSourceRefreshCopyTest.kt`=H174、`ShelfSourceRefreshFeedbackTest.kt`=H180。

**矩阵归属错误（1 处，需更正）**：`ShelfSourceRefreshFeedbackTest.kt`（H180）在 180 行 CSV 中 `mapping = 候选分组 B2（未隔离编译）`，但本轮实测其**唯一依赖**是 `ShelfViewModel.kt:98` 的 `summarizeSourceRefreshFailureReason` 与 `:117` 的 `buildSourceRefreshSummary`——二者位于 **M-BUG-03 hunk 5**，与 B2（`clearBookCache` 缓存清理）**无任何符号关系**。这与 [HOLD 16 行逐项裁决](../../legacy-stages/2026-09/2026-09-29-stage-e-hold-16-adjudication.md) §四 4.2 H174 条目登记的「H174/H180 口径不一致」一致，本轮给出确定性证据：**两者同属 M-BUG-03，H180 的 B2 标注应更正。**

---

## 六、最小编译闭包候选清单（**静态推断，未验证**）

> ⚠ **本节不宣称可编译。** 本窗口零构建；以下为按符号依赖推出的**最小文件/hunk 集合**，实际编译必须由后续窗口在 E 盘隔离目录验证。

### 6.1 生产（main）—— 9 个文件

| # | 文件 | 取用范围 | 备注 |
|---|---|---|---|
| 1 | `feature/library/LibrarySourceIndex.kt` | **全部 2 hunk**（`new 3`、`new 48..112`） | 无他批交织 |
| 2 | `feature/library/ShelfSourceUpdateChecker.kt` | **整文件**（未跟踪新文件） | 无他批交织 |
| 3 | `feature/library/ShelfImporter.kt` | hunk **1、3、4(A-1)、9(A-2)、10、11、12、13、29** | 4 与 9 需手工拆；**A-2 必须删除 `L284..287`** |
| 4 | `ui/viewmodel/ShelfViewModel.kt` | hunk **1、2、4、5、6、13、14、15、16、19** | 无块内多特征；但 **13–16 必须四块同取**（`refresh()` 整体重写，§四 A-9） |
| 5 | `ui/screen/shelf/ShelfAction.kt` | hunk **3**（`new 111..114`） | — |
| 6 | `ui/screen/shelf/ShelfUiState.kt` | hunk **1 的 `L37..42`** + hunk **2**（`new 86`） | hunk 1 需手工拆 |
| 7 | `ui/screen/shelf/ShelfRoute.kt` | hunk **5 的 `L58..85`** + hunk **9** + hunk **10 的 `L270`** + hunk **12** + hunk **17** + hunk **23** + hunk **24** | 5、10 需手工拆 |
| 8 | `ui/screen/shelf/BookGrid.kt` | hunk **8 的 `L254..255`** + hunk **13 的 `L416..434`** + hunk **20** | 8、13 需手工拆 |
| 9 | `ui/screen/shelf/ShelfScreen.kt` | hunk **16 的 `L539..540`**（保留 3 行旧 lambda） | 需手工拆 |

**不需要的**：Hilt/Dagger module（`LibrarySourceIndex`、`LibraryRootStore` 均已在 HEAD 为 `@Singleton @Inject constructor`；`ShelfSourceUpdateChecker` 亦为构造注入）；Room schema / 迁移；`AndroidManifest.xml`；`build.gradle.kts`；任何 `AppDatabase` / DAO 改动。

**未纳入但被 KDoc 引用的符号**（不构成编译依赖，仅 KDoc 链接可能未解析）：`ShelfImporter.kt:250` 引 `[sourceDocumentUriOf]`（**在闭包内**）、`:256` 引 `[ShelfSourceUpdateChecker]`（**在闭包内**）。

### 6.2 测试 —— 5 个文件

| # | 文件 | 取用范围 | 备注 |
|---|---|---|---|
| 1 | `feature/library/ShelfSourceUpdateCheckerTest.kt` | **整文件**（未跟踪新文件，6 条） | 纯 JVM |
| 2 | `ui/viewmodel/ShelfImporterTest.kt` | **`L880..946`** + 共享助手 `L196..215` + 相关 import（`new 16/24/31/36/40,2/50,2`） | 需手工从 B-1 块中切出；**且依赖 `L820`/`L864` 的反向需求见 §九 SR-3** |
| 3 | `ui/viewmodel/ShelfViewModelTest.kt` | **`L487..666`** + `L472..485` 助手 + `L151..153` 接缝 + import（`new 18,2`/`new 21,2`）+ 构造实参（`new 178,3`） | 需手工从 B-2 块中切出 |
| 4 | `ui/screen/shelf/ShelfSourceRefreshCopyTest.kt` | **整文件**（未跟踪新文件，5 条） | 依赖 ShelfRoute hunk 5 的 M-BUG-03 部分 |
| 5 | `ui/viewmodel/ShelfSourceRefreshFeedbackTest.kt` | **整文件**（未跟踪新文件，14 条） | 依赖 ShelfViewModel hunk 5；**矩阵 H180 的 B2 标注应更正** |

---

## 七、排除清单

| 排除项 | 归属 | 排除理由 |
|---|---|---|
| `ShelfImporter.kt` hunk 2（`new 18`）、hunk 5（`new 96`）、hunk 9 的 `L284..287` | **B1** | `SearchIndexBookRefreshCoordinator` 注入与 `request()` 调用；排除后 M-BUG-03 不引用任何 B1 符号（A-2 已证） |
| `ShelfImporterTest.kt` `L1059..1326`（5 条 S04-2 用例） | **B1** | 属窗口 1 的 B1 候选 |
| `ShelfViewModelTest.kt` `new 11`、`new 164,2` | **B1** | `import SearchIndexBookRefreshCoordinator` + `indexRefreshCoordinator = mockk(relaxed = true)` |
| `BookRepository.kt`（全部 18 hunk 中的 B1 部分） | **B1** | 本域不触及 `BookRepository` |
| `SearchTermDao.kt` / `SearchIndexRepository.kt` / `SearchIndexScheduler.kt` / `SearchIndexBookRefreshCoordinator.kt` / `BookRepositorySearchIndexTest.kt` / `SearchIndexRepository*Test.kt` | **B1** | 窗口 1 独占 |
| `BookRepository.kt` B2 hunks / `ShelfBookActions.kt` / `BookResidueSafeCleanupTest.kt` / `ShelfBookActionsTest.kt` / `StorageSubPage.kt` | **B2** | 窗口 1 独占 |
| `LibraryRootStore.kt` / `LibrarySource.kt` / `LibraryBrowserViewModel.kt` / `SafLibrarySourceTest.kt` / `LibraryBrowserSelectionTest.kt` / `LibraryBrowserSortTest.kt` | **库浏览器（窗口 1）** | 窗口 1 独占；且 M-BUG-03 只用到 `LibraryRootStore`（HEAD 既有）与 `LibrarySourceIndex` 的**新函数**，不触及 `ADDED_DESC` 排序 |
| `ShelfAction.kt` hunk 1、2；`ShelfUiState.kt` hunk 3；`ShelfViewModel.kt` hunk 12；`ShelfRoute.kt` hunk 16、20、21；`BookGrid.kt` hunk 2–7、9、12、14–16、19、21–23；`ShelfScreen.kt` hunk 13–15 | **S05 / B6** | 完读状态机、状态分组条、进度串 |
| `ShelfViewModel.kt` hunk 7、18 | **M-FEAT-01** | 排序/视图偏好同步 |
| `ShelfViewModel.kt` hunk 3、8、9、10；`ShelfRoute.kt` hunk 4、6、11 | **M-BUG-09** | 骨架屏门（`libraryLoaded`） |
| `ShelfViewModel.kt` hunk 11、17；`ShelfRoute.kt` hunk 2、7、8、13、14、18、19、22；`BookGrid.kt` hunk 8 的 `L256..257`、13 的 `L396..415`、21；`ShelfScreen.kt` hunk 1–10、12、17；`ShelfUiState.kt` hunk 1 的 `L43..50`；`ShelfImporter.kt` hunk 6、7、8、14–21、30、31 | **NBB / S05-④** | 「新」角标与「查看」定位 |
| `ShelfImporter.kt` hunk 22–28 | **S04 / S04-3** | 崩溃残留守卫、替换回滚阶段分流、`books.content_hash` 同步 |
| `ShelfImporterTest.kt` `L500..879`、`L1145..1326`、`L1350..1526` | **S04 / S04-3 / S04-4 / S05-④** | 与 M-BUG-03 同块但不同段落 |
| `BookGrid.kt` hunk 1、10、11、17、18；`ShelfScreen.kt` hunk 11；`ShelfRoute.kt` hunk 1 的 `navigationBarsPadding`、hunk 15 | **general** | 与 M-BUG-03 无符号关系 |
| `ShelfModels.kt` | **S05 / smart-search** | M-BUG-03 链**不引用** `ShelfModels` 的任何符号（`bookStatus` / `isDisplayFinished` / `extractChapterLabel` 均属他批） |
| `feature/log/AppLog.kt` | 未归类 | 唯一不在 376 行矩阵内的 android 变更项；与 M-BUG-03 无关 |

---

## 八、测试归属

| 测试类 | 条数 | 覆盖的 M-BUG-03 契约 | 依赖的生产符号 | 矩阵现状 |
|---|---|---|---|---|
| `ShelfSourceUpdateCheckerTest` | 6 | 「只有能证明变化时才判定为更新」：size 变 / mtime 变 / 两者同 / observed size 缺失 / baseline mtime 为 null 不可比 / 两者皆缺 | `isSourceContentUpdated`（纯函数） | `EXCLUDED`（**应更正**） |
| `ShelfImporterTest`（`L880..946`） | 4 | `refreshFromSource` 的 `NoSourceRef`（无引用 / 无 documentId）、`Success` 时基线推进、`Failed` 时基线不动 | `ShelfImporter.refreshFromSource`、`SourceRefreshResult` | H139（保留 HOLD） |
| `ShelfViewModelTest`（`L487..666`） | 5 | S04-1 前置一次性确认：检出即置待确认且**零正文替换**；取消清状态且角标保持；确认后逐本串行并汇总；失败原因不丢；无更新时直接回执 | `pendingSourceRefreshIds`、`contentUpdatedBookIds`、`confirmSourceRefresh`、`cancelSourceRefresh`、`ShelfSourceUpdateChecker` | H140（保留 HOLD） |
| `ShelfSourceRefreshCopyTest` | 5 | 弹窗文案边界：不得称「重置/清空/清除阅读进度」；必须含「不会主动清除已有阅读记录」「章节会重新划分」「阅读位置也可能随之变化」；不得把位置变化说成必然 | `SOURCE_REFRESH_PROGRESS_NOTE`、`sourceRefreshConfirmBody` | H174（保留 HOLD） |
| `ShelfSourceRefreshFeedbackTest` | 14 | 汇总文案：残留场景语义完整且不引导销毁/不承诺自动恢复；多书折叠与截断；0 本成功如实报；不泄露正文 | `summarizeSourceRefreshFailureReason`、`buildSourceRefreshSummary` | H180（**标 B2，应更正为 M-BUG-03**） |
| **合计** | **34** | | | |

**未被本域测试覆盖的 M-BUG-03 生产面**（如实登记）：

- `sourceDocumentUriOf` 的**两条授权路径分支**（树 URI vs `authority + documentId`）无独立单测；只经 `refreshFromSource` 的 mock 路径间接覆盖。
- `ShelfSourceUpdateChecker.checkUpdated` 的**取消语义**（`coroutineContext.ensureActive()`）与 `loadRoot()` 失败降级无独立用例（`isSourceContentUpdated` 纯函数有覆盖，`checkUpdated` 的 IO 外壳没有）。
- `ShelfRoute.kt` 的确认弹窗（hunk 17）与 `handleShelfAction` 的两个分支（hunk 23）**无 Compose/androidTest 覆盖**；`ShelfScreen.kt` 的 `contentUpdated` 传参（hunk 16）同样无 UI 测试。
- `BookGrid.kt` 的「可更新」角标（hunk 13 的 `L416..434`）与列表圆点（hunk 20）**无 Compose 测试**。

---

## 九、SEAM REQUEST

> 只登记可复核事实，不替任何窗口裁决其补丁。

### SR-1（与 B1，双向）：`ShelfImporter.kt` 的 hunk `236..296` 由 M-BUG-03 与 B1 共享，且**双向都拆不开**

- **B1 → M-BUG-03 方向**：`indexRefreshCoordinator.request(bookId)`（`L287`）与三行注释（`L284..286`）落在 M-BUG-03 的 `refreshFromSource`（`L236..283`）之后的**同一 `-U0` 连续块**内。窗口 1 若按「`git add -p` 只取协调器 hunk」处理，在本文件上**不成立**。
- **M-BUG-03 → B1 方向**（本轮新增事实）：M-BUG-03 若只取自己的 hunk 9，也必须**删除 `L284..287`**，否则引用不存在的 `indexRefreshCoordinator` 构造参数（B1 hunk 5）。
- **可复核证据**：`git diff -U0 -- …/ShelfImporter.kt` 中该块为 `@@ -168,2 +236,61 @@`；`Get-Content` 实测 `L283 refreshSourceBaseline(ref, uri)` / `L287 indexRefreshCoordinator.request(bookId)` / `L288 return SourceRefreshResult.Success(...)`。
- **请求**：窗口 1 与本窗口（或后续 M-BUG-03 实现窗口）需就 `ShelfImporter.kt` 的切分口径达成一致；**建议由集成者统一决定**，两窗口不各自取用同一文件。

### SR-2（与 B1）：B1 的 `ShelfImporterTest` 段**反向依赖 M-BUG-03 的生产符号**

- B1 的 5 条 S04-2 用例（`ShelfImporterTest.kt:1059..1326`）每一条都调用 `importer.refreshFromSource(...)`（`:1071`、`:1092`、`:1106`、`:1116`、`:1132`），并使用 `SourceRefreshResult`（`:1073`、`:1094`、`:1108`、`:1118`、`:1135`、`:1138`）。
- ⇒ **`refreshFromSource` / `SourceRefreshResult` 是 B1 测试的编译前置**。B1 若不同批纳入 M-BUG-03 的 `ShelfImporter.kt` hunk（9/10/11/12/13/29）与 `LibrarySourceIndex.kt` 的两个新函数，其测试**无法编译**。
- 请求：窗口 1 的 B1 候选清单需显式登记该前置（其现行清单未含 `LibrarySourceIndex.kt`）。

### SR-3（与 S04 / S04-3）：S04-3 测试段同样反向依赖 `refreshFromSource`

- `ShelfImporterTest.kt:823`（`refresh with unmeasurable small-file hash records unknown hash rather than the stale one`）与 `:867`（`large source file keeps policy null hash while still advancing size`）位于 **S04-3 段（`L753..879`）**，却调用 `importer.refreshFromSource("b1")` 并断言 `SourceRefreshResult.Success`。
- ⇒ 取 S04-3 段而不取 M-BUG-03 段 ⇒ **S04-3 测试编译失败**；反之取 M-BUG-03 段而不取 S04-3 段是**可行的**（M-BUG-03 段不引用 S04-3 符号）。
- 请求：S04-3 批次与 M-BUG-03 批次需约定先后，或把 `refreshFromSource` 作为**共享前置**单独立批。

### SR-4（域内）：8 个 MIXED 生产 hunk + 1 处跨 hunk 联动重写 + 2 个连续测试块必须手工拆分

见 §四 A-1..A-9、B-1、B-2 的精确行。**`git add -p` 不足以完成**；需要 `e` 编辑模式或直接生成子集补丁。特别提示：

- A-2 必须**删除** `L284..287`（不是保留）；
- A-8 必须**保留** 3 行旧内联 lambda（不取 general 的稳定化改动）；
- **A-9 的 hunk 13–16 必须四块同取**——它们共同构成 `refresh()` 的一次整体重写，单独取任一都会破坏语句/括号平衡；且省去它们会让 5 条 `ShelfViewModelTest` 用例失败；
- B-1 与 B-2 的提取会同时改变文件内行号，**必须按段落内容而非绝对行号定位**（建议以 `// =====` 段落标记为锚）。

### SR-5（矩阵）：2 个核心未跟踪新文件在 180 行 HOLD CSV 中无行

- `ShelfSourceUpdateChecker.kt`、`ShelfSourceUpdateCheckerTest.kt`：376 行总矩阵标 `group=EXCLUDED`，180 行 HOLD CSV **无行**。
- 该 `EXCLUDED` 已被 [数据/同步 HOLD 判定](../../legacy-stages/2026-09/2026-09-29-stage-e-hold-data-sync.md) §2.7 证伪（排除即 `ShelfImporter` / `ShelfViewModel` 编译失败）。
- 请求：集成者需为这两个文件**新建批次归属**（本报告建议名：**「M-BUG-03 来源更新确认组」**），并在 180 行映射表中补行；否则任何按 180 行对账的流程都会漏掉 M-BUG-03 的生产核心。

### SR-6（矩阵）：`ShelfSourceRefreshFeedbackTest.kt`（H180）的 `B2` 标注应更正

- 实测依赖 `ShelfViewModel.kt:98` / `:117`（M-BUG-03 hunk 5），与 B2 的 `clearBookCache` 无符号关系。
- 请求：把 H180 从「候选分组 B2」改为「M-BUG-03」，与 H174 同组。

### SR-7（与窗口 1，**文件级独占冲突**）：7 个文件与窗口 1 的候选物理重叠

| 文件 | 窗口 1 侧 | M-BUG-03 侧 | 冲突性质 |
|---|---|---|---|
| `ShelfImporter.kt` | B1（hunk 2、5、9 部分） | M-BUG-03（hunk 1、3、4、9、10–13、29） | **同 hunk 双向交织**（SR-1） |
| `ShelfImporterTest.kt` | B1（`L1059..1326`）+ B2 无关 | M-BUG-03（`L880..946`） | 同文件 + 同连续块（`L500..947` 与 `L1059..1326` 分属两块，但共享 `sourceRef` 助手） |
| `ShelfViewModel.kt` | B2（`clearBookCache` 桩：`ShelfViewModelTest`） | M-BUG-03（hunk 1、2、4、5、6、13–16、19） | 同文件不同 hunk；`ShelfViewModel.kt` 本身不在 B2 候选内 |
| `ShelfViewModelTest.kt` | B1（`L11`、`L164-165`）、B2（`L82-84`） | M-BUG-03（`L487..666`） | 同文件不同块 |
| `ShelfRoute.kt` / `BookGrid.kt` / `ShelfScreen.kt` | 库浏览器候选**不含**这三文件 | M-BUG-03 | **无重叠**（仅在本报告澄清） |
| `ShelfAction.kt` / `ShelfUiState.kt` | 不在窗口 1 候选中 | M-BUG-03 | **无重叠** |

- 请求：**在窗口 1 的 B1+B2 联合候选完成并冻结之前，M-BUG-03 不得对 `ShelfImporter.kt` / `ShelfImporterTest.kt` / `ShelfViewModelTest.kt` 做任何写操作**；三文件的切分口径应由集成者串行裁定。本窗口**未**触碰这三个文件。

---

## 十、尚缺的验证与纪律声明

### 10.1 尚缺的验证

| # | 缺口 | 说明 |
|---|---|---|
| 1 | **隔离编译** | 本窗口零构建。§六 的 9+5 文件闭包**从未**在 HEAD 上验证过；8 个 MIXED hunk 的手工拆分也**未执行** |
| 2 | **定向 JVM** | 34 条 M-BUG-03 测试**未在拆分后子集上执行过**；`ShelfImporterTest` / `ShelfViewModelTest` 的段落切分改变行号后需重跑 |
| 3 | **`checkUpdated` 的取消与失败降级** | 无独立用例（§八） |
| 4 | **`sourceDocumentUriOf` 两条授权路径** | 无独立用例 |
| 5 | **UI 接线** | `ShelfRoute` 确认弹窗、`ShelfScreen` 传参、`BookGrid` 角标/圆点均无 Compose / androidTest 覆盖 |
| 6 | **设备证据** | 本域无任何真机/MuMu 证据；历史证据为 MuMu qahost 限定 PASS（`docs/qa/2026-09/2026-09-27-s04-mumu-true-source-refresh.md`），**不等于**实体手机与正式书库 |
| 7 | **`ShelfModels.kt` 的归属确认** | 本报告判定 M-BUG-03 链**不引用** `ShelfModels`；该结论来自符号穷举，未经编译证实 |

### 10.2 未做的事（纪律）

- 共享仓库内**唯一写入 = 本文件**（`docs/coordination/reviews/2026-09/2026-09-29-stage-e-mbug03-boundary-review.md`）。
- **未**修改任何源码 / 测试 / CSV / 公共配置 / 其他窗口报告；**未**读取或写入窗口 1 的任何 E 盘目录。
- **未**运行任何 Gradle 命令、**未**构建、**未**操作设备 / MuMu / ADB / 正式书库 / 原始仓库。
- **未** stage / commit / push / reset / checkout；**未**迁移目录。
- 本报告**不宣称**任何候选已可编译；§六 的闭包明确标注为「静态推断，未验证」。
- 未把「符号在 HEAD 内自足」表述为「已可编译」；A-2 / SR-1 已给出**具体反例**（同一 `-U0` 块内 B1 与 M-BUG-03 双向交织）。
