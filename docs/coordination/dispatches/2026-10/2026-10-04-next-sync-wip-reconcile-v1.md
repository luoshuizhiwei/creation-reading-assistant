# 同步/Profile 修复与 WIP 语义三方合并

- 任务编号/版本：CRA-SYNC-WIP-RECONCILE-v1；2026-10-04。应用 ZCode/GLM。
- 输入指纹：HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开始/结束一致）；暂存区空（前后 0 条 staged）。固定输入：[已验产品基线](<E:/creation-reading-assistant/next-round-integration-build-accept-v2/android>)、[WIP 快照](<E:/creation-reading-assistant/integrationv2-three-deliveries-review-20261004/wip-snapshot>)（[manifest](<E:/creation-reading-assistant/integrationv2-three-deliveries-review-20261004/wip-snapshot/manifest.json>)，9 文件 SHA 锚定）。D 工作树对 5 个共享文件零漂移（D == WIP 快照字节一致）；2 个新测试文件仅存在于候选（不在 HEAD/D/WIP）。
- 所有权与改动：仅合并 4 产品 + 3 测试共 7 文件；不修改 ReaderProgressEffects、回滚模块、公共类型/构建配置。写入仅自有目录 `E:\creation-reading-assistant\next-round-sync-wip-reconcile-v1`（payload/patches/freeze/test-results/inputs/scripts/execution.json）与本唯一报告。未应用 D、未操作 Git 写、未启动设备、未跑全量测试。
- 环境：Windows 10.0.26300 x64 / Git Bash / Java 17.0.14（`JAVA_HOME=D:\develop\Java\jdk-17.0.14`）/ Android SDK `D:\develop\Android\Sdk` / Gradle 8.9。构建根 `…/next-round-sync-wip-reconcile-v1/android-build`（候选树副本）；独立 Gradle home `…/next-round-sync-wip-reconcile-v1/gradle-home`（`-g`，`--no-daemon`），不与并行窗口共享可写构建目录或 D 侧 Gradle 用户缓存。
- 执行与结果：定向单测 `:app:testDebugUnitTest --tests` 三套件，退出码 0，耗时 5m56s。**55 测试全绿**（SyncRepositoryLocalFactsTest 25/0/0/0、WebDavCompatibilityTest 27/0/0/0、ProfileViewModelTest 3/0/0/0；failures/errors/skipped）。编译通过（仅既有无关告警）。
- 原始证据：[execution.json](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/execution.json>)；[merged SHAs](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/freeze/merged-sha256.csv>)；[payload](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/payload>)；[patches](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/patches>)；[test XML](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/test-results/xml>)；[console log](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/test-results/run-2-console.log>)；[inputs 3-way](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/inputs>)。
- 未覆盖项：设备/仪器测试；全量 `:app:testDebugUnitTest`（仅 3 定向套件）；ReaderProgressEffects 合并（md-annotation owner，排除）；ShelfImporter/EpubRepository/ShelfImporterTest 合并（rollback owner，排除）；D 回灌/Git 提交/push（无本轮授权）；APK 构建（未请求）。
- 恢复与副作用：D 仓库未写入；D Git 未改；设备未用；共享 Gradle 缓存未改；仅在 E: 创建隔离构建目录与 Gradle home（可弃）。无恢复动作需要。
- 交付状态与下一步：可独立合并范围全部完成，主候选 7 文件已冻结新 SHA，定向回归 55/55 成立。仍需选择：无（Legado 经核查无实际删留冲突，两侧均保留）。下一步由集成者决定是否回灌 D / 提交 / 进入联合构建；本任务不越权执行。

## 输入指纹（三方）

