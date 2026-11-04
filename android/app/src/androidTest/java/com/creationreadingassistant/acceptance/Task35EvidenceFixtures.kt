package com.creationreadingassistant.acceptance

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * CRA-CLAMP-SAF-EVIDENCE-PREP-v1 —— 任务35 专属证据夹具。
 *
 * 本文件只服务任务35（短末项完整行证据 / 真实系统 SAF 证据 / 主机端证据合同），
 * 不修改共用 [J1RecoveryContract] / [J1RealReaderFixtures]。
 *
 * 解决的两个已明确未关闭接缝：
 * 1. 短末项 `readProgressSnapshot` 只 SELECT 四列，不是完整 reading_progress 行 ——
 *    这里按**真实 schema**读回全部列，并让每个时点独立读回自己的行；
 * 2. 证据里的 `Process.myPid()` 不能单独证明「真实阅读器活跃 / 冷进程」——
 *    这里定义**设备侧身份握手**（宿主进程、前台组件、进程启动时刻、进程结束时刻），
 *    由最终唯一设备 driver 执行；本轮无设备，只实现接口与合同，**不伪造任何 PID 或运行证据**。
 */

// ══════════════════════════════════════════════════════════════════════════════
// 1. 完整进度行（按真实 schema，不用四列代替整行）
// ══════════════════════════════════════════════════════════════════════════════

/**
 * `reading_progress` 的**契约列**（与 `ReadingProgressEntity` 的 12 个属性逐一对齐）。
 * 读回时先核 `PRAGMA table_info`，缺列即失败——不靠猜测列名，也不静默少读。
 */
val TASK35_PROGRESS_COLUMNS: List<String> = listOf(
    "book_id", "progress_percent", "last_read_at", "total_reading_time_ms",
    "completion_state", "current_location_json", "payload", "revision",
    "device_id", "updated_at", "completed_at", "deleted_at",
)

/** 完整进度行 + 实际读到的列名（列名用于证明「整行」而不是子集）。 */
data class Task35ProgressRow(
    val bookId: String,
    val progressPercent: Float,
    val lastReadAt: String?,
    val totalReadingTimeMs: Long,
    val completionState: String,
    val currentLocationJson: String?,
    val payload: String?,
    val revision: Int,
    val deviceId: String?,
    val updatedAt: String,
    val completedAt: Long?,
    val deletedAt: String?,
    val readColumns: List<String>,
) {
    /** 派生偏移：只用于与既有 [J1RecoveryContract] 判定对接，不替代完整行。 */
    val legacyOffset: Int
        get() = LocatorCodec.locatorFromProgress(currentLocationJson)?.legacyOffset ?: -1

    /** 对接共用判定的最小快照（由完整行派生，不是另一次独立读取）。 */
    fun toSnapshot(): J1ProgressSnapshot =
        J1ProgressSnapshot(legacyOffset, progressPercent, updatedAt, currentLocationJson ?: "", bookId)

    fun toJson(): JSONObject = JSONObject()
        .put("book_id", bookId)
        .put("progress_percent", progressPercent.toDouble())
        .put("last_read_at", lastReadAt ?: JSONObject.NULL)
        .put("total_reading_time_ms", totalReadingTimeMs)
        .put("completion_state", completionState)
        .put("current_location_json", currentLocationJson ?: JSONObject.NULL)
        .put("payload", payload ?: JSONObject.NULL)
        .put("revision", revision)
        .put("device_id", deviceId ?: JSONObject.NULL)
        .put("updated_at", updatedAt)
        .put("completed_at", completedAt ?: JSONObject.NULL)
        .put("deleted_at", deletedAt ?: JSONObject.NULL)
        .put("read_columns", JSONArray(readColumns))
}

object Task35ProgressRows {

    fun openDb(): AppDatabase =
        Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java, AppDatabase.DB_NAME,
        ).allowMainThreadQueries().build()

    /** 实际表列名（实读 schema）。 */
    fun actualColumns(db: AppDatabase): List<String> =
        db.openHelper.writableDatabase.query("PRAGMA table_info(reading_progress)").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
        }

    /**
     * 按**真实 schema 全列**读回该书最新一行。
     *
     * @return null 表示该书没有任何进度行（不是「读到了但字段为空」）。
     * @throws IllegalStateException 实际 schema 缺少契约列 —— 明确失败，不静默少读。
     */
    fun readFull(db: AppDatabase, bookId: String): Task35ProgressRow? {
        val actual = actualColumns(db)
        val missing = TASK35_PROGRESS_COLUMNS.filterNot { it in actual }
        check(missing.isEmpty()) {
            "reading_progress 缺少契约列：$missing（实际列=$actual）—— 不得以四列代替整行"
        }
        val sql = "SELECT ${TASK35_PROGRESS_COLUMNS.joinToString(", ")} FROM reading_progress " +
            "WHERE book_id=? ORDER BY updated_at DESC LIMIT 1"
        return db.openHelper.writableDatabase.query(sql, arrayOf(bookId)).use { c ->
            if (!c.moveToFirst()) return null
            Task35ProgressRow(
                bookId = c.getString(0) ?: "",
                progressPercent = c.getFloat(1),
                lastReadAt = if (c.isNull(2)) null else c.getString(2),
                totalReadingTimeMs = c.getLong(3),
                completionState = c.getString(4) ?: "",
                currentLocationJson = if (c.isNull(5)) null else c.getString(5),
                payload = if (c.isNull(6)) null else c.getString(6),
                revision = c.getInt(7),
                deviceId = if (c.isNull(8)) null else c.getString(8),
                updatedAt = c.getString(9) ?: "",
                completedAt = if (c.isNull(10)) null else c.getLong(10),
                deletedAt = if (c.isNull(11)) null else c.getString(11),
                readColumns = TASK35_PROGRESS_COLUMNS.toList(),
            )
        }
    }

    /** 等待「完整行」满足条件；返回最后一次读到的完整行（每次轮询都是一次独立读回）。 */
    fun awaitFull(
        db: AppDatabase,
        bookId: String,
        timeoutMs: Long = 6000L,
        ready: (Task35ProgressRow?) -> Boolean,
    ): Task35ProgressRow? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var last: Task35ProgressRow? = null
        do {
            last = readFull(db, bookId)
            if (ready(last)) return last
            SystemClock.sleep(100L)
        } while (SystemClock.elapsedRealtime() < deadline)
        return last
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 2. 证据准入判定（纯函数：缺列/缺 updated_at/复用同一时点/缺身份/无真实位移一律拒绝）
// ══════════════════════════════════════════════════════════════════════════════

/** 判定结果：布尔 + 可解释原因（与 [J1Verdict] 同形，便于复用报告口径）。 */
data class Task35Verdict(val accepted: Boolean, val reason: String)

object Task35EvidenceGate {

