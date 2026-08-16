package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.InspirationVariantEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.data.settings.AISettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * InspirationViewModel 灵感编辑/AI 动作/删除与 payload 解析测试。
 *
 * 分层收敛后 ViewModel 仅依赖 InspirationRepository：本测试验证 VM 的
 * 草稿映射 / 委托调用 / AI 编排行为；软删除与候选版本采纳的落库细节
 * 由 InspirationRepositoryTest 覆盖。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InspirationViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var inspirationRepository: InspirationRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var aiClient: AiClient
    private lateinit var settings: SettingsStore
    private lateinit var inspirationSortFlow: MutableStateFlow<String>

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        inspirationRepository = mockk(relaxed = true)
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
        every { inspirationRepository.observeAllActive() } returns flowOf(inspirations)
        every { bookRepository.observeBooks() } returns flowOf(books)
        return InspirationViewModel(
            inspirationRepository = inspirationRepository,
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

        coVerify(exactly = 1) { inspirationRepository.upsert(capture(saved)) }
        val entity = saved.captured
        assertEquals("未命名灵感", entity.title)
        assertEquals("bk1", entity.source_book_id)

        val payload = Json.decodeFromString<InspirationPayloadData>(entity.payload!!)
        assertEquals(listOf("人物", "冲突"), payload.tags)
        assertEquals("某书", payload.source?.bookTitle)
        assertEquals(0.5f, payload.source?.progressPercent ?: -1f)
    }

    @Test
    fun `saveInspiration new draft with editor id keeps same id`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        vm.saveInspiration(
            InspirationDraft(
                id = "editor-id-A",
                title = "新灵感",
                body = "今天想到一个转折",
                type = "note",
                status = "inbox",
                tags = emptyList(),
                source = null,
            )
        )
        testScheduler.advanceUntilIdle()

        val saved = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationRepository.upsert(capture(saved)) }
        assertEquals("editor-id-A", saved.captured.id)
    }

    @Test
    fun `itemsState stays Loading until repository emits`() = runTest(mainDispatcher.scheduler) {
        every { inspirationRepository.observeAllActive() } returns flow { }
        every { bookRepository.observeBooks() } returns flowOf(emptyList())
        val vm = InspirationViewModel(
            inspirationRepository = inspirationRepository,
            bookRepository = bookRepository,
            aiClient = aiClient,
            settings = settings,
        )

        val job = launch { vm.itemsState.collect { } }
        testScheduler.advanceUntilIdle()

        assertTrue("无 emission 时保持 Loading", vm.itemsState.value is InspirationItemsState.Loading)
        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `itemsState first emission empty list becomes Loaded(empty) immediately`() = runTest(mainDispatcher.scheduler) {
        val repoFlow = MutableStateFlow<List<InspirationEntity>>(emptyList())
        every { inspirationRepository.observeAllActive() } returns repoFlow
        every { bookRepository.observeBooks() } returns flowOf(emptyList())
        val vm = InspirationViewModel(
            inspirationRepository = inspirationRepository,
            bookRepository = bookRepository,
            aiClient = aiClient,
            settings = settings,
        )

        assertTrue("订阅前为 Loading", vm.itemsState.value is InspirationItemsState.Loading)
        val observed = mutableListOf<InspirationItemsState>()
        val job = launch { vm.itemsState.collect { observed += it } }
        testScheduler.advanceUntilIdle()

        assertEquals(
            "首发空列表立即 Loaded(empty)，无固定延时",
            InspirationItemsState.Loaded(emptyList()),
            vm.itemsState.value,
        )
        assertTrue(
            "observed 中包含首发 Loaded(empty)",
            observed.any { it == InspirationItemsState.Loaded(emptyList()) },
        )
        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `saveInspiration tracks new id as pending until list emission includes it`() = runTest(mainDispatcher.scheduler) {
        val repoFlow = MutableStateFlow<List<InspirationEntity>>(emptyList())
        every { inspirationRepository.observeAllActive() } returns repoFlow
        every { bookRepository.observeBooks() } returns flowOf(emptyList())
        val vm = InspirationViewModel(
            inspirationRepository = inspirationRepository,
            bookRepository = bookRepository,
            aiClient = aiClient,
            settings = settings,
        )

        val job = launch { vm.itemsState.collect { } }
        testScheduler.advanceUntilIdle()

        vm.saveInspiration(
            InspirationDraft(
                id = "A",
                title = "新灵感",
                body = "正文",
                type = "note",
                status = "inbox",
                tags = emptyList(),
                source = null,
            )
        )
        testScheduler.advanceUntilIdle()
        assertTrue("刚保存尚未被观察到的 id 处于待解析", "A" in vm.pendingSavedIds.value)

        repoFlow.value = listOf(inspiration("A"))
        testScheduler.advanceUntilIdle()
        assertTrue("列表 emission 命中后清除待解析", vm.pendingSavedIds.value.isEmpty())

        job.cancel()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `saveInspiration update keeps created_at and id`() = runTest(mainDispatcher.scheduler) {
        val existing = inspiration("i1", "2026-07-01T10:00:00Z")
        val vm = createVm(inspirations = listOf(existing))
        val job = launch { vm.itemsState.collect { } }
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
        coVerify(exactly = 1) { inspirationRepository.upsert(capture(saved)) }
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
    fun `deleteInspiration delegates to repository`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        vm.deleteInspiration("i1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { inspirationRepository.deleteInspiration("i1") }
    }

    @Test
    fun `applyVariant delegates to repository`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()
        val variant = InspirationVariantEntity(
            id = "v1",
            inspiration_id = "i1",
            kind = "polish",
            content = "打磨后的正文",
            created_at = "2026-08-01T12:00:00Z",
        )

        vm.applyVariant("i1", variant)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { inspirationRepository.applyVariant("i1", variant) }
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

        coVerify(exactly = 0) { inspirationRepository.applyVariant(any(), any()) }
    }

    @Test
    fun `deleteVariant delegates to repository`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        vm.deleteVariant("v1")
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { inspirationRepository.deleteVariant("v1") }
    }

    @Test
    fun `observeVariants delegates to repository`() {
        every { inspirationRepository.observeVariants("i1") } returns flowOf(emptyList())
        val vm = createVm()

        vm.observeVariants("i1")

        verify(exactly = 1) { inspirationRepository.observeVariants("i1") }
    }

    @Test
    fun `runInspirationAction success stores variant with prompt and model`() = runTest(mainDispatcher.scheduler) {
        coEvery { aiClient.chat(any(), any()) } returns Result.success("候选文本")
        val vm = createVm()

        var done: Boolean? = null
        vm.runInspirationAction("i1", "polish", "我的标题", "原始灵感正文") { done = it }
        testScheduler.advanceUntilIdle()

        assertEquals(true, done)
        val saved = slot<InspirationVariantEntity>()
        coVerify(exactly = 1) { inspirationRepository.saveVariant(capture(saved)) }
        assertEquals("polish", saved.captured.kind)
        assertEquals("候选文本", saved.captured.content)
        assertEquals("gpt-3.5-turbo", saved.captured.model)
        assertTrue(saved.captured.prompt!!.contains("原始灵感正文"))
        assertTrue(saved.captured.prompt!!.contains("标题：我的标题"))
    }

    @Test
    fun `runInspirationAction failure reports false without storing`() = runTest(mainDispatcher.scheduler) {
        coEvery { aiClient.chat(any(), any()) } returns Result.failure(IllegalStateException("api down"))
        val vm = createVm()

        var done: Boolean? = null
        vm.runInspirationAction("i1", "expand", "标题", "正文") { done = it }
        testScheduler.advanceUntilIdle()

        assertEquals(false, done)
        coVerify(exactly = 0) { inspirationRepository.saveVariant(any()) }
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
