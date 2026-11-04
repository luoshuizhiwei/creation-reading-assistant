package com.creationreadingassistant.feature.sync

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import android.os.Process
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.domain.model.SyncEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * N1 真实 Room / 跨进程恢复验收驱动（**J3-n1-fix-accept-v2**）。
 *
 * ## 这个文件是什么
 *
 * 它是**驱动**，不是自动跑完的测试套件。真实进程终止必须由设备执行者在观察到信号后
 * 用 `am force-stop` 完成——本文件只用既有测试接缝（[LocalZipBackup.fileCopyOperation]）
 * 在「DB 计划已持久化、正文尚未完成」的位置**写信号 + 有上限等待**，
 * **不抛 IOException 冒充真实终止**，不改生产代码，不延长产品窗口。
 *
 * ## 与 JVM 用例的分工
 *
 * `app/src/test/.../LocalZipBackupTest.kt`（v7 已验收版本）用 mockk 受控 DAO；
 * 本文件用**真实 AppDatabase/Room + 真实 JsonBridge + 真实 LocalZipBackup/StateStore**，
 * 因此能覆盖 JVM 夹具覆盖不到的两件事：真实 Room 版本分类（equal/accepted/strictlyNewer）
 * 与**真实导出的 ZIP 字节**（JVM 夹具里 `bookDao.observeAllActive()` 未桩，导出必然失败）。
 *
 * ## 沙箱与隔离（必须与代码同步维护）
 *
 * - **UID**：本 APK 只装到 `com.creationreadingassistant.qahostn1`（`.qahostn1` 后缀）。
 * - **场景级隔离（v2 修订）**：**每个场景一份独立沙箱目录与独立 Room 库**
 *   （`<filesDir>/n1-sandbox/<scenario>/{files,cache}`、`n1_sandbox_restore_<scenario>.db`）。
 *   同一场景**内部**跨进程状态（DB 行、正文、`zip-restore-state.json`、逐文件 journal）
 *   稳定留存；场景之间互不可见，**上一场景的未完成义务在结构上不可能触发下一场景的导出门禁**。
 * - **内部沙箱**：`<filesDir>/n1-sandbox/`（`n1-` 前缀），夹具与证据都放这里；
 *   `LocalZipBackup` 遍历的 `books/` 被重定向到场景沙箱内，
 *   **绝不枚举也不写入沙箱包的既有 `files/books/`**。
 * - **数据库**：每场景唯一名（`n1_sandbox_restore_<scenario>.db`），与任何正式库名不重合；
 *   由 `n1_99_cleanupSandbox` 删除。
 * - **SecurePrefs**：[SandboxContext] 覆写 `getSharedPreferences` 返回内存实现、
 *   并把 `deleteSharedPreferences` 置为 no-op——`SecurePrefs` 的降级重试会删除同名
 *   prefs 文件，不覆写会删掉沙箱包真实的 `cra_device_identity`。
 * - **信号可读性**：信号同时写内部沙箱与 `getExternalFilesDir(null)/n1-sandbox/<scenario>/`
 *   （后者 shell 可读，便于执行者 `adb shell cat` 观察，无需 root）。
 * - **删除证明写在被删树之外（v2 修订）**：取路径的 getter **一律无副作用**
 *   （`*Path()` 系列只拼路径、不 `mkdirs`）；创建目录只走 `ensure*()`。
 *   `n1_00` / `n1_99` 的删除证据只写**外部证据目录**（`n1-sandbox/evidence/`），
 *   它是被删沙箱树的**兄弟**，不在被删范围内——否则写证据会把刚删掉的目录重建出来。
 *   若为证据新建目录，其路径与被删除的测试数据严格区分（前者在 `n1-sandbox/evidence`，
 *   后者在 `n1-sandbox/<scenario>`）。
 * - **残留设备侧触碰（如实登记）**：`SecurePrefs` 会用 `MasterKey` 在 AndroidKeyStore
 *   get-or-create 沙箱包的主密钥别名（幂等、非用户数据）；`androidx.startup` 仍会创建
 *   `androidx.work.workdb`。两者换 UID 无法消除，设备批须纳入基线与恢复对账。
 *
 * ## 阶段与方法顺序（每个方法可单独 `-e class X#method` 调用）
 *
 * | 顺序 | 方法 | 性质 |
 * |---|---|---|
 * | 0 | `n1_00_cleanupBeforeSeed` | 准备：清空全部场景沙箱与测试库（幂等） |
 * | 1 | `n1_01_seedScenario1_equal` | 场景 1 夹具（equal） |
 * | 1 | `n1_02_armKillScenario1` | **须被真实强停**；未终止即 FAIL |
 * | 1 | `n1_03_readOnlyAfterKillScenario1` | 新进程纯读（校验真实终止事实） |
 * | 1 | `n1_04_retryBScenario1` | B（第一次）必须被阻断 |
 * | 1 | `n1_05_retryBAgainScenario1` | B（第二次，同指纹）必须被阻断 |
 * | 1 | `n1_06_reselectAScenario1` | 重选原 A 收敛 + 真实导出 |
 * | 2 | `n1_10_seedScenario2_equal` | 场景 2 夹具（equal） |
 * | 2 | `n1_11_armKillScenario2` | **须被真实强停** |
 * | 2 | `n1_12_readOnlyAfterKillScenario2` | 新进程纯读 |
 * | 2 | `n1_13_importBadZipScenario2` | 坏 ZIP 被拒且留下空指纹现场 |
 * | 2 | `n1_14_retryBScenario2` | 空指纹不得授权 B 的冲突正文 |
 * | 3 | `n1_20_seedScenario3_legacy` | 场景 3 夹具（legacy 长度义务） |
 * | 3 | `n1_21_importBScenario3` | B 必须被阻断 |
 * | 3 | `n1_22_importBAgainScenario3` | 第二次 B 不得把长度义务替成 B 的 SHA |
 * | 4 | `n1_30_seedScenario4_accepted` | 场景 4 夹具（accepted，本地 rev 较低） |
 * | 4 | `n1_31_armKillScenario4` | **须被真实强停** |
 * | 4 | `n1_32_readOnlyAfterKillScenario4` | 新进程纯读（A revision=2） |
 * | 4 | `n1_33_importBScenario4` | B（accepted，rev 3）不得写入冲突正文 |
 * | 4 | `n1_34_importBAgainScenario4` | 第二次 B 仍不得写入 |
 * | 5 | `n1_40_seedScenario5_strictlyNewer` | 场景 5 夹具（strictlyNewer） |
 * | 5 | `n1_41_importBScenario5` | 本地较新正文与版本不被覆盖 |
 * | 6 | `n1_50_exportGateAndRealZip` | 固定预期：场景 1 正向真实导出 + 场景 3/4 反向精确阻断 |
 * | 7 | `n1_99_cleanupSandbox` | 收尾：删除沙箱与测试库并证明 |
 *
 * 带「须被真实强停」的方法（`n1_02_*`、`n1_11_*`、`n1_31_*`）：
 *
 * 1. 开始前清理旧信号与 continue 标记，生成本次唯一 `runId`；
 * 2. 接缝写信号（含 `runId` / `scenario` / 实际 `pid` / 新 `timestampMillis`）后**有上限等待**；
 * 3. 执行者核对 runId、scenario、时间戳新鲜度与 `pidof` 后 `am force-stop` 本包；
 * 4. **被真实终止时本方法不会返回**——进程已消失，JUnit 不会为它记 PASS；
 * 5. 若未被终止（等待超时或被 continue 标记放行），写「未终止标记」并**主动 FAIL**：
 *    **arm 正常返回或超时属于无效中断，不得记 PASS**。
 *
 * 「真实终止」的判据（由 `n1_03` / `n1_12` / `n1_32` 在**新进程**里核对）：
 * 信号文件存在 **且**「未终止标记」不存在 **且** 当前 PID ≠ 信号里的 PID
 * **且** 状态停在 `db_committed`。四条同时成立才算真实终止；
 * 执行者另需在设备侧确认原 PID 已消失、`am instrument` 已结束。
 *
 * ## 证据格式
 *
 * 本驱动写出的都是 **JSON 证据**（`n1-sandbox/evidence/<tag>.json`，内外各一份），
 * **不是 instrumentation XML**；instrumentation 结果 XML 由 `am instrument` 侧产出，
 * 两者不混称。每次运行使用独立 tag 文件名，不覆盖旧失败日志。
 *
 * ## 状态
 *
 * **本任务只编译，不运行任何设备方法。** 全部方法在本轮均为 NOT RUN。
 */
