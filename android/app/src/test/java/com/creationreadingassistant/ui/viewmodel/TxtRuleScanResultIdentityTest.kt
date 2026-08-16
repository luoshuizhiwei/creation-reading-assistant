package com.creationreadingassistant.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-A：扫描结果身份与 pending anchor 消费必须绑定到「书 + 规则」，
 * 禁止跨书 / 跨请求消费（旧扫描不能覆盖新书状态、不能跳到另一本书的锚点）。
 */
class TxtRuleScanResultIdentityTest {

    private fun result(bookId: String, ruleId: String) = TxtRuleScanResult(
        bookId = bookId,
        ruleId = ruleId,
        requestId = 1L,
    )

    @Test
    fun `result only matches its own book session`() {
        assertTrue(result("book-1", "builtin").matchesSession("book-1"))
        assertFalse(result("book-1", "builtin").matchesSession("book-2"))
    }

    @Test
    fun `pending anchor is consumed only by matching book and rule`() {
        val anchor = PendingTxtRuleAnchor(bookId = "book-1", ruleId = "rule-2", offset = 1234)

        assertTrue(result("book-1", "rule-2").canConsumeAnchor(anchor))
        assertFalse("跨书不得消费", result("book-2", "rule-2").canConsumeAnchor(anchor))
        assertFalse("跨规则不得消费", result("book-1", "rule-1").canConsumeAnchor(anchor))
        assertFalse("空 anchor 不得消费", result("book-1", "rule-2").canConsumeAnchor(null))
    }

    @Test
    fun `scan status matches only its own book session`() {
        assertTrue(TxtRuleScanStatus.Running("book-1", "rule-1", 0.5f).matchesSession("book-1"))
        assertFalse(TxtRuleScanStatus.Running("book-1", "rule-1", 0.5f).matchesSession("book-2"))

        assertTrue(TxtRuleScanStatus.Completed("book-1", "rule-1", 3).matchesSession("book-1"))
        assertFalse(TxtRuleScanStatus.Completed("book-1", "rule-1", 3).matchesSession("book-2"))

        assertTrue(TxtRuleScanStatus.Cancelled("book-1", "rule-1").matchesSession("book-1"))
        assertFalse(TxtRuleScanStatus.Cancelled("book-1", "rule-1").matchesSession("book-2"))

        assertTrue(TxtRuleScanStatus.Failed("book-1", "rule-1", "磁盘错误").matchesSession("book-1"))
        assertFalse(TxtRuleScanStatus.Failed("book-1", "rule-1", "磁盘错误").matchesSession("book-2"))
    }
}
