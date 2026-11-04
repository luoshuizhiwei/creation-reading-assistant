package com.creationreadingassistant.feature.sync

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.CoroutineScopeModule.IODispatcher
import com.creationreadingassistant.feature.log.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
import java.security.MessageDigest
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
 * 2'. **整次操作的持久中断状态**（N1-repair-v1，2026-10-01）：逐文件 journal 只覆盖
 *    「单个目标文件的换入现场」，回答不了「上一次整包恢复到底做完了没有」。真实进程
 *    中断实证（N1）确认：DB 提交后、文件复制中被杀会留下混合状态，且**重启不会自动
 *    收敛**。因此 [importZip] 在任何持久副作用之前先用 [LocalZipRestoreStateStore]
 *    持久记录整次操作的身份与阶段（started → db_committed → files_copied），
 *    完成标记只在 DB 与需要复制的文件都完成后才关闭；任何一步的持久化失败都
 *    fail-closed 上抛，不带着缺失的归属继续。未完成状态下 [exportZip] **明确阻止**
 *    导出（避免把混合状态扩散进新备份）并提示重新选择同一份备份重试；重试复用既有
 *    journal 与 staging 复制，对同一 ZIP 幂等收敛，**不做无依据的整库回滚**。
 * 3. **与 JSON 备份一致**：backup.json 与 JsonBridge 导出的纯 JSON 完全相同，
 *    因此用户若只想快速迁移元数据（不含书源文件），继续用纯 JSON 即可；
 *    ZIP 是「连书一起搬走」的整包形态。
 */