@RunWith(AndroidJUnit4::class)
class LocalZipRestoreAcceptanceTest {

    // ── 沙箱路径：getter 一律无副作用（v2 修订）──────────────────────────────
    //
    // 取路径与创建目录严格分开：`*Path()` 只拼 File，不 mkdirs；只有 `ensure*()` 会创建。
    // 删除断言因此不可能因「读一次路径」而把被删目录重建出来。

    private val realContext: Context get() = ApplicationProvider.getApplicationContext()

    /** 沙箱根**路径**（无副作用）。 */
    private fun sandboxRootPath(): File = File(realContext.filesDir, SANDBOX_DIR)

    /** 外部沙箱根**路径**（无副作用；外部存储不可用时返回 null）。 */
    private fun externalSandboxRootPath(): File? =
        realContext.getExternalFilesDir(null)?.let { File(it, SANDBOX_DIR) }

    /** 场景沙箱**路径**（无副作用）。 */
    private fun scenarioDirPath(scenario: String): File = File(sandboxRootPath(), scenario)

    /** 外部场景沙箱**路径**（无副作用）。 */
    private fun externalScenarioDirPath(scenario: String): File? =
        externalSandboxRootPath()?.let { File(it, scenario) }

    /** 被删沙箱树**之外**的证据目录（外部证据目录的兄弟位置，不随沙箱删除）。 */
    private fun externalEvidenceDirPath(): File? =
        externalSandboxRootPath()?.let { File(it, "evidence") }

    /** 沙箱树**之内**的证据目录（随沙箱一起删除，仅供沙箱内自证）。 */
    private fun internalEvidenceDirPath(): File = File(sandboxRootPath(), "evidence")

    // ── 创建目录：只有这些会 mkdirs ──────────────────────────────────────────

    private fun ensureDir(dir: File): File = dir.also { it.mkdirs() }

    private fun ensureScenarioDir(scenario: String): File = ensureDir(scenarioDirPath(scenario))

    private fun ensureExternalScenarioDir(scenario: String): File? =
        externalScenarioDirPath(scenario)?.let { ensureDir(it) }

    private fun scenarioFilesDir(scenario: String): File = ensureDir(File(ensureScenarioDir(scenario), "files"))

    private fun scenarioCacheDir(scenario: String): File = ensureDir(File(ensureScenarioDir(scenario), "cache"))

    /** 传给 LocalZipBackup / JsonBridge 的沙箱上下文（filesDir/cacheDir 重定向 + 内存 prefs）。 */
    private fun sandboxContext(scenario: String): Context =
        SandboxContext(realContext, scenarioDirPath(scenario))

    private fun booksDir(scenario: String): File = File(scenarioFilesDir(scenario), "books")

    private fun bookFile(scenario: String, bookId: String): File = File(booksDir(scenario), relOf(bookId))

    private fun journalFile(scenario: String): File = File(booksDir(scenario), JOURNAL_NAME)

    private fun signalFile(scenario: String): File = File(ensureScenarioDir(scenario), SIGNAL_NAME)

    private fun externalSignalFile(scenario: String): File? =
        externalScenarioDirPath(scenario)?.let { File(ensureDir(it), SIGNAL_NAME) }

    /** 未被真实终止时写下的标记（存在于外部，shell 可读）。 */
    private fun armNotTerminatedFile(scenario: String): File? =
        externalScenarioDirPath(scenario)?.let { File(it, ARM_NOT_TERMINATED_NAME) }

    private fun continueFlagFile(scenario: String): File? =
        externalScenarioDirPath(scenario)?.let { File(it, CONTINUE_FLAG_NAME) }

    private fun runIdFile(scenario: String): File? =
        externalScenarioDirPath(scenario)?.let { File(it, RUN_ID_NAME) }

    // ── 数据库（场景级唯一名，跨仪器调用持久）──────────────────────────────

    private fun dbName(scenario: String): String = "n1_sandbox_restore_$scenario.db"

    private fun dbFiles(scenario: String): List<File> {
        val base = realContext.getDatabasePath(dbName(scenario))
        return listOf(base, File(base.path + "-wal"), File(base.path + "-shm"), File(base.path + "-journal"))
    }

    private fun openDb(scenario: String): AppDatabase = Room.databaseBuilder(
        realContext,
        AppDatabase::class.java,
        dbName(scenario),
    ).allowMainThreadQueries().build()

    /**
     * 纯读阶段的前置：**测试库必须已存在**。
     * 不允许「新建空库 → 读不到东西 → 返回成功」——那会把「夹具没建立」伪装成通过。
     */
    private fun requireExistingDb(scenario: String) {
        val base = dbFiles(scenario).first()
        if (!base.isFile) {
            fail(
                "场景 $scenario 的测试库不存在（${base.absolutePath}）：" +
                    "上一阶段的夹具或强停没有建立现场，本阶段不得据此判定任何结论",
            )
        }
    }

    private fun buildBridge(db: AppDatabase): JsonBridge = JsonBridge(
        bookDao = db.bookDao(),
        inspirationDao = db.inspirationDao(),
        progressDao = db.readingProgressDao(),
        sessionDao = db.readingSessionDao(),
        noteDao = db.noteDao(),
        highlightDao = db.highlightDao(),
        tagDao = db.tagDao(),
        categoryDao = db.categoryDao(),
        shelfDao = db.shelfDao(),
        bookTagDao = db.bookTagDao(),
        bookCategoryDao = db.bookCategoryDao(),
        shelfBookDao = db.shelfBookDao(),
        chapterReadDao = db.chapterReadDao(),
        ioDispatcher = Dispatchers.Unconfined,
        sourceRefDao = db.librarySourceRefDao(),
        textRuleDao = db.readerTextRuleDao(),
        correctionDao = db.readerCorrectionDao(),
    )

    // =====================================================================
    // 0. 准备
    // =====================================================================

