# S15-F3-A 执行结果（Claude / 窗口 B）

- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`
- 分支：`codex/recover-worktree-20260920`
- 固定基点：`5aed5939600e491843e3c44453921bd6273a233c`
- 执行日期：2026-09-22

---

## 一、最终计数口径（23 / 18 / 5）

| 口径 | 数值 |
|---|---|
| 独立时长**渲染位置**总数 | **23** |
| F3-A 之前已有 toggle | **13** |
| F3-A 之前没有 toggle | **10** |
| F3-A 新增代表性入口 | **5** |
| F3-A 之后**带** toggle 的渲染位置 | **18**（13 + 5） |
| F3-A 之后**不带** toggle 的渲染位置 | **5**（10 − 5） |

> 早期报告曾写「22 个渲染点」「YearBillHeroCard 只有 1 个渲染点」，两处均为错误口径，
> 已在本轮（F3-A-R1）更正。

### YearBillHeroCard：3 个展示位置 / 1 个代表性入口

必须区分**展示位置**与**入口**，二者不是一回事：

| 展示位置 | 是否带 toggle | 说明 |
|---|---|---|
| 主值（`YearBillHeroCard.kt:115`，`value = cardDurationText`） | **有**，且唯一 | 本卡代表性入口；`valueModifier` 挂 `durationUnitToggle()`（第 125 行） |
| subtitle（`YearBillHeroCard.kt:118`，`"$cardDurationText $anchorYear"`） | **无** | 独立展示位置，与主值同源同文 |
| headline 金句（`YearBillHeroCard.kt:77` → `buildYearHeadline`，两个互斥分支的 `spoken`） | **无** | 独立展示位置，`spoken` 由同一个 `formatCompactDuration` 产生 |
| **合计** | **3 个展示位置 / 1 个代表性 toggle 入口** | |

即：YearBill 贡献 3 个渲染位置，其中只有主值 1 个带入口。**不得再写「YearBill 只有
一个渲染点」。**

### 我独立复核的证据（不机械转述）

对 `android/app/src/main/java` 全量 grep `formatDuration(` / `formatCompactDuration(` /
`DurationText(`：剔除 formatter 自身（`TimeUtils.kt`）、`StatsPage.kt` 的转发包装、
`DurationDisplay.kt` 的注释行与 `DurationText` 本体后，落在具体 UI 位置的调用点计数与
上述口径一致；YearBill 的 `cardDurationText`（主值 + subtitle）与 `spoken`（headline）
确为 3 个展示位置。

### 改动前已挂入口的调用点 11 处（覆盖 13 个渲染位置）

| # | 位置 |
|---|---|
| 1 | `HomeCompletedScreen.kt:512` — `durationUnitPill` |
| 2 | `ProfileHomeScreen.kt:198` — `ArchiveStat` 阅读时长 |
| 3 | `ReadingArchiveSection.kt:226` |
| 4 | `ReaderBookInfoSheet.kt:221` |
| 5 | `ReaderBookInfoSheet.kt:229` |
| 6 | `ReaderProgressSheet.kt:174` |
| 7 | `ReaderProgressSheet.kt:249` |
| 8 | `ReaderProgressSheet.kt:285` |
| 9 | `BookDetailStatsSection.kt:132` |
| 10 | `BookDetailStatsSection.kt:321` — `durationUnitPill` |
| 11 | `DurationDisplay.kt`（`DurationText` 本体，覆盖 `HomeCompletedScreen.kt:246` 与 `MyReadingScreen.kt:248` 两个渲染位置） |

11 处调用点覆盖 13 个渲染位置（第 11 项一处覆盖两个），与「13 个已有 toggle」一致。

父级普通点击的节点按 Codex 纠正为 **4 处**（`BookDetailStatsSection` 会话条目、
`HomeCompletedScreen` 每本卡、`MyReadingScreen` 每本历史条目、
`HomeReadingArchiveSection` 档案卡），**本片一律不触碰**。

P 级按纠正口径：**P0=0、P1=0、P2=0、P3=3**（文档承诺与实现不一致 / 隐藏长按不可发现 /
缺少 `onLongClickLabel`）。「无触觉反馈」按纠正属于产品裁决，**本片未加振动**。

---

## 二、实际新增入口清单（5 个语义组件，各 1 个）

| # | 语义组件 | 入口位置 | 挂载方式 |
|---|---|---|---|
| 1 | HomeMetricsSection | 今日阅读时长主值（`HomeMetricsSection.kt:109`） | `GridStat` 新增 `valueModifier` |
| 2 | SummaryGroup | 阅读时长主值（`SummaryGroup.kt:81`） | `MetricMicroCard` 的 `valueModifier` |
| 3 | CreationSection | 沉浸时长主值（`CreationSection.kt:99`） | `CreationMetricItem.valueModifier` → `MetricMicroCard` |
| 4 | HeatmapSection | 汇总时长胶囊（`HeatmapSection.kt:125`） | 直接链式挂到胶囊 `Text` |
| 5 | YearBillHeroCard | 年度累计阅读主值（`YearBillHeroCard.kt:125`） | `MetricMicroCard` 的 `valueModifier` |

逐文件校验「每个组件恰 1 个调用点」：

```
HomeMetricsSection: 1 call site(s)
SummaryGroup:       1 call site(s)
CreationSection:    1 call site(s)
HeatmapSection:     1 call site(s)   # 链式调用，故未被 valueModifier 模式命中
YearBillHeroCard:   1 call site(s)
```

### 明确排除的重复文本 / 图表节点

| 排除对象 | 理由 |
|---|---|
| YearBill `subtitle`（`"$cardDurationText $anchorYear"`） | 与主值同源同文；挂上即同卡第 2 个同名动作 |
| YearBill `headline` 金句（两个互斥分支） | 与主值同源（`buildYearHeadline` 内同一个 `spoken`）；挂上即第 3 个 |
| Heatmap 单日 tooltip（`HeatmapCellTooltip`） | 单日口径，且随选中态出现/消失，不是稳定入口 |
| Heatmap 365 个格子 | 格子已有「点击选中」主交互；长按会与点击竞争，且会让 TalkBack 同屏读到 365 个同名操作 |
| Trend 每根柱的 `TICK` 标签 | 图表刻度，不构成语义组件 |
| SummaryGroup「阅读天数」/ CreationSection「阅读天数」「笔记记录」「闪念灵感」 | 整数计数，**不经过任何 formatter**，不是时长 |
| `HomeReadingArchiveSection` 档案卡 | 属于父卡点击切片（F3-B），本片不处理 |
| 已有 toggle 的 13 个节点 | 不重复接线 |

---

## 三、每个入口的交互与语义

全部 5 个入口均由同一个生产 Modifier `durationUnitToggle()`
（`DurationDisplay.kt:76`）产生，行为一致：

| 交互 | 行为 |
|---|---|
| 普通点击 | **不触发 toggle**。`combinedClickable` 的 `onClick = {}`；无 `Role.Button`，无 Toast，无振动，无视觉动效 |
| 长按 | **只调用一次 toggle**（`onLongClick = toggle`，单次回调，不重复触发） |
| TalkBack 语义 | `onLongClickLabel = "切换时长显示单位"`；保持时长文字的**文本语义**，不加 `Role.Button` |
| 单位变化 | 走既有 `DurationPrefs.toggle()` 写 DataStore；**formatter、舍入、HOURS/DAYS 结果一律未改** |

标签抽为常量 `DURATION_UNIT_TOGGLE_LABEL`（`DurationDisplay.kt`），文案与仓库既有
`BookDetailStatsSection.kt:68` 的 `DURATION_UNIT_PILL_LONG_PRESS_LABEL` 一致。

### 文案/KDoc 承诺修正

按契约删除「任意时长文本都能长按」，改为：

> 时长会跟随全局显示单位；在支持的阅读时长文本或卡片代表区域长按，可快速切换小时/天。

改动位置：`DurationPrefs.kt`（`DurationUnit` 与 `DurationPrefs` 的 KDoc）、
`DurationDisplay.kt` 文件头注释，并补充「入口粒度是语义组件而非任意文本」的说明。

---

## 四、代码改动

**生产代码（8 个授权文件）**

1. `DurationPrefs.kt` — 承诺文案修正（注释，无行为改动）
2. `DurationDisplay.kt` — 新增 `DURATION_UNIT_TOGGLE_LABEL` 常量；`durationUnitToggle()` 补 `onLongClickLabel`
3. `MetricMicroCard.kt` — **新增兼容参数** `valueModifier: Modifier = Modifier`（默认行为不变），仅应用到主值 `Text`
4. `HomeMetricsSection.kt` — 私有 `GridStat` 新增 `valueModifier`（默认 `Modifier`，其余 3 个格子行为不变）
5. `SummaryGroup.kt`
6. `CreationSection.kt` — `CreationMetricItem` 新增 `valueModifier` 字段（默认 `Modifier`）
7. `HeatmapSection.kt`
8. `YearBillHeroCard.kt`

**未修改**：`SharedComponents.SectionCard`、`MyReadingScreen`、`HomeCompletedScreen`、
`BookDetailStatsSection`、`HomeReadingArchiveSection`。

**新增测试（1 个 androidTest 文件）**

`android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/stats/DurationUnitToggleEntryTest.kt`
—— 10 个用例，全部渲染**生产组件**（`HomeMetricsSection` / `SummaryGroup` /
`CreationSection` / `HeatmapSection` / `YearBillHeroCard`），测试自己不造任何
clickable Text 自证；只替换根注入的 `LocalDurationUnitToggle` 来计数。

覆盖要求逐条对应：

| 要求 | 用例 |
|---|---|
| 1. 节点存在长按语义标签 | 各组件 `assertSingleEntry` 断言标签等于 `DURATION_UNIT_TOGGLE_LABEL` |
| 2. 长按只触发一次 toggle | `*_hasSingleToggleEntryAndTogglesOnce` ×5，断言 `toggles == 1` |
| 3. 普通点击不触发 toggle | `*_plainClickDoesNotToggle` ×5 |
| 4. YearBill 只存在一个切换动作 | `yearBillMainValue_hasSingleToggleEntryAndTogglesOnce` |
| 5. Heatmap 汇总只存在一个入口 | `heatmapSummary_hasSingleToggleEntryAndTogglesOnce`（造满 365 个格子） |
| 6. subtitle / headline 不出现重复动作 | 递归计数整棵子树，计数 >1 即失败（仅在已渲染的 YearBill 子树内） |
| 7. HOURS/DAYS 文本由既有 formatter 测试负责 | 本文件不复制格式算法，不依赖中文格式化文本定位 |

### 自动化覆盖的**实际边界**（收窄后的声明）

测试只渲染 **5 个生产组件**：`HomeMetricsSection`、`SummaryGroup`、`CreationSection`、
`HeatmapSection`、`YearBillHeroCard`。因此：

- ✅ 这 5 个组件的「入口唯一性 + 长按一次 + 点击不触发」**有自动化保证**；
- ❌ **`TrendSection` 没有自动化渲染覆盖** —— 本测试文件不渲染它，
  `sampleStats()` 里的 `trend` 字段只是入参，不会让 `TrendSection` 被组合或断言。

Trend 柱刻度**不带入口**这件事，本片是作为**产品契约**明确排除的
（依据：图表刻度不构成语义组件，见第二节排除清单），**不是**由本测试文件防住的。
若将来有人给 Trend 柱挂上入口，**当前测试不会失败**。需要防住这一点应另开切片补渲染
覆盖或加静态检查，本片（含 F3-A-R1）刻意不扩大到新增 Trend 测试。

「入口唯一性」用**遍历计数**而非「取第一个节点」：在**已渲染的**子树内，新增的重复入口
会让断言立刻打穿，不会被引擎静默忽略。

---

### 验证

### 命令、exit code 与结果

| 命令 | exit code | 结果 |
|---|---|---|
| `.\gradlew.bat --console=plain :app:testDebugUnitTest --tests "com.creationreadingassistant.ui.util.TimeUtilsTest" --rerun` | **0** | BUILD SUCCESSFUL；**tests=16, skipped=0, failures=0, errors=0** |
| `.\gradlew.bat --console=plain :app:compileDebugKotlin --rerun` | **0** | BUILD SUCCESSFUL（仅 1 条既有 deprecation warning：`LibraryBrowserRoute.kt:145` 的 `Icons.Outlined.Sort`，与本片无关） |
| `.\gradlew.bat --console=plain :app:compileDebugAndroidTestKotlin --rerun` | **0** | BUILD SUCCESSFUL（首次编译曾报 3 个错误：`onAllNodes` 未解析、`SemanticsConfiguration.getOrNull` 需 `androidx.compose.ui.semantics.getOrNull`；已修正后通过） |

测试数量：**16 个 JVM 用例全部通过**（`TimeUtilsTest`）。
新增的 10 个 Compose 用例为 **instrumentation**，按任务约束**只跑到编译门禁，未在设备执行**。

### git diff --check

对 8 个授权生产文件执行 `git diff --check`：**exit 0，无输出**（无空白/冲突标记问题）。

> 全仓 `git diff --check` 会输出大量 `CRLF will be replaced by LF` 警告，是该工作区既有
> 的换行设置问题，覆盖数百个本片未触碰的文件，与本片改动无关。

### ⚠️ 本节数据已被 F3-A-R1 部分取代

`DurationDisplay.kt` 与 `DurationUnitToggleEntryTest.kt` 在 **F3-A-R1** 中又发生了改动
（KDoc 边界重写 + 弃用手势 API 迁移），下述两个文件的 SHA-256 **已失效**。
**当前有效哈希、当前编译结果、收窄后的覆盖声明以
[agent-result-s15-f3-a-r1-claude.md](agent-result-s15-f3-a-r1-claude.md) 为准。**

### 文件 SHA-256（F3-A 时点；标 † 者已被 R1 取代）

```
9f26f7a2a748b2dd9ed5de405e45324e8a2c6d3af4cb8a113472ab339486445c  android/app/src/main/java/com/creationreadingassistant/data/settings/DurationPrefs.kt
5b5e21294974bb76cf70207359e8c9bc9ba092c23ca39961ff033854fcdf4b97  android/app/src/main/java/com/creationreadingassistant/ui/util/DurationDisplay.kt  †已被 R1 取代
900a65f3dc1c4360629f8efdd824fcb41e5ecd753003737ac71fbae916617433  android/app/src/main/java/com/creationreadingassistant/ui/components/MetricMicroCard.kt
76b9cb5f598b3928de99a4cbb938e6e872210b2a3bcecafc2a9efc5832460806  android/app/src/main/java/com/creationreadingassistant/ui/screen/home/components/HomeMetricsSection.kt
9bf3e164e3e5ba067adddff8c2afe3db4ca39f5a1410b2cac3ecfdae760a5553  android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/components/SummaryGroup.kt
2721a642350aa25b39d8cf2398e68f53872a73f1416406bdb8fe830b8ac650f4  android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/components/CreationSection.kt
156ad2b24541ce006b5b4a6dbf91599e1af37c160877c5d6e54d588ff3d8201a  android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/components/HeatmapSection.kt
ae24345221a2c5cec49944871a46fabd5202e3b25d9f65230f8da5b8e006f71b  android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/components/YearBillHeroCard.kt
f88ee65c7e7062b53704212897fb8f526fd46079e379d445b8e007c36c13840a  android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/stats/DurationUnitToggleEntryTest.kt  †已被 R1 取代
```

---

## 六、开始与结束 git status

**开始**：`git status --short` = **379 行**；HEAD = `5aed593`

**结束**：`git status --short` = **382 行**；HEAD = `5aed593`（未变）

净新增 3 个未跟踪文件（untracked）：

```
?? android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/stats/DurationUnitToggleEntryTest.kt   ← 本片新增
?? android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/shelf/DurationPillGestureTest.kt      ← 窗口 C 的 F3-B 文件，本片未触碰
?? android/app/src/androidTest/java/com/creationreadingassistant/ui/screen/readinghistory/                       ← 窗口 C 的 F3-B 文件，本片未触碰
```

除上述 3 项外，开始与结束的 `git status --short` **逐行一致**：本片未新增、未删除、
未改动任何其他文件（含窗口 C/D 的文件、协调看板、handoff、Obsidian）。

---

## 七、声明

- 只新增 1 个测试文件与 1 份本报告；仅在 8 个授权文件内改动。
- **未**修改原仓库 `D:\develop\Code\Codex\creation-reading-assistant`（本会话全程未进入该目录）。
- **未**执行 `reset`、`checkout`、`clean`、`stash`、`stage`、`commit`、`push`；`git diff --cached` 为空。
- **未**执行全仓格式化、全量 JVM 测试、Lint、APK 构建、ADB、真机或模拟器验证。
- **未**修改窗口 C 的 legacy EPUB 文件、窗口 D 的报告或测试。
- **未**修改 `SharedComponents.SectionCard`、`MyReadingScreen`、`HomeCompletedScreen`、
  `BookDetailStatsSection`、`HomeReadingArchiveSection`。
- 新增的 10 个 Compose 用例**只跑到编译门禁**，尚未在设备执行 —— 真机 / TalkBack 验证
  属于 F3-C 切片，不在本片范围。

完成后停止，未领取下一项任务。
