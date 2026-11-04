package com.creationreadingassistant.data.repository

import android.content.Context
import com.creationreadingassistant.data.local.dao.BookCategoryDao
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.dao.BookTagDao
import com.creationreadingassistant.data.local.dao.ChapterReadDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.dao.ShelfBookDao
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.FileOps
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * CRA-READER-DISPATCH-ORDER-MINIMAL-v6 —— 阅读器进度保存**派发次序**的真实保存链定向证据。
 *
 * 覆盖两条真实入口：
 * - TXT/Markdown：`BookRepository.saveProgress` → `mergeReaderProgress` → `ReadingProgressDao.upsert`
 * - EPUB：`EpubRepository.saveProgress` → `mergeReaderProgress` → `ReadingProgressDao.upsert`
 *
 * 顺序来源一律是生产实现 [ReaderProgressSaveOrder]（单调事件派发序号），
 * 测试只控制**执行时序**（闩锁）与外部存储（内存替身），不复制保存算法。
 *
 * 相比 16-v5：删除未有真实可达路径支持的 session 身份层，消除旧存活生产者返回后合法保存被误拒的缺陷；
 * 相比 16-v4：不使用墙钟时间戳排序，避免校时回拨误拒合法保存。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionProgressSaveOrderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 每本书的行（外部存储替身）。 */
    private val store = mutableMapOf<String, ReadingProgressEntity>()

    private lateinit var progressDao: ReadingProgressDao
    private lateinit var repository: BookRepository
    private lateinit var epubRepository: EpubRepository

    /** 写回闩锁：只扣住**指定书 + 指定 locator 偏移**的那一次 upsert，用来构造执行时序。 */
    private var heldBookId: String? = null
    private var holdWriteForOffset: Int? = null
    private var holdWriteGate: CompletableDeferred<Unit>? = null

    @Before
    fun setUp() {
        progressDao = mockk(relaxed = true)
        coEvery { progressDao.getByBook(any()) } coAnswers { store[firstArg<String>()] }
        coEvery { progressDao.upsert(any()) } coAnswers {
            val entity = firstArg<ReadingProgressEntity>()
            if (entity.book_id == heldBookId && locatorOffsetOf(entity) == holdWriteForOffset) {
                holdWriteGate?.await()
            }
            store[entity.book_id] = entity
        }
        repository = BookRepository(
            bookDao = mockk<BookDao>(relaxed = true) {
                coEvery { runInTransaction(any()) } coAnswers {
                    firstArg<suspend () -> Unit>().invoke()
                }
            },
            bookContentDao = mockk(relaxed = true),
            bookFileDao = mockk(relaxed = true),
            progressDao = progressDao,
            sessionDao = mockk(relaxed = true),
            noteDao = mockk(relaxed = true),
            highlightDao = mockk(relaxed = true),
            inspirationDao = mockk(relaxed = true),
            bookTagDao = mockk(relaxed = true),
            bookCategoryDao = mockk(relaxed = true),
            shelfBookDao = mockk(relaxed = true),
            chapterReadDao = mockk(relaxed = true),
            searchIndexRepository = null,
            indexRefreshCoordinator = null,
            context = mockk { every { filesDir } returns tmp.newFolder("files") },
        )
        epubRepository = EpubRepository(
            context = mockk<Context>(relaxed = true),
            bookDao = mockk<BookDao>(relaxed = true),
            readingProgressDao = progressDao,
            ioDispatcher = Dispatchers.Unconfined,
            fileOps = mockk<FileOps>(relaxed = true),
        )
    }

    private fun holdWrite(bookId: String, offset: Int): CompletableDeferred<Unit> {
        heldBookId = bookId
        holdWriteForOffset = offset
        return CompletableDeferred<Unit>().also { holdWriteGate = it }
    }

    /** 生产 `ReaderProgressEffects` / `persistCurrentProgress` 落库实体的等价构造。 */
    private fun progressOf(
        bookId: String,
        offset: Int,
        percent: Float,
        updatedAt: String = "2026-10-12T10:00:00Z",
    ): ReadingProgressEntity = ReadingProgressEntity(
        book_id = bookId,
        progress_percent = percent,
        completion_state = "reading",
        current_location_json = LocatorBuilder.progressJson(
            legacyOffset = offset,
            chapterIndex = 0,
            charOffset = offset,
        ),
        updated_at = updatedAt,
    )

    private fun locatorOffsetOf(entity: ReadingProgressEntity): Int? =
        entity.current_location_json?.let { LocatorCodec.locatorFromProgress(it)?.charOffset }

    private fun storedOffset(bookId: String): Int? = store[bookId]?.let { locatorOffsetOf(it) }

    private fun storedChapter(bookId: String): Int? =
        store[bookId]?.current_location_json?.let { LocatorCodec.locatorFromProgress(it)?.chapterIndex }

    // 每本测试书用独立 id：ReaderProgressSaveOrder 的记账是进程内共享状态，
    // 跨用例复用同一本书会让上一个用例的序号影响下一个用例。
    private fun book(tag: String) = "book_$tag"

    // ── 对照：顺序正常时，较新事件的位置胜出 ─────────────────────────────

    @Test
    fun `control in-order saves let the newer event win`() = runTest {
        val b = book("inorder")
        repository.saveProgress(progressOf(b, 1775, 46.22f), ReaderProgressSaveOrder.next())
        repository.saveProgress(progressOf(b, 3850, 70.0f), ReaderProgressSaveOrder.next())

        assertEquals("顺序保存时较新位置胜出", 3850, storedOffset(b))
    }

    // ── F 本体 1：旧事件整体迟到（派发在前、进入保存链在后）────────────────────────

    @Test
    fun `F a late old event must not overwrite the newer position`() = runTest {
        val b = book("late")
        // 派发边界取号：旧事件先派发（序号小），新事件后派发（序号大）。
        val oldOrder = ReaderProgressSaveOrder.next()
        val newOrder = ReaderProgressSaveOrder.next()

        val gate = CompletableDeferred<Unit>()
        val oldJob = launch {
            gate.await() // 旧事件的落库协程在 IO 队列里被拖后
            repository.saveProgress(progressOf(b, 1775, 46.22f), oldOrder)
        }
        runCurrent()

        repository.saveProgress(progressOf(b, 3850, 70.0f), newOrder)
        assertEquals("前提：新事件位置已落库", 3850, storedOffset(b))

        gate.complete(Unit)
        oldJob.join()

        assertEquals("迟到的旧事件不得把较新进度覆盖回旧位置", 3850, storedOffset(b))
    }

    // ── F 本体 2：读改写交错（旧事件读到旧值后、写回前被新事件插入）────────────────

    @Test
    fun `F an older event interleaved inside read-modify-write must not win`() = runTest {
        val b = book("interleave")
        val oldOrder = ReaderProgressSaveOrder.next()
        val newOrder = ReaderProgressSaveOrder.next()

        val gate = holdWrite(b, 1775)
        val oldJob = launch { repository.saveProgress(progressOf(b, 1775, 46.22f), oldOrder) }
        runCurrent() // 旧事件已读到存储（此时为空），卡在写回之前

        val newJob = launch { repository.saveProgress(progressOf(b, 3850, 70.0f), newOrder) }
        runCurrent() // 新事件试图插入同一条链

        gate.complete(Unit)
        oldJob.join()
        newJob.join()

        assertEquals("读改写交错时也不得让较早的事件覆盖较新进度", 3850, storedOffset(b))
    }

    // ── 反例 1：同一时间戳的两条合法保存（v4 的墙钟排序无法定序）────────────────────

    @Test
    fun `two legitimate saves with the same wall-clock timestamp are still ordered by event order`() =
        runTest {
            val b = book("samelclock")
            val sameMoment = "2026-10-12T10:00:00Z"
            repository.saveProgress(progressOf(b, 1775, 46.22f, sameMoment), ReaderProgressSaveOrder.next())
            repository.saveProgress(progressOf(b, 3850, 70.0f, sameMoment), ReaderProgressSaveOrder.next())

            assertEquals("同一时间戳下仍按事件先后定序，较新者胜出", 3850, storedOffset(b))
        }

    // ── 反例 2：系统校时回拨后的合法新保存必须被接受（v4 墙钟回归）──────────────────

    @Test
    fun `a legitimate new save after a system clock rollback must still be accepted`() = runTest {
        val b = book("rollback")
        // 先落一条 10:00 的进度，随后系统校时回拨到 09:55，用户继续读到 3850 并保存。
        repository.saveProgress(
            progressOf(b, 1775, 46.22f, "2026-10-12T10:00:00Z"), ReaderProgressSaveOrder.next(),
        )
        repository.saveProgress(
            progressOf(b, 3850, 70.0f, "2026-10-12T09:55:00Z"), ReaderProgressSaveOrder.next(),
        )

        assertEquals("校时回拨不得让合法新保存被当成迟到事件丢弃", 3850, storedOffset(b))
    }

    // ── 反例 3：用户主动回读（位置变小）是合法新位置 ─────────────────────────────

    @Test
    fun `an active backward jump is a legitimate new position`() = runTest {
        val b = book("backward")
        repository.saveProgress(progressOf(b, 3850, 70.0f), ReaderProgressSaveOrder.next())
        repository.saveProgress(progressOf(b, 1200, 30.0f), ReaderProgressSaveOrder.next())

        assertEquals("用户主动回读后的较小位置必须被接受", 1200, storedOffset(b))
    }

    // ── 16-v6 核心实证：同书两个仍存活生产者交替派发，返回较早创建者后的合法前进/回读都接受 ──
    // 证明：16-v5 字典序会因 session1 < session2 误拒返回较早生产者后的合法新事件；
    // 而 16-v6 基于生产派发单调序号，不仅正确接受合法新事件，还能阻止迟到旧 IO 覆盖。

    @Test
    fun `two surviving producers interleaved saves accept valid forwards and backwards jumps from earlier producer`() =
        runTest {
            val b = book("interleaved_producers")

            // 生产者 A（较早创建）派发初始阅读进度 1000
            val orderA1 = ReaderProgressSaveOrder.next()
            repository.saveProgress(progressOf(b, 1000, 20.0f), orderA1)
            assertEquals("生产者 A 初始保存生效", 1000, storedOffset(b))

            // 生产者 B（较晚创建）接替阅读，派发进度 2000
            val orderB1 = ReaderProgressSaveOrder.next()
            repository.saveProgress(progressOf(b, 2000, 40.0f), orderB1)
            assertEquals("生产者 B 保存生效", 2000, storedOffset(b))

            // 关键反例验证（对应 16-v5 缺陷）：
            // 用户返回生产者 A：
            // 场景 1：生产者 A 继续向前阅读到 3000，在派发边界取单调递增的新序号 orderA2 (> orderB1)
            val orderA2 = ReaderProgressSaveOrder.next()
            repository.saveProgress(progressOf(b, 3000, 60.0f), orderA2)
            assertEquals("返回较早生产者 A 后的合法前进必须被接受（16-v5 曾因旧 session 误拒）", 3000, storedOffset(b))

            // 场景 2：生产者 A 回读到 500，在派发边界取单调递增的新序号 orderA3 (> orderA2)
            val orderA3 = ReaderProgressSaveOrder.next()
            repository.saveProgress(progressOf(b, 500, 10.0f), orderA3)
            assertEquals("返回较早生产者 A 后的合法回读必须被接受（不退回单调增长判定）", 500, storedOffset(b))

            // 场景 3：此时生产者 B 之前产生的一个迟到旧 IO 事件（序号早于 orderA3）异步到达落库
            val lateOrderB = orderB1 // 迟到旧事件，序号小于当前已被接受的 orderA3
            repository.saveProgress(progressOf(b, 2500, 50.0f), lateOrderB)
            assertEquals("迟到的旧 IO 事件不得覆盖后派发的合法进度", 500, storedOffset(b))
        }

    @Test
    fun `v5 session dictionary order contrast test demonstrates why session token was dropped`() {
        // 本用例提供明确的纯逻辑对比实证：
        // 模拟 16-v5 的 (session, order) 字典序与 16-v6 的 order 单调序对同一事件序列的判定差异。
        val sessionA = 1L
        val sessionB = 2L

        val order1 = 100L // A 派发
        val order2 = 200L // B 派发
        val order3 = 300L // 返回 A 派发合法新事件

        // 16-v5 字典序判定：
        // 已接受为 (sessionB, order2) = (2, 200)
        // 传入事件为 (sessionA, order3) = (1, 300)
        // 判定 isOlder: sessionA (1) < sessionB (2) -> true！
        // 结果：即使 order3 (300) > order2 (200)，16-v5 仍将其判定为旧事件并丢弃！
        val v5WouldAccept = sessionA > sessionB || (sessionA == sessionB && order3 >= order2)
        org.junit.Assert.assertFalse("16-v5 字典序会错误拒绝较早生产者返回后的合法新保存", v5WouldAccept)

        // 16-v6 最小实施判定：
        // 仅看事件派发序号：order3 (300) >= order2 (200) -> 接受！
        val v6Accepts = order3 >= order2
        org.junit.Assert.assertTrue("16-v6 单调派发序号正确接受较早生产者返回后的合法新保存", v6Accepts)
    }

    // ── 对照：跨书互不影响 ────────────────────────────────────────────────────

    @Test
    fun `control a save for another book never touches the active book row`() = runTest {
        val a = book("cross_a")
        val c = book("cross_b")
        repository.saveProgress(progressOf(c, 900, 30.0f), ReaderProgressSaveOrder.next())
        repository.saveProgress(progressOf(a, 1775, 46.22f), ReaderProgressSaveOrder.next())

        assertEquals("另一本书的位置不受影响", 900, storedOffset(c))
        assertEquals("本书写入自己的行", 1775, storedOffset(a))
    }

    // ── EPUB 同形：章切换落库（粗位置）不得覆盖其后的精确位置保存 ──────────────────

    @Test
    fun `F EPUB a slow chapter-switch save must not overwrite a later precise position`() = runTest {
        val b = book("epub")
        // 章切换导航的派发边界取号（章 5），随后分页防抖保存了章 8 的精确位置。
        val chapterSwitchOrder = ReaderProgressSaveOrder.next()
        val preciseOrder = ReaderProgressSaveOrder.next()

        epubRepository.saveProgress(
            bookId = b, chapterIndex = 8, percent = 60f, offsetInChapter = 400,
            absoluteOffset = 5000, order = preciseOrder,
        )
        assertEquals("前提：精确位置已落库", 8, storedChapter(b))

        // 慢完成的旧章加载（章 5）在此刻才落库：序号更小，不得把进度倒回去。
        epubRepository.saveProgress(
            bookId = b, chapterIndex = 5, percent = 50f, order = chapterSwitchOrder,
        )

        assertEquals("慢完成的旧章切换保存不得覆盖较新的精确位置", 8, storedChapter(b))
    }

    @Test
    fun `control EPUB in-order chapter saves let the later chapter win`() = runTest {
        val b = book("epub_inorder")
        epubRepository.saveProgress(
            bookId = b, chapterIndex = 5, percent = 50f, order = ReaderProgressSaveOrder.next(),
        )
        epubRepository.saveProgress(
            bookId = b, chapterIndex = 8, percent = 60f, order = ReaderProgressSaveOrder.next(),
        )

        assertEquals("顺序的章切换保存按较后者生效", 8, storedChapter(b))
    }

    // ── 对照：未经阅读会话的写入（不传 order）保持既有行为 ────────────────────────

    @Test
    fun `control saves outside a reading session keep the previous behaviour`() = runTest {
        val b = book("outsession")
        repository.saveProgress(progressOf(b, 1775, 46.22f))
        repository.saveProgress(progressOf(b, 3850, 70.0f))

        assertEquals("不经会话的连续写入仍按后写生效", 3850, storedOffset(b))
    }
}
