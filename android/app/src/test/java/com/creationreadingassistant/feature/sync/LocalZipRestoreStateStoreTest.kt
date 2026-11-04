package com.creationreadingassistant.feature.sync

import android.content.Context
import com.creationreadingassistant.feature.log.AppLog
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 锁定 [LocalZipRestoreStateStore] 的持久状态契约（N1-repair-v1）。
 *
 * 这些用例针对的是**真实风险**，不是镜像实现：
 * 1. 阶段推进后必须能在**全新实例**上读回（重启识别的唯一依据）；
 * 2. 状态文件损坏/不可读时按「未完成」保守处理，且**绝不删除任何文件**；
 * 3. 落盘失败必须上抛（调用方 fail-closed），不能静默返回一个未持久化的状态；
 * 4. 完成态（files_copied）不被误判为中断；
 * 5. 异常链（cause/suppressed）在摘要里可观察。
 */
class LocalZipRestoreStateStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        AppLog.clear()
    }

    private fun fakeContext(): Context {
        val ctx = mockk<Context>(relaxed = true)
        every { ctx.filesDir } returns filesDir
        return ctx
    }

    @Test
    fun `begin persists started stage and new instance reads it back`() {
        val ctx = fakeContext()
        val written = LocalZipRestoreStateStore().begin(ctx, "content://backup.zip", 1_700_000_000_000L)

        // 全新实例（模拟重启后的新进程）只凭磁盘即可读到同一状态。
        val readBack = LocalZipRestoreStateStore().read(ctx)
        assertNotNull("新实例必须能读到已持久化的状态", readBack)
        assertEquals(written.operationId, readBack!!.operationId)
        assertEquals(LocalZipRestoreState.STAGE_STARTED, readBack.stage)
        assertEquals("content://backup.zip", readBack.sourceLabel)
        assertFalse("started 阶段不是完成态", readBack.isComplete)
        assertFalse(readBack.isUnknown)
    }

    @Test
    fun `markStage advances stage durably and completion is distinguishable`() {
        val ctx = fakeContext()
        val store = LocalZipRestoreStateStore()
        var state = store.begin(ctx, "u", 1L)
        state = store.markStage(ctx, state, LocalZipRestoreState.STAGE_DB_COMMITTED, 2L)

        val afterDb = LocalZipRestoreStateStore().read(ctx)!!
        assertEquals(LocalZipRestoreState.STAGE_DB_COMMITTED, afterDb.stage)
        assertFalse("DB 已提交但文件未复制完，仍属未完成", afterDb.isComplete)

        state = store.markStage(ctx, state, LocalZipRestoreState.STAGE_FILES_COPIED, 3L)
        val done = LocalZipRestoreStateStore().read(ctx)!!
        assertEquals(LocalZipRestoreState.STAGE_FILES_COPIED, done.stage)
        assertTrue("files_copied 才是完成态", done.isComplete)

        store.clear(ctx)
        assertNull("清除后新实例读到 null", LocalZipRestoreStateStore().read(ctx))
    }

    @Test
    fun `missing state file reads as null`() {
        assertNull(LocalZipRestoreStateStore().read(fakeContext()))
    }

    @Test
    fun `corrupt state file is treated as unfinished and deletes nothing`() {
        val ctx = fakeContext()
        val booksDir = File(filesDir, "books/b1").also { it.mkdirs() }
        val book = File(booksDir, "content.txt").also { it.writeText("USER BYTES") }
        File(filesDir, LocalZipRestoreStateStore.STATE_FILE_NAME).writeText("{ not json")

        val state = LocalZipRestoreStateStore().read(ctx)

        assertNotNull("损坏状态必须保守上报，不能当作「从未有过操作」", state)
        assertTrue(state!!.isUnknown)
        assertFalse("unknown 不是完成态", state.isComplete)
        assertTrue("损坏状态不得导致任何文件被删除", book.isFile)
        assertEquals("USER BYTES", book.readText())
    }

    @Test
    fun `state file missing required fields is treated as unfinished`() {
        val ctx = fakeContext()
        File(filesDir, LocalZipRestoreStateStore.STATE_FILE_NAME).writeText("""{"stage":""}""")

        val state = LocalZipRestoreStateStore().read(ctx)

        assertNotNull(state)
        assertTrue(state!!.isUnknown)
    }

    @Test
    fun `begin fails closed when state path cannot be written`() {
        val ctx = fakeContext()
        // 确定性制造落盘失败：状态文件路径被同名目录占用（与 S14 journal 用例同款手法）。
        File(filesDir, LocalZipRestoreStateStore.STATE_FILE_NAME).mkdirs()

        val result = runCatching { LocalZipRestoreStateStore().begin(ctx, "u", 1L) }

        assertTrue("状态持久化失败必须上抛（fail-closed）", result.isFailure)
    }

    @Test
    fun `markStage fails closed when state path cannot be written`() {
        val ctx = fakeContext()
        val store = LocalZipRestoreStateStore()
        val state = store.begin(ctx, "u", 1L)
        store.clear(ctx)
        File(filesDir, LocalZipRestoreStateStore.STATE_FILE_NAME).mkdirs()

        val result = runCatching {
            store.markStage(ctx, state, LocalZipRestoreState.STAGE_DB_COMMITTED, 2L)
        }

        assertTrue("阶段持久化失败必须上抛，不能留下未记录的阶段推进", result.isFailure)
    }

    @Test
    fun `recordFailure keeps stage and surfaces cause chain`() {
        val ctx = fakeContext()
        val store = LocalZipRestoreStateStore()
        var state = store.begin(ctx, "u", 1L)
        state = store.markStage(ctx, state, LocalZipRestoreState.STAGE_DB_COMMITTED, 2L)

        val root = java.io.IOException("磁盘写入失败")
        val failure = IllegalStateException("整包恢复失败", root)
        failure.addSuppressed(IllegalStateException("临时目录清理失败"))
        val recorded = store.recordFailure(ctx, state, failure, 3L)

        assertNotNull(recorded)
        val readBack = LocalZipRestoreStateStore().read(ctx)!!
        assertEquals(
            "记录失败不得把已提交 DB 的阶段降级（否则重启后无法判断现场）",
            LocalZipRestoreState.STAGE_DB_COMMITTED,
            readBack.stage,
        )
        val summary = readBack.failureSummary.orEmpty()
        assertTrue("摘要必须含主异常：$summary", summary.contains("IllegalStateException"))
        assertTrue("摘要必须含 cause 链：$summary", summary.contains("磁盘写入失败"))
        assertTrue("摘要必须含 suppressed：$summary", summary.contains("临时目录清理失败"))
    }

    @Test
    fun `describe never drops the primary exception message`() {
        val failure = IllegalStateException("主异常", java.io.IOException("根因"))
        val text = LocalZipRestoreStateStore().describe(failure)
        assertTrue(text.contains("主异常"))
        assertTrue(text.contains("根因"))
    }

    @Test
    fun `clear on absent file is a no-op`() {
        val ctx = fakeContext()
        LocalZipRestoreStateStore().clear(ctx)
        assertNull(LocalZipRestoreStateStore().read(ctx))
    }

    @Test
    fun `markDbCommitted records fingerprint and targets and new instance reads them back`() {
        val ctx = fakeContext()
        val store = LocalZipRestoreStateStore()
        val initial = store.begin(ctx, "content://backup.zip", 1000L)

        val targets = mapOf("b1/content.txt" to "hash-b1", "epub/b2.epub" to "hash-b2")
        store.markDbCommitted(ctx, initial, "fp-abc-123", targets, 2000L)

        val readBack = LocalZipRestoreStateStore().read(ctx)
        assertNotNull(readBack)
        assertEquals("fp-abc-123", readBack!!.backupFingerprint)
        assertEquals(LocalZipRestoreState.STAGE_DB_COMMITTED, readBack.stage)
        assertEquals(2, readBack.pendingTargets.size)
        assertEquals("hash-b1", readBack.pendingTargets["b1/content.txt"])
        assertEquals("hash-b2", readBack.pendingTargets["epub/b2.epub"])
    }

    @Test
    fun `begin preserves unresolved targets from previous unfinished restore when fingerprint changes`() {
        val ctx = fakeContext()
        val store = LocalZipRestoreStateStore()
        val stateA = store.begin(ctx, "content://backup-a.zip", 1000L, backupFingerprint = "fp-a")
        store.markDbCommitted(ctx, stateA, "fp-a", mapOf("a/book.txt" to "hash-a"), 1500L)

        // 用户在 A 未完成时选择另一份备份 B
        val stateB = store.begin(ctx, "content://backup-b.zip", 2000L, backupFingerprint = "fp-b")

        val readBack = LocalZipRestoreStateStore().read(ctx)
        assertNotNull(readBack)
        assertEquals("stateB 必须继承 A 的未完成目标作为 unresolvedTargets 守护", 1, readBack!!.unresolvedTargets.size)
        assertTrue(readBack.unresolvedTargets.containsKey("a/book.txt"))
        assertFalse("由于 unresolvedTargets 非空，哪怕处于 STAGE_FILES_COPIED 也不算完成", readBack.copy(stage = LocalZipRestoreState.STAGE_FILES_COPIED).isComplete)
    }

    @Test
    fun `clear refuses to delete state file when targets are not on disk`() {
        val ctx = fakeContext()
        val store = LocalZipRestoreStateStore()
        val state = store.begin(ctx, "content://backup.zip", 1000L)
        val booksDir = File(filesDir, "books").also { it.mkdirs() }

        // 设置待修目标
        store.markDbCommitted(ctx, state, "fp-1", mapOf("missing.txt" to "hash-missing"), 1500L)
        val committed = store.read(ctx)!!

        // 尝试 clear：由于 missing.txt 不存在，clear 绝不能删除状态文件
        store.clear(ctx, committed.operationId, booksDir)
        assertNotNull("未完成目标在磁盘缺失时，clear 绝不得删除状态文件", store.read(ctx))

        // 补全文件
        File(booksDir, "missing.txt").writeText("100 bytes")
        // 当推进到完成态后
        val finished = store.markStage(ctx, committed.copy(pendingTargets = emptyMap()), LocalZipRestoreState.STAGE_FILES_COPIED, 2000L)
        store.clear(ctx, finished.operationId, booksDir)
        assertNull("目标均在磁盘就位且完成时，clear 成功删除", store.read(ctx))
    }

    @Test
    fun `clear refuses to delete state file when operationId mismatches`() {
        val ctx = fakeContext()
        val store = LocalZipRestoreStateStore()
        val state = store.begin(ctx, "content://backup.zip", 1000L)
        val stateFile = File(filesDir, LocalZipRestoreStateStore.STATE_FILE_NAME)
        assertTrue(stateFile.isFile)

        store.clear(ctx, "different-op-id")
        assertTrue("operationId 不匹配时绝不能删除状态文件", stateFile.isFile)

        store.clear(ctx, state.operationId)
        assertFalse("operationId 匹配时允许删除", stateFile.exists())
    }

    /**
     * N1-v4 路径 3 (P2) / N1-repair-v5 强化：从真实 v2 长度格式状态 fixture 启动：
     * 不能把长度字符串当 SHA，不能静默删掉旧义务。
     *
     * v5 合同：旧长度格式无法证明正文身份（长度相同不代表内容相同），
     * matchesTarget 对 legacy 长度一律返回 false，clear 拒绝清除，
     * 保留阻断直到可确认来源的重试建立 SHA 证明为止。
     */
    @Test
    fun `N1v4_P2_legacyLengthFormatStateMigrationDoesNotTreatLengthAsSha`() {
        val ctx = fakeContext()
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val target = File(booksDir, "b1/content.txt").also {
            it.parentFile!!.mkdirs()
            it.writeText("12345678901") // 恰好 11 字节
        }

        // 预置真实 v2 状态文件 fixture：pendingTargets 中的值为长度数字 "11"
        val v2FixtureJson = """
            {
              "operationId": "zip-v2-legacy-123",
              "sourceLabel": "content://backup.zip",
              "backupFingerprint": "fp-v2",
              "stage": "files_copied",
              "startedAtMillis": 1000,
              "updatedAtMillis": 2000,
              "pendingTargets": {
                "b1/content.txt": "11"
              },
              "unresolvedTargets": {}
            }
        """.trimIndent()
        File(filesDir, LocalZipRestoreStateStore.STATE_FILE_NAME).writeText(v2FixtureJson)

        val store = LocalZipRestoreStateStore()
        val state = store.read(ctx)
        assertNotNull("必须成功读取 v2 fixture 状态", state)
        assertEquals("保留旧义务，不能静默删掉", 1, state!!.pendingTargets.size)
        assertEquals("值为 11", "11", state.pendingTargets["b1/content.txt"])

        // N1-repair-v5：旧长度格式不能证明正文身份，即使磁盘文件长度匹配也必须阻断 clear。
        // 状态保留，等待可确认来源的重试建立 SHA 证明后才能收敛。
        store.clear(ctx, "zip-v2-legacy-123", booksDir)
        val after = store.read(ctx)
        assertNotNull("旧长度格式无法证明正文身份，clear 必须拒绝清除并保留阻断", after)
        assertEquals("旧义务保留", 1, after!!.pendingTargets.size)
        assertEquals("旧义务值保留", "11", after.pendingTargets["b1/content.txt"])
    }

    /**
     * N1-repair-v5 反例2（P1）：v2 旧状态目标值为长度 "11"，磁盘旧正文也为 11 字节但内容不同。
     *
     * v4 缺陷：matchesTarget 对 legacy 长度只比 file.length() == expectedLen，
     * 内容不同但长度相同也判完成，clear 误清状态。
     * v5 修复：旧长度格式无法证明正文身份，matchesTarget 一律返回 false，
     * clear 拒绝清除，保留阻断并准确报告。
     */
    @Test
    fun `N1v5_P1_legacyLengthCannotProveContentIdentityAndBlocksClear`() {
        val ctx = fakeContext()
        val booksDir = File(filesDir, "books").also { it.mkdirs() }
        val target = File(booksDir, "b1/content.txt").also {
            it.parentFile!!.mkdirs()
            // 11 字节但内容与原来源不同——长度相同不能证明正文身份
            it.writeText("XXXXXXXXXXX") // 恰好 11 字节，但内容不同
        }

        // 预置真实 v2 状态文件 fixture：pendingTargets 中的值为长度数字 "11"
        val v2FixtureJson = """
            {
              "operationId": "zip-v2-legacy-456",
              "sourceLabel": "content://backup.zip",
              "backupFingerprint": "fp-v2",
              "stage": "files_copied",
              "startedAtMillis": 1000,
              "updatedAtMillis": 2000,
              "pendingTargets": {
                "b1/content.txt": "11"
              },
              "unresolvedTargets": {}
            }
        """.trimIndent()
        File(filesDir, LocalZipRestoreStateStore.STATE_FILE_NAME).writeText(v2FixtureJson)

        val store = LocalZipRestoreStateStore()
        val state = store.read(ctx)
        assertNotNull("必须成功读取 v2 fixture 状态", state)
        assertEquals("值为 11", "11", state!!.pendingTargets["b1/content.txt"])

        // 核心断言：旧长度格式无法证明正文身份，clear 必须拒绝清除！
        store.clear(ctx, "zip-v2-legacy-456", booksDir)
        assertNotNull("旧长度不能证明内容一致，状态必须保留阻断", store.read(ctx))
    }
}
