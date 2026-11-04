package com.creationreadingassistant.feature.material

import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.StatsCreatedRow
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 任务65-v2（CRA-READING-MATERIAL-FLOW-v2）：取材「保存 → 检索找回 → 回源坐标」的
 * **真实生产调用路径**定向测试。
 *
 * 与 v1 草稿的区别（v1 已被 Root 判定为无效，本文件不继承其断言）：
 *  - v1 step2 只构造 NoteEntity 并断言自己填的字面值 —— 不经过任何保存入口；
 *    这里改用真实 [NoteRepository.saveHighlight] / [saveNote] /
 *    [NoteRepository.convertHighlightToNote] / [NoteRepository.convertHighlightToInspiration]，
 *    存入真实（非 mock）DAO 实现后**再读回来**。
 *  - v1 step3 用 `String.contains` 冒充检索 —— 这里调用真实
 *    [NoteRepository.searchNotes] / [NoteRepository.searchHighlights]，检索语义由
 *    DAO 实现按生产 SQL 合同（见各 @Query 注释）执行。
 *  - v1 step1/step7 只构造实体就宣称「打开书」「冷启动」—— 本文件不保留这两类断言。
 *
 * 层级边界（如实写明）：
 *  - 这三个 DAO 是**按生产 @Query 语义手写的内存实现**，覆盖「仓储 → DAO 接口」调用链与
 *    LIKE/deleted_at 语义；**真实 SQLite/Room 文件库与关闭重开属于仪器测试层**
 *    （[com.creationreadingassistant.acceptance.HighlightInspirationPersistenceAcceptTest]），
 *    本文件不冒充该层证据。
 *  - 路由/返回/导出/归类由既有测试覆盖（见 REPORT-65-v2.md 复用清单），本文件不重复。
 */
/** 未用到的 DAO 方法：被调用即说明走出了取材检索合同，直接失败而不是静默返回空。 */
private fun <T> unsupported(): T =
    throw UnsupportedOperationException("本用例不需要该方法：被调用说明走出了取材检索合同")

class ReadingMaterialRetrievalTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── 真实（非 mock）内存 DAO：语义对齐生产 @Query 注释 ────────────────────

    private class MemoryNoteDao : NoteDao {
        val rows = LinkedHashMap<String, NoteEntity>()
        private fun active(): List<NoteEntity> = rows.values.filter { it.deleted_at == null }

        override suspend fun upsert(note: NoteEntity) { rows[note.id] = note }
        override suspend fun upsertAll(notes: List<NoteEntity>) { notes.forEach { rows[it.id] = it } }
        override fun observeAllActive(): Flow<List<NoteEntity>> = flow { emit(active()) }
        override fun observeStatsCreatedRows(): Flow<List<StatsCreatedRow>> = unsupported()
        override fun observeByBook(bookId: String): Flow<List<NoteEntity>> =
            flow { emit(active().filter { it.book_id == bookId }) }
        override suspend fun getActiveByBookIds(bookIds: Collection<String>): List<NoteEntity> =
            active().filter { it.book_id in bookIds }
        /** 生产 SQL：`deleted_at IS NULL AND (title LIKE '%q%' OR body LIKE '%q%' OR excerpt LIKE '%q%')`。 */
        override suspend fun search(q: String): List<NoteEntity> = active().filter {
            it.title.contains(q) || it.body.contains(q) || it.excerpt?.contains(q) == true
        }
        override suspend fun getDeleted(): List<NoteEntity> = rows.values.filter { it.deleted_at != null }
        override suspend fun getById(id: String): NoteEntity? = rows[id]
        override suspend fun getByIds(ids: Collection<String>): List<NoteEntity> = ids.mapNotNull { rows[it] }
    }

    private class MemoryHighlightDao : HighlightDao {
        val rows = LinkedHashMap<String, HighlightEntity>()
        private fun active(): List<HighlightEntity> = rows.values.filter { it.deleted_at == null }

        override suspend fun upsert(highlight: HighlightEntity) { rows[highlight.id] = highlight }
        override suspend fun upsertAll(highlights: List<HighlightEntity>) =
            highlights.forEach { rows[it.id] = it }
        override fun observeByBook(bookId: String): Flow<List<HighlightEntity>> =
            flow { emit(active().filter { it.book_id == bookId }) }
        override suspend fun getActiveByBookIds(bookIds: Collection<String>): List<HighlightEntity> =
            active().filter { it.book_id in bookIds }
        override fun observeAllActive(): Flow<List<HighlightEntity>> = flow { emit(active()) }
        override suspend fun getDeleted(): List<HighlightEntity> =
            rows.values.filter { it.deleted_at != null }
        override suspend fun getById(id: String): HighlightEntity? = rows[id]
        override suspend fun getByIds(ids: Collection<String>): List<HighlightEntity> =
            ids.mapNotNull { rows[it] }
        /** 生产 SQL：`deleted_at IS NULL AND (text LIKE '%q%' OR note LIKE '%q%')`。 */
        override suspend fun search(q: String): List<HighlightEntity> = active().filter {
            it.text.contains(q) || it.note?.contains(q) == true
        }
    }

    private class MemoryInspirationDao : InspirationDao {
        val rows = LinkedHashMap<String, InspirationEntity>()
        private fun active(): List<InspirationEntity> = rows.values.filter { it.deleted_at == null }

        override suspend fun upsert(inspiration: InspirationEntity) { rows[inspiration.id] = inspiration }
        override fun observeAllActive(): Flow<List<InspirationEntity>> = flow { emit(active()) }
        override fun observeStatsCreatedRows(): Flow<List<StatsCreatedRow>> = unsupported()
        override suspend fun getById(id: String): InspirationEntity? = rows[id]
        override suspend fun getByIds(ids: Collection<String>): List<InspirationEntity> =
            ids.mapNotNull { rows[it] }
        /** 生产 SQL：`deleted_at IS NULL AND (title LIKE '%q%' OR body LIKE '%q%')`。 */
        override suspend fun search(q: String): List<InspirationEntity> = active().filter {
            it.title.contains(q) || it.body.contains(q)
        }
        override suspend fun getDeleted(): List<InspirationEntity> =
            rows.values.filter { it.deleted_at != null }
        override fun observeByBook(bookId: String): Flow<List<InspirationEntity>> =
            flow { emit(active().filter { it.source_book_id == bookId }) }
    }

    // ── 用例 ────────────────────────────────────────────────────────────────

    /** 步骤 2→3：摘录与个人想法经真实保存入口落库后，能被真实检索入口找回，且回源坐标未被破坏。 */
    @Test
    fun saved_excerpt_and_thought_are_found_by_real_repository_search() = runTest {
        val highlights = MemoryHighlightDao()
        val notes = MemoryNoteDao()
        val repository = NoteRepository(highlights, notes, MemoryInspirationDao())

        val excerpt = "文学的本质在于反映社会生活的真实一面。"
        val thought = "这段话点出了核心矛盾，可用于第三章美学论证。"
        val locatorJson = LocatorCodec.encode(
            legacyOffset = 1520,
            chapterIndex = 0,
            charOffset = 320,
            excerpt = excerpt,
        )

        repository.saveHighlight(
            HighlightEntity(
                id = "hl-excerpt-1",
                book_id = "book-txt-001",
                text = excerpt,
                note = thought,
                chapter_title = "第一章 绪论",
                progress_percent = 12.5f,
                locator_json = locatorJson,
                created_at = "2026-10-19T20:05:00Z",
                updated_at = "2026-10-19T20:05:00Z",
            ),
        )
        // 真实生产转换入口：高亮（摘录 + 批注想法）→ 笔记
        repository.convertHighlightToNote("hl-excerpt-1")

        val found = repository.searchNotes("核心矛盾")
        assertEquals("个人想法必须能被检索找回", 1, found.size)
        val note = found.first()
        assertEquals(excerpt, note.excerpt)
        assertTrue("笔记正文必须带上个人想法", note.body.contains(thought))
        assertEquals("book-txt-001", note.book_id)

        // 命中项自带的定位能还原到同一全书/章节偏移 —— 回源坐标未被保存流程改坏
        val decoded = LocatorCodec.decode(note.locator_json)
        assertEquals(1520, requireNotNull(decoded?.legacyOffset))
        assertEquals(320, requireNotNull(decoded?.charOffset))

        // 高亮检索按正文命中
        val highlightHits = repository.searchHighlights("文学的本质")
        assertEquals(1, highlightHits.size)
        assertEquals("hl-excerpt-1", highlightHits.first().id)
    }

    /** 步骤 3 的反面：不命中返回空；软删除的取材不再被检索返回（deleted_at 生效）。 */
    @Test
    fun search_misses_return_empty_and_soft_deleted_material_is_excluded() = runTest {
        val notes = MemoryNoteDao()
        val repository = NoteRepository(MemoryHighlightDao(), notes, MemoryInspirationDao())

        val note = NoteEntity(
            id = "note-dialog-1",
            book_id = "book-txt-001",
            title = "美学矛盾的笔记",
            body = "可用于第三章的美学论证。",
            excerpt = "形式与内容的辩证统一。",
            chapter_title = "第二章 发展",
            progress_percent = 25.0f,
            kind = "note",
            locator_json = LocatorCodec.encode(4000, 1, 80, "形式与内容的辩证统一。"),
            payload = "{}",
            created_at = "2026-10-19T20:10:00Z",
            updated_at = "2026-10-19T20:10:00Z",
        )
        // 真实保存入口（笔记对话框确认路径）
        repository.saveNote(note)

        assertEquals(1, repository.searchNotes("美学").size)
        assertTrue("不命中必须返回空", repository.searchNotes("并不存在的关键词").isEmpty())

        repository.deleteNote("note-dialog-1")
        assertTrue("软删除后不得再被检索返回", repository.searchNotes("美学").isEmpty())
        assertEquals("软删除保留记录本身", 1, notes.rows.size)
    }

    /** 步骤 4 的数据前提：转灵感必须把来源摘录与定位写进 payload.source，回源才有据可依。 */
    @Test
    fun converted_inspiration_keeps_source_excerpt_and_locator_for_return() = runTest {
        val inspirations = MemoryInspirationDao()
        val repository = NoteRepository(MemoryHighlightDao(), MemoryNoteDao(), inspirations)

        val excerpt = "叙事视角的转换决定了读者的道德判断。"
        val locatorJson = LocatorCodec.encode(9800, 2, 140, excerpt)
        repository.saveHighlight(
            HighlightEntity(
                id = "hl-excerpt-2",
                book_id = "book-epub-002",
                text = excerpt,
                note = "视角与伦理的关系可以单列一节。",
                chapter_title = "第三章 视角",
                progress_percent = 60.0f,
                locator_json = locatorJson,
                created_at = "2026-10-19T20:20:00Z",
                updated_at = "2026-10-19T20:20:00Z",
            ),
        )
        repository.convertHighlightToInspiration("hl-excerpt-2")

        val created = inspirations.rows.values.single { it.deleted_at == null }
        assertEquals("book-epub-002", created.source_book_id)
        val source = json.parseToJsonElement(requireNotNull(created.payload)).jsonObject["source"]!!.jsonObject
        assertEquals(excerpt, source["excerpt"]!!.jsonPrimitive.content)
        val decoded = LocatorCodec.decode(source["locatorJson"]!!.jsonPrimitive.content)
        assertEquals(9800, requireNotNull(decoded?.legacyOffset))
        assertEquals(140, requireNotNull(decoded?.charOffset))
    }
}
