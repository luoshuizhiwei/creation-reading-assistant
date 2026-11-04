# TXT 冷启动重进匹配对照 v1（A 同进程 / B 无仪器 force-stop / C 仪器重建）

- 任务编号/版本：`CRA-TXT-COLD-REENTRY-CONTROL-v1`；2026-10-06 指定，实际执行 2026-10-06T09:27–11:20+08:00，应用 ZCode / GLM（GLM-5.3-Flash）。角色=本批唯一 MuMu 所有者（另一窗口只修独立驱动脚本副本，本批未读取其进行中工件、未改公共驱动原件）。本轮为 MuMu 模拟器验收，不称实体手机/MIUI 通过。
- 输入指纹：D HEAD `e39a78da9c62f02614207cffb36969a32492eaea`（开工=收工）；源码=行级探针批 TXT-v4 同源副本，982 项清单 [source-sha256-coldre1.json](<E:/creation-reading-assistant/txt-cold-reentry-control-v1/freeze/source-sha256-coldre1.json>)，**与上批唯一差异=`app/build.gradle.kts` 后缀 hunk**（`.qahostrowprobe1`→`.qahostcoldre1`）；双 APK：host `d0eeb5651bbd90e6c6dfd8d8b49f2f215666543ac3232443e6b44384ee255a78`、test `c3775f118f01c0fca33079e5b6e36b9baaf66f085d9ff7ca3d375fab275bde68`，安装读回逐字节一致（[05-install-verify.txt](<E:/creation-reading-assistant/txt-cold-reentry-control-v1/evidence/raw/05-install-verify.txt>)）；fixture `15e6f965…` 字节复用；滚动模式（上下滚动）与保存目标单元（中间单元，避开短末项 clamp）沿用行级探针批方法。
- 所有权与改动：写入仅 `E:\creation-reading-assistant\txt-cold-reentry-control-v1\` 与本唯一报告。产品源码**零改动**；androidTest **零新增**（C 组仅复用既有 `j1_15_waitNoZero` 做进程重建对照，其内部读 j1_10 合同文件失败属预期——本批无该文件——失败路径无任何写入）；不 commit/push/reset/checkout；不触正式书库与实体手机；旧候选与证据只读。
- 环境：Windows / Git Bash；JDK 17.0.14；Gradle 8.9 `--offline`（隔离 gradle-home 复制自上批）。**构建插曲**：提交内存不足（页面文件固定 2G+MuMu 8.4G commit，宿主仅余 ~1.7G）致 4G/2G/1.5G 堆四次失败（hs_err 1455 在案），最终 `-Xmx1024m + kotlin in-process + --max-workers=1` 第五次成功——构建参数与本批证据有效性无关，如实记录。MuMu serial `127.0.0.1:16416`（实时核实）。

## 执行与结果

### 基线与流程

- 新 UID `com.creationreadingassistant.qahostcoldre1(.test)` 装前双重缺席（[00](<E:/creation-reading-assistant/txt-cold-reentry-control-v1/evidence/raw/00-uid-absence.txt>)）；基线 7 设置（**night=no，本批自始至终**）+wm+forward/reverse 空；protected 43 文件逐文件 MD5 完整前账；DB `faacca75…`；原 APK 对 `e82904cc`/`fdbd0190`；装前无本产品进程（[01–04](<E:/creation-reading-assistant/txt-cold-reentry-control-v1/evidence/raw/01-baseline-settings.txt>)）。
- seed 经既有 `j1_00_seedFixture`（唯一一次 instrumentation 数据准备）；binding 经 `cat>` shell 重定向写入 `/data/local/tmp/j1-accept/`（adb push 对该目录 fchown 拒绝，绕过方式记录于 console）；随后**全部对照组均为真实 UI 手势（input tap/swipe/keyevent）+ shell 只读取证（run-as sqlite3 SELECT / uiautomator dump / screencap）**，观测不依赖任何仪器。

### 匹配条件

三组共用同一 bookId=`j1-final-b1`、同一滚动模式（全局「上下滚动」）、同一保存目标（中间单元，swipe 链一致：书架→书卡→5s→n×swipe@300ms→back）；每组行级四时点：保存后（T0）、重启/重进后未开书（T1）、开书后 15s/30s（T2），另记录宿主 PID、页面 dump、app_settings.preferences_pb MD5（`81b4eeb1`，全程不变——模式设置持久化在所有组中稳定）。

### 对照结果总表（全部原始值在 [timeline/](<E:/creation-reading-assistant/txt-cold-reentry-control-v1/evidence/raw/timeline>) 32 个 meta + 40 dump/23 png）

| 组 | 条件 | 保存基线（T0 行） | T1 未开书 | T2 重进 15s/30s | UI 落位 | 判定 |
|---|---|---|---|---|---|---|
| **A 同进程** | PID 3563 全程存活；17 swipe→S=5054（48.64%）→back | `5054/48.64%/reading/NULL/02:05:02.552Z` | 首页停 10s 行不变 | 行**不变**（15s/30s） | **29%**（菜单头） | **通过** |
| **B 无仪器 force-stop** | 同条件重建（视口滚至 87% 但见下"保存冻结"节）；`am force-stop`→PID 3563 消失→普通 `am start` 新 PID 5529 | 行保持 `5054` | 冷启首页显示 **48%**（DB 值） | 行**不变**（15s/30s） | **29%** | **通过** |
| **C 仪器重建** | PID 5529 上 `am instrument`（j1_15）→旧进程被杀（PID 空）→新 PID 6315 | 行保持 `5054` | 首页 8s 行不变 | 重进 15s 行**不变** | — | **通过（行不丢）** |
| **E force-stop 两连复核** | PID 6315→6564→6663 两次 force-stop→启动→重进 | 行保持 `5054` | — | 15s 行**不变** | — | **B 可复现** |
| **F kill -9 硬杀** | `kill -9 6663`（无保存回调机会）→启动→重进 | 行保持 `5054` | — | 15s 行**不变** | **29%** | **通过** |
| **X 计划外整机重启** | A 组滚动中 MuMu 整机退出（02:44 前后），重启后行残留 `22.15%/01:43:31Z`（最后一次自动保存）；am start→首页卡片显示 22%→书卡重进 | （前置非本组建立） | 首页 22% | **行被覆盖为 `0.0/offset=0/02:01:35Z`**→30s 时 `0.08%/offset=8` | **0%** | **失败（清零复现）** |

### 结果分支归位（对照任务书四分支）

**本批落在「B 正常、A 正常、C 正常」+「X 单点失败」的组合**，即：`am force-stop`、`kill -9`、仪器进程重建三条"进程死亡"路径在**本批条件**下都不清零（任务书"B/C 均正常→说明旧失败尚未稳定复现，保留条件差异"分支，加上 X 组新增的条件差异）；**唯一复现清零的条件是 MuMu 整机重启（X 组，单次）**——其与 B/F 的关键差异：整机重启时应用进程被系统硬杀、且该次会话的最后一次行写入停在自动保存值 22.15%（01:43:31），重进打开时行被**覆盖写**为 0.0（updated_at=重进打开时刻），30s 后又出现 `0.08%/offset=8` 的微小回写。**X 组为单次观测、且该批未按任务书先建立组内非零保存基线（残留值来自中断前自动保存），故不据此裁定生产冷启动缺陷，只登记为条件差异**。

### 新发现：重进后会话保存冻结（A/B 一致、非冷启特有）

- A 组重进后（B 组实际流程中）会话：视口经 25 次真实 swipe 从 29% 滚到 **87%**（菜单头证据 [B3-menu.xml](<E:/creation-reading-assistant/txt-cold-reentry-control-v1/evidence/raw/timeline/B3-menu.xml>)），back 退出后 DB 行**完全不写**（冻结 5054，updated_at 仍是 02:05:02.552Z）。
- 该行为在 A（同进程重进）、B（冷启重进）、B17b/D（再次重开）会话中**一致复现**（D3：12 次大位移 29%→58%，行仍冻结）；而**首次会话**（seed 后首次开书，A 组 A2/A7 时点）滚动写行正常（2613→5241 实时更新）。
- 即：**一旦经历"退出→重进"边界，后续会话的滚动保存与退出保存全部失效**——这解释了旧批 j1_60（恢复后新位置保存）失败的机制方向，且与冷启动无关（A 组同进程即复现）。此为本批**新证实的独立缺陷方向**（首次被行级数据锁定），其"重进打开时消费了某状态后保存链被关断"的具体代码位置未定位（不写生产结论）。

### 其他记录

- **设备中断**：MuMu 实例 1 在 A 组滚动中意外退出（A3/A4 两时点工件为空，meta 已标注 NOTE；中断前最后一次行值 22.15% 在案），重启后接续 X 组。中断本身构成 X 组条件，非本批计划。
- C 组 `j1_15` 因缺 j1_10 合同文件 FAIL（本批无该文件），instrument 失败路径**零写入**（行前后一致），符合"观察器不得 seed/删除/upsert/改进度"约束。
- 菜单头百分比（29%）与 DB percent（48.64%）数值不等的语义差异在行级探针批已核（58%≈单元 2 起点），本批不再展开；本批只以"是否落在保存位置附近、是否为 0%"作落位判定。

## 原始证据

`E:\creation-reading-assistant\txt-cold-reentry-control-v1\`：`freeze/`（双 APK、982 清单）；`evidence/raw/`（00–09 编号工件：缺席/基线/43 文件 MD5/APK 对/装前/安装读回/seed/收工对账/后账）+`timeline/`（32 meta+40 dump+23 png，A3/A4 空工件带 NOTE）；`runs/`（binding-coldre.json、freeze-sha.json）；`build-logs/`（build1–5，含 4 次内存失败记录）；`execution.json`。

## 恢复与副作用

沙箱双包卸载（Success×2）+缺席双确认；`/data/local/tmp/j1-accept`（本批 binding/fixture）与 `/sdcard/crp.xml/crp.png`（本批 dump）已清；**43 文件 MD5 前账=后账全部一致**（[08](<E:/creation-reading-assistant/txt-cold-reentry-control-v1/evidence/raw/08-final-43files-md5.txt>) 对 [02](<E:/creation-reading-assistant/txt-cold-reentry-control-v1/evidence/raw/02-baseline-43files-md5.txt>)）；DB `faacca75…`/WAL/SHM 不变；原 APK 对后账 `e82904cc`/`fdbd0190` 一致（[09](<E:/creation-reading-assistant/txt-cold-reentry-control-v1/evidence/raw/09-final-apk-pair.txt>)）；设置 7 项+wm+forward/reverse 与开工一致（night=no 全程）；`/data/local/tmp` 其余历史遗留未动。D 仓库/Git 零写入。

## 未覆盖项

- **X 组清零（整机重启条件）为单次观测**，未在重建同条件（整机重启+残留自动保存值+首次重进）下复现验证；其与"未 checkpoint 的 WAL 状态"或"覆盖写路径"的因果未定位。
- **重进后会话保存冻结**的代码位置未定位（本批为诊断任务，不做产品修复）；"重进"边界是首页重进一种入口，书架入口/冷启入口的保存冻结是否同机制未分别验证（B 组冷启重进后已复现冻结，倾向同机制，未单列成组）。
- 短末项 clamp、MD 邻接、恢复等待阶段设备观测维持既有边界；J5 不重跑；旧批不可补证边界保持。
- 全产品分母未核；实体手机/MIUI/发布维持既有边界。

## 交付状态与下一步

本批交付完成：A/B/C/E/F 五条路径在**本批条件**下全部通过（同进程、无仪器 force-stop、kill -9、仪器重建均不清零，重进行保持、UI 落位正常），**旧失败未在匹配对照下稳定复现**；两项条件差异登记为待核：①X 组整机重启单点清零（条件特殊、未复现验证）；②**新证实**的"重进后会话保存冻结"（A/B 一致复现，非冷启特有，旧批 j1_60 失败的机制方向）。二者均为行级/时点证据，未做任何生产修复。下一步归 Codex：按这两项分别派窄任务——①重建整机重启条件的复现批（含 WAL 状态取证）；②"重进边界后保存链关断"的代码定位（可在源码静态追 + 少量设备复现）；产品修复等 Root 核定后再派。D 集成/Git 不执行。
