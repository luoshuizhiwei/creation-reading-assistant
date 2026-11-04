# S15-F3 实施设计（只读）：时长长按切换、父子手势与无障碍

> 执行：Claude（窗口 B）　|　任务：S15-F3-DESIGN（**严格只读**，不写代码）
> 工作仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
> 分支：`codex/recover-worktree-20260920`　固定基点：`5aed5939600e491843e3c44453921bd6273a233c`
> 开工与收尾 `git rev-parse --short HEAD` 均 = `5aed593`
> **未运行 Gradle / ADB / APK / 真机 / 模拟器**；未修改任何 Kotlin、测试、Gradle、协调看板、handoff、Obsidian
> 唯一产物：本文件

---

## 0. 方法与口径

全部结论来自当前工作树源码的静态阅读（`grep` / `sed` / 逐个读函数体）。**静态源码只能证明事件结构**，因此本文严格分三档标注：

- **[事实]** —— 代码里可直接读出的结构（谁挂在哪、用什么 API、传了什么参数）。
- **[推断]** —— 依 Compose 指针/语义分发语义推出，但未经运行验证。
- **[真机]** —— 必须真机 + TalkBack 才能确认。

**不得**把「可能抢手势」直接定为现役 P2（任务明确要求）。

---

## 1. 展示节点完整矩阵

### 1.1 枚举口径

以当前工作树为准，用两条命令交叉确认：

```
grep -rn "formatDuration(\|DurationText(\|formatCompactDuration(" android/app/src/main/java
grep -rn "60_000\|3_600_000" android/app/src/main/java   # 排查绕过公共 formatter 的裸换算
```

**结果：真正的阅读时长展示节点 = 22 个**（比 S15-F12 报的 24 少 2，原因见 §1.4 的重复计数修正）。

### 1.2 A 类 —— 真正的阅读时长（22 个）

| # | 文件 : 行 | 所属函数 | formatter | 读单位 | 已有 toggle | 角色 |
|---:|---|---|---|---|---|---|
| 1 | `profile/ReadingArchiveSection.kt:225` | `ReadingArchiveSection` | `formatDuration` NORMAL | ✅ | ✅ | 独立徽章主值 |
| 2 | `shelf/BookDetailStatsSection.kt:75` | `BookDetailStatsSection` | `formatDuration` NORMAL | ✅ | ✅ | 统计格主值 |
| 3 | `shelf/BookDetailStatsSection.kt:270` | `SessionMicroIsland` | `DurationText` | ✅ | ✅ | 会话 pill |
| 4 | `reader/sheets/ReaderBookInfoSheet.kt:217` | `ReaderBookInfoSheet` | `formatDuration` NORMAL | ✅ | ✅ | 「本次已读」 |
| 5 | `reader/sheets/ReaderBookInfoSheet.kt:225` | 同上 | `formatDuration` NORMAL | ✅ | ✅ | 「累计阅读」 |
| 6 | `reader/sheets/ReaderProgressSheet.kt:173` | `ReaderProgressSheet` | `formatDuration` NORMAL | ✅ | ✅ | 「累计」 |
| 7 | `reader/sheets/ReaderProgressSheet.kt:248` | 同上 | `formatDuration` NORMAL | ✅ | ✅ | 「本次已读」 |
| 8 | `reader/sheets/ReaderProgressSheet.kt:277` | 同上 | `formatDuration` NORMAL | ✅ | ✅ | 「预计读完」 |
| 9 | `homearchive/HomeCompletedScreen.kt:243` | `CompletedArchiveHeader`（聚合胶囊） | `DurationText` | ✅ | ✅ | 聚合主值 |
| 10 | `homearchive/HomeCompletedScreen.kt:516` | `CompletedBookCard`（每本胶囊） | `DurationText` | ✅ | ✅ | 每本 pill |
| 11 | `readinghistory/MyReadingScreen.kt:246` | `ReadingFootprintOverview` | `DurationText` | ✅ | ✅ | 聚合主值 |
| 12 | `readinghistory/MyReadingScreen.kt:565` | `TimelineBookRow` | `DurationText` | ✅ | ✅ | 每条 pill |
| 13 | **`profile/ProfileHomeScreen.kt:198`** | `ProfileHomeScreen`（档案卡统计行） | `formatDuration` NORMAL | ✅ | ✅ | 「阅读时长」 |
| 14 | `home/components/HomeMetricsSection.kt:101` | `HomeMetricsSection` | `formatDuration` **COMPACT** | ✅ | ❌ | 「今日」主值 |
| 15 | `home/components/HomeReadingArchiveSection.kt:136` | `HomeReadingArchiveSection` | `formatDuration` NORMAL | ✅ | ❌ | 档案卡聚合 |
| 16 | `stats/components/SummaryGroup.kt:72` | `SummaryGroup` | `formatCompactDuration` | ✅ | ❌ | 摘要「阅读时长」 |
| 17 | `stats/components/CreationSection.kt:87` | `CreationSection` | `formatCompactDuration` | ✅ | ❌ | 「沉浸时长」 |
| 18 | `stats/components/HeatmapSection.kt:115` | `HeatmapSection` | `formatCompactDuration` | ✅ | ❌ | 热力图汇总胶囊 |
| 19 | `stats/components/HeatmapSection.kt:400` | `HeatmapCellTooltip` | `formatCompactDuration` | ✅ | ❌ | 单日详情句 |
| 20 | `stats/components/TrendSection.kt:167` | `TrendChart` | `formatDuration` **TICK** | ✅ | ❌ | 柱顶标签（每个柱一个） |
| 21 | `stats/components/YearBillHeroCard.kt:107` | `YearBillHeroCard` | `formatCompactDuration` | ✅ | ❌ | 年度主值「累计阅读」 |
| 22 | `stats/components/YearBillHeroCard.kt:110` | 同上 | `formatCompactDuration` | ✅ | ❌ | **同卡 subtitle（与 #21 同文）** |

