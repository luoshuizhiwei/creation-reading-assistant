# CRA-EXACT-INTEGRATION-PREP-v2 —— 精确 D 集成清单（增量补正）

## 一、任务编号/版本

- 任务编号：`CRA-EXACT-INTEGRATION-PREP-v2`；2026-10-05。在 v1 机械清单基础上增量补正：绑定已冻结 TXT-v4、复用 semantic-v3 已验决策、明确仪器工件归属。不重新做整轮语义审查，不应用任何 D 产品变更。
- 执行者：ZCode（桌面应用，命令行 CLI）；客户端上报模型串 `new-provider-5/ling-3.1-flash`（未做界面核验）。
- 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`。
- 性质：**只读分析 + 清单准备**。不实施合并、不应用 patch、不构建、不操作设备、不改任何源码。

## 二、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 HEAD（开工=收工） | `e39a78da9c62f02614207cffb36969a32492eaea` |
| 暂存区 | 条目 0（开工=收工） |
| 统一候选验证 | `E:\creation-reading-assistant\unified-v1-codex-review-20261005\verification.json`（978 源码 SHA、JVM 424/0/0/0、manifest_rows 980） |
| TXT-v4 冻结 | `E:\creation-reading-assistant\txt-reentry-fix-v4\freeze\source-sha256.json`（980 条目；`txt-reentry-fix-v4/android` 目录实算 980/980 与冻结一致） |
| v1 清单 | `E:\creation-reading-assistant\exact-integration-prep-v1\integration-manifest.json`（980 条） |

**D 零漂移复核（工作项 1）**：本轮复算 D 当前 SHA 与 v1 清单记录逐文件对比——**960 在位文件全部一致、20 仍缺**（18 新文件 + 2 个 TxtReentry 测试），与 CRA-TXT-V4-INTEGRATION-PREP-CODEX-REVIEW-v1 的独立复算结论一致。**未变部分复用 v1 分析**，仅 TXT 四路径与决策映射做增量。

**v4↔统一候选交叉校验**：对 976 个非 TXT 路径逐对比 v4 冻结 SHA 与统一候选 verification SHA——**零不一致**。即 v4 树 = 统一候选 + TXT 修复；非 TXT 路径目标 SHA 与统一候选逐字节一致，TXT 四路径以 v4 冻结为最终目标。

## 三、所有权与改动

- 只写：`E:\creation-reading-assistant\exact-integration-prep-v2\`（`integration-manifest-v2.json`、`_gen_manifest_v2.py`、`_gen_auth.py`）与本报告。
- 唯一 D 写入：`docs/coordination/dispatches/2026-10/2026-10-05-exact-integration-prep-v2.md`（本文件）。
- 另一窗口负责构建/MuMu：**未读取其进行中工件作为最终输入**，未改其文件、公共配置、全局交接或 TODO。
- D/E 产品树、候选、旧报告、旧工件全部只读。无 Git 写操作（仅 `rev-parse`/`status` 只读子命令）。

## 四、环境

Windows / Git Bash / Python 3.13（仅 stdlib：hashlib/json/pathlib）；`diff`/`git` 只读子命令。未启动 JDK/Gradle、未连接设备、未运行任何测试或构建。**不重跑已输入匹配的 424 JVM、37 TXT 测试与设备批次**。

## 五、执行与结果

### 5.1 TXT-v4 绑定（工作项 2）

四路径替换为 v4 最终指纹；v3→v4 仅变 3 文件、1 文件原样：

| 路径 | v3 SHA | v4 SHA（最终目标） | v3→v4 变化 |
|---|---|---|---|
| `ui/screen/reader/ReaderProgressEffects.kt` | `90eef008…` | `749d0a89…` | P2：恢复 effect 实际 keys 加入 bid 与 plainListState |
| `ui/screen/reader/ReaderScrollPosition.kt` | `919fdcbe…` | `91c9643b…` | P1：plainRestoreSettled 新增 isLastUnit，中间目标 clamp 分支拒绝（36/37/37 拒绝、38/39/39 允许） |
| `test/.../TxtReentryRestoreFlowTest.kt` | `5cf6233b…` | `ecfa4421…` | 定向测试更新（新反例直接调用生产；wiring 测试只证明源码接入） |
| `test/.../TxtReentryRestoreTest.kt` | `7d9e9ea3…` | `7d9e9ea3…` | 原样 |

TXT-v4 验收状态：SOURCE_JVM_ACCEPTED（37 tests/0 failures/errors/skipped，7 XML 与冻结源码一致）。

### 5.2 决策映射（工作项 3）

semantic-v3 是历史决策来源，unified-v1 是已实施合流结果；以最终联合候选/v4 为当前目标，不退回早期候选。输入一致即引用已有决策，**不重新裁决 34 块**：

| 路径 | 已验决策 | 输入核对 | 最终候选 SHA |
|---|---|---|---|
| `data/repository/SyncRepository.kt` | 采用候选（第三组） | 候选 `b01cfda9…`/D `21802075…` 决策时与当前逐字节一致 → 直接引用 | `b01cfda9…` |
| `feature/library/ShelfImporter.kt` | 采用候选 | 候选 `bfc37d71…`/D `5120e75c…` 一致 → 直接引用 | `bfc37d71…` |
| `feature/reader/EpubParser.kt` | 采用候选（严格超集） | 候选 `27a4644d…`/D `af223434…` 一致 → 直接引用 | `27a4644d…` |
| `feature/reader/EpubRepository.kt` | 精确组合 = 候选全部 + D 的 S12-① 行 | D 一致（`4adb16e3…`）；候选侧已查明增量：final-v3 `15a92883…` → 统一候选 `a9814494…`，delta = S12-① 注释 1→5 行（契约文档化），代码行原样 → 决策仍适用 | `a9814494…` |
| `feature/sync/WebDavBackup.kt` | 采用候选（P2 修复后） | 候选 `1291b147…`/D `dd200bb6…` 一致 → 直接引用 | `1291b147…` |
| `test/.../ShelfImporterTest.kt` | 采用候选（纯增量包含） | 候选 `89e335be…`/D `e70b1074…` 一致 → 直接引用 | `89e335be…` |
| `ui/screen/reader/ReaderProgressActions.kt` | 精确组合：D 基座 + A1/A4/A6，保留 A2/A3/A5/A7 | D 一致（`933513e1…`）；候选侧已查明增量：final-v3 `e35ddf56…` → 统一候选 `86a92280…`，delta = 决策规定的合并结果本身，非未裁决差异 → 决策适用 | `86a92280…` |
| `ui/screen/reader/ReaderProgressEffects.kt` | 采用候选（严格超集）+ TXT-v4 P2 | 候选 `a59258be…`/D `a7baa349…` 一致；最终目标取 v4 冻结 | `749d0a89…`（v4） |
| `ui/screen/profile/ProfileRoute.kt` | 采用候选 | 候选 `8fde6ce2…`/D `a1cad555…` 一致；决策时候选与统一候选逐字节相等 | `8fde6ce2…` |
| `ui/viewmodel/ProfileViewModel.kt` | 采用候选 | 候选 `18b52513…`/D `f538cbb3…` 一致 → 直接引用 | `18b52513…` |
| `test/.../EpubRepositoryReplaceTest.kt` | 采用候选（替换 D 未跟踪旧版） | 候选 `39e7457d…`/D 未跟踪版 `6b254a75…` 一致 → 直接引用 | `39e7457d…` |

**结论：无待处理项**。所有决策输入要么逐字节一致（直接复用），要么已查明增量属于决策本身规定的实现（S12-① 注释扩展、A1–A7 合并结果），均有逐行核证。

### 5.3 两项必须保全语义（工作项 4）

- **EpubRepository S12-①**：`val globalOffset = absoluteOffset.takeIf { it >= 0 }` 在 D 当前、统一候选（L370）与 TXT-v4 三处均存在且代码行原样；S12-① 契约注释由 1 行扩为 5 行（文档化）。候选的 `if (absoluteOffset >= 0) absoluteOffset else legacyOffset`（把章内偏移冒充全局）不得采用——无全书偏移时 locator v2 `"offset"` 键缺省，由读侧 `chapterStart[ci]+co` 派生；伪造值会让 SourceNavigationContract 一致性校验拒绝所有回源跳转。
- **ReaderProgressActions D WIP＋MD 组合**：统一候选逐项落实 semantic-v3 第二组 A1–A7 全部要素——三 import 全保留（A1）；`atChapterEnd`/`txtScrollChapterTargetOffset`/`plainListState`/`readingUnits`/`livePlainOffset`/`chapterForLocator`（A2/A3/A5/A7，D 语义）；`markdownVisibleSourceOffset`/`MarkdownDocument`/`MarkdownRenderModel`（A4，候选语义）；A6 同区分支链 `pager → markdown(候选 canonical source 偏移) → livePlainOffset(D)` 已在位。

### 5.4 六个新增 androidTest 逐项分类（工作项 5）

| # | 路径 | 证据来源 | 可维护回归 | 一次性流程 | 临时身份/夹具 | 建议保留位置 |
|---|---|---|---|---|---|---|
| 1 | `acceptance/EpubReplaceFreshSourceAcceptTest.kt` | CRA-JOINT-ACCEPTANCE-TESTS-v1 fresh-source 合同（S04 §9-1 / rollback-wip-reconcile-v2/v3）；JVM ReplaceTest 同一合同的真机复验（真实 ContentResolver/EpubParser/EpubRepository/Room） | 是 | 否 | 隔离测试宿主（独立 applicationId 后缀+UID）；夹具书 id 生产派生，tearDown 精确清理；无硬编码 qahost 包名 | androidTest acceptance/；**待永久归属**（改造量小；对未合入 v3 修复的树运行会 FAIL） |
| 2 | `acceptance/MdPagedChapterFixV1Test.kt` | CRA-MD-PAGED-CHAPTER-FIX-v1（上游失败 device-md-r3；已验收 5/5 与收尾恢复） | 部分（真实 UI 由设备侧驱动脚本 mdpc_ui.py 完成，该脚本不在候选树内） | 是（验收批次驱动，分阶段 JSON+截图） | 结果写 mdpc-results；依赖外部驱动脚本 | **待永久归属**；部分回归价值由 JVM MarkdownChapterSourceTest 覆盖 |
| 3 | `acceptance/MdTrueChapterAcceptV1Test.kt` | CRA-MD-TRUE-CHAPTER-ACCEPT-v1（已验收 Root 六路径/90JVM/真实跨章 5 项） | 部分（真实 UI 依赖外部 ui.py uiautomator 驱动，不在候选树内） | 是 | 结果写 mdtc-results；依赖外部驱动脚本 | **待永久归属**（验收批次驱动，作为已验证据保留） |
| 4 | `data/repository/SyncRepositoryPersistenceTest.kt` | J1-v2 返修：R1 合并修复的真 Room 持久化验证（修复外键父行缺失 + SecurePrefs 触碰 KeyStore 两类夹具缺陷） | 是（真 Room 跨 close/reopen + 真 wire 合同 pull/push JSON；与 JVM SyncRepositoryLocalFactsTest 分工明确） | 否 | **六项中隔离最完整**：私有唯一库名；mockk-android 替身使 SyncConfigStore/DeviceInfoProvider 对 qahost prefs/KeyStore 零读写；MockWebServer 进程内；中性合成数据；@After unmockkAll | androidTest data/repository/；**待永久归属**（六项中改造量最小） |
| 5 | `feature/sync/LocalZipRestoreAcceptanceTest.kt` | N1 真实 Room/跨进程恢复验收驱动（J3-n1-fix-accept-v2） | **否**（文件自述「它是驱动，不是自动跑完的测试套件」；需设备执行者 am force-stop） | 是（写信号+有上限等待接缝，不抛 IOException 冒充真实终止） | **硬编码临时包身份** `com.creationreadingassistant.qahostn1`（.qahostn1 后缀）；n1-sandbox 沙箱目录与 n1_sandbox_restore_<scenario>.db 私有库名 | **临时验收排除**（不删不移） |
| 6 | `ui/screen/reader/ReaderAnnotationPendingConsumptionTest.kt` | CRA-JOINT-ACCEPTANCE-TESTS-v1：SE4「精确跳转」effect key 与 pending 时序最小仪器补验（M-BUG-10）；Compose 仪器环境真实组合生产 ReaderProgressEffects，断言 effect 实际执行结果 | 是 | 否 | 高亮/笔记实体只在内存 state（不落 Room）；DB 仅 AnchorCacheStore 真实 DAO（jse4- 前缀，tearDown 清理）；无硬编码临时包身份；自包含 | androidTest ui/screen/reader/；**待永久归属** |

分类原则：不默认全部交付，也不删除或移走任何工件；无充分依据则标「待永久归属」。

### 5.5 未跟踪旧测试替换的语义保全分析（工作项 6）

对象：`app/src/test/.../EpubRepositoryReplaceTest.kt`（D 未跟踪旧版 1310 行/21 @Test → 候选 1607 行/28 @Test）。**方法集合包含关系不足以证明语义保全**，故做逐行 diff 复核：

- D 的 21 用例逐字节包含于候选 28 用例（remaining-review 已核文本包含）。
- **D 相对候选的唯一独有内容 = KDoc 2 行「未覆盖缺口」声明**：①「长度相同但内容不同」的损坏备份不会被 `require` 拦下（只比长度）；② `require` 通过之后、`target.delete()` 之前进程被杀（无 fsync）。**无 D 独有断言、无独有测试准备、无独有测试行为**。
- 候选以 3 个插入块共 298 行（KDoc item-10 4 行 + 93 行/2 @Test + 201 行/5 @Test）覆盖：等长损坏备份拒绝（**缺口 1 由新测试钉住**）、fresh-source 逐字节核对、守卫前移残留清理、落地副本清理（备份失败/DB 写失败）。
- **缺口 2（进程被杀无 fsync）声明两侧共同保留**——JVM 不可直测进程杀灭，该场景由仪器层 EpubReplaceFreshSourceAcceptTest 的真运行时复验间接邻接，不作为语义丢失。
- 候选版本 lineage = rollback-wip-reconcile-v2/v3/v4（v4 已验收 149 JVM，Root 已合流）；D 版为同一演化文件的较早快照（Stage Q 强化版，KDoc items 1–9）。

结论：替换保全 D 全部测试语义；替换依据是逐行差异 + 已验验收证据，非「严格超越即可覆盖」的无条件宣称。

### 5.6 互斥类别清单（工作项 7）

类别互斥、计数自洽、无重复路径（980 条唯一）：

| 类别 | 计数 | 说明 |
|---|---|---|
| 无动作 | **939** | D 与候选逐字节一致（含 D==候选的临时命名文件与保持 D 版的测试） |
| 已有决策可应用 | **35** | 9 clean-apply（候选字节）+ 3 clean-merge（ProfileRoute/ReaderProgressActions/ProfileViewModel）+ 6 冲突→决策 + 1 替换 + 3 新增生产 + 9 新增 JVM + 4 TXT-v4 路径。「可应用」仅指技术上准备好，仍需本轮精确授权 |
| 待永久归属 | **5** | 仪器测试 #1/#2/#3/#4/#6（见 5.4） |
| 临时验收排除 | **1** | 仪器测试 #5 LocalZipRestoreAcceptanceTest |
| 待处理 | **0** | 无输入变化且决策未覆盖的真实差异 |
| **合计** | **980** | |

### 5.7 未来授权清单（工作项 8）

分组包而非 980 个应用动作（10 包覆盖全部 35 个可应用路径；完整 SHA/hunk 明细见清单 `future_authorization.bundles`）：

| 包 | 内容 | 必要配置/测试 | 关键排除项与停止点 |
|---|---|---|---|
| B1 | `app/build.gradle.kts`（2 hunk：release 签名显式开关 `craAllowDebugSigningFallback` + androidTest 依赖块 mockwebserver/mockk-android 1.13.12 排除 junit-jupiter/junit-platform 组） | 应用后 Gradle 同步；无独立测试 | 不改 signingConfigs 既有内容 |
| B2 | 八个候选字节文件（MarkdownDocument 1 hunk/CRLF、MarkdownChapterSource 6/LF、JsonBridge 9/LF、LocalZipBackup 55/LF、ReaderContentHostMarkdownBranch 3/CRLF、MarkdownDocumentTest 2/CRLF、JsonBridgeTest 3/LF、LocalZipBackupTest 6/LF） | 依赖 B1 依赖块；应用后 SHA=冻结 SHA，424 JVM 证据输入匹配可复用 | CRLF 文件按候选原始字节应用，不得行尾归一化；SHA 对账失败即停止 |
| B3 | ProfileRoute（23 hunk）+ ProfileViewModel（27 hunk）采用候选 | 依赖 B1+B8；sync-wip-reconcile 三套件 55/55 输入匹配可复用 | ProfileViewModelTest 保持 D 版不得覆盖；ProfileScreen 搜索等 D WIP 保留 |
| B4 | ReaderProgressActions 精确组合（12 hunk，CRLF） | 依赖 B8；TxtScrollChapterTargetTest（D 独有）合流后重跑；ReaderProgressActionsPersistTest/MarkdownVisibleSourceOffsetTest 随 424 复用 | ReaderActions/ReaderLayerBuilders/ReaderScaffold/ReaderScreen 不得覆盖（181 保留D，调用 A2/A3 新签名）；A6 分支链被改动即停止 |
| B5 | 五文件采用候选（SyncRepository/ShelfImporter/EpubParser/WebDavBackup/ShelfImporterTest） | SyncRepository 三套件 55/55、WebDavCompatibilityTest 27/27、ShelfImporterTest 63/63 输入匹配可复用；EpubParser 三套件（ParserText 28/MalformedCorpus 11/Metadata 3）须与 B6 同批新跑 | 判弃留痕项不恢复（ShelfImporter 守卫前 staging.delete()、WebDavBackup 两项轻微特性）；RollbackTest 若 stub EpubParser.parse 需按 parseUnderSwapGate 适配 |
| B6 | EpubRepository 精确组合（候选全部 + S12-① 行） | **五套件新跑**（ReplaceTest 28+RollbackTest 11+ParserText 28+MalformedCorpus 11+Metadata 3；精确组合后 SHA 与 rollback-wip-reconcile-v3 冻结不同，旧 147/147 不可移植）；S12-① 定向回归（章切换落库→locator v2 无 offset 键→SourceNavigationContract 按 chapterStart[ci]+co 派生成功）或核实既有 SourceNavigationContractTest 已覆盖 | S12-① 行不得移除；不得采用候选 legacyOffset 兜底；S12-① 缺失即停止 |
| B7 | EpubRepositoryReplaceTest 替换（D 未跟踪旧版 → 候选 28 用例版） | 随 B6 五套件新跑 | 依据 5.5 逐行分析；若发现除 2 行缺口声明外的 D 独有内容即停止退回核查 |
| B8 | 三新增生产文件（EpubSwapGate/LocalZipRestoreStateStore/ReaderAnnotationNavigation） | 编译门禁：缺 ReaderAnnotationNavigation.kt 合流树编译失败 | 必须与依赖方同批，不得拆分 |
| B9 | 九新增 JVM 测试（SyncRepositoryLocalFacts/EpubRepositoryConcurrency/EpubRepositoryReplaceRollback/MarkdownChapterSource/LocalZipRestoreStateStore/WebDavCompatibility/MarkdownVisibleSourceOffset/ReaderAnnotationNavigation/ReaderProgressActionsPersist） | 属已验 424 JVM 合同；应用后输入匹配可复用，不重跑 | JVM 证据不含真实 DB/设备（DAO MockK 属 JVM 契约证据），设备结论不得由此派生 |
| B10 | TXT-v4 四路径（ReaderScrollPosition `b9cd9043…→91c9643b…`、ReaderProgressEffects `a7baa349…→749d0a89…`、TxtReentryRestoreFlowTest 新增 `ecfa4421…`、TxtReentryRestoreTest 新增 `7d9e9ea3…`） | 37 tests/0 failures 已验（7 XML 与冻结一致）；不重跑 | 四路径同批应用（v4 冻结为原子交付），不得退回 v3 指纹 |

全局排除项：五个待永久归属仪器测试与 LocalZipRestoreAcceptanceTest 不在本授权范围；939 个无动作文件不动；D 已删除且不在 978 清单的 3 个文件（ProfileHomeScreenGestureTest/ProfileHomeStatPlaceholderSemanticsGuardTest/ReaderInspirationSnapshotSaveTest）保持删除态。全局停止点见清单 `future_authorization.global_stop_points`。

## 六、原始证据

- 本任务清单：`E:\creation-reading-assistant\exact-integration-prep-v2\integration-manifest-v2.json`（980 条逐文件记录：target_path / category / d_current_sha256 / target_sha256 / unified_candidate_sha256 / 决策或分类明细）；生成脚本 `_gen_manifest_v2.py`、`_gen_auth.py` 可复跑。
- v1 报告与清单：[2026-10-05-exact-integration-prep-v1.md](2026-10-05-exact-integration-prep-v1.md)、`E:\creation-reading-assistant\exact-integration-prep-v1\`（integration-manifest.json、diffs/、INDEX.md）。
- 决策来源报告：[2026-10-05-semantic-v3-codex-review.md](../../reviews/2026-10/2026-10-05-semantic-v3-codex-review.md)、[2026-10-05-remaining-semantic-merge-review-v3.md](../../reviews/2026-10/2026-10-05-remaining-semantic-merge-review-v3.md)（41 hunk 逐块结论）、[2026-10-05-reader-progress-merge-decision-v3.md](../../decisions/2026-10/2026-10-05-reader-progress-merge-decision-v3.md)（A1–A7/E1–E7）、[2026-10-05-profile-merge-decision-v3.md](../../decisions/2026-10/2026-10-05-profile-merge-decision-v3.md)。
- 统一候选与 TXT-v4：[2026-10-05-unified-v1-codex-review.md](../../reviews/2026-10/2026-10-05-unified-v1-codex-review.md)、[2026-10-05-txt-v4-integration-prep-codex-review.md](../../reviews/2026-10/2026-10-05-txt-v4-integration-prep-codex-review.md)（SOURCE_JVM_ACCEPTED，含本轮两项 P2 纠正要求）。
- 三方 SHA 证据：`E:\creation-reading-assistant\remaining-semantic-merge-review-v3\execution.json`、`E:\creation-reading-assistant\reader-progress-merge-decision-v3\execution.json`、`E:\creation-reading-assistant\profile-merge-decision-v3\execution.json`。

## 七、恢复与副作用

- D 仓库/Git/候选零写入；HEAD 仍 `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区 0 条（收工复核）。
- 无设备操作、无构建、无测试运行；已输入匹配的 424 JVM/37 TXT/设备批次未重跑。
- 另一窗口（构建/MuMu）工件未读；未改任何共享文件、公共配置、全局交接或 TODO。
- 全部产出在授权写入范围内：`E:\creation-reading-assistant\exact-integration-prep-v2\`（3 文件）+ 本报告。

## 八、未覆盖项

- 不重跑任何已验批次（424 JVM、37 TXT、设备旅程）；清单不含设备 PASS 声明，设备验收按当轮授权由设备 owner 执行（MuMu 模拟器验收）。
- 五个待永久归属仪器测试的最终归属（永久回归 vs 验收证据留存）由本轮精确授权决定；本清单不默认交付。
- B6 的 S12-① 定向回归（或 SourceNavigationContractTest 覆盖核实）与 RollbackTest 的 parseUnderSwapGate 适配核实，属合流实施时动作，本轮未执行。
- 桌面端/electron 边界不在本清单范围；未核对 `.codex/` 与根文档。
- 不给全产品百分比；本清单是集成技术准备，不构成合流授权。

## 九、交付状态与下一步

**已交付**：v2 清单（980 条互斥分类：939/35/5/1/0）+ 决策映射（11 文件）+ TXT-v4 绑定（4 路径）+ 六仪器测试分类 + 替换语义分析 + 10 包未来授权清单。验证项全部通过：计数自洽、无重复路径、v4 四路径指纹正确、v4↔统一候选 976 路径零不一致、已决议事项未被误列为新冲突。

**下一步**（均需当轮单独精确授权，本任务到此停止）：① 集成 owner 按 B1→B10 顺序与全局排除/停止点执行应用；② 应用后逐文件 SHA 对账（目标 = 清单 target_sha256）；③ 按各包必要配置/测试执行（B6/B7 五套件新跑 + S12-① 定向回归；其余复用已验证据）；④ 设备批次按统一验收合同 J1–J5 最小集由设备 owner 执行。
