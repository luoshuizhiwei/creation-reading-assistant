# 阶段 E — Android 候选改动 hunk 拆分计划

> 生成时间：2026-09-29（本轮补正）
> 生成方式：只读检查 `android/` 当前 git 改动，按提交边界分组
> 基线 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`
> 输入清单：`docs/coordination/legacy-stages/2026-09/2026-09-29-stage-e-entry-inventory.md`
> 上游约束：`docs/plans/android/2026-09-22-android-recovery-convergence-plan.md` §E（line 93：仅"直接相关源码、必要配置、对应测试"）
> D 阶段验证：`docs/coordination/reviews/2026-09/2026-09-29-stage-d-limited-exit-review.md`
> 三窗复核：`docs/coordination/reviews/2026-09/2026-09-29-stage-e-three-report-review.md`
> **本轮补正**：全量覆盖矩阵、G8 子拆分、B4/B5/B8/B9 逐 hunk 归属、证据修正（AppDatabaseMigrationTest androidTest 非 JVM、版本号 @Upsert 非 Room v15、S05-C 边界）、编译状态改为"待验证"。
> **大矩阵**：`E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-full-coverage-matrix.csv`，SHA-256 `eb289a71bca83fe5c6e8b4fee845fc4717008e1e278b8ab498c2fc30fd2ba66c`，376 行。

---

## 1. 只读检查范围

本次检查未修改任何源码、测试、配置或公共交接文件，未 stage/commit/push/reset/checkout，未运行全量构建，未使用设备。

实际执行的只读命令：

| 检查项 | 命令 | 结果 |
|--------|------|------|
| HEAD 基线 | `git rev-parse HEAD` | `5aed5939...` |
| android/ 总改动 | `git status --porcelain=v1 -- android/` | 376 项（239 M + 137 ??）— 全矩阵分母 |
| 源码改动 | 同上，过滤 `src/main` | 231 M + 129 ?? |
| 未跟踪测试 | 同上，过滤 `src/test` + `src/androidTest` | 86 ?? |
| Room schema 目录 | `git status` + `ls` schemas 目录 | 15.json 未跟踪；5-9.json 已跟踪；1-15.json 在磁盘 |
| build.gradle.kts hunks | `git diff` + `git diff --stat` | 4 hunks（version + testBuildType + qahost + runner） |
| MainActivity.kt hunks | `git diff` | 7 hunks（S06 boot theme + V5 DurationPrefs + 其他） |
| AndroidManifest.xml hunks | `git diff` | 2 hunks（configChanges + TTS_SERVICE） |
| ShelfModels.kt hunks | `git diff --stat` | 460 insertions（S05 + smart search + grid helpers 混合） |
| JsonBridge.kt hunks | `git diff` | 10 hunks（S14 backup + R3 library_source_refs 混合） |
| WebDavBackup.kt hunks | `git diff` | 8 hunks（S14 + R3 混合） |
| Reader/pager 改动 | `git diff --stat` 过滤 `feature/reader/` | 20 M 文件，+1843/-401 |
| S12 Legado 文件 | `git diff --stat` + glob | 2 ?? 主源码 + 4 M 关联文件 |
| applyBootWindowTheme 定义 | grep | MainActivity.kt:160 `private fun applyBootWindowTheme()` |
| IsolatedTestContext 位置 | glob | `androidTest/.../feature/reader/IsolatedTestContext.kt` |

---

## 2. 候选提交组总览

共 **9 个候选组**（G0–G8），按依赖先后排列。另有 **1 个排除清单**（新功能，不纳入 E 恢复提交）。

| 组 | 主题 | 文件数 | 编译边界 | 优先级 |
|----|------|--------|----------|--------|
| G0 | Room v15 schema + migration | 4 | 待验证 | 最高（数据基础） |
| G1 | 版本号 0.5.1(3) | 1（拆 hunk） | 待验证 | 与 G0 同提交 |
| G2 | 测试基础设施 qahost + isolated runner | 4 | 待验证 | 独立 |
| G3 | S06 启动窗口主题 | 5（拆 hunk） | ⚠ 需验证 | 独立 |
| G4 | S12 Legado 导入 + marker guard | 11 | 待验证 | 独立 |
| G5 | S14 ZIP 备份 + journal kill | 3（拆 hunk） | ⚠ 需验证 | 依赖 R3 hunks |
| G6 | V5 DurationPrefs + 单位切换 | 6（拆 hunk） | 待验证 | 独立 |
| G7 | S05 完本状态契约 | 8（拆 hunk） | ⚠ 需验证 | 依赖 smart search |
| G8 | Reader/pager 大组 | 103 | ⚠ 需子拆分 | 依赖多 |

---

## 3. 候选提交组详情

### G0 — Room v15 schema + migration（数据基础）

**文件：**

| 文件 | 状态 | 说明 |
|------|------|------|
| `android/app/schemas/.../AppDatabase/15.json` | ?? → add | Room v15 schema 导出 |
| `android/app/src/main/.../data/local/AppDatabase.kt` | M | version 14→15，新增 `MIGRATION_14_15`（drop `index_search_terms_term`） |
| `android/app/src/main/.../data/local/DatabaseModule.kt` | M | 将 `MIGRATION_14_15` 加入 migration 列表 |
| `android/app/src/androidTest/.../data/local/AppDatabaseMigrationTest.kt` | M | +324 行，v14→v15 迁移测试 + open callback 测试 |

**Hunk 拆分：** 无需拆分。3 个 Kotlin 文件的所有 hunk 均为 v14→v15 相关，已逐文件确认。

**依赖：** 无。这是数据基础，必须最先提交。

**缺口：** 无。`15.json` 未跟踪但内容就绪。

**可复用 D 验证：**
- ~~D 阶段 gate JVM 3208/3208（含 `AppDatabaseMigrationTest`）~~ **修正：** `AppDatabaseMigrationTest.kt` 位于 `src/androidTest/`，是 instrumented test，**不在 JVM 3208/3208 范围内**。JVM 3208/3208 不能证明它已运行。它的验证来源是 D 阶段 build（androidTest APK 编译通过）+ S04 EPUB isolated Room 8/8（使用 in-memory Room，间接验证 migration 逻辑）。
- S04 EPUB isolated Room 8/8（`EpubReplaceRoomIsolatedTest`，使用 in-memory Room）
- D 阶段 build（Debug APK + androidTest APK 编译通过，但**未在设备上执行 androidTest**）

**编译边界：** 待验证。全量工作树 build 通过不等于 G0 拆分后单独编译通过。需独立验证：仅暂存 G0 文件后 `./gradlew :app:compileDebugKotlin` + `./gradlew :app:compileDebugAndroidTestKotlin`。

---

### G1 — 版本号 0.5.1(3)

**文件：**

| 文件 | 状态 | 说明 |
|------|------|------|
| `android/app/build.gradle.kts` | M（仅 1 hunk） | `versionCode 2→3`，`versionName "0.5.0"→"0.5.1"` |

**Hunk 拆分：** `build.gradle.kts` 共 4 hunks，本组仅取 version hunk：

| Hunk | 行范围（约） | 归属 |
|------|-------------|------|
| 1 | ~11 `testBuildType` | → G2 |
| 2 | ~22 `versionCode`/`versionName` | → **G1** |
| 3 | ~25 `testInstrumentationRunner` | → G2 |
| 4 | ~102 + ~240 `qahost` build type + `qahostImplementation` | → G2 |

需 `git add -p` 交互式暂存仅 hunk 2。

**依赖：** **修正：** `build.gradle.kts` 的 0.5.1 版本号注释指向**父表 @Upsert 修复**（`ParentUpsertCascadeSafetyTest`），**不是 Room v15 migration**。因此 G1 不依赖 G0，而是依赖 @Upsert 修复（该修复的 androidTest 为 `ParentUpsertCascadeSafetyTest.kt`，同样不在 JVM 3208/3208 中）。G0 与 G1 可独立提交，但建议同提交以简化版本号管理。

**缺口：** `build.gradle.kts` 是集成者所有文件（AGENTS.md：单一集成者维护），拆 hunk 需协调。`ParentUpsertCascadeSafetyTest.kt` 需归入 G1 或独立组。

**可复用 D 验证：** D 阶段 build（版本号在 APK 中可见）。**注意：** `ParentUpsertCascadeSafetyTest.kt` 是 androidTest，JVM 3208/3208 不覆盖。

**编译边界：** 待验证。全量工作树 build 通过不等于 G1 拆分后单独编译通过。

---

### G2 — 测试基础设施 qahost + isolated runner

**文件：**

| 文件 | 状态 | 说明 |
|------|------|------|
| `android/app/build.gradle.kts` | M（3 hunks） | `testBuildType`、`qahost` build type、`CraIsolatedTestRunner`、`qahostImplementation` |
| `android/app/src/androidTest/.../testing/CraIsolatedTestRunner.kt` | ?? → add | 自定义 test runner |
| `android/app/src/androidTest/.../testing/CraIsolatedTestApplication.kt` | ?? → add | 隔离 test application |
| `android/app/src/androidTest/.../feature/reader/IsolatedTestContext.kt` | ?? → add | 隔离 test context 工具 |

**Hunk 拆分：** `build.gradle.kts` 取 hunk 1/3/4（见 G1 表），排除 hunk 2。

**依赖：** 无。测试基础设施自包含，可与 G0/G1 并行提交。

**缺口：** 无。

**可复用 D 验证：**
- D 阶段 gate：qahost APK build、androidTest APK build
- V5 62/62（使用 `CraIsolatedTestRunner`）
- S04 EPUB isolated Room 8/8（使用 isolated test context）

**编译边界：** 待验证。`testInstrumentationRunner` 改动要求 `CraIsolatedTestRunner.kt` 同时在位，必须同提交。全量工作树 build 通过不等于 G2 拆分后单独编译通过。

---

### G3 — S06 启动窗口主题（D2）

**文件：**

| 文件 | 状态 | 说明 |
|------|------|------|
| `android/app/src/main/res/values/colors.xml` | M | `boot_window_background` 颜色定义 |
| `android/app/src/main/res/values/themes.xml` | M | `Theme.CreationReadingAssistant.Dark` + `windowBackground` |
| `android/app/src/main/res/values-night/themes.xml` | ?? → add | 夜间主题覆盖 |
| `android/app/src/main/.../MainActivity.kt` | M（仅 S06 hunks） | `applyBootWindowTheme()` 调用 + `ThemeResolver`/`BootMirror` import |
| `android/app/src/main/AndroidManifest.xml` | M（仅 configChanges hunk） | `orientation\|screenSize\|...\|uiMode` |

**Hunk 拆分：**

`MainActivity.kt` 共 7 hunks，混合 S06 + V5 + 其他。本组仅取 S06 相关：
- `applyBootWindowTheme()` 函数定义（line 160）
- `applyBootWindowTheme()` 调用点
- `ThemeResolver` / `BootMirror` import
- 排除 V5 的 `DurationPrefs` import/injection 和 `LocalDurationUnit`/`LocalDurationUnitToggle` hunk

`AndroidManifest.xml` 共 2 hunks：
- hunk A：`configChanges`（S06）→ **G3**
- hunk B：`TTS_SERVICE` query（S12）→ G4

**依赖：** 无。主题自包含。

**缺口：** `MainActivity.kt` 是共享文件，需 `git add -p` 拆 hunk。`applyBootWindowTheme()` 定义在 line 160，需确认该 hunk 不含 V5 代码。

**可复用 D 验证：** S06 theme D2 手机尺寸（`theme-phone-dark-loaded.png` + XML 检查）。

**编译边界：** ⚠ 需验证。若 `applyBootWindowTheme()` 的 hunk 与 V5 代码在同一 hunk 块内，拆分后可能缺少函数定义。最小调整：手动编辑 hunk 边界，确保函数定义完整保留在 S06 hunk 中。

---

### G4 — S12 Legado 导入 + marker guard

**文件：**

| 文件 | 状态 | 说明 |
|------|------|------|
| `android/app/src/main/.../feature/reader/locator/LegadoProgressMarker.kt` | ?? → add | Legado 进度标记数据结构 |
| `android/app/src/main/.../feature/sync/LegadoReadingProgressImporter.kt` | ?? → add | Legado 阅读进度导入器 |
| `android/app/src/main/.../ui/viewmodel/ReaderDocumentLoader.kt` | M（S12 hunks） | +74 行，Legado marker 集成 |
| `android/app/src/main/.../ui/screen/reader/ReaderProgressEffects.kt` | M（S12 hunks） | +253 行，`LegadoMarkerSaveGuard` |
| `android/app/src/main/.../ui/viewmodel/ProfileViewModel.kt` | M（S12 hunks） | +146 行，Legado importer 接线 |
| `android/app/src/main/.../ui/viewmodel/ProfileWebDavManager.kt` | M（S12 hunks） | +36 行，Legado importer 接线 |
| `android/app/src/main/AndroidManifest.xml` | M（仅 TTS_SERVICE hunk） | TTS service 可见性 |
| `android/app/src/test/.../LegadoProgressMarkerTest.kt` | ?? → add | |
| `android/app/src/test/.../LegadoReadingProgressImporterTest.kt` | ?? → add | |
| `android/app/src/test/.../LegadoImportEpubLocatorContractTest.kt` | ?? → add | |
| `android/app/src/test/.../ReaderDocumentLoaderLegadoProgressTest.kt` | ?? → add | |
| `android/app/src/test/.../ReaderDocumentLoaderStreamingLegadoTest.kt` | ?? → add | |
| `android/app/src/test/.../ReaderLegadoMarkerSaveGuardTest.kt` | ?? → add | |

**Hunk 拆分：**

- `ReaderProgressEffects.kt`（14 hunks，+253 行）— **B8 已拆分：**
  - S12 hunks（`LegadoMarkerSaveGuard`）：hunk 3, 4, 8, 12, 14，约 167 行 → **G4**
  - 非 S12 hunks：hunk 1, 2, 5, 6, 7, 9, 10, 11, 13，约 37 行 → G8e（reader progress 通用）
  - S12 与非 S12 hunk 不交错，可干净拆分
- `ProfileViewModel.kt`（13 hunks，+146 行）— **B9 已拆分：**
  - S12 hunks（Legado importer 接线）：hunk 1, 2, 4, 9, 10, 12，约 35 行 → **G4**
  - 非 S12 hunks：hunk 3, 5, 6, 7, 8, 11, 13，约 101 行 → HOLD（profile 通用改动，归属待定）
  - S12 与非 S12 hunk 不交错，可干净拆分
- `ReaderDocumentLoader.kt`（+74 行）：需确认全部为 S12 相关
- `ProfileWebDavManager.kt`（+36 行）：大概率全部 S12
- `AndroidManifest.xml`：取 TTS_SERVICE hunk（见 G3）

**依赖：** 无直接依赖。但 `ReaderDocumentLoader.kt` 和 `ReaderProgressEffects.kt` 是共享 reader 文件，非 S12 hunk 归属其他组。

**缺口：** B8/B9 hunk 边界已判定（见上）。`ReaderDocumentLoader.kt` 和 `ProfileWebDavManager.kt` 需确认是否全为 S12。

**可复用 D 验证：**
- S12 MuMu real import temporary route acceptance
- S12 MuMu qahost production import

**编译边界：** 待验证。`LegadoProgressMarker.kt` + `LegadoReadingProgressImporter.kt` 必须与修改文件的 S12 hunk 同提交。全量工作树 build 通过不等于 G4 拆分后单独编译通过。

---

### G5 — S14 ZIP 备份 + journal kill window

**文件：**

| 文件 | 状态 | 说明 |
|------|------|------|
| `android/app/src/main/.../feature/sync/LocalZipBackup.kt` | M | +521 行，5 hunks，大概率全 S14 |
| `android/app/src/main/.../feature/sync/JsonBridge.kt` | M（仅 S14 hunks） | 10 hunks，混合 S14 + R3 `library_source_refs` |
| `android/app/src/main/.../feature/sync/WebDavBackup.kt` | M（仅 S14 hunks） | 8 hunks，混合 S14 + R3 |
| `android/app/src/test/.../BookResidueSafeCleanupTest.kt` | ?? → add | S14 残留清理测试 |

**Hunk 拆分：**

`JsonBridge.kt` 10 hunks — **B5 已拆分：**
- Hunk 1, 2：通用 → HOLD
- Hunk 3：**混合 R3 + S14**（`library_source_refs` serial name + backup 字段）→ 需同提交 R3 + S14 或拆分
- Hunk 4, 9, 10：S14（backup/restore 扩展）→ **G5**
- Hunk 5, 6, 7, 8：需确认归属

`WebDavBackup.kt` 9 hunks — **B5 已拆分：**
- Hunk 1, 2：通用 → HOLD
- Hunk 3, 4, 7：S14（backup safety）→ **G5**
- Hunk 4：**Legado**（与 S12 相关）→ 需归入 G4 或 HOLD
- Hunk 5, 6, 8, 9：需确认归属

**依赖：** Hunk 3 of `JsonBridge.kt` 混合 R3 + S14。若 S14 部分引用 R3 `library_source_refs` 实体，需同提交 R3 hunk。**标 HOLD**，不猜测可独立编译。

**缺口：** B5 hunk 边界已部分判定（见上）。Hunk 3 of `JsonBridge.kt` 的 R3/S14 混合需手动拆分或同提交。`WebDavBackup.kt` hunk 4 的 Legado 归属需确认。

**可复用 D 验证：** S14 JVM 22/22（handoff §2, 2026-09-28）。

**编译边界：** ⚠ 需验证。`JsonBridge.kt` hunk 3 混合 R3 + S14，拆分后可能编译失败。最小调整：将 hunk 3 整体归入 G5（含 R3 部分），或标 HOLD 待集成者决定。

---

### G6 — V5 DurationPrefs + 单位切换

**文件：**

| 文件 | 状态 | 说明 |
|------|------|------|
| `android/app/src/main/.../data/settings/DurationPrefs.kt` | ?? → add | 时长偏好数据类 |
| `android/app/src/main/.../ui/util/DurationDisplay.kt` | ?? → add | 时长显示工具 + `LocalDurationUnit`/`LocalDurationUnitToggle` |
| `android/app/src/main/.../MainActivity.kt` | M（仅 V5 hunks） | `DurationPrefs` import/injection，`LocalDurationUnit`/`LocalDurationUnitToggle` 提供 |
| `android/app/src/main/.../ui/screen/profile/AppearanceSubPage.kt` | M | `DurationPrefs` 引用 |
| `android/app/src/test/.../DurationPrefsTest.kt` | ?? → add | |
| `android/app/src/test/.../DurationUnitTest.kt` | ?? → add | |
| `android/app/src/androidTest/.../DurationPillGestureTest.kt` | ?? → add | |
| `android/app/src/androidTest/.../DurationUnitToggleEntryTest.kt` | ?? → add | |

**Hunk 拆分：** `MainActivity.kt` 取 V5 hunk（`DurationPrefs` import/injection + `LocalDurationUnit`/`LocalDurationUnitToggle`），排除 S06 hunk（见 G3）。

**依赖：** 无直接依赖。`MainActivity.kt` 与 G3 共享。

**缺口：** 需确认 `AppearanceSubPage.kt` 的改动是否全为 V5 相关。

**可复用 D 验证：** V5 62/62（含 `DurationPillGestureTest` + `DurationUnitToggleEntryTest`）。

**编译边界：** 待验证。`DurationPrefs.kt` + `DurationDisplay.kt` + `MainActivity.kt` V5 hunk 必须同提交。`LocalDurationUnit`/`LocalDurationUnitToggle` CompositionLocal 需在 `DurationDisplay.kt` 或伴生文件中定义。全量工作树 build 通过不等于 G6 拆分后单独编译通过。

---

### G7 — S05 完本状态契约

**文件：**

| 文件 | 状态 | 说明 |
|------|------|------|
| `android/app/src/main/.../ui/screen/shelf/ShelfModels.kt` | M（仅 S05 hunks） | 460 insertions，混合 S05 + smart search + grid helpers |
| `android/app/src/main/.../ui/screen/shelf/BookGrid.kt` | M（仅 S05 hunks） | 12 hunks，混合 |
| `android/app/src/main/.../ui/screen/shelf/BookActionSheet.kt` | M（仅 S05 hunks） | 5 hunks，混合 |
| `android/app/src/main/.../ui/screen/shelf/ShelfRoute.kt` | M（仅 S05 hunks） | 18 hunks，混合 |
| `android/app/src/main/.../ui/screen/stats/StatsPage.kt` | M | 统计页完本状态 |
| `android/app/src/main/.../ui/util/BookReadiness.kt` | M | 完本就绪度 |
| `android/app/src/test/.../ShelfStatusCountsTest.kt` | ?? → add | |
| `android/app/src/test/.../BookReadinessProgressLabelTest.kt` | ?? → add | |

**Hunk 拆分：**

`ShelfModels.kt` 460 insertions — **B4 已拆分**为 3 类改动：
1. **S05-①**：`bookStatus`/`readingStatus`/`isDisplayFinished` 完本状态重构 → **G7**
2. **Smart search**：`extractPinyinInitials`、`filterItems` 升级、`extractChapterLabel` → **排除**（新功能）
3. **Grid helpers**：`shelfLeadingItemCount`、`shelfGridIndexOfBook`、`shelfStatusCounts` → **G7**（被 S05 hunk 引用，需同提交）

`BookGrid.kt`（12 hunks）、`BookActionSheet.kt`（5 hunks）、`ShelfRoute.kt`（18 hunks）均需逐 hunk 拆分 S05 vs smart search vs grid helpers。**标 HOLD**，不猜测可独立编译。

**依赖：** `ShelfModels.kt` 的 grid helpers（`shelfStatusCounts` 等）被 `BookGrid.kt`/`ShelfRoute.kt` 的 S05 hunk 引用。需将 S05 + 被引用的 grid helper hunk 合并提交。smart search hunk 排除。

**缺口：**
- B4 `ShelfModels.kt` 三类改动边界已判定（见上）
- `BookGrid.kt`/`ShelfRoute.kt` 的逐 hunk S05 vs smart search 拆分**未完成** → **标 HOLD**
- **S05-C 边界修正：** 420dpi 手机补验已完成，但"重开即写库"**未成立**。D exit review 记录的是"全程 420dpi 手机布局"不完全成立——具体为：C 的手机布局截图验证通过，但"重开应用即写入数据库"的端到端验证未在 D 阶段完成。应按 D exit review 写准确边界：**S05-C 手机布局已验证，重开即写库未验证**。

**可复用 D 验证：**
- S05 A/B MuMu（完本状态契约）
- S05 C MuMu（手机布局已验证，**重开即写库未验证**）

**编译边界：** ⚠ 需验证。`BookGrid.kt`/`ShelfRoute.kt` 的 S05 hunk 引用 `shelfStatusCounts` 等 grid helper，需将相关 grid helper hunk 一起提交。smart search hunk 排除。`BookGrid.kt`/`ShelfRoute.kt` 逐 hunk 拆分未完成 → 标 HOLD。

---

### G8 — Reader/pager 大组（子拆分 G8a–G8e）

**文件：** 103 个文件（矩阵统计），位于 `android/app/src/main/.../feature/reader/`、`ui/screen/reader/` 及对应测试，共 +1843/-401 行（仅 main 源码）。

**子拆分（按 session/topic）：**

| 子组 | 主题 | 候选文件 | 说明 |
|------|------|----------|------|
| G8a | T8 reader layout / pager | `PageTurner.kt`（24 hunks）、`PagedReaderHeaderFooter.kt`、`PagedReaderPageSurface.kt`、`PagedTxtReaderHost.kt`、`TxtPagedController.kt`、`PagedReaderClock.kt`、`PagedReaderControllerEffects.kt`、`PageSelection.kt`、`PendingTurnQueue.kt`（??）、`WholeBookPageModel.kt`（??）、`ReaderFontManager.kt`、`ReaderHardwareKeys.kt`、`ReaderTapAction.kt`、`TxtFileScanner.kt` + 对应测试 | T8 分页/翻页/布局核心 |
| G8b | P3.1 EPUB fresh source replace | `EpubParser.kt`、`EpubRepository.kt`（仅 P3.1 hunks）、`EpubParserFreshSourceTest.kt`（??）、`EpubRepositoryReplaceTest.kt`（??）、`EpubReplaceRoomIsolatedTest.kt`（?? androidTest）、`EpubParserInstrumentedTest.kt`（?? androidTest） | P3.1 EPUB 替换源 |
| G8c | S09 跨章导航 / locator | `LocatorBuilder.kt`、`LocatorCodec.kt`、`StoredProgressLocatorNavigationTest.kt`（??）、`EpubRepository.kt`（仅 S09 hunks） | S09 跨章导航 |
| G8d | S14 backup safety（reader 侧） | `EpubRepository.kt`（仅 S14 hunks）、`FileOps.kt` | S14 备份安全 |
| G8e | 其他 reader UI / progress | `ReaderProgressEffects.kt`（非 S12 hunks：1,2,5-7,9-11,13，约 37 行）、`ChapterTextDiff.kt`、reader screen/sheets/tts/content UI 文件 | reader UI 通用改动 |

**`EpubRepository.kt` 混合 hunk 拆分：** 该文件跨 G8b + G8c + G8d 三个子组，9 hunks 需逐 hunk 归属。**标 HOLD**，不猜测可独立编译。

**`PageTurner.kt` 24 hunks：** 需逐 hunk 确认 T8 vs 其他 session 归属。**标 HOLD**。

**依赖：** 复杂。G8a 是基础（pager/layout），G8b/G8c/G8d 依赖 G8a 的接口。G8e 依赖 G8a 的 reader 框架。子组间依赖需逐 hunk 理解。

**缺口：** `EpubRepository.kt` 和 `PageTurner.kt` 的逐 hunk 归属未完成 → 标 HOLD。reader UI 文件（screen/reader/）的逐 hunk 归属未完成 → 标 HOLD。

**可复用 D 验证：** D 阶段 gate JVM 3208/3208（含全部 reader JVM 测试）。**注意：** androidTest reader 测试（如 `EpubReplaceRoomIsolatedTest`、`EpubParserInstrumentedTest`）不在 JVM 3208/3208 中。

**编译边界：** ⚠ 必须子拆分。103 文件作为单一提交不可审阅。`EpubRepository.kt` 和 `PageTurner.kt` 的 hunk 级归属未完成 → 标 HOLD。全量工作树 build 通过不等于任一子组拆分后单独编译通过。

---

## 4. 排除清单（新功能，不纳入 E 恢复提交）

按 §E line 93 约束，仅"直接相关源码、必要配置、对应测试"纳入 E 提交。以下为新功能，需独立产品决策：

| 类别 | 候选文件 | 说明 |
|------|----------|------|
| Smart search | `ShelfModels.kt` 中 `extractPinyinInitials`/`filterItems` 升级/`extractChapterLabel` | 拼音首字母搜索，新功能 |
| SettingsSearchIndex | `android/app/src/main/.../feature/search/` 未跟踪文件 | 设置搜索索引 |
| SearchHitRanker | 同上 | 搜索命中排序 |
| CoverImageCache | `android/app/src/main/.../ui/util/` 未跟踪 | 封面图片缓存 |
| CraSlider | `android/app/src/main/.../ui/components/` 未跟踪 | 自定义滑块组件 |
| CharacterTopologySection | `android/app/src/main/.../ui/screen/stats/components/` 未跟踪 | 字符拓扑统计 |
| values-night/ 部分 | 若含非 S06 主题覆盖 | 需确认 |
| 84 个未跟踪测试中的新功能测试 | 对应上述新功能 | 需逐文件判断 |

**注意：** 入口清单的 360 Android 源码/测试文件包含上述新功能。不应将 366 个候选文件全部视为应提交。

---

## 5. 阻塞点汇总

| # | 阻塞点 | 影响组 | 严重度 | 状态 |
|---|--------|--------|--------|------|
| B1 | `build.gradle.kts` 4 hunks 混合 version + testBuildType + qahost + runner | G1, G2 | 中 | **已拆分**（见 G1/G2 hunk 表） |
| B2 | `MainActivity.kt` 7 hunks 混合 S06 + V5 + 其他 | G3, G6 | 中 | **已拆分**（S06 hunks → G3，V5 hunks → G6） |
| B3 | `AndroidManifest.xml` 2 hunks 混合 configChanges + TTS_SERVICE | G3, G4 | 低 | **已拆分**（hunk A → G3，hunk B → G4） |
| B4 | `ShelfModels.kt` 460 insertions 混合 S05 + smart search + grid helpers | G7 | 高 | **已拆分**：S05 + grid helpers → G7，smart search → 排除。`BookGrid.kt`/`ShelfRoute.kt` 逐 hunk 未完成 → HOLD |
| B5 | `JsonBridge.kt` / `WebDavBackup.kt` 混合 S14 + R3 + Legado | G5 | 高 | **已部分拆分**：JsonBridge hunk 3 混合 R3+S14 → HOLD；WebDavBackup hunk 4 Legado → HOLD |
| B6 | Reader/pager 103 文件跨多 session | G8 | 高 | **已子拆分 G8a–G8e**。`EpubRepository.kt`/`PageTurner.kt` 逐 hunk 未完成 → HOLD |
| B7 | S05-C "重开即写库"未验证 | G7 | 低 | **已修正**：手机布局已验证，重开即写库未验证（按 D exit review 准确边界） |
| B8 | `ReaderProgressEffects.kt` 14 hunks 跨多 session | G4 | 中 | **已拆分**：S12 = hunk 3,4,8,12,14（~167 行）→ G4；非 S12 = hunk 1,2,5-7,9-11,13（~37 行）→ G8e |
| B9 | `ProfileViewModel.kt` 13 hunks 跨多 session | G4 | 中 | **已拆分**：S12 = hunk 1,2,4,9,10,12（~35 行）→ G4；非 S12 = hunk 3,5-8,11,13（~101 行）→ HOLD |

---

## 6. 依赖先后顺序

```
G0 (Room v15) ── 独立（数据基础）

