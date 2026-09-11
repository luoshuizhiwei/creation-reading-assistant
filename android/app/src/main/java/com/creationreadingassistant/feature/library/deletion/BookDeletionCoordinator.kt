package com.creationreadingassistant.feature.library.deletion

import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.repository.TaxonomyRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** 撤销没有成功时的原因；分成枚举而不是塞进文案字符串，是为了让上层能分别测与分别措辞。 */
enum class DeletionUndoFailure {
    /**
     * 凭证不可用：已经撤销过、已被用户放弃、已过有效期，或进程重启后消失。
     * 重复撤销同样落在这里并且不改任何数据，因此是幂等的。
     */
    UNAVAILABLE,

    /** 恢复事务抛错。仓储层事务已回滚，数据保持删除后的状态，凭证仍在有效期内可重试。 */
    RESTORE_FAILED,
}

/** 一次撤销的结果。[restored] 为 true 只表示凭证被受理并写库成功，逐项细节看 [reports]。 */
data class DeletionUndoOutcome(
    val restored: Boolean,
    val reports: List<BookRestoreReport> = emptyList(),
    val failure: DeletionUndoFailure? = null,
    val error: Throwable? = null,
) {
    /** 真正写回了东西的书数量；空快照（删除前就没有资料/记录）的书不计入。 */
    val restoredBookCount: Int get() = reports.count { it.restoredAnything }

    /** 因「操作之后又被动过」而让路的行数，撤销必须如实说出来而不是假装全恢复了。 */
    val skippedNewerChangeCount: Int get() = reports.sumOf { it.skippedNewerChangeCount }

    /** 关系目标已消失而被跳过的关联条数（标签/分类/书单）。 */
    val skippedRelationTargetCount: Int get() = reports.sumOf { it.skippedRelationTargetIds.size }

    /** 有书的正文缓存没能随撤销回来，需要重新打开书籍重建。 */
    val contentPayloadDropped: Boolean get() = reports.any { it.contentPayloadDropped }

    companion object {
        val NOTHING_TO_UNDO = DeletionUndoOutcome(restored = false, failure = DeletionUndoFailure.UNAVAILABLE)
    }
}

/**
 * 删除与撤销的协调层 —— UI 只跟它打交道，不直接碰仓储和凭证登记处。
 *
 * 负责三件在仓储层不该做的事：
 * 1. 把一次入口操作（单本或批量）收敛成**一张**撤销凭证，让提示的数量与撤销范围一致；
 * 2. 撤销前从分类仓储解析当前仍活跃的关系目标，避免写回指向已删除标签/分类/书单的孤儿关系；
 * 3. 把恢复结果整理成可措辞的结构，区分「恢复了」「让路了」「正文要重建」。
 *
 * 凭证只存在于内存，进程重启即消失，因此不会在重启后渲染出失效的撤销入口。
 */
@Singleton
class BookDeletionCoordinator @Inject constructor(
    private val repository: BookRepository,
    private val taxonomyRepository: TaxonomyRepository,
    private val undoStore: DeletionUndoStore,
    private val policy: DeletionUndoPolicy,
) {

    /** 当前可撤销的提示，最新一张在最前；为空即不该显示任何撤销入口。 */
    val offers: StateFlow<List<DeletionUndoOffer>> get() = undoStore.offers

    /**
     * 撤销窗口秒数。确认框文案（「可在 N 秒内撤销」）与提示条倒计时共用这一个来源，
     * 改了策略不会让文案与实际窗口对不上。
     */
    val undoWindowSeconds: Int get() = ceil(policy.undoWindowMillis / 1000.0).toInt()

    fun remainingMillis(offer: DeletionUndoOffer): Long = undoStore.remainingMillis(offer)

    /** 用户主动关闭提示条：放弃撤销，不动任何数据。 */
    fun dismiss(credentialId: String) = undoStore.dismiss(credentialId)

    /** 倒计时结束时调用，保证过期凭证不会滞留在 offers 里。 */
    fun prune() = undoStore.prune()

    suspend fun deleteBook(bookId: String): BookDeletionCredential? = deleteBooks(listOf(bookId))

    /**
     * 删除整本资料（[DeletionScope.DELETE_BOOK]）并登记一张撤销凭证。
     *
     * 一次调用 = 一张凭证 = 一个撤销范围，所以批量入口提示「已删除 N 本」时，
     * 撤销恢复的正是这 N 本。捕获与写入在仓储层的同一个事务里，失败则整体回滚、
     * 不登记凭证，不会出现「删了一半还能撤销」。
     */
    suspend fun deleteBooks(bookIds: Collection<String>): BookDeletionCredential? {
        val ids = bookIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (ids.isEmpty()) return null
        val snapshots = repository.deleteBooksScoped(ids)
        return undoStore.publish(DeletionScope.DELETE_BOOK, snapshots)
    }

    /**
     * 撤销一次删除。
     *
     * 先 [DeletionUndoStore.findLive] 再写库、写成功才 consume：恢复事务失败时凭证还在，
     * 用户可以在剩余有效期内重试；成功之后凭证被取走，重复撤销只会拿到 UNAVAILABLE。
     */
    suspend fun undo(credentialId: String): DeletionUndoOutcome {
        val credential = undoStore.findLive(credentialId) ?: return DeletionUndoOutcome.NOTHING_TO_UNDO
        if (credential.snapshots.isEmpty()) {
            undoStore.consume(credentialId)
            return DeletionUndoOutcome(restored = true)
        }
        // 活跃关系目标必须在进入事务前解析：事务内读 Room Flow 会拿不到一致快照。
        val liveTargets = resolveLiveTargets()
        return try {
            val reports = repository.restoreDeletions(credential.snapshots, liveTargets)
            undoStore.consume(credentialId)
            DeletionUndoOutcome(restored = true, reports = reports)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            DeletionUndoOutcome(
                restored = false,
                failure = DeletionUndoFailure.RESTORE_FAILED,
                error = e,
            )
        }
    }

    private suspend fun resolveLiveTargets(): LiveRelationTargets = LiveRelationTargets(
        tagIds = taxonomyRepository.observeTags().first().map { it.id }.toSet(),
        categoryIds = taxonomyRepository.observeCategories().first().map { it.id }.toSet(),
        shelfIds = taxonomyRepository.observeShelves().first().map { it.id }.toSet(),
    )
}
