package com.creationreadingassistant.feature.reader.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterPaginatorImageTest {

    private val em = 100f
    private val ruler = FakeTextRuler(em)
    private val oracle = AnywhereBreakOracle()

    private fun cfg(width: Float = 1_000f, height: Float = 1_000f) = LayoutConfig(
        contentWidthPx = width,
        contentHeightPx = height,
        fontSizePx = em,
    )

    @Test
    fun `pure text block entry is byte-for-byte compatible with paragraph entry`() {
        val paragraphs = listOf(
            LayoutParagraph("第一段正文。".repeat(30), charOffset = 0),
            LayoutParagraph("第二章", BlockRole.HEADING, charOffset = 180),
            LayoutParagraph("第二段正文。".repeat(30), charOffset = 183),
        )
        val legacy = ChapterPaginator.paginate(paragraphs, cfg(), ruler, oracle)
        val blocks = ChapterPaginator.paginateBlocks(
            paragraphs.map(LayoutBlock::Text),
            cfg(),
            ruler,
            oracle,
        )

        assertTrue(legacy.pageStarts.contentEquals(blocks.pageStarts))
        assertEquals(legacy.pages.size, blocks.pages.size)
        legacy.pages.indices.forEach { pageIndex ->
            val a = legacy.pages[pageIndex]
            val b = blocks.pages[pageIndex]
            assertEquals(a.startCharOffset, b.startCharOffset)
            assertEquals(a.endCharOffset, b.endCharOffset)
            assertEquals(a.lines, b.lines)
            assertTrue(a.lineTops.contentEquals(b.lineTops))
            assertTrue(a.lineParaOffsets.contentEquals(b.lineParaOffsets))
            assertTrue(b.images.isEmpty())
        }
    }

    @Test
    fun `image that does not fit moves intact to the next page`() {
        val layout = ChapterPaginator.paginateBlocks(
            listOf(
                LayoutBlock.Text(LayoutParagraph("正文", charOffset = 0)),
                LayoutBlock.Image("image", 400f, 400f, anchorOffset = 2),
            ),
            cfg(height = 500f),
            ruler,
            oracle,
        )

        assertEquals(2, layout.pages.size)
        assertTrue(layout.pages[0].images.isEmpty())
        val imagePage = layout.pages[1]
        assertEquals(2, imagePage.startCharOffset)
        assertEquals(2, imagePage.endCharOffset)
        assertEquals(1, imagePage.images.size)
        assertEquals(400f, imagePage.images.single().width, 0.01f)
        assertEquals(400f, imagePage.images.single().height, 0.01f)
    }

    @Test
    fun `oversized image is clamped by both viewport dimensions`() {
        val layout = ChapterPaginator.paginateBlocks(
            listOf(LayoutBlock.Image("poster", 2_000f, 4_000f, 0)),
            cfg(width = 1_000f, height = 500f),
            ruler,
            oracle,
        )

        val image = layout.pages.single().images.single()
        assertEquals(250f, image.width, 0.01f)
        assertEquals(500f, image.height, 0.01f)
        assertEquals(375f, image.left, 0.01f)
        assertEquals(0f, image.top, 0.01f)
    }

    @Test
    fun `unknown image size uses finite four-by-three placeholder`() {
        val image = ChapterPaginator.paginateBlocks(
            listOf(LayoutBlock.Image("unknown", 0f, 0f, 0)),
            cfg(width = 800f, height = 1_000f),
            ruler,
            oracle,
        ).pages.single().images.single()

        assertEquals(800f, image.width, 0.01f)
        assertEquals(600f, image.height, 0.01f)
        assertFalse(image.width.isNaN())
        assertFalse(image.height.isNaN())
    }

    @Test
    fun `consecutive images paginate without loops and preserve anchors`() {
        val layout = ChapterPaginator.paginateBlocks(
            listOf(
                LayoutBlock.Image("cover", 0f, 0f, 0),
                LayoutBlock.Image("ending", 0f, 0f, 99),
            ),
            cfg(width = 1_000f, height = 1_000f),
            ruler,
            oracle,
        )

        assertEquals(2, layout.pages.size)
        assertTrue(layout.pageStarts.contentEquals(intArrayOf(0, 99)))
        assertEquals("cover", layout.pages[0].images.single().sourceKey)
        assertEquals("ending", layout.pages[1].images.single().sourceKey)
        assertEquals(99, layout.charCount)
    }

    @Test
    fun `duplicate image and text anchors restore to the later text page`() {
        val layout = ChapterPaginator.paginateBlocks(
            listOf(
                LayoutBlock.Image("illustration", 800f, 600f, 0),
                LayoutBlock.Text(LayoutParagraph("正文", charOffset = 0)),
            ),
            cfg(width = 1_000f, height = 600f),
            ruler,
            oracle,
        )

        assertEquals(2, layout.pages.size)
        assertTrue(layout.pageStarts.contentEquals(intArrayOf(0, 0)))
        assertEquals(1, layout.pageIndexFor(0))
    }

    @Test
    fun `heading moves with a following image instead of being orphaned`() {
        val layout = ChapterPaginator.paginateBlocks(
            listOf(
                LayoutBlock.Text(LayoutParagraph("正文", charOffset = 0)),
                LayoutBlock.Text(LayoutParagraph("插图", BlockRole.HEADING, charOffset = 2)),
                LayoutBlock.Image("illustration", 200f, 200f, anchorOffset = 4),
            ),
            cfg(height = 600f),
            ruler,
            oracle,
        )

        assertEquals(2, layout.pages.size)
        assertTrue(layout.pages.first().lines.none { it.role == BlockRole.HEADING })
        assertTrue(layout.pages.last().lines.any { it.role == BlockRole.HEADING })
        assertEquals(1, layout.pages.last().images.size)
    }
}
