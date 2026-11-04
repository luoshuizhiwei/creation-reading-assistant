package com.creationreadingassistant.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.remote.AuthInterceptor
import com.creationreadingassistant.data.remote.DeviceInfoProvider
import com.creationreadingassistant.data.remote.SyncApiProvider
import com.creationreadingassistant.data.remote.SyncConfigStore
import com.creationreadingassistant.data.remote.SyncContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * J1-v2 返修：R1 SyncRepository 合并修复的**真 Room 持久化**仪器验证。
 *
 * J1 版在设备首跑暴露两类夹具缺陷，本版修复：
 * 1. **外键父行缺失**：`reading_progress.book_id → books.id`（Room FK，CASCADE）。
 *    原版直接 upsert 进度行导致 SQLiteConstraintException；pull 内的同类失败又被
 *    `runCatching` 吞成 failedItems，后续 `getByBook(...)!!` 以 NPE 遮蔽真实错误。
 *    本版先建合法 books 父行，并在任何 `!!` 之前断言 pull 计数、failedItems 与
 *    目标行存在。
 * 2. **副作用隔离**：`SyncConfigStore` / `DeviceInfoProvider` 为 final 类且经
 *    `SecurePrefs` 触碰 AndroidKeyStore 默认主密钥与真实加密 prefs
 *    （sync_config / cra_device_identity；`SecurePrefs.open` 解密失败还会删 prefs）。
 *    本版用 **mockk-android 测试替身**替换这两个提供者（androidTest 无 mockk 时
 *    首版误用真实实例），使测试对 qahost 的 prefs 文件与 KeyStore **零读写**；
 *    `SyncApiProvider` / `AuthInterceptor` / Retrofit / OkHttp / kotlinx.serialization
 *    / SyncRepository / Room 全部走真实生产代码。
 *
 * ## 与 SyncRepositoryLocalFactsTest 的分工
 *
 * - JVM 专项：逐字段合并规则矩阵（mock DAO，快速红绿）；
 * - 本文件（androidTest）：真 Room 落库 + 跨 close/reopen 持久化 + 真 wire 合同
 *   （pull 响应 JSON 与 push 请求 JSON 都走生产序列化路径）。
 *
 * ## 隔离边界
 *
 * - **持久库**：[DB_NAME] 是本文件私有唯一库名，与正式库 `AppDatabase.DB_NAME`
 *   （"creation_reading_assistant_native"）永不重名；@Before/@After 删除
 *   db + -wal + -shm + -journal 四件，重开按同名重建以证明持久化。
 * - **配置与设备身份**：mockk-android 替身——`SyncConfigStore.config` 固定返回
 *   指向进程内 MockWebServer 的测试配置（setter 经 relaxed mock 吞掉，
 *   `markSyncedAt` 因此为无副作用 no-op），`DeviceInfoProvider.provide()` 返回
 *   固定测试设备信息。**不构造任何真实 SecurePrefs / EncryptedSharedPreferences /
 *   AndroidKeyStore 路径**，因此同 UID 的 `sync_config`、`cra_device_identity`
 *   prefs 文件与默认主密钥在本测试中既不被读也不被写，@After `unmockkAll()`
 *   释放替身。单换 Context / prefs 文件名无法给出这种证明（KeyStore 是同 UID
 *   全局资源），替身是唯一能从构造上杜绝触碰的方案。
 * - **数据**：只有中性合成记录（测试书籍/测试进度），无书源文件、无封面文件；
 *   pull/push 路径不触碰 filesDir/books，**不修改 qahost 书库**。
 * - **网络**：`MockWebServer` 为进程内 127.0.0.1 明文服务（应用 network_security_config
 *   有意放行 cleartext，见该文件注释）；不访问任何真实桌面端或外部服务。
 * - **外键**：Room FK 保持开启（绝不关闭），所有进度行先建 books 父行。
 */
