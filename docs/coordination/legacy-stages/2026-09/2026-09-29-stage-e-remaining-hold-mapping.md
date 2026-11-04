# 阶段 E — 剩余 86 行未映射 HOLD 逐行归属（2026-09-29，只读）

> 窗口：E 阶段剩余 HOLD 逐行归属（ZCode GLM Flash）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支 `codex/recover-worktree-20260920`，**HEAD 已核对 = `513891d6a66a9b78e072ba759d34ed580d377ecf`**（首批 A/B 两次提交之后）
> 输入：[当前交接](../../../handoff/current.md)、[首批两次提交复核](../../reviews/2026-09/2026-09-29-stage-e-first-batch-commit-review.md)、[机械性预检](2026-09-29-stage-e-mechanical-preflight.md)、`E:\creation-reading-assistant\stage-e-incremental-20260929\hold-180-row-mapping.csv`、`hold-unmapped-list.txt`
> 输出：`E:\creation-reading-assistant\stage-e-incremental-20260929\hold-180-row-mapping-updated.csv`
> **未改源码、未提交**；未 stage/commit/push/reset/checkout；未切换项目目录；未触碰 Antigravity 报告、MuMu、正式书库或原仓库。

---

## 一、结论

| 类别 | 行数 |
|---|---|
| **候选分组（未隔离编译）** | **55** |
| **保留 HOLD** | **122** |
| **其中「仍待裁决」** | **16** |
| 仅「首批已验」（不涉候选分组） | 3 |
| 合计 | 180 |

**本轮处理的 86 行**（原 `mapping=未映射`）判定：

| 判定 | 行数 |
|---|---|
| 候选分组 B6（未隔离编译） | 25 |
| 候选分组 B3（未隔离编译） | 3 |
| 候选分组 B1（未隔离编译） | 1 |
| 候选分组 B2（未隔离编译） | 1 |
| 候选分组 B1 + B2（未隔离编译，跨批次） | 1 |
| 候选分组 B6 + B1（未隔离编译，跨批次） | 1 |
| 首批已验（U T1–T4） | 1 |
| 保留 HOLD | 53 |
| 合计 | 86 |

**ID 校验**：`H001`–`H180` 连续、唯一、无漏行、无重复行；180 个 `file_path` 与源矩阵 `group=HOLD` 的 180 行**逐序相同**（已用脚本断言 `source_row` 与顺序一致）。

---

## 二、方法与证据来源

只读手段：`git status --porcelain -uall`、`git diff -U0/-U1`、全仓符号 grep、以及对**既有报告**的逐文件检索（`docs/**/*.md` 447 篇）。

**归属判定只采用两类证据，不做文件名前缀猜测**：

1. **源码调用关系**：文件实际引用的符号属于某特征（如 `newBookIds` → NBB、`sourceDocumentUriOf`/`querySourceMetadata` → M-BUG-03 依赖、`ImportMergeResult` → S14）。
2. **既有报告点名**：报告在文件清单中**逐文件列出**该路径，且该报告已被本轮复核或交接引用。

**特征 → 候选批次的映射**（取自[集成决策材料](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md) §2）：

| 特征 | 批次 | 依据 |
|---|---|---|
| S05 完本契约（含 NBB 新书角标） | **B6** | §2.6 标题「G7 + UI 组合 A（S05 + smart-search + grid-helper + NBB）」 |
| V5 时长单位切换 | **B3** | §2.3（G3+G6，含 `TimeUtils.kt`/`DurationDisplay.kt`） |
| S03 搜索索引并发 | **B1** | §2.1 |
| S04 来源替换与残留清理 | **B2** | §2.2 |
| M-BUG-03 来源更新确认 | **无批次（B6 明确排除）** | §2.6「排除范围：严格排除 M-BUG-03…M-BUG-09…cache-clear 逻辑」 |
| Profile / Stats 大重写、Reader 设置覆盖、选区动作 | **§2.9 已定义 HOLD 桶** | §2.9 表 |

**三份报告构成本轮主要证据源**：
- [S05-① 报告](2026-09-28-s05-cancel-finished-state-result.md) §四「实际改动文件」——逐文件列出 11 个文件；
- [S05 跨页消费者报告](2026-09-28-s05-cross-page-finished-consumers-result.md) §四——逐文件列出 17 个文件；
- [Stage-E UI HOLD 窗口报告](2026-09-29-stage-e-hold-ui.md) §5「全部 102 文件归属表」——逐文件给出特征与缺口，其 §5.6「通用 UI / other（39 文件）」与集成决策材料 §2.9 的「…等 39 个 UI 文件」**数量精确对应**。

---

## 三、候选分组归属（本轮 32 行）

### 3.1 B6 — S05 完本契约 / NBB（25 行）

**S05-① 报告 §四点名（5）**：`BookActionSheet.kt`、`BookDetailHeaderSection.kt`、`BookDetailSheet.kt`、`ShelfSearchRoute.kt`、`ShelfSelectionRoute.kt`

