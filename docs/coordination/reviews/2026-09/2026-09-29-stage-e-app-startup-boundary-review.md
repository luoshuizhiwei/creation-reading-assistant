# 阶段 E — `App.kt` 启动依赖边界调查（只读，2026-09-29）

> **窗口**：E 阶段 `App.kt` 启动依赖边界只读调查（对应[16 行裁决](../../legacy-stages/2026-09/2026-09-29-stage-e-hold-16-adjudication.md) §七 建议任务 **N4**）。
> **工作仓库**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> **分支 / HEAD 已核对**：`codex/recover-worktree-20260920` / **`513891d6a66a9b78e072ba759d34ed580d377ecf`**（= 首批 A `209ec8a` → B `513891d`）；`git log --oneline -3` 实测 `513891d` → `209ec8a` → `5aed593`；`git status --short` 工作树 **537** 条。
> **输入**：[AGENTS.md](../../../../AGENTS.md)、[当前交接](../../../handoff/current.md)、[16 行裁决](../../legacy-stages/2026-09/2026-09-29-stage-e-hold-16-adjudication.md)、[S04 启动隔离设计](../../2026-09-25-s04-instrumentation-startup-isolation-design.md)、[S06 跟随系统主题报告](../../legacy-stages/2026-09/2026-09-28-s06-follow-system-theme-result.md)、[集成决策包](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md)、`E:\creation-reading-assistant\codex-e-preflight-20260929\stage-e-full-coverage-matrix.csv`（376 行）、`E:\creation-reading-assistant\stage-e-incremental-20260929\hold-180-row-mapping-updated.csv`（180 行）。
> **性质**：只读调查。**未改**任何产品源码、测试、资源、CSV、公共配置或其他窗口报告；**未** stage / commit / push / reset / checkout；**未**构建、**未**连接设备、**未**迁目录、**未**触碰原仓库与正式书库。仓库内**唯一写入 = 本文件**。
> **零构建声明**：本报告**没有运行任何 Gradle 命令**（无 `compileDebugKotlin`、无 `testDebugUnitTest`、无 `lintDebug`、无 `compileDebugAndroidTestKotlin`）。下文所有「可编译 / 编译失败」表述均来自 `git grep HEAD` 的符号存在性实测与 `git diff` 的 hunk 边界实测，**不宣称任何候选已通过编译或测试**。

---

## 一、结论摘要（先说人话）

1. **`App.kt` 的新改动不是一个功能，而是四摊事挤在一个文件里**：① 崩溃日志增强（加设备型号、内存、版本号）；② 把首次打开「引导标记文件」提前到进程最早期（避免首帧前卡 ~250ms）；③ 把三项启动接线（EPUB 修复、阅读目标提醒、全文搜索索引）从主线程挪到后台线程 `cra-startup`；④ S06 启动主题：把用户的应用内深浅色告诉系统，让冷启动第一帧就是正确颜色。
2. **只有第 ④ 项（S06 主题）在[集成决策包](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md)里有批次归属**（批次 3 / G3）。但**批次 3 目前写的文件清单是残缺的，照它执行必然编译不过**：`App.kt` 与 `MainActivity.kt` 都调用 `SettingsStore.kt` 里的 `BootMirror` / `ThemeResolver`，而 `SettingsStore.kt` 与 `App.kt` 在 376 行矩阵里都还是 HOLD、批次 3 未列；`SettingsStoreBootMirrorTest.kt` 虽被列进 G3，但它测的 `BootMirror`/`ThemeResolver` 定义体恰恰在未列出的 `SettingsStore.kt` 里。
3. **第 ①②③ 项在 376 行矩阵与 180 行 CSV 中都没有任何批次覆盖**（`App.kt` 的 `reason` 字段原文即「无任何候选批次覆盖」）。第 ② 项虽在 [S04 启动隔离设计](../../2026-09-25-s04-instrumentation-startup-isolation-design.md) §3.2 被逐字记录并判定「已证实」，但那是**设计事实登记**，不是批次归属。
4. **`App.kt` 的 10 个 hunk 里有 2 个是多族混装**；更关键的是，用 `git add -p` 实际使用的 3 行上下文粒度看，`App.kt` 只剩 **6 个 hunk**，其中「S06 主题镜像」与「后台线程搬迁」被并进**同一个 hunk**，「ONBOARDING_PREFS 预热」与「版本号诊断」也被并进**同一个 hunk**。因此**只能手工编辑 hunk，`git add -p` 不可用**。
5. 给出一个**不夹带其他功能的最小隔离候选清单**（§十）与**排除项**（§十一）。该候选**只覆盖 S06 启动主题这一族**；诊断增强与后台线程搬迁因缺少批次归属，明确排除。

---

## 二、方法与证据来源

### 2.1 本轮实际执行的只读命令

| 类别 | 命令 |
|---|---|
| 版本基线 | `git rev-parse HEAD`、`git log --oneline -3`、`git status --short` |
| 状态与规模 | `git status --porcelain -uall -- <path>`、`git diff --numstat` |
| hunk 边界 | `git diff -U0 / -U3 -- <path>`、`git diff -U0 \| Select-String '^@@'` 计数 |
| 符号存在性 | `git grep -n <符号> HEAD -- android`、`git grep -n --untracked -e <符号> -- android` |
| 基线内容 | `git show HEAD:<path>`、`git ls-tree -r --name-only HEAD -- <dir>` |
| 资源与清单 | `Select-String` 读 `values/themes.xml`、`values-night/themes.xml`、`AndroidManifest.xml`、`build.gradle.kts` |
| 外部清单 | `Import-Csv` 读 376 行覆盖矩阵与 180 行 HOLD 映射 CSV |

### 2.2 判定口径（避免口径漂移）

| 标记 | 含义 |
|---|---|
| **既定启动主题设计** | 已被本仓库既有报告逐字记录并判定「已证实」/「已定」的 S06 启动主题链路 |
| **既定启动隔离设计** | 已被 [S04 启动隔离设计](../../2026-09-25-s04-instrumentation-startup-isolation-design.md) 记录并驱动「方案 A 换 Application」的启动隔离链路 |
| **仅诊断增强** | 只影响崩溃日志 / 日志轮转的内容，不改变启动功能语义 |
| **有批次归属** | 376 行矩阵 `group` 字段或集成决策包批次清单明确点名 |
| **无批次归属** | 上述两处均未覆盖，或 180 行 CSV `reason` 明写「无任何候选批次覆盖」 |
| **MIXED** | 同一 hunk 内含两个及以上族，`git add -p` 无法物理切开 |

### 2.3 一个必须写清的口径差

**376 行矩阵的 `group` 与 180 行 CSV 的 `mapping` 不一致**，本报告两者都列、不合并：

| 文件 | 376 行矩阵 `group` | 180 行 CSV `mapping` |
|---|---|---|
| `App.kt` | **HOLD**（needs manual attribution） | 保留 HOLD |
| `MainActivity.kt` | **HOLD**（needs manual attribution） | **候选分组 B3（未隔离编译）** |
| `data/settings/SettingsStore.kt` | **HOLD** | 保留 HOLD（§2.9 Reader 设置覆盖 29 hunks） |
| `ui/onboarding/OnboardingOverlay.kt` | **HOLD** | 保留 HOLD |
| `data/settings/SettingsStoreBootMirrorTest.kt` | **HOLD** | **候选分组 B3（未隔离编译）** |
| `res/values/colors.xml`、`res/values/themes.xml`、`res/values-night/themes.xml` | **G3（S06 theme）** | 不在 180 行内 |
| `ui/util/TimeUtils.kt` | **HOLD** | 候选分组 B3 |

