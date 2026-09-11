package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * R1 统一阅读笔记新增的仓储能力单测：撤销恢复（restore）与批注编辑（updateNoteBody）。
 * 关键回归：恢复只清自身 deleted_at（不复活更早的已删项）、编辑只改 body 并保留定位信息。
 */
class NoteRepositoryRestoreTest {

    private lateinit var highlightDao: HighlightDao
    private lateinit var noteDao: NoteDao
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        highlightDao = mockk(relaxed = true)
        noteDao = mockk(relaxed = true)
        repository = NoteRepository(highlightDao, noteDao, mockk<InspirationDao>(relaxed = true))
    }

    private fun deletedHighlight() = HighlightEntity(
        id = "h1",
        book_id = "b1",
        text = "摘录",
        note = "批注",
        chapter_title = "第一章",
        locator_json = """{"v":2,"offset":100,"ci":0,"co":50}""",
        revision = 3,
        created_at = "2026-08-01T10:00:00Z",
        updated_at = "2026-08-01T10:00:00Z",
        deleted_at = "2026-08-05T10:00:00Z",
    )

    private fun deletedNote() = NoteEntity(
        id = "n1",
        book_id = "b1",
        title = "笔记",
        body = "旧批注",
        excerpt = "摘录",
        chapter_title = "第二章",
        kind = "note",
        locator_json = """{"v":2,"offset":200,"ci":1,"co":20}""",
        revision = 2,
        created_at = "2026-08-01T10:00:00Z",
        updated_at = "2026-08-01T10:00:00Z",
        deleted_at = "2026-08-05T10:00:00Z",
    )

    @Test
    fun `restoreHighlight clears deleted_at and bumps revision`() = runTest {
        coEvery { highlightDao.getById("h1") } returns deletedHighlight()

        repository.restoreHighlight("h1")

        val saved = slot<HighlightEntity>()
        coVerify(exactly = 1) { highlightDao.upsert(capture(saved)) }
        assertNull(saved.captured.deleted_at)
        assertEquals(4, saved.captured.revision)
        // 恢复不得动定位与内容字段
        assertEquals("""{"v":2,"offset":100,"ci":0,"co":50}""", saved.captured.locator_json)
        assertEquals("摘录", saved.captured.text)
    }

    @Test
    fun `restoreHighlight is no-op for missing or alive records`() = runTest {
        coEvery { highlightDao.getById("missing") } returns null
        coEvery { highlightDao.getById("alive") } returns deletedHighlight().copy(deleted_at = null)

        repository.restoreHighlight("missing")
        repository.restoreHighlight("alive")

        coVerify(exactly = 0) { highlightDao.upsert(any()) }
    }

    @Test
    fun `restoreNote clears deleted_at and bumps revision`() = runTest {
        coEvery { noteDao.getById("n1") } returns deletedNote()

        repository.restoreNote("n1")

        val saved = slot<NoteEntity>()
        coVerify(exactly = 1) { noteDao.upsert(capture(saved)) }
        assertNull(saved.captured.deleted_at)
        assertEquals(3, saved.captured.revision)
        assertEquals("note", saved.captured.kind)
    }

    @Test
    fun `restoreNote is no-op for missing or alive records`() = runTest {
        coEvery { noteDao.getById("missing") } returns null
        coEvery { noteDao.getById("alive") } returns deletedNote().copy(deleted_at = null)

        repository.restoreNote("missing")
        repository.restoreNote("alive")

        coVerify(exactly = 0) { noteDao.upsert(any()) }
    }

    @Test
    fun `updateNoteBody keeps locator chapter and kind`() = runTest {
        coEvery { noteDao.getById("n1") } returns deletedNote().copy(deleted_at = null)

        repository.updateNoteBody("n1", "新批注")

        val saved = slot<NoteEntity>()
        coVerify(exactly = 1) { noteDao.upsert(capture(saved)) }
        assertEquals("新批注", saved.captured.body)
        assertEquals(3, saved.captured.revision)
        // 定位与来源信息原样保留
        assertEquals("""{"v":2,"offset":200,"ci":1,"co":20}""", saved.captured.locator_json)
        assertEquals("第二章", saved.captured.chapter_title)
        assertEquals("摘录", saved.captured.excerpt)
        assertEquals("note", saved.captured.kind)
        assertNotNull(saved.captured.updated_at)
    }

    @Test
    fun `updateNoteBody missing note is no-op`() = runTest {
        coEvery { noteDao.getById("missing") } returns null
        repository.updateNoteBody("missing", "x")
        coVerify(exactly = 0) { noteDao.upsert(any()) }
    }
}