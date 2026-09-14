# Android R3（应用内书籍目录、来源索引、增量更新）只读集成审阅报告

> 日期：2026-09-13
> 基线：`main` / `6cd29670e54c3fc2915d876896ecbaae8d000aa4`（HEAD 最新 schema 为 `12.json`）。
> 性质：**只读审阅**。本文不授权 stage / commit / push / reset / checkout / clean / stash；
> 未运行任何 Gradle 构建，未安装 APK，未做真机操作，未修改任何 Android 产品源码、Room schema、测试、脚本或设备数据。
> 输入：`git diff HEAD -- android` 全量已跟踪改动（98 条路径）+ `git ls-files --others --exclude-standard -- android`（85 条未跟踪路径）逐 seam 核对，
> 以及 [AGENTS.md](../../AGENTS.md)、[docs/handoff/current.md](../handoff/current.md)、
> [2026-09-13-android-wip-integration-plan.md](2026-09-13-android-wip-integration-plan.md)、
> [2026-09-13-android-r3-slice1-integration-manifest.md](2026-09-13-android-r3-slice1-integration-manifest.md)、
> [2026-09-12-android-library-folder-smart-recognition-roadmap.md](2026-09-12-android-library-folder-smart-recognition-roadmap.md)、
> 两份交付报告（`android-parallel-delivery-2026-09-09/reports/{workbuddy-r4-library-folder,zcode-r4-library-source-index}.md`）。

## 0. 结论速览

- R3 的代码在工作区**完整且自洽**，7 项已确认产品决定全部符合（§6）。
- R3 **不能**按"整个 AppDatabase diff"提交：v12→v13（ReaderCorrection，Reader 切片前置）与 v13→v14（R3）在
  `AppDatabase.kt` / `DatabaseModule.kt` / `AppDatabaseMigrationTest.kt` / `13.json` 中交织，必须按 §4 的顺序二选一。
- 5 个混合文件需要 **hunk 级拆分**（§2.C），其中 `ShelfViewModel.kt` 的全部改动属 L1 动态视图（非 R3）——
  这是对 manifest §2 归属表的一处**修正**。
- `ImportSourceSheet.kt` 是书架 UI 拆分（切片 4）的产物，**不得**进入 R3 提交，否则与 HEAD 版 `ImportSheets.kt`
  产生 `ImportSourceSheet` 重复声明编译冲突（§2.E）。
- 判定：**可进入"候选独立提交"状态**（有条件，见 §9）——前提是完成 §4 的迁移顺序选择与 §2.C 的 hunk 拆分。

---

## 1. R3 候选文件清单（应进入同一个未来 R3 提交）

### 1.A 未跟踪新文件——整文件纯 R3（主代码 14 个）

| # | 文件（`android/app/src/main/java/com/creationreadingassistant/` 下） | 职责 |
|---|---|---|
| 1 | `data/local/entity/LibrarySourceRefEntity.kt` | `library_source_refs` 实体（主键 `book_id`，FK→books CASCADE）+ `LibrarySourceAvailability` |
| 2 | `data/local/dao/LibrarySourceRefDao.kt` | upsert / getAll / getByFingerprint / updateObservation / updateAvailability / backfillRootId |
| 3 | `feature/library/LibraryRootStore.kt` | DataStore 单一 SAF 根目录、显示名、上次位置、排序（含 `loadRoot()`） |
| 4 | `feature/library/LibrarySource.kt` | `LibrarySource` 接口 + `SafLibrarySource`（单目录 ≤1000 项、UNREADABLE 语义、`LibraryListing.truncated`） |
| 5 | `feature/library/LibrarySourceModule.kt` | Hilt 绑定 `LibrarySource → SafLibrarySource` |
| 6 | `feature/library/LibrarySourceIndex.kt` | 来源索引（`SourceKey` / `sourceRootIdFor` 边界匹配 / `SourceReconciliation.plan` 纯函数 / markObserved / reconcileAfterScan） |
| 7 | `feature/library/SmartBookRecognizer.kt` | 识别接口、事件流、`RecognitionLimits`（2000 项 / 500 候选 / 16 层 / 64 KiB 样本）、`RecognitionSummary.truncated` |
| 8 | `feature/library/SafSmartBookRecognizer.kt` | 生产 Adapter；>32 MiB 头块复用 + 尾块跳读产指纹；`ensureActive` 取消 |
| 9 | `feature/library/SourceFingerprint.kt` | `fp1|size|headMd5|tailMd5`（256 KiB 首尾），读不满整块即放弃 |
| 10 | `ui/viewmodel/LibraryBrowserModels.kt` | `LibraryShelfMatchKind`（priority ①<④<②<③）、UI 模型 |
| 11 | `ui/viewmodel/LibraryBrowserPolicy.kt` | §5.6 四级判定纯函数 + 分档过滤 + 默认勾选口径 |
| 12 | `ui/viewmodel/LibraryBrowserViewModel.kt` | 浏览/识别状态机；只读来源，导入交 `ShelfViewModel`；观测仅显式动作 |
| 13 | `ui/screen/shelf/LibraryBrowserRoute.kt` | `shelf/library` 页面主体（OpenDocumentTree 授权、面包屑、识别、确认档单击 `reader/{bookId}`） |
| 14 | `ui/screen/shelf/LibraryBrowserComponents.kt` | 根目录卡/面包屑/排序/目录行/文件行/多选底栏/识别面板 |

