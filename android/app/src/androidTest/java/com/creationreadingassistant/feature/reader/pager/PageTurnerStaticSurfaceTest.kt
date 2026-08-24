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

class PageTurnerStaticSurfaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settledNoneEffectDoesNotPaintASeparatePaperCard() {
        var turnRequest by mutableIntStateOf(0)

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
                    nextFrame = null,
                    effect = "none",
                    turnRequest = turnRequest,
                    onTurnRequestConsumed = { turnRequest = 0 },
                    onPrevious = {},
                    onNext = {},
                    pageSurface = ReaderPaperSurfaceSpec.solid(Color.White),
                ) { frame, _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (frame == 0) Modifier.size(120.dp) else Modifier),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val image = composeRule.onNodeWithTag("pager").captureToImage()
        val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
        assertTrue(
            "静止的 none 页面不应把纸面铺成独立白色卡片，实际像素=$pixel",
            pixel.red < 0.05f && pixel.green < 0.05f && pixel.blue < 0.05f,
        )
    }
}
