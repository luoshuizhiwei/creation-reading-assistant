# CRA-MD-PAGED-RESTORE-CLOSE-v2 —— Markdown 分页修复：恢复取证与报告勘误

- 任务编号/版本：`CRA-MD-PAGED-RESTORE-CLOSE-v2`；2026-10-04。
- 执行者：Claude Code（桌面应用 Code 标签页）；客户端上报模型串 `anthropic/claude-ccr-h776f726b62756464792041492f676c6f62616c3a646565707365656b2d76342e312d666c617368`（该串为路由标识，未做界面核验，如实登记）。本批唯一 MuMu 操作者。
- 性质：**恢复取证 + 报告勘误**。不新增功能、不重建 APK、不重跑 111 项或五条 UI 旅程、不改产品/测试源码。
- 状态：**已交付待 Codex 核查**。分页功能 5/5 已由 Codex 核查；本任务只关闭剩余恢复口径。

## 一、输入指纹

| 项 | 值 |
|---|---|
| 恢复仓库 HEAD（开工=收工） | `e39a78da9c62f02614207cffb36969a32492eaea`（与预期一致） |
| 暂存区 | 条目 0（与预期一致） |
| 分页修复候选（只读） | `E:\creation-reading-assistant\next-round-md-paged-chapter-fix-v1` |
| 上游（只读） | `E:\creation-reading-assistant\next-round-joint-final-v3` |
| 复用驱动口径（只读） | `E:\creation-reading-assistant\next-round-joint-mumu-driver-v4\joint_driver.py` 的 `SETTING_PROBES`（七项设置探针） |
| 最终宿主 APK SHA-256 | `eeb6b4bbf4b737adf1283e973d6494a56326edb67922511134a5c6538b593319`（Codex 已核，本轮未复算） |
| 最终测试 APK SHA-256 | `448e7bf0db2ee34f543ba3e935dfef59b902795af5fc49ba69d0a5ec6a238ed5`（同上） |

## 二、所有权与改动

- 本批唯一 MuMu 操作者。另一窗口只读分析源码与清单，不操作设备。
- **唯一设备写入**：`adb -s 127.0.0.1:16416 shell cmd uimode night yes`（1 次）。未改其他设置、数据或 APK。
- **唯一仓库写入**：本报告。未改 `current.md`、TODO、其他窗口报告；未暂存/commit/push/reset/checkout；未搬移目录。
- 未访问实体手机、正式书库、原损坏仓库或其他应用私有记忆库。

## 三、环境

- MuMu 模拟器 serial `127.0.0.1:16416`（`adb devices` 同时列出 `emulator-5556`，为同一设备的另一 transport；所有命令显式 `-s 127.0.0.1:16416`）。
- 1080×1920、280dpi 竖屏宽布局；`uptime` = `up 1 day, 14:13`（设备连续运行、本日无重启）。
- 结果口径：**MuMu 模拟器**，不写实体手机 / MIUI。

## 四、时间线（按时间顺序，逐项附原始证据路径）

真实设备夜间模式读数（仅取真实设备文件；`logs/jointv2-*` 与 `_task_tmp/jointv2-*` 已核为**离线合成夹具**——其 `wm_density=440`、`accelerometer_rotation=0`，与真机 280/1 不符，不作为设备证据）：

| 本地时间 | 事件 | Night mode | 原始证据 |
|---|---|---|---|
| 2026-10-01 18:03–18:06 | j2-v3-room-acceptance 基线/恢复 | no | `next-round-j2-v3-room-acceptance/pre_baseline/system_settings.json` |
| 2026-10-01 18:58–18:59 | j2-v4-room-acceptance 基线/恢复 | yes | `next-round-j2-v4-room-acceptance/phase3_*/system_settings.json` |
| 2026-10-02 20:48–21:00 | j3-n1-fix-accept-v2 | yes | `.../device-baseline/settings-before|after.txt` |
| 2026-10-03 19:14–19:36 | md-annotation-return-fix-v1 | no | `.../device-baseline/settings-before|after.txt` |
| 2026-10-03 23:54–2026-10-04 00:12 | integration-build-accept-v2 | yes | `.../device-baseline/settings-before|after.txt` |
| 2026-10-04 10:20–12:39 | md-true-chapter-accept-v1 | no | `.../device-baseline/settings-baseline.txt`、`root-evidence/{before,after}.json` |
| **2026-10-04 19:42–20:07** | **上游 joint-final-v3（最后已知状态）** | **yes** | `device-core/baseline.json`、`device-md*/baseline.json` 与 `adb-readback.jsonl` |
| **2026-10-04 21:50–22:34** | **本任务 md-paged-chapter-fix-v1（第一次设备操作前 → 各批 finally 恢复后）** | **yes** | `device-mdpc-{diag,diag2,diag3,final-b..f}/baseline.json` 与 `adb-readback.jsonl` |
| 2026-10-04 ≈22:35 起 | **收工额外修改**：执行者 `cmd uimode night no` | no | 仅见于原报告文字（见第六节），无独立原始命令日志 |
| 2026-10-04 22:50 | Codex 只读核查（未写设备） | no | Codex 核查报告文字 |
| 2026-10-04 23:47 | 本轮写入前读回 | no | `evidence/readback_before.json` |
| 2026-10-04 23:48 | 本轮恢复写入后读回 | yes | `evidence/restore_night_mode.json` |