### 1.B 未跟踪新文件——测试（7 个）

`app/src/test/java/com/creationreadingassistant/` 下：
`feature/library/LibraryRootStoreTest.kt`、`LibrarySourceIndexTest.kt`、`SafBookSourceScannerTest.kt`、
`SafLibrarySourceTest.kt`、`SmartBookRecognizerTest.kt`、`SourceFingerprintTest.kt`、
`ui/viewmodel/LibraryBrowserPolicyTest.kt`。
（对应 manifest §2 的 9 个回归测试类中，`ShelfImporterTest`/`ShelfViewModelTest` 为已跟踪修改，见 1.C/1.D。）

### 1.C 已跟踪修改——diff 全部属 R3（整文件入提交）

| 文件 | diff 概要 |
|---|---|
| `feature/library/ShelfImporter.kt` | +96：注入 `LibrarySourceIndex`/`LibraryRootStore`；>32 MiB 走 `SourceFingerprint`；`uriLastModified` 基线；导入成功 best-effort `recordSourceRef`；指纹去重（引用挂在在册书才判重） |
| `feature/library/SafBookSourceScanner.kt` | +17/-9：`maxVisitedFiles`→`maxVisitedEntries`，先计数后分类——纯目录深树不能绕过 2000 项预算 |
| `ui/screen/shelf/ShelfSharedComponents.kt` | +1：`SHELF_LIBRARY_ROUTE = "shelf/library"` 常量（注：该文件有 CRLF 提示，但内容 diff 仅此 1 行，正常提交不会混入格式化噪音） |
| `app/src/test/.../ui/viewmodel/ShelfImporterTest.kt` | +152：4 条 R3 用例（落引用不伪造 root / 重复导入不重写引用 / 在册指纹判重 / 已删书指纹不阻再导入）+ 测试桩 |

### 1.D 已跟踪修改——混合文件，R3 hunks 必须精确挑出（hunk 级拆分）

| 文件 | R3 hunks（必须进 R3 提交） | 非 R3 hunks（必须留在其他切片） |
|---|---|---|
| `data/local/AppDatabase.kt` | `MIGRATION_13_14` 全块、`LibrarySourceRefDao`/`LibrarySourceRefEntity` import、`librarySourceRefDao()` 抽象、实体列表 `LibrarySourceRefEntity`、版本注释 v13→v14 段 | v12→v13 全部：`ReaderCorrectionEntity`/`ReaderCorrectionDao` import、`MIGRATION_12_13` 块、`readerCorrectionDao()`、`APP_DATABASE_SCHEMA_VERSION` 的 v13 语义段 |
| `data/local/DatabaseModule.kt` | `MIGRATION_13_14` 注册、`provideLibrarySourceRefDao`、`LibrarySourceRefDao` import、注释 13→14 段 | `MIGRATION_12_13` 注册、`provideReaderCorrectionDao`、`ReaderCorrectionDao` import、注释 12→13 段 |
| `androidTest/.../AppDatabaseMigrationTest.kt` | `migrate_13_to_14_creates_library_source_refs`、`migrate_12_to_14_full_chain_preserves_data_and_creates_both_tables`（注意：链路用例同时覆盖 12→13，归属见 §4） | `migrate_12_to_13_adds_reader_text_corrections` |
| `ui/navigation/AppNavigation.kt` | `shelf/library` composable 块 + `LibraryBrowserRoute`/`SHELF_LIBRARY_ROUTE` import + graph 作用域 `ShelfViewModel` 注释 | `Icons.AutoMirrored.Outlined.MenuBook` 图标修正；灵感 `onOpenRoute`（R5-I2） |
| `ui/screen/shelf/ShelfRoute.kt` | `ImportSourceSheet(...)` 调用中的 `onOpenLibrary = { navigate(SHELF_LIBRARY_ROUTE) }` 参数 hunk | L1 动态视图全部（`savedViews`/`FilterSheet` 回调）；`onImportFromDesktop`/`showDesktopBooks`/`DesktopBooksSheet` 接线 |
| `app/src/test/.../ui/viewmodel/ShelfViewModelTest.kt` | 构造 `ShelfImporter` 时的 `sourceIndex = mockk(relaxed=true)` + `rootStore` 桩（ShelfImporter 新构造参数的**编译必需**修复） | L1 动态视图全部：`savedViewsStore` 桩、`SavedShelfFilter` import、2 条 `apply/save` 用例 |

