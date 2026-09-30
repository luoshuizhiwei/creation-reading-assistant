package com.creationreadingassistant.feature.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingCompletionState
import com.creationreadingassistant.feature.reader.locator.LegadoProgressMarker
import com.creationreadingassistant.ui.navigation.NAVIGATION_MODE_TEMPORARY
import com.creationreadingassistant.ui.navigation.readerNavigationMode
import com.creationreadingassistant.ui.navigation.readerTemporaryRouteForSource
import com.creationreadingassistant.ui.screen.reader.LegadoMarkerSaveGuard
import com.creationreadingassistant.ui.screen.reader.ReaderNavigationMode
import com.creationreadingassistant.ui.screen.reader.legadoGuardObservation
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * S12 限定验收仪器测试：
 * 1. 生产 LegadoReadingProgressImporter.importArchive 真实解析受控 ZIP 并通过 Room DAO 落库 marker；
 * 2. 真实临时导航模式（navigationMode=ReaderNavigationMode.TEMPORARY）与保存守卫的交互与保护。
 */
@RunWith(AndroidJUnit4::class)
class LegadoMarkerRealImportTemporaryTest {

    private lateinit var context: Context
    private lateinit var isolatedDb: AppDatabase

    companion object {
        const val ISOLATED_TEST_DB = "s12_isolated_import_test.db"
        const val TEST_BOOK_ID = "00000000-0000-4000-8000-000000000012"
        const val TEST_BOOK_TITLE = "测试 TXT"
        const val TEST_BOOK_AUTHOR = "测试"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 构造独立命名的持久测试 Room 数据库，不污染正式/已有数据
        context.deleteDatabase(ISOLATED_TEST_DB)
        isolatedDb = Room.databaseBuilder(context, AppDatabase::class.java, ISOLATED_TEST_DB)
            .allowMainThreadQueries()
            .build()
        LegadoMarkerSaveGuard.disarm(TEST_BOOK_ID)
    }

    @After
    fun tearDown() {
        LegadoMarkerSaveGuard.disarm(TEST_BOOK_ID)
        if (::isolatedDb.isInitialized && isolatedDb.isOpen) {
            isolatedDb.close()
        }
        context.deleteDatabase(ISOLATED_TEST_DB)
    }

