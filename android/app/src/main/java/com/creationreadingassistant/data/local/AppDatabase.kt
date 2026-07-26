package com.creationreadingassistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.CategoryDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.InspirationVariantDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.SyncAccountDao
import com.creationreadingassistant.data.local.dao.ReaderPageIndexDao
import com.creationreadingassistant.data.local.dao.SyncStateDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.BookCategoryEntity
import com.creationreadingassistant.data.local.entity.BookTagEntity
import com.creationreadingassistant.data.local.entity.CategoryEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.local.entity.ShelfBookEntity
import com.creationreadingassistant.data.local.entity.ShelfEntity
import com.creationreadingassistant.data.local.entity.SyncAccountEntity
import com.creationreadingassistant.data.local.entity.ReaderPageIndexEntity
import com.creationreadingassistant.data.local.entity.SyncStateEntity
import com.creationreadingassistant.data.local.entity.TagEntity

/**
 * 数据库 schema 版本。唯一真源 —— [AppDatabase] 的 `@Database(version)` 与
 * [DatabaseSafetyNet] 的升级判断都读它，改版本号只改这一处。
 *
 * 提成顶层 const 而不是放进 companion，是因为注解参数必须是编译期常量，
 * 而在 `@Database` 上引用被注解类自己的嵌套常量会构成循环引用。
 */
const val APP_DATABASE_SCHEMA_VERSION = 5

/**
 * 原生端 Room 数据库（v1）。
 *
 * 严格对齐 mobile/src/storage/mobile-schema.ts 的 V2 schema（17 张表）。
 * 注意：V2 含「部分索引」(WHERE deleted_at IS NULL)，Room @Index 注解不支持，
 * 因此在 [CreateIndexCallback] 中于建库时补建这些索引，保证与现有同步查询语义一致。
 *
 * 版本历史：
 *  - v1→v2：高亮表补 chapter_title / progress_percent（MIGRATION_1_2）
 *  - v2→v3：books 表补 description（MIGRATION_2_3）
 *  - v3→v4：reading_progress 表补 completed_at（MIGRATION_3_4，对应网页 completedAt）
 * 本机仅存在 v2 的 schema 导出文件，故关闭 exportSchema 以免缺失中间版本
 * schema 文件导致 Room 迁移校验失败（行为不受影响，运行时仍按表结构校验）。
 */
