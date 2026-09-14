# Android 第二轮集成冻结清单

> 日期：2026-09-14  
> 基线：`main` / `6cd29670e54c3fc2915d876896ecbaae8d000aa4`。  
> 范围：当前未提交工作区的 Android A–F 并行 WIP；本文件是**冻结与串行集成说明**，不 stage、commit、push、reset、checkout、clean、stash 或修改产品行为。  
> 本轮不做最终验收、不跑全量 JVM/Lint/APK、不做真机操作；真机最终验收仍由 WorkBuddy 独立承担。

## 1. 冻结前提与最终顺序

集成者一次只处理一个切片，并在上一切片的最小门禁结束、工作树没有其他写入方或 Gradle 进程后，才进入下一项。不能把“当前完整脏工作区编译”误写成任一未来拆分提交已经独立成立。

```text
A  ReaderCorrection 最小数据层：v12 → v13
   ↓
B  R3 来源索引：v13 → v14
   ↓
C  安装脚本（独立）
   ↓
D  Reader 核心、词典与选区设置
   ↓
E  Home / Profile / Inspiration 消费侧
   ↓
F  Shelf UI、动态视图与路由收口
```

`A → B` 是不可变顺序。`C` 没有 Kotlin/Room 依赖，但固定在 B 后、D 前，以保持本轮约定的可审计顺序。D、E、F 均不得借由“当前工作区已经能编译”跳过自己的独立门禁。

## 2. 共享 Room 版本线：不得混合悬置

HEAD 的最新 schema 是 `12.json`。当前工作区的 `AppDatabase.kt`、`DatabaseModule.kt`、`AppDatabaseMigrationTest.kt` 同时含两段迁移，不能按整文件提交：

| 共享文件 | A：ReaderCorrection v12→v13 | B：R3 v13→v14 | 冻结规则 |
|---|---|---|---|
| `data/local/AppDatabase.kt` | `ReaderCorrectionEntity/Dao` import、版本改为 13、v12→v13 说明、实体/DAO 声明、`MIGRATION_12_13` 全块 | `LibrarySourceRefEntity/Dao` import、版本改为 14、v13→v14 说明、实体/DAO 声明、`MIGRATION_13_14` 全块 | A 落地时该文件必须是完整可编译的 v13；B 只可从该 v13 再升 v14。 |
| `data/local/DatabaseModule.kt` | `ReaderCorrectionDao` import、迁移注册、`provideReaderCorrectionDao`、12→13 注释 | `LibrarySourceRefDao` import、迁移注册、`provideLibrarySourceRefDao`、13→14 注释 | 两段注册不可互相遗漏。 |
| `androidTest/.../AppDatabaseMigrationTest.kt` | `migrate_12_to_13_adds_reader_text_corrections` | `migrate_13_to_14_creates_library_source_refs`、`migrate_12_to_14_full_chain_preserves_data_and_creates_both_tables`、覆盖标题到 `…13→14` 的说明 | 连续链路测试属于 B；它引用两条迁移，不能提前放进 A。 |
| `schemas/.../13.json` | A，必须与 `MIGRATION_12_13`、实体、DAO 同一提交 | 不进入 B | v13 必须只有 `reader_text_corrections`，不得含 `library_source_refs`。 |
| `schemas/.../14.json` | 不进入 A | B，必须与 `MIGRATION_13_14`、来源实体、DAO 同一提交 | v14 必须含两个新表的完整最终结构。 |

因此不允许以下任何悬置形态：只挑 `MIGRATION_13_14`；先提交 `14.json`；A 中带 v14 实体或测试；B 中回带 `13.json`；或把三件套、迁移测试和 schema 以混合提交留在工作树。

## 3. A — ReaderCorrection v12→v13 最小数据层前置

**推荐顺序：1。** 这是 B 之前唯一允许接触 v13 的切片；不包含 Reader UI、规则编辑器或投影行为。

### 进入 A 的精确文件集合

整文件：

- `android/app/src/main/java/com/creationreadingassistant/data/local/entity/ReaderCorrectionEntity.kt`
- `android/app/src/main/java/com/creationreadingassistant/data/local/dao/ReaderCorrectionDao.kt`
- `android/app/schemas/com.creationreadingassistant.data.local.AppDatabase/13.json`

hunk 级：

