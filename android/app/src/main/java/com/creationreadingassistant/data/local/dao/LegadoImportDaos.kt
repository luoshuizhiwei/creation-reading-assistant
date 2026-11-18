package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.creationreadingassistant.data.local.entity.LegadoBookBindingEntity
import com.creationreadingassistant.data.local.entity.LegadoExternalBookSnapshotEntity
import com.creationreadingassistant.data.local.entity.LegadoExternalDaySnapshotEntity
import com.creationreadingassistant.data.local.entity.LegadoExternalSessionEntity
import com.creationreadingassistant.data.local.entity.LegadoImportBatchEntity
import com.creationreadingassistant.data.local.entity.LegadoImportBatchItemEntity
import com.creationreadingassistant.data.local.entity.LegadoImportSourceEntity
import com.creationreadingassistant.data.local.entity.LegadoImportSubjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * Legado 导入独立账本的全部读写（LEGADO-IMPORT-PLAN-v2 §3/§9）。
 *
 * 幂等与归属语义：
 *  - 会话/快照新建一律 [Insert][Insert.onConflict] = IGNORE：已存在 = 记引用
 *    （`*_ref` 批次项）而不改 created_by_batch；同键异值由管线判定为快照冲突。
 *  - 撤销按批次项逐类回退；外部新建行仅在无其他批次引用时物理删除。
 */
@Dao
interface LegadoImportDao {

    @Query("SELECT * FROM legado_import_subjects ORDER BY subject_id")
    suspend fun getAllSubjects(): List<LegadoImportSubjectEntity>

    @Query("SELECT * FROM legado_external_sessions ORDER BY session_id")
    suspend fun getAllSessions(): List<LegadoExternalSessionEntity>

    @Query("SELECT * FROM legado_external_day_snapshots ORDER BY snapshot_id")
    suspend fun getAllDaySnapshots(): List<LegadoExternalDaySnapshotEntity>

    @Query("SELECT * FROM legado_external_book_snapshots ORDER BY snapshot_id")
    suspend fun getAllBookSnapshots(): List<LegadoExternalBookSnapshotEntity>

    @Query("SELECT * FROM legado_import_batches ORDER BY batch_id")
    suspend fun getAllBatches(): List<LegadoImportBatchEntity>

    @Query("SELECT * FROM legado_import_batch_items ORDER BY item_id")
    suspend fun getAllBatchItems(): List<LegadoImportBatchItemEntity>

    // ---- 来源库 ----

    @Query("SELECT * FROM legado_import_sources ORDER BY created_at DESC")
    fun observeSources(): Flow<List<LegadoImportSourceEntity>>

    @Query("SELECT * FROM legado_import_sources ORDER BY created_at DESC")
    suspend fun getSources(): List<LegadoImportSourceEntity>

    @Query("SELECT * FROM legado_import_sources WHERE source_id = :id")
    suspend fun getSource(id: String): LegadoImportSourceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSource(source: LegadoImportSourceEntity)

    @Query("SELECT COUNT(*) FROM legado_external_sessions WHERE source_id = :sourceId")
    suspend fun countSessionsOf(sourceId: String): Long

    @Query("SELECT COUNT(*) FROM legado_external_day_snapshots WHERE source_id = :sourceId")
    suspend fun countDaySnapshotsOf(sourceId: String): Long

    @Query("SELECT COUNT(*) FROM legado_external_book_snapshots WHERE source_id = :sourceId")
    suspend fun countBookSnapshotsOf(sourceId: String): Long

    // ---- 主体 ----

    @Query("SELECT * FROM legado_import_subjects WHERE source_id = :sourceId")
    suspend fun getSubjectsOf(sourceId: String): List<LegadoImportSubjectEntity>

    @Query("SELECT * FROM legado_import_subjects WHERE subject_id IN (:ids)")
    suspend fun getSubjectsByIds(ids: Collection<String>): List<LegadoImportSubjectEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubjectsIfAbsent(subjects: List<LegadoImportSubjectEntity>): List<Long>

    // ---- 绑定 ----

    @Query(
        "SELECT * FROM legado_book_bindings WHERE subject_id IN (:subjectIds) " +
            "AND needs_review = 0 AND book_id IS NOT NULL",
    )
    suspend fun getActiveBindings(subjectIds: Collection<String>): List<LegadoBookBindingEntity>

    @Query("SELECT * FROM legado_book_bindings WHERE subject_id = :subjectId")
    suspend fun getBinding(subjectId: String): LegadoBookBindingEntity?

    @Query("SELECT * FROM legado_book_bindings")
    suspend fun getAllBindings(): List<LegadoBookBindingEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBinding(binding: LegadoBookBindingEntity)

    @Query(
        "UPDATE legado_book_bindings SET needs_review = :needsReview, " +
            "content_identity = :contentIdentity, content_version = content_version + 1 " +
            "WHERE subject_id = :subjectId",
    )
    suspend fun updateBindingReview(subjectId: String, needsReview: Int, contentIdentity: String?)

    @Query("DELETE FROM legado_book_bindings WHERE binding_id = :bindingId")
    suspend fun deleteBinding(bindingId: String)

