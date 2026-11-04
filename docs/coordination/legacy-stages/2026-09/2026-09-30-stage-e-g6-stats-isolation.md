# 阶段 E — G6 统计页隔离结果（2026-09-30）

> **窗口**：G6 统计页隔离（与设置页窗口并行，互不重叠）。
> **共享恢复仓库（只读入口）**：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> **分支 / HEAD 已核对**：`codex/recover-worktree-20260920` / **`513891d6a66a9b78e072ba759d34ed580d377ecf`**；`git diff --cached --stat` 无输出（**暂存区空**）。
> **基线候选（只读来源）**：`E:\creation-reading-assistant\stage-e-g6-consumers3-20260930\android`
> **本窗口独占目录**：`E:\creation-reading-assistant\stage-e-g6-stats-20260930`
> **构建缓存**：`GRADLE_USER_HOME` = `<本窗口>\gradle-home`（E 盘）；`JAVA_HOME` = `D:\develop\Java\jdk-17.0.14`
> **输入**：[AGENTS.md](../../../../AGENTS.md)、[当前交接](../../../handoff/current.md)、[G6 边界更正](../../decisions/2026-09/2026-09-30-stage-e-g6-scope-correction.md)、[G6 分层候选](2026-09-30-stage-e-g6-partial-isolation.md)。
> **性质**：共享仓库**唯一写入 = 本文件**。未改其他窗口的 E 目录或报告；未 stage / commit / push / reset / checkout / stash；未迁目录；未触碰原仓库、正式包、正式书库与设备。
> **设备声明**：**未连接任何设备、未运行 adb、未使用 MuMu**。编译通过**不等于** MuMu UI 通过；本轮不存在任何 UI/手势/视觉证据。

---

## 一、结论摘要（先说人话）

1. **G6 统计页 5 个文件已隔离完成，编译通过、关联定向 JVM 37/37 全绿。** 在 consumers3 基线候选上，按「只取时长单位链路、不取相邻卡片视觉重构」的口径逐块提取，`LocalDurationUnit` / `durationUnitToggle` / `yearBillCardValue` 三条 G6 链路已贯通到统计页的创建统计、汇总、热力图、年度账单四个区块。
2. **补丁可复算**：全新基线 + 补丁 == 本窗口候选，**5/5 文件逐字节一致**；`StatsPage.kt` 的 SHA 与基线候选**完全相同**（`a12cb6c6…`），确认本窗口未改它。
3. **四个被排除的相邻改动已逐 hunk 登记**：共享 `MetricMicroCard` 抽取（属未跟踪新文件 `ui/components/MetricMicroCard.kt`，非本窗口所有权）、`YearBillHeroBanner` / `StreakMicroCapsule` 重组边界抽取、静态 2×2 网格改写、热力图单格点击微气泡（**独立新功能**）。四者在候选 vs 工作树的 diff 里合计 74 个 hunk，全部未纳入。
4. **任务点名的语义核对全部落到实测证据上**（§四）：NORMAL/COMPACT/TICK 同源阈值、HOURS 不进位到天、DAYS 在 24 小时进位、年度账单文案与数值同单位、每个语义组件仅 1 个长按入口、未删除或改动任何既有点击。
5. **仍属未验**：所有 UI 手势、长按切换的实际观感、TalkBack 朗读、视觉布局；以及本候选与设置页窗口（`AppearanceSubPage.kt` / `ProfileHomeScreen.kt`）合并后的**联合态**。不得把 37/37 外推为完整 G6 或设备通过。

---

## 二、方法与证据来源

### 2.1 隔离做法（共享仓库零写入）

