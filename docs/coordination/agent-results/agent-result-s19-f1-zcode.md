# S19-F1 / F2 实施报告：androidTest APK 安装后的错误验包风险修复

- **执行**：Zcode（窗口 B）
- **仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- **分支**：`codex/recover-worktree-20260920`
- **固定审查基点**：`5aed5939600e491843e3c44453921bd6273a233c`
- **任务**：S19-F1（P1 止血）+ **S19-F2（Codex 复核发现的 Spec P2 补口）**
- **未运行**：真实 ADB、设备操作、Gradle
- **未 commit / push / stage**
- **状态**：**等待 Codex 独立复核**（不预先宣称关闭）

---

## 0. 复核后补口（S19-F2）

Codex 对 F1 的独立核验结论：原始 P1 已被 F1 覆盖，fake-adb 契约测试 65/65、两个脚本语法 0 error；但**残留 1 个 Spec P2**。

**P2 内容**：显式传入仅含空白的包名 `-TestApk -PackageName '   '` 时，`$PSBoundParameters.ContainsKey('PackageName')` 返回 **true**，因此 F1 的守卫被绕过，脚本开始调用 ADB。Codex 在清空 PATH、完全无设备的探针中复现：脚本运行到了第一个 adb 调用，而非在参数校验阶段失败。

**F2 关闭方式**：把「参数在不在」与「值能不能用」拆成两个判据，用一个谓词覆盖三种无效值：

```powershell
$packageNameExplicit = $PSBoundParameters.ContainsKey('PackageName')
$testPackageNameMissing =
    -not $packageNameExplicit -or
    [string]::IsNullOrWhiteSpace($PackageName)

if ($TestApk -and $testPackageNameMissing) { ...; exit 1 }
```

`IsNullOrWhiteSpace` 同时覆盖 `$null` / `''` / 纯空白，因此三条路径（省略、空串、空白）统一在任何 ADB 动作之前失败。守卫条件改为读取收敛后的 `$testPackageNameMissing`，`ContainsKey` 仍然保留（用于区分「省略」与「显式传了主包名」这一合法用法）。

**F2 复现与修复验证**（无设备探针，PATH 前置假 adb）：

| 阶段 | 退出码 | adb 日志 |
|---|---|---|
| 修复前（F1 代码） | 1（但已是设备失败） | `-s probe-serial devices` / `get-state` ×2 / `shell rm -f` ← **已触碰设备** |
| 修复后（F2 代码） | **1** | **文件未生成（零 adb 调用）** |

> 说明：F1 代码下探针的最终退出码也是 1，但那是 `Android device 'probe-serial' is not available.` 的设备失败，不是参数校验失败——这正是 Codex 报告的实质。修复后失败发生在参数阶段。

**测试新增**：B9（空白包名）、B10（空字符串）两个行为场景，共 16 项新断言。

**空字符串投递能力说明**（对应任务第 3 条要求）：Windows PowerShell 5.1 经 `-File` 传参时，**显式空字符串会被命令行解析器整体丢弃**——脚本收到的是"缺少参数 PackageName"的绑定错误，而不是 `''`。我用探针实测确认：

```
& $psExe -File probe.ps1 -PackageName ''    → MissingArgument（参数被丢弃）
& $psExe -Command "& 'probe.ps1' -PackageName ''"  → explicit=True value=[] isWS=True
同进程 & 'probe.ps1' -PackageName ''          → explicit=True value=[] isWS=True
```

因此 B10 改用 `-Command` 投递（新增 `ConvertTo-PsSingleQuoted` 与 `-VerbatimPackageName` 开关），**空字符串场景已真实覆盖并通过**，不是伪覆盖。B9（空白）不依赖该机制，`-File` 即可传递，同时它也走 `-VerbatimPackageName` 以保持两条路径一致。

---

## 1. 修改文件清单（F1 + F2 累计）

| # | 文件 | 变更 | 相对基点状态 |
|---|---|---|---|
| 1 | `android/scripts/install_with_confirm.ps1` | F1：+23 行 `-TestApk` 守卫；**F2：守卫条件改为 `$testPackageNameMissing`（覆盖 null/空串/空白）** | 已修改（工作区既有脏改动 + 本次） |
| 2 | `android/scripts/install_with_confirm.contract.tests.ps1` | F1：+99 / -5 行；**F2：+静态断言 A20b-A20d、行为场景 B9/B10、`-VerbatimPackageName` 投递与 `AdbLogExisted` 判据** | 已修改（本次） |
| 3 | `docs/plans/android-parallel-delivery-2026-09-09/COMMON.md` | F1：+10 / -2 行，安装示例按主 APK / androidTest APK 拆分 | 已修改（**F1 期间**；F2 未触碰） |
| 4 | `docs/coordination/agent-results/agent-result-s19-f1-zcode.md` | 本报告 | 新增 |

