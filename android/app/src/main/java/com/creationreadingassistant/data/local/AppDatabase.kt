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
import com.creationreadingassistant.data.local.dao.ReaderAnchorCacheDao
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.dao.SyncStateDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.dao.SearchIndexStateDao
import com.creationreadingassistant.data.local.dao.SearchIndexStateRow
import com.creationreadingassistant.data.local.dao.SearchTermDao
import com.creationreadingassistant.data.local.dao.SearchTermRow
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
import com.creationreadingassistant.data.local.entity.ReaderAnchorCacheEntity
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import com.creationreadingassistant.data.local.entity.SyncStateEntity
import com.creationreadingassistant.data.local.entity.TagEntity
import com.creationreadingassistant.data.local.entity.ChapterReadEntity

/**
 * 数据库 schema 版本。唯一真源 —— [AppDatabase] 的 `@Database(version)` 与
 * [DatabaseSafetyNet] 的升级判断都读它，改版本号只改这一处。
 *
 * 提成顶层 const 而不是放进 companion，是因为注解参数必须是编译期常量，
 * 而在 `@Database` 上引用被注解类自己的嵌套常量会构成循环引用。
 */
const val APP_DATABASE_SCHEMA_VERSION = 11

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
 *  - v4→v5：新增分页索引缓存表 reader_page_index（MIGRATION_4_5）
 *  - v5→v6：新增 locator 懒解析缓存表 reader_anchor_cache（MIGRATION_5_6）
 *  - v6→v7：reading_sessions 补时间索引 idx_sessions_created（MIGRATION_6_7，
 *    部分索引，仅 onOpen 重建，不改任何表结构）
 *  - v7→v8：为全部外键列补 Room 声明索引（MIGRATION_7_8），消除 KSP
 *    「外键未索引」警告与父表更新/删除时的全表扫描
 *  - v8→v9：新增阅读器文本规则表 reader_text_rules（MIGRATION_8_9），
 *    目录/替换规则的持久化底座，不接入任何读取路径
 *  - v9→v10：新增章节已读表 chapter_reads（片 1）；tags/shelves 两表补
 *    sort_order 列并统一 ORDER BY sort_order ASC, created_at ASC 兜底排序（片 3）
 *  - v10→v11：新增本地全文索引表 search_terms 与断点表 search_index_state；
 *    只新增派生数据，绝不修改书库、进度或阅读批注
 * exportSchema = true：schema 导出到 app/schemas/，供 MigrationTestHelper 校验。
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
        ReaderAnchorCacheEntity::class,
        ReaderTextRuleEntity::class,
        ChapterReadEntity::class,
        SearchTermRow::class,
        SearchIndexStateRow::class,
    ],
    version = APP_DATABASE_SCHEMA_VERSION,
    exportSchema = true,
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
    abstract fun readerAnchorCacheDao(): ReaderAnchorCacheDao
    abstract fun readerTextRuleDao(): ReaderTextRuleDao
    abstract fun chapterReadDao(): ChapterReadDao
    abstract fun searchTermDao(): SearchTermDao
    abstract fun searchIndexStateDao(): SearchIndexStateDao

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
         * DatabaseModule 已移除 fallbackToDestructiveMigration()；
         * 迁移失败会抛异常，因此必须保证所有历史版本都有正确 Migration 和测试覆盖。
         * 只新建表的话，最坏情况仅仅是丢掉缓存。
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

        /** v5→v6：仅新增 locator 懒解析缓存；不修改任何用户内容表。 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropPartialIndexes(db)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reader_anchor_cache` (" +
                        "`kind` TEXT NOT NULL, " +
                        "`entity_id` TEXT NOT NULL, " +
                        "`content_key` TEXT NOT NULL, " +
                        "`chapter_index` INTEGER NOT NULL, " +
                        "`char_offset` INTEGER NOT NULL, " +
                        "`confidence` INTEGER NOT NULL, " +
                        "`resolved_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`kind`, `entity_id`, `content_key`))",
                )
            }
        }

        /**
         * v6→v7：为 reading_sessions 补时间维度索引（idx_sessions_created，见 [PARTIAL_INDEX_SQL]），
         * 支撑今日/7日/30日阅读时长的 SQL 聚合下推。**不 ALTER 任何表、不新增列。**
         *
         * 与 MIGRATION_1_2 同理：索引统一交给 [CreateIndexCallback] onOpen 重建 ——
         * 在迁移里建会让紧随其后的 Room 表结构校验因「多出未知索引」而失败。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropPartialIndexes(db)
            }
        }

        /**
         * v7→v8：为全部外键列补 Room 声明索引（A8）。
         *
         * 消除两类实际问题：
         * 1. KSP 的「外键列未索引」警告 —— 父表更新/删除时 SQLite 外键约束需要
         *    反向查子表，无索引就是全表扫描；
         * 2. 手工部分索引（[PARTIAL_INDEX_SQL]）带 `WHERE deleted_at IS NULL`，
         *    Room 校验不认，也不覆盖软删除行 —— 外键完整性不能依赖它们。
         *
         * 索引名必须与 Room 为 @Index 注解生成的完全一致（`index_<表>_<列>`），
         * 否则迁移后的表结构校验会因「期望有索引但没建」而失败。
         * 与既有迁移一致：先 [dropPartialIndexes] 再建，onOpen 会重建部分索引。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropPartialIndexes(db)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_book_id` ON `reading_sessions` (`book_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inspirations_source_book_id` ON `inspirations` (`source_book_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inspiration_variants_inspiration_id` ON `inspiration_variants` (`inspiration_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_book_id` ON `notes` (`book_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_inspiration_id` ON `notes` (`inspiration_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_highlights_book_id` ON `highlights` (`book_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_tag_tag_id` ON `book_tag` (`tag_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_category_category_id` ON `book_category` (`category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shelf_book_book_id` ON `shelf_book` (`book_id`)")
            }
        }

        /**
         * v8→v9：新增阅读器文本规则表 reader_text_rules。
         *
         * 只建新表，不 ALTER 任何现有表。建表语句必须与 Room 为
         * [ReaderTextRuleEntity] 生成的完全一致（列顺序、NOT NULL、主键、外键、
         * 索引），否则迁移后的表结构校验会失败。索引名与 @Index 注解生成的
         * `index_<表>_<列>` 完全一致。
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropPartialIndexes(db)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reader_text_rules` (" +
                        "`id` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`pattern` TEXT, " +
                        "`replacement` TEXT NOT NULL, " +
                        "`builtin` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, " +
                        "`scope` TEXT NOT NULL, " +
                        "`book_id` TEXT, " +
                        "`position` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`book_id`) REFERENCES `books`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reader_text_rules_kind` ON `reader_text_rules` (`kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reader_text_rules_scope` ON `reader_text_rules` (`scope`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reader_text_rules_book_id` ON `reader_text_rules` (`book_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reader_text_rules_position` ON `reader_text_rules` (`position`)")
            }
        }

        /**
         * v9→v10：P3.3 片 1 + 片 3 合并迁移包。
         *
         * 片 1：新增 chapter_reads 表（EPUB/Markdown 章节已读持久化）。
         * 列顺序、主键、索引名必须与 [ChapterReadEntity] + Room KSP 生成一致，
         * 否则 schema 校验失败。
         *
         * 片 3：为 tags/shelves 补 sort_order 列，并为三类既有数据生成稳定且唯一的顺序。
         * SQLite ALTER TABLE ADD COLUMN 带 NOT NULL 必须有 DEFAULT，
         * 故 DEFAULT 0 与 [TagEntity]/[ShelfEntity] 构造默认值对齐。
         * 若只保留 DEFAULT 0，相邻项交换两个相同值不会改变顺序；因此迁移时按旧
         * `sort_order`（分类）/ `created_at` + `id`（标签、书单）一次性生成稠密序号。
         * 日常上移/下移仍然只交换相邻两项，不做全表重排。
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropPartialIndexes(db)
                // 片 3：tags/shelves 补排序列
                db.execSQL("ALTER TABLE tags ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shelves ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
                backfillLegacyTaxonomySortOrders(db)
                // 片 1：chapter_reads 建表 + 索引
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chapter_reads` (" +
                        "`book_id` TEXT NOT NULL, " +
                        "`chapter_index` INTEGER NOT NULL, " +
                        "`read_at` TEXT NOT NULL, " +
                        "PRIMARY KEY(`book_id`, `chapter_index`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chapter_reads_book_id` " +
                        "ON `chapter_reads` (`book_id`)",
                )
            }
        }

        /**
         * v10→v11：本地全文搜索的派生索引与增量扫描断点。
         *
         * 两表均不持有用户正文：search_terms 只存分词、命中次数和可选短偏移；
         * search_index_state 只存构建进度。迁移仅 CREATE TABLE/INDEX，不触碰既有数据。
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dropPartialIndexes(db)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `search_terms` (" +
                        "`term` TEXT NOT NULL, " +
                        "`book_id` TEXT NOT NULL, " +
                        "`chapter_index` INTEGER NOT NULL, " +
                        "`hits` INTEGER NOT NULL DEFAULT 0, " +
                        "`offsets` TEXT, " +
                        "PRIMARY KEY(`term`, `book_id`, `chapter_index`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_search_terms_term` " +
                        "ON `search_terms` (`term`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_search_terms_book` " +
                        "ON `search_terms` (`book_id`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `search_index_state` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`tokenizer_version` INTEGER NOT NULL DEFAULT 0, " +
                        "`last_scanned_book_id` TEXT, " +
                        "`last_scanned_chapter_index` INTEGER NOT NULL DEFAULT 0, " +
                        "`built_at` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`id`))",
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

        /**
         * 为 v9 既有标签、书单、分类快照稳定顺序，再写回唯一的 0..N-1 序号。
         *
         * 使用临时表是为了避免直接 UPDATE categories 时，相关子查询读到同一语句
         * 已经更新过的 sort_order。相关 COUNT 子查询不依赖窗口函数，可覆盖项目支持
         * 的旧 Android SQLite 版本。
         */
        private fun backfillLegacyTaxonomySortOrders(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TEMP TABLE `_migration_taxonomy_sort_order` (" +
                    "`kind` TEXT NOT NULL, `id` TEXT NOT NULL, `target_order` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`kind`, `id`))",
            )
            db.execSQL(
                "INSERT INTO `_migration_taxonomy_sort_order` (`kind`, `id`, `target_order`) " +
                    "SELECT 'tag', current.id, (SELECT COUNT(*) FROM tags preceding " +
                    "WHERE preceding.created_at < current.created_at " +
                    "OR (preceding.created_at = current.created_at AND preceding.id < current.id)) " +
                    "FROM tags current",
            )
            db.execSQL(
                "INSERT INTO `_migration_taxonomy_sort_order` (`kind`, `id`, `target_order`) " +
                    "SELECT 'shelf', current.id, (SELECT COUNT(*) FROM shelves preceding " +
                    "WHERE preceding.created_at < current.created_at " +
                    "OR (preceding.created_at = current.created_at AND preceding.id < current.id)) " +
                    "FROM shelves current",
            )
            db.execSQL(
                "INSERT INTO `_migration_taxonomy_sort_order` (`kind`, `id`, `target_order`) " +
                    "SELECT 'category', current.id, (SELECT COUNT(*) FROM categories preceding " +
                    "WHERE preceding.sort_order < current.sort_order " +
                    "OR (preceding.sort_order = current.sort_order AND preceding.created_at < current.created_at) " +
                    "OR (preceding.sort_order = current.sort_order AND preceding.created_at = current.created_at " +
                    "AND preceding.id < current.id)) FROM categories current",
            )
            db.execSQL(
                "UPDATE tags SET sort_order = (SELECT target_order FROM `_migration_taxonomy_sort_order` " +
                    "WHERE kind = 'tag' AND id = tags.id)",
            )
            db.execSQL(
                "UPDATE shelves SET sort_order = (SELECT target_order FROM `_migration_taxonomy_sort_order` " +
                    "WHERE kind = 'shelf' AND id = shelves.id)",
            )
            db.execSQL(
                "UPDATE categories SET sort_order = (SELECT target_order FROM `_migration_taxonomy_sort_order` " +
                    "WHERE kind = 'category' AND id = categories.id)",
            )
            db.execSQL("DROP TABLE `_migration_taxonomy_sort_order`")
        }

        /** 与 [PARTIAL_INDEX_SQL] 一一对应，改一处必须改另一处。 */
        val PARTIAL_INDEX_NAMES: List<String> = listOf(
            "idx_books_updated", "idx_books_hash", "idx_sessions_book",
            "idx_sessions_created",
            "idx_notes_book", "idx_notes_insp", "idx_highlights_book",
            "idx_insp_source", "idx_variants_insp", "idx_book_tag_tag",
            "idx_shelf_book_book",
        )

        /** V2 部分索引（Room 注解不支持，故手工建；只在 onOpen 建，见 CreateIndexCallback） */
        val PARTIAL_INDEX_SQL: List<String> = listOf(
            "CREATE INDEX IF NOT EXISTS idx_books_updated ON books(updated_at) WHERE deleted_at IS NULL;",
            "CREATE INDEX IF NOT EXISTS idx_books_hash ON books(content_hash);",
            "CREATE INDEX IF NOT EXISTS idx_sessions_book ON reading_sessions(book_id) WHERE deleted_at IS NULL;",
            // v7：时间维度索引，服务 created_at >= ? 的聚合下推；reading_sessions 有软删除，
            // 与 idx_sessions_book 同样带 WHERE deleted_at IS NULL 条件。
            "CREATE INDEX IF NOT EXISTS idx_sessions_created ON reading_sessions(created_at) WHERE deleted_at IS NULL;",
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
