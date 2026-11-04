# S15-F1/F2 实施报告：时长展示单一事实源与全局单位一致性

> 执行：Claude（窗口 B）　|　任务：S15-F1/F2
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`　固定基点：`5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收尾 `git rev-parse --short HEAD` 均 = `5aed593`
> 未运行 ADB / 真机 / APK 安装；未 commit / push / stage

---

## 1. 执行摘要

Codex 的三条纠正全部成立，并已在本片处理：

1. **不接受"恰好 20 个展示位"**：本轮用 `rg`/`grep` 重新枚举，实际生产时长文本节点 **24 处**（比 Qoder 的 20 处多 4 处，且 TrendSection 与 YearBillHeroCard 各自有**多个**独立节点）。清单见 §2。
2. **英文 h/m 不单独按 P1 修**：`"3h 20m"` 的私有 formatter 直接删除、改调公共中文 formatter，该问题**自然消失**，未单独立级。
3. **P1 确认**：`DurationPrefs` 承诺"任意时长文本跟随全局单位"，而统计页 5 处 + 首页 2 处完全不读该单位。本片已让它们接入公共 formatter 并读取 `LocalDurationUnit`。
4. **P2 确认**：`ProfileHomeScreen` 的时长行读单位但不能长按。本片改调公共 formatter 并挂 `durationUnitToggle()`。
5. **未动父子手势结构**：`MyReadingScreen` / `BookDetailStatsSection` 的卡片手势一行未改（列为真机事项 §8）。

**核心交付**：`formatDuration(ms, unit, style)` 成为唯一时长格式化入口，新增 `DurationStyle { NORMAL, COMPACT, TICK }` 参数。**三种样式的阈值、舍入、不足一分钟策略完全同源**，页面只能选"说多详细"，不能选"怎么算"。三处私有 formatter 中删除 2 处、1 处改为纯转发。

---

## 2. 实际盘点到的展示节点清单

用 `grep -rn "formatDuration(\|DurationText(\|formatCompactDuration\|formatArchiveDuration"` 加 `60_000 / 3_600_000` 数值换算扫描交叉确认。**一个 Composable 内有两段独立时长文字时分别计数。**

### A. 本就跟随单位（12 处，未改行为）

| # | 位置 | 节点 |
|---:|---|---|
| 1 | `profile/ReadingArchiveSection.kt:225` | 「累计 …」 |
| 2 | `shelf/BookDetailStatsSection.kt:75` | 「总阅读时长」 |
| 3 | `shelf/BookDetailStatsSection.kt:270` | 单条阅读记录 pill（`DurationText`） |
| 4 | `reader/sheets/ReaderBookInfoSheet.kt:217` | 「本次已读」 |
| 5 | `reader/sheets/ReaderBookInfoSheet.kt:225` | 「累计阅读」 |
| 6 | `reader/sheets/ReaderProgressSheet.kt:173` | 「累计」 |
| 7 | `reader/sheets/ReaderProgressSheet.kt:248` | 「本次已读」 |
| 8 | `reader/sheets/ReaderProgressSheet.kt:277` | 「预计读完」 |
| 9 | `homearchive/HomeCompletedScreen.kt:243` | 头部聚合（`DurationText`） |
| 10 | `homearchive/HomeCompletedScreen.kt:516` | 每本（`DurationText`） |
| 11 | `readinghistory/MyReadingScreen.kt:246` | 聚合（`DurationText`） |
| 12 | `readinghistory/MyReadingScreen.kt:565` | 每本（`DurationText`） |

### B. 本片新接入单位（11 处，其中 4 处是 Codex 指出的漏项）

