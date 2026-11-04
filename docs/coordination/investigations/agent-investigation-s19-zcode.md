# S19 Android 安装与诊断脚本资产盘点 — Zcode 报告

> 执行 Agent：Zcode　|　实际模型：`deepseek v4.1Flash`
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`
> 固定比较基点：`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`
> 调查期间 `git rev-parse --short HEAD` = `5aed593`（基点未推进）
> 性质：**只读盘点，未运行 Gradle / ADB / 设备操作，未修改任何脚本或产品源码**
> 报告路径：`docs/coordination/investigations/agent-investigation-s19-zcode.md`
> 状态：**等待 Codex 核验与裁决**

事实分级：**[已确认]** = 由源码、Git 状态或文档直接证明；
**[推断]** = 由调用关系/命名推导，未执行；
**[无法验证]** = 本轮禁止设备与脚本执行，无法确认。

---

## 1. 执行摘要

**S19 范围内没有 P0，也没有当前可达的 P1。** 正式安装入口
`android/scripts/install_with_confirm.ps1` 是全仓质量最高、防护最完整的一份设备脚本，
它有一个专门的 37 条断言契约测试保护，且这些保护**已被实测验证过**。

三个必须知道的事实：

1. **[已确认] `android/scripts/` 相对基点零改动。**
   `git diff --name-status 5aed593 -- android/scripts/` 为空。11 个 tracked 脚本与 `origin/main`
   完全一致；`android/scripts/` 下没有任何 modified 文件。本次盘点面对的是稳定资产，
   不是进行中的 WIP。

2. **[已确认] 未跟踪的只有 3 个文件，且全部在 `android/scripts/` 下**：
   `diag_usb_confirm.py`、`tap_usb_install_confirm.py`、`ui_now.xml`。
   三者都**不被 `.gitignore` 覆盖**（`git check-ignore` 全部返回未忽略）。

3. **[已确认] 最严重的问题不是安装脚本，而是 `ui_now.xml` 泄漏了一个真实书名。**
   该文件是 `dump_ui.py` 的输出残留，包含 `text="不平静的日常_作者：惰天使"`。
   这是未跟踪文件，但**未被 ignore、随时可能被 `git add .` 带入仓库**。
   它同时违反 AGENTS.md 的「测试报告只写中性名称」纪律。

**正式安装入口结论：仍是 `android/scripts/install_with_confirm.ps1`。**
它的参数接口、退出码语义、回退标记、设备绑定与 UTF-8 处理都优于其他任何候选，
没有任何证据支持替换它。

**计数：P0 × 0；P1 × 0；P2 × 5；P3 × 7；Readiness blocker × 4。**

---

## 2. 调查边界及未执行事项

### 2.1 已执行的只读操作

- `git rev-parse`、`git status --short --untracked-files=all`、`git ls-files`、
  `git diff --name-status 5aed593 -- <限定路径>`、`git diff -- <file>`、`git check-ignore -v`
- `rg` / `grep` / `find` 静态检索
- 读取脚本、`.gitignore`、`.gitattributes`、`.github/workflows/ci.yml`、`release.yml` 与相关文档

### 2.2 明确未执行（按任务约束）

| 未执行项 | 原因 |
|---|---|
| 运行 `install_with_confirm.ps1` | 会安装 APK、操作设备 |
| 运行 `install_with_confirm.contract.tests.ps1` | 任务禁止执行安装脚本；该测试会调用真实脚本（用假 adb，但仍会启动 PowerShell 子进程执行安装路径） |
| 任何 `adb` 命令 | 任务禁止 |
| 连接/操作真实手机 | 任务禁止 |
| 运行 Gradle | 任务禁止（且窗口 C/D 可能占用构建锁） |
| 运行 `dump_ui.py` / `diag_usb_confirm.py` / `tap_usb_install_confirm.py` | 都会操作设备 |
| 修改任何脚本或产品文件 | 任务禁止（唯一新增为本报告） |

因此，**所有「脚本在真机上的实际行为」结论均来自文档中的历史记录，本轮未独立复现**。

### 2.3 边界声明

- 未触碰 `D:\develop\Code\Codex\creation-reading-assistant`（原始仓库）。
- 未触碰 D-2-B / S08 / S09 的文件或报告。
- 未触碰协调看板、handoff、Obsidian、TODO。
- 未使用 MuMu 模拟器。

---

## 3. 相关资产完整清单

### 3.1 `android/scripts/` 全量（本任务核心目录）

| # | 路径（仓库相对） | Git 状态 | 角色 | 调用方 / 文档引用 | 建议 |
|---|---|---|---|---|---|
| 1 | `android/scripts/install_with_confirm.ps1` | **tracked**（= 基点） | **正式入口** | 19 处文档引用；`R3-WORKBUDDY.md:25`、`COMMON.md:43`、冻结清单 §5 | **保留为正式入口** |
| 2 | `android/scripts/install_with_confirm.contract.tests.ps1` | **tracked**（= 基点） | **正式契约测试** | 冻结清单 `:144`、`:153`；4 处文档引用 | **保留**（建议纳入 CI 或至少纳入验收前置） |
| 3 | `android/scripts/watch_install_confirm.ps1` | **tracked**（= 基点） | **临时/过期** | **零文档引用** | **明确标为诊断或删除**（见 P2-1） |
| 4 | `android/scripts/dump_ui.py` | **tracked**（= 基点） | **诊断** | 无文档引用 | 保留但标为诊断；**修机器绑定路径**（P2-2） |
| 5 | `android/scripts/diag_usb_confirm.py` | **untracked** | **诊断（一次性）** | `2026-09-19-...inventory.md:437` | 加 `.gitignore` 或补文档后决定（见 §8） |
| 6 | `android/scripts/tap_usb_install_confirm.py` | **untracked** | **诊断（有长期价值）** | `inventory.md:437`；`2026-09-15-...notes.md:178` | 建议正式化（补文档 + 去设备绑定） |
| 7 | `android/scripts/ui_now.xml` | **untracked** | **临时工件（应删）** | 无 | **删除；并补 `.gitignore`**（P1 相关，见 §7/§8） |
| 8 | `android/scripts/sort_retest.py` | **tracked**（= 基点） | **一次性性能验证** | 无文档引用 | 保留但标为诊断；修机器绑定路径（P2-2） |
| 9 | `android/scripts/sort_retest_framestats.py` | **tracked**（= 基点） | **一次性性能验证** | 无文档引用 | 同上 |
| 10 | `android/scripts/build_display_font.py` | **tracked**（= 基点） | **正式工具（字体子集）** | 自述用法 `py -3 android/scripts/build_display_font.py` | 保留；**修机器绑定路径**（P2-2） |
| 11 | `android/scripts/run-migration-test.sh` | **tracked**（= 基点） | **正式（CI 依赖）** | `.github/workflows/ci.yml:140` | **保留，绝不可删** |
| 12 | `android/scripts/dump_appid.init.gradle.kts` | **tracked**（= 基点） | **临时诊断** | 无文档引用 | 保留但标为诊断 |
| 13 | `android/scripts/retest_benchmark_variant.init.gradle` | **tracked**（= 基点） | **临时诊断** | 无文档引用 | 保留但标为诊断 |
| 14 | `android/scripts/generate_large_txt_fixture.ps1` | **tracked**（= 基点） | **正式 fixture 生成器** | `docs/handoff/current.md` §15 提及 | 保留（无设备依赖，可复用） |

### 3.2 `scripts/`（仓库根，Desktop 为主）

| # | 路径 | Git 状态 | 与本任务关系 |
|---|---|---|---|
| 1 | `scripts/packaged-stage3-outline-focus-acceptance.mjs` | **modified**（vs 基点） | **Desktop**，非 Android 安装资产 → 属 S20 |
| 2 | `scripts/verify-reader-settings.mjs` | **modified**（vs 基点） | **Desktop**（断言 `src/**`、`electron/**`）→ 属 S20 |
| 3 | `scripts/visual-evidence/smoke-final.png`、`smoke-log.json` | **modified** | Desktop 视觉证据 → 属 S20 |
| 4 | `scripts/beta-check.mjs` | tracked | **不含**任何 Android 安装/adb 引用（已 grep 确认） |

**结论 [已确认]：仓库根 `scripts/` 下没有任何 Android 安装或 ADB 资产。**
S19 的仓库根扫描为空；两个 modified 文件属 Desktop，归 S20。

### 3.3 `docs/qa/evidence/` 下的设备辅助脚本

| 路径 | Git 状态 | 角色 |
|---|---|---|
| `docs/qa/evidence/2026-09-21-a01-qoder-device-acceptance/chrome_tap.sh` | **untracked** | 一次性设备手势脚本（硬编码 serial + SDK 路径） |
| `docs/qa/evidence/2026-09-21-a01-qoder-device-acceptance/query_fixtures.py` | **untracked** | fixture 生成（无设备绑定） |
| `docs/qa/evidence/2026-09-21-s07-qoder-device/make_fixtures.py` | **untracked** | fixture 生成（无设备绑定） |
| `docs/qa/evidence/.tmp/tap_ui.xml`、`last_xy`、`needle.txt` | **untracked（但已被 ignore）** | 瞬时工件；`.gitignore:23 *.tmp` 命中 `.tmp/` 目录 |

`docs/qa/evidence/` 共 **0 个 tracked 文件、489 个 untracked 文件**——整个目录都是本轮并行 Agent 的验收产物。

### 3.4 仓库根其他设备相关未跟踪工件

| 路径 | Git 状态 | ignore | 说明 |
|---|---|---|---|
| `.scratch-shots/10-inspiration.png`（4.4 MB） | untracked | **未忽略** | 3 张设备截图，共约 12.8 MB；`inventory.md:446` 归 S20 |

---

## 4. 正式安装路径调用关系

```
文档授权层
  docs/plans/android-parallel-delivery-2026-09-09/COMMON.md:43
  docs/plans/android-parallel-delivery-2026-09-09/R3-WORKBUDDY.md:25
  docs/plans/historical-integration/2026-09-14-android-integration-freeze-manifest.md:143-156
        │  规定：先调 install_with_confirm.ps1 -Serial <serial> -Apk <absolute-apk>
        ▼
正式入口  android/scripts/install_with_confirm.ps1
        │
        ├─[1] adb devices 盘点 + 校验 $Serial 在列            (:139-148)
        ├─[2] adb get-state 可用性探测                        (:155-159)
        ├─[3] adb push  APK → /sdcard/Download/<GUID>.apk     (:161)
        ├─[4] am start 显式拉起 MIUI 安装器                    (:166-171)
        ├─[5] 确认循环（≤ TimeoutSeconds=90）
        │      uiautomator dump → pull → UTF-8 解码           (:181-195)
        │      解析 XML → 找 start_button → pm path 校验       (:207-226)
        │      成功 → RESULT: CONFIRM_LOOP + exit 0           (:222-224)
        ├─[6] 超时/异常 → Invoke-DirectInstall                (:110-130, :250, :263)
        │      成功 → RESULT: FALLBACK_DIRECT_INSTALL + exit 2
        └─[7] finally: rm 远端 APK/XML + 删本地临时文件        (:272-275)

契约测试  android/scripts/install_with_confirm.contract.tests.ps1
        │  37 断言：A0–A19 静态 + B1–B4 假 adb 行为
        └─ 用 %TEMP% 假 adb（成功摘要写 STDERR）跑真实脚本，断言退出码与 RESULT 文案

CI 参考  .github/workflows/ci.yml:140  →  android/scripts/run-migration-test.sh（模拟器，非真机）
```

**[已确认] 没有任何脚本绕过这个入口去自动安装。** 全仓 `rg` 未发现第二个「自动点 MIUI 确认」的
tracked 脚本。未跟踪的 `watch_install_confirm.ps1`（tracked）与
`tap_usb_install_confirm.py`（untracked）是**并发的替代手段**，见 §6。

---

## 5. `install_with_confirm.ps1` 静态行为审查

逐项对照任务第 7 条要求。

| 检查项 | 结论 | 证据 |
|---|---|---|
| **APK 路径 / 带空格路径** | **正确处理** | `:19` `Resolve-Path -LiteralPath $Apk`；`:161` `push` 传 `$apkPath`；`:119` 直接入 argv 数组，不经字符串拼接 |
| **主 APK 与 androidTest APK** | **参数支持，但校验有缺口** | `:6` `-TestApk` → `:118` 加 `-t`；`:4` `-PackageName`。**缺口见 P2-3** |
| **多设备 / 离线设备** | **处理正确** | `:139-148` 解析 `adb devices` 并校验 serial 在列；`:146` 仅在解析成功且 serial 缺失时抛错；`:155-159` `get-state` 二次确认 `device` |
| **超时** | **有** | `:11` `TimeoutSeconds=90`；`:173/:178` deadline 循环 |
| **退出码** | **三态清晰** | `0` 确认循环成功（`:224`）；`2` 回退（`:125`）；`1` 失败（`:251`/`:266`） |
| **失败证据** | **充分** | `:241-249` 打印 parse error 计数 + 首条样例 + 分类原因；`:242` `XML_PARSE_ERROR_SAMPLE` |
| **PowerShell 编码** | **已修且被测试锁住** | `:193` `Get-Content -LiteralPath $localUi -Raw -Encoding UTF8`；文件带 UTF-8 BOM（已用 `xxd` 确认 `efbbbf`）；契约测试 A19 断言「含非 ASCII 必须带 BOM」 |
| **错误传播** | **已修且被测试锁住** | `:25-48` `Invoke-AdbNative` 在 `Continue` 下执行原生命令，只用 `$LASTEXITCODE` 判定；`:256` `Write-Error $_ -ErrorAction Continue`；`:267-276` finally 清理不掩盖退出码 |
| **临时目录** | **正确** | `:20/:21/:23` 远端与本地临时文件均 GUID 命名；`:273-275` 只删自己创建的 `$localUi` |
| **日志** | **走 stdout** | 无独立日志文件；由调用方重定向（文档中的 `rrv-install.log` 等由调用者产生） |
| **截图** | **不涉及** | 脚本不产生截图 |
| **stay-awake / 旋转 / 字号 / 通知权限** | **脚本完全不处理** | 全脚本无 `settings put`、无 `svc power`、无 `pm grant`。**这是设计选择**（见 §6.3） |

### 5.1 值得记录的正面设计

- **`:70-104` `Invoke-InstallerNode` 只点 MIUI 安装器自己拥有的精确控件**：
  候选是 `android:id/button2`、`android:id/button3`、
  `com.miui.packageinstaller:id/checkbox`、`com.miui.packageinstaller:id/second_button`，
  且每个候选都要求 `package -eq 'com.miui.packageinstaller'`。
  **它不按中文文本匹配**，避开了 CP936 乱码误判。
- **`:90-95` 风险确认前不放行最终动作**：点 `second_button` 前要求 `checkbox.checked -eq 'true'`。
- **`:146` 设备校验是「宽松但有效」的**：解析失败不阻塞，解析成功且 serial 缺失才阻塞。
  这避免了因 `adb devices` 输出格式微调而卡死，同时保留了真实误连保护。

---

## 6. 裸 ADB、人工确认、MIUI 安全设置、多设备风险审查

### 6.1 裸 adb 调用点全表

| 脚本 | 行号 | 是否绑定 `-s` | 风险 |
|---|---|---|---|
| `install_with_confirm.ps1` | `:42` `& adb -s $Serial @args` | **是**（唯一调用点，统一走 `Invoke-AdbNative`） | 无 |
| `watch_install_confirm.ps1` | `:13`、`:14`、`:22` | **是**（三条都带 `-s $Serial`） | 无 |
| `dump_ui.py` | `:7`、`:9` | **是**（`-s serial`） | 无 |
| `sort_retest.py` | 全部经 `["adb","-s",SERIAL,...]` | **是** | 无 |
| `sort_retest_framestats.py` | `:20` `["adb","-s",SERIAL]+args` | **是** | 无 |
| `diag_usb_confirm.py` | `:29`、`:78` | **是**（`-s serial`） | 无 |
| `tap_usb_install_confirm.py` | `:53`、`:106` | **是** | 无 |
| `chrome_tap.sh` | 全部 `adb -s $SER` | **是** | 无 |
| `run-migration-test.sh` | 无 adb（走 Gradle connectedAndroidTest） | N/A | 无 |

**[已确认] 仓库内不存在未绑定 `-s <serial>` 的裸 adb 调用。** 这是一条真实的、值得保留的纪律。

### 6.2 人工点击 MIUI 确认

**[已确认] 所有脚本的目标都是「不要求人工点击」**：

- `install_with_confirm.ps1` 自动点 `:100` `input tap`；
- `watch_install_confirm.ps1` 自动点 `:22`；
- `tap_usb_install_confirm.py` 自动点 `:128`；
- `diag_usb_confirm.py` 自动点 `:107`。

**[已确认] 全仓脚本无 `Read-Host` / `pause`**（契约测试 A2/A3 显式断言）。

### 6.3 MIUI 安全设置

**[已确认] 无任何脚本修改设备安全设置**：全仓 `android/scripts/` 无
`settings put`、`pm grant`、`pm revoke`、`su`（契约测试 A4/A5/A6 断言）。

**关于 stay-awake / 旋转 / 字号 / 通知权限 [已确认]**：
**安装脚本完全不碰这些状态，也没有捕获-恢复逻辑。**
这不是缺陷——脚本只负责安装，状态管理是验收任务的事。
但任务第 7 条要求盘点「状态捕获与恢复」在哪里实现，答案是：

- **没有任何脚本实现它**；
- 该纪律只存在于**文档**（`COMMON.md:46`、`AGENTS.md`「Android 真机验证约束」），
  由执行 Agent 手工遵守；
- 历史报告中确有恢复记录（如 `docs/qa/2026-09/2026-09-21-s07-f5-qoder-device-retest.md`），
  但那是人工操作，不是脚本能力。

**[推断] 这是当前最大的「纪律靠人」缺口**，但**不构成 P1**——因为它不影响安装正确性，
且文档已明确要求。归为 P3（§10）。

### 6.4 多设备误操作

| 场景 | `install_with_confirm.ps1` 行为 | 评估 |
|---|---|---|
| 只有一台设备，serial 不匹配 | `:146` 抛错 | **安全** |
| 多台设备，serial 在列 | 正常执行，全部 `-s` 绑定 | **安全** |
| 多台设备，serial 不在列 | `:146` 抛错 | **安全** |
| `adb devices` 输出无法解析 | 跳过校验，落到 `:155` `get-state` | **可接受**（`get-state` 无 `-s` 时在多设备下会失败，`:157` 随即抛错） |
| 默认 serial 恰好是别人的设备 | **默认值 `c49ac6cf` 是硬编码**（见 P2-4） | **需注意** |

---

## 7. 机器绑定、隐私与敏感信息扫描

### 7.1 绝对路径（机器绑定）

| 文件 | 行号 | 内容 | 影响 |
|---|---|---|---|
| `android/scripts/build_display_font.py` | `:27` | `ROOT = r"d:\develop\Code\Codex\creation-reading-assistant"` | 换仓库/换机器即失效 |
| `android/scripts/build_display_font.py` | `:28` | `SRC_FONT = r"C:\Windows\Fonts\NotoSerifSC-VF.ttf"` | Windows 专属；非 Windows 或未装该字体即失效 |
| `android/scripts/dump_ui.py` | `:10`、`:12` | `r"d:\develop\Code\Codex\creation-reading-assistant\android\scripts\ui_now.xml"` | **指向原始仓库**（非恢复仓库）；且写死输出位置 |
| `android/scripts/sort_retest.py` | `:78` | `fr"d:\develop\...\android\scripts\sort_retest_{i}.perfetto-trace"` | 换仓库即失效 |
| `docs/qa/evidence/.../chrome_tap.sh` | `:5` | `export PATH="$PATH:/d/develop/Android/Sdk/platform-tools"` | 机器专属 SDK 路径 |

**[已确认] 4 个 tracked 脚本含硬编码绝对路径**（3 个指向 `d:\develop\Code\Codex\creation-reading-assistant`，
即**原始仓库而非恢复仓库**）。

### 7.2 硬编码设备 serial

| 文件 | 行号 | 值 |
|---|---|---|
| `install_with_confirm.ps1` | `:3` | `[string]$Serial = 'c49ac6cf'`（**参数默认值，可覆盖**） |
| `watch_install_confirm.ps1` | `:3` | `[string]$Serial = 'c49ac6cf'`（**参数默认值，可覆盖**） |
| `dump_ui.py` | `:6` | `serial = "c49ac6cf"`（**常量，不可覆盖**） |
| `sort_retest.py` | `:15` | `SERIAL = "c49ac6cf"`（**常量，不可覆盖**） |
| `sort_retest_framestats.py` | `:7` | `SERIAL = "c49ac6cf"`（**常量，不可覆盖**） |
| `chrome_tap.sh` | `:6` | `SER=c49ac6cf`（**常量，不可覆盖**） |

**[已确认]**：正式入口 `install_with_confirm.ps1` 的 serial 是**可覆盖参数**（正确设计）；
其余 4 个诊断脚本是**不可覆盖常量**（诊断脚本可接受，但不应被当作正式入口）。

### 7.3 敏感信息（密码 / 令牌 / 端口）

**[已确认] 未发现任何密码、令牌、API Key、私钥、端口或 localhost 配置。**
对 `android/scripts/*.{py,ps1,sh,kts,gradle}` 的定向扫描结果为空。
`ui_now.xml` 中大量 `password="false"` 是 uiautomator 的**属性名**，不是凭据。

### 7.4 隐私：真实书名泄漏（**本报告最重要的单项发现**）

**[已确认]** `android/scripts/ui_now.xml`（18,287 字节，untracked）含真实书名文本：

```
text="不平静的日常_作者：惰天使"
```

- 该文件是 `dump_ui.py:10/:12` 的**输出目标**（脚本把设备 UI dump 拉回仓库目录）。
- **未被 `.gitignore` 覆盖**：`.gitignore:41` 的规则是 `/android/ui*.xml`，
  锚定在**仓库根下的 `android/`**，而文件位于 `android/scripts/`，因此不命中
  （已用 `git check-ignore -v` 确认返回「未忽略」）。
- 同样的书名还出现在 `docs/plans/android-parallel-delivery-2026-09-09/reports/workbuddy-r3-final-integration.md`
  （tracked 文档）。
- **违反 AGENTS.md**：「测试报告、TODO、截图说明和交接记录只写"测试 EPUB""测试 TXT"等中性名称，
  不记录真实测试书名」。

**[推断] 当前风险等级**：文件未跟踪 → 不在 HEAD → 不会被 clone 带走。
但只要有人执行 `git add .` / `git add android/scripts/`，它就会进入历史，
**且一旦进入历史就难以彻底清除**。这是「高后果 + 低概率但真实」的组合，
按任务口径定为 **P2**（不是当前可达的故障，但必须在任何提交动作前处理）。

---

## 8. 临时工件、重复脚本与过期资产

### 8.1 瞬时工件（应清理）

| 路径 | 状态 | 说明 |
|---|---|---|
| `android/scripts/ui_now.xml` | untracked | **UI dump 残留 + 真实书名**；`dump_ui.py` 的输出目标 |
| `.scratch-shots/*.png`（3 张，12.8 MB） | untracked，未忽略 | 设备截图；`inventory.md:446` 归 S20 |
| `docs/qa/evidence/.tmp/`（3 文件） | untracked，**已忽略** | `.gitignore:23 *.tmp` 命中；无风险 |

### 8.2 功能重复的脚本（4 组「点 MIUI 确认」实现）

**[已确认] 存在 4 份独立实现，做的是同一件事**（自动点击 MIUI 安装确认）：

| 脚本 | 匹配方式 | 前台探测 | 编码处理 | 设备探测 | 多控件 | 状态 |
|---|---|---|---|---|---|---|
| `install_with_confirm.ps1` | **resource-id + package** | 无（全量 dump 轮询） | **pull + UTF-8** ✅ | ✅ devices+get-state | ✅ 4 候选 | **正式** |
| `watch_install_confirm.ps1` | **中文文本 `继续安装`** | 无 | **`exec-out cat`** ❌ | ❌ | ❌ 单点 | tracked，零文档引用 |
| `tap_usb_install_confirm.py` | **resource-id** | ✅ `dumpsys window` | **pull + UTF-8** ✅ | ❌ | ❌ 单点 | untracked，有文档 |
| `diag_usb_confirm.py` | **resource-id** | ✅ `dumpsys window` | **pull + UTF-8** ✅ | ❌ | ❌ 单点 | untracked，无文档 |

**关键差异 [已确认]**：

- `watch_install_confirm.ps1` 是**唯一按中文文本匹配**的实现（`:18` `$txt -eq '继续安装'`），
  而它同时是**唯一使用 `exec-out cat`** 的实现（`:14`）。
  这两点正是 `install_with_confirm.ps1` 在 `:183-190` 与契约测试 A7 中**明确判定为缺陷并修掉**的做法。
  也就是说，`watch_install_confirm.ps1` 保留了**已被证伪的技术路线**。
- `tap_usb_install_confirm.py` 与 `diag_usb_confirm.py` 使用了 `install_with_confirm.ps1`
  **没有**的能力：`dumpsys window` 快速前台探测。这对「8 秒倒计时」场景是真实优势
  （`2026-09-15-...notes.md:179` 记录了这个理由）。

### 8.3 过期资产

| 脚本 | 过期证据 |
|---|---|
| `watch_install_confirm.ps1` | `:5` 自述「复测期间临时使用」；零文档引用；技术路线已被正式脚本证伪 |
| `dump_appid.init.gradle.kts` | `:1` 自述「临时诊断脚本：输出 :app 各变体 applicationId」；零文档引用 |
| `retest_benchmark_variant.init.gradle` | `:1` 自述「临时复测用 init 脚本」；零文档引用 |
| `run-migration-test.sh` | **`:4` 注释已过期**：「当前库版本 v9」，而 `APP_DATABASE_SCHEMA_VERSION = 15` |

---

## 9. 文档与实际脚本的一致性检查

| # | 文档说法 | 脚本实际 | 一致性 |
|---|---|---|---|
| 1 | `COMMON.md:43` 先调 `install_with_confirm.ps1 -Serial <serial> -Apk <absolute-apk>` | 参数名、语义一致 | ✅ |
| 2 | `COMMON.md:44` 允许 `adb -s <serial> install -r` 回退，**不代表脚本 PASS** | `:124` `RESULT: FALLBACK_DIRECT_INSTALL` + `:125` `exit 2` + 文案「NOT a confirm-loop PASS」 | ✅ **完全一致** |
| 3 | `R3-WORKBUDDY.md:28`「不要求用户手动点 MIUI，不改安全设置，不卸载、不清应用数据」 | 无 `Read-Host`/`settings put`/`pm grant`/`uninstall`/`pm clear` | ✅ |
| 4 | `COMMON.md:43`「安装主 APK **或 androidTest APK** 先调用…」 | 脚本支持 `-TestApk` + `-PackageName`，但**默认 `PackageName` 是主包** | ⚠️ **见 P2-3** |
| 5 | `docs/handoff/current.md:475`、`:958` 写 `scripts/install_with_confirm.ps1` | 真实路径是 `android/scripts/install_with_confirm.ps1` | ⚠️ **路径错误（P3）** |
| 6 | `docs/qa/2026-09-21-s07-qoder-device-acceptance.md:112` 写 `-File "scripts/install_with_confirm.ps1"` | 同上 | ⚠️ **路径错误（P3）** |
| 7 | `run-migration-test.sh:4`「当前库版本 v9」 | 实际 v15 | ⚠️ **过期（P3）** |
| 8 | `2026-09-15-...notes.md:178` 称 `tap_usb_install_confirm.py` 是 Gradle 并发解法 | 脚本能力与描述一致（`dumpsys window` + resource-id + 持续模式） | ✅ |
| 9 | `inventory.md:437-439` 称 3 个未跟踪文件是「设备诊断/临时自动化资产」，正式入口仍是 `install_with_confirm.ps1` | 与本次独立盘点结论**一致** | ✅ |
| 10 | `AGENTS.md` 要求测试资料用中性名称 | `ui_now.xml` 含真实书名 | ❌ **违反（见 §7.4）** |

---

## 10. 双轴问题表

### 10.1 Standards / 工程与安全轴

#### P2-1：`watch_install_confirm.ps1` 保留已证伪的技术路线，且零文档引用

- **严重度**：P2
- **当前是否可达**：**否**（tracked 但零文档引用、零生产调用；只有人工直接运行才会生效）
- **文件与行号**：`android/scripts/watch_install_confirm.ps1:14`（`exec-out cat`）、`:18`（中文文本匹配）
- **证据**：
  - `:14` `$xml = (adb -s $Serial exec-out cat /sdcard/ui_install.xml) -join "`n"`
  - `:18` `if ($txt -eq '继续安装')`
  - `install_with_confirm.ps1:183-190` 的注释明确记录 `exec-out cat` + CP936 会导致
    「every node came back mojibake and some byte sequences even swallowed the closing quote」；
  - 契约测试 `:56` A7 断言被测脚本**不得**出现 `exec-out`；
  - `:180` `2026-09-15-...notes.md:180` 明确记录「**不按中文文本**（规避 CP936 编码损坏）」。
- **影响**：该脚本在同一仓库里保留了一条**已被正式脚本判定为不可靠**的路径。
  若被后来者当作参考实现，会重新引入 CP936 乱码导致的「安装器 UI 永远不出现」故障。
  同时它是唯一按中文文本匹配的实现，在非中文 MIUI 或文案变更时直接失效。
- **建议**：三选一（**由 Codex 裁决，本轮不实施**）：
  ① 删除；② 保留但在文件头标注「已废弃，请用 `install_with_confirm.ps1`」；
  ③ 重写为与正式脚本同构（pull + UTF-8 + resource-id）。
- **是否需要后续代码修改**：**是**（删除或标注）。

#### P2-2：4 个 tracked 脚本硬编码绝对路径，其中 3 个指向**原始仓库**

- **严重度**：P2
- **当前是否可达**：**是**（`build_display_font.py` 有明确用法说明；其余为诊断脚本，人工可运行）
- **文件与行号**：
  - `android/scripts/build_display_font.py:27` `ROOT = r"d:\develop\Code\Codex\creation-reading-assistant"`
  - `android/scripts/build_display_font.py:28` `SRC_FONT = r"C:\Windows\Fonts\NotoSerifSC-VF.ttf"`
  - `android/scripts/dump_ui.py:10`、`:12` 同上仓库路径
  - `android/scripts/sort_retest.py:78` 同上仓库路径
- **证据**：`grep -n -i -E 'd:\\develop|C:\\Windows'` 命中上述 4 行。
- **影响**：
  - `build_display_font.py` 是**正式字体子集生成工具**（产物进 `res/font/`）。
    在恢复仓库中运行会把输出写到**原始仓库**，或直接因路径不存在而失败。
  - `dump_ui.py` 会把设备 UI dump 写到**原始仓库**的 `android/scripts/ui_now.xml`，
    这正是「恢复仓库里出现原始仓库残留」的成因之一（本次 `ui_now.xml` 已在恢复仓库，
    说明曾在恢复仓库运行过或曾被复制）。
  - `C:\Windows\Fonts` 使脚本完全不可移植（非 Windows / 未装 NotoSerifSC 即失效）。
- **建议**：把 `ROOT` 改为基于 `__file__` 推导的仓库根；把字体路径改为可配置参数或环境变量。
  **由 Codex 裁决，本轮不实施。**
- **是否需要后续代码修改**：**是**。

#### P2-3：`-TestApk` 安装 androidTest APK 时，包名校验仍默认主包名

- **严重度**：P2
- **当前是否可达**：**是**（文档明确要求用本脚本装 androidTest APK）
- **文件与行号**：`android/scripts/install_with_confirm.ps1:4`（默认值）、`:122`、`:215`
- **证据**：
  - `:4` `[string]$PackageName = 'com.creationreadingassistant'`
  - `:122` `$verify = ((Invoke-AdbNative shell pm path $PackageName).Output -join "`n")`（回退路径校验）
  - `:215` `$pmProbe = Invoke-AdbNative shell pm path $PackageName`（确认循环校验）
  - 文档要求：`COMMON.md:43`「安装主 APK **或 androidTest APK** 先调用 `install_with_confirm.ps1`」
  - androidTest APK 的包名是 `com.creationreadingassistant.test`（`docs/handoff/current.md:301`、
    `docs/qa/evidence/2026-09-21-d02a-qoder-device-acceptance/02-app-identity-before.txt:4`）
  - 历史正确用法：`workbuddy-r3-final-acceptance-2026-09-14.md:78` 第 2 行显式传
    `-PackageName com.creationreadingassistant.r3gate.test -TestApk`
- **影响**：**若调用者只传 `-TestApk` 而忘记 `-PackageName`**，脚本会用主包名去 `pm path` 校验。
  此时：
  - 若主 APK **已安装** → `pm path` 成功 → 脚本会误判「安装成功」，但实际装的是 test APK，
    **校验的是另一个包**；
  - 若主 APK **未安装** → `pm path` 失败 → 即使 test APK 装成功，也会落到超时/失败分支。
  两种情况都不报「参数不一致」错误。**这是一个静默的错误校验目标。**
- **说明**：`-TestApk` 只控制 `-t` 标志（`:118`），**不推导包名**。脚本没有
  「`-TestApk` 时把 PackageName 默认成 `.test` 后缀」的逻辑，也没有一致性校验。
- **建议**：当 `-TestApk` 且 `PackageName` 未显式提供时，要么自动推导 `.test` 后缀，
  要么直接报错要求显式传 `-PackageName`。**由 Codex 裁决，本轮不实施。**
- **是否需要后续代码修改**：**是**（建议加一致性门）。

#### P2-4：默认设备 serial `c49ac6cf` 硬编码在 6 个脚本中，其中 4 个不可覆盖

- **严重度**：P2
- **当前是否可达**：**是**
- **文件与行号**：见 §7.2 全表
- **证据**：`grep -n 'c49ac6cf' android/scripts/` 命中 6 个文件。
- **影响**：
  - **正式入口** `install_with_confirm.ps1:3` 与 `watch_install_confirm.ps1:3` 是参数默认值，
    可覆盖 → **可接受**（默认值提升便利性，且 `:139-148` 会校验该 serial 确实在线）。
  - **4 个诊断脚本**（`dump_ui.py:6`、`sort_retest.py:15`、`sort_retest_framestats.py:7`、
    `chrome_tap.sh:6`）是**不可覆盖常量** → 换设备即失效，且无法通过参数切换。
  - 该 serial 已出现在多份 tracked 文档中，属于既成事实，**不建议做全仓脱敏**（会制造无意义的大 diff）。
- **建议**：诊断脚本改为接收 `--serial` 参数（`tap_usb_install_confirm.py` 与
  `diag_usb_confirm.py` **已经**是 `required=True`，可作为模板）。
  正式脚本的默认值可保留。**由 Codex 裁决，本轮不实施。**
- **是否需要后续代码修改**：**是**（仅诊断脚本）。

#### P2-5：`ui_now.xml` 未跟踪但未被 ignore，且含真实书名

- **严重度**：P2（**隐私 + 纪律违反**；未跟踪故不在 HEAD，风险为「下一次 `git add`」）
- **当前是否可达**：**是**（文件就在工作区，任何 `git add .` 都会带上）
- **文件与行号**：`android/scripts/ui_now.xml`（整个文件；关键文本为 `text="不平静的日常_作者：惰天使"`）
- **证据**：
  - `git status --short` → `?? android/scripts/ui_now.xml`
  - `git check-ignore -v android/scripts/ui_now.xml` → **返回未忽略**
  - `.gitignore:41` 规则是 `/android/ui*.xml`（锚定仓库根 `android/`），不覆盖 `android/scripts/`
  - 该文件由 `dump_ui.py:10/:12` 生成
  - `AGENTS.md`：「测试报告、TODO、截图说明和交接记录只写"测试 EPUB""测试 TXT"等中性名称，
    不记录真实测试书名」
- **影响**：
  1. **隐私/纪律**：真实书名（含作者）会随提交进入仓库历史。
  2. **仓库卫生**：一个 18 KB 的设备 dump 残留放在脚本目录。
  3. **误判来源**：它是 `dump_ui.py` 硬编码指向**原始仓库**路径的产物，
     出现在恢复仓库说明路径漂移已经发生。
- **建议**：
  ① **删除该文件**（它是可随时重新生成的瞬时工件，删除不损失信息）；
  ② **补 `.gitignore`**：把规则从 `/android/ui*.xml` 扩展为覆盖 `android/scripts/ui*.xml`
     （或加 `/android/**/ui*.xml`）。
  **注：删除与 .gitignore 修改都由 Codex/集成者执行，本轮不实施。**
- **是否需要后续代码修改**：**是**（`.gitignore`）+ 文件删除。

#### P3-1：`run-migration-test.sh:4` 注释过期（v9 → v15）

- **严重度**：P3｜**当前可达**：是（CI 使用该脚本）｜**行号**：`android/scripts/run-migration-test.sh:4`
- **证据**：注释「当前库版本 v9」；`AppDatabase.kt:70` `APP_DATABASE_SCHEMA_VERSION = 15`。
- **影响**：仅误导读者；脚本逻辑本身正确（它调用 `AppDatabaseMigrationTest`，不硬编码版本）。
- **建议**：更新注释。**是否需要代码修改**：是（仅注释）。

#### P3-2：3 个 tracked 诊断脚本零文档引用

- **严重度**：P3｜**当前可达**：是（人工可直接运行）
- **文件**：`dump_appid.init.gradle.kts`、`retest_benchmark_variant.init.gradle`、
  `sort_retest.py` / `sort_retest_framestats.py`
- **证据**：`rg -l` 在 `docs/` 与 `.github/` 中零命中。
- **影响**：无法判断其是否仍有价值，也无法判断是否可清理。
- **建议**：在 `docs/` 增加一页「Android 诊断脚本清单」，为每个脚本写明用途、平台、依赖与状态。
  **是否需要代码修改**：否（仅文档）。

#### P3-3：设备状态纪律（stay-awake / 旋转 / 字号 / 通知）无脚本支撑

- **严重度**：P3｜**当前可达**：是
- **证据**：全仓 `android/scripts/` 无 `stay_on_while_plugged_in`、无 `settings put`。
  纪律仅存在于 `COMMON.md:46` 与 `AGENTS.md`。
- **影响**：依赖人工遵守；历史报告显示实际执行过（如 s07-f5 复验），但无自动化校验。
- **建议**：暂不改。若未来要自动化，应做成**独立的 capture/restore 辅助脚本**，
  不要塞进安装脚本（安装脚本当前「只装不改」的定位是优点）。
- **是否需要代码修改**：否（建议维持）。

#### P3-4：`.scratch-shots/` 未纳入 `.gitignore`

- **严重度**：P3｜**当前可达**：是
- **证据**：`git check-ignore -v .scratch-shots/10-inspiration.png` → 未忽略；
  `inventory.md:446` 已把它列为 S20 隔离项。
- **影响**：12.8 MB 设备截图可能被误提交。
- **建议**：加入 `.gitignore`（**归 S20，不属 S19 动作**）。
- **是否需要代码修改**：是（`.gitignore`，S20 范围）。

### 10.2 Spec / 用户要求与项目约束轴

#### Spec-1：`ui_now.xml` 违反 AGENTS.md 中性名称纪律（与 Standards P2-5 同源）

- **严重度**：P2｜**当前可达**：是｜**文件**：`android/scripts/ui_now.xml`
- **依据**：`AGENTS.md`「Android 真机验证约束」第 4 条明确禁止在仓库工件中记录真实测试书名。
- **影响**：直接违反项目硬约束；且是「真实书名出现在**设备 dump 文件**里」这一更隐蔽的形态
  （不是报告文字，而是机器产物）。
- **建议**：删除文件 + 补 ignore（同 P2-5）。
- **是否需要代码修改**：是。

#### Spec-2：文档路径错误 `scripts/` vs `android/scripts/`

- **严重度**：P3｜**当前可达**：是
- **文件与行号**：
  - `docs/handoff/current.md:475`、`:958`
  - `docs/qa/2026-09-21-s07-qoder-device-acceptance.md:112`、`:529`
  - `docs/qa/undated/android-wip-audit-2026-09-02.md:57`
- **证据**：仓库根 `scripts/` 下**不存在** `install_with_confirm.ps1`
  （`git ls-files scripts/` 已确认）；真实位置是 `android/scripts/`。
  另有 19 处文档使用**正确**路径 `android/scripts/`，因此这是**局部笔误**而非全局错误。
- **影响**：复制粘贴该命令会失败。`s07-qoder-device-acceptance.md:112` 尤其危险——
  它是一份**被实际执行的验收命令记录**，读者会照抄。
  （注：该次执行可能是在 `android/` 目录下运行的，故命令本身在当时的 CWD 下有效；
  但文档未写明 CWD，脱离上下文即失效。）
- **建议**：统一为 `android/scripts/install_with_confirm.ps1` 并注明 CWD。
  **是否需要代码修改**：否（仅文档）。

#### Spec-3：`tap_usb_install_confirm.py` 有真实价值但处于未跟踪状态

- **严重度**：P3（**Readiness blocker，见 §11**）
- **当前是否可达**：**否**（untracked，未在任何自动化中调用）
- **文件**：`android/scripts/tap_usb_install_confirm.py`
- **证据**：
  - 文档认可其价值：`2026-09-15-...notes.md:178-183` 记录它解决了「Gradle 装 app APK + test APK
    各弹一次 MIUI 确认」的问题，并附实测证据（`Finished 8 tests`、watcher TAP 两次）。
  - 它具备正式脚本**没有**的能力：`:69` `dumpsys window` 快速前台探测
    （对 8 秒倒计时窗口是关键优势）。
  - 它已正确处理编码（`:106` pull + `:114` UTF-8 解码）与 resource-id 匹配（`:119-122`）。
  - 但：无 `adb devices` 校验；serial 为 `required=True`（`:80`，正确）；**无 `-s` 之外的多设备保护**。
- **影响**：如果 `connectedDebugAndroidTest` 再次被 MIUI 拦截，**当前没有 tracked 的解法**，
  必须依赖这个未跟踪文件——而它随时可能被清理。
- **建议**：**正式化**：补文档（用途/平台/依赖/与正式脚本的关系）+ 加 `adb devices` 校验 +
  移入仓库跟踪。**由 Codex 裁决，本轮不实施。**
- **是否需要代码修改**：是（若决定正式化）。

#### Spec-4：`diag_usb_confirm.py` 是纯一次性诊断，无文档

- **严重度**：P3
- **当前是否可达**：否（untracked，零文档引用）
- **文件**：`android/scripts/diag_usb_confirm.py`
- **证据**：文件自述「is the tap too late, or is it ineffective?」——典型的**一次性归因实验**；
  `rg -l` 在 `docs/` 中零命中（`inventory.md:437` 只列举了文件名）。
- **与 Spec-3 的区别**：`tap_usb_install_confirm.py` 是**长期可复用的解法**；
  本脚本是**回答一个具体问题的一次性实验**。
- **建议**：加 `.gitignore` 或删除；若保留，必须补一行说明。
- **是否需要代码修改**：是（ignore 或删除）。

---

## 11. Readiness blocker（单独列出，**不冒充现役故障**）

以下问题在当前**未接线/未采用**状态下不构成故障，但若要把对应资产正式采用，必须先解决。

| # | 资产 | Blocker | 采用前必须解决 |
|---|---|---|---|
| RB-1 | `tap_usb_install_confirm.py` | 未跟踪、无文档、无 `adb devices` 校验 | 补文档 + 加设备盘点 + 纳入跟踪 |
| RB-2 | `diag_usb_confirm.py` | 未跟踪、无文档、一次性实验 | 明确「保留为诊断」或删除 |
| RB-3 | `watch_install_confirm.ps1` | tracked 但使用已证伪路线（`exec-out` + 中文匹配），零引用 | 删除 / 标注废弃 / 重写 |
| RB-4 | `build_display_font.py` | 硬编码原始仓库路径 + `C:\Windows\Fonts` | 改 `__file__` 推导 + 参数化字体路径 |

**说明**：RB-1 与 RB-3 是**同一问题的两面**——「Gradle 并发安装时谁来点 MIUI 确认」这件事，
当前既没有 tracked 的可信实现（RB-3 已被证伪），也没有 tracked 的替代（RB-1 未跟踪）。

---

## 12. 建议的最小后续切片（按依赖顺序，**本轮不实施**）

### 切片 S19-A：安全清理（零风险，可最先做）

1. 删除 `android/scripts/ui_now.xml`（瞬时工件，含真实书名）。
2. 修改 `.gitignore`：`/android/ui*.xml` → 覆盖 `android/scripts/ui*.xml`（或 `/android/**/ui*.xml`）。
3. 修正 4 处文档路径笔误（`scripts/` → `android/scripts/`）+ 注明 CWD。
4. 更新 `run-migration-test.sh:4` 的版本注释。

**依赖**：无。**风险**：极低。**验收**：`git check-ignore -v android/scripts/ui_now.xml` 命中。

### 切片 S19-B：诊断脚本归类（依赖 A）

5. 为 `watch_install_confirm.ps1` 加废弃标注，或删除（**需 Codex 裁决**）。
6. `build_display_font.py` / `dump_ui.py` / `sort_retest.py` 去机器绑定。
7. `dump_ui.py` 改为接收 `--serial`（与 `tap_usb_install_confirm.py` 对齐）。

**依赖**：A（避免同时改 ignore 与文件）。**风险**：低。

### 切片 S19-C：安装脚本校验加固（依赖 Codex 裁决）

8. `install_with_confirm.ps1` 增加 `-TestApk` / `-PackageName` 一致性门（P2-3）。
9. 扩充契约测试覆盖该门。

**依赖**：需先裁决 P2-3 的期望语义（自动推导 vs 显式报错）。**风险**：中（改动正式入口，
必须重跑契约测试）。

### 切片 S19-D：文档与正式化（依赖 Codex 裁决）

10. 新增「Android 诊断脚本清单」文档（用途/平台/依赖/状态）。
11. 若裁决正式化，把 `tap_usb_install_confirm.py` 补文档并纳入跟踪。

**依赖**：B、C。

---

## 13. 建议文件所有权与 SEAM REQUEST

### 13.1 文件所有权建议

| 文件 | 建议 owner | 理由 |
|---|---|---|
| `android/scripts/install_with_confirm.ps1` | **单一集成者** | 正式入口，且与契约测试是同一行为契约（冻结清单 `:143-146` 已如此规定） |
| `android/scripts/install_with_confirm.contract.tests.ps1` | **同上（必须同批改）** | 两者是同一契约；只改一个必然导致契约测试红 |
| `.gitignore` | **集成者** | 根级共享文件 |
| `android/scripts/*.py`（诊断） | Android 线单一 owner | 与产品源码无关，但需避免与窗口 C/D 冲突 |
| `docs/` 中的路径笔误 | **Codex**（文档由 Codex 统一维护） | 任务明确禁止本窗口改文档 |

### 13.2 SEAM REQUEST

- **SEAM-S19-1**：`.gitignore` 修改（根级共享文件）。需集成者授权；
  建议与 S20 的 `.scratch-shots/` 隔离项**同批**处理，避免两次触碰同一文件。
- **SEAM-S19-2**：`android/scripts/install_with_confirm.ps1` + 其契约测试的**成对**修改授权。
  单独修改其中任一个都会破坏契约测试。
- **SEAM-S19-3**：`docs/` 路径笔误修正（5 处）——需 Codex 执行或授权，本窗口不改文档。
- **SEAM-S19-4**：若裁决正式化 `tap_usb_install_confirm.py`，需要新增 `docs/` 说明页，
  属 Codex 文档所有权范围。

**未申请**：任何产品源码、测试源码、Gradle 配置、数据库、D-2-B / S08 / S09 文件的修改权。

---

## 14. 未覆盖项

| # | 未覆盖内容 | 原因 |
|---|---|---|
| U1 | `install_with_confirm.ps1` 在真机上的**实际**确认循环行为 | 禁止 ADB/设备操作；历史证据见 `s07-qoder-device-acceptance.md`（exit 0 + CONFIRM_LOOP） |
| U2 | 契约测试当前是否仍全绿（37 断言） | 禁止运行脚本；历史证据见 `workbuddy-r3-final-acceptance-2026-09-14.md`（`断言合计: 37，失败: 0`） |
| U3 | 未跟踪的 `tap_usb_install_confirm.py` 是否仍能工作 | 需设备 |
| U4 | `sort_retest*.py` 的 perfetto trace 输出是否仍在被消费 | 零文档引用，无法确认其历史使用者 |
| U5 | `docs/qa/evidence/` 下 489 个未跟踪文件的完整清单与归属 | 超出 S19 范围（属 S20 隔离项）；本轮只清点了其中的设备辅助脚本 |
| U6 | `dump_appid.init.gradle.kts` / `retest_benchmark_variant.init.gradle` 是否仍被引用 | 零文档引用；可能在未记录的会话中被使用 |
| U7 | 其他 ROM 上 `com.miui.packageinstaller` 控件 id 是否一致 | 需多设备；脚本已用 `package` + `resource-id` 双重匹配，但 id 本身是 MIUI 专属 |
| U8 | `.scratch-shots/` 截图内容是否含真实书名 | 本轮未逐张检查（12.8 MB PNG）；建议 S20 处理时一并核对 |

---

## 15. 最终结论

### 15.1 S19 是否可以直接进入清理？

**可以进入「切片 S19-A」的安全清理，但不能进入更广的清理。**

- **可以立即做（S19-A）**：删除 `ui_now.xml`、补 `.gitignore`、修文档路径笔误、修注释版本号。
  这四项零风险、无产品影响、无设备依赖。
- **不能立即做**：删除或重写任何 tracked 脚本。理由是
  `watch_install_confirm.ps1`、`dump_appid.init.gradle.kts`、`retest_benchmark_variant.init.gradle`、
  `sort_retest*.py` 虽然零文档引用，但**「零文档引用」不等于「零使用者」**——
  它们可能在未记录的会话中被使用过，且删除 tracked 文件是不可逆的仓库动作。
  §12 的 B/C/D 切片必须先有 Codex 裁决。

### 15.2 是否必须先进行独立复核？

**需要，但只需针对两项**：

1. **P2-3（`-TestApk` 包名校验缺口）** —— 这是对**正式入口**的行为判断，
   且历史报告显示正确用法依赖调用者显式传 `-PackageName`。建议由独立 Agent
   用契约测试的假 adb 机制构造一个「只传 `-TestApk` 不传 `-PackageName`」的场景，
   确认是否真的会误判。**本轮未运行任何脚本，故该项为静态推断。**
2. **P2-1 / RB-3（`watch_install_confirm.ps1` 处置）** —— 删除还是标注，
   需要确认它是否被某个未记录的验收流程依赖。

其余问题（P2-2、P2-4、P2-5、P3-*）证据充分，可直接进入裁决。

### 15.3 哪些文件现在**绝对不能删**

| 文件 | 理由 |
|---|---|
| `android/scripts/install_with_confirm.ps1` | **正式安装入口**，19 处文档依赖 |
| `android/scripts/install_with_confirm.contract.tests.ps1` | **正式契约测试**，冻结清单规定与入口同批 |
| `android/scripts/run-migration-test.sh` | **CI 依赖**（`.github/workflows/ci.yml:140`） |
| `android/scripts/generate_large_txt_fixture.ps1` | 中性 fixture 生成器，handoff 引用 |
| `android/scripts/build_display_font.py` | 正式字体子集工具（产物进 `res/font/`） |
| `android/scripts/tap_usb_install_confirm.py` | **untracked 但当前唯一可信的 Gradle 并发安装解法**（RB-1） |
| `.github/workflows/ci.yml`、`release.yml` | CI/发布链路 |
| 任何 `docs/qa/evidence/**` | 并行 Agent 的验收证据，属 S20 隔离项 |

### 15.4 关于「正式安装入口是否仍是 `install_with_confirm.ps1`」

**是。本轮未发现任何需要更换入口的证据。** 该脚本在设备绑定、编码处理、
错误传播、回退区分、控件匹配五个维度上都优于仓库内其他实现，
且是唯一有契约测试保护的安装脚本。

**唯一需要补强的是 P2-3 的参数一致性门**，这是加固而非替换。

---

## 16. 回传摘要

- **Agent / 模型**：Zcode / `deepseek v4.1Flash`
- **报告绝对路径**：
  `D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919\docs\coordination\investigations\agent-investigation-s19-zcode.md`
- **问题数量**：
  - **P0 × 0**
  - **P1 × 0**
  - **P2 × 5**（P2-1 watch 脚本保留已证伪路线；P2-2 硬编码原始仓库路径；
    P2-3 `-TestApk` 包名校验缺口；P2-4 不可覆盖的硬编码 serial；P2-5 `ui_now.xml` 未忽略且含真实书名）
  - **P3 × 7**（注释过期、零引用诊断脚本、状态纪律无脚本支撑、`.scratch-shots` 未忽略、
    文档路径笔误、`tap_usb_install_confirm.py` 未跟踪、`diag_usb_confirm.py` 无文档）
  - **Readiness blocker × 4**（RB-1 至 RB-4，见 §11）
- **最严重问题**：**P2-5 / Spec-1 —— `android/scripts/ui_now.xml` 含真实书名
  （`不平静的日常_作者：惰天使`）且未被 `.gitignore` 覆盖，违反 AGENTS.md 中性名称纪律。**
  它未跟踪（不在 HEAD），但任何 `git add .` 都会把它带入仓库历史。
- **是否运行 Gradle / ADB / 设备操作**：**全部未运行**。未运行 Gradle、未运行 ADB、
  未连接或操作任何手机、未运行任何安装或诊断脚本、未使用 MuMu。
- **除报告外是否改动文件**：**否**。仅新增本报告一个文件；
  未修改产品源码、测试源码、Gradle、脚本、`.gitignore`、设备状态或任何现有报告。
  未执行 reset / checkout / restore / clean / stash / commit / push / stage。
- **是否申请下一阶段**：**否**。未领取 S20 或其他任务。
  等待 Codex 核验本报告并裁决 §11 的 4 项 readiness blocker 与 §12 的切片顺序。
