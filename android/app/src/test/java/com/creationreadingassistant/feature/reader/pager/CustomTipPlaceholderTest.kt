package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.data.settings.HeaderFooterItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTipPlaceholderTest {

    @Test
    fun extractPlaceholdersFindsAllKeys() {
        val template = "《{BookName}》 {ChapterTitle} [{PageIndex}/{PageSize}] ({ReadProgress})"
        val placeholders = CustomTipPlaceholder.extractPlaceholders(template)
        assertEquals(listOf("BookName", "ChapterTitle", "PageIndex", "PageSize", "ReadProgress"), placeholders)
    }

    @Test
    fun extractPlaceholdersIgnoresUnclosedBraces() {
        val template = "{BookName} {Unknown {Time}"
        val placeholders = CustomTipPlaceholder.extractPlaceholders(template)
        assertEquals(listOf("BookName", "Time"), placeholders)
    }

    @Test
    fun isValidReturnsTrueForKnownPlaceholders() {
        assertTrue(CustomTipPlaceholder.isValid("{BookName}"))
        assertTrue(CustomTipPlaceholder.isValid("{ChapterTitle} · {Time}"))
        assertTrue(CustomTipPlaceholder.isValid("{PageIndex}/{PageSize} - {PageRemaining}页剩余"))
        assertTrue(CustomTipPlaceholder.isValid("纯文本没有占位符也是合法的"))
        assertTrue(CustomTipPlaceholder.isValid(""))
    }

    @Test
    fun isValidReturnsFalseForUnknownPlaceholders() {
        assertFalse(CustomTipPlaceholder.isValid("{InvalidToken}"))
        assertFalse(CustomTipPlaceholder.isValid("{BookName} {WrongKey}"))
        assertFalse(CustomTipPlaceholder.isValid("{foo}"))
    }

    @Test
    fun fromHeaderFooterItemConvertsCorrectly() {
        assertEquals("", CustomTipPlaceholder.fromHeaderFooterItem(HeaderFooterItem.NONE))
        assertEquals("{ChapterTitle}", CustomTipPlaceholder.fromHeaderFooterItem(HeaderFooterItem.CHAPTER_TITLE))
        assertEquals("{BookName}", CustomTipPlaceholder.fromHeaderFooterItem(HeaderFooterItem.BOOK_NAME))
        assertEquals("{Time}", CustomTipPlaceholder.fromHeaderFooterItem(HeaderFooterItem.TIME))
        assertEquals("{BatteryPercent}", CustomTipPlaceholder.fromHeaderFooterItem(HeaderFooterItem.BATTERY))
        assertEquals("{PageIndex}/{PageSize}", CustomTipPlaceholder.fromHeaderFooterItem(HeaderFooterItem.PAGE_NUMBER))
        assertEquals("{ReadProgress}", CustomTipPlaceholder.fromHeaderFooterItem(HeaderFooterItem.PROGRESS))
    }

    @Test
    fun renderReplacesAllKnownPlaceholders() {
        val template = "{BookName} - {ChapterTitle} [{PageIndex}/{PageSize}] 剩余{PageRemaining}页 时间:{Time} 电量:{BatteryPercent}"
        val rendered = CustomTipPlaceholder.render(
            template = template,
            source = null,
            absOffset = 0,
            pageInfo = PageInfo(
                chapterIndex = 0,
                pageIndex = 2, // 0-based, so PageIndex = 3
                pageCount = 10,
                progressPercent = 30f,
            ),
            currentTime = "18:30",
            batteryLevel = 88,
            bookName = "百年孤独",
        )
        assertEquals("百年孤独 -  [3/10] 剩余7页 时间:18:30 电量:88%", rendered)
    }

    @Test
    fun renderHandlesEmptyTemplateGracefully() {
        val rendered = CustomTipPlaceholder.render(
            template = "",
            source = null,
            absOffset = 0,
            pageInfo = PageInfo(),
            currentTime = "12:00",
            batteryLevel = 100,
            bookName = "Test",
        )
        assertEquals("", rendered)
    }

    @Test
    fun renderSampleEvaluatesPreviewCorrectly() {
        assertEquals("", CustomTipPlaceholder.renderSample(""))
        val preview = CustomTipPlaceholder.renderSample("《{BookName}》 {ChapterTitle} [{PageIndex}/{PageSize}] ({ReadProgress})")
        assertEquals("《测试书籍》 第3章 序幕 [1/18] (24.5%)", preview)
    }
}
