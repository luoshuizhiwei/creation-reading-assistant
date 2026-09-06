package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.rules.ReplaceRule
import com.creationreadingassistant.feature.reader.rules.RuleScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结构化坐标契约：EPUB 分页路径经 EpubReplaceProjector 结构保真投影放开（章内真实
 * 坐标 + 既有估算混合空间，持久化 locator 坐标系不变）；Markdown 渲染坐标仍保持关闭。
 */
class StructuredReplacementContractTest {

    @Test
    fun `EPUB paged projection applies while keeping estimated declaration and mixed-space mapping`() {
        val source = EpubChapterSource(
            titles = listOf("chapter"),
            chapterStartOffsets = listOf(0),
            totalChars = 128,
            loadBlocks = {
                listOf(
                    DocBlock.Text("heading", isHeading = true),
                    DocBlock.Text("body", isHeading = false),
                )
            },
        )

        val prepared = preparePagedReplacement(source, "epub", listOf(removeBodyRule()))

        // 坐标声明不变：全书层仍是估算空间；投影映射是章内真实坐标（ChapterBlockOffsetMap）
        assertEquals(ReplacementCoordinateSpace.ESTIMATED, source.replacementCoordinateSpace)
        assertEquals(PagedReplacementAvailability.APPLIED, prepared.availability)
        assertTrue(prepared.source is EpubReplacedChapterSource)
        assertEquals("heading\n", prepared.source.loadChapterText(0))

        val projection = (prepared.source as ProjectedChapterSource).projectionForChapter(0)!!
        // 「body」[8,12) 被删除：display 末位 8 回写 source 仍锚定删除起点
        assertEquals(8, projection.localDisplayToGlobalSource(8))
        assertEquals(0, projection.globalSourceToLocalDisplay(0))
        assertEquals(8, projection.globalSourceToLocalDisplay(8))
        assertEquals(8, projection.globalSourceToLocalDisplay(12))
        // 图片/标题结构原样保留
        assertEquals(1, prepared.source.loadChapter(0).blocks.size)
    }

    @Test
    fun `Markdown source declares canonical display coordinates and keeps markup-derived text unchanged`() {
        val markdownSource = "# Heading\nA **bold** sentence."
        val document = MarkdownDocument(markdownSource)
        val source = MarkdownChapterSource(document)

        val prepared = preparePagedReplacement(source, "markdown", listOf(removeBodyRule()))

        assertEquals(ReplacementCoordinateSpace.CANONICAL_DISPLAY, source.replacementCoordinateSpace)
        assertEquals(PagedReplacementAvailability.NON_SOURCE_COORDINATES, prepared.availability)
        assertEquals(document.text(0), prepared.source.loadChapterText(0))
        assertNotEquals(markdownSource, document.text(0))
    }

    @Test
    fun `Markdown visible offsets can map to source but paged persistence is not source based yet`() {
        val sourceText = "Before **visible** after"
        val chapter = MarkdownParser.parse(sourceText)
        val canonicalStart = chapter.canonicalText.indexOf("visible")
        val sourceStart = sourceText.indexOf("visible")

        assertEquals(sourceStart, chapter.offsetMap.toSource(canonicalStart))
        assertEquals(canonicalStart, chapter.offsetMap.toCanonical(sourceStart))
        assertNotEquals(sourceText.length, chapter.canonicalText.length)
    }

    @Test
    fun `empty rules keep EPUB manageable and Markdown unavailable respectively`() {
        val epub = EpubChapterSource(
            titles = listOf("ch"),
            chapterStartOffsets = listOf(0),
            totalChars = 50,
            loadBlocks = { listOf(DocBlock.Text("body", isHeading = false)) },
        )
        val preparedEpub = preparePagedReplacement(epub, "epub", emptyList())
        assertEquals(
            "EPUB 分页路径结构保真投影已放开：空规则 = 可管理（可新增规则）",
            PagedReplacementAvailability.NO_EFFECTIVE_RULES,
            preparedEpub.availability,
        )
        assertSame("空规则必须原样透传 delegate（零开销快路径）", epub, preparedEpub.source)

        val md = MarkdownChapterSource(MarkdownDocument("# Title\nBody"))
        val preparedMd = preparePagedReplacement(md, "md", emptyList())
        assertEquals(
            "Markdown with empty rules must stay NON_SOURCE_COORDINATES",
            PagedReplacementAvailability.NON_SOURCE_COORDINATES,
            preparedMd.availability,
        )
    }

    @Test
    fun `incomplete scope or estimated lengths source falls back to unprojected delegate`() {
        val estimatedSource = object : PagedChapterSource {
            override val chapterCount: Int = 1
            override val totalChars: Int = 100
            override val replacementCoordinateSpace: ReplacementCoordinateSpace = ReplacementCoordinateSpace.SOURCE
            override val chapterLengthsAreEstimated: Boolean = true
            override fun chapterTitle(index: Int) = "Est"
            override fun chapterStartAbs(index: Int) = 0
            override fun loadChapter(index: Int) = PagedChapterContent("text", emptyList())
        }
        val prepEst = preparePagedReplacement(estimatedSource, "b1", listOf(removeBodyRule()))
        assertEquals(PagedReplacementAvailability.ESTIMATED_COORDINATES, prepEst.availability)

        val incompleteSource = object : PagedChapterSource {
            override val chapterCount: Int = 1
            override val totalChars: Int = 100
            override val replacementCoordinateSpace: ReplacementCoordinateSpace = ReplacementCoordinateSpace.SOURCE
            override val replaceProjectionScopeIsComplete: Boolean = false
            override fun chapterTitle(index: Int) = "Inc"
            override fun chapterStartAbs(index: Int) = 0
            override fun loadChapter(index: Int) = PagedChapterContent("text", emptyList())
        }
        val prepInc = preparePagedReplacement(incompleteSource, "b2", listOf(removeBodyRule()))
        assertEquals(PagedReplacementAvailability.INCOMPLETE_SCOPE, prepInc.availability)
    }

    private fun removeBodyRule() = ReplaceRule(
        id = "replace-body",
        name = "remove body",
        pattern = "body",
        replacement = "",
        enabled = true,
        position = 0,
        scope = RuleScope.GLOBAL,
    )
}
