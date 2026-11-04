# 阶段 E — 数据/同步/配置 HOLD 项逐项判定（2026-09-29，只读）

> 窗口：Android 数据/同步/配置 HOLD（`data/**`、`feature/sync/**`、`feature/library/**`、
> `AndroidManifest.xml`、`android/app/build.gradle.kts` 及相关 schema/配置）
> 基线 HEAD：`5aed5939600e491843e3c44453921bd6273a233c`（分支 `codex/recover-worktree-20260920`）
> 输入：`E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-full-coverage-matrix.csv`
> （SHA-256 `eb289a71bca83fe5c6e8b4fee845fc4717008e1e278b8ab498c2fc30fd2ba66c`，376 行）
> 依据：[Android hunk 计划](2026-09-29-stage-e-android-hunk-plan.md)、[第二轮复核](../../reviews/2026-09/2026-09-29-stage-e-second-round-review.md)、[入口清单](2026-09-29-stage-e-entry-inventory.md)
> 逐项矩阵：`E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-hold-data-sync-matrix.csv`
> SHA-256 `dd8cc5083a8d94cb7dea6160c634444bfe7caf191ecf3809d122c5504f241663`（64 行 + 表头）

---

## 0. 结论

**接手 64 项**（矩阵中命中本窗口范围的 `HOLD` 37、`EXCLUDED` 9、`G5` 6、`G0` 5、`G4` 4、`G6` 3）。

| 判定 | 项数 | 含义 |
|---|---|---|
| **DECIDED** | **35** | 已给出 hunk 级归属、同组必带项与理由 |
| **EXCLUDED-INVALID** | **9** | 矩阵判 `EXCLUDED`，但被本窗口内 main 源码引用 ⇒ **排除后无法编译**，需裁决 |
| **HOLD** | **20** | 所属工作流在计划中**没有对应候选组**，或依赖未裁决项；已给出具体证据与缺口 |

其中 17 项原判 `HOLD` 已升为 DECIDED；6 项原判有组但**归属与内容不符**（见 §4）。

**本轮不宣告任何组「可独立编译」。** 全量工作树 JVM 3208/3208 与 build 成功**不能**证明拆分后任一子集可编译；下文凡写「可编译」的，都限定为「该 hunk 的符号在 HEAD 内自足，且已列出同组必带项」，不代表已实测。

---

## 1. 方法与只读边界

- 未改任何源码、测试、配置、schema；未 stage / commit / push / reset / checkout；未构建；未用设备。
- 唯一写入的仓库文件即本文件。矩阵 CSV 写在 E 盘 preflight 目录（不覆盖总矩阵）。
- 用到的只读证据：`git diff -U0/-U1/-U2/-U3`、`git status --porcelain`、`git ls-files`、
  schema JSON 逐表比对、`javap` 反编译 Gradle 缓存中的 `room-runtime-2.6.1` 字节码、全仓符号引用 grep。

---

## 2. 关键判定

### 2.1 G0（Room v15）**缺一项**，且该项是硬约束

**矩阵/计划的 G0 = 4 个文件；实际必须含第 5 项：`SearchTermDao.kt` 的 `indices` hunk。**

证据链（三层，均可复核）：

1. `android/app/schemas/…/14.json` 与 `15.json` **逐表比对**：26 张表无增删，**唯一差异**是
   `search_terms.indices` 去掉了 `index_search_terms_term`（14.json 有两项，15.json 只剩
   `index_search_terms_book`）；`identityHash` 由 `008e3a91…` 变为 `4dfc708f…`。
2. 该 `indices` 声明写在 `SearchTermDao.kt` 的 `@Entity search_terms` 上 —— Room 的 schema 导出
   正是由它生成，所以 15.json 与这个 hunk 是**同一处改动的两个面**。
3. Room 2.6.1 运行时校验（`javap -c` 实读 Gradle 缓存 `room-runtime-2.6.1`）：
   `TableInfo.equals` 对 `indices: Set` 用 `Intrinsics.areEqual` 做**集合相等**比较；
   `TableInfo$Index.DEFAULT_PREFIX = "index_"` ⇒ DB 侧 `index_*` 索引会被读入 found 集合。
   **若保留实体索引而迁移把它 DROP，开库时 found ≠ expected，`onValidateSchema` 失败。**

⇒ `AppDatabase.kt` + `DatabaseModule.kt` + `15.json` + `AppDatabaseMigrationTest.kt` **必须**与
`SearchTermDao.kt` 的 `indices` hunk 同提交，否则**升级后首次开库即崩**。这是本轮最高优先的修正。

