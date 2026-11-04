package com.creationreadingassistant.acceptance

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.repository.NoteRepository
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.model.MultipleFailureException
import java.io.File
import java.util.UUID

/**
 * CRA-TASK60-SOURCE-ROOM-v1 —— 高亮转灵感的**真实 Room 文件持久化**仪器测试。
 *
 * 与 57/58 的定向 JVM 单测（MockK DAO）互补：这里用真实 `AppDatabase` + 真实 DAO
 * 写入文件型隔离数据库，直接调用生产 `NoteRepository.convertHighlightToInspiration`，
 * 关闭数据库后**重新打开**再读取，核对灵感记录与 `payload.source`。
 *
 * 边界（不要过度解读）：
 * - 只证明 Room 文件持久化 + 生产转换入口的落库内容；
 * - 不证明进程冷启动、UI 全流程、SAF/导出/分享或整链验收；
 * - 不访问应用正式数据库：数据库名每次唯一，且路径经 [ContextWrapper.getDatabasePath]
 *   重定向到本测试自己的根目录；打开后还会断言底层文件确实在该根目录内。
 */
@RunWith(AndroidJUnit4::class)
class HighlightInspirationPersistenceAcceptTest {

    private lateinit var base: Context
    private lateinit var isolatedContext: Context
    private lateinit var rootDir: File
    private lateinit var dbName: String

    /** 本用例打开过的所有数据库实例（收尾逐个关闭，失败单独暴露）。 */
    private val openedDatabases = mutableListOf<AppDatabase>()

    /** 本轮排他创建的精确数据库路径。资产保留给设备owner取证和恢复。 */
    private lateinit var expectedDatabaseFile: File

    /** 与 InspirationViewModel / HomeArchiveFormatters 完全一致的解析配置。 */
    private val payloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val createdAt = "2026-10-18T09:00:00Z"

