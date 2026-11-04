package com.creationreadingassistant.data.repository

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicLong

/**
 * CRA-READER-DISPATCH-ORDER-MINIMAL-v6：阅读器进度保存的**进程内**事件派发顺序。
 *
 * 为什么需要它：阅读器保存链是无序并发（`ReaderViewModel.io` = `viewModelScope.launch(ioDispatcher)`），
 * 而 `mergeReaderProgress` 无条件以 incoming 覆盖位置。若保存事件在异步 IO 队列中乱序到达存储，
 * 较早派发的旧进度可能在较晚派发的新进度之后才落库，导致较新进度被覆盖回旧位置。
 *
 * 判据原则：
 * - 不能用墙钟（系统校时可回拨，会把合法新保存误判为迟到）；
 * - 不能用「进度只能增长」（用户会主动回读、跳到目录/搜索命中处）；
 * - 不能用库里的 `updated_at`（那是合并时刻，不是事件派发先后）。
 * 唯一可靠的判据是**保存事件在业务派发边界同步取到的单调序号**。
 *
 * 16-v6 收敛设计（最小实施）：
 * - 移除未有真实可达路径支持的 session 身份层（ViewModel 生命周期不等于阅读区间，
 *   旧 VM 存活且返回时合法新事件若带旧 session 会被误拒）；
 * - 仅保留进程内全局单调递增的 [next] 事件序号、串行化互斥锁与每本书最近已接受序号；
 * - 迟到旧 IO 事件（order < last）丢弃；新事件（order >= last）执行写操作并记账；
 * - 异常向上传播，finally 释放互斥锁。
 */
internal object ReaderProgressSaveOrder {

    private val orderCounter = AtomicLong(0L)
    private val mutex = Mutex()

    /** 每本书最近一次**已接受**的 order。 */
    private val accepted = HashMap<String, Long>()

    /** 在保存事件的产生点（业务派发边界）取事件序号（进程内单调递增）。 */
    fun next(): Long = orderCounter.incrementAndGet()

    /**
     * 串行化「判定 + 读改写 + 记账」，并在判定处丢弃迟到事件。
     * 仅当 [order] 不早于本书已接受记录时才执行 [write] 并记账。
     */
    suspend fun accept(bookId: String, order: Long, write: suspend () -> Unit) {
        mutex.lock()
        try {
            val last = accepted[bookId]
            if (last != null && order < last) return
            write()
            accepted[bookId] = order
        } finally {
            mutex.unlock()
        }
    }
}