@Database(
    entities = [
        BookEntity::class, BookContentEntity::class, BookFileEntity::class,
        ReadingProgressEntity::class, ReadingSessionEntity::class,
        InspirationEntity::class, InspirationVariantEntity::class,
        NoteEntity::class, HighlightEntity::class,
        TagEntity::class, CategoryEntity::class, ShelfEntity::class,
        BookTagEntity::class, BookCategoryEntity::class, ShelfBookEntity::class,
        SyncAccountEntity::class, SyncStateEntity::class,
        ReaderPageIndexEntity::class,
    ],
    version = APP_DATABASE_SCHEMA_VERSION,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookContentDao(): BookContentDao
    abstract fun bookFileDao(): BookFileDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun inspirationDao(): InspirationDao
    abstract fun inspirationVariantDao(): InspirationVariantDao
    abstract fun noteDao(): NoteDao
    abstract fun highlightDao(): HighlightDao
    abstract fun tagDao(): TagDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shelfDao(): ShelfDao
    abstract fun bookTagDao(): BookTagDao
    abstract fun bookCategoryDao(): BookCategoryDao
    abstract fun shelfBookDao(): ShelfBookDao
    abstract fun syncAccountDao(): SyncAccountDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun readerPageIndexDao(): ReaderPageIndexDao

    companion object {
        const val DB_NAME = "creation_reading_assistant_native"

        /** 见顶层 [APP_DATABASE_SCHEMA_VERSION]。此处仅为调用方提供一个更好找的名字。 */
        const val SCHEMA_VERSION = APP_DATABASE_SCHEMA_VERSION

        /** v2→v3：为 books 表补 description 一列（非破坏迁移，保留既有数据）。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropPartialIndexes(db)
                db.execSQL("ALTER TABLE books ADD COLUMN description TEXT")
            }
        }

        /** v3→v4：为 reading_progress 表补 completed_at 一列（对齐网页 completedAt，用于「已读完」排序）。非破坏迁移。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropPartialIndexes(db)
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN completed_at INTEGER")
            }
        }

        /**
         * v4→v5：新增分页索引缓存表 reader_page_index。
         *
         * **只做 CREATE TABLE IF NOT EXISTS，绝不 ALTER 任何现有表。**
         * `DatabaseModule` 上挂着 `fallbackToDestructiveMigration()`，任何迁移失误
         * 都会被静默转成「清库重建」—— 用户全部书籍、进度、高亮、笔记、灵感、
         * 同步状态一次归零，而且不报错。只新建表的话，最坏情况仅仅是丢掉缓存。
         *
         * 建表语句必须与 Room 为 [ReaderPageIndexEntity] 生成的完全一致
         * （列顺序、NOT NULL、主键），否则打开时的 schema 校验会失败。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropPartialIndexes(db)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reader_page_index` (" +
                        "`content_key` TEXT NOT NULL, " +
                        "`chapter_index` INTEGER NOT NULL, " +
                        "`fingerprint` INTEGER NOT NULL, " +
                        "`page_starts` BLOB NOT NULL, " +
                        "`char_count` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`content_key`, `chapter_index`, `fingerprint`))",
                )
            }
        }

        /** v1→v2：为高亮表补 chapter_title / progress_percent 两列（非破坏迁移，保留既有数据）。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropPartialIndexes(db)
                db.execSQL("ALTER TABLE highlights ADD COLUMN chapter_title TEXT")
                db.execSQL("ALTER TABLE highlights ADD COLUMN progress_percent REAL")
                // 索引统一交给 CreateIndexCallback.onOpen 重建 ——
                // 在迁移里建会让紧随其后的 Room 表结构校验因「多出未知索引」而失败。
            }
        }

        /**
         * 迁移前必须先删掉所有手工建的部分索引。
         *
         * Room 在迁移后会校验表结构，而它不认识这些索引（`WHERE deleted_at IS NULL`
         * 无法用 `@Index` 表达），多出来就判为 schema 不一致并抛异常。
         * 删掉后校验通过，[CreateIndexCallback] 会在 onOpen 里立刻重建，索引不会真的丢。
         *
         * **新增 Migration 时，第一行就调用它。**
         */
        fun dropPartialIndexes(db: SupportSQLiteDatabase) {
            PARTIAL_INDEX_NAMES.forEach { name ->
                runCatching { db.execSQL("DROP INDEX IF EXISTS $name") }
                    .onFailure { e -> Log.e("AppDatabase", "删索引失败（已忽略）: $name", e) }
            }
        }

        /** 与 [PARTIAL_INDEX_SQL] 一一对应，改一处必须改另一处。 */
        val PARTIAL_INDEX_NAMES: List<String> = listOf(
            "idx_books_updated", "idx_books_hash", "idx_sessions_book",
            "idx_notes_book", "idx_notes_insp", "idx_highlights_book",
            "idx_insp_source", "idx_variants_insp", "idx_book_tag_tag",
            "idx_shelf_book_book",
        )

        /** V2 部分索引（Room 注解不支持，故手工建；只在 onOpen 建，见 CreateIndexCallback） */
        val PARTIAL_INDEX_SQL: List<String> = listOf(
            "CREATE INDEX IF NOT EXISTS idx_books_updated ON books(updated_at) WHERE deleted_at IS NULL;",
            "CREATE INDEX IF NOT EXISTS idx_books_hash ON books(content_hash);",
            "CREATE INDEX IF NOT EXISTS idx_sessions_book ON reading_sessions(book_id) WHERE deleted_at IS NULL;",
            "CREATE INDEX IF NOT EXISTS idx_notes_book ON notes(book_id) WHERE deleted_at IS NULL;",
            "CREATE INDEX IF NOT EXISTS idx_notes_insp ON notes(inspiration_id) WHERE deleted_at IS NULL;",
            "CREATE INDEX IF NOT EXISTS idx_highlights_book ON highlights(book_id) WHERE deleted_at IS NULL;",
            "CREATE INDEX IF NOT EXISTS idx_insp_source ON inspirations(source_book_id) WHERE deleted_at IS NULL;",
            "CREATE INDEX IF NOT EXISTS idx_variants_insp ON inspiration_variants(inspiration_id);",
            "CREATE INDEX IF NOT EXISTS idx_book_tag_tag ON book_tag(tag_id);",
            "CREATE INDEX IF NOT EXISTS idx_shelf_book_book ON shelf_book(book_id);",
        )
    }

    /**
     * 部分索引的建立时机：**只在 onOpen，不在 onCreate / 迁移里**。
     *
     * 原因是 Room 的表结构校验会把「数据库里多出 Room 不认识的索引」判为不一致。
     * 这些部分索引带 `WHERE deleted_at IS NULL`，Room 的 `@Index` 注解表达不了，
     * 所以只能手工建 —— 于是每次迁移后的校验都会失败：
     *
     *     Migration didn't properly handle: books(BookEntity)
     *     Expected: indices=[]
     *     Found:    indices=[idx_books_hash, idx_books_updated]
     *
     * 而校验失败会抛 IllegalStateException，应用启动即崩溃。
     * 这个坑潜伏在任何一次未来的迁移里，v4→v5 只是第一个踩到的。
     *
     * 时序上 Room 是：onCreate/onUpgrade（含校验）→ onOpen。
     * 所以放在 onOpen 建，校验永远看不到它们，而索引照常存在、照常生效。
     * 配套地，每个 Migration 开头都要先 [dropPartialIndexes]。
     */
    class CreateIndexCallback : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // 部分索引为性能优化项；个别设备/列定义差异不应拖垮开库，故逐条容错。
            PARTIAL_INDEX_SQL.forEach { sql ->
                runCatching { db.execSQL(sql) }
                    .onFailure { e ->
                        Log.e("AppDatabase", "建索引失败（已忽略，不影响启动）: $sql", e)
                        AppLog.w("DB", "建索引失败（已忽略）: ${e.message}")
                    }
            }
        }
    }
}
