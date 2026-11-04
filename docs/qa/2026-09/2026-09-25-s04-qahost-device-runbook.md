# S04 QA host 真机验收：分阶段执行清单（阶段 1 已执行 · 阶段 2/3 暂不执行）

- 日期：2026-09-25
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 性质：**执行清单（runbook）**。本文档描述全部门槛满足后"如何执行"，**本身不构成任何授权、不放行任何阶段**。撰写窗口完全离线（零 adb、零 Gradle connected、零安装、零 instrumentation、零设备触碰），唯一写入 = 本文档；未读取 E 盘备份内的书籍内容、数据库行或凭据；未 commit / push / stage / reset / checkout / clean。
- 当前状态（2026-09-25 第三次修订时点）：**用户已授权安装（D1），两个 qahost 包已安装（历史时点，见 §3）**；**尚未运行任何 instrumentation，沙箱应用图标未手动打开**。**测试（阶段 2）与测试后对账（阶段 3）尚未放行**：D2（测试后 force-stop 再授权）、D3（恢复能力 UNVERIFIED 风险）、D4（验收窗口）均**未批准**、仍待用户决定（§6）；安装完成**不**倒推这些决策已批。**当前执行入口 = 安装后的只读复测与待决策事项（§3.4）**；安装成功 ≠ 测试通过。
- 修订：历次修订见文末「§9 修订记录」。最近一次（2026-09-25 第三次修订）：校正授权口径——全文历史授权事实**仅限**「用户已授权安装（D1），两包已安装」，删除「安装时 D1–D4 全部满足」「D4 已批准」等错误表述。本清单对阶段 2/3 仍为**暂不执行**。
- 输入（判据来源，均为只读）：
  - `docs/code-review/2026-09/2026-09-25-s04-qahost-predevice-review.md`（验机前独立审查；§4 测试清单、§5 前置条件、§6 恢复点要求、§7 B1–B9）
  - `docs/qa/2026-09/2026-09-25-s04-e-backup-offline-baseline.md`（E 盘基线；compare 工具与退出码；workdb 实际位置修正）
  - `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-qahost-offline-integration.md`（离线构建门禁与 APK 哈希）
  - `docs/qa/2026-09/2026-09-25-s04-qahost-readonly-preflight.md`（真机只读预检：设备/包状态/通道/空间实测）
  - `docs/coordination/legacy-stages/2026-09/2026-09-25-s04-consistent-snapshot-options.md`（快照方法评估；§3 捕获协议；§7 用户决策清单）
  - `E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\README.md`（归档 README，只读）
  - `android/scripts/install_with_confirm.ps1`（安装脚本接口，只读核对）

---

## 0. 撰写时刻的已核实状态（2026-09-25，撰写窗口现场重算）

### 0.1 本窗口直接重算/复读核实

| 项 | 值 | 佐证 |
|---|---|---|
| host APK | `android/app/build/outputs/apk/qahost/app-qahost.apk`，SHA-256 `064F53341475CCFDC898205ADFB36451E380F3A49C8C014AECEA9373BC7DC054`（产物 mtime 2026-09-25 07:53） | 本窗口 `sha256sum` 重算 = 集成者报告锚点 = 验机前审查 §2.2 = B 报告 `2026-09-25-s04-qahost-apk-refresh.md` §3（三方逐字节一致） |
| test APK | `android/app/build/outputs/apk/androidTest/qahost/app-qahost-androidTest.apk`，SHA-256 `FA48A6BC6DA850C367FA4DD53679384E21D5A7C5C717DD9D711FB002F9A21B54` | 同上（B 报告 §3 同列，三方逐字节一致） |
| E 盘基线清单 | `baseline-manifest.json`（13,310 字节，2026-09-25 13:48）SHA-256 `FE42381E4515A02F3ABA528066B0C07E1469DEE65FDD26639A94BED0D659D352` | 撰写窗口重算 = 基线报告 §3 完整性锚 = C 报告 §9 复核（真实归档只读自比较前后均复核为同值；自检 **24 例全过**，见 W2） |
| E 盘归档 | `formal-app-data.tar` 2,294,109,184 字节，SHA-256 `9DFF292C806F33EA17659BE99072E01B72C82891E2394CED2D6E6E870DB8D29C` | 基线报告 §2（本窗口未复读归档本体） |
| 构建接线 | `android/app/build.gradle.kts` 未提交差异仍为 +23/−3（qahost 接线），与验机前审查 §5-A-3 一致 | 本窗口 `git diff --stat` |
| 源码锚点 | HEAD = `5aed5939600e491843e3c44453921bd6273a233c`；工作区未提交条目 484（撰写时刻） | 本窗口 `git rev-parse` / `git status --porcelain` |
| 测试源文件 | 两个目标测试类与 `CraIsolatedTestRunner` / `CraIsolatedTestApplication` 均在位 | 本窗口 ls |
| 设备安装状态（**执行窗口报告值**） | host `com.creationreadingassistant.qahost` 与 test `com.creationreadingassistant.qahost.test` 已于 2026-09-25 经 `install_with_confirm.ps1` 以 `RESULT: CONFIRM_LOOP` 安装成功，**两次退出码均为 0**、未走回退；安装后两包 uid 与正式包不同，正式包 base.apk SHA-256 未变；沙箱应用未手动打开、未运行任何 instrumentation | **执行窗口（Codex）报告值，本修订窗口未复测**（零 adb）；仓库内暂无独立安装执行报告；退出码语义见 §3.3，安装成功 ≠ 测试通过（§4） |

