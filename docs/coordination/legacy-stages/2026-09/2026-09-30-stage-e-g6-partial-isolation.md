# 阶段 E：G6 时长单位精确候选阶段记录（2026-09-30）

## 已实测

以已验证 S06 最小候选为底，在 E 盘构造四层 G6 候选，不改恢复仓库源码：

1. `E:\creation-reading-assistant\stage-e-g6-core-20260930\android`：加入 `DurationPrefs.kt`、`DurationDisplay.kt`、`TimeUtils.kt` 的单位/样式格式化部分、`MainActivity.kt` 的根部注入/CompositionLocal，以及三个定向测试。`TimeUtils.kt` 工作树后半的 `formatRelativeTime` 属其他功能，未纳入；同文件测试也只截取相应时长断言。第一次完整复制测试导致 `formatRelativeTime` 未解析，修正测试边界后 `:app:testDebugUnitTest --tests '*DurationPrefsTest' --tests '*DurationUnitTest' --tests '*TimeUtilsTest'` 成功，XML **23/23**、0 失败/错误/跳过。原失败日志与修正后日志均留在 `_evidence/`。
2. `E:\creation-reading-assistant\stage-e-g6-consumers1-20260930\android`：在上述核心候选上增加 8 个较小的展示页/辅助文件与 `HomeReadingArchiveSectionTest.kt`。提取时明确排除 `HomeMetricsSection.kt` 的 `compact = true` 布局变动，以及 `ReaderProgressSheet.kt` 的 `CraSlider` 替换。`:app:testDebugUnitTest` 对四个相关类成功，XML **25/25**、0 失败/错误/跳过；日志 `_evidence/test-consumers1.log`，提取脚本 `_evidence/build_consumers1.py`。
3. `E:\creation-reading-assistant\stage-e-g6-consumers2-20260930\android`：再加入 `MyReadingScreen.kt`、`BookDetailStatsSection.kt`、`StatsPage.kt` 的时长相关代码；剔除 `BookDetailStatsSection` 的相对时间格式化和 `StatsPage` 的 S05 完成态契约 hunk。编译与相同四类定向 JVM 成功，XML **25/25**、0 失败/错误/跳过；日志 `_evidence/test-consumers2.log`，提取脚本 `_evidence/build_consumers2.py`。
4. `E:\creation-reading-assistant\stage-e-g6-consumers3-20260930\android`：再从隔离候选的 `HomeCompletedScreen.kt` 精确加入统计总时长与已读书籍时长 pill；保留基线的 S04 重选文件路由，未复制工作树的 Snackbar 改动。编译与四类定向 JVM 成功，XML **25/25**、0 失败/错误/跳过；日志 `_evidence/test-consumers3.log`，提取脚本 `_evidence/build_completed.py`。这里仅有编译门禁，pill 点击/长按手势仍未做设备验收。

四层构建均使用 E 盘 `GRADLE_USER_HOME`、`--offline --max-workers=1`，未在 D/C 新建构建目录。核心候选脚本为 `stage-e-g6-core-20260930\_evidence\build_core.py`。G6 的完整符号依赖见 [边界更正](../../decisions/2026-09/2026-09-30-stage-e-g6-scope-correction.md)。

## 尚未完成

- 这只是 **16/22 个生产文件**和 **4/5 个关联测试文件**的分层隔离候选，不是可提交的全局 G6。余下 6 个生产文件为 `AppearanceSubPage.kt`、`ProfileHomeScreen.kt`、`CreationSection.kt`、`HeatmapSection.kt`、`SummaryGroup.kt`、`YearBillHeroCard.kt`；还有 `YearBillHeadlineTest.kt`。它们与其他 UI/统计功能 hunk 大量交叠，需要逐块提取并继续编译/定向测试。
- 尚未生成可复算的最终 G6 补丁，也未验证完整 G6 + S06 的所有展示行为或 MuMu 切换。不得把 25/25 外推为最终批次通过。
- 本轮未 stage/commit/push，未更改原始仓库或正式包。阶段 E 整体仍 **4/5（80%）**。
