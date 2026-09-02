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
 * Room 迁移测试：覆盖全部迁移路径 1→2→3→4→5→6→7→8。
 *
 * 使用 [MigrationTestHelper] 加载 schema JSON，逐步执行迁移 SQL 并校验表结构。
 * 每次迁移前插入测试数据，迁移后验证数据未丢失、新列/新表正确创建。
 * 注：部分索引（含 v7 的 idx_sessions_created）由 CreateIndexCallback onOpen 重建，
 * 不在迁移内建，因此迁移测试只验表结构与数据，索引存在性由开库路径保证。
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

    // ─── 6 → 7：reading_sessions 补时间索引（不改表结构）─────────────

    @Test
    @Throws(IOException::class)
    fun migrate_6_to_7_preserves_sessions() {
        // 1. 创建 v6 数据库并插入测试数据（表结构与 v7 完全一致，仅差索引）
        var db = migrationTestHelper.createDatabase(TEST_DB, 6)

        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, description, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('book-6', '第六本书', '作者F', 'txt', 'book6.txt', 'hash006', " +
                "1024, '/uri/f', '/content/f', 'ready', NULL, NULL, " +
                "'2026-06-01T00:00:00Z', 'device-1', '{}', 1, '2026-06-01T00:00:00Z', NULL)",
        )

        // reading_sessions：活跃 + 软删各一条，验证迁移不碰任何行
        db.execSQL(
            "INSERT INTO reading_sessions (id, book_id, started_at, ended_at, duration_ms, " +
                "progress_percent, created_at, device_id, revision, payload, updated_at, deleted_at) " +
                "VALUES ('session-6', 'book-6', '2026-06-02T10:00:00Z', '2026-06-02T11:00:00Z', " +
                "3600000, 55.5, '2026-06-02T10:00:00Z', 'device-1', 1, '{}', " +
                "'2026-06-02T11:00:00Z', NULL)",
        )
        db.execSQL(
            "INSERT INTO reading_sessions (id, book_id, started_at, ended_at, duration_ms, " +
                "progress_percent, created_at, device_id, revision, payload, updated_at, deleted_at) " +
                "VALUES ('session-6-deleted', 'book-6', '2026-06-01T08:00:00Z', '2026-06-01T08:30:00Z', " +
                "1800000, 10.0, '2026-06-01T08:00:00Z', 'device-1', 1, '{}', " +
                "'2026-06-01T08:30:00Z', '2026-06-03T00:00:00Z')",
        )

        db.close()

        // 2. 执行迁移 6 → 7（仅 dropPartialIndexes，索引由 onOpen 重建）
        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 7, true, AppDatabase.MIGRATION_6_7,
        )

        // 3. 验证活跃会话数据未丢失
        val sessionCursor = db.query(
            "SELECT duration_ms, progress_percent, created_at FROM reading_sessions WHERE id = 'session-6'",
        )
        assertTrue("reading_sessions 数据应保留", sessionCursor.moveToFirst())
        assertEquals(3600000, sessionCursor.getLong(0))
        assertEquals(55.5, sessionCursor.getDouble(1), 0.001)
        assertEquals("2026-06-02T10:00:00Z", sessionCursor.getString(2))
        sessionCursor.close()

        // 4. 验证软删会话同样保留（迁移不得碰 deleted_at）
        val deletedCursor = db.query(
            "SELECT deleted_at FROM reading_sessions WHERE id = 'session-6-deleted'",
        )
        assertTrue("软删会话应保留", deletedCursor.moveToFirst())
        assertEquals("2026-06-03T00:00:00Z", deletedCursor.getString(0))
        deletedCursor.close()

        // 5. 验证 books 数据未丢失
        val booksCursor = db.query("SELECT title FROM books WHERE id = 'book-6'")
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("第六本书", booksCursor.getString(0))
        booksCursor.close()

        db.close()
    }

    // ─── 7 → 8：为全部外键列补 Room 声明索引 ─────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate_7_to_8_adds_fk_indexes() {
        // 1. 创建 v7 数据库并插入覆盖全部外键关联的测试数据
        var db = migrationTestHelper.createDatabase(TEST_DB, 7)

        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, description, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('book-8', '第八本书', '作者G', 'epub', 'book8.epub', 'hash008', " +
                "1024, '/uri/g', '/content/g', 'ready', NULL, NULL, " +
                "'2026-08-01T00:00:00Z', 'device-1', '{}', 1, '2026-08-01T00:00:00Z', NULL)",
        )
        db.execSQL(
            "INSERT INTO tags (id, name, color, type, created_at, device_id, revision, payload, " +
                "updated_at, deleted_at) " +
                "VALUES ('tag-8', '测试标签', 'red', NULL, '2026-08-01T00:00:00Z', 'device-1', 1, " +
                "'{}', '2026-08-01T00:00:00Z', NULL)",
        )
        db.execSQL(
            "INSERT INTO categories (id, name, cover_tone, parent_id, sort_order, created_at, " +
                "device_id, revision, payload, updated_at, deleted_at) " +
                "VALUES ('cat-8', '测试分类', NULL, NULL, 0, '2026-08-01T00:00:00Z', 'device-1', 1, " +
                "'{}', '2026-08-01T00:00:00Z', NULL)",
        )
        db.execSQL(
            "INSERT INTO shelves (id, name, created_at, device_id, revision, payload, updated_at, deleted_at) " +
                "VALUES ('shelf-8', '测试书架', '2026-08-01T00:00:00Z', 'device-1', 1, '{}', " +
                "'2026-08-01T00:00:00Z', NULL)",
        )
        db.execSQL(
            "INSERT INTO inspirations (id, title, body, type, status, source_book_id, payload, " +
                "created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('insp-8', '第八灵感', '灵感正文', 'note', 'inbox', 'book-8', '{}', " +
                "'2026-08-02T00:00:00Z', 'device-1', 1, '2026-08-02T00:00:00Z', NULL)",
        )
        db.execSQL(
            "INSERT INTO inspiration_variants (id, inspiration_id, kind, content, prompt, model, " +
                "payload, created_at) " +
                "VALUES ('var-8', 'insp-8', 'rewrite', '改写文本', NULL, NULL, '{}', " +
                "'2026-08-02T01:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO reading_sessions (id, book_id, started_at, ended_at, duration_ms, " +
                "progress_percent, created_at, device_id, revision, payload, updated_at, deleted_at) " +
                "VALUES ('session-8', 'book-8', '2026-08-03T10:00:00Z', '2026-08-03T11:00:00Z', " +
                "3600000, 30.0, '2026-08-03T10:00:00Z', 'device-1', 1, '{}', " +
                "'2026-08-03T11:00:00Z', NULL)",
        )
        db.execSQL(
            "INSERT INTO notes (id, book_id, inspiration_id, title, body, excerpt, chapter_title, " +
                "progress_percent, kind, locator_json, payload, created_at, device_id, revision, " +
                "updated_at, deleted_at) " +
                "VALUES ('note-8', 'book-8', 'insp-8', '第八笔记', '笔记正文', '摘录', '第三章', 30.0, " +
                "'note', '{}', '{}', '2026-08-04T00:00:00Z', 'device-1', 1, " +
                "'2026-08-04T00:00:00Z', NULL)",
        )
        db.execSQL(
            "INSERT INTO highlights (id, book_id, text, note, color, chapter_title, progress_percent, " +
                "locator_json, payload, created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('hl-8', 'book-8', '第八高亮', '备注', 'yellow', '第三章', 30.0, " +
                "'{}', '{}', '2026-08-04T01:00:00Z', 'device-1', 1, '2026-08-04T01:00:00Z', NULL)",
        )
        db.execSQL(
            "INSERT INTO book_tag (book_id, tag_id) VALUES ('book-8', 'tag-8')",
        )
        db.execSQL(
            "INSERT INTO book_category (book_id, category_id) VALUES ('book-8', 'cat-8')",
        )
        db.execSQL(
            "INSERT INTO shelf_book (shelf_id, book_id, position) VALUES ('shelf-8', 'book-8', 1)",
        )

        db.close()

        // 2. 执行迁移 7 → 8（补 9 个外键列索引）
        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 8, true, AppDatabase.MIGRATION_7_8,
        )

        // 3. 验证 9 个索引全部存在
        val expectedIndexes = listOf(
            "index_reading_sessions_book_id",
            "index_inspirations_source_book_id",
            "index_inspiration_variants_inspiration_id",
            "index_notes_book_id",
            "index_notes_inspiration_id",
            "index_highlights_book_id",
            "index_book_tag_tag_id",
            "index_book_category_category_id",
            "index_shelf_book_book_id",
        )
        for (indexName in expectedIndexes) {
            val idxCursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name = ?",
                arrayOf(indexName),
            )
            assertTrue("索引 $indexName 应存在", idxCursor.moveToFirst())
            assertEquals(indexName, idxCursor.getString(0))
            idxCursor.close()
        }

        // 4. 验证各关联表数据未丢失
        val sessionCursor = db.query(
            "SELECT duration_ms FROM reading_sessions WHERE id = 'session-8'",
        )
        assertTrue("reading_sessions 数据应保留", sessionCursor.moveToFirst())
        assertEquals(3600000, sessionCursor.getLong(0))
        sessionCursor.close()

        val noteCursor = db.query(
            "SELECT book_id, inspiration_id FROM notes WHERE id = 'note-8'",
        )
        assertTrue("notes 数据应保留", noteCursor.moveToFirst())
        assertEquals("book-8", noteCursor.getString(0))
        assertEquals("insp-8", noteCursor.getString(1))
        noteCursor.close()

        val inspCursor = db.query(
            "SELECT source_book_id FROM inspirations WHERE id = 'insp-8'",
        )
        assertTrue("inspirations 数据应保留", inspCursor.moveToFirst())
        assertEquals("book-8", inspCursor.getString(0))
        inspCursor.close()

        val variantCursor = db.query(
            "SELECT inspiration_id FROM inspiration_variants WHERE id = 'var-8'",
        )
        assertTrue("inspiration_variants 数据应保留", variantCursor.moveToFirst())
        assertEquals("insp-8", variantCursor.getString(0))
        variantCursor.close()

        val tagCursor = db.query(
            "SELECT tag_id FROM book_tag WHERE book_id = 'book-8'",
        )
        assertTrue("book_tag 数据应保留", tagCursor.moveToFirst())
        assertEquals("tag-8", tagCursor.getString(0))
        tagCursor.close()

        val catCursor = db.query(
            "SELECT category_id FROM book_category WHERE book_id = 'book-8'",
        )
        assertTrue("book_category 数据应保留", catCursor.moveToFirst())
        assertEquals("cat-8", catCursor.getString(0))
        catCursor.close()

        val shelfCursor = db.query(
            "SELECT position FROM shelf_book WHERE book_id = 'book-8'",
        )
        assertTrue("shelf_book 数据应保留", shelfCursor.moveToFirst())
        assertEquals(1, shelfCursor.getInt(0))
        shelfCursor.close()

        db.close()
    }

    // ─── 1 → 6 历史链路（与既有 v6 数据盘配套保留）──────────────────

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

    // ─── 1 → 8 完整链路 ────────────────────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate_1_to_8_full_chain() {
        // 1. 创建 v1 数据库并插入综合测试数据（仅使用 v1 列定义）
        var db = migrationTestHelper.createDatabase(TEST_DB, 1)

        // books (v1: 无 description)
        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('chain7-book', '全链路v7 EPUB', '全链路作者', 'epub', 'chain7.epub', " +
                "'hashchain7', 4096, '/uri/c7', '/content/c7', 'ready', NULL, " +
                "'2026-07-01T00:00:00Z', 'device-3', '{}', 1, '2026-07-01T00:00:00Z', NULL)",
        )

        // reading_progress (v1: 无 completed_at)
        db.execSQL(
            "INSERT INTO reading_progress (book_id, progress_percent, last_read_at, " +
                "total_reading_time_ms, completion_state, current_location_json, payload, " +
                "revision, device_id, updated_at, deleted_at) " +
                "VALUES ('chain7-book', 33.3, '2026-07-10T00:00:00Z', 4500000, 'in_progress', " +
                "'{\"chapter\":2}', '{}', 1, 'device-3', '2026-07-10T00:00:00Z', NULL)",
        )

        // highlights (v1: 无 chapter_title, progress_percent)
        db.execSQL(
            "INSERT INTO highlights (id, book_id, text, note, color, locator_json, payload, " +
                "created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('chain7-hl', 'chain7-book', '全链路v7高亮', '笔记', 'purple', " +
                "'{\"cfi\":\"/2/2\"}', '{}', '2026-07-05T00:00:00Z', 'device-3', 1, " +
                "'2026-07-05T00:00:00Z', NULL)",
        )

        // reading_sessions（v1 列已齐备，v7 仅加索引）
        db.execSQL(
            "INSERT INTO reading_sessions (id, book_id, started_at, ended_at, duration_ms, " +
                "progress_percent, created_at, device_id, revision, payload, updated_at, deleted_at) " +
                "VALUES ('chain7-session', 'chain7-book', '2026-07-08T10:00:00Z', " +
                "'2026-07-08T12:00:00Z', 7200000, 33.3, '2026-07-08T10:00:00Z', 'device-3', 1, " +
                "'{}', '2026-07-08T12:00:00Z', NULL)",
        )

        db.close()

        // 2. 执行完整迁移链 1 → 8（传入全部 7 个迁移）
        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 8, true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
        )

        // 3. 验证 books 数据完好（含 v3 新列 description）
        val booksCursor = db.query(
            "SELECT title, description FROM books WHERE id = 'chain7-book'",
        )
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("全链路v7 EPUB", booksCursor.getString(0))
        assertTrue("description 应为 NULL", booksCursor.isNull(1))
        booksCursor.close()

        // 4. 验证 reading_progress 数据完好（含 v4 新列 completed_at）
        val progressCursor = db.query(
            "SELECT progress_percent, completed_at FROM reading_progress WHERE book_id = 'chain7-book'",
        )
        assertTrue("reading_progress 数据应保留", progressCursor.moveToFirst())
        assertEquals(33.3, progressCursor.getDouble(0), 0.001)
        assertTrue("completed_at 应为 NULL", progressCursor.isNull(1))
        progressCursor.close()

        // 5. 验证 highlights 数据完好（含 v2 新列）
        val hlCursor = db.query(
            "SELECT text, chapter_title, progress_percent FROM highlights WHERE id = 'chain7-hl'",
        )
        assertTrue("highlights 数据应保留", hlCursor.moveToFirst())
        assertEquals("全链路v7高亮", hlCursor.getString(0))
        assertTrue("chapter_title 应为 NULL", hlCursor.isNull(1))
        assertTrue("progress_percent 应为 NULL", hlCursor.isNull(2))
        hlCursor.close()

        // 6. 验证 reading_sessions 数据完好（v7 核心关注表）
        val sessionCursor = db.query(
            "SELECT duration_ms, progress_percent, created_at FROM reading_sessions WHERE id = 'chain7-session'",
        )
        assertTrue("reading_sessions 数据应保留", sessionCursor.moveToFirst())
        assertEquals(7200000, sessionCursor.getLong(0))
        assertEquals(33.3, sessionCursor.getDouble(1), 0.001)
        assertEquals("2026-07-08T10:00:00Z", sessionCursor.getString(2))
        sessionCursor.close()

        // 7. 验证新表存在
        val tablesCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                "('reader_page_index', 'reader_anchor_cache') ORDER BY name",
        )
        assertTrue("reader_anchor_cache 应存在", tablesCursor.moveToFirst())
        assertEquals("reader_anchor_cache", tablesCursor.getString(0))
        assertTrue("reader_page_index 应存在", tablesCursor.moveToNext())
        assertEquals("reader_page_index", tablesCursor.getString(0))
        tablesCursor.close()

        // 8. 验证外键索引在完整迁移链末端存在（v8 核心新增）
        val idxCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name = 'index_reading_sessions_book_id'",
        )
        assertTrue("索引 index_reading_sessions_book_id 应存在", idxCursor.moveToFirst())
        idxCursor.close()

        db.close()
    }

    // ─── 8 → 9：新建 reader_text_rules 表 ─────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate_8_to_9_creates_reader_text_rules() {
        // 1. 创建 v8 数据库并插入一本书
        var db = migrationTestHelper.createDatabase(TEST_DB, 8)

        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, description, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('rule-book', '测试 TXT', '测试作者', 'txt', 'rule.txt', 'hashrule', " +
                "2048, '/uri/rule', '/content/rule', 'ready', NULL, NULL, " +
                "'2026-08-01T00:00:00Z', 'device-1', '{}', 1, '2026-08-01T00:00:00Z', NULL)",
        )

        db.close()

        // 2. 执行迁移 8 → 9
        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 9, true, AppDatabase.MIGRATION_8_9,
        )

        // 3. 验证 books 数据保留
        val booksCursor = db.query("SELECT title FROM books WHERE id = 'rule-book'")
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("测试 TXT", booksCursor.getString(0))
        booksCursor.close()

        // 4. 新表存在且可插入全局规则与按书规则
        db.execSQL(
            "INSERT INTO reader_text_rules (id, kind, name, pattern, replacement, builtin, " +
                "enabled, scope, book_id, position, created_at, updated_at) " +
                "VALUES ('rule-global', 'TOC', '全局目录规则', NULL, '', 0, 1, 'GLOBAL', NULL, 1, " +
                "1780000000000, 1780000000000)",
        )
        db.execSQL(
            "INSERT INTO reader_text_rules (id, kind, name, pattern, replacement, builtin, " +
                "enabled, scope, book_id, position, created_at, updated_at) " +
                "VALUES ('rule-per-book', 'REPLACE', '按书替换', '旧词', '新词', 0, 1, 'PER_BOOK', " +
                "'rule-book', 2, 1780000000000, 1780000000000)",
        )

        val rulesCursor = db.query(
            "SELECT kind, scope, book_id FROM reader_text_rules ORDER BY position",
        )
        assertTrue("reader_text_rules 应有数据", rulesCursor.moveToFirst())
        assertEquals("TOC", rulesCursor.getString(0))
        assertEquals("GLOBAL", rulesCursor.getString(1))
        assertTrue("全局规则的 book_id 应为 NULL", rulesCursor.isNull(2))
        assertTrue("第二条规则应存在", rulesCursor.moveToNext())
        assertEquals("REPLACE", rulesCursor.getString(0))
        assertEquals("PER_BOOK", rulesCursor.getString(1))
        assertEquals("rule-book", rulesCursor.getString(2))
        rulesCursor.close()

        // 5. 外键存在：PRAGMA foreign_key_list 应包含 book_id → books（ON DELETE CASCADE）
        val fkCursor = db.query("PRAGMA foreign_key_list('reader_text_rules')")
        var fkFound = false
        while (fkCursor.moveToNext()) {
            val from = fkCursor.getString(fkCursor.getColumnIndexOrThrow("from"))
            val refTable = fkCursor.getString(fkCursor.getColumnIndexOrThrow("table"))
            if (from == "book_id" && refTable == "books") {
                fkFound = true
                assertEquals(
                    "CASCADE",
                    fkCursor.getString(fkCursor.getColumnIndexOrThrow("on_delete")),
                )
            }
        }
        fkCursor.close()
        assertTrue("reader_text_rules.book_id 外键应指向 books", fkFound)

        // 6. 四个显式索引存在
        val expectedIndexes = listOf(
            "index_reader_text_rules_kind",
            "index_reader_text_rules_scope",
            "index_reader_text_rules_book_id",
            "index_reader_text_rules_position",
        )
        for (indexName in expectedIndexes) {
            val idxCursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND name = ?",
                arrayOf(indexName),
            )
            assertTrue("索引 $indexName 应存在", idxCursor.moveToFirst())
            assertEquals(indexName, idxCursor.getString(0))
            idxCursor.close()
        }

        // 7. MigrationTestHelper 的原始 SQLite 连接不会替 Room 自动开启外键执行。
        // 显式开启后再验证 ON DELETE CASCADE 的运行时语义。
        db.execSQL("PRAGMA foreign_keys = ON")
        val fkEnabledCursor = db.query("PRAGMA foreign_keys")
        assertTrue(fkEnabledCursor.moveToFirst())
        assertEquals(1, fkEnabledCursor.getInt(0))
        fkEnabledCursor.close()

        // 删除书籍后其按书规则应被清掉
        db.execSQL("DELETE FROM books WHERE id = 'rule-book'")
        val orphanCursor = db.query(
            "SELECT COUNT(*) FROM reader_text_rules WHERE book_id = 'rule-book'",
        )
        assertTrue(orphanCursor.moveToFirst())
        assertEquals(0, orphanCursor.getInt(0))
        orphanCursor.close()

        db.close()
    }

    // ─── 9 → 10：chapter_reads 新表 + tags/shelves 补 sort_order ──────

    @Test
    @Throws(IOException::class)
    fun migrate_9_to_10_adds_chapter_reads_and_sort_orders() {
        // 1. 创建 v9 数据库并插入覆盖 tags/shelves/categories 的数据（v9 tags/shelves 无 sort_order，categories 有）
        var db = migrationTestHelper.createDatabase(TEST_DB, 9)

        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, description, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('mig10-book', 'P3.3书籍', '作者', 'epub', 'mig10.epub', 'hashmig10', " +
                "4096, '/uri/m10', '/content/m10', 'ready', NULL, NULL, " +
                "'2026-08-15T00:00:00Z', 'device-1', '{}', 1, '2026-08-15T00:00:00Z', NULL)",
        )
        // v9 tags：无 sort_order 列
        db.execSQL(
            "INSERT INTO tags (id, name, color, type, created_at, device_id, revision, payload, " +
                "updated_at, deleted_at) " +
                "VALUES ('mig10-tag', 'P3.3标签', NULL, 'book', '2026-08-15T00:00:00Z', 'device-1', 1, " +
                "'{}', '2026-08-15T00:00:00Z', NULL)",
        )
        // v9 categories：已有 sort_order 列（NOT NULL，必须显式给值）
        db.execSQL(
            "INSERT INTO categories (id, name, cover_tone, parent_id, sort_order, created_at, " +
                "device_id, revision, payload, updated_at, deleted_at) " +
                "VALUES ('mig10-cat', 'P3.3分类', NULL, NULL, 0, '2026-08-15T00:00:00Z', 'device-1', 1, " +
                "'{}', '2026-08-15T00:00:00Z', NULL)",
        )
        // v9 shelves：无 sort_order 列
        db.execSQL(
            "INSERT INTO shelves (id, name, created_at, device_id, revision, payload, updated_at, deleted_at) " +
                "VALUES ('mig10-shelf', 'P3.3书架', '2026-08-15T00:00:00Z', 'device-1', 1, '{}', " +
                "'2026-08-15T00:00:00Z', NULL)",
        )

        db.close()

        // 2. 执行迁移 9 → 10
        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 10, true, AppDatabase.MIGRATION_9_10,
        )

        // 3. 验证既有数据未丢失
        val booksCursor = db.query("SELECT title FROM books WHERE id = 'mig10-book'")
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("P3.3书籍", booksCursor.getString(0))
        booksCursor.close()

        val tagCursor = db.query("SELECT name FROM tags WHERE id = 'mig10-tag'")
        assertTrue("tags 数据应保留", tagCursor.moveToFirst())
        assertEquals("P3.3标签", tagCursor.getString(0))
        tagCursor.close()

        val catCursor = db.query("SELECT name, sort_order FROM categories WHERE id = 'mig10-cat'")
        assertTrue("categories 数据应保留", catCursor.moveToFirst())
        assertEquals("P3.3分类", catCursor.getString(0))
        assertEquals(0, catCursor.getInt(1))
        catCursor.close()

        val shelfCursor = db.query("SELECT name FROM shelves WHERE id = 'mig10-shelf'")
        assertTrue("shelves 数据应保留", shelfCursor.moveToFirst())
        assertEquals("P3.3书架", shelfCursor.getString(0))
        shelfCursor.close()

        // 4. 验证 tags.sort_order / shelves.sort_order 新列存在且默认值 0
        val tagSortCursor = db.query(
            "SELECT sort_order FROM tags WHERE id = 'mig10-tag'",
        )
        assertTrue("tags.sort_order 应存在", tagSortCursor.moveToFirst())
        assertEquals(0, tagSortCursor.getInt(0))
        tagSortCursor.close()

        val shelfSortCursor = db.query(
            "SELECT sort_order FROM shelves WHERE id = 'mig10-shelf'",
        )
        assertTrue("shelves.sort_order 应存在", shelfSortCursor.moveToFirst())
        assertEquals(0, shelfSortCursor.getInt(0))
        shelfSortCursor.close()

        // 5. 验证 chapter_reads 表存在 + 主键(book_id, chapter_index) + book_id 索引
        val tableCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='chapter_reads'",
        )
        assertTrue("chapter_reads 表应存在", tableCursor.moveToFirst())
        tableCursor.close()

        db.execSQL(
            "INSERT INTO chapter_reads (book_id, chapter_index, read_at) " +
                "VALUES ('mig10-book', 0, '2026-08-15T10:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO chapter_reads (book_id, chapter_index, read_at) " +
                "VALUES ('mig10-book', 1, '2026-08-15T10:05:00Z')",
        )
        // 主键冲突：(book_id, chapter_index) 重复应抛（先尝试 REPLACE 语义，不失败就证明 UNIQUE）
        db.execSQL(
            "INSERT OR REPLACE INTO chapter_reads (book_id, chapter_index, read_at) " +
                "VALUES ('mig10-book', 0, '2026-08-15T11:00:00Z')",
        )

        val readsCursor = db.query(
            "SELECT chapter_index, read_at FROM chapter_reads WHERE book_id = 'mig10-book' " +
                "ORDER BY chapter_index ASC",
        )
        assertTrue("chapter_reads 应有 2 行", readsCursor.moveToFirst())
        assertEquals(0, readsCursor.getInt(0))
        assertEquals("2026-08-15T11:00:00Z", readsCursor.getString(1)) // REPLACE 后应是更新值
        assertTrue("第 2 行应存在", readsCursor.moveToNext())
        assertEquals(1, readsCursor.getInt(0))
        readsCursor.close()

        val chReadsIdxCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name = 'index_chapter_reads_book_id'",
        )
        assertTrue("chapter_reads.book_id 索引应存在", chReadsIdxCursor.moveToFirst())
        chReadsIdxCursor.close()

        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate_9_to_10_assigns_distinct_stable_sort_orders_to_legacy_taxonomy_rows() {
        var db = migrationTestHelper.createDatabase(TEST_DB, 9)

        listOf(
            Triple("tag-late", "标签 C", "2026-08-15T03:00:00Z"),
            Triple("tag-early", "标签 A", "2026-08-15T01:00:00Z"),
            Triple("tag-middle", "标签 B", "2026-08-15T02:00:00Z"),
        ).forEach { (id, name, createdAt) ->
            db.execSQL(
                "INSERT INTO tags (id, name, color, type, created_at, device_id, revision, payload, " +
                    "updated_at, deleted_at) VALUES (?, ?, NULL, 'book', ?, 'device-1', 1, '{}', ?, NULL)",
                arrayOf(id, name, createdAt, createdAt),
            )
        }
        listOf(
            Triple("shelf-late", "书单 C", "2026-08-15T03:00:00Z"),
            Triple("shelf-early", "书单 A", "2026-08-15T01:00:00Z"),
            Triple("shelf-middle", "书单 B", "2026-08-15T02:00:00Z"),
        ).forEach { (id, name, createdAt) ->
            db.execSQL(
                "INSERT INTO shelves (id, name, created_at, device_id, revision, payload, updated_at, deleted_at) " +
                    "VALUES (?, ?, ?, 'device-1', 1, '{}', ?, NULL)",
                arrayOf(id, name, createdAt, createdAt),
            )
        }
        listOf(
            Triple("category-late", "分类 C", "2026-08-15T03:00:00Z"),
            Triple("category-early", "分类 A", "2026-08-15T01:00:00Z"),
            Triple("category-middle", "分类 B", "2026-08-15T02:00:00Z"),
        ).forEach { (id, name, createdAt) ->
            db.execSQL(
                "INSERT INTO categories (id, name, cover_tone, parent_id, sort_order, created_at, device_id, " +
                    "revision, payload, updated_at, deleted_at) " +
                    "VALUES (?, ?, NULL, NULL, 0, ?, 'device-1', 1, '{}', ?, NULL)",
                arrayOf(id, name, createdAt, createdAt),
            )
        }
        db.close()

        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 10, true, AppDatabase.MIGRATION_9_10,
        )

        assertStableDenseOrder(
            db = db,
            table = "tags",
            expectedIds = listOf("tag-early", "tag-middle", "tag-late"),
        )
        assertStableDenseOrder(
            db = db,
            table = "shelves",
            expectedIds = listOf("shelf-early", "shelf-middle", "shelf-late"),
        )
        assertStableDenseOrder(
            db = db,
            table = "categories",
            expectedIds = listOf("category-early", "category-middle", "category-late"),
        )

        db.close()
    }

    // ─── 10 → 11：全文搜索派生索引表 ─────────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate_10_to_11_adds_search_index_tables_without_touching_books() {
        var db = migrationTestHelper.createDatabase(TEST_DB, 10)
        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, description, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('migration-11-book', '测试 TXT', NULL, 'txt', 'sample.txt', 'hash-11', " +
                "1024, NULL, NULL, 'available', NULL, NULL, NULL, 'device-1', '{}', 1, " +
                "'2026-09-01T00:00:00Z', NULL)",
        )
        db.close()

        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 11, true, AppDatabase.MIGRATION_10_11,
        )

        val bookCursor = db.query("SELECT title FROM books WHERE id = 'migration-11-book'")
        assertTrue("既有书库数据应保留", bookCursor.moveToFirst())
        assertEquals("测试 TXT", bookCursor.getString(0))
        bookCursor.close()

        db.execSQL(
            "INSERT INTO search_terms (term, book_id, chapter_index, hits, offsets) " +
                "VALUES ('测试', 'migration-11-book', 0, 2, '0:2')",
        )
        db.execSQL(
            "INSERT INTO search_index_state (id, tokenizer_version, last_scanned_book_id, " +
                "last_scanned_chapter_index, built_at) VALUES (1, 2, 'migration-11-book', 0, 1)",
        )
        val indexCursor = db.query(
            "SELECT hits FROM search_terms WHERE term = '测试' AND book_id = 'migration-11-book'",
        )
        assertTrue("search_terms 应可读写", indexCursor.moveToFirst())
        assertEquals(2, indexCursor.getInt(0))
        indexCursor.close()

        val indexNames = listOf("index_search_terms_term", "index_search_terms_book")
        indexNames.forEach { name ->
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?", arrayOf(name))
            assertTrue("索引 $name 应存在", cursor.moveToFirst())
            cursor.close()
        }
        db.close()
    }

    private fun assertStableDenseOrder(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        expectedIds: List<String>,
    ) {
        val cursor = db.query("SELECT id, sort_order FROM $table ORDER BY sort_order ASC, created_at ASC, id ASC")
        val actualIds = mutableListOf<String>()
        val actualOrders = mutableListOf<Int>()
        while (cursor.moveToNext()) {
            actualIds += cursor.getString(0)
            actualOrders += cursor.getInt(1)
        }
        cursor.close()

        assertEquals(expectedIds, actualIds)
        assertEquals(expectedIds.indices.toList(), actualOrders)
    }

    // ─── 1 → 9 完整链路 ────────────────────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate_1_to_9_full_chain() {
        // 1. 创建 v1 数据库并插入综合测试数据（仅使用 v1 列定义）
        var db = migrationTestHelper.createDatabase(TEST_DB, 1)

        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('chain9-book', '全链路测试 EPUB', '全链路作者', 'epub', 'chain9.epub', " +
                "'hashchain9', 4096, '/uri/c9', '/content/c9', 'ready', NULL, " +
                "'2026-09-01T00:00:00Z', 'device-1', '{}', 1, '2026-09-01T00:00:00Z', NULL)",
        )

        db.execSQL(
            "INSERT INTO reading_progress (book_id, progress_percent, last_read_at, " +
                "total_reading_time_ms, completion_state, current_location_json, payload, " +
                "revision, device_id, updated_at, deleted_at) " +
                "VALUES ('chain9-book', 42.0, '2026-09-02T00:00:00Z', 3600000, 'in_progress', " +
                "'{\"chapter\":1}', '{}', 1, 'device-1', '2026-09-02T00:00:00Z', NULL)",
        )

        db.execSQL(
            "INSERT INTO highlights (id, book_id, text, note, color, locator_json, payload, " +
                "created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('chain9-hl', 'chain9-book', '全链路高亮', '笔记', 'yellow', " +
                "'{\"cfi\":\"/2/2\"}', '{}', '2026-09-03T00:00:00Z', 'device-1', 1, " +
                "'2026-09-03T00:00:00Z', NULL)",
        )

        db.close()

        // 2. 执行完整迁移链 1 → 9
        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 9, true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
        )

        // 3. 验证 books 数据完好
        val booksCursor = db.query(
            "SELECT title, description FROM books WHERE id = 'chain9-book'",
        )
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("全链路测试 EPUB", booksCursor.getString(0))
        assertTrue("description 应为 NULL", booksCursor.isNull(1))
        booksCursor.close()

        // 4. 验证 reading_progress 数据完好
        val progressCursor = db.query(
            "SELECT progress_percent, completed_at FROM reading_progress WHERE book_id = 'chain9-book'",
        )
        assertTrue("reading_progress 数据应保留", progressCursor.moveToFirst())
        assertEquals(42.0, progressCursor.getDouble(0), 0.001)
        assertTrue("completed_at 应为 NULL", progressCursor.isNull(1))
        progressCursor.close()

        // 5. 验证 highlights 数据完好
        val hlCursor = db.query(
            "SELECT text, chapter_title FROM highlights WHERE id = 'chain9-hl'",
        )
        assertTrue("highlights 数据应保留", hlCursor.moveToFirst())
        assertEquals("全链路高亮", hlCursor.getString(0))
        assertTrue("chapter_title 应为 NULL", hlCursor.isNull(1))
        hlCursor.close()

        // 6. 验证 reader_text_rules 表存在且可插入
        db.execSQL(
            "INSERT INTO reader_text_rules (id, kind, name, pattern, replacement, builtin, " +
                "enabled, scope, book_id, position, created_at, updated_at) " +
                "VALUES ('chain9-rule', 'TOC', '全链路目录规则', NULL, '', 0, 1, 'GLOBAL', NULL, 1, " +
                "1780000000000, 1780000000000)",
        )
        val ruleCursor = db.query(
            "SELECT kind, scope FROM reader_text_rules WHERE id = 'chain9-rule'",
        )
        assertTrue("reader_text_rules 数据应存在", ruleCursor.moveToFirst())
        assertEquals("TOC", ruleCursor.getString(0))
        assertEquals("GLOBAL", ruleCursor.getString(1))
        ruleCursor.close()

        // 7. 验证 reader_text_rules 的索引在完整迁移链末端存在
        val ruleIdxCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name = 'index_reader_text_rules_kind'",
        )
        assertTrue("索引 index_reader_text_rules_kind 应存在", ruleIdxCursor.moveToFirst())
        ruleIdxCursor.close()

        db.close()
    }

    // ─── 1 → 10 完整链路 ────────────────────────────────────────────────

    @Test
    @Throws(IOException::class)
    fun migrate_1_to_10_full_chain() {
        // 1. 创建 v1 数据库并插入综合测试数据（仅使用 v1 列定义）
        var db = migrationTestHelper.createDatabase(TEST_DB, 1)

        db.execSQL(
            "INSERT INTO books (id, title, author, format, original_file_name, content_hash, " +
                "size, local_uri, local_content_path, content_status, cover_data_url, " +
                "imported_at, device_id, payload, revision, updated_at, deleted_at) " +
                "VALUES ('chain10-book', '全链路v10 EPUB', '全链路作者', 'epub', 'chain10.epub', " +
                "'hashchain10', 4096, '/uri/c10', '/content/c10', 'ready', NULL, " +
                "'2026-10-01T00:00:00Z', 'device-x', '{}', 1, '2026-10-01T00:00:00Z', NULL)",
        )

        db.execSQL(
            "INSERT INTO reading_progress (book_id, progress_percent, last_read_at, " +
                "total_reading_time_ms, completion_state, current_location_json, payload, " +
                "revision, device_id, updated_at, deleted_at) " +
                "VALUES ('chain10-book', 55.0, '2026-10-02T00:00:00Z', 5400000, 'in_progress', " +
                "'{\"chapter\":3}', '{}', 1, 'device-x', '2026-10-02T00:00:00Z', NULL)",
        )

        db.execSQL(
            "INSERT INTO highlights (id, book_id, text, note, color, locator_json, payload, " +
                "created_at, device_id, revision, updated_at, deleted_at) " +
                "VALUES ('chain10-hl', 'chain10-book', '全链路v10高亮', '笔记', 'blue', " +
                "'{\"cfi\":\"/4/2\"}', '{}', '2026-10-03T00:00:00Z', 'device-x', 1, " +
                "'2026-10-03T00:00:00Z', NULL)",
        )

        db.close()

        // 2. 执行完整迁移链 1 → 10（传入全部 9 个迁移）
        db = migrationTestHelper.runMigrationsAndValidate(
            TEST_DB, 10, true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
        )

        // 3. 验证 books / reading_progress / highlights 历史数据完好
        val booksCursor = db.query(
            "SELECT title, description FROM books WHERE id = 'chain10-book'",
        )
        assertTrue("books 数据应保留", booksCursor.moveToFirst())
        assertEquals("全链路v10 EPUB", booksCursor.getString(0))
        assertTrue("description 应为 NULL", booksCursor.isNull(1))
        booksCursor.close()

        val progressCursor = db.query(
            "SELECT progress_percent, completed_at FROM reading_progress WHERE book_id = 'chain10-book'",
        )
        assertTrue("reading_progress 数据应保留", progressCursor.moveToFirst())
        assertEquals(55.0, progressCursor.getDouble(0), 0.001)
        assertTrue("completed_at 应为 NULL", progressCursor.isNull(1))
        progressCursor.close()

        val hlCursor = db.query(
            "SELECT text, chapter_title, progress_percent FROM highlights WHERE id = 'chain10-hl'",
        )
        assertTrue("highlights 数据应保留", hlCursor.moveToFirst())
        assertEquals("全链路v10高亮", hlCursor.getString(0))
        assertTrue("chapter_title 应为 NULL", hlCursor.isNull(1))
        assertTrue("progress_percent 应为 NULL", hlCursor.isNull(2))
        hlCursor.close()

        // 4. 验证 chapter_reads 表存在（P3.3 片 1 新增）
        val tablesCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name = 'chapter_reads'",
        )
        assertTrue("chapter_reads 应存在于 1→10 全链末端", tablesCursor.moveToFirst())
        tablesCursor.close()

        // 5. 验证 tags/shelves 可写入且带 sort_order
        db.execSQL(
            "INSERT INTO tags (id, name, color, type, sort_order, created_at, device_id, " +
                "revision, payload, updated_at, deleted_at) " +
                "VALUES ('chain10-tag', 'v10标签', NULL, 'book', 5, '2026-10-04T00:00:00Z', " +
                "'device-x', 1, '{}', '2026-10-04T00:00:00Z', NULL)",
        )
        db.execSQL(
            "INSERT INTO shelves (id, name, sort_order, created_at, device_id, revision, " +
                "payload, updated_at, deleted_at) " +
                "VALUES ('chain10-shelf', 'v10书架', 7, '2026-10-04T00:00:00Z', 'device-x', 1, " +
                "'{}', '2026-10-04T00:00:00Z', NULL)",
        )
        val tagSort = db.query("SELECT sort_order FROM tags WHERE id = 'chain10-tag'")
        assertTrue("tag 应插入", tagSort.moveToFirst())
        assertEquals(5, tagSort.getInt(0))
        tagSort.close()
        val shelfSort = db.query("SELECT sort_order FROM shelves WHERE id = 'chain10-shelf'")
        assertTrue("shelf 应插入", shelfSort.moveToFirst())
        assertEquals(7, shelfSort.getInt(0))
        shelfSort.close()

        // 6. 验证 chapter_reads.book_id 索引存在（全链末端）
        val chIdx = db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name = 'index_chapter_reads_book_id'",
        )
        assertTrue("chapter_reads.book_id 索引应存在", chIdx.moveToFirst())
        chIdx.close()

        db.close()
    }
}
