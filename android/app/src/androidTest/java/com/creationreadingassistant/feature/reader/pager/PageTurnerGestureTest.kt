package com.creationreadingassistant.feature.reader.pager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import com.creationreadingassistant.ui.theme.ReaderPaperSurfaceSpec

class PageTurnerGestureTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settledPageDoesNotStealShortTapFromPageContent() {
        var tapCount by mutableIntStateOf(0)

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                PageTurner(
                    currentFrame = 0,
                    previousFrame = null,
                    nextFrame = 1,
                    effect = "cover",
                    turnRequest = 0,
                    onTurnRequestConsumed = {},
                    onPrevious = {},
                    onNext = {},
                    pageSurface = ReaderPaperSurfaceSpec.solid(Color.White),
                ) { _, _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("page-content")
                            .clickable { tapCount++ },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("page-content").performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, tapCount) }
    }
}