**F2 本轮只修改了第 1、2、4 项。** `COMMON.md` 的 mtime 为 `2026-09-22 02:42`，早于 F2 对两个脚本的改动（`02:59` / `03:01`），可佐证 F2 未触碰它。

**未触碰**（`git status` 复核确认保持原状）：`android/scripts/ui_now.xml`、`.gitignore`、`android/scripts/diag_usb_confirm.py`、`android/scripts/tap_usb_install_confirm.py`、其他安装脚本、其他源码/测试/文档。

> 说明：上述 4 个被禁文件在 `git status` 中仍显示为未跟踪（`??`）——**这是我未触碰它们的证据**，不是我的改动。S19 调查已记录 `ui_now.xml` 含具体测试书名，本轮**未读取、未删除、未改写**它（mtime 仍为 `2026-09-16 02:12`）。

---

## 2. 根因与修复策略

### 2.1 根因

`-TestApk` 是 `[switch]`，旧实现里它**只影响一处**：`Invoke-DirectInstall` 里的 `adb install -r -t`。

```powershell
# 修复前（install_with_confirm.ps1:117-119）
$installArgs = @('install', '-r')
if ($TestApk) { $installArgs += '-t' }
$installArgs += $apkPath
```

包名是**另一条正交轴**，`-TestApk` 完全没碰它。`$PackageName` 的默认值恒为 `'com.creationreadingassistant'`（主应用），于是当调用方安装 androidTest APK 却省略 `-PackageName` 时：

- 确认循环（`:215`）执行 `adb shell pm path com.creationreadingassistant`；
- direct 回退（`:122`）执行同样的查询；
- **主应用原本已安装** ⇒ `pm path` 返回 `package:...` ⇒ 匹配 `'^package:'` ⇒ 判定"安装成功"。

脚本随后打印 `RESULT: CONFIRM_LOOP` 与 `Installed com.creationreadingassistant successfully.`，**退出码 0**。而这次运行真正安装的可能是测试包，也可能因签名不匹配**根本没装上任何东西**——脚本仍报成功。

**这是"虚假成功"型 P1**：它污染的正是真机验收证据本身。旧 `COMMON.md` 又允许 `-Serial`/`-Apk` 两个参数就安装 androidTest APK，使该路径成为文档背书的常规用法。

### 2.2 修复策略

在**脚本入口、任何 ADB/设备操作之前**加守卫：

```powershell
# install_with_confirm.ps1:16-20
# 在默认值被消费之前捕获「调用方是否显式传了 -PackageName」。
# $PSBoundParameters 是唯一能区分「调用方省略」与「调用方恰好写了默认字面量」的手段。
$packageNameExplicit = $PSBoundParameters.ContainsKey('PackageName')

# install_with_confirm.ps1:22-52（F2 之后的最终形态）
# 「参数在不在」与「值能不能用」是两个独立问题：
#   ContainsKey 回答前者；IsNullOrWhiteSpace 回答后者（覆盖 null / '' / 纯空白）。
$packageNameExplicit = $PSBoundParameters.ContainsKey('PackageName')
$testPackageNameMissing =
    -not $packageNameExplicit -or
    [string]::IsNullOrWhiteSpace($PackageName)

if ($TestApk -and $testPackageNameMissing) {
    Write-Host "ERROR: -TestApk requires a non-empty -PackageName."
    Write-Host "The androidTest APK's package name cannot be derived from the main package: build variants may append an applicationIdSuffix (for example '.test' or '.r3gate.test')."
    Write-Host "Pass the test APK's real package name, for example:"
    Write-Host "  -TestApk -PackageName com.creationreadingassistant.test"
    Write-Host "RESULT: FAILED (-TestApk requires a non-empty -PackageName; the test APK package name cannot be inferred)"
    exit 1
}
```

设计要点：

1. **用 `$PSBoundParameters.ContainsKey('PackageName')`，不比较 `$PackageName`。** 比较值无法区分"调用方省略"与"调用方显式传了主包名"；后者是合法用法（B7 场景），必须继续工作。
2. **必须同时用 `[string]::IsNullOrWhiteSpace($PackageName)`。** 仅凭 `ContainsKey` 只回答"参数在不在"：调用方显式传 `''` 或 `'   '` 时它返回 true，空包名会一路走到 adb。**这正是 Codex 复核发现的 Spec P2（S19-F2）。**
3. **布尔值在脚本作用域、默认值被消费之前捕获**，后续任何代码改动都不会让判定漂移。
4. **守卫位于第一条 adb 调用之前**（源码中守卫在第 41 行，唯一裸 adb 调用在第 76 行 `& adb -s $Serial @args`）。缺参/空包名时脚本**不触碰设备**。
5. **`exit 1` 显式设置退出码**，不受 `$ErrorActionPreference = 'Stop'` 或 `finally` 块影响（守卫在 `try` 之前，故 `finally` 不执行）。
6. 失败输出含可机器识别的 `RESULT: FAILED`，并同时点名 `-TestApk` 与 `-PackageName`。

