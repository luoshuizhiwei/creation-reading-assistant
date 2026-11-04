# S14 ZIP 恢复 crash residue 安全收口 · 实施与判别测试结果（2026-09-28）

> **Codex 2026-09-28 独立复核：暂不验收，须返修。** E 盘原始 XML 确认 `LocalZipBackupTest` 22/22、0 失败/错误/跳过；这只支持已测夹具。当前 `matchStagingResidue`/`matchBackupResidue` 仅凭文件名判断归属：普通书籍文件若恰好符合完整后缀，`exportZip` 会跳过它（目标存在时 backup 同名文件亦被跳过）；`importZip` 还可能在目标缺失时把同名普通文件重命名。该情形不是“零删除即无数据丢失”，因为导出 ZIP 可缺书。`matchBackupResidue` 对任意长度数字直接 `.toLong()` 也可抛溢出异常。原报告 §0 “导出只过滤可靠认定残留”及 §4 的低概率兜底不能成立。下一轮仅在本文件与测试所有权内补严格同格式碰撞、超长数字判别用例，并实现有可验证归属的处理；不能可靠认定时宁可保留文件并明确残留未清理。设备 kill/SQLite-WAL 仍 NOT RUN。

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`。未进入/未修改原仓库。
- 性质：**实施 + 判别测试 + 报告**。本轮代码写入 = [`LocalZipBackup.kt`](../../../../android/app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt)（crash residue 恢复与识别，最小安全规则）+ [`LocalZipBackupTest.kt`](../../../../android/app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt)（新增 6 项判别测试，其中 1 项为上轮导出缺口表征测试的改写承接）。文档写入 = 本报告。**未修改** JsonBridge / ProfileViewModel / ProfileRoute / SettingsStore / 书架文件 / schema / Gradle。
- 输入：[上轮决策报告顶部 Codex 复核](../../decisions/2026-09/2026-09-28-s14-postcommit-failure-decision.md)（关键更正：保护性换入两次 rename 之间的 kill 使 dst 缺失、backup 成为唯一完整旧字节，**不得按后缀盲删 backup**）、上轮四窗口划分、[MuMu 设备验收](../../../qa/2026-09/2026-09-28-s14-mumu-zip-safety-acceptance.md)。
- 时效自证：`LocalZipBackup.kt` 本轮读取时 mtime 2026-09-28 01:57:24（S14 修订版，未被其他窗口改动）；本轮修改后为唯一写入者。`LocalZipBackupTest.kt` 为上轮本窗口所写。

## 0. 结论先行

1. 已按「先红后绿」落地最小安全规则：**恢复优先（backup→dst 内容保全复位）、绝不盲删、staging 保留不作为恢复源、导出只过滤可靠认定的本工具残留**。红测 3 项（tests=22 / failures=3 / EXIT=1）→ 实施后全绿（tests=22 / failures=0 / errors=0 / skipped=0 / EXIT=0）。
2. **删除类清扫未实现，是有意的设计止步**：仅凭文件名无法达到「可靠认定为本工具产生」的删除门槛（完全同格式命名学冲突需要恢复日志元数据）。当前规则下**没有任何新增删除路径**，残留要么被内容保全地消费（backup→dst rename），要么原样保留——不删就无数据丢失风险。
3. **JVM 可证明的**：预置残留状态下的恢复/保全/过滤语义、既有全部行为保持。**仍未验证的**：真实 kill -9 在两次 rename 之间的现场、重启后 SQLite/WAL 回滚、残留现场多样性（多 backup 累积、跨目录、并发）——全部记 NOT RUN，交设备窗口（本窗口未运行 ADB）。

## 1. 残留状态模型与安全规则（源码事实 → 规则）

保护性换入（`copyWithStaging`，仅当直接 `staging.renameTo(dst)` 失败且 dst 存在时进入；Android POSIX 上 rename 原子替换，此路径平时不可达，Windows JVM 上为常态路径）：

```
① staging 写入 + 长度校验 → ② staging.renameTo(dst)（POSIX 原子替换）
→ ② 失败且 dst 存在 → ③ dst.renameTo(backup) → ④ staging.renameTo(dst) → ⑤ backup.delete()
→ ④ 失败 → backup.renameTo(dst) 自动恢复（恢复失败显式报错保留 backup）
```

kill 点 → 残留状态（Codex 复核确认的两种危险态加粗）：

| kill 点 | dst | backup | staging | 语义 |
|---|---|---|---|---|
| ① 中 / ② 前 | 旧字节完好 | — | 半写入或未落位 | staging 为垃圾，dst 无损 |
| **③ 与 ④ 之间** | **缺失** | **唯一完整旧字节** | 完整新字节（未落位） | 最危险：恢复目标 |
| **④ 后、⑤ 前** | 完整新字节（已校验） | 旧字节 | — | dst 胜出，backup 为被取代旧字节 |

已实施规则（`LocalZipBackup.kt`，`recoverCrashResidues` + 导出过滤）：

- **R1 恢复优先**：`importZip` 开始（解压与 DB 之前）扫描 books 目录：**dst 缺失 + 存在严格格式 backup → 取 nano 最大者 rename 回 dst**（内容保全的恢复，非复制后删除；nano 最大 = 最近一次换入前的 dst 字节）。恢复失败显式抛错并保留 backup。恢复只依据 books 目录自身状态，**不依赖本次 ZIP 是否含该书**。多 backup 并存（多轮 kill 累积）取 nano 最大者恢复，其余保留不删。
- **R2 不盲删**：dst 已存在（含目录占位）→ 一律不动；并存的 backup 全部原样保留。本轮**零新增删除路径**——工具自身 staging/backup 的清理语义（finally 清 staging、换入成功后清自己的 backup）不变。
- **R3 staging 保留**：不删除、不作为恢复源（内容可能半写入不可信；命名学冲突无法排除）；保留行为不阻塞任何路径。
- **R4 导出选择性过滤**（`exportZip` walk 内）：staging 残留一律不打包（可能半写入，永不让其进入备份链）；backup 在 **dst 存在**时跳过（已被取代的旧字节）；backup 在 **dst 缺失**时照常打包（唯一已知字节，导入端 R1 复原——备份链上不丢数据）；形近命名的普通文件不匹配严格格式，永不过滤。
- **识别格式的严格性**：`X.restore-<≥13位数字>.staging` 与 `X.restore-backup-<≥13位数字>`（工具用 `System.nanoTime()`，当前 19 位）；目标名含路径分隔符或为 `..` 时一律不视为残留（防恢复路径逃逸）。形近（非数字、位数不足、下划线变体）均不匹配。

## 2. 判别测试与运行证据

命令（在 `android/` 执行）：

```
gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.LocalZipBackupTest
```

| 轮 | 退出码 | XML（tests/failures/errors/skipped） | 说明 |
|---|---|---|---|
| 红（实施前） | 1 | 22 / **3** / 0 / 0 | T1、T5、T6 按设计失败，其余 19 项通过 |
| 绿（实施后） | 0 | 22 / 0 / 0 / 0 | 全部通过（16 项既有 + 6 项本轮） |

原始日志与 XML：`E:\creation-reading-assistant\zcode-s14-residue-jvm-20260928\`（`jvm-red-run1.log`、`jvm-green-run2.log`、`TEST-localzipbackup-green.xml`）。运行前后均核查 Gradle daemon 空闲（最后一次他人构建 16:30 结束，其后仅心跳）。

逐项判别点：

| # | 用例 | 判别点 | 红测 |
|---|---|---|---|
| T1 | `importRecoversMissingTargetFromLoneBackupResidue` | dst 缺失 + backup 独存（该书**不在** ZIP 内）：dst 被恢复为 backup 旧字节、backup 被 rename 消费、staging 保留、不依赖 ZIP 内容 | ✗→✓ |
| T2 | `backupAlongsideExistingTargetIsPreservedThroughReplace` | dst 与 backup 并存：dst 优先照常替换，**植入 backup 逐字节保留**（锁定"绝不按后缀盲删"），本次换入自己的 backup 照常清理 | 守卫（两轮均绿） |
| T3 | `stagingOnlyResidueIsPreservedAndDoesNotBlockReplace` | 仅 staging（dst 在 / dst 缺失两形态）：保留、不作恢复源、不阻塞替换 | 守卫 |
| T4 | `bookFilesWithResidueLikeNamesAreNeverTouched` | 形近命名（`-重要`、`12ab`、3 位数、下划线、空后缀）五类 decoy 全部逐字节保留 | 守卫 |
| T5 | `reimportWithResidueConvergesAndConsumesBackupKeepsStaging` | 残留存在时首轮导入完成「恢复+替换」（backup 被消费）、同 ZIP 重导幂等收敛、无 backup 残留新增 | ✗→✓ |
| T6 | `exportFiltersOwnResidueKeepsOnlyCopyBackupAndLookalikes`（承接改写上轮 `exportPacksKillResidueUnderBooksIntoFutureBackup`） | 导出：staging 不打包；dst 在的 backup 不打包；**dst 缺失的 backup 必须打包**；形近文件不过滤 | ✗→✓ |

红测三条失败消息与设计预期一一对应（「dst 缺失时唯一旧字节必须恢复为 dst」「首轮：植入 backup 被恢复路径消费」「staging 残留（内容不可信）不得打包」），无意外失败。

## 3. JVM 能证明与不能证明的边界（如实）

**本轮 JVM 已证明**：在预置残留状态下，恢复（R1）的字节保全性、并存 backup 的不删除性、staging 的不作为恢复源、导出过滤的精确边界、形近命名零误伤、既有 16 项行为（四窗口划分、单文件 staging 保护、版本感知跳过、部分失败重导收敛、W4 残留表征）全部保持。

**本轮 JVM 不能证明（全部 NOT RUN，不写 PASS）**：

1. **真实 kill 注入**：进程在两次 rename 之间被杀的现场多样性（多 backup 累积、跨目录残留、kill 恰在 rename 系统调用内）不可由 JVM 夹具模拟——本轮全部用预置状态驱动。
2. **重启后的 SQLite/WAL 恢复**与 DB-文件组合态（设备窗口，MuMu 由窗口 1 独占，本窗口未运行 ADB）。
3. **Windows/POSIX 差异的设备侧**：R1 恢复 rename 在 Android POSIX 上原子；并发窗口（恢复目标恰被同时创建）未测，rename 失败路径为显式报错保留 backup（源码保障，未注入验证）。
4. `LocalZipBackupPersistentRestoreTest`（MuMu 6 项）未复跑——其夹具无残留状态，理论上不受影响（R1 对无残留目录为 no-op），建议窗口 1 复跑确认。

## 4. 若要达到「删除级清扫」所需的接缝（本轮未实现）

命名学上，`X.restore-<13+位数字>.staging` 与真实书籍文件**可能重名**：R1 会把这类文件恢复为 X（内容保全但改名）、R4 会在其"dst"存在时把它从导出过滤掉——这是当前规则对完全同格式冲突的已知行为，风险以「13+ 位纯数字时间戳的巧合概率极低 + 从不删除」兜底。**根治需要恢复日志元数据**：`importZip` 在文件段开始前把每个目标的 {dst 名、staging 名、backup 名、阶段} 写入 journal（落点可选 books 下专用文件或 DataStore，**不动 Room schema**），完成一条删一条；凭 journal 才能「可靠认定为本工具 in-flight」，从而安全删除 staging/backup。该接缝限于 `LocalZipBackup.kt` 内部，与上轮策略 B 的持久日志是同一基础设施的子集；本轮止步原因：最小规则已消除数据丢失风险，journal 的复杂度（写入自身也可能被杀、需自愈）不值得在无删除需求时引入。

## 5. 最小验收矩阵

| # | 场景 | 层级 | 判据 | 状态 |
|---|---|---|---|---|
| 1 | backup 独存 → 恢复为 dst（不依赖 ZIP） | JVM | T1 | **本轮 PASS** |
| 2 | dst 与 backup 并存 → backup 保留不盲删 | JVM | T2 | **本轮 PASS** |
| 3 | staging 独存 → 保留、不作恢复源、不阻塞 | JVM | T3 | **本轮 PASS** |
| 4 | 形近命名书籍文件零误伤 | JVM | T4 | **本轮 PASS** |
| 5 | 残留存在时重导收敛 | JVM | T5 | **本轮 PASS** |
| 6 | 导出选择性过滤（staging/被取代 backup 不打包；独存 backup 打包；形近不过滤） | JVM | T6 | **本轮 PASS** |
| 7 | 既有行为保持（四窗口 7 项 + 单文件/版本/目录守卫 9 项） | JVM | 既有 16 项全绿 | **本轮 PASS** |
| 8 | 真实 kill -9（两次 rename 间）→ 重启 → 导入恢复旧字节 | 设备（MuMu qahost 隔离） | kill 注入 + 前后对账 | **NOT RUN**，交设备窗口 |
| 9 | 真实 kill 后导出过滤端到端 + SQLite/WAL 组合态 | 设备 | 同上 | **NOT RUN** |
| 10 | 整包跨 DB/文件原子性 | — | — | **设计上不承诺**（非原子 + 可收敛），任何报告不得标 PASS |

## 6. 边界声明

- 本轮文件触碰清单：`LocalZipBackup.kt`、`LocalZipBackupTest.kt`、本报告（所有权内）；未修改 ProfileViewModel / ProfileRoute / SettingsStore / JsonBridge / 书架文件 / schema / Gradle 配置；未触碰其他窗口文件。
- 未运行 ADB/MuMu；未接触真实手机、正式书库、qahost 既有数据；未 commit/push/reset/checkout。
- 上轮 A-1（失败反馈文案，`ProfileViewModel.kt:414-421`）与 A-3（失败窗口分类）不在本轮所有权内，仍为未实施，归 UI 轮。
- 大工件（红/绿日志、绿测 XML）在 `E:\creation-reading-assistant\zcode-s14-residue-jvm-20260928\`，仓库内只留测试-results 的构建产物（gitignore 范围）。
- 落盘即停，交总指挥复核。SEAM REQUEST：无；建议设备窗口在复跑 MuMu 6 项外，可选补一条「kill 注入于换入换出名」的设备级用例（§5 #8）。