| 步骤 | 做法 |
|---|---|
| 复制基线 | `robocopy <consumers3>\android <本窗口>\android /E /MT:16 /XD build .gradle .kotlin` → **838 文件 / 12.2 MB**（排除构建产物） |
| 只读参照 | `git archive --format=tar HEAD android` → `_base\android` |
| 提取 | 不用 `git add -p`；由 `_evidence\build_stats.py` 以**内容锚点 + 断言**逐块插入，锚点不唯一或断言失败即中止 |
| 前置断言 | 5 个所有权文件在 consumers3 上必须 == HEAD（实测成立，证明它们此前未被任何 G6 层触及） |
| 补丁 | `difflib.unified_diff` 生成 `a/android/… b/android/…` 标准统一补丁 |
| 复算 | 全新基线 5 文件 → `git apply --check` → `git apply` → 逐文件 SHA-256 比对 |

### 2.2 文件所有权（本窗口）

仅这 5 个文件被写入：

- `ui/screen/stats/components/CreationSection.kt`
- `ui/screen/stats/components/HeatmapSection.kt`
- `ui/screen/stats/components/SummaryGroup.kt`
- `ui/screen/stats/components/YearBillHeroCard.kt`
- `ui/screen/stats/components/YearBillHeadlineTest.kt`

`ui/screen/stats/StatsPage.kt` **属基线候选，未改**（SHA 复核见 §七）。其余统计文件的相邻改动一律未取。

---

## 三、逐文件纳入 / 排除 hunk

### 3.1 纳入（本窗口补丁，共 19 个 hunk）

| 文件 | 纳入 hunk | 内容 |
|---|---|---|
| `CreationSection.kt` | 4（`@@ -35,6 +35,8 @@`、`@@ -42,6 +44,13 @@`、`@@ -82,9 +91,11 @@`、`@@ -186,6 +197,7 @@`） | 2 个 G6 import；`CreationMetricItem.valueModifier` 字段（含 KDoc）；`formatCompactDuration(…, LocalDurationUnit.current)`；「沉浸时长」唯一长按入口；本地 `CreationMicroCard` 把 `valueModifier` 应用到主值 `Text` |
| `HeatmapSection.kt` | 2（`@@ -39,6 +39,8 @@`、`@@ -87,10 +89,16 @@`） | 2 个 G6 import；汇总胶囊 `formatCompactDuration(totalMs, LocalDurationUnit.current)`；该胶囊唯一长按入口 |
| `SummaryGroup.kt` | 4（`@@ -35,6 +35,8 @@`、`@@ -42,6 +44,13 @@`、`@@ -65,10 +74,13 @@`、`@@ -205,6 +217,7 @@`） | 2 个 G6 import；`SummaryMetric.valueModifier` 字段（含 KDoc）；「阅读时长」改用 `LocalDurationUnit.current`；该指标唯一长按入口；本地 `SummaryMicroCard` 应用 `valueModifier` |
| `YearBillHeroCard.kt` | 7（`@@ -28,6 +28,7 @@`、`@@ -38,8 +39,9 @@`、`@@ -47,6 +49,13 @@`、`@@ -65,20 +74,32 @@`、`@@ -86,9 +107,16 @@`、`@@ -231,6 +259,7 @@`、`@@ -248,26 +277,53 @@`） | `DurationUnit` / `LocalDurationUnit` / `durationUnitToggle` 三个 import；`YearBillMetricItem.valueModifier`；**分钟量化动画**替代「小时四舍五入」；`yearBillCardValue` 两个重载 + `buildYearHeadline` 的 `unit` 参数与 `spoken`；主值与 subtitle 同源同单位；「累计阅读」唯一长按入口；删除仅服务于旧小时四舍五入的 `kotlin.math.roundToInt` import |
| `YearBillHeadlineTest.kt` | 2（`@@ -1,5 +1,7 @@`、`@@ -85,4 +87,101 @@`） | 2 个 import + **4 条新用例**（主值 == 公共 formatter、50.5 小时不自相矛盾、47.5 小时不提前进位、含时长金句逐字包含主值） |

### 3.2 排除（候选 → 工作树的 diff，共 74 个 hunk + 测试文件 0 差异）

