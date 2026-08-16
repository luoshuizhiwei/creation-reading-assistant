package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.rules.BuiltinTocRules
import com.creationreadingassistant.feature.reader.rules.RuleMutationResult
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.RuleSnapshot
import com.creationreadingassistant.feature.reader.rules.TocRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-A：规则写入成功后是否重新识别流式 TXT 的裁决策略：
 * 只有「会改变有效目录身份（profile.key）的 Success/Saved」才触发；
 * 替换净化、失败/拒绝、迁移单选、非 TXT、跨书结果一律不触发。
 */
class TxtRuleRescanPolicyTest {

    private val standard = TocRule(
        id = BuiltinTocRules.STANDARD_ID,
        name = "标准",
        pattern = null,
        builtin = true,
        enabled = true,
        scope = RuleScope.GLOBAL,
        position = 0,
    )
    private val numDot = TocRule(
        id = "num-dot",
        name = "数字+标点",
        pattern = null,
        builtin = true,
        enabled = true,
        scope = RuleScope.PER_BOOK,
        position = 1,
    )

    private fun snapshot(bookId: String = "book-1", toc: List<TocRule> = listOf(standard)) =
        RuleSnapshot(
            bookId = bookId,
            tocRules = toc,
            replaceRules = emptyList(),
            effectiveToc = toc.filter { it.enabled },
            effectiveReplace = emptyList(),
        )

    @Test
    fun `successful toc change requires streaming rescan`() {
        val snap = snapshot(toc = listOf(standard, numDot))
        assertTrue(
            TxtRuleRescanPolicy.shouldRescanStreamingTxt(
                mutationResult = RuleMutationResult.Success,
                snapshot = snap,
                bid = "book-1",
                isTxt = true,
                currentTocKey = BuiltinTocRules.STANDARD_ID,
            ),
        )
    }

    @Test
    fun `saved custom toc with changed identity requires rescan`() {
        val custom = TocRule(
            id = "c1",
            name = "自定义",
            pattern = "^甲\\d+$",
            builtin = false,
            enabled = true,
            scope = RuleScope.PER_BOOK,
            position = 10,
        )
        assertTrue(
            TxtRuleRescanPolicy.shouldRescanStreamingTxt(
                mutationResult = RuleMutationResult.Saved("c1"),
                snapshot = snapshot(toc = listOf(standard, custom)),
                bid = "book-1",
                isTxt = true,
                currentTocKey = BuiltinTocRules.STANDARD_ID,
            ),
        )
    }

    @Test
    fun `replace-only command does not require rescan`() {
        assertFalse(
            TxtRuleRescanPolicy.shouldRescanStreamingTxt(
                mutationResult = RuleMutationResult.Success,
                snapshot = snapshot(),
                bid = "book-1",
                isTxt = true,
                currentTocKey = BuiltinTocRules.STANDARD_ID,
            ),
        )
    }

    @Test
    fun `rejected or not-found results never require rescan`() {
        val snap = snapshot(toc = listOf(standard, numDot))
        assertFalse(
            TxtRuleRescanPolicy.shouldRescanStreamingTxt(
                RuleMutationResult.Rejected(emptyList()), snap, "book-1", true, "builtin",
            ),
        )
        assertFalse(
            TxtRuleRescanPolicy.shouldRescanStreamingTxt(
                RuleMutationResult.NotFound, snap, "book-1", true, "builtin",
            ),
        )
    }

    @Test
    fun `migrated single rule selection requires rescan when identity changes`() {
        assertTrue(
            TxtRuleRescanPolicy.shouldRescanStreamingTxt(
                RuleMutationResult.Migrated("num-dot", "num-dot"),
                snapshot(toc = listOf(standard, numDot)),
                "book-1",
                true,
                "builtin",
            ),
        )
        assertFalse(
            "归一化后身份未变化（如再次选中同一规则）不得重扫",
            TxtRuleRescanPolicy.shouldRescanStreamingTxt(
                RuleMutationResult.Migrated("num-dot", "num-dot"),
                snapshot(toc = listOf(standard, numDot)),
                "book-1",
                true,
                "num-dot",
            ),
        )
    }

    @Test
    fun `non txt books never require rescan`() {
        assertFalse(
            TxtRuleRescanPolicy.shouldRescanStreamingTxt(
                RuleMutationResult.Success,
                snapshot(toc = listOf(standard, numDot)),
                "book-1",
                isTxt = false,
                currentTocKey = null,
            ),
        )
    }

    @Test
    fun `snapshot from another book never triggers`() {
        assertFalse(
            TxtRuleRescanPolicy.shouldRescanStreamingTxt(
                RuleMutationResult.Success,
                snapshot(bookId = "book-2", toc = listOf(standard, numDot)),
                bid = "book-1",
                isTxt = true,
                currentTocKey = "builtin",
            ),
        )
    }

    @Test
    fun `no mutation result never triggers`() {
        assertFalse(
            TxtRuleRescanPolicy.shouldRescanStreamingTxt(
                null,
                snapshot(toc = listOf(standard, numDot)),
                "book-1",
                true,
                "builtin",
            ),
        )
    }
}
