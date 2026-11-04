package com.creationreadingassistant.feature.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.mergeReaderProgress
import com.creationreadingassistant.data.repository.ReaderProgressSaveOrder
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** 文件替换阶段（CRA-RESELECT-ROLLBACK-FIX-v1）：按旧目标被触碰的程度决定回滚动作。 */
private const val SWAP_UNTOUCHED = 0
private const val SWAP_BACKED_UP = 1
private const val SWAP_TARGET_WRITTEN = 2

/**
 * EPUB 仓储：负责打开解析、登记到书架（满足 reading_progress 外键约束）、
 * 进度持久化（复用 V2 reading_progress 表）。
 */
@Singleton
class EpubRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val readingProgressDao: ReadingProgressDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    /** 文件操作接缝：路由「建立备份」与「回滚恢复」两个阶段的 renameTo / copyTo，见 [FileOps]。 */
    private val fileOps: FileOps,
) {
    /** 进程内解析缓存，最多缓存 3 本，超出自动淘汰最久未访问的条目。 */
    private val memoryCache = object : LinkedHashMap<String, EpubBook>(3, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EpubBook>?): Boolean {
            return size > 3
        }
    }

    suspend fun openEpub(uri: Uri): EpubBook {
        val book = EpubParser.parse(context, uri)
        persistOpenedBook(book, uri.lastPathSegment)
        return book
    }

    /**
     * 打开书架中已有的 EPUB。优先使用应用自有副本，并显式保留数据库 book id，
     * 避免从 file:// 回退打开时生成新 id、丢失原书进度。
     */
    suspend fun openStoredEpub(stored: BookEntity): EpubBook {
        memoryCache[stored.id]?.let { return it }
        val uri = stored.local_uri
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?: stored.local_content_path
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.fromFile(File(it.removePrefix("file://"))) }
            ?: throw IllegalStateException("本书缺少可读取的 EPUB 文件，请重新导入")
        val book = EpubParser.parse(
            context = context,
            uri = uri,
            expectedBookId = stored.id,
            fallbackLocalPath = stored.local_content_path,
        )
        persistOpenedBook(book, stored.original_file_name ?: uri.lastPathSegment)
        return book
    }

    /**
     * Replaces the local EPUB bytes for an existing book while preserving its database id and
     * every id-keyed reading record. The selected file is fully parsed before the old copy is
     * touched, then swapped through a staging file so a failed repair cannot destroy the last
     * readable copy.
     */
    suspend fun replaceStoredEpub(
        stored: BookEntity,
        selectedUri: Uri,
        selectedFileName: String,
    ): EpubBook = withContext(ioDispatcher) {
        // CRA-ROLLBACK-WIP-RECONCILE-v3：同书文件互斥闸。守卫、fresh-source 解析、暂存/
        // 备份/提升/DAO/回滚全程在闸内串行 —— 单个协程内的 for-loop 不构成跨协程互斥，
        // 多个 ViewModel 协程先后触发同书替换、或「打开未落盘的书」的落位段并发时，
        // 会互删 <id>.importing 或交替改写 target/backup。替换是低频用户操作，
        // 进程级串行换取结构上不可能交叉写同一组文件；读书热路径不受影响。
        EpubSwapGate.withGate {
        val target = File(context.filesDir, "books/epub/${stored.id}.epub")
        target.parentFile?.mkdirs()
        val staging = File(target.parentFile, "${stored.id}.repairing")
        val backup = File(target.parentFile, "${stored.id}.backup")
        // CRA-RESELECT-ROLLBACK-FIX-v2/v3：既有 backup 可能是上次中断后**唯一**保存旧副本的文件
        // （此时目标位恰好空缺）；残留也可能是文件或目录（中断的覆盖复制兜底不同阶段形态
        // 不同）。**存在任何同名 backup 残留**即拒绝本次替换：不作归属猜测、不盲删、
        // 不自动恢复，也不改写 target 或写 DB —— 把处理权交给用户。
        // CRA-ROLLBACK-WIP-RECONCILE-v2：守卫必须前移到 fresh-source 解析**之前** ——
        // 旧位置在 parse 之后，残留现场会先解析新来源、在派生路径落下 `.importing` 副本
        // （同 URI 时派生 id 与 stored.id 重合），随后才被守卫拒绝，留下本次产生的孤儿副本。
        if (backup.exists()) {
            throw IllegalStateException(
                "检测到未完成的替换残留（${backup.name}），本次替换已停止；" +
                    "旧正文若仍存在，则未被本次操作修改，残留备份已保留。" +
                    "重复重试不会自动恢复该备份",
            )
        }
        // CRA-ROLLBACK-WIP-RECONCILE-v2（S04 §9-1）：必须读取来源 URI 的**当前字节**，
        // 禁止 EpubParser 复用既有内部副本 —— 导入时 bookId 往往就等于 Uri 派生 id，
        // 普通模式下 parse 的早退分支会直接沿用旧 target，替换退化为「用 V1 覆盖 V1」的 no-op。
        // 失败语义（S04 §9-4）：该阶段任何失败都必须清理本次落下的 `.importing` 副本，
        // 且绝不退回旧内部副本冒充新来源 —— EpubParser 在 fresh-source 模式下已禁用该兜底。
            val validated = EpubParser.parseUnderSwapGate(context, selectedUri, requireFreshSource = true)
        val validatedFile = File(validated.cachedEpubPath)
        require(validatedFile.isFile && validatedFile.length() > 0L) {
            "所选 EPUB 没有可读取的正文"
        }

        staging.delete()
        // CRA-RESELECT-ROLLBACK-FIX-v1：按「旧目标被触碰的程度」分阶段回滚。
        // 修复前 catch 无条件 target.delete()，而暂存复制发生在旧目标备份**之前**：
        // 复制阶段就失败时旧副本原地未动、backup 尚未创建，却被删掉且无从恢复。
        // CRA-RESELECT-ROLLBACK-FIX-v2：catch 只能清理本次操作确实可能留下的临时字节。
        // 入口守卫已保证开始时 backup 位不存在，故此后在 backup 位出现的文件只会来自本次写入。
        var swapStage = SWAP_UNTOUCHED
        var backupMayHoldOurPartialBytes = false
        var oldBytesParkedInBackup = false
        try {
            validatedFile.inputStream().use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            }
            require(staging.length() == validatedFile.length() && staging.length() > 0L) {
                "复制 EPUB 时文件不完整"
            }
            if (target.exists()) {
                if (!fileOps.renameTo(target, backup)) {
                    // copyTo 兜底此刻起可能在本备份路径留下半成品，归本次操作所有
                    backupMayHoldOurPartialBytes = true
                    fileOps.copyTo(target, backup)
                    // S04 阻断①（CRA-ROLLBACK-WIP-RECONCILE-v2）：短写与「等长但错字节」都必须
                    // 拒绝，不得只比长度 —— 删掉旧 target 之前，backup 必须已与 target 逐字节一致。
                    require(verifyFilesEqual(target, backup)) { "备份旧副本时文件不完整" }
                }
                oldBytesParkedInBackup = true
                swapStage = SWAP_BACKED_UP
            }
            // 从这里起目标位会被写入（rename 失败时走覆盖复制的兜底，同样可能写坏目标）
            swapStage = SWAP_TARGET_WRITTEN
            if (!staging.renameTo(target)) {
                staging.copyTo(target, overwrite = true)
                staging.delete()
            }

            memoryCache.remove(stored.id)
            val replacement = EpubParser.parseUnderSwapGate(
                context = context,
                uri = Uri.fromFile(target),
                expectedBookId = stored.id,
                fallbackLocalPath = target.absolutePath,
            )
            val now = Instant.now().toString()
            // S04 修复：根据已成功落盘并校验的新内部副本 target 计算 content_hash。
            // - 小文件（≤32 MiB）：写入新内部文件的全量 MD5，与 library_source_refs 同口径；
            // - 大文件（>32 MiB）：置为 null，彻底清除旧正文哈希（旧哈希绝不能保留）；
            // - 失败/拒绝时由于已在之前的阶段抛出，此处绝不会把新哈希提前写入。
            val newContentHash = computeFileHash(target)
            val updated = stored.copy(
                title = replacement.title.takeIf { it.isNotBlank() } ?: stored.title,
                author = replacement.author ?: stored.author,
                original_file_name = selectedFileName,
                size = target.length().toBookSize(),
                local_uri = Uri.fromFile(target).toString(),
                local_content_path = target.absolutePath,
                content_status = "available",
                content_hash = newContentHash,
                updated_at = now,
            )
            if (bookDao.update(updated) == 0) bookDao.upsert(updated)
            memoryCache[stored.id] = replacement
            backup.delete()
            replacement
        } catch (error: Throwable) {
            memoryCache.remove(stored.id)
            when (swapStage) {
                // 尚未触碰旧目标：旧副本原地不动，只清掉本次 copyTo 兜底可能留下的半成品
                SWAP_UNTOUCHED -> if (backupMayHoldOurPartialBytes) backup.delete()
                // 旧副本已安全移入 backup、目标位空缺：复位旧字节
                SWAP_BACKED_UP -> restoreBackupOver(target, backup, error)
                // 新副本已（部分）安装：先删本次写入的新字节，再复位旧字节
                else -> {
                    target.delete()
                    if (oldBytesParkedInBackup) restoreBackupOver(target, backup, error)
                }
            }
            throw error
        } finally {
            staging.delete()
            // CRA-ROLLBACK-WIP-RECONCILE-v3：validatedFile（fresh-source 落在
            // <派生id>.importing 的来源副本）是本次独占产生的临时字节，
            // 成功与任何失败路径（暂存/备份建立/DAO/回滚）都必须清理；
            // 防御 `!= target` —— 万一未来 parse 语义变化把正式副本路径返回时不得误删。
            runCatching {
                if (validatedFile.absolutePath != target.absolutePath) validatedFile.delete()
            }
        }
        }
    }

    /**
     * 把 [backup] 复位到 [target]（CRA-RESELECT-ROLLBACK-FIX-v1 / v2）。
     *
     * 先 rename；跨平台失败时退回覆盖复制，复制**逐字节核对通过后才删 backup**
     * （S04 阻断②：短写或等长错字节都不得删掉唯一完整旧副本）。恢复失败时**保留 backup
     * 字节**供人工恢复，并把恢复异常挂到主异常 [primary] 的 suppressed 上：调用方看到的
     * 仍是原始失败原因，恢复失败也不会被吞掉，且本条路径绝不返回「恢复成功」。
     */
    private fun restoreBackupOver(target: File, backup: File, primary: Throwable) {
        if (!backup.exists()) {
            // 旧字节本应在此备份里却不存在 —— 这是恢复失败，不能静默当作「无需恢复」。
            primary.addSuppressed(IllegalStateException("回滚失败：旧副本备份 ${backup.name} 已不存在"))
            return
        }
        try {
            if (!fileOps.renameTo(backup, target)) {
                fileOps.copyTo(backup, target)
                if (!verifyFilesEqual(backup, target)) {
                    throw IOException("回滚恢复不完整：旧副本未逐字节复位")
                }
                backup.delete()
            }
        } catch (recovery: Throwable) {
            // 中性诊断（S04 §9-6）：只记录发生了什么，不含书名 / 文件名 / 完整路径 / 内容。
            // CRA-ROLLBACK-WIP-RECONCILE-v3：备份是否真的保留必须按现场断言 ——
            // 不得声称不存在的备份「已保留」；日志自身失败不得顶替原始主异常。
            runCatching {
                if (backup.exists()) {
                    AppLog.e("EpubRepository", "EPUB 回滚恢复未完成，完整备份已保留，旧正文可能部分损坏")
                } else {
                    AppLog.e("EpubRepository", "EPUB 回滚恢复未完成，旧副本备份已丢失，需要从外部来源恢复")
                }
            }
            primary.addSuppressed(recovery)
        }
    }

    /**
     * 有界缓冲流式核对两文件逐字节一致（S04 阻断①②，CRA-ROLLBACK-WIP-RECONCILE-v2）。
     *
     * 只检查长度不能拦下「等长但错字节」的损坏副本；全文件载入内存又会在大书上
     * 撑爆堆。这里用 64 KiB 双缓冲逐块比较：长度不同先失败，同长逐字节核对，
     * 任何一字节差异都返回 false。仅使用 java.io 基础 API，无 Android 版本要求。
     */
    private fun verifyFilesEqual(expected: File, actual: File): Boolean {
        if (expected.length() != actual.length()) return false
        expected.inputStream().use { a ->
            actual.inputStream().use { b ->
                val bufA = ByteArray(64 * 1024)
                val bufB = ByteArray(64 * 1024)
                while (true) {
                    val nA = a.read(bufA)
                    val nB = b.read(bufB)
                    if (nA != nB) return false
                    if (nA <= 0) return true
                    if (!bufA.copyOf(nA).contentEquals(bufB.copyOf(nB))) return false
                }
            }
        }
    }

    private suspend fun persistOpenedBook(book: EpubBook, originalFileName: String?) {
        val now = Instant.now().toString()
        val existing = bookDao.getById(book.id)
        val coverDataUrl = existing?.cover_data_url?.takeIf { it.isNotBlank() }
            ?: book.coverEntryPath?.let { entry ->
                withContext(ioDispatcher) {
                    runCatching {
                        EpubParser.loadCoverDataUrl(book.cachedEpubPath, entry)
                    }.getOrNull()
                }
            }
        val record = mergeEpubBookRecord(
            existing = existing,
            parsed = book,
            originalFileName = originalFileName,
            resolvedSize = withContext(ioDispatcher) {
                fileSize(book.cachedEpubPath)
            },
            now = now,
        ).let { r ->
            if (r.cover_data_url.isNullOrBlank() && !coverDataUrl.isNullOrBlank()) {
                r.copy(cover_data_url = coverDataUrl)
            } else {
                r
            }
        }
        if (existing == null || bookDao.update(record) == 0) {
            bookDao.upsert(record)
        }
        memoryCache[book.id] = book
    }

    /**
     * 修复旧版本留下的 EPUB size=0 记录。
     *
     * 只更新确认可读取且长度大于 0 的记录；权限失效或暂时不可访问时保留原记录，
     * 避免把一次瞬时 I/O 失败误判成正文丢失。
     */
    suspend fun repairMissingLocalFileSizes(): Int = withContext(ioDispatcher) {
        var repaired = 0
        bookDao.getEpubBooksNeedingSizeRepair().forEach { stored ->
            val uri = stored.local_uri
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            val resolvedSize = uri?.let { resolveUriSize(it, stored.local_content_path) }
                ?: fileSize(stored.local_content_path)
            if (resolvedSize > 0) {
                repaired += bookDao.updateSizeIfMissing(stored.id, resolvedSize)
            }
        }
        repaired
    }

    /**
     * 封面补扫：为「正文可读但没有封面」的 EPUB 书记录提取内嵌封面。
     * 覆盖两类缺口：导入早于封面提取功能的旧书；导入时封面解析瞬时失败的书。
     * 用户手动设置 / 同步来的封面优先（查询条件已排除非空 cover_data_url，不覆盖）。
     * 返回补上的数量。进程级一次性调用（见 EpubSizeRepairTask），必须在 IO 线程。
     */
    suspend fun backfillMissingEpubCovers(): Int = withContext(ioDispatcher) {
        var filled = 0
        bookDao.getEpubBooksMissingCover().forEach { stored ->
            val path = stored.local_content_path
                ?.takeIf { it.isNotBlank() }
                ?.removePrefix("file://")
                ?.takeIf { File(it).isFile && File(it).length() > 0L }
                ?: File(context.filesDir, "books/epub/${stored.id}.epub")
                    .takeIf { it.isFile && it.length() > 0L }
                    ?.absolutePath
                ?: return@forEach
            runCatching {
                val entry = EpubParser.resolveCoverEntry(path) ?: return@forEach
                val dataUrl = EpubParser.loadCoverDataUrl(path, entry) ?: return@forEach
                // bump updated_at：封面渲染的 Coil cacheKey 含 updated_at，确保 UI 立即换图
                bookDao.update(stored.copy(cover_data_url = dataUrl, updated_at = Instant.now().toString()))
                filled++
            }
        }
        filled
    }

    fun cached(bookId: String): EpubBook? = memoryCache[bookId]

    /**
     * CRA-READER-DISPATCH-ORDER-MINIMAL-v6：与 [com.creationreadingassistant.data.repository.BookRepository.saveProgress]
     * 同一条迟到丢弃合同（见 `ReaderProgressSaveOrder`）。EPUB 的两次真实保存动作
     * （分页防抖/退出保存、章切换落库）都经本入口，[order] 由生产方在事件产生点（业务派发边界）给出。
     */
    suspend fun saveProgress(
        bookId: String,
        chapterIndex: Int,
        percent: Float,
        offsetInChapter: Int = 0,
        absoluteOffset: Int = -1,
        /**
         * 事件序号。**不传**表示本次写入不属于任何阅读派发（自成一次事件，取单调递增的新序号，
         * 因此总是被接受）——只有替换/验收夹具这类不经阅读派发的调用方才省略；生产阅读
         * 路径（`ReaderViewModel` 的保存分支与章切换落库）恒显式传入真实值。
         */
        order: Long = ReaderProgressSaveOrder.next(),
    ) {
        val now = Instant.now()
        val legacyOffset = offsetInChapter.coerceAtLeast(0)
        // locator v2 单一写入契约（S12-①）：调用方给不出全书偏移（章切换落库等只知章号）
        // 时，v2 "offset" 必须缺省，由读侧按 `chapterStart[ci] + co` 派生；绝不以章内偏移
        // 冒充全局 —— 伪造值会让 SourceNavigationContract 的一致性校验拒绝所有回源跳转。
        // 传入 absoluteOffset 的调用方（分页防抖/退出保存）负责保证该不变式成立。
        val globalOffset = absoluteOffset.takeIf { it >= 0 }
        val currentLocationJson = com.creationreadingassistant.feature.reader.locator.LocatorBuilder.progressJson(
            legacyOffset = legacyOffset,
            chapterIndex = chapterIndex,
            charOffset = legacyOffset,
            globalOffset = globalOffset,
            legacyChapter = chapterIndex,
        )
        val incoming = ReadingProgressEntity(
            book_id = bookId,
            progress_percent = percent,
            completion_state = if (percent >= 99.9f) "finished" else "reading",
            current_location_json = currentLocationJson,
            updated_at = now.toString(),
        )
        ReaderProgressSaveOrder.accept(bookId, order) {
            readingProgressDao.upsert(
                mergeReaderProgress(
                    existing = readingProgressDao.getByBook(bookId),
                    incoming = incoming,
                    nowIso = now.toString(),
                    nowMillis = now.toEpochMilli(),
                ),
            )
        }
    }

    suspend fun loadProgress(bookId: String): Int {
        val raw = readingProgressDao.getByBook(bookId)?.current_location_json ?: return 0
        val v2 = com.creationreadingassistant.feature.reader.locator.LocatorCodec.locatorFromProgress(raw)
        if (v2?.chapterIndex != null) return v2.chapterIndex
        val m = Regex("\"chapter\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1)
        return m?.toIntOrNull() ?: 0
    }

    suspend fun loadProgressOffset(bookId: String): Int {
        val raw = readingProgressDao.getByBook(bookId)?.current_location_json ?: return 0
        val v2 = com.creationreadingassistant.feature.reader.locator.LocatorCodec.locatorFromProgress(raw)
        if (v2?.charOffset != null) return v2.charOffset.coerceAtLeast(0)
        val m = Regex("\"offset\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1)
        return m?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }

    private fun resolveUriSize(uri: Uri, fallbackPath: String? = null): Int {
        val queried = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    cursor.getLong(index)
                } else {
                    0L
                }
            } ?: 0L
        }.getOrDefault(0L)
        if (queried > 0) return queried.toBookSize()

        val descriptorLength = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        }.getOrDefault(0L)
        if (descriptorLength > 0) return descriptorLength.toBookSize()

        if (uri.scheme == "file") {
            val directFileSize = fileSize(uri.path)
            if (directFileSize > 0) return directFileSize
        }
        return fileSize(fallbackPath)
    }

    private fun fileSize(path: String?): Int {
        if (path.isNullOrBlank()) return 0
        val normalized = path.removePrefix("file://")
        return runCatching { File(normalized).length().toBookSize() }.getOrDefault(0)
    }

    /**
     * 计算内部副本文件的全量 MD5（S04 EPUB 来源刷新）。
     *
     * 与 ShelfImporter.refreshSourceBaseline 保持完全一致的 32 MiB 阈值：
     * - 大文件（>32 MiB）：返回 null，供清除旧哈希使用（策略性不计算全量哈希）；
     * - 小文件（≤32 MiB）：计算并返回小写 32 位十六进制 MD5 字符串；
     *   若小文件为空（<= 0）或读取发生 I/O 故障，直接抛出异常，绝不以 null 掩盖故障误导写库。
     */
    internal fun computeFileHash(file: File): String? {
        val len = file.length()
        if (len > MAX_HASH_BYTES) return null
        require(len > 0L) { "EPUB 本地副本为空，无法计算内容哈希" }
        val md = java.security.MessageDigest.getInstance("MD5")
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

    companion object {
        const val MAX_HASH_BYTES = 32 * 1024 * 1024
    }
}

internal fun mergeEpubBookRecord(
    existing: BookEntity?,
    parsed: EpubBook,
    originalFileName: String?,
    resolvedSize: Int,
    now: String,
): BookEntity {
    if (existing == null) {
        return BookEntity(
            id = parsed.id,
            title = parsed.title,
            author = parsed.author,
            format = "epub",
            original_file_name = originalFileName,
            size = resolvedSize.coerceAtLeast(0),
            local_uri = parsed.localUri,
            local_content_path = parsed.cachedEpubPath,
            content_status = "available",
            imported_at = now,
            updated_at = now,
        )
    }
    return existing.copy(
        title = parsed.title.ifBlank { existing.title },
        author = parsed.author ?: existing.author,
        format = "epub",
        original_file_name = existing.original_file_name ?: originalFileName,
        size = if (resolvedSize > 0) resolvedSize else existing.size,
        local_uri = parsed.localUri,
        local_content_path = parsed.cachedEpubPath,
        content_status = "available",
        updated_at = now,
    )
}

private fun Long.toBookSize(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
