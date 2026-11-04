package com.creationreadingassistant.acceptance

import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.TxtTocProfile
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.ui.screen.reader.buildChapterAlignedPlainUnits
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * CRA-TXT-PERSIST-ACCEPT-HARDENING-v2 —— 短末项 clamp 补充验收测试。
 *
 * ## 设计目的与返修加固
 *
 * 验证书籍末尾短项（LazyList 无法将末项推至屏幕顶部，触发 plainRestoreSettled 的 clamp 分支）
 * 合法落位后能够继续正常保存，不被清零或冻结。
 *
 * 核心返修加固：
 * 1. 拆分为独立生命周期时点，绝不复用同一 snapshot 冒充多个时点：
 *    - S (j1_clamp_10_recordSaved): 退出保存末单元 S 完整快照；
 *    - R (j1_clamp_20_reentrySettled): 重新进入触发 clamp 落位 R 完整快照与 UI dump；
 *    - S2 (j1_clamp_30_newPositionSaved): 阅读器内反向移动至新位置并退出保存 S2 完整快照；
 *    - Cold (j1_clamp_40_coldReentryVerify): 真实冷进程读回并验证单元恢复合同；
 * 2. 严格断言 S2 != R 且 S2 != S，updated_at 刷新，新位置 marker 匹配；
 * 3. 独立中性夹具（fixture_short_clamp.txt）与独立图书 ID（j1-clamp-b1），不污染普通 J1 基准。
 */
@RunWith(AndroidJUnit4::class)
class TxtShortClampSupplementTest {

    companion object {
        const val CLAMP_BOOK_ID = "j1-clamp-b1"
        const val CLAMP_BOOK_TITLE = "测试 TXT"
        const val RESULT_DIR = "j1-accept"
        const val TAG = "TxtShortClampTest"
    }

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun resultsDir(): File =
        File(context.getExternalFilesDir(null), "$RESULT_DIR/${task35RunId()}").also { it.mkdirs() }

    private fun bodyFile(): File = File(context.filesDir, "books/$CLAMP_BOOK_ID/content.txt")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun identityBoundRejectsUnsupportedOrInconsistentProcessStartEvidence() {
        val startMillis = 1_000L
        val bootMillis = 500L
        val hz = 100L
        val ticks = 50L
        val stat = "123 (cra-host) " + (listOf("S") + List(18) { "0" } + ticks).joinToString(" ")
        fun record(startText: String, start: Long = startMillis, processStat: String = stat) = Task35IdentityRecord(
            hostPackage = "com.creationreadingassistant.qahost", hostPid = "123",
            processStartTime = startText, processStartMillis = start,
            foregroundComponent = "com.creationreadingassistant.qahost/.Reader",
            readerForeground = false, capturedAtMillis = 2_000L, myPidOnly = false,
            raw = processStat, runId = "run-test", phase = "j1_clamp_10_recordSaved",
            clkTck = hz, processStartStat = processStat, processStartBootMillis = bootMillis,
        )
        val valid = record(Instant.ofEpochMilli(startMillis).toString())
        assertTrue(Task35EvidenceGate.identityBound(valid, false).accepted)
        assertFalse(Task35EvidenceGate.identityBound(record("Usage: ps [OPTIONS]"), false).accepted)
        assertFalse(Task35EvidenceGate.identityBound(record("not-an-iso-time"), false).accepted)
        assertFalse(Task35EvidenceGate.identityBound(
            record(Instant.ofEpochMilli(startMillis + 1).toString()), false,
        ).accepted)
        assertFalse(Task35EvidenceGate.identityBound(
            record(Instant.ofEpochMilli(startMillis).toString(), startMillis + 1), false,
        ).accepted)
        val otherPidStat = stat.replaceFirst("123 ", "456 ")
        assertFalse(Task35EvidenceGate.identityBound(
            record(Instant.ofEpochMilli(startMillis).toString(), processStat = otherPidStat), false,
        ).accepted)
    }

    private fun timestampAtLeast(actual: String, expected: String): Boolean = runCatching {
        actual.isNotBlank() && expected.isNotBlank() && !Instant.parse(actual).isBefore(Instant.parse(expected))
    }.getOrDefault(false)

