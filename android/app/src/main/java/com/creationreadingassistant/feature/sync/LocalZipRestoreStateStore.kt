package com.creationreadingassistant.feature.sync

import android.content.Context
import com.creationreadingassistant.feature.log.AppLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 整包 ZIP 恢复的**持久**操作状态（N1-repair-v1）。
 *
 * 背景（N1 真实进程中断实证）：[LocalZipBackup.importZip] 是「先提交 DB 事务、再复制
 * 书籍文件」的两段式流程，进程在两段之间被杀会留下「DB 已是备份世界、文件仍是新旧混合」
 * 的状态。此前这段状态只存在于内存与逐文件 journal 里：**重启后没有任何地方能回答
 * 「上一次整包恢复到底做完了没有」**，于是
 *   - 用户看到的是「导入失败」但不知道是否需要重试；
 *   - 导出仍会照常产出这份混合状态（把不一致扩散到下一份备份）。
 *
 * 本类只做一件事：把「整次导入的身份 + 阶段」可靠地落在磁盘上，使**新实例**也能识别
 * 未完成状态。它**不**参与文件搬运，也不替代逐文件 journal——journal 记录的是单个目标
 * 文件的 in-flight 换入现场，本状态记录的是整次操作走到哪一段。
 *
 * 阶段语义（[LocalZipRestoreState.stage]）：
 * - [LocalZipRestoreState.STAGE_STARTED]：已开始，恢复未完成（数据库状态未知）。
 * - [LocalZipRestoreState.STAGE_DB_COMMITTED]：DB 已提交，书籍文件尚未复制完。
 *   **这正是 N1 实证的「混合状态」窗口**。
 * - [LocalZipRestoreState.STAGE_FILES_COPIED]：DB 与需要复制的文件都已完成。
 *   写到这里之后才会清除状态文件；若清除本身失败，残留的状态文件是**已完成**的，
 *   不应再被当成中断。
 * - [LocalZipRestoreState.STAGE_UNKNOWN]：状态文件损坏或不可读。**保守**方向：按
 *   「可能有未完成操作」处理（阻止导出并提示重试），但绝不据此删除任何书籍文件。
 *
 * 落盘位置：[STATE_FILE_NAME] 在 `filesDir` 根下，**不在** `books/` 树内——因此
 * 既不会被导出打进备份包，也不会被 [LocalZipBackup] 的 books 目录遍历/残留识别看到，
 * 外来 ZIP 也无法借它伪造归属。
 *
 * 持久化约定：写临时文件 + `flush` + `fd.sync()` + rename 落位。写失败一律上抛，
 * 由调用方 fail-closed 处理（不带着缺失的归属继续危险操作）。
 */
@Singleton
class LocalZipRestoreStateStore @Inject constructor() {

    private val lock = Any()

    /** 状态文件（`filesDir` 根下，不属于 books 树）。 */
    fun stateFile(context: Context): File = File(context.filesDir, STATE_FILE_NAME)

