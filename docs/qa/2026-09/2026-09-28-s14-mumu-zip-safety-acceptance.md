# S14 MuMu 本地 ZIP 安全修复设备层验收（2026-09-28）

> **Codex 回读：限定接受。** 两份 E 盘原始 instrumentation 均 `OK (6 tests)` / `INSTRUMENTATION_CODE: -1` / ADB_EXIT=0；宿主/测试 APK 文件哈希与报告相同，qahost 原库 DB/WAL/SHM 三件前/后 SHA 逐项一致。测试夹具覆盖旧文件中途写失败、目录目标拒绝、较新本地书行/文件双轴保护。qahost 既有 `files/books` 仅做名称/数量快照，未逐文件哈希；因此接受的是隔离测试的 6 项结果与原库 DB 三件零变化，不宣称既有所有书源文件逐字节不变、正式书库或实体手机通过，也不宣称整包 DB/文件原子。

## 结论（先说结果）

`LocalZipBackupPersistentRestoreTest` 当前 **6 项在 MuMu `emulator-5556` 的 qahost 隔离持久库上通过**，`OK (6 tests)`、ADB 退出码 0，逐项状态码均为 0。本报告的结论**只适用于 MuMu 隔离范围**，不扩展到真实手机、正式书库或真实 WebDAV。

- 结果性质：**MuMu 隔离范围通过**。
- 本轮**未修改任何产品源码**；构建与设备运行只用于补设备层证据。
- 保留边界：DB 先提交、文件后复制，**整包非原子**（见 §5）。

## 范围与源码对应

| 项 | 值 |
|---|---|
| 仓库 | `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920` |
| 设备 | `emulator-5556`（`adb devices` 唯一 serial）；`x86_64`、Android 12、`SM-S9280`/`Samsung/e3q/e3q:12/V417IR/189:user/release-keys` |
| 包 | 仅 `com.creationreadingassistant.qahost` / `.qahost.test`；正式包未安装、未运行 |
| 构建 | `gradlew.bat -PcraAndroidTestBuildType=qahost :app:assembleQahost :app:assembleQahostAndroidTest` → `BUILD SUCCESSFUL in 55s`、`72 actionable tasks: 15 executed, 57 up-to-date`、退出码 0；原始日志 `qahost-build.log` |
| 源码对应基准 | `E:\creation-reading-assistant\codex-s14-fix-20260928\LocalZipBackupTest-passed.xml`（`tests="10" failures="0" errors="0"`，时间戳 `2026-09-27T17:58:40`）；四个相关源文件 mtime 均在该次通过运行**之前**，未再改动：`JsonBridge.kt 2026-09-28_01:42:12`、`LocalZipBackup.kt 01:57:24`、`LocalZipBackupTest.kt 01:57:33`、`LocalZipBackupPersistentRestoreTest.kt 01:57:45` |

## APK 哈希（安装与执行使用同一构建）

| 文件 | 字节 | SHA-256 |
|---|---|---|
| 宿主 `app-qahost.apk` | 52602051 | `7C36ADC264C5E1267157DD1032467046399DEFFB0883EC6C2A4C7CF51629F236` |
| 测试 `app-qahost-androidTest.apk` | 1657458 | `EDBB50DA9D903206088F783718A26027E7CD64E6C623F5CA27370EB27F999CB7` |

两包均 `adb install -r` 输出 `Success`、退出 0（`install.log`）。设备侧 `pm path` 指向的 `base.apk` 用 `sha256sum` 回读，与上表**逐字相同**（宿主 `7c36adc2…`、测试 `edbb50da…`），证明安装与执行来自同一构建。qahost 原有包数据未清除。

## 原始结果

| 轮次 | 原始结果 | 处理 |
|---|---|---|
| 1 | `OK (6 tests)`、`INSTRUMENTATION_CODE: -1`、ADB 退出 0，6 项状态码全 0 | 计为通过 |
| 2（确认复跑，同一安装） | `OK (6 tests)`、`INSTRUMENTATION_CODE: -1`、ADB 退出 0 | 复现一致 |

命令：