    /**
     * 幂等清空**全部场景**沙箱与测试库（每次设备批开始前跑一次）。
     *
     * 删除证明只写**外部证据目录**（被删树之外）；`*Path()` getter 无副作用，
     * 因此 `assertFalse(...exists())` 不会被自己重建的目录破坏。
     */
    @Test
    fun n1_00_cleanupBeforeSeed() {
        val root = sandboxRootPath()
        val externalRoot = externalSandboxRootPath()

        root.deleteRecursively()
        ALL_SCENARIOS.forEach { scenario ->
            externalScenarioDirPath(scenario)?.deleteRecursively()
            dbFiles(scenario).forEach { runCatching { it.delete() } }
        }

        assertFalse("沙箱根必须已删除（未被 getter 重建）", root.exists())
        val dbLeft = ALL_SCENARIOS.flatMap { dbFiles(it) }.filter { it.exists() }
        assertTrue("全部场景测试库必须已删除：$dbLeft", dbLeft.isEmpty())

        // 删除证明写在被删树之外：externalEvidenceDirPath() 是 n1-sandbox 的兄弟子树，不在删除范围内
        writeEvidenceExternalOnly("n1_00_cleanupBeforeSeed", buildJsonObject {
            put("sandboxRootPath", root.absolutePath)
            put("sandboxRootExistsAfterDelete", root.exists())
            put("externalSandboxRootPath", externalRoot?.absolutePath.orEmpty())
            put("externalEvidenceDir", externalEvidenceDirPath()?.absolutePath.orEmpty())
            put("deletedScenarios", buildJsonArray { ALL_SCENARIOS.forEach { add(it) } })
            put("dbFilesRemaining", dbLeft.size)
            put("note", "本证据只写外部证据目录；该目录不在被删沙箱树内，不会重建沙箱")
        })
    }

    // =====================================================================
    // 场景 1：A→B→B→A（equal 分类）
    // =====================================================================

    /**
     * 准备场景 1 夹具：受控书行（本地 revision == 远端 → **equal**）、A/B/坏 ZIP、受控正文。
     * **夹具准备不算产品通过。**
     */
    @Test
    fun n1_01_seedScenario1_equal() = runBlocking {
        seedScenario(
            scenario = S1,
            bookId = BOOK_S1,
            localRevision = REMOTE_REVISION,   // == 远端 → equal
            localContent = ORIGINAL_CONTENT,
        )
        writeEvidence("n1_01_seedScenario1_equal", S1, buildJsonObject {
            put("scenario", S1)
            put("bookId", BOOK_S1)
            put("expectedClassification", "equal")
            put("localRevision", REMOTE_REVISION)
            put("remoteRevision", REMOTE_REVISION)
            put("localContentSha256", sha256(ORIGINAL_CONTENT.toByteArray()))
            put("aContentSha256", sha256(ORIGINAL_A_CONTENT.toByteArray()))
            put("bContentSha256", sha256(CONFLICT_B_CONTENT.toByteArray()))
            put("aZipSha256", sha256(File(scenarioCacheDir(S1), zipName(S1, "A")).readBytes()))
            put("bZipSha256", sha256(File(scenarioCacheDir(S1), zipName(S1, "B")).readBytes()))
            put("sandboxDir", scenarioDirPath(S1).absolutePath)
            put("dbName", dbName(S1))
        })
    }

    /**
     * 真实 `importZip(A)` + 接缝信号 + 有上限等待。
     * 执行者观察到信号后必须 `am force-stop` 本包；未被终止即**主动 FAIL**（无效中断，不记 PASS）。
     */
    @Test
    fun n1_02_armKillScenario1() = runBlocking {
        armAndImportWithKillWindow(S1, BOOK_S1)
    }

    /** 新进程纯读：不重新 seed、不重建缺失正文、不重写中断状态；并核对真实终止事实。 */
    @Test
    fun n1_03_readOnlyAfterKillScenario1() = runBlocking {
        readOnlyAfterKill(S1, BOOK_S1, expectedRevision = REMOTE_REVISION)
    }

    /** 重试 B（第一次）：不得写入冲突正文、不得消除 A 义务、不得放行导出。 */
    @Test
    fun n1_04_retryBScenario1() = runBlocking {
        importScenarioZip(S1, "B")
        assertConflictingBBlocked(S1, BOOK_S1, "第一次B")
    }

    /** 重试 B（第二次，同一份 B）：v5 曾在此经同指纹分支绕过。 */
    @Test
    fun n1_05_retryBAgainScenario1() = runBlocking {
        importScenarioZip(S1, "B")
        assertConflictingBBlocked(S1, BOOK_S1, "第二次B")
    }

    /** 正向恢复：重新选择匹配义务的原 A → 补齐正文、状态清除、导出真实产出 ZIP 且内容可比对。 */
    @Test
    fun n1_06_reselectAScenario1() = runBlocking {
        requireRealTermination(S1)
        importScenarioZip(S1, "A")
        val dst = bookFile(S1, BOOK_S1)
        assertEquals("重选原 A 必须补齐正文", ORIGINAL_A_CONTENT, dst.readText())
        assertNull("A 义务收敛后状态必须清除", stateStore().read(sandboxContext(S1)))
        val zip = exportToFile(S1, "s1-after-converge")
        assertTrue("收敛后导出必须真实产出 ZIP", zip.isFile && zip.length() > 0)
        writeEvidence("n1_06_reselectAScenario1", S1, buildJsonObject {
            put("contentSha256", sha256(dst.readBytes()))
            put("stateCleared", true)
            put("exportZipSha256", sha256(zip.readBytes()))
            put("exportZipEntries", buildJsonArray { zipEntries(zip).sorted().forEach { add(it) } })
        })
    }

    // =====================================================================
    // 场景 2：A→缺 backup.json 的坏 ZIP→B（空指纹不得解除保护）
    // =====================================================================

    @Test
    fun n1_10_seedScenario2_equal() = runBlocking {
        seedScenario(
            scenario = S2,
            bookId = BOOK_S2,
            localRevision = REMOTE_REVISION,
            localContent = ORIGINAL_CONTENT,
        )
        writeEvidence("n1_10_seedScenario2_equal", S2, buildJsonObject {
            put("scenario", S2)
            put("bookId", BOOK_S2)
            put("expectedClassification", "equal")
            put("badZipHasBackupJson", false)
            put("sandboxDir", scenarioDirPath(S2).absolutePath)
            put("dbName", dbName(S2))
        })
    }

    @Test
    fun n1_11_armKillScenario2() = runBlocking {
        armAndImportWithKillWindow(S2, BOOK_S2)
    }

    @Test
    fun n1_12_readOnlyAfterKillScenario2() = runBlocking {
        readOnlyAfterKill(S2, BOOK_S2, expectedRevision = REMOTE_REVISION)
    }

    /** 坏 ZIP：被拒绝，且 `begin` 已用**空指纹**接管前序义务。 */
    @Test
    fun n1_13_importBadZipScenario2() = runBlocking {
        requireRealTermination(S2)
        val failure = runCatching { importZipFile(S2, File(scenarioCacheDir(S2), zipName(S2, "BAD"))) }
        assertTrue("缺 backup.json 的 ZIP 必须被拒绝", failure.isFailure)
        val state = stateStore().read(sandboxContext(S2))
        assertNotNull("坏 ZIP 后状态必须仍在", state)
        assertTrue("坏 ZIP 必须留下空指纹现场", state!!.backupFingerprint.isEmpty())
        assertEquals(
            "坏 ZIP 后 A 义务必须原样保留",
            sha256(ORIGINAL_A_CONTENT.toByteArray()),
            obligationOf(state, relOf(BOOK_S2)),
        )
        writeEvidence("n1_13_importBadZipScenario2", S2, buildJsonObject {
            put("rejected", true)
            put("failureMessage", failure.exceptionOrNull()?.message.orEmpty())
            put("stateBackupFingerprint", state.backupFingerprint)
            put("stateStage", state.stage)
            put("obligation", obligationOf(state, relOf(BOOK_S2)).orEmpty())
        })
    }

