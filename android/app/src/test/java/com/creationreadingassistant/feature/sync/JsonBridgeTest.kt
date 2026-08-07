package com.creationreadingassistant.feature.sync

import android.content.Context
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.domain.model.SyncEnvelope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test

/**
 * 锁定 [JsonBridge.importFromString] 的三种合并情形（与局域网同步拉取共用 [SyncMergePolicy]）：
 * 1. 远端 revision 更新 → 覆盖本地
 * 2. 远端 revision 更旧 → 不覆盖本地
 * 3. revision 相等 → 比较 updated_at 决胜负（较新者胜）
 *
 * mockk 模式参考 ProfileSyncEngineTest：DAO 全部 relaxed mock，
 * runInTransaction 桩成立即执行传入的挂起块（JVM 可测，不依赖 Room）。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JsonBridgeTest {

    private val bookDao = mockk<BookDao>(relaxed = true)
    private val inspirationDao = mockk<InspirationDao>(relaxed = true)
    private val progressDao = mockk<ReadingProgressDao>(relaxed = true)
    private val sessionDao = mockk<ReadingSessionDao>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val json = Json { ignoreUnknownKeys = true }

    private fun bridge() = JsonBridge(bookDao, inspirationDao, progressDao, sessionDao, Dispatchers.Unconfined)

    @Before
    fun setUp() {
        // 事务桩：直接执行块本身，不依赖真实 Room。
        coEvery { bookDao.runInTransaction(any()) } coAnswers { firstArg<suspend () -> Unit>().invoke() }
    }

    private fun book(
        id: String,
        revision: Int,
        updatedAt: String,
        title: String = "书-$id",
    ) = BookEntity(id = id, title = title, format = "txt", revision = revision, updated_at = updatedAt)

    private fun exportJson(vararg envelopes: SyncEnvelope<BookEntity>): String {
        val export = LocalExport(
            exportedAt = "2026-08-05T00:00:00Z",
            deviceId = "test-device",
            books = envelopes.toList(),
            inspirations = emptyList(),
            progress = emptyList(),
            sessions = emptyList(),
        )
        return json.encodeToString(LocalExport.serializer(), export)
    }

    private fun envelope(book: BookEntity, updatedAt: String = book.updated_at) = SyncEnvelope(
        id = book.id,
        type = "book",
        revision = book.revision,
        deviceId = "remote-device",
        updatedAt = updatedAt,
        deletedAt = null,
        payload = book,
    )

    @Test
    fun `remote newer revision overwrites local`() = runTest {
        val local = book("b1", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        val remote = book("b1", revision = 2, updatedAt = "2026-01-01T00:00:00Z", title = "远端新版")
        coEvery { bookDao.getByIds(any()) } returns listOf(local)

        bridge().importFromString(context, exportJson(envelope(remote)))

        coVerify(exactly = 1) { bookDao.upsert(remote) }
    }

    @Test
    fun `remote older revision does not overwrite local`() = runTest {
        val local = book("b1", revision = 5, updatedAt = "2026-01-01T00:00:00Z")
        val remote = book("b1", revision = 2, updatedAt = "2026-06-01T00:00:00Z", title = "远端旧版")
        coEvery { bookDao.getByIds(any()) } returns listOf(local)

        bridge().importFromString(context, exportJson(envelope(remote)))

        coVerify(exactly = 0) { bookDao.upsert(any<BookEntity>()) }
    }

    @Test
    fun `equal revision newer updatedAt wins`() = runTest {
        val local = book("b1", revision = 3, updatedAt = "2026-01-01T00:00:00Z")
        val remote = book("b1", revision = 3, updatedAt = "2026-02-01T00:00:00Z", title = "远端同版更新")
        coEvery { bookDao.getByIds(any()) } returns listOf(local)

        bridge().importFromString(context, exportJson(envelope(remote)))

        coVerify(exactly = 1) { bookDao.upsert(remote) }
    }

    @Test
    fun `equal revision older updatedAt keeps local`() = runTest {
        val local = book("b1", revision = 3, updatedAt = "2026-03-01T00:00:00Z")
        val remote = book("b1", revision = 3, updatedAt = "2026-02-01T00:00:00Z", title = "远端同版更旧")
        coEvery { bookDao.getByIds(any()) } returns listOf(local)

        bridge().importFromString(context, exportJson(envelope(remote)))

        coVerify(exactly = 0) { bookDao.upsert(any<BookEntity>()) }
    }

    @Test
    fun `unknown id is inserted`() = runTest {
        val remote = book("new-book", revision = 1, updatedAt = "2026-01-01T00:00:00Z")
        coEvery { bookDao.getByIds(any()) } returns emptyList()

        bridge().importFromString(context, exportJson(envelope(remote)))

        coVerify(exactly = 1) { bookDao.upsert(remote) }
    }
}
