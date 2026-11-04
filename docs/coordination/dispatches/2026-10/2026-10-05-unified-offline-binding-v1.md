# CRA-UNIFIED-OFFLINE-BINDING-v1 —— 统一候选 J1–J5 离线绑定核查报告

日期：2026-10-05
任务编号/版本：CRA-UNIFIED-OFFLINE-BINDING-v1（独立离线核查负责人角色）
实际应用：ZCode CLI（桌面交互编码代理）
模型：new-provider-5/ling-3.1-flash
工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（恢复仓库，git HEAD `e39a78d`）
任务目录：`E:\creation-reading-assistant\unified-offline-binding-v1\`
状态：**READY_FOR_BINDING（离线通过）** —— 尚无新双 APK 与设备输出，本报告不含任何设备 PASS。

## 1. 任务编号/版本

- 任务：CRA-UNIFIED-OFFLINE-BINDING-v1。核查既有严格驱动 v4 能否正确承载统一候选 J1–J5 合同、消除绑定错误与假 PASS 风险；只读核查 + 任务目录内最小脚本副本修补，禁止构建与设备任务。
- 输入范围以 Root 2026-10-05 复核为准：统一候选 424 JVM 测试全过；分页根因已修复（新 APK 需回归绑定）；设备最小集 J1–J5（不做历史全量重跑）；旧文件计数/serial/APK 身份不是新批固定基线。
- 六项工作核查结论见 §5。发现一个具体缺口（GAP-1：runner.py 冻结 SHA 从未作为门禁），已在任务目录做最小补丁副本并完成负向测试与无回归证明。

## 2. 输入指纹

| 输入 | SHA-256 |
| --- | --- |
| 原驱动 `next-round-joint-mumu-driver-v4/joint_driver.py` | `72c38e5b3d2fbe63bb9134e3635d368066c508f2172d25e5b3fa11b145ef6b61` |
| 原驱动 `next-round-joint-mumu-driver-v4/runner.py` | `a634a344f0d8a8c0bf8d14ddee93518551fa5577f2950b61ab77445549c2167e` |
| 原驱动 `next-round-joint-mumu-driver-v4/offline_test.py` | `4bbf5527774149a3bb01d8c3a32c52e28222b88491fb4257c787d280d9b722e6` |
| Root 冻结 `unified-v1-codex-review-20261005/verification.json` | `4f41571676a3df7cf747c0b99b3757604fee0e8baf487ee3a223ae7e3dff442f` |
| `docs/coordination/reviews/2026-10/2026-10-05-unified-v1-codex-review.md` | `76125826ea1928898452a392787ec83a546e5ececd72dab449da379ff009c82d` |
| `docs/coordination/dispatches/2026-10/2026-10-05-unified-acceptance-contract-v1.md` | `2aa6d91ccf3b38aac1b8cd82c5a075c6d9526b480c37945756e93017e0ecd5e8` |
| `docs/handoff/current.md` | `1daa262058e204ed7d928c37b40d23a458295c39f58259b881884dbe5d8b1c29` |
| `AGENTS.md` | `656094b7116650a665e5dc75a38442a973cf2d7c5029b2919c2f7b1efa9fa2d0` |
| 统一候选 `unified-candidate-integration-v1/android/` | 978/978 文件与 verification.json 冻结清单逐字节一致（本次复算 0 不匹配、0 缺失） |

verification.json 结构核对：manifest_rows=980（actions 十类计数之和），source_sha256=978 文件全量指纹，junit=424/0/0/0（28 XML 套件），mismatch=[]。**完整 978 文件清单（而非原 980 行应用清单）才是冻结依据**——原 980 行只是 actions 分类计数行，不含逐文件指纹。

## 3. 所有权与改动

授权写入（且仅写入）：

- `E:\creation-reading-assistant\unified-offline-binding-v1\`（任务目录）
- 本报告 `docs/coordination/dispatches/2026-10/2026-10-05-unified-offline-binding-v1.md`

任务目录交付物：

| 文件 | 性质 |
| --- | --- |
| `binding_check.py` | 新增离线绑定检查器（10 项检查）；复用原驱动纯函数 `validate_config/check_apk/check_sandbox_isolation/evidence_guard`（importlib 导入，不复制逻辑），追加绑定层检查 |
| `negative_tests.py` | 新增 14 场景负向测试（S0–S13） |
| `_make_patched_driver.py` | 补丁生成器（3 个锚定 hunk，锚点唯一性已验证） |
| `patched-driver/joint_driver.py`（SHA `013ca2f964312c4b434cc98c2c94b0a915349134bc04a801987e715700d8ff85`） | 最小补丁副本：**任务目录内副本，不是上游修改** |
| `patched-driver/runner.py` | 原 runner.py 逐字节副本（SHA 与冻结一致） |
| `negative-results.json` | 14 场景命令、退出码、原始结果 |
| `work/` | 4 份全量套件结果 JSON、10 份绑定报告 JSON、漂移目录与配置/APK 夹具 |

未改动（已复核）：任何 Android 源码、构建配置、原驱动目录 `next-round-joint-mumu-driver-v4/`（逐字节未动）、旧报告、`docs/handoff/current.md`/TODO、公共清单；无 commit/push/reset/checkout。

最小补丁 diff（相对原驱动，精确 3 hunks，+23 行）：

```diff
@@ -52,6 +52,24 @@   # 新增 check_runner_identity()：强制相邻 runner.py 与 RUNNER_SHA_EXPECTED 逐字节一致
@@ -695,6 +713,8 @@   # cmd_precheck：checks['runner_identity'] = check_runner_identity()
@@ -1052,6 +1072,9 @@   # run_all：evidence_guard 之后不通过即 raise DriverFailure
```

## 4. 环境

- Windows 10.0.26300 x64；Python 3.13；ZCode CLI 会话内执行。
- 全程离线：**无 ADB、无设备/模拟器、无 Gradle/构建、无真实数据接触**（negative-results.json 记录 `no_adb_or_device_calls: true`）；FakeAdb 为 offline_test.py 既有内存夹具。
- 未读取窗口一正在生成的文件；统一候选目录以 Root 冻结的 verification.json 为唯一冻结依据。

## 5. 执行与结果

### 5.1 六项工作核查结论

**（1）完整源码清单绑定 —— 原驱动缺失，由绑定层补齐。** 原驱动只吃 config.json，不接触源码清单；`derive_capabilities` 仅探测 2 个陈旧键（`require_fresh_source`、`md_acceptance_class`），config items 的 `requires` 字段只是规划信息、从不强制。binding_check 检查 `candidate_dir_matches_frozen_manifest`（978 文件逐字节对账）与 `new_combination_tests_in_manifest`（4 仪器 + 2 JVM + 4 生产源必须在冻结清单内）补齐该层。S0 对当前候选目录通过（978/978）。

**（2）双 APK/包名/runner/run_id 一致绑定 —— 正确。** 复用驱动 `check_apk`（声明 SHA 与实际文件一致）、`validate_config`（14 必填键、占位值、重复合同 id 拒绝）、`check_sandbox_isolation`（宿主≠正式包、正式包不得嵌套于宿主）、`evidence_guard`（run_id 碰撞拒绝）；绑定层追加：声明 SHA 不得是 6 个历史 APK 身份之一（final-v3/md-paged/原 qahost 各对，值已与 coordination 文档逐字核对）、沙箱包名不得是历史包名/正式包/protected。负向证明：S5（错误 SHA→`apk_identity_host` 失败）、S6（历史 SHA→`HISTORICAL apk identity` 拒绝）、S9（run_id 碰撞→`run_id_unused` 失败且驱动 `evidence_guard` 直接拒绝）。

**（3）J1–J5 items ↔ before_item/UI 回调/原始证据 —— 正确，缺证据拒判 PASS。** `run_all` 在任何 ADB 动作前要求注入 UI 回调（S10：原驱动与补丁驱动一致抛 `DriverFailure('configured before_item requires an injected real UI callback')`，`adb.log` 为空）；回调必须确认相同 action 且返回非空证据文件列表，文件必须存在，驱动记录解析后路径 + SHA-256；`cmd_report` 复核 action 匹配、文件非空、条目 path+sha256 并重算，矛盾则把 PASS 降级为 FAIL。`_runner_evidence_verdict` 核对 sidecar command 与配置类/方法/目标绑定、mode=method、时间戳一致性、`normal_gate`（S11/S13）。items 只能引用冻结清单内真实仪器源码（S7 清单外类、S8 类内不存在方法均被拒）。

**（4）FAIL/NOT_RUN/强停/JUnit 区分与非零退出 —— 正确。** `KNOWN_VERDICTS` 五态；`aggregate` 要求每项 verdict 严格 `'PASS'`，TERMINATED/NOT RUN/未知一律 NOT PASS；恢复证据 ok+verified+run_id 匹配缺一不可。负向证明：S11（缺原始侧证→`NOT RUN`→聚合 `NOT PASS`，reason 含 `items not run: it_x`）、S12（删除 restore.json 后即使 it_a/it_b 全 PASS→整体 `FAIL`，`restore evidence missing`）、S13（非零退出 + PASS sidecar 矛盾→`FAIL`，`PASS sidecar contradicts raw normal_gate or process exit`）。非零退出码绝不自动视为成功或不存在。

**（5）恢复按当前基线对账 —— 驱动语义正确，历史计数不当基线由合同层排除。** `run_restore` 恢复至**本次 run_id** 的基线（run_id 不匹配拒绝）、设置逐项回写并复验、forward/reverse 新条目清除、基线存在现在消失报 problem（不以列表重构）、protected 包按 data_manifest 精确列表对账（非固定文件数）——即不刚性套用历史 71 文件计数；绑定层也不硬编码任何历史计数。旧计数/serial/APK 身份作为新批基线的误用途径由绑定层身份检查（S6）与合同排除条款闭合。

**（6）假 PASS 路径 —— 无驱动可走通的路径。** 旧 APK PASS 不可移植：声明历史 SHA 直接被绑定期拒绝（S6）；install 成功只是 timeline 一环，item PASS 必须经 runner 原始输出 + sidecar 门控；直接写 DB/标记无法被驱动检测（属限制，见 §8），由 items 绑定冻结清单内真实仪器源码（S7/S8）+ 合同禁止条款缓解；截图/标题只是证据材料，驱动只验存在性/非空/完整性，真实性属人工评审。

### 5.2 GAP-1 发现与最小修补

**GAP-1（具体缺口）**：`RUNNER_SHA_EXPECTED`（`a634a344…`）定义于 `joint_driver.py:48` 与 `offline_test.py:23`，但**从未作为门禁强制**：

- `offline_test.py:1246-1257` 把 `runner_reused_byte_identical` 记进结果 JSON，但退出码（`offline_test.py:1275`）只看 `failures == 0`；
- 且该字段哈希的是**套件自身目录**的 `runner.py`，不是 `--against` 被测目录的——记录层连被测目录的漂移都观察不到；
- 后果：漂移/篡改的 `runner.py` 会同时污染子进程门禁（runner.py 自带的 `normal_gate`）与驱动导入复用的 `normal_gate`，离线套件仍全绿。

**S1 演示（缺口证实）**：行为中性字节漂移的 runner × 原驱动 → 55/55 全绿、exit 0、`failures=0`、记录字段仍为 `runner_reused_byte_identical=true`，而漂移 runner 实际 SHA `dda2af926fcd…` ≠ 冻结值。

**最小修补（任务目录副本）**：`check_runner_identity()` 在 `cmd_precheck`（新增 `checks['runner_identity']`）与 `run_all`（evidence_guard 之后、任何 ADB 动作之前，不通过即 `raise DriverFailure`）两处强制。负向与无回归证明：

| 场景 | 结果 |
| --- | --- |
| S2 漂移 runner × 补丁驱动 | exit 1，9/55 失败（2 个 precheck + 7 个 run_all 用例），`check_runner_identity()` 返回 mismatch |
| S3 补丁驱动 + 冻结 runner | 55/55 全绿，exit 0（**无回归**） |
| S4 原驱动复跑 | 55/55 全绿，exit 0（环境基线确认） |

**零代码缓解（无需改上游即可闭合设备前风险）**：`binding_check.py` 的 `runner_identity_frozen` 检查对任何 `--driver-dir` 强制 runner.py 与冻结 SHA 逐字节一致——设备执行前对原驱动目录跑一次 binding_check 即阻断 GAP-1。

### 5.3 负向测试总表（14/14 通过）

S0 合法绑定→BINDING_OK（正向对照，证明负向场景失败于对应检查而非恒假）；S1 GAP-1 演示；S2 补丁强制生效；S3/S4 无回归与基线；S5 错误 APK SHA；S6 旧 APK 身份误用；S7 清单外仪器类；S8 类内不存在方法；S9 run_id 碰撞；S10 缺 UI 回调（任何 ADB 动作前拒绝）；S11 缺原始侧证（NOT RUN→NOT PASS）；S12 缺恢复证据（全 item PASS 仍 FAIL）；S13 非零退出+PASS sidecar 矛盾（FAIL）。全部命令、退出码与原始结果见 `negative-results.json`。

## 6. 原始证据

- `E:\creation-reading-assistant\unified-offline-binding-v1\negative-results.json` —— 14 场景完整记录（命令、退出码、失败检查名与详情、原始 stdout、输入 SHA）。
- `work/offline-green-original.json`、`work/offline-green-patched.json` —— 全量离线套件（55 用例）对原驱动/补丁驱动的 fresh 全绿结果。
- `work/runner-tamper-original.json`（exit 0，缺口证据）、`work/runner-tamper-patched.json`（exit 1，9 个失败用例名单）。
- `work/s0-positive/binding-negbind-s0.json` 等 10 份绑定报告 —— 每检查 `{name, ok, detail}`。
- `work/drift-orig/`、`work/drift-patched/` —— 漂移 driver 副本（joint_driver.py 原样复制 + runner.py 追加一行行为中性注释，篡改前后 SHA 记录在 negative-results.json）。
- 补丁 diff 锚点：`joint_driver.py` `54a55,72`、`697a716,717`、`1054a1075,1077`（相对原文件行号）。

## 7. 恢复与副作用

- 无设备/模拟器/ADB 调用，无构建，无真实数据接触；不需要设备侧恢复。
- 系统临时目录产生 `jointv2-*` FakeAdb 临时目录（offline_test.py 既有夹具行为，纯离线）。
- 恢复仓库（`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`）仅新增本报告一个文件；HEAD `e39a78d` 与暂存区不变（收工复核见 §9 前置检查）。
- 未 commit/push/reset/checkout；未修改原驱动、旧报告、current/TODO、公共清单。

## 8. 未覆盖项

- **直接写 DB/标记不可被驱动检测**：驱动只判 runner 原始输出、sidecar 与恢复证据；测试内直接写数据库或进度标记无法从驱动侧发现。缓解：合同 items 绑定冻结清单内真实仪器源码（S7/S8 拒绝虚构引用）+ 合同禁止把直接写标记当作旅程 PASS（execution-and-recovery.md 已列）。真实性最终属设备批次的人工评审。
- **before_item 证据真实性**：驱动验证回调存在、action 匹配、文件非空并记录 SHA，但"截图确由本设备本次运行产生"属人工评审范围。
- **未运行新双 APK**：双 APK 尚不存在（S0 使用本地生成夹具 APK），全部结论为离线通过，不构成设备 PASS。
- **derive_capabilities 陈旧探针键与 items.requires 不强制**：属规划层信息，已在绑定时刻由 `items_reference_real_instrumented_tests` 闭合引用真实性；不影响判定。
- **补丁副本未上游化**：`patched-driver/` 是任务目录副本。若设备批次直接使用原驱动目录，须先应用 GAP-1 修复，或在执行前用 `binding_check.py --driver-dir <原驱动目录>` 的 `runner_identity_frozen` 检查核验（已含零代码缓解）。

## 9. 交付状态与下一步

**交付状态：READY_FOR_BINDING（离线通过）**。14/14 负向场景通过；补丁无回归（S3/S4 全绿）；候选目录与冻结清单 978/978 逐字节一致。

**设备执行前关键阻断（必须全部闭合，按序）**：

1. **GAP-1**：上游驱动应用 §5.2 补丁，或在设备执行前对所用驱动目录跑 `binding_check.py`（`runner_identity_frozen` 已强制冻结 SHA）；
2. **978 文件完整清单绑定**：`binding_check.py --verification <verification.json> --candidate-dir <合流树 android/>` 全绿（S0 已对当前候选通过；合流树若有变须重跑）；
3. **双 APK 绑定**：窗口一构建后用真实 SHA 更新 config（host/test APK 路径 + SHA-256）并重跑 binding_check（S0 为夹具基线）；install 后按合同做 install 读回 SHA 复核；
4. **新隔离 UID**：设备上用 `pm path`（rc≠0 且空输出）+ 成功包列表精确匹配双重证据确认不存在（`pm path` 失败单独不证明缺失——合同已定）；
5. **before_item UI 回调**：窗口一按 execution-and-recovery.md 的回调合同注入（真实 UI 证据文件），S10 证明缺回调时驱动在任何 ADB 动作前拒绝；
6. **run_id/evidence_dir 每批唯一**（S9 证明碰撞被拒）；串行/adb 路径按当轮设备决定绑定。

**下一步**：窗口一构建新双 APK（不必等待本核查）→ Codex 用 `binding_check.py` 完成绑定（全部检查 BINDING_OK）→ 按 `unified-acceptance-contract-v1/execution-and-recovery.md` 八阶段单所有者执行 J1–J5 → 恢复后出设备批次报告。本任务到此停止，不扩展产品功能，不 commit/push/reset/checkout。
