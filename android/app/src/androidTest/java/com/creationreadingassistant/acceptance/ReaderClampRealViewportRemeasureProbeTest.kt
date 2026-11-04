package com.creationreadingassistant.acceptance

import android.content.Context
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.MainActivity
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * CRA-D-REAL-VIEWPORT-REMEASURE-PROBE-v5.
 * Diagnostic only: open the real TXT reader at its persisted anchor, issue no reader gesture,
 * then request a real Activity window orientation change. A probe is considered exercised only
 * when Compose's actual root bounds change while the unique body marker remains displayed.
 * It records the production guard/debounce trace and complete Room row snapshots; it does not
 * invent guard coordinates, call guard APIs, or loosen any pending/marker save protection.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderClampRealViewportRemeasureProbeTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var runId: String
    private lateinit var bookIdA: String
    private lateinit var bookIdB: String
    private lateinit var bookIdUnreadable: String
    private lateinit var contentA: J1RealReaderFixtures.BookAContent
    private lateinit var ledger: J1RealReaderFixtures.RunLifecycleLedger
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarded: Boolean? = null
    private var originalReaderMode: String? = null
    private var originalRequestedOrientation: Int? = null
    private var originalConfigurationOrientation: Int? = null
    private var runStartNanos: Long = 0L
    private var runStartSeq: Int = 0
    private var evidenceFile: File? = null
    private var orientationBaselineBounds: Bounds? = null
    private var primaryTestFailure: Throwable? = null
    private var probeMode: String = "orientation"
    private var originalWmSize: WmSizeSnapshot? = null
    private var expandedWmSize: WmSizeSnapshot? = null
    private var originalRotationSettings: RotationSettings? = null
    private var expandedRotationSettings: RotationSettings? = null
    private var expandedViewportAttempted = false
    private var fixtureSeedBefore: JSONObject? = null
    private var fixtureSeedAfter: JSONObject? = null
    private var readerModeReadback: JSONObject? = null
    private var requestedExpandedViewport: String = EXPANDED_VIEWPORT

    private companion object {
        const val TASK_ID = "CRA-D-REAL-VIEWPORT-REMEASURE-PROBE-v6"
        const val EVIDENCE_DIR = "j1_guard_evidence"
        const val EXPANDED_VIEWPORT = "2160x3840"

        /**
         * 设置恢复回读的有界超时。`SettingsStore.updateReader` 写入 DataStore 后，`reader`
         * 这个 StateFlow 由**另一条**上游收集驱动，写返回时其 `.value` 可能仍是旧值，
         * 因此必须在有界时间内等待可观测值满足期望，超时即如实失败（不得吞异常改绿）。
         */
        const val READER_MODE_READBACK_TIMEOUT_MS = 15_000L

        /** 有界等待"窗口变化后生产防抖裁决"出现的时间（防抖 500ms + 余量）。 */
        const val DEBOUNCE_VERDICT_WAIT_MS = 5_000L

        /** 生产防抖链中，会话守卫对本次坐标变化的两种真实裁决事件名。 */
        const val DEBOUNCE_BLOCKED_BY_SESSION_GUARD = "debounce_save_blocked_by_session_guard"
        const val DEBOUNCE_SAVE_DISPATCH = "debounce_save_dispatch"
    }

    // 判定逻辑集中在 ReaderClampRemeasureVerdict（androidTest 与 test 源集逐字节相同，
    // 可离线逐条反例）；探针只做采集与映射，不再持有第二套判定规则。
    private fun isDebounceEvent(event: String): Boolean =
        ReaderClampRemeasureVerdict.isDebounceEvent(event)

    private fun isSessionGuardVerdict(event: String): Boolean =
        ReaderClampRemeasureVerdict.isSessionGuardVerdict(event)

    private fun Diag.toVerdictEvt(): ReaderClampRemeasureVerdict.Evt =
        ReaderClampRemeasureVerdict.Evt(seq, tsNanos, event, fields)

    /** 完整 12 列快照 → 纯规则可比较形式（不改共享夹具，不缩减比较列）。 */
    private fun J1RealReaderFixtures.ProgressRowSnapshot.toRow12(): ReaderClampRemeasureVerdict.Row12 =
        ReaderClampRemeasureVerdict.Row12(
            book_id = book_id,
            progress_percent = progress_percent.toString(),
            last_read_at = last_read_at,
            total_reading_time_ms = total_reading_time_ms.toString(),
            completion_state = completion_state,
            current_location_json = current_location_json,
            payload = payload,
            revision = revision.toString(),
            device_id = device_id,
            updated_at = updated_at,
            completed_at = completed_at?.toString(),
            deleted_at = deleted_at,
        )

    /** 变化前后都用**非空 fixture 正文 marker** 证明"仍是同一阅读会话"。 */
    private fun visibleFixtureMarkers(root: Bounds): List<Pair<String, JSONObject>> =
        listOf(contentA.marker1, contentA.marker2)
            .mapNotNull { m -> markerBounds(m)?.let { m to it } }
            .filter { (_, b) -> markerIntersectsRoot(b, root) }

    @Before fun setUp() {
        runId = "remeasure_" + UUID.randomUUID().toString().take(8)
        bookIdA = "${J1RealReaderFixtures.PREFIX_BOOK_A}-$runId"
        bookIdB = "${J1RealReaderFixtures.PREFIX_BOOK_B}-$runId"
        bookIdUnreadable = "${J1RealReaderFixtures.PREFIX_BOOK_UNREADABLE}-$runId"
        ledger = J1RealReaderFixtures.RunLifecycleLedger(runId)
        db = J1RealReaderFixtures.openDatabase(context)
        originalOnboarded = J1RealReaderFixtures.prepareOnboardingState(context)
        val fixtures = J1RealReaderFixtures.setupRunFixtures(
            context, db, runId, bookIdA, bookIdB, bookIdUnreadable, ledger,
        )
        contentA = fixtures.first
        runBlocking {
            val original = checkNotNull(db.readingProgressDao().getByBook(bookIdA)) {
                "fixture A progress row missing before endpoint seed"
            }
            fixtureSeedBefore = JSONObject()
                .put("progressPercent", original.progress_percent)
                .put("currentLocationJson", original.current_location_json ?: JSONObject.NULL)
                .put("updatedAt", original.updated_at)
            val endpointLocator = LocatorBuilder.progressJson(
                legacyOffset = contentA.offset2,
                chapterIndex = 0,
                charOffset = contentA.offset2,
                globalOffset = contentA.offset2,
            )
            val seeded = original.copy(
                progress_percent = contentA.offset2.toFloat() / contentA.bodyText.length.toFloat() * 100f,
                current_location_json = endpointLocator,
                updated_at = Instant.now().toString(),
            )
            db.readingProgressDao().upsert(seeded)
            fixtureSeedAfter = JSONObject()
                .put("scope", "test-owned BookA fixture initialization before launching real Activity")
                .put("target", "BookA offset2 / marker2 near end of fixture body")
                .put("offset", contentA.offset2)
                .put("bodyLength", contentA.bodyText.length)
                .put("progressPercent", seeded.progress_percent)
                .put("currentLocationJson", seeded.current_location_json)
                .put("updatedAt", seeded.updated_at)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            originalReaderMode = activity.settingsStore.reader.value.readerMode
            originalRequestedOrientation = activity.requestedOrientation
            originalConfigurationOrientation = activity.resources.configuration.orientation
            runBlocking { activity.settingsStore.updateReader { copy(readerMode = "scroll") } }
        }
    }

    @After fun tearDown() {
        val failures = mutableListOf<Throwable>()
        var readerModeRestored = originalReaderMode == null
        var requestedOrientationRestored = originalRequestedOrientation == null
        var actualConfigurationOrientationRestored = originalConfigurationOrientation == null
        var wmSizeRestored = !expandedViewportAttempted
        var rotationSettingsRestored = !expandedViewportAttempted
        var scenarioClosed = false
        var fixturesCleaned = false
        var databaseClosed = false
        var onboardingRestored = false
        try {
            var store: SettingsStore? = null
            scenario?.onActivity { activity ->
                runBlocking {
                    originalReaderMode?.let { mode ->
                        activity.settingsStore.updateReader { copy(readerMode = mode) }
                    }
                }
                store = activity.settingsStore
            }
            val expectedMode = originalReaderMode
            if (expectedMode == null) {
                readerModeRestored = true
            } else if (store == null) {
                readerModeRestored = false
                failures.add(IllegalStateException("Reader mode restore could not be verified: settings store unavailable"))
            } else {
                // 有界等待可观测值真正回到原设置：DataStore 写入返回后 StateFlow 仍可能是旧值，
                // 立即读一次 `.value` 会把"延迟到达"误判为恢复失败。
                val readback = awaitReaderModeReadback(store!!, expectedMode)
                readerModeReadback = readback
                readerModeRestored = readback.getBoolean("matched")
                if (!readerModeRestored) {
                    failures.add(IllegalStateException(
                        "Reader mode did not return to its original value within " +
                            "${READER_MODE_READBACK_TIMEOUT_MS}ms (expected=$expectedMode " +
                            "observed=${readback.optString("observed")} timedOut=${readback.optBoolean("timedOut")})"
                    ))
                }
            }
        } catch (t: Throwable) {
            failures.add(IllegalStateException("Failed to restore reader mode", t))
        }
        var restoredWmSize: WmSizeSnapshot? = null
        var wmSizeImmediatelyBeforeRestore: WmSizeSnapshot? = null
        var restoredRotationSettings: RotationSettings? = null
        if (expandedViewportAttempted) {
            try {
                wmSizeImmediatelyBeforeRestore = readWmSize()
            } catch (t: Throwable) {
                failures.add(IllegalStateException("Failed to read wm size before restoration", t))
            }
            try {
                val baseline = checkNotNull(originalWmSize) { "Missing pre-change wm size snapshot" }
                shell(if (baseline.overrideSize == null) "wm size reset" else "wm size ${baseline.overrideSize}")
                restoredWmSize = readWmSize()
                wmSizeRestored = restoredWmSize!!.sameStateAs(baseline)
                check(wmSizeRestored) {
                    "wm size restore mismatch: expected=${baseline.toJson()} actual=${restoredWmSize!!.toJson()}"
                }
                restoredRotationSettings = readRotationSettings()
                rotationSettingsRestored = restoredRotationSettings == originalRotationSettings
                check(rotationSettingsRestored) {
                    "rotation settings changed: expected=${originalRotationSettings?.toJson()} actual=${restoredRotationSettings!!.toJson()}"
                }
            } catch (t: Throwable) {
                failures.add(IllegalStateException("Failed to restore/verify system viewport and rotation settings", t))
            }
        }
        try {
            scenario?.onActivity { activity ->
                originalRequestedOrientation?.let { activity.requestedOrientation = it }
                requestedOrientationRestored = originalRequestedOrientation == null ||
                    activity.requestedOrientation == originalRequestedOrientation
            }
            originalConfigurationOrientation?.let { expected ->
                composeRule.waitUntil(15_000) {
                    var current = Configuration.ORIENTATION_UNDEFINED
                    scenario!!.onActivity { activity -> current = activity.resources.configuration.orientation }
                    current == expected
                }
                scenario!!.onActivity { activity ->
                    actualConfigurationOrientationRestored =
                        activity.resources.configuration.orientation == expected
                }
            }
            check(requestedOrientationRestored) { "requestedOrientation did not return to its original value" }
            check(actualConfigurationOrientationRestored) {
                "resources.configuration.orientation did not return to its original value"
            }
        } catch (t: Throwable) {
            failures.add(IllegalStateException("Failed to restore requested/actual orientation", t))
        }
        var rootBoundsAfterRestore: Bounds? = null
        if (orientationBaselineBounds != null) {
            try {
                composeRule.waitForIdle()
                rootBoundsAfterRestore = rootBounds()
            } catch (t: Throwable) {
                failures.add(IllegalStateException("Failed to capture Compose root bounds after restore", t))
            }
        }
        try { scenario?.close(); scenarioClosed = true }
        catch (t: Throwable) { failures.add(IllegalStateException("Failed to close ActivityScenario", t)) }
        try {
            J1RealReaderFixtures.cleanRunFixtures(context, db, ledger)
            fixturesCleaned = true
        } catch (t: Throwable) {
            failures.add(IllegalStateException("Failed to clean test-owned reader fixtures", t))
        }
        try { db.close(); databaseClosed = true }
        catch (t: Throwable) { failures.add(IllegalStateException("Failed to close fixture database", t)) }
        try {
            J1RealReaderFixtures.restoreOnboardingState(context, originalOnboarded)
            onboardingRestored = true
        } catch (t: Throwable) {
            failures.add(IllegalStateException("Failed to restore onboarding state", t))
        }
        val cleanup = JSONObject()
            .put("readerModeRestored", readerModeRestored)
            .put("readerModeReadback", readerModeReadback ?: JSONObject.NULL)
            .put("originalRequestedOrientation", originalRequestedOrientation ?: JSONObject.NULL)
            .put("requestedOrientationRestored", requestedOrientationRestored)
            .put("originalConfigurationOrientation", originalConfigurationOrientation ?: JSONObject.NULL)
            .put("actualConfigurationOrientationRestored", actualConfigurationOrientationRestored)
            .put("rootBoundsBeforeOrientationChange", orientationBaselineBounds?.toJson() ?: JSONObject.NULL)
            .put("rootBoundsAfterRestore", rootBoundsAfterRestore?.toJson() ?: JSONObject.NULL)
            .put("rootBoundsExactEqualityRequired", false)
            .put("orientationRestored", requestedOrientationRestored && actualConfigurationOrientationRestored)
            .put("probeMode", probeMode)
            .put("label", "$TASK_ID:$probeMode:realWindowRemeasureAfterRestoreWithoutReaderGesture")
            .put("originalWmSize", originalWmSize?.toJson() ?: JSONObject.NULL)
            .put("expandedWmSize", expandedWmSize?.toJson() ?: JSONObject.NULL)
            .put("wmSizeImmediatelyBeforeRestore", wmSizeImmediatelyBeforeRestore?.toJson() ?: JSONObject.NULL)
            .put("restoredWmSize", restoredWmSize?.toJson() ?: JSONObject.NULL)
            .put("wmSizeRestored", wmSizeRestored)
            .put("originalRotationSettings", originalRotationSettings?.toJson() ?: JSONObject.NULL)
            .put("restoredRotationSettings", restoredRotationSettings?.toJson() ?: JSONObject.NULL)
            .put("rotationSettingsRestored", rotationSettingsRestored)
            .put("fixtureSeedBefore", fixtureSeedBefore ?: JSONObject.NULL)
            .put("fixtureSeedAfter", fixtureSeedAfter ?: JSONObject.NULL)
            .put("scenarioClosed", scenarioClosed)
            .put("fixturesCleaned", fixturesCleaned)
            .put("databaseClosed", databaseClosed)
            .put("onboardingRestored", onboardingRestored)
            .put("restored", failures.isEmpty())
            .put("testFailureType", primaryTestFailure?.javaClass?.name ?: JSONObject.NULL)
            .put("testFailureMessage", primaryTestFailure?.message ?: JSONObject.NULL)
            .put("failureMessages", JSONArray().apply { failures.forEach { put(it.message ?: it.javaClass.name) } })
        try {
            writeTeardownEvidence(cleanup)
        } catch (t: Throwable) {
            failures.add(IllegalStateException("Failed to record teardown/restoration outcome", t))
        }
        if (failures.isNotEmpty()) {
            val restorationFailure = failures.first()
            failures.drop(1).forEach(restorationFailure::addSuppressed)
            val testFailure = primaryTestFailure
            if (testFailure != null) {
                testFailure.addSuppressed(restorationFailure)
                throw testFailure
            }
            throw restorationFailure
        }
    }

    @Test fun realWindowRemeasureAfterRestoreWithoutReaderGesture() = captureTestFailure {
        waitHome()
        val before = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        assertNotNull("显式 test-only endpoint seed 必须建立恢复基线", before)

        runStartSeq = readDiag().maxOfOrNull { it.seq } ?: 0
        runStartNanos = SystemClock.elapsedRealtimeNanos()
        openBookA()
        waitControlsHidden()
        val anchor = waitForAnchor()
        // 前置条件：真实 anchor 必须携带 index/offsetPx（缺一即无法判定重测量语义）
        val anchorPresent = anchor.int("index") != null && anchor.int("offsetPx") != null
        val rootBefore = rootBounds()
        // 前置条件：变化前必须有**非空 fixture 正文 marker** 可见且与 root 相交（正向身份，不得弱化）
        val markerBeforeVisible = visibleFixtureMarkers(rootBefore)
        val markerBefore = markerBeforeVisible.firstOrNull()?.second
        assertNotNull("恢复后必须看到真实正文 marker", markerBefore)
        assertTrue("真实 anchor 必须携带 index/offsetPx，否则无法判定重测量语义", anchorPresent)
        assertTrue("变化前 fixture 正文 marker 必须可见且与 root 相交", markerBeforeVisible.isNotEmpty())
        val initialViewportCallback = runDiagnostics().lastOrNull {
            it.event == "reader_viewport_layout" &&
                it.fields["bid"] == bookIdA && it.int("width") != null && it.int("height") != null
        }
        orientationBaselineBounds = rootBefore
        // ── 时间边界三分（v3）：请求时刻 / 命令回读时刻 / 实测 root 变化时刻 ──────────
        // v2 把"操作请求时刻" tChange 直接当成"变化后"起点，于是请求之后、真实 Compose root
        // 变化之前的 observe 会被当成变化后采样。v3 只认实测的 [tRootChanged]。
        val tRequest = SystemClock.elapsedRealtimeNanos()
        var tCommandDone: Long? = null
        var tRootChanged: Long? = null
        var tRootPollConfirmed: Long? = null
        var productionViewportBoundary: Diag? = null
        probeMode = InstrumentationRegistry.getArguments().getString("craProbeViewport") ?: "orientation"
        check(probeMode == "orientation" || probeMode == "expanded") {
            "craProbeViewport must be omitted/orientation or expanded, was '$probeMode'"
        }
        // 展开尺寸可由本轮参数指定，便于在不改代码的前提下选择"能改变布局且仍保留正文 marker"的尺寸。
        requestedExpandedViewport =
            InstrumentationRegistry.getArguments().getString("craProbeExpandedSize")?.takeIf { it.isNotBlank() }
                ?: EXPANDED_VIEWPORT
        check(Regex("^\\d{2,5}x\\d{2,5}$").matches(requestedExpandedViewport)) {
            "craProbeExpandedSize must look like WxH, was '$requestedExpandedViewport'"
        }
        val configOrientationBeforeChange = currentConfigurationOrientation()
        if (probeMode == "expanded") {
            originalWmSize = readWmSize()
            originalRotationSettings = readRotationSettings()
            expandedViewportAttempted = true
            shell("wm size $requestedExpandedViewport")
            expandedWmSize = readWmSize()
            check(expandedWmSize!!.overrideSize == requestedExpandedViewport) {
                "wm size did not apply requested controlled viewport: ${expandedWmSize!!.toJson()}"
            }
            // 命令回读时刻：只证明命令被系统接受，仍不等于 Compose 已完成重新测量。
            tCommandDone = SystemClock.elapsedRealtimeNanos()
            expandedRotationSettings = readRotationSettings()
            check(expandedRotationSettings == originalRotationSettings) {
                "viewport command unexpectedly changed rotation settings: before=${originalRotationSettings?.toJson()} after=${expandedRotationSettings?.toJson()}"
            }
        } else {
            scenario!!.onActivity {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            // 命令回读：requestedOrientation 已实际生效（仍不等于 Compose 已完成重新测量）。
            var requestedReadback: Int? = null
            scenario!!.onActivity { requestedReadback = it.requestedOrientation }
            check(requestedReadback == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                "requestedOrientation did not apply LANDSCAPE: $requestedReadback"
            }
            tCommandDone = SystemClock.elapsedRealtimeNanos()
        }

        var rootAfter: Bounds? = null
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            composeRule.waitForIdle()
            rootAfter = rootBounds()
            if (rootAfter!!.width != rootBefore.width || rootAfter.height != rootBefore.height) {
                // 保存根节点轮询确认时间，但严格边界只来自真实正文宿主的生产 layout callback。
                tRootPollConfirmed = SystemClock.elapsedRealtimeNanos()
                break
            }
            SystemClock.sleep(100)
        }
        val actualBoundsChanged = rootAfter!!.width != rootBefore.width || rootAfter.height != rootBefore.height
        val postRequestViewportCallbacks = runDiagnostics().filter {
            it.event == "reader_viewport_layout" && it.fields["bid"] == bookIdA && it.tsNanos > tRequest
        }
        val initialWidth = initialViewportCallback?.int("width")
        val initialHeight = initialViewportCallback?.int("height")
        productionViewportBoundary = postRequestViewportCallbacks.firstOrNull { callback ->
            val width = callback.int("width")
            val height = callback.int("height")
            width != null && height != null && width > 0 && height > 0 &&
                initialWidth != null && initialHeight != null &&
                (width != initialWidth || height != initialHeight)
        }
        tRootChanged = productionViewportBoundary?.tsNanos
        val markerAfterVisible = if (actualBoundsChanged) visibleFixtureMarkers(rootAfter!!) else emptyList()
        val markerAfter = markerAfterVisible.firstOrNull()?.second
        val readerMarkerStillVisible = markerAfterVisible.isNotEmpty()

        // 观察窗口 1：**可核实的变化后边界**内至少出现一次即时 observe。
        // root 变化时刻尚未实测到时不存在"变化后"边界，不做无意义等待。
        val rootBoundary = tRootChanged
        var postRootObserveWindowStartedNanos: Long? = null
        var postRootObserveWindowEndedNanos: Long? = null
        var postRootObservePollCount = 0
        val observeComposeClockStartMillis = composeRule.mainClock.currentTime
        var firstPostRootObserve: Diag? = null
        var diag = runDiagnostics()
        if (rootBoundary != null) {
            postRootObserveWindowStartedNanos = SystemClock.elapsedRealtimeNanos()
            val observationDeadline = SystemClock.elapsedRealtime() + 2_000
            while (SystemClock.elapsedRealtime() < observationDeadline) {
                // Wall-clock sleep does not advance Compose's effect TestCoroutineScheduler.
                // Drive real production coroutines, then read actual UI/logcat events.
                composeRule.mainClock.advanceTimeBy(100L)
                composeRule.waitForIdle()
                diag = runDiagnostics()
                postRootObservePollCount++
                firstPostRootObserve = diag.firstOrNull {
                    it.seq >= anchor.seq && it.tsNanos >= rootBoundary && it.event == "session_guard_observe"
                }
                if (firstPostRootObserve != null) break
                SystemClock.sleep(100)
            }
            postRootObserveWindowEndedNanos = SystemClock.elapsedRealtimeNanos()
        }
        // 观察窗口 2：**有界等待生产防抖裁决出现**。防抖窗口 500ms；这里等到真实生产事件
        // 落地为止（不是"多等一次 yield"），因为守卫的真实裁决只存在于该生产事件里。
        var postRootDebounceWindowStartedNanos: Long? = null
        var postRootDebounceWindowEndedNanos: Long? = null
        val observeComposeClockEndMillis = composeRule.mainClock.currentTime
        var postRootDebouncePollCount = 0
        val debounceComposeClockStartMillis = composeRule.mainClock.currentTime
        var postChangeDebounce = emptyList<Diag>()
        if (rootBoundary != null) {
            postRootDebounceWindowStartedNanos = SystemClock.elapsedRealtimeNanos()
            val debounceDeadline = SystemClock.elapsedRealtime() + DEBOUNCE_VERDICT_WAIT_MS
            while (SystemClock.elapsedRealtime() < debounceDeadline) {
                composeRule.mainClock.advanceTimeBy(100L)
                composeRule.waitForIdle()
                diag = runDiagnostics()
                postRootDebouncePollCount++
                postChangeDebounce = diag.filter {
                    it.seq >= anchor.seq && it.tsNanos >= rootBoundary && isDebounceEvent(it.event)
                }
                if (postChangeDebounce.any { isSessionGuardVerdict(it.event) }) break
                SystemClock.sleep(100)
            }
            postRootDebounceWindowEndedNanos = SystemClock.elapsedRealtimeNanos()
        }
        val debounceComposeClockEndMillis = composeRule.mainClock.currentTime
        val afterRemeasure = J1RealReaderFixtures.captureProgressRow(db, bookIdA)
        val relevant = diag.filter { it.seq >= anchor.seq }
        val afterWindowChange = if (rootBoundary == null) emptyList() else relevant.filter {
            it.tsNanos >= rootBoundary
        }
        val observes = relevant.filter { it.event == "session_guard_observe" }
        val postChangeObserves = afterWindowChange.filter { it.event == "session_guard_observe" }
        val debounce = relevant.filter { ReaderClampRemeasureVerdict.isDebounceEvent(it.event) }
        // ── 判定只有一份实现：纯规则对象 ReaderClampRemeasureVerdict（离线可逐条反例）──
        // 它同时负责：完整 12 列比较、变化后**有效**观测（坐标存在且合法、晚于实测 root 变化
        // 时刻）、身份绑定、以及"防抖裁决是否因果晚于该观测"。探针只把**已采集的真实事件与行**
        // 喂进去，不做本地放宽或二次解释，也不调用 guard / 伪造坐标 / 注入手势。
        val verdict = ReaderClampRemeasureVerdict.decide(
            events = relevant.map { it.toVerdictEvt() },
            anchorSeq = anchor.seq,
            boundary = ReaderClampRemeasureVerdict.WindowChangeBoundary(
                requestNanos = tRequest,
                commandDoneNanos = tCommandDone,
                rootChangedNanos = tRootChanged,
                rootBoundsChanged = actualBoundsChanged,
            ),
            anchorIndex = anchor.int("index"),
            anchorOffsetPx = anchor.int("offsetPx"),
            targetBookId = bookIdA,
            actualBoundsChanged = actualBoundsChanged,
            markerStillVisible = readerMarkerStillVisible,
            beforeRow = before?.toRow12(),
            afterRow = afterRemeasure?.toRow12(),
        )
        // 「有效变化观测」也只由纯规则给出（避免探针里出现"null 与 anchor 不等"的第二套判定）。
        val changed = relevant.filter { it.seq in verdict.changedObserves.map { v -> v.seq }.toSet() }
        // 因果裁决事件的原始 Diag（保留原始证据，供复核；判定本身来自 verdict）。
        val causalVerdictSeqs = verdict.causalVerdictEvents.map { it.seq }.toSet()
        val causalVerdictDiags = relevant.filter { it.seq in causalVerdictSeqs }
        val guardVerdict = verdict.guardVerdict
        val rowUnchangedAfterRemeasure = verdict.rowUnchangedAfterRemeasure
        exitReaderToHome()
        val afterExit = J1RealReaderFixtures.captureProgressRow(db, bookIdA)

        writeEvidence(JSONObject().apply {
            // 退出判定：由纯规则对象给出，只有「前置条件齐备 + 真实窗口变化 + 变化后同会话
            // marker 可见 + 有因果关联的生产守卫裁决」才允许 observed；任一关键条件缺失一律
            // inconclusive，绝不因坐标变化就宣布"重测量保护通过"。
            put("stage", verdict.stage)
            put("preconditions", JSONObject()
                .put("realAnchorWithIndexAndOffset", anchorPresent)
                .put("markerVisibleBeforeNonEmpty", markerBeforeVisible.isNotEmpty())
                .put("composeRootBoundsCapturedBefore", rootBefore.width > 0f && rootBefore.height > 0f))
            put("inconclusiveReason", when {
                !anchorPresent -> "真实 anchor 缺 index/offsetPx"
                else -> verdict.inconclusiveReason ?: JSONObject.NULL
            })
            put("probeMode", probeMode)
            // v3：窗口变化的三类时间边界，全部如实记录；判定只使用 rootChangedNanos。
            put("windowChangeBoundary", JSONObject()
                .put("requestNanos", tRequest)
                .put("commandDoneNanos", tCommandDone ?: JSONObject.NULL)
                .put("rootChangedNanos", tRootChanged ?: JSONObject.NULL)
                .put("rootPollConfirmedNanos", tRootPollConfirmed ?: JSONObject.NULL)
                .put("productionViewportCallback", productionViewportBoundary?.toJson() ?: JSONObject.NULL)
                .put("initialProductionViewportCallback", initialViewportCallback?.toJson() ?: JSONObject.NULL)
                .put("productionViewportCallbackChanged", productionViewportBoundary != null)
                .put("rootBoundsChanged", actualBoundsChanged)
                .put("postChangeFromNanos", verdict.postChangeFromNanos ?: JSONObject.NULL)
                .put("windowBoundaryKnown", verdict.windowBoundaryKnown))
            put("postRootObserveSamplingWindow", JSONObject()
                .put("startNanos", postRootObserveWindowStartedNanos ?: JSONObject.NULL)
                .put("endNanos", postRootObserveWindowEndedNanos ?: JSONObject.NULL)
                .put("pollCount", postRootObservePollCount)
                .put("composeClockStartMillis", observeComposeClockStartMillis)
                .put("composeClockEndMillis", observeComposeClockEndMillis)
                .put("firstObserveWithinWaitSeq", firstPostRootObserve?.seq ?: JSONObject.NULL)
                .put("firstObserveWithinWaitNanos", firstPostRootObserve?.tsNanos ?: JSONObject.NULL)
                .put("deadlineMillis", 2_000))
            put("postRootDebounceSamplingWindow", JSONObject()
                .put("startNanos", postRootDebounceWindowStartedNanos ?: JSONObject.NULL)
                .put("endNanos", postRootDebounceWindowEndedNanos ?: JSONObject.NULL)
                .put("pollCount", postRootDebouncePollCount)
                .put("composeClockStartMillis", debounceComposeClockStartMillis)
                .put("composeClockEndMillis", debounceComposeClockEndMillis)
                .put("clockPolicy", "advanceTimeBy(100L) + waitForIdle per bounded poll; real production events required")
                .put("deadlineMillis", DEBOUNCE_VERDICT_WAIT_MS))
            // v3：身份与作用域分离 —— PID/run 裁剪不等于已绑定本书，这里如实记录绑定结论与缺字段。
            put("identitySufficient", verdict.identitySufficient)
            put("identityBinding", verdict.identityBinding?.name ?: JSONObject.NULL)
            put("identityMissingFields", JSONArray().apply {
                verdict.identityMissingFields.forEach { put(it) }
            })
            put("requiredProductionSeam", JSONArray().apply {
                ReaderClampRemeasureVerdict.REQUIRED_PRODUCTION_SEAM.forEach { put(it) }
            })
            put("rawPostChangeObserveCount", verdict.rawPostChangeObserves.size)
            put("invalidCoordinateObserveCount", verdict.invalidCoordinateObserves.size)
            put("invalidCoordinateObserveSeqs", JSONArray().apply {
                verdict.invalidCoordinateObserves.forEach { put(it.seq) }
            })
            put("unboundChangedObserveCount", verdict.unboundChangedObserves.size)
            put("unboundChangedObserveSeqs", JSONArray().apply {
                verdict.unboundChangedObserves.forEach { put(it.seq) }
            })
            // 方法标签与**实际请求尺寸及回读尺寸**一致，不再用固定常量冒充本轮实际值。
            put(
                "method",
                if (probeMode == "expanded") {
                    "UiAutomation wm size requested=$requestedExpandedViewport " +
                        "applied=${expandedWmSize?.overrideSize ?: "unread"}"
                } else {
                    "Activity.requestedOrientation=LANDSCAPE"
                },
            )
            put("expandedViewportRequested", requestedExpandedViewport)
            put("expandedViewportAppliedReadback", expandedWmSize?.overrideSize ?: JSONObject.NULL)
            put("diagnosticSamplingPid", android.os.Process.myPid())
            put("diagnosticSamplingScope", "CRA_CLAMP_DIAG logcat records from this instrumentation process only; additionally bounded by run start time, sequence, and this run's book id when present")
            put("readerGestureIssued", false)
            put("anchorEvent", anchor.toJson())
            put("fixtureSeedBefore", fixtureSeedBefore ?: JSONObject.NULL)
            put("fixtureSeedAfter", fixtureSeedAfter ?: JSONObject.NULL)
            put("rootBoundsBefore", rootBefore.toJson())
            put("rootBoundsAfter", rootAfter.toJson())
            put("actualComposeRootBoundsChanged", actualBoundsChanged)
            put("requestedOrientationBeforeChange", originalRequestedOrientation ?: JSONObject.NULL)
            put("configurationOrientationBeforeChange", configOrientationBeforeChange)
            put("configurationOrientationAfterChange", currentConfigurationOrientation())
            put("originalWmSize", originalWmSize?.toJson() ?: JSONObject.NULL)
            put("expandedWmSize", expandedWmSize?.toJson() ?: JSONObject.NULL)
            put("originalRotationSettings", originalRotationSettings?.toJson() ?: JSONObject.NULL)
            put("expandedRotationSettings", expandedRotationSettings?.toJson() ?: JSONObject.NULL)
            put("markerBefore", markerBefore)
            put("markerAfter", markerAfter ?: JSONObject.NULL)
            put("readerMarkerStillVisible", readerMarkerStillVisible)
            put("markerBeforeVisible", JSONArray().apply { markerBeforeVisible.forEach { put(it.first) } })
            put("markerAfterVisible", JSONArray().apply { markerAfterVisible.forEach { put(it.first) } })
            put("fixtureMarkersProbed", JSONArray(listOf(contentA.marker1, contentA.marker2)))
            // 保留更严格的那一项原始数据（marker2 是否仍可见）供复核；同会话身份判定用
            // "至少一个 fixture 正文 marker 可见"，因为放大视口后具体 marker 滚出视野是
            // 窗口变化的正常后果，不等于丢失阅读会话。
            put("marker2BeforeVisible", markerBeforeVisible.any { it.first == contentA.marker2 })
            put("marker2AfterVisible", markerAfterVisible.any { it.first == contentA.marker2 })
            put("requestedExpandedViewport", requestedExpandedViewport)
            put("fullDiagnosticTimelineSinceAnchor", JSONArray().apply { relevant.forEach { put(it.toJson()) } })
            put("eventsSinceWindowChange", JSONArray().apply { afterWindowChange.forEach { put(it.toJson()) } })
            put("observeTimeline", JSONArray().apply { observes.forEach { put(it.toJson()) } })
            put("postWindowChangeObserveTimeline", JSONArray().apply { postChangeObserves.forEach { put(it.toJson()) } })
            put("firstCapturedPostRootObserve", postChangeObserves.firstOrNull()?.toJson() ?: JSONObject.NULL)
            put("debounceAndPersistTimeline", JSONArray().apply { debounce.forEach { put(it.toJson()) } })
            put("changedObserveCount", changed.size)
            put("changedObserveTimeline", JSONArray().apply { changed.forEach { put(it.toJson()) } })
            // 坐标变化只说明"发生了重测量"，**不等于**"保护通过"。保护结论必须由生产裁决派生。
            put("remeasureObserved", verdict.remeasureObserved)
            put("firstChangedObserveSeq", verdict.firstChangedObserveSeq ?: JSONObject.NULL)
            put("postChangeObserveCount", postChangeObserves.size)
            put("postChangeDebounceTimeline", JSONArray().apply { postChangeDebounce.forEach { put(it.toJson()) } })
            put("causalVerdictTimeline", JSONArray().apply { causalVerdictDiags.forEach { put(it.toJson()) } })
            put("causalVerdictCount", causalVerdictDiags.size)
            put("guardVerdict", verdict.guardVerdict)
            put(
                "guardVerdictSource",
                "production debounce events causally AFTER the post-change coordinate observation " +
                    "and identity-bound to the target book (no guard API call, no synthetic coordinates)",
            )
            // 完整 12 列比较结果：列出实际变化的列名；只有空列表才算整行未变。
            put("rowChangedColumns", JSONArray().apply { verdict.rowChangedColumns.forEach { put(it) } })
            put("rowUnchangedAfterRemeasure", verdict.rowUnchangedAfterRemeasure)
            put("protectionClaim", verdict.protectionClaim)
            put("initialProductionRow12", before!!.toJson())
            put("rowAfterRemeasureBeforeExit12", afterRemeasure?.toJson() ?: JSONObject.NULL)
            put("rowAfterExit12", afterExit?.toJson() ?: JSONObject.NULL)
        })
        assertTrue("实际 Compose 窗口边界未变化，无法证明发生了真实重测量", actualBoundsChanged)
        // 关键条件缺失必须明确 inconclusive/失败：marker 丢失或拿不到生产裁决时，
        // 绝不能输出"重测量保护通过"。这里用带 inconclusive 语义的失败信息，避免被读成产品判定。
        if (!readerMarkerStillVisible) {
            fail(
                "INCONCLUSIVE: 窗口变化后同会话 fixture 正文 marker 不可见 " +
                    "(before=${markerBeforeVisible.map { it.first }}, after=${markerAfterVisible.map { it.first }}, " +
                    "probed=${listOf(contentA.marker1, contentA.marker2)})；" +
                    "本 run 不能对 D 风险作任何结论（不是产品判定）。" +
                    "下一轮请用 --es craProbeExpandedSize 选择仍保留正文 marker 的尺寸后重跑。"
            )
        }
        // v3：只要判定没能到达 observed（含身份不足、root 变化时刻无法核实、拿不到因果裁决），
        // 一律带 INCONCLUSIVE 语义失败 —— 绝不"没结论也算过"。
        if (verdict.stage != "observed") {
            fail(
                "INCONCLUSIVE: " + (verdict.inconclusiveReason ?: "窗口变化后未观察到与本次观测" +
                    "因果关联的生产防抖裁决事件") +
                    "（stage=${verdict.stage}，verdict=$guardVerdict，${DEBOUNCE_VERDICT_WAIT_MS}ms 窗口" +
                    "，windowBoundaryKnown=${verdict.windowBoundaryKnown}" +
                    "，identitySufficient=${verdict.identitySufficient}" +
                    "，missing=${verdict.identityMissingFields}）；" +
                    "坐标变化本身不足以宣布重测量保护通过。"
            )
        }
        assertNotNull("阅读器退出后进度行缺失", afterExit)
    }

    private inline fun captureTestFailure(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            primaryTestFailure = t
            throw t
        }
    }

    private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        fun toJson() = JSONObject().put("left", left).put("top", top).put("right", right)
            .put("bottom", bottom).put("width", width).put("height", height)
    }

    private data class WmSizeSnapshot(val output: String, val physical: String, val overrideSize: String?) {
        fun sameStateAs(other: WmSizeSnapshot): Boolean = physical == other.physical && overrideSize == other.overrideSize
        fun toJson() = JSONObject().put("physical", physical).put("override", overrideSize ?: JSONObject.NULL)
            .put("raw", output)
    }

    private data class RotationSettings(val accelerometerRotation: String, val userRotation: String) {
        fun toJson() = JSONObject().put("accelerometer_rotation", accelerometerRotation)
            .put("user_rotation", userRotation)
    }

    private data class Diag(val seq: Int, val tsNanos: Long, val event: String, val fields: Map<String, String>) {
        fun int(key: String): Int? = fields[key]?.toIntOrNull()
        fun toJson() = JSONObject().put("seq", seq).put("tsNanos", tsNanos)
            .put("event", event).put("fields", JSONObject(fields))
    }

    /**
     * 有界等待 `SettingsStore.reader` 可观测值回到 [expected]，并产出可核实的回读证据。
     *
     * 依据 DataStore/Flow 的实际保证：`updateReader` 的 `ds.edit{}` 只保证写入完成，
     * `reader` 这个 StateFlow 的更新由**另一条**上游收集驱动，因此写入返回后
     * `reader.value` 可能仍是旧值。这里用 `first { predicate }` 订阅等待真实发射，
     * 外层 `withTimeoutOrNull` 给出有界超时；超时返回 null ⇒ 如实失败，不用 sleep 或吞异常改绿。
     */
    private fun awaitReaderModeReadback(store: SettingsStore, expected: String): JSONObject {
        val started = SystemClock.elapsedRealtime()
        val observed = runBlocking {
            withTimeoutOrNull(READER_MODE_READBACK_TIMEOUT_MS) {
                store.reader.first { it.readerMode == expected }.readerMode
            }
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        return JSONObject()
            .put("expected", expected)
            .put("observed", observed ?: store.reader.value.readerMode)
            .put("matched", observed == expected)
            .put("timedOut", observed == null)
            .put("elapsedMillis", elapsed)
            .put("timeoutMillis", READER_MODE_READBACK_TIMEOUT_MS)
            .put("method", "SettingsStore.reader(StateFlow).first { readerMode == expected } under withTimeoutOrNull")
            .put("immediateValueReadIsInsufficient", true)
    }

    private fun rootBounds(): Bounds {
        val rect = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        return Bounds(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun currentConfigurationOrientation(): Int {
        var orientation = Configuration.ORIENTATION_UNDEFINED
        scenario!!.onActivity { orientation = it.resources.configuration.orientation }
        return orientation
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }.trim()
    }

    private fun readWmSize(): WmSizeSnapshot {
        val output = shell("wm size")
        val physical = Regex("(?m)^Physical size:\\s*(\\d+x\\d+)\\s*$").find(output)?.groupValues?.get(1)
            ?: error("wm size output omitted Physical size: $output")
        val overrideSize = Regex("(?m)^Override size:\\s*(\\d+x\\d+)\\s*$").find(output)?.groupValues?.get(1)
        return WmSizeSnapshot(output, physical, overrideSize)
    }

    private fun readRotationSettings(): RotationSettings = RotationSettings(
        accelerometerRotation = shell("settings get system accelerometer_rotation"),
        userRotation = shell("settings get system user_rotation"),
    )

    private fun markerBounds(marker: String): JSONObject? = composeRule.onAllNodesWithText(marker, substring = true)
        .fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.let {
            JSONObject().put("marker", marker).put("left", it.left).put("top", it.top)
                .put("right", it.right).put("bottom", it.bottom)
        }

    private fun markerIntersectsRoot(marker: JSONObject, root: Bounds): Boolean {
        val left = marker.getDouble("left").toFloat()
        val top = marker.getDouble("top").toFloat()
        val right = marker.getDouble("right").toFloat()
        val bottom = marker.getDouble("bottom").toFloat()
        return right > root.left && left < root.right && bottom > root.top && top < root.bottom
    }

    private fun waitForAnchor(): Diag {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val anchor = runDiagnostics().lastOrNull {
                it.event == "session_guard_anchor" && it.fields["bid"] == bookIdA
            }
            if (anchor != null) return anchor
            SystemClock.sleep(100)
        }
        error("真实恢复未产生 session_guard_anchor")
    }

    /** Filter shared logcat without clearing device logs: this run is bounded by start seq/time and unique book id. */
    private fun runDiagnostics(): List<Diag> {
        val raw = readDiag()
        // 作用域裁剪也走同一份纯规则：别的 run / 别的书的事件不得混入本判定。
        val scopedSeqs = ReaderClampRemeasureVerdict.scopeEvents(
            raw.map { it.toVerdictEvt() }, runStartSeq, runStartNanos, bookIdA,
        ).map { it.seq }.toSet()
        return raw.filter { it.seq in scopedSeqs }
    }

    private fun readDiag(): List<Diag> {
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-v", "raw", "--pid=${android.os.Process.myPid()}", "-s", "CRA_CLAMP_DIAG:I")
        )
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "读取生产诊断 logcat 失败" }
        return output.lineSequence().mapNotNull { line ->
            val start = line.indexOf("seq=")
            if (start < 0) return@mapNotNull null
            var seq = -1
            var ts = -1L
            var event = ""
            val fields = linkedMapOf<String, String>()
            line.substring(start).trim().split(Regex("\\s+")).forEach { token ->
                val eq = token.indexOf('=')
                if (eq <= 0) return@forEach
                val key = token.substring(0, eq)
                val value = token.substring(eq + 1).trimEnd(',')
                when (key) {
                    "seq" -> seq = value.toIntOrNull() ?: -1
                    "ts" -> ts = value.toLongOrNull() ?: -1L
                    "event" -> event = value
                    else -> fields[key] = value
                }
            }
            if (seq < 0 || ts <= 0 || event.isBlank()) null else Diag(seq, ts, event, fields)
        }.sortedBy { it.seq }.toList()
    }

    private fun writeEvidence(payload: JSONObject) {
        val dir = File(context.getExternalFilesDir(null), EVIDENCE_DIR)
        check(dir.exists() || dir.mkdirs()) { "创建独立证据目录失败: ${dir.absolutePath}" }
        val file = File(dir, "${runId}__real_viewport_remeasure.json")
        file.writeText(JSONObject().put("task", TASK_ID).put("runId", runId)
            .put("label", "$TASK_ID:$probeMode:realWindowRemeasureAfterRestoreWithoutReaderGesture")
            .put("pid", android.os.Process.myPid()).put("packageName", context.packageName)
            .put("diagnosticSamplingPid", android.os.Process.myPid())
            .put("diagnosticSamplingScope", "CRA_CLAMP_DIAG logcat records from this instrumentation process only; additionally bounded by run start time, sequence, and this run's book id when present")
            .put("timestamp", Instant.now().toString()).put("payload", payload).toString(2))
        evidenceFile = file
        println("J1GUARD_EVIDENCE=${file.name}")
        println("J1GUARD_PRODUCER=$runId")
    }

    private fun writeTeardownEvidence(cleanup: JSONObject) {
        val dir = File(context.getExternalFilesDir(null), EVIDENCE_DIR)
        check(dir.exists() || dir.mkdirs()) { "创建teardown证据目录失败: ${dir.absolutePath}" }
        val file = evidenceFile ?: File(dir, "${runId}__real_viewport_remeasure.json")
        val root = if (file.exists()) JSONObject(file.readText()) else JSONObject()
            .put("task", TASK_ID)
            .put("runId", runId)
            .put("label", "$TASK_ID:$probeMode:realWindowRemeasureAfterRestoreWithoutReaderGesture")
            .put("pid", android.os.Process.myPid())
            .put("diagnosticSamplingPid", android.os.Process.myPid())
            .put("diagnosticSamplingScope", "CRA_CLAMP_DIAG logcat records from this instrumentation process only; additionally bounded by run start time, sequence, and this run's book id when present")
            .put("packageName", context.packageName)
            .put("timestamp", Instant.now().toString())
        root.put("teardown", cleanup)
        root.put("overallResult", when {
            !cleanup.optBoolean("restored") -> "FAILED_RESTORATION"
            primaryTestFailure != null -> "RESTORATION_OK_TEST_FAILED"
            else -> "RESTORATION_OK_NO_TEST_FAILURE_RECORDED"
        })
        file.writeText(root.toString(2))
        evidenceFile = file
        println("J1GUARD_EVIDENCE=${file.name}")
        println("J1GUARD_PRODUCER=$runId")
    }

    private fun waitHome() {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithContentDescription("搜索", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openBookA() {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(J1RealReaderFixtures.BOOK_TITLE_A, substring = true)).performClick()
        composeRule.waitUntil(20_000) { markerBounds(contentA.marker2) != null }
        composeRule.onNode(hasText(contentA.marker2, substring = true)).assertIsDisplayed()
    }

    private fun waitControlsHidden() {
        composeRule.waitUntil(25_000) {
            composeRule.onAllNodesWithContentDescription("返回").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun pressBack() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun exitReaderToHome() {
        var attempts = 0
        while (attempts < 4 && composeRule.onAllNodesWithContentDescription("搜索", substring = true)
                .fetchSemanticsNodes().isEmpty()) {
            pressBack()
            SystemClock.sleep(250)
            attempts++
        }
        waitHome()
    }
}
