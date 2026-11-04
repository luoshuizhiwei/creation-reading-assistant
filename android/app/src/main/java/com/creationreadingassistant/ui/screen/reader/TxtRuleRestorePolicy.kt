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

/**
 * 规则重扫完成后应落到的 source 偏移（M-BUG-09）。
 *
 * 背景：重扫会重建 `readingUnits`，列表位置失效，因此**必须**重新定位一次；
 * 但旧实现固定跳到「重扫**开始时**捕获的锚点」。大 TXT 重扫耗时长，用户在等待期间
 * 往往已经继续读了很远 —— 重扫一完成就被拽回旧位置，表现为「阅读中途毫无征兆地
 * 回到之前的进度」。
 *
 * 取值规则：**优先用户此刻的真实偏移**。
 * - 用户没动 → 与锚点相同，行为等价；
 * - 用户往后读了 → 停在原处，不回退（正是本次修复的诉求）；
 * - 用户往回翻了 → 保持他所在处，也不往前弹。
 *
 * 锚点只作为兜底：当前偏移不可用（≤ 0，例如列表尚未测量）时才回落到锚点。
 */
internal fun txtRuleRestoreOffset(anchorOffset: Int, currentOffset: Int): Int =
    if (currentOffset > 0) currentOffset else anchorOffset.coerceAtLeast(0)
