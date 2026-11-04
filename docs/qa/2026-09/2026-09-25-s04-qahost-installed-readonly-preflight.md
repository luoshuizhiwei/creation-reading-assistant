# S04 qahost 安装后独立只读预检报告

- 日期：2026-09-25（检查窗口 16:00–16:05 前后，单窗口完成）
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 性质：**安装后的独立只读预检**。仅核对设备与包现状；未重装、未卸载、未运行任何 instrumentation、未启动/停止任何应用、未 force-stop、未清数据、未改任何系统设置、未操作正式书库、未读取 E 盘私有归档、未触碰原始仓库；未 commit / push / stage / reset / checkout / clean。
- 判据来源：`docs/qa/2026-09/2026-09-25-s04-qahost-device-runbook.md`（2026-09-25 **第三次修订**：授权事实仅限「用户已授权安装（D1），两包已安装」；当前执行入口 = §3.4 安装后只读复测；D2/D3/D4 未决；安装成功 ≠ 测试通过）。基准另参照 `docs/qa/2026-09/2026-09-25-s04-qahost-readonly-preflight.md` 登记的包路径与哈希锚。
- 本窗口唯一写入 = 本文档。
- **本报告只陈述现状核对结果，不构成、不暗示任何「测试已通过」结论**；instrumentation（阶段 2）未运行，其结果无从谈起。

---

## 1. 判定汇总（逐项）

| # | 检查项 | 判定 |
|---|---|---|
| 1 | 唯一真机与设备身份 | **PASS** |
| 2 | qahost 两包已安装；Runner/targetPackage 正确；UID 与正式包隔离 | **PASS** |
| 3 | 正式包 APK 路径 / 版本 / base.apk SHA-256 与登记基准一致 | **PASS** |
| 4 | `run-as` 正式包可用；沙箱进程当前未运行；竖屏 | **PASS**（「未运行」的证明口径见 §6） |
| 5 | 本地两只 qahost APK SHA-256 与 runbook 锚点一致 | **PASS** |

**无 BLOCKED 项**，未触发 runbook §7 任何停机条件（S4/S5/S9 等均未触发）。以下全部为事实记录。

---

## 2. 命令与输出记录（serial 统一脱敏为 `c49***6cf`；实际执行时逐条显式 `adb -s <serial>`）