`MainActivity.kt` 与 `SettingsStoreBootMirrorTest.kt` 在 180 行 CSV 里被挂到 **B3**，但 B3 在 376 行矩阵中的成员只有「时长单位」族（`DurationPrefs.kt`、`DurationDisplay.kt`、`DurationPrefsTest.kt`、`DurationUnitTest.kt`、两个 androidTest）。**`SettingsStoreBootMirrorTest.kt` 测的是启动主题（`BootMirror`/`ThemeResolver`），不是时长单位**（实测：全文件 0 处 `ReaderSettings`/`BatteryTipStyle`/`DurationUnit` 命中，见 §九）。这是一个应在后续裁决中修正的映射错位。

---

## 三、`App.kt` 逐 hunk 归属（`-U0` 实测 10 hunk，与 H005 一致）

工作树行号取自 `git diff -U0` 的新侧；HEAD 侧行号见 hunk 头。

| # | `-U0` hunk | 工作树行 | 内容 | 族 | 归属判定 |
|---|---|---|---|---|---|
| A1 | `@@ -10,0 +11 @@` | 11 | `+import …ui.onboarding.ONBOARDING_PREFS` | 启动隔离（I） | **无批次归属** |
| A2 | `@@ -29,0 +31,3 @@` | 31–33 | `+TAG_BOOT = "CRA-Boot"` 常量 + KDoc | 后台线程搬迁（T）的配套符号 | **无批次归属** |
| A3 | `@@ -42 +46 @@` | 46 | 崩溃日志时间戳 `MM-dd` → `yyyy-MM-dd` | 仅诊断增强（D） | **无批次归属** |
| A4 | `@@ -44 +48,9 @@` | 48–56 | `writeFatal` 增写 `Device:` / `Heap:` 两行 | 仅诊断增强（D） | **无批次归属** |
| A5 | `@@ -53,0 +66,18 @@` | 66–83 | `base?.let { getPackageInfo → AppContextHolder.appVersion; 预热 getSharedPreferences(ONBOARDING_PREFS) }` | **MIXED：D（版本号）+ I（预热）** | 无批次归属 |
| A6 | `@@ -91,15 +121,4 @@` | 121–124 | 删除 HEAD 91–105 三项内联启动接线；插入 S06 主题镜像注释 | **MIXED：T（删接线）+ S06（S）** | T 无批次；S 属 G3 |
| A7 | `@@ -107,3 +126,5 @@` | 126–130 | 把原 `SearchIndex` 的 `runCatching` 体换成 `BootMirror.themeMode` + 条件 `applyApplicationNightMode` | S06 启动主题（S） | **G3（决策包批次 3 已点名）** |
| A8 | `@@ -110,0 +132,34 @@` | 132–165 | `Thread({ … }, "cra-startup").start()` 内三项接线 + 三条 `onFailure` 日志 | 后台线程搬迁（T） | **无批次归属**（S04 隔离设计已登记） |
| A9 | `@@ -125 +180 @@` | 180 | `AppContextHolder` KDoc 补「与应用版本」 | 仅诊断增强（D） | 无批次归属 |
| A10 | `@@ -127,0 +183 @@` | 183 | `+var appVersion: String? = null` | 仅诊断增强（D） | 无批次归属 |

**族计数**：D = A3/A4/A9/A10 + A5 的一半；I = A1 + A5 的一半；T = A2/A6 的一半/A8；S = A6 的一半/A7。

### 3.1 `-U3`（`git add -p` 实际粒度）合并结果：10 → 6 hunk

| `-U3` hunk | 合并了哪些 `-U0` hunk | 混装族 |
|---|---|---|
| `@@ -8,6 +8,7 @@` | A1 | I |
| `@@ -27,6 +28,9 @@` | A2 | T |
| `@@ -39,9 +43,17 @@` | A3 + A4 | D |
| `@@ -51,6 +63,24 @@` | A5 | **MIXED：D + I** |
| `@@ -88,26 +118,51 @@` | A6 + A7 + A8 | **MIXED：T + S06** |
| `@@ -122,9 +177,10 @@` | A9 + A10 | D |

**这是本报告最重要的机械事实**：在 `git add -p` 的粒度下，**S06 主题镜像（A7）与后台线程搬迁（A8）在同一个 hunk 里**，且该 hunk 内部**没有任何未变更行可供 `s`（split）切开**（`-88,26` 是连续删除块接连续新增块）。同理 **`ONBOARDING_PREFS` 预热（A5 后半）与版本号诊断（A5 前半）不可用 `git add -p` 分开**。

---

## 四、`App.kt` 的实际依赖图（以 HEAD + 工作树 diff 实测为准）

### 4.1 编译期硬依赖（缺一个即 `unresolved reference`）

| 工作树引用点 | 符号 | 定义位置 | HEAD 是否已有 |
|---|---|---|---|
| `App.kt:11`（import）、`:82`（调用） | `ONBOARDING_PREFS` | `ui/onboarding/OnboardingOverlay.kt:77` | **0 命中**（`git grep -n 'ONBOARDING_PREFS' HEAD -- android` 空） |
| `App.kt:126`、`:128` | `BootMirror`（`themeMode`、`applyApplicationNightMode`） | `data/settings/SettingsStore.kt:369`（族内方法 `:370`、`:427`） | **0 命中**（`git grep -n 'BootMirror' HEAD -- android` 空） |
| `App.kt:52` | `AppContextHolder.appVersion` | `App.kt:183`（同文件新增） | **0 命中**（HEAD 仅有 `cacheDir`） |
| `App.kt:149/155/164` | `TAG_BOOT` | `App.kt:32`（同文件新增） | 不适用（同文件） |
| `App.kt:146/152/161` | `EpubRepairEntryPoint` / `GoalSchedulerEntryPoint` / `SearchIndexEntryPoint` | `App.kt:189/…/196` | **已有**（HEAD 已存在） |

**⇒ 结论：单独取 `App.kt` 必然编译失败**（缺 `ONBOARDING_PREFS` 与 `BootMirror`），与[16 行裁决](../../legacy-stages/2026-09/2026-09-29-stage-e-hold-16-adjudication.md) §四 4.2 H005 的判定一致，本轮复核确认。

### 4.2 `App.kt` **不**依赖的符号（排除项的依据）

- **`ThemeResolver`**：`App.kt` 全文 0 处引用（只有 `MainActivity.kt:32/112/165` 与 `SettingsStoreBootMirrorTest.kt` 用）。⇒ 若只做「`App.kt` 启动最小候选」，`ThemeResolver` 属可选；但**若做完整 S06 候选（含 `MainActivity`）则必需**。
- **`ReaderSettings` 的新增字段**：`App.kt` 0 处引用；`SettingsStore.kt` 的 `bootReader` 只用 `ReaderSettings(background = …)`，而 **HEAD 的 `ReaderSettings` 已有 `val background: String = "follow"`**（`git show HEAD:…SettingsStore.kt` 实测）⇒ **BootMirror 族不依赖阅读器设置新增字段**。
- **`ReaderDayNightToggle`**（376 行矩阵判 **EXCLUDED**）：`App.kt` 0 处引用。
- **`isCordHintSeen` / `markCordHintSeen`**（同属灯绳 EXCLUDED 域）：`App.kt` 0 处引用。
- **`DurationPrefs` / `DurationUnit` / `LocalDurationUnit` / `LocalDurationUnitToggle`**（G6）：`App.kt` 0 处引用。
- **`ReaderHardwareKeys.dispatchScroll`**（新符号，HEAD 0 命中）：`App.kt` 0 处引用。

