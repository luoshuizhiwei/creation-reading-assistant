package com.creationreadingassistant.feature.reader.pager

/**
 * 待处理翻页方向队列 —— S06-⑤「动画期间连续点按被吞」里的**纯策略**部分。
 *
 * 只负责四件事：入队、满则丢最新、反向作废未开始的积压、取出 / 放回队首。
 * 不碰动画、协程排空时序、帧快照、位图轮换与手势 —— 那些留在 [PageTurner] 里。
 * 之所以值得单独成为一个类型：这几条规则是可判定对错的策略，而它们原先埋在
 * composable 的局部函数里，只能在真机 instrumentation 上间接触发；抽出来后可直接用
 * JVM 测试钉死（见 `PendingTurnQueueTest`），而这台机器的 Compose instrumentation
 * 目前跑不起来（见 `docs/qa/2026-09-24-c-high-speed-test-diagnosis.md`）。
 *
 * **容量口径**：正在播放的那一个请求由调用方持有、不进本队列，所以 [capacity] = 3
 * 表达的是「1 个在播 + 最多 3 个排队」= 一次爆发最多推进 4 页。
 */
internal class PendingTurnQueue(val capacity: Int = PENDING_TURN_CAPACITY) {

    private val directions = ArrayDeque<Int>(capacity)

    val size: Int get() = directions.size

    val isEmpty: Boolean get() = directions.isEmpty()

    /**
     * 接受一次翻页请求。返回是否入队（`false` = 被丢弃：方向为 0，或队列已满）。
     *
     * - 方向 0 不是请求（外层的空槽值），直接拒收；
     * - 与队尾方向相反 ⇒ 先清空尚未开始的积压，只保留这一次反向意图；
     * - 已满 ⇒ 丢弃**最新**这一笔，绝不挤掉已排队的请求，也绝不影响在播动画。
     */
    fun enqueue(direction: Int): Boolean {
        if (direction == 0) return false
        val last = directions.lastOrNull()
        if (last != null && last != direction) directions.clear()
        if (directions.size >= capacity) return false
        directions.addLast(direction)
        return true
    }

    /** 取队首待处理方向；没有则 null。 */
    fun poll(): Int? = directions.removeFirstOrNull()

    /**
     * 把刚 poll 出来的方向放回队首（调用方发现被在播动画 / 新手势抢占时用）。
     *
     * 不加容量判断：它的契约就是「归还自己刚取走的那一个」，此时必有空位。
     */
    fun requeueFront(direction: Int) {
        directions.addFirst(direction)
    }

    /** 丢弃全部未开始的积压（翻页效果切换等会让旧请求失去意义的场景）。 */
    fun clear() {
        directions.clear()
    }
}

/**
 * 连点积压上限（1 个在播动画之外最多再排 3 个方向）。
 * 取 3 是为了覆盖真机上「手指抖动式三连点」，同时保证一次爆发不会在停手后
 * 继续自己翻很多页（翻页节奏由动画时长驱动，队列越长失控越久）。
 */
internal const val PENDING_TURN_CAPACITY = 3
