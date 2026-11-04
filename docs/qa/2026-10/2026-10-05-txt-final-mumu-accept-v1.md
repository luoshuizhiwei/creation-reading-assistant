# TXT 最终 MuMu 验收 v1（TXT-v4 绑定批）

- 任务编号/版本：`CRA-TXT-FINAL-MUMU-ACCEPT-v1`；2026-10-05 指定，实际执行 2026-10-05T17:27–20:40+08:00，应用 ZCode / GLM（account:zai-start-plan / GLM-5.3-Flash）。角色=本批候选构建、公共配置与 MuMu 唯一所有者。**本轮为 MuMu 模拟器验收，不称实体手机/MIUI 通过。**
- 输入指纹：核查 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工）；暂存区前后 0 条。产品输入 [TXT-v4 候选](<E:/creation-reading-assistant/txt-reentry-fix-v4/android>)（[source-sha256.json](<E:/creation-reading-assistant/txt-reentry-fix-v4/freeze/source-sha256.json>) 980 项复制后逐文件实算 980/980 匹配，零漂移）；验收工件 [prep-v2](<E:/creation-reading-assistant/txt-final-acceptance-prep-v2/>)（20 工件 SHA 与其 manifest 一致）；严格驱动 [patched-driver](<E:/creation-reading-assistant/unified-offline-binding-v1/patched-driver>)（joint_driver `013ca2f9…`、runner `a634a344…` 字节复用）。
- 所有权与改动：写入仅 `E:\creation-reading-assistant\txt-final-mumu-accept-v1\`（android 候选、freeze、evidence、config、runs、driver 副本、build-logs、gradle-home、scripts、execution.json）与本唯一报告 [docs/qa/2026-10/2026-10-05-txt-final-mumu-accept-v1.md](2026-10-05-txt-final-mumu-accept-v1.md)。D 产品源码/prep-v2 工件/patched-driver 原件/另一窗口集成清单零改动；不 commit/push/reset/checkout；不触正式书库与实体手机。
- 环境：Windows / Git Bash / Python 3.13；JDK 17.0.14；Gradle 8.9 `--offline --no-daemon`（qahost 变体显式 `-PcraAndroidTestBuildType=qahost`）；隔离 Gradle home 3.1G（不与并行窗口共享）；MuMu serial `127.0.0.1:16416`（本轮实读 adb devices，`emulator-5556` 为同 VM 别名未使用）。

## 执行与结果

### 绑定与构建

- **后缀/UID**：新唯一 `.qahosttxtv4`（host=`com.creationreadingassistant.qahosttxtv4`，test=`.test`）。安装前**双重确认缺席**：`pm path` rc=1（两包）+ 成功 `pm list packages` 精确 grep 计数 0（[uid-absence.txt](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/evidence/raw/uid-absence.txt>)）。
- **构建**：`assembleQahost` + `assembleQahostAndroidTest` EXIT=0；冻结宿主 `3211482225ee…`、测试 `5c516b4d33b6…`（含两次重建：①prep-v2 测试源曾落错路径致 `ClassNotFoundException`，移正位重建；②chapter 合同断言修正后重建）。源码冻结 [source-sha256.json](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/freeze/source-sha256.json>)（982 项 = v4 980 + prep-v2 两测试源 + build.gradle 后缀 hunk）；[v4-delta.json](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/freeze/v4-delta.json>) = 恰 3 处（两测试源 + 后缀）。instrumentation 安装读回三元组核实；**安装后 APK pull 读回 SHA 与冻结一致**。
- **prep-v2 接入**：`J1RecoveryContract.kt`/`TxtFinalAcceptanceJ1Test.kt` 复制到 androidTest；夹具 `fixture.txt`（SHA `15e6f965…` 与 payload 记录一致）；driver 副本 `j1_run.py`/`j1_ui_callback.py`/`j1_evidence_check.py`。
- **接缝修正（仅本任务副本，逐项记录）**：①驱动 ui_probe 在 postinstall 后、app 未启动时执行 → `j1_run.py` 包裹 `cmd_uiprobe`，probe 前经 callback `start()`+dismiss 首启引导；②首启按钮实测为「跳过」（旧词「跳过并开启」保留回退）；③prep-v2 假设 `chapters.isEmpty()` 与生产 TxtChapterDetector 合同（识别不出→单章「全文」，永不为空）矛盾 → 断言修正为 `chapters.size==1`；④scoped storage 下 push 到 `/sdcard/Android/data/<pkg>` 对 app 自身不可见（实测 Permission denied）→ 绑定改推 `/data/local/tmp/j1-accept` + 测试改读该路径；⑤dump 镜像唯一化（修 evidence hash 覆盖误判）。**生产 Kotlin 与 prep-v2 合同/谓词逻辑零改动。**

### 设备基线（开工实读，[baseline.txt](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/evidence/raw/baseline.txt>) / [baseline-apk-files.txt](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/evidence/raw/baseline-apk-files.txt>)）

- 设置：window_anim 0.5 / transition_anim 0.5 / animator null / stay_on 0 / screen_off 60000 / rotation 0 / wm 1080x1920@280；**夜间 no（本轮基线）**；forward/reverse 空。
- protected `qahost(.test)`：两包在位（pm path 原始路径记录）；**原 APK 对 pull 实算** host `e82904cc…` / test `fdbd0190…`；43 个文件逐文件 md5 全量前账（DB 本体 md5 `faacca75…`、1,138,335,744 字节、wal 0、shm 32768）。

### J1 真实回归（唯一入口 `j1_run.py` 串行执行；两层判定）

run_id `j1-txtv4-20261005-a`；驱动批次汇总 **FAIL**（[summary.json](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/runs/j1-txtv4-20261005-a/summary.json>)，restore `ok=true/verified=true`）：

| 场景 | 判定 | 实测 |
|---|---|---|
| j1_00_seedFixture | PASS | 夹具（10390 字符 / 4 阅读单元）seed 成功；unit 表 [0,0,3025][1,3025,3036][2,6061,3068][3,9129,1261] 落盘 |
| j1_10_recordExitSaved（真实 UI 滚动→退出保存 S） | **PASS** | 分批滚动至段落 132+（ui_scrolled_b0/16 证据）；instrument 断言 savedOffset>0 通过（真实 DB 进度行，非直写） |
| j1_15_waitNoZero（首页/书架停留不清零） | PASS | 持久保持覆盖成立（wait_persistence_hold 观测在案） |
| j1_20_reentryFromHome（首页入口重进） | **FAIL** | 重进后偏移=0（清零复现） |
| j1_30_reentryFromShelf（书架入口重进） | **FAIL** | 同上 0 |
| j1_40_reentryColdStart（强停冷启重进） | **FAIL** | 同上 0 |
| j1_60_newPositionSaved（恢复后新位置保存） | **FAIL** | 新保存偏移=0（前置恢复失败连锁） |
| j1_90_recordFinalState | PASS | 终态记录完成 |

**核心产品结论：TXT 重进清零缺陷在 TXT-v4 生产代码上仍复现（j1_20/30/40 三条入口一致回 0，非驱动伪影）**——v4 修复的布局等待/clamp/keys 接入未覆盖该路径的真实根因；j1_10 保存非零 S 成立，清零发生在重进消费链（与 unified v1 批一致）。原始 runner 输出、sidecar、dump/png、DB 观测齐备。

### MD 邻接烟测

MD 阅读器可达（第 1 章/0.0%），但**滚动位移经 UI 未能达成**（RenderMarkdownUnit 渲染文本对 uiautomator 不可见 + 夹具单章 8 次 swipe 无位移证据），DB 进度行保持 0.0 → 判 **NOT_PROVEN**（前置条件未达成）；J2 原有 PASS 证据（canonical 1894，unified v1 批）保留原有效力，不据此宣称 MD 受影响或不受影响。

### 恢复对账（收工实读）

沙箱双包已卸载（`Success`×2；缺席=pm path rc1+成功包列表 grep 0 双重确认）；**protected 原宿主 APK 对 SHA 前后一致**（host `e82904cc…` pull 比对）；43 个 protected 文件**逐文件 md5 全量前账+后账一致**（[protected-files-after.txt](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/evidence/raw/protected-files-after.txt>)，DB 本体 md5 `faacca75…` 不变）；设置 7 项+wm 全一致；forward/reverse 空；本批 tmp 文件已清（`/data/local/tmp/j1*`/`cra-*` 中本批产物 0 残留，pre-dating `cra-s04-ui.xml`/`cra-ui.xml` 属历史遗留如实记录）；**夜间模式基线 no → 收工读数 yes**（与 Codex 核查恢复值一致，如实记录为本批未动的继承态）。

### 严格 checker 分层判定

`j1_evidence_check.py`（退出码 1）：**verdict=`J1_BATCH_REJECTED`**，`driver_batch=NOT_PASS`（4 项 FAIL）、`j1_behavior=REJECTED`、`coverage.wait_recovery_phase=NOT_RUN`（首页停留不冒充恢复等待阶段）。`binding_task_id` 失败系 taskId 绑定为本批次编号（checker 期望 prep-v2 id），属预期绑定差异，如实记录。

## 原始证据

`E:\creation-reading-assistant\txt-final-mumu-accept-v1\`：`freeze/`（双 APK、source-sha256.json 982 项、v4-delta、apk-sha256）；`runs/j1-txtv4-20261005-a/`（run manifest、items×8、raw×8+sidecar、baseline.json、restore.json、summary.json、ui/probe.xml、harvest）；`evidence/raw/`（基线/安装/uid 双确认/console×N/对账，[final-reconciliation.txt](<E:/creation-reading-assistant/txt-final-mumu-accept-v1/evidence/raw/final-reconciliation.txt>)）；`evidence/raw/j1_ui/`（45 份 dump+png）；`build-logs/`；`execution.json`；`driver/` 副本与 `scripts/`。

## 恢复与副作用

沙箱双包卸载+缺席双确认；protected 原宿主 43 文件逐文件 md5 与原 APK 对 SHA 前后一致；设置/wm/forward 全对账一致；夜间读数 yes 与开工 no 的差异系 Codex 核查在批次间恢复（本轮 0 条 uimode 命令），如实记录；D 仓库/Git 零写入；本批测试数据仅存于已卸载隔离包。

## 未覆盖项

- **J1 产品缺陷未关闭**：TXT 重进清零在 TXT-v4 上仍复现；v4 修复（布局等待/clamp/keys）不构成该路径根因修复——需新任务在 v4 候选上按本轮证据（三入口一致 0 + j1_10 非零 S + ui_scrolled 段落证据）重新定位重进消费链。
- 阅读器重进**落位前恢复等待阶段**的设备观测仍 NOT_RUN（合同明确不用首页停留冒充）。
- MD 邻接 NOT_PROVEN（本轮前置位移未达成）；J5 已验不重跑。
- 末尾短单元 clamp 真实回归（j1 夹具为 4 单元无书尾 clamp 场景）未覆盖——J3 既有分页证据不延伸至此。
- 全产品分母未核；实体手机/MIUI/发布维持既有边界。

## 交付状态与下一步

构建冻结+真实 J1+恢复对账+checker 分层判定完成：**批次 FAIL（J1 清零复现），整批不得写通过**。本轮到此停止；保存全部失败证据并完成恢复。下一步归 Codex：按本轮根因证据（重进消费链、三入口一致）派 TXT 恢复链新定位任务；通过前 TXT-v4 候选不进入集成合流。