时间线要点：

1. **上游最后已知状态 = `yes`**：final-v3 五批（device-core/md/md-r2/r3/r4）的基线**与**末次 readback 均为 `yes`，最后一批结束于 20:07:37。
2. **本任务第一次设备操作前状态 = `yes`**：`device-mdpc-diag/baseline.json`（21:50:18，install 之前采集）记录 `Night mode: yes`，与上游末态一致。
3. **各批 finally 恢复后 = `yes`**：本任务 8 个有基线的批次，基线**与**末次 readback 逐批一致为 `yes`；各批 `restore.json` 的 `settings_restored` 均为 `[]`（驱动只在检测到漂移时回写，未检出漂移 → 批内恢复成立）。
4. **收工额外修改 = `yes` → `no`**：执行者在批次结束后执行 `cmd uimode night no`。
5. **当前实际状态（写入前）= `no`**。

## 五、判定与执行结果

### 5.1 恢复目标判定（按任务规则）

| 条件 | 结论 | 依据 |
|---|---|---|
| 本任务最早可靠基线明确 | ✅ 明确 = `yes` | `device-mdpc-diag/baseline.json`（21:50:18），且与上游 final-v3 末态一致 |
| 设备已无其他活动 owner | ✅ | 另一窗口只读源码/清单；Codex 仅只读查询 |
| 未出现新的设备使用证据 | ✅ | 收工后除 Codex 只读查询外无其他设备访问；`E:\creation-reading-assistant` 下无其他任务含设备 readback |
| 当前变化来源是否可确定 | ✅ 可确定 = 收工 `cmd uimode night no` | 原报告明确记载 |

四个条件均满足 → 按任务授权，将**本任务收工造成的夜间模式差异恢复到该基线（`yes`）**。此为目标值来自**本任务自身基线**，不是"采用旧任务某次设置"。

### 5.2 实际设备写入（唯一）

| 项 | 值 |
|---|---|
| 精确命令 | `adb -s 127.0.0.1:16416 shell cmd uimode night yes` |
| 退出码 | 0 |
| 写入前读回 | `Night mode: no`（`secure ui_night_mode=1` MODE_NIGHT_NO） |
| 写入后读回 | `Night mode: yes`（`secure ui_night_mode=2` MODE_NIGHT_YES） |
| 原始 stdout/stderr/exit | `evidence/restore_night_mode.json`、`evidence/readback_before.json`、`evidence/readback_after.json` |

### 5.3 收工只读对账（写后，全部通过）

| 对账项 | 结果 |
|---|---|
| 七项约定设置 vs 基线 | **7/7 一致**（含 night_mode=yes；其余 wm_size 1080x1920、density 280、accel 1、user_rotation 0、stay_on 0、timeout 60000 均未变） |
| 原宿主 71 文件清单 | **集合完全相同**（71 vs 71，差异 0） |
| 原 APK 对 | **2/2 一致**：宿主 `e82904cc9c1315466ed5c521693f93f541560feef038bf074ce7f7cf8324ef8a`、测试 `fdbd019011088505fa37d71565259a0d76f6927407af69148379e2ef7c27038d` |
| 分页测试 UID 包与数据目录 | `com.creationreadingassistant.qahostmdpagedv1` 与 `.test` 的 `pm path` 均 rc=1；`/data/data/...qahostmdpagedv1*` 不存在 → 已清理 |
| 本任务临时文件 | `/data/local/tmp` 无 `cra_mdpc*`；无 2026-10-04 13:00 之后新增文件 |
| forward / reverse | 均为空 |

## 六、旧报告勘误（对 `2026-10-04-next-md-paged-chapter-fix-v1.md`）