| # | 位置 | 节点 | 改动前 |
|---:|---|---|---|
| 13 | `home/components/HomeMetricsSection.kt:101` | 「今日」指标卡主值 | `formatCompactDuration` → `"3h 20m"`（**英文**） |
| 14 | `home/components/HomeReadingArchiveSection.kt:136` | 阅读档案卡聚合 | `formatArchiveDuration`（不读单位） |
| 15 | `stats/components/SummaryGroup.kt:72` | 摘要「阅读时长」 | `StatsPage.formatCompactDuration` |
| 16 | `stats/components/CreationSection.kt:87` | 「沉浸时长」 | 同上 |
| 17 | `stats/components/HeatmapSection.kt:115` | 热力图汇总 | 同上 |
| 18 | `stats/components/HeatmapSection.kt:400` | 热力图单日详情 | 同上 |
| 19 | **`stats/components/TrendSection.kt:167`** | **柱顶分钟数字**（Codex 指出的漏项） | `"${ms / 60_000L}"`，无单位 |
| 20 | **`stats/components/YearBillHeroCard.kt:107`** | **年度账单主值「累计阅读」** | `formatCompactDuration` |
| 21 | **`stats/components/YearBillHeroCard.kt:110`** | **同卡 subtitle**（Codex 指出的漏项） | `"$hours 小时 $anchorYear"` 硬编码 |
| 22 | **`stats/components/YearBillHeroCard.kt:227`** | **年度金句分支「共翻阅 N 小时」**（Codex 指出的漏项） | `hours.toInt()` 硬编码 |
| 23 | **`stats/components/YearBillHeroCard.kt:231`** | **年度金句分支「累计 N 小时」**（Codex 指出的漏项） | 同上 |

### C. 本片新接入单位 + 新增长按（1 处，P2）

| # | 位置 | 节点 |
|---:|---|---|
| 24 | `profile/ProfileHomeScreen.kt:198` | 「我的」首页档案卡「阅读时长」（改用公共 formatter + `durationUnitToggle()`） |

**合计 24 处**（Qoder 报 20 处；差额 = TrendSection 柱顶 1 + YearBillHeroCard 的 subtitle/金句共 3）。

> YearBillHeroCard 一卡之内有 **4 段**独立时长文字（主值 / subtitle / 两个金句分支），Qoder 只计了主值。

---

## 3. 修改文件清单及每个文件的理由

| # | 文件 | 理由 |
|---:|---|---|
| 1 | `ui/util/TimeUtils.kt` | **核心**：新增 `DurationStyle { NORMAL, COMPACT, TICK }`；`formatDuration` 增加 `style` 参数；三种样式共用同一套阈值/舍入/不足一分钟策略。这是本片唯一产生时长文本的地方。 |
| 2 | `ui/util/DurationDisplay.kt` | 注释中"6 个页面的 12 个展示位"已与现状不符（P3-5），改为不写死数量 + 给出盘点命令。**未改任何行为**。 |
| 3 | `home/components/HomeMetricsSection.kt` | 删除私有 `formatCompactDuration`（英文 `"Nh Mm"` + 四舍五入），改调公共 `COMPACT` 并传单位。 |
| 4 | `home/components/HomeReadingArchiveSection.kt` | 删除私有 `formatArchiveDuration`（含 `coerceAtLeast(1)` 与整点省略分钟两处本地判断），改调公共 `NORMAL` 并传单位。 |
| 5 | `stats/StatsPage.kt` | `formatCompactDuration` 由"自带小数小时进位算法"改为**纯转发**公共 `COMPACT`，并增加 `unit` 参数。保留函数名以免动 5 个调用点的 import（它们是同模块的稳定引用）。 |
| 6 | `stats/components/SummaryGroup.kt` | 传 `LocalDurationUnit.current`。 |
| 7 | `stats/components/CreationSection.kt` | 同上。 |
| 8 | `stats/components/HeatmapSection.kt` | 两处独立节点（汇总 + 单日详情）都传单位。 |
| 9 | `stats/components/TrendSection.kt` | 柱顶数字改用公共 `TICK` 形态（`"45分"`/`"2时"`/`"1天"`）；柱区窄，`COMPACT` 的 `"45 分钟"` 会溢出。 |
| 10 | `stats/components/YearBillHeroCard.kt` | 四段节点全部接入：主值走 `COMPACT`，subtitle 与主值同源，`buildYearHeadline` 增加 `unit` 参数且两处金句改走公共策略。 |
| 11 | `profile/ProfileHomeScreen.kt` | 删除内联 `when` 分支（缺 `ms<=0` 保护），改调公共 formatter；`ArchiveStat` 增加 `valueModifier` 并给时长行挂 `durationUnitToggle()`。 |
| 12 | `profile/ProfileUtils.kt` | 删除**零调用**的 `formatDuration` 转发（P3-6）。 |
| 13 | `test/.../ui/util/TimeUtilsTest.kt` | 新增 7 个用例覆盖 COMPACT/TICK/跨样式一致性。 |
| 14 | `test/.../ui/screen/home/HomeReadingArchiveSectionTest.kt` | 原用例断言已删除的 `formatArchiveDuration`，改写为"档案卡改走公共 formatter 且跟随单位"的迁移护栏。 |

