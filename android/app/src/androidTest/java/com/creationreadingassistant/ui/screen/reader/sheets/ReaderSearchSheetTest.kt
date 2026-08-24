package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.creationreadingassistant.ui.screen.reader.BookSearchResult
import com.creationreadingassistant.ui.screen.reader.BookSearchSession
import com.creationreadingassistant.ui.screen.reader.searchContextKeyOf
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.theme.paperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * S2 书内搜索 UI 行为（验收 1）：
 * - 点击具体搜索结果：选中 + 关闭搜索 Sheet（正文可见），结果行有明确选中态/语义；
 * - 上一处 / 下一处留在 Sheet 内，不关闭。
 */
class ReaderSearchSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sessionWith(count: Int): BookSearchSession {
        val s = BookSearchSession(bookKey = "book-1")
        s.onQueryChanged("测试")
        // 与 SearchSheet 内 searchContextKeyOf(null, null, "") 同一上下文，避免面板内重启搜索
        val run = s.beginRun(searchContextKeyOf(null, null, ""))
        s.onSearchCompleted(
            run,
            List(count) { i ->
                BookSearchResult(
                    occurrenceIndex = i,
                    snippet = "结果片段$i",
                    progressPercent = i * 0.1f,
                    chapterIndex = -1,
                    chapterTitle = "全文",
                    charOffset = i * 10,
                    absoluteRange = i * 10 until i * 10 + 2,
                )
            },
        )
        return s
    }

    private fun setSearchSheet(
        session: BookSearchSession,
        query: String = "测试",
        onResultSelected: (Int) -> Unit = {},
    ) {
        composeRule.setContent {
            AppTheme {
                ReaderPaperTheme(paperPalette("white", darkTheme = false)) {
                    SearchSheet(
                        document = null,
                        txtDocument = null,
                        plainContent = "",
                        chapterStartOffsets = emptyList(),
                        chapterTitles = emptyList(),
                        totalChars = 0,
                        isTxt = true,
                        query = query,
                        onQueryChange = {},
                        session = session,
                        onResultSelected = onResultSelected,
                    )
                }
            }
        }
    }

    @Test
    fun clickingResult_selectsItAndDismissesSheet() {
        val session = sessionWith(3)
        var selectedIndex = -1
        var dismissed = false
        setSearchSheet(session, onResultSelected = { index ->
            selectedIndex = index
            dismissed = true
        })

        composeRule.onNodeWithTag("search-result-1").assertIsDisplayed().performClick()

        assertEquals(1, selectedIndex)
        assertTrue("点击结果必须关闭 Sheet（正文可见）", dismissed)
        assertEquals(1, session.currentIndex)
        assertEquals(1, session.currentTarget?.resultIndex)
    }

    @Test
    fun openingSearch_focusesQueryField() {
        setSearchSheet(sessionWith(0))

        composeRule.onNodeWithTag("reader-search-field").assertIsFocused()
    }

    @Test
    fun blankQuery_showsShortGuidanceInsteadOfAnEmptyCanvas() {
        setSearchSheet(BookSearchSession(bookKey = "book-1"), query = "")

        composeRule.onNodeWithText("输入关键词开始搜索").assertIsDisplayed()
        composeRule.onNodeWithText("可搜索人名、设定或句子片段。").assertIsDisplayed()
        composeRule.onNodeWithText("未找到匹配结果").assertDoesNotExist()
    }

    @Test
    fun searchingWithoutResults_doesNotClaimThereAreNoMatches() {
        val session = BookSearchSession(bookKey = "book-1")
        session.onQueryChanged("测试")
        setSearchSheet(session)

        composeRule.onNodeWithText("正在搜索…").assertIsDisplayed()
        composeRule.onNodeWithText("未找到匹配结果").assertDoesNotExist()
    }

    @Test
    fun completedResults_showTotalAndCurrentPosition() {
        setSearchSheet(sessionWith(3))

        composeRule.onNodeWithText("共 3 处结果 · 最多显示前 80 条").assertIsDisplayed()
        composeRule.onNodeWithText("共 3 处").assertIsDisplayed()
        composeRule.onNodeWithText("下一处").performClick()
        composeRule.onNodeWithText("当前位置 1 / 3").assertIsDisplayed()
    }

    @Test
    fun resultList_exposesSelectedSemanticsAndUpdatesImmediately() {
        val session = sessionWith(3)
        setSearchSheet(session)

        composeRule.onNodeWithTag("search-result-0").assertIsNotSelected()
        composeRule.onNodeWithTag("search-result-1").performClick()

        // 点击即选中：语义与可视化选中态同步更新（不等待导航完成）
        composeRule.onNodeWithTag("search-result-1").assertIsSelected()
        composeRule.onNodeWithTag("search-result-0").assertIsNotSelected()
        composeRule.onNodeWithTag("search-result-2").assertIsNotSelected()
    }

    @Test
    fun previousAndNext_stayInSheetWithoutDismissing() {
        val session = sessionWith(3)
        var dismissed = false
        setSearchSheet(session, onResultSelected = { dismissed = true })

        composeRule.onNodeWithText("下一处").performClick()
        assertEquals(0, session.currentIndex)
        assertFalse("上一处/下一处必须留在 Sheet 内", dismissed)

        composeRule.onNodeWithText("下一处").performClick()
        assertEquals(1, session.currentIndex)

        composeRule.onNodeWithText("上一处").performClick()
        assertEquals(0, session.currentIndex)
        assertFalse("上一处/下一处必须留在 Sheet 内", dismissed)
    }
}