| 文件 | 排除规模 | 被排除的族（逐类） |
|---|---|---|
| `CreationSection.kt` | +8 / **−62**，4 hunk | ① `import …components.MetricMicroCard`；②③ 改用共享 `MetricMicroCard(icon=…, value=…, valueModifier=…)` 的调用点替换；④ **删除本地 `CreationMicroCard`（59 行）** |
| `HeatmapSection.kt` | **+146** / −7，18 hunk | **热力图单格点击微气泡整条功能**：`AnimatedVisibility`/`fadeIn`/`expandVertically`/`clickable`/`border`/`mutableStateOf`/`Icons.Outlined.Close`/`IconButton`/`AccessTime`/`DateTimeFormatter`/`TextStyle`/`Locale`/`roundToInt` 等 import、`selectedCell` 状态、`HeatmapGrid` 与 `HeatmapCellBox` 签名改造、`HeatmapCellTooltip`（68 行）、格子选中描边 |
| `SummaryGroup.kt` | +91 / **−150**，25 hunk | ① import 清理与 `MetricMicroCard`/`Dp` 新增；② **删除 `SummaryMetric` 数据类**；③ 删除 `metrics = listOf(…)` 动态列表；④ **静态 2×2 网格改写**；⑤ **`StreakMicroCapsule` 抽取**（含 `SummaryMicroCard` → 胶囊的全部改造）；⑥ KDoc 措辞改动 |
| `YearBillHeroCard.kt` | +95 / **−157**，27 hunk | ① import 清理与 `MetricMicroCard`/`Dp` 新增；② **删除 `YearBillMetricItem`**；③ 删除 `metrics = listOf(…)`；④ **`YearBillHeroBanner` 抽取**；⑤ **静态 2×2 网格改写**；⑥ KDoc 措辞改动 |
| `YearBillHeadlineTest.kt` | **0**（无差异） | 该文件工作树改动全部属 G6，已逐字节纳入 |

**判据**：`MetricMicroCard` 是**未跟踪新文件**（`?? android/app/src/main/java/com/creationreadingassistant/ui/components/MetricMicroCard.kt`），不在本窗口所有权内；`YearBillHeroBanner`/`StreakMicroCapsule` 是重组边界优化（纯性能重构）；静态网格是布局改写；热力图点击微气泡是**新功能**。四类均非「G6 的 LocalDurationUnit / 公共格式化 / 时长切换入口」，故一律排除。

**为「不夹带共享组件」所做的必要适配（3 处）**：在三个本地私有卡片上把 `valueModifier` 应用到主值 `Text`，并把 `valueModifier` 作为 item 数据类的默认参数（`= Modifier`）。这样长按入口能生效，但**不引入对 `MetricMicroCard` 的依赖**。这是「必要的时长切换入口」的最小实现，已在 §八 登记为与工作树形态的差异。

---

## 四、关键语义核对（任务点名项）

### 4.1 NORMAL / COMPACT / TICK 阈值一致 —— **成立（同源单一实现）**

`ui/util/TimeUtils.kt` 的 `formatDuration(ms, unit, style)` 是唯一实现：`ms <= 0` → `"0 分钟"`；`minutes = ms / 60_000`、`hours = minutes / 60`、`days = hours / 24` 三个量级只算一次；三种样式共用**同一套 `when` 分支条件**，只有输出的字符串不同。`formatCompactDuration` 只是 `formatDuration(ms, unit, DurationStyle.COMPACT)` 的转发（`StatsPage.kt:186`，本窗口未改）。

实测守门：`TimeUtilsTest` **13/13**，其中
- `default unit is hours and never rolls over to days`
- `days unit rolls over at 24 hours`
- `days unit keeps hours below one day`
- `compact hours style drops the minute tail but keeps the thresholds`
- `tick style keeps the magnitude but uses single character units`
- `all three styles agree on the magnitude of the same value`

即「同一毫秒值在三种样式下不可能落到互相矛盾的量级」是**被断言**的，不只是注释承诺。

