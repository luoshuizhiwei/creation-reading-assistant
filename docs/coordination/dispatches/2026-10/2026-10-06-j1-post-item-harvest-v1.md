# J1 逐项结束采集接缝（after_item）：离线实现与验证

## 任务编号/版本
CRA-J1-POST-ITEM-HARVEST-v1；2026-10-06 交付。**离线通过、设备未运行**。

## 输入指纹
- 调用链只读输入：[txt-failure-evidence-repair-v1/patched-driver](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/patched-driver>)（joint_driver.py `013ca2f9…`、runner.py `a634a344…`，与公共原件 [unified-offline-binding-v1/patched-driver](<E:/creation-reading-assistant/unified-offline-binding-v1/patched-driver>) 实算一致）与同任务 `driver/`（j1_run.py `8713a9fa…`、j1_ui_callback.py `308b865d…`）。
- 前序依据：[2026-10-06-txt-evidence-cause-codex-review.md](../../reviews/2026-10/2026-10-06-txt-evidence-cause-codex-review.md)（新批 S=9855 方向成立；末项 j1_20 观测在卸载前漏取为本任务缺口）。

## 所有权与改动
所有权目录 [j1-post-item-harvest-v1](<E:/creation-reading-assistant/j1-post-item-harvest-v1>)；唯一本报告。未用 ADB、未构建 APK、未改公共驱动原件/repair-v1 目录/产品源码；未操作 Git 与全局交接/TODO。**占用披露**：目录内存在 2026-10-06 02:2x 的同任务中断 WIP（joint_driver/j1_harvest/j1_run/fake_adb_cli 改件与 _bat_test 痕迹），本窗口按"保护既有 WIP"沿用其接缝实现收尾；本窗口曾误建的重复子目录副本已在同会话内移除。改动清单与逐文件 SHA 见 [CHANGE-FREEZE.md](<E:/creation-reading-assistant/j1-post-item-harvest-v1/CHANGE-FREEZE.md>)。

## 环境
Windows/Python 3.13 本地离线验证；设备模型为 fake adb CLI（真子进程，事件 JSONL 记账），观测目录在卸载后不可达=时序可验证约束。runner.py 以冻结字节经 default_runner_factory 真实子进程参与全部场景（normal_gate/命令绑定原样生效）。

## 执行与结果

### 接缝语义（交付核心）
执行序改为：方法结束 → `_record_item`（原始判定先于采集）→ **after_item 采集接缝** → 下一项；末项结束后才进 finally 恢复/卸载——即**方法结束→归档观测→恢复/卸载**，采集在 run_all 内部完成，"run_all 返回后才采集"不再出现。覆盖正常方法、断言失败（FAIL 保留且照常采集）、最后一项、异常路径（`_harvest_interrupted_item` 尽力采集并记 `summary['interrupted_after_item']`）。采集失败（非零/空输出/非法 JSON/回调违约/哈希不符）显式记入 `items/<id>.json` 的 `after_item.harvest`（status=failed+problems）并由 `cmd_report` 把该项降级 FAIL（runner PASS 也不得通过），不阻塞 finally 恢复/卸载。合同驱动：config 逐项 `after_item` 段（action=`harvest_j1_accept`、evidence_required=true）；无合同不触发、有合同无回调入口拒绝——既有调用合同兼容。

### 离线验证（8/8 PASS，全走生产调用链）
| 场景 | 结论 |
|---|---|
| S1-normal-last | 全 PASS；末项 instrument(42)→ls(43)→cat(44-46)→force-stop(47)→uninstall(48-49)，采集严格先于卸载；timeline `item→after_item→restore` |
| S2-fail-last | 末项 FAIL 保留 + 其采集 status=ok + 整体 FAIL + 恢复 ok |
| S3-harvest-fail | 注入双档空取回 → status=failed 且 problems 明确；**runner PASS 仍降级 FAIL**；清理照常执行 |
| S4a/S4b | 无合同无回调 / 有回调无合同：行为与原驱动等价，接缝不触发 |
| S5a/S5b | 方法崩溃（观测已写/未写）：尽力采集成功归档 / 快照如实不含未产出文件；`original_failure` 保留；恢复均执行 |
| S6-entry-cli | 经 `j1_run.py` 唯一入口子进程整链（serial 绑定、双回调注入）：overall PASS、采集先于卸载 |

### 验证中发现并修复
1. **驱动真缺陷（前序 WIP 遗留）**：`current` 相位字典在 try 内迟到初始化，precheck/基线/安装段抛错时异常路径守卫触发 `UnboundLocalError`——已提前初始化并纳入冻结改动（离线 S1 首跑实测）。
2. harness 三处缺陷（工厂签名误包层、观测渐进写模型、S3 注入被 run-as 回退击穿）修正后 8/8 通过；fake 注入改为按次计数以命中双档回退。

## 原始证据
- 冻结清单/绑定说明：[CHANGE-FREEZE.md](<E:/creation-reading-assistant/j1-post-item-harvest-v1/CHANGE-FREEZE.md>)（joint_driver `d9d93b1e…` 改 / runner.py `a634a344…` 冻结字节 / j1_harvest `d828f2ba…` 新 / j1_run `599f88ad…` 改；checker `157562b2…` 未改）。
- 离线总账：[offline-verify-summary.json](<E:/creation-reading-assistant/j1-post-item-harvest-v1/offline/out/offline-verify-summary.json>)（8 场景、failures=[]）；逐场景 `offline/out/<场景>/`（config、fake-device-state、events.jsonl 事件顺序账、evidence/raw+items+harvest+baseline+restore+summary、result 断言）。
- harness：[offline/verify_offline.py](<E:/creation-reading-assistant/j1-post-item-harvest-v1/offline/verify_offline.py>)；设备模型：[fake_adb_cli.py](<E:/creation-reading-assistant/j1-post-item-harvest-v1/fake_adb_cli.py>)。

## 恢复与副作用
无设备接触、无副作用。所有权目录内的前序 WIP 痕迹（`_bat_test/`、`__pycache__/`）保留未动；本会话误建的重复子目录已自行移除并在此披露。旧证据、公共原件、repair-v1 目录零改动。

## 未覆盖项
设备真机链未跑（本批不依赖本窗口进行中工件）；真实 UI 旅程回调（j1_ui_callback before_item 动作）仅合同兼容验证，动作本身需真机；完整八项 checker 的设备证据未产生（checker 未降低、未重跑）；`ls` 注入故障场景未单列（取回失败路径已覆盖）。

## 交付状态与下一步
1/1 交付：接缝实现 + 冻结清单 + 8/8 离线回归 + 调用顺序证据 + 下一批绑定说明（config 逐项 after_item 合同段 + `python j1_run.py --config … --serial … --ui-callback …`，以本目录为驱动目录）。交回 Codex 审核；新驱动审核通过后方可绑定后续批次，设备 owner 的 T0/T1/T2 行级观测可与其并行但互不读取进行中工件。产品修复等 Root 核定原因后另派；D 集成/Git 不执行。
