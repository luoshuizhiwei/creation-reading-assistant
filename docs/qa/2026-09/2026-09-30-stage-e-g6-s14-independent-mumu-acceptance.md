# 阶段 E：G6 与 S14+R3 候选 MuMu 独立限定验收报告（2026-09-30）

- **验收角色**：阶段 E 独立验收者（只读产品源码，独占 MuMu / ADB）
- **基线仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`，HEAD `513891d6a66a9b78e072ba759d34ed580d377ecf`，暂存区空）
- **设备性质说明**：**本验收完全限定于已授权 MuMu 模拟器（adb 端点 `127.0.0.1:16416`，实例 1 "洛水之蔚"，`SM-S9280` / Android 12 / x86_64），绝非物理真机实测，结论不外推实体手机**
- **独占证据目录**：`E:\creation-reading-assistant\stage-e-g6-s14-independent-acceptance-20260930\`
- **唯一可写共享报告**：本报告
- **操作纪律**：不修产品源码；不触碰正式书库、实体手机、原仓库；不执行 stage/commit/push/reset/checkout；不删除或搬移目录。**失败时保存原始日志并如实记录，未修测试、未自行修复后跑绿。**

---

# §0 最新实际结果（2026-09-30 执行，置顶）

## 0.1 结论摘要

| 项 | 结果 |
|---|---|
| **A. G6 手势仪器测试（6 条）** | **3 通过 / 3 失败**（`Tests run: 6, Failures: 3`，ADB 退出码 0）。失败**全部为测试自身节点定位歧义**（`HomeStat` 隐形占位 Text 与可见值 Text 同文本同坐标，`onNodeWithText` 命中 2 节点），**非产品行为断言失败**；按纪律未修改测试、未重跑至绿 |
| **A. G6 手工 UI 行为（替代通道）** | **通过**：身份区 / 时长区 / 原可点空白区点击**均打开「我的阅读」**；长按时长区**切换单位且不导航**（DataStore 实证）；默认 HOURS ✓；显式 DAYS ✓；进程重启持久化 ✓ |
| **B. S14+R3 仪器测试（2 类 8 条）** | **8/8 通过**（`LocalZipBackupPersistentRestoreTest` OK 6 tests；`LocalExportRoomRollbackTest` OK 2 tests），退出码均 0 |
| **C. TalkBack** | **实际播报未验**（设备**未安装** TalkBack/读屏服务，仅一个旋转无障碍服务）；语义断言亦未在设备完成（G6 语义用例因同一节点歧义失败）——**明确未验，不阻塞其它项** |
| **D. 设备与数据恢复** | **完全恢复**：原 APK（`e82904cc…` / `fdbd0190…`）重装成功；**53 项持久文件清单与基线逐字节 IDENTICAL**；密度 280、夜间 no、常亮 0 全部回读一致 |
| **总体判定** | **B 通过；A 的产品行为经手工通道通过、仪器用例 3/6（测试缺陷）；C 未验；D 完成。** 不得表述为"G6 设备验收全通过" |

## 0.2 候选来源与 APK 核验（安装前逐枚 SHA 复核）

| 候选 | READY 结构 | host APK SHA-256（磁盘=READY=设备） | test APK SHA-256（磁盘=READY=设备） |
|---|---|---|---|
| G6 行为保留 | `apk` 字段，绝对路径 `<dir>/android/app/build/outputs/apk/…` | `7d39b0a02ca230f2aa4eaa4a211ea478e4d7ee196854afa9c5fa483b335780ea` | `428d58f4c7045c84422d6a531387b16052e892789ab0fe48bce4de4de879240e` |
| S14+R3 备份闭合 | `apks` 字段，相对路径（项目根=`stage-e-s14-r3-backup-closed-20260930`，无 android 子目录） | `b7ce65df96c7f3732a07d3f929cc90cd77eccfc02473c8e8593b805e1f06bf37` | `97609ba0078da7e7e6f6e5f062107b02086400417b5ade0b635f0b0d75c46771` |

- 两候选的 Android 项目根**结构不同**，已按各自 READY 实际结构解析（未硬编码同一目录模板）；4 枚 APK 的磁盘 SHA 与 READY 逐字节一致，**未重建、未替换**。
- 候选切换记录：**A 阶段安装 G6 两枚**（设备侧 SHA `7d39b0a0…`/`428d58f4…`，日志 `logs/A1-installed-g6-shas.txt`）→ **B 阶段安装 S14+R3 两枚**（设备侧 SHA `b7ce65df…`/`97609ba0…`，日志 `logs/B1-installed-s14-shas.txt`）→ **D 阶段重装归档原件**（`logs/D3-restored-apk-shas.txt`）。

## 0.3 A. G6 验收详情

### A-1 环境（420dpi 竖屏手机宽度）

`adb -s 127.0.0.1:16416 shell wm density 420` → `Physical size: 1080x1920` / `Override density: 420`（≈411dp 手机宽度，`user_rotation=0` 竖屏）。日志 `logs/A0-display-before-install.txt`。**全部截图实测 1080x1920 portrait**（§0.6）。

### A-2 手势仪器测试（原始结果，未修饰）

```
adb -s 127.0.0.1:16416 shell am instrument -w \
  -e class com.creationreadingassistant.ui.screen.profile.ProfileHomeScreenGestureTest \
  com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
