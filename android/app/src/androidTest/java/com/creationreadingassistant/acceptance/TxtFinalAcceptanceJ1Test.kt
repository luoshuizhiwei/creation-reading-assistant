package com.creationreadingassistant.acceptance

import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.TxtTocProfile
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
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
 * CRA-TXT-PERSIST-ACCEPT-HARDENING-v2 —— J1 最终验收测试（强化恢复后移动保存与防冻结）。
 *
 * ## 重点强化（防保存冻结假通过）
 * 1. 独立记录退出保存 S 与恢复完成后的基准 R 以及完整进度行（bookId、offset、percent、updated_at、locator）；
 * 2. 真实 UI 滚动到明确不同位置，dump 与正文标记证明位移；
 * 3. 严格断言：不仅 offset > 0，且必须 != R，更**不能等于旧 S**！核验 updated_at 实际变化与书籍身份；
 * 4. 普通恢复仍按阅读单元合同（单元起点或精确 S，真实 UI 标记）；
 * 5. 负向用例覆盖“UI 有位移但 DB 仍旧 S / updated_at 不变”的反例。
 */
@RunWith(AndroidJUnit4::class)
class TxtFinalAcceptanceJ1Test {

    companion object {
        const val BOOK_ID = "j1-final-b1"
        const val BOOK_TITLE = "J1 恢复验收中性 TXT"
        const val RESULT_DIR = "j1-accept"
        const val TAG = "TxtFinalAcceptanceJ1"
    }

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun resultsDir(): File =
        File(context.getExternalFilesDir(null), RESULT_DIR).also { it.mkdirs() }

    private fun bodyFile(): File = File(context.filesDir, "books/$BOOK_ID/content.txt")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

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

    private fun readProgressSnapshot(db: AppDatabase): J1ProgressSnapshot? {
        val sql = "SELECT current_location_json, progress_percent, updated_at, book_id FROM reading_progress " +
            "WHERE book_id='$BOOK_ID' ORDER BY updated_at DESC LIMIT 1"
        return db.openHelper.writableDatabase.query(sql).use { c ->
            if (c.moveToFirst()) {
                val locJson = if (!c.isNull(0)) c.getString(0) else ""
                val percent = if (!c.isNull(1)) c.getFloat(1) else 0f
                val updatedAt = if (!c.isNull(2)) c.getString(2) else ""
                val bookId = if (!c.isNull(3)) c.getString(3) else ""
                val offset = LocatorCodec.locatorFromProgress(locJson)?.legacyOffset ?: -1
                J1ProgressSnapshot(offset, percent, updatedAt, locJson, bookId)
            } else {
                null
            }
        }
    }