### 4.3 其他新符号的调用方穷举（本轮实测）

| 新符号 | 全仓消费者（`git grep --untracked`） | 归属 |
|---|---|---|
| `BootMirror` | `App.kt:126/128`、`MainActivity.kt:28/161`、`SettingsStore.kt:369/447/473/477/486/488/492/501/658`、`SettingsStoreBootMirrorTest.kt`（34/98/103/104/109/110/121/122/127/128/140/141/146/147/238/243/248/253） | S06 启动主题 |
| `ThemeResolver` | `MainActivity.kt:32/112/165`、`SettingsStore.kt:353/408`、`SettingsStoreBootMirrorTest.kt`（156–230） | S06 启动主题 |
| `ONBOARDING_PREFS` | `App.kt:11/82`、`OnboardingOverlay.kt:77/78` | 启动隔离 |
| `AppContextHolder.appVersion` | `App.kt:52/75/183`（**仅自身**） | 仅诊断 |
| `isCordHintSeen` / `markCordHintSeen` | `ReaderInteractionLayer.kt:30/31/200/204`、KDoc 引用 `ReaderDayNightCord.kt:222` | 灯绳（EXCLUDED） |
| `ReaderHardwareKeys.dispatchScroll` | `MainActivity.kt:227`、`ReaderHardwareKeys.kt:49`、`ReaderHardwareKeysTest.kt:145–158` | 阅读器硬件键（新族，无批次） |

---

## 五、`SettingsStore.kt` 的 `BootMirror` / `ThemeResolver` 族边界（29 hunk 中的 7 个）

### 5.1 族内 7 个 `-U0` hunk

| # | `-U0` hunk | 工作树行 | 内容 | `-U3` 对应 hunk | 备注 |
|---|---|---|---|---|---|
| S1 | `@@ -236,0 +319,121 @@` | 319–439 | 启动镜像注释 + 3 个常量 + `ThemeResolver` + `BootMirror` 全体 | `@@ -234,6 +316,127 @@` | **文件内 MIXED**（见 5.2） |
| S2 | `@@ -243,0 +447,3 @@` | 447–449 | `private val appContext: Context = context` | `@@ -241,6 +444,9 @@` | 独立 |
| S3 | `@@ -263,0 +470,34 @@` | 470–503 | `bootAppearance` / `mirrorBootTheme` / `backfillBootThemeIfNeeded` / `mirrorBootReaderBackground` / `bootReader` | `@@ -261,6 +467,40 @@` | 独立 |
| S4 | `@@ -271,2 +511,4 @@` | 511–514 | `.also { backfillBootThemeIfNeeded(...) }` + `stateIn(…, bootAppearance)` | `@@ -268,8 +508,10 @@` | 独立 |
| S5 | `@@ -326 +586 @@` | 586 | `stateIn(…, ReaderSettings())` → `stateIn(…, bootReader)` | **被并入 `@@ -322,8 +573,17 @@`** | **`-U3` 与阅读器设置族 MIXED**（见 5.3） |
| S6 | `@@ -395,0 +656,3 @@` | 656–658 | `updateAppearance` 内 `mirrorBootTheme` + `applyApplicationNightMode` | `@@ -393,6 +653,9 @@` | 独立 |
| S7 | `@@ -406,0 +670 @@` | 670 | `updateReader` 内 `if (next.background != …) mirrorBootReaderBackground(…)` | `@@ -404,6 +667,7 @@` | 独立 |

**7 + 22（阅读器设置族）= 29**，与 376 行矩阵「needs manual attribution」、180 行 CSV「§2.9 Reader 设置覆盖(29 hunks)」逐字对上。

### 5.2 S1 的文件内拆分点与一处文档瑕疵

S1（工作树 319–439）**自足可编译**：HEAD 的 `SettingsStore.kt` 第 3–4 行**已有** `import android.content.Context` 与 `import android.content.SharedPreferences`（`git show HEAD:…` 实测），`BootMirror`/`ThemeResolver` 不需要任何新 import。

**精确手工拆分点（若要把 `ThemeResolver` 与 `BootMirror` 分开取用）**：

- 工作树 **342–359** = `ThemeResolver` 的 KDoc + `object ThemeResolver { … }`，**整体独立**，可单独取或单独留。
- 工作树 **361–439** = `BootMirror` 的 KDoc + `internal object BootMirror { … }`，**整体独立**。
- 工作树 **319–332** = 启动镜像族注释 + `BOOT_MIRROR_PREFS` / `MIRROR_THEME_MODE` / `MIRROR_READER_BACKGROUND` 三个 `private const`，**`BootMirror` 必需**；`ThemeResolver` **不需要**它们。
- **工作树 334–341 是一段悬空重复 KDoc**：`BootMirror` 的 KDoc 被完整复制了两份，第一份（334–341）后面紧跟的是 `ThemeResolver` 的 KDoc（342–352），**该段没有附着任何声明**。这是纯文档瑕疵，**不改变任何行为**，但会让「按 KDoc 找声明」的读者误判 `BootMirror` 在 342 行之前。建议实施窗口顺手删除 334–341，**不属任何功能族**。

### 5.3 S5 是跨族 MIXED hunk（`git add -p` 不可用）

`-U3` 的 `@@ -322,8 +573,17 @@` 同时包含：

- **阅读器设置族**（`customTipHeaderLeft/Right`、`customTipFooterLeft/Right`、`customTipHeaderCenter/FooterCenter`、`batteryTipStyle`，工作树 576–584）；
- **启动镜像族**（工作树 585 的 `)` 是未变更行，586 行 `}.stateIn(scope, …, bootReader)` 是本族）。

两族之间**只有工作树 585 的 `)` 一行未变更**，`-U3` 下被并成一个 hunk。`-U0` 下二者是**两个独立 hunk**（`@@ -324,0 +576,9 @@` 与 `@@ -326 +586 @@`），边界清晰。

**⇒ 手工拆分点：工作树第 586 行的单行替换。** 若用 `git add -p` 整 hunk 取用，会把 `batteryTipStyle` / `customTip*` 一并拖入，从而**必须同时带上** `BatteryTipStyle` 枚举（`@@ -41,0 +42,23 @@`）、`KEY_CUSTOM_TIP_*` / `KEY_BATTERY_TIP_STYLE`（`@@ -115,0 +145,7 @@`）、`ReaderSettings` 的 7 个新字段与 `effective*()` 方法 + `headerItemToTemplate`（`@@ -224 +270,37 @@`）、以及对应的读取与写入 hunk——即**夹带整个「阅读器页眉页脚自定义」功能**。这正是必须避免的。

### 5.4 `BootMirror` 族的**行为**语义（写清后果，不裁决）

| 写入点 | 触发时机 | 行为 |
|---|---|---|
| `bootAppearance` / `bootReader`（S3） | `SettingsStore` 实例构造时 | 从 `cra_boot_mirror` SharedPreferences **同步**读两个字段，作为 `appearance` / `reader` 两个 `StateFlow` 的首帧初值 |
| `backfillBootThemeIfNeeded`（S3/S4） | `appearance` 第一次从 DataStore 读到值时 | 镜像缺失或值不同时补写镜像，并调用 `applyApplicationNightMode` |
| `mirrorBootTheme` + `applyApplicationNightMode`（S6） | 用户改外观时 | 写镜像 + **写系统级 `UiModeManager` 应用夜间模式（API 31+，持久、跨进程、在应用沙箱之外）** |
| `mirrorBootReaderBackground`（S7） | 用户改纸色时 | 只写镜像，不碰系统 |