> **计数修正记录**：本设计初稿漏计 #13（`ProfileHomeScreen.kt:198`，S15-F12 新增的时长行），当时报 21 个。复核后补入，**正确数量为 22**。该节点已有 toggle 且父级无 clickable（见 §2.2），属 A 类。

另有两处**派生文本**（`YearBillHeroCard.kt:227 / :231`，`buildYearHeadline` 的两条金句分支）——它们与 #21/#22 同源同文，见 §1.4。

### 1.3 非时长节点（正确排除，**不得**误判为 `DurationUnit.DAYS` 时长）

| 类别 | 位置示例 | 为什么排除 |
|---|---|---|
| **天数计数** | `SummaryGroup.kt:83` `"$animatedDays 天"`；`CreationSection.kt:93`；`HeatmapSection.kt:115` 的 `"$activeDays 天开卷"`；`YearBillHeroCard.kt:110/132` 的 `"$days 天"` / `"$longest 天"`；`GoalSubPage.kt:586` | 这是 `readingDays` / `streakLongest` 这类**整数计数**，不是毫秒换算。**它们不经过任何 formatter**，与 `DurationUnit` 无关。**这是本轮最容易误判的一类**——`"30 天"` 看起来像 DAYS 时长，实则永远是天数。 |
| **相对时间** | `TimeUtils.kt:75-91` `formatRelativeTime`（"刚刚/N 分钟前/N 小时前/昨天/N 天前"）；`HomeArchiveFormatters.kt:92` `"${hours}小时前"` | 语义是"距今多久"，不是"累计多久" |
| **阅读目标** | `GoalSubPage.kt:236/246/281/286/356-358`；`ProfileHomeScreen.kt:234`；`HomeMetricsSection.kt:95` `"今日 /${dailyGoalMinutes}分"`；`GoalRingSection.kt:176/178`；`ReadingGoalDecision.kt:42/46` | 用户设定的**阈值**，单位是产品契约里的"分钟"；改成"0.5 小时"反而不可输入 |
| **配置间隔** | `ReaderSubPage.kt:727/750`；`ReaderEyeCareSettings.kt:146/169`；`ReaderProgressEffects.kt:158/165`；`ReaderSubPage.kt:884` 自动隐藏 | 间隔/超时配置，非累计时长 |
| **TTS 睡眠定时** | `ReaderTtsBar.kt:220/233/578` | 倒计时配置 |
| **同步耗时** | `ProfileUtils.kt:29-33` `formatSyncDuration` | 操作耗时诊断，量级为秒 |
| **阅读速度** | `StatsPage.kt:314-315`；`ReaderProgressSheet.kt:268-272`（字/分） | 速率，单位固定 |
| **时钟时间** | `ProfileUtils.kt:12-24` | 时刻不是时长 |

### 1.4 与 S15-F12「24 个」的差异说明（**修正**）

S15-F12 报 24 个，本轮报 22 个真正的时长节点。差异**不是漏检，而是计数口径**：

| S15-F12 计入 | 本轮处置 | 理由 |
|---|---|---|
| `YearBillHeroCard` 的 subtitle（:110） | **仍计入**（#22） | 它确是一段独立渲染的时长文字 |
| `YearBillHeroCard` 的两条金句分支（:227/:231） | **不计为独立节点** | 它们与 #21/#22 **同源同文**（同一个 `$spoken`），是**同一语义组件的重复呈现**，不是两个展示位 |
| `TrendSection` 柱顶（:167） | **计为 1 类节点** | 但**每个柱各渲染一份**（月度 12 柱 = 12 个 Text），见 §2.2 |
| `HomeMetricsSection` / `HomeReadingArchiveSection` | **仍计入** | 同前 |
| `ProfileHomeScreen.kt:198` | **仍计入**（#13） | S15-F12 计入，本轮初稿曾漏计，已补正 |

⇒ **22 个节点**是"独立的时长文字渲染点"数；若按"每个柱各算一个 Text"展开，TrendSection 会再贡献 ~7–12 个。

### 1.5 重复呈现同一时长的辅助文本（**关键**）

| 语义组件 | 主值 | 重复文本 | 是否同文 |
|---|---|---|---|
| `YearBillHeroCard` | `value`（#21） | `subtitle`（#22）+ 金句两条分支 | **完全同文**（都来自 `yearBillCardValue` / `$spoken`） |
| `HomeReadingArchiveSection` | 无主值 | `"累计 12 小时 · 3 本"`（#15） | 单处 |
| `HeatmapSection` | 汇总胶囊（#18） | 单日 tooltip（#19） | **不同粒度**（365 天 vs 单日），非重复 |

