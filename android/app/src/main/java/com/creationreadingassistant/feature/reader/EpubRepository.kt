package com.creationreadingassistant.feature.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.mergeReaderProgress
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.feature.log.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

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
     *
     * **崩溃残留守卫（第一道，最小范围）**：若本次操作开始前同名的 `.backup` 已存在，
     * 说明上次替换被进程杀死在「旧副本已挪走 / 新副本已落位」之间。此时磁盘上可能是
     * 唯一完好的旧正文（旧文件已挪进 backup、新文件尚未落位），也可能只是上次已提交成功
     * 却未清理的过期副本 —— 仅凭 target 是否存在或长度**无法区分**这两种情况。
     * 因此一律保留 target 与 backup、拒绝本次替换，并抛出可识别错误；**绝不**删除、覆盖或
     * 自动恢复任何一侧。自动恢复状态机属独立设计，不在本片实现。
     * 无残留时保持下方 backupReady / promoteStarted 的进程内回滚语义不变。
     */
    suspend fun replaceStoredEpub(
        stored: BookEntity,
        selectedUri: Uri,
        selectedFileName: String,
    ): EpubBook = withContext(ioDispatcher) {
        val target = File(context.filesDir, "books/epub/${stored.id}.epub")
        target.parentFile?.mkdirs()
        val staging = File(target.parentFile, "${stored.id}.repairing")
        val backup = File(target.parentFile, "${stored.id}.backup")

        // 守卫必须先于 EpubParser.parse：解析会为来源文件落一份临时副本、对大文件还要读全量
        // 字节，而拒绝与否只取决于磁盘上是否已有残留，与来源是否合法无关。
        if (backup.exists()) {
            // 提示语必须对**两种现场都成立**：target 存在（旧正文仍在）与 target 缺失
            // （旧正文已挪进 backup）。故不能断言「原有文件已保留」—— target 缺失时并没有
            // 这样一份文件，那样写会让用户误以为旧正文还在原处。
            // 也不能暗示存在自助恢复入口，或引导用户删书/清备份（那正是销毁唯一副本的做法）。
            throw IllegalStateException(
                "检测到未完成的替换残留（${backup.name}），本次替换已停止；" +
                    "旧正文若仍存在，则未被本次操作修改，残留备份已保留。" +
                    "重复重试不会自动恢复该备份",
            )
        }

        // 必须传 requireFreshSource = true：本次要验证的是**所选来源的当前字节**，
        // 而不是任何已存在的内部副本。
        //
        // 关键：传 expectedBookId **不足以**解决这个问题。书被导入时若不传 expectedBookId，
        // parse 按 Uri 派生 id，而该派生 id 就是书的 bookId ⇒ 派生路径与下面的 target 是
        // **同一个文件** ⇒ 只要存在旧副本，「文件已存在就直接沿用」的早退必然命中，
        // 来源的新字节根本没被读过，替换退化成「用 V1 覆盖 V1」的 no-op
        // （真机证据：docs/qa/2026-09-24-s04-safe-real-library-acceptance.md §9-1）。
        //
        // 新鲜来源模式下 parse 把字节落到 <id>.importing（不碰 target），
        // 因此此刻 target 仍是唯一完好的旧正文；它只在下面备份就绪后才被改写。
        val validated = EpubParser.parse(
            context = context,
            uri = selectedUri,
            expectedBookId = stored.id,
            requireFreshSource = true,
        )
        val validatedFile = File(validated.cachedEpubPath)
        require(validatedFile.isFile && validatedFile.length() > 0L) {
            "所选 EPUB 没有可读取的正文"
        }

        staging.delete()
        // 此处不再有无条件的 `backup.delete()`：入口守卫已保证本次操作开始时不存在残留，
        // 而对残留做删除正是「销毁唯一完好正文」的原始缺陷（见上方守卫注释）。
        // 回滚必须按「走到哪一步」分流。旧实现把 `backup.exists()` 当作「备份可用」的判据，
        // 于是有两个窗口会毁掉用户仅存的旧副本：
        //   1) 建立 backup **之前**失败（staging 复制 / 长度校验）—— 此时 target 还是完好的
        //      旧副本，而 backup 根本不存在；catch 里无条件的 `target.delete()` 直接把它删掉，
        //      且没有任何东西可以还原。
        //   2) `renameTo` 失败后退回 `copyTo`、而 `copyTo` 中途失败 —— backup 残缺、target 完好；
        //      catch 仍会删掉 target，再把残缺的 backup 挪成 target（用坏文件覆盖好文件）。
        // 下面两个标记把「旧副本是否已安全落进 backup」与「是否已开始改写 target」分开，
        // 只有 backupReady 时才允许删除 / 覆盖 target。
        var backupReady = false
        var promoteStarted = false
        try {
            validatedFile.inputStream().use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            }
            require(staging.length() == validatedFile.length() && staging.length() > 0L) {
                "复制 EPUB 时文件不完整"
            }
            if (target.exists()) {
                if (fileOps.renameTo(target, backup)) {
                    backupReady = true
                } else {
                    // rename 失败退回复制：必须**复制完整并校验通过**后才允许删原文件。
                    // 中途失败时 target 仍是完好的旧副本，backup 可能是残缺文件。
                    fileOps.copyTo(target, backup)
                    require(backup.length() == target.length()) { "备份旧副本时文件不完整" }
                    backupReady = true
                    target.delete()
                }
            }
            promoteStarted = true
            if (!staging.renameTo(target)) {
                staging.copyTo(target, overwrite = true)
                staging.delete()
            }

            memoryCache.remove(stored.id)
            val replacement = EpubParser.parse(
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
            if (validatedFile.absolutePath != target.absolutePath) validatedFile.delete()
            replacement
        } catch (error: Throwable) {
            memoryCache.remove(stored.id)
            // 只在「旧副本已安全落进 backup」时才动 target：
            //   - backup 就绪：先删掉本次已改写的 target，再把 backup 还原回去。
            //   - backup 未就绪：target 就是唯一完好的旧副本，**原样保留**；
            //     若磁盘上残留的是「复制备份中途失败」的残缺文件，把它清掉，
            //     避免下次启动被误当成可用备份。
            if (backupReady) {
                if (promoteStarted) target.delete()
                restoreBackupAfterFailedPromotion(target, backup, error)
            } else {
                if (backup.exists()) backup.delete()
                if (promoteStarted) target.delete()
            }
            // 取消同样走这条回滚（不在此处直接重抛 CancellationException 跳过回滚）：
            // 否则取消会把半截的新副本留在 target 上，反而破坏原本完好的旧副本。
            throw error
        } finally {
            staging.delete()
            // 新鲜来源副本（<id>.importing）是**本次操作**产生的：无论成功失败都不该留下。
            // 成功路径已在 try 末尾删除；这里兜住「成功删除之前就抛出」与所有失败路径，
            // 否则损坏/失败的 EPUB 会在私有目录留下无人引用的孤儿副本
            // （真机证据：docs/qa/2026-09-24-s04-safe-real-library-acceptance.md §9-4）。
            // 注意：它绝不等于 target/backup，删它不会碰任何已登记的正文或备份。
            runCatching { validatedFile.delete() }
        }
    }

    /**
     * 失败回滚的恢复段：把 [backup]（旧副本）还原为 [target]。
     *
     * 两条不变式，均以 [primaryError] 优先：
     * 1. **原异常不被顶替**：恢复动作自身的任何异常经 [runCatching] 收集后挂到
     *    [primaryError.addSuppressed]，最终仍抛出 [primaryError]。
     * 2. **完整 backup 只在验证通过后才删除**：
     *    - `fileOps.renameTo` 成功 ⇒ 内容未变，直接完成；
     *    - 失败则退回 `fileOps.copyTo`，返回后先流式核对 target 与 backup 逐字节一致
     *      （[contentEquals]，固定 64 KiB 缓冲、遇差异即停，不做整文件驻留内存）；
     *    - 核对**通过**才删除 backup（完整旧字节已被 target 接管）；
     *    - 核对**失败**则保留 backup（完整旧字节仍在磁盘上，绝不静默丢失），
     *      把失败原因挂为 suppressed 并写入诊断日志（[AppLog]，固定描述，不含书名/文件名/路径/正文/异常消息）；
     *      注意 suppressed 只是尽力而为的附加信息：当前生产调用方（ShelfImporter.repairFileTyped）
     *      只读取异常 message 做任务态展示，并不依据 suppressed 作出用户提示决策；当异常跨
     *      `withContext` 边界且协程栈回溯恢复开启（JVM 断言开启或显式 `kotlinx.coroutines.debug`，
     *      生产 Android 默认关闭）时，调用方捕获到的是恢复后的副本——其 suppressed 为空，
     *      携带 suppressed 的原实例位于其 cause 链上。调用层当前对回滚状态的可靠判据是
     *      磁盘残留（入口守卫），不是异常对象；
     *      此时 target 是不完整的恢复结果，但**不存在比它更完整的在盘副本被删掉**。
     *
     * 恢复侧的 rename / copy 经 [fileOps] 路由（与建立备份阶段同一条接缝），
     * 测试可按 source/dest 区分注入；生产实现 [DefaultFileOps] 与直接 `File` 调用逐字同义。
     *
     * `target.delete()` 与 `backup.delete()` 的返回值沿既有边界不检查（尽力而为）；
     * 对 `renameTo` 成功路径的内容一致性由「rename 是原子换名」这一文件系统语义保证。
     */
    private fun restoreBackupAfterFailedPromotion(target: File, backup: File, primaryError: Throwable) {
        if (!backup.exists()) return
        runCatching {
            if (fileOps.renameTo(backup, target)) return@runCatching
            fileOps.copyTo(backup, target)
            if (!contentEquals(target, backup)) {
                error("回滚恢复不完整：target 与 backup 内容不一致，backup 已保留")
            }
            backup.delete()
        }.onFailure { restoreFailure ->
            primaryError.addSuppressed(restoreFailure)
            // 诊断记录（应用「日志与诊断」页可见）。必须先挂载 suppressed 再写日志，且记录动作
            // 自身包在 runCatching 里：即使记录失败也不得顶替主异常、不阻断 suppressed、不触碰 backup。
            // 消息为固定描述 + 失败类别（异常类名），不含书名、文件名、路径、正文或异常消息。
            runCatching {
                AppLog.w(
                    "EpubRepository",
                    "EPUB 回滚恢复未完成(${restoreFailure::class.simpleName ?: "Throwable"})：" +
                        "完整旧字节已保留在备份文件中，后续替换会被残留守卫拒绝",
                )
            }
        }
    }

    /** 流式逐字节比较两个文件内容；长度不同直接判否，不做整文件驻留内存。 */
    private fun contentEquals(a: File, b: File): Boolean {
        if (a.length() != b.length()) return false
        a.inputStream().use { ia ->
            b.inputStream().use { ib ->
                val bufA = ByteArray(BUFFER_BYTES)
                val bufB = ByteArray(BUFFER_BYTES)
                while (true) {
                    val nA = ia.read(bufA)
                    val nB = ib.read(bufB)
                    if (nA != nB) return false
                    if (nA < 0) return true
                    if (!bufA.copyOf(nA).contentEquals(bufB.copyOf(nB))) return false
                }
                @Suppress("UNREACHABLE_CODE") true
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

    suspend fun saveProgress(
        bookId: String,
        chapterIndex: Int,
        percent: Float,
        offsetInChapter: Int = 0,
        absoluteOffset: Int = -1,
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
        readingProgressDao.upsert(
            mergeReaderProgress(
                existing = readingProgressDao.getByBook(bookId),
                incoming = incoming,
                nowIso = now.toString(),
                nowMillis = now.toEpochMilli(),
            ),
        )
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

        /** 回滚恢复内容核对的流式缓冲大小（64 KiB，与既有哈希计算口径一致）。 */
        private const val BUFFER_BYTES = 64 * 1024
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
