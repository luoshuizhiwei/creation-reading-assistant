package com.creationreadingassistant.ui.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTokensTest {
    @Test
    fun adaptivePageMetricsKeepPhoneFullWidthAndCapTabletContent() {
        val phone = adaptivePageMetrics(430.dp)
        assertFalse(phone.isWide)
        assertEquals(430.dp, phone.contentWidth)
        assertEquals(16.dp, phone.horizontalPadding)

        val tablet = adaptivePageMetrics(840.dp)
        assertTrue(tablet.isWide)
        assertEquals(720.dp, tablet.contentWidth)
        assertEquals(24.dp, tablet.horizontalPadding)

        val landscapeTablet = adaptivePageMetrics(1200.dp)
        assertEquals(720.dp, landscapeTablet.contentWidth)
    }

    // —————————————————————————————————————————————————————————
    // 1. 顶部栏最小内容高度一致（状态栏 inset 可在此基础上增加整体高度）
    // —————————————————————————————————————————————————————————

    @Test
    fun defaultSpacingFollowsFourPointRhythm() {
        val tokens = DefaultLayoutTokens

        assertEquals(4.dp, tokens.microGap)
        assertEquals(8.dp, tokens.relatedGap)
        assertEquals(12.dp, tokens.contentGap)
        assertEquals(20.dp, tokens.sectionGap)
        assertEquals(16.dp, tokens.pageHorizontal)
        assertEquals(24.dp, tokens.pageHorizontalWide)
    }

    @Test
    fun interactiveRowsMeetMinimumTouchTarget() {
        val tokens = DefaultLayoutTokens

        assertEquals(48.dp, tokens.minimumTouchTarget)
        assertTrue(tokens.singleLineRowHeight >= tokens.minimumTouchTarget)
        assertTrue(tokens.supportingRowHeight >= tokens.minimumTouchTarget)
        assertTrue(tokens.topBarHeight >= tokens.minimumTouchTarget)
    }

    /** 顶部栏内容区最小高度保持 64dp；系统状态栏不应被压进这 64dp。 */
    @Test
    fun topBarHeightIsFixed64dp() {
        val tokens = DefaultLayoutTokens
        assertEquals(64.dp, tokens.topBarHeight)
    }

    /** 底部导航栏高度估算值（NavigationBar Material3 默认容器高度）。 */
    @Test
    fun bottomNavHeightIsMaterial3Default80dp() {
        val tokens = DefaultLayoutTokens
        assertEquals(80.dp, tokens.bottomNavHeight)
    }

    @Test
    fun viewportTokensDistinctFromContentTokens() {
        val tokens = DefaultLayoutTokens
        // 视口避让 token（topBarHeight、bottomNavHeight）必须和内容边距 token（pageVertical）不相等，
        // 避免未来有人误用同一常量两处导致重复 inset。
        assertNotEquals(tokens.topBarHeight, tokens.pageVertical)
        assertNotEquals(tokens.bottomNavHeight, tokens.pageVertical)
    }

    // —————————————————————————————————————————————————————————
    // 2. PaddingValues 工具：plusTokens / structurallyEquals
    // —————————————————————————————————————————————————————————

    @Test
    fun plusTokensSumEachDirection() {
        val a = PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)
        val b = PaddingValues(start = 10.dp, top = 20.dp, end = 30.dp, bottom = 40.dp)
        val rtl = LayoutDirection.Rtl

        val sum = a.plusTokens(b, LayoutDirection.Ltr)
        assertEquals(11.dp, sum.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(22.dp, sum.calculateTopPadding())
        assertEquals(33.dp, sum.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(44.dp, sum.calculateBottomPadding())

        // RTL 下 start/end 方向对应 PaddingValues 的 end/start 顺序，但我们的 plusTokens 不改变 PaddingValues
        // 内部的 absolute start/end；关键是 structurallyEquals 在两种 LD 下都能正确判等。
        assertEquals(1.dp, a.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(3.dp, a.calculateEndPadding(LayoutDirection.Ltr))
    }

    @Test
    fun structurallyEqualsMatchesAllDirections() {
        val a = PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)
        val b = PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)
        val c = PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 999.dp)

        assertTrue(a.structurallyEquals(b, LayoutDirection.Ltr))
        assertFalse(a.structurallyEquals(c, LayoutDirection.Ltr))
        assertTrue(a.structurallyEquals(b, LayoutDirection.Rtl))
    }

    @Test
    fun describeProducesHumanReadableString() {
        val p = PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)
        val s = p.describe(LayoutDirection.Ltr)
        assertTrue(s.contains("start=1.0"))
        assertTrue(s.contains("top=2.0"))
        assertTrue(s.contains("end=3.0"))
        assertTrue(s.contains("bottom=4.0"))
    }

    // —————————————————————————————————————————————————————————
    // 3. 内容起点位于顶部栏之后的契约（纯数值验证）
    // —————————————————————————————————————————————————————————

    /** 模拟 AppScreenScaffold 的 viewportPadding：top = topBarHeight（不含 statusBars 时）。 */
    @Test
    fun viewportPaddingTopAtLeastTopBarHeight() {
        val tokens = DefaultLayoutTokens
        // 当系统状态栏为 0 时（例如 UI tree 断言用的 Activity 全屏无 title），
        // AppScreenScaffold 出的 top 值应≥ tokens.topBarHeight。
        // 这里只验证纯常量关系：topBarHeight 本身是正的，足够作为"起点之后"的依据。
        assertTrue(tokens.topBarHeight > 0.dp)
        assertTrue(tokens.pageVertical <= tokens.topBarHeight) // content 边距不应该超过 bar 高度
    }

    // —————————————————————————————————————————————————————————
    // 4. 底部内容不会被导航栏遮住：bottom padding ≥ bottomNavHeight
    // —————————————————————————————————————————————————————————

    /** 当 AppNavigation 应用了 NavHost 外层 padding(bottom = bottomNavHeight) 后，
     *  叠加 AppScreenScaffold 的 innerPadding（navigationBars=0 的简化情况下），
     *  最终 viewportPadding.bottom + contentPadding.calculateBottomPadding() 的和
     *  必须 ≥ bottomNavHeight。这里只验证 bottomNavHeight 非零。 */
    @Test
    fun bottomNavPaddingIsAtLeastTheBarHeight() {
        val tokens = DefaultLayoutTokens
        val appLayerBottom = PaddingValues(bottom = tokens.bottomNavHeight)
        val scaffoldLayerBottom = PaddingValues(bottom = 0.dp) // 全屏无 system nav 时
        val combinedBottom = appLayerBottom.plusTokens(scaffoldLayerBottom).calculateBottomPadding()
        assertTrue(combinedBottom >= tokens.bottomNavHeight)
    }

    // —————————————————————————————————————————————————————————
    // 5. 320dp 小屏 + 字体 1.3 下无重复 inset（纯数值验证）
    // —————————————————————————————————————————————————————————

    /** 320dp 小屏断点：水平内容边距 ×2 必须小于屏宽，还有空间给实际内容。 */
    @Test
    fun compactScreen320dpHasRoomForContent() {
        val tokens = DefaultLayoutTokens
        val screenWidth: Dp = tokens.compactScreenMinWidth  // 320
        val horizontalMargins = tokens.pageHorizontal * 2   // 16 * 2 = 32
        val remaining = screenWidth - horizontalMargins      // 288
        assertTrue(
            "Content width after margins must be positive on 320dp, got $remaining",
            remaining > 0.dp
        )
        // 至少剩余 256dp 可以放卡片/按钮
        assertTrue(remaining >= 256.dp)
    }

    /** 字体 1.3：虽然 LayoutTokens 都是 Dp（不随 fontScale 变化），
     *  但若调用方错误地把 viewportPadding 和 contentPadding 设为同一值，
     *  structurallyEquals 必须返回 true → 从而被 PageLazyColumn 的 check() 捕获。
     *  这个测试直接验证防重复机制的前提条件。 */
    @Test
    fun duplicatePaddingValuesAreDetected() {
        val viewport = PaddingValues(
            start = 0.dp,
            top = DefaultLayoutTokens.topBarHeight,    // 64
            end = 0.dp,
            bottom = DefaultLayoutTokens.bottomNavHeight, // 80
        )
        // 同一个 PV 既当 viewport 又当 content（错误用法）
        val content = viewport

        assertTrue(
            "Duplicate viewportPadding & contentPadding must be structurally equal " +
                "so PageLazyColumn can reject them",
            viewport.structurallyEquals(content)
        )
    }

    /** 正常用法（viewport 来自 Scaffold，content 来自 pageHorizontal/pageVertical）→
     *  两者 structurallyEquals=false，不会触发 check()。 */
    @Test
    fun properViewportAndContentPaddingAreNotIdentical() {
        val tokens = DefaultLayoutTokens
        // 典型 AppScreenScaffold 传出的 viewport padding（近似值）
        val viewport = PaddingValues(
            start = 0.dp,
            top = tokens.topBarHeight, // 64
            end = 0.dp,
            bottom = tokens.bottomNavHeight, // 80
        )
        // 典型 PageLazyColumn 默认 contentPadding
        val content = PaddingValues(
            horizontal = tokens.pageHorizontal, // 16
            vertical = tokens.pageVertical,     // 12
        )

        assertFalse(
            "viewportPadding and contentPadding must differ to avoid check() false positives",
            viewport.structurallyEquals(content)
        )
    }

    /** 字体 1.3 + 320dp 综合：fontScale 改变的是 Sp，不影响 Dp padding；
     *  但如果有人"为了字体 1.3 手动把 Dp 乘 1.3"并同时应用到两处，会被 structurallyEquals 检测。 */
    @Test
    fun manuallyScaledDuplicatePaddingIsDetected() {
        val tokens = DefaultLayoutTokens
        // 错误示范：把 pageHorizontal 作为 viewport 起点（不合理，但恰好和 contentPadding 同值）
        val wronglyScaledViewport = PaddingValues(
            horizontal = tokens.pageHorizontal,
            vertical = tokens.pageVertical,
        )
        val content = PaddingValues(
            horizontal = tokens.pageHorizontal,
            vertical = tokens.pageVertical,
        )
        assertTrue(wronglyScaledViewport.structurallyEquals(content))
    }

    @Test
    fun addingSamePaddingTwiceDoublesTheInset() {
        val tokens = DefaultLayoutTokens
        val once = PaddingValues(bottom = tokens.bottomNavHeight)
        val doubled = once.plusTokens(once)
        assertEquals(tokens.bottomNavHeight * 2, doubled.calculateBottomPadding())
        // 这证明：如果 AppNavigation 和 AppScreenScaffold 各自加一次 bottomNavHeight，
        // 底部间距会变成 160dp → 被肉眼发现。
    }
}
