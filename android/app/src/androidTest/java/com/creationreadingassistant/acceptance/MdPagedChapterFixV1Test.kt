package com.creationreadingassistant.acceptance

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.TextStreamLoader
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * CRA-MD-PAGED-CHAPTER-FIX-v1：Markdown 左右翻页模式下「目录选章」的设备夹具与持久断言。
 *
 * 上游失败：final-v3 device-md-r3 —— 分页模式目录选第 2 章后，工具栏显示第 2 章，
 * 正文/底栏仍是第 1 章（r3 原件保留为失败证据）。本类职责与 MdTrueChapterAcceptV1Test
 * 相同：**夹具准备 + 真实持久数据断言**；真实 UI（开书/目录/选章/模式切换）由设备侧
 * 驱动脚本（mdpc_ui.py）经 before_item 完成，禁止直写 DB 制造进度或 PASS。
 *
 * 断言口径（与 r4 同源）：MD 分页进度 `current_location_json` 为 canonical 全书偏移
 * （`ci=0` 为既有 plain 合同，章身份由偏移落在生产章节表的区间证明）。
 * 目标章 index=1（第 2 章），区间 [starts[1], starts[2])。
 */
@RunWith(AndroidJUnit4::class)
class MdPagedChapterFixV1Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun resultsDir(): File =
        File(context.getExternalFilesDir(null), "mdpc-results").also { it.mkdirs() }

    private fun writeResult(phase: String, ok: Boolean, details: Map<String, Any?>) {
        val obj = JSONObject().put("phase", phase).put("pass", ok)
            .put("at", Instant.now().toString())
            .put("pid", Process.myPid())
            .put("packageName", context.packageName)
            .put("applicationClass", context.applicationContext.javaClass.name)
        details.forEach { (k, v) ->
            when (v) {
                null -> obj.put(k, "")
                is JSONArray, is JSONObject -> obj.put(k, v)
                else -> obj.put(k, v.toString())
            }
        }
        File(resultsDir(), "$phase.json").writeText(obj.toString(2))
        runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bmp: Bitmap ->
                File(resultsDir(), "$phase.png").outputStream()
                    .use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            }
        }
        android.util.Log.i(TAG, "$phase pass=$ok $obj")
    }

    private fun require(cond: Boolean, message: String) {
        if (!cond) throw AssertionError(message)
    }

    private fun openDb(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .allowMainThreadQueries().build()

    private fun rawOne(db: AppDatabase, sql: String): String? =
        db.openHelper.writableDatabase.query(sql).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    // ── 夹具（与 mdtc 同源：4 个 H1 章，无前言） ──────────────────────────

    private fun mdFile() = File(context.filesDir, "books/$BOOK_MD/content.md")

    private fun mdBody(): String = buildString {
        (1..4).forEach { ch ->
            appendLine("# 第 $ch 章")
            appendLine()
            (1..10).forEach { i ->
                if (ch == TARGET_CHAPTER_NUMBER && i == MARKER_PARAGRAPH) {
                    appendLine(MARKER)
                } else {
                    appendLine("中性 Markdown 第 $ch 章段落 $i：用于真实跨章定位验收的正文。")
                }
                appendLine()
            }
        }
    }

    private data class ChapterRow(
        val index: Int,
        val title: String,
        val startOffset: Int,
        val charCount: Int,
    )

    private data class ParseEvidence(
        val doc: MarkdownDocument,
        val rows: List<ChapterRow>,
        val path: String,
    )

    /** 生产同源路径：TextStreamLoader.load → MarkdownDocument（ReaderDocumentLoader md 分支两步）。 */
    private fun productionChapterTable(): ParseEvidence {
        val file = mdFile()
        require(file.isFile) { "夹具文件缺失：${file.absolutePath}" }
        val result = TextStreamLoader(context.cacheDir).load(
            context = context,
            uri = Uri.fromFile(file),
            reportedSize = file.length(),
        )
        val doc = if (result.isStreaming) {
            MarkdownDocument.fromFileIndex(result.sourceFile!!, result.fileIndex!!)
        } else {
            MarkdownDocument(result.fullText ?: "")
        }
        val rows = doc.chapters.map {
            ChapterRow(it.index, it.title, it.startOffset, it.charCount)
        }
        return ParseEvidence(
            doc = doc,
            rows = rows,
            path = if (result.isStreaming) "streamingFileIndex" else "wholeDocumentText",
        )
    }

    private fun chapterIndexForOffset(rows: List<ChapterRow>, offset: Int): Int =
        rows.indexOfLast { it.startOffset <= offset }.coerceAtLeast(0)

    private fun chaptersJson(rows: List<ChapterRow>): JSONArray = JSONArray().also { arr ->
        rows.forEach { r ->
            arr.put(
                JSONObject()
                    .put("index", r.index)
                    .put("title", r.title)
                    .put("startOffset", r.startOffset)
                    .put("charCount", r.charCount)
            )
        }
    }

    private fun chapterRange(rows: List<ChapterRow>, index: Int): Pair<Int, Int> {
        val start = rows[index].startOffset
        val end = if (index + 1 < rows.size) rows[index + 1].startOffset else start + rows[index].charCount
        return start to end
    }

    /** 通用断言：当前持久进度必须落在指定章区间。expectedChapterTitle 只用于记录。 */
    private fun assertProgressInChapter(phase: String, targetCi: Int) {
        val pe = productionChapterTable()
        val db = openDb()
        try {
            require(pe.rows.size >= 3) { "生产解析路径必须给出 >=3 章，实得 ${pe.rows.size}" }
            val progress = rawOne(
                db,
                "SELECT COALESCE(current_location_json,'') FROM reading_progress WHERE book_id='$BOOK_MD'",
            )
            require(!progress.isNullOrBlank()) {
                "缺少阅读进度行：真实 UI 操作后需先 ON_PAUSE/离开落盘再断言"
            }
            val off = Regex("\"offset\":(\\d+)").find(progress!!)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            val space = Regex("\"space\":\"([^\"]+)\"").find(progress)?.groupValues?.get(1)
            val cur = chapterIndexForOffset(pe.rows, off)
            val (rangeStart, rangeEnd) = chapterRange(pe.rows, targetCi)
            require(off in rangeStart until rangeEnd && cur == targetCi) {
                "持久进度必须落在第 ${targetCi + 1} 章区间 [$rangeStart, $rangeEnd)，" +
                    "实得 offset=$off（章 index=$cur / ${pe.rows.getOrNull(cur)?.title}）"
            }
            writeResult(
                phase, true,
                mapOf(
                    "progressJson" to progress,
                    "readerOffset" to off,
                    "space" to (space ?: "<none>"),
                    "currentChapterIndex" to cur,
                    "currentChapterTitle" to pe.rows[cur].title,
                    "expectedChapterIndex" to targetCi,
                    "expectedChapterTitle" to pe.rows[targetCi].title,
                    "chapterRange" to "$rangeStart..$rangeEnd",
                    "chapterCount" to pe.rows.size,
                    "chapters" to chaptersJson(pe.rows),
                ),
            )
        } finally {
            db.close(); pe.doc.close()
        }
    }

    @Test
    fun mdpc_00_seedFixture() = runBlocking {
        val db = openDb()
        try {
            cleanRows(db)
            mdFile().apply { parentFile!!.mkdirs(); writeText(mdBody()) }
            val now = Instant.now().toString()
            db.bookDao().upsert(
                BookEntity(
                    id = BOOK_MD,
                    title = "测试 Markdown 分页跨章",
                    author = "中性作者",
                    format = "md",
                    size = mdFile().length().toInt(),
                    content_status = "available",
                    imported_at = now,
                    revision = 1,
                    updated_at = now,
                    local_content_path = mdFile().absolutePath,
                )
            )
            writeResult(
                "mdpc_00_seedFixture", true,
                mapOf(
                    "fixtureOnly" to true,
                    "mdSha" to sha(mdFile()),
                    "h1ChapterCount" to 4,
                    "targetChapterNumber" to TARGET_CHAPTER_NUMBER,
                    "note" to "夹具准备不算产品通过",
                )
            )
        } finally { db.close() }
    }

    @Test
    fun mdpc_01_proveProductionChapters() = runBlocking {
        val pe = productionChapterTable()
        try {
            require(pe.rows.size >= 3) { "生产解析路径必须给出 >=3 章，实得 ${pe.rows.size}" }
            val markerChapter = pe.rows.firstOrNull { pe.doc.text(it.index).contains(MARKER) }
                ?: throw AssertionError("标记句未落在任何章节内")
            require(markerChapter.index == TARGET_CI) {
                "标记句必须落在目标章 index=$TARGET_CI，实得 ${markerChapter.index}"
            }
            val local = pe.doc.text(markerChapter.index).indexOf(MARKER)
            require(local > 0) { "标记句在目标章内必须为非零位置，实得 $local" }
            writeResult(
                "mdpc_01_proveProductionChapters", true,
                mapOf(
                    "parsePath" to pe.path,
                    "chapterCount" to pe.rows.size,
                    "chapters" to chaptersJson(pe.rows),
                    "targetChapterIndex" to markerChapter.index,
                    "targetChapterStartOffset" to markerChapter.startOffset,
                    "totalChars" to pe.doc.totalChars,
                ),
            )
        } finally { pe.doc.close() }
    }

    // ── B：分页模式 真实目录选第 2 章后，持久进度必须落在第 2 章区间 ──
    @Test
    fun mdpc_10_assertProgressAtTargetChapter() = runBlocking {
        assertProgressInChapter("mdpc_10_assertProgressAtTargetChapter", TARGET_CI)
    }

    // ── C：第 2 章 → 第 3 章 → 第 2 章（含等待不被旧请求拉回） ──
    @Test
    fun mdpc_20_assertProgressAtChapter3() = runBlocking {
        assertProgressInChapter("mdpc_20_assertProgressAtChapter3", 2)
    }

    @Test
    fun mdpc_21_assertProgressBackAtChapter2() = runBlocking {
        assertProgressInChapter("mdpc_21_assertProgressBackAtChapter2", TARGET_CI)
    }

    // ── D：强停冷启动后恢复到实际保存的章节 ──
    @Test
    fun mdpc_30_assertColdStartProgress() = runBlocking {
        assertProgressInChapter("mdpc_30_assertColdStartProgress", TARGET_CI)
    }

    // ── E：MD 滚动模式语义回归（真实 UI 切滚动后目录选章） ──
    @Test
    fun mdpc_40_assertScrollTocJump() = runBlocking {
        assertProgressInChapter("mdpc_40_assertScrollTocJump", TARGET_CI)
    }

    @Test
    fun mdpc_90_recordFinalState() = runBlocking {
        val db = openDb()
        try {
            val summary = StringBuilder()
                .append("book=").append(BOOK_MD)
                .append(" status=").append(rawOne(db, "SELECT COALESCE(content_status,'') FROM books WHERE id='$BOOK_MD'"))
                .append(" active=").append(rawOne(db, "SELECT COUNT(*) FROM books WHERE id='$BOOK_MD' AND deleted_at IS NULL"))
                .append(" progress=").append(rawOne(db, "SELECT COALESCE(current_location_json,'') FROM reading_progress WHERE book_id='$BOOK_MD'"))
                .toString()
            writeResult("mdpc_90_recordFinalState", true, mapOf("summary" to summary))
        } finally { db.close() }
    }

    // ── helpers（与 mdtc 同源） ──────────────────────────────────────────

    private fun sha(f: File): String {
        if (!f.isFile) return "MISSING"
        val d = java.security.MessageDigest.getInstance("SHA-256")
        return d.digest(f.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private fun cleanRows(db: AppDatabase) {
        val wdb = db.openHelper.writableDatabase
        listOf(
            "DELETE FROM highlights WHERE book_id LIKE 'mdpc-%'",
            "DELETE FROM notes WHERE book_id LIKE 'mdpc-%' OR id LIKE 'mdpc-%'",
            "DELETE FROM reading_sessions WHERE book_id LIKE 'mdpc-%'",
            "DELETE FROM book_files WHERE book_id LIKE 'mdpc-%'",
            "DELETE FROM book_content WHERE book_id LIKE 'mdpc-%'",
            "DELETE FROM reading_progress WHERE book_id LIKE 'mdpc-%'",
            "DELETE FROM books WHERE id LIKE 'mdpc-%'",
        ).forEach { wdb.execSQL(it) }
    }

    private companion object {
        const val TAG = "MdPagedChapterFix"

        /** 夹具书 id：mdpc- 前缀，便于本批夹具清理。 */
        const val BOOK_MD = "mdpc-md-paged-chapter"

        /** 目标章在生产章节表中的索引（第 2 章 → 1，>0）。 */
        const val TARGET_CI = 1

        /** 目标章在夹具中的 H1 序号（`# 第 2 章`）。 */
        const val TARGET_CHAPTER_NUMBER = 2

        /** 标记句所在段号。 */
        const val MARKER_PARAGRAPH = 7

        /** 中性标记句（诊断期不用于选区，仅证明章节表）。 */
        const val MARKER = "中性测试标记句跨章甲甲甲"
    }
}
