# S14 ZIP crash residue 严格同格式碰撞 · 超长数字 · 归属日志返修报告（2026-09-28）

> **Codex 2026-09-28 独立复核：32/32 JVM 证据有效，但安全结论暂不通过。** E 盘绿测 XML 为 32/0/0/0，源码 SHA 与 `final-source-hashes.txt` 可核。新日志已消除无证据文件仅凭名称被改名/漏出 ZIP 的上一轮问题。但存在未测的真实 kill 点：`journalAdd` 后、`dst.renameTo(backup)` 前，`dst` 仍是旧字节、完整 `staging` 已存在。当前 `recoverCrashResidues` 仅在 `!dst.exists()` 时落位 staging；随后 `dst.isFile` 分支删除 staging 并剪枝日志，留下与已提交 DB 不一致的旧正文。报告 §1.2 “③ 后、④ 前 staging 落位”、§0 全部修复及 §7 全通过均超出源码事实。另 `writeJournal` 吞掉写入失败且 `journalAdd` 继续执行破坏性 rename；不能声称日志写入成功先于每次 rename，更不能把普通 `writeText` 当成崩溃持久化保证。下一轮只在同两文件加“日志在、旧 dst 在、完整 staging 在、backup 不在”的判别测试，修正安全收敛并对日志写入失败采取 fail-closed 或明示且验证的等价保护；保留原字节与主异常。设备 kill、SQLite/WAL 仍 NOT RUN。

> 执行角色：ZCode (GLM 5.3 Flash)
> 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`
> 输入复核：[`2026-09-28-s14-crash-residue-safety-result.md`](2026-09-28-s14-crash-residue-safety-result.md) 顶部 Codex「暂不验收，须返修」注记——上一轮 22/22 JVM 绿测只支持已测夹具，`exportZip`/`importZip`/`matchBackupResidue` 存在三处按文件名判定归属的缺口
> 本轮性质：**判别测试（先红）+ 最小安全修复（后绿）+ 报告**。未运行 ADB/MuMu，未触碰真实手机、正式书库、qahost 数据；未 commit/push/reset/checkout；未修改所有权外任何文件。

## 0. 结论先行

1. Codex 复核指出的三个缺口全部以**先红后绿**的判别用例证实并修复：
   - **导出漏书**：普通书籍文件完整命中残留格式时，旧 `exportZip` 凭文件名跳过它（staging 格式一律跳、backup 格式在"dst"存在时跳）——导出 ZIP 直接缺一本书，是真正的数据丢失；
   - **导入擅改普通文件名**：普通文件完整命中 backup 格式且"dst"缺失时，旧 `recoverCrashResidues` 凭文件名把它 rename 成 dst；
   - **超长数字崩溃**：`matchBackupResidue` 对任意长度数字串直接 `.toLong()`，40 位数字即抛 `NumberFormatException`，导入/导出双双崩溃（红测 C3 实锤复现）。
2. 修复方案 = **恢复归属日志（ownership journal）**：books 根下 `.cra-restore-journal.json`，保护性换入的**第一次破坏性 rename 之前**写入 `{dst 相对路径, backup 名, staging 名}`。恢复/清理/导出过滤全部只认日志；**无日志证据的同格式文件一律视为普通书籍文件**——保留原名、导出照常打包、诊断日志（AppLog WARN）明确报告"未能自动清理"。不引入任何按文件名的改名/删除/跳过。
3. 红测 `32 tests / 13 failures / EXIT=1`（13 项失败原因与设计一一对应）→ 绿测 `32 tests / 0 failures / 0 errors / 0 skipped / EXIT=0`（19 项既有全保持 + 3 项改写 + 10 项新增）。
4. **本轮新增了证据级删除路径**（此前"零删除"止步被替代）：仅对日志归属的文件执行——被取代 backup 的 ⑤ 补完清理、op 已解析后残留 staging 的清理。无证据文件依然零处置。删除门槛从"文件名格式"提升为"日志 + 严格格式 + 三名一致性"三重校验。

## 1. 归属日志设计与其自身的被杀恢复语义（任务要求的审查项）

### 1.1 写入时序与不变量

```
copyWithStaging（保护性换入路径，POSIX 直接 rename 成功时不进入）：
① staging 写入 + 长度校验（完整可信新字节）
② staging.renameTo(dst) 失败且 dst 存在
③ journalAdd({rel, backup, staging})   ← 任何破坏性 rename 之前
④ dst.renameTo(backup)                  失败 → journalRemove + throw（dst 原样，无残留）
⑤ staging.renameTo(dst)                 失败 → backup 自动复位：成功则 journalRemove + throw；
                                                失败则保留条目 + throw（恢复端补完）