| # | 实际命令（脱敏） | 退出码 | 关键输出（脱敏摘录） |
|---|---|---|---|
| 1 | `adb devices -l` | 0 | 1 台设备：`c49***6cf`，状态 `device`，product `diting`，model `22081212C` |
| 2 | `adb -s c49***6cf shell getprop ro.product.brand / ro.product.model / ro.product.device / ro.build.version.release / ro.build.version.sdk`（5 条） | 0（各条） | `Redmi` / `22081212C` / `diting` / `15` / `35` |
| 3 | `adb -s c49***6cf shell pm list packages`（本地过滤 `creationreadingassistant`） | 0 | 命中 5 包：`com.creationreadingassistant`、`com.creationreadingassistant.qahost`、`com.creationreadingassistant.qahost.test`、`com.creationreadingassistant.test`（遗留）、`local.creationReadingAssistant.mobile`（遗留） |
| 4 | `adb -s c49***6cf shell pm path com.creationreadingassistant` | 0 | 单行：`/data/app/~~…==/com.creationreadingassistant-…==/base.apk`（与预检报告登记路径逐字符一致；无 split） |
| 5 | `adb -s c49***6cf shell dumpsys package com.creationreadingassistant`（本地过滤版本/uid） | 0 | `versionCode=3`、`versionName=0.5.1`、`uid=10949 … prot=signature` |
| 6 | `adb -s c49***6cf shell dumpsys package com.creationreadingassistant.qahost`（同过滤） | 0 | `versionCode=3`、`versionName=0.5.1`、`uid=10957`、`pkgFlags=[ DEBUGGABLE … ]` |
| 7 | `adb -s c49***6cf shell dumpsys package com.creationreadingassistant.qahost.test`（同过滤） | 0 | `versionCode=0`、`versionName=null`（androidTest APK 正常态）、`pkgFlags=[ DEBUGGABLE … ]`；**该 dumpsys 无 uid 行**（故改用 #10 取 uid） |
| 8 | `adb -s c49***6cf shell pm list instrumentation` | 0 | 命中：`instrumentation:com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner (target=com.creationreadingassistant.qahost)`；另有遗留 `com.creationreadingassistant.test/androidx.test.runner.AndroidJUnitRunner (target=com.creationreadingassistant)`（runbook §6 非阻塞登记项，本轮未触碰） |
| 9 | `adb -s c49***6cf shell run-as com.creationreadingassistant id` | 0 | `uid=10949(u0_a949) gid=10949(u0_a949) …` |
| 10 | `adb -s c49***6cf shell run-as com.creationreadingassistant.qahost id` | 0 | `uid=10957(u0_a957) …` |
| 11 | `adb -s c49***6cf shell run-as com.creationreadingassistant.qahost.test id` | 0 | `uid=10958(u0_a958) …` |
| 12 | `adb -s c49***6cf shell sha256sum <正式包 base.apk 设备端路径>` | 0 | `53d0c01b0d36dc19690dc1f62834f19fc61634a2dd394de2b23efc9da85359ac` |
| 13 | `adb -s c49***6cf shell "stat -c '%s %n' <同路径>"` | 0 | `53532616`（字节） |
| 14 | `adb -s c49***6cf shell pidof com.creationreadingassistant.qahost` | 1（空输出） | 无进程 → **当前未运行** |
| 15 | `adb -s c49***6cf shell pidof com.creationreadingassistant.qahost.test` | 1（空输出） | 无进程 → **当前未运行** |
| 16 | `adb -s c49***6cf shell pidof com.creationreadingassistant`（仅登记，不干预） | 0 | pid `20817` → 正式包**当前在运行**；`dumpsys window displays` 同时显示其 MainActivity 在显示栈中（仅类名级信息，无任何书库内容） |
| 17 | `adb -s c49***6cf shell dumpsys window displays`（本地过滤 orientation/rotation/尺寸） | 0 | `mDisplayRotation=ROTATION_0`，bounds `0,0 - 1220, 2712`（高>宽），overrideConfig 含 `port` → **竖屏** |
| 18 | `Get-FileHash -Algorithm SHA256 android\app\build\outputs\apk\qahost\app-qahost.apk` | —（cmdlet，见输出） | `064F53341475CCFDC898205ADFB36451E380F3A49C8C014AECEA9373BC7DC054` |
| 19 | `Get-FileHash -Algorithm SHA256 android\app\build\outputs\apk\androidTest\qahost\app-qahost-androidTest.apk` | —（cmdlet，见输出） | `FA48A6BC6DA850C367FA4DD53679384E21D5A7C5C717DD9D711FB002F9A21B54` |

登记备注：#13 之前有一次被中止的同命令变体（PowerShell 引号导致 `%n` 被误拆，`stat` 报参数错误、退出码 1），设备端只读无任何影响，已按 #13 的正确引号重跑取得干净的退出码 0；两个 attempt 均只读。

---

## 3. 逐项核对明细（对照基准）

### 3.1 设备与身份（PASS）

- 唯一设备，状态 `device`，无第二台、无 offline、无模拟器条目；serial 与 runbook 脱敏值 `c49***6cf` 一致。
- 身份：Redmi / `22081212C`（diting）= Redmi K50 Ultra 同型；Android 15 / SDK 35 —— 与 runbook §0.2 及预检报告登记一致。

### 3.2 qahost 两包、instrumentation 与 UID 隔离（PASS）

| 项 | 实测 | 基准（runbook §3.4 / B 报告 §5） | 判定 |
|---|---|---|---|
| host 包安装 | `pm list packages` 命中 `com.creationreadingassistant.qahost` | 已安装（§0.1 执行窗口报告值，本轮现场独立证实） | ✅ |
| test 包安装 | 命中 `com.creationreadingassistant.qahost.test` | 已安装 | ✅ |
| 恰好新增两包 | qahost 命名空间内恰为此二包（其余两条为 runbook 已登记的遗留包） | §3.4-1「恰好新增两包」 | ✅ |
| instrumentation | `com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner`，`target=com.creationreadingassistant.qahost` | §3.4-2 同值 | ✅ |
| host versionCode/Name | 3 / 0.5.1 | 3 / 0.5.1 | ✅ |
| UID 隔离 | 正式 10949 / host 10957 / test 10958，三者互不相同 | §3.4-3 uid 与正式包（预期 10949）不同 | ✅ |

### 3.3 正式包基线对账（PASS）

