# S15 只读静态调查：阅读时长单位、展示位与长按切换 — Qoder

> 调查 Agent：Qoder（窗口 D）　|　声明模型：`qwen3.8Flash`
> 任务编号：S15-INVESTIGATION（严格只读静态调查，不实现修复）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`　固定基点：`origin/main = 5aed5939600e491843e3c44453921bd6273a233c`
> 开工 `git rev-parse --short HEAD` = `5aed593`
> 唯一产物：`docs/coordination/investigations/agent-investigation-s15-qoder.md`（本文件）
> 口径：所有结论来自当前工作树源码；**本轮未运行 Gradle / 测试 / ADB / 手机**，因此不声称任何"测试已通过"。事实 / 推断 / 未验证在正文分别标注。

---

## 1. Executive Summary

1. **展示位总数 = 20**（本轮从当前代码重新枚举，不沿用旧文档的"12 个"说法）。其中 **13 个跟随全局 `DurationUnit`**，**7 个完全不读该偏好**。`ui/util/DurationDisplay.kt:23` 的注释仍写"6 个页面的 12 个展示位"，与现状不符（见 §8 P3-5）。
2. **13 个跟随单位的展示位里，12 个可长按切换，1 个不能**（`ProfileHomeScreen.kt:96-103` + `:201`，自带 `when` 分支且未挂 `durationUnitToggle()`）。
3. **7 个漏接展示位集中在统计页（5）与首页（2），并且各自用了不同的私有 formatter**，导致：
   - 首页同一屏内出现两种格式：`HomeMetricsSection.kt:98` 的 `"3h 20m"`（英文）与 `HomeReadingArchiveSection.kt:134` 的 `"1 小时 30 分钟"`（中文）——两者都由 `HomeScreen.kt:118` / `:139` 渲染；
   - 统计页用小数小时 `"1.5 小时"`（`StatsPage.kt:175-184`），与公共 formatter 的 `"1 小时 30 分钟"` 契约不同。
4. **同一个数值最多有 4 种显示结果**（见 §5 对照表）。30 秒：`"0 分钟"`（公共）/ `"1 分钟"`（首页档案）/ `"30 秒"`（首页指标）/ `"0 分钟"`（统计）。1 小时整：`"1 小时 0 分钟"`（公共、我的首页）/ `"1 小时"`（首页档案、统计）/ `"1h 0m"`（首页指标）。
5. **状态与持久化链是健康的**：单一 DataStore key、脏值回落 HOURS、`toggle()` 刻意重读 DataStore 规避 `WhileSubscribed` 过期值、根部 `CompositionLocalProvider` 提供 ⇒ 切换后当前页面立即重组、重启保持。这些**由源码保证**（§2、§3）。唯一未验证项是并发长按的读-改-写竞态（§8 P3-1）。
6. **长按手势存在两处真实的父级交互劫持**（`combinedClickable` 的 `onClick = {}` 会消费点击）：
   - `MyReadingScreen.kt:565` 的 `DurationText` 位于 `:488` 的 `combinedClickable(onClick = onOpen, onLongClick = onManage)` 卡片内 ⇒ 长按时长文字切单位而**不打开管理菜单**，点击时长文字**不打开书**；
   - `BookDetailStatsSection.kt:270` 位于 `:244` 的 `clickable { itemExpanded = !itemExpanded }` 行内 ⇒ 点击时间 pill 不展开。
   按 Compose 指针派发语义这是预期行为，但**具体派发结果仍需真机确认**（§9）。
7. **无障碍缺口是仓库内可对照的，不是推测**：`combinedClickable` 未传 `onLongClickLabel`，而同仓库 `shelf/BookGrid.kt:282` 已经在用该参数；`DurationDisplay.kt:51-59` 也没有触觉反馈，而全应用有 216 处 `haptic(...)` 调用。
8. **测试只覆盖纯函数**：`TimeUtilsTest` 6 例覆盖 formatter，`DurationUnitTest` 2 例覆盖 `fromStorage`，`HomeReadingArchiveSectionTest` 1 例覆盖一个私有 formatter。**`DurationPrefs` 本身（DataStore 往返、`toggle()`、`setUnit()`）零测试**，`LocalDurationUnit` 注入、长按回调、单位一致性、重启保持全部零测试。
9. **正确排除**的固定分钟/时钟/相对时间/计数类共 17 组（§4），未计入"漏接"。

---

## 2. 持久化与状态传播链

| 环节 | 位置 | 事实 | 保证级别 |
|---|---|---|---|
| DataStore 实例 | `data/settings/DurationPrefs.kt:45` | 独立 store `duration_prefs`（不复用 `app_settings`） | 源码 |
| key | `:47` | `stringPreferencesKey("duration_unit")`，存 `storageValue`（`"hours"` / `"days"`，`:30-34`） | 源码 |
| 默认值 | `:65` | `stateIn(..., DurationUnit.HOURS)`；`fromStorage` 对 null / 空 / `"Days"` / 任意脏值一律回落 HOURS（`:38-41`） | 源码 + 测试（`DurationUnitTest:20-25` 逐值断言，含大小写陷阱 `"Days"`） |
| StateFlow 启动 | `:63-65` | `SharingStarted.WhileSubscribed(5_000)`，作用域 `@ApplicationScope` | 源码 |
| 根部提供 | `MainActivity.kt:78` `collectAsStateWithLifecycle()` → `:128-131` `CompositionLocalProvider(LocalDurationUnit provides durationUnit, LocalDurationUnitToggle provides toggleDurationUnit)` | Activity 处于 STARTED 期间始终有订阅者 ⇒ 实际值实时；`WhileSubscribed` 停流只发生在后台 | 源码 |
| 未提供时回落 | `ui/util/DurationDisplay.kt:33` `staticCompositionLocalOf { DurationUnit.HOURS }`；`:40` toggle 默认空实现 | 测试/预览不崩 | 源码 |
| 长按 → 切换 | `DurationDisplay.kt:51-59` `Modifier.durationUnitToggle()` → `combinedClickable(onClick = {}, onLongClick = toggle)` | `toggle` 即 `MainActivity.kt:81-96` 的 lambda | 源码 |
| `toggle()` 实现 | `DurationPrefs.kt:78-83` | **从 `ds.data.first()` 重读**再取反，刻意不读 `unit.value`；注释 `:74-76` 说明理由（无订阅者时 `value` 恒为初始 HOURS） | 源码；**无测试** |
| 切换回执 | `MainActivity.kt:85-93` | `Toast`（`"阅读时长已改为按「天」显示"` / 「小时」）；注释 `:76-77` 说明"各页面 SnackbarHost 互不相通"所以用 Toast | 源码 |
| 外观页开关 | `ui/screen/profile/AppearanceSubPage.kt:152-159` | `SettingSwitchRow(checked = durationUnit == DurationUnit.DAYS, onCheckedChange = { toggleDurationUnit() })` ⇒ 与长按**共用同一入口**，无第二状态源 | 源码 |
| 切换后当前页是否立即重组 | — | `LocalDurationUnit` 的值来自 `MainActivity` 的 `collectAsStateWithLifecycle`；新值 ⇒ CompositionLocal 变更 ⇒ 所有 `LocalDurationUnit.current` 读取点与 `DurationText` 重组 | 源码推断（**需真机确认无遗漏的 `remember` 缓存**） |
| 重启保持 | `DurationPrefs.kt:63-65` + DataStore | 冷启动 `stateIn` 初值 HOURS，随后 DataStore 首值覆盖 | 源码；**无测试**（无 DataStore 往返测试） |
| 并发/快速切换 | `DurationPrefs.kt:78-83` | 读-改-写非原子：两个协程都读到 HOURS ⇒ 都写 DAYS ⇒ 丢一次切换。`ds.edit` 只序列化写，不保护这次读 | 源码推断；**无测试、需真机压力验证** |
| 无订阅者旧值风险 | `:65` + `:74-76` | `toggle()` 已规避；`unit` 的展示值在重新订阅时从 DataStore 重放 ⇒ 不会出现"切到天之后界面还是小时" | 源码 |

**小结**：状态链设计是清醒的（尤其 `toggle()` 重读 DataStore 这一点，注释明确记录了为什么不用 `StateFlow.value`）。风险集中在**没有测试**与**读-改-写竞态**，不在设计。

---

## 3. 用户可见展示位完整矩阵

数据源单位统一为 **毫秒（ms）**，除非另注。

### A. 跟随全局单位 + 可长按（12）

| # | 页面 / 用户入口 | 文件:行 | 数据源 | formatter | 读单位 | toggle |
|---:|---|---|---|---|---|---|
| 1 | 我的 → 阅读档案：聚合「累计 …」 | `profile/ReadingArchiveSection.kt:225-226` | `totalMs`（ms） | `ui.util.formatDuration` | ✅ | ✅ |
| 2 | 书架 → 书籍详情：「总阅读时长」 | `shelf/BookDetailStatsSection.kt:75-81` | `bookDetailReadingTimeMs(progress.total_reading_time_ms, sessions)`（ms） | `ShelfUtils.formatDuration`（`:18-19` 转发） | ✅ | ✅（`valueModifier`，挂在 `:470` 的 value `Text` 上） |
| 3 | 书架 → 书籍详情：单条阅读记录 pill | `BookDetailStatsSection.kt:270-276` | `session.duration_ms` | `DurationText` | ✅ | ✅ |
| 4 | 阅读器 → 书籍信息：「本次已读」 | `reader/sheets/ReaderBookInfoSheet.kt:217-221` | `activeReadingMs` | `ReaderHelpers.formatDuration`（`:46-47` 转发） | ✅ | ✅（`valueModifier` → `:470` value Text） |
| 5 | 阅读器 → 书籍信息：「累计阅读」 | `ReaderBookInfoSheet.kt:225-229` | `savedReadingMs` | 同上 | ✅ | ✅ |
| 6 | 阅读器 → 进度弹层：「累计」 | `reader/sheets/ReaderProgressSheet.kt:173-174` | `savedBookReadingMs + activeReadingMs` | 同上 | ✅ | ✅ |
| 7 | 阅读器 → 进度弹层：「本次已读」 | `ReaderProgressSheet.kt:248-249` | `activeReadingMs` | 同上 | ✅ | ✅ |
| 8 | 阅读器 → 进度弹层：「预计读完」 | `ReaderProgressSheet.kt:276-285` | `estimatedRemainingMs`（≤0 时显示 `"—"`，不格式化） | 同上 | ✅ | ✅ |
| 9 | 首页 → 已读完档案：头部聚合 | `homearchive/HomeCompletedScreen.kt:243-248` | `totalReadingTimeMs` | `DurationText` | ✅ | ✅ |
| 10 | 首页 → 已读完档案：每本 | `HomeCompletedScreen.kt:516-521` | `item.progress.total_reading_time_ms` | `DurationText` | ✅ | ✅ |
| 11 | 我的 → 阅读历史：聚合 | `readinghistory/MyReadingScreen.kt:246-251` | `totalReadingMs` | `DurationText` | ✅ | ✅ |
| 12 | 我的 → 阅读历史：每本书 | `MyReadingScreen.kt:565-569` | `item.readingTimeMs` | `DurationText` | ✅ | ✅ |

### B. 跟随全局单位但**不可长按**（1）

| # | 页面 / 入口 | 文件:行 | 数据源 | 实现 | 读单位 | toggle |
|---:|---|---|---|---|---|---|
| 13 | 「我的」首页 → 档案卡统计行「阅读时长」 | 计算 `ProfileHomeScreen.kt:96-103`，渲染 `:201` `ArchiveStat(durationText, "阅读时长")` | `state.homeSummary.totalDurationMs`（`ProfileViewModel.kt:236` = `sessions.sumOf { duration_ms }`） | **自带 `when` 分支**，未调用 `formatDuration`，未挂 `durationUnitToggle()` | ✅ | ❌ |

> 该分支的输出与公共 `formatDuration` 在正常值下逐字一致（DAYS 且 ≥1440 分 ⇒「N 天 M 小时」；≥60 分 ⇒「H 小时 M 分钟」；否则「M 分钟」）。差异只在负值：`formatDuration` 有 `ms <= 0 → "0 分钟"` 保护（`TimeUtils.kt:30`），而 `:97` 的 `(ms/60000).toInt()` 为负时会输出 `"-1 分钟"`。**实际不可达**（聚合自非负 session），列为维护风险。

### C. **完全不读全局单位**（7 个展示位，3 个私有 formatter）

| # | 页面 / 入口 | 文件:行 | 数据源 | formatter | HOURS 模式 | DAYS 模式 |
|---:|---|---|---|---|---|---|
| 14 | 首页 →「今日」指标卡 | `home/components/HomeMetricsSection.kt:98`，实现 `:162-169` | `todayReadingMs` → `(ms/60000).toInt()` → count-up → `*60000L`（**量化到整分钟**） | `private formatCompactDuration` | `"3h 20m"` / `"45 分钟"` / `"N 秒"` | **同左（不响应）** |
| 15 | 首页 → 阅读档案卡聚合 | `home/components/HomeReadingArchiveSection.kt:134`，实现 `:159-170` | `totalReadingMs` | `internal formatArchiveDuration` | `"1 小时 30 分钟"` / `"2 小时"` | **同左** |
| 16 | 统计页 → 摘要「阅读时长」 | `stats/components/SummaryGroup.kt:71` | `animatedMinutes * 60000` | `stats/StatsPage.kt:175-184` `internal formatCompactDuration` | `"1.5 小时"` / `"30 分钟"` / `"2 小时"` | **同左** |
| 17 | 统计页 →「沉浸时长」 | `stats/components/CreationSection.kt:86` | `stats.totalReadingMs` | 同上 | 同上 | **同左** |
| 18 | 统计页 → 热力图汇总 | `stats/components/HeatmapSection.kt:114` | `totalMs`（365 天） | 同上 | 同上 | **同左** |
| 19 | 统计页 → 热力图单日详情 | `HeatmapSection.kt:399` | `cell.durationMs` | 同上 | 同上 | **同左** |
| 20 | 统计页 → 年度报告「累计阅读」 | `stats/components/YearBillHeroCard.kt:102`（另有 `:104` subtitle `"$hours 小时 $anchorYear"`、`:213`、`:217` 三处硬编码「小时」文案） | `hours`（小时整数）回乘成 ms 再格式化 | 同上 + 字符串拼接 | `"1 天 6 小时"` 不可能出现；只有 `"30 小时"` | **同左** |

> 站点 14 的 `"N 秒"` 分支在其唯一调用点上**不可达**（传入值恒为 `分钟 × 60000`）。

**合计：20 个用户可见阅读时长展示位；13 跟随单位（12 可长按），7 不跟随。**

---

## 4. 正确排除的「固定分钟 / 非时长」项目

以下均**不是** `DurationUnit` 的作用范围，不计入漏接：

| 项 | 位置 | 为什么正确排除 |
|---|---|---|
| 每日阅读目标分钟 | `profile/GoalSubPage.kt:236`、`:246`、`:281`、`:286`、`:356-358`；`profile/ProfileHomeScreen.kt:234`；`home/components/HomeMetricsSection.kt:95`（`"今日 /${dailyGoalMinutes}分"`）；`stats/components/GoalRingSection.kt:176`、`:178`；`feature/goal/ReadingGoalDecision.kt:42`、`:46` | 目标是**用户设定的阈值**，单位是产品契约里的"分钟"；改成"0.5 小时"反而不可输入 |
| 目标进度差分钟 | `GoalSubPage.kt:185`、`:286` | 同上 |
| 护眼提醒间隔 | `profile/ReaderSubPage.kt:727`；`reader/sheets/ReaderEyeCareSettings.kt:146`；`reader/ReaderProgressEffects.kt:158` | 间隔配置，非累计时长 |
| 阅读节奏提醒间隔 | `ReaderSubPage.kt:750`；`ReaderEyeCareSettings.kt:169`；`ReaderProgressEffects.kt:165` | 同上 |
| 菜单自动隐藏选项 | `ReaderSubPage.kt:884`（`0/15/30/60 分钟`） | 交互超时配置 |
| TTS 睡眠定时 | `reader/tts/ReaderTtsBar.kt:220`、`:233`、`:578` | 倒计时配置 |
| 通知提前分钟 | `GoalSubPage.kt:512`、`:530`（`contentDescription = "提前 30 分钟"`） | 提醒偏移量 |
| 相对时间 | `ui/util/TimeUtils.kt:61-91` `formatRelativeTime`（"刚刚 / N 分钟前 / N 小时前 / 昨天 / N 天前"） | 语义是"距今多久"，不是"累计多久"；换成"天"会破坏自然语言 |
| 档案相对时间 | `homearchive/HomeArchiveFormatters.kt:92`（`"${hours}小时前"`） | 同上 |
| 同步耗时 | `profile/ProfileUtils.kt:29-33` `formatSyncDuration`（`ms` / `秒` / `分秒`） | 操作耗时诊断，量级为秒；与阅读时长无关 |
| 天数计数 | `stats/components/SummaryGroup.kt:79`、`:154`、`:164`；`CreationSection.kt:93`；`HeatmapSection.kt:101`、`:114` 的 `"$activeDays 天开卷"`；`YearBillHeroCard.kt:110`、`:132`；`GoalSubPage.kt:586` | 「开卷天数 / 连续天数」是**计数**，不是时长换算 |
| 阅读速度 | `stats/StatsPage.kt:314-315`；`ReaderProgressSheet.kt:268-272`（`字/分`） | 速率，单位固定 |
| 时钟时间 | `ProfileUtils.kt:12-24` `formatDateTime` / `formatDateTimeShort` | 时刻不是时长 |
| 动画时长 | 各 `tween(...)` / `Duration` 常量 | 渲染参数 |
| 章节数 / 本数 / 灵感数 | 各 `"$x 本"`、`"$x 条"` | 非时长 |
| 书籍详情 7 日柱状图 | `shelf/BookDetailStatsSection.kt:340-386` | 只渲染柱高与日期标签，**不输出时长文本** |
| Kotlin `kotlin.time.DurationUnit` | `main/baseline-prof.txt:22260+` | 与本产品枚举同名但无关（baseline profile 符号表） |

---

## 5. formatter 与舍入规则对照

### 5.1 实现清单

| 实现 | 位置 | 是否单位感知 | 舍入 | 输出形态 |
|---|---|---|---|---|
| 公共 `formatDuration` | `ui/util/TimeUtils.kt:29-44` | ✅ | **向下取整**（`ms / 60_000`） | HOURS：`"H 小时 M 分钟"` / `"M 分钟"`；DAYS：`"D 天 H 小时"`（≥24h）/ 其余同 HOURS；`ms<=0` → `"0 分钟"` |
| `ShelfUtils.formatDuration` | `shelf/ShelfUtils.kt:18-19` | 转发 | 同上 | 纯转发，**无害** |
| `ReaderHelpers.formatDuration` | `reader/ReaderHelpers.kt:46-47` | 转发 | 同上 | 纯转发，**无害** |
| `ProfileUtils.formatDuration` | `profile/ProfileUtils.kt:26-27` | 转发 | 同上 | **无任何调用者**（`profile` 包内唯一调用点 `ReadingArchiveSection.kt:50` 直接 import `ui.util.formatDuration`）⇒ 死代码 |
| `DurationText` | `ui/util/DurationDisplay.kt:67-86` | ✅（内部读） | 同公共 | 组件封装 |
| `ProfileHomeScreen` 内联 `when` | `profile/ProfileHomeScreen.kt:98-103` | ✅ | 向下取整 | 与公共一致，但**无 `ms<=0` 保护**、无 toggle |
| `formatArchiveDuration` | `home/components/HomeReadingArchiveSection.kt:159-170` | ❌ | 向下取整 + **`coerceAtLeast(1)`**（正数不足 1 分钟强制"1 分钟"） | `"M 分钟"` / `"H 小时"`（整点省略分钟）/ `"H 小时 M 分钟"` |
| `HomeMetricsSection.formatCompactDuration` | `home/components/HomeMetricsSection.kt:162-169` | ❌ | **四舍五入**（`round(ms/60_000.0)`）；<1 分钟另有 `round(ms/1000)` 秒分支 | `"Nh Mm"`（**英文**）/ `"M 分钟"` / `"N 秒"` |
| `StatsPage.formatCompactDuration` | `stats/StatsPage.kt:175-184` | ❌ | 向下取整 + **十分位进位**（`round(rem/60*10)`，≥10 进 1 小时） | `"M 分钟"` / `"H 小时"`（整点）/ `"H.T 小时"`（小数） |

### 5.2 同值对照（真实计算，非推测）

| 输入 ms | 公共 HOURS | 公共 DAYS | 首页档案 | 首页指标 | 统计页 | 我的首页 |
|---|---|---|---|---|---|---|
| 30,000（30 秒） | `0 分钟` | `0 分钟` | `1 分钟` | `30 秒` | `0 分钟` | `0 分钟` |
| 164,875（2 分 45 秒） | `2 分钟` | `2 分钟` | `2 分钟` | `3 分钟` | `2 分钟` | `2 分钟` |
| 3,600,000（1 小时整） | `1 小时 0 分钟` | `1 小时 0 分钟` | `1 小时` | `1h 0m` | `1 小时` | `1 小时 0 分钟` |
| 5,430,000（1.508 小时） | `1 小时 30 分钟` | `1 小时 30 分钟` | `1 小时 30 分钟` | `1h 31m` | `1.5 小时` | `1 小时 30 分钟` |
| 108,000,000（30 小时） | `30 小时 0 分钟` | `1 天 6 小时` | `30 小时` | `30h 0m` | `30 小时` | `30 小时 0 分钟` |
| 172,800,000（48 小时） | `48 小时 0 分钟` | `2 天 0 小时` | `48 小时` | `48h 0m` | `48 小时` | `48 小时 0 分钟` |
| 0 | `0 分钟` | `0 分钟` | `0 分钟` | `0 分钟` | `0 分钟` | `0 分钟` |
| −1 | `0 分钟` | `0 分钟` | `0 分钟` | `0 分钟` | `0 分钟` | `"-0 分钟"`（`(-1/60000).toInt()`=0，实际输出 `0 分钟`） |

**结论**：
- 单位切换对 7 个站点无效（事实）；
- 即使只看 HOURS 默认模式，**同一本书的时长在首页 / 统计页 / 详情页也会不同**（`1h 31m` vs `1.5 小时` vs `1 小时 30 分钟`）；
- `"1 小时 0 分钟"`（公共）与 `"1 小时"`（首页档案、统计页）是**产品契约层面的分叉**，不是舍入误差 —— 需要 §10 的裁决。

---

## 6. 长按手势、父级交互与无障碍矩阵

### 6.1 实现本体

`ui/util/DurationDisplay.kt:49-59`：

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.durationUnitToggle(): Modifier {
    val toggle = LocalDurationUnitToggle.current
    return combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = {},
        onLongClick = toggle,
    )
}
```

