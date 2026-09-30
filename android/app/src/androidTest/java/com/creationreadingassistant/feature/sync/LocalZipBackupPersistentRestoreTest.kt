package com.creationreadingassistant.feature.sync

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.LibrarySourceRefEntity
import com.creationreadingassistant.data.local.entity.ReaderCorrectionEntity
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.domain.model.SyncEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * S14「隔离持久库备份恢复」仪器测试。
 *
 * ## 与既有 [LocalExportRoomRollbackTest] 的分工
 *
 * 既有测试（2026-09-27 MuMu 2/2 验收）只覆盖 `Room.inMemoryDatabaseBuilder` 的事务断言，
 * 不能证明三件事，本文件补的正是这三件：
 *
 * 1. **持久库导入**：`Room.databaseBuilder` + 唯一命名数据库文件（非内存），导入真实落盘；
 * 2. **关闭→重新打开**：`close()` 后按同一名字重建连接，对账五类记录仍然在；
 * 3. **完整 ZIP 备份恢复**：`LocalZipBackup.exportZip → importZip` 的文件集合
 *    （backup.json + books/ 下的书源文件）用独立 SHA-256 对账，不只对账数据库行。
 *
 * ## 隔离边界（必须与代码同步维护）
 *
 * - **持久库**：`TEST_DB_NAME` 是本文件私有的唯一名字，与正式库
 *   `AppDatabase.DB_NAME`（"creation_reading_assistant_native"，AppDatabase.kt:159）
 *   永不重名；`@Before`/`@After` 删除 db + `-wal` + `-shm` + `-journal` 四件。
 * - **文件系统**：`IsolatedSyncTestContext` 把 `filesDir` / `cacheDir` 重定向到测试专属
 *   唯一根目录。`LocalZipBackup.exportZip` 遍历的就是被重定向后的 `filesDir/books`，
 *   **绝不枚举 qahost 已有书籍文件**；`importZip` 的 books 覆盖与临时解压目录也全部
 *   落在测试根内，绝不写入 qahost 真实 `files/books/`。
 * - **SecurePrefs**：`JsonBridge.exportToString` 经 `SecurePrefs.getOrCreateAppInstanceId`
 *   取 deviceId，会打开 qahost 真实加密 prefs（甚至可能写）。本包装覆写
 *   `getSharedPreferences` 返回**内存实现**，使加密 prefs 的 delegate 完全落在内存；
 *   同时覆写 `deleteSharedPreferences` 为空操作——`SecurePrefs.open` 的降级重试路径
 *   会调用它删除同名 prefs 文件，不覆写的话这条路径会删掉 qahost 真实的
 *   `cra_device_identity`（含 WebDAV 令牌等），这是本文件最关键的一道防线。
 * - **残留设备侧触碰（如实登记）**：`SecurePrefs` 正常路径会用 `MasterKey` 在
 *   AndroidKeyStore 中 get-or-create qahost 测试包的主密钥别名——幂等、非用户数据、
 *   等价于应用首次启动的自有行为；此外主 APK 合并清单的 `androidx.startup` 仍会创建
 *   `androidx.work.workdb`（`CraIsolatedTestApplication` KDoc 已登记，换 Application
 *   无法消除）。除这两项外，本测试对 qahost 状态零写入。
 * - **数据**：只有合成记录（测试 EPUB / 测试 TXT），书籍文件是合成字节，不做任何解析。
 *
 * ## 状态
 *
 * **未编译 / 未执行**。本文件由窗口按「窗口 1 唯一 Gradle 所有者」纪律交付静态产物，
 * 编译与设备运行由总指挥串行安排（见
 * `docs/coordination/2026-09-28-s14-persistent-backup-test-preparation.md`）。
 */
@RunWith(AndroidJUnit4::class)
class LocalZipBackupPersistentRestoreTest {