**向后兼容**：守卫条件是 `$TestApk -and $testPackageNameMissing`。普通主 APK 不传 `-TestApk` ⇒ 条件恒假 ⇒ 零行为变化。`$PackageName` 默认值 `'com.creationreadingassistant'` 未改动。

---

## 3. 为什么不自动追加 `.test`

**产品裁决已明确：不得推导 `主包名 + .test`。** 技术理由如下（我在代码中独立复核了这一点）：

1. **applicationIdSuffix 是构建期变量。** androidTest 变体的包名由 `testApplicationId` / `applicationIdSuffix` 决定，可以是 `.test`、`.r3gate.test`、`.debug.test` 等任意后缀。脚本**没有**读 `build.gradle.kts` 或 AGP 输出的能力，只能靠猜。
2. **猜错的代价与现状一样严重，但更隐蔽。** 若脚本猜 `com.creationreadingassistant.test` 而真实包名是 `...r3gate.test`，`pm path` 会返回**空**，脚本判失败——但错误信息会指向"包不存在"，而真实原因是"脚本猜错了包名"。用户会去查应用是否装上了，而不是查脚本。
3. **猜测会制造第二套包名真源。** 一旦脚本内嵌 `.test` 规则，它就会与 `build.gradle.kts` 分叉；将来改后缀必须同时改脚本，而脚本里的那份**不会**随构建配置更新。
4. **显式传参是唯一无歧义的做法。** 调用方（Codex / WorkBuddy）在发起安装时已经知道自己在装哪个变体，把包名作为必填参数传进来，成本一次，收益是"验包与安装目标必然一致"。

因此守卫选择**快速失败**而非**自动补全**。

---

## 4. 新增 / 调整的契约测试矩阵

### 4.1 新增静态断言（A20-A20d、A21-A26，共 11 项）

| 编号 | 断言 | 防的是什么 |
|---|---|---|
| A20 | 源码用 `$PSBoundParameters.ContainsKey('PackageName')` | 退化成比较 `$PackageName` 值（无法区分"省略"与"显式传主包名"） |
| **A20b** | 源码用 `[string]::IsNullOrWhiteSpace($PackageName)` | **（F2）** 只判存在性 ⇒ 空白/空包名绕过守卫 |
| **A20c** | 无效判定收敛到 `$testPackageNameMissing` 单一变量 | **（F2）** 判定散落多处，条件分叉 |
| **A20d** | 判定同时含 `-not $packageNameExplicit -or` | **（F2）** 只判空白而丢掉"省略"这条路径 |
| A21 | 守卫条件为 `$TestApk -and $testPackageNameMissing` | 条件写错（如漏 `-and`、只判 `$TestApk`）导致普通主 APK 也被拦 |
| A22 | 失败输出含 `RESULT: FAILED` 且点名 `-TestApk` | 失败不可机器识别 / 错误信息不含关键参数名 |
| A23 | 不得用固定 `.test` 后缀拼接推导包名 | 有人后来"顺手"加回自动补全 |
| A24 | 能在源码中定位 `-TestApk` 守卫 | 守卫被删除（配合 A25/A26 提供行号证据） |
| A25 | 能在源码中定位裸 adb 调用点 | 同上 |
| A26 | 守卫行号 **早于** 第一条裸 adb 调用行号 | 守卫被挪到设备操作之后（"先连设备再校验"） |

> 静态断言只是护栏；**行为测试（B5/B9/B10）才是主要证据**，见 §4.2。

### 4.2 新增行为场景（B5-B8）