事实核对：

| 检查点 | 结果 | 证据 |
|---|---|---|
| 手势类型 | `combinedClickable`（Foundation 实验 API） | `:53` |
| 是否消费普通点击 | **是** —— `onClick = {}` 仍会声明并消费 tap | `:56`；后果见 §6.2 |
| 挂载位置 | 挂在**时长文本自身**（`Text` 的 `modifier`），不是整行或卡片 | `DurationText` `:79`；`ReaderBookInfoSheet:470`；`BookDetailStatsSection:470`；`ReaderProgressSheet:174/249` |
| 是否有 ripple/视觉反馈 | 无（`indication = null`，注释 `:44-47` 说明理由） | `:55` |
| 是否有触觉反馈 | **无**。全应用有 216 处 `haptic(HapticFeedbackType…)` 调用，`DurationDisplay.kt` 一处都没有 | grep 结果 |
| 是否有切换结果反馈 | **有**：`Toast`（`MainActivity.kt:85-93`），文案含目标单位 | 源码 |
| 是否声明 `onLongClickLabel` | **未声明**。同仓库 `shelf/BookGrid.kt:282` 已使用 `onLongClickLabel = "打开书籍操作"` ⇒ 该 API 可用且项目已有惯例 | 对照 |
| 长按区域尺寸 | 多为 `labelSmall` / `bodySmall` 文本（如 `ReadingArchiveSection.kt:225` 的 11sp、`ReaderProgressSheet.kt:173` 的 labelSmall），命中区仅文字本身 | 源码 |
| 与阅读器正文选区长按是否冲突 | **不冲突**：展示位全部位于 sheet / 页面卡片内，阅读器正文长按在 `ReaderScaffold` 层，sheet 打开时不接收该手势（推断，需真机确认） | — |
| 与书架卡片长按是否冲突 | 书架网格卡片的长按在 `BookGrid.kt:282`，与时长展示位不在同一节点 | 源码 |

