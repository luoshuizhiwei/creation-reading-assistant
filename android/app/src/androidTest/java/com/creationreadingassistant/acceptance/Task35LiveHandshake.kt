package com.creationreadingassistant.acceptance

import android.content.Context
import android.os.Process
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Explicit driver/test rendezvous for the three live clamp UI checkpoints. */
internal object Task35LiveHandshake {
    private data class CapturedIdentity(
        val identity: Task35IdentityRecord,
        val readerMarker: String,
        val dumpSha256: String,
    )
    private const val LIVE_DIR = "task35-live"
    private val allowedPhases = setOf(
        "j1_clamp_20_reentrySettled",
        "j1_clamp_30_newPositionSaved",
        "j1_clamp_40_coldReentryVerify",
    )
    private val dumpNames = mapOf(
        "j1_clamp_20_reentrySettled" to "ui_clamp_reentry",
        "j1_clamp_30_newPositionSaved" to "ui_clamp_new_scroll",
        "j1_clamp_40_coldReentryVerify" to "ui_clamp_cold",
    )
    private val identities = mutableMapOf<String, CapturedIdentity>()

    @Synchronized
    fun awaitAndCapture(context: Context, phase: String) {
        require(phase in allowedPhases) { "live handshake phase is not allowlisted: $phase" }
        val dataRoot = File(context.getExternalFilesDir(null), "j1-accept")
        val binding = File(dataRoot, "binding.json")
        check(binding.isFile) { "live handshake requires binding.json" }
        val runId = JSONObject(binding.readText()).getString("runId")
        check(runId.matches(Regex("[A-Za-z0-9_-]+"))) { "invalid bound runId" }
        val liveDir = File(File(dataRoot, runId), LIVE_DIR).apply { mkdirs() }
        val ready = JSONObject()
            .put("runId", runId)
            .put("phase", phase)
            .put("testPid", Process.myPid())
            .put("readyAtMillis", System.currentTimeMillis())
        val readyFile = File(liveDir, "$phase.ready.json")
        val receiptFile = File(liveDir, "$phase.receipt.json")
        val readyTmp = File(liveDir, "$phase.ready.json.tmp")
        check(!readyFile.exists() && !readyTmp.exists() && !receiptFile.exists()) {
            "live handshake artifacts already exist for $phase; refusing stale reuse"
        }
        readyTmp.writeText(ready.toString())
        check(readyTmp.renameTo(readyFile)) { "cannot publish live ready file for $phase" }

        val deadline = SystemClock.elapsedRealtime() + 120_000L
        while (!receiptFile.isFile && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(100L)
        }
        check(receiptFile.isFile) { "driver did not provide live receipt for $phase" }
        val receipt = JSONObject(receiptFile.readText())
        check(receipt.getString("runId") == runId && receipt.getString("phase") == phase) {
            "live receipt run/phase mismatch for $phase"
        }
        check(!receipt.has("abortReason")) {
            "live driver aborted $phase: ${receipt.getString("abortReason")}"
        }
        val dumpRelPath = receipt.getString("dumpRelPath")
        val taskId = JSONObject(binding.readText()).getString("taskId")
        val expectedDumpPath = "$taskId/$runId/$phase/${phase}__${dumpNames.getValue(phase)}.xml"
        check(dumpRelPath == expectedDumpPath) {
            "live dump path does not match the exact bound task/run phase artifact"
        }
        val dumpFile = File(dataRoot, dumpRelPath)
        check(dumpFile.isFile) { "live receipt dump is missing: $dumpRelPath" }
        val dump = dumpFile.readText()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(dump.toByteArray())
            .joinToString("") { "%02x".format(it) }
        check(receipt.getString("dumpSha256") == digest) { "live receipt dump hash mismatch" }

        val identity = Task35Identity.capture(
            hostPackage = context.packageName,
            readerMarker = receipt.getString("readerMarker"),
            foregroundDump = dump,
            liveReceipt = receipt,
        )
        check(Task35EvidenceGate.identityBound(identity, requireReaderForeground = true).accepted) {
            "live receipt did not prove current foreground reader identity"
        }
        synchronized(identities) {
            identities[phase] = CapturedIdentity(identity, receipt.getString("readerMarker"), digest)
        }
    }

    @Synchronized
    fun consumeIdentity(phase: String, readerMarker: String?, foregroundDump: String?): Task35IdentityRecord {
        val captured = synchronized(identities) { identities.remove(phase) }
            ?: error("missing live identity captured by wrapper for $phase")
        check(!readerMarker.isNullOrBlank() && readerMarker == captured.readerMarker) {
            "method marker differs from live receipt marker for $phase"
        }
        val actualSha = MessageDigest.getInstance("SHA-256")
            .digest((foregroundDump ?: error("missing foreground dump for $phase")).toByteArray())
            .joinToString("") { "%02x".format(it) }
        check(actualSha == captured.dumpSha256) { "method dump differs from live receipt dump for $phase" }
        return captured.identity
    }
}
