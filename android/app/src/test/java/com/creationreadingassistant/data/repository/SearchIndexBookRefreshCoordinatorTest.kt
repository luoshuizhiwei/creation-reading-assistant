package com.creationreadingassistant.data.repository

import com.creationreadingassistant.feature.log.AppLog
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SearchIndexBookRefreshCoordinator] 单元测试（S04-2）。
 *
 * 不使用真实 sleep；使用 [TestScope] + [StandardTestDispatcher] 控制协程推进。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchIndexBookRefreshCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val searchIndexRepository: SearchIndexRepository = mockk()

    /** 用 testScope 构造协调器，使 launch 的子协程受 TestDispatcher 控制。 */
    private val coordinator = SearchIndexBookRefreshCoordinator(
        searchIndexRepository = searchIndexRepository,
        applicationScope = testScope,
    )

    /**
     * 1. request() 返回后，reindexBookById 尚未被调用（异步，不同步等待）。
     */
    @Test
    fun `request returns immediately without waiting for reindexBookById`() = testScope.runTest {
        var reindexCalled = false
        coEvery { searchIndexRepository.reindexBookById(any()) } coAnswers {
            reindexCalled = true
        }

        coordinator.request("book-1")

        // 还未推进调度器：子协程尚未执行，reindexCalled 应仍为 false。
        assertFalse(
            "request() 不得同步等待 reindexBookById 完成",
            reindexCalled,
        )
    }

    /**
     * 2. 调度协程执行后，reindexBookById 恰好调用一次，且传入正确的 bookId。
     */
    @Test
    fun `after dispatch runs reindexBookById exactly once with correct bookId`() = testScope.runTest {
        coEvery { searchIndexRepository.reindexBookById(any()) } returns Unit

        coordinator.request("book-42")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { searchIndexRepository.reindexBookById("book-42") }
    }

    /**
     * 3. 普通异常不向请求方抛出，也不取消整个 applicationScope。
     */
    @Test
    fun `ordinary exception is silently logged without cancelling the scope`() = testScope.runTest {
        coEvery { searchIndexRepository.reindexBookById(any()) } throws RuntimeException("索引写入失败")

        // request() 本身不抛
        coordinator.request("book-err")
        testScheduler.advanceUntilIdle()

        // scope 仍然活跃：能继续 launch 新协程
        var newJobRan = false
        testScope.launch { newJobRan = true }
        testScheduler.advanceUntilIdle()
        assertTrue("applicationScope 不应因索引失败而取消", newJobRan)
    }

    /**
     * 4. 多次调用 request 时，每次成功均触发对应的 reindexBookById。
     */
    @Test
    fun `each request call triggers exactly one reindex for the given bookId`() = testScope.runTest {
        coEvery { searchIndexRepository.reindexBookById(any()) } returns Unit

        coordinator.request("b-1")
        coordinator.request("b-2")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { searchIndexRepository.reindexBookById("b-1") }
        coVerify(exactly = 1) { searchIndexRepository.reindexBookById("b-2") }
        coVerify(exactly = 2) { searchIndexRepository.reindexBookById(any()) }
    }

    /**
     * 5. 索引失败时，AppLog.w 被调用（写入警告日志）。
     *    AppLog 是 object，直接断言其 entries 是否新增一条 WARN 即可。
     */
    @Test
    fun `ordinary exception triggers AppLog warn entry`() = testScope.runTest {
        AppLog.clear()
        coEvery { searchIndexRepository.reindexBookById("book-log") } throws IllegalStateException("crash")

        coordinator.request("book-log")
        testScheduler.advanceUntilIdle()

        val entries = AppLog.entries.value
        assertTrue(
            "索引失败应写入 WARN 日志",
            entries.any { it.level == AppLog.Level.WARN && it.message.contains("book-log") },
        )
    }

    /**
     * 6. CancellationException 不被吞掉（结构化取消正常传播）。
     *    通过让 reindexBookById 抛 CancellationException 并验证协程正常退出来间接验证。
     *    注意：在 SupervisorJob scope 中，子协程取消不会传播给 scope 自身。
     */
    @Test
    fun `CancellationException is rethrown and not treated as ordinary failure`() = testScope.runTest {
        AppLog.clear()
        // CancellationException 应原样重抛，不进入 catch (e: Exception) 分支
        coEvery { searchIndexRepository.reindexBookById("book-cancel") } throws CancellationException("test-cancel")

        coordinator.request("book-cancel")
        testScheduler.advanceUntilIdle()

        // CancellationException 不应写入 AppLog（不被当做普通失败）
        val entries = AppLog.entries.value
        assertFalse(
            "CancellationException 不应被记录为普通失败日志",
            entries.any { it.message.contains("book-cancel") },
        )
    }
}