⇒ **只有 `YearBillHeroCard` 一卡之内存在 3 处同文重复**（主值 / subtitle / 金句）。

---

## 2. 四类交互分类

### 2.1 分类总表

| 类 | 含义 | 节点 | 数量 |
|---|---|---|---:|
| **A** | 独立时长 Text，**可安全直接长按**（无父级 clickable） | #1, #4, #5, #6, #7, #8, #13, #16, #17, #18, #19 | **11** |
| **B** | 位于父级 clickable/combinedClickable 内，**需父子事件策略** | #2, #3, #9, #10, #11, #12, #14, #15, #21, #22 | **10** |
| **C** | 图表刻度/柱标签，**不应逐个成为长按入口** | #20 | **1 类**（多实例） |
| **D** | subtitle/headline 等**重复文本**，不应制造重复无障碍动作 | #22（subtitle）、#21 的金句 | **1 组** |

> 注：#22 同时属 B 与 D —— 它在 `YearBillHeroCard`（静态卡，无父级手势）内，但作为 subtitle 与 #21 同文重复。**按"不应有长按"归入 D 处理**（§3.2）。

### 2.2 A 类明细（可安全接线）

| # | 位置 | 父级 clickable | 依据 |
|---|---|---|---|
| 1 | `ReadingArchiveSection.kt:225` | **无**（该文件 `grep -c clickable` = 0） | [事实] 文件内零 clickable |
| 4,5 | `ReaderBookInfoSheet.kt:217/225` | **无**（`grep -c clickable` = 0） | [事实] |
| 6,7,8 | `ReaderProgressSheet.kt:173/248/277` | **无**（`grep -c clickable` = 0） | [事实] |
| **13** | **`ProfileHomeScreen.kt:198`** | **无** —— 含 clickable 的 Row 在 `:158`（兄弟节点），被 `:191` 的 `CompactItemDivider` 分隔；统计行 `:192` 自身无手势 | [事实] 逐行核对 |
| 15 | `HomeReadingArchiveSection.kt:136` | ⚠️ **有**（`:63` `.clickable(role = Button)`） | **实为 B 类**，见下方修正 |
| 16 | `SummaryGroup.kt:72` | **无** | [事实] |
| 17 | `CreationSection.kt:87` | **无** | [事实] |
| 18 | `HeatmapSection.kt:115` | **无**（`:384` 的 clickable 只在**单元格**上，不在标题胶囊） | [事实] |
| 19 | `HeatmapSection.kt:400` | ⚠️ 位于 `HeatmapCellTooltip`，由 `AnimatedVisibility` 展开、**本身不可点** | [事实] |

**修正**：#15 实为 B 类（父级 `Surface.clickable`）。因此

- **A 类（真正无父级冲突）= 11 个**：#1, #4, #5, #6, #7, #8, #13, #16, #17, #18, #19
  （#19 虽无父级手势，但按 C 类**不应接线**，见 §3.2 → **实际可接线 10 个**）
- **B 类 = 10 个**：#2, #3, #9, #10, #11, #12, #14, #15, #21, #22

### 2.3 B 类父子结构明细（[事实]）

| # | 节点 | 最近的父级手势 | 父级普通点击行为 | 父级长按行为 | 容器 |
|---|---|---|---|---|---|
| 2 | `BookDetailStatsSection.kt:75` | `DetailIslandCard`（`:66`） | **无 onClick** | 无 | 静态卡 |
| 3 | `BookDetailStatsSection.kt:270` | `:244` `.clickable { itemExpanded = !itemExpanded }` | **展开/收起该条记录** | 无 | Lazy 列表项 |
| 9 | `HomeCompletedScreen.kt:243` | `:227` `Surface(shape=…)` **无 onClick** | 无 | 无 | 聚合头 |
| 10 | `HomeCompletedScreen.kt:516` | `CompletedBookCard` → `SectionCard(onClick = onOpen)`（`:72`，内部 `Surface(onClick=…)`） | **打开该书** | **无**（`Surface(onClick)` 只有点击） | Lazy 网格/列表 |
| 11 | `MyReadingScreen.kt:246` | `ReadingFootprintOverview`（`:198` `IslandCard`） | **无 onClick** | 无 | 静态卡 |
| 12 | `MyReadingScreen.kt:565` | `:485` `IslandCard(... .combinedClickable(onClick = onOpen, onLongClick = onManage))` | **打开该书** | **打开管理菜单** | Lazy 列表项 |
| 14 | `HomeMetricsSection.kt:101` | `GridStat` → `Surface`（`:113`）**无 onClick** | 无 | 无 | 静态格 |
| 15 | `HomeReadingArchiveSection.kt:136` | `:63` `.clickable(role = Button, onClick = onClick)` | **进入阅读档案** | 无 | 静态卡 |
| 21,22 | `YearBillHeroCard.kt:107/110` | `IslandCard`（**未传 onClick**） | 无 | 无 | 静态卡 |