- `android/app/src/main/java/com/creationreadingassistant/data/local/AppDatabase.kt`：表 2 所列的 v12→v13 hunk，且版本在此提交为 13。
- `android/app/src/main/java/com/creationreadingassistant/data/local/DatabaseModule.kt`：表 2 所列的 12→13 hunk。
- `android/app/src/androidTest/java/com/creationreadingassistant/data/local/AppDatabaseMigrationTest.kt`：仅 `migrate_12_to_13_adds_reader_text_corrections`。

### 依赖、排除和门禁

- 上游：HEAD schema 12；无 A–F 内部前置。
- 明确不得进入：`LibrarySourceRefEntity.kt`、`LibrarySourceRefDao.kt`、`14.json`、任何 `MIGRATION_13_14` hunk、连续 12→14 migration test、`feature/reader/**`、`ui/screen/reader/**`、`data/settings/**`、`CorrectionProjectionTest.kt`。后两组属于 D。
- 冻结后命令（在 `android/`）：

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

A 没有独占 JVM test：`CorrectionProjectionTest` 明确归 D，且不会进入 A 的独立候选提交，不能为了运行它把 D 混入 A。真正的 v12→v13 运行时迁移断言仍属于后续 WorkBuddy 仪器化验收；此处 compile gate 只证明 migration test source 已能随 A 编译。D 冻结时再运行其完整 Reader/规则 JVM selector 集合。

## 4. B — R3 来源索引 v13→v14

**推荐顺序：2，必须紧随 A。** A 冻结后，B 从 schema 13 增量升至 schema 14。

### 进入 B 的精确文件集合

整文件（R3 生产代码）：

- `android/app/src/main/java/com/creationreadingassistant/data/local/entity/LibrarySourceRefEntity.kt`
- `android/app/src/main/java/com/creationreadingassistant/data/local/dao/LibrarySourceRefDao.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/library/LibraryRootStore.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/library/LibrarySource.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/library/LibrarySourceModule.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/library/LibrarySourceIndex.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/library/SmartBookRecognizer.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/library/SafSmartBookRecognizer.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/library/SourceFingerprint.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/LibraryBrowserModels.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/LibraryBrowserPolicy.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/LibraryBrowserViewModel.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/LibraryBrowserRoute.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/LibraryBrowserComponents.kt`
- `android/app/schemas/com.creationreadingassistant.data.local.AppDatabase/14.json`

整文件（R3 已跟踪改动）：

- `android/app/src/main/java/com/creationreadingassistant/feature/library/ShelfImporter.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/library/SafBookSourceScanner.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfImportRoute.kt`

整文件（R3 JVM tests）：

- `android/app/src/test/java/com/creationreadingassistant/feature/library/LibraryRootStoreTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/library/LibrarySourceIndexTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/library/SafBookSourceScannerTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/library/SafLibrarySourceTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/library/SmartBookRecognizerTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/library/SourceFingerprintTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/LibraryBrowserPolicyTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfImporterTest.kt`

hunk 级：

- `AppDatabase.kt`、`DatabaseModule.kt`、`AppDatabaseMigrationTest.kt`：表 2 所列的全部 v13→v14 hunk。
- `android/app/src/main/java/com/creationreadingassistant/ui/navigation/AppNavigation.kt`：`LibraryBrowserRoute` / `SHELF_LIBRARY_ROUTE` imports 和 `shelf/library` composable；不带 MenuBook 自动镜像或 Inspiration `onOpenRoute` hunk。
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfSharedComponents.kt`：仅 `SHELF_LIBRARY_ROUTE` 常量。
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ShelfViewModelTest.kt`：`ShelfImporter` 的 `sourceIndex` 与 `rootStore` 编译桩；不带 `ShelfSavedViewsStore` 桩和动态视图用例。

### 依赖、排除和门禁

