package com.creationreadingassistant.data.local.dao

/**
 * Statistics-only projections. Keeping these rows narrow avoids materializing five complete
 * entities whenever the dashboard period changes or a related table is invalidated.
 */
data class StatsSessionRow(
    val book_id: String,
    val occurred_at: String?,
    val duration_ms: Long,
    val progress_percent: Float?,
)

data class StatsProgressRow(
    val book_id: String,
    val progress_percent: Float,
    val completion_state: String,
)

data class StatsBookRow(
    val id: String,
    val size: Int,
    val content_status: String,
)

data class StatsCreatedRow(
    val created_at: String,
)
