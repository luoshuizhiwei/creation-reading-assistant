package com.creationreadingassistant.acceptance

import android.content.Context
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.BookFileDao
import com.creationreadingassistant.data.local.dao.HighlightDao
import com.creationreadingassistant.data.local.dao.LibrarySourceRefDao
import com.creationreadingassistant.data.local.dao.NoteDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.LibrarySourceAvailability
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinator
import com.creationreadingassistant.data.settings.ImportHistoryStore
import com.creationreadingassistant.feature.library.LibraryRootStore
import com.creationreadingassistant.feature.library.LibrarySourceIndex
import com.creationreadingassistant.feature.library.ShelfImporter
import com.creationreadingassistant.feature.library.SourceRefreshResult
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.FileOps
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * CRA-D2-DEVICE-ARTIFACTS-REPAIR-v4 —— D2「安全关联 / 刷新」的真正隔离与完整持久化验收。
 *
 * 核心增强（余项 1、余项 2、余项 3 闭环）：
 * 1. DataStore 实际路径保护与前后指纹对账：
 *    - 监控 import_history 与 library_root 两个具体 Store 在测试隔离与宿主目录的前后指纹；
 *    - actualDataStoreFiles 与 candidateDataStorePaths 真实落盘，安全范围不确定时标 MAY_TOUCH_BOTH；
 * 2. 真实执行全实体规范化差分比较：
 *    - SAME 强断言所有相关行非空，消除 if (file != null) 跳过漏洞；
 *    - 真实执行 normalize 过滤允许变更列后进行整实体比对；
 *    - Refresh 严核 Provider 实际 size、last_modified、content_hash；
 * 3. 证据身份绑定唯一 Run ID 与失败现场保护：
 *    - 按 run 创建独立输出目录 evidence/runs/<runId>/，杜绝新失败残留旧成功；
 *    - 失败时捕获异常并保留现场快照与错误堆栈；
 *    - 导出 run-manifest.json 供下游精确消费。
 */
@RunWith(AndroidJUnit4::class)
class D2RelinkPersistenceAcceptTest {

    companion object {
        const val DB_NAME = "d2-accept.db"
        const val AUTHORITY = "com.creationreadingassistant.test.documents"

        const val TXT_BOOK_ID = "d2-accept-txt-b1"
        const val TXT_BOOK_TITLE = "测试TXT"
        const val TXT_FILE_NAME = "d2-accept-txt-b1.txt"
        const val TXT_ORIGINAL = "第一段正文标记ALPHA。\n第二段正文标记BETA。\n第三段正文标记GAMMA。\n"
        const val TXT_REFRESHED = "刷新后正文标记DELTA。\n第二段正文标记BETA。\n第三段正文标记GAMMA。\n"
        const val TXT_DIFFERENT = "完全不同的正文标记OMEGA。\n"

        const val EPUB_BOOK_ID = "d2-accept-epub-b2"
        const val EPUB_BOOK_TITLE = "测试EPUB"
        const val EPUB_FILE_NAME = "d2-accept-epub-b2.epub"
        const val EPUB_ORIGINAL_BODY = "EPUB第一章原始正文标记ALPHA。\n第二段正文BETA。\n"
        const val EPUB_DIFFERENT_BODY = "EPUB完全不同的正文标记OMEGA。\n"

        /**
         * 运行级稳定自有根目录：保证静态 DataStore PreferencesDelegate 具有稳定有效的上下文，
         * 避免每个测试方法频繁生成不同路径并删除旧 root 导致单例内部句柄失效。
         */
        val RUN_STABLE_ROOT: File by lazy {
            val baseContext = ApplicationProvider.getApplicationContext<Context>()
            File(baseContext.cacheDir, "d2-accept-run-stable").also { it.mkdirs() }
        }

        /**
         * 本次测试运行的全局唯一 Run 标识（余项 3）。
         */
        val CURRENT_RUN_ID: String = "d2-run-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"

        /**
         * 本次运行专属的独立证据输出目录。
         */
        val RUN_EVIDENCE_DIR: File by lazy {
            File(RUN_STABLE_ROOT, "evidence/runs/$CURRENT_RUN_ID").also { it.mkdirs() }
        }

        /** 汇总证据收集清单（按 run 隔离） */
        val allEvidenceList = mutableListOf<D2MachineReadableEvidence>()

        @JvmStatic
        @AfterClass
        fun afterClass() {
            // 1. 写出当轮所有方法汇总结果
            val summaryFile = File(RUN_EVIDENCE_DIR, "d2-evidence-summary.json")
            D2EvidenceWriter.writeSummary(allEvidenceList, summaryFile)

            // 2. 写出当轮 Manifest 清单与消费合同
            val manifestFile = File(RUN_EVIDENCE_DIR, "run-manifest.json")
            val manifestObj = JSONObject().apply {
                put("run_id", CURRENT_RUN_ID)
                put("timestamp", System.currentTimeMillis())
                put("method_count", allEvidenceList.size)
                put("methods", JSONArray(allEvidenceList.map { it.testName }))
                put("passed_count", allEvidenceList.count { it.status == "PASSED" })
                put("failed_count", allEvidenceList.count { it.status == "FAILED" })
                put("contract", "下游（如工件 C）仅允许消费此 run-manifest 所列出的属于本 runId 的证据快照")
            }
            manifestFile.writeText(manifestObj.toString(2), Charsets.UTF_8)

            // 3. 记录最新一轮 run 标识索引（旧 run 目录完整保留，不删除）
            val latestIndexFile = File(RUN_STABLE_ROOT, "evidence/latest_run.txt")
            latestIndexFile.parentFile?.mkdirs()
            latestIndexFile.writeText(CURRENT_RUN_ID, Charsets.UTF_8)
        }
    }