- 上游：A 已提交的 v13 schema、ReaderCorrection entity/DAO/migration；R3 产品决定已完成独立真机验收，但此事实不替代 B 的拆分门禁。
- 明确不得进入：`13.json`、ReaderCorrection 源码与 12→13 hunk、`ShelfViewModel.kt`、`ShelfSavedViewsStore.kt`、`FilterSheet.kt`、`BookDetail*.kt`、`ImportSheets.kt`、`ImportHistorySheet.kt`、`DesktopBooksSheet.kt`、`ImportSourceSheet.kt`、Reader/字典/settings 代码、Home/Profile/Inspiration 代码、安装脚本。
- 定向门禁（在 `android/`）：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.feature.library.*" --tests "com.creationreadingassistant.ui.viewmodel.LibraryBrowserPolicyTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfImporterTest" --tests "com.creationreadingassistant.ui.viewmodel.ShelfViewModelTest"
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

### 已冻结的 ShelfRoute 例外

`2026-09-13-android-r3-slice1-review.md` 曾把 `ShelfRoute.kt` 中 `onOpenLibrary` 作为 R3 hunk。实际源码显示它调用的是新的 `ImportSourceSheet(onOpenLibrary, onImportFromDesktop)`；HEAD 的旧 `ImportSheets.kt` 函数签名只有 `onSelectFiles/onSelectFolder/onDismiss`。因此该调用**不能独立随 B 提交而保持可编译**。

冻结决定：B 保留自包含的 `ShelfImportRoute.kt`「我的书籍目录」入口和 `shelf/library` navigation；`ShelfRoute.kt` 的新 ImportSourceSheet 调用留给 F，在 B 之后与新 ImportSourceSheet/ImportSheets 拆分同批进入。它是本轮唯一纠正既有报告归属的跨切片 hunk；不修改源码，不靠猜测硬拆。

## 5. C — 安装脚本可靠性

**推荐顺序：3。** 它独立于 Room/Kotlin，但固定在 B 后、D 前。

整文件：

- `android/scripts/install_with_confirm.ps1`
- `android/scripts/install_with_confirm.contract.tests.ps1`

不需要 hunk 拆分；二者是同一行为契约。上游依赖是 Windows PowerShell 5.1、`adb`，以及实际安装时的已确认真机 serial；不依赖 A/B。

明确不得进入：APK、`app/build/**`、任何 Android 生产 Kotlin、Room schema、设备安全设置、手动确认逻辑。不得把 `adb -s <serial> install -r` 伪装成脚本成功。

定向命令（在 `android/scripts/`）：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\install_with_confirm.contract.tests.ps1
```

判定口径固定如下：只有脚本的 `RESULT: CONFIRM_LOOP` 且 shell 观察到 `SCRIPT_EXITCODE=0`，才是自动确认安装通过；`RESULT: FALLBACK_DIRECT_INSTALL` / exit 2 是功能回退（FALLBACK）；`-NoDirectInstall` 失败则是失败证据，不可被回退掩盖。实际真机失败时，先保留其输出；核实真实 serial 后才允许 `adb -s <serial> install -r <apk>` 继续功能测试，并在报告中标注 FALLBACK。

## 6. D — Reader 核心、词典与选区设置

**推荐顺序：4。** 选区设置的数据定义、DataStore 和 Reader 行为同属 Reader；Profile 不拥有它们。

### 进入 D 的精确文件集合

整文件（Reader 设置/词典）：

- `android/app/src/main/java/com/creationreadingassistant/data/settings/PerBookSettingsStore.kt`
- `android/app/src/main/java/com/creationreadingassistant/data/settings/ReaderOverride.kt`
- `android/app/src/main/java/com/creationreadingassistant/data/settings/ReaderSettingsRouter.kt`
- `android/app/src/main/java/com/creationreadingassistant/data/settings/ReadingPresets.kt`
- `android/app/src/main/java/com/creationreadingassistant/data/settings/SelectionActionSettings.kt`
- `android/app/src/main/java/com/creationreadingassistant/data/settings/SelectionActionStore.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/dictionary/DictionaryRepository.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/dictionary/StarDict.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/dictionary/StarDictImporter.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/util/ClipboardUtils.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/DictionaryViewModel.kt`

整文件（已跟踪 Reader 生产改动）：

- `android/app/src/main/java/com/creationreadingassistant/feature/reader/doc/MarkdownParser.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/EpubReplacedChapterSource.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/PageTurner.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/pager/ReplacedChapterSource.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/rules/BoundedReplaceProjector.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/rules/EpubReplaceProjector.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/rules/ReplaceProfile.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/rules/ReplaceProjection.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/rules/RuleEngine.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/rules/RuleModels.kt`
- `android/app/src/main/java/com/creationreadingassistant/feature/reader/rules/RulesRepository.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderHelpers.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderInteractionLayer.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderLayerBuilders.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderReplacementCapability.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScaffold.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScreen.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScreenState.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSelectionExternalActions.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSelectionToolbar.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSheetHost.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ReaderViewModel.kt`

整文件（Reader 新拆分 sheets）：

- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/AiExplainInspirationSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/AiExplainResultSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/AiExplainSelectedSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/DictionarySheet.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderAdvancedSettings.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderDisplaySettings.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderEyeCareSettings.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderNoteEditorDialog.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderNotesComponents.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderNotesExporter.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderPagingSettings.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderRuleEditorSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderRulesListSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderSettingsCommon.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderTocBookmarkList.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderTocChapterList.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderTocModels.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderTypographySettings.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/RuleEditorDraft.kt`

