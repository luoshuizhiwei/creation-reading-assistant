package com.creationreadingassistant.feature.reader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * EPUB 同书文件互斥闸（CRA-ROLLBACK-WIP-RECONCILE-v3）。
 *
 * 为什么需要它：`books/epub/` 下的 `<id>.importing`（来源落盘暂存）、`<id>.repairing`
 * （替换暂存）、`<id>.backup`（旧副本备份）与 `<id>.epub`（正式内部副本）都由书的 id
 * 确定路径，且各写入阶段（EpubParser 的来源落位、EpubRepository 的替换/回滚）没有
 * 天然的调用方互斥 —— 多个 ViewModel 协程先后触发同书替换、或「打开未落盘的书」与
 * 「替换正文」并发时，会互相删除/覆盖对方的临时文件，甚至交替改写 target。
 * 单个协程内的 for-loop（批量导入）只保证协程内串行，不等于跨协程互斥。
 *
 * 合同：
 * - [EpubRepository.replaceStoredEpub] 的**全程**（守卫、fresh-source 解析、暂存/备份/
 *   提升落位/DAO/回滚）必须在 [withGate] 内执行；
 * - [EpubParser.parse] 的来源选择、落位、解析和失败清理全程在 [withGate] 内执行，
 *   让复用判断及清理权属与同书替换串行；已持闸调用方使用显式
 *   `parseUnderSwapGate`，禁止通过公共入口再入；
 * - [waiters] 暴露当前正在等闸/持闸的操作数，供受控时序并发测试观察（非业务状态）。
 *
 * 闸是进程级的：不同书的替换也会互相排队。替换是低频用户操作，用一把进程级闸换取
 * 「结构上不可能交叉写同一组文件」的最小正确性；读书热路径（reuse-existing）不受影响。
 */
internal object EpubSwapGate {

    private val mutex = Mutex()

    /** 当前正在等待或持有闸的操作数；仅供诊断与并发测试观察。 */
    private val _waiters = MutableStateFlow(0)
    val waiters: StateFlow<Int> get() = _waiters

    suspend fun <T> withGate(block: suspend () -> T): T {
        _waiters.value += 1
        try {
            return mutex.withLock { block() }
        } finally {
            _waiters.value -= 1
        }
    }
}
