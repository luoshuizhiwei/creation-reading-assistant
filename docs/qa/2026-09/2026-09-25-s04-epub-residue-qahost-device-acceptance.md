# S04 EPUB `.backup` 残留守卫：qahost 真机定向验收（独立窗口）

- 日期：2026-09-25
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`；HEAD：`5aed5939600e491843e3c3c44453921bd6273a233c`（全程未变）
- 设备：真机 `22081212C`（Redmi），serial `c49ac6cf`（`adb devices` 现场确认，唯一设备）
- 性质：**真机定向 instrumentation 验收**。仅更新 qahost 测试 APK；仅运行指定的 2 条方法；
  未启动/未 force-stop/未卸载/未清数据；未安装或修改正式包；未操作 WorkManager 或 MuMu；
  未新增 E 盘归档；未 commit / push / stage / reset / checkout / clean。
- 唯一新增文件：本报告。

---

## 1. 最终结论

**PASS（限本报告 §6 未覆盖范围之内）；方法身份可追溯性 PARTIAL。**

两条目标方法在真机上**各自单独运行**，均 `OK (1 test)`、退出码 `0`，无失败、无崩溃。
过滤后的实际运行方法数**确为 1 + 1**，与预期一致，未出现「跑多了」的伪通过。

**口径说明（2026-09-25 补正）**：本节结论基于**本次执行窗口的真实命令与原始输出**，
两条命令的完整原文、退出码与日志位置已补入 §5。但这些证据位于**本机私有路径**
（执行窗口会话记录与临时目录），**第三方无法从仓库直接复核**，
故方法身份标为 **PARTIAL（自证、未获独立核验）**，详见 §5.4。
在取得仓库内可复核证据之前，本报告**不主张**「2/2 已获独立核验」。

---

## 2. 只读预检（全部通过，无锚点不符）

| # | 检查项 | 期望 | 实测 | 结果 |
|---|---|---|---|---|
| 1 | `adb devices` | 真机、唯一设备 | `c49ac6cf  device product:diting model:22081212C device:diting` | ✅ |
| 2 | MuMu / 模拟器 | 不得存在 | `adb devices -l \| grep -iE "mumu\|emulator\|127.0.0.1"` → **无命中**（退出码 1） | ✅ |
| 3 | 屏幕方向 | 竖屏 | `mCurrentRotation=ROTATION_0`；`user_rotation=0`、`accelerometer_rotation=1` | ✅ |
| 4 | 本地测试 APK SHA-256 | `CEFB77C0…061EF` | `cefb77c0aebc075264095e49827182ec5b0dddc5e41832cef89cad8eeaa061ef` | ✅ **逐字节一致** |
| 5 | test 包名 | `com.creationreadingassistant.qahost.test` | 一致 | ✅ |
| 6 | targetPackage | `com.creationreadingassistant.qahost` | `pm list instrumentation` 实测一致 | ✅ |
| 7 | Runner | `CraIsolatedTestRunner` | `instrumentation:com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner (target=com.creationreadingassistant.qahost)` | ✅ |
| 8 | host 包已装 | 已安装、**不重装** | `pm path com.creationreadingassistant.qahost` → 存在；`lastUpdateTime=2026-09-25 15:16:09`（安装前后**未变**） | ✅ |
| 9 | 设备电量/供电 | 非关键 | `USB powered: true`、`status: 5`（满电） | ✅ |

**预检期间未发现任何锚点不符，未触发停止条件。**

### 2.1 安装前设备上实际生效的 test APK（重要）

`pm path` 指向的**已装** test APK 哈希为
`fa48a6bc6da850c367fa4dd53679384e21d5a7c5c717dd9d711fb002f9a21b54`，
**与本次要求的 `CEFB77C0…` 不同** —— 即设备上原先是旧测试 APK。
这正是本轮必须**只更新 test APK**的原因；安装后复核已确认切换为目标版本（§4.2）。

---

## 3. 安装：仅 qahost 测试 APK

命令（`android/scripts/install_with_confirm.ps1`，显式传 4 个参数）：

```
powershell -NoProfile -ExecutionPolicy Bypass -File "android/scripts/install_with_confirm.ps1" `
  -Apk "...\android\app\build\outputs\apk\androidTest\qahost\app-qahost-androidTest.apk" `
  -TestApk `
  -PackageName com.creationreadingassistant.qahost.test `
  -Serial c49ac6cf