| 路径（android/ 前缀省略） | HEAD blob | D 工作树 | WIP 快照 | 候选 | D==WIP |
|---|---|---|---|---|---|
| …/data/repository/SyncRepository.kt | ad3a45d3… | 21802075… | 21802075… | b01cfda9… | ✓ |
| …/feature/sync/WebDavBackup.kt | 6d4db3d8… | dd200bb6… | dd200bb6… | 1291b147… | ✓ |
| …/ui/screen/profile/ProfileRoute.kt | 99eb2aaf… | a1cad555… | a1cad555… | 8fde6ce2… | ✓ |
| …/ui/viewmodel/ProfileViewModel.kt | 9ef25b8f… | f538cbb3… | f538cbb3… | 18b52513… | ✓ |
| …/test/…/ProfileViewModelTest.kt | b940fbfc… | 4bcc8b2e… | 4bcc8b2e… | cefea0a3… | ✓ |
| …/test/…/SyncRepositoryLocalFactsTest.kt | NOT_IN_HEAD | NOT_IN_D | NOT_IN_WIP | f281635b… | n/a（候选新文件） |
| …/test/…/WebDavCompatibilityTest.kt | NOT_IN_HEAD | NOT_IN_D | NOT_IN_WIP | 16980171… | n/a（候选新文件） |

完整 CSV：[inputs/fingerprints.csv](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/inputs/fingerprints.csv>)。

## 合并决策（语义三方）

**总体结论：候选在全部 7 个拥有文件中是 WIP 的严格超集。** WIP 的门控（`SyncOptions`/`ConflictStrategy`/`syncPrefsStore`）已被候选保留；候选额外带来 R1 合并语义（SyncRepository）、已验 WebDAV 兼容（WebDavBackup）与 zipRestoreState（ProfileRoute/ProfileViewModel）。不按修改时间选边，不把"合并后编译"当语义验证——以下逐文件说明保留了哪些 WIP 与已验行为、哪些仍需选择。

### ① SyncRepository.kt → 候选（超集）

- **保留的 WIP 行为**：`pull(options)`/`push(options)` 的 `SyncOptions` 签名与 `syncBooks`/`syncInspirations`/`syncProgress` 门控；`ConflictStrategy` 参数化的 `shouldAcceptRemote`（REMOTE_WINS/LOCAL_WINS/null 三分支）；push 侧关闭类别发空列表（与 pull 门控对称）。
- **保留的已验行为**：R1 安全解析（`optString`/`optLong`/`optFloat` 拒绝 JSON null/非法类型/NaN）；`applyBook`/`applyProgress`/`applySession` 逐字段合并保留本机事实（`local_uri`/`content_status`/`current_location_json` 等）；进度 0..1↔0..100 尺度换算 + `completionState` 词汇映射；`completed_at` 跟随最终合并状态（S05-① 显式状态权威）；push `backfillBookPayload`/`backfillProgressPayload`/`backfillSessionPayload`（合并非透传）；filePath 缺失跳过并记 `failedItems`（不伪造路径）。
- **未保留的 WIP 行为**：无。WIP 的 `applyBook` 用朴素 `str()` 合并（有字面量 "null" 风险），候选的 `optString` 是其安全超集。
- **门控与已验行为共同成立的验证**：`SyncRepositoryLocalFactsTest` 的最后 4 个 `merged*` 测试直接验证 `repository.pull(SyncOptions(...))` 在 REMOTE_WINS/LOCAL_WINS/关闭类别三种门控下与 R1 合并语义共同成立。

### ② WebDavBackup.kt → 候选（超集，但 WIP 的放宽 host 判定不保留）

