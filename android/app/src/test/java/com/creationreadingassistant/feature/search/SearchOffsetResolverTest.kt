package com.creationreadingassistant.feature.search

import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 命中 → 全书原文偏移的纯决策契约测试（R2-S1.4 / S1.6 验证缺口收口）。
 *
 * 这套分支的取数在 [com.creationreadingassistant.data.repository.SearchIndexRepository.resolveLegacyOffset]
 * 里（依赖 Room / 磁盘），但**坐标判定**被抽到这里做纯函数，必须逐条钉死：
 * 这里算错**不会崩溃**，只会让用户静默跳到错位置 —— 正是 S1.4 声明的最大验证缺口。
 */
class SearchOffsetResolverTest {

    private fun rule(pattern: String, replacement: String, enabled: Boolean = true) = ReplaceRule(
        id = "r-$pattern",
        name = "rule for $pattern",
        pattern = pattern,
        replacement = replacement,
        enabled = enabled,
        position = 0,
        scope = RuleScope.PER_BOOK,
    )

    @Test
    fun `original chapter hit adds chapter start offset`() {
        val result = SearchOffsetResolver.resolve(
            charOffset = 868,
            isPreviewHit = false,
            isTxtLike = true,
            isDisplayBasis = false,
            readerChapterIndex = 163,
            sourceText = "任意章正文",
            chapterStart = 100_000,
            effectiveRules = emptyList(),
        )
        assertEquals(100_868, result)
    }

    @Test
    fun `original preview hit keeps offset as global offset`() {
        // 预览命中（索引章号 0）的偏移本身就是全书偏移，不再叠加章起始。
        val result = SearchOffsetResolver.resolve(
            charOffset = 1_500,
            isPreviewHit = true,
            isTxtLike = true,
            isDisplayBasis = false,
            readerChapterIndex = null,
            sourceText = "正文预览串",
            chapterStart = 0,
            effectiveRules = emptyList(),
        )
        assertEquals(1_500, result)
    }

    @Test
    fun `epub preview hit is not precisely resolvable`() {
        // EPUB 导入不写 reader_preview；非 TXT/MD 一律不精确到达。
        val result = SearchOffsetResolver.resolve(
            charOffset = 10,
            isPreviewHit = true,
            isTxtLike = false,
            isDisplayBasis = false,
            readerChapterIndex = null,
            sourceText = "whatever",
            chapterStart = 0,
            effectiveRules = emptyList(),
        )
        assertNull(result)
    }

    @Test
    fun `display chapter hit maps offset back through rule replay then adds chapter start`() {
        // 原文「世界好美」，删除「好」→ 显示文「世界美」。
        // 显示文偏移 2（「美」）经 `toSource` 按 **floor 语义**回到删除起点 source 2
        // （TextOffsetMap 既有约定：删除坍缩点的 displayToSource 回到删除起点，
        //  而不是「美」自身所在的 source 3），再叠加章起始 1000 → 1002。
        // 这与阅读器渲染同源（同一 offsetMap），不是本函数引入的偏差。
        val source = "世界好美"
        val result = SearchOffsetResolver.resolve(
            charOffset = 2,
            isPreviewHit = false,
            isTxtLike = true,
            isDisplayBasis = true,
            readerChapterIndex = 0,
            sourceText = source,
            chapterStart = 1_000,
            effectiveRules = listOf(rule("好", "")),
        )
        assertEquals(1_002, result)
    }

    @Test
    fun `display chapter hit with equal length replacement keeps offset aligned`() {
        // 等长替换（山峰→山巅）逐字对齐：显示文偏移 == 原文章内偏移，叠加章起始后精确还原。
        val result = SearchOffsetResolver.resolve(
            charOffset = 5,
            isPreviewHit = false,
            isTxtLike = true,
            isDisplayBasis = true,
            readerChapterIndex = 0,
            sourceText = "云雾缭绕的山峰",
            chapterStart = 1_000,
            effectiveRules = listOf(rule("山峰", "山巅")),
        )
        assertEquals(1_005, result)
    }

    @Test
    fun `display chapter hit with no effective rules is offset identity`() {
        val result = SearchOffsetResolver.resolve(
            charOffset = 5,
            isPreviewHit = false,
            isTxtLike = true,
            isDisplayBasis = true,
            readerChapterIndex = 0,
            sourceText = "云雾缭绕的山峰",
            chapterStart = 0,
            effectiveRules = emptyList(),
        )
        assertEquals(5, result)
    }

    @Test
    fun `display preview hit maps within the preview text`() {
        // 预览命中不回加章起始（preview 即正文前缀）；显示文偏移 2（「美」）按 floor 回到 2。
        val result = SearchOffsetResolver.resolve(
            charOffset = 2,
            isPreviewHit = true,
            isTxtLike = true,
            isDisplayBasis = true,
            readerChapterIndex = null,
            sourceText = "世界好美",
            chapterStart = 0,
            effectiveRules = listOf(rule("好", "")),
        )
        assertEquals(2, result)
    }

    @Test
    fun `bad regex degrades to null instead of fabricating an offset`() {
        val result = SearchOffsetResolver.resolve(
            charOffset = 1,
            isPreviewHit = false,
            isTxtLike = true,
            isDisplayBasis = true,
            readerChapterIndex = 0,
            sourceText = "任意正文",
            chapterStart = 10,
            effectiveRules = listOf(rule("[[", "")),
        )
        assertNull(result)
    }

    @Test
    fun `null or negative char offset yields null`() {
        assertNull(
            SearchOffsetResolver.resolve(
                charOffset = null,
                isPreviewHit = false,
                isTxtLike = true,
                isDisplayBasis = false,
                readerChapterIndex = 0,
                sourceText = "x",
                chapterStart = 0,
                effectiveRules = emptyList(),
            ),
        )
        assertNull(
            SearchOffsetResolver.resolve(
                charOffset = -1,
                isPreviewHit = false,
                isTxtLike = true,
                isDisplayBasis = false,
                readerChapterIndex = 0,
                sourceText = "x",
                chapterStart = 0,
                effectiveRules = emptyList(),
            ),
        )
    }

    @Test
    fun `chapter hit without readable source chapter yields null`() {
        assertNull(
            SearchOffsetResolver.resolve(
                charOffset = 3,
                isPreviewHit = false,
                isTxtLike = true,
                isDisplayBasis = false,
                readerChapterIndex = 0,
                sourceText = null,
                chapterStart = 0,
                effectiveRules = emptyList(),
            ),
        )
    }

    @Test
    fun `chapter hit without chapter index yields null`() {
        assertNull(
            SearchOffsetResolver.resolve(
                charOffset = 3,
                isPreviewHit = false,
                isTxtLike = true,
                isDisplayBasis = false,
                readerChapterIndex = null,
                sourceText = "x",
                chapterStart = 0,
                effectiveRules = emptyList(),
            ),
        )
    }
}