    private lateinit var base: Context
    private lateinit var root: File
    private lateinit var testContext: D2AcceptTestContext
    private lateinit var provider: D2AcceptDocProvider
    private lateinit var db: AppDatabase
    private lateinit var importer: ShelfImporter
    private lateinit var epubRepo: EpubRepository

    private lateinit var bookDao: BookDao
    private lateinit var contentDao: BookContentDao
    private lateinit var fileDao: BookFileDao
    private lateinit var progressDao: ReadingProgressDao
    private lateinit var noteDao: NoteDao
    private lateinit var highlightDao: HighlightDao
    private lateinit var sourceDao: LibrarySourceRefDao

    /** TXT 内部稳定副本路径：`<filesDir>/books/<bookId>/<fileName>` */
    private val txtInternalCopy: File
        get() = File(File(testContext.filesDir, "books/$TXT_BOOK_ID"), TXT_FILE_NAME)

    /** EPUB 内部稳定副本路径：`<filesDir>/books/epub/<bookId>.epub` */
    private val epubInternalCopy: File
        get() = File(File(testContext.filesDir, "books/epub"), "$EPUB_BOOK_ID.epub")

    @Before
    fun setUp() {
        base = ApplicationProvider.getApplicationContext()
        root = RUN_STABLE_ROOT

        provider = D2AcceptDocProvider().apply {
            attachInfo(base, ProviderInfo().apply { authority = AUTHORITY })
        }
        testContext = D2AcceptTestContext(base, root, provider)

        // 关键：在方法测试执行前抓取初始 DataStore 指纹基线
        testContext.snapshotInitialStoreFingerprints()

        initDatabase()

        // 验证数据库隔离路径严格位于 root 内
        testContext.assertDatabasePathIsolated(db)

        val bookRepository = BookRepository(
            bookDao = bookDao,
            bookContentDao = contentDao,
            bookFileDao = fileDao,
            progressDao = progressDao,
            sessionDao = db.readingSessionDao(),
            noteDao = noteDao,
            highlightDao = highlightDao,
            inspirationDao = db.inspirationDao(),
            bookTagDao = db.bookTagDao(),
            bookCategoryDao = db.bookCategoryDao(),
            shelfBookDao = db.shelfBookDao(),
            chapterReadDao = db.chapterReadDao(),
            context = testContext,
        )
        epubRepo = EpubRepository(
            context = testContext,
            bookDao = bookDao,
            readingProgressDao = progressDao,
            ioDispatcher = Dispatchers.IO,
            fileOps = RealFileOps,
        )
        importer = ShelfImporter(
            context = testContext,
            repository = bookRepository,
            bookDao = bookDao,
            bookContentDao = contentDao,
            bookFileDao = fileDao,
            epubRepository = epubRepo,
            importHistoryStore = ImportHistoryStore(testContext, CoroutineScope(Dispatchers.IO)),
            sourceIndex = LibrarySourceIndex(sourceDao),
            rootStore = LibraryRootStore(testContext, CoroutineScope(Dispatchers.IO)),
            ioDispatcher = Dispatchers.IO,
            indexRefreshCoordinator = mockk<SearchIndexBookRefreshCoordinator>(relaxed = true),
        )
    }

