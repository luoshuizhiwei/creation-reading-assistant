package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.ui.viewmodel.PendingTxtRuleAnchor
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P1-A：规则重扫成功后 pending anchor 的恢复目标按渲染模式路由：
 * 分页 → pagedJumpRequest；滚动 → jumpToPlainOffset；跨书 / 身份不匹配 / 无
 * anchor / 无结果一律不消费。
 */
class TxtRuleRestorePolicyTest {

    private val pending = PendingTxtRuleAnchor(bookId = "book-1", ruleId = "rule-2", offset = 4321)

    private fun result(bookId: String, ruleId: String) = TxtRuleScanResult(
        bookId = bookId,
        ruleId = ruleId,
        requestId = 1L,
    )

    @Test
    fun `paged engine routes anchor restore to paged jump`() {
        assertEquals(
            TxtRuleRestoreTarget.PagedJump,
            txtRuleRestoreTarget(
                pagerEngineOn = true,
                bookId = "book-1",
                result = result("book-1", "rule-2"),
                pending = pending,
            ),
        )
    }

    @Test
    fun `scroll engine routes anchor restore to scroll jump`() {
        assertEquals(
            TxtRuleRestoreTarget.ScrollJump,
            txtRuleRestoreTarget(
                pagerEngineOn = false,
                bookId = "book-1",
                result = result("book-1", "rule-2"),
                pending = pending,
            ),
        )
    }

    @Test
    fun `cross book result is never consumed`() {
        assertNull(
            txtRuleRestoreTarget(
                pagerEngineOn = true,
                bookId = "book-1",
                result = result("book-2", "rule-2"),
                pending = pending,
            ),
        )
        assertNull(
            txtRuleRestoreTarget(
                pagerEngineOn = false,
                bookId = "book-1",
                result = result("book-2", "rule-2"),
                pending = pending,
            ),
        )
    }

    @Test
    fun `rule identity mismatch is never consumed`() {
        assertNull(
            txtRuleRestoreTarget(
                pagerEngineOn = true,
                bookId = "book-1",
                result = result("book-1", "rule-1"),
                pending = pending,
            ),
        )
    }

    @Test
    fun `null pending anchor is never consumed`() {
        assertNull(
            txtRuleRestoreTarget(
                pagerEngineOn = true,
                bookId = "book-1",
                result = result("book-1", "rule-2"),
                pending = null,
            ),
        )
    }

    @Test
    fun `null result is never consumed`() {
        assertNull(
            txtRuleRestoreTarget(
                pagerEngineOn = true,
                bookId = "book-1",
                result = null,
                pending = pending,
            ),
        )
    }
}