**新增的持久化落点**：`shared_prefs/cra_boot_mirror.xml`（HEAD 无此文件）。**新增的系统级副作用**：`setApplicationNightMode`（[S04 隔离设计](../../2026-09-25-s04-instrumentation-startup-isolation-design.md) §3.3 已把它列为「**系统级、跨进程持久设置，测试结束不会自动还原**」）。

---

## 六、`MainActivity.kt` 的 S06 部分与其他族（`-U0` 实测 16 hunk）

### 6.1 逐 hunk 归属

| # | `-U0` hunk | 工作树行 | 内容 | 族 | 归属 |
|---|---|---|---|---|---|
| M1 | `@@ -6,0 +7 @@` | 7 | `+import android.widget.Toast` | G6 时长 | B3/G6 |
| M2 | `@@ -7,0 +9 @@` | 9 | `+import android.view.MotionEvent` | 滚轮 | **无批次归属** |
| M3 | `@@ -13,0 +16,2 @@` | 16–17 | `+CompositionLocalProvider` + `+LaunchedEffect` | **MIXED：G6 + 启动隔离** | 部分 G6、部分无归属 |
| M4 | `@@ -16,0 +21 @@` | 21 | `+rememberCoroutineScope` | G6 | B3/G6 |
| M5 | `@@ -18,0 +24 @@` | 24 | `+LocalContext` | G6 | B3/G6 |
| M6 | `@@ -21,0 +28,3 @@` | 28–30 | `+BootMirror`（S06）+ `+DurationPrefs`、`+DurationUnit`（G6） | **MIXED：S06 + G6** | 部分 G3、部分 B3/G6 |
| M7 | `@@ -22,0 +32 @@` | 32 | `+import …ThemeResolver` | S06 | **G3** |
| M8 | `@@ -30,0 +41,2 @@` | 41–42 | `+LocalDurationUnit`、`+LocalDurationUnitToggle` | G6 | B3/G6 |
| M9 | `@@ -43,0 +56 @@` | 56 | `+@Inject lateinit var durationPrefs: DurationPrefs` | G6（Hilt 注入） | B3/G6 |
| M10 | `@@ -47,2 +60,6 @@` | 60–65 | 替换「Activity 必须先完成系统初始化」注释；在 **`super.onCreate` 之前**插入 `applyBootWindowTheme()` | S06 | **G3** |
| M11 | `@@ -58,5 +75,36 @@` | 75–110 | **MIXED**：删 `showOnboarding` 同步读（隔离）+ 删内联 `darkTheme when`（S06）+ 加时长块（G6）+ 加引导异步块（隔离） | **G6 + 隔离 + S06** | 部分 G3、部分 G6、部分无归属 |
| M12 | `@@ -63,0 +112,4 @@` | 112–115 | `val darkTheme = ThemeResolver.resolveAppDarkTheme(…)` | S06 | **G3** |
| M13 | `@@ -76,4 +128,10 @@` | 128–137 | **MIXED**：`CompositionLocalProvider`（G6）+ `if (onboardingCompleted == false)`（隔离） | **G6 + 隔离** | 部分 G6、部分无归属 |
| M14 | `@@ -92,0 +151,21 @@` | 151–170 | `private fun applyBootWindowTheme()` 定义 | S06 | **G3** |
| M15 | `@@ -108 +187,13 @@` | 187–199 | `cra_fatal.shown` → `cra_fatal_$ts.shown` + 只保留最近 5 份 | **仅诊断增强** | **无批次归属** |
| M16 | `@@ -129,0 +221,15 @@` | 221–235 | `override fun dispatchGenericMotionEvent`（鼠标滚轮 → `ReaderHardwareKeys.dispatchScroll`） | 阅读器硬件键 | **无批次归属** |

### 6.2 `-U3` 合并结果：16 → 8 hunk

| `-U3` hunk | 合并了哪些 | 混装族 |
|---|---|---|
| `@@ -4,22 +4,32 @@` | M1–M7 | G6 + 滚轮 + 隔离 + S06（**全混**） |
| `@@ -28,6 +38,8 @@` | M8 | G6 |
| `@@ -41,11 +53,16 @@` | M9 + M10 | **G6 + S06** |
| `@@ -55,12 +72,47 @@` | M11 + M12 | **G6 + 隔离 + S06** |
| `@@ -73,10 +125,16 @@` | M13 | **G6 + 隔离** |
| `@@ -90,6 +148,27 @@` | M14 | S06 |
| `@@ -105,7 +184,19 @@` | M15 | 仅诊断 |
| `@@ -127,6 +218,21 @@` | M16 | 滚轮 |

**⇒ `MainActivity.kt` 在 `git add -p` 粒度下无法按族拆分**：8 个 hunk 里 4 个混装，其中 `-4,22` 一个 hunk 同时含 4 个族。

### 6.3 `MainActivity.kt` 的 S06 族最小手工拆分点

| 取用项 | 精确位置 | 依赖 |
|---|---|---|
| `ThemeResolver` / `BootMirror` import | 工作树 **28**、**32** | `SettingsStore.kt` 的 S1 |
| `applyBootWindowTheme()` **调用**（`super.onCreate` 之前） | 工作树 **60–65** | 该函数定义 + `BootMirror` + `ThemeResolver` |
| `applyBootWindowTheme()` **定义** | 工作树 **151–170** | 同上 + `R.style.Theme_CreationReadingAssistant_Dark`（§八已核实存在） |
| Compose 内 `darkTheme` 改用 `ThemeResolver` | 工作树 **112–115**；同时**删除** HEAD 59–62 的内联 `when` | 同上 |
| `AndroidManifest.xml` 的 `uiMode` | `AndroidManifest.xml:40` | 见 §八 |

**删除 HEAD 内联 `when` 的位置**：HEAD `MainActivity.kt:59–62` 四行 + 第 58 行的 `showOnboarding` 声明共 5 行，落在 `-U3` 的 `@@ -55,12 +72,47 @@` 里，与 G6 时长块、引导异步块同 hunk ⇒ **必须手工编辑**。

---

## 七、`OnboardingOverlay.kt` 的 `ONBOARDING_PREFS` 边界（`-U0` 实测 3 hunk）

| # | `-U0` hunk | 工作树行 | 内容 | 族 | 归属 |
|---|---|---|---|---|---|
| O1 | `@@ -69 +69,10 @@` | 69–78 | KDoc + `internal const val ONBOARDING_PREFS = "app_prefs"` + `private const val PREFS = ONBOARDING_PREFS`（替换 HEAD 的 `private const val PREFS = "app_prefs"`） | 启动隔离 | **无批次归属** |
| O2 | `@@ -70,0 +80 @@` | 80 | `+private const val KEY_CORD_HINT_SEEN = "cord_hint_seen_v1"` | 灯绳（EXCLUDED） | 无批次归属 |
| O3 | `@@ -78,0 +89,15 @@` | 89–103 | `+fun isCordHintSeen` / `+fun markCordHintSeen` | 灯绳（EXCLUDED） | 无批次归属 |

**`-U3` 合并结果：3 → 2 hunk**
- `@@ -66,8 +66,18 @@` = **O1 + O2 合并**（分隔它们的是工作树 **79** 行 `private const val KEY_ONBOARDED = "onboarded_v1"`，唯一一行未变更）。
- `@@ -76,6 +86,21 @@` = O3（独立）。

**⇒ 精确手工拆分点：工作树第 79 行。** 只取 `ONBOARDING_PREFS`（O1）时，必须在这一行处切断，**不得带上 `KEY_CORD_HINT_SEEN`**（否则夹带 EXCLUDED 的灯绳功能）。