    private fun initDatabase() {
        db = Room.databaseBuilder(testContext, AppDatabase::class.java, DB_NAME)
            .addCallback(AppDatabase.CreateIndexCallback())
            .build()
        bookDao = db.bookDao()
        contentDao = db.bookContentDao()
        fileDao = db.bookFileDao()
        progressDao = db.readingProgressDao()
        noteDao = db.noteDao()
        highlightDao = db.highlightDao()
        sourceDao = db.librarySourceRefDao()
    }

    @After
    fun tearDown() {
        // 精确清理数据库连接与临时文件，保护 DataStore 目录生命周期与宿主文件
        testContext.cleanup(db, DB_NAME)
    }

    // ---------------------------------------------------------------- helpers

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun sourceTxtFile(name: String, text: String): File {
        val dir = File(root, "source").also { it.mkdirs() }
        return File(dir, name).apply {
            writeText(text, Charsets.UTF_8)
            provider.serve(name, this)
        }
    }

    private fun sourceEpubFile(name: String, title: String, body: String): File {
        val dir = File(root, "source").also { it.mkdirs() }
        val target = File(dir, name)
        D2EpubTestFixture.createMinimalEpub(target, title = title, body = body)
        val verify = D2EpubTestFixture.verifyEpub(target)
        assertTrue("测试 EPUB 必须合法有效: ${verify.message}", verify.isValid)
        provider.serve(name, target)
        return target
    }

    private fun docUri(documentId: String): Uri =
        DocumentsContract.buildDocumentUri(AUTHORITY, documentId)

    /** 关库重开：断言数据真实落盘并可重新读回 */
    private fun reopenDatabase() {
        db.close()
        initDatabase()
        testContext.assertDatabasePathIsolated(db)
    }

    /**
     * 关键安全网测试执行包裹器（余项 3）：
     * 无论测试成功还是失败，均完整记录本次现场快照与状态落盘，绝不残留旧成功冒充！
     */
    private inline fun executeTestScenario(
        testName: String,
        format: String,
        scenario: String,
        allowedChanges: List<String> = emptyList(),
        preservedFields: List<String> = emptyList(),
        testBody: (
            recordSuccessSnapshot: (before: D2BookSnapshot, after: D2BookSnapshot, reopened: D2BookSnapshot, passedAssertions: List<String>) -> Unit
        ) -> Unit
    ) {
        var capturedBefore: D2BookSnapshot? = null
        var capturedAfter: D2BookSnapshot? = null
        var capturedReopened: D2BookSnapshot? = null
        var capturedPassed: List<String> = emptyList()

        try {
            testBody { before, after, reopened, passed ->
                capturedBefore = before
                capturedAfter = after
                capturedReopened = reopened
                capturedPassed = passed

                val evidence = D2MachineReadableEvidence(
                    runId = CURRENT_RUN_ID,
                    testName = testName,
                    format = format,
                    scenario = scenario,
                    status = "PASSED",
                    dbIsolated = true,
                    dbPath = db.openHelper.writableDatabase.path.orEmpty(),
                    touchRecord = testContext.collectTouchedPaths(DB_NAME),
                    beforeState = before.toReadableMap(),
                    afterState = after.toReadableMap(),
                    reopenedState = reopened.toReadableMap(),
                    allowedChanges = allowedChanges,
                    preservedFields = preservedFields,
                    passedAssertions = passed,
                )
                recordEvidence(evidence)
            }
        } catch (t: Throwable) {
            // 失败时记录现场与异常堆栈并立即落盘
            val failedEvidence = D2MachineReadableEvidence(
                runId = CURRENT_RUN_ID,
                testName = testName,
                format = format,
                scenario = scenario,
                status = "FAILED",
                failureMessage = t.message ?: t.toString(),
                failureStackTrace = t.stackTraceToString().take(3000),
                dbIsolated = true,
                dbPath = runCatching { db.openHelper.writableDatabase.path.orEmpty() }.getOrDefault(""),
                touchRecord = runCatching { testContext.collectTouchedPaths(DB_NAME) }.getOrNull(),
                beforeState = capturedBefore?.toReadableMap(),
                afterState = capturedAfter?.toReadableMap(),
                reopenedState = capturedReopened?.toReadableMap(),
                allowedChanges = allowedChanges,
                preservedFields = preservedFields,
                passedAssertions = capturedPassed,
            )
            recordEvidence(failedEvidence)
            throw t
        }
    }