**只有两处父级带长按**：`MyReadingScreen.kt:488`（`onLongClick = onManage`）与 `BookGrid.kt:283` / `ShelfSearchRoute.kt:470`（**与时长节点无关**）。**唯一真正与 `durationUnitToggle` 争抢长按的是 #12。**

**只有四处父级带普通点击**：#3（展开）、#10（打开书）、#12（打开书）、#15（进档案）。

---

## 3. 推荐的产品契约（唯一方案）

### 3.1 推荐：**方案 2 + 方案 4 的合并式表述**

> **「每个包含时长的语义组件只提供一个长按入口；长按是快捷方式，单位跟随是承诺，逐文本可长按不是承诺。」**

具体：

1. **承诺面**：所有时长文本**跟随全局单位**（S15-F1/F2 已实现，21/21 节点读取 `LocalDurationUnit`）。这是**产品承诺**。
2. **快捷面**：**每个语义组件最多一个**长按切换入口；长按是**快捷方式**，不是可发现性主路径。
3. **可发现性主路径**：`外观 → 时长按「天」显示` 显式开关（已存在，`AppearanceSubPage.kt:152-159`）。
4. **不承诺**："任意时长文本都能长按"这一表述**应删除**。

### 3.2 逐条回答任务提出的问题

| 问题 | 结论 |
|---|---|
| `DurationPrefs.kt:22-23` 与 `DurationDisplay.kt` 的"任意时长文本"承诺是否保留 | **不保留**。该表述与实现（22 个节点中 12 个有 toggle、10 个没有）不符，且**技术上不可达**（C 类图表标签、D 类重复文本都不应挂长按） |
| 应改成什么准确文案 | `DurationPrefs`：`需要按天看时，可在外观设置中切换，或在时长文本上长按。`；`DurationDisplay.durationUnitToggle` 的 KDoc：`把长按切换挂到**该语义组件的代表文本**上。同一组件内的重复文本不得再挂，避免重复无障碍动作。` |
| 哪些节点**必须**有长按 | **A 类可接线的 10 个**（#1,4,5,6,7,8,13,16,17,18）+ **B 类中父级无冲突的 6 个**（#2,9,11,14,21）→ 合计 **16 个**。**#12 需专门策略**（§4.3）；**#15 已由父级 clickable 覆盖**（§4.3） |
| 哪些节点**明确不应**有长按 | **#20**（趋势柱标签，每柱一个，会变成 ~12 个长按节点）；**#22**（subtitle，与 #21 同文重复）；**#21 的两条金句**（同文重复）。⇒ 年度账单**整卡只保留 #21 一个入口** |
| TalkBack 应暴露什么 `onLongClickLabel` | `"切换时长单位"`（对齐仓库既有惯例 `BookGrid.kt:282` 的 `onLongClickLabel = "打开书籍操作"`） |
| 是否需要振动反馈 | **建议加**。理由：`durationUnitToggle` 是**无视觉反馈**的长按（`indication = null`），全应用有 **218 处** `haptic(...)` 而此处 0 处；无振动时用户无法确认"长按是否被识别"。**但须产品裁决**（任务允许"不增加振动"，S15-F12 亦如此要求） |
| 普通点击与长按冲突时谁优先 | **父级普通点击优先**（不改变既有导航语义）。子级**只消费长按，不消费点击**——这是本设计的核心技术要求，见 §4.2 |
| 用户如何发现？只靠长按是否可发现性不足 | **是，严重不足**。`AppearanceSubPage.kt:76-77` 的注释**已自认**"长按属于隐藏手势，用户不会自己发现"，因此才补了显式开关；`OnboardingOverlay`（`MainActivity.kt:135`）**不含**时长单位引导（[事实]：其三条文案为"本地优先"/"极致东方排版"/"AI 伴读"，均无时长） |

### 3.3 不推荐的方案及原因

| 方案 | 不推荐原因 |
|---|---|
| **方案 1**（每段时长文字都能长按） | ①**技术上不可达**：C 类图表标签（`TrendSection` 每柱一个）会变成 ~12 个长按入口，且柱区仅 10sp、命中区远小于 48dp；②**无障碍退化**：D 类同文重复（年度账单 3 处）会让 TalkBack 播报多个"切换时长单位"动作；③与父级 `combinedClickable`（#12）直接争抢长按 |
| **方案 3**（只在聚合组件提供长按，其他页面只靠设置开关） | 过度收缩：A 类 10 个节点**无任何父级冲突**，直接挂长按零风险、零成本；砍掉它们会让"长按快捷"这一既有能力在大部分页面消失，属**功能倒退** |
| **方案 4 单独使用**（只承诺跟随单位，不承诺任何长按） | 与方案 3 同样过度；且**已接线的 13 个 toggle**（#1–#13，含 `ProfileHomeScreen`）会成为"文档说没有、实际有"的悬空能力 |

---

## 4. 父子事件矩阵

### 4.1 `durationUnitToggle` 的现状（[事实]）

```kotlin
// DurationDisplay.kt:52-62
return combinedClickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = {},          // ← 消费点击
    onLongClick = toggle,
)
```

