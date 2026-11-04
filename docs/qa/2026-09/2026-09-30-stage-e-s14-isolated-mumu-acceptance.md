# 阶段 E S14 新隔离候选 MuMu qahost 限定验收（2026-09-30）· 候选失败报告

> **（Codex 复核位：留空待总指挥转交复核）**

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（开工核对：分支 `codex/recover-worktree-20260920`；仓库 HEAD 现为 `513891d6`，与 [S14 隔离报告](../../coordination/legacy-stages/2026-09/2026-09-30-stage-e-s14-isolation.md) 记录一致）。未误入原目录。
- 候选：`E:\creation-reading-assistant\stage-e-s14-isolated-20260930\android`（HEAD `513891d6` + B1+B2+库浏览器联合候选 + S14 隔离 5 文件；关键文件 SHA 与隔离报告逐项复核一致：`LocalZipBackup.kt ab78a16e…`、`JsonBridge.kt d90c7b12…`、`LocalZipBackupTest.kt 174cfbf6…`、`LocalZipBackupPersistentRestoreTest.kt ede20f80…`、`LocalExportRoomRollbackTest.kt 75e9f71e…`）。
- 本窗口唯一共享写入 = 本报告。候选树保留在失败现场（仅构建产物写入其 `build/`，属 E 盘构建输出）；未修改共享源码、Antigravity 报告、G6/资产窗口文件；未 stage/commit/push/reset/checkout；未切换目录。

## 0. 结论先行

**候选失败：androidTest 编译闭包断裂，本轮设备验收 NOT RUN（零设备操作）。**

1. 从候选构建 host/test APK 失败：`compileQahostAndroidTestKotlin` **25 处 Kotlin 编译错误**，全部落在两个目标测试类（16 处 `LocalZipBackupPersistentRestoreTest.kt` + 9 处 `LocalExportRoomRollbackTest.kt`），均指向被 S14 隔离候选**有意排除**的 R3/S04-5 API（`SourceRefExport`/`TextRuleExport`/`CorrectionExport`/`sourceRefDao`/`textRuleDao`/`correctionDao`/`librarySourceRefs`/`textRules`/`textCorrections`）。host 主源编译无错误，但无测试 APK 即无验收。
2. **未做任何变通**：删改两个测试类中的 R3 断言以"跑通"会削弱这些测试所验证的 R3 回滚/覆盖语义，属"悄悄重跑到通过"；合并 R3 hunks 属 [隔离报告 §8 SEAM REQUEST](../../coordination/legacy-stages/2026-09/2026-09-30-stage-e-s14-isolation.md) 的另一窗口所有权。两者均不为本窗口所动。
3. **MuMu 独占被遵守但设备零操作**：执行时 `adb devices` 显示 `emulator-5556` 未连接（模拟器未运行）；叠加构建失败，本轮未安装、未备份、未改动设备任何状态——无恢复义务，正式包/正式书库/原 APK 均未触碰。
4. 旧 MuMu 记录（09-28 `6/6`、09-27 Room 回滚）对应**旧 APK 构建**（`7C36ADC2…`/`CA700ACC…` 时代）与**整树 JsonBridge**，对新候选不构成替代；本轮亦未运行任何测试。

## 1. 复现（最小复现，命令与退出码）

```
工作目录：E:\creation-reading-assistant\stage-e-s14-isolated-20260930\android
命令：gradlew.bat :app:assembleQahost :app:assembleQahostAndroidTest -PcraAndroidTestBuildType=qahost --offline --console=plain
退出码：1（BUILD FAILED in 3m 21s，58 tasks: 32 executed, 26 from-cache）
日志：E:\creation-reading-assistant\stage-e-s14-isolated-20260930\build-qahost-apks.log（SHA-256 2c9ec9060f8db503…前缀 2c9ec9060f8db503585e8f9b79ae2cda）
失败任务：:app:compileQahostAndroidTestKotlin
```

错误清单（25 条 `e:`，全部指向被排除 API）：

| 文件 | 错误数 | 代表行 |
|---|---|---|
| `androidTest/…/LocalZipBackupPersistentRestoreTest.kt` | 16 | `:151/:153/:154 Unresolved 'librarySourceRefs'/'textRules'/'textCorrections'`；`:280-281 Unresolved TextRuleExport/CorrectionExport`；`:651-653 No parameter sourceRefDao/textRuleDao/correctionDao` |
| `androidTest/…/LocalExportRoomRollbackTest.kt` | 9 | `:62-64 No parameter sourceRefDao/textRuleDao/correctionDao`；`:113/:129 Unresolved SourceRefExport`；`:138 No parameter librarySourceRefs` |