> **对 manifest 的修正**：manifest §2 把 `ShelfViewModel.kt` 列入切片归属，但当前 diff 中 `ShelfViewModel.kt`
> 的 +53 行**全部是 L1 书架动态视图**（`ShelfSavedViewsStore`/`SavedShelfFilter`/save-apply-delete），没有任何 R3 改动——
> R3 提交**不需要**该文件（Hilt 会自动解析 `ShelfImporter` 新参数，两个依赖均已是 `@Singleton @Inject`）。
> manifest 所列 `ShelfImportRoute.kt`、`ShelfRoute.kt`、`AppNavigation.kt` 仍按本表 hunk 口径成立。

### 1.E 归属需集成者裁决的文件

| 文件 | 现状与建议 |
|---|---|
| `android/app/schemas/.../13.json` | **共享**：它是 ReaderCorrection（v12→v13）的 schema，不是 R3 的。应随 §4 的前置提交走，不进 R3 提交 |
| `android/app/schemas/.../14.json` | **R3**：`version=14`、含 `library_source_refs` 建表与 4 索引，已与 `MIGRATION_13_14` SQL 逐字段一致 |
| `android/.gitignore`（+7 行根级 `/*.xml` 等产物忽略） | 真机测试工件卫生改动，非 R3 功能代码。建议单独卫生提交或随切片 0；如需并入 R3 须集成者明示 |
| `ui/screen/shelf/ImportSourceSheet.kt`（未跟踪，164 行） | **排除出 R3**（理由见 §2 首条）。它是 WorkBuddy §6.1 三入口 Sheet 的新载体，但由书架 UI 拆分产生，与 `ImportSheets.kt` 删除、`DesktopBooksSheet.kt`、`ImportHistorySheet.kt` 互为编译整体 |

---

## 2. 应排除的文件清单（不得混入 R3 提交）