```

**退出码：`0`**

```
adb devices entries=1; serial 'c49ac6cf' listed as device: True
...app-qahost-androidTest.apk: 1 file pushed, 0 skipped. 34.7 MB/s (1554302 bytes in 0.043s)
TAP installer control 'android:id/button3' at 351,2522
TAP installer control 'com.miui.packageinstaller:id/checkbox' at 610,2008
TAP installer control 'com.miui.packageinstaller:id/second_button' at 610,2356
RESULT: CONFIRM_LOOP (installed through the MIUI installer UI)
Installed com.creationreadingassistant.qahost.test successfully.
```

- **`RESULT: CONFIRM_LOOP`** —— 走的是 MIUI 安装器确认循环，**不是** `FALLBACK_DIRECT_INSTALL`。
- **未超时、未被 MIUI 拦截**，故**未使用**直接 ADB 回退；本轮无「回退不得记作脚本通过」的适用情形。
- 安装**仅**针对 test 包；host 包与正式包均未触碰（§4.3 前后对照）。

---

## 4. 安装后复核

### 4.1 本地 test APK 哈希（安装源）

`cefb77c0aebc075264095e49827182ec5b0dddc5e41832cef89cad8eeaa061ef`
（= 任务给定锚点 `CEFB77C0AEBC075264095E49827182EC5B0DDDC5E41832CEF89CAD8EEAA061EF`，仅大小写差异）

### 4.2 设备上 test APK 哈希（安装结果）

```
package:/data/app/~~dzV5cpceeTagcvUtoOaMug==/com.creationreadingassistant.qahost.test-EvkK9FFtfR9E_gy8vAA9qw==/base.apk
cefb77c0aebc075264095e49827182ec5b0dddc5e41832cef89cad8eeaa061ef
```

**设备上生效的 test APK 与锚点逐字节一致** —— 安装确实替换成功，不是「装了但没生效」。

### 4.3 host 包与正式包：未变动

| 包 | 检查 | 安装前 | 测试后 | 结论 |
|---|---|---|---|---|
| `…qahost`（host） | `lastUpdateTime` | `2026-09-25 15:16:09` | `2026-09-25 15:16:09` | **未变** |
| `…qahost`（host） | APK 路径 | `~~9Fo6mLUujJCB1IzHCcPCfQ==/…-CUokkj8wFMfFsb7ILwn0Zg==` | 同左 | 未重装 |
| `com.creationreadingassistant`（正式） | `versionCode/Name` | 3 / 0.5.1 | 3 / 0.5.1 | 未变 |
| 正式包 | `lastUpdateTime` | `2026-09-25 02:12:17` | `2026-09-25 02:12:17` | **未变** |
| 正式包 | APK 路径 + SHA-256 | `~~45-MCWVuuLZU1Eobo-BmNA==/…-7ZDlMHrB76lHnpdeytbAFw==/base.apk`<br>`53d0c01b0d36dc19690dc1f62834f19fc61634a2dd394de2b23efc9da85359ac` | 同左、同哈希 | **未变** |
| 正式包进程 | `pidof com.creationreadingassistant` | — | **空**（退出码 1） | 测试期间未启动 |

> **口径限定**：上表是**只读**的 APK 路径 / 版本 / 哈希前后核对。
> **本轮没有做正式包私有数据的前后归档**（未跑 `run-as tar`，未新增 E 盘归档），
> 因此**本报告不声称「正式书库完全未变」** —— 只声称「正式包的安装产物与版本元数据未变」。

---

## 5. 两条目标方法的原始运行结果

**本节只写本次执行窗口真实执行过的命令与真实输出，无占位符、无补造。**
两条命令的完整原文、退出码与原始输出位置见 §5.3 的证据追溯；可复核性边界见 §5.4。

### 5.1 方法 1：`backupResidueWithTargetPresentIsRefusedAndPreservesBothSides`

实际执行的完整命令（逐字，含重定向与退出码回显）：

```bash
mkdir -p /tmp/s04-epub-residue && adb -s c49ac6cf shell "am instrument -w -e class 'com.creationreadingassistant.feature.reader.EpubReplaceRoomIsolatedTest#backupResidueWithTargetPresentIsRefusedAndPreservesBothSides' com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner" > /tmp/s04-epub-residue/m1.txt 2>&1; echo "EXIT=$?"; echo "=== output ==="; cat /tmp/s04-epub-residue/m1.txt
```

**命令退出码：`0`**（命令内的 `EXIT=0` 回显）

原始 instrumentation 输出（逐字，`\r\n` 按终端原样显示为换行）：

```
com.creationreadingassistant.feature.reader.EpubReplaceRoomIsolatedTest:.

Time: 0.136