| 场景 | 输入 | 断言 |
|---|---|---|
| **B5** `-TestApk` 缺 `-PackageName` | `-TestApk`，不传 `-PackageName`；fake adb 配置为主包**存在**（`CRA_FAKE_PM_PATH=1`，正是旧实现的虚假成功条件） | B5.1 退出码 1；B5.2 含 `RESULT: FAILED`；B5.3 点名 `-TestApk`；B5.4 点名 `-PackageName`；B5.5 无 `CONFIRM_LOOP`；B5.6 无 `FALLBACK_DIRECT_INSTALL`；B5.7 无 `Installed ... successfully`；**B5.8 fake adb 日志为空（零 adb 调用）** |
| **B6** `-TestApk -PackageName <测试包名>` | `-PackageName com.creationreadingassistant.r3gate.test`（带变体后缀，验证非固定 `.test`）；fake adb 仅在 `pm path` 收到**该包名**时返回成功 | B6.1 退出码 0；B6.2 `RESULT: CONFIRM_LOOP`；B6.3 未落回退；B6.4 确实发起了 `pm path`；B6.5 日志中所有 `pm path <X>` 的 X 恒等于测试包名 |
| **B7** 普通主 APK 显式传主包名 | `-PackageName com.creationreadingassistant`（不带 `-TestApk`） | B7.1 退出码 0；B7.2 `RESULT: CONFIRM_LOOP`；B7.3 所有 `pm path` 用主包名 |
| **B8** `-TestApk` 回退仍带 `-t` | `-TestApk -PackageName <测试包名>`，UI 停在未完成态 ⇒ 触发 direct 回退 | B8.1 退出码 2；B8.2 `RESULT: FALLBACK_DIRECT_INSTALL`；B8.3 日志含 `install -r -t` |
| **B9**（F2）`-TestApk -PackageName '   '` | 显式纯空白包名，经 `-VerbatimPackageName`（`-Command`）投递；fake adb 配置为主包**存在** | B9.1 退出码 1；B9.2 `RESULT: FAILED`；B9.3 点名 `-TestApk`；B9.4 点名 `-PackageName`；B9.5 无 `CONFIRM_LOOP`；B9.6 无 `FALLBACK_DIRECT_INSTALL`；B9.7 无 `Installed ... successfully`；**B9.8 fake adb 日志文件根本未生成（零 adb 调用）** |
| **B10**（F2）`-TestApk -PackageName ''` | 显式空字符串，经 `-VerbatimPackageName`（`-Command`）投递 | 同 B9 的 8 项（B10.1-B10.8） |

### 4.3 强化的既有场景

| 编号 | 变更 | 理由 |
|---|---|---|
| B1.7 | 新增：普通主 APK 安装命令**不带** `-t` | 锁住 `-TestApk` 的既有语义（它才加 `-t`） |
| B1.8 | 新增：省略 `-PackageName` 时 `pm path` 仍用默认主包名 | **普通主 APK 向后兼容的直接证据** |
| B6.5 / B7.3 | 由子串匹配改为「收集全部 `pm path <X>` 后断言集合」 | 见 §4.4 |
| **B5.8 / B9.8 / B10.8**（F2） | 由"日志内容为空"升级为"**日志文件根本未生成**"（新增 `AdbLogExisted`） | fake adb 第一行无条件写日志，文件不存在 ⇔ 一次都没被调用；比读空串更强 |

### 4.4 fake adb / 测试宿主的三处加固

**(a) 新增 `CRA_FAKE_PM_EXPECT`：包名白名单。**
fake adb 的 `shell pm` 分支现在会在 `CRA_FAKE_PM_EXPECT` 非空时**拒绝任何其他包名**：

```bat
if not "%CRA_FAKE_PM_EXPECT%"=="" if not "%6"=="%CRA_FAKE_PM_EXPECT%" (
  echo Error: package %6 not found 1>&2
  exit /b 1
)
```

这使 B6 具备**因果性**：若脚本仍用主包名验证，fake adb 会拒绝，确认循环走不到成功态。B6.2 的 `RESULT: CONFIRM_LOOP` 因此是"用了正确包名"的真实后果，而非恒真断言。

**(b) 断言改为集合判据。**
测试包名 `com.creationreadingassistant.r3gate.test` **以主包名为前缀**，`-notmatch 'pm path com.creationreadingassistant'` 这类子串判据**永远为真**，测不出任何东西。改用：

```powershell
$pmQueried = @([regex]::Matches($r6.AdbLog, 'pm path (\S+)') | ForEach-Object { $_.Groups[1].Value })
Assert ($pmQueried.Count -gt 0 -and ($pmQueried | Where-Object { $_ -ne $testPkg }).Count -eq 0) ...
```

**（这是我在 F1 首轮运行中自己发现的测试缺陷，已修正；见 §5.2。）**

**(c)（F2）新增 `-VerbatimPackageName` + `ConvertTo-PsSingleQuoted`。**
`-File` 无法投递空字符串（见 §0）。该开关把整个调用重建为 `-Command "& '<script>' ... -PackageName ''"`，使 `''` 与 `'   '` 能真实到达脚本参数绑定。B10 若投递失败会在绑定阶段报错并让 B10.1 失败——**不会伪造通过**。

