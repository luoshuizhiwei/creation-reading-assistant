package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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

    private fun highlight(
        id: String = "h1",
        text: String = "原文摘录",
        note: String? = null,
        bookId: String = "b1",
        chapterTitle: String? = "第三章",
        progressPercent: Float? = 0.4f,
        locatorJson: String? = null,
    ) = HighlightEntity(
        id = id,
        book_id = bookId,
        text = text,
        color = "#ff0",
        note = note,
        chapter_title = chapterTitle,
        progress_percent = progressPercent,
        locator_json = locatorJson,
        created_at = "2026-08-01T10:00:00Z",
        updated_at = "2026-08-01T10:00:00Z",
    )

    /**
     * 用**真实消费模型**反序列化落库 payload —— 与 InspirationViewModel /
     * HomeArchiveFormatters 的解析配置完全一致，避免只核对 JSON 子串。
     */
    private val payloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun sourceOf(entity: InspirationEntity): InspirationSourceInfo? =
        payloadJson.decodeFromString<InspirationPayloadData>(entity.payload ?: "{}").source

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
    fun `convertHighlightToInspiration persists structured source with excerpt and locator`() = runTest {
        val locator = """{"v":2,"offset":128,"chapterIndex":2}"""
        coEvery { highlightDao.getById("h1") } returns highlight(
            text = "原文摘录",
            note = "我的批注",
            locatorJson = locator,
        )

        repository.convertHighlightToInspiration("h1")

        val saved = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationDao.upsert(capture(saved)) }
        val entity = saved.captured
        val source = sourceOf(entity)
        assertNotNull("payload.source 缺失：灵感详情与回源无法消费来源", source)
        source!!
        assertEquals("b1", source.bookId)
        assertEquals("第三章", source.chapterTitle)
        assertEquals(0.4f, source.progressPercent ?: -1f)
        assertEquals("原文摘录", source.excerpt)
        assertEquals(locator, source.locatorJson)
        // 来源摘录与个人批注可区分：source.excerpt 只有原文，body 才带批注
        assertEquals("原文摘录", source.excerpt)
        assertEquals("原文摘录\n\n我的批注", entity.body)
    }

    @Test
    fun `convertHighlightToInspiration without note still keeps excerpt and locator`() = runTest {
        val locator = """{"v":2,"offset":64}"""
        coEvery { highlightDao.getById("h1") } returns highlight(
            text = "摘录",
            note = null,
            locatorJson = locator,
        )

        repository.convertHighlightToInspiration("h1")

        val saved = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationDao.upsert(capture(saved)) }
        val source = sourceOf(saved.captured)
        assertNotNull(source)
        assertEquals("摘录", source!!.excerpt)
        assertEquals(locator, source.locatorJson)
        assertEquals("摘录", saved.captured.body)
    }

    @Test
    fun `convertHighlightToInspiration does not fabricate source for missing locator or book id`() = runTest {
        coEvery { highlightDao.getById("h1") } returns highlight(
            text = "摘录",
            note = null,
            bookId = " ",
            chapterTitle = null,
            progressPercent = null,
            locatorJson = null,
        )

        repository.convertHighlightToInspiration("h1")

        val saved = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationDao.upsert(capture(saved)) }
        val source = sourceOf(saved.captured)
        assertNotNull(source)
        assertNull(source!!.bookId)
        assertNull(source.chapterTitle)
        assertNull(source.progressPercent)
        assertNull(source.locatorJson)
        // 真实存在的摘录仍保留，不被"无来源"连坐清空
        assertEquals("摘录", source.excerpt)
    }

    @Test
    fun `convertHighlightToInspiration preserves a real zero progress`() = runTest {
        coEvery { highlightDao.getById("h1") } returns highlight(progressPercent = 0f)
        repository.convertHighlightToInspiration("h1")
        val saved = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationDao.upsert(capture(saved)) }
        val source = sourceOf(saved.captured)
        assertNotNull(source)
        assertEquals(0f, source!!.progressPercent)
    }

    @Test
    fun `convertHighlightToInspiration missing highlight is no-op`() = runTest {
        coEvery { highlightDao.getById("missing") } returns null

        repository.convertHighlightToInspiration("missing")

        coVerify(exactly = 0) { inspirationDao.upsert(any()) }
    }

    @Test
    fun `convertHighlightToInspiration leaves the original highlight untouched`() = runTest {
        coEvery { highlightDao.getById("h1") } returns highlight(
            text = "摘录",
            note = "批注",
            locatorJson = """{"v":2,"offset":1}""",
        )

        repository.convertHighlightToInspiration("h1")

        coVerify(exactly = 0) { highlightDao.upsert(any()) }
        coVerify(exactly = 1) { highlightDao.getById("h1") }
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
