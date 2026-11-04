package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.domain.model.EpubChapter
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.locator.EpubLocatorMapping
import com.creationreadingassistant.feature.reader.pager.EpubChapterSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CRA-TASK48-v3：退出落库裁决的**实时权威位置**定向测试。
 *
 * [temporaryReturnLeaveSourcePosition] 必须与 ReaderProgressEffects 四个 500ms
 * 防抖落库消费者**同口径**：分页取引擎页首偏移、Markdown 取可见渲染单元映射的
 * source 偏移（且只能取 epubListState —— Markdown 布局列表）、纯文本滚动取
 * 持久位置语义。位置未定案（恢复中 / 搜索聚焦未消费 / 越界单元）时返回 null，
 * 调用方据此保守冻结。
 */
class ReaderTemporaryReturnLeavePositionTest {

    private val chapters = listOf(0, 500, 1000)

    private fun epubBook() = EpubBook(
        id = "leave_pos_book",
        title = "leave_pos_book",
        author = null,
        chapters = List(3) { index -> EpubChapter("chapter-$index", "chapter-$index.xhtml", "", "") },
        localUri = "",
        cachedEpubPath = "",
    )

    private fun pagedSource() = EpubChapterSource(
        titles = listOf("c1", "c2", "c3"),
        chapterStartOffsets = chapters,
        totalChars = 1500,
    ) { emptyList() }

    private fun markdownFixture(): Triple<MarkdownDocument, com.creationreadingassistant.feature.reader.doc.MarkdownParser.MarkdownChapter, Int> {
        val doc = MarkdownDocument("# First\n\nFirst paragraph.\n\n# Second\n\nTarget paragraph.")
        val chapter = (doc.blocks(1).single() as DocBlock.Markdown).chapter
        val chapterBase = doc.chapters[1].startOffset
        return Triple(doc, chapter, chapterBase)
    }

    @Test
    fun pendingInitialPositionReturnsUnreadyNull() {
        assertNull(
            temporaryReturnLeaveSourcePosition(
                pagerEngineOn = false,
                pendingInitialPosition = true,
                searchScrollFocusPending = false,
                pagedAbsOffset = 600,
                epubBook = null,
                pagedSource = pagedSource(),
                markdownDocument = null,
                chapterBlocks = emptyList(),
                chapterBase = 0,
                epubListState = LazyListState(0, 0),
                plainListState = LazyListState(0, 0),
                readingUnits = emptyList(),
                txtStreamingDocument = null,
                plainContent = "abc",
                chapterStartOffsets = chapters,
                scrollChapterIndex = 0,
            ),
        )
    }

    @Test
    fun pendingSearchScrollFocusReturnsUnreadyNull() {
        assertNull(
            temporaryReturnLeaveSourcePosition(
                pagerEngineOn = true,
                pendingInitialPosition = false,
                searchScrollFocusPending = true,
                pagedAbsOffset = 600,
                epubBook = null,
                pagedSource = pagedSource(),
                markdownDocument = null,
                chapterBlocks = emptyList(),
                chapterBase = 0,
                epubListState = LazyListState(0, 0),
                plainListState = LazyListState(0, 0),
                readingUnits = emptyList(),
                txtStreamingDocument = null,
                plainContent = "abc",
                chapterStartOffsets = chapters,
                scrollChapterIndex = 0,
            ),
        )
    }

    @Test
    fun pagedTxtPositionUsesEngineOffsetAndFullBookChapterMapping() {
        val position = temporaryReturnLeaveSourcePosition(
            pagerEngineOn = true,
            pendingInitialPosition = false,
            searchScrollFocusPending = false,
            pagedAbsOffset = 600,
            epubBook = null,
            pagedSource = pagedSource(),
            markdownDocument = null,
            chapterBlocks = emptyList(),
            chapterBase = 0,
            epubListState = LazyListState(0, 0),
            plainListState = LazyListState(0, 0),
            readingUnits = emptyList(),
            txtStreamingDocument = null,
            plainContent = "x".repeat(1500),
            chapterStartOffsets = chapters,
            scrollChapterIndex = 0,
        )
        val expected = EpubLocatorMapping.toChapterOffset(600, chapters)
        assertEquals("分页 TXT：位置 = 引擎页首偏移（与翻页防抖落库同口径）", 600, position?.first)
        assertEquals("分页 TXT：章号按全书章起始偏移映射（与翻页防抖落库同口径）", expected.first, position?.second)
    }

    @Test
    fun pagedEpubPositionUsesSourceChapterIndexFor() {
        val position = temporaryReturnLeaveSourcePosition(
            pagerEngineOn = true,
            pendingInitialPosition = false,
            searchScrollFocusPending = false,
            pagedAbsOffset = 600,
            epubBook = epubBook(),
            pagedSource = pagedSource(),
            markdownDocument = null,
            chapterBlocks = emptyList(),
            chapterBase = 0,
            epubListState = LazyListState(0, 0),
            plainListState = LazyListState(0, 0),
            readingUnits = emptyList(),
            txtStreamingDocument = null,
            plainContent = "",
            chapterStartOffsets = chapters,
            scrollChapterIndex = 0,
        )
        // EPUB 分页：章号由 PagedChapterSource.chapterIndexFor（接口默认二分）推导。
        assertEquals("分页 EPUB：位置 = 引擎页首偏移", 600, position?.first)
        assertEquals("分页 EPUB：章号由 source.chapterIndexFor 推导", 1, position?.second)
    }

