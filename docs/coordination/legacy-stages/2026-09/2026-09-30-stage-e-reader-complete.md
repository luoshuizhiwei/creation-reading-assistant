# 阶段 E：S07+S12 阅读器闭包第二轮（导入链接通 + 专项测试入列；READY=false 有明确阻塞）（2026-09-30）

> **窗口**：S07（选区/高亮/笔记跳转）+ S12（Legado 导入 marker 保护）阅读器闭包，第二轮扩权执行。
> **共享恢复仓库（只读入口）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；HEAD `513891d6a66a9b78e072ba759d34ed580d377ecf`；暂存区空。
> **只读底座**：`E:\creation-reading-assistant\stage-e-final-joint-20260930\joint\android`（联合候选树）。
> **本窗口独占候选**：`E:\creation-reading-assistant\stage-e-reader-complete-20260930\android`（从第一轮候选 `stage-e-reader-preserved-20260930/android` 复制纯源码扩权改造）。
> **输入**：[增量回读复核](../../reviews/2026-09/2026-09-30-stage-e-increments-codex-review.md)、[第一轮闭包报告](2026-09-30-stage-e-reader-preserved-closure.md)。
> **纪律**：共享仓库**唯一写入 = 本文件**。未操作设备；未 stage/commit/push/reset/checkout；未删改目录；未触碰原仓库/正式书库/其他窗口。构建缓存 `_cache/` 在 E 盘本目录。

---

## 一、结论摘要

1. **S12 全链已闭合并通过定向 JVM（191/191 全绿）**：Legado 导入的生产入口已接通（ProfileAction→ProfileRoute→ProfileViewModel→ProfileWebDavManager→WebDavBackup.getBytes/listLegadoBackups→LegadoReadingProgressImporter），marker 编解码/导入器/保存守卫/EPUB locator 合同全部入列并通过。
2. **首轮遗留的两项 BLOCKED（selectionSnapshot、TOC notes）已部分闭合**：`ReaderSelectionSnapshot` 数据类、`consumesSelection`、捕获/释放 helpers、`computeLocatorJsonForSnapshot`、笔记对话框快照消费、VM 捕获/释放均已接线；`ReaderTocSheet` 已升级为 TocNoteEntry typed 签名并接通宿主。**但源码级守卫测试（TocNotesWiringGuardTest 5 项 + ReaderConsumerSnapshotWiringGuardTest 6 项）在本候选下失败**——它们断言的是 worktree 的**全量**接线形态，其闭包牵出 day-night 视觉、弹层系统栏、路由协调器等超出授权范围的批次（缺失符号清单见 §四）。**这些守卫项即 READY=false 的明确阻塞。**
3. **EPUB JSON 适配已按实际语义验证**：`LegadoImportEpubLocatorContractTest`（2 项）用真实解析字段断言 `buildString` 产物与 `LocatorBuilder.progressJson(globalOffset=null)` 的字段级等价（legacy offset/chapter/locator_v2 无 offset），**不是"逐字等价"概括**。
4. **受控导入/marker 设备用例已入 androidTest APK**：`LegadoMarkerRealImportTemporaryTest`（4 条，自建受控库不改用户 DB）已编译进 qahost test APK（`compileQahostAndroidTestKotlin` EXIT 0）；**设备执行 NOT RUN（本窗口不操作设备）**。
5. **READY.json 已写出但 `ready=false`**，阻塞项与缺失符号已按任务要求记录；集成者在阻塞消除前不应合流此候选的 reader 部分。

## 二、第一轮以来的增量（相对首轮候选）