### 0.2 既有设备侧实测（只读预检报告；撰写窗口未复测）

设备 Redmi K50 Ultra / Android 15（SDK 35）、竖屏、serial 脱敏 `c49***6cf`；正式包 `com.creationreadingassistant` versionCode 3 / versionName 0.5.1、base.apk SHA-256 `53d0c01b0d36dc19690dc1f62834f19fc61634a2dd394de2b23efc9da85359ac`（53,532,616 字节）；两个 qahost 包均未安装；`run-as` 对正式包可用（uid=10949）；设备端无 `sqlite3`；正式包私有数据聚合 ≈2.29 GB（databases 1,126,281,728 / files 1,167,181,726 / shared_prefs 5,131 字节）。

其中 run-as 可用、设备无 sqlite3、qahost 两包未安装、数据聚合值四项属**单窗口报告值**（快照方案评估 §5"无法独立复核"），执行窗口 stage 0 必须现场重测并逐项留脱敏原始输出与退出码。

**历史时点说明**：本节全部为**预检时刻**的快照。「两个 qahost 包均未安装」一项已随 2026-09-25 执行窗口完成安装而失效——现为**已安装**（属预期结果，见 §0.1/§3）；其余三项（run-as 可用、设备无 sqlite3、数据聚合值）在阶段 3 捕获前仍须现场复核（W4）。

### 0.3 待核登记（W1–W5；未到齐不得假定通过）

| # | 项 | 撰写时刻现状 | 转绿条件 |
|---|---|---|---|
| W1 | **B 窗口新 APK 哈希报告** | **已转绿（证明口径已收窄）**。B 报告 §4③ 将「72 任务 up-to-date」的证明范围**收窄**为「本轮离线构建所核对的产物与当前构建输入对应」这一有限结论——**不**外推为「所有源码逐字节绝对未变」，也**不**外推为设备或运行期安全；两个 APK SHA-256 与撰写窗口重算、集成者锚点三方逐字节一致（§0.1）；B §5 以 aapt + dex **双重核实**两包包名、`targetPackage`、`CraIsolatedTestRunner`（Codex 已再次核对一致） | 已满足。安装已按 §3.2 完成（历史时点）；如需**重装**属新的设备写决策，须先重算哈希并重新授权（S2 仍适用于任何重装前复核） |
| W2 | **C 窗口加固后基线哈希** | **已转绿**。C 第 3 轮（`docs/qa/2026-09/2026-09-25-s04-e-backup-offline-baseline.md` §9）已落地「并发覆盖」窄修：`write_private_json` 独占占位 + 原子发布（TOCTOU 加固），新增确定性竞态测试 `test_manifest_blocks_when_output_appears_during_scan`；定向自检 **24/24 通过、退出码 0**（`0o600` 调整后复跑仍 24/24）；真实归档只读自比较 `RESULT: PASS`、0 差异、**退出码 0**；基线完整性锚仍为 `FE42381E…D352`（本修订窗口按测试文件清点 `def test_` 方法 = 24，与报告一致） | 已满足。阶段 3 使用基线前仍须现场重算哈希锚一致（S3） |
| W3 | **测试后归档捕获命令原文** | **已转绿（无需再问用户）**。13:07 备份实际使用的准确命令已确认：`tar -cf - -C /data/data/com.creationreadingassistant databases files shared_prefs no_backup`，二进制流写 E 盘；准确参数顺序见 §5.1 | 已满足；阶段 3 捕获必须**同法、同根、同序**（§5.1），不得凭记忆改写 |
| W4 | 预检"无法独立复核"四项（§0.2） | 「两包未安装」一项已随安装完成而**失效**（现为已安装，属预期，§0.1）；其余三项（run-as 可用、设备无 sqlite3、数据聚合值）仍为单窗口报告值；安装执行过程未在仓库内落盘复测记录 | 阶段 3 捕获前现场复核剩余三项，逐项留脱敏原始输出与退出码 |
| W5 | 用户决策 D1–D4（§6） | **D1 已批准并执行完毕**（安装完成，历史时点，见 §0.1/§3）；**D2 / D3 / D4 仍未决**（原「接受 E 盘基线」与「W1 处理」已消解，见 §6） | 用户对 D2/D3/D4 逐项决定 |

