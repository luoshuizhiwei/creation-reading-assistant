package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.theme.paperPalette
import org.junit.Assert.assertEquals
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
        composeRule.onNodeWithText("当前").assertIsDisplayed()
        composeRule.onNodeWithText("最近浏览").assertIsDisplayed()
    }

    @Test fun tocDisplayModelPreservesSourceIndicesAcrossVolumeHeaders() {
        val entries = readerTocEntries(listOf("第一卷", "甲", "乙", "第二卷", "丙"), 4, listOf(2))
        assertEquals(listOf(1, 2, 4), entries.map { it.index })
        assertEquals("第二卷", entries.last().volume)
        assertEquals(true, entries.last().isCurrent)
    }
}