**`ONBOARDING_PREFS` 本身自足可编译**：`isOnboardingCompleted`（工作树 82–83）与 `markOnboardingComplete`（85–87）在 HEAD 已存在且不引用 `KEY_CORD_HINT_SEEN`；HEAD 的 `PREFS` 私有常量被 `ONBOARDING_PREFS` 别名替换后，同文件全部 `getSharedPreferences(PREFS, …)` 调用点**无需改动**。

---

## 八、资源与清单接线（S06 启动主题的另外三个文件）

| 文件 | 状态 | hunk | 内容 | 矩阵 `group` |
|---|---|---|---|---|
| `android/app/src/main/res/values/colors.xml` | ` M` | 1（`@@ -1,0 +2,7 @@`） | `boot_window_background = #F6F2EA`、`boot_window_background_dark = #1A1917` + 注释 | **G3（S06 theme）** |
| `android/app/src/main/res/values/themes.xml` | ` M` | 2（`@@ -2 +2,8 @@`、`@@ -6,0 +14,9 @@`） | 注释重写；浅色主题加 `windowBackground`；新增 `Theme.CreationReadingAssistant.Dark` | **G3（S06 theme）** |
| `android/app/src/main/res/values-night/themes.xml` | `??` 新文件 | 整文件 | 系统深色时的 `Theme.CreationReadingAssistant` 变体 | **G3（S06 theme）** |
| `android/app/src/main/AndroidManifest.xml` | ` M` | 3 | `@@ -13 +13 @@` 注释（字典 + TTS）；`@@ -18,0 +19,3 @@` `TTS_SERVICE` query；`@@ -36,0 +40 @@` **`android:configChanges="…\|uiMode"`** | 矩阵 `HOLD`；180 CSV 挂 `B3+B4` |

**已核实的资源存在性**：`R.style.Theme_CreationReadingAssistant_Dark` **确有定义**（`values/themes.xml:18`），`MainActivity.applyBootWindowTheme()` 的引用可解析。`values-night/themes.xml` 只覆盖同名 `Theme.CreationReadingAssistant`，**不重复声明 `.Dark`**，符合[集成决策包](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md) §2.3 的 G3 范围。

**清单依赖**：`AndroidManifest.xml:40` 的 `uiMode` 属 S06（[决策包](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md) §2.3 逐字判定「划归 G3」）；同文件的 `TTS_SERVICE` query 判归 G4（批次 4）。**manifest 的三个 hunk 分属两族**，`git add -p` 下 `@@ -13 +13 @@` 与 `@@ -18,0 +19,3 @@` 是否与 `@@ -36,0 +40 @@` 合并需按实施时的上下文粒度再确认（本轮 `-U0` 为 3 个独立 hunk，`-U3` 下 `-13` 与 `-18` 相距 5 行、应保持独立）。

---

## 九、测试与 Hilt / Application 接线核查

### 9.1 测试覆盖

| 测试文件 | 状态 | 覆盖对象 | 与本候选的关系 |
|---|---|---|---|
| `app/src/test/.../data/settings/SettingsStoreBootMirrorTest.kt` | `??` 新文件（256 行，**13 个 `@Test`** 实测） | `BootMirror` 首帧值 / `hasThemeMode` 回填 / `ThemeResolver` 六场景 / `desiredNightMode` 映射 | **本候选必需**；实测 0 处引用 `ReaderSettings`/`BatteryTipStyle`/`customTip*`/`dayNight*`/`DurationUnit` ⇒ **与阅读器设置族、G6 完全解耦** |
| `app/src/test/.../data/settings/SettingsStoreTest.kt` | ` M`（`+26/-0`，2 条新用例） | `dayNightLightPaper` / `dayNightDarkPaper` 持久化与脏值拒绝 | **必须排除**：新用例引用 `ReaderDayNightToggle`（矩阵判 **EXCLUDED**） |
| `app/src/test/.../ui/util/TimeUtilsTest.kt` | `??` | 时长格式化 | G6，与本候选无关 |
| `app/src/test/.../data/settings/DurationPrefsTest.kt`、`DurationUnitTest.kt` | `??` | G6 时长 | G6，与本候选无关 |
| **`App.kt` 自身** | — | **无任何测试**（`git grep --untracked 'AppContextHolder\|writeFatal'` 在 `src/test` 与 `src/androidTest` 均 0 命中） | 缺口 |
| **`ONBOARDING_PREFS` / `isOnboardingCompleted`** | — | **无任何测试**（同上 grep 0 命中；`androidTest` 仅 `CraIsolatedTestApplication.kt:26` 的注释提及 `app_prefs`） | 缺口 |
| **`MainActivity`** | — | **无任何测试**：`app/src` 下不存在 `MainActivity*Test.kt`；`androidTest` 对 `MainActivity` 的 4 处命中**全部是注释**（`CraIsolatedTestApplication.kt:39`、`CraIsolatedTestRunner.kt:25`、`DurationPillGestureTest.kt:170`、`DurationUnitToggleEntryTest.kt:46/185`） | 缺口（S06 设备行为无自动化门禁） |

**测试依赖可用性**：`SettingsStoreBootMirrorTest.kt` 使用 `InMemorySharedPreferences`（`testutil/TestDoubles.kt:14`）与 `testDataStoreContext()`（同文件 `:134`），二者**在 HEAD 已存在**，且 `TestDoubles.kt` 工作树**无改动** ⇒ 该测试的编译闭包不额外引入新文件。

### 9.2 Hilt / Application 接线

| 检查项 | 实测结果 | 影响 |
|---|---|---|
| `App` 声明 | `AndroidManifest.xml:25` `android:name=".App"`；`App.kt:27` `@HiltAndroidApp` | 启动依赖图的宿主；instrumentation 下由 runner 替换（见下） |
| `BootMirror` / `ThemeResolver` 是否需 Hilt | **不需要**：两者是顶层 `object`，直接读 `getSharedPreferences`；`git grep --untracked` 在 `di/`、`data/local/` 下 **0 命中** | 这是 `MainActivity` 能在 **`super.onCreate` 之前**（Hilt 注入之前）读到主题的前提，符合 `BootMirror` KDoc 自述的设计意图 |
| `SettingsStore` 注入 | `@Singleton` + `@Inject constructor(@ApplicationContext, @ApplicationScope)`（HEAD 已如此）；`MainActivity.kt:55` `@Inject lateinit var settingsStore`（HEAD 已有） | BootMirror 族**不需要新增 Hilt 模块** |
| `DurationPrefs` 注入 | `MainActivity.kt:56` `@Inject lateinit var durationPrefs`（**新增**）；`DurationPrefs.kt` 为 `@Singleton @Inject constructor`（G6 新文件） | 属 G6，本候选排除 |
| `App.onCreate` 内的 `EntryPointAccessors.fromApplication` | 位于 `super.onCreate()`（构建 Hilt 图）**之后**的 `cra-startup` 线程内 | 线程启动时图已建好；三项在同一线程内串行，未引入新的并发装配 |
| instrumentation Application 替换 | `build.gradle.kts:32` `testInstrumentationRunner = "com.creationreadingassistant.testing.CraIsolatedTestRunner"`；`CraIsolatedTestApplication.kt`、`CraIsolatedTestRunner.kt` **已在 HEAD（`513891d`）跟踪**（`git ls-tree` 实测），工作树无改动 | 仪器测试下 `App.kt` 的预热 / 主题镜像 / `cra-startup` **都不执行**；⇒ **S06 启动主题与启动隔离在设备上无 instrumentation 证据** |

---

## 十、最小隔离候选清单（只含 S06 启动主题，不夹带其他功能）