    private fun recordEvidence(evidence: D2MachineReadableEvidence) {
        allEvidenceList.add(evidence)
        val targetFile = File(RUN_EVIDENCE_DIR, "${evidence.testName}.json")
        D2EvidenceWriter.writeEvidence(evidence, targetFile)
    }

    /** 种一本已入架的 TXT */
    private fun seedTxtBook(contentHash: String?) = runBlocking {
        val internal = txtInternalCopy
        internal.parentFile!!.mkdirs()
        internal.writeText(TXT_ORIGINAL, Charsets.UTF_8)
        val now = "2026-10-07T12:00:00Z"
        val size = TXT_ORIGINAL.toByteArray(Charsets.UTF_8).size

        bookDao.upsert(
            BookEntity(
                id = TXT_BOOK_ID,
                title = TXT_BOOK_TITLE,
                author = "测试作者",
                format = "txt",
                original_file_name = TXT_FILE_NAME,
                content_hash = contentHash,
                size = size,
                local_uri = Uri.fromFile(internal).toString(),
                local_content_path = internal.absolutePath,
                content_status = "available",
                imported_at = now,
                updated_at = now,
            ),
        )
        contentDao.upsert(
            BookContentEntity(
                book_id = TXT_BOOK_ID,
                reader_preview = TXT_ORIGINAL.take(20),
            ),
        )
        fileDao.upsert(
            BookFileEntity(
                book_id = TXT_BOOK_ID,
                file_name = TXT_FILE_NAME,
                format = "txt",
                content_hash = contentHash,
                size = size,
                local_uri = Uri.fromFile(internal).toString(),
                updated_at = now,
            ),
        )
        progressDao.upsert(
            ReadingProgressEntity(
                book_id = TXT_BOOK_ID,
                progress_percent = 42.5f,
                last_read_at = now,
                total_reading_time_ms = 123_456L,
                completion_state = "reading",
                current_location_json = "{\"offset\":6100}",
                updated_at = now,
            ),
        )
        noteDao.upsert(
            NoteEntity(
                id = "note-txt-1",
                book_id = TXT_BOOK_ID,
                title = "测试笔记",
                body = "用户笔记正文",
                excerpt = "第一段正文标记ALPHA",
                created_at = now,
                updated_at = now,
            ),
        )
        highlightDao.upsert(
            HighlightEntity(
                id = "hl-txt-1",
                book_id = TXT_BOOK_ID,
                text = "第一段正文标记ALPHA",
                note = "划线备注",
                color = "yellow",
                progress_percent = 42.5f,
                created_at = now,
                updated_at = now,
            ),
        )
        sourceDao.upsert(
            LibrarySourceRefEntity(
                book_id = TXT_BOOK_ID,
                root_id = null,
                provider_authority = AUTHORITY,
                document_id = TXT_FILE_NAME,
                display_name = TXT_FILE_NAME,
                format = "txt",
                size = size.toLong(),
                last_modified = 1_700_000_000_000L,
                content_hash = contentHash,
                candidate_fingerprint = null,
                last_seen_at = 1_700_000_000_000L,
                availability = LibrarySourceAvailability.AVAILABLE.storageValue,
            ),
        )
    }