@Singleton
class LocalZipBackup @Inject constructor(
    private val jsonBridge: JsonBridge,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val restoreStateStore: LocalZipRestoreStateStore = LocalZipRestoreStateStore(),
) {

    /** 导出与导入共享的服务层互斥边界（N1-repair-v2 漏洞 3 修复）。 */
    private val zipOperationMutex = Mutex()

    /** 供测试注入的测试挂钩与接缝 */
    internal var onExportCheckFinished: (suspend () -> Unit)? = null
    internal var onImportStarted: (suspend () -> Unit)? = null
    internal var deleteRecursivelyOperation: ((File) -> Boolean)? = null
    /** 测试接缝：注入 staging 残片删除行为（N1-repair-v3 反例4）。null 时走默认 File.delete()。 */
    internal var fileDeleteOperation: ((File) -> Boolean)? = null

    /**
     * 读取上一次整包恢复的持久状态（N1-repair-v1）：新实例据此识别「未完成」，
     * 不依赖内存提示。返回 `null` 表示从未有过未完成操作或上次已正常完成。
     */
    fun readRestoreState(context: Context): LocalZipRestoreState? = restoreStateStore.read(context)

    /** 通过 SAF 选择的 URI 导出 ZIP（backup.json + filesDir/books/ 原始文件）。 */
    suspend fun exportZip(context: Context, zipUri: Uri) = withContext(ioDispatcher) {
        zipOperationMutex.withLock {
            // N1-repair-v1：未完成的整包恢复意味着「DB 已是备份世界、书籍文件仍是新旧混合」。
            // 此时导出会把这份不一致扩散进下一份备份，因此**明确阻止**并提示先完成恢复；
            // 只阻止，不删除、不改名任何既有文件（无归属证据的文件一律保留）。
            val unfinished = restoreStateStore.read(context)?.takeIf { !it.isComplete }
            if (unfinished != null) {
                throw IllegalStateException(unfinished.blockingExportMessage())
            }
            onExportCheckFinished?.invoke()

            context.contentResolver.openOutputStream(zipUri)?.use { rawOs ->
                BufferedOutputStream(rawOs, BUF_SIZE).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        // 1. 先写 backup.json（元数据最小，通常 < 5MB，优先落盘）
                        val json = jsonBridge.exportToString(context)
                        putTextEntry(zos, NAME_BACKUP_JSON, json)

                        // 2. 遍历 booksDir 下所有文件，按相对路径写入
                        val booksDir = File(context.filesDir, "books")
                        if (booksDir.isDirectory) {
                            // S14 残留归口：导出前先按归属日志收敛可判定的 crash 残留
                            runCatching { recoverCrashResidues(booksDir) }
                                .onFailure {
                                    AppLog.w(TAG, "导出前 crash 残留恢复失败（保留现场继续导出）：${it.message}")
                                }
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
    }

    /** 从 SAF 选择的 ZIP 恢复：解到临时目录 → 校验 JSON → 入库 + 覆盖 books。 */
    suspend fun importZip(context: Context, zipUri: Uri) = withContext(ioDispatcher) {
        zipOperationMutex.withLock {
            onImportStarted?.invoke()
            val tmp = File(context.cacheDir, "zip-restore-${System.currentTimeMillis()}")
            var state: LocalZipRestoreState? = null
            var primary: Throwable? = null
            try {
                val previousState = restoreStateStore.read(context)

                // 0'. 在任何持久副作用之前，先把整次导入的身份与阶段可靠落盘。
                state = restoreStateStore.begin(
                    context = context,
                    sourceLabel = zipUri.toString(),
                    nowMillis = System.currentTimeMillis(),
                )

                // 0. crash residue 恢复：先按归属日志完成上一次被杀换入的现场
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

                val backupJsonHash = computeSha256(jsonFile)

                val realBooksDir = File(context.filesDir, "books")
                val allSrcBookFiles = if (tmpBooks.isDirectory) {
                    tmpBooks.walkTopDown().filter { it.isFile && relativePathOf(tmpBooks, it) != JOURNAL_NAME }.toList()
                } else emptyList()
                // N1-repair-v3/v4：目标完成判定用内容指纹（SHA-256），不只靠长度
                val allSrcTargets = allSrcBookFiles.associate {
                    relativePathOf(tmpBooks, it) to computeSha256(it)
                }

                // N1-repair-v3/v4：备份身份覆盖正文内容，同 backup.json 不同正文判为不同备份
                val contentFingerprint = allSrcTargets.entries
                    .sortedBy { it.key }
                    .joinToString(",") { "${it.key}:${it.value}" }
                val backupFingerprint = "$backupJsonHash|$contentFingerprint"

                var actualTargetsToCopy = emptyMap<String, String>()
                val effectiveSkipped = mutableSetOf<String>()
                var planCommitted = false

                val onPlanReady: (ImportMergeResult) -> Unit = { mergeResult ->
                    if (!planCommitted) {
                        planCommitted = true
                        // 严格较新路径：本地 revision/updatedAt 严格胜出者，绝对受保护！
                        val strictlyNewerPaths = mergeResult.strictlyNewerBookContentPaths.toSet()
                        val strictlyNewerRels = strictlyNewerPaths
                            .mapNotNull { relativePathOrNull(realBooksDir, File(it)) ?: it.takeIf { s -> !s.startsWith("/") } }
                            .toSet()

                        effectiveSkipped.clear()
                        effectiveSkipped.addAll(mergeResult.skippedBookContentPaths)

                        val equalRels = mergeResult.equalBookContentPaths
                            .mapNotNull { relativePathOrNull(realBooksDir, File(it)) ?: it.takeIf { s -> !s.startsWith("/") } }
                            .toSet()

                        // 前序未完成义务：unresolvedTargets 与 pendingTargets（如 A->B->A 场景继承的 A 目标）
                        val inheritedObligations = previousState?.let {
                            it.unresolvedTargets + it.pendingTargets
                        }.orEmpty()

                        allSrcTargets.forEach { (rel, srcHash) ->
                            val dstFile = File(realBooksDir, rel)
                            val isStrictlyNewer = rel in strictlyNewerRels || dstFile.absolutePath in strictlyNewerPaths
                            if (isStrictlyNewer) {
                                // 铁律 1（反例 1 守卫）：严格较新永不被旧正文覆盖！
                                // 绝不因空指纹或重试而解除保护！
                                return@forEach
                            }

                            // 关键规则（反例 2 & 3 守卫）：源内容与继承义务完全匹配才能补偿
                            val expectedObligation = inheritedObligations[rel]
                            // N1-repair-v6：义务是否被**本次源正文**满足。三种情况：
                            // - 无继承义务 → 不存在冲突（true）
                            // - legacy 长度义务 → 长度证据不足以证明正文身份，**永不算满足**
                            //   （不猜测原来源；继续保留阻断）
                            // - 可靠 SHA 义务 → 仅当源正文 SHA 与之完全一致
                            val obligationSatisfied = obligationSatisfiedBy(srcHash, expectedObligation)
                            val isEqualRel = rel in equalRels || dstFile.absolutePath in mergeResult.equalBookContentPaths
                            val isStartedEmptyFingerprint = previousState != null &&
                                !previousState.isComplete &&
                                previousState.backupFingerprint.isEmpty()
                            val isSameFingerprintRetry = previousState != null &&
                                !previousState.isComplete &&
                                previousState.backupFingerprint.isNotEmpty() &&
                                previousState.backupFingerprint == backupFingerprint
                            // 同一备份指纹重试：待修集合严格限定在 pendingTargets / unresolvedTargets 中，被 merge 保护的文件不在其中
                            val isSameFingerprintCandidate = isSameFingerprintRetry && rel in inheritedObligations.keys

                            // ── 统一冲突守卫（N1-repair-v6）──────────────────────────────
                            // 只要存在继承义务且源正文不能满足它，本次导入对该 rel 的**一切**
                            // 授权路径都不成立：equal、空指纹、最近一次指纹相同、路径相同，
                            // 都只证明「DB 版本相等 / 重复选了最近的包 / 目标路径相同」，
                            // **都不证明源正文就是被继承义务要求的那份正文**。
                            // 因此冲突目标既不得进入本轮复制集合，也不得据此剪掉旧义务。
                            // （v5 只把冲突挂在 equalAuthorized 上，其余 OR 仍可单独授权，
                            //   导致 A→B→B / A→坏ZIP→B / legacy→B→B 三条路径可绕过。）
                            val hasConflictingObligation = expectedObligation != null && !obligationSatisfied

                            // 补偿条件（受统一守卫约束）：
                            // 1. 继承义务已被源正文满足（补齐义务，如 A→B→A 回到 A）
                            // 2. 等版本（equal）且本地未就位
                            // 3. 同一备份指纹待修目标（在 pendingTargets / unresolvedTargets 中）且本地未就位
                            // 4. DB 提交后 markDbCommitted 未落盘现场（空指纹且非严格较新）且本地未就位
                            val sourceAuthorized = !hasConflictingObligation &&
                                (expectedObligation != null || isEqualRel ||
                                    isSameFingerprintCandidate || isStartedEmptyFingerprint)
                            val needCompensate = sourceAuthorized &&
                                (!dstFile.isFile || computeSha256(dstFile) != srcHash)

                            if (needCompensate) {
                                effectiveSkipped.remove(dstFile.absolutePath)
                                effectiveSkipped.remove(rel)
                            } else if (hasConflictingObligation) {
                                // 冲突目标显式留在跳过集合：即使 merge 把它判为 accepted
                                // （本地版本较低）也不得写入——否则同样会剪掉旧义务。
                                effectiveSkipped.add(dstFile.absolutePath)
                                effectiveSkipped.add(rel)
                            }
                        }

                        actualTargetsToCopy = allSrcTargets.filterKeys { rel ->
                            val dst = File(realBooksDir, rel)
                            val isProtected = rel in effectiveSkipped || dst.absolutePath in effectiveSkipped
                            !isProtected
                        }

                        // 2'. 在 DB 事务提交前（或 relaxed 桩兜底时）：持久化 db_committed 阶段、内容指纹与待修目标！
                        // 若落盘失败上抛，DB 事务整体回滚，不留脏数据（fail-closed）
                        state = restoreStateStore.markDbCommitted(
                            context = context,
                            state = state!!,
                            backupFingerprint = backupFingerprint,
                            pendingTargets = actualTargetsToCopy,
                            nowMillis = System.currentTimeMillis(),
                        )
                    }
                }

                // 2. 先导入 DB（整体事务，失败自动回滚）：
                // N1-v4 窄接缝：回调在 DB 事务提交前执行；区分接受、等版本、严格较新。
                // 在 DB 可能提交之前，可靠持久化备份身份与可执行计划；计划落盘失败时抛出异常，DB 事务不会提交，保证 fail-closed。
                jsonBridge.importFromString(context, jsonFile.readText(Charsets.UTF_8)) { mergeResult ->
                    onPlanReady(mergeResult)
                }

                // 针对 relaxed mockk 或未执行回调桩的防御性兜底：确保 markDbCommitted 无论如何均能落盘
                if (!planCommitted) {
                    onPlanReady(ImportMergeResult())
                }

                // 3. 复制书籍文件
                copyBookFiles(tmpBooks, realBooksDir, effectiveSkipped)

                // 3'. 验证目标确实完成（漏洞 1 核心守卫 + N1-repair-v3：内容一致性证明）
                for ((rel, expectedHash) in actualTargetsToCopy) {
                    val dst = File(realBooksDir, rel)
                    if (!dst.isFile || computeSha256(dst) != expectedHash) {
                        throw IllegalStateException("待修目标验证失败（正文未就位或内容不符）：$rel")
                    }
                }

                // 完成标记推进
                val committedState = state ?: throw IllegalStateException("恢复状态未初始化")
                val filesCopiedState = restoreStateStore.markStage(
                    context = context,
                    state = committedState.copy(pendingTargets = emptyMap()),
                    stage = LocalZipRestoreState.STAGE_FILES_COPIED,
                    nowMillis = System.currentTimeMillis(),
                )
                state = filesCopiedState
                restoreStateStore.clear(context, filesCopiedState.operationId, realBooksDir)
            } catch (e: Throwable) {
                primary = e
                throw e
            } finally {
                // 漏洞 5 修复：先清理临时目录并准确检查 deleteRecursively 返回值
                var cleanupFailure: Throwable? = null
                if (tmp.exists()) {
                    val deleteFunc = deleteRecursivelyOperation ?: { f: File -> f.deleteRecursively() }
                    val deleted = runCatching { deleteFunc(tmp) }.getOrElse {
                        cleanupFailure = it
                        false
                    }
                    if (!deleted && cleanupFailure == null) {
                        cleanupFailure = java.io.IOException("临时目录删除失败（deleteRecursively 返回 false）：${tmp.path}")
                    }
                }

                // 异常合并：保全主异常与清理异常
                val effectiveError: Throwable?
                if (primary != null) {
                    if (cleanupFailure != null && primary !== cleanupFailure) {
                        primary.addSuppressed(cleanupFailure)
                    }
                    effectiveError = primary
                } else {
                    effectiveError = cleanupFailure
                }

                // 在异常合并完成后，将包含 suppressed 链的完整摘要落盘
                if (effectiveError != null) {
                    val recorded = state
                    if (recorded != null) {
                        runCatching {
                            restoreStateStore.recordFailure(context, recorded, effectiveError, System.currentTimeMillis())
                        }
                    }
                }

                if (primary == null && cleanupFailure != null) {
                    throw IllegalStateException(
                        "整包恢复已完成，但临时目录清理失败，已在 cacheDir 留下残留：${tmp.path}",
                        cleanupFailure,
                    )
                }
            }
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(BUF_SIZE)
            var n: Int
            while (fis.read(buf).also { n = it } > 0) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 义务是否被源正文满足（N1-repair-v6 统一守卫的唯一判定入口）。
     *
     * - 空义务字符串（不应出现）→ 不满足；
     * - legacy 长度义务 → **不满足**：长度证据不足以证明正文身份，不猜测原来源；
     * - 可靠 SHA-256 义务 → 仅当源正文指纹完全一致。
     */
    private fun obligationSatisfiedBy(srcHash: String, expected: String?): Boolean = when {
        expected == null -> true
        expected.isEmpty() -> false
        LocalZipRestoreStateStore.isLegacyLength(expected) -> false
        else -> srcHash == expected
    }

    /** 未完成状态下阻止导出时的说明文案（含阶段与重试指引）。 */
    private fun LocalZipRestoreState.blockingExportMessage(): String {
        val whenText = if (startedAtMillis > 0L) "（开始于 $startedAtMillis）" else ""
        val cause = failureSummary?.let { "；上次失败：$it" } ?: ""
        return "上次整包恢复未完成$whenText，阶段=$stage$cause。" +
            "当前数据库与书籍文件可能不一致，已阻止导出以免把混合状态写进新备份。" +
            "请先重新选择同一份整包备份完成恢复（我的 → 数据与存储 → 导入整包备份），再导出。"
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
    private fun copyBookFiles(tmpBooks: File, realBooksDir: File, skippedPaths: Set<String>) {
        if (!tmpBooks.isDirectory) return
        realBooksDir.mkdirs()
        val protectedRels = skippedPaths
            .mapNotNull { relativePathOrNull(realBooksDir, File(it)) ?: it.takeIf { s -> !s.startsWith("/") } }
            .toSet()
        tmpBooks.walkTopDown().forEach { src ->
            val rel = relativePathOf(tmpBooks, src)
            val dst = File(realBooksDir, rel)
            if (src.isDirectory) {
                dst.mkdirs()
            } else if (src.isFile) {
                // 外来 ZIP 不得借 books/.cra-restore-journal.json 注入或覆盖本地归属日志
                if (rel == JOURNAL_NAME) return@forEach
                if (rel in protectedRels || dst.absolutePath in skippedPaths) return@forEach
                dst.parentFile?.mkdirs()
                copyWithStaging(src, dst, rel, realBooksDir)
            }
        }
    }

    /**
     * 单个书源文件的受保护复制：
     * 关键防御（漏洞 4 修复）：在实际创建/写入 staging 之前，必须先将归属记录落入 journal（verified = false）！
     * 写入并校验长度通过后更新为 verified = true；发生中断时未校验的 staging 绝不换入。
     */
    private fun copyWithStaging(src: File, dst: File, rel: String, booksDir: File) {
        if (dst.isDirectory) {
            throw IllegalStateException("书源文件替换失败：目标位置被目录占用（已原样保留）：$rel")
        }
        val staging = File(dst.parentFile, "${dst.name}.restore-${System.nanoTime()}.staging")
        val backup = File(dst.parentFile, "${dst.name}.restore-backup-${System.nanoTime()}")

        // 漏洞 4 修复：创建 staging 之前，必须在归属日志中可靠记录归属（verified = false）！
        val initialEntry = RestoreJournalEntry(
            rel = rel,
            backup = backup.name,
            staging = staging.name,
            verified = false,
        )
        journalAdd(booksDir, initialEntry)

        var verified = false
        try {
            staging.outputStream().use { out ->
                src.inputStream().use { input -> fileCopyOperation(input, out) }
            }
            if (staging.length() != src.length()) {
                throw IllegalStateException("书源文件 staging 校验失败（长度与源不一致）：$rel")
            }

            verified = true
            val verifiedEntry = initialEntry.copy(verified = true)
            journalUpdate(booksDir, verifiedEntry)

            if (staging.renameTo(dst)) {
                journalRemove(booksDir, verifiedEntry)
                return
            }
            if (!dst.exists()) {
                journalRemove(booksDir, verifiedEntry)
                throw IllegalStateException("书源文件替换失败（目标不存在，无旧文件可保护）：$rel")
            }
            // Windows 等平台对已存在目标 renameTo 返回 false：走保护性 backup 换入。
            if (!dst.renameTo(backup)) {
                journalRemove(booksDir, verifiedEntry)
                throw IllegalStateException("书源文件替换失败：旧目标移入保护性 backup 失败（旧目标原样保留）：$rel")
            }
            try {
                if (!staging.renameTo(dst)) {
                    throw IllegalStateException("书源文件替换失败：新文件落位失败：$rel")
                }
            } catch (e: IllegalStateException) {
                if (backup.renameTo(dst)) {
                    journalRemove(booksDir, verifiedEntry)
                    throw e
                }
                throw IllegalStateException(
                    "书源文件替换失败，且旧目标自动恢复失败；保护性 backup 已保留：${backup.path}",
                    e,
                )
            }
            if (!backup.delete()) {
                throw IllegalStateException("书源文件已替换，但保护性 backup 清理失败，已按原样保留：${backup.path}")
            }
            journalRemove(booksDir, verifiedEntry)
        } finally {
            val stagingDeleted = if (staging.exists()) {
                val deleteFunc = fileDeleteOperation ?: { f: File -> f.delete() }
                runCatching { deleteFunc(staging) }.getOrDefault(false)
            } else true

            if (!verified) {
                if (stagingDeleted && !staging.exists()) {
                    runCatching { journalRemove(booksDir, initialEntry) }
                } else {
                    AppLog.w(TAG, "未校验 staging 删除失败，保留归属日志以防止残片失去归属：${staging.path}")
                }
            }
        }
    }

    // ── crash residue 恢复与识别（S14 残留归属收口，2026-09-28）───────────

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
     * [backup]/[staging] 是 dst 同目录下的文件名。[verified] 标明 staging 是否已通过长度校验。
     */
    private data class RestoreJournalEntry(
        val rel: String,
        val backup: String,
        val staging: String,
        val verified: Boolean = false,
    )

    private data class RawJournal(
        val validEntries: List<RestoreJournalEntry>,
        val allElements: List<JsonElement>,
    )

    private fun readRawJournal(booksDir: File): RawJournal {
        val file = File(booksDir, JOURNAL_NAME)
        if (!file.isFile) return RawJournal(emptyList(), emptyList())
        val elements = runCatching {
            Json.parseToJsonElement(file.readText()).jsonArray
        }.onFailure {
            AppLog.w(TAG, "恢复归属日志残缺（进程可能死于写入中途），按无归属证据处理：同格式文件全部保留原名")
        }.getOrNull() ?: return RawJournal(emptyList(), emptyList())

        val valid = mutableListOf<RestoreJournalEntry>()
        elements.forEach { elem ->
            val obj = elem.jsonObject
            val entry = RestoreJournalEntry(
                rel = obj["rel"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                backup = obj["backup"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                staging = obj["staging"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                verified = obj["verified"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
            ).validated()
            if (entry != null) {
                valid.add(entry)
            } else {
                AppLog.w(TAG, "归属日志条目无效，按无证据处理（文件保留原名）：$elem")
            }
        }
        return RawJournal(valid, elements)
    }

    /**
     * 读取并校验归属日志。任何异常形态都按「无证据」处理（返回已有有效条目或空）：
     */
    private fun readJournal(booksDir: File): List<RestoreJournalEntry> =
        readRawJournal(booksDir).validEntries

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

    private fun writeElements(booksDir: File, elements: List<JsonElement>) {
        val json = JsonArray(elements)
        runCatching { File(booksDir, JOURNAL_NAME).writeText(json.toString()) }
            .onFailure { AppLog.w(TAG, "恢复归属日志写入失败（若进程被杀，现场将按无证据保留）：${it.message}") }
    }

    private fun writeJournal(booksDir: File, remainingEntries: List<RestoreJournalEntry>) {
        runCatching {
            val raw = readRawJournal(booksDir)
            val remainingKeys = remainingEntries.map { it.rel to it.staging }.toSet()
            val initialKeys = raw.validEntries.map { it.rel to it.staging }.toSet()
            val removedKeys = initialKeys - remainingKeys
            val filteredElements = raw.allElements.filterNot { elem ->
                val obj = elem.jsonObject
                val rel = obj["rel"]?.jsonPrimitive?.contentOrNull
                val staging = obj["staging"]?.jsonPrimitive?.contentOrNull
                (rel to staging) in removedKeys
            }
            writeElements(booksDir, filteredElements)
        }.onFailure { AppLog.w(TAG, "归属日志重写失败：${it.message}") }
    }

    private fun writeElementsDurably(booksDir: File, elements: List<JsonElement>) {
        val json = JsonArray(elements)
        val journalFile = File(booksDir, JOURNAL_NAME)
        FileOutputStream(journalFile).use { fos ->
            fos.write(json.toString().toByteArray(Charsets.UTF_8))
            fos.flush()
            fos.fd.sync()
        }
    }

    /**
     * 追加一条 in-flight 记录（**fail-closed**）
     */
    private fun journalAdd(booksDir: File, entry: RestoreJournalEntry) {
        try {
            val raw = readRawJournal(booksDir)
            val entryObj = buildJsonObject {
                put("rel", entry.rel)
                put("backup", entry.backup)
                put("staging", entry.staging)
                put("verified", entry.verified.toString())
            }
            writeElementsDurably(booksDir, raw.allElements + entryObj)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("归属日志持久化失败（fail-closed：中止换入，旧目标原样保留）", e)
        }
    }

    /**
     * 更新一条记录（如由 verified=false 更新为 verified=true）
     */
    private fun journalUpdate(booksDir: File, entry: RestoreJournalEntry) {
        try {
            val raw = readRawJournal(booksDir)
            val newObj = buildJsonObject {
                put("rel", entry.rel)
                put("backup", entry.backup)
                put("staging", entry.staging)
                put("verified", entry.verified.toString())
            }
            val updated = raw.allElements.map { elem ->
                val obj = elem.jsonObject
                if (obj["rel"]?.jsonPrimitive?.contentOrNull == entry.rel &&
                    obj["staging"]?.jsonPrimitive?.contentOrNull == entry.staging
                ) {
                    newObj
                } else {
                    elem
                }
            }
            writeElementsDurably(booksDir, updated)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("归属日志更新持久化失败", e)
        }
    }

    /** 剪枝一条已解析记录；保留其他原始元素（包括无效条目，不破坏原日志）。 */
    private fun journalRemove(booksDir: File, entry: RestoreJournalEntry) {
        runCatching {
            val raw = readRawJournal(booksDir)
            val remaining = raw.allElements.filterNot { elem ->
                val obj = elem.jsonObject
                obj["rel"]?.jsonPrimitive?.contentOrNull == entry.rel &&
                    obj["staging"]?.jsonPrimitive?.contentOrNull == entry.staging
            }
            writeElements(booksDir, remaining)
        }.onFailure { AppLog.w(TAG, "归属日志剪枝失败（陈旧条目将由下次恢复收敛）：${it.message}") }
    }

    /** [rel] 同目录下的兄弟文件相对路径。 */
    private fun siblingRel(rel: String, fileName: String): String {
        val parent = rel.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parent.isEmpty()) fileName else "$parent/$fileName"
    }

    /**
     * 恢复 crash residues。
     * 漏洞 4 防御：对于 verified == false 的条目（半写中断产生），绝不换入为 dst，直接清理未校验 staging 并剪枝！
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
                // 漏洞 4 关键防御：未通过长度校验的半写 staging 绝不能换入为 dst！
                // N1-repair-v3：delete 返回 false 时保留归属条目——staging 残片不被剪掉，
                // 仍在 stagingRels 中被导出过滤，不会当普通资产打包（反例4）。
                if (!entry.verified) {
                    if (stagingFile.exists()) {
                        val deleteFunc = fileDeleteOperation ?: { f: File -> f.delete() }
                        if (!deleteFunc(stagingFile)) {
                            AppLog.w(TAG, "半写 staging 删除失败（保留归属记录，防止残片被误当普通资产）：${stagingFile.path}")
                            continue
                        }
                    }
                    remaining.remove(entry)
                    continue
                }
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
                    completeInterruptedSwapIn(dst, backupFile, stagingFile, rel)
                }
                if (dst.isFile) {
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
                    remaining.remove(entry)
                } else {
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
