# TXT 失败证据链修正与最小复现 v1

- 任务编号/版本：`CRA-TXT-FAILURE-EVIDENCE-REPAIR-v1`；2026-10-05 指定，实际执行 2026-10-06T00:20–01:50+08:00（离线核查 00:20 起、设备最小批 01:36–01:41），应用 ZCode / GLM（GLM-5.3-Flash）。角色=本任务 E 目录所有者、隔离 MuMu（实例 1「洛水之蔚」）唯一设备所有者。本轮为 MuMu 模拟器验收，不称实体手机/MIUI 通过。
- 输入指纹：D HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工，暂存区 0 条，既有 WIP 500 条未触碰）；TXT-v4 候选 [E:/creation-reading-assistant/txt-reentry-fix-v4/android](<E:/creation-reading-assistant/txt-reentry-fix-v4/android>)（980 项，经旧批副本逐条 SHA 实算核对）；旧批证据 [E:/creation-reading-assistant/txt-final-mumu-accept-v1](<E:/creation-reading-assistant/txt-final-mumu-accept-v1>)（只读保留，未改写任何原件）；已验 patched-driver 原件复用（joint_driver `013ca2f9…`、runner `a634a344…`）；fixture `15e6f965…` 字节复用。
- 所有权与改动：写入仅 `E:\creation-reading-assistant\txt-failure-evidence-repair-v1\`（android 副本、freeze、driver 接缝副本、patched-driver、runs、evidence、build-logs、gradle-home 副本、config.json、execution.json）与本唯一报告。D 侧仅新增本报告，不触碰产品源码/全局交接/TODO/旧批证据/TXT-v4 源码；不 commit/push/reset/checkout；不触正式书库与实体手机。另一窗口只读分析未被干扰。
- 环境：Windows / Git Bash / Python 3.13；JDK 17.0.14；Gradle 8.9 `--offline --no-daemon`（隔离 gradle-home 复制自旧批缓存，全程未联网下载）；MuMu serial `127.0.0.1:16416`（实时 `adb devices` 核实，`emulator-5556` 别名未使用）。MuMu 启动过程见「执行与结果-环境插曲」。

## 执行与结果

### 一、旧批不可追回缺口（离线核查，全部有原件证据）

**[P1] 测试 APK 绑定断裂的精确时间线（已实锤，不改写旧 SHA）**

| 时刻（10-05） | 事件 | 证据 |
|---|---|---|
| 17:36:33 | build1 → `freeze/` 双 APK 写入；`source-sha256.json` 冻结（982 项，测试源=555d86d2 版本） | freeze mtime、build.log |
| 17:38:09 | 开工前账（night=no） | evidence/raw/baseline.txt |
| 17:39:15–16 | install host+test → 安装的是 **17:36 构建**的测试 APK | install-host/test.txt mtime |
| 17:39:18–19 | pull 读回：test=`7e342388`（9,264,994 字节）；host=`32114822` | installed-*-readback.apk 实算 |
| 18:00–18:32 | 测试源两次接缝修正（J1RecoveryContract 18:00、TxtFinalAcceptanceJ1Test 18:32=b29486d2） | 源文件 mtime |
| 18:32:48 | build5 → `freeze/app-qahost-androidTest.apk` **覆盖写**= `5c516b4d`（9,397,004 字节） | freeze mtime、build5.log |
| 17:41–20:08 | 31 次驱动运行全部使用 **17:39 安装的旧测试 APK**（此后无任何重装命令） | 全部 console/exit 序列 |

结论：`5c516b4d` = 18:32 重建后仅存在于 freeze 目录的文件（含 b29486d2 测试源），从未安装；`7e342388` = 17:39 安装并跑完全部 J1 批次的实际运行物（含 555d86d2 测试源）。binding.json/config.json 声明 `testApkSha256=5c516b4d` 与设备实际运行物不符——**旧批绑定不成立**，"声明构建物≠设备运行物"。旧批"j1_20/30/40 三入口清零" runner 输出真实存在，但跑的是与最终冻结源不对应的测试 APK；据此**不能宣称旧批 TXT-v4 产品复现结论在最终源绑定下成立，也不能据此推翻**——由本批新批重新裁决（见下文三）。

**[P1] 观测 JSON 缺失的接缝根因（证据取回失败，非产品事实）**

- 旧批 `evidence/raw/j1_10-obs.json`、`j1_20-obs.json` 原件均为 `cat: files/j1-accept/j1_*.json: No such file or directory` 错误文本（76 字节）。
- 根因：测试 `resultsDir()` 写 `getExternalFilesDir`（=`/sdcard/Android/data/<pkg>/files/j1-accept/`），driver `_harvest_observations` 却 `run-as … cat files/j1-accept/*.json`（app **内部**目录）——`run-as` 列内部目录永远为空，harvest 目录为空；事后补救 cat 命令同样指向错误路径。harvest 空目录 + prep-v2 合同（`chapters.size==1` 断言修正版）均与此一致。
- **真实观测副本不可追回**：写入目标（外部目录）随 restore 卸载被系统清除；全 E 盘定向搜索仅找到 prep-v2 离线仿真副本（`txt-final-acceptance-prep-v2/offline/*/evidence/j1/`，pid=1、timestampMillis=1、runId=j1-offline-v2-001，**为离线驱动开发的仿真数据，非设备观测，不冒充旧批证据**）。旧批 j1_10 保存值 S、j1_20 重进读数无原件，**旧批不可补造**。

**[P1] 旧恢复结论纠正（前账缺失项如实记录，不事后补造）**

- 43 文件前账：`protected-files-list.txt` 只有文件名无 MD5；开工仅 DB/WAL/SHM 3 条 MD5 + 7 条 datastore 文件名。收工 `protected-files-after.txt` 42 行 + DB 1 行。**前账 MD5 不存在的 40 个文件无法宣称"前后一致"**，只能记为"收工在位 + 后账 MD5 在案"。
- 夜间 no→yes：本批 31 次 console 中仅 console6（18:02）出现 `settings_restored:[night_mode]`（该次运行 baseline 已是 yes），**无任何本批 uimode 写命令记录**；runs/baseline.json 被最后一次运行覆盖，17:38（no）→20:30（yes）之间的变更者无法从旧批工件归因。**不采纳旧报告"系 Codex 核查在批次间恢复"的归因，也不归因任何特定窗口**；事实仅为：变更发生在旧批会话期间的某个未记录时点，本批开工即 yes。
- 原 APK 对：前账 `e82904cc`/`fdbd0190` 与收工读回一致成立（旧批此项已核，本批复核一致）。

### 二、修正交付（离线）

- **修正路径版源码清单**：[freeze/source-sha256-corrected.json](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/freeze/source-sha256-corrected.json>)（982 项）。修正 `androidTest/com/…` → `app/src/androidTest/java/com/…`（2 项）；逐条对旧批 android 副本实算，**发现旧清单还有第 3 处缺陷**：`TxtFinalAcceptanceJ1Test.kt` 内容 SHA=555d86d2（17:36 冻结）≠ 实际 b29486d2（18:32 重建后），旧清单未随重建再冻结。修正清单中该项 actual_sha=b29486d2 并保留 old_sha 对照。旧清单原件未覆盖。
- 接缝修正（仅本任务 driver 副本，`driver/j1_ui_callback.py` SHA `308b865d…`）：`_harvest_observations` 改为从外部目录 `ls`+`cat`（run-as 不通时回退 shell 直读），**rc≠0/空输出/JSON 不可解析一律抛错判证据失败**，删除旧版 `except: pass` 静默吞错。生产 Kotlin 与 prep-v2 合同/谓词逻辑零改动。
- 新批后缀唯一差异：`app/build.gradle.kts` `applicationIdSuffix` `.qahosttxtv4` → `.qahosttxtevir1`（982 项清单中仅此 1 项内容差异，见 [freeze/source-sha256-evir1.json](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/freeze/source-sha256-evir1.json>)）。

### 三、最小新批复现（设备实测事实）

**环境插曲**：MuMu 实例 1 首次启动 VERR_UNRESOLVED_ERROR（VBox 错误 1455=提交内存不足，VBox.log 在案），完全重启后成功；期间误启实例 0 已即时关闭，**实例 0 未执行任何 adb 操作**。设备占用核查开工时 `adb devices` 为空、无 MuMu 进程，未影响任何并行窗口。

**绑定（全部成立）**：

- 新 UID `com.creationreadingassistant.qahosttxtevir1(.test)` 安装前双重缺席确认（pm path rc=1×2 + 成功包列表 grep=0，[uid-absence.txt](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/evidence/raw/uid-absence.txt>)）。
- `assembleQahost`+`assembleQahostAndroidTest` EXIT=0（[build1.log](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/build-logs/build1.log>)）；冻结 host `9f568fa1…`、test `256a279c…`。
- 驱动自管安装后 postinstall 读回 SHA：host/test 均与冻结一致（本批**安装读回=冻结声明**，绑定闭合；先行手动安装验证的读回亦一致，[install-verify.txt](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/evidence/raw/install-verify.txt>)）。

**约定基线（开工实读）**：settings 7 项（window_anim 0.5 / transition 0.5 / animator null / stay_on 0 / screen_off 60000 / rotation 0 / **night=yes**，yes 为旧批遗留继承态、本批 0 条 uimode 命令，如实记录）+ wm 1080x1920@280 + forward/reverse 空；**43 个 protected 文件逐文件 MD5 完整前账**（[baseline-43files-md5.txt](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/evidence/raw/baseline-43files-md5.txt>)）+ DB 本体 `faacca75…`/WAL 0/SHM `b7c14ec6…`；protected 原 APK 对 pull SHA `e82904cc`/`fdbd0190`；装前进程空、topResumed=LawnchairLauncher。

**最小流程（run_id `evir-min-20261006`，严格驱动两层判定）**：

| 场景 | 判定 | 实测 |
|---|---|---|
| j1_00_seedFixture | PASS | 夹具 seed 成功（10390 字符/4 单元） |
| j1_10_recordExitSaved（真实 UI 滚动→退出保存） | **PASS** | **真实观测 JSON 本批成功取回**（[harvest/j1_10_recordExitSaved.json](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/runs/evir-min/harvest/j1_10_recordExitSaved.json>)）：`savedOffset=9855`、unit=3 [9129,10390]、`expectedRestoreOffset=9129`、期望可见标记 `CRAJ1P0132`；rawProgressJson 含 locator_v2；真实 DB 进度行，非直写 |
| j1_20_reentryFromHome（首页入口重进） | **FAIL** | `重进偏移必须 > 0（清零必须拒绝），实际=0`；UI dump（[ui_reentry_home-1927821.xml](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/runs/evir-min/raw/j1_ui/ui_reentry_home-1927821.xml>)）显示 `TXT · 全文 · 0%`——**清零在 TXT-v4 正确绑定的新批复现** |

- instrumentation 前后进程/页面状态区分：j1_20 前 callback dump 显示首页（`书架上的 1 本都还没开始读`，独立佐证首页"继续阅读"态未反映已保存的 9855）；重进后直接进书 0%。测试启动（instrument 01:38:38–41 / 01:38:59–02:04）与产品重进动作由 callback dump 链分层记录，状态变化归因可分。
- **j1_20 自身观测 JSON 本批亦未取回**：测试先 writeResult 再 assert（写成功了），但 j1_20 是末项，其后无人再进 callback harvest，restore 即卸载清目录——**harvest 时序缺口**（每项 before_item 才拉、末项观测无人拉）已定位，属取证接缝残余缺口，如实记录；j1_20 判定事实由 raw runner 断言链 + UI dump 双证充分支撑。修复（如 run_all 后 finally 里补一次 harvest）属驱动改动，超出本批"最小接缝修正"范围，留给后续批次。

**严格 checker**：`J1_BATCH_REJECTED`（预期——最小 3 场景不满足 prep-v2 8 项合同布局；8 项 step_* 缺 j1_15/30/40/60 属最小批设计使然，binding_task_id 期望值差异属绑定差异；`source_manifest_contains_new_tests` 系 checker 硬编码旧清单路径所致）。这些判定差异逐项如实记录，不冒充通过。

### 四、恢复与副作用（收工实读，[final-reconciliation.txt](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/evidence/raw/final-reconciliation.txt>)）

- 沙箱双包卸载（restore.json ok=true/verified=true；缺席=pm path rc1+grep 0 双确认）。
- **43 个 protected 文件逐文件 MD5 前账=后账全部一致**（[final-43files-md5.txt](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/evidence/raw/final-43files-md5.txt>) 对比 [baseline-43files-md5.txt](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/evidence/raw/baseline-43files-md5.txt>)）；DB `faacca75…`/WAL/SHM 不变。
- protected 原 APK 对后账 pull 实算 `e82904cc`/`fdbd0190` 与前账一致。
- 设置 7 项+wm+forward/reverse 与开工一致；夜间 yes 本批未动（继承态）。
- `/data/local/tmp` 本批 0 残留（旧批 cra-*.xml 历史遗留亦已不在）；`/sdcard/j1-ui.xml`（mtime 10-05 13:38，早于旧批 TXT 批次，属更早历史遗留）已删除。
- 收工进程空、无本产品进程残留。D 仓库 HEAD/暂存区/既有 WIP 前后一致。

## 原始证据

`E:\creation-reading-assistant\txt-failure-evidence-repair-v1\`：`freeze/`（双 APK、source-sha256-evir1.json、source-sha256-corrected.json）；`runs/evir-min/`（baseline.json、binding.json、restore.json、summary.json、run manifest、items×3、raw×3+sidecar、harvest×2、raw/j1_ui 22 xml+22 png）；`evidence/raw/`（uid-absence、baseline-settings、baseline-43files-md5、baseline-protected、baseline-apk-pair、pre-install-state、install-new-pair、install-verify、final-reconciliation、final-43files-md5、final-db-md5、evir-run-console×3、evir-run-exit、evir-check-exit、batch-sha256）；`evidence/`（installed-*-readback.apk、baseline-apk-*-before.apk）；`build-logs/build1.log`；`config.json`；`execution.json`；`driver/`（接缝修正副本）与 `patched-driver/`（byte-reuse 原件+补 j1_evidence_check.py）。

## 恢复与副作用

见上文四；无未清理副作用。本批测试数据仅存在于已卸载隔离包与 E 目录工件。

## 未覆盖项

- **根因待核项（不写入生产结论）**：①重进消费链断裂的具体代码位置（j1_10 保存 locator_v2 完整、重进读 0，中间环节——进程存续/DB 读回/UI 装载——何层丢弃未分层定位）；②首页"继续阅读"态与 DB 保存值不一致属新观察，未定位；③j1_20 重进为驱动 `am start` 路径，用户真实手势重进未单独区分。
- 旧批 40 个无前账 MD5 文件的"前后一致"永久不可补证（本批新前账已建立，后续批次可延续）。
- 旧批夜间 no→yes 变更者不可归因（工件不足），本批以"开工即 yes"事实封存。
- j1_30/40（书架/冷启入口）、j1_15/60、MD 邻接、末项 clamp、恢复等待阶段设备观测均未在本批重复（避免扩大批次）；J5 不重跑。
- checker 对最小批布局的适配、末项 harvest 时序修复属驱动改动，未实施。
- 全产品分母未核；实体手机/MIUI/发布维持既有边界。

## 交付状态与下一步

本批交付完成：旧批三处 P1 证据缺口全部定位并可解释（APK 绑定断裂时间线实锤、观测取回接缝根因、前账/归因不可补造项如实封存），修正清单与修正接缝已交付，最小新批在**正确绑定+完整前账**下运行——**j1_10 真实非零保存（S=9855）成立、j1_20 重进清零复现，TXT 重进清零缺陷在 TXT-v4 上维持"未修复"判定**，批次 FAIL，整批不写通过；约定恢复与前后账全部完成（43 文件 MD5 前后一致在本批首次实现）。

下一步归 Codex：①按本批双证（harvest j1_10 JSON + j1_20 UI dump/断言链）派 TXT 重进消费链根因定位任务（建议范围：保存→进程退出→重进装载的分层读回埋点，以及首页"继续阅读"态数据源）；②驱动末项 harvest 时序修复与 checker 最小批适配可并入下一批接缝清单；③修复后验收须沿用本批模式（新 UID+读回绑定+43 文件 MD5 前账）。D 集成/Git 不执行。