    // ---- 外部会话 ----

    @Query("SELECT * FROM legado_external_sessions WHERE source_id = :sourceId")
    suspend fun getSessionsOf(sourceId: String): List<LegadoExternalSessionEntity>

    @Query(
        "SELECT * FROM legado_external_sessions WHERE session_id IN (:ids)",
    )
    suspend fun getSessionsByIds(ids: Collection<String>): List<LegadoExternalSessionEntity>

    /** 返回每个 id 的插入结果：-1 = 已存在（幂等命中，管线记 ref）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionsIfAbsent(rows: List<LegadoExternalSessionEntity>): List<Long>

    @Query("DELETE FROM legado_external_sessions WHERE session_id IN (:ids)")
    suspend fun deleteSessions(ids: Collection<String>)

    /** 撤销用整行恢复（REPLACE 覆写；无子表引用本表，安全）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSession(row: LegadoExternalSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreDaySnapshot(row: LegadoExternalDaySnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreBookSnapshot(row: LegadoExternalBookSnapshotEntity)

    // ---- 按日快照 ----

    @Query("SELECT * FROM legado_external_day_snapshots WHERE snapshot_id IN (:ids)")
    suspend fun getDaySnapshotsByIds(ids: Collection<String>): List<LegadoExternalDaySnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDaySnapshotsIfAbsent(rows: List<LegadoExternalDaySnapshotEntity>): List<Long>

    @Query(
        "UPDATE legado_external_day_snapshots SET read_time_ms = :readTimeMs, " +
            "read_words = :readWords, first_read_ms = :firstReadMs, last_read_ms = :lastReadMs, " +
            "created_by_batch = :batchId, updated_at = :updatedAt WHERE snapshot_id = :snapshotId",
    )
    suspend fun replaceDaySnapshot(
        snapshotId: String, readTimeMs: Long, readWords: Long,
        firstReadMs: Long, lastReadMs: Long, batchId: String, updatedAt: String,
    )

    @Query("DELETE FROM legado_external_day_snapshots WHERE snapshot_id IN (:ids)")
    suspend fun deleteDaySnapshots(ids: Collection<String>)

    // ---- 书级快照 ----

    @Query("SELECT * FROM legado_external_book_snapshots WHERE snapshot_id IN (:ids)")
    suspend fun getBookSnapshotsByIds(ids: Collection<String>): List<LegadoExternalBookSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookSnapshotsIfAbsent(rows: List<LegadoExternalBookSnapshotEntity>): List<Long>

    @Query(
        "UPDATE legado_external_book_snapshots SET read_time_ms = :readTimeMs, " +
            "last_read_ms = :lastReadMs, created_by_batch = :batchId, updated_at = :updatedAt " +
            "WHERE snapshot_id = :snapshotId",
    )
    suspend fun replaceBookSnapshot(
        snapshotId: String, readTimeMs: Long, lastReadMs: Long,
        batchId: String, updatedAt: String,
    )

    @Query("DELETE FROM legado_external_book_snapshots WHERE snapshot_id IN (:ids)")
    suspend fun deleteBookSnapshots(ids: Collection<String>)

    // ---- 批次 ----

    @Query("SELECT * FROM legado_import_batches ORDER BY created_at DESC")
    fun observeBatches(): Flow<List<LegadoImportBatchEntity>>

    @Query("SELECT * FROM legado_import_batches WHERE batch_id = :batchId")
    suspend fun getBatch(batchId: String): LegadoImportBatchEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(batch: LegadoImportBatchEntity)

    @Query(
        "UPDATE legado_import_batches SET status = :status, undo_note = :note " +
            "WHERE batch_id = :batchId",
    )
    suspend fun finishBatch(batchId: String, status: String, note: String?)

    // ---- 批次项 ----

    @Query("SELECT * FROM legado_import_batch_items WHERE batch_id = :batchId")
    suspend fun getBatchItems(batchId: String): List<LegadoImportBatchItemEntity>

    /** 其他批次对同一外部行的引用（判断撤销时是否可物理删除）。 */
    @Query(
        "SELECT COUNT(*) FROM legado_import_batch_items " +
            "WHERE row_key IN (:rowKeys) AND batch_id != :batchId " +
            "AND kind IN ('session_ref','day_ref','book_ref')",
    )
    suspend fun countOtherRefs(rowKeys: Collection<String>, batchId: String): Int

    /** 待关联视图：撤销批次的批次项已随撤销物理删除，这里只查现存项。 */
    @Query("SELECT * FROM legado_import_batch_items WHERE kind = 'pending_position'")
    fun observePendingPositions(): Flow<List<LegadoImportBatchItemEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatchItems(items: List<LegadoImportBatchItemEntity>)

    @Query("DELETE FROM legado_import_batch_items WHERE item_id IN (:ids)")
    suspend fun deleteBatchItems(ids: Collection<String>)

    /** 单条待关联位置删除（用户在导入页移除）。 */
    @Query("DELETE FROM legado_import_batch_items WHERE item_id = :itemId AND kind = 'pending_position'")
    suspend fun deletePendingPosition(itemId: String)
}
