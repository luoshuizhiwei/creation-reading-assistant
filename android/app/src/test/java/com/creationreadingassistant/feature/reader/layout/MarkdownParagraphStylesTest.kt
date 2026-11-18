package com.creationreadingassistant.feature.reader.layout

import com.creationreadingassistant.feature.reader.doc.MdInlineKind
import com.creationreadingassistant.feature.reader.doc.MdInlineSpan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParagraphStylesTest {
    @Test
    fun `wrapped lines keep paragraph coordinates and overlapping style bits`() {
        val spans = listOf(
            MdInlineSpan(0, 1, MdInlineKind.EMPHASIS),
            MdInlineSpan(1, 6, MdInlineKind.STRONG),
            MdInlineSpan(2, 3, MdInlineKind.STRONG),
            MdInlineSpan(4, 5, MdInlineKind.LINK),
        )
        val lines = LineComposer.layoutParagraph(
            LayoutParagraph("甲乙丙丁戊己庚辛", inlineSpans = spans),
            LayoutConfig(60f, 200f, fontSizePx = 20f, firstLineIndentEm = 0f),
            FakeTextRuler(20f),
            AnywhereBreakOracle(),
        )
        assertTrue(lines.size > 1)
        val expected = intArrayOf(
            MarkdownStyleMap.ITALIC, MarkdownStyleMap.BOLD, MarkdownStyleMap.BOLD,
            MarkdownStyleMap.BOLD, MarkdownStyleMap.BOLD or MarkdownStyleMap.LINK,
            MarkdownStyleMap.BOLD, 0, 0,
        )
        for (line in lines) {
            val masks = IntArray(line.clusterCount + 1) { i ->
                if (i < line.clusterCount) expected[line.clusterStarts[i]] else 0
            }
            assertArrayEquals(masks, line.clusterStyles)
        }
    }
}
