# N1-repair-v4 任务交付报告：提交前恢复计划、版本保护与重试收敛

---

## 1. 任务编号 / 版本

- **任务编号**：N1-repair-v4
- **任务目标**：修复 Codex 独立审查发现的 4 项根本缺陷（1P1/3P2），建立 JsonBridge DB 提交前计划窄接缝、修复空指纹解禁较新保护、打通 A→B→A 重试收敛、支持旧 v2 长度状态安全迁移、修复 copyWithStaging finally 分支删除失败时误剪枝归属日志，并通过定向 JVM 先红后绿验收。
- **执行角色**：智能体（Gemini 3.8 Flash，无 ADB / 无设备安装，只读共享仓库产品代码，独占 E 盘构建目录与缓存）。
- **执行时间**：2026-10-02 12:00 - 12:25 (UTC+8)

---

## 2. 输入指纹

- **活动仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **预期 HEAD**：`e39a78da9c62f02614207cffb36969a32492eaea`
- **产品基线输入**：`E:\creation-reading-assistant\next-round-n1-repair-v3\android`
- **输入基线核验（与 v3 交付及 Codex 独立审查 verification.json 逐字节一致）**：
  - `LocalZipBackup.kt`: `5b981e8f1e8d6239f78c7ff2968ef8d6055d40c4c99899a74b5cc67489bb4227`
  - `LocalZipRestoreStateStore.kt`: `f428e947e93223116af5922256129184175574d1a08bc8c946d8bff6eb23b8ce`
  - `ProfileRoute.kt`: `89a793ba258cd6cd4269adaf523cbfbaef027f7ff8a2844307ae09dc21482519`
  - `ProfileViewModel.kt`: `124517d2800853c80ea24c9af24bf945f7f988f003f8f3168b0cae1e6bb35231`
  - `LocalZipBackupTest.kt`: `f26bec0f0f60d8f0e5d3528547d728b4fb3d03bfd702b7c2e3591644b1275591`
  - `LocalZipRestoreStateStoreTest.kt`: `74782c0cbd8f3617e537a8c3845e8de3a4b576471462655a43cd9f6625047c58`
  - `JsonBridge.kt` (HEAD): `b9d8c3612eed44419aa2e9a9c7aa7d0e93589f4ae03f5d38e33ce3a308ce515a`
  - `JsonBridgeTest.kt` (HEAD): `d6548b12489424a079d3ccc1370c15fe885e4e9d4653795dbc0b9f0b8fb1c6e8`

---

## 3. 所有权与改动

本任务严格限制在授权的 8 个文件范围内，在独占候选目录 `E:\creation-reading-assistant\next-round-n1-repair-v4\android` 中修改：

### 生产代码改动（3 个文件）
1. `app/src/main/java/com/creationreadingassistant/feature/sync/JsonBridge.kt`:
   - 扩展 `ImportMergeResult`：增加 `strictlyNewerBookContentPaths`（本地 revision/updatedAt 严格更新路径）与 `equalBookContentPaths`（完全相等版本路径），保持原有 `skippedBookContentPaths` 向后兼容包含两者。
   - 建立事务内预提交挂钩接缝：将合并结果分类与 `onMergeResult` 回调移入 `bookDao.runInTransaction` 事务内部。在任何实体写入前触发计划回调，若调用方落盘失败上抛异常，Room 数据库事务整体回滚，实现完全的 fail-closed。
2. `app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt`:
   - **路径 1 修复**：在 `importZip` 中，解压并计算源文件 SHA-256 和备份指纹前移至 DB 阶段前；在 `jsonBridge.importFromString` 的事务内回调中构建计划并调用 `restoreStateStore.markDbCommitted`；严格较新（`strictlyNewer`）路径永远受保护，空指纹绝不得解除本地较新保护。
   - **路径 2 修复**：前序中断（如 A→B）留下的未完成目标通过 `inheritedObligations`（`unresolvedTargets + pendingTargets`）识别；再次导入原 A 时，源文件哈希与义务相匹配即可触发补偿写入；复制成功后转移并在完成时安全收敛清空状态。
   - **路径 4 修复**：在 `copyWithStaging` 的 `finally` 分支中，当 `verified=false` 且 `staging.exists()` 时，若文件删除返回 `false`（或异常），**严禁**调用 `journalRemove(booksDir, initialEntry)`，保留归属日志以守护残片。
