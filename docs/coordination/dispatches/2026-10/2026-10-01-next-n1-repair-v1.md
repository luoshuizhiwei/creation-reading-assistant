# N1-repair-v1：整包 ZIP 恢复的中断持久状态与可证明归属的残留导出保护

## 一、任务编号/版本

- **任务编号/版本**：`N1-repair-v1`
- **性质**：独立 E 盘候选的**源码实施 + 定向 JVM 验证**。**不操作设备、不做联合集成、不提交。**
- **上游依据**：[W2-v2/D1 复核](../../reviews/2026-10/2026-10-01-next-w2v2-d1-codex-review.md)（§规划复核：「N1-repair-v1 在独立 E 副本实现和定向 JVM 可提前并行……N1 须复用已存在 journal，补整次导入持久中断状态/明确重试入口与可证明归属的残留导出保护；未知后缀文件保留，禁止按后缀误删」）、[N1–N3 总指挥回读](../../reviews/2026-10/2026-10-01-next-n1-n3-codex-review.md)（§N1：K3/K4/K5 存在 DB 已更新但文件未更新/缺失，**重启不会自动修好**；F1 孤儿解压缓存、F2 torn staging 为实际残留风险；无证据不得按后缀批量删除未知用户文件）
- **状态**：**已交付，待 Codex 独立核查**。不自行关闭数据安全缺陷。

## 二、输入指纹

