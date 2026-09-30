package com.creationreadingassistant.ui.screen.shelf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S04 来源更新确认弹窗的措辞边界 —— 守住它**必须**怎么说、**不得**怎么说。
 *
 * 事实依据（与 [SOURCE_REFRESH_PROGRESS_NOTE] 的 KDoc 同源）：
 *
 * 1. **不得声称「重置阅读进度」。** 替换链路
 *    `ShelfImporter.refreshFromSource` → `repairFileTyped` → `reselectEpub` /
 *    `reselectPlainText` 完全不引用 `ReadingProgressDao`，且该 DAO 没有 delete / clear
 *    方法，全仓无重置调用方 —— 「重置」这个操作**不存在**，旧文案是假的。
 * 2. **也不得声称「进度不变」。** 正文换掉后阅读器重新加载会按新内容夹取旧坐标
 *    （EPUB `coerceIn(0, chapters.lastIndex)`、TXT/MD `resolveImportedTextOffset`），
 *    位置对应关系**可能变化**。真实契约是「记录行保留、坐标重新映射」。
 * 3. 因此文案只能陈述可确认的一半（不主动清除），再用「可能变化」覆盖不确定部分。
 *
 * 一旦有人把这里改回「重置」，用户会以为同步会丢进度而拒绝更新；
 * 改成「保留进度/进度不变」则会让用户对位置漂移毫无预期。
 */
class ShelfSourceRefreshCopyTest {

    @Test
    fun `source refresh copy does not claim progress is reset`() {
        val copy = sourceRefreshConfirmBody(count = 1, titleText = "")
        assertFalse("不得声称重置阅读进度", copy.contains("重置"))
        assertFalse("不得声称清空阅读进度", copy.contains("清空"))
        assertFalse("不得声称清除阅读进度", copy.contains("清除阅读进度"))
        assertFalse("不得回到『重置章节』的说法", copy.contains("重置章节"))
    }

    @Test
    fun `source refresh copy states existing reading records are not cleared`() {
        assertTrue(
            "必须显式说明不会主动清除已有阅读记录",
            sourceRefreshConfirmBody(1, "").contains("不会主动清除已有阅读记录"),
        )
    }

    @Test
    fun `source refresh copy warns that position may shift after content changes`() {
        val copy = sourceRefreshConfirmBody(1, "")
        assertTrue("必须提示章节会重新划分", copy.contains("章节会重新划分"))
        assertTrue("必须提示阅读位置可能变化（不确定项只能用『可能』）", copy.contains("阅读位置也可能随之变化"))
        assertFalse("不得把位置变化说成必然", copy.contains("阅读位置将重新计算"))
    }

    @Test
    fun `source refresh copy keeps the leading fact sentence intact`() {
        assertEquals(
            "检测到 2 本书（甲、乙）的源文件已被修改，是否重新同步正文？" +
                SOURCE_REFRESH_PROGRESS_NOTE,
            sourceRefreshConfirmBody(2, "（甲、乙）"),
        )
        assertTrue(
            sourceRefreshConfirmBody(3, "").startsWith("检测到 3 本书的源文件已被修改"),
        )
    }

    @Test
    fun `source refresh copy is a usable sentence rather than a placeholder`() {
        val copy = sourceRefreshConfirmBody(1, "")
        assertTrue(copy.isNotBlank())
        assertTrue("长度应足以承载完整语义", copy.length >= 20)
        assertFalse("不得出现未替换的模板空括号", copy.contains("（）"))
    }
}