    /** 种一本已入架的 EPUB */
    private fun seedEpubBook(epubFile: File) = runBlocking {
        val internal = epubInternalCopy
        internal.parentFile!!.mkdirs()
        epubFile.copyTo(internal, overwrite = true)
        val now = "2026-10-07T12:00:00Z"
        val size = internal.length().toInt()
        val contentHash = D2EpubTestFixture.computeFileMd5(internal)

        bookDao.upsert(
            BookEntity(
                id = EPUB_BOOK_ID,
                title = EPUB_BOOK_TITLE,
                author = "测试作者",
                format = "epub",
                original_file_name = EPUB_FILE_NAME,
                content_hash = contentHash,
                size = size,
                local_uri = Uri.fromFile(internal).toString(),
                local_content_path = internal.absolutePath,
                content_status = "available",
                imported_at = now,
                updated_at = now,
            ),
        )
        contentDao.upsert(
            BookContentEntity(
                book_id = EPUB_BOOK_ID,
                reader_preview = "EPUB章节预览",
            ),
        )
        fileDao.upsert(
            BookFileEntity(
                book_id = EPUB_BOOK_ID,
                file_name = EPUB_FILE_NAME,
                format = "epub",
                content_hash = contentHash,
                size = size,
                local_uri = Uri.fromFile(internal).toString(),
                updated_at = now,
            ),
        )
        progressDao.upsert(
            ReadingProgressEntity(
                book_id = EPUB_BOOK_ID,
                progress_percent = 65.0f,
                last_read_at = now,
                total_reading_time_ms = 345_678L,
                completion_state = "reading",
                current_location_json = "{\"chapter\":0,\"charOffset\":120}",
                updated_at = now,
            ),
        )
        noteDao.upsert(
            NoteEntity(
                id = "note-epub-1",
                book_id = EPUB_BOOK_ID,
                title = "EPUB测试笔记",
                body = "EPUB用户笔记内容",
                excerpt = "EPUB第一章原始正文标记ALPHA",
                created_at = now,
                updated_at = now,
            ),
        )
        highlightDao.upsert(
            HighlightEntity(
                id = "hl-epub-1",
                book_id = EPUB_BOOK_ID,
                text = "EPUB第一章原始正文标记ALPHA",
                note = "EPUB划线标注",
                color = "blue",
                progress_percent = 65.0f,
                created_at = now,
                updated_at = now,
            ),
        )
        sourceDao.upsert(
            LibrarySourceRefEntity(
                book_id = EPUB_BOOK_ID,
                root_id = null,
                provider_authority = AUTHORITY,
                document_id = EPUB_FILE_NAME,
                display_name = EPUB_FILE_NAME,
                format = "epub",
                size = size.toLong(),
                last_modified = 1_700_000_000_000L,
                content_hash = contentHash,
                candidate_fingerprint = null,
                last_seen_at = 1_700_000_000_000L,
                availability = LibrarySourceAvailability.AVAILABLE.storageValue,
            ),
        )
    }

    // ---------------------------------------------------------------- tests

    /**
     * 场景 1：SAME 重新关联（TXT）
     * 换入所选文件的实际字节，全实体规范化比对，核心身份与用户资产保持，关库重开强断言，证据落盘绑定 Run。
     */
    @Test
    fun sameContentRelinkConsumesSelectedBytesAndPreservesUserData() = runBlocking {
        executeTestScenario(
            testName = "sameContentRelinkConsumesSelectedBytesAndPreservesUserData",
            format = "txt",
            scenario = "SAME_RELINK",
            allowedChanges = listOf(
                "BookEntity.updated_at",
                "BookEntity.size",
                "BookEntity.local_uri",
                "BookEntity.local_content_path",
                "BookEntity.content_status",
                "BookFileEntity.updated_at",
                "BookFileEntity.size"
            ),
            preservedFields = listOf(
                "BookEntity 规范化除变更字段外 100% 相等",
                "BookFileEntity 规范化除变更字段外 100% 相等",
                "BookContentEntity 100% 保持",
                "LibrarySourceRefEntity 100% 保持",
                "ReadingProgressEntity 全字段逐列保持",
                "NoteEntity 全字段逐列保持",
                "HighlightEntity 全字段逐列保持"
            ),
        ) { recordSuccess ->
            seedTxtBook(contentHash = md5(TXT_ORIGINAL))
            val before = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            val selected = sourceTxtFile(TXT_FILE_NAME, TXT_ORIGINAL)
            val selectedSha = D2SnapshotReader.sha256(selected)
            val selectedSize = selected.length().toInt()

            val message = importer.repairFile(TXT_BOOK_ID, docUri(TXT_FILE_NAME))
            assertEquals("《$TXT_BOOK_TITLE》正文已修复", message)

            val after = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            D2ContractVerifier.assertSameRelinkContract(
                before = before,
                after = after,
                expectedSelectedSha256 = selectedSha,
                expectedContentHash = md5(TXT_ORIGINAL),
                expectedSelectedSize = selectedSize,
                tag = "TXT/SAME",
            )

            reopenDatabase()
            val reopened = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            D2ContractVerifier.assertReopenMatches(after, reopened, "TXT/SAME")

            recordSuccess(
                before, after, reopened,
                listOf(
                    "SelectedBytesConsumed",
                    "UserDataPreserved",
                    "FullEntityNormalizedEqual",
                    "IdentityGatePassed",
                    "ReopenMatched",
                    "DatabaseIsolated"
                )
            )
        }
    }