| 项 | 值 |
|---|---|
| 活动仓库 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` |
| 分支 | `codex/recover-worktree-20260920` |
| **HEAD（开工核对）** | `e39a78da9c62f02614207cffb36969a32492eaea` —— 与任务预期一致 |
| **HEAD（收工复核）** | 同上，未变；暂存区未动；未 stage/commit/push/reset/checkout |
| 源码父候选 | `E:\creation-reading-assistant\next-round-joint-j1-v3\android` |
| 冻结清单 | `E:\creation-reading-assistant\next-round-joint-j1-v3\freeze\six-file-sha256.txt` —— **6/6 实算一致**（未修改任何冻结输入） |
| `LocalZipBackup.kt` 父 SHA-256 | `ab78a16e8e34c50a59ada31040278569baaa013f2c1eba6054a675b89997514c` —— 与预期值一致 |
| 父候选其余 3 个待改文件 SHA-256 | `ProfileViewModel.kt` `9ef25b8f4543f70db61ae4b785d183306b57ba3a43f54a02823df5cfcd043943`；`ProfileRoute.kt` `99eb2aaf558b5e819d9866d40145dac69fbcfe9212572a093295607d9def1b85`；`LocalZipBackupTest.kt` `174cfbf6cbd3ff35e182efc4b956ff46d72b4ded20ae7cc910bfb01cc29a690a` |
| 新候选 | `E:\creation-reading-assistant\next-round-n1-repair-v1\android` |
| 产品基线来源 | **J1-v3 冻结输入**；**未**从旧 N1 测试钩子候选取产品基线 |

## 三、所有权与改动

### 3.1 改动清单（仅授权路径，逐文件）

| # | 相对路径 | 类型 | 父 SHA-256 | 候选 SHA-256 |
|---|---|---|---|---|
| 1 | `app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt` | 改 | `ab78a16e…97514c` | `63e033ca66b3060960d25b4893d47805f2706495863b4f8ab58c06b9408d0805` |
| 2 | `app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStore.kt` | **新增** | （父无） | `47a763cb5742914312199528eb7b103cb302d69929582910b58c04b0b29e5888` |
| 3 | `app/src/main/java/com/creationreadingassistant/ui/viewmodel/ProfileViewModel.kt` | 改 | `9ef25b8f…043943` | `124517d2800853c80ea24c9af24bf945f7f988f003f8f3168b0cae1e6bb35231` |
| 4 | `app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileRoute.kt` | 改 | `99eb2aaf…def1b85` | `31e0cd0d4b82ecaf04b91f37da8752a7282fc47e2a07e21cf6d28db5067d4312` |
| 5 | `app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt` | 改 | `174cfbf6…29a690a` | `5d1b7439fb54d55bcedda2afe90130918a63595eb05167196805eb135d80baba` |
| 6 | `app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStoreTest.kt` | **新增** | （父无） | `4df47765dd9e3d7cf4eaab0edab323e3bb47a1fae3683596af288c4cbdef3cd5` |

**范围校验（逐文件哈希全树比对，排除 `build/`、`.gradle/`、`.kotlin/`）**：
父候选与候选之间 —— 父独有 **0**、候选独有 **2**（上表第 2、6 项）、内容不同 **4**（第 1、3、4、5 项）。
其余 **870 个文件逐字节相同**。**未越界**：未碰共享 D 盘产品源码、数据库 schema/migration、公共类型、Gradle 配置、其他 UI、旧证据、HOLD 与全局账本。

### 3.2 补丁

- `E:\creation-reading-assistant\next-round-n1-repair-v1\patch\n1-repair-v1.patch`
- SHA-256 `7f2c1f9915c9de1497c2ef1f6a48d7ffe0ebdb05771049009ebc13415cdbdbcf`
- **已在干净父副本上实测**：`git -c core.autocrlf=false apply --check` 通过、`apply` 后 6 个文件与候选 **逐字节相同**（`cmp` 全 OK）。
- 注意：本机 git `autocrlf` 会把 LF 转 CRLF，应用补丁需显式 `-c core.autocrlf=false`（父/候选本身均为 LF）。

### 3.3 实施内容（最小闭环，逐条对应任务第三节要求）

| 要求 | 落地 |
|---|---|
| ① 持久副作用之前可靠保存操作身份/阶段/归属；失败显式处理 | 新增 `LocalZipRestoreStateStore`（`filesDir/zip-restore-state.json`，**不在 books 树内**，不参与导出与残留识别）。`importZip` 在**任何持久副作用之前**（含 `recoverCrashResidues`）调 `begin()`；写临时文件 + `flush` + `fd.sync()` + rename 落位；失败上抛 ⇒ fail-closed，不再执行 DB 事务与文件换入 |
| ② 完成标记只在 DB 与文件全部完成且校验成立后关闭；重启后仍能识别未完成 | 阶段 `started → db_committed → files_copied`；只有 `copyBookFiles` 正常返回（逐文件 staging 长度校验均通过）才推进到 `files_copied` 并清除状态。`isComplete` 仅当 `files_copied`。失败/中断时状态留在磁盘，**新实例读同一文件即可识别** |
| ③ 现有 ZIP 操作 UI 给出中断说明与重试路径；跨新实例识别，不只靠 Toast | `ProfileViewModel.zipRestoreState`（从磁盘读，非内存 Toast）+ `refreshZipRestoreState()`；`ProfileRoute` 页首常驻说明条 `ZipRestoreInterruptedBanner`，含阶段说明与「重新选择同一备份重试」按钮，**复用既有的 `ProfileAction.ImportZip` 入口**（同一确认对话框 + SAF 选择器） |
| ④ 重试复用既有 journal 与安全复制、幂等、不做无依据整库回滚 | 未新增任何复制/回滚框架；重试即重新 `importZip`，复用 `recoverCrashResidues` + `copyWithStaging` + 版本感知跳过。**没有任何整库回滚代码** |
| ⑤ 未完成状态不得默默导出混合状态；未知后缀/不明归属文件必须保留 | `exportZip` 在未完成状态下**明确抛错阻止导出**（含阶段、失败摘要与重试指引）；**只阻止，不删除、不改名任何文件**。原有「无日志证据的同格式文件一律保留」语义未改 |
| ⑥ 完整旧副本、主异常/cause/suppressed、恢复失败可观察；清理异常不覆盖主异常 | 旧副本保护沿用 S14 既有语义（未改动）；失败摘要 `describe()` 展开主异常 + cause 链 + suppressed 并持久化；`finally` 中临时目录清理失败时 —— 有主异常则挂 `addSuppressed`（不覆盖），无主异常则显式上抛（避免静默留下 F1 孤儿缓存） |
| ⑦ 不宣称整包原子性、不做无依据架构重写 | 类注释明确「DB 先提交、文件在后，整包不是原子的」；**未**引入跨 DB/文件系统事务；未重写既有框架 |

**关键设计取舍（供复核）**：`begin()` 放在 `recoverCrashResidues` **之前**。理由：残留恢复会 rename/删除 books 下文件，属持久副作用；先落状态才能保证「任何持久副作用都有归属记录」。代价是极窄的假阳性——进程在 `begin` 后、任何实际改动前被杀，会显示一次「未完成」，用户重试即可幂等收敛。取 fail-closed。

## 四、环境

| 项 | 值 |
|---|---|
| 平台 | Windows；Git Bash（MSYS） |
| Gradle | wrapper `gradle-8.9-bin`，`--offline` |
| `GRADLE_USER_HOME` | `E:\creation-reading-assistant\next-round-n1-repair-v1\_cache\gradle-home`（**自有**，由 `next-round-joint-20261001` 缓存 robocopy 复制，2.8 GB；未与其他窗口共用可写缓存） |
| SDK | `local.properties` → `sdk.dir=D:\develop\Android\Sdk`（自父候选复制） |
| 构建目录 | 全部在候选自有 `android/app/build`（未触碰仓库构建目录） |
| 设备 | **未使用**。本轮零 ADB / 零 MuMu / 零安装 |
| 并发 | 未与其他窗口共享构建目录或缓存；未占用设备 |

## 五、执行与结果

### 5.1 编译

| # | 命令 | 退出码 | 说明 |
|---|---|---|---|
| 1 | `:app:compileDebugUnitTestKotlin` | 1 | 首轮失败：`LocalZipBackupTest.kt` 缺 `org.junit.Assert.assertNull` import（2 处），主源码已编译通过 |
| 2 | `:app:compileDebugUnitTestKotlin` | **0** | 补 import 后通过 |

### 5.2 定向 JVM（唯一执行范围）

```
:app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.LocalZipBackupTest
                      --tests com.creationreadingassistant.feature.sync.LocalZipRestoreStateStoreTest
