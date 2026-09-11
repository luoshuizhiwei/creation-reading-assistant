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

    /**
     * 是否存在可返回目标。仅当临时查阅栈非空（存在一层临时跳转可回退）时为 true；
     * 普通阅读位置本身不构成「待返回的临时目标」，因此栈空时恒为 false。
     */
    val hasReturnableTarget: Boolean
        get() = _state.value.temporaryReturnStack.isNotEmpty()

    /**
     * 记录普通阅读位置。这是唯一更新可恢复位置、并主动结束临时查阅链的操作。
     * [target] 为 null（无有效 source 坐标）时拒绝，不进入状态。
     */
    fun recordNormalReading(target: SourceNavigationTarget?) {
        target ?: return
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
}