**关键**：`onClick = {}` **不是"无点击"**，而是"声明并消费一个空点击"。

### 4.2 事件优先级（[推断]，依 Compose 指针分发语义）

Compose 的命中测试自子向父冒泡，**最内层的可点击节点先消费**。下表只列**当前确实挂着 toggle**的 B 类节点（即已存在的吞没风险）：

| 场景 | 现状行为 | 用户期望 | 判定 |
|---|---|---|---|
| **长按**子时长文字（#12，唯一父级带长按者） | 子级 `onLongClick` 命中 → **切单位**；父级 `onLongClick = onManage` **不触发** | 长按时长想切单位 ✅；长按卡片想开管理 ✅ —— 两者按**命中区域**区分，符合直觉 | **[推断] 可接受**，但需真机确认 |
| **点击**子时长文字（#3，父级 `:244` 展开记录行） | 子级 `onClick = {}` 消费 → **不展开记录行** | 点时长想展开 ❌ | **[推断] 真实退化** |
| **点击**子时长文字（#10，父级 `SectionCard.onClick` 打开书） | 子级消费 → **不打开书** | 点时长想打开书 ❌ | **[推断] 真实退化** |
| **点击**子时长文字（#12，父级 `:488` 打开书） | 子级消费 → **不打开书** | 点时长想打开书 ❌ | **[推断] 真实退化** |
| 长按子时长文字（#3, #10） | 父级无长按 → 无冲突 | ✅ | — |
| #15（父级 `.clickable` 进档案） | **子级当前无 toggle** → 父级点击正常 | ✅ 现状无退化 | — 若按 §4.3 给子级加 toggle，**会引入**与 #3/#10/#12 同类的新吞没 |

⇒ **当前实际存在的退化 = 3 处**（#3、#10、#12），均为"点击被吞"。**#15 尚未接线，故无退化**——但若按"每段文字都挂 toggle"的方案接线，**会新增第 4 处**。

**这与 Qoder 报告 P2-1 的描述方向相反**：Qoder 强调"长按劫持父级长按"，但静态分析显示**父级长按只有 #12 一处**，而**父级点击被吞有 3 处**。本设计以本节为准。

### 4.3 修复策略（对 #12 及所有 B 类）

**原则：子级只消费长按，不消费点击。**

Compose 的 `combinedClickable` **必然**声明 `onClick`（参数非空）。要"只消费长按"，需要让**父级**感知子级区域：

| 方案 | 做法 | 评价 |
|---|---|---|
| **b1（推荐）** | 给 B 类的**父级**改用 `combinedClickable`，并把 `durationUnitToggle` 从子文本**上移**到父级：父级 `onClick` = 原行为、`onLongClick` = toggle | 语义组件只有**一个**长按入口（符合 §3.1）；点击仍走父级原行为；**无争抢** |
| b2 | 保持子级 toggle，另给子级补 `onClick` 透传父级行为 | 子级需知道父级 onClick，**耦合**；且父级区域与子级区域行为不一致（同一次点击在文字上/文字外结果不同），违反一致性 |
| b3 | 子级不挂 toggle，只在聚合区挂 | 对 #12 可行（`MyReadingScreen:246` 已有聚合 toggle），但 #3/#10 无聚合入口可用 |

⇒ **推荐 b1**：**把长按入口从"文本"上移到"语义组件"**。这与 §3.1 的"每个语义组件只提供一个长按入口"完全一致，并**顺带消灭全部点击吞没问题**。

**逐节点落地方案**：

| 节点 | 现状 | b1 落地 |
|---|---|---|
| #3（`SessionMicroIsland` 会话 pill） | 子级 toggle + 父级 `.clickable` 展开 | toggle 上移到 `:244` 的 `Surface`，父级改 `combinedClickable(onClick = { itemExpanded = !itemExpanded }, onLongClick = toggle)` |
| #10（`CompletedBookCard` 每本 pill） | 子级 toggle + `SectionCard.onClick` 打开书 | toggle 上移到卡片；`SectionCard` 目前只支持 `onClick`，需**新增 `onLongClick` 参数**（属公共组件改动，需 Codex 裁决） |
| #12（`TimelineBookRow` pill） | 子级 toggle + 父级 `combinedClickable` | 父级 `:488` 已用 `combinedClickable`，**只需把 `onLongClick` 从 `onManage` 改为「toggle」**——但这样会**夺走管理菜单**。⇒ 需产品裁决（§8.3 PD-3）：或改为「单击开书、长按切单位、管理菜单改由卡片内按钮进入」 |
| #15（档案卡聚合文本） | 无 toggle + 父级 `.clickable` 进档案 | 同 #10：toggle 上移到父级 `:63` 的 `Surface` |
| #2, #9, #11, #21（父级无手势） | 部分有/无 toggle | 直接给该语义组件的**代表文本**挂 toggle（保持现状或补上），无需父级改动 |
| #22（subtitle） | 无 toggle | **不挂**（D 类）；整卡只保留 #21 一个入口 |

### 4.4 三档结论