    /**
     * 读取当前状态。
     *
     * - 文件不存在 → `null`（从未有过未完成操作，或上次已正常完成并清除）。
     * - 文件存在且可解析 → 该状态。
     * - 文件存在但损坏/不可读 → [LocalZipRestoreState.STAGE_UNKNOWN] 的保守状态
     *   （**不**删除任何文件，只把「无法判定」如实上报）。
     */
    fun read(context: Context): LocalZipRestoreState? = synchronized(lock) {
        val file = stateFile(context)
        if (!file.exists()) return null
        if (!file.isFile) {
            // 路径被目录等非普通文件占用：无法判定，按「可能有未完成操作」保守处理。
            AppLog.w(TAG, "恢复状态路径被非普通文件占用（保守按未完成处理）：${file.path}")
            return LocalZipRestoreState.unknown(file.lastModified())
        }
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrElse {
            AppLog.w(TAG, "恢复状态文件不可读（按「可能有未完成操作」保守处理）：${it.message}")
            return LocalZipRestoreState.unknown(file.lastModified())
        }
        val parsed = runCatching {
            val obj = Json.parseToJsonElement(text).jsonObject
            val pendingTargets = obj["pendingTargets"]?.jsonObject?.mapNotNull { (k, v) ->
                v.jsonPrimitive.contentOrNull?.let { k to it }
            }?.toMap().orEmpty()
            val unresolvedTargets = obj["unresolvedTargets"]?.jsonObject?.mapNotNull { (k, v) ->
                v.jsonPrimitive.contentOrNull?.let { k to it }
            }?.toMap().orEmpty()

            LocalZipRestoreState(
                operationId = obj["operationId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                sourceLabel = obj["sourceLabel"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                backupFingerprint = obj["backupFingerprint"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                stage = obj["stage"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                startedAtMillis = obj["startedAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                updatedAtMillis = obj["updatedAtMillis"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                failureSummary = obj["failureSummary"]?.jsonPrimitive?.contentOrNull,
                pendingTargets = pendingTargets,
                unresolvedTargets = unresolvedTargets,
            )
        }.getOrElse {
            AppLog.w(TAG, "恢复状态文件残缺（按「可能有未完成操作」保守处理，不删除任何书籍文件）：${it.message}")
            return LocalZipRestoreState.unknown(file.lastModified())
        }
        if (parsed.operationId.isBlank() || parsed.stage.isBlank()) {
            AppLog.w(TAG, "恢复状态文件缺少必需字段（按「可能有未完成操作」保守处理）")
            return LocalZipRestoreState.unknown(file.lastModified())
        }
        return parsed
    }

    /**
     * 在**任何持久副作用之前**记录整次导入的开始。
     *
     * 上一次未完成的状态会被本次接管；若前序存在未完成目标，无论本次指纹是否相同，
     * 都将守护前序未完成目标（[LocalZipRestoreState.unresolvedTargets]），不允许不同备份的成功轻易消除前序未完成保护。
     * 落盘失败上抛：调用方必须 fail-closed，不得带着缺失的归属继续执行 DB 事务或文件换入。
     */
    fun begin(
        context: Context,
        sourceLabel: String,
        nowMillis: Long,
        backupFingerprint: String = "",
    ): LocalZipRestoreState = synchronized(lock) {
        val previous = read(context)
        var inheritedUnresolved = emptyMap<String, String>()
        if (previous != null && !previous.isComplete) {
            AppLog.w(
                TAG,
                "上一次整包恢复未完成（阶段=${previous.stage}，开始于 ${previous.startedAtMillis}，指纹=${previous.backupFingerprint}）；" +
                    "本次导入将守护并接管该状态",
            )
            // 继承前序所有未完成目标，防止不同备份静默消除前序未完成保护
            inheritedUnresolved = previous.unresolvedTargets + previous.pendingTargets
        }
        val state = LocalZipRestoreState(
            operationId = "zip-${nowMillis}-${System.nanoTime()}",
            sourceLabel = sourceLabel,
            backupFingerprint = backupFingerprint,
            stage = LocalZipRestoreState.STAGE_STARTED,
            startedAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            failureSummary = null,
            pendingTargets = emptyMap(),
            unresolvedTargets = inheritedUnresolved,
        )
        writeDurably(context, state)
        return state
    }

    /**
     * 在 DB 提交后记录阶段、备份内容指纹与待复制目标集合。
     * 落盘失败上抛：调用方必须 fail-closed。
     */
    fun markDbCommitted(
        context: Context,
        state: LocalZipRestoreState,
        backupFingerprint: String,
        pendingTargets: Map<String, String>,
        nowMillis: Long,
    ): LocalZipRestoreState = synchronized(lock) {
        val current = read(context)
        if (current != null && current.operationId != state.operationId) {
            throw IllegalStateException("操作状态已被覆盖（当前=${current.operationId}，期望=${state.operationId}）")
        }
        val remainingUnresolved = state.unresolvedTargets.filterKeys { it !in pendingTargets.keys }
        val next = state.copy(
            stage = LocalZipRestoreState.STAGE_DB_COMMITTED,
            backupFingerprint = backupFingerprint,
            pendingTargets = pendingTargets,
            unresolvedTargets = remainingUnresolved,
            updatedAtMillis = nowMillis,
            failureSummary = null,
        )
        writeDurably(context, next)
        return next
    }

    /**
     * 推进阶段并**持久化**。落盘失败上抛——调用方不得在无法记录阶段的情况下继续。
     */
    fun markStage(
        context: Context,
        state: LocalZipRestoreState,
        stage: String,
        nowMillis: Long,
    ): LocalZipRestoreState = synchronized(lock) {
        val current = read(context)
        if (current != null && current.operationId != state.operationId) {
            throw IllegalStateException("操作状态已被覆盖（当前=${current.operationId}，期望=${state.operationId}）")
        }
        val next = state.copy(stage = stage, updatedAtMillis = nowMillis, failureSummary = null)
        writeDurably(context, next)
        return next
    }

    /**
     * 记录失败摘要（best-effort）。**不改阶段**：阶段只能由 [markStage] 前进，
     * 失败记录不得把「已提交 DB」降级成「未开始」，否则重启后无法判断现场。
     */
    fun recordFailure(
        context: Context,
        state: LocalZipRestoreState,
        failure: Throwable,
        nowMillis: Long,
    ): LocalZipRestoreState? = synchronized(lock) {
        runCatching {
            val current = read(context)
            if (current != null && current.operationId != state.operationId) {
                AppLog.w(TAG, "记录失败跳过：状态已变更")
                return@runCatching current
            }
            val next = state.copy(
                updatedAtMillis = nowMillis,
                failureSummary = describe(failure),
            )
            writeDurably(context, next)
            next
        }.onFailure {
            AppLog.w(TAG, "恢复失败摘要落盘失败（现场仍按未完成识别）：${it.message}")
        }.getOrNull()
    }

    /**
     * 清除状态文件（仅在 [LocalZipRestoreState.STAGE_FILES_COPIED] 之后调用）。
     * 验证 operationId；如果提供了 [realBooksDir]，会严格核验 pendingTargets 和 unresolvedTargets 是否已就位；
     * 若仍有未完成目标，拒绝清除，确保不会误解禁保护。
     */
    fun clear(context: Context, operationId: String = "", realBooksDir: File? = null) {
        synchronized(lock) {
        val file = stateFile(context)
        if (!file.isFile) return
        val current = read(context) ?: return
        if (operationId.isNotBlank() && current.operationId != operationId) {
            AppLog.w(TAG, "恢复状态清除拒绝：operationId 不匹配（当前=${current.operationId}，调用方=$operationId）")
            return
        }
        if (realBooksDir != null) {
            val missingUnresolved = current.unresolvedTargets.filterKeys { rel ->
                val f = File(realBooksDir, rel)
                !matchesTarget(f, current.unresolvedTargets[rel].orEmpty())
            }
            if (missingUnresolved.isNotEmpty()) {
                AppLog.w(TAG, "恢复状态清除拒绝：仍有前序未完成目标内容不一致：${missingUnresolved.keys}")
                return
            }
            val missingPending = current.pendingTargets.filterKeys { rel ->
                val f = File(realBooksDir, rel)
                !matchesTarget(f, current.pendingTargets[rel].orEmpty())
            }
            if (missingPending.isNotEmpty()) {
                AppLog.w(TAG, "恢复状态清除拒绝：待修目标内容不一致：${missingPending.keys}")
                return
            }
        } else if (current.unresolvedTargets.isNotEmpty() || current.pendingTargets.isNotEmpty()) {
            AppLog.w(TAG, "恢复状态清除拒绝：包含未清空的待修目标且未验证磁盘就位")
            return
        }
        runCatching { file.delete() }
            .onFailure { AppLog.w(TAG, "恢复状态文件清除失败（残留的是已完成状态）：${it.message}") }
        }
    }

    /** 异常链摘要：主异常 + cause 链 + suppressed（恢复失败必须可观察）。 */
    internal fun describe(failure: Throwable): String {
        val builder = StringBuilder(failure.javaClass.simpleName)
        failure.message?.let { builder.append(": ").append(it) }
        var cause = failure.cause
        var depth = 0
        while (cause != null && depth < MAX_CAUSE_DEPTH) {
            builder.append(" ← ").append(cause.javaClass.simpleName)
            cause.message?.let { builder.append(": ").append(it) }
            cause = cause.cause
            depth++
        }
        failure.suppressed.forEach { sup ->
            builder.append(" [suppressed: ").append(sup.javaClass.simpleName)
            sup.message?.let { builder.append(": ").append(it) }
            builder.append(']')
        }
        return builder.toString()
    }

    private fun writeDurably(context: Context, state: LocalZipRestoreState) {
        val file = stateFile(context)
        file.parentFile?.mkdirs()
        val json = buildJsonObject {
            put("operationId", state.operationId)
            put("sourceLabel", state.sourceLabel)
            put("backupFingerprint", state.backupFingerprint)
            put("stage", state.stage)
            put("startedAtMillis", state.startedAtMillis.toString())
            put("updatedAtMillis", state.updatedAtMillis.toString())
            state.failureSummary?.let { put("failureSummary", it) }
            if (state.pendingTargets.isNotEmpty()) {
                put("pendingTargets", buildJsonObject {
                    state.pendingTargets.forEach { (k, v) -> put(k, v.toString()) }
                })
            }
            if (state.unresolvedTargets.isNotEmpty()) {
                put("unresolvedTargets", buildJsonObject {
                    state.unresolvedTargets.forEach { (k, v) -> put(k, v.toString()) }
                })
            }
        }.toString()
        val tmp = File(file.parentFile, "${file.name}.${state.operationId}-${System.nanoTime()}.tmp")
        try {
            FileOutputStream(tmp).use { fos ->
                fos.write(json.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
        } catch (e: java.io.IOException) {
            runCatching { tmp.delete() }
            throw java.io.IOException("恢复状态持久化失败（fail-closed：中止本次导入）", e)
        }
        if (!tmp.renameTo(file)) {
            // Windows 等平台对已存在目标 renameTo 返回 false：先移除旧状态再落位。
            // Android/POSIX 上第一次 rename 即原子替换，不会走到这里。
            // 在 synchronized(lock) 保护下完成，防止并发读取线程感知到空窗。
            val replaced = file.isFile && file.delete() && tmp.renameTo(file)
            if (!replaced) {
                runCatching { tmp.delete() }
                throw java.io.IOException("恢复状态落位失败（fail-closed：中止本次导入）：${file.path}")
            }
        }
    }

    companion object {
        private const val TAG = "LocalZipRestoreState"
        private const val MAX_CAUSE_DEPTH = 8

        /** 状态文件名（`filesDir` 根下，不在 books 树内，不参与导出与残留识别）。 */
        internal const val STATE_FILE_NAME = "zip-restore-state.json"

        private const val HASH_BUF_SIZE = 64 * 1024

        /**
         * 检查磁盘文件是否满足预期。
         *
         * N1-repair-v5：旧长度格式（v2 legacy）不能证明正文身份——长度相同不代表内容相同。
         * 仅 SHA-256 内容指纹可以证明。旧长度条目保留为内容未验证，返回 false 阻断收敛，
         * 直到可确认来源的重试建立 SHA 证明为止（反例2）。
         */
        internal fun matchesTarget(file: File, expected: String): Boolean {
            if (!file.isFile) return false
            if (isLegacyLength(expected)) return false
            return computeSha256(file) == expected
        }

        internal fun isLegacyLength(value: String): Boolean =
            value.isNotEmpty() && value.all { it.isDigit() } && value.length != 64

        /** 计算文件 SHA-256 内容指纹（N1-repair-v3：目标完成判定用内容一致性证明，不只靠长度）。 */
        internal fun computeSha256(file: File): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { fis ->
                val buf = ByteArray(HASH_BUF_SIZE)
                var n: Int
                while (fis.read(buf).also { n = it } > 0) {
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * 一次整包恢复的持久状态。见 [LocalZipRestoreStateStore] 的类注释。
 */
data class LocalZipRestoreState(
    val operationId: String,
    val sourceLabel: String,
    val stage: String,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val failureSummary: String? = null,
    val backupFingerprint: String = "",
    val pendingTargets: Map<String, String> = emptyMap(),
    val unresolvedTargets: Map<String, String> = emptyMap(),
) {
    /** 只有「DB 与需要复制的文件都已完成且所有未决目标已收敛」才算完成；其余一律视为未完成。 */
    val isComplete: Boolean get() = stage == STAGE_FILES_COPIED && unresolvedTargets.isEmpty() && pendingTargets.isEmpty()

    /** 状态不可判定（文件损坏/不可读）——保守按未完成处理，但区别于「确定中断」。 */
    val isUnknown: Boolean get() = stage == STAGE_UNKNOWN

    companion object {
        const val STAGE_STARTED = "started"
        const val STAGE_DB_COMMITTED = "db_committed"
        const val STAGE_FILES_COPIED = "files_copied"
        const val STAGE_UNKNOWN = "unknown"

        /** 状态文件损坏时的保守占位（`startedAtMillis` 用文件 mtime 近似）。 */
        internal fun unknown(fileModifiedMillis: Long): LocalZipRestoreState = LocalZipRestoreState(
            operationId = "unknown",
            sourceLabel = "",
            stage = STAGE_UNKNOWN,
            startedAtMillis = fileModifiedMillis,
            updatedAtMillis = fileModifiedMillis,
            failureSummary = "恢复状态文件损坏或不可读",
            backupFingerprint = "",
            pendingTargets = emptyMap(),
            unresolvedTargets = emptyMap(),
        )
    }
}
