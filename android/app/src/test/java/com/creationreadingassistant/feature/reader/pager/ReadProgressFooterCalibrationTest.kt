package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.locator.LearningProgressEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 任务90-v1：页脚 `{ReadProgress}` 对 EPUB（估算章长来源）必须消费分页控制器放入
 * [PageInfo] 的校准百分比，而不是 `absOffset * 100 / source.totalChars`。
 *
 * 调用的是真实页脚消费入口 [CustomTipPlaceholder.render]——`PagedReaderFooter`
 * 经 `renderPiece` 逐字调用同一函数；测试不复制页脚公式到 helper 自证。
 *
 * 坐标样本取自 89-v3 run 20261022-165359-e1db9d 的原始 logcat/DB：
 * 估算总量 2862、第二章章首 1791、controller 64.83092、页脚旧值 62.6%。
 */
class ReadProgressFooterCalibrationTest {

    private fun epubEstimatedSource() = FakeSource(
        chapterCount = 2,
        totalChars = 2862,
        estimated = true,
        chapterStarts = listOf(0, 1791),
    )

    private fun nonEstimatedSource(totalChars: Int) = FakeSource(
        chapterCount = 1,
        totalChars = totalChars,
        estimated = false,
        chapterStarts = listOf(0),
    )

    private fun renderFooter(
        source: PagedChapterSource?,
        absOffset: Int,
        pageInfo: PageInfo,
        template: String = "{ReadProgress}",
    ): String = CustomTipPlaceholder.render(
        template = template,
        source = source,
        absOffset = absOffset,
        pageInfo = pageInfo,
        currentTime = "12:00",
        batteryLevel = 80,
        bookName = "测试EPUB-89",
    )

    // ── EPUB：消费 PageInfo 校准值 ───────────────────────────────────────

    @Test
    fun epubFooterUsesCalibratedPercentFromSecondChapterStartSample() {
        // 真实样本：absOffset=1791、估算总量=2862（旧口径 62.6%），controller=64.83092%。
        val rendered = renderFooter(
            source = epubEstimatedSource(),
            absOffset = 1791,
            pageInfo = PageInfo(chapterIndex = 1, pageIndex = 0, pageCount = 2, progressPercent = 64.83092f),
        )
        assertEquals("64.8%", rendered)
        // 明确区分旧实现，防止把混合坐标比例重新写回页脚。
        assertNotEquals("62.6%", rendered)
    }

    @Test
    fun epubFooterUsesControllerPercentOnFirstChapterSecondPageSample() {
        // 真实样本：absOffset=367、估算总量=2862（旧口径 12.8%），controller=35.45894%。
        val rendered = renderFooter(
            source = epubEstimatedSource(),
            absOffset = 367,
            pageInfo = PageInfo(chapterIndex = 0, pageIndex = 1, pageCount = 2, progressPercent = 35.45894f),
        )
        assertEquals("35.5%", rendered)
        assertNotEquals("12.8%", rendered)
    }

    @Test
    fun epubFooterFollowsControllerWithNoMeasuredChapters() {
        // 未测章：控制器用估算章长（校准比 1.0）得到 12.82%，页脚必须显示同一值。
        val controllerPercent = LearningProgressEstimator.percent(
            chapterIndex = 0,
            charOffset = 367,
            estimatedCounts = listOf(1791, 1071),
            actualCounts = emptyMap(),
        )
        assertEquals(12.82f, controllerPercent, 0.01f)
        val rendered = renderFooter(
            source = epubEstimatedSource(),
            absOffset = 367,
            pageInfo = PageInfo(chapterIndex = 0, pageIndex = 1, pageCount = 2, progressPercent = controllerPercent),
        )
        assertEquals("12.8%", rendered)
    }

    @Test
    fun epubFooterFollowsControllerWithMeasuredChapters() {
        // 已实测章：校准比 < 1，控制器 64.8x%，页脚跟随同一口径。
        val controllerPercent = LearningProgressEstimator.percent(
            chapterIndex = 1,
            charOffset = 0,
            estimatedCounts = listOf(1791, 1071),
            actualCounts = mapOf(0 to 1034, 1 to 561),
        )
        assertEquals(64.83f, controllerPercent, 0.01f)
        val rendered = renderFooter(
            source = epubEstimatedSource(),
            absOffset = 1791,
            pageInfo = PageInfo(chapterIndex = 1, pageIndex = 0, pageCount = 2, progressPercent = controllerPercent),
        )
        assertEquals("64.8%", rendered)
    }

    @Test
    fun epubFooterClampsCalibratedPercent() {
        val rendered = renderFooter(
            source = epubEstimatedSource(),
            absOffset = 1791,
            pageInfo = PageInfo(progressPercent = 120f),
        )
        assertEquals("100.0%", rendered)
    }

    // ── TXT / MD：保持原有显示计算 ───────────────────────────────────────

    @Test
    fun nonEstimatedSourceKeepsOffsetRatioForTxt() {
        val rendered = renderFooter(
            source = nonEstimatedSource(1000),
            absOffset = 500,
            pageInfo = PageInfo(progressPercent = 77f),
        )
        assertEquals("50.0%", rendered)
    }

    @Test
    fun nonEstimatedSourceKeepsOffsetRatioForMarkdown() {
        val rendered = renderFooter(
            source = nonEstimatedSource(4000),
            absOffset = 1000,
            pageInfo = PageInfo(progressPercent = 88f),
        )
        assertEquals("25.0%", rendered)
    }

    @Test
    fun nonEstimatedSourceKeepsClampAndZeroTotalBehavior() {
        assertEquals(
            "100.0%",
            renderFooter(nonEstimatedSource(1000), absOffset = 2000, pageInfo = PageInfo(progressPercent = 10f)),
        )
        assertEquals(
            "0.0%",
            renderFooter(nonEstimatedSource(0), absOffset = 10, pageInfo = PageInfo(progressPercent = 90f)),
        )
    }

    @Test
    fun nullSourceKeepsZeroPercent() {
        assertEquals("0.0%", renderFooter(null, absOffset = 0, pageInfo = PageInfo()))
    }

    // ── 定位相关占位符不受影响 ──────────────────────────────────────────

    @Test
    fun estimatedSourceKeepsChapterAndPagePlaceholders() {
        val template = "{ChapterIndex}/{ChapterSize} {PageIndex}/{PageSize} {ReadProgress}"
        val rendered = renderFooter(
            source = epubEstimatedSource(),
            absOffset = 1791,
            pageInfo = PageInfo(chapterIndex = 1, pageIndex = 0, pageCount = 2, progressPercent = 64.83092f),
            template = template,
        )
        assertEquals("2/2 1/2 64.8%", rendered)
    }

    private class FakeSource(
        override val chapterCount: Int,
        override val totalChars: Int,
        val estimated: Boolean,
        private val chapterStarts: List<Int>,
    ) : PagedChapterSource {
        override val chapterLengthsAreEstimated: Boolean get() = estimated
        override fun chapterTitle(index: Int): String = "第${index + 1}章"
        override fun chapterStartAbs(index: Int): Int = chapterStarts.getOrElse(index) { totalChars }
        override fun loadChapter(index: Int): PagedChapterContent = PagedChapterContent("", emptyList())
    }
}