⑥ backup.delete()                       失败 → 保留条目 + throw（恢复端补完 ⑤）
⑦ journalRemove（换入完成，条目剪枝）
```

关键不变量：**日志条目存在 ⇒ 该 op 的 staging 已通过长度校验、且 DB 事务已提交**（`copyWithStaging` 只被 `copyBookFiles` 调用，后者在 `importFromString` 返回之后执行）。因此"按日志完成被打断的换入"（staging 落位为 dst）与已提交的 DB 一致。

### 1.2 进程被杀点 × 恢复行为（日志自身的鲁棒性审查）

| kill 点 | 日志状态 | 下次导入/导出的恢复行为 | 数据结局 |
|---|---|---|---|
| ③ 写日志中途 | JSON 残缺 | 解析失败 → **整体按无证据处理**（告警），全部同格式文件保留原名、导出照常打包 | 无损失（此刻尚无任何破坏性 rename） |
| ③ 后、④ 前 | 条目在，dst 旧字节完好 | dst 存在 → staging 按证据落位为 dst（完成换入），条目剪枝 | dst 由旧变新，与已提交 DB 一致 |
| **④、⑤ 之间** | 条目在，dst 缺失，backup=旧、staging=新完整 | staging 落位为 dst → backup（被取代旧字节）按证据清理 → 条目剪枝 | 无损失，收敛 |
| ⑤、⑥ 之间 | 条目在，dst=新，backup=旧 | dst 在 → backup 按证据补完 ⑤ 清理，条目剪枝 | 无损失 |
| ⑥ 中（delete 部分完成） | POSIX delete 原子；失败则等同上一行 | 同上 | 无损失 |
| ⑥ 后、⑦ 前 | 条目在，文件已不在 | 陈旧条目 → 剪枝 | 无损失 |
| ⑦ 剪枝重写中途 | JSON 残缺 | 同第 1 行：无证据兜底（已完成的换入不受影响，其余现场退化为保留+报告） | 无损失 |
| 日志写入失败（磁盘满等） | 无条目 | 换入继续；若其后被杀，现场退化为"无证据保留 + 报告"（与升级前旧版本残留同等对待） | 无损失 |

共同设计原则：**日志的任何异常形态只会把状态推向"无证据 → 保留 + 报告"，绝不推向"删除/改名"**。剪枝采用"读有效条目 → 过滤 → 整体重写"，重写被杀的后果是 JSON 残缺 → 下次按无证据处理，安全兜底。

### 1.3 伪造与注入面

- 归属日志**不被导出打包**（`rel == JOURNAL_NAME` 恒跳过）、**不被导入落盘**（`copyBookFiles` 跳过）→ 外来备份包无法注入/覆盖本地日志（测试 C9）。
- 条目读取时三重校验：`backup`/`staging` 必须严格匹配残留格式、目标名安全（无分隔符/非 `..`）、**三名的目标段必须一致**；不一致条目丢弃（无证据效力），其文件退化为保留+报告。

## 2. 恢复端规则（`recoverCrashResidues` 改写后的行为矩阵）

对日志证明的现场（按 dst 分组、组内按 staging nanoTime 新→旧，字符串数值序比较，**无 toLong**）：

| 现场 | 行为 |
|---|---|
| dst 缺失 + staging 在 | staging 落位为 dst（完成被打断的 ④；rename 失败显式抛错、文件保留、条目保留） |
| dst 缺失 + staging 缺 + backup 在 | backup 旧字节复位为 dst（兜底；失败显式抛错保留） |
| dst 在（含刚落位） | 同 op 的 backup 与 staging 按证据清理（⑤ 补完）；清理失败保留条目、下次再补 |
| 条目引用文件全部缺席 | 陈旧条目剪枝 |
| dst 被目录占用 | 文件与条目都保守保留 + 告警 |
| 多条目同 dst（多轮 kill） | 新代 staging 落位 → 其余各代文件按证据清理 → 全部条目剪枝 |

对**无日志证据**的同格式文件（升级前旧版本残留、外部创建、或恰好同名的普通书籍文件）：
**不改名、不删除、不跳过导出**；`AppLog.w` 逐文件报告（"已原样保留、导出照常打包，不自动改名或删除"）。任务要求"不能以碰撞概率低或不执行 delete 作为安全证明"——本方案的安全性不依赖概率：导出端无证据文件**必然打包**（不漏字节），导入端**必然不改名**，与"是否恰好同名"无关。

## 3. 判别测试与红绿证据

命令（在 `android/` 执行）：

```
gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.LocalZipBackupTest
```

| 轮 | 退出码 | XML（tests/failures/errors/skipped） | 说明 |
|---|---|---|---|
| 红（修复前，源码零改动） | 1 | 32 / **13** / 0 / 0 | 3 项改写（T1/T5/T6）+ 10 项新增全部按设计失败，19 项既有通过 |
| 绿（修复后） | 0 | 32 / 0 / 0 / 0 | 全部通过 |
| 确认（绿后源码未再改动，复跑） | 0 | 任务 `UP-TO-DATE` | Gradle 按输入哈希判定 up-to-date：当前源码哈希与绿测输入逐字节一致 |

原始日志与 XML：`E:\creation-reading-assistant\zcode-s14-residue-collision-20260928\`（`jvm-red-run.log`、`jvm-green-run.log`、`jvm-green-confirm.log`、`TEST-localzipbackup-red.xml`、`TEST-localzipbackup-green.xml`、`final-source-hashes.txt`）。红测 13 项失败消息（XML 摘录）与设计预期一一对应，无意外失败；其中 `superLongNanoDigitsDoNotCrashExportOrImport` 直接复现 `NumberFormatException: For input string: "1234567890123456789012345678901…"。

