package com.creationreadingassistant.feature.library.deletion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 删除/撤销的 UI 侧 ViewModel —— 给没有自带协调器的入口（阅读历史等）用。
 *
 * 所有入口共用同一个 [BookDeletionCoordinator] 与 [DeletionUndoStore] 单例，因此
 * 在书架删的书切到阅读历史仍然能撤销，提示不会出现「换了页面就失效」的割裂。
 */
@HiltViewModel
class DeletionUndoViewModel @Inject constructor(
    private val coordinator: BookDeletionCoordinator,
) : ViewModel() {

    val offers: StateFlow<List<DeletionUndoOffer>> = coordinator.offers

    /** 确认框文案用：告诉用户实际有多少秒可撤销。 */
    val undoWindowSeconds: Int get() = coordinator.undoWindowSeconds

    fun remainingMillis(offer: DeletionUndoOffer): Long = coordinator.remainingMillis(offer)

    fun dismiss(credentialId: String) = coordinator.dismiss(credentialId)

    /** 提示条倒计时归零时调用，让过期凭证立刻从 offers 里消失。 */
    fun prune() = coordinator.prune()

    fun deleteBooks(
        bookIds: Collection<String>,
        onResult: (Boolean) -> Unit = {},
    ) = viewModelScope.launch {
        // 不用 runCatching：它会把协程取消也吞成「删除失败」，让已经取消的作用域继续跑。
        val ok = try {
            coordinator.deleteBooks(bookIds) != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            false
        }
        onResult(ok)
    }

    fun deleteBook(bookId: String, onResult: (Boolean) -> Unit = {}) =
        deleteBooks(listOf(bookId), onResult)

    fun undo(credentialId: String, onResult: (DeletionUndoOutcome) -> Unit) = viewModelScope.launch {
        onResult(coordinator.undo(credentialId))
    }
}