**S05 跨页消费者报告 §四点名（11）**：`HomeViewModel.kt`、`HomeContinueModels.kt`、`HomeArchiveViewModel.kt`、`MyReadingViewModel.kt`、`StatsRepository.kt`、`StatsPage.kt`、`HomeContinueCards.kt`、`HomeContinueSection.kt`、`HomeContinueModelsTest.kt`、`HomeViewModelTest.kt`、`HomeArchiveViewModelTest.kt`、`MyReadingViewModelTest.kt`、`StatsComputationTest.kt`、`ReadingStatsPolicyTest.kt`（后 7 项为测试）

**UI 报告 §5 判 S05（3）**：`BookViewModel.kt`、`BookViewModelTest.kt`、`ShelfStatusFilterTest.kt`

**NBB 特征（3）**：`ShelfPrefs.kt`（`newBookIds`/`clearNewBookId`）、`ShelfPrefsTest.kt`、`ShelfImportFocusTargetTest.kt`（`focusImportTarget`/`awaitFocusTarget`）

> **口径提示**：`StatsPage.kt` 在 UI 报告 §5.4-24 被标为「S05+V5 需手动拆分」，`HomeContinueSection.kt` 在 §5.6-40 被粗归 other —— 两处均取**更具体的 S05 报告**为准，但拆分动作仍属未做，故只标「未隔离编译」。

### 3.2 B3 — V5 时长单位（3 行）

`HomeReadingArchiveSection.kt`、`HomeReadingArchiveSectionTest.kt`、`TimeUtilsTest.kt`
依据：UI 报告 §5.4-21 / §5.7-91 判 V5；§2.8 V5 链列出 `HomeReadingArchiveSection.kt`；B3 文件清单含 `TimeUtils.kt`。

### 3.3 B1 / B2（3 行）

| 文件 | 归属 | 依据 |
|---|---|---|
| `data/local/dao/SearchTermDao.kt` | B1 | 集成决策材料 §2.1 明列「`SearchTermDao.kt`：hunk 6（`SearchIndexCoverageDao.distinctBookIds`）」 |
| `data/repository/BookRepository.kt` | **B1 + B2**（跨批次） | §2.1 B1 列其 S03 hunk、§2.2 B2 列其 S04 hunk；另含 S05-① 报告 §四-8 的 KDoc hunk |
| `data/repository/BookRepositoryTest.kt` | **B6 + B1**（跨批次） | S05-① 报告 §四-11（两用例）+ §2.1 B1 列其搜索索引用例 |

### 3.4 首批已验（1 行）

`data/local/dao/TaxonomyDao.kt` —— **HEAD `513891d6` 已含其全部 T1–T4 改动**（`git status` 无 diff）。矩阵成文时（HEAD `5aed593`）仍标 HOLD，属**状态已过期**，本轮更正为「首批已验（U T1–T4）」。

---

## 四、保留 HOLD（本轮 53 行）

### 4.1 已由 §2.9 定义 HOLD 桶（37 行，**无需新裁决**）

| 桶 | 行数 | 文件 |
|---|---|---|
| Reader 设置覆盖与选区动作 | 5 | `ReadingPresets.kt`、`SelectionActionStore.kt`、`ReadingPresetsTest.kt`、`SelectionActionSettingsTest.kt`、`SettingsStoreTest.kt` |
| Profile / Stats 大重写（= UI 报告 §5.6「other 39 文件」的 UI 部分） | 32 | `AppLayout.kt`、`GlassDialogs.kt`、`GlassSurface.kt`、`MicroIsland.kt`、`OptimizedImage.kt`、`SettingsComponents.kt`、`SharedComponents.kt`、`LayoutTokens.kt`、`AppNavigation.kt`、`OnboardingOverlay.kt`、`HomeRoute.kt`、`HomeScreen.kt`、`HomeUiState.kt`、`HomeCompletedSection.kt`、`HomeInspirationSection.kt`、`HomeMetricsSection.kt`、`HomeArchiveFormatters.kt`、`HomeCompletedScreen.kt`、`InspirationDetail.kt`、`MyReadingRoute.kt`、`MyReadingScreen.kt`、`SearchScreen.kt`、`BatchSheet.kt`、`ShelfUtils.kt`、`StatsScreen.kt`、`CreationSection.kt`、`HeatmapSection.kt`、`SummaryGroup.kt`、`YearBillHeroCard.kt`、`YearBillHeadlineTest.kt`、`LibraryBrowserComponents.kt`、`LibraryBrowserRoute.kt` |

### 4.2 仍待裁决（16 行，**无批次覆盖，需总指挥决定去向**）

