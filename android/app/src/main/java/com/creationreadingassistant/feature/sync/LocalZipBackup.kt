package com.creationreadingassistant.feature.sync

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.feature.log.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地 ZIP 备份恢复（P1-6 MVP）。
 *
 * ZIP 结构（向后兼容：所有 entry 带相对路径）：
 *   backup.json               JsonBridge.LocalExport 完整 JSON（13 类实体 + 关联表）
 *   books/{id}/...            普通书源文件目录（TXT/MD 等导入时复制的原始文件）
 *   books/epub/{id}.epub      EPUB 存储副本（可选，缺失时可由 books/{id}/ 下原文件重解析）
 *
 * 设计要点：
 * 1. **流式读写**：ZipOutputStream / ZipInputStream 逐 entry 处理，整库（数百 MB 级）
 *    不一次性加载到内存，避免 OOM。
 * 2. **分段恢复与保护**（S14 修订，2026-09-28）：解压到临时目录并校验 backup.json 后，
 *    先执行 DB 事务（失败整体回滚），再恢复 books 目录。**DB 先提交、文件在后，
 *    整包不是原子的**。文件段的保护是：
 *    - 每个目标文件先写同目录独立 staging，完整复制并校验长度后替换：
 *      POSIX/Android 由 `renameTo` 原子替换既有目标；Windows（JVM 测试等）对已存在
 *      目标走「保护性 backup 换入」（旧字节全程存在，绝不先删除旧目标）；
 *      替换失败保留旧目标（旧文件字节零损伤），绝不退回直接对目标写流；
 *    - 版本感知：本地较新书籍行（DB 合并时被跳过）的源文件一律不覆盖，
 *      依据 [JsonBridge.importFromString] 回报的实际合并结果（[ImportMergeResult]），
 *      不看 ZIP 是否含该文件，也不按路径模式猜测书 id。
 *    - crash residue（进程被杀残留，S14 residue 收口）：保护性换入先把 dst 改名为
 *      `*.restore-backup-*` 再把 staging 落位为 dst——进程死于两次 rename 之间时
 *      dst 缺失、backup 是唯一完整旧字节。残留的「本工具归属」由**恢复归属日志**
 *      证明（[JOURNAL_NAME]，保护性换入的第一次破坏性 rename 之前**持久化**写入
 *      {dst 相对路径, backup 名, staging 名}）：导入/导出开始先按日志完成被打断的换入
 *      （staging 已校验完整且 DB 已提交 → staging 优先落位；dst 仍为旧字节、staging
 *      在、backup 未产生时同样补完换入，见 [completeInterruptedSwapIn]；backup 兜底
 *      复位；被取代文件按证据清理）；**无日志证据的同格式文件一律视为普通书籍文件**
 *      ——保留原名、导出照常打包、写入诊断日志明确报告，绝不凭文件名改名或删除
 *      （完全同格式的命名学冲突仅凭文件名不可判定，见 [recoverCrashResidues]）。
 *      **fail-closed**：日志写不进去时 [journalAdd] 上抛、绝不执行随后的破坏性
 *      rename（旧目标原样保留），杜绝「无归属证据」的换入中间态。
 *    失败后的中间态（DB=备份世界 ∪ 保留旧书行、文件=新旧混合）由「再次导入同一
 *    ZIP 收敛」兜底（DB 合并幂等、文件全量重写自愈半写入）。
 * 3. **与 JSON 备份一致**：backup.json 与 JsonBridge 导出的纯 JSON 完全相同，
 *    因此用户若只想快速迁移元数据（不含书源文件），继续用纯 JSON 即可；
 *    ZIP 是「连书一起搬走」的整包形态。
 */
