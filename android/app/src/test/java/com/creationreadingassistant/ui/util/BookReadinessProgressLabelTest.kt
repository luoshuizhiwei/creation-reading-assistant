package com.creationreadingassistant.ui.util

import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S05-① 跨页完成态判定的判别测试：
 * 「100% 但 completion_state=reading（用户取消读完）」与「真实 finished」必须给出不同结果。
 */
class BookReadinessProgressLabelTest {

    // ── isReadingFinished：显式状态权威 ──────────────────────────

    @Test
    fun `cancelled full percent is not finished`() {
        // markFinished 抬高的 100% 残余 + 用户取消读完（state=reading）
        assertFalse(isReadingFinished(progress(percent = 100f, state = "reading")))
    }

    @Test
    fun `explicit finished is finished regardless of percent`() {
        assertTrue(isReadingFinished(progress(percent = 100f, state = "finished")))
        assertTrue(isReadingFinished(progress(percent = 30f, state = "finished")))
        // 历史值兼容
        assertTrue(isReadingFinished(progress(percent = 0f, state = "completed")))
    }

    @Test
    fun `shelved and missing rows are not finished`() {
        assertFalse(isReadingFinished(progress(percent = 100f, state = "shelved")))
        assertFalse(isReadingFinished(null))
    }

    // ── formatBookProgressForCard：状态否决「已读完」文案 ─────────

    @Test
    fun `cancelled full percent shows percent not finished label`() {
        assertEquals("100%", formatBookProgressForCard(100f, "reading"))
    }

    @Test
    fun `real finished keeps finished label`() {
        assertEquals("已读完", formatBookProgressForCard(100f, "finished"))
        assertEquals("已读完", formatBookProgressForCard(99.6f, "finished"))
    }

    @Test
    fun `manual finished below threshold keeps numeric label`() {
        // 手动标记读完但进度未到阈值：数字文案维持既有行为（完成标识由书架徽章表达）
        assertEquals("30%", formatBookProgressForCard(30f, "finished"))
    }

    @Test
    fun `ordinary numeric labels unchanged`() {
        assertEquals("未读", formatBookProgressForCard(0f, "reading"))
        assertEquals("未读", formatBookProgressForCard(0.04f, "reading"))
        assertEquals("9.5%", formatBookProgressForCard(9.5f, "reading"))
        assertEquals("45%", formatBookProgressForCard(45f, "reading"))
    }

    private fun progress(percent: Float, state: String) = ReadingProgressEntity(
        book_id = "b1",
        progress_percent = percent,
        completion_state = state,
        updated_at = "2026-01-01T00:00:00Z",
    )
}
