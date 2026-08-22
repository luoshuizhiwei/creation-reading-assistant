package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.theme.paperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderTocSheetTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun nightPaperKeepsCurrentChapterAndSecondaryTextVisible() {
        val titles = listOf("第一卷", "开端", "一个非常非常长但仍然只能占据两行的章节标题", "尾声")
        composeRule.setContent {
            AppTheme(darkTheme = true) {
                ReaderPaperTheme(paperPalette("night", true)) {
                    TocSheet(
                        entries = readerTocEntries(titles, current = 2, recent = listOf(1, 2)),
                        current = 2,
                        totalChapters = titles.size,
                        onPick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("目录").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("当前章节").assertIsDisplayed()
        composeRule.onNodeWithText("最近浏览").assertIsDisplayed()
    }

    @Test fun tocDisplayModelPreservesSourceIndicesAcrossVolumeHeaders() {
        val entries = readerTocEntries(
            titles = listOf("第一卷", "甲", "乙", "第二卷", "丙"),
            current = 4,
            recent = listOf(2),
            read = setOf(1, 4),
        )
        assertEquals(listOf(1, 2, 4), entries.map { it.index })
        assertEquals("第二卷", entries.last().volume)
        assertEquals(true, entries.last().isCurrent)
        assertEquals(listOf(true, false, true), entries.map { it.isRead })
    }

    @Test fun epubReadMarksShowWholeBookAndVolumeCountsWithoutOverridingCurrentStyle() {
        val entries = readerTocEntries(
            titles = listOf("第一卷", "甲", "乙", "第二卷", "丙"),
            current = 4,
            recent = emptyList(),
            read = setOf(1, 4),
        )
        composeRule.setContent {
            AppTheme {
                TocSheet(
                    entries = entries,
                    current = 4,
                    totalChapters = 5,
                    onPick = {},
                    showReadStatus = true,
                )
            }
        }

        composeRule.onNodeWithText("已读 2/3").assertIsDisplayed()
        composeRule.onNodeWithText("已读 1/2").assertIsDisplayed()
        composeRule.onNodeWithText("已读 1/1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("当前章节").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("已读章节").assertCountEquals(1)
    }

    @Test fun txtTocDoesNotShowReadStatus() {
        composeRule.setContent {
            AppTheme {
                TocSheet(
                    entries = readerTocEntries(
                        titles = listOf("第一章", "第二章"),
                        current = 0,
                        recent = emptyList(),
                    ),
                    current = 0,
                    totalChapters = 2,
                    onPick = {},
                    showReadStatus = false,
                )
            }
        }

        composeRule.onNodeWithText("2章").assertIsDisplayed()
        composeRule.onAllNodesWithText("已读 0/2").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("已读章节").assertCountEquals(0)
    }

    @Test fun clearReadMarksRequiresConfirmationBeforeCallingBack() {
        var cleared = false
        composeRule.setContent {
            AppTheme {
                TocSheet(
                    entries = readerTocEntries(listOf("第一章"), current = 0, recent = emptyList()),
                    current = 0,
                    totalChapters = 1,
                    onPick = {},
                    showClearReadMarks = true,
                    onClearReadMarks = { cleared = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("目录更多操作").performClick()
        composeRule.onNodeWithText("清除已读标记").performClick()
        assertEquals(false, cleared)
        composeRule.onNodeWithText("清除").performClick()
        composeRule.runOnIdle { assertTrue(cleared) }
    }
}