逐项判别点（新增 C1–C10 + 改写 T1/T5/T6）：

| # | 用例 | 判别点 | 红测 |
|---|---|---|---|
| C1 | `exportPacksOrdinaryFilesWithCompleteResidueFormatNames` | 普通文件完整命中 staging 格式 / backup 格式且同名 dst 存在 → 导出逐字节打包（旧码跳过=缺书） | ✗→✓ |
| C2 | `importKeepsOrdinaryFileMatchingBackupFormatWhenTargetMissing` | 普通文件命中 backup 格式且 dst 缺失 → 原名原字节保留、dst 不得被造出、AppLog 明确报告 | ✗→✓ |
| C3 | `superLongNanoDigitsDoNotCrashExportOrImport` | 40 位数字 → 导出与导入都不抛异常，文件照常打包/保留（旧码 NumberFormatException） | ✗→✓ |
| C4 | `unjournaledResidueFormatFilesAreKeptNotRenamedAndReported` | 升级前无日志残留（dst 缺失）→ 保留原名原字节 + 报告，绝不自动改名 | ✗→✓ |
| C5 | `journalListedSupersededBackupIsCleanedWhenTargetPresent` | 日志归属、dst 在、backup 在（kill 于 ④⑤ 间）→ 按证据补完 ⑤，条目剪枝，dst 不被波及 | ✗→✓ |
| C6 | `multipleJournalEntriesForSameTargetConvergeToNewestBytes` | 同 dst 两条目（多轮 kill）→ 收敛到最近完整新字节，旧代文件按证据清理 | ✗→✓ |
| C7 | `corruptJournalFallsBackToKeepEverything` | 残缺 JSON 日志 → 导入不失败、无证据不改名、文件保留 | ✗→✓ |
| C8 | `staleJournalEntryWithoutFilesIsPruned` | 条目引用文件全缺席 → 剪枝，无副作用 | ✗→✓ |
| C9 | `journalFileIsNeverPackedAndNeverImportedFromZip` | 日志不进备份链；外来 ZIP 不得注入/覆盖本地日志；无效条目被丢弃 | ✗→✓ |
| C10 | `exportResolvesJournalBackedMissingTargetBeforePacking` | 日志归属、dst 缺、staging 缺 → 导出前按 backup 兜底复位，备份链拿到书本体 | ✗→✓ |
| T1′ | `importRecoversMissingTargetFromLoneBackupResidue`（改写） | kill 于 ④⑤ 间的日志现场：staging（完整新字节，与已提交 DB 一致）优先落位；backup 按证据清理；不依赖 ZIP 内容 | ✗→✓ |
| T5′ | `reimportWithResidueConvergesAndConsumesBackupKeepsStaging`（改写） | 日志现场首轮导入完成"恢复+替换"，日志收敛为空；重导幂等、无新增残留 | ✗→✓ |
| T6′ | `exportFiltersOwnResidueKeepsOnlyCopyBackupAndLookalikes`（改写） | 导出过滤只认日志：归属 staging 不打包、dst 在的 backup 不打包、dst 缺失的 backup 先复位再打包；无证据/形近文件照常打包 | ✗→✓ |

**既有守卫全保持**（绿测通过，未改动）：T2（无日志的植入 backup 在 dst 存在时逐字节保留——"绝不盲删"现在对所有无证据文件成立）、T3（无日志 staging 保留不阻塞）、T4（五类形近命名零误伤）、W1–W4 与既有 16 项（四窗口划分、单文件 staging 保护、版本感知、失败重导收敛、W4 残留表征）。

## 4. 按任务要求的分行为说明