    private fun awaitProgressSnapshot(db: AppDatabase, timeoutMs: Long = 6000L,
                                      ready: (J1ProgressSnapshot?) -> Boolean): J1ProgressSnapshot? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var last: J1ProgressSnapshot? = null
        do {
            last = readProgressSnapshot(db)
            if (ready(last)) return last
            SystemClock.sleep(100L)
        } while (SystemClock.elapsedRealtime() < deadline)
        return last
    }

    private fun binding(): JSONObject {
        val f = File(resultsDir(), "binding.json")
        assertTrue(
            "缺绑定文件 binding.json（${f.absolutePath}）——缺必要绑定值不得执行",
            f.isFile,
        )
        val obj = JSONObject(f.readText())
        for (key in listOf(
            "taskId", "runId", "hostPackage", "testPackage", "serial",
            "candidateDir", "configSha256", "driverSha256", "runnerSha256", "fixtureSha256",
        )) {
            assertTrue("绑定字段缺失或为空：$key", obj.optString(key).isNotBlank())
        }
        return obj
    }

    private fun writeResult(step: String, payload: JSONObject, binding: JSONObject?) {
        val obj = JSONObject()
            .put("step", step)
            .put("pid", Process.myPid())
            .put("packageName", context.packageName)
            .put("timestampMillis", System.currentTimeMillis())
            .put("at", Instant.now().toString())
        if (binding != null) {
            obj.put("taskId", binding.optString("taskId"))
            obj.put("runId", binding.optString("runId"))
            obj.put("configSha256", binding.optString("configSha256"))
            obj.put("fixtureSha256", binding.optString("fixtureSha256"))
        }
        obj.put("payload", payload)
        File(resultsDir(), "$step.json").writeText(obj.toString(2))
        android.util.Log.i(TAG, "$step $payload")
    }

    private fun readPushed(name: String): String? {
        File(resultsDir(), name).takeIf { it.isFile }?.let { return it.readText() }
        val bindingFile = File(resultsDir(), "binding.json")
        if (!bindingFile.isFile) return null
        val b = JSONObject(bindingFile.readText())
        val task = b.optString("taskId")
        val run = b.optString("runId")
        if (task.isBlank() || run.isBlank()) return null
        val root = File(resultsDir(), "$task/$run")
        return root.takeIf { it.isDirectory }?.walkTopDown()
            ?.firstOrNull { it.isFile && it.name.endsWith("__$name") }
            ?.readText()
    }

    private fun fixtureBody(): String {
        val text = readPushed("fixture.txt")
        assertTrue("缺夹具 fixture.txt（应推送至 Android/data/<pkg>/files/j1-accept/）", text != null)
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
            "DELETE FROM reading_progress WHERE book_id='$BOOK_ID'",
            "DELETE FROM highlights WHERE book_id='$BOOK_ID'",
            "DELETE FROM notes WHERE book_id='$BOOK_ID'",
            "DELETE FROM books WHERE id='$BOOK_ID'",
            "DELETE FROM book_files WHERE book_id='$BOOK_ID'",
        ).forEach { runCatching { wdb.execSQL(it) } }
    }

    // ── 1. 夹具准备 ────────────────────────────────────────────────────────────

    @Test
    fun j1_00_seedFixture() = runBlocking {
        val binding = binding()
        val db = openDb()
        try {
            cleanRows(db)
            val body = fixtureBody()
            bodyFile().apply { parentFile!!.mkdirs(); writeText(body) }
            db.bookDao().upsert(
                BookEntity(
                    id = BOOK_ID, title = BOOK_TITLE, author = "中性作者", format = "txt",
                    size = body.toByteArray().size, content_status = "available",
                    imported_at = Instant.now().toString(), revision = 1,
                    updated_at = Instant.now().toString(),
                    local_content_path = bodyFile().absolutePath,
                ),
            )
            val chapters = PlainTextDocument(body, TxtTocProfile.fromRuleId("builtin")).chapters
            // CRA-TXT-PERSIST-MERGE-MUMU-ACCEPT-v1 设备适配修正（沿用
            // CRA-TXT-FINAL-MUMU-ACCEPT-v1 先例）：生产 TxtChapterDetector 合同是
            // 「识别不出 → 单章『全文』，永不为空」。B 工件继承的 prep-v2 断言
            // isEmpty 与生产合同矛盾——本夹具（15e6f965，无章节标题行）实测
            // chapters=1（设备探针证据见任务 evidence/diagnostics/）。断言改为
            // 单章全文兜底；夹具正文、单元映射与其余断言不变。
            assertEquals(
                "夹具（无章节标题）应命中生产兜底合同：单章『全文』",
                1, chapters.size,
            )
            assertEquals("单章应为全文兜底", "全文", chapters.first().title)
            assertTrue("夹具标记必须全书唯一", J1RecoveryContract.markersUnique(body))
            val units = productionUnits(body)
            assertTrue("夹具必须覆盖 >= 4 个阅读单元，实际=${units.size}", units.size >= 4)
            assertTrue(
                "夹具必须存在 >= 2 个标记",
                J1RecoveryContract.markerOffsets(body).size >= 2,
            )
            val unitsArr = JSONArray()
            units.forEach { unitsArr.put(JSONArray().put(it.unitIndex).put(it.charStart).put(it.charCount)) }
            val bodySha = sha256(body)
            assertEquals(
                "夹具正文 SHA 必须与绑定声明一致",
                binding.optString("fixtureSha256"), bodySha,
            )
            writeResult(
                "j1_00_seedFixture",
                JSONObject()
                    .put("fixtureOnly", true)
                    .put("note", "夹具准备不算产品通过")
                    .put("bodyLength", body.length)
                    .put("bodySha256", bodySha)
                    .put("unitCount", units.size)
                    .put("units", unitsArr),
                binding,
            )
        } finally {
            db.close()
        }
    }

    // ── 2. 退出保存（观察）：建立退出保存 S 合同 ────────────────────────────────

    @Test
    fun j1_10_recordExitSaved() {
        val binding = binding()
        val db = openDb()
        try {
            val body = bodyFile().readText()
            val units = productionUnits(body)
            val snap = readProgressSnapshot(db)
            val saved = snap?.legacyOffset
            val (contract, reason) = J1RecoveryContract.build(body, units, saved, snap, expectedBookId = BOOK_ID)
            assertNotNull("J1：退出保存后必须存在可解码的非零进度（原因：$reason）", contract)
            writeResult(
                "j1_10_recordExitSaved",
                JSONObject()
                    .put("result", "OBSERVED")
                    .put("bodyLength", contract!!.bodyLength)
                    .put("bodySha256", sha256(body))
                    .put("rawProgressJson", snap?.currentLocationJson ?: "")
                    .put("savedOffset", contract.savedOffset)
                    .put("savedPercent", snap?.progressPercent ?: 0f)
                    .put("savedUpdatedAt", snap?.updatedAt ?: "")
                    .put("savedBookId", snap?.bookId ?: "")
                    .put("savedUnitIndex", contract.savedUnitIndex)
                    .put("savedUnitStart", contract.savedUnitStart)
                    .put("savedUnitEnd", contract.savedUnitEnd)
                    .put("savedParagraphMarker", contract.savedParagraphMarker)
                    .put("expectedRestoreOffset", contract.expectedRestoreOffset)
                    .put("expectedVisibleMarker", contract.expectedVisibleMarker)
                    .put("note", "退出保存值 S 与完整进度行记录，作为后续恢复与移动基准"),
                binding,
            )
        } finally {
            db.close()
        }
    }

    private fun readContract(binding: JSONObject): J1Contract {
        val f = File(resultsDir(), "j1_10_recordExitSaved.json")
        assertTrue("缺退出保存合同证据（j1_10_recordExitSaved.json）", f.isFile)
        val p = JSONObject(f.readText()).getJSONObject("payload")
        return J1Contract(
            bodyLength = p.getInt("bodyLength"),
            savedOffset = p.getInt("savedOffset"),
            savedUnitIndex = p.getInt("savedUnitIndex"),
            savedUnitStart = p.getInt("savedUnitStart"),
            savedUnitEnd = p.getInt("savedUnitEnd"),
            savedParagraphMarker = p.getString("savedParagraphMarker"),
            expectedRestoreOffset = p.getInt("expectedRestoreOffset"),
            expectedVisibleMarker = p.getString("expectedVisibleMarker"),
            savedUpdatedAt = p.optString("savedUpdatedAt", ""),
            savedPercent = p.optDouble("savedPercent", 0.0).toFloat(),
            savedLocationJson = p.optString("rawProgressJson", ""),
            bookId = p.optString("savedBookId", BOOK_ID),
        )
    }

    private fun observeReentry(step: String, uiDumpName: String) {
        val binding = binding()
        val db = openDb()
        try {
            val body = bodyFile().readText()
            val units = productionUnits(body)
            val contract = readContract(binding)
            val snap = readProgressSnapshot(db)
            val reentered = snap?.legacyOffset
            val dump = readPushed(uiDumpName)
            val verdict = J1RecoveryContract.reentryAccepted(contract, units, reentered, dump)
            writeResult(
                step,
                JSONObject()
                    .put("result", if (verdict.accepted) "ACCEPTED" else "REJECTED")
                    .put("accepted", verdict.accepted)
                    .put("reason", verdict.reason)
                    .put("reenteredOffset", reentered ?: -1)
                    .put("reenteredPercent", snap?.progressPercent ?: 0f)
                    .put("reenteredUpdatedAt", snap?.updatedAt ?: "")
                    .put("reenteredBookId", snap?.bookId ?: "")
                    .put("reenteredLocationJson", snap?.currentLocationJson ?: "")
                    .put("savedOffset", contract.savedOffset)
                    .put("savedUnitIndex", contract.savedUnitIndex)
                    .put("savedUnitStart", contract.savedUnitStart)
                    .put("expectedVisibleMarker", contract.expectedVisibleMarker)
                    .put("uiDumpPresent", dump != null)
                    .put("uiDumpSha256", dump?.let { sha256(it) } ?: ""),
                binding,
            )
            assertTrue("J1 [$step]：${verdict.reason}", verdict.accepted)
        } finally {
            db.close()
        }
    }

    // ── 3. 等待恢复期间未被清零 ────────────────────────────────────────────────

    @Test
    fun j1_15_waitNoZero() {
        val binding = binding()
        val db = openDb()
        try {
            val body = bodyFile().readText()
            val units = productionUnits(body)
            val contract = readContract(binding)
            val snap = readProgressSnapshot(db)
            val observed = snap?.legacyOffset
            val verdict = J1RecoveryContract.noZeroDuringWaitAccepted(contract, units, listOf(observed))
            writeResult(
                "j1_15_waitNoZero",
                JSONObject()
                    .put("result", if (verdict.accepted) "ACCEPTED" else "REJECTED")
                    .put("accepted", verdict.accepted)
                    .put("reason", verdict.reason)
                    .put("observedOffset", observed ?: -1)
                    .put("waitPhase", "persistence_hold")
                    .put("savedOffset", contract.savedOffset)
                    .put("note", "停在首页/书架时验证原非零进度未清零"),
                binding,
            )
            assertTrue("J1 等待期不得清零：${verdict.reason}", verdict.accepted)
        } finally {
            db.close()
        }
    }

    // ── 4–6. 三条重进路径 ──────────────────────────────────────────────────────

    @Test
    fun j1_20_reentryFromHome() = observeReentry("j1_20_reentryFromHome", "ui_reentry_home.xml")

    @Test
    fun j1_30_reentryFromShelf() = observeReentry("j1_30_reentryFromShelf", "ui_reentry_shelf.xml")

    @Test
    fun j1_40_reentryColdStart() = observeReentry("j1_40_reentryColdStart", "ui_cold.xml")

    // ── 7. 恢复后继续滚动并退出：验证新位置保存且无保存冻结 ────────────────────

    /**
     * CRA-TXT-ACCEPT-CHAIN-REPAIR-v3（接缝 1）：存活握手旅程。
     *
     * v2 的缺陷：before_item 在仪器运行前就 back 退出，本方法只能在退出后连续
     * 读两次 DB，既不能证明"退出前 S2"也不能证明"退出后 Exit"，还把仪器自身
     * PID 当成 UI 会话身份。v3 改为：
     *   (a) 驱动在**阅读器仍存活**时经独立 DB 读通道取得 S2 完整行，并记录真实
     *       UI 会话 PID（pidof + 前台校验 + 存活 dump），写 handshake_s2.json；
     *   (b) 握手成立后才 back 退出，退出后取 Exit 完整行写 handshake_exit.json；
     *   (c) 本方法读回两份握手（同一字节，SHA 绑定），用**自身读库**交叉核对退出后
     *       行；载荷 `sessionPid` 取自握手记录的**真实 UI 会话 PID**，不是本进程 PID。
     *   (d) Cold —— 由 j1_65s_coldReadNewPosition 在新进程完成，绑定上一真实 UI
     *       会话身份（自身 PID 必须不同于握手记录的会话 PID）。
     * 旧 j1_55/60/65 三方法的全部用户合同逐项保留；
     * 旧→新映射：j1_55+j1_60 → j1_55s（存活握手旅程），j1_65 → j1_65s（新进程）。
     */
    @Test
    fun j1_55s_newPositionJourney() {
        val binding = binding()
        val db = openDb()
        try {
            val body = bodyFile().readText()
            val units = productionUnits(body)
            val contract = readContract(binding)

            val coldFile = File(resultsDir(), "j1_40_reentryColdStart.json")
            val (baselineR, baselineSnap) = if (coldFile.isFile) {
                val pr = JSONObject(coldFile.readText()).getJSONObject("payload")
                val r = pr.optInt("reenteredOffset", -1)
                r to J1ProgressSnapshot(
                    r,
                    pr.optDouble("reenteredPercent", 0.0).toFloat(),
                    pr.optString("reenteredUpdatedAt", ""),
                    pr.optString("reenteredLocationJson", ""),
                    pr.optString("reenteredBookId", BOOK_ID),
                )
            } else {
                -1 to null
            }

            val dumpAfter = readPushed("ui_new_scroll.xml")
            val dumpBefore = readPushed("ui_before_new_scroll.xml")
            assertTrue("缺位移 canonical dump（链路必须经真实交付）", dumpAfter != null && dumpBefore != null)

            // 接缝 1：读回驱动侧两份握手（同一字节，SHA 绑定）
            val hsS2Text = readPushed("handshake_s2.json")
            assertTrue("缺存活握手 handshake_s2.json（阅读器存活时的独立 S2 观测）", hsS2Text != null)
            val hsExitText = readPushed("handshake_exit.json")
            assertTrue("缺退出后握手 handshake_exit.json（退出后的独立 Exit 观测）", hsExitText != null)
            val hsS2 = JSONObject(hsS2Text!!)
            val hsExit = JSONObject(hsExitText!!)
            val hsS2Sha = sha256(hsS2Text)
            val hsExitSha = sha256(hsExitText)

            // 身份：握手必须绑定同一 task/run/step，且记录真实 UI 会话 PID
            val sessionPid = hsS2.optInt("readerSessionPid", -1)
            val hsIdentityOk = hsS2.optString("taskId") == binding.optString("taskId") &&
                hsS2.optString("runId") == binding.optString("runId") &&
                hsS2.optString("stepId") == "j1_55s_newPositionJourney" &&
                hsS2.optString("phase") == "reader_alive" &&
                hsExit.optString("phase") == "after_exit" &&
                hsExit.optInt("readerSessionPid", -1) == sessionPid &&
                hsExit.optString("s2HandshakeSha256") == hsS2Sha &&
                hsS2.optBoolean("s2RowPresent", false) && hsExit.optBoolean("exitRowPresent", false)

            val s2Row = J1ProgressSnapshot(
                hsS2.optInt("offset", -1),
                hsS2.optDouble("percent", 0.0).toFloat(),
                hsS2.optString("updatedAt", ""),
                hsS2.optString("locationJson", ""),
                hsS2.optString("bookId", ""),
            )
            val exitRow = J1ProgressSnapshot(
                hsExit.optInt("offset", -1),
                hsExit.optDouble("percent", 0.0).toFloat(),
                hsExit.optString("updatedAt", ""),
                hsExit.optString("locationJson", ""),
                hsExit.optString("bookId", ""),
            )

            // 自身读库交叉核对：退出后当前持久行必须仍是 S2 位置（时间戳不早于 S2）
            val ownRow = readProgressSnapshot(db)
            val rowsMatch = ownRow != null && ownRow.bookId == s2Row.bookId &&
                ownRow.legacyOffset == s2Row.legacyOffset &&
                ownRow.currentLocationJson == s2Row.currentLocationJson &&
                ownRow.progressPercent == s2Row.progressPercent &&
                timestampAtLeast(ownRow.updatedAt, s2Row.updatedAt)

            val s2Accepted = hsIdentityOk && sessionPid > 0 && s2Row.bookId == BOOK_ID &&
                s2Row.legacyOffset > 0 && s2Row.legacyOffset != contract.savedOffset &&
                s2Row.legacyOffset != baselineR && s2Row.updatedAt.isNotBlank() &&
                s2Row.updatedAt != contract.savedUpdatedAt && s2Row.updatedAt != baselineSnap?.updatedAt &&
                s2Row.currentLocationJson.isNotBlank() &&
                (J1RecoveryContract.lastMarkerAtOrBefore(body, s2Row.legacyOffset)?.let { dumpAfter?.contains(it) } == true)

            val exitAccepted = s2Accepted && exitRow.bookId == s2Row.bookId &&
                exitRow.legacyOffset == s2Row.legacyOffset &&
                exitRow.currentLocationJson == s2Row.currentLocationJson &&
                exitRow.progressPercent == s2Row.progressPercent &&
                exitRow.updatedAt.isNotBlank() && timestampAtLeast(exitRow.updatedAt, s2Row.updatedAt)

            val handshakeVerified = hsIdentityOk && rowsMatch
            val allAccepted = s2Accepted && exitAccepted && handshakeVerified
            val reason = when {
                allAccepted -> "存活握手 S2 + 退出后 Exit 完整行对账成立（真实 UI 会话 pid=$sessionPid）"
                !hsIdentityOk -> "握手身份/阶段不符（缺存活或退出后握手、SHA 不匹配）"
                !rowsMatch -> "自身读库与握手 Exit 行不一致（保存冻结或行被改写）"
                !s2Accepted -> "S2 未达成：DB 行与 R/S/时间戳全同或缺失（保存冻结反例）"
                else -> "退出完整行缺失或与 S2 不一致"
            }
            writeResult(
                "j1_55s_newPositionJourney",
                JSONObject()
                    .put("result", if (allAccepted) "ACCEPTED" else "REJECTED")
                    .put("accepted", allAccepted)
                    .put("reason", reason)
                    .put("sessionPid", sessionPid)
                    .put("instrumentPid", Process.myPid())
                    .put("baselineR", baselineR)
                    .put("savedOffset", contract.savedOffset)
                    .put("handshakeVerified", handshakeVerified)
                    .put("handshake", JSONObject()
                        .put("s2Present", hsS2.optBoolean("s2RowPresent", false))
                        .put("exitPresent", hsExit.optBoolean("exitRowPresent", false))
                        .put("sessionPidMatch", hsIdentityOk)
                        .put("rowsMatch", rowsMatch)
                        .put("s2Sha256", hsS2Sha)
                        .put("exitSha256", hsExitSha))
                    .put("s2", JSONObject()
                        .put("accepted", s2Accepted)
                        .put("offset", s2Row.legacyOffset)
                        .put("percent", s2Row.progressPercent)
                        .put("updatedAt", s2Row.updatedAt)
                        .put("locationJson", s2Row.currentLocationJson)
                        .put("bookId", s2Row.bookId))
                    .put("exit", JSONObject()
                        .put("accepted", exitAccepted)
                        .put("offset", exitRow.legacyOffset)
                        .put("percent", exitRow.progressPercent)
                        .put("updatedAt", exitRow.updatedAt)
                        .put("locationJson", exitRow.currentLocationJson)
                        .put("bookId", exitRow.bookId))
                    .put("uiDumpPresent", dumpAfter != null)
                    .put("uiDumpSha256", dumpAfter?.let { sha256(it) } ?: ""),
                binding,
            )
            assertTrue("J1 存活握手 S2+Exit 旅程：$reason", allAccepted)
        } finally {
            db.close()
        }
    }

    /**
     * 接缝 1/3 对应物：冷进程读回（必须发生在新进程，并绑定**上一真实 UI 会话身份**）。
     *
     * 冷读在 force-stop + 冷启动后的新进程内运行，因此自身 PID 必须不同于
     * j1_55s 载荷记录的 UI 会话 PID（`previousSessionPid`）——不得把新进程
     * 冒充为先前 UI 会话，也不得复用旧会话身份。
     */
    @Test
    fun j1_65s_coldReadNewPosition() {
        val binding = binding()
        val db = openDb()
        try {
            val body = bodyFile().readText(); val units = productionUnits(body)
            val journeyFile = File(resultsDir(), "j1_55s_newPositionJourney.json")
            assertTrue("缺 j1_55s 旅程 S2 证据", journeyFile.isFile)
            val jp = JSONObject(journeyFile.readText()).getJSONObject("payload")
            val p = jp.getJSONObject("s2")
            val s2Offset = p.optInt("offset", -1)
            assertTrue("j1_55s S2 未成立，不得冷读", s2Offset > 0)
            val previousSessionPid = jp.optInt("sessionPid", -1)
            assertTrue("j1_55s 未记录上一真实 UI 会话身份", previousSessionPid > 0)
            val coldPid = Process.myPid()
            assertTrue("冷读必须在新进程（不得复用上一 UI 会话进程）", coldPid != previousSessionPid)

            val expectedUnit = J1RecoveryContract.unitIndexForOffset(units, s2Offset)
            val dump = readPushed("ui_cold_after_new.xml")
            val expectedMarker = J1RecoveryContract.lastMarkerAtOrBefore(body, s2Offset)
            val saved = awaitProgressSnapshot(db, timeoutMs = 15_000L) { row -> row != null && row.bookId == BOOK_ID &&
                row.currentLocationJson.isNotBlank() && row.updatedAt.isNotBlank() && row.legacyOffset >= 0 &&
                J1RecoveryContract.unitIndexForOffset(units, row.legacyOffset) == expectedUnit &&
                (row.legacyOffset == s2Offset || row.legacyOffset == units[expectedUnit].charStart) &&
                expectedMarker != null && dump?.contains(expectedMarker) == true }
            val exactOrStart = saved != null && (saved.legacyOffset == s2Offset ||
                saved.legacyOffset == units[expectedUnit].charStart)
            val marker = J1RecoveryContract.lastMarkerAtOrBefore(body, saved?.legacyOffset ?: -1)
            val ok = saved != null && saved.bookId == BOOK_ID && saved.currentLocationJson.isNotBlank() &&
                saved.updatedAt.isNotBlank() && saved.legacyOffset > 0 && exactOrStart &&
                marker != null && dump?.contains(marker) == true && coldPid != previousSessionPid
            writeResult("j1_65s_coldReadNewPosition", JSONObject().put("accepted", ok)
                .put("reason", if (ok) "S2之后冷读回位置与正文marker一致且绑定上一UI会话身份"
                    else "冷读回与S2不符或未绑定上一UI会话身份")
                .put("coldPid", coldPid).put("previousSessionPid", previousSessionPid)
                .put("coldOffset", saved?.legacyOffset ?: -1).put("coldPercent", saved?.progressPercent ?: -1f)
                .put("coldUpdatedAt", saved?.updatedAt ?: "").put("coldLocationJson", saved?.currentLocationJson ?: "")
                .put("coldBookId", saved?.bookId ?: "").put("uiDumpPresent", dump != null), binding)
            assertTrue("S2之后冷读回必须与S2一致且绑定上一UI会话身份", ok)
        } finally { db.close() }
    }

    @Test
    fun j1_55_newPositionSaved() {
        val binding = binding()
        val db = openDb()
        try {
            val body = bodyFile().readText()
            val units = productionUnits(body)
            val contract = readContract(binding)

            // 读取冷启动重进基准 R 与进度行
            val coldFile = File(resultsDir(), "j1_40_reentryColdStart.json")
            val (baselineR, baselineSnap) = if (coldFile.isFile) {
                val p = JSONObject(coldFile.readText()).getJSONObject("payload")
                val r = p.optInt("reenteredOffset", -1)
                val snap = J1ProgressSnapshot(
                    r,
                    p.optDouble("reenteredPercent", 0.0).toFloat(),
                    p.optString("reenteredUpdatedAt", ""),
                    p.optString("reenteredLocationJson", ""),
                    p.optString("reenteredBookId", BOOK_ID),
                )
                r to snap
            } else {
                -1 to null
            }

            // 读取退出后持久化的新进度行 S2
            val dumpAfter = readPushed("ui_new_scroll.xml")
            val dumpBefore = readPushed("ui_before_new_scroll.xml")
            val newSnap = awaitProgressSnapshot(db) { snap ->
                snap != null && snap.bookId == BOOK_ID && snap.currentLocationJson.isNotBlank() &&
                    snap.legacyOffset > 0 && snap.legacyOffset != contract.savedOffset &&
                    snap.legacyOffset != baselineR && snap.updatedAt.isNotBlank() &&
                    snap.updatedAt != contract.savedUpdatedAt && snap.updatedAt != baselineSnap?.updatedAt &&
                    (J1RecoveryContract.lastMarkerAtOrBefore(body, snap.legacyOffset)?.let { dumpAfter?.contains(it) } == true)
            }

            val verdict = J1RecoveryContract.newPositionAccepted(
                contract = contract,
                units = units,
                reenteredOffset = baselineR.takeIf { it >= 0 },
                baselineSnapshot = baselineSnap,
                newSnapshot = newSnap,
                body = body,
                uiDumpAfterMove = dumpAfter,
                uiDumpBeforeMove = dumpBefore,
            )

            writeResult(
                "j1_55_newPositionSaved",
                JSONObject()
                    .put("result", if (verdict.accepted) "ACCEPTED" else "REJECTED")
                    .put("accepted", verdict.accepted)
                    .put("reason", verdict.reason)
                    .put("baselineR", baselineR)
                    .put("savedOffset", contract.savedOffset)
                    .put("newOffset", newSnap?.legacyOffset ?: -1)
                    .put("newPercent", newSnap?.progressPercent ?: 0f)
                    .put("newLocationJson", newSnap?.currentLocationJson ?: "")
                    .put("newUpdatedAt", newSnap?.updatedAt ?: "")
                    .put("savedUpdatedAt", contract.savedUpdatedAt ?: "")
                    .put("newBookId", newSnap?.bookId ?: "")
                    .put("uiDumpPresent", dumpAfter != null)
                    .put("uiDumpSha256", dumpAfter?.let { sha256(it) } ?: ""),
                binding,
            )
            assertTrue("J1 恢复后移动新位置保存与防冻结校验：${verdict.reason}", verdict.accepted)
        } finally {
            db.close()
        }
    }

    @Test
    fun j1_60_exitPositionSaved() {
        val binding = binding()
        val db = openDb()
        try {
            val s2File = File(resultsDir(), "j1_55_newPositionSaved.json")
            assertTrue("缺S2完整行", s2File.isFile)
            val s2 = JSONObject(s2File.readText()).getJSONObject("payload")
            val exit = awaitProgressSnapshot(db) { row -> row != null && row.bookId == s2.optString("newBookId") &&
                row.legacyOffset == s2.optInt("newOffset", -1) && row.currentLocationJson == s2.optString("newLocationJson") &&
                row.progressPercent == s2.optDouble("newPercent", -1.0).toFloat() &&
                timestampAtLeast(row.updatedAt, s2.optString("newUpdatedAt")) }
            val same = exit != null && exit.bookId == s2.optString("newBookId") &&
                exit.legacyOffset == s2.optInt("newOffset", -1) &&
                exit.currentLocationJson == s2.optString("newLocationJson") &&
                exit.progressPercent == s2.optDouble("newPercent", -1.0).toFloat() &&
                exit.updatedAt.isNotBlank() && exit.updatedAt == s2.optString("newUpdatedAt")
            writeResult("j1_60_exitPositionSaved", JSONObject()
                .put("accepted", same).put("reason", if (same) "退出行与S2完整行一致" else "退出行缺失或与S2不一致")
                .put("exitOffset", exit?.legacyOffset ?: -1).put("exitPercent", exit?.progressPercent ?: -1f)
                .put("exitUpdatedAt", exit?.updatedAt ?: "").put("exitLocationJson", exit?.currentLocationJson ?: "")
                .put("exitBookId", exit?.bookId ?: ""), binding)
            assertTrue("S2之后退出持久化行必须与S2匹配", same)
        } finally { db.close() }
    }

    @Test
    fun j1_65_coldReadNewPosition() {
        val binding = binding()
        val db = openDb()
        try {
            val body = bodyFile().readText(); val units = productionUnits(body)
            val s2File = File(resultsDir(), "j1_55_newPositionSaved.json")
            assertTrue("缺S2快照", s2File.isFile)
            val p = JSONObject(s2File.readText()).getJSONObject("payload")
            val s2Offset = p.optInt("newOffset", -1)
            val expectedUnit = J1RecoveryContract.unitIndexForOffset(units, s2Offset)
            val dump = readPushed("ui_cold_after_new.xml")
            val expectedMarker = J1RecoveryContract.lastMarkerAtOrBefore(body, s2Offset)
            val saved = awaitProgressSnapshot(db) { row -> row != null && row.bookId == BOOK_ID &&
                row.currentLocationJson.isNotBlank() && row.updatedAt.isNotBlank() && row.legacyOffset >= 0 &&
                J1RecoveryContract.unitIndexForOffset(units, row.legacyOffset) == expectedUnit &&
                (row.legacyOffset == s2Offset || row.legacyOffset == units[expectedUnit].charStart) &&
                expectedMarker != null && dump?.contains(expectedMarker) == true }
            val exactOrStart = saved != null && (saved.legacyOffset == s2Offset ||
                saved.legacyOffset == units[expectedUnit].charStart)
            val marker = J1RecoveryContract.lastMarkerAtOrBefore(body, saved?.legacyOffset ?: -1)
            val ok = saved != null && saved.bookId == BOOK_ID && saved.currentLocationJson.isNotBlank() &&
                saved.updatedAt.isNotBlank() && saved.legacyOffset > 0 && exactOrStart &&
                marker != null && dump?.contains(marker) == true
            writeResult("j1_65_coldReadNewPosition", JSONObject().put("accepted", ok)
                .put("reason", if (ok) "S2之后冷读回位置与正文marker一致" else "冷读回与S2不符")
                .put("coldOffset", saved?.legacyOffset ?: -1).put("coldPercent", saved?.progressPercent ?: -1f)
                .put("coldUpdatedAt", saved?.updatedAt ?: "").put("coldLocationJson", saved?.currentLocationJson ?: "")
                .put("coldBookId", saved?.bookId ?: "").put("uiDumpPresent", dump != null), binding)
            assertTrue("S2之后冷读回必须匹配", ok)
        } finally { db.close() }
    }

    // ── 8. 负向验证：覆盖零、错单元、坏合同、以及保存冻结反例 ──────────────────

    @Test
    fun j1_71_negative_zeroAndWrongUnitRejected() {
        val units = listOf(
            ReadingUnitRange(0, 0, 3000),
            ReadingUnitRange(1, 3000, 3000),
            ReadingUnitRange(2, 6000, 3000),
        )
        val body = "a".repeat(3000) + J1RecoveryContract.marker(1) + "b".repeat(2996) +
            J1RecoveryContract.marker(2) + "c".repeat(2996)
        val (contract, _) = J1RecoveryContract.build(body, units, 4553)
        assertNotNull(contract)
        val dumpOk = "<node text=\"${contract!!.expectedVisibleMarker}\"/>"

        val cases = linkedMapOf(
            "S=4553->R=0（清零）" to J1RecoveryContract.reentryAccepted(contract, units, 0, dumpOk),
            "非零但恢复到错误单元（R=6553，unit=2）" to J1RecoveryContract.reentryAccepted(contract, units, 6553, dumpOk),
            "非零但恢复到错误单元（R=2999，unit=0）" to J1RecoveryContract.reentryAccepted(contract, units, 2999, dumpOk),
            "R 在单元内但既非起点也非精确值（R=3553）" to J1RecoveryContract.reentryAccepted(contract, units, 3553, dumpOk),
            "UI dump 缺失" to J1RecoveryContract.reentryAccepted(contract, units, 3000, null),
            "UI dump 不含恢复目标标记" to J1RecoveryContract.reentryAccepted(contract, units, 3000, "<node text=\"无关\"/>"),
            "合法单元起点恢复（R=3000）" to J1RecoveryContract.reentryAccepted(contract, units, 3000, dumpOk),
            "精确偏移恢复（R=4553）" to J1RecoveryContract.reentryAccepted(contract, units, 4553, dumpOk),
        )
        val accepted = cases.filterValues { it.accepted }.keys
        assertEquals(
            "应仅单元起点与精确偏移两条正例",
            setOf("合法单元起点恢复（R=3000）", "精确偏移恢复（R=4553）"),
            accepted,
        )
        writeResult(
            "j1_71_negative_zeroAndWrongUnitRejected",
            JSONObject().put("result", "PASS").put("cases", cases.mapValues { it.value.reason }.toString()),
            binding(),
        )
    }

    @Test
    fun j1_72_negative_invalidContractRejected() {
        val units = listOf(ReadingUnitRange(0, 0, 3000), ReadingUnitRange(1, 3000, 3000))
        val body = "a".repeat(3000) + J1RecoveryContract.marker(1) + "b".repeat(2996)
        val bad = linkedMapOf(
            "S=null" to J1RecoveryContract.build(body, units, null).second,
            "S=0" to J1RecoveryContract.build(body, units, 0).second,
            "S 越界" to J1RecoveryContract.build(body, units, body.length).second,
            "S 在初始单元" to J1RecoveryContract.build(body, units, 1000).second,
            "单元为空" to J1RecoveryContract.build(body, emptyList(), 1000).second,
            "书籍身份错配" to J1RecoveryContract.build(
                body, units, 4000,
                J1ProgressSnapshot(4000, 0.4f, "2026-10-06T00:00:00Z", "{}", "wrong-book"),
                expectedBookId = "correct-book",
            ).second,
        )
        for ((name, reason) in bad) {
            assertFalse("负向用例 [$name] 必须被拒绝", reason == "ok")
        }
        assertEquals("ok", J1RecoveryContract.build(body, units, 4000).second)
        writeResult(
            "j1_72_negative_invalidContractRejected",
            JSONObject().put("result", "PASS").put("cases", bad.toString()),
            binding(),
        )
    }

    @Test
    fun j1_73_negative_waitZeroAndFreezeRejected() {
        val units = listOf(
            ReadingUnitRange(0, 0, 3000),
            ReadingUnitRange(1, 3000, 3000),
            ReadingUnitRange(2, 6000, 3000),
        )
        val body = "a".repeat(3000) + J1RecoveryContract.marker(1) + "b".repeat(2996) +
            J1RecoveryContract.marker(2) + "c".repeat(2996)
        val oldSavedSnap = J1ProgressSnapshot(4553, 0.45f, "2026-10-06T02:05:02.552Z", "{\"offset\":4553}", BOOK_ID)
        val (contract, _) = J1RecoveryContract.build(body, units, 4553, oldSavedSnap, expectedBookId = BOOK_ID)
        assertNotNull(contract)

        val baselineSnap = J1ProgressSnapshot(3000, 0.30f, "2026-10-06T02:05:10.000Z", "{\"offset\":3000}", BOOK_ID)
        val dumpAfterOk = "<node text=\"${J1RecoveryContract.marker(2)}\"/>"

        // 核心反例：UI 位移但 DB 停留在旧 S 或 updated_at 未变（保存冻结）
        val freezeSnapOffsetS = J1ProgressSnapshot(4553, 0.45f, "2026-10-06T02:05:20.000Z", "{\"offset\":4553}", BOOK_ID)
        val freezeSnapSameTimestamp = J1ProgressSnapshot(7000, 0.70f, "2026-10-06T02:05:02.552Z", "{\"offset\":7000}", BOOK_ID)
        val legalNewSnap = J1ProgressSnapshot(7000, 0.70f, "2026-10-06T02:06:00.000Z", "{\"offset\":7000}", BOOK_ID)

        val newCases = linkedMapOf(
            "反例：新偏移等于旧S（典型保存冻结）" to J1RecoveryContract.newPositionAccepted(
                contract, units, 3000, baselineSnap, freezeSnapOffsetS, body, dumpAfterOk,
            ),
            "反例：updated_at未更新仍为旧保存时间（时间戳冻结）" to J1RecoveryContract.newPositionAccepted(
                contract, units, 3000, baselineSnap, freezeSnapSameTimestamp, body, dumpAfterOk,
            ),
            "反例：新偏移等于重进值R（未移动）" to J1RecoveryContract.newPositionAccepted(
                contract, units, 3000, baselineSnap,
                J1ProgressSnapshot(3000, 0.30f, "2026-10-06T02:06:00.000Z", "{\"offset\":3000}", BOOK_ID),
                body, dumpAfterOk,
            ),
            "正例：合法新位置保存且时间戳更新" to J1RecoveryContract.newPositionAccepted(
                contract, units, 3000, baselineSnap, legalNewSnap, body, dumpAfterOk,
            ),
        )

        assertFalse("新偏移等于旧S必须被拒绝", newCases.getValue("反例：新偏移等于旧S（典型保存冻结）").accepted)
        assertTrue(newCases.getValue("反例：新偏移等于旧S（典型保存冻结）").reason.contains("保存冻结"))
        assertFalse("时间戳未变必须被拒绝", newCases.getValue("反例：updated_at未更新仍为旧保存时间（时间戳冻结）").accepted)
        assertTrue(newCases.getValue("反例：updated_at未更新仍为旧保存时间（时间戳冻结）").reason.contains("保存冻结"))
        assertTrue("正例必须通过", newCases.getValue("正例：合法新位置保存且时间戳更新").accepted)

        writeResult(
            "j1_73_negative_waitZeroAndFreezeRejected",
            JSONObject().put("result", "PASS").put("cases", newCases.mapValues { it.value.reason }.toString()),
            binding(),
        )
    }

    // ── 9. 终态记录 ────────────────────────────────────────────────────────────

    @Test
    fun j1_90_recordFinalState() {
        val binding = binding()
        val db = openDb()
        try {
            val cnt = rawOne(db, "SELECT COUNT(*) FROM books WHERE id='$BOOK_ID'")?.toInt() ?: 0
            writeResult(
                "j1_90_recordFinalState",
                JSONObject().put("result", "OBSERVED").put("bookRows", cnt),
                binding,
            )
        } finally {
            db.close()
        }
    }
}
