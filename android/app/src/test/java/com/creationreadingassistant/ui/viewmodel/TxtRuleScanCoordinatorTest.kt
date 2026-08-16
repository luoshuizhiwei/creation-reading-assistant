package com.creationreadingassistant.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-A：TXT 目录规则扫描协调器必须只允许「最新请求」发布结果。
 * 快速换规则、切书或取消后，旧请求的成功/失败结果一律丢弃。
 */
class TxtRuleScanCoordinatorTest {

    @Test
    fun `only the latest request may publish after rule switch`() {
        val coordinator = TxtRuleScanCoordinator()
        val stale = coordinator.begin("book-1", "rule-1")
        val latest = coordinator.begin("book-1", "rule-2")

        assertFalse("旧规则请求不得发布", coordinator.isCurrent("book-1", "rule-1", stale))
        assertTrue("最新请求可以发布", coordinator.isCurrent("book-1", "rule-2", latest))
    }

    @Test
    fun `re-selecting the same rule invalidates previous in-flight request`() {
        val coordinator = TxtRuleScanCoordinator()
        val first = coordinator.begin("book-1", "builtin")
        val second = coordinator.begin("book-1", "builtin")

        assertFalse(coordinator.isCurrent("book-1", "builtin", first))
        assertTrue(coordinator.isCurrent("book-1", "builtin", second))
    }

    @Test
    fun `invalidate drops in-flight requests from a previous book`() {
        val coordinator = TxtRuleScanCoordinator()
        val inFlight = coordinator.begin("book-1", "builtin")
        coordinator.invalidate()

        assertFalse(coordinator.isCurrent("book-1", "builtin", inFlight))
    }

    @Test
    fun `result with mismatched book identity is rejected`() {
        val coordinator = TxtRuleScanCoordinator()
        val requestId = coordinator.begin("book-1", "builtin")

        assertFalse(coordinator.isCurrent("book-2", "builtin", requestId))
    }

    @Test
    fun `invalidate returns the active request being cancelled`() {
        val coordinator = TxtRuleScanCoordinator()
        coordinator.begin("book-1", "rule-2")

        val active = coordinator.invalidate()

        assertEquals("book-1" to "rule-2", active)
    }

    @Test
    fun `invalidate with no active request returns null`() {
        val coordinator = TxtRuleScanCoordinator()

        assertNull(coordinator.invalidate())
    }
}
