package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 章节跳转边界（M-BUG-08）的纯 JVM 测试。
 *
 * 语义对齐 md3（legado）的 `moveToPrevChapter` / `moveToNextChapter`：
 * 不环绕、越界不可用。
 */
class ReaderChapterNavigationTest {

    @Test
    fun `a middle chapter can go both ways`() {
        assertEquals(true to true, readerChapterNavBounds(chapterIndex = 3, chapterCount = 10))
    }

    @Test
    fun `the first chapter cannot go back`() {
        assertEquals(false to true, readerChapterNavBounds(chapterIndex = 0, chapterCount = 10))
    }

    @Test
    fun `the last chapter cannot go forward`() {
        assertEquals(true to false, readerChapterNavBounds(chapterIndex = 9, chapterCount = 10))
    }

    @Test
    fun `a single chapter book cannot go anywhere`() {
        // 不环绕：只有一章时两个方向都不可用（对应 md3 的首章/末章同时成立）。
        assertEquals(false to false, readerChapterNavBounds(chapterIndex = 0, chapterCount = 1))
    }

    @Test
    fun `an unknown chapter count disables both buttons`() {
        // 章节信息未知（TXT 未识别出目录等）时，宁可禁用，也不给按下去没反应的按钮。
        assertEquals(false to false, readerChapterNavBounds(chapterIndex = 0, chapterCount = 0))
        assertEquals(false to false, readerChapterNavBounds(chapterIndex = 5, chapterCount = -1))
    }

    @Test
    fun `an out of range index is treated as the last chapter`() {
        assertEquals(true to false, readerChapterNavBounds(chapterIndex = 99, chapterCount = 10))
    }

    /**
     * 回归：[readerChapterNavBounds] 返回「能否上/下一章」，而消费方字段是「是否首章/末章」
     * 并按 `enabled = !isFirstChapter` 取反。两者语义相反，曾因直接透传导致**中间章**
     * 同时被判为首章+末章 → 两个按钮一起禁用、点击穿透到翻页手势（点「下一章」只翻一页）。
     * [readerChapterEdgeFlags] 是这条口径转换的唯一真相，下面按按钮启用态整链路钉死。
     */
    @Test
    fun `a middle chapter keeps both nav buttons enabled`() {
        val (isFirstChapter, isLastChapter) = readerChapterEdgeFlags(chapterIndex = 3, chapterCount = 6)
        assertEquals("中间章不是首章", false, isFirstChapter)
        assertEquals("中间章不是末章", false, isLastChapter)
        assertEquals("「上一章」必须可用", true, !isFirstChapter)
        assertEquals("「下一章」必须可用", true, !isLastChapter)
    }

    @Test
    fun `the first chapter disables only the prev button`() {
        val (isFirstChapter, isLastChapter) = readerChapterEdgeFlags(chapterIndex = 0, chapterCount = 6)
        assertEquals(true, isFirstChapter)
        assertEquals(false, isLastChapter)
        assertEquals("首章禁用「上一章」", false, !isFirstChapter)
        assertEquals("首章「下一章」仍可用", true, !isLastChapter)
    }

    @Test
    fun `the last chapter disables only the next button`() {
        val (isFirstChapter, isLastChapter) = readerChapterEdgeFlags(chapterIndex = 5, chapterCount = 6)
        assertEquals(false, isFirstChapter)
        assertEquals(true, isLastChapter)
        assertEquals("末章「上一章」仍可用", true, !isFirstChapter)
        assertEquals("末章禁用「下一章」", false, !isLastChapter)
    }

    @Test
    fun `a one chapter book disables both buttons`() {
        val (isFirstChapter, isLastChapter) = readerChapterEdgeFlags(chapterIndex = 0, chapterCount = 1)
        assertEquals(false to false, !isFirstChapter to !isLastChapter)
    }

}
