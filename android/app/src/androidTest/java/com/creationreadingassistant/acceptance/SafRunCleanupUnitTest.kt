package com.creationreadingassistant.acceptance

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 任务42-v1：SAF 按 run 清理恢复与证据生产的离线定向验证套件。
 *
 * 覆盖 41-v1 评审纠正及安全边界：
 * 1. 错误 runId / 缺失 handoff 拒绝清理；
 * 2. 既有授权受保护，绝不释放测试前已存在的 URI；
 * 3. 删除失败如实报错，禁止假称已清理；
 * 4. 权限拒绝（SecurityException）绝不当作文档不存在（observedUriAbsent）；
 * 5. 成功恢复生成符合 evidence-contracts.json 严格规范的 __restoration 证据；
 * 6. 验证导出→冷读→清理调用顺序，冷读前绝不提前清理。
 */
@RunWith(AndroidJUnit4::class)
class SafRunCleanupUnitTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createTestDir(testName: String): File {
        val dir = File(context.cacheDir, "test_saf_cleanup_$testName")
        dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }

    /** run 专属导出内容（含本 run token），与清理侧 requiredRunTokens 同源。 */
    private fun runContent(runId: String): ByteArray =
        Task35EvidenceGate.requiredRunTokens(runId).joinToString("\n").toByteArray()

    private fun shaOf(bytes: ByteArray): String = Task35EvidenceGate.sha256(bytes)

    /**
     * 场景 1：错误 runId、缺失基线、缺失账本、账本未登记或指纹不符时严格拒绝清理并如实报告。
     */
    @Test
    fun testOwnershipValidation_rejectsWrongRunOrMissingHandoff() {
        val testDir = createTestDir("ownership_reject")
        val runId = "testRunAlpha"
        val testUri = "content://com.android.documents/document/alpha_doc"

        // 场景 1.1：缺 pre-grants 基线
        val resNoBase = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
        )
        assertFalse("缺基线快照应拒绝", resNoBase.observedUriAbsent)
        assertNotNull("应有明确失败原因", resNoBase.failureReason)
        assertTrue(resNoBase.failureReason!!.contains("缺授权基线快照"))

        // 场景 1.2：基线快照 runId 不匹配
        val wrongSnapshot = Task35PreGrantsSnapshot("otherRunX", System.currentTimeMillis(), emptyList())
        val fSnapshot = File(testDir, "saf-$runId-pre-grants.json")
        fSnapshot.writeText(wrongSnapshot.toJson().toString(2))
        val resSnapshotMismatch = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
        )
        assertFalse("快照 runId 不匹配应拒绝", resSnapshotMismatch.observedUriAbsent)
        assertTrue(resSnapshotMismatch.failureReason!!.contains("基线快照 runId 不匹配"))
        fSnapshot.delete()

        // 写入合法基线
        Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), emptyList()).writeTo(testDir)

        // 场景 1.3：缺 ledger 账本（禁止新建空账直接清理）
        val resNoLedger = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
        )
        assertFalse("缺创建账本应拒绝", resNoLedger.observedUriAbsent)
        assertTrue(resNoLedger.failureReason!!.contains("缺创建账本"))

        // 场景 1.4：账本 runId 不匹配
        val wrongLedgerFile = File(testDir, "task35-ledger-$runId.json")
        val wrongLedger = Task35CreationLedger("otherRunY")
        wrongLedgerFile.writeText(wrongLedger.toJson().toString(2))
        val resLedgerMismatch = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
        )
        assertFalse("账本 runId 不匹配应拒绝", resLedgerMismatch.observedUriAbsent)
        assertTrue(resLedgerMismatch.failureReason!!.contains("账本 runId 不匹配"))

        // 写入合法账本，但先不登记目标 URI
        val ledger = Task35CreationLedger(runId)
        val ledgerFile = File(testDir, "task35-ledger-$runId.json")
        ledgerFile.writeText(ledger.toJson().toString(2))

        // 场景 1.5：写出基线与账本但缺 handoff
        val resNoHandoff = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
        )
        assertFalse("缺 handoff 应拒绝", resNoHandoff.observedUriAbsent)
        assertTrue(resNoHandoff.failureReason!!.contains("缺交接文件"))

        // 场景 1.6：handoff runId 不匹配
        val handoffFile = File(testDir, "saf-$runId-handoff.json")
        handoffFile.writeText(JSONObject().put("runId", "otherRunBeta").put("uri", testUri).toString())
        val resHandoffMismatch = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
        )
        assertFalse("handoff runId 不匹配应拒绝", resHandoffMismatch.observedUriAbsent)
        assertTrue(resHandoffMismatch.failureReason!!.contains("handoff runId 不匹配"))

        // 场景 1.7：handoff runId 匹配，但账本中未登记该 URI
        handoffFile.writeText(JSONObject().put("runId", runId).put("uri", testUri).toString())
        val resUnregistered = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
        )
        assertFalse("未在账本登记的目标 URI 应被拒绝", resUnregistered.observedUriAbsent)
        assertTrue(resUnregistered.failureReason!!.contains("账本中未登记该目标 URI"))

        // 场景 1.8：账本登记了，但 handoff 中的 fileSha256 指纹不匹配
        ledger.registerObserved("export_uri", testUri, "correct_bytes".toByteArray(), true)
        ledgerFile.writeText(ledger.toJson().toString(2))
        handoffFile.writeText(
            JSONObject()
                .put("runId", runId)
                .put("uri", testUri)
                .put("fileSha256", "forged_sha256_hash")
                .put("fileSize", "correct_bytes".toByteArray().size.toLong())
                .toString()
        )
        val resFingerprintMismatch = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
        )
        assertFalse("handoff 内容指纹与账本不符应被拒绝", resFingerprintMismatch.observedUriAbsent)
        assertTrue(resFingerprintMismatch.failureReason!!.contains("指纹不符"))
    }

    /**
     * 场景 2：授权基线快照防覆写保护（一次写入，禁止重复篡改）。
     */
    @Test
    fun testPreGrantsSnapshot_overwriteProtection() {
        val testDir = createTestDir("snapshot_overwrite")
        val runId = "testRunSnapshotProtect"

        val snap1 = Task35PreGrantsSnapshot(runId, 1000L, emptyList())
        val f = snap1.writeTo(testDir)
        assertTrue("首次写入成功", f.isFile)

        val snap2 = Task35PreGrantsSnapshot(runId, 2000L, emptyList())
        var exceptionThrown = false
        try {
            snap2.writeTo(testDir)
        } catch (e: IllegalStateException) {
            exceptionThrown = true
            assertTrue(e.message!!.contains("禁止覆写"))
        }
        assertTrue("已存在快照时再次写入必须抛出异常", exceptionThrown)
    }

    /**
     * 场景 3：目标 URI 若已在 pre-run 基线中存在，绝对禁止当作本轮新建释放！
     */
    @Test
    fun testPreExistingGrant_protectedFromRelease() {
        val testDir = createTestDir("pre_existing_protect")
        val runId = "testRunPreExist"
        val existingUri = "content://com.android.documents/document/pre_existing_doc"

        // 将该 URI 登记在测试前基线中
        val preGrants = Task35PreGrantsSnapshot(
            runId = runId,
            capturedAtMillis = System.currentTimeMillis(),
            grants = listOf(Task35PreGrantEntry(existingUri, isRead = true, isWrite = false, persistedTime = 1000L)),
        )
        preGrants.writeTo(testDir)

        // 登记账本
        val ledger = Task35CreationLedger(runId)
        ledger.registerObserved("export_uri", existingUri, null, true)
        File(testDir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))

        // 伪造本 run handoff 试图冒充该 URI
        val handoff = File(testDir, "saf-$runId-handoff.json")
        handoff.writeText(JSONObject().put("runId", runId).put("uri", existingUri).toString())

        var releaseCalled = false
        val res = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
            documentDeleter = { false },
            permissionReleaser = { _, _ -> releaseCalled = true },
        )

        assertFalse("既有授权冒充本轮新建应被拒绝", res.observedUriAbsent)
        assertFalse("绝不能调用 release 释放原有授权", releaseCalled)
        assertTrue(res.failureReason!!.contains("不得当作本轮新建释放"))
    }

    /**
     * 场景 4：文档删除失败时如实记录失败，绝不假称已清理，且严格保留 handoff 凭证。
     */
    @Test
    fun testDeleteFailure_reportedHonestly() {
        val testDir = createTestDir("delete_fail")
        val runId = "testRunDeleteFail"
        val newUri = "content://com.android.documents/document/new_doc_1"

        // 基线为空
        Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), emptyList()).writeTo(testDir)

        // 账本登记
        val content = runContent(runId)
        val ledger = Task35CreationLedger(runId)
        ledger.registerObserved("export_uri", newUri, content, true)
        File(testDir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))

        val handoff = File(testDir, "saf-$runId-handoff.json")
        handoff.writeText(
            JSONObject().put("runId", runId).put("uri", newUri)
                .put("fileSha256", shaOf(content)).put("fileSize", content.size.toLong()).toString()
        )

        // 注入文档删除器：抛出 UnsupportedOperationException（删除前对象仍可读）
        val res = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
            documentDeleter = { throw UnsupportedOperationException("provider does not support document delete") },
            streamOpener = { java.io.ByteArrayInputStream(content) },
            permissionReleaser = { _, _ -> },
            grantInspector = { emptyList() },
        )

        assertFalse("删除失败时 documentDeleted 必须为 false", res.documentDeleted)
        assertFalse("删除失败时 observedUriAbsent 必须为 false", res.observedUriAbsent)
        assertTrue(res.failureReason!!.contains("deleteDocument 异常"))
        assertTrue("删除失败时 handoff 凭证必须保留供排查", handoff.isFile)
    }

    /**
     * 场景 5：权限拒绝（SecurityException）绝不当作文档不存在（observedUriAbsent 必须为 false，保持 NOT_PROVEN）。
     */
    @Test
    fun testSecurityException_notTreatedAsDocumentAbsent() {
        val testDir = createTestDir("security_ex_not_absent")
        val runId = "testRunSecEx"
        val newUri = "content://com.android.documents/document/new_doc_2"
        val content = runContent(runId)

        Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), emptyList()).writeTo(testDir)
        val ledger = Task35CreationLedger(runId)
        ledger.registerObserved("export_uri", newUri, content, true)
        File(testDir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))

        val handoff = File(testDir, "saf-$runId-handoff.json")
        handoff.writeText(
            JSONObject().put("runId", runId).put("uri", newUri)
                .put("fileSha256", shaOf(content)).put("fileSize", content.size.toLong()).toString()
        )

        // 首次读取真实归属内容；删除成功后，第二次读取模拟 provider 权限拒绝。
        var streamCalls = 0
        var deleteCalled = false
        val res = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
            documentDeleter = { deleteCalled = true; true },
            streamOpener = {
                streamCalls++
                if (streamCalls == 1) java.io.ByteArrayInputStream(content)
                else throw SecurityException("permission denied by provider")
            },
            permissionReleaser = { _, _ -> },
            grantInspector = { emptyList() },
        )

        assertTrue("归属凭证有效后应执行删除", deleteCalled)
        assertEquals("应先读归属内容、删除后再检查文档", 2, streamCalls)
        assertTrue("删除前必须确认 ledger/handoff 指纹与大小匹配", res.documentDeleted)
        assertFalse("仅权限不足绝不能推导文档物理不存在 (observedUriAbsent 必须为 false)", res.observedUriAbsent)
        assertNotNull("必须记录 absence 原因或失败说明", res.failureReason)
        assertTrue("失败原因必须指明删除后权限拒绝与 NOT_PROVEN", res.failureReason!!.contains("openInputStream 权限拒绝") && res.failureReason!!.contains("SecurityException") && res.failureReason!!.contains("NOT_PROVEN"))
        assertTrue("因未完全成功，handoff 文件必须保留", handoff.isFile)
    }

    @Test
    fun testPreDeleteSecurityException_rejectsWithoutDeletion() {
        val testDir = createTestDir("security_ex_pre_delete")
        val runId = "testRunSecExBeforeDelete"
        val newUri = "content://com.android.documents/document/new_doc_security"
        val content = runContent(runId)

        Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), emptyList()).writeTo(testDir)
        val ledger = Task35CreationLedger(runId)
        ledger.registerObserved("export_uri", newUri, content, true)
        File(testDir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))
        val handoff = File(testDir, "saf-$runId-handoff.json")
        handoff.writeText(
            JSONObject().put("runId", runId).put("uri", newUri)
                .put("fileSha256", shaOf(content)).put("fileSize", content.size.toLong()).toString()
        )

        var deleteCalled = false
        val res = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
            documentDeleter = { deleteCalled = true; true },
            streamOpener = { throw SecurityException("permission denied before delete") },
            permissionReleaser = { _, _ -> },
            grantInspector = { emptyList() },
        )

        assertFalse("删除前无法核验对象归属时不得调用删除", deleteCalled)
        assertFalse(res.documentDeleted)
        assertFalse(res.observedUriAbsent)
        assertTrue(res.failureReason!!.contains("SecurityException") && res.failureReason!!.contains("NOT_PROVEN"))
        assertTrue("失败时保留handoff凭证", handoff.isFile)
    }

    @Test
    fun testIndependentMissingFileEvidence_isExactAndRejectsPermissionErrors() {
        val uri = Uri.parse("content://com.android.documents/document/MuMuShared%3ADownload%2Fnote.txt")
        val id = android.provider.DocumentsContract.getDocumentId(uri)
        assertTrue(Task35SafCleaner.confirmsIndependentMissingFile(uri,
            "java.io.FileNotFoundException: Missing file for $id at /mnt/shared/Download/note.txt"))
        assertFalse(Task35SafCleaner.confirmsIndependentMissingFile(uri,
            "java.io.FileNotFoundException: Missing file for wrong-id at /mnt/shared/Download/note.txt"))
        assertFalse(Task35SafCleaner.confirmsIndependentMissingFile(uri,
            "java.io.FileNotFoundException: Missing file for $id at /mnt/shared/Download/note.txt\nSecurityException: Permission Denial"))
        assertFalse(Task35SafCleaner.confirmsIndependentMissingFile(uri, "Permission Denial: SecurityException"))
        assertFalse(Task35SafCleaner.confirmsIndependentMissingFile(uri, "provider output unavailable"))
    }

    @Test
    fun testProviderAutoRevokesRunGrant_afterIndependentAbsenceProof_skipsReleaseCall() {
        val testDir = createTestDir("provider_auto_revokes")
        val runId = "testRunAutoRevoke"
        val newUri = "content://com.android.documents/document/MuMuShared%3ADownload%2Fnote.txt"
        val targetGrant = Task35PreGrantEntry(newUri, isRead = true, isWrite = false, persistedTime = 700L)
        val content = runContent(runId)
        Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), emptyList()).writeTo(testDir)
        val ledger = Task35CreationLedger(runId)
        ledger.registerObserved("export_uri", newUri, content, true)
        ledger.registerObserved("uri_read_permission", newUri, null, true)
        File(testDir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))
        val handoff = File(testDir, "saf-$runId-handoff.json")
        handoff.writeText(JSONObject().put("runId", runId).put("uri", newUri)
            .put("fileSha256", shaOf(content)).put("fileSize", content.size.toLong()).toString())

        var deleted = false
        var releaseCalls = 0
        val res = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
            documentDeleter = { deleted = true; true },
            streamOpener = { if (deleted) throw SecurityException("provider revoked URI grant") else content.inputStream() },
            permissionReleaser = { _, _ -> releaseCalls++ },
            grantInspector = { if (deleted) emptyList() else listOf(targetGrant) },
            independentAbsenceObserver = { uri ->
                val id = android.provider.DocumentsContract.getDocumentId(uri)
                "java.io.FileNotFoundException: Missing file for $id at /mnt/shared/Download/note.txt"
            },
        )

        assertTrue("删除前归属有效且删除成功", res.documentDeleted)
        assertTrue("仅匹配 documentId 的独立 Missing file 可确认不存在", res.observedUriAbsent)
        assertTrue("Provider 已撤销本 run 授权且 grants 恢复基线", res.permissionReleased)
        assertEquals("不得对已自动撤销的授权再次调用 release", 0, releaseCalls)
        assertEquals(res.grantsBefore, res.grantsAfter)
        assertFalse("全流程成功后 handoff 才能删除", handoff.exists())
    }

    /**
     * 场景 6：授权比对校验读写标志（read/write flags）—— 若标志漂移必须判定 grantsMatch 为 false。
     */
    @Test
    fun testGrantFlagsMismatch_detectedAndRejected() {
        val testDir = createTestDir("grant_flags_mismatch")
        val runId = "testRunFlagsMismatch"
        val existingUri = "content://com.other.app/doc/1"
        val targetUri = "content://com.android.documents/document/target_doc"

        // 基线：existingUri 仅具有 READ 权限
        val preGrants = Task35PreGrantsSnapshot(
            runId = runId,
            capturedAtMillis = System.currentTimeMillis(),
            grants = listOf(Task35PreGrantEntry(existingUri, isRead = true, isWrite = false, persistedTime = 500L)),
        )
        preGrants.writeTo(testDir)

        val content = runContent(runId)
        val ledger = Task35CreationLedger(runId)
        ledger.registerObserved("export_uri", targetUri, content, true)
        File(testDir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))

        val handoff = File(testDir, "saf-$runId-handoff.json")
        handoff.writeText(
            JSONObject().put("runId", runId).put("uri", targetUri)
                .put("fileSha256", shaOf(content)).put("fileSize", content.size.toLong()).toString()
        )

        // 模拟：清理后 existingUri 的 isWrite 意外变成了 true（授权状态漂移）
        val afterGrants = listOf(Task35PreGrantEntry(existingUri, isRead = true, isWrite = true, persistedTime = 500L))

        // 删除前对象可读、删除后明确不存在（真实顺序，不始终抛 FileNotFoundException）
        var deleted = false
        val res = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
            documentDeleter = { deleted = true; true },
            streamOpener = {
                if (deleted) throw java.io.FileNotFoundException("deleted")
                else java.io.ByteArrayInputStream(content)
            },
            permissionReleaser = { _, _ -> },
            grantInspector = { afterGrants },
        )

        assertFalse("读写权限状态漂移时 observedUriAbsent 必须为 false", res.observedUriAbsent)
        assertNotNull("必须记录基线不符失败原因", res.failureReason)
        assertTrue("失败原因应指出与基线不符", res.failureReason!!.contains("基线不符"))
        assertTrue("状态未完全恢复时 handoff 文件必须保留", handoff.isFile)
    }

    /**
     * 场景 7：正常清理与恢复全流程，产出完全符合 evidence-contracts.json 严格规范的 __restoration。
     */
    @Test
    fun testSuccessfulCleanup_producesValidRestorationContract() {
        val testDir = createTestDir("success_cleanup")
        val runId = "testRunSuccess"
        val newUri = "content://com.android.documents/document/valid_doc"

        // 前置基线中已有其它应用的合法授权
        val otherUri = "content://com.other.app/document/other"
        val baseGrant = Task35PreGrantEntry(otherUri, isRead = true, isWrite = false, persistedTime = 500L)
        val preGrants = Task35PreGrantsSnapshot(
            runId = runId,
            capturedAtMillis = System.currentTimeMillis(),
            grants = listOf(baseGrant),
        )
        preGrants.writeTo(testDir)

        // 登记初始 ledger
        val testContent = runContent(runId)
        val testSha = Task35SafCleaner.run {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(testContent)
                .joinToString("") { "%02x".format(it) }
        }
        val ledger = Task35CreationLedger(runId)
        ledger.registerObserved("export_uri", newUri, testContent, true)
        ledger.registerObserved("uri_read_permission", newUri, null, true)
        val ledgerFile = File(testDir, "task35-ledger-$runId.json")
        ledgerFile.writeText(ledger.toJson().toString(2))

        val handoff = File(testDir, "saf-$runId-handoff.json")
        handoff.writeText(
            JSONObject()
                .put("runId", runId)
                .put("uri", newUri)
                .put("fileSha256", testSha)
                .put("fileSize", testContent.size.toLong())
                .toString()
        )

        var deleteExecuted = false
        var releaseExecuted = false
        var deleted = false

        val res = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
            documentDeleter = { uri ->
                if (uri.toString() == newUri) {
                    deleteExecuted = true
                    deleted = true
                    true
                } else false
            },
            streamOpener = {
                if (deleted) throw java.io.FileNotFoundException("document has been deleted")
                else java.io.ByteArrayInputStream(runContent(runId))
            },
            permissionReleaser = { uri, _ ->
                if (uri.toString() == newUri) {
                    releaseExecuted = true
                }
            },
            grantInspector = { listOf(baseGrant) },
        )

        assertTrue("文档真实删除应执行", deleteExecuted)
        assertTrue("授权释放应执行", releaseExecuted)
        assertTrue("文档应被确认已不存在", res.documentDeleted)
        assertTrue("物理不存在且授权恢复时 observedUriAbsent 为 true", res.observedUriAbsent)
        assertEquals("deletedUri 应与目标一致", newUri, res.deletedUri)
        assertEquals("grantsAfter 应与 grantsBefore 严格一致", res.grantsBefore, res.grantsAfter)
        assertFalse("failureReason 应为空", res.failureReason != null && res.failureReason.isNotBlank())

        // 验证写出的 task35-restoration-$runId.json 文件
        val restFile = File(testDir, "task35-restoration-$runId.json")
        assertTrue("必须产生 restoration 证据文件", restFile.isFile)
        val restJson = JSONObject(restFile.readText())
        assertEquals(runId, restJson.getString("runId"))
        assertTrue(restJson.has("grantsBefore"))
        assertTrue(restJson.has("grantsAfter"))
        assertTrue(restJson.getBoolean("observedUriAbsent"))

        // 验证 handoff 已在成功后被清理
        assertFalse("handoff 应在全流程成功后被清理", handoff.exists())
    }

    /**
     * 场景 8：调用次序验证 —— 证明冷读必须在清理前执行，提前清理会导致冷读失败。
     */
    @Test
    fun testOrderInvariance_coldReadRequiresPreCleanupState() {
        val testDir = createTestDir("order_invariance")
        val runId = "testRunOrder"
        val docUri = "content://com.android.documents/document/order_test"

        // 1. 导出后状态：handoff 存在
        val handoff = File(testDir, "saf-$runId-handoff.json")
        val content = runContent(runId)
        handoff.writeText(
            JSONObject().put("runId", runId).put("uri", docUri)
                .put("fileSha256", shaOf(content)).put("fileSize", content.size.toLong()).toString()
        )
        assertTrue("冷读前 handoff 必须存在", handoff.isFile)

        // 2. 写入账本
        val ledger = Task35CreationLedger(runId)
        ledger.registerObserved("export_uri", docUri, content, true)
        File(testDir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))

        // 模拟清理（删除前可读 → 删除后明确不存在）：
        Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), emptyList()).writeTo(testDir)
        var deleted = false
        val res = Task35SafCleaner.performCleanup(
            context = context,
            runId = runId,
            evidenceDir = testDir,
            documentDeleter = { deleted = true; true },
            streamOpener = {
                if (deleted) throw java.io.FileNotFoundException()
                else java.io.ByteArrayInputStream(content)
            },
            permissionReleaser = { _, _ -> },
            grantInspector = { emptyList() },
        )
        assertTrue("清理应真实成功", res.documentDeleted && res.observedUriAbsent)

        // 3. 清理成功后 handoff 被移除 ⇒ 冷读若排在清理之后必失败
        assertFalse("清理后 handoff 已不存在，冷读必须排在清理之前", handoff.isFile)
        // 从而证明：导出 → handoff → 冷读 → 清理恢复 的次序具有严格单向依赖性，不可颠倒。
    }

    /**
     * 场景 9：删除前**实际对象归属核验**（任务42-v3 补齐）。
     *
     * 9.1 缺 hash/size 凭证不得静默跳过；
     * 9.2 同 URI 的 uri_read_permission 条目不得充当导出对象证明（要求 export_uri）；
     * 9.3 实际对象内容被替换（SHA/size/token 不符）⇒ 零删除零释放；
     * 9.4 删除前读取失败（异常）⇒ 零删除零释放，保留凭证；
     * 9.5 原书/既有的同 URI 对象冒充（URI 在基线授权中）⇒ 零删除零释放；
     * 9.6 正常对象匹配后才进入清理（删除与释放被执行）。
     */
    @Test
    fun testDeletionGate_actualObjectOwnership() {
        val runId = "testRunOwnership"
        val targetUri = "content://com.android.documents/document/owned_doc"
        val content = runContent(runId)

        // ---- 9.1 缺 fileSize/fileSha256 凭证 ----
        run {
            val dir = createTestDir("own_missing_cred")
            Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), emptyList()).writeTo(dir)
            val ledger = Task35CreationLedger(runId)
            ledger.registerObserved("export_uri", targetUri, content, true)
            File(dir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))
            val handoff = File(dir, "saf-$runId-handoff.json")
            // 只写 uri/runId，故意缺 fileSha256 与 fileSize
            handoff.writeText(JSONObject().put("runId", runId).put("uri", targetUri).toString())

            var deleteCalled = false
            var releaseCalled = false
            val res = Task35SafCleaner.performCleanup(
                context = context, runId = runId, evidenceDir = dir,
                documentDeleter = { deleteCalled = true; true },
                streamOpener = { java.io.ByteArrayInputStream(content) },
                permissionReleaser = { _, _ -> releaseCalled = true },
                grantInspector = { emptyList() },
            )
            assertFalse("缺 hash/size 凭证必须拒绝", res.documentDeleted)
            assertFalse("拒绝时不得调用删除", deleteCalled)
            assertFalse("拒绝时不得释放授权", releaseCalled)
            assertTrue(res.failureReason!!.contains("凭证不完整"))
            assertTrue("失败必须保留 handoff 凭证", handoff.isFile)
        }

        // ---- 9.2 同 URI 只有 uri_read_permission 条目 ----
        run {
            val dir = createTestDir("own_wrong_kind")
            Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), emptyList()).writeTo(dir)
            val ledger = Task35CreationLedger(runId)
            ledger.registerObserved("uri_read_permission", targetUri, null, true)
            File(dir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))
            File(dir, "saf-$runId-handoff.json").writeText(
                JSONObject().put("runId", runId).put("uri", targetUri)
                    .put("fileSha256", shaOf(content)).put("fileSize", content.size.toLong()).toString()
            )
            var deleteCalled = false
            val res = Task35SafCleaner.performCleanup(
                context = context, runId = runId, evidenceDir = dir,
                documentDeleter = { deleteCalled = true; true },
                streamOpener = { java.io.ByteArrayInputStream(content) },
                permissionReleaser = { _, _ -> },
                grantInspector = { emptyList() },
            )
            assertFalse("权限条目不得当作导出对象证明", res.documentDeleted)
            assertFalse(deleteCalled)
            assertTrue(res.failureReason!!.contains("不得当作导出对象证明"))
        }

        // ---- 9.3 实际对象内容被替换 ----
        run {
            val dir = createTestDir("own_replaced")
            Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), emptyList()).writeTo(dir)
            val ledger = Task35CreationLedger(runId)
            ledger.registerObserved("export_uri", targetUri, content, true)
            File(dir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))
            val handoff = File(dir, "saf-$runId-handoff.json")
            handoff.writeText(
                JSONObject().put("runId", runId).put("uri", targetUri)
                    .put("fileSha256", shaOf(content)).put("fileSize", content.size.toLong()).toString()
            )
            // resolver 读到的却是另一个 run / 原书内容（缺本 run token、SHA 不符）
            val other = "other-run-content".toByteArray()
            var deleteCalled = false
            var releaseCalled = false
            val res = Task35SafCleaner.performCleanup(
                context = context, runId = runId, evidenceDir = dir,
                documentDeleter = { deleteCalled = true; true },
                streamOpener = { java.io.ByteArrayInputStream(other) },
                permissionReleaser = { _, _ -> releaseCalled = true },
                grantInspector = { emptyList() },
            )
            assertFalse("内容被替换必须拒绝", res.documentDeleted)
            assertFalse(deleteCalled)
            assertFalse(releaseCalled)
            assertTrue(res.failureReason!!.contains("归属核验失败"))
            assertTrue(handoff.isFile)
        }

        // ---- 9.4 删除前读取失败 ----
        run {
            val dir = createTestDir("own_read_fail")
            Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), emptyList()).writeTo(dir)
            val ledger = Task35CreationLedger(runId)
            ledger.registerObserved("export_uri", targetUri, content, true)
            File(dir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))
            val handoff = File(dir, "saf-$runId-handoff.json")
            handoff.writeText(
                JSONObject().put("runId", runId).put("uri", targetUri)
                    .put("fileSha256", shaOf(content)).put("fileSize", content.size.toLong()).toString()
            )
            var deleteCalled = false
            val res = Task35SafCleaner.performCleanup(
                context = context, runId = runId, evidenceDir = dir,
                documentDeleter = { deleteCalled = true; true },
                streamOpener = { throw java.io.IOException("provider read failure") },
                permissionReleaser = { _, _ -> },
                grantInspector = { emptyList() },
            )
            assertFalse("读取失败时归属 NOT_PROVEN，必须拒绝", res.documentDeleted)
            assertFalse(deleteCalled)
            assertTrue(res.failureReason!!.contains("读取目标对象异常"))
            assertTrue(handoff.isFile)
        }

        // ---- 9.5 原对象冒充（URI 已在基线授权中）----
        run {
            val dir = createTestDir("own_preexisting")
            val pre = Task35PreGrantsSnapshot(
                runId, System.currentTimeMillis(),
                listOf(Task35PreGrantEntry(targetUri, isRead = true, isWrite = false, persistedTime = 1L)),
            )
            pre.writeTo(dir)
            val ledger = Task35CreationLedger(runId)
            ledger.registerObserved("export_uri", targetUri, content, true)
            File(dir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))
            File(dir, "saf-$runId-handoff.json").writeText(
                JSONObject().put("runId", runId).put("uri", targetUri)
                    .put("fileSha256", shaOf(content)).put("fileSize", content.size.toLong()).toString()
            )
            var deleteCalled = false
            var releaseCalled = false
            val res = Task35SafCleaner.performCleanup(
                context = context, runId = runId, evidenceDir = dir,
                documentDeleter = { deleteCalled = true; true },
                streamOpener = { java.io.ByteArrayInputStream(content) },
                permissionReleaser = { _, _ -> releaseCalled = true },
                grantInspector = { listOf(Task35PreGrantEntry(targetUri, true, false, 1L)) },
            )
            assertFalse("原对象冒充必须拒绝", res.documentDeleted)
            assertFalse(deleteCalled)
            assertFalse(releaseCalled)
            assertTrue(res.failureReason!!.contains("不得当作本轮新建释放"))
        }

        // ---- 9.6 正常匹配后才进入清理 ----
        run {
            val dir = createTestDir("own_ok")
            Task35PreGrantsSnapshot(runId, System.currentTimeMillis(), emptyList()).writeTo(dir)
            val ledger = Task35CreationLedger(runId)
            ledger.registerObserved("export_uri", targetUri, content, true)
            File(dir, "task35-ledger-$runId.json").writeText(ledger.toJson().toString(2))
            val handoff = File(dir, "saf-$runId-handoff.json")
            handoff.writeText(
                JSONObject().put("runId", runId).put("uri", targetUri)
                    .put("fileSha256", shaOf(content)).put("fileSize", content.size.toLong()).toString()
            )
            var deleteCalled = false
            var releaseCalled = false
            var deleted = false
            val res = Task35SafCleaner.performCleanup(
                context = context, runId = runId, evidenceDir = dir,
                documentDeleter = { deleteCalled = true; deleted = true; true },
                streamOpener = {
                    if (deleted) throw java.io.FileNotFoundException("deleted")
                    else java.io.ByteArrayInputStream(content)
                },
                permissionReleaser = { _, _ -> releaseCalled = true },
                grantInspector = { emptyList() },
            )
            assertTrue("归属核验通过后才删除", deleteCalled)
            assertTrue("删除后释放本 run 授权", releaseCalled)
            assertTrue(res.documentDeleted)
            assertTrue(res.observedUriAbsent)
            assertFalse("成功后 handoff 应被清理", handoff.isFile)
        }
    }
}