    /** 完整行：必须整列读回、书身份一致、`updated_at` 非空。 */
    fun rowComplete(row: Task35ProgressRow?, expectedBookId: String): Task35Verdict {
        if (row == null) return Task35Verdict(false, "缺进度行（该书没有任何 reading_progress 行）")
        val missing = TASK35_PROGRESS_COLUMNS.filterNot { it in row.readColumns }
        if (missing.isNotEmpty()) {
            return Task35Verdict(false, "不是完整行：缺列 $missing")
        }
        if (row.updatedAt.isBlank()) return Task35Verdict(false, "缺 updated_at")
        if (row.bookId != expectedBookId) {
            return Task35Verdict(false, "书身份错配：行=${row.bookId} 期望=$expectedBookId")
        }
        if (row.currentLocationJson.isNullOrBlank()) return Task35Verdict(false, "缺 current_location_json")
        return Task35Verdict(true, "完整行 ok")
    }

    /**
     * 时点独立性（结构层）：每个时点必须是**独立的一次读回** —— 本时点证据时刻必须严格晚于上一时点。
     * 复用同一 snapshot 冒充多个时点（读回时刻未推进）一律拒绝。
     */
    fun evidenceMomentAdvanced(previousMillis: Long?, currentMillis: Long?): Task35Verdict {
        if (currentMillis == null || currentMillis <= 0) return Task35Verdict(false, "缺本时点证据时刻")
        if (previousMillis == null) return Task35Verdict(true, "首个时点，无需比较")
        if (currentMillis <= previousMillis) {
            return Task35Verdict(false, "证据时点复用：本时点($currentMillis) 未晚于上一时点($previousMillis)")
        }
        return Task35Verdict(true, "本时点为独立读回")
    }

    /** 非新写入时点（退出行 / 冷读行）：只要求不早于基准，不要求必须推进。 */
    fun timePointNotEarlier(previousUpdatedAt: String?, row: Task35ProgressRow?): Task35Verdict {
        if (row == null) return Task35Verdict(false, "本时点缺行")
        if (previousUpdatedAt.isNullOrBlank()) return Task35Verdict(true, "无基准")
        if (row.updatedAt < previousUpdatedAt) {
            return Task35Verdict(false, "时点倒退：cur=${row.updatedAt} prev=$previousUpdatedAt")
        }
        return Task35Verdict(true, "时点不早于基准")
    }

    /**
     * 新写入时点（S2）：本时点的行必须**严格晚于**上一时点。
     * 同一快照冒充多个时点（`updated_at` 未推进）一律拒绝。
     */
    fun timePointAdvanced(previousUpdatedAt: String?, row: Task35ProgressRow?): Task35Verdict {
        if (row == null) return Task35Verdict(false, "本时点缺行")
        if (previousUpdatedAt.isNullOrBlank()) return Task35Verdict(true, "首个时点，无需比较")
        val prev = runCatching { Instant.parse(previousUpdatedAt) }.getOrNull()
            ?: return Task35Verdict(false, "上一时点 updated_at 不可解析：$previousUpdatedAt")
        val cur = runCatching { Instant.parse(row.updatedAt) }.getOrNull()
            ?: return Task35Verdict(false, "本时点 updated_at 不可解析：${row.updatedAt}")
        if (!cur.isAfter(prev)) {
            return Task35Verdict(false, "复用时点：updated_at 未推进（prev=$previousUpdatedAt cur=${row.updatedAt}）")
        }
        return Task35Verdict(true, "时点已推进")
    }

    /**
     * 真实位移：偏移必须变化，且前后可见 marker 都必须非空并发生改变。
     * 「同一位置两遍」或「marker 缺失」一律拒绝。
     */
    fun realDisplacement(
        beforeOffset: Int,
        beforeMarker: String?,
        afterOffset: Int,
        afterMarker: String?,
    ): Task35Verdict {
        if (beforeMarker.isNullOrBlank()) return Task35Verdict(false, "位移前可见 marker 缺失")
        if (afterMarker.isNullOrBlank()) return Task35Verdict(false, "位移后可见 marker 缺失")
        if (beforeOffset == afterOffset) return Task35Verdict(false, "偏移未变化（$beforeOffset）")
        if (beforeMarker == afterMarker) return Task35Verdict(false, "可见 marker 未变化（$beforeMarker）")
        return Task35Verdict(true, "真实位移 ok（$beforeOffset→$afterOffset）")
    }

    /**
     * 身份握手：必须绑定**实际宿主进程**、**前台 reader** 与**进程启动时刻**。
     * `Process.myPid()` 单独出现不算身份证据。
     */
    fun identityBound(identity: Task35IdentityRecord?, requireReaderForeground: Boolean): Task35Verdict {
        if (identity == null) return Task35Verdict(false, "缺身份握手记录")
        if (identity.hostPackage.isBlank()) return Task35Verdict(false, "缺宿主包名")
        if (identity.hostPid.isBlank()) return Task35Verdict(false, "缺设备侧解析的宿主 pid")
        if (identity.processStartTime.isBlank()) return Task35Verdict(false, "缺进程启动时刻")
        val parsedStart = try {
            Instant.parse(identity.processStartTime).toEpochMilli()
        } catch (_: DateTimeParseException) {
            return Task35Verdict(false, "进程启动时刻不是有效 ISO-8601")
        }
        val numericStart = identity.processStartMillis
            ?: return Task35Verdict(false, "缺数值进程启动时刻")
        if (parsedStart != numericStart) return Task35Verdict(false, "ISO 与数值进程启动时刻不一致")
        if (!identity.processStartStat.startsWith("${identity.hostPid} (")) {
            return Task35Verdict(false, "进程启动 stat 不属于绑定的宿主 PID")
        }
        val startTicks = identity.processStartStat.substringAfterLast(')').trim()
            .split(Regex("\\s+")).getOrNull(19)?.toLongOrNull()
            ?: return Task35Verdict(false, "缺可解析的 /proc stat 启动 jiffies")
        if (startTicks < 0) return Task35Verdict(false, "/proc stat 启动 jiffies 为负数")
        val hz = identity.clkTck?.takeIf { it > 0 }
            ?: return Task35Verdict(false, "缺有效 CLK_TCK")
        val bootMillis = identity.processStartBootMillis
            ?: return Task35Verdict(false, "缺设备启动时刻校准值")
        if (bootMillis + startTicks * 1000L / hz != numericStart) {
            return Task35Verdict(false, "数值启动时刻与 /proc stat/CLK_TCK 校准不一致")
        }
        if (identity.foregroundComponent.isBlank()) return Task35Verdict(false, "缺前台组件")
        if (requireReaderForeground && !identity.readerForeground) {
            return Task35Verdict(false, "前台不是 reader（foreground=${identity.foregroundComponent}）")
        }
        if (identity.runId.isBlank() || identity.phase.isBlank()) return Task35Verdict(false, "缺 run/阶段身份绑定")
        if (requireReaderForeground && (identity.dumpPid != identity.hostPid || identity.dumpCapturedAtMillis == null)) return Task35Verdict(false, "缺 PID/时点 dump 绑定")
        if (identity.myPidOnly) return Task35Verdict(false, "只有 Process.myPid()，不能证明真实阅读器/冷进程")
        return Task35Verdict(true, "身份握手 ok")
    }

