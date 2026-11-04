package com.creationreadingassistant.ui.navigation

import androidx.lifecycle.ViewModel
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationContract
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationState
import com.creationreadingassistant.feature.reader.navigation.SourceNavigationTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * J1-CROSSBOOK-v2：一次「待确认的临时查阅请求」。
 *
 * 请求只表示「用户想去 [destination] 看一眼」，**不代表会话已经成立**：目标书是否真实存在、
 * 正文是否可读要等阅读器给出真实加载结果。只有 [TemporaryReadingNavigationViewModel.commitPendingInspection]
 * 成功，临时会话（active + LIFO 返回栈）才会改变；失败/取消只清除本请求，原会话分毫不动。
 *
 * [requestId] 是本次请求的唯一标识：迟到的、已被新请求取代的、或已随书切换作废的结果，
 * 都会因 requestId 不符而被拒绝，绝不提交或回退到新会话。
 */
data class PendingTemporaryInspection(
    val requestId: Long,
    val destination: SourceNavigationTarget,
)

/**
 * 已提交请求身份记录的上界。临时返回栈本身有界（[SourceNavigationContract.DEFAULT_MAX_TEMPORARY_HISTORY]），
 * 记录只需覆盖当前会话中仍可能参与返回判定的 requestId；超出即淘汰最旧者，避免无界增长。
 */
private const val MAX_COMMITTED_REQUEST_HISTORY = 32

/**
 * 「临时查阅」会话协调器：把 [SourceNavigationContract] 的纯状态规约包装成应用导航生命周期的
 * 内存状态持有者，供后续 ReaderRoute / AppNavigation 消费。
 *
 * 唯一状态真源是 [SourceNavigationState]（经 [state] 暴露），所有变更都委托给
 * [SourceNavigationContract] 的纯函数。协调器自身不做坐标判定，也不持有 NavController，因此返回
 * 历史的真源是 [SourceNavigationState.temporaryReturnStack]，而不是 NavController 的 back stack
 * 数量。
 *
 * 生命周期语义：
 * - 跨 reader route 存活：本类不绑定任何单个 route；能否跨 route 存活由调用方如何 scope 决定。
 *   它只用内存，不写数据库、不写 SavedStateHandle。
 * - 进程重启后：新实例从空 [SourceNavigationState] 开始，临时栈消失；普通阅读位置不会被本协调器
 *   「伪造恢复」，只能由持久化层在阅读器真正打开时通过 [recordNormalReading] 重新注入。
 *
 * 坐标安全：方法只接受 [SourceNavigationTarget]（其构造器为 internal，唯一合法构造入口是
 * [SourceNavigationContract.target]，已拒绝空书籍 ID、负 offset 与半截章节坐标）；页号、LazyList
 * index、display offset、替换投影 offset 在 [com.creationreadingassistant.feature.reader.locator.ReaderLocator]
 * 类型中无对应字段，天然无法进入状态。
 */
class TemporaryReadingNavigationViewModel : ViewModel() {

    private val _state = MutableStateFlow(SourceNavigationState())

    /** 唯一状态真源。UI 可据此响应式派生（例如返回按钮的可见性 = 临时栈非空）。 */
    val state: StateFlow<SourceNavigationState> = _state.asStateFlow()

    private val _pendingInspection = MutableStateFlow<PendingTemporaryInspection?>(null)

    /**
     * 当前「待确认的临时查阅请求」；为 null 表示没有在途请求。
     *
     * 与 [state] 严格分离：请求在途期间 [state] 不会被改动，因此目标加载失败时**不需要回滚** ——
     * 原普通阅读位置与既有临时返回栈天然保持原样。这条不变量是 §四.2「失败后保留原会话」的实现方式。
     */
    val pendingInspection: StateFlow<PendingTemporaryInspection?> = _pendingInspection.asStateFlow()

    private var inspectionRequestSeq = 0L

    /**
     * 已**真正提交成功**的请求身份集合（缺陷 1 的判定真源）。只按 requestId 记录，不落任何
     * 目标值；有界且随普通阅读记录整体作废。见 [isRequestCommitted] / [markRequestCommitted]。
     */
    private val committedRequestIds = LinkedHashSet<Long>()

    /**
     * 登记一次临时查阅请求（只登记，不改会话）。返回本次请求 id，供后续提交/放弃时核对；
     * [destination] 为 null（无有效 source 坐标）时拒绝，返回 null。
     */
    fun requestTemporaryInspection(destination: SourceNavigationTarget?): Long? {
        destination ?: return null
        val requestId = ++inspectionRequestSeq
        _pendingInspection.value = PendingTemporaryInspection(requestId, destination)
        return requestId
    }