> **定位**：这是「`App.kt` 的 S06 启动主题链路」的最小自洽可编译集合。**不含**诊断增强、后台线程搬迁、ONBOARDING_PREFS 预热、G6 时长、灯绳、滚轮。
> **前提**：下列每一项都标注了必须**手工编辑 hunk**（`git add -p` 不可用）。本轮**未执行任何拆分、未 stage**。

### 10.1 纳选清单

| # | 文件 | 取用范围（精确到 hunk / 行） | 理由 |
|---|---|---|---|
| C1 | `data/settings/SettingsStore.kt` | `-U0` 的 **S1、S2、S3、S4、S6、S7**（工作树 319–439、447–449、470–503、511–514、656–658、670）；**S5 只取工作树第 586 行** | `BootMirror` + `ThemeResolver` 定义体与全部读写点 |
| C2 | `MainActivity.kt` | `-U0` 的 **M7、M10、M12、M14**；`M6` 只取 `+import …BootMirror` 一行；`M11` 只取「删除 HEAD 59–62 内联 `when`」并保留 HEAD 58 行不动 | `applyBootWindowTheme()` 定义 + `super.onCreate` 前调用 + Compose 内 `darkTheme` 改用 `ThemeResolver` |
| C3 | `App.kt` | `-U0` 的 **A7**（工作树 126–130）；`A6` 只取工作树 **121–124 的主题镜像注释**，**保留** HEAD 91–105 三项内联接线 | `App.onCreate` 的启动主题收敛（仅显式 dark/light 下发系统） |
| C4 | `res/values/colors.xml` | 全部 1 个 hunk | 启动窗口底色 |
| C5 | `res/values/themes.xml` | 全部 2 个 hunk | 浅色窗口主题 + `Theme.CreationReadingAssistant.Dark` |
| C6 | `res/values-night/themes.xml` | 整文件（新） | 系统深色时的窗口主题 |
| C7 | `AndroidManifest.xml` | 仅 `@@ -36,0 +40 @@`（`uiMode` 一行） | 系统深浅切换时不重建 Activity，交给 Compose |
| C8 | `app/src/test/.../SettingsStoreBootMirrorTest.kt` | 整文件（新，13 测试） | 唯一守门测试；依赖全部在 HEAD |

### 10.2 C1 的两个必须手工处理的点（复述）

1. **S1 内**：`ThemeResolver`（工作树 342–359）与 `BootMirror`（361–439）可整体取；工作树 **334–341 的悬空重复 KDoc** 建议顺手删除（无行为影响）。若本候选要「最小」，理论上可只取 `BootMirror` 部分——但 C2 的 M12/M14 需要 `ThemeResolver`，故**两者都取**。
2. **S5**：只取工作树 **586** 行单行替换；**不得**整 hunk 取 `-U3` 的 `@@ -322,8 +573,17 @@`（会拖入 `customTip*` / `batteryTipStyle` 阅读器设置族）。

### 10.3 与集成决策包批次 3 的差异（必须回报集成者）

[集成决策包](../../decisions/2026-09/2026-09-29-stage-e-integration-decision-package.md) §2.3 把批次 3 的 G3 独立文件写成「`colors.xml`、`themes.xml`、`values-night/themes.xml`、`SettingsStoreBootMirrorTest.kt`」。**该清单不足以编译**，缺三项：

1. `SettingsStore.kt` 的 **S1–S4、S6、S7**（`BootMirror` / `ThemeResolver` 定义体）——`MainActivity` 与 `App.kt` 都引用；
2. `App.kt` 的 **A7**（+ A6 的注释部分）——`App.onCreate` 的启动主题收敛；
3. `AndroidManifest.xml` 的 `uiMode` hunk——决策包已在正文点名，但未进「独立文件」清单。

另需注意：`SettingsStore.kt` 在 376 行矩阵与 180 行 CSV 中**整体标为 HOLD**（理由为「§2.9 Reader 设置覆盖 29 hunks」）。**该 HOLD 判定的依据只覆盖了 22 个阅读器设置 hunk，未识别出同文件另有的 7 个启动主题 hunk** ⇒ 本报告建议把 `SettingsStore.kt` 的 HOLD 口径细化为「22 个阅读器设置 hunk 保持 HOLD；7 个启动主题 hunk 归 G3」。**这是建议，不是裁决。**

---

## 十一、排除项（明确不进本候选）

| 排除项 | 位置 | 排除理由 |
|---|---|---|
| **诊断增强 D** | `App.kt` A3、A4、A9、A10、A5 的 `getPackageInfo → appVersion` 部分；`MainActivity.kt` M15 | 376 行矩阵与 180 行 CSV 均无批次；180 行 CSV `reason` 明写「启动诊断（设备信息/heap/版本）、`appVersion` 缓存…**无任何候选批次覆盖**」 |
| **`ONBOARDING_PREFS` 预热 I** | `App.kt` A1、A5 的预热行；`MainActivity.kt` M3 的 `LaunchedEffect`、M11 的引导异步块、M13 的 `onboardingCompleted` 判定 | 同上无批次；[S04 隔离设计](../../2026-09-25-s04-instrumentation-startup-isolation-design.md) §3.2 只登记为「已证实」的既有事实，非批次归属 |
| **后台线程搬迁 T** | `App.kt` A2、A6 的删除部分、A8 | 同上无批次；且与 S06 在 `-U3` 同 hunk，取用需手工编辑（见 §十二） |
| **G6 时长单位** | `MainActivity.kt` M1、M4、M5、M6 的 `DurationPrefs`/`DurationUnit`、M8、M9、M11 的时长块、M13 的 `CompositionLocalProvider`；`DurationPrefs.kt`、`DurationDisplay.kt`、`TimeUtils.kt`、`DurationPrefsTest.kt`、`DurationUnitTest.kt` | 属 376 行矩阵 **G6**，决策包归批次 3 的 G6 半边；与本候选无编译依赖 |
| **灯绳 / 日夜纸色** | `OnboardingOverlay.kt` O2、O3；`SettingsStore.kt` 的 R3、R8、R14、R19 等引用 `ReaderDayNightToggle` 的 hunk；`SettingsStoreTest.kt` 的 2 条新用例；`ReaderDayNightToggle.kt`、`ReaderDayNightCord.kt`、`ReaderDayNightToggleTest.kt`、`ReaderInteractionLayer.kt` | `ReaderDayNightToggle` 等被 376 行矩阵判 **EXCLUDED**；`ReaderInteractionLayer.kt` 属 **G8/G8e** |
| **鼠标滚轮翻页** | `MainActivity.kt` M2、M16；`ReaderHardwareKeys.kt` 的 `dispatchScroll` | 新族，矩阵与 CSV 均无覆盖；与 S06 无依赖 |
| **TTS_SERVICE query** | `AndroidManifest.xml` `@@ -18,0 +19,3 @@` | 决策包 §2.3 判归 **G4（批次 4）** |
| **`SettingsStore.kt` 阅读器设置族 22 hunk** | `-U0` 的其余 22 个 hunk（`BatteryTipStyle`、`tapActionPreset`、`volumeKeyInvert`、`ttsSpeechRate`、`customTip*`、`dayNight*` 等） | 决策包 §2.9 明确「仍必须严格保持 HOLD」 |

---

## 十二、混合 hunk 的精确手工拆分点与**可能改变的启动行为**

> 本节只描述「若按某族单独取用，代码形态与启动行为会怎么变」，**不裁决是否应当这样做**。所有行为描述均来自源码静态阅读，**未经任何设备或构建验证**。

### 12.1 `App.kt` `-U3` 的 `@@ -88,26 +118,51 @@`（T + S06 混装）