- **保留的 WIP 行为**：`getBytes`（Legado ZIP 二进制下载 + 上限校验）；`listLegadoBackups`（backup*.zip 过滤）；`basicAuth` UTF-8；`parseHttpDateMillis`；命名空间感知的 `parsePropFind`。
- **保留的已验行为**：`withRetry` 全随机抖动指数退避（仅瞬态 IOException）；`ensureCollection` MKCOL 有界重试（仅 404/409，不递归建目录）；`test()` HEAD→PROPFIND(Depth:0)→OPTIONS 回退链；`put()` 认证失败快速失败（401/403 不 MKCOL、不重试）；`resolveTarget` 经 `HttpUrl.addPathSegment`（杜绝双重编码）；`parsePropFind` 前缀剥离 + collection 排除 + href URL 解码；`requireSafeTarget` 严格 `PairingManager.isLanHost` + userinfo 拒绝。
- **未保留的 WIP 行为**：WIP 的 `isPermittedHttpHost`（放宽到无点单段主机名 + `.lan`/`.home`/`.internal`）**不保留**。依据：候选的 `WebDavCompatibilityTest.urlSecurity_rejectsPlaintextHttpForSingleLabelOrLanDomain_restoringPairingContract` 断言 `http://nas/`、`http://router.lan/` 等被拒绝，候选更严格的 `requireSafeTarget` 是已验合同。WIP 的 `isPermittedHttpHost` 函数体在候选中仍存在但未被 `requireSafeTarget` 调用（死代码，保持原样以减小 diff）。
- **核对项**：WebDAV 鉴权（Basic UTF-8 + userinfo 前置阻断）、请求构建（`HttpUrl.addPathSegment`）、PROPFIND（命名空间前缀剥离）、兼容逻辑（HEAD/PROPFIND/OPTIONS/MKCOL 回退链）均经 27 测试覆盖。

### ③ ProfileRoute.kt → 候选（超集）

- **保留的 WIP 行为**：`focusAnchor`/`SettingsFocusController`/`settingsListState`（设置搜索滚动）；`syncPrefs`/`aboutUpdate`/`aiTesting` 状态收集；`SetSyncItem`/`SetConflictStrategy`/`OpenSettingsEntry`/`CheckUpdate`/`ClearReaderCacheNow` action 处理器；`RefreshLegadoBackups`/`ImportLegadoBackup` 处理器（**两侧均保留 Legado**）；`LocalFocusedSettingsAnchor`/`LocalSettingsFocusController`/`LocalSettingsListState` CompositionLocals。
- **保留的已验行为**：`zipRestoreState` 收集 + `ZipRestoreInterruptedBanner`（N1-repair-v1 持久中断恢复说明条）；`LaunchedEffect(Unit) { viewModel.refreshZipRestoreState(context) }`；Column 包裹 banner 在 ProfileScreen 之上。
- **未保留的 WIP 行为**：无。

### ④ ProfileViewModel.kt → 候选（超集，无 syncPrefsStore 重复）

- **保留的 WIP 行为**：`syncPrefsStore: SyncPrefsStore` 构造参数（**单一声明，无重复**）；`syncPrefs` StateFlow + `setSyncBooks`/`setSyncProgress`/`setSyncInspirations`/`setConflictStrategy`；`syncNow` 读 `syncPrefsStore.prefs.first()` → `SyncOptions` → `syncEngine.runSync(autoDownload, options)`；`legadoImporter` 构造参数 + `_legadoBackups` + `loadLegadoBackups` + `importLegadoBackup`；`testWebDav(url,user,pass)` override-config 变体；`aiTesting`/`aboutUpdate`/`checkUpdate`/`ProfileUpdateChecker`；`rebuildSearchIndex` 走 `triggerFullRebuildNow`（清孤儿索引行）。
- **保留的已验行为**：`LocalZipRestoreState` 导入 + `_zipRestoreState` + `zipRestoreState` StateFlow；`refreshZipRestoreState(context)`；`zipExport`/`zipImport` 调用 `refreshZipRestoreState(context)`；`AppLog.w`。
- **未保留的 WIP 行为**：无。
- **避免 syncPrefsStore 重复声明/重复构造参数**：预检分析④担心的重复声明**未发生**——WIP 与候选都只声明一次 `syncPrefsStore`，候选版本与 WIP 完全一致。语义合并确认单一声明。

### ⑤ ProfileViewModelTest.kt → 候选（超集，无 syncPrefsStore mock 重复）

