package com.creationreadingassistant.ui.screen.stats.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.screen.home.components.HomeMetricsSection
import com.creationreadingassistant.ui.screen.stats.BookStatus
import com.creationreadingassistant.ui.screen.stats.HeatmapCell
import com.creationreadingassistant.ui.screen.stats.StatsUi
import com.creationreadingassistant.ui.screen.stats.TrendItem
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.util.DURATION_UNIT_TOGGLE_LABEL
import com.creationreadingassistant.ui.util.LocalDurationUnitToggle
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * S15-F3-A：无父级点击冲突的时长组件「单一长按入口」契约测试。
 *
 * 覆盖 5 个语义组件共 5 个入口：
 *  1. [HomeMetricsSection] 今日阅读时长主值；
 *  2. [SummaryGroup] 阅读时长摘要；
 *  3. [CreationSection] 沉浸时长；
 *  4. [HeatmapSection] 汇总时长胶囊；
 *  5. [YearBillHeroCard] 年度累计阅读主值。
 *
 * 全部渲染**生产组件**，测试自己不造任何 clickable Text 自证：唯一被测的长按手势来自
 * 生产的 [com.creationreadingassistant.ui.util.durationUnitToggle]。测试只提供
 * [LocalDurationUnitToggle]（它本就是根注入的依赖，MainActivity 提供的是写 DataStore
 * 的实现），以便计数切换次数。
 *
 * 「入口唯一性」靠对整棵子树做**计数**而非「取第一个节点」，这样新增的重复入口会直接
 * 把断言打穿：
 * - YearBill 的主值 / subtitle / 金句同源同文，多挂任何一个都会把计数推到 ≥2；
 * - Heatmap 造满 365 个格子，格子或单日 tooltip 被挂上会立刻爆计数。
 *
 * 定位用生产的稳定 testTag 缩小范围 + 长按标签匹配入口，不依赖中文格式化文本；
 * HOURS/DAYS 的具体文案由既有 formatter 测试负责，本文件不复制格式算法。
 *
 * 注意：本文件是 instrumentation 用例，本切片只跑到编译门禁，未在设备执行。
 */
class DurationUnitToggleEntryTest {

    @get:Rule
    val composeRule = createComposeRule()

    /* ── 入口 1：HomeMetricsSection 今日阅读时长主值 ──────────────────── */

    @Test
    fun homeMetricsTodayValue_hasSingleToggleEntryAndTogglesOnce() {
        val counter = ToggleCounter()
        render(counter) {
            HomeMetricsSection(
                thisWeekNew = 3,
                readingCount = 5,
                completedCount = 2,
                todayReadingMs = 90 * 60_000L,
            )
        }
        assertSingleEntry(METRICS_ROW_TAG)
        longPressEntry(METRICS_ROW_TAG)
        assertEquals("长按今日时长必须只切换一次", 1, counter.toggles)
    }

    @Test
    fun homeMetricsTodayValue_plainClickDoesNotToggle() {
        val counter = ToggleCounter()
        render(counter) {
            HomeMetricsSection(
                thisWeekNew = 3,
                readingCount = 5,
                completedCount = 2,
                todayReadingMs = 90 * 60_000L,
            )
        }
        clickEntry(METRICS_ROW_TAG)
        assertEquals("普通点击不得触发切换", 0, counter.toggles)
    }

    /* ── 入口 2：SummaryGroup 阅读时长摘要 ───────────────────────────── */

    @Test
    fun summaryGroup_hasSingleToggleEntryAndTogglesOnce() {
        val counter = ToggleCounter()
        render(counter) { SummaryGroup(stats = sampleStats()) }
        assertSingleEntry(SUMMARY_TAG)
        longPressEntry(SUMMARY_TAG)
        assertEquals("长按阅读时长摘要必须只切换一次", 1, counter.toggles)
    }

    @Test
    fun summaryGroup_plainClickDoesNotToggle() {
        val counter = ToggleCounter()
        render(counter) { SummaryGroup(stats = sampleStats()) }
        clickEntry(SUMMARY_TAG)
        assertEquals("普通点击不得触发切换", 0, counter.toggles)
    }

    /* ── 入口 3：CreationSection 沉浸时长 ────────────────────────────── */

    @Test
    fun creationSection_hasSingleToggleEntryAndTogglesOnce() {
        val counter = ToggleCounter()
        render(counter) { CreationSection(stats = sampleStats()) }
        // 笔记数 / 灵感数 / 阅读天数都是整数计数，不得成为入口。
        assertSingleEntry(CREATION_TAG)
        longPressEntry(CREATION_TAG)
        assertEquals("长按沉浸时长必须只切换一次", 1, counter.toggles)
    }

    @Test
    fun creationSection_plainClickDoesNotToggle() {
        val counter = ToggleCounter()
        render(counter) { CreationSection(stats = sampleStats()) }
        clickEntry(CREATION_TAG)
        assertEquals("普通点击不得触发切换", 0, counter.toggles)
    }

    /* ── 入口 4：HeatmapSection 汇总时长胶囊 ─────────────────────────── */

    @Test
    fun heatmapSummary_hasSingleToggleEntryAndTogglesOnce() {
        val counter = ToggleCounter()
        render(counter) { HeatmapSection(heatmap = sampleHeatmap()) }
        assertSingleEntry(HEATMAP_TAG)
        longPressEntry(HEATMAP_TAG)
        assertEquals("长按热力图汇总必须只切换一次", 1, counter.toggles)
    }

    @Test
    fun heatmapSummary_plainClickDoesNotToggle() {
        val counter = ToggleCounter()
        render(counter) { HeatmapSection(heatmap = sampleHeatmap()) }
        clickEntry(HEATMAP_TAG)
        assertEquals("普通点击不得触发切换", 0, counter.toggles)
    }