| 项 | 本轮实测 | 登记基准 | 判定 |
|---|---|---|---|
| `pm path` | 单一 base.apk，路径与预检报告登记值**逐字符一致**（无 split） | 预检报告「包路径」登记值 | ✅ |
| versionCode / versionName | 3 / 0.5.1 | runbook §0.2 / §5.3：3 / 0.5.1 | ✅ |
| 设备端 base.apk SHA-256 | `53d0c01b0d36dc19690dc1f62834f19fc61634a2dd394de2b23efc9da85359ac` | runbook §0.2 参考值（同值） | ✅ |
| base.apk 字节数 | 53,532,616 | runbook §0.2（53,532,616 字节） | ✅ |

该组一致是「安装动作未触碰正式包本体」的即时佐证（runbook §3.4-4）。

### 3.4 最小只读环境检查（PASS，附证明口径）

- `run-as com.creationreadingassistant id` → 退出码 0，`uid=10949(u0_a949)`，与 runbook 预期一致（runbook W4 的「run-as 可用」一项在本窗口现场复测成立）。
- 沙箱进程：host 与 test `pidof` 均空、退出码 1 → **当前未运行**（见 §6 口径）。
- 方向：`ROTATION_0`、1220×2712、`port` → 竖屏，未触碰任何旋转设置。
- 本窗口未读取任何数据库行、书籍内容或私人文件名；`run-as … id` 仅输出进程身份，未列目录、未读文件。

### 3.5 本地 APK 锚点（PASS）

| APK | 本地实测 SHA-256 | runbook §2.2 锚点 | 判定 | 字节数 / mtime |
|---|---|---|---|---|
| `app-qahost.apk` | `064F5334…BC7DC054` | `064F5334…BC7DC054` | ✅ | 51,666,652 / 2026-09-25 07:53:45 |
| `app-qahost-androidTest.apk` | `FA48A6BC…F9A21B54` | `FA48A6BC…F9A21B54` | ✅ | 1,516,628 / 2026-09-25 07:53:10 |

仅哈希与 stat，未构建、未改写 APK。两只 APK 与 runbook 锚点（= 集成报告 = B 报告三方锚）逐字节一致。

---

## 4. 「当前未运行」与「从未运行过」的区分（诚实登记）

- `pidof` 空（退出码 1）只能证明**检查瞬间** host / test 沙箱进程不存在；它**不能**证明「自安装以来从未运行过」——已退出或被系统回收的进程同样查不到。
- 本窗口未做任何能证明「从未运行」的设备端取证（如读取沙箱数据目录、历史日志），也不允许为证明此点引入新的设备读取。「从未运行」目前只有 runbook §0.1 的执行窗口报告值（「未运行任何 instrumentation、图标未手动打开」）作**报告级**支撑，无设备侧独立证明。
- 辅助观察（非证明）：沙箱包为全新安装态的常规迹象（host versionCode/Name 与正式包同源、`DEBUGGABLE`、dumpsys 未见异常标志），但这些都不能区分「从未运行」与「运行过又退出」。
- 后续若需强佐证，属阶段 3 对账与 D2/D4 授权范围，本窗口不推进。

---

## 5. 合规自证

| 动作 | 是否执行 |
|---|---|
| `adb devices -l`、getprop、pm path / pm list、dumpsys（package/window）、sha256sum（设备端）、stat、pidof、`run-as … id` —— 全部只读 | ✅（#1–#17） |
| `adb install` / 卸载 / 重装 / 清数据 | ❌ 未执行 |
| `am instrument` / `am start` / force-stop / 启动或停止任何应用 | ❌ 未执行 |
| 修改系统设置（含旋转、stay_on_while_plugged_in） | ❌ 未执行 |
| 读取数据库行、书籍内容、私人文件名、文件清单 | ❌ 未执行 |
| 读取 E 盘私有归档 | ❌ 未执行 |
| 触碰原始仓库 / 他人窗口文件 | ❌ 未执行 |
| commit / push / stage / reset / checkout / clean | ❌ 未执行 |

本窗口唯一写入 = 本文档；除 serial 脱敏外，报告不含真实书名、数据库行、逐文件清单或敏感私有路径。

---

## 6. 与 runbook 的衔接（仅登记，不推进）

- runbook §3.4 四项只读复测在本窗口全部现场复测通过（其第 3、4 项此前为执行窗口报告值，现为独立留证）。
- 阶段 2（两个已审查类的 instrumentation）与阶段 3（force-stop 后归档捕获与对账）**未运行、未获授权，本报告不涉及也不放行**；D2 / D3 / D4 仍待用户决定。
- 遗留包 `com.creationreadingassistant.test` 与 `local.creationReadingAssistant.mobile` 仍存在，属 runbook §6 非阻塞登记项，本轮未触碰。
