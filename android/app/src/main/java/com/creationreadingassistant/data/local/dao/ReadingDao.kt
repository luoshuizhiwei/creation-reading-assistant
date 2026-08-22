package com.creationreadingassistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId AND deleted_at IS NULL")
    suspend fun getByBook(bookId: String): ReadingProgressEntity?

    /** 批量按 book_id 查询（仅活跃记录），供导入合并建 Map 消除 N+1。 */
    @Query("SELECT * FROM reading_progress WHERE book_id IN (:bookIds) AND deleted_at IS NULL")
    suspend fun getByBooks(bookIds: Collection<String>): List<ReadingProgressEntity>

    @Query("SELECT * FROM reading_progress WHERE deleted_at IS NULL ORDER BY updated_at DESC")
    fun observeAllActive(): Flow<List<ReadingProgressEntity>>

    @Query(
        "SELECT book_id, progress_percent, completion_state " +
            "FROM reading_progress WHERE deleted_at IS NULL"
    )
    fun observeStatsRows(): Flow<List<StatsProgressRow>>

    @Query("SELECT * FROM reading_progress WHERE deleted_at IS NOT NULL")
    suspend fun getDeleted(): List<ReadingProgressEntity>
}

@Dao
interface ReadingSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ReadingSessionEntity)

    /** 批量写入，供软删除/恢复等事务路径替代逐行 upsert。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(sessions: List<ReadingSessionEntity>)

    @Query("SELECT * FROM reading_sessions WHERE book_id = :bookId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByBook(bookId: String): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAllActive(): Flow<List<ReadingSessionEntity>>

    @Query(
        "SELECT book_id, COALESCE(started_at, created_at) AS occurred_at, " +
            "duration_ms, progress_percent " +
            "FROM reading_sessions WHERE deleted_at IS NULL"
    )
    fun observeStatsRows(): Flow<List<StatsSessionRow>>

    @Query("SELECT * FROM reading_sessions WHERE id = :id")
    suspend fun getById(id: String): ReadingSessionEntity?

    /** 批量按 id 查询（与 [getById] 同语义，不过滤软删除），供导入合并建 Map 消除 N+1。 */
    @Query("SELECT * FROM reading_sessions WHERE id IN (:ids)")
    suspend fun getByIds(ids: Collection<String>): List<ReadingSessionEntity>

    @Query("SELECT * FROM reading_sessions WHERE deleted_at IS NOT NULL")
    suspend fun getDeleted(): List<ReadingSessionEntity>

    // ─── 时间窗口聚合下推（A4）────────────────────────────────────
    //
    // 与内存旧逻辑 `epochDayOf(iso) 比较日历日` 严格等价的 SQL 谓词：
    //  1. :startEpochSecond / :endEpochSecond 是本地时区日历日零点换算出的
    //     epoch 秒边界（由 StatsRepository 的 startEpochSecondOf 计算）；
    //  2. strftime('%s', …) 向下取整到秒；日边界恰在整数秒上，同一秒内
    //     的取整不改变日历日归属，因此秒级比较与 epochDay 比较等价；
    //  3. GLOB 限定 ISO 'T' 分隔、秒级时刻、'Z' 结尾，与 Instant.parse 失败
    //     返回 -1 被排除的语义对齐（带时区偏移/空格分隔的串两边都被排除）；
    //  4. :coarseIso 是比精确边界早 2 天的粗下界，只做预过滤，不影响结果正确性；
    //  5. duration_ms 仅接受 (0, 24h]，与统计页共享的会话有效性策略一致。

    /** 有效活跃会话总时长（排除非正数及超过 24 小时的异常记录）。 */
    @Query(
        "SELECT COALESCE(SUM(duration_ms), 0) FROM reading_sessions " +
            "WHERE deleted_at IS NULL AND duration_ms > 0 AND duration_ms <= 86400000"
    )
    suspend fun sumAllActiveDuration(): Long

    /** created_at 落在 [startEpochSecond, endEpochSecond) 内的活跃会话时长之和（今日窗口）。 */
    @Query(
        "SELECT COALESCE(SUM(duration_ms), 0) FROM reading_sessions " +
            "WHERE deleted_at IS NULL " +
            "AND duration_ms > 0 AND duration_ms <= 86400000 " +
            "AND created_at >= :coarseIso " +
            "AND created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]*Z' " +
            "AND CAST(strftime('%s', created_at) AS INTEGER) >= :startEpochSecond " +
            "AND CAST(strftime('%s', created_at) AS INTEGER) < :endEpochSecond"
    )
    suspend fun sumCreatedDurationBetween(
        startEpochSecond: Long,
        endEpochSecond: Long,
        coarseIso: String,
    ): Long

    /** created_at 的日历日 >= :startEpochSecond 对应日的活跃会话时长之和（7日/30日窗口）。 */
    @Query(
        "SELECT COALESCE(SUM(duration_ms), 0) FROM reading_sessions " +
            "WHERE deleted_at IS NULL " +
            "AND duration_ms > 0 AND duration_ms <= 86400000 " +
            "AND created_at >= :coarseIso " +
            "AND created_at GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]*Z' " +
            "AND CAST(strftime('%s', created_at) AS INTEGER) >= :startEpochSecond"
    )
    suspend fun sumCreatedDurationSince(startEpochSecond: Long, coarseIso: String): Long

    /** 发生时间 COALESCE(started_at, created_at) 落在 [startEpochSecond, endEpochSecond) 内的时长之和（Home 今日）。 */
    @Query(
        "SELECT COALESCE(SUM(duration_ms), 0) FROM reading_sessions " +
            "WHERE deleted_at IS NULL " +
            "AND duration_ms > 0 AND duration_ms <= 86400000 " +
            "AND COALESCE(started_at, created_at) >= :coarseIso " +
            "AND COALESCE(started_at, created_at) GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]*Z' " +
            "AND CAST(strftime('%s', COALESCE(started_at, created_at)) AS INTEGER) >= :startEpochSecond " +
            "AND CAST(strftime('%s', COALESCE(started_at, created_at)) AS INTEGER) < :endEpochSecond"
    )
    suspend fun sumOccurredDurationBetween(
        startEpochSecond: Long,
        endEpochSecond: Long,
        coarseIso: String,
    ): Long

    /** 发生时间 COALESCE(started_at, created_at) 在边界之后的时长之和（7日/30日窗口）。 */
    @Query(
        "SELECT COALESCE(SUM(duration_ms), 0) FROM reading_sessions " +
            "WHERE deleted_at IS NULL " +
            "AND duration_ms > 0 AND duration_ms <= 86400000 " +
            "AND COALESCE(started_at, created_at) >= :coarseIso " +
            "AND COALESCE(started_at, created_at) GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]T[0-9][0-9]:[0-9][0-9]:[0-9][0-9]*Z' " +
            "AND CAST(strftime('%s', COALESCE(started_at, created_at)) AS INTEGER) >= :startEpochSecond"
    )
    suspend fun sumOccurredDurationSince(startEpochSecond: Long, coarseIso: String): Long
}
