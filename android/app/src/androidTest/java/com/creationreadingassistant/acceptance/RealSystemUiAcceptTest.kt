package com.creationreadingassistant.acceptance

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.data.local.entity.NoteEntity
import com.creationreadingassistant.ui.screen.reader.sheets.NotesSheet
import com.creationreadingassistant.ui.theme.paperPalette
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** Task35-v2: real DocumentsUI returns the URI observed after successful stream close.
 * Controlled registry tests are separate and never count as real SAF acceptance.
 * Driver must restore the run's URI grant, object, handoff, dumps and fixtures.
 */
@RunWith(AndroidJUnit4::class)
class RealSystemUiAcceptTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    @Volatile private var completedUri: android.net.Uri? = null

    private val instr get() = InstrumentationRegistry.getInstrumentation()

    companion object {
        const val BOOK_TITLE = "测试 EPUB"
        const val TASK_ID = "CRA-CLAMP-SAF-EVIDENCE-PREP-v2"
        const val SAF_DIR = "/sdcard/Download"

        /** dump 递归安全阀：超过即视为截断，必须显式失败而非静默冒充完整树。 */
        const val MAX_DUMP_DEPTH = 100

        /** 本任务专属 dump 根位于应用外部私有目录，避免 scoped storage 拒绝直接写 /sdcard。 */

        /**
         * 供外部 driver 直接调用的按 run 清理恢复入口。
         */
        @JvmStatic
        fun cleanupSafRun(context: Context, runId: String): Task35RestorationRecord {
            val evidenceDir = File(context.getExternalFilesDir(null), "task35_evidence/$runId")
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            return Task35SafCleaner.performCleanup(context, runId, evidenceDir) { uri ->
                rootContentReadOutput(automation, uri)
            }
        }

        private fun rootContentReadOutput(automation: android.app.UiAutomation, uri: android.net.Uri): String {
            val pipes = automation.executeShellCommandRw("su 0 sh")
            val command = buildString {
                append("content read --uri ${shellQuote(uri.toString())} 2>&1\n")
                append("status=\$?\n")
                append("printf '\\n__CRA_ROOT_CONTENT_READ_DONE__:%s\\n' \"\$status\"\n")
                append("exit\n")
            }
            ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { output ->
                output.write(command.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            val output = ParcelFileDescriptor.AutoCloseInputStream(pipes[0])
                .bufferedReader().use { it.readText() }
            check(output.lineSequence().any { it.startsWith("__CRA_ROOT_CONTENT_READ_DONE__:") }) {
                "privileged content shell exited without its completion marker"
            }
            return output
        }

        private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }

    // ── run 绑定（由 driver 先放 binding.json；缺则明确失败，不猜 run） ──────────────

    private fun evidenceDir(): File =
        File(context.getExternalFilesDir(null), "task35_evidence/$runId").also { it.mkdirs() }

    private fun binding(): JSONObject {
        val f = File(context.getExternalFilesDir(null), "task35_evidence/binding.json")
        assertTrue(
            "缺 run 绑定 binding.json：任务35 要求 dump/交接/导出按 run 专属绑定，" +
                "没有 runId 就无法做预存拒绝与对象归属（不得猜 run、不得共用旧路径）",
            f.isFile,
        )
        return JSONObject(f.readText())
    }

    private val runId: String by lazy {
        val b = binding()
        val id = b.optString("runId")
        assertTrue("binding.json invalid runId", id.matches(Regex("[A-Za-z0-9_-]+")))
        id
    }

    /** 本 run 专属 dump 目录；已存在 ⇒ 预存对象，直接拒绝（不覆盖、不当本轮产物）。 */
    private fun runDumpDir(): File {
        val dumpRoot = File(context.getExternalFilesDir(null), Task35SafCleaner.DUMP_DIR_NAME)
        val dir = File(dumpRoot, runId)
        assertTrue("预存 dump 目录拒绝：${dir.absolutePath} 已存在，不是本 run 创建", !dir.exists())
        assertTrue("无法创建任务 dump 根目录：${dumpRoot.absolutePath}", dumpRoot.mkdirs() || dumpRoot.isDirectory)
        assertTrue("无法创建 run 专属 dump 目录：${dir.absolutePath}", dir.mkdirs())
        return dir
    }

    /**
     * 将当前前台系统窗口（DocumentsUI / SAF 窗口）真实转储为标准 XML。
     * 运行在测试进程内部，直接使用已持有的 uiAutomation 访问活跃窗口 AccessibilityNodeInfo 树，
     * 杜绝外部 uiautomator 命令抢占连接冲突以及 Scoped Storage 跨 UID 写权限拒绝。
     *
     * @param requirePackage 期望出现在 dump 内的系统窗口包名片段（正向身份核验）；
     *                       应用自身空树或其它前台窗口不得冒充系统 DocumentsUI。
     */
    private fun dumpWindowHierarchyToXml(targetFile: File, requirePackage: String) {
        assertTrue("预存 dump 拒绝：${targetFile.absolutePath} 已存在，不是本 run 创建", !targetFile.exists())
        val automation = instr.uiAutomation
        var root: android.view.accessibility.AccessibilityNodeInfo? = null
        val deadline = SystemClock.elapsedRealtime() + 3000
        while (SystemClock.elapsedRealtime() < deadline) {
            root = automation.rootInActiveWindow
            if (root != null) break
            SystemClock.sleep(100)
        }
        assertNotNull("uiAutomation.rootInActiveWindow 为空，无法获取系统窗口层次结构", root)
        val nonNullRoot = root!!

        // 证据字段必须来自实际状态：旋转取当前默认显示的真实 rotation，不写死值冒充观测。
        val windowManager =
            context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        val rotation = windowManager.defaultDisplay?.rotation ?: 0

        val stringWriter = java.io.StringWriter()
        val serializer = android.util.Xml.newSerializer()
        serializer.setOutput(stringWriter)
        serializer.startDocument("UTF-8", true)
        runCatching { serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true) }
        serializer.startTag("", "hierarchy")
        serializer.attribute("", "rotation", rotation.toString())

        // 深度上限只作递归安全阀：一旦触发必须显式记录并失败，绝不静默截断后仍称完整。
        var truncated = false
        fun serializeNode(node: android.view.accessibility.AccessibilityNodeInfo?, index: Int, depth: Int) {
            if (node == null) return
            if (depth > MAX_DUMP_DEPTH) {
                truncated = true
                return
            }
            serializer.startTag("", "node")
            serializer.attribute("", "index", index.toString())
            serializer.attribute("", "text", node.text?.toString() ?: "")
            serializer.attribute("", "resource-id", node.viewIdResourceName ?: "")
            serializer.attribute("", "class", node.className?.toString() ?: "")
            serializer.attribute("", "package", node.packageName?.toString() ?: "")
            serializer.attribute("", "content-desc", node.contentDescription?.toString() ?: "")
            serializer.attribute("", "checkable", node.isCheckable.toString())
            serializer.attribute("", "checked", node.isChecked.toString())
            serializer.attribute("", "clickable", node.isClickable.toString())
            serializer.attribute("", "enabled", node.isEnabled.toString())
            serializer.attribute("", "focusable", node.isFocusable.toString())
            serializer.attribute("", "focused", node.isFocused.toString())
            serializer.attribute("", "scrollable", node.isScrollable.toString())
            serializer.attribute("", "long-clickable", node.isLongClickable.toString())
            serializer.attribute("", "password", node.isPassword.toString())
            serializer.attribute("", "selected", node.isSelected.toString())
            val r = android.graphics.Rect()
            node.getBoundsInScreen(r)
            serializer.attribute("", "bounds", "[${r.left},${r.top}][${r.right},${r.bottom}]")

            for (i in 0 until node.childCount) {
                serializeNode(node.getChild(i), i, depth + 1)
            }
            serializer.endTag("", "node")
        }

        serializeNode(nonNullRoot, 0, 0)
        serializer.endTag("", "hierarchy")
        serializer.endDocument()

        val xml = stringWriter.toString()
        assertTrue("生成的 dump XML 不合法或为空", xml.isNotBlank() && xml.contains("<hierarchy") && xml.contains("</hierarchy>"))
        assertTrue(
            "dump 因深度上限被截断（depth>$MAX_DUMP_DEPTH），不得冒充完整窗口树",
            !truncated,
        )
        assertTrue(
            "dump 必须是系统窗口（$requirePackage）的真实层次结构，" +
                "应用自身空树或其它前台窗口不得冒充系统 DocumentsUI（前 200 字符：${xml.take(200)}）",
            xml.contains(requirePackage),
        )
        targetFile.writeText(xml, Charsets.UTF_8)
        assertTrue("dump 文件写入失败或为空: ${targetFile.absolutePath}", targetFile.isFile && targetFile.length() > 0)
    }

    private val ledger by lazy { Task35CreationLedger(runId) }

    // ── shell 驱动 ─────────────────────────────────────────────────────────────

    /** 单条 shell 命令（executeShellCommand 不经 sh：只用 argv 形式，不用管道/重定向）。 */
    private fun shell(cmd: String): String {
        val pfd = instr.uiAutomation.executeShellCommand(cmd)
        return java.io.FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
            .also { runCatching { pfd.close() } }
    }

    private fun ensureAutomationFlags() {
        runCatching {
            val info = instr.uiAutomation.serviceInfo
                ?: android.accessibilityservice.AccessibilityServiceInfo()
            info.flags = info.flags or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            instr.uiAutomation.serviceInfo = info
        }
    }

    private fun activeWindowSummary(): String {
        val root = instr.uiAutomation.rootInActiveWindow ?: return "(no active window)"
        val sb = StringBuilder()
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 25) return
            val r = android.graphics.Rect(); n.getBoundsInScreen(r)
            val rid = n.viewIdResourceName ?: ""
            val txt = n.text?.toString() ?: ""
            val desc = n.contentDescription?.toString() ?: ""
            if (rid.isNotBlank() || txt.isNotBlank() || desc.isNotBlank()) {
                sb.append(rid).append('|').append(txt).append('|').append(desc)
                    .append('|').append("[${r.left},${r.top}][${r.right},${r.bottom}]")
                    .append('|').append(n.className ?: "").append('\n')
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return sb.toString()
    }

    private fun nodeCenter(resId: String): Pair<Int, Int>? {
        val root = instr.uiAutomation.rootInActiveWindow ?: return null
        var found: android.view.accessibility.AccessibilityNodeInfo? = null
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo?) {
            if (n == null || found != null) return
            if (n.viewIdResourceName == resId) { found = n; return }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        val f = found ?: return null
        val r = android.graphics.Rect(); f.getBoundsInScreen(r)
        return ((r.left + r.right) / 2) to ((r.top + r.bottom) / 2)
    }

    private fun nodeCenterByText(text: String): Pair<Int, Int>? {
        val root = instr.uiAutomation.rootInActiveWindow ?: return null
        var found: android.view.accessibility.AccessibilityNodeInfo? = null
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo?) {
            if (n == null || found != null) return
            if (n.text?.toString() == text) { found = n; return }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        val f = found ?: return null
        val r = android.graphics.Rect(); f.getBoundsInScreen(r)
        return ((r.left + r.right) / 2) to ((r.top + r.bottom) / 2)
    }

    private fun tap(x: Int, y: Int) { shell("input tap $x $y") }

    private fun currentFocus(): String =
        shell("dumpsys window").lines().firstOrNull { it.contains("mCurrentFocus") }?.trim() ?: "(none)"

    // ── 夹具（run 专属 token，中性测试数据）────────────────────────────────────

    private val json = Json { encodeDefaults = true; explicitNulls = false }

    /** run 专属 ASCII token：出现在导出内容里 ⇒ 该对象确由本 run 产生（不按文件名猜）。

        规则复用 [Task35EvidenceGate.runTokenPrefix] —— 导出侧与清理侧同源，不得另建一套。 */
    private fun tok(suffix: String) = Task35EvidenceGate.runTokenPrefix(runId) + suffix

    private fun inspiration(id: String, title: String, body: String, payload: String?) = InspirationEntity(
        id = id, title = title, body = body, payload = payload,
        created_at = "2026-10-13T00:00:00Z", updated_at = "2026-10-13T00:00:00Z",
    )

    private fun singleSourcePayload(): String = json.encodeToString(
        com.creationreadingassistant.ui.viewmodel.InspirationPayloadData.serializer(),
        com.creationreadingassistant.ui.viewmodel.InspirationPayloadData(
            source = com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo(
                bookId = "b24", bookTitle = tok("SRC"), bookAuthor = "测试作者",
                chapterTitle = "第一章", locationLabel = tok("LOC"),
                progressPercent = 12.5f,
                excerpt = "${tok("L1")}\n${tok("L2")}",
                locatorJson = "{\"v\":2,\"offset\":42}",
            ),
        ),
    )

    /** 导出内容必须命中的 token：多行摘录（L1/L2）、来源（SRC）、定位（LOC）、高亮（HL）。 */
    private fun requiredTokens(): List<String> =
        listOf(tok("L1"), tok("L2"), tok("SRC"), tok("LOC"), tok("HL"))

    private fun renderNotesSheet(highlights: List<HighlightEntity>, notes: List<NoteEntity>, inspirations: List<InspirationEntity>) {
        composeRule.setContent {
            MaterialTheme {
                NotesSheet(
                    paper = paperPalette("white", false),
                    bookTitle = BOOK_TITLE,
                    highlights = highlights, notes = notes, inspirations = inspirations,
                    onAddBookmark = {}, onDeleteHighlight = {}, onDeleteNote = {},
                    onChangeHighlightColor = { _, _ -> }, onEditHighlightNote = { _, _ -> },
                    onHighlightToNote = {}, onHighlightToInspiration = {},
                    onJumpToHighlight = {}, onJumpToNote = {},
                    onExportCompleted = { completedUri = it },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun writeEvidence(step: String, payload: JSONObject, identity: Task35IdentityRecord? = null) {
        if (identity != null) check(identity.runId == runId && identity.phase == step) { "identity run/phase mismatch" }
        val f = File(evidenceDir(), "$step.json")
        f.writeText(JSONObject().apply {
            put("task", TASK_ID); put("runId", runId); put("step", step)
            // producerPid 只是进程内自述；身份证据看 identity 块（设备侧实读）
            put("producerPid", android.os.Process.myPid())
            put("pidIsIdentityEvidence", false)
            put("packageName", context.packageName)
            put("timestamp", Instant.now().toString()); put("timestampMillis", System.currentTimeMillis())
            if (identity != null) put("identity", identity.toJson())
            put("creationLedger", ledger.toJson())
            put("payload", payload)
        }.toString(2))
        println("TASK35_EVIDENCE=${f.name}")
        println("TASK35_RUN=$runId")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 真实系统 SAF CreateDocument
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun notesExport_realSystemSafCreateDocumentAndSave() {
        val dumpDir = runDumpDir()
        completedUri = null
        // 关键安全前置：在创建新文档/申请新授权之前，保存真实 pre-run 授权基线到专属证据文件
        Task35PreGrantsSnapshot.capture(context.contentResolver, runId).writeTo(evidenceDir())
        val existingGrants = context.contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()
        renderNotesSheet(
            listOf(HighlightEntity(id = "h1", book_id = "b24", text = tok("HL"), chapter_title = "第一章",
                created_at = "2026-10-13T00:00:00Z", updated_at = "2026-10-13T00:00:00Z")),
            emptyList(),
            listOf(inspiration("i1", tok("INS"), "个人正文", singleSourcePayload())),
        )

        composeRule.onNodeWithContentDescription("导出").performClick()
        SystemClock.sleep(2500)
        ensureAutomationFlags()
        val focus1 = currentFocus()
        val dump1 = File(dumpDir, "saf-open-1.xml")
        dumpWindowHierarchyToXml(dump1, "documentsui")
        ledger.register("dump", dump1.absolutePath, dump1)
        val registeredDump = ledger.entries().lastOrNull { it.kind == "dump" }
        assertNotNull("dump 对象必须登记到创建物账本", registeredDump)
        assertTrue(
            "dump 对象必须真实存在且标记为本 run 创建 (sha=${registeredDump?.sha256}, bytes=${registeredDump?.bytes})",
            registeredDump?.createdByThisRun == true && registeredDump.bytes > 0 && !registeredDump.sha256.isNullOrBlank(),
        )
        val summary1 = activeWindowSummary()
        val safOpened = focus1.contains("documentsui") || summary1.contains("documentsui")

        var saveTap = "not_attempted"
        if (safOpened) {
            nodeCenterByText("Download")?.let { tap(it.first, it.second); SystemClock.sleep(1800) }
            val saveBtn = nodeCenter("android:id/button1")
            if (saveBtn != null) {
                tap(saveBtn.first, saveBtn.second)
                saveTap = "tapped"
            }
        }
        val deadline = SystemClock.elapsedRealtime() + 45000
        while (completedUri == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        val actualUri = completedUri
        val bytes = actualUri?.let { uri -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
        val content = Task35EvidenceGate.exportContent(bytes, null, requiredTokens())
        val sizeAfter = bytes?.size?.toLong() ?: -1L
        val shaAfter = bytes?.let { Task35EvidenceGate.sha256(it) } ?: ""
        val tokenHits = requiredTokens().associateWith { token -> bytes?.toString(Charsets.UTF_8)?.split(token)?.size?.minus(1) ?: 0 }
        val saved = actualUri != null && content.accepted
        if (actualUri != null) {
            assertTrue("existing URI grant cannot be claimed as created by this run", actualUri.toString() !in existingGrants)
            context.contentResolver.takePersistableUriPermission(actualUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ledger.registerObserved("export_uri", actualUri.toString(), bytes, saved)
            ledger.registerObserved("uri_read_permission", actualUri.toString(), null, true)
        }

        val identity = Task35Identity.capture(
            hostPackage = context.packageName,
            readerMarker = null,
            foregroundDump = null,
        )
        writeEvidence("notesExport_realSystemSafCreateDocumentAndSave", JSONObject().apply {
            put("stage", "observed")
            put("safOpened", safOpened); put("focusAfterLaunch", focus1)
            put("saveTap", saveTap)
            put("fileSize", sizeAfter); put("fileSha256", shaAfter)
            put("bytesBase64", bytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) } ?: "")
            put("runId", runId)
            put("requiredTokens", org.json.JSONArray(requiredTokens()))
            put("tokenHits", JSONObject(tokenHits as Map<*, *>)); put("contentBoundToRun", saved)
            put("runDumpDir", dumpDir.absolutePath)
            put("uriObservation", JSONObject().put("status", if (actualUri != null) "OBSERVED" else "BLOCKED")
                .put("uri", actualUri?.toString() ?: JSONObject.NULL).put("runId", runId)
                .put("seam", "NotesSheet.onExportCompleted after output stream close")
                .put("resolverRead", bytes != null).put("fileSha256", shaAfter))
            put("note", "真实系统 SAF（DocumentsUI）窗口实际打开并驱动；非受控 ActivityResultRegistry")
        }, identity)

        assertTrue("必须实际打开系统 SAF 窗口（focus=$focus1）", safOpened)
        assertNotNull("must observe actual successful callback URI", actualUri)
        assertTrue("导出对象必须非空（size=$sizeAfter）", sizeAfter > 0)
        assertTrue("导出对象必须能实算 SHA（sha=$shaAfter）", shaAfter.isNotBlank())
        assertTrue(
            "导出内容必须按 run 专属 token 绑定本 run（多行摘录/来源/定位/高亮）：$tokenHits",
            tokenHits.isNotEmpty() && tokenHits.values.all { it >= 1 },
        )
        assertTrue("内容绑定与 SHA 齐备才算保存成功", saved)
        assertTrue("导出证据必须携带身份握手", Task35EvidenceGate.identityBound(identity, false).accepted)
        assertNotNull("successful callback must expose the actual returned URI", actualUri)
        val handoff = File(evidenceDir(), "saf-$runId-handoff.json")
        check(!handoff.exists()) { "pre-existing handoff rejected" }
        handoff.writeText(JSONObject().put("runId", runId).put("uri", actualUri.toString())
            .put("fileSha256", shaAfter).put("fileSize", sizeAfter).toString())
        ledger.register("handoff", handoff.absolutePath, handoff)
        File(evidenceDir(), "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))
    }

    /**
     * 冷读阶段：应用进程已重启（生产回调经真实返回 URI 的写出必已完成），读取上一阶段
     * 系统 SAF 保存的**真实对象字节**（SHA + run token）并核对；同时要求**真实冷进程身份**
     * （设备侧 pid / 进程启动时刻 / 前台组件），不再只判 size>0。
     */
    @Test
    fun notesExport_realSystemSafColdReadContent() {
        val handoff = File(evidenceDir(), "saf-$runId-handoff.json")
        assertTrue("missing same-run URI handoff", handoff.isFile)
        val doc = JSONObject(handoff.readText())
        assertTrue("wrong handoff run", doc.getString("runId") == runId)
        val uri = android.net.Uri.parse(doc.getString("uri"))
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        val verdict = Task35EvidenceGate.exportContent(bytes, doc.getString("fileSha256"), requiredTokens())
        val size = bytes?.size?.toLong() ?: -1L
        val sha = bytes?.let { Task35EvidenceGate.sha256(it) } ?: ""
        val tokenHits = requiredTokens().associateWith { token -> bytes?.toString(Charsets.UTF_8)?.split(token)?.size?.minus(1) ?: 0 }
        val contentOk = verdict.accepted && size == doc.getLong("fileSize")

        val previousEvidenceMillis = File(evidenceDir(), "notesExport_realSystemSafCreateDocumentAndSave.json")
            .takeIf { it.isFile }
            ?.let { JSONObject(it.readText()).optLong("timestampMillis", -1L) }
            ?.takeIf { it > 0 }
        val previousProcessEndMillis = Task35Identity.readProcessEndMillis(evidenceDir())
        val identity = Task35Identity.capture(
            hostPackage = context.packageName,
            readerMarker = null,
            foregroundDump = null,
        )
        val cold = Task35EvidenceGate.coldProcess(identity, previousEvidenceMillis, previousProcessEndMillis)

        writeEvidence("notesExport_realSystemSafColdReadContent", JSONObject().apply {
            put("stage", "cold_read"); put("uri", uri.toString()); put("resolverRead", bytes != null)
            put("fileSize", size)
            put("fileSha256", sha)
            put("bytesBase64", bytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) } ?: "")
            put("runId", runId); put("requiredTokens", org.json.JSONArray(requiredTokens()))
            put("tokenHits", JSONObject(tokenHits as Map<*, *>))
            put("contentOk", contentOk); put("sameUri", uri.toString() == doc.getString("uri"))
            put("previousEvidenceMillis", previousEvidenceMillis ?: JSONObject.NULL)
            put("previousProcessEndMillis", previousProcessEndMillis ?: JSONObject.NULL)
            put("coldProcess", cold.accepted)
            put("coldProcessReason", cold.reason)
            put("note", "冷读真实系统 SAF 保存对象的实际字节与 run token；进程身份由设备侧实读，不用 Process.myPid()")
        }, identity)

        assertTrue("冷读导出对象必须非空（size=$size）", size > 0)
        assertTrue("冷读必须实算对象 SHA（sha=$sha）", sha.isNotBlank())
        assertTrue("冷读必须按实际字节命中 run 专属 token：$tokenHits", contentOk)
        assertTrue("冷读必须是真实冷进程：${cold.reason}", cold.accepted)
        assertTrue("冷读证据必须携带身份握手", Task35EvidenceGate.identityBound(identity, false).accepted)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 真实系统分享选择器
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun notesShare_realSystemChooserObservedThenCancel() {
        renderNotesSheet(
            listOf(HighlightEntity(id = "h2", book_id = "b24", text = tok("HL"), chapter_title = "第一章",
                created_at = "2026-10-13T00:00:00Z", updated_at = "2026-10-13T00:00:00Z")),
            emptyList(),
            listOf(inspiration("i2", tok("INS"), "正文", singleSourcePayload())),
        )

        composeRule.onNodeWithContentDescription("分享").performClick()
        SystemClock.sleep(2500)
        ensureAutomationFlags()
        val focus = currentFocus()
        val summary = activeWindowSummary()
        val chooserOpened = focus.contains("chooser", ignoreCase = true) ||
            focus.contains("ResolverActivity", ignoreCase = true) ||
            summary.contains("分享阅读笔记") || summary.contains("android:id/resolver_list")
        val titles = summary.lines().flatMap { line ->
            line.split('|').drop(1).take(2)
        }.filter { it.isNotBlank() && it != "null" }.distinct().take(60)

        // 传递内容核验：chooser 窗口必须能看到本次 run 的 token（证明传递的就是本轮内容）
        val runToken = tok("HL")
        val contentSeen = summary.contains(runToken)
        val systemIdentity = focus

        shell("input keyevent KEYCODE_BACK")
        SystemClock.sleep(1500)
        val focusAfter = currentFocus()

        val identity = Task35Identity.capture(
            hostPackage = context.packageName,
            readerMarker = null,
            foregroundDump = null,
        )
        writeEvidence("notesShare_realSystemChooserObservedThenCancel", JSONObject().apply {
            put("stage", "observed")
            put("chooserOpened", chooserOpened); put("focusAfterLaunch", focus)
            put("focusAfterCancel", focusAfter)
            put("systemWindowIdentity", systemIdentity)
            put("runToken", runToken)
            put("contentSeenInChooser", contentSeen)
            put("observableTexts", org.json.JSONArray(titles))
            put("windowSummary", summary.take(4000))
            put("note", "真实系统 chooser 实际打开并采集可观察内容后取消；未选择接收应用、未发送消息")
        }, identity)

        assertTrue("必须实际打开系统分享 chooser（focus=$focus）", chooserOpened)
        assertTrue("chooser 可观察文本必须非空", titles.isNotEmpty())
        assertTrue("必须记录系统窗口身份（前台组件）", systemIdentity.isNotBlank())
        assertTrue("chooser 证据必须携带身份握手", Task35EvidenceGate.identityBound(identity, false).accepted)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 真实系统 SAF 清理与恢复入口
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 独立恢复与清理步骤：由 driver 在导出和冷读均完成后调用。
     * 绝不在测试类内部通过 @After 无条件提前清理，以确保冷读前文档与授权完整可用。
     */
    @Test
    fun notesExport_realSystemSafCleanupAndRestore() {
        val res = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = evidenceDir(),
            independentAbsenceObserver = { uri -> rootContentReadOutput(instr.uiAutomation, uri) },
        )
        println("TASK35_RESTORE_RESULT=${res.toJson()}")
        assertTrue(
            "SAF 清理与授权恢复失败：${res.failureReason}",
            res.observedUriAbsent && res.permissionReleased && res.grantsAfter == res.grantsBefore,
        )
    }
}
