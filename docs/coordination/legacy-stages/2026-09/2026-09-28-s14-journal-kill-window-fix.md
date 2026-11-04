# S14 归属日志 kill 窗口 + 写入 fail-closed · 判别测试与最小修复报告（2026-09-28）

> **Codex 2026-09-28 独立复核：代码/JVM 范围限定通过，设备安全未验。** E 盘红/绿 XML 实读为 34/2→34/0，当前 `LocalZipBackup.kt` SHA-256 `AB78A16E8E34C50A59ADA31040278569BAAA013F2C1EBA6054A675B89997514C` 与报告一致。源码已为“旧 dst + 完整 staging + 日志在”增加补完换入分支，`journalAdd` 失败会在第一次破坏性 rename 前抛错；W5/W6 用例对应这两项。普通 `writeJournal` 仍用于剪枝，且新 `writeJournalDurably` 直接覆盖已有日志，不能把所有断电/并发场景概括为已证明的崩溃持久性；本轮仅证明注入状态与 JVM 语义。报告所说“按 AGENTS.md 不使用 MuMu”是旧限制，用户已明确授权 MuMu qahost 隔离验收。下一步返修输入的新 APK/测试 APK 与 MuMu 持久库 6 项、受控残留状态、DB/文件前后对账；实体手机和整包原子性仍未验。

> 执行角色：ZCode (GLM-5.2)
> 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`；分支 `codex/recover-worktree-20260920`
> 输入复核：[`2026-09-28-s14-residue-collision-fix.md`](2026-09-28-s14-residue-collision-fix.md) 顶部 Codex「安全结论暂不通过」注记——指出两个未测 kill 点：(1) `journalAdd` 后 `dst.renameTo(backup)` 前，dst 仍是旧字节、完整 staging 已存在，`recoverCrashResidues` 仅在 `!dst.exists()` 时落位 staging，随后 `dst.isFile` 分支删除 staging 并剪枝日志，留下与已提交 DB 不一致的旧正文；(2) `writeJournal` 吞掉写入失败且 `journalAdd` 继续执行破坏性 rename，不能声称日志写入成功先于每次 rename。
> 本轮性质：**判别测试（先红）+ 最小安全修复（后绿）+ 报告**。未运行 ADB/MuMu，未触碰真实手机、正式书库、qahost 数据；未 commit/push/reset/checkout；未修改所有权外任何文件。

## 0. 结论先行

1. Codex 复核指出的两个缺口全部以**先红后绿**的判别用例证实并修复：
   - **W5（kill 于 journalAdd 后、③ dst→backup 前）**：dst 仍为旧字节、staging 为已校验新字节、backup 未产生。旧 `recoverCrashResidues` 不处理此态（dst 存在 → 走 ⑤ 清理 staging + 剪枝日志 → 旧正文停在 dst，与已提交 DB 不一致）。修复：新增 `completeInterruptedSwapIn`，把旧字节移入 backup、staging 落位为 dst（与 `copyWithStaging` 的 ③④ 等价），随后 ⑤ 清理 backup。
   - **W6（归属日志写入失败）**：旧 `journalAdd` 用 `runCatching` 吞掉写入失败继续执行破坏性 rename，制造「无归属证据」的换入中间态。修复：`writeJournalDurably`（写 + flush + fsync）+ `journalAdd` fail-closed（IOException 包装为 IllegalStateException 上抛，旧目标原样保留）。

2. 红测 `34 tests / 2 failures / EXIT=1`（W5 + W6 各 1 项，源码 SHA = 基线 `e3b05355`，逐字节等于上一轮绿测源码）→ 绿测 `34 tests / 0 failures / 0 errors / 0 skipped / EXIT=0`（32 项既有全保持 + 2 项新增）。同步包全量 `feature.sync.*` 亦全绿。

3. 修复范围 = `LocalZipBackup.kt`（+1 import、+2 private method、改写 `journalAdd`、`recoverCrashResidues` 加 1 分支、KDoc/注释更新）+ `LocalZipBackupTest.kt`（+2 判别测试）。未修改 JsonBridge / ProfileViewModel / ProfileRoute / SettingsStore / 书架文件 / schema / Gradle。

## 1. 缺口分析与判别测试设计

### 1.1 保护性换入的 rename 序列与 kill 窗口

```
copyWithStaging（保护性换入路径，POSIX 直接 rename 成功时不进入）：
① staging 写入 + 长度校验（完整可信新字节）
② staging.renameTo(dst) 失败且 dst 存在
③ journalAdd({rel, backup, staging})   ← 任何破坏性 rename 之前
④ dst.renameTo(backup)                  失败 → journalRemove + throw（dst 原样，无残留）
⑤ staging.renameTo(dst)                 失败 → backup 自动复位 + throw；或保留条目 + throw
⑥ backup.delete()                       失败 → 保留条目 + throw（恢复端补完 ⑤）
⑦ journalRemove（换入完成，条目剪枝）
```

上一轮报告 §1.2 声称「③ 后、④ 前：dst 存在 → staging 按证据落位为 dst」，但源码 `recoverCrashResidues` 的分支结构是 `if (!dst.exists() && stagingFile.isFile) { … }`——dst 存在时**不落位 staging**，而是直接走到 `if (dst.isFile) { ⑤ 清理 }`，把 staging 删掉、条目剪枝。Codex 复核正确指出此为源码事实与报告声明不符。

### 1.2 W5 判别测试：`killedAfterJournalBeforeFirstRenameConvergesToStagingBytes`

构造 kill 于 ③ 后 ④ 前的现场：
- dst = 旧字节 `OLD-MUST-BE-REPLACED-BY-STAGING`
- staging = 新字节 `STAGING-NEW-CONTENT`（已校验完整）
- backup 不存在
- 日志条目在（`{rel, backup_name, staging_name}`）

断言恢复后：
- dst = `STAGING-NEW-CONTENT`（staging 落位，与已提交 DB 一致）
- 无 staging/backup 残留
- 日志收敛为空

旧码结果：dst = 旧字节（staging 被删、条目被剪枝）→ 断言失败（红）。

### 1.3 W6 判别测试：`journalWriteFailureAbortsSwapInAndKeepsOldTargetIntact`

用「同名目录占用日志路径」制造确定性写失败（`FileOutputStream` 对目录必失败，跨平台一致）：
- dst = 旧字节 `OLD-MUST-SURVIVE-JOURNAL-WRITE-FAILURE`
- 日志路径被同名目录占用 → `writeJournalDurably` 必抛 `IOException`

断言：
- `result.isFailure`（换入被中止）
- `failure is IllegalStateException && failure.cause is IOException`（主异常类型 + cause 保留）
- dst 逐字节保留旧字节
- 无 staging/backup 残留

旧码结果：`journalAdd` 吞掉写入失败 → 继续执行 ④ ⑤ → dst 被换成 ZIP 新字节、无归属证据 → 断言失败（红）。

## 2. 最小安全修复

### 2.1 `writeJournalDurably`（新增 private method）

`writeJournal` 的持久化版本：`FileOutputStream.use { write + flush + fd.sync() }`，确保日志在破坏性 rename 前落盘。失败上抛（不吞异常，不写半文件）。

### 2.2 `journalAdd` fail-closed（改写）

```
旧：runCatching { writeJournal(…) }.onFailure { AppLog.w(…) }   // 吞掉，继续换入
新：try { writeJournalDurably(…) } catch (e: IOException) {
        throw IllegalStateException("归属日志持久化失败（fail-closed）", e)
    }                                                          // 上抛，中止换入
