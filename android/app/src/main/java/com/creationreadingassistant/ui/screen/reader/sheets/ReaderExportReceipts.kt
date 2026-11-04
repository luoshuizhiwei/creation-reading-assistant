package com.creationreadingassistant.ui.screen.reader.sheets

import android.net.Uri

/**
 * 最近一次真实 SAF 导出回执。
 *
 * 仅记录系统 `CreateDocument` 链路的**实际结果**（成功 URI / 用户取消 / 写入失败），
 * 不改变导出行为、不参与业务判定。存在的理由：导出链路的观察方（例如仪器）
 * 需要拿到系统真正授权的那一个 URI 与原始失败，而不是事后按目录差集重新拼装，
 * 或在写入失败时只看到 Toast、最终以超时冒充“没有结果”。
 *
 * 成功、取消、失败三者互斥：记录任一结果都会清掉其余两种，避免旧值复用或混淆。
 */
object ReaderExportReceipts {

    data class Receipt(
        val uri: Uri,
        val authority: String?,
        val completedAtMillis: Long,
    )

    data class Failure(
        val message: String,
        val cause: Throwable?,
        val failedAtMillis: Long,
    )

    private val lock = Any()
    private var latestReceipt: Receipt? = null
    private var latestFailure: Failure? = null
    private var cancelledAtMillis: Long? = null

    fun record(uri: Uri) = synchronized(lock) {
        latestReceipt = Receipt(uri, uri.authority, System.currentTimeMillis())
        latestFailure = null
        cancelledAtMillis = null
    }

    fun recordFailure(error: Throwable) = synchronized(lock) {
        latestReceipt = null
        latestFailure = Failure(
            message = error.message ?: error::class.java.name,
            cause = error,
            failedAtMillis = System.currentTimeMillis(),
        )
        cancelledAtMillis = null
    }

    fun recordCancelled() = synchronized(lock) {
        latestReceipt = null
        latestFailure = null
        cancelledAtMillis = System.currentTimeMillis()
    }

    fun latest(): Receipt? = synchronized(lock) { latestReceipt }

    fun latestFailure(): Failure? = synchronized(lock) { latestFailure }

    fun wasCancelled(): Boolean = synchronized(lock) { cancelledAtMillis != null }

    fun clear() = synchronized(lock) {
        latestReceipt = null
        latestFailure = null
        cancelledAtMillis = null
    }
}