### 6.2 父级交互冲突（两处，均现役可达）

**冲突 1 —— `MyReadingScreen`（我的 → 阅读历史）**

- 父级：`:485-490` `IslandCard(modifier = Modifier.weight(1f).combinedClickable(onClick = onOpen, onLongClick = onManage).testTag("reading-item-${item.book.id}"))`
- 子级：`:565-569` `DurationText(ms = item.readingTimeMs, …)`（位于该卡片内部第 3 个 `Row`）
- 后果（按 Compose 指针派发语义推断，**需真机确认**）：
  1. 长按时长文字 ⇒ 子级消费 ⇒ **切换单位，不打开"管理"菜单**；
  2. 点击时长文字 ⇒ 子级 `onClick = {}` 消费 ⇒ **不打开书籍**。
- 用户可见影响：列表项上有一小块"点了没反应、长按会改全局设置"的区域。面积小但位置显眼（每本书卡片右下角的时长 pill）。

**冲突 2 —— `BookDetailStatsSection`（书架 → 书籍详情 → 阅读记录行）**

- 父级：`:237-244` `Surface(modifier = … .clickable { itemExpanded = !itemExpanded })`
- 子级：`:270-276` `DurationText(ms = session.duration_ms, …)`
- 后果：点击时间 pill 不展开该行；长按切换单位。影响面小于冲突 1（行内其他区域仍可点）。

