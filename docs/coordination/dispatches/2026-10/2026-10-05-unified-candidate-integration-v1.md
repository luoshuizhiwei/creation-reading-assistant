# 统一候选集成 v1：D WIP 保全 + 已核修复 + 两组合接缝 + 分页增量

- 任务编号/版本：`CRA-UNIFIED-CANDIDATE-INTEGRATION-v1`；2026-10-05 指定，实际执行 2026-10-05T01:02–01:35+08:00，应用 ZCode / GLM（account:zai-start-plan / GLM-5.3-Flash）。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工一致）；暂存区前后 0 条；D 工作树 1599 条状态全保全（**未把 D 工作树当已提交输入：候选基座=从 D 当前 android 源码新建快照**）。固定输入：机械清单 `next-round-joint-integration-manifest-v2`（980 行）、已验修复 `next-round-joint-final-v3\android`、分页增量 `next-round-md-paged-chapter-fix-v1\android`、阅读进度提议合并件 `reader-progress-merge-decision-v3\evidence`。**输入漂移：37 个拟动作行 + 全 980 行 vs v2 机械清单 SHA 逐行核对，0 漂移**。
- 所有权与改动：写入仅 `E:\creation-reading-assistant\unified-candidate-integration-v1`（android 候选、manifest、freeze、test-results、gradle-home、scripts）与本唯一报告。D/原仓库/旧候选/旧报告全部只读；未改 current/TODO/公共清单；未 commit/push/reset/checkout；未操作设备/安装 APK/触正式书库。本轮为唯一候选集成者与唯一构建执行者。
- 环境：Windows / Git Bash / Python 3.13；JDK 17.0.14；Gradle 8.9 `--offline --no-daemon`；隔离 Gradle home `gradle-home`（3.4G，复制自本会话此前任务的私有缓存，不与并行窗口共享）；构建产物已清理，证据保留。

## 执行与结果

### 1. 候选建立（D 快照基座，非候选整树覆盖）

从 D 当前 android 源码复制 960 文件为快照（排除 build/.gradle/.kotlin/.idea/__pycache__/local.properties/android 顶层 49 个 `*.log`——构建缓存与无关大日志按任务要求不复制）。D 删除状态原样保全（3 个删除意图 HOLD 测试在候选树中仍不存在，逐个核对通过）。

### 2. 精确应用 manifest（[application-manifest.json](<E:/creation-reading-assistant/unified-candidate-integration-v1/manifest/application-manifest.json>)）

980 行逐行分类（动作/依据/D 输入 SHA/源候选 SHA/应用后 SHA），计数：保持D/no-op 931（含 181 候选==HEAD、87 仅D存在资产、4 CRLF-only、3 删除 HOLD、11 双方同值）、采用候选 23（semantic12 九文件 9 + D 干净 14）、新增 11、精确组合 2、保持D(ProfileViewModelTest) 1、build.gradle 1。每个动作行带 `pre_apply_sha256`（D 当前值）与 `applied_sha256`，**收工复核触碰行 D 侧零漂移**。树级核对 vs D：恰 20 改 + 17 新增，51 个仅-D 文件全部为排除的日志/pycache，**D 独有源码资产零丢失**。

- **采用候选 9 文件**（SHA 与已验交付逐字一致）：ProfileRoute `8fde6ce2`、ProfileViewModel `18b52513`、ReaderProgressEffects `a59258be`、SyncRepository `b01cfda9`、ShelfImporter `bfc37d71`、EpubParser `27a4644d`、WebDavBackup `1291b147`、ShelfImporterTest `89e335be`、EpubRepositoryReplaceTest `39e7457d`。
- **保持D**：ProfileViewModelTest（D 版含重建索引护栏，与候选生产构造兼容——门禁 4/4 证实）。
- **依赖闭合新增**：EpubSwapGate.kt（候选 EpubParser/EpubRepository 持闸实现）、ReaderAnnotationNavigation.kt(+Test)（候选 Effects SE4 分派）、LocalZipBackup/LocalZipRestoreStateStore(+两 Test)、JsonBridge(+Test)、MarkdownDocument/ReaderContentHostMarkdownBranch(+MarkdownDocumentTest)、SyncRepositoryLocalFactsTest、WebDavCompatibilityTest、MarkdownVisibleSourceOffsetTest、分页 MarkdownChapterSource(+Test)、验收资产 6 件（androidTest：FreshSource/MdTrueChapter/AnnotationPending/LocalZipRestore/MdPaged/`SyncRepositoryPersistenceTest`，**单列验收资产，不作为本轮通过证据**）。
- **配置**：build.gradle.kts 采用候选依赖 hunk 后把 `.qahostjoint20261004v3` 精确回退为 `.qahost`（核对：批次后缀零残留、`.qahost` 在位、`.qahostmdpagedv1` 未带入）；生产/runner 其余配置保持 D。