- **保留的 WIP 行为**：`syncPrefsStore` mock + `flowOf(SyncPrefs())`（**单一声明**）；`homeSummary must not trigger sub-page entity loads`；`libraryState collects entity flows only after sub-page subscribes`；`empty projections produce zero summary`。
- **保留的已验行为**：`rebuild search index delegates to the repository entry that purges orphan rows`（新测试）；`searchIndexRepository`/`searchIndexScheduler` 命名 mock 字段 + `coVerify`/`verify` 导入；`LegadoReadingProgressImporter` 导入 + mock 构造参数。
- **未保留的 WIP 行为**：无。
- **避免 syncPrefsStore mock 重复声明/重复构造参数**：预检分析⑤担心的重复声明**未发生**——WIP 与候选都只声明一次 `syncPrefsStore` mock。语义合并确认单一声明。

### ⑥⑦ SyncRepositoryLocalFactsTest.kt / WebDavCompatibilityTest.kt → 候选新文件（逐字复制）

候选独有新文件，不在 HEAD/D/WIP 中。`SyncRepositoryLocalFactsTest` 覆盖 R1 合并语义 + WIP 门控（最后 4 个 `merged*` 测试验证 `SyncOptions`/`ConflictStrategy`）；`WebDavCompatibilityTest` 覆盖 WebDAV 兼容（HEAD/PROPFIND/OPTIONS/MKCOL/重试/编码/安全）。逐字复制，未改测试断言。

## Legado 删留争议核查

任务要求："Legado 相关删除/保留存在争议：先查当前任务范围内的既有决定与来源。没有充分依据时，保留两份可审阅备选 patch 和未决说明；不要自行复活 WIP 删除的功能或删掉已验功能。"

**核查结论：无实际冲突，两侧均保留 Legado，无需备选 patch。**

预检文档 [2026-10-03-next-integration-scope-precheck-v1.md](../../decisions/2026-10/2026-10-03-next-integration-scope-precheck-v1.md) 分析③/④称"D 已删除 `ProfileAction.RefreshLegadoBackups` 处理器（H391-398 delete）"。实际核查固定 WIP 快照（manifest SHA 锚定）：

- WIP `ProfileRoute.kt` 第 462-469 行：`ProfileAction.RefreshLegadoBackups -> viewModel.loadLegadoBackups()` 与 `ProfileAction.ImportLegadoBackup` 确认对话框**均在**。
- WIP `ProfileViewModel.kt`：`legadoImporter` 构造参数（第 149 行）、`_legadoBackups`（第 607 行）、`loadLegadoBackups`（第 631 行）、`importLegadoBackup`（第 678 行）**均在**。
- 候选 `ProfileRoute.kt` 第 535-542 行、`ProfileViewModel.kt` 第 150/627-628/651-660/698-711 行：同样**全部保留**。

预检的行 diff 把重排/重编号误判为删除。当前任务范围内没有其他来源支持 Legado 删除决定。两侧语义一致（keep），故无需备选 patch、无需未决说明——直接保留两侧的 Legado 实现。

## 定向测试结果

命令：`./gradlew :app:testDebugUnitTest --tests <3 套件> -g <独立 gradle home> --no-daemon --console=plain`
退出码：0。耗时：5m56s。

| 套件 | tests | failures | errors | skipped | XML SHA256 |
|---|---|---|---|---|---|
| SyncRepositoryLocalFactsTest | 25 | 0 | 0 | 0 | 9045d3df… |
| WebDavCompatibilityTest | 27 | 0 | 0 | 0 | 90cf7651… |
| ProfileViewModelTest | 3 | 0 | 0 | 0 | 08eb7fd2… |
| **合计** | **55** | **0** | **0** | **0** | — |