    private fun openDb(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
            .allowMainThreadQueries().build()

    private fun rawOne(db: AppDatabase, sql: String): String? =
        db.openHelper.writableDatabase.query(sql).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    // CRA-CLAMP-SAF-EVIDENCE-PREP-v1：不再只 SELECT 四列 —— 按**真实 schema 全列**读回，
    // 每个时点各读一次自己的完整行（见 Task35ProgressRows），四列子集不能冒充整行。
    private fun readProgressRow(db: AppDatabase): Task35ProgressRow? =
        Task35ProgressRows.readFull(db, CLAMP_BOOK_ID)

    private fun awaitProgressRow(
        db: AppDatabase,
        timeoutMs: Long = 6000L,
        ready: (Task35ProgressRow?) -> Boolean,
    ): Task35ProgressRow? = Task35ProgressRows.awaitFull(db, CLAMP_BOOK_ID, timeoutMs, ready)

    /** 本时点的设备侧身份握手；[readerMarker] 必须出现在该时点抓取的前台 dump 里。 */
    private fun task35Identity(
        readerMarker: String?,
        foregroundDump: String?,
        phase: String? = null,
    ): Task35IdentityRecord = if (phase != null) {
        Task35LiveHandshake.consumeIdentity(phase, readerMarker, foregroundDump)
    } else {
        Task35Identity.capture(
            hostPackage = context.packageName,
            readerMarker = readerMarker,
            foregroundDump = foregroundDump,
        )
    }

    /** 上一时点证据文件的时刻（证明本时点是**独立的一次读回**，不是复用同一 snapshot）。 */
    private fun evidenceMillisOf(step: String): Long? =
        File(resultsDir(), "$step.json").takeIf { it.isFile }
            ?.let { JSONObject(it.readText()).optLong("timestampMillis", -1L) }
            ?.takeIf { it > 0 }

    private fun task35RunId(): String {
        val b = File(context.getExternalFilesDir(null), "$RESULT_DIR/binding.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }
        return b?.optString("runId")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: error("binding.json 缺 runId")
    }

    private fun binding(): JSONObject {
        val f = File(context.getExternalFilesDir(null), "$RESULT_DIR/binding.json")
        assertTrue("缺绑定文件 binding.json", f.isFile)
        return JSONObject(f.readText())
    }

    private fun writeResult(
        step: String,
        payload: JSONObject,
        binding: JSONObject?,
        identity: Task35IdentityRecord? = null,
        verdicts: Map<String, Task35Verdict> = emptyMap(),
    ) {
        val obj = JSONObject()
            .put("step", step)
            // producerPid 保留原字段（进程内自述）；**不作为**身份证据 ——
            // 身份证据一律看 identity 块（设备侧实读的宿主进程/前台/启动时刻）。
            .put("pid", Process.myPid())
            .put("producerPid", Process.myPid())
            .put("pidIsIdentityEvidence", false)
            .put("packageName", context.packageName)
            .put("timestampMillis", System.currentTimeMillis())
            .put("at", Instant.now().toString())
        if (binding != null) {
            obj.put("taskId", binding.optString("taskId"))
            obj.put("runId", binding.optString("runId"))
            obj.put("configSha256", binding.optString("configSha256"))
            obj.put("fixtureSha256", binding.optString("clampFixtureSha256", binding.optString("fixtureSha256")))
        }
        if (identity != null) {
            obj.put("identity", identity.toJson())
            check(identity.runId == binding?.optString("runId") && identity.phase == step) { "identity run/phase mismatch" }
        }
        if (verdicts.isNotEmpty()) {
            obj.put(
                "gate",
                JSONObject().apply {
                    verdicts.forEach { (k, v) ->
                        put(k, JSONObject().put("accepted", v.accepted).put("reason", v.reason))
                    }
                },
            )
        }
        obj.put("payload", payload)
        File(resultsDir(), "$step.json").writeText(obj.toString(2))
        android.util.Log.i(TAG, "$step $payload")
        verdicts.forEach { (name, verdict) ->
            assertTrue("$step gate $name: ${verdict.reason}", verdict.accepted)
        }
    }

    private fun gate(vararg pairs: Pair<String, Task35Verdict>): Map<String, Task35Verdict> =
        pairs.toMap()

    /** 本时点完整行 + 准入判定的公共装载。 */
    private fun rowPayload(row: Task35ProgressRow?, extra: JSONObject): JSONObject {
        val complete = Task35EvidenceGate.rowComplete(row, CLAMP_BOOK_ID)
        return extra
            .put("rowComplete", complete.accepted)
            .put("rowCompleteReason", complete.reason)
            .put("fullRow", row?.toJson() ?: JSONObject.NULL)
            .put("readColumns", JSONArray(row?.readColumns ?: emptyList<String>()))
    }

    private fun readPushed(name: String): String? {
        File(resultsDir(), name).takeIf { it.isFile }?.let { return it.readText() }
        val dataRoot = File(context.getExternalFilesDir(null), RESULT_DIR)
        File(dataRoot, name).takeIf { it.isFile }?.let { return it.readText() }
        val bindingFile = File(context.getExternalFilesDir(null), "$RESULT_DIR/binding.json")
        if (!bindingFile.isFile) return null
        val b = JSONObject(bindingFile.readText())
        val task = b.optString("taskId")
        val run = b.optString("runId")
        if (task.isBlank() || run.isBlank()) return null
        val canonicalRoot = File(dataRoot, "$task/$run")
        canonicalRoot.takeIf { it.isDirectory }?.walkTopDown()
            ?.firstOrNull { it.isFile && it.name.endsWith("__$name") }
            ?.let { return it.readText() }
        val legacyRoot = File(resultsDir(), "$task/$run")
        return legacyRoot.takeIf { it.isDirectory }?.walkTopDown()
            ?.firstOrNull { it.isFile && it.name.endsWith("__$name") }
            ?.readText()
    }

    private fun clampBody(): String {
        val text = readPushed("fixture_short_clamp.txt")
        assertTrue("缺短末项夹具 fixture_short_clamp.txt", text != null)
        return text!!
    }

    private fun productionUnits(body: String): List<ReadingUnitRange> {
        val chapters = PlainTextDocument(body, TxtTocProfile.fromRuleId("builtin")).chapters
        val units: List<ReadingUnit> = buildChapterAlignedPlainUnits(body, chapters)
        return units.map { ReadingUnitRange(it.unitIndex, it.charStart, it.charCount) }
    }

    private fun cleanRows(db: AppDatabase) {
        val wdb = db.openHelper.writableDatabase
        listOf(
            "DELETE FROM reading_progress WHERE book_id='$CLAMP_BOOK_ID'",
            "DELETE FROM highlights WHERE book_id='$CLAMP_BOOK_ID'",
            "DELETE FROM notes WHERE book_id='$CLAMP_BOOK_ID'",
            "DELETE FROM books WHERE id='$CLAMP_BOOK_ID'",
            "DELETE FROM book_files WHERE book_id='$CLAMP_BOOK_ID'",
        ).forEach { runCatching { wdb.execSQL(it) } }
    }

    // ── 1. 短末项夹具植入 ──────────────────────────────────────────────────────

    @Test
    fun j1_clamp_00_seedFixture() = runBlocking {
        val binding = binding()
        val db = openDb()
        try {
            cleanRows(db)
            val body = clampBody()
            bodyFile().apply { parentFile!!.mkdirs(); writeText(body) }
            db.bookDao().upsert(
                BookEntity(
                    id = CLAMP_BOOK_ID, title = CLAMP_BOOK_TITLE, author = "中性作者", format = "txt",
                    size = body.toByteArray().size, content_status = "available",
                    imported_at = Instant.now().toString(), revision = 1,
                    updated_at = Instant.now().toString(),
                    local_content_path = bodyFile().absolutePath,
                ),
            )
            val units = productionUnits(body)
            assertEquals("短末项夹具必须恰好为 2 个阅读单元", 2, units.size)
            assertTrue("末单元必须显著短于首单元", units[1].charCount < units[0].charCount / 2)
            assertTrue(
                "末单元必须包含 CRACLAMP 标记",
                J1RecoveryContract.markerOffsets(body, J1RecoveryContract.CLAMP_MARKER_PREFIX).isNotEmpty(),
            )
            val seededAt = Instant.now().toString()
            val seededLocation = LocatorBuilder.progressJson(
                legacyOffset = body.length, chapterIndex = 0, charOffset = body.length,
                excerpt = body.takeLast(96),
            )
            db.readingProgressDao().upsert(
                ReadingProgressEntity(
                    book_id = CLAMP_BOOK_ID, progress_percent = 100f,
                    current_location_json = seededLocation, updated_at = seededAt,
                ),
            )
            val unitsArr = JSONArray()
            units.forEach { unitsArr.put(JSONArray().put(it.unitIndex).put(it.charStart).put(it.charCount)) }
            writeResult(
                "j1_clamp_00_seedFixture",
                JSONObject()
                    .put("fixtureOnly", true)
                    .put("seeded", true)
                    .put("seededOffset", body.length)
                    .put("seededPercent", 100f)
                    .put("seededUpdatedAt", seededAt)
                    .put("seededLocationJson", seededLocation)
                    .put("seededBookId", CLAMP_BOOK_ID)
                    .put("bodyLength", body.length)
                    .put("unitCount", units.size)
                    .put("units", unitsArr),
                binding,
            )
        } finally {
            db.close()
        }
    }

    // ── 2. 时点 1 (S)：观察fixtureOnly末单元seed行，不冒称UI退出保存 ─────────────

    @Test
    fun j1_clamp_10_recordSaved() {
        val binding = binding()
        val db = openDb()
        try {
            val body = bodyFile().readText()
            val units = productionUnits(body)
            // 时点 S：本时点**独立**读回完整行（不是四列子集，也不复用别的时点）
            val sRow = readProgressRow(db)
            val sComplete = Task35EvidenceGate.rowComplete(sRow, CLAMP_BOOK_ID)
            assertTrue("时点 S 必须是完整 reading_progress 行：${sComplete.reason}", sComplete.accepted)
            val snap = sRow?.toSnapshot()
            val saved = snap?.legacyOffset
            val (contract, reason) = J1RecoveryContract.buildClamp(body, units, saved, snap, expectedBookId = CLAMP_BOOK_ID)
            assertNotNull("短末项必须成功建立末单元保存合同（原因：$reason）", contract)
            val clamp = contract!!
            // 时点 S 来自夹具 seed（无真实阅读器前台），因此不声称 reader 前台；
            // 真实 UI 退出保存轮由 driver 单独执行并单独取身份。
            val identity = task35Identity(readerMarker = null, foregroundDump = null)
            writeResult(
                "j1_clamp_10_recordSaved",
                rowPayload(
                    sRow,
                    JSONObject()
                        .put("result", "OBSERVED_FIXTURE_SEED")
                        .put("fixtureOnly", true)
                        .put("seeded", true)
                        .put("bodyLength", clamp.bodyLength)
                        .put("savedOffset", clamp.savedOffset)
                        .put("savedPercent", snap?.progressPercent ?: 0f)
                        .put("savedUpdatedAt", snap?.updatedAt ?: "")
                        .put("savedLocationJson", snap?.currentLocationJson ?: "")
                        .put("savedBookId", snap?.bookId ?: "")
                        .put("savedUnitIndex", clamp.savedUnitIndex)
                        .put("isLastUnit", clamp.isLastUnit)
                        .put("savedParagraphMarker", clamp.savedParagraphMarker),
                ),
                binding,
                identity = identity,
                verdicts = gate(
                    "rowComplete" to sComplete,
                    // 时点 S 由夹具 seed 产生，不声称真实阅读器前台；驱动侧另行补真实 UI 退出保存轮
                    "identityBound" to Task35EvidenceGate.identityBound(identity, requireReaderForeground = false),
                ),
            )
        } finally {
            db.close()
        }
    }

    private fun readClampContract(): J1ClampContract {
        val f = File(resultsDir(), "j1_clamp_10_recordSaved.json")
        assertTrue("缺短末项保存合同证据", f.isFile)
        val p = JSONObject(f.readText()).getJSONObject("payload")
        val body = bodyFile().readText()
        val units = productionUnits(body)
        val unitIdx = p.getInt("savedUnitIndex")
        return J1ClampContract(
            bodyLength = p.getInt("bodyLength"),
            savedOffset = p.getInt("savedOffset"),
            savedUnitIndex = unitIdx,
            savedUnitStart = units[unitIdx].charStart,
            savedUnitEnd = units[unitIdx].charEnd,
            isLastUnit = p.getBoolean("isLastUnit"),
            savedParagraphMarker = p.getString("savedParagraphMarker"),
            savedUpdatedAt = p.optString("savedUpdatedAt", ""),
            savedPercent = p.optDouble("savedPercent", 0.0).toFloat(),
            savedLocationJson = p.optString("savedLocationJson", ""),
            bookId = p.optString("savedBookId", CLAMP_BOOK_ID),
        )
    }

    // ── 3. 时点 2 (R)：短末项重进触发 clamp 落位，记录独立 R 快照 ──────────────

    @Test
    fun j1_clamp_20_reentrySettled() {
        val binding = binding()
        val db = openDb()
        try {
            val body = bodyFile().readText()
            val units = productionUnits(body)
            val contract = readClampContract()
            // 时点 R：本时点**独立**读回完整行
            val rRow = readProgressRow(db)
            val rComplete = Task35EvidenceGate.rowComplete(rRow, CLAMP_BOOK_ID)
            // 时点独立性由「独立读回时刻」证明（R 不一定产生新写入，故不硬要求 updated_at 推进）
            val rIndependent = Task35EvidenceGate.evidenceMomentAdvanced(
                evidenceMillisOf("j1_clamp_10_recordSaved"), System.currentTimeMillis(),
            )
            val rSnap = rRow?.toSnapshot()
            val dump = readPushed("ui_clamp_reentry.xml")

            val verdict = J1RecoveryContract.clampReentryAccepted(
                clampContract = contract,
                units = units,
                reenteredSnapshot = rSnap,
                reenteredDump = dump,
            )

            val identity = task35Identity(
                readerMarker = contract.savedParagraphMarker,
                foregroundDump = dump,
                phase = "j1_clamp_20_reentrySettled",
            )
            writeResult(
                "j1_clamp_20_reentrySettled",
                rowPayload(
                    rRow,
                    JSONObject()
                        .put("result", if (verdict.accepted) "ACCEPTED" else "REJECTED")
                        .put("accepted", verdict.accepted)
                        .put("reason", verdict.reason)
                        .put("reenteredOffset", rSnap?.legacyOffset ?: -1)
                        .put("reenteredPercent", rSnap?.progressPercent ?: 0f)
                        .put("reenteredUpdatedAt", rSnap?.updatedAt ?: "")
                        .put("reenteredBookId", rSnap?.bookId ?: "")
                        .put("reenteredLocationJson", rSnap?.currentLocationJson ?: "")
                        .put("savedOffset", contract.savedOffset)
                        .put("savedUnitIndex", contract.savedUnitIndex)
                        .put("uiDumpPresent", dump != null),
                ),
                binding,
                identity = identity,
                verdicts = gate(
                    "rowComplete" to rComplete,
                    "timePointsIndependent" to rIndependent,
                    "identityBound" to Task35EvidenceGate.identityBound(identity, requireReaderForeground = true),
                ),
            )
            assertTrue("时点 R 必须是完整行：${rComplete.reason}", rComplete.accepted)
            assertTrue("时点 R 必须是独立读回（不得复用 S 的 snapshot）：${rIndependent.reason}", rIndependent.accepted)
            assertTrue("短末项 clamp 重进落位：${verdict.reason}", verdict.accepted)
        } finally {
            db.close()
        }
    }

    // ── 4. 时点 3 (S2)：恢复后反向移动并退出，记录独立 S2 快照 ─────────────────

    @Test
    fun j1_clamp_30_newPositionSaved() {
        val binding = binding()
        val db = openDb()
        try {
            val body = bodyFile().readText()
            val units = productionUnits(body)
            val contract = readClampContract()

            // 读取时点 2 (R) 快照
            val rFile = File(resultsDir(), "j1_clamp_20_reentrySettled.json")
            assertTrue("缺短末项重进落位证据", rFile.isFile)
            val rp = JSONObject(rFile.readText()).getJSONObject("payload")
            val rSnap = J1ProgressSnapshot(
                rp.getInt("reenteredOffset"),
                rp.optDouble("reenteredPercent", 0.0).toFloat(),
                rp.optString("reenteredUpdatedAt", ""),
                rp.optString("reenteredLocationJson", ""),
                rp.optString("reenteredBookId", CLAMP_BOOK_ID),
            )

            // 读取退出后的新持久化行 S2（本时点**独立**读回完整行）
            val dumpAfterMove = readPushed("ui_clamp_new_scroll.xml")
            val rOffset = rSnap.legacyOffset
            val s2Row = awaitProgressRow(db) { row -> row != null && row.bookId == CLAMP_BOOK_ID &&
                !row.currentLocationJson.isNullOrBlank() && row.legacyOffset >= 0 && row.legacyOffset != rOffset &&
                row.legacyOffset != contract.savedOffset && row.updatedAt.isNotBlank() &&
                row.updatedAt != contract.savedUpdatedAt && row.updatedAt != rSnap.updatedAt &&
                ((J1RecoveryContract.lastMarkerAtOrBefore(body, row.legacyOffset, J1RecoveryContract.CLAMP_MARKER_PREFIX)
                    ?: if (row.legacyOffset == 0) J1RecoveryContract.firstMarkerAtOrAfter(body, 0, J1RecoveryContract.CLAMP_MARKER_PREFIX) else null)
                    ?.let { dumpAfterMove?.contains(it) } == true) }
            val s2Complete = Task35EvidenceGate.rowComplete(s2Row, CLAMP_BOOK_ID)
            val s2Advance = Task35EvidenceGate.timePointAdvanced(rSnap.updatedAt, s2Row)
            val s2Snap = s2Row?.toSnapshot()

            // 真实位移：恢复落点 R 与位移后 S2 的偏移与可见 marker 都必须变化
            val rMarker = J1RecoveryContract.lastMarkerAtOrBefore(body, rOffset, J1RecoveryContract.CLAMP_MARKER_PREFIX)
                ?: J1RecoveryContract.firstMarkerAtOrAfter(body, rOffset, J1RecoveryContract.CLAMP_MARKER_PREFIX)
            val s2Marker = s2Row?.let {
                J1RecoveryContract.lastMarkerAtOrBefore(body, it.legacyOffset, J1RecoveryContract.CLAMP_MARKER_PREFIX)
                    ?: J1RecoveryContract.firstMarkerAtOrAfter(body, it.legacyOffset, J1RecoveryContract.CLAMP_MARKER_PREFIX)
            }
            val displacement = Task35EvidenceGate.realDisplacement(
                beforeOffset = rOffset, beforeMarker = rMarker,
                afterOffset = s2Snap?.legacyOffset ?: -1, afterMarker = s2Marker,
            )

            val verdict = J1RecoveryContract.clampNewPositionAccepted(
                clampContract = contract,
                units = units,
                reenteredSnapshot = rSnap,
                newSnapshot = s2Snap,
                body = body,
                uiDumpAfterMove = dumpAfterMove,
            )

            val identity = task35Identity(
                readerMarker = s2Marker,
                foregroundDump = dumpAfterMove,
                phase = "j1_clamp_30_newPositionSaved",
            )
            writeResult(
                "j1_clamp_30_newPositionSaved",
                rowPayload(
                    s2Row,
                    JSONObject()
                        .put("result", if (verdict.accepted) "ACCEPTED" else "REJECTED")
                        .put("accepted", verdict.accepted)
                        .put("reason", verdict.reason)
                        .put("reenteredOffset", rSnap.legacyOffset)
                        .put("savedOffset", contract.savedOffset)
                        .put("newOffset", s2Snap?.legacyOffset ?: -1)
                        .put("newPercent", s2Snap?.progressPercent ?: 0f)
                        .put("newLocationJson", s2Snap?.currentLocationJson ?: "")
                        .put("newUpdatedAt", s2Snap?.updatedAt ?: "")
                        .put("savedUpdatedAt", contract.savedUpdatedAt ?: "")
                        .put("newBookId", s2Snap?.bookId ?: "")
                        .put("reenteredMarker", rMarker ?: JSONObject.NULL)
                        .put("newMarker", s2Marker ?: JSONObject.NULL)
                        .put("uiDumpPresent", dumpAfterMove != null),
                ),
                binding,
                identity = identity,
                verdicts = gate(
                    "rowComplete" to s2Complete,
                    "timePointAdvanced" to s2Advance,
                    "realDisplacement" to displacement,
                    "identityBound" to Task35EvidenceGate.identityBound(identity, requireReaderForeground = true),
                ),
            )
            assertTrue("时点 S2 必须是完整行：${s2Complete.reason}", s2Complete.accepted)
            assertTrue("时点 S2 必须晚于时点 R：${s2Advance.reason}", s2Advance.accepted)
            assertTrue("S2 必须是真实位移（S2≠R 且 marker 变化）：${displacement.reason}", displacement.accepted)
            assertTrue("短末项反向移动新位置保存：${verdict.reason}", verdict.accepted)
        } finally {
            db.close()
        }
    }

    @Test
    fun j1_clamp_35_exitPositionSaved() {
        val binding = binding(); val db = openDb()
        try {
            val f = File(resultsDir(), "j1_clamp_30_newPositionSaved.json")
            assertTrue("缺短末项S2完整行", f.isFile)
            val p = JSONObject(f.readText()).getJSONObject("payload")
            // 时点 退出：本时点**独立**读回完整行（不复用 S2 快照）
            val exit = awaitProgressRow(db) { row -> row != null && row.bookId == p.optString("newBookId") &&
                row.legacyOffset == p.optInt("newOffset", -1) && row.currentLocationJson == p.optString("newLocationJson") &&
                row.progressPercent == p.optDouble("newPercent", -1.0).toFloat() &&
                timestampAtLeast(row.updatedAt, p.optString("newUpdatedAt")) }
            val same = exit != null && exit.bookId == p.optString("newBookId") &&
                exit.legacyOffset == p.optInt("newOffset", -1) &&
                exit.currentLocationJson == p.optString("newLocationJson") &&
                exit.progressPercent == p.optDouble("newPercent", -1.0).toFloat() &&
                timestampAtLeast(exit.updatedAt, p.optString("newUpdatedAt"))
            val exitComplete = Task35EvidenceGate.rowComplete(exit, CLAMP_BOOK_ID)
            val exitNotEarlier = Task35EvidenceGate.timePointNotEarlier(p.optString("newUpdatedAt"), exit)
            val exitIndependent = Task35EvidenceGate.evidenceMomentAdvanced(
                evidenceMillisOf("j1_clamp_30_newPositionSaved"), System.currentTimeMillis(),
            )
            writeResult(
                "j1_clamp_35_exitPositionSaved",
                rowPayload(
                    exit,
                    JSONObject().put("accepted", same)
                        .put("reason", if (same) "退出行与S2完整行一致" else "短末项退出行与S2不一致")
                        .put("exitOffset", exit?.legacyOffset ?: -1).put("exitPercent", exit?.progressPercent ?: -1f)
                        .put("exitUpdatedAt", exit?.updatedAt ?: "").put("exitLocationJson", exit?.currentLocationJson ?: "")
                        .put("exitBookId", exit?.bookId ?: ""),
                ),
                binding,
                identity = task35Identity(null, null),
                verdicts = gate(
                    "rowComplete" to exitComplete,
                    "timePointNotEarlier" to exitNotEarlier,
                    "timePointsIndependent" to exitIndependent,
                ),
            )
            assertTrue("退出时点必须是完整行：${exitComplete.reason}", exitComplete.accepted)
            assertTrue("退出时点必须不早于 S2：${exitNotEarlier.reason}", exitNotEarlier.accepted)
            assertTrue("短末项S2后退出行必须独立一致", same)
        } finally { db.close() }
    }

    // ── 5. 时点 4 (Cold)：冷启动读回新位置并验证单元恢复合同 ──────────────────

    @Test
    fun j1_clamp_40_coldReentryVerify() {
        val binding = binding()
        val db = openDb()
        try {
            val body = bodyFile().readText()
            val units = productionUnits(body)

            // 读取基准 S2 快照
            val s2File = File(resultsDir(), "j1_clamp_30_newPositionSaved.json")
            assertTrue("缺短末项新位置持久化证据", s2File.isFile)
            val s2p = JSONObject(s2File.readText()).getJSONObject("payload")
            val s2Snap = J1ProgressSnapshot(
                s2p.getInt("newOffset"),
                s2p.optDouble("newPercent", 0.0).toFloat(),
                s2p.optString("newUpdatedAt", ""),
                s2p.optString("newLocationJson", ""),
                s2p.optString("newBookId", CLAMP_BOOK_ID),
            )

            val coldDump = readPushed("ui_clamp_cold.xml")
            val expectedUnit = J1RecoveryContract.unitIndexForOffset(units, s2Snap.legacyOffset)
            val expectedMarker = J1RecoveryContract.lastMarkerAtOrBefore(
                body, s2Snap.legacyOffset, J1RecoveryContract.CLAMP_MARKER_PREFIX)
            // 时点 冷读：本时点**独立**读回完整行
            val coldRow = awaitProgressRow(db) { row -> row != null && row.bookId == CLAMP_BOOK_ID &&
                !row.currentLocationJson.isNullOrBlank() && row.updatedAt.isNotBlank() && row.legacyOffset >= 0 &&
                J1RecoveryContract.unitIndexForOffset(units, row.legacyOffset) == expectedUnit &&
                (row.legacyOffset == s2Snap.legacyOffset || row.legacyOffset == units[expectedUnit].charStart) &&
                expectedMarker != null && coldDump?.contains(expectedMarker) == true }
            val coldComplete = Task35EvidenceGate.rowComplete(coldRow, CLAMP_BOOK_ID)
            val coldIndependent = Task35EvidenceGate.evidenceMomentAdvanced(
                evidenceMillisOf("j1_clamp_35_exitPositionSaved"), System.currentTimeMillis(),
            )
            val coldSnap = coldRow?.toSnapshot()

            val verdict = J1RecoveryContract.clampColdReentryAccepted(
                persistedSnapshot = s2Snap,
                coldSnapshot = coldSnap,
                units = units,
                body = body,
                coldDump = coldDump,
            )

            // 冷进程身份：进程启动时刻必须晚于上一时点（退出）的证据时刻，
            // 且驱动必须交出上一进程的结束时刻；只有 myPid 不算冷进程证据。
            val exitEvidenceMillis = File(resultsDir(), "j1_clamp_35_exitPositionSaved.json")
                .takeIf { it.isFile }
                ?.let { JSONObject(it.readText()).optLong("timestampMillis", -1L) }
                ?.takeIf { it > 0 }
            val previousProcessEndMillis = Task35Identity.readProcessEndMillis(
                resultsDir(), task35RunId(), "j1_clamp_40_coldReentryVerify",
            )
            val identity = task35Identity(
                readerMarker = expectedMarker,
                foregroundDump = coldDump,
                phase = "j1_clamp_40_coldReentryVerify",
            )
            val coldProcess = Task35EvidenceGate.coldProcess(
                identity = identity,
                previousEvidenceMillis = exitEvidenceMillis,
                previousProcessEndMillis = previousProcessEndMillis,
            )

            writeResult(
                "j1_clamp_40_coldReentryVerify",
                rowPayload(
                    coldRow,
                    JSONObject()
                        .put("result", if (verdict.accepted) "ACCEPTED" else "REJECTED")
                        .put("accepted", verdict.accepted)
                        .put("reason", verdict.reason)
                        .put("persistedOffset", s2Snap.legacyOffset)
                        .put("coldOffset", coldSnap?.legacyOffset ?: -1)
                        .put("coldPercent", coldSnap?.progressPercent ?: 0f)
                        .put("coldUpdatedAt", coldSnap?.updatedAt ?: "")
                        .put("coldLocationJson", coldSnap?.currentLocationJson ?: "")
                        .put("coldBookId", coldSnap?.bookId ?: "")
                        .put("previousEvidenceMillis", exitEvidenceMillis ?: JSONObject.NULL)
                        .put("previousProcessEndMillis", previousProcessEndMillis ?: JSONObject.NULL)
                        .put("uiDumpPresent", coldDump != null),
                ),
                binding,
                identity = identity,
                verdicts = gate(
                    "rowComplete" to coldComplete,
                    "coldProcess" to coldProcess,
                    "timePointsIndependent" to coldIndependent,
                    "identityBound" to Task35EvidenceGate.identityBound(identity, requireReaderForeground = true),
                ),
            )
            assertTrue("冷读时点必须是完整行：${coldComplete.reason}", coldComplete.accepted)
            assertTrue("冷读必须是独立读回：${coldIndependent.reason}", coldIndependent.accepted)
            assertTrue("冷读必须是真实冷进程（启动时刻晚于上一时点且有进程结束时刻）：${coldProcess.reason}", coldProcess.accepted)
            assertTrue("短末项冷读回验证：${verdict.reason}", verdict.accepted)
        } finally {
            db.close()
        }
    }

    // ── 6. 负向验证：非末项冒充 clamp、清零或冻结被拒绝 ────────────────────────

    @Test
    fun j1_clamp_70_negative_clampRejected() {
        val units = listOf(ReadingUnitRange(0, 0, 3000), ReadingUnitRange(1, 3000, 500))
        val body = "a".repeat(3000) + J1RecoveryContract.clampMarker(1) + "b".repeat(480)
        val badClamp = linkedMapOf(
            "保存落在非末单元（unit 0）" to J1RecoveryContract.buildClamp(body, units, 1500).second,
            "保存偏移为 0" to J1RecoveryContract.buildClamp(body, units, 0).second,
            "保存偏移越界" to J1RecoveryContract.buildClamp(body, units, 4000).second,
            "书籍身份错配" to J1RecoveryContract.buildClamp(
                body, units, 3200,
                J1ProgressSnapshot(3200, 0.9f, "2026-10-06T00:00:00Z", "{}", "wrong-clamp-book"),
                expectedBookId = "correct-clamp-book",
            ).second,
        )
        for ((name, reason) in badClamp) {
            assertFalse("短末项负向用例 [$name] 必须被拒绝", reason == "ok")
        }
        assertEquals("ok", J1RecoveryContract.buildClamp(body, units, 3200).second)

        // 验证移动判定拒绝旧 S 冻结反例
        val cOk = J1RecoveryContract.buildClamp(
            body, units, 3400,
            J1ProgressSnapshot(3400, 0.95f, "2026-10-06T02:00:00Z", "{}", CLAMP_BOOK_ID),
            expectedBookId = CLAMP_BOOK_ID,
        ).first!!
        val rSnap = J1ProgressSnapshot(3200, 0.90f, "2026-10-06T02:01:00Z", "{}", CLAMP_BOOK_ID)
        val freezeSnap = J1ProgressSnapshot(3400, 0.95f, "2026-10-06T02:02:00Z", "{}", CLAMP_BOOK_ID)
        val dumpMarker = "<node text=\"${J1RecoveryContract.clampMarker(1)}\"/>"
        val freezeVerdict = J1RecoveryContract.clampNewPositionAccepted(
            cOk, units, rSnap, freezeSnap, body, dumpMarker,
        )
        assertFalse("反例：移动后仍为旧保存值 S 必须被拒绝", freezeVerdict.accepted)
        assertTrue(freezeVerdict.reason.contains("保存冻结"))

        writeResult(
            "j1_clamp_70_negative_clampRejected",
            JSONObject().put("result", "PASS").put("cases", badClamp.toString()),
            binding(),
        )
    }

    // ── 7. 终态记录 ────────────────────────────────────────────────────────────

    @Test
    fun j1_clamp_90_recordFinalState() {
        val binding = binding()
        val db = openDb()
        try {
            val cnt = rawOne(db, "SELECT COUNT(*) FROM books WHERE id='$CLAMP_BOOK_ID'")?.toInt() ?: 0
            writeResult(
                "j1_clamp_90_recordFinalState",
                JSONObject().put("result", "OBSERVED").put("bookRows", cnt),
                binding,
            )
        } finally {
            db.close()
        }
    }

    /** Live-driver entry: test is already active before UI action and dump are collected. */
    @Test
    fun j1_clamp_20_liveHandshake() {
        Task35LiveHandshake.awaitAndCapture(context, "j1_clamp_20_reentrySettled")
        j1_clamp_20_reentrySettled()
    }

    /** Live-driver entry: capture the moved reader in the active host process. */
    @Test
    fun j1_clamp_30_liveHandshake() {
        Task35LiveHandshake.awaitAndCapture(context, "j1_clamp_30_newPositionSaved")
        j1_clamp_30_newPositionSaved()
    }

    /** Live-driver entry: after the driver proves the old PID absent, open in this process. */
    @Test
    fun j1_clamp_40_liveHandshake() {
        Task35LiveHandshake.awaitAndCapture(context, "j1_clamp_40_coldReentryVerify")
        j1_clamp_40_coldReentryVerify()
    }
}