1. **`ImportSourceSheet.kt` + `ImportSheets.kt`（-855 行拆分）+ `ImportHistorySheet.kt` + `DesktopBooksSheet.kt` + `ShelfRoute.kt` 的导入 Sheet 重接线**——书架 UI 切片（集成计划切片 4）。编译闭包证据：HEAD 版 `ImportSheets.kt` 第 443 行已声明 `internal fun ImportSourceSheet`，与新文件重复声明；`onImportFromDesktop` 回调指向切片 4 的 `DesktopBooksSheet`。R3 在该提交形态下的用户可见入口是 `ShelfImportRoute.kt` 的「我的书籍目录」渠道行（`ImportChannelRow` 定义于该文件内部第 622 行，自包含）。
2. **ReaderCorrection（v12→v13）数据层**：未跟踪的 `data/local/dao/ReaderCorrectionDao.kt`、`data/local/entity/ReaderCorrectionEntity.kt`，及 §1.D 表中所列各文件的 12→13 hunks、`13.json`——Reader 切片 2 的前置，按 §4 串行。
3. **Reader / 字典切片**：`feature/reader/**`（doc/pager/rules 全部修改）、`ui/screen/reader/**`、`feature/dictionary/*`（未跟踪）、`DictionaryViewModel.kt`、`reader/sheets/*` 未跟踪新文件、`data/settings/{PerBookSettingsStore,ReaderOverride,ReaderSettingsRouter,ReadingPresets,SelectionAction*,}.kt` 及对应测试（`CorrectionProjectionTest`、`ReaderSettings*`、`PerBookSettingsStoreTest`、`SelectionActionSettingsTest`、`ReadingPresetsTest`、`DictionaryEmptyNoticeTest`、`ReaderViewModelTest` 等）、`feature/search/*`、`SearchViewModel.kt`、`ClipboardUtils.kt`。
4. **首页 / 档案 / 灵感 / Profile / 统计切片**：`ui/screen/home*`、`homearchive/*`（删+新拆）、`profile/*`、`inspiration/*`、`stats/components/*`、`Motion.kt`、`HomeViewModel`、`MyReadingViewModel`、`ProfileViewModel`、`InspirationViewModel`、`InspirationMaterialOps`、`HomeScreenComposeTest`、`ReadingSessionRecorderPersistenceTest`、`ui/theme/CountUpStartValueTest` 等。
5. **L1 书架动态视图**：`data/settings/ShelfSavedViewsStore.kt`（未跟踪）、`ShelfViewModel.kt` 全部改动、`ShelfViewModelTest.kt` 的 L1 hunks、`ShelfRoute.kt` 的 savedViews hunks、`FilterSheet.kt`。
6. **共享数据 / 同步 / 安全（切片 5）**：`feature/sync/JsonBridge.kt`（设备 ID 注入改造）、`data/remote/DeviceInfoProvider.kt`、`data/security/SecurePrefs.kt`、`data/repository/SearchIndex*`、`SyncRepositoryTest`、`data/settings/ShelfPrefs` 相关以外的 settings 文件、`ui/components/GlassDialogs.kt`。
7. **安装脚本（切片 6）**：`android/scripts/install_with_confirm.ps1`。
8. **R2 checkpoint 编译修复**：`androidTest/.../BookDeletionPersistenceTest.kt`（+3 行，为 `BookRepository` 补 `context` 参数）——归属 R2/书库仓储切片，与 R3 无关。

---

## 3. Migration 依赖顺序

```
HEAD：schema 12（已提交）
   │
   ├─ v12→v13  ReaderCorrection：reader_text_corrections
   │            文件：ReaderCorrectionEntity/Dao（未跟踪）+ AppDatabase.kt + DatabaseModule.kt
   │                    + AppDatabaseMigrationTest.kt + 13.json        【Reader 切片 2 前置】
   │
   └─ v13→v14  R3：library_source_refs
                文件：LibrarySourceRefEntity/Dao + AppDatabase.kt + DatabaseModule.kt
                        + AppDatabaseMigrationTest.kt + 14.json          【R3】
```

- 两个版本跳跃**同时**存在于同一个未提交工作区；`APP_DATABASE_SCHEMA_VERSION` 一口气是 14。
- **不可只挑 `MIGRATION_13_14` 单独提交**：`AppDatabase` 的实体列表/DAO 声明/import 与 `13.json` 缺失会让
  `MigrationTestHelper.createDatabase(TEST_DB, 13)` 无 schema 可载，且编译期就引用 `ReaderCorrectionEntity`。
- 两条合法顺序（manifest §4 已定，本审阅确认可执行）：
  1. **串行**：先落「最小 ReaderCorrection 数据层前置提交」（ReaderCorrectionEntity/Dao + 13.json + 各文件 12→13 hunks，
     不含 Reader UI），再落 R3 提交（14 侧 hunks + §1 全部文件）。混合文件按 §1.D 拆 hunk。
  2. **同序列**：由同一集成者在单一数据库版本序列中一次审阅、分两个连续提交（先 13 后 14）落库。
- 提交后验证锚点：`14.json` 的 `createSql` 与 `MIGRATION_13_14` SQL 逐字段一致（本审阅已核对列序、NOT NULL、
  主键、FK CASCADE、4 个索引名 `index_library_source_refs_*`）；`13.json` 无 `library_source_refs`、
  `12.json`（HEAD）无 `reader_text_corrections`，边界干净。

---

## 4. 每个 seam 的调用链

