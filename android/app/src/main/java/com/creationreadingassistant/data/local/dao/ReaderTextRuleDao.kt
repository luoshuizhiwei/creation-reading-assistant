package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import kotlinx.coroutines.flow.Flow

/** 批量调整 [ReaderTextRuleEntity.position] 的单条载荷。 */
data class ReaderTextRulePositionUpdate(
    val id: String,
    val position: Int,
    val updatedAt: Long,
)

/**
 * 阅读器文本规则 DAO（v9）。
 *
 * 删除接口刻意限定 `builtin = 0`：内置规则在 SQL 层就被拦截，
 * 调用方无法通过 DAO 误删内置规则。
 */
@Dao
interface ReaderTextRuleDao {
    @Query("SELECT * FROM reader_text_rules ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<ReaderTextRuleEntity>>

    @Query("SELECT * FROM reader_text_rules WHERE kind = :kind ORDER BY position ASC, id ASC")
    fun observeByKind(kind: String): Flow<List<ReaderTextRuleEntity>>

    @Query("SELECT * FROM reader_text_rules WHERE id = :id")
    fun observeById(id: String): Flow<ReaderTextRuleEntity?>

    @Query("SELECT * FROM reader_text_rules WHERE id = :id")
    suspend fun getById(id: String): ReaderTextRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReaderTextRuleEntity)

    /** 仅删除自定义规则；命中内置规则（builtin = 1）时返回 0。 */
    @Query("DELETE FROM reader_text_rules WHERE id = :id AND builtin = 0")
    suspend fun deleteCustom(id: String): Int

    @Query(
        "UPDATE reader_text_rules SET enabled = :enabled, updated_at = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long): Int

    @Query(
        "UPDATE reader_text_rules SET position = :position, updated_at = :updatedAt " +
            "WHERE id = :id",
    )
    suspend fun updatePosition(id: String, position: Int, updatedAt: Long): Int

    /** 事务内批量更新 position（拖动排序后一次落库）。 */
    @Transaction
    suspend fun updatePositions(updates: List<ReaderTextRulePositionUpdate>) {
        updates.forEach { updatePosition(it.id, it.position, it.updatedAt) }
    }
}
