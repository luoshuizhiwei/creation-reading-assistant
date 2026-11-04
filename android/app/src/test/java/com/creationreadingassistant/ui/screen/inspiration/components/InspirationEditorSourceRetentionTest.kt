package com.creationreadingassistant.ui.screen.inspiration.components

import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.InspirationRepository
import com.creationreadingassistant.data.repository.TaxonomyRepository
import com.creationreadingassistant.data.settings.AISettings
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel
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
import kotlinx.coroutines.test.TestScope
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
 * 任务94-v1：编辑器来源保留回归。
 *
 * 走**真实保存路径**：`buildEditorDraft`（UI 保存按钮使用的同一函数）→
 * `InspirationViewModel.saveInspiration` → `InspirationRepository.upsert`，
 * 再解码落库 payload 断言来源字段。不是只测一个未被调用的 helper。
 *
 * 背景：旧编辑器无条件重建 `InspirationSourceInfo`，只写 bookId/书名/作者/位置/摘录，
 * 因此「只改分类」保存会把 chapterTitle、progressPercent、locatorJson 清空，
 * 书籍字典未加载时书名/作者也会丢。这是既有根因，本任务补齐保留要求。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InspirationEditorSourceRetentionTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private lateinit var inspirationRepository: InspirationRepository
    private lateinit var bookRepository: BookRepository

    private val bookId = "bk-94"
    private val originalSource = InspirationSourceInfo(
        bookId = bookId,
        bookTitle = "测试 TXT",
        bookAuthor = "测试作者",
        chapterTitle = "第二章 转折",
        progressPercent = 42.5f,
        excerpt = "他摸了摸怀表。",
        locatorJson = """{"v":2,"offset":1200,"ci":1,"co":80}""",
    )

    private val existing = InspirationEntity(
        id = "insp-94",
        title = "怀表",
        body = "我自己的想法",
        type = "note",
        status = "inbox",
        source_book_id = bookId,
        payload = json.encodeToString(
            InspirationPayloadData.serializer(),
            InspirationPayloadData(
                tags = listOf("人物"),
                categoryIds = listOf("c-unknown-94"),
                source = originalSource,
            ),
        ),
        created_at = "2026-10-20T00:00:00Z",
        updated_at = "2026-10-20T00:00:00Z",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        inspirationRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        every { bookRepository.observeBooks() } returns flowOf(emptyList())
        every { inspirationRepository.observeAllActive() } returns flowOf(listOf(existing))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * itemsState 是 `WhileSubscribed` 的 StateFlow：没有收集者时保持 Loading，
     * saveInspiration 会把它当成「新草稿」，从而拿不到既有 payload。这里像真实 UI
     * 一样订阅一次，让保存走「更新既有条目」路径。
     */
    private fun TestScope.primedVm(): InspirationViewModel {
        val vm = createVm()
        // backgroundScope 随测试结束自动取消，不会让 runTest 等一个永不完成的收集。
        backgroundScope.launch { vm.itemsState.collect { } }
        testScheduler.advanceUntilIdle()
        return vm
    }

    private fun createVm(): InspirationViewModel {
        val settings = mockk<SettingsStore>(relaxed = true) {
            every { ai } returns MutableStateFlow(AISettings())
            every { inspirationSort } returns MutableStateFlow("recent")
        }
        return InspirationViewModel(
            inspirationRepository = inspirationRepository,
            bookRepository = bookRepository,
            aiClient = mockk<AiClient>(relaxed = true),
            settings = settings,
            taxonomyRepository = mockk<TaxonomyRepository>(relaxed = true) {
                every { observeCategories() } returns flowOf(emptyList())
            },
        )
    }

    /** 模拟编辑器打开既有条目：三个来源输入按预填值填写，其它字段原样。 */
    private fun editorDraft(
        sourceBookId: String = originalSource.bookId ?: "",
        sourceLocation: String = originalSource.locationLabel ?: "",
        sourceExcerpt: String = originalSource.excerpt ?: "",
        selectedBook: BookEntity? = null,
        categories: List<String> = listOf("c-unknown-94"),
        categoriesTouched: Boolean = false,
    ) = buildEditorDraft(
        id = existing.id,
        title = existing.title,
        body = existing.body,
        type = existing.type,
        status = existing.status,
        tagsInput = "人物",
        sourceBookId = sourceBookId,
        sourceLocation = sourceLocation,
        sourceExcerpt = sourceExcerpt,
        selectedBook = selectedBook,
        originalSource = originalSource,
        selectedCategories = categories,
        categoriesTouched = categoriesTouched,
    )

    private fun TestScope.captureSavedPayload(): InspirationPayloadData {
        testScheduler.advanceUntilIdle()
        val slot = slot<InspirationEntity>()
        coVerify(exactly = 1) { inspirationRepository.upsert(capture(slot)) }
        return json.decodeFromString(InspirationPayloadData.serializer(), slot.captured.payload!!)
    }

    // ------------------------------------------------------------------ 来源保留

    @Test
    fun `category only edit keeps chapter progress and locator through viewmodel save`() =
        runTest(mainDispatcher.scheduler) {
            val vm = primedVm()
            // 只点分类：来源输入与预填完全一致。
            vm.saveInspiration(editorDraft(categories = listOf("c-new-94"), categoriesTouched = true))

            val payload = captureSavedPayload()
            assertEquals("来源必须原样保留", originalSource, payload.source)
            assertEquals(listOf("c-new-94"), payload.categoryIds)
            assertEquals(listOf("人物"), payload.tags)
        }

    @Test
    fun `unchanged source survives even when book dictionary is not loaded`() =
        runTest(mainDispatcher.scheduler) {
            val vm = primedVm()
            // selectedBook = null：书籍目录未加载，旧实现会把书名/作者写成 null。
            vm.saveInspiration(editorDraft(selectedBook = null))

            val payload = captureSavedPayload()
            assertEquals(originalSource, payload.source)
            assertEquals("测试 TXT", payload.source?.bookTitle)
            assertEquals("测试作者", payload.source?.bookAuthor)
            assertEquals("""{"v":2,"offset":1200,"ci":1,"co":80}""", payload.source?.locatorJson)
        }

    @Test
    fun `same book excerpt edit keeps position metadata`() = runTest(mainDispatcher.scheduler) {
        val vm = primedVm()
        vm.saveInspiration(
            editorDraft(sourceExcerpt = "他摸了摸怀表，又放下了。", selectedBook = null),
        )

        val payload = captureSavedPayload()
        assertEquals("他摸了摸怀表，又放下了。", payload.source?.excerpt)
        assertEquals("同一本书内改摘录不应丢章节", "第二章 转折", payload.source?.chapterTitle)
        assertEquals(42.5f, payload.source?.progressPercent)
        assertEquals(
            "同一本书内改摘录不应丢 locator",
            """{"v":2,"offset":1200,"ci":1,"co":80}""",
            payload.source?.locatorJson,
        )
    }

    @Test
    fun `switching to another book does not inherit old locator or chapter`() =
        runTest(mainDispatcher.scheduler) {
            val vm = primedVm()
            val otherBook = BookEntity(
                id = "bk-other-94",
                title = "另一本书",
                author = "另一作者",
                format = "txt",
                updated_at = "2026-10-20T00:00:00Z",
            )
            vm.saveInspiration(
                editorDraft(
                    sourceBookId = otherBook.id,
                    selectedBook = otherBook,
                ),
            )

            val payload = captureSavedPayload()
            assertEquals("bk-other-94", payload.source?.bookId)
            assertEquals("另一本书", payload.source?.bookTitle)
            assertEquals("另一作者", payload.source?.bookAuthor)
            assertNull("换书不得继承旧 locator", payload.source?.locatorJson)
            assertNull("换书不得继承旧章节", payload.source?.chapterTitle)
            assertNull("换书不得继承旧进度", payload.source?.progressPercent)
        }

    @Test
    fun `clearing all source inputs removes source`() = runTest(mainDispatcher.scheduler) {
        val vm = primedVm()
        vm.saveInspiration(
            editorDraft(sourceBookId = "", sourceLocation = "", sourceExcerpt = ""),
        )

        val payload = captureSavedPayload()
        assertNull(payload.source)
        // 其它非目标字段仍然保留
        assertEquals(listOf("人物"), payload.tags)
        assertEquals(listOf("c-unknown-94"), payload.categoryIds)
    }

    // ------------------------------------------------------------------ 分类与取消

    @Test
    fun `untouched categories keep unknown legacy ids`() = runTest(mainDispatcher.scheduler) {
        val vm = primedVm()
        vm.saveInspiration(editorDraft(categoriesTouched = false))
        assertEquals(
            "未动分类时未知旧 id 不得被静默删除",
            listOf("c-unknown-94"),
            captureSavedPayload().categoryIds,
        )
    }

    @Test
    fun `explicit category clear writes empty list and keeps source`() =
        runTest(mainDispatcher.scheduler) {
            val vm = primedVm()
            vm.saveInspiration(editorDraft(categories = emptyList(), categoriesTouched = true))
            val payload = captureSavedPayload()
            assertTrue("明确清空应写入空列表", payload.categoryIds.isEmpty())
            assertEquals("清空分类不得影响来源", originalSource, payload.source)
        }

    @Test
    fun `cancel does not write anything`() = runTest(mainDispatcher.scheduler) {
        // 取消 = 编辑器不调用 saveInspiration；草稿构造本身不得产生落库。
        primedVm()
        editorDraft(categoriesTouched = true, categories = emptyList())

        coVerify(exactly = 0) { inspirationRepository.upsert(any()) }
    }
}