### 4.1 Room v13→v14（不可拆的编译/运行契约）
`ShelfImporter.recordSourceRef` → `LibrarySourceIndex.record` → `LibrarySourceRefDao.upsert` → `library_source_refs` 表
（`LibrarySourceRefEntity`，FK `book_id → books.id ON DELETE CASCADE`）。
DDL：`AppDatabase.MIGRATION_13_14`（先 `dropPartialIndexes`，只建表+索引）→ `DatabaseModule.addMigrations(...)` 注册 →
KSP 导出 `14.json`。校验：`AppDatabaseMigrationTest` 两条用例（单段 + 12→13→14 连续链路）。

### 4.2 AppDatabase / DatabaseModule
同 4.1；另 `DatabaseModule.provideLibrarySourceRefDao` 供 Hilt 注入 `LibrarySourceIndex(@Singleton @Inject)`。
`ShelfViewModel` 无需改动即可获得新 `ShelfImporter`（Hilt 直接构造）。

### 4.3 schema JSON
`13.json`（v13，ReaderCorrection 前置）与 `14.json`（v14，R3）均为未跟踪；`exportSchema=true`。
提交顺序必须 13 在前（§3）。

### 4.4 migration test
`AppDatabaseMigrationTest`（androidTest）：`migrate_13_to_14_creates_library_source_refs`（可空列语义、4 索引、
CASCADE 删书清引用、既有数据保留）与 `migrate_12_to_14_full_chain_...`（v12 书/进度/高亮在两段迁移后完好、
两表可写可读、全部索引、双表级联）。门禁状态：已过 `compileDebugAndroidTestKotlin`，**未在设备执行**（§8-R2）。

### 4.5 LibrarySourceRef（来源引用领域流）
写入：`ShelfImporter.importOne`（`SourceFingerprint.compute` 仅 >32 MiB；`uriLastModified` 基线）→ 导入成功后
`recordSourceRef`（`runCatching` best-effort；`sourceRootIdFor` 边界匹配，证明不了归属则 `root_id=null`）。
消费：`LibrarySourceIndex.snapshot/getByFingerprint` → `LibraryBrowserPolicy.shelfMatch`（四级，弱判定无 `bookId`）→
`LibraryBrowserViewModel`（uiState/recognition combine）→ `LibraryBrowserRoute/Components`（MicroTag 分层文案、
「已入架」页签、确认档单击 `reader/{bookId}`）。

### 4.6 ShelfImporter（唯一书架写入方）
入口三处：`LibraryBrowserRoute.onImport`（浏览多选/识别批选）与 `ShelfRoute`/`ShelfImportRoute` 既有入口 →
`ShelfViewModel.importFiles`（graph 作用域同一实例）→ `ShelfImporter.importBatch → importOne`：
`FormatClassifier` 判定 → 元数据（大小/魔数/`contentHashOrNull`（≤32 MiB 全量 MD5，超限 null）/指纹/lastModified）→
判重（指纹引用挂在在册书 ‖ 批内指纹 ‖ 在册书 local_uri/文件名+大小）→ 复制私有副本 → 解析 metadata →
章节索引 → 写库 → `recordSourceRef`。删除链不动：`feature/library/deletion/*` 零 diff。

### 4.7 LibraryBrowserPolicy / Browser UI
`LibraryBrowserViewModel.loadDirectory → SafLibrarySource.list`（单目录 1000 上限、`UNREADABLE`）→ `observeListing →
markObserved`（只刷观测列，绝不判 missing）；`startRecognition → SafSmartBookRecognizer.recognize`
（`SafBookSourceScanner.discover`（2000 项合计预算）+ `probe`（64 KiB 样本 + 大文件指纹））→
`CandidateClassified` 即时定档（快照口径）→ `Completed → reconcileAfterScan(allowMissing = !summary.truncated)`。
取消：`stopRecognition` / 页面退出 → `recognitionJob.cancel()` → `CancellationException` 原样上抛，`finally` 只收敛
运行态，**不产生任何扫描完成结论**。

### 4.8 来源更新 / 失效 / 截断 / 取消边界（汇总）
- **更新**：判定级④——同 `authority+documentId` 但大小/修改时间相对导入基线变化 → `CONTENT_UPDATED`（有 bookId）。
- **失效**：仅 `reconcileAfterScan(allowMissing=true)`，且只对「同根、此前 `available`、documentId 非空、本次完整扫描未出现」生效；
  单目录浏览的 `markObserved` 永不判 missing；null documentId / 其它根 / 已 missing 的引用都不参与。
