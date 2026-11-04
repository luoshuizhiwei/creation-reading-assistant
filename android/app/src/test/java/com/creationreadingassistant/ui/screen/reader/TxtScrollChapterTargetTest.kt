package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.DocChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S07-F2：纯 TXT 滚动模式章节导航的目标偏移与「是否真的换了章」判定。
 *
 * 这条路径走 `jumpToPlainOffset` 而非 `LoadChapter`，所以调用方要自己清选区。
 * 该函数把「没有可跳目标」表达为 null，调用方据此**只在真的跳了时才清** ——
 * 首章点「上一章」、末章点「下一章」、目录未识别时都不得清选区，
 * 因为这些情况下页面/章节身份没有发生变化。
 */
class TxtScrollChapterTargetTest {

    private fun chapters(vararg starts: Int): List<DocChapter> =
        starts.mapIndexed { i, start ->
            DocChapter(index = i, title = "第${i + 1}章", startOffset = start, charCount = 100)
        }

    @Test
    fun `next chapter targets the following chapter start`() {
        // 索引 1（章首 1_000）的下一章是索引 2（章首 2_500）。
        assertEquals(2_500, txtScrollChapterTargetOffset(1, chapters(0, 1_000, 2_500), 1))
    }

    @Test
    fun `previous chapter targets the end of the previous chapter`() {
        // 上一章末尾 = 本章章首 - 1，对齐 md3 moveToPrevChapter(toLast = true)。
        assertEquals(999, txtScrollChapterTargetOffset(-1, chapters(0, 1_000, 2_500), 1))
    }

    @Test
    fun `the first chapter previous target falls back to the chapter start`() {
        // 首章章首为 0 → 0-1 被 coerceAtLeast(0) 钳到 0，所以**有**目标（滚回本章开头），
        // 不是 null。这与 EPUB/Markdown 路径的 `i.coerceIn(0, maxIndex)` 同向：
        // 「上一章」在首章退化为「回到本章开头」，而不是无动作。
        //
        // 因此首章点「上一章」时调用方仍会发 ClearSelection（位置确实移动了）。
        // 该行为由本用例显式记录，避免被误读成「首章边界已挡住、不会清选区」。
        val target = txtScrollChapterTargetOffset(-1, chapters(0, 1_000), 0)

        assertEquals(0, target)
        assertNotNull("首章「上一章」仍有目标（回到章首），调用方据此清选区", target)
    }

    @Test
    fun `the last chapter has no next target`() {
        assertNull("末章点「下一章」没有目标，不得伪装成一次导航", txtScrollChapterTargetOffset(1, chapters(0, 1_000), 1))
    }

    @Test
    fun `an unidentified toc has no target in either direction`() {
        assertNull(txtScrollChapterTargetOffset(1, emptyList(), 0))
        assertNull(txtScrollChapterTargetOffset(-1, emptyList(), 0))
    }

    @Test
    fun `an out of range chapter index has no target`() {
        assertNull(txtScrollChapterTargetOffset(1, chapters(0, 1_000), 5))
        assertNull(txtScrollChapterTargetOffset(-1, chapters(0, 1_000), 5))
    }

    @Test
    fun `a zero direction never produces a target`() {
        assertNull(txtScrollChapterTargetOffset(0, chapters(0, 1_000), 0))
    }
}
