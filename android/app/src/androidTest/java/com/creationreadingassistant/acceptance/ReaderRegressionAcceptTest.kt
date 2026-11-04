package com.creationreadingassistant.acceptance

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
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
 * CRA-V0-JOINT-FREEZE-MUMU-ACCEPT-v1 —— 任务24：共同候选上的**阅读回归**与**风险 D / F 观测**设备证据。
 *
 * 只用真实路径：真实 MainActivity / 真实阅读器（TXT 上下滚动，pagerEngineOn=false）、
 * 真实 Compose 触控手势、真实系统返回键、经生产保存入口落库；位置真值一律从 Room
 * reading_progress 读取（[J1RealReaderFixtures.captureProgressRow]）。
 *
 * 门禁收紧（相对上一版）：
 *  · 阅读器身份：必须出现**真实正文锚点**（夹具唯一 marker 或章节标题）文本节点、
 *    displayed、bounds 非空；首页消失/返回控件数为 0 **不算**进入正文。
 *  · 非空 marker：前进/回读/恢复各阶段均在**阅读器仍显示正文时**采集可见锚点与非空 bounds，
 *    采集失败即明确失败（不写 null 后继续 PASS）。
 *  · 前进保存与重进：断言退出行与重进行**完整位置/locator/percent 对应**，非仅非空。
 *  · 主动回读：先等前进保存完成并采集前进已保存行，再真实反向移动采集回读已保存行，
 *    断言回读行与前进行形成对照且 updated_at 推进。
 *  · D：字号（已证未触发）+ 受控窗口/旋转第二种真实控制。
 *  · F：真实退出/快速重进/返回流程内保留事件产生时序，如实记录可达性。
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderRegressionAcceptTest {

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
    private var originalReaderMode: String? = null
    private var originalFontSize: Float? = null
    private var originalOrientation: Int? = null

    private companion object {
        const val GUARD_EVIDENCE_DIR = "j1_guard_evidence"
        const val TASK_ID = "CRA-V0-JOINT-FREEZE-MUMU-ACCEPT-v1"
        const val TAG = "ReaderRegressionAccept"
    }

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
            context = context, db = db, runId = runId,
            bookIdA = bookIdA, bookIdB = bookIdB, bookIdUnreadable = bookIdUnreadable,
            ledger = ledger,
        )
        contentA = contents.first
        contentB = contents.second
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            originalReaderMode = activity.settingsStore.reader.value.readerMode
            originalFontSize = activity.settingsStore.reader.value.fontSize
            originalOrientation = activity.requestedOrientation
        }
    }

    @After
    fun tearDown() {
        var closeError: Throwable? = null
        try {
            scenario?.onActivity { activity ->
                runBlocking {
                    val mode = originalReaderMode
                    val size = originalFontSize
                    activity.settingsStore.updateReader {
                        copy(readerMode = mode ?: readerMode, fontSize = size ?: fontSize)
                    }
                }
                originalOrientation?.let { activity.requestedOrientation = it }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "restore settings failed: $t")
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

    // ── 证据 ──────────────────────────────────────────────────────────────────

    private fun evidenceFile(): File =
        File(context.getExternalFilesDir(null), GUARD_EVIDENCE_DIR).apply { mkdirs() }

    private fun recordEvidence(step: String, payload: JSONObject): File {
        val file = File(evidenceFile(), "${runId}__${step}.json")
        val root = JSONObject().apply {
            put("task", TASK_ID); put("runId", runId); put("step", step)
            put("pid", android.os.Process.myPid()); put("packageName", context.packageName)
            put("timestamp", Instant.now().toString()); put("timestampMillis", System.currentTimeMillis())
            put("payload", payload)
        }
        file.writeText(root.toString(2))
        println("J1GUARD_EVIDENCE=${file.name}")
        println("J1GUARD_PRODUCER=$runId")
        return file
    }

    private fun rowJson(s: J1RealReaderFixtures.ProgressRowSnapshot?): JSONObject =
        s?.toJson() ?: JSONObject().put("absent", true)

    // ── 真实 UI 动作 ───────────────────────────────────────────────────────────

    private fun setScrollMode() {
        scenario!!.onActivity { activity ->
            runBlocking { activity.settingsStore.updateReader { copy(readerMode = "scroll") } }
        }
        composeRule.waitForIdle()
    }

    private fun homeSearchNodeCount(): Int =
        composeRule.onAllNodesWithContentDescription("搜索", substring = true)
            .fetchSemanticsNodes().size

    private fun readerChromeBackCount(): Int =
        composeRule.onAllNodesWithContentDescription("返回").fetchSemanticsNodes().size

    private fun waitHome() {
        composeRule.waitUntil(20_000) { homeSearchNodeCount() > 0 }
    }

    /**
     * 真实正文锚点（夹具唯一 marker 与章节标题）。用于**正向阅读器身份**与**非空 marker**
     * 门禁：必须来自真实正文文本节点，不能是固定标签或画面外围框。
     */
    private fun bodyAnchors(): List<String> = listOf(
        contentA.marker1, contentA.marker2,
        "第二章 中间深入探索", "第三章 完结尾声",
    )

    /** 当前可见的正文锚点及其非空 bounds（取第一个可见者）；无则返回 null。 */
    private fun visibleAnchorWithBounds(): Pair<String, String>? {
        for (a in bodyAnchors()) {
            val nodes = composeRule.onAllNodesWithText(a, substring = true).fetchSemanticsNodes()
            val n = nodes.firstOrNull() ?: continue
            val b = n.boundsInRoot
            if (b.width > 0f && b.height > 0f) {
                return a to "[${b.left.toInt()},${b.top.toInt()},${b.right.toInt()},${b.bottom.toInt()}]"
            }
        }
        return null
    }

    /**
     * 打开书 A 并**正向核验阅读器身份**：必须出现真实正文锚点文本节点且 bounds 非空。
     * 返回 (anchor, bounds)。加载页/错误页/首页/临时错误状态一律不满足。
     */
    private fun openBookAAndRequireReaderIdentity(): Pair<String, String> {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true)).performClick()
        composeRule.waitForIdle()
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            // 锚点可见性以「文本节点 + 非空 bounds」为准（同一文本可能有多节点，不做单节点断言）
            val hit = visibleAnchorWithBounds()
            if (hit != null) return hit
            SystemClock.sleep(200)
        }
        throw AssertionError(
            "阅读器身份核验失败：未出现书 A 的正文锚点（marker/章节标题）；" +
                "首页消失或返回控件为 0 不算进入正文",
        )
    }

    /** 阅读器仍显示正文时，必须能采集到非空锚点；否则明确失败。 */
    private fun requireVisibleAnchor(stage: String): Pair<String, String> =
        visibleAnchorWithBounds()
            ?: throw AssertionError("阶段「$stage」阅读器未显示任何正文锚点（marker/章节标题），采集失败")

    private fun waitControlsHidden() {
        composeRule.waitUntil(25_000) { readerChromeBackCount() == 0 }
    }

    private fun pressBack() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

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

    // ── 诊断事件读取（CRA_CLAMP_DIAG）───────────────────────────────────────────

    private data class DiagEvent(
        val seq: Int, val tsNanos: Long, val event: String, val fields: Map<String, String>,
    ) {
        fun int(k: String): Int? = fields[k]?.toIntOrNull()
    }

    private fun readDiag(): List<DiagEvent> {
        val sb = StringBuilder()
        try {
            val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "raw", "-s", "CRA_CLAMP_DIAG:I"))
            p.inputStream.bufferedReader().use { sb.append(it.readText()) }
            p.waitFor()
        } catch (t: Throwable) {
            Log.w(TAG, "readDiag failed: $t")
        }
        val list = ArrayList<DiagEvent>()
        for (line in sb.lines()) {
            val i = line.indexOf("seq=")
            if (i < 0) continue
            var seq = -1; var ts = -1L; var ev = ""
            val fields = HashMap<String, String>()
            for (tok in line.substring(i).trim().split(Regex("\\s+"))) {
                val eq = tok.indexOf('=')
                if (eq <= 0) continue
                val k = tok.substring(0, eq)
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

    private fun diagJson(events: List<DiagEvent>): JSONArray = JSONArray().apply {
        for (e in events) put(JSONObject().apply {
            put("seq", e.seq); put("tsNanos", e.tsNanos); put("event", e.event)
            put("fields", JSONObject(e.fields as Map<*, *>))
        })
    }

    /** 用生产保存入口写一次，建立生产口径的合法初始位置。 */
    private fun primeProductionRow(): J1RealReaderFixtures.ProgressRowSnapshot? {
        openBookAAndRequireReaderIdentity()
        waitControlsHidden()
        pressBack()
        waitHome()
        return captureRow()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. 普通前进：真实滚动前进 → 退出 → 重进；退出行与重进行完整位置对应
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun regression_txtForwardThenExitSavesNewPosition() {
        setScrollMode()
        waitHome()
        val primed = primeProductionRow()
        assertNotNull("生产首次无操作退出必须落库合法初始位置", primed)
        val before = captureRow()!!
        assertTrue("生产写入的合法初始位置必须非零", before.legacyOffset > 0)

        // 打开并正向核验阅读器身份 + 非空 marker（仍在阅读器内）
        val (openAnchor, openBounds) = openBookAAndRequireReaderIdentity()
        waitControlsHidden()
        val anchorEv = readDiag().lastOrNull { it.event == "session_guard_anchor" }
        val anchorIdx = anchorEv?.int("index") ?: -1

        // 真实向上滚动前进（多屏），直到诊断 index 推进或达上限
        val tSwipe = SystemClock.elapsedRealtimeNanos()
        var finalIdx = anchorIdx
        var swipes = 0
        for (i in 1..8) {
            swipeReaderBy(0.88f, 0.12f, 300L); SystemClock.sleep(1000L); swipes = i
            finalIdx = readDiag().filter { it.tsNanos >= tSwipe && it.event == "session_guard_observe" }
                .lastOrNull()?.int("index") ?: finalIdx
            if (finalIdx > anchorIdx) break
        }
        val observes = readDiag().filter { it.tsNanos >= tSwipe && it.event == "session_guard_observe" }
        val moved = observes.any { it.fields["moved"] == "true" }
        // 前进后仍在阅读器内采集非空 marker（失败即明确失败）
        val (fwdAnchor, fwdBounds) = requireVisibleAnchor("前进后")

        // 前进已保存行 = 退出保存（生产在退出瞬间落实时位置）
        pressBack()
        waitHome()
        val forwardSavedRow = captureRow()
        assertNotNull("前进退出后必须落库进度行", forwardSavedRow)

        // 重进（暖）——核身份 + marker + 完整位置对应
        val (reAnchor, reBounds) = openBookAAndRequireReaderIdentity()
        waitControlsHidden()
        pressBack()
        waitHome()
        val reentryRow = captureRow()

        recordEvidence("regression_txtForwardThenExitSavesNewPosition", JSONObject().apply {
            put("stage", "observed")
            put("openAnchor", openAnchor); put("openBounds", openBounds)
            put("forwardAnchor", fwdAnchor); put("forwardBounds", fwdBounds)
            put("reentryAnchor", reAnchor); put("reentryBounds", reBounds)
            put("anchorIndex", anchorIdx); put("finalIndex", finalIdx); put("swipes", swipes)
            put("guardObservedMoved", moved)
            put("observeTimeline", diagJson(observes))
            put("initialRow", rowJson(before))
            put("forwardSavedRow", rowJson(forwardSavedRow))
            put("reentryRow", rowJson(reentryRow))
        })

        assertTrue("真实向上滚动必须被守卫观测为移动", moved)
        assertTrue("前进必须推进到更后的阅读单元（anchor=$anchorIdx final=$finalIdx）", finalIdx > anchorIdx)
        assertTrue("前进必须使位置变化（初始=${before.legacyOffset} 前进后=${forwardSavedRow!!.legacyOffset}）",
            forwardSavedRow.legacyOffset != before.legacyOffset)
        assertTrue("前进已保存行 locator 必须非空", forwardSavedRow.current_location_json.orEmpty().isNotBlank())
        assertNotNull("重进后必须落库", reentryRow)
        // 完整位置/locator/percent 对应（不只非空）
        assertEquals("重进后 offset 必须与前进已保存行一致",
            forwardSavedRow.legacyOffset, reentryRow!!.legacyOffset)
        assertEquals("重进后 locator 必须与前进已保存行一致",
            forwardSavedRow.current_location_json, reentryRow.current_location_json)
        assertEquals("重进后 percent 必须与前进已保存行一致",
            forwardSavedRow.progress_percent, reentryRow.progress_percent, 0.0001f)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. 主动回读：前进已保存行 vs 回读已保存行对照 + 重进保持
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun regression_txtActiveRereadSavesNewPosition() {
        setScrollMode()
        waitHome()
        val primed = primeProductionRow()
        assertNotNull("生产首次无操作退出必须落库", primed)
        val before = captureRow()!!

        openBookAAndRequireReaderIdentity()
        waitControlsHidden()

        // 会话 1：前进（多屏）→ 退出，取「前进已保存行」
        val tFwd = SystemClock.elapsedRealtimeNanos()
        for (i in 1..5) { swipeReaderBy(0.88f, 0.12f, 300L); SystemClock.sleep(800L) }
        val fwdObs = readDiag().filter { it.tsNanos >= tFwd && it.event == "session_guard_observe" }
        val fwdIdx = fwdObs.lastOrNull()?.int("index") ?: -1
        val fwdPx = fwdObs.lastOrNull()?.int("offsetPx") ?: -1
        val (fwdAnchor, fwdBounds) = requireVisibleAnchor("前进后")
        pressBack(); waitHome()
        val forwardSaved = captureRow()
        assertNotNull("前进退出必须已保存完整行", forwardSaved)

        // 会话 2：重进 → 真实反向阅读移动（向下滑 = 回退）→ 退出，取「回读已保存行」
        openBookAAndRequireReaderIdentity()
        waitControlsHidden()
        val tBack = SystemClock.elapsedRealtimeNanos()
        for (i in 1..2) { swipeReaderBy(0.12f, 0.88f, 340L); SystemClock.sleep(900L) }
        val backObs = readDiag().filter { it.tsNanos >= tBack && it.event == "session_guard_observe" }
        val backIdx = backObs.lastOrNull()?.int("index") ?: fwdIdx
        val backPx = backObs.lastOrNull()?.int("offsetPx") ?: fwdPx
        val (backAnchor, backBounds) = requireVisibleAnchor("回读后")
        pressBack(); waitHome()
        val rereadSaved = captureRow()
        assertNotNull("回读退出必须已保存完整行", rereadSaved)

        // 会话 3：重进（暖）——保持回读位置
        openBookAAndRequireReaderIdentity()
        waitControlsHidden()
        pressBack()
        waitHome()
        val reentryRow = captureRow()

        recordEvidence("regression_txtActiveRereadSavesNewPosition", JSONObject().apply {
            put("stage", "observed")
            put("forwardAnchor", fwdAnchor); put("forwardBounds", fwdBounds)
            put("rereadAnchor", backAnchor); put("rereadBounds", backBounds)
            put("forwardIndex", fwdIdx); put("forwardOffsetPx", fwdPx)
            put("rereadIndex", backIdx); put("rereadOffsetPx", backPx)
            put("forwardObserveTimeline", diagJson(fwdObs))
            put("rereadObserveTimeline", diagJson(backObs))
            put("initialRow", rowJson(before))
            put("forwardSavedRow", rowJson(forwardSaved))
            put("rereadSavedRow", rowJson(rereadSaved))
            put("reentryRow", rowJson(reentryRow))
        })

        assertTrue("主动回读必须产生真实反向坐标（前进=$fwdIdx/$fwdPx 回读=$backIdx/$backPx）",
            backIdx < fwdIdx || (backIdx == fwdIdx && backPx < fwdPx))
        // 回读已保存行必须与前进已保存行形成对照（位置不同 + updated_at 推进）
        assertTrue("回读保存位置必须与前进已保存行不同（前进=${forwardSaved!!.legacyOffset} 回读=${rereadSaved!!.legacyOffset}）",
            rereadSaved.legacyOffset != forwardSaved.legacyOffset)
        assertTrue("回读保存 updated_at 必须晚于前进已保存行",
            rereadSaved.updated_at > forwardSaved.updated_at)
        assertTrue("回读保存 locator 必须非空", rereadSaved.current_location_json.orEmpty().isNotBlank())
        assertNotNull("重进后必须落库", reentryRow)
        assertEquals("重进后 offset 必须与回读已保存行一致",
            rereadSaved.legacyOffset, reentryRow!!.legacyOffset)
        assertEquals("重进后 locator 必须与回读已保存行一致",
            rereadSaved.current_location_json, reentryRow.current_location_json)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. 风险 D（方法一）：字号布局控制（上一轮已证未触发，保留）
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun guardD2_postAnchorRealRemeasureObserved() {
        setScrollMode()
        waitHome()
        val primed = primeProductionRow()
        assertNotNull("生产首次无操作退出必须落库", primed)
        val before = captureRow()!!

        try { Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor() } catch (t: Throwable) { Log.w(TAG, "logcat -c: $t") }

        openBookAAndRequireReaderIdentity()
        waitControlsHidden()
        val anchor = readDiag().lastOrNull { it.event == "session_guard_anchor" }
        assertNotNull("必须观测到锚定事件（恢复落位）", anchor)
        val anchorIdx = anchor!!.int("index")
        val anchorPx = anchor.int("offsetPx")
        val tRemeasure = SystemClock.elapsedRealtimeNanos()
        val preAnchor = visibleAnchorWithBounds()

        scenario!!.onActivity { activity ->
            runBlocking {
                val cur = activity.settingsStore.reader.value.fontSize
                activity.settingsStore.updateReader { copy(fontSize = (cur + 12f).coerceAtMost(48f)) }
            }
        }
        composeRule.waitForIdle()
        SystemClock.sleep(2500L)
        val postObs = readDiag().filter { it.tsNanos >= tRemeasure && it.event == "session_guard_observe" }
        val changed = postObs.filter { it.int("index") != anchorIdx || it.int("offsetPx") != anchorPx }
        val postAnchor = visibleAnchorWithBounds()
        val triggered = changed.isNotEmpty()

        pressBack(); waitHome()
        val after = captureRow()
        val overwritten = after != null && after.legacyOffset != before.legacyOffset

        recordEvidence("guardD2_postAnchorRealRemeasureObserved", JSONObject().apply {
            put("stage", "observed"); put("method", "production_fontSize_layout_control")
            put("anchorIndex", anchorIdx ?: -1); put("anchorOffsetPx", anchorPx ?: -1)
            put("preAnchor", preAnchor?.let { it.first to it.second }?.let { JSONObject().put("a", it.first).put("b", it.second) } ?: JSONObject.NULL)
            put("postAnchor", postAnchor?.let { JSONObject().put("a", it.first).put("b", it.second) } ?: JSONObject.NULL)
            put("postRemeasureObserveCount", postObs.size)
            put("postRemeasureObserveTimeline", diagJson(postObs))
            put("changedObserveCount", changed.size)
            put("triggered", triggered)
            put("initialRow", rowJson(before)); put("exitRow", rowJson(after))
            put("overwritten", overwritten)
        })
        assertNotNull("退出后进度行必须存在", after)
        Log.i(TAG, "D2(fontSize) triggered=$triggered changed=${changed.size} overwritten=$overwritten")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3b. 风险 D（方法二）：受控窗口/旋转真实控制
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 锚定后无阅读手势，用**受控窗口方向**（requestedOrientation 横屏）触发真实重测量。
     * 记录前后窗口尺寸、锚点坐标、marker、observe 时间线与前后完整行；如实判定
     * triggered/overwritten。恢复原方向在 tearDown。
     */
    @Test
    fun guardD3_postAnchorWindowOrientationRemeasureObserved() {
        setScrollMode()
        waitHome()
        val primed = primeProductionRow()
        assertNotNull("生产首次无操作退出必须落库", primed)
        val before = captureRow()!!

        try { Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor() } catch (t: Throwable) { Log.w(TAG, "logcat -c: $t") }

        openBookAAndRequireReaderIdentity()
        waitControlsHidden()
        val anchor = readDiag().lastOrNull { it.event == "session_guard_anchor" }
        assertNotNull("必须观测到锚定事件", anchor)
        val anchorIdx = anchor!!.int("index")
        val anchorPx = anchor.int("offsetPx")
        val preSize = windowSize()
        val preAnchor = visibleAnchorWithBounds()

        val tRemeasure = SystemClock.elapsedRealtimeNanos()
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        SystemClock.sleep(3000L)
        val postObs = readDiag().filter { it.tsNanos >= tRemeasure && it.event == "session_guard_observe" }
        val changed = postObs.filter { it.int("index") != anchorIdx || it.int("offsetPx") != anchorPx }
        val postSize = windowSize()
        val postAnchor = visibleAnchorWithBounds()
        val triggered = changed.isNotEmpty()

        pressBack(); waitHome()
        val after = captureRow()
        val overwritten = after != null && after.legacyOffset != before.legacyOffset

        recordEvidence("guardD3_postAnchorWindowOrientationRemeasureObserved", JSONObject().apply {
            put("stage", "observed"); put("method", "requestedOrientation_landscape")
            put("preWindowSize", preSize); put("postWindowSize", postSize)
            put("anchorIndex", anchorIdx ?: -1); put("anchorOffsetPx", anchorPx ?: -1)
            put("preAnchor", preAnchor?.let { JSONObject().put("anchor", it.first).put("bounds", it.second) } ?: JSONObject.NULL)
            put("postAnchor", postAnchor?.let { JSONObject().put("anchor", it.first).put("bounds", it.second) } ?: JSONObject.NULL)
            put("postRemeasureObserveCount", postObs.size)
            put("postRemeasureObserveTimeline", diagJson(postObs))
            put("changedObserveCount", changed.size)
            put("triggered", triggered)
            put("initialRow", rowJson(before)); put("exitRow", rowJson(after))
            put("overwritten", overwritten)
        })
        assertNotNull("退出后进度行必须存在", after)
        Log.i(TAG, "D3(orientation) triggered=$triggered changed=${changed.size} overwritten=$overwritten")
    }

    private fun windowSize(): String =
        try {
            val p = Runtime.getRuntime().exec(arrayOf("wm", "size"))
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor(); out.trim()
        } catch (t: Throwable) { "ERR:$t" }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. F：真实退出/快速重进/返回流程内保留事件产生时序（旧 UI 迟到回调可达性）
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 在真实退出 → 快速重进 → 返回流程内保留生产保存事件的**产生时序**（seq/ts）。
     * 只观测、不伪造：若未捕获到"旧会话闭包在新阅读保存之后又产生保存动作"的可达路径，
     * 明确记录 NOT_OBSERVED，不推断发生故障或已经安全。
     */
    @Test
    fun guardF_oldUiLateCallbackEventTimingObserved() {
        setScrollMode()
        waitHome()
        primeProductionRow()
        val before = captureRow()!!

        // 会话 1：移动后快速退出
        openBookAAndRequireReaderIdentity()
        waitControlsHidden()
        val tStart = SystemClock.elapsedRealtimeNanos()
        swipeReaderBy(0.80f, 0.20f, 220L)
        val tBack = SystemClock.elapsedRealtimeNanos()
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        waitHome()
        // 立即快速重进（不等防抖窗口）
        val tReentry = SystemClock.elapsedRealtimeNanos()
        openBookAAndRequireReaderIdentity()
        waitControlsHidden()
        SystemClock.sleep(2500L)
        pressBack(); waitHome()
        val after = captureRow()

        val diag = readDiag().filter { it.tsNanos >= tStart }
        val dispatch = diag.filter { it.event == "persist_current_progress_dispatch" }
        val afterReentrySaves = dispatch.filter { it.tsNanos >= tReentry }
        val sessionEvents = diag.filter {
            it.event.startsWith("session_guard") || it.event == "persist_current_progress_call"
        }

        recordEvidence("guardF_oldUiLateCallbackEventTimingObserved", JSONObject().apply {
            put("stage", "observed")
            put("tSwipeToBackMs", (tBack - tStart) / 1_000_000)
            put("dispatchTotal", dispatch.size)
            put("dispatchAfterReentry", afterReentrySaves.size)
            put("dispatchTimeline", diagJson(dispatch))
            put("sessionTimeline", diagJson(sessionEvents))
            put("initialRow", rowJson(before)); put("exitRow", rowJson(after))
            put("note", "只观测真实退出/快速重进/返回流程内的事件时序；未捕获旧闭包迟到动作即 NOT_OBSERVED，不推断")
        })
        assertNotNull("退出后进度行必须存在", after)
        Log.i(TAG, "F dispatch=${dispatch.size} afterReentry=${afterReentrySaves.size}")
    }
}