- **拆分点**：HEAD `App.kt:91–105` 是三项内联接线（EPUB / Goal / SearchIndex）；HEAD `:106–109` 是包裹 SearchIndex 的 `runCatching { … }`。工作树把前者整体删掉（A6 的删除侧）、把后者的函数体换成 `BootMirror` 调用（A7）、再在文件后段新增 `cra-startup` 线程把三项放回去（A8）。三者在 `-U3` 下是**一个连续 hunk，中间无未变更行**。
- **只取 S06（C3）的精确编辑**：保留 HEAD `:91–109` **原样不动**，在其**之后**新增一个独立的 `runCatching { val mode = BootMirror.themeMode(this); if (mode != "system") BootMirror.applyApplicationNightMode(this, mode) }`。注意这与 diff 里 A7 的形态**不同**（A7 是替换而非新增），因此**不能靠挑选 hunk 得到，必须手工写**。
- **只取 T（线程搬迁）的精确编辑**：删除 HEAD `:91–105` 三项接线，把 A7 的删除侧（HEAD `:107–109` 三行）一并删除，保留 `runCatching { }` 空壳会报「空 lambda 无用」类告警——实际形态是**同时**需要 A6 的删除侧 + A8 的新增侧，即**必须整段重写** HEAD `:91–109`。
- **可能改变的启动行为（T）**：
  1. `EntryPointAccessors.fromApplication(...)` 会即时构建 Hilt 子图并连带构造 `AppDatabase` 单例；`DatabaseModule.provideDatabase` 内会调用 `DatabaseSafetyNet.snapshotIfUpgrading`（源码注释称升级那次要复制整库，本机实测可达 319MB）。**移到后台线程后，这段耗时不再阻塞 `onCreate` 返回**，但 `MainActivity` 可能在它完成前就开始渲染。
  2. 三项从「`onCreate` 返回前已发起」变为「`onCreate` 返回后由另一线程发起」⇒ **首帧前不再保证 WorkManager 任务已入队**。若任何 UI 在首帧读取「全文索引是否已建立」之类的状态，理论上存在**短暂的先后差异**（本轮未验证具体消费点）。
  3. `EpubSizeRepairTask.startOnce()` 与书架首次加载的先后关系由确定变为竞态；`startOnce` 的幂等守卫若依赖主线程串行，需另核（本轮未核）。
  4. 新增 `TAG_BOOT` 的三条 `onFailure` 日志：三项失败从「静默」变为「logcat 可见」，**不改变控制流**。

### 12.2 `App.kt` `-U3` 的 `@@ -51,6 +63,24 @@`（D + I 混装）

- **拆分点**：工作树 66–83。其中 67–76 是 `getPackageInfo → appVersion`（诊断），77–82 是注释 + `getSharedPreferences(ONBOARDING_PREFS)`（隔离），83 是 `}`。**二者之间无未变更行**。
- **只取 I 的精确编辑**：删除工作树 67–76 的 `runCatching { … }` 块，保留 77–82。
- **可能改变的启动行为（I）**：
  1. `attachBaseContext` 是进程**最早的代码执行点**（早于窗口创建），提前 `getSharedPreferences("app_prefs")` 会把首次 XML 解析与目录创建的 ~250ms 从「Compose 组合期（首帧前）」挪到「窗口未显示时」。**但注意：这仍是主线程 I/O**，只是发生得更早——源码注释自称是为了摘出首帧关键路径，本轮**无法验证它是否真的缩短了首帧**。
  2. **新增落盘副作用**：`shared_prefs/app_prefs.xml` 会在**每次冷启动**被创建/读取（首次创建即写文件）。[S04 隔离设计](../../2026-09-25-s04-instrumentation-startup-isolation-design.md) §3.2 已把「**会创建** `shared_prefs/app_prefs.xml`」列为**已证实**的启动副作用。
  3. 与 `MainActivity` 的引导异步读（M11/M13）是**一对**：只取预热而不取异步读，则 `isOnboardingCompleted` 仍在组合期同步读（但此时是内存命中）；只取异步读而不取预热，则首次读仍落在 IO 线程（不阻塞首帧，但多一次磁盘读）。**两者行为组合不同，本轮未验证任一组合。**

### 12.3 `App.kt` `-U3` 的 `@@ -39,9 +43,17 @@`（纯 D）

- 时间戳格式变更 + `writeFatal` 增写两行。**只影响 `cacheDir/cra_fatal.log` 的内容**，不改变启动控制流。`Runtime.getRuntime()` 调用开销可忽略；`AppContextHolder.appVersion` 为 `null` 时输出 `"unknown"`。

### 12.4 `SettingsStore.kt` `-U3` 的 `@@ -322,8 +573,17 @@`（阅读器设置族 + S5 混装）

- **拆分点**：工作树 585（未变更的 `)`）与 586（`}.stateIn(scope, SharingStarted.WhileSubscribed(5000), bootReader)`）。**唯一一行未变更行分隔。**
- **只取 S5 的精确编辑**：把工作树 586 行的 `bootReader` 写回；**不**取 576–584 的 `customTip*` / `batteryTipStyle`。
- **可能改变的启动行为（S5）**：`reader` StateFlow 的首帧初值由 `ReaderSettings()`（`background = "follow"`）变为 `ReaderSettings(background = BootMirror.readerBackground(appContext))`。若镜像缺失则仍为 `"follow"`（与旧行为一致）；若镜像存在则冷启动首帧即为用户纸色。**这是可见行为变化**（消除「先闪默认纸色再纠正」），但**只在镜像存在时发生**。

### 12.5 `MainActivity.kt` `-U3` 的 `@@ -55,12 +72,47 @@`（G6 + 隔离 + S06 混装）

- **拆分点**：HEAD 58（`showOnboarding` 同步读）、HEAD 59–62（内联 `darkTheme when`）、HEAD 63（`}`，未变更）构成删除侧；工作树 75–110 构成新增侧（时长块 + 引导异步块）。**删除侧与新增侧之间无未变更行。**
- **只取 S06 的精确编辑**：保留 HEAD 58 不动；删除 HEAD 59–62 四行；在工作树 112–115 的位置插入 `ThemeResolver` 版 `darkTheme`。**注意 `-U0` 的 M12（`@@ -63,0 +112,4 @@`）本身是纯新增 hunk**，因此「新增 `ThemeResolver` 版 `darkTheme`」可以独立取用；「删除内联 `when`」则必须手工编辑 M11。
- **可能改变的启动行为（S06）**：
  1. `applyBootWindowTheme()` 在 `super.onCreate` **之前**调用 `setTheme(...)`，把 manifest 的浅色父主题替换为 `.Dark`（当镜像为 `"dark"`，或镜像为 `"system"` 且当前 `uiMode` 为深色）。**这是首帧可见的颜色变化**——正是设计目标（消除「夜间模式进应用先闪日间」）。
  2. 该调用读取 `resources.configuration.uiMode`，**发生在 `super.onCreate` 之前**，属早期资源访问；本轮未验证其在所有 ROM 上的可用性。
  3. 与 `AndroidManifest.xml` 的 `uiMode` `configChanges` 是**一对**：若只取 `setTheme` 而不加 `uiMode`，系统深浅切换时会重建 Activity（原行为）；若只加 `uiMode` 而不取 `setTheme`，Compose 侧的 `isSystemInDarkTheme()` 会在不重建的情况下重组，但启动窗口仍走 manifest 浅色主题。
  4. Compose 内 `darkTheme` 由内联 `when` 改为 `ThemeResolver.resolveAppDarkTheme`：**语义等价**（`"dark"→true`、`"light"→false`、其他→`isSystemInDarkTheme()`），是纯重构，不改变行为。