`AppDatabaseMigrationTest.openingDatabaseRebuildsPartialIndexesAndDoesNotResurrectTermIndex`
（androidTest）正是走 `Room.databaseBuilder(...).build().openHelper.writableDatabase` 真开库的用例，
会触发该校验 —— 它同时是这条约束的守门测试。

### 2.2 `build.gradle.kts`：5 个 U0 hunk，G1 只有 1 个

U3 视图下是 4 个 hunk，但其中「版本号 + `testInstrumentationRunner`」在同一个块内。
按 **U0** 边界（已实测）实为 5 个，version 与 runner 之间隔着 1 行上下文空行 ⇒ `git add -p` 可用 `s` 拆开：

| U0 hunk | 内容 | 归属 |
|---|---|---|
| `@@ -13,0 +14,3 @@` | `testBuildType = providers.gradleProperty("craAndroidTestBuildType")…` | **G2** |
| `@@ -22,2 +25,6 @@` | `versionCode 2→3`、`versionName "0.5.0"→"0.5.1"` + 4 行注释 | **G1** |
| `@@ -25 +32 @@` | `testInstrumentationRunner` → `CraIsolatedTestRunner` | **G2** |
| `@@ -97,0 +105,10 @@` | `create("qahost")` build type | **G2** |
| `@@ -225,0 +243,3 @@` | `add("qahostImplementation", …ui.test.manifest)` | **G2** |

**版本号注释的语义父是父表 `@Upsert` 修复，不是 Room v15**：新增注释原文写
「0.5.1(3)：数据丢失修复（父表 `@Insert(REPLACE)` → `@Upsert`，见 `BookDao.upsert` 注释）」。
故 **G1 与 G0 无依赖关系**；G1 单独是纯字面量改动，其真正同组项是
`BookDao.kt` / `InspirationDao.kt` / `TaxonomyDao.kt` 的 `@Upsert` hunk 与
`ParentUpsertCascadeSafetyTest.kt`。

补充：文件工作副本是 CRLF（`git diff` 有 `CRLF will be replaced by LF` 警告），拆 hunk 前需确认行尾处理。

### 2.3 `AndroidManifest.xml`：2 hunk，分属两组

| hunk | 内容 | 归属 |
|---|---|---|
| `@@ -10,12 +10,15 @@` | 注释改写 + `<queries>` 增 `android.intent.action.TTS_SERVICE` | **G4**（S12 TTS 绑定可见性） |
| `@@ -34,6 +37,7 @@` | `MainActivity` 增 `android:configChanges="…|uiMode"` | **G3**（S06 启动窗口主题） |

两 hunk 不交错、互不依赖，可分别提交。

### 2.4 `JsonBridge.kt`：三族精确边界（21 个 U0 hunk）

| 族 | hunk 内容 | 归属 |
|---|---|---|
| **S04-5/R3**（来源引用 + 阅读器规则/纠错） | `SourceRefExport` / `TextRuleExport` / `CorrectionExport` 三个 DTO；`LocalExport` 三个 `@SerialName` 字段（`library_source_refs` / `reader_text_rules` / `reader_text_corrections`）；构造器三个 DAO 形参；`export()` 收集与装配；`importFromString` 内三段合并 | 建议 **R3/S04-5 组** |
| **S14**（合并结果快照，供文件保护） | `ImportMergeResult` 数据类；`importFromString` 增 `onMergeResult` 形参；`acceptedBookIds` / `skippedBookContentPaths` 两个局部量与赋值；事务提交后的回调调用 | **G5** |
| **imports** | 新增 `LibrarySourceRef*` / `ReaderCorrection*` / `ReaderTextRule*` / `kotlinx.serialization.SerialName`（服务 S04-5/R3）+ 若干导入重排 | 随 S04-5/R3（重排部分无语义） |

**精确边界结论**：S14 与 S04-5/R3 在 **hunk 级不交错**，`git add -p` 可干净分离。
但有两个**不可再分的连续块**，若要进一步把「R3 来源引用」与「阅读器规则/纠错」分开，必须**手工编辑 hunk**：
`@SerialName` 三字段在同一 +7 连续块内；三个 DTO 在同一 +146 连续块内；imports 块被两族共用。

**该族对 HEAD 自足**：`LibrarySourceRefDao/Entity`、`ReaderTextRuleDao/Entity`、`ReaderCorrectionDao/Entity`
经 `git status --porcelain` 核验为**已跟踪且未修改**（R3 核心已随 `e3fafad feat(library): add v14 source index migration and in-app book directory` 入库），
故 S04-5/R3 族**不需要任何未跟踪新文件**。

### 2.5 `WebDavBackup.kt`：矩阵/计划的归属与内容不符

