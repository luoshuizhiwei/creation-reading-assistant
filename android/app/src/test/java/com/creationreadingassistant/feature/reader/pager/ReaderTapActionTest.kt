package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapActionTest {
    private fun action(
        x: Float,
        y: Float = 500f,
        mode: String = "three-zone",
        canPrevious: Boolean = true,
        canNext: Boolean = true,
    ) = resolveReaderTapAction(x, y, 900f, 1000f, mode, canPrevious, canNext)

    @Test fun leftAndRightZonesTurnExactlyOneDirection() {
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(100f))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(800f))
    }

    @Test fun middleZoneOnlyTogglesControls() {
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(450f))
    }

    @Test fun fiveZoneAddsTopAndBottomTurns() {
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(450f, 50f, "five-zone"))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(450f, 950f, "five-zone"))
    }

    @Test fun centerZoneTogglesControlsInEveryTapZoneMode() {
        listOf("three-zone", "five-zone", "unknown-mode").forEach { mode ->
            assertEquals(
                "mode=$mode center should toggle controls",
                ReaderTapAction.TOGGLE_CONTROLS,
                action(450f, 500f, mode),
            )
        }
    }

    @Test fun fiveZoneCenterVerticalBandStillTogglesControls() {
        // five-zone 只把顶/底 12% 划给翻页；中央竖带（含 25%/75% 高度）仍是唤出。
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(450f, 250f, "five-zone"))
        assertEquals(ReaderTapAction.TOGGLE_CONTROLS, action(450f, 750f, "five-zone"))
    }

    @Test fun unavailablePageDoesNothingInsteadOfHidingControls() {
        assertEquals(ReaderTapAction.NONE, action(100f, canPrevious = false))
        assertEquals(ReaderTapAction.NONE, action(800f, canNext = false))
    }

    @Test fun invalidViewportDoesNothing() {
        assertEquals(
            ReaderTapAction.NONE,
            resolveReaderTapAction(0f, 0f, 0f, 0f, "three-zone", true, true),
        )
    }

    // ── S07-F2：边界页不得伪装成成功翻页 ────────────────────────────
    //
    // PageTurn 在 VM 侧承担「页面身份已变化 → 完整清除选区」的语义，所以
    // 「有没有真的换页」必须由这个纯函数把住：到达第一页/最后一页时返回 NONE，
    // 调用方就不会派发 PageTurn，也就不会在页面没变的情况下清掉选区。
    @Test fun boundaryPagesReturnNoneSoNoTurnIsReported() {
        assertEquals("第一页点「上一页」不是一次成功翻页", ReaderTapAction.NONE, action(100f, canPrevious = false))
        assertEquals("最后一页点「下一页」不是一次成功翻页", ReaderTapAction.NONE, action(800f, canNext = false))
        // 同一个位置在可翻页时必须给出真实动作 —— 证明上面两条不是恒真断言。
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(100f, canPrevious = true))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(800f, canNext = true))
    }

    @Test fun boundaryPagesStillToggleControlsInTheCentre() {
        // 边界只影响翻页分区；中央分区在边界页仍必须能唤出/收起控制栏。
        assertEquals(
            ReaderTapAction.TOGGLE_CONTROLS,
            action(450f, canPrevious = false, canNext = false),
        )
    }

    // ── S09-B：边缘 deadzone 语义保持 ───────────────────────────────
    //
    // deadzone 是为了挡住手掌贴边微触碰。它必须只吞掉「边缘」的翻页请求，
    // 不能把中央唤出控制栏也一起吞掉，也不能把被吞掉的点击改判成翻页。
    @Test fun edgeDeadzoneSuppressesSideTurns() {
        assertEquals(
            "左边缘 deadzone 内的点击不得翻页",
            ReaderTapAction.NONE,
            resolveReaderTapAction(5f, 500f, 900f, 1000f, "three-zone", true, true, edgeDeadzonePx = 24f),
        )
        assertEquals(
            "右边缘 deadzone 内的点击不得翻页",
            ReaderTapAction.NONE,
            resolveReaderTapAction(895f, 500f, 900f, 1000f, "three-zone", true, true, edgeDeadzonePx = 24f),
        )
        // 同一坐标在没有 deadzone 时确实会翻页 —— 证明上面两条不是恒真断言。
        assertEquals(ReaderTapAction.PREVIOUS_PAGE, action(5f))
        assertEquals(ReaderTapAction.NEXT_PAGE, action(895f))
    }

    @Test fun edgeDeadzoneStillLetsTheCentreToggleControls() {
        assertEquals(
            ReaderTapAction.TOGGLE_CONTROLS,
            resolveReaderTapAction(450f, 500f, 900f, 1000f, "three-zone", true, true, edgeDeadzonePx = 24f),
        )
    }

    @Test fun edgeDeadzoneAlsoSuppressesFiveZoneTopAndBottomBands() {
        // 顶部/底部带同样覆盖到屏幕左右边缘，贴边误触同样不该翻页。
        assertEquals(
            ReaderTapAction.NONE,
            resolveReaderTapAction(5f, 50f, 900f, 1000f, "five-zone", true, true, edgeDeadzonePx = 24f),
        )
        assertEquals(
            ReaderTapAction.PREVIOUS_PAGE,
            resolveReaderTapAction(450f, 50f, 900f, 1000f, "five-zone", true, true, edgeDeadzonePx = 24f),
        )
    }
}
