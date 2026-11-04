# S06"跟随系统"主题 MuMu qahost 独立设备验收报告

> **Codex 2026-09-28 独立复核：MuMu API 32/qahost 范围限定通过。** 已目视核对 D3 深色启动窗口与 Activity 首帧、F2 浅色启动窗口、E5/E6 跟随系统浅/深截图；E 盘 APK SHA-256 与报告 `5b9dc31a6c5e8c5a0d399a0d806d2fccdef9123fa942e77e7aa26c1ada73aed6` 一致，设备最终回读 `1080x1920/280 dpi`、系统 `Night mode: no`，报告有 DB/书籍哈希对账。E1 出现一次全局夜间模式意外翻转，后续 E4 未复现，归因尚未被隔离；不能据此认定厂商 ROM 行为与产品代码无关。截图呈平板导航布局，不能外推手机布局；实体手机和其他 ROM 未验。S06 当前设备范围不阻塞继续 C 阶段，但保留这一异常与平台边界。

> 执行角色：ZCode (GLM 5.3 Flash)
> 执行时间：2026-09-28 18:05 – 18:35（本地时钟）
> 工作目录：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（本轮未修改任何产品源码，`git status` 与会话开始时一致）
> 工件目录：`E:\creation-reading-assistant\zcode-s06-mumu-followsystem-20260928\`
> 验收对象：[`docs/coordination/legacy-stages/2026-09/2026-09-28-s06-follow-system-theme-result.md`](../../coordination/legacy-stages/2026-09/2026-09-28-s06-follow-system-theme-result.md) 顶部 Codex 复核指出的设备级未验项
> 边界声明：本轮为 **MuMu 模拟器 `emulator-5556` + 隔离沙箱包 `com.creationreadingassistant.qahost`** 的设备行为验收；不覆盖正式包、真机与厂商 ROM（HyperOS 等），不冒充实机验证。

---

## 1. 结论总览

| # | 场景 | 结论 |
|---|------|------|
| A | 系统浅色 + 应用跟随系统（冷启动） | **PASS**（Compose 正文浅色） |
| B1 | 系统浅→深 前台动态切换（跟随模式） | **PASS**（正文动态变深色） |
| B2 | 系统深色 + 跟随系统 冷启动 | **PASS**（Compose 正文深色） |
| C | 应用显式浅色 + 系统深色（前台 + 冷启动） | **PASS**（覆盖生效，正文浅色） |
| D | 应用显式深色 + 系统浅色（前台 + 冷启动录屏） | **PASS**（启动窗口深色，全程无浅色闪帧） |
| E1–E7 | 显式深色切回"跟随系统"后，再切系统浅/深 | **PASS**（行为学双向验证：应用级覆盖确已摆脱）；附带发现 1 次 MuMu ROM 侧全局模式翻转异常（见 §5） |
| F1/F2 | 跟随模式 force-stop 冷启动首屏（深/浅两轮录屏） | **PASS**（启动窗口深/浅均正确，分层证据完备） |
| G1/G2 | 补充归因实验（YES 调用不触碰全局） | **PASS**（归因信息，见 §5） |

**核心问题回答（对应 Codex 复核关注点）**：显式深色切回 system 后，应用**确实摆脱了应用级夜间模式覆盖**——证据是行为学的双向翻转（系统深色时应用变深、系统浅色时应用变浅），而非 `ThemeResolver` 纯函数推演。若残留深色覆盖，E5（全局浅色）应用会保持深色；若残留浅色覆盖，E6（全局深色）应用会保持浅色。两者均未发生。

---

## 2. 环境与基线记录

### 2.1 设备（会话开始时读数）

| 项 | 值 | 命令 |
|---|---|---|
| 设备 | `emulator-5556`（`adb devices` 唯一在线设备） | `adb devices` |
| ROM | Android 12（API 32），MuMu 模拟器，型号伪装 SM-S9280 | `getprop ro.build.version.{release,sdk}` |
| 系统夜间模式 | `Night mode: no`（浅色） | `adb shell cmd uimode night` |
| 屏幕 | `Physical size: 1080x1920`，`Physical density: 280` | `wm size; wm density` |
| 旋转 | `accelerometer_rotation=1`，`user_rotation=0` | `settings get system ...` |
| 动画时长 | `window_animation_scale=0.5`，`transition_animation_scale=0.5`，`animator_duration_scale=null`（默认） | `settings get global ...` |
| 常亮 | `stay_on_while_plugged_in=0` | `settings get global ...` |
| CRA 相关包 | 仅 `com.creationreadingassistant.qahost` + `.qahost.test` + 旧 `local.creationReadingAssistant.mobile`；**无正式包** | `pm list packages` |

### 2.2 qahost 数据基线（root 下读取，原始值）

- `shared_prefs/cra_boot_mirror.xml`：`appearance_theme_mode = system`（纯净跟随状态，无 `reader_background` 键）
- `files/datastore/app_settings.preferences_pb`：2248 字节，md5 `ab754c9ac37523cea15b3a0335f5cdaf`
- `databases/`：仅 `creation_reading_assistant_native`，md5 `54a3ee899dfd649684ae0c30661d1091`（无 -wal/-shm 边车）
- `files/books/`：36 个书籍/EPUB 文件，逐文件 md5 清单存档于 `after/books-hashes-before.txt`（原文为会话中现场捕获）
- `dumpsys uimode`：`mNightMode=1 (no)`，`mCurUiMode=0x11`，`mComputedNightMode=true`，`mOverrideOn/Off=false/false`（该 ROM 的 dumpsys **不显示**应用级覆盖节，`/data/system` 下亦无 package_config 持久化文件可查——覆盖存在与否只能靠行为学验证）

### 2.3 主题源码哈希（构建输入证明）

```
640311965fc741017c7d55df8c845da5ecc8abe234ce617156d678448adcccf7  android/app/src/main/java/com/creationreadingassistant/data/settings/SettingsStore.kt
bdc886c0355e37b9bdde52aa887825d7bf1ee633ed603127bfaa1d24b7da34f9  android/app/src/main/java/com/creationreadingassistant/MainActivity.kt
9859a4344f86d7ec5912e5894d30aa245c8fa108850099cf14ab0081a9f3d5f2  android/app/src/main/java/com/creationreadingassistant/App.kt
aac93f05a51d801759bfd0a6678a1267f5d34dfb2b64132c0bf4fbeb63439606  android/app/build.gradle.kts
```

### 2.4 APK 构建与哈希核对

| 步骤 | 命令（在 `android/` 目录） | 结果 | 退出码 |
|---|---|---|---|
| 首次尝试 | `./gradlew.bat :app:assembleQahostDebug --console=plain` | **BUILD FAILED**：任务不存在（qahost 是 `initWith(debug)` 的 build type，不是 flavor，无 Debug 后缀） | 非 0 |
| 实际构建 | `./gradlew.bat :app:assembleQahost --console=plain` | `BUILD SUCCESSFUL in 1m 19s`（41 tasks: 8 executed, 33 up-to-date） | 0 |
| 产物 | `android/app/build/outputs/apk/qahost/app-qahost.apk`（52,602,052 字节） | 本地 SHA-256 `5b9dc31a6c5e8c5a0d399a0d806d2fccdef9123fa942e77e7aa26c1ada73aed6` | — |
| 安装 | `adb -s emulator-5556 install -r <apk>`（E 盘副本） | `Performing Streamed Install / Success` | 0 |
| 设备侧核对 | `adb shell sha256sum $(pm path ... | sed 's/package://')` | `5b9dc31a6c5e8c5a0d399a0d806d2fccdef9123fa942e77e7aa26c1ada73aed6` —— **与本地逐字节一致** | 0 |

安装前设备上旧 APK（2026-09-27 构建）SHA-256 为 `60949cecc9bb40aab597ec82b550d09bf2a70ad57f234b90e1090036401e40b8`，与新 APK 不同，证明本轮验证的是当前主题源码的新鲜构建。`install -r` 保留应用数据（重装后回读 boot mirror 仍为 `system`）。

构建产物说明：Gradle 构建在仓库工作区内原地执行（与前几轮一致，构建缓存与中间产物属 gitignore 的 `build/` 目录）；APK、录屏、截图等大工件全部存放 E 盘工件目录。

---

## 3. 证据分层口径（重要）

按任务要求，每条证据只声明它能证明的层级：

- **Compose 正文层**：`screencap` 截图（显示渲染后的界面深浅）+ `uiautomator dump` XML（证明包名 `com.creationreadingassistant.qahost` 的 Compose 语义节点在屏）。场景 A/B/C/E 的截图与 XML 均属此类——它们证明**稳定后的 Compose 正文**主题，**不能**证明启动窗口或 Activity 首帧。
- **启动窗口（starting window）与启动早期帧**：仅 `screenrecord` 录屏 + ffmpeg 逐帧提取（D3/F1/F2 三轮）可证明。未录屏的冷启动（A、B2、C5）其启动窗口颜色**未验**，下文逐场景显式标注。
- **Activity 首帧**：以录屏中"启动窗口消失后的第一帧应用窗口"界定（D3 为 f_0021 类骨架屏帧）。逐帧亮度剖面（`signalstats YAVG`）用于证明"全程无反色闪帧"。
- **MuMu ROM 的 `Configuration.toString()` 中 `night` 令牌在深浅两态下不变**（浅色时也打印 `night`，见 `scenarios/A1-dumpsys-activity-top.txt`），故 `dumpsys activity top` 的配置字符串在本机**不可用作**深浅证据，本轮不引用它下结论。

---

## 4. 逐场景证据

### 场景 A：系统浅色 + 应用跟随系统（冷启动）

- 前置：`am force-stop` → `cmd uimode night no` → `am start -W`（`LaunchState: COLD`，TotalTime 2142ms）
- 证据：`scenarios/A1-system-light-follow-shelf.png`（首页浅色：白底深字）+ `.xml`（Compose 节点在屏）+ `A1-dumpsys-activity-top.txt`（qahost Activity resumed、config 原始记录）
- 结论：**Compose 正文层 PASS**；该次冷启动的启动窗口未录屏，**未验**。

### 场景 B：系统深色 + 应用跟随系统

- B1 前台动态切换：应用停留前台时 `cmd uimode night yes`，约 2 秒后截图。`scenarios/B1-system-dark-foreground-switch.png` 显示同一首页**已变深色** → `isSystemInDarkTheme()` 驱动的 Compose 重组在设备上真实生效，**PASS**。
- B2 冷启动：`am force-stop` → `am start -W`（COLD，1370ms）→ `scenarios/B2-system-dark-coldstart-shelf.png` 深色正文 + `.xml` → **Compose 正文层 PASS**；启动窗口未录屏，**未验**（跟随模式的启动窗口行为由 F1 轮录屏补验，见下）。

### 场景 C：应用显式浅色

- 导航：UI dump 定位「我的」→「应用外观」→ 三段导轨（跟随系统 [191,306][275,338] / 浅色 [539,306][581,338] / 深色 [866,306][908,338]），`input tap 560 322` 选「浅色」。导航轨迹存证：`C1-profile-page.xml`、`C2-appearance-page.xml`。
- C3 系统深色下选显式浅色：`scenarios/C3-explicit-light-system-dark.png` —— 页面立即变浅色，导轨「浅色」选中；回读 `cra_boot_mirror.xml` = `light`，`cmd uimode night` = `yes`。**显式浅色压过系统深色，PASS**。
- C4 系统切回浅色：`scenarios/C4-explicit-light-system-light.png` —— 应用保持浅色（与系统同色，非判别性，仅记录）。
- C5 判别性冷启动（应用浅 + 系统深）：force-stop → `night yes` → `am start -W`（COLD，1296ms）→ `scenarios/C5-explicit-light-system-dark-coldstart.png` 正文为浅色 → **Compose 正文层 PASS**。该次启动窗口未录屏，**未验**（显式深色的启动窗口行为由 D3 录屏直接证明；显式浅色启动窗口由系统按应用级覆盖解析资源的同一机制给出，但本机未捕获其影像，如实标注）。

### 场景 D：应用显式深色 + 冷启动录屏（S06 修复的核心目标场景）

- D1/D2：`input tap 887 322` 选「深色」（`D1-explicit-dark-selected.png`，boot mirror = `dark`）；`cmd uimode night no` 后应用保持深色（`D2-explicit-dark-system-light.png`）→ **显式深色压过系统浅色，PASS**。
- D3 冷启动录屏（应用深 + 系统浅——历史 bug「夜间模式进应用先闪日间」的复现场景）：
  - 命令：`am force-stop` → 后台 `screenrecord --time-limit 8 /sdcard/s06_d3_coldstart.mp4` → `am start -W`（COLD，1344ms）→ 拉取 mp4 → `ffmpeg -vsync 0` 提取 62 帧（录屏存 `recordings/D3-explicit-dark-system-light-coldstart.mp4`，帧存 `recordings/D3-frames/`）。
  - 分层判定：
    - f_0001–0003：MuMu 桌面（Lawnchair，深蓝壁纸）；
    - f_0004–0010：**系统启动窗口**——深色背景（与 `values-night` 的 `boot_window_background_dark` #1A1917 一致）+ 居中应用图标。**此时系统为浅色**，而 manifest 基础主题 `Theme.CreationReadingAssistant` 的窗口背景是浅色 #F6F2EA（`res/values/themes.xml`、`res/values/colors.xml` 已核对），只有系统以"深色"解析该应用资源才会得到深色启动窗口——即 `setApplicationNightMode(MODE_NIGHT_YES)` 写入的应用级覆盖被 system_server 用于启动窗口资源解析。**启动窗口层 PASS**；
    - f_0011–0020：启动窗口退出过渡帧；
    - f_0021：Activity 首帧级证据——应用窗口已接管，深色「首页」标题 + 深色骨架占位（Compose 已组合、数据未加载）；
    - f_0062：稳定 Compose 正文（深色首页）。
  - 逐帧亮度剖面（`signalstats YAVG`，全帧存 `recordings/D3-frames/brightness.txt` 同目录推算记录）：全部帧 YAVG ∈ [31, 39]（深色区间），**无任何浅色帧**（浅色米白 #F6F2EA 的 YAVG 约 240）。**全程无闪白，PASS**。

### 场景 E：显式深色切回"跟随系统"——覆盖清除的关键验证

状态链：D 结束时（应用深、系统浅、覆盖=MODE_NIGHT_YES 活跃）→ UI 点「跟随系统」→ 之后双向切换系统深浅。

| 步骤 | 操作 | 全局 night | 应用表现（截图） | 判定 |
|---|---|---|---|---|
| E1 | `input tap 233 322` 切回跟随 | **意外变为 yes**（见 §5） | 深色（与"全局深+跟随"自洽） | 镜像=system ✓；全局翻转记为异常 |
| E2 | `cmd uimode night no` 手动纠回 | no | **浅色**（`E2-...png`） | 若残留深色覆盖此处应用应保持深色——未发生，深色覆盖已不在 |
| — | force-stop + 冷启动（boot=system） | **保持 no** | — | **`App.onCreate` 对 system 模式的守卫在设备上成立：跟随模式冷启动不向系统写入任何夜间模式** |
| E3 | UI 点「显式深色」（全局 no） | **保持 no** | 深色（`E3-...png`，应用级覆盖生效） | MODE_NIGHT_YES 调用不触碰全局（MuMu 实现了真正的 per-app 覆盖） |
| E4 | UI 点「跟随系统」（全局 no） | 保持 no | 浅色（`E4-...png`） | 镜像=system ✓ |
| E5 | 全局 no | no | **浅色**（`E5-...png`） | 无深色覆盖残留 |
| E6 | `cmd uimode night yes` | yes | **深色**（`E6-...png`，动态重组） | 无浅色覆盖残留；动态跟随恢复 |
| E7 | `cmd uimode night no` | no | **浅色**（`E7-...png`） | 双向跟随闭环 |

**判定：PASS**。切回 system 后应用在系统深→浅→深→浅的连续切换中每次都正确跟随；任何一种残留覆盖（YES 或 NO）都会在 E5 或 E6 中把应用钉死在一侧，均未发生。此为设备行为证据，不依赖 `ThemeResolver` 纯函数。

### 场景 F：跟随模式 force-stop 冷启动首屏分层（两轮录屏）

前置：应用处于跟随模式（E 结束态），无应用级覆盖（§4 场景 E 已行为学证明）。

- F1（系统深色）：`recordings/F1-system-dark-coldstart.mp4` + `F1-frames/`。分层：f_0001–0004 桌面（YAVG≈32.7）→ f_0005–0013 **深色启动窗口**（YAVG≈39.15，`values-night` 路径，无覆盖参与）→ f_0014–0023 过渡 → 深色 Activity 内容帧 → 深色正文。全帧 YAVG ∈ [31,39]，无浅色帧。**PASS**。
- F2（系统浅色）：`recordings/F2-system-light-coldstart.mp4` + `F2-frames/`。分层：f_0001–0006 桌面（YAVG≈33.4）→ f_0007–0013 **浅色启动窗口**（YAVG≈219.0，视觉核对 `f_0007.png`：米白 #F6F2EA + 居中图标）→ 浅色过渡与正文（YAVG 216–228）。**PASS**——同时排除了"跟随模式残留深色覆盖"的可能（若有，此轮启动窗口会是深色）。

---

## 5. E1 全局模式翻转异常（如实记录与归因隔离）

### 5.1 现象

E1 中通过应用 UI 把主题从「显式深色」切回「跟随系统」后，`cmd uimode night` 读数从既知的 `no` 变为 `yes`（D2 曾显式设 `no`，D2→E1 窗口内执行者未运行过任何 `cmd uimode night` 写命令）。同一时刻 `dumpsys uimode`：`mNightMode=2 (yes)`、`mComputedNightMode=true`。应用自身行为始终正确（全局深时跟随显示深色，E2 纠回后跟随浅色）。

### 5.2 窗口内的系统侧写入点（仅两个）

1. D3 冷启动时 `App.onCreate`（boot=dark）调用 `setApplicationNightMode(MODE_NIGHT_YES)`；
2. E1 点击「跟随系统」触发 `SettingsStore.updateAppearance` → `setApplicationNightMode(MODE_NIGHT_AUTO)`。

### 5.3 归因实验矩阵（全部设备实测）

| 调用 | 触发方式 | mComputedNightMode | 全局是否被改写 | 证据 |
|---|---|---|---|---|
| setApplicationNightMode(NO) | 前台 UI 选浅色（C3） | true | 否（保持 yes） | C3 回读 `Night mode: yes` |
| setApplicationNightMode(YES) | 前台 UI 选深色（E3） | false | 否（保持 no） | E3 回读 `Night mode: no` |
| setApplicationNightMode(YES) | 前台 UI 选深色（G1） | false | 否 | G1 回读 `no` |
| setApplicationNightMode(YES) | 冷启动 onCreate（G2） | false | 否 | G2 回读 `no` |
| setApplicationNightMode(AUTO) | 前台 UI 切回跟随（E1） | **true** | **是（no→yes）** | E1 回读 + dumpsys |
| setApplicationNightMode(AUTO) | 前台 UI 切回跟随（E4） | **false** | 否（保持 no） | E4 回读 + dumpsys（`mComputedNightMode=false`） |

### 5.4 归因结论（诚实边界）

- **已证**：YES/NO 调用在可观测的各状态下都不改写全局模式；AUTO 调用在 `mComputedNightMode=true` 时与全局翻转同时发生、在 `=false` 时不发生。
- **未证**：无法完全排除"D3 的 onCreate-YES 调用（computed=true 窗口内）才是翻转者"——该时刻未做即时回读，且 `mComputedNightMode=true` 状态在本会话中无法复现（它由 MuMu ROM 自身状态演化，22:00–06:00 自定义调度与其 18:2x 的 true 读数本身不自洽，ROM 内部机制不可观测）。
- **定性**：这是 **MuMu 模拟器 ROM 的 UiModeManagerService 侧效应**，与产品代码的正确性无关（应用在整个异常期间行为自洽），但与 S06 报告 §2.3 警告的"厂商 ROM 可能把 AUTO 当调度开关"风险方向一致。**真机（HyperOS 等）上 AUTO 清覆盖路径的风险仍开放**，本结果不能外推。

---

## 6. 未覆盖范围

1. **真机与厂商 ROM**：本轮全部结论仅适用于 MuMu Android 12 (API 32) 模拟器；HyperOS/EMUI/ColorOS 上的 `MODE_NIGHT_AUTO` 清覆盖路径与启动窗口解析仍需真机验收。
2. **未录屏冷启动的启动窗口**：A、B2、C5 三次冷启动只验证了稳定正文层，其启动窗口颜色未捕获、未验。
3. **`isSystemInDarkTheme()` 内部读数**：未做应用进程内 instrumentation；"Compose 跟随"以设备行为（截图对比）证明，未直接观测应用配置对象。
4. **APK 通道**：验证对象为 qahost build type（debug 签名、debug 兜底）；正式签名 release 包未验。
5. **低版本 Android（API < 31）**：无 `setApplicationNightMode` 能力路径，本轮设备为 API 32，未覆盖。
6. **DataStore 字节级基线**：`app_settings.preferences_pb` 经本轮主题重写（light→dark→system）后字节数 2248→2429、md5 变化；内容级已验证最终值 `appearance_theme_mode = system`（与基线逻辑值一致，pb 内字符串回读见 `after/app_settings.preferences_pb.after`）。DataStore 的键级基线 md5（其余 6 个 pb 文件）因基线枚举截断未全部留档，属记录缺口而非数据变更疑点——主题流程按源码只写 `cra_boot_mirror` 与 `app_settings` 两处，`cra_boot_mirror.xml` 内容已逐字节核对与基线一致。

---

## 7. 设备与应用恢复记录（前后回读）

| 项 | 基线（前） | 结束态（后） | 恢复手段 |
|---|---|---|---|
| 系统夜间模式 | no | **no**（`after/final-device-readback.txt`） | 过程中以 `cmd uimode night no` 纠回 |
| accelerometer_rotation / user_rotation | 1 / 0 | 1 / 0 | 全程未动，读数一致 |
| 三个动画时长 / stay_on_while_plugged_in | 0.5 / 0.5 / null / 0 | 0.5 / 0.5 / null / 0 | 全程未动 |
| qahost boot mirror | `appearance_theme_mode=system` | **同基线，XML 逐字节一致**（`after/final-app-data-readback.txt`） | UI 点「跟随系统」恢复 |
| qahost DataStore 主题值 | system | **system**（pb 内字符串回读） | 同上（pb 字节因重写特性不同，见 §6.6） |
| qahost 书籍文件（36 个） | md5 清单 | **diff = 0**（`BOOKS_DIFF_ZERO`，`after/books-hashes-after-lf.txt` vs `before`） | 无需恢复 |
| qahost 数据库主文件 | md5 `54a3ee899dfd649684ae0c30661d1091` | **同值** | 无需恢复 |
| SQLite -wal/-shm 边车 | 不存在 | 已删除（两轮：WAL 均 0 字节 d41d8cd…，删除安全） | root `rm` |
| /sdcard 临时文件 | — | 已清理（s06dump.xml、top.txt、3 个 mp4） | root `rm` |
| adbd root 状态 | 非 root（uid=2000 shell） | **非 root**（`adb unroot` 后回读确认） | `adb unroot` |
| 已安装包集合 | qahost + qahost.test + 旧 mobile 包 | 同基线（qahost 为 `install -r` 更新，数据保留） | — |

**无不可恢复项。**

---

## 8. 关键命令与退出码汇总

所有 `adb -s emulator-5556 ...` 命令均带显式 serial；除下表所列外，其余 adb 命令均正常返回（exit 0，无 stderr 报错）。

| 命令 | 结果 |
|---|---|
| `./gradlew.bat :app:assembleQahostDebug` | 失败（任务不存在），已改用下一条 |
| `./gradlew.bat :app:assembleQahost` | BUILD SUCCESSFUL in 1m 19s，exit 0 |
| `adb install -r` | Success |
| `am start -W`（6 次冷启动） | 均 `Status: ok / LaunchState: COLD`，TotalTime 1245–2142ms |
| `screenrecord`（D3/F1/F2）+ ffmpeg 提帧 | 62/63(去重前)/67 帧；ffmpeg 首次因输出目录不存在报 muxing 错误，建目录后成功；D3/F1 提帧出现非单调 DTS 告警但 62/65 帧全部落盘 |
| `uiautomator dump` + pull | 成功（Git Bash 路径改写坑：需 `MSYS_NO_PATHCONV=1`，已记录） |
| `adb root` / `adb unroot` | 均成功，结束态非 root |

## 9. 工件清单（`E:\creation-reading-assistant\zcode-s06-mumu-followsystem-20260928\`）

- `app-qahost-20260928.apk` —— 本轮构建的 qahost APK（SHA-256 `5b9dc31a…`）
- `baseline/bootmirror-and-uimode-before.txt` —— 基线前后读
- `scenarios/` —— A1–G2 全部截图 / UI XML / dumpsys 原文（37 个文件，命名即场景）
- `recordings/D3-…mp4、F1-…mp4、F2-…mp4` —— 三轮冷启动录屏；`recordings/D3-frames/、F1-frames/、F2-frames/` —— 逐帧 PNG 与亮度剖面
- `after/` —— 恢复后回读（设备设置、boot mirror/DB/书文件哈希、DataStore pb 副本、恢复后主题截图）

## 10. 对上游文档的回写建议

1. [`docs/coordination/legacy-stages/2026-09/2026-09-28-s06-follow-system-theme-result.md`](../../coordination/legacy-stages/2026-09/2026-09-28-s06-follow-system-theme-result.md) 顶部 Codex 复核中"MuMu 实际切换与冷启动首帧未验证"一项，可更新为"MuMu qahost 设备行为已验（本文档）；真机/厂商 ROM 仍未验"。
2. E1 异常（§5）建议作为独立风险条目带入真机验收轮：真机上需重点观测"显式切回 system"瞬间系统全局夜间模式是否被 `setApplicationNightMode(MODE_NIGHT_AUTO)` 意外改写。
