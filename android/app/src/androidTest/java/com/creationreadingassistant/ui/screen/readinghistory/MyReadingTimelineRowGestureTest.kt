package com.creationreadingassistant.ui.screen.readinghistory

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.data.settings.DurationUnit
import com.creationreadingassistant.ui.layout.DefaultLayoutTokens
import com.creationreadingassistant.ui.layout.LocalLayoutTokens
import com.creationreadingassistant.ui.navigation.AppChrome
import com.creationreadingassistant.ui.navigation.LocalAppChrome
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.util.LocalDurationUnit
import com.creationreadingassistant.ui.util.LocalDurationUnitToggle
import com.creationreadingassistant.ui.viewmodel.MyReadingFilter
import com.creationreadingassistant.ui.viewmodel.MyReadingItem
import com.creationreadingassistant.ui.viewmodel.MyReadingMonth
import com.creationreadingassistant.ui.viewmodel.MyReadingUiState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * 阅读历史行（`TimelineBookRow`）的手势所有权验收（S15-F3-B3）。
 *
 * 被裁决的唯一长按语义：**整行长按 = 打开管理操作**。行内时长文本降级为纯展示 ——
 * 它既不能有点击（会吞掉"打开该书"），也不能有长按（会和"管理操作"在同一行抢手势）。
 *
 * 这里渲染的是**生产入口** [MyReadingScreen]（public，参数是纯数据 + `onAction`），
 * 因此顺带钉住真实的动作映射（[MyReadingAction.OpenBook] / [MyReadingAction.ManageBook]），
 * 不需要把 private 的 `TimelineBookRow` 改成 internal，也不在测试里造任何替身组件。
 *
 * 节点定位一律走稳定 testTag（`reading-item-*`），不用中文时长文本查找；
 * 只有在断言显示值时才比对文本内容。
 */
class MyReadingTimelineRowGestureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<MyReadingAction>()

    /** 行内若还残留任何"长按切单位"入口，这个计数就会 > 0。 */
    private var toggleCount = 0

    private val book = BookEntity(
        id = "gesture-1",
        title = "测试书甲",
        author = "作者甲",
        format = "TXT",
        updated_at = "2026-09-22T00:00:00",
    )

    private val item = MyReadingItem(
        book = book,
        progress = null,
        state = ReadingCompletionState.READING,
        // 30 小时：小时制显示「30 小时 0 分钟」、日制显示「1 天 6 小时」，
        // 两制输出必然不同，才能证明"跟随全局单位"不是巧合。
        readingTimeMs = 30L * 60 * 60 * 1000,
        startedAtMs = 0L,
        lastActivityAtMs = 0L,
    )

    private val state = MyReadingUiState(
        counts = mapOf(MyReadingFilter.ALL to 1, MyReadingFilter.READING to 1),
        months = persistentListOf(MyReadingMonth(year = 2026, month = 9, items = listOf(item))),
        isReady = true,
    )

    private val rowTag = "reading-item-${book.id}"
    private val durationTag = "reading-item-duration-${book.id}"
    private val manageTag = "reading-item-manage-${book.id}"

    // ————————————————————————————————————————————————
    // 1 / 2：整行的两个手势各归其位
    // ————————————————————————————————————————————————

    @Test
    fun rowClick_opensTheBook_andDoesNotOpenManage() {
        show(DurationUnit.HOURS)

        composeRule.onNodeWithTag(rowTag).performClick()

        assertEquals(listOf(MyReadingAction.OpenBook(book)), actions)
        assertEquals(0, toggleCount)
    }

    @Test
    fun rowLongClick_opensManage_andDoesNotOpenTheBook() {
        show(DurationUnit.HOURS)

        composeRule.onNodeWithTag(rowTag).performTouchInput { longClick() }

        assertEquals(listOf(MyReadingAction.ManageBook(book)), actions)
        assertEquals(0, toggleCount)
    }

    // ————————————————————————————————————————————————
    // 3：显式管理按钮保持可用
    // ————————————————————————————————————————————————

    @Test
    fun manageButton_stillOpensManage() {
        show(DurationUnit.HOURS)

        composeRule.onNodeWithTag(manageTag).performClick()

        assertEquals(1, actions.count { it is MyReadingAction.ManageBook })
        assertEquals(0, actions.count { it is MyReadingAction.OpenBook })
    }

    // ————————————————————————————————————————————————
    // 4 / 5：时长 pill 不再暴露任何点击动作
    // ————————————————————————————————————————————————

    @Test
    fun durationPill_exposesNoClickAndNoLongClick() {
        show(DurationUnit.HOURS)

        val config = pillConfig()
        assertFalse("时长 pill 仍暴露 OnClick：会吞掉整行的「打开该书」", config.contains(SemanticsActions.OnClick))
        assertFalse(
            "时长 pill 仍暴露 OnLongClick：会与整行的「管理操作」抢手势",
            config.contains(SemanticsActions.OnLongClick),
        )
    }

    @Test
    fun longClickAtDurationPillCoordinates_belongsToManage_andDoesNotToggleUnit() {
        show(DurationUnit.HOURS)

        // 用 pill 自己的坐标长按：命中的应当是整行的 combinedClickable，语义必须是管理。
        composeRule.onNodeWithTag(durationTag, useUnmergedTree = true)
            .performTouchInput { longClick() }

        assertEquals(
            "长按时长区域应当落到整行的「打开管理操作」上，实际动作=$actions",
            listOf(MyReadingAction.ManageBook(book)),
            actions,
        )
        assertEquals("行内时长仍存在隐藏的切单位入口", 0, toggleCount)
    }

    // ————————————————————————————————————————————————
    // 6 / 唯一性：整行只有一个长按动作，且带读屏可预期的标签
    // ————————————————————————————————————————————————

    @Test
    fun row_exposesExactlyOneLongClick_labeledForManage() {
        show(DurationUnit.HOURS)

        val row = composeRule.onNodeWithTag(rowTag).fetchSemanticsNode("阅读行节点")
        val longClick = longClickActionOf(row.config)
        val click = clickActionOf(row.config)
        assertEquals(
            "整行必须暴露长按动作，否则读屏用户拿不到「管理操作」",
            true,
            longClick != null,
        )
        assertEquals("打开管理操作", longClick?.label)
        assertEquals("打开书籍", click?.label)

        // 整行子树里带长按动作的节点只能有整行自己：
        // 曾经时长 pill 自己也带一个长按，读屏用户在同一行会听到两种长按动作。
        val holders = (listOf(row) + descendantsOf(row)).filter { it.config.contains(SemanticsActions.OnLongClick) }
        assertEquals(
            "阅读行内只允许一个长按动作（归整行的管理操作）；找到 ${holders.size} 个：" +
                holders.joinToString { it.config.toString() },
            1,
            holders.size,
        )
    }

    // ————————————————————————————————————————————————
    // 7：时长跟随全局单位，但 pill 依旧无任何手势
    // ————————————————————————————————————————————————

    @Test
    fun durationText_followsHoursUnit_withoutAnyGesture() {
        show(DurationUnit.HOURS)

        composeRule.onNodeWithTag(durationTag, useUnmergedTree = true)
            .assertExists()
            .assertTextEquals("30 小时 0 分钟")
        assertPillHasNoGestures()
    }

    @Test
    fun durationText_followsDaysUnit_withoutAnyGesture() {
        show(DurationUnit.DAYS)

        composeRule.onNodeWithTag(durationTag, useUnmergedTree = true)
            .assertExists()
            .assertTextEquals("1 天 6 小时")
        assertPillHasNoGestures()
    }

    // ———————————————————————————————————————————————— helpers

    private fun show(unit: DurationUnit) {
        composeRule.setContent {
            AppTheme {
                CompositionLocalProvider(
                    LocalLayoutTokens provides DefaultLayoutTokens,
                    LocalAppChrome provides AppChrome(bottomNavHeight = 0.dp, navLayerPaddingApplied = true),
                    LocalDurationUnit provides unit,
                    LocalDurationUnitToggle provides { toggleCount++ },
                    content = {
                        MyReadingScreen(state = state, onAction = { action -> actions.add(action) })
                    },
                )
            }
        }
        // 行上方还有总览/筛选/搜索三块，小屏下可能未组成，先滚到目标行再断言。
        composeRule.onNodeWithTag("my-reading-list").performScrollToNode(hasTestTag(rowTag))
    }

    private fun pillConfig(): SemanticsConfiguration =
        composeRule.onNodeWithTag(durationTag, useUnmergedTree = true)
            .fetchSemanticsNode("时长 pill 节点").config

    private fun assertPillHasNoGestures() {
        val config = pillConfig()
        assertNull("时长 pill 不应可点击", clickActionOf(config))
        assertNull("时长 pill 不应可长按", longClickActionOf(config))
    }

    /**
     * [SemanticsConfiguration.get] 的返回类型是非空 `T`（键不存在时按 `as T` 取值会抛），
     * 所以"有没有这个动作"必须先 `contains` 再取，不能写成 `config[key] != null`
     * （编译器会直接报 "Condition is always 'true'"）。
     */
    private fun clickActionOf(
        config: SemanticsConfiguration,
    ): AccessibilityAction<() -> Boolean>? =
        if (config.contains(SemanticsActions.OnClick)) config[SemanticsActions.OnClick] else null

    private fun longClickActionOf(
        config: SemanticsConfiguration,
    ): AccessibilityAction<() -> Boolean>? =
        if (config.contains(SemanticsActions.OnLongClick)) config[SemanticsActions.OnLongClick] else null

    @Suppress("DEPRECATION")
    private fun descendantsOf(node: SemanticsNode): List<SemanticsNode> =
        node.children.flatMap { listOf(it) + descendantsOf(it) }
}
