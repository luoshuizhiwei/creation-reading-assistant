package com.creationreadingassistant.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.entity.ChapterReadEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ChapterReadDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ChapterReadDao

    @Before
    fun setup() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            )
            .allowMainThreadQueries()
            .build()
        dao = database.chapterReadDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // ─── upsert 单条 & 重复键 REPLACE ────────────────────────────────

    @Test
    fun upsert_inserts_new_row_and_emits_chapter_index() = runTest {
        dao.upsert(
            ChapterReadEntity(
                book_id = "book-a",
                chapter_index = 0,
                read_at = "2026-08-15T10:00:00Z",
            ),
        )

        val chapterIndexes = dao.observeReadChapters("book-a").first()
        assertEquals(listOf(0), chapterIndexes)
        assertEquals("2026-08-15T10:00:00Z", dao.getReadAt("book-a", 0))
    }

    @Test
    fun upsert_conflict_replaces_read_at_without_duplicate_row() = runTest {
        dao.upsert(
            ChapterReadEntity(
                book_id = "book-a",
                chapter_index = 1,
                read_at = "2026-08-15T10:00:00Z",
            ),
        )
        dao.upsert(
            ChapterReadEntity(
                book_id = "book-a",
                chapter_index = 1,
                read_at = "2026-08-15T11:00:00Z",
            ),
        )

        val chapterIndexes = dao.observeReadChapters("book-a").first()
        assertEquals(listOf(1), chapterIndexes)
        assertEquals(1, dao.readCount("book-a"))
        assertEquals("2026-08-15T11:00:00Z", dao.getReadAt("book-a", 1))
    }

    @Test
    fun getReadAt_missing_chapter_returns_null() = runTest {
        dao.upsert(ChapterReadEntity("book-a", 0, "t0"))
        assertNull(dao.getReadAt("book-a", 99))
        assertNull(dao.getReadAt("nonexistent", 0))
    }

    // ─── upsertAll 批量 ───────────────────────────────────────────────

    @Test
    fun upsertAll_inserts_multiple_rows() = runTest {
        val batch = (0 until 5).map { i ->
            ChapterReadEntity(
                book_id = "book-b",
                chapter_index = i,
                read_at = "2026-08-15T10:0${i}:00Z",
            )
        }
        dao.upsertAll(batch)

        assertEquals(5, dao.readCount("book-b"))
        assertEquals((0 until 5).toList(), dao.observeReadChapters("book-b").first())
    }

    @Test
    fun upsertAll_mixed_with_conflict_updates_existing_read_at() = runTest {
        val first = listOf(
            ChapterReadEntity("book-mix", 0, "t0"),
            ChapterReadEntity("book-mix", 1, "t1"),
        )
        dao.upsertAll(first)

        val mixed = listOf(
            ChapterReadEntity("book-mix", 1, "t1-new"),
            ChapterReadEntity("book-mix", 2, "t2"),
        )
        dao.upsertAll(mixed)

        val chapterIndexes = dao.observeReadChapters("book-mix").first()
        assertEquals(listOf(0, 1, 2), chapterIndexes)
        assertEquals(3, dao.readCount("book-mix"))
        assertEquals("t0", dao.getReadAt("book-mix", 0))
        assertEquals("t1-new", dao.getReadAt("book-mix", 1))
        assertEquals("t2", dao.getReadAt("book-mix", 2))
    }

    // ─── observeReadChapters: List<Int> & 跨书隔离 & 空状态 ───────────

    @Test
    fun observeReadChapters_returns_sorted_chapter_indexes() = runTest {
        dao.upsert(ChapterReadEntity("book-x", 5, "t5"))
        dao.upsert(ChapterReadEntity("book-x", 0, "t0"))
        dao.upsert(ChapterReadEntity("book-x", 3, "t3"))

        assertEquals(listOf(0, 3, 5), dao.observeReadChapters("book-x").first())
    }

    @Test
    fun observeReadChapters_isolated_by_book() = runTest {
        dao.upsert(ChapterReadEntity("book-a", 0, "ta0"))
        dao.upsert(ChapterReadEntity("book-a", 1, "ta1"))
        dao.upsert(ChapterReadEntity("book-b", 0, "tb0"))

        val a = dao.observeReadChapters("book-a").first()
        val b = dao.observeReadChapters("book-b").first()
        assertEquals(listOf(0, 1), a)
        assertEquals(listOf(0), b)
    }

    @Test
    fun observeReadChapters_empty_book_emits_empty_list() = runTest {
        assertTrue(dao.observeReadChapters("nonexistent").first().isEmpty())
    }

    // ─── count: readCount & observeReadCount 跨书/空 ─────────────────

    @Test
    fun readCount_matches_observeReadCount_and_observe_chapter_size() = runTest {
        repeat(7) { i -> dao.upsert(ChapterReadEntity("book-c", i, "t$i")) }

        val direct = dao.readCount("book-c")
        val observed = dao.observeReadCount("book-c").first()
        val listSize = dao.observeReadChapters("book-c").first().size
        assertEquals(7, direct)
        assertEquals(7, observed)
        assertEquals(7, listSize)
    }

    @Test
    fun readCount_empty_book_returns_zero() = runTest {
        assertEquals(0, dao.readCount("missing"))
    }

    @Test
    fun readCount_isolated_by_book() = runTest {
        dao.upsert(ChapterReadEntity("book-a", 0, "t"))
        dao.upsert(ChapterReadEntity("book-a", 1, "t"))
        dao.upsert(ChapterReadEntity("book-b", 0, "t"))

        assertEquals(2, dao.readCount("book-a"))
        assertEquals(1, dao.readCount("book-b"))
        assertEquals(0, dao.readCount("book-c"))
    }

    // ─── clearForBook: 只删目标书 & 空安全 ────────────────────────────

    @Test
    fun clearForBook_deletes_only_target_book() = runTest {
        dao.upsert(ChapterReadEntity("book-a", 0, "ta"))
        dao.upsert(ChapterReadEntity("book-a", 1, "ta"))
        dao.upsert(ChapterReadEntity("book-b", 0, "tb"))

        dao.clearForBook("book-a")

        assertEquals(0, dao.readCount("book-a"))
        assertEquals(1, dao.readCount("book-b"))
        assertTrue(dao.observeReadChapters("book-a").first().isEmpty())
        assertFalse(dao.observeReadChapters("book-b").first().isEmpty())
    }

    @Test
    fun clearForBook_on_empty_book_is_safe() = runTest {
        dao.clearForBook("nonexistent") // 不抛异常
        assertEquals(0, dao.readCount("nonexistent"))
    }
}
