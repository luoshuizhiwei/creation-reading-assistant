package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.creationreadingassistant.data.local.entity.ReaderPageIndexEntity

@Dao
interface ReaderPageIndexDao {

    @Query(
        "SELECT * FROM reader_page_index " +
            "WHERE content_key = :contentKey AND chapter_index = :chapterIndex AND fingerprint = :fingerprint",
    )
    suspend fun get(contentKey: String, chapterIndex: Int, fingerprint: Int): ReaderPageIndexEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReaderPageIndexEntity)

    /** 一本书换了排版配置后，旧指纹的索引就没用了。每本最多留两个指纹。 */
    @Query(
        "DELETE FROM reader_page_index WHERE content_key = :contentKey AND fingerprint NOT IN " +
            "(SELECT DISTINCT fingerprint FROM reader_page_index WHERE content_key = :contentKey " +
            "ORDER BY created_at DESC LIMIT 2)",
    )
    suspend fun pruneOldFingerprints(contentKey: String)

    @Query("DELETE FROM reader_page_index WHERE content_key = :contentKey")
    suspend fun clearBook(contentKey: String)

    @Query("DELETE FROM reader_page_index")
    suspend fun clearAll()

    /** 每章只取最近一次真实 char_count，供 EPUB 学习比率进度跨会话复用。 */
    @Query(
        "SELECT * FROM reader_page_index WHERE content_key = :contentKey " +
            "ORDER BY created_at DESC",
    )
    suspend fun listRecent(contentKey: String): List<ReaderPageIndexEntity>
}
