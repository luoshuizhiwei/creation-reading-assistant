package com.creationreadingassistant.ui.screen.reader

import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    @Test
    fun synchronousSeekProgressUpdateKeepsPreviewVisibleForAtLeastOneFrame() {
        composeRule.setContent {
            var chapterProgress by remember { mutableFloatStateOf(10f) }
            ReaderBottomActions(
                onAction = {},
                chapterProgress = chapterProgress,
                onSeekProgress = { chapterProgress = it },
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
        val minimumVisibleUntil = SystemClock.uptimeMillis() + SEEK_PREVIEW_LINGER_MILLIS + 1L
        composeRule.waitUntil(timeoutMillis = SEEK_PREVIEW_LINGER_MILLIS + 500L) {
            SystemClock.uptimeMillis() >= minimumVisibleUntil &&
                composeRule.onAllNodesWithText("跳到 63%").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("跳到 63%").assertDoesNotExist()
    }

}