---

## 4. 私有 formatter 的删除与保留

| 实现 | 处置 | 理由 |
|---|---|---|
| `HomeMetricsSection.formatCompactDuration`（`private`） | **删除** | 唯一调用点已改调公共；英文 `h/m` 与四舍五入随之消失 |
| `HomeReadingArchiveSection.formatArchiveDuration`（`internal`） | **删除** | 唯一生产调用点已改调公共；`coerceAtLeast(1)` 与整点省略分钟属页面本地判断，按任务第 7 条必须上交公共策略 |
| `ProfileUtils.formatDuration`（`internal`） | **删除** | 全仓零调用（P3-6），死代码 |
| `ShelfUtils.formatDuration`（`internal`） | **保留** | 仍有 1 个生产调用点（`BookDetailStatsSection.kt:75`） |
| `ReaderHelpers.formatDuration`（`internal`） | **保留** | 仍有 3 个生产调用点（`ReaderBookInfoSheet` ×2、`ReaderProgressSheet` ×1） |
| `StatsPage.formatCompactDuration`（`internal`） | **保留但改为纯转发** | 仍有 5 个生产调用点；改名会牵动 5 处 import 而不改变行为。**它现在不再包含任何算法**，只做 `formatDuration(ms, unit, COMPACT)`。 |

> 按任务第 5 条：**没有为"清理"强删仍有调用的转发函数**。两个保留的转发（ShelfUtils / ReaderHelpers）是零逻辑的一行委托，删它们需要改 4 个调用点的 import，收益为零。

---

## 5. 公共 formatter 的最终契约

```kotlin
fun formatDuration(
    ms: Long,
    unit: DurationUnit = DurationUnit.HOURS,
    style: DurationStyle = DurationStyle.NORMAL,
): String
```

| 条件 | NORMAL | COMPACT | TICK |
|---|---|---|---|
| `ms <= 0` | `0 分钟` | `0 分钟` | `0 分钟` |
| HOURS，不足 1 小时 | `45 分钟` | `45 分钟` | `45分` |
| HOURS，≥1 小时 | `1 小时 30 分钟` | `1 小时` | `1时` |
| HOURS，跨天 | `30 小时 0 分钟`（**不进位**） | `30 小时` | `30时` |
| DAYS，不足 1 小时 | `40 分钟` | `40 分钟` | `40分` |
| DAYS，不足 1 天 | `5 小时 20 分钟` | `5 小时` | `5时` |
| DAYS，≥24 小时 | `1 天 6 小时` | `1 天` | `1天` |

**同源保证**（由测试钉住）：
- 舍入统一为 `ms / 60_000` 向下取整；**没有任何一处 round / 小数小时 / 秒**。
- 不足一分钟统一 `0 分钟`；不再出现旧档案卡的 `1 分钟` 与旧指标卡的 `30 秒`。
- 三种样式对同一 `(ms, unit)` **必定落在同一量级**（`TimeUtilsTest` 用 14 个样本 × 2 单位 × 3 样式穷举断言）。

---

## 6. 测试命令、exit code、测试数量

### 6.1 公共 formatter 测试（先写测试，后改生产代码）

```
cd android
./gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.util.TimeUtilsTest"
```
- exit code **0**
- `TimeUtilsTest`：**16 项**（原 9 + 新增 7），failures=0 errors=0 skipped=0

新增用例：`compact hours style…`、`compact days style…`、`compact style keeps the zero policy…`、`the two styles never disagree…`、`tick style keeps the magnitude…`、`tick style stays within the width budget…`、`all three styles agree on the magnitude…`

### 6.2 定向 JVM 测试（本片生产文件直接相关）

```
./gradlew.bat --console=plain :app:testDebugUnitTest \
  --tests "com.creationreadingassistant.ui.util.*" \
  --tests "com.creationreadingassistant.ui.screen.home.*" \
  --tests "com.creationreadingassistant.ui.screen.stats.*" \
  --tests "com.creationreadingassistant.ui.screen.profile.*"
```
- exit code **0**，`BUILD SUCCESSFUL`
- **合计 71 项，failures + errors = 0**