| # | 项 | 文件 | 说明 |
|---|---|---|---|
| 1 | **Legado 导入链生产接线** | `ProfileUiState.kt`（`legadoBackups` 状态 + `RefreshLegadoBackups`/`ImportLegadoBackup` 动作）、`ProfileRoute.kt`（两动作分发 + `ConfirmSpec` 确认对话框）、`ProfileViewModel.kt`（`loadLegadoBackups`/`importLegadoBackup` + `legadoImporter` 注入 + `clearWebDav` 清理）、`ProfileWebDavManager.kt`（worktree 版：`listLegadoBackups`/`importLegadoBackup` + DatabaseSafetyNet 快照）、`WebDavBackup.kt`（**最小新增** `getBytes` + `listLegadoBackups`，复用候选既有 `requireSafeTarget/requireSafeFilename/resolveTarget/parsePropFind/basicAuth/parseHttpDateMillis`，**未引入** worktree 的 withRetry/MKCOL 大改）、`WebDavSubPage.kt`（"扫描开源阅读备份"UI 块 + 确认对话框） | 完整真实链路；保留确认对话框、错误反馈、99.4% 版本保护；**无第二套绕过用户的入口** |
| 2 | **selectionSnapshot 最小生产接缝** | `ReaderScreenState.kt`（`ReaderSelectionSnapshot` 数据类 + `consumesSelection` + `withSelectionCaptured/Released` + `selectionSnapshot` 字段）、`ReaderProgressActions.kt`（`computeLocatorJsonForSnapshot`）、`ReaderActions.kt`（`ReaderNavActions` 字段 + 接线）、`ReaderScreen.kt`（笔记对话框读快照 + `saveReaderNote` 用同一快照的 locator）、`ReaderViewModel.kt`（OpenSheet/SetNoteOpen 捕获/释放 + imports） | S07-F2.4 最小闭环；RULES/AI_EXPLAIN/INSPIRATION/AI_ASSIST 打开时捕获 |
| 3 | **TOC notes typed 升级** | `ReaderTocBookmarkList.kt`（worktree 版：`TocNoteEntry` sealed interface + `tocNoteEntries` + Highlight/Note 微岛）、`ReaderTocSheet.kt`（TocNoteEntry 签名 + NOTES Tab）、`ReaderSheetHost.kt`（`notes = tocNoteEntries(...)` + `onPickNote` typed 回源） | typed target 由条目携带，防同 rawId 跨表跳错 |
| 4 | **Markdown 三件套** | `MarkdownScrollHighlight.kt` / `ReaderContentHostMarkdownBranch.kt` / `ReaderMarkdownRender.kt`（worktree 版，S07-F3/F3.1 Markdown 持久高亮投影） | 被复制的 `MarkdownPersistentHighlightProjectionTest` 等 3 个测试所需的最小生产闭包 |
| 5 | **专项测试 12 个入列** | `LegadoReadingProgressImporterTest`(12)、`LegadoImportEpubLocatorContractTest`(2)、`EpubBlockSourceRangeTest`(9)、`EpubBlockPersistentHighlightTest`(15)、`MarkdownPersistentHighlightProjectionTest`、`ReadingUnitSelectionOwnershipTest`(10)、`ScrollSelectionVisibilityPolicyTest`(20)、`ReaderLegadoMarkerSaveGuardTest`(13)、`ReaderNoteSnapshotSaveTest`、`ReaderConsumerSnapshotWiringGuardTest`(6，**6 失败=阻塞**)、`TocNotesWiringGuardTest`(5，**5 失败=阻塞**)、`ScrollSelectionVisibilityWiringGuardTest` | 源码字符串守卫类读取仓库相对路径，在候选目录下运行 |
| 6 | **受控导入/marker 设备用例** | `androidTest/.../LegadoMarkerRealImportTemporaryTest.kt`（4 条：生产 importArchive 解析+Room 落 marker、临时导航守卫合同、临时路由构造、直入 qahost 库供 UI 验收） | 编译进 qahost test APK；**设备 NOT RUN** |
| 7 | **androidTest 同步** | `ReaderTocSheetTest.kt`/`ReaderRulesSheetTest.kt`（worktree 版，适配 TocSheet 新签名） | qahost androidTest 编译 EXIT 0 |

## 三、验证证据

### 3.1 编译（E 盘独占缓存）

| 命令 | 退出码 |
|---|---|
| `gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --offline --max-workers=1` | **0**（`_evidence/compile-s12.log`） |
| `gradlew :app:compileQahostAndroidTestKotlin -PcraAndroidTestBuildType=qahost --offline` | **0**（`_evidence/compile-androidtest3.log`） |

### 3.2 专项定向 JVM（14 套 191 tests，0 失败/错误/跳过，EXIT 0）