G1 (version bump 0.5.1) ── 独立（指向 @Upsert 修复，非 Room v15）
                    ↑ 建议与 G0 同提交以简化版本号管理

G2 (test infra) ── 独立，可与 G0/G1 并行

G3 (S06 theme) ── 独立，但与 G6 共享 MainActivity.kt（需协调 hunk 拆分顺序）

G4 (S12 Legado) ── 独立，但与 G3 共享 AndroidManifest.xml（需协调 hunk 拆分顺序）

G5 (S14 backup) ── 依赖 R3 hunk 状态（B5 hunk 3 混合），JsonBridge hunk 3 → HOLD

G6 (V5 Duration) ── 独立，但与 G3 共享 MainActivity.kt

G7 (S05 finished) ── 依赖 grid helper 引用关系（B4 已拆分），BookGrid/ShelfRoute 逐 hunk → HOLD

G8 (Reader/pager) ── 已子拆分 G8a–G8e，EpubRepository/PageTurner 逐 hunk → HOLD
```

**推荐提交顺序：**
1. G0 + G1（Room v15 + 版本号，建议同提交）
2. G2（测试基础设施）
3. G3（S06 主题）+ G6（V5 Duration）— 共享 MainActivity.kt，建议同一人连续拆分
4. G4（S12 Legado）— B8/B9 已拆分
5. G5（S14 备份）— B5 hunk 3 HOLD 需集成者决定
6. G7（S05 完本）— B4 ShelfModels 已拆分，BookGrid/ShelfRoute HOLD
7. G8a–G8e（Reader/pager 子组）— EpubRepository/PageTurner HOLD

---

## 7. 可复用 D 阶段验证汇总

| 验证项 | 覆盖组 | 来源 | 修正 |
|--------|--------|------|------|
| D gate JVM 3208/3208 | G2, G4, G8（JVM 部分） | D exit review | **不含 androidTest**：`AppDatabaseMigrationTest`、`ParentUpsertCascadeSafetyTest`、`EpubReplaceRoomIsolatedTest`、`EpubParserInstrumentedTest` 等均不在 JVM 3208/3208 中 |
| S04 EPUB isolated Room 8/8 | G0, G2 | D exit review | 使用 in-memory Room，间接验证 migration 逻辑 |
| D build (Debug + androidTest APK) | G0, G1, G2 | D exit review | **仅编译通过**，未在设备上执行 androidTest |
| S06 theme D2 手机尺寸 | G3 | D exit review | |
| S12 MuMu real import acceptance | G4 | D exit review | |
| S14 JVM 22/22 | G5 | handoff §2 | |
| V5 62/62 | G2, G6 | D exit review | |
| S05 A/B MuMu | G7 | D exit review | |
| S05 C 420dpi 手机布局 | G7 | D exit review | **手机布局已验证，"重开即写库"未验证** |

---

## 8. 本次完成与未完成项

### 已完成

| 项 | 结果 |
|----|------|
| 全覆盖矩阵 | 376 文件 × 组/HOLD/排除，写入 E 盘 CSV（SHA-256 `eb289a71...`） |
| G8 子拆分 | G8a–G8e 五子组，文件级归属已判定 |
| B4 ShelfModels.kt | 3 类改动边界已判定：S05 + grid helpers → G7，smart search → 排除 |
| B5 JsonBridge/WebDavBackup | hunk 级部分判定：hunk 3 混合 R3+S14 → HOLD |
| B8 ReaderProgressEffects.kt | 14 hunks 已拆分：S12 = 3,4,8,12,14；非 S12 = 1,2,5-7,9-11,13 |
| B9 ProfileViewModel.kt | 13 hunks 已拆分：S12 = 1,2,4,9,10,12；非 S12 = 3,5-8,11,13 |
| 证据修正 | AppDatabaseMigrationTest androidTest 非 JVM；版本号 @Upsert 非 Room v15；S05-C 重开即写库未验证 |
| 编译状态修正 | 所有"✓ 通过"改为"待验证"（全量工作树 build 不证明拆分后单独编译） |

### 未完成（HOLD）

| 项 | 原因 | 后续需做 |
|----|------|----------|
| `EpubRepository.kt` 9 hunks | 跨 G8b + G8c + G8d 三子组 | 逐 hunk session 归属 |
| `PageTurner.kt` 24 hunks | T8 vs 其他 session | 逐 hunk session 归属 |
| `BookGrid.kt` 12 hunks / `ShelfRoute.kt` 18 hunks | S05 vs smart search | 逐 hunk 拆分 |
| `JsonBridge.kt` hunk 3 | 混合 R3 + S14 | 手动拆分或同提交 |
| `WebDavBackup.kt` hunk 4 | Legado 归属 | 确认归入 G4 或 HOLD |
| 180 个 HOLD 文件 | 矩阵中未判定归属的文件 | 逐文件 session 归属分析 |

---

## 9. 全覆盖矩阵汇总

**矩阵文件：** `E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-full-coverage-matrix.csv`
**SHA-256：** `eb289a71bca83fe5c6e8b4fee845fc4717008e1e278b8ab498c2fc30fd2ba66c`
**行数：** 376（不含表头）
**列：** file_path, status, group, sub_group, hunk_detail, reason, lines_changed

### 分组统计

| 组 | 文件数 | 说明 |
|----|--------|------|
| G0 | 5 | Room v15 schema + migration + androidTest |
| G2 | 4 | 测试基础设施 |
| G3 | 3 | S06 主题 |
| G4 | 9 | S12 Legado |
| G5 | 7 | S14 备份 |
| G6 | 6 | V5 Duration |
| G7 | 3 | S05 完本 |
| G8 | 103 | Reader/pager（G8a–G8e 子拆分） |
| EXCLUDED | 56 | 新功能，不纳入 E 恢复提交 |
| **HOLD** | **180** | **需逐文件/hunk 判定归属** |
| **合计** | **376** | |

### HOLD 文件分布（主要目录）

| 目录 | HOLD 文件数 | 说明 |
|------|-------------|------|
| `data/local/dao/` | ~20 | DAO 接口改动，归属待定 |
| `data/repository/` | ~15 | Repository 改动，跨多 session |
| `data/settings/` | ~10 | 设置数据类改动 |
| `feature/library/` | ~15 | 书库功能改动 |
| `feature/sync/` | ~10 | 同步功能改动（含 JsonBridge/WebDavBackup 部分 hunk） |
| `ui/components/` | ~20 | UI 组件改动 |
| `ui/screen/profile/` | ~15 | 个人页改动 |
| `ui/screen/shelf/` | ~15 | 书架页改动（含 BookGrid/ShelfRoute 逐 hunk） |
| `ui/screen/stats/` | ~10 | 统计页改动 |
| `ui/viewmodel/` | ~20 | ViewModel 改动 |
| `ui/screen/reader/` | ~30 | reader UI 改动（逐 hunk） |
| **合计** | **~180** | |

**注意：** HOLD 不等于排除。HOLD 文件是恢复改动的一部分，但当前无法判定归属哪个提交组。需在实施阶段逐文件/hunk 分析后归入对应组或独立组。

---

> 本文件仅为只读分析输出，不含任何源码/测试/配置改动。所有 hunk 拆分建议需在实施阶段由对应文件所有者用 `git add -p` 执行。编译边界均标"待验证"——全量工作树 build 通过不证明任一拆分后提交可独立编译。