整文件（D tests）：

- `android/app/src/test/java/com/creationreadingassistant/data/settings/PerBookSettingsStoreTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/data/settings/ReaderSettingsOverlayTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/data/settings/ReaderSettingsRouterTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/data/settings/ReadingPresetsTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/data/settings/SelectionActionSettingsTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/dictionary/StarDictTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/reader/rules/CorrectionProjectionTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/util/ClipboardUtilsTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/DictionaryEmptyNoticeTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/reader/pager/ReplacedChapterSourceTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/reader/rules/EpubReplaceProjectorTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/reader/rules/RulesRepositorySingleRuleSelectionTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/feature/reader/rules/RulesRepositoryTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderReplacementCapabilityTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderSelectionExternalActionsTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/ReaderSelectionToolbarModelTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/screen/reader/sheets/RuleEditorDraftTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ReaderDocumentLoaderProfileTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/ReaderViewModelTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/SettingsViewModelTest.kt`

hunk 级：

- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/SettingsViewModel.kt`：仅 Reader settings、per-book override 和 SelectionAction facade hunk；不得把不相关的全局设置维护一起带入。
- Reader 的旧 sheet 薄壳：`ReaderAiAssistSheet.kt`、`ReaderAiExplainSheet.kt`、`ReaderNotesSheet.kt`、`ReaderRulesSheet.kt`、`ReaderSettingsSheet.kt`、`ReaderTocSheet.kt`；其删除/调用 hunk 必须与对应的新文件同一提交。

上游：A 的 ReaderCorrection schema/entity/DAO；Reader 的已有稳定 source/display 坐标契约。明确不得进入：B 的 library/Room v14，E 的 `SelectionSubPage.kt` 和 Profile state/route，F 的 Shelf files，Search/Sync/Security WIP。

定向门禁（在 `android/`）：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.data.settings.*" --tests "com.creationreadingassistant.feature.dictionary.*" --tests "com.creationreadingassistant.feature.reader.pager.ReplacedChapterSourceTest" --tests "com.creationreadingassistant.feature.reader.rules.*" --tests "com.creationreadingassistant.ui.screen.reader.*" --tests "com.creationreadingassistant.ui.viewmodel.ReaderViewModelTest" --tests "com.creationreadingassistant.ui.viewmodel.SettingsViewModelTest" --tests "com.creationreadingassistant.ui.viewmodel.DictionaryEmptyNoticeTest" --tests "com.creationreadingassistant.ui.util.ClipboardUtilsTest"
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

### SEAM REQUEST — Profile 接入 Reader 的最小 API

**所有权固定：** `SelectionActionSettings`、`SelectionActions`、`SelectionActionStore`、其 DataStore key/清洗/默认值、Reader settings routing 和 Reader 的消费规则全部归 D（Reader）。`Profile` 的 `SelectionSubPage` 只能显示状态并派发用户意图，不能复制状态、默认值、URL 校验、动作过滤/排序或 Store。

在 D 冻结后，对 E 暴露一个由 Reader owner 维护的最小 facade（可由现有 `SettingsViewModel` 实现，但 Profile 不得注入 Store）：

```kotlin
interface SelectionActionPreferences {
    val settings: StateFlow<SelectionActionSettings>
    fun setActionEnabled(id: String, enabled: Boolean, target: SelectionActionGroup)
    fun setBrowserUrlTemplate(template: String)
    fun setDictionaryUrlTemplate(template: String)
    fun setDictionaryMode(mode: String)
    fun resetToDefaults()
}
```

E 只需要 `settings` 快照和上述六个命令。Profile 的 `ProfileAction` 可以继续是 UI 事件，但必须一对一委托给该 API；不新增 Profile copy、`DataStore` key、`sanitize` 或 `SelectionActionStore` 实例。离线词典导入/卸载是同一 Reader 体验域的独立 facade，不能改变上述选区设置所有权。

## 7. E — Home / Profile / Inspiration 消费侧

**推荐顺序：5。** E 只在 D 的 SelectionAction facade 稳定后接入；它是消费者而不是 Reader 设置的第二个实现者。

整文件（slice3 所有权）：

- `android/app/src/main/java/com/creationreadingassistant/ui/screen/InspirationScreen.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/ProfileScreen.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/home/HomeContinueSheet.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/home/HomeRoute.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/home/HomeUiState.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/home/components/HomeMetricsSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/inspiration/InspirationPage.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/inspiration/InspirationRoute.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/inspiration/InspirationScreen.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/inspiration/components/InspirationDetail.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/inspiration/components/InspirationEditor.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/inspiration/components/InspirationList.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/DiagnosticsSubPage.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileHomeScreen.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileRoute.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileUiState.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/ReadingNotesSubPage.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/StorageSubPage.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/HomeArchiveViewModel.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/HomeViewModel.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/InspirationViewModel.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ProfileViewModel.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/homearchive/HomeArchiveScreens.kt`（删除必须与下方四个 replacement 同批）
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/home/HomeContinueCards.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/home/HomeContinueMenu.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/home/HomeContinueModels.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/homearchive/HomeArchiveFormatters.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/homearchive/HomeCompletedScreen.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/homearchive/HomeInspirationDetailScreen.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/homearchive/HomeInspirationsScreen.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/AnnotationBatchAndDialogs.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/AnnotationEntryCard.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/AnnotationFilterSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/ReadingArchiveSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/SelectionSubPage.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/InspirationMaterialOps.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/InspirationViewModelTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/viewmodel/InspirationMaterialOpsTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/screen/home/HomeContinueModelsTest.kt`

