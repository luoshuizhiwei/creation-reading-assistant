package com.creationreadingassistant.ui.screen.reader.sheets

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 69-v4-B：导出回执（真实生产对象 [ReaderExportReceipts]）的定向回归。
 *
 * 只验证回执语义：系统返回的 URI 与 authority 被原样记录、clear 后不会被旧值复用、
 * 新结果覆盖旧结果；写入失败保留原始异常；取消、失败、成功三者互斥。
 * 选择器/写盘/权限属于设备行为，不在本测试范围内冒充。
 */
class ReaderExportReceiptsTest {

    @After
    fun tearDown() {
        ReaderExportReceipts.clear()
    }

    private fun uriWithAuthority(authority: String?): Uri =
        mockk<Uri>().also { every { it.authority } returns authority }

    @Test
    fun `records the actual uri and provider authority`() {
        ReaderExportReceipts.clear()
        val uri = uriWithAuthority("com.android.providers.downloads.documents")
        val before = System.currentTimeMillis()

        ReaderExportReceipts.record(uri)

        val receipt = ReaderExportReceipts.latest()
        assertNotNull("系统返回 URI 后必须留下回执", receipt)
        assertEquals(uri, receipt!!.uri)
        assertEquals("com.android.providers.downloads.documents", receipt.authority)
        assertTrue("完成时间必须是本次记录时刻", receipt.completedAtMillis >= before)
    }

    @Test
    fun `clear prevents a stale uri from being reused by the next export`() {
        ReaderExportReceipts.record(uriWithAuthority("provider.a"))
        ReaderExportReceipts.clear()

        assertNull("上一轮导出回执不得被下一轮复用", ReaderExportReceipts.latest())
    }

    @Test
    fun `a newer export replaces the previous receipt`() {
        ReaderExportReceipts.record(uriWithAuthority("provider.a"))
        val latestUri = uriWithAuthority("provider.b")

        ReaderExportReceipts.record(latestUri)

        assertEquals(latestUri, ReaderExportReceipts.latest()!!.uri)
        assertEquals("provider.b", ReaderExportReceipts.latest()!!.authority)
    }

    @Test
    fun `write failure keeps the original error and is not a success`() {
        ReaderExportReceipts.record(uriWithAuthority("provider.a"))
        val cause = IllegalStateException("无法写入目标文件")

        ReaderExportReceipts.recordFailure(cause)

        val failure = ReaderExportReceipts.latestFailure()
        assertNotNull("写入失败必须留下回执", failure)
        assertEquals("无法写入目标文件", failure!!.message)
        assertSame("回执必须保留原始异常对象", cause, failure.cause)
        assertNull("失败不得同时被读成成功 URI", ReaderExportReceipts.latest())
        assertFalse("失败不得同时被读成取消", ReaderExportReceipts.wasCancelled())
    }

    @Test
    fun `cancellation is distinct from success and failure`() {
        ReaderExportReceipts.recordFailure(IllegalStateException("previous"))

        ReaderExportReceipts.recordCancelled()

        assertTrue("取消必须可被单独识别", ReaderExportReceipts.wasCancelled())
        assertNull("取消不得同时是成功", ReaderExportReceipts.latest())
        assertNull("取消不得同时是失败", ReaderExportReceipts.latestFailure())
    }

    @Test
    fun `a later success clears an earlier failure`() {
        ReaderExportReceipts.recordFailure(IllegalStateException("previous"))
        val uri = uriWithAuthority("com.android.providers.downloads.documents")

        ReaderExportReceipts.record(uri)

        assertEquals(uri, ReaderExportReceipts.latest()!!.uri)
        assertNull("成功必须清掉旧失败", ReaderExportReceipts.latestFailure())
        assertFalse("成功必须清掉旧取消", ReaderExportReceipts.wasCancelled())
    }

    @Test
    fun `clear resets success failure and cancellation`() {
        ReaderExportReceipts.recordCancelled()
        ReaderExportReceipts.recordFailure(IllegalStateException("previous"))
        ReaderExportReceipts.record(uriWithAuthority("provider.a"))

        ReaderExportReceipts.clear()

        assertNull(ReaderExportReceipts.latest())
        assertNull(ReaderExportReceipts.latestFailure())
        assertFalse(ReaderExportReceipts.wasCancelled())
    }
}