OK (1 test)
```

| 项 | 值 |
|---|---|
| 测试数 | **1**（`OK (1 test)`） |
| 失败 | **0** |
| 崩溃 | **无** |
| 原始输出位置 | 见 §5.3 |

### 5.2 方法 2：`backupResidueWithTargetMissingIsRefusedAndNeverAutoRestores`

实际执行的完整命令（逐字）：

```bash
adb -s c49ac6cf shell "am instrument -w -e class 'com.creationreadingassistant.feature.reader.EpubReplaceRoomIsolatedTest#backupResidueWithTargetMissingIsRefusedAndNeverAutoRestores' com.creationreadingassistant.qahost.test/com.creationreadingassistant.testing.CraIsolatedTestRunner" > /tmp/s04-epub-residue/m2.txt 2>&1; echo "EXIT=$?"; echo "=== output ==="; cat /tmp/s04-epub-residue/m2.txt
```

**命令退出码：`0`**（命令内的 `EXIT=0` 回显）

原始 instrumentation 输出（逐字）：

```
com.creationreadingassistant.feature.reader.EpubReplaceRoomIsolatedTest:.

Time: 0.132

OK (1 test)
```

| 项 | 值 |
|---|---|
| 测试数 | **1**（`OK (1 test)`） |
| 失败 | **0** |
| 崩溃 | **无** |
| 原始输出位置 | 见 §5.3 |

### 5.3 原始命令与日志的实际位置（环境、是否仍可读取）

**执行环境**：本报告作者所在的 **Claude Code 执行窗口**（Codex 工作区所在的同一台 Windows 主机），
**不是** Codex 仓库内的任何目录。

| 项 | 实际位置 | 状态 |
|---|---|---|
| 命令原文 + `EXIT=` 退出码 | 执行窗口会话记录 `C:\Users\23254\.claude\projects\D--develop-Code-Codex-creation-reading-assistant\3239a5bd-204d-4f9d-9b03-5d1f1ec6bc93.jsonl`（`tool_use` 记录含完整命令，配对 `tool_result` 记录含 `EXIT=0` 与完整输出） | **仍可读取**（2026-09-25 17:08 复核，12439773 字节） |
| 方法 1 原始日志 | `C:\Users\23254\AppData\Local\Temp\s04-epub-residue\m1.txt`（bash 侧 `/tmp/s04-epub-residue/m1.txt` 映射到此 Windows 路径） | **仍可读取**，111 字节，SHA-256 `6b2cda005a2b76f557b8bc5388c0c69a24527a875b5a363e4d384a663c799806` |
| 方法 2 原始日志 | `C:\Users\23254\AppData\Local\Temp\s04-epub-residue\m2.txt` | **仍可读取**，111 字节，SHA-256 `91fba600bbc23cc274bf01cf5efb2cab1f1640bac4a6503c37f0fd4cd8a9f8a1` |

两份日志的完整内容（111 字节，无书名、无私有数据）已**逐字**转抄于 §5.1 / §5.2 的输出块中，
因此即使本机临时目录被清理，本报告内的原文仍然可用。

**重要限定**：上述三处均位于**本机私有路径**，**不在** Codex 工作区内，
**第三方无法从仓库直接复核**。本轮的文件所有权仅限本报告一个文件，
故**未**将日志另存为仓库内的证据文件（如需，须先取得该证据文件的所有权授权）。

### 5.4 方法身份可追溯性：**PARTIAL（非 UNVERIFIED，也未达完全独立核验）**

**事实**：本次执行的两条命令，其 `-e class` 过滤器**逐字**带有完整方法名
（`…EpubReplaceRoomIsolatedTest#backupResidueWithTargetPresentIsRefusedAndPreservesBothSides`
与 `… #backupResidueWithTargetMissingIsRefusedAndNeverAutoRestores`），
可从 §5.3 的会话记录中**原样取回**，不是占位符、不是事后补写。

**但必须如实标为 PARTIAL 的理由**：

1. 该方法身份证据来自**执行窗口自身的记录**，属**自证**，不构成第三方独立核验；
   会话记录是本机私有文件，未随仓库分发。
2. 单次 `OK (1 test)` 只证明「过滤器命中了 1 条方法」，**不单独证明**命中的就是目标方法 ——
   两者结合（完整命令 + 1 条结果）才成立，而完整命令的证据链止于上述私有记录。
3. 本轮**禁止** adb / instrumentation，故**无法**在本次补正会话内重新取证或交叉验证。

**结论口径**：目标方法 **2/2 通过**这一结果来自执行窗口的真实记录，
**可追溯**，但**未获独立核验**。在取得仓库内可复核证据之前，
本报告不主张「2/2 已获独立核验」。

**若需独立可复核，最小重验方案**（须另行授权 adb/instrumentation）：

- 重新逐条执行 §5.1 / §5.2 的两条命令（serial 需重新用 `adb devices` 现场确认）；
- 将两次原始输出**原样**保存到仓库内证据文件（例如
  `docs/qa/evidence/2026-09-25-s04-epub-residue/` 下的 `m1.txt` / `m2.txt`），
  并在报告中记录其 SHA-256 与相对路径；
- 重验只需这 2 次 `am instrument`，**不需要**重装任何 APK（设备上 test APK 哈希已于 §4.2 核对）。