@Singleton
class LocalZipBackup @Inject constructor(
    private val jsonBridge: JsonBridge,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** 通过 SAF 选择的 URI 导出 ZIP（backup.json + filesDir/books/ 原始文件）。 */
    suspend fun exportZip(context: Context, zipUri: Uri) = withContext(ioDispatcher) {
        context.contentResolver.openOutputStream(zipUri)?.use { rawOs ->
            BufferedOutputStream(rawOs, BUF_SIZE).use { bos ->
                ZipOutputStream(bos).use { zos ->
                    // 1. 先写 backup.json（元数据最小，通常 < 5MB，优先落盘）
                    val json = jsonBridge.exportToString(context)
                    putTextEntry(zos, NAME_BACKUP_JSON, json)

                    // 2. 遍历 booksDir 下所有文件，按相对路径写入
                    val booksDir = File(context.filesDir, "books")
                    if (booksDir.isDirectory) {
                        // S14 残留归属收口：导出前先按归属日志收敛可判定的 crash 残留
                        // （如「dst 缺失 + backup 独存」复位为正常书籍文件），让备份链
                        // 拿到书本体；失败不阻塞导出，现场原样保留并继续。
                        runCatching { recoverCrashResidues(booksDir) }
                            .onFailure {
                                AppLog.w(TAG, "导出前 crash 残留恢复失败（保留现场继续导出）：${it.message}")
                            }
                        // 过滤只认归属日志：日志归属的 staging 永不打包（内容不可信）；
                        // 日志归属的 backup 在 dst 仍存在时已被取代，不打包。**其余一律
                        // 打包**——包括无日志证据的同格式文件：它可能是恰好同名的普通
                        // 书籍文件，凭文件名跳过会让导出缺书（真正的数据丢失）；打包
                        // 半写入残留的最坏后果只是目标端多一个无 DB 行的孤立文件。
                        val journal = readJournal(booksDir)
                        val stagingRels = journal.map { siblingRel(it.rel, it.staging) }.toSet()
                        val backupRelToDst = journal.associate { siblingRel(it.rel, it.backup) to it.rel }
                        booksDir.walkTopDown().forEach { f ->
                            if (!f.isFile) return@forEach
                            val rel = relativePathOf(booksDir, f)
                            if (rel == JOURNAL_NAME) return@forEach // 归属日志是工具元数据，永不进入备份链
                            if (rel in stagingRels) return@forEach
                            val backupDst = backupRelToDst[rel]
                            if (backupDst != null && File(booksDir, backupDst).isFile) return@forEach
                            val entryName = "$BOOKS_PREFIX/$rel"
                            putFileEntry(zos, entryName, f)
                        }
                    }
                }
            }
        } ?: throw IllegalStateException("无法写入 ZIP：$zipUri")
    }

    /** 从 SAF 选择的 ZIP 恢复：解到临时目录 → 校验 JSON → 入库 + 覆盖 books。 */
    suspend fun importZip(context: Context, zipUri: Uri) = withContext(ioDispatcher) {
        val tmp = File(context.cacheDir, "zip-restore-${System.currentTimeMillis()}")
        try {
            // 0. crash residue 恢复：先按归属日志完成上一次被杀换入的现场（staging
            //    优先落位 / backup 兜底复位 / 被取代文件清理），再进入解压 / DB / 文件段。
            //    恢复只依据 books 目录与归属日志，不依赖本次 ZIP 内容；无日志证据的
            //    同格式文件按普通文件保留并报告，绝不凭文件名改名。
            recoverCrashResidues(File(context.filesDir, "books"))

            // 1. 解压到临时目录，边解边校验 entry 名（防止 ZipSlip）
            tmp.mkdirs()
            val jsonFile = File(tmp, NAME_BACKUP_JSON)
            val tmpBooks = File(tmp, "books")
            context.contentResolver.openInputStream(zipUri)?.use { rawIs ->
                BufferedInputStream(rawIs, BUF_SIZE).use { bis ->
                    ZipInputStream(bis).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            val safeName = sanitizeEntryName(entry.name)
                            val target = File(tmp, safeName)
                            if (!target.normalize().path.startsWith(tmp.normalize().path + File.separator) &&
                                target.normalize().path != tmp.normalize().path
                            ) {
                                throw SecurityException("ZIP 包含非法路径：${entry.name}")
                            }
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else {
                                target.parentFile?.mkdirs()
                                target.outputStream().use { os -> zis.copyTo(os, BUF_SIZE) }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } ?: throw IllegalStateException("无法读取 ZIP：$zipUri")

            if (!jsonFile.isFile) {
                throw IllegalStateException("ZIP 中缺少 $NAME_BACKUP_JSON，不是合法备份")
            }

            // 2. 先导入 DB（整体事务，失败自动回滚），并取回合并结果：
            //    哪些书籍行本次实际被写入、哪些本地较新书籍行被跳过（S14 版本感知复制）。
            var mergeResult = ImportMergeResult()
            jsonBridge.importFromString(context, jsonFile.readText(Charsets.UTF_8)) {
                mergeResult = it
            }

            // 3. 再恢复 books 目录。DB 已提交而文件复制在后，这一段不在任何事务里：
            //    每个目标文件走 staging → 校验 → 原子替换；本地较新书籍的文件按
            //    合并结果跳过。失败时的中间态与恢复路径见类注释（S14 修订）。
            copyBookFiles(tmpBooks, File(context.filesDir, "books"), mergeResult)
        } finally {
            // 无论成功失败，清理临时目录
            runCatching { tmp.deleteRecursively() }
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    // ── 文件恢复段（S14 修订）─────────────────────────────────────────────

    /**
     * 仅供测试注入的文件复制接缝（S14）：模拟「写入中途失败」。
     * 生产路径恒为默认实现；业务代码不得改写，测试用例负责在结束后还原。
     */
    internal var fileCopyOperation: (InputStream, OutputStream) -> Unit =
        { input, output -> input.copyTo(output, BUF_SIZE) }

    /**
     * 把临时解压出的 books 目录恢复到真实 books 目录。
     *
     * - **版本感知**：[mergeResult] 的 [ImportMergeResult.skippedBookContentPaths]
     *   （本地较新书籍行的 `local_content_path`）换算成 books 根的相对路径后，
     *   一律跳过、不用备份字节覆盖；换算不出的条目（路径不在 books 根下）不猜测归属，
     *   按未保护处理（该文件本来也不在导出集合里的情况才可能发生）。
     * - **逐文件 staging**：见 [copyWithStaging]。
     */
    private fun copyBookFiles(tmpBooks: File, realBooksDir: File, mergeResult: ImportMergeResult) {
        if (!tmpBooks.isDirectory) return
        realBooksDir.mkdirs()
        val protectedRels = mergeResult.skippedBookContentPaths
            .mapNotNull { relativePathOrNull(realBooksDir, File(it)) }
            .toSet()
        tmpBooks.walkTopDown().forEach { src ->
            val rel = relativePathOf(tmpBooks, src)
            val dst = File(realBooksDir, rel)
            if (src.isDirectory) {
                dst.mkdirs()
            } else if (src.isFile) {
                // 外来 ZIP 不得借 books/.cra-restore-journal.json 注入或覆盖本地归属日志：
                // 日志一旦可被备份包写入，伪造条目就能让恢复端误判普通文件归属。
                if (rel == JOURNAL_NAME) return@forEach
                if (rel in protectedRels) return@forEach
                dst.parentFile?.mkdirs()
                copyWithStaging(src, dst, rel, realBooksDir)
            }
        }
    }

    /**
     * 单个书源文件的受保护复制：先写同目录独立 staging，完整复制并校验长度后替换目标。
     *
     * 替换是**跨平台**的（`File.renameTo` 在 POSIX/Android 上原子替换既有目标，
     * 但在 Windows 对已存在目标返回 false）：
     * 1. 目标是**目录**时直接拒绝（目录内容来历不明，不得被搬走后删除）；
     * 2. 先 `renameTo`——Android/Linux 一次到位（含既有目标）；
     * 3. 失败且目标存在时走「保护性 backup 换入」：旧目标 rename 为同目录
     *    `*.restore-backup-<nano>`（旧字节全程存在，**绝不先删除旧目标**），
     *    再 rename staging 到位；落位失败则把 backup 恢复回目标，自动恢复也失败时
     *    **显式报错并保留 backup**；成功后清理 backup，清理失败同样显式报错并保留。
     *
     * 任何失败都保留旧目标字节并上抛；绝不退回直接对目标 outputStream 写入。
     * finally 清理 staging 残留。
     */
    private fun copyWithStaging(src: File, dst: File, rel: String, booksDir: File) {
        if (dst.isDirectory) {
            throw IllegalStateException("书源文件替换失败：目标位置被目录占用（已原样保留）：$rel")
        }
        val staging = File(dst.parentFile, "${dst.name}.restore-${System.nanoTime()}.staging")
        try {
            staging.outputStream().use { out ->
                src.inputStream().use { input -> fileCopyOperation(input, out) }
            }
            if (staging.length() != src.length()) {
                throw IllegalStateException("书源文件 staging 校验失败（长度与源不一致）：$rel")
            }
            if (staging.renameTo(dst)) {
                return
            }
            if (!dst.exists()) {
                throw IllegalStateException("书源文件替换失败（目标不存在，无旧文件可保护）：$rel")
            }
            // Windows 等平台对已存在目标 renameTo 返回 false：走保护性 backup 换入。
            val backup = File(dst.parentFile, "${dst.name}.restore-backup-${System.nanoTime()}")
            // 归属日志先于任何破坏性 rename **持久化**落盘（writeJournalDurably：写 +
            // flush + fsync 文件内容）：此后进程被杀，恢复端才能凭日志「可靠认定」
            // dst/backup/staging 是本工具 in-flight 现场（而不是恰好同名的普通书籍文件）。
            // **fail-closed**：日志写不进去（磁盘满 / 路径不可写等）时 journalAdd 直接
            // 上抛，绝不执行随后的破坏性 rename —— 宁可本次换入失败、旧目标原样保留，
            // 也不制造「无归属证据」的换入中间态（此时旧目标尚未被触碰）。
            val journalEntry = RestoreJournalEntry(rel = rel, backup = backup.name, staging = staging.name)
            journalAdd(booksDir, journalEntry)
            if (!dst.renameTo(backup)) {
                // ③ 失败：dst 原样、无 backup 产生，条目即时剪枝。
                journalRemove(booksDir, journalEntry)
                throw IllegalStateException("书源文件替换失败：旧目标移入保护性 backup 失败（旧目标原样保留）：$rel")
            }
            try {
                if (!staging.renameTo(dst)) {
                    throw IllegalStateException("书源文件替换失败：新文件落位失败：$rel")
                }
            } catch (e: IllegalStateException) {
                if (backup.renameTo(dst)) {
                    // 自动恢复成功：dst=旧字节、backup 已被消费，条目剪枝。
                    journalRemove(booksDir, journalEntry)
                    throw e
                }
                // 自动恢复也失败：dst 缺失、backup 旧字节唯一存在——保留条目，恢复端补完。
                throw IllegalStateException(
                    "书源文件替换失败，且旧目标自动恢复失败；保护性 backup 已保留：${backup.path}",
                    e,
                )
            }
            if (!backup.delete()) {
                // dst=新字节、backup=被取代旧字节：保留条目，恢复端按证据补完 ⑤。
                throw IllegalStateException("书源文件已替换，但保护性 backup 清理失败，已按原样保留：${backup.path}")
            }
            journalRemove(booksDir, journalEntry)
        } finally {
            if (staging.exists()) staging.delete()
        }
    }

    // ── crash residue 恢复与识别（S14 残留归属收口，2026-09-28）───────────

    // 本工具残留的严格命名格式：时间戳取 System.nanoTime()（当前 19 位，下限 13 位）。
    // 形近命名（非数字后缀、位数不足、分隔符变体）一律不匹配——普通书源文件名可能
    // 恰好类似残留，识别必须宁缺毋滥。**严格格式匹配 ≠ 归属**：完全同格式的命名学
    // 冲突仅凭文件名不可判定，归属必须由恢复归属日志证明；无证据的同格式文件按
    // 普通文件处理（保留原名、导出照常打包、诊断日志报告）。
    // 数字一律以字符串承载与比较（[compareNanoDigits]）：任意长度都不会溢出。
    private val backupResidueRegex = Regex("^(.+)\\.restore-backup-(\\d{13,})$")
    private val stagingResidueRegex = Regex("^(.+)\\.restore-(\\d{13,})\\.staging$")

    private data class BackupResidue(val file: File, val targetName: String, val nanoDigits: String)

    /** 匹配残留**格式**（不代表归属）；目标名含路径分隔符或为 ".." 时不匹配（防恢复路径逃逸）。 */
    private fun matchBackupResidue(file: File): BackupResidue? {
        val match = backupResidueRegex.matchEntire(file.name) ?: return null
        val targetName = match.groupValues[1]
        if (!isSafeResidueTargetName(targetName)) {
            return null
        }
        return BackupResidue(file, targetName, match.groupValues[2])
    }

    /** 匹配残留**格式**（不代表归属）；与 [matchBackupResidue] 同样的保守守卫。 */
    private fun matchStagingResidue(file: File): Boolean {
        val match = stagingResidueRegex.matchEntire(file.name) ?: return false
        return isSafeResidueTargetName(match.groupValues[1])
    }

    private fun isSafeResidueTargetName(targetName: String): Boolean =
        targetName.isNotEmpty() && targetName != ".." &&
            !targetName.contains('/') && !targetName.contains('\\') && !targetName.contains(File.separatorChar)

    /**
     * 任意长度数字串的数值序比较（不转 Long，永不过时/溢出）：
     * 去前导零后先比长度、再比字典序——两者合起来对任意长度都是精确数值序。
     */
    private fun compareNanoDigits(a: String, b: String): Int {
        val na = a.trimStart('0').ifEmpty { "0" }
        val nb = b.trimStart('0').ifEmpty { "0" }
        return if (na.length != nb.length) na.length.compareTo(nb.length) else na.compareTo(nb)
    }

    private fun stagingNanoDigits(stagingName: String): String =
        stagingResidueRegex.matchEntire(stagingName)?.groupValues?.get(2).orEmpty()

    // ── 恢复归属日志 ──────────────────────────────────────────────────────

    /**
     * 一条 in-flight 换入记录。[rel] 是 dst 相对 books 根的路径（正斜杠），
     * [backup]/[staging] 是 dst 同目录下的文件名。三个名字间有一致性约束
     * （backup/staging 的目标名 == rel 的最后一段），读取时校验，不一致按无效丢弃。
     */
    private data class RestoreJournalEntry(val rel: String, val backup: String, val staging: String)

    /**
     * 读取并校验归属日志。任何异常形态都按「无证据」处理（返回已有有效条目或空）：
     * - 文件不存在 → 空（常态：POSIX 上直接 rename 原子替换，不产生备份，无需日志）；
     * - JSON 残缺（进程死于写入/剪枝重写中途）→ 整体按无证据处理并记录诊断日志，
     *   此前所有破坏性 rename 都发生在日志写成功之后，因此"无证据"方向是安全的；
     * - 条目名不合严格格式 / 三名不一致 → 仅丢弃该条（其余条目仍有证据效力）。
     */
    private fun readJournal(booksDir: File): List<RestoreJournalEntry> {
        val file = File(booksDir, JOURNAL_NAME)
        if (!file.isFile) return emptyList()
        val parsed = runCatching {
            Json.parseToJsonElement(file.readText()).jsonArray.map { element ->
                val obj = element.jsonObject
                RestoreJournalEntry(
                    rel = obj["rel"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    backup = obj["backup"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    staging = obj["staging"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                )
            }
        }.onFailure {
            AppLog.w(TAG, "恢复归属日志残缺（进程可能死于写入中途），按无归属证据处理：同格式文件全部保留原名")
        }.getOrNull() ?: return emptyList()
        return parsed.mapNotNull { entry ->
            entry.validated().also {
                if (it == null) AppLog.w(TAG, "归属日志条目无效，按无证据处理（文件保留原名）：$entry")
            }
        }
    }

    private fun RestoreJournalEntry.validated(): RestoreJournalEntry? {
        fun badSegment(segment: String) = segment.isBlank() || segment == "." || segment == ".." ||
            segment.contains('/') || segment.contains('\\') || segment.contains(File.separatorChar)
        val dstName = rel.substringAfterLast('/', missingDelimiterValue = "")
        if (rel.isBlank() || rel.startsWith("/") || rel.split('/').any { badSegment(it) }) return null
        if (badSegment(backup) || badSegment(staging)) return null
        if (backupResidueRegex.matchEntire(backup)?.groupValues?.get(1) != dstName) return null
        if (stagingResidueRegex.matchEntire(staging)?.groupValues?.get(1) != dstName) return null
        return this
    }

    private fun writeJournal(booksDir: File, entries: List<RestoreJournalEntry>) {
        val json = buildJsonArray {
            entries.forEach { entry ->
                addJsonObject {
                    put("rel", entry.rel)
                    put("backup", entry.backup)
                    put("staging", entry.staging)
                }
            }
        }
        runCatching { File(booksDir, JOURNAL_NAME).writeText(json.toString()) }
            .onFailure { AppLog.w(TAG, "恢复归属日志写入失败（若进程被杀，现场将按无证据保留）：${it.message}") }
    }

    /**
     * [writeJournal] 的持久化版本：写 + flush + fsync 文件内容后才返回。
     * 用于归属日志先于破坏性 rename 落盘的场景——确保进程被杀后日志在磁盘上。
     * 失败上抛（调用方决定是否继续）；不吞异常，不写半文件。
     */
    private fun writeJournalDurably(booksDir: File, entries: List<RestoreJournalEntry>) {
        val json = buildJsonArray {
            entries.forEach { entry ->
                addJsonObject {
                    put("rel", entry.rel)
                    put("backup", entry.backup)
                    put("staging", entry.staging)
                }
            }
        }
        val journalFile = File(booksDir, JOURNAL_NAME)
        FileOutputStream(journalFile).use { fos ->
            fos.write(json.toString().toByteArray(Charsets.UTF_8))
            fos.flush()
            fos.fd.sync()
        }
    }

    /**
     * 追加一条 in-flight 记录（**fail-closed**）：持久化写入失败时上抛，绝不执行
     * 随后的破坏性 rename——宁可本次换入失败、旧目标原样保留，也不制造「无归属
     * 证据」的换入中间态（此时旧目标尚未被触碰）。IOException 包装为
     * IllegalStateException（与 [copyWithStaging] 的异常约定一致）并保留原始 cause。
     */
    private fun journalAdd(booksDir: File, entry: RestoreJournalEntry) {
        try {
            writeJournalDurably(booksDir, readJournal(booksDir) + entry)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("归属日志持久化失败（fail-closed：中止换入，旧目标原样保留）", e)
        }
    }

    /** 剪枝一条已解析记录；失败仅记录（陈旧条目会在下次恢复时再次剪枝）。 */
    private fun journalRemove(booksDir: File, entry: RestoreJournalEntry) {
        runCatching { writeJournal(booksDir, readJournal(booksDir) - entry) }
            .onFailure { AppLog.w(TAG, "归属日志剪枝失败（陈旧条目将由下次恢复收敛）：${it.message}") }
    }

    /** [rel] 同目录下的兄弟文件相对路径。 */
    private fun siblingRel(rel: String, fileName: String): String {
        val parent = rel.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isEmpty()) fileName else "$parent/$fileName"
    }

    /**
     * 进程在保护性换入的破坏性 rename 序列中被杀时，books 目录会留下 dst/backup/
     * staging 的中间态。恢复只对**归属日志**证明的现场生效（日志先于第一个破坏性
     * rename 写入，且写入时 staging 已通过长度校验、DB 事务已提交）：
     *
     * - **完成被打断的换入**：dst **缺失**时优先把日志归属的 staging（已校验的完整新
     *   字节，与已提交 DB 一致）落位为 dst；staging 缺席时以 backup 旧字节兜底复位。
     *   dst **仍在**（旧字节）且 staging 在、backup 未产生时（kill 于 ③ 后 ④ 前），
     *   同样补完换入：旧字节移入 backup、staging 落位为 dst（[completeInterruptedSwapIn]）
     *   ——否则旧正文会停在 dst，与已提交 DB 不一致。
     * - **⑤ 补完**：dst 就位后，同 op 的 backup（被取代旧字节）与残留 staging 凭
     *   日志证据清理——这正是原操作成功路径本来要做的事。
     * - **同一 dst 多条目**（多轮 kill 累积）：按 staging nanoTime 新→旧处理，收敛
     *   到最近一次完整新字节；旧代文件随 ⑤ 补完清理。
     * - **陈旧条目**（引用文件已不存在）→ 剪枝；**日志残缺/条目无效** → 整体按无
     *   证据处理。
     * - **无日志证据的同格式文件**：一律不改名、不删除、原样保留，并在诊断日志
     *   明确报告"未能自动清理"——它可能是恰好同名的普通书籍文件，仅凭文件名
     *   达不到「可靠认定为本工具产生」的门槛。
     */
    private fun recoverCrashResidues(booksDir: File) {
        if (!booksDir.isDirectory) return
        val entries = readJournal(booksDir)
        if (entries.isEmpty()) {
            reportUntrustedResidue(booksDir, trustedRels = emptySet())
            return
        }
        val remaining = entries.toMutableList()
        entries.groupBy { it.rel }.forEach { (rel, group) ->
            val dst = File(booksDir, rel)
            val dir = dst.parentFile ?: return@forEach
            val ordered = group.sortedWith { a, b ->
                compareNanoDigits(stagingNanoDigits(b.staging), stagingNanoDigits(a.staging))
            }
            for (entry in ordered) {
                val backupFile = File(dir, entry.backup)
                val stagingFile = File(dir, entry.staging)
                if (!dst.exists() && stagingFile.isFile) {
                    if (!stagingFile.renameTo(dst)) {
                        throw IllegalStateException(
                            "crash residue 恢复失败：日志归属的 staging 落位失败，已原样保留：${stagingFile.path}",
                        )
                    }
                } else if (!dst.exists() && backupFile.isFile) {
                    if (!backupFile.renameTo(dst)) {
                        throw IllegalStateException(
                            "crash residue 恢复失败：日志归属的 backup 复位失败，已原样保留：${backupFile.path}",
                        )
                    }
                } else if (dst.isFile && stagingFile.isFile && !backupFile.exists()) {
                    // kill 于 journalAdd 后、③（dst→backup）前：dst 仍为旧字节、staging
                    // 为已校验新字节、backup 未产生——补完换入（旧字节移入 backup、
                    // staging 落位为 dst），否则旧正文停在 dst 与已提交 DB 不一致。
                    completeInterruptedSwapIn(dst, backupFile, stagingFile, rel)
                }
                if (dst.isFile) {
                    // ⑤ 补完：dst 就位后，同 op 的 backup 与 staging 已无角色，按证据清理。
                    val backupGone = !backupFile.exists() || backupFile.delete()
                    val stagingGone = !stagingFile.exists() || stagingFile.delete()
                    if (backupGone && stagingGone) {
                        remaining.remove(entry)
                    } else {
                        AppLog.w(
                            TAG,
                            "crash residue 按证据清理失败（保留条目，下次导入继续补完）：${backupFile.path} / ${stagingFile.path}",
                        )
                    }
                } else if (!backupFile.exists() && !stagingFile.exists()) {
                    // 陈旧条目：无可恢复文件，剪枝。
                    remaining.remove(entry)
                } else {
                    // dst 被目录占用等保守场景：文件与条目都原样保留。
                    AppLog.w(TAG, "crash residue 现场保守保留（dst 非普通文件）：${dst.path}")
                }
            }
        }
        if (remaining.size != entries.size) {
            writeJournal(booksDir, remaining)
        }
        reportUntrustedResidue(booksDir, trustedRels(entries))
    }

    /**
     * 补完被 kill 打断的保护性换入（kill 于 journalAdd 后、③ dst→backup 前）：
     * 此时 dst 仍为旧字节、staging 为已校验的新字节、backup 未产生。
     * 把旧字节移入 backup、staging 落位为 dst——与 [copyWithStaging] 的 ③④ 等价。
     * 任何 rename 失败都上抛并保留文件（交由下次恢复或人工处置），不丢字节。
     */
    private fun completeInterruptedSwapIn(dst: File, backup: File, staging: File, rel: String) {
        if (!dst.renameTo(backup)) {
            throw IllegalStateException(
                "crash residue 恢复失败：补完换入时旧目标移入 backup 失败，已原样保留：${dst.path}",
            )
        }
        try {
            if (!staging.renameTo(dst)) {
                throw IllegalStateException(
                    "crash residue 恢复失败：补完换入时 staging 落位失败：$rel",
                )
            }
        } catch (e: IllegalStateException) {
            // staging 落位失败：把 backup 复位为 dst，回到 kill 前现场。
            if (backup.renameTo(dst)) {
                throw e
            }
            throw IllegalStateException(
                "crash residue 恢复失败：补完换入 staging 落位失败且 backup 复位也失败；backup 已保留：${backup.path}",
                e,
            )
        }
    }

    /** 日志条目引用的全部残留文件的 books 相对路径集合（含已被消费的，供误报排除）。 */
    private fun trustedRels(entries: List<RestoreJournalEntry>): Set<String> = entries
        .flatMap { listOf(siblingRel(it.rel, it.backup), siblingRel(it.rel, it.staging)) }
        .toSet()

    /**
     * 「明确报告未能自动清理的残留」：存在同格式文件名但无任何归属日志证据的文件。
     * 只记录、不处置——处置（改名/删除/不打包）都需要归属证据。
     */
    private fun reportUntrustedResidue(booksDir: File, trustedRels: Set<String>) {
        booksDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val isResidueLike = matchBackupResidue(file) != null || matchStagingResidue(file)
            if (!isResidueLike) return@forEach
            val rel = relativePathOf(booksDir, file)
            if (rel in trustedRels) return@forEach
            AppLog.w(
                TAG,
                "发现疑似 crash 残留但无归属证据（可能是恰好同名的普通书籍文件）：" +
                    "已原样保留、导出照常打包，不自动改名或删除——$rel",
            )
        }
    }

    /** [relativePathOf] 的非抛出版本：文件不在 base 之下时返回 null，不猜测归属。 */
    private fun relativePathOrNull(base: File, file: File): String? = try {
        relativePathOf(base, file)
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * 计算 [child] 相对于 [base] 的相对路径（纯 File + 字符串，兼容 minSdk 24；
     * 不使用 NIO.2 的 Path#relativize，因为它需要 API 26）。Zip entry 统一正斜杠。
     */
    internal fun relativePathOf(base: File, child: File): String {
        val sep = File.separatorChar
        val baseAbs = base.absolutePath.trimEnd(sep)
        val childAbs = child.absolutePath.trimEnd(sep)
        require(childAbs == baseAbs || childAbs.startsWith(baseAbs + sep)) {
            "File $childAbs is not a descendant of $baseAbs"
        }
        if (childAbs == baseAbs) return ""
        return childAbs.substring(baseAbs.length + 1).replace("\\", "/")
    }

    private fun putTextEntry(zos: ZipOutputStream, name: String, text: String) {
        val entry = ZipEntry(name)
        zos.putNextEntry(entry)
        zos.write(text.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun putFileEntry(zos: ZipOutputStream, name: String, file: File) {
        val entry = ZipEntry(name).apply { time = file.lastModified() }
        zos.putNextEntry(entry)
        file.inputStream().use { it.copyTo(zos, BUF_SIZE) }
        zos.closeEntry()
    }

    /** 过滤 Zip entry 名中的 ZipSlip 片段（../、绝对路径、反斜杠）。 */
    internal fun sanitizeEntryName(raw: String): String =
        raw.replace("\\", "/")
            .split("/")
            .filter { it != ".." && it.isNotBlank() }
            .joinToString("/")

    companion object {
        private const val BUF_SIZE = 64 * 1024
        private const val NAME_BACKUP_JSON = "backup.json"
        private const val BOOKS_PREFIX = "books"
        private const val TAG = "LocalZipBackup"

        /**
         * 恢复归属日志（books 根下的工具元数据文件）：{dst 相对路径, backup 名,
         * staging 名} 列表。导出不打包、导入不落盘（防外来备份包伪造归属），仅由
         * [copyWithStaging] 写入、[recoverCrashResidues] 读取剪枝。
         */
        internal const val JOURNAL_NAME = ".cra-restore-journal.json"
    }
}