---

## 1. 总体纪律（贯穿所有阶段）

- **真机唯一**：先 `adb devices` 核对唯一设备，逐条命令 `adb -s <serial>`；报告只写脱敏 serial（`c49***6cf`）。
- **竖屏**；不使用 MuMu、不使用任何模拟器；不测试横屏。如临时启用 `stay_on_while_plugged_in`，记录原值并在窗口结束前恢复。
- 全程**不卸载、不重装、不清数据正式包**；不取消、不改正式包 WorkManager 任务；不手动启动沙箱应用图标（§3.5）。
- **中性称呼**：报告只写"测试 EPUB""书库文件"等；真实书名、逐文件清单、数据库行、偏好值只存在于仓库外私有证据。
- **证据分区**：仓库内报告只写方法、摘要、差异计数、哈希与退出码；逐行/逐文件清单、原始 tar、logcat 原文、`diff-evidence.json` 一律落仓库外私有目录（建议与既有基线同处 `E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\`，或用户指定的等价仓库外位置），不入 git/docs/任何同步渠道。
- **执行与审查分离**：阶段 1–3 只在获授权的独立验收窗口执行；执行前由另一窗口（或用户指定审查者）对照本清单逐项勾验。

---

## 2. 阶段 0：上机前（零设备写操作）

> 【历史时点】**用户已授权安装（D1），两个 qahost 包已安装**（§3）；除此之外**不得推定本节任何门槛（含 D4）在安装当时已满足**——D2/D3/D4 均未批准，且仓库内无安装执行报告可佐证阶段 0 检查的实际执行情况。本节保留作清单标准与审计记录。**§2.3 第 3 行「两包均不存在」为安装前时点的预期**，两包现已安装（属预期结果）；§2.2 的哈希/aapt 复核若因任何**重装**而重做，须先获用户授权。

### 2.1 用户决策门槛

D1–D4（§6）全部落定。特别注意：**安装两个沙箱包的同意（D1）是完全独立的单项**，不因备份授权或任何先前批准而自动生效。（历史：已发生的授权事实**仅限 D1**——用户已授权安装，两包已安装；**D2/D3/D4 仍未决定**，不得由安装完成倒推「D1–D4 全部落定」。）

### 2.2 版本闭合（源码 ↔ 产物）

1. 重算两个 APK SHA-256，必须逐字等于（W1 已到齐：B 报告哈希与锚点逐字节一致，未产生新哈希）：
   - host：`064F53341475CCFDC898205ADFB36451E380F3A49C8C014AECEA9373BC7DC054`
   - test：`FA48A6BC6DA850C367FA4DD53679384E21D5A7C5C717DD9D711FB002F9A21B54`
2. `aapt dump badging` 直接对 **APK 文件**核：host 包名 `com.creationreadingassistant.qahost`（versionCode 3 / versionName 0.5.1）、test 包名 `com.creationreadingassistant.qahost.test`、`targetPackage="com.creationreadingassistant.qahost"`、Runner `com.creationreadingassistant.testing.CraIsolatedTestRunner`。本清单撰写时仅 packaged_manifests 中间产物被核对过，APK 级复核是必做项（验机前审查 §5-A-2）。
3. `git diff --stat -- android/app/build.gradle.kts` 仍为 +23/−3；记录 HEAD `5aed5939…`。接线自验机前审查后被改动 → 版本闭合作废，退回独立审查（S2）。
4. 确认两个 APK 均为 debug 签名、包名与正式包不同（不存在签名冲突覆盖正式包的路径）。

### 2.3 stage 0 设备只读复测（现场执行，逐项留脱敏原始输出与退出码）

| # | 检查 | 预期 | 不符动作 |
|---|---|---|---|
| 1 | `adb devices` | 唯一真实设备，状态 `device` | 多台/离线 → S4 |
| 2 | 方向（`dumpsys window displays`） | 竖屏 | 请用户手动转竖屏；不改系统旋转设置 |
| 3 | `pm list packages` 过滤 qahost | `com.creationreadingassistant.qahost` / `…qahost.test` 均不存在 | 已存在 → 取证（数据目录大小级摘要）→ S5，处置等用户决定 |
| 4 | `dumpsys package com.creationreadingassistant` | versionCode 3 / versionName 0.5.1（与预检一致） | 不符 → S4 |
| 5 | 正式包 base.apk 路径 + 设备端 `sha256sum` | 与预检参考值 `53d0c01b…85359ac` 一致；**本次现场记录值即阶段 3 比对基准** | 不符 → S4 |
| 6 | `run-as com.creationreadingassistant id` | 可用（预期 uid=10949） | 不可用 → S9 |
| 7 | `which sqlite3`（系统与 run-as 环境） | 预期仍不存在 | 若存在仅登记，不改变流程 |
| 8 | `adb shell pidof` 命令可用性 | 可用 | 不可用 → 改用 `ps -A` 过滤并登记替代 |
| 9 | 正式包前台/运行状态 | 记录（不干预） | 仅登记，降低后续归因难度 |

### 2.4 阶段 0 通过判据

D1–D4 全部落定 + W1–W5 全部转绿 + §2.2、§2.3 全部通过 → 进入阶段 1。任何一项红色 → 停在阶段 0，输出阻塞清单交用户。（历史：阶段 1 安装**仅凭 D1 授权**完成；本判据整体在安装当时是否逐项满足无仓库内登记佐证，**不得倒推为已满足**；D2/D3/D4 仍未批。）

---

## 3. 阶段 1：安装（仅在获授权的独立验收窗口执行）

> 【历史时点，已执行】**用户已授权安装（D1），两个 qahost 包已安装**：2026-09-25 执行窗口（Codex）按 §3.2 完成 host 与 test 安装，两次均为 `RESULT: CONFIRM_LOOP`、**退出码 0**，未走任何回退（执行窗口报告值，§0.1；仓库内暂无独立安装执行报告）。**该授权仅限 D1**：D4（验收窗口）从未批准，D2/D3 亦未批，不得由安装完成倒推。**安装成功 ≠ 测试通过**：尚未运行任何 instrumentation（§4），沙箱应用图标未手动打开。§3.2/§3.3 保留作审计记录；**不得据本节默认重装**——重装属新的设备写操作，须另行授权并重算 APK 哈希。

### 3.1 进入条件

清单标准：阶段 0 全绿；D1（安装同意）与 D4（验收窗口授权）已获明确批准。**授权事实核对（历史）**：已发生的仅是「用户已授权 D1（安装），两包已安装」；**D4 未批准**；「阶段 0 全绿」在安装当时是否成立无仓库内登记佐证，二者均**不得作为已满足引用**；D2/D3/D4 现仍未决（§6）。

### 3.2 安装步骤（顺序：先 host 后 test；每步完整 stdout/stderr + 退出码落执行日志）

> 【历史·已执行，不得默认重跑】以下两条命令为 2026-09-25 执行窗口实际使用的命令（CONFIRM_LOOP、退出码 0×2）。仅当用户就「重装」作出新的决策时才可再次执行，且执行前须按 §2.2 重算哈希。

1. host（在仓库根执行，**单行**；不得用 `^` 续行）：

   ```
   powershell -ExecutionPolicy Bypass -File android\scripts\install_with_confirm.ps1 -Apk android\app\build\outputs\apk\qahost\app-qahost.apk -Serial <serial> -PackageName com.creationreadingassistant.qahost
   ```

   ⚠ **必须显式传 `-PackageName com.creationreadingassistant.qahost`**。脚本默认值是**正式包名**：host 安装漏传该参数时，脚本的确认循环与回退会以正式包验证安装结果——正式包必然存在 ⇒ 假 PASS。

2. test（**单行**）：

   ```
   powershell -ExecutionPolicy Bypass -File android\scripts\install_with_confirm.ps1 -Apk android\app\build\outputs\apk\androidTest\qahost\app-qahost-androidTest.apk -TestApk -Serial <serial> -PackageName com.creationreadingassistant.qahost.test
   ```

   `-TestApk` 负责加 `adb install -t`；`-TestApk` 缺 `-PackageName` 时脚本会拒绝启动，属预期防护，不是故障。

### 3.3 退出码语义与回退规则

| 退出码 / RESULT | 含义 | 记录方式 |
|---|---|---|
| `0` + `RESULT: CONFIRM_LOOP` | 经 MIUI 安装器确认循环安装成功 | 可记"安装脚本通过" |
| `2` + `RESULT: FALLBACK_DIRECT_INSTALL` | 确认循环未完成，脚本内部直接 `adb install` 回退成功 | 记 **FALLBACK_DIRECT_INSTALL**，**不得写"安装脚本通过"** |
| `1` + `RESULT: FAILED` | 脚本失败 | 留证（退出码 + 完整日志）后允许人工回退（下条） |

**人工回退（仅当脚本确实失败且留下退出码/日志后）**：`adb -s <serial> install -r [-t] <apk>`（test APK 加 `-t`）。成功记 **FALLBACK_DIRECT_INSTALL**，同样**不得写"安装脚本通过"**。回退依据、退出码与日志引用写入执行报告；无证据的回退 = S6。

### 3.4 安装后只读复测与待决策事项（**当前执行入口**）

> 本节是 2026-09-25 修订后确认的**当前执行入口**：两包已安装（D1 授权范围）、测试未运行，当前可做且仅可做的是下列**只读**复核；复测通过后剩余门槛全部是用户决策（§6 D2/D3/D4），不存在任何可自行推进的设备写步骤。

以下四项均为**只读**，可在任何窗口重跑留证；第 3、4 项已有执行窗口报告值（§0.1），重跑仅作独立留证：

1. `pm list packages` 过滤 qahost → 恰好新增两包；
2. `pm list instrumentation` → `com.creationreadingassistant.qahost.test` 的 instrumentation 存在且 target 为 `com.creationreadingassistant.qahost`；
3. `dumpsys package` 两个 qahost 包 → uid 与正式包（预期 10949）不同；
4. 正式包 base.apk SHA-256 复测 = 阶段 0 记录值（安装动作未触碰正式包本体的即时证据）。

任一不符 → S5/S9。

复测全部通过后：**不运行 instrumentation（§4 需 D4 窗口授权）、不 force-stop 正式包（§5.1 需 D2 再授权）、不演练恢复（D3 待决）**——安装成功不推及任何测试结论。

### 3.5 安装后纪律

- **不手动点开沙箱应用图标**：手动启动会让完整生产 `App.onCreate` 在沙箱内执行（系统级夜间模式写入、cra-startup 三接线、LAN 同步能力）——验收窗口内沙箱只应经 instrumentation 进程运行。若发生手动启动 → S11。
- 正式包不 force-stop、不打开、不操作；窗口内设备不交给日常使用。

---

## 4. 阶段 2：测试执行（仅限两个已审查类）

> 【尚未执行】截至本次修订（2026-09-25），**未运行任何 instrumentation**；沙箱包安装成功**不**推及任何测试结论。进入本阶段需 §6 D4 验收窗口授权；窗口内仍仅限下表两个类。

### 4.1 允许的精确清单

| # | 类 | 方法数 |
|---|---|---|
| 1 | `com.creationreadingassistant.feature.reader.EpubParserInstrumentedTest` | 4 |
| 2 | `com.creationreadingassistant.feature.reader.EpubReplaceRoomIsolatedTest` | 6 |

其余 32 个 androidTest 类**未获放行**（"沙箱包构建成功"不推及全量）。

### 4.2 命令（分两次跑，便于归因；组件固定）

```
adb -s <serial> shell am instrument -w -e class com.creationreadingassistant.feature.reader.EpubParserInstrumentedTest com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner

adb -s <serial> shell am instrument -w -e class com.creationreadingassistant.feature.reader.EpubReplaceRoomIsolatedTest com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

推荐（可选）：运行前起 `adb -s <serial> logcat -v time > <仓库外>\instrument-logcat.txt`，用于证实 Runner 换 Application 生效（`App.onCreate` 未执行、无夜间模式/cra-startup 副作用）；logcat 原文属仓库外证据，报告只引中性摘要。

### 4.3 禁止项（任一尝试即 S7）

- `connectedQahostAndroidTest` / `connectedDebugAndroidTest` 等一切 Gradle connected 任务（会无差别运行全部 34 类）；
- 两个类之外的任何测试（包括"顺手跑全量"）；
- MuMu/模拟器；横屏；手动启动沙箱应用。

### 4.4 结果记录与已知混淆

- 每类记录：起止时间、shell 退出码、instrumentation 汇总（passed/failed/crashed 计数）、失败时的中性化栈摘要。
- **用例失败 ≠ 对账失败**：照实记录后照常进入阶段 3（数据保护网是对账，不是测试结果）；但设备级异常（device offline、instrumentation 进程崩溃挂死）→ S10。
- **已知混淆（诚实登记）**：正式包自身 WorkManager 定时任务（`search_index_daily_v1` 等）可能在窗口内自发执行并改写正式包 workdb ⇒ 测试后对账可能因此出现无法归因的差异。缓解 = 窗口尽量短；**不用取消任务来"消除"混淆**（取消本身是设备写且改变被对账状态）；出现差异一律按 S8 停，不用时间窗猜测归因。

