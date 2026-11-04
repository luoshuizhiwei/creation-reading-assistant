package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.domain.model.EpubChapter
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.ui.screen.reader.tts.TtsEngineHost
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressNavigationTest {

    // ── CRA-EPUB-TEMPORARY-PROGRESS-FIX-v1：临时查阅章导航不得落普通进度 ──────────
    //
    // 直接走**真实生产函数** [goToChapter]（与 ReaderScreen 的导航闭包同一入口），
    // 捕获它实际发出的 [ReaderAction.LoadChapter].persistProgress。

    /** 走真实 [goToChapter]，返回它发出的 LoadChapter 的 persistProgress（无则 null）。 */
    private fun emittedPersistProgress(
        targetChapter: Int,
        pagerEngineOn: Boolean,
        jumpToStart: Boolean,
        temporaryInspection: Boolean,
    ): Boolean? {
        val actions = mutableListOf<ReaderAction>()
        val chapterIndexState = mutableIntStateOf(0)
        goToChapter(
            i = targetChapter,
            epubBook = epubBook(),
            markdownDocument = null,
            pagerEngineOn = pagerEngineOn,
            chapterStartOffsets = listOf(0, 100, 1_000),
            pagedJumpRequest = mutableStateOf(null),
            chapterIndexState = chapterIndexState,
            tts = mockk<TtsEngineHost>(relaxed = true),
            onAction = { actions += it },
            bid = "book-1",
            jumpToStart = jumpToStart,
            temporaryInspection = temporaryInspection,
        )
        // 章号必须真实更新（临时查阅仍要加载正确章节）
        assertEquals("章号必须更新为目标章", targetChapter, chapterIndexState.intValue)
        return actions.filterIsInstance<ReaderAction.LoadChapter>().singleOrNull()?.persistProgress
    }

    @Test
    fun `temporary source-locator cross-chapter navigation does not persist normal progress`() {
        // 临时查阅（滚动模式，source locator 目标章与当前章不同）→ 不落普通进度
        assertFalse(
            "临时查阅跨章不得落普通进度",
            emittedPersistProgress(
                targetChapter = 2, pagerEngineOn = false, jumpToStart = true,
                temporaryInspection = true,
            )!!,
        )
    }

    @Test
    fun `temporary toc chapter jump does not persist normal progress`() {
        // 临时 EPUB 界面主动选目录另一章（分页引擎）→ 同样不落普通进度
        assertFalse(
            "临时查阅目录选章不得落普通进度",
            emittedPersistProgress(
                targetChapter = 1, pagerEngineOn = true, jumpToStart = true,
                temporaryInspection = true,
            )!!,
        )
    }

    @Test
    fun `normal active chapter jump still persists as before`() {
        // 普通阅读主动跳章（目录点击）→ 保持原保存行为（persistProgress=true）
        assertTrue(
            "普通主动跳章必须仍保存",
            emittedPersistProgress(
                targetChapter = 2, pagerEngineOn = false, jumpToStart = true,
                temporaryInspection = false,
            )!!,
        )
    }

    @Test
    fun `paged passive chapter sync keeps its original no-persist rule`() {
        // 分页被动跨章同步（jumpToStart=false）→ 维持原规则：不写章首进度
        assertFalse(
            "分页被动同步必须仍不保存",
            emittedPersistProgress(
                targetChapter = 2, pagerEngineOn = true, jumpToStart = false,
                temporaryInspection = false,
            )!!,
        )
    }

    @Test
    fun `counter-example the temporary flag is the only guard against cross-chapter persist`() {
        // 反例（真实生产函数）：同一 target/pagerEngine/jumpToStart 下，
        // 修复前（无临时标志 = 默认 false）会保存普通进度 → 修复后传 true 才不保存。
        val oldBehaviour = emittedPersistProgress(
            targetChapter = 2, pagerEngineOn = false, jumpToStart = true,
            temporaryInspection = false,
        )
        val fixedBehaviour = emittedPersistProgress(
            targetChapter = 2, pagerEngineOn = false, jumpToStart = true,
            temporaryInspection = true,
        )
        assertNotNull(oldBehaviour)
        assertTrue("旧行为（无临时标志）确实会写普通进度", oldBehaviour!!)
        assertFalse("修复后（临时查阅）不写普通进度", fixedBehaviour!!)
    }


    @Test
    fun `whole book target selects the chapter by character offset`() {
        val starts = listOf(0, 100, 1_000)

        assertEquals(0, chapterIndexForBookOffset(starts, targetOffset = 0, totalChars = 2_000))
        assertEquals(1, chapterIndexForBookOffset(starts, targetOffset = 999, totalChars = 2_000))
        assertEquals(2, chapterIndexForBookOffset(starts, targetOffset = 1_000, totalChars = 2_000))
        assertEquals(2, chapterIndexForBookOffset(starts, targetOffset = 2_000, totalChars = 2_000))
    }

    @Test
    fun `whole book target tolerates missing or incomplete indexes`() {
        assertNull(chapterIndexForBookOffset(emptyList(), targetOffset = 500, totalChars = 2_000))
        assertEquals(0, chapterIndexForBookOffset(listOf(100), targetOffset = 0, totalChars = 2_000))
    }

    @Test
    fun `epub chapter offset maps to the matching rendered text block`() {
        val blocks = listOf(
            DocBlock.Text("abcd"),
            DocBlock.Text("efghij"),
            DocBlock.Text("kl"),
        )

        assertEquals(0, blockIndexForChapterOffset(blocks, inChapter = 0))
        assertEquals(1, blockIndexForChapterOffset(blocks, inChapter = 5))
        assertEquals(2, blockIndexForChapterOffset(blocks, inChapter = 12))
    }

    @Test
    fun `legacy epub book progress uses character weighted chapter mapping`() {
        var chapter = -1

        seekToPercent(
            p = 50f,
            epubBook = epubBook(),
            markdownDocument = null,
            pagerEngineOn = false,
            bookIndex = BookIndex(listOf(0, 100, 1_000), emptyList(), totalChars = 2_000),
            txtStreamingDocument = null,
            plainContent = "",
            pagedJumpRequest = mutableStateOf(null),
            goToChapter = { chapter = it },
            jumpToPlainOffset = { error("EPUB must not use TXT scrolling") },
            jumpToMarkdownOffset = { error("EPUB must not use Markdown scrolling") },
        )

        assertEquals(2, chapter)
    }

    @Test
    fun `legacy epub chapter progress uses epub scrolling rather than txt scrolling`() {
        var epubOffset = -1
        var plainScrolled = false

        seekToChapterPercent(
            p = 50f,
            epubBook = epubBook(),
            markdownDocument = null,
            chapterStartOffsets = listOf(1_000),
            chapterIndex = 0,
            contentText = "x".repeat(100),
            pagerEngineOn = false,
            txtChapters = emptyList(),
            txtChapterIndex = 0,
            plainContent = "",
            pagedJumpRequest = mutableStateOf(null),
            jumpToPlainOffset = { plainScrolled = true },
            jumpToEpubOffset = { epubOffset = it },
            jumpToMarkdownOffset = { error("EPUB must not use Markdown scrolling") },
        )

        assertEquals(1_050, epubOffset)
        assertFalse(plainScrolled)
    }

    @Test
    fun `legacy scrolling does not persist before initial position restoration`() {
        assertFalse(canPersistLegacyScrollPosition(initialPositionPending = true))
        assertTrue(canPersistLegacyScrollPosition(initialPositionPending = false))
    }

    @Test
    fun `epub chapter end follows the final text block coordinate`() {
        val blocks = listOf(
            DocBlock.Text("abcd"),
            DocBlock.Text("efghij"),
            DocBlock.Image("", 0, 0),
            DocBlock.Text("kl"),
        )
        val endOffset = epubChapterEndOffset(
            blocks = blocks,
            blockGlobalOffsets = listOf(100, 105, -1, 112),
            chapterBase = 100,
        )

        assertEquals(114, endOffset)
        assertEquals(
            114,
            visibleScrollOffset(
                firstVisibleItemIndex = 0,
                firstVisibleItemOffset = 0,
                firstVisibleItemSize = 400,
                itemStartOffsets = listOf(100, 105, -1, 112),
                itemLengths = listOf(4, 6, 0, 2),
                endReached = true,
                endOffset = endOffset,
            ),
        )
    }

    private fun epubBook(): EpubBook = EpubBook(
        id = "test",
        title = "test",
        author = null,
        chapters = List(3) { index ->
            EpubChapter("chapter-$index", "chapter-$index.xhtml", "", "")
        },
        localUri = "",
        cachedEpubPath = "",
    )
}