    /** 空指纹不得授权写入 B 的冲突正文。 */
    @Test
    fun n1_14_retryBScenario2() = runBlocking {
        importScenarioZip(S2, "B")
        assertConflictingBBlocked(S2, BOOK_S2, "空指纹后的B")
    }

    // =====================================================================
    // 场景 3：legacy 长度义务→B→B（不得把长度义务替成 B 的 SHA）
    // =====================================================================

    /** 播种 legacy 长度义务（非 64 位数字 → `isLegacyLength`）。夹具准备不算产品通过。 */
    @Test
    fun n1_20_seedScenario3_legacy() = runBlocking {
        seedScenario(
            scenario = S3,
            bookId = BOOK_S3,
            localRevision = REMOTE_REVISION,
            localContent = ORIGINAL_CONTENT,
        )
        val store = stateStore()
        val ctx = sandboxContext(S3)
        assertNull("场景 3 沙箱必须自始无状态（场景级隔离）", store.read(ctx))
        val seeded = store.begin(ctx, "n1-legacy-seed", System.currentTimeMillis())
        store.markDbCommitted(
            context = ctx,
            state = seeded,
            backupFingerprint = "",
            pendingTargets = mapOf(relOf(BOOK_S3) to LEGACY_LENGTH_OBLIGATION),
            nowMillis = System.currentTimeMillis(),
        )
        val state = store.read(ctx)
        assertEquals(
            "legacy 长度义务必须落盘",
            LEGACY_LENGTH_OBLIGATION,
            obligationOf(state, relOf(BOOK_S3)),
        )
        writeEvidence("n1_20_seedScenario3_legacy", S3, buildJsonObject {
            put("scenario", S3)
            put("bookId", BOOK_S3)
            put("legacyObligation", LEGACY_LENGTH_OBLIGATION)
            put("stateStage", state?.stage.orEmpty())
        })
    }

    @Test
    fun n1_21_importBScenario3() = runBlocking {
        importScenarioZip(S3, "B")
        assertConflictingBBlocked(S3, BOOK_S3, "legacy后的第一次B")
    }

    /** 第二次 B：不得把未知来源的长度义务替成 B 的 SHA 并清除。 */
    @Test
    fun n1_22_importBAgainScenario3() = runBlocking {
        importScenarioZip(S3, "B")
        assertConflictingBBlocked(S3, BOOK_S3, "legacy后的第二次B")
        val state = stateStore().read(sandboxContext(S3))
        assertEquals(
            "legacy 长度义务不得被 B 的 SHA 替换",
            LEGACY_LENGTH_OBLIGATION,
            obligationOf(state, relOf(BOOK_S3)),
        )
    }

    // =====================================================================
    // 场景 4：accepted（本地 revision 较低）+ 冲突义务，跨进程四段
    // =====================================================================

    @Test
    fun n1_30_seedScenario4_accepted() = runBlocking {
        seedScenario(
            scenario = S4,
            bookId = BOOK_S4,
            localRevision = REMOTE_REVISION - 1,   // < A 的远端版本 → A 走 accepted
            localContent = ORIGINAL_CONTENT,
            bRevision = REMOTE_REVISION + 1,       // B 再高一级 → B 仍走 accepted
        )
        writeEvidence("n1_30_seedScenario4_accepted", S4, buildJsonObject {
            put("scenario", S4)
            put("bookId", BOOK_S4)
            put("expectedClassification", "accepted")
            put("localRevision", REMOTE_REVISION - 1)
            put("aRemoteRevision", REMOTE_REVISION)
            put("bRemoteRevision", REMOTE_REVISION + 1)
            put("note", "A 先以 accepted 落库（本地行被抬到 REMOTE_REVISION），B 再高一级，故 B 仍是 accepted")
        })
    }

    /**
     * 段 1/4：真实 `importZip(A)` + 接缝强停窗口。
     * **本方法被真实终止时不会返回**；返回即无效中断并主动 FAIL。
     */
    @Test
    fun n1_31_armKillScenario4() = runBlocking {
        armAndImportWithKillWindow(S4, BOOK_S4)
    }

    /** 段 2/4：**新进程**纯读；A 已 accepted 落库，Room 必须读回 revision=2。 */
    @Test
    fun n1_32_readOnlyAfterKillScenario4() = runBlocking {
        readOnlyAfterKill(S4, BOOK_S4, expectedRevision = REMOTE_REVISION)
    }

    /** 段 3/4：B（accepted，rev 3）不得写入与继承义务冲突的正文。 */
    @Test
    fun n1_33_importBScenario4() = runBlocking {
        importScenarioZip(S4, "B")
        assertConflictingBBlocked(S4, BOOK_S4, "accepted下的B")
        val row = readBookRow(S4, BOOK_S4)
        assertEquals("B 仍走 accepted，Room 必须读回 revision=3", REMOTE_REVISION + 1, row?.revision)
        writeEvidence("n1_33_importBScenario4", S4, buildJsonObject {
            put("bookRevision", row?.revision ?: -1)
            put("expectedRevision", REMOTE_REVISION + 1)
        })
    }

    /** 段 4/4：同一份 B 再导入一次，正文与义务保护仍成立，版本不再变化。 */
    @Test
    fun n1_34_importBAgainScenario4() = runBlocking {
        importScenarioZip(S4, "B")
        assertConflictingBBlocked(S4, BOOK_S4, "accepted下的第二次B")
        val row = readBookRow(S4, BOOK_S4)
        assertEquals("第二次 B 后版本必须稳定在 3", REMOTE_REVISION + 1, row?.revision)
        val state = stateStore().read(sandboxContext(S4))
        assertEquals(
            "A 义务必须原样保留",
            sha256(ORIGINAL_A_CONTENT.toByteArray()),
            obligationOf(state, relOf(BOOK_S4)),
        )
    }

    // =====================================================================
    // 场景 5：strictlyNewer（本地版本更高）仍受保护
    // =====================================================================

    @Test
    fun n1_40_seedScenario5_strictlyNewer() = runBlocking {
        seedScenario(
            scenario = S5,
            bookId = BOOK_S5,
            localRevision = REMOTE_REVISION + 1,   // > 远端 → strictlyNewer
            localContent = ORIGINAL_CONTENT,
        )
        writeEvidence("n1_40_seedScenario5_strictlyNewer", S5, buildJsonObject {
            put("scenario", S5)
            put("bookId", BOOK_S5)
            put("expectedClassification", "strictlyNewer")
            put("localRevision", REMOTE_REVISION + 1)
            put("remoteRevision", REMOTE_REVISION)
        })
    }

    /** 严格较新的本地正文与版本永不被备份正文覆盖。 */
    @Test
    fun n1_41_importBScenario5() = runBlocking {
        val before = sha256(bookFile(S5, BOOK_S5).readBytes())
        val revisionBefore = readBookRow(S5, BOOK_S5)?.revision
        importScenarioZip(S5, "B")
        val after = sha256(bookFile(S5, BOOK_S5).readBytes())
        val revisionAfter = readBookRow(S5, BOOK_S5)?.revision
        assertEquals("strictlyNewer 本地正文不得被覆盖", before, after)
        assertEquals(ORIGINAL_CONTENT, bookFile(S5, BOOK_S5).readText())
        assertEquals("strictlyNewer 本地版本不得被覆盖", revisionBefore, revisionAfter)
        assertEquals(REMOTE_REVISION + 1, revisionAfter)
        assertNull("场景 5 无继承义务，导入完成后状态必须清除", stateStore().read(sandboxContext(S5)))
        writeEvidence("n1_41_importBScenario5", S5, buildJsonObject {
            put("contentShaBefore", before)
            put("contentShaAfter", after)
            put("revisionBefore", revisionBefore ?: -1)
            put("revisionAfter", revisionAfter ?: -1)
            put("preserved", before == after && revisionBefore == revisionAfter)
        })
    }

