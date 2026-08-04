package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.repository.StatsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * StatsViewModel 统计流转发测试（浅测：ViewModel 本身只是状态化转发）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var repo: StatsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repo = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stats(totalSessions: Int): StatsRepository.Stats = StatsRepository.Stats(
        totalSessions = totalSessions,
        totalDurationMs = 1_000L,
        todayMs = 100L,
        last7Ms = 200L,
        last30Ms = 300L,
        streakDays = 3,
        bookCount = 4,
        completedBookCount = 1,
        inspirationCount = 2,
        recentBooks = emptyList(),
        byBook = emptyList(),
    )

    @Test
    fun `stats initial value is null before subscription`() = runTest(mainDispatcher.scheduler) {
        every { repo.observeStats() } returns flowOf(stats(5))
        val vm = StatsViewModel(repo)

        assertNull(vm.stats.value)
    }

    @Test
    fun `stats forwards repository emissions after subscription`() = runTest(mainDispatcher.scheduler) {
        every { repo.observeStats() } returns flowOf(stats(7))
        val vm = StatsViewModel(repo)
        val job = launch { vm.stats.collect { } }
        testScheduler.advanceUntilIdle()

        assertEquals(7, vm.stats.value?.totalSessions)
        assertEquals(3, vm.stats.value?.streakDays)

        job.cancel()
        testScheduler.advanceUntilIdle()
    }
}
