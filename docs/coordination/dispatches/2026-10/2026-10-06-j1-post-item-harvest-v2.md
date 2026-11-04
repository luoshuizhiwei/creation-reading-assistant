# J1 逐项采集身份窄返修（after_item v2）：离线实现与验证

## 任务编号/版本
CRA-J1-POST-ITEM-HARVEST-v2；2026-10-06 交付。**离线验证（14/14 场景），设备未运行**。

## 输入指纹
- v1 基线（已审 1P2）：[j1-post-item-harvest-v1](<E:/creation-reading-assistant/j1-post-item-harvest-v1>)（joint_driver `d9d93b1e…`、j1_harvest `d828f2ba…`、j1_run `599f88ad…`、runner `a634a344…`、checker `157562b2…`）。
- Root 反例：[stale-only-repro.json](<E:/creation-reading-assistant/rowprobe-harvest-codex-review-20261006/stale-only-repro.json>)——CURRENT-RUN + 仅 j1_00/OLD-RUN 文件，v1 生产采集返回 status=ok/problems=[]（Root P2）。
- 真实 writeResult 字段合同：evir 批真实观测 [j1_10_recordExitSaved.json](<E:/creation-reading-assistant/txt-failure-evidence-repair-v1/runs/evir-min/harvest/j1_10_recordExitSaved.json>)（step/pid/packageName/taskId/runId/configSha256/fixtureSha256/payload）。
- 依据：[2026-10-06-rowprobe-harvest-codex-review.md](../../reviews/2026-10/2026-10-06-rowprobe-harvest-codex-review.md)、[handoff/current.md](../../../handoff/current.md)。

## 所有权与改动
所有权目录 [j1-post-item-harvest-v2](<E:/creation-reading-assistant/j1-post-item-harvest-v2>)；唯一本报告。v1 目录只读（未覆盖），公共驱动/repair-v1 原件/产品/Git/全局交接零写入；未用 ADB、未构建 APK。冻结清单与逐文件 SHA 见 [CHANGE-FREEZE.md](<E:/creation-reading-assistant/j1-post-item-harvest-v2/CHANGE-FREEZE.md>)（全部经程序化复核）。

## 环境
Windows/Python 3.13 本地离线；设备模型 fake adb（真子进程 + 事件 JSONL 顺序账），观测目录卸载后不可达=时序可验证约束；冻结 runner.py 经 default_runner_factory 真子进程参与全部场景。

## 执行与结果

### 窄返修内容（保留 v1 全部有效时序与合同）
时序不变：方法结束 → `_record_item`（原始判定先于采集）→ after_item 采集 → 下一项；末项后才 finally 恢复/卸载；无合同不触发；采集失败不阻塞清理；**原始方法 FAIL 不因采集成功升级 PASS**；完整八项 checker 不降低。本轮仅补：
1. **合同明确本 item 必需观测文件**（joint_driver.py +7 行）：`after_item.expect` 必填非占位（J1 = `<method>.json`，与生产 writeResult 落点一致）；其余驱动逻辑零改动（v1→v2 实测差异 7 行全为加法）。
2. **必需文件 + 身份校验**（j1_harvest.py 重写）：按 expect 定位本方法产物——目录中任意旧 JSON 不再当作本方法产物。必需三项 `step`==method、`runId`==cfg.run_id、`packageName`==cfg.host_package；按实际合同"有则核对"：taskId（cfg.task_id 存在即要求携带且相符）、configSha256/fixtureSha256（观测携带且期望可计算时核对，缺席记 absent 不判失败——不凭空要求不存在的字段）。记录新增 `expected{name,found,identity}`，文件条目带 `current_item_file` 标记。
3. **累积快照仍归档**旧步骤文件（诊断价值），但不满足本步骤必需证据；方法崩溃未产出本步骤文件 → status=failed + 明确缺失问题，原始失败保留、清理照常（不因取得旧快照标成完整成功）。
4. fake_adb_cli 仅离线替身：观测载荷按真实 writeResult 字段结构生成，`write_override`/`write=None` 支撑反例。

