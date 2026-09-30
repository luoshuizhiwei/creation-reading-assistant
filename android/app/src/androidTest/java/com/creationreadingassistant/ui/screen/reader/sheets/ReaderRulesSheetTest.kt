package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.creationreadingassistant.feature.reader.doc.TxtChapterDetector
import com.creationreadingassistant.feature.reader.rules.BuiltinTocRules
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot
import com.creationreadingassistant.ui.screen.reader.ReaderReplacementCapability
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

    private fun renderRulesSheet(
        replacementCapability: ReaderReplacementCapability = ReaderReplacementCapability.Available(),
    ) {
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
                        replacementCapability = replacementCapability,
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
        composeRule.onNodeWithText("章节模式正则").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unsupportedReaderHidesReplaceTabAndExplainsSourceTextIsPreserved() {
        val message = "正文替换净化仅在新分页引擎的左右翻页模式生效，当前模式将保留原文。"

        renderRulesSheet(ReaderReplacementCapability.Unavailable(message))

        composeRule.onAllNodesWithText("替换净化").assertCountEquals(0)
        composeRule.onNodeWithText("替换净化当前不可用").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
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
                        // A01：TocSheet 的 notes/onPickNote 已改为必填（去掉默认空列表，
                        // 防止生产宿主漏传时静默显示空态）。本用例不关心笔记 Tab，显式传空。
                        notes = emptyList(),
                        onPickNote = {},
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
                        // A01：同上，显式传空（本用例只验证规则入口的显隐）。
                        notes = emptyList(),
                        onPickNote = {},
                    )
                }
            }
        }

        composeRule.onAllNodesWithContentDescription("管理目录与净化规则").assertCountEquals(0)
    }
}
