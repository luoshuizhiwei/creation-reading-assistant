# 阶段 E：G6 时长单位集成边界更正（2026-09-30）

## 核查结果

`2026-09-29-stage-e-integration-decision-package.md` §2.3 把 G6 的“其余独立文件”列为 7 个，但该批次的目标是全局时长单位切换。对恢复仓库工作树的 `android/app/src/main/java` 做 `rg -l 'LocalDurationUnit|DurationPrefs|DurationUnit'`，实际有 **22 个生产文件**引用这套新类型或 CompositionLocal；相应测试至少有 **5 个**。这 27 个文件均是相对 HEAD 新增或已修改。旧清单不能当成完整的 G6 集成清单，也不能只拷贝 7 个文件便宣称“全局切换”完成。

生产路径按功能分组：

- 根部与公共契约：`MainActivity.kt`、`data/settings/DurationPrefs.kt`、`ui/util/DurationDisplay.kt`、`ui/util/TimeUtils.kt`。
- 首页/归档/历史：`ui/screen/home/components/HomeMetricsSection.kt`、`HomeReadingArchiveSection.kt`、`ui/screen/homearchive/HomeCompletedScreen.kt`、`ui/screen/readinghistory/MyReadingScreen.kt`。
- 我的/设置：`ui/screen/profile/AppearanceSubPage.kt`、`ProfileHomeScreen.kt`、`ReadingArchiveSection.kt`。
- 阅读器与书架：`ui/screen/reader/ReaderHelpers.kt`、`reader/sheets/ReaderBookInfoSheet.kt`、`ReaderProgressSheet.kt`、`ui/screen/shelf/BookDetailStatsSection.kt`、`ShelfUtils.kt`。
- 统计：`ui/screen/stats/StatsPage.kt`、`stats/components/CreationSection.kt`、`HeatmapSection.kt`、`SummaryGroup.kt`、`TrendSection.kt`、`YearBillHeroCard.kt`。
- 测试：`data/settings/DurationPrefsTest.kt`、`DurationUnitTest.kt`、`ui/util/TimeUtilsTest.kt`、`ui/screen/home/HomeReadingArchiveSectionTest.kt`、`ui/screen/stats/components/YearBillHeadlineTest.kt`。

## 集成边界

这些是**符号依赖清单**，尚不是可直接提交的补丁。`MainActivity.kt` 同时含 S06 启动主题、引导异步、滚轮和诊断等 hunk；多个展示页也有其他功能改动。下一步必须在 E 盘从 HEAD 或已批准的精确候选构造 G6 hunk，按文件审查，运行编译和定向 JVM，之后再验证与 S06 联合态。不能全文件拷贝工作树，也不能把其他 HOLD 功能带入 G6。

本轮只读核查代码并新增本报告；未改源码、未 stage/commit/push。阶段 E 整体仍为 **4/5（80%）**。