    // =====================================================================
    // 导出：正向真实产出 + 反向精确阻断（固定预期，不按现态选分支）
    // =====================================================================

    /**
     * **固定预期**，不根据「当前状态恰好是什么」挑一个能通过的分支：
     *
     * - 场景 1 已由 `n1_06` 收敛 → 状态必须为 `null`，导出**必须成功**，
     *   且解包后的 `backup.json` 与真实 DB 行逐字段一致、ZIP 正文等于恢复后的 A 字节；
     * - 场景 3（legacy 义务保留）与场景 4（accepted 冲突保留）**必须仍被阻断**，
     *   且阻断原因必须是「上次整包恢复未完成」这一条。
     */
    @Test
    fun n1_50_exportGateAndRealZip() = runBlocking {
        // ── 正向：场景 1 真实导出 ────────────────────────────────────────────
        assertNull("场景 1 收敛后不得残留恢复状态", stateStore().read(sandboxContext(S1)))
        val zip = exportToFile(S1, "s1-n1_50")
        assertTrue("场景 1 收敛后导出必须真实产出 ZIP", zip.isFile && zip.length() > 0)

        val dbRow = readBookRow(S1, BOOK_S1)
        assertNotNull("导出前场景 1 必须有书行", dbRow)

        java.util.zip.ZipFile(zip).use { zf ->
            val jsonEntry = zf.getEntry("backup.json")
            assertNotNull("导出 ZIP 必须含 backup.json", jsonEntry)
            val jsonText = zf.getInputStream(jsonEntry).use { it.readBytes().toString(Charsets.UTF_8) }
            val export = Json { ignoreUnknownKeys = true }.decodeFromString<LocalExport>(jsonText)
            val env = export.books.firstOrNull { it.id == BOOK_S1 }
            assertNotNull("backup.json 必须含场景 1 的书", env)
            assertEquals("backup.json 的 id 必须与真实 DB 行一致", dbRow!!.id, env!!.id)
            assertEquals("backup.json 的 revision 必须与真实 DB 行一致", dbRow.revision, env.revision)

            val bodyEntry = zf.getEntry("books/${relOf(BOOK_S1)}")
            assertNotNull("导出 ZIP 必须含已恢复的正文条目", bodyEntry)
            val body = zf.getInputStream(bodyEntry).use { it.readBytes().toString(Charsets.UTF_8) }
            assertEquals("导出 ZIP 正文必须等于恢复后的 A 字节", ORIGINAL_A_CONTENT, body)
        }

        writeEvidence("n1_50_exportGateAndRealZip", S1, buildJsonObject {
            put("positiveScenario", S1)
            put("stateCleared", true)
            put("exportZipSha256", sha256(zip.readBytes()))
            put("dbBookRevision", dbRow!!.revision)
            put("expectedRevision", REMOTE_REVISION)
            put("zipBodyEqualsRecoveredA", true)
            put("zipEntries", buildJsonArray { zipEntries(zip).sorted().forEach { add(it) } })
        })

        // ── 反向：场景 3 / 场景 4 必须被精确阻断 ─────────────────────────────
        listOf(S3 to BOOK_S3, S4 to BOOK_S4).forEach { (scenario, bookId) ->
            val state = stateStore().read(sandboxContext(scenario))
            assertNotNull("$scenario 必须仍有未完成状态", state)
            assertFalse("$scenario 状态不得完成", state!!.isComplete)
            val failure = runCatching { exportToFile(scenario, "$scenario-n1_50-blocked") }
            assertTrue("$scenario 未完成时导出必须失败", failure.isFailure)
            val msg = failure.exceptionOrNull()?.message.orEmpty()
            assertTrue("$scenario 导出必须被状态门禁精确阻止：$msg", msg.contains("上次整包恢复未完成"))
            writeEvidence("n1_50_blocked_$scenario", scenario, buildJsonObject {
                put("scenario", scenario)
                put("bookId", bookId)
                put("stateStage", state.stage)
                put("stateIsComplete", state.isComplete)
                put("obligation", obligationOf(state, relOf(bookId)).orEmpty())
                put("exportBlockedByGate", true)
                put("exportMessage", msg)
            })
        }
    }

    // =====================================================================
    // 收尾
    // =====================================================================

    /**
     * 删除**全部场景**沙箱与测试库，并证明已删除（设备恢复对账的一部分）。
     * 删除证明只写**外部证据目录**（被删树之外），因此不会把刚删掉的沙箱重建出来。
     */
    @Test
    fun n1_99_cleanupSandbox() {
        val root = sandboxRootPath()
        val existedBefore = root.exists()

        root.deleteRecursively()
        ALL_SCENARIOS.forEach { scenario ->
            externalScenarioDirPath(scenario)?.deleteRecursively()
            dbFiles(scenario).forEach { runCatching { it.delete() } }
        }

        val rootExistsAfter = root.exists()
        val dbLeft = ALL_SCENARIOS.flatMap { dbFiles(it) }.filter { it.exists() }
        val scenarioDirsLeft = ALL_SCENARIOS.map { scenarioDirPath(it) }.filter { it.exists() }
        val externalScenarioLeft = ALL_SCENARIOS.mapNotNull { externalScenarioDirPath(it) }.filter { it.exists() }

        writeEvidenceExternalOnly("n1_99_cleanupSandbox", buildJsonObject {
            put("sandboxExistedBefore", existedBefore)
            put("sandboxRootPath", root.absolutePath)
            put("sandboxRootExistsAfter", rootExistsAfter)
            put("scenarioDirsRemaining", buildJsonArray { scenarioDirsLeft.forEach { add(it.absolutePath) } })
            put("externalScenarioDirsRemaining", buildJsonArray { externalScenarioLeft.forEach { add(it.absolutePath) } })
            put("dbFilesRemaining", buildJsonArray { dbLeft.forEach { add(it.absolutePath) } })
            put("externalEvidenceKeptForPull", externalEvidenceDirPath()?.absolutePath.orEmpty())
            put("note", "外部证据目录不在删除范围内，供执行者 adb pull；随包卸载一并消失")
        })

        assertFalse("沙箱必须已删除（未被 getter 重建）", rootExistsAfter)
        assertTrue("场景沙箱目录必须已删除：$scenarioDirsLeft", scenarioDirsLeft.isEmpty())
        assertTrue("全部场景测试库必须已删除：$dbLeft", dbLeft.isEmpty())
    }

    // =====================================================================
    // 准备阶段（夹具）
    // =====================================================================