## 2. 根因（三层，逐层有据）

1. **隔离报告的 androidTest 前提不成立**：[隔离报告 §1.3](../../coordination/legacy-stages/2026-09/2026-09-30-stage-e-s14-isolation.md) 称两个 androidTest 为"HEAD 版（复制）、HEAD 已跟踪"——实际 `git show HEAD` 返回 *"path exists on disk, but not in 'HEAD'"*：`LocalZipBackupPersistentRestoreTest.kt` 是**未跟踪工作树文件**，复制进候选的是引用 R3/S04-5 API 的工作树版本。
2. **HEAD 的 androidTest 闭包本就断裂**：HEAD `JsonBridge.kt` 中 R3 引用计数为 **0**（G0/U/G2 三个提交均未携带 R3），而该未跟踪测试文件引用 R3 API——即在 HEAD 上 `compileQahostAndroidTestKotlin` 同样不可能通过。这不是本轮候选"引入"的回归，而是隔离候选把既有断裂**显式化**了。
3. **测试文件对 R3 的引用是实质语义**（非偶然 import）：`:278-281` 用 `librarySourceRefs/textRules/textCorrections` 构造 `LocalExport` 验证来源引用/规则/纠错的恢复回滚；`:651-653` 以三 DAO 构造 `JsonBridge`。按 S14-only 边界删除这些构造等于删除被验证语义——因此不采用。

## 3. 对既有证据的影响边界

- 隔离报告的 **JVM 34/34** 仍有效（`LocalZipBackupTest.kt` 为 JVM 源集，`JsonBridge` S14-only 足以支撑，XML SHA `45C6D936…`）——本轮未重跑，引用其记录。
- 旧 MuMu `LocalZipBackupPersistentRestoreTest 6/6`（09-28）与 `LocalExportRoomRollbackTest` Room 回滚记录：运行对象为旧 APK + 整树 JsonBridge，**不适用**于本候选，亦不能外推到本轮新 APK（本轮无 APK 产物：`apk/qahost/` 输出目录不存在）。
- `LocalZipBackup.kt` 候选 SHA 与隔离报告一致（`ab78a16e…`），JVM 判别用例（W5/W6/journal/C10）结论维持；**设备层对新候选为零证据**。

## 4. 停止点与修复方向（归属：隔离报告 §8 所有权窗口）

- **本窗口未实施任何修复**（所有权与"不悄悄跑到通过"约束）。修复方向二选一，由所有权窗口/总指挥裁决：
  - **A**：为 S14-only `JsonBridge` 重新生成两个 androidTest 的 S14 子集版本（剥离 R3 构造；R3 部分的测试归 R3/S04-5 批次），并补齐新 `LocalExport` 构造所需的必填空列表；
  - **B**：联合批次将 `JsonBridge` 的 R3/S04-5 hunks 与 S14 hunks 合流（即撤销本轮隔离中的排除），恢复整树语义后再建 androidTest。
- 任一方向完成后需重走本轮全部步骤：构建 → 设备前快照（serial/密度/夜间模式/原 APK/主 DB 与持久文件 SHA 清单）→ 安装 → 运行 → 恢复对账。
- 修复前**不得**引用旧 MuMu 6/6 作为新候选设备证据。

## 5. 本轮设备状态声明

- `adb devices` 执行时无在线设备（`emulator-5556` 未运行；adb daemon 为本轮首次启动）。**未发生任何安装、卸载、备份、恢复或设置变更**；正式包、正式书库、原 APK、qahost 数据均未被触碰——无需也无比对账。
- MuMu 独占约束期内未与其他窗口争用设备；未使用真实手机。

## 6. 边界声明

- 候选树（E 盘）除 `build/` 构建输出与构建日志外零改动，失败现场保留；复现仅需 §1 命令。
- 未修改共享源码、Antigravity 报告、G6/资产窗口文件；未 stage/commit/push/reset/checkout；未切换目录；构建与日志均在 E 盘。
- 本报告不含真实书名；所有 SHA 为本轮实测。
