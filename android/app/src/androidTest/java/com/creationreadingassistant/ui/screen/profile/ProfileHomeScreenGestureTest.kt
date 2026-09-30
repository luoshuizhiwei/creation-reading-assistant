package com.creationreadingassistant.ui.screen.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.settings.DurationUnit
import com.creationreadingassistant.ui.util.DURATION_UNIT_TOGGLE_LABEL
import com.creationreadingassistant.ui.util.LocalDurationUnit
import com.creationreadingassistant.ui.util.LocalDurationUnitToggle
import com.creationreadingassistant.ui.util.formatDuration
import com.creationreadingassistant.ui.viewmodel.ProfileHomeSummary
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * G6 手势收口的行为级仪器测试（真手势，非源码字符串断言）。
 *
 * 被测契约（ProfileHomeScreen 阅读档案卡，行为保留版）：
 * 1. 整卡是**唯一**手势层：点击身份区 / 时长区 / 原可点空白区（统计格间的非手势节点）
 *    都打开「我的阅读」——保留改动前的整卡点击区域；
 * 2. 长按（卡片任意处，含时长文本上）恰好切换一次全局时长单位，且**不**触发导航；
 * 3. 「灵感数量」格保留自己的点击入口（直达灵感中心），且不挂长按切换；
 * 4. 卡片语义节点持有唯一的 OnLongClick（label = DURATION_UNIT_TOGGLE_LABEL），
 *    时长文本叶子自身无长按手势（子节点不吞点击、读屏不重复播报）；
 * 5. 定宽用的**隐形占位文本**（`Color.Transparent`）必须退出无障碍语义树：
 *    它与可见 count-up 值同文本同坐标，留在语义树里会让读屏把同一个时长朗读两遍，
 *    也让「按文本定位可见值」永远命中两个节点；
 * 6. 超过 24 小时时 HOURS / DAYS 两种单位在真实 UI 里文案可区分，且长按切换后
 *    界面文案随之变化（不是只改了偏好状态）。
 *
 * **选择器口径**：一律使用 [onNodeWithText] 定位**唯一**节点，并在需要时用
 * [onAllNodesWithText] + `assertCountEquals(1)` **断言唯一性**。不使用
 * `onAllNodes(...)[0]` 之类的取首项写法绕过重复节点——那会把「语义树里有重复
 * 朗读源」这一真实缺陷掩盖成测试通过。
 *
 * 运行环境：qahost 变体（CraIsolatedTestRunner + 裸 CraIsolatedTestApplication +
 * ui-test-manifest 的 ComponentActivity）。**编译通过不等于设备通过**——真机手势/
 * 读屏验收由设备窗口执行。
 */
class ProfileHomeScreenGestureTest {

    @get:Rule
    val compose = createComposeRule()

    /** 3 天的阅读总时长：HOURS 下应显示「72 小时 0 分钟」，DAYS 下「3 天 0 小时」。 */
    private val totalMinutes = 3L * 24 * 60
    private val totalMs = totalMinutes * 60_000L

    /** HOURS 与 DAYS 两种单位对同一 ≥24h 值的期望文案（用于判别性断言）。 */
    private val hoursText = formatDuration(totalMs, DurationUnit.HOURS)
    private val daysText = formatDuration(totalMs, DurationUnit.DAYS)

    private val toggleCount = AtomicInteger(0)
    private val actions = java.util.Collections.synchronizedList(mutableListOf<ProfileAction>())

    /** 单位切换测试用：CompositionLocal 的真实来源，长按回调会翻转它。 */
    private lateinit var switchableUnit: MutableState<DurationUnit>

    @Before
    fun reset() {
        actions.clear()
        toggleCount.set(0)
    }

    /** 手势断言用的辅助：记录动作并回读的轻量包装（避免并发窗口期的列表竞争）。 */
    private fun fired(action: ProfileAction): Boolean = actions.contains(action)