**非冲突但需记录**：`ProfileHomeScreen.kt:201` 的 `ArchiveStat(durationText, "阅读时长")` **完全没有 toggle**，用户在该页长按无效 —— 与"任意时长文本长按即可切换"的产品承诺（`DurationPrefs.kt:23`）不一致。

### 6.3 可发现性

| 检查点 | 结果 | 证据 |
|---|---|---|
| 外观页是否告知可长按 | ✅ 有 | `AppearanceSubPage.kt:155` subtitle：`"关闭后按「小时」显示；长按时长文本可切换"` |
| 设置搜索能否找到 | ✅ 能 | `SettingsSearchIndex.kt:219-220`：`SettingsEntry("appearance_duration_day", "时长按「天」显示", …, keywords = "时长 单位 天 小时 换算")` |
| 时长文本本身是否有首次提示 / 视觉可供性 | ❌ 无 | 无下划线、无图标、无 tooltip、无引导；`OnboardingOverlay`（`MainActivity.kt:135-137`）未涉及时长单位（未逐行核对引导内容，列为待确认） |
| 提示与实际是否一致 | ⚠️ **不一致** | 提示说"长按时长文本可切换"，但 7 个展示位（统计页全部 + 首页两处）与「我的」首页档案行长按均无反应 |
| 中文文案是否统一 | ⚠️ 部分 | 单位词「小时 / 天 / 分钟」在 4 套 formatter 中组合方式不同；`"Nh Mm"` 为英文（§5） |

