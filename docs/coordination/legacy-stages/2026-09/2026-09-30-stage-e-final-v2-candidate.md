# 阶段 E 最终 V2 联合候选 — 集成者报告（2026-09-30）

> 本文是本窗口唯一写入共享仓库的文件（`docs/coordination/`）。所有候选产物、构建缓存、日志均在
> `E:\creation-reading-assistant\stage-e-final-v2-20260930\`（E 盘独占目录），未触碰原仓库源码、
> 未暂存/提交任何改动、未操作实体设备。

## 1. 结论

**V2 候选已成形并通过构建类门禁，但按"失败不写 READY"规则，本窗口未写入最终 READY.json。**

- 合流：只读联合基线（71 文件）+ verification-fix（5 文件）+ S05 保留（30 文件）+ Reader 完成态（44 文件）。
- 源码增量 vs HEAD：**140 文件（41 新增 + 99 修改）**，逐文件 SHA 清单见
  `E:\…\stage-e-final-v2-20260930\manifest\final-v2-source-shas.csv`。
- 可复算补丁：`E:\…\stage-e-final-v2-20260930\stage-e-final-v2-from-head.patch`
  （2,662,015 B，SHA-256 `86680bda77efb312093062086ab9778ace64e17dfa378909e0ef7c9ebe56496b`，
  140 文件 / 284 hunk / 41 new file / 0 deleted）。在**全新 HEAD 副本**上 `git apply` exit=0，
  应用后与候选树 **871/871 文件逐字节一致**（复算记录 `E:\…\_verify\fh4`）。
- 门禁 4 项中 3 项全绿；**全量 JVM 有 11 项失败**，且全部属于 Reader 输入
  `stage-e-reader-complete-20260930\READY.json` 自身已申报的 blocker（该 READY 自标 `ready:false`）。
  失败**不是合流引入**（见 §3 证据），但按任务规则"失败或输入未齐则不写 READY"，故不写。

## 2. 计数纠正（针对旧报告 71/72）

| 项 | 数值 | 说明 |
|---|---|---|
| 联合基线选择文件 | **71** | `joint-selection.csv`：b1b2=17、g6=35、mb03=14、s14r3=5；71/71 SHA 全部一致 |
| 旧报告"72"的来源 | +1 | `joint-delta-vs-head.csv` 共 72 行 = 71 源文件 + `local.properties`（构建配置，非源码） |
| verification-fix 增量 | 5 文件 | 4 MOD + 1 新增（`ProfileHomeStatPlaceholderSemanticsGuardTest.kt`） |
| S05 增量 | **30 文件** | 28 MOD + 2 新增（`ShelfStatusCountsTest.kt`、`BookReadinessProgressLabelTest.kt`）；补丁 SHA 与回读报告一致 `3b5d41e6de15…` |
| Reader 增量 | 44 文件 | 119 hunk；补丁 SHA `622988bb0d59…` 与其 READY 声明一致；dry-run + apply 均 exit=0 |
| 最终源码增量 vs HEAD | **140** | 41 ADD + 99 MOD（已排除 `local.properties`） |

## 3. 合流完整性与"11 项失败非合流引入"的证据

- 逐文件对比：合入前两增量后相对联合基线差异 = **35 文件**（= 5 + 30，无交叉覆盖）。
- 各组标记在最终树中共存（抽验）：M-BUG-03 `contentUpdated`（BookGrid=3）、G6 `LocalDurationUnit`
  （ProfileHomeScreen=4）/`durationUnitToggle`=2、S05 `markFinished`（BookRepository=1）与
  `completionState`（StatsPage=4）、B1/B2 `BookCacheClearOutcome`=16 / `indexRefreshCoordinator`=5、
  S14/R3 `JsonBridge`（LocalZipBackup=6）/`sourceDocumentUriOf`（LibrarySourceIndex=1）、
  Reader `ImportLegadoBackup`（ProfileRoute=2）。
- 全量 JVM 结果：**2623 tests / 11 failures / 0 errors / 0 skipped（284 套件）**。
  11 项失败全部来自两个守卫测试，与 Reader READY.json 原文逐字吻合：
  - `TocNotesWiringGuardTest` 5 项失败
  - `ReaderConsumerSnapshotWiringGuardTest` 6 项失败
  - Reader READY 原文："断言 worktree 全量接线形态，在本候选下 11 项失败；这些测试要求把
    day-night 视觉、弹层系统栏、路由协调器等批次一并带入，超出本窗口授权闭包。"
  - 即：同一组守卫在 Reader 候选自己的树上同样失败；合流未新增任何失败面。

## 4. 串行门禁结果（本窗口独占 Gradle home，`--offline --max-workers=1`）

| # | 门禁 | 命令 | 退出码 | 结果 |
|---|---|---|---|---|
| 1 | 全量 JVM | `:app:testDebugUnitTest` | 1 | 2623 tests / **11 failures** / 0 errors（详见 §3） |
| 2 | Lint | `:app:lintDebug` | **0** | BUILD SUCCESSFUL |
| 3 | Host APK | `:app:assembleDebug` | **0** | `app-debug.apk` 51,289,772 B，SHA-256 `e677bf041201a16fc2341b19cf075c81fc29cadc6eb1a2127a7efdaf3022c9a1` |
| 4 | 测试 + qahost APK | `:app:assembleDebugAndroidTest` + `:app:assembleQahost` | **0** | `app-debug-androidTest.apk` 1,485,093 B，SHA-256 `20c69748f95b42f298ddcd12b830df6775db0d1c2eb3510e550c4fc7769f9d4f`；`app-qahost.apk` 51,289,780 B，SHA-256 `3de59171bd43af026dc7dda6de742bc0817a191ac18685799ab5551f373459da` |

日志：`E:\…\_evidence\gate-1-jvm.log` … `gate-4-androidtest.log`；JVM XML 汇总 284 套件。
说明：qahost flavor 无独立 androidTest 编译任务（其 androidTest 由 debug 变体承载，
`assembleQahostUnitTest` 为其 JVM 测试变体），故 Gate 4 以 `assembleDebugAndroidTest`
覆盖测试 APK、`assembleQahost` 覆盖 qahost host APK。

- 包名 / Runner：`com.creationreadingassistant` /
  `com.creationreadingassistant.testing.CraIsolatedTestRunner`（qahost 为 `.qahost` 后缀）。
- 受控导入设备用例 `LegadoMarkerRealImportTemporaryTest` 已确认打包进测试 APK
  （classes12.dex 字符串池命中）；设备执行 NOT RUN（本窗口不操作设备）。

## 5. 为什么不写最终 READY.json（留给收口窗口的明确路径）

任务规则是"全部门禁通过才写 READY"。当前状态是：**3/4 门禁全绿 + 1 项门禁带 11 项上游已申报失败**，
且 Reader 输入自身 `ready:false`。如实结论：

1. 合流本身没有破坏任何东西（871/871 复算一致；失败集合与上游申报完全一致；其余 2612 项 JVM
   全绿；lint 与全部 APK 构建通过）。
2. 唯一缺口是把 **day-night 视觉批次、弹层系统栏（LocalDialogSystemBarsEffect）、
   阅读器路由协调器（ReaderRoute/ReaderScreenState 协调器族）**带入后，让
   `TocNotesWiringGuardTest`(5) 与 `ReaderConsumerSnapshotWiringGuardTest`(6) 的
   全量接线断言转绿 —— 这正是 Reader READY 列出的 A01/A02 blocker。
3. 因此 **READY.json 未写入**。收口窗口只需：补齐上述两个批次的接线 → 复跑 gate1 全量 JVM
   （预期 2623/0）→ 其余三门禁复用本候选缓存即可 → 写
   `E:\creation-reading-assistant\stage-e-final-v2-20260930\READY.json`
   （模板内容见 §6，数字已冻结可直接引用）。

## 6. 冻结的候选事实（供收口窗口直接引用）

- HEAD：`513891d6a66a9b78e072ba759d34ed580d377ecf`
- 最终补丁：`stage-e-final-v2-from-head.patch`，SHA-256
  `86680bda77efb312093062086ab9778ace64e17dfa378909e0ef7c9ebe56496b`，2,662,015 B，
  140 文件 / 284 hunk / 41 new / 0 deleted；复算方式：全新 `git archive HEAD` 副本 +
  `git -c core.autocrlf=false apply` → 871/871 一致。
- 源码清单：`manifest\final-v2-source-shas.csv`（140 行，含逐文件 SHA-256 与字节量）、
  `manifest\final-v2-file-list.csv`、`manifest\gate-summary.json`。
- APK 三个（绝对路径）：
  - `E:\creation-reading-assistant\stage-e-final-v2-20260930\android\app\build\outputs\apk\debug\app-debug.apk`
  - `E:\creation-reading-assistant\stage-e-final-v2-20260930\android\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk`
  - `E:\creation-reading-assistant\stage-e-final-v2-20260930\android\app\build\outputs\apk\qahost\app-qahost.apk`
- 设备验收范围（与上游一致）：书架批量操作/菜单、库浏览器、统计页时长单位切换、
  Profile 入口 Legado 备份导入最小入口、阅读器目录/书签/笔记/规则弹层、受控 Legado marker 导入
  （`LegadoMarkerRealImportTemporaryTest`，自建受控库不改用户 DB）。
- 已知缺口（唯一）：§5 第 2 条的两个守卫测试批次（11 项）。

## 7. 本地提交拆分建议（供集成者收口时使用；本窗口未暂存、未提交）

按来源与依赖顺序，建议 6 个本地提交（每个都可通过 `git apply` 独立复算）：

1. `feat(library): S14/R3 库浏览器与备份断言闭包` — LibrarySourceIndex/ShelfImporter/
   ShelfSourceUpdateChecker(+Test)/LocalZipBackup/JsonBridge/LocalExportRoomRollbackTest/
   LocalZipBackupPersistentRestoreTest 等纯新增与配套。
2. `fix(shelf): M-BUG-03 新书角标与 contentUpdated 稳定化` — ShelfViewModel/BookGrid/ShelfScreen
   的 contentUpdated 链 + ShelfSourceRefreshFeedback/Copy 测试。
3. `feat(settings): G6 阅读时长单位与设置接缝` — DurationPrefs/DurationDisplay/SettingsStore
   镜像/ProfileHomeScreen 时长单位切换 + SettingsStoreBootMirrorTest。
4. `feat(stats): S05 阅读统计完成态闭包` — StatsRepository/StatsPage/BookReadiness/
   HomeContinue* 家族、MyReading/HomeArchive ViewModel 与 StatsComputation/
   ReadingStatsPolicy/ShelfStatusCounts/BookReadinessProgressLabel 测试。
5. `feat(reader): S07+S12 阅读器修复闭包（目录/笔记/规则/导入）` — ReaderScreen/SheetHost/
   TocSheet/NotesSheet/RulesSheet/Markdown 分支/ReaderViewModel/ReaderDocumentLoader +
   ReaderTocSheetTest/ReaderRulesSheetTest；`LegadoMarkerRealImportTemporaryTest` 一并入库。
6. `chore(test): verification-fix 语义守卫` — ProfileHomeStatPlaceholderSemanticsGuardTest、
   ProfileHomeScreenGestureTest 的 androidTest 断言与 TestDoubles 收口。

> 拆分以"一个来源一个提交"为原则，边界文件（ProfileViewModel、BookRepository、ProfileRoute）
> 归入其语义主批次（3/4/5），避免跨提交半成品。

## 8. 窗口纪律声明

- 只读输入：联合基线、三个增量候选、`stage-e-final-joint-20260930\head\android`（HEAD 对照）。
- 独占写入：仅 `E:\creation-reading-assistant\stage-e-final-v2-20260930\` 与本文。
- 未执行 stage/commit/push/reset/checkout；未触碰原仓库可写状态；未安装 APK、未连接设备。
- 构建使用本窗口独占 Gradle home（`_cache\gradle-home`，805.9 MB），未与其他窗口共享缓存。
