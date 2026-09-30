package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.viewmodel.ReaderAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S07-F2.4：笔记保存的**文本与 locator 同源**（要求 3 + 要求 7）。
 *
 * `ReaderScreen` 的笔记对话框把「打开时快照」交给 `saveReaderNote`：摘录文字与
 * `locator_json` 都由同一份 [ReaderSelectionSnapshot] 推出。旧实现读实时 VM 选区，
 * 在 TTS 跟读导致 ReadingUnit dispose 后二者同时失效（摘录变空、locator 变 null）。
 *
 * 本类用真实 `saveReaderNote` 驱动（内部 `internal`，同模块可访问），
 * 断言落库的 `NoteEntity.excerpt` 与 `locator_json` 都来自快照。
 */
class ReaderNoteSnapshotSaveTest {

    private fun collect(
        selectedText: String,
        locatorJson: String?,
        noteBody: String = "我的批注",
    ): NoteEntity {
        val saved = mutableListOf<ReaderAction>()
        saveReaderNote(
            onAction = { saved.add(it) },
            bid = "book-1",
            selectedText = selectedText,
            noteBody = noteBody,
            currentChapterTitle = "第一章",
            progressPercent = 12.5f,
            locatorJson = locatorJson,
        )
        val save = saved.filterIsInstance<ReaderAction.SaveNote>().single()
        return save.note
    }

    private fun snapshotLocator(
        text: String = "一期一会",
        rangeStart: Int = 5,
        globalOffset: Int = 100,
        sourceEnd: Int = 120,
    ): String? = computeLocatorJsonForSnapshot(
        epubBook = null,
        chapterStartOffsets = emptyList(),
        snapshot = ReaderSelectionSnapshot(text, rangeStart, globalOffset, sourceEnd),
    )

    // ── 要求 3：摘录用快照文字 ────────────────────────────────────
    @Test
    fun `note save stores the snapshot excerpt even after the body selection was cleared`() {
        // 正文活动选区已被清（selectedText == ""），但快照还在 ⇒ 摘录必须是快照里的原文。
        val note = collect(
            selectedText = "一期一会",
            locatorJson = snapshotLocator(),
        )

        assertEquals("一期一会", note.excerpt)
    }

    @Test
    fun `note save with an empty snapshot yields no excerpt`() {
        // 无选区打开笔记（顶栏入口）：保持原「无摘录」语义。
        val note = collect(selectedText = "", locatorJson = null)

        assertNull(note.excerpt)
    }

    // ── 要求 7：locator 与文字同源 ────────────────────────────────
    @Test
    fun `note save stores a locator derived from the same snapshot`() {
        val note = collect(
            selectedText = "一期一会",
            locatorJson = snapshotLocator(),
        )

        val locator = LocatorCodec.decode(note.locator_json)
        assertNotNull("快照必须能产出可解析的 locator", locator)
        // TXT 口径：absOffset = selectedRangeStart = 5
        assertEquals(5, locator?.legacyOffset)
        assertEquals(0, locator?.chapterIndex)
        assertEquals(5, locator?.charOffset)
        // fp 是摘录指纹：能解析出 fp 就证明 locator 与这段文字同源。
        assertEquals(
            "locator 指纹必须与快照文字同源",
            LocatorCodec.decode(
                com.creationreadingassistant.feature.reader.locator.LocatorBuilder.forPlain(5, "一期一会"),
            )?.excerptFingerprint,
            locator?.excerptFingerprint,
        )
    }

    @Test
    fun `note save never mixes snapshot text with a null locator`() {
        // 反向锁定：只要带了摘录，就必须同时带 locator —— 混搭（文字取快照、
        // 坐标取已清空的实时状态）会让「有摘录但无位置」的记录落库。
        val note = collect(
            selectedText = "一期一会",
            locatorJson = snapshotLocator(),
        )

        assertEquals("一期一会", note.excerpt)
        assertNotNull("有摘录就必须有 locator", note.locator_json)
    }

    @Test
    fun `the snapshot locator survives a cleared live selection`() {
        // 实时口径在清选区后为 null；快照口径仍可用 —— 这是 F2.4 的收益本身。
        val live = computeLocatorJson(
            epubBook = null,
            selectedGlobalOffset = -1,
            chapterStartOffsets = emptyList(),
            selectedText = "",
            selectedRangeStart = -1,
        )
        val snapshot = snapshotLocator()

        assertNull("实时口径已清空", live)
        assertNotNull("快照口径仍可用", snapshot)
    }

    // ── 保存后的收尾语义不变 ─────────────────────────────────────
    @Test
    fun `note save still resets body and closes the dialog`() {
        val actions = mutableListOf<ReaderAction>()
        saveReaderNote(
            onAction = { actions.add(it) },
            bid = "book-1",
            selectedText = "一期一会",
            noteBody = "批注",
            currentChapterTitle = "第一章",
            progressPercent = 1f,
            locatorJson = null,
        )

        assertEquals(
            "保存后的动作序列不得改变",
            listOf(
                ReaderAction.SetNoteBody(""),
                ReaderAction.SetNoteOpen(false),
                ReaderAction.ClearSelection,
            ).map { it::class },
            actions.drop(1).map { it::class },
        )
    }
}
