package com.creationreadingassistant.ui.screen.reader.sheets

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.creationreadingassistant.ui.theme.AppTheme
import com.creationreadingassistant.ui.theme.ReaderPaperTheme
import com.creationreadingassistant.ui.theme.paperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * READER-PAGES-v1 集中修正补验：目录长标题「长按展开完整标题」的组件行为验收。
 *
 * 用 compose 测试的确定性 longClick（排除 adb 手势注入的不确定性）：
 * 断言长按后标题节点高度从「两行截断」变为「完整展开」，且行仍在目录内
 * （长按不触发跳章——combinedClickable 消歧的行为证明）。
 */
class ReaderTocRowLongPressExpandTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun longPressExpandsClippedLongTitleWithoutNavigating() {
        // 约 60 个全角字符：默认视口必然超过两行，截断态可观测。
        val longTitle = "第001章 " + "超长章节标题验证".repeat(12)
        val entries = readerTocEntries(
            titles = listOf(longTitle, "第二章 乙", "第三章 丙"),
            current = 0,
            recent = emptyList(),
        )
        var picks = 0
        composeRule.setContent {
            AppTheme {
                ReaderPaperTheme(paperPalette("white", false)) {
                    TocSheet(
                        entries = entries,
                        current = 0,
                        totalChapters = entries.size,
                        onPick = { picks++ },
                        notes = emptyList(),
                        onPickNote = {},
                    )
                }
            }
        }
        // 高度差分（与隔离探针同法，已在设备证明可靠）：101 字标题在 TocSheet 行宽内
        // 必然超过两行 → 截断态高度 < 展开态高度；长按不得触发跳章。
        val before = composeRule.onNodeWithText(longTitle)
            .fetchSemanticsNode().boundsInRoot.height
        composeRule.onNodeWithText(longTitle).performTouchInput { longClick() }
        composeRule.waitForIdle()
        val after = composeRule.onNodeWithText(longTitle)
            .fetchSemanticsNode().boundsInRoot.height
        assertEquals("长按不得触发跳章（若此值=1说明长按被当点击消费）", 0, picks)
        assertTrue(
            "长按后标题必须从两行截断展开为完整标题（$before -> $after）",
            after > before,
        )
    }
}