hunk 级：

- `AppNavigation.kt`：仅 Inspiration `onOpenRoute` consumer hunk；B 的 `shelf/library` hunk 和未归属的 MenuBook icon hunk 不得混入。
- `ProfileRoute.kt` / `ProfileUiState.kt` / `ProfileHomeScreen.kt` / `SelectionSubPage.kt`：SelectionAction 只是 facade consumer hunk，依赖 D，不复制 Store。
- `ui/theme/Motion.kt` 与其调用方形成不可分的签名迁移。它需要与 `ui/screen/stats/components/{CreationSection,SummaryGroup,YearBillHeroCard}.kt`、Home/Profile 调用点和 `ui/theme/CountUpStartValueTest.kt` 一起作为 E 内的 `E0 Motion` 子批；不得只提交必填 `key` API 或只改部分调用点。

上游：D 的 facade/词典 API；E0 Motion 后才能编译所有 `rememberCountUp` 调用点。明确不得进入：`SelectionActionStore.kt`、`SelectionActionSettings.kt`、Reader 层代码、Room、R3/Shelf/安装脚本；`MyReadingViewModel.kt`、`RebuildSearchIndexDialog.kt`、Search/Sync/Security 的当前 WIP 因本报告没有可靠所有权而保持冻结。

定向门禁（在 `android/`）：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.viewmodel.InspirationViewModelTest" --tests "com.creationreadingassistant.ui.viewmodel.HomeViewModelTest" --tests "com.creationreadingassistant.ui.viewmodel.HomeArchiveViewModelTest" --tests "com.creationreadingassistant.ui.viewmodel.InspirationMaterialOpsTest" --tests "com.creationreadingassistant.ui.screen.inspiration.InspirationDetailStateTest" --tests "com.creationreadingassistant.ui.screen.inspiration.InspirationFileStructureTest" --tests "com.creationreadingassistant.ui.screen.home.HomeReadingArchiveSectionTest" --tests "com.creationreadingassistant.ui.screen.profile.UpdateCheckTest" --tests "com.creationreadingassistant.ui.theme.CountUpStartValueTest" --tests "com.creationreadingassistant.data.settings.ContinueReadingStoreTest"
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