| 测试类 | tests | fail | err |
|---|---:|---:|---:|
| `ui.util.TimeUtilsTest` | 16 | 0 | 0 |
| `ui.screen.home.HomeReadingArchiveSectionTest` | 2 | 0 | 0 |
| `ui.screen.home.HomeContinueModelsTest` | 7 | 0 | 0 |
| `ui.screen.stats.components.YearBillHeadlineTest` | 6 | 0 | 0 |
| `ui.screen.stats.ComputeTodayReadingMsTest` | 6 | 0 | 0 |
| `ui.screen.stats.HeatmapBuildTest` | 4 | 0 | 0 |
| `ui.screen.stats.StatsComputationTest` | 1 | 0 | 0 |
| `ui.screen.stats.StatsFileStructureTest` | 3 | 0 | 0 |
| `ui.screen.profile.SettingsSearchIndexTest` | 15 | 0 | 0 |
| `ui.screen.profile.UpdateCheckTest` | 11 | 0 | 0 |

> 未运行全量 JVM、未运行 Lint。

### 6.3 编译

```
./gradlew.bat --console=plain :app:compileDebugKotlin --rerun
```
- exit code **0**，`BUILD SUCCESSFUL in 2m 7s`
- 唯一 warning 位于**未修改**的 `ui/screen/shelf/LibraryBrowserRoute.kt:145`（`Icons.Outlined.Sort` 弃用），与本体无关。

### 6.4 测试过程中发现并修正的两处问题

| 问题 | 性质 | 处置 |
|---|---|---|
| `TICK 形态必须 ≤4 字符` 在 9999 小时处失败 | **我的断言过严**，非产品缺陷：趋势柱是日/周/月聚合，月视图物理上界 744 小时 | 样本上界改为 `31×24h`，并写明理由 |
| `compileDebugKotlin` 报 `Could not get file mode for …HomeCompletedScreenKt.class` | **共享工作区的构建缓存竞争**（其他窗口的 Gradle 并发访问同一 `build/`），非代码错误；同次运行中 Kotlin 编译本身已通过、无 `e:` | 重试即成功 |

---

## 7. `git diff --check` 结果

```
git diff --check -- <本片 12 个 tracked 修改文件>
```
- **exit code 0**，无空白错误、无行尾冲突标记。
- 唯一输出是 Git 对 `ProfileHomeScreen.kt` 的提示 `CRLF will be replaced by LF`——该文件是 CRLF 行尾，**这是它原有的行尾风格**，非本片引入。
- 过程中曾报出 `ProfileHomeScreen.kt:439` 与 `SummaryGroup.kt:175` 的 `new blank line at EOF`（**我引入的**），已修正后复检为 0。

---

## 8. P0 / P1 / P2 / P3 与尚未处理的真机事项

### 分级

| 级别 | 数量 | 说明 |
|---|---:|---|
| **P0** | **0** | — |
| **P1** | **0**（本片关闭 2 项） | ①统计页 5 处 + 首页 2 处不读全局单位（Qoder P1-2）；②首页指标卡英文 `"3h 20m"`（Qoder P1-1，按 Codex 指示不单独立级，随 formatter 收敛自然消失）。 |
| **P2** | **0**（本片关闭 1 项） | `ProfileHomeScreen` 时长行读单位但不能长按（Qoder P2-2）。 |
| **P3** | **0**（本片关闭 2 项） | ①`DurationDisplay.kt` 注释数量过时（P3-5）；②`ProfileUtils.formatDuration` 死代码（P3-6）。 |

**本片未引入任何新缺陷。** 关闭项均**待 Codex 独立复核**确认。

### 未处理的既有项（不在本片范围）

| ID | 项 | 为何未做 |
|---|---|---|
| P2-1 | 长按劫持父级卡片手势（`MyReadingScreen:488` / `BookDetailStatsSection:244`） | **Codex 明确指示：父子手势冲突尚未真机复现，本片不要擅自改手势结构。** |
| P2-3 | 同值不同显的**舍入**分叉 | 本片已消除全部舍入分叉（统一向下取整）。剩余"整点是否省略分钟"属 `NORMAL` vs `COMPACT` 的**样式差异**，是显式选择而非矛盾，且已被 §5 同量级测试钉住。 |
| P3-1 | `DurationPrefs.toggle()` 读-改-写竞态 | 状态层，属窗口 D 所有；任务禁止修改 `DurationPrefs` 状态层测试。 |
| P3-2 | 详情页 pill 点击不展开父行 | 同 P2-1，手势结构。 |
| P3-3 / P3-4 | `durationUnitToggle` 无触觉反馈 / 无 `onLongClickLabel` | **任务明确：不增加振动、不扩大到 S15-F3。** |
| P3-7 | `ProfileHomeScreen` 缺 `ms<=0` 保护 | **已随本片消除**（改调公共 formatter，其 `ms<=0 → "0 分钟"` 生效）。 |
| P3-8 | `HomeMetricsSection` 不可达的 `"N 秒"` 分支 | **已随私有 formatter 删除而消失**。 |

