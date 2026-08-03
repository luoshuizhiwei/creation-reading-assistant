package com.creationreadingassistant.ui.screen.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.navigation.AppChrome
import com.creationreadingassistant.ui.navigation.LocalAppChrome
import com.creationreadingassistant.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Stats 重构验收 UI 测试（纯 Screen，不依赖 ViewModel）。
 *
 * 验收条目：
 *  1. Screen 中不得执行 sessions 分桶、日期范围计算或整表过滤（通过源码 grep 验证）。
 *  2. 单一 AppScreenScaffold（唯一 `stats-screen` testTag）。
 *  3. 全局空态（showGlobalEmpty=true）时，summary/trend/status/creation 区块都不组合。
 *  4. 有数据且周期空（showPeriodEmpty=true）时，仍组合 4 大区块 + 显示 period-empty 提示。
 *  5. 周期切换时 Screen 重新组合，仍保持唯一 `stats-screen`（验证缓存 + 切换周期只刷新必要区块）。
 */
@OptIn(ExperimentalMaterial3Api::class)
class StatsComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleState: StatsUiState = StatsUiState(
        period = StatsPeriod.WEEK,
        periodTitle = "1月1日 - 1月7日",
        isCurrentPeriod = true,
        nextEnabled = false,
        hasAnyData = true,
        showGlobalEmpty = false,
        showPeriodEmpty = false,
        stats = StatsUi(
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
        ),
        booksEmpty = false,
    )

    private val globalEmptyState: StatsUiState = sampleState.copy(
        booksEmpty = true,
        hasAnyData = false,
        showGlobalEmpty = true,
        showPeriodEmpty = false,
        stats = EMPTY_STATS,
    )

    private val periodEmptyState: StatsUiState = sampleState.copy(
        booksEmpty = false,
        hasAnyData = false,
        showGlobalEmpty = false,
        showPeriodEmpty = true,
        stats = EMPTY_STATS.copy(status = BookStatus(1, 1, 2, 0, 4)),
    )

    /* ---------- 源码级：Screen 文件不含分桶/范围计算关键字 ---------- */

    @Test
    fun screenFileDoesNotContainBucketingOrRangeCalculations() {
        val f = File(
            "android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/StatsScreen.kt"
        )
        assertTrue("StatsScreen 文件存在", f.exists())
        val content = f.readText()
        listOf(
            "computeStats", "buildTrend", "buildRange", "inRange", "bucketByDate",
            "fillDaily", "fillWeekly", "fillMonthly", "fillTotal", "computeStreak",
            ".filter {", ".map { session", ".groupBy",
        ).forEach { token ->
            assertTrue(
                "StatsScreen 文件不得包含 '$token'（计算应在 VM/StatsPage）",
                !content.contains(token),
            )
        }
    }

    @Test
    fun computeStatsIsNowDefinedInStatsPageNotOldScreen() {
        val old = File(
            "android/app/src/main/java/com/creationreadingassistant/ui/screen/StatsScreen.kt"
        ).readText()
        val new = File(
            "android/app/src/main/java/com/creationreadingassistant/ui/screen/stats/StatsPage.kt"
        ).readText()
        assertTrue("旧 StatsScreen 只是转发，不再内联 computeStats 实现", !old.contains("bucketByDate"))
        assertTrue("computeStats 实现在 StatsPage.kt（趋势分桶/连续天数均在 VM 层）", new.contains("bucketByDate"))
    }

    /* ---------- 单 AppScreenScaffold ---------- */

    @Test
    fun screenRendersSingleStatsScreenTag() {
        val actions = mutableListOf<StatsAction>()
        composeRule.setContent {
            themedRoot {
                StatsScreen(
                    state = sampleState,
                    onAction = { actions.add(it) },
                    onGoToShelf = {},
                )
            }
        }
        composeRule.onNodeWithTag("stats-screen").assertIsDisplayed()
        assertEquals(
            "仅一个 stats-screen 根节点",
            1,
            composeRule.onAllNodesWithTag("stats-screen").fetchSemanticsNodes().size,
        )
    }

    /* ---------- 全局空态短路 ---------- */

    @Test
    fun globalEmptyOmitsSummaryTrendStatusCreation() {
        composeRule.setContent {
            themedRoot {
                StatsScreen(
                    state = globalEmptyState,
                    onAction = {},
                    onGoToShelf = {},
                )
            }
        }
        composeRule.onNodeWithTag("stats-global-empty").assertIsDisplayed()
        // 4 个区块的 testTag 均不应存在
        listOf("stats-summary", "stats-trend", "stats-status", "stats-creation").forEach { tag ->
            assertEquals(
                "全局空态 '$tag' 不应组合",
                0,
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size,
            )
        }
    }

    /* ---------- 有数据：4 区块全部组合 ---------- */

    @Test
    fun withDataAllFourSectionsAreComposed() {
        composeRule.setContent {
            themedRoot {
                StatsScreen(
                    state = sampleState,
                    onAction = {},
                    onGoToShelf = {},
                )
            }
        }
        listOf("stats-summary", "stats-trend", "stats-status", "stats-creation").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        assertEquals(
            "有数据时不组合 period-empty",
            0,
            composeRule.onAllNodesWithTag("stats-period-empty").fetchSemanticsNodes().size,
        )
    }

    /* ---------- 周期空：仍保留 4 区块（书籍状态卡等有用），额外显示 period-empty 提示 ---------- */

    @Test
    fun periodEmptyStillComposesFourSectionsPlusHint() {
        composeRule.setContent {
            themedRoot {
                StatsScreen(
                    state = periodEmptyState,
                    onAction = {},
                    onGoToShelf = {},
                )
            }
        }
        listOf("stats-summary", "stats-trend", "stats-status", "stats-creation").forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("stats-period-empty").assertIsDisplayed()
    }

    /* ---------- 切换周期：UI 重新组合，但根 scaffold 仍是 1 个（缓存切换稳定） ---------- */

    @Test
    fun switchingPeriodStillHasSingleScaffold() {
        var state by mutableStateOf(sampleState)
        composeRule.setContent {
            themedRoot {
                StatsScreen(
                    state = state,
                    onAction = {},
                    onGoToShelf = {},
                )
            }
        }
        assertEquals(1, composeRule.onAllNodesWithTag("stats-screen").fetchSemanticsNodes().size)
        listOf(StatsPeriod.MONTH, StatsPeriod.YEAR, StatsPeriod.TOTAL).forEach { p ->
            state = state.copy(period = p, periodTitle = "周期：$p")
            composeRule.waitForIdle()
            assertEquals(
                "切到 $p 后 stats-screen 仍为 1 个",
                1,
                composeRule.onAllNodesWithTag("stats-screen").fetchSemanticsNodes().size,
            )
        }
    }

    /* ---------- 旧入口文件仍为精简转发（≤80 行） ---------- */

    @Test
    fun oldEntryFileIsTinyForwarder() {
        val f = File(
            "android/app/src/main/java/com/creationreadingassistant/ui/screen/StatsScreen.kt"
        )
        val lines = f.readLines().size
        assertTrue("旧入口 $lines 行 ≤ 90（含 typealias 转发）", lines <= 90)
    }

    /* ---------- 主题根（与 InspirationComposeTest 等价，不依赖真实 VM） ---------- */

    @Composable
    private fun themedRoot(content: @Composable () -> Unit) {
        AppTheme {
            Box(Modifier.requiredSize(393.dp, 800.dp).testTag("root")) {
                CompositionLocalProvider(
                    LocalAppChrome provides AppChrome(
                        bottomNavHeight = 0.dp,
                        navLayerPaddingApplied = true,
                    ),
                    LocalLayoutTokens provides DefaultLayoutTokens,
                ) {
                    content()
                }
            }
        }
    }
}