### 4.2 默认 HOURS 不自动进位到天 —— **成立**

`DurationUnit.HOURS` 分支**根本没有 days 判定**，只有 `hours > 0` 与 else。实测断言：`formatDuration(30 * hourMs)` == `"30 小时 0 分钟"`（而非「1 天 6 小时」）。

### 4.3 DAYS 在 24 小时进位 —— **成立**

`DurationUnit.DAYS` 分支条件为 `hours >= 24`，`days = hours / 24`，**向下取整**。实测断言：30 小时 → `"1 天 6 小时"`；48 小时 → `"2 天 0 小时"`；5 小时 20 分 → `"5 小时 20 分钟"`（不足一天不进位）。

### 4.4 年度账单文案与数值同单位 —— **成立，且被测试钉死**

- 主值：`yearBillCardValue(totalMs, unit) = formatCompactDuration(totalMs / 60_000L * 60_000L, unit)`（**量化到整分钟，不做小时级四舍五入**）。
- 渲染期：`cardDurationText = yearBillCardValue(animatedMinutes, durationUnit)`；`subtitle = "$cardDurationText $anchorYear"`（**与主值同一个字符串**）。
- 金句：`buildYearHeadline(…, unit)` 内 `val spoken = formatCompactDuration(totalMs, unit)`，两条含时长的分支都改用 `$spoken`；分支判定仍用 `hours >= 50f`（**与单位偏好解耦**，换单位不改变命中哪一句）。

新增的 4 条用例正是守这条不变量（实测 `YearBillHeadlineTest` 6 → **10/10**）：
1. `card value equals the public formatter result on the original duration` —— 7 个样本 × 2 个单位，断言 `formatCompactDuration(ms, unit) == yearBillCardValue(ms, unit)`；
2. `fifty hours thirty minutes never contradicts itself between card and headline` —— 50.5 小时主值**不得含「51」**，且金句**逐字包含**主值；
3. `days unit rolls over consistently between card and headline` —— 47.5 小时在 DAYS 下必须是 `"1 天"`，**不得提前进位到 2 天**；
4. `headline never contradicts the card whenever it mentions a duration` —— 只要金句含时长，就必须逐字包含主值。

### 4.5 一个语义组件只设一个长按入口 —— **成立（实测每文件恰好 1 处）**

| 组件 | 唯一入口 | 刻意不挂的位置 |
|---|---|---|
| `CreationSection` | 「沉浸时长」主值 | 笔记数 / 灵感数 / 阅读天数（整数计数，不过 formatter） |
| `SummaryGroup` | 「阅读时长」主值 | 阅读天数 / 读过书籍 / 已读完 / 连续打卡胶囊 |
| `HeatmapSection` | 顶部汇总胶囊 | 365 个格子（各自成为入口会让 TalkBack 同屏读到 365 个同名操作） |
| `YearBillHeroCard` | 「累计阅读」主值 | subtitle 与金句（与主值**同源同文**，再挂就是同语义的第 2、3 个入口） |

实测计数：4 个文件各含 `durationUnitToggle` 3 次 = 1 个 import + 1 处 KDoc 引用 + **1 处实际调用**。

### 4.6 保留原卡片点击 —— **成立（HEAD 本无卡片点击，未删除也未改动）**

实测 HEAD 的 4 个组件均无 `clickable` / `combinedClickable` / `onClick`（只有 `testTag`）。本候选**未删除或改写任何既有手势**，唯一新增手势是每组件 1 个 `Modifier.durationUnitToggle()`（内部 `onClick = {}`，只有长按生效）。热力图格子点击（新增功能）已排除，故不存在「父子两层抢手势」。

---

## 五、编译门禁（已验）

```
$env:GRADLE_USER_HOME = '<本窗口>\gradle-home'
$env:JAVA_HOME = 'D:\develop\Java\jdk-17.0.14'
cd <本窗口>\android
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --console=plain --offline --max-workers=1
```

