package com.creationreadingassistant.data.local

import android.content.Context
import androidx.room.Room
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
import com.creationreadingassistant.data.local.dao.ReaderPageIndexDao
import com.creationreadingassistant.data.local.dao.ReaderAnchorCacheDao
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.dao.ShelfDao
import com.creationreadingassistant.data.local.dao.SyncAccountDao
import com.creationreadingassistant.data.local.dao.SyncStateDao
import com.creationreadingassistant.data.local.dao.TagDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 数据层装配：提供单例 AppDatabase 与各 DAO。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        // 必须在 build() 之前：build() 不打开库，但第一次查询就会触发 onUpgrade，
        // 所以下面的 snapshotIfUpgrading 会在升级前备份数据库快照。
        // 迁移 1→2、2→3、3→4、4→5、5→6、6→7、7→8、8→9、9→10 均已定义；
        // 测试覆盖：1→2、2→3、3→4、4→5、5→6、6→7、7→8、8→9、9→10、1→10 全链路。
        // 迁移失败时应崩溃并报明确错误，而非静默擦除用户数据。
        DatabaseSafetyNet.snapshotIfUpgrading(context, AppDatabase.DB_NAME, AppDatabase.SCHEMA_VERSION)
        return Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .addCallback(AppDatabase.CreateIndexCallback())
            .addMigrations(
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
            .build()
    }

    @Provides fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()
    @Provides fun provideBookContentDao(db: AppDatabase): BookContentDao = db.bookContentDao()
    @Provides fun provideBookFileDao(db: AppDatabase): BookFileDao = db.bookFileDao()
    @Provides fun provideReadingProgressDao(db: AppDatabase): ReadingProgressDao = db.readingProgressDao()
    @Provides fun provideReadingSessionDao(db: AppDatabase): ReadingSessionDao = db.readingSessionDao()
    @Provides fun provideInspirationDao(db: AppDatabase): InspirationDao = db.inspirationDao()
    @Provides fun provideInspirationVariantDao(db: AppDatabase): InspirationVariantDao = db.inspirationVariantDao()
    @Provides fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides fun provideHighlightDao(db: AppDatabase): HighlightDao = db.highlightDao()
    @Provides fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
    @Provides fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideShelfDao(db: AppDatabase): ShelfDao = db.shelfDao()
    @Provides fun provideBookTagDao(db: AppDatabase): BookTagDao = db.bookTagDao()
    @Provides fun provideBookCategoryDao(db: AppDatabase): BookCategoryDao = db.bookCategoryDao()
    @Provides fun provideShelfBookDao(db: AppDatabase): ShelfBookDao = db.shelfBookDao()
    @Provides fun provideSyncAccountDao(db: AppDatabase): SyncAccountDao = db.syncAccountDao()
    @Provides fun provideSyncStateDao(db: AppDatabase): SyncStateDao = db.syncStateDao()
    @Provides fun provideReaderPageIndexDao(db: AppDatabase): ReaderPageIndexDao = db.readerPageIndexDao()
    @Provides fun provideReaderAnchorCacheDao(db: AppDatabase): ReaderAnchorCacheDao = db.readerAnchorCacheDao()
    @Provides fun provideReaderTextRuleDao(db: AppDatabase): ReaderTextRuleDao = db.readerTextRuleDao()
    @Provides fun provideChapterReadDao(db: AppDatabase): ChapterReadDao = db.chapterReadDao()
}