    /**
     * 目标**真实加载成功**后提交本次临时查阅：把当前 active 推入 LIFO 返回栈并切换到目的地。
     *
     * 仅当 [requestId] 仍是当前在途请求、且 [loadedBookId] 与请求目的地一致时才提交 ——
     * 迟到的、已作废的、或指向另一本书的结果一律拒绝（返回 false，状态不变）。
     * 目的地已是当前 active 时不重复压栈（保持既有去重语义）。
     */
    fun commitPendingInspection(requestId: Long, loadedBookId: String?): Boolean {
        val pending = _pendingInspection.value ?: return false
        if (pending.requestId != requestId) return false
        if (pending.destination.bookId != loadedBookId) return false
        _pendingInspection.value = null
        // 本次请求真正成功：记录其身份，供 ReaderRoute 判定「本 route 是否已提交」。
        markRequestCommitted(requestId)
        _state.update { state ->
            if (state.active == pending.destination) {
                state
            } else {
                SourceNavigationContract.beginTemporaryInspection(state, pending.destination)
            }
        }
        return true
    }

    /**
     * 目标加载失败/取消：仅清除本请求，**不改动会话与既有栈**（因此不存在需要回退的部分）。
     * 仅当 [requestId] 仍是当前在途请求时生效，返回是否真的清除了它。
     */
    fun abandonPendingInspection(requestId: Long): Boolean {
        val pending = _pendingInspection.value ?: return false
        if (pending.requestId != requestId) return false
        _pendingInspection.value = null
        return true
    }

    /**
     * 本 route 拥有的请求 [requestId] 是否**真正提交成功过**（缺陷 1 的关闭方式）。
     *
     * 判定只认**本次请求的成功身份**，绝不用目标值 / 旧 active / 旧栈成员 / 仅 bookId 代替：
     * - 重复请求栈内目标、重复旧 active 的**未提交**请求 → false（不得消费合法栈）；
     * - 失败后 pending 已清，但该请求身份从未成功提交 → false（仍知道本 route 未成功）；
     * - 只有 [commitPendingInspection] 真正返回 true 的那个 requestId → true。
     *
     * 身份记录是**有界**的（[MAX_COMMITTED_REQUEST_HISTORY]），并在记录普通阅读
     * （临时链被清空）时整体作废，避免无界增长。
     */
    fun isRequestCommitted(requestId: Long?): Boolean {
        if (requestId == null) return false
        return committedRequestIds.contains(requestId)
    }

    private fun markRequestCommitted(requestId: Long) {
        committedRequestIds.add(requestId)
        while (committedRequestIds.size > MAX_COMMITTED_REQUEST_HISTORY) {
            val eldest = committedRequestIds.iterator()
            eldest.next()
            eldest.remove()
        }
    }

    /**
     * 是否存在可返回目标。仅当临时查阅栈非空（存在一层临时跳转可回退）时为 true；
     * 普通阅读位置本身不构成「待返回的临时目标」，因此栈空时恒为 false。
     */
    val hasReturnableTarget: Boolean
        get() = _state.value.temporaryReturnStack.isNotEmpty()

    /**
     * 记录普通阅读位置。这是唯一更新可恢复位置、并主动结束临时查阅链的操作。
     * [target] 为 null（无有效 source 坐标）时拒绝，不进入状态。
     *
     * 记录普通阅读意味着用户已经真实落在普通阅读上，任何在途的临时查阅请求随之作废
     * （否则迟到的加载结果会把用户从新的普通阅读位置拖回旧请求的目的地）。
     */
    fun recordNormalReading(target: SourceNavigationTarget?) {
        target ?: return
        _pendingInspection.value = null
        // 普通阅读意味着临时查阅链结束：已提交请求身份随之整体作废，不跨会话残留。
        committedRequestIds.clear()
        _state.update { SourceNavigationContract.recordNormalReading(it, target) }
    }

    /**
     * 从当前精确 source 位置开始一次临时查阅：把当前 active 推入 LIFO 返回栈，跳到 [destination]。
     * 普通阅读位置保持不变；[destination] 为 null（无有效 source 坐标）时拒绝。
     */
    fun beginTemporaryInspection(destination: SourceNavigationTarget?) {
        destination ?: return
        _state.update { SourceNavigationContract.beginTemporaryInspection(it, destination) }
    }

    /** 按 LIFO 返回一层；临时栈为空时回退到普通阅读位置，不伪造近似位置。 */
    fun returnFromTemporaryInspection() {
        _state.update { SourceNavigationContract.returnFromTemporaryInspection(it) }
    }

    /**
     * 120-v1：结束当前阅读器导航会话（导航目标离开 reader 域时由 AppNavigation 调用）。
     *
     * 语义：清 [SourceNavigationState.active] 与残留临时返回栈、作废在途请求与已提交请求身份
     * （与 [recordNormalReading] 的作废口径一致——会话结束后迟到的加载/提交不得改写任何状态），
     * **保留** normalReading 持久指针。此后从阅读器外（如全局搜索）发起的临时查阅不再拥有
     * "返回阅读处"目标（[hasReturnableTarget]=false），返回交还导航回退到真实来源页；
     * 历史普通阅读指针仅在下次普通打开该书时经 [recordNormalReading] 重建会话。
     */
    fun endReaderSession() {
        _pendingInspection.value = null
        committedRequestIds.clear()
        _state.update { SourceNavigationContract.leaveReaderSession(it) }
    }
}