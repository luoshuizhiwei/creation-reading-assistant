package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.rules.BuiltinTocRules
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.theme.paperPalette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 规则管理 Sheet 与目录页规则入口的 Compose 测试：
 * 双 tab、新增按钮、同 Sheet 编辑器、空状态，以及 TocSheet 顶部入口的 contentDescription 与回调。
 */
class ReaderRulesSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun renderRulesSheet() {
        composeRule.setContent {
            AppTheme(darkTheme = false) {
                ReaderPaperTheme(paperPalette("white", false)) {
                    RulesSheet(
                        snapshot = RuleSnapshot(
                            bookId = "book-1",
                            tocRules = BuiltinTocRules.seeds,
                            replaceRules = emptyList(),
                            effectiveToc = BuiltinTocRules.seeds.filter { it.enabled },
                            effectiveReplace = emptyList(),
                        ),
                        previewText = "第一章 起点\n第二章 转折",
                        mutationResult = null,
                        onCommand = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun bothTabsAndAddButtonRender() {
        renderRulesSheet()

        composeRule.onNodeWithText("目录规则").assertIsDisplayed()
        composeRule.onNodeWithText("替换净化").assertIsDisplayed()
        composeRule.onNodeWithText("新增规则").assertIsDisplayed()
    }

    @Test
    fun replaceTabShowsUnderstandableEmptyState() {
        renderRulesSheet()

        composeRule.onNodeWithText("替换净化").performClick()
        composeRule.onNodeWithText("暂无替换净化规则").assertIsDisplayed()
    }

    @Test
    fun addButtonOpensSameSheetEditor() {
        renderRulesSheet()

        composeRule.onNodeWithText("新增规则").performClick()
        composeRule.onNodeWithText("新增规则").assertIsDisplayed()
        composeRule.onNodeWithText("规则名称").assertIsDisplayed()
        composeRule.onNodeWithText("正则表达式").assertIsDisplayed()
    }

    @Test
    fun tocSheetShowsRulesEntryWithContentDescriptionAndCallsCallback() {
        var opened = false
        composeRule.setContent {
            AppTheme(darkTheme = true) {
                ReaderPaperTheme(paperPalette("night", true)) {
                    TocSheet(
                        entries = readerTocEntries(listOf("第一卷", "开端", "尾声"), current = 1, recent = emptyList()),
                        current = 1,
                        totalChapters = 2,
                        onPick = {},
                        txtRules = TxtChapterDetector.rules,
                        onManageRules = { opened = true },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("管理目录与净化规则")
            .assertIsDisplayed()
            .performClick()
        assertTrue(opened)
    }

    @Test
    fun tocSheetHidesRulesEntryWithoutTxtRules() {
        composeRule.setContent {
            AppTheme(darkTheme = true) {
                ReaderPaperTheme(paperPalette("night", true)) {
                    TocSheet(
                        entries = readerTocEntries(listOf("卷一", "甲"), current = 0, recent = emptyList()),
                        current = 0,
                        totalChapters = 1,
                        onPick = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("管理目录与净化规则").assertCountEquals(0)
    }
}