@RunWith(AndroidJUnit4::class)
class SyncRepositoryPersistenceTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repository: SyncRepository

    /** 解码 push 请求体用：与生产同构的宽容配置。 */
    private val json = Json { ignoreUnknownKeys = true }

    /** 测试用固定设备信息（替身返回值，绝不触真实设备身份）。 */
    private val testDevice = SyncContract.DeviceInfo(
        deviceId = "pk-test-device",
        name = "PK Test Host",
        platform = SyncContract.SyncPlatform.android,
        pairedAt = "2026-10-01T00:00:00Z",
        lastSeenAt = "2026-10-01T00:00:00Z",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        deleteDbFiles(context)
        db = openDb(context)

        server = MockWebServer()
        server.start()

        // 测试替身（J1-v2）：两个 final 提供者整体替换，杜绝 SecurePrefs /
        // AndroidKeyStore / 真实加密 prefs 的任何读写路径。
        val configStore = mockk<SyncConfigStore>(relaxed = true)
        every { configStore.config } returns SyncConfigStore.Config(
            baseUrl = server.url("/").toString(),
            token = "pk-test-token",
            deviceId = testDevice.deviceId,
            pairedAt = testDevice.pairedAt,
        )
        val deviceInfoProvider = mockk<DeviceInfoProvider>()
        every { deviceInfoProvider.provide() } returns testDevice

        repository = SyncRepository(
            // 真实 SyncApiProvider / AuthInterceptor / Retrofit / OkHttp：
            // current() 读替身的 config 指向 MockWebServer，wire 栈全程真实。
            apiProvider = SyncApiProvider(AuthInterceptor(configStore), configStore),
            configStore = configStore,
            deviceInfoProvider = deviceInfoProvider,
            bookDao = db.bookDao(),
            bookFileDao = db.bookFileDao(),
            inspirationDao = db.inspirationDao(),
            readingProgressDao = db.readingProgressDao(),
            readingSessionDao = db.readingSessionDao(),
            context = context,
            ioDispatcher = Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
        runCatching { server.shutdown() }
        unmockkAll()
        deleteDbFiles(ApplicationProvider.getApplicationContext())
    }

    // ── 夹具：合法 books 父行（外键父表）────────────────────────────────

    /** 进度行的合法父行；不建父行的进度 upsert 会被 Room FK 拒绝（J1 缺陷根因）。 */
    private suspend fun seedBook(id: String, updatedAt: String = "2026-01-01T00:00:00Z") {
        db.bookDao().upsert(
            BookEntity(
                id = id,
                title = "测试书籍$id",
                format = "txt",
                size = 2048,
                content_status = "available",
                imported_at = "2025-12-01T00:00:00Z",
                revision = 1,
                updated_at = updatedAt,
            ),
        )
    }

    // ── 1. 接受远端书籍：本机事实跨 close/reopen 保持 ────────────────────

    @Test
    fun acceptedRemoteBookKeepsLocalDeviceFactsAcrossReopen() = runBlocking {
        db.bookDao().upsert(
            BookEntity(
                id = "pk-book-1",
                title = "测试书籍(本地)",
                author = "本地作者",
                format = "epub",
                original_file_name = "测试EPUB.epub",
                content_hash = "local-hash",
                size = 123456,
                local_uri = "file:///data/pk-book-1/content.epub",
                local_content_path = "/data/pk-book-1/content.epub",
                content_status = "available",
                cover_data_url = "data:image/png;base64,AAAA",
                description = "本地描述",
                imported_at = "2025-12-01T00:00:00Z",
                revision = 1,
                updated_at = "2026-01-01T00:00:00Z",
            ),
        )
        // 桌面词汇 payload：rev2 新标题；不含任何本机事实键（桌面 filePath 是对端路径）
        val bookPayload = """{"id":"pk-book-1","filePath":"/desktop/pk-book-1.epub","title":"测试书籍(远端新版)","author":"远端作者","format":"epub","size":123456,"importedAt":"2025-11-01T00:00:00Z","revision":2,"deviceId":"desktop-1","updatedAt":"2026-02-01T00:00:00Z"}"""
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(pullBody(book = envelope("pk-book-1", "book", 2, "2026-02-01T00:00:00Z", bookPayload))),
        )

        val result = repository.pull()

        // 先断言同步结果本身，避免以 !! 的 NPE 遮蔽真实错误（J1 缺陷）
        assertEquals(1, result.books)
        assertEquals(0, result.progress)
        assertTrue("pull 失败条目：${result.failedItems}", result.failedItems.isEmpty())
        val after = requireNotNull(db.bookDao().getById("pk-book-1")) { "接受远端后书籍行必须存在" }
        // 同连接：远端合同字段被接受，本机事实列原样保留
        assertEquals("测试书籍(远端新版)", after.title)
        assertEquals(2, after.revision)
        assertEquals("/data/pk-book-1/content.epub", after.local_content_path)
        assertEquals("file:///data/pk-book-1/content.epub", after.local_uri)
        assertEquals("available", after.content_status)
        assertEquals(123456, after.size)
        assertEquals("data:image/png;base64,AAAA", after.cover_data_url)
        assertEquals("测试EPUB.epub", after.original_file_name)

        // 重开数据库：本机事实与远端合并结果**真实落盘**
        reopen()
        val reopened = requireNotNull(db.bookDao().getById("pk-book-1")) { "重开后书籍行必须仍在" }
        assertEquals("测试书籍(远端新版)", reopened.title)
        assertEquals(2, reopened.revision)
        assertEquals("/data/pk-book-1/content.epub", reopened.local_content_path)
        assertEquals("available", reopened.content_status)
        assertEquals(123456, reopened.size)
        assertEquals("data:image/png;base64,AAAA", reopened.cover_data_url)
    }

    // ── 2. 接受远端进度：本机阅读事实保持 + 远端显式值落库，跨重开仍在 ────

    @Test
    fun acceptedRemoteProgressKeepsLocalFactsAndAppliesExplicitValuesAcrossReopen() = runBlocking {
        // 合法 books 父行（外键）
        seedBook("pk-keep")
        seedBook("pk-remote")
        // pk-keep：本地已有阅读事实，远端 payload 缺键 → 必须原样保持
        db.readingProgressDao().upsert(
            ReadingProgressEntity(
                book_id = "pk-keep",
                progress_percent = 42.5f,
                last_read_at = "2025-12-31T23:00:00Z",
                total_reading_time_ms = 5_000L,
                completion_state = "finished",
                current_location_json = """{"offset":900,"locator_v2":{"chapter":1,"charOffset":900}}""",
                completed_at = 1_760_000_000_000L,
                revision = 1,
                updated_at = "2026-01-01T00:00:00Z",
            ),
        )
        // pk-remote：远端显式给值 → 采纳并持久化
        db.readingProgressDao().upsert(
            ReadingProgressEntity(
                book_id = "pk-remote",
                progress_percent = 10f,
                completion_state = "reading",
                total_reading_time_ms = 1_000L,
                revision = 1,
                updated_at = "2026-01-01T00:00:00Z",
            ),
        )
        val keepPayload = """{"bookId":"pk-keep","filePath":"/desktop/pk-keep.epub","revision":2,"deviceId":"desktop-1","updatedAt":"2026-02-01T00:00:00Z"}"""
        val remotePayload = """{"bookId":"pk-remote","filePath":"/desktop/pk-remote.epub","progressPercent":0.32,"completionState":"completed","completedAt":"2026-01-02T03:04:05Z","lastReadAt":"2026-01-05T00:00:00Z","totalReadingTimeMs":7000,"revision":2,"deviceId":"desktop-1","updatedAt":"2026-02-01T00:00:00Z"}"""
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                pullBody(
                    progress = envelope("pk-keep", "progress", 2, "2026-02-01T00:00:00Z", keepPayload) +
                        "," + envelope("pk-remote", "progress", 2, "2026-02-01T00:00:00Z", remotePayload),
                ),
            ),
        )

        val result = repository.pull()

        assertEquals(2, result.progress)
        assertTrue("pull 失败条目：${result.failedItems}", result.failedItems.isEmpty())
        // 同连接：pk-keep 本机事实保持；pk-remote 显式值采纳（桌面 0..1 → 原生 0..100）
        val kept = requireNotNull(db.readingProgressDao().getByBook("pk-keep")) { "pk-keep 进度行必须存在" }
        assertEquals(42.5f, kept.progress_percent)
        assertEquals("finished", kept.completion_state)
        assertEquals(5_000L, kept.total_reading_time_ms)
        assertEquals(1_760_000_000_000L, kept.completed_at)
        assertEquals("""{"offset":900,"locator_v2":{"chapter":1,"charOffset":900}}""", kept.current_location_json)
        val applied = requireNotNull(db.readingProgressDao().getByBook("pk-remote")) { "pk-remote 进度行必须存在" }
        assertEquals(32f, applied.progress_percent, 0.01f)
        assertEquals("finished", applied.completion_state)
        assertEquals(7_000L, applied.total_reading_time_ms)
        assertEquals(1_767_323_045_000L, applied.completed_at)

        // 重开数据库：两行合并结果真实落盘
        reopen()
        val keptReopened = requireNotNull(db.readingProgressDao().getByBook("pk-keep")) { "重开后 pk-keep 进度行必须仍在" }
        assertEquals(42.5f, keptReopened.progress_percent)
        assertEquals("finished", keptReopened.completion_state)
        assertEquals(5_000L, keptReopened.total_reading_time_ms)
        assertEquals(1_760_000_000_000L, keptReopened.completed_at)
        assertEquals("""{"offset":900,"locator_v2":{"chapter":1,"charOffset":900}}""", keptReopened.current_location_json)
        val appliedReopened = requireNotNull(db.readingProgressDao().getByBook("pk-remote")) { "重开后 pk-remote 进度行必须仍在" }
        assertEquals(32f, appliedReopened.progress_percent, 0.01f)
        assertEquals("finished", appliedReopened.completion_state)
        assertEquals(7_000L, appliedReopened.total_reading_time_ms)
        assertEquals(1_767_323_045_000L, appliedReopened.completed_at)
    }

    // ── 3. pull → 本地推进 → push：真实协议栈发出当前事实而非旧 payload ──

    @Test
    fun pullThenLocalProgressChangeThenPushSendsCurrentFactsNotStalePayload() = runBlocking {
        seedBook("pk-b1")
        db.readingProgressDao().upsert(
            ReadingProgressEntity(
                book_id = "pk-b1",
                progress_percent = 5f,
                completion_state = "reading",
                total_reading_time_ms = 500L,
                revision = 1,
                updated_at = "2026-01-01T00:00:00Z",
            ),
        )
        val stalePayload = """{"bookId":"pk-b1","filePath":"/desktop/pk-b1.epub","progressPercent":0.10,"completionState":"reading","lastReadAt":"2026-01-01T00:00:00Z","totalReadingTimeMs":1000,"revision":2,"deviceId":"desktop-1","updatedAt":"2026-01-01T00:00:00Z","customUnknown":"keep-me"}"""
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(pullBody(progress = envelope("pk-b1", "progress", 2, "2026-01-01T00:00:00Z", stalePayload))),
        )
        val pullResult = repository.pull()

        assertEquals(1, pullResult.progress)
        assertTrue("pull 失败条目：${pullResult.failedItems}", pullResult.failedItems.isEmpty())
        val pulled = requireNotNull(db.readingProgressDao().getByBook("pk-b1")) { "pull 后进度行必须存在" }
        assertEquals(2, pulled.revision)

        // 本地阅读推进（模拟真实阅读流：只更新提升列，payload 列保持 pull 存入的旧值）
        db.readingProgressDao().upsert(
            pulled.copy(
                progress_percent = 55f,
                completion_state = "finished",
                completed_at = 1_760_000_000_000L,
                total_reading_time_ms = 9_000L,
                last_read_at = "2026-02-01T00:00:00Z",
                updated_at = "2026-02-01T00:00:00Z",
            ),
        )
        assertEquals(
            55f,
            requireNotNull(db.readingProgressDao().getByBook("pk-b1")) { "本地推进后进度行必须存在" }.progress_percent,
        )

        server.enqueue(MockResponse().setResponseCode(200).setBody(pushOkBody()))
        val pushResult = repository.push()

        assertEquals(0, pushResult.failedItems.size)
        // 第 1 个请求 = pull，第 2 个 = push；用真实反序列化解码 wire 请求
        val pullRequest = requireNotNull(server.takeRequest(10, TimeUnit.SECONDS)) { "pull 请求必须已发出" }
        assertEquals("/sync/pull", pullRequest.path)
        val pushRequest = requireNotNull(server.takeRequest(10, TimeUnit.SECONDS)) { "push 请求必须已发出" }
        assertEquals("/sync/push", pushRequest.path)
        val decoded = json.decodeFromString(SyncContract.SyncPushPayload.serializer(), pushRequest.body.readUtf8())
        assertEquals(1, decoded.progress.size)
        val envelope = decoded.progress.single()
        assertEquals("pk-b1", envelope.id)
        assertEquals(2, envelope.revision)
        assertEquals("2026-02-01T00:00:00Z", envelope.updatedAt)
        val payload = envelope.payload
        // 当前事实（旧 payload 是 0.10 / reading / 1000 / 旧 updatedAt；
        // completedAt 为 1_760_000_000_000 millis 的 ISO 表示）
        assertEquals(0.55, payload["progressPercent"]!!.jsonPrimitive.content.toDouble(), 1e-6)
        assertEquals("completed", payload["completionState"]!!.jsonPrimitive.content)
        assertEquals(9_000L, payload["totalReadingTimeMs"]!!.jsonPrimitive.content.toLong())
        assertEquals("2026-02-01T00:00:00Z", payload["lastReadAt"]!!.jsonPrimitive.content)
        assertEquals("2025-10-09T08:53:20Z", payload["completedAt"]!!.jsonPrimitive.content)
        // metadata 与当前信封一致
        assertEquals(2, payload["revision"]!!.jsonPrimitive.content.toInt())
        assertEquals(envelope.deviceId, payload["deviceId"]!!.jsonPrimitive.content)
        assertEquals("2026-02-01T00:00:00Z", payload["updatedAt"]!!.jsonPrimitive.content)
        // 对端已有 filePath 保留（不伪造、不用本机路径覆盖对端自己的路径）；未知字段保留
        assertEquals("/desktop/pk-b1.epub", payload["filePath"]!!.jsonPrimitive.content)
        assertEquals("keep-me", payload["customUnknown"]!!.jsonPrimitive.content)
    }

    // ── 4. reading/shelved 不得带完成时间（pull 合并与 push 发出两端一致）──

    @Test
    fun readingAndShelvedRowsNeverCarryCompletedTimeAfterPullOrPush() = runBlocking {
        seedBook("pk-r")
        seedBook("pk-s")
        db.readingProgressDao().upsert(
            ReadingProgressEntity(
                book_id = "pk-r",
                progress_percent = 20f,
                completion_state = "reading",
                revision = 1,
                updated_at = "2026-01-01T00:00:00Z",
            ),
        )
        db.readingProgressDao().upsert(
            ReadingProgressEntity(
                book_id = "pk-s",
                progress_percent = 30f,
                completion_state = "shelved",
                revision = 1,
                updated_at = "2026-01-01T00:00:00Z",
            ),
        )
        // 远端缺 completionState 但带合法 completedAt：不得形成「非完成 + 完成时间」
        fun completedAtOnlyPayload(bookId: String) =
            """{"bookId":"$bookId","filePath":"/desktop/$bookId.epub","completedAt":"2026-01-02T03:04:05Z","revision":2,"deviceId":"desktop-1","updatedAt":"2026-02-01T00:00:00Z"}"""
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                pullBody(
                    progress = envelope("pk-r", "progress", 2, "2026-02-01T00:00:00Z", completedAtOnlyPayload("pk-r")) +
                        "," + envelope("pk-s", "progress", 2, "2026-02-01T00:00:00Z", completedAtOnlyPayload("pk-s")),
                ),
            ),
        )
        val result = repository.pull()

        assertEquals(2, result.progress)
        assertTrue("pull 失败条目：${result.failedItems}", result.failedItems.isEmpty())
        val reading = requireNotNull(db.readingProgressDao().getByBook("pk-r")) { "pk-r 进度行必须存在" }
        assertEquals("reading", reading.completion_state)
        assertNull(reading.completed_at)
        val shelved = requireNotNull(db.readingProgressDao().getByBook("pk-s")) { "pk-s 进度行必须存在" }
        assertEquals("shelved", shelved.completion_state)
        assertNull(shelved.completed_at)

        // push 端：受管理键 completedAt 为 null → wire payload 不携带该键
        server.enqueue(MockResponse().setResponseCode(200).setBody(pushOkBody()))
        val pushResult = repository.push()
        assertEquals(0, pushResult.failedItems.size)
        server.takeRequest(10, TimeUnit.SECONDS)!! // pull
        val pushRequest = requireNotNull(server.takeRequest(10, TimeUnit.SECONDS)) { "push 请求必须已发出" }
        val decoded = json.decodeFromString(SyncContract.SyncPushPayload.serializer(), pushRequest.body.readUtf8())
        val payloads = decoded.progress.associateBy { it.id }
        assertTrue("push 必须恰好携带 pk-r/pk-s 两条进度：${payloads.keys}", setOf("pk-r", "pk-s") == payloads.keys)
        val readingPayload = payloads.getValue("pk-r").payload
        val shelvedPayload = payloads.getValue("pk-s").payload
        assertFalse(readingPayload.containsKey("completedAt"))
        assertFalse(shelvedPayload.containsKey("completedAt"))
        assertEquals("reading", readingPayload["completionState"]!!.jsonPrimitive.content)
        assertEquals("shelved", shelvedPayload["completionState"]!!.jsonPrimitive.content)
    }

    // ── 基建 ─────────────────────────────────────────────────────────────

    private fun openDb(context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .allowMainThreadQueries()
            .build()

    /** 关闭当前连接并按同名重开：证明状态真实落盘而非仅存在于连接内存。 */
    private fun reopen() {
        db.close()
        db = openDb(ApplicationProvider.getApplicationContext())
    }

    private fun deleteDbFiles(context: Context) {
        val base = context.getDatabasePath(DB_NAME)
        listOf(base, java.io.File(base.path + "-wal"), java.io.File(base.path + "-shm"), java.io.File(base.path + "-journal"))
            .forEach { runCatching { it.delete() } }
    }

    private fun deviceJson(): String =
        """{"deviceId":"desktop-1","name":"Desktop","platform":"desktop","pairedAt":"2026-01-01T00:00:00Z","lastSeenAt":"2026-01-01T00:00:00Z"}"""

    private fun manifestJson(): String =
        """{"device":${deviceJson()},"generatedAt":"2026-01-01T00:00:00Z","inspirations":[],"books":[],"progress":[],"sessions":[],"bookFiles":[]}"""

    private fun pullBody(
        book: String = "",
        progress: String = "",
    ): String = """{"manifest":${manifestJson()},"inspirations":[],"books":[$book],"progress":[$progress],"sessions":[]}"""

    private fun envelope(id: String, type: String, revision: Int, updatedAt: String, payload: String): String =
        """{"id":"$id","type":"$type","revision":$revision,"deviceId":"desktop-1","updatedAt":"$updatedAt","deletedAt":null,"payload":$payload}"""

    private fun pushOkBody(): String =
        """{"ok":true,"applied":{"inspirations":0,"books":0,"progress":0,"sessions":0},"conflicts":[],"manifest":${manifestJson()}}"""

    private companion object {
        /** 本文件私有持久测试库；绝不可与 AppDatabase.DB_NAME 或其他测试库重合。 */
        private const val DB_NAME = "sync_persistence_test.db"
    }
}