### 尚未处理的真机事项（readiness blocker）

| ID | 项 | 为什么必须真机 |
|---|---|---|
| RB-1 | 两处父级手势劫持的**实际派发结果** | 静态只能给语义推断；本片未动结构，需真机确认现状 |
| RB-2 | 长按命中区是否达到 48dp 触达标准 | 需真机量测 |
| RB-3 | Toast 在 TalkBack 下是否播报 | 需开 TalkBack |
| RB-4 | 切换单位后**所有**页面是否立即重绘 | 本片已把 `durationUnit` 加入 `YearBillHeroCard` 的 `remember` key，但 count-up 动画中间值、`LazyColumn` 复用项仍需真机确认 |
| RB-5 | 冷启动 / 后台返回后单位是否保持 | `WhileSubscribed(5_000)` 时序 |
| RB-6 | 快速连点 / 双指长按是否丢切换（P3-1） | 需压测 |
| **RB-8（本片新增）** | **`TICK` 形态在真机 10sp 下是否确实不截断** | 我用字符数（≤4）与物理上界（744 小时）做了静态预算，但真实字宽与字体缩放需真机确认 |
| **RB-9（本片新增）** | **`YearBillHeroCard` subtitle 由 `"30 小时 2026"` 变为 `"30 小时 2026"`/`"1 天 2026"` 后是否与主值视觉重复** | DAYS 模式下主值与 subtitle 同源同文，可能出现「1 天 … 1 天 2026」的重复观感。**需产品裁决是否接受** |

---

## 9. 开始前 / 结束后的 `git status --short`

### 开始前（本片开工时）

```
$ git rev-parse --short HEAD
5aed593
$ git status --short | wc -l
378
```
（工作区含其他窗口的大量既有改动，全部视为用户资产，未触碰。）

### 结束后

```
$ git rev-parse --short HEAD
5aed593
$ git status --short | wc -l
378
```

本片涉及的 14 个文件状态：

```
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/home/components/HomeMetricsSection.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/home/components/HomeReadingArchiveSection.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileHomeScreen.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/profile/ProfileUtils.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/StatsPage.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/components/CreationSection.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/components/HeatmapSection.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/components/SummaryGroup.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/components/TrendSection.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/components/YearBillHeroCard.kt
 M android/app/src/main/java/com/creationreadingassistant/ui/util/TimeUtils.kt
 M android/app/src/test/java/com/creationreadingassistant/ui/screen/home/HomeReadingArchiveSectionTest.kt
?? android/app/src/main/java/com/creationreadingassistant/ui/util/DurationDisplay.kt
?? android/app/src/test/java/com/creationreadingassistant/ui/util/TimeUtilsTest.kt
```

> 后两个是**未跟踪**文件：`DurationDisplay.kt` 与 `TimeUtilsTest.kt` 在基点 `5aed593` 中不存在（S15 调查期间由窗口 D 新建，尚未提交）。我修改了它们的内容，但 `git status` 仍显示 `??`。**这不是我新建的文件**，我未把它们加入索引。

`git diff --cached --name-only` = **0**（无 staged 变更）。

---

## 10. 本片文件 SHA-256