**[事实]（代码可直接读出）**
1. `durationUnitToggle` 用 `combinedClickable(onClick = {}, onLongClick = toggle)` —— 消费点击。
2. 全仓**唯一**带 `onLongClick` 的时长相关父级是 `MyReadingScreen.kt:488`。
3. 四处父级带普通点击：#3（`:244`）、#10（`SectionCard.onClick`）、#12（`:488`）、#15（`:63`）。
4. **13 个节点已有 toggle**（#1–#13），**9 个没有**（#14–#22）。
5. `durationUnitToggle` / `DurationText` / `LocalDurationUnit` 在 `test/` 与 `androidTest/` **零命中**。
6. `OnboardingOverlay` 不含时长单位引导。
7. `AppearanceSubPage.kt:76-77` 注释自认长按不可发现。

**[推断]（Compose 分发语义，需运行验证）**
1. 子级 `onClick = {}` 吞掉 #3/#10/#12/#14 的父级点击（4 处）。
2. #12 的长按按命中区域分流（文字上切单位、文字外开管理）。
3. `AnimatedVisibility` 内的 #18（tooltip）不参与父级手势。

**[真机]（必须设备 + TalkBack）**
1. 上述 3 处点击吞没（#3, #10, #12）的实际范围（11sp `labelSmall` 的实际命中盒）。
2. 长按命中区是否达 48dp。
3. TalkBack 是否播报 `onLongClickLabel`、Toast 是否被播报。
4. 切换后所有页面是否立即重绘（`rememberCountUp` 缓存、`LazyColumn` 复用项）。
5. 快速连点 / 双指长按是否丢切换（`DurationPrefs.toggle()` 读-改-写非原子）。
6. `#12` 长按分流在真机上的实际表现。

---

## 5. TalkBack 语义设计

### 5.1 现状（[事实]）

| 检查点 | 现状 |
|---|---|
| `onLongClickLabel` | **未传**（`DurationDisplay.kt:56-61` 只有 `onClick`/`onLongClick`） |
| 无障碍可读文本 | `Text` 本身可读（如"12 小时 30 分钟"） |
| 长按动作标签 | 无 → TalkBack 只播"双击以激活"之类**通用**动作，**不说明长按会改全局设置** |
| 振动 | 无 |
| Toast | `MainActivity.kt:85-93` 有文案（"阅读时长已改为按「天」显示"） |

### 5.2 设计

| 项 | 设计 | 理由 |
|---|---|---|
| `onLongClickLabel` | **`"切换时长单位"`** | 对齐 `BookGrid.kt:282` 的既有惯例；明确说明长按的作用 |
| 语义角色 | **不加 `Role.Button`** | 加 `Role.Button` 会让 TalkBack 播报"按钮"，而它本质是**文本上的隐藏快捷手势**，不是按钮。保持默认文本语义 + `onLongClickLabel` |
| `contentDescription` | **不设** | 文本内容本身即描述；设 `contentDescription` 反而会覆盖真实时长读数（无障碍反模式） |
| 重复动作抑制 | 每个**语义组件**只挂 1 个 toggle（§3.1） | 年度账单 3 处同文若各挂一个，TalkBack 会播报 3 个"切换时长单位"动作 |
| 振动 | **建议加**（`haptic(HapticFeedbackType.TextHandleMove)`，对齐仓库惯例） | 无视觉反馈的长按需要触觉确认。**待产品裁决** |
| 播报单位变更 | 依赖既有 Toast | Toast 在 TalkBack 下**是否播报未验证** → [真机] |

---

## 6. 最小实施切片与文件所有权

### S15-F3-A：公共语义 + 无冲突节点接线

- **范围**：
  1. `DurationDisplay.kt`：`durationUnitToggle()` 增加 `onLongClickLabel = "切换时长单位"`；（可选）加 `haptic`。
  2. 对 **A 类可接线的 10 个节点**（#1,4,5,6,7,8,13,16,17,18）确认/补齐 toggle 接线（#19 按 C 类不接）。
  3. 更新 `DurationPrefs.kt:22-23` 与 `DurationDisplay.kt:45-51` 的**承诺文案**为 §3.2 的准确表述。
- **文件**：`ui/util/DurationDisplay.kt`、`data/settings/DurationPrefs.kt`（**仅注释**）、`stats/components/{SummaryGroup,CreationSection,HeatmapSection}.kt`、`home/components/HomeMetricsSection.kt`、`profile/ProfileHomeScreen.kt`（#13 已接线，确认即可）
- **测试**：`DurationDisplayTest.kt`（新增，见 §7.1）
- **冲突**：`DurationPrefs.kt` 是窗口 D 的 S15-F4 文件 → **仅改注释**，且需 Codex 确认 F4 已收尾

### S15-F3-B：父卡组件的事件矩阵

- **范围**：按 §4.3 的 **b1**，把 B 类中 4 处带父级点击的组件（#3, #10, #12, #15）的 toggle 从子文本**上移到父级**，父级改 `combinedClickable(onClick = 原行为, onLongClick = toggle)`。**#12 需先裁决 PD-3**；**#10 需 `SectionCard` 新增 `onLongClick` 参数**（公共组件，见 §4.3）。
- **文件**：`shelf/BookDetailStatsSection.kt`、`homearchive/HomeCompletedScreen.kt`、`readinghistory/MyReadingScreen.kt`、`home/components/HomeReadingArchiveSection.kt`、（可能）`ui/components/SharedComponents.kt`（`SectionCard` 加 `onLongClick`）
- **测试**：各页 Compose 点击/长按测试（见 §7.2）
- **冲突**：**无**（这 4 个文件无其他窗口占用）

