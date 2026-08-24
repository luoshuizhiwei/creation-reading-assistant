package com.creationreadingassistant.feature.reader.pager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.creationreadingassistant.ui.theme.ReaderPaperSurfaceSpec

class PageTurnerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun coverTurnKeepsUnderlyingPageTextFromShowingThroughCurrentPage() {
        var turnRequest by mutableIntStateOf(0)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("pager")
                    .background(Color.Black),
            ) {
                PageTurner(
                    currentFrame = 0,
                    previousFrame = null,
                    nextFrame = 1,
                    effect = "cover",
                    turnRequest = turnRequest,
                    onTurnRequestConsumed = { turnRequest = 0 },
                    onPrevious = {},
                    onNext = {},
                    pageSurface = ReaderPaperSurfaceSpec.solid(Color.White),
                ) { frame, _ ->
                    // 两页只画左侧色块；其余区域必须由 PageTurner 的不透明纸色覆盖。
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(if (frame == 0) Color.Red else Color.Blue),
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle { turnRequest = 1 }
        composeRule.mainClock.advanceTimeBy(110)

        val image = composeRule.onNodeWithTag("pager").captureToImage()
        val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
        assertTrue(
            "拖动中相邻页内容不应穿透当前页，实际像素=$pixel",
            pixel.red > 0.95f && pixel.green > 0.95f && pixel.blue > 0.95f,
        )
    }
}
