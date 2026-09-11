package com.creationreadingassistant.feature.library.deletion

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 会话内撤销凭证登记处。
 *
 * 刻意只存在于内存：需求明确不承诺重启后仍可撤销，因此这里没有落库，进程结束凭证即消失，
 * 重启后 [offers] 为空，UI 不会渲染出失效的撤销按钮。
 *
 * 积累是有界的：过期凭证在每次读写时被清理，同时存活的凭证数量不超过
 * [DeletionUndoPolicy.maxCredentials]，超出即淘汰最旧的一张。
 */
@Singleton
class DeletionUndoStore @Inject constructor(
    private val policy: DeletionUndoPolicy,
    private val clock: DeletionClock,
) {

    private val lock = Any()

    /** 最旧在前；淘汰与合并都基于这个顺序。 */
    private val credentials = ArrayDeque<BookDeletionCredential>()

    private val _offers = MutableStateFlow<List<DeletionUndoOffer>>(emptyList())

    /** 当前仍可撤销的凭证投影，最新的一张排在最前。 */
    val offers: StateFlow<List<DeletionUndoOffer>> = _offers.asStateFlow()

    fun nowMillis(): Long = clock.nowMillis()

    /**
     * 登记一次删除操作。
     *
     * [DeletionUndoPolicy.coalesceWindowMillis] 内的同范围删除会并入同一张凭证，
     * 这样既有入口里 `ids.forEach { deleteBook(it) }` 形式的批量循环也能一次性撤销，
     * 撤销范围与入口提示的数量对应。
     */
    fun publish(
        scope: DeletionScope,
        snapshots: List<BookDeletionSnapshot>,
    ): BookDeletionCredential = synchronized(lock) {
        val now = clock.nowMillis()
        pruneLocked(now)

        val mergeTarget = credentials.lastOrNull {
            it.scope == scope && now - it.updatedAtMillis <= policy.coalesceWindowMillis
        }
        if (mergeTarget != null) {
            val merged = mergeTarget.copy(
                // 合并会把多批快照聚到一张凭证上，预算必须在合并结果上重新收敛，
                // 否则单批合规、合并后超预算。
                snapshots = applyContentBudget(
                    mergeSnapshots(mergeTarget.snapshots, snapshots),
                    policy.maxRetainedContentChars,
                ),
                updatedAtMillis = now,
                expiresAtMillis = now + policy.undoWindowMillis,
            )
            credentials[credentials.indexOfLast { it.id == mergeTarget.id }] = merged
            publishOffersLocked(now)
            return@synchronized merged
        }

        val credential = BookDeletionCredential(
            id = newCredentialId(),
            scope = scope,
            createdAtMillis = now,
            updatedAtMillis = now,
            expiresAtMillis = now + policy.undoWindowMillis,
            snapshots = applyContentBudget(snapshots, policy.maxRetainedContentChars),
        )
        credentials.addLast(credential)
        while (credentials.size > policy.maxCredentials) {
            credentials.removeFirst()
        }
        publishOffersLocked(now)
        credential
    }

    /**
     * 查一张仍然有效的凭证，但不取走。
     *
     * 撤销先 peek 再写库、写成功才 [consume]：这样恢复事务失败时凭证还在有效期内，
     * 用户可以重试，而不是白白烧掉一次撤销机会。
     */
    fun findLive(id: String): BookDeletionCredential? = synchronized(lock) {
        val now = clock.nowMillis()
        pruneLocked(now)
        publishOffersLocked(now)
        credentials.lastOrNull { it.id == id && it.isLiveAt(now) }
    }

    /**
     * 取走一张凭证用于撤销：命中且未过期才返回，并从登记处移除。
     *
     * 返回 null 表示「无可撤销项」——凭证不存在、已过期，或已经被上一次撤销取走；
     * 取走即移除也让重复撤销天然幂等。
     */
    fun consume(id: String): BookDeletionCredential? = synchronized(lock) {
        val now = clock.nowMillis()
        pruneLocked(now)
        val index = credentials.indexOfLast { it.id == id }
        if (index < 0) {
            // 凭证已过期被 prune 清掉（或根本不存在）：offers 必须跟着刷新，
            // 否则界面上会留着一个点了也没反应的撤销条。
            publishOffersLocked(now)
            return@synchronized null
        }
        val credential = credentials.removeAt(index)
        publishOffersLocked(now)
        if (credential.isLiveAt(now)) credential else null
    }

    /** 用户主动放弃撤销（关闭提示条），不恢复任何数据。 */
    fun dismiss(id: String) {
        synchronized(lock) {
            val now = clock.nowMillis()
            credentials.removeAll { it.id == id }
            pruneLocked(now)
            publishOffersLocked(now)
        }
    }

    /** 丢弃全部凭证，用于退出登录/清空数据一类会让撤销失去意义的场景。 */
    fun clear() {
        synchronized(lock) {
            credentials.clear()
            _offers.value = emptyList()
        }
    }

    /** 清理过期凭证并刷新 [offers]；UI 倒计时结束时调用，保证提示条不会滞留。 */
    fun prune() {
        synchronized(lock) {
            val now = clock.nowMillis()
            pruneLocked(now)
            publishOffersLocked(now)
        }
    }

    fun latestOffer(): DeletionUndoOffer? = _offers.value.firstOrNull()

    fun remainingMillis(offer: DeletionUndoOffer): Long =
        (offer.expiresAtMillis - clock.nowMillis()).coerceAtLeast(0L)

    /** 当前存活凭证数量，用于验证「不无界积累」。 */
    fun liveCredentialCount(): Int = synchronized(lock) {
        pruneLocked(clock.nowMillis())
        credentials.size
    }

    private fun pruneLocked(now: Long) {
        credentials.removeAll { !it.isLiveAt(now) }
    }

    private fun publishOffersLocked(now: Long) {
        _offers.value = credentials
            .filter { it.isLiveAt(now) }
            .sortedByDescending { it.updatedAtMillis }
            .map { it.toOffer() }
    }

    /**
     * 同一本书只保留第一份非空快照。
     *
     * 重复删除同一本书时第二次捕获必然是空的（资料已被上一次删除），用空快照覆盖
     * 会让撤销丢失真正的恢复依据。
     */
    private fun mergeSnapshots(
        existing: List<BookDeletionSnapshot>,
        incoming: List<BookDeletionSnapshot>,
    ): List<BookDeletionSnapshot> {
        val byBookId = LinkedHashMap<String, BookDeletionSnapshot>()
        existing.forEach { byBookId[it.bookId] = it }
        incoming.forEach { snapshot ->
            val current = byBookId[snapshot.bookId]
            if (current == null || (current.isEmpty && !snapshot.isEmpty)) {
                byBookId[snapshot.bookId] = snapshot
            }
        }
        return byBookId.values.toList()
    }

    private fun BookDeletionCredential.toOffer() = DeletionUndoOffer(
        id = id,
        scope = scope,
        bookCount = bookCount,
        restorableBookCount = restorableBookCount,
        createdAtMillis = createdAtMillis,
        expiresAtMillis = expiresAtMillis,
    )
}