    /** 建场景沙箱、书行、正文与 A/B/坏 ZIP；记录阶段前指纹。夹具准备**不算**产品通过。 */
    private suspend fun seedScenario(
        scenario: String,
        bookId: String,
        localRevision: Int,
        localContent: String,
        bRevision: Int = REMOTE_REVISION,
    ) {
        booksDir(scenario).mkdirs()
        // 正文落盘
        bookFile(scenario, bookId).apply { parentFile!!.mkdirs(); writeText(localContent) }
        // 真实 Room 书行（local_content_path 指向场景沙箱内正文）
        val db = openDb(scenario)
        try {
            db.bookDao().upsert(
                BookEntity(
                    id = bookId,
                    title = "测试 TXT ${scenario.uppercase()}",
                    format = "txt",
                    size = localContent.toByteArray().size,
                    content_status = "available",
                    imported_at = STABLE_TIME,
                    revision = localRevision,
                    updated_at = STABLE_TIME,
                    local_content_path = bookFile(scenario, bookId).absolutePath,
                ),
            )
        } finally {
            db.close()
        }
        // ZIP：A（与义务匹配）/ B（同 JSON 不同正文）/ BAD（缺 backup.json）
        writeZip(
            File(scenarioCacheDir(scenario), zipName(scenario, "A")),
            backupJson(bookId, REMOTE_REVISION),
            bookId,
            ORIGINAL_A_CONTENT,
        )
        writeZip(
            File(scenarioCacheDir(scenario), zipName(scenario, "B")),
            backupJson(bookId, bRevision),
            bookId,
            CONFLICT_B_CONTENT,
        )
        writeZip(File(scenarioCacheDir(scenario), zipName(scenario, "BAD")), null, bookId, CONFLICT_B_CONTENT)
    }

    // =====================================================================
    // 产品调用阶段
    // =====================================================================

    /**
     * 真实 `importZip(A)`；用 [LocalZipBackup.fileCopyOperation] 接缝在
     * 「DB 计划已持久化、正文尚未完成」处写信号并**有上限等待**外部强停。
     *
     * - 每次 arm 生成唯一 `runId`，并先清掉旧信号、旧 continue 标记、旧未终止标记；
     * - **被真实终止时本方法不会返回**（进程已消失，JUnit 不会为它记 PASS）；
     * - 未被终止（超时或 continue 放行）时写「未终止标记」并**主动 FAIL**：
     *   arm 正常返回或超时属于**无效中断**，不得记 PASS；
     * - continue 标记只表示「继续完成本次复制」，**绝不记为 killObserved**。
     */
    private suspend fun armAndImportWithKillWindow(scenario: String, bookId: String) {
        // 1. 清理旧信号与旧标记，避免陈旧文件误触发
        signalFile(scenario).delete()
        externalSignalFile(scenario)?.delete()
        continueFlagFile(scenario)?.delete()
        armNotTerminatedFile(scenario)?.delete()

        val runId = "$scenario-${System.currentTimeMillis()}-${System.nanoTime()}"
        val armedAtMillis = System.currentTimeMillis()
        runIdFile(scenario)?.writeText(runId)

        val ctx = sandboxContext(scenario)
        val db = openDb(scenario)
        val backup = LocalZipBackup(buildBridge(db), Dispatchers.Unconfined)
        var signalWritten = false
        var continueRequested = false
        var seamEnteredPid = -1
        backup.fileCopyOperation = { input, output ->
            if (!signalWritten) {
                signalWritten = true
                seamEnteredPid = Process.myPid()
                writeKillSignal(scenario, bookId, runId)
                val deadline = System.currentTimeMillis() + KILL_WAIT_MILLIS
                while (System.currentTimeMillis() < deadline && !externalContinueRequested(scenario)) {
                    Thread.sleep(KILL_POLL_MILLIS)
                }
                continueRequested = externalContinueRequested(scenario)
                if (!continueRequested) {
                    // 等待窗口内未被终止：本次中断无效，写标记后主动失败。
                    armNotTerminatedFile(scenario)?.writeText(
                        buildJsonObject {
                            put("runId", runId)
                            put("scenario", scenario)
                            put("pid", Process.myPid())
                            put("waitedMillis", KILL_WAIT_MILLIS)
                            put("reason", "等待窗口内未观察到真实进程终止")
                        }.toString(),
                    )
                    db.close()
                    throw AssertionError(
                        "arm($scenario) 未被真实终止：等待 ${KILL_WAIT_MILLIS}ms 后进程仍存活。" +
                            "本次中断无效，不得记 PASS；执行者需重跑本场景（先 n1_00 清理）。",
                    )
                }
            }
            input.copyTo(output)
        }

        val result = runCatching {
            backup.importZip(ctx, Uri.fromFile(File(scenarioCacheDir(scenario), zipName(scenario, "A"))))
        }
        db.close()

        // 只有「continue 放行」才会走到这里（真实终止不会返回）。
        writeEvidenceExternalOnly("arm_${scenario}_continue", buildJsonObject {
            put("scenario", scenario)
            put("runId", runId)
            put("signalWritten", signalWritten)
            put("seamEnteredPid", seamEnteredPid)
            put("continueRequested", continueRequested)
            put("killObserved", false)
            put("importFailed", result.isFailure)
            put("failureMessage", result.exceptionOrNull()?.message.orEmpty())
            put("note", "continue 只表示继续完成复制，绝不表示真实终止；本次中断无效")
        })
        fail(
            "arm($scenario) 收到 continue 标记而非真实终止：本次中断无效，不得记 PASS；" +
                "执行者需重跑本场景（先 n1_00 清理）。",
        )
    }

    /**
     * 纯读阶段：只读 DB / 正文 / 状态 / journal，然后写证据。
     * 不 seed、不重建、不改状态；**打开 Room 前先确认测试库存在**。
     *
     * 同时核对「真实终止」四条判据：信号存在、未终止标记不存在、
     * 当前 PID ≠ 信号 PID、状态停在 `db_committed`。
     */
    private suspend fun readOnlyAfterKill(scenario: String, bookId: String, expectedRevision: Int) {
        requireExistingDb(scenario)
        requireRealTermination(scenario)

        val signal = readSignal(scenario)
        val killedPid = signal?.get("pid")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1
        val currentPid = Process.myPid()

        val ctx = sandboxContext(scenario)
        val db = openDb(scenario)
        val row = try {
            db.bookDao().getById(bookId)
        } finally {
            db.close()
        }
        val state = stateStore().read(ctx)
        val journalRaw = journalFile(scenario).takeIf { it.isFile }?.readText().orEmpty()
        val contentBytes = bookFile(scenario, bookId).takeIf { it.isFile }?.readBytes()

        assertNotNull("$scenario：强停后书行必须存在", row)
        assertEquals("$scenario：强停后书版本必须等于 A 的远端版本", expectedRevision, row!!.revision)
        assertNotNull("$scenario：强停后原正文必须存在", contentBytes)
        assertEquals(
            "$scenario：强停后正文必须仍是原字节（A 尚未换入）",
            ORIGINAL_CONTENT,
            contentBytes!!.toString(Charsets.UTF_8),
        )
        assertNotNull("$scenario：强停后恢复状态必须仍在", state)
        assertFalse("$scenario：强停后状态不得完成", state!!.isComplete)
        assertEquals(
            "$scenario：状态阶段必须是 DB 已提交、文件未复制",
            LocalZipRestoreState.STAGE_DB_COMMITTED,
            state.stage,
        )
        assertEquals(
            "$scenario：义务必须是准确的 A 正文 SHA",
            sha256(ORIGINAL_A_CONTENT.toByteArray()),
            obligationOf(state, relOf(bookId)),
        )
        assertTrue("$scenario：必须留下接缝对应的逐文件 journal 条目", journalRaw.contains(relOf(bookId)))
        assertTrue("$scenario：接缝 journal 条目必须仍未校验（verified=false）", journalRaw.contains("false"))
        assertTrue("$scenario：新进程 PID 必须不同于被强停 PID", currentPid != killedPid)

        writeEvidence("readonly_$scenario", scenario, buildJsonObject {
            put("scenario", scenario)
            put("bookId", bookId)
            put("bookRowPresent", true)
            put("bookRevision", row.revision)
            put("expectedRevision", expectedRevision)
            put("contentSha256", sha256(contentBytes))
            put("contentEqualsOriginal", true)
            put("statePresent", true)
            put("stateStage", state.stage)
            put("stateIsComplete", state.isComplete)
            put("stateBackupFingerprintEmpty", state.backupFingerprint.isEmpty())
            put("obligation", obligationOf(state, relOf(bookId)).orEmpty())
            put("pendingTargets", buildJsonArray { state.pendingTargets.keys.sorted().forEach { add(it) } })
            put("unresolvedTargets", buildJsonArray { state.unresolvedTargets.keys.sorted().forEach { add(it) } })
            put("journalRaw", journalRaw)
            put("journalExists", journalFile(scenario).isFile)
            put("killedPid", killedPid)
            put("currentPid", currentPid)
            put("pidChanged", currentPid != killedPid)
            put("signalRunId", signal?.get("runId")?.jsonPrimitive?.contentOrNull.orEmpty())
        })
    }

