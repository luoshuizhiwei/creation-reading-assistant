package com.creationreadingassistant.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 书内编辑路由（R3-P1）的纯函数测试。
 *
 * 最重要的一条：**书内编辑绝不能把「本书覆盖值」泄漏进全局设置**——那会让用户在某本书里
 * 调一次字号，静默改掉所有其它书。本测试类就是这条不变量的守卫。
 */
class ReaderSettingsRouterTest {

    /** 全局设置（非默认值，便于识别「谁被写进了全局」）。 */
    private val global = ReaderSettings(
        fontSize = 24f,
        lineHeight = 1.7f,
        background = "white",
        pageTurnSpeed = 400f,
        tapZoneMode = "three-zone",
        keepAwake = false,
    )

    /** 本书覆盖了字号（32）与纸张（warm），其余跟随全局。 */
    private val overrides: PerBookOverrides = mapOf(
        ReaderOverrideKey.FONT_SIZE to "32.0",
        ReaderOverrideKey.BACKGROUND to "warm",
    )

    /** 展示给用户的有效设置 = 全局 + 本书覆盖。 */
    private val displayed = ReaderSettingsOverlay.apply(global, overrides)

    @Test
    fun `displayed really is global plus overrides`() {
        assertEquals(32f, displayed.fontSize)
        assertEquals("warm", displayed.background)
        assertEquals(global.lineHeight, displayed.lineHeight)
    }

    @Test
    fun `scope book writes touched key as book override and leaves global alone`() {
        val edited = displayed.copy(fontSize = 30f)

        val plan = ReaderSettingsRouter.plan(ReaderSettingsScope.BOOK, global, displayed, edited)

        assertEquals(mapOf(ReaderOverrideKey.FONT_SIZE to "30.0"), plan.overridesToSet)
        assertTrue(plan.overridesToClear.isEmpty())
        // 关键：全局字号与全局纸张都保持原值
        assertEquals(24f, plan.global.fontSize)
        assertEquals("white", plan.global.background)
        assertFalse(plan.globalDiffersFrom(global))
    }

    @Test
    fun `scope book never leaks an untouched book override into global`() {
        // 用户只改了不可覆盖的翻页速度；此时本书覆盖的字号/纸张绝不能进全局
        val edited = displayed.copy(pageTurnSpeed = 260f)

        val plan = ReaderSettingsRouter.plan(ReaderSettingsScope.BOOK, global, displayed, edited)

        assertTrue(plan.overridesToSet.isEmpty())
        assertEquals(24f, plan.global.fontSize)
        assertEquals("white", plan.global.background)
        // 不可覆盖项照常进全局
        assertEquals(260f, plan.global.pageTurnSpeed)
        assertTrue(plan.globalDiffersFrom(global))
    }

    @Test
    fun `non overridable keys always go global regardless of scope`() {
        listOf(ReaderSettingsScope.BOOK, ReaderSettingsScope.GLOBAL).forEach { scope ->
            val edited = displayed.copy(tapZoneMode = "five-zone", keepAwake = true)

            val plan = ReaderSettingsRouter.plan(scope, global, displayed, edited)

            assertEquals("$scope 下点击区域必须进全局", "five-zone", plan.global.tapZoneMode)
            assertEquals("$scope 下屏幕常亮必须进全局", true, plan.global.keepAwake)
        }
    }

    @Test
    fun `scope global writes touched key to global and clears the book override`() {
        val edited = displayed.copy(fontSize = 36f)

        val plan = ReaderSettingsRouter.plan(ReaderSettingsScope.GLOBAL, global, displayed, edited)

        assertEquals(36f, plan.global.fontSize)
        assertTrue(plan.overridesToSet.isEmpty())
        // 显式选「全局」→ 本书对该项的覆盖让位，否则新全局值会被遮住
        assertEquals(setOf(ReaderOverrideKey.FONT_SIZE), plan.overridesToClear)
    }

    @Test
    fun `scope global keeps untouched book overrides and does not leak them`() {
        // 只改字号（全局），本书的纸张覆盖必须保留；全局纸张不能被 warm 污染
        val edited = displayed.copy(fontSize = 36f)

        val plan = ReaderSettingsRouter.plan(ReaderSettingsScope.GLOBAL, global, displayed, edited)

        assertFalse(
            "纸张覆盖未被触碰，不得清除",
            ReaderOverrideKey.BACKGROUND in plan.overridesToClear,
        )
        assertEquals("全局纸张必须保持原值，不能被本书覆盖值污染", "white", plan.global.background)
    }

    @Test
    fun `scope global with no book override writes the plain value through`() {
        val edited = global.copy(fontSize = 40f, lineHeight = 2.2f)

        val plan = ReaderSettingsRouter.plan(ReaderSettingsScope.GLOBAL, global, global, edited)

        assertEquals(40f, plan.global.fontSize)
        assertEquals(2.2f, plan.global.lineHeight)
        assertEquals(setOf(ReaderOverrideKey.FONT_SIZE, ReaderOverrideKey.LINE_HEIGHT), plan.overridesToClear)
        assertTrue(plan.overridesToSet.isEmpty())
    }

    @Test
    fun `scope book returns no override when nothing relevant changed`() {
        val edited = displayed.copy(pageTurnSpeed = 500f)

        val plan = ReaderSettingsRouter.plan(ReaderSettingsScope.BOOK, global, displayed, edited)

        assertTrue(plan.overridesToSet.isEmpty())
        assertTrue(plan.overridesToClear.isEmpty())
    }

    @Test
    fun `changed keys are detected by encoded value not by float identity`() {
        assertEquals(
            setOf(ReaderOverrideKey.FONT_SIZE),
            ReaderSettingsRouter.changedOverridableKeys(displayed, displayed.copy(fontSize = 33f)),
        )
        // 同数值（32f 与 32f）不算改动 —— 避免手指抖动/滑块回弹被误记成本书覆盖
        assertTrue(
            ReaderSettingsRouter.changedOverridableKeys(displayed, displayed.copy(fontSize = 32f)).isEmpty(),
        )
        assertTrue(ReaderSettingsRouter.changedOverridableKeys(displayed, displayed).isEmpty())
        // 只改不可覆盖项时，可覆盖项集合必须为空（否则会误建本书覆盖）
        assertTrue(
            ReaderSettingsRouter.changedOverridableKeys(displayed, displayed.copy(pageTurnSpeed = 150f))
                .isEmpty(),
        )
    }

    @Test
    fun `book scope editing an overridden key updates that override instead of global`() {
        val edited = displayed.copy(background = "night")

        val plan = ReaderSettingsRouter.plan(ReaderSettingsScope.BOOK, global, displayed, edited)

        assertEquals(mapOf(ReaderOverrideKey.BACKGROUND to "night"), plan.overridesToSet)
        assertEquals("white", plan.global.background)
    }

    @Test
    fun `plan applies to a book with no overrides at all`() {
        val cleanGlobal = ReaderSettings(fontSize = 26f)
        val edited = cleanGlobal.copy(fontSize = 28f, pageTurnSpeed = 300f)

        val bookPlan = ReaderSettingsRouter.plan(ReaderSettingsScope.BOOK, cleanGlobal, cleanGlobal, edited)

        assertEquals(mapOf(ReaderOverrideKey.FONT_SIZE to "28.0"), bookPlan.overridesToSet)
        assertEquals(26f, bookPlan.global.fontSize)
        assertEquals(300f, bookPlan.global.pageTurnSpeed)
    }
}
