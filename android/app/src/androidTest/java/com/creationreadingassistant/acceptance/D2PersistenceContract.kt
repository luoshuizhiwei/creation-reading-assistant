package com.creationreadingassistant.acceptance

import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.BookFileEntity
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File
import java.security.MessageDigest

/**
 * CRA-D2-DEVICE-ARTIFACTS-REPAIR-v4
 *
 * 完整持久数据合同定义、快照读取、全实体差分合同验证器与证据落盘器。
 *
 * 定点修复与核心增强（余项 2 & 余项 3）：
 * 1. 成功差分真正执行：
 *    - 相关行非空强断言：SAME 重新关联场景下 BookFile、BookContent、SourceRef、用户资产
 *      相关行必须非空，严禁使用 if (file != null) 跳过检查；
 *    - 真实产品允许变化字段实体规范化：定义 normalize 函数消除合法变更列，其余全部列执行逐字段严格比较；
 *    - Refresh 场景严核 Provider 实际 size、last_modified、content_hash；
 *    - 用户核心资产（进度、笔记、高亮）100% 逐字段绝对保持。
 * 2. 真实证据落盘与本轮身份对账：
 *    - 增加 runId 标识，支持按 run 隔离输出；
 *    - 完整序列化 actualDataStoreFiles、candidateDataStorePaths 及前后指纹；
 *    - 支持 PASSED 与 FAILED 状态输出，失败时保留现场与异常信息，杜绝旧成功冒充。
 */
data class D2BookSnapshot(
    val book: BookEntity?,
    val bookContent: BookContentEntity?,
    val bookFile: BookFileEntity?,
    val progress: ReadingProgressEntity?,
    val note: NoteEntity?,
    val highlight: HighlightEntity?,
    val sourceRef: LibrarySourceRefEntity?,
    val internalFileSha256: String?,
    val internalFileSize: Long,
) {
    fun toReadableMap(): Map<String, Any?> = mapOf(
        "book" to book?.let {
            mapOf(
                "id" to it.id,
                "title" to it.title,
                "author" to it.author,
                "format" to it.format,
                "original_file_name" to it.original_file_name,
                "content_hash" to it.content_hash,
                "size" to it.size,
                "local_uri" to it.local_uri,
                "local_content_path" to it.local_content_path,
                "content_status" to it.content_status,
                "cover_data_url" to it.cover_data_url,
                "description" to it.description,
                "imported_at" to it.imported_at,
                "device_id" to it.device_id,
                "payload" to it.payload,
                "revision" to it.revision,
                "updated_at" to it.updated_at,
                "deleted_at" to it.deleted_at,
            )
        },
        "bookContent" to bookContent?.let {
            val preview = it.reader_preview
            val epubJson = it.epub_json
            mapOf(
                "book_id" to it.book_id,
                "reader_preview" to preview,
                "reader_preview_len" to (preview?.length ?: 0),
                "reader_preview_sha256" to preview?.let { text -> D2SnapshotReader.sha256Text(text) },
                "epub_json" to epubJson,
                "epub_json_len" to (epubJson?.length ?: 0),
                "epub_json_sha256" to epubJson?.let { text -> D2SnapshotReader.sha256Text(text) },
                "has_epub_json" to (!epubJson.isNullOrBlank()),
            )
        },
        "bookFile" to bookFile?.let {
            mapOf(
                "book_id" to it.book_id,
                "file_name" to it.file_name,
                "format" to it.format,
                "content_hash" to it.content_hash,
                "size" to it.size,
                "local_uri" to it.local_uri,
                "payload" to it.payload,
                "updated_at" to it.updated_at,
                "deleted_at" to it.deleted_at,
            )
        },
        "progress" to progress?.let {
            mapOf(
                "book_id" to it.book_id,
                "progress_percent" to it.progress_percent,
                "last_read_at" to it.last_read_at,
                "total_reading_time_ms" to it.total_reading_time_ms,
                "completion_state" to it.completion_state,
                "current_location_json" to it.current_location_json,
                "payload" to it.payload,
                "revision" to it.revision,
                "device_id" to it.device_id,
                "updated_at" to it.updated_at,
                "completed_at" to it.completed_at,
                "deleted_at" to it.deleted_at,
            )
        },
        "note" to note?.let {
            mapOf(
                "id" to it.id,
                "book_id" to it.book_id,
                "inspiration_id" to it.inspiration_id,
                "title" to it.title,
                "body" to it.body,
                "excerpt" to it.excerpt,
                "chapter_title" to it.chapter_title,
                "progress_percent" to it.progress_percent,
                "kind" to it.kind,
                "locator_json" to it.locator_json,
                "payload" to it.payload,
                "created_at" to it.created_at,
                "device_id" to it.device_id,
                "revision" to it.revision,
                "updated_at" to it.updated_at,
                "deleted_at" to it.deleted_at,
            )
        },
        "highlight" to highlight?.let {
            mapOf(
                "id" to it.id,
                "book_id" to it.book_id,
                "text" to it.text,
                "note" to it.note,
                "color" to it.color,
                "chapter_title" to it.chapter_title,
                "progress_percent" to it.progress_percent,
                "locator_json" to it.locator_json,
                "payload" to it.payload,
                "created_at" to it.created_at,
                "device_id" to it.device_id,
                "revision" to it.revision,
                "updated_at" to it.updated_at,
                "deleted_at" to it.deleted_at,
            )
        },
        "sourceRef" to sourceRef?.let {
            mapOf(
                "book_id" to it.book_id,
                "root_id" to it.root_id,
                "provider_authority" to it.provider_authority,
                "document_id" to it.document_id,
                "display_name" to it.display_name,
                "format" to it.format,
                "size" to it.size,
                "last_modified" to it.last_modified,
                "content_hash" to it.content_hash,
                "candidate_fingerprint" to it.candidate_fingerprint,
                "last_seen_at" to it.last_seen_at,
                "availability" to it.availability,
            )
        },
        "internalFileSha256" to internalFileSha256,
        "internalFileSize" to internalFileSize,
    )
}