### S15-F3-C：真机 + TalkBack + 大字体验收

- **范围**：§4.4 [真机] 全部 6 项 + §7.3 矩阵
- **文件**：无代码改动（仅验收报告）
- **冲突**：需独占设备时段（与 WorkBuddy 协调）

**为什么是 3 片而不是更多**：A 与 B 的**文件集不重叠**、可并行；C 依赖 A+B 完成后才有验收对象。若把 A 再拆成"语义标签"与"节点接线"，两片会改同一个 `DurationDisplay.kt`，反而制造冲突。

---

## 7. 测试设计

### 7.1 S15-F3-A 的 JVM 测试（新增 `DurationDisplayTest.kt`）

| 测试 | 断言 |
|---|---|
| `toggle modifier exposes a long click label` | 用 `createComposeRule` 渲染一个带 `Modifier.durationUnitToggle()` 的 `Text`，断言语义树含 `SemanticsActions.OnLongClick` 且 `onLongClickLabel == "切换时长单位"` |
| `toggle invokes the provided callback exactly once per long click` | 计数 `LocalDurationUnitToggle` 提供的 lambda 调用次数，`performTouchInput { longClick() }` 后断言 `== 1` |
| `toggle does not invoke the callback on a plain click` | `performClick()` 后断言计数 `== 0`（钉住"只有长按才切"） |
| `duration text follows the composition local unit` | 分别提供 HOURS/DAYS，断言渲染文本为 `formatDuration(ms, unit)` 的结果（复用 S15-F12 的公共 formatter） |

> 这几条**直接覆盖当前零覆盖的 toggle 语义**（§4.4 [事实] 5）。

### 7.2 S15-F3-B 的生产组件测试

| 测试 | 目标 Composable | 操作节点 | 断言 |
|---|---|---|---|
| `history card long press toggles unit without opening manage menu` | `TimelineBookRow`（`MyReadingScreen`） | `testTag("reading-item-<id>")` 上 `longClick()` | toggle 计数 `== 1`；`onManage` 计数 `== 0` |
| `history card click still opens the book` | 同上 | `testTag("reading-item-<id>")` 上 `click()` | `onOpen` 计数 `== 1`；toggle 计数 `== 0` |
| `session row click still expands after the change` | `SessionMicroIsland` | 行根节点 `click()` | `itemExpanded` 翻转（用可见子节点断言） |
| `session row long press toggles without expanding` | 同上 | 行根节点 `longClick()` | toggle 计数 `== 1`；行**未**展开 |
| `completed book card click still opens the book` | `CompletedBookCard` | `testTag("completed-book-<id>")` `click()` | `onOpen` 计数 `== 1` |
| `archive card click still navigates` | `HomeReadingArchiveSection` | `testTag("reading-archive-card")` `click()` | `onClick` 计数 `== 1` |
| `archive card long press toggles without navigating` | 同上 | 同上 `longClick()` | toggle 计数 `== 1`；`onClick` 计数 `== 0` |
| `each semantic component exposes exactly one toggle action` | `YearBillHeroCard` | 语义树 | `OnLongClick` 动作**恰好 1 个**（钉住 D 类抑制） |

### 7.3 只能真机验证的项

| 项 | 为什么 |
|---|---|
| **3 处点击吞没**（#3, #10, #12）的**实际命中范围** | 11sp 文本的实际触摸盒需真机量 |
| 长按命中区是否 ≥48dp | 同上 |
| TalkBack 动作播报、Toast 播报 | 需开 TalkBack |
| 切换后全页面立即重绘 | `rememberCountUp` 缓存 / `LazyColumn` 复用项 |
| `#12` 长按分流 | 命中区域分流需实测 |
| 快速连点丢切换 | `DurationPrefs.toggle()` 读-改-写竞态（P3-1） |

### 7.4 必要矩阵

| 维度 | 取值 | 必要性 |
|---|---|---|
| 单位 | HOURS / DAYS | **必须**（本轮核心） |
| 屏幕宽 | 393dp（默认） / **320dp** | 必须（320dp 下长按命中盒更小） |
| 字体缩放 | 1.0 / **1.3** | 必须（大字号下文本溢出、命中盒变化） |
| 减少动画 | off / **on** | 必须（`reducedMotion` 影响 `rememberCountUp`，间接影响重绘） |
| TalkBack | off / **on** | 必须（§5 全部结论） |
| 深/浅色 | 至少 1 种深色 | 建议（`indication = null` 无涟漪，深色下需确认长按有反馈） |

---

## 8. 严重级别

### 8.1 计数

| 类别 | 数量 | 明细 |
|---|---:|---|
| **P0** | **0** | — |
| **P1** | **0** | — |
| **P2** | **0** | **静态源码不足以把"可能抢手势"定为现役 P2**（任务明确要求）。§4.2 的"点击吞没"是 [推断]，需真机确认后才可升级 |
| **P3** | **4** | 见下 |
| **产品契约待裁决** | **4** | 见 §8.3 |
| **测试缺口** | **3** | 见 §8.4 |
| **真机 readiness** | **6** | §4.4 [真机] 6 项 |