    @Test
    fun pagedUnresolvedStateReturnsNull() {
        // 引擎尚未上报页首偏移（初始加载 / 跨章瞬间）：位置未定案 → 保守冻结。
        assertNull(
            temporaryReturnLeaveSourcePosition(
                pagerEngineOn = true,
                pendingInitialPosition = false,
                searchScrollFocusPending = false,
                pagedAbsOffset = -1,
                epubBook = null,
                pagedSource = pagedSource(),
                markdownDocument = null,
                chapterBlocks = emptyList(),
                chapterBase = 0,
                epubListState = LazyListState(0, 0),
                plainListState = LazyListState(0, 0),
                readingUnits = emptyList(),
                txtStreamingDocument = null,
                plainContent = "abc",
                chapterStartOffsets = chapters,
                scrollChapterIndex = 0,
            ),
        )
        // 分页开启但缺少 source：无法映射章号 → 同样按未定案处理。
        assertNull(
            temporaryReturnLeaveSourcePosition(
                pagerEngineOn = true,
                pendingInitialPosition = false,
                searchScrollFocusPending = false,
                pagedAbsOffset = 600,
                epubBook = null,
                pagedSource = null,
                markdownDocument = null,
                chapterBlocks = emptyList(),
                chapterBase = 0,
                epubListState = LazyListState(0, 0),
                plainListState = LazyListState(0, 0),
                readingUnits = emptyList(),
                txtStreamingDocument = null,
                plainContent = "abc",
                chapterStartOffsets = chapters,
                scrollChapterIndex = 0,
            ),
        )
    }

    @Test
    fun markdownPositionUsesEpubListVisibleUnitNotPlainListDecoy() {
        val (doc, chapter, chapterBase) = markdownFixture()
        val expected = markdownVisibleSourceOffset(chapter, chapterBase, true, 1, 0f)
        val flattened = MarkdownRenderModel.flatten(chapter)
        assert(expected in flattened[1].canonicalRange)

        val position = temporaryReturnLeaveSourcePosition(
            pagerEngineOn = false,
            pendingInitialPosition = false,
            searchScrollFocusPending = false,
            pagedAbsOffset = -1,
            epubBook = null,
            pagedSource = null,
            markdownDocument = doc,
            chapterBlocks = listOf(DocBlock.Markdown(chapter)),
            chapterBase = chapterBase,
            epubListState = LazyListState(1, 0),
            plainListState = LazyListState(0, 0), // 诱饵：纯文本列表停在 0，Markdown 不得误取
            readingUnits = listOf(ReadingUnit(0, 0, "decoy", 0, 5)),
            txtStreamingDocument = null,
            plainContent = "decoy",
            chapterStartOffsets = chapters,
            scrollChapterIndex = 1,
        )
        assertEquals("Markdown：位置 = 可见渲染单元映射的 canonical source 偏移", expected, position?.first)
        assertEquals("Markdown：章号 = 当前章", 1, position?.second)
    }

    @Test
    fun markdownUnresolvedUnitReturnsNull() {
        val (doc, chapter, chapterBase) = markdownFixture()
        // 越界单元（恢复动画中途 / 列表尚未布局）：映射不可得 → 保守冻结。
        assertNull(
            temporaryReturnLeaveSourcePosition(
                pagerEngineOn = false,
                pendingInitialPosition = false,
                searchScrollFocusPending = false,
                pagedAbsOffset = -1,
                epubBook = null,
                pagedSource = null,
                markdownDocument = doc,
                chapterBlocks = listOf(DocBlock.Markdown(chapter)),
                chapterBase = chapterBase,
                epubListState = LazyListState(99, 0),
                plainListState = LazyListState(0, 0),
                readingUnits = emptyList(),
                txtStreamingDocument = null,
                plainContent = "",
                chapterStartOffsets = chapters,
                scrollChapterIndex = 1,
            ),
        )
        // 无 Markdown 块（章装载未完成）：同样按未定案处理。
        assertNull(
            temporaryReturnLeaveSourcePosition(
                pagerEngineOn = false,
                pendingInitialPosition = false,
                searchScrollFocusPending = false,
                pagedAbsOffset = -1,
                epubBook = null,
                pagedSource = null,
                markdownDocument = doc,
                chapterBlocks = emptyList(),
                chapterBase = chapterBase,
                epubListState = LazyListState(1, 0),
                plainListState = LazyListState(0, 0),
                readingUnits = emptyList(),
                txtStreamingDocument = null,
                plainContent = "",
                chapterStartOffsets = chapters,
                scrollChapterIndex = 1,
            ),
        )
    }

    @Test
    fun plainPositionUsesPersistOffsetSemantics() {
        val units = listOf(
            ReadingUnit(unitIndex = 0, chapterIndex = 0, title = "c1", charStart = 0, charCount = 10),
            ReadingUnit(unitIndex = 1, chapterIndex = 0, title = "c2", charStart = 10, charCount = 10),
        )
        // 裸 LazyListState（无组合环境）canScrollForward=false → 持久位置语义取全书末尾，
        // 正是「书末 clamp」路径（与 TXT 滚动防抖落库同一映射）。
        val position = temporaryReturnLeaveSourcePosition(
            pagerEngineOn = false,
            pendingInitialPosition = false,
            searchScrollFocusPending = false,
            pagedAbsOffset = -1,
            epubBook = null,
            pagedSource = null,
            markdownDocument = null,
            chapterBlocks = emptyList(),
            chapterBase = 0,
            epubListState = LazyListState(0, 0),
            plainListState = LazyListState(1, 0),
            readingUnits = units,
            txtStreamingDocument = null,
            plainContent = "x".repeat(20),
            chapterStartOffsets = chapters,
            scrollChapterIndex = 0,
        )
        assertEquals("纯文本滚动：位置 = 持久位置语义（书末 clamp 取全书字符数）", 20, position?.first)
        assertEquals("纯文本滚动：章号按全书章起始偏移映射", EpubLocatorMapping.toChapterOffset(20, chapters).first, position?.second)
    }
}
