# S14 本地 ZIP 恢复「DB 提交后、逐文件复制」剩余非原子窗口 · 四窗口划分与收口决策（2026-09-28）

> **Codex 2026-09-28 独立复核（优先于下文 W4 与清扫结论）：** E 盘 XML 为 `LocalZipBackupTest` **17/17、0 失败/错误/跳过**，只证明隔离 JVM 定义的窗口。当前 `copyWithStaging` 在直接 `staging.renameTo(dst)` 失败时，会先将既有 `dst` 移到 `*.restore-backup-*`，随后再把 staging 移到 `dst`（`LocalZipBackup.kt:203-223`）。**进程恰在两次 rename 之间终止时，`dst` 可以暂时缺失，完整旧字节只在 backup；若在新文件落位后、backup 删除前终止，则新 `dst` 与旧 backup 同时存在。** 因此下文“任何 W4 点 dst 必为完整旧/新字节”“W4 只额外留下 staging/cache”“按模式清扫 backup”均超出源码保障；尤其不得在确认/恢复缺失目标前删除 backup。推荐策略 A 需先补 crash residue 恢复/保全规则与故障注入测试，再考虑清扫/导出过滤和用户指引。真实 kill/重启仍 NOT RUN，DB/文件整包仍非原子。

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`。未进入/未修改原仓库。
- 性质：**测试 + 决策报告**。产品源码本轮只读；本轮唯一代码写入 = [`LocalZipBackupTest.kt`](../../../../android/app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt) 新增 7 项隔离故障注入 JVM 测试；唯一文档写入 = 本报告。
- 输入：`AGENTS.md`、[交接入口](../../../handoff/current.md)、[S14 MuMu 验收报告顶部 Codex 复核](../../../qa/2026-09/2026-09-28-s14-mumu-zip-safety-acceptance.md)、[S14 只读调查](../../legacy-stages/2026-09/2026-09-28-s14-zip-restore-failure-atomicity.md)；`LocalZipBackup.kt` / `JsonBridge.kt` / `ProfileViewModel.kt` / `ProfileRoute.kt` 均为本日工作树版本（S14 修订已落地：逐文件 staging → 长度校验 → `renameTo` 原子替换 / Windows 保护性 backup 换入；版本感知跳过依据 `ImportMergeResult`）。
- 时效自证：本报告所有 file:line 读于 2026-09-28 15:40–16:40；`LocalZipBackup.kt`（mtime 09-28 01:57）、`JsonBridge.kt`（01:42）在读取后未被修改。**并行窗口正在 `ShelfStatusFilterTest.kt` 做 TDD 红灯中间态（16:24 起），与本窗口无文件交集。**

## 0. 结论先行

1. 四窗口中，**W1（DB 提交前）安全**、**W2/W3（提交后单文件/部分失败）"每文件字节无损但集合错位"**、**W4（进程被杀）在 W3 基础上额外留下可清理的残留**。S14 修复后，**任何窗口都不再产生半写入的目标文件**——单文件粒度 dst 只能是完整旧字节或完整备份字节；剩余非原子性只存在于"DB 事务 vs 多文件集合"之间，这与类注释（`LocalZipBackup.kt:31-41`）声明的设计一致。
2. **推荐策略 A**：保持"可重试的部分恢复"，补两项——失败时给出结构化指引文案（DB 状态说明 + 重导收敛指引）、顺手修掉 W4 残留卫生缺口（导入清扫 + 导出过滤）。**不推荐 B（持久恢复日志 + 重启续做）**：B 的收益只是"免手动重导"，而重导已被证明幂等且文件自愈；B 自身引入新的非原子点（日志写入）与 SAF 持久权限脆弱性，使其必须保留 A 的指引作兜底，即 B ⊃ A 的成本。
3. 本轮 JVM 可证明的都已给出测试；**不可由 JVM 夹具证明的部分（真实 kill 后重启收敛、SQLite 崩溃回滚、跨 DB/文件整包原子性）明确记为未验证，不写 PASS**（§4 矩阵）。

## 1. 四窗口精确划分（源码事实 + 本轮 JVM 证据）

执行顺序（`LocalZipBackup.importZip`，`LocalZipBackup.kt:78-129`）：解压到 `cacheDir/zip-restore-<millis>` → 校验 `backup.json` → `JsonBridge.importFromString`（单事务，提交后回调 `ImportMergeResult`，`JsonBridge.kt:373-494`）→ `copyBookFiles` 逐文件 `copyWithStaging` → finally 清临时目录。

| 窗口 | 边界定义 | 失败瞬间的状态 | 证据 |
|---|---|---|---|
| **W1 DB 提交前** | 解压段（损坏 ZIP / IO / ZipSlip）→ `backup.json` 校验 → DB 事务本身 | DB 未提交或已整体回滚；books 目录零变化；无任何复制残留；临时目录被 finally 清理 | 源码 + 本轮 JVM W1-a（DB 抛异常）、W1-b（损坏 ZIP）+ 既有 JVM 缺失 backup.json 用例 + MuMu 用例 2/3（设备侧） |
| **W2 提交后首个文件失败** | `importFromString` 正常返回（合并结果已回报）后，第一个 `copyWithStaging` 抛异常（staging 写失败 / 长度校验失败 / mkdirs 失败 / 目录占位拒绝） | DB=备份世界（∪ 保留行）；**零目标文件被替换**；旧文件字节零损伤；无残留；tmp 清理 | 本轮 JVM W2 |
| **W3 部分复制后失败** | ≥1 个文件完成替换后，某文件失败，`walkTopDown` 循环中止上抛 | DB=备份世界；已完成者=完整备份字节；未到达者=缺席；受保护（本地较新）者=本地新字节；**无半写入、无残留** | 本轮 JVM W3 + W3 收敛（重导后全量收敛且版本感知仍生效） |
| **W4 进程被终止** | kill -9 / 断电，**finally 不执行**，循环在两行指令间任意点断开 | 同 W3 瞬间状态，另加**残留**：books 内 `*.restore-<nano>.staging`（半写入，但 dst 无损）与 `cacheDir/zip-restore-*`；SQLite 依赖 WAL/journal 在下次打开时回滚未提交事务 | **部分未验证**：残留的**后验后果**已由 JVM 表征 2 项证明（不阻塞恢复、不被清理、会被导出打包）；真实 kill 后的重启收敛与 SQLite 回滚 = **未验证（设备窗口）** |

**单文件不变量**（源码构造 + 既有 MuMu 用例 4/5 + 本轮各窗口断言共同锁定）：`copyWithStaging`（`LocalZipBackup.kt:185-227`）全程只写 staging，`renameTo` 原子换入，替换失败保留旧目标——因此 W2/W3/W4 中每个目标文件要么是完整旧字节、要么是完整备份字节，**不存在半写入目标**。这是 S14 修复与旧实现（`FileOutputStream` 先截断）的本质差别；剩余风险全部收敛到"集合错位 + 可重导收敛"这一档。

**本轮新发现的 W4 后验缺口**（JVM 表征固定，产品源码本轮只读未改）：

1. `importZip` 不清扫 books 下旧 `*.restore-*.staging` 残留（无启动/导入清扫）；
2. **`exportZip` 的 `walkTopDown` 会把残留打包进未来备份 ZIP**（`LocalZipBackup.kt:64-70`），并在下次恢复时作为普通书籍文件还原到目标设备。风险评级：低（不影响既有书籍字节，备份仍可恢复），属数据卫生缺口，建议随策略 A 一并修（见 §3 A-2）。

## 2. 本轮 JVM 测试证据

命令（在 `android/` 执行）：

```
gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.LocalZipBackupTest
```

**最终轮：EXIT=0，BUILD SUCCESSFUL**（日志 `E:\creation-reading-assistant\zcode-s14-postcommit-jvm-20260928\jvm-test-run3.log`；run1 为修复前记录——17 项 1 失败，失败为本测试自身的计数 bug，见下；run2 因并行窗口 `ShelfStatusFilterTest.kt` 的 TDD 红灯中间态编译失败，与本窗口无关）。

XML 统计（`android/app/build/test-results/testDebugUnitTest/TEST-com.creationreadingassistant.feature.sync.LocalZipBackupTest.xml`）：

| tests | failures | errors | skipped |
|---|---|---|---|
| 17 | 0 | 0 | 0 |

其中既有 10 项 + 本轮新增 7 项：

| # | 用例 | 窗口 | 判别点 |
|---|---|---|---|
| 1 | `dbImportFailureBeforeCommitLeavesFilesAndCacheUntouched` | W1-a | DB mock 抛异常（=回滚）：合并回调 0 次、books 零变化、无残留、tmp 清理 |
| 2 | `corruptZipFailsBeforeDbAndLeavesFilesAndCacheUntouched` | W1-b | 损坏 ZIP：DB mock 0 次调用、books 零变化、tmp 清理 |
| 3 | `firstFileFailureAfterCommitKeepsAllTargetsUntouchedWhileMergeResultReported` | W2 | 回调恰 1 次（DB 已提交）且零文件被替换——与 W1（回调 0 次）、W3（≥1 文件已换）双向区分 |
| 4 | `partialCopyFailureRestoresOnlyCompletedFilesAndLeavesUnreachedAbsent` | W3 | 恰 1 个全新文件=备份字节、另 1 个缺席、受保护文件=本地新字节；顺序无关集合级断言 |
| 5 | `reimportAfterPartialFailureConvergesToBackupBytesAndKeepsProtectedFiles` | W3 收敛 | 同 ZIP 重导：全部=备份字节、受保护仍保留本地新字节、import 恰 2 次 |
| 6 | `residueFromKilledRestoreDoesNotBlockRecoveryAndIsNotSwept` | W4 表征 | 预置 staging 残留与 stale tmp 不阻塞恢复；现状不清扫（缺口固定）；本次运行自身零新增残留 |
| 7 | `exportPacksKillResidueUnderBooksIntoFutureBackup` | W4 表征 | 导出会把 `*.staging` 残留打包进备份（缺口固定；收口轮修复时应改写本用例） |

**夹具边界（如实声明）**：

- DB 以 relaxed mock 表达："importFromString 正常返回 + 回调收到 `ImportMergeResult`" = "事务已提交"，"抛异常" = "回滚/未提交"。真实 Room 事务回滚、SQLite WAL/journal 崩溃恢复**不在 JVM 可证范围**。
- 进程终止不可由 JVM 夹具模拟：W4 两项是**残留后验的表征测试**（预置残留观察后续行为），不是 kill 注入；真实 kill 场景记为未验证。
- Windows JVM 的 `renameTo` 对既有目标返回 false → 走保护性 backup 换入路径；Android(POSIX) 则原子替换。单文件替换语义的设备侧证据已由 MuMu 用例 4/5 覆盖（qa 报告），本轮 JVM 证据覆盖**窗口序列与集合级不变量**，两者正交、互不替代。
- run1 的失败教训登记：mock `answers` 块中"先自增计数再抛异常"会把"mock 被调用"误计为"回调被调用"——回调计数必须放在回调桩内。已修复。

## 3. 两种收口策略对比与推荐

### 策略 A：保持「可重试的部分恢复」+ 明确失败报告/恢复指引（推荐）

不改恢复机制本身，补三件事：

- **A-1 失败反馈结构化**：按失败窗口分类生成中文指引——DB 是否已切备份态（W1 未切 / W2-W3 已切∪保留行）、旧文件完好性说明、"重新导入同一 ZIP 即可收敛"、"空间不足请先释放"等可执行指引；替换现在的原始 OS message。
- **A-2 W4 残留卫生**：`importZip` 入口（或成功收尾处）清扫 books 下本工具模式的 `*.restore-*.staging` 残留；`exportZip` 打包时跳过 `*.restore-*.staging` 与 `*.restore-backup-*`。
- **A-3（可选）失败窗口分类**：`importZip` 失败时带出阶段信息（解压段/DB 段/文件段），供 A-1 文案与日志使用。

**需要修改的确切文件接缝**（下一实施轮；本轮全部只读未动）：

| 文件 | 接缝 | 改动 |
|---|---|---|
| `ProfileViewModel.kt` | `zipImport` onFailure 分支（:414-421，`_zipStatus.value = "整包导入失败：${it.message}"`） | 按窗口分类生成指引文案；`zipExport` 同理不需要 |
| `ProfileRoute.kt` | `:190`（`zipStatus` → snackbar 出口）、`:539`（确认弹窗已承诺"按版本规则合并"，成功路径经 S14 修复已兑现，无需改；失败指引文案随 A-1 落在 ViewModel） | 文案消费 |
| `LocalZipBackup.kt` | `importZip` catch/finally 段（:125-128）、`copyBookFiles` 入口（:151-153）、`exportZip` walk 过滤（:64-70）；可选新增带阶段的异常类型 | A-2 清扫/过滤、A-3 分类 |
| `LocalZipBackupTest.kt` | 本轮新增 W4 两项表征用例 | A-2 实施后改写为"被清理/被过滤"断言（红—绿） |
| `LocalZipBackupPersistentRestoreTest.kt` | 设备轮 | A-1/A-2 的隔离设备复验 + §4 #10 的 kill 矩阵 |

**迁移/兼容风险：零 schema 变更**。异常类型与文案为加法；清扫必须精确匹配本工具自身模式（`*.restore-*.staging` / `*.restore-backup-*`），不碰任何书籍文件；ZIP 备份格式不变。

**残留缺口**：用户不重导则一直处于中间态（每本书可打开、无字节损坏；DB 行为备份版本而未完成书文件为旧版本）；ZIP 丢失时无法自动回到恢复前 DB 态（旧文件仍在，但行版本已切换）——该残余与"备份为准"的既定合并设计一致，应写入产品文档而非代码修复。

### 策略 B：持久恢复日志 + 重启续做（不推荐，存档理由）

文件段开始前写恢复日志（ZIP 来源、合并结果、逐文件完成清单）；启动时检测未完成日志，提示续做（只复制未完成文件）或放弃。

- **接缝**：`LocalZipBackup.kt`（日志写入/读取/续做）、`App.kt`（启动对账）、`ProfileRoute.kt`/`ProfileViewModel.kt`（续做 UI 与 ZIP 重选）、持久层二选一——Room 路线 = `AppDatabase.kt` + `DatabaseModule.kt` + 版本 bump（v14→v15）+ 迁移 + `AppDatabaseMigrationTest` + `app/schemas` 快照；文件/DataStore 路线 = 免 Room 迁移但需自版本化与自愈。
- **固有脆弱性**：① ZIP URI 是 ActivityResult 临时授权，重启续做必须 `takePersistableUriPermission` 且仍可能被系统回收 → 续做退化为"再次选 ZIP"，即回到策略 A 的指引路径——**B 必须内嵌 A**；② 日志与 DB 提交之间仍是非原子点（提交后、写日志前被杀 = 无日志的 W2 态），只是把中间态种类变多；③ 崩溃矩阵只能设备验证，成本高。
- **收益**：仅"免手动重导一次"。而重导已具备三重证据：DB 幂等（MuMu 用例 6 + `SyncMergePolicy` revision 门控）、文件全量重写自愈、本轮 W3 收敛的 JVM 证明。

### 推荐

**A（A-1 + A-2 必做，A-3 随 A-1 顺手做），B 不立项。** 触发重估 B 的条件：真实用户反馈"失败后重导负担重"或出现大库（数百 MB，重导耗时显著）场景的实测数据。

## 4. 最小验收矩阵

| # | 场景 | 层级 | 判据 | 状态 |
|---|---|---|---|---|
| 1 | W1 DB 事务失败零副作用 | JVM | §2 #1 PASS | **本轮 PASS** |
| 2 | W1 解压失败零副作用 | JVM | §2 #2 PASS | **本轮 PASS** |
| 3 | W2 首文件失败：DB 已提交且零文件变化 | JVM | §2 #3 PASS | **本轮 PASS** |
| 4 | W3 部分失败混合态精确性（含版本感知） | JVM | §2 #4 PASS | **本轮 PASS** |
| 5 | W3 重导收敛 + 重导时版本感知仍生效 | JVM | §2 #5 PASS | **本轮 PASS** |
| 6 | W4 残留不阻塞恢复、现状不清扫（表征） | JVM | §2 #6 PASS | **本轮 PASS** |
| 7 | W4 残留被打包进未来导出（表征缺口） | JVM | §2 #7 PASS | **本轮 PASS** |
| 8 | A-1 失败文案含状态说明与重导指引 | ViewModel/设备 | VM 定向测试 + 设备观察 snackbar | 未实施（下一轮 A-1） |
| 9 | A-2 残留清扫 + 导出过滤 | JVM | 改写 §2 #6/#7 断言 | 未实施（下一轮 A-2） |
| 10 | 真实 kill -9 后重启：SQLite 回滚、残留现状、重导收敛 | 设备（MuMu qahost 隔离） | kill 注入 + DB/文件前后对账 | **未验证**，交设备窗口，不写 PASS |
| 11 | 整包跨 DB/文件原子性 | — | — | **设计上不承诺**（非原子 + 可收敛），任何报告不得标 PASS |

## 5. 边界声明

- 本轮未修改任何产品源码（`LocalZipBackup.kt`、`JsonBridge.kt`、`ProfileViewModel.kt`、`ProfileRoute.kt` 等均只读）；未触碰并行窗口所有文件（含其 TDD 中间态 `ShelfStatusFilterTest.kt`、`ShelfStatusCountsTest.kt`）；未运行 ADB/MuMu，未接触真实手机、正式书库、qahost 既有数据；未 commit/push/reset/checkout。
- 构建 discipline：运行前核查 Gradle daemon（daemon-47548 上一次构建 15:39 结束、此后空闲心跳）；run2 遇并行窗口编译中间态失败后未重试抢占，待其稳定后完成 run3。原始日志在 `E:\creation-reading-assistant\zcode-s14-postcommit-jvm-20260928\`（run1/run2/run3）。
- 所有"未验证"项均显式标注（§1 W4、§4 #8-#11），无一处以 JVM 表征冒充崩溃恢复或设备证据；报告与测试不含真实书名。
- 落盘即停，交总指挥复核。SEAM REQUEST：无（A-1/A-2 所需文件不与本窗口之外的既有所有权冲突，`ProfileViewModel.kt`/`ProfileRoute.kt`/`LocalZipBackup.kt` 的下轮 hunk 所有权请总指挥在实施轮分配）。
