package com.creationreadingassistant.acceptance

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.hasContentDescription
import androidx.core.content.FileProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinator
import com.creationreadingassistant.data.settings.ImportHistoryStore
import com.creationreadingassistant.feature.library.LibraryRootStore
import com.creationreadingassistant.feature.library.LibrarySourceIndex
import com.creationreadingassistant.feature.library.ShelfImporter
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.FileOps
import io.mockk.mockk
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 任务96-v1 / lane A：D2「正文缺失 → 实际重新定位入口 → 恢复同书」的用户闭环。
 *
 * 只使用本轮创建的中性测试书和两个 Download 夹具；产品修复路径本身不改。
 * 覆盖：
 *  1) 真实产品导入 → 书架卡片；
 *  2) 书架「更多 → 批量选择 → 清缓存」（产品“移除正文”入口）后正文缺失；
 *  3) 长按卡片 →「重新定位文件」→ 系统 DocumentsUI → 选同内容文件 →
 *     同 bookId、正文可读、进度/笔记/高亮/灵感 source 完整保留；
 *  4) 同格式不同内容 → 明确拒绝，正文与用户数据不变；
 *  5) 系统选择器取消 → 不改记录。
 */
@RunWith(AndroidJUnit4::class)
class D2RelinkUserJourneyAcceptTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var runId: String
    private lateinit var resultsDir: File
    private lateinit var db: AppDatabase
    private lateinit var importer: ShelfImporter
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null
    private var bookId: String = ""
    private var lastPhase: String = "start"

    private val bodyText: String = buildString {
        appendLine("第1章 重新关联测试")
        appendLine()
        (1..12).forEach { p ->
            appendLine("第${p}段：这是任务96-v1 使用的中性测试正文，用于验证正文缺失后的重新关联与用户数据保留，不含任何正式书库内容。")
            appendLine()
        }
    }
    private val differentText: String = buildString {
        appendLine("第1章 不同内容的书")
        appendLine()
        appendLine("这段文字与导入时的正文不同，用于验证重新关联必须拒绝不同内容，且不得改动旧记录。")
    }

    private companion object {
        const val TAG = "CRA96D2"
        const val UI_TIMEOUT_MS = 20_000L
        const val PICKER_TIMEOUT_MS = 30_000L
        const val REPAIR_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 120L
        const val IMPORT_TIMEOUT_MS = 30_000L
        const val MAX_A11Y_NODES = 600
        val DOCUMENTS_UI_PACKAGES = setOf("com.android.documentsui", "com.google.android.documentsui")
    }

    @Before
    fun setUp() {
        runId = requireNotNull(InstrumentationRegistry.getArguments().getString("craRunId")) { "Root must bind craRunId" }
        require(runId.matches(Regex("T96V[0-9]+-[A-Za-z0-9-]+")))
        resultsDir = File(context.getExternalFilesDir(null), "task96-d2-results-$runId").apply { mkdirs() }
        db = J1RealReaderFixtures.openDatabase(context)
        importer = buildImporter()
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)
    }

    @After
    fun tearDown() {
        val errors = mutableListOf<Throwable>()
        fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                errors += t
            }
        }
        attempt { scenario?.close() }
        attempt { J1RealReaderFixtures.restoreOnboardingState(context, originalOnboarded) }
        // 96-v2：仪器不再删除 Download 夹具。删除统一由宿主在 collect 成功后按
        // “E 原字节 → 初始缺席 → 精确路径 → 当前设备 size/SHA → 删除 → 缺席复核”执行；
        // 仪器只保留 manifest 与 E 原字节，失败时留给宿主决定删除或 BLOCKED。
        attempt {
            File(resultsDir, "t96_d2_summary.json").writeText(
                JSONObject().put("runId", runId).put("lastPhase", lastPhase).put("bookId", bookId).toString(2),
            )
        }
        attempt { db.close() }
        if (errors.isNotEmpty()) {
            val failure = AssertionError("tearDown 失败 ${errors.size} 项（原始异常保留在 suppressed）")
            errors.forEach { failure.addSuppressed(it) }
            throw failure
        }
    }

    @Test
    fun missingBodyRelinksSameContentAndRejectsDifferent() = runBlocking {
        // P1：真实产品导入中性 TXT
        markPhase("t96_d2_10_import")
        val fixture = File(context.cacheDir, "diagnostics/t96-d2-fixtures/$runId.txt").apply {
            parentFile?.mkdirs()
            writeText(bodyText, Charsets.UTF_8)
        }
        File(resultsDir, "t96_d2_import_fixture.txt").writeBytes(fixture.readBytes())
        // 使用主 Manifest 已声明的 FileProvider（cache/diagnostics）获得真实可解析 content URI；
        // 不是仅在测试进程 attachInfo 的本地 provider。
        val importUri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", fixture)
        val beforeIds = activeBookIds()
        importer.importFiles(listOf(importUri), "task96-d2-device")
        assertTrue(
            "真实导入必须且只能新增一行本轮 TXT 书",
            waitFor("t96_d2_10_import", IMPORT_TIMEOUT_MS, "导入新增一行") {
                val added = activeBookIds() - beforeIds
                added.size == 1 && rawOne("SELECT format FROM books WHERE id='${added.first()}' AND deleted_at IS NULL") == "txt"
            },
        )
        bookId = (activeBookIds() - beforeIds).single()
        val internalPath = requireNotNull(rawOne("SELECT local_content_path FROM books WHERE id='$bookId'"))
        val originalBytes = File(internalPath).readBytes()
        assertEquals("导入后的内部正文必须是夹具字节", sha256(fixture.readBytes()), sha256(originalBytes))
        writeEvidence("t96_d2_10_import", mapOf("bookId" to bookId, "internalPath" to internalPath))

        // P1b：导入身份实证（真实 URI 元数据 + 正文哈希 + DB content_hash）。
        // 身份不成立立即失败，不进入 UI 旅程、不等待 30 秒。
        markPhase("t96_d2_15_import_identity")
        var resolverName: String? = null
        var resolverSize = -1L
        context.contentResolver.query(importUri, null, null, null, null)?.use { c ->
            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (nameIdx >= 0) resolverName = c.getString(nameIdx)
                if (sizeIdx >= 0) resolverSize = c.getLong(sizeIdx)
            }
        }
        val fixtureMd5 = md5Hex(fixture.readBytes())
        val identityObserved = waitFor(lastPhase, 5_000L, "导入 content_hash 回填") {
            rawOne("SELECT content_hash FROM books WHERE id='$bookId'") != null
        }
        val dbHash = rawOne("SELECT content_hash FROM books WHERE id='$bookId'")
        File(resultsDir, "t96_d2_15_import_identity.json").writeText(
            JSONObject()
                .put("uri", importUri.toString())
                .put("resolverName", resolverName ?: JSONObject.NULL)
                .put("resolverSize", resolverSize)
                .put("fixtureBytes", fixture.length())
                .put("fixtureMd5", fixtureMd5)
                .put("dbContentHash", dbHash ?: JSONObject.NULL)
                .put("identityObserved", identityObserved)
                .toString(2),
        )
        assertEquals("FileProvider 必须返回真实 size", fixture.length(), resolverSize)
        assertEquals("真实导入必须写入与正文一致的 content_hash", fixtureMd5, dbHash)
        assertTrue("导入 content_hash 必须建立", identityObserved)
        writeEvidence(
            "t96_d2_15_import_identity",
            mapOf("resolverName" to resolverName, "resolverSize" to resolverSize, "dbHash" to dbHash),
        )

        // P2：本轮用户资产（夹具）——进度/笔记/高亮/灵感来源
        markPhase("t96_d2_20_seed_assets")
        val now = Instant.now().toString()
        db.readingProgressDao().upsert(
            ReadingProgressEntity(
                book_id = bookId,
                progress_percent = 0.42f,
                last_read_at = now,
                total_reading_time_ms = 90_000L,
                current_location_json = "{\"v\":2,\"offset\":42,\"ci\":0,\"co\":42,\"fp\":\"t96-d2\"}",
                updated_at = now,
            ),
        )
        db.noteDao().upsert(
            NoteEntity(
                id = "t96-note-$runId",
                book_id = bookId,
                title = "任务96笔记",
                body = "neutral-note-body",
                chapter_title = "第1章",
                progress_percent = 0.42f,
                locator_json = "{\"v\":2,\"offset\":42}",
                created_at = now,
                updated_at = now,
            ),
        )
        db.highlightDao().upsert(
            HighlightEntity(
                id = "t96-highlight-$runId",
                book_id = bookId,
                text = "中性高亮文本",
                note = "neutral-highlight-note",
                chapter_title = "第1章",
                progress_percent = 0.42f,
                locator_json = "{\"v\":2,\"offset\":60}",
                created_at = now,
                updated_at = now,
            ),
        )
        db.inspirationDao().upsert(
            InspirationEntity(
                id = "t96-inspiration-$runId",
                title = "任务96灵感",
                body = "neutral-inspiration-body",
                status = "inbox",
                source_book_id = bookId,
                payload = "{\"tags\":[\"任务96\"],\"source\":{\"bookId\":\"$bookId\",\"bookTitle\":\"task96-d2-$runId\",\"chapterTitle\":\"第1章\",\"excerpt\":\"中性高亮文本\",\"locatorJson\":\"{\\\"v\\\":2,\\\"offset\\\":60}\"}}",
                created_at = now,
                updated_at = now,
            ),
        )
        val assetsBefore = assetSnapshot()
        writeEvidence("t96_d2_20_seed_assets", mapOf("assetsSha" to sha256(assetsBefore.toByteArray())))

        // P3：真实 UI 书架
        markPhase("t96_d2_30_shelf")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
        assertTrue("必须进入书架", ensureOnShelf(lastPhase))
        assertTrue(
            "书架必须出现本轮书籍卡片",
            waitFor(lastPhase, UI_TIMEOUT_MS, "书架卡片") {
                composeRule.onAllNodesWithTag("book-tile-$bookId").fetchSemanticsNodes().isNotEmpty()
            },
        )

        // P4：产品“移除正文”入口：更多 → 批量选择 → 选中 → 清缓存
        markPhase("t96_d2_40_clear_cache")
        composeRule.onAllNodesWithContentDescription("更多")[0].performClick()
        composeRule.waitForIdle()
        assertTrue("书架更多菜单必须出现「批量选择」", waitFor(lastPhase, UI_TIMEOUT_MS, "批量选择") {
            composeRule.onAllNodesWithText("批量选择").fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onNodeWithText("批量选择").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("book-tile-$bookId").performClick()
        composeRule.waitForIdle()
        assertTrue("批量操作必须出现「清缓存」", waitFor(lastPhase, UI_TIMEOUT_MS, "清缓存") {
            composeRule.onAllNodesWithText("清缓存").fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onNodeWithText("清缓存").performClick()
        assertTrue(
            "移除正文后同一本书必须进入 missing 且正文路径为空",
            waitFor(lastPhase, REPAIR_TIMEOUT_MS, "正文缺失") {
                rawOne("SELECT content_status FROM books WHERE id='$bookId'") == "missing" &&
                    rawOne("SELECT local_content_path FROM books WHERE id='$bookId'").isNullOrBlank()
            },
        )
        assertFalse("移除正文后内部正文文件必须不存在", File(internalPath).exists())
        assertEquals("移除正文不得改动用户资产", assetsBefore, assetSnapshot())
        writeEvidence("t96_d2_40_clear_cache", mapOf("status" to "missing"))

        // P5：长按 → 重新定位文件 → 同内容文件 → 同书恢复可读
        markPhase("t96_d2_50_relink_same")
        val sameName = pushDownloadFile("t96-d2-$runId.txt", bodyText)
        openRelinkPicker()
        selectDownloadFile(sameName)
        assertTrue(
            "同内容重新关联必须恢复到 available",
            waitFor(lastPhase, REPAIR_TIMEOUT_MS, "正文可用") {
                rawOne("SELECT content_status FROM books WHERE id='$bookId'") == "available"
            },
        )
        val relinkedRow = db.bookDao().getById(bookId)
        assertNotNull("重新关联后必须仍是同一 bookId 的记录", relinkedRow)
        assertEquals("重新关联不得改变 bookId", bookId, relinkedRow!!.id)
        val relinkedPath = requireNotNull(rawOne("SELECT local_content_path FROM books WHERE id='$bookId'"))
        assertEquals("重新关联后的内部正文必须是所选字节", sha256(bodyText.toByteArray()), sha256(File(relinkedPath).readBytes()))
        assertEquals("重新关联后用户资产必须保持", assetsBefore, assetSnapshot())
        assertEquals(
            "重新关联成功后 books.content_hash 必须为新正文哈希",
            md5Hex(bodyText.toByteArray()),
            rawOne("SELECT content_hash FROM books WHERE id='$bookId'"),
        )
        assertTrue("重新关联后操作面板必须关闭", waitFor(lastPhase, UI_TIMEOUT_MS, "面板关闭") {
            composeRule.onAllNodesWithText("重新定位文件").fetchSemanticsNodes().isEmpty()
        })
        // 真实可阅读
        composeRule.onNodeWithTag("book-tile-$bookId").performClick()
        assertTrue("重新关联后必须能进入阅读器正文", waitFor(lastPhase, UI_TIMEOUT_MS, "阅读器正文") {
            composeRule.onAllNodesWithContentDescription("阅读正文已就绪").fetchSemanticsNodes().isNotEmpty()
        })
        assertTrue("重新关联后必须能读到本轮正文", waitFor(lastPhase, UI_TIMEOUT_MS, "正文可见") {
            composeRule.onAllNodesWithText("第1章 重新关联测试", substring = true).fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onAllNodesWithContentDescription("返回")[0].performClick()
        composeRule.waitForIdle()
        assertTrue("必须回到书架", ensureOnShelf(lastPhase))
        writeEvidence("t96_d2_50_relink_same", mapOf("bookId" to bookId, "contentSha" to sha256(bodyText.toByteArray())))
        // 打开阅读器会由产品正常写入阅读进度；P6/P7 的“资产不变”基线必须在阅读之后重新取，
        // 否则会把合法的进度写入误报为“被拒绝操作改动”。
        val assetsAfterReading = assetSnapshot()
        val nonProgressBefore = nonProgressAssets()
        val nonProgressAfter = nonProgressAssets()
        File(resultsDir, "t96_d2_55_assets_after_reading.json").writeText(
            JSONObject()
                .put("assetsBefore", assetsBefore)
                .put("assetsAfterReading", assetsAfterReading)
                .put("nonProgressBefore", nonProgressBefore)
                .put("nonProgressAfter", nonProgressAfter)
                .put("progressChanged", assetsBefore != assetsAfterReading)
                .toString(2),
        )
        assertEquals("阅读只允许更新进度，笔记/高亮/灵感来源必须保持", nonProgressBefore, nonProgressAfter)

        // P6：不同内容同格式 → 明确拒绝且不改旧资产
        markPhase("t96_d2_60_relink_different")
        val differentName = pushDownloadFile("t96-d2-$runId-diff.txt", differentText)
        val availableBytes = File(relinkedPath).readBytes()
        val availableHash = requireNotNull(rawOne("SELECT content_hash FROM books WHERE id='$bookId'"))
        val p6AssetsBefore = assetSnapshot()
        openRelinkPicker()
        selectDownloadFile(differentName)
        assertTrue("不同内容必须明确提示不一致", waitFor(lastPhase, REPAIR_TIMEOUT_MS, "不一致提示") {
            composeRule.onAllNodesWithText("不一致", substring = true).fetchSemanticsNodes().isNotEmpty()
        })
        assertEquals("拒绝后正文状态不变", "available", rawOne("SELECT content_status FROM books WHERE id='$bookId'"))
        assertEquals("拒绝后 content_hash 不变", availableHash, rawOne("SELECT content_hash FROM books WHERE id='$bookId'"))
        assertEquals("拒绝后内部正文文件不变", sha256(availableBytes), sha256(File(relinkedPath).readBytes()))
        val p6AssetsAfter = assetSnapshot()
        File(resultsDir, "t96_d2_60_assets.json").writeText(
            JSONObject()
                .put("statusBefore", "available").put("statusAfter", rawOne("SELECT content_status FROM books WHERE id='$bookId'"))
                .put("hashBefore", availableHash).put("hashAfter", rawOne("SELECT content_hash FROM books WHERE id='$bookId'"))
                .put("bytesShaBefore", sha256(availableBytes)).put("bytesShaAfter", sha256(File(relinkedPath).readBytes()))
                .put("assetsBefore", p6AssetsBefore).put("assetsAfter", p6AssetsAfter)
                .toString(2),
        )
        assertEquals("拒绝后用户资产不变", p6AssetsBefore, p6AssetsAfter)
        writeEvidence("t96_d2_60_relink_different", mapOf("rejected" to true))

        // P7：取消选择器 → 零改动
        markPhase("t96_d2_70_cancel")
        val p7AssetsBefore = assetSnapshot()
        openRelinkPicker()
        var attempts = 0
        while (documentsUiRoot() != null && attempts < 3) {
            pressBack()
            composeRule.waitForIdle()
            attempts += 1
        }
        assertTrue("取消后系统选择器必须关闭", waitFor(lastPhase, UI_TIMEOUT_MS, "选择器关闭") { documentsUiRoot() == null })
        assertEquals("取消后正文状态不变", "available", rawOne("SELECT content_status FROM books WHERE id='$bookId'"))
        assertEquals("取消后内部正文文件不变", sha256(availableBytes), sha256(File(relinkedPath).readBytes()))
        val p7AssetsAfter = assetSnapshot()
        File(resultsDir, "t96_d2_70_assets.json").writeText(
            JSONObject()
                .put("statusBefore", "available").put("statusAfter", rawOne("SELECT content_status FROM books WHERE id='$bookId'"))
                .put("bytesShaBefore", sha256(availableBytes)).put("bytesShaAfter", sha256(File(relinkedPath).readBytes()))
                .put("assetsBefore", p7AssetsBefore).put("assetsAfter", p7AssetsAfter)
                .toString(2),
        )
        assertEquals("取消后用户资产不变", p7AssetsBefore, p7AssetsAfter)
        writeEvidence("t96_d2_70_cancel", mapOf("backAttempts" to attempts))

        markPhase("t96_d2_done")
    }

    /**
     * 96-v7：EPUB 最小真实旅程。
     * FileProvider 真实导入 → UI 移除正文 → 同原字节重新关联 → 阅读器章节可读；
     * 不同 EPUB 拒绝、取消零改动，均以各操作紧贴前的完整资产为基线。
     */
    @Test
    fun epubMissingBodyRelinksSameContentAndRejectsDifferent() = runBlocking {
        val epubTitle = "任务96EPUB-$runId"
        val epubBody = "EPUB第一章中性正文，用于重新关联验证。"
        val epubDifferentBody = "EPUB完全不同的正文，用于拒绝验证。"
        markPhase("t96v7_10_import")
        val sourceEpub = File(context.cacheDir, "diagnostics/t96-d2-fixtures/$runId.epub").apply { parentFile?.mkdirs() }
        D2EpubTestFixture.createMinimalEpub(
            dest = sourceEpub,
            title = epubTitle,
            body = epubBody,
            identifier = "t96v7-$runId",
        )
        val verify = D2EpubTestFixture.verifyEpub(sourceEpub)
        assertTrue("测试 EPUB 必须结构合法：${verify.message}", verify.isValid)
        File(resultsDir, "t96v7_import_fixture.epub").writeBytes(sourceEpub.readBytes())
        val epubMd5 = D2EpubTestFixture.computeFileMd5(sourceEpub)
        val importUri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", sourceEpub)
        val beforeIds = activeBookIds()
        importer.importFiles(listOf(importUri), "task96v7-device")
        assertTrue(
            "真实 EPUB 导入必须且只能新增一行本轮书",
            waitFor(lastPhase, IMPORT_TIMEOUT_MS, "导入新增 epub") {
                val added = activeBookIds() - beforeIds
                added.size == 1 && rawOne("SELECT format FROM books WHERE id='${added.first()}' AND deleted_at IS NULL") == "epub"
            },
        )
        bookId = (activeBookIds() - beforeIds).single()
        val internalPath = requireNotNull(rawOne("SELECT local_content_path FROM books WHERE id='$bookId'"))
        assertEquals("EPUB 内部副本必须是源字节", sha256(sourceEpub.readBytes()), sha256(File(internalPath).readBytes()))
        var resolverName: String? = null
        var resolverSize = -1L
        context.contentResolver.query(importUri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) resolverName = c.getString(ni)
                if (si >= 0) resolverSize = c.getLong(si)
            }
        }
        val identityObserved = waitFor(lastPhase, 5_000L, "EPUB content_hash") {
            rawOne("SELECT content_hash FROM books WHERE id='$bookId'") != null
        }
        val dbHash = rawOne("SELECT content_hash FROM books WHERE id='$bookId'")
        File(resultsDir, "t96v7_15_import_identity.json").writeText(
            JSONObject().put("uri", importUri.toString())
                .put("resolverName", resolverName ?: JSONObject.NULL)
                .put("resolverSize", resolverSize)
                .put("fixtureBytes", sourceEpub.length())
                .put("fixtureMd5", epubMd5)
                .put("dbContentHash", dbHash ?: JSONObject.NULL)
                .put("identityObserved", identityObserved)
                .toString(2),
        )
        assertEquals("FileProvider 必须返回真实 EPUB size", sourceEpub.length(), resolverSize)
        assertEquals("真实导入必须写入与 EPUB 原字节一致的 content_hash", epubMd5, dbHash)
        assertTrue("EPUB 导入 content_hash 必须建立", identityObserved)
        writeEvidence("t96v7_15_import_identity", mapOf("bookId" to bookId, "resolverSize" to resolverSize))

        markPhase("t96v7_20_seed_assets")
        val now = Instant.now().toString()
        db.readingProgressDao().upsert(
            ReadingProgressEntity(
                book_id = bookId, progress_percent = 0.42f, last_read_at = now,
                total_reading_time_ms = 90_000L,
                current_location_json = "{\"v\":2,\"offset\":42,\"ci\":0,\"co\":42,\"fp\":\"t96v7\"}",
                updated_at = now,
            ),
        )
        db.noteDao().upsert(
            NoteEntity(
                id = "t96v7-note-$runId", book_id = bookId, title = "任务96EPUB笔记",
                body = "neutral-epub-note", chapter_title = "第1章", progress_percent = 0.42f,
                locator_json = "{\"v\":2,\"offset\":42}", created_at = now, updated_at = now,
            ),
        )
        db.highlightDao().upsert(
            HighlightEntity(
                id = "t96v7-highlight-$runId", book_id = bookId, text = "EPUB中性高亮",
                note = "neutral-epub-highlight", chapter_title = "第1章", progress_percent = 0.42f,
                locator_json = "{\"v\":2,\"offset\":60}", created_at = now, updated_at = now,
            ),
        )
        db.inspirationDao().upsert(
            InspirationEntity(
                id = "t96v7-inspiration-$runId", title = "任务96EPUB灵感",
                body = "neutral-epub-inspiration", status = "inbox", source_book_id = bookId,
                payload = "{\"tags\":[\"任务96EPUB\"],\"source\":{\"bookId\":\"$bookId\",\"bookTitle\":\"$epubTitle\",\"chapterTitle\":\"第1章\",\"excerpt\":\"EPUB中性高亮\",\"locatorJson\":\"{\\\"v\\\":2,\\\"offset\\\":60}\"}}",
                created_at = now, updated_at = now,
            ),
        )
        val assetsBefore = assetSnapshot()
        writeEvidence("t96v7_20_seed_assets", mapOf("assetsSha" to sha256(assetsBefore.toByteArray())))

        markPhase("t96v7_30_shelf")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
        assertTrue("必须进入书架", ensureOnShelf(lastPhase))
        assertTrue("书架必须出现本轮 EPUB 卡片", waitFor(lastPhase, UI_TIMEOUT_MS, "EPUB 卡片") {
            composeRule.onAllNodesWithTag("book-tile-$bookId").fetchSemanticsNodes().isNotEmpty()
        })

        markPhase("t96v7_40_clear_cache")
        composeRule.onAllNodesWithContentDescription("更多")[0].performClick()
        composeRule.waitForIdle()
        assertTrue("书架更多菜单必须出现「批量选择」", waitFor(lastPhase, UI_TIMEOUT_MS, "批量选择") {
            composeRule.onAllNodesWithText("批量选择").fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onNodeWithText("批量选择").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("book-tile-$bookId").performClick()
        composeRule.waitForIdle()
        assertTrue("批量操作必须出现「清缓存」", waitFor(lastPhase, UI_TIMEOUT_MS, "清缓存") {
            composeRule.onAllNodesWithText("清缓存").fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onNodeWithText("清缓存").performClick()
        assertTrue("移除 EPUB 正文后必须 missing", waitFor(lastPhase, REPAIR_TIMEOUT_MS, "EPUB missing") {
            rawOne("SELECT content_status FROM books WHERE id='$bookId'") == "missing" &&
                rawOne("SELECT local_content_path FROM books WHERE id='$bookId'").isNullOrBlank()
        })
        assertFalse("移除后 EPUB 内部副本必须不存在", File(internalPath).exists())
        assertEquals("移除 EPUB 正文不得改动用户资产", assetsBefore, assetSnapshot())
        writeEvidence("t96v7_40_clear_cache", mapOf("status" to "missing"))

        markPhase("t96v7_50_relink_same")
        val sameName = pushDownloadFileBytes("t96-d2-$runId.epub", sourceEpub.readBytes())
        openRelinkPicker()
        selectDownloadFile(sameName)
        assertTrue("同 EPUB 重新关联必须恢复 available", waitFor(lastPhase, REPAIR_TIMEOUT_MS, "EPUB available") {
            rawOne("SELECT content_status FROM books WHERE id='$bookId'") == "available"
        })
        val relinkedRow = db.bookDao().getById(bookId)
        assertNotNull("重新关联后必须仍是同一 EPUB 记录", relinkedRow)
        assertEquals("重新关联不得改变 bookId", bookId, relinkedRow!!.id)
        val relinkedPath = requireNotNull(rawOne("SELECT local_content_path FROM books WHERE id='$bookId'"))
        assertEquals("重新关联后的 EPUB 内部字节必须与源一致", sha256(sourceEpub.readBytes()), sha256(File(relinkedPath).readBytes()))
        assertEquals("重新关联后用户资产必须保持", assetsBefore, assetSnapshot())
        assertEquals("EPUB content_hash 必须与源 MD5 一致", epubMd5, rawOne("SELECT content_hash FROM books WHERE id='$bookId'"))
        assertTrue("重新关联后操作面板必须关闭", waitFor(lastPhase, UI_TIMEOUT_MS, "面板关闭") {
            composeRule.onAllNodesWithText("重新定位文件").fetchSemanticsNodes().isEmpty()
        })
        composeRule.onNodeWithTag("book-tile-$bookId").performClick()
        assertTrue("重新关联后必须进入 EPUB 阅读器", waitFor(lastPhase, UI_TIMEOUT_MS, "EPUB 阅读器") {
            composeRule.onAllNodesWithContentDescription("阅读正文已就绪").fetchSemanticsNodes().isNotEmpty()
        })
        assertTrue("重新关联后必须读到 EPUB 章节正文", waitFor(lastPhase, UI_TIMEOUT_MS, "EPUB 正文可见") {
            composeRule.onAllNodesWithText(epubBody.take(12), substring = true).fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onAllNodesWithContentDescription("返回")[0].performClick()
        composeRule.waitForIdle()
        assertTrue("必须回到书架", ensureOnShelf(lastPhase))
        val assetsAfterReading = assetSnapshot()
        val nonProgressBefore = nonProgressAssets()
        val nonProgressAfter = nonProgressAssets()
        File(resultsDir, "t96v7_55_assets_after_reading.json").writeText(
            JSONObject().put("assetsBefore", assetsBefore).put("assetsAfterReading", assetsAfterReading)
                .put("nonProgressBefore", nonProgressBefore).put("nonProgressAfter", nonProgressAfter)
                .toString(2),
        )
        assertEquals("阅读只允许更新进度，EPUB 笔记/高亮/灵感来源必须保持", nonProgressBefore, nonProgressAfter)
        writeEvidence("t96v7_50_relink_same", mapOf("bookId" to bookId, "contentSha" to sha256(sourceEpub.readBytes())))

        markPhase("t96v7_60_relink_different")
        val diffEpub = File(context.cacheDir, "diagnostics/t96-d2-fixtures/$runId-diff.epub")
        D2EpubTestFixture.createMinimalEpub(
            dest = diffEpub, title = epubTitle, body = epubDifferentBody, identifier = "t96v7-diff-$runId",
        )
        val differentName = pushDownloadFileBytes("t96-d2-$runId-diff.epub", diffEpub.readBytes())
        val availableBytes = File(relinkedPath).readBytes()
        val availableHash = requireNotNull(rawOne("SELECT content_hash FROM books WHERE id='$bookId'"))
        val p6AssetsBefore = assetSnapshot()
        openRelinkPicker()
        selectDownloadFile(differentName)
        assertTrue("不同 EPUB 必须明确提示不一致", waitFor(lastPhase, REPAIR_TIMEOUT_MS, "不一致提示") {
            composeRule.onAllNodesWithText("不一致", substring = true).fetchSemanticsNodes().isNotEmpty()
        })
        assertEquals("拒绝后 EPUB 状态不变", "available", rawOne("SELECT content_status FROM books WHERE id='$bookId'"))
        assertEquals("拒绝后 content_hash 不变", availableHash, rawOne("SELECT content_hash FROM books WHERE id='$bookId'"))
        assertEquals("拒绝后 EPUB 内部字节不变", sha256(availableBytes), sha256(File(relinkedPath).readBytes()))
        val p6AssetsAfter = assetSnapshot()
        File(resultsDir, "t96v7_60_assets.json").writeText(
            JSONObject().put("statusBefore", "available").put("statusAfter", rawOne("SELECT content_status FROM books WHERE id='$bookId'"))
                .put("hashBefore", availableHash).put("hashAfter", rawOne("SELECT content_hash FROM books WHERE id='$bookId'"))
                .put("bytesShaBefore", sha256(availableBytes)).put("bytesShaAfter", sha256(File(relinkedPath).readBytes()))
                .put("assetsBefore", p6AssetsBefore).put("assetsAfter", p6AssetsAfter).toString(2),
        )
        assertEquals("拒绝后用户资产不变", p6AssetsBefore, p6AssetsAfter)
        writeEvidence("t96v7_60_relink_different", mapOf("rejected" to true))

        markPhase("t96v7_70_cancel")
        val p7AssetsBefore = assetSnapshot()
        openRelinkPicker()
        var attempts = 0
        while (documentsUiRoot() != null && attempts < 3) {
            pressBack()
            composeRule.waitForIdle()
            attempts += 1
        }
        assertTrue("取消后系统选择器必须关闭", waitFor(lastPhase, UI_TIMEOUT_MS, "选择器关闭") { documentsUiRoot() == null })
        assertEquals("取消后 EPUB 状态不变", "available", rawOne("SELECT content_status FROM books WHERE id='$bookId'"))
        assertEquals("取消后 EPUB 内部字节不变", sha256(availableBytes), sha256(File(relinkedPath).readBytes()))
        val p7AssetsAfter = assetSnapshot()
        File(resultsDir, "t96v7_70_assets.json").writeText(
            JSONObject().put("statusBefore", "available").put("statusAfter", rawOne("SELECT content_status FROM books WHERE id='$bookId'"))
                .put("bytesShaBefore", sha256(availableBytes)).put("bytesShaAfter", sha256(File(relinkedPath).readBytes()))
                .put("assetsBefore", p7AssetsBefore).put("assetsAfter", p7AssetsAfter).toString(2),
        )
        assertEquals("取消后用户资产不变", p7AssetsBefore, p7AssetsAfter)
        writeEvidence("t96v7_70_cancel", mapOf("backAttempts" to attempts))

        markPhase("t96v7_done")
    }


    // ── 产品入口 ─────────────────────────────────────────────────────────────

    private fun openRelinkPicker() {
        composeRule.onNodeWithTag("book-tile-$bookId").performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertTrue("长按必须出现「重新定位文件」", waitFor(lastPhase, UI_TIMEOUT_MS, "重新定位文件") {
            composeRule.onAllNodesWithText("重新定位文件").fetchSemanticsNodes().isNotEmpty()
        })
        composeRule.onNodeWithText("重新定位文件").performClick()
        composeRule.waitForIdle()
        assertTrue("必须拉起系统文档选择器", waitFor(lastPhase, PICKER_TIMEOUT_MS, "DocumentsUI") {
            documentsUiRoot() != null
        })
        writePickerSnapshot("t96_d2_picker_initial")
    }

    private fun selectDownloadFile(fileName: String) {
        if (composeRuleFileVisible(fileName)) {
            clickUnique(fileRowNodes(fileName), "file $fileName")
            waitPickerClosed()
            return
        }
        // 96-v1 设备证据：打开选择器会记住 Download，但长列表只挂载可见项，目标文件可能在视口外。
        // 若已在 Download，先用系统搜索定位本轮精确文件名（不走滚动猜测）。
        val alreadyInDownload = nodes().any {
            it.viewIdResourceName?.endsWith("/header_title") == true &&
                it.text?.toString()?.contains("Download") == true
        }
        if (alreadyInDownload) {
            assertTrue("已在 Download 时必须通过系统搜索唯一定位本轮文件", searchAndSelectFile(fileName))
            waitPickerClosed()
            return
        }
        clickUnique(nodes().filter {
            it.contentDescription?.toString() in setOf("显示根目录", "Show roots")
        }, "DocumentsUI roots drawer")
        val (providerTitle, documentId) = queryProviderPrimaryRoot()
        assertEquals("provider primary documentId 必须精确", "primary:", documentId)
        try {
            waitFor(lastPhase, PICKER_TIMEOUT_MS, "provider 根行") { rootsListTitleNodes(providerTitle).isNotEmpty() }
        } finally {
            writePickerSnapshot("t96_d2_picker_drawer")
        }
        val rows = rootsListTitleNodes(providerTitle)
        check(rows.size == 1) { "provider 根行必须唯一，实得 ${rows.size}" }
        clickUnique(rows, "provider root")
        waitFor(lastPhase, PICKER_TIMEOUT_MS, "抽屉收起") { rootsList() == null }
        assertTrue("必须出现 Download 目录", waitFor(lastPhase, PICKER_TIMEOUT_MS, "Download") {
            nodes().count { it.text?.toString() == "Download" && it.viewIdResourceName?.endsWith("/title") == true } == 1
        })
        clickUnique(nodes().filter {
            it.text?.toString() == "Download" && it.viewIdResourceName?.endsWith("/title") == true
        }, "Download")
        if (!waitFor(lastPhase, PICKER_TIMEOUT_MS, "文件 $fileName") { fileRowNodes(fileName).isNotEmpty() }) {
            assertTrue("Download 中必须能通过系统搜索找到本轮文件", searchAndSelectFile(fileName))
        } else {
            clickUnique(fileRowNodes(fileName), "file $fileName")
        }
        waitPickerClosed()
    }

    /** 用 DocumentsUI 自带搜索定位精确文件名；只绑定结果行，不把搜索框自身当结果。 */
    private fun searchAndSelectFile(fileName: String): Boolean {
        writePickerSnapshot("t96_d2_search_before_click")
        val search = nodes().firstOrNull {
            it.viewIdResourceName?.endsWith("/option_menu_search") == true ||
                it.contentDescription?.toString() in setOf("搜索", "Search")
        } ?: return false
        val searchClicked = search.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
            (search.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false)
        composeRule.waitForIdle()
        writePickerSnapshot("t96_d2_search_open")
        if (!waitFor(lastPhase, PICKER_TIMEOUT_MS, "搜索输入框") { nodes().any { it.isEditable } }) {
            writeSearchAction(fileName, searchClicked, null, emptyList(), "no-edit-field")
            return false
        }
        val field = nodes().first { it.isEditable }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fileName)
        }
        val setTextResult = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        val found = waitFor(lastPhase, PICKER_TIMEOUT_MS, "搜索结果 $fileName") { fileRowNodes(fileName).isNotEmpty() }
        writePickerSnapshot("t96_d2_search_results")
        val rows = fileRowNodes(fileName)
        writeSearchAction(
            fileName, searchClicked, field,
            rows.map { Triple(it.viewIdResourceName, it.isEditable, it.isVisibleToUser) },
            "rows=${rows.size}, setText=$setTextResult, found=$found",
        )
        if (!found || rows.size != 1) return false
        clickUnique(rows, "search result $fileName")
        return true
    }

    private fun writeSearchAction(
        fileName: String,
        searchClicked: Boolean,
        field: AccessibilityNodeInfo?,
        rows: List<Triple<String?, Boolean, Boolean>>,
        outcome: String,
    ) {
        val rowsJson = JSONArray()
        rows.forEach { r ->
            rowsJson.put(JSONObject().put("id", r.first).put("editable", r.second).put("visible", r.third))
        }
        File(resultsDir, "t96_d2_search_action.json").writeText(
            JSONObject()
                .put("fileName", fileName)
                .put("searchClicked", searchClicked)
                .put("inputId", field?.viewIdResourceName)
                .put("inputEditable", field?.isEditable)
                .put("inputText", field?.text?.toString())
                .put("resultRows", rowsJson)
                .put("outcome", outcome)
                .toString(2),
        )
    }

    private fun waitPickerClosed() {
        assertTrue("选择文件后必须离开选择器", waitFor(lastPhase, PICKER_TIMEOUT_MS, "选择器关闭") {
            documentsUiRoot() == null
        })
    }

    /** 只认文件结果行的标题节点；排除搜索输入框自身（EditableText 也会显示同名文本）。 */
    private fun fileRowNodes(fileName: String): List<AccessibilityNodeInfo> =
        nodes().filter {
            !it.isEditable && it.isVisibleToUser && it.text?.toString() == fileName &&
                (it.viewIdResourceName == "android:id/title" ||
                    it.viewIdResourceName?.endsWith("/title") == true)
        }

    private fun composeRuleFileVisible(fileName: String): Boolean = fileRowNodes(fileName).isNotEmpty()

    // ── 夹具 ────────────────────────────────────────────────────────────────

    private fun pushDownloadFile(name: String, text: String): String =
        pushDownloadFileBytes(name, text.toByteArray(Charsets.UTF_8))

    private fun pushDownloadFileBytes(name: String, bytes: ByteArray): String {
        val path = "/storage/emulated/0/Download/$name"
        val manifestFile = File(resultsDir, "t96_d2_download_manifest.json")
        val manifest = if (manifestFile.exists()) JSONObject(manifestFile.readText()) else JSONObject().put("files", JSONArray())
        manifest.getJSONArray("files").put(
            JSONObject().put("path", path).put("name", name)
                .put("sha256", sha256(bytes)).put("bytes", bytes.size),
        )
        manifestFile.writeText(manifest.toString(2))
        File(resultsDir, "t96_d2_download_$name").writeBytes(bytes)
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val out = shell(
            "printf '%s' '$b64' | base64 -d > '$path'\n" +
                "echo CRA_WRITE_RC=\$?\n" +
                "sha256sum '$path'\n" +
                "stat -c %s '$path'\n",
        )
        assertTrue("夹具写入必须成功：$out", out.contains("CRA_WRITE_RC=0"))
        assertTrue("设备夹具 SHA 必须与 E 一致", out.contains(sha256(bytes)))
        return name
    }

    // ── 用户资产快照 ─────────────────────────────────────────────────────────

    /** 不含 reading_progress 的资产快照：用于“阅读允许更新进度”的精确比较。 */
    private fun nonProgressAssets(): String = buildString {
        appendLine(rowDump("notes", "id", "t96-note-$runId"))
        appendLine(rowDump("highlights", "id", "t96-highlight-$runId"))
        appendLine(rowDump("inspirations", "id", "t96-inspiration-$runId"))
    }

    private fun assetSnapshot(): String = buildString {
        appendLine(rowDump("reading_progress", "book_id", bookId))
        appendLine(rowDump("notes", "id", "t96-note-$runId"))
        appendLine(rowDump("highlights", "id", "t96-highlight-$runId"))
        appendLine(rowDump("inspirations", "id", "t96-inspiration-$runId"))
    }

    private fun rowDump(table: String, key: String, value: String): String {
        db.openHelper.readableDatabase.query("SELECT * FROM $table WHERE $key=?", arrayOf(value)).use { c ->
            if (!c.moveToFirst()) return "$table:ABSENT"
            val parts = mutableListOf<String>()
            for (i in 0 until c.columnCount) parts += "${c.getColumnName(i)}=${c.getString(i)}"
            return "$table:" + parts.joinToString("|")
        }
    }

    // ── 数据库/导入工具 ─────────────────────────────────────────────────────

    private fun activeBookIds(): Set<String> {
        val ids = LinkedHashSet<String>()
        db.openHelper.readableDatabase
            .query("SELECT id FROM books WHERE deleted_at IS NULL")
            .use { c -> while (c.moveToNext()) ids.add(c.getString(0)) }
        return ids
    }

    private fun rawOne(sql: String): String? =
        db.openHelper.writableDatabase.query(sql).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    private object RealFileOps : FileOps {
        override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)
        override fun copyTo(source: File, dest: File) {
            source.copyTo(dest, overwrite = true)
        }
    }

    private fun buildImporter(): ShelfImporter {
        val bookDao = db.bookDao()
        val contentDao = db.bookContentDao()
        val fileDao = db.bookFileDao()
        val progressDao = db.readingProgressDao()
        val repository = BookRepository(
            bookDao = bookDao,
            bookContentDao = contentDao,
            bookFileDao = fileDao,
            progressDao = progressDao,
            sessionDao = db.readingSessionDao(),
            noteDao = db.noteDao(),
            highlightDao = db.highlightDao(),
            inspirationDao = db.inspirationDao(),
            bookTagDao = db.bookTagDao(),
            bookCategoryDao = db.bookCategoryDao(),
            shelfBookDao = db.shelfBookDao(),
            chapterReadDao = db.chapterReadDao(),
            context = context,
        )
        val epubRepository = EpubRepository(
            context = context,
            bookDao = bookDao,
            readingProgressDao = progressDao,
            ioDispatcher = Dispatchers.IO,
            fileOps = RealFileOps,
        )
        return ShelfImporter(
            context = context,
            repository = repository,
            bookDao = bookDao,
            bookContentDao = contentDao,
            bookFileDao = fileDao,
            epubRepository = epubRepository,
            importHistoryStore = ImportHistoryStore(context, CoroutineScope(Dispatchers.IO)),
            sourceIndex = LibrarySourceIndex(db.librarySourceRefDao()),
            rootStore = LibraryRootStore(context, CoroutineScope(Dispatchers.IO)),
            ioDispatcher = Dispatchers.IO,
            indexRefreshCoordinator = mockk<SearchIndexBookRefreshCoordinator>(relaxed = true),
        )
    }

    // ── UI/无障碍工具（与既有验收测试同口径） ───────────────────────────────

    private fun waitFor(phase: String, timeoutMs: Long, description: String, block: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (block()) return true
            composeRule.waitForIdle()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        android.util.Log.w(TAG, "$phase 等待超时（$timeoutMs ms）：$description")
        return false
    }

    private fun topLevelPage(): String? = when {
        composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty() -> "shelf"
        composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() -> "home"
        else -> null
    }

    private fun ensureOnShelf(phase: String): Boolean {
        val appeared = waitFor(phase, UI_TIMEOUT_MS, "首页或书架页面出现") { topLevelPage() != null }
        if (!appeared) return false
        if (topLevelPage() == "shelf") return true
        composeRule.onNode(hasText("书架") and isSelectable()).performClick()
        composeRule.waitForIdle()
        return waitFor(phase, UI_TIMEOUT_MS, "书架页面出现") { topLevelPage() == "shelf" }
    }

    private fun documentsUiRoot(): AccessibilityNodeInfo? {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo ?: AccessibilityServiceInfo()
        if ((info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) == 0) {
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
        }
        return automation.windows.mapNotNull { w ->
            val root = w.root ?: return@mapNotNull null
            if (root.packageName?.toString() in DOCUMENTS_UI_PACKAGES) root else null
        }.firstOrNull()
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = documentsUiRoot() ?: return emptyList()
        val found = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            check(found.size < MAX_A11Y_NODES) { "DocumentsUI tree exceeded bounded scan" }
            val n = queue.removeFirst()
            found += n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return found
    }

    private fun clickUnique(matches: List<AccessibilityNodeInfo>, description: String) {
        check(matches.size == 1) { "$description must have exactly one real node, got ${matches.size}" }
        var target: AccessibilityNodeInfo? = matches.single()
        while (target != null && !target.isClickable) target = target.parent
        check(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Cannot click $description" }
    }

    private fun rootsList(): AccessibilityNodeInfo? =
        nodes().firstOrNull { it.viewIdResourceName?.endsWith("/roots_list") == true && it.isVisibleToUser }

    private fun rootsListTitleNodes(title: String): List<AccessibilityNodeInfo> {
        val list = rootsList() ?: return emptyList()
        val found = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(list)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.text?.toString() == title) found += n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return found
    }

    private fun queryProviderPrimaryRoot(): Pair<String, String> {
        val raw = runRootShellScript(
            "su 0 content query --uri content://com.android.externalstorage.documents/root " +
                "--projection root_id:title:document_id\n",
        )
        val match = Regex("root_id=primary, title=(.*?), document_id=(\\S+)").find(raw)
            ?: throw AssertionError("provider primary 根查询失败：$raw")
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun runRootShellScript(script: String): String = UiAutomationShellChannel.run(script)

    private fun shell(script: String): String = UiAutomationShellChannel.run(script)

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
        )
    }

    private fun writePickerSnapshot(name: String) {
        val rows = JSONArray()
        nodes().forEach { n ->
            val rect = Rect()
            n.getBoundsInScreen(rect)
            rows.put(
                JSONObject().put("id", n.viewIdResourceName).put("text", n.text?.toString())
                    .put("description", n.contentDescription?.toString())
                    .put("visible", n.isVisibleToUser).put("clickable", n.isClickable)
                    .put("bounds", rect.toShortString()),
            )
        }
        File(resultsDir, "$name.json").writeText(JSONObject().put("nodes", rows).toString(2))
    }

    // ── 证据 ────────────────────────────────────────────────────────────────

    private fun markPhase(phase: String) {
        lastPhase = phase
    }

    private fun writeEvidence(phase: String, details: Map<String, Any?>) {
        val obj = JSONObject().put("phase", phase).put("pass", true)
            .put("at", Instant.now().toString()).put("runId", runId).put("bookId", bookId)
        details.forEach { (k, v) -> obj.put(k, v ?: JSONObject.NULL) }
        File(resultsDir, "$phase.json").writeText(obj.toString(2))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
}