3. `app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStore.kt`:
   - **路径 3 修复**：新增 `isLegacyLength` 与 `matchesTarget` 助手方法。在 `clear` 磁盘核验时，若期望值为纯数字（v2 legacy 格式），按文件长度验证就位，不将长度当 SHA-256 比较；保留旧义务不被静默清除，重试时安全支持升级。
4. `ProfileViewModel.kt` 与 `ProfileRoute.kt`:
   - 未改动（保持 v3 基线，SHA 与基线完全一致）。

### 测试代码改动（3 个文件）
5. `app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt`:
   - 新增路径 1 反例红测：`N1v4_P1_strictlyNewerProtectedAgainstEmptyFingerprintRetry`。
   - 新增路径 2 反例红测：`N1v4_P2_abaConvergence`。
   - 新增路径 4 反例红测：`N1v4_P2_copyWithStagingFinallyDeleteFailurePreservesJournal`。
6. `app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStoreTest.kt`:
   - 新增路径 3 反例红测：`N1v4_P2_legacyLengthFormatStateMigrationDoesNotTreatLengthAsSha`。
7. `app/src/test/java/com/creationreadingassistant/feature/sync/JsonBridgeTest.kt`:
   - 新增窄接缝分类断言测试：`importFromString distinguishes strictly newer from equal revision`。
   - 新增事务回滚 fail-closed 测试：`preCommitPlan failure aborts transaction and prevents upsert`。

---

## 4. 环境

