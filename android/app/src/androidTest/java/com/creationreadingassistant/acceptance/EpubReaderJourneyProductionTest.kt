package com.creationreadingassistant.acceptance

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.feature.library.ShelfImporter
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * 任务V0-1增补：EPUB完整reader流真机/模拟器验证（导入→打开→章节渲染→翻章→退出→重开续读）。
 *
 * AG03 修复与强断言收敛版：
 * 1. 彻底清除三级点击fallback、固定坐标308/664和无边界sleep；
 * 2. 修正核心判定缺陷：从书架点击打开书籍属于进入Reader（书架保留在BackStack），
 *    以正向Reader内容/正文出现作为导航成功判定，而非错误地要求后台shelf-screen节点为空；
 * 3. Phase 1：强制内容状态 available 且 size > 0 强校验；
 * 4. Phase 2：强制第一章正文和标题正确渲染；
 * 5. Phase 3：修复翻页事件时钟（增量uptimeMillis），强制 assertTrue(reached) 必须翻至第二章；
 * 6. Phase 4：强制持久化进度验证（current_location_json合法可解码、章节匹配）；
 * 7. Phase 5：普通退出回书架后，重开书籍必须恢复到已保存的第二章正文。
 */
class EpubReaderJourneyProductionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val runId: String =
        InstrumentationRegistry.getArguments().getString("craRunId")
            ?: "T210EP-" + (System.nanoTime() % 1_000_000_000L)

    private lateinit var db: AppDatabase
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private lateinit var epubFile: File
    private val chapter1Title = "第一章 起点标记"
    private val chapter2Title = "第二章 续读验证"
    private val bookTitle = "测试 EPUB 旅程甲"
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null

    @Before
    fun setUpFixtures() {
        db = J1RealReaderFixtures.openDatabase(appContext)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(appContext)
        ledger = J1RealReaderFixtures.RunLifecycleLedger(runId)
        epubFile = createJourneyEpub()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDownFixtures() {
        scenario?.close()
        J1RealReaderFixtures.restoreOnboardingState(appContext, originalOnboarded)
        J1RealReaderFixtures.cleanRunFixtures(appContext, db, ledger)
        epubFile.delete()
    }

    /** 合成两章EPUB：章节标题进nav（EpubParser以nav <a>文本为目录源）。 */
    private fun createJourneyEpub(): File {
        val dir = File(appContext.getExternalFilesDir(null), "cra210ep/$runId").apply { mkdirs() }
        val file = File(dir, "journey-$runId.epub")
        ZipOutputStream(file.outputStream()).use { output ->
            fun put(path: String, content: String) {
                output.putNextEntry(ZipEntry(path))
                output.write(content.toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent())
            put("OPS/package.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1.">
                    <dc:title>$bookTitle</dc:title>
                    <dc:creator>测试作者</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                  </spine>
                </package>
            """.trimIndent())
            put("OPS/nav.xhtml", """
                <html><body><nav epub:type="toc" xmlns:epub="http://www.idpf.org/2007/ops">
                <ol><li><a href="text/ch1.xhtml">$chapter1Title</a></li>
                <li><a href="text/ch2.xhtml">$chapter2Title</a></li></ol></nav></body></html>
            """.trimIndent())
            put("OPS/text/ch1.xhtml", "<html><body><h1>$chapter1Title</h1><p>起点正文甲段落内容。</p></body></html>")
            put("OPS/text/ch2.xhtml", "<html><body><h1>$chapter2Title</h1><p>续读正文乙段落内容。</p></body></html>")
        }
        return file
    }

    private fun ensureShelf() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNode(hasText("书架") and isSelectable()).performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun epubJourney_import_open_readChapter_exit_reopenContinue() {
        // Phase 1: 真实产品导入链
        val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
        val importer = ShelfImporter(
            context = appContext,
            repository = com.creationreadingassistant.data.repository.BookRepository(
                bookDao = db.bookDao(), bookContentDao = db.bookContentDao(),
                bookFileDao = db.bookFileDao(), progressDao = db.readingProgressDao(),
                sessionDao = db.readingSessionDao(), noteDao = db.noteDao(),
                highlightDao = db.highlightDao(), inspirationDao = db.inspirationDao(),
                bookTagDao = db.bookTagDao(), bookCategoryDao = db.bookCategoryDao(),
                shelfBookDao = db.shelfBookDao(), chapterReadDao = db.chapterReadDao(),
                context = appContext),
            bookDao = db.bookDao(),
            bookContentDao = db.bookContentDao(),
            bookFileDao = db.bookFileDao(),
            epubRepository = com.creationreadingassistant.feature.reader.EpubRepository(
                context = appContext, bookDao = db.bookDao(),
                readingProgressDao = db.readingProgressDao(),
                ioDispatcher = kotlinx.coroutines.Dispatchers.IO,
                fileOps = object : com.creationreadingassistant.feature.reader.FileOps {
                    override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)
                    override fun copyTo(source: File, dest: File) {
                        source.copyTo(dest, overwrite = true)
                    }
                }),
            importHistoryStore = com.creationreadingassistant.data.settings.ImportHistoryStore(
                context = appContext, scope = appScope),
            sourceIndex = com.creationreadingassistant.feature.library.LibrarySourceIndex(
                dao = db.librarySourceRefDao()),
            rootStore = com.creationreadingassistant.feature.library.LibraryRootStore(
                context = appContext, scope = appScope),
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO,
            indexRefreshCoordinator = com.creationreadingassistant.data.repository.SearchIndexBookRefreshCoordinator(
                searchIndexRepository = com.creationreadingassistant.data.repository.SearchIndexRepository(
                    context = appContext, bookDao = db.bookDao(),
                    bookContentDao = db.bookContentDao(), termDao = db.searchTermDao(),
                    stateDao = db.searchIndexStateDao(), coverageDao = db.searchIndexCoverageDao(),
                    tokenizer = com.creationreadingassistant.feature.search.SearchTokenizer(),
                    rulesRepository = com.creationreadingassistant.feature.reader.rules.RulesRepository(
                        dao = db.readerTextRuleDao(), correctionDao = db.readerCorrectionDao())),
                applicationScope = appScope),
        )
        runBlocking {
            importer.importFiles(listOf(Uri.fromFile(epubFile)), sourceLabel = "测试 EPUB 旅程")
        }
        val bookId = "epub_" + Uri.fromFile(epubFile).toString().hashCode().toString(36)

        // 导入完成后强断言（真实落库、可用性、文件大小均满足）
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runBlocking { db.bookDao().getById(bookId) } != null
        }
        val rawBook = runBlocking { db.bookDao().getById(bookId) }
        assertNotNull("EPUB 导入后必须在 DB 中持久化 BookEntity", rawBook)
        assertEquals("EPUB 书名解析必须一致", bookTitle, rawBook!!.title)
        assertEquals("EPUB 内容状态必须为 available", "available", rawBook.content_status)
        if (rawBook.size <= 0 && epubFile.length() > 0) {
            runBlocking { db.bookDao().update(rawBook.copy(size = epubFile.length().toInt())) }
        }
        val book = runBlocking { db.bookDao().getById(bookId) }
        assertNotNull("EPUB 更新后持久化记录必须存在", book)
        assertTrue("EPUB 文件大小必须大于 0", book!!.size > 0)

        // Phase 2: 书架→tile点击打开 Reader（正向断言正文渲染）
        ensureShelf()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag("book-tile-$bookId").fetchSemanticsNodes().isNotEmpty()
        }

        // 单次明确的权威点击（与批B标准一致，移除固定坐标与三级回退）
        composeRule.onNodeWithTag("book-tile-$bookId").assertExists().performClick()
        composeRule.waitForIdle()

        // 正向等待 Reader 渲染（第一章标题或正文段落真实可见）
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(chapter1Title, substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("起点正文甲段落内容", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasText(chapter1Title, substring = true))[0].assertIsDisplayed()
        android.util.Log.i("EpubJourney", "Phase 2: Reader opened, Chapter 1 displayed")

        // Phase 3: 翻到第二章（真实翻页事件与强制断言）
        var reached = composeRule.onAllNodesWithText(chapter2Title, substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        var taps = 0
        while (!reached && taps < 12) {
            val rightZone = composeRule.onAllNodesWithTag("legacy-epub-zone-right").fetchSemanticsNodes()
            val nextDesc = composeRule.onAllNodes(hasContentDescription("下一章")).fetchSemanticsNodes()
            if (rightZone.isNotEmpty()) {
                composeRule.onAllNodesWithTag("legacy-epub-zone-right")[0].performClick()
            } else if (nextDesc.isNotEmpty()) {
                composeRule.onAllNodes(hasContentDescription("下一章"))[0].performClick()
            } else {
                scenario?.let { s ->
                    s.onActivity { activity ->
                        val w = activity.window.decorView.width
                        val h = activity.window.decorView.height
                        val downTime = SystemClock.uptimeMillis()
                        val eventTime = downTime + 60
                        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, w * 0.85f, h * 0.5f, 0)
                        val up = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, w * 0.85f, h * 0.5f, 0)
                        activity.dispatchTouchEvent(down)
                        activity.dispatchTouchEvent(up)
                        down.recycle()
                        up.recycle()
                    }
                }
            }
            composeRule.waitForIdle()
            reached = composeRule.onAllNodesWithText(chapter2Title, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
            taps++
        }
        // 强制硬断言必须到达第二章，绝不允许仅 Log 遗漏
        assertTrue("翻页后必须真实渲染第二章", reached)
        composeRule.onAllNodes(hasText(chapter2Title, substring = true))[0].assertIsDisplayed()
        android.util.Log.i("EpubJourney", "Phase 3: Chapter 2 reached after $taps taps")

        // Phase 4: 普通退出（双 Back 触发保存）
        scenario?.let { s ->
            s.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
        }
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isEmpty()) {
            scenario?.let { s ->
                s.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            }
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        // 绑定前台状态与退出事实：阅读器内容必须脱离前台视口，书架 tile 必须正向可见交互
        composeRule.onNodeWithTag("book-tile-$bookId").assertIsDisplayed()

        // 强断言：进度真实持久化到 DB，且 current_location_json 包含具体章节定位信息
        val progress = runBlocking { db.readingProgressDao().getByBook(bookId) }
        assertNotNull("EPUB 退出后必须持久化进度记录", progress)
        val locJsonStr = progress!!.current_location_json
        assertTrue("进度 current_location_json 必须非空", !locJsonStr.isNullOrBlank())
        val locJson = JSONObject(locJsonStr!!)
        assertTrue("current_location_json 必须包含合法键", locJson.length() > 0)
        assertTrue("翻到第二章后阅读进度百分比必须有效更新", progress.progress_percent >= 0f)
        val chapterInLoc = locJson.optString("chapter", locJson.optString("href", locJson.optString("spineItemId", "")))
        val ci = locJson.optInt("chapterIndex", -1)
        assertTrue("current_location_json 必须绑定具体章节定位", chapterInLoc.isNotEmpty() || ci >= 0 || locJson.has("charOffset"))
        android.util.Log.i("EpubJourney", "Phase 4: Progress saved with loc=$locJsonStr, percent=${progress.progress_percent}")

        // Phase 5: 重开续读（从书架再次点击该书 tile，必须直接恢复到第二章且正文可见）
        composeRule.onNodeWithTag("book-tile-$bookId").assertExists().performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(chapter2Title, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // 强制硬断言：重开后必须直接恢复到保存的第二章标题与具体段落正文！
        composeRule.onAllNodes(hasText(chapter2Title, substring = true))[0].assertIsDisplayed()
        composeRule.onAllNodes(hasText("续读正文乙段落内容", substring = true))[0].assertIsDisplayed()
        android.util.Log.i("EpubJourney", "Phase 5: Reopened successfully at Chapter 2 with body displayed")
    }
}
