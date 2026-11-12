package com.creationreadingassistant.data.repository

import android.content.Context
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.remote.DeviceInfoProvider
import com.creationreadingassistant.data.remote.SyncApi
import com.creationreadingassistant.data.remote.SyncApiProvider
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.domain.model.SyncEnvelope
import com.creationreadingassistant.ui.viewmodel.InspirationDraft
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import com.creationreadingassistant.ui.viewmodel.InspirationViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import com.creationreadingassistant.data.repository.TaxonomyRepository
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

/**
 * CRA-INSPIRATION-SYNC-WIRE-FIX-v1 —— 灵感同步 wire payload 生产合同定向回归。
 *
 * 目标（F3）：**由原生保存路径实际产生的局部 payload**（`InspirationViewModel.saveInspiration`
 * 写入的 `InspirationPayloadData`：只含 tags/categoryIds/source/adoptions/excerpts/mergedInto，
 * encodeDefaults=true 恒非空）进入生产 `SyncRepository.push()` 后，wire payload 必须
 * **合成完整实体身份**（id/title/body/type/status/sourceBookId + 同步元信息），同时
 * **保留扩展字段**（source/excerpts/adoptions/tags/categoryIds/mergedInto 及未知键）。
 *
 * 关键：**不手工先塞齐 title/body**——实体一律由真实 VM 生产者产出，再喂给真实 push。
 * 旧实现「payload 非空即原样返回」会在这些用例上失败（红），修复后通过（绿）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InspirationSyncWireTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var apiProvider: SyncApiProvider
    private lateinit var api: SyncApi
    private lateinit var configStore: SyncConfigStore
    private lateinit var deviceInfoProvider: DeviceInfoProvider
    private lateinit var bookDao: BookDao
    private lateinit var bookFileDao: BookFileDao
    private lateinit var inspirationDao: InspirationDao
    private lateinit var readingProgressDao: ReadingProgressDao
    private lateinit var readingSessionDao: ReadingSessionDao
    private lateinit var context: Context

    private lateinit var syncRepository: SyncRepository

    private val device = SyncContract.DeviceInfo(
        deviceId = "dev-local",
        name = "Test Phone",
        platform = SyncContract.SyncPlatform.android,
        pairedAt = "2026-01-01T00:00:00Z",
        lastSeenAt = "2026-01-01T00:00:00Z",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        api = mockk()
        apiProvider = mockk { every { current() } returns api }
        configStore = mockk(relaxed = true)
        deviceInfoProvider = mockk { every { provide() } returns device }
        bookDao = mockk(relaxed = true) {
            coEvery { runInTransaction(any()) } coAnswers {
                firstArg<suspend () -> Unit>().invoke()
            }
        }
        bookFileDao = mockk(relaxed = true)
        inspirationDao = mockk(relaxed = true)
        readingProgressDao = mockk(relaxed = true)
        readingSessionDao = mockk(relaxed = true)
        context = mockk { every { cacheDir } returns tempFolder.root }
        syncRepository = SyncRepository(
            apiProvider = apiProvider,
            configStore = configStore,
            deviceInfoProvider = deviceInfoProvider,
            bookDao = bookDao,
            bookFileDao = bookFileDao,
            inspirationDao = inspirationDao,
            readingProgressDao = readingProgressDao,
            readingSessionDao = readingSessionDao,
            context = context,
            ioDispatcher = Dispatchers.Unconfined,
        )
        syncRepository.fileUri = { file -> "file://" + file.absolutePath }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 真实原生生产者：InspirationViewModel.saveInspiration 写入实体 ─────────────

    /** 用真实 VM 生产者产出实体（不手工填 title/body 之外的提升列）。 */
    private suspend fun produceEntity(
        draft: InspirationDraft,
        existingItems: List<InspirationEntity> = emptyList(),
    ): InspirationEntity {
        val inspirationRepository = mockk<InspirationRepository>(relaxed = true)
        every { inspirationRepository.observeAllActive() } returns flowOf(existingItems)
        val bookRepository = mockk<BookRepository>(relaxed = true)
        every { bookRepository.observeBooks() } returns flowOf(emptyList())
        val aiClient = mockk<AiClient>(relaxed = true)
        val settings = mockk<SettingsStore>(relaxed = true) {
            every { inspirationSort } returns MutableStateFlow("recent")
        }
        val vm = InspirationViewModel(
            inspirationRepository = inspirationRepository,
            bookRepository = bookRepository,
            aiClient = aiClient,
            settings = settings,
            taxonomyRepository = mockk<TaxonomyRepository>(relaxed = true).also { repo ->
                every { repo.observeCategories() } returns flowOf(emptyList())
            },
        )
        val saved = slot<InspirationEntity>()
        vm.saveInspiration(draft)
        coVerify(exactly = 1) { inspirationRepository.upsert(capture(saved)) }
        return saved.captured
    }

    private fun pushOk() = SyncContract.SyncPushResult(
        ok = true,
        applied = SyncContract.SyncPushResult.AppliedCount(inspirations = 1),
        conflicts = emptyList(),
        manifest = SyncContract.SyncManifest(
            device = device, generatedAt = "2026-01-01T00:00:00Z",
            inspirations = emptyList(), books = emptyList(), progress = emptyList(),
            sessions = emptyList(), bookFiles = emptyList(),
        ),
    )

    /** 把给定灵感实体喂给真实 push，返回被推送的 wire payload。 */
    private suspend fun pushAndCapture(entity: InspirationEntity): JsonObject {
        every { bookDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { bookDao.getDeleted() } returns emptyList()
        every { inspirationDao.observeAllActive() } returns flowOf(listOf(entity))
        coEvery { inspirationDao.getDeleted() } returns emptyList()
        every { readingProgressDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { readingProgressDao.getDeleted() } returns emptyList()
        every { readingSessionDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { readingSessionDao.getDeleted() } returns emptyList()
        val slot = slot<SyncContract.SyncPushPayload>()
        coEvery { api.push(capture(slot)) } returns pushOk()
        syncRepository.push()
        return slot.captured.inspirations.single().payload
    }

    private fun payloadOf(entity: InspirationEntity): InspirationPayloadData =
        Json { ignoreUnknownKeys = true; encodeDefaults = true }
            .decodeFromString(entity.payload!!)

    // ── 用例 ──────────────────────────────────────────────────────────────────

    @Test
    fun `native-produced local payload gets full identity in wire payload`() = runTest(mainDispatcher.scheduler) {
        // 真实生产路径：编辑器保存普通灵感，payload 只含 tags（局部形状）
        val entity = produceEntity(
            InspirationDraft(
                id = "insp-native-1", title = "标题原文", body = "个人正文内容",
                type = "note", status = "inbox",
                tags = listOf("人物", "冲突"), source = null,
            ),
        )
        // 前提：生产 payload 确实是局部形状（不含 title/body/id），否则本测试无意义
        val localPayload = payloadOf(entity)
        assertTrue("生产 payload 应含 tags", localPayload.tags.isNotEmpty())

        val wire = pushAndCapture(entity)

        // 修复目标：wire payload 合成完整实体身份
        assertEquals(entity.id, wire["id"]?.jsonPrimitive?.content)
        assertEquals("标题原文", wire["title"]?.jsonPrimitive?.content)
        assertEquals("个人正文内容", wire["body"]?.jsonPrimitive?.content)
        assertEquals("note", wire["type"]?.jsonPrimitive?.content)
        assertEquals("inbox", wire["status"]?.jsonPrimitive?.content)
        // 同步元信息
        assertEquals(entity.revision.toString(), wire["revision"]?.jsonPrimitive?.content)
        assertEquals("dev-local", wire["deviceId"]?.jsonPrimitive?.content)
        assertEquals(entity.updated_at, wire["updatedAt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `wire payload preserves extension fields and unknown keys`() = runTest(mainDispatcher.scheduler) {
        // 真实生产者产出（含 source 定位），再叠加扩展字段（模拟 R5 详情/聚合维护的 payload）
        val base = produceEntity(
            InspirationDraft(
                id = "insp-native-2", title = "素材卡", body = "合并正文",
                type = "note", status = "reviewing",
                tags = listOf("素材"),
                source = InspirationSourceInfo(bookId = "bk1", bookTitle = "某书", excerpt = "首来源摘录"),
            ),
        )
        val r5Payload = buildJsonObject {
            put("tags", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("素材")) })
            put("categoryIds", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("cat1")) })
            put("source", kotlinx.serialization.json.buildJsonObject {
                put("bookId", "bk1"); put("bookTitle", "某书")
            })
            put("adoptions", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject { put("kind", "text"); put("target", "章节3") })
            })
            put("excerpts", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject { put("bookId", "bk1"); put("excerpt", "摘录A") })
                add(kotlinx.serialization.json.buildJsonObject { put("bookId", "bk2"); put("excerpt", "摘录B") })
            })
            put("mergedInto", "insp-target")
            put("payloadExtra", "unknown-extension-value")
        }
        val entity = base.copy(payload = r5Payload.toString())

        val wire = pushAndCapture(entity)

        // 身份仍合成
        assertEquals(entity.id, wire["id"]?.jsonPrimitive?.content)
        assertEquals("素材卡", wire["title"]?.jsonPrimitive?.content)
        // 扩展字段全部保留
        assertEquals(1, (wire["categoryIds"] as kotlinx.serialization.json.JsonArray).size)
        assertEquals("insp-target", wire["mergedInto"]?.jsonPrimitive?.content)
        assertEquals("unknown-extension-value", wire["payloadExtra"]?.jsonPrimitive?.content)
        assertEquals(2, (wire["excerpts"] as kotlinx.serialization.json.JsonArray).size)
        assertEquals(1, (wire["adoptions"] as kotlinx.serialization.json.JsonArray).size)
        assertTrue("source 必须保留", wire.containsKey("source"))
    }

    @Test
    fun `edited entity title and body win over stale payload values`() = runTest(mainDispatcher.scheduler) {
        // 模拟：payload 里残留旧 title/body（如旧 pull 落库），实体提升列已被编辑为新值
        val stalePayload = buildJsonObject {
            put("title", "陈旧标题")
            put("body", "陈旧正文")
            put("type", "old-type")
            put("status", "old-status")
            put("tags", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("保留标签")) })
        }
        val entity = InspirationEntity(
            id = "insp-edit-1", title = "编辑后标题", body = "编辑后正文",
            type = "note", status = "inbox",
            payload = stalePayload.toString(),
            created_at = "2026-08-01T10:00:00Z", device_id = "dev-local",
            revision = 4, updated_at = "2026-08-02T10:00:00Z",
        )

        val wire = pushAndCapture(entity)

        assertEquals("编辑后标题", wire["title"]?.jsonPrimitive?.content)
        assertEquals("编辑后正文", wire["body"]?.jsonPrimitive?.content)
        assertEquals("note", wire["type"]?.jsonPrimitive?.content)
        assertEquals("inbox", wire["status"]?.jsonPrimitive?.content)
        // 扩展字段不因合并而丢
        assertTrue(wire.containsKey("tags"))
    }

    @Test
    fun `empty payload legacy path still builds from promoted columns`() = runTest(mainDispatcher.scheduler) {
        val entity = InspirationEntity(
            id = "insp-legacy", title = "旧版灵感", body = "旧正文",
            type = "note", status = "inbox", source_book_id = "bk9",
            payload = null,
            created_at = "2026-08-01T10:00:00Z", device_id = "dev-local",
            revision = 1, updated_at = "2026-08-01T10:00:00Z",
        )

        val wire = pushAndCapture(entity)

        assertEquals("insp-legacy", wire["id"]?.jsonPrimitive?.content)
        assertEquals("旧版灵感", wire["title"]?.jsonPrimitive?.content)
        assertEquals("旧正文", wire["body"]?.jsonPrimitive?.content)
        assertEquals("bk9", wire["sourceBookId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `tombstone inspiration is pushed with identity and deletedAt`() = runTest(mainDispatcher.scheduler) {
        val entity = InspirationEntity(
            id = "insp-gone", title = "已删灵感", body = "正文",
            type = "note", status = "inbox",
            payload = buildJsonObject { put("tags", kotlinx.serialization.json.buildJsonArray { }) }.toString(),
            created_at = "2026-08-01T10:00:00Z", device_id = "dev-local",
            revision = 6, updated_at = "2026-08-03T10:00:00Z", deleted_at = "2026-08-03T10:00:00Z",
        )

        val wire = pushAndCapture(entity)

        assertEquals("insp-gone", wire["id"]?.jsonPrimitive?.content)
        assertEquals("已删灵感", wire["title"]?.jsonPrimitive?.content)
        assertEquals("2026-08-03T10:00:00Z", wire["deletedAt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `inspiration sync disabled pushes no inspirations`() = runTest(mainDispatcher.scheduler) {
        val entity = produceEntity(
            InspirationDraft(
                id = "insp-off", title = "T", body = "B", type = "note", status = "inbox",
                tags = emptyList(), source = null,
            ),
        )
        every { bookDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { bookDao.getDeleted() } returns emptyList()
        every { inspirationDao.observeAllActive() } returns flowOf(listOf(entity))
        coEvery { inspirationDao.getDeleted() } returns emptyList()
        every { readingProgressDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { readingProgressDao.getDeleted() } returns emptyList()
        every { readingSessionDao.observeAllActive() } returns flowOf(emptyList())
        coEvery { readingSessionDao.getDeleted() } returns emptyList()
        val slot = slot<SyncContract.SyncPushPayload>()
        coEvery { api.push(capture(slot)) } returns pushOk()

        syncRepository.push(
            SyncOptions(syncBooks = false, syncInspirations = false, syncProgress = false),
        )

        assertTrue("关闭灵感同步时不得推送任何灵感", slot.captured.inspirations.isEmpty())
    }
}
