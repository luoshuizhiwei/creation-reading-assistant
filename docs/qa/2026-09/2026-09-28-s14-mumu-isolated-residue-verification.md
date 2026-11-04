# S14 隔离设备核验（补验）— journal 杀窗修复的真机复算

> **Codex 2026-09-28 独立复核：MuMu qahost 限定通过。** 本次设备是 MuMu `emulator-5556`，标题与正文“真机”均应读作“模拟器”；无真实进程 kill 注入。E 盘 UTP XML 实读 11/0/0/0、SHA-256 `6de0c9215a8899325af080fc2ee214c7435b89fe91085c98f7fdd82b790da4c4`，另有 `am instrument` 6/6 原始日志；受控残留 5 项、同轮 APK 哈希、前后 73 行归一化对账均可回读。POSIX 上覆盖 rename 的一次成功实测不足以断言备份分支在所有 Android 异常环境“不可达”，应记“正常路径本轮未触发”。整包 DB+文件非原子、真实杀进程和实体手机仍未验。

> 本报告是 `2026-09-28-s14-mumu-zip-safety-acceptance.md` 与
> `2026-09-28-s14-mumu-persistent-zip-acceptance.md` 的**同批原始证据补充**，
> 目的是让「6 项持久化恢复测试通过」「受控隔离目录恢复结果」从执行者陈述
> 变为可独立复算的工件。
>
> **范围限定：** 本轮**未**修改任何产品源码；`LocalZipBackup.kt` 保持任务指定版本。
> 仅新增一个一次性 androidTest（`S14ResidueRecoveryDeviceTest.kt`），验后已从仓库删除，
> 源码副本与 SHA-256 留在 E 盘工件目录。

| 字段 | 值 |
|------|-----|
| 日期 | 2026-09-28 |
| 设备 | MuMu `emulator-5556`（SM-S9280 / API 32，物理密度 280，核验期间 override 420） |
| 被测源码 | `android/app/src/main/java/com/creationreadingassistant/feature/sync/LocalZipBackup.kt` |
| 被测源码 SHA-256 | `ab78a16e8e34c50a59ada31040278569baaa013f2c1eba6054a675b89997514c`（= 任务指定值，逐字节一致） |
| 一次性测试 SHA-256 | `70f0584f4d546e273d2398ba1d74d0b767061ec9dac5f97c1074fbc6da2ca50f` |
| 工件目录 | `E:\creation-reading-assistant\codex-c-final-isolated-20260928\s14\` |

## 结论

| # | 项 | 结果 |
|---|----|------|
| 1 | 用**当前** `LocalZipBackup.kt` 构建 qahost 与测试 APK | **PASS** |
| 2 | 宿主 / 设备侧 APK SHA-256 逐字节一致 | **PASS** |
| 3 | 固定代码确在出厂 APK 内（dex 常量核对） | **PASS** |
| 4 | `LocalZipBackupPersistentRestoreTest` 6 项在**真机**通过 | **PASS**（XML 0 failures / 0 errors） |
| 5 | 受控隔离目录：**日志在 + 旧 dst + 完整 staging 并存** → 收敛 staging | **PASS** |
| 6 | 受控隔离目录：**dst 缺失 + 完整 staging 在** → 恢复 staging | **PASS** |
| 7 | 受控隔离目录：**dst 缺失 + 仅 backup 在** → 恢复 backup | **PASS** |
| 8 | journal 路径被目录占用时不破坏导入 | **PASS** |
| 9 | 逐文件字节哈希 / 日志 / **测试 XML** / DB-WAL-SHM 前后状态 | **PASS** |
| 10 | 设备状态恢复（density、stay_on） | **PASS** |
| 11 | 不用 JVM 结果代替设备结果 | **PASS**（本轮结论一律以设备 XML 与设备 sqlite3/字节哈希为准） |
| 12 | Android 上「保护性 swap-in」分支的可达性 | **NOT REACHABLE**（平台语义，见下） |
| 13 | 整包 DB + 文件写入的原子性 | **未修复，如实登记**（见下） |

**没有任何一项 FAIL。**

---

## 1. 构建与安装（命令 + 退出码）

```
./gradlew :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --console=plain
→ BUILD SUCCESSFUL in 5m 3s        EXIT=0     （logs/01-assemble-qahost-and-test.log）