| 套件 | tests |
|---|---:|
| `ReaderViewModelTest`（原 224 核心） | 62 |
| `ScrollSelectionVisibilityPolicyTest` | 20 |
| `EpubBlockPersistentHighlightTest` | 15 |
| `ReaderSelectionExternalActionsTest` / `ReaderSourcePositionTest` | 14 / 14 |
| `ReaderLegadoMarkerSaveGuardTest` | 13 |
| `LegadoReadingProgressImporterTest` | 12 |
| `ReadingUnitSelectionOwnershipTest` | 10 |
| `EpubBlockSourceRangeTest` | 9 |
| `ReaderProgressNavigationTest` / `ReaderTemporaryInspectionUiTest` | 7 / 5 |
| `ReaderTocStateTest` / `LocatorCodecTest` | 4 / 4 |
| `LegadoImportEpubLocatorContractTest` | 2 |
| **合计 14 套** | **191 / 0 失败 / 0 错误 / 0 跳过** |

`MarkdownPersistentHighlightProjectionTest`、`MarkdownBlockSelectionLocatorTest`、`ScrollSelectionVisibilityWiringGuardTest` 也在候选内并通过（在 191 计数外的补跑中验证）；XML 原件在 `android/app/build/test-results/testDebugUnitTest/`，日志 `_evidence/test-s12-final.log`。

### 3.3 可复算补丁（联合底座 → 本候选）

| 项 | 值 |
|---|---|
| 路径 | `E:\creation-reading-assistant\stage-e-reader-complete-20260930\reader-complete-from-joint.patch` |
| 规模 | 44 文件（17 A + 27 M）/ 119 hunk / 251,672 字符 |
| SHA-256 | `622988bb0d596bc0daccacc2379e137bdac0bae2c79513f3883dab9081894a97` |
| round-trip | 全新联合底座 + `git -c core.autocrlf=false apply`：`--check` EXIT 0，**44/44 文件字节级一致** |

**基线声明**：基线是联合候选树（非 HEAD）。第一轮报告的 `g6-integrated.patch`/`reader-preserved-closure.patch` 均为中间增量，不可冒充本补丁。

### 3.4 排除项复核

word-boundary 扫描：`withRetry`/`ensureCollection`/`MKCOL`（WebDAV 大改）、`SettingsSearchIndex` 激活、`CompactSettingsGroup`/`ReaderPaperGrid`（视觉重构）、`MetricMicroCard`、`HeatmapCellTooltip`、`onMarkFinished`/`onShelve`/`shelfBookCount`/`extractPinyinInitials`（S05 批次）——**除 3 个已声明的依赖闭包文件外零夹带**：
- `HomeContinueMenu/HomeContinueSheet` 的 `onShelve` **在联合底座已存在**（非本窗口引入）；
- `SettingsAnchor.kt`/`SettingsFocus.kt` 是 legado UI 块（`SettingsAnchor("legado-progress-import-card")`）的**依赖闭包**；其内的 `SettingsSearchIndex`/`rememberSettingsPageListState` 符号随文件带入但**运行时未激活**（候选 `ProfileRoute` 不调用设置搜索入口）——依赖闭包边界如实登记。

## 四、READY=false 的明确阻塞（缺失符号）

**守卫测试 11 项失败的根因**：`TocNotesWiringGuardTest`(5) 与 `ReaderConsumerSnapshotWiringGuardTest`(6) 用源码字符串断言锁定 **worktree 全量接线形态**。满足它们需要把以下符号所在批次一并带入（均超出本窗口授权闭包，属 reader 视觉/路由重构）：

| 缺失符号 | 定义/依赖位置（worktree） | 所属批次 |
|---|---|---|
| `ReaderDayNightToggle` / `dayNightLightPaper` / `dayNightDarkPaper` | `ReaderInteractionLayer.kt` / `ReaderScaffold.kt` | 日夜切换视觉批次（含 `CraSlider`、`ReaderDayNightCord`/`ReaderDayNightHint`） |
| `readerChapterEdgeFlags` | `ReaderChrome.kt` | 章末标志视觉批次 |
| `txtScrollChapterTargetOffset` | `ReaderProgressActions.kt` + `ReaderLayerBuilders.kt` 联动 | TXT 跨章滚动批次 |
| `LocalDialogSystemBarsEffect`（`ui.window`） | worktree SheetHost/Screen/TtsBar | 弹层系统栏批次 |
| `applyReaderEdit`（`ReaderSettingsEditApplier.kt` 已带入但 `ReaderScaffold` 链未接） | `ReaderScaffold.kt` + `ReaderSettingsEditApplier.kt` | 设置应用闭环 |
| `ReaderRoute`/`ReaderRouteUiState`/`ReaderAuxiliaryState`/`ReaderSearchIndexCoordinator`/`ReaderChapterLoaderCoordinator`/`ReaderTxtScanCoordinator` | worktree `ReaderScreen.kt`/`ReaderRoute.kt` | 阅读器路由重构批次（首轮报告已登记为非目标） |
| `AI 解读/灵感弹层快照参数版` | worktree `ReaderSheetHost.kt` AI_EXPLAIN/INSPIRATION 块 | 与 SheetHost 全量迁移绑定 |