    /**
     * 场景 2：DIFFERENT（TXT 内容不同）
     * 身份门明确拒绝重新关联，旧正文与全部用户资产 100% 保持，零修改，关库重开断言，证据落盘。
     */
    @Test
    fun differentContentRelinkIsRejectedAndOriginalAssetSurvives() = runBlocking {
        executeTestScenario(
            testName = "differentContentRelinkIsRejectedAndOriginalAssetSurvives",
            format = "txt",
            scenario = "DIFFERENT_REJECTED",
            allowedChanges = emptyList(),
            preservedFields = listOf(
                "全表记录 100% 零修改",
                "内部文件 SHA-256 100% 保持"
            ),
        ) { recordSuccess ->
            seedTxtBook(contentHash = md5(TXT_ORIGINAL))
            val before = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            sourceTxtFile(TXT_FILE_NAME, TXT_DIFFERENT)

            val message = importer.repairFile(TXT_BOOK_ID, docUri(TXT_FILE_NAME))
            assertTrue("必须明确拒绝且包含不一致说明：$message", message.contains("不一致"))

            val after = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            D2ContractVerifier.assertNoChange(before, after, "TXT/DIFFERENT")

            reopenDatabase()
            val reopened = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            D2ContractVerifier.assertReopenMatches(after, reopened, "TXT/DIFFERENT")

            recordSuccess(
                before, after, reopened,
                listOf(
                    "IdentityGateRejected",
                    "NoModificationOccurred",
                    "ReopenMatched"
                )
            )
        }
    }

    /**
     * 场景 3：UNKNOWN（缺少内容哈希）
     * 无法可靠确认身份，停止重新关联，数据 100% 保持，关库重开断言，证据落盘。
     */
    @Test
    fun unknownIdentityRelinkIsRejected() = runBlocking {
        executeTestScenario(
            testName = "unknownIdentityRelinkIsRejected",
            format = "txt",
            scenario = "UNKNOWN_REJECTED",
            allowedChanges = emptyList(),
            preservedFields = listOf(
                "全表记录 100% 零修改",
                "内部文件 SHA-256 100% 保持"
            ),
        ) { recordSuccess ->
            seedTxtBook(contentHash = null)
            val before = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            sourceTxtFile(TXT_FILE_NAME, TXT_ORIGINAL)

            val message = importer.repairFile(TXT_BOOK_ID, docUri(TXT_FILE_NAME))
            assertTrue("UNKNOWN 必须明确拒绝：$message", message.contains("无法确认"))

            val after = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            D2ContractVerifier.assertNoChange(before, after, "TXT/UNKNOWN")

            reopenDatabase()
            val reopened = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            D2ContractVerifier.assertReopenMatches(after, reopened, "TXT/UNKNOWN")

            recordSuccess(
                before, after, reopened,
                listOf(
                    "UnknownGateRejected",
                    "NoModificationOccurred",
                    "ReopenMatched"
                )
            )
        }
    }

    /**
     * 场景 4：Changed-content Refresh（TXT 来源直接刷新）
     * 换入新字节，更新 content_hash 并推进来源基线，核实 Provider 参数，全部用户资产严格保持，关库重开断言。
     */
    @Test
    fun changedContentRefreshConsumesNewBytesAndPreservesUserData() = runBlocking {
        executeTestScenario(
            testName = "changedContentRefreshConsumesNewBytesAndPreservesUserData",
            format = "txt",
            scenario = "CHANGED_CONTENT_REFRESH",
            allowedChanges = listOf(
                "BookEntity.content_hash",
                "BookEntity.size",
                "BookEntity.updated_at",
                "LibrarySourceRefEntity.content_hash",
                "LibrarySourceRefEntity.size",
                "LibrarySourceRefEntity.last_modified",
                "LibrarySourceRefEntity.last_seen_at"
            ),
            preservedFields = listOf(
                "BookEntity 核心元数据除更新列外 100% 相等",
                "ReadingProgressEntity 全字段逐列保持",
                "NoteEntity 全字段逐列保持",
                "HighlightEntity 全字段逐列保持"
            ),
        ) { recordSuccess ->
            seedTxtBook(contentHash = md5(TXT_ORIGINAL))
            val before = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            val refreshed = sourceTxtFile(TXT_FILE_NAME, TXT_REFRESHED)
            val refreshedSha = D2SnapshotReader.sha256(refreshed)
            val refreshedSize = refreshed.length()
            val refreshedModified = refreshed.lastModified()
            val refreshedHash = md5(TXT_REFRESHED)

            val result = importer.refreshFromSource(TXT_BOOK_ID)
            assertTrue("刷新应成功：$result", result is SourceRefreshResult.Success)

            val after = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            D2ContractVerifier.assertRefreshContract(
                before = before,
                after = after,
                expectedRefreshedSha256 = refreshedSha,
                expectedNewContentHash = refreshedHash,
                expectedProviderSize = refreshedSize,
                expectedProviderLastModified = refreshedModified,
                tag = "TXT/REFRESH",
            )

            reopenDatabase()
            val reopened = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            D2ContractVerifier.assertReopenMatches(after, reopened, "TXT/REFRESH")

            recordSuccess(
                before, after, reopened,
                listOf(
                    "RefreshSuccess",
                    "NewBytesInstalled",
                    "ProviderParametersVerified",
                    "BaselineAdvanced",
                    "UserDataPreserved",
                    "ReopenMatched"
                )
            )
        }
    }

