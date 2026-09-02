package com.creationreadingassistant.ui.screen.reader

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderBottomActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun draggingProgressShowsTheTargetPercentBeforeSeeking() {
        composeRule.setContent {
            ReaderBottomActions(
                onAction = {},
                chapterProgress = 10f,
                onSeekProgress = {},
                onPreviousChapter = {},
                onNextChapter = {},
                isFirstChapter = false,
                isLastChapter = false,
                autoPagingActive = false,
                autoPageSpeed = 5,
                onAutoPageSpeedChange = {},
            )
        }

        composeRule.onNodeWithTag("reader-progress-scrubber")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(63f))
            }

        composeRule.onNodeWithText("跳到 63%").assertExists()
    }

}