关键验证测试（门控 + 已验行为共同成立）：
- `SyncRepositoryLocalFactsTest.mergedDisabledCategoriesPerformNoApply`：关闭全部 3 类门控 → 0 apply、0 计数。
- `SyncRepositoryLocalFactsTest.mergedRemoteWinsEqualKeepsR1FactsAndRejectsWrongTypes`：等 revision + REMOTE_WINS → 接受远端但保留本机事实、拒绝非法类型。
- `SyncRepositoryLocalFactsTest.mergedLocalWinsEqualDoesNotWriteDespiteNewerTimestamp`：等 revision + LOCAL_WINS → 不写。
- `SyncRepositoryLocalFactsTest.mergedLocalWinsStillAcceptsHigherRevisionWithLocalFacts`：高 revision + LOCAL_WINS → 接受但保留本机事实。
- `WebDavCompatibilityTest.urlSecurity_rejectsPlaintextHttpForSingleLabelOrLanDomain_restoringPairingContract`：`http://nas/`、`http://router.lan/` 等被拒绝（验证候选收紧的 host 判定）。
- `WebDavCompatibilityTest.urlSecurity_rejectsUserInfoInUrl_toPreventHostConfusionAttack`：userinfo 前置阻断。
- `WebDavCompatibilityTest.mergedUnsupportedHeadAndPropfindUsesOptions`：HEAD→PROPFIND→OPTIONS 回退链。
- `ProfileViewModelTest.rebuild search index delegates to the repository entry that purges orphan rows`：孤儿索引行回收入口。

新增回归未重复实现式测试：候选测试已直接验证门控（`SyncOptions(conflictStrategy=…, syncBooks=false, …)`）与已验同步行为（R1 字段合并）共同成立，无需另写。

## 交付物（精确路径）

- 主候选 payload（7 文件）：[payload/](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/payload>)
- 合并后源 SHA（新冻结）：[freeze/merged-sha256.csv](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/freeze/merged-sha256.csv>)
- before/after SHA：见上方"输入指纹"表（before = HEAD blob / D 工作树 / WIP 快照；after = merged = 候选）
- patches：[patches/](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/patches>)
  - `merged-vs-head.patch`（7 文件合并 vs HEAD）
  - `head-vs-merged_<file>.patch`（逐文件，5 个已存在文件）
  - `new-file_<file>.patch`（逐文件，2 个新测试文件）
- execution.json：[execution.json](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/execution.json>)
- 测试证据：[test-results/xml/](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/test-results/xml>)、[run-2-console.log](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/test-results/run-2-console.log>)
- 三方输入副本：[inputs/](<E:/creation-reading-assistant/next-round-sync-wip-reconcile-v1/inputs>)（head/dside/wip/cand 四子目录）

## 未覆盖项 / 仍需选择

- **仍需选择**：无。Legado 经核查无实际删留冲突（两侧均保留）。主候选无未关闭边界。
- **未覆盖**：设备/仪器测试；全量 `:app:testDebugUnitTest`；ReaderProgressEffects 合并（md-annotation owner，排除）；ShelfImporter/EpubRepository/ShelfImporterTest 合并（rollback owner，排除）；D 回灌/Git 提交/push（无本轮授权）；APK 构建（未请求）。
- **范围外依赖阻塞**：无。本任务的 7 文件合并自洽，不依赖其他 owner 的改动即可编译并跑通定向测试。

## 恢复与副作用

D 仓库未写入；D Git 未改；设备未用；共享 Gradle 缓存未改；仅在 E: 创建隔离构建目录 `…/android-build` 与 Gradle home `…/gradle-home`（可弃，不与并行窗口共享）。无恢复动作需要。

## 交付状态与下一步

可独立合并范围全部完成。主候选 7 文件已冻结新 SHA（不沿用旧 32 路径 SHA），定向回归 55/55 成立，门控与已验同步行为共同成立。下一步由集成者决定是否回灌 D / 提交 / 进入联合构建；本任务不越权执行，不回灌 D，不提交，不启动后续任务。
