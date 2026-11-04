# 阶段 E：G6 设置页隔离（AppearanceSubPage / ProfileHomeScreen）（2026-09-30）

> **窗口**：G6 时长单位 —— 设置页（我的/外观）两文件隔离。
> **范围**：仅 `ui/screen/profile/AppearanceSubPage.kt` 与 `ui/screen/profile/ProfileHomeScreen.kt`。**不含**统计页（`CreationSection` / `HeatmapSection` / `SummaryGroup` / `YearBillHeroCard`）与 S14。
> **工作仓库（只读）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`，分支 `codex/recover-worktree-20260920`。
> **HEAD 已核对** = `513891d6a66a9b78e072ba759d34ed580d377ecf`；`origin/main` = `5aed5939600e491843e3c44453921bd6273a233c`；**暂存区空**。
> **隔离候选**：`E:\creation-reading-assistant\stage-e-g6-settings-20260930\android`（由 `stage-e-g6-consumers3-20260930\android` 复制而来，排除 `build` / `.gradle` / `.kotlin`；**原候选未修改**）。
> **输入**：[AGENTS.md](../../../../AGENTS.md)、[当前交接](../../../handoff/current.md)、[G6 集成边界更正](../../decisions/2026-09/2026-09-30-stage-e-g6-scope-correction.md)、[G6 精确候选阶段记录](2026-09-30-stage-e-g6-partial-isolation.md)。
> **纪律**：共享恢复仓库内**唯一写入 = 本文件**。未改共享产品源码 / 测试 / 配置 / 其他窗口报告 / handoff；**未** stage / commit / push / reset / checkout；**未**触碰原仓库、正式包、正式书库或实体手机。

---

## 一、结论摘要

| 项 | 结果 |
|---|---|
| 候选构造 | 由 `consumers3` 复制**仅源码**（12.2 MB，无 `build` / `.gradle` / `.kotlin`）；原候选 SHA 前后一致 |
| **所属模块编译** | `:app:compileDebugKotlin` + `:app:compileDebugUnitTestKotlin` → **EXIT 0**，`BUILD SUCCESSFUL in 6m 32s` |
| **定向 JVM** | 4 个 G6 测试类 → **EXIT 0**，**25 tests / 0 failures / 0 errors / 0 skipped**（与 `consumers3` 基线 25/25 一致，**无回归**） |
| 纳入的 hunk | `AppearanceSubPage.kt` **3 处**；`ProfileHomeScreen.kt` **8 处**（逐 hunk 见 §三） |
| 排除的交叠改动 | 设置搜索（`SettingsAnchor` / `rememberSettingsPageListState` / `SettingsSearchIndex` / `ProfileTopBarSearchField`）、外观重设计（`ReaderPaperGrid` / `ReaderPaperCapsuleCard` / `CompactSettingsGroup` / `CompactSettingRow` / `CompactItemDivider` / 菜单组重命名 / 快捷磁贴移除）——**全部未纳入**（`grep` 实测均为 0） |
| 验收 1「默认 HOURS」 | **成立**（源码级）：`DurationPrefs.unit` 初值 `DurationUnit.HOURS`、`DurationUnit.fromStorage(null) → HOURS` |
| 验收 2「显式切换 DAYS」 | **成立**（源码级约束）：`AppearanceSubPage` 开关 `checked = durationUnit == DurationUnit.DAYS`，`onCheckedChange = { toggleDurationUnit() }` |
| 验收 3「持久化后两页同步」 | **成立**（源码级）：`MainActivity` 在 `AppNavigation()` 之上用 `LocalDurationUnit provides durationPrefs.unit` 单点提供，两页同源 |
| 验收 4「长按不吞点击 / 不重复无障碍」 | **源码级结构核查通过**，但**付出了交互调整代价**（F3-B，见 §五.4）；**手势行为本身未在设备上验证** |
| 未覆盖 | Compose UI / 手势设备验收、与 S06 联合态、其余 4 个 G6 生产文件与 1 个测试文件（不属本窗口） |

**一句话结论**：设置页两文件的 G6 时长单位**显示**与**切换入口**已在 `consumers3` 基线之上精确落地并通过编译与 25/25 定向 JVM；但时长文本的「长按」入口只有在**统计行不再是可点卡片的子节点**时才安全，因此本候选把「查看阅读档案」的点击从整卡收窄到**身份行**（F3-B）——这是本次唯一超出纯显示语义的改动，已在 §五.4 逐条说明并留待设备验收。

---

## 二、基线与隔离工作区

### 2.1 基线核对

| 项 | 值 |
|---|---|
| 恢复仓库 HEAD | `513891d6a66a9b78e072ba759d34ed580d377ecf` |
| 暂存区 | 空 |
| 候选来源 | `E:\creation-reading-assistant\stage-e-g6-consumers3-20260930\android`（G6 第 4 层：16/22 生产文件 + 4/5 测试文件） |
| 来源候选两文件状态 | 与 **HEAD 完全一致**（`git hash-object` 比对：`AppearanceSubPage.kt = f710a655c0…`、`ProfileHomeScreen.kt = 8fdf1dbbee…`）⇒ 本次是**在基线之上新增**，不是覆盖 |

### 2.2 隔离环境（全部在 E 盘）

| 项 | 值 |
|---|---|
| 候选源码 | `E:\creation-reading-assistant\stage-e-g6-settings-20260930\android`（12.2 MB） |
| Gradle 缓存（本窗口独占） | `E:\creation-reading-assistant\stage-e-g6-settings-20260930\_cache\gradle-home`（805.9 MB：`wrapper/dists/gradle-8.9-bin` 来自 `gradle-home-g0`，`caches/modules-2` 依赖缓存 504 MB 从 `stage-e-s06-boot-isolated-20260929\gradle-home` 复制） |
| 构建产物 | `…\android\app\build`、`…\android\.gradle`（均在候选内、在 E 盘） |
| 日志 / XML | `…\_evidence\compile-debug.log`、`…\_evidence\test-g6.log`、`…\_evidence\test-xml\` |
| JAVA_HOME | `D:\develop\Java\jdk-17.0.14` |
| 构建参数 | `--offline --max-workers=1`（与 G6 前四层一致，不在 D/C 新建构建目录） |
| 原候选完整性 | `consumers3` 两文件 SHA 与复制前一致（`948fe081cb…` / `4ef43fb383…`），**未被修改** |

---

## 三、逐 hunk 纳入 / 排除

hunk 编号与行号均取自共享工作树 `git diff -U0 HEAD -- <文件>`（`-U0`：每个 hunk 即一个极大连续变更块）。

### 3.1 `AppearanceSubPage.kt`（工作树共 19 个 `-U0` hunk；纳入 3）

| # | new 行 | 内容 | 归属 | 处置 |
|---|---|---|---|---|
| 1 | 27 | `import AutoStories` | 外观重设计（阅读纸张） | **排除** |
| 2 | 33–34 | `import ReaderPaperOptions` / `paperPalette` | 外观重设计 | **排除** |
| **3** | **56–58** | `import DurationUnit` / `LocalDurationUnit` / `LocalDurationUnitToggle` | **G6** | **纳入（整块）** |
| 4 | 74–79 | 74–75 `rememberSettingsPageListState()`（设置搜索）+ **76–79 G6 两 val** | **MIXED** | **只取 76–79**（G6），排除 74–75 |
| 5 | 91 | `listState = settingsListState` | 设置搜索 | **排除** |
| 6 | 97 | 作用域提示文案精简 | 外观重设计 | **排除** |
| 7 | 104–114 | `SettingsAnchor("appearance_mode_section")` 包裹 + 重缩进 | 设置搜索 | **排除** |
| 8–9 | 116–124 | 重缩进 + `SectionDivider()` 缩进 | 外观重设计 | **排除** |
| 10 | 126–135 | `SettingsAnchor("appearance_amoled")` + 副标题精简 | 设置搜索 + 文案 | **排除** |
| 11 | 137 | 重缩进 | 外观重设计 | **排除** |
| 12 | 139–160 | 139–148 `SettingsAnchor("appearance_paper_texture")` 包裹（外观重设计）+ **150–159 G6 时长开关** | **MIXED** | **只取 150–159**（G6 的 `SectionDivider()` + `SettingSwitchRow`），**去掉 `SettingsAnchor` 包裹** |
| 13–18 | 167–261 | `SettingsAnchor("appearance_palette_section")` / `appearance_wallpaper_color` 包裹、重缩进、调色板文案、新增「阅读纸张」分节（`ReaderPaperGrid`） | 设置搜索 + 外观重设计 | **排除** |
| 19 | 557–727 | 新组件 `ReaderPaperGrid` / `ReaderPaperCapsuleCard`（+171 行） | 外观重设计 | **排除** |

### 3.2 `ProfileHomeScreen.kt`（工作树共 46 个 `-U0` hunk；纳入 8 处改动，落在 4 个 hunk 的语义上）

| # | new 行 | 内容 | 归属 | 处置 |
|---|---|---|---|---|
| 1–14 | 11–73 | 设置搜索所需 import（`BasicTextField` / `FocusRequester` / `Search` / `Close` / `ArrowBack` …）、`Spacer`/`CircleShape`/`TextOverflow` 增删、`Compact*` 组件 import、`animateEnter` import | 设置搜索 + 重设计 | **排除** |
| **15** | **77–79** | `import LocalDurationUnit` / `durationUnitToggle` / `formatDuration` | **G6** | **纳入（整块）** |
| 16–17 | 81 / 87–90 | `@OptIn(ExperimentalLayoutApi::class)`、`searchActive` / `searchQuery` / `onQueryChange` / `onCloseSearch` 参数 | 设置搜索 | **排除** |
| 18 | 94–100 | 94 `SettingsSearchIndex.search(...)`（设置搜索）+ **96–100 G6：`durationUnit` / `durationText`** | **MIXED** | **只取 G6 语义**：`val durationUnit = LocalDurationUnit.current`（`:77`）；`durationText`（String 形态）随 `ArchiveStat` 重设计存在，本候选保留基线的 **count-up `HomeStat`**，故显示改由 `formatDuration(v * 60_000L, durationUnit)` 在 format 闭包内提供 |
| 19–23 | 106–188 | `searchActive` 分支、搜索结果容器、快捷热搜胶囊、`CompactSettingsGroup` 档案卡重写 | 设置搜索 + 重设计 | **排除** |
| 24 | 191–201 | `CompactItemDivider()` + `ArchiveStat` 三格（含 **`valueModifier = Modifier.durationUnitToggle()`**） | **MIXED**（重设计 + G6） | **只取 G6 语义**：时长按全局单位格式化 + 长按切换；落到基线的 `HomeStat`（新增 `valueModifier` 参数），排除 `CompactItemDivider` / `ArchiveStat` 本体 |
| 25–33 | 203–288 | 快捷磁贴整段移除、菜单组重命名、`MenuItem(desc = …)` 签名变化、`HomeStat` 删除 | 重设计 | **排除** |
| 34–40 | 291–308 | `ArchiveStat` 取代 `QuickTile`（含 `valueModifier: Modifier = Modifier` 与 `Text(modifier = valueModifier)`） | **MIXED**（重设计 + G6） | **只取 `valueModifier` 语义**：加到基线 `HomeStat` 上，排除 `ArchiveStat` 本体 |
| 41–46 | 315–436 | `MenuGroup` → `CompactSettingsGroup`、`MenuItem` → `CompactSettingRow`、分割线换 `CompactItemDivider`、新增 `ProfileTopBarSearchField`（+75 行） | 设置搜索 + 重设计 | **排除** |

**泄漏核查（`grep` 实测，两文件均为 0）**：`SettingsAnchor`、`rememberSettingsPageListState`、`SettingsSearchIndex`、`SettingsSearchResults`、`CompactSettingsGroup`、`CompactSettingRow`、`CompactItemDivider`、`ProfileTopBarSearchField`、`ReaderPaperGrid`、`ReaderPaperCapsuleCard`、`ArchiveStat`、`readerPaperOptions`。

---

## 四、两处文件的实际改动（逐块）

### 4.1 `AppearanceSubPage.kt`（相对 `consumers3` 基线 3 个 hunk）

```diff
@@ -52,2 +52,5 @@ import …ui.components.SectionDivider
+import com.creationreadingassistant.data.settings.DurationUnit
+import com.creationreadingassistant.ui.util.LocalDurationUnit
+import com.creationreadingassistant.ui.util.LocalDurationUnitToggle
@@ -75,2 +78,6 @@ internal fun AppearanceSubPage(
+    // 时长显示单位：与「在任意时长文本上长按」共用同一全局状态源（DurationPrefs）。
+    // 这里提供显式开关，是因为长按属于隐藏手势，用户不会自己发现。
+    val durationUnit = LocalDurationUnit.current
+    val toggleDurationUnit = LocalDurationUnitToggle.current
@@ -130,2 +137,11 @@ internal fun AppearanceSubPage(     ← 「外观模式」卡内、纸张质感开关之后
+
+                    SectionDivider()
+
+                    SettingSwitchRow(
+                        title = "时长按「天」显示",
+                        subtitle = "关闭后按「小时」显示；长按时长文本可切换",
+                        checked = durationUnit == DurationUnit.DAYS,
+                        onCheckedChange = { toggleDurationUnit() },
+                    )
```

### 4.2 `ProfileHomeScreen.kt`（相对 `consumers3` 基线 8 个 hunk）

```diff
@@ -60,2 +60,5 @@
+import com.creationreadingassistant.ui.util.LocalDurationUnit
+import com.creationreadingassistant.ui.util.durationUnitToggle
+import com.creationreadingassistant.ui.util.formatDuration
@@ -70,2 +73,7 @@
+    // 阅读时长按当前单位（天/小时/分钟）呈现，供顶部档案卡的统计行使用。
+    // S15-F1/F2：改用公共 formatter，不再本地重写 when 分支（本地版缺 ms<=0 保护，
+    // 且与公共策略分叉）；长按能力由 HomeStat 的 valueModifier 提供。
+    val durationUnit = LocalDurationUnit.current
@@ -77,6 +85,13 @@   ← F3-B：点击从整卡收窄到身份行
-            SectionCard(
-                onClick = { onAction(ProfileAction.OpenSubPage(ProfileSubPage.READING)) },
-            ) {
-                Row(verticalAlignment = Alignment.CenterVertically) {
+            // F3-B（G6 时长单位）：点击归「身份行」所有，统计行因此成为**无手势祖先的安全节点**。…
+            SectionCard {
+                Row(
+                    modifier = Modifier
+                        .fillMaxWidth()
+                        .clickable { onAction(ProfileAction.OpenSubPage(ProfileSubPage.READING)) },
+                    verticalAlignment = Alignment.CenterVertically,
+                ) {
@@ -135,11 +150,10 @@   ← 时长统计：跟随全局单位 + 长按入口
-                        { v ->
-                            when {
-                                totalMinutes >= 1440 -> "${v / 1440} 天 ${(v % 1440) / 60} 小时"
-                                totalMinutes >= 60 -> "${v / 60} 小时 ${v % 60} 分钟"
-                                else -> "$v 分钟"
-                            }
-                        },
+                        { v -> formatDuration(v * 60_000L, durationUnit) },
+                        valueModifier = Modifier.durationUnitToggle(),
@@ -291,2 +305,9 @@ private fun HomeStat(
+    /** 时长行用它挂长按切换；「读完 / 灵感」两格不传，保持纯文本。… */
+    valueModifier: Modifier = Modifier,
@@ -311,2 +332,3 @@ private fun HomeStat(     ← 只挂在「可见值」Text 上
+                modifier = valueModifier,
```

---

## 五、验收项逐条核查

> 说明：本次**未操作设备**，Compose 手势与读屏行为**无法在 JVM 上执行**。以下 4 条中，1–3 为**源码级成立**（含已有 JVM 用例覆盖的格式化/默认值契约），4 为**源码结构核查**，其运行态留待设备验收（§八）。

### 5.1 验收 1 —— 默认 HOURS ✅（源码级成立）

- `data/settings/DurationPrefs.kt`：`DurationUnit.fromStorage(raw)` 对未知值一律回落 `HOURS`（`:43-46`）；`DurationPrefs.unit` 的 `stateIn(..., DurationUnit.HOURS)`（`:68-70`）。
- `ui/util/DurationDisplay.kt`：`LocalDurationUnit = staticCompositionLocalOf { DurationUnit.HOURS }`（`:41`）——未提供时也回落到 HOURS。
- 既有 JVM 覆盖：`DurationPrefsTest`（8）+ `DurationUnitTest`（2）共 10 条，本次实跑 **0 失败**。

### 5.2 验收 2 —— 显式切换 DAYS ✅（源码级成立）

- 入口在**我的 → 外观**：「外观模式」卡内、纸张质感开关之后新增 `SettingSwitchRow("时长按「天」显示")`。
- `checked = durationUnit == DurationUnit.DAYS`：开关状态与全局单位**同源**，不是本地镜像。
- `onCheckedChange = { toggleDurationUnit() }`：直连 `LocalDurationUnitToggle`，最终写 `DurationPrefs.toggle()`（`:83-86`，HOURS ⇄ DAYS）。两个状态下点按都会翻转，符合二态开关语义。
- 这是**可发现**的入口（源码注释自述：长按属隐藏手势，故在设置里给显式开关）。

### 5.3 验收 3 —— 持久化后两页显示同步 ✅（源码级成立）

- 单点提供：`MainActivity.kt` 在 `AppNavigation()` 之上
  ```kotlin
  CompositionLocalProvider(
      LocalDurationUnit provides durationUnit,                 // ← durationPrefs.unit.collectAsStateWithLifecycle()
      LocalDurationUnitToggle provides toggleDurationUnit,      // ← 写 DurationPrefs（DataStore）
  ) { Box(Modifier.fillMaxSize()) { AppNavigation(); … } }
  ```
  （`MainActivity.kt:113-123`）⇒ **外观子页与我的首页都在同一 provider 子树内**。
- `DurationPrefs` 走 DataStore（`preferencesDataStore("duration_prefs")`，键 `duration_unit`）⇒ 切换即落盘；重启后 `fromStorage` 读回 `days`。
- 两页各自读取同一 `LocalDurationUnit.current`：外观页用于开关 `checked`，我的首页用于 `formatDuration(…, durationUnit)`。**无第二份状态、无本地缓存**，因此不存在两页不同步的路径。
- 既有 JVM 覆盖：`DurationPrefsTest` 含 set/toggle/落盘往返；本次实跑 **0 失败**。

### 5.4 验收 4 —— 长按入口不吞点击 / 不制造重复无障碍 ⚠️（源码结构核查通过，**代价已登记**）

**核查依据**：`DurationDisplay.kt` 对 `durationUnitToggle()` 写明了**适用范围边界**（`:61-86`）：

> 本 Modifier 只适用于**没有父级点击 / 长按冲突的安全节点**……`combinedClickable` 挂在子节点上时，手势识别器会参与命中竞争，空 `onClick` 仍可能截获本该交给父级的点击；`indication = null` 只关掉 ripple，不改变命中行为。
> **不得在父子两层重复挂**，两层各自消费手势会让父级主交互时灵时不灵，且 TalkBack 会读到两个同名操作。

**基线下的冲突**：`consumers3` 基线里「阅读时长」统计位于 `SectionCard(onClick = { 打开我的阅读 })` **内部**（整卡可点）⇒ 若直接把 `durationUnitToggle()` 挂到该统计文本上，**点击时长文本会被子节点的空 `onClick` 截获**，用户点它不再打开「我的阅读」，且同一张卡会出现「卡片点击 + 长按切换」两个操作。

**本候选的处理（F3-B）**：把卡片点击**收窄到身份行**——

| 检查点 | 结果 |
|---|---|
| 时长统计是否有可点祖先 | **无**。`SectionCard {` 不带 `onClick`（`SharedComponents.kt:116` 判定：`onClick == null` 时用非可点 `Surface`）；统计行 `Row` 与身份行 `Row` 是**兄弟**，统计行无 `clickable` |
| 时长格自身是否可点 | **否**。`HomeStat` 的 `onClick` 未传 ⇒ `modifier = if (onClick != null) … else Modifier`（`ProfileHomeScreen.kt:317`） |
| 长按入口数量 | `ProfileHomeScreen.kt` 中 `durationUnitToggle()` **恰好 1 处**（`:158` 的 `valueModifier`）；`AppearanceSubPage.kt` **0 处**（只用显式开关）⇒ 无重复入口 |
| 同一语义组件内是否重复挂 | 否。只挂在**可见值** `Text`（`:332`）；同 `Box` 内的隐形占位 `Text` **不挂**，故读屏只会读到一个「切换时长显示单位」操作 |
| 「查看阅读档案」点击是否保留 | **保留**，移到身份行 `Row`（`:94`） |
| 「灵感数量」既有入口是否保留 | **保留**（`HomeStat(onClick = { OpenInspirations })` 未改） |

**本次新增/变化的交互（必须让集成者知道）**：

- **变化**：点击**统计行区域**（时长 / 读完两格及其间隙）**不再**打开「我的阅读」；点击**身份行**（图标、标题、副标题、右箭头）仍打开。此前整卡任意处可点。
- **原因**：这是让长按入口不吞点击的**必要前提**（与工作树 F3-B 的取向一致：文字类接缝由代表容器统一拥有手势）。
- **未采纳的替代方案**：保留整卡可点，同时让时长格的长按 `onClick` **转发**卡片动作。这需要改 `ui/util/DurationDisplay.kt` 的 `durationUnitToggle()`（空 `onClick` 是硬编码）——**该文件不在本窗口所有权内**，见 §八 seam-2。
- **不得据此宣称手势已通过**：源码结构只能证明「没有可点祖先、没有重复入口」，**不能**证明命中竞争与读屏朗读在真实设备上的表现（§八）。

---

## 六、构建与测试记录

### 6.1 实际命令（工作目录 `E:\creation-reading-assistant\stage-e-g6-settings-20260930\android`）

```powershell
$env:JAVA_HOME='D:\develop\Java\jdk-17.0.14'
$env:GRADLE_USER_HOME='E:\creation-reading-assistant\stage-e-g6-settings-20260930\_cache\gradle-home'

# 编译
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --offline --max-workers=1 --console=plain

# 定向 JVM（4 个 G6 测试类）
.\gradlew.bat :app:testDebugUnitTest --offline --max-workers=1 --console=plain `
  --tests "com.creationreadingassistant.data.settings.DurationPrefsTest" `
  --tests "com.creationreadingassistant.data.settings.DurationUnitTest" `
  --tests "com.creationreadingassistant.ui.util.TimeUtilsTest" `
  --tests "com.creationreadingassistant.ui.screen.home.HomeReadingArchiveSectionTest"
```

### 6.2 编译

| 项 | 值 |
|---|---|
| 退出码 | **0** |
| 结果 | `BUILD SUCCESSFUL in 6m 32s`，`22 actionable tasks: 22 executed` |
| 警告 | 1 条 `LibraryBrowserComponents.kt:498` 弃用 + 3 条 `ReaderDocumentLoaderProfileTest.kt` opt-in —— **均为基线既有**，与本次两文件无关 |
| 日志 | `_evidence\compile-debug.log` |

> 备注：首次以本窗口独占的 E 盘 Gradle home 运行时，wrapper 仍重新下载了一次 Gradle 8.9 发行版（缓存路径未命中），但**依赖解析全程 `--offline` 成功**；发行版落地在 E 盘缓存内，未污染其他盘。

### 6.3 定向 JVM（XML 实测）

| 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `data.settings.DurationPrefsTest` | 8 | 0 | 0 | 0 |
| `data.settings.DurationUnitTest` | 2 | 0 | 0 | 0 |
| `ui.util.TimeUtilsTest` | 13 | 0 | 0 | 0 |
| `ui.screen.home.HomeReadingArchiveSectionTest` | 2 | 0 | 0 | 0 |
| **合计** | **25** | **0** | **0** | **0** |

- 退出码 **0**，`BUILD SUCCESSFUL in 49s`；日志 `_evidence\test-g6.log`；XML `_evidence\test-xml\`（4 份）。
- **与 `consumers3` 基线的 25/25 逐类一致 ⇒ 本次两文件改动无 JVM 回归**。

**XML SHA-256**

| 文件 | SHA-256 |
|---|---|
| `TEST-…DurationPrefsTest.xml` | `286ca0dd2cb5a60b1cfec3500e9bc5d9df26846e780da7939e384857b156f2f3` |
| `TEST-…DurationUnitTest.xml` | `2a8b9f530d2d99d94d2daf96a73fb575c58c84b71cb6c1f6f2e38e65f5c452e1` |
| `TEST-…TimeUtilsTest.xml` | `656cec50ad19066416e8aa6c1a311da8b7d5d82d78704f1b909bf4b56dfa0e7f` |
| `TEST-…HomeReadingArchiveSectionTest.xml` | `5160cef749a9c36a9009f5619b0e44433f9dbb4b4b1b851ae21ef345018da421` |

---

## 七、文件 SHA-256 对照

| 文件 | HEAD | `consumers3` 基线 | **本候选** | 共享工作树 |
|---|---|---|---|---|
| `ui/screen/profile/AppearanceSubPage.kt` | `f710a655c0c4efc350400f1892e6d9b4b65f4de5` | `948fe081cb8e62862c00dd3ca9db0c22528204fedb5e805f6f5eab9b9c8d63d5` | **`99d545813055c8ab7323d4c22513cb74f7bcfa2832effb2aeb17d4aedd1d3cbe`** | `7017558503ddb28c8a9918a370cbc53ca71491c8b2b2d53c116242f7ceb75430` |
| `ui/screen/profile/ProfileHomeScreen.kt` | `8fdf1dbbee8107a3a5a44f457bcca656d7a18a2b` | `4ef43fb383dc7e95171cdc4a1558fbb6ba732e7278666de957ffa85ddb91103b` | **`2c5ce238677bf7a0fb5187a51e593f4c5326c4179bf749f8cc77de2c8535dc7c`** | `2b8b3d324eb1a27f3e98514d19df0baff646e0a927315b5f0359d9f6543e3ece` |

- 基线列 = `consumers3`（本候选的直接底座，两文件与 HEAD 完全相同）；`mine ≠ worktree` 属**预期**（工作树含全部交叠改动，本候选只取 G6）。
- 行数：`AppearanceSubPage.kt` 490 → **506**（+16）；`ProfileHomeScreen.kt` 446 → **468**（+22）。
- 原始候选 `consumers3` 两文件 SHA 与本次开工前一致 ⇒ **原候选未被修改**。

---

## 八、未覆盖项与 seam

### 8.1 未覆盖（本轮**未运行**，不得当作通过）

| # | 未覆盖项 | 说明 |
|---|---|---|
| 1 | **Compose 手势 / UI 运行态** | `ProfileHomeScreen` / `AppearanceSubPage` **无任何 JVM 或 androidTest 引用**（两处 grep 实测为空）；长按是否真的不吞点击、读屏是否只读到一个操作，**只能在设备/仪器测试上验证**。本轮只做源码结构核查。 |
| 2 | **点击区域变化** | 「统计行不再打开我的阅读」是**行为变化**，需产品确认是否接受；视觉/手感未验收。 |
| 3 | **与 S06 联合态** | G6 根部依赖 `MainActivity`（含 S06 启动主题 hunk）；本候选沿用 `consumers3` 的 S06 最小候选底座，**未**与其它 S06 变体验证联合。 |
| 4 | **持久化重启路径** | 「切 DAYS → 杀进程 → 重启仍为 DAYS」有 `DurationPrefsTest` 的 DataStore 往返覆盖，但**未**在真实进程重启下验证。 |
| 5 | **设备/真机** | 按任务约束未操作任何设备；MuMu / 实体手机均未跑。 |
| 6 | **其余 G6 生产文件** | 本窗口只做 2 个文件；`CreationSection.kt` / `HeatmapSection.kt` / `SummaryGroup.kt` / `YearBillHeroCard.kt` 与 `YearBillHeadlineTest.kt` 不属本窗口（统计页明确排除）。 |
| 7 | **`lintDebug` / `assembleDebug` / `connectedAndroidTest`** | 未运行（按任务只要求所属模块编译 + 必要定向 JVM）。 |

### 8.2 seam（**未越界修改**，留给集成者/其他窗口）

| # | seam | 涉及文件（**非本窗口所有权**） | 说明与建议 |
|---|---|---|---|
| seam-1 | **`SettingsAnchor` / 设置搜索锚点** | `ui/screen/profile/SettingsAnchor.kt`、`SettingsFocus.kt`、`SettingsSearchIndex.kt`、`ProfileRoute.kt`、`ProfileUiState.kt` | 工作树把 G6 时长开关包在 `SettingsAnchor("appearance_duration_day")` 里，使其可被「设置搜索」定位。两个文件基线**都没有**这些符号（`HEAD`/候选均不存在）。若将来接入设置搜索，本候选的开关需要补一个锚点；**本轮刻意不引入**（属设置搜索功能，非 G6）。 |
| seam-2 | **长按入口的「保持整卡可点」变体** | `ui/util/DurationDisplay.kt` | 若产品不接受「统计行不再触发卡片点击」，需要让 `durationUnitToggle()` 支持把空 `onClick` 转发给外层动作（当前硬编码 `onClick = {}`）。该文件不在本窗口所有权内，故本轮选择 F3-B 收窄点击。 |
| seam-3 | **档案卡/首页重设计** | `ui/components/CompactSettingComponents.kt`（`CompactSettingsGroup` / `CompactSettingRow` / `CompactItemDivider`）、`ProfileHomeScreen` 的其余 hunk | 工作树的 `ArchiveStat` 是 String 形态（无 count-up），并把时长入口挂在统计行；本候选保留基线的 `HomeStat` + count-up。若整卡重设计后续落地，本候选的 `valueModifier` 需平移到 `ArchiveStat`。 |
| seam-4 | **外观重设计** | `ui/theme/ReaderPaperOptions.kt`、`paperPalette`、`AppearanceSubPage` 的 `ReaderPaperGrid` 分节 | 工作树在「外观」页新增「阅读纸张」分节（+171 行新组件）。属外观重设计，非 G6，本轮未纳入。 |

---

## 九、纪律声明

- 共享恢复仓库内**唯一写入 = 本文件**（`docs/coordination/legacy-stages/2026-09/2026-09-30-stage-e-g6-settings-isolation.md`）。未改共享产品源码 / 测试 / 公共配置 / 其他窗口报告 / `handoff/current.md` / `docs/handoff`。
- **未** stage / commit / push / reset / checkout；**未**迁移目录；**未**触碰原仓库 `D:\develop\Code\Codex\creation-reading-assistant`、正式包、正式书库或实体手机。
- 只读入口仓库；所有写入（候选源码、构建产物、Gradle 缓存、日志、XML）均在 `E:\creation-reading-assistant\stage-e-g6-settings-20260930\` 之下。
- **未修改**来源候选 `stage-e-g6-consumers3-20260930`（两文件 SHA 前后一致）。
- 生产文件所有权严格限于 `AppearanceSubPage.kt` 与 `ProfileHomeScreen.kt`；需要其他文件时**只登记 seam**，未越界修改。
- 未把未运行项写作通过：§八.1 的 7 项明确标注「未覆盖」；§五.4 明确区分「源码结构核查通过」与「设备手势/读屏未验证」。
- 阶段 E 整体进度不在本报告内重新裁定；本报告只记录 G6 设置页子片的隔离结果。
