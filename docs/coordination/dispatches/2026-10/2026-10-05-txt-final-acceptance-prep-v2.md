# TXT 最终验收工件准备 v2（窄返修）：独立验收准备报告

## 任务编号/版本

CRA-TXT-FINAL-ACCEPTANCE-PREP-v2；2026-10-05。角色：独立验收工件准备负责人（不承担 TXT 生产修复，
不重做已验 J5）。交付状态：**READY_FOR_BINDING / 离线 26/26 + 接缝 7/7 通过 / 测试源定向编译通过**。
**不产生任何 J1 设备 PASS 结论。**

返修依据：[TXT-v2 与 J1 验收准备独立核查及窄返修](../../reviews/2026-10/2026-10-05-txt-v2-prep-codex-review.md) 的两项 P1 中
属于**验收准备**的一项（整批门禁可缺原始 runner/恢复账仍接受）与文档指出的**可执行接缝**。

## 输入指纹

- 恢复仓库 HEAD `e39a78da9c62f02614207cffb36969a32492eaea`，暂存区 0；本任务只读，未改生产源码/构建/current/TODO。
- 只读输入（实算 SHA256）：
  - v1 检查器 `txt-final-acceptance-prep-v1/driver/j1_evidence_check.py` `2c638e22…`
  - v1 回调 `…/driver/j1_ui_callback.py` `878ab943…`、v1 离线 `…/offline/j1_offline_tests.py` `3205f0d2…`
  - 已验驱动 `unified-offline-binding-v1/patched-driver/joint_driver.py` `013ca2f9…`、
    `runner.py` `a634a344…`（冻结 runner，逐字节复用）
  - 冻结 TXT-v2 `txt-reentry-fix-v2/freeze/source-sha256.json` `2e94e2e8…`（980 项，作候选完整源码清单）
  - 复用 UI 原语 `restore-real-interruption-accept-v1/scripts/ui.py` `4e7c6227…`
- Root 反例证据（只读）：`txt-v2-prep-codex-review-20261005/offline-work/good_unit_start/`
  —— manifest 仅 `{"run_id": ...}`，无 items/raw/restore，v1 仍输出 `J1_EVIDENCE_ACCEPTED`。
- **未读取正在变化的 TXT-v3**（`txt-reentry-fix-v3/`）作为任何冻结输入；编译副本只用已冻结 TXT-v2。

## 所有权与改动

