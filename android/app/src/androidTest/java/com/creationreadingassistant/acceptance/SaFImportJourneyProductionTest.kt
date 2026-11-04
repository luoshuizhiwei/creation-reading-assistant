package com.creationreadingassistant.acceptance

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * NB04：SAF 真实用户导入旅程（系统 DocumentsUI 全链）。
 *
 * AR03 合同单独记的「SAF UI 未测」缺口：既有 EPUB 旅程用 importFiles 直调，
 * 绕过了用户实际走的「书架 → 导入书籍 → 选择文件 → 系统 SAF 选择器 → 选中 →
 * 落库 → 书架 tile → 打开 reader」全链。本测试补齐：
 *
 * 1. 测试内合成两章 EPUB，经 UiDevice.executeShellCommand 复制到 /sdcard/Download
 *    （shell uid 可写公共 Downloads，选择器默认根可达）；
 * 2. 真实 UI：书架顶栏「导入书籍」→ 导入页「选择文件」→ OpenMultipleDocuments
 *    拉起系统 DocumentsUI → UiAutomator 驱动（先试最近列表，再走 下载/Show roots 抽屉）→
 *    点选本 runId 的文件；
 * 3. 返回应用后断言真实落库（bookDao available）+ 返回书架断言 tile 可见 → 打开 reader
 *    第一章标题渲染。
 *
 * 注意：系统选择器部分只能 sleep+轮询（waitUntil 超时抛异常，任务94 教训）；
 * DocumentsUI 包名兼容 AOSP/Google 两种；中英文环境双兼容（MuMu 中文环境）。
 */
class SaFImportJourneyProductionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)

    private val runId: String =
        InstrumentationRegistry.getArguments().getString("craRunId")
            ?: "T210SAF-" + (System.nanoTime() % 1_000_000_000L)

    private lateinit var db: AppDatabase
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private val bookTitle = "测试 SAF 导入旅程"
    private val chapter1Title = "第一章 SAF 起点"
    private val downloadName = "saf-journey-$runId.epub"
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null
    private var downloadUri: Uri? = null

    @Before
    fun setUpFixtures() {
        db = J1RealReaderFixtures.openDatabase(appContext)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(appContext)
        ledger = J1RealReaderFixtures.RunLifecycleLedger(runId)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDownFixtures() {
        scenario?.close()
        J1RealReaderFixtures.restoreOnboardingState(appContext, originalOnboarded)
        J1RealReaderFixtures.cleanRunFixtures(appContext, db, ledger)
        // 清理公共 Downloads 的测试文件（shell uid 写入，shell 可删）
        runCatching {
            uiDevice.executeShellCommand("rm -f /sdcard/Download/$downloadName")
        }
        downloadUri?.let { uri ->
            runCatching {
                appContext.contentResolver.delete(uri, null, null)
            }
        }
    }

    /** 合成两章 EPUB 并放入公共 Downloads（MediaStore 入册 + shell cp 双保险）。 */
    private fun stageEpubToDownloads() {
        val dir = File(appContext.getExternalFilesDir(null), "cra210saf/$runId").apply { mkdirs() }
        val staged = File(dir, downloadName)
        ZipOutputStream(staged.outputStream()).use { output ->
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
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                  </spine>
                </package>
            """.trimIndent())
            put("OPS/nav.xhtml", """
                <html><body><nav epub:type="toc" xmlns:epub="http://www.idpf.org/2007/ops">
                <ol><li><a href="text/ch1.xhtml">$chapter1Title</a></li></ol></nav></body></html>
            """.trimIndent())
            put("OPS/text/ch1.xhtml", "<html><body><h1>$chapter1Title</h1><p>SAF 正文段落内容。</p></body></html>")
        }

        // MediaStore.Downloads 入册（Android 12 规范路径：应用自己的 insert 无需权限，
        // DocumentsUI 的 Downloads/Recent 根立即可见）。fail-loud：入册后必须可查询。
        val resolver = appContext.contentResolver
        val existing = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=?",
            arrayOf(downloadName),
            null,
        )
        val existingId = existing?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        if (existingId == null) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, downloadName)
                put(MediaStore.Downloads.MIME_TYPE, "application/epub+zip")
            }
            downloadUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            assertNotNull("MediaStore.Downloads 插入必须成功（否则选择器不可见）", downloadUri)
            resolver.openOutputStream(downloadUri!!)!!.use { out ->
                staged.inputStream().use { it.copyTo(out) }
            }
        }
        val verify = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.SIZE),
            "${MediaStore.Downloads.DISPLAY_NAME}=?",
            arrayOf(downloadName),
            null,
        )
        val verifyId = verify?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        assertNotNull("MediaStore 必须能按名查到测试文件（DocumentsUI 可见性前提）", verifyId)
        // 等待 MediaStore 扫描稳定
        Thread.sleep(1_500)
    }

    private fun ensureShelf() {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("home-screen").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isEmpty()) {
            // 底栏书架 tab：与 EpubReaderJourneyProductionTest 验证过的定位同形
            composeRule.onNode(hasText("书架") and isSelectable()).performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** 诊断快照：选择器当前窗口的可见文本/描述（进断言消息，绕开 logcat 采集盲区）。 */
    private fun snapshotPickerTexts(): String {
        return runCatching {
            val texts = StringBuilder()
            uiDevice.findObjects(By.clazz("android.widget.TextView")).take(40).forEach {
                val t = it.text
                if (!t.isNullOrBlank()) texts.append('[').append(t.take(30)).append(']')
            }
            uiDevice.findObjects(By.clazz("android.widget.Button")).take(15).forEach {
                val d = it.contentDescription
                if (!d.isNullOrBlank()) texts.append("(btn)").append('[').append(d.take(30)).append(']')
            }
            "pkg=${uiDevice.currentPackageName} $texts".take(900)
        }.getOrDefault("snapshot-failed:${'$'}{it.message?.take(120)}")
    }

    /** 驱动系统 DocumentsUI 选中本 runId 的文件。 */
    private fun pickFileInSystemPicker() {
        val pickerPkgs = listOf("com.android.documentsui", "com.google.android.documentsui")
        val deadline = System.currentTimeMillis() + 20_000
        var inPicker = false
        while (System.currentTimeMillis() < deadline) {
            if (uiDevice.currentPackageName in pickerPkgs) {
                inPicker = true
                break
            }
            Thread.sleep(500)
        }
        assertTrue("系统 SAF 选择器必须被拉起（DocumentsUI）", inPicker)

        // 先试最近列表直接命中文件；否则走抽屉 → 下载。列表项多于屏宽时必须滚动
        // （findObject 只搜可见项；Download 目录有历史文件，saf-journey 按 s 排序在屏外）。
        fun findFile() = uiDevice.findObject(By.textContains("saf-journey"))
        var clicked = false
        var scrolled = false
        val pickDeadline = System.currentTimeMillis() + 45_000
        while (System.currentTimeMillis() < pickDeadline && !clicked) {
            val direct = findFile()
            if (direct != null) {
                direct.click()
                clicked = true
                break
            }
            val downloads = uiDevice.findObject(By.text("下载"))
                ?: uiDevice.findObject(By.text("Downloads"))
            if (downloads != null && !scrolled) {
                downloads.click()
                Thread.sleep(1_200)
                continue
            }
            // 已在列表视图：向上滚动一屏找目标
            uiDevice.swipe(540, 1500, 540, 500, 10)
            scrolled = true
            Thread.sleep(1_000)
        }
        assertTrue("系统选择器中必须选中测试 EPUB 文件；选择器可见文本快照：${snapshotPickerTexts()}", clicked)
        // 返回应用需要一点时间处理 SAF 回调
        val backDeadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < backDeadline) {
            if (uiDevice.currentPackageName == appContext.packageName) break
            Thread.sleep(500)
        }
    }

    @Test
    fun safJourney_shelfImportPicker_systemDocumentsUI_importShelfTile_openReader() {
        stageEpubToDownloads()
        ensureShelf()

        // Phase 1: 书架顶栏「导入书籍」→ 导入页
        composeRule.onNodeWithContentDescription("导入书籍").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("选择文件").fetchSemanticsNodes().isNotEmpty()
        }
        android.util.Log.i("SafJourney", "Phase 1: import page reached")

        // Phase 2: 「选择文件」→ 系统 DocumentsUI → 选中 → 返回应用
        composeRule.onAllNodesWithText("选择文件")[0].performClick()
        pickFileInSystemPicker()
        android.util.Log.i("SafJourney", "Phase 2: SAF picker returned to app")

        // Phase 3: 真实落库断言（按书名查询：bookId 由生产按 content URI hash 推导，
        // 测试侧不复制该推导——fixtures 保证 DB 干净，该标题唯一）
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runBlocking { db.bookDao().getAllActive().any { it.title == bookTitle } }
        }
        val book = runBlocking { db.bookDao().getAllActive().first { it.title == bookTitle } }
        val expectedBookId = book.id
        assertNotNull("SAF 导入后必须在 DB 中持久化 BookEntity", book)
        assertEquals("SAF 导入书名必须一致", bookTitle, book.title)
        assertEquals("SAF 导入内容状态必须 available", "available", book.content_status)
        android.util.Log.i("SafJourney", "Phase 3: imported into DB as $expectedBookId")

        // Phase 4: 回到书架（系统 Back 离开导入页，定位形态无关），断言 tile 可见
        uiDevice.pressBack()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag("book-tile-$expectedBookId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("book-tile-$expectedBookId").assertExists()
        android.util.Log.i("SafJourney", "Phase 4: shelf tile visible")

        // Phase 5: 点击 tile 打开 reader，第一章标题渲染
        composeRule.onNodeWithTag("book-tile-$expectedBookId").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(chapter1Title, substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("SAF 正文段落内容", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasText(chapter1Title, substring = true))[0].assertExists()
        android.util.Log.i("SafJourney", "Phase 5: reader opened with chapter 1")
    }
}
