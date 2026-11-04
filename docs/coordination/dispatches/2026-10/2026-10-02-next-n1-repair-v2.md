# N1-repair-v2：整包恢复重试完整性、并发互斥与 staging 校验加固报告

## 一、任务编号/版本

- **任务编号/版本**：`N1-repair-v2`
- **任务目标**：修复 Codex 独立审查发现的 5 项需求漏洞（同一 ZIP 重试、不同 ZIP 覆盖、并发互斥、半写 staging 归属、清理与异常）及附带文案修正，完成独立 E 候选更新与定向 JVM 验证。
- **所有权承诺**：
  - 严格独占候选目录：`E:\creation-reading-assistant\next-round-n1-repair-v2\android`；
  - 严格保持共享仓库 D 盘产品源码只读，未修改任何非授权文件，未做 git commit/push/reset/checkout；
  - 严格不操作设备（零 ADB、零 MuMu、零安装）；
- **交付状态**：**已交付待 Codex 核查**（严格保持中立，不自行宣称关闭安全缺陷）。

---

## 二、输入基线与指纹核对

| 项 | 值 / 状态 |
|---|---|
| 活动仓库 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` |
| 预期 HEAD | `e39a78da9c62f02614207cffb36969a32492eaea` |
| 当前 HEAD | `e39a78da9c62f02614207cffb36969a32492eaea`（实测一致，暂存区干净） |
| 源码输入源 | `E:\creation-reading-assistant\next-round-n1-repair-v1\android` |
| 父候选基线 | `E:\creation-reading-assistant\next-round-joint-j1-v3\android` |
| 独立执行凭据 | `E:\creation-reading-assistant\next-round-n1-repair-v2\execution.json`（已落盘置为 `COMPLETED`） |

### 2.1 初始 6 文件基线哈希核实（100% 吻合 `verification.json`）

| 文件相对路径 | v1 预期 SHA-256 | v2 初始实测 SHA-256 | 核验结果 |
|---|---|---|---|
| `app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt` | `63e033ca66b3060960d25b4893d47805f2706495863b4f8ab58c06b9408d0805` | `63e033ca66b3060960d25b4893d47805f2706495863b4f8ab58c06b9408d0805` | MATCH |
| `app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStore.kt` | `47a763cb5742914312199528eb7b103cb302d69929582910b58c04b0b29e5888` | `47a763cb5742914312199528eb7b103cb302d69929582910b58c04b0b29e5888` | MATCH |
| `app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileRoute.kt` | `31e0cd0d4b82ecaf04b91f37da8752a7282fc47e2a07e21cf6d28db5067d4312` | `31e0cd0d4b82ecaf04b91f37da8752a7282fc47e2a07e21cf6d28db5067d4312` | MATCH |
| `app/src/main/java/com/creationreadingassistant/ui/viewmodel/ProfileViewModel.kt` | `124517d2800853c80ea24c9af24bf945f7f988f003f8f3168b0cae1e6bb35231` | `124517d2800853c80ea24c9af24bf945f7f988f003f8f3168b0cae1e6bb35231` | MATCH |
| `app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt` | `5d1b7439fb54d55bcedda2afe90130918a63595eb05167196805eb135d80baba` | `5d1b7439fb54d55bcedda2afe90130918a63595eb05167196805eb135d80baba` | MATCH |
| `app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStoreTest.kt` | `4df47765dd9e3d7cf4eaab0edab323e3bb47a1fae3683596af288c4cbdef3cd5` | `4df47765dd9e3d7cf4eaab0edab323e3bb47a1fae3683596af288c4cbdef3cd5` | MATCH |

---

## 三、所有权与候选文件精确对账

### 3.1 授权文件修改清单及最终 SHA-256

| # | 相对路径 | 变更类型 | v1 SHA-256 | v2 最终 SHA-256 |
|---|---|---|---|---|
| 1 | `app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt` | 修改 | `63e033ca66b3060960d25b4893d47805f2706495863b4f8ab58c06b9408d0805` | `2365b1ce23c60b265301a9df0d9a6f47772971bc85f6134e4e23e6faebcec100` |
| 2 | `app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStore.kt` | 修改 | `47a763cb5742914312199528eb7b103cb302d69929582910b58c04b0b29e5888` | `d5b3863dc2826d115512a95ccc63592874e2e26af6ddb1a8f47a1cd469567915` |
| 3 | `app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileRoute.kt` | 修改 | `31e0cd0d4b82ecaf04b91f37da8752a7282fc47e2a07e21cf6d28db5067d4312` | `89a793ba258cd6cd4269adaf523cbfbaef027f7ff8a2844307ae09dc21482519` |
| 4 | `app/src/main/java/com/creationreadingassistant/ui/viewmodel/ProfileViewModel.kt` | 保持 | `124517d2800853c80ea24c9af24bf945f7f988f003f8f3168b0cae1e6bb35231` | `124517d2800853c80ea24c9af24bf945f7f988f003f8f3168b0cae1e6bb35231` |
| 5 | `app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt` | 修改 | `5d1b7439fb54d55bcedda2afe90130918a63595eb05167196805eb135d80baba` | `39e0a38cc0a2927253c25a7f4193b63d929d0ff56d362f25436d43735e39696e` |
| 6 | `app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStoreTest.kt` | 修改 | `4df47765dd9e3d7cf4eaab0edab323e3bb47a1fae3683596af288c4cbdef3cd5` | `76b0264f7a6eac504b564eb2cb1f9084670af9a264b88be739b2e1b145d2a8dd` |

### 3.2 全树逐文件无越界比对

- **比对基准**：`E:\creation-reading-assistant\next-round-n1-repair-v1\android` vs `E:\creation-reading-assistant\next-round-n1-repair-v2\android`（排除 `build/`、`.gradle/`）
- **文件总数**：878
- **一致文件数**：873（包含无需变更的 `ProfileViewModel.kt` 保持完全不变）
- **允许修改文件数**：5（上表第 1、2、3、5、6 项）
- **非预期差异**：**0**（无多余文件、无误删文件、无非授权改动）

### 3.3 补丁归档与应用核验

1. **从 v1 到 v2 增量补丁**：
   - 路径：`E:\creation-reading-assistant\next-round-n1-repair-v2\patch\n1-repair-v2-from-v1.patch`
   - SHA-256：`e2fc3ed332649091de1556cc6765643714bbde1f025bbd7fbe48571d4cb6487e`
   - 应用测试：`git -C E:\...\next-round-n1-repair-v1\android -c core.autocrlf=false apply --check` **通过（0 错误）**。
2. **从 J1-v3 累积补丁**：
   - 路径：`E:\creation-reading-assistant\next-round-n1-repair-v2\patch\n1-repair-v2.patch`
   - SHA-256：`12db1465574983f9e106473d1a3d76acb7afea2672ff4f6771f56d4bbce7633d`
   - 应用测试：`git -C E:\...\next-round-joint-j1-v3\android -c core.autocrlf=false apply --check` **通过（0 错误）**。

---

## 四、构建环境

- **操作系统**：Windows 11
- **Gradle**：`8.9`（通过 `gradlew.bat`，`--offline --no-daemon` 运行）
- **Gradle 缓存**：`GRADLE_USER_HOME=E:\creation-reading-assistant\next-round-n1-repair-v1\_cache\gradle-home`（自有离线缓存，无跨会话污染）
- **JDK**：OpenJDK 17 / Java HotSpot 64-Bit Server VM
- **设备操作**：**零 ADB / 零 MuMu / 零设备连接**

---

## 五、定向 JVM 验证结果

- **执行命令**：
  ```cmd
  cmd /c "set GRADLE_USER_HOME=E:\creation-reading-assistant\next-round-n1-repair-v1\_cache\gradle-home&& gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.LocalZipBackupTest --tests com.creationreadingassistant.feature.sync.LocalZipRestoreStateStoreTest --rerun --offline --no-daemon > E:\creation-reading-assistant\next-round-n1-repair-v2\logs\test-targeted-final.log 2>&1"
  ```
- **构建结果**：`BUILD SUCCESSFUL in 37s`（退出码 0）
- **测试套件明细（实算 XML 证据）**：

| 测试套件 | 测试数 | 失败数 | 错误数 | 跳过数 | XML SHA-256 |
|---|---|---|---|---|---|
| `LocalZipBackupTest` | 47 | 0 | 0 | 0 | `6da78f9b2b70f38ae8924ca910f67836e8277cd61cdbe15fbffcdfc999c4dcb1` |
| `LocalZipRestoreStateStoreTest` | 14 | 0 | 0 | 0 | `b78d1856100319c1cc0035c6da6808e5d6cd75360c38bd7b17fab4784e744bab` |
| **总计** | **61** | **0** | **0** | **0** | **全部通过** |

- **对比 v1 变化**：
  - `LocalZipBackupTest`：从 41 个增加到 47 个（**新增 6 个针对漏洞的专项验证用例**）；
  - `LocalZipRestoreStateStoreTest`：从 10 个增加到 14 个（**新增 4 个针对状态守护与原子互斥的验证用例**）；
  - 累计新增 10 个测试用例，覆盖全部 5 个漏洞修复。

---

## 六、Codex 独立审查 5 项漏洞逐项回应与修复机制

### 6.1 漏洞 1（P1）：同一 ZIP 重试跳过与虚假完成

- **漏洞表现**：若恢复在 DB 提交后被杀，且部分书籍文件尚未写入。用户再次选择同一 ZIP 导入重试时，由于 DB 中的版本号已经与备份相同，版本比对逻辑错误地判定“本地文件已是最新/相同版本”而将缺失的目标加入 `skippedBookContentPaths` 跳过复制，随后 `importZip` 判定复制无报错，草率将状态置为 `files_copied` 并清除中断状态，导致缺失书籍彻底丢失且无法再次感知。
- **机制修复**：
  1. `LocalZipRestoreStateStore` 在 `markDbCommitted` 时持久化记录 `backupFingerprint`（`backup.json` SHA-256）与待复制目标清单 `pendingTargets`（相对路径与预期长度）。
  2. `LocalZipBackup.importZip` 区分“本地真正较新”与“上次已提交相等版本”：当检测到同一备份重试时，检查 `pendingTargets` 中磁盘尚未就位（不存在或长度不符）的目标，从 `skippedBookContentPaths` 中强制剔除，强制补齐 staging 复制与落位。
  3. `LocalZipRestoreStateStore.clear` 引入严格的物理核验守卫：只有当待复制书籍文件全部在 `realBooksDir` 真实存在且长度一致，且传入正确的 `operationId` 时，才允许清除状态；否则拒绝清除并保留状态待修。
- **测试覆盖**：
  - `LocalZipBackupTest.retrySameZipAfterKillRepairsMissingBookFilesAndClearsState`
  - `LocalZipRestoreStateStoreTest.clearRefusesToClearIfPendingTargetsMissingOnDisk`

### 6.2 漏洞 2（P1）：不同 ZIP 覆盖未完成状态与解禁风险

- **漏洞表现**：若用户在 A 备份恢复中断（状态未完成）后，误选择或故意选择 B 备份进行导入，原实现直接用 B 的状态覆盖 A 的状态，导致 A 备份中未完成修复的书籍目标保护彻底失效。
- **机制修复**：
  1. `LocalZipRestoreStateStore` 增加 `unresolvedTargets` 守护映射。
  2. 当 `begin` 被调用且检测到已有未完成状态、且指纹不同时，旧状态中未完成修复的目标（`pendingTargets` ∪ `unresolvedTargets`）自动继承并合并入新状态的 `unresolvedTargets`。
  3. 即使新备份成功完成并尝试 clear，只要 `unresolvedTargets` 中仍有历史缺失书籍未被核实修复，状态依然保持锁定保护，防止混合状态导出。
- **测试覆盖**：
  - `LocalZipBackupTest.importDifferentZipPreservesUnresolvedTargetsProtection`
  - `LocalZipRestoreStateStoreTest.beginInheritsUnresolvedTargetsWhenSwitchingBackup`

### 6.3 漏洞 3（P1）：并发互斥边界与状态竞争

- **漏洞表现**：原状态存储缺少进程内同步锁，服务层也缺少操作互斥锁，多协程或并发请求可能导致 `begin`/`markDbCommitted`/`clear` 产生并发写冲突，导出与导入亦无全生命周期互斥。
- **机制修复**：
  1. `LocalZipBackup` 服务层引入进程级协程互斥边界 `private val zipOperationMutex = Mutex()`，`exportZip` 与 `importZip` 全生命周期加锁互斥，杜绝导出与导入同时穿透。
  2. `LocalZipRestoreStateStore` 引入进程内原子锁 `private val lock = Any()`，对 `begin`、`markDbCommitted`、`clear`、`recordFailureSummary` 以及持久文件读写执行 `synchronized(lock)` 保护。
- **测试覆盖**：
  - `LocalZipBackupTest.concurrentExportAndImportAreMutexBlocked`
  - `LocalZipRestoreStateStoreTest.stateStoreHasInProcessLocking`

### 6.4 漏洞 4（P2）：半写 staging 与日志归属换入漏洞

- **漏洞表现**：旧 journal 设计在 `copyWithStaging` 创建 staging 前后未区分“半写中”与“已完成长度校验”，若进程恰好死在文件复制中途，遗留的 staging 文件可能只有一半大小；`recoverCrashResidues` 在重启恢复时可能误把该损坏的 staging 换入正式目标 `dst`，造成正文损坏。
- **机制修复**：
  1. `RestoreJournalEntry` 结构扩充 `verified: Boolean = false` 字段。
  2. 在 `copyWithStaging` 开始向 staging 写入前，先以 `verified = false` 持久化 journal（fail-closed：即使中断也能被识别为残缺）。
  3. 只有当 staging 复制完成且通过精确长度校验后，才调用 `journalUpdate` 将其持久化为 `verified = true`。
  4. `recoverCrashResidues` 在重启扫描时，对 `!entry.verified` 的条目绝不执行换入，而是立即物理删除残缺 staging 文件，并从 journal 中剪枝，防止损坏正文污染目标。
- **测试覆盖**：
  - `LocalZipBackupTest.unverifiedStagingInJournalIsDeletedAndNeverSwappedIn`
  - `LocalZipBackupTest.journalPreservesUnrecognizedElements`

### 6.5 漏洞 5（P2）：清理布尔值忽略与主异常覆盖

- **漏洞表现**：`finally` 块中调用 `deleteRecursively()` 忽略了返回的 `Boolean` 失败标识；此外，若清理过程抛出异常，可能直接覆盖原有的业务主异常，掩盖真正的中断根因。
- **机制修复**：
  1. `deleteRecursivelyOperation` 支持显式返回状态与接缝注入。
  2. 在 `finally` 块中，首先调用 `restoreStateStore.recordFailureSummary` 将主异常与现场错误持久化落盘。
  3. 检查清理布尔值与异常：若清理失败或抛出异常，当存在业务主异常时，将其作为 `addSuppressed` 挂接到主异常上上抛；当无主异常但清理返回 false 时，显式抛出 `IllegalStateException` 阻止孤儿解压缓存静默遗留。
- **测试覆盖**：
  - `LocalZipBackupTest.cleanupFailurePreservesPrimaryExceptionAsSuppressed`
  - `LocalZipBackupTest.cleanupReturningFalseThrowsWhenNoPrimaryException`

### 6.6 附带文案修正

- **说明**：Codex 审查指出 `STAGE_STARTED` 说明“DB 事务尚未提交（或未知）”表意不够准确，因进程可能在任何早期阶段中断。
- **修改**：在 `ProfileRoute.kt` 与 `LocalZipRestoreStateStore.kt` 中统一将 `STAGE_STARTED` 说明修改为：**「已开始，恢复未完成（数据库状态未知）」**。

---

## 七、自产工件清单

所有自产工件均存放于独立候选目录 `E:\creation-reading-assistant\next-round-n1-repair-v2\` 下：

```text
E:\creation-reading-assistant\next-round-n1-repair-v2\
├── execution.json                                              # 任务执行凭据（状态 COMPLETED）
├── compare.ps1                                                 # 6 文件哈希比对脚本
├── verify_tree.ps1                                             # 工作树全文件一致性核验脚本
├── generate_patches.sh                                         # 标准补丁生成脚本
├── logs\
│   └── test-targeted-final.log                                 # 定向单元测试最终 rerun 完整日志
├── patch\
│   ├── n1-repair-v2-from-v1.patch                              # v1 -> v2 增量补丁（SHA-256: e2fc3ed3...）
│   └── n1-repair-v2.patch                                      # J1-v3 -> v2 累积补丁（SHA-256: 12db1465...）
└── results\
    ├── TEST-com.creationreadingassistant.feature.sync.LocalZipBackupTest.xml            # 47 测试通过 XML
    └── TEST-com.creationreadingassistant.feature.sync.LocalZipRestoreStateStoreTest.xml # 14 测试通过 XML
```

---

## 八、交付与交接结论

1. **改动封闭**：本次仅在 `next-round-n1-repair-v2\android` 隔离工作树中完成，涉及的 5 个代码文件均经全生命周期定向验证，其余 873 个工程文件逐字节一致，未对共享工程产生副作用。
2. **规范履行**：严格遵守无设备操作、无仓库提交、无主观关闭缺陷的原则。
3. **交付状态**：**已交付待 Codex 核查**。已为 Codex 准备好完整的补丁文件、XML 测试证据、执行日志与状态清单，随时可供独立复核。