### 8.2 P3 明细（按共享根因归并，**不重复计数**）

| # | 问题 | 证据 |
|---|---|---|
| P3-1 | **文档/注释与实现不符**：`DurationPrefs.kt:22-23` 与 `DurationDisplay.kt` 承诺"任意时长文本长按可切换"，实际 22 个节点中只有 13 个挂 toggle | [事实] §1.2 |
| P3-2 | **长按不可发现**：无视觉可供性、无引导（`OnboardingOverlay` 不含）、`AppearanceSubPage.kt:76-77` 注释自认"用户不会自己发现" | [事实] |
| P3-3 | **无 `onLongClickLabel`**：TalkBack 不说明长按作用；仓库已有 `BookGrid.kt:282` 先例 | [事实] |
| P3-4 | **无触觉反馈**：全应用 218 处 `haptic(...)`，`DurationDisplay.kt` 0 处；`indication = null` 使长按无任何视觉反馈 | [事实] |

> **归并说明**：任务要求"不要把'所有文字都没有长按'重复计数成十几个缺陷"。上述 4 项按**共享根因**（承诺文案 / 可发现性 / 无障碍标签 / 触觉）归并，**而非按 9 个缺 toggle 的节点各计一次**。

### 8.3 产品契约待裁决

| # | 问题 | 选项 |
|---|---|---|
| PD-1 | 承诺文案改为 §3.2 的表述，还是保留"任意时长文本" | 推荐改 |
| PD-2 | 是否加触觉反馈 | 推荐加（P3-4） |
| PD-3 | `#12`（阅读历史条目）长按归属：切单位 vs 开管理菜单 | 推荐 b1（上移到父级） |
| PD-4 | 长按是否降级为"彩蛋"，可发现性完全依赖外观设置开关 | 推荐是（§3.1） |

### 8.4 测试缺口

| # | 缺口 |
|---|---|
| TG-1 | `durationUnitToggle` / `DurationText` / `LocalDurationUnit` 在 `test/` 与 `androidTest/` **零命中** |
| TG-2 | 无任何测试验证"父级 onClick 在子级时长文字上仍可达" |
| TG-3 | 无任何测试验证"每个语义组件只暴露一个长按动作" |

### 8.5 真机 readiness

见 §4.4 [真机] 6 项（命中盒、48dp、TalkBack 播报、重绘、分流、竞态）。

---

## 9. 开始与结束 `git status --short`

### 开始

```
$ git rev-parse --abbrev-ref HEAD
codex/recover-worktree-20260920
$ git rev-parse --short HEAD
5aed593
$ git status --short | wc -l
379
$ git diff --cached --name-only | wc -l
0
```

### 结束

```
$ git rev-parse --short HEAD
5aed593
$ git status --short | wc -l
379
$ git diff --cached --name-only | wc -l
0
```

**本轮只新增本报告**（`docs/coordination/designs/agent-design-s15-f3-claude.md`，未跟踪文件）。工作区改动计数 379 → 379（**未增减**），HEAD 未变。

---

## 10. 声明

- **只新增本报告**：本轮唯一写操作是创建 `docs/coordination/designs/agent-design-s15-f3-claude.md`。
- **未修改任何产品代码**：未触碰任何 Kotlin、测试、Gradle、资源文件。
- **未运行 Gradle / ADB / APK / 真机 / 模拟器**。
- **未 reset / checkout / clean / stash / stage / commit / push**（`git diff --cached` = 0）。
- **未恢复、格式化、评价或改写窗口 C/D 的文件**：本报告不包含对其改动的任何评价。
- **未修改**协调看板、handoff、Obsidian。
- **未领取下一任务**：等待 Codex 复核。
- 报告未记录任何真实测试书名。

---

## 11. 给 Codex 的复核清单

1. 确认 §1.4 的计数口径修正（**22 个真正节点**，而非 24）是否接受——差异是"金句与主值同文，不计为独立展示位"，且本轮**补正了初稿漏计的 `ProfileHomeScreen.kt:198`**。
2. 确认 §2.3 的父子结构表：**父级带长按的只有 `MyReadingScreen.kt:488` 一处**，而**父级带点击的有 4 处**。
3. 确认 §4.2 的判定方向：真正的退化是**点击被吞（4 处）**，而非 Qoder 报告强调的长按争抢。
4. 裁决 §3.1 的推荐契约（方案 2 + 4 合并）与 §3.3 的不推荐理由。
5. 裁决 §8.3 的 PD-1…PD-4（尤其 PD-3：阅读历史条目的长按归属）。
6. 确认 §6 的三片切分与文件所有权，特别是 `DurationPrefs.kt`（窗口 D 的 S15-F4 文件，本轮建议**仅改注释**）。
7. 确认 §8.1 的分级：**P2 = 0**，不把未经真机的"可能抢手势"计为现役 P2。

---

**本设计完成，等待 Codex 复核。未领取下一任务。**