---

## 5. 实际执行命令、退出码、通过/失败数量

### 5.1 基线（修改前）

```
cd android/scripts
powershell -NoProfile -ExecutionPolicy Bypass -File ./install_with_confirm.contract.tests.ps1
```
- **退出码 0**
- **37 项断言全通过，失败 0**
- `ALL_CONTRACT_CHECKS_PASSED`

### 5.2 修改后首轮（暴露 3 项测试自身缺陷）

```
powershell -NoProfile -ExecutionPolicy Bypass -File ./install_with_confirm.contract.tests.ps1
```
- **退出码 1**，61 项断言，失败 3

失败项与根因（**全部是测试断言缺陷，不是产品缺陷**）：

| 失败断言 | 根因 |
|---|---|
| `A20` | 断言正则写成 `ContainsKey\(''-PackageName''\)`，多了连字符；真实源码是 `ContainsKey('PackageName')`。**断言写错，产品正确。** |
| `B6.5` | 子串判据被前缀关系击穿（§4.4b）。**断言写错。** |
| `B6.6` | 断言"确认循环路径日志含 `install -r -t`"，但确认循环**不做** `adb install`（它走 MIUI 安装器 UI）。该断言前提不成立。**已改为独立场景 B8（回退路径）验证 `-t` 保留。** |

修正后重跑通过。

### 5.3 修改后最终（F1 阶段）

```
powershell -NoProfile -ExecutionPolicy Bypass -File ./install_with_confirm.contract.tests.ps1
```
- **退出码 0**
- **65 项断言全通过，失败 0**
- `ALL_CONTRACT_CHECKS_PASSED`

断言分布（F1 阶段）：静态契约 A0-A26 = **27 项**；行为契约 B1-B8 = **38 项**。

### 5.4 变异验证（F1，证明 B5 真的能抓住缺陷）

用**临时副本**（Windows TEMP，非仓库文件）删除守卫段，对变异体运行同一套测试：

```
# 变异体：删除 install_with_confirm.ps1 的守卫块（1195 字符），其余逐字保留
powershell -NoProfile -ExecutionPolicy Bypass -File ./install_with_confirm.contract.tests.ps1 -ScriptPath <TEMP>\s19f1-mutant\mutated.ps1
```
- **退出码 1**，65 项断言，**失败 11**
- 关键失败证据：
  - `FAIL B5.1 退出码 1 -- exit=0` ← **旧行为正是"退出码 0 的虚假成功"**
  - `FAIL B5.8 零 adb 调用（fake adb 日志为空） -- log='-s fake-serial-0001 devices ...` ← 缺参时仍连了设备
  - `FAIL B5.2 / B5.3 / B5.4 / B5.5 / B5.7`
  - `FAIL A21 / A22 / A24 / A26`

⇒ **测试对目标缺陷敏感，不是恒真断言。** 变异体已删除。

### 5.5 F2 修复前复现（Codex 报告的 Spec P2）

无设备探针（`%TEMP%\s19f2-probe`，PATH 前置假 adb，假 adb 只记日志后 `exit /b 1`）：

```
powershell -NoProfile -ExecutionPolicy Bypass -File install_with_confirm.ps1 \
    -Serial probe-serial -Apk <probe>\fake.apk -TimeoutSeconds 2 -TestApk -PackageName '   '
```
**F1 代码的结果**：退出码 1，但 adb 日志含

```
-s probe-serial devices
-s probe-serial get-state
-s probe-serial get-state
-s probe-serial shell rm -f /sdcard/Download/...
```

⇒ **已执行设备查询与清理**，失败原因是"设备不可用"而非"参数非法"。**Codex 的 P2 成立，本机复现一致。**

### 5.6 F2 修复后验证（同一探针）

**F2 代码的结果**：退出码 **1**，输出

```
ERROR: -TestApk requires a non-empty -PackageName.
...
RESULT: FAILED (-TestApk requires a non-empty -PackageName; the test APK package name cannot be inferred)
```
**adb 日志文件未生成（零 adb 调用）。**

### 5.7 F2 修改后完整契约测试

```
cd android/scripts
powershell -NoProfile -ExecutionPolicy Bypass -File ./install_with_confirm.contract.tests.ps1
```
- **退出码 0**
- **84 项断言全通过，失败 0**
- `ALL_CONTRACT_CHECKS_PASSED`

断言分布（F2 后）：静态契约 A0-A26（含 A20b/A20c/A20d）= **30 项**；行为契约 B1-B10 = **54 项**。

新增/变更断言确认已执行（非跳过）：

