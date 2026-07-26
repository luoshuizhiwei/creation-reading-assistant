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
import com.creationreadingassistant.data.local.entity.SyncStateEntity
import com.creationreadingassistant.data.local.entity.TagEntity

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
    ],
    version = 4,
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

    companion object {
        const val DB_NAME = "creation_reading_assistant_native"

        /** v2→v3：为 books 表补 description 一列（非破坏迁移，保留既有数据）。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN description TEXT")
            }
        }

        /** v3→v4：为 reading_progress 表补 completed_at 一列（对齐网页 completedAt，用于「已读完」排序）。非破坏迁移。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN completed_at INTEGER")
            }
        }

        /** v1→v2：为高亮表补 chapter_title / progress_percent 两列（非破坏迁移，保留既有数据）。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE highlights ADD COLUMN chapter_title TEXT")
                db.execSQL("ALTER TABLE highlights ADD COLUMN progress_percent REAL")
                // 升级用户也需要补建部分索引（与 CreateIndexCallback.onCreate 保持一致）
                PARTIAL_INDEX_SQL.forEach { sql ->
                    runCatching { db.execSQL(sql) }
                        .onFailure { e ->
                            Log.e("AppDatabase", "迁移建索引失败（已忽略）: $sql", e)
                            AppLog.w("DB", "迁移建索引失败（已忽略）: ${e.message}")
                        }
                }
            }
        }

        /** V2 部分索引（Room 注解不支持，建库时手工补建） */
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

    class CreateIndexCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // 部分索引为性能优化项；个别设备/列定义差异不应拖垮首启开库，故逐条容错。
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