    /**
     * 场景 5：NoSourceRef（无来源引用刷新）
     * 明确返回 NoSourceRef，不动任何旧资料，关库重开断言，证据落盘。
     */
    @Test
    fun refreshWithoutSourceRefIsRejected() = runBlocking {
        executeTestScenario(
            testName = "refreshWithoutSourceRefIsRejected",
            format = "txt",
            scenario = "NO_SOURCE_REF",
            allowedChanges = emptyList(),
            preservedFields = listOf(
                "全表记录 100% 零修改",
                "内部文件 SHA-256 100% 保持"
            ),
        ) { recordSuccess ->
            seedTxtBook(contentHash = md5(TXT_ORIGINAL))
            // 模拟「从未记录来源引用」
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM library_source_refs WHERE book_id = ?",
                arrayOf<Any>(TXT_BOOK_ID),
            )
            val before = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)

            val result = importer.refreshFromSource(TXT_BOOK_ID)
            assertEquals(SourceRefreshResult.NoSourceRef, result)

            val after = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            D2ContractVerifier.assertNoChange(before, after, "TXT/NoSourceRef")

            reopenDatabase()
            val reopened = D2SnapshotReader.capture(db, TXT_BOOK_ID, txtInternalCopy)
            D2ContractVerifier.assertReopenMatches(after, reopened, "TXT/NoSourceRef")

