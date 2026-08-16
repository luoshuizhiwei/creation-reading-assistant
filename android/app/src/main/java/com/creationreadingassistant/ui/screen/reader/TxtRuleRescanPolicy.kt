package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot

/**
 * 规则写入成功后是否需要重新识别当前流式 TXT 的裁决策略（P1-A）。
 *
 * 只有「会改变有效目录身份（RuleSnapshot.effectiveTocProfile.key）的
 * Success / Saved 结果」才触发重扫：
 * - REPLACE 规则命令、启停 REPLACE、保存同内容目录规则等不改变 key → 不触发；
 * - 校验失败 / NotFound / 跨书快照 / 非 TXT → 不触发；
 * - Migrated（快速单选）同样按 key 变化判定：归一化后目录身份变化即重扫。
 */
internal object TxtRuleRescanPolicy {

    fun shouldRescanStreamingTxt(
        mutationResult: RuleMutationResult?,
        snapshot: RuleSnapshot,
        bid: String,
        isTxt: Boolean,
        currentTocKey: String?,
    ): Boolean {
        if (
            mutationResult !is RuleMutationResult.Success &&
            mutationResult !is RuleMutationResult.Saved &&
            mutationResult !is RuleMutationResult.Migrated
        ) {
            return false
        }
        if (!isTxt) return false
        if (snapshot.bookId != bid) return false
        val key = snapshot.effectiveTocProfile.key
        return key != currentTocKey
    }
}
