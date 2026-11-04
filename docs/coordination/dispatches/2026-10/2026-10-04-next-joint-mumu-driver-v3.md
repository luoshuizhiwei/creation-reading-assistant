# CRA-JOINT-MUMU-DRIVER-v3 交付记录

日期：2026-10-04  
隔离候选：`E:\creation-reading-assistant\next-round-joint-mumu-driver-v3`  
输入：v2 副本；v2 目录未改。所有权范围仅为 v3 的 driver/offline test/runbook/config 与本报告。

## 本轮修复

- `cmd_report` 不再信任 item JSON 自报的 PASS：逐项重读 raw 和 runner sidecar，复用原 `runner.normal_gate`，绑定 config 声明的完整命令/class/method/测试包/runner；检查 sidecar 必需字段、时间戳、ADB exit、runner 进程 exit、结果值及 item 记录一致性。缺文件/字段/exit、raw 有 `FAILURES!!!`、方法不符或证据矛盾均不能 PASS。
- run manifest 精确绑定 config SHA、合同 id/class/method/`before_item` 和 `run_id`；item/restore 必须具备相同 run_id。旧 item raw/sidecar/记录文件存在时，`run_all` 在任何设备动作前拒绝覆盖。
- `before_item` 为可选真实 UI 前置动作合同。缺少注入回调时在 ADB 调用前拒绝；回调须返回动作名和真实证据文件路径，驱动检查文件并记录 SHA-256。回调异常保留为原始失败并仍走 finally 恢复；没有 UI 自动化成功模拟。
- 恢复拒绝条件加固：baseline `run_id` 不匹配，或 host/test 包/数据的任一基线字段缺失、不是明确 `absent`，在 force-stop/uninstall 前写出 refused 结果。
- runner 原件未改，仍使用其真实 sidecar 格式（包含 command/mode/timestamps/adb_exit/result；其本身没有 run_id）。run_id 由外层 manifest/item/restore 绑定。

## 验证证据

Python：`D:\develop\Python\Python313\python.exe`；执行期间 `TEMP` 与 `TMP` 均指向 v3 `_task_tmp`。使用 `-B` 禁用字节码缓存。

绿色命令（退出码 0）：

```powershell
$work='E:\creation-reading-assistant\next-round-joint-mumu-driver-v3'; $tmp=Join-Path $work '_task_tmp'; $env:TEMP=$tmp; $env:TMP=$tmp; & 'D:\develop\Python\Python313\python.exe' -B "$work\offline_test.py" --out "$tmp\green-v3-final.json"
```

结果：49 项、0 失败、0 跳过；旧回归与 v3 新判据均在同一离线运行中通过。

v2 红色对照命令（退出码 0 表示预期红点已出现）：

```powershell
$work='E:\creation-reading-assistant\next-round-joint-mumu-driver-v3'; $tmp=Join-Path $work '_task_tmp'; $env:TEMP=$tmp; $env:TMP=$tmp; & 'D:\develop\Python\Python313\python.exe' -B "$work\offline_test.py" --against 'E:\creation-reading-assistant\next-round-joint-mumu-driver-v2' --out "$tmp\red-v2-final.json"
```

结果：49 项、11 个失败案例、0 跳过；其中 1 项是既有标记的 ADB 错误负向基线，新增 10 个 v3 反例均在 v2 上失败，包括 raw `FAILURES!!!` 冲突、方法错绑、缺 run_id/exit/sidecar 字段、非对象 sidecar、错误恢复基线、回调缺失及异常未恢复判据。

runner SHA-256：`a634a344f0d8a8c0bf8d14ddee93518551fa5577f2950b61ab77445549c2167e`，与合同预期一致且测试报告 `runner_reused_byte_identical=true`。

## 文件 SHA-256

| 文件 | SHA-256 |
|---|---|
| `joint_driver.py` | `dfdd0585e415659fff70687fe38915e2e0ea41ce93d3902455829250175ea814` |
| `offline_test.py` | `1a95ccb47487f54b041542ef5a219b1a6ee83843b114ad4e277ddafc39fa26c4` |
| `runner.py` | `a634a344f0d8a8c0bf8d14ddee93518551fa5577f2950b61ab77445549c2167e` |
| `runbook.md` | `cd0d3062da2e96ab21bdcb964b3e9b2e5fadc66cdabfceacd851c5be7238b799` |
| `config.template.json` | `96f05be0a54a2b390f713a8fe26661ff996f58fd7bc82b6df319383e2879b8e3` |

## 限制与交接

- 本轮仅为离线 Fake ADB 与证据判定测试；没有设备、MuMu、Gradle、APK 或真实 UI 回调运行证据，不能据此声称联合设备验收通过。
- 合同若要求保存、冷启动、离章、回源等真实步骤，Root 的集成调用方须逐项注入实际 UI 回调并返回现场保存的证据文件；单次 `_ui_probe` 不能替代这些步骤，也不能把多个方法串行当作跨步骤完整合同。
- 本轮没有修改产品源码、runner、v2、恢复仓库 Git 状态或既有结果文件；无设备/构建/安装操作。交付停止于离线驱动判据与回调接口。
