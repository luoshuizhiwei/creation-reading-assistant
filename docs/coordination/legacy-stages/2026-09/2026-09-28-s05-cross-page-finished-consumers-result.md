# S05 跨页消费者执行结果报告：取消读完后的残余 100% 不再被当作已完成

> **Codex 2026-09-28 独立复核：代码/JVM 范围限定通过。** 当前 `android/app/build/test-results/testDebugUnitTest/` 中所列 8 套件 XML 逐一回读，合计 40/40、0 失败/错误/跳过，时间 18:25。报告所述跨页 UI 行为尚无 MuMu 截图及 DB 前后对账，不能记为设备验收。下一轮由单一 MuMu 所有者在 qahost 隔离夹具验收取消读完后的首页、归档、我的阅读、我的页与统计页；同一批次做文件/DB/设置与设备密度恢复对账。

- 日期：2026-09-28
- 执行窗口：S05 跨页消费者（ZCode / GLM 5.3 Flash），与总指挥及其他窗口并行
- 仓库：`D:\develop\Code\Codex\creation-reading-assistant-recovery-20260919`（分支 `codex/recover-worktree-20260920`；共享工作区含大量他人未提交改动，本报告只列**本窗口 hunk**）
- 上游：[`2026-09-28-s05-cancel-finished-state-result.md`](2026-09-28-s05-cancel-finished-state-result.md)（S05-① 书架契约 + Codex 复核 + §7 产品决策点第 2 项）

## 一、结论（TL;DR）

**已修复（JVM 范围）。** 上一轮报告 §7 列出的全部跨页消费者——首页继续阅读、首页「最近读完」、首页已完成归档、我的阅读、我的页完读数、统计页完读计数与书态分布、继续阅读卡片文案——共 **10 个判定站点**全部改为尊重显式 `completion_state`，与书架 `bookStatus` 契约对齐。所有页面的 `completion_state` 在**现有查询/流中已可得**（实体直通或 `StatsProgressRow` 投影已含该列），**零 DAO/查询改动，无需 SEAM REQUEST**。红测 6 判别用例全红（30 tests / 6 failed，EXIT=1）→ 修复后 8 套件定向 JVM **40/40 全绿**（0 fail / 0 err / 0 skip，EXIT=0）。未改 99.4%、未伪造位置、未扩大查询范围。

## 二、调用点盘点与数据可得性（任务要求先行列出）

| # | 页面 | 站点 | 修复前判定 | completion_state 可得性 | 处置 |
|---|---|---|---|---|---|
| 1 | 首页·继续阅读 | `HomeViewModel.buildContinueBooks`（排除已完成） | 裸 `percent >= 99.5f` | ✔ 完整 `ReadingProgressEntity`（`repository.observeProgress()`） | **已修** |
| 2 | 首页·最近读完 | `HomeViewModel` `completed` 过滤 | `state=="finished" \|\| percent>=99.5` | ✔ 同上 | **已修** |
| 3 | 首页·继续阅读（Sheet/模型） | `HomeContinueModels.buildContinueItems` | 裸 `pct >= 99.5f` | ✔ 完整实体（`progressById`） | **已修** |
| 4 | 首页·已完成归档 | `HomeArchiveViewModel` completedBooks | `state==FINISHED \|\| percent>=99.5` | ✔ 完整实体（`observeProgress()`） | **已修** |
| 5 | 档案·我的阅读 | `MyReadingViewModel.buildMyReadingUiState` | **用 percent≥99.5 把状态覆盖成 FINISHED**（比 OR 更重） | ✔ 完整实体 | **已修** |
| 6 | 我的页·完读数 | `ProfileViewModel.homeSummary` | `state=="finished" \|\| percent>=99.5` | ✔ `StatsProgressRow` 投影含 `completion_state`（`observeStatsProgress()`，`StatsProjections.kt:14-18`） | **已修** |
| 7 | 统计页·完读计数 | `StatsRepository.computeWithWindowAggregates` | 同 #6 模式（实体行） | ✔ 完整实体（`progressDao.observeAllActive()`） | **已修** |
| 8 | 统计页·书态分布 | `StatsPage.computeStats` | `state==FINISHED \|\| percent>=99.5` | ✔ `StatsProgressRow`（`p?.completion_state` 当场就在读） | **已修** |
| 9 | 继续阅读卡片文案 | `HomeContinueCards` + `HomeContinueSection` 经 `formatBookProgressForCard(pct)` | 纯百分比 →「已读完」 | ✔ `ContinueItem`/`ContinueCard` 均持完整实体 | **已修**（透传状态） |
| 10 | 共享工具 | `BookReadiness.formatBookProgressForCard` | 纯 Float 参数无状态 | — | **已修**（新增可选状态参数） |

**数据可得性结论**：所有站点所用的流/投影已携带 `completion_state`，无需改任何 DAO/查询/投影，**无 SEAM REQUEST**。