```

- **退出码 0**；最终记录以 `--rerun` 强制实际执行（非 FROM-CACHE）。
- **原始 XML 实算：2 套件 / 51 tests / 0 failures / 0 errors / 0 skipped**。
  - `LocalZipBackupTest`：41 tests（基线 34，**新增 7**）
  - `LocalZipRestoreStateStoreTest`：10 tests（**新增文件**）
- **未**重跑全量 2673；**未**运行联合构建；**未**安装 APK；**未**执行 MuMu kill。

### 5.3 新增用例逐条对应任务第四节验证清单

| 验证要求 | 用例 |
|---|---|
| DB 提交后/文件复制中断状态持久保留，新实例能识别 | `interruptedAfterDbCommitLeavesDurableStateReadableByNewInstance`（`LocalZipBackupTest`）+ `begin persists started stage and new instance reads it back`（store） |
| journal/状态写入失败不进入未记录的危险阶段 | `stateWriteFailureStopsBeforeDbImportAndTouchesNothing`（并断言 `importFromString` **0 次**调用、书籍文件未动）+ store 的 `begin/markStage fails closed…` |
| 完整副本保留、异常链可观察 | `failureSummaryIsRecordedForInterruptedImport`、`describe never drops the primary exception message`、`recordFailure keeps stage and surfaces cause chain`；旧副本保护由既有 S14 用例继续锁定（本轮未改其语义） |
| 已完成状态不被误判中断、重复恢复/重试收敛 | `completedImportClearsStateAndIsNotReportedAsInterrupted`、`retryAfterInterruptionConvergesAndClearsState`、store 的 `markStage advances stage durably and completion is distinguishable` |
| 未完成状态的导出被明确拒绝或安全处理 | `exportIsBlockedWhileRestoreUnfinishedAndDeletesNothing` |
| 合法相似后缀文件/未知资产不会被误删 | `corruptStateBlocksExportWithoutDeletingUserFiles`；既有「无日志证据的同格式文件保留」用例未改动、继续通过 |
| 状态损坏、引用缺失或路径越界不能造成误清理 | `corrupt state file is treated as unfinished and deletes nothing`、`state file missing required fields is treated as unfinished`、store 的路径被目录占用时不代为删除 |

### 5.4 负向对照（证明用例卡住真实行为，不是镜像实现）

| # | 注入的回归 | 期望 | 实测 | 日志 |
|---|---|---|---|---|
| 1 | 关闭导出守卫（`takeIf { false }`） | 导出相关用例失败 | **恰 2 条失败**：`exportIsBlockedWhileRestoreUnfinishedAndDeletesNothing`、`corruptStateBlocksExportWithoutDeletingUserFiles` | `logs/negative-control-2.log` |
| 2 | 不记录 `db_committed`（写成 `started`） | 阶段识别用例失败 | **恰 2 条失败**：`interruptedAfterDbCommitLeavesDurableStateReadableByNewInstance`、`failureSummaryIsRecordedForInterruptedImport` | `logs/negative-control-3.log` |

对照后生产文件已还原（`LocalZipBackup.kt` SHA 回到 `63e033ca…`），最终绿跑见 §5.2。

**JVM 模拟中断只证明相应契约，不冒充真实进程 kill**（见 §七）。

## 六、原始证据

工件根：`E:\creation-reading-assistant\next-round-n1-repair-v1\`

| 路径 | 内容 | SHA-256 |
|---|---|---|
| `patch/n1-repair-v1.patch` | 6 文件补丁（a/b 前缀，`-p1`） | `7f2c1f9915c9de1497c2ef1f6a48d7ffe0ebdb05771049009ebc13415cdbdbcf` |
| `results/TEST-…LocalZipBackupTest.xml` | 41 tests / 0 fail / 0 err / 0 skip | `ab3af1df83512845afbcac752cad33d304eaea3273ef28e35ac7b84eec1d2f4a` |
| `results/TEST-…LocalZipRestoreStateStoreTest.xml` | 10 tests / 0 fail / 0 err / 0 skip | `2f44c155a3773a71637c17d55aee678f25780b4f3d72ba6e7b6fa625cc5df227` |
| `logs/compile-1.log` | 首轮编译（EXIT 1） | `02b0c9df7734a878ec51f738d3faf1d95378f09ecb16303e27652eeb2bbdb652` |
| `logs/compile-2.log` | 编译通过（EXIT 0） | `3e298a33a4fbde6d979e47bdb2e1377ded773bb8ce415c80f1c3e5e7465b710c` |
| `logs/test-targeted-1.log` | 定向首跑（3 失败） | `d4b1683176f7b820ebfbb98f2d3035a1c86161e67ab4131fb4d8f3df6a898d75` |
| `logs/test-targeted-2.log` | 修复后绿跑 | `4d00637daf446230375ba9fe2daea012763a4c33bded2d52802fe1b78f2d4fc8` |
| `logs/test-targeted-final.log` | **最终 `--rerun` 实际执行绿跑** | `ea4304e5eb7aa10f6a8475adf6434366dca19a630c1c08c200219d1a0f4799ba` |
| `logs/negative-control-1.log` | 对照 v1（编译不成立，作废保留） | `38c6e5c3f6bd91050f0570cbf8568e130ffa908eaeadf017b512fd1ebb14e525` |
| `logs/negative-control-2.log` | 对照 v2：导出守卫 | `1677a6eecfe195478012e9dfb0e784f375f7d04a67557c9986592b001d518f7e` |
| `logs/negative-control-3.log` | 对照 v3：db_committed 阶段 | `206f5c60ae6c9aa48f24c99f8b71baad82c07f6276d1f7f60ee07bca6ca8c6ac` |
| `logs/LocalZipBackup.kt.bak` | 负向对照期间的生产文件备份（还原依据） | — |
| `execution.json` | 本轮执行记录 | — |
| `android/` | 候选树（含构建产物） | — |
| `_cache/gradle-home/` | 自有 Gradle 缓存（2.8 GB） | — |

**首次失败的诚实记录**：定向首跑有 3 条失败（`LocalZipRestoreStateStoreTest` 2 条 + `LocalZipBackupTest` 1 条），根因是「状态路径被同名**目录**占用」未能制造写失败——空目录可被 `File.delete()` 删除。修正为生产代码只在 `file.isFile` 时才替换/清除，并把「路径被非普通文件占用」在 `read()` 中按保守未完成处理。该修正同时是更正确的行为（绝不代为删除目录）。原始失败日志 `logs/test-targeted-1.log` 保留。

## 七、未覆盖项

| # | 项 | 状态 |
|---|---|---|
| 1 | **真实进程 kill 下的整次中断行为** | **NOT RUN**。本轮 JVM 用 `fileCopyOperation` 接缝模拟「复制中途失败」，只证明**契约**（状态在 db_committed、可被新实例识别、导出被阻止），**不冒充真实进程被杀**。真实 kill 下状态文件是否已 fsync 落盘、`finally` 未执行时的现场，须由后续 MuMu 验收 |
| 2 | `ProfileRoute` 常驻说明条的**呈现与交互** | 仅编译通过；未做 Compose/设备验收（按钮是否可达、文案是否被截断、与 `ProfileScreen` 的布局权重是否合理） |
| 3 | 「导出被阻止」的用户可见路径 | 仅 JVM 断言异常与文案；未做设备验收 |
| 4 | 重试的**设备端**端到端 | JVM 已证同一 ZIP 重试收敛；设备端（SAF 重新选择同一文件）未验 |
| 5 | F1 孤儿解压缓存 | **不自动回收**，仅在清理失败时可观察（`addSuppressed` / 显式上抛）。本轮**未**引入按后缀清理 cacheDir 的逻辑 |
| 6 | F2 torn staging 进入后续备份 | 沿用 S14 既有 journal 语义（未改动）；本轮未新增验证 |
| 7 | 跨 DB/文件系统整包原子性 | **不做、不宣称**。本轮只补「中断可识别 + 明确重试 + 未完成禁止导出」 |
| 8 | 联合冻结 / 共享源码集成 / MuMu 中断验收 | **未做**。按任务要求等 Codex 确认 M1 退出后再串行安排 |
| 9 | 全量 JVM 2673 / 联合构建 / APK 安装 | **未跑**（不在授权范围） |

## 八、恢复与副作用

- **仓库**：HEAD `e39a78da…` 未变；未 stage/commit/push/reset/checkout；未移动仓库目录；未修改任何既有源码、测试、配置或其他窗口报告。
- **唯一仓库写入**：本文件 `docs/coordination/dispatches/2026-10/2026-10-01-next-n1-repair-v1.md`。
- **冻结输入**：J1-v3 `six-file-sha256.txt` 6/6 实收一致，**未修改**；J1/W2 冻结输入未被触碰。
- **父候选**：只读引用，未修改（父候选 4 文件 SHA 与开工时一致）。
- **设备**：零操作。无 APK 安装/卸载、无系统设置修改、无正式书库访问。
- **其他窗口资产**：未触碰 Antigravity 报告、他人 E 盘目录、公共账本。
- **自有目录**：`next-round-n1-repair-v1\` 下的候选、缓存、日志、结果、补丁均为本轮产出。
- **构建缓存**：使用自有 `_cache/gradle-home`（由既有缓存复制），未与其他窗口共用可写缓存。

## 九、交付状态与下一步

**交付状态：已交付，待 Codex 独立核查。** 不自行关闭数据安全缺陷。

- 交付物：精确改动清单（§3.1）、父/候选输入 SHA（§2、§3.1）、仅授权 6 文件的补丁（§3.2，已验证可复现候选）、定向原始结果（§5、§6）、剩余边界（§七）。
- 本轮**未**新增任何架构重写、未引入跨 DB/文件系统事务、未改动既有 journal/staging 语义。

**建议的后续（均需 Codex/总指挥排期，本轮不执行）**：

1. **Codex 独立核查**：复核 §3.1 的 6 文件 SHA、§5.2 的 XML 计数与 §5.4 负向对照日志。
2. **MuMu 中断验收**（须在 M1 退出、联合冻结之后，由唯一设备 owner 串行执行）：
   - 真实进程 kill 于「DB 已提交、文件复制中」窗口 → 重启后确认状态文件为 `db_committed`、`ProfileRoute` 常驻说明条出现、导出被阻止；
   - 重新选择同一 ZIP 重试 → 确认收敛（文件与 DB 一致、状态清除、导出恢复）；
   - 恢复本轮设备/数据基线并逐文件对账。
3. **UI 呈现验收**：说明条在真机手机布局下的可读性、按钮可达性、与「导入整包备份」入口的一致性。
4. **F1/F2 残留**：如需自动回收孤儿解压缓存，须单独立项并给出**可证明归属**的判据（本轮明确不做按后缀清理）。
5. **联合集成**：本候选须在 M1 退出后由 Codex 合流到联合候选，按变化范围重新门禁；**不复用**上一轮 142 路径提交授权。
