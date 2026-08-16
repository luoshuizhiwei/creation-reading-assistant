package com.creationreadingassistant.ui.viewmodel

import java.util.concurrent.atomic.AtomicLong

/**
 * TXT 目录规则扫描协调器（P1-A）。
 *
 * 为每次扫描请求分配递增 requestId，并记录发起请求的书 + 规则。
 * 只有「当前最新请求」的成功/失败结果允许发布：换规则、切书（[invalidate]）后，
 * 旧请求的迟到结果一律丢弃，防止旧扫描覆盖新书状态或跨书消费 pending anchor。
 */
class TxtRuleScanCoordinator {
    private val generation = AtomicLong(0)

    @Volatile
    private var activeBookId: String? = null

    @Volatile
    private var activeRuleId: String? = null

    /** 发起新请求（旧请求全部失效），返回本次请求 id。 */
    fun begin(bookId: String, ruleId: String): Long {
        activeBookId = bookId
        activeRuleId = ruleId
        return generation.incrementAndGet()
    }

    /**
     * 使所有在途请求失效（切书 / 取消），并返回被失效的当前请求（书 + 规则）；
     * 没有在途请求时返回 null。返回的书 / 规则用于发布 Cancelled 状态。
     */
    fun invalidate(): Pair<String, String>? {
        val active = activeBookId?.let { book -> activeRuleId?.let { rule -> book to rule } }
        generation.incrementAndGet()
        return active
    }

    /** 该请求（书 + 规则 + 代次）是否仍是当前最新请求。 */
    fun isCurrent(bookId: String, ruleId: String, requestId: Long): Boolean =
        requestId == generation.get() && activeBookId == bookId && activeRuleId == ruleId
}