    /**
     * 真实终止判据（四条同时成立）：信号存在、未终止标记不存在、
     * 当前 PID ≠ 信号 PID、状态停在 `db_committed`。
     *
     * 用于纯读阶段与收敛阶段，防止「arm 其实没被杀」被下游静默当成通过。
     */
    private fun requireRealTermination(scenario: String) {
        val signal = readSignal(scenario)
            ?: failNow("$scenario：缺少强停信号文件，本场景的强停未发生，不得据此判定任何结论")
        val notTerminated = armNotTerminatedFile(scenario)
        if (notTerminated != null && notTerminated.isFile) {
            fail(
                "$scenario：存在「未终止标记」（${notTerminated.absolutePath}）——" +
                    "上一次 arm 未被真实终止，本次中断无效，不得记 PASS；请重跑本场景（先 n1_00 清理）",
            )
        }
        val killedPid = signal["pid"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1
        if (killedPid <= 0 || killedPid == Process.myPid()) {
            fail(
                "$scenario：强停 PID 无效或与当前 PID 相同（killedPid=$killedPid，" +
                    "currentPid=${Process.myPid()}）——未观察到真实进程终止",
            )
        }
    }

    /** 冲突目标断言：正文未被覆盖、原义务身份保留、状态未完成、导出被门禁精确阻止。 */
    private suspend fun assertConflictingBBlocked(scenario: String, bookId: String, label: String) {
        val ctx = sandboxContext(scenario)
        val content = bookFile(scenario, bookId).readText()
        assertEquals("$label：冲突正文不得写入", ORIGINAL_CONTENT, content)
        val state = stateStore().read(ctx)
        assertNotNull("$label：状态必须仍在", state)
        assertFalse("$label：状态不得完成", state!!.isComplete)
        val obligation = obligationOf(state, relOf(bookId))
        assertNotNull(
            "$label：原义务必须保留" +
                "（若此处为空：执行者未在强停窗口内 force-stop，本次夹具未建立）",
            obligation,
        )
        assertFalse("$label：义务不得被 B 的 SHA 替换", obligation == sha256(CONFLICT_B_CONTENT.toByteArray()))
        val exportFailure = runCatching { exportToFile(scenario, "$scenario-blocked-$label") }
        assertTrue("$label：未完成状态下导出必须失败", exportFailure.isFailure)
        val msg = exportFailure.exceptionOrNull()?.message.orEmpty()
        assertTrue("$label：导出必须被状态门禁精确阻止：$msg", msg.contains("上次整包恢复未完成"))
        writeEvidence("blocked_${scenario}_$label", scenario, buildJsonObject {
            put("label", label)
            put("contentSha256", sha256(content.toByteArray()))
            put("obligation", obligation.orEmpty())
            put("stateStage", state.stage)
            put("exportBlockedByGate", true)
            put("exportMessage", msg)
        })
    }

    private suspend fun importScenarioZip(scenario: String, kind: String) {
        importZipFile(scenario, File(scenarioCacheDir(scenario), zipName(scenario, kind)))
    }

    private suspend fun importZipFile(scenario: String, zip: File) {
        val db = openDb(scenario)
        try {
            LocalZipBackup(buildBridge(db), Dispatchers.Unconfined)
                .importZip(sandboxContext(scenario), Uri.fromFile(zip))
        } finally {
            db.close()
        }
    }

    private suspend fun exportToFile(scenario: String, tag: String): File {
        val out = File(scenarioCacheDir(scenario), "n1-export-$tag.zip")
        val db = openDb(scenario)
        try {
            LocalZipBackup(buildBridge(db), Dispatchers.Unconfined)
                .exportZip(sandboxContext(scenario), Uri.fromFile(out))
        } finally {
            db.close()
        }
        return out
    }

    private suspend fun readBookRow(scenario: String, bookId: String): BookEntity? {
        val db = openDb(scenario)
        return try {
            db.bookDao().getById(bookId)
        } finally {
            db.close()
        }
    }

    // =====================================================================
    // 接缝信号
    // =====================================================================

    /** 写「可终止进程」信号：runId、阶段、实际 PID、状态与 journal 现场、目标文件信息。 */
    private fun writeKillSignal(scenario: String, bookId: String, runId: String) {
        val ctx = sandboxContext(scenario)
        val state = stateStore().read(ctx)
        val journal = journalFile(scenario)
        val payload = buildJsonObject {
            put("phase", "after_db_plan_before_content_copy")
            put("runId", runId)
            put("scenario", scenario)
            put("bookId", bookId)
            put("pid", Process.myPid())
            put("timestampMillis", System.currentTimeMillis())
            put("boundedWaitMillis", KILL_WAIT_MILLIS)
            put("rel", relOf(bookId))
            put("dst", bookFile(scenario, bookId).absolutePath)
            put("dstExists", bookFile(scenario, bookId).isFile)
            put("dstSha256", if (bookFile(scenario, bookId).isFile) {
                sha256(bookFile(scenario, bookId).readBytes())
            } else {
                ""
            })
            put("expectedObligation", sha256(ORIGINAL_A_CONTENT.toByteArray()))
            put("stateFile", stateStore().stateFile(ctx).absolutePath)
            put("stateStage", state?.stage.orEmpty())
            put("stateIsComplete", state?.isComplete ?: false)
            put("stateBackupFingerprintEmpty", state?.backupFingerprint?.isEmpty() ?: true)
            put("statePendingTargets", buildJsonArray { state?.pendingTargets?.keys?.sorted()?.forEach { add(it) } })
            put("stateUnresolvedTargets", buildJsonArray { state?.unresolvedTargets?.keys?.sorted()?.forEach { add(it) } })
            put("journalFile", journal.absolutePath)
            put("journalExists", journal.isFile)
            put("journalRaw", if (journal.isFile) journal.readText() else "")
            put("note", "执行者核对 runId/scenario/时间戳/pidof 后 am force-stop 本包；本文件不等于产品通过")
        }.toString()
        signalFile(scenario).writeText(payload)
        externalSignalFile(scenario)?.writeText(payload)
    }

    /** 读取强停信号（优先外部，shell 可读那份；缺失时回退内部）。 */
    private fun readSignal(scenario: String): JsonObject? {
        val candidates = listOfNotNull(externalSignalFile(scenario), signalFile(scenario))
        val file = candidates.firstOrNull { it.isFile } ?: return null
        return runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
    }

    /**
     * 执行者可通过外部信号旁的 continue 标记请求「不终止，继续完成」。
     * **只表示继续，绝不表示真实终止。**
     */
    private fun externalContinueRequested(scenario: String): Boolean =
        continueFlagFile(scenario)?.isFile == true

    // =====================================================================
    // 证据
    // =====================================================================

    /** 写 JSON 证据：沙箱内一份（随沙箱删除）+ 外部一份（供 `adb pull`）。 */
    private fun writeEvidence(tag: String, scenario: String, payload: JsonObject) {
        val body = buildJsonObject {
            put("tag", tag)
            put("scenario", scenario)
            put("pid", Process.myPid())
            put("timestampMillis", System.currentTimeMillis())
            put("payload", payload)
        }.toString()
        runCatching { File(ensureDir(internalEvidenceDirPath()), "$tag.json").writeText(body) }
        writeEvidenceExternalOnly(tag, payload)
    }

    /**
     * 只写**外部证据目录**：它是被删沙箱树的兄弟，不在 `n1_00` / `n1_99` 的删除范围内。
     * 删除类证据必须走这里，否则写证据会把刚删掉的沙箱目录重建出来。
     */
    private fun writeEvidenceExternalOnly(tag: String, payload: JsonObject) {
        val dir = externalEvidenceDirPath() ?: return
        ensureDir(dir)
        File(dir, "$tag.json").writeText(
            buildJsonObject {
                put("tag", tag)
                put("pid", Process.myPid())
                put("timestampMillis", System.currentTimeMillis())
                put("payload", payload)
            }.toString(),
        )
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private fun stateStore(): LocalZipRestoreStateStore = LocalZipRestoreStateStore()

    /**
     * 表达式位置使用的失败助手。`org.junit.Assert.fail` 在 Kotlin 里返回 `Unit`（不是 `Nothing`），
     * 直接用于 `?:` 会把结果类型拓宽成 `Any`；本助手返回 `Nothing`，保持类型收窄。
     */
    private fun failNow(message: String): Nothing = throw AssertionError(message)

    private fun obligationOf(state: LocalZipRestoreState?, rel: String): String? =
        state?.let { it.unresolvedTargets[rel] ?: it.pendingTargets[rel] }

    private fun relOf(bookId: String): String = "$bookId/content.txt"

    private fun zipName(scenario: String, kind: String): String = "n1-$scenario-$kind.zip"

    /** `backupJson == null` 时生成**缺 backup.json** 的坏 ZIP。 */
    private fun writeZip(zip: File, backupJson: String?, bookId: String, content: String) {
        zip.parentFile?.mkdirs()
        ZipOutputStream(zip.outputStream()).use { zos ->
            if (backupJson != null) {
                zos.putNextEntry(ZipEntry("backup.json"))
                zos.write(backupJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            zos.putNextEntry(ZipEntry("books/${relOf(bookId)}"))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun backupJson(bookId: String, revision: Int = REMOTE_REVISION): String {
        val json = Json { ignoreUnknownKeys = true }
        val book = BookEntity(
            id = bookId, title = "测试 TXT", format = "txt",
            revision = revision, updated_at = STABLE_TIME,
        )
        val envelope = SyncEnvelope(
            id = bookId, type = "book", revision = revision,
            deviceId = "n1-remote-device", updatedAt = STABLE_TIME, deletedAt = null, payload = book,
        )
        val export = LocalExport(
            schemaVersion = 1, exportedAt = STABLE_TIME, deviceId = "n1-sandbox-device",
            books = listOf(envelope), inspirations = emptyList(), progress = emptyList(), sessions = emptyList(),
        )
        return json.encodeToString(LocalExport.serializer(), export)
    }

    private fun zipEntries(zip: File): Set<String> = java.util.zip.ZipFile(zip).use { zf ->
        zf.entries().toList().map { it.name }.toSet()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    // =====================================================================
    // 沙箱上下文
    // =====================================================================

    /**
     * 把 `filesDir` / `cacheDir` 重定向到**场景**沙箱根，并把 SharedPreferences 全部换成内存实现。
     * `deleteSharedPreferences` 必须为 no-op：`SecurePrefs` 的降级重试会删同名 prefs 文件。
     */
    private class SandboxContext(base: Context, private val root: File) : ContextWrapper(base) {
        private val prefsByName = mutableMapOf<String, MutableMap<String, Any?>>()
        private val filesRoot = File(root, "files")
        private val cacheRoot = File(root, "cache")

        override fun getFilesDir(): File = filesRoot.also { it.mkdirs() }
        override fun getCacheDir(): File = cacheRoot.also { it.mkdirs() }
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            InMemorySharedPreferences(prefsByName.getOrPut(name ?: "default") { mutableMapOf() })
        override fun deleteSharedPreferences(name: String?): Boolean = true
    }

    private class InMemorySharedPreferences(
        private val values: MutableMap<String, Any?>,
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            override fun putString(key: String?, value: String?) = apply { put(key, value) }
            override fun putStringSet(key: String?, value: MutableSet<String>?) = apply { put(key, value) }
            override fun putInt(key: String?, value: Int) = apply { put(key, value) }
            override fun putLong(key: String?, value: Long) = apply { put(key, value) }
            override fun putFloat(key: String?, value: Float) = apply { put(key, value) }
            override fun putBoolean(key: String?, value: Boolean) = apply { put(key, value) }
            override fun remove(key: String?) = apply { removals.add(key ?: "") }
            override fun clear() = apply { removals.addAll(values.keys) }
            override fun commit(): Boolean { applyChanges(); return true }
            override fun apply() { applyChanges() }
            private fun put(key: String?, value: Any?) {
                val k = key ?: return
                removals.remove(k)
                pending[k] = value
            }
            private fun applyChanges() {
                removals.forEach { values.remove(it) }
                values.putAll(pending)
                pending.clear()
                removals.clear()
            }
        }
    }

    private companion object {
        /** 沙箱根目录（`n1-` 前缀，位于沙箱包私有存储，跨仪器调用稳定）。 */
        private const val SANDBOX_DIR = "n1-sandbox"

        /** 场景键（同时用于沙箱子目录名与测试库名后缀）。 */
        private const val S1 = "s1"
        private const val S2 = "s2"
        private const val S3 = "s3"
        private const val S4 = "s4"
        private const val S5 = "s5"
        private val ALL_SCENARIOS = listOf(S1, S2, S3, S4, S5)

        private const val SIGNAL_NAME = "n1-kill-signal.json"
        private const val CONTINUE_FLAG_NAME = "n1-continue.flag"
        private const val ARM_NOT_TERMINATED_NAME = "n1-arm-not-terminated.json"
        private const val RUN_ID_NAME = "n1-arm-run-id.txt"
        private const val JOURNAL_NAME = ".cra-restore-journal.json"

        private const val STABLE_TIME = "2026-10-02T00:00:00Z"
        private const val REMOTE_REVISION = 2

        private const val ORIGINAL_CONTENT = "N1 SANDBOX ORIGINAL CONTENT"
        private const val ORIGINAL_A_CONTENT = "N1 SANDBOX A CONTENT"
        private const val CONFLICT_B_CONTENT = "N1 SANDBOX B CONTENT"
        private const val LEGACY_LENGTH_OBLIGATION = "12345"

        private const val BOOK_S1 = "n1-b1"
        private const val BOOK_S2 = "n1-b2"
        private const val BOOK_S3 = "n1-b3"
        private const val BOOK_S4 = "n1-b4"
        private const val BOOK_S5 = "n1-b5"

        /** 信号写盘后等待外部强停的上限；超时即判无效中断并主动 FAIL。 */
        private const val KILL_WAIT_MILLIS = 120_000L
        private const val KILL_POLL_MILLIS = 200L
    }
}
