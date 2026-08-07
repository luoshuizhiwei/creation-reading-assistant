package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * NoteRepository 单测：高亮 / 笔记 / 灵感的写入与转换落库细节，
 * 承接自 ReaderViewModel 分层收敛前的内联断言。
 */
class NoteRepositoryTest {

    private lateinit var highlightDao: HighlightDao
    private lateinit var noteDao: NoteDao
    private lateinit var inspirationDao: InspirationDao
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        highlightDao = mockk(relaxed = true)
        noteDao = mockk(relaxed = true)
        inspirationDao = mockk(relaxed = true)
        repository = NoteRepository(highlightDao, noteDao, inspirationDao)
    }

    private fun highlight(id: String = "h1", text: String = "原文摘录", note: String? = null) =
        HighlightEntity(
            id = id,
            book_id = "b1",
            text = text,
            color = "#ff0",
            note = note,
            chapter_title = "第三章",
            progress_percent = 0.4f,
            created_at = "2026-08-01T10:00:00Z",
            updated_at = "2026-08-01T10:00:00Z",
        )

    @Test
    fun `observe by book delegates to daos`() = runTest {
        every { highlightDao.observeByBook("b1") } returns flowOf(listOf(highlight()))
        every { noteDao.observeByBook("b1") } returns flowOf(emptyList())
        every { inspirationDao.observeByBook("b1") } returns flowOf(emptyList())

        assertEquals(1, repository.observeHighlightsByBook("b1").first().size)
        assertEquals(0, repository.observeNotesByBook("b1").first().size)
        assertEquals(0, repository.observeInspirationsByBook("b1").first().size)
    }

    @Test
    fun `deleteHighlight soft deletes and missing id is no-op`() = runTest {
        coEvery { highlightDao.getById("h1") } returns highlight()
        coEvery { highlightDao.getById("missing") } returns null

        repository.deleteHighlight("h1")
        val saved = slot<HighlightEntity>()
        coVerify(exactly = 1) { highlightDao.upsert(capture(saved)) }
        assertNotNull(saved.captured.deleted_at)

        repository.deleteHighlight("missing")
        coVerify(exactly = 1) { highlightDao.upsert(any()) }
    }

    @Test
    fun `updateHighlightColor bumps revision and updated_at`() = runTest {
        coEvery { highlightDao.getById("h1") } returns highlight()

        repository.updateHighlightColor("h1", "#f00")

        val saved = slot<HighlightEntity>()
        coVerify(exactly = 1) { highlightDao.upsert(capture(saved)) }
        assertEquals("#f00", saved.captured.color)
        assertEquals(2, saved.captured.revision)
    }

    @Test
    fun `updateHighlightNote blanks become null`() = runTest {
        coEvery { highlightDao.getById("h1") } returns highlight()

        repository.updateHighlightNote("h1", "   ")

        val saved = slot<HighlightEntity>()
        coVerify(exactly = 1) { highlightDao.upsert(capture(saved)) }
        assertNull(saved.captured.note)
    }

    @Test
    fun `deleteNote soft deletes and missing id is no-op`() = runTest {
        val note = NoteEntity(
            id = "n1",
            book_id = "b1",
            title = "笔记",
            body = "",
            kind = "note",
            created_at = "2026-08-01T10:00:00Z",
            updated_at = "2026-08-01T10:00:00Z",
        )
        coEvery { noteDao.getById("n1") } returns note
        coEvery { noteDao.getById("missing") } returns null

        repository.deleteNote("n1")
        val saved = slot<NoteEntity>()
        coVerify(exactly = 1) { noteDao.upsert(capture(saved)) }
        assertNotNull(saved.captured.deleted_at)

        repository.deleteNote("missing")
        coVerify(exactly = 1) { noteDao.upsert(any()) }
    }

    @Test
    fun `addBookmark builds bookmark note`() = runTest {
        repository.addBookmark("b1", "转折处", 128)

        val saved = slot<NoteEntity>()
        coVerify(exactly = 1) { noteDao.upsert(capture(saved)) }
        val note = saved.captured
        assertEquals("书签 · 转折处", note.title)
        assertEquals("bookmark", note.kind)
        assertEquals(128f, note.progress_percent ?: -1f)
        assertEquals("b1", note.book_id)
    }

    @Test
    fun `addBookmark blank book id stores null`() = runTest {
        repository.addBookmark(" ", "", 0)

        val saved = slot<NoteEntity>()
        coVerify(exactly = 1) { noteDao.upsert(capture(saved)) }
        assertNull(saved.captured.book_id)
        assertNull(saved.captured.chapter_title)
    }

    @Test
    fun `convertHighlightToNote merges text and note`() = runTest {
        coEvery { highlightDao.getById("h1") } returns highlight(text = "摘录", note = "批注")

        repository.convertHighlightToNote("h1")

        val saved = slot<NoteEntity>()
        coVerify(exactly = 1) { noteDao.upsert(capture(saved)) }
        val note = saved.captured
        assertEquals("笔记：第三章", note.title)
        assertEquals("摘录\n\n批注", note.body)
        assertEquals("摘录", note.excerpt)
        assertEquals("note", note.kind)
        assertEquals(0.4f, note.progress_percent ?: -1f)
    }

    @Test
    fun `convertHighlightToNote missing highlight is no-op`() = runTest {
        coEvery { highlightDao.getById("missing") } returns null

        repository.convertHighlightToNote("missing")

        coVerify(exactly = 0) { noteDao.upsert(any()) }
    }

    @Test
    fun `convertHighlightToInspiration maps highlight fields`() = runTest {
        coEvery { highlightDao.getById("h1") } returns highlight(text = "一段足够长的原文摘录用于截取前二十四个字符", note = null)

        repository.convertHighlightToInspiration("h1")

        val saved = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationDao.upsert(capture(saved)) }
        val entity = saved.captured
        assertEquals("高亮灵感：${"一段足够长的原文摘录用于截取前二十四个字符".take(24)}", entity.title)
        assertEquals("一段足够长的原文摘录用于截取前二十四个字符", entity.body)
        assertEquals("inbox", entity.status)
        assertEquals("b1", entity.source_book_id)
    }

    @Test
    fun `save pass-through delegates to daos`() = runTest {
        val h = highlight()
        repository.saveHighlight(h)
        coVerify(exactly = 1) { highlightDao.upsert(h) }

        val i = InspirationEntity(
            id = "i1",
            title = "t",
            created_at = "2026-08-01T10:00:00Z",
            updated_at = "2026-08-01T10:00:00Z",
        )
        repository.saveInspiration(i)
        coVerify(exactly = 1) { inspirationDao.upsert(i) }
    }
}