    private fun setContent(unit: DurationUnit = DurationUnit.HOURS) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDurationUnit provides unit,
                    LocalDurationUnitToggle provides { toggleCount.incrementAndGet() },
                ) {
                    ProfileHomeScreen(
                        state = ProfileUiState(
                            homeSummary = ProfileHomeSummary(
                                totalDurationMs = totalMs,
                                completedBookCount = 5,
                                inspirationCount = 7,
                            ),
                        ),
                        onAction = { actions.add(it) },
                        scaffoldPadding = PaddingValues(0.dp),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * 与 [setContent] 相同的真实页面，但把 `LocalDurationUnit` 接到可写状态上，
     * 长按回调会真的切换单位——用于验证「切换在 UI 文案上可见」。
     */
    private fun setContentWithSwitchableUnit(initial: DurationUnit = DurationUnit.HOURS) {
        switchableUnit = mutableStateOf(initial)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDurationUnit provides switchableUnit.value,
                    LocalDurationUnitToggle provides {
                        toggleCount.incrementAndGet()
                        switchableUnit.value =
                            if (switchableUnit.value == DurationUnit.HOURS) DurationUnit.DAYS
                            else DurationUnit.HOURS
                    },
                ) {
                    ProfileHomeScreen(
                        state = ProfileUiState(
                            homeSummary = ProfileHomeSummary(
                                totalDurationMs = totalMs,
                                completedBookCount = 5,
                                inspirationCount = 7,
                            ),
                        ),
                        onAction = { actions.add(it) },
                        scaffoldPadding = PaddingValues(0.dp),
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun waitUntilDurationTextVisible(unit: DurationUnit) {
        val text = formatDuration(totalMs, unit)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * 断言「可见时长值」在未合并语义树里**恰好一个**节点。
     *
     * 这不是取首项绕过重复：隐形占位文本已被 `clearAndSetSemantics` 移出语义树，
     * 重复节点根本不应存在；数量不是 1 就说明占位又回到了语义树（读屏会重复朗读）。
     */
    private fun assertDurationValueIsUnique(unit: DurationUnit) {
        compose.onAllNodesWithText(formatDuration(totalMs, unit), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    // ---- 1. 整卡点击区域保留 ----

    @Test
    fun click_identity_row_opens_reading_archive() {
        setContent()
        compose.onNodeWithText("创作阅读助手", useUnmergedTree = true)
            .performTouchInput { click() }
        compose.waitForIdle()
        assertTrue(fired(ProfileAction.OpenSubPage(ProfileSubPage.READING)))
    }

    @Test
    fun click_duration_value_still_opens_reading_archive() {
        setContent()
        waitUntilDurationTextVisible(DurationUnit.HOURS)
        assertDurationValueIsUnique(DurationUnit.HOURS)
        compose.onNodeWithText(hoursText, useUnmergedTree = true)
            .performTouchInput { click() }
        compose.waitForIdle()
        assertTrue("时长区点击应保留原导航动作", fired(ProfileAction.OpenSubPage(ProfileSubPage.READING)))
    }

    @Test
    fun click_non_gesture_stat_label_still_opens_reading_archive() {
        // 原整卡可点时的「空白区」等价物：卡内无手势子节点（另一统计格的标签文本），
        // 点击它应由卡片手势层接收并打开「我的阅读」。
        setContent()
        compose.onNodeWithText("累计读完", useUnmergedTree = true)
            .performTouchInput { click() }
        compose.waitForIdle()
        assertTrue(fired(ProfileAction.OpenSubPage(ProfileSubPage.READING)))
    }

    // ---- 2. 长按切换唯一且不导航 ----

    @Test
    fun long_press_duration_value_toggles_exactly_once_and_does_not_navigate() {
        setContent()
        waitUntilDurationTextVisible(DurationUnit.HOURS)
        assertDurationValueIsUnique(DurationUnit.HOURS)
        compose.onNodeWithText(hoursText, useUnmergedTree = true)
            .performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals("长按应恰好切换一次", 1, toggleCount.get())
        assertFalse("长按不得触发导航", fired(ProfileAction.OpenSubPage(ProfileSubPage.READING)))
        assertFalse("长按不得触发灵感入口", fired(ProfileAction.OpenInspirations))
    }

    // ---- 3. 灵感入口保留 ----

    @Test
    fun inspiration_stat_click_goes_to_inspirations() {
        setContent()
        compose.onNodeWithText("灵感数量")
            .performClick()
        compose.waitForIdle()
        assertTrue(fired(ProfileAction.OpenInspirations))
        assertFalse("灵感格点击不得顺带触发档案卡导航", fired(ProfileAction.OpenSubPage(ProfileSubPage.READING)))
    }

    // ---- 4. 手势归属（同一语义组件只有一个切换操作） ----

    @Test
    fun card_semantics_hold_the_single_toggle_and_leaf_text_holds_none() {
        setContent()
        waitUntilDurationTextVisible(DurationUnit.HOURS)

        // 合并树：卡片节点 = 唯一手势层，同时持有 OnClick 与带 label 的 OnLongClick。
        val cardConfig = compose.onNodeWithText(hoursText)
            .fetchSemanticsNode().config
        assertTrue(cardConfig.contains(SemanticsActions.OnClick))
        assertTrue(cardConfig.contains(SemanticsActions.OnLongClick))
        assertEquals(
            "卡片长按动作的读屏 label 必须是切换时长显示单位",
            DURATION_UNIT_TOGGLE_LABEL,
            cardConfig[SemanticsActions.OnLongClick].label,
        )

        // 未合并树：时长文本叶子自身没有任何长按手势（不吞父级点击）。
        val leafConfig = compose.onNodeWithText(hoursText, useUnmergedTree = true)
            .fetchSemanticsNode().config
        assertFalse("时长文本叶子不得自带长按手势", leafConfig.contains(SemanticsActions.OnLongClick))
        assertFalse(leafConfig.contains(SemanticsActions.OnClick))

        // 灵感格：独立合并节点，只有自己的点击，无长按切换。
        val inspConfig = compose.onNodeWithText("灵感数量")
            .fetchSemanticsNode().config
        assertTrue(inspConfig.contains(SemanticsActions.OnClick))
        assertFalse(inspConfig.contains(SemanticsActions.OnLongClick))
    }

    // ---- 5. 隐形占位文本不得进入语义树（不重复朗读 / 可见值可唯一定位） ----

    @Test
    fun invisible_width_placeholder_leaves_semantics_tree_so_duration_is_announced_once() {
        setContent()
        waitUntilDurationTextVisible(DurationUnit.HOURS)

        // 未合并树：与可见值同文本的占位节点必须不存在，只剩可见 count-up 值一个节点。
        // 占位若回到语义树，这里会变成 2 —— 正是本轮修复前 G6 用例失败的原因。
        assertDurationValueIsUnique(DurationUnit.HOURS)

        // 合并树：卡片节点把子树文本并成一份朗读内容，同一个时长只能出现一次；
        // 出现两次意味着读屏会在同一张卡里把时长朗读两遍。
        val cardTexts = compose.onNodeWithText(hoursText)
            .fetchSemanticsNode().config[SemanticsProperties.Text]
            .map { it.text }
        assertEquals(
            "时长在卡片合并语义里只能出现一次（占位不得重复并入）",
            1,
            cardTexts.count { it == hoursText },
        )

        // 可见值仍可唯一定位并承载主交互：卡片长按入口保持单一。
        val cardConfig = compose.onNodeWithText(hoursText).fetchSemanticsNode().config
        assertEquals(
            DURATION_UNIT_TOGGLE_LABEL,
            cardConfig[SemanticsActions.OnLongClick].label,
        )
    }

    // ---- 6. ≥24h 单位显示判别与切换契约 ----

    @Test
    fun over_24h_hours_and_days_render_distinguishable_text_and_long_press_switches_unit() {
        // 受控夹具：3 天（≥24h）总时长，只走测试数据，不读写用户数据库。
        assertNotEquals(
            "超过 24 小时时 HOURS 与 DAYS 文案必须可区分，否则设备上无法判别单位切换",
            hoursText,
            daysText,
        )
        assertEquals("72 小时 0 分钟", hoursText)
        assertEquals("3 天 0 小时", daysText)

        setContentWithSwitchableUnit(DurationUnit.HOURS)
        waitUntilDurationTextVisible(DurationUnit.HOURS)
        assertDurationValueIsUnique(DurationUnit.HOURS)
        compose.onAllNodesWithText(daysText, useUnmergedTree = true).assertCountEquals(0)

        // 真手势长按 → 回调把 CompositionLocal 切到 DAYS → 真实 UI 文案随之改变。
        compose.onNodeWithText(hoursText, useUnmergedTree = true)
            .performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals("长按应恰好切换一次", 1, toggleCount.get())
        assertEquals("长按后单位状态应切到 DAYS", DurationUnit.DAYS, switchableUnit.value)

        waitUntilDurationTextVisible(DurationUnit.DAYS)
        assertDurationValueIsUnique(DurationUnit.DAYS)
        compose.onAllNodesWithText(hoursText, useUnmergedTree = true).assertCountEquals(0)
        assertFalse("切换单位不得触发导航", fired(ProfileAction.OpenSubPage(ProfileSubPage.READING)))
    }
}
