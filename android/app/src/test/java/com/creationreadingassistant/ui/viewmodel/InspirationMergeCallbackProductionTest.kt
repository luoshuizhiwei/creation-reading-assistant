package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.InspirationVariantDao
import com.creationreadingassistant.data.local.dao.StatsCreatedRow
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import com.creationreadingassistant.data.repository.TaxonomyRepository
import com.creationreadingassistant.data.settings.AISettings
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 107-A-v1：直接驱动**生产** `InspirationViewModel.mergeIntoMaterialCard` +
 * 生产 `InspirationRepository`（只把 Room DAO 换成同接口的内存实现），
 * 核 106 合并失败的三条可离线验证假设：
 *
 * 1. 列表已 `Loaded` 且两个 ID 都在列表内时，生产回调必须给出新卡 id，
 *    且新卡 payload 保留两条来源的 excerpt/locator，原条目归档并写 `mergedInto`；
 * 2. 列表尚未首发（`Loading`）时，生产代码回调 null 且零写入；
 * 3. `Loaded` 但某个 ID 不在列表内时，回调 null 且零写入。
 *
 * 这不是另写策略：断言对象就是 `InspirationViewModel` 的真实方法与
 * `InspirationRepository` 的真实 payload 编码，只替换了存储边界。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InspirationMergeCallbackProductionTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val payloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private lateinit var dao: InMemoryInspirationDao
    private lateinit var viewModel: InspirationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        dao = InMemoryInspirationDao()
        val repository = InspirationRepository(dao, mockk<InspirationVariantDao>(relaxed = true))
        val settings = mockk<SettingsStore>(relaxed = true) {
            every { ai } returns MutableStateFlow(AISettings())
            every { inspirationSort } returns MutableStateFlow("updated")
        }
        val bookRepository = mockk<BookRepository>(relaxed = true) {
            every { observeBooks() } returns flowOf(emptyList())
        }
        val taxonomy = mockk<TaxonomyRepository>(relaxed = true) {
            every { observeCategories() } returns flowOf(emptyList())
        }
        viewModel = InspirationViewModel(
            inspirationRepository = repository,
            bookRepository = bookRepository,
            aiClient = mockk<AiClient>(relaxed = true),
            settings = settings,
            taxonomyRepository = taxonomy,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loaded list merges both sources and keeps locators`() = runTest(mainDispatcher.scheduler) {
        val bookId = "t107a-book"
        val idA = "t107a-item-a"
        val idB = "t107a-item-b"
        dao.seed(bookId, idA, "甲", "第一章 甲", "摘录甲内容", LocatorCodec.encode(120, 0, 120, "摘录甲内容"))
        dao.seed(bookId, idB, "乙", "第二章 乙", "摘录乙内容", LocatorCodec.encode(300, 0, 300, "摘录乙内容"))

        val subscription = launch { viewModel.itemsState.collect { } }
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.itemsState.value is InspirationItemsState.Loaded)

        var callback: String? = "unset"
        viewModel.mergeIntoMaterialCard(listOf(idA, idB)) { callback = it }
        testScheduler.advanceUntilIdle()

        assertNotNull("已 Loaded 且两个 ID 都在列表内时，生产回调必须给出新卡 id", callback)
        val cardId = requireNotNull(callback)

        val card = dao.rows.getValue(cardId)
        val payload = payloadJson.decodeFromString(InspirationPayloadData.serializer(), requireNotNull(card.payload))
        assertEquals("新卡必须保留两条来源摘录", 2, payload.excerpts.size)
        assertEquals(
            "两条来源的 locator 必须逐条保留",
            setOf(
                LocatorCodec.encode(120, 0, 120, "摘录甲内容"),
                LocatorCodec.encode(300, 0, 300, "摘录乙内容"),
            ),
            payload.excerpts.mapNotNull { it.locatorJson }.toSet(),
        )
        assertEquals("新卡状态必须是待整理", "reviewing", card.status)

        val archivedA = dao.rows.getValue(idA)
        val archivedB = dao.rows.getValue(idB)
        assertEquals("原条目 A 必须归档", "archived", archivedA.status)
        assertEquals("原条目 B 必须归档", "archived", archivedB.status)
        assertEquals(
            "原条目 A 的 mergedInto 必须指向新卡",
            cardId,
            payloadJson.decodeFromString(InspirationPayloadData.serializer(), requireNotNull(archivedA.payload)).mergedInto,
        )
        assertEquals(
            "原条目 B 的 mergedInto 必须指向新卡",
            cardId,
            payloadJson.decodeFromString(InspirationPayloadData.serializer(), requireNotNull(archivedB.payload)).mergedInto,
        )

        subscription.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `merge before first emission returns null and writes nothing`() = runTest(mainDispatcher.scheduler) {
        val idA = "t107a-item-a"
        val idB = "t107a-item-b"
        dao.seed("t107a-book", idA, "甲", "第一章 甲", "摘录甲内容", null)
        dao.seed("t107a-book", idB, "乙", "第二章 乙", "摘录乙内容", null)
        assertTrue("未订阅时生产状态必须是 Loading", viewModel.itemsState.value is InspirationItemsState.Loading)
        val writesBefore = dao.writeCount

        var callback: String? = "unset"
        viewModel.mergeIntoMaterialCard(listOf(idA, idB)) { callback = it }
        testScheduler.advanceUntilIdle()

        assertNull("Loading 时生产回调必须是 null", callback)
        assertEquals("Loading 时不得产生任何写入", writesBefore, dao.writeCount)
    }

    @Test
    fun `merge with unknown id returns null and writes nothing`() = runTest(mainDispatcher.scheduler) {
        val idA = "t107a-item-a"
        dao.seed("t107a-book", idA, "甲", "第一章 甲", "摘录甲内容", null)
        val subscription = launch { viewModel.itemsState.collect { } }
        testScheduler.advanceUntilIdle()
        val writesBefore = dao.writeCount

        var callback: String? = "unset"
        viewModel.mergeIntoMaterialCard(listOf(idA, "t107a-missing")) { callback = it }
        testScheduler.advanceUntilIdle()

        assertNull("解析到的条目不足 2 条时生产回调必须是 null", callback)
        assertEquals("解析不足时不得产生任何写入", writesBefore, dao.writeCount)

        subscription.cancel()
        testScheduler.advanceUntilIdle()
    }

    // ── 存储边界替身：同接口的内存实现，不复制任何业务逻辑 ──────────────────

    private class InMemoryInspirationDao : InspirationDao {
        val rows = LinkedHashMap<String, InspirationEntity>()
        private val state = MutableStateFlow<List<InspirationEntity>>(emptyList())
        var writeCount = 0

        fun seed(bookId: String, id: String, title: String, chapter: String, excerpt: String, locator: String?) {
            val payload = InspirationPayloadData(
                source = InspirationSourceInfo(
                    bookId = bookId,
                    bookTitle = "T107中性来源",
                    chapterTitle = chapter,
                    excerpt = excerpt,
                    locatorJson = locator,
                ),
            )
            rows[id] = InspirationEntity(
                id = id,
                title = title,
                body = "正文-$id",
                status = "inbox",
                source_book_id = bookId,
                payload = Json { ignoreUnknownKeys = true; encodeDefaults = true }
                    .encodeToString(InspirationPayloadData.serializer(), payload),
                created_at = "2026-10-24T00:00:00Z",
                updated_at = "2026-10-24T00:00:00Z",
            )
            state.value = rows.values.toList()
        }

        override suspend fun upsert(inspiration: InspirationEntity) {
            writeCount += 1
            rows[inspiration.id] = inspiration
            state.value = rows.values.toList()
        }

        override fun observeAllActive(): Flow<List<InspirationEntity>> = state

        override fun observeStatsCreatedRows(): Flow<List<StatsCreatedRow>> = flowOf(emptyList())

        override suspend fun getById(id: String): InspirationEntity? = rows[id]

        override suspend fun getByIds(ids: Collection<String>): List<InspirationEntity> =
            ids.mapNotNull { rows[it] }

        override suspend fun search(q: String): List<InspirationEntity> = emptyList()

        override suspend fun getDeleted(): List<InspirationEntity> = emptyList()

        override fun observeByBook(bookId: String): Flow<List<InspirationEntity>> = flowOf(emptyList())
    }
}
