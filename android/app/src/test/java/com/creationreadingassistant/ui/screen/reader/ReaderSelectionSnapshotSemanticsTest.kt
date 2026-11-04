package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.ui.viewmodel.withSelectionCleared
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S07-F2.4：消费者快照的**纯函数**语义（不依赖 ViewModel / Compose）。
 *
 * 三条正交性：
 * - 捕获（[ReaderScreenState.withSelectionCaptured]）只写快照，不动活动选区；
 * - 清活动选区（[withSelectionCleared]）只动活动选区，不动快照；
 * - 释放（[withSelectionSnapshotReleased]）只清快照，不动活动选区。
 *
 * 以及要求 7：保存路径的 locator 必须由**同一份快照**生成（[computeLocatorJsonForSnapshot]），
 * 禁止「文字取快照、坐标取已被清空的实时状态」这种混搭。
 */
class ReaderSelectionSnapshotSemanticsTest {

    private fun seeded(
        text: String = "一期一会",
        rangeStart: Int = 5,
        globalOffset: Int = 100,
        sourceEnd: Int = 120,
    ): ReaderScreenState = ReaderScreenState(
        selectedText = text,
        selectedRangeStart = rangeStart,
        selectedGlobalOffset = globalOffset,
        selectedSourceEnd = sourceEnd,
    )

    // ── 捕获 ─────────────────────────────────────────────────────
    @Test
    fun `capture copies the four fields and leaves the active selection intact`() {
        val captured = seeded().withSelectionCaptured()

        assertEquals(
            ReaderSelectionSnapshot(text = "一期一会", rangeStart = 5, globalOffset = 100, sourceEnd = 120),
            captured.selectionSnapshot,
        )
        assertEquals("捕获不得改动活动选区", "一期一会", captured.selectedText)
        assertEquals(5, captured.selectedRangeStart)
        assertEquals(100, captured.selectedGlobalOffset)
        assertEquals(120, captured.selectedSourceEnd)
    }

    @Test
    fun `capture is atomic across all four fields`() {
        // 半捕获（只带文字、坐标留 -1）会让保存路径写出「有摘录但无位置」的记录。
        val captured = seeded(
            text = "甲",
            rangeStart = 11,
            globalOffset = 222,
            sourceEnd = 333,
        ).withSelectionCaptured()

        val s = captured.selectionSnapshot
        checkNotNull(s)
        assertEquals("甲", s.text)
        assertEquals(11, s.rangeStart)
        assertEquals(222, s.globalOffset)
        assertEquals(333, s.sourceEnd)
    }

    @Test
    fun `capturing a blank selection yields no snapshot`() {
        // 要求 9：无选区打开消费者必须保持「无摘录」，不得复用上一轮快照。
        assertNull(ReaderScreenState().withSelectionCaptured().selectionSnapshot)
        assertNull(
            seeded(text = "   ", rangeStart = -1, globalOffset = -1, sourceEnd = -1)
                .withSelectionCaptured()
                .selectionSnapshot,
        )
    }

    @Test
    fun `capturing again overwrites rather than keeps a stale snapshot`() {
        val first = seeded(text = "甲", rangeStart = 1, globalOffset = 10, sourceEnd = 20).withSelectionCaptured()
        val second = first
            .copy(selectedText = "乙", selectedRangeStart = 2, selectedGlobalOffset = 30, selectedSourceEnd = 40)
            .withSelectionCaptured()

        assertEquals(
            ReaderSelectionSnapshot("乙", 2, 30, 40),
            second.selectionSnapshot,
        )
    }

    // ── 正交性 ──────────────────────────────────────────────────
    @Test
    fun `clearing the active selection never releases the snapshot`() {
        val captured = seeded().withSelectionCaptured()

        val cleared = captured.withSelectionCleared()

        assertEquals(
            "快照必须留住：这正是 F2.4 要修的核心",
            ReaderSelectionSnapshot("一期一会", 5, 100, 120),
            cleared.selectionSnapshot,
        )
        assertEquals("", cleared.selectedText)
        assertEquals(-1, cleared.selectedRangeStart)
        assertEquals(-1, cleared.selectedGlobalOffset)
        assertEquals(-1, cleared.selectedSourceEnd)
    }