    /**
     * 冷进程：冷读时点的进程启动时刻必须**晚于**上一时点的证据时刻，
     * 且驱动必须交出上一进程的结束时刻。
     */
    fun coldProcess(
        identity: Task35IdentityRecord?,
        previousEvidenceMillis: Long?,
        previousProcessEndMillis: Long?,
    ): Task35Verdict {
        if (identity == null) return Task35Verdict(false, "冷读时点缺身份记录")
        if (previousEvidenceMillis == null) return Task35Verdict(false, "缺上一时点证据时刻，无法证明冷启动")
        if (previousProcessEndMillis == null) return Task35Verdict(false, "缺上一进程结束时刻")
        if (!identity.calibratedClock) return Task35Verdict(false, "系统 CLK_TCK 未校准，拒绝冷启动时序")
        val start = identity.processStartMillis
            ?: return Task35Verdict(false, "缺进程启动毫秒时刻，无法证明冷启动")
        if (start <= previousEvidenceMillis) {
            return Task35Verdict(false, "不是冷进程：启动($start) 不晚于上一时点证据($previousEvidenceMillis)")
        }
        if (previousProcessEndMillis > start) {
            return Task35Verdict(false, "进程结束时刻($previousProcessEndMillis) 晚于新进程启动($start)，时序不自洽")
        }
        return Task35Verdict(true, "冷进程 ok")
    }

    /** 导出对象内容：必须按**实际字节**核对（非空 size 不算），并命中来源/多行摘录/定位。 */
    fun exportContent(
        bytes: ByteArray?,
        expectedSha256: String?,
        requiredFragments: List<String>,
    ): Task35Verdict {
        if (bytes == null || bytes.isEmpty()) return Task35Verdict(false, "导出对象为空")
        val text = bytes.toString(Charsets.UTF_8)
        val missing = requiredFragments.filterNot { it.isNotBlank() && text.contains(it) }
        if (missing.isNotEmpty()) return Task35Verdict(false, "导出内容缺必需片段：$missing")
        if (!expectedSha256.isNullOrBlank()) {
            val actual = sha256(bytes)
            if (actual != expectedSha256) {
                return Task35Verdict(false, "导出字节 SHA 不符：actual=$actual expected=$expectedSha256")
            }
        }
        return Task35Verdict(true, "导出内容 ok（${bytes.size} 字节）")
    }

    /**
     * run 专属内容 token 的**唯一构造规则**（导出侧与清理侧必须同源）。
     *
     * 用途：删除前用实际对象字节核 token，证明该文档确由本 run 导出产生，
     * 而不是原书/其他 run 的同 URI 对象。不得另建第二套 token 规则。
     */
    fun runTokenPrefix(runId: String): String =
        "SAFRUN" + runId.replace(Regex("[^A-Za-z0-9]"), "")

    /** 多行摘录/来源/定位/高亮五类 run 专属 token（与导出侧 requiredTokens 对应）。 */
    fun requiredRunTokens(runId: String): List<String> =
        listOf("L1", "L2", "SRC", "LOC", "HL").map { runTokenPrefix(runId) + it }

    /** 交接/临时对象：必须由本 run 创建并登记；预存对象一律拒绝。 */
    fun handoffObjectAllowed(path: String, createdByThisRun: Boolean, runId: String): Task35Verdict {
        if (runId.isBlank()) return Task35Verdict(false, "缺 runId，无法证明对象归属")
        if (!createdByThisRun) return Task35Verdict(false, "预存对象拒绝：$path 不是本 run 创建")
        if (!path.contains(runId)) return Task35Verdict(false, "对象路径未绑定本 run：$path")
        return Task35Verdict(true, "对象归属 ok")
    }

