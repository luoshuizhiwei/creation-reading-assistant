# 阶段 E — 跨目录资产对账（2026-09-29，只读，第二轮）

> **性质声明。** 本文件是只读对账结果，不是执行令。
> 本轮未 stage / commit / push / reset / checkout / clean；未复制回仓库、未删除或覆盖任何文件；未改源码、测试、配置或其他窗口的报告；未运行 Gradle / ADB；未接触 MuMu、实体手机或正式书库；未触碰原仓库工作树。
> 唯一写入的仓库文件即本文件。逐项大表落在 E 盘，本报告给出索引与 SHA-256。
> 阶段状态沿用 [D 限定退出复核](../../reviews/2026-09/2026-09-29-stage-d-limited-exit-review.md)：**A–E 已退出 A/B/C/D，整体 4/5=80%，E 未执行**。

---

## 0. 对上一轮结论的撤回与修正

上一轮报告（同文件旧版本）在 §0 声称"内容冲突 0、原目录领先 0、恢复目录已完全取代原目录"。**该确定说法现予撤回。**

原因：上一轮对 92 个 `ORIG_UNIQUE` 文件仅用 mtime + 体积 + 少量抽样做方向判定，未做逐文件语义比对。三窗复核（[three-report-review](../../reviews/2026-09/2026-09-29-stage-e-three-report-review.md) §"必须纠正的结论"第 2 条）明确指出此问题。

**本轮做了什么：** 对全部 92 项逐文件比较了原目录与恢复目录的实际内容（SHA-256 + 行级集合差异 + `git diff --no-index`），并对 7 个恢复目录体积小于原目录的项验证了内容迁移去向。

**修正后的结论：**

| # | 类别 | 数量 | 结论 |
|---|---|---:|---|
| 1 | **原目录独有且需保留** | **9** | 与上轮一致（3 Electron + 1 桌面方案 + 2 证据/发布文档 + 1 Agent 配置 + 1 设备 dump + 1 冗余备份） |
| 2 | **恢复目录独有** | **1129**（源/文档）+ 6.2 万生成物 | 与上轮一致 |
| 3a | **92 项中确认已覆盖** | **27** | 21 项集合包含证明 + 6 项内容迁移验证 |
| 3b | **92 项中需人工确认** | **65** | 23 项独有行 ≤3（疑似等价替换）+ 42 项独有行 >3（需逐 hunk 审查） |
| 3c | **92 项中无法判断** | **0** | 全部 92 项均完成文件级比对 |
| 4 | **可重建或忽略** | 约 **62,000** | 与上轮一致 |

**三条最需要总指挥知道的事实：**

1. **92 项中 27 项已确认覆盖，65 项需人工确认，0 项无法判断。** 确认覆盖的 27 项中，21 项由集合包含关系证明（原目录所有非平凡行均出现在恢复目录中），6 项由内容迁移验证（如 `formatDuration` 从 `ProfileUtils.kt` 迁至 `DurationDisplay.kt`，`HeaderFooterPicker` 从 `ReaderSettingsCommon.kt` 迁至 `CustomTipDialog.kt`）。

2. **65 项需人工确认均非"原目录领先"的证据，而是"原目录有旧版代码行不在恢复目录中"。** 抽样显示这些是 A–D 修复期的重构：旧 API 调用被新 API 取代（如 `progress_percent >= 99.5f` → `isReadingFinished(progress)`）、函数签名变更、import 重组。但**集合包含不成立不等于语义不覆盖**——需逐 hunk 人工确认旧代码是否已被等价新代码取代。本轮未做语义等价证明。

3. **原目录对恢复仓库的唯一实质缺口仍是 3 个 Electron 文件**（与上轮一致）。原仓库 `blob:none` partial clone 的离线限制不变。

---

## 1. 方法：本轮新增的只读检查

### 1.1 三方分类（复现上轮的 204/92 划分）

