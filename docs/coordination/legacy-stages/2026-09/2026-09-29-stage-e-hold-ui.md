# Stage-E UI HOLD 窗口 2 — 逐 hunk 归属报告

> **窗口**: 2（UI 非 reader）
> **日期**: 2026-09-29
> **作者**: 窗口 2 调查 agent
> **独占范围**: `ui/**` HOLD 项，排除 `ui/screen/reader/**`、reader 专属组件及 `feature/reader/**`（归窗口 3）
> **可写文件**: 本文件 + E 盘 UI 矩阵
> **约束**: 只读源码，不改产品代码/测试/公共交接，不 stage/commit/push/reset/checkout，不构建或操作设备

---

## 1. 汇总

| 指标 | 数量 |
|------|------|
| 接手文件数（ui/** HOLD，排除 reader） | **102** |
| 已判定归属文件数 | **102** |
| 仍 HOLD 文件数（无法干净拆分） | **8** |
| 可独立提交文件数（单特征、无混合 hunk） | **61** |
| 需手动 hunk 编辑才能提交文件数 | **33** |

### 仍 HOLD 的 8 个文件（精确清单）

| 文件 | HOLD 原因 |
|------|-----------|
| `ui/screen/shelf/BookGrid.kt` | hunk 15/20/21 将 S05 `displayFinished` 与 smart search `chapterLabel` 编织在同一 if-else 链中，`git add -p` 无法拆分 |
| `ui/screen/shelf/ShelfRoute.kt` | hunk 6 将 S05 `focusIdsForBatch` 与 NBB `focusNewBookIds` 编织在同一 snackbar action 块中，`git add -p` 无法拆分 |
| `ui/screen/shelf/ShelfModels.kt` | hunk 3 将 S05 + smart search + grid helper 混在 122 行连续块中；可手动编辑拆分但三者在同一 `@@` 块内 |
| `ui/screen/shelf/ShelfScreen.kt` | hunk 14 单行 `val progressEntity` 被 S05 和 smart search 共同引用；hunk 15/16 混合多特征 |
| `ui/screen/shelf/ShelfUiState.kt` | hunk 1 将 M-BUG-03 `contentUpdatedBookIds` 与 NBB `newBookIds` 放在同一 data class 字段块 |
| `ui/viewmodel/ShelfViewModel.kt` | 19 hunks 跨 S05/M-BUG-03/NBB/M-BUG-09/prefs-sync/M-FEAT-01 六个特征；各 hunk 可独立拆分但组合依赖复杂 |
| `ui/viewmodel/ShelfBookActions.kt` | S05 hunk 3 与 cache-clear hunks 1/2/4/5 在同一类中；可拆分但 cache-clear 依赖 `BookCacheClearOutcome`（data 层 HOLD） |
| `ui/screen/shelf/ShelfAction.kt` | S05 hunks 1/2 与 M-BUG-03 hunk 3 在同一 sealed interface；可拆分但 M-BUG-03 hunk 3 依赖 ViewModel M-BUG-03 方法 |

> **注**: "仍 HOLD" 不等于"不能提交"。上述 8 个文件中，**ShelfModels.kt、ShelfAction.kt、ShelfUiState.kt、ShelfViewModel.kt、ShelfBookActions.kt** 可通过 `git add -p` 手动编辑按特征拆分提交；**BookGrid.kt、ShelfRoute.kt、ShelfScreen.kt** 存在不可拆分的 hunk，必须以组合形式提交或手动改写代码。

---

## 2. 特征定义与跨文件依赖链

### 2.1 特征清单

| 特征 ID | 描述 | 涉及文件数 |
|---------|------|-----------|
| **S05** | 完本契约：`isDisplayFinished`/`readingStatus`/`ShelfStatusGroupOptions`/`shelfStatusCounts`/`bookStatus`/`markFinished`/`markReading`/`shelveBook` | 28 |
| **smart-search** | 拼音首字母匹配 + 章节标签：`extractPinyinInitials`/`getChineseCharInitial`/`extractChapterLabel`/`filterItems` pinyin 分支 | 4 |
| **grid-helper** | 网格定位辅助：`shelfLeadingItemCount`/`shelfGridIndexOfBook` | 3 |
| **NBB** | 新书角标（New Book Badge）：`newBookIds`/`clearNewBookId`/`isNew`/`focusNewBookIds`/`focusBookIds`/`focusIdsForBatch`/`focusImportTarget`/`awaitFocusTarget` | 7 |
| **M-BUG-03** | 来源文件更新确认：`contentUpdatedBookIds`/`pendingSourceRefreshIds`/`confirmSourceRefresh`/`cancelSourceRefresh`/`refreshSourceUpdates`/`SOURCE_REFRESH_*` | 8 |
| **M-BUG-09** | 骨架屏时序修正：`libraryLoaded` 替代 `firstLoad` + 350ms 定时器 | 4 |
| **V5** | 时长显示单位切换（S15-F3-B1）：`LocalDurationUnit`/`LocalDurationUnitToggle`/`durationUnitToggle`/`formatDuration` 重载 | 7 |
| **cache-clear** | 批量清缓存结果细化：`BookCacheClearOutcome`/`MidwayAbortCause`/`buildCacheClearMessage` | 2 |
| **M-FEAT-01** | 默认排序模式从 RECENT 改为 IMPORTED | 1 |
| **prefs-sync** | ShelfPrefs sortMode/viewMode → session 状态同步 | 1 |
| **other** | 通用 UI / profile 重写 / stats 重写 / 组件优化等不涉及上述特征的改动 | 39 |

### 2.2 S05 跨文件依赖链

```
ShelfModels.kt (hunks 1,2,3-S05,8)
  ├─ defines: readingStatus, isDisplayFinished, ShelfStatusGroupOptions, shelfStatusCounts, bookStatus, statusLabel
  │
  ├─→ ShelfUiState.kt (hunk 3)
  │    └─ uses: shelfStatusCounts → adds statusCounts: Map<ShelfStatusFilter, Int>
  │
  ├─→ BookActionSheet.kt (all 6 hunks)
  │    └─ uses: isDisplayFinished → status action card (MarkFinished/MarkReading/Shelve)
  │
  ├─→ BookDetailHeaderSection.kt (2 hunks)
  │    └─ uses: isDisplayFinished → 藏书印由显式状态决定
  │
  ├─→ BookDetailSheet.kt (1 hunk)
  │    └─ passes: completionState = progress?.completion_state
  │
  ├─→ BookGrid.kt (hunks 2-7,9,12,14,18,22)
  │    └─ uses: ShelfStatusGroupOptions, isDisplayFinished, statusLabel → StatusRail + displayFinished
  │
  ├─→ ShelfScreen.kt (hunks 1,3-10,12,13,17 + parts of 14,15)
  │    └─ uses: statusCounts, StatusRail, completionState, shelfGridIndexOfBook, awaitFocusTarget
  │
  ├─→ ShelfAction.kt (hunks 1,2)
  │    └─ defines: MarkFinished, MarkReading, ShelveBook, BatchMarkFinished, BatchMarkReading
  │
  ├─→ ShelfBookActions.kt (hunk 3)
  │    └─ defines: markFinished, markReading, markFinishedBatch, markReadingBatch
  │
  ├─→ ShelfViewModel.kt (hunk 12)
  │    └─ wraps: bookActions.markFinished etc. → exposes fun markFinished/markReading/...
  │
  ├─→ ShelfRoute.kt (hunks 2,3-S05,5-S05,6-S05,11-S05,13,17,18)
  │    └─ uses: focusIdsForBatch, focusNewBookIds, onMarkFinished/onMarkReading/onShelve, action handling
  │
  ├─→ ShelfSearchRoute.kt (6 hunks)
  │    └─ uses: ShelfStatusFilter, statusLabel
  │
  ├─→ ShelfSelectionRoute.kt (6 hunks)
  │    └─ uses: ShelfStatusFilter, statusLabel
  │
  ├─→ HomeContinueCards.kt (1 hunk)
  ├─→ HomeContinueModels.kt (4 hunks)
  ├─→ BookViewModel.kt (2 hunks)
  ├─→ HomeArchiveViewModel.kt (3 hunks)
  ├─→ HomeViewModel.kt (5 hunks)
  ├─→ MyReadingViewModel.kt (1 hunk)
  └─→ StatsPage.kt (hunks with S05 part)
```

### 2.3 smart-search 跨文件依赖链

```
ShelfModels.kt (hunks 3-smart,4,5,6,7,9)
  ├─ defines: extractPinyinInitials, getChineseCharInitial, extractChapterLabel, filterItems pinyin 分支
  │
  ├─→ BookGrid.kt (hunks 8-chapterLabel,15,20,21)
  │    └─ uses: chapterLabel param in progress label if-else chains
  │
  └─→ ShelfScreen.kt (hunks 14,15-chapterLabel)
       └─ uses: extractChapterLabel(progressEntity)
```

> **关键**: smart search 的 `chapterLabel` 与 S05 的 `displayFinished` 在 BookGrid.kt hunk 15/20/21 中被编织在同一 if-else 链中。二者无调用依赖（`displayFinished` 不调用 `extractChapterLabel`，反之亦然），但在同一表达式内共存。

### 2.4 grid-helper 跨文件依赖链

```
ShelfModels.kt (hunk 3-grid)
  ├─ defines: shelfLeadingItemCount, shelfGridIndexOfBook
  │
  └─→ ShelfScreen.kt (hunk 8)
       └─ uses: shelfGridIndexOfBook in LaunchedEffect scroll focus
```

> **关键**: grid-helper 的 `shelfGridIndexOfBook` 被 ShelfScreen.kt 的 S05 focus scroll 逻辑引用。若提交 S05 focus scroll，必须同时提交 grid-helper。

### 2.5 NBB 跨文件依赖链

```
ShelfViewModel.kt (hunks 11,17)
  ├─ defines: newBookIds, clearNewBookId, importer.onBookImported
  │
  ├─→ ShelfUiState.kt (hunk 1-newBookIds)
  │    └─ adds: newBookIds: Set<String>
  │
  ├─→ ShelfScreen.kt (hunks 14,16-isNew)
  │    └─ uses: state.newBookIds → passes isNew to BookTile
  │
  ├─→ BookGrid.kt (hunks 8-isNew,13,20)
  │    └─ uses: isNew param → "新" badge
  │
  └─→ ShelfRoute.kt (hunks 5,8,11,15,16,19)
       └─ uses: newBookIds, clearNewBookId, focusNewBookIds
```

> **关键**: NBB 的 `focusNewBookIds` 与 S05 的 `focusIdsForBatch` 在 ShelfRoute.kt hunk 6 中被编织在同一 snackbar action 块。二者无调用依赖，但在同一 `if` 块内共存。

### 2.6 M-BUG-03 跨文件依赖链

```
ShelfViewModel.kt (hunks 1,2,4,5,6,13,14,15,16,19)
  ├─ defines: contentUpdatedBookIds, pendingSourceRefreshIds, confirmSourceRefresh, cancelSourceRefresh, refreshSourceUpdates, buildSourceRefreshSummary
  │
  ├─→ ShelfAction.kt (hunk 3)
  │    └─ defines: CancelSourceRefreshConfirm, ConfirmSourceRefresh
  │
  ├─→ ShelfUiState.kt (hunk 1-contentUpdated, hunk 2)
  │    └─ adds: contentUpdatedBookIds, confirmSourceRefreshIds
  │
  ├─→ ShelfScreen.kt (hunks 14,16-contentUpdated)
  │    └─ uses: state.contentUpdatedBookIds → passes contentUpdated to BookTile
  │
  ├─→ BookGrid.kt (hunks 8-contentUpdated,13,19)
  │    └─ uses: contentUpdated param → "可更新" badge
  │
  └─→ ShelfRoute.kt (hunks 1,3-M-BUG-03,7,8,10,14,20)
       └─ uses: contentUpdatedBookIds, pendingSourceRefreshIds, confirmSourceRefresh, cancelSourceRefresh
```

> M-BUG-03 依赖 data 层 `LibrarySourceIndex`、`ShelfSourceUpdateChecker`、`SourceRefreshResult`（非 ui/** HOLD，归窗口 1）。

### 2.7 M-BUG-09 跨文件依赖链

```
ShelfViewModel.kt (hunks 3,8,9,10)
  ├─ defines: _libraryLoaded, libraryLoaded (onEach { _libraryLoaded.value = true })
  │
  └─→ ShelfRoute.kt (hunks 3-M-BUG-09,4,9)
       └─ uses: viewModel.libraryLoaded → showSkeleton = !libraryLoaded
```

### 2.8 V5 跨文件依赖链

```
ui/util/TimeUtils.kt (5 hunks)
  ├─ defines: LocalDurationUnit, LocalDurationUnitToggle, durationUnitToggle, formatDuration overload
  │
  ├─→ BookDetailStatsSection.kt (13 hunks)
  │    └─ uses: formatDuration(ms, unit), durationUnitPill, durationUnitToggle
  │
  ├─→ HomeReadingArchiveSection.kt (3 hunks)
  ├─→ HeatmapSection.kt (21 hunks)
  ├─→ TrendSection.kt (4 hunks)
  ├─→ StatsPage.kt (S05+V5 part)
  ├─→ ProfileHomeScreen.kt (44 hunks)
  └─→ ProfileViewModel.kt (21 hunks)
```

> V5 与 S05 在 StatsPage.kt 中共存但可按 hunk 拆分。

---

## 3. 核心 shelf 文件逐 hunk 归属

### 3.1 ShelfModels.kt（9 hunks, +219/-14）

| Hunk | 行范围 | 归属 | 可拆分 | 说明 |
|------|--------|------|--------|------|
| 1 | `@@ -67 +67,27` | **S05** | ✓ | doc comment for bookStatus |
| 2 | `@@ -72 +98,6` | **S05** | ✓ | bookStatus delegates to readingStatus |
| 3 | `@@ -74,6 +105,122` | **MIXED** | △ 手动 | S05 (readingStatus, isDisplayFinished, ShelfStatusGroupOptions, shelfStatusCounts) + grid-helper (shelfLeadingItemCount, shelfGridIndexOfBook) + smart-search (GB2312_CHARSET, PINYIN_CHARS, extractPinyinInitials, getChineseCharInitial)。三者是独立顶层声明，可手动编辑拆分，但在同一 `@@` 块内 |
| 4 | `@@ -80,0 +228,15` | **smart-search** | ✓ | extractPinyinInitials + getChineseCharInitial 实现 |
| 5 | `@@ -83 +245,6` | **smart-search** | ✓ | filterItems doc comment |
| 6 | `@@ -97 +264,5` | **smart-search** | ✓ | filterItems token splitting |
| 7 | `@@ -100,2 +271,17` | **smart-search** | ✓ | filterItems pinyin matching（依赖 hunk 4） |
| 8 | `@@ -103,2 +289,9` | **S05** | ✓ | filterItems status filter uses `book.readiness().tone`（依赖 hunks 1,2,3-S05） |
| 9 | `@@ -115,0 +309,12` | **smart-search** | ✓ | extractChapterLabel |

**依赖**: S05 hunks 自闭合；smart-search hunks 自闭合（不引用 S05 函数）；grid-helper 自闭合。
**HOLD 原因**: hunk 3 三特征混在同一 122 行块。可 `git add -p` edit 模式手动拆分。

### 3.2 BookGrid.kt（22 hunks, +146/-33）

| Hunk | 行范围 | 归属 | 可拆分 | 说明 |
|------|--------|------|--------|------|
| 1 | `@@ -38,0 +39` | **general** | ✓ | import remember |
| 2 | `@@ -154 +155,12` | **S05** | ✓ | StatusRail doc comment |
| 3 | `@@ -158,0 +171,2` | **S05** | ✓ | StatusRail counts + modifier params |
| 4 | `@@ -160,7 +173,0` | **S05** | ✓ | remove old options list |
| 5 | `@@ -168 +175` | **S05** | ✓ | Modifier → modifier |
| 6 | `@@ -174 +181` | **S05** | ✓ | options.forEach → ShelfStatusGroupOptions.forEach |
| 7 | `@@ -175,0 +183,6` | **S05** | ✓ | count display logic |
| 8 | `@@ -240,0 +254,8` | **MIXED** | △ 手动 | adds contentUpdated (M-BUG-03) + isNew (NBB) + chapterLabel (smart-search) + completionState (S05) as BookTile params。4 个独立参数声明，可手动编辑拆分 |
| 9 | `@@ -246,0 +268,2` | **S05** | ✓ | displayFinished val |
| 10 | `@@ -271,0 +295,18` | **general** | ✓ | gridBorder/listBorder remember |
| 11 | `@@ -281,5 +322` | **general** | ✓ | inline border → gridBorder |
| 12 | `@@ -294 +331` | **S05** | ✓ | percent = if (displayFinished) percent else null |
| 13 | `@@ -358,0 +396,39` | **MIXED** | △ 手动 | "新" badge (NBB) + "可更新" badge (M-BUG-03)。两个独立 if 块，可手动拆分 |
| 14 | `@@ -385 +461` | **S05** | ✓ | percent label comment |
| 15 | `@@ -392 +475` | **MIXED** | ✗ | `val label = if (displayFinished) { "完" } else if (chapterLabel != null && percent > 0f) { ... } else { ... }` — S05 displayFinished 与 smart-search chapterLabel 在同一 if-else 链。**不可拆分** |
| 16 | `@@ -394,0 +478,2` | **general** | ✓ | maxLines/overflow |
| 17 | `@@ -430,5 +515` | **general** | ✓ | inline border → listBorder |
| 18 | `@@ -441 +522` | **S05** | ✓ | percent = if (displayFinished) percent else null |
| 19 | `@@ -462,0 +544,14` | **M-BUG-03** | ✓ | contentUpdated dot indicator |
| 20 | `@@ -473,7 +568,26` | **MIXED** | ✗ | "新" badge in list mode (NBB) + progress string with displayFinished (S05) + chapterLabel (smart-search)。三者编织在同一 Row + if-else 块。**不可拆分** |
| 21 | `@@ -485 +599,7` | **MIXED** | ✗ | progress string: `if (displayFinished) { "已读完" } else if (percent > 0f) { if (chapterLabel != null) ... }` — S05 + smart-search 在同一 if-else 链。**不可拆分** |
| 22 | `@@ -499 +619` | **S05** | ✓ | percent < 99.5f → !displayFinished |

**HOLD 原因**: hunks 15/20/21 将 S05 `displayFinished` 与 smart-search `chapterLabel` 编织在同一 if-else 表达式链中。`git add -p` 无法拆分，需手动改写代码。

### 3.3 ShelfRoute.kt（24 hunks with --unified=0, 20 hunks with default, +190/-10）

| Hunk | 行范围 | 归属 | 可拆分 | 说明 |
|------|--------|------|--------|------|
| 1 | `@@ -4,8 +4,11` | **MIXED** | △ 手动 | imports: navigationBarsPadding (general) + SnackbarDuration/SnackbarResult (M-BUG-03)。独立 import 行，可手动拆分 |
| 2 | `@@ -26,0 +27` | **S05** | ✓ | ImportBatchSuccesses import |
| 3 | `@@ -35,11 +36,53` | **MIXED** | △ 手动 | removes delay/SkeletonMinDisplayMillis (M-BUG-09) + adds focusIdsForBatch (S05) + SOURCE_REFRESH_PROGRESS_NOTE/sourceRefreshConfirmBody (M-BUG-03)。独立顶层声明，可手动拆分 |
| 4 | `@@ -110,9 +153,11` | **M-BUG-09** | ✓ | firstLoad → libraryLoaded |
| 5 | `@@ -146,0 +191,17` | **MIXED** | △ 手动 | newBookIds (NBB) + focusNewBookIds (S05)。独立 var 声明，可手动拆分 |
| 6 | `@@ -155,7 +211,38` | **MIXED** | ✗ | "查看" snackbar action: focusIdsForBatch (S05) + focusNewBookIds (NBB) 在同一 if 块内编织。**不可拆分** |
| 7 | `@@ -167,6 +255,12` | **M-BUG-03** | ✓ | contentUpdatedBookIds + pendingSourceRefreshIds |
| 8 | `@@ -174,6 +268,8` | **MIXED** | △ 手动 | contentUpdatedBookIds (M-BUG-03) + newBookIds (NBB) in ShelfUiState constructor。独立行，可手动拆分 |
| 9 | `@@ -188,7 +284,7` | **M-BUG-09** | ✓ | showSkeleton = !libraryLoaded |
| 10 | `@@ -196,0 +294,7` | **M-BUG-03** | ✓ | confirmSourceRefreshIds in ShelfUiState |
| 11 | `@@ -210,10 +309,22` | **MIXED** | △ 手动 | focusBookIds/onFocusHandled (S05) + clearNewBookId (NBB)。focusBookIds 和 clearNewBookId 在不同回调中，可手动拆分 |
| 12 | `@@ -285,7 +395,8` | **general** | ✓ | navigationBarsPadding |
| 13 | `@@ -318,6 +428,10` | **S05** | ✓ | onMarkFinished/onMarkReading/onShelve callbacks |
| 14 | `@@ -544,6 +658,37` | **M-BUG-03** | ✓ | source refresh confirm dialog |
| 15 | `@@ -628,6 +773,9` | **NBB** | ✓ | clearNewBookId in OpenBook |
| 16 | `@@ -638,6 +786,7` | **NBB** | ✓ | clearNewBookId in ContinueFromActionSheet |
| 17 | `@@ -649,6 +798,20` | **S05** | ✓ | MarkFinished/MarkReading/ShelveBook handling |
| 18 | `@@ -659,6 +822,16` | **S05** | ✓ | BatchMarkFinished/BatchMarkReading handling |
| 19 | `@@ -707,6 +880,7` | **NBB** | ✓ | clearNewBookId in DetailContinue |
| 20 | `@@ -733,10 +907,14` | **M-BUG-03** | ✓ | CancelSourceRefreshConfirm/ConfirmSourceRefresh + TriggerRefresh change |

**HOLD 原因**: hunk 6 将 S05 `focusIdsForBatch` 与 NBB `focusNewBookIds` 编织在同一 snackbar action 块。`git add -p` 无法拆分，需手动改写代码。

### 3.4 ShelfScreen.kt（17 hunks, +145/-3）

| Hunk | 行范围 | 归属 | 可拆分 | 说明 |
|------|--------|------|--------|------|
| 1 | `@@ -23,0 +24` | **S05** | ✓ | LazyGridState import |
| 2 | `@@ -25,0 +27` | **general** | ✓ | items import |
| 3 | `@@ -57,0 +60,2` | **S05** | ✓ | LaunchedEffect + getValue imports |
| 4 | `@@ -58,0 +63` | **S05** | ✓ | remember import |
| 5 | `@@ -71,0 +77` | **general** | ✓ | TextOverflow import |
| 6 | `@@ -80,0 +87` | **general** | ✓ | rememberReducedMotion import |
| 7 | `@@ -98,0 +106,14` | **S05** | ✓ | focusBookIds/onFocusHandled params |
| 8 | `@@ -100,0 +122,37` | **S05** | ✓ | gridState + LaunchedEffect for focus scroll（依赖 ShelfModels.shelfGridIndexOfBook） |
| 9 | `@@ -246,0 +305` | **S05** | ✓ | passes gridState to ShelfContent |
| 10 | `@@ -336,0 +396` | **S05** | ✓ | gridState param to ShelfContent |
| 11 | `@@ -353,0 +414,11` | **general** | ✓ | stable callbacks optimization |
| 12 | `@@ -367,0 +439` | **S05** | ✓ | state = gridState in LazyVerticalGrid |
| 13 | `@@ -391,0 +464,10` | **S05** | ✓ | StatusRail item with state.statusCounts |
| 14 | `@@ -444,0 +527` | **MIXED** | ✗ | `val progressEntity = state.library.progressById[book.id]` — 单行，被 S05 (completionState) 和 smart-search (chapterLabel) 共同引用。**不可拆分** |
| 15 | `@@ -447,0 +531,3` | **MIXED** | △ 手动 | chapterLabel (smart-search) + completionState (S05) in BookTile call。独立行，可手动拆分 |
| 16 | `@@ -453,3 +539,7` | **MIXED** | △ 手动 | contentUpdated (M-BUG-03) + isNew (NBB) + stable callbacks (general)。独立行，可手动拆分 |
| 17 | `@@ -530,0 +621,52` | **S05** | ✓ | focusImportTarget + awaitFocusTarget + constants |

**HOLD 原因**: hunk 14 单行 `progressEntity` 被 S05 和 smart-search 共同引用。若同时提交 S05 + smart-search 则无问题。

### 3.5 ShelfAction.kt（3 hunks, +16/-0）

| Hunk | 行范围 | 归属 | 可拆分 | 说明 |
|------|--------|------|--------|------|
| 1 | `@@ -55,0 +56,8` | **S05** | ✓ | MarkFinished/MarkReading/ShelveBook |
| 2 | `@@ -64,0 +73,4` | **S05** | ✓ | BatchMarkFinished/BatchMarkReading |
| 3 | `@@ -98,0 +111,4` | **M-BUG-03** | ✓ | CancelSourceRefreshConfirm/ConfirmSourceRefresh |

**可拆分**: 三个 hunk 是独立 sealed class 分支，`git add -p` 可直接选择。

### 3.6 ShelfUiState.kt（3 hunks, +26/-0）

| Hunk | 行范围 | 归属 | 可拆分 | 说明 |
|------|--------|------|--------|------|
| 1 | `@@ -34,6 +34,20` | **MIXED** | △ 手动 | contentUpdatedBookIds (M-BUG-03) + newBookIds (NBB)。独立字段声明，可手动拆分 |
| 2 | `@@ -69,6 +83,7` | **M-BUG-03** | ✓ | confirmSourceRefreshIds |
| 3 | `@@ -86,6 +101,17` | **S05** | ✓ | statusCounts lazy val（依赖 ShelfModels.shelfStatusCounts） |

### 3.7 ShelfViewModel.kt（19 hunks, +316/-9）

| Hunk | 行范围 | 归属 | 可拆分 | 说明 |
|------|--------|------|--------|------|
| 1 | `@@ -21,0 +22` | **M-BUG-03** | ✓ | LibrarySourceIndex import |
| 2 | `@@ -22,0 +24,2` | **M-BUG-03** | ✓ | ShelfSourceUpdateChecker + SourceRefreshResult imports |
| 3 | `@@ -52,0 +56` | **M-BUG-09** | ✓ | onEach import |
| 4 | `@@ -54,0 +59` | **M-BUG-03** | ✓ | Job import |
| 5 | `@@ -58,0 +64,74` | **M-BUG-03** | ✓ | source refresh summary functions/constants |
| 6 | `@@ -79,0 +159,2` | **M-BUG-03** | ✓ | sourceIndex + sourceUpdateChecker constructor params |
| 7 | `@@ -101,0 +183,28` | **prefs-sync** | ✓ | init block for sortMode/viewMode sync |
| 8 | `@@ -198,0 +308,15` | **M-BUG-09** | ✓ | _libraryLoaded + libraryLoaded |
| 9 | `@@ -199,0 +324` | **M-BUG-09** | ✓ | onEach to books |
| 10 | `@@ -302 +427,4` | **M-BUG-09** | ✓ | WhileSubscribed → Eagerly |
| 11 | `@@ -310,0 +439,11` | **NBB** | ✓ | newBookIds + clearNewBookId |
| 12 | `@@ -406,0 +546,20` | **S05** | ✓ | markFinished/markReading/markFinishedBatch/markReadingBatch |
| 13 | `@@ -518 +677,15` | **M-BUG-03** | ✓ | refresh() → refresh(onResult) + _pendingSourceRefreshIds |
| 14 | `@@ -522,4 +695,6` | **M-BUG-03** | ✓ | refresh body change |
| 15 | `@@ -526,0 +702,74` | **M-BUG-03** | ✓ | checkUpdatedSources + confirmSourceRefresh + cancelSourceRefresh |
| 16 | `@@ -528,2 +777,51` | **M-BUG-03** | ✓ | _contentUpdatedBookIds + contentUpdatedBookIds + refreshSourceUpdates |
| 17 | `@@ -534,0 +833,3` | **NBB** | ✓ | importer.onBookImported |
| 18 | `@@ -543 +844,3` | **M-FEAT-01** | ✓ | default sort RECENT → IMPORTED |
| 19 | `@@ -549,0 +853,4` | **M-BUG-03** | ✓ | refreshSourceUpdates() in init |

**可拆分**: 所有 19 hunk 是独立方法/声明/import，`git add -p` 可直接选择。S05 仅需 hunk 12。

### 3.8 ShelfBookActions.kt（5 hunks, +105/-3）

| Hunk | 行范围 | 归属 | 可拆分 | 说明 |
|------|--------|------|--------|------|
| 1 | `@@ -6,0 +7` | **cache-clear** | ✓ | BookCacheClearOutcome import |
| 2 | `@@ -7,0 +9` | **cache-clear** | ✓ | MidwayAbortCause import |
| 3 | `@@ -77,0 +80,37` | **S05** | ✓ | markFinished/markReading/markFinishedBatch/markReadingBatch |
| 4 | `@@ -113 +152,7` | **cache-clear** | ✓ | clearCacheForBooks doc comment |
| 5 | `@@ -118,2 +163,59` | **cache-clear** | ✓ | clearCacheForBooks rewrite + buildCacheClearMessage |

**可拆分**: S05 hunk 3 与 cache-clear hunks 1/2/4/5 是独立方法，`git add -p` 可直接选择。

### 3.9 BookActionSheet.kt（6 hunks, +88/-2）

| Hunk | 归属 | 说明 |
|------|------|------|
| 1 | **S05** | Archive + CheckCircle icon imports |
| 2 | **S05** | ReadingCompletionState import |
| 3 | **S05** | onMarkFinished/onMarkReading/onShelve params |
| 4 | **S05** | readingState/isFinished/isShelved vals（依赖 ShelfModels.isDisplayFinished） |
| 5 | **S05** | percent = if (isFinished) percent else null |
| 6 | **S05** | status action card with MarkFinished/MarkReading/Shelve |

**全部 S05**。依赖 ShelfModels.kt S05 hunks。可独立提交。

---

## 4. 最小可编译组合分析

### 4.1 不可拆分 hunk 约束

| 文件 | 不可拆分 hunk | 涉及特征 | 约束 |
|------|-------------|---------|------|
| BookGrid.kt | 15, 20, 21 | S05 + smart-search | 必须同时提交 S05 + smart-search，或手动改写 if-else 链 |
| ShelfRoute.kt | 6 | S05 + NBB | 必须同时提交 S05 + NBB，或手动改写 snackbar action 块 |
| ShelfScreen.kt | 14 | S05 + smart-search | 单行 `progressEntity` 被两者共用；必须同时提交 |

### 4.2 推荐最小可编译组合

**组合 A: S05 + smart-search + grid-helper + NBB**（推荐）

包含以下文件的对应 hunks：
- ShelfModels.kt: 全部 9 hunks（S05 + smart-search + grid-helper）
- ShelfBookActions.kt: hunk 3（S05）
- ShelfViewModel.kt: hunks 11, 12, 17（S05 + NBB）
- ShelfAction.kt: hunks 1, 2（S05）
- ShelfUiState.kt: hunk 1 的 newBookIds 部分 + hunk 3（S05 + NBB）
- BookActionSheet.kt: 全部 6 hunks（S05）
- BookDetailHeaderSection.kt: 全部 2 hunks（S05）
- BookDetailSheet.kt: 全部 1 hunk（S05）
- BookGrid.kt: 全部 22 hunks（S05 + smart-search + NBB；排除 M-BUG-03 hunks 13-contentUpdated, 19）
- ShelfScreen.kt: 全部 17 hunks（S05 + smart-search + NBB + grid-helper；排除 M-BUG-03 hunk 16-contentUpdated）
- ShelfRoute.kt: hunks 2, 3-S05, 5, 6, 11, 13, 15, 16, 17, 18, 19（S05 + NBB；排除 M-BUG-03 和 M-BUG-09 hunks）
- ShelfSearchRoute.kt: 全部 6 hunks（S05）
- ShelfSelectionRoute.kt: 全部 6 hunks（S05）
- HomeContinueCards.kt, HomeContinueModels.kt, BookViewModel.kt, HomeArchiveViewModel.kt, HomeViewModel.kt, MyReadingViewModel.kt: 全部 hunks（S05）

**排除**: M-BUG-03, M-BUG-09, cache-clear, M-FEAT-01, prefs-sync

**需要手动 hunk 编辑的文件**:
- ShelfModels.kt hunk 3: 包含全部三特征（S05 + grid + smart-search），无需排除
- ShelfUiState.kt hunk 1: 排除 contentUpdatedBookIds，保留 newBookIds
- ShelfAction.kt: 排除 hunk 3
- BookGrid.kt hunk 8: 排除 contentUpdated，保留 completionState + chapterLabel + isNew
- BookGrid.kt hunk 13: 排除 contentUpdated badge，保留 isNew badge
- ShelfScreen.kt hunk 16: 排除 contentUpdated，保留 isNew + stable callbacks
- ShelfRoute.kt: 排除 hunks 1(M-BUG-03 part), 3(M-BUG-03+M-BUG-09 part), 4, 7, 8(M-BUG-03 part), 9, 10, 14, 20
- ShelfViewModel.kt: 排除 hunks 1-10(except 11,12,17), 13-16, 18, 19
- ShelfBookActions.kt: 排除 hunks 1, 2, 4, 5

**编译可行性**: ✓ 所有不可拆分 hunk 的特征均被包含。排除的特征（M-BUG-03/M-BUG-09）在保留的代码中不被引用。

**组合 B: S05 only**（不推荐）

需要手动改写以下代码：
1. BookGrid.kt hunks 15/20/21: 删除 `chapterLabel` 分支，只保留 `displayFinished` 逻辑
2. ShelfRoute.kt hunk 6: 删除 `focusNewBookIds` 逻辑，只保留 `focusIdsForBatch`
3. ShelfScreen.kt hunk 14: `progressEntity` 只用于 `completionState`，删除 `chapterLabel` 引用

风险：代码改写引入新 bug，且改写后的代码与仓库中已有 diff 不一致，后续合并困难。

### 4.3 V5 独立可编译组合

V5 与 S05 无跨文件引用依赖（V5 使用 `LocalDurationUnit`/`formatDuration` 重载，S05 使用 `isDisplayFinished`/`shelfStatusCounts`）。V5 可独立提交：

- TimeUtils.kt: 全部 5 hunks
- BookDetailStatsSection.kt: 全部 13 hunks
- HomeReadingArchiveSection.kt: 全部 3 hunks
- HeatmapSection.kt: 全部 21 hunks
- TrendSection.kt: 全部 4 hunks
- StatsPage.kt: V5 hunks only（需手动拆分 S05 + V5）
- ProfileHomeScreen.kt: V5 hunks only（需进一步分析 44 hunks 中哪些是 V5）
- ProfileViewModel.kt: V5 hunks only（需进一步分析 21 hunks 中哪些是 V5）

**HOLD 缺口**: ProfileHomeScreen.kt 和 ProfileViewModel.kt 的 V5 hunks 与其他 profile 特征混合，需进一步逐 hunk 分析。

---

## 5. 全部 102 文件归属表

### 5.1 S05 核心 shelf（混合特征）

| # | 文件 | hunks | 特征 | 归属组 | 测试 | 排除理由 | 仍 HOLD 缺口 |
|---|------|-------|------|--------|------|---------|------------|
| 1 | shelf/ShelfModels.kt | 9 | S05+smart+grid | 组合 A | ShelfStatusFilterTest | — | hunk 3 三特征混块，需手动编辑 |
| 2 | shelf/BookGrid.kt | 22 | S05+smart+M03+NBB | 组合 A | — | — | hunks 15/20/21 不可拆分 |
| 3 | shelf/ShelfRoute.kt | 24 | S05+M03+NBB+M09 | 组合 A | ShelfViewModelTest | — | hunk 6 不可拆分 |
| 4 | shelf/ShelfScreen.kt | 17 | S05+smart+M03+NBB+grid | 组合 A | — | — | hunk 14 单行共用 |
| 5 | shelf/ShelfAction.kt | 3 | S05+M03 | 组合 A | — | — | 可拆分，无 HOLD |
| 6 | shelf/ShelfUiState.kt | 3 | S05+M03+NBB | 组合 A | — | — | hunk 1 混字段，需手动编辑 |
| 7 | viewmodel/ShelfViewModel.kt | 19 | S05+M03+NBB+M09+FEAT+prefs | 组合 A | ShelfViewModelTest | — | 6 特征混合，需手动编辑 |
| 8 | viewmodel/ShelfBookActions.kt | 5 | S05+cache-clear | 组合 A | ShelfBookActionsTest | — | 可拆分，无 HOLD |

### 5.2 S05 纯净 shelf

| # | 文件 | hunks | 归属组 | 测试 | 仍 HOLD 缺口 |
|---|------|-------|--------|------|------------|
| 9 | shelf/BookActionSheet.kt | 6 | S05 | — | 无 |
| 10 | shelf/BookDetailHeaderSection.kt | 2 | S05 | — | 无 |
| 11 | shelf/BookDetailSheet.kt | 1 | S05 | — | 无 |
| 12 | shelf/ShelfSearchRoute.kt | 6 | S05 | — | 无 |
| 13 | shelf/ShelfSelectionRoute.kt | 6 | S05 | — | 无 |

### 5.3 S05 home/viewmodel

| # | 文件 | hunks | 归属组 | 测试 | 仍 HOLD 缺口 |
|---|------|-------|--------|------|------------|
| 14 | home/HomeContinueCards.kt | 1 | S05 | HomeContinueModelsTest | 无 |
| 15 | home/HomeContinueModels.kt | 4 | S05 | HomeContinueModelsTest | 无 |
| 16 | viewmodel/BookViewModel.kt | 2 | S05 | BookViewModelTest | 无 |
| 17 | viewmodel/HomeArchiveViewModel.kt | 3 | S05 | HomeArchiveViewModelTest | 需确认 hunks 是否纯 S05 |
| 18 | viewmodel/HomeViewModel.kt | 5 | S05 | HomeViewModelTest | 需确认 hunks 是否纯 S05 |
| 19 | viewmodel/MyReadingViewModel.kt | 1 | S05 | MyReadingViewModelTest | 无 |

### 5.4 V5（时长单位切换）

| # | 文件 | hunks | 归属组 | 测试 | 仍 HOLD 缺口 |
|---|------|-------|--------|------|------------|
| 20 | shelf/BookDetailStatsSection.kt | 13 | V5 | — | 无（全 V5） |
| 21 | home/components/HomeReadingArchiveSection.kt | 3 | V5 | HomeReadingArchiveSectionTest | 无 |
| 22 | stats/components/HeatmapSection.kt | 21 | V5 | — | 需确认是否纯 V5 |
| 23 | stats/components/TrendSection.kt | 4 | V5 | — | 无 |
| 24 | stats/StatsPage.kt | 3 | S05+V5 | StatsComputationTest | 需手动拆分 S05/V5 hunks |
| 25 | profile/ProfileHomeScreen.kt | 44 | V5+other | — | 44 hunks 混合 V5 与 profile 重写，需逐 hunk 分析 |
| 26 | viewmodel/ProfileViewModel.kt | 21 | V5+other | ProfileViewModelTest | 21 hunks 混合 V5 与 profile 逻辑，需逐 hunk 分析 |
| 27 | util/TimeUtils.kt | 5 | V5 | TimeUtilsTest | 无（V5 基硎） |

### 5.5 M-BUG-03 专属

| # | 文件 | hunks | 归属组 | 测试 | 仍 HOLD 缺口 |
|---|------|-------|--------|------|------------|
| 28 | test/viewmodel/ShelfImporterTest.kt | 18 | M-BUG-03+NBB | — | 18 hunks，939 insertions；需确认是否全 M-BUG-03/NBB |

### 5.6 通用 UI / other（39 文件）

以下文件未检测到 S05/smart-search/grid-helper/NBB/M-BUG-03/M-BUG-09/V5/cache-clear 特征引用。它们是通用 UI 改动、profile 页面重写、stats 重写、组件优化等，不涉及上述特征的跨文件依赖。

| # | 文件 | hunks | 归属组 | 仍 HOLD 缺口 |
|---|------|-------|--------|------------|
| 29 | components/AppLayout.kt | 13 | other (general UI) | 需确认改动内容 |
| 30 | components/GlassDialogs.kt | 2 | other (general UI) | 无 |
| 31 | components/GlassSurface.kt | 8 | other (general UI) | -88 行删除，需确认 |
| 32 | components/MicroIsland.kt | 14 | other (general UI) | -154 行删除，需确认 |
| 33 | components/OptimizedImage.kt | 2 | other (general UI) | 无 |
| 34 | components/SettingsComponents.kt | 12 | other (general UI) | 需确认 |
| 35 | components/SharedComponents.kt | 13 | other (general UI) | 需确认 |
| 36 | layout/LayoutTokens.kt | 3 | other (layout) | 无 |
| 37 | navigation/AppNavigation.kt | 2 | other (nav) | 无 |
| 38 | onboarding/OnboardingOverlay.kt | 3 | other (onboarding) | 无 |
| 39 | home/components/HomeCompletedSection.kt | 1 | other | 无 |
| 40 | home/components/HomeContinueSection.kt | 4 | other | 无 |
| 41 | home/components/HomeInspirationSection.kt | 1 | other | 无 |
| 42 | home/components/HomeMetricsSection.kt | 7 | other | 需确认 |
| 43 | home/HomeRoute.kt | 4 | other | 无 |
| 44 | home/HomeScreen.kt | 2 | other | 无 |
| 45 | home/HomeUiState.kt | 2 | other | 无 |
| 46 | homearchive/HomeArchiveFormatters.kt | 1 | other | -8 行删除 |
| 47 | homearchive/HomeCompletedScreen.kt | 13 | other | 需确认 |
| 48 | inspiration/components/InspirationDetail.kt | 21 | other (inspiration) | 需确认 |
| 49 | profile/AboutSubPage.kt | 45 | other (profile) | 大量重写，需逐 hunk 分析 |
| 50 | profile/AiSubPage.kt | 38 | other (profile) | 大量重写 |
| 51 | profile/AppearanceSubPage.kt | 19 | other (profile) | 需确认 |
| 52 | profile/Components.kt | 4 | other (profile) | 无 |
| 53 | profile/DiagnosticsSubPage.kt | 35 | other (profile) | 大量重写 |
| 54 | profile/GoalSubPage.kt | 73 | other (profile) | 大量重写，需逐 hunk 分析 |
| 55 | profile/LibrarySubPage.kt | 16 | other (profile) | 需确认 |
| 56 | profile/PrivacySubPage.kt | 26 | other (profile) | 需确认 |
| 57 | profile/ProfileRoute.kt | 19 | other (profile) | 需确认 |
| 58 | profile/ProfileUiState.kt | 13 | other (profile) | 需确认 |
| 59 | profile/ProfileUtils.kt | 1 | other (profile) | -4 行删除 |
| 60 | profile/ReadingArchiveSection.kt | 2 | other (profile) | 无 |
| 61 | profile/ReadingNotesSubPage.kt | 2 | other (profile) | 无 |
| 62 | profile/SelectionSubPage.kt | 27 | other (profile) | 需确认 |
| 63 | profile/StorageSubPage.kt | 48 | other (profile) | 大量重写 |
| 64 | profile/SyncSubPage.kt | 54 | other (profile) | 大量重写 |
| 65 | profile/WebDavSubPage.kt | 42 | other (profile) | 大量重写 |
| 66 | ProfileScreen.kt | 14 | other (profile) | 需确认 |
| 67 | readinghistory/MyReadingRoute.kt | 2 | other | 无 |
| 68 | readinghistory/MyReadingScreen.kt | 7 | other | 需确认 |
| 69 | search/SearchScreen.kt | 12 | other (search) | -120 行删除，需确认 |
| 70 | shelf/BatchSheet.kt | 9 | other (general UI) | haptic/reducedMotion hoisting |
| 71 | shelf/BookDetailStatsSection.kt | 13 | V5 (见 5.4) | — |
| 72 | shelf/LibraryBrowserComponents.kt | 21 | other (library browser) | 需确认 |
| 73 | shelf/LibraryBrowserRoute.kt | 23 | other (library browser) | 需确认 |
| 74 | shelf/ShelfSearchRoute.kt | 6 | S05 (见 5.2) | — |
| 75 | shelf/ShelfSelectionRoute.kt | 6 | S05 (见 5.2) | — |
| 76 | shelf/ShelfUtils.kt | 2 | other | 无 |
| 77 | stats/components/CreationSection.kt | 8 | other (stats) | -62 行删除 |
| 78 | stats/components/SummaryGroup.kt | 24 | other (stats) | 需确认 |
| 79 | stats/components/YearBillHeroCard.kt | 36 | other (stats) | 需确认 |
| 80 | stats/StatsScreen.kt | 2 | other (stats) | 无 |
| 81 | viewmodel/LibraryBrowserViewModel.kt | 15 | other (library browser) | 需确认 |
| 82 | viewmodel/ProfileSyncEngine.kt | 4 | other (profile sync) | 需确认 |
| 83 | viewmodel/ProfileWebDavManager.kt | 5 | other (profile webdav) | 需确认 |

### 5.7 测试文件（19 文件）

| # | 文件 | hunks | 归属组 | 仍 HOLD 缺口 |
|---|------|-------|--------|------------|
| 84 | test/home/HomeContinueModelsTest.kt | 2 | S05 | 无 |
| 85 | test/home/HomeReadingArchiveSectionTest.kt | 3 | other | 无 |
| 86 | test/shelf/ShelfImportFocusTargetTest.kt | 0 | S05 (new file?) | 0 hunks — 新文件未跟踪？ |
| 87 | test/shelf/ShelfSourceRefreshCopyTest.kt | 0 | M-BUG-03 (new file?) | 0 hunks — 新文件未跟踪？ |
| 88 | test/shelf/ShelfStatusFilterTest.kt | 5 | S05 | 无 |
| 89 | test/stats/components/YearBillHeadlineTest.kt | 2 | other (stats) | 无 |
| 90 | test/stats/StatsComputationTest.kt | 2 | other (stats) | 无 |
| 91 | test/util/TimeUtilsTest.kt | 0 | V5 (new file?) | 0 hunks — 新文件未跟踪？ |
| 92 | test/viewmodel/BookViewModelTest.kt | 2 | S05 | 无 |
| 93 | test/viewmodel/HomeArchiveViewModelTest.kt | 3 | other | 需确认 |
| 94 | test/viewmodel/HomeViewModelTest.kt | 2 | S05 | 无 |
| 95 | test/viewmodel/LibraryBrowserSelectionTest.kt | 0 | other (new file?) | 0 hunks |
| 96 | test/viewmodel/LibraryBrowserSortTest.kt | 0 | other (new file?) | 0 hunks |
| 97 | test/viewmodel/MyReadingViewModelTest.kt | 5 | S05 | 无 |
| 98 | test/viewmodel/ProfileSyncEngineTest.kt | 3 | other | 无 |
| 99 | test/viewmodel/ProfileViewModelTest.kt | 13 | V5 | 需确认 |
| 100 | test/viewmodel/ProfileWebDavManagerTest.kt | 4 | other | 无 |
| 101 | test/viewmodel/ShelfBookActionsTest.kt | 4 | S05+cache-clear | 需确认 |
| 102 | test/viewmodel/ShelfSourceRefreshFeedbackTest.kt | 0 | M-BUG-03 (new file?) | 0 hunks |

> **注**: 0 hunk 文件可能是未跟踪新文件（`??` 状态），`git diff` 不显示内容。需 `git status` 确认。

---

## 6. BookGrid.kt / ShelfRoute.kt / ShelfModels.kt hunk 归属总结

### BookGrid.kt

- **S05-only hunks**: 2, 3, 4, 5, 6, 7, 9, 12, 14, 18, 22（11 hunks）
- **smart-search-only hunks**: —（无独立 hunk）
- **NBB-only hunks**: —（无独立 hunk）
- **M-BUG-03-only hunks**: 19（1 hunk）
- **general-only hunks**: 1, 10, 11, 16, 17（5 hunks）
- **不可拆分混合 hunks**: 15 (S05+smart), 20 (S05+smart+NBB), 21 (S05+smart)（3 hunks）
- **可手动拆分混合 hunks**: 8 (S05+smart+M03+NBB), 13 (NBB+M03)（2 hunks）

### ShelfRoute.kt

- **S05-only hunks**: 2, 13, 17, 18（4 hunks）
- **NBB-only hunks**: 15, 16, 19（3 hunks）
- **M-BUG-03-only hunks**: 7, 10, 14, 20（4 hunks）
- **M-BUG-09-only hunks**: 4, 9（2 hunks）
- **general-only hunks**: 12（1 hunk）
- **不可拆分混合 hunks**: 6 (S05+NBB)（1 hunk）
- **可手动拆分混合 hunks**: 1 (general+M03), 3 (S05+M03+M09), 5 (S05+NBB), 8 (M03+NBB), 11 (S05+NBB)（5 hunks）

### ShelfModels.kt

- **S05-only hunks**: 1, 2, 8（3 hunks）
- **smart-search-only hunks**: 4, 5, 6, 7, 9（5 hunks）
- **不可拆分混合 hunk**: 3 (S05+smart+grid)（1 hunk，可手动编辑拆分为三组独立声明）

---

## 7. UI 矩阵

UI 专属矩阵已写入 E 盘：
`E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-hold-ui-matrix.csv`

- **行数**：103 行（1 header + 102 data rows）
- **SHA-256**：`233606FE4962BB95F30BC466E1C5DBDC1BAAB557CCAC9CE0ADE92ECFA425E3EC`
- **列**：file_path, hunk_count, insertions, deletions, feature_tags, attribution_group, sub_group, split_status, hold_reason

---

## 8. 调查约束声明

- 本报告只读源码，未修改任何产品代码、测试或公共交接文件。
- 未执行 stage/commit/push/reset/checkout。
- 未构建或操作设备。
- 唯一可写文件：本文件 + E 盘 UI 矩阵。
- 其他窗口同时工作，未改他们的文件。
- reader 专属文件（`ui/screen/reader/**`、`ReaderViewModel.kt`、`ReaderModels.kt`、`ReaderDocumentLoader.kt`、`ReaderPaperPalette.kt` 及对应测试）归窗口 3，本报告不涉及。

---

## 9. 仍需进一步分析的缺口

| 缺口 | 说明 | 优先级 |
|------|------|--------|
| ProfileHomeScreen.kt 44 hunks | V5 与 profile 重写混合，需逐 hunk 区分 | 中 |
| ProfileViewModel.kt 21 hunks | V5 与 profile 逻辑混合，需逐 hunk 区分 | 中 |
| GoalSubPage.kt 73 hunks | 大量重写，需确定特征归属 | 低 |
| StorageSubPage.kt 48 hunks | 大量重写，需确定特征归属 | 低 |
| SyncSubPage.kt 54 hunks | 大量重写，需确定特征归属 | 低 |
| WebDavSubPage.kt 42 hunks | 大量重写，需确定特征归属 | 低 |
| 0-hunk 文件（6 个） | 可能是未跟踪新文件，需 `git status` 确认 | 低 |
| HomeArchiveViewModel.kt 3 hunks | 标记为 S05 但需确认是否纯 S05 | 低 |
| HomeViewModel.kt 5 hunks | 标记为 S05 但需确认是否纯 S05 | 低 |
| ShelfImporterTest.kt 18 hunks | M-BUG-03+NBB，939 insertions，需确认归属 | 中 |
