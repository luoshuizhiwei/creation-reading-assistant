package com.creationreadingassistant.acceptance

import android.content.Context
import android.graphics.Path
import android.provider.MediaStore
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
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
 * NB11：SAF 文件夹导入旅程（OpenDocumentTree 渠道，NB04 文件渠道的同型覆盖）。
 *
 * 真实用户路径：书架 → 导入书籍 → 「选择文件夹」→ 系统 DocumentsUI 树选择器 →
 * 进入含 EPUB 的子目录 → 确认「使用此文件夹」→ 应用扫描导入 → 书架 tile → reader。
 *
 * staging：MediaStore.Downloads insert 携 RELATIVE_PATH 子目录（MediaProvider 自动建目录，
 * DocumentsUI 的 Download 根可见）。断言按书名查询（bookId 由生产按 URI 推导，测试不复制）。
 * 失败诊断沿用 NB04 惯例：可见文本快照直接进断言消息（绕开 logcat 采集盲区）。
 */
class SaFTreeImportJourneyProductionTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val uiDevice: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val runId: String =
        InstrumentationRegistry.getArguments().getString("craRunId")
            ?: "T210SAFTREE-" + (System.nanoTime() % 1_000_000_000L)

    private lateinit var db: AppDatabase
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private val bookTitle = "测试 SAF 文件夹导入"
    private val chapter1Title = "第一章 文件夹起点"
    private val subDir = "cra-saf-tree-$runId"
    private val fileName = "tree-journey-$runId.epub"
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null
    private var insertedUri: android.net.Uri? = null

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
        insertedUri?.let { uri ->
            runCatching { appContext.contentResolver.delete(uri, null, null) }
        }
    }

    /** 合成 EPUB 并经 MediaStore.Downloads + RELATIVE_PATH 写入 Download/<subDir>/。 */
    private fun stageEpubIntoDownloadSubdir() {
        val dir = File(appContext.getExternalFilesDir(null), "cra210saftree/$runId").apply { mkdirs() }
        val staged = File(dir, fileName)
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
            put("OPS/text/ch1.xhtml", "<html><body><h1>$chapter1Title</h1><p>文件夹正文段落内容。</p></body></html>")
        }

        val resolver = appContext.contentResolver
        val values = android.content.ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/epub+zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/$subDir")
        }
        insertedUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        assertNotNull("MediaStore.Downloads 子目录插入必须成功", insertedUri)
        resolver.openOutputStream(insertedUri!!)!!.use { out ->
            staged.inputStream().use { it.copyTo(out) }
        }
        val verify = resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME}=?",
            arrayOf(fileName),
            null,
        )
        assertNotNull("MediaStore 必须能按名查到测试文件", verify?.use { c -> if (c.moveToFirst()) c.getLong(0) else null })
        Thread.sleep(1_500)
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

    /** 应用内诊断快照：当前 Compose 层级全部带文本节点（导入页批量状态/失败原因）。 */
    private fun snapshotAppTexts(): String {
        return runCatching {
            val anyText = SemanticsMatcher("hasAnyText") { node ->
                node.config.getOrNull(SemanticsProperties.Text) != null
            }
            val texts = StringBuilder()
            composeRule.onAllNodes(anyText).fetchSemanticsNodes().take(60).forEach { node ->
                val t = node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
                if (!t.isNullOrBlank()) texts.append('[').append(t.take(40)).append(']')
            }
            texts.toString().take(1200).ifEmpty { "no-visible-text" }
        }.getOrDefault("app-snapshot-failed")
    }

    private fun snapshotPickerTexts(): String {
        return runCatching {
            val texts = StringBuilder()
            uiDevice.findObjects(By.clazz("android.widget.TextView")).take(40).forEach {
                val t = it.text
                if (!t.isNullOrBlank()) texts.append('[').append(t.take(30)).append(']')
            }
            (uiDevice.findObjects(By.clazz("android.widget.Button")) +
                uiDevice.findObjects(By.clazz("android.widget.ImageButton"))).take(15).forEach {
                val d = it.contentDescription
                if (!d.isNullOrBlank()) texts.append("(btn)").append('[').append(d.take(30)).append(']')
            }
            // 全部可点击节点：类名+desc+text（工具栏菜单项是 TextView 子类，desc 不在 Button 类里）
            uiDevice.findObjects(By.clickable(true)).take(40).forEach {
                val cls = it.className?.substringAfterLast('.') ?: "?"
                val d = it.contentDescription
                val t = it.text
                if (!d.isNullOrBlank() || !t.isNullOrBlank()) {
                    texts.append('(').append(cls).append(')')
                    if (!d.isNullOrBlank()) texts.append('[').append(d.take(30)).append(']')
                    if (!t.isNullOrBlank()) texts.append('{').append(t.take(20)).append('}')
                }
            }
            "pkg=${uiDevice.currentPackageName} $texts".take(900)
        }.getOrDefault("snapshot-failed")
    }

    /** 驱动树选择器：进入子目录并确认「使用此文件夹」。 */
    private fun pickFolderInSystemTreePicker() {
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
        assertTrue("系统树选择器必须被拉起；快照：${snapshotPickerTexts()}", inPicker)

        // 导航到 Download/<subDir> 并确认。先找子目录项（最近/Download 两级都试），必要时滚动。
        var confirmed = false
        var enteredDir = false
        var lastConfirmState = "confirm-never-found"
        val navDeadline = System.currentTimeMillis() + 45_000
        while (System.currentTimeMillis() < navDeadline && !confirmed) {
            val dirEntry = uiDevice.findObject(By.text(subDir))
            if (dirEntry != null && !enteredDir) {
                dirEntry.click()
                enteredDir = true
                Thread.sleep(1_500)
                continue
            }
            // 确认按钮：AOSP 树模式的确认是图标按钮（对勾/保存），文字在 contentDescription
            // 而非 text——textContains 在此为假阳性源（快照实证）。desc 优先，text 回退。
            val confirm = uiDevice.findObject(By.descContains("使用此文件夹"))
                ?: uiDevice.findObject(By.descContains("USE THIS FOLDER"))
                ?: uiDevice.findObject(By.descContains("Use this folder"))
                ?: uiDevice.findObject(By.descContains("选择此文件夹"))
                ?: uiDevice.findObject(By.textContains("使用此文件夹"))
                ?: uiDevice.findObject(By.textContains("USE THIS FOLDER"))
            if (enteredDir && confirm != null) {
                // 状态诊断：按钮 disabled 是当前最高嫌疑（click() 对 disabled 无效果）
                val b = confirm.visibleBounds
                val state = "enabled=${confirm.isEnabled} clickable=${confirm.isClickable} " +
                    "bounds=$b screen=${uiDevice.displayWidth}x${uiDevice.displayHeight}"
                // 关键：MuMu 上按钮中心可能落在系统导航栏区域（1080x1920，导航栏顶≈1836），
                // 中心点击会被导航栏吃掉——点按钮上部 1/5 处避开遮挡。
                val tapY = b.top + max(1, b.height / 5)
                uiDevice.click(b.centerX, tapY)
                // 保存完整窗口层级供事后取证（app external files，批后 pull）
                runCatching {
                    uiDevice.dumpWindowHierarchy(
                        File(appContext.getExternalFilesDir(null), "cra210saftree/$runId/tree-picker-hierarchy.xml"))
                }
                // 部分定制 DocumentsUI 点确认后还有二次授权弹窗（「允许」/"ALLOW"）；
                // 点完主确认后若未返回应用，逐个点掉可能的二次确认，再判返回。
                val returnDeadline = System.currentTimeMillis() + 8_000
                while (System.currentTimeMillis() < returnDeadline) {
                    if (uiDevice.currentPackageName == appContext.packageName) {
                        confirmed = true
                        break
                    }
                    val allow = uiDevice.findObject(By.textContains("允许"))
                        ?: uiDevice.findObject(By.textContains("ALLOW"))
                        ?: uiDevice.findObject(By.textContains("Allow"))
                        ?: uiDevice.findObject(By.textContains("继续"))
                        ?: uiDevice.findObject(By.textContains("确定"))
                    allow?.click()
                    Thread.sleep(1_000)
                }
                if (confirmed) break
                lastConfirmState = state
            }
            if (!enteredDir) {
                val downloads = uiDevice.findObject(By.text("下载"))
                    ?: uiDevice.findObject(By.text("Downloads"))
                if (downloads != null) {
                    downloads.click()
                    Thread.sleep(1_200)
                    continue
                }
                uiDevice.swipe(540, 1500, 540, 500, 10)
            } else {
                uiDevice.swipe(540, 1500, 540, 500, 10)
            }
            Thread.sleep(1_000)
        }
        assertTrue(
            "树选择器必须选中测试子目录并确认；confirmState=$lastConfirmState 快照：${snapshotPickerTexts()}",
            confirmed,
        )
        val backDeadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < backDeadline) {
            if (uiDevice.currentPackageName == appContext.packageName) break
            Thread.sleep(500)
        }
        assertEquals(
            "确认后必须真正返回应用（confirm 未生效时导入从未开始）",
            appContext.packageName,
            uiDevice.currentPackageName,
        )
    }

    @Test
    fun safTreeJourney_folderPicker_systemDocumentsUI_importScan_tile_reader() {
        stageEpubIntoDownloadSubdir()
        ensureShelf()

        // Phase 1: 书架顶栏「导入书籍」→ 导入页
        composeRule.onNodeWithContentDescription("导入书籍").assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("选择文件夹").fetchSemanticsNodes().isNotEmpty()
        }

        // Phase 2: 「选择文件夹」→ 树选择器 → 进入子目录 → 确认
        composeRule.onAllNodesWithText("选择文件夹")[0].performClick()
        pickFolderInSystemTreePicker()

        // Phase 3: 扫描导入落库（按书名查询）。超时时快照导入页 UI 状态（0 本/失败原因/进行中）。
        val dbReady = runCatching {
            composeRule.waitUntil(timeoutMillis = 60_000) {
                runBlocking { db.bookDao().getAllActive().any { it.title == bookTitle } }
            }
        }.isSuccess
        assertTrue(
            "文件夹扫描导入必须在 60s 内落库；导入页快照：${snapshotAppTexts()}",
            dbReady,
        )
        val book = runBlocking { db.bookDao().getAllActive().first { it.title == bookTitle } }
        assertEquals("SAF 文件夹导入书名必须一致", bookTitle, book.title)
        assertEquals("内容状态必须 available", "available", book.content_status)

        // Phase 4: 回书架断言 tile
        uiDevice.pressBack()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("shelf-screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithTag("book-tile-${book.id}").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("book-tile-${book.id}").assertExists()

        // Phase 5: 打开 reader，第一章渲染
        composeRule.onNodeWithTag("book-tile-${book.id}").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(chapter1Title, substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("文件夹正文段落内容", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(true)
    }
}