---

## 7. 测试覆盖矩阵

| # | 行为 | 现有测试 | 真实覆盖程度 |
|---:|---|---|---|
| 1 | `DurationUnit.fromStorage` 脏值回落 | `DurationUnitTest.kt:20-25` | ✅ 真实驱动生产函数，含 `"Days"` 大小写陷阱 |
| 2 | `storageValue` 往返 | `DurationUnitTest.kt:14-18` | ✅ |
| 3 | HOURS 不进位到天 | `TimeUtilsTest.kt:19-24` | ✅ 断言 `"30 小时 0 分钟"` |
| 4 | DAYS 24 小时进位 | `TimeUtilsTest.kt:26-30` | ✅ 含 `"1 天 6 小时"`、`"2 天 0 小时"` |
| 5 | DAYS 不足一天 | `TimeUtilsTest.kt:32-35` | ✅ |
| 6 | 不足 1 小时两单位一致 | `TimeUtilsTest.kt:37-41` | ✅ |
| 7 | 分钟余数保留 | `TimeUtilsTest.kt:43-46` | ✅ |
| 8 | 0 / 负数 | `TimeUtilsTest.kt:48-52` | ✅（`0L` 与 `-1L`；**未测 24h 整点、48h+ 的 HOURS 侧、极大值 Long.MAX**） |
| 9 | `formatArchiveDuration`（首页档案） | `HomeReadingArchiveSectionTest.kt:8-14` | ✅ 真实驱动该私有函数，5 个值 |
| 10 | `HomeMetricsSection.formatCompactDuration` | **无**（`private`，无法直接测） | ❌ 零覆盖 ⇒ 英文 `"3h 20m"` 与四舍五入分叉无人守 |
| 11 | `StatsPage.formatCompactDuration` | **无**（grep 全测试目录零命中） | ❌ 零覆盖 ⇒ 小数小时 `"1.5 小时"` 无契约测试 |
| 12 | `ProfileHomeScreen` 内联 `when` | 无 | ❌ |
| 13 | `DurationPrefs.setUnit` / `toggle` **DataStore 往返** | 无（测试目录零引用 `DurationPrefs`） | ❌ **核心持久化零覆盖**；`toggle()` 的"重读 DataStore 规避 WhileSubscribed"这一关键设计没有任何测试防止被改回 `unit.value` |
| 14 | 无订阅者时连续 `toggle` | 无 | ❌ |
| 15 | 并发 / 快速连点 `toggle` | 无 | ❌（且源码存在读-改-写窗口，§2） |
| 16 | 外观页开关 | 无 Compose 测试 | ❌ |
| 17 | `LocalDurationUnit` 注入与回落 | 无 | ❌（`staticCompositionLocalOf` 默认值行为未测） |
| 18 | 所有展示位读取同一单位 | 无 | ❌ **没有任何机制能发现"新增展示位漏接单位"** —— 这正是 §3-C 的 7 个站点长期存在的原因 |
| 19 | 长按切换回调被触发 | 无 | ❌ |
| 20 | 长按与父级点击/导航冲突 | 无 | ❌（§6.2 两处均无测试） |
| 21 | 无障碍 long-click / `onLongClickLabel` | 无 | ❌ |
| 22 | 重启后单位保持 | 无 | ❌ |
| 23 | `androidTest`（Compose / 仪器化） | `grep` 整个 `androidTest` 目录对 `DurationUnit` / `formatDuration` / `DurationText` **零命中** | ❌ 完全缺失 |