| 项 | 结果 |
|---|---|
| 结果 | **BUILD SUCCESSFUL**；**EXITCODE=0** |
| 日志 | 首轮（含测试文件多一行空行的中间态）`_evidence\gradle-01-compile.log`（4m 9s）；**最终产物** `_evidence\gradle-02-compile-final.log` |
| 警告 | 仅既有 `ReaderDocumentLoaderProfileTest` 的 opt-in 警告；**无 error** |

**未运行**：`:app:lintDebug`、`:app:assembleDebug`、`:app:compileDebugAndroidTestKotlin`、`:app:connectedDebugAndroidTest`（完整门禁与设备验收由集成者/设备所有者串行执行）。

---

## 六、关联定向 JVM（已验）

```
.\gradlew.bat :app:testDebugUnitTest --console=plain --offline --max-workers=1 `
  --tests "com.creationreadingassistant.ui.screen.stats.components.YearBillHeadlineTest" `
  --tests "com.creationreadingassistant.ui.util.TimeUtilsTest" `
  --tests "com.creationreadingassistant.ui.screen.stats.HeatmapBuildTest" `
  --tests "com.creationreadingassistant.ui.screen.stats.StatsComputationTest" `
  --tests "com.creationreadingassistant.ui.screen.stats.ComputeTodayReadingMsTest" `
  --tests "com.creationreadingassistant.ui.screen.stats.StatsFileStructureTest"
```

| 套件 | tests | failures | errors | skipped | 说明 |
|---|---:|---:|---:|---:|---|
| `ui.screen.stats.components.YearBillHeadlineTest` | **10** | 0 | 0 | 0 | 6 条原有 + **4 条新增**（§4.4） |
| `ui.util.TimeUtilsTest` | **13** | 0 | 0 | 0 | 同源阈值 / HOURS 不进位 / DAYS 24h 进位 / 三样式量级一致 |
| `ui.screen.stats.HeatmapBuildTest` | 4 | 0 | 0 | 0 | 热力图分桶 |
| `ui.screen.stats.StatsComputationTest` | 1 | 0 | 0 | 0 | 统计计算 |
| `ui.screen.stats.ComputeTodayReadingMsTest` | 6 | 0 | 0 | 0 | 今日时长 |
| `ui.screen.stats.StatsFileStructureTest` | 3 | 0 | 0 | 0 | 统计文件结构守卫 |
| **合计** | **37** | **0** | **0** | **0** | **BUILD SUCCESSFUL in 31s；TEST_EXITCODE=0** |

