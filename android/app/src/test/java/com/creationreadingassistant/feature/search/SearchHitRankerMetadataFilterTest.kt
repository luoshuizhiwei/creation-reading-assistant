package com.creationreadingassistant.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R6-S（RDS-ROOT-DECISION-20261106-v1）：正文通道纯元数据命中过滤的定向回归。
 *
 * 原错误链（2026-11-06 真机 CRA_SEARCH_DIAG 事件链原证）：搜「84」，书名「测试MD-84」的
 * 标题 token 经 CHAPTER_TITLE_MULTIPLIER 加权进 chapterAgg（chapterIndex=0、无 offsets），
 * 该书正文 token 是「84v1」（latin run 合并），独立 token 84 只在标题 → pickCoordinate
 * 全组无 span 返回 (0, null) → SearchHit(isMetadataHit=true) 混入 contentHits 渲染成
 * 「正文 · 书名/作者」卡片；点击 resolveLegacyOffset → readerChapterIndex=null →
 * NoReliableSource → 只弹「该结果没有可定位的原文位置」snackbar，永不导航。
 * RDS-102 三轮真机失败（含 Z2 成功坐标）全部命中此链。
 *
 * 修复：rank() 对 (chapterIndex==0 && span==null) 的纯元数据组返回 null（不进正文通道）。
 * 书名命中已有「书籍」tab 专门卡片，正文通道丢弃不丢信息。
 *
 * 锁定行为：
 * 1. 红（修复前语义）：元数据组混入 → 本测试断言被过滤（修复后合同）；
 * 2. 正文命中（chapterIndex>=1 + span）不受影响；
 * 3. 正文预览命中（chapterIndex=0 + span 存在）保留（isPreviewHit 可跳）；
 * 4. 同书同基准既有标题又有正文命中时，取正文坐标保留整行。
 */
class SearchHitRankerMetadataFilterTest {

    private val bookId = "b-r6s-meta-filter"
    private val active = setOf(bookId)

    private fun raw(
        chapterIndex: Int,
        spanStart: Int? = null,
        spanLen: Int? = null,
        hits: Int = 1,
        term: String = "84",
    ) = RawTermMatch(
        bookId = bookId,
        textBasis = SearchTextBasis.ORIGINAL,
        chapterIndex = chapterIndex,
        term = term,
        hits = hits,
        queryTermCount = 1,
        span = if (spanStart != null && spanLen != null) SearchMatchSpan(spanStart, spanLen) else null,
    )

    @Test
    fun metadataOnlyGroupIsFilteredFromContentChannel() {
        // 原 84 场景：标题 token（ch=0、无 span）。修复前 → SearchHit(isMetadataHit) 混入；
        // 修复后 → 正文通道返回空（书名命中归「书籍」tab）。
        val out = SearchHitRanker.rank(
            rawMatches = listOf(raw(chapterIndex = 0)),
            activeBookIds = active,
        )
        assertTrue(
            "纯元数据命中不得出现在正文通道（RDS-102 根因）",
            out.isEmpty(),
        )
    }

    @Test
    fun chapterHitWithSpanIsPreserved() {
        val out = SearchHitRanker.rank(
            rawMatches = listOf(raw(chapterIndex = 2, spanStart = 176, spanLen = 2)),
            activeBookIds = active,
        )
        assertEquals(1, out.size)
        val hit = out.single()
        assertEquals(2, hit.indexChapterIndex)
        assertNotNull(hit.charOffset)
        assertEquals(176, hit.charOffset)
        assertTrue(hit.hasSourcePosition)
        assertNull(hit.coverage) // provider 缺省
    }

    @Test
    fun previewHitWithSpanAtChapterZeroIsPreserved() {
        // 正文预览命中（ch=0 且有 span）：可跳（isPreviewHit），不得被过滤。
        val out = SearchHitRanker.rank(
            rawMatches = listOf(raw(chapterIndex = 0, spanStart = 12, spanLen = 2)),
            activeBookIds = active,
        )
        assertEquals(1, out.size)
        assertTrue(out.single().isPreviewHit)
        assertTrue(out.single().hasSourcePosition == false) // ch=0 无 readerChapter，但可走 preview 解析
        assertEquals(12, out.single().charOffset)
    }

    @Test
    fun mixedTitleAndBodyGroupKeepsBodyCoordinate() {
        // 同书同基准：标题命中（ch0 无 span）+ 正文命中（ch3 有 span）。
        // 修复 v2（pickCoordinate 跳过无 span 组）：标题组不再遮蔽正文组 ——
        // pickCoordinate 选中 ch3 组返回 (3, 703..)，ranker 保留为可跳正文命中。
        // （修复 v1 只在 ranker 侧过滤 (0,null)，导致 ranked=0、正文命中整体丢失——
        // 真机 diag2 原证 rawCount=4 ranked=0。v2 是正确的单点修复。）
        val out = SearchHitRanker.rank(
            rawMatches = listOf(
                raw(chapterIndex = 0, term = "84"),           // 标题 token
                raw(chapterIndex = 3, spanStart = 703, spanLen = 2, term = "84"), // 正文 token
            ),
            activeBookIds = active,
        )
        assertEquals(1, out.size)
        assertEquals(3, out.single().indexChapterIndex)
        assertEquals(703, out.single().charOffset)
        assertTrue(out.single().hasSourcePosition)
    }

    @Test
    fun metadataOnlyGroupStillFilteredWhenNoSpanAnywhere() {
        // 只有标题行（全书正文无该 token）：pickCoordinate 全组无 span → (0,null)
        // → ranker 过滤（v1 行为保留），不产生不可跳的「正文」卡。
        val out = SearchHitRanker.rank(
            rawMatches = listOf(raw(chapterIndex = 0, term = "84")),
            activeBookIds = active,
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun bodyHitInSameChapterAsTitleSurvivesViaSpan() {
        // 标题与正文同章（ch0 是预览通道的形态之外）：章节行含标题 token（无 offsets）
        // 与正文 token（有 offsets）同 chapterAgg —— 写入侧聚合后 offsets 取正文 token 的。
        // pickCoordinate 组内 mapNotNull span → 有 span 就返回 → 保留。
        val out = SearchHitRanker.rank(
            rawMatches = listOf(
                raw(chapterIndex = 1, term = "84"),                      // 章标题 token（无 span）
                raw(chapterIndex = 1, spanStart = 54, spanLen = 2, term = "84"), // 正文 token
            ),
            activeBookIds = active,
        )
        assertEquals(1, out.size)
        assertEquals(1, out.single().indexChapterIndex)
        assertEquals(54, out.single().charOffset)
        assertTrue(out.single().hasSourcePosition)
    }
}
