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

    // ── M-BUG-09：重扫完成后的落点必须是「用户此刻位置」，不是旧锚点 ──

    @Test
    fun `restore offset follows the user when they read forward during the rescan`() {
        // 大文件重扫耗时长，用户期间读了很远：绝不能把他拽回锚点。
        assertEquals(9_000, txtRuleRestoreOffset(anchorOffset = 1_000, currentOffset = 9_000))
    }

    @Test
    fun `restore offset stays put when the user scrolled backwards`() {
        // 用户往回翻了，就留在他所在处，也不往前弹到锚点。
        assertEquals(400, txtRuleRestoreOffset(anchorOffset = 1_000, currentOffset = 400))
    }

    @Test
    fun `restore offset equals the anchor when the user has not moved`() {
        // 用户没动（等的就是这次重扫）→ 与原行为等价。
        assertEquals(1_000, txtRuleRestoreOffset(anchorOffset = 1_000, currentOffset = 1_000))
    }

    @Test
    fun `restore offset falls back to the anchor when the current offset is unavailable`() {
        // 当前偏移不可用（列表尚未测量等）→ 回落到锚点，保证重建后不会掉到文首。
        assertEquals(1_000, txtRuleRestoreOffset(anchorOffset = 1_000, currentOffset = 0))
    }

    @Test
    fun `restore offset never goes negative`() {
        assertEquals(0, txtRuleRestoreOffset(anchorOffset = -5, currentOffset = 0))
    }
}
