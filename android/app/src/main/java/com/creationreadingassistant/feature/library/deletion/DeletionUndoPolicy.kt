package com.creationreadingassistant.feature.library.deletion

import java.util.UUID

/** 撤销凭证的有效期与容量上限；集中一处，便于测试用短窗口验证过期行为。 */
data class DeletionUndoPolicy(
    /** 凭证有效期。超时后撤销入口必须消失，且 consume 会拒绝执行。 */
    val undoWindowMillis: Long = 12_000L,
    /**
     * 合并窗口：在此时间内连续发生的同范围删除并入同一张凭证。
     *
     * 批量删除在多个既有入口是 `ids.forEach { deleteBook(it) }` 形式的循环调用，
     * 合并后才能让「撤销」的范围与入口提示的数量一致，而不是只恢复最后一本。
     */
    val coalesceWindowMillis: Long = 1_500L,
    /** 同时保留的凭证上限，防止会话内无界积累。超出时淘汰最旧的。 */
    val maxCredentials: Int = 4,
    /**
     * 单张凭证最多保留多少正文缓存字符（reader_preview + epub_json）。
     *
     * 正文缓存是可重建的派生数据，但整批大书全部驻留内存会有压力；超预算时丢弃载荷，
     * 撤销仍恢复资料与阅读数据，只是正文缓存需要重新打开书籍重建。
     */
    val maxRetainedContentChars: Long = 4_000_000L,
) {
    init {
        require(undoWindowMillis > 0) { "undoWindowMillis 必须为正数" }
        require(coalesceWindowMillis >= 0) { "coalesceWindowMillis 不能为负" }
        require(maxCredentials > 0) { "maxCredentials 必须为正数" }
        require(maxRetainedContentChars >= 0) { "maxRetainedContentChars 不能为负" }
    }
}

/** 时钟抽象，让过期/合并逻辑可在 JVM 测试里精确推进。 */
fun interface DeletionClock {
    fun nowMillis(): Long
}

/** 会话内的删除撤销凭证：一次删除操作 + 它影响到的全部书籍快照。 */
data class BookDeletionCredential(
    val id: String,
    val scope: DeletionScope,
    val createdAtMillis: Long,
    /** 最近一次并入快照的时刻；合并窗口从这里算，批量循环进行中不会中途断开。 */
    val updatedAtMillis: Long,
    val expiresAtMillis: Long,
    val snapshots: List<BookDeletionSnapshot>,
) {
    val bookIds: List<String> get() = snapshots.map { it.bookId }
    val bookCount: Int get() = snapshots.size

    fun isLiveAt(nowMillis: Long): Boolean = nowMillis < expiresAtMillis

    fun remainingMillis(nowMillis: Long): Long = (expiresAtMillis - nowMillis).coerceAtLeast(0L)

    /** 快照里确实有东西可恢复的书数量；空快照的书只影响提示措辞，不影响撤销可用性。 */
    val restorableBookCount: Int get() = snapshots.count { !it.isEmpty }
}

/** 供 UI 渲染的最小投影：只暴露展示与倒计时需要的字段，不泄漏快照内容。 */
data class DeletionUndoOffer(
    val id: String,
    val scope: DeletionScope,
    val bookCount: Int,
    val restorableBookCount: Int,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
)

internal fun newCredentialId(): String = "del-${UUID.randomUUID()}"