- **截断**：`SafBookSourceScanner` 2000 项按**所有 provider 行（文件+文件夹）**计数（本轮修复纯目录树绕过），
  `maxBookFiles=500`、`maxDepth=16`；`SafLibrarySource` 单目录 1000；`RecognitionSummary.truncated` 逐级上传 UI。
- **取消**：扫描/识别全链 `ensureActive`；取消 ≠ 完成，不落任何对账。
- **刷新时机**：进入/显式刷新目录、识别开始、识别完成——零新增轮询。

---

## 5.（保留节号与 manifest 对齐）产品决定符合性核对

| # | 已确认决定 | 结论 | 证据 |
|---|---|---|---|
| 1 | 保留 SAF `OpenDocumentTree` | **符合** | `LibraryBrowserRoute.kt:77` 根目录授权用 `OpenDocumentTree` + `takePersistableUriPermission` 成功才落库；旧文件夹导入链完整保留：`ShelfRoute.kt:85/255`（`importFolderLauncher`→`ShelfViewModel.importFolder`）、`ShelfImportRoute.kt:125`（folderPicker） |
| 2 | 不申请 `MANAGE_EXTERNAL_STORAGE` | **符合** | `grep -rn MANAGE_EXTERNAL_STORAGE android/app/src/main/` 零命中 |
| 3 | 不因旧 HyperOS 报告实现多文件选择回退 | **符合** | 无任何回退 UI；`OpenMultipleDocuments` 仅作路线既定的「从系统选择文件」备用入口；handoff 2026-09-13 复现已推翻旧 BLOCKED 结论 |
| 4 | 不伪造系统选择器来源 rootId | **符合** | `sourceRootIdFor`：authority 一致 + documentId 以树根为前缀且停在边界（`primary:Books` 不吞 `primary:Books2`），否则 null；`ShelfImporterTest`「without fabricated root」；迁移测试显式断言 `root_id` 可为 NULL |
| 5 | >32 MiB 只用候选指纹，不冒充内容哈希 | **符合** | `contentHashOrNull` 对 `size > MAX_HASH_BYTES`(32 MiB) 返回 null——全量 MD5 列与指纹列物理分离；指纹带 `fp1` 版本号、只进出 `candidate_fingerprint` 与去重提示，代码注释与测试均锁定「不是安全签名、不参与内容覆盖」 |
| 6 | 来源失效仅在完整、未截断扫描后判断 | **符合** | `reconcileAfterScan(allowMissing = !event.summary.truncated)`；`SourceReconciliation.plan` 纯函数守卫；`LibrarySourceIndexTest` 锁定其它根/null id/已 missing 不参与 |
| 7 | 删除书架记录不删除来源文件 | **符合** | 引用仅随 books 行 FK CASCADE 清除（迁移测试断言删书后引用清零）；`BookDeletionCoordinator` 及 deletion 目录零 diff（外部来源文件从未进入删除链）；「来源失效后内部副本可读」由 WorkBuddy A–E 真机验收覆盖 |

---

## 6. 冻结后需要运行的定向测试

> 仅在切片冻结、且不与其他构建抢占时执行；全量门禁（JVM 全量 / lint / assembleDebug / 真机）由集成者在全部切片结束后串行跑，不在本清单。