```
PASS  A20b 用 [string]::IsNullOrWhiteSpace 判定 -PackageName 值是否可用
PASS  A20c 无效包名判定收敛到单一变量
PASS  A20d 无效包名判定同时覆盖「省略」与「传了无效值」两条路径
PASS  A21  守卫条件为 -TestApk 且包名无效（省略或空白）
PASS  A26  守卫位于第一条 adb 调用之前（缺参/空白包名时不触碰设备）
PASS  B5.1 … B5.8   （8 项，缺参场景，含日志文件未生成）
PASS  B9.1 … B9.8   （8 项，纯空白包名场景）
PASS  B10.1 … B10.8 （8 项，空字符串场景）
```

### 5.8 F2 变异验证（证明 B9/B10 真的能抓住缺陷）

把守卫**回退到 F1 形态**（仅 `-not $packageNameExplicit`，删掉 `IsNullOrWhiteSpace`），其余逐字保留：

```
powershell -NoProfile -ExecutionPolicy Bypass -File ./install_with_confirm.contract.tests.ps1 -ScriptPath <TEMP>\s19f2-mutant\mutated.ps1
```
- **退出码 1**，84 项断言，**失败 16**
- 关键失败证据：

| 失败断言 | 证据 | 含义 |
|---|---|---|
| `B9.8` | `existed=True log='-s fake-serial-0001 devices` | **空白包名绕过守卫并调用 adb**（正是 Codex 的 P2） |
| `B10.1` | `exit=0` | 空字符串场景下的**虚假成功** |
| `B10.8` | `existed=True log='-s fake-serial-0001 devices` | 空字符串同样绕过 |
| `A20b / A20c / A20d / A21 / A24 / A26` | 源码判据缺失 | 静态护栏同步失效 |
| `B9.3 / B9.4 / B10.2 / B10.3 / B10.4 / B10.5 / B10.7` | 无 `RESULT: FAILED` / 出现成功文案 | 失败不可识别 |

⇒ **B9/B10 对目标缺陷敏感，不是恒真断言。** 变异体已删除。

### 5.9 语法检查

```
powershell -NoProfile -Command "[System.Management.Automation.Language.Parser]::ParseFile(...)"
```
- `install_with_confirm.ps1`：**0 parse errors**
- `install_with_confirm.contract.tests.ps1`：**0 parse errors**

### 5.10 未运行

- **未运行真实 ADB**（fake adb 是 `%TEMP%` 下的 `adb.cmd` shim，仅在测试/探针进程的 `PATH` 前置）。
- **未连接或操作任何手机**。
- **未运行 Gradle**。
- 探针与变异体全部位于 Windows TEMP，**已删除**；TEMP 已确认无 `s19f2-*` / `probe-*` / `cra-contract-*` / `cra-install-ui-*` 残留。

---

## 6. 普通主 APK 向后兼容证据

| 证据 | 内容 |
|---|---|
| **代码层** | 守卫条件 `$TestApk -and $testPackageNameMissing`。不传 `-TestApk` ⇒ 条件恒假 ⇒ 守卫不进入。`$PackageName` 默认值 `'com.creationreadingassistant'` **未改动**。 |
| **B1（既有场景）** | 省略 `-PackageName` 且不传 `-TestApk` ⇒ 退出码 0、`RESULT: CONFIRM_LOOP`；**B1.8 新增**：日志中所有 `pm path <X>` 的 X 恒等于 `com.creationreadingassistant`。 |
| **B1.7（新增）** | 普通主 APK 安装命令不含 `-t` —— `-TestApk` 的既有语义未被守卫改动。 |
| **B2（既有）** | `-NoDirectInstall` 超时 ⇒ 退出码 1、`RESULT: FAILED`、原因含 `NoDirectInstall`。 |
| **B3（既有）** | direct fallback ⇒ 退出码 **2**、`RESULT: FALLBACK_DIRECT_INSTALL`、含 `NOT a confirm-loop PASS`。 |
| **B4（既有）** | malformed XML ⇒ 退出码 1、解析失败计数 > 0、打印 `XML_PARSE_ERROR_SAMPLE`。 |
| **B7（新增）** | 显式传主包名（不传 `-TestApk`）⇒ 退出码 0、`RESULT: CONFIRM_LOOP`、`pm path` 用主包名。证明"显式化包名"本身不改变主 APK 契约。 |

**B1-B4 四个既有场景在修改后全部继续通过**，这是向后兼容的直接回归证据。

---

## 7. fake adb 的零调用证据

B5 场景中，`CRA_FAKE_ADB_LOG` 指向 `%TEMP%\cra-contract-<guid>\adb.log`。fake adb 的**第一行**就是无条件记录：