```

IOException 包装为 IllegalStateException，与 `copyWithStaging` 的异常约定一致（所有错误路径均 IllegalStateException），保留原始 cause 供诊断。

### 2.3 `recoverCrashResidues` W5 分支（新增 1 个 else-if）

```
} else if (dst.isFile && stagingFile.isFile && !backupFile.exists()) {
    // kill 于 journalAdd 后、③ 前：补完换入
    completeInterruptedSwapIn(dst, backupFile, stagingFile, rel)
}
```

`completeInterruptedSwapIn`（新增 private method）：`dst→backup` + `staging→dst`，与 `copyWithStaging` 的 ③④ 等价。staging 落位失败时把 backup 复位为 dst（回到 kill 前现场），不丢字节。

补完后 dst=新字节、backup=旧字节、staging 已不在 → 走到下方 `if (dst.isFile) { ⑤ }` 清理 backup + 剪枝日志，与正常成功路径收敛一致。

### 2.4 KDoc / 注释更新

- 类级 KDoc：补充「持久化写入」「fail-closed」「completeInterruptedSwapIn」描述。
- `recoverCrashResidues` KDoc：补充「dst 仍在（旧字节）且 staging 在、backup 未产生时同样补完换入」。
- `copyWithStaging` 注释：补充「writeJournalDurably：写 + flush + fsync」「fail-closed」。

## 3. 证据

### 3.1 红测（修复前，源码 = 基线）

```
源码 SHA-256：E3B0535507D089BF92802D472B69597839021A55CD4945A300A0CA410F5D46A3
              （逐字节等于上一轮绿测基线）