- **操作系统**：Windows 11 (amd64)
- **JDK**：OpenJDK 17.0.14 (`D:\develop\Java\jdk-17.0.14`)
- **Gradle 用户目录**：`E:\creation-reading-assistant\next-round-n1-repair-v1\_cache\gradle-home`（完全离线复用）
- **独立候选目录**：`E:\creation-reading-assistant\next-round-n1-repair-v4\android`
- **构建输出与日志隔离**：所有日志、测试 XML、patch、中间脚本均置于 `E:\creation-reading-assistant\next-round-n1-repair-v4\`

---

## 5. 执行与结果（先红后绿）

### 5.1 红色阶段（Red Run）
在未应用生产代码修复的基线代码上执行 4 个失败路径测试，捕获预期失败证据：
- 执行命令：
  ```powershell
  $env:GRADLE_USER_HOME='E:\creation-reading-assistant\next-round-n1-repair-v1\_cache\gradle-home'
  .\gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.LocalZipBackupTest.N1v4_* --tests com.creationreadingassistant.feature.sync.LocalZipRestoreStateStoreTest.N1v4_* --info
  ```
- **红测结果**：**4 tests completed, 4 failed**
  1. `LocalZipBackupTest.N1v4_P1_strictlyNewerProtectedAgainstEmptyFingerprintRetry`：
     - 失败表现：`expected:<[LOCAL_STRICTLY_NEWER_CONTENT_V2]> but was:<[OLD_BACKUP_CONTENT_V1]>`（本地较新正文被空指纹解除保护并覆盖）
  2. `LocalZipBackupTest.N1v4_P2_abaConvergence`：
     - 失败表现：`java.lang.AssertionError: 回到 A 后 a/content.txt 必须被补齐写入`（A 目标无法收敛补齐）
  3. `LocalZipBackupTest.N1v4_P2_copyWithStagingFinallyDeleteFailurePreservesJournal`：
     - 失败表现：`java.lang.AssertionError: 删除失败时归属日志文件必须存在`（finally 误执行了 journalRemove）
  4. `LocalZipRestoreStateStoreTest.N1v4_P2_legacyLengthFormatStateMigrationDoesNotTreatLengthAsSha`：
     - 失败表现：`expected null, but was:<...pendingTargets={b1/content.txt=11}...>`（把长度 11 当 SHA，导致已就位文件无法识别，clear 拒绝删除）
- 原始日志归档：`E:\creation-reading-assistant\next-round-n1-repair-v4\red-logs\red-test.log` 与 `TEST-*.xml`。

### 5.2 绿色阶段（Green Run）
应用生产代码修复后，运行 3 个受影响测试类的全量定向测试套件：
- 执行命令：
  ```powershell
  $env:GRADLE_USER_HOME='E:\creation-reading-assistant\next-round-n1-repair-v1\_cache\gradle-home'
  .\gradlew.bat :app:testDebugUnitTest --tests com.creationreadingassistant.feature.sync.LocalZipBackupTest --tests com.creationreadingassistant.feature.sync.LocalZipRestoreStateStoreTest --tests com.creationreadingassistant.feature.sync.JsonBridgeTest --info
  ```
- **绿测结果**：**91 tests completed, 0 failed, 0 errors**（BUILD SUCCESSFUL）
  - `com.creationreadingassistant.feature.sync.JsonBridgeTest`：20/20 PASSED
  - `com.creationreadingassistant.feature.sync.LocalZipBackupTest`：56/56 PASSED
  - `com.creationreadingassistant.feature.sync.LocalZipRestoreStateStoreTest`：15/15 PASSED
- 原始日志归档：`E:\creation-reading-assistant\next-round-n1-repair-v4\green-logs\test-targeted-green.log` 与 `TEST-*.xml`。

---

## 6. 原始证据与哈希清单

### 6.1 授权文件最终 SHA-256 清单
```
3d20caa85118f27ba565cad6bde874e918da86e086b1a3d40fec9271ee430966  app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt
df5ec13ee2dd2b8d07b3e5eb6935c7a9c1a16070e04977ab20910f796f578af8  app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStore.kt
f8e455a452e0182d8bad50349b26e58dbdd0dd7a942e2ea09d3d8223570ece37  app/src/main/java/com/creationreadingassistant/feature/sync/JsonBridge.kt
124517d2800853c80ea24c9af24bf945f7f988f003f8f3168b0cae1e6bb35231  app/src/main/java/com/creationreadingassistant/ui/viewmodel/ProfileViewModel.kt
89a793ba258cd6cd4269adaf523cbfbaef027f7ff8a2844307ae09dc21482519  app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileRoute.kt
50b1d1d5a009894318109a1de3d56c056931ef4c6e76375066ec335957cbbaa8  app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipBackupTest.kt
e8df1abdec9b71999a9ca5d6ffd9251697ea82f7b965fc2a829e8e33005de4e4  app/src/test/java/com/creationreadingassistant/feature/sync/LocalZipRestoreStateStoreTest.kt
bcc27a4d0635071f96b05c341119293a8e076248469168782bd8795bb90c412b  app/src/test/java/com/creationreadingassistant/feature/sync/JsonBridgeTest.kt
```

### 6.2 产物归档位置
- **相对 v3 增量补丁**：`E:\creation-reading-assistant\next-round-n1-repair-v4\patch\n1-repair-v4.patch` (34,871 bytes)
- **红测日志与 XML**：`E:\creation-reading-assistant\next-round-n1-repair-v4\red-logs\`
- **绿测日志与 XML**：`E:\creation-reading-assistant\next-round-n1-repair-v4\green-logs\`

---

## 7. 未覆盖项

1. **真实操作系统强制终止（SIGKILL / 掉电中断）**：本轮验证均基于 JVM 级受控故障注入（IOException、delete 返回 false、预置状态 fixture），操作系统级进程突发销毁时的文件系统事务持久性由底层 SQLite WAL 与原子重命名契约保证，未在物理真机或模拟器上实施硬件级断电验证。
2. **全套 UI / 仪器测试**：遵循任务约束，本任务独占原生 Android 构建目录，仅执行定向 JVM 门禁验证，不包含 Compose UI 仪器化测试与端到端模拟器渲染。

---

## 8. 恢复与副作用

1. **零共享代码污染**：恢复仓库 `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919` 中产品源码分毫未动；禁止且未执行任何 `git commit` / `push` / `stage` / `reset` / `checkout`。
2. **零设备状态影响**：未连接 ADB、未启动 MuMu 模拟器、未安装任何宿主或测试 APK。
3. **隔离目录独立性**：所有临时脚本与构建缓存均严格约束在 `E:\creation-reading-assistant\next-round-n1-repair-v4\`。

---

## 9. 交付状态与下一步

- **交付状态**：`READY_FOR_CODEX_REVIEW`（N1-repair-v4 已闭环，四项缺陷全部先红后绿验证通过，产物与证据齐备）。
- **下一步**：移交 Codex 独立审查员核验增量补丁 `n1-repair-v4.patch`、红绿证据与 SHA 清单，裁决是否合流或集成。
