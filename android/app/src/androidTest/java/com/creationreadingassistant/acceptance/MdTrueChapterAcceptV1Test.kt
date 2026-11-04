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
 * CRA-MD-TRUE-CHAPTER-ACCEPT-v1：**Markdown 真实跨阅读器章节**的设备夹具与断言驱动。
 *
 * ## 为什么需要本批（上一批为什么不成立）
 *
 * `integration-build-accept-v2` 的 MD 多章夹具只有一个 H1（书名）与四个 H2，而
 * [MarkdownDocument] 的生产规则**只按 H1 拆阅读器章节**（`buildChaptersFromText` /
 * `scanSourceChapters` 均只认 `level == 1`），H2–H6 只是章内渲染块。因此那本书实际只有
 * **1 章**，locator 恒 `ci=0`，回源只是同一章内长文滚动 —— 跨章 NOT PROVEN。
 *
 * 本批按同一生产规则构造 **多个 H1「# 第 n 章」**（4 章），目标章为 index 1（第 2 章），
 * 标记句位于目标章内**非零位置**，再用真实 UI 保存高亮、冷启动、切到别的章、从书内笔记
 * 回源。
 *
 * ## 产品已知行为（必须如实记录，不得算作通过、不得改产品）
 *
 * `LocatorBuilder.forPlain` 对 TXT/Markdown **恒写 `ci = 0`**（该函数注释即产品契约）。
 * 因此 MD 高亮/进度的 `locator_json` 里**看不到真实章号**；真实章身份由
 * ①`chapter_title` 字段 ②canonical 偏移落在生产章节表的哪一段 共同确定。
 * 本批据此取证，并对 `ci=0` 原样记录。
 *
 * ## 职责划分（沿用既有批次结论）
 *
 * Compose 仪器环境无法让本阅读器 readOnly 文本产生选区，故**选区/保存/查看/回源由设备侧
 * uiautomator 驱动**（本目录 `ui.py`）；本类只做**夹具准备**与**真实持久数据断言**，
 * 禁止直写 DB 制造验收 PASS。
 */
@RunWith(AndroidJUnit4::class)
class MdTrueChapterAcceptV1Test {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun resultsDir(): File =
        File(context.getExternalFilesDir(null), "mdtc-results").also { it.mkdirs() }

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

    // ── 夹具 ──────────────────────────────────────────────────────────────

    private fun mdFile() = File(context.filesDir, "books/$BOOK_MD/content.md")

    /**
     * 中性夹具：**4 个 H1 章节**（`# 第 n 章`），无前言（避免产生「开篇」章）。
     * 每章 10 段，标记句位于**第 2 章第 7 段** —— 目标章 index=1（>0），
     * 且章内偏移非零（前面有标题块与 6 个段落）。
     */
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

    // ── 生产解析路径（与 ReaderDocumentLoader 的 md 分支同源） ─────────────

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

    /**
     * 用**生产同源路径**构建章节表：`TextStreamLoader.load` → `MarkdownDocument`
     * （`ReaderDocumentLoader.load` 的 "md"/"markdown" 分支逐字相同的两步）。
     */
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