| 对象 | 导出（exportZip） | 导入（importZip） |
|---|---|---|
| **普通文件（含完整命中残留格式者）** | 无日志证据 → 一律逐字节打包，绝不跳过 | 绝不改名；若恰为"backup 格式 + dst 缺失"，保持原名并在 AppLog 报告 |
| **真实残留（日志在案）** | 导出前先恢复收敛：staging 落位/backup 复位 → dst 以正常书名打包；被取代文件不进备份链 | 完成被打断的换入 + ⑤ 补完 + 条目剪枝；rename 失败显式抛错保留现场 |
| **真实残留（无日志：升级前旧版本 / 日志写入失败后被杀）** | 照常打包（字节不丢；最坏只是备份链多一个残留名文件） | 保留原名原字节 + AppLog 逐文件报告"未能自动清理"；**不自动恢复 dst**（无证据即不动） |
| **目标缺失** | 日志在：先复位再打包；无日志：残留名文件原样打包 | 日志在：恢复为 dst；无日志：dst 保持缺失、文件保留、报告 |
| **多 backup（多轮 kill）** | 同导入收敛后正常打包 | 新代 staging 落位为 dst；旧代 backup/staging 按证据清理；全部条目剪枝 |
| **异常路径** | 导出前恢复失败 → runCatching 兜底：告警 + 现场保留 + 继续导出（备份不因残留失败） | 恢复 rename 失败 → 显式抛错保留现场；日志写入/剪枝失败 → 告警不阻塞；残缺日志 → 无证据兜底 |

## 5. 未覆盖边界（如实）

1. **恢复 rename 失败注入**：JVM 夹具无法让"日志归属文件的 renameTo"失败（无接缝），失败分支（显式抛错保留现场）为源码保障 + 代码审阅，未注入验证。既有 `fileCopyOperation` 接缝只覆盖复制失败。
2. **真实 kill -9 / 设备窗口**：与上轮相同，kill 注入、SQLite/WAL 重启恢复、`LocalZipBackupPersistentRestoreTest`（MuMu 6 项）仍 NOT RUN，交设备窗口。建议设备轮新增：kill 后（含日志）重启 → 导入收敛 + 导出无残留名的端到端对账。
3. **DB 已提交不变量的前提**：日志条目仅在 `copyBookFiles`（DB 提交后）写入；若未来代码在 DB 提交前调用 `copyWithStaging`，"staging 落位与 DB 一致"的前提失效（退化为 backup 兜底仍安全，但需重新审视）。当前调用图下成立。
4. **并发**：两个 import 并发写日志/换入不在设计范围（应用层导入本就串行）；导出与导入并发时，日志提供的是即时快照一致性，不承诺跨进程互斥。
5. **无日志残留的用户告知**：当前只有 AppLog 诊断缓冲（日志页可见）+ 导入结果不受影响；未在导入 UI 上新增"残留未清理"提示（A-1/A-3 属 UI 轮接缝，不在本轮所有权）。
6. **日志文件本身在 books 根可见**：`.cra-restore-journal.json` 对用户文件管理器可见、无 DB 行故书架不可见；未做隐藏属性（跨平台无统一手段），文件名前缀 `.` 在 Android 私有存储无实际暴露面。

## 6. 文件触碰清单与所有权

- 本轮写入：[`android/app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt`](../../../../android/app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt)（SHA-256 `e3b05355…`，mtime 2026-09-28 20:27:03）、[`android/app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt`](../../../../android/app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt)（SHA-256 `f5ec49d2…`，mtime 2026-09-28 20:25:44）、本报告。三文件均在独占所有权内。
- 未修改：JsonBridge / ProfileViewModel / ProfileRoute / SettingsStore / Room schema / Gradle 配置 / 任何其他窗口文件。**SEAM REQUEST：无。**
- 工件（红绿日志 + 两份 XML）：`E:\creation-reading-assistant\zcode-s14-residue-collision-20260928\`；仓库内仅测试 results 构建产物（gitignore 范围）。

## 7. 验收矩阵增量

| # | 场景 | 层级 | 状态 |
|---|---|---|---|
| 上轮 1–7 | 恢复/保全/导出过滤/形近/收敛/既有行为（按日志语义改写后） | JVM | **本轮 PASS**（32/32） |
| 本轮 11 | 严格同格式碰撞（导出不漏字节 / 导入不改名 + 报告） | JVM | **本轮 PASS**（C1/C2） |
| 本轮 12 | 超长数字不崩溃（导入+导出） | JVM | **本轮 PASS**（C3） |
| 本轮 13 | 日志自身被杀语义（残缺/陈旧/剪枝失败/伪造注入） | JVM | **本轮 PASS**（C7/C8/C9，剪枝失败为代码保障未注入） |
| 上轮 8–9 | 真实 kill 注入 / SQLite-WAL 组合态 | 设备 | **NOT RUN**，交设备窗口 |
| 上轮 10 | 整包跨 DB/文件原子性 | — | **设计上不承诺**（可收敛），不得标 PASS |

落盘即停，交总指挥复核。