---

## 5. 阶段 3：测试后捕获与前后对账

### 5.1 测试后归档捕获（需 D2 force-stop 再授权）

1. `adb -s <serial> shell am force-stop com.creationreadingassistant`；
2. `adb -s <serial> shell pidof com.creationreadingassistant` → 空；捕获完成后再测一次，仍空；
3. 按与 13:07 归档**相同的方法、四根、同序**流式捕获。13:07 备份实际使用的准确命令已确认（W3 已转绿）——注意 `tar -cf -` 后紧跟 `-C /data/data/com.creationreadingassistant`，四个根按 `databases files shared_prefs no_backup` 顺序——以**二进制流**写入仓库外私有文件（13:07 备份即写入 E 盘）：

   ```
   cmd /c "adb -s <serial> exec-out run-as com.creationreadingassistant tar -cf - -C /data/data/com.creationreadingassistant databases files shared_prefs no_backup > <仓库外私有目录>\s04-qahost-post-test.tar"
   ```

   二进制重定向必须用 `cmd /c "…"` 包裹，避免 PowerShell 文本管道改写字节；**本窗口不执行**；`-C /data/data/<pkg>` 与四个根的顺序**不得改动**；
4. 记录本机归档字节数 + `Get-FileHash -Algorithm SHA256`，与捕获时间、serial、命令原文一并写入仓库外私有证据 manifest。