    @Test
    fun `releasing the snapshot never touches the active selection`() {
        val captured = seeded().withSelectionCaptured()

        val released = captured.withSelectionSnapshotReleased()

        assertNull(released.selectionSnapshot)
        assertEquals("一期一会", released.selectedText)
        assertEquals(5, released.selectedRangeStart)
        assertEquals(100, released.selectedGlobalOffset)
        assertEquals(120, released.selectedSourceEnd)
    }

    @Test
    fun `releasing twice is idempotent`() {
        val once = seeded().withSelectionCaptured().withSelectionSnapshotReleased()
        assertNull(once.withSelectionSnapshotReleased().selectionSnapshot)
    }

    // ── 消费者分类 ──────────────────────────────────────────────
    @Test
    fun `long lived consumers are classified as consuming selection`() {
        listOf(
            ReaderSheet.RULES,
            ReaderSheet.AI_EXPLAIN,
            ReaderSheet.INSPIRATION,
            ReaderSheet.AI_ASSIST,
        ).forEach {
            assertEquals("$it 必须是选区消费者", true, it.consumesSelection())
        }
    }

    @Test
    fun `sheets that snapshot their input elsewhere are not consumers`() {
        // SEARCH 把词快照进 searchQuery；DICTIONARY 把词快照进 dictionaryWord；
        // 其余根本不读选区。它们若被误判为消费者，就会在打开时清掉正文选区的快照语义。
        listOf(
            ReaderSheet.TOC,
            ReaderSheet.NOTES,
            ReaderSheet.SETTINGS,
            ReaderSheet.PROGRESS,
            ReaderSheet.SEARCH,
            ReaderSheet.BOOK_INFO,
            ReaderSheet.THEME,
            ReaderSheet.DICTIONARY,
        ).forEach {
            assertEquals("$it 不应消费选区", false, it.consumesSelection())
        }
    }

    // ── 要求 7：locator 与文字同源 ───────────────────────────────
    @Test
    fun `snapshot locator is derived from the snapshot not from live state`() {
        // 正文活动选区已被清空（-1），快照仍带真实坐标 ⇒ 必须仍能生成 locator。
        val snapshot = ReaderSelectionSnapshot(text = "一期一会", rangeStart = 5, globalOffset = 100, sourceEnd = 120)

        val forPlain = computeLocatorJsonForSnapshot(
            epubBook = null,
            chapterStartOffsets = emptyList(),
            snapshot = snapshot,
        )
        val fromLiveForPlain = computeLocatorJson(
            epubBook = null,
            selectedGlobalOffset = -1,
            chapterStartOffsets = emptyList(),
            selectedText = "",
            selectedRangeStart = -1,
        )

        checkNotNull(forPlain) { "TXT 快照必须能生成 locator" }
        assertNull("实时状态已清空 ⇒ 不该凭它生成 locator", fromLiveForPlain)
    }

    @Test
    fun `snapshot locator is null when there is no snapshot`() {
        assertNull(
            computeLocatorJsonForSnapshot(
                epubBook = null,
                chapterStartOffsets = emptyList(),
                snapshot = null,
            ),
        )
    }

    @Test
    fun `snapshot locator matches the live locator computed from the same four fields`() {
        // 同一份四字段，快照口径与实时口径必须产出**完全相同**的 locator ——
        // 保证 F2.4 没有悄悄改变持久化坐标契约。
        val snapshot = ReaderSelectionSnapshot(text = "一期一会", rangeStart = 5, globalOffset = 100, sourceEnd = 120)
        val chapterStartOffsets = listOf(0, 50, 150)

        val viaSnapshot = computeLocatorJsonForSnapshot(
            epubBook = null,
            chapterStartOffsets = chapterStartOffsets,
            snapshot = snapshot,
        )
        val viaLive = computeLocatorJson(
            epubBook = null,
            selectedGlobalOffset = snapshot.globalOffset,
            chapterStartOffsets = chapterStartOffsets,
            selectedText = snapshot.text,
            selectedRangeStart = snapshot.rangeStart,
        )

        assertEquals("持久化坐标口径必须一致", viaLive, viaSnapshot)
    }
}