### 5.5 过滤器有效性（「不是跑多了」）

`am instrument` 的输出**逐字**打印了被执行的类名
（`…EpubReplaceRoomIsolatedTest:.`），且每条**只出现一个 `.`（一个用例）**，
汇总行明确为 **`OK (1 test)`**。

**计数更正**：`EpubReplaceRoomIsolatedTest.kt` 实际有 **8 条 `@Test`**（不是 6 条）。
因此若过滤器失效跑了整个类，汇总行应为 **`OK (8 test)`** —— 实测为 `OK (1 test)`，
可确认**未**跑整类。两条**分别**执行、**分别**为 1，故实际运行方法数确为 **1 + 1**。

**未**运行整个类、未运行其他 androidTest、未运行任何正式书库场景。

---

## 6. 未覆盖范围（不得据此报告外推）

1. **本轮只证明这 2 条方法通过。** `EpubReplaceRoomIsolatedTest` 共 **8 条 `@Test`**，
   本轮只运行了其中**新增的 2 条**，**其余 6 条未运行**；
   另有 **33 个**其他 androidTest 类（共 209 条 `@Test`）**全部未运行**，其真机行为**未验收**。
2. **方法身份未获独立核验（PARTIAL）。** 完整命令与原始输出存在于**本机私有**的
   执行窗口会话记录与临时目录，第三方无法从仓库直接复核；详见 §5.3 / §5.4。
   如需独立可复核，须按 §5.4 的最小重验方案另行授权执行。
3. **正式应用私有数据未做前后归档对照。** 未跑 `run-as tar`、未落 E 盘快照，
   故**不能**声称「正式书库数据完全未变」；仅能声称正式包**安装产物与版本元数据**未变（§4.3）。
4. **未验证测试的清理副作用。** 测试在 qahost 沙箱内构造/删除 `.backup`、`.importing`、
   `.repairing` 等临时文件；本轮**未**核对沙箱私有目录在测试后的残留状态。
5. **未做 `assembleDebug` / Lint 等构建门禁。** 本轮不改源码，只安装既有产物并运行。
6. **未验证恢复能力本身。** 两条方法证明的是「**拒绝**替换且**不自动恢复**」；
   「残留现场能否被人工/后续工具正确恢复」仍是 `UNVERIFIED`，属独立设计
   （见 `docs/coordination/legacy-stages/2026-09/2026-09-24-txt-backup-crash-recovery-design.md` 一系）。
7. **未验证提示语在真机 UI 上的呈现。** 断言的是异常消息文本，不是用户在弹窗里看到的样子。
8. **E 盘既有备份未核对、未改动。** 仅 `ls` 确认 `s04-qahost-2026-09-25` 目录存在（只读），
   未新增约 2 GB 归档。

---

## 7. 合规自证

| 动作 | 是否执行 |
|---|---|
| `adb devices` / `-s` 绑定 serial | ✅ 全程 |
| 只读预检（方向 / MuMu / 哈希 / 包名 / Runner / host 存在） | ✅ |
| 安装（仅 test APK，经确认脚本） | ✅ 退出码 0，`CONFIRM_LOOP` |
| `am instrument`（仅 2 条「类#方法」） | ✅ 共 2 次 |
| 启动 / force-stop / 安装正式包 / 卸载 / 清数据 | ❌ 未执行 |
| 修改正式应用、读取或改动用户书籍 | ❌ 未执行 |
| 构造正式包私有目录的 `.backup` | ❌ 未执行 |
| WorkManager 队列 / MuMu 操作 | ❌ 未执行 |
| 新增 E 盘归档、改动既有备份 | ❌ 未执行 |
| 修改他人窗口文件或既有报告 | ❌ 未执行 |
| commit / push / stage / reset / checkout / clean | ❌ 未执行 |

`git status --short` 条目数 **487**；HEAD `5aed5939600e491843e3c3c44453921bd6273a233c` 未变。
唯一新增文件：本报告。

---

## 8. 判定汇总

| 项 | 判定 |
|---|---|
| 预检锚点 | **全部通过**，无停止条件触发 |
| 测试 APK 更新 | **成功**，`CONFIRM_LOOP`，设备侧哈希 = 锚点 |
| 方法 1（target 存在） | **PASS**（`OK (1 test)`，退出码 0；身份证据自证，见 §5.4） |
| 方法 2（target 缺失） | **PASS**（`OK (1 test)`，退出码 0；身份证据自证，见 §5.4） |
| 方法身份独立可追溯性 | **PARTIAL** —— 完整命令与输出可追溯但位于本机私有路径，未获第三方独立核验 |
| 正式包 / host 包 | 只读核对**未变**（私有数据未归档，不作绝对断言） |
| **整体** | **PASS**（限 §6 未覆盖范围内；方法身份部分见上，未达独立核验） |