**已执行验证**：曾尝试整文件取 worktree `ReaderLayerBuilders`+`ReaderSheetHost` 并按依赖闭包扩展（`ReaderInteractionLayer`/`ReaderScaffold`/`ReaderChrome`/`ReaderSettingsEditApplier`/`ReaderTtsBar`），编译错误从 39→72 扩散（`CraSlider`、日夜 cord、TTS speech rate 等），确认该闭包**无界**，遂按"不夹带 reader 视觉重构"回退为旧候选版 + 最小接缝。迭代证据：`_evidence/compile-w1.log`、`compile-w2.log`、`compile-final*.log`（r11–final5 的完整轨迹）。

## 五、当前候选实际状态（诚实清单）

| 项 | 状态 |
|---|---|
| S12 导入链（生产入口→导入器→marker→守卫） | **已闭合**，191/191 JVM 全绿 |
| EPUB/TXT/Markdown 选区·高亮·滚动的行为 JVM（非守卫类） | **已闭合**（EpubBlock 24、ScrollPolicy 20、Ownership 10、Projection/Locator 等全绿） |
| EPUB JSON 适配（可选 offset 语义） | **已按字段级验证**（ContractTest 2 项 + LocatorCodecTest 4 项） |
| 受控导入/marker androidTest | **编译入包**；设备 NOT RUN |
| 笔记快照（S07-F2.4）最小闭环 | **生产接线完成**（ScreenState/Actions/Screen/VM），`ReaderNoteSnapshotSaveTest` 通过 |
| TOC notes typed 跳转 | **生产接线完成**（TocSheet/BookmarkList/SheetHost），但 **守卫 5 项失败**（要求全量形态） |
| 消费者快照守卫 | **守卫 6 项失败**（同上） |
| 设备验收 | NOT RUN（本窗口不操作设备；受控导入用例已就位待统一执行） |

## 六、未覆盖与边界

1. **守卫 11 项失败即阻塞**：不删断言、不放宽守卫；消除阻塞需要集成者裁决把 §四 批次纳入（或授权后续窗口专项接线），闭包体积经实测无界（72+ 错误扩散）。
2. **Legado 导入链设备行为**：`getBytes`/`listLegadoBackups` 为最小实现（无 worktree 的 withRetry/MKCOL），网络异常分支（409/404 自动建目录、超时重试）未覆盖——只保证受控导入路径可用。
3. **`SettingsAnchor`/`SettingsFocus` 依赖闭包**：settings-search 符号随文件带入但未激活；若集成者要求零符号带入，需改用非 SettingsAnchor 的 legado 卡片包裹（1 处 UI 修改）。
4. **联合候选自身**：本候选基于 joint 树；joint 的 2464 tests 等门禁由集成者负责。
5. **设备/真机/正式包**：未触碰。

## 七、纪律声明

- 共享仓库**唯一写入 = 本文件**；未改共享源码/测试/配置/其他窗口候选或 READY。
- 未 stage/commit/push/reset/checkout/stash；未删改目录；未操作设备；未触碰原仓库/正式书库。
- 本窗口全部写入在 `E:\creation-reading-assistant\stage-e-reader-complete-20260930\`（候选、`_cache/`、`_evidence/`、补丁、`_verify/`、READY.json）。
- 来源候选 `stage-e-reader-preserved-20260930` 只读；READY.json `ready=false`，阻塞符号如实列出；11 项守卫失败未删未宽放。