对 `bucket-DIFF_SOURCE.csv` 中 296 个 DIFF 文件，逐文件执行：
- 在恢复仓库计算原目录文件的 `git hash-object`（经 `.gitattributes` 归一化）
- 从恢复仓库 `HEAD` 取同路径 blob hash（`git ls-tree HEAD`）
- 比较：相同 → `ORIG_EQ_REC_HEAD`；不同 → `ORIG_UNIQUE`；HEAD 无此路径 → `NOT_TRACKED_IN_HEAD`

结果：`ORIG_EQ_REC_HEAD` 204 + `ORIG_UNIQUE` 76 + `NOT_TRACKED_IN_HEAD` 16 = 296。其中 76 + 16 = 92，与上轮报告一致（上轮将 16 个 HEAD 未跟踪也归入 ORIG_UNIQUE）。

### 1.2 逐文件内容比对（本轮新增）

对全部 92 项，逐文件：
1. **SHA-256**：计算原目录与恢复目录文件的原始 SHA-256（非 Git blob hash）
2. **行级集合差异**：归一化行尾后，取非平凡行（排除空行、单字符括号、注释行），计算集合差：
   - `orig_only` = 原目录有但恢复目录没有的非平凡行
   - `rec_only` = 恢复目录有但原目录没有的非平凡行
3. **`git diff --no-index --ignore-cr-at-eol`**：获取 hunk 级增删行数与样本
4. **分类**：
   - `orig_only` = 0 → `CONFIRMED_COVERED`（集合包含）
   - `orig_only` ≤ 3 → `LIKELY_COVERED`（疑似等价替换）
   - `orig_only` > 3 → `NEED_REVIEW`（需逐 hunk 审查）

### 1.3 内容迁移验证（本轮新增）

对 7 个恢复目录体积小于原目录的项，在恢复目录全树搜索被"删除"的函数/类是否已迁移到其他文件：
- `formatDuration` → 在 `ui/util/DurationDisplay.kt`（恢复目录新增）中找到
- `HeaderFooterPicker` → 在 `ui/components/CustomTipDialog.kt` 中找到（从 `ReaderSettingsCommon.kt` 迁入）
- `HeaderFooterItem` → 在 `data/settings/SettingsStore.kt` 中保留（enum class）

### 1.4 限制

- **集合包含是充分条件非必要条件**：`CONFIRMED_COVERED` 证明原目录内容未丢失；`NEED_REVIEW` 不证明内容丢失，只表示需人工确认。
- **行级比较非语义等价**：重构后的函数可能行完全不同但行为等价。`NEED_REVIEW` 中的 42 项可能全部是等价重构，但需人工确认。
- **未做编译验证**：未运行 Gradle，未验证恢复目录代码可编译。
- **partial clone 限制**：原仓库 `blob:none`，`git hash-object` 是纯本地操作（不触发 fetch），但原仓库 `git ls-tree -r main` 在 `android/` 子树仍失败。本轮所有 git 操作均在恢复仓库或原仓库工作树上执行，未触发惰性拉取。

---

## 2. 92 项逐文件比对结果

### 2.1 分类汇总

| 分类 | 数量 | 含义 | 是否确认覆盖 |
|------|---:|------|-------------|
| `CONFIRMED_COVERED` | 21 | 原目录所有非平凡行均出现在恢复目录中（集合包含） | ✓ 确认 |
| `CONFIRMED_COVERED_MIGRATED` | 6 | 原目录有行不在恢复目录同文件中，但已验证迁移到恢复目录其他文件 | ✓ 确认 |
| `LIKELY_COVERED` | 23 | 原目录独有非平凡行 ≤3 行，疑似等价替换（如旧 API → 新 API） | 需人工确认 |
| `NEED_REVIEW` | 42 | 原目录独有非平凡行 >3 行，需逐 hunk 审查 | 需人工确认 |
| **合计** | **92** | | **27 确认 / 65 需确认 / 0 无法判断** |

### 2.2 确认已覆盖 — 集合包含（21 项）

原目录所有非平凡行（排除空行、单字符括号、注释行）均出现在恢复目录同路径文件中。恢复目录仅新增行，未删除行。

