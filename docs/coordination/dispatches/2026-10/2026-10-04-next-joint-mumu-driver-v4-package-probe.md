# CRA-JOINT-MUMU-DRIVER-v4 包缺席探测修订

日期：2026-10-04  
候选：`E:\creation-reading-assistant\next-round-joint-mumu-driver-v4`（由 v3 复制；v3 保留未改）

## 修订与判定

`Adb.package_path` 保留 `pm path` 成功时返回的 APK 路径。若 `pm path` 以非零退出且 stdout、stderr 均为空，才执行 `pm list packages <完整包名>`；只有退出 0、stderr 为空、输出格式可解析，且输出中没有与目标完整包名完全相等的行时，才返回 `absent`。近似包名不能命中。权限/诊断输出、list 失败、不可解析输出或 list 中仍有目标包均返回 `inconclusive`。后者不把矛盾的“清单存在、pm path 无 APK 路径”解释为已安装；设备检查会 fail closed。

恢复保护未变：`run_restore` 要求 baseline run_id 与配置匹配，host/test 包和数据基线都明确为 absent，否则在任何 force-stop/uninstall 前拒绝。安装后 APK 路径仍由 `pm path` 提供。

Root 提供的本轮只读 MuMu 事实按中文报告字段记录如下（不是本智能体现场设备测试）：

| 中文字段 | 事实 |
|---|---|
| 目标包名 | `com.creationreadingassistant` |
| `pm path` 结果 | 退出码 1；stdout 空；stderr 空；未返回 APK 路径 |
| 回退包清单结果 | `pm list packages com.creationreadingassistant` 退出码 0；列出 `com.creationreadingassistant.qahost` 与 `com.creationreadingassistant.qahost.test` |
| 完整包名精确匹配 | 清单无 `com.creationreadingassistant` 完整项；两个 qahost 名称不算精确命中 |
| 包状态 | 依 fallback 判定为 `absent`；数据目录状态未由上述 Root 事实给出，须由现有独立数据目录探针确认 |
| 中文结论 | 正式包缺席已由成功的完整包名清单确认；原 qahost 与 qahost.test 是本次 protected 包 |
| 保护动作 | v4 模板仅将 `qahost`、`qahost.test` 放入 `protected_packages`；不要求不存在的正式包为 installed |

## 离线验证

使用 `D:\develop\Python\Python313\python.exe -B`，`TEMP` 与 `TMP` 均设为候选 `_task_tmp`。

绿色：`python -B offline_test.py --out <v4 _task_tmp\green-v4-final.json>`，退出码 0，**55 项 / 0 失败 / 0 跳过**。

红色对照：`python -B offline_test.py --against E:\creation-reading-assistant\next-round-joint-mumu-driver-v3 --out <v4 _task_tmp\red-v3-package-probe.json>`，退出码 0 表示恰好命中新增加的四个 v4 反例；**55 项 / 4 失败 / 0 跳过**。用例覆盖 rc=1 空输出后成功列表确认缺席、近似包名精确排除、权限错误禁止空列表掩盖、list 失败/无法解析/仍列出目标时 fail closed，以及成功 `pm path` 仍返回 APK 路径。

runner SHA-256 未变：`a634a344f0d8a8c0bf8d14ddee93518551fa5577f2950b61ab77445549c2167e`，符合原合同。v4 驱动与测试 SHA-256：

| 文件 | SHA-256 |
|---|---|
| `joint_driver.py` | `72c38e5b3d2fbe63bb9134e3635d368066c508f2172d25e5b3fa11b145ef6b61` |
| `offline_test.py` | `4bbf5527774149a3bb01d8c3a32c52e28222b88491fb4257c787d280d9b722e6` |
| `runner.py` | `a634a344f0d8a8c0bf8d14ddee93518551fa5577f2950b61ab77445549c2167e` |
| `runbook.md` | `544eb2667cb8ecd961b3b409424883821cc975d706df0d7f71be25c0fdcdae01` |
| `config.template.json` | `c9a7cc08676f92bd765e0125952a82dd03c0967ed7e0c0416fc4f829da5c9dcf` |

## 边界

本轮没有连接设备、构建 APK、安装/卸载包或修改产品代码；没有对 Root 的设备事实自行补充实体测试结论。v3 原件保留不动。报告已按“目标包名、pm path 结果、回退包清单结果、包状态、数据目录状态、中文结论、保护动作”约定填写；设备完整验收仍由 Root 按独占设备流程继续。
