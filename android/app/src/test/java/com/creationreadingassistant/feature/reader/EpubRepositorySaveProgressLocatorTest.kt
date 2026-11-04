package com.creationreadingassistant.feature.reader

import android.content.Context
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationPosition
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S12-① EPUB locator 单一写入契约（EpubRepository 侧）。
 *
 * 覆盖任务书要求的场景：
 * - 「章节切换后再保存」：章变更自动保存只知章号，v2 缺省全局 offset（自洽派生）；
 * - 「退出重进」：退出保存携带真实全书偏移（满足 `offset == chapterStart[ci] + co`），
 *   重开时 loadProgress / loadProgressOffset 读回同一章号与章内偏移；
 * - 「旧 locator 兼容或安全回退」：存量伪造全局的旧导入记录首开仍可读；
 *   一次章变更保存后即被新契约产物覆盖（自愈），自愈后回源跳转恢复可用。
 */
class EpubRepositorySaveProgressLocatorTest {

    private val progressDao = mockk<ReadingProgressDao>()
    private val repository = EpubRepository(
        context = mockk<Context>(relaxed = true),
        bookDao = mockk<BookDao>(),
        readingProgressDao = progressDao,
        ioDispatcher = Dispatchers.Unconfined,
        fileOps = mockk(),
    )

    @Test
    fun `chapter switch save stores chapter start without a fabricated global offset`() = runTest {
        coEvery { progressDao.getByBook("epub-1") } returns null
        coJustRun { progressDao.upsert(any()) }

        repository.saveProgress(bookId = "epub-1", chapterIndex = 5, percent = 50f)

        val saved = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { progressDao.upsert(capture(saved)) }
        assertEquals(
            """{"chapter":5,"offset":0,"locator_v2":{"v":2,"ci":5,"co":0}}""",
            saved.captured.current_location_json,
        )
    }

    @Test
    fun `exit save keeps the global invariant and reopen reads the same position`() = runTest {
        // 退出保存：分页防抖/退出路径传入章内偏移 + 真实全书偏移。
        // chapterStartOffsets = [0, 400, 700, 1000, …] → 第 3 章起点 1000，章内 250。
        coEvery { progressDao.getByBook("epub-1") } returns null
        coJustRun { progressDao.upsert(any()) }

        repository.saveProgress(
            bookId = "epub-1",
            chapterIndex = 3,
            percent = 62f,
            offsetInChapter = 250,
            absoluteOffset = 1250,
        )

        val saved = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { progressDao.upsert(capture(saved)) }
        assertEquals(
            """{"chapter":3,"offset":250,"locator_v2":{"v":2,"offset":1250,"ci":3,"co":250}}""",
            saved.captured.current_location_json,
        )

        // 一致性不变式：全书偏移 == chapterStart[ci] + co（回源跳转可用）。
        val target = SourceNavigationContract.targetFromStoredLocation("epub-1", saved.captured.current_location_json)
        assertNotNull(target)
        val position = SourceNavigationContract.resolveChapteredPosition(
            target = target!!,
            chapterStartOffsets = listOf(0, 400, 700, 1000, 1600),
        )
        assertEquals(SourceNavigationPosition(absoluteOffset = 1250, chapterIndex = 3, chapterOffset = 250), position)

        // 「重进」：新会话首开消费的就是 loadProgress / loadProgressOffset 两个读点。
        coEvery { progressDao.getByBook("epub-1") } returns saved.captured
        assertEquals(3, repository.loadProgress("epub-1"))
        assertEquals(250, repository.loadProgressOffset("epub-1"))
    }

    @Test
    fun `legacy imported locator still opens and heals on the next chapter save`() = runTest {
        // 旧数据边界：历史导入器写过「章内偏移冒充全局」的 v2.offset（存量用户记录，
        // 不批量改写）。首开恢复读 ci/co，不受伪造全局影响 —— 兼容。
        val staleImport = ReadingProgressEntity(
            book_id = "epub-1",
            current_location_json =
                """{"chapter":8,"offset":321,"locator_v2":{"v":2,"offset":321,"ci":8,"co":321}}""",
            updated_at = "2026-09-01T00:00:00Z",
        )
        coEvery { progressDao.getByBook("epub-1") } returns staleImport
        assertEquals(8, repository.loadProgress("epub-1"))
        assertEquals(321, repository.loadProgressOffset("epub-1"))

        // 伪造全局仍会被回源跳转一致性校验安全拒绝（宁可不跳也不落错位）。
        val staleTarget = SourceNavigationContract.targetFromStoredLocation("epub-1", staleImport.current_location_json)
        assertNotNull(staleTarget)
        assertNull(
            SourceNavigationContract.resolveChapteredPosition(
                target = staleTarget!!,
                chapterStartOffsets = List(20) { it * 1000 },
            ),
        )

        // 自愈：一次章变更保存（merge 采信 incoming）后，库里就是新契约产物，
        // 回源跳转恢复可用。不批量改写存量记录，只在其被原生保存覆盖时收敛。
        coEvery { progressDao.getByBook("epub-1") } returns staleImport
        coJustRun { progressDao.upsert(any()) }
        repository.saveProgress(bookId = "epub-1", chapterIndex = 8, percent = 45f)

        val merged = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { progressDao.upsert(capture(merged)) }
        assertEquals(
            """{"chapter":8,"offset":0,"locator_v2":{"v":2,"ci":8,"co":0}}""",
            merged.captured.current_location_json,
        )
        val healedTarget = SourceNavigationContract.targetFromStoredLocation("epub-1", merged.captured.current_location_json)
        assertNotNull(healedTarget)
        val position = SourceNavigationContract.resolveChapteredPosition(
            target = healedTarget!!,
            chapterStartOffsets = List(20) { it * 1000 },
        )
        assertEquals(SourceNavigationPosition(absoluteOffset = 8000, chapterIndex = 8, chapterOffset = 0), position)
        val healedLocator = LocatorCodec.locatorFromProgress(merged.captured.current_location_json)
        assertEquals(8, healedLocator?.chapterIndex)
        assertEquals(0, healedLocator?.charOffset)
        assertNull(healedLocator?.legacyOffset)
    }
}
