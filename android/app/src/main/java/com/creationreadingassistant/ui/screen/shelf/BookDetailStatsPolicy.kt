package com.creationreadingassistant.ui.screen.shelf

import com.creationreadingassistant.data.local.entity.ReadingSessionEntity

/**
 * 进度表的累计时长可能来自旧版本或尚未同步；会话明细则是另一条独立来源。
 * 取两者较大值可避免重复相加，同时不会把已有阅读记录显示成 0 分钟。
 */
internal fun bookDetailReadingTimeMs(
    progressTotalMs: Long,
    sessions: List<ReadingSessionEntity>,
): Long = maxOf(
    progressTotalMs.coerceAtLeast(0L),
    sessions.sumOf { it.duration_ms.coerceAtLeast(0L) },
)
