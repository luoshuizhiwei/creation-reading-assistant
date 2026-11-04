package com.creationreadingassistant.acceptance

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.ui.screen.reader.sheets.NotesSheet
import com.creationreadingassistant.ui.theme.paperPalette
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * CRA-V0-JOINT-FREEZE-MUMU-ACCEPT-v1 —— 任务24：书内笔记**真实 NotesSheet 入口**的
 * 导出（SAF 实际写出）与分享（系统选择器内容）设备证据。
 *
 * 与 23-v2 的 JVM 纯函数单测互补：这里渲染**真实 [NotesSheet]** 组合，
 * 点击真实「导出」/「分享」胶囊，经生产 ActivityResult 通道与 `context.startActivity`
 * 观测真实写出字节 / chooser 意图内容。
 *
 * **证据范围（不得夸大）**：
 *  · 导出返回的是**受控结果** `file://` 缓存 URI（[RecordingRegistry] 直接回填结果），
 *    验证的是 NotesSheet 生产写字节逻辑与导出内容；**不代表系统 SAF（CreateDocument）
 *    文件选择器 UI 已通过**——真实系统 SAF 需在设备上单独执行。
 *  · 分享只捕获 `ACTION_CHOOSER` 意图（[RecordingContext] 拦截 startActivity），
 *    验证的是生产组装的分享内容；**不代表系统 chooser UI 已展示/通过**。
 *
 *  · 导出：拦截 `ActivityResultContracts.CreateDocument` 返回受控 file:// uri，
 *    待 NotesSheet 真实写出后从磁盘读回字节，与预期来源/摘录/定位逐项比对；
 *  · 损坏 payload：断言不进入 SAF 选择器（onLaunch 未被调用）、不产生半份文件；
 *  · 删书快照：真实隔离 Room + 既有删除路径，核 payload 快照保持后导出字节仍在；
 *  · 分享：仅捕获 chooser 意图核 EXTRA_TEXT 内容后取消（不选择接收应用、不发消息）。
 */
