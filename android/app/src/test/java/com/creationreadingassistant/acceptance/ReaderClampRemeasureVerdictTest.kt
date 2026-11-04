package com.creationreadingassistant.acceptance

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRA-REMEASURE-PROBE-REPAIR-v3 —— D 重测量探针**实际使用的判定规则**的离线定向回归。
 *
 * 这些用例直接调用探针所调用的 [ReaderClampRemeasureVerdict]（探针只做采集与映射，判定
 * 全部来自该对象），因此不存在"测试另写一套规则、探针仍旧逻辑"的分叉。最后一个用例额外
 * 用**字节一致性守卫**证明 test 源集的规则文件与 androidTest 源集探针实际使用的那份逐字节相同。
 *
 * 覆盖 v1 的三个缺口（完整 12 列比较 / 变化与裁决的因果关联 / 方法标签一致）与 v2 的
 * 三个**证据边界**缺口：
 *  - 请求时刻 ≠ 命令回读时刻 ≠ 实测 root 变化时刻（只有后者是"变化后"的可核实起点）；
 *  - 观测坐标必须存在且合法，缺失不能变成位移；
 *  - 作用域裁剪（PID/run/bid）不等于身份绑定，身份不足不得签保护结论。
 */
class ReaderClampRemeasureVerdictTest {

    private fun evt(seq: Int, ts: Long, event: String, vararg fields: Pair<String, String>) =
        ReaderClampRemeasureVerdict.Evt(seq, ts, event, fields.toMap())

    /** 12 列全量行；单列可覆写以构造反例。 */
    private fun row(
        bookId: String = TARGET_BOOK,
        percent: String = "49.641357421875",
        lastReadAt: String = "2026-10-15T19:36:00Z",
        totalMs: String = "60000",
        completion: String = "reading",
        locator: String = "{\"offset\":8019,\"locator_v2\":{\"v\":2,\"offset\":8019,\"ci\":1,\"co\":8019}}",
        payload: String? = null,
        revision: String = "1",
        deviceId: String? = null,
        updatedAt: String = "2026-10-15T19:36:00Z",
        completedAt: String? = null,
        deletedAt: String? = null,
    ) = ReaderClampRemeasureVerdict.Row12(
        book_id = bookId, progress_percent = percent, last_read_at = lastReadAt,
        total_reading_time_ms = totalMs, completion_state = completion, current_location_json = locator,
        payload = payload, revision = revision, device_id = deviceId, updated_at = updatedAt,
        completed_at = completedAt, deleted_at = deletedAt,
    )

    private val anchorSeq = 10
    private val anchorIndex = 4
    private val anchorOffsetPx = 1828

    /** 操作**请求**时刻：v2 曾把它当成"变化后"起点，这是 v3 修掉的缺口。 */
    private val tRequest = 1_000_000L

    /** 命令完成/回读时刻：只证明命令被系统接受，不证明 Compose 已重测量。 */
    private val tCommandDone = 1_050_000L

    /** 探针**实测**到 Compose root 边界变化的时刻：唯一可核实的"变化后"起点。 */
    private val tRootChanged = 1_200_000L

    private val anchorEvent get() = evt(
        anchorSeq, tRequest - 500, ReaderClampRemeasureVerdict.ANCHOR,
        "bid" to TARGET_BOOK, "index" to "$anchorIndex", "offsetPx" to "$anchorOffsetPx",
    )

    private val blockedEvent get() = evt(
        12, tRootChanged + 200, ReaderClampRemeasureVerdict.DEBOUNCE_BLOCKED_BY_SESSION_GUARD,
        "bid" to TARGET_BOOK,
    )

