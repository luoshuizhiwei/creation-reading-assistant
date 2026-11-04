# 阶段 E S14 备份恢复安全候选隔离报告（2026-09-30）

- **日期**：2026-09-30
- **执行角色**：Antigravity（本窗口），独立于 G6 联合批次
- **工作仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **HEAD**：`513891d6a66a9b78e072ba759d34ed580d377ecf`
- **隔离目录**：`E:\creation-reading-assistant\stage-e-s14-isolated-20260930\`
- **基础源**：`E:\creation-reading-assistant\stage-e-b1-b2-library-combined-20260929\`（B1+B2+库浏览器联合候选，JVM 167/167）
- **唯一可写共享文件**：本报告

---

## 0. 结论先行

| 项 | 结论 |
|---|---|
| 隔离编译 `:app:compileDebugKotlin` | **BUILD SUCCESSFUL in 2m 53s，退出码 0** |
| 隔离编译 `:app:compileDebugUnitTestKotlin` | **同批，BUILD SUCCESSFUL，退出码 0** |
| `LocalZipBackupTest` JVM 定向测试 | **34/34 PASS，0 failures，0 errors，0 skipped** |
| JsonBridge S14 hunk 精确提取（排除 R3/S04-5） | **已验证（仅 4 hunk，无 SourceRefExport 等 DTO）** |
| 是否可进入后续联合候选 | **是**，待 androidTest 设备验证（需另行授权 MuMu） |

**旧 34 项 JVM 通过结论按本次最新源码重新核查：通过。** 本次实跑与历史报告引用的同一版本 `LocalZipBackup.kt`（SHA 一致），判别用例 W5/W6 均在结果中可见并通过。

---

## 1. 候选文件清单与 SHA-256

### 1.1 生产源码（工作树已修改）

| 文件 | 状态 | SHA-256 |
|---|---|---|
| `feature/sync/LocalZipBackup.kt` | 整文件 S14 修订版 | `AB78A16E8E34C50A59ADA31040278569BAAA013F2C1EBA6054A675B89997514C` |
| `feature/sync/JsonBridge.kt` | **精确 4 hunk**（S14 only） | `D90C7B12B4D014E86F374DFC7B1CBD90FC02B4DBAF1EC860B0D787FE7459A63F` |

> `LocalZipBackup.kt` SHA 与旧报告（`2026-09-28-s14-mumu-isolated-residue-verification.md`）引用值 `ab78a16e…` **逐字节一致**，证明候选为已验证版本。

### 1.2 JVM 单元测试（工作树已修改）

| 文件 | 状态 | SHA-256 |
|---|---|---|
| `test/feature/sync/LocalZipBackupTest.kt` | 整文件（含 W5/W6 判别用例） | `174CFBF6CBD3FF35E182EFC4B956FF46D72B4DED20AE7CC910BFB01CC29A690A` |

### 1.3 androidTest（HEAD 已跟踪，无工作树修改）

| 文件 | 状态 | SHA-256 |
|---|---|---|
| `androidTest/feature/sync/LocalZipBackupPersistentRestoreTest.kt` | HEAD 版（复制） | `EDE20F8022AB67BD67C74C5885CA57484C2BF67D4EE41007A2637FAD6D078BF0` |
| `androidTest/feature/sync/LocalExportRoomRollbackTest.kt` | HEAD 版（复制） | `75E9F71E0787E90C2FB5141F4F1CEC9DC80AC2B498071DA4A9D9F15D76C48320` |

> `LocalExportRoomRollbackTest.kt` 为 androidTest（使用真实 Room），不属于本轮 JVM 测试范围；旧 MuMu Room 回滚验收（`2026-09-27-s14-mumu-room-rollback-acceptance.md`）已有设备证据，本报告不重新验证。

---

## 2. JsonBridge S14 精确 Hunk 边界

工作树 `JsonBridge.kt` 差异含 **S14 + R3/S04-5 混合改动**，本候选仅提取以下 4 个精确 hunk：

| Hunk | 内容 | 行范围（隔离文件） |
|---|---|---|
| H1 | `ImportMergeResult` 数据类（含 KDoc） | 插入于 `LocalExport` 类结束后 |
| H2 | `importFromString` 签名添加 `onMergeResult: (ImportMergeResult) -> Unit = {}` 参数 | 函数签名行 |
| H3 | 事务内 `acceptedBookIds`/`skippedBookContentPaths` 收集 + 书籍 else 分支 | 书籍 upsert 循环内 |
| H4 | 事务提交后 `onMergeResult(ImportMergeResult(...))` 回调 | 事务 `}` 之后，函数 `}` 之前 |

**明确排除（不带入）：**
- `SourceRefExport` / `TextRuleExport` / `CorrectionExport` DTO 类（R3/S04-5）
- `JsonBridge` 构造器的 `sourceRefDao` / `textRuleDao` / `correctionDao` 参数（R3）
- `LocalExport` 的 `librarySourceRefs` / `textRules` / `textCorrections` 字段（S04-5）
- `importFromString` 中的 R3 合并块（sourceRef/textRule/correction 写入逻辑）
- `exportToString`/`buildExport` 中的 R3 填充逻辑

---

## 3. 编译证据

```
命令：
  $env:GRADLE_USER_HOME = "E:\creation-reading-assistant\stage-e-s14-isolated-20260930\gradle-cache"
  $env:JAVA_HOME = "D:\develop\Java\jdk-17.0.14"
  $env:ANDROID_HOME = "D:\develop\Android\Sdk"
  .\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon

结果：
  BUILD SUCCESSFUL in 2m 53s
  22 actionable tasks: 4 executed, 18 up-to-date
  退出码：0