    /** 创建物账：任一登记对象未按预期清理 ⇒ 不得签恢复通过。 */
    fun ledgerCleaned(entries: List<Task35LedgerEntry>): Task35Verdict {
        val dirty = entries.filter { it.existsAfterCleanup }
        if (entries.isEmpty()) return Task35Verdict(false, "创建物账为空，不能证明清理")
        if (dirty.isNotEmpty()) {
            return Task35Verdict(false, "清理失败，不得签恢复：${dirty.map { it.path }}")
        }
        return Task35Verdict(true, "创建物账已全部清理（${entries.size} 项）")
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

// ══════════════════════════════════════════════════════════════════════════════
// 3. 设备侧身份握手（真实宿主进程 / 前台 reader / 进程启动与结束时刻）
// ══════════════════════════════════════════════════════════════════════════════

/**
 * 一次身份握手记录。
 *
 * [myPidOnly] 恒为 false —— 本记录必须由**设备侧实读**（`pidof` / `/proc/<pid>/stat` /
 * `dumpsys`）填充；只有 `Process.myPid()` 时构造方必须置 true，准入判定会拒绝。
 */
data class Task35IdentityRecord(
    val hostPackage: String,
    val hostPid: String,
    val processStartTime: String,
    val processStartMillis: Long?,
    val foregroundComponent: String,
    val readerForeground: Boolean,
    val capturedAtMillis: Long,
    val myPidOnly: Boolean,
    val raw: String,
    val runId: String = "",
    val phase: String = "",
    val dumpCapturedAtMillis: Long? = null,
    val dumpPid: String = "",
    val calibratedClock: Boolean = false,
    val clkTck: Long? = null,
    val clkTckRaw: String = "",
    val processStartStat: String = "",
    val processStartBootMillis: Long? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("hostPackage", hostPackage)
        .put("hostPid", hostPid)
        .put("processStartTime", processStartTime)
        .put("processStartMillis", processStartMillis ?: JSONObject.NULL)
        .put("foregroundComponent", foregroundComponent)
        .put("readerForeground", readerForeground)
        .put("capturedAtMillis", capturedAtMillis)
        .put("myPidOnly", myPidOnly)
        .put("raw", raw)
        .put("runId", runId).put("phase", phase)
        .put("dumpCapturedAtMillis", dumpCapturedAtMillis ?: JSONObject.NULL)
        .put("dumpPid", dumpPid).put("calibratedClock", calibratedClock)
        .put("clkTck", clkTck ?: JSONObject.NULL).put("clkTckRaw", clkTckRaw)
        .put("processStartStat", processStartStat)
        .put("processStartBootMillis", processStartBootMillis ?: JSONObject.NULL)
        .put("dump", JSONObject().put("runId", runId).put("phase", phase).put("pid", dumpPid).put("capturedAtMillis", dumpCapturedAtMillis ?: JSONObject.NULL))
}

object Task35Identity {

    data class ProcessStart(val isoTime: String, val millis: Long, val stat: String, val bootMillis: Long, val clkTck: Long)

    private fun shell(cmd: String): String = runCatching {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        java.io.FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
            .also { runCatching { pfd.close() } }
    }.getOrDefault("")

    /** 前台组件（真实系统窗口焦点），不用猜测。 */
    fun foregroundComponent(): String =
        shell("dumpsys window").lines().firstOrNull { it.contains("mCurrentFocus") }?.trim() ?: ""

    /** 设备侧解析的宿主 pid（`pidof`），与 `Process.myPid()` 相互独立。 */
    fun devicePid(pkg: String): String = shell("pidof $pkg").trim()

    /** 进程启动时刻只由设备 /proc stat 第 22 字段与 CLK_TCK 推导。 */
    fun processStart(pid: String): ProcessStart {
        require(pid.matches(Regex("[0-9]+"))) { "device PID is not numeric: $pid" }
        val stat = shell("cat /proc/$pid/stat").trim()
        require(stat.startsWith("$pid ") && stat.contains(')')) { "unreadable /proc/$pid/stat" }
        val ticks = stat.substringAfterLast(')').trim().split(Regex("\\s+")).getOrNull(19)?.toLongOrNull()
            ?: error("/proc/$pid/stat has no numeric starttime field")
        val hz = android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK)
        require(hz > 0) { "sysconf(_SC_CLK_TCK) returned $hz" }
        val bootMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        require(bootMillis > 0) { "device boot-time calibration is not positive" }
        val startMillis = bootMillis + ticks * 1000L / hz
        require(startMillis > 0 && startMillis <= System.currentTimeMillis()) { "computed process start is outside device time" }
        return ProcessStart(Instant.ofEpochMilli(startMillis).toString(), startMillis, stat, bootMillis, hz)
    }

    /**
     * 采集一次身份握手。
     *
     * @param readerMarker 期望在**前台窗口内容**里出现的 reader 正文标记（如 CRACLAMP0001）；
     *                     前台组件必须是宿主应用且该标记可见，才算「前台 reader」。
     * @param foregroundDump 驱动/测试在该时点抓取的前台窗口 dump 文本。
     */
    fun capture(
        hostPackage: String,
        readerMarker: String?,
        foregroundDump: String?,
        liveReceipt: JSONObject? = null,
    ): Task35IdentityRecord {
        val fg = foregroundComponent()
        val pid = devicePid(hostPackage)
        val processStart = if (pid.isNotBlank()) processStart(pid) else null
        val args = InstrumentationRegistry.getArguments()
        val run = liveReceipt?.optString("runId") ?: args.getString("task35RunId") ?: ""
        val phase = liveReceipt?.optString("phase") ?: args.getString("task35Phase") ?: ""
        val stamp = if (liveReceipt != null) {
            liveReceipt.optLong("capturedAtMillis", -1L).takeIf { it > 0 }
        } else {
            args.getString("task35DumpCapturedAtMillis")?.toLongOrNull()
        }
        val dumpPid = liveReceipt?.optString("dumpPid") ?: args.getString("task35DumpPid") ?: ""
        val dumpSha = foregroundDump?.toByteArray()?.let { Task35EvidenceGate.sha256(it) } ?: ""
        val dumpBound = if (liveReceipt != null) {
            val currentMillis = System.currentTimeMillis()
            liveReceipt.optString("hostPackage") == hostPackage &&
                liveReceipt.optString("dumpRunId") == run && liveReceipt.optString("dumpPhase") == phase &&
                dumpPid == pid && liveReceipt.optString("hostPid") == pid && stamp != null &&
                stamp <= currentMillis && currentMillis - stamp <= 5000L &&
                liveReceipt.optString("dumpSha256") == dumpSha &&
                liveReceipt.optString("foregroundComponent") == fg &&
                liveReceipt.optBoolean("foregroundReader") && fg.contains(hostPackage) &&
                !readerMarker.isNullOrBlank() && readerMarker == liveReceipt.optString("readerMarker") &&
                foregroundDump?.contains(readerMarker) == true
        } else {
            run.isNotBlank() && phase.isNotBlank() &&
                args.getString("task35DumpRunId") == run && args.getString("task35DumpPhase") == phase &&
                dumpPid == pid && stamp != null && stamp <= System.currentTimeMillis() &&
                System.currentTimeMillis() - stamp <= 5000L &&
                args.getString("task35DumpSha256") == dumpSha
        }
        val readerForeground = dumpBound && fg.contains(hostPackage) &&
            !readerMarker.isNullOrBlank() &&
            foregroundDump?.contains(readerMarker) == true
        val raw = "focus=$fg\npid=$pid\nprocessStartStat=${processStart?.stat.orEmpty()}"
        return Task35IdentityRecord(
            hostPackage = hostPackage,
            hostPid = pid,
            processStartTime = processStart?.isoTime.orEmpty(),
            processStartMillis = processStart?.millis,
            foregroundComponent = fg,
            readerForeground = readerForeground,
            capturedAtMillis = System.currentTimeMillis(),
            // 设备侧 pid 解析失败 ⇒ 只有 myPid 可用 ⇒ 明确标记为不可作为身份证据
            myPidOnly = pid.isBlank(),
            raw = raw, runId = run, phase = phase, dumpCapturedAtMillis = stamp,
            dumpPid = dumpPid, calibratedClock = processStart != null,
            clkTck = processStart?.clkTck,
            clkTckRaw = "Os.sysconf(_SC_CLK_TCK)=${processStart?.clkTck}",
            processStartStat = processStart?.stat.orEmpty(),
            processStartBootMillis = processStart?.bootMillis,
        )
    }

    /**
     * 驱动必须提供的**进程结束时刻**（上一进程在冷读之前被杀死的时点）。
     * 由 driver 写入 `resultsDir/task35_process_end.json`；测试只读不造。
     */
    fun readProcessEndMillis(resultsDir: File): Long? {
        val args = InstrumentationRegistry.getArguments()
        val run = args.getString("task35RunId") ?: return null
        val phase = args.getString("task35Phase") ?: return null
        return readProcessEndMillis(resultsDir, run, phase)
    }

    fun readProcessEndMillis(resultsDir: File, expectedRunId: String, expectedPhase: String): Long? {
        val f = File(resultsDir, "task35_process_end.json")
        if (!f.isFile) return null
        val doc = JSONObject(f.readText())
        val previous = File(resultsDir, doc.optString("previousStep") + ".json")
        if (!previous.isFile) return null
        val prior = JSONObject(previous.readText())
        val priorIdentity = prior.optJSONObject("identity") ?: return null
        val forceStop = doc.optJSONObject("forceStopReceipt") ?: return null
        val forceCommand = forceStop.optJSONArray("command") ?: return null
        val samples = doc.optJSONArray("pidSamples") ?: return null
        if (samples.length() == 0) return null
        val priorPackage = priorIdentity.optString("hostPackage")
        val serial = forceCommand.optString(2)
        if (priorPackage.isBlank() || !priorIdentity.optString("hostPid").matches(Regex("[0-9]+")) ||
            forceCommand.length() != 7 || forceCommand.optString(0).isBlank() ||
            forceCommand.optString(1) != "-s" || serial.isBlank() ||
            forceCommand.optString(3) != "shell" || forceCommand.optString(4) != "am" ||
            forceCommand.optString(5) != "force-stop" || forceCommand.optString(6) != priorPackage ||
            forceStop.optInt("returncode", -1) != 0) return null
        val forceOut = android.util.Base64.decode(forceStop.optString("stdoutBase64"), android.util.Base64.DEFAULT)
        val forceErr = android.util.Base64.decode(forceStop.optString("stderrBase64"), android.util.Base64.DEFAULT)
        if (String(forceOut, Charsets.UTF_8) != forceStop.optString("stdout") ||
            String(forceErr, Charsets.UTF_8) != forceStop.optString("stderr")) return null
        val forceAt = forceStop.optLong("completedAtDeviceMillis", -1L)
        if (forceAt <= 0) return null
        var previousSampleAt = forceAt
        var finalAbsent = false
        var finalAt = -1L
        for (index in 0 until samples.length()) {
            val sample = samples.optJSONObject(index) ?: return null
            val command = sample.optJSONArray("command") ?: return null
            if (command.length() != 6 || command.optString(0) != forceCommand.optString(0) ||
                command.optString(1) != "-s" || command.optString(2) != serial ||
                command.optString(3) != "shell" || command.optString(4) != "pidof" ||
                command.optString(5) != priorPackage) return null
            val stdout = android.util.Base64.decode(sample.optString("stdoutBase64"), android.util.Base64.DEFAULT)
            val stderr = android.util.Base64.decode(sample.optString("stderrBase64"), android.util.Base64.DEFAULT)
            if (String(stdout, Charsets.UTF_8) != sample.optString("stdout") ||
                String(stderr, Charsets.UTF_8) != sample.optString("stderr")) return null
            val rc = sample.optInt("returncode", -1)
            val sampledAt = sample.optLong("sampledAtDeviceMillis", -1L)
            if (sampledAt <= previousSampleAt) return null
            previousSampleAt = sampledAt
            finalAbsent = rc == 1 && stdout.isEmpty() && stderr.isEmpty()
            if (index < samples.length() - 1) {
                if (rc != 0 || stdout.isEmpty()) return null
            }
            finalAt = sampledAt
            if (index < samples.length() - 1 && finalAbsent) return null
        }
        if (!finalAbsent) return null
        val ended = doc.optLong("processEndMillis", -1L)
        val captured = doc.optLong("capturedAtMillis", -1L)
        if (doc.optString("runId") != expectedRunId || doc.optString("phase") != expectedPhase ||
            prior.optString("runId") != expectedRunId || doc.optString("previousPid") != priorIdentity.optString("hostPid") ||
            doc.optBoolean("pidAbsent") != finalAbsent || ended != finalAt || ended <= prior.optLong("timestampMillis") ||
            forceAt < prior.optLong("timestampMillis") ||
            captured < ended || captured > System.currentTimeMillis() || System.currentTimeMillis() - captured > 60000L) return null
        return ended
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 4. 创建物登记（dump / 交接文件 / 导出对象 / 临时对象）
// ══════════════════════════════════════════════════════════════════════════════

data class Task35LedgerEntry(
    val kind: String,
    val path: String,
    val sha256: String?,
    val bytes: Long,
    val createdByThisRun: Boolean,
    val existsAfterCleanup: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind).put("path", path)
        .put("sha256", sha256 ?: JSONObject.NULL)
        .put("bytes", bytes)
        .put("createdByThisRun", createdByThisRun)
        .put("existsAfterCleanup", existsAfterCleanup)
}

/** 本 run 的创建物账：登记 + 清理核验；清理失败不得签恢复通过。 */
class Task35CreationLedger(val runId: String) {

    private val entries = mutableListOf<Task35LedgerEntry>()
    private val createdPaths = mutableSetOf<String>()

    /** 登记一个由本 run 创建的对象。路径必须绑定 runId。 */
    fun register(kind: String, path: String, file: File?) {
        val exists = file?.isFile == true && (file?.length() ?: 0L) > 0L
        val bytes = file?.takeIf { it.isFile && it.length() > 0L }?.readBytes()
        createdPaths.add(path)
        entries += Task35LedgerEntry(
            kind = kind,
            path = path,
            sha256 = bytes?.let { Task35EvidenceGate.sha256(it) },
            bytes = bytes?.size?.toLong() ?: -1L,
            createdByThisRun = exists && path.contains(runId),
            existsAfterCleanup = exists,
        )
    }

    /** Actual callback URI, resolver bytes or a newly granted permission; no path guessing. */
    fun registerObserved(kind: String, uri: String, bytes: ByteArray?, created: Boolean) {
        if (created) createdPaths.add(uri)
        entries += Task35LedgerEntry(kind, uri, bytes?.let { Task35EvidenceGate.sha256(it) },
            bytes?.size?.toLong() ?: -1L, created, true)
    }

    /** 该对象是否由本 run 创建（预存对象必须为 false）。 */
    fun createdByThisRun(path: String): Boolean = path in createdPaths

    /** 清理后核验：把每个登记对象的「清理后是否仍存在」写回。 */
    fun markCleanup(probe: (String) -> Boolean) {
        for (i in entries.indices) {
            entries[i] = entries[i].copy(existsAfterCleanup = probe(entries[i].path))
        }
    }

    fun entries(): List<Task35LedgerEntry> = entries.toList()

    fun addRawEntry(entry: Task35LedgerEntry) {
        entries += entry
        if (entry.createdByThisRun) {
            createdPaths.add(entry.path)
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("runId", runId)
        .put("entries", JSONArray().apply { entries.forEach { put(it.toJson()) } })

    companion object {
        fun fromFile(file: File): Task35CreationLedger {
            val obj = JSONObject(file.readText())
            val runId = obj.getString("runId")
            val ledger = Task35CreationLedger(runId)
            val arr = obj.optJSONArray("entries") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val kind = item.getString("kind")
                val path = item.getString("path")
                val sha = if (item.isNull("sha256")) null else item.getString("sha256")
                val bytes = item.optLong("bytes", -1L)
                val created = item.optBoolean("createdByThisRun", false)
                val existsAfter = item.optBoolean("existsAfterCleanup", true)
                ledger.addRawEntry(Task35LedgerEntry(kind, path, sha, bytes, created, existsAfter))
            }
            return ledger
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// 5. SAF 授权基线与按 run 清理恢复契约
// ══════════════════════════════════════════════════════════════════════════════

/** 单项持久 URI 授权快照条目（不记录私人书名等无关信息）。 */
data class Task35PreGrantEntry(
    val uri: String,
    val isRead: Boolean,
    val isWrite: Boolean,
    val persistedTime: Long = 0L,
) : Comparable<Task35PreGrantEntry> {
    fun toJson(): JSONObject = JSONObject()
        .put("uri", uri)
        .put("isRead", isRead)
        .put("isWrite", isWrite)
        .put("persistedTime", persistedTime)

    /**
     * 规范化权限状态表示，包含完整 (uri, isRead, isWrite) 语义。
     * 防止仅比对 URI 字符串而漏测读写权限状态漂移。
     */
    fun toPermissionKey(): String = "$uri [read=$isRead, write=$isWrite]"

    override fun compareTo(other: Task35PreGrantEntry): Int {
        val uriCmp = this.uri.compareTo(other.uri)
        if (uriCmp != 0) return uriCmp
        val readCmp = this.isRead.compareTo(other.isRead)
        if (readCmp != 0) return readCmp
        return this.isWrite.compareTo(other.isWrite)
    }

    companion object {
        fun fromJson(obj: JSONObject): Task35PreGrantEntry = Task35PreGrantEntry(
            uri = obj.getString("uri"),
            isRead = obj.optBoolean("isRead", false),
            isWrite = obj.optBoolean("isWrite", false),
            persistedTime = obj.optLong("persistedTime", 0L),
        )
    }
}

/** 本 run 开始前（创建文档/取得新授权前）的持久 URI 授权基线快照。 */
data class Task35PreGrantsSnapshot(
    val runId: String,
    val capturedAtMillis: Long,
    val grants: List<Task35PreGrantEntry>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("runId", runId)
        .put("capturedAtMillis", capturedAtMillis)
        .put("grants", JSONArray().apply { grants.sorted().forEach { put(it.toJson()) } })

    fun uriSet(): Set<String> = grants.map { it.uri }.toSet()

    /** 返回包含完整读写状态的已排序列表，用于基线与恢复后比对 */
    fun permissionKeyList(): List<String> = grants.map { it.toPermissionKey() }.sorted()

    fun containsUri(uri: String): Boolean = grants.any { it.uri == uri }

    /**
     * 将快照原子落盘。
     * 防覆写保护：若文件已存在，绝不允许覆盖，抛出 IllegalStateException，保证 pre-run 基线不可篡改。
     */
    fun writeTo(dir: File): File {
        dir.mkdirs()
        val f = File(dir, "saf-$runId-pre-grants.json")
        if (f.exists()) {
            throw IllegalStateException("授权基线快照已存在，禁止覆写: ${f.absolutePath}")
        }
        f.writeText(toJson().toString(2))
        return f
    }

    companion object {
        fun capture(
            resolver: android.content.ContentResolver,
            runId: String,
            grantInspector: ((android.content.ContentResolver) -> List<Task35PreGrantEntry>)? = null,
        ): Task35PreGrantsSnapshot {
            val list = if (grantInspector != null) {
                grantInspector(resolver)
            } else {
                resolver.persistedUriPermissions.map {
                    Task35PreGrantEntry(
                        uri = it.uri.toString(),
                        isRead = it.isReadPermission,
                        isWrite = it.isWritePermission,
                        persistedTime = it.persistedTime,
                    )
                }
            }
            return Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), list.sorted())
        }

        fun fromFile(file: File): Task35PreGrantsSnapshot {
            val obj = JSONObject(file.readText())
            val runId = obj.getString("runId")
            val captured = obj.optLong("capturedAtMillis", 0L)
            val arr = obj.optJSONArray("grants") ?: JSONArray()
            val list = (0 until arr.length()).map { i ->
                Task35PreGrantEntry.fromJson(arr.getJSONObject(i))
            }
            return Task35PreGrantsSnapshot(runId, captured, list.sorted())
        }
    }
}

/**
 * 结构化恢复记录：完全匹配 evidence-contracts.json / evidence_contract_check.py 的 __restoration 契约。
 */
data class Task35RestorationRecord(
    val runId: String,
    val grantsBefore: List<String>,
    val grantsAfter: List<String>,
    val observedUriAbsent: Boolean,
    val documentDeleted: Boolean,
    val permissionReleased: Boolean,
    val deletedUri: String?,
    val failureReason: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("runId", runId)
        .put("grantsBefore", JSONArray(grantsBefore.sorted()))
        .put("grantsAfter", JSONArray(grantsAfter.sorted()))
        .put("observedUriAbsent", observedUriAbsent)
        .put("documentDeleted", documentDeleted)
        .put("permissionReleased", permissionReleased)
        .put("deletedUri", deletedUri ?: JSONObject.NULL)
        .put("failureReason", failureReason ?: JSONObject.NULL)
}

/**
 * 按 run 绑定的真实 SAF 清理与恢复执行器。
 */
object Task35SafCleaner {

    const val DUMP_DIR_NAME = "task35-saf-dumps"

    internal fun confirmsIndependentMissingFile(uri: Uri, output: String): Boolean {
        if (output.contains("SecurityException") || output.contains("Permission Denial")) return false
        val documentId = DocumentsContract.getDocumentId(uri)
        if (documentId.isBlank()) return false
        return output.lineSequence().any { line ->
            val prefix = "java.io.FileNotFoundException: Missing file for $documentId at "
            line.trimStart().startsWith(prefix) && line.trimStart().removePrefix(prefix).isNotBlank()
        }
    }

    /** 导出对象在账本中的**必需条目类型**；同 URI 的权限条目不得充当导出对象证明。 */
    const val KIND_EXPORT_URI = "export_uri"

    private fun fetchCurrentGrants(
        resolver: android.content.ContentResolver,
        inspector: ((android.content.ContentResolver) -> List<Task35PreGrantEntry>)?,
    ): List<Task35PreGrantEntry> {
        return if (inspector != null) {
            inspector(resolver).sorted()
        } else {
            resolver.persistedUriPermissions.map {
                Task35PreGrantEntry(
                    uri = it.uri.toString(),
                    isRead = it.isReadPermission,
                    isWrite = it.isWritePermission,
                    persistedTime = it.persistedTime,
                )
            }.sorted()
        }
    }

    /**
     * 执行按 run 清理与恢复。
     *
     * 顺序铁律：
     * 1. 严格授权基线校验（快照存在且 runId 匹配，提取完整读写状态基线）；
     * 2. 严格账本校验（ledger 必须存在且 runId 匹配，缺账本则 0 删除 0 释放并中止）；
     * 3. 严格所有权与内容一致性确认（handoff runId 匹配、URI 在 ledger 登记、内容指纹/size 相符、且不在 preGrants 中）；
     * 4. 在仍持有必要写权限时删除文档对象；
     * 5. 严格探测 absence（严禁将权限释放后的 SecurityException 误当 absence，只有 FileNotFoundException 才是真实不存在）；
     * 6. 精确释放本 run 的持久 READ 授权；
     * 7. 回读 persistedUriPermissions，严格比对 grantsAfter 与 grantsBefore（含读写权限标志）；
     * 8. 成功时清理 dump 临时目录与 handoff；失败时严格保留 handoff 与 dump 供重试与排查；
     * 9. markCleanup 回填 ledger 并产出 __restoration 结果文件。
     */
    fun performCleanup(
        context: Context,
        runId: String,
        evidenceDir: File,
        documentDeleter: ((Uri) -> Boolean)? = null,
        permissionReleaser: ((Uri, Int) -> Unit)? = null,
        grantInspector: ((android.content.ContentResolver) -> List<Task35PreGrantEntry>)? = null,
        streamOpener: ((Uri) -> java.io.InputStream?)? = null,
        independentAbsenceObserver: ((Uri) -> String)? = null,
    ): Task35RestorationRecord {
        val resolver = context.contentResolver
        val curGrants = fetchCurrentGrants(resolver, grantInspector)
        val curGrantsList = curGrants.map { it.toPermissionKey() }.sorted()

        // 1. 读取基线授权快照并校验 runId
        val preGrantsFile = File(evidenceDir, "saf-$runId-pre-grants.json")
        if (!preGrantsFile.isFile) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = emptyList(),
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = null,
                failureReason = "缺授权基线快照 saf-$runId-pre-grants.json，无法核验恢复前后一致性",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }
        val preGrantsSnapshot = Task35PreGrantsSnapshot.fromFile(preGrantsFile)
        if (preGrantsSnapshot.runId != runId) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = emptyList(),
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = null,
                failureReason = "基线快照 runId 不匹配 (${preGrantsSnapshot.runId} != $runId)，拒绝跨 run 清理",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }
        val grantsBeforeList = preGrantsSnapshot.permissionKeyList()

        // 2. 读取并严格校验创建账本（缺账本直接拒绝，禁止新建空账）
        val ledgerFile = File(evidenceDir, "task35-ledger-$runId.json")
        if (!ledgerFile.isFile) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = null,
                failureReason = "缺创建账本 task35-ledger-$runId.json，缺少对象创建证明，停止清理",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }
        val ledger = Task35CreationLedger.fromFile(ledgerFile)
        if (ledger.runId != runId) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = null,
                failureReason = "账本 runId 不匹配 (${ledger.runId} != $runId)，拒绝跨 run 清理",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }

        // 3. 读取交接文件并校验所有权与指纹一致性
        val handoffFile = File(evidenceDir, "saf-$runId-handoff.json")
        if (!handoffFile.isFile) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = null,
                failureReason = "缺交接文件 saf-$runId-handoff.json，无 run 绑定对象所有权证据，停止清理",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }
        val handoffDoc = JSONObject(handoffFile.readText())
        val handoffRunId = handoffDoc.optString("runId")
        if (handoffRunId != runId) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = null,
                failureReason = "handoff runId 不匹配 ($handoffRunId != $runId)，拒绝跨 run 清理",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }
        val rawUriStr = handoffDoc.optString("uri")
        val targetUri = runCatching { Uri.parse(rawUriStr) }.getOrNull()
        if (targetUri == null || rawUriStr.isBlank()) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = null,
                failureReason = "handoff 中的 URI 非法：$rawUriStr",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }
        val targetGrantObservedInitially = curGrants.any { it.uri == rawUriStr } && !preGrantsSnapshot.containsUri(rawUriStr)

        // 检查 targetUri 是否在测试前基线中已存在
        if (preGrantsSnapshot.containsUri(rawUriStr)) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = rawUriStr,
                failureReason = "目标 URI 在测试前已存在于基线授权中，不得当作本轮新建释放！",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }

        // 账本中必须存在 **export_uri 类型** 的条目：
        // 同 URI 的 uri_read_permission 条目只证明"取过读授权"，不构成导出对象证明。
        val ledgerUriEntry = ledger.entries().find { it.path == rawUriStr && it.kind == KIND_EXPORT_URI }
        if (ledgerUriEntry == null) {
            val sameUriOtherKind = ledger.entries().firstOrNull { it.path == rawUriStr }
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = rawUriStr,
                failureReason = if (sameUriOtherKind != null) {
                    "账本中该 URI 只有 ${sameUriOtherKind.kind} 条目，不得当作导出对象证明（要求 $KIND_EXPORT_URI）"
                } else {
                    "账本中未登记该目标 URI ($rawUriStr)，缺少所有权归属证明，拒绝清理"
                },
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }

        // 必需凭证完整性：任一缺失即拒绝，不得静默跳过比较。
        val missingCredential = mutableListOf<String>()
        if (!handoffDoc.has("fileSha256") || handoffDoc.optString("fileSha256").isBlank()) {
            missingCredential += "handoff.fileSha256"
        }
        if (!handoffDoc.has("fileSize") || handoffDoc.optLong("fileSize", -1L) < 0L) {
            missingCredential += "handoff.fileSize"
        }
        if (ledgerUriEntry.sha256.isNullOrBlank()) missingCredential += "ledger.sha256"
        if (ledgerUriEntry.bytes < 0L) missingCredential += "ledger.bytes"
        if (!ledgerUriEntry.createdByThisRun) missingCredential += "ledger.createdByThisRun"
        if (missingCredential.isNotEmpty()) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = rawUriStr,
                failureReason = "待删对象凭证不完整（缺 ${missingCredential.joinToString()}），不得跳过归属核验，拒绝清理",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }

        // 声明一致性（两份自报记录相同**不等于**对象归属，仅为前置条件）
        if (handoffDoc.optString("fileSha256") != ledgerUriEntry.sha256) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = rawUriStr,
                failureReason = "handoff fileSha256 与账本登记指纹不符，拒绝清理",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }
        if (handoffDoc.optLong("fileSize", -1L) != ledgerUriEntry.bytes) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = rawUriStr,
                failureReason = "handoff fileSize 与账本登记大小不符，拒绝清理",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }

        // ── 删除前**实际对象归属核验**：仍有读取权限时经 resolver 读取字节，实算 SHA/size/run token ──
        val actualBytes = try {
            val stream = if (streamOpener != null) streamOpener(targetUri) else resolver.openInputStream(targetUri)
            if (stream == null) {
                null
            } else {
                stream.use { it.readBytes() }
            }
        } catch (e: java.io.FileNotFoundException) {
            null
        } catch (e: SecurityException) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = rawUriStr,
                failureReason = "删除前无法读取目标对象（SecurityException），归属 NOT_PROVEN，拒绝清理",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        } catch (e: Throwable) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = rawUriStr,
                failureReason = "删除前读取目标对象异常（${e.javaClass.simpleName}），归属 NOT_PROVEN，拒绝清理",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }

        val ownership = Task35EvidenceGate.exportContent(
            bytes = actualBytes,
            expectedSha256 = handoffDoc.optString("fileSha256"),
            requiredFragments = Task35EvidenceGate.requiredRunTokens(runId),
        )
        if (!ownership.accepted) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = rawUriStr,
                failureReason = "删除前实际对象归属核验失败：${ownership.reason}（零删除零释放，保留凭证）",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }
        if (actualBytes == null || actualBytes.size.toLong() != ledgerUriEntry.bytes) {
            val rec = Task35RestorationRecord(
                runId = runId,
                grantsBefore = grantsBeforeList,
                grantsAfter = curGrantsList,
                observedUriAbsent = false,
                documentDeleted = false,
                permissionReleased = false,
                deletedUri = rawUriStr,
                failureReason = "删除前实际对象大小与账本登记不符，拒绝清理",
            )
            File(evidenceDir, "task35-restoration-$runId.json").writeText(rec.toJson().toString(2))
            return rec
        }

        // 检查账本中是否登记了该 URI（旧路径保留兼容：上面已按 export_uri 精确校验）
        // 4. 删除文档（必须在仍持有写权限时调用，再释放本 run 取得的授权）
        var documentDeleted = false
        var deleteError: String? = null
        try {
            if (documentDeleter != null) {
                documentDeleted = documentDeleter(targetUri)
            } else {
                documentDeleted = DocumentsContract.deleteDocument(resolver, targetUri)
            }
        } catch (e: Exception) {
            deleteError = "deleteDocument 异常: ${e.javaClass.simpleName}: ${e.message}"
            documentDeleted = false
        }

        // 5. 探测文档 absence
        // 铁律：权限不足（SecurityException）或任意未知异常绝不代表文档已不存在！
        // 只有真实删除成功且明确捕获 FileNotFoundException，才能认定为物理不存在。
        var observedUriAbsent = false
        var absenceReason: String? = null
        if (documentDeleted) {
            var streamOpened = false
            try {
                val stream = if (streamOpener != null) {
                    streamOpener(targetUri)
                } else {
                    resolver.openInputStream(targetUri)
                }
                stream?.use {
                    streamOpened = true
                }
            } catch (e: java.io.FileNotFoundException) {
                observedUriAbsent = true
            } catch (e: SecurityException) {
                observedUriAbsent = false
                absenceReason = "openInputStream 权限拒绝 (SecurityException)，文档物理 absence 保持 NOT_PROVEN"
                if (independentAbsenceObserver != null) {
                    try {
                        val output = independentAbsenceObserver(targetUri)
                        if (confirmsIndependentMissingFile(targetUri, output)) {
                            observedUriAbsent = true
                            absenceReason = null
                        } else {
                            absenceReason += "；独立观察未返回匹配的 Missing file 证据"
                        }
                    } catch (observerError: Exception) {
                        absenceReason += "；独立观察失败 (${observerError.javaClass.simpleName})"
                    }
                }
            } catch (e: Throwable) {
                observedUriAbsent = false
                absenceReason = "openInputStream 异常 (${e.javaClass.simpleName})，文档 absence 保持 NOT_PROVEN"
            }
            if (streamOpened) {
                observedUriAbsent = false
                deleteError = (deleteError?.let { "$it; " } ?: "") + "deleteDocument 返回成功但仍能打开文档流"
            }
        } else {
            observedUriAbsent = false
            if (deleteError == null) {
                deleteError = "provider 不支持删除或删除返回 false"
            }
        }

        // 6. 释放本 run 取得的持久 READ 授权
        var permissionReleased = false
        var releaseError: String? = null
        val grantsBeforeRelease = fetchCurrentGrants(resolver, grantInspector).map { it.toPermissionKey() }.sorted()
        if (documentDeleted && observedUriAbsent && targetGrantObservedInitially && grantsBeforeRelease == grantsBeforeList) {
            permissionReleased = true
            releaseError = null
        } else {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (permissionReleaser != null) {
                    permissionReleaser(targetUri, flags)
                    permissionReleased = true
                } else {
                    resolver.releasePersistableUriPermission(targetUri, flags)
                    permissionReleased = true
                }
            } catch (e: Exception) {
                releaseError = "releasePersistableUriPermission 异常: ${e.javaClass.simpleName}: ${e.message}"
                permissionReleased = false
            }
        }

        // 7. 重读 persistedUriPermissions，核验 grantsAfter == grantsBefore（含读写权限标志）
        val afterGrants = fetchCurrentGrants(resolver, grantInspector)
        val grantsAfterList = afterGrants.map { it.toPermissionKey() }.sorted()
        val stillHasTarget = afterGrants.any { it.uri == rawUriStr }
        if (stillHasTarget) {
            permissionReleased = false
            releaseError = (releaseError?.let { "$it; " } ?: "") + "授权释放后 URI 仍留在 persistedUriPermissions"
        }

        val grantsMatch = (grantsAfterList == grantsBeforeList)
        val dumpRoot = File(context.getExternalFilesDir(null), DUMP_DIR_NAME)
        val dumpRootPath = dumpRoot.canonicalPath

        // 8. 成功时清理专属 dump 临时目录与 handoff 文件；失败时严格保留供取证排查
        val isAllSuccess = documentDeleted && observedUriAbsent && permissionReleased && !stillHasTarget && grantsMatch
        var dumpCleanupOk = true
        var handoffCleanupOk = true
        var cleanupEvidenceError: String? = null

        if (isAllSuccess) {
            if (runId.matches(Regex("[A-Za-z0-9_-]+"))) {
                val dumpDir = File(dumpRoot, runId)
                if (dumpDir.isDirectory && dumpDir.canonicalPath.startsWith(dumpRootPath + File.separator)) {
                    val allFilesDeleted = dumpDir.listFiles()?.all { it.delete() } ?: true
                    val dirDeleted = dumpDir.delete()
                    if (!allFilesDeleted || !dirDeleted) {
                        dumpCleanupOk = false
                        cleanupEvidenceError = "dump 临时目录删除未完全成功"
                    }
                }
            }
            if (handoffFile.isFile) {
                if (!handoffFile.delete()) {
                    handoffCleanupOk = false
                    cleanupEvidenceError = (cleanupEvidenceError?.let { "$it; " } ?: "") + "handoff 文件删除失败"
                }
            }
        } else {
            // 失败时严格保留 handoff 与 dump 文件，保留重试与排查依据
        }

        // 9. markCleanup 回填 ledger
        ledger.markCleanup { path ->
            when {
                path == rawUriStr -> !observedUriAbsent
                path == handoffFile.absolutePath -> handoffFile.exists()
                path == dumpRootPath || path.startsWith(dumpRootPath + File.separator) -> File(path).exists()
                else -> File(path).exists()
            }
        }
        ledgerFile.writeText(ledger.toJson().toString(2))

        val finalError = listOfNotNull(
            deleteError,
            absenceReason,
            releaseError,
            if (!grantsMatch) "grantsAfter 与 pre-run 基线不符: ($grantsAfterList != $grantsBeforeList)" else null,
            cleanupEvidenceError,
        ).joinToString("; ").takeIf { it.isNotBlank() }

        val restoration = Task35RestorationRecord(
            runId = runId,
            grantsBefore = grantsBeforeList,
            grantsAfter = grantsAfterList,
            observedUriAbsent = observedUriAbsent && grantsMatch,
            documentDeleted = documentDeleted,
            permissionReleased = permissionReleased && !stillHasTarget,
            deletedUri = rawUriStr,
            failureReason = finalError,
        )

        val restorationFile = File(evidenceDir, "task35-restoration-$runId.json")
        restorationFile.writeText(restoration.toJson().toString(2))

        return restoration
    }
}