→ ADB 退出码 0；Tests run: 6, Failures: 3
```

| # | 用例 | 结果 |
|---|---|---|
| 1 | `click_identity_row_opens_reading_archive` | **PASS** |
| 2 | `click_non_gesture_stat_label_still_opens_reading_archive` | **PASS** |
| 3 | `inspiration_stat_click_goes_to_inspirations` | **PASS** |
| 4 | `click_duration_value_still_opens_reading_archive` | **FAIL**（节点歧义） |
| 5 | `long_press_duration_value_toggles_exactly_once_and_does_not_navigate` | **FAIL**（节点歧义） |
| 6 | `card_semantics_hold_the_single_toggle_and_leaf_text_holds_none` | **FAIL**（节点歧义） |

**失败根因（最小复现）**：`ProfileHomeScreen` 的 `HomeStat` 同时渲染**隐形占位 Text**（`format(value)`，`Color.Transparent`）与**可见 count-up Text**，二者**文本与 bounds 完全相同**（失败输出两节点均为 `(l=152,t=324,r=521,b=406)px`、`Text = '[72 小时 0 分钟]'`）。测试用 `onNodeWithText(<时长文本>, useUnmergedTree = true)` 期望唯一节点，实际命中 2 个 → `fetchOneOrThrow` 抛 `Expected exactly '1' node but found '2'`。**这是测试夹具的节点定位缺陷**（产品在合并语义树下仍为单节点）；用例 1–3 用唯一文本（身份标题/统计标签/灵感标签）故通过。
- 原始证据：`logs/A2-gesture-instrument.log`（完整堆栈）、`logs/A2-gesture-summary.txt`、`logs/A2-failure-logcat.txt`。
- **未采取**：修改测试（如 `onAllNodesWithText(...)[0]` 或改用标签定位）后重跑至绿——按任务纪律禁止。

### A-3 手工 UI 行为验证（产品行为独立通道）

| 检查项 | 操作 | 结果 | 证据 |
|---|---|---|---|
| 身份区点击打开档案 | tap 身份标题（366,334） | **PASS**（进入「我的阅读」页：页头+筛选 chips+书目列表） | `screenshots/A5-click-identity.png`、`xml/A5-click-identity.xml` |
| 时长区点击打开档案 | tap 时长值（333,533） | **PASS**（同页特有文本命中） | `A6-click-duration.png/.xml` |
| 原可点空白区点击打开档案 | tap 无手势统计标签（657,592） | **PASS**（同页特有文本命中） | `A7-click-blank.png/.xml` |
| 长按切换且不导航 | 长按（1000ms）时长区 | **PASS**：DataStore `duration_unit` 翻转（`days`→`hours`），页面**未导航**（仍在我的页） | `A9-longpress-after.png`、`A10-longpress-motion.png`、`xml/A8/A9/A10` |
| 默认 HOURS | 首次启动读数 | **PASS**：我的页显示「2 小时 53 分钟」；启动前 `duration_prefs` 文件**不存在**（默认 HOURS） | `screenshots/A4-profile.png`、`xml/A4-profile.xml` |
| 显式 DAYS | 外观页「时长按「天」显示」开关行 tap | **PASS**：DataStore 写为 `days` | `A13-switch-days.png` |
| 进程重启持久化 | force-stop → 重启（TotalTime 1486ms） | **PASS**：`duration_unit` 仍为 `days` | `A14-after-restart.png/.xml` |
| 跨页同源 | 我的页与「我的阅读」页时长文本 | **部分**：两页均走公共 `formatDuration` + 同一单位状态；但当前数据总时长 2h53m **< 24h**，HOURS 与 DAYS 样式对该值**同形**（「2 小时 53 分钟」），设备上**无法用现有数据区分单位显示**；≥24h 进位由 JVM `TimeUtilsTest`（13/13，含 `days unit rolls over at 24 hours`）覆盖 | `A4/A14` 截图与 XML |

> **显示判别限制如实登记**：本设备 qahost 数据总阅读时长不足一天，单位切换在**文本渲染上不可见**（这是 `TimeUtils` 的有意语义：DAYS 样式对不足一天的值按小时呈现）。单位状态以**外观页开关状态 + DataStore 偏好**为设备证据，渲染差异以 JVM 用例为证据，**不宣称设备端看到 24h 进位显示**。

## 0.4 B. S14+R3 验收详情

```
adb -s 127.0.0.1:16416 shell am instrument -w -r -e log true -e class <class> \
  com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner
```

| 类 | 用例 | 结果 |
|---|---|---|
| `LocalZipBackupPersistentRestoreTest`（6） | `newerLocalRowKeepsNewerFileBytesOnSuccessfulRestore` | PASS |
| | `zipBackupRoundTripRestoresFiveCategoriesAndFilesAcrossCloseReopen` | PASS |
| | `zipWithoutBackupJsonIsRejectedBeforeTouchingPersistentDb` | PASS |
| | `fileCopyMidWriteFailureKeepsExistingUniqueSourceFileIntact` | PASS |
| | `correctionForeignKeyFailureRollsBackAllCategoriesInPersistentDbAndStaysRolledBackAfterReopen` | PASS |
| | `replaceFailureKeepsTargetAndThrowsInsteadOfDirectOverwrite` | PASS |
| `LocalExportRoomRollbackTest`（2） | `importFailureRollsBackEarlierBookWriteAndRelationTableClearInRealRoom` | PASS |
| | `olderBackupKeepsNewerBookAndSourceReferenceInRealRoom` | PASS |
| **合计** | **8/8** | **OK**，两类 ADB 退出码均 **0**（`INSTRUMENTATION_CODE: -1`＝成功） |

覆盖映射（任务点名项）：**来源引用/规则/纠错** → 五类实体+文件跨关闭重开、纠错外键回滚；**版本保护** → 本地较新行/旧备份保留较新书与来源引用（2 条）；**事务回滚** → 纠错外键失败回滚全类别且重开仍回滚、真实 Room 导入失败回滚（2 条）；**持久重开** → `AcrossCloseReopen`/`StaysRolledBackAfterReopen`；**正文文件安全** → 写中途失败保留既有唯一源文件、替换失败保留目标并抛错（2 条）。
- 证据：`logs/B2-persistent-restore.log`、`logs/B2b-persistent-restore-named.log`、`logs/B3-room-rollback.log`、`logs/B3b-room-rollback-named.log`。

## 0.5 C. TalkBack（明确未验）

- `pm list packages | grep -iE 'talkback|accessibility'` → 仅 `com.tile.rotation2`（旋转无障碍服务）；**未安装 TalkBack**，`enabled_accessibility_services` 无读屏项。
- 因此：**实际播报证据不可取得 → 明确未验**（不阻塞其它项）。
- 语义断言：G6 用例 6（`card_semantics_hold_...`）本应在设备断言「卡片节点持唯一 `OnLongClick`（label=切换时长显示单位）＋时长文本叶子无手势」，但该用例因节点歧义在**取节点阶段即失败**，**设备语义断言未完成**。源码级结构结论（卡片为唯一手势层、双 label、子文本无手势）来自 `2026-09-30-stage-e-g6-behavior-preserved.md`，**不等于**设备语义/读屏验证。

## 0.6 D. 恢复与前后对账

| 项 | 基线（前） | 恢复后 | 判定 |
|---|---|---|---|
| qahost 主 APK | `e82904cc…4ef8a` | `e82904cc…4ef8a` | **一致**（`logs/D3-restored-apk-shas.txt`） |
| qahost.test APK | `fdbd0190…27038d` | `fdbd0190…27038d` | **一致** |
| 53 项持久文件 | 清单 53 行 | **IDENTICAL**（0 added / 0 removed / 0 changed） | **逐字节一致**（`logs/D5-verdict.txt`） |
| 密度 | 280 | 280（`wm density reset`） | **一致** |
| 系统夜间模式 | no | no | **一致** |
| 常亮 / 旋转 | 0 / 1,0 | 0 / 1,0 | **一致** |

恢复过程（`logs/D2-restore.log`）：force-stop → 删除测试期新增的 `files/datastore/duration_prefs.preferences_pb`（基线不存在）→ 从冷备 tar 回写 4 个测试期变更文件（`files/profileInstalled`、`shared_prefs/{ai_secrets,sync_config,webdav_config}.xml`，均 `chown u0_a70` + `chmod 660`）→ 重装原 APK → `wm density reset`。**主数据库与全部书籍文件全程未被修改**（D1 对账显示 changes 不含 `databases/*` 与 `files/books/*`）。

**截图实际方向**：全部 9 张实测 **1080x1920 portrait**（420dpi 手机宽度竖屏），无横屏截图。

**设备进程与 adbd 状态**：本轮开工时 MuMu 实例 1（`127.0.0.1:16416`）**未运行**，由本窗口通过 `MuMuManager.exe api -v 1 launch_player` 启动；验收结束后**保持运行**（便于后续窗口按需接管），未做其它设备级改动。`adb root` 在验收期间启用（数据目录读写所需），结束时以 `adb unroot` 复位为 shell 权限（adbd 状态非持久设备设置）。

## 0.7 未覆盖项（不得当作通过）

1. **G6 仪器手势用例 3/6**：失败为测试夹具节点歧义，未修复未重跑；**产品行为由手工通道验证**，两者口径分开。
2. **TalkBack 实际播报**：设备无读屏服务，未验；设备语义断言未完成。
3. **≥24h 单位显示差异**：现有数据不足一天，设备端不可见；仅 JVM 覆盖。
4. **实体手机 / 正式包 / 正式书库**：未触碰（任务约束）。
5. **G6 与 S06/其它批次的联合态**：未在本轮验证（本报告仅限两候选各自 READY 的 APK）。
6. **S14 的真实 WebDAV、跨设备来源 URI、kill 注入**：不在本轮两测试类范围。

---

# 以下为历史记录（2026-09-30 12:45+ 的 WAITING 状态与验收规程，保留备查）

## 一、测试前设备基线与原装状态冷备存证

### 1.1 设备运行环境基线

| 参数项 | 设备现场实测值 | 校验与约束说明 |
|---|---|---|
| **设备 Serial** | `127.0.0.1:16416` | MuMu 模拟器实例 1 独占端口（`player_state=start_finished`） |
| **设备机型 / 系统** | `SM-S9280` / Android 12 (API 32, x86_64) | 模拟器默认多核配置 |
| **屏幕物理密度** | `Physical density: 280` | `wm density` 输出，初始无 override |
| **系统夜间模式** | `Night mode: no` | `cmd uimode night` 输出（浅色模式） |
| **插电保持常亮** | `0` | `settings get global stay_on_while_plugged_in` |
| **已安装应用包** | `com.creationreadingassistant.qahost`<br>`com.creationreadingassistant.qahost.test` | **正式包 `com.creationreadingassistant` 未安装、未触碰** |

### 1.2 原装已安装 APK 哈希（比对基准）

| 包名 | SHA-256 校验和 |
|---|---|
| 宿主包 `com.creationreadingassistant.qahost` | `E82904CC9C1315466ED5C521693F93F541560FEEF038BF074CE7F7CF8324EF8A` |
| 测试包 `com.creationreadingassistant.qahost.test` | `FDBD019011088505FA37D71565259A0D76F6927407AF69148379E2EF7C27038D` |

> 该两枚 APK 已保全至 `backup/original-app-qahost.apk` / `backup/original-app-qahost-test.apk`，D 阶段用于恢复并复核。

### 1.3 持久状态清单与整包冷备

- **持久文件清单**：`./databases ./files ./shared_prefs` 共 **53 个持久文件**，SHA-256 清单 `backup/before-sha256-manifest.txt`；主库 `creation_reading_assistant_native` = `7bd076c6…99a28`。
- **整包冷备**：`backup/qahost_backup_before.tar`（1,181,988,864 字节，SHA-256 `CAE16E820BCAFB1E269FAD7682F44C832581BFB986E5CCE6FA81084B388026F0`）。
- **现场适用性复核（本轮开工时）**：已装 APK SHA = 归档原件；重扫持久清单 53 项与基线**逐字节一致**（`logs/site-manifest-now.txt`）⇒ 归档原件与冷备**适用**，未重新存证。

## 二、候选就绪状态与等待记录（WAITING，历史）

| 候选单元 | 目标 READY 路径 | 当时检测状态 |
|---|---|---|
| **候选 1：G6 行为保全候选** | `E:\creation-reading-assistant\stage-e-g6-behavior-preserved-20260930\READY.json` | **WAITING（当时不存在）** |
| **候选 2：S14+R3 备份闭合候选** | `E:\creation-reading-assistant\stage-e-s14-r3-backup-closed-20260930\READY.json` | **WAITING（当时不存在）** |

> 执行纪律：严格遵循"READY 缺失时不安装旧 APK，也不称验收完成"；当时未对设备做任何安装或数据修改。**本轮（2026-09-30 后续）两 READY 均已到达，§0 为实际执行结果。**

## 三、待执行验收规程（当时预设，本轮已执行）

### 3.1 G6 时长单位切换与卡片手势限定验收
1. 校验 `READY.json` 中的 APK SHA-256、源码变更与构建日志。→ **已执行**（§0.2）
2. 配置手机宽度 420 dpi。→ **已执行**（§0.3 A-1）
3. 验证默认 `HOURS`、显式切 `DAYS`、各页面同源显示、进程重启持久化。→ **已执行**（§0.3 A-3，含显示判别限制登记）
4. 运行卡片手势仪器测试。→ **已执行**（§0.3 A-2：3/6，测试缺陷；产品行为另经手工通道 §0.3 A-3）
5. 记录 TalkBack 语义断言与实际播报。→ **已执行**（§0.5：实际播报未验）

### 3.2 S14+R3 备份恢复安全限定验收
1. 校验 READY 中 APK SHA 及 androidTest APK。→ **已执行**（§0.2）
2. 运行 `LocalZipBackupPersistentRestoreTest` 与 `LocalExportRoomRollbackTest`。→ **已执行**（§0.4：8/8）
3. 验证备份类别完整性、来源引用/规则/纠错持久化、版本保护、事务回滚与崩溃安全。→ **已执行**（§0.4 覆盖映射）

### 3.3 设备事后恢复与对账
1. 重装原装两枚 APK。→ **已执行**（§0.6）
2. 回写冷备并逐文件核对 53 个持久文件。→ **已执行**（§0.6：IDENTICAL）
3. 恢复密度与系统设置。→ **已执行**（§0.6）

---

## 四、纪律声明

- 共享恢复仓库内**唯一写入 = 本报告**；未改产品源码、测试、配置、其他窗口候选或 READY。
- **未** stage / commit / push / reset / checkout / stash；**未**删除或搬移目录；**未**触碰原仓库、正式包、正式书库、实体手机。
- 全部构建/日志/截图/XML/脚本在独占证据目录 `E:\creation-reading-assistant\stage-e-g6-s14-independent-acceptance-20260930\`（E 盘）。
- 失败项（G6 3/6）保留原始日志与最小复现，**未修测试、未重跑至绿**；TalkBack 未验如实标注；未把编译/手工通道结果写作仪器全通过。
