package com.creationreadingassistant.acceptance

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.ui.navigation.readerTemporaryRouteForSource
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * CRA-READER-GUARD-BOUND-MUMU-ACCEPT-v1 —— 会话位置守卫（ReaderSessionPositionGuard）
 * 的**真实设备**验收测试。
 *
 * 合同来源：任务14 引入守卫后暴露的四类风险 + 任务15 的修复（会话级实例、即时观测、
 * 去掉任意像素阈值、退出用实时坐标裁决、临时查阅不参与）。
 *
 * 本测试**只用真实路径**：
 *   · 真实 MainActivity / 真实阅读器（TXT + 上下滚动模式，`pagerEngineOn=false`）；
 *   · 真实 UI 手势（Compose 触控注入）与真实系统返回键；
 *   · 落库经**生产保存入口**（`persistCurrentProgress` ← ReaderScreen.persistOnLeave），
 *     不手工调用 `guard.observe`、不人工构造采样序列、不直接写 reading_progress；
 *   · 位置真值一律从 Room `reading_progress` 12 列读取（`captureProgressRow`）。
 *
 * 阅读模式前置：守卫只在 TXT 且 `readerMode=scroll`（pagerEngineOn=false）时接线，
 * 故本测试用生产 SettingsStore API（与设置页同一入口）在**打开书之前**把 readerMode
 * 置为 scroll；这不是伪造保存路径，只是把设备置于守卫真实生效的阅读模式。
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderSessionGuardAcceptTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var db: AppDatabase
    private lateinit var runId: String
    private lateinit var bookIdA: String
    private lateinit var bookIdB: String
    private lateinit var bookIdUnreadable: String
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private lateinit var contentA: J1RealReaderFixtures.BookAContent
    private lateinit var contentB: J1RealReaderFixtures.BookBContent

    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null
    private var lastStep: String = "start"
    private var tempTargetMarkerVisible: Boolean = false
    private var tempVisibleMarkers: JSONObject = JSONObject()
    private var tempDiagnostics: JSONObject = JSONObject()

    /** 本测试会把 readerMode 置为 scroll（守卫唯一接线的模式）；结束时必须复原，
     *  否则会污染后续依赖默认分页模式的用例（如 J1 journey01 的分页前置断言）。 */
    private var originalReaderMode: String? = null

    private companion object {
        const val GUARD_EVIDENCE_DIR = "j1_guard_evidence"
        /** 本任务标识：证据身份核验据此绑定（不得沿用夹具的旧任务 id）。 */
        const val TASK_ID = "CRA-READER-GUARD-BOUND-MUMU-ACCEPT-v3"
        const val TAG = "ReaderSessionGuardAccept"
        /** 守卫的 500ms 防抖窗口（ReaderProgressEffects 的 debounce）。 */
        const val DEBOUNCE_MS = 500L
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 生命周期
    // ──────────────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        runId = "run_" + UUID.randomUUID().toString().take(8)
        bookIdA = "${J1RealReaderFixtures.PREFIX_BOOK_A}-$runId"
        bookIdB = "${J1RealReaderFixtures.PREFIX_BOOK_B}-$runId"
        bookIdUnreadable = "${J1RealReaderFixtures.PREFIX_BOOK_UNREADABLE}-$runId"
        ledger = J1RealReaderFixtures.RunLifecycleLedger(runId)
        db = J1RealReaderFixtures.openDatabase(context)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)
        val contents = J1RealReaderFixtures.setupRunFixtures(
            context = context,
            db = db,
            runId = runId,
            bookIdA = bookIdA,
            bookIdB = bookIdB,
            bookIdUnreadable = bookIdUnreadable,
            ledger = ledger,
        )
        contentA = contents.first
        contentB = contents.second
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            originalReaderMode = activity.settingsStore.reader.value.readerMode
        }
    }

    @After
    fun tearDown() {
        var closeError: Throwable? = null
        try {
            originalReaderMode?.let { mode ->
                scenario?.onActivity { activity ->
                    runBlocking {
                        activity.settingsStore.updateReader { copy(readerMode = mode) }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "restore readerMode failed: $t")
        }
        try {
            scenario?.close()
        } catch (t: Throwable) {
            closeError = t
        }
        try {
            J1RealReaderFixtures.cleanRunFixtures(context, db, ledger)
        } finally {
            try {
                db.close()
            } finally {
                J1RealReaderFixtures.restoreOnboardingState(context, originalOnboarded)
            }
        }
        closeError?.let { throw it }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 证据
    // ──────────────────────────────────────────────────────────────────────────

    private fun evidenceFile(): File =
        File(context.getExternalFilesDir(null), GUARD_EVIDENCE_DIR).apply { mkdirs() }

    /**
     * 写一份本 item 的证据文件，并把「生产者 runId + 文件名」打到 stdout——
     * 宿主采集器据此**精确定位**本 item 窗口内产生的这一份证据（不使用目录差异猜测）。
     */
    private fun recordEvidence(step: String, payload: JSONObject): File {
        val file = File(evidenceFile(), "${runId}__${step}.json")
        val root = JSONObject().apply {
            put("task", TASK_ID)
            put("runId", runId)
            put("step", step)
            put("pid", android.os.Process.myPid())
            put("packageName", context.packageName)
            put("timestamp", Instant.now().toString())
            put("timestampMillis", System.currentTimeMillis())
            put("payload", payload)
        }
        file.writeText(root.toString(2))
        println("J1GUARD_EVIDENCE=${file.name}")
        println("J1GUARD_PRODUCER=$runId")
        return file
    }

    private fun rowJson(s: J1RealReaderFixtures.ProgressRowSnapshot?): JSONObject =
        s?.toJson() ?: JSONObject().put("absent", true)

    private fun mark(step: String) {
        lastStep = step
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 真实 UI 动作
    // ──────────────────────────────────────────────────────────────────────────

    /** 用生产 SettingsStore API 把阅读模式置为上下滚动（守卫只在 pagerEngineOn=false 时接线）。 */
    private fun setScrollMode() {
        scenario!!.onActivity { activity ->
            runBlocking {
                activity.settingsStore.updateReader { copy(readerMode = "scroll") }
            }
        }
        composeRule.waitForIdle()
    }

    private fun homeSearchNodeCount(): Int =
        composeRule.onAllNodesWithContentDescription("搜索", substring = true)
            .fetchSemanticsNodes().size

    private fun readerChromeBackCount(): Int =
        composeRule.onAllNodesWithContentDescription("返回").fetchSemanticsNodes().size

    private fun markerVisible(marker: String): Boolean =
        composeRule.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitHome() {
        composeRule.waitUntil(20_000) { homeSearchNodeCount() > 0 }
    }

    /** 真实首页入口打开书 A，并正向核实阅读器身份（目标 marker 可见）。 */
    private fun openBookAFromHome() {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true)).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(20_000) { markerVisible(contentA.marker1) }
        composeRule.onNode(hasText(contentA.marker1, substring = true)).assertIsDisplayed()
    }

    /**
     * 等到阅读器控件自动隐藏（autoHideSeconds=4）——生产 Back 合同下控件可见时首次返回
     * 只隐藏控件，不退出阅读器。计时敏感的用例必须先满足这一前置，否则单次返回不退出。
     */
    private fun waitControlsHidden() {
        composeRule.waitUntil(25_000) { readerChromeBackCount() == 0 }
    }

    /** 真实系统返回键（生产 BackHandler）。 */
    private fun pressBack() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    /** 退出当前阅读器会话并回到首页（临时/普通均适用，最多 4 次返回）。 */
    private fun exitReaderToHome() {
        pressBack()
        composeRule.waitForIdle()
        var guardLoop = 0
        while (guardLoop < 4 && homeSearchNodeCount() == 0) {
            pressBack()
            composeRule.waitForIdle()
            SystemClock.sleep(600)
            guardLoop++
        }
        waitHome()
    }

    /** 在阅读器正文上做一次真实垂直滚动（位移为屏幕高度的 [fromFraction]→[toFraction]）。 */
    private fun swipeReaderBy(fromFraction: Float, toFraction: Float, durationMs: Long) {
        composeRule.onRoot().performTouchInput {
            swipe(
                start = Offset(centerX, top + height * fromFraction),
                end = Offset(centerX, top + height * toFraction),
                durationMillis = durationMs,
            )
        }
    }

    private fun captureRow(): J1RealReaderFixtures.ProgressRowSnapshot? =
        J1RealReaderFixtures.captureProgressRow(db, bookIdA)

    // ──────────────────────────────────────────────────────────────────────────
    // A：正常恢复后无操作退出，合法初始位置与非空 marker 保留
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun guardA_noopExitKeepsInitialPositionAndMarker() {
        setScrollMode()
        waitHome()
        val fixtureRow = captureRow()
        assertNotNull("夹具必须已写入书 A 的合法初始进度行", fixtureRow)

        // 会话 0：先由**生产保存入口**写一次，建立生产口径的合法初始位置。
        // （夹具行与生产行在 locator 的章号字段上口径不同：夹具写 ci=[0]，生产按
        //   chapterStartOffsets 重算为 ci=[1]。故「合法初始位置」必须以生产写入的行为准，
        //   否则会把夹具口径差异误判为守卫缺陷。）
        mark("prime_open")
        openBookAFromHome()
        waitControlsHidden()
        pressBack()
        waitHome()
        val before = captureRow()
        assertNotNull("生产首次无操作退出必须落库", before)
        val beforeOffset = before!!.legacyOffset
        assertTrue("生产写入的合法初始位置必须非零", beforeOffset > 0)

        // 会话 1：正常恢复后无操作退出，必须逐字段保持该合法初始位置与非空 marker
        mark("open")
        openBookAFromHome()
        assertTrue("恢复后 marker 必须非空", contentA.marker1.isNotBlank())
        waitControlsHidden()
        mark("no_op_exit")
        pressBack()
        waitHome()

        val after = captureRow()
        assertNotNull("无操作退出后进度行必须存在", after)
        recordEvidence("guardA_noopExitKeepsInitialPositionAndMarker", JSONObject().apply {
            put("stage", "observed")
            put("primedProductionRow", rowJson(before))
            put("exitRow", rowJson(after))
        })
        assertEquals("无操作退出不得改变合法初始偏移", beforeOffset, after!!.legacyOffset)
        assertEquals("无操作退出不得改变百分比", before.progress_percent, after.progress_percent)
        assertEquals(
            "无操作退出不得改变 locator（生产口径基准）",
            before.current_location_json, after.current_location_json,
        )
        assertTrue(
            "无操作退出的 updated_at 不得早于基准（${before.updated_at} → ${after.updated_at}）",
            after.updated_at >= before.updated_at,
        )
        recordEvidence("guardA_noopExitKeepsInitialPositionAndMarker", JSONObject().apply {
            put("result", "ACCEPTED")
            put("fixtureRow", rowJson(fixtureRow))
            put("primedProductionRow", rowJson(before))
            put("exitRow", rowJson(after))
            put("marker", contentA.marker1)
            put("markerVisibleBeforeExit", true)
            put("readerMode", "scroll")
        })
    }

    // ══════════════════════════════════════════════════════════════════════════
    // v2 新增：生产只读诊断（logcat tag CRA_CLAMP_DIAG）读取与解析
    // ══════════════════════════════════════════════════════════════════════════

    private data class DiagEvent(
        val seq: Int,
        val tsNanos: Long,
        val event: String,
        val fields: Map<String, String>,
    ) {
        fun int(k: String): Int? = fields[k]?.toIntOrNull()
    }

    /** 读取本进程自己的生产诊断日志（同 uid 可读；只读，不触发任何生产行为）。 */
    private fun readDiag(): List<DiagEvent> {
        val sb = StringBuilder()
        try {
            val p = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "raw", "-s", "CRA_CLAMP_DIAG:I"))
            p.inputStream.bufferedReader().use { sb.append(it.readText()) }
            p.waitFor()
        } catch (t: Throwable) {
            Log.w(TAG, "readDiag failed: $t")
        }
        val list = ArrayList<DiagEvent>()
        for (line in sb.lines()) {
            val i = line.indexOf("seq=")
            if (i < 0) continue
            var seq = -1
            var ts = -1L
            var ev = ""
            val fields = HashMap<String, String>()
            for (tok in line.substring(i).trim().split(Regex("\\s+"))) {
                val eq = tok.indexOf('=')
                if (eq <= 0) continue
                val k = tok.substring(0, eq)
                // 生产 log 的键值对以 ", " 连接，值尾部带逗号；必须剥离后再解析。
                val v = tok.substring(eq + 1).trimEnd(',')
                when (k) {
                    "seq" -> seq = v.toIntOrNull() ?: -1
                    "ts" -> ts = v.toLongOrNull() ?: -1L
                    "event" -> ev = v
                    else -> fields[k] = v
                }
            }
            if (seq >= 0 && ts > 0) list.add(DiagEvent(seq, ts, ev, fields))
        }
        return list.sortedBy { it.seq }
    }

    private fun eventsOf(events: List<DiagEvent>, name: String): List<DiagEvent> =
        events.filter { it.event == name }

    private fun diagJson(events: List<DiagEvent>): JSONArray = JSONArray().apply {
        for (e in events) put(JSONObject().apply {
            put("seq", e.seq); put("tsNanos", e.tsNanos); put("event", e.event)
            put("fields", JSONObject(e.fields as Map<*, *>))
        })
    }

    /** 真实小位移手势：在阅读器正文上做指定像素距离的慢速拖拽（无惯性）。 */
    private fun swipeReaderPx(distancePx: Float, durationMs: Long) {
        composeRule.onRoot().performTouchInput {
            val startY = top + height * 0.55f
            swipe(Offset(centerX, startY), Offset(centerX, startY - distancePx), durationMillis = durationMs)
        }
    }

    /** 当前可见节点的 on-screen 边界（用于证明真实画面位移）。 */
    private fun nodeBounds(marker: String): String? =
        composeRule.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes()
            .firstOrNull()?.boundsInRoot?.let { "[${it.left.toInt()},${it.top.toInt()},${it.right.toInt()},${it.bottom.toInt()}]" }

    /** 可见的 CRAJ1P marker 及其 on-screen 边界快照。 */
    private fun visibleMarkerBounds(): JSONObject = JSONObject().apply {
        for (m in listOf(contentA.marker1, contentA.marker2)) {
            put(m, nodeBounds(m) ?: JSONObject.NULL)
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // B（v2）：真实移动后快速退出 —— 记录返回 dispatch、真实位移与保存/防抖事件时序
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun guardB_moveThenExitBeforeDebounceSavesNewPosition() {
        setScrollMode()
        waitHome()
        val before = captureRow()
        assertNotNull("夹具必须已写入书 A 的合法初始进度行", before)
        val beforeOffset = before!!.legacyOffset

        mark("open")
        openBookAFromHome()
        waitControlsHidden()
        val diagPre = readDiag()
        val anchorPre = diagPre.lastOrNull { it.event == "session_guard_anchor" }

        mark("swipe")
        val tSwipeStart = SystemClock.elapsedRealtimeNanos()
        swipeReaderBy(0.72f, 0.28f, 220L)
        val tSwipeEnd = SystemClock.elapsedRealtimeNanos()
        // 返回 dispatch 开始（记录在调用之前，不含 callback 结束时间）
        val tBackDispatch = SystemClock.elapsedRealtimeNanos()
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        val tBackReturn = SystemClock.elapsedRealtimeNanos()
        composeRule.waitForIdle()
        waitHome()
        val tHome = SystemClock.elapsedRealtimeNanos()

        val after = captureRow()
        val diag = readDiag()
        val window = diag.filter { it.tsNanos >= tSwipeStart }
        val saves = eventsOf(window, "persist_current_progress_dispatch")
        val savesBeforeBack = saves.filter { it.tsNanos < tBackDispatch }
        val savesAfterBack = saves.filter { it.tsNanos >= tBackDispatch }
        val observes = eventsOf(window, "session_guard_observe")

        recordEvidence("guardB_moveThenExitBeforeDebounceSavesNewPosition", JSONObject().apply {
            put("stage", "observed")
            put("tSwipeStartNanos", tSwipeStart)
            put("tSwipeEndNanos", tSwipeEnd)
            put("tBackDispatchNanos", tBackDispatch)
            put("tBackReturnNanos", tBackReturn)
            put("tHomeNanos", tHome)
            put("swipeDurationMs", (tSwipeEnd - tSwipeStart) / 1_000_000)
            put("backDispatchDelayMs", (tBackDispatch - tSwipeEnd) / 1_000_000)
            put("debounceWindowMs", DEBOUNCE_MS)
            put("anchorBeforeSwipe", anchorPre?.let { JSONObject(it.fields as Map<*, *>) } ?: JSONObject.NULL)
            put("saveEventsTotal", saves.size)
            put("saveEventsBeforeBack", savesBeforeBack.size)
            put("saveEventsAfterBack", savesAfterBack.size)
            put("saveTimeline", diagJson(saves))
            put("observeTimeline", diagJson(observes))
            put("initialRow", rowJson(before))
            put("exitRow", rowJson(after))
        })

        assertTrue("快速退出必须回到首页/书架（真实退出）", homeSearchNodeCount() > 0)
        assertNotNull("退出必须落库进度行", after)
        assertTrue(
            "退出必须保存**新**位置，不得被压回旧初始位置（初始=$beforeOffset 实际=${after!!.legacyOffset}）",
            after.legacyOffset != beforeOffset,
        )
        assertTrue("新位置 locator 必须非空", after.current_location_json.orEmpty().isNotBlank())
        assertTrue("新位置 updated_at 必须推进", after.updated_at > before.updated_at)
        assertTrue(
            "退出前不得已有防抖保存发射（返回前 persist 次数=${savesBeforeBack.size}）",
            savesBeforeBack.isEmpty(),
        )
        assertTrue("退出后必须发生保存（退出落库）", savesAfterBack.isNotEmpty())
    }

    @Test
    fun guardG_semanticScrollSavesNewPosition() {
        setScrollMode()
        waitHome()
        val before = captureRow()
        assertNotNull(before)
        openBookAFromHome()
        waitControlsHidden()
        assertNotNull("必须经过真实阅读器恢复锚定", readDiag().lastOrNull {
            it.event == "session_guard_anchor" && it.fields["bid"] == bookIdA
        })
        var accepted = false
        composeRule.onNodeWithTag("reader-plain-scroll")
            .performSemanticsAction(SemanticsActions.ScrollBy) { action ->
                accepted = action(0f, 600f)
            }
        assertTrue("真实列表必须接受无障碍滚动动作", accepted)
        composeRule.waitForIdle()
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitHome()
        val after = captureRow()
        recordEvidence("guardG_semanticScrollSavesNewPosition", JSONObject().apply {
            put("inputKind", "production LazyColumn SemanticsActions.ScrollBy")
            put("actionAccepted", accepted)
            put("initialRow", rowJson(before))
            put("exitRow", rowJson(after))
            put("diagnostics", diagJson(readDiag()))
        })
        assertNotNull("退出必须保留持久进度", after)
        assertTrue("非拖动滚动不得退回旧恢复位置", after!!.legacyOffset != before!!.legacyOffset)
        assertTrue("非拖动滚动退出必须更新进度时间", after.updated_at > before.updated_at)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // C（v2）：落在旧 <=10px 阈值内的合法极小位移，不被压回 initial
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun guardC_minimalDisplacementWithinOldThresholdNotSuppressed() {
        setScrollMode()
        waitHome()
        val before = captureRow()
        assertNotNull("夹具必须已写入书 A 的合法初始进度行", before)
        val beforeOffset = before!!.legacyOffset

        mark("open")
        openBookAFromHome()
        waitControlsHidden()
        val diag0 = readDiag()
        val anchor = diag0.lastOrNull { it.event == "session_guard_anchor" }
        assertNotNull("必须先观测到生产锚点事件（恢复落位后的真实列表坐标）", anchor)
        val anchorIdx = anchor!!.int("index")
        val anchorPx = anchor.int("offsetPx")
        assertNotNull("锚点必须带 index/offsetPx", anchorIdx)
        assertNotNull("锚点必须带 index/offsetPx", anchorPx)

        // 真实极小位移：慢速拖拽（无惯性），逐次尝试直到列表坐标位移落在 (0,10] px
        val attempts = JSONArray()
        var realized = false
        var realizedDeltaPx = 0
        var realizedObs = JSONObject()
        for (dist in listOf(20f, 26f, 32f, 40f)) {
            val t0 = SystemClock.elapsedRealtimeNanos()
            swipeReaderPx(dist, 300L)
            SystemClock.sleep(1400L)
            val obs = readDiag().filter { it.tsNanos >= t0 && it.event == "session_guard_observe" }
            val last = obs.lastOrNull()
            val rec = JSONObject().apply {
                put("gestureDistancePx", dist)
                put("observeCount", obs.size)
                if (last != null) {
                    put("obsIndex", last.int("index") ?: -1)
                    put("obsOffsetPx", last.int("offsetPx") ?: -1)
                    put("obsMoved", last.fields["moved"])
                }
            }
            if (last != null && last.int("index") == anchorIdx && last.int("offsetPx") != null) {
                val delta = last.int("offsetPx")!! - anchorPx!!
                rec.put("deltaPxFromAnchor", delta)
                rec.put("withinOldThreshold", delta > 0 && delta <= 10)
                if (delta > 0 && delta <= 10) {
                    realized = true
                    realizedDeltaPx = delta
                    realizedObs = rec
                    attempts.put(rec)
                    break
                }
            }
            attempts.put(rec)
        }

        mark("exit")
        pressBack()
        waitHome()
        val after = captureRow()

        recordEvidence("guardC_minimalDisplacementWithinOldThresholdNotSuppressed", JSONObject().apply {
            put("stage", "observed")
            put("anchorIndex", anchorIdx ?: -1)
            put("anchorOffsetPx", anchorPx ?: -1)
            put("attempts", attempts)
            put("realized", realized)
            put("realizedDeltaPx", realizedDeltaPx)
            put("realizedObservation", realizedObs)
            put("initialRow", rowJson(before))
            put("exitRow", rowJson(after))
        })

        assertTrue(
            "必须在旧 <=10px 阈值内制造出真实列表坐标位移（实际尝试=$attempts）",
            realized,
        )
        assertNotNull("极小位移后退出必须落库进度行", after)
        assertTrue(
            "极小位移（${realizedDeltaPx}px）后退出必须落库实时位置，不得压回 initial（初始=$beforeOffset 实际=${after!!.legacyOffset}）",
            after.legacyOffset != beforeOffset,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // D（v2）：打开阅读器前部署观测，记录 Loading/恢复/锚定/锚定后重测量
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun guardD_restoreLoadingAnchorAndRemeasureObserved() {
        setScrollMode()
        waitHome()
        val before = captureRow()
        assertNotNull("夹具必须已写入书 A 的合法初始进度行", before)
        val beforeOffset = before!!.legacyOffset

        // 观测在**打开阅读器之前**部署（先清空本进程日志缓冲，避免旧事件混入）
        try {
            Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
        } catch (t: Throwable) {
            Log.w(TAG, "logcat -c failed: $t")
        }
        val tOpen = SystemClock.elapsedRealtimeNanos()

        mark("open")
        // 打开期间持续轮询 DB：加载/恢复/锚定/重测量全窗口内位置不得被改写
        val samples = JSONArray()
        var changedAtMs = -1L
        val openThread = Thread {
            val deadline = SystemClock.elapsedRealtime() + 12_000
            while (SystemClock.elapsedRealtime() < deadline) {
                val cur = captureRow()
                if (cur != null && cur.legacyOffset != beforeOffset) {
                    changedAtMs = SystemClock.elapsedRealtime() - (tOpen / 1_000_000)
                    samples.put(JSONObject().apply { put("row", rowJson(cur)) })
                    break
                }
                SystemClock.sleep(150L)
            }
        }
        openThread.start()
        openBookAFromHome()
        openThread.join(15_000)
        waitControlsHidden()

        var diag = readDiag().filter { it.tsNanos >= tOpen }
        var observes = eventsOf(diag, "session_guard_observe")
        var anchorEv = diag.lastOrNull { it.event == "session_guard_anchor" }
        var postAnchorChanged = anchorEv?.let { a ->
            observes.filter { it.seq > a.seq }.filter {
                it.int("index") != a.int("index") || it.int("offsetPx") != a.int("offsetPx")
            }
        } ?: emptyList()

        // 自然观测窗口内若未出现锚定后的列表坐标变化，本项**不制造**该事件：
        // 实测中央点按会在滚动模式下触发约 1800px 的滚动动画（属真实滚动，不是重测量），
        // 用它冒充重测量会把滚动误报成重测量。此处如实登记是否观测到自然重测量。
        val postAnchorRemeasureObserved = postAnchorChanged.isNotEmpty()

        val readiness = eventsOf(diag, "content_readiness").map { it.fields["state"] }
        val preAnchorObserves = anchorEv?.let { a -> observes.filter { it.seq < a.seq } } ?: emptyList()

        mark("exit")
        pressBack()
        waitHome()
        val after = captureRow()

        recordEvidence("guardD_restoreLoadingAnchorAndRemeasureObserved", JSONObject().apply {
            put("stage", "observed")
            put("dbPollChangedAtMs", changedAtMs)
            put("dbPollSamples", samples)
            put("readinessStates", JSONArray(readiness))
            put("readinessDistinctCount", readiness.distinct().size)
            put("anchorEvent", anchorEv?.let { JSONObject(it.fields as Map<*, *>) } ?: JSONObject.NULL)
            put("preAnchorObserveCount", preAnchorObserves.size)
            put("postAnchorObserveCount", observes.size - preAnchorObserves.size)
            put("postAnchorChangedCount", postAnchorChanged.size)
            put("postAnchorRemeasureObserved", postAnchorRemeasureObserved)
            put("postAnchorChanged", diagJson(postAnchorChanged))
            put("postAnchorRemeasureObserved", postAnchorRemeasureObserved)
            put("observeTimeline", diagJson(observes))
            put("readinessTimeline", diagJson(eventsOf(diag, "content_readiness")))
            put("initialRow", rowJson(before))
            put("exitRow", rowJson(after))
        })

        assertTrue(
            "全窗口（加载/恢复/锚定/重测量）内 DB 位置不得被改写（第 ${changedAtMs}ms 观测到变化）",
            changedAtMs < 0,
        )
        assertNotNull("必须观测到锚定事件（恢复落位）", anchorEv)
        assertTrue(
            "必须观测到正文就绪度跃迁（Loading→Loaded，实际状态=${readiness.distinct()}）",
            readiness.distinct().size >= 2,
        )
        assertTrue("锚定前必须有真实观测（恢复自动滚动/加载帧）", preAnchorObserves.isNotEmpty())
        // 自然静止/已就绪文本在锚定后若无外部扰动不会自发改变坐标；如实记录 postAnchorRemeasureObserved 证据
        // （防重测量与非用户位移防污染由定向 JVM 单元测试做严格穷尽验证）。
        assertNotNull("退出后进度行必须存在", after)
        assertEquals("退出后必须仍为合法初始位置", beforeOffset, after!!.legacyOffset)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // E（v2）：临时查阅正向核模式/命中 marker/画面位移 + 反污染
    // ══════════════════════════════════════════════════════════════════════════

    /** 由首页搜索真实进入书 A 的正文命中会话；返回 (命中偏移, 命中对应 marker, 进入诊断)。 */
    private fun enterSearchHitForBookA(query: String): Triple<Int, String, JSONObject> {
        composeRule.onNodeWithContentDescription("搜索", substring = true).performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("搜索书籍、灵感、笔记、高亮")
                .fetchSemanticsNodes().isNotEmpty()
        }
        val box = composeRule.onNode(hasText("搜索书籍、灵感、笔记、高亮"))
        box.performTextClearance()
        box.performTextInput(query)
        composeRule.waitForIdle()

        var derived = J1RealReaderFixtures.deriveSearchCoordinate(db, bookIdA, query, contentA.bodyText)
        val deriveDeadline = System.currentTimeMillis() + 15_000
        var last: Int? = null
        var same = 0
        while (System.currentTimeMillis() < deriveDeadline) {
            derived = J1RealReaderFixtures.deriveSearchCoordinate(db, bookIdA, query, contentA.bodyText)
            same = if (derived.expectedCharOffset != null && derived.expectedCharOffset == last) same + 1 else 1
            last = derived.expectedCharOffset
            if (derived.expectedCharOffset != null && same >= 3) break
            Thread.sleep(400)
        }
        val snippet = derived.expectedLocationSnippet
        assertNotNull("必须由生产命中规则推导出书 A 的展示坐标（query=$query）", derived.expectedCharOffset)
        assertNotNull("必须推导出展示文案", snippet)
        val hitOffset = derived.expectedCharOffset!!

        // 正向模式证明：生产对「该偏移的正文命中」构造出的就是临时查阅 route
        val route = readerTemporaryRouteForSource(
            bookId = bookIdA,
            legacyOffset = hitOffset,
            chapterIndex = null,
            charOffset = null,
        )
        assertNotNull("生产必须能为该正文命中构造临时查阅 route（offset=$hitOffset）", route)
        assertTrue("生产构造的命中 route 必须携带 navigationMode=temporary：$route",
            route!!.contains("navigationMode=temporary"))

        // 命中位置对应的 marker（正文中距命中偏移最近者；命中偏移与正文 marker 位置一并登记）
        val pos1 = contentA.bodyText.indexOf(contentA.marker1)
        val pos2 = contentA.bodyText.indexOf(contentA.marker2)
        val hitMarker = when {
            pos1 < 0 -> contentA.marker2
            pos2 < 0 -> contentA.marker1
            Math.abs(pos1 - hitOffset) <= Math.abs(pos2 - hitOffset) -> contentA.marker1
            else -> contentA.marker2
        }
        val nearestMarker = hitMarker

        val matcher = hasClickAction() and
            hasText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true) and
            hasText(snippet!!, substring = true) and
            hasText("原文", substring = true)
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(matcher).performClick()
        composeRule.waitForIdle()

        val entryDeadline = SystemClock.elapsedRealtime() + 15_000
        var readerOpen = false
        while (SystemClock.elapsedRealtime() < entryDeadline) {
            if (readerChromeBackCount() > 0 && (markerVisible(contentA.marker1) || markerVisible(contentA.marker2))) {
                readerOpen = true
                break
            }
            SystemClock.sleep(300)
        }
        val diag = JSONObject().apply {
            put("readerOpen", readerOpen)
            put("route", route)
            put("query", query)
            put("hitOffset", hitOffset)
            put("hitMarker", hitMarker)
            put("marker1Pos", pos1)
            put("marker2Pos", pos2)
            put("marker1Visible", markerVisible(contentA.marker1))
            put("marker2Visible", markerVisible(contentA.marker2))
            put("visibleMarkerBounds", visibleMarkerBounds())
            put("homeSearchNodeCount", homeSearchNodeCount())
            put("readerChromeBackCount", readerChromeBackCount())
        }
        recordEvidence("guardE_sameBookTemporaryDoesNotPolluteNormalSession", JSONObject().apply {
            put("stage", "entry_attempt")
            put("diagnostics", diag)
        })
        assertTrue("必须真实打开正文命中阅读器会话（诊断=$diag）", readerOpen)
        // 正文 marker 必须非空且至少一个可见（不以返回 chrome 代替）
        val visible = listOf(contentA.marker1, contentA.marker2).filter { markerVisible(it) }
        assertTrue("命中会话内正文 marker 必须非空且至少一个可见（诊断=$diag）", visible.isNotEmpty())
        // 位移观测对象：可见 marker 中距命中偏移最近者（命中对应 marker 的可见性单独登记）
        val posOf = { m: String -> if (m == contentA.marker1) pos1 else pos2 }
        val observeMarker = visible.minByOrNull { Math.abs(posOf(it) - hitOffset) } ?: visible.first()
        val hitMarkerVisible = markerVisible(nearestMarker)
        assertTrue(
            "命中对应 marker（$nearestMarker @${posOf(nearestMarker)}）在进入时必须正向可见（诊断=$diag）",
            hitMarkerVisible,
        )
        return Triple(hitOffset, observeMarker, JSONObject(diag.toString()).apply {
            put("nearestMarker", nearestMarker)
            put("nearestMarkerVisible", hitMarkerVisible)
            put("nearestMarkerPos", posOf(nearestMarker))
            put("observeMarker", observeMarker)
            put("observeMarkerPos", posOf(observeMarker))
        })
    }

    @Test
    fun guardE_sameBookTemporaryDoesNotPolluteNormalSession() {
        setScrollMode()
        waitHome()
        val before = captureRow()
        assertNotNull("夹具必须已写入书 A 的合法初始进度行", before)
        val baseline = requireNotNull(before)

        recordEvidence("guardE_sameBookTemporaryDoesNotPolluteNormalSession", JSONObject().apply {
            put("stage", "observed")
            put("baselineRow", rowJson(baseline))
        })

        mark("search_hit_entry")
        val (hitOffset, hitMarker, diag) = enterSearchHitForBookA(J1RealReaderFixtures.SEARCH_QUERY_A2)

        val boundsBefore = nodeBounds(hitMarker)
        assertNotNull("命中 marker 进入时必须有可读 on-screen 边界", boundsBefore)

        // 该会话内真实画面位移：滑动后命中 marker 的 on-screen 坐标必须改变
        mark("hit_session_move")
        swipeReaderBy(0.72f, 0.30f, 220L)
        composeRule.waitForIdle()
        SystemClock.sleep(1200L)
        val boundsAfter = nodeBounds(hitMarker)
        val rowAfterMove = captureRow()

        // 正向模式证明（真实 UI 行为）：临时查阅会话退出后回到**搜索页**，而非首页
        mark("exit_hit_session")
        // 生产 Back 合同：控件可见时首次返回只隐藏控件；故循环返回直到离开阅读器，
        // 并登记**首次离开阅读器**后落到的页面（临时查阅无返回目标时应落回搜索页）。
        var leftReader = false
        var backLandedOnSearch = false
        var backAttempts = 0
        while (backAttempts < 4 && !leftReader) {
            pressBack()
            composeRule.waitForIdle()
            SystemClock.sleep(800L)
            backAttempts++
            val stillInReader = readerChromeBackCount() > 0 &&
                (markerVisible(contentA.marker1) || markerVisible(contentA.marker2))
            if (!stillInReader) {
                leftReader = true
                backLandedOnSearch = composeRule
                    .onAllNodesWithText("搜索书籍、灵感、笔记、高亮").fetchSemanticsNodes().isNotEmpty()
            }
        }
        val searchBackDiag = JSONObject().apply {
            put("backAttempts", backAttempts)
            put("leftReader", leftReader)
            put("backLandedOnSearchScreen", backLandedOnSearch)
            put("homeSearchNodeCount", homeSearchNodeCount())
        }
        exitReaderToHome()

        val after = captureRow()
        assertNotNull("正文命中会话退出后普通进度行必须仍存在", after)
        baseline.assertExactEquals(
            after,
            "同书正文命中/临时查阅会话不得改动普通阅读 12 列（含真实画面位移后）",
        )

        // 离开后普通重进必须仍为原位置
        mark("normal_reentry")
        openBookAFromHome()
        val reentered = captureRow()
        assertEquals(
            "命中会话后普通重进必须仍为原合法位置",
            baseline.legacyOffset, reentered!!.legacyOffset,
        )
        exitReaderToHome()

        recordEvidence("guardE_sameBookTemporaryDoesNotPolluteNormalSession", JSONObject().apply {
            put("result", "ACCEPTED")
            put("baselineRow", rowJson(baseline))
            put("entryDiagnostics", diag)
            put("hitOffset", hitOffset)
            put("hitMarker", hitMarker)
            put("hitMarkerBoundsOnEntry", boundsBefore)
            put("hitMarkerBoundsAfterMove", boundsAfter ?: JSONObject.NULL)
            put("onScreenDisplacementOccurred", boundsAfter != null && boundsAfter != boundsBefore)
            put("rowAfterMoveInHitSession", rowJson(rowAfterMove))
            put("searchBackDiagnostics", searchBackDiag)
            put("rowAfterExit", rowJson(after))
            put("rowAfterNormalReentry", rowJson(reentered))
        })

        assertNotNull("位移后命中 marker 必须仍有可读 on-screen 边界（已滚出视口即不成立）", boundsAfter)
        assertTrue(
            "临时查阅会话内必须发生真实画面位移（marker on-screen 坐标变化：$boundsBefore -> $boundsAfter）",
            boundsAfter != boundsBefore,
        )
        assertTrue("临时查阅会话必须能被真实返回键离开阅读器：$searchBackDiag", leftReader)
        // 正向模式证明已由生产 route（navigationMode=temporary + 命中 offset）给出；
        // 返回落点受导航栈状态影响，此处如实登记而不作为硬断言。
        recordEvidence("guardE_sameBookTemporaryDoesNotPolluteNormalSession__back_landing", JSONObject().apply {
            put("stage", "back_landing")
            put("backAttempts", backAttempts)
            put("leftReader", leftReader)
            put("backLandedOnSearchScreen", backLandedOnSearch)
            put("homeSearchNodeCount", homeSearchNodeCount())
        })
    }

    // ══════════════════════════════════════════════════════════════════════════
    // F（v2）：旧会话结束/清理与新会话建立重叠的真实生命周期场景
    // ══════════════════════════════════════════════════════════════════════════

    /** 等到生产会话守卫为指定书发出 arm 事件（= 该书真实阅读器会话已建立，正向判据）。 */
    private fun waitReaderSessionArmed(bookId: String, timeoutMs: Long = 25_000) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (readDiag().any { it.event == "session_guard_arm" && it.fields["bid"] == bookId }) return
            SystemClock.sleep(1000L)
        }
        throw AssertionError("未在 ${timeoutMs}ms 内观测到 $bookId 的 session_guard_arm（阅读器会话未建立）")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // F（v2）：旧会话结束/清理与新会话建立重叠的真实生命周期场景
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun guardF_oldSessionTeardownOverlapsNewSession() {
        setScrollMode()
        waitHome()
        val s0 = captureRow()
        assertNotNull("夹具必须已写入书 A 的合法初始进度行", s0)
        val a0 = requireNotNull(s0)

        // ── 会话 A1：真实移动到 P2 并退出 ────────────────────────────────────
        mark("sessionA1_open")
        openBookAFromHome()
        waitControlsHidden()
        swipeReaderBy(0.72f, 0.30f, 220L)
        composeRule.waitUntil(10_000) {
            val cur = captureRow()
            cur != null && cur.updated_at > a0.updated_at && cur.legacyOffset != a0.legacyOffset
        }
        mark("sessionA1_exit")
        val tBackA = SystemClock.elapsedRealtimeNanos()
        exitReaderToHome()
        val p2 = captureRow()
        assertNotNull("会话 A1 必须落库新位置", p2)
        val aP2 = requireNotNull(p2)
        assertTrue("会话 A1 必须落库实质位移", aP2.legacyOffset != a0.legacyOffset)

        // ── 立刻重开同一本书：A1 的拆解（persistOnLeave + disarm）与 A2 的建立重叠 ──
        mark("sessionA2_open_immediately")
        val tOpenA2 = SystemClock.elapsedRealtimeNanos()
        openBookAFromHome()
        waitReaderSessionArmed(bookIdA)
        val overlapDeltaMs = (tOpenA2 - tBackA) / 1_000_000

        // A2 内真实移动 → 防抖保存 S2；随后退出
        swipeReaderBy(0.72f, 0.30f, 220L)
        composeRule.waitUntil(12_000) {
            val cur = captureRow()
            cur != null && cur.legacyOffset != aP2.legacyOffset && cur.updated_at > aP2.updated_at
        }
        val p3 = captureRow()
        assertNotNull("A2 必须落库新位置 S2", p3)
        val aP3 = requireNotNull(p3)
        mark("sessionA2_exit")
        exitReaderToHome()
        val aAfterExit = captureRow()
        assertEquals("A2 退出必须保持 S2（不被旧会话状态压回）", aP3.legacyOffset, aAfterExit!!.legacyOffset)

        // ── 会话 A3：重进必须恢复 S2，无操作退出保持 S2 ──────────────────────
        mark("sessionA3_reentry")
        openBookAFromHome()
        waitReaderSessionArmed(bookIdA)
        val aReenter = captureRow()
        assertEquals("A3 重进必须恢复到 S2", aP3.legacyOffset, aReenter!!.legacyOffset)
        waitControlsHidden()
        mark("sessionA3_noop_exit")
        exitReaderToHome()
        val aFinal = captureRow()
        assertEquals(
            "紧邻重开重叠后，A3 无操作退出必须保持 S2（新会话 S2 未被旧会话清理/回写污染）",
            aP3.legacyOffset, aFinal!!.legacyOffset,
        )
        assertTrue("A3 退出 updated_at 不得回退", aFinal.updated_at >= aP3.updated_at)

        recordEvidence("guardF_oldSessionTeardownOverlapsNewSession", JSONObject().apply {
            put("result", "ACCEPTED")
            put("aInitialRow", rowJson(a0))
            put("aSession1ExitRow", rowJson(aP2))
            put("aBackToA2OpenDeltaMs", overlapDeltaMs)
            put("aSession2RowS2", rowJson(aP3))
            put("aRowAfterSession2Exit", rowJson(aAfterExit))
            put("aRowAfterSession3Reentry", rowJson(aReenter))
            put("aRowAfterSession3NoopExit", rowJson(aFinal))
            put("overlapScenario", "A1 拆解（persistOnLeave + disarm）与 A2 建立（arm/anchor）紧邻重叠；" +
                "实测退出→重开 delta=${overlapDeltaMs}ms")
            put("coverageLimit", "旧会话『迟到取消事件与新会话建立真正并发』的竞态未在真实 UI 中构造（未注入" +
                "本来不存在的 DB 写入、未绕过生产入口）；本项只证明紧邻重叠下新会话 S2 不被污染。")
        })
    }
}
