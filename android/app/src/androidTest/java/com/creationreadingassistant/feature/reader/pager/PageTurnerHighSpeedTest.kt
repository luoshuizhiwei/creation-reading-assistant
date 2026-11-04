package com.creationreadingassistant.feature.reader.pager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.creationreadingassistant.ui.theme.ReaderPaperSurfaceSpec

/**
 * 高速翻页（speed 调小）回归：slide / cover 在动画期间不得出现透明/空白帧。
 *
 * 背景：用户把「翻页速度」调快后，下一页/上一页在翻页过程中会空白。两个根因：
 * 1. 兜底分支只给邻页自身套纸色，邻页尚未预排（frameAt 返回 null）时什么都不画，
 *    当前页滑走后直接露出底层背景；
 * 2. 渲染分支判定读实时 currentFrame，换帧那一帧会把位图分支翻回实时分支 ⇒ 闪屏。
 *
 * 测试用纯黑底 + 白纸色，任何透明洞都会被采样为黑色；页面内容画在左上角 1/4，
 * 中心采样点恒为纸色，可同时覆盖「位图分支」与「实时兜底分支」。
 */
class PageTurnerHighSpeedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun assertNoBlankFrameDuringHighSpeedTurn(effect: String) {
        var turnRequest by mutableIntStateOf(0)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("pager")
                    .background(Color.Black),
            ) {
                PageTurner(
                    currentFrame = 0,
                    previousFrame = -1,
                    nextFrame = 1,
                    effect = effect,
                    // 最快档：位图预渲染来不及完成，必然走兜底实时渲染分支。
                    speed = 150f,
                    turnRequest = turnRequest,
                    onTurnRequestConsumed = { turnRequest = 0 },
                    onPrevious = {},
                    onNext = {},
                    pageSurface = ReaderPaperSurfaceSpec.solid(Color.White),
                ) { frame, _ ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.25f)
                                .background(
                                    when (frame) {
                                        0 -> Color.Red
                                        1 -> Color.Blue
                                        else -> Color.Green
                                    },
                                ),
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle { turnRequest = 1 }
        // 逐帧推进整个动画（含落位提交帧），每一帧都不允许出现透明/空白。
        repeat(10) { step ->
            composeRule.mainClock.advanceTimeBy(30)
            val image = composeRule.onNodeWithTag("pager").captureToImage()
            val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
            assertTrue(
                "$effect 高速翻页第 ${step + 1} 帧出现透明/空白，实际像素=$pixel",
                pixel.red > 0.95f && pixel.green > 0.95f && pixel.blue > 0.95f,
            )
        }
    }

    @Test
    fun slideHighSpeedTurnNeverShowsABlankFrame() = assertNoBlankFrameDuringHighSpeedTurn("slide")

    @Test
    fun coverHighSpeedTurnNeverShowsABlankFrame() = assertNoBlankFrameDuringHighSpeedTurn("cover")

    /**
     * 翻页真实推进 currentFrame 时同样不得出现空白帧 —— 覆盖「换帧与位图轮换不在同一帧」
     * 这一路径（旧实现在此处翻转渲染分支，是闪屏根因）。
     */
    @Test
    fun advancingTurnNeverShowsABlankFrame() {
        var page by mutableIntStateOf(0)
        var turnRequest by mutableIntStateOf(0)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("pager")
                    .background(Color.Black),
            ) {
                PageTurner(
                    currentFrame = page,
                    previousFrame = page - 1,
                    nextFrame = page + 1,
                    effect = "cover",
                    speed = 150f,
                    turnRequest = turnRequest,
                    onTurnRequestConsumed = { turnRequest = 0 },
                    onPrevious = { page-- },
                    onNext = { page++ },
                    pageSurface = ReaderPaperSurfaceSpec.solid(Color.White),
                ) { frame, _ ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.25f)
                                .background(if (frame % 2 == 0) Color.Red else Color.Blue),
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle { turnRequest = 1 }
        repeat(10) { step ->
            composeRule.mainClock.advanceTimeBy(30)
            val image = composeRule.onNodeWithTag("pager").captureToImage()
            val pixel = image.toPixelMap()[image.width / 2, image.height / 2]
            assertTrue(
                "推进翻页第 ${step + 1} 帧出现透明/空白，实际像素=$pixel",
                pixel.red > 0.95f && pixel.green > 0.95f && pixel.blue > 0.95f,
            )
        }
    }

    // ── S06-⑤：动画期间连续点按不得被无声吞掉，也不得无限积压 ────────────────────
    //
    // 旧行为（PageTurner.settle 开头的 `if (turning …) return`）下，第二次点按既不排队也不生效；
    // 上面三个用例只看像素，看不出「少翻页」。以下用例一律断言**最终页号 + 内容身份 +
    // 翻页回调次数**，因此对「画面没空白但少翻了一页」是有牙的。

    /**
     * 一次测试的可见状态：页号、请求槽、回调次数、消费次数、渲染过的当前页身份。
     *
     * [speed] 就是 `settle` 里一轮动画的时长（`durationMs = speed × 剩余比例`，满位移时比例=1）。
     * 排队类用例需要「多次点按落在同一轮动画内」，因此把窗口放大到远大于点按间隔，
     * 而不是靠帧数去猜（见 [QUEUE_TEST_SPEED_MS]）。
     */
    private class TurnHarness(
        val effect: String,
        val speed: Float,
        val firstPage: Int = 0,
        val lastPage: Int = 100,
    ) {
        var page by mutableIntStateOf(0)
        var turnRequest by mutableIntStateOf(0)

        /** 外层翻页回调被调用的次数 —— 与「页号是否变化」分开记，末页点按才会露出差别。 */
        var nextInvocations = 0
        var previousInvocations = 0

        /**
         * 请求槽被 `onTurnRequestConsumed` 清空的次数。
         * 作用是把「注入被单槽通道合并」与「实现丢了请求」区分开：若少了几次消费，
         * 上限断言得到的页号就来自更少的输入，结论无意义。
         */
        var consumedRequests = 0

        /** 每次以 isCurrent=true 渲染时按顺序记录帧身份（相邻重复只记一次）。 */
        val currentIdentities = mutableListOf<Int>()

        /** 最后一次「当前页槽」渲染到的帧身份 —— 静止后必须等于页号，否则就是播错了页。 */
        var lastRenderedCurrent = Int.MIN_VALUE
    }

    private fun setPageTurner(h: TurnHarness) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("pager")
                    .background(Color.Black),
            ) {
                PageTurner(
                    currentFrame = h.page,
                    previousFrame = if (h.page > h.firstPage) h.page - 1 else null,
                    nextFrame = if (h.page < h.lastPage) h.page + 1 else null,
                    effect = h.effect,
                    speed = h.speed,
                    turnRequest = h.turnRequest,
                    onTurnRequestConsumed = { h.consumedRequests++; h.turnRequest = 0 },
                    onPrevious = { h.previousInvocations++; if (h.page > h.firstPage) h.page-- },
                    onNext = { h.nextInvocations++; if (h.page < h.lastPage) h.page++ },
                    pageSurface = ReaderPaperSurfaceSpec.solid(Color.White),
                ) { frame, isCurrent ->
                    if (isCurrent) {
                        h.lastRenderedCurrent = frame
                        if (h.currentIdentities.lastOrNull() != frame) h.currentIdentities.add(frame)
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color.White))
                }
            }
        }
    }

    /**
     * 注入一次点按，然后显式推进 [afterMillis] 毫秒。
     *
     * 用 `runOnUiThread` 而不是 `runOnIdle`：在 ui-test 1.9.0 里 `runOnIdle` → `waitForIdle` →
     * `ComposeIdlingResource.isIdleNow` **会自己泵帧**（字节码里可见它调用
     * `MainTestClockImpl.advanceTimeByFrame()`），于是「这次点按落在动画的第几毫秒」不再由测试
     * 决定，每笔注入消耗的帧数也不确定。`runOnUiThread` 只把写状态投递到 UI 线程，
     * 不等 idle、不泵帧，时间推进完全由 `advanceTimeBy` 给出。
     */
    private fun tap(h: TurnHarness, direction: Int, afterMillis: Long) {
        composeRule.runOnUiThread { h.turnRequest = direction }
        composeRule.mainClock.advanceTimeBy(afterMillis)
    }

    /**
     * 按毫秒推进到排空（动画 + 尾帧 + 预渲染补帧都在其中），再取一份状态快照。
     *
     * 断言一律留在测试线程：把 assertEquals 写进等待块里的话，块一旦没被执行，测试就会
     * 「绿得毫无意义」。快照为 null 时直接判定测试自身失效。
     */
    private data class TurnSnapshot(
        val page: Int,
        val nextInvocations: Int,
        val previousInvocations: Int,
        val consumedRequests: Int,
        val lastRenderedCurrent: Int,
        val currentIdentities: List<Int>,
    )

    private fun drainThenSnapshot(h: TurnHarness, millis: Long = DRAIN_MILLIS): TurnSnapshot {
        var snapshot: TurnSnapshot? = null
        composeRule.mainClock.advanceTimeBy(millis)
        composeRule.runOnUiThread {
            snapshot = TurnSnapshot(
                page = h.page,
                nextInvocations = h.nextInvocations,
                previousInvocations = h.previousInvocations,
                consumedRequests = h.consumedRequests,
                lastRenderedCurrent = h.lastRenderedCurrent,
                currentIdentities = h.currentIdentities.toList(),
            )
        }
        return checkNotNull(snapshot) { "runOnUiThread 没有执行 ⇒ 状态未被读取，本测试自身失效" }
    }

    @Test
    fun singleTapAdvancesExactlyOnePage() {
        val h = TurnHarness(effect = "cover", speed = TAP_TEST_SPEED_MS)
        setPageTurner(h)
        tap(h, direction = 1, afterMillis = TAP_INTERVAL_MS)
        val s = drainThenSnapshot(h)
        assertEquals("单次点按必须推进且只推进一页", 1, s.page)
        assertEquals(1, s.nextInvocations)
        assertEquals(0, s.previousInvocations)
        assertEquals("这一次点按确实被通道消费了", 1, s.consumedRequests)
        assertEquals("静止后当前页槽渲染的必须是真实页号", s.page, s.lastRenderedCurrent)
    }

    /**
     * 核心回归：动画期间连点三次必须推进三页（旧实现只翻一页，其余被吞）。
     *
     * 同时断言「当前页身份」逐页出现 0→1→2 且静止后为 3：这排除了另一种假通过 ——
     * 用**同一份过期快照**连播三次动画（页号对了，但用户看到的是同一页滑三遍）。
     */
    @Test
    fun threeRapidTapsDuringAnimationAdvanceThreePages() {
        val h = TurnHarness(effect = "cover", speed = QUEUE_TEST_SPEED_MS)
        setPageTurner(h)
        // 3 × 24ms = 72ms ≪ 600ms 动画窗口 ⇒ 第 2、3 次必然落在第 1 次动画进行中。
        tap(h, 1, afterMillis = TAP_INTERVAL_MS)
        tap(h, 1, afterMillis = TAP_INTERVAL_MS)
        tap(h, 1, afterMillis = TAP_INTERVAL_MS)
        val s = drainThenSnapshot(h)
        assertEquals("三次点按必须被投递", 3, s.consumedRequests)
        assertEquals("三次点按必须推进三页（旧行为：只翻 1 页）", 3, s.page)
        assertEquals(3, s.nextInvocations)
        assertEquals(0, s.previousInvocations)
        assertEquals(s.page, s.lastRenderedCurrent)
        assertTrue(
            "每一轮动画的当前页必须是各不相同的真实帧，实际=${s.currentIdentities}",
            s.currentIdentities.containsAll(listOf(0, 1, 2)) && s.currentIdentities.last() == 3,
        )
    }

    /**
     * 反方向语义：尚未开始的同向积压被清空，只保留这次反向意图（用户最新方向优先）。
     *
     * 点按序列 next、next、prev ⇒ 实际执行「前进 1 页 + 后退 1 页」，回到第 0 页；
     * 中间那次 next 作废而不是先把它翻完。
     */
    @Test
    fun oppositeTapReplacesUnstartedBacklog() {
        val h = TurnHarness(effect = "slide", speed = QUEUE_TEST_SPEED_MS)
        setPageTurner(h)
        tap(h, 1, afterMillis = TAP_INTERVAL_MS)
        tap(h, 1, afterMillis = TAP_INTERVAL_MS)
        tap(h, -1, afterMillis = TAP_INTERVAL_MS)
        val s = drainThenSnapshot(h)
        assertEquals(3, s.consumedRequests)
        assertEquals("反向点按应作废未开始的积压 ⇒ 净位移 0", 0, s.page)
        assertEquals(1, s.nextInvocations)
        assertEquals(1, s.previousInvocations)
        assertEquals(s.page, s.lastRenderedCurrent)
    }

    /**
     * 上限：一次爆发最多「1 个在播 + [PENDING_TURN_COUNT] 个排队」= 4 页，多余点按被合并。
     *
     * 两条边界各证伪一种实现错误：`consumedRequests == 6` 保证「少翻页」不是「少投递」；
     * 再推进一段有界时间断言页号不变，证伪「队列无限积压、停手后还在自己翻页」。
     * 注：本用例只钉住**数量**上限；纯同向爆发下「丢最新」与「丢最旧」不可区分，
     * 该策略差异由入队处的显式注释与评审保证，不在此假装覆盖。
     */
    @Test
    fun burstBeyondCapacityIsBounded() {
        val h = TurnHarness(effect = "cover", speed = QUEUE_TEST_SPEED_MS)
        setPageTurner(h)
        // 6 × 24ms = 144ms ≪ 600ms ⇒ 全部落在第一轮动画窗口内。
        repeat(6) { tap(h, 1, afterMillis = TAP_INTERVAL_MS) }
        val s = drainThenSnapshot(h)
        assertEquals("6 次点按必须全部被通道消费", 6, s.consumedRequests)
        assertEquals(
            "6 次连点应被夹到「1 个在播 + $PENDING_TURN_COUNT 个排队」= ${PENDING_TURN_COUNT + 1} 页",
            PENDING_TURN_COUNT + 1,
            s.page,
        )
        assertEquals("被执行的每一次翻页都真实推进一页", s.nextInvocations, s.page)
        assertEquals(s.page, s.lastRenderedCurrent)
        val later = drainThenSnapshot(h, millis = DRAIN_MILLIS)
        assertEquals("停手后不得继续自己翻页", s.page, later.page)
    }

    /**
     * 边界：最后一页继续点「下一页」不能把队列卡死。
     *
     * 邻页快照缺失时旧实现仍会把这一请求交给外层（异步加载正文时只有这条路能翻页），
     * 本用例锁住该语义，并要求后续请求照样被排空。
     */
    @Test
    fun tapsAtLastPageStillConsumeRequestsWithoutStalling() {
        val h = TurnHarness(effect = "cover", speed = QUEUE_TEST_SPEED_MS, lastPage = 1)
        setPageTurner(h)
        tap(h, 1, afterMillis = TAP_INTERVAL_MS) // 0 → 1
        tap(h, 1, afterMillis = TAP_INTERVAL_MS) // 已在末页：仍要被消费
        tap(h, 1, afterMillis = TAP_INTERVAL_MS)
        val s = drainThenSnapshot(h)
        assertEquals(3, s.consumedRequests)
        assertEquals(1, s.page) // 页号夹在末页，不会越界
        assertEquals("末页点按也必须被消费（不排队、不卡队列）", 3, s.nextInvocations)
        assertEquals(s.page, s.lastRenderedCurrent)
    }

    private companion object {
        /** 与 PageTurner 的 PENDING_TURN_CAPACITY 对应（后者是文件私有，这里镜像其语义值）。 */
        const val PENDING_TURN_COUNT = 3

        /** 一笔点按之间的间隔：必须 > 1 帧（16ms），否则单槽通道来不及复位、下一笔会被合并。 */
        const val TAP_INTERVAL_MS = 24L

        /** 排队类用例的动画时长：让整段爆发确定性地落在同一轮动画之内。 */
        const val QUEUE_TEST_SPEED_MS = 600f

        /** 单击类用例沿用较快档位（与既有像素用例同量级）。 */
        const val TAP_TEST_SPEED_MS = 150f

        /**
         * 排空预算：4 轮 ×（600ms 动画 + 尾帧 + 预渲染补帧）并留余量。
         * 有界推进，绝不使用「等到空闲」这类可能永不满足的等待。
         */
        const val DRAIN_MILLIS = 6000L
    }
}