```bat
if defined CRA_FAKE_ADB_LOG echo %*>> "%CRA_FAKE_ADB_LOG%"
```

因此"日志文件不存在"等价于"**adb 一次都没被调用**"。

- **F2 起断言**：`Assert (-not $r5.AdbLogExisted -and [string]::IsNullOrWhiteSpace($r5.AdbLog)) '...'`（B5.8 / B9.8 / B10.8 三处）
- **结果**：PASS
- **补充证据**：B5/B9/B10 场景**未**设置 `CRA_FAKE_UI_XML`，但 fake adb 仍会被 shim 解析——脚本若走到 `push`/`pull` 阶段，日志必非空。日志文件**根本未生成**证明脚本在**第一条 `adb devices` 之前**就退出了。
- **静态对应**：A26 断言守卫行号早于裸 adb 调用行号（守卫第 41 行 vs adb 第 76 行）。
- **反证**：F2 变异体（回退到 F1 守卫）下 B9.8/B10.8 报 `existed=True`，日志含 `-s fake-serial-0001 devices` —— 说明该判据确实在测真实行为。

---

## 8. 已知未覆盖项

| # | 未覆盖 | 原因 / 风险 |
|---|---|---|
| U1 | 未在**真实设备**上验证 `-TestApk -PackageName <真实测试包名>` 的完整安装链 | 本轮禁止 ADB/真机。fake adb 只能证明"脚本传了正确包名"，不能证明真实 MIUI 安装器接受该 APK。 |
| U2 | 未验证真实测试包名到底是不是 `com.creationreadingassistant.test` | 需要读 `build.gradle.kts` 的 `testApplicationId`/`applicationIdSuffix` 或 AGP 输出。**本报告刻意不猜**，COMMON.md 也只给示例写法。 |
| ~~U3~~ | ~~未覆盖 `-TestApk -PackageName ''`~~ | **已由 S19-F2 关闭。** 原判为 P3 是**错误分级**：Codex 复核指出它不只是"后续会失败"，而是**绕过守卫并触碰设备**（Spec P2）。现由 B9（空白）+ B10（空串）两个行为场景覆盖，守卫改用 `IsNullOrWhiteSpace`。 |
| U4 | 未覆盖 `-TestApk` + `-PackageName` 指向**不存在的**包 | fake adb 的 `CRA_FAKE_PM_PATH=0` 分支会返回失败，脚本走既有失败路径——但未新增专门场景。行为与"包未装上"一致，无需新逻辑。 |
| U5 | 未验证 `watch_install_confirm.ps1` 或其他脚本是否也有同类问题 | 任务明确限定修改范围仅本脚本；不扩大到其他安装工具。 |
| U6 | 未覆盖 `-TimeoutSeconds` 极小值（如 0/负数）的边界 | 与本片缺陷无关。 |
| U7 | B5/B9/B10 未断言**具体**错误文案逐字内容（只断言关键子串） | 文案可读性调整不应导致测试失败；关键子串（`RESULT: FAILED`、`-TestApk`、`-PackageName`）已锁。 |
| U8 | 未做跨 PowerShell 7（pwsh）验证 | 契约测试套件本身 `#Requires -Version 5.1` 且用 `powershell.exe` 拉起子进程，与既有套件口径一致。 |
| **U9**（F2 新增） | 未覆盖 `-PackageName` 为**非空白但非法**的字符串（如 `'com. example'`、含控制字符） | 脚本不做包名合法性校验（那是 `pm path` 的职责，失败会走既有失败路径）。本轮只堵"值不可用"，不扩大到格式校验。 |
| **U10**（F2 新增） | 未验证 B10 的 `-Command` 投递在**非中文** Windows 区域设置下的行为 | 投递机制只用单引号字面量与 ASCII 路径拼接，理论上区域无关；未在多语言宿主实测。 |

---

## 9. P0 / P1 / P2 / P3 数量

**F1 + F2 累计（截至本报告，等待 Codex 复核）：**

| 级别 | 数量 | 说明 |
|---|---|---|
| **P0** | **0** | — |
| **P1** | **0** | S19 调查登记的"androidTest APK 验包虚假成功"P1 由 **F1** 修复（缺参时在任何 adb 之前快速失败）。**待 Codex 复核确认关闭。** |
| **P2** | **0** | Codex 复核发现的"显式空白包名绕过守卫"Spec P2 由 **F2** 修复（`IsNullOrWhiteSpace`）。**待 Codex 复核确认关闭。** |
| **P3** | **0** | F1 报告中把 U3（空串）判为 P3 是**错误分级**，已在 §8 更正并撤销。除 U4-U10 列出的**未覆盖项**（非缺陷）外，无遗留 P3。 |