```

---

## 4. JVM 定向测试证据

```
命令：
  .\gradlew.bat :app:testDebugUnitTest \
    --tests "com.creationreadingassistant.feature.sync.LocalZipBackupTest" \
    --no-daemon

结果：
  BUILD SUCCESSFUL in 1m 12s
  退出码：0

XML：TEST-com.creationreadingassistant.feature.sync.LocalZipBackupTest.xml
  tests="34" failures="0" errors="0" skipped="0"
  timestamp="2026-09-30T02:39:30"
  XML SHA-256：45C6D936D2AA3AF2D82240DCBB3D6FCC28286C56E6DF63C03DA71F4D22B4025A
  日志路径：E:\creation-reading-assistant\stage-e-s14-isolated-20260930\android\app\build\test-results\testDebugUnitTest\
```

**34 项全部通过，含关键判别用例：**

| 用例 | 验证点 |
|---|---|
| `killedAfterJournalBeforeFirstRenameConvergesToStagingBytes` (W5) | journalAdd 后、③ dst→backup 前被 kill：staging 落位补完，不留旧正文 |
| `journalWriteFailureAbortsSwapInAndKeepsOldTargetIntact` (W6) | 归属日志写失败 fail-closed：不执行破坏性 rename，旧目标原样保留 |
| `files of skipped newer books are not overwritten by backup bytes` | 版本感知文件保护：本地较新书籍文件不被覆盖 |
| `journalFileIsNeverPackedAndNeverImportedFromZip` | 归属日志不打包进备份，不被外来 ZIP 注入 |
| `exportResolvesJournalBackedMissingTargetBeforePacking` (C10) | 导出前按证据复位 dst，备份链不丢书本体 |
| `importKeepsOrdinaryFileMatchingBackupFormatWhenTargetMissing` | 无归属证据的同格式文件原样保留，不误判 |
| `superLongNanoDigitsDoNotCrashExportOrImport` | 超长数字不触发 toLong() 溢出 |

---

## 5. 旧通过结论核查

### 5.1 LocalZipBackupTest（JVM）
- 旧报告（`2026-09-28-s14-journal-kill-window-fix.md`）：34/34，退出码 0
- 本次重跑：**34/34，退出码 0**，SHA 一致 ✅

### 5.2 LocalZipBackupPersistentRestoreTest（MuMu androidTest）
- 旧报告（`2026-09-28-s14-mumu-zip-safety-acceptance.md`）：6/6，宿主 APK SHA `7C36ADC2…`
- 本次：**未重跑**（androidTest 需设备，本任务不操作 MuMu）
- 结论：旧结论限定有效（MuMu qahost 隔离范围），未扩展到新 APK 构建

### 5.3 LocalExportRoomRollbackTest（MuMu androidTest）
- 旧报告（`2026-09-27-s14-mumu-room-rollback-acceptance.md`）：6/6 （Room 内存 DB）
- 本次：**未重跑**
- 结论：旧 MuMu 证据有效，HEAD 文件无工作树修改

---

## 6. 已知剩余 Kill 窗口风险

| 风险 | 类别 | 说明 |
|---|---|---|
| 真实进程 kill（kill -9）在 rename 序列中 | **未验证** | JVM 夹具以预置中间态驱动，不代表真实 OS 级进程终止 |
| SQLite/WAL 与文件复制的整包原子性 | **已知未修复** | DB 先提交、文件后复制，整包非原子；旧报告明确登记 |
| 多 backup 累积跨目录并发残留 | **JVM 夹具覆盖，设备未验** | `multipleJournalEntriesForSameTargetConvergeToNewestBytes` 已 PASS |
| Android POSIX rename 对保护性换入路径的可达性 | **旧报告登记为 NOT REACHABLE（POSIX 原子替换）** | 平台语义，Windows JVM 为常态路径 |
| 实体手机 ROM/MIUI 差异 | **NOT RUN** | 仅 MuMu 模拟器验证 |

---

## 7. 候选状态

```
隔离目录：E:\creation-reading-assistant\stage-e-s14-isolated-20260930\android
基线：HEAD 513891d6（B1+B2+库浏览器联合候选之上，不改变已有批次改动）
JVM：34/34 PASS（退出码 0，XML SHA 已记录）
androidTest：LocalZipBackupPersistentRestoreTest 旧 MuMu 6/6（本轮未重构建）
             LocalExportRoomRollbackTest 旧 MuMu 已验（本轮未重构建）
暂存区：空（未 stage/commit/push）
```

**可进入后续联合候选**，前提：
1. 后续联合批次需包含本候选的 5 个文件；
2. 若需新 androidTest APK 构建与 MuMu 验收，须另行明确授权。

---

## 8. 所有权与边界

- **本窗口独占所有权**：`LocalZipBackup.kt`、`JsonBridge.kt`（仅 S14 4 hunk）、`LocalZipBackupTest.kt`、`LocalZipBackupPersistentRestoreTest.kt`、`LocalExportRoomRollbackTest.kt`
- **未越界修改**：未触碰 G6、B1、B2、库浏览器、S06、M-BUG-03 任何文件
- **未 stage/commit/push/reset/checkout**，共享仓库产品源码只读
- **发现交叉**：`JsonBridge.kt` 同时含 R3/S04-5 改动，本候选已精确分离；R3/S04-5 部分记 SEAM REQUEST，不由本窗口整合

> **SEAM REQUEST**：`JsonBridge.kt` 的 R3 `SourceRefExport`/`TextRuleExport`/`CorrectionExport` DTO 及 `sourceRefDao/textRuleDao/correctionDao` 接线（当前仍在工作树，未隔离）与 S14 hunk 共存于同一文件。后续联合批次集成者在合并 S14 JsonBridge hunk 时需与 R3/S04-5 窗口协调，确认两组改动合流后语义一致、无重叠行。本窗口不越界修改 R3/S04-5 部分。