## 8. F — Shelf UI、动态视图与路由收口

**推荐顺序：6。** F 依赖 B 的 `SHELF_LIBRARY_ROUTE` / Library Browser，但不回带 R3 数据层。它只收口现有书架 UI、动态视图、导入面板和删除文案，不实现回收站、私有正文自动回收或破坏性文件删除。

整文件：

- `android/app/src/main/java/com/creationreadingassistant/data/settings/ShelfSavedViewsStore.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BatchSheet.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookActionSheet.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookDetailSheet.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookDetailHeaderSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookDetailManagementSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookDetailNotesSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/BookDetailStatsSection.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/DesktopBooksSheet.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/FilterSheet.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ImportHistorySheet.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ImportSheets.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ImportSourceSheet.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/ShelfModels.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/screen/shelf/SortSheet.kt`
- `android/app/src/main/java/com/creationreadingassistant/ui/viewmodel/ShelfViewModel.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/screen/shelf/ShelfDeleteCopyConsistencyTest.kt`
- `android/app/src/test/java/com/creationreadingassistant/ui/screen/shelf/ShelfLongPressPanelTest.kt`

hunk 级：

- `ShelfRoute.kt`：全部 L1 saved views hunk (`savedFilters` collection、FilterSheet callbacks) 和新的 ImportSourceSheet wiring (`onOpenLibrary`/`onImportFromDesktop`)；B 不应单独带这段调用。
- `ShelfViewModelTest.kt`：`ShelfSavedViewsStore` stub、构造参数与 apply/save/delete tests；B 的 `sourceIndex/rootStore` stub 留在 B。
- `ShelfSharedComponents.kt`：`DELETE_BOOK_SELF_DESCRIPTION` 常量与注释归 F；B 的 `SHELF_LIBRARY_ROUTE` 常量另属 B。二者在同一文件，必须按 hunk 拆分。

上游：B 已提供 `SHELF_LIBRARY_ROUTE`、R3 导航和新 `ShelfImporter` 构造参数；Shelf 既有 `DeletionCopy` 及 strings 的删除契约。

明确不得进入：`ShelfImporter.kt`、`LibrarySourceRef*`、`LibraryBrowser*`、`ShelfImportRoute.kt` 的 R3 入口、`AppDatabase` / migration / schema、任何 `feature/library/deletion/**` 行为改动、`StorageSubPage.kt` 的“清空所有离线书籍与资源”路径。F 只让 UI 文案如实说明现状；不新增回收站、保留期、孤儿扫描、自动清理或删除来源原文件。

删除文案冻结结论：当前 WIP 的 `BookActionSheet.kt` 与 `BookDetailManagementSection.kt` 已共同使用 `DELETE_BOOK_SELF_DESCRIPTION`：**“移除书籍资料、进度、书签和笔记；不删除本地正文文件”。** 该文案与 `DeletionCopy` / `strings_deletion.xml` 及真实 `DELETE_BOOK`（软删/关联清理、无文件删除）一致；“移除正文”仍是唯一释放私有正文缓存的不可逆路径。

ImportSourceSheet 冻结结论：HEAD 的声明仍在 `ImportSheets.kt`，新 `ImportSourceSheet.kt` 会重复声明；因此 `ImportSheets.kt` 的薄壳化、`ImportHistorySheet.kt`、`DesktopBooksSheet.kt`、`ImportSourceSheet.kt` 和 `ShelfRoute.kt` 新调用必须一起进入 F，绝不能混入 B。

