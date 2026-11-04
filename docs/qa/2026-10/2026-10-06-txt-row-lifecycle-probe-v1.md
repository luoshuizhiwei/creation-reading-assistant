# TXT 进度行生命周期探针 v1（T0/T1/T2 行级观测）

- 任务编号/版本：`CRA-TXT-ROW-LIFECYCLE-PROBE-v1`；2026-10-06 指定，实际执行 2026-10-06T02:16–02:55+08:00，应用 ZCode / GLM（GLM-5.3-Flash）。角色=本批唯一设备所有者（另一窗口为独立驱动脚本副本，离线，未触碰设备与公共驱动原件；本批未读取其任何进行中工件）。本轮为 MuMu 模拟器验收，不称实体手机/MIUI 通过。
- 输入指纹：D HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工）；源码=上批已验 TXT-v4 副本（982 项清单 [source-sha256-rowprobe1.json](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/freeze/source-sha256-rowprobe1.json>)，与上批 evir1 清单**唯一差异=`app/build.gradle.kts` 后缀 hunk**）；双 APK：host `df501792eb8069f4e6f04055f71269e54c6058ae7f6599df07e53cfbf12d39fd`、test `aaabce1ca471d4e4e01612795a73ad88fde3a97d131a87e160afa6f212018609`，安装读回逐字节一致（[05-install-verify.txt](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/05-install-verify.txt>)、重装后二次读回 [08-reinstall-verify.txt](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/08-reinstall-verify.txt>)）；fixture `15e6f965…` 字节复用。
- 所有权与改动：写入仅 `E:\creation-reading-assistant\txt-row-lifecycle-probe-v1\` 与本唯一报告。产品源码**零改动**（无任何防清零逻辑、无新增诊断日志——任务书第 8 项未触发）；不 commit/push/reset/checkout；不触正式书库与实体手机；旧批证据只读。
- 环境：Windows / Git Bash；JDK 17.0.14；Gradle 8.9 `--offline`（隔离 gradle-home 复制自上批缓存）；MuMu serial `127.0.0.1:16416`（实例 1「洛水之蔚」，实时 `adb devices` 核实；`emulator-5556` 别名未使用）；设备 `/system/bin/sqlite3` 在（实测可用，全部行级观测用 `run-as … sqlite3 'SELECT …'` **只读**直查，未对任何库执行 checkpoint/写入；WAL 一致性：查询经 SQLite 正常打开（含 -wal），非复制件判读；protected 原库 MD5 开工=收工见后账）。

## 执行与结果

### 流程与观测方式

- 新 UID `com.creationreadingassistant.qahostrowprobe1(.test)`，装前双重缺席确认（[00-uid-absence.txt](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/00-uid-absence.txt>)）。
- **基线**（[01](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/01-baseline-settings.txt>)/[02](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/02-baseline-43files-md5.txt>)/[03](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/03-baseline-apk-pair.txt>)/[04](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/04-pre-install-state.txt>)）：设置 7 项（night=yes 继承态，本批 0 条 uimode）+wm+forward/reverse 空；protected 43 文件逐文件 MD5 完整前账；DB 本体 `faacca75…`/WAL 0/SHM `b7c14ec6…`；原 APK 对 `e82904cc`/`fdbd0190`；装前无本产品进程。
- 驱动批 `rowprobe-20261006`（j1_00+j1_10，驱动自管安装/恢复）跑通 seed；此后**改为全手动无扰动流程**：`am start` 开宿主 → shell `input tap/swipe` 真实手势 → `input keyevent 4` 真实 back。宿主 PID `11863` 从 T1 前至全部观测结束**全程未变未死**；instrumentation（seed）结束于观测开始前，T0/T1/T2 全部为纯 shell 只读观测（sqlite3 SELECT + uiautomator dump + screencap + `logcat -d`），**仪器启动与产品状态变化可完全区分**。
- j1_10 仪器跑法在本批未复现 PASS（见"偏差与边界"），真实滚动/退出保存全部由真实手势完成，且行为与生产一致（行随滚动更新、back 保存）。

### 行生命周期完整时间线（同一 bookId=`j1-final-b1`，全部原始值在 [timeline/](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/timeline>) 与 [timeline-summary.json](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/runs/timeline-summary.json>)）

| 阶段 | 观测点 | reading_progress 行（book_id/percent/state/deleted_at/updated_at/locator） |
|---|---|---|
| **A. 首次打开（分页模式）** | T1 首页已渲染未开书 | **行不存在（空）** |
| | T2 开书后 5s/15s/30s | 行被**创建**：`0.0 / reading / NULL / 18:35:36.777Z / offset=0`（updated_at 冻结在打开时刻，30s 内不再写） |
| | 分页模式 swipe×16 | 行**不变**（offset 恒 0；dump 显示「分页正文已就绪」，分页分支 swipe 无位移——既有接缝事实） |
| **B. 切滚动模式后重进** | R1 打开 5s | 行被**重写**：updated_at=18:40:06.640Z，仍 `0.0/offset=0` |
| | 真实 swipe×8 / ×16 / ×24 | 行**实时跟随**：offset 2613（25.15%）→ 5229（50.33%）→ 7852（75.57%），updated_at 每次滚动都在变（18:40:45→18:41:17→18:41:50） |
| | **T0：back 退出后 5s** | 行落定为 **`72.14% / reading / deleted_at=NULL / 18:41:54.327Z / offset=7495`**（退出保存写入了最终视口 offset=7495，小于最后一次观测的 7852，为 back 前实际停留位） |
| **C. 裁决性 T1** | 退出后首页停留 0s/5s/10s | 行**稳定不变**（7495/72.14%，三连测一致）；首页可见文案为「在读 1」+书卡（**无**「未开始」空态） |
| | **首页入口重进打开 15s/30s** | **DB 行保持 7495 不变**；轻点唤出菜单头显示 **`TXT · 全文 · 58%`**（非 0%），正文视口落在保存位置附近（E2 判定：**已落位**） |
| | 二次重进复核 12s | 行仍 7495；菜单头仍 58%——**可稳定复现，非偶发** |

### 语义对比与加载轨迹

- **裸 SQL vs 生产 getByBook**（[14-sql-semantics.txt](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/14-sql-semantics.txt>)）：全表仅 1 行；`deleted_at IS NULL`（`ReadingProgressDao.getByBook` 语义）返回该行 `72.14/reading`；`deleted_at IS NOT NULL` 为空；books 行 deleted_at 亦为 NULL。**本批时间线内不存在软删行、双行分裂或 percent/locator 分裂**——裸 SQL 与 getByBook 在本批实际数据上语义等价（两者返回同一非零行）。
- **E3 logcat**（[12](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/12-logcat-trace.txt>)/[13](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/13-logcat-chapter.txt>)）：三次重进均有 `TxtDocumentLoad: 2ms/1ms/1ms`（load 确实执行且极快）；`TxtChapterRead: 0ms`、`TxtFirstPageReady: 41ms`；`TxtChapterDetect` 无输出（单章全文路径无预检日志）。加载链正常，无超时/重载循环迹象。
- **卸载前最终观测取回**：宿主私有 `files/j1-accept/` 与外部 j1-accept 目录核对，seed JSON 取回并验证（runId/taskId/pid 一致、非空、可解析，`runs/probe/j1_00_seedFixture-final.json`）。T0/T1/T2 行级数据为 sqlite3 只读直查+文件落盘双份（meta+summary），无「卸载前才想起取回」的时序风险。

### 已证事实（对照假设）

1. **A1（软删）否证**：本批全程 reading_progress 行 `deleted_at` 恒为 NULL，无软删发生；首页也不显示「未开始」空态。首页「继续阅读」卡片存在且点击即以 58% 落位。
2. **A2（getByBook 过滤吞行）否证（在本批数据上）**：无 deleted_at 非空行可被过滤；getByBook 语义下该行可见。
3. **打开即建行 `0.0/reading`**：首次打开书 5s 内行被创建为 `0.0/offset=0`（updated_at=打开时刻）——这是**正常新书首开路径**（任务书第 9 项：不对正常新书 null 进度提议永久保持 pending；此处行为正常）。
4. **保存链正常**：滚动实时写行（offset 随 swipe 递增）；back 退出以最终停留位收口（7495）。
5. **恢复链正常（本批）**：首页重进后 DB 行未清零、UI 以 58% 落位——**与本批条件（宿主进程 11863 全程存活）一致**。
6. **与上批 j1_20 清零的差异指向进程边界**：上批三入口清零（含首页重进）时，j1_20 前宿主进程经历了 instrumentation 的 force-stop/start 循环；本批首页重进在**进程从未死亡**条件下不触发清零。**这把根因嫌疑收敛到"冷启动/进程重建路径的恢复消费链"，而非行被软删或查询过滤**。此为观测性收敛，非最终根因裁定——冷启动路径本批未测（见未覆盖项）。

### 偏差与边界（如实记录）

- 驱动批 j1_10 末次执行 FAIL（`退出保存偏移缺失`）：手动 `am start`+跳过引导在 seed 之后进行，与上批 driver 的 `cmd_uiprobe 内 dismiss` 时序不同，导致 j1_10 内部 UI 链滚动零位移（b0 两 dump 同 SHA）。此后改为全手动流程，且上批同源码同 fixture 已有 j1_10 PASS + S=9855 真实观测（[上批 harvest](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/runs/evir-min/harvest/j1_10_recordExitSaved.json>)），不影响本批主目标。
- 首次 am start 时 onboarding「跳过」按钮实测坐标 (141,1229)（上批 driver 用 tap_text 匹配，本批为 dump 解析坐标）；切「上下滚动」开关后 dump 显示附近节点 `checked=true` 生效。
- 第三次 back 链把 app 退到了桌面（launcher topResumed），属用户级 back 行为，无数据影响（收工账已核）。
- T0 落定值 7495 略小于最后一次滚动观测 7852：back 前最后一次惯性滚动回弹，退出保存记录的是实际停留位——与「保存真实视口」语义一致，非异常。

## 原始证据

`E:\creation-reading-assistant\txt-row-lifecycle-probe-v1\`：`freeze/`（双 APK、982 清单）；`evidence/raw/`（00–19 编号工件：缺席/基线/43 文件 MD5/APK 对/装前状态/安装读回/驱动 console/seed/j1_10/logcat/SQL 语义/最终状态/恢复/对账）+`timeline/`（21 观测点 meta+dump+png）；`runs/`（rowprobe 驱动批、probe 手动 binding、timeline-summary.json）；`build-logs/build1.log`；`config.json`；`execution.json`；`driver/`（上批接缝副本 byte-reuse `308b865d…`）与 `patched-driver/`（joint_driver `013ca2f9…`/runner `a634a344…`）。

## 恢复与副作用

沙箱双包卸载（Success×2）+缺席双确认；`/data/local/tmp/j1-accept`（本批 binding）已删，`/sdcard/j1p.xml/j1p.png`（本批 dump）已清；**43 文件 MD5 前账=后账全部一致**（[18](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/18-final-43files-md5.txt>) 对 [02](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/02-baseline-43files-md5.txt>)）；DB `faacca75…` 不变；原 APK 对后账 `e82904cc`/`fdbd0190` 一致（[19](<E:/creation-reading-assistant/txt-row-lifecycle-probe-v1/evidence/raw/19-final-apk-pair.txt>)）；设置 7 项+wm+forward/reverse 与开工一致；`/data/local/tmp` 其余遗留（2026.08/after_exit 等）为历史工件未动、未清、如实记录。D 仓库/Git 零写入。

## 未覆盖项

- **冷启动（进程重建）路径未测**：本批刻意保持宿主进程存活以完成无扰动观测；上批 j1_40（强停冷启）与 j1_20/30 在仪器循环下的清零是否同源于冷启动恢复链，需下一批在进程死亡/重建条件下重复本批 T0/T1/T2 观测方可裁定。
- 书架入口重进、Tom 了 locator_v2 `ci/co` 非 0 场景、MD 邻接、书尾 clamp 未覆盖；J5 不重跑。
- `TxtDocumentLoad` 与 UI 落位之间的 locator 消费代码路径未做静态追踪（留给 Root 核定）。
- 上批 j1_20 观测 JSON 仍不可追回（旧边界保持）；旧批 40 文件前账缺失不可补造（旧边界保持）。
- 全产品分母未核；实体手机/MIUI/发布维持既有边界。

## 交付状态与下一步

本批交付完成：T0/T1/T2 完整行级时点证据齐备（21 观测点、双份落盘、行生命周期从"无行→建行 0.0→滚动跟随→退出收口 7495→首页停留稳定→重进不丢行且 UI 落位"全程闭环），恢复与前后账全部通过。**已证**：本批条件（进程存活）下保存链、行写入、getByBook 语义、首页重进恢复全部正常，软删/过滤假设在实测数据上均否证；**根因嫌疑收敛**：TXT 重进清零只在"进程重建/冷启动"条件下复现（上批三入口清零均发生在 instrumentation force-stop 循环之后），但冷启动路径本批未测，**不据此裁定最终根因，不做产品修复**。下一步归 Codex：派冷启动（强停/进程死亡→重启）同规格 T0/T1/T2 行级探针批（可直接复用本批 probe 方法与时间线脚本），并按 Root 核定决定是否启动修复任务。D 集成/Git 不执行。
