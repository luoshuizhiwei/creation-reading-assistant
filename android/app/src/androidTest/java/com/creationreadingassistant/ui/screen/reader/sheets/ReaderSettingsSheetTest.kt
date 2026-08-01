package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.theme.paperPalette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderSettingsSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allSettingsGroupsOpenAndReturnToRoot() {
        renderSettings()

        val groups = listOf(
            "排版" to "行文节奏",
            "翻页与操作" to "按键与自动翻页",
            "显示与页眉页脚" to "屏幕显示",
            "护眼与提醒" to "护眼滤镜",
            "高级兼容" to "分页兼容",
        )
        groups.forEach { (entry, pageContent) ->
            composeRule.onNodeWithText(entry).performScrollTo().performClick()
            composeRule.onNodeWithText(pageContent).assertIsDisplayed()
            composeRule.onNodeWithContentDescription("返回")
                .assertIsDisplayed()
                .assertHasClickAction()
                .performClick()
            composeRule.onNodeWithText("快捷调整").assertIsDisplayed()
        }
    }

    @Test
    fun dependentOptionsOnlyAppearWhenTheirParentIsEnabled() {
        renderSettings(
            initial = ReaderSettings(
                volumeKeyPaging = false,
                showReaderInfo = false,
                eyeCareFilterEnabled = false,
                eyeCareScheduleEnabled = false,
                readingRhythmReminderEnabled = false,
            ),
        )

        composeRule.onNodeWithText("翻页与操作").performScrollTo().performClick()
        composeRule.onAllNodesWithText("朗读时音量键仍翻页").assertCountEquals(0)
        composeRule.onNodeWithText("音量键翻页").performScrollTo().performClick()
        composeRule.onNodeWithText("朗读时音量键仍翻页").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()

        composeRule.onNodeWithText("显示与页眉页脚").performScrollTo().performClick()
        composeRule.onAllNodesWithText("页眉左侧").assertCountEquals(0)
        composeRule.onNodeWithText("显示安静阅读信息").performScrollTo().performClick()
        composeRule.onNodeWithText("页眉左侧").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()

        composeRule.onNodeWithText("护眼与提醒").performScrollTo().performClick()
        composeRule.onAllNodesWithText("色温").assertCountEquals(0)
        composeRule.onNodeWithText("开启护眼滤镜").performScrollTo().performClick()
        composeRule.onNodeWithText("色温").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun closingAndReopeningSettingsReturnsToRoot() {
        val visible = mutableStateOf(true)
        renderSettings(visible = visible)

        composeRule.onNodeWithText("高级兼容").performScrollTo().performClick()
        composeRule.onNodeWithText("分页兼容").assertIsDisplayed()
        composeRule.runOnIdle { visible.value = false }
        composeRule.onAllNodesWithText("分页兼容").assertCountEquals(0)
        composeRule.runOnIdle { visible.value = true }

        composeRule.onNodeWithText("快捷调整").assertIsDisplayed()
        composeRule.onAllNodesWithText("分页兼容").assertCountEquals(0)
    }

    @Test
    fun nightSettingsRemainUsableAt320DpWithLargeFont() {
        renderSettings(
            width = 320.dp,
            height = 640.dp,
            fontScale = 1.3f,
            darkTheme = true,
            initial = ReaderSettings(background = "night"),
        )

        val rootBounds = composeRule.onNodeWithTag("settings-root").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithText("阅读设置").assertIsDisplayed()
        composeRule.onNodeWithText("快捷调整").assertIsDisplayed()
        composeRule.onNodeWithText("高级兼容").performScrollTo().assertIsDisplayed()
        val advancedBounds = composeRule.onNodeWithText("高级兼容").fetchSemanticsNode().boundsInRoot
        assertTrue(advancedBounds.left >= rootBounds.left)
        assertTrue(advancedBounds.right <= rootBounds.right)
    }

    private fun renderSettings(
        width: Dp = 393.dp,
        height: Dp = 780.dp,
        fontScale: Float = 1f,
        darkTheme: Boolean = false,
        initial: ReaderSettings = ReaderSettings(),
        visible: MutableState<Boolean> = mutableStateOf(true),
    ) {
        composeRule.setContent {
            var settings by remember { mutableStateOf(initial) }
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                AppTheme(darkTheme = darkTheme) {
                    Box(
                        Modifier
                            .width(width)
                            .height(height)
                            .testTag("settings-root"),
                    ) {
                        if (visible.value) {
                            SettingsSheet(
                                paper = paperPalette(settings.background, darkTheme),
                                settings = settings,
                                onSettingsChange = { settings = it },
                                onBookInfo = {},
                            )
                        }
                    }
                }
            }
        }
    }
}