**33 个 U0 hunk**，实际是两个工作流：

- **WebDAV 客户端升级**：`put` 增 MKCOL（409/404 后建目录并重试）、`ensureCollection`、
  `test` 改 PROPFIND + OPTIONS 回退、`get` 增 `withRetry`、`list` 增 `D:` 命名空间感知与 collection 过滤、
  `resolveTarget` 改 `HttpUrl`、`basicAuth` 显式 UTF-8、`isPermittedHttpHost`、`requireSafeFilename` 放宽 Unicode、
  `parsePropFind` 命名空间剥离。
- **Legado ZIP**：`getBytes`（带 `maxBytes` 上限的二进制下载）、`listLegadoBackups`（只列 `backup*.zip`）。

**证据**：全文 diff 中 **不出现 `S14` 字样**（grep 计数 0）；唯一的 `Legado` 出现在 `listLegadoBackups`。
⇒ 计划所写「`WebDavBackup.kt` 8 hunks（S14 + R3 混合）」与 hunk 3/4 的「S14（backup safety）→ G5」「hunk 4 Legado」
**在内容上不成立**。本文件没有 R3（`library_source_refs`）改动，也没有 S14 改动。

### 2.6 `G5` 的真实依赖是 `JsonBridge` 的 **S14** hunk，不是 R3

`LocalZipBackup.kt` 的文件复制接缝签名是
`private fun copyBookFiles(tmpBooks: File, realBooksDir: File, mergeResult: ImportMergeResult)`，
`ImportMergeResult` 由 `JsonBridge.kt` 的 S14 hunk 提供 ⇒ **G5 必须与 JsonBridge 的 S14 hunk 同提交**。
计划 §5/§6 所写「G5 依赖 R3 hunk」「JsonBridge hunk 3 混合 R3 + S14 需同提交」应更正为
**「G5 依赖 JsonBridge 的 S14 族」**。

`LocalZipBackup.kt` 本身 11 hunk、8 处 `S14` 标注，无 R3/Legado 内容 ⇒ 与矩阵 G5 一致。

### 2.7 `EXCLUDED` 有 9 项不成立（**跨文件编译边界**）

矩阵把下列未跟踪新文件判为 `EXCLUDED`（新功能），但它们**被本窗口内的 main 源码直接引用**。
一旦排除，引用方无法编译 —— 这正是不看整树 build 就发现不了的边界：

| 被判 EXCLUDED 的文件 | 引用它的 main 源码（本窗口内） | 后果 |
|---|---|---|
| `data/repository/SyncOptions.kt` | `SyncRepository.kt`（`pull/push` 形参类型）、`ProfileSyncEngine.kt`、`ProfileViewModel.kt` | 排除 ⇒ `SyncRepository` 27 hunk 全部无法编译 |
| `data/repository/SearchIndexBookRefreshCoordinator.kt` | `BookRepository.kt`（`indexRefreshCoordinator?.request()`）、`ShelfImporter.kt` | 排除 ⇒ `BookRepository` 的 S03 hunk 与 `ShelfImporter` 无法编译 |
| `data/settings/ReaderDayNightToggle.kt` | `SettingsStore.kt`（`dayNightLightPaper` 默认值引用其常量）、4 个 reader UI 文件 | 排除 ⇒ `SettingsStore` 无法编译 |
| `data/settings/SyncPrefsStore.kt` | `ProfileViewModel.kt` | 排除 ⇒ `ProfileViewModel` 无法编译 |
| `feature/library/ShelfSourceUpdateChecker.kt` | `ShelfImporter.kt`、`ShelfViewModel.kt` | 排除 ⇒ 二者无法编译 |

对应 4 个测试（`SearchIndexBookRefreshCoordinatorTest`、`ReaderDayNightToggleTest`、
`SyncPrefsStoreTest`、`ShelfSourceUpdateCheckerTest`）随其被测类一并裁决。

**这是「不能凭整树 JVM 3208/3208 声称拆分后组已独立编译」的具体实例**：整树编译通过，是因为这 5 个文件都在树里。

---

## 3. 逐项矩阵

**文件**：`E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-hold-data-sync-matrix.csv`
**SHA-256**：`dd8cc5083a8d94cb7dea6160c634444bfe7caf191ecf3809d122c5504f241663`
**行数**：64（不含表头）

列：`file_path, status, matrix_group, verdict, assigned_group, hunk_detail, co_required, reason`

### 3.1 已判定（35）

