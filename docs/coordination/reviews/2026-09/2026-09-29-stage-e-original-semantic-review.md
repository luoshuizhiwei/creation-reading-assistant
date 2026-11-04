# 阶段 E — 原目录独有内容语义复核

> **生成时间**: 2026-09-29
> **复核范围**: `orig-unique-full-reconciliation-corrected.csv` 中 23 个 LIKELY_COVERED + 42 个 NEED_REVIEW = 65 项
> **原目录**: `D:\develop\Code\Codex\creation-reading-assistant`
> **恢复目录**: `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> **修正 CSV**: `E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-recon\orig-unique-full-reconciliation-corrected.csv`（SHA-256 `E223726489155547E49E872AD8E0E3A3E4087919139CB2EFF64BCA1A1A53F34B`）
> **diff 索引**: `E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-recon\stage-e-semantic-diff-index.md`

## 1. CSV handling 字段 ".Count" 错误定位与修正

### 错误根因

生成 CSV 的 PowerShell 脚本中，`handling` 字段使用 `$($sample).Count` 形式的字符串插值。PowerShell 将 `$sample`（一个字符串）先插值为其文本内容，再追加字面量 `.Count`，而非计算 `$($sample.Count)`（字符串长度）。因此 65 行的 `handling` 字段均以 `.Count` 结尾。

### 修正方式

在 E 盘输出修正后的独立 CSV `orig-unique-full-reconciliation-corrected.csv`，不覆盖原件。修正后 `handling` 字段改为正确的中文描述文本。校验 65 行数据行、SHA-256 `E223726489155547E49E872AD8E0E3A3E4087919139CB2EFF64BCA1A1A53F34B`。

## 2. 语义复核方法

对 65 项逐文件读取原目录与恢复目录的实际源码，按重构模式分组比对 diff 与调用关系。**不使用** mtime、体积、简单行集合包含或单个符号命中代替语义判断；21 项行集合包含和 6 项迁移线索均逐项验证实际 diff 后才归类。

### 已验证的关键抽象（恢复目录新增）

| 抽象 | 位置 | 语义 |
|------|------|------|
| `isReadingFinished(progress)` | `ui/util/BookReadiness.kt` | `progress?.readingState == FINISHED`（显式状态，非百分比） |
| `isDisplayFinished(percent, completionState)` | `ui/screen/shelf/ShelfModels.kt` | `readingStatus(...) == COMPLETED` ≡ `fromStorage(completionState) == FINISHED` |
| `isReadingFinishedRow()` | `data/repository/StatsRepository.kt` | `fromStorage(completion_state) == FINISHED`（容忍 legacy `"completed"`） |
| `ThemeResolver.resolveAppDarkTheme(themeMode, isSystemDark)` | `data/settings/SettingsStore.kt` | `when (themeMode) { "dark" -> true; "light" -> false; else -> isSystemDark }` |
| `formatDuration(ms, unit, style)` | `ui/util/TimeUtils.kt` | 统一时长格式化，`DurationStyle`（NORMAL/COMPACT/TICK）× `DurationUnit`（HOURS/DAYS） |
| `ReaderTapZoneGeometry` | `feature/reader/pager/ReaderTapAction.kt` | `SIDE_WIDTH=1/3, CENTER_WIDTH=1/3, TOP_HEIGHT=0.12, BOTTOM_HEIGHT=0.12` |
| `ReaderTapTurnPlan` | `feature/reader/pager/PagedReaderPageSurface.kt` | sealed interface: None / ToggleControls / RequestTurn / ManualReveal |
| `DORMANT_IDS` | `data/settings/SelectionActionSettings.kt` | `setOf("edit")` — 过滤未接线的"编辑"动作 |

## 3. 65 项逐项判定

### 3.1 确认覆盖 — 65 项

以下 65 项原目录独有内容均被恢复目录等价替代或有意增强，无遗漏、无需保留合并。

#### A. S05-① 完读判定重构（11 项）

`percent >= 99.5f` / `completion_state == "finished" || progress_percent >= 99.5f` → 显式 `FINISHED` 状态判定。S05-① 契约：完读由显式状态决定，百分比不再推断完成；取消读完后的残余 100% + `reading` 归入在读。

| # | 路径 | orig→rec 关键变化 |
|---|------|-------------------|
| 1 | `android/app/src/main/java/.../ui/screen/shelf/BookActionSheet.kt` | `readingState == FINISHED \|\| percent >= 99.5f` → `isDisplayFinished(percent, completionState)`；BookCover seal 改为仅 finished 时显示 |
| 2 | `android/app/src/main/java/.../ui/screen/shelf/BookGrid.kt` | 三处 `percent >= 99.5f` → `displayFinished`；新增 `completionState` 参数透传 |
| 3 | `android/app/src/main/java/.../ui/screen/shelf/ShelfModels.kt` | `bookStatus` 中 `p >= 99.5f -> COMPLETED` 分支删除 → `readingStatus` 按显式状态判定；新增 `isDisplayFinished`；`filterItems` 加 `READABLE` 特例补偿 |
| 4 | `android/app/src/main/java/.../ui/screen/shelf/ShelfSearchRoute.kt` | 无 `99.5f` 字面量，通过 `bookStatus` 签名继承 S05-① 行为 |
| 5 | `android/app/src/main/java/.../ui/screen/shelf/ShelfSelectionRoute.kt` | 两处 `progressPercent >= 99.5f` → `isDisplayFinished(progressPercent, completionState)` |
| 6 | `android/app/src/main/java/.../ui/viewmodel/HomeViewModel.kt` | `completion_state == "finished" \|\| progress_percent >= 99.5f` → `isReadingFinished(progress)`（完读书过滤 + 继续阅读排除） |
| 7 | `android/app/src/main/java/.../ui/viewmodel/ProfileViewModel.kt` | `completion_state == "finished" \|\| progress_percent >= 99.5f` → `isReadingFinishedRow()` |
| 8 | `android/app/src/test/java/.../data/repository/BookRepositoryTest.kt` | 原断言全保留（数据层 99.5% → finished 不变）；新增 cancel/shelve 回归测试 |
| 9 | `android/app/src/test/java/.../ui/screen/ShelfStatusFilterTest.kt` | 旧 `99.5f → COMPLETED` 断言 → 显式 `finished` 断言；新增 cancel-finished + badge-matrix 测试 |
| 10 | `android/app/src/test/java/.../ui/screen/shelf/ShelfStatusCountsTest.kt` | fixture 从 `100f + reading` 改为显式 `finished`；新增 cancel-finished + READABLE-filter 测试 |
| 11 | `android/app/src/test/java/.../ui/viewmodel/ProfileViewModelTest.kt` | b2 从 `99.7f + reading` 改为 `finished`；新增 b4 `100f + reading` 不算完读 |

#### B. formatDuration 统一迁移（10 项）

各页面本地 `formatCompactDuration` / `formatDuration` → 统一 `ui.util.formatDuration(ms, unit, style)` + `LocalDurationUnit` + `durationUnitToggle()`。行为差异（子分钟显示"0 分钟"而非秒、向下取整而非四舍五入、中文单位）为有意统一，KDoc 明确记载。

| # | 路径 | orig→rec 关键变化 |
|---|------|-------------------|
| 12 | `android/app/src/main/java/.../ui/screen/home/components/HomeMetricsSection.kt` | 本地 `formatCompactDuration`（秒/h m/round）→ `formatDuration(ms, unit, COMPACT)` + `durationUnitToggle` |
| 13 | `android/app/src/main/java/.../ui/screen/profile/ProfileHomeScreen.kt` | 本地 `when` 分支 → `formatDuration` + `durationUnitToggle` |
| 14 | `android/app/src/main/java/.../ui/screen/profile/ProfileUtils.kt` | 委托函数删除，调用方直接 import `ui.util.formatDuration` |
| 15 | `android/app/src/main/java/.../ui/screen/readinghistory/MyReadingScreen.kt` | `DurationText(ms=...)` → `formatDuration` + `LocalDurationUnit` + `combinedClickable` 加 `onClickLabel` |
| 16 | `android/app/src/main/java/.../ui/screen/shelf/BookDetailStatsSection.kt` | `DurationText(ms=...)` → `formatDuration` + `combinedClickable` + `testTag` |
| 17 | `android/app/src/main/java/.../ui/screen/stats/components/CreationSection.kt` | `formatCompactDuration(ms)` → `formatCompactDuration(ms, LocalDurationUnit.current)` + `durationUnitToggle` |
| 18 | `android/app/src/main/java/.../ui/screen/stats/components/HeatmapSection.kt` | 两处 `formatCompactDuration(ms)` → 带 `LocalDurationUnit.current` + `durationUnitToggle` |
| 19 | `android/app/src/main/java/.../ui/screen/stats/components/SummaryGroup.kt` | `formatCompactDuration(ms)` → 带 `LocalDurationUnit.current` + `durationUnitToggle` |
| 20 | `android/app/src/main/java/.../ui/screen/stats/components/YearBillHeroCard.kt` | 本地 `hoursFloat` 计算 → `formatDuration` + `LocalDurationUnit` + `durationUnitToggle` |
| 21 | `android/app/src/main/java/.../ui/util/TimeUtils.kt` | `formatDuration(ms, unit)` → `formatDuration(ms, unit, style)` 新增 `DurationStyle` 参数 |

#### C. ThemeResolver 提取（3 项）

内联 `when (themeMode)` → `ThemeResolver.resolveAppDarkTheme(themeMode, isSystemDark)`。纯提取，语义等价。

| # | 路径 | orig→rec 关键变化 |
|---|------|-------------------|
| 22 | `android/app/src/main/java/.../App.kt` | `BootMirror.applyApplicationNightMode(this, mode)` 无条件调用 → `if (mode != "system")` 条件调用（system 交系统处理） |
| 23 | `android/app/src/main/java/.../MainActivity.kt` | 两处内联 `when (themeMode)` → `ThemeResolver.resolveAppDarkTheme`；`dispatchKeyEvent` → `dispatchGenericMotionEvent`（滚轮事件走 generic motion） |
| 24 | `android/app/src/main/java/.../data/settings/SettingsStore.kt` | 内联 `when (themeMode)` → `ThemeResolver` object 提取 |

#### D. ReaderTapZoneGeometry / ReaderTapTurnPlan 提取（4 项）

内联 `height * 0.12f` / `width / 3f` → `ReaderTapZoneGeometry` 常量。PagedEpubContent / TapZonePreviewCard 视觉 weight 从 0.16/0.68/0.16 改为 1/3/1/3 以对齐实际点击边界。

| # | 路径 | orig→rec 关键变化 |
|---|------|-------------------|
| 25 | `android/app/src/main/java/.../feature/reader/pager/PagedReaderPageSurface.kt` | 内联 `when (resolveReaderTapAction(...))` → `ReaderTapTurnPlan` sealed interface |
| 26 | `android/app/src/main/java/.../feature/reader/pager/ReaderTapAction.kt` | 内联 `height * 0.12f` / `width / 3f` → `ReaderTapZoneGeometry` 常量 |
| 27 | `android/app/src/main/java/.../ui/screen/reader/content/PagedEpubContent.kt` | 内联 `.weight(0.16f)` / `.weight(0.68f)` → `ReaderTapZoneGeometry.horizontalWeights` |
| 28 | `android/app/src/main/java/.../ui/screen/reader/sheets/TapZonePreviewCard.kt` | 内联 `.weight(0.24f)` / `.weight(0.52f)` 等 → `ReaderTapZoneGeometry` weights + `testTag` |

#### E. DORMANT_IDS 过滤（3 项）

`ALL.filter { it.group != FIXED }` → `ALL.filter { it.group != FIXED && it.id !in DORMANT_IDS }`，DORMANT_IDS = {"edit"}。

| # | 路径 | orig→rec 关键变化 |
|---|------|-------------------|
| 29 | `android/app/src/main/java/.../data/settings/SelectionActionSettings.kt` | `pickableDefs` 加 `DORMANT_IDS` 过滤 |
| 30 | `android/app/src/test/java/.../data/settings/SelectionActionSettingsTest.kt` | 旧"edit renders between"测试 → "dormant edit is neither configurable nor renderable"测试 |
| 31 | `android/app/src/test/java/.../ui/screen/reader/ReaderSelectionToolbarModelTest.kt` | "pickable set excludes only fixed" → "pickable set excludes fixed and dormant" |

#### F. 搜索索引 / BookRepository / ShelfImporter 重构（7 项）

简单 `clearBookCache` → `BookCacheClearOutcome` + 拆留扫描；内联并发进度 → `Mutex`/`AtomicReference` 原子协调（S03-1）；导入 → `ImportBatchSuccesses` 原子批次发布（S05-④）。

| # | 路径 | orig→rec 关键变化 |
|---|------|-------------------|
| 32 | `android/app/src/main/java/.../data/local/dao/SearchTermDao.kt` | `deleteByChapter` / `countTerms` 删除（不再使用）；新增 `SELECT DISTINCT book_id FROM search_index_coverage` 孤儿回收 |
| 33 | `android/app/src/main/java/.../data/repository/BookRepository.kt` | `clearBookCache(id)` + `deleteBookContentFiles` → `BookCacheClearOutcome` + 拆留保护链 + `indexRefreshCoordinator` |
| 34 | `android/app/src/main/java/.../data/repository/SearchIndexRepository.kt` | 内联 `_progress.value` 并发 → `Mutex`/`AtomicReference` 原子协调（S03-1） |
| 35 | `android/app/src/main/java/.../feature/library/ShelfImporter.kt` | 来源基线更新保留；新增 `ImportBatchSuccesses` + `SearchIndexBookRefreshCoordinator` |
| 36 | `android/app/src/main/java/.../ui/viewmodel/ShelfBookActions.kt` | `ids.forEach { clearBookCache(it) }` → `BookCacheClearOutcome` + 拆留保护批量清理 |
| 37 | `android/app/src/main/java/.../ui/viewmodel/ShelfViewModel.kt` | `syncUpdatedSources` 保留；新增 `SOURCE_REFRESH_*` 常量 + `SourceRefreshFailureUi` |
| 38 | `android/app/src/test/java/.../feature/search/SearchIndexRepositorySearchTest.kt` | 旧 `deleteByChapter`/`countTerms` mock 删除；新增 coverage-only 孤儿回收测试 |

#### G. 阅读器 / 翻页 / 选区重构（18 项）

| # | 路径 | orig→rec 关键变化 |
|---|------|-------------------|
| 39 | `android/app/src/main/java/.../feature/reader/pager/PageTurner.kt` | `LaunchedEffect(turnRequest)` → `PendingTurnQueue` + `TurnGate` + `turnEpoch`（S06-⑤ 翻页排队） |
| 40 | `android/app/src/main/java/.../feature/sync/LegadoReadingProgressImporter.kt` | `progressDao.upsert(mergeReaderProgress(...))` → 先 `val merged` 再安全检查（导入不推导 FINISHED） |
| 41 | `android/app/src/main/java/.../ui/components/CraSlider.kt` | 内联 slider 几何 → `computeCraSliderTrackGeometry` + RTL 支持 |
| 42 | `android/app/src/main/java/.../ui/screen/homearchive/HomeCompletedScreen.kt` | `bookOps.reselectFile` + `DurationText` → `SnackbarHost` + `formatDuration` + `durationUnitPill` |
| 43 | `android/app/src/main/java/.../ui/screen/profile/ProfileRoute.kt` | 消息文案更新（新增"笔记、高亮、标签、分类、书单、正文规则与来源引用"）；"已清理缓存" → "已清理阅读器临时缓存" |
| 44 | `android/app/src/main/java/.../ui/screen/profile/StorageSubPage.kt` | `booksDir.deleteRecursively()` → `OfflineBooksResidueClear` + 拆留保护消息 |
| 45 | `android/app/src/main/java/.../ui/screen/reader/ReaderLayerBuilders.kt` | `SetSelectedText` 统一 → 空文本先 `ClearSelection`（S07-F2）+ `readerCopyNotice` |
| 46 | `android/app/src/main/java/.../ui/screen/reader/ReaderScreen.kt` | `nav.persistCurrentProgress()` → 加 `!LegadoMarkerSaveGuard.isArmed(bid)` 守卫（S12） |
| 47 | `android/app/src/main/java/.../ui/screen/reader/ReaderSelectionExternalActions.kt` | "正在打开…"预报 + 失败 → 仅结果通知（"已打开"/"未找到"），消除双提示 |
| 48 | `android/app/src/main/java/.../ui/screen/reader/ReaderSheetHost.kt` | `onJumpToHighlight`/`onJumpToBookmark` 分离回调 → `onJumpToTypedAnnotation` 统一 typed 回源（A01） |
| 49 | `android/app/src/main/java/.../ui/screen/reader/sheets/ReaderDisplaySettings.kt` | 内联 `onChange(settings.copy(...))` → `HeaderFooterPicker` + `onTemplateChange` |
| 50 | `android/app/src/main/java/.../ui/screen/reader/sheets/ReaderSettingsSheet.kt` | 新增 `readerSettingsScopeNotice` 页级作用域说明（D-2-B） |
| 51 | `android/app/src/main/java/.../ui/screen/reader/sheets/ReaderTocSheet.kt` | `notes: List<NoteEntity>` → `notes: List<TocNoteEntry>`（typed 条目，无默认值强制接线） |
| 52 | `android/app/src/main/java/.../ui/screen/shelf/ShelfRoute.kt` | `focusNewBookId: String?` → `ImportBatchSuccesses` + `focusIdsForBatch` 批次焦点 |
| 53 | `android/app/src/main/java/.../ui/screen/shelf/ShelfScreen.kt` | `focusBookId: String?` → `focusBookIds: List<String>` + `onFocusHandled: (Boolean) -> Unit` |
| 54 | `android/app/src/main/java/.../ui/viewmodel/LibraryBrowserViewModel.kt` | 查询变更不清选 → 查询变更清选（防止隐藏结果残留导入选择） |
| 55 | `android/app/src/main/java/.../ui/viewmodel/ReaderDocumentLoader.kt` | `fileIndex` 透传 → 流式章节检测 + `LegadoChapterPosition`（S12-P2-2） |
| 56 | `android/app/src/main/java/.../ui/viewmodel/ReaderViewModel.kt` | `readerChromeReducer` 保留；新增 `PagedReplacementAvailability` + 统一选区清除（S07-F2） |

#### H. 其他（9 项）

| # | 路径 | orig→rec 关键变化 |
|---|------|-------------------|
| 57 | `android/app/build.gradle.kts` | `AndroidJUnitRunner` → `CraIsolatedTestRunner` + `testBuildType` + `qahost` build type |
| 58 | `android/app/src/main/java/.../ui/screen/home/components/HomeContinueSection.kt` | `formatBookProgressForCard(pct)` → `formatBookProgressForCard(pct, progress?.completion_state)`（S05-①） |
| 59 | `android/app/src/main/java/.../ui/screen/reader/ReaderContentHostEpubBranch.kt` | `text = ann` + `onSelect` → 新增 `epubTextBlockSourceRange` 源半开区间（S07-F3） |
| 60 | `android/app/src/main/java/.../ui/screen/reader/ReaderContentHostPlainTextBranch.kt` | `selectedRangeStart in ...` → 新增 `rememberUpdatedState` + `snapshotFlow` 可见性失效（S07-F5） |
| 61 | `android/app/src/main/java/.../ui/screen/reader/ReaderProgressEffects.kt` | `LaunchedEffect` key 调整 → 新增 `ReaderLoadedContent` + 替换可用性效果 |
| 62 | `android/app/src/main/java/.../ui/screen/reader/sheets/ReaderNotesSheet.kt` | `onJumpToBookmark: (NoteEntity) -> Unit` → `onJumpToNote: (NoteEntity) -> Unit`（typed 回源 A01） |
| 63 | `android/app/src/main/java/.../ui/util/DurationDisplay.kt` | KDoc 更新（新增"首页"到页面清单）+ `DURATION_UNIT_TOGGLE_LABEL` |
| 64 | `android/app/src/test/java/.../ui/viewmodel/ShelfImporterTest.kt` | `bookDao = mockk()` 等 fixture → `SearchIndexBookRefreshCoordinator` + 新增断言 |
| 65 | `android/app/src/test/java/.../ui/viewmodel/ShelfViewModelTest.kt` | `clearBookCache returns Unit` → `BookCacheClearOutcome` mock + `SearchIndexBookRefreshCoordinator` |

#### I. 文档（2 项）

| # | 路径 | orig→rec 关键变化 |
|---|------|-------------------|
| 66 | `docs/handoff/current.md` | adb instrument 命令行 → 阶段 D/E 复核进度记录（历史快照保留，新增进度段） |
| 67 | `docs/research/2026-09-19-android-worktree-product-inventory.md` | "只读盘点" + "G0 阻塞" → "旁路恢复" + "G0 已完成" + clone 路径记录 |

### 3.2 需保留合并 — 0 项

无。

### 3.3 仍无法确认 — 0 项

无。

## 4. 原目录独有 Electron 文件（不在 65 项内，须保留）

以下 3 个文件仅存在于原目录 `electron/main/creation-workspace/`，恢复目录无对应文件。属桌面端代码，**不混入 Android 提交**，须单独保留。

| # | 路径 |
|---|------|
| 1 | `electron/main/creation-workspace/bundle-merge.ts` |
| 2 | `electron/main/creation-workspace/proof-engine.ts` |
| 3 | `electron/main/creation-workspace/validate.ts` |

## 5. 原目录独有文档（不在 65 项内，须保留）

以下 3 个文档仅存在于原目录 `docs/`，恢复目录无对应文件。须单独保留。

| # | 路径 |
|---|------|
| 1 | `docs/plans/2026-09-21-desktop-main-process-split-proposal.md` |
| 2 | `docs/qa/2026-09-26-s04-epub-image-cache-mumu-portrait-supplement.md` |
| 3 | `docs/release/ANDROID_RELEASE.md` |

## 6. 汇总

| 判定 | 数量 |
|------|------|
| 确认覆盖 | **65** |
| 需保留合并 | **0** |
| 仍无法确认 | **0** |
| **合计** | **65** |

原目录独有 Electron 文件：**3**（须保留，不混入 Android 提交）
原目录独有文档：**3**（须保留）

## 7. 约束遵守

- 未修改恢复仓库或原仓库任何源码
- 未 stage、commit、push、reset、checkout
- 未操作设备
- 未修改其他窗口的报告
- 唯一可写仓库文件：本报告
- CSV 和 diff 索引写 E 盘