若用户**不**授权 force-stop（D2 否）：改走快照方案 §3 的零设备写双通道变体，且必须先获用户对"强佐证·不可证明"一致性等级的接受；与基线捕获方法不同这一点必须写进执行报告。

### 5.2 对账执行（仓库根执行）

1. 使用前复核基线完整性锚（PowerShell 单行）：

   ```
   Get-FileHash -Algorithm SHA256 "E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\baseline-manifest.json"
   ```

   输出的 `Hash` 必须等于 `FE42381E4515A02F3ABA528066B0C07E1469DEE65FDD26639A94BED0D659D352`；不符 → S3。

2. （可选，建议）重跑工具自检 `python android/scripts/s04_private_archive_manifest_tests.py`，**24 例**全过再进入比较（数量以 C 报告 §9 为准；2026-09-25 实测 24/24、退出码 0）。

3. 比较（PowerShell **单行**；**必须**传 `--expect-baseline-sha256`）：

   ```
   python android/scripts/s04_private_archive_manifest.py compare --archive "<测试后归档.tar>" --baseline "E:\creation-reading-assistant-backups\s04-qahost-2026-09-25\baseline-manifest.json" --expect-baseline-sha256 FE42381E4515A02F3ABA528066B0C07E1469DEE65FDD26639A94BED0D659D352 --evidence-json "<仓库外私有路径>\diff-evidence.json"
   ```

   `--expect-baseline-sha256` 为**必填信任锚**（缺参 / 格式错误 / 不符一律 BLOCKED）——示例已含该参数，**不得删改**；

4. 退出码：`0` = PASS（逐字节一致）；`1` = 存在差异 → **UNATTRIBUTED → S8**；`2` = BLOCKED（归档不安全/期望哈希不符等）→ S3。

5. 对账范围：四根内容级（相对路径 + 字节数 + SHA-256；tar 元数据刻意忽略），自动覆盖正式 Room 库（`databases/`）、workdb 三件套（**实际位于 `no_backup/`**——旧审查文档 §5-D-13 写的 `databases/androidx.work.workdb` 路径已被归档实测推翻，报告不得再引用旧路径）、书库文件（`files/`，含 `files/books/epub/`）与偏好项（`shared_prefs/`）。工具不做 SQLite 解读：**不存在"行数未变即放行"的通道**；workdb 逐任务字段核对仅在差异归因阶段按用户指示进行。

### 5.3 正式主 APK 对账（安装前基线 → 测试后复测，逐项一致为唯一通过）

| 项 | 基线来源 | 通过标准 |
|---|---|---|
| `pm path com.creationreadingassistant` | 阶段 0 现场记录（预检参考值见 §0.2） | 完全一致 |
| versionName / versionCode | 0.5.1 / 3（阶段 0 复测值） | 完全一致 |
| base.apk SHA-256 | 阶段 0 现场记录（预检参考 `53d0c01b…85359ac`） | 完全一致 |

任一不一致 → S9。

### 5.4 "字节一致"能证明什么、不能证明什么（执行报告必须原文携带本节）

**能证明：** 在两个捕获各自的那个时刻，归档覆盖的正式包四个私有根（databases / files / shared_prefs / no_backup）所包含的文件集合与每个文件的内容（相对路径 + 字节数 + SHA-256）**取值相同**——即沙箱包安装与两个测试类的运行没有在**这两个捕获端点**留下可观测的数据改变。**该结论仅限两个捕获端点的覆盖文件一致**，不等于「窗口期间从未发生任何变化」。

**不能证明：**

1. **恢复能力**。归档与基线一致只说明"数据没动"，不说明"能还原"：恢复流程从未实机演练，恢复能力在演练完成或用户书面接受"未经演练"之前一律标 **UNVERIFIED**，不得写成"已验证可恢复"；
2. 覆盖范围之外无变化：Android Keystore 条目、系统设置、/sdcard 共享存储、包管理器状态、系统侧 JobScheduler 注册、其他应用的数据均不在归档内；
3. 捕获方法的事务级一致性：归档是 force-stop 后的文件级冷拷（强佐证级，非"捕获瞬间原子一致"的形式证明）；基线捕获的质量边界被原样继承到比较结论中；
4. 窗口内的瞬时变化：对账只比较两个端点时刻，两个端点之间发生又复原、且未在端点留下字节残留的变化不可见；因此「端点一致」**不得**表述为「窗口期间从未发生过任何瞬时变化」；
5. 权限/属主变化：工具刻意忽略 tar 元数据（mtime/mode/uid/gid），不感知权限类差异。