    @Before
    fun setUp() {
        base = ApplicationProvider.getApplicationContext()
        dbName = "task60_highlight_inspiration_" + UUID.randomUUID().toString().replace("-", "")
        rootDir = File(base.cacheDir, "task60-accept-$dbName")
        check(rootDir.mkdir()) { "无法排他创建测试目录：$rootDir" }
        val dbDir = File(rootDir, "databases")
        check(dbDir.mkdir()) { "无法创建本轮数据库目录：$dbDir" }
        expectedDatabaseFile = File(dbDir, dbName)
        check(listOf("", "-wal", "-shm", "-journal").none {
            File(expectedDatabaseFile.path + it).exists()
        }) { "新测试目录已有数据库资产，保留并阻塞：$dbDir" }
        isolatedContext = object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File {
                check(name == dbName) { "拒绝非本轮数据库名称：$name" }
                return expectedDatabaseFile
            }

            // 框架 SQLiteOpenHelper 实际走 openOrCreateDatabase；仅重写 getDatabasePath
            // 不足以把文件钉在测试目录，因此这两个重载都显式打开隔离路径。
            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
            ): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory)

            override fun openOrCreateDatabase(
                name: String,
                mode: Int,
                factory: SQLiteDatabase.CursorFactory?,
                errorHandler: DatabaseErrorHandler?,
            ): SQLiteDatabase = SQLiteDatabase.openDatabase(
                getDatabasePath(name).path,
                factory,
                SQLiteDatabase.CREATE_IF_NECESSARY,
                errorHandler,
            )

            override fun getApplicationContext(): Context = this
        }
    }

    @After
    fun tearDown() {
        // 保留原始关闭异常，由JUnit与用例根因一起报告。
        val failures = mutableListOf<Throwable>()
        openedDatabases.asReversed().forEach { database ->
            try {
                if (database.isOpen) database.close()
            } catch (failure: Exception) {
                failures += failure
            }
        }
        openedDatabases.clear()
        // 仅观察路径不足以证明文件归属。本测试不删除数据库或目录；
        // 后续唯一设备owner须将创建物纳入清单，取证并按恢复合同处理。
        if (::rootDir.isInitialized) println("TASK60_DATABASE_EVIDENCE_DIR=${rootDir.absolutePath}")
        MultipleFailureException.assertEmpty(failures)
    }

    // ---------------------------------------------------------------- helpers

    /** 打开真实文件型 Room，并断言底层路径确实落在本测试根目录内。 */
    private fun openDatabase(): AppDatabase {
        val database = Room.databaseBuilder(isolatedContext, AppDatabase::class.java, dbName)
            .addCallback(AppDatabase.CreateIndexCallback())
            .build()
        openedDatabases += database
        val path = requireNotNull(database.openHelper.writableDatabase.path) {
            "文件型Room没有返回数据库路径；保留资产并阻塞"
        }
        assertTrue(
            "数据库实际路径与本轮精确预期不符：$path != ${expectedDatabaseFile.absolutePath}",
            File(path).absoluteFile == expectedDatabaseFile.absoluteFile,
        )
        return database
    }

    private fun seedBook(database: AppDatabase, bookId: String) {
        runBlocking {
            database.bookDao().upsert(
                BookEntity(
                    id = bookId,
                    title = "测试 EPUB",
                    format = "epub",
                    imported_at = createdAt,
                    updated_at = createdAt,
                ),
            )
        }
    }

    private fun newHighlight(
        bookId: String,
        text: String,
        note: String?,
        chapterTitle: String?,
        progressPercent: Float?,
        locatorJson: String?,
    ) = HighlightEntity(
        id = UUID.randomUUID().toString(),
        book_id = bookId,
        text = text,
        note = note,
        color = "#ff0",
        chapter_title = chapterTitle,
        progress_percent = progressPercent,
        locator_json = locatorJson,
        created_at = createdAt,
        updated_at = createdAt,
    )

    private fun decodeSource(entity: InspirationEntity) =
        payloadJson.decodeFromString<InspirationPayloadData>(entity.payload ?: "{}").source

    // ---------------------------------------------------------------- cases

    /**
     * 1) 完整实际来源：bookId / 章节 / 进度 / 摘录 / locator 全部经真实转换写入，
     *    关闭并重开数据库后仍可读；正文兼容；原高亮未被改写。
     */
    @Test
    fun completeSourceSurvivesCloseAndReopen() {
        val bookId = "task60-book-" + UUID.randomUUID().toString().replace("-", "")
        val text = "测试 EPUB 的原文摘录"
        val note = "个人批注：这一段值得展开"
        val locator = LocatorCodec.encode(
            legacyOffset = 4096,
            chapterIndex = 2,
            charOffset = 512,
            excerpt = text,
        )

        val first = openDatabase()
        seedBook(first, bookId)
        val highlight = newHighlight(
            bookId = bookId,
            text = text,
            note = note,
            chapterTitle = "第三章",
            progressPercent = 12.5f,
            locatorJson = locator,
        )
        runBlocking {
            first.highlightDao().upsert(highlight)
            NoteRepository(first.highlightDao(), first.noteDao(), first.inspirationDao())
                .convertHighlightToInspiration(highlight.id)
        }
        first.close()

        // 重新打开：只依赖磁盘上的 Room 文件，不共享任何内存状态。
        val second = openDatabase()
        val saved = runBlocking { second.inspirationDao().observeAllActive().first().single() }

        // 原始持久化 payload 的最低要求：不是空对象，且带 source 结构。
        val rawPayload = saved.payload
        assertNotNull("灵感 payload 未落库", rawPayload)
        assertTrue("payload 仍是空对象：$rawPayload", rawPayload != "{}")
        assertTrue("payload 缺 source 键：$rawPayload", rawPayload!!.contains("\"source\""))

        // 用真实消费类型核字段。
        val source = decodeSource(saved)
        assertNotNull("payload.source 解码为空，灵感详情与回源无法消费来源", source)
        source!!
        assertEquals(bookId, source.bookId)
        assertEquals(bookId, saved.source_book_id)
        assertEquals("第三章", source.chapterTitle)
        assertNotNull("完整来源的进度不得丢失", source.progressPercent)
        assertEquals(12.5f, source.progressPercent!!, 0f)
        assertEquals(text, source.excerpt)
        assertEquals(locator, source.locatorJson)

        // locator 是真实编码，能被生产解码器还原回同一坐标。
        val decodedLocator = LocatorCodec.decode(source.locatorJson)
        assertNotNull("locatorJson 无法被生产解码器解析：${source.locatorJson}", decodedLocator)
        assertEquals(4096, decodedLocator!!.legacyOffset ?: -1)
        assertEquals(2, decodedLocator.chapterIndex ?: -1)
        assertEquals(512, decodedLocator.charOffset ?: -1)

        // 来源摘录与个人批注结构上可区分。
        assertFalse("source.excerpt 不应包含个人批注", source.excerpt!!.contains("个人批注"))

        // 正文展示兼容：仍是「原文 ＋ 空行 ＋ 批注」。
        assertEquals("$text\n\n$note", saved.body)
        assertEquals("inbox", saved.status)
        assertEquals("note", saved.type)

        // 原高亮未被意外修改。
        val rereadHighlight = runBlocking { second.highlightDao().getById(highlight.id) }
        assertEquals("原高亮被转换流程改写", highlight, rereadHighlight)
    }

    /**
     * 2) 合法 0 进度：走同一生产入口与真实持久化重开路径，0 必须被保留，
     *    不能因为「0 看起来像缺省」而被丢掉或写成 null。
     */
    @Test
    fun zeroProgressIsPreservedAcrossReopen() {
        val bookId = "task60-book-" + UUID.randomUUID().toString().replace("-", "")
        val text = "测试 TXT 的零进度摘录"
        val locator = LocatorCodec.encode(
            legacyOffset = 0,
            chapterIndex = 0,
            charOffset = 0,
            excerpt = text,
        )

        val first = openDatabase()
        seedBook(first, bookId)
        val highlight = newHighlight(
            bookId = bookId,
            text = text,
            note = null,
            chapterTitle = "第一章",
            progressPercent = 0f,
            locatorJson = locator,
        )
        runBlocking {
            first.highlightDao().upsert(highlight)
            NoteRepository(first.highlightDao(), first.noteDao(), first.inspirationDao())
                .convertHighlightToInspiration(highlight.id)
        }
        first.close()

        val second = openDatabase()
        val saved = runBlocking { second.inspirationDao().observeAllActive().first().single() }
        val source = decodeSource(saved)
        assertNotNull(source)
        source!!
        assertNotNull("合法 0 进度被写成了 null", source.progressPercent)
        assertEquals("合法 0 进度被改写", 0f, source.progressPercent!!, 0f)
        assertEquals(locator, source.locatorJson)
        assertEquals(text, source.excerpt)
    }

    /**
     * 3) 最小缺省来源：章节/进度/locator 缺省时按合同落为 null，
     *    不编造书名、作者或定位；真实存在的 bookId 与摘录仍保留。
     */
    @Test
    fun minimalSourceDoesNotFabricateBookTitleAuthorOrLocator() {
        val bookId = "task60-book-" + UUID.randomUUID().toString().replace("-", "")
        val text = "测试 TXT 的最小来源摘录"

        val first = openDatabase()
        seedBook(first, bookId)
        val highlight = newHighlight(
            bookId = bookId,
            text = text,
            note = null,
            chapterTitle = null,
            progressPercent = null,
            locatorJson = null,
        )
        runBlocking {
            first.highlightDao().upsert(highlight)
            NoteRepository(first.highlightDao(), first.noteDao(), first.inspirationDao())
                .convertHighlightToInspiration(highlight.id)
        }
        first.close()

        val second = openDatabase()
        val saved = runBlocking { second.inspirationDao().observeAllActive().first().single() }
        val source = decodeSource(saved)
        assertNotNull(source)
        source!!

        assertEquals(bookId, source.bookId)
        assertEquals(text, source.excerpt)
        assertNull("缺省章节不得被编造", source.chapterTitle)
        assertNull("缺省进度不得被伪造为 0", source.progressPercent)
        assertNull("无 locator 时必须保持无精确定位语义", source.locatorJson)
        assertNull("本次输入没有书名，不得编造", source.bookTitle)
        assertNull("本次输入没有作者，不得编造", source.bookAuthor)

        val rawPayload = saved.payload.orEmpty()
        assertFalse("payload 不得出现编造的书名键：$rawPayload", rawPayload.contains("\"bookTitle\""))
        assertFalse("payload 不得出现编造的作者键：$rawPayload", rawPayload.contains("\"bookAuthor\""))

        // 无批注时正文就是原文本身（展示兼容）。
        assertEquals(text, saved.body)
    }

    /**
     * 4) 不存在的高亮：生产合同为无操作，真实数据库重开后也不应产生灵感行。
     */
    @Test
    fun missingHighlightWritesNothingAcrossReopen() {
        val bookId = "task60-book-" + UUID.randomUUID().toString().replace("-", "")
        val first = openDatabase()
        seedBook(first, bookId)
        runBlocking {
            NoteRepository(first.highlightDao(), first.noteDao(), first.inspirationDao())
                .convertHighlightToInspiration("task60-missing-highlight")
        }
        first.close()

        val second = openDatabase()
        val rows = runBlocking { second.inspirationDao().observeAllActive().first() }
        assertTrue("不存在的高亮不应产生灵感行：$rows", rows.isEmpty())
    }
}