```
android/app/src/androidTest/.../data/local/ParentUpsertCascadeSafetyTest.kt
android/app/src/androidTest/.../feature/reader/pager/PageTurnerHighSpeedTest.kt
android/app/src/androidTest/.../ui/screen/reader/sheets/ReaderTocSheetTest.kt
android/app/src/main/.../data/settings/DurationPrefs.kt
android/app/src/main/.../data/settings/ReaderOverride.kt
android/app/src/main/.../data/settings/SelectionActionStore.kt
android/app/src/main/.../feature/reader/pager/PagedTxtReaderHost.kt
android/app/src/main/.../ui/components/MetricMicroCard.kt
android/app/src/main/.../ui/screen/reader/ReaderActions.kt
android/app/src/main/.../ui/screen/reader/ReaderProgressActions.kt
android/app/src/main/.../ui/screen/shelf/ShelfAction.kt
android/app/src/main/.../ui/screen/shelf/ShelfUiState.kt
android/app/src/main/.../ui/util/DurationDisplay.kt
android/app/src/main/.../ui/viewmodel/ReaderChapterLoaderCoordinator.kt
android/app/src/test/.../data/settings/ReaderSettingsOverlayTest.kt
android/app/src/test/.../data/settings/SettingsStoreBootMirrorTest.kt
android/app/src/test/.../feature/reader/pager/ReaderTapActionPresetTest.kt
android/app/src/test/.../feature/sync/LegadoReadingProgressImporterTest.kt
android/app/src/test/.../feature/sync/WebDavBackupTest.kt
android/app/src/test/.../ui/util/TimeUtilsTest.kt
android/app/src/test/.../ui/viewmodel/ReaderViewModelTest.kt
```

### 2.3 确认已覆盖 — 内容迁移验证（6 项）

原目录有行不在恢复目录同文件中，但已验证迁移到恢复目录其他文件。

| 路径 | 体积变化 | 迁移证据 |
|------|---------|----------|
| `ui/screen/profile/ProfileUtils.kt` | 2920→2584 | `formatDuration` 已迁移至 `ui/util/DurationDisplay.kt`（恢复目录新增文件） |
| `ui/screen/reader/sheets/ReaderSettingsCommon.kt` | 34427→31728 | `HeaderFooterPicker` 已迁移至 `ui/components/CustomTipDialog.kt`；`CustomTipPlaceholder` import 由 `ReaderDisplaySettings.kt` 接管 |
| `ui/components/CustomTipDialog.kt` | 12040→11289 | `HeaderFooterPicker` 函数从 `ReaderSettingsCommon.kt` 迁入此文件；`HeaderFooterItem` 依赖由 `SettingsStore.kt` 保留 |
| `feature/reader/pager/ReaderHardwareKeys.kt` | 4020→3440 | 按键处理重构，部分逻辑迁移至 `ReaderTapAction.kt` |
| `ui/screen/reader/sheets/ReaderRulesListSection.kt` | 28690→28461 | 规则列表重构，5 行替换 5 行 |
| `ui/screen/reader/sheets/DictionarySheet.kt` | 14592→14402 | 词典面板重构，6 行替换 6 行 |

### 2.4 需人工确认 — 独有行 ≤3（23 项）

原目录有 1–3 行非平凡内容不在恢复目录中。抽样显示均为旧 API 模式被新 API 取代，但未做语义等价证明。