    @Test
    fun mdtc_00_seedFixture() = runBlocking {
        val db = openDb()
        try {
            cleanRows(db)
            mdFile().apply { parentFile!!.mkdirs(); writeText(mdBody()) }
            val now = Instant.now().toString()
            db.bookDao().upsert(
                BookEntity(
                    id = BOOK_MD,
                    title = "测试 Markdown 真跨章",
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
                "mdtc_00_seedFixture", true,
                mapOf(
                    "fixtureOnly" to true,
                    "mdSha" to sha(mdFile()),
                    "h1ChapterCount" to 4,
                    "targetChapterNumber" to TARGET_CHAPTER_NUMBER,
                    "markerParagraph" to "$MARKER_PARAGRAPH/10",
                    "note" to "夹具准备不算产品通过",
                )
            )
        } finally { db.close() }
    }

    /**
     * 合同 2：**用生产解析路径证明 `document.chapters` 至少 3 章**，并记录
     * 章节索引、标题、startOffset；同时证明标记句落在 index>0 的章节内的非零位置。
     * 不看正文标题、不看书名。
     */
    @Test
    fun mdtc_01_proveProductionChapters() = runBlocking {
        val pe = productionChapterTable()
        try {
            require(pe.rows.size >= 3) { "生产解析路径必须给出 >=3 章，实得 ${pe.rows.size}" }
            val markerChapter = pe.rows.firstOrNull { pe.doc.text(it.index).contains(MARKER) }
                ?: throw AssertionError("标记句未落在任何章节内")
            require(markerChapter.index == TARGET_CI) {
                "标记句必须落在目标章 index=$TARGET_CI，实得 ${markerChapter.index}"
            }
            require(markerChapter.index > 0) { "目标章索引必须 > 0，实得 ${markerChapter.index}" }
            val local = pe.doc.text(markerChapter.index).indexOf(MARKER)
            require(local > 0) { "标记句在目标章内必须为非零位置，实得章内偏移 $local" }
            writeResult(
                "mdtc_01_proveProductionChapters", true,
                mapOf(
                    "parsePath" to pe.path,
                    "chapterCount" to pe.rows.size,
                    "chapters" to chaptersJson(pe.rows),
                    "targetChapterIndex" to markerChapter.index,
                    "targetChapterTitle" to markerChapter.title,
                    "targetChapterStartOffset" to markerChapter.startOffset,
                    "targetChapterCharCount" to markerChapter.charCount,
                    "markerOffsetInChapter" to local,
                    "markerCanonicalOffset" to (markerChapter.startOffset + local),
                    "totalChars" to pe.doc.totalChars,
                    "note" to "章节表与 ReaderDocumentLoader 的 md 分支同源（TextStreamLoader + MarkdownDocument）",
                )
            )
        } finally { pe.doc.close() }
    }

    // =====================================================================
    // 3：真实 UI 保存的高亮必须落在目标章（index>0）
    // =====================================================================

    @Test
    fun mdtc_10_assertTargetHighlightSaved() = runBlocking {
        val pe = productionChapterTable()
        val db = openDb()
        try {
            val target = pe.rows.first { it.index == TARGET_CI }
            val id = rawOne(
                db,
                "SELECT id FROM highlights WHERE book_id='$BOOK_MD' AND deleted_at IS NULL " +
                    "ORDER BY created_at DESC LIMIT 1"
            )
            require(id != null) { "未找到经真实 UI 保存的活跃高亮" }
            val excerpt = rawOne(db, "SELECT text FROM highlights WHERE id='$id'")
            val locator = rawOne(db, "SELECT COALESCE(locator_json,'') FROM highlights WHERE id='$id'")
            val chapterTitle = rawOne(db, "SELECT COALESCE(chapter_title,'') FROM highlights WHERE id='$id'")
            require(excerpt != null && excerpt.contains(MARKER)) {
                "摘录必须含标记句「$MARKER」，实得：$excerpt"
            }
            require(!locator.isNullOrBlank()) { "必须有 locator_json" }
            val off = Regex("\"offset\":(\\d+)").find(locator!!)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            val ci = Regex("\"ci\":(\\d+)").find(locator)?.groupValues?.get(1)?.toIntOrNull()
            val rangeEnd = target.startOffset + target.charCount
            require(off >= target.startOffset && off < rangeEnd) {
                "高亮 canonical 偏移必须落在目标章区间 [${
                    target.startOffset
                }, $rangeEnd)，实得 $off"
            }
            require(chapterTitle == target.title) {
                "高亮章节名必须是目标章「${target.title}」，实得「$chapterTitle」"
            }
            writeResult(
                "mdtc_10_assertTargetHighlightSaved", true,
                mapOf(
                    "highlightId" to id,
                    "excerpt" to excerpt,
                    "locatorJson" to locator,
                    "offset" to off,
                    "locatorCi" to (ci ?: "<absent>"),
                    "chapterTitle" to chapterTitle,
                    "targetChapterIndex" to target.index,
                    "targetChapterTitle" to target.title,
                    "targetRange" to "[${target.startOffset},$rangeEnd)",
                    "realUiSave" to true,
                    "productNote" to "LocatorBuilder.forPlain 对 MD/TXT 恒写 ci=0；真实章身份 = chapter_title + canonical 区间归属",
                )
            )
        } finally { db.close(); pe.doc.close() }
    }

    /** 合同 4：强停后冷启动（新进程）读回同值定位。 */
    @Test
    fun mdtc_11_coldReadTargetHighlight() = runBlocking {
        val pe = productionChapterTable()
        val db = openDb()
        try {
            val target = pe.rows.first { it.index == TARGET_CI }
            val row = rawOne(
                db,
                "SELECT text || '|' || COALESCE(locator_json,'') || '|' || COALESCE(chapter_title,'') " +
                    "|| '|' || COALESCE(deleted_at,'') FROM highlights WHERE book_id='$BOOK_MD' " +
                    "AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 1"
            )
            require(row != null) { "冷启动后高亮必须仍在" }
            val p = row.split("|")
            require(p[0].contains(MARKER)) { "冷读摘录必须含标记句：${p[0]}" }
            require(p[1].isNotBlank()) { "冷读 locator 必须仍在" }
            require(p[3].isBlank()) { "高亮必须仍活跃" }
            val off = Regex("\"offset\":(\\d+)").find(p[1])?.groupValues?.get(1)?.toIntOrNull() ?: -1
            require(off >= target.startOffset && off < target.startOffset + target.charCount) {
                "冷读偏移必须仍落在目标章区间，实得 $off"
            }
            require(p[2] == target.title) { "冷读章节名必须是「${target.title}」，实得「${p[2]}」" }
            writeResult(
                "mdtc_11_coldReadTargetHighlight", true,
                mapOf(
                    "excerpt" to p[0],
                    "locatorJson" to p[1],
                    "chapterTitle" to p[2],
                    "offset" to off,
                    "targetChapterIndex" to target.index,
                    "coldStartNewProcess" to true,
                    "pid" to Process.myPid(),
                )
            )
        } finally { db.close(); pe.doc.close() }
    }

    // =====================================================================
    // 5：真实切到另一个阅读章节 —— 当前章必须不同于目标章
    // =====================================================================

    @Test
    fun mdtc_20_assertReaderMovedOffTargetChapter() = runBlocking {
        val pe = productionChapterTable()
        val db = openDb()
        try {
            val progress = rawOne(
                db,
                "SELECT COALESCE(current_location_json,'') FROM reading_progress WHERE book_id='$BOOK_MD'"
            )
            require(!progress.isNullOrBlank()) {
                "缺少阅读进度行：真实切章后需先 ON_PAUSE 落盘再断言"
            }
            val off = Regex("\"offset\":(\\d+)").find(progress!!)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            val space = Regex("\"space\":\"([^\"]+)\"").find(progress)?.groupValues?.get(1)
            val cur = chapterIndexForOffset(pe.rows, off)
            val curRow = pe.rows[cur]
            require(cur != TARGET_CI) {
                "当前章必须已离开目标章 index=$TARGET_CI，实得 index=$cur（offset=$off）"
            }
            writeResult(
                "mdtc_20_assertReaderMovedOffTargetChapter", true,
                mapOf(
                    "progressJson" to progress,
                    "readerOffset" to off,
                    "space" to (space ?: "<none>"),
                    "currentChapterIndex" to cur,
                    "currentChapterTitle" to curRow.title,
                    "targetChapterIndex" to TARGET_CI,
                    "chapterCount" to pe.rows.size,
                    "chapters" to chaptersJson(pe.rows),
                )
            )
        } finally { db.close(); pe.doc.close() }
    }

    // =====================================================================
    // 6：书内笔记回源 —— 必须回到目标章（index>0）
    // =====================================================================

    @Test
    fun mdtc_30_assertReturnedToTargetChapter() = runBlocking {
        val pe = productionChapterTable()
        val db = openDb()
        try {
            val progress = rawOne(
                db,
                "SELECT COALESCE(current_location_json,'') FROM reading_progress WHERE book_id='$BOOK_MD'"
            )
            require(!progress.isNullOrBlank()) {
                "缺少阅读进度行：回源后需先 ON_PAUSE 落盘再断言"
            }
            val off = Regex("\"offset\":(\\d+)").find(progress!!)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            val cur = chapterIndexForOffset(pe.rows, off)
            val curRow = pe.rows[cur]
            require(cur == TARGET_CI) {
                "回源后当前章必须回到目标章 index=$TARGET_CI，实得 index=$cur（offset=$off）"
            }
            writeResult(
                "mdtc_30_assertReturnedToTargetChapter", true,
                mapOf(
                    "progressJson" to progress,
                    "readerOffset" to off,
                    "currentChapterIndex" to cur,
                    "currentChapterTitle" to curRow.title,
                    "targetChapterIndex" to TARGET_CI,
                    "chapters" to chaptersJson(pe.rows),
                )
            )
        } finally { db.close(); pe.doc.close() }
    }

    @Test
    fun mdtc_90_recordFinalState() = runBlocking {
        val db = openDb()
        try {
            val summary = StringBuilder()
                .append("book=").append(BOOK_MD)
                .append(" status=").append(rawOne(db, "SELECT COALESCE(content_status,'') FROM books WHERE id='$BOOK_MD'"))
                .append(" active=").append(rawOne(db, "SELECT COUNT(*) FROM books WHERE id='$BOOK_MD' AND deleted_at IS NULL"))
                .append(" hl=").append(rawOne(db, "SELECT COUNT(*) FROM highlights WHERE book_id='$BOOK_MD' AND deleted_at IS NULL"))
                .append(" progress=").append(rawOne(db, "SELECT COALESCE(current_location_json,'') FROM reading_progress WHERE book_id='$BOOK_MD'"))
                .toString()
            writeResult("mdtc_90_recordFinalState", true, mapOf("summary" to summary))
        } finally { db.close() }
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private fun sha(f: File): String {
        if (!f.isFile) return "MISSING"
        val d = java.security.MessageDigest.getInstance("SHA-256")
        return d.digest(f.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private fun cleanRows(db: AppDatabase) {
        val wdb = db.openHelper.writableDatabase
        listOf(
            "DELETE FROM highlights WHERE book_id LIKE 'mdtc-%'",
            "DELETE FROM notes WHERE book_id LIKE 'mdtc-%' OR id LIKE 'mdtc-%'",
            "DELETE FROM reading_sessions WHERE book_id LIKE 'mdtc-%'",
            "DELETE FROM book_files WHERE book_id LIKE 'mdtc-%'",
            "DELETE FROM book_content WHERE book_id LIKE 'mdtc-%'",
            "DELETE FROM reading_progress WHERE book_id LIKE 'mdtc-%'",
            "DELETE FROM books WHERE id LIKE 'mdtc-%'",
        ).forEach { wdb.execSQL(it) }
    }

    private companion object {
        const val TAG = "MdTrueChapterAccept"

        /** 夹具书 id：全部以 mdtc- 前缀，便于本批夹具清理。 */
        const val BOOK_MD = "mdtc-md-cross-chapter"

        /** 目标章在**生产章节表**中的索引（第 2 章 → 1，>0）。 */
        const val TARGET_CI = 1

        /** 目标章在夹具中的 H1 序号（`# 第 2 章`）。 */
        const val TARGET_CHAPTER_NUMBER = 2

        /** 标记句所在段号（目标章内第 7 段 → 章内非零位置）。 */
        const val MARKER_PARAGRAPH = 7

        /** 中性标记句：正文为一个独立段落，UI「整块选择」应整段选中它。 */
        const val MARKER = "中性测试标记句跨章甲甲甲"
    }
}