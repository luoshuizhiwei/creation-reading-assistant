package com.creationreadingassistant.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.ui.screen.reader.ReaderBottomActions
import com.creationreadingassistant.ui.screen.reader.ReaderDocumentStatus
import com.creationreadingassistant.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderAccessibilityLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readerControlsFitAt320DpWithLargeFont() {
        renderControls(width = 320.dp, height = 360.dp, fontScale = 1.5f)
        assertControlsAreReachableAndInsideRoot()
    }

    @Test
    fun readerControlsFitAt393DpWithUserFontScale() {
        renderControls(width = 393.dp, height = 420.dp, fontScale = 1.3f)
        assertControlsAreReachableAndInsideRoot()
    }

    @Test
    fun readerControlsFitAt430DpAndLandscapeHeight() {
        renderControls(width = 430.dp, height = 320.dp, fontScale = 1.3f)
        assertControlsAreReachableAndInsideRoot()
    }

    @Test
    fun longRecoverableErrorKeepsActionsVisibleAtLargeFont() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                AppTheme {
                    Box(
                        Modifier
                            .width(320.dp)
                            .height(480.dp)
                            .testTag("root")
                    ) {
                        ReaderDocumentStatus(
                            isLoading = false,
                            errorMessage = "本地文件已经移动或访问权限失效。返回书架后可以重新定位文件，阅读进度、书签与笔记会继续保留。",
                            foreground = Color.Black,
                            onBack = {},
                            onRetry = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("返回书架")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("重新打开")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    private fun renderControls(width: Dp, height: Dp, fontScale: Float) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                AppTheme {
                    Box(
                        Modifier
                            .width(width)
                            .height(height)
                            .testTag("root")
                    ) {
                        ReaderBottomActions(
                            onAction = {},
                            chapterProgress = 42f,
                            onSeekProgress = {},
                            onPreviousChapter = {},
                            onNextChapter = {},
                            isFirstChapter = false,
                            isLastChapter = false,
                        )
                    }
                }
            }
        }
    }

    private fun assertControlsAreReachableAndInsideRoot() {
        val rootBounds = composeRule.onNodeWithTag("root").fetchSemanticsNode().boundsInRoot
        listOf("目录", "听书", "灵感", "主题", "设置").forEach { label ->
            val node = composeRule.onNodeWithText(label)
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
            assertInside(rootBounds, node.fetchSemanticsNode().boundsInRoot, label)
        }
        composeRule.onNodeWithContentDescription("上一章")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("下一章")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    private fun assertInside(root: Rect, child: Rect, label: String) {
        assertTrue("$label 左侧被裁切", child.left >= root.left)
        assertTrue("$label 右侧被裁切", child.right <= root.right)
        assertTrue("$label 顶部被裁切", child.top >= root.top)
        assertTrue("$label 底部被裁切", child.bottom <= root.bottom)
    }
}
