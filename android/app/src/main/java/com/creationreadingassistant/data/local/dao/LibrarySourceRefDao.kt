package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity

/**
 * 来源引用的持久化入口。写入只发生在 `ShelfImporter` 导入成功与目录观测刷新两个场景；
 * 没有定时轮询，也不会为了观测去打开文件内容。
 */
@Dao
interface LibrarySourceRefDao {

    @Upsert
    suspend fun upsert(ref: LibrarySourceRefEntity)

    @Query("SELECT * FROM library_source_refs")
    suspend fun getAll(): List<LibrarySourceRefEntity>

    @Query("SELECT * FROM library_source_refs WHERE candidate_fingerprint = :fingerprint")
    suspend fun getByFingerprint(fingerprint: String): List<LibrarySourceRefEntity>

    /** 观测刷新：来源仍可见时更新 last_seen_at 并翻回 available。 */
    @Query(
        "UPDATE library_source_refs SET last_seen_at = :nowMillis, availability = :availability " +
            "WHERE book_id IN (:bookIds)",
    )
    suspend fun updateObservation(bookIds: List<String>, nowMillis: Long, availability: String)

    /** 来源失效：只在一次完整（未截断）扫描的结论下调用。 */
    @Query("UPDATE library_source_refs SET availability = :availability WHERE book_id IN (:bookIds)")
    suspend fun updateAvailability(bookIds: List<String>, availability: String)

    /** 观测到 null root_id 的来源位于当前授权树内时，回填真实归属。 */
    @Query(
        "UPDATE library_source_refs SET root_id = :rootId " +
            "WHERE provider_authority = :authority AND document_id IN (:documentIds) AND root_id IS NULL",
    )
    suspend fun backfillRootId(rootId: String, authority: String, documentIds: List<String>)
}