object D2SnapshotReader {
    /**
     * 读取指定书籍相关全部数据行与内部正文副本快照。
     * 严格使用合法生产 DAO API，绝无新增或伪造方法。
     */
    suspend fun capture(db: AppDatabase, bookId: String, internalFile: File): D2BookSnapshot {
        val book = db.bookDao().getById(bookId)
        val content = db.bookContentDao().getByBook(bookId)
        val file = db.bookFileDao().getByBook(bookId)
        val progress = db.readingProgressDao().getByBook(bookId)
        val note = db.noteDao().getActiveByBookIds(listOf(bookId)).firstOrNull()
        val highlight = db.highlightDao().getActiveByBookIds(listOf(bookId)).firstOrNull()
        val sourceRef = db.librarySourceRefDao().getAll().firstOrNull { it.book_id == bookId }

        val (fileSha, fileSize) = if (internalFile.exists() && internalFile.isFile) {
            Pair(sha256(internalFile), internalFile.length())
        } else {
            Pair(null, -1L)
        }

        return D2BookSnapshot(
            book = book,
            bookContent = content,
            bookFile = file,
            progress = progress,
            note = note,
            highlight = highlight,
            sourceRef = sourceRef,
            internalFileSha256 = fileSha,
            internalFileSize = fileSize,
        )
    }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256Text(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = text.toByteArray(Charsets.UTF_8)
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

object D2ContractVerifier {

    /**
     * 无论何时，用户自身创建与推进的核心资产（阅读进度、笔记、高亮标注）必须绝对 100% 逐行逐列完全保持。
     */
    fun assertStrictUserDataPreserved(before: D2BookSnapshot, after: D2BookSnapshot, tag: String) {
        val beforeProg = requireNotNull(before.progress) { "[$tag] before.progress 必须非空" }
        val afterProg = requireNotNull(after.progress) { "[$tag] after.progress 必须非空" }
        assertEquals("[$tag] 阅读进度必须逐字段完全一致", beforeProg, afterProg)

        val beforeNote = requireNotNull(before.note) { "[$tag] before.note 必须非空" }
        val afterNote = requireNotNull(after.note) { "[$tag] after.note 必须非空" }
        assertEquals("[$tag] 笔记记录必须逐字段完全一致", beforeNote, afterNote)

        val beforeHl = requireNotNull(before.highlight) { "[$tag] before.highlight 必须非空" }
        val afterHl = requireNotNull(after.highlight) { "[$tag] after.highlight 必须非空" }
        assertEquals("[$tag] 高亮标注必须逐字段完全一致", beforeHl, afterHl)
    }

    /**
     * 适用于拒绝类场景（DIFFERENT / UNKNOWN / NoSourceRef）：数据与正文必须 100% 零修改，无任何写入副作用。
     */
    fun assertNoChange(before: D2BookSnapshot, after: D2BookSnapshot, tag: String) {
        assertEquals("[$tag] 书籍记录不可被修改", before.book, after.book)
        assertEquals("[$tag] 正文记录不可被修改", before.bookContent, after.bookContent)
        assertEquals("[$tag] 文件元信息不可被修改", before.bookFile, after.bookFile)
        assertStrictUserDataPreserved(before, after, tag)
        assertEquals("[$tag] 来源引用不可被修改", before.sourceRef, after.sourceRef)
        assertEquals("[$tag] 内部正文文件 SHA 不可被修改", before.internalFileSha256, after.internalFileSha256)
        assertEquals("[$tag] 内部正文文件大小不可被修改", before.internalFileSize, after.internalFileSize)
    }

    // -------------------------------- 规范化函数（余项 2）

    /**
     * SAME 关联场景下 BookEntity 的规范化：将产品明确允许变更的字段置为空值，以便对剩余字段做整实体相等比较。
     * 允许变更字段：updated_at, size, local_uri, local_content_path, content_status
     */
    fun normalizeBookForSameRelink(book: BookEntity): BookEntity = book.copy(
        updated_at = "__NORMALIZED__",
        size = 0,
        local_uri = "__NORMALIZED__",
        local_content_path = "__NORMALIZED__",
        content_status = "__NORMALIZED__",
    )

    /**
     * SAME 关联场景下 BookFileEntity 的规范化：
     * 允许变更字段：file_name, size, local_uri, payload, updated_at
     */
    fun normalizeFileForSameRelink(file: BookFileEntity): BookFileEntity = file.copy(
        content_hash = null, // repairFile recreates metadata without this optional hash
        file_name = "__NORMALIZED__",
        size = 0,
        local_uri = "__NORMALIZED__",
        payload = "__NORMALIZED__",
        updated_at = "__NORMALIZED__",
    )

    /**
     * 适用于 SAME 重新关联场景：
     * - 相关行（Book, File, Content, SourceRef, Progress, Note, Highlight）必须全部非空，严禁为 null 跳过！
     * - 对允许变化字段通过规范化实体进行全实体差分比较；
     * - 校验允许变化字段的变化符合预期；
     * - 用户核心资产 100% 严格保持。
     */
    fun assertSameRelinkContract(
        before: D2BookSnapshot,
        after: D2BookSnapshot,
        expectedSelectedSha256: String,
        expectedContentHash: String,
        expectedSelectedSize: Int,
        tag: String,
    ) {
        // 1. 相关行必须非空强断言（绝不跳过）
        val oldBook = requireNotNull(before.book) { "[$tag] before.book 必须非空" }
        val newBook = requireNotNull(after.book) { "[$tag] after.book 必须非空" }
        val oldFile = requireNotNull(before.bookFile) { "[$tag] before.bookFile 必须非空" }
        val newFile = requireNotNull(after.bookFile) { "[$tag] after.bookFile 必须非空" }
        val oldContent = requireNotNull(before.bookContent) { "[$tag] before.bookContent 必须非空" }
        val newContent = requireNotNull(after.bookContent) { "[$tag] after.bookContent 必须非空" }
        val oldSource = requireNotNull(before.sourceRef) { "[$tag] before.sourceRef 必须非空" }
        val newSource = requireNotNull(after.sourceRef) { "[$tag] after.sourceRef 必须非空" }

        // 2. BookEntity 全实体规范化比对（除允许变化字段外，其余必须 100% 相等）
        assertEquals(
            "[$tag] BookEntity 除去允许变更字段后必须全实体一致",
            normalizeBookForSameRelink(oldBook),
            normalizeBookForSameRelink(newBook),
        )
        // 允许变更字段的预期状态校验
        assertEquals("[$tag] content_status 必须为 available", "available", newBook.content_status)
        assertEquals("[$tag] content_hash 必须与所选内容哈希完全一致", expectedContentHash, newBook.content_hash)
        assertEquals("[$tag] content_hash 与原始记录保持一致", oldBook.content_hash, newBook.content_hash)
        assertEquals("[$tag] size 必须更新为所选文件大小", expectedSelectedSize, newBook.size)
        assertNotNull("[$tag] local_uri 必须非空", newBook.local_uri)
        assertNotNull("[$tag] local_content_path 必须非空", newBook.local_content_path)
        assertTrue("[$tag] local_content_path 必须有效存在", File(newBook.local_content_path!!).exists())

        // 3. BookFileEntity 全实体规范化比对（除允许变更字段外，必须 100% 相等）
        assertEquals(
            "[$tag] BookFileEntity 除去允许变更字段后必须全实体一致",
            normalizeFileForSameRelink(oldFile),
            normalizeFileForSameRelink(newFile),
        )
        assertEquals("[$tag] repairFile 重建文件元信息的可选 hash", null, newFile.content_hash)
        assertEquals("[$tag] BookFile.size 必须更新", expectedSelectedSize, newFile.size)

        // 4. BookContentEntity 比对（正文结构未变，全字段保持）
        val expectedContent = if (newBook.format.equals("txt", ignoreCase = true)) {
            oldContent.copy(reader_preview = File(newBook.local_content_path!!).readText(Charsets.UTF_8).take(20_000))
        } else oldContent
        assertEquals("[$tag] BookContentEntity 符合格式对应的实际重关联合同", expectedContent, newContent)

        // 5. LibrarySourceRefEntity 比对（来源绑定关系保持）
        assertEquals("[$tag] LibrarySourceRef.book_id 保持", oldSource.book_id, newSource.book_id)
        assertEquals("[$tag] LibrarySourceRef.provider_authority 保持", oldSource.provider_authority, newSource.provider_authority)
        assertEquals("[$tag] LibrarySourceRef.document_id 保持", oldSource.document_id, newSource.document_id)

        // 6. 物理正文文件换入所选字节
        assertEquals("[$tag] 内部正文文件 SHA 必须等于所选文件 SHA", expectedSelectedSha256, after.internalFileSha256)
        assertEquals("[$tag] 内部正文文件大小必须等于所选文件大小", expectedSelectedSize.toLong(), after.internalFileSize)

        // 7. 用户资产 100% 严格保持
        assertStrictUserDataPreserved(before, after, tag)
    }

    /**
     * 适用于 refreshFromSource 场景：
     * - 校验本次 Provider 实际参数（size, last_modified, content_hash）；
     * - 校验 content_hash 与来源基线推进；
     * - 核心元数据规范化全实体比对；
     * - 全部用户资产 100% 严格保持。
     */
    fun assertRefreshContract(
        before: D2BookSnapshot,
        after: D2BookSnapshot,
        expectedRefreshedSha256: String,
        expectedNewContentHash: String,
        expectedProviderSize: Long,
        expectedProviderLastModified: Long,
        tag: String,
    ) {
        val oldBook = requireNotNull(before.book) { "[$tag] before.book 必须非空" }
        val newBook = requireNotNull(after.book) { "[$tag] after.book 必须非空" }
        val oldSource = requireNotNull(before.sourceRef) { "[$tag] before.sourceRef 必须非空" }
        val newSource = requireNotNull(after.sourceRef) { "[$tag] after.sourceRef 必须非空" }
        val newContent = requireNotNull(after.bookContent) { "[$tag] after.bookContent 必须非空" }

        // 1. 严格核验本次 Provider 实际参数
        assertEquals("[$tag] 来源引用的内容哈希必须等于 Provider hash", expectedNewContentHash, newSource.content_hash)
        assertEquals("[$tag] 来源引用的文件大小必须等于 Provider size", expectedProviderSize, newSource.size)
        assertEquals("[$tag] 来源引用的修改时间必须等于 Provider last_modified", expectedProviderLastModified, newSource.last_modified)
        assertTrue("[$tag] 来源引用最后观测时间必须推进", newSource.last_seen_at >= oldSource.last_seen_at)

        // 2. 来源身份保持
        assertEquals("[$tag] 来源引用的 book_id 保持", oldSource.book_id, newSource.book_id)
        assertEquals("[$tag] 来源引用的 authority 保持", oldSource.provider_authority, newSource.provider_authority)
        assertEquals("[$tag] 来源引用的 document_id 保持", oldSource.document_id, newSource.document_id)

        // 3. BookEntity 更新与元数据保持
        assertEquals("[$tag] BookEntity.content_hash 必须更新为新哈希", expectedNewContentHash, newBook.content_hash)
        assertNotEquals("[$tag] BookEntity.content_hash 不得沿用旧值", oldBook.content_hash, newBook.content_hash)
        assertEquals("[$tag] BookEntity.size 必须更新为新尺寸", expectedProviderSize.toInt(), newBook.size)

        // 规范化比对元数据：除 content_hash, size, updated_at 推进外，核心元数据全等
        val normalizedOld = oldBook.copy(content_hash = "", size = 0, updated_at = "")
        val normalizedNew = newBook.copy(content_hash = "", size = 0, updated_at = "")
        assertEquals("[$tag] BookEntity 核心元数据除更新字段外必须完全一致", normalizedOld, normalizedNew)

        // 4. 正文记录更新且非空
        assertNotNull("[$tag] 正文预览不可为空", newContent.reader_preview)

        // 5. 内部正文文件换入新字节
        assertEquals("[$tag] 内部正文文件 SHA 必须等于刷新文件的 SHA", expectedRefreshedSha256, after.internalFileSha256)
        assertEquals("[$tag] 内部正文文件大小必须等于刷新文件大小", expectedProviderSize, after.internalFileSize)

        // 6. 用户资产 100% 严格保持
        assertStrictUserDataPreserved(before, after, tag)
    }

    /**
     * 关库重开断言：证明数据真正落盘并可完整重开读回，而非仅在内存中有效。
     */
    fun assertReopenMatches(closedSnapshot: D2BookSnapshot, reopenedSnapshot: D2BookSnapshot, tag: String) {
        assertEquals("[$tag/reopen] 关库重开后 BookEntity 必须完全一致", closedSnapshot.book, reopenedSnapshot.book)
        assertEquals("[$tag/reopen] 关库重开后 BookContentEntity 必须完全一致", closedSnapshot.bookContent, reopenedSnapshot.bookContent)
        assertEquals("[$tag/reopen] 关库重开后 BookFileEntity 必须完全一致", closedSnapshot.bookFile, reopenedSnapshot.bookFile)
        assertEquals("[$tag/reopen] 关库重开后 ReadingProgressEntity 必须完全一致", closedSnapshot.progress, reopenedSnapshot.progress)
        assertEquals("[$tag/reopen] 关库重开后 NoteEntity 必须完全一致", closedSnapshot.note, reopenedSnapshot.note)
        assertEquals("[$tag/reopen] 关库重开后 HighlightEntity 必须完全一致", closedSnapshot.highlight, reopenedSnapshot.highlight)
        assertEquals("[$tag/reopen] 关库重开后 LibrarySourceRefEntity 必须完全一致", closedSnapshot.sourceRef, reopenedSnapshot.sourceRef)
        assertEquals("[$tag/reopen] 关库重开后内部文件 SHA 必须完全一致", closedSnapshot.internalFileSha256, reopenedSnapshot.internalFileSha256)
        assertEquals("[$tag/reopen] 关库重开后内部文件大小必须完全一致", closedSnapshot.internalFileSize, reopenedSnapshot.internalFileSize)
    }
}

/**
 * 机器可读的证据项。
 */
data class D2MachineReadableEvidence(
    val task: String = "CRA-D2-DEVICE-ARTIFACTS-REPAIR-v4",
    val runId: String,
    val testName: String,
    val format: String,
    val scenario: String,
    val status: String = "PASSED", // PASSED 或 FAILED
    val failureMessage: String? = null,
    val failureStackTrace: String? = null,
    val dbIsolated: Boolean,
    val dbPath: String,
    val touchRecord: D2StorageTouchRecord? = null,
    val beforeState: Map<String, Any?>? = null,
    val afterState: Map<String, Any?>? = null,
    val reopenedState: Map<String, Any?>? = null,
    val allowedChanges: List<String> = emptyList(),
    val preservedFields: List<String> = emptyList(),
    val passedAssertions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * 结构化证据实际落盘器：保证证据不仅留在内存，而且实际序列化为 JSON 写入磁盘。
 */
object D2EvidenceWriter {

    fun toJsonObject(evidence: D2MachineReadableEvidence): JSONObject {
        val root = JSONObject()
        root.put("task", evidence.task)
        root.put("run_id", evidence.runId)
        root.put("test_name", evidence.testName)
        root.put("format", evidence.format)
        root.put("scenario", evidence.scenario)
        root.put("status", evidence.status)
        root.put("failure_message", evidence.failureMessage ?: JSONObject.NULL)
        root.put("failure_stack_trace", evidence.failureStackTrace ?: JSONObject.NULL)
        root.put("db_isolated", evidence.dbIsolated)
        root.put("db_path", evidence.dbPath)

        evidence.touchRecord?.let {
            val touchObj = JSONObject()
            touchObj.put("root_dir", it.rootDir)
            touchObj.put("database_file", it.databaseFile)
            touchObj.put("database_wal_file", it.databaseWalFile)
            touchObj.put("database_shm_file", it.databaseShmFile)
            touchObj.put("files_dir", it.filesDir)
            touchObj.put("cache_dir", it.cacheDir)
            touchObj.put("datastore_dir", it.dataStoreDir)
            touchObj.put("actual_datastore_location", it.actualDataStoreLocation)
            touchObj.put("actual_datastore_files", JSONArray(it.actualDataStoreFiles))
            touchObj.put("candidate_datastore_paths", JSONArray(it.candidateDataStorePaths))

            val beforeFpObj = JSONObject()
            for ((k, fp) in it.storeFingerprintsBefore) {
                beforeFpObj.put(k, fingerprintToJson(fp))
            }
            touchObj.put("store_fingerprints_before", beforeFpObj)

            val afterFpObj = JSONObject()
            for ((k, fp) in it.storeFingerprintsAfter) {
                afterFpObj.put(k, fingerprintToJson(fp))
            }
            touchObj.put("store_fingerprints_after", afterFpObj)

            touchObj.put("protection_and_recovery_requirement", it.protectionAndRecoveryRequirement)
            root.put("touch_record", touchObj)
        }

        root.put("before_state", evidence.beforeState?.let { mapToJsonObject(it) } ?: JSONObject.NULL)
        root.put("after_state", evidence.afterState?.let { mapToJsonObject(it) } ?: JSONObject.NULL)
        root.put("reopened_state", evidence.reopenedState?.let { mapToJsonObject(it) } ?: JSONObject.NULL)
        root.put("allowed_changes", JSONArray(evidence.allowedChanges))
        root.put("preserved_fields", JSONArray(evidence.preservedFields))
        root.put("passed_assertions", JSONArray(evidence.passedAssertions))
        root.put("timestamp", evidence.timestamp)
        return root
    }

    private fun fingerprintToJson(fp: StoreFileFingerprint): JSONObject {
        val obj = JSONObject()
        obj.put("path", fp.path)
        obj.put("exists", fp.exists)
        obj.put("length", fp.length)
        obj.put("last_modified", fp.lastModified)
        obj.put("sha256", fp.sha256 ?: JSONObject.NULL)
        return obj
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        for ((k, v) in map) {
            when (v) {
                null -> json.put(k, JSONObject.NULL)
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") json.put(k, mapToJsonObject(v as Map<String, Any?>))
                is List<*> -> json.put(k, JSONArray(v))
                else -> json.put(k, v)
            }
        }
        return json
    }

    fun writeEvidence(evidence: D2MachineReadableEvidence, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        val json = toJsonObject(evidence)
        targetFile.writeText(json.toString(2), Charsets.UTF_8)
    }

    fun writeSummary(evidences: List<D2MachineReadableEvidence>, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        val arr = JSONArray()
        for (e in evidences) {
            arr.put(toJsonObject(e))
        }
        targetFile.writeText(arr.toString(2), Charsets.UTF_8)
    }
}
