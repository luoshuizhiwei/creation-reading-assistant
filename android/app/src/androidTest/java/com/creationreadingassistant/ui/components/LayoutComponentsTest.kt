package com.creationreadingassistant.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import com.creationreadingassistant.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class LayoutComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingRowProvidesMinimumTouchHeight() {
        composeRule.setContent {
            AppTheme {
                SettingRow(
                    title = "阅读设置",
                    modifier = Modifier.testTag("setting-row"),
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("setting-row")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun compactEmptyStateKeepsPrimaryActionVisible() {
        composeRule.setContent {
            AppTheme {
                CompactEmptyState(
                    title = "暂无内容",
                    message = "稍后可以从这里继续。",
                    actionLabel = "开始",
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("暂无内容").assertIsDisplayed()
        composeRule.onNodeWithText("开始").assertIsDisplayed()
    }

    @Test
    fun rootTopBarUsesSharedHeightAndHeadline() {
        composeRule.setContent {
            AppTheme {
                AppTopBar(
                    title = "书架",
                    modifier = Modifier.testTag("app-top-bar"),
                )
            }
        }

        composeRule.onNodeWithTag("app-top-bar")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(64.dp)
        composeRule.onNodeWithText("书架").assertIsDisplayed()
    }
}
