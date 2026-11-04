package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.lazy.LazyListState
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.MarkdownRenderModel
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * persistCurrentProgress 三模式组合的定向回归（CRA-UNIFIED-CANDIDATE-INTEGRATION-v1）。
 *
 * 合并合同：绝对偏移分支顺序为 分页 → Markdown canonical（候选）→ TXT 实时列表偏移（D）；
 * Markdown 取不到 canonical 偏移时不落库（候选 `?: return` 语义）；初始定位未完成不落库。
 * 全部断言通过真实函数入口 + 捕获的 onAction 持久结果（ReaderAction）验证，
 * 不做源码关键词检查。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderProgressActionsPersistTest {

    private val bookId = "b_persist"

    private fun loaded() = mockk<ReaderLoadedBook>(relaxed = true)

    @Test
    fun `txt on-leave persists the live list offset instead of the stale visible offset`() = runTest {
        val units = listOf(
            ReadingUnit(unitIndex = 0, chapterIndex = 0, title = "c", charStart = 0, charCount = 10),
            ReadingUnit(unitIndex = 1, chapterIndex = 0, title = "c", charStart = 10, charCount = 10),
        )
        // 列表当前视口停在第 2 个单元：实时偏移 = unit.charStart = 10（无可见条目信息 → 单元起点）。
        val plainListState = LazyListState(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0)
        var saved: ReaderAction? = null
        persistCurrentProgress(
            bid = bookId,
            loadedBook = loaded(),
            error = null,
            pendingInitialPosition = false,
            epubBook = null,
            pagerEngineOn = false,
            pagedAbsOffset = -1,
            pagedSource = null,
            chapterIndex = 0,
            epubListState = LazyListState(0, 0),
            blockGlobalOffsets = emptyList(),
            chapterBase = 0,
            chapterBlocks = emptyList(),
            bookIndex = null,
            chapterStartOffsets = listOf(0),
            visiblePlainOffset = 999, // 旧组合时偏移是过期值——合并后不得再被使用
            markdownDocument = null,
            txtStreamingDocument = null,
            plainContent = "x".repeat(20),
            plainListState = plainListState,
            readingUnits = units,
        ) { saved = it }

        val save = saved as? ReaderAction.SaveProgress
        assertTrue("TXT 离开保存必须走 SaveProgress，实际=$saved", save != null)
        val locator = LocatorCodec.locatorFromProgress(save!!.progress.current_location_json)
        assertEquals("实时列表偏移（第 2 单元起点）必须落库", 10, locator?.charOffset)
        assertEquals(10, locator?.legacyOffset)
        assertEquals(50f, save.progress.progress_percent, 0.01f) // 10/20
    }

    @Test
    fun `markdown on-leave persists the canonical source offset and never the txt list offset`() = runTest {
        val doc = MarkdownDocument("# First\n\nFirst paragraph.\n\n# Second\n\nTarget paragraph.")
        val markdownChapter = (doc.blocks(1).single() as DocBlock.Markdown).chapter
        val chapterBase = doc.chapters[1].startOffset
        val expected = markdownVisibleSourceOffset(markdownChapter, chapterBase, true, 1, 0f)!!
        val flattened = MarkdownRenderModel.flatten(markdownChapter)
        assertTrue("前置：目标单元 canonical 区间必须存在", !flattened[1].canonicalRange.isEmpty())

        var saved: ReaderAction? = null
        persistCurrentProgress(
            bid = bookId,
            loadedBook = loaded(),
            error = null,
            pendingInitialPosition = false,
            epubBook = null,
            pagerEngineOn = false,
            pagedAbsOffset = -1,
            pagedSource = null,
            chapterIndex = 1,
            epubListState = LazyListState(1, 0),
            blockGlobalOffsets = emptyList(),
            chapterBase = chapterBase,
            chapterBlocks = listOf(DocBlock.Markdown(markdownChapter)),
            bookIndex = null,
            chapterStartOffsets = emptyList(),
            visiblePlainOffset = 999,
            markdownDocument = doc,
            txtStreamingDocument = null,
            plainContent = "",
            plainListState = LazyListState(0, 0), // 诱饵：TXT 列表偏移为 0，Markdown 不得误取
            readingUnits = listOf(ReadingUnit(0, 0, "decoy", 0, 5)),
        ) { saved = it }

        val save = saved as? ReaderAction.SaveProgress
        assertTrue("Markdown 离开保存必须走 SaveProgress，实际=$saved", save != null)
        val locator = LocatorCodec.locatorFromProgress(save!!.progress.current_location_json)
        assertEquals("Markdown 必须写 canonical source 偏移（与 markdownVisibleSourceOffset 同一口径）",
            expected, locator?.charOffset)
        assertTrue("canonical 偏移必须落在该单元 canonical 区间内", expected in flattened[1].canonicalRange)
        assertTrue("不得取 TXT 列表诱饵偏移 0", locator?.charOffset != 0)
        assertTrue("不得取过期可见偏移 999", locator?.charOffset != 999)
    }

    @Test
    fun `markdown missing canonical offset skips the save instead of guessing`() = runTest {
        val doc = MarkdownDocument("# First\n\nFirst paragraph.")
        val markdownChapter = (doc.blocks(0).single() as DocBlock.Markdown).chapter
        var saved: ReaderAction? = null
        persistCurrentProgress(
            bid = bookId,
            loadedBook = loaded(),
            error = null,
            pendingInitialPosition = false,
            epubBook = null,
            pagerEngineOn = false,
            pagedAbsOffset = -1,
            pagedSource = null,
            chapterIndex = 0,
            epubListState = LazyListState(99, 0), // 越界单元 → markdownVisibleSourceOffset 返回 null
            blockGlobalOffsets = emptyList(),
            chapterBase = 0,
            chapterBlocks = listOf(DocBlock.Markdown(markdownChapter)),
            bookIndex = null,
            chapterStartOffsets = emptyList(),
            visiblePlainOffset = 7,
            markdownDocument = doc,
            txtStreamingDocument = null,
            plainContent = "",
        ) { saved = it }
        assertNull("候选 `?: return` 语义：canonical 取不到时必须整体不落库", saved)
    }

    @Test
    fun `initial position still pending means no save at all`() = runTest {
        var calls = 0
        persistCurrentProgress(
            bid = bookId,
            loadedBook = loaded(),
            error = null,
            pendingInitialPosition = true,
            epubBook = null,
            pagerEngineOn = false,
            pagedAbsOffset = -1,
            pagedSource = null,
            chapterIndex = 0,
            epubListState = LazyListState(0, 0),
            blockGlobalOffsets = emptyList(),
            chapterBase = 0,
            chapterBlocks = emptyList(),
            bookIndex = null,
            chapterStartOffsets = emptyList(),
            visiblePlainOffset = 5,
            markdownDocument = null,
            txtStreamingDocument = null,
            plainContent = "abc",
            plainListState = LazyListState(0, 0),
            readingUnits = listOf(ReadingUnit(0, 0, "c", 0, 3)),
        ) { calls++ }
        assertEquals("初始定位未完成时不得落库", 0, calls)
    }

    @Test
    fun `paged path persists the paged absolute offset`() = runTest {
        var saved: ReaderAction? = null
        persistCurrentProgress(
            bid = bookId,
            loadedBook = loaded(),
            error = null,
            pendingInitialPosition = false,
            epubBook = null,
            pagerEngineOn = true,
            pagedAbsOffset = 123,
            pagedSource = null,
            chapterIndex = 0,
            epubListState = LazyListState(0, 0),
            blockGlobalOffsets = emptyList(),
            chapterBase = 0,
            chapterBlocks = emptyList(),
            bookIndex = null,
            chapterStartOffsets = listOf(0),
            visiblePlainOffset = 999,
            markdownDocument = null,
            txtStreamingDocument = null,
            plainContent = "x".repeat(300),
            plainListState = null,
            readingUnits = emptyList(),
        ) { saved = it }
        val save = saved as? ReaderAction.SaveProgress
        assertTrue(save != null)
        val locator = LocatorCodec.locatorFromProgress(save!!.progress.current_location_json)
        assertEquals("分页绝对偏移必须原样落库", 123, locator?.charOffset)
    }
}