```
0e443c8f0f005ef2ae8e1639e5062101b0b7aad6fafbd8573ae44f89f476ac9e  main/java/com/creationreadingassistant/ui/util/TimeUtils.kt
5353554cb7d63116fb696e7289326a507a1d4c0eea19e3926ed2fdc4713f67d3  main/java/com/creationreadingassistant/ui/util/DurationDisplay.kt
6f6edcb2afc5d90d0403d39574b50c8739cc611f4ec0bec591aab756b7ea695e  main/java/com/creationreadingassistant/ui/screen/home/components/HomeMetricsSection.kt
dbc3b08f288271972366f1fcddeb63d55f3a22328a263f4f1eeae64809405274  main/java/com/creationreadingassistant/ui/screen/home/components/HomeReadingArchiveSection.kt
a12cb6c6a571b1c4e72043fd794f6442f3bab3cf3d3ae87a5ffaffef79aea67c  main/java/com/creationreadingassistant/ui/screen/stats/StatsPage.kt
33033adcde4b96f45b41a103242f2a801753e77a4d64b8e2c9827f39e8a26eec  main/java/com/creationreadingassistant/ui/screen/stats/components/SummaryGroup.kt
58710482fcd93a19a94d1959abc6b33002f4f9c883ccfb59463d290f9a8bc5cd  main/java/com/creationreadingassistant/ui/screen/stats/components/CreationSection.kt
1a7b6ba6af909d806e24503f273153955ec7339708aaf2bae29e478654bb4f7d  main/java/com/creationreadingassistant/ui/screen/stats/components/HeatmapSection.kt
8120d41fe13cd124c749757520321ef56d8b233ba43d02923541aec73a4f4ec4  main/java/com/creationreadingassistant/ui/screen/stats/components/TrendSection.kt
405fcf0cbff4afd0cad29f6feee8638b1cb666ef7ac96083860666dcf500a74a  main/java/com/creationreadingassistant/ui/screen/stats/components/YearBillHeroCard.kt
2b8b3d324eb1a27f3e98514d19df0baff646e0a927315b5f0359d9f6543e3ece  main/java/com/creationreadingassistant/ui/screen/profile/ProfileHomeScreen.kt
f82ca4ba0cacd9729759fb0cf8818b5a4663c12837242485ad18cbfde1ea49b3  main/java/com/creationreadingassistant/ui/screen/profile/ProfileUtils.kt
31fee944a39c6e1b01f80e78ca84497ea03c8eef978fe35ec3ff99fb5824123c  test/java/com/creationreadingassistant/ui/util/TimeUtilsTest.kt
4541360236e70e8cde1e3244d7ff6134cdbceda17c09993db415eabef060dce2  test/java/com/creationreadingassistant/ui/screen/home/HomeReadingArchiveSectionTest.kt
```

（路径相对 `android/app/src/`。）

---

## 11. 明确声明

- **未修改原仓库** `D:\develop\Code\Codex\creation-reading-assistant`：本片所有读写均在 `…-recovery-20260919` 内。
- **未 commit / push / stage**：`git diff --cached --name-only` = 0。
- **未 reset / checkout / clean / stash / 全仓格式化**；未恢复、覆盖或重写任何其他窗口的改动。
- **未触碰**窗口 C 的点击热区文件、`android/scripts/**`、协调看板、handoff、Obsidian。
  - 佐证：`android/scripts/install_with_confirm*.ps1` mtime = `02:59` / `03:01`，本片文件 mtime = `03:54`~`04:33`，本片未写这些路径。
- **未修改 `DurationPrefs.kt` 与 `DurationUnitTest.kt`**（归窗口 D）：mtime 分别为 `23:41` / `23:37`，均早于本片开工。
- **未运行** ADB、真机操作、APK 安装、MuMu、全量 JVM、Lint。
- **未增加振动**、**未改父子手势结构**、**未扩大**到 S15-F3。
- 未处理尚未证实的父子手势劫持；未对 S15 §10 的 P-1…P-6 作产品决定（§8 RB-9 是新增待裁决项）。
- 测试只覆盖 JVM 纯函数与既有相关包；**未新增 Compose / androidTest 用例**。

---

## 12. 给 Codex 的复核清单

1. 确认 `formatDuration` 三样式**共用同一套阈值与舍入**（`TimeUtils.kt`，无第二处 `round` / `秒` / 小数小时）。
2. 确认 §2 的 24 处清单覆盖了你指出的 TrendSection 柱顶与 YearBillHeroCard 的 subtitle/金句（后三者各为一个独立节点）。
3. 复核 `StatsPage.formatCompactDuration` 保留为**纯转发**是否可接受（而非删除改名牵动 5 处 import）。
4. 复核 §4 对 4 个转发函数的删留判断。
5. **独立复跑**：`TimeUtilsTest`（16 项）与 §6.2 的 71 项定向套件，确认 0 失败。
6. 裁决 **RB-9**：DAYS 模式下 `YearBillHeroCard` 主值与 subtitle 同源同文（如「1 天」+「1 天 2026」）是否可接受。
7. 裁决 **RB-8**：`TICK` 形态（`"45分"`/`"2时"`/`"1天"`）的真机字宽是否确实不截断。

---

**本片实施完成，等待 Codex 独立复核。未领取下一项。**