**判定**：现有测试**只覆盖 formatter 纯函数**（且只覆盖 4 套实现中的 2 套）。状态链（`DurationPrefs`）、传播（CompositionLocal）、手势（`durationUnitToggle`）、一致性（各站点是否读单位）**全部无测试**。没有任何测试是"源码字符串扫描"式守卫 —— 反过来说，也**没有**这类守卫来阻止新展示位漏接。

---

## 8. 现役问题与严重度

### P1

**P1-1 首页「今日」指标卡输出英文 `3h 20m`，与全应用中文格式冲突**
- 触发路径：打开 App → 首页 → 今日阅读时长 ≥ 1 小时（`HomeScreen.kt:139` → `HomeMetricsSection.kt:98`）。
- 源码证据：`HomeMetricsSection.kt:168` `return if (h > 0) "${h}h ${m}m" else "$m 分钟"`。
- 用户可见影响：中文 UI 里出现 `"3h 20m"`；同屏另一张卡（`:134`）写 `"1 小时 30 分钟"`。
- 为什么是 P1：当前用户可达、无需特殊数据、**与单位偏好无关的显示错误**（即使保持 HOURS 默认也是错的）。
- 最小修复边界：`HomeMetricsSection.kt:162-169` 改调公共 `formatDuration(ms, LocalDurationUnit.current)` 并删私有实现（或至少把 `"Nh Mm"` 改中文）。1 个文件。
- 需要产品裁决：否。

**P1-2 全局「时长按天显示」偏好在 7 个展示位完全失效**
- 触发路径：外观页打开「时长按天显示」（`AppearanceSubPage.kt:152-159`）或长按任意时长文本 → 进入统计页 / 首页。
- 源码证据：`grep LocalDurationUnit` 在 `ui/screen/stats/**`、`ui/screen/home/**` **零命中**；三处私有 formatter 无 `unit` 参数（`StatsPage.kt:175`、`HomeReadingArchiveSection.kt:159`、`HomeMetricsSection.kt:162`）。
- 用户可见影响：用户明确设置的偏好对统计页 5 处 + 首页 2 处无效；同一本书的累计时长在详情页显示 `"1 天 6 小时"`、统计页显示 `"30 小时"`、首页显示 `"30h 0m"`。
- 为什么是 P1：**用户可设置的产品偏好在主要页面不生效**，属"设置与行为不一致"，不是审美问题。
- 最小修复边界：三处 formatter 增加 `unit` 参数并在调用点传 `LocalDurationUnit.current`（7 个调用点 + 3 个函数），或统一改调公共 `formatDuration`。**需要先做 P-1 裁决**（统计页紧凑卡是否允许豁免）。
- 需要产品裁决：是（见 §10 P-1）。

### P2

**P2-1 长按时长文本劫持父级卡片手势（阅读历史）**
- 触发路径：我的 → 阅读历史 → 长按某本书卡片上的时长文字（想打开"管理"）/ 点击该文字（想打开书）。
- 源码证据：父 `MyReadingScreen.kt:488` `combinedClickable(onClick = onOpen, onLongClick = onManage)`；子 `:565` `DurationText` → `DurationDisplay.kt:56` `onClick = {}`。
- 用户可见影响：长按变成切全局单位 + Toast；点击无响应。
- 为什么是 P2：现役可达、行为与用户意图相反，但影响面积小（单个 pill）、无数据损失、可再次长按恢复。
- 最小修复边界：列表项内联 pill 不挂 toggle（只在聚合统计区挂），或改用只消费长按不消费 tap 的实现，或给 pill 单独留非交互内边距。
- 需要产品裁决：是（§10 P-3：长按是否应继续作为唯一切换手势）。

**P2-2「我的」首页档案时长行跟随单位但不能长按，与产品承诺不符**
- 触发路径：设置「时长按天显示」→ 我的首页看档案卡「阅读时长」→ 长按。
- 源码证据：`ProfileHomeScreen.kt:96-103`（自带 `when`）、`:201`（`ArchiveStat` 未传 modifier）；承诺见 `DurationPrefs.kt:22-23`「用户在任意时长文本上长按即可切换」。
- 用户可见影响：该处不能切换；用户会怀疑功能失效。
- 为什么是 P2：承诺与实现不一致，但显示值本身正确（跟随单位）。
- 最小修复边界：`ProfileHomeScreen.kt:98-103` 改调 `formatDuration(totalDurationMs, durationUnit)`，`:201` 传 `Modifier.durationUnitToggle()`。1 个文件。
- 需要产品裁决：否。