    private val openedDatabases = mutableListOf<AppDatabase>()
    private val testRoots = mutableListOf<File>()

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        deleteTestDbFiles()
    }

    @After
    fun tearDown() {
        openedDatabases.forEach { runCatching { it.close() } }
        openedDatabases.clear()
        deleteTestDbFiles()
        testRoots.forEach { runCatching { it.deleteRecursively() } }
        testRoots.clear()
    }

    // ================= 用例 1：导出 → 重置 → ZIP 恢复 → 关闭 → 重开 对账 =================

    @Test
    fun zipBackupRoundTripRestoresFiveCategoriesAndFilesAcrossCloseReopen() = runTest {
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val exportRoot = newTestRoot("export")
        val exportWrapper = IsolatedSyncTestContext(targetContext, exportRoot)

        // 1. 合成书源文件：ZIP 的 books/** 部分
        val txtBytes = "S14 测试 TXT 正文：第一章……这是持久库恢复对账的合成内容。".toByteArray()
        val epubBytes = ByteArray(2048) { ((it * 31) % 251).toByte() }
        val plantedFiles = mapOf(
            "books/s14-txt-1/content.txt" to txtBytes,
            "books/epub/s14-epub-1.epub" to epubBytes,
        )
        plantedFiles.forEach { (rel, bytes) ->
            val f = File(exportWrapper.filesDir, rel)
            f.parentFile!!.mkdirs()
            f.writeBytes(bytes)
        }

        // 2. 种入持久库：书籍 / 进度 / 来源引用 / 规则 / 纠错 五类
        val seedEpubBook = bookEntity("s14-epub-1", "测试 EPUB S14", "epub", epubBytes.size)
        val seedTxtBook = bookEntity("s14-txt-1", "测试 TXT S14", "txt", txtBytes.size)
        val seedProgress = listOf(
            progressEntity("s14-epub-1", 32.5f, """{"chapter":1,"offset":120}"""),
            progressEntity("s14-txt-1", 45f, """{"legado_position_v1":{"ci":2,"co":88}}"""),
        )
        val seedRule = ruleEntity()
        val seedCorrection = correctionEntity("s14-txt-1")
        val seedRefs = listOf(
            sourceRefEntity("s14-epub-1", epubBytes.size.toLong(), "s14-epub-hash"),
            sourceRefEntity("s14-txt-1", txtBytes.size.toLong(), "s14-txt-hash"),
        )

        val db1 = openPersistentDb()
        val bridge1 = buildBridge(db1)
        db1.bookDao().upsert(seedEpubBook)
        db1.bookDao().upsert(seedTxtBook)
        seedProgress.forEach { db1.readingProgressDao().upsert(it) }
        seedRefs.forEach { db1.librarySourceRefDao().upsert(it) }
        db1.readerTextRuleDao().upsert(seedRule)
        db1.readerCorrectionDao().upsert(seedCorrection)
        val digest1 = canonicalDigest(db1)

        // 3. 导出：纯 JSON 与 ZIP 两条产物
        val jsonBefore = bridge1.exportToString(exportWrapper)
        val parsedBefore = json.decodeFromString(LocalExport.serializer(), jsonBefore)
        assertEquals("导出应含 2 本书籍", 2, parsedBefore.books.size)
        assertEquals("导出应含 2 条来源引用", 2, parsedBefore.librarySourceRefs.size)
        assertEquals("导出应含 2 条进度", 2, parsedBefore.progress.size)
        assertEquals("导出应含 1 条规则", 1, parsedBefore.textRules.size)
        assertEquals("导出应含 1 条纠错", 1, parsedBefore.textCorrections.size)

        val zipFile = File(exportWrapper.cacheDir, "s14-backup.zip")
        LocalZipBackup(bridge1, Dispatchers.Unconfined).exportZip(exportWrapper, Uri.fromFile(zipFile))
        assertTrue("ZIP 必须真实落盘", zipFile.isFile && zipFile.length() > 0)

        // 4. ZIP 文件集合与关键文件独立哈希对账
        ZipFile(zipFile).use { zip ->
            val entryNames = zip.entries().toList().map { it.name }.toSet()
            assertEquals(
                "ZIP 条目集合必须是 backup.json + 全部书源文件",
                setOf("backup.json") + plantedFiles.keys,
                entryNames,
            )
            val backupJsonBytes = zip.getInputStream(zip.getEntry("backup.json")).use { it.readBytes() }
            val parsedZip = json.decodeFromString(LocalExport.serializer(), backupJsonBytes.toString(Charsets.UTF_8))
            assertEquals("backup.json 的书籍清单必须与导出一致", 2, parsedZip.books.size)
            assertEquals("backup.json 的纠错清单必须与导出一致", 1, parsedZip.textCorrections.size)
            plantedFiles.forEach { (rel, bytes) ->
                val entryBytes = zip.getInputStream(zip.getEntry(rel)).use { it.readBytes() }
                assertEquals("ZIP 内书源文件哈希必须与落盘源文件一致：$rel", sha256(bytes), sha256(entryBytes))
            }
        }

        // 5. 模拟整库丢失：关闭并删除持久库文件，再按同名重建空库
        db1.close()
        openedDatabases.remove(db1)
        deleteTestDbFiles()

        val db2 = openPersistentDb()
        val bridge2 = buildBridge(db2)
        val restoreRoot = newTestRoot("restore")
        val restoreWrapper = IsolatedSyncTestContext(targetContext, restoreRoot)
        LocalZipBackup(bridge2, Dispatchers.Unconfined).importZip(restoreWrapper, Uri.fromFile(zipFile))

        // 6. 恢复后对账：五类记录逐实体相等（导出 payload 原样入库）
        assertEquals(
            listOf(seedEpubBook, seedTxtBook).sortedBy { it.id },
            db2.bookDao().getByIds(listOf("s14-epub-1", "s14-txt-1")).sortedBy { it.id },
        )
        assertEquals(
            seedProgress.sortedBy { it.book_id },
            db2.readingProgressDao().getByBooks(listOf("s14-epub-1", "s14-txt-1")).sortedBy { it.book_id },
        )
        assertEquals(
            seedRefs.sortedBy { it.book_id },
            db2.librarySourceRefDao().getAll().sortedBy { it.book_id },
        )
        assertEquals(listOf(seedRule), db2.readerTextRuleDao().observeAll().first())
        assertEquals(
            listOf(seedCorrection),
            db2.readerCorrectionDao().listForBook("s14-txt-1"),
        )

        // 7. 恢复出的书源文件逐字节对账
        val restoredBooksDir = File(restoreWrapper.filesDir, "books")
        val restoredFiles = restoredBooksDir.walkTopDown().filter { it.isFile }
            .associate { it.relativeTo(restoredBooksDir).invariantSeparatorsPath to it.readBytes() }
        assertEquals(
            "恢复出的文件集合必须与打包前一致",
            plantedFiles.keys.map { it.removePrefix("books/") }.toSet(),
            restoredFiles.keys,
        )
        plantedFiles.forEach { (rel, bytes) ->
            assertEquals(
                "恢复文件哈希必须一致：$rel",
                sha256(bytes),
                sha256(restoredFiles.getValue(rel.removePrefix("books/"))),
            )
        }

        // 8. 关闭 → 重新打开：五类记录与逻辑摘要必须原样存活
        val digest2 = canonicalDigest(db2)
        assertEquals("导入后的逻辑摘要必须与导出前一致", digest1, digest2)
        db2.close()
        openedDatabases.remove(db2)

        val db3 = openPersistentDb()
        assertTrue("持久库文件必须真实存在于应用私有 databases 目录", targetContext.getDatabasePath(TEST_DB_NAME).isFile)
        assertEquals("重开后逻辑摘要不得变化", digest2, canonicalDigest(db3))
        assertEquals(
            "重开后书籍必须仍在",
            listOf("s14-epub-1", "s14-txt-1"),
            db3.bookDao().getByIds(listOf("s14-epub-1", "s14-txt-1")).map { it.id }.sorted(),
        )
        assertEquals("重开后规则必须仍在", 1, db3.readerTextRuleDao().observeAll().first().size)
        assertEquals("重开后纠错必须仍在", 1, db3.readerCorrectionDao().listForBook("s14-txt-1").size)
        assertEquals("重开后来源引用必须仍在", 2, db3.librarySourceRefDao().getAll().size)
        db3.close()
        openedDatabases.remove(db3)
    }

    // ============ 用例 2：可注入失败路径 —— 持久库中途导入失败回滚且重开后仍回滚 ============

    /**
     * 注入方式与既有内存测试同源但更强：`reader_text_corrections.book_id` 有
     * FK→books CASCADE（schema 15.json 核实），而纠错是 [JsonBridge.importFromString]
     * 事务内的**最后一步**。构造一本"不存在书籍"的纠错，让书籍/进度/来源引用/规则的
     * 写入全部先行完成后才触发 [SQLiteConstraintException]——持久库上先前的写入
     * 必须全部回滚，且**关闭重开后回滚结果仍然成立**（内存测试无法证明的持久性）。
     */
    @Test
    fun correctionForeignKeyFailureRollsBackAllCategoriesInPersistentDbAndStaysRolledBackAfterReopen() = runTest {
        val db = openPersistentDb()
        val bridge = buildBridge(db)
        val keepBook = bookEntity("s14-keep", "保留书籍", "txt", 1)
        db.bookDao().upsert(keepBook)

        val incomingBook = bookEntity("s14-incoming", "不应留下的书籍", "epub", 9)
        val incomingProgress = progressEntity("s14-incoming", 50f, """{"chapter":0,"offset":1}""")
        val incomingRef = sourceRefEntity("s14-incoming", 9L, "s14-incoming-hash")
        val incomingRule = ReaderTextRuleEntity(
            id = "s14-rule-x", kind = "replace", name = "不应留下的规则", pattern = "a",
            replacement = "b", builtin = false, enabled = true, scope = "global",
            book_id = null, position = 1, created_at = FIXED_MILLIS, updated_at = FIXED_MILLIS,
        )
        // 故意指向不存在的书：纠错在事务最后一步写入，此处 FK 失败 = 中途失败而非解析失败
        val orphanCorrection = correctionEntity("s14-missing-book").copy(id = "s14-corr-x")
        val export = LocalExport(
            exportedAt = STABLE_TIME,
            deviceId = "s14-isolated-test",
            books = listOf(bookEnvelope(incomingBook)),
            inspirations = emptyList(),
            progress = listOf(progressEnvelope(incomingProgress)),
            sessions = emptyList(),
            librarySourceRefs = listOf(SourceRefExport.fromEntity(incomingRef)),
            textRules = listOf(TextRuleExport.fromEntity(incomingRule)),
            textCorrections = listOf(CorrectionExport.fromEntity(orphanCorrection)),
        )

        val outcome = runCatching {
            bridge.importFromString(ApplicationProvider.getApplicationContext(), json.encodeToString(export))
        }

        assertTrue("受控外键冲突必须上抛", outcome.exceptionOrNull() is SQLiteConstraintException)
        assertNull("中途导入的新书不得遗留", db.bookDao().getById("s14-incoming"))
        assertEquals("既有书籍不得受影响", keepBook, db.bookDao().getById("s14-keep"))
        assertTrue("中途写入的进度必须回滚", db.readingProgressDao().getByBooks(listOf("s14-incoming")).isEmpty())
        assertTrue("中途写入的来源引用必须回滚", db.librarySourceRefDao().getAll().isEmpty())
        assertNull("中途写入的规则必须回滚", db.readerTextRuleDao().getById("s14-rule-x"))
        assertNull("触发失败的纠错本身不得遗留", db.readerCorrectionDao().getById("s14-corr-x"))
        db.close()
        openedDatabases.remove(db)

        // 关闭 → 重新打开：回滚必须持久成立，而不是只存在于当前连接的未提交视图
        val dbReopened = openPersistentDb()
        assertEquals(
            "重开后书籍必须只剩保留书",
            listOf("s14-keep"),
            dbReopened.bookDao().getByIds(listOf("s14-keep", "s14-incoming")).map { it.id },
        )
        assertNull("重开后 incoming 不得出现", dbReopened.bookDao().getById("s14-incoming"))
        assertNotNull("重开后保留书籍必须仍在", dbReopened.bookDao().getById("s14-keep"))
        assertTrue("重开后进度必须仍为空", dbReopened.readingProgressDao().getByBooks(listOf("s14-incoming")).isEmpty())
        assertTrue("重开后来源引用必须仍为空", dbReopened.librarySourceRefDao().getAll().isEmpty())
        assertNull("重开后规则必须仍不存在", dbReopened.readerTextRuleDao().getById("s14-rule-x"))
        dbReopened.close()
        openedDatabases.remove(dbReopened)
    }

    // ============ 用例 3：非备份 ZIP 在触碰数据库之前被拒绝 ============

    @Test
    fun zipWithoutBackupJsonIsRejectedBeforeTouchingPersistentDb() = runTest {
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val db = openPersistentDb()
        val bridge = buildBridge(db)
        val keepBook = bookEntity("s14-keep", "保留书籍", "txt", 1)
        db.bookDao().upsert(keepBook)

        val wrapper = IsolatedSyncTestContext(targetContext, newTestRoot("badzip"))
        val zipFile = File(wrapper.cacheDir, "not-a-backup.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("books/notes.txt"))
            zos.write("缺少 backup.json 的包".toByteArray())
            zos.closeEntry()
        }

        val outcome = runCatching {
            LocalZipBackup(bridge, Dispatchers.Unconfined).importZip(wrapper, Uri.fromFile(zipFile))
        }

        assertTrue(
            "缺少 backup.json 必须在入库前被拒绝（IllegalStateException）",
            outcome.exceptionOrNull() is IllegalStateException,
        )
        assertEquals("持久库不得被非法 ZIP 触碰", keepBook, db.bookDao().getById("s14-keep"))
        db.close()
        openedDatabases.remove(db)

        val dbReopened = openPersistentDb()
        assertEquals("重开后既有书籍必须原样保留", keepBook, dbReopened.bookDao().getById("s14-keep"))
        dbReopened.close()
        openedDatabases.remove(dbReopened)
    }

    // ============ 用例 4（S14 安全修复）：复制中途失败不得截断/半写入覆盖旧文件 ============

    /**
     * 旧行为可复现失败：DB 提交后直接对 `dst.outputStream()` 写入，
     * FileOutputStream 先截断既有文件，写中途失败即损坏唯一完好旧文件。
     * 新行为把写入限制在同目录 staging 上；本用例用 [LocalZipBackup.fileCopyOperation]
     * 测试接缝在 staging 写入中途抛出——断言旧文件逐字节保留、无 staging 残留，
     * 而 DB 行已提交（持久库上 DB 先提交、文件后复制的边界如实保留）。
     * 夹具自洽：目标路径只有「旧文件」一个角色，失败由接缝制造，不靠磁盘满。
     */
    @Test
    fun fileCopyMidWriteFailureKeepsExistingUniqueSourceFileIntact() = runTest {
        val restoreRoot = newTestRoot("safety-overwrite")
        val wrapper = IsolatedSyncTestContext(ApplicationProvider.getApplicationContext(), restoreRoot)
        val oldBytes = "S14 旧版书源：这是本地唯一完好副本，绝不能被截断或半写入覆盖。".toByteArray()
        val dstRel = "books/s14-over-1/content.txt"
        val dstFile = File(wrapper.filesDir, dstRel)
        dstFile.parentFile!!.mkdirs()
        dstFile.writeBytes(oldBytes)

        val db = openPersistentDb()
        val bridge = buildBridge(db)
        // 本地行 rev1；备份行 rev2 → 合并时被接受，文件复制必然被触发
        db.bookDao().upsert(bookEntity("s14-over-1", "覆盖场景书", "txt", oldBytes.size))

        val backupRow = bookEntity("s14-over-1", "覆盖场景书(备份)", "txt", 4096).copy(revision = 2)
        val export = LocalExport(
            exportedAt = STABLE_TIME,
            deviceId = "s14-isolated-test",
            books = listOf(bookEnvelope(backupRow)),
            inspirations = emptyList(),
            progress = emptyList(),
            sessions = emptyList(),
        )
        val zipFile = File(wrapper.cacheDir, "s14-safety.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("backup.json"))
            zos.write(json.encodeToString(export).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry(dstRel))
            zos.write("S14 备份版书源字节".toByteArray() + ByteArray(2048))
            zos.closeEntry()
        }

        val backup = LocalZipBackup(bridge, Dispatchers.Unconfined)
        backup.fileCopyOperation = { input, _ ->
            val buf = ByteArray(16)
            input.read(buf)
            throw IOException("S14 注入：复制中途失败")
        }
        val outcome = runCatching { backup.importZip(wrapper, Uri.fromFile(zipFile)) }

        assertTrue("注入的写中途失败必须上抛", outcome.exceptionOrNull() is IOException)
        assertEquals("旧文件必须逐字节保留", sha256(oldBytes), sha256(dstFile.readBytes()))
        assertTrue(
            "失败后不得留下 staging 残留",
            dstFile.parentFile!!.listFiles()!!.none { it.name.endsWith(".staging") },
        )
        // DB 先提交的边界如实保留：备份行 rev2 已生效
        assertEquals("DB 行必须已提交为备份行", 2, db.bookDao().getById("s14-over-1")?.revision)
        db.close()
        openedDatabases.remove(db)
    }

    // ============ 用例 5（S14 安全修复）：替换失败保留旧目标且绝不直接写目标 ============

    /**
     * 目标位置预置同名目录（夹具自洽：该路径只有「目录」一个角色，无旧文件）。
     * 目标为目录时替换在 staging 之前就被拒绝（不得把来历不明的目录搬走后删除，
     * 更不得退回直接对目标写流）→ 断言异常、目录原样保留、无任何残留。
     * 同时证明 DB 行已提交。
     */
    @Test
    fun replaceFailureKeepsTargetAndThrowsInsteadOfDirectOverwrite() = runTest {
        val restoreRoot = newTestRoot("safety-replace")
        val wrapper = IsolatedSyncTestContext(ApplicationProvider.getApplicationContext(), restoreRoot)
        val obstacle = File(wrapper.filesDir, "books/s14-obs-1/content.txt")
        obstacle.mkdirs()

        val db = openPersistentDb()
        val bridge = buildBridge(db)

        val backupRow = bookEntity("s14-obs-1", "替换失败场景书", "txt", 1024).copy(revision = 5)
        val export = LocalExport(
            exportedAt = STABLE_TIME,
            deviceId = "s14-isolated-test",
            books = listOf(bookEnvelope(backupRow)),
            inspirations = emptyList(),
            progress = emptyList(),
            sessions = emptyList(),
        )
        val zipFile = File(wrapper.cacheDir, "s14-replace.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("backup.json"))
            zos.write(json.encodeToString(export).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("books/s14-obs-1/content.txt"))
            zos.write("S14 备份版字节".toByteArray())
            zos.closeEntry()
        }

        val outcome = runCatching {
            LocalZipBackup(bridge, Dispatchers.Unconfined).importZip(wrapper, Uri.fromFile(zipFile))
        }

        assertTrue(
            "rename 失败必须上抛（IllegalStateException），不得静默或改写目标",
            outcome.exceptionOrNull() is IllegalStateException,
        )
        assertTrue("旧目标（目录）必须原样保留", obstacle.isDirectory)
        assertTrue(
            "失败后不得留下 staging 残留",
            obstacle.parentFile!!.listFiles()!!.none { it.name.endsWith(".staging") },
        )
        assertEquals("DB 行必须已提交为备份行", 5, db.bookDao().getById("s14-obs-1")?.revision)
        db.close()
        openedDatabases.remove(db)
    }

    // ============ 用例 6（S14 安全修复）：成功路径的版本偏斜必须被消除 ============

    /**
     * 旧行为可复现失败（无需故障）：本地行 revision/updated_at 较新、备份中同一书较旧时，
     * DB 合并正确跳过该行，但文件复制无条件覆盖——本地新书源文件被改成旧备份字节。
     * 本用例以行字段与文件 SHA-256 两轴对账：旧行为下文件轴必然失败。
     * 保护依据是 [JsonBridge.importFromString] 回报的实际合并结果（跳过行的
     * `local_content_path`），不看 ZIP 是否含该文件。同备份中的全新书籍照常恢复。
     */
    @Test
    fun newerLocalRowKeepsNewerFileBytesOnSuccessfulRestore() = runTest {
        val restoreRoot = newTestRoot("safety-skew")
        val wrapper = IsolatedSyncTestContext(ApplicationProvider.getApplicationContext(), restoreRoot)
        val newerBytes = "S14 本地新版书源内容：比备份新，绝不能被备份旧字节覆盖。".toByteArray()
        val olderBytes = "S14 备份旧版书源内容。".toByteArray()
        val freshBytes = "S14 全新书籍的书源内容。".toByteArray()
        val skewRel = "books/s14-skew-1/content.txt"
        val skewFile = File(wrapper.filesDir, skewRel)
        skewFile.parentFile!!.mkdirs()
        skewFile.writeBytes(newerBytes)

        val db = openPersistentDb()
        val bridge = buildBridge(db)
        val localRow = bookEntity("s14-skew-1", "本地较新书", "txt", newerBytes.size).copy(
            revision = 7,
            updated_at = "2026-09-28T12:00:00Z",
            local_content_path = skewFile.absolutePath,
        )
        db.bookDao().upsert(localRow)

        val backupSkewRow = bookEntity("s14-skew-1", "本地较新书(备份)", "txt", olderBytes.size).copy(
            revision = 6,
            updated_at = STABLE_TIME,
        )
        val freshRow = bookEntity("s14-fresh-1", "全新书籍", "txt", freshBytes.size)
        val export = LocalExport(
            exportedAt = STABLE_TIME,
            deviceId = "s14-isolated-test",
            books = listOf(bookEnvelope(backupSkewRow), bookEnvelope(freshRow)),
            inspirations = emptyList(),
            progress = emptyList(),
            sessions = emptyList(),
        )
        val zipFile = File(wrapper.cacheDir, "s14-skew.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("backup.json"))
            zos.write(json.encodeToString(export).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry(skewRel))
            zos.write(olderBytes)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("books/s14-fresh-1/content.txt"))
            zos.write(freshBytes)
            zos.closeEntry()
        }

        LocalZipBackup(bridge, Dispatchers.Unconfined).importZip(wrapper, Uri.fromFile(zipFile))

        // 行轴：本地较新行必须原样保留（revision/updated_at/标题都不被备份覆盖）
        assertEquals("本地较新书籍行必须原样保留", localRow, db.bookDao().getById("s14-skew-1"))
        // 文件轴：本地较新文件必须保持新字节（旧行为下这里会变成 olderBytes）
        assertEquals("本地较新书源文件必须保持新字节", sha256(newerBytes), sha256(skewFile.readBytes()))
        // 被接受的全新书籍照常恢复（行 + 文件）
        assertEquals("全新书籍行必须恢复", freshRow, db.bookDao().getById("s14-fresh-1"))
        assertEquals(
            "全新书籍文件必须恢复",
            sha256(freshBytes),
            sha256(File(wrapper.filesDir, "books/s14-fresh-1/content.txt").readBytes()),
        )
        assertTrue(
            "成功恢复后不得留下 staging 残留",
            File(wrapper.filesDir, "books").walkTopDown().filter { it.isFile }
                .none { it.name.endsWith(".staging") },
        )
        db.close()
        openedDatabases.remove(db)
    }

    // ================= 隔离基建 =================

    /**
     * filesDir / cacheDir / applicationContext 重定向到测试专属唯一根目录；
     * [getSharedPreferences] 返回内存实现（拦截 SecurePrefs 的加密 prefs 读写）；
     * [deleteSharedPreferences] 空操作（拦截 SecurePrefs 降级重试路径对真实 prefs 的删除）。
     * 设计依据与边界记录见本文件 KDoc 与对应报告。
     */
    private class IsolatedSyncTestContext(
        base: Context,
        val testRoot: File,
    ) : ContextWrapper(base) {

        val filesRoot: File = File(testRoot, "files")
        val cacheRoot: File = File(testRoot, "cache")
        private val prefsByName = mutableMapOf<String, MutableMap<String, Any?>>()

        override fun getFilesDir(): File = filesRoot.also { it.mkdirs() }

        override fun getCacheDir(): File = cacheRoot.also { it.mkdirs() }

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
            InMemorySharedPreferences(prefsByName.getOrPut(name ?: "default") { mutableMapOf() })

        override fun deleteSharedPreferences(name: String?): Boolean = true
    }

    /** 纯内存 [SharedPreferences]：给 SecurePrefs / EncryptedSharedPreferences 当 delegate。 */
    private class InMemorySharedPreferences(
        private val values: MutableMap<String, Any?>,
    ) : SharedPreferences {

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (values[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = InMemoryEditor()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class InMemoryEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = put(key, value)
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = put(key, values)
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = put(key, value)
            override fun remove(key: String?): SharedPreferences.Editor { removals.add(key!!); return this }
            override fun clear(): SharedPreferences.Editor { clearAll = true; return this }
            override fun commit(): Boolean { applyChanges(); return true }
            override fun apply() { applyChanges() }

            private fun put(key: String?, value: Any?): SharedPreferences.Editor {
                pending[key!!] = value
                return this
            }

            private fun applyChanges() {
                if (clearAll) values.clear()
                removals.forEach { values.remove(it) }
                values.putAll(pending)
                pending.clear()
                removals.clear()
                clearAll = false
            }
        }
    }

    // ================= 构造与对账辅助 =================

    private fun openPersistentDb(): AppDatabase =
        Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
            TEST_DB_NAME,
        ).allowMainThreadQueries().build().also { openedDatabases.add(it) }

    /** 与既有 LocalExportRoomRollbackTest 相同的手工装配；三个 S14 DAO 显式传入。 */
    private fun buildBridge(database: AppDatabase): JsonBridge = JsonBridge(
        bookDao = database.bookDao(),
        inspirationDao = database.inspirationDao(),
        progressDao = database.readingProgressDao(),
        sessionDao = database.readingSessionDao(),
        noteDao = database.noteDao(),
        highlightDao = database.highlightDao(),
        tagDao = database.tagDao(),
        categoryDao = database.categoryDao(),
        shelfDao = database.shelfDao(),
        bookTagDao = database.bookTagDao(),
        bookCategoryDao = database.bookCategoryDao(),
        shelfBookDao = database.shelfBookDao(),
        chapterReadDao = database.chapterReadDao(),
        ioDispatcher = Dispatchers.Unconfined,
        sourceRefDao = database.librarySourceRefDao(),
        textRuleDao = database.readerTextRuleDao(),
        correctionDao = database.readerCorrectionDao(),
    )

    /** 五类记录的规范逻辑摘要（排序后逐行哈希）：跨关闭/重开的最终对账凭证。 */
    private suspend fun canonicalDigest(db: AppDatabase): String {
        val books = db.bookDao().observeAllActive().first().sortedBy { it.id }
        val progress = db.readingProgressDao().observeAllActive().first().sortedBy { it.book_id }
        val refs = db.librarySourceRefDao().getAll().sortedBy { it.book_id }
        val rules = db.readerTextRuleDao().observeAll().first().sortedBy { it.id }
        val corrections = books.flatMap { book ->
            db.readerCorrectionDao().listForBook(book.id).sortedBy { it.id }
                .map { "corr|${book.id}|${it.id}|${it.source_start}|${it.source_end}|${it.find_text}|${it.replace_text}|${it.status}|${it.updated_at}" }
        }
        val lines = buildList {
            books.forEach { add("book|${it.id}|${it.title}|${it.format}|${it.size}|${it.revision}|${it.updated_at}") }
            progress.forEach { add("progress|${it.book_id}|${it.progress_percent}|${it.completion_state}|${it.current_location_json}|${it.revision}|${it.updated_at}") }
            refs.forEach { add("ref|${it.book_id}|${it.provider_authority}|${it.document_id}|${it.size}|${it.last_modified}|${it.content_hash}|${it.last_seen_at}|${it.availability}") }
            rules.forEach { add("rule|${it.id}|${it.name}|${it.pattern}|${it.replacement}|${it.builtin}|${it.enabled}|${it.scope}|${it.book_id}|${it.position}|${it.created_at}|${it.updated_at}") }
            addAll(corrections)
        }.sorted()
        return sha256(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
    }

    private fun newTestRoot(tag: String): File =
        File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "$TEST_DB_NAME-root-$tag-${System.nanoTime()}")
            .also { testRoots.add(it) }

    private fun deleteTestDbFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val base = context.getDatabasePath(TEST_DB_NAME)
        listOf(base, File(base.path + "-wal"), File(base.path + "-shm"), File(base.path + "-journal"))
            .forEach { runCatching { it.delete() } }
    }

    private fun bookEntity(id: String, title: String, format: String, size: Int) = BookEntity(
        id = id,
        title = title,
        format = format,
        size = size,
        content_status = "available",
        imported_at = STABLE_TIME,
        revision = 1,
        updated_at = STABLE_TIME,
    )

    private fun progressEntity(bookId: String, percent: Float, location: String) = ReadingProgressEntity(
        book_id = bookId,
        progress_percent = percent,
        completion_state = "reading",
        current_location_json = location,
        revision = 2,
        updated_at = STABLE_TIME,
    )

    private fun ruleEntity() = ReaderTextRuleEntity(
        id = "s14-rule-1",
        kind = "replace",
        name = "S14 导出规则",
        pattern = "测试TXT旧词",
        replacement = "测试TXT新词",
        builtin = false,
        enabled = true,
        scope = "global",
        book_id = null,
        position = 1,
        created_at = FIXED_MILLIS,
        updated_at = FIXED_MILLIS,
    )

    private fun correctionEntity(bookId: String) = ReaderCorrectionEntity(
        id = "s14-corr-1",
        book_id = bookId,
        source_start = 10,
        source_end = 15,
        find_text = "旧句",
        replace_text = "新句",
        status = ReaderCorrectionEntity.STATUS_ACTIVE,
        created_at = FIXED_MILLIS + 1,
        updated_at = FIXED_MILLIS + 1,
    )

    private fun sourceRefEntity(bookId: String, size: Long, hash: String) = LibrarySourceRefEntity(
        book_id = bookId,
        root_id = null,
        provider_authority = "test.authority",
        document_id = "doc-$bookId",
        display_name = if (bookId.contains("epub")) "测试 EPUB" else "测试 TXT",
        format = if (bookId.contains("epub")) "epub" else "txt",
        size = size,
        last_modified = FIXED_MILLIS,
        content_hash = hash,
        candidate_fingerprint = null,
        last_seen_at = FIXED_MILLIS + 500,
        availability = "available",
    )

    private fun bookEnvelope(book: BookEntity) = SyncEnvelope(
        id = book.id,
        type = "book",
        revision = book.revision,
        deviceId = "s14-remote-device",
        updatedAt = book.updated_at,
        deletedAt = null,
        payload = book,
    )

    private fun progressEnvelope(progress: ReadingProgressEntity) = SyncEnvelope(
        id = progress.book_id,
        type = "progress",
        revision = progress.revision,
        deviceId = "s14-remote-device",
        updatedAt = progress.updated_at,
        deletedAt = null,
        payload = progress,
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        /**
         * 唯一命名的持久测试库。绝不可与 `AppDatabase.DB_NAME`
         * （"creation_reading_assistant_native"）或任何既有库名重合。
         */
        private const val TEST_DB_NAME = "s14_persistent_backup_restore_test.db"

        private const val STABLE_TIME = "2026-09-28T00:00:00Z"
        private const val FIXED_MILLIS = 1_790_000_000_000L
    }
}