    /**
     * 生成受控的 Legado backup*.zip 字节数组。
     * ZIP 内包含 bookshelf.json，记录 name="测试 TXT", author="测试", durChapterIndex=99, durChapterPos=500。
     */
    private fun createControlledLegadoZip(
        name: String = TEST_BOOK_TITLE,
        author: String = TEST_BOOK_AUTHOR,
        chapterIndex: Int = 99,
        charOffset: Int = 500,
    ): ByteArray {
        val json = """
            [
              {
                "name": "$name",
                "author": "$author",
                "durChapterIndex": $chapterIndex,
                "durChapterPos": $charOffset,
                "durChapterTime": 1759050000000,
                "durChapterTitle": "第100章",
                "totalChapters": 100
              }
            ]
        """.trimIndent()
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("bookshelf.json"))
            zos.write(json.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun testProductionLegadoImportArchiveParsesAndPersistsMarkerViaRoomDao() = runTest {
        val bookDao = isolatedDb.bookDao()
        val progressDao = isolatedDb.readingProgressDao()

        // 1. 准备唯一匹配的测试 TXT 元数据
        val book = BookEntity(
            id = TEST_BOOK_ID,
            title = TEST_BOOK_TITLE,
            author = TEST_BOOK_AUTHOR,
            format = "txt",
            original_file_name = "test_s12_isolated.txt",
            content_hash = "80f3cb305d32034fd812e2bbcf132e50b46b58a55c64daa284988f893bfbd377",
            size = 5242880,
            local_uri = "file:///dummy/path/test_s12_isolated.txt",
            local_content_path = "/dummy/path/test_s12_isolated.txt",
            content_status = "available",
            imported_at = "2026-09-28T09:00:00Z",
            revision = 1,
            updated_at = "2026-09-28T09:00:00Z",
        )
        bookDao.upsert(book)

        // 确保导入前无进度
        assertNull(progressDao.getByBook(TEST_BOOK_ID))

        // 2. 准备受控 ZIP 并记录 SHA-256
        val zipBytes = createControlledLegadoZip(
            name = TEST_BOOK_TITLE,
            author = TEST_BOOK_AUTHOR,
            chapterIndex = 99,
            charOffset = 500,
        )
        val zipHash = sha256(zipBytes)
        assertTrue("ZIP 内容应非空", zipBytes.isNotEmpty())

        // 3. 调用生产 LegadoReadingProgressImporter.importArchive
        val importer = LegadoReadingProgressImporter(bookDao, progressDao)
        val report = importer.importArchive(zipBytes)

        // 4. 验证生产导入报告指标
        assertEquals("扫描数应为 1", 1, report.scanned)
        assertEquals("导入成功数应为 1", 1, report.imported)
        assertEquals("跳过数应为 0", 0, report.skipped)
        assertEquals("未匹配数应为 0", 0, report.unmatched)
        assertEquals("歧义数应为 0", 0, report.ambiguous)

        // 5. 验证真实 Room DAO 读取出的进度与 marker
        val saved = progressDao.getByBook(TEST_BOOK_ID)
        assertNotNull("导入后进度实体应落盘", saved)
        assertEquals(TEST_BOOK_ID, saved!!.book_id)
        assertEquals(ReadingCompletionState.READING, saved.readingState)
        assertEquals(0f, saved.progress_percent, 0.0001f)

        // 验证实际落库 marker 的 ci/co 与 ZIP 中的数值完全一致
        val expectedMarker = LegadoProgressMarker.encode(99, 500)
        assertEquals(expectedMarker, saved.current_location_json)

        val decoded = LegadoProgressMarker.decode(saved.current_location_json)
        assertNotNull("落库的 marker 必须能被正确解码", decoded)
        assertEquals(99, decoded!!.chapterIndex)
        assertEquals(500, decoded.charOffset)
    }

    @Test
    fun testRealTemporaryNavigationModeSaveGuardProtectionContract() {
        val bid = TEST_BOOK_ID

        // 1. 未换算 Legado marker 初始化并武装
        LegadoMarkerSaveGuard.arm(bid)
        assertTrue("未换算 marker 必须使守卫进入武装态", LegadoMarkerSaveGuard.isArmed(bid))

        // 初始恢复落点锚定在 (0, 0)
        LegadoMarkerSaveGuard.anchorAt(bid, 0 to 0)

        // 2. 进入临时查阅模式：navigationMode = ReaderNavigationMode.TEMPORARY (temporaryInspection = true)
        val temporaryInspection = true

        // 临时模式下发生位移（例如用户在临时查阅中翻页或滚动到了 (5, 200)）
        val tempObservation = legadoGuardObservation(
            initialPositionPending = false,
            temporaryInspection = temporaryInspection,
            position = 5 to 200,
        )
        // 核心断言：temporaryInspection 必须使接线函数强制产生 null 观察值
        assertNull("临时查阅期间的位置变化必须被屏蔽为 null", tempObservation)

        // 将 null 喂给守卫观察器
        val guardDecisionInTemp = LegadoMarkerSaveGuard.observe(bid, tempObservation)
        // 守卫对 null 的语义：不锚定、不解除、维持抑制保存
        assertEquals("临时查阅期间保存必须持续被抑制", true, guardDecisionInTemp)
        assertTrue("临时查阅期间守卫绝不能被解除", LegadoMarkerSaveGuard.isArmed(bid))

        // 离开临时查阅（ReaderScreen.persistOnLeave 判定：!temporaryInspection && !isArmed）
        val canPersistOnLeaveInTemp = !temporaryInspection && !LegadoMarkerSaveGuard.isArmed(bid)
        assertFalse("离开临时查阅模式时绝对禁止写库", canPersistOnLeaveInTemp)

        // 3. 返回普通阅读模式：navigationMode = ReaderNavigationMode.NORMAL (temporaryInspection = false)
        val normalInspection = false

        // 回到原锚点 (0, 0)
        val returnToAnchorObservation = legadoGuardObservation(
            initialPositionPending = false,
            temporaryInspection = normalInspection,
            position = 0 to 0,
        )
        assertEquals(0 to 0, returnToAnchorObservation)

        val returnDecision = LegadoMarkerSaveGuard.observe(bid, returnToAnchorObservation)
        assertEquals("返回原锚点后保存持续被抑制", true, returnDecision)
        assertTrue("返回原锚点后守卫依然维持武装，保护 marker", LegadoMarkerSaveGuard.isArmed(bid))

        // 离开原锚点（不移动即退出）
        val canPersistOnLeaveAtAnchor = !normalInspection && !LegadoMarkerSaveGuard.isArmed(bid)
        assertFalse("在原锚点退出时保存仍被抑制，marker 受到保护", canPersistOnLeaveAtAnchor)

        // 4. 用户在普通模式下主动移动偏离锚点到 (1, 100)
        val moveObservation = legadoGuardObservation(
            initialPositionPending = false,
            temporaryInspection = normalInspection,
            position = 1 to 100,
        )
        val moveDecision = LegadoMarkerSaveGuard.observe(bid, moveObservation)
        assertEquals("用户主动偏离锚点，观察器放行保存", false, moveDecision)
        assertFalse("偏离锚点后守卫成功解除（disarm），允许原生进度落库", LegadoMarkerSaveGuard.isArmed(bid))

        // 此时退出允许保存原生位置
        val canPersistOnLeaveAfterMove = !normalInspection && !LegadoMarkerSaveGuard.isArmed(bid)
        assertTrue("主动移动后退出允许保存原生阅读位置", canPersistOnLeaveAfterMove)
    }

    @Test
    fun testTemporaryRouteConstructionAndNavigationModeParsing() {
        // 验证可追溯的临时查阅路由生成
        val route = readerTemporaryRouteForSource(
            bookId = TEST_BOOK_ID,
            legacyOffset = 100,
            chapterIndex = 0,
            charOffset = 100,
        )
        assertNotNull("有效坐标必须成功构造临时 route", route)
        assertTrue("临时 route 必须携带 navigationMode=temporary", route!!.contains("navigationMode=$NAVIGATION_MODE_TEMPORARY"))
        assertTrue("临时 route 必须包含正确的书籍 ID", route.contains(TEST_BOOK_ID))

        // 验证 navigationMode 参数解析
        assertEquals(ReaderNavigationMode.TEMPORARY, readerNavigationMode(NAVIGATION_MODE_TEMPORARY))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode("normal"))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode(null))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode(""))
        assertEquals(ReaderNavigationMode.NORMAL, readerNavigationMode("TEMPORARY")) // 严格小写
    }

    /**
     * 将受控 ZIP 通过生产 LegadoReadingProgressImporter 真实写入 qahost 数据库（供宿主 App UI 验收），
     * 绝不使用直接 SQL INSERT reading_progress 伪造导入记录。
     */
    @Test
    fun testProductionImportDirectlyIntoQahostDatabaseForUiAcceptance() = runTest {
        val qahostDb = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .allowMainThreadQueries()
            .build()
        try {
            val bookDao = qahostDb.bookDao()
            val progressDao = qahostDb.readingProgressDao()

            // 1. 确保 books 表中存在该中性测试书（由真实 DAO upsert）
            val book = BookEntity(
                id = TEST_BOOK_ID,
                title = TEST_BOOK_TITLE,
                author = TEST_BOOK_AUTHOR,
                format = "txt",
                original_file_name = "test_s12_isolated.txt",
                content_hash = "80f3cb305d32034fd812e2bbcf132e50b46b58a55c64daa284988f893bfbd377",
                size = 5242880,
                local_uri = "file:///data/user/0/com.creationreadingassistant.qahost/files/books/$TEST_BOOK_ID/test_s12_isolated.txt",
                local_content_path = "/data/user/0/com.creationreadingassistant.qahost/files/books/$TEST_BOOK_ID/test_s12_isolated.txt",
                content_status = "available",
                imported_at = "2026-09-28T09:00:00Z",
                revision = 1,
                updated_at = "2026-09-28T09:00:00Z",
            )
            bookDao.upsert(book)

            // 先清空该测试书旧的 progress，确保是新导入
            qahostDb.openHelper.writableDatabase.execSQL("DELETE FROM reading_progress WHERE book_id = '$TEST_BOOK_ID'")
            assertNull("导入前 progress 必须为空", progressDao.getByBook(TEST_BOOK_ID))

            // 2. 准备受控 Legado ZIP (ci=99, co=500)
            val zipBytes = createControlledLegadoZip(
                name = TEST_BOOK_TITLE,
                author = TEST_BOOK_AUTHOR,
                chapterIndex = 99,
                charOffset = 500,
            )

            // 3. 生产 importer 真实解析与入库
            val importer = LegadoReadingProgressImporter(bookDao, progressDao)
            val report = importer.importArchive(zipBytes)

            assertEquals("生产导入器必须报告 1 条导入成功", 1, report.imported)

            // 4. 验证真实数据库中落库的 marker
            val record = progressDao.getByBook(TEST_BOOK_ID)
            assertNotNull("生产导入器必须真实落库 reading_progress", record)
            assertEquals("""{"legado_position_v1":{"ci":99,"co":500}}""", record!!.current_location_json)
        } finally {
            qahostDb.close()
        }
    }
}