    /** 合法链：变化后有效观测(11) → 因果更晚、且身份绑定的守卫 blocked 裁决(12)。 */
    private fun legitEvents() = listOf(
        anchorEvent,
        evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
            "bid" to TARGET_BOOK, "index" to "2", "offsetPx" to "1214"),
        blockedEvent,
    )

    private fun boundary(
        requestNanos: Long = tRequest,
        commandDoneNanos: Long? = tCommandDone,
        rootChangedNanos: Long? = tRootChanged,
        rootBoundsChanged: Boolean = true,
    ) = ReaderClampRemeasureVerdict.WindowChangeBoundary(
        requestNanos = requestNanos,
        commandDoneNanos = commandDoneNanos,
        rootChangedNanos = rootChangedNanos,
        rootBoundsChanged = rootBoundsChanged,
    )

    private fun decide(
        events: List<ReaderClampRemeasureVerdict.Evt> = legitEvents(),
        windowBoundary: ReaderClampRemeasureVerdict.WindowChangeBoundary = boundary(),
        boundsChanged: Boolean = true,
        markerVisible: Boolean = true,
        before: ReaderClampRemeasureVerdict.Row12? = row(),
        after: ReaderClampRemeasureVerdict.Row12? = row(),
        targetBookId: String = TARGET_BOOK,
    ) = ReaderClampRemeasureVerdict.decide(
        events = events, anchorSeq = anchorSeq, boundary = windowBoundary,
        anchorIndex = anchorIndex, anchorOffsetPx = anchorOffsetPx,
        targetBookId = targetBookId,
        actualBoundsChanged = boundsChanged, markerStillVisible = markerVisible,
        beforeRow = before, afterRow = after,
    )

    // ── 1. 12 列逐列变化都使「整行未变」为 false ────────────────────────────────

    @Test
    fun `every one of the 12 columns changes rowUnchanged to false`() {
        val mutations: List<Pair<String, ReaderClampRemeasureVerdict.Row12>> = listOf(
            "book_id" to row(bookId = "book-B"),
            "progress_percent" to row(percent = "49.7"),
            "last_read_at" to row(lastReadAt = "2026-10-15T20:00:00Z"),
            "total_reading_time_ms" to row(totalMs = "90000"),
            "completion_state" to row(completion = "finished"),
            "current_location_json" to row(locator = "{\"offset\":3532}"),
            "payload" to row(payload = "{\"a\":1}"),
            "revision" to row(revision = "2"),
            "device_id" to row(deviceId = "dev-1"),
            "updated_at" to row(updatedAt = "2026-10-15T20:00:00Z"),
            "completed_at" to row(completedAt = "1791853728334"),
            "deleted_at" to row(deletedAt = "2026-10-15T20:00:00Z"),
        )
        assertEquals("必须恰有 12 列被逐一验证", 12, mutations.size)
        val base = row()
        for ((column, mutated) in mutations) {
            val v = decide(after = mutated)
            assertEquals("列 $column 变化必须被报告为该列", listOf(column), v.rowChangedColumns)
            assertFalse("列 $column 变化时 rowUnchangedAfterRemeasure 必须为 false", v.rowUnchangedAfterRemeasure)
            assertFalse(
                "列 $column 变化时即使有因果 blocked 裁决也不得声称保护通过",
                v.protectionClaim == "guard_blocked_and_row_unchanged",
            )
            assertTrue(
                "未改动的列 $column 不得被报告",
                ReaderClampRemeasureVerdict.changedColumns(base, row()).isEmpty(),
            )
        }
        val onlyLastRead = decide(after = row(lastReadAt = "2026-10-15T21:00:00Z"))
        assertEquals(listOf("last_read_at"), onlyLastRead.rowChangedColumns)
        assertEquals("not_claimed", onlyLastRead.protectionClaim)
    }

    // ── 2. 有晚到防抖事件但没有变化后有效观测 → 不能签保护结论 ──────────────────

    @Test
    fun `late debounce without post-change valid observation cannot claim protection`() {
        val events = listOf(
            anchorEvent,
            evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
                "bid" to TARGET_BOOK, "index" to "$anchorIndex", "offsetPx" to "$anchorOffsetPx"),
            blockedEvent,
        )
        val v = decide(events = events)
        assertFalse("无有效坐标变化 → remeasureObserved 必须为 false", v.remeasureObserved)
        assertEquals("no_remeasure_observed", v.guardVerdict)
        assertEquals("inconclusive", v.stage)
        assertEquals("not_claimed", v.protectionClaim)
        assertNull("firstChangedObserveSeq 应无值", v.firstChangedObserveSeq)
    }

    // ── 3. 防抖事件早于变化后观测 → 不能当因果证据 ──────────────────────────────

    @Test
    fun `debounce earlier than the post-change observation is not causal evidence`() {
        val events = listOf(
            anchorEvent,
            evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.DEBOUNCE_BLOCKED_BY_SESSION_GUARD,
                "bid" to TARGET_BOOK),
            evt(12, tRootChanged + 200, ReaderClampRemeasureVerdict.OBSERVE,
                "bid" to TARGET_BOOK, "index" to "2", "offsetPx" to "1214"),
        )
        val v = decide(events = events)
        assertTrue("有效观测存在 → remeasureObserved 为 true", v.remeasureObserved)
        assertEquals("早于观测的裁决不得被当作因果证据", 12, v.firstChangedObserveSeq)
        assertTrue("因果裁决集合必须为空", v.causalVerdictEvents.isEmpty())
        assertEquals("no_production_verdict", v.guardVerdict)
        assertEquals("inconclusive", v.stage)
        assertEquals("not_claimed", v.protectionClaim)
    }

    // ── 4. PID/book 不匹配不能混入 ──────────────────────────────────────────────

    @Test
    fun `events from another book or run are scoped out`() {
        val events = listOf(
            evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
                "index" to "2", "offsetPx" to "1214", "bid" to "book-A"),
            evt(12, tRootChanged + 200, ReaderClampRemeasureVerdict.OBSERVE,
                "index" to "9", "offsetPx" to "9999", "bid" to "book-B"),
            evt(9, tRootChanged + 300, ReaderClampRemeasureVerdict.OBSERVE,
                "index" to "1", "offsetPx" to "1", "bid" to "book-A"),
            evt(13, tRootChanged + 400, ReaderClampRemeasureVerdict.OBSERVE,
                "index" to "3", "offsetPx" to "3", "bid" to "book-A"),
            // 早于本 run 起点的事件（别的 run）
            evt(14, tRequest - 1_500, ReaderClampRemeasureVerdict.OBSERVE,
                "index" to "5", "offsetPx" to "5", "bid" to "book-A"),
            evt(15, tRootChanged + 500, ReaderClampRemeasureVerdict.OBSERVE,
                "index" to "6", "offsetPx" to "6"),
        )
        val scoped = ReaderClampRemeasureVerdict.scopeEvents(
            events, runStartSeq = 10, runStartNanos = tRequest, bookId = "book-A",
        )
        assertEquals(
            "只保留本 run（seq>起点且 ts>=起点时间）+ 本书（bid 缺省或等于 book-A）的事件",
            listOf(11, 13, 15), scoped.map { it.seq },
        )
        // 作用域裁剪保留缺 bid 的事件 ≠ 已绑定到本书：身份必须另行判定。
        val sessionToBook = ReaderClampRemeasureVerdict.sessionBindings(scoped)
        assertEquals(
            ReaderClampRemeasureVerdict.IdentityBinding.UNBOUND,
            ReaderClampRemeasureVerdict.identityBindingOf(scoped.last(), "book-A", sessionToBook),
        )
    }

    // ── 5. 合法完整链满足时才允许对应结论 ──────────────────────────────────────

    @Test
    fun `legitimate complete chain yields observed and the guard-blocked protection claim`() {
        val v = decide()
        assertTrue(v.remeasureObserved)
        assertTrue(v.windowBoundaryKnown)
        assertEquals(tRootChanged, v.postChangeFromNanos)
        assertEquals(11, v.firstChangedObserveSeq)
        assertEquals(listOf(12), v.causalVerdictEvents.map { it.seq })
        assertEquals("guard_blocked_no_dispatch", v.guardVerdict)
        assertEquals("observed", v.stage)
        assertTrue("整行未变", v.rowUnchangedAfterRemeasure)
        assertTrue(v.rowChangedColumns.isEmpty())
        assertTrue(v.identitySufficient)
        assertEquals(ReaderClampRemeasureVerdict.IdentityBinding.TARGET_BOOK, v.identityBinding)
        assertEquals("guard_blocked_and_row_unchanged", v.protectionClaim)
        assertNull(v.inconclusiveReason)
    }

    @Test
    fun `guard dispatch verdict is reported as allowed dispatch not as protection`() {
        val events = listOf(
            anchorEvent,
            evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
                "bid" to TARGET_BOOK, "index" to "2", "offsetPx" to "1214"),
            evt(12, tRootChanged + 200, ReaderClampRemeasureVerdict.DEBOUNCE_SAVE_DISPATCH,
                "bid" to TARGET_BOOK),
        )
        val v = decide(events = events)
        assertEquals("guard_allowed_dispatch", v.guardVerdict)
        assertEquals("observed", v.stage)
        assertEquals("派发保存不得被误报为拦截保护", "not_claimed", v.protectionClaim)
    }

    // ── 6. 关键证据缺失保持 inconclusive ───────────────────────────────────────

    @Test
    fun `missing key evidence stays inconclusive and never claims protection`() {
        val noBounds = decide(boundsChanged = false)
        assertEquals("inconclusive", noBounds.stage)
        assertEquals("not_claimed", noBounds.protectionClaim)

        val noMarker = decide(markerVisible = false)
        assertEquals("inconclusive", noMarker.stage)
        assertEquals("not_claimed", noMarker.protectionClaim)

        val noRow = decide(after = null)
        assertFalse(noRow.rowUnchangedAfterRemeasure)
        assertEquals("not_claimed", noRow.protectionClaim)
    }

    @Test
    fun `remeasureObserved false never coexists with a protection claim`() {
        val cases = listOf(
            decide(boundsChanged = false),
            decide(events = emptyList()),
            decide(
                events = listOf(
                    anchorEvent,
                    evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
                        "bid" to TARGET_BOOK, "index" to "$anchorIndex", "offsetPx" to "$anchorOffsetPx"),
                    blockedEvent,
                ),
            ),
        )
        for (v in cases) {
            assertFalse(v.remeasureObserved)
            assertEquals("not_claimed", v.protectionClaim)
        }
    }

    // ── 7. v3-A：请求后、真实 root 变化前的观测不得算「变化后」 ──────────────────

    @Test
    fun `observe between request and real root change cannot prove post-window protection`() {
        // 调用方在 wm size / orientation 之前记录 tRequest；真实 root 变化发生在 tRootChanged。
        // ts 落在两者之间的 observe 属于**变化前**采样，配上一个后续 blocked 也不得签保护。
        val events = listOf(
            anchorEvent,
            evt(11, tRequest + 100, ReaderClampRemeasureVerdict.OBSERVE,
                "bid" to TARGET_BOOK, "index" to "2", "offsetPx" to "1214"),
            evt(12, tRootChanged + 200, ReaderClampRemeasureVerdict.DEBOUNCE_BLOCKED_BY_SESSION_GUARD,
                "bid" to TARGET_BOOK),
        )
        val v = decide(events = events)
        assertFalse("请求后、root 变化前的观测不是变化后采样", v.remeasureObserved)
        assertNull(v.firstChangedObserveSeq)
        assertTrue("该观测也不得进入变化后有效集合", v.changedObserves.isEmpty())
        assertEquals("no_remeasure_observed", v.guardVerdict)
        assertEquals("inconclusive", v.stage)
        assertEquals("not_claimed", v.protectionClaim)
    }

    @Test
    fun `command readback time alone does not establish the post-change boundary`() {
        // 命令回读成功只证明系统接受了命令；没有实测 root 变化时刻就不存在"变化后"边界。
        val v = decide(windowBoundary = boundary(rootChangedNanos = null))
        assertFalse(v.windowBoundaryKnown)
        assertNull(v.postChangeFromNanos)
        assertFalse(v.remeasureObserved)
        assertTrue("原始观测不得凭空产生", v.rawPostChangeObserves.isEmpty())
        assertEquals("no_remeasure_observed", v.guardVerdict)
        assertEquals("inconclusive", v.stage)
        assertEquals("not_claimed", v.protectionClaim)
        assertNotNull(v.inconclusiveReason)
        assertTrue("原因必须点明是 root 变化时刻无法核实", v.inconclusiveReason!!.contains("root"))
    }

    // ── 8. v3-B：变更前的采样、变更后才落日志的裁决不能凭顺序当因果 ──────────────

    @Test
    fun `pre-change sampled verdict that lands late in the log is not causal`() {
        val events = listOf(
            anchorEvent,
            // 变化后（可核实边界内）的有效观测
            evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
                "bid" to TARGET_BOOK, "index" to "2", "offsetPx" to "1214"),
            // 采样发生在**变化之前**（ts 早于 root 变化），只是日志序号更晚（排队后落盘）
            evt(12, tRequest + 50, ReaderClampRemeasureVerdict.DEBOUNCE_BLOCKED_BY_SESSION_GUARD,
                "bid" to TARGET_BOOK),
        )
        val v = decide(events = events)
        assertTrue("变化后有效观测成立", v.remeasureObserved)
        assertTrue("seq 更大但采样于变化前 → 不得当因果证据", v.causalVerdictEvents.isEmpty())
        assertEquals("no_production_verdict", v.guardVerdict)
        assertEquals("inconclusive", v.stage)
        assertEquals("not_claimed", v.protectionClaim)
    }

    // ── 9. v3-C：缺/非法坐标不能变成有效位移 ────────────────────────────────────

    @Test
    fun `missing or illegal coordinates never become a valid displacement`() {
        val cases: List<Pair<String, Map<String, String>>> = listOf(
            "缺 index 与 offsetPx" to mapOf("bid" to TARGET_BOOK),
            "缺 offsetPx" to mapOf("bid" to TARGET_BOOK, "index" to "2"),
            "缺 index" to mapOf("bid" to TARGET_BOOK, "offsetPx" to "1214"),
            "index 非数值" to mapOf("bid" to TARGET_BOOK, "index" to "abc", "offsetPx" to "1214"),
            "offsetPx 非数值" to mapOf("bid" to TARGET_BOOK, "index" to "2", "offsetPx" to "nope"),
            "index 为负" to mapOf("bid" to TARGET_BOOK, "index" to "-1", "offsetPx" to "1214"),
            "空字符串坐标" to mapOf("bid" to TARGET_BOOK, "index" to "", "offsetPx" to "1214"),
        )
        for ((label, fields) in cases) {
            val events = listOf(
                anchorEvent,
                ReaderClampRemeasureVerdict.Evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE, fields),
                blockedEvent,
            )
            val v = decide(events = events)
            assertFalse("$label 不得产生有效位移", v.remeasureObserved)
            assertTrue("$label 必须被记录为无效坐标观测", v.invalidCoordinateObserves.size == 1)
            assertTrue("$label 不得进入有效变化集合", v.changedObserves.isEmpty())
            assertEquals("$label 不得签保护", "not_claimed", v.protectionClaim)
            assertEquals("$label 应保持 inconclusive", "inconclusive", v.stage)
        }
    }

    @Test
    fun `anchor without valid coordinates leaves displacement undefined`() {
        val events = listOf(
            anchorEvent,
            evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
                "bid" to TARGET_BOOK, "index" to "2", "offsetPx" to "1214"),
            blockedEvent,
        )
        val noAnchorIndex = ReaderClampRemeasureVerdict.decide(
            events = events, anchorSeq = anchorSeq, boundary = boundary(),
            anchorIndex = null, anchorOffsetPx = anchorOffsetPx, targetBookId = TARGET_BOOK,
            actualBoundsChanged = true, markerStillVisible = true,
            beforeRow = row(), afterRow = row(),
        )
        assertFalse("anchor 缺 index → 位移无定义", noAnchorIndex.remeasureObserved)
        assertEquals("not_claimed", noAnchorIndex.protectionClaim)
        assertTrue(noAnchorIndex.inconclusiveReason!!.contains("anchor"))
    }

    // ── 10. v3-D：身份不足不得签保护；可回溯 sid 链可以 ────────────────────────

    @Test
    fun `observation without target book identity cannot claim protection`() {
        // 生产 session_guard_observe 当前没有 bid/sid —— 这正是本轮的实际形态。
        val events = listOf(
            anchorEvent,
            evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
                "index" to "2", "offsetPx" to "1214"),
            blockedEvent,
        )
        val v = decide(events = events)
        assertTrue("坐标变化本身成立（仅表示观测到位移）", v.remeasureObserved)
        assertEquals(ReaderClampRemeasureVerdict.IdentityBinding.UNBOUND, v.identityBinding)
        assertFalse("身份不足 → identitySufficient 必须为 false", v.identitySufficient)
        assertEquals("inconclusive", v.stage)
        assertEquals("身份不足不得签保护", "not_claimed", v.protectionClaim)
        // 原始观测与缺失原因必须保留，供后续复核与补接缝
        assertTrue("原始观测必须保留", v.rawPostChangeObserves.any { it.seq == 11 })
        assertEquals("未绑定观测必须单独列出", listOf(11), v.unboundChangedObserves.map { it.seq })
        assertTrue(
            "缺失字段必须点名",
            v.identityMissingFields.contains("session_guard_observe.bid|sid"),
        )
        assertTrue(
            "原因必须说明无法归因到本书",
            v.inconclusiveReason!!.contains("身份") || v.inconclusiveReason!!.contains("bid"),
        )
    }

    @Test
    fun `production verdict without book identity is reported as identity unbound`() {
        val events = listOf(
            anchorEvent,
            evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
                "index" to "2", "offsetPx" to "1214"),
            evt(12, tRootChanged + 200, ReaderClampRemeasureVerdict.DEBOUNCE_BLOCKED_BY_SESSION_GUARD),
        )
        val v = decide(events = events)
        assertEquals("verdict_identity_unbound", v.guardVerdict)
        assertEquals("inconclusive", v.stage)
        assertEquals("not_claimed", v.protectionClaim)
        assertTrue(v.identityMissingFields.contains("debounce_verdict.bid"))
        assertTrue(v.identityMissingFields.contains("session_guard_observe.bid|sid"))
    }

    @Test
    fun `observation of another book never binds to the target book`() {
        val events = listOf(
            anchorEvent,
            evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
                "bid" to "book-B", "index" to "2", "offsetPx" to "1214"),
            blockedEvent,
        )
        val v = decide(events = events)
        assertEquals(ReaderClampRemeasureVerdict.IdentityBinding.OTHER_BOOK, v.identityBinding)
        assertFalse(v.identitySufficient)
        assertEquals(listOf(11), v.unboundChangedObserves.map { it.seq })
        assertEquals("not_claimed", v.protectionClaim)
    }

    @Test
    fun `session id chain binds an observation to the target book`() {
        // 最小接缝的另一种形态：anchor 带 sid+bid，observe 只带 sid，仍可回溯到本书。
        val events = listOf(
            evt(anchorSeq, tRequest - 500, ReaderClampRemeasureVerdict.ANCHOR,
                "bid" to TARGET_BOOK, "sid" to "S1",
                "index" to "$anchorIndex", "offsetPx" to "$anchorOffsetPx"),
            evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
                "sid" to "S1", "index" to "2", "offsetPx" to "1214"),
            blockedEvent,
        )
        val v = decide(events = events)
        assertEquals(ReaderClampRemeasureVerdict.IdentityBinding.TARGET_BOOK, v.identityBinding)
        assertTrue(v.identitySufficient)
        assertEquals("observed", v.stage)
        assertEquals("guard_blocked_and_row_unchanged", v.protectionClaim)
    }

    @Test
    fun `session id from another book does not bind to the target book`() {
        val events = listOf(
            evt(anchorSeq, tRequest - 500, ReaderClampRemeasureVerdict.ANCHOR,
                "bid" to "book-B", "sid" to "S2",
                "index" to "$anchorIndex", "offsetPx" to "$anchorOffsetPx"),
            evt(11, tRootChanged + 100, ReaderClampRemeasureVerdict.OBSERVE,
                "sid" to "S2", "index" to "2", "offsetPx" to "1214"),
            blockedEvent,
        )
        val v = decide(events = events)
        assertEquals(ReaderClampRemeasureVerdict.IdentityBinding.OTHER_BOOK, v.identityBinding)
        assertEquals("not_claimed", v.protectionClaim)
    }

    // ── 源文件字节一致性守卫：test 副本 == androidTest 探针实际使用的那份 ────────

    @Test
    fun `verdict rules file is byte identical in test and androidTest source sets`() {
        val moduleRoot = locateModuleRoot()
        val testCopy = File(moduleRoot, TEST_RULES_REL)
        val androidTestCopy = File(moduleRoot, ANDROID_TEST_RULES_REL)
        assertTrue("test 源集规则文件缺失：$testCopy", testCopy.isFile)
        assertTrue("androidTest 源集规则文件缺失：$androidTestCopy", androidTestCopy.isFile)
        assertEquals(
            "判定规则必须只有一份实现：探针所用的 androidTest 副本与离线测试所用的 test 副本必须逐字节相同",
            sha256(testCopy.readBytes()), sha256(androidTestCopy.readBytes()),
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun locateModuleRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, ANDROID_TEST_RULES_REL).isFile && File(dir, TEST_RULES_REL).isFile) return dir
            dir = dir.parentFile
        }
        error("找不到 app 模块根目录（定位判定规则源文件失败）")
    }

    private companion object {
        const val TARGET_BOOK = "book-A"
        const val PKG_REL = "java/com/creationreadingassistant/acceptance/ReaderClampRemeasureVerdict.kt"
        const val TEST_RULES_REL = "src/test/$PKG_REL"
        const val ANDROID_TEST_RULES_REL = "src/androidTest/$PKG_REL"
    }
}
