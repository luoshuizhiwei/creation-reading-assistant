package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.InspirationVariantDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.data.settings.AISettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * InspirationViewModel 灵感编辑/AI 动作/删除与 payload 解析测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InspirationViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var inspirationDao: InspirationDao
    private lateinit var variantDao: InspirationVariantDao
    private lateinit var bookRepository: BookRepository
    private lateinit var aiClient: AiClient
    private lateinit var settings: SettingsStore
    private lateinit var inspirationSortFlow: MutableStateFlow<String>

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        inspirationDao = mockk()
        variantDao = mockk()
        bookRepository = mockk()
        aiClient = mockk()
        inspirationSortFlow = MutableStateFlow("recent")
        settings = mockk {
            every { ai } returns MutableStateFlow(AISettings())
            every { inspirationSort } returns inspirationSortFlow
            coEvery { setInspirationSort(any()) } answers {
                inspirationSortFlow.value = it.invocation.args[0] as String
                Unit
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(
        inspirations: List<InspirationEntity> = emptyList(),
        books: List<BookEntity> = emptyList(),
    ): InspirationViewModel {
        every { inspirationDao.observeAllActive() } returns flowOf(inspirations)
        every { bookRepository.observeBooks() } returns flowOf(books)
        return InspirationViewModel(
            inspirationDao = inspirationDao,
            variantDao = variantDao,
            bookRepository = bookRepository,
            aiClient = aiClient,
            settings = settings,
        )
    }

    private fun inspiration(id: String, updatedAt: String = "2026-08-01T10:00:00Z"): InspirationEntity =
        InspirationEntity(
            id = id,
            title = "标题$id",
            body = "正文$id",
            updated_at = updatedAt,
            created_at = updatedAt,
        )

    @Test
    fun `saveInspiration new draft falls back title and encodes payload`() = runTest(mainDispatcher.scheduler) {
        coEvery { inspirationDao.upsert(any()) } returns Unit
        val vm = createVm()

        val saved = slot<InspirationEntity>()
        vm.saveInspiration(
            InspirationDraft(
                id = null,
                title = "   ",
                body = "今天想到一个转折",
                type = "note",
                status = "inbox",
                tags = listOf("人物", "冲突"),
                source = InspirationSourceInfo(bookId = "bk1", bookTitle = "某书", progressPercent = 0.5f),
            )
        )
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { inspirationDao.upsert(capture(saved)) }
        val entity = saved.captured
        assertEquals("未命名灵感", entity.title)
        assertEquals("bk1", entity.source_book_id)

        val payload = Json.decodeFromString<InspirationPayloadData>(entity.payload!!)
        assertEquals(listOf("人物", "冲突"), payload.tags)
        assertEquals("某书", payload.source?.bookTitle)
        assertEquals(0.5f, payload.source?.progressPercent ?: -1f)
    }

    @Test
    fun `saveInspiration update keeps created_at and id`() = runTest(mainDispatcher.scheduler) {
        coEvery { inspirationDao.upsert(any()) } returns Unit
        val existing = inspiration("i1", "2026-07-01T10:00:00Z")
        val vm = createVm(inspirations = listOf(existing))
        val job = launch { vm.items.collect { } }
        testScheduler.advanceUntilIdle()

        vm.saveInspiration(
            InspirationDraft(
                id = "i1",
                title = "新标题",
                body = "新正文",
                type = "note",
                status = "inbox",
                tags = emptyList(),
                source = null,
            )
        )
        testScheduler.advanceUntilIdle()

        val saved = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationDao.upsert(capture(saved)) }
        assertEquals("i1", saved.captured.id)
        assertEquals("新标题", saved.captured.title)
        assertEquals("2026-07-01T10:00:00Z", saved.captured.created_at)
        assertEquals(
            InspirationPayloadData(emptyList(), emptyList(), null),
            Json.decodeFromString<InspirationPayloadData>(saved.captured.payload!!),
        )

        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `deleteInspiration soft deletes existing entry`() = runTest(mainDispatcher.scheduler) {
        coEvery { inspirationDao.getById("i1") } returns inspiration("i1")
        coEvery { inspirationDao.upsert(any()) } returns Unit
        val vm = createVm()

        vm.deleteInspiration("i1")
        testScheduler.advanceUntilIdle()

        val saved = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationDao.upsert(capture(saved)) }
        assertTrue(saved.captured.deleted_at != null)
    }

    @Test
    fun `deleteInspiration missing id is no-op`() = runTest(mainDispatcher.scheduler) {
        coEvery { inspirationDao.getById("missing") } returns null
        coEvery { inspirationDao.upsert(any()) } returns Unit
        val vm = createVm()

        vm.deleteInspiration("missing")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { inspirationDao.upsert(any()) }
    }

    @Test
    fun `applyVariant updates body of existing inspiration`() = runTest(mainDispatcher.scheduler) {
        coEvery { inspirationDao.getById("i1") } returns inspiration("i1")
        coEvery { inspirationDao.upsert(any()) } returns Unit
        val vm = createVm()

        vm.applyVariant("i1", InspirationVariantEntity(
            id = "v1",
            inspiration_id = "i1",
            kind = "polish",
            content = "打磨后的正文",
            created_at = "2026-08-01T12:00:00Z",
        ))
        testScheduler.advanceUntilIdle()

        val saved = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationDao.upsert(capture(saved)) }
        assertEquals("打磨后的正文", saved.captured.body)
    }

    @Test
    fun `applyVariant with null content is no-op`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        vm.applyVariant("i1", InspirationVariantEntity(
            id = "v1",
            inspiration_id = "i1",
            kind = "polish",
            content = null,
            created_at = "2026-08-01T12:00:00Z",
        ))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { inspirationDao.getById(any()) }
        coVerify(exactly = 0) { inspirationDao.upsert(any()) }
    }

    @Test
    fun `runInspirationAction success stores variant with prompt and model`() = runTest(mainDispatcher.scheduler) {
        coEvery { aiClient.chat(any(), any()) } returns Result.success("候选文本")
        coEvery { variantDao.upsert(any()) } returns Unit
        val vm = createVm()

        var done: Boolean? = null
        vm.runInspirationAction("i1", "polish", "我的标题", "原始灵感正文") { done = it }
        testScheduler.advanceUntilIdle()

        assertEquals(true, done)
        val saved = slot<InspirationVariantEntity>()
        coVerify(exactly = 1) { variantDao.upsert(capture(saved)) }
        assertEquals("polish", saved.captured.kind)
        assertEquals("候选文本", saved.captured.content)
        assertEquals("gpt-3.5-turbo", saved.captured.model)
        assertTrue(saved.captured.prompt!!.contains("原始灵感正文"))
        assertTrue(saved.captured.prompt!!.contains("标题：我的标题"))
    }

    @Test
    fun `runInspirationAction failure reports false without storing`() = runTest(mainDispatcher.scheduler) {
        coEvery { aiClient.chat(any(), any()) } returns Result.failure(IllegalStateException("api down"))
        coEvery { variantDao.upsert(any()) } returns Unit
        val vm = createVm()

        var done: Boolean? = null
        vm.runInspirationAction("i1", "expand", "标题", "正文") { done = it }
        testScheduler.advanceUntilIdle()

        assertEquals(false, done)
        coVerify(exactly = 0) { variantDao.upsert(any()) }
    }

    @Test
    fun `runInspirationAction unknown action skips ai call`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        var done: Boolean? = null
        vm.runInspirationAction("i1", "not-an-action", "标题", "正文") { done = it }
        testScheduler.advanceUntilIdle()

        assertEquals(false, done)
        coVerify(exactly = 0) { aiClient.chat(any(), any()) }
    }

    @Test
    fun `runInspirationAction blank body skips ai call`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        var done: Boolean? = null
        vm.runInspirationAction("i1", "polish", "标题", "   ") { done = it }
        testScheduler.advanceUntilIdle()

        assertEquals(false, done)
        coVerify(exactly = 0) { aiClient.chat(any(), any()) }
    }

    @Test
    fun `tagsOf and sourceOf parse payload with fallback`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        val withPayload = InspirationEntity(
            id = "p1",
            title = "t",
            payload = """{"tags":["冲突"],"categoryIds":["c1"],"source":{"bookId":"bk9","bookTitle":"书","progressPercent":0.25}}""",
            updated_at = "2026-08-01T10:00:00Z",
            created_at = "2026-08-01T10:00:00Z",
        )
        assertEquals(listOf("冲突"), vm.tagsOf(withPayload))
        assertEquals("bk9", vm.sourceOf(withPayload)?.bookId)
        assertEquals(0.25f, vm.sourceOf(withPayload)?.progressPercent ?: -1f)

        val badPayload = InspirationEntity(
            id = "p2",
            title = "t",
            payload = "not-json{{{",
            updated_at = "2026-08-01T10:00:00Z",
            created_at = "2026-08-01T10:00:00Z",
        )
        assertTrue(vm.tagsOf(badPayload).isEmpty())
        assertNull(vm.sourceOf(badPayload)?.bookId)

        assertTrue(vm.tagsOf(InspirationEntity(
            id = "p3", title = "t", payload = null,
            updated_at = "2026-08-01T10:00:00Z", created_at = "2026-08-01T10:00:00Z",
        )).isEmpty())
    }

    @Test
    fun `setInspirationSort persists to settings`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        vm.setInspirationSort("updated_desc")
        testScheduler.advanceUntilIdle()

        assertEquals("updated_desc", inspirationSortFlow.value)
    }
}