### 5.5 差异处理（UNATTRIBUTED 协议）

任何差异（单文件字节变化、新增、删除、根缺失，无一例外）→ 立即停止一切后续设备操作 → 报告只写差异计数与类别 → `diff-evidence.json` 留仓库外 → 归因仅在停止后按用户指示进行；恢复执行需用户明确批准。**不得以"差异看起来无害""与时间窗吻合"自行放行。**

### 5.6 收尾（对账 PASS 且用户批准后）

- 沙箱两包的卸载/保留 = 用户决策，不自动执行；
- `stay_on_while_plugged_in` 等临时设置恢复原值并记录；
- 执行报告 + 私有证据清单登记；基线与归档按 §6 非阻塞登记第 2 条的用户决定处置。

---

## 6. 用户决策清单（最小集；2026-09-25 第三次修订时点：**D1 已执行，D2/D3/D4 未决**）

只保留两类真正需要用户拍板的项：**设备写操作的授权**（D2 测试后 force-stop、D4 验收窗口；D1 安装**已批准并执行完毕**，降为历史记录）与**未经恢复演练的风险选择**（D3）。原「接受 E 盘基线」与「W1/B 报告处理」不再列为用户决策项——基线已建成并自比较 PASS（第 3 轮 24/24 + 真实归档自比较 PASS，C 报告 §9），B 报告已到齐（§0.3 W1/W3），二者不构成用户拍板门槛。

| # | 决策 | 说明 | 不批的后果 |
|---|---|---|---|
| D1 | **安装两个 qahost 包（`…qahost` / `…qahost.test`）的单独同意** | **已批准并执行完毕（历史时点，2026-09-25）**：host/test 两包经 `install_with_confirm.ps1` CONFIRM_LOOP 安装成功（退出码 0×2，§0.1/§3）。原门槛说明保留作审计：安装是设备写操作，静态审查通过不构成预授权（验机前审查 §0-2）；与快照方案 §7-6 同源、完全独立 | 已消解——安装已完成；**重装/卸载**属新的用户决策（测试后沙箱两包的卸载/保留见 §5.6，同样待用户决定） |
| D2 | **测试后归档捕获的 force-stop 再授权** | 用户此前允许为建立 E 盘备份暂时停止正式应用（2026-09-25 13:07 那次），**不等于**永久授权后续安装、测试或再次停止应用；本次需重新授权 | 阶段 3 改走零设备写双通道（需另接受其一致性等级）或停 |
| D3 | **接受恢复能力 UNVERIFIED 的残余风险**（或另行单独授权一次实机恢复演练） | 恢复流程从未实机演练；归档与基线一致只证明「数据没动」，不证明「能还原」；演练本身是设备写操作，不得默认并入本轮 | 维持 BLOCKED |
| D4 | **独立验收窗口的时机与执行人授权**（实施与审查分离） | 阶段 1–3 只在获授权窗口执行；窗口授权即授权其中的设备写操作（历史：安装**仅凭 D1** 完成，D4 从未批准；**阶段 2 测试与阶段 3 捕获仍待 D4**） | 阶段 2/3 不开始 |

非阻塞登记（不要求本轮决定，仅留痕）：

1. 设备遗留包 `com.creationreadingassistant.test`（旧同包 debug 测试包）与 `local.creationReadingAssistant.mobile`（旧 Capacitor 产品线）的保留/清理属用户后续决策；本轮不触碰。
2. E 盘归档与 `baseline-manifest.json` 的**保留期限与到期销毁方式**属用户后续决策，不阻塞真机验收；阶段 3 使用前以 §5.2 的哈希锚自行校验。

---

## 7. 停机条件（STOP；任一触发即停止一切设备操作并报告）

验机前审查 §7 B1–B9 全部继续有效；下表是其在本清单语境的落地与扩充。停机后：冻结现场（不回滚、不清理、不卸载），输出阻塞清单与证据位置，等用户决定。