XML 原件与 SHA 见 `_evidence\test-xml\` 与 `sha256-manifest.txt` §5；日志 `_evidence\gradle-03-stats-jvm.log`。

---

## 七、可复算补丁与逐文件 SHA

### 7.1 补丁

| 项 | 值 |
|---|---|
| 路径 | `E:\creation-reading-assistant\stage-e-g6-stats-20260930\patches\stage-e-g6-stats.patch` |
| 大小 | 20,716 字节 |
| SHA-256 | `6547463eb400b52f9cedaf9b85cc31a79fd8652c807b419491a3cb385f2d184f` |
| `git apply --check -p1` | **EXITCODE=0**（5 个文件全部 `Checking patch … OK`） |
| `git apply -p1` | **EXITCODE=0** |
| 复算比对 | 全新基线 + 补丁 与 候选：**5/5 IDENTICAL** |

### 7.2 逐文件 SHA-256

| 文件 | 本窗口候选 | 基线候选 consumers3 | 共享工作树 |
|---|---|---|---|
| `CreationSection.kt` | `e9e627dc943cc6aa201325638de947b295ebf74f9778ba1ed131920928632c3a` | `336fdc9e60b9a813329d2c4cd173b81184b786d42667068ee113d90b887990cc` | `2721a642350aa25b39d8cf2398e68f53872a73f1416406bdb8fe830b8ac650f4` |
| `HeatmapSection.kt` | `5c6f2154dde88229e1e978151073eab532aaa44f9e6a2cc4200ed31e3ea6cd2b` | `b79aa554a51c44a703fee9768efc4b978224801f235b061a1be28766d8c501a9` | `156ad2b24541ce006b5b4a6dbf91599e1af37c160877c5d6e54d588ff3d8201a` |
| `SummaryGroup.kt` | `0f018e57660ec67f50b81bc4ea30ced9755eac61776df9fe1fadb68c621fe35f` | `021a9fb3dcd2dec4b7896eb29b963f148d7c72a4253911992fee935ceb4236a5` | `9bf3e164e3e5ba067adddff8c2afe3db4ca39f5a1410b2cac3ecfdae760a5553` |
| `YearBillHeroCard.kt` | `6b7c5a9cd3d742bae626646fddc7432aa24c62dc3a10bc82df5c4c44fa3e075c` | `47990220a36f2f811d386c2b50d197262d5d2283e1dae94a360d929c8c8a0de5` | `ae24345221a2c5cec49944871a46fabd5202e3b25d9f65230f8da5b8e006f71b` |
| `YearBillHeadlineTest.kt` | `2c07ce22287fadac85c306e937f69b0d364ccd7b47823f6747042a9b070624da` | `a1cf0ad0e59b357ffc62cd616a043d20efef7e3254b6936e79bbb524f58f39ce` | `2c07ce22287fadac85c306e937f69b0d364ccd7b47823f6747042a9b070624da` |

> `YearBillHeadlineTest.kt` 与共享工作树**逐字节相同**（`2c07ce22…`）——该文件的工作树改动全部属 G6，无相邻改动需排除。

### 7.3 `StatsPage.kt` 未被改动（所有权边界证据）

| 来源 | SHA-256 |
|---|---|
| 本窗口候选 | `a12cb6c6a571b1c4e72043fd794f6442f3bab3cf3d3ae87a5ffaffef79aea67c` |
| 基线候选 consumers3 | `a12cb6c6a571b1c4e72043fd794f6442f3bab3cf3d3ae87a5ffaffef79aea67c` |
| 共享工作树（仅参考，说明其另有 S05 等改动未纳入） | `994c7b3fab754b115d69601839856ca33153a71ab4fd5a0ad0a5b81c76866d5a` |

### 7.4 E 盘产物

```
E:\creation-reading-assistant\stage-e-g6-stats-20260930\
  android\                                    本窗口候选（consumers3 复制 + 5 文件 G6）
  _base\android\                              HEAD 只读参照
  _verify\android\                            复算校验树（全新基线 + 补丁）
  gradle-home\                                GRADLE_USER_HOME（E 盘）
  patches\stage-e-g6-stats.patch              可复算补丁（20,716 B）
  _evidence\
    build_stats.py                            提取脚本（锚点 + 断言）
    build-stats.log                           脚本运行日志
    commands.txt                              实际命令与退出码
    selection-map.csv                         5 行纳选映射
    excluded-vs-worktree.txt                  候选 vs 工作树逐文件被排除 hunk
    sha256-manifest.txt                       5 节完整 SHA 清单
    gradle-01-compile.log / gradle-02-compile-final.log
    gradle-03-stats-jvm.log
    test-xml\                                 6 份结果 XML