| 组 | 文件 |
|---|---|
| **G0**（5） | `15.json`、`AppDatabase.kt`、`DatabaseModule.kt`、`AppDatabaseMigrationTest.kt`、**`SearchTermDao.kt` 的 `indices` hunk** |
| **G1**（1 hunk） | `build.gradle.kts` 的 `@@ -22,2 +25,6 @@` |
| **G2**（4 hunk） | `build.gradle.kts` 其余 4 个 U0 hunk |
| **G3**（2） | `AndroidManifest.xml` configChanges hunk、`SettingsStoreBootMirrorTest.kt` |
| **G4**（5） | `AndroidManifest.xml` TTS hunk、`BookDao.getAllActive()`、`LegadoReadingProgressImporter.kt`、`LegadoMarkerRealImportTemporaryTest.kt`、`LegadoImportEpubLocatorContractTest.kt`、`LegadoReadingProgressImporterTest.kt` |
| **G5**（6） | `LocalZipBackup.kt`、`LocalZipBackupTest.kt`、`LocalZipBackupPersistentRestoreTest.kt`、`LocalExportRoomRollbackTest.kt` + JsonBridge 的 S14 族 |
| **G6**（3） | `DurationPrefs.kt`、`DurationPrefsTest.kt`、`DurationUnitTest.kt` |
| **G7**（2） | `StatsRepository.kt`、`ReadingStatsPolicyTest.kt` |
| **建议新组** | `@Upsert` 组（`BookDao`/`InspirationDao`/`TaxonomyDao` 的 `@Upsert` hunk + `ParentUpsertCascadeSafetyTest.kt`）；S03 搜索组（`SearchIndexRepository.kt`、`SearchIndexScheduler.kt`、`BookRepository` S03 hunk、`BookRepositorySearchIndexTest.kt`、`SearchIndexRepositoryRebuildConcurrencyTest.kt`）；S04 残留组（`BookRepository` S04 hunk、`BookResidueSafeCleanupTest.kt`）；R3/S04-5 组（`JsonBridge` S04-5 族、`WebDavBackupTest` 的 R3 用例）；WebDAV 升级组（`WebDavBackup.kt` + 其测试的 WebDAV 用例）；`BookRepositoryTest.kt`（S03 + S05 混合） |

### 3.2 `EXCLUDED` 不成立（9）

见 §2.7 表；CSV 中 `verdict = EXCLUDED-INVALID`。

### 3.3 仍 HOLD（20）与具体原因

| 文件 | 具体原因 |
|---|---|
| `data/repository/SyncRepository.kt` | 27 hunk 全部围绕 `SyncOptions` 形参；`SyncOptions.kt` 被判 EXCLUDED ⇒ 归属需先裁决「同步选项算恢复必需还是新功能」 |
| `data/settings/SettingsStore.kt` | 29 hunk / +283，跨「reader 设置键」与「S06 boot mirror」两族；且引用被判 EXCLUDED 的 `ReaderDayNightToggle` |
| `data/settings/PerBookSettingsStore.kt` | 属 reader 设置覆盖工作流，计划无候选组；组内其它文件不在本窗口，无法验证编译边界 |
| `data/settings/ReaderOverride.kt` | 同上 |
| `data/settings/ReadingPresets.kt` | 同上 |
| `data/settings/SelectionActionSettings.kt` | 属文本选区动作工作流，计划无候选组 |
| `data/settings/SelectionActionStore.kt` | 同上 |
| `data/settings/ShelfPrefs.kt` | `newBookIds` 新书角标工作流，计划未列 |
| `feature/library/LibraryRootStore.kt` | R3 增量，组内文件跨窗口未闭合 |
| `feature/library/LibrarySource.kt` | 同上（`LibraryEntry` 排序） |
| `feature/library/LibrarySourceIndex.kt` | 同上（SAF 文档元数据查询） |
| `feature/library/ShelfImporter.kt` | 31 hunk 跨 S04/S05/R3 三族，且引用两个被判 EXCLUDED 的新文件 |
| `test/…/data/settings/PerBookSettingsStoreTest.kt` | 随 reader 设置覆盖组 |
| `test/…/data/settings/ReaderSettingsOverlayTest.kt` | 同上 |
| `test/…/data/settings/ReaderSettingsRouterTest.kt` | 同上 |
| `test/…/data/settings/ReadingPresetsTest.kt` | 同上 |
| `test/…/data/settings/SelectionActionSettingsTest.kt` | 随选区动作组 |
| `test/…/data/settings/SettingsStoreTest.kt` | 随 reader 设置组；被 EXCLUDED 依赖链波及 |
| `test/…/data/settings/ShelfPrefsTest.kt` | 随新书角标组 |
| `test/…/feature/library/SafLibrarySourceTest.kt` | 随 R3 增量组 |

