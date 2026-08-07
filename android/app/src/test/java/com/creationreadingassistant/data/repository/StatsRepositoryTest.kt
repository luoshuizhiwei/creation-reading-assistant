package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * StatsRepository 时间窗口聚合下推（A4）测试。
 *
 * 覆盖：无数据、边界日、跨日三种情形，以及下推 SQL 谓词
 * （epoch 秒边界比较）与旧内存逻辑（epochDayOf 日历日比较）的等价性。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val sessionDao = mockk<ReadingSessionDao>()
    private val progressDao = mockk<ReadingProgressDao>()
    private val bookDao = mockk<BookDao>()
    private val inspirationDao = mockk<InspirationDao>()
    private val noteDao = mockk<NoteDao>()

    private val sessionsFlow = MutableStateFlow<List<ReadingSessionEntity>>(emptyList())
    private val progressFlow = MutableStateFlow<List<ReadingProgressEntity>>(emptyList())
    private val booksFlow = MutableStateFlow<List<BookEntity>>(emptyList())
    private val inspirationsFlow = MutableStateFlow<List<InspirationEntity>>(emptyList())

    private lateinit var repo: StatsRepository

    @Before
    fun setUp() {
        every { sessionDao.observeAllActive() } returns sessionsFlow
        every { progressDao.observeAllActive() } returns progressFlow
        every { bookDao.observeAllActive() } returns booksFlow
        every { inspirationDao.observeAllActive() } returns inspirationsFlow
        repo = StatsRepository(
            sessionDao, progressDao, bookDao, inspirationDao, noteDao,
            testDispatcher, testDispatcher,
        )
    }

    private fun session(id: String, createdAt: String?, durationMs: Long) = ReadingSessionEntity(
        id = id,
        book_id = "b1",
        started_at = null,
        ended_at = null,
        duration_ms = durationMs,
        progress_percent = null,
        created_at = createdAt,
        device_id = null,
        revision = 1,
        payload = null,
        updated_at = createdAt ?: "2026-01-01T00:00:00Z",
        deleted_at = null,
    )

    // ─── 情形一：无数据 ────────────────────────────────────────────────

    @Test
    fun `aggregates are zero when there is no data`() = runTest(testDispatcher.scheduler) {
        coEvery { sessionDao.sumCreatedDurationBetween(any(), any(), any()) } returns 0L
        coEvery { sessionDao.sumCreatedDurationSince(any(), any()) } returns 0L

        val stats = repo.observeStats().first()

        assertEquals(0, stats.totalSessions)
        assertEquals(0L, stats.totalDurationMs)
        assertEquals(0L, stats.todayMs)
        assertEquals(0L, stats.last7Ms)
        assertEquals(0L, stats.last30Ms)
        assertEquals(0, stats.streakDays)
    }

    // ─── 情形二：边界日 —— 下推参数必须是精确的本地日历日零点边界 ─────

    @Test
    fun `window aggregates come from dao pushdown with exact day boundaries`() =
        runTest(testDispatcher.scheduler) {
            // 内存 sessions 只含远古数据：若今日/7日/30日仍走旧的逐行内存路径，结果会是 0；
            // 断言最终值等于 DAO SUM 的返回，证明聚合确实已下推。
            sessionsFlow.value = listOf(session("old", "2020-01-01T00:00:00Z", 123L))

            val sinceStarts = mutableListOf<Long>()
            coEvery {
                sessionDao.sumCreatedDurationBetween(any(), any(), any())
            } returns 100L
            coEvery {
                sessionDao.sumCreatedDurationSince(capture(sinceStarts), any())
            } returnsMany listOf(200L, 300L)

            val stats = repo.observeStats().first()

            assertEquals(100L, stats.todayMs)
            assertEquals(200L, stats.last7Ms)
            assertEquals(300L, stats.last30Ms)
            // 旧的总量/连续天数仍由内存列表计算，行为不变
            assertEquals(1, stats.totalSessions)
            assertEquals(123L, stats.totalDurationMs)

            val today = LocalDate.now()
            coVerify {
                sessionDao.sumCreatedDurationBetween(
                    startEpochSecondOf(today),
                    startEpochSecondOf(today.plusDays(1)),
                    coarseLowerIso(startEpochSecondOf(today)),
                )
            }
            // 7日窗口在前、30日窗口在后（与调用顺序一致）
            assertEquals(2, sinceStarts.size)
            assertEquals(startEpochSecondOf(today.minusDays(7)), sinceStarts[0])
            assertEquals(startEpochSecondOf(today.minusDays(30)), sinceStarts[1])
        }

    // ─── 情形三：跨日 —— 今日窗口恰为 [本地今日零点, 本地明日零点) ─────

    @Test
    fun `today window crosses local midnight exactly`() = runTest(testDispatcher.scheduler) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val todayStart = startEpochSecondOf(today)
        val tomorrowStart = startEpochSecondOf(today.plusDays(1))

        // 昨日本地 23:59:59 在窗口外；今日本地 00:00:00 恰为窗口下界
        val lastSecondOfYesterday = today.atStartOfDay(zone).minusSeconds(1).toEpochSecond()
        assertTrue(lastSecondOfYesterday < todayStart)
        assertEquals(todayStart, today.atStartOfDay(zone).toEpochSecond())
        // 窗口上界是明日零点（跨夏令时时可能不是整 86400 秒，但必须严格单调）
        assertTrue(tomorrowStart > todayStart)

        // 旧谓词（epochDayOf == 今日）与新谓词（秒级窗口）对同一批时刻的判定一致
        var instant = today.minusDays(2).atStartOfDay(zone).toInstant()
        val todayDay = today.toEpochDay()
        repeat(4 * 24) {
            val iso = instant.toString()
            val oldPredicate = epochDayOf(iso) == todayDay
            val newPredicate = instant.epochSecond >= todayStart && instant.epochSecond < tomorrowStart
            assertEquals("跨日判定不一致: $iso", oldPredicate, newPredicate)
            instant = instant.plusSeconds(3_607) // 非整点步长，必跨越午夜边界
        }
    }

    // ─── 等价性：秒级边界谓词 ⇔ epochDayOf 日历日比较（7日/30日窗口）───

    @Test
    fun `epoch second boundary predicate is equivalent to epochDayOf comparison`() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val windowStartDates = listOf(today.minusDays(30), today.minusDays(7), today)
        val boundaries = windowStartDates.associateWith { startEpochSecondOf(it) }

        var instant = today.minusDays(40).atStartOfDay(zone).toInstant()
        repeat(60 * 24) {
            val iso = instant.toString()
            val day = epochDayOf(iso)
            for (date in windowStartDates) {
                val oldPredicate = day >= date.toEpochDay()
                val newPredicate = instant.epochSecond >= boundaries.getValue(date)
                assertEquals("since 谓词不等价: $iso vs $date", oldPredicate, newPredicate)
            }
            instant = instant.plusSeconds(3_607) // 非整点步长，覆盖日界与时区偏移边界
        }
    }

    // ─── 粗下界工具：比精确边界早 2 天且可被 Instant 解析 ──────────────

    @Test
    fun `coarse lower iso is two days before the exact boundary`() {
        val today = LocalDate.now()
        val start = startEpochSecondOf(today)
        val coarse = coarseLowerIso(start)
        assertEquals(start - 2 * 86_400L, Instant.parse(coarse).epochSecond)
    }
}