```powershell
# 在 android/ 目录
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.library.*" `
  --tests "com.creationreadingassistant.ui.viewmodel.LibraryBrowserPolicyTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" `
  --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest"
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

- 范围说明：通配会连带既有 library deletion/classifier 测试（manifest §6 已记录，15 类 155 项基线）；
  `ShelfViewModelTest` 整类运行会包含 L1 动态视图用例——作为门禁可接受，但它**不能**作为「R3 提交不含 L1」的证据
  （证据以 §1.D 的 hunk 清单为准）。
- `compileDebugAndroidTestKotlin` 覆盖 `AppDatabaseMigrationTest` 的 13→14 与 12→13→14 用例编译。
- 提交前另跑 `git diff --check -- <R3 文件>`（本审阅实测 R3 已跟踪文件 EXIT=0，无空白符错误）。
- 设备侧 migration 断言（`connectedDebugAndroidTest` 的 `AppDatabaseMigrationTest`）归 WorkBuddy；
  注意 handoff 记录的既有风险：connected 测试结束会卸载主应用。

---

## 7. 不足以由当前历史门禁证明的风险

1. **备份导出不含 `library_source_refs`**：`JsonBridge`（切片 5 的 WIP）的导出/导入清单未纳入来源引用表。
   备份恢复后「精确已入架/内容有更新」判定退化为弱判定，且导入基线（document_id/display_name/size/last_modified/hash）
   只能靠重新导入或观测回填部分重建（`backfillRootId` 只补 root_id）。该表按设计非正文事实源，降级可接受与否是**产品决定**，
   需要显式记录或列为后续切片（备份完整性契约此前已有「新增实体必须扩展 JsonBridge」的教训）。
2. **仪器化迁移测试未在设备执行**：13→14 用例只过了编译门禁。WorkBuddy A–E 验收跑在已升级设备上，但文档中未见
   受控的单段 on-device v13→v14 断言记录。真机验收归 WorkBuddy，不阻塞候选状态。
3. **hunk 级拆分的操作风险**：§1.D 的 6 个混合文件中，`AppDatabase.kt`/`DatabaseModule.kt`/`AppDatabaseMigrationTest.kt`
   的两段迁移 hunks 空间上相邻（import 交错）。用 `git add -p` 挑 hunk 时漏挑或多挑一行都会造成「编译过但迁移链不完整」
   或「混入 ReaderCorrection」。缓解：拆分后先编译、再按 §3 锚点核对 `13.json`/`14.json` 归属。
4. **`ShelfImporter` 构造签名变更的隐性消费者**：任何并行切片若手工构造 `ShelfImporter`（目前仅 `ShelfImporterTest`、
   `ShelfViewModelTest` 两处测试）必须同步补桩；冻结后若其他切片新增构造点，需交回集成者。
5. **扫描器语义收紧**：`maxVisitedEntries` 把文件夹也计入 2000 预算，改变既有「从文件夹导入」路径的截断点
   （比旧行为更早截断）。已有 `SafBookSourceScannerTest` JVM 回归与路线文档背书，属有意变更，但在冻结后的真机矩阵中
   值得带一条深目录用例。
6. **`LibrarySourceAvailability.fromStorage` 对未知值宽容回退 `AVAILABLE`**：损坏值不会崩溃，但会把未知态当可见；
   当前仅两态、写入方唯一，风险低，记录备查。
7. **CRLF 提示文件不在 R3 清单内**：计划 §2.3 列出的行尾转换文件（`ImportSheets.kt` 等）全部属其他切片；
   R3 唯一相关的是 `ShelfSharedComponents.kt`（+1 行，正常提交无格式化噪音）。提交时勿对 R3 文件做全文件重排。

---

## 8. 是否可以进入「候选独立提交」状态

**可以（有条件）**。R3 具备候选独立提交的全部要素：

- 功能闭环完整（授权→浏览→识别→导入→落引用→四级判定→观测/失效对账→截断/取消边界），7 项产品决定全部符合（§5）；
- 文件归属可精确枚举（§1），排除清单明确（§2），seam 调用链与迁移顺序已核对（§3–§4）；
- 定向 JVM 门禁历史绿（manifest §6：15 类 155 项 / 0 fail，含 9 个 R3 测试类 88 项），`compileDebugAndroidTestKotlin` PASS，
  WorkBuddy 真机 A–E 验收 PASS。

进入实际提交前必须完成三件事（均为集成者操作，本报告不授权执行）：

1. 按 §3 确定迁移顺序（推荐：先落最小 ReaderCorrection 数据层前置提交，R3 紧随其后）；
2. 按 §1.D 对 6 个混合文件做 hunk 级拆分，并按 §6 重跑定向测试确认拆分后仍编译、仍绿；
3. 对 §7-1（备份不含来源引用表）取得产品侧显式接受或立项，避免静默缺口。

提交说明中应写明：与 `13.json`/v12→v13 的顺序依赖、`ShelfViewModel.kt` 归属修正（其改动属 L1 动态视图）、
`ImportSourceSheet.kt` 留给书架 UI 切片的原因，以及 §7 的风险清单。

---

*本报告由只读审阅产生；除本文件外未新增或修改任何文件。未执行 git stage/commit/push，未运行 Gradle，未触碰设备。*