### 3. 两处精确组合（[组合 diff](<E:/creation-reading-assistant/unified-candidate-integration-v1/freeze/combined-ReaderProgressActions.D-base.patch>) / [combined-EpubRepository.vs-candidate.patch](<E:/creation-reading-assistant/unified-candidate-integration-v1/freeze/combined-EpubRepository.vs-candidate.patch>)）

- **ReaderProgressActions.kt** = D 基座 `933513e1…`（与提议 patch 基准逐字节一致，零漂移）+ 提议 patch 3 hunk（imports / `markdownVisibleSourceOffset` / MD canonical 保存分支），`git apply --check` 通过后应用；A2 atChapterEnd、A3 txtScrollChapterTargetOffset、A5 plainListState/readingUnits 参数、A7 chapterForLocator 全部保全；分支顺序 分页 → Markdown → TXT 实时列表。调用方 ReaderActions/LayerBuilders/Scaffold/Screen 为 181 保留D/no-op，未触碰（D 版已按名传参）。
- **EpubRepository.kt** = 候选基座 `15a92883…`（闸/逐字节核对/missing-backup suppressed/条件 AppLog 全保留）+ D 的 saveProgress 两行：`globalOffset = absoluteOffset.takeIf { it >= 0 }` 及 S12-① 契约注释——无全书偏移时 locator_v2 offset 缺省，由章首+co 派生。

### 4. 串行 JVM 门禁（唯一一次，受影响范围）

命令：`:app:testDebugUnitTest --offline --no-daemon`，28 个受影响套件（原始日志 [gate-run.log](<E:/creation-reading-assistant/unified-candidate-integration-v1/test-results/gate-run.log>)、EXIT=0、[XML ×28](<E:/creation-reading-assistant/unified-candidate-integration-v1/test-results/xml>)）。**实际计数：424 tests / 0 failures / 0 errors / 0 skipped**。覆盖：Sync 25+27+20+3、ShelfImporter 63、EPUB 28+11+5+3+3+28+11+5（含 **parseUnderSwapGate mock 命中核实**：候选 RollbackTest stub 的是 `parseUnderSwapGate`，与候选生产调用入口一致，mocks 实际命中）、LocalZip 62+16、MD 组 19+2+13+6+11+2、分页 MarkdownChapterSourceTest 2、导航/合同 11+13+10+7、**新组合回归 ReaderProgressActionsPersistTest 5/5**（TXT 离开读实时列表偏移≠诱饵 999、Markdown 走 canonical 且不误取 TXT 列表、canonical 缺失整体不落库、初始定位未完成不落库、分页 offset 原样）、**D 自带 saveProgress 契约测试 3/3**（S12-①：章切换 offset 缺省→真实 SourceNavigationContract 按 chapterStart[ci]+co 派生成功、全书偏移在位且一致、legacy 自愈——直接绑定组合后实现，五.2 由 D 资产覆盖，未新增重复文件）。失败零个；无既有无关失败单列。

## 原始证据

`E:\creation-reading-assistant\unified-candidate-integration-v1\`：`execution.json`（应用/模型/HEAD/输入指纹/门禁/收工核对）、`manifest/application-manifest.json`（980 行逐行动作与前后 SHA）、`freeze/`（两组合 diff、preservation-check.json、门禁日志与退出码）、`test-results/xml/`（28 份原始 JUnit XML）、`scripts/`×5 可复跑、`combine-actions/`（相对化 patch 与应用现场）。

## 恢复与副作用

D 仓库/Git 零写入（收工 HEAD/暂存区与开工一致）；D 工作树 1599 条状态保全，触碰行零漂移；旧候选/证据只读；设备未用；构建/缓存/日志全部在本任务 E 目录，候选树内构建产物已清理。

## 未覆盖项

- **统一候选未做设备验收**（本轮无设备需求，未加任何新后缀/批次编排）；6 件 androidTest 验收资产未编译运行。
- D 的 canonical locator ci>0 但 co 仍全局的合同偏离（ReaderProgress 两文件侧）按上游登记为既有风险保留，本轮未修未证。
- `pagedSource.chapterIndexFor` 注释与实现的语义疑点（reader-progress 决策 4.5）原样保留 D 现状，未验证。
- 未重跑未变证据：111 JVM 分页批、217 联合批、55 同步批等按指纹条件由集成说明引用，本轮门禁仅覆盖受影响 424 项。
- 桌面端、`.codex/`、发布范围未动。

## 交付状态与下一步

统一候选 `E:\creation-reading-assistant\unified-candidate-integration-v1\android` 已建立并通过受影响 JVM 门禁 424/424；精确 manifest、两组合 diff、保全核对、原始证据齐备。**该候选未做设备验收，必须如实标记为未设备验收。**本轮到此停止：不操作 MuMu、不回灌 D、不提交 Git；设备验收与 D 回灌由 Codex/下一授权决定。