```
adb -s emulator-5556 shell am instrument -w -r \
  -e class com.creationreadingassistant.feature.sync.LocalZipBackupPersistentRestoreTest \
  com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

原始输出：`s14-instrument-run1.log`、`s14-instrument-run2.log`（E 盘）。

### 逐项结果（两轮一致）

| # | 用例 | 结果 | 覆盖点 |
|---|---|---|---|
| 1 | `zipBackupRoundTripRestoresFiveCategoriesAndFilesAcrossCloseReopen` | PASS | 导出→导入→关闭 DB→重开的五类记录与文件集合独立 SHA-256 对账（既有回归） |
| 2 | `correctionForeignKeyFailureRollsBackAllCategoriesInPersistentDbAndStaysRolledBackAfterReopen` | PASS | 纠错外键失败时持久库事务回滚，重开后仍保持回滚（既有回归） |
| 3 | `zipWithoutBackupJsonIsRejectedBeforeTouchingPersistentDb` | PASS | 缺 `backup.json` 的 ZIP 在触碰持久库前被拒（既有回归） |
| 4 | `fileCopyMidWriteFailureKeepsExistingUniqueSourceFileIntact` | PASS | **旧文件写中途失败保护**：`fileCopyOperation` 接缝在 staging 写入中途抛 `IOException`；断言旧文件 SHA-256 与原件逐字节相同、无 `.staging` 残留，且 DB 行已提交为 rev2 |
| 5 | `replaceFailureKeepsTargetAndThrowsInsteadOfDirectOverwrite` | PASS | **替换失败保护**：目标位置预置同名目录（夹具自洽，该路径只有"目录"一个角色），目录守卫在 staging 之前拒绝；断言 `IllegalStateException`、目录原样保留、无残留，DB 行已提交为 rev5 |
| 6 | `newerLocalRowKeepsNewerFileBytesOnSuccessfulRestore` | PASS | **版本偏斜双轴对账**：本地 rev7/updated_at 较新、备份 rev6 较旧时，行轴（整行实体相等：revision/updated_at/title/local_content_path）与文件轴（SHA-256 仍为新字节）**两轴都保持本地较新**；同 ZIP 的全新书籍行+文件照常恢复 |

用例 4/5/6 同轮证明的正是 JVM 绿灯不能代替的部分：它们在**真实 Linux 文件系统语义**上走了 staging→`renameTo` 替换链，并断言了目标字节与残留状态。Windows JVM 上 `renameTo` 对既有目标返回 false 而触发的保护性 backup 换入路径，在 Android(POSIX) 上不可达，本轮不需要也没有验证其设备侧行为。

## 隔离与对账

### qahost 原有库前后一致（未做恢复、未改字节）

`creation_reading_assistant_native` / `-wal` / `-shm` 三件在运行前、run1 后、run2 后各取一次 `sha256sum`，**三组逐项一致**（`run-as` 在应用私有目录内执行，无权限绕过）：

| 文件 | SHA-256（前/后一致） |
|---|---|
| `creation_reading_assistant_native`（1138290688 B） | `ec9eb75b7527155daf42bf0dbed888722b924f33bcfd3a77f50f1ccd9bcbfeb8` |
| `-wal`（524288 B） | `dd429a594075584b56066f72df5eedafd9a72a50a3487af52e4baf8580230476` |
| `-shm`（32768 B） | `cf9a2c45a21ff8597ae93e96a953fa9b8ab2a312dc9d31f9abd4b85a3177317b` |

原始文件：`qahost-db-before.sha256`、`qahost-db-after-run1.sha256`、`qahost-db-after-run2.sha256`。

### 隔离数据只落在专用路径

- 测试库名 `s14_persistent_backup_restore_test.db`（唯一命名）。运行后 `databases/` 仍只有正式库三件，`@After` 已删除 db/-wal/-shm/-journal；残留痕迹仅为 `cache/s14_persistent_backup_restore_test.db.lck`（Room 的锁文件，0 字节）。
- 隔离根目录 `cache/s14_persistent_backup_restore_test.db-root-<tag>-<nano>` 在 `@After` 递归删除；运行后 `cache/` 下 root 目录计数为 **0**。
- `files/books` 前后快照一致：条目 22 项（含 `epub/`），逐项同名。测试只对被重定向后的 `filesDir/books` 做恢复，**绝不对既有书或正式库做恢复**。
- 快照原始文件：`qahost-pre-snapshot.txt`、`qahost-post-snapshot.txt`。

### 设备设置回读（本轮未改动）

`wm density` → `Physical density: 280`；`wm size` → `1080x1920`；`user_rotation=0`；`accelerometer_rotation=1`。与运行前基线一致，无需恢复动作（原始记录 `device-settings-after.txt`，身份信息 `device-identity.txt`）。

## 边界（如实保留）

1. **整包非原子**：`JsonBridge.importFromString` 的事务先提交，之后才复制书源文件。用例 4/5 明确断言"DB 行已提交（rev2 / rev5）"——这不是缺陷掩盖，是该边界被写进断言。失败后收敛路径仍是**重新导入同一 ZIP**（备份字节仍在，DB 行的版本门控使重复导入幂等）。
2. 只证明 MuMu 隔离范围；不覆盖真实 WebDAV 网络、跨设备 SAF URI、实体手机 ROM、正式库。
3. 用例 6 的保护依据是 `JsonBridge` 回报的**实际 DB 合并结果**（被跳过行的 `local_content_path`），不是"ZIP 是否含该文件"。因此 `local_content_path` 为 null 的行仍存在理论偏斜残余（无路径可保护）。
4. `SecurePrefs` 的 MasterKey/AndroidKeyStore 与 `androidx.work.workdb` 两处固有触碰已在测试 KDoc 登记，属幂等、非用户数据。
5. 未对 qahost 既有 `files/books` 做逐文件前后哈希对账（只做了条目名与数量一致的快照）。

## 证据目录

`E:\creation-reading-assistant\codex-s14-mumu-safety-20260928\`：`qahost-build.log`、`install.log`、`s14-instrument-run1.log`、`s14-instrument-run2.log`、两枚 APK、`qahost-db-{before,after-run1,after-run2}.sha256`、`qahost-{pre,post}-snapshot.txt`、`device-settings-after.txt`、`device-identity.txt`、`evidence-sha256.txt`（目录内各文件 SHA-256 索引）。大工件与原始设备记录留在 E 盘，不纳入仓库。

## 纪律

本轮未修改产品源码；未操作真实手机、正式书库、原始仓库；未 commit/push/reset/checkout，未清理他人未提交文件。Gradle 构建目录与 MuMu 在报告落盘后释放，交总指挥复核。