**HOLD 不等于排除。** 这 20 项都是恢复改动的一部分，缺的是「组边界」与「跨窗口编译边界」，
不是证据。它们集中在三类工作流：**reader 设置覆盖 / 选区动作 / 书架新书角标 / 同步选项 / R3 增量**。

---

## 4. 对总矩阵的修正建议

| # | 矩阵/计划现值 | 建议更正 | 证据 |
|---|---|---|---|
| 1 | `ParentUpsertCascadeSafetyTest.kt` = **G0** | → **`@Upsert` 组**（G1 的语义同族） | 用例断言 `bookDao.upsert` 保留子表行，对应 0.5.1(3) 注释所指的父表 `@Upsert` 修复，与 v14→v15 索引迁移无关 |
| 2 | `BookResidueSafeCleanupTest.kt` = **G5** | → **S04 残留安全清理组** | 用例测 `clearBookCache` 的残留保护（`docs/coordination/operations/2026-09/2026-09-25-s04-residue-safe-cleanup-implementation.md`），无 S14 内容 |
| 3 | `WebDavBackupTest.kt` = **G5** | → **WebDAV 升级组 + R3 用例组** | +472 行 10 个新用例中无 S14 断言（文件内 `S14` 仅 1 次且非用例） |
| 4 | `WebDavBackup.kt` 描述为「S14 + R3 混合」 | → **WebDAV 客户端升级 + Legado ZIP** | diff 内 `S14` 出现 0 次；无 `library_source_refs` |
| 5 | G0 = 4 个文件 | → **G0 = 4 文件 + `SearchTermDao.kt` 的 `indices` hunk** | 15.json 与 14.json 唯一差异即该索引；Room `TableInfo.equals` 对 indices 做集合相等 |
| 6 | G5「依赖 R3 hunk」 | → **G5 依赖 JsonBridge 的 S14 hunk** | `copyBookFiles(…, mergeResult: ImportMergeResult)` |
| 7 | 5 个新文件判 `EXCLUDED` | → **`EXCLUDED` 不成立**，须随引用方同组或连引用 hunk 一并排除 | §2.7 的 main 源码引用清单 |
| 8 | 矩阵无 G1 行 | 保持（`build.gradle.kts` 整行 HOLD 无法表达多归属）；由本文件 §2.2 的 U0 hunk 表替代 | 第二轮复核已指出 |

---

## 5. 不得外推 / 编译边界声明

1. 本文件**未运行任何构建或测试**；所有「可编译」表述均限定为「符号在 HEAD 内自足 + 已列同组必带项」。
2. **全量工作树 JVM 3208/3208、D 阶段 build 成功，均不能证明任一拆分后子集可编译**；
   本轮给出的反例是 §2.7：整树编译通过恰恰因为 5 个 EXCLUDED 文件都在树里。
3. `AppDatabaseMigrationTest.kt`、`ParentUpsertCascadeSafetyTest.kt`、`LegadoMarkerRealImportTemporaryTest.kt`、
   `LocalExportRoomRollbackTest.kt`、`LocalZipBackupPersistentRestoreTest.kt` 均位于 `src/androidTest/`，
   **不在 JVM 3208/3208 范围内**；它们的验证来源是编译通过 + 各 session 的设备报告。
4. 本窗口**未覆盖**：UI（非 reader）、reader/pager G8 及其测试；这些由其他窗口负责，本文件不领取。
5. 本轮**未裁定**「同步选项 / reader 设置覆盖 / 选区动作 / 新书角标 / R3 增量 / WebDAV 升级」是否属
   收口计划 §E 第 93 行的「直接相关源码」—— 需总指挥裁决后才可进入候选清单。
6. 本文件不含任何源码、测试、配置或 schema 改动，未 stage / commit / push / reset / checkout。

---

## 6. 建议下一步（供总指挥裁决，不自行执行）

1. **先落 G0 完整版**（含 `SearchTermDao.kt` 的 `indices` hunk）—— 缺它会升级即崩，属数据风险。
2. **裁决 5 个 EXCLUDED 文件**的归属（§2.7）；在此之前 `SyncRepository` / `ShelfImporter` /
   `SettingsStore` / `BookRepository` S03 hunk 都不应进入任何提交。
3. **裁决 5 类无组工作流**（reader 设置覆盖 / 选区动作 / 新书角标 / 同步选项 / WebDAV 升级）
   是否纳入 E 恢复提交；若不纳入，需把引用它们的 hunk 一并排除并重算编译边界。
4. 需要跨窗口合并时，`WebDavBackupTest.kt` 与 `BookRepositoryTest.kt` 需按用例拆分归属
   （二者各自跨两个工作流）。
