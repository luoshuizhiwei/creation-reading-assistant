package com.creationreadingassistant.ui.screen.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.creationreadingassistant.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibrarySortControlsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun sortModeHeaderSwitchesBetweenNormalAndSortingCopy() {
        composeRule.setContent {
            AppTheme {
                var sorting by remember { mutableStateOf(false) }
                LibrarySortModeHeader(
                    sorting = sorting,
                    itemCount = 3,
                    onToggle = { sorting = !sorting },
                )
            }
        }

        composeRule.onNodeWithText("调整顺序").performClick()
        composeRule.onNodeWithText("完成排序").assertIsDisplayed()
        composeRule.onNodeWithText("使用箭头调整，修改会立即保存").assertIsDisplayed()
    }

    @Test fun orderControlsDisableBoundariesAndExposeNamedActions() {
        var downCalls = 0
        composeRule.setContent {
            AppTheme {
                TaxonomyOrderControls(
                    name = "分类甲",
                    position = 0,
                    total = 3,
                    onMoveUp = {},
                    onMoveDown = { downCalls++ },
                )
            }
        }

        composeRule.onNodeWithText("01").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("上移分类甲").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("下移分类甲").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, downCalls) }
    }
}