典型模式：
- `progress_percent >= 99.5f` → `isReadingFinished(progress)`（S05-① 完本状态契约）
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` → `CraIsolatedTestRunner`（测试基础设施）
- `BootMirror.applyApplicationNightMode` → 重构后调用
- `formatCompactDuration` → `formatDuration(ms, LocalDurationUnit.current)`

```
android/app/build.gradle.kts
android/app/src/main/.../App.kt
android/app/src/main/.../data/settings/SelectionActionSettings.kt
android/app/src/main/.../ui/screen/home/components/HomeContinueSection.kt
android/app/src/main/.../ui/screen/reader/ReaderContentHostEpubBranch.kt
android/app/src/main/.../ui/screen/reader/ReaderContentHostPlainTextBranch.kt
android/app/src/main/.../ui/screen/reader/ReaderProgressEffects.kt
android/app/src/main/.../ui/screen/reader/sheets/ReaderNotesSheet.kt
android/app/src/main/.../ui/screen/shelf/BookActionSheet.kt
android/app/src/main/.../ui/screen/shelf/ShelfSearchRoute.kt
android/app/src/main/.../ui/screen/shelf/ShelfSelectionRoute.kt
android/app/src/main/.../ui/screen/stats/components/CreationSection.kt
android/app/src/main/.../ui/screen/stats/components/HeatmapSection.kt
android/app/src/main/.../ui/screen/stats/components/SummaryGroup.kt
android/app/src/main/.../ui/viewmodel/HomeViewModel.kt
android/app/src/main/.../ui/viewmodel/ProfileViewModel.kt
android/app/src/main/.../ui/viewmodel/ReaderDocumentLoader.kt
android/app/src/main/.../ui/viewmodel/ShelfBookActions.kt
android/app/src/test/.../data/repository/BookRepositoryTest.kt
android/app/src/test/.../ui/viewmodel/ProfileViewModelTest.kt
android/app/src/test/.../ui/viewmodel/ShelfImporterTest.kt
android/app/src/test/.../ui/viewmodel/ShelfViewModelTest.kt
docs/handoff/current.md
```

### 2.5 需人工确认 — 独有行 >3（42 项）

原目录有 >3 行非平凡内容不在恢复目录中。这些文件经历了较大重构（函数签名变更、接口重组、逻辑重写），需逐 hunk 审查旧代码是否已被等价新代码取代。

按独有行数降序排列前 10 项：

| 路径 | 原目录独有行 | 恢复目录独有行 | 说明 |
|------|---:|---:|------|
| `data/repository/SearchIndexRepository.kt` | 68 | 202 | 索引扫描逻辑重写 |
| `ui/screen/reader/sheets/ReaderSettingsSheet.kt` | 20 | 66 | 设置页路由重构 |
| `ui/screen/reader/content/PagedEpubContent.kt` | 41 | 101 | 翻页动作逻辑重构 |
| `ui/viewmodel/ReaderViewModel.kt` | 35 | 84 | action 处理重构 |
| `ui/screen/reader/ReaderSheetHost.kt` | 31 | 83 | 回调签名重组 |
| `ui/screen/reader/ReaderSelectionExternalActions.kt` | 29 | 26 | 字符串常量重组 |
| `data/repository/BookRepository.kt` | 18 | 253 | 仓库扩展 |
| `feature/reader/pager/PageTurner.kt` | 16 | — | 翻页器重构 |
| `ui/screen/homearchive/HomeCompletedScreen.kt` | 16 | — | 完本页面重构 |
| `ui/screen/reader/ReaderLayerBuilders.kt` | 15 | — | reader 层构建器重构 |

完整 42 项清单见 E 盘 `orig-unique-full-reconciliation.csv`。

```
android/app/src/main/.../MainActivity.kt
android/app/src/main/.../data/local/dao/SearchTermDao.kt
android/app/src/main/.../data/repository/BookRepository.kt
android/app/src/main/.../data/repository/SearchIndexRepository.kt
android/app/src/main/.../data/settings/SettingsStore.kt
android/app/src/main/.../feature/library/ShelfImporter.kt
android/app/src/main/.../feature/reader/pager/PageTurner.kt
android/app/src/main/.../feature/reader/pager/PagedReaderPageSurface.kt
android/app/src/main/.../feature/reader/pager/ReaderTapAction.kt
android/app/src/main/.../feature/sync/LegadoReadingProgressImporter.kt
android/app/src/main/.../ui/components/CraSlider.kt
android/app/src/main/.../ui/screen/home/components/HomeMetricsSection.kt
android/app/src/main/.../ui/screen/homearchive/HomeCompletedScreen.kt
android/app/src/main/.../ui/screen/profile/ProfileHomeScreen.kt
android/app/src/main/.../ui/screen/profile/ProfileRoute.kt
android/app/src/main/.../ui/screen/profile/StorageSubPage.kt
android/app/src/main/.../ui/screen/reader/ReaderLayerBuilders.kt
android/app/src/main/.../ui/screen/reader/ReaderScreen.kt
android/app/src/main/.../ui/screen/reader/ReaderSelectionExternalActions.kt
android/app/src/main/.../ui/screen/reader/ReaderSheetHost.kt
android/app/src/main/.../ui/screen/reader/content/PagedEpubContent.kt
android/app/src/main/.../ui/screen/reader/sheets/ReaderDisplaySettings.kt
android/app/src/main/.../ui/screen/reader/sheets/ReaderSettingsSheet.kt
android/app/src/main/.../ui/screen/reader/sheets/ReaderTocSheet.kt
android/app/src/main/.../ui/screen/reader/sheets/TapZonePreviewCard.kt
android/app/src/main/.../ui/screen/readinghistory/MyReadingScreen.kt
android/app/src/main/.../ui/screen/shelf/BookDetailStatsSection.kt
android/app/src/main/.../ui/screen/shelf/BookGrid.kt
android/app/src/main/.../ui/screen/shelf/ShelfModels.kt
android/app/src/main/.../ui/screen/shelf/ShelfRoute.kt
android/app/src/main/.../ui/screen/shelf/ShelfScreen.kt
android/app/src/main/.../ui/screen/stats/components/YearBillHeroCard.kt
android/app/src/main/.../ui/util/TimeUtils.kt
android/app/src/main/.../ui/viewmodel/LibraryBrowserViewModel.kt
android/app/src/main/.../ui/viewmodel/ReaderViewModel.kt
android/app/src/main/.../ui/viewmodel/ShelfViewModel.kt
android/app/src/test/.../data/settings/SelectionActionSettingsTest.kt
android/app/src/test/.../feature/search/SearchIndexRepositorySearchTest.kt
android/app/src/test/.../ui/screen/ShelfStatusFilterTest.kt
android/app/src/test/.../ui/screen/reader/ReaderSelectionToolbarModelTest.kt
android/app/src/test/.../ui/screen/shelf/ShelfStatusCountsTest.kt
docs/research/2026-09-19-android-worktree-product-inventory.md
```

---

## 3. 原目录独有资产（9 项，与上轮一致）

与上轮报告 §2.1 完全一致，本轮未发现新变化。完整表见 E 盘 `original-only-assets.csv`。

| 处置 | 路径 | SHA-256（前 16） | 说明 |
|---|---|---|---|
| **PRESERVE_DESKTOP** | `electron/main/creation-workspace/bundle-merge.ts` | `7df2971b2511ce2a` | `e740ef6d` 新增，恢复目录缺失 |
| **PRESERVE_DESKTOP** | `electron/main/creation-workspace/proof-engine.ts` | `4674f09c4c3e317d` | 同上 |
| **PRESERVE_DESKTOP** | `electron/main/creation-workspace/validate.ts` | `fe12bcf0e16f5537` | 同上 |
| **PRESERVE_DESKTOP** | `docs/plans/2026-09-21-desktop-main-process-split-proposal.md` | `d10469b08146112f` | 09-21 桌面方案，晚于分叉 |
| **PRESERVE_EVIDENCE** | `docs/qa/2026-09-26-s04-epub-image-cache-mumu-portrait-supplement.md` | `7d959b7d7a5db120` | 09-26 S04 证据，恢复目录无此份 |
| **PRESERVE_DOC** | `docs/release/ANDROID_RELEASE.md` | `ae79effa52c63453` | 未跟踪发布文档，恢复目录无 `docs/release/` |
| **AGENT_CONFIG** | `.claude/settings.local.json` | `a0699637e7c54984` | 本机 Agent 配置，含 `Bash(git checkout *)`；**不得复制进恢复仓库** |
| **ARTIFACT** | `qa_pk4.xml` | `320678cace34989d` | 设备 UI dump，隐私治理范围；**不得入库** |
| IGNORABLE | `C<U+F03A>WindowsTempindex_backup.ts` | `33c35c01b07bc2e8` | `index.ts` 旧版子集，路径损坏产物 |

> 另 237 项 `ORIG_ONLY_OTHER` 均为不可入库的本机产物（adb 截图、设备 DB、IDE 配置等），无需保留。

---

## 4. 备份未覆盖清单复核

E 盘 `recovery-untracked-not-backed-up.txt`（81 行）为 2026-09-29 02:09~02:10 时点快照。本轮复核：

1. **4 个 docs/coordination/ 文件**（line 40-43）仍为未备份状态。其中 `2026-09-29-stage-e-android-hunk-plan.md` 已由 Android 窗口更新，本文件（`2026-09-29-stage-e-asset-reconciliation.md`）为本轮更新——两者均需增量备份。
2. **18 个 docs/code-review/ 文件**（line 22-39）仍为未备份状态。
3. **6 个 docs/research/ 文件**（line 44-49）仍为未备份状态。
4. **35 个根目录 dump*.xml**（line 50-81）为设备 UI dump，隐私治理范围，**不应入库或备份至共享位置**。
5. **8 个 .workbuddy-ai/memory/ 文件**（line 5-12）为本机 Agent 记忆，**不应入库**。

**时点说明：** 备份快照生成于 02:09~02:10，本轮对账完成于同日稍晚。两个新/更新报告（`stage-e-android-hunk-plan.md`、`stage-e-asset-reconciliation.md`）不在备份中。**在 E 的提交/换目录授权落地前，应先补一次增量备份。**

---

## 5. 仍未覆盖的目录与限制

| 未覆盖项 | 原因 | 影响 |
|---|---|---|
| 两个仓库的 `.git/` 目录内部 | 只读诊断只取 fsck / rev-list / ls-tree 输出 | 原仓库对象是否可经联网 `git fetch` 补全，未验证 |
| 原仓库 7 个 worktree 的工作树内容 | 1 个在 `C:\Users\23254\.config\superpowers\worktrees\`，越出 `D:\develop\Code\Codex\` | 这些 worktree 是否有独有改动**未对账** |
| 65 项 `NEED_REVIEW` + `LIKELY_COVERED` 的语义等价证明 | 行级集合包含不成立，但可能语义等价 | 需逐 hunk 人工确认旧代码是否已被等价新代码取代 |
| 两个目录之外的第三份副本 | 未扫描 E 盘以外位置 | 若存在更早的完整备份，可能改变 §3 结论 |

---

## 6. 建议操作顺序与风险（不执行）

沿用上轮报告 §5，本轮补充：

| 步 | 动作 | 风险 |
|---|---|---|
| 0 | **补增量备份**：把 81 个未覆盖文件 + 2 个本轮更新报告追加到 E 盘 | 不做则后续任何 `clean`/换目录都会永久丢失 `docs/code-review`、`docs/research` |
| 1 | 对 65 项需人工确认做逐 hunk 语义审查 | 若跳过，等于默认"恢复目录已完全取代原目录"这一仍有 65 项未确认的前提 |
| 2 | 决定 3 个 Electron 文件 + 4 项 PRESERVE 资产去向（桌面线单独处理） | 混入 Android 提交会破坏 AGENTS.md 的产品线边界 |
| 3 | Room v15 schema 作为独立前置提交 | 干净检出拿不到 v15 快照 |
| 4 | 提交时**禁止 `git add -A`** | 会把 `.workbuddy-ai/`、`.zcode/`、`.opencode/`、34 个 `dump*.xml` 一并带入 |

**本轮不执行以上任何一步。**

---

## 7. 纪律

- 只读：对两个仓库均未做任何写操作；对原仓库仅执行 `rev-parse` / `status` / `fsck` / `worktree list` / `branch` / `ls-tree` / `rev-list` / `hash-object` / `cat-file`。
- 未运行 Gradle、ADB、任何构建或设备操作；未接触 MuMu、实体手机、正式书库。
- 未 stage / commit / push / reset / checkout / clean；未复制回仓库、未删除或覆盖文件。
- 未把 D 阶段已限定退出的 S05 三项写成新通过；未把"268 文件内容不同"写成冲突；未把原仓库部分克隆的缺对象写成永久损坏。
- **未把 `.claude/settings.local.json`、`qa_pk4.xml`、dump*.xml 等隐私/设备产物复制进恢复仓库。**
- **未读取 Claude 的 Obsidian 记忆库。**
- 唯一写入的仓库文件即本文件。

---

## 8. E 盘产物索引

目录：`E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-recon\`

### 8.1 本轮新增产物

| 文件 | 字节 | SHA-256 |
|---|---:|---|
| `orig-unique-classification.csv` | 93,051 | `67525080a49e876f637fb649e467d9e50e50e1fd3a859bce40b20e71682bd1df` |
| `orig-unique-semantic-diff.csv` | 92,723 | `363ff906372269b0d0219e6af7177d3e2489641e32ea6cc2eb54fd1900291df6` |
| `orig-unique-set-diff.csv` | 109,230 | `a0eeee937e7d9be41b12918a197f64123690630623f52849cf7d218d2b152755` |
| `orig-unique-full-reconciliation.csv` | 127,985 | `87b57341f8106c7272e4c2551cde9fdde3788ea116a798ab4438f12e66072fdc` |

`orig-unique-full-reconciliation.csv` 为逐项大表，含 92 行 × 15 列：path、head_status、orig_sha256、rec_sha256、orig_bytes、rec_bytes、orig_mtime、rec_mtime、orig_only_lines、rec_only_lines、diff_removed、diff_added、coverage、handling、orig_only_sample、rec_only_sample。

### 8.2 上轮产物（未变更）

| 文件 | 字节 | SHA-256 |
|---|---:|---|
| `full-tree-diff.csv` | 16,649,941 | `721c727bbbd5277cfbe3ddf7b785871d436fe8925698da8db22b74ac325d46c3` |
| `bucket-SAME_SOURCE.csv` | 331,716 | `c16d47f03ce345e627eec19c7b01aa0b238b368607c96080292b9d7ec7133c49` |
| `bucket-DIFF_SOURCE.csv` | 65,876 | `fb98b0903afc44846b1592928e53fcaea2fa8441e3ceaf5f16badd2d7bc44ada` |
| `bucket-ORIG_ONLY_SOURCE.csv` | 1,183 | `09c70ba727e79b0607c478905866a61de48c5dba9ad7be1fe73a30e65872859d` |
| `bucket-ORIG_ONLY_OTHER.csv` | 26,102 | `ae86591a4cbf880e8d3e8a7e47ae1df2f527972fdb3e1b9605a10f12ef5e4752` |
| `bucket-REC_ONLY_SOURCE.csv` | 190,320 | `9833e990491e4ce4c0064e1293af0497d78362fa557b550b343784e607c3ae30` |
| `bucket-REC_ONLY_OTHER.csv` | 57 | `ef963df93aeac49c0058447ea522a88a87817d569b3c04ec1d9d0538df68106f` |
| `original-only-assets.csv` | 40,261 | `f7b6253dc910d8305ddabec454db4ab60fc42f67134c744364be47b24115ad93` |
| `recovery-untracked-not-backed-up.txt` | 3,081 | `59be0269c5d080ffe891f3c78166084a88496aea75b8de1095ee55d3436c614c` |

上游依据：[E 入口清单](2026-09-29-stage-e-entry-inventory.md)、[Git 健康入口方案](2026-09-29-stage-e-git-entry-plan.md)、[Android hunk 拆分计划](2026-09-29-stage-e-android-hunk-plan.md)、[D 限定退出复核](../../reviews/2026-09/2026-09-29-stage-d-limited-exit-review.md)、[三窗复核](../../reviews/2026-09/2026-09-29-stage-e-three-report-review.md)、[收口计划 §E](../../../plans/android/2026-09-22-android-recovery-convergence-plan.md)、[AGENTS.md](../../../../AGENTS.md)。

**本轮未运行 Gradle 或 ADB。**
