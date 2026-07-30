package com.creationreadingassistant.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Room 迁移测试：覆盖全部迁移路径 1→2→3→4→5→6。
 *
 * 使用 [MigrationTestHelper] 加载 schema JSON，逐步执行迁移 SQL 并校验表结构。
 * 每次迁移前插入测试数据，迁移后验证数据未丢失、新列/新表正确创建。
 */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val migrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory(),
    )

    // ─── 1 → 2：highlights 补 chapter_title / progress_percent ─────────

    @Test
    @Throws(IOException::class)
    fun migrate_1_to_2_preserves_highlights() {
        // 1. 创建 v1 数据库，插入测试数据（不含 v2 新增列）
        var db = migrationTestHelper.createDatabase(TEST_DB, 1)

        // books (v1: 无 description)
        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('test-book-1', '测试 EPUB', '测试作者', 'epub', 'test.epub', 'hash001', " +
                "1024, '/uri/test', '/content/test', 'ready', NULL, " +
                "'2026-01-01T00:00:00Z', 'device-1', '{}', 1, '2026-01-01T00:00:00Z', NULL)",
        )

        // highlights (v1: 无 chapter_title, progress_percent)
        db.execSQL(
            "INSERT INTO highlights (id, book_id, text, note, color, locator_json, payload, " +
                "created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('test-highlight-1', 'test-book-1', '高亮文本', '笔记内容', 'yellow', " +
                "'{\"cfi\":\"/4/2\"}', '{}', '2026-01-02T00:00:00Z', 'device-1', 1, " +
                "'2026-01-02T00:00:00Z', NULL)",
        )

        // reading_progress (v1: 无 completed_at)
        db.execSQL(
            "INSERT INTO reading_progress (book_id, progress_percent, last_read_at, " +
                "total_reading_time_ms, completion_state, current_location_json, payload, " +
                "revision, device_id, updated_at, deleted_at) " +
                "VALUES ('test-book-1', 25.0, '2026-01-03T00:00:00Z', 1800000, 'in_progress', " +
                "'{\"chapter\":1}', '{}', 1, 'device-1', '2026-01-03T00:00:00Z', NULL)",
        )

        db.close()

        // 2. 执行迁移 1 → 2
        db = migrationTestHelper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2)

        // 3. 验证 highlights 表新增列存在
        val highlightCursor = db.query(
            "SELECT chapter_title, progress_percent FROM highlights WHERE id = 'test-highlight-1'",
        )
        assertTrue("highlights 数据应保留", highlightCursor.moveToFirst())
        // 新增列默认为 NULL
        assertTrue(
            "chapter_title 列应存在且为 NULL",
            highlightCursor.isNull(highlightCursor.getColumnIndexOrThrow("chapter_title")),
        )
        assertTrue(
            "progress_percent 列应存在且为 NULL",
            highlightCursor.isNull(highlightCursor.getColumnIndexOrThrow("progress_percent")),
        )
        highlightCursor.close()

        // 4. 验证 books 数据未丢失
        val booksCursor = db.query("SELECT title, author FROM books WHERE id = 'test-book-1'")
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("测试 EPUB", booksCursor.getString(0))
        assertEquals("测试作者", booksCursor.getString(1))
        booksCursor.close()

        // 5. 验证 reading_progress 数据未丢失
        val progressCursor = db.query(
            "SELECT progress_percent, completion_state FROM reading_progress WHERE book_id = 'test-book-1'",
        )
        assertTrue("reading_progress 数据应保留", progressCursor.moveToFirst())
        assertEquals(25.0, progressCursor.getDouble(0), 0.001)
        assertEquals("in_progress", progressCursor.getString(1))
        progressCursor.close()

        db.close()
    }

    // ─── 2 → 3：books 补 description ───────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate_2_to_3_adds_books_description() {
        // 1. 创建 v2 数据库（highlights 已有 chapter_title/progress_percent，books 无 description）
        var db = migrationTestHelper.createDatabase(TEST_DB, 2)

        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('test-book-2', '测试 TXT', '作者B', 'txt', 'book.txt', 'hash002', " +
                "2048, '/uri/b', '/content/b', 'ready', NULL, " +
                "'2026-02-01T00:00:00Z', 'device-1', '{}', 1, '2026-02-01T00:00:00Z', NULL)",
        )

        db.execSQL(
            "INSERT INTO highlights (id, book_id, text, note, color, chapter_title, progress_percent, " +
                "locator_json, payload, created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('test-highlight-2', 'test-book-2', '第二章高亮', '批注', 'green', " +
                "'第二章', 35.5, '{}', '{}', '2026-02-02T00:00:00Z', 'device-1', 1, " +
                "'2026-02-02T00:00:00Z', NULL)",
        )

        db.execSQL(
            "INSERT INTO reading_progress (book_id, progress_percent, last_read_at, " +
                "total_reading_time_ms, completion_state, current_location_json, payload, " +
                "revision, device_id, updated_at, deleted_at) " +
                "VALUES ('test-book-2', 50.0, '2026-02-03T00:00:00Z', 3600000, 'in_progress', " +
                "NULL, '{}', 1, 'device-1', '2026-02-03T00:00:00Z', NULL)",
        )

        db.close()

        // 2. 执行迁移 2 → 3
        db = migrationTestHelper.runMigrationsAndValidate(TEST_DB, 3, true, AppDatabase.MIGRATION_2_3)

        // 3. 验证 books.description 列存在
        val booksCursor = db.query(
            "SELECT title, description FROM books WHERE id = 'test-book-2'",
        )
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("测试 TXT", booksCursor.getString(0))
        assertTrue(
            "description 列应存在且为 NULL",
            booksCursor.isNull(booksCursor.getColumnIndexOrThrow("description")),
        )
        booksCursor.close()

        // 4. 验证 highlights 数据未丢失（含 v2 新增列）
        val highlightCursor = db.query(
            "SELECT text, chapter_title, progress_percent FROM highlights WHERE id = 'test-highlight-2'",
        )
        assertTrue("highlights 数据应保留", highlightCursor.moveToFirst())
        assertEquals("第二章高亮", highlightCursor.getString(0))
        assertEquals("第二章", highlightCursor.getString(1))
        assertEquals(35.5, highlightCursor.getDouble(2), 0.001)
        highlightCursor.close()

        // 5. 验证 reading_progress 数据未丢失
        val progressCursor = db.query(
            "SELECT progress_percent FROM reading_progress WHERE book_id = 'test-book-2'",
        )
        assertTrue("reading_progress 数据应保留", progressCursor.moveToFirst())
        assertEquals(50.0, progressCursor.getDouble(0), 0.001)
        progressCursor.close()

        db.close()
    }

    // ─── 3 → 4：reading_progress 补 completed_at ───────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate_3_to_4_adds_completed_at() {
        // 1. 创建 v3 数据库（books 已有 description，reading_progress 无 completed_at）
        var db = migrationTestHelper.createDatabase(TEST_DB, 3)

        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, description, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('test-book-3', '测试书籍', '作者C', 'epub', 'test3.epub', 'hash003', " +
                "4096, '/uri/c', '/content/c', 'ready', NULL, '一本测试书', " +
                "'2026-03-01T00:00:00Z', 'device-1', '{}', 1, '2026-03-01T00:00:00Z', NULL)",
        )

        db.execSQL(
            "INSERT INTO highlights (id, book_id, text, note, color, chapter_title, progress_percent, " +
                "locator_json, payload, created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('test-highlight-3', 'test-book-3', '精彩段落', NULL, 'red', " +
                "'第一章', 80.0, '{}', '{}', '2026-03-02T00:00:00Z', 'device-1', 1, " +
                "'2026-03-02T00:00:00Z', NULL)",
        )

        db.execSQL(
            "INSERT INTO reading_progress (book_id, progress_percent, last_read_at, " +
                "total_reading_time_ms, completion_state, current_location_json, payload, " +
                "revision, device_id, updated_at, deleted_at) " +
                "VALUES ('test-book-3', 100.0, '2026-03-10T00:00:00Z', 18000000, 'completed', " +
                "'{\"chapter\":10}', '{}', 1, 'device-1', '2026-03-10T00:00:00Z', NULL)",
        )

        db.close()

        // 2. 执行迁移 3 → 4
        db = migrationTestHelper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4)

        // 3. 验证 reading_progress.completed_at 列存在
        val progressCursor = db.query(
            "SELECT progress_percent, completion_state, completed_at " +
                "FROM reading_progress WHERE book_id = 'test-book-3'",
        )
        assertTrue("reading_progress 数据应保留", progressCursor.moveToFirst())
        assertEquals(100.0, progressCursor.getDouble(0), 0.001)
        assertEquals("completed", progressCursor.getString(1))
        assertTrue(
            "completed_at 列应存在且为 NULL",
            progressCursor.isNull(progressCursor.getColumnIndexOrThrow("completed_at")),
        )
        progressCursor.close()

        // 4. 验证 books 数据未丢失
        val booksCursor = db.query(
            "SELECT title, description FROM books WHERE id = 'test-book-3'",
        )
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("测试书籍", booksCursor.getString(0))
        assertEquals("一本测试书", booksCursor.getString(1))
        booksCursor.close()

        // 5. 验证 highlights 数据未丢失
        val highlightCursor = db.query(
            "SELECT text, chapter_title FROM highlights WHERE id = 'test-highlight-3'",
        )
        assertTrue("highlights 数据应保留", highlightCursor.moveToFirst())
        assertEquals("精彩段落", highlightCursor.getString(0))
        assertEquals("第一章", highlightCursor.getString(1))
        highlightCursor.close()

        db.close()
    }

    // ─── 4 → 5：新建 reader_page_index 表 ──────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate_4_to_5_creates_reader_page_index() {
        // 1. 创建 v4 数据库并插入测试数据
        var db = migrationTestHelper.createDatabase(TEST_DB, 4)

        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, description, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('book-4', '测试书籍', '测试作者', 'epub', 'test.epub', 'hash004', " +
                "1024, '/uri/test', '/content/test', 'ready', NULL, '测试描述', " +
                "'2026-01-01T00:00:00Z', 'device-1', '{}', 1, '2026-01-01T00:00:00Z', NULL)",
        )

        db.execSQL(
            "INSERT INTO reading_progress (book_id, progress_percent, last_read_at, " +
                "total_reading_time_ms, completion_state, current_location_json, payload, " +
                "revision, device_id, updated_at, completed_at, deleted_at) " +
                "VALUES ('book-4', 42.5, '2026-01-02T00:00:00Z', 3600000, 'in_progress', " +
                "'{\"chapter\":3}', '{}', 1, 'device-1', '2026-01-02T00:00:00Z', NULL, NULL)",
        )

        db.execSQL(
            "INSERT INTO highlights (id, book_id, text, note, color, chapter_title, progress_percent, " +
                "locator_json, payload, created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('hl-4', 'book-4', '高亮文本', NULL, 'yellow', '第三章', 42.5, " +
                "'{}', '{}', '2026-01-02T00:00:00Z', 'device-1', 1, '2026-01-02T00:00:00Z', NULL)",
        )

        db.close()

        // 2. 执行迁移 4 → 5
        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 5, true, AppDatabase.MIGRATION_4_5,
        )

        // 3. 验证 reader_page_index 表存在
        val tableCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='reader_page_index'",
        )
        assertTrue("reader_page_index 表应该存在", tableCursor.moveToFirst())
        tableCursor.close()

        // 4. 验证 books 数据未丢失
        val booksCursor = db.query("SELECT title, author FROM books WHERE id = 'book-4'")
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("测试书籍", booksCursor.getString(0))
        assertEquals("测试作者", booksCursor.getString(1))
        booksCursor.close()

        // 5. 验证 reading_progress 数据未丢失
        val progressCursor = db.query(
            "SELECT progress_percent, completion_state FROM reading_progress WHERE book_id = 'book-4'",
        )
        assertTrue("reading_progress 数据应保留", progressCursor.moveToFirst())
        assertEquals(42.5, progressCursor.getDouble(0), 0.001)
        assertEquals("in_progress", progressCursor.getString(1))
        progressCursor.close()

        // 6. 验证 highlights 数据未丢失
        val hlCursor = db.query(
            "SELECT text, chapter_title FROM highlights WHERE id = 'hl-4'",
        )
        assertTrue("highlights 数据应保留", hlCursor.moveToFirst())
        assertEquals("高亮文本", hlCursor.getString(0))
        assertEquals("第三章", hlCursor.getString(1))
        hlCursor.close()

        db.close()
    }

    // ─── 5 → 6：新建 reader_anchor_cache 表 ────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate_5_to_6_creates_reader_anchor_cache() {
        // 1. 创建 v5 数据库并插入测试数据
        var db = migrationTestHelper.createDatabase(TEST_DB, 5)

        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, description, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('book-5', '第五本书', '作者E', 'txt', 'book5.txt', 'hash005', " +
                "2048, '/uri/e', '/content/e', 'ready', NULL, NULL, " +
                "'2026-05-01T00:00:00Z', 'device-1', '{}', 1, '2026-05-01T00:00:00Z', NULL)",
        )

        db.execSQL(
            "INSERT INTO reading_progress (book_id, progress_percent, last_read_at, " +
                "total_reading_time_ms, completion_state, current_location_json, payload, " +
                "revision, device_id, updated_at, completed_at, deleted_at) " +
                "VALUES ('book-5', 80.0, '2026-05-05T00:00:00Z', 7200000, 'in_progress', " +
                "NULL, '{}', 1, 'device-1', '2026-05-05T00:00:00Z', NULL, NULL)",
        )

        db.execSQL(
            "INSERT INTO highlights (id, book_id, text, note, color, chapter_title, progress_percent, " +
                "locator_json, payload, created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('hl-5', 'book-5', '重要段落', '备注', 'blue', '第五章', 80.0, " +
                "'{}', '{}', '2026-05-03T00:00:00Z', 'device-1', 1, '2026-05-03T00:00:00Z', NULL)",
        )

        // 插入 reader_page_index 数据（v5 已有此表）
        db.execSQL(
            "INSERT INTO reader_page_index (content_key, chapter_index, fingerprint, " +
                "page_starts, char_count, created_at) " +
                "VALUES ('book-5', 0, 12345, X'0001020304', 50000, 1706745600000)",
        )

        db.close()

        // 2. 执行迁移 5 → 6
        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 6, true, AppDatabase.MIGRATION_5_6,
        )

        // 3. 验证 reader_anchor_cache 表存在
        val tableCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='reader_anchor_cache'",
        )
        assertTrue("reader_anchor_cache 表应该存在", tableCursor.moveToFirst())
        tableCursor.close()

        // 4. 验证 books 数据未丢失
        val booksCursor = db.query("SELECT title FROM books WHERE id = 'book-5'")
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("第五本书", booksCursor.getString(0))
        booksCursor.close()

        // 5. 验证 reading_progress 数据未丢失
        val progressCursor = db.query(
            "SELECT progress_percent FROM reading_progress WHERE book_id = 'book-5'",
        )
        assertTrue("reading_progress 数据应保留", progressCursor.moveToFirst())
        assertEquals(80.0, progressCursor.getDouble(0), 0.001)
        progressCursor.close()

        // 6. 验证 reader_page_index 数据未丢失
        val pageIndexCursor = db.query(
            "SELECT char_count FROM reader_page_index WHERE content_key = 'book-5'",
        )
        assertTrue("reader_page_index 数据应保留", pageIndexCursor.moveToFirst())
        assertEquals(50000, pageIndexCursor.getInt(0))
        pageIndexCursor.close()

        db.close()
    }

    // ─── 1 → 6 完整链路 ────────────────────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate_1_to_6_full_chain() {
        // 1. 创建 v1 数据库并插入综合测试数据（仅使用 v1 列定义）
        var db = migrationTestHelper.createDatabase(TEST_DB, 1)

        // books (v1: 无 description)
        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('full-chain-book', '全链路测试 EPUB', '全链路作者', 'epub', 'full.epub', " +
                "'hashfull', 4096, '/uri/full', '/content/full', 'ready', NULL, " +
                "'2026-06-01T00:00:00Z', 'device-2', '{}', 1, '2026-06-01T00:00:00Z', NULL)",
        )

        // reading_progress (v1: 无 completed_at)
        db.execSQL(
            "INSERT INTO reading_progress (book_id, progress_percent, last_read_at, " +
                "total_reading_time_ms, completion_state, current_location_json, payload, " +
                "revision, device_id, updated_at, deleted_at) " +
                "VALUES ('full-chain-book', 66.6, '2026-06-10T00:00:00Z', 9000000, 'in_progress', " +
                "'{\"chapter\":5}', '{}', 1, 'device-2', '2026-06-10T00:00:00Z', NULL)",
        )

        // highlights (v1: 无 chapter_title, progress_percent)
        db.execSQL(
            "INSERT INTO highlights (id, book_id, text, note, color, locator_json, payload, " +
                "created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('full-chain-hl', 'full-chain-book', '全链路高亮', '全链路笔记', 'orange', " +
                "'{\"cfi\":\"/6/4\"}', '{}', '2026-06-05T00:00:00Z', 'device-2', 1, " +
                "'2026-06-05T00:00:00Z', NULL)",
        )

        // notes
        db.execSQL(
            "INSERT INTO notes (id, book_id, inspiration_id, title, body, excerpt, chapter_title, " +
                "progress_percent, kind, locator_json, payload, created_at, device_id, revision, " +
                "updated_at, deleted_at) " +
                "VALUES ('full-chain-note', 'full-chain-book', NULL, '全链路笔记标题', '笔记正文', " +
                "'摘录文本', '第五章', 66.6, 'highlight', '{}', '{}', " +
                "'2026-06-06T00:00:00Z', 'device-2', 1, '2026-06-06T00:00:00Z', NULL)",
        )

        // inspirations
        db.execSQL(
            "INSERT INTO inspirations (id, title, body, type, status, source_book_id, payload, " +
                "created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('full-chain-insp', '全链路灵感', '灵感内容', 'original', 'active', " +
                "'full-chain-book', '{}', '2026-06-04T00:00:00Z', 'device-2', 1, " +
                "'2026-06-04T00:00:00Z', NULL)",
        )

        // reading_sessions
        db.execSQL(
            "INSERT INTO reading_sessions (id, book_id, started_at, ended_at, duration_ms, " +
                "progress_percent, created_at, device_id, revision, payload, updated_at, deleted_at) " +
                "VALUES ('full-chain-session', 'full-chain-book', '2026-06-08T10:00:00Z', " +
                "'2026-06-08T11:00:00Z', 3600000, 66.6, '2026-06-08T10:00:00Z', 'device-2', 1, " +
                "'{}', '2026-06-08T11:00:00Z', NULL)",
        )

        // book_content
        db.execSQL(
            "INSERT INTO book_content (book_id, reader_preview, epub_json) " +
                "VALUES ('full-chain-book', '预览内容', '{\"spine\":[]}')",
        )

        // book_files
        db.execSQL(
            "INSERT INTO book_files (book_id, file_name, format, content_hash, size, local_uri, " +
                "payload, updated_at, deleted_at) " +
                "VALUES ('full-chain-book', 'full.epub', 'epub', 'hashfull', 4096, '/uri/full', " +
                "'{}', '2026-06-01T00:00:00Z', NULL)",
        )

        db.close()

        // 2. 执行完整迁移链 1 → 6（传入所有 5 个迁移）
        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 6, true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
        )

        // 3. 验证 books 数据完好
        val booksCursor = db.query(
            "SELECT title, author, description FROM books WHERE id = 'full-chain-book'",
        )
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("全链路测试 EPUB", booksCursor.getString(0))
        assertEquals("全链路作者", booksCursor.getString(1))
        // description 是 v3 新增列，迁移后应为 NULL（未插入值）
        assertTrue("description 应为 NULL", booksCursor.isNull(2))
        booksCursor.close()

        // 4. 验证 reading_progress 数据完好（含 completed_at 列）
        val progressCursor = db.query(
            "SELECT progress_percent, completion_state, completed_at " +
                "FROM reading_progress WHERE book_id = 'full-chain-book'",
        )
        assertTrue("reading_progress 数据应保留", progressCursor.moveToFirst())
        assertEquals(66.6, progressCursor.getDouble(0), 0.001)
        assertEquals("in_progress", progressCursor.getString(1))
        assertTrue("completed_at 应为 NULL", progressCursor.isNull(2))
        progressCursor.close()

        // 5. 验证 highlights 数据完好（含 chapter_title, progress_percent 列）
        val hlCursor = db.query(
            "SELECT text, note, color, chapter_title, progress_percent " +
                "FROM highlights WHERE id = 'full-chain-hl'",
        )
        assertTrue("highlights 数据应保留", hlCursor.moveToFirst())
        assertEquals("全链路高亮", hlCursor.getString(0))
        assertEquals("全链路笔记", hlCursor.getString(1))
        assertEquals("orange", hlCursor.getString(2))
        assertTrue("chapter_title 应为 NULL", hlCursor.isNull(3))
        assertTrue("progress_percent 应为 NULL", hlCursor.isNull(4))
        hlCursor.close()

        // 6. 验证 notes 数据完好
        val noteCursor = db.query(
            "SELECT title, body, chapter_title FROM notes WHERE id = 'full-chain-note'",
        )
        assertTrue("notes 数据应保留", noteCursor.moveToFirst())
        assertEquals("全链路笔记标题", noteCursor.getString(0))
        assertEquals("笔记正文", noteCursor.getString(1))
        assertEquals("第五章", noteCursor.getString(2))
        noteCursor.close()

        // 7. 验证 inspirations 数据完好
        val inspCursor = db.query(
            "SELECT title, body, type FROM inspirations WHERE id = 'full-chain-insp'",
        )
        assertTrue("inspirations 数据应保留", inspCursor.moveToFirst())
        assertEquals("全链路灵感", inspCursor.getString(0))
        assertEquals("灵感内容", inspCursor.getString(1))
        assertEquals("original", inspCursor.getString(2))
        inspCursor.close()

        // 8. 验证 reading_sessions 数据完好
        val sessionCursor = db.query(
            "SELECT duration_ms, progress_percent FROM reading_sessions WHERE id = 'full-chain-session'",
        )
        assertTrue("reading_sessions 数据应保留", sessionCursor.moveToFirst())
        assertEquals(3600000, sessionCursor.getLong(0))
        assertEquals(66.6, sessionCursor.getDouble(1), 0.001)
        sessionCursor.close()

        // 9. 验证 book_content 数据完好
        val contentCursor = db.query(
            "SELECT reader_preview, epub_json FROM book_content WHERE book_id = 'full-chain-book'",
        )
        assertTrue("book_content 数据应保留", contentCursor.moveToFirst())
        assertEquals("预览内容", contentCursor.getString(0))
        assertEquals("{\"spine\":[]}", contentCursor.getString(1))
        contentCursor.close()

        // 10. 验证 book_files 数据完好
        val fileCursor = db.query(
            "SELECT file_name, format, size FROM book_files WHERE book_id = 'full-chain-book'",
        )
        assertTrue("book_files 数据应保留", fileCursor.moveToFirst())
        assertEquals("full.epub", fileCursor.getString(0))
        assertEquals("epub", fileCursor.getString(1))
        assertEquals(4096, fileCursor.getLong(2))
        fileCursor.close()

        // 11. 验证新表存在
        val tablesCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                "('reader_page_index', 'reader_anchor_cache') ORDER BY name",
        )
        assertTrue("reader_anchor_cache 应存在", tablesCursor.moveToFirst())
        assertEquals("reader_anchor_cache", tablesCursor.getString(0))
        assertTrue("reader_page_index 应存在", tablesCursor.moveToNext())
        assertEquals("reader_page_index", tablesCursor.getString(0))
        tablesCursor.close()

        db.close()
    }
}