**P2-3 同一数值在不同页面显示不同结果（舍入与形态分叉）**
- 触发路径：正常阅读 ≥ 1 小时后对比首页 / 统计页 / 详情页 / 阅读器弹层。
- 源码证据：§5.2 对照表（`round` vs 向下取整、`"1 小时"` vs `"1 小时 0 分钟"`、`"1.5 小时"` vs `"1 小时 30 分钟"`、`"30 秒"` vs `"0 分钟"`）。
- 用户可见影响：用户会认为某一处算错了；跨页核对失败。
- 为什么是 P2：不是崩溃也不是数据错误，但属**可验证的显示正确性不一致**，现役可达。
- 最小修复边界：先由产品定"分钟余数是否必须显示"（§10 P-2），再统一 formatter；否则各页保留紧凑形态但必须共用同一舍入函数。
- 需要产品裁决：是。

### P3

| ID | 问题 | 证据 |
|---|---|---|
| P3-1 | `toggle()` 读-改-写非原子，并发切换可能丢一次 | `DurationPrefs.kt:78-83`（无 Mutex / 不用 `edit { }` 内读旧值）；需真机压力确认 |
| P3-2 | `BookDetailStatsSection.kt:270` 时间 pill 点击不展开父行 | 父 `:244` `clickable { itemExpanded = … }` |
| P3-3 | `durationUnitToggle` 无触觉反馈，与全应用 216 处 `haptic(...)` 惯例不一致 | `DurationDisplay.kt:51-59` |
| P3-4 | 未传 `onLongClickLabel`，TalkBack 无"切换单位"自定义操作标签；同仓库 `BookGrid.kt:282` 已有先例 | `DurationDisplay.kt:53-58` |
| P3-5 | `DurationDisplay.kt:23` 注释"6 个页面的 12 个展示位"与现状（20 个，13 跟随）不符 | §3 |
| P3-6 | `ProfileUtils.formatDuration`（`:26-27`）为死代码（`profile` 包唯一调用点直接 import 公共实现） | §5.1 |
| P3-7 | `ProfileHomeScreen.kt:97` 缺 `ms <= 0` 保护（与公共 formatter 不对称，实际不可达） | §3-B |
| P3-8 | `HomeMetricsSection.formatCompactDuration` 的 `"N 秒"` 分支在唯一调用点不可达（入参恒为整分钟） | `:98` + `:164` |

### 未来维护风险（不计级）

- **没有任何机制能阻止新增长展示位漏接单位**（§7 #18）。当前 3 套私有 formatter 就是历史漏接的存量证据；只要统计页继续新增指标卡，同类问题会重复出现。
- `internal formatArchiveDuration` / `internal formatCompactDuration` 可被同模块任意文件复用，扩大不一致面。

---

## 9. 真机 / readiness blocker

| ID | 项 | 为什么必须真机 |
|---|---|---|
| RB-1 | §6.2 两处父级手势劫持的**实际派发结果** | Compose 子节点 `combinedClickable` 是否真的完全吞掉父级 tap/long-press，取决于命中区与手势竞态；静态只能给语义推断 |
| RB-2 | 长按命中区是否可操作 | 11sp `labelSmall` 文本的实际可长按面积（是否 <48dp 触达标准）只能真机量 |
| RB-3 | Toast 在 TalkBack 下是否播报、是否与 `onLongClickLabel` 缺失叠加导致完全不可发现 | 需开 TalkBack |
| RB-4 | 切换后**所有**页面是否立即重绘（尤其 `remember` 缓存了格式化字符串的位置、`LazyColumn` 复用项、count-up 动画 `rememberCountUp`） | `HomeMetricsSection.kt:88` 与 `SummaryGroup.kt:71` 用 count-up 动画值乘 60000，单位切换后动画值是否重算需真机确认 |
| RB-5 | 冷启动 / 后台返回后单位是否保持 | `WhileSubscribed(5_000)` + `collectAsStateWithLifecycle` 的实际时序 |
| RB-6 | 快速连点 / 双指长按是否丢切换（P3-1） | 竞态需压测 |
| RB-7 | 阅读器正文长按与 sheet 内长按是否有交叠 | 静态只能推断 |

---

## 10. 产品待裁决项

| # | 问题 | 影响 | 备注 |
|---:|---|---|---|
| P-1 | **统计页与首页指标卡是否豁免全局单位**（紧凑卡刻意用 `"30 小时"` / `"1.5 小时"` 省空间） | 决定 P1-2 是"修 7 处"还是"承认豁免并改文案承诺" | 若豁免，应把 `DurationPrefs.kt:22-23` 与 `AppearanceSubPage.kt:155` 的"任意时长文本"承诺改为"统计类时长" |
| P-2 | **分钟余数是否必须显示**：`"1 小时"` vs `"1 小时 0 分钟"`；小数 `"1.5 小时"` 是否允许 | 决定 §5 分叉是 bug 还是设计 | 公共 formatter 的注释（`TimeUtils.kt:25-27`）已给出理由（59 分钟与 1 小时不能看起来相同），但未覆盖"整点是否省略" |
| P-3 | **长按是否是唯一切换入口**，是否补显式入口（下拉菜单 / 卡片角标 / 双击） | 决定 P2-1 的修法形态 | 外观页开关已存在，可考虑"长按降为彩蛋"或"仅聚合区可长按" |
| P-4 | 不足 1 分钟显示什么（`"0 分钟"` / `"1 分钟"` / `"30 秒"`） | 决定 P2-3 | 现状 4 种 |
| P-5 | 英文 `h/m` 是否属于设计意图 | 决定 P1-1 是否可降级 | 我判断不是（全应用中文），但需产品确认 |
| P-6 | 「天」模式下是否显示分钟（`"1 天 6 小时"` 丢分钟，`"1 天 6 小时 20 分钟"` 太长） | `TimeUtils.kt:39` 当前丢分钟，与 `:25-27` 注释的"两者都保留分钟尾数"**自相矛盾** | 事实性文档/实现冲突，建议一并裁决 |

---

## 11. 推荐的最小后续切片

按 ROI 排序，每片可独立验收：

