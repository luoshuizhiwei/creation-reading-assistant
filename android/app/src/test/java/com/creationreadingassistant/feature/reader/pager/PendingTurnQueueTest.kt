package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PendingTurnQueue] 的策略级 JVM 测试（S06-⑤ 的可脱机防线）。
 *
 * 每条用例都对应一种**具体的错误实现**：如果只断言「不崩 / 数量差不多」，
 * 把「满时丢最新」写成「满时丢最旧」、把「反向替换」写成「反向取消」都能蒙混过关。
 * 方向值这里刻意用 7/9 这类任意非零整数 —— 队列只关心「与 0 不同 + 是否同向」，
 * 不合法方向由调用方（点按/手势入口）负责，不在本策略职责内。
 */
class PendingTurnQueueTest {

    @Test
    fun capacityIsThreeUnstartedRequests() {
        // 语义钉子：正在播放的那一个请求由调用方持有，不进队列 ⇒ 3 = 最多「1 在播 + 3 排队」。
        assertEquals(3, PendingTurnQueue().capacity)
        assertEquals(PENDING_TURN_CAPACITY, PendingTurnQueue().capacity)
    }

    @Test
    fun sameDirectionTapsAllQueueUpWithinCapacity() {
        val q = PendingTurnQueue()
        assertTrue(q.enqueue(1))
        assertTrue(q.enqueue(1))
        assertTrue(q.enqueue(1))
        assertEquals(3, q.size)
        // FIFO：一次点按推进一页，顺序就是点按顺序。
        assertEquals(listOf(1, 1, 1), listOf(q.poll(), q.poll(), q.poll()))
        assertNull(q.poll())
    }

    /** 证伪「满时丢最旧」（那种实现会接受第 4 笔并把队首挤掉）。 */
    @Test
    fun overflowDropsTheNewestRequestAndKeepsTheBacklogIntact() {
        val q = PendingTurnQueue()
        repeat(3) { assertTrue(q.enqueue(1)) }
        assertFalse("已满时必须拒绝新的那一笔", q.enqueue(1))
        assertFalse(q.enqueue(1))
        assertEquals("被拒的注入不得改变队列长度", 3, q.size)
        assertEquals("被拒的注入不得挤掉已排队的请求", listOf(1, 1, 1), listOf(q.poll(), q.poll(), q.poll()))
    }

    /** 证伪「反向=取消」（size 会是 0）与「反向也排队」（size 会是 3、队首仍是旧方向）。 */
    @Test
    fun oppositeTapReplacesUnstartedBacklogWithItselfOnly() {
        val q = PendingTurnQueue()
        q.enqueue(7)
        q.enqueue(7)
        assertTrue(q.enqueue(-7))
        assertEquals("反向点按后要只剩这一笔", 1, q.size)
        assertEquals(-7, q.poll())
        assertNull("旧的同向积压必须已被作废", q.poll())
    }

    @Test
    fun oppositeTapOnEmptyQueueIsAccepted() {
        // 队列为空（那一个请求正在播放、尚未入队）时，反向判断不能把它当成「覆盖旧积压」吞掉。
        val q = PendingTurnQueue()
        assertTrue(q.enqueue(-1))
        assertEquals(1, q.size)
        assertEquals(-1, q.poll())
    }

    @Test
    fun reversalAfterBacklogWasDrainedStillReplacesWhatRemains() {
        val q = PendingTurnQueue()
        q.enqueue(1)
        q.enqueue(1)
        assertEquals(1, q.poll())          // 队列里还剩一笔未开始的 1
        assertTrue(q.enqueue(-1))          // 反向 ⇒ 剩下那笔作废，只留 -1
        assertEquals(1, q.size)
        assertEquals(-1, q.poll())
    }

    /** 证伪「放回队尾」：抢占重试必须回到队首，否则该请求会被后面的请求插队。 */
    @Test
    fun requeueFrontRestoresTheRequestAtTheHead() {
        val q = PendingTurnQueue()
        q.enqueue(9)
        q.enqueue(9)
        assertEquals(9, q.poll())
        q.requeueFront(7)
        assertEquals(listOf(7, 9), listOf(q.poll(), q.poll()))
    }

    @Test
    fun requeueFrontAtCapacityStillAcceptsBecauseItReplacesAPop() {
        val q = PendingTurnQueue()
        repeat(3) { q.enqueue(1) }
        assertEquals(1, q.poll())          // 让出空位（真实用法就是刚 poll 出来）
        q.requeueFront(1)
        assertEquals(3, q.size)
    }

    @Test
    fun zeroDirectionIsNeverAccepted() {
        val q = PendingTurnQueue()
        assertFalse(q.enqueue(0))
        assertEquals(0, q.size)
        assertTrue(q.isEmpty)
    }

    @Test
    fun pollOnEmptyQueueReturnsNull() = assertTrue(PendingTurnQueue().poll() == null)

    /**
     * 容量不变式的**类侧**保证：任意方向序列（含 0、同向、反复反向）下，`enqueue` 单独
     * 永远不可能让 size 越过 capacity。
     *
     * 这条存在的意义是把「唯一的越容缺口在 `requeueFront`（它按契约只做归还，不查容量）」
     * 写进测试：将来若有人把 `requeueFront` 改成走 `enqueue`、或给 enqueue 加扩容 / 加丢最旧，
     * 本文件其他用例会立刻指出语义漂移；反之若有人以为「容量由类自己兜住」而把 poll 与
     * requeue 拆到两个挂起点之间，这里也留得住证据 —— 越容只能来自归还，不来自入队。
     */
    @Test
    fun enqueueAloneCanNeverExceedCapacity() {
        val q = PendingTurnQueue()
        val directions = listOf(1, 1, -1, -1, 1, 7, 7, 9, -9, 1, 1, 1, 1, -1, 0, -7, 3, 3, 3, 3, -3)
        for (direction in directions) {
            q.enqueue(direction)
            assertTrue(
                "入队后不得越容（capacity=${q.capacity}，本轮方向=$direction，实际 size=${q.size}）",
                q.size <= q.capacity,
            )
        }
    }

    @Test
    fun clearDropsTheWholeUnstartedBacklog() {
        val q = PendingTurnQueue()
        q.enqueue(1)
        q.enqueue(1)
        q.clear()
        assertTrue(q.isEmpty)
        assertNull(q.poll())
    }
}
