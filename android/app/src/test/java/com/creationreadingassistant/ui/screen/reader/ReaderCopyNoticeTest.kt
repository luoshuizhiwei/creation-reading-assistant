package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「复制」动作的用户提示必须与剪贴板真实写入结果一致（缺陷 B）。
 *
 * 原实现丢弃 `Clipboard.copyText()` 的 Boolean 返回值并无条件提示「已复制」：
 * 空白选区或系统剪贴板异常时内容根本没写进去，用户却收到成功反馈。
 * 这里只测那条窄的提示决策函数，避免为了验证一个字符串而挂载整个阅读页面。
 */
class ReaderCopyNoticeTest {

    @Test
    fun `a successful clipboard write reports success`() {
        // 成功提示是用户可见契约，锁字面量。
        assertEquals("已复制", readerCopyNotice(succeeded = true))
    }

    @Test
    fun `a failed clipboard write never claims success`() {
        val notice = readerCopyNotice(succeeded = false)

        assertFalse("写入失败时不得再提示「已复制」", notice == "已复制")
        assertTrue("失败必须给出非空提示，不能静默", notice.isNotBlank())
    }

    @Test
    fun `success and failure notices are distinguishable`() {
        // 两种结果必须产生不同文案；相同的话用户无法区分是否真的复制成功。
        assertFalse(readerCopyNotice(succeeded = true) == readerCopyNotice(succeeded = false))
    }
}