adb -s emulator-5556 install -r -t app-qahost.apk            → Success  EXIT=0
adb -s emulator-5556 install -r -t app-qahost-androidTest.apk → Success  EXIT=0
                                                          （logs/02-install.log）
```

### 宿主 / 设备 APK 哈希（两轮）

| 轮次 | 产物 | 宿主 SHA-256 | 设备 SHA-256 | 一致 |
|------|------|--------------|--------------|------|
| A（`am instrument`） | `app-qahost.apk` | `de880690…c2cc2e` | `de880690…c2cc2e` | ✓ |
| A | `app-qahost-androidTest.apk` | `dc572002…6080e9` | `dc572002…6080e9` | ✓ |
| B（`connectedQahostAndroidTest`） | `app-qahost.apk` | `e82904cc…24ef8a` | `e82904cc…24ef8a` | ✓ |
| B | `app-qahost-androidTest.apk` | `a2696cf1…284002` | `a2696cf1…284002` | ✓ |

两轮的 APK 字节不同是正常的（APK 打包不可复现），关键是**同一轮内宿主与设备逐字节相同**。
完整记录：`logs/03-device-apk-hash.log`、`logs/09-apk-hash-round-b.log`。
设备侧 base.apk 已拉回本地：`build/device-base.apk`、`build/device-androidTest.apk`。

### 固定代码确在 APK 内

解包 `classes*.dex`（`build/dexcheck/`）后在 `classes16.dex` 中检索到修复引入的
`fail-closed`、`completeInterruptedSwapIn`、`writeJournalDurably` 以及中文错误串，
证明被测二进制确实包含 journal 杀窗修复，而非旧包。

---

## 2. 6 项持久化恢复测试在真机通过

### 轮次 A（`am instrument`，原始 stdout）

```
adb -s emulator-5556 shell am instrument -w -r \
  -e class com.creationreadingassistant.feature.sync.LocalZipBackupPersistentRestoreTest \
  com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
→ Time: 0.501 / OK (6 tests) / INSTRUMENTATION_CODE: -1      EXIT=0
                                          （logs/05-instrument-persistent-restore.log）
```

6 项均出现 `INSTRUMENTATION_STATUS_CODE: 1`（开始）→ `0`（通过）：

1. `zipBackupRoundTripRestoresFiveCategoriesAndFilesAcrossCloseReopen`
2. `correctionForeignKeyFailureRollsBackAllCategoriesInPersistentDbAndStaysRolledBackAfterReopen`
3. `zipWithoutBackupJsonIsRejectedBeforeTouchingPersistentDb`
4. `fileCopyMidWriteFailureKeepsExistingUniqueSourceFileIntact`
5. `replaceFailureKeepsTargetAndThrowsInsteadOfDirectOverwrite`
6. `newerLocalRowKeepsNewerFileBytesOnSuccessfulRestore`

### 轮次 B（AGP/UTP，产出真实 JUnit XML）

```
./gradlew :app:connectedQahostAndroidTest -PcraAndroidTestBuildType=qahost \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.creationreadingassistant.feature.sync.LocalZipBackupPersistentRestoreTest,com.creationreadingassistant.feature.sync.S14ResidueRecoveryDeviceTest \
  --console=plain
→ BUILD SUCCESSFUL in 2m 15s / Starting 11 tests on emulator-5556 / Finished 11 tests
                                                          EXIT=0（logs/08-gradle-connected-xml.log）
