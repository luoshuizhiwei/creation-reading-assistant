package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.ui.viewmodel.PendingTxtRuleAnchor
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanResult
import com.creationreadingassistant.ui.viewmodel.canConsumeAnchor
import com.creationreadingassistant.ui.viewmodel.matchesSession

/**
 * TXT 规则重扫成功后 pending anchor 恢复目标的裁决策略（P1-A）。
 *
 * 分页模式走 pagedJumpRequest；滚动模式走 jumpToPlainOffset(pending.offset)。
 * 跨书 / 书内规则身份不匹配 / 无 anchor / 无结果一律返回 null（不消费），
 * 与 ReaderRuntimeEffects 的 LaunchedEffect 解耦，便于 JVM 单测。
 */
internal enum class TxtRuleRestoreTarget {
    PagedJump,
    ScrollJump,
}

internal fun txtRuleRestoreTarget(
    pagerEngineOn: Boolean,
    bookId: String,
    result: TxtRuleScanResult?,
    pending: PendingTxtRuleAnchor?,
): TxtRuleRestoreTarget? {
    if (result == null) return null
    if (!result.matchesSession(bookId)) return null
    if (!result.canConsumeAnchor(pending)) return null
    return if (pagerEngineOn) TxtRuleRestoreTarget.PagedJump else TxtRuleRestoreTarget.ScrollJump
}
