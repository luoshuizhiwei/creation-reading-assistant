# Profile 三文件归属取证与精确集成决策 v3

## 任务编号/版本

`CRA-PROFILE-MERGE-DECISION-v3`，2026-10-05。**只读源码取证与精确集成决策，不是实施者**。执行应用/模型：ZCode 客户端 / `new-provider-5/ling-3.1-flash`（与任务建议模型 Ling-3.1-Flash 一致）。

纠正对象：`docs/coordination/dispatches/2026-10/2026-10-04-next-joint-integration-manifest-v2.md` §二第 6、9 条对 ProfileRoute/ProfileViewModel 的方向误判（第 10 条方向正确，本任务复证）。纠正依据为本任务独立实读，不是只凭上一轮结论。

## 输入指纹

- 恢复仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`。开工实测 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，与交接入口一致；暂存区空（`git diff --cached` 无输出）；工作树含必须保护的 WIP（大量 M/D/?? 条目，本任务零改动）。
- 候选：`E:\creation-reading-assistant\next-round-joint-final-v3\android`（既有冻结输入，本轮实读未变）。
- 原始三方差异：`E:\creation-reading-assistant\next-round-joint-integration-manifest-v2\evidence\diffs`。

三文件九版本指纹（行数 / SHA-256 前 16 位，完整值见 `E:\creation-reading-assistant\profile-merge-decision-v3\execution.json`）：

| 文件 | HEAD | D 工作树 | 候选 |
|---|---|---|---|
| `ui/screen/profile/ProfileRoute.kt` | 513 行 `99eb2aaf…` | 571 行 `a1cad555…` | 644 行 `8fde6ce2…` |
| `ui/viewmodel/ProfileViewModel.kt` | 674 行 `9ef25b8f…` | 757 行 `f538cbb3…` | 777 行 `18b52513…` |
| `ui/viewmodel/ProfileViewModelTest.kt` | 206 行 `b940fbfc…` | 243 行 `4bcc8b2e…` | 210 行 `cefea0a3…` |

git diffstat（双口径之一，另一为 GNU diff -u；数值与 v2 报告记载完全吻合，机械层无误）：

| 文件 | HEAD→候选 | HEAD→D | 候选→D |
|---|---|---|---|
| ProfileRoute.kt | +147/−16 | +70/−12 | **+4/−77** |
| ProfileViewModel.kt | +138/−35 | +118/−35 | **0 增/−20** |
| ProfileViewModelTest.kt | +4/−0 | +40/−3 | **+39/−6** |

三文件三侧全部 LF 行尾，无 CRLF 干扰。本轮独立 diff 的 cand2dw 变更行集合与 v2 原始证据对应三份 cand2dw **逐行一致**——既证明两侧树自 2026-10-04 23:38 以来未变，也证明 v2 报告的差错不在证据、只在其文字解读。

## 所有权与改动

本任务只读。只写入两处：`E:\creation-reading-assistant\profile-merge-decision-v3\`（证据快照与 diff）与本报告。未修改任何源码、旧清单、旧报告、current/TODO、公共配置或他人文件；无 commit/push/reset/checkout；无 ADB/MuMu/实体手机/正式书库操作；未运行构建与测试。

## 环境

Windows / Git Bash；git 只读子命令（rev-parse/status/show/diff --no-index）；sha256sum；GNU diff -u；cmp。未启动 JDK/Gradle/JUnit；未连接任何设备。

## 执行与结果

### 方向约定（每份 diff 的左右侧）

- `head2cand` = 左 HEAD → 右候选；`head2dw` = 左 HEAD → 右 D 工作树；`cand2dw` = 左候选 → 右 D 工作树。
- cand2dw 中 `-` 行 = **候选独有**（D 无）；`+` 行 = **D 独有**（候选无）；两侧等价内容不出现在 diff 中。
- 快照与 diff 全文在 `E:\creation-reading-assistant\profile-merge-decision-v3\evidence\`（HEAD_/DW_/CAND_ 各 3 份 + 三向 diff 各 3 份）。

### 文件 1：ProfileRoute.kt —— **采用候选**

逐 hunk 归属（cand2dw 全文仅 4 个 `-` 块，无 `+` 语义块）：

| hunk | 归属 | 证据（候选侧行号） |
|---|---|---|
| imports：Arrangement/Column/Row/fillMaxSize/fillMaxWidth/padding/MaterialTheme/Surface/Modifier/dp + `LocalZipRestoreState` | **候选独有** | 候选 45–56 行区，cand2dw 第一个 `-` 块 |
| `val zipRestoreState by viewModel.zipRestoreState.collectAsStateWithLifecycle()` | **候选独有** | 候选 119 行 |
| `LaunchedEffect(Unit) { viewModel.refreshZipRestoreState(context) }` | **候选独有** | 候选 205–206 行 |
| `Column(Modifier.fillMaxSize())` + `ZipRestoreInterruptedBanner(...)` + `ProfileScreen(..., modifier = Modifier.weight(1f))` | **候选独有** | 候选 377–388 行；重试入口 `handleAction(ProfileAction.ImportZip)` |
| `private fun ZipRestoreInterruptedBanner(state, onRetry)` 整函数（含 STAGE_STARTED/STAGE_DB_COMMITTED/STAGE_UNKNOWN 文案、失败摘要、"重新选择同一备份重试"按钮） | **候选独有** | 候选 410–457 行（函数签名 418 行） |
| D 侧 `ProfileScreen(state, onAction)` 调用（cand2dw 的 +4 行） | **双方等价**：与候选 Column 内调用同参数，候选仅多传 `modifier`；两侧 `ProfileScreen` 签名均有 `modifier: Modifier = Modifier`（已实读两侧 `ui/screen/ProfileScreen.kt` 57/60 行 vs 47/50 行） | — |

**双方等价（不出现在 cand2dw，即 v2 报告所称"候选同步链"实为两侧共同基线，非候选独有）**：`focusAnchor` 参数与 `SettingsFocusController`/`LocalFocusedSettingsAnchor`/`LocalSettingsFocusController`/`LocalSettingsListState` 全套接线、`aiTesting`/`syncPrefs`/`aboutUpdate` 收集与透传、`OpenSettingsEntry` 锚点导航、`SetSyncItem`/`SetConflictStrategy`、`testWebDav(url,user,pass)`、DownloadRestore/Import 合并文案更新、Legado 段重排、"已清理阅读器临时缓存"文案、`ClearReaderCacheNow`、`CheckUpdate`。

- **D 独有：无**（语义层面）。**真正冲突：无。**
- 结论：**采用候选**。候选 = D + 整包恢复中断横幅块；D 无任何独有内容，"把 D 横幅叠回候选"没有操作对象。v2 §二第 6 条"D=N1-repair-v1 的 ZipRestoreInterruptedBanner+zipRestoreState 三段（cand2dw +4/-77 全为 D 独有）"方向读反：其自身证据里 −77 行全部是候选侧删除线。

### 文件 2：ProfileViewModel.kt —— **采用候选**

cand2dw 为纯删除（0 增/−20），即**候选 = D 的严格超集**：

| hunk | 归属 | 证据（候选侧行号） |
|---|---|---|
| `import …feature.sync.LocalZipRestoreState` | **候选独有** | 候选 31 行 |
| KDoc + `private val _zipRestoreState = MutableStateFlow<LocalZipRestoreState?>(null)` + `val zipRestoreState: StateFlow<…>` + `fun refreshZipRestoreState(context)`（磁盘读取 `localZipBackup.readRestoreState(context)`，失败 `AppLog.w` 后 fail-open 到 null） | **候选独有** | 候选 331–348 行（状态 338–339，函数 342–348；注释自署 N1-repair-v1） |
| `exportBackup` 末尾 `refreshZipRestoreState(context)` | **候选独有** | 候选 431 行 |
| `importBackup` 末尾 `refreshZipRestoreState(context)` | **候选独有** | 候选 441 行 |

**双方等价（逐字相同）**：构造参数重排（`legadoImporter` 移至 `webDavBackup` 后）、`syncPrefsStore` 注入、`syncPrefs` 流与 `setSyncBooks/Progress/Inspirations`/`setConflictStrategy`、`rebuildSearchIndex` 改走 `searchIndexRepository.triggerFullRebuildNow(searchIndexScheduler)`（含孤儿行回收说明）、`syncNow` 读 `syncPrefsStore.prefs.first()` 组 `SyncOptions` 透传 `runSync(autoDownloadBooks, options)`、`_legadoBackups` 上移与 `loadLegadoBackups`（失败不再写 `_webDavMsg`）、`testWebDav(overrideConfig)`、`importLegadoBackup` KDoc 移除、`clearWebDav` 顺序、`_aiTesting`/`aboutUpdate`/`ProfileUpdateChecker`/`checkUpdate`、`testAi` 的 try/finally。

- **D 独有：无。冲突：无。**
- 结论：**采用候选**。v2 §二第 9 条"候选=同步构造链；D=zipRestoreState/refreshZipRestoreState 纯增量；合并：候选基座 + D zip 恢复状态块"方向读反：zip 恢复状态块在候选里，不在 D 里；所谓"候选同步构造链"两侧逐字相同。

### 文件 3：ProfileViewModelTest.kt —— **以 D 为基座（精确组合，无需叠加候选块）**

cand2dw 为 +39/−6，全部为 D 侧增量或写法差异：

| hunk | 归属 | 证据 |
|---|---|---|
| imports：`SearchIndexRepository`/`SearchIndexScheduler`/`LegadoReadingProgressImporter`/`coVerify`/`verify` | **D 独有** | D 17–27 行区 |
| 类字段 `searchIndexRepository`/`searchIndexScheduler`（relaxed=true） | **D 独有**（候选为 setUp 内联同值 mock，供新测试引用才提升为字段） | D 65–66 行 |
| `syncPrefsStore` mock（`prefs` → `flowOf(SyncPrefs())`） | **双方等价**：候选 112 行单行写法 = D 114–116 行多行写法，relaxed 与流值相同 | — |
| 构造调用参数集合与顺序 | **双方等价**：两侧一致（`legadoImporter` 均在 `webDavBackup` 后；`syncPrefsStore` 均为末位注入，与生产构造器一致）；searchIndex 两参数候选内联、D 用字段，语义相同 | D 123–143 行 vs 候选 114–133 行 |
| 新测试 `` `rebuild search index delegates to the repository entry that purges orphan rows` `` | **D 独有** | D 159–180 行：`coVerify(exactly = 1) { searchIndexRepository.triggerFullRebuildNow(searchIndexScheduler) }`、`coVerify(exactly = 0) { searchIndexRepository.invalidateSweepForFullRebuild() }`、`verify(exactly = 0) { searchIndexScheduler.triggerNow(any()) }`、`assertEquals(1, messages.size)` |
| 既有测试体与断言 | **双方等价**：head2cand 仅增不删，HEAD 全部断言两侧逐字保留 | — |

合同核验（实读依赖，非仅看测试名）：`SearchIndexRepository.triggerFullRebuildNow(overrideScheduler: SearchIndexScheduler? = null)` 为 suspend（575 行），内部调 `purgeOrphanIndexRows()`（576 行；internal suspend 602 行）；`SearchIndexScheduler.triggerNow(force: Boolean)` 存在（64 行）；测试类 `mainDispatcher` 已定义（82 行）。该护栏守的接线（`triggerFullRebuildNow` 改写）**在两侧生产代码中逐字相同**，故 D 新测试对合并后（候选版）ViewModel 同样有效。

- **候选独有：无。冲突：无**（仅声明位置/格式差异，语义中性）。
- 结论：v2 §二第 10 条"D 版 = 候选适配（写法不同、语义同）+ 1 条 rebuild search index 新测试，以 D 版为基座"**方向正确，本任务复证成立**。

### 任务要求第 3 项：四处行为的归属核定

| 行为 | 归属 | 说明 |
|---|---|---|
| 恢复中断横幅（`ZipRestoreInterruptedBanner`） | **候选独有** | Route 候选 377–457 行；D 树零引用（全树 grep 无 `LocalZipRestoreState/zipRestoreState/readRestoreState`） |
| 磁盘状态读取（`readRestoreState`/`refreshZipRestoreState`） | **候选独有** | ViewModel 候选 342–348 行；底层 `LocalZipBackup.readRestoreState` 仅候选版有（见下） |
| 导入/导出后的刷新 | **候选独有** | ViewModel 候选 431、441 行两处 `refreshZipRestoreState(context)` |
| 同步接线（SyncOptions 透传、三开关、冲突策略、syncPrefs 流） | **双方等价** | Route/ViewModel 中两侧逐字相同，不是任何一侧的增量 |

### 依赖绑定（"采用候选"不可三文件单独执行）

候选 zip 恢复块编译依赖候选侧配套文件（均实读核对）：

| 文件 | 两侧关系 | manifest 类别（v2 §三/§五） |
|---|---|---|
| `feature/sync/LocalZipBackup.kt` | 候选版含 `readRestoreState(context)`（104 行）、`LocalZipRestoreStateStore` 注入、Mutex、测试挂钩；**D 版 == HEAD（干净，无 readRestoreState）** | "可采用但未授权写入" |
| `feature/sync/LocalZipRestoreStateStore.kt` | **候选新增**（`LocalZipRestoreStateStore` 48 行；`data class LocalZipRestoreState` 366 行，含 STAGE_* 常量） | "候选新增" |
| `test/…/feature/sync/LocalZipBackupTest.kt` | 候选修改 | "可采用"内"两测试"之一 |
| `test/…/feature/sync/LocalZipRestoreStateStoreTest.kt`、`LocalZipBackupPersistentRestoreTest.kt` | 候选新增 JVM 测试 | "候选新增" |
| `androidTest/…/feature/sync/LocalZipRestoreAcceptanceTest.kt` | 候选新增仪器测试 | v2 §5 来源已核（j3-n1-sandbox-build-v1 新增驱动类） |

其余直接引用类型两侧逐字节相同（已 cmp 验证）：`SearchIndexRepository.kt`、`SearchIndexScheduler.kt`、`SyncOptions.kt`（含 `ConflictStrategy`）、`SyncPrefsStore.kt`（含 `SyncPrefs`）、`ProfileWebDavManager.kt`（`test(overrideConfig: WebDavConfigStore.Config? = null): Result<String>`，50 行）、`ProfileUpdateChecker.kt`（含 `AboutUpdateState`）。

另：`ui/screen/ProfileScreen.kt` 候选 == HEAD，**D 独有顶栏搜索 WIP**（`ProfileTopBarSearchField` 等）；两侧签名均含 `modifier` 参数，候选 Route 的 `weight(1f)` 调用与 D 版兼容——ProfileScreen 不在三文件范围，保持 D 版即可（属 v2 清单 181 项 no-op 类）。

### 合流后最小验证建议（本任务不运行构建和测试）

1. **编译**：Route/ViewModel 取候选、Test 以 D 为基座、`LocalZipBackup.kt` + `LocalZipRestoreStateStore.kt` 同批合入后跑 `./gradlew :app:compileDebugKotlin`（或 `:app:assembleDebug`）；ProfileScreen 保持 D 版不应有编译问题（modifier 参数两侧皆有）。
2. **JVM 定向**：`:app:testDebugUnitTest --tests "*ProfileViewModelTest*"`（含 D 新增重建索引护栏，预期绿：两侧生产代码均为 `triggerFullRebuildNow` 接线）；同批跑 `LocalZipBackupTest`、`LocalZipRestoreStateStoreTest`（若 `LocalZipBackupPersistentRestoreTest` 一并采用亦跑）。
3. **复用既有设备证据，不重跑**：N1-ui-accept-v1 限定 UI 7/7（真 Profile/SAF 强停提示）、j3-n1-fix-accept-v2 核心合同 7/7（98 JVM、21 普通方法+3 真实强停）、`LocalZipRestoreAcceptanceTest` 合同来源 j3-n1-sandbox-build-v1。设备批次由唯一设备 owner 按当前 MuMu 合同另行组织；本报告不继承也不宣称任何设备通过。
4. 集成时 `app/build.gradle.kts` 候选后缀 `.qahostjoint20261004v3` 须改回 `.qahost`（v2 清单 §三警示）。
5. 按 hunk 应用，禁止整树覆盖；D 工作树 WIP（含 ProfileScreen 顶栏搜索等）必须原样保全。

## 原始证据

- 本任务证据：`E:\creation-reading-assistant\profile-merge-decision-v3\evidence\`——三侧快照 9 份（`HEAD_/DW_/CAND_` × 3）+ 三向 diff 9 份；`execution.json` 记完整 SHA-256 与方法。
- v2 原始证据：`E:\creation-reading-assistant\next-round-joint-integration-manifest-v2\evidence\diffs\` 对应三份 cand2dw。本轮 cand2dw 变更行集合与其**逐行一致**；v2 原始证据本身即显示横幅行为 `-`（候选侧）——v2 报告文字与自身证据矛盾，本任务纠正文字、不纠正证据。
- 候选依赖文件实读：`LocalZipBackup.kt`（readRestoreState 104 行）、`LocalZipRestoreStateStore.kt`（48/366 行）、两侧 `ProfileScreen.kt`、`SearchIndexRepository.kt`（575/576/602 行）、`SearchIndexScheduler.kt`（64 行）、`ProfileWebDavManager.kt`（50 行）。
- 交接与协调文档：`docs/handoff/current.md`、`docs/coordination/reviews/2026-10/2026-10-05-restore-manifest-v2-codex-review.md`、`docs/coordination/dispatches/2026-10/2026-10-04-next-joint-integration-manifest-v2.md`。

## 恢复与副作用

只读任务，无产品副作用：未改源码、未操作设备/MuMu/ADB、未触正式书库、未执行 Git 写操作。HEAD 与暂存区开工收工一致（收工前复核见下方"交付状态"）。仅有的两个写入点：E 任务目录与本报告。

## 未覆盖项

- 其余 semantic12 九文件不属本任务范围；`ReaderProgressActions.kt`/`ReaderProgressEffects.kt` 的方向与接缝问题已由本轮 Root 核查（2026-10-05-restore-manifest-v2-codex-review）记录，待各自决策任务处理，本报告不越权裁决。
- 三个"删除意图待核"HOLD 文件（`ProfileHomeScreenGestureTest.kt` 等）与本任务无关，未重新裁决。
- 候选 zip 恢复特性的完整交付链细节（N1-repair-v1/v2/v3 各轮报告）未重审，仅按 current.md 与 v2 §5 已核来源引用。
- 未运行构建/测试/设备验收；本报告是决策输入，**不宣称源码已集成或运行通过**。

## 交付状态与下一步

**已交付**：三个文件全部差异逐 hunk 归属与处理结论，无未知 HOLD 项：

| 文件 | 结论 |
|---|---|
| ProfileRoute.kt | **采用候选**（候选 = D + 横幅块；D 无独有；v2 第 6 条方向纠正） |
| ProfileViewModel.kt | **采用候选**（候选 = D + zip 恢复状态块；D 无独有；v2 第 9 条方向纠正） |
| ProfileViewModelTest.kt | **以 D 为基座**（候选适配语义已含于 D + D 独有重建索引护栏；v2 第 10 条复证成立） |

组合要点：候选 zip 块必须与 `LocalZipBackup.kt`（候选版）+ `LocalZipRestoreStateStore.kt`（候选新增）**同批**由单一集成 owner 处置，否则 `refreshZipRestoreState` 无 `readRestoreState` 可调、编译失败；Test 不需要再向 D 叠加任何候选块。

下一步：交单一集成 owner 按本报告执行精确合流（按 hunk 应用、保全 D WIP、build.gradle.kts 后缀改回 `.qahost`），随后按"合流后最小验证"跑编译与 JVM 定向；设备验收与 Git 提交仍待单独授权。本任务到此停止，不应用 patch、不启动下一任务。