### 12.6 `MainActivity.kt` `-U3` 的 `@@ -73,10 +125,16 @@`（G6 + 隔离混装）

- **拆分点**：工作树 128–131 是 `CompositionLocalProvider(...) {` 两行（G6）与 `Box(...)`；工作树 135–137 是 `if (onboardingCompleted == false)` 与 `OnboardingOverlay`。**删除 G6 必须同时删除工作树 139 多出来的一层闭合大括号**（HEAD 只有一层 `Box` + `AppTheme` 闭合）。
- **可能改变的启动行为（隔离）**：HEAD 是「组合期同步读 → `showOnboarding` 立即有确定布尔值」；工作树是「`null`（未知）→ IO 线程读完 → 才可能渲染引导层」。⇒ **老用户冷启动时，引导层从「立即判定为不显示」变为「先不渲染、读到 false 才渲染」**，源码注释称后者避免了「闪一下引导层」。**该差异只在首帧后极短时间内可见，本轮无设备证据。**

### 12.7 `OnboardingOverlay.kt` `-U3` 的 `@@ -66,8 +66,18 @@`（隔离 + 灯绳混装）

- **拆分点**：工作树 **79** 行（`private const val KEY_ONBOARDED = "onboarded_v1"`，唯一未变更行）。取 `ONBOARDING_PREFS`（69–78）时不取第 80 行。
- **可能改变的启动行为**：无。`ONBOARDING_PREFS` 只是把字面量提为 `internal` 顶层常量并保留 `private const val PREFS = ONBOARDING_PREFS` 别名，**同文件所有读取路径行为不变**；它唯一的作用是让 `App.kt` 能引用同一文件名。

---

## 十三、仍无批次归属的改动清单（汇总）

| 族 | 位置 | 376 行矩阵 | 180 行 CSV |
|---|---|---|---|
| 启动诊断（设备/内存/版本 + `appVersion` + 日志轮转） | `App.kt` A3/A4/A5(前)/A9/A10；`MainActivity.kt` M15 | `App.kt` HOLD；`MainActivity.kt` HOLD | `App.kt` 保留 HOLD（reason 明写「无任何候选批次覆盖」）；`MainActivity.kt` 挂 B3 |
| `ONBOARDING_PREFS` 预热 + 引导异步读 | `App.kt` A1/A5(后)；`OnboardingOverlay.kt` O1；`MainActivity.kt` M3(LaunchedEffect)/M11(引导块)/M13(判定) | HOLD | 保留 HOLD |
| `cra-startup` 后台线程搬迁 | `App.kt` A2/A6(删)/A8 | HOLD | 保留 HOLD |
| 鼠标滚轮翻页 | `MainActivity.kt` M2/M16；`ReaderHardwareKeys.dispatchScroll` | 无该行（`ReaderHardwareKeys.kt` 不在矩阵） | 不在 180 行 |
| S06 启动主题 | `App.kt` A6(注释)/A7；`MainActivity.kt` M7/M10/M12/M14；`SettingsStore.kt` S1–S7；3 个 res 文件；manifest `uiMode` | res 三件 = **G3**；其余 = HOLD | `MainActivity.kt` 挂 B3；`SettingsStoreBootMirrorTest.kt` 挂 B3；`App.kt` 保留 HOLD |

**⇒ 结论：`App.kt` 一行改动都没有「现成批次号」；只有 S06 主题这半边被集成决策包事后挂进了批次 3，且该批次清单不完整。**

---

## 十四、尚缺的验证（本轮零构建）

| 验证项 | 状态 | 说明 |
|---|---|---|
| `App.kt` 最小候选的隔离编译 | **未做** | 本轮零构建；且 §十 的候选**必须先手工编辑 hunk** 才能成立 |
| `SettingsStore.kt` BootMirror 族 7 hunk 的隔离编译 | **未做** | 同上 |
| `MainActivity.kt` S06 族 4+2 hunk 的隔离编译 | **未做** | `MainActivity` 无任何 JVM/仪器测试，只有编译门禁可作静态证据 |
| `SettingsStoreBootMirrorTest` 定向 JVM | **未做** | 该测试在 [S06 报告](../../legacy-stages/2026-09/2026-09-28-s06-follow-system-theme-result.md) §6.1 有 **34/34**（含 `SettingsStoreTest` 13 + `SettingsViewModelTest` 8）的 E 盘 XML 记录，但那是**未拆分的工作树全量**，**不能外推到拆分后的子集** |
| `compileDebugAndroidTestKotlin` | **未做** | §十 候选不含 androidTest 文件 |
| S06 主题的设备首帧行为 | **未做** | [S06 报告](../../legacy-stages/2026-09/2026-09-28-s06-follow-system-theme-result.md) 顶部 Codex 复核已明确：`MODE_NIGHT_AUTO` 清除路径「**仅有 JVM 映射断言，MuMu 实际切换与冷启动首帧未验证；厂商 ROM 更未验证**」 |
| `App.kt` 诊断 / 预热 / 线程搬迁的设备行为 | **未做** | 无任何测试覆盖；本轮未连设备 |

**外推禁止**：工作树全量 JVM 3208/3208 与 D 阶段 build 成功**不能**证明上述任一拆分后子集可编译或行为等价。本轮已用符号存在性实测证明 `App.kt` 单独取用必然失败。

---

## 十五、建议交给集成者/总指挥的三项（只提问题，不裁决）

1. **批次 3 的 G3 清单需要补齐**：至少补 `SettingsStore.kt` 的 7 个启动主题 hunk、`App.kt` 的 A7（+A6 注释）、`AndroidManifest.xml` 的 `uiMode`。否则批次 3 无法编译。
2. **`SettingsStore.kt` 的 HOLD 口径需要细化**：现行「29 hunks 全 HOLD」的依据只覆盖 22 个阅读器设置 hunk，未识别 7 个启动主题 hunk。
3. **180 行 CSV 的两处映射需要复核**：`MainActivity.kt`（H006）与 `SettingsStoreBootMirrorTest.kt`（H144）被挂到 B3（时长单位），但两者实测主要承载 S06 启动主题；`SettingsStoreBootMirrorTest.kt` 与 G6 的 4 个文件**零耦合**（§9.1 实测）。

---

## 十六、纪律声明

- 仓库内**唯一写入 = 本文件**（`docs/coordination/reviews/2026-09/2026-09-29-stage-e-app-startup-boundary-review.md`）。未改 `App.kt`、`MainActivity.kt`、`SettingsStore.kt`、`OnboardingOverlay.kt`、任何 res / manifest / 测试 / CSV / 公共配置 / 其他窗口报告。
- **未** stage / commit / push / reset / checkout / stash；**未**切换项目目录；**未**触碰原仓库 `D:\develop\Code\Codex\creation-reading-assistant`、MuMu / ADB、正式书库或用户资产。
- **未运行任何 Gradle 命令**、**未构建**、**未做设备操作**。全部「编译期依赖 / 编译失败 / hunk 边界」表述均来自 `git grep HEAD` 与 `git diff -U0/-U3` 的实测输出。
- **不宣称任何候选已可编译、已通过测试或已在设备上验证**。§十二 的行为描述全部为源码静态阅读，标注为「可能改变」，非实测结论。
- 本报告**不替其他窗口裁决**：`ShelfImporter.kt` / `ShelfImporterTest.kt` / `ShelfViewModelTest.kt` 的 B1 部分、M-BUG-03 联合候选、`SyncRepository.kt` / `WebDavBackup.kt` 的族拆分均不属本窗口，未触碰、未取用。
- 全文只用中性标识，未记录任何真实书名。