```

XML：`xml/TEST-emulator-5556 - 12-_app-.xml`
`tests=11  failures=0  errors=0  skipped=0  time=1.602  timestamp=2026-09-28T14:21:25`
XML SHA-256：`6de0c9215a8899325af080fc2ee214c7435b89fe91085c98f7fdd82b790da4c4`
UTP 原始结果（含逐用例 logcat）：`xml/utp-raw/`。

> 为什么分两轮：`am instrument` 不产 XML，且默认的 `connectedQahostAndroidTest`
> 会在跑完后卸载应用、毁掉 33 本验收书库。轮次 B 用
> `leaveApksInstalledAfterRun=true` 取到真实 XML，跑后核对 qahost 主 DB SHA-256
> 仍为 `d938c7b8…c71214b`、行数仍 33/16/21、书籍文件仍 36 个 —— 数据未被卸载。

---

## 3. 受控隔离目录的恢复结果（任务点名要求的两种状态）

一次性测试 `S14ResidueRecoveryDeviceTest` 在**受控隔离目录**里人为植入残留状态，
再驱动生产 `importZip` 走恢复分支。5 项全部 PASS（轮次 A `OK (5 tests)`，
轮次 B 含在 11 项 XML 内）。原始证据：`state/residue-evidence/`，
逐文件 SHA-256：`state/residue-evidence-hashes.txt`。

| 用例 | 植入状态 | 恢复结果（字节级） |
|------|----------|--------------------|
| **s14-w5**（任务点名） | journal 在 + 旧 dst + 完整 staging 并存 + 无 backup | dst `5e6a98b9…` → **`b9f3d8b4…`（= staging 字节）**；staging 已清除；backup 不存在；`journal=[]` |
| **s14-b1**（任务点名） | dst 缺失 + 完整 staging 在 | dst 不存在 → **`fbdefdf7…`（= staging 字节）**；staging 已清除；`journal=[]` |
| **s14-b2**（任务点名） | dst 缺失 + 仅 backup 在 | dst 不存在 → **`9de8e75c…`（= backup 字节）**；backup 已清除；`journal=[]` |
| s14-journal-dir | journal 路径被**目录**占用 | 导入仍 `SUCCESS`；dst `fc757b48…` 未被改动 |
| s14-rename-semantics | 平台语义取证 | `renameTo` 覆盖既有目标返回 `true`，目标体变为源字节 |

设备侧证据文件（5 个）已拉回并留哈希，设备上的 `s14-residue/` 目录已删除。

---

## 4. 前后状态：逐文件字节哈希 + DB/WAL/SHM

`state/pre-qahost-state.txt` vs `state/post-qahost-state.txt`，
归一化后 diff **EXIT=0**（73 行逐字节一致，`state/pre-post-diff.txt`）：

| 项 | pre | post |
|----|-----|------|
| 主 DB SHA-256 | `d938c7b8…c71214b` | `d938c7b8…c71214b` |
| WAL SHA-256 | `e3b0c442…b7852b855`（空） | 同 |
| SHM SHA-256 | `b0ec8cec…1ef6531` | 同 |
| `books` / `reading_progress` / `book_content` / `inspirations` 行数 | 33 / 16 / 21 / 0 | 33 / 16 / 21 / 0 |
| `files/books` 逐文件哈希 | 36 项 | 36 项，全部相同 |
| `shared_prefs` 逐文件哈希 | 7 项 | 7 项，全部相同 |
| `datastore` 逐文件哈希 | 6 项 | 6 项，全部相同 |
| 主 `databases/` 目录 | 仅主库 | 仅主库（无隔离库残留） |

---

## 5. 如实登记（未通过 / 边界 / 不可达）

### 5.1 整包 DB + 文件写入仍非原子

本轮**没有**改动这一点，也**没有**验证到「整包（DB 与书籍文件一起）原子」。
现有保证的范围是：单个文件的落盘经 staging/journal 收敛，DB 侧由 Room 事务回滚
（第 2 节第 2 项已覆盖）。若进程在「DB 已提交、文件尚未落齐」之间被杀，
整包一致性仍可能不成立。这是**已知边界，不是本轮回归**。

### 5.2 保护性 swap-in（backup + journal）分支在 Android 上不可达

`copyWithStaging` 的逻辑是「先 `staging.renameTo(dst)`；成功即返回」，
只有 rename 失败且 dst 已存在时才创建 backup 并写 journal。
在 Android/POSIX 上 `File.renameTo` 会**原子覆盖**既有目标，因此该失败分支
在 Android 上不会被走到 —— 它是 Windows 语义下的保护路径。

- 证据：用例 `androidRenameOverExistingTargetSucceedsOnDevice` 实测
  `renameTo returned=true`、目标被覆盖、源消失（`s14-rename-semantics.txt`）。
- 因此 W5/W6 那类「杀在 journal 与首次 rename 之间」的状态，在 Android 上
  **只能靠人为植入 + 驱动 `importZip` 的恢复侧**来验证（第 3 节就是这么做的），
  **不能**声称它是 Android 上可自然到达的崩溃窗口。
- 与之配套的 JVM 用例（`LocalZipBackupTest.kt` 的
  `killedAfterJournalBeforeFirstRenameConvergesToStagingBytes` /
  `journalWriteFailureAbortsSwapInAndKeepsOldTargetIntact`）通过测试接缝模拟了
  该分支，属于**接缝级**覆盖，本轮未用它充当设备结论。

---

## 6. 证据清单

```
s14/
  EVIDENCE-INDEX.md                     证据索引
  S14ResidueRecoveryDeviceTest.kt       一次性测试源码副本（仓库内已删除）
  build/                                APK、设备回读 APK、dex 常量核对目录
  logs/01-assemble-qahost-and-test.log  构建
  logs/02-install.log                   安装（EXIT=0）
  logs/03-device-apk-hash.log           轮次 A 宿主/设备哈希
  logs/04-density-stayon.log            设备参数调整（原值 density=280 stay_on=0）
  logs/05-instrument-persistent-restore.log   6 项 stdout
  logs/06-instrument-residue-recovery.log     5 项 stdout
  logs/07-logcat-residue-device.log           隔离恢复的逐字节证据
  logs/08-gradle-connected-xml.log            AGP/UTP 11 项
  logs/09-apk-hash-round-b.log                轮次 B 宿主/设备哈希
  state/00-baseline-device.txt          设备/应用基线
  state/pre-qahost-state.txt            核验前状态
  state/post-qahost-state.txt           核验后状态
  state/pre-post-diff.txt               归一化 diff（EXIT=0）
  state/residue-evidence/               设备侧 5 个证据文件
  state/residue-evidence-hashes.txt     证据文件哈希
  xml/TEST-emulator-5556 - 12-_app-.xml 真实 JUnit XML（11 项 0 失败）
  xml/utp-raw/                          UTP 原始结果（含逐用例 logcat）
```

## 7. 设备状态恢复

| 项 | 核验前原值 | 核验中 | 恢复后 |
|----|-----------|--------|--------|
| `wm density` | 280（无 override） | override 420 | 280（`wm density reset`）✓ |
| `stay_on_while_plugged_in` | 0 | 7 | 0 ✓ |
| `ui_night_mode` | 2 | 2（未改） | 2 ✓ |
| `user_rotation` / `accelerometer_rotation` | 0 / 1 | 未改 | 0 / 1 ✓ |

设备侧临时备份（`/data/local/tmp/qahost-safety-backup`，204 MB）与
`/sdcard/Android/data/<pkg>/files/s14-residue/` 均已删除。

## 8. 仓库改动

- 新增后删除：`android/app/src/androidTest/.../feature/sync/S14ResidueRecoveryDeviceTest.kt`
- `git status` 无被跟踪文件删除；未 commit / push / reset / checkout。