            recordSuccess(
                before, after, reopened,
                listOf(
                    "NoSourceRefReturned",
                    "ZeroModification",
                    "ReopenMatched"
                )
            )
        }
    }

    /**
     * 场景 6：真实 EPUB staging 消费（SAME 重新关联）
     *
     * 构造真实最小 EPUB 夹具，验证通过 Provider -> staging -> EpubRepository.replaceStoredEpub ->
     * EpubParser 解析 -> 换入正式内部副本 -> 身份校验放行与落库 -> 规范化全实体比对 -> 用户数据保持 -> 关库重开。
     */
    @Test
    fun sameContentEpubRelinkConsumesStagedBytesAndPreservesUserData() = runBlocking {
        executeTestScenario(
            testName = "sameContentEpubRelinkConsumesStagedBytesAndPreservesUserData",
            format = "epub",
            scenario = "EPUB_STAGING_CONSUME",
            allowedChanges = listOf(
                "BookEntity.updated_at",
                "BookEntity.size",
                "BookEntity.local_uri",
                "BookEntity.local_content_path",
                "BookEntity.content_status",
                "BookFileEntity.updated_at",
                "BookFileEntity.size"
            ),
            preservedFields = listOf(
                "BookEntity 规范化除变更列外 100% 相等",
                "BookFileEntity 规范化除变更列外 100% 相等",
                "BookContentEntity 100% 保持",
                "LibrarySourceRefEntity 100% 保持",
                "ReadingProgressEntity 全字段保持",
                "NoteEntity 全字段保持",
                "HighlightEntity 全字段保持"
            ),
        ) { recordSuccess ->
            // 1. 创建种子 EPUB
            val seedEpub = File(File(root, "seed"), EPUB_FILE_NAME)
            D2EpubTestFixture.createMinimalEpub(seedEpub, title = EPUB_BOOK_TITLE, body = EPUB_ORIGINAL_BODY)
            val seedHash = D2EpubTestFixture.computeFileMd5(seedEpub)
            seedEpubBook(seedEpub)

            val before = D2SnapshotReader.capture(db, EPUB_BOOK_ID, epubInternalCopy)

            // 2. 提供相同内容的目标文件（模拟用户重新选择相同 EPUB）
            val selectedEpub = sourceEpubFile(EPUB_FILE_NAME, title = EPUB_BOOK_TITLE, body = EPUB_ORIGINAL_BODY)
            val selectedSha = D2SnapshotReader.sha256(selectedEpub)
            val selectedHash = D2EpubTestFixture.computeFileMd5(selectedEpub)
            val selectedSize = selectedEpub.length().toInt()
            assertEquals("前置断言：所选文件哈希必须与种子哈希一致", seedHash, selectedHash)

            // 3. 执行真实 repairFile：走真实 staging 消费与真实 replaceStoredEpub
            val message = importer.repairFile(EPUB_BOOK_ID, docUri(EPUB_FILE_NAME))
            assertEquals("《$EPUB_BOOK_TITLE》正文已修复", message)

            // 4. 断言内部正式 EPUB 副本更新为所选字节，全实体规范化比对
            val after = D2SnapshotReader.capture(db, EPUB_BOOK_ID, epubInternalCopy)
            D2ContractVerifier.assertSameRelinkContract(
                before = before,
                after = after,
                expectedSelectedSha256 = selectedSha,
                expectedContentHash = selectedHash,
                expectedSelectedSize = selectedSize,
                tag = "EPUB/SAME",
            )

            // 5. 关库重开断言
            reopenDatabase()
            val reopened = D2SnapshotReader.capture(db, EPUB_BOOK_ID, epubInternalCopy)
            D2ContractVerifier.assertReopenMatches(after, reopened, "EPUB/SAME")

            // 6. 验证重开后内部 EPUB 副本结构依旧完整有效
            val internalInspection = D2EpubTestFixture.verifyEpub(epubInternalCopy)
            assertTrue("换入后内部 EPUB 必须可离线验证解析: ${internalInspection.message}", internalInspection.isValid)

            recordSuccess(
                before, after, reopened,
                listOf(
                    "RealEpubStagingConsumed",
                    "RealEpubParserVerified",
                    "FullEntityNormalizedEqual",
                    "UserDataPreserved",
                    "ReopenMatched",
                    "DatabaseIsolated"
                )
            )
        }
    }

    /**
     * 场景 7：EPUB 内容不同（DIFFERENT）
     * 提供不同正文的 EPUB，验证被身份门明确拒绝，旧 EPUB 内部副本逐字节保持，用户数据保持，关库重开，证据落盘。
     */
    @Test
    fun differentContentEpubRelinkIsRejectedAndOriginalSurvives() = runBlocking {
        executeTestScenario(
            testName = "differentContentEpubRelinkIsRejectedAndOriginalSurvives",
            format = "epub",
            scenario = "EPUB_DIFFERENT_REJECTED",
            allowedChanges = emptyList(),
            preservedFields = listOf(
                "全表记录 100% 零修改",
                "内部 EPUB 文件 SHA-256 100% 保持"
            ),
        ) { recordSuccess ->
            val seedEpub = File(File(root, "seed"), EPUB_FILE_NAME)
            D2EpubTestFixture.createMinimalEpub(seedEpub, title = EPUB_BOOK_TITLE, body = EPUB_ORIGINAL_BODY)
            seedEpubBook(seedEpub)

            val before = D2SnapshotReader.capture(db, EPUB_BOOK_ID, epubInternalCopy)

            // 提供内容不同的 EPUB
            sourceEpubFile(EPUB_FILE_NAME, title = EPUB_BOOK_TITLE, body = EPUB_DIFFERENT_BODY)

            val message = importer.repairFile(EPUB_BOOK_ID, docUri(EPUB_FILE_NAME))
            assertTrue("EPUB DIFFERENT 必须明确拒绝：$message", message.contains("不一致"))

            val after = D2SnapshotReader.capture(db, EPUB_BOOK_ID, epubInternalCopy)
            D2ContractVerifier.assertNoChange(before, after, "EPUB/DIFFERENT")

            reopenDatabase()
            val reopened = D2SnapshotReader.capture(db, EPUB_BOOK_ID, epubInternalCopy)
            D2ContractVerifier.assertReopenMatches(after, reopened, "EPUB/DIFFERENT")

            recordSuccess(
                before, after, reopened,
                listOf(
                    "EpubIdentityGateRejected",
                    "OriginalEpubUntouched",
                    "UserDataPreserved",
                    "ReopenMatched"
                )
            )
        }
    }

    // ---------------------------------------------------------------- test seams

    /** 真实 FileOps 生产语义 */
    private object RealFileOps : FileOps {
        override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)
        override fun copyTo(source: File, dest: File) {
            source.copyTo(dest, overwrite = true)
        }
    }
}
