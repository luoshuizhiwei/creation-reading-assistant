package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
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
                        notes = emptyList(),
                        onPickNote = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("目录与标记").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("当前章节").assertIsDisplayed()
        // 候选保留当前章阅读中提示：必须唯一且可见，不将视觉重构假设当作契约。
        composeRule.onAllNodesWithText("阅读中").assertCountEquals(1)
        composeRule.onNodeWithText("阅读中").assertIsDisplayed()
        composeRule.onNodeWithText("最近浏览").assertIsDisplayed()
    }

    @Test fun tocDisplayModelPreservesSourceIndicesAcrossVolumeHeaders() {
        val entries = readerTocEntries(
            titles = listOf("第一卷", "甲", "乙", "第二卷", "丙"),
            current = 4,
            recent = listOf(2),
            read = setOf(1, 4),
            wordCountLabels = listOf("", "820字", "1.2万字", "", "约2.5万字"),
        )
        assertEquals(listOf(1, 2, 4), entries.map { it.index })
        assertEquals("第二卷", entries.last().volume)
        assertEquals(true, entries.last().isCurrent)
        assertEquals(listOf(true, false, true), entries.map { it.isRead })
        assertEquals(listOf("820字", "1.2万字", "约2.5万字"), entries.map { it.wordCountLabel })
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
                    notes = emptyList(),
                    onPickNote = {},
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
                    notes = emptyList(),
                    onPickNote = {},
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
                    notes = emptyList(),
                    onPickNote = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("目录更多操作").performClick()
        composeRule.onNodeWithText("清除已读标记").performClick()
        assertEquals(false, cleared)
        composeRule.onNodeWithText("清除").performClick()
        composeRule.runOnIdle { assertTrue(cleared) }
    }

    // ===================== A01：目录与标记「笔记」Tab 的标注索引 =====================

    private fun highlight(id: String, text: String, chapter: String? = null) = HighlightEntity(
        id = id,
        book_id = "book-1",
        text = text,
        chapter_title = chapter,
        created_at = "2026-09-21T00:00:00",
        updated_at = "2026-09-21T00:00:00",
    )

    private fun note(id: String, kind: String = "note", title: String = "标题") = NoteEntity(
        id = id,
        title = title,
        body = "正文",
        kind = kind,
        created_at = "2026-09-21T00:00:00",
        updated_at = "2026-09-21T00:00:00",
    )

    @Test fun notesTabShowsHighlightsInsteadOfEmptyState() {
        val picked = mutableListOf<TocNoteEntry>()
        composeRule.setContent {
            AppTheme {
                TocSheet(
                    entries = readerTocEntries(listOf("第一章"), current = 0, recent = emptyList()),
                    current = 0,
                    totalChapters = 1,
                    onPick = {},
                    notes = tocNoteEntries(
                        highlights = listOf(highlight("h1", "这是一条划线摘录")),
                        notes = emptyList(),
                    ),
                    onPickNote = { picked += it },
                )
            }
        }

        composeRule.onNodeWithText("笔记").performClick()
        // 有高亮时不得显示空态 —— 这正是 A01 的用户可见缺陷。
        composeRule.onAllNodesWithText("暂无划线与笔记").assertCountEquals(0)
        composeRule.onNodeWithText("这是一条划线摘录").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, picked.size)
            assertEquals("highlight:h1", picked.single().navigationTarget)
        }
    }

    @Test fun notesTabShowsPlainNotesAndKeepsBookmarksOut() {
        val picked = mutableListOf<TocNoteEntry>()
        composeRule.setContent {
            AppTheme {
                TocSheet(
                    entries = readerTocEntries(listOf("第一章"), current = 0, recent = emptyList()),
                    current = 0,
                    totalChapters = 1,
                    onPick = {},
                    notes = tocNoteEntries(
                        highlights = emptyList(),
                        notes = listOf(note("n1", title = "我的批注"), note("b1", kind = "bookmark", title = "书签标题")),
                    ),
                    onPickNote = { picked += it },
                )
            }
        }

        composeRule.onNodeWithText("笔记").performClick()
        composeRule.onAllNodesWithText("暂无划线与笔记").assertCountEquals(0)
        composeRule.onNodeWithText("我的批注").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals("note:n1", picked.single().navigationTarget)
        }
        // 书签有自己的 Tab，不得重复进入笔记 Tab。
        composeRule.onAllNodesWithText("书签标题").assertCountEquals(0)
    }

    @Test fun notesTabShowsEmptyStateOnlyWhenBothSourcesAreEmpty() {
        composeRule.setContent {
            AppTheme {
                TocSheet(
                    entries = readerTocEntries(listOf("第一章"), current = 0, recent = emptyList()),
                    current = 0,
                    totalChapters = 1,
                    onPick = {},
                    notes = tocNoteEntries(highlights = emptyList(), notes = emptyList()),
                    onPickNote = {},
                )
            }
        }

        composeRule.onNodeWithText("笔记").performClick()
        composeRule.onNodeWithText("暂无划线与笔记").assertIsDisplayed()
    }
}
