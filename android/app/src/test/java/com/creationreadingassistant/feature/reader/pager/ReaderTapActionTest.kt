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
}