- 唯一写入：`E:\creation-reading-assistant\txt-final-acceptance-prep-v2\`（新目录，v1 复制后返修）
  与本报告。
- 未修改：v1 原件、TXT-v2 冻结候选、patched-driver、TXT-v3、D 源码、current/TODO、公共构建配置、
  旧报告。未 commit/push/reset/checkout；未访问正式书库或其他应用私有记忆。
- 开工记录：应用=Claude Code 桌面版（Code 标签页）；模型=deepseek-v4.1-flash（经 anthropic/claude-ccr
  代理路由，environment 报告的 model id 解码即 deepseek-v4.1-flash）；开始 ≈ 2026-10-05 14:47 (+08:00)。
- **同编号任务核占用**：E 盘无 `txt-final-acceptance-prep-v2` 目录、D 盘无本报告、E 盘无 `*prep-v2*`
  产物 → 本编号任务此前未运行，无重复写入风险。
- 设备与构建环境：未连接设备；未安装、未 ADB、未做设备验收。定向编译使用**私有缓存副本**
  （TXT-v2 `gradle-home` 的字节复制），编译后已删除，不写回旧候选。

## 环境

Windows 11 / Python 3.13.2 / JDK 17.0.14 / sha256sum 可用；Gradle 由冻结 TXT-v2 的 `gradlew.bat`
离线执行（`--offline`，私有 `GRADLE_USER_HOME`）。

## 执行与结果

### 1. 判定分层：整批通过 = A 驱动批次门禁 且 B J1 行为

v1 只核「run manifest 存在 + run_id 相等」，不核真实逐方法 runner 判定，也不核真实 restore 账。
v2 把判定**显式分成两层**，复用已验驱动、不重写：

| 层 | 内容 | 复用 |
|---|---|---|
| **A 驱动批次门禁** | run manifest 的 `contract_items`/`contract`/`config_sha256`/`started_at_ms`；逐项 `items/<id>.json` + `raw/<id>.txt` + `raw/<id>.json`(sidecar) 复算；`restore.json` 的 `ok`/`verified`/`run_id` | **`joint_driver.cmd_report`（不重写）** |
| **B J1 行为** | 单元映射、退出保存 S、三条重进 R、等待期、新位置、UI 标记 SHA | 本合同（Kotlin/Python 双实现） |

输出 `driver_batch`（PASS/NOT_PASS）、`j1_behavior`（ACCEPTED/REJECTED）与合并 `verdict`
（`J1_BATCH_ACCEPTED` / `J1_BATCH_REJECTED`）。缺一项、原始方法 FAIL/NOT_RUN、或恢复失败 → A 层不通过
→ 整批不得通过；**缺恢复账时不得因 J1 行为正确而判整批 PASS**。

**Root 反例复现**（`offline/root_repro.py`，同一证据束并跑两版检查器）：
manifest 只有 run_id、无 items/raw/restore、J1 观察 JSON 8 个全绿 →
**v1 = `J1_EVIDENCE_ACCEPTED`（exit 0）；v2 = `J1_BATCH_REJECTED`（exit 1，
`driver_batch=NOT_PASS`，`j1_behavior=ACCEPTED`）**。证明缺口已堵且**不是**因为 J1 行为判定变严。

### 2. 新增实际漏测反例（原 18 例全部保留）

| 新反例 | 注入 | 结果 |
|---|---|---|
| `reject_stub_manifest_only_run_id` | manifest 仅 run_id，删 items/raw/restore | 拒绝（A 层） |
| `reject_restore_ledger_missing` | 行为 JSON 全绿，删 `restore.json` | 拒绝（A 层） |
| `reject_restore_failed` | `restore.ok=false` | 拒绝（A 层） |
| `reject_restore_run_id_mismatch` | `restore.run_id` 不符 | 拒绝（A 层） |
| `reject_runner_raw_missing_observation_ok` | 删 `raw/<id>.txt`，观察 JSON 仍称成功 | 拒绝（A 层） |
| `reject_runner_sidecar_failed_observation_ok` | sidecar=FAIL/raw 含 `FAILURES!!!`，观察 JSON 仍称成功 | 拒绝（A 层） |
| `reject_runner_command_mismatch` | sidecar 命令方法 ≠ config | 拒绝（A 层） |
| `reject_config_changed_after_run` | 运行后改 config（绑定同步更新，隔离证明驱动层独立生效） | 拒绝（A 层） |

**更名澄清**：v1 的 `reject_missing_recovery_evidence` 实际删的是**退出保存记录**，已更名
`reject_missing_exit_record`；「删恢复账」由新增的 `reject_restore_ledger_missing` 承担。

### 3. 可执行接缝（`offline/j1_seam_tests.py`，7/7 真实执行）

- 新增**唯一执行入口** `driver/j1_run.py`：注入 `cfg['_config_path']` 与 `cfg['driver_dir']`
  （`run_all` 的 `before_item` 签名不携带它们），并设置回调模块级 `CONFIG_PATH`/`DRIVER_DIR` 回退；
  强制 `--serial` 与 `config.serial` 及本轮 `adb devices` 的 device 列表一致。
- 回调 v2：`prepare_binding` 缺 `_config_path`/`driver_dir`/`fixture`/`source_manifest`/双 APK →
  **拒绝执行**；`Ctx` 缺 serial 或 serial 为占位 → 拒绝（**不再回落 `127.0.0.1:16416`**）。
- 关键 ADB 动作（push/dump/cat/tap/swipe/start/force-stop）统一经 `Ctx.check()`：**非零退出即抛错**，
  空 dump 亦拒绝，读取/推送失败绝不当成功。

### 4. 等待期覆盖口径（不合并）

「等待期未清零」拆成两项，检查器输出 `coverage` 与 `not_covered`：

- `wait_no_zero`（`waitPhase=persistence_hold`）：app 停在首页/书架时的**持久进度保持**——本轮可观测；
- `wait_recovery_phase`（阅读器重进、落位前的恢复等待阶段）：本轮**无法安全观测 → NOT_RUN**，
  不用首页/书架停留冒充。

### 5. 定向编译（已执行，仅证明测试 API 可接入）

在**独立 E 编译副本**（冻结 TXT-v2 `android/` 只读复制 + 两处测试源）执行：

```
GRADLE_USER_HOME=<私有缓存> ./gradlew.bat :app:compileQahostAndroidTestKotlin -PcraAndroidTestBuildType=qahost --offline --console=plain
```

- **EXIT=0，BUILD SUCCESSFUL in 3m 27s，`^e: ` 计数 0**；`J1RecoveryContract` / `TxtFinalAcceptanceJ1Test`
  产出 `.class`。原始日志 `compile-copy/logs/compile-qahost-androidtest.log`（SHA `2ef9222d…`）。
- 变体校正：qahost 变体任务为 `:app:compileQahostAndroidTestKotlin` / `:app:assembleQahostAndroidTest`，
  **必须带 `-PcraAndroidTestBuildType=qahost`**（否则 `testBuildType` 回落 `debug`）。
- 编译副本 980/980 冻结文件与 `freeze/source-sha256.json` **零不一致**，仅新增两处测试源
  （与 payload 字节一致：`59b45b93…` / `555d86d2…`）。**生产源码零改动。**
- **此编译不是最终 v3 APK，也不是设备通过。**

## 原始证据

- 交付目录：`E:\creation-reading-assistant\txt-final-acceptance-prep-v2\`
- 离线正反例：`offline/results/`（26 例 JSON + `SUMMARY.json` + `run-raw.txt`）
- Root 反例复现：`offline/root-repro/{root-repro.json,report-v1.json,report-v2.json}`
- 接缝验证：`offline/seam-results/SUMMARY.json`
- 定向编译：`compile-copy/logs/compile-qahost-androidtest.log`
- 交付清单 SHA：`manifest/payload-sha256.txt`（20 项，实算）

### 交付 SHA256（`manifest/payload-sha256.txt`）

```
d98be4c340f2…  README.md
2ef9222df114…  compile-copy/logs/compile-qahost-androidtest.log
db1d89fcfe4a…  config/binding-fields.md
b6191b61661c…  config/j1-config.template.json
157562b2e659…  driver/j1_evidence_check.py
2fe9259e07ca…  driver/j1_run.py
8e49aec736d7…  driver/j1_ui_callback.py
4274b2ccf85a…  offline/j1_offline_tests.py
fe42eeceaf22…  offline/j1_seam_tests.py
258278ed3484…  offline/root_repro.py
7624f44af07d…  offline/root-repro/root-repro.json
73be651d013a…  payload/APPLY.md
59b45b934642…  payload/androidTest/…/J1RecoveryContract.kt
555d86d22355…  payload/androidTest/…/TxtFinalAcceptanceJ1Test.kt
477fd489c59a…  payload/fixture/fixture.sha256.txt
15e6f96577c6…  payload/fixture/fixture.txt
1fc7a107d520…  payload/fixture/j1_fixture.py
（另有 report-v1/v2.json 与 seam-results/SUMMARY.json，见清单原文）
```

### 最小工件差异（v1 → v2）

| 文件 | 变化 |
|---|---|
| `driver/j1_evidence_check.py` | 新增 A 层 `cmd_report` 门禁 + `restore_ledger_present`/`driver_raw_runner_evidence_present`；分层输出 `driver_batch`/`j1_behavior`/`coverage`/`not_covered`；taskId→v2 |
| `driver/j1_ui_callback.py` | `Ctx.check()` 退出码强制；`_config_path`/`driver_dir` 注入与拒绝；serial 必填；`waitPhase` 语义注释 |
| `driver/j1_run.py` | **新增**（唯一执行入口） |
| `offline/j1_offline_tests.py` | 好束升级为驱动级完整；新增 8 反例；更名澄清；输出 driver/j1 分层字段与 run-raw |
| `offline/root_repro.py`、`offline/j1_seam_tests.py` | **新增** |
| `config/*` | taskId→v2；serial 去历史默认值；等待期口径表；`_run_entry` 说明 |
| `payload/APPLY.md` | 校正为真实 qahost 任务名与已执行编译结果 |
| `payload/androidTest/*.kt` | 仅头注释 v1→v2 与 `waitPhase` 字段（**合同/谓词逻辑不变**） |

## 恢复与副作用

本任务无设备操作，无系统设置/APK/DB 改动；不触碰 protected 包与正式书库。
唯一副作用为新建 E 盘交付目录与本报告（只增不改）；编译副本的私有缓存与构建产物已在编译后删除，
未写回任何旧候选。旧报告/旧驱动/旧候选/TXT-v3 原件保留不动。

## 未覆盖项

- **设备执行 NOT_RUN**：J1 行为与真实 UI 结论须由唯一设备 owner 在冻结 APK 上产生；本任务不写设备 PASS。
- 未绑定 TXT-v2 最终 APK/候选源码清单/runner/serial（明确标**待绑定**）。
- 阅读器**重进等待阶段**偏移观测：本轮无法安全观测 → 检查器记 `NOT_RUN`（见 `config/binding-fields.md` §5）。
- 生产「落位等待/底部 clamp」缺口属窗口一（TXT-v3）职责，本任务不判断、不预判其实现。
- 未用重复实现的 Python 谓词宣称 Kotlin 测试已通过；编译通过只证明测试 API 可接入。
- J5 已验，本任务不重跑。

## 交付状态与下一步

状态：**READY_FOR_BINDING**（离线 26/26 + 接缝 7/7 + 定向编译通过）。不写 J1 设备 PASS，
不提前绑定正在变化的 TXT-v3。

待 TXT-v2 冻结后由唯一集成/设备 owner：

1. 按 `payload/APPLY.md` 复制两源文件到候选并编译（qahost 变体）；
2. 填齐 `config.json`（**serial 显式**）→ `python driver/j1_run.py --config … --serial … --driver-dir …` 串行执行；
3. 拉回证据 → `python driver/j1_evidence_check.py …` 得**分层**判定（A 驱动批次 + B J1 行为）；
4. APK 身份、恢复合同、旅程行为分别判定；**恢复缺证据时不得因 J1 行为正确而判整批 PASS**；只使用本轮前后账。

准备完成，停止，等 Codex 核查后再由唯一 owner 合流构建及 MuMu 验收。