| # | 条件 |
|---|---|
| S1 | 决策门槛未落定而试图进入对应阶段（历史：阶段 1 安装已凭 D1 完成；现行适用 = 进入阶段 2 须 D4、进入阶段 3 捕获须 D2 与 D3） |
| S2 | 版本闭合失败：APK 现场重算哈希 ≠ 锚点/B 报告；aapt 核包名/targetPackage/Runner 不符；`build.gradle.kts` 接线自审查后被改（= 预审 B1） |
| S3 | 基线/参考不可用：baseline-manifest.json 完整性锚不符；compare 退出码 2；W3 捕获命令与 §5.1 记录的参数顺序不一致（= 预审 B2/B9 域） |
| S4 | 设备状态漂移：serial 变化、多设备、非竖屏且用户未纠正、正式包版本或 base.apk 哈希与阶段 0 记录不符 |
| S5 | 设备已存在 qahost 主/测试包且用户未对既有数据作出处置决定（= 预审 B8）；或 uid 相同、authority 冲突、沙箱 workdb 非空且含非测试产生任务（= 预审 B3） |
| S6 | 安装回退未留证：任何无退出码/日志佐证的安装状态视为未发生；证据丢失即停（= 预审 B5） |
| S7 | 范围扩大企图：两个类之外的任何测试、Gradle connected 任务、MuMu/模拟器、横屏（= 预审 B6） |
| S8 | 对账出现任何无法归因为测试自身产物的差异 → UNATTRIBUTED → 停（= 预审 B4） |
| S9 | 正式主 APK 路径/版本/SHA-256 测试前后不一致（= 预审 B7）；或 `run-as` 中途失效 |
| S10 | 设备级异常：device offline、instrumentation 挂死、沙箱包引发系统异常 |
| S11 | 沙箱包在验收期间被手动启动（完整 `App.onCreate` 已执行、系统设置写入已发生）→ 停止测试，照常完成阶段 3 对账，事件写入报告 |
| S12 | 任何步骤需要超出本清单授权的设备写操作才能继续 → 停，升级为用户决策 |

---

## 8. 撰写窗口限制与隐私声明

- 本窗口零 adb、零 Gradle、零设备操作、零安装、零 instrumentation；未启动/停止应用；未触碰原始仓库（`creation-reading-assistant`）、真机数据、Claude 独立记忆库；未 commit / push / stage / reset / checkout / clean。
- 本窗口唯一写入 = 本文档；未修改任何既有报告、代码或 E 盘文件。
- 对 E 盘备份仅读取了 `README.md` 与 `baseline-manifest.json` 的 SHA-256（哈希重算，未读内容）；未读取任何书籍内容、数据库行或凭据。
- 全文使用中性称呼（"测试 EPUB""书库文件"）；设备 serial 脱敏（`c49***6cf`）；报告只含方法、聚合值、哈希与判定，不含真实书名、文件名、数据库行或偏好值。
- 2026-09-25 第二次修订窗口：零 adb、零 Gradle、零设备触碰、零安装、零 instrumentation；**未读取 E 盘任何文件**（含哈希重算），事实核对全部来自仓库内报告/脚本复读与测试文件清点；唯一写入 = 本文档；未 commit / push / stage / reset / checkout / clean。

---

## 9. 修订记录

| # | 时点 | 内容摘要 |
|---|---|---|
| 1 | 2026-09-25 撰写窗口 | 初版：分阶段执行清单（阶段 0–3、决策 D1–D6、停机 S1–S9），完全离线撰写 |
| 2 | 2026-09-25 第一次修订 | W1/W3 到齐、W2 部分转绿；决策表重排为 D1–D4（删「接受 E 盘基线」「W1 处理」）；全部示例改 PowerShell **单行**（去 `^`/`\` 续行）；compare 补必填 `--expect-baseline-sha256`；自检 21→23 例；「字节一致」限定为两个捕获端点；停机表扩至 S12 |
| 3 | 2026-09-25 第二次修订（本窗口） | ① 登记安装完成（历史时点）：host/test 经 `install_with_confirm.ps1` CONFIRM_LOOP、退出码 0×2、uid 与正式包不同、base.apk 未变——**安装成功 ≠ 测试通过**，未运行任何 instrumentation；② W1 更正为 B 收窄后的有限结论（不外推「源码逐字节未变」/运行期安全；Runner/targetPackage 经 aapt+dex 复核）；③ W2 转绿：C 第 3 轮并发覆盖窄修落盘，自检 **23→24 例**（24/24、退出码 0），真实归档只读自比较 PASS、0 差异、退出码 0，基线锚不变；④ 当前执行入口改为「安装后的只读复测与待决策事项」（§3.4），安装/重装步骤标历史、不得默认重装；⑤ §6：D1 标已执行（历史），D2（测试后 force-stop）/D3（恢复 UNVERIFIED 风险）/D4（验收窗口）保持待决策；⑥ §4 标注尚未执行，§7 S1 改为按阶段适用决策门槛 |
| 4 | 2026-09-25 第三次修订（本窗口） | **授权口径校正**：全文历史授权事实统一为「用户已授权安装（D1），两个 qahost 包已安装」；删除 §2 头注/§2.1/§2.4 的「阶段 0 门槛已放行/安装时点判据已满足」、§3.1 的「D4 已获明确批准」等错误表述；§3.1 改标「清单标准」并与「授权事实核对」分列；§6 D4 行改「安装仅凭 D1 完成，D4 从未批准」；安装阶段完成与测试/测试后对账未放行分清；历史安装命令保留但不引导重装 |