    /* ── 入口 5：YearBillHeroCard 年度累计阅读主值 ───────────────────── */

    @Test
    fun yearBillMainValue_hasSingleToggleEntryAndTogglesOnce() {
        val counter = ToggleCounter()
        render(counter) { YearBillHeroCard(stats = sampleStats(), anchorYear = 2026) }
        assertSingleEntry(YEAR_BILL_TAG)
        longPressEntry(YEAR_BILL_TAG)
        assertEquals("长按年度累计阅读必须只切换一次", 1, counter.toggles)
    }

    @Test
    fun yearBillMainValue_plainClickDoesNotToggle() {
        val counter = ToggleCounter()
        render(counter) { YearBillHeroCard(stats = sampleStats(), anchorYear = 2026) }
        clickEntry(YEAR_BILL_TAG)
        assertEquals("普通点击不得触发切换", 0, counter.toggles)
    }

    /* ── 生产渲染与断言辅助 ──────────────────────────────────────────── */

    /** 单位切换计数，全部由生产组件真实触发。 */
    private class ToggleCounter {
        var toggles: Int = 0
    }

    private fun render(counter: ToggleCounter, content: @Composable () -> Unit) {
        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(
                    // 根部（MainActivity）真实提供的是写 DataStore 的实现；这里只替换同一
                    // CompositionLocal 的值来计数，不改任何被测组件的内部结构。
                    LocalDurationUnitToggle provides { counter.toggles++ },
                    LocalLayoutTokens provides DefaultLayoutTokens,
                ) {
                    Box(Modifier.requiredSize(393.dp, 800.dp)) { content() }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** 入口定位器：长按动作带统一的切换标签。走 unmerged 树，避免被父级合并后漏检。 */
    private fun isToggleEntry(): SemanticsMatcher = SemanticsMatcher(
        "OnLongClick label == $DURATION_UNIT_TOGGLE_LABEL",
    ) { node ->
        node.config.getOrNull(SemanticsActions.OnLongClick)?.label == DURATION_UNIT_TOGGLE_LABEL
    }

    /**
     * 断言 [rootTag] 子树**恰有 1 个**切换入口。
     *
     * 刻意用遍历计数而不是「取第一个」：新增的重复入口（subtitle / 金句 / 365 个格子）
     * 会让计数立刻 >1，而不是被静默忽略。
     */
    private fun assertSingleEntry(rootTag: String) {
        val root = composeRule.onNodeWithTag(rootTag, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("'$rootTag' 内必须恰有一个「$DURATION_UNIT_TOGGLE_LABEL」动作", 1, countToggleEntries(root))
    }

    private fun longPressEntry(rootTag: String) {
        entryIn(rootTag).performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    private fun clickEntry(rootTag: String) {
        entryIn(rootTag).performClick()
        composeRule.waitForIdle()
    }

    /**
     * 在 [rootTag] 子树内定位那**唯一**的入口节点。
     *
     * 先按标签匹配，再用「归属判断」把范围收进 [rootTag] 子树：单个组件测试里通常
     * 唯一命中，但保留归属判断可让同一棵树里出现多个入口时精确定位而不是误点。
     */
    private fun entryIn(rootTag: String): SemanticsNodeInteraction {
        val rootId = composeRule.onNodeWithTag(rootTag, useUnmergedTree = true).fetchSemanticsNode().id
        return composeRule.onNode(
            isToggleEntry() and SemanticsMatcher("属于 '$rootTag' 子树") {
                it.id == rootId || isDescendantOf(rootId, it)
            },
            useUnmergedTree = true,
        )
    }

    private fun isDescendantOf(rootId: Int, node: SemanticsNode): Boolean {
        var cursor: SemanticsNode? = node
        while (cursor != null) {
            if (cursor.id == rootId) return true
            cursor = cursor.parent
        }
        return false
    }

    private fun countToggleEntries(node: SemanticsNode): Int {
        var total = if (node.config.getOrNull(SemanticsActions.OnLongClick)?.label == DURATION_UNIT_TOGGLE_LABEL) 1 else 0
        node.children.forEach { total += countToggleEntries(it) }
        return total
    }

    private fun sampleStats(): StatsUi = StatsUi(
        totalReadingMs = 60 * 60_000L,
        readingDays = 3,
        readBooks = 2,
        completed = 1,
        sessionCount = 7,
        streakCurrent = 2,
        streakLongest = 5,
        status = BookStatus(reading = 1, completed = 1, unread = 2, unreadable = 0, total = 4),
        words = 42_000,
        speed = 350,
        noteCount = 5,
        inspirationCount = 8,
        trend = listOf(
            TrendItem("d1", "1/1", 15 * 60_000L, 2),
            TrendItem("d2", "1/2", 0L, 0),
            TrendItem("d3", "1/3", 30 * 60_000L, 3),
        ),
    )

    /** 365 天热力图：格子必须足够多，才能让「格子挂了入口」立刻暴露为计数 >1。 */
    private fun sampleHeatmap(): List<HeatmapCell> =
        (0 until 365).map { i ->
            HeatmapCell(
                date = LocalDate.of(2026, 1, 1).plusDays(i.toLong()),
                durationMs = if (i % 3 == 0) 30 * 60_000L else 0L,
                intensity = if (i % 3 == 0) 0.6f else 0f,
            )
        }

    private companion object {
        /** 与生产组件里已有的稳定 testTag 保持一致（禁止在测试中另造一套）。 */
        const val METRICS_ROW_TAG = "metrics-row"
        const val SUMMARY_TAG = "stats-summary"
        const val CREATION_TAG = "stats-creation"
        const val HEATMAP_TAG = "stats-heatmap"
        const val YEAR_BILL_TAG = "stats-year-bill-hero"
    }
}
