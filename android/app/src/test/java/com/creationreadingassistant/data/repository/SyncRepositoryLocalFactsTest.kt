package com.creationreadingassistant.data.repository

import android.content.Context
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.InspirationDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReadingSessionDao
import com.creationreadingassistant.data.local.entity.BookEntity
// 20-v1：新增合同测试用到灵感实体（既有 25 项未引用，故原本没有这条 import）
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReadingSessionEntity
import com.creationreadingassistant.data.remote.DeviceInfoProvider
import com.creationreadingassistant.data.remote.SyncApi
import com.creationreadingassistant.data.remote.SyncApiProvider
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.data.remote.SyncContract
import com.creationreadingassistant.domain.model.SyncEnvelope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
// 20-v1：新增合同测试用 verify 断言「push 仍发起一次、仍记录同步时间」。
// 注意 any() 不是顶层函数（它是 MockK 匹配器作用域内的成员），不能 import。
import io.mockk.verify
import io.mockk.CapturingSlot
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/**
 * R1（本地事实与远端提升列合并）专项回归。
 *
 * 锁定 SyncRepository.pull 的 applyBook / applyProgress / applySession 合并语义：
 *
 * 1. **本机事实不被远端覆盖**：`local_uri` / `local_content_path` / `content_status` /
 *    `size` / `cover_data_url` / `original_file_name` / `imported_at` / `description` /
 *    `current_location_json` 不在 LAN 同步 payload 合同内（桌面 LibraryBook 的
 *    filePath/coverPath 是对端设备自己的路径，绝不能落成"本机路径"），接受远端新版时
 *    必须原样保留本地值；
 * 2. **远端显式给值才消费**：payload 缺键（或显式 JSON null，合同外输入）时保留本地值，
 *    新记录用默认值；显式 0 是合法值，不等于缺键；
 * 3. **尺度与词汇按合同换算**：progress 的 `progressPercent` 为桌面 0..1 尺度
 *    （证据：electron normalizeProgressItem clamp01 + 0.995 完成阈值），落库 ×100；
 *    `completionState` 经 ReadingCompletionState.fromStorage 映射（completed→finished）；
 *    `completedAt` ISO 字符串 → epoch millis，不可解析视为未给出；
 * 4. **完成态/完成时间一致**：远端显式非完成态清空 completed_at；显式完成态保留/采纳
 *    完成时间，不伪造时间戳；
 * 5. **push 闭环**：payload 列为空的本地记录按 wire 合同回填（book 含 id/filePath/
 *    同步元数据；progress 按 0..1 尺度回填；session 按 Android 词汇回填）；**非空
 *    payload 一律合并而非透传**——pull 会保存远端非空 payload，本机阅读/取消完成
 *    只更新提升列，透传会把旧进度/状态/时长与旧内嵌 metadata 发回。合并保留未知
 *    字段，受管理字段与 metadata（revision/deviceId/updatedAt/deletedAt，桌面合并
 *    依据，证据：electron withSyncMetadata）一律取当前实体/信封值。filePath 只用
 *    payload 已有值或本机书籍事实，不伪造；两者皆无时跳过该条并给出可检查原因。
 * 6. **安全解析**：显式 JSON null、字符串冒充数字、数字冒充字符串、NaN/Infinity
 *    等非有限值一律按「未给出」处理（保留本地/默认值），绝不落字面量 "null"。
 *
 * 全部断言走真实 pull()/push() 调用链，捕获 DAO 实际收到的实体——不测镜像 helper。
 */
class SyncRepositoryLocalFactsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var apiProvider: SyncApiProvider
    private lateinit var api: SyncApi
    // 20-v1：暴露给新增的 push 合同测试，用于断言「类别禁用后其余合同仍保持」
    // （push 仍发起一次、仍记录同步时间）。仅为断言增加持有引用，不改变任何桩行为。
    private lateinit var configStore: SyncConfigStore
    private lateinit var bookDao: BookDao
    private lateinit var inspirationDao: InspirationDao
    private lateinit var readingProgressDao: ReadingProgressDao
    private lateinit var readingSessionDao: ReadingSessionDao
    private lateinit var repository: SyncRepository

    private val device = SyncContract.DeviceInfo(
        deviceId = "dev-1",
        name = "Test Phone",
        platform = SyncContract.SyncPlatform.android,
        pairedAt = "2026-01-01T00:00:00Z",
        lastSeenAt = "2026-01-01T00:00:00Z",
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** 本机书籍行：携带全部本机事实列。 */
    private fun localBook(
        id: String = "b1",
        revision: Int = 1,
        updatedAt: String = "2026-01-01T00:00:00Z",
    ) = BookEntity(
        id = id,
        title = "本地标题",
        author = "本地作者",
        format = "epub",
        original_file_name = "原始文件名.epub",
        content_hash = "local-hash",
        size = 123456,
        local_uri = "file:///data/b1/content.epub",
        local_content_path = "/data/b1/content.epub",
        content_status = "available",
        cover_data_url = "data:image/png;base64,AAAA",
        description = "本地描述",
        imported_at = "2025-12-01T00:00:00Z",
        revision = revision,
        updated_at = updatedAt,
    )

    /** 本机进度行：携带全部阅读事实提升列。 */
    private fun localProgress(
        revision: Int = 1,
        updatedAt: String = "2026-01-01T00:00:00Z",
    ) = ReadingProgressEntity(
        book_id = "b1",
        progress_percent = 42.5f,
        last_read_at = "2025-12-31T23:00:00Z",
        total_reading_time_ms = 5_000L,
        completion_state = "reading",
        current_location_json = """{"offset":900,"locator_v2":{"chapter":1,"charOffset":900}}""",
        revision = revision,
        updated_at = updatedAt,
    )

    private fun finishedProgress() = localProgress().copy(
        completion_state = "finished",
        progress_percent = 100f,
        completed_at = 1_760_000_000_000L,
    )

    @Before
    fun setUp() {
        api = mockk()
        apiProvider = mockk { every { current() } returns api }
        configStore = mockk(relaxed = true)
        val deviceInfoProvider = mockk<DeviceInfoProvider> { every { provide() } returns device }
        bookDao = mockk(relaxed = true) {
            coEvery { runInTransaction(any()) } coAnswers { firstArg<suspend () -> Unit>().invoke() }
        }
        inspirationDao = mockk(relaxed = true)
        readingProgressDao = mockk(relaxed = true)
        readingSessionDao = mockk(relaxed = true)
        val context = mockk<Context> { every { cacheDir } returns tempFolder.root }
        repository = SyncRepository(
            apiProvider = apiProvider,
            configStore = configStore,
            deviceInfoProvider = deviceInfoProvider,
            bookDao = bookDao,
            bookFileDao = mockk(relaxed = true),
            inspirationDao = inspirationDao,
            readingProgressDao = readingProgressDao,
            readingSessionDao = readingSessionDao,
            context = context,
            ioDispatcher = Dispatchers.Unconfined,
        )
        repository.fileUri = { file -> "file://" + file.absolutePath }
    }

    private fun pullResponse(
        books: List<SyncEnvelope<JsonObject>> = emptyList(),
        progress: List<SyncEnvelope<JsonObject>> = emptyList(),
        sessions: List<SyncEnvelope<JsonObject>> = emptyList(),
    ) = SyncContract.SyncPullResponse(
        manifest = SyncContract.SyncManifest(
            device = device,
            generatedAt = "2026-01-01T00:00:00Z",
            inspirations = emptyList(),
            books = emptyList(),
            progress = emptyList(),
            sessions = emptyList(),
            bookFiles = emptyList(),
        ),
        inspirations = emptyList(),
        books = books,
        progress = progress,
        sessions = sessions,
    )

    private fun envelope(
        id: String,
        type: String,
        revision: Int = 2,
        updatedAt: String = "2026-02-01T00:00:00Z",
        payload: JsonObject,
    ) = SyncEnvelope(
        id = id, type = type, revision = revision, deviceId = "remote-dev",
        updatedAt = updatedAt, deletedAt = null, payload = payload,
    )

    private fun capturedBook(): BookEntity {
        val slot = slot<BookEntity>()
        coVerify(exactly = 1) { bookDao.upsert(capture(slot)) }
        return slot.captured
    }

    private fun capturedProgress(): ReadingProgressEntity {
        val slot = slot<ReadingProgressEntity>()
        coVerify(exactly = 1) { readingProgressDao.upsert(capture(slot)) }
        return slot.captured
    }

    private fun capturedSession(): ReadingSessionEntity {
        val slot = slot<ReadingSessionEntity>()
        coVerify(exactly = 1) { readingSessionDao.upsert(capture(slot)) }
        return slot.captured
    }

    // ── applyBook ──────────────────────────────────────────────────────

    @Test
    fun `accepted book keeps local device facts and takes remote contract fields`() = runTest {
        coEvery { bookDao.getById("b1") } returns localBook()
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(
                envelope(
                    "b1", "book", payload = buildJsonObject {
                    put("title", "远端新标题")
                    put("author", "远端作者")
                    put("format", "epub")
                    put("contentHash", "remote-hash")
                },
                ),
            ),
        )

        val result = repository.pull()

        assertEquals(1, result.books)
        assertTrue(result.failedItems.isEmpty())
        val saved = capturedBook()
        // 远端合同字段被接受
        assertEquals("远端新标题", saved.title)
        assertEquals("远端作者", saved.author)
        assertEquals("remote-hash", saved.content_hash)
        assertEquals(2, saved.revision)
        assertEquals("remote-dev", saved.device_id)
        // 本机事实列原样保留（远端 payload 不携带，也不得取对端路径）
        assertEquals("/data/b1/content.epub", saved.local_content_path)
        assertEquals("file:///data/b1/content.epub", saved.local_uri)
        assertEquals("available", saved.content_status)
        assertEquals(123456, saved.size)
        assertEquals("data:image/png;base64,AAAA", saved.cover_data_url)
        assertEquals("原始文件名.epub", saved.original_file_name)
        assertEquals("2025-12-01T00:00:00Z", saved.imported_at)
        assertEquals("本地描述", saved.description)
    }

    @Test
    fun `accepted book with missing keys keeps local values instead of defaults`() = runTest {
        coEvery { bookDao.getById("b1") } returns localBook()
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(envelope("b1", "book", payload = buildJsonObject { put("title", "只有标题") })),
        )

        repository.pull()

        val saved = capturedBook()
        assertEquals("只有标题", saved.title)
        // 缺键：保留本地，不得重置为实体默认值
        assertEquals("本地作者", saved.author)
        assertEquals("epub", saved.format)
        assertEquals("local-hash", saved.content_hash)
    }

    @Test
    fun `new book without local row builds entity from payload contract`() = runTest {
        coEvery { bookDao.getById("b1") } returns null
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(
                envelope("b1", "book", payload = buildJsonObject { put("title", "新书") }),
            ),
        )

        repository.pull()

        val saved = capturedBook()
        assertEquals("新书", saved.title)
        assertNull(saved.author)
        assertEquals("txt", saved.format)
        assertNull(saved.local_content_path)
        assertEquals("available", saved.content_status)
        assertEquals(0, saved.size)
    }

    @Test
    fun `rejected older book leaves local row untouched`() = runTest {
        coEvery { bookDao.getById("b1") } returns localBook(revision = 3)
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(envelope("b1", "book", revision = 2, payload = buildJsonObject { put("title", "旧远端") })),
        )

        repository.pull()

        coVerify(exactly = 0) { bookDao.upsert(any()) }
    }

    // ── applyProgress ──────────────────────────────────────────────────

    @Test
    fun `accepted progress keeps local reading facts when payload lacks keys`() = runTest {
        coEvery { readingProgressDao.getByBook("b1") } returns localProgress()
        coEvery { api.pull(any()) } returns pullResponse(
            progress = listOf(envelope("b1", "progress", payload = buildJsonObject { })),
        )

        repository.pull()

        val saved = capturedProgress()
        assertEquals(42.5f, saved.progress_percent)
        assertEquals("reading", saved.completion_state)
        assertEquals("""{"offset":900,"locator_v2":{"chapter":1,"charOffset":900}}""", saved.current_location_json)
        assertEquals(5_000L, saved.total_reading_time_ms)
        assertEquals("2025-12-31T23:00:00Z", saved.last_read_at)
        assertNull(saved.completed_at)
        assertEquals(2, saved.revision)
        assertEquals("remote-dev", saved.device_id)
    }

    @Test
    fun `accepted progress applies remote explicit values in desktop scale`() = runTest {
        coEvery { readingProgressDao.getByBook("b1") } returns localProgress()
        coEvery { api.pull(any()) } returns pullResponse(
            progress = listOf(
                envelope(
                    "b1", "progress", payload = buildJsonObject {
                    // 桌面 0..1 尺度：0.32 → 32%
                    put("progressPercent", 0.32)
                    put("completionState", "completed")
                    put("completedAt", "2026-01-02T03:04:05Z")
                    put("lastReadAt", "2026-01-05T00:00:00Z")
                    put("totalReadingTimeMs", 7_000)
                },
                ),
            ),
        )

        repository.pull()

        val saved = capturedProgress()
        assertEquals(32f, saved.progress_percent, 0.01f)
        assertEquals("finished", saved.completion_state)
        assertEquals(Instant.parse("2026-01-02T03:04:05Z").toEpochMilli(), saved.completed_at)
        assertEquals("2026-01-05T00:00:00Z", saved.last_read_at)
        assertEquals(7_000L, saved.total_reading_time_ms)
        // 定位 JSON 是 Android 专属模式（locator_v2/offset），桌面 currentLocation 对象
        // 不是同一合同——本机定位保留，远端位置对象留在 payload 真相源里
        assertEquals("""{"offset":900,"locator_v2":{"chapter":1,"charOffset":900}}""", saved.current_location_json)
    }

    @Test
    fun `progress explicit zero is a legal value not absence`() = runTest {
        coEvery { readingProgressDao.getByBook("b1") } returns localProgress()
        coEvery { api.pull(any()) } returns pullResponse(
            progress = listOf(
                envelope(
                    "b1", "progress", payload = buildJsonObject {
                    // 只显式给了时长 0 与最近阅读；percent 缺键 → 保留本地 42.5%
                    put("totalReadingTimeMs", 0)
                    put("lastReadAt", "2026-01-06T00:00:00Z")
                },
                ),
            ),
        )

        repository.pull()

        val saved = capturedProgress()
        assertEquals(42.5f, saved.progress_percent)
        assertEquals(0L, saved.total_reading_time_ms)
        assertEquals("2026-01-06T00:00:00Z", saved.last_read_at)
    }

    @Test
    fun `progress completion state drives completed_at consistency both ways`() = runTest {
        // ① 远端显式标记完成：采纳完成时间
        coEvery { readingProgressDao.getByBook("b1") } returns localProgress()
        coEvery { api.pull(any()) } returns pullResponse(
            progress = listOf(
                envelope(
                    "b1", "progress", payload = buildJsonObject {
                    put("completionState", "completed")
                    put("completedAt", "2026-01-02T03:04:05Z")
                },
                ),
            ),
        )
        repository.pull()
        run {
            val saved = capturedProgress()
            assertEquals("finished", saved.completion_state)
            assertEquals(Instant.parse("2026-01-02T03:04:05Z").toEpochMilli(), saved.completed_at)
            assertEquals(42.5f, saved.progress_percent)
        }

        // ② 远端显式改回在读：完成时间清空，其余阅读事实保留
        setUp()
        coEvery { readingProgressDao.getByBook("b1") } returns finishedProgress()
        coEvery { api.pull(any()) } returns pullResponse(
            progress = listOf(
                envelope("b1", "progress", payload = buildJsonObject { put("completionState", "reading") }),
            ),
        )
        repository.pull()
        val reopened = capturedProgress()
        assertEquals("reading", reopened.completion_state)
        assertNull(reopened.completed_at)
        // percent / 定位 / 时长缺键保留（远端只说了状态）
        assertEquals(100f, reopened.progress_percent)
        assertEquals(5_000L, reopened.total_reading_time_ms)
    }

    @Test
    fun `progress with unparseable completedAt keeps local completion time`() = runTest {
        coEvery { readingProgressDao.getByBook("b1") } returns finishedProgress()
        coEvery { api.pull(any()) } returns pullResponse(
            progress = listOf(
                envelope(
                    "b1", "progress", payload = buildJsonObject {
                    put("completionState", "completed")
                    put("completedAt", "not-a-date")
                },
                ),
            ),
        )

        repository.pull()

        val saved = capturedProgress()
        assertEquals("finished", saved.completion_state)
        assertEquals(1_760_000_000_000L, saved.completed_at)
    }

    // ── applySession ───────────────────────────────────────────────────

    @Test
    fun `accepted session keeps local facts when payload lacks keys`() = runTest {
        coEvery { readingSessionDao.getById("s1") } returns ReadingSessionEntity(
            id = "s1", book_id = "b1", started_at = "2025-12-31T10:00:00Z", ended_at = "2025-12-31T10:30:00Z",
            duration_ms = 1_800_000L, progress_percent = 55f, created_at = "2025-12-31T10:00:00Z",
            revision = 1, updated_at = "2025-12-31T10:30:00Z",
        )
        coEvery { api.pull(any()) } returns pullResponse(
            sessions = listOf(envelope("s1", "session", payload = buildJsonObject { })),
        )

        repository.pull()

        val saved = capturedSession()
        assertEquals("b1", saved.book_id)
        assertEquals("2025-12-31T10:00:00Z", saved.started_at)
        assertEquals("2025-12-31T10:30:00Z", saved.ended_at)
        assertEquals(1_800_000L, saved.duration_ms)
        assertEquals(55f, saved.progress_percent)
        assertEquals("2025-12-31T10:00:00Z", saved.created_at)
        assertEquals(2, saved.revision)
    }

    @Test
    fun `session applies remote android vocabulary payload with valid zero duration`() = runTest {
        // Android ReadingSessionRecorder 的 payload 词汇（startedAt/endedAt/durationMs/
        // progressPercent，本机 0..100 尺度，证据：ReadingSessionRecorder.kt）
        coEvery { readingSessionDao.getById("s1") } returns null
        coEvery { api.pull(any()) } returns pullResponse(
            sessions = listOf(
                envelope(
                    "s1", "session", payload = buildJsonObject {
                    put("id", "s1")
                    put("bookId", "b1")
                    put("startedAt", "2026-01-01T10:00:00Z")
                    put("endedAt", "2026-01-01T10:05:00Z")
                    put("durationMs", 0)
                    put("progressPercent", 12.5f)
                    put("createdAt", "2026-01-01T10:00:00Z")
                    put("deviceId", "remote-dev")
                    put("revision", 2)
                    put("updatedAt", "2026-01-01T10:05:00Z")
                },
                ),
            ),
        )

        repository.pull()

        val saved = capturedSession()
        assertEquals("b1", saved.book_id)
        assertEquals("2026-01-01T10:00:00Z", saved.started_at)
        assertEquals("2026-01-01T10:05:00Z", saved.ended_at)
        assertEquals(0L, saved.duration_ms)
        assertEquals(12.5f, saved.progress_percent)
        assertEquals("2026-01-01T10:00:00Z", saved.created_at)
    }

    @Test
    fun `session applies remote desktop vocabulary keys`() = runTest {
        // 桌面 normalizeSessionItem 的 wire 词汇：startAt / endAt / durationMs（无进度键）
        coEvery { readingSessionDao.getById("s1") } returns null
        coEvery { api.pull(any()) } returns pullResponse(
            sessions = listOf(
                envelope(
                    "s1", "session", payload = buildJsonObject {
                    put("bookId", "b1")
                    put("startAt", "2026-01-01T12:00:00Z")
                    put("endAt", "2026-01-01T12:10:00Z")
                    put("durationMs", 600_000)
                },
                ),
            ),
        )

        repository.pull()

        val saved = capturedSession()
        assertEquals("2026-01-01T12:00:00Z", saved.started_at)
        assertEquals("2026-01-01T12:10:00Z", saved.ended_at)
        assertEquals(600_000L, saved.duration_ms)
        assertNull(saved.progress_percent)
        assertEquals("2026-02-01T00:00:00Z", saved.created_at) // 缺 createdAt → 现状回退 env.updatedAt
    }

    // ── push 闭环：空 payload 列按 wire 合同回填 ────────────────────────

    // 20-v1：新增可选 `inspirations`（默认空，既有 25 项调用点行为完全不变），
    // 使「灵感开关」也能被真实 payload 断言，而不是只能断言空集。
    private fun mockPushSources(
        books: List<BookEntity>,
        progress: List<ReadingProgressEntity> = emptyList(),
        sessions: List<ReadingSessionEntity> = emptyList(),
        inspirations: List<InspirationEntity> = emptyList(),
    ): CapturingSlot<SyncContract.SyncPushPayload> {
        every { bookDao.observeAllActive() } returns flowOf(books)
        coEvery { bookDao.getDeleted() } returns emptyList()
        every { inspirationDao.observeAllActive() } returns flowOf(inspirations)
        coEvery { inspirationDao.getDeleted() } returns emptyList()
        every { readingProgressDao.observeAllActive() } returns flowOf(progress)
        coEvery { readingProgressDao.getDeleted() } returns emptyList()
        every { readingSessionDao.observeAllActive() } returns flowOf(sessions)
        coEvery { readingSessionDao.getDeleted() } returns emptyList()
        val slot = slot<SyncContract.SyncPushPayload>()
        coEvery { api.push(capture(slot)) } returns SyncContract.SyncPushResult(
            ok = true,
            applied = SyncContract.SyncPushResult.AppliedCount(books = books.size),
            conflicts = emptyList(),
            manifest = SyncContract.SyncManifest(
                device = device,
                generatedAt = "2026-01-01T00:00:00Z",
                inspirations = emptyList(),
                books = emptyList(),
                progress = emptyList(),
                sessions = emptyList(),
                bookFiles = emptyList(),
            ),
        )
        return slot
    }

    @Test
    fun `push backfills book payload with id and filePath for desktop contract`() = runTest {
        val slot = mockPushSources(books = listOf(localBook()))
        repository.push()

        val payload = slot.captured.books.single().payload
        // 桌面 normalizeLibraryBook 要求 id/filePath/title 非空，否则整条丢弃
        assertEquals("b1", payload["id"]?.jsonPrimitive?.content)
        assertEquals("/data/b1/content.epub", payload["filePath"]?.jsonPrimitive?.content)
        assertEquals("本地标题", payload["title"]?.jsonPrimitive?.content)
        // 桌面 mergeByUpdatedAt 读取 payload 内嵌的同步元数据（withSyncMetadata）
        assertEquals(1, payload["revision"]?.jsonPrimitive?.content?.toInt())
        assertEquals("dev-1", payload["deviceId"]?.jsonPrimitive?.content)
        assertEquals("2026-01-01T00:00:00Z", payload["updatedAt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `push backfills progress payload in desktop percent scale`() = runTest {
        val slot = mockPushSources(
            books = listOf(localBook()),
            progress = listOf(finishedProgress().copy(payload = null)),
        )
        repository.push()

        val payload = slot.captured.progress.single().payload
        assertEquals("b1", payload["bookId"]?.jsonPrimitive?.content)
        assertEquals("/data/b1/content.epub", payload["filePath"]?.jsonPrimitive?.content)
        // 100% → 桌面 0..1 尺度 1.0（桌面 clamp01 会把 0..100 误收成 1.0=100%？——
        // 不：clamp01(100)=1.0 恰好对，但 47.42 会被钳成 1.0=100%，因此必须 ÷100）
        assertEquals(1.0, payload["progressPercent"]?.jsonPrimitive?.content?.toDouble()!!, 1e-6)
        assertEquals("completed", payload["completionState"]?.jsonPrimitive?.content)
        assertEquals(
            Instant.ofEpochMilli(1_760_000_000_000L).toString(),
            payload["completedAt"]?.jsonPrimitive?.content,
        )
        assertEquals(5_000L, payload["totalReadingTimeMs"]?.jsonPrimitive?.content?.toLong())
        assertEquals(1, payload["revision"]?.jsonPrimitive?.content?.toInt())
        assertEquals("2026-01-01T00:00:00Z", payload["updatedAt"]?.jsonPrimitive?.content)
    }

    @Test
    fun `push backfills session payload with android vocabulary`() = runTest {
        val slot = mockPushSources(
            books = listOf(localBook()),
            sessions = listOf(
                ReadingSessionEntity(
                    id = "s1", book_id = "b1", started_at = "2025-12-31T10:00:00Z",
                    ended_at = "2025-12-31T10:30:00Z", duration_ms = 1_800_000L,
                    progress_percent = 55f, created_at = "2025-12-31T10:00:00Z",
                    revision = 1, updated_at = "2025-12-31T10:30:00Z",
                ),
            ),
        )
        repository.push()

        val payload = slot.captured.sessions.single().payload
        assertEquals("s1", payload["id"]?.jsonPrimitive?.content)
        assertEquals("b1", payload["bookId"]?.jsonPrimitive?.content)
        assertEquals("/data/b1/content.epub", payload["filePath"]?.jsonPrimitive?.content)
        assertEquals("2025-12-31T10:00:00Z", payload["startedAt"]?.jsonPrimitive?.content)
        assertEquals("2025-12-31T10:30:00Z", payload["endedAt"]?.jsonPrimitive?.content)
        assertEquals(1_800_000L, payload["durationMs"]?.jsonPrimitive?.content?.toLong())
        assertEquals(55f, payload["progressPercent"]?.jsonPrimitive?.content?.toFloat()!!)
    }

    @Test
    fun `push merged session payload injects filePath refreshes managed fields and keeps unknown keys`() = runTest {
        // 实际 ReadingSessionRecorder 产生的非空 payload 形状（Android 词汇，无
        // filePath），叠加一个桌面合同外的未知字段；实体事实比 payload 新。
        val recorderPayload = """{"id":"s1","bookId":"b1","startedAt":"2026-01-01T10:00:00Z","endedAt":"2026-01-01T10:05:00Z","durationMs":300000,"progressPercent":10,"createdAt":"2026-01-01T10:00:00Z","deviceId":"dev-1","revision":1,"updatedAt":"2026-01-01T10:05:00Z","wallClock":"keep-me"}"""
        val slot = mockPushSources(
            books = listOf(localBook()),
            sessions = listOf(
                ReadingSessionEntity(
                    id = "s1", book_id = "b1", started_at = "2026-01-01T10:00:00Z",
                    ended_at = "2026-01-01T10:30:00Z", duration_ms = 1_800_000L,
                    progress_percent = 55f, created_at = "2026-01-01T10:00:00Z",
                    revision = 1, updated_at = "2026-01-01T10:30:00Z",
                    payload = recorderPayload,
                ),
            ),
        )
        repository.push()

        val payload = slot.captured.sessions.single().payload
        // filePath 按真实合同注入本机书籍事实（记录器 payload 不含路径，不伪造）
        assertEquals("/data/b1/content.epub", payload["filePath"]?.jsonPrimitive?.content)
        // 受管理字段与当前实体一致，不是旧 payload 值
        assertEquals(1_800_000L, payload["durationMs"]?.jsonPrimitive?.content?.toLong())
        assertEquals(55f, payload["progressPercent"]?.jsonPrimitive?.content?.toFloat())
        assertEquals("2026-01-01T10:00:00Z", payload["startedAt"]?.jsonPrimitive?.content)
        assertEquals("2026-01-01T10:30:00Z", payload["endedAt"]?.jsonPrimitive?.content)
        // metadata 与当前信封一致
        assertEquals(1, payload["revision"]?.jsonPrimitive?.content?.toInt())
        assertEquals("dev-1", payload["deviceId"]?.jsonPrimitive?.content)
        assertEquals("2026-01-01T10:30:00Z", payload["updatedAt"]?.jsonPrimitive?.content)
        // 未知字段保留
        assertEquals("keep-me", payload["wallClock"]?.jsonPrimitive?.content)
    }

    @Test
    fun `push merged progress payload carries current local facts not stale remote payload`() = runTest {
        // 真实调用链：pull 保存桌面非空 payload → 本地阅读推进（只更新提升列，
        // payload 列不变）→ push 必须发送当前事实与当前 metadata，而不是旧 payload
        val remotePayload = buildJsonObject {
            put("bookId", "b1")
            put("filePath", "/desktop/b1.epub")
            put("progressPercent", 0.10)
            put("completionState", "reading")
            put("lastReadAt", "2026-01-01T00:00:00Z")
            put("totalReadingTimeMs", 1_000)
            put("revision", 2)
            put("deviceId", "remote-dev")
            put("updatedAt", "2026-01-01T00:00:00Z")
            put("customUnknown", "keep-me")
        }
        coEvery { bookDao.getById("b1") } returns null
        coEvery { readingProgressDao.getByBook("b1") } returns null
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(envelope("b1", "book", payload = buildJsonObject { put("title", "书") })),
            progress = listOf(envelope("b1", "progress", payload = remotePayload)),
        )
        repository.pull()
        val pulled = capturedProgress()

        // 本地阅读推进：55% / 已读完 / 完成时间 / 时长与最近阅读 / updated_at 前进
        val locallyUpdated = pulled.copy(
            progress_percent = 55f,
            completion_state = "finished",
            completed_at = 1_760_000_000_000L,
            total_reading_time_ms = 9_000L,
            last_read_at = "2026-02-01T00:00:00Z",
            updated_at = "2026-02-01T00:00:00Z",
        )
        val pushSlot = mockPushSources(
            books = listOf(localBook()),
            progress = listOf(locallyUpdated),
        )
        repository.push()

        val payload = pushSlot.captured.progress.single().payload
        // 当前事实（旧 payload 是 0.10 / reading / 1000 / 旧时间）
        assertEquals(0.55, payload["progressPercent"]?.jsonPrimitive?.content?.toDouble()!!, 1e-6)
        assertEquals("completed", payload["completionState"]?.jsonPrimitive?.content)
        assertEquals(9_000L, payload["totalReadingTimeMs"]?.jsonPrimitive?.content?.toLong())
        assertEquals("2026-02-01T00:00:00Z", payload["lastReadAt"]?.jsonPrimitive?.content)
        assertEquals(Instant.ofEpochMilli(1_760_000_000_000L).toString(), payload["completedAt"]?.jsonPrimitive?.content)
        // metadata 与当前信封一致
        assertEquals(2, payload["revision"]?.jsonPrimitive?.content?.toInt())
        assertEquals("remote-dev", payload["deviceId"]?.jsonPrimitive?.content)
        assertEquals("2026-02-01T00:00:00Z", payload["updatedAt"]?.jsonPrimitive?.content)
        // 对端已有 filePath 保留（不伪造、不用本机路径覆盖对端自己的路径）
        assertEquals("/desktop/b1.epub", payload["filePath"]?.jsonPrimitive?.content)
        // 未知字段保留
        assertEquals("keep-me", payload["customUnknown"]?.jsonPrimitive?.content)
    }

    @Test
    fun `push skips merged records without any filePath and reports checkable reason`() = runTest {
        // 非空 payload（桌面词汇）既无 filePath、本书也无本机文件：无法形成桌面
        // 合同要求的合法记录——本地跳过并给出可检查原因，而不是发注定被对端
        // 丢弃的记录，也不伪造路径。空 payload 的书籍行走既有尽力而为回填路径
        // （含墓碑传播），不在本规则内。
        val noPathPayload = buildJsonObject {
            put("bookId", "b1")
            put("progressPercent", 0.10)
            put("completionState", "reading")
            put("totalReadingTimeMs", 1_000)
            put("revision", 2)
            put("deviceId", "remote-dev")
            put("updatedAt", "2026-01-01T00:00:00Z")
        }
        val slot = mockPushSources(
            books = listOf(
                BookEntity(id = "b1", title = "无文件书", format = "txt", revision = 1, updated_at = "2026-01-01T00:00:00Z"),
            ),
            progress = listOf(
                ReadingProgressEntity(
                    book_id = "b1", progress_percent = 10f, completion_state = "reading",
                    total_reading_time_ms = 1_000L, revision = 2, device_id = "remote-dev",
                    updated_at = "2026-01-01T00:00:00Z", payload = noPathPayload.toString(),
                ),
            ),
        )
        val result = repository.push()

        assertTrue(slot.captured.progress.isEmpty())
        assertEquals(1, result.failedItems.size)
        val failed = result.failedItems.single()
        assertEquals("progress", failed.type)
        assertEquals("b1", failed.id)
        assertTrue(failed.reason.contains("filePath"))
        // 既有空 payload 回填路径不受影响（书籍行照发，无书籍失败项）
        assertEquals(1, slot.captured.books.size)
        assertEquals(0, result.failedItems.count { it.type == "book" })
    }

    @Test
    fun `completed_at follows final merged state when remote omits state`() = runTest {
        // ① 本地 reading，远端缺 completionState 但带合法 completedAt：
        //    不得形成「非完成状态 + 完成时间」
        coEvery { readingProgressDao.getByBook("b1") } returns localProgress()
        coEvery { api.pull(any()) } returns pullResponse(
            progress = listOf(
                envelope("b1", "progress", payload = buildJsonObject { put("completedAt", "2026-01-02T03:04:05Z") }),
            ),
        )
        repository.pull()
        run {
            val saved = capturedProgress()
            assertEquals("reading", saved.completion_state)
            assertNull(saved.completed_at)
        }

        // ② 本地 shelved 同理
        setUp()
        coEvery { readingProgressDao.getByBook("b1") } returns localProgress().copy(completion_state = "shelved")
        coEvery { api.pull(any()) } returns pullResponse(
            progress = listOf(
                envelope("b1", "progress", payload = buildJsonObject { put("completedAt", "2026-01-02T03:04:05Z") }),
            ),
        )
        repository.pull()
        run {
            val saved = capturedProgress()
            assertEquals("shelved", saved.completion_state)
            assertNull(saved.completed_at)
        }

        // ③ 状态缺键 + 100% 不推断完成（S05-①：显式状态权威，percent 不反推）
        setUp()
        coEvery { readingProgressDao.getByBook("b1") } returns localProgress()
        coEvery { api.pull(any()) } returns pullResponse(
            progress = listOf(
                envelope("b1", "progress", payload = buildJsonObject { put("progressPercent", 1.0) }),
            ),
        )
        repository.pull()
        run {
            val saved = capturedProgress()
            assertEquals(100f, saved.progress_percent, 0.01f)
            assertEquals("reading", saved.completion_state)
            assertNull(saved.completed_at)
        }
    }

    @Test
    fun `book rejects explicit null and illegal types on both new and existing records`() = runTest {
        // ① 新记录：显式 null / 数字 / 布尔 / 对象一律不构成字符串值，
        //    不得落字面量 "null" 或把 123/true 变成字符串
        coEvery { bookDao.getById("b1") } returns null
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(
                envelope(
                    "b1", "book", payload = buildJsonObject {
                    put("title", JsonNull)
                    put("author", 123)
                    put("format", true)
                    put("contentHash", buildJsonObject { })
                },
                ),
            ),
        )
        repository.pull()
        run {
            val saved = capturedBook()
            assertEquals("(未命名)", saved.title)
            assertNull(saved.author)
            assertEquals("txt", saved.format)
            assertNull(saved.content_hash)
        }

        // ② 已有记录：非法类型与显式 null 同途（按缺键保留本地）
        setUp()
        coEvery { bookDao.getById("b1") } returns localBook()
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(envelope("b1", "book", payload = buildJsonObject { put("title", 123) })),
        )
        repository.pull()
        val saved = capturedBook()
        assertEquals("本地标题", saved.title)
        assertEquals("本地作者", saved.author)
        assertEquals("local-hash", saved.content_hash)
    }

    @Test
    fun `progress numeric fields reject non-finite values and illegal types`() = runTest {
        coEvery { readingProgressDao.getByBook("b1") } returns localProgress()
        coEvery { api.pull(any()) } returns pullResponse(
            progress = listOf(
                envelope(
                    "b1", "progress", payload = buildJsonObject {
                    put("progressPercent", Float.NaN)   // 非有限值 → 按未给出
                    put("totalReadingTimeMs", "7000")   // 字符串冒充数字 → 非法类型
                    put("lastReadAt", 123)              // 数字冒充字符串 → 非法类型
                },
                ),
            ),
        )
        repository.pull()

        val saved = capturedProgress()
        assertEquals(42.5f, saved.progress_percent)      // NaN 被拒 → 保留本地
        assertEquals(5_000L, saved.total_reading_time_ms) // 字符串数字被拒 → 保留本地
        assertEquals("2025-12-31T23:00:00Z", saved.last_read_at) // 非字符串被拒 → 保留本地
    }

    @Test fun mergedRemoteWinsEqualKeepsR1FactsAndRejectsWrongTypes() = runTest {
        coEvery { bookDao.getById("b1") } returns localBook(revision = 2)
        coEvery { api.pull(any()) } returns pullResponse(books = listOf(envelope(
            "b1", "book", revision = 2, updatedAt = "2020-01-01T00:00:00Z",
            payload = buildJsonObject { put("title", 123); put("author", "new-author") },
        )))
        repository.pull(SyncOptions(conflictStrategy = ConflictStrategy.REMOTE_WINS))
        val actual = capturedBook()
        assertEquals(localBook().title, actual.title)
        assertEquals(localBook().local_content_path, actual.local_content_path)
        assertEquals("new-author", actual.author)
    }
    @Test fun mergedLocalWinsEqualDoesNotWriteDespiteNewerTimestamp() = runTest {
        coEvery { bookDao.getById("b1") } returns localBook(revision = 2)
        coEvery { api.pull(any()) } returns pullResponse(books = listOf(envelope(
            "b1", "book", revision = 2, payload = buildJsonObject { put("title", "remote") },
        )))
        repository.pull(SyncOptions(conflictStrategy = ConflictStrategy.LOCAL_WINS))
        coVerify(exactly = 0) { bookDao.upsert(any()) }
    }
    @Test fun mergedLocalWinsStillAcceptsHigherRevisionWithLocalFacts() = runTest {
        coEvery { bookDao.getById("b1") } returns localBook(revision = 1)
        coEvery { api.pull(any()) } returns pullResponse(books = listOf(envelope(
            "b1", "book", revision = 2, payload = buildJsonObject { put("title", "remote") },
        )))
        repository.pull(SyncOptions(conflictStrategy = ConflictStrategy.LOCAL_WINS))
        assertEquals(localBook().local_content_path, capturedBook().local_content_path)
    }
    @Test fun mergedDisabledCategoriesPerformNoApply() = runTest {
        coEvery { api.pull(any()) } returns pullResponse(
            books = listOf(envelope("b1", "book", payload = buildJsonObject {})),
            progress = listOf(envelope("b1", "progress", payload = buildJsonObject {})),
            sessions = listOf(envelope("s1", "session", payload = buildJsonObject {})),
        )
        val result = repository.pull(SyncOptions(syncBooks = false, syncProgress = false, syncInspirations = false))
        assertEquals(0, result.books); assertEquals(0, result.progress); assertEquals(0, result.sessions)
        coVerify(exactly = 0) { bookDao.upsert(any()) }
        coVerify(exactly = 0) { readingProgressDao.upsert(any()) }
        coVerify(exactly = 0) { readingSessionDao.upsert(any()) }
    }

    // =========================================================================
    // 20-v1 · push 侧两项真实合同
    //
    // 与上面对称：pull 侧已有 mergedDisabledCategoriesPerformNoApply 门控落库，
    // 这里门控的是 **SyncRepository 真正交给 SyncApi.push 的 payload**。
    // 断言对象一律是 api.push 捕获到的 SyncPushPayload，不是「options 被透传」。
    // =========================================================================

    /** 一条能形成合法记录的灵感行（payload 为空 → 走提升列回填）。 */
    private fun pushableInspiration(id: String = "i1") = InspirationEntity(
        id = id,
        title = "灵感$id",
        body = "正文",
        created_at = "2026-01-01T00:00:00Z",
        revision = 1,
        updated_at = "2026-01-01T00:00:00Z",
    )

    /** 一条能形成合法记录的会话行（payload 为空 → filePath 取本机书籍事实）。 */
    private fun pushableSession(id: String = "s1") = ReadingSessionEntity(
        id = id,
        book_id = "b1",
        started_at = "2026-01-01T10:00:00Z",
        ended_at = "2026-01-01T10:30:00Z",
        duration_ms = 1_800_000L,
        progress_percent = 55f,
        created_at = "2026-01-01T10:00:00Z",
        revision = 1,
        updated_at = "2026-01-01T10:30:00Z",
    )

    /** 四类各一条可推送记录 —— 门控测试的统一输入。 */
    private fun oneOfEach(): CapturingSlot<SyncContract.SyncPushPayload> = mockPushSources(
        books = listOf(localBook()),
        progress = listOf(localProgress()),
        sessions = listOf(pushableSession()),
        inspirations = listOf(pushableInspiration()),
    )

    /**
     * 合同①-a：三个开关全关时，**四类全部不得出现在 payload 里**，但 push 仍要跑完一次。
     *
     * 抓住的错误实现：把 SyncOptions 收下却不做任何门控（四个列表仍是 1 条），
     * 或「关一个就全部不发」式的一刀切（本用例同时也是该实现的最小反例）。
     */
    @Test
    fun `push with every category disabled sends four empty lists but still pushes once`() = runTest {
        val slot = oneOfEach()

        val result = repository.push(
            SyncOptions(syncBooks = false, syncProgress = false, syncInspirations = false),
        )

        assertTrue("书架已关闭，books 必须为空", slot.captured.books.isEmpty())
        assertTrue("进度已关闭，progress 必须为空", slot.captured.progress.isEmpty())
        assertTrue("进度已关闭，sessions 必须为空", slot.captured.sessions.isEmpty())
        assertTrue("灵感已关闭，inspirations 必须为空", slot.captured.inspirations.isEmpty())

        // 其余合同保持：仍发起一次 push、仍带 device、仍记录同步时间、且不产生失败项
        coVerify(exactly = 1) { api.push(any()) }
        assertEquals(device.deviceId, slot.captured.device.deviceId)
        verify(exactly = 1) { configStore.markSyncedAt(any()) }
        assertTrue("关闭类别不是失败，不得记入 failedItems：${result.failedItems}", result.failedItems.isEmpty())
    }

    /**
     * 合同①-b：只关书架 —— 只有 books 为空，其余三类照发。
     *
     * 抓住的错误实现：开关串位（关书架却清了进度/灵感）或过度门控（关一个就全空）。
     */
    @Test
    fun `push with only books disabled empties books only`() = runTest {
        val slot = oneOfEach()

        repository.push(SyncOptions(syncBooks = false))

        assertTrue(slot.captured.books.isEmpty())
        assertEquals(1, slot.captured.progress.size)
        assertEquals(1, slot.captured.sessions.size)
        assertEquals(1, slot.captured.inspirations.size)
        assertEquals("b1", slot.captured.progress.single().id)
        assertEquals("s1", slot.captured.sessions.single().id)
        assertEquals("i1", slot.captured.inspirations.single().id)
    }

    /**
     * 合同①-c：只关「阅读进度与阅读历史」—— **progress 与 sessions 必须同时为空**。
     *
     * 抓住的错误实现：这两个类别共用一个开关，只门控 progress 而漏掉 sessions
     * （本用例的 sessions 断言会红）。
     */
    @Test
    fun `push with only progress disabled empties progress and sessions together`() = runTest {
        val slot = oneOfEach()

        repository.push(SyncOptions(syncProgress = false))

        assertTrue("progress 必须为空", slot.captured.progress.isEmpty())
        assertTrue("sessions 与 progress 同一开关，必须同时为空", slot.captured.sessions.isEmpty())
        assertEquals(1, slot.captured.books.size)
        assertEquals(1, slot.captured.inspirations.size)
        assertEquals("b1", slot.captured.books.single().id)
    }

    /**
     * 合同①-d：只关灵感 —— 只有 inspirations 为空，其余三类照发。
     *
     * 抓住的错误实现：书架与灵感开关串位。
     */
    @Test
    fun `push with only inspirations disabled empties inspirations only`() = runTest {
        val slot = oneOfEach()

        repository.push(SyncOptions(syncInspirations = false))

        assertTrue(slot.captured.inspirations.isEmpty())
        assertEquals(1, slot.captured.books.size)
        assertEquals(1, slot.captured.progress.size)
        assertEquals(1, slot.captured.sessions.size)
    }

    /**
     * 合同②：非空 payload 且**没有任何 filePath** 的墓碑 —— 当前保守合同是
     * 「跳过该条 + 给出可检查原因」，而不是伪造路径，也不是照发。
     *
     * 同一批 push 里另放两条**能形成合法记录**的墓碑作为邻接合同：
     * - `b-legacy`：payload 列为空 → 走既有「尽力而为回填」路径，filePath 允许空串，墓碑照发；
     * - `b-with-path`：非空 payload 自带 filePath → 正常发送。
     *
     * 抓住的错误实现：
     * - 删掉跳过门（照发注定被对端丢弃的记录）→ 「b-no-path 不在 payload」与「failedItems 只有 1 条」两条断言会红；
     * - 反转成「全部墓碑都过滤」→ 「b-legacy / b-with-path 仍照发」的断言会红；
     * - 跳过但不给原因或原因不点名 filePath → failedItems 断言会红。
     *
     * 本用例只钉住**当前保守行为**，不代表跨端墓碑兼容问题已解决（该接缝另行登记）。
     */
    @Test
    fun `push skips tombstone with payload but no filePath while tombstones that can form a record still propagate`() = runTest {
        val noPathTombstone = BookEntity(
            id = "b-no-path", title = "无路径墓碑", format = "txt",
            revision = 2, updated_at = "2026-02-01T00:00:00Z",
            deleted_at = "2026-02-01T00:00:00Z",
            payload = buildJsonObject { put("title", "无路径墓碑"); put("format", "txt") }.toString(),
        )
        val legacyTombstone = BookEntity(
            id = "b-legacy", title = "旧版墓碑", format = "txt",
            revision = 3, updated_at = "2026-02-02T00:00:00Z",
            deleted_at = "2026-02-02T00:00:00Z",
            payload = null,
        )
        val withPathTombstone = BookEntity(
            id = "b-with-path", title = "带路径墓碑", format = "txt",
            revision = 4, updated_at = "2026-02-03T00:00:00Z",
            deleted_at = "2026-02-03T00:00:00Z",
            payload = buildJsonObject {
                put("title", "带路径墓碑")
                put("filePath", "/desktop/b-with-path.txt")
            }.toString(),
        )
        val slot = mockPushSources(books = emptyList())
        // 三条都是软删除记录：只在 getDeleted 里出现
        coEvery { bookDao.getDeleted() } returns listOf(noPathTombstone, legacyTombstone, withPathTombstone)

        val result = repository.push()

        val pushed = slot.captured.books.associateBy { it.id }

        // ① 缺 filePath 的墓碑被跳过，且不伪造路径
        assertTrue("缺 filePath 的墓碑必须被跳过：${pushed.keys}", "b-no-path" !in pushed)
        val failed = result.failedItems.single { it.type == "book" }
        assertEquals("b-no-path", failed.id)
        assertTrue("跳过原因必须点名 filePath 以便排查：${failed.reason}", failed.reason.contains("filePath"))

        // ② 邻接合同：能形成合法记录的墓碑照发，删除标记完整
        assertEquals(setOf("b-legacy", "b-with-path"), pushed.keys)
        assertEquals("2026-02-02T00:00:00Z", pushed.getValue("b-legacy").deletedAt)
        assertEquals("2026-02-03T00:00:00Z", pushed.getValue("b-with-path").deletedAt)
        assertEquals("", pushed.getValue("b-legacy").payload["filePath"]?.jsonPrimitive?.content)
        assertEquals("/desktop/b-with-path.txt", pushed.getValue("b-with-path").payload["filePath"]?.jsonPrimitive?.content)

        // ③ 只有那一条被记失败：不得把全部墓碑都记成失败
        assertEquals(1, result.failedItems.size)
    }
}