```

---

## 八、跨所有权与 seam（只登记，不越界）

1. **`MetricMicroCard` 属他窗口/未跟踪新文件**。本窗口为避开该依赖，在 3 个本地私有卡片上各自加了 `valueModifier` 参数。**若将来集成者采纳 `MetricMicroCard` 抽取，这 3 处适配应被替换为共享组件调用**——否则会出现「同一个视觉卡片有两份实现」。
2. **热力图单格点击微气泡**（`?? ` 之外的 `HeatmapCellTooltip` 等，均在 `HeatmapSection.kt` 内）是**独立新功能**，本窗口已完整排除。它与本窗口的汇总胶囊入口**不冲突**（入口挂在标题胶囊、点击挂在格子），但 `DurationDisplay.kt` 的 KDoc 明确要求「不得在父子两层重复挂 `durationUnitToggle`」——若该功能后续纳入，须按 F3-B 口径复核格子是否已有 `clickable`（本候选下**该问题不存在**，因为格子点击未纳入）。
3. **与设置页窗口的联合态未验**。另一窗口独占 `AppearanceSubPage.kt` / `ProfileHomeScreen.kt` / `ReadingArchiveSection.kt`。本窗口的候选与它**文件不重叠**，但两者合并后才会形成「G6 全局切换」；合并后的编译与设备表现不属本窗口结论。
4. **注释口径的一处主动收窄**：`HeatmapSection.kt` 的入口注释，工作树原文包含「格子本身已有『点击选中』的主交互，再挂长按会让一次点击先被长按手势消费」一句——该句以**被排除的点击功能**为前提。本候选把该句改为只陈述持久成立的理由（365 个格子不做入口的 TalkBack 理由），使注释与候选自身一致。这是**有意的措辞差异**，不是功能差异。
5. **`kotlin.math.roundToInt` import 的删除**：该 import 在原文件仅服务于「先把时长抬成整数小时」的旧实现；改为分钟量化后它不再被使用，故随之删除。属 G6 改动的直接后果，非独立重构。

---

## 九、未验项与不得外推

| 项 | 状态 |
|---|---|
| 长按切换单位的实际观感 / 触感 | **NOT RUN**（无设备） |
| TalkBack 朗读「切换时长显示单位」的唯一性 | **NOT RUN** |
| 四个区块的视觉布局（本候选保留了 HEAD 的 `listOf` + `chunked(2)` 与本地卡片） | **NOT RUN**；且**本候选与工作树的视觉不同**（工作树有静态网格与共享卡片，本候选没有） |
| MuMu qahost / 真机 UI | **NOT RUN**；**不得把编译通过称为 MuMu UI 通过** |
| `lintDebug` / `assembleDebug` / APK | **未跑** |
| `compileDebugAndroidTestKotlin` | **未跑**（候选不含 androidTest 文件；`StatsComposeTest` 未执行） |
| 与设置页窗口合并后的联合态 | **NOT RUN** |
| 完整 G6（22 生产文件 + 5 测试） | 本窗口只补 **4 个生产文件 + 1 个测试**；加上此前分层候选共覆盖 20/22 生产文件、5/5 测试；余 `AppearanceSubPage.kt`、`ProfileHomeScreen.kt` 属设置页窗口 |
| 可提交的最终 G6 补丁 | **尚未生成**；本窗口产出的是统计页这一层的可复算补丁 |

**外推禁止**：本候选编译通过 + 关联定向 JVM 37/37，**只证明该精确候选在这两条命令下通过**；不证明视觉等价、手势可用、设备通过，也不证明与其它 G6 层的联合态成立。

---

## 十、纪律声明

- 共享恢复仓库内**唯一写入 = 本文件**（`docs/coordination/legacy-stages/2026-09/2026-09-30-stage-e-g6-stats-isolation.md`）。未改任何产品源码 / 测试 / 资源 / 配置 / CSV / 其他窗口报告。
- **未**在共享仓库 stage / commit / push / reset / checkout / stash；**未**迁目录；**未**触碰原仓库、正式包、正式书库、设备、MuMu、ADB。
- **未**修改其他窗口的 E 盘目录或报告（设置页窗口的 `stage-e-g6-settings-20260930` 只被读取过目录清单，未写入）。
- 本轮所有构建、缓存、日志与产物**均在 E 盘**（`GRADLE_USER_HOME` 指向本窗口 E 盘目录；未在 C/D 新建构建目录）。
- 未把编译通过表述为 UI 或设备通过；§四 的语义结论均给出实测断言来源，§九 的未验项逐条列出。
- 全文只用中性标识，未记录任何真实书名。