**排除项（核对后无需改）**：`MyReadingScreen:508`、`HomeCompletedSection:159`、`HomeCompletedScreen:439`、`HomeContinueCards` 封面、`ReadingArchiveSection:134` 的 `BookCover` 均传 `percent=null`（无藏书印）；`HomeArchiveFormatters.kt:108` 用 `completed_at`（取消读完时列表里已无此书）；`HomeCompletedSection` 的「已读完」文案无条件显示（列表项按构造已是 finished）。

## 三、实现方式

**两个共享助手（口径与书架 `ShelfModels.bookStatus` 一致，KDoc 记录完整契约与写入面不变量）：**

1. `ui/util/BookReadiness.kt`（所有权内）——实体版：
   - `isReadingFinished(progress: ReadingProgressEntity?): Boolean` = `progress?.readingState == FINISHED`；供首页/继续阅读/归档/我的阅读消费（这些页面不反向依赖书架包）。
   - `formatBookProgressForCard(progress, completionState: String? = null)`：「已读完」要求 `percent>=99.5` **且** 显式 `finished`；取消读完的残余 100% 显示「100%」。
2. `data/repository/StatsRepository.kt`（所有权内）——投影版：`internal fun StatsProgressRow.isReadingFinishedRow()`，供我的页完读数复用并在 `ReadingStatsPolicyTest` 直接测试。`computeWithWindowAggregates` 内的实体行用 `it.readingState == FINISHED` 内联（数据层不 import ui.util）。

**行为语义**：
- 取消读完（percent=100 残余 + reading）→ **回到继续阅读**、退出「最近读完」/归档、我的阅读「在读」页签、统计分布计「在读」、卡片显示「100%」；
- 真实 finished（含读满 100% 由 merge 写入的）→ 一切保持既有行为；
- 手动 finished（进度未到阈值）→ 归档/计数仍算完成（显式意图），卡片数字文案维持既有（完成标识由书架徽章表达，本函数不新增文案分支）。

## 四、实际改动文件（仅本窗口 hunk）

**主代码（9 文件）**

1. `android/app/src/main/java/com/creationreadingassistant/ui/util/BookReadiness.kt` — 新增 `isReadingFinished`；`formatBookProgressForCard` 增加状态参数与契约 KDoc。
2. `.../ui/viewmodel/HomeViewModel.kt` — 站点 1、2。
3. `.../ui/screen/home/HomeContinueModels.kt` — 站点 3；`ContinueItem` 新增 `completionState` 字段并透传。
4. `.../ui/viewmodel/HomeArchiveViewModel.kt` — 站点 4（顺带移除失效 import）。
5. `.../ui/viewmodel/MyReadingViewModel.kt` — 站点 5（移除百分比对状态的覆盖）。
6. `.../ui/viewmodel/ProfileViewModel.kt` — 站点 6。
7. `.../data/repository/StatsRepository.kt` — 站点 7 + 投影版助手定义。
8. `.../ui/screen/stats/StatsPage.kt` — 站点 8。
9. `.../ui/screen/home/HomeContinueCards.kt` + `.../home/components/HomeContinueSection.kt` — 站点 9（文案传状态）。

**测试（7 文件，其一新建）**

10. `.../test/.../ui/screen/home/HomeContinueModelsTest.kt` — 改写「接近读完排除」为判别用例（真实 finished 排除 / 取消读完保留）；新增 `ContinueItem.completionState` 透传断言。
11. `.../ui/viewmodel/HomeViewModelTest.kt` — 夹具 b2 改显式 finished；新增 `cancelled finished returns to continue and leaves completed`。
12. `.../ui/viewmodel/HomeArchiveViewModelTest.kt` — 夹具 b2 改显式 finished、测试更名；新增 `cancelled finished is not archived as completed`。
13. `.../ui/viewmodel/MyReadingViewModelTest.kt` — 改写为 5 书判别矩阵（含 b5 = 100% + reading → 在读页签）。
14. `.../ui/viewmodel/ProfileViewModelTest.kt` — 夹具加取消读完行（计数不变断言）。
15. `.../ui/screen/stats/StatsComputationTest.kt` — 新增分布判别用例（`stats.status.completed=1 / reading=2`）。
16. `.../ui/util/BookReadinessProgressLabelTest.kt`（新建）— `isReadingFinished` 与文案函数的判别矩阵（7 用例）。
17. `.../data/repository/ReadingStatsPolicyTest.kt` — 新增 `isReadingFinishedRow` 判别用例（含历史值 `completed` 兼容）。

## 五、测试证据

**红测**（修复前，仅判别用例；6 失败精确对应 6 个消费者）：

