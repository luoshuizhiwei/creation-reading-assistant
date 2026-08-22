package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.StatsSessionRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class ReadingStreak(
    val current: Int,
    val longest: Int,
)

internal const val MAX_READING_SESSION_DURATION_MS = 24L * 60 * 60 * 1_000

internal fun isValidReadingSessionDuration(durationMs: Long): Boolean =
    durationMs in 1..MAX_READING_SESSION_DURATION_MS

/** Stats 页面与 Repository 共用的连续阅读日算法。 */
internal fun computeReadingStreak(
    sessions: Iterable<StatsSessionRow>,
    today: LocalDate = LocalDate.now(),
): ReadingStreak {
    val days = sessions.asSequence()
        .filter { isValidReadingSessionDuration(it.duration_ms) }
        .mapNotNull { row ->
            row.occurred_at?.let { iso ->
                runCatching {
                    Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
                }.getOrNull()
            }
        }
        .distinct()
        .sorted()
        .toList()
    if (days.isEmpty()) return ReadingStreak(current = 0, longest = 0)

    var longest = 1
    var runLength = 1
    for (index in 1..days.lastIndex) {
        if (ChronoUnit.DAYS.between(days[index - 1], days[index]) == 1L) {
            runLength++
            longest = maxOf(longest, runLength)
        } else {
            runLength = 1
        }
    }

    val daySet = days.toSet()
    var cursor = if (today in daySet) today else today.minusDays(1)
    var current = 0
    while (cursor in daySet) {
        current++
        cursor = cursor.minusDays(1)
    }
    return ReadingStreak(current = current, longest = longest)
}
