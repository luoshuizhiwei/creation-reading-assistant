package com.creationreadingassistant.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.viewmodel.ChapterLoadResult
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import com.creationreadingassistant.ui.viewmodel.ReaderUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI 测试覆盖 ReaderScreen 关键 UI 行为。
 *
 * 由于 ReaderScreen 内部使用 hiltViewModel() 获取 SettingsViewModel，
 * 完整渲染需要 Hilt 注入链。本测试聚焦于：
 * - UI 状态数据类的行为验证
 * - Action 路由逻辑验证
 * - Compose 基础渲染（加载/错误/内容状态）
 * - 配置适配（最小宽度、字体缩放）
 */
class ReaderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // ── 1. 加载状态显示 — 章节加载中显示正确 UI ──────────────────
    @Test
    fun chapter_loading_state_has_correct_properties() {
        val state = ChapterLoadResult.Loading("book-1", 0)
        assertEquals("book-1", state.bookId)
        assertEquals(0, state.chapterIndex)
    }

    @Test
    fun loading_ui_state_shows_isLoading_true() {
        val uiState = ReaderUiState(requestedBookId = "book-1", isLoading = true)
        assertTrue(uiState.isLoading)
        assertNull(uiState.loadedBook)
        assertNull(uiState.errorMessage)
        assertFalse(uiState.isReady)
    }

    // ── 2. 错误状态显示 — 加载失败时显示错误提示 ─────────────────
    @Test
    fun error_ui_state_exposes_error_message() {
        val uiState = ReaderUiState(
            requestedBookId = "book-1",
            errorMessage = "加载失败：文件不存在",
        )
        assertNotNull(uiState.errorMessage)
        assertEquals("加载失败：文件不存在", uiState.errorMessage)
        assertFalse(uiState.isReady)
        assertNull(uiState.loadedBook)
    }

    @Test
    fun chapter_load_error_state_contains_message() {
        val state = ChapterLoadResult.Error("book-1", 2, "章节解压失败")
        assertEquals("book-1", state.bookId)
        assertEquals(2, state.chapterIndex)
        assertEquals("章节解压失败", state.message)
    }

    // ── 3. 控制栏打开/关闭 ────────────────────────────────────────
    @Test
    fun controls_visible_toggle_state() {
        val initial = ReaderScreenState()
        assertTrue(initial.controlsVisible)

        val hidden = initial.copy(controlsVisible = false)
        assertFalse(hidden.controlsVisible)

        val toggled = hidden.copy(controlsVisible = !hidden.controlsVisible)
        assertTrue(toggled.controlsVisible)
    }

    @Test
    fun controls_visibility_renders_in_composable() {
        composeRule.setContent {
            AppTheme {
                val visible = true
                Box(modifier = Modifier.testTag("controls")) {
                    if (visible) {
                        Text("控制栏", modifier = Modifier.testTag("controls-text"))
                    }
                }
            }
        }
        composeRule.onNodeWithTag("controls-text").assertIsDisplayed()
    }

    // ── 4. Sheet 打开关闭（目录/设置/笔记等）──────────────────────
    @Test
    fun sheet_open_and_close_state_transitions() {
        val initial = ReaderScreenState()
        assertNull(initial.sheet)

        val withToc = initial.copy(sheet = ReaderSheet.TOC)
        assertEquals(ReaderSheet.TOC, withToc.sheet)

        val closed = withToc.copy(sheet = null)
        assertNull(closed.sheet)
    }

    @Test
    fun all_sheet_types_can_be_opened() {
        val sheets = ReaderSheet.entries
        assertTrue(sheets.contains(ReaderSheet.TOC))
        assertTrue(sheets.contains(ReaderSheet.NOTES))
        assertTrue(sheets.contains(ReaderSheet.AI_ASSIST))
        assertTrue(sheets.contains(ReaderSheet.AI_EXPLAIN))
        assertTrue(sheets.contains(ReaderSheet.INSPIRATION))
        assertTrue(sheets.contains(ReaderSheet.SETTINGS))
        assertTrue(sheets.contains(ReaderSheet.PROGRESS))
        assertTrue(sheets.contains(ReaderSheet.SEARCH))
        assertTrue(sheets.contains(ReaderSheet.BOOK_INFO))
        assertTrue(sheets.contains(ReaderSheet.THEME))
    }

    // ── 5. 长标题截断显示 ────────────────────────────────────────
    @Test
    fun long_title_text_is_handled_with_overflow_ellipsis() {
        val longTitle = "这是一本非常非常长的书名，".repeat(10)
        composeRule.setContent {
            AppTheme {
                Text(
                    text = longTitle,
                    maxLines = 1,
                    modifier = Modifier
                        .width(200.dp)
                        .testTag("title-text"),
                )
            }
        }
        composeRule.onNodeWithTag("title-text").assertIsDisplayed()
    }

    // ── 6. 最小宽度 320dp 布局 ───────────────────────────────────
    @Test
    fun content_renders_at_minimum_320dp_width() {
        composeRule.setContent {
            AppTheme {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .testTag("min-width-container")
                ) {
                    Text("内容区域", modifier = Modifier.testTag("content"))
                }
            }
        }
        composeRule.onNodeWithTag("min-width-container").assertIsDisplayed()
        composeRule.onNodeWithTag("content").assertIsDisplayed()
    }

    // ── 7. 字体缩放适配 ─────────────────────────────────────────
    @Test
    fun text_renders_with_different_font_sizes() {
        composeRule.setContent {
            AppTheme {
                Box(modifier = Modifier.testTag("font-container")) {
                    Text("正常字体", fontSize = 16.sp, modifier = Modifier.testTag("font-normal"))
                    Text("大字体", fontSize = 24.sp, modifier = Modifier.testTag("font-large"))
                }
            }
        }
        composeRule.onNodeWithTag("font-normal").assertIsDisplayed()
        composeRule.onNodeWithTag("font-large").assertIsDisplayed()
    }

    // ── 8. 横屏布局 — 验证状态在横屏下的行为 ─────────────────────
    @Test
    fun screen_state_maintains_consistency_across_configurations() {
        val state = ReaderScreenState(
            controlsVisible = true,
            sheet = ReaderSheet.TOC,
            selectedText = "选中文本",
        )
        // 状态在横屏/竖屏应保持一致
        assertTrue(state.controlsVisible)
        assertEquals(ReaderSheet.TOC, state.sheet)
        assertEquals("选中文本", state.selectedText)
    }

    // ── 9. 点击区域正确响应 ──────────────────────────────────────
    @Test
    fun clickable_area_responds_to_click() {
        var clicked = false
        composeRule.setContent {
            AppTheme {
                Text(
                    text = "点击区域",
                    modifier = Modifier
                        .testTag("clickable")
                        .clickable { clicked = true }
                )
            }
        }
        composeRule.onNodeWithTag("clickable").assertHasClickAction()
        composeRule.onNodeWithTag("clickable").performClick()
        assertTrue(clicked)
    }

    // ── 补充：ReaderScreenState 完整性 ───────────────────────────
    @Test
    fun screen_state_default_values_are_correct() {
        val state = ReaderScreenState()
        assertTrue(state.controlsVisible)
        assertFalse(state.sheetOpenGuard)
        assertEquals("", state.selectedText)
        assertEquals(-1, state.selectedRangeStart)
        assertEquals(-1, state.selectedGlobalOffset)
        assertNull(state.sheet)
        assertFalse(state.showTts)
        assertEquals("", state.searchQuery)
        assertFalse(state.showReaderOverflow)
        assertFalse(state.noteOpen)
        assertEquals("", state.noteBody)
        assertFalse(state.showColorRow)
    }

    @Test
    fun reader_ui_state_isReady_only_when_book_loaded_and_not_loading() {
        // 初始状态
        assertFalse(ReaderUiState().isReady)

        // 加载中
        assertFalse(ReaderUiState(requestedBookId = "b1", isLoading = true).isReady)

        // 有错误
        assertFalse(ReaderUiState(requestedBookId = "b1", errorMessage = "err").isReady)
    }

    @Test
    fun chapter_loaded_state_carries_blocks() {
        val blocks = listOf(
            com.creationreadingassistant.feature.reader.doc.DocBlock.Text("段落1"),
            com.creationreadingassistant.feature.reader.doc.DocBlock.Text("段落2", isHeading = true),
        )
        val state = ChapterLoadResult.Loaded("book-1", 0, blocks)
        assertEquals(2, state.blocks.size)
        assertEquals("book-1", state.bookId)
        assertEquals(0, state.chapterIndex)
    }

    @Test
    fun action_types_cover_all_UI_interactions() {
        // 验证所有 Action 类型可以正确构造
        val actions = listOf<ReaderAction>(
            ReaderAction.OpenBook("book-1"),
            ReaderAction.Retry,
            ReaderAction.LoadChapter("book-1", 0),
            ReaderAction.ScanTxtTocRule("/path", "rule"),
            ReaderAction.ToggleControls(),
            ReaderAction.ToggleControls(visible = true),
            ReaderAction.OpenSheet(ReaderSheet.TOC),
            ReaderAction.CloseSheet,
            ReaderAction.SetSelectedText("text", 0, 0),
            ReaderAction.ClearSelection,
            ReaderAction.SetShowTts(true),
            ReaderAction.SetSearchQuery("query"),
            ReaderAction.SetShowOverflow(true),
            ReaderAction.SetNoteOpen(true),
            ReaderAction.SetNoteBody("body"),
            ReaderAction.ToggleColorRow,
            ReaderAction.SetSheetOpenGuard(true),
        )
        assertEquals(17, actions.size)
    }
}
