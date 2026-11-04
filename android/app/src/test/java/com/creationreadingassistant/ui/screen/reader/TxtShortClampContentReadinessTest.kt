package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.pager.ScrollUnitContentLoader
import com.creationreadingassistant.feature.reader.pager.ScrollUnitContentState
import com.creationreadingassistant.feature.reader.rules.ScrollUnitProjection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors

/**
 * CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2 —— 短末项 clamp 的**正文就绪观察接缝**定向回归。
 *
 * ## 设备缺陷（隔离 MuMu，j1_clamp_20_reentrySettled）
 *
 * 短末项 clamp 书（2 单元，共 3840 字）重进时：滚动单元仍在异步加载，LazyColumn 先用
 * [com.creationreadingassistant.ui.theme.ShimmerBlock] 占位项测量。占位高度与正文无关，
 * 一屏即可容纳全部占位项 → `visibleItemsInfo` 报出「末项就在屏内」→
 * [plainRestoreSettled] 的 clamp 分支据此判为**已落位**（设备诊断：
 * `loop_landed {f:0, l:1, atEnd:true, target:1, isLast:true}`）。随后真实正文加载、
 * 列表重新测量并把位置拉回书首，500ms 防抖保存按视口顶部把 `offset=0` 写库
 * （`scroll_save_dispatch {offset:0, canForward:true}`），覆盖刚植入的末单元进度 3840。
 *
 * 修复合同（本轮）：**正文未就绪时不确认 clamp、不清 pending**；正文加载完成且对应布局
 * 更新后**重新判定**；Failed 不得冒充 Loaded；不设超时放行；不用 `size>0`、一次 yield
 * 或人工采样顺序证明正文已加载。
 *
 * ## 覆盖内容
 *
 * 1. 就绪度策略 [scrollRestoreContentReadiness]（真实 [ScrollUnitContentLoader] 状态驱动）；
 * 2. clamp 落位门槛（占位/失败不得放行；正常首项==目标分支不受影响）；
 * 3. 真实生产控制流 [runPlainRestoreLoop] 上的红→绿：占位帧不得释放，内容加载后重新落位；
 * 4. 普通长书 / 中间项拒绝 / 取消 / 同状态不忙循环 的既有语义不回退；
 * 5. 视口物理 clamp 与持久位置语义的区分（[plainScrollPersistOffset]）；
 * 6. 生命周期保存标记三条真实路径（[ReaderLifecyclePersistGate]）。
 *
 * 全部纯 JVM：不启设备、不构建 APK。`DEVICE_ACCEPTED=false`，设备结论另计。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TxtShortClampContentReadinessTest {

    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "scroll-readiness-io").apply { isDaemon = true }
    }
    private val ioDispatcher = ioExecutor.asCoroutineDispatcher()

    @After
    fun tearDown() {
        ioExecutor.shutdown()
    }

    // ── 夹具：短末项 clamp 书（2 单元） ────────────────────────────────────────

    private val shortUnits = listOf(
        ReadingUnit(unitIndex = 0, chapterIndex = 0, title = "短末项", charStart = 0, charCount = 3069),
        ReadingUnit(unitIndex = 1, chapterIndex = 0, title = "短末项", charStart = 3069, charCount = 771),
    )
    private val shortTotalChars = 3840

    private fun longUnits(total: Int, perUnit: Int): List<ReadingUnit> =
        (0 until total).map { i -> ReadingUnit(i, 0, "", i * perUnit, perUnit) }

    private fun identityProjection(text: String): ScrollUnitProjection = requireNotNull(
        ScrollUnitProjection.fromSource(
            ReadingUnit(unitIndex = 0, chapterIndex = 0, title = "u", charStart = 0, charCount = text.length),
            text,
        ),
    )

    // ── 1. 就绪度策略：由真实 ScrollUnitContentLoader 状态驱动 ────────────────

    @Test
    fun `readiness policy - absent entries are not ready and loaded visible units are`() = runTest {
        val loader = ScrollUnitContentLoader(ioDispatcher = ioDispatcher)
        val source = Any()
        loader.switchSource(source)

        assertEquals(
            "未组合/未开始加载的单元必须按 Loading 处理（正文未就绪）",
            ScrollUnitContentState.Loading,
            loader.currentStateOf(0),
        )
        assertEquals(
            ScrollRestoreContentReadiness.PENDING,
            scrollRestoreContentReadiness(listOf(0), loader::currentStateOf),
        )

        loader.load(source, 0) { identityProjection("unit-0") }
        assertTrue(loader.currentStateOf(0) is ScrollUnitContentState.Loaded)
        assertEquals(
            "可见单元全部 Loaded 才可就绪",
            ScrollRestoreContentReadiness.READY,
            scrollRestoreContentReadiness(listOf(0), loader::currentStateOf),
        )
    }

    @Test
    fun `readiness policy - partially loaded visible set stays pending until all loaded`() = runTest {
        val loader = ScrollUnitContentLoader(ioDispatcher = ioDispatcher)
        val source = Any()
        loader.switchSource(source)
        loader.load(source, 0) { identityProjection("unit-0") }
        val gate = CompletableDeferred<Unit>()
        val slow = launch { loader.load(source, 1) { gate.await(); identityProjection("unit-1") } }
        runCurrent()

        assertEquals(
            "仍有一个可见单元在加载时不得就绪（占位高度决定布局）",
            ScrollRestoreContentReadiness.PENDING,
            scrollRestoreContentReadiness(listOf(0, 1), loader::currentStateOf),
        )
        gate.complete(Unit)
        slow.join()
        assertEquals(
            ScrollRestoreContentReadiness.READY,
            scrollRestoreContentReadiness(listOf(0, 1), loader::currentStateOf),
        )
    }

    @Test
    fun `readiness policy - failed visible unit never passes as loaded`() = runTest {
        val loader = ScrollUnitContentLoader(ioDispatcher = ioDispatcher)
        val source = Any()
        loader.switchSource(source)
        loader.load(source, 0) { identityProjection("unit-0") }
        loader.load(source, 1) { throw IOException("boom") }
        assertTrue(loader.currentStateOf(1) is ScrollUnitContentState.Failed)

        assertEquals(
            "Failed 不得冒充 Loaded",
            ScrollRestoreContentReadiness.FAILED,
            scrollRestoreContentReadiness(listOf(0, 1), loader::currentStateOf),
        )
    }

    @Test
    fun `readiness policy - empty visible set is pending not ready`() {
        assertEquals(
            ScrollRestoreContentReadiness.PENDING,
            scrollRestoreContentReadiness(emptyList()) {
                ScrollUnitContentState.Loaded(identityProjection("unit"))
            },
        )
    }

    @Test
    fun `loader revision changes on every load state transition`() = runTest {
        val loader = ScrollUnitContentLoader(ioDispatcher = ioDispatcher)
        val source = Any()
        loader.switchSource(source)
        val afterSwitch = loader.stateRevision.value
        loader.load(source, 0) { identityProjection("unit-0") }
        val afterLoad = loader.stateRevision.value
        assertTrue("加载状态变化必须推进修订号（恢复等待流的唤醒依据）", afterLoad > afterSwitch)

        val loader2 = ScrollUnitContentLoader(ioDispatcher = ioDispatcher)
        val s2 = Any()
        loader2.switchSource(s2)
        val base = loader2.stateRevision.value
        loader2.load(s2, 0) { throw IOException("boom") }
        assertTrue("失败同样是状态变化，必须推进修订号", loader2.stateRevision.value > base)
    }

    // ── 2. clamp 落位门槛（纯判定） ────────────────────────────────────────────

    @Test
    fun `clamp landing requires loaded content - placeholder frame geometry is rejected`() {
        // 设备实测的占位帧几何：first=0 / lastVisible=target=末单元。
        // 修复前该几何一律判为已落位；修复后必须由正文就绪度把关。
        assertFalse(
            "占位帧（PENDING）不得确认 clamp 落位",
            plainRestoreSettled(
                firstVisibleItemIndex = 0, lastVisibleItemIndex = 1,
                targetItemIndex = 1, unitsEmpty = false, isLastUnit = true,
                contentReadiness = ScrollRestoreContentReadiness.PENDING,
            ),
        )
        assertFalse(
            "加载失败（FAILED）不得冒充已加载并确认 clamp 落位",
            plainRestoreSettled(
                firstVisibleItemIndex = 0, lastVisibleItemIndex = 1,
                targetItemIndex = 1, unitsEmpty = false, isLastUnit = true,
                contentReadiness = ScrollRestoreContentReadiness.FAILED,
            ),
        )
        assertTrue(
            "正文就绪后的同一几何是合法 clamp 落位",
            plainRestoreSettled(
                firstVisibleItemIndex = 0, lastVisibleItemIndex = 1,
                targetItemIndex = 1, unitsEmpty = false, isLastUnit = true,
                contentReadiness = ScrollRestoreContentReadiness.READY,
                canScrollForward = false,
            ),
        )
        assertFalse(
            "正文就绪但列表仍能向前滚动（未真正贴底）时不得确认 clamp 落位",
            plainRestoreSettled(
                firstVisibleItemIndex = 0, lastVisibleItemIndex = 1,
                targetItemIndex = 1, unitsEmpty = false, isLastUnit = true,
                contentReadiness = ScrollRestoreContentReadiness.READY,
                canScrollForward = true,
            ),
        )
        assertFalse(
            "目标为末单元但列表停在第 0 像素时（尚未发生实际滚动）不得确认 clamp 落位",
            plainRestoreSettled(
                firstVisibleItemIndex = 0, lastVisibleItemIndex = 1,
                targetItemIndex = 1, unitsEmpty = false, isLastUnit = true,
                contentReadiness = ScrollRestoreContentReadiness.READY,
                canScrollForward = false,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun `first-visible-equals-target branch is unaffected by readiness`() {
        // 该分支不是「贴底 clamp」推断，而是滚动请求被真实兑现（目标就在视口顶部），
        // 与正文占位无关；本轮不改变其语义。
        assertTrue(
            plainRestoreSettled(
                firstVisibleItemIndex = 37, lastVisibleItemIndex = 38,
                targetItemIndex = 37, unitsEmpty = false, isLastUnit = false,
                contentReadiness = ScrollRestoreContentReadiness.PENDING,
            ),
        )
    }

    @Test
    fun `middle item clamp is still rejected and unloaded last item too`() {
        assertFalse(
            "中间项贴底不是落位（与就绪度无关）",
            plainRestoreSettled(
                firstVisibleItemIndex = 36, lastVisibleItemIndex = 37,
                targetItemIndex = 37, unitsEmpty = false, isLastUnit = false,
                contentReadiness = ScrollRestoreContentReadiness.READY,
            ),
        )
        assertFalse(
            "目标==末单元但末可见项不是目标：条件不完整",
            plainRestoreSettled(
                firstVisibleItemIndex = 36, lastVisibleItemIndex = 38,
                targetItemIndex = 39, unitsEmpty = false, isLastUnit = true,
                contentReadiness = ScrollRestoreContentReadiness.READY,
            ),
        )
    }

    // ── 3. 生产控制流：占位帧不得释放，内容加载后重新落位 ─────────────────────

    /**
     * 模拟短末项 clamp 书的真实帧序：
     * ① 占位帧：全部单元 Loading，占位高度一屏容纳（首 0 / 末 1）；
     * ② 正文加载完成：首单元独占一屏 → 列表停在顶部（首 0 / 末 0），就绪度 READY；
     * ③ 恢复循环重新滚动到末单元 → 只能贴底（首 0 / 末 1）。
     */
    private class ShortClampScaffold(
        private val units: List<ReadingUnit>,
        private val totalChars: Int,
    ) {
        val layout = MutableStateFlow(
            plainRestoreLayoutSnapshot(
                totalItemsCount = 2, firstVisibleItemIndex = 0, lastVisibleItemIndex = 1,
                contentReadiness = ScrollRestoreContentReadiness.PENDING,
            ),
        )
        var readiness: ScrollRestoreContentReadiness = ScrollRestoreContentReadiness.PENDING
        var contentLoaded = false
        val scrollCalls = mutableListOf<Int>()

        fun settleSnapshot(): Triple<Int, Int, Boolean> {
            val s = layout.value
            return Triple(s.firstVisibleItemIndex, s.lastVisibleItemIndex, s.totalItemsCount == 0)
        }

        fun scrollTo(index: Int) {
            scrollCalls.add(index)
            // 占位帧下滚动不改变布局（占位项一屏容纳）；真实正文下滚到末单元只能贴底。
            if (contentLoaded) {
                layout.value = plainRestoreLayoutSnapshot(
                    totalItemsCount = units.size, firstVisibleItemIndex = 0,
                    lastVisibleItemIndex = units.lastIndex,
                    contentReadiness = ScrollRestoreContentReadiness.READY,
                )
            }
        }

        fun loadContent() {
            contentLoaded = true
            readiness = ScrollRestoreContentReadiness.READY
            layout.value = plainRestoreLayoutSnapshot(
                totalItemsCount = units.size, firstVisibleItemIndex = 0, lastVisibleItemIndex = 0,
                contentReadiness = ScrollRestoreContentReadiness.READY,
            )
        }

        fun failContent() {
            contentLoaded = true
            readiness = ScrollRestoreContentReadiness.FAILED
            layout.value = plainRestoreLayoutSnapshot(
                totalItemsCount = units.size, firstVisibleItemIndex = 0, lastVisibleItemIndex = 0,
                contentReadiness = ScrollRestoreContentReadiness.FAILED,
            )
        }
    }

    @Test
    fun `short clamp reentry must not release pending on the loading placeholder frame`() {
        val s = ShortClampScaffold(shortUnits, shortTotalChars)
        var released = false
        runBlocking {
            withTimeout(5_000) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = shortTotalChars, savedPlainPercent = 100f,
                        totalChars = shortTotalChars, units = shortUnits, pagedAbsOffset = -1,
                        scrollTo = { s.scrollTo(it) },
                        settleSnapshot = { s.settleSnapshot() },
                        layoutChanges = s.layout,
                        contentReadiness = { s.readiness },
                    )
                }
                repeat(10) { kotlinx.coroutines.yield() }
                assertFalse(
                    "正文未就绪的占位帧不得确认 clamp 落位并释放 pending" +
                        "（否则 0 位置保存会覆盖原进度）",
                    released,
                )
                assertEquals("占位帧下滚动不改变布局，不得重复滚动", 1, s.scrollCalls.size)

                s.loadContent()
                repeat(20) { kotlinx.coroutines.yield() }
                job.join()
            }
        }
        assertTrue("正文加载完成且列表真实贴底后必须确认落位并释放 pending", released)
        assertEquals("内容加载后需重新滚动一次，在真实布局上重新判定落位", 2, s.scrollCalls.size)
    }

    @Test
    fun `red model - without content readiness the placeholder frame releases immediately`() {
        // 修复前行为模型：不观察正文就绪度（等价于本轮修复前）。占位帧即被判为 clamp 落位
        // 并释放 pending —— 这正是设备缺陷。保留为反例，证明缺陷来自「不观察正文状态」。
        val s = ShortClampScaffold(shortUnits, shortTotalChars)
        var released = false
        runBlocking {
            withTimeout(5_000) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = shortTotalChars, savedPlainPercent = 100f,
                        totalChars = shortTotalChars, units = shortUnits, pagedAbsOffset = -1,
                        scrollTo = { s.scrollTo(it) },
                        settleSnapshot = { s.settleSnapshot() },
                        layoutChanges = s.layout,
                        contentReadiness = { ScrollRestoreContentReadiness.READY },
                    )
                }
                repeat(10) { kotlinx.coroutines.yield() }
                assertTrue("不观察正文就绪度时，占位帧即释放 pending（缺陷模型）", released)
                assertFalse("缺陷模型下正文甚至还没加载", s.contentLoaded)
                job.cancel()
                job.join()
            }
        }
    }

    @Test
    fun `failed content must not release pending and must not time out`() {
        val s = ShortClampScaffold(shortUnits, shortTotalChars)
        var released = false
        runBlocking {
            withTimeout(5_000) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = shortTotalChars, savedPlainPercent = 100f,
                        totalChars = shortTotalChars, units = shortUnits, pagedAbsOffset = -1,
                        scrollTo = { s.scrollTo(it) },
                        settleSnapshot = { s.settleSnapshot() },
                        layoutChanges = s.layout,
                        contentReadiness = { s.readiness },
                    )
                }
                repeat(10) { kotlinx.coroutines.yield() }
                s.failContent()
                repeat(60) { kotlinx.coroutines.yield() }
                assertFalse("Failed 不得冒充 Loaded，也不得超时放行", released)
                job.cancel()
                job.join()
            }
        }
    }

    @Test
    fun `long book ordinary restore still lands and releases exactly once`() {
        val units = longUnits(40, 68)
        val totalChars = 40 * 68
        val target = unitIndexForOffset(units, 2553) // = 37
        val layout = MutableStateFlow(
            plainRestoreLayoutSnapshot(units.size, 0, units.size - 1),
        )
        val scrollCalls = mutableListOf<Int>()
        var released = false
        runBlocking {
            withTimeout(5_000) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = 2553, savedPlainPercent = 0f,
                        totalChars = totalChars, units = units, pagedAbsOffset = -1,
                        scrollTo = {
                            scrollCalls.add(it)
                            layout.value = plainRestoreLayoutSnapshot(units.size, it, units.size - 1)
                        },
                        settleSnapshot = {
                            val s = layout.value
                            Triple(s.firstVisibleItemIndex, s.lastVisibleItemIndex, s.totalItemsCount == 0)
                        },
                        layoutChanges = layout,
                        contentReadiness = { ScrollRestoreContentReadiness.READY },
                    )
                }
                repeat(10) { kotlinx.coroutines.yield() }
                job.join()
            }
        }
        assertTrue("普通长书恢复不得因新接缝回退", released)
        assertEquals("落位仅需一次滚动", 1, scrollCalls.size)
        assertEquals(listOf(target), scrollCalls)
    }

    @Test
    fun `cancellation while waiting for content leaves no scroll and no release`() {
        val s = ShortClampScaffold(shortUnits, shortTotalChars)
        var released = false
        runBlocking {
            withTimeout(5_000) {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    released = runPlainRestoreLoop(
                        savedPlainOffset = shortTotalChars, savedPlainPercent = 100f,
                        totalChars = shortTotalChars, units = shortUnits, pagedAbsOffset = -1,
                        scrollTo = { s.scrollTo(it) },
                        settleSnapshot = { s.settleSnapshot() },
                        layoutChanges = s.layout,
                        contentReadiness = { s.readiness },
                    )
                }
                repeat(5) { kotlinx.coroutines.yield() }
                job.cancel() // 切书/切模式：effect key 失效
                s.loadContent()
                repeat(10) { kotlinx.coroutines.yield() }
                assertFalse("等待正文时被取消：不得释放 pending", released)
                assertEquals("等待正文时被取消：不得再滚动", 1, s.scrollCalls.size)
                job.join()
            }
        }
    }

    // ── 4. 视口物理 clamp 与持久位置语义 ──────────────────────────────────────

    @Test
    fun `physical bottom clamp maps to end-of-book position not viewport-top offset`() {
        val listState = androidx.compose.foundation.lazy.LazyListState(
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )
        // 同一视口物理状态（首可见项 = 首单元），两种前滚能力对应两种持久位置语义。
        assertEquals(
            "列表已无法前滚（短末项贴底 / 读到书末）：持久位置取全书末尾",
            shortTotalChars,
            plainScrollPersistOffset(
                canScrollForward = false,
                listState = listState,
                units = shortUnits,
                totalChars = shortTotalChars,
            ),
        )
        assertEquals(
            "仍可前滚：按视口顶部项的已消费像素比例映射（首单元起点 = 0）",
            0,
            plainScrollPersistOffset(
                canScrollForward = true,
                listState = listState,
                units = shortUnits,
                totalChars = shortTotalChars,
            ),
        )
    }

    // ── 5. 生命周期保存标记：三条真实路径 ─────────────────────────────────────

    @Test
    fun `lifecycle gate - dispose after lifecycle persist is skipped`() {
        val gate = ReaderLifecyclePersistGate()
        gate.onPauseOrStop()
        assertFalse("已由 ON_PAUSE/ON_STOP 权威落库：销毁不得用过渡布局覆盖", gate.shouldPersistOnDispose())
    }

    @Test
    fun `lifecycle gate - dispose without lifecycle persist still saves`() {
        val gate = ReaderLifecyclePersistGate()
        assertTrue("未经生命周期落库（同 Activity 内路由离开）：销毁仍需落库", gate.shouldPersistOnDispose())
    }

    @Test
    fun `lifecycle gate - resume then route-away still saves and marker never sticks`() {
        val gate = ReaderLifecyclePersistGate()
        // 暂停（落库）→ 回到前台 → 同 Activity 内路由离开（只触发 composable dispose）。
        gate.onPauseOrStop()
        assertFalse(gate.shouldPersistOnDispose())
        gate.onResume()
        assertTrue("重新 ON_RESUME 后路由离开仍必须保存（标记不得永久抑制后续保存）", gate.shouldPersistOnDispose())

        // 再来一轮：暂停 → 回到前台 → 离开，行为必须一致可重复。
        gate.onPauseOrStop()
        assertFalse(gate.shouldPersistOnDispose())
        gate.onResume()
        assertTrue(gate.shouldPersistOnDispose())

        // 暂停后回到前台再暂停：仍按最新事件判定。
        gate.onPauseOrStop()
        gate.onResume()
        gate.onPauseOrStop()
        assertFalse(gate.shouldPersistOnDispose())
    }

    @Test
    fun `lifecycle gate - fresh composition starts unsuppressed`() {
        val gate = ReaderLifecyclePersistGate()
        gate.onPauseOrStop()
        // 组合（重新）开始：效果重新建立，标记必须复位。
        gate.onResume()
        assertTrue(gate.shouldPersistOnDispose())
    }
}