1. **S15-F1（纯显示修复，无裁决依赖）**：`HomeMetricsSection.kt:162-169` 改中文（P1-1）；`ProfileHomeScreen.kt:98-103`/`:201` 改调公共 formatter 并挂 toggle（P2-2）；删 `ProfileUtils.formatDuration` 死代码（P3-6）；更新 `DurationDisplay.kt:23` 注释为真实数量（P3-5）。约 3 个文件。
2. **S15-F2（单位一致性，需 P-1 裁决）**：三处私有 formatter 接 `DurationUnit` 并统一舍入函数；7 个调用点传 `LocalDurationUnit.current`。同时补 §7 #10/#11 的 formatter 测试。
3. **S15-F3（手势与无障碍，需 P-3 裁决）**：`durationUnitToggle` 增加 `onLongClickLabel` 与 `haptic`；把列表项内联 pill 的 toggle 摘除或改为不消费 tap；补 Compose 测试验证父级 `onOpen` / `onManage` 仍可达（P2-1、P3-2/3/4）。
4. **S15-F4（状态层测试）**：`DurationPrefs` 用临时 DataStore/`AutoCleanupCorruptionHandler` 风格做真实往返测试：默认值、脏值、`setUnit` → 重读、`toggle` 连续、无订阅者后 toggle（守住 `:74-76` 的设计意图）。
5. **S15-F5（防回归守卫）**：加一条"新增时长展示位必须走 `formatDuration`/`DurationText`"的可执行约束（形式由 Codex 决定；我倾向**行为测试**而非源码字符串扫描）。
6. **S15-F6（真机）**：覆盖 RB-1 … RB-7。

---

## 12. 本轮明确未做事项

- **未运行 Gradle / 任何测试 / 编译**：§7 的覆盖判定全部来自读码，**不声称任何用例通过**。
- **未运行 ADB、未操作手机、未使用任何模拟器**：§6.2 手势后果与 §9 全部列为未验证。
- **未修改任何文件**（除本报告）。未改 Kotlin / 测试 / Gradle / 脚本 / 既有文档。
- 未触碰窗口 B（`android/scripts/**`、S19-F2）与窗口 C（S09-B 点击区域）的任何文件，未对其改动作评价。
- 未 commit / push / stage / stash / reset / checkout / clean，未全仓格式化，未回退任何既有改动。
- 未进入原始仓库 `D:\develop\Code\Codex\creation-reading-assistant`。
- 未逐行核对 `OnboardingOverlay` 是否包含时长单位引导（§6.3 该项标为待确认）。
- 未穷举 `rememberCountUp` 等动画中间值对单位切换的影响（列为 RB-4）。
- 未做真机 TalkBack 验证，未验证 Toast 播报。
- 未实现任何修复，未对 §10 作产品决定。

---

## 13. 数量统计

| 级别 | 数量 | 条目 |
|---|---:|---|
| **P0** | **0** | — |
| **P1** | **2** | P1-1 首页今日卡英文 `3h 20m` 格式冲突；P1-2 全局单位偏好在 7 个展示位失效 |
| **P2** | **3** | P2-1 长按劫持父级卡片手势；P2-2「我的」首页时长行不能长按（与承诺不符）；P2-3 同值不同显（舍入/形态分叉） |
| **P3** | **8** | P3-1 toggle 读-改-写竞态；P3-2 详情页 pill 点击不展开；P3-3 无触觉反馈；P3-4 无 `onLongClickLabel`；P3-5 注释数量过时；P3-6 死代码 wrapper；P3-7 缺负值保护；P3-8 不可达秒分支 |
| **readiness blocker** | **7** | RB-1 … RB-7（§9，全部为"必须真机"） |
| **产品待裁决** | **6** | P-1 … P-6（§10） |
| 未来维护风险 | 2 | 无防漏接守卫；`internal` formatter 可被继续复用扩散不一致 |
| 展示位统计 | **20** | 跟随单位 13（可长按 12 / 不可长按 1），不跟随 7 |
| 正确排除项 | 17 组 | §4 |
| 测试 | 15 例真实覆盖纯函数；**状态链 / 手势 / 一致性 / 重启 全部零覆盖** | §7 |

---

## 14. 边界遵守声明

| 约束 | 结果 |
|---|---|
| 唯一可写文件 = 本报告 | ✅ 仅创建 `docs/coordination/investigations/agent-investigation-s15-qoder.md` |
| 不修改 Kotlin / JVM / Compose / androidTest / Gradle / 脚本 / 既有文档 | ✅ 未使用任何编辑工具触碰；本轮全部为读操作 |
| 不覆盖、回滚或评论窗口 B / C 的未完成修改 | ✅ 未触碰 `android/scripts/**` 与点击区域相关文件；本报告不含对其改动的评价 |
| 不 reset / checkout / clean / stash / commit / push / stage / 全仓格式化 | ✅ 全部未执行；开工与收尾 HEAD 均 `5aed593`，`git diff --cached` = 0，`git stash list` = 0 |
| 不运行 Gradle / ADB / 真机 / MuMu 或任何模拟器 | ✅ 未执行任何构建、测试或设备命令 |
| 不进入原始仓库 | ✅ 所有路径均为 `…-recovery-20260919` |
| 展示位数量从当前代码重新枚举，不预设 12 | ✅ 结果为 20（§3），并指出 `DurationDisplay.kt:23` 的"12"注释已过时（P3-5） |
| 不把旧计划文档当现状 | ✅ 未引用任何计划文档作为结论依据；每条结论给当前文件行号 |
| 区分事实 / 推断 / 未验证 | ✅ §2 表列"保证级别"，§6.2 手势后果标"需真机确认"，§9 单列 blocker |
| 不把"缺测试""理论可能""真机未验""个人偏好"定为 P1/P2 | ✅ 单位豁免、`"1.5 小时"` 是否可接受等主观项全部放 §10 待裁决；纯测试缺口放 §7 与 F4/F5，不占 P 级 |
| 书籍只用中性名称 | ✅ 全文仅「测试 EPUB / 测试 TXT / 测试 Markdown」，无真实书名 |
| 不实现修复 | ✅ 只给最小修复边界与切片建议，未改一行代码 |

**完成报告后停止。等待 Codex 主任务复核，未领取下一片；S15 的修复形态依赖 §10 的 P-1 / P-2 / P-3 裁决。**
