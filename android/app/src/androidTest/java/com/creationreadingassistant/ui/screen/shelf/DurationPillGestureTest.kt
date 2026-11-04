package com.creationreadingassistant.ui.screen.shelf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.settings.DurationUnit
import com.creationreadingassistant.ui.screen.homearchive.COMPLETED_DURATION_PILL_TAG
import com.creationreadingassistant.ui.screen.homearchive.CompletedBookCard
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.util.LocalDurationUnit
import com.creationreadingassistant.ui.util.LocalDurationUnitToggle
import com.creationreadingassistant.ui.viewmodel.CompletedArchiveItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 时长 pill 的手势契约测试（S15-F3-B1/B2，R1 修正渲染入口）。
 *
 * 全部渲染**生产组件**：
 * - B1 走真正的生产调用链 [BookDetailSessionsSection] → private `SessionMicroIsland`
 *   （`BookDetailStatsSection` 只画统计格与趋势图，不创建会话卡片 —— 上一版渲染错了组件，
 *   pill 标签在那棵树里永远不存在；`SessionMicroIsland` 无需为测试放宽可见性）；
 * - B2 直接渲染生产 [CompletedBookCard]。
 *
 * 测试自己不提供任何 clickable / combinedClickable：唯一被测的手势来自生产的
 * [durationUnitPill]。测试只替换两个根注入的 CompositionLocal：`LocalDurationUnit`
 * 给确定单位、`LocalDurationUnitToggle` 用来计数长按。
 *
 * 定位一律用稳定 testTag，不依赖中文格式化文本；渲染后立即断言 pill 标签存在，
 * 避免"找错生产树"退化成一句含糊的找不到节点。
 *
 * 注意：本文件是 instrumentation 用例，本切片只跑到编译门禁，未在设备执行。
 */
class DurationPillGestureTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── B1：SessionMicroIsland 的会话时长 pill ─────────────────────────

    @Test
    fun clickingSessionDurationPillExpandsThenCollapsesDetail() {
        renderSessionSection()

        val detail = composeRule.onNodeWithTag(SESSION_DETAIL_BODY_TAG, useUnmergedTree = true)
        val pill = composeRule.onNodeWithTag(SESSION_DURATION_PILL_TAG, useUnmergedTree = true)

        detail.assertDoesNotExist()
        pill.assertHasClickAction()

        pill.performClick()
        composeRule.waitForIdle()
        detail.assertIsDisplayed()

        pill.performClick()
        composeRule.waitForIdle()
        detail.assertDoesNotExist()
    }

    @Test
    fun longPressingSessionDurationPillTogglesUnitWithoutExpanding() {
        val counters = PillCounters()
        renderSessionSection(counters)

        composeRule.onNodeWithTag(SESSION_DURATION_PILL_TAG, useUnmergedTree = true)
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals("长按 pill 必须切换一次时长单位", 1, counters.toggles)
        // 展开状态由生产的 itemExpanded 持有；未展开时详情容器根本不存在，
        // 所以"详情没出现"就是"展开状态没变"的可观测证据。
        composeRule.onNodeWithTag(SESSION_DETAIL_BODY_TAG, useUnmergedTree = true).assertDoesNotExist()
        // 长按也不得把 pill 自己弄丢（父行重排/收起都会让它消失）
        composeRule.onNodeWithTag(SESSION_DURATION_PILL_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun sessionDurationPillPublishesLongPressLabelAndStaysPlainTextInside() {
        renderSessionSection()

        val pill = composeRule.onNodeWithTag(SESSION_DURATION_PILL_TAG, useUnmergedTree = true)
        assertPillSemantics(pill.fetchSemanticsNode())
        assertNoClickSemanticsOnPillText(pill.fetchSemanticsNode())
    }

    // ── B2：CompletedBookCard 的时长 pill ─────────────────────────────

    @Test
    fun clickingCompletedDurationPillOpensBookExactlyOnce() {
        val counters = PillCounters()
        renderCompletedCard(counters)

        composeRule.onNodeWithTag(COMPLETED_DURATION_PILL_TAG, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals("点 pill 必须打开该书一次", 1, counters.opens)
        assertEquals("pill 点击不得顺带切单位", 0, counters.toggles)
        assertEquals("pill 点击不得触发管理", 0, counters.manages)
    }

    @Test
    fun longPressingCompletedDurationPillTogglesUnitWithoutOpeningOrManaging() {
        val counters = PillCounters()
        renderCompletedCard(counters)

        composeRule.onNodeWithTag(COMPLETED_DURATION_PILL_TAG, useUnmergedTree = true)
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertEquals("长按 pill 只切换单位", 1, counters.toggles)
        assertEquals("长按 pill 不得打开书籍", 0, counters.opens)
        assertEquals("长按 pill 不得触发管理", 0, counters.manages)
    }

    @Test
    fun clickingManageButtonStillOnlyOpensTheManagementSheet() {
        val counters = PillCounters()
        renderCompletedCard(counters)

        // 管理按钮保持原样：用其无障碍标签定位（这是 UI 契约，不是格式化文本）。
        composeRule.onNodeWithContentDescription(MANAGE_BUTTON_DESCRIPTION).performClick()
        composeRule.waitForIdle()

        assertEquals("管理按钮必须仍 work", 1, counters.manages)
        assertEquals("点管理不得打开书籍", 0, counters.opens)
        assertEquals("点管理不得切换单位", 0, counters.toggles)
    }

    @Test
    fun completedDurationPillPublishesLongPressLabelAndStaysPlainTextInside() {
        val counters = PillCounters()
        renderCompletedCard(counters)

        val pill = composeRule.onNodeWithTag(COMPLETED_DURATION_PILL_TAG, useUnmergedTree = true)
        pill.assertHasClickAction()
        assertPillSemantics(pill.fetchSemanticsNode())
        assertNoClickSemanticsOnPillText(pill.fetchSemanticsNode())
    }

    // ── 生产渲染与断言辅助 ────────────────────────────────────────────

    /** 业务回调与单位切换的计数器，全部由生产组件真实触发。 */
    private class PillCounters {
        var toggles: Int = 0
        var opens: Int = 0
        var manages: Int = 0
    }

    @Composable
    private fun WithToggle(counters: PillCounters, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            // 根部（MainActivity）真实提供的是写 DataStore 的实现 + 持久化单位；
            // 这里只替换同一批 CompositionLocal 的值：单位给确定值，切换用来计数。
            // 不改任何被测组件的内部结构，也不在测试里造 pill / Surface / combinedClickable。
            LocalDurationUnit provides DurationUnit.HOURS,
            LocalDurationUnitToggle provides { counters.toggles++ },
            content = content,
        )
    }

    /**
     * 渲染 B1 的真实生产调用链。
     *
     * `BookDetailSessionsSection(sessions)` -> `SessionMicroIsland(session, …)` ->
     * 父行 `Surface(clickable = 展开/收起)` -> 时长 pill（`Modifier.durationUnitPill`）。
     *
     * 上一版这里渲染的是 `BookDetailStatsSection`：它只画统计格与阅读趋势图，
     * 树里根本没有 SessionMicroIsland，pill 标签永远找不到（编译能过、真机必红）。
     * 所以这里补一条"渲染后 pill 必须存在"的前置断言：找错生产树时立刻给出明确原因。
     */
    private fun renderSessionSection(counters: PillCounters = PillCounters()) {
        composeRule.setContent {
            AppTheme {
                WithToggle(counters) {
                    BookDetailSessionsSection(sessions = listOf(sessionFixture()))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SESSION_DURATION_PILL_TAG, useUnmergedTree = true)
            .assertExists("渲染的生产树里必须有会话时长 pill；找不到就是渲染错了组件")
    }

    private fun renderCompletedCard(counters: PillCounters) {
        composeRule.setContent {
            AppTheme {
                WithToggle(counters) {
                    CompletedBookCard(
                        item = completedFixture(),
                        onOpen = { counters.opens++ },
                        onManage = { counters.manages++ },
                        modifier = Modifier.testTag(CARD_ROOT_TAG),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** pill 的无障碍契约：有 OnClick（可点）、OnLongClick 带指定标签（长按切单位）。 */
    private fun assertPillSemantics(pill: SemanticsNode) {
        val config = pill.config
        assertTrue("pill 容器必须自己就是那个可点击节点", config.contains(SemanticsActions.OnClick))
        val longClickLabel = config[SemanticsActions.OnLongClick]?.label
        assertEquals(
            "长按标签必须是切换单位的说明，且只有一个来源常量",
            DURATION_UNIT_PILL_LONG_PRESS_LABEL,
            longClickLabel,
        )
    }

    /**
     * pill 内部的文本必须是纯展示：语义树里带 Text 属性的后代不得再挂 OnClick。
     *
     * 这条盯的是回归本体 —— 一旦有人把 pill 内的 Text 换回 DurationText /
     * 再加一个 durationUnitToggle，文字上的点击就会重新吞掉 pill 的点击。
     */
    private fun assertNoClickSemanticsOnPillText(pill: SemanticsNode) {
        val textNodes = collectDescendants(pill).filter { it.config.contains(SemanticsProperties.Text) }
        assertTrue("pill 内应当有展示文本，否则这条断言是空转", textNodes.isNotEmpty())
        textNodes.forEach { node ->
            assertFalse(
                "pill 内的文本必须是纯展示，不能自带点击/长按语义：${node.config}",
                node.config.contains(SemanticsActions.OnClick) ||
                    node.config.contains(SemanticsActions.OnLongClick),
            )
        }
    }

    private fun collectDescendants(node: SemanticsNode): List<SemanticsNode> =
        node.children.flatMap { listOf(it) + collectDescendants(it) }

    private fun sessionFixture() = ReadingSessionEntity(
        id = "session-1",
        book_id = BOOK_ID,
        started_at = "2026-09-20T10:00:00",
        ended_at = "2026-09-20T11:00:00",
        duration_ms = 3_600_000L,
        progress_percent = 42f,
        created_at = "2026-09-20T11:00:00",
        updated_at = "2026-09-20T11:00:00",
    )

    private fun completedFixture() = CompletedArchiveItem(
        book = BookEntity(
            id = BOOK_ID,
            title = "测试书甲",
            format = "epub",
            updated_at = "2026-09-20T11:00:00",
        ),
        progress = ReadingProgressEntity(
            book_id = BOOK_ID,
            total_reading_time_ms = 7_200_000L,
            completion_state = "completed",
            updated_at = "2026-09-20T11:00:00",
        ),
    )

    private companion object {
        const val BOOK_ID = "book-1"
        const val CARD_ROOT_TAG = "duration-pill-card-root"

        /** 与生产 CompletedBookCard 里管理按钮的无障碍描述保持一致。 */
        const val MANAGE_BUTTON_DESCRIPTION = "管理《测试书甲》"
    }
}