### 离线验证（14/14 PASS，原始输出/退出码落盘）
| 场景 | 判别点 |
|---|---|
| S0-root-stale-repro | **Root P2 反例直接复现并关闭**：生产采集函数对 CURRENT-RUN+仅旧 run 文件返回 status=failed、expected.found=false、问题点名 `必需观测文件缺失：j1_20_reentryFromHome.json`（v1 同输入实测 ok） |
| S1-normal-last | 全 PASS；身份六字段 verdict 全 ok（含 configSha256/fixtureSha256 有则核对正例）；采集先于卸载 |
| S2-fail-last | **当前文件正确+方法 FAIL：采集 status=ok、方法仍 FAIL**（反例 5） |
| S3-harvest-fail | 双档空取回 → failed；runner PASS 降级 FAIL；清理照常 |
| S4a/S4b | 无合同无回调 / 有回调无合同：既有调用合同兼容 |
| S5a/S5b | 崩溃已写观测 → 尽力采集 ok；崩溃未产出本步骤文件 → status=**failed**+明确缺失（不因旧快照标成功）；两者原始失败保留、清理执行 |
| S7-stale-only-pass | runner PASS、目录仅旧 run 文件 → 必需缺失降级 FAIL（Root 反例批内复现） |
| S8/S9/S9b | runId/step/packageName 各自不符 → 问题文本含期望/实际 → 降级 FAIL |
| S10-current-missing-others | 当前文件缺失但当前 run 旧步骤存在 → 必需缺失降级 FAIL；旧文件仍归档且 `current_item_file=false` |
| S11-entry-cli | `j1_run.py` 唯一入口子进程整链：overall PASS、采集先于卸载 |

调用顺序账（events.jsonl）与身份记录（`expected.identity` 六字段）均入证据；总账 [offline-verify-summary.json](<E:/creation-reading-assistant/j1-post-item-harvest-v2/offline/out/offline-verify-summary.json>)（`e2070602…`，verdict=PASS，failures=[]）。

## 原始证据
- 冻结清单：[CHANGE-FREEZE.md](<E:/creation-reading-assistant/j1-post-item-harvest-v2/CHANGE-FREEZE.md>)；v1→v2 逐行精确差异：[CHANGE-v1-to-v2.diff](<E:/creation-reading-assistant/j1-post-item-harvest-v2/CHANGE-v1-to-v2.diff>)（`d96cc9c2…`，joint_driver 仅 +7 行 expect 校验）。
- harness：[offline/verify_offline.py](<E:/creation-reading-assistant/j1-post-item-harvest-v2/offline/verify_offline.py>)；采集实现：[j1_harvest.py](<E:/creation-reading-assistant/j1-post-item-harvest-v2/j1_harvest.py>)。
- 逐场景证据树：`offline/out/<场景>/`（config/state/events/evidence 全树）。

## 恢复与副作用
无设备接触、无副作用；v1/公共原件/其他窗口文件零覆盖；未操作 Git 与全局交接/TODO。

## 未覆盖项
设备真机链未跑；真实 UI 旅程（before_item 动作）需真机；configSha256/fixtureSha256 的 mismatch 分支离线未触发（正例与缺席分支已覆盖，mismatch 逻辑与 runId 同构）；完整八项 checker 的设备证据未产生。设备 owner 的匹配冷启/仪器重建对照与本返修互不依赖、并行推进。

## 交付状态与下一步
1/1 交付：身份窄返修 + 14 场景离线回归 + 冻结差异与绑定说明（config 逐项 `after_item.expect` + 以本目录为驱动目录执行 j1_run.py）。交回 Codex 审核；新驱动审核通过后按 [CHANGE-FREEZE.md](<E:/creation-reading-assistant/j1-post-item-harvest-v2/CHANGE-FREEZE.md>) §4 绑定后续批次。根因未核定前不做产品修复，D/Git 暂停。