```
./gradlew :app:testDebugUnitTest --tests ...HomeContinueModelsTest --tests ...HomeViewModelTest \
  --tests ...HomeArchiveViewModelTest --tests ...MyReadingViewModelTest \
  --tests ...ProfileViewModelTest --tests ...StatsComputationTest
→ BUILD FAILED, EXIT=1, 30 tests completed, 6 failed
```

XML：HomeContinueModelsTest 7/1/0/0；HomeViewModelTest 7/1/0/0；HomeArchiveViewModelTest 4/1/0/0；MyReadingViewModelTest 6/1/0/0；ProfileViewModelTest 4/1/0/0；StatsComputationTest 2/1/0/0（tests/failures/errors/skipped）。

**绿测**（修复后，8 套件含新建/扩展测试）：

```
./gradlew :app:testDebugUnitTest --tests（上述 6 套件）--tests ...BookReadinessProgressLabelTest --tests ...ReadingStatsPolicyTest
→ BUILD SUCCESSFUL, EXIT=0
```

XML（tests/failures/errors/skipped）：

| 套件 | tests | failures | errors | skipped |
|---|---|---|---|---|
| HomeContinueModelsTest | 7 | 0 | 0 | 0 |
| HomeViewModelTest | 7 | 0 | 0 | 0 |
| HomeArchiveViewModelTest | 4 | 0 | 0 | 0 |
| MyReadingViewModelTest | 6 | 0 | 0 | 0 |
| ProfileViewModelTest | 4 | 0 | 0 | 0 |
| StatsComputationTest | 2 | 0 | 0 | 0 |
| BookReadinessProgressLabelTest | 7 | 0 | 0 | 0 |
| ReadingStatsPolicyTest | 3 | 0 | 0 | 0 |
| **合计** | **40** | **0** | **0** | **0** |

过程记录：绿测首轮编译错误一次——`computeWithWindowAggregates` 的 progress 是实体而非 `StatsProgressRow`（接收者不匹配），改为实体 `readingState` 内联后通过；另有一次测试期望笔误（`"0% 未读"` 是 `HomeContinueSection` 调用方包装文案，格式化函数返回「未读」），修正测试后全绿。全量编译闭包随 `testDebugUnitTest` 隐式验证；androidTest 静态核对无对改动 API 的引用（grep 为空）。

## 六、逐页面清单（任务要求：已修 / 需接缝 / 未验）

| 页面 | 已修 | 需接缝 | 未验 |
|---|---|---|---|
| 首页·继续阅读（列表构建 + Sheet 卡片文案） | ✔ 站点 1、3、9 | 无 | 真机 UI（继续阅读卡显示「100%」而非「已读完」） |
| 首页·最近读完 | ✔ 站点 2 | 无 | 真机 UI（取消后立即从该块消失） |
| 首页·已完成归档（HomeCompletedScreen/Section） | ✔ 站点 4 | 无 | 真机 UI |
| 档案·我的阅读（页签/计数） | ✔ 站点 5 | 无 | 真机 UI（取消后回到「在读」页签） |
| 我的页·完读数（homeSummary） | ✔ 站点 6 | 无 | 真机 UI（数字实时变化） |
| 统计页·完读计数 + 书态分布 | ✔ 站点 7、8 | 无 | 真机 UI |
| 共享工具 BookReadiness | ✔ 站点 10 | 无 | — |

**需接缝（SEAM REQUEST）：无。** 所有页面数据可得性已足够；未动 DAO/投影/schema。

## 七、边界与未验范围

- **真机/MuMu UI 未验**（本窗口不运行 ADB；MuMu 由窗口 1 独占）：上述表格右列的 UI 表现需设备验收；残余窗口内继续阅读卡片的进度条仍显示满格（数据残余，未伪造）。
- `:app:compileDebugAndroidTestKotlin` 未运行（归集成者；已静态核对无引用）。
- 全量 JVM 套件未跑（并行纪律：定向归窗口，全量归集成者）。
- 残余诚实性：取消读完 → 下次真实阅读之间，各页把该书按「在读 · 100% 残余」处理；打开书继续阅读会由 locator 重算自愈。pre-finish 真实百分比的恢复仍属上游报告 §7 决策点 1（需 schema 决策）。
- `computeReadingStreak`/趋势分桶等纯统计与完成态无关，未触碰。

## 八、协作纪律遵守情况

- 未 commit / push / reset / checkout；未撤销任何他人改动。
- 未触碰 SettingsStore、MainActivity、LocalZipBackup、BookRepository、ShelfModels、数据库 schema（`ShelfBookActions`/`BookRepository` 本轮零改动）。
- 无越界需求，未提交 SEAM REQUEST。
- 三次 Gradle 运行（红测 18:13、绿测 18:21/18:24）前均确认 `app/build` 4 分钟窗口零写入，未与他窗争用。
- 未运行 ADB/设备操作；未触碰原始仓库、正式书库与真实手机。
