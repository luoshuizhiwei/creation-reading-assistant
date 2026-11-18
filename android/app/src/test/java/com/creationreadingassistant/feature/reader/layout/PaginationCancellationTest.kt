package com.creationreadingassistant.feature.reader.layout

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PaginationCancellationTest {
    private val cfg = LayoutConfig(200f, 200f, fontSizePx = 20f)
    private val ruler = FakeTextRuler(20f)
    private val oracle = AnywhereBreakOracle()

    @Test
    fun `single long paragraph checks cancellation during preparation`() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            LineComposer.layoutParagraph(
                LayoutParagraph("测试正文。".repeat(10000)), cfg, ruler, oracle,
                checkActive = {
                    checks++
                    if (checks == 8) throw CancellationException("test cancellation")
                },
            )
        }
        assertEquals(8, checks)
    }

    @Test
    fun `mixed image pagination stops after initial pages when cancelled`() {
        var cancelled = false
        var initialPages = 0
        val blocks = List(100) {
            LayoutBlock.Image(sourceKey = "test-$it", widthPx = 200f, heightPx = 200f, anchorOffset = it)
        }
        assertThrows(CancellationException::class.java) {
            ChapterPaginator.paginateProgressive(
                blocks, cfg, ruler, oracle,
                checkActive = { if (cancelled) throw CancellationException("test cancellation") },
                onInitialPages = {
                    initialPages = it.pages.size
                    cancelled = true
                },
            )
        }
        assertEquals(2, initialPages)
    }

    @Test
    fun `pre cancelled text pagination does not measure text`() {
        val uncalledRuler = object : TextRuler {
            override val fontSizePx = ruler.fontSizePx
            override val typefaceKey = ruler.typefaceKey

            override fun getTextWidths(text: String, out: FloatArray) = error("must not measure")
        }
        assertThrows(CancellationException::class.java) {
            ChapterPaginator.paginateProgressive(
                listOf(LayoutBlock.Text(LayoutParagraph("测试正文"))), cfg, uncalledRuler, oracle,
                checkActive = { throw CancellationException("already cancelled") },
            )
        }
    }
}