**本片（F1 + F2）未引入任何新缺陷。**

> 分级更正记录：F1 报告曾写「P3 | 1 | U3」，理由是"后续 `pm path ''` 会失败，不构成虚假成功"。该理由**不成立**——Codex 在无设备探针上复现出脚本带着空白包名执行了 `adb devices`，即"绕过校验并触碰设备"，属 Spec P2。本报告撤销原 P3 结论。

---

## 10. `git diff --check` 结果

**F2 本轮（3 个文件）：**

```
git diff --check -- android/scripts/install_with_confirm.ps1 \
                    android/scripts/install_with_confirm.contract.tests.ps1 \
                    docs/coordination/agent-results/agent-result-s19-f1-zcode.md
```
- **输出为空，退出码 0** ⇒ 无空白错误、无行尾冲突标记。

**F1 阶段（含 COMMON.md，供对照）：**

```
git diff --check -- android/scripts/install_with_confirm.ps1 \
                    android/scripts/install_with_confirm.contract.tests.ps1 \
                    docs/plans/android-parallel-delivery-2026-09-09/COMMON.md
```
- **输出为空，退出码 0**

补充核验：

| 项 | 结果 |
|---|---|
| 两个 `.ps1` 的 UTF-8 BOM | F1/F2 改动后仍为 `ef bb bf`（A19 断言同时守护这一点；BOM 丢失会让 PS 5.1 静默丢弃 `-TestApk`/`-NoDirectInstall` 等 switch 参数） |
| `COMMON.md` 的 BOM | 原本就无 BOM（首字节 `23 20 e6` = `# ` 起头），F1 改动后保持一致 |
| `COMMON.md` 本轮是否被触碰 | **否**。mtime `2026-09-22 02:42`，早于 F2 对脚本的改动（`02:59` / `03:01`）；diff 内容仍是 F1 的 9 行插入 |
| staged 变更 | `git diff --cached --name-only` = **0** |

---

## 11. 明确声明

- **未运行真实 ADB。** 所有 adb 调用都命中 `%TEMP%\cra-contract-<guid>\bin\adb.cmd` 或 `%TEMP%\s19f2-probe\bin\adb.cmd`（测试/探针进程内临时前置到 `PATH` 的 shim）。
- **未连接、未操作任何真机。** 探针里的 `adb devices` 也命中假 adb，未对真机执行任何命令。
- **未使用 MuMu 模拟器。**
- **未运行 Gradle。**
- **未 commit、未 push、未 stage。** `git diff --cached` 为空。
- **未 reset / checkout / clean / stash。** 未覆盖、删除或回滚任何他人改动。
- **未运行全仓格式化或机械重写。**
- **未读取、未修改、未删除** `android/scripts/ui_now.xml`（S19 记录其含具体测试书名，本轮全程未打开）。
- **未修改** `.gitignore`、`diag_usb_confirm.py`、`tap_usb_install_confirm.py`、其他安装脚本、其他源码/测试/文档。
- **未扩大到**隐私临时文件、`.gitignore` 或其他安装工具的清理工作。
- 临时工件（变异体副本、3 个 scratch 日志）均建在 Windows TEMP 并**已删除**；契约测试自身的 `cra-contract-*` 工作目录由套件在每场景结束时自行清理。

---

## 12. 给 Codex 的复核清单

1. 确认守卫位置（`install_with_confirm.ps1:41`）确实早于裸 adb 调用（`:76`）。
2. 确认 `$packageNameExplicit` 用 `$PSBoundParameters.ContainsKey('PackageName')`，且未比较 `$PackageName` 值。
3. 确认 `$testPackageNameMissing` 同时含 `-not $packageNameExplicit` 与 `[string]::IsNullOrWhiteSpace($PackageName)`。
4. 确认 `$PackageName` 默认值仍为 `'com.creationreadingassistant'`（未改动）。
5. 复核 B5.8 / B9.8 / B10.8 的"日志文件未生成"证据是否被接受为"不触碰设备"的充分证明。
6. 复核 §8 对 U3 的**分级更正**（原 P3 → Spec P2，已由 F2 修复）是否与你的判断一致。
7. 复核 §0 关于 PS 5.1 `-File` 无法投递空字符串的说明，以及 B10 改用 `-Command` 投递是否被接受为**真实覆盖**（而非伪覆盖）。
8. **独立复跑**契约测试（本机命令见 §5.7），确认 **84/84** 与退出码 0 可复现。
9. 裁决 U2：真实 androidTest 包名应由谁提供、是否需要在构建配置侧固定下来。

---

**F1 + F2 实施完成，等待 Codex 独立复核。未领取下一片。**