@RunWith(AndroidJUnit4::class)
class NotesExportShareAcceptTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    private companion object {
        const val BOOK_TITLE = "测试 EPUB"
        const val TASK_ID = "CRA-V0-JOINT-FREEZE-MUMU-ACCEPT-v1"
    }

    // ── 受控 SAF 结果注册表 ──────────────────────────────────────────────────────

    private class RecordingRegistry : ActivityResultRegistry() {
        var launchCount = 0
        var lastSuggestedName: String? = null
        var lastMime: String? = null
        var uriToReturn: Uri? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launchCount++
            lastSuggestedName = input as? String
            lastMime = if (contract is ActivityResultContracts.CreateDocument) "text/markdown" else null
            @Suppress("UNCHECKED_CAST")
            dispatchResult(requestCode, uriToReturn as O)
        }
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var capturedIntent: Intent? = null
        override fun startActivity(intent: Intent) {
            capturedIntent = intent
        }
    }

    private fun highlight(text: String, note: String? = null) = HighlightEntity(
        id = "h_" + text.hashCode(), book_id = "b24", text = text, note = note,
        chapter_title = "第一章", created_at = "2026-10-12T00:00:00Z", updated_at = "2026-10-12T00:00:00Z",
    )

    private fun note(title: String, body: String, excerpt: String? = null) = NoteEntity(
        id = "n_" + title.hashCode(), book_id = "b24", title = title, body = body, excerpt = excerpt,
        created_at = "2026-10-12T00:00:00Z", updated_at = "2026-10-12T00:00:00Z",
    )

    private fun inspiration(id: String, title: String, body: String, payload: String?) = InspirationEntity(
        id = id, title = title, body = body, payload = payload,
        created_at = "2026-10-12T00:00:00Z", updated_at = "2026-10-12T00:00:00Z",
    )

    private val json = Json { encodeDefaults = true; explicitNulls = false }

    /** 单来源 payload（含完整快照 + locator）。 */
    private fun singleSourcePayload(excerpt: String): String = json.encodeToString(
        com.creationreadingassistant.ui.viewmodel.InspirationPayloadData.serializer(),
        com.creationreadingassistant.ui.viewmodel.InspirationPayloadData(
            source = com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo(
                bookId = "b24", bookTitle = "测试 EPUB", bookAuthor = "测试作者",
                chapterTitle = "第一章", locationLabel = "第 42 字附近",
                progressPercent = 12.5f, excerpt = excerpt, locatorJson = "{\"v\":2,\"offset\":42}",
            ),
        ),
    )

    /** 多来源 payload：source 缺书名快照 + excerpts 两条（其中一条多行摘录）。 */
    private fun multiSourceMissingBookPayload(): String = json.encodeToString(
        com.creationreadingassistant.ui.viewmodel.InspirationPayloadData.serializer(),
        com.creationreadingassistant.ui.viewmodel.InspirationPayloadData(
            source = com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo(
                bookTitle = null, chapterTitle = "无书名来源章", excerpt = "无书名来源摘录",
            ),
            excerpts = listOf(
                com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo(
                    bookTitle = "第二来源书", bookAuthor = "乙", chapterTitle = "第二章",
                    locationLabel = "第 100 字附近", progressPercent = 33.3f,
                    excerpt = "多行摘录第一行\n\n多行摘录第三行 > 带引用符",
                    locatorJson = "{\"v\":2,\"offset\":100}",
                ),
            ),
        ),
    )

    private fun setNotesContent(
        registry: RecordingRegistry,
        ctx: Context,
        highlights: List<HighlightEntity>,
        notes: List<NoteEntity>,
        inspirations: List<InspirationEntity>,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides ctx,
                LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                    override val activityResultRegistry = registry
                },
            ) {
                MaterialTheme {
                    NotesSheet(
                        paper = paperPalette("white", false),
                        bookTitle = BOOK_TITLE,
                        highlights = highlights,
                        notes = notes,
                        inspirations = inspirations,
                        onAddBookmark = {}, onDeleteHighlight = {}, onDeleteNote = {},
                        onChangeHighlightColor = { _, _ -> }, onEditHighlightNote = { _, _ -> },
                        onHighlightToNote = {}, onHighlightToInspiration = {},
                        onJumpToHighlight = {}, onJumpToNote = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. 导出：SAF 实际写出后读回字节（单来源，含定位）
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun notesExport_safWritesRealBytes_singleSource() {
        val target = File(appContext.cacheDir, "task24-export-single.md")
        target.delete()
        val registry = RecordingRegistry().apply { uriToReturn = Uri.fromFile(target) }
        val inspirations = listOf(inspiration("i1", "灵感甲", "个人正文甲", singleSourcePayload("单来源摘录")))
        setNotesContent(registry, appContext, listOf(highlight("书摘一", "批注一")), listOf(note("笔记一", "正文一")), inspirations)

        composeRule.onNodeWithContentDescription("导出").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) { target.exists() && target.length() > 0 }

        val bytes = target.readBytes()
        val text = bytes.toString(Charsets.UTF_8)
        val payload = JSONObject().apply {
            put("launchCount", registry.launchCount)
            put("suggestedName", registry.lastSuggestedName)
            put("mime", registry.lastMime)
            put("bytes", bytes.size)
            put("sha256", sha256(bytes))
            put("content", text)
        }
        writeEvidence("notesExport_safWritesRealBytes_singleSource", payload)

        assertEquals("必须恰好打开一次 SAF 写出通道", 1, registry.launchCount)
        assertNotNull("SAF 建议文件名必须非空", registry.lastSuggestedName)
        assertTrue("建议文件名必须是 markdown", registry.lastSuggestedName!!.endsWith(".md"))
        assertTrue("写出内容必须含书名标题", text.contains("# 《$BOOK_TITLE》阅读笔记"))
        assertTrue("写出内容必须含书摘", text.contains("书摘一"))
        assertTrue("写出内容必须含灵感来源书名快照", text.contains("《测试 EPUB》"))
        assertTrue("写出内容必须含来源定位 JSON", text.contains("{\"v\":2,\"offset\":42}"))
        assertTrue("写出内容必须含单来源摘录", text.contains("单来源摘录"))
        assertFalse("不得产生半份文件", text.isBlank())
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3b. 导出：多来源 + 缺原书快照 + 多行摘录
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun notesExport_safWritesRealBytes_multiSourceMissingBookAndMultilineExcerpt() {
        val target = File(appContext.cacheDir, "task24-export-multi.md")
        target.delete()
        val registry = RecordingRegistry().apply { uriToReturn = Uri.fromFile(target) }
        val inspirations = listOf(
            inspiration("i2", "灵感乙", "个人正文乙", multiSourceMissingBookPayload()),
        )
        setNotesContent(registry, appContext, emptyList(), emptyList(), inspirations)

        composeRule.onNodeWithContentDescription("导出").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) { target.exists() && target.length() > 0 }

        val bytes = target.readBytes()
        val text = bytes.toString(Charsets.UTF_8)
        writeEvidence("notesExport_safWritesRealBytes_multiSourceMissingBookAndMultilineExcerpt", JSONObject().apply {
            put("launchCount", registry.launchCount)
            put("bytes", bytes.size)
            put("sha256", sha256(bytes))
            put("content", text)
        })

        assertEquals("必须恰好打开一次 SAF 写出通道", 1, registry.launchCount)
        assertTrue("缺书名快照必须显式标注「未记录书名」", text.contains("未记录书名"))
        assertTrue("必须含第二来源书名", text.contains("《第二来源书》"))
        assertTrue("多行摘录第二行必须仍在引用区", text.contains("> 多行摘录第三行 > 带引用符"))
        assertTrue("多行摘录空行必须保留引用前缀", text.contains(">\n"))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3c. 导出：损坏 payload 明确失败，不打开写出、不产生半份文件
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun notesExport_corruptPayloadFailsWithoutOpeningSaf() {
        val target = File(appContext.cacheDir, "task24-export-corrupt.md")
        target.delete()
        val registry = RecordingRegistry().apply { uriToReturn = Uri.fromFile(target) }
        val inspirations = listOf(inspiration("i3", "损坏灵感", "正文", "{not-valid-json"))
        setNotesContent(registry, appContext, emptyList(), emptyList(), inspirations)

        composeRule.onNodeWithContentDescription("导出").performClick()
        composeRule.waitForIdle()
        SystemClockSleep(1200)

        writeEvidence("notesExport_corruptPayloadFailsWithoutOpeningSaf", JSONObject().apply {
            put("launchCount", registry.launchCount)
            put("targetExists", target.exists())
            put("targetBytes", if (target.exists()) target.length() else 0)
        })

        assertEquals("损坏 payload 必须阻止进入 SAF 选择器", 0, registry.launchCount)
        assertFalse("损坏 payload 不得产生半份导出文件", target.exists())
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3d. 导出：删原书后快照保留（真实隔离 Room 夹具 + 既有删除路径）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 用真实隔离 Room 库建「真书 + source_book_id 非空的灵感」，经**既有删除路径**
     * （[BookRepository.deleteBook]）删原书，核灵感 payload 快照逐字节保持，再经真实
     * NotesSheet 导出、读回字节核快照（书名/摘录/定位）仍在。
     *
     * 说明：`source_book_id` 的实际删后取值如实登记在证据里（既有删除是软删除，
     * 本用例不预设其被置空或保持，只如实记录并断言快照不被清空）。
     */
    @Test
    fun notesExport_bookDeletedSnapshotPreserved() {
        val bookId = "t24-export-book-" + UUID.randomUUID().toString().take(8)
        val inspId = "t24-export-insp-" + UUID.randomUUID().toString().take(8)
        val db = Room.databaseBuilder(appContext, AppDatabase::class.java, "task24-export-fixture.db")
            .allowMainThreadQueries().build()
        val target = File(appContext.cacheDir, "task24-export-deleted-book.md")
        target.delete()
        val registry = RecordingRegistry().apply { uriToReturn = Uri.fromFile(target) }
        try {
            val now = Instant.now().toString()
            val payload = json.encodeToString(
                com.creationreadingassistant.ui.viewmodel.InspirationPayloadData.serializer(),
                com.creationreadingassistant.ui.viewmodel.InspirationPayloadData(
                    source = com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo(
                        bookId = bookId, bookTitle = "测试 EPUB", bookAuthor = "测试作者",
                        chapterTitle = "第一章", locationLabel = "第 42 字附近",
                        progressPercent = 12.5f, excerpt = "删书前摘录快照", locatorJson = "{\"v\":2,\"offset\":42}",
                    ),
                ),
            )
            runBlocking {
                db.bookDao().upsert(BookEntity(id = bookId, title = "测试 EPUB", format = "epub", updated_at = now))
                db.inspirationDao().upsert(
                    InspirationEntity(
                        id = inspId, title = "灵感快照", body = "个人正文", source_book_id = bookId,
                        payload = payload, created_at = now, updated_at = now,
                    ),
                )
                val repo = BookRepository(
                    bookDao = db.bookDao(), bookContentDao = db.bookContentDao(), bookFileDao = db.bookFileDao(),
                    progressDao = db.readingProgressDao(), sessionDao = db.readingSessionDao(), noteDao = db.noteDao(),
                    highlightDao = db.highlightDao(), inspirationDao = db.inspirationDao(),
                    bookTagDao = db.bookTagDao(), bookCategoryDao = db.bookCategoryDao(), shelfBookDao = db.shelfBookDao(),
                    chapterReadDao = db.chapterReadDao(), context = appContext,
                )
                repo.deleteBook(bookId)
            }

            val activeBook = runBlocking { db.bookDao().getById(bookId) }
            val after = runBlocking { db.inspirationDao().getById(inspId) }
            assertNull("删书后原书必须不再活跃", activeBook)
            assertNotNull("灵感必须仍存在", after)
            assertEquals("删书后灵感 payload 快照必须逐字节保持", payload, after!!.payload)

            setNotesContent(registry, appContext, emptyList(), emptyList(), listOf(after))
            composeRule.onNodeWithContentDescription("导出").performClick()
            composeRule.waitForIdle()
            composeRule.waitUntil(10_000) { target.exists() && target.length() > 0 }
            val text = target.readBytes().toString(Charsets.UTF_8)

            writeEvidence("notesExport_bookDeletedSnapshotPreserved", JSONObject().apply {
                put("bookId", bookId)
                put("sourceBookIdAfterDelete", after.source_book_id ?: JSONObject.NULL)
                put("payloadPreserved", payload == after.payload)
                put("launchCount", registry.launchCount)
                put("bytes", target.length())
                put("content", text)
            })

            assertEquals("必须恰好打开一次 SAF 写出通道", 1, registry.launchCount)
            assertTrue("删书后导出仍必须含快照书名", text.contains("《测试 EPUB》"))
            assertTrue("删书后导出仍必须含快照摘录", text.contains("删书前摘录快照"))
            assertTrue("删书后导出仍必须含快照定位", text.contains("{\"v\":2,\"offset\":42}"))
        } finally {
            db.close()
            File(appContext.getDatabasePath("task24-export-fixture.db").absolutePath).delete()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. 分享：仅打开选择器核内容后取消（不选择接收应用、不发消息）
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun notesShare_chooserContentThenCancel() {
        val ctx = RecordingContext(appContext)
        val registry = RecordingRegistry()
        val inspirations = listOf(inspiration("i4", "灵感丁", "分享正文", singleSourcePayload("分享摘录")))
        setNotesContent(registry, ctx, listOf(highlight("分享书摘")), emptyList(), inspirations)

        composeRule.onNodeWithContentDescription("分享").performClick()
        composeRule.waitForIdle()

        val chooser = ctx.capturedIntent
        val inner = chooser?.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
        val sharedText = inner?.getStringExtra(Intent.EXTRA_TEXT)
        writeEvidence("notesShare_chooserContentThenCancel", JSONObject().apply {
            put("chooserAction", chooser?.action)
            put("chooserTitle", chooser?.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString())
            put("innerAction", inner?.action)
            put("innerType", inner?.type)
            put("sharedTextPresent", sharedText != null)
            put("sharedTextLength", sharedText?.length ?: -1)
            put("sharedText", sharedText ?: JSONObject.NULL)
            put("note", "意图捕获证据：仅核 chooser 内容后取消，未选择接收应用、未发消息；非真实选择器 UI")
        })

        assertNotNull("必须打开系统分享选择器", chooser)
        assertEquals("必须是 ACTION_CHOOSER", Intent.ACTION_CHOOSER, chooser!!.action)
        assertNotNull("chooser 必须携带原始 SEND 意图", inner)
        assertEquals("原始意图必须是 ACTION_SEND", Intent.ACTION_SEND, inner!!.action)
        assertNotNull("分享内容必须非空", sharedText)
        assertTrue("分享内容必须含书名", sharedText!!.contains("《$BOOK_TITLE》"))
        assertTrue("分享内容必须含书摘", sharedText.contains("分享书摘"))
        assertNull("取消后不得留下任何接收侧痕迹（本测试未选择应用）", ctx.capturedIntent?.getStringExtra("android.intent.extra.CHOSEN_COMPONENT"))
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun SystemClockSleep(ms: Long) = android.os.SystemClock.sleep(ms)

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun writeEvidence(step: String, payload: JSONObject) {
        val dir = File(appContext.getExternalFilesDir(null), "j1_guard_evidence").apply { mkdirs() }
        val runId = "run_" + android.os.Process.myPid() + "_" + (System.currentTimeMillis() % 100000)
        val file = File(dir, "${runId}__${step}.json")
        val root = JSONObject().apply {
            put("task", TASK_ID)
            put("runId", runId)
            put("step", step)
            put("pid", android.os.Process.myPid())
            put("packageName", appContext.packageName)
            put("timestamp", Instant.now().toString())
            put("timestampMillis", System.currentTimeMillis())
            put("payload", payload)
        }
        file.writeText(root.toString(2))
        println("J1GUARD_EVIDENCE=${file.name}")
        println("J1GUARD_PRODUCER=$runId")
    }
}
