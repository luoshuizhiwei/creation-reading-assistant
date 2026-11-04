# CRA-JOINT-MUMU-DRIVER-v2 —— 驱动返修：错误通过与恢复合同

- 任务编号/版本：`CRA-JOINT-MUMU-DRIVER-v2`；2026-10-04。
- 执行者：ZCode / GLM（account:zai-start-plan）。
- 性质：**只做脚本与离线验证**。未连接 MuMu、未安装/卸载、未跑 Gradle、未改产品源码、未操作 Git、未改 current.md/Obsidian；未覆盖 EPUB/MD 并行窗口文件。
- 输入：[三项并行交付核查](../../reviews/2026-10/2026-10-04-three-parallel-deliveries-codex-review.md)（驱动返修依据）；v1 原件 `E:\creation-reading-assistant\next-round-joint-mumu-driver-v1\`（保留未改）。
- 输出：`E:\creation-reading-assistant\next-round-joint-mumu-driver-v2\` 与本报告。目标报告与目录开工时均不存在，首次执行。

## 输入指纹

| 项 | 值 |
|---|---|
| 仓库 HEAD（开工=收工） | `e39a78da9c62f02614207cffb36969a32492eaea`；暂存区条目 0；无 stage/commit/push/reset/checkout |
| v1 关键件 SHA | `joint_driver.py` a7379249…、`offline_test.py` 6f9af573…、`config.template.json` 40910c7e…、`runbook.md` 1b1a8c20…、`runner.py` a634a344…（均只读，原件未动） |
| v2 产物 SHA | `joint_driver.py` ebaecdd0…、`offline_test.py` b4256928…、`config.template.json` c25f2fde…、`runbook.md` ac34670b…、`red-results.json` f2571649…、`offline-results.json` 9fb85aa3…、`runner.py` a634a344…（复用字节未变） |

## 所有权与改动

只写自有脚本/测试/runbook/配置于 v2 目录，加唯一报告本文件。v1 目录与其中所有文件保持原件；未改任何产品源码、候选、current/TODO/其他报告。

## 环境

Windows / Git Bash；Python 3.13.2（仅 stdlib，`python -B`）。JDK/Gradle 未启动；ADB/MuMu 未触碰——设备交互全部经 Fake Adb（状态机 + 命令日志 + 脚本化非零退出）。

## 执行与结果

### 方法：红先行

同一套 39 条离线用例写入 `offline_test.py`，通过 `--against <目录>` 既可对 v1 现件跑红、也可对 v2 跑绿；红跑不修改 v1 任何文件。Fake Adb 记录完整命令序列（验证动作顺序、确认拒绝发生在卸载之前）并模拟非零退出/权限失败/空输出；对 v1 跑红时切换为忠实模拟 v1 包装层语义（忽略退出码），避免假红。

### 红色基线（对 v1 现件，16 失败 / 12 回归守卫通过 / 12 项 v2 专属跳过）

| 已复现问题（任务书） | 红色证据 |
|---|---|
| aggregate([], True) 返回 PASS | `空合同返回 PASS` |
| 单项 TERMINATED + restore_ok=True → PASS | `TERMINATED 返回 PASS`（未知状态同样 PASS） |
| 基线已有 APK/数据，卸载清空后 cmd_restore 返回 True | `v1 把"卸载已有包清空数据"当成恢复成功`，且拒绝前已执行卸载 |
| precheck 要求仪器已注册却排在安装前 | `安装前 precheck 失败项: ['instrumentation']`；离线静态 precheck 也因混入设备项退出 1 |
| report 直接收外部 restore_ok/results | `v1 report 接受手填 results（合同 2 项只报 1 项仍 PASS）` |

同族额外红点：缺项/多项/重复项/run_id 失配全部 PASS；恢复证据 ok=true 带问题 PASS；原宿主数据被改动恢复不发现；su 失败被当空数据清单；重复合同 id 被接受；`<...>` 模板占位被接受（v1 模板承诺拒绝但未实现）。

### 修复（v2 joint_driver.py）

1. **aggregate 合同绑定**：PASS 必要条件 = 合同非空且无重复 id、结果与合同完全一致（无缺项/多项/重复）、每项 verdict 严格 `PASS`、各项与恢复证据 run_id 一致、恢复证据 ok 且 verified。单项失败/恢复失败 → `FAIL`；TERMINATED/NOT RUN/未知状态/结构问题 → `NOT PASS`；两者退出码均非 0。恢复证据自相矛盾（ok=true 带问题、缺 verified）按 FAIL。
2. **report 从证据生成**：只读 `evidence/run-<run_id>.json`（含 config SHA 绑定）+ `evidence/items/<id>.json`（引用 runner 原始输出 raw/<id>.txt）+ `evidence/restore.json`；`--results` 手填入口移除；config 运行后漂移拒绝；证据称 PASS 但 runner 退出码非零 → 降级 FAIL；原始输出缺失 → 结构问题。
3. **两阶段检查**：安装前 = 静态（config/APK+SHA/候选目录/能力探测/隔离/保护名单不相交）+ 设备级（serial 在线、沙箱双包运行时确认未安装无数据、protected 可读）；安装后 `postinstall` = 双 APK 读回 SHA == config 声明 + `pm list instrumentation` 三元组注册检查。`precheck --offline` 可无设备通过。
4. **沙箱占用前置拒绝**：`pm path` 退出 0 且无输出 + `su 0 test -d` 显式 `DATA_DIR_ABSENT` 才算确认不存在；发现已有资产（installed/present/inconclusive）在安装/卸载前拒绝（refused=true），**不做真备份还原**（本版限制，如实声明），绝不以卸载已有包方式"恢复为空"。
5. **恢复合同 = 恢复到基线**：强停+按序卸载新装双包后以显式状态探测复验（未装包的 uninstall `Failure` 容忍，判定只看探测）；运行期间实际改变的设置逐项回写并复验（wm size/density 覆盖与 reset、夜间模式、settings put/delete）；forward/reverse 增删对账，基线有而现无的映射无法凭 list 重建 → 如实报问题；protected 原宿主数据清单（含 DB/WAL/SHM）与原 APK 对 SHA 逐条只读对账，任何变化即恢复失败。
6. **ADB 失败显式化**：`Adb.check` 非零退出抛 `AdbError`；pm path/su 探针三态 absent/present/inconclusive；非零/权限不足/空输出/不可解析一律按失败处理，绝不解释为"不存在/无数据"。
7. **run_all 带 finally**：precheck→baseline→install→postinstall→(UI 探针)→items→finally 恢复→从证据生成汇总；安装失败、runner 异常、UI 探针失败、KeyboardInterrupt 都会恢复并保留 `original_failure`，恢复失败使整体不通过。
8. **配置收紧**：`run_id` 必填、合同项 id 唯一、`<...>` 模板占位拒绝启动；能力探测标注为计划信息，缺能力/缺类只会 NOT RUN → 整体 NOT PASS，不能自动跳过凑 PASS。
9. **runner 字节复用**：`runner.py` 与 v1/n1-runner-hardening-v1 同为 `a634a344…`，离线门禁自证；单方法门禁未重写。

### 绿色门禁（对 v2）

`offline-results.json`：**39/39 通过、0 失败**。覆盖：全部红色反例转绿、安装失败（finally 恢复+原始失败保留+无合同项执行）、runner 中途异常（同上）、恢复失败（设置回写后仍漂移 → 整体 FAIL，即使全项 PASS）、原包已有资产拒绝（且拒绝前无任何卸载命令）、完整合同绑定（缺项证据/原始输出缺失/退出码矛盾/恢复问题/run_id 失配/config 漂移全部拒绝）、正常路径动作顺序（devices→precheck 探测→基线读取→双安装→读回+仪器注册→items→双卸载+复验；卸载全部发生在安装与 items 之后）、设置回写复验（夜间/超时/密度覆盖 reset）、转发移除、沙箱未装时容忍 uninstall Failure、UI 探针失败跳过合同项。测试过程中红测试自身抓到并修正了两处缺陷：Fake 探针正则未容忍 `]` 前空格（`(S+?)\]` → `[^\s\]]+`）、`run_restore` 对 baseline=None 的 TypeError（改为显式"基线缺失即恢复失败"）。

## 原始证据

| 件 | 路径 / SHA-256 |
|---|---|
| 红色基线 | `E:\…\next-round-joint-mumu-driver-v2\red-results.json`（39 例，16 失败明细逐条含断言消息） |
| 绿色门禁 | `E:\…\next-round-joint-mumu-driver-v2\offline-results.json`（39/39） |
| 驱动/测试/配置/手册 | `joint_driver.py` ebaecdd0…、`offline_test.py` b4256928…、`config.template.json` c25f2fde…、`runbook.md` ac34670b… |
| 执行记录 | `execution.json`（v1/v2 全部 SHA、修复清单、已知缺口） |
| v1 原件（未改） | `E:\…\next-round-joint-mumu-driver-v1\`（SHA 见执行记录） |

## 未覆盖项

- 无任何设备证据：本轮连 MuMu 都未连接；恢复合同的设备面行为（真机 uninstall 输出、su 权限、wm/settings 回写、protected 对账）只在 Fake Adb 语义上验证，最终 APK/SHA 由集成者提供后才可执行。
- 已有资产场景未实现真备份还原：当前合同是前置拒绝；需要该场景须另立范围授权。
- 恢复对账不防 evidence 文件事后手工篡改（离线无签名机制）；绑定靠 run manifest/config SHA/逐项证据链。
- EPUB/MD 侧并行返修（fresh-source 失败分支、串行性、MD 验收类入合流树）不在本任务范围；驱动对它们只保留"合同项必须真实执行"的入口。
- 不产生全产品百分比；本任务范围内红→绿即 39/39，不代表联合验收通过。

## 恢复与副作用

零设备、零构建、零产品写入、零 Git 写入；HEAD 前后一致、暂存区空。v1 目录原件未动（SHA 复核一致）。唯一仓库写入为本报告。测试全部在临时目录与 v2 目录内完成，无残留进程/服务。

## 交付状态与下一步

**已交付待核查。** v2 驱动 + 红绿门禁 + 配置模板 + 运行手册就绪；runbook §4 如实写明实际支持的恢复合同（恢复到基线：状态探测复验 + 设置回写复验 + protected 对账 + finally）与限制（已有资产前置拒绝、不防事后篡改、无设备证据）。下一步由 Codex/集成者核查本报告与 freeze 件；设备执行须待最终联合候选 APK/SHA 就绪、由当轮唯一 MuMu owner 按 runbook 执行 run_all——本轮不连接设备、不启动后续任务。