定向门禁（在 `android/`）：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.screen.shelf.*" --tests "com.creationreadingassistant.ui.screen.ShelfStatusFilterTest" --tests "com.creationreadingassistant.data.settings.ImportHistoryStoreTest" --tests "com.creationreadingassistant.data.settings.ShelfPrefsTest" --tests "com.creationreadingassistant.feature.library.deletion.*"
.\gradlew.bat :app:compileDebugAndroidTestKotlin
```

## 9. 当前无法安全归属的 hunk / 冻结项

以下项不进入 A–F，除非原 owner 给出更窄的书面归属与定向门禁；本轮不猜测、不跨文件硬改：

| 文件或 hunk | 原因与冻结动作 |
|---|---|
| `AppNavigation.kt` 的 `Icons.AutoMirrored.Outlined.MenuBook` | 不属于 B 的 Library route，也不属于 E 的 Inspiration consumer；保留不动，待视觉/导航 owner 单独认领。 |
| `android/.gitignore` 的根级 XML 忽略 | 测试工件卫生，不是任一功能切片。 |
| `data/remote/DeviceInfoProvider.kt`、`data/repository/{SearchIndexRepository,SearchIndexScheduler}.kt`、`data/security/SecurePrefs.kt`、`feature/search/**`、`feature/sync/JsonBridge.kt`、`ui/screen/search/SearchScreen.kt`、`ui/viewmodel/SearchViewModel.kt`，及其 Sync/Search tests | 共享 seam 审计明确禁止把它们打成“基础设施大杂烩”；仍需各自产品决定和调用方表。 |
| `ui/components/GlassDialogs.kt`、`ui/viewmodel/MyReadingViewModel.kt`、`ui/screen/profile/RebuildSearchIndexDialog.kt`、`androidTest/.../ReadingSessionRecorderPersistenceTest.kt` | 不在切片 3 报告的所有权清单，且与搜索/阅读会话交叠；保持冻结。 |
| `androidTest/.../BookDeletionPersistenceTest.kt` | R2 书库仓储编译修复，不是 R3 或 F 的 UI/删除语义切片。 |
| `ShelfRoute.kt` 的新 ImportSourceSheet hunk | 已由第 4 节的编译依赖决定归 F；这是与旧 R3 审阅记录的唯一已识别归属冲突。 |
| `library_source_refs` 未进 `JsonBridge` 备份导出/导入 | 这是产品决定：备份恢复将退化为弱判定。当前不静默补进 Sync/R3，也不称为已接受。 |

## 10. 本轮实际复核记录（不等于最终验收）

- 已实际运行 `android/scripts/install_with_confirm.contract.tests.ps1`：37 条静态/假 adb 行为断言通过，exit 0。覆盖 UTF-8 UI dump、PowerShell STDERR、`CONFIRM_LOOP`、`FALLBACK_DIRECT_INSTALL`、`NoDirectInstall` 与 exit 码分流；它不替代真机确认循环证据。
- 已实际运行关键迁移/R3/Shelf/安装脚本路径的 `git diff --check`：exit 0。安装脚本有 LF→CRLF 的 Git 警告，但本检查没有 whitespace error；提交时不得混入整文件行尾重写。
- 开始 Kotlin 门禁前已复核：没有本项目 Gradle/Kotlin 进程。实际定向门禁按 A→B→D→E→F 执行：
  - A：`CorrectionProjectionTest` JVM exit 0；`compileDebugAndroidTestKotlin` exit 0。
  - B：R3 library + `LibraryBrowserPolicyTest` / `ShelfImporterTest` / `ShelfViewModelTest` JVM exit 0。
  - D：Reader/settings/dictionary 定向 JVM exit 0。
  - E：Home/Profile/Inspiration 定向 JVM exit 0。
  - F：Shelf/deletion 定向 JVM exit 0；最新 XML 是 13 suites / 82 tests / 0 failures / 0 errors / 0 skipped。
  - 最终一次当前工作区 `compileDebugAndroidTestKotlin` exit 0。
- 一次错误的**组合命令**（`testDebugUnitTest --tests …` 与 `compileDebugAndroidTestKotlin` 同调用）在配置阶段 exit 1，因为 Gradle 不接受把 `--tests` 施加给 compile task；没有执行测试。随后已拆成上列独立命令，不能把该配置错误说成源码或测试失败。
- 这些结果只证明当前混合冻结工作区在定向范围内可编译/测试；它们不证明将来 hunk 拆分后的候选提交已经独立通过。未运行全量 JVM、Lint、APK、connected/instrumented 执行或真机验收。

## 11. 第三轮入口

当 A–F 都按本清单形成独立、可编译的候选提交且写入方全部停止后，第三轮才由单一集成者串行执行全量 JVM、Lint、APK 与提交后差异检查；需要真机行为证据的项目再交 WorkBuddy。第三轮前不得把历史绿灯、direct ADB 回退或当前混合工作区编译结果描述为最终验收通过。