测试 SHA-256：174CFBF6CBD3FF35E182EFC4B956FF46D72B4DED20AE7CC910BFB01CC29A690A
结果：34 tests, 2 failures, 0 errors, 0 skipped, EXIT=1
失败项：
  - killedAfterJournalBeforeFirstRenameConvergesToStagingBytes (W5)
  - journalWriteFailureAbortsSwapInAndKeepsOldTargetIntact (W6)
```

### 3.2 绿测（修复后）

```
源码 SHA-256：AB78A16E8E34C50A59ADA31040278569BAAA013F2C1EBA6054A675B89997514C
测试 SHA-256：174CFBF6CBD3FF35E182EFC4B956FF46D72B4DED20AE7CC910BFB01CC29A690A
              （测试未变，仅源码变）
结果：34 tests, 0 failures, 0 errors, 0 skipped, time=7.558s, EXIT=0
同步包全量 feature.sync.*：BUILD SUCCESSFUL, EXIT=0
```

### 3.3 异常站点核对（W6）

| 层 | 旧码 | 新码 |
|---|---|---|
| `writeJournal` | `runCatching { writeText }` + `AppLog.w`（吞） | 不参与 `journalAdd` 路径 |
| `writeJournalDurably` | — | `FileOutputStream.use { write+flush+sync }`，IOException 上抛 |
| `journalAdd` | `runCatching { writeJournal }` + `AppLog.w`（吞） | `try { writeJournalDurably } catch(IOException) → IllegalStateException(cause=e)` |
| `copyWithStaging` | 吞掉后继续 ④ ⑤ | IllegalStateException 上抛，③ 后的 ④ ⑤ 不执行 |
| 测试断言 | dst 被换成 ZIP 字节、无证据 | `IllegalStateException` + `cause=IOException`，dst 逐字节保留 |

### 3.4 归档

`E:\creation-reading-assistant\zcode-s14-journal-killwindow-20260928\`：
- `TEST-localzipbackup-red.xml` / `TEST-localzipbackup-green.xml` — JUnit XML
- `jvm-red-run.log` / `jvm-green-run.log` — 控制台日志
- `LocalZipBackup.fixed.kt`（SHA `ab78a16e`）/ `LocalZipBackupTest.final.kt`（SHA `174cfbf6`）— 源码快照

## 4. kill 窗口 × 恢复行为（修正后）

| kill 点 | 日志状态 | 文件现场 | 恢复行为 | 数据结局 |
|---|---|---|---|---|
| ③ 写日志中途 | JSON 拋缺 | dst=旧、staging=新、无 backup | 解析失败 → 无证据兜底（保留+报告） | 无损失（尚无破坏性 rename） |
| **③ 后 ④ 前**（W5） | 条目在 | **dst=旧、staging=新、无 backup** | **`completeInterruptedSwapIn`：dst→backup、staging→dst → ⑤ 清理 backup** | dst=新，与 DB 一致 |
| ④ 后 ⑤ 前 | 条目在 | dst 缺、backup=旧、staging=新 | staging 落位为 dst → ⑤ 清理 backup | 无损失 |
| ⑤ 后 ⑥ 前 | 条目在 | dst=新、backup=旧 | ⑤ 清理 backup + 剪枝 | 无损失 |
| ⑥ 后 ⑦ 前 | 条目在 | 文件已不在 | 陈旧条目 → 剪枝 | 无损失 |
| ⑦ 剪枝重写中途 | JSON 拋缺 | — | 无证据兜底 | 无损失 |
| **日志写入失败**（W6） | 无条目 | dst=旧（未触碰） | **fail-closed：上抛 IllegalStateException，④ ⑤ 不执行** | 无损失（旧目标原样保留） |

## 5. 未验证项（NOT RUN）

- 设备 kill（adb / 真实进程被杀）：本轮为 JVM 单元测试，未在真实设备上验证 kill 恢复。
- SQLite / WAL：未涉及。
- MuMu 模拟器：按 AGENTS.md 约束不使用。
- 全量 `:app:testDebugUnitTest`：仅运行 `feature.sync.*` 定向测试（并行实现阶段约束）。

## 6. 变更清单

| 文件 | 变更 |
|---|---|
| `android/app/src/main/java/.../LocalZipBackup.kt` | +`import FileOutputStream`；+`writeJournalDurably`；改写 `journalAdd`（fail-closed）；`recoverCrashResidues` +1 分支（W5）；+`completeInterruptedSwapIn`；KDoc/注释更新 |
| `android/app/src/test/java/.../LocalZipBackupTest.kt` | +`killedAfterJournalBeforeFirstRenameConvergesToStagingBytes`（W5）；+`journalWriteFailureAbortsSwapInAndKeepsOldTargetIntact`（W6） |
| `docs/coordination/legacy-stages/2026-09/2026-09-28-s14-journal-kill-window-fix.md` | 本报告 |