1. **基线实际记录过 night_mode。** 原报告称"本批各批基线/恢复账内无漂移记录"，易被读成"无基线记录"。事实：**8 个批次（diag/diag2/diag3/final-b/c/d/e/f）的 `baseline.json` 均记录 `night_mode.value = "Night mode: yes"`**。"无漂移"指的是 `restore.json.settings_restored=[]`（驱动未检出漂移、故未回写），**不等于没有基线记录**。
2. **收工 yes→no 不是"恢复"，而是偏离本任务基线。** 原报告把收工时的 `yes` 视为"无法归因本批动作"并改为 `no`；按本任务基线（`yes`）衡量，该写入引入了差异。本轮已按基线恢复为 `yes`。
3. **JVM 计数：15 个 XML，不是 16 套件。** 实算 `android/app/build/test-results/testQahostUnitTest/*.xml` = **15 个 XML，合计 111 tests / 0 failures / 0 errors / 0 skipped**。与 Codex 核查一致。
4. **final-b 整体 NOT PASS 与分项通过的关系。** `device-mdpc-final-b/summary.json` 的 `overall = "NOT PASS"`，`reason = "items not run: mdpc_40_assertScrollTocJump, mdpc_90_recordFinalState"`；已通过分项 = `mdpc_00/01/10/20/21/30`。`device-mdpc-final-f/summary.json` 的 `overall = "PASS"`（`mdpc_00/40/90`）。**已通过分项可结合 final-f 证据采用，但不改写原 `overall` 结论。**
5. **功能 5/5 与整项恢复验收分别结论。**
   - 功能：**5/5 通过**（四分页旅程：目录 1→2、2→3、3→2 等待 8 秒、强停冷启动恢复；一滚动旅程 final-f 目录选章）——分母仅为这五条，**不代表全部阅读模式、全产品或永久集成**。
   - 整项恢复验收：**原报告 A–F 6/6 中的 F 不能整项采纳**——批内恢复成立（第 4 节第 3 点），但**收工恢复不成立**（night_mode 被改为 `no`，偏离基线）。本轮已补正该差异，收工恢复现与基线一致。
6. **口径：MuMu 模拟器，不是实体手机 / MIUI。** 全部证据为 MuMu 1080×1920 宽布局；手机窄布局未验。

## 七、原始证据

| 证据 | 路径 | SHA-256 |
|---|---|---|
| 写入前读回 | `E:\creation-reading-assistant\next-round-md-paged-restore-close-v2\evidence\readback_before.json` | `b9c8e8046635c7e1d86259d3fa94d73f38eeee2041d640be4108a7f649a63c32` |
| 写入后读回 | `…\evidence\readback_after.json` | `9f1a4b3d168fdcc718538559137f2ced34fc3d383e0029c1912f4dce6f8fbd05` |
| 夜间模式写入记录（含原始命令/退出码/前后输出） | `…\evidence\restore_night_mode.json` | `382f67bee76451e17ce60267713008dbee26b4382a5b1bf83df8a9a0e8e05acd` |
| 只读采集器 | `…\collect.py` | `b798348f33d9436cb5812adad5d9db1839132d85b2d71f5e6c8e7f482bb76620` |
| 写入脚本 | `…\do_restore.py` | `832e6968da675700eb81dfe611e0d661908d7dd54aa6711914c83643bb442f5b` |
| 执行记录 | `…\execution.json` | 见文件 |

被引用的只读原件：`next-round-md-paged-chapter-fix-v1/device-mdpc-*/`、`next-round-joint-final-v3/device-*/`、`next-round-joint-mumu-driver-v4/joint_driver.py`。**原始失败批、旧报告、summary 均保持原样未改。**

## 八、未覆盖项

- **夜间模式在本设备上并非稳定托管设置。** 2026-10-01 至 10-04 真实设备读数在 `yes`/`no` 间多次翻转（第 4 节时间线），且**没有任何任务在 restore 中管理它**（各批 `settings_restored=[]`）。其翻转来源**未知**：本日设备连续运行无重启，`secure ui_night_mode` 为直接写入（1=NO / 2=YES，非 `auto=0`），故非重启所致、也非 auto 模式。**本轮只按任务规则恢复到本任务基线值，未追查该翻转机制。**
- 若 Codex 认为应以"设备长期稳定值"而非"本任务基线值"为准，该值需另行取证判定——本轮不猜。
- 手机窄布局、实体设备 / MIUI、TalkBack、R8、签名发布维持既有边界。
- 流式大 MD（>60k 源字符）的设备验证、持久 locator_v2 的 ci/co 与重推章号关系未新增覆盖（沿用 Codex 核查结论）。

## 九、交付状态与下一步

- **交付状态：已交付待 Codex 核查。** MuMu 所有权已释放。
- 实际恢复结论：**收工造成的夜间模式差异已恢复到本任务基线值 `yes`**；其余六项设置、宿主 71 文件、原 APK 对、分页沙箱清理、转发均为基线一致，未漂移。
- 未完成事项：夜间模式翻转来源未查明（第 8 节）；是否改判目标值由 Codex 决定。
- 建议核查顺序：`evidence/restore_night_mode.json` 的原始前后输出 → `readback_before/after.json` 的七项设置与 APK/文件对账 → 第 4 节时间线各原件 → 第 6 节勘误逐条。
- 本任务就此停止，不启动后续实施；分页 payload 的精确合流与全模式联合退出仍由 Codex / 集成者按既定流程另行决策。