| 文件 | 缺什么证据 / 待裁决点 |
|---|---|
| `App.kt` | 启动诊断（设备信息/heap/版本）、`appVersion` 缓存、`ONBOARDING_PREFS` 预热——**无任何候选批次覆盖**；需裁决独立成批或并入 Profile 诊断域 |
| `data/repository/SyncRepository.kt` | `pull/push` 形参类型 `SyncOptions.kt` 归属未裁决（前轮标 EXCLUDED 但被本文件与 `ProfileSyncEngine`/`ProfileViewModel` 引用） |
| `feature/library/LibraryRootStore.kt` | R3 来源索引增量，**B1–B8 无覆盖批次** |
| `feature/library/LibrarySource.kt` | 同上（`LibraryEntry` 排序） |
| `feature/library/LibrarySourceIndex.kt` | UI 报告 §2.6 明载 **M-BUG-03 依赖本文件**，而 M-BUG-03 被 B6 明确排除 |
| `feature/library/ShelfImporter.kt` | 源码含 `newBookIds`(NBB) + `sourceDocumentUriOf`/`querySourceMetadata`(M-BUG-03) + `ShelfSourceUpdateChecker`，跨三域；M-BUG-03 被 B6 排除 |
| `feature/sync/WebDavBackup.kt` | 改动为 WebDAV 客户端升级（MKCOL/PROPFIND/OPTIONS/HttpUrl）+ Legado ZIP 支持，**无批次覆盖** |
| `test/feature/library/SafLibrarySourceTest.kt` | R3 增量测试，无候选批次 |
| `test/ui/viewmodel/ShelfImporterTest.kt` | UI 报告 §5.5-28 判「M-BUG-03+NBB」且自注「需确认是否全 M-BUG-03/NBB」 |
| `test/ui/viewmodel/ShelfViewModelTest.kt` | 跨 M-BUG-03/NBB/R3 多域；`ShelfViewModel.kt` 本身在 UI 报告「仍 HOLD 8 文件」内 |
| `test/ui/viewmodel/ShelfBookActionsTest.kt` | UI 报告 §5.7-101 判「S05+cache-clear」且自注「需确认」；cache-clear 归 B2、S05 归 B6，拆分未证 |
| `test/ui/screen/shelf/ShelfSourceRefreshCopyTest.kt` | M-BUG-03 来源刷新文案，被 B6 排除且无其它批次 |
| `androidTest/…/MyReadingTimelineRowGestureTest.kt` | 新文件**同时 import `ReadingCompletionState`(S05→B6) 与 `DurationUnit`(V5→B3)**，跨两批次 |
| `ui/viewmodel/LibraryBrowserViewModel.kt` | UI 报告 §5.6-81 判 other（library browser）且自注「需确认」；R3/库浏览器域无候选批次 |
| `test/ui/viewmodel/LibraryBrowserSelectionTest.kt` | 库浏览器（R3）域新测试，无候选批次 |
| `test/ui/viewmodel/LibraryBrowserSortTest.kt` | 同上 |

**另 1 行**（`HeatmapSection.kt`）已计入 §4.1 的 Profile/Stats 桶，但其缺口需单独说明：UI 报告 §2.8 的 V5 链列出本文件，而 §5.4-22 自注「**需确认是否纯 V5**」⇒ 逐 hunk 纯度未证，**不能**归 B3。

---

## 五、明确不做的事

1. **不按文件名前缀猜归属**。例如 `ui/screen/stats/**` 未一律归 V5（`StatsScreen.kt`、`CreationSection.kt` 等在 UI 报告中判 other，属 §2.9 桶）；`feature/library/**` 未一律归 R3（`ShelfImporter.kt` 实为三域混合）。
2. **不把候选分组写成已可编译**。全部 55 行一律标注「**未隔离编译**」；本轮未运行任何构建、未操作设备。
3. **不用 JVM 或整树结果外推**。本轮零构建。
4. **不裁决产品行为**。§4.2 的 16 行只指出「缺什么证据/待谁裁决」，不给结论。

---

## 六、产物与校验

| 项 | 值 |
|---|---|
| 更新后 CSV | `E:\creation-reading-assistant\stage-e-incremental-20260929\hold-180-row-mapping-updated.csv` |
| SHA-256 | `ffb5fc2309ccd39d52dcc0488e049c153b61a4a9802c7fad12abfa8e18ad7bf7` |
| 行数 | 180 数据行 + 表头 |
| 列 | `matrix_id, source_row, file_path, status, group, sub_group, hunk_detail, lines_changed, mapping, rule_provenance` |
| ID 校验 | `H001`–`H180` 连续唯一；`file_path` 与源矩阵 `group=HOLD` 的 180 行逐序相同；无漏行、无重复 |
| 原列保留 | 原矩阵 ID（新增 `matrix_id`/`source_row`）、`file_path`、`status`、`group`、`sub_group`、`hunk_detail`、`lines_changed` 原样保留 |
| 措辞归一 | 上轮 69 行的 `保留HOLD` 归一为 `保留 HOLD`（仅字符串归一，无语义变更） |

---

## 七、纪律声明

- 仓库内**唯一写入 = 本文件**；未改产品源码、测试、配置或其他窗口文件。
- 未 stage/commit/push/reset/checkout；未切换项目目录；未触碰 Antigravity 报告、MuMu/ADB、正式书库、原仓库。
- 未把「候选分组」表述为「最小可编译已证」；未用整树构建结果外推拆分后子集。
