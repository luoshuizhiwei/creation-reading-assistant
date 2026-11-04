package com.creationreadingassistant.feature.reader

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.entity.BookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * S04 EPUB 替换链路的**真实 Room 隔离回归测试**（非 Compose）。
 *
 * 与 JVM 版 `EpubRepositoryReplaceTest`（mock DAO + TemporaryFolder + Unconfined）互补：
 * 这里 DAO 是**真实 Room + SQLite**，能证明 JVM mock 证明不了的三件事——
 * 1. `bookDao.update` 在真实 SQLite 上**不会**触发外键级联（进度行物理存活）；
 * 2. `content_hash` / `size` 写入的是真实 SQL UPDATE 的最终列值；
 * 3. 替换失败时真实 Room 里没有任何部分写入残留。
 *
 * ## 三个必须分开陈述的隔离命题（不得混为一谈）
 *
 * | 命题 | 本测试的状态 | 依据 |
 * |---|---|---|
 * | **A. 测试 DAO 隔离** | ✅ **成立** | 只用 `Room.inMemoryDatabaseBuilder`；文件内无 `Room.databaseBuilder`；[databaseUnderTestIsInMemoryAndNeverTheProductionDatabase] 运行期钉住 helper 名 |
 * | **B. 测试文件隔离** | ✅ **成立** | 仓储拿到 [IsolatedTestContext]，`filesDir`/`cacheDir` 指向本测试唯一根目录；写入与清理前一律经 `assertInsideRoot`；**目标包**的 `files/books/epub/` 不在任何被测写入路径上（目标包是 qahost 沙箱还是正式包，取决于构建变体，见 [replaceNeverWritesIntoProductionFilesDirectory]） |
 * | **C. 测试进程不执行正式 `App.onCreate`** | ✅ **成立**（2026-09-25 更正，原表述已过时） | `app/build.gradle.kts` 的 `defaultConfig` 已把 `testInstrumentationRunner` 指向 `com.creationreadingassistant.testing.CraIsolatedTestRunner`；该 Runner 覆写 `newApplication` 注入 `CraIsolatedTestApplication`（裸 `Application`，**不继承**生产 `App`）⇒ 三条 `cra-startup` 后台接线（EPUB 修复 / 目标调度 / 搜索索引）在测试进程中**不会执行** |
 *
 * **不得**由 A、B、C 成立推断「正式应用的一切设备状态绝对安全」—— 见下方「已知残余」。
 *
 * ### 命题 C 的准确边界（两个不同命题，勿合并）
 *
 * - **「qahost 测试进程使用隔离 Application」= 成立**：本测试进程内的目标 Application
 *   是 `CraIsolatedTestApplication`，`App.onCreate` 不执行，因而**不会**经 Hilt 构造
 *   正式 `AppDatabase`，也不触发搜索索引 / 目标调度 / 系统夜间模式设置。
 * - **「正式应用的所有设备状态绝对安全」= 未证明，且不成立**：换 Application 只改变
 *   **测试进程内**的 Application 实例，不改变设备上的其他事实。已知残余至少包括：
 *   1. `androidx.startup` 的 `InitializationProvider`（ContentProvider）由主 APK 合并清单驱动，
 *      **先于** `Application.onCreate` 执行，`WorkManagerInitializer` 等仍会运行并创建应用
 *      私有目录内的 `androidx.work.workdb`（已在本仓库 `CraIsolatedTestApplication` KDoc
 *      中登记；该库不是 Room 正式库，且换 Application 无法影响 ContentProvider 时序）；
 *   2. 测试进程与设备上的其他状态（Keystore、系统设置、包管理器状态、其他应用数据）
 *      不在本测试可控范围；
 *   3. 正式 APK 自身仍是 `com.creationreadingassistant.App`，行为不受本文件影响。
 *
 * 引用本测试时按上述边界表述；**不得**把「测试进程已隔离 Application」写成
 * 「正式应用数据绝对安全」。
 *
 * > 历史说明（保留以免读者困惑）：本表 C 行此前的表述为「❌ 不成立 / 未消除，
 * > 已提交 SEAM REQUEST」。那是在集成者把 `testInstrumentationRunner` 切到
 * > `CraIsolatedTestRunner` **之前**的状态。该 SEAM 已落地，故此行更正为 ✅；
 * > 上述「已知残余」与「绝对安全未证明」两点**不因此 SEAM 而改变**。
 *
 * ## 真实 / 替身边界（引用结论时必须注明）
 *
 * **真实**：
 * - Room + SQLite（内存库，见命题 A）；
 * - `EpubParser` 全链路（真实 ZipFile / container.xml / OPF / spine 解析）；
 * - 真实 `ContentResolver` + `file://` Uri 读取（与真机导入同一代码路径）；
 * - 真实文件系统 —— 但写入根被重定向到测试专属目录（见命题 B），
 *   断言针对 `testRoot/files/books/epub/` 下的 target / staging / backup；
 * - 真实 `MessageDigest` MD5（独立于 `EpubRepository.computeFileHash` 再算一遍做交叉核对）。
 *
 * **替身 / 注入**（均不改变生产语义）：
 * - [IsolatedTestContext]：仅重定向 `filesDir` / `cacheDir`，其余全部委托真实 Context；
 * - [DirectFileOps]：与生产 `DefaultFileOps` 逐字同义的两行转发（生产实现是 private，
 *   测试无法引用；行为等价性由其转发语义保证）；
 * - [FailingUpdateBookDao]：仅在「替换已落盘、后置写库」那一刻注入一次失败
 *   （`BookDao by delegate` 类委托，其余方法全部转发真实 DAO）。
 *
 * ## 安全边界
 *
 * - 数据库只用 `inMemoryDatabaseBuilder`；不打开、不清空、不修改应用正式数据库；
 * - 来源 EPUB 由测试合成，内部副本由被测代码写入，二者**同在** `testRoot` 之下；
 * - 清理只删 `testRoot` 内、且经 `assertInsideRoot` 复核的路径（整个唯一根目录），
 *   不再依据 bookId 去目标包的书目录里逐个删文件；
 * - 不读取用户书库或既有设备书籍的任何内容。
 */
@RunWith(AndroidJUnit4::class)
class EpubReplaceRoomIsolatedTest {

    /** 真实应用 Context，只用于构建内存库与作为包装基底；**不直接交给仓储**。 */
    private lateinit var appContext: Context

    /** 交给仓储的隔离 Context：`filesDir` / `cacheDir` 均指向 [testRoot]。 */
    private lateinit var isolated: IsolatedTestContext

    private lateinit var database: AppDatabase

    /** 本次测试的唯一根目录；来源与内部副本全部在其下。 */
    private lateinit var testRoot: File

    /** 来源 EPUB 目录：`<testRoot>/sources`。 */
    private lateinit var sourceDir: File

    /** 被测代码写入的内部副本根：`<testRoot>/files/books/epub`。 */
    private lateinit var epubDir: File

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        // 唯一根目录：用真实 cacheDir 作为「父」只是为了拿到一个可写位置，
        // 根本身仍是唯一命名的独立目录，且其下所有路径都受 assertInsideRoot 约束。
        testRoot = File(appContext.cacheDir, "s04-epub-room-iso-${UUID.randomUUID()}")
        assertTrue("测试专属根目录必须创建成功", testRoot.mkdirs())
        isolated = IsolatedTestContext(appContext, testRoot)
        sourceDir = File(testRoot, "sources")
        assertTrue("来源目录必须创建成功", sourceDir.mkdirs())
        epubDir = File(isolated.filesDir, "books/epub")

        database = Room
            .inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        // 清理只作用于测试专属根目录本身：先经 assertInsideRoot 复核，
        // 再整目录删除。不再依据 bookId 去目标包的书目录里逐个删文件。
        isolated.assertInsideRoot(testRoot).deleteRecursively()
    }

    // ─── 契约 1：同一来源 URI 的 V1→V2 ────────────────────────────────

    /**
     * 同一来源 URI 内容 V1→V2 后替换必须读到**新字节**（S04 §9-1 根因现场）：
     *
     * 断言清单：
     * 1. 返回值 title 来自 V2；
     * 2. 最终内部正文 target 与 V2 来源**逐字节相同**（含 V2MARKER、不含 V1MARKER）；
     * 3. books 行：**bookId 不变**（不新增、不软删，countActive==1）、
     *    `size` == 新文件实际字节数、`content_hash` == 独立计算的 MD5、
     *    `local_content_path` 指向 target、`content_status == "available"`；
     * 4. 已有阅读进度**关系不丢失**：真实 Room 中 reading_progress 行仍在，
     *    percent 与 current_location_json 原样保留（真实 UPDATE 不触发级联删除）；
     * 5. 无 `.importing` / `.repairing` / `.backup` 残留；
     * 6. 进程内解析缓存已刷新为 V2（否则阅读器可能继续读旧缓存标题）。
     */
    @Test
    fun sameUriV1ToV2ReplaceReadsFreshBytesAndKeepsBookIdAndProgress() = runTest {
        val repository = newRepository()
        val source = File(sourceDir, "same-uri-source.epub")
        writeEpub(source, title = "V1旧标题", bodyMarker = "V1MARKER旧正文内容")
        val uri = Uri.fromFile(source)

        // 导入：真实 parse + 真实 Room 写入。openEpub 不传 expectedBookId ⇒ bookId 按
        // Uri 派生 —— 这正是 S04 根因现场（派生 id == bookId ⇒ 旧实现早退必然命中）。
        val v1 = repository.openEpub(uri)
        // 路径不变量：内部副本必须落在测试专属目录内，而非目标包自己的 books 目录。
        val target = isolated.assertInsideRoot(File(epubDir, "${v1.id}.epub"))
        assertTrue("导入后必须存在内部副本", target.isFile)
        assertInsideRootSelfCheck(target)
        val v1Bytes = target.readBytes()
        assertTrue(
            "前提自检：导入的内部副本应含 V1 标记",
            v1Bytes.toString(Charsets.UTF_8).contains("V1MARKER"),
        )

        // 已有阅读进度（真实 Room 外键：books → reading_progress）
        repository.saveProgress(
            bookId = v1.id,
            chapterIndex = 0,
            percent = 42.5f,
            offsetInChapter = 77,
            absoluteOffset = 1234,
        )
        val progressBefore = database.readingProgressDao().getByBook(v1.id)
        assertNotNull("前提自检：进度行必须已写入", progressBefore)

        // 同一 URI，来源内容更新为 V2
        writeEpub(source, title = "V2全新标题", bodyMarker = "V2MARKER新正文内容")

        val replaced = repository.replaceStoredEpub(
            stored = database.bookDao().getById(v1.id)!!,
            selectedUri = uri,
            selectedFileName = "same-uri-source.epub",
        )

        // 1) 返回值来自 V2
        assertEquals("V2全新标题", replaced.title)

        // 2) 最终内部正文逐字节 == V2 来源
        assertArrayEquals("内部正文必须逐字节等于 V2 来源", source.readBytes(), target.readBytes())
        val targetText = target.readBytes().toString(Charsets.UTF_8)
        assertTrue("内部正文必须含 V2 标记", targetText.contains("V2MARKER"))
        assertFalse("内部正文不得残留 V1 标记", targetText.contains("V1MARKER"))

        // 3) books 行与实际新文件一致，bookId 不变
        val row = database.bookDao().getById(v1.id)
        assertNotNull("替换后书籍行必须仍在", row)
        assertEquals("bookId 必须保持不变", v1.id, row!!.id)
        assertEquals("size 必须等于新文件实际字节数", target.length().toInt(), row.size)
        assertEquals(
            "content_hash 必须等于新文件 MD5",
            md5Of(target.readBytes()),
            row.content_hash,
        )
        assertEquals("local_content_path 必须指向 target", target.absolutePath, row.local_content_path)
        assertEquals("available", row.content_status)
        assertEquals("不得新增或软删书籍行", 1, database.bookDao().countActive())

        // 4) 阅读进度关系不丢失（真实 Room：UPDATE 不删行 ⇒ 外键关系原样存活）
        val progressAfter = database.readingProgressDao().getByBook(v1.id)
        assertNotNull("替换后进度行必须仍在（级联删除会把整段阅读历史抹掉）", progressAfter)
        assertEquals(42.5f, progressAfter!!.progress_percent, 0.001f)
        assertEquals(
            "进度位置不得被重置",
            progressBefore!!.current_location_json,
            progressAfter.current_location_json,
        )

        // 5) 无残留 6) 缓存刷新
        assertNoResidue(v1.id)
        assertEquals("进程内解析缓存必须刷新为 V2", "V2全新标题", repository.cached(v1.id)?.title)
    }

    // ─── 契约 2：无效 EPUB ────────────────────────────────────────────

    /**
     * 来源变为无效 EPUB 时替换必须被拒绝，且**不推进任何状态**：
     *
     * 断言清单：
     * 1. 必须抛异常（复制成功但 ZipFile 校验失败 —— 走「先落盘后校验」的解析阶段失败面）；
     * 2. 旧内部正文**逐字节不变**；
     * 3. books 行整行不变（数据类全字段相等，含 updated_at / content_hash / size）；
     * 4. 阅读进度行整行不变；
     * 5. 不留下本次操作产生的孤儿副本（.importing/.repairing/.backup 全部不存在）。
     */
    @Test
    fun invalidEpubReplaceIsRejectedAndPreservesEverything() = runTest {
        val repository = newRepository()
        val source = File(sourceDir, "invalid-source.epub")
        writeEpub(source, title = "V1旧标题", bodyMarker = "V1MARKER旧正文内容")
        val uri = Uri.fromFile(source)

        val v1 = repository.openEpub(uri)
        val target = isolated.assertInsideRoot(File(epubDir, "${v1.id}.epub"))
        assertInsideRootSelfCheck(target)
        val v1Bytes = target.readBytes()
        repository.saveProgress(bookId = v1.id, chapterIndex = 1, percent = 66f)
        val rowBefore = database.bookDao().getById(v1.id)!!
        val progressBefore = database.readingProgressDao().getByBook(v1.id)!!

        // 来源变成损坏数据：复制会成功、ZipFile 必然抛错
        source.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))

        val caught = try {
            repository.replaceStoredEpub(
                stored = database.bookDao().getById(v1.id)!!,
                selectedUri = uri,
                selectedFileName = "invalid-source.epub",
            )
            null
        } catch (e: Throwable) {
            e
        }
        assertNotNull("损坏 EPUB 必须被拒绝", caught)
        assertTrue(
            "必须是普通异常而非断言失败，实际=${caught!!.javaClass.simpleName}: ${caught.message}",
            caught is Exception && caught !is AssertionError,
        )

        // 旧内部正文逐字节不变
        assertArrayEquals("旧内部正文必须逐字节不变", v1Bytes, target.readBytes())
        // books / 进度不推进（真实 Room：失败路径没有任何部分写入）
        assertEquals("books 行不得推进", rowBefore, database.bookDao().getById(v1.id))
        assertEquals("阅读进度行不得推进", progressBefore, database.readingProgressDao().getByBook(v1.id))
        assertEquals(1, database.bookDao().countActive())
        // 不留孤儿
        assertNoResidue(v1.id)
    }

    // ─── 契约 3a：来源读取失败 ────────────────────────────────────────

    /**
     * 来源 Uri 不可读（文件不存在）时替换必须失败，且不得动旧正文：
     *
     * 断言清单：
     * 1. 抛 IllegalStateException 且消息为「无法读取所选 EPUB 来源，请重新选择文件」
     *    （新鲜来源模式下**绝不**退回「复用旧副本」的兜底 —— 那会把旧内容伪装成新来源）；
     * 2. 旧内部正文逐字节不变；
     * 3. books / 进度不推进；
     * 4. 无孤儿副本。
     *
     * 注：异常可能跨 `withContext(Dispatchers.IO)` 边界被协程栈恢复复制，
     * 因此断言用「类型 + 消息」而不是对象同一性。
     */
    @Test
    fun unreadableSourceIsRejectedWithoutTouchingTarget() = runTest {
        val repository = newRepository()
        val source = File(sourceDir, "unreadable-source.epub")
        writeEpub(source, title = "V1旧标题", bodyMarker = "V1MARKER旧正文内容")
        val uri = Uri.fromFile(source)

        val v1 = repository.openEpub(uri)
        val target = isolated.assertInsideRoot(File(epubDir, "${v1.id}.epub"))
        assertInsideRootSelfCheck(target)
        val v1Bytes = target.readBytes()
        repository.saveProgress(bookId = v1.id, chapterIndex = 0, percent = 10f)
        val rowBefore = database.bookDao().getById(v1.id)!!
        val progressBefore = database.readingProgressDao().getByBook(v1.id)!!

        val missing = File(sourceDir, "missing.epub")
        assertTrue("前提自检：来源文件必须确实不存在", !missing.exists())

        val caught = try {
            repository.replaceStoredEpub(
                stored = database.bookDao().getById(v1.id)!!,
                selectedUri = Uri.fromFile(missing),
                selectedFileName = "missing.epub",
            )
            null
        } catch (e: Throwable) {
            e
        }
        assertNotNull("不可读来源必须被拒绝", caught)
        assertTrue(
            "必须以「无法读取所选 EPUB 来源」失败，实际=${caught!!.javaClass.simpleName}: ${caught.message}",
            caught is IllegalStateException &&
                caught.message == "无法读取所选 EPUB 来源，请重新选择文件",
        )

        assertArrayEquals("旧内部正文必须逐字节不变", v1Bytes, target.readBytes())
        assertEquals("books 行不得推进", rowBefore, database.bookDao().getById(v1.id))
        assertEquals("阅读进度行不得推进", progressBefore, database.readingProgressDao().getByBook(v1.id))
        assertNoResidue(v1.id)
    }

    // ─── 契约 3b：后置数据库写入失败 ─────────────────────────────────

    /**
     * 文件替换已落盘、后置 `bookDao.update` 写库失败时，必须整体回滚：
     *
     * 注入方式：[FailingUpdateBookDao] 以类委托转发全部真实 DAO 方法，只在
     * `failUpdates = true` 期间对 `update` 抛 IOException —— 注入点正是
     * `replaceStoredEpub` 成功路径中**唯一**的 DAO 写入（swap 完成后那次 update）。
     * 导入（openEpub 首次，existing==null 走 upsert）与进度写入不受影响。
     *
     * 断言清单：
     * 1. 抛出注入的 IOException（类型 + 消息）；
     * 2. target **逐字节恢复为 V1**（从 backup 回滚，而不是留下半截 V2）；
     * 3. 真实 Room 的 books 行保持替换前原样（失败的 UPDATE 不留部分写入）；
     * 4. 阅读进度行保持原样；
     * 5. 无 .backup/.repairing/.importing 残留（backup 已在回滚中消耗）。
     */
    @Test
    fun postSwapDatabaseWriteFailureRollsBackFileAndKeepsOldRow() = runTest {
        val dao = FailingUpdateBookDao(database.bookDao())
        val repository = newRepository(bookDao = dao)
        val source = File(sourceDir, "db-failure-source.epub")
        writeEpub(source, title = "V1旧标题", bodyMarker = "V1MARKER旧正文内容")
        val uri = Uri.fromFile(source)

        val v1 = repository.openEpub(uri)
        val target = isolated.assertInsideRoot(File(epubDir, "${v1.id}.epub"))
        assertInsideRootSelfCheck(target)
        val v1Bytes = target.readBytes()
        repository.saveProgress(bookId = v1.id, chapterIndex = 2, percent = 88f)
        val rowBefore = database.bookDao().getById(v1.id)!!
        val progressBefore = database.readingProgressDao().getByBook(v1.id)!!

        writeEpub(source, title = "V2全新标题", bodyMarker = "V2MARKER新正文内容")
        dao.failUpdates = true

        val caught = try {
            repository.replaceStoredEpub(
                stored = database.bookDao().getById(v1.id)!!,
                selectedUri = uri,
                selectedFileName = "db-failure-source.epub",
            )
            null
        } catch (e: Throwable) {
            e
        }
        assertNotNull("注入的写库失败必须向上抛出", caught)
        assertTrue(
            "必须是注入的 IOException，实际=${caught!!.javaClass.simpleName}: ${caught.message}",
            caught is IOException && caught.message == "注入：后置数据库写入失败",
        )

        // 文件回滚：target 逐字节恢复为 V1
        assertArrayEquals("target 必须从 backup 逐字节恢复为 V1", v1Bytes, target.readBytes())
        // 真实 Room 行保持旧值
        assertEquals("books 行必须保持替换前原样", rowBefore, database.bookDao().getById(v1.id))
        assertEquals("阅读进度行必须保持原样", progressBefore, database.readingProgressDao().getByBook(v1.id))
        assertEquals(1, database.bookDao().countActive())
        assertNoResidue(v1.id)
    }

    // ─── 隔离性证明 ──────────────────────────────────────────────────

    /**
     * 运行期钉住「测试库不是正式库」：in-memory Room 库的 helper 名为 null，
     * 绝不会是 `creation_reading_assistant_native`。
     * （结构性证明：本文件只调用 `Room.inMemoryDatabaseBuilder`，
     * 不含任何 `Room.databaseBuilder` —— 可 grep 复核。）
     */
    @Test
    fun databaseUnderTestIsInMemoryAndNeverTheProductionDatabase() {
        val name = database.openHelper.databaseName
        assertTrue(
            "测试库不得使用正式库名，实际=$name",
            name != AppDatabase.DB_NAME,
        )
    }

    /**
     * 运行期钉住**文件隔离**（命题 B）：一次完整导入+替换之后，
     * **目标包**的 `files/books/epub/` 目录必须不存在，或至少不含本次 bookId 的任何文件。
     *
     * 这条是对 [IsolatedTestContext] 是否真正生效的端到端反证：
     * 前面几个用例断言「文件在 testRoot 下」，本用例反向断言
     * 「目标包的书目录里没有这本书的任何痕迹」。
     *
     * **目标包是谁取决于构建变体**（此处必须准确，勿一概称「正式目录」）：
     * - `-PcraAndroidTestBuildType=qahost`：targetPackage =
     *   `com.creationreadingassistant.qahost` ⇒ 本用例检查的是**沙箱包**的私有目录；
     * - 默认 `debug`：targetPackage = `com.creationreadingassistant`
     *   ⇒ 检查的才是**正式包**的私有目录。
     *
     * 两种变体下本断言都成立且都有意义：它证明被测代码经 [IsolatedTestContext]
     * 重定向后**没有**落进目标包自己的 `books/epub/`。但**不得**据此声称
     * 「正式应用的所有设备状态绝对安全」—— 变体差异与命题 C 的残余见类级 KDoc。
     */
    @Test
    fun replaceNeverWritesIntoProductionFilesDirectory() = runTest {
        val repository = newRepository()
        val source = File(sourceDir, "prod-isolation-probe.epub")
        writeEpub(source, title = "隔离探针", bodyMarker = "PROBEMARKER正文")

        val book = repository.openEpub(Uri.fromFile(source))
        writeEpub(source, title = "隔离探针V2", bodyMarker = "PROBEMARKER正文V2")
        repository.replaceStoredEpub(
            stored = database.bookDao().getById(book.id)!!,
            selectedUri = Uri.fromFile(source),
            selectedFileName = "prod-isolation-probe.epub",
        )

        // 内部副本落在测试目录内
        val isolatedCopy = File(epubDir, "${book.id}.epub")
        assertTrue("内部副本必须存在于隔离目录", isolatedCopy.isFile)

        // 目标包的书目录里不得出现任何以该 bookId 命名的文件
        // （qahost 变体下 targetPackage 是沙箱包；debug 变体下才是正式包）
        val productionDir = File(appContext.filesDir, "books/epub")
        val intruders = if (productionDir.isDirectory) {
            productionDir.listFiles()?.map { it.name }?.filter { it.startsWith(book.id) }.orEmpty()
        } else {
            emptyList()
        }
        assertTrue(
            "正式 files/books/epub/ 不得出现本次 bookId 的文件，实际=$intruders",
            intruders.isEmpty(),
        )
    }

    // ─── 契约 4：已有 .backup 残留时拒绝替换 ─────────────────────────

    /**
     * **残留守卫现场 A：target 与同名 `.backup` 同时存在**（真机常见形态 —— 上次替换
     * 已把旧副本复制进 backup，随后进程被杀在「提升新副本」之前）。
     *
     * 守卫依据（`EpubRepository.kt:96`）：仅凭 target 是否存在或长度**无法区分**
     * 「上次被杀死在中间」与「上次已提交成功但未清理过期 backup」两种现场，
     * 因此一律拒绝，且**绝不**删除/覆盖/自动恢复任何一侧。
     *
     * 断言清单（全部为真实 Room / 真实字节）：
     * 1. 抛出 `IllegalStateException`，消息可识别为残留拒绝；
     * 2. 旧 target **逐字节不变**；
     * 3. 残留 backup **逐字节不变**（不被删除、不被覆盖、不被提升为 target）；
     * 4. `books` 行与 `reading_progress` 行**整行不变**（守卫在任何 DAO 调用之前）；
     * 5. 不产生**新的** `.importing` / `.repairing`（守卫先于 parse，故本次未落任何临时文件）。
     *
     * 注意：本用例**不能**复用 `assertNoResidue` —— 该辅助断言「backup 不存在」，
     * 与本现场的「backup 必须原样保留」正相反。
     */
    @Test
    fun backupResidueWithTargetPresentIsRefusedAndPreservesBothSides() = runTest {
        val repository = newRepository()
        val source = File(sourceDir, "residue-both-source.epub")
        writeEpub(source, title = "残留现场旧标题", bodyMarker = "RESIDUEMARKER旧正文")
        val uri = Uri.fromFile(source)

        val book = repository.openEpub(uri)
        val target = isolated.assertInsideRoot(File(epubDir, "${book.id}.epub"))
        assertInsideRootSelfCheck(target)
        assertTrue("前提自检：导入后 target 必须存在", target.isFile)
        val targetBytesBefore = target.readBytes()

        repository.saveProgress(bookId = book.id, chapterIndex = 3, percent = 55f)
        val rowBefore = database.bookDao().getById(book.id)!!
        val progressBefore = database.readingProgressDao().getByBook(book.id)!!

        // 制造残留：同名 .backup 已存在（内容刻意与 target 不同，便于识别被覆盖）
        val backup = isolated.assertInsideRoot(File(epubDir, "${book.id}.backup"))
        val backupBytes = "残留备份字节-RESIDUE-BACKUP-勿动".toByteArray(Charsets.UTF_8)
        backup.writeBytes(backupBytes)
        assertTrue("前提自检：残留 backup 必须已存在", backup.exists())

        // 来源本身是**合法**新 EPUB —— 证明拒绝来自残留守卫，而非来源非法
        writeEpub(source, title = "新版标题", bodyMarker = "NEWMARKER新正文")

        val caught = try {
            repository.replaceStoredEpub(
                stored = database.bookDao().getById(book.id)!!,
                selectedUri = uri,
                selectedFileName = "residue-both-source.epub",
            )
            null
        } catch (e: Throwable) {
            e
        }

        // 1) 拒绝且错误可识别
        assertNotNull("存在残留时必须拒绝本次替换", caught)
        assertTrue(
            "必须是 IllegalStateException，实际=${caught!!.javaClass.simpleName}: ${caught.message}",
            caught is IllegalStateException,
        )
        assertTrue(
            "错误消息必须点出残留，实际=${caught.message}",
            caught.message?.contains("未完成的替换残留") == true,
        )
        assertTrue(
            "消息必须说明本次替换已停止",
            caught.message?.contains("本次替换已停止") == true,
        )
        assertTrue(
            "消息必须说明重试不会自动恢复",
            caught.message?.contains("重复重试不会自动恢复") == true,
        )

        // 2) 旧 target 逐字节不变
        assertArrayEquals("旧 target 必须逐字节不变", targetBytesBefore, target.readBytes())
        // 3) 残留 backup 逐字节不变（不删、不覆盖、不提升）
        assertArrayEquals("残留 backup 必须逐字节保留", backupBytes, backup.readBytes())
        // 4) Room 整行不变（守卫先于任何 DAO 调用）
        assertEquals("books 行不得推进", rowBefore, database.bookDao().getById(book.id))
        assertEquals("阅读进度行不得推进", progressBefore, database.readingProgressDao().getByBook(book.id))
        assertEquals("不得新增或软删书籍行", 1, database.bookDao().countActive())
        // 5) 不产生新的临时文件
        assertFalse(
            "本次不得留下 .importing",
            File(epubDir, "${book.id}.importing").exists(),
        )
        assertFalse(
            "本次不得留下 .repairing",
            File(epubDir, "${book.id}.repairing").exists(),
        )
    }

    /**
     * **残留守卫现场 B：target 缺失、但 `.backup` 存在**（K2 现场 —— 旧副本已挪进
     * backup、新副本尚未落位）。此时 backup 很可能就是**唯一完好的旧正文**。
     *
     * 本用例锁三件事：
     * 1. 守卫仍必须拒绝（不能因 target 缺失就「顺手」继续替换）；
     * 2. backup 的完整字节必须保留，**不得**被自动恢复成 target、**不得**被删除；
     * 3. 提示语对「target 缺失」现场必须仍然为真 —— 用**条件式**表述
     *    （「旧正文若仍存在」），**不得**虚称 target 仍在；
     * 4. Room 不写入（不虚增行、不推进 updated_at）。
     *
     * 与现场 A 的区别：那条证明「两侧都在时都不动」，本条证明「少一侧时也不自作主张」。
     */
    @Test
    fun backupResidueWithTargetMissingIsRefusedAndNeverAutoRestores() = runTest {
        val repository = newRepository()
        val source = File(sourceDir, "residue-no-target-source.epub")
        writeEpub(source, title = "K2现场旧标题", bodyMarker = "K2MARKER旧正文")
        val uri = Uri.fromFile(source)

        val book = repository.openEpub(uri)
        val target = isolated.assertInsideRoot(File(epubDir, "${book.id}.epub"))
        assertInsideRootSelfCheck(target)
        assertTrue("前提自检：导入后 target 必须存在", target.isFile)
        val originalBytes = target.readBytes()

        repository.saveProgress(bookId = book.id, chapterIndex = 7, percent = 77f)
        val rowBefore = database.bookDao().getById(book.id)!!
        val progressBefore = database.readingProgressDao().getByBook(book.id)!!

        // 制造 K2 现场：旧副本整体挪进 backup ⇒ target 消失、backup 持有完整旧正文
        val backup = isolated.assertInsideRoot(File(epubDir, "${book.id}.backup"))
        assertTrue("前提自检：target 必须能挪成 backup", target.renameTo(backup))
        assertFalse("前提自检：target 必须确实缺失", target.exists())
        assertTrue("前提自检：backup 必须存在", backup.exists())
        val backupBytesBefore = backup.readBytes()
        assertArrayEquals("前提自检：backup 应持有完整旧正文", originalBytes, backupBytesBefore)

        // 来源合法，证明拒绝来自残留守卫
        writeEpub(source, title = "新版标题", bodyMarker = "NEWMARKER新正文")

        val caught = try {
            repository.replaceStoredEpub(
                stored = database.bookDao().getById(book.id)!!,
                selectedUri = uri,
                selectedFileName = "residue-no-target-source.epub",
            )
            null
        } catch (e: Throwable) {
            e
        }

        // 1) 仍必须拒绝
        assertNotNull("target 缺失时存在残留仍必须拒绝", caught)
        assertTrue(
            "必须是 IllegalStateException，实际=${caught!!.javaClass.simpleName}: ${caught.message}",
            caught is IllegalStateException,
        )
        val message = caught.message.orEmpty()
        assertTrue("错误消息必须点出残留", message.contains("未完成的替换残留"))
        assertTrue("消息必须说明本次替换已停止", message.contains("本次替换已停止"))
        assertTrue("消息必须说明重试不会自动恢复", message.contains("重复重试不会自动恢复"))
        // 3) 对旧正文的表述必须是条件式 —— 本现场 target 并不存在
        assertTrue(
            "提示必须用条件式描述旧正文（不得虚称 target 仍在），实际=$message",
            message.contains("若仍存在"),
        )

        // 2) backup 完整字节保留；未被自动恢复成 target；未被删除
        assertTrue("backup 必须仍然存在", backup.exists())
        assertArrayEquals("backup 必须逐字节保留", backupBytesBefore, backup.readBytes())
        assertFalse("不得自动把 backup 恢复成 target", target.exists())

        // 4) Room 不写入
        assertEquals("books 行不得推进", rowBefore, database.bookDao().getById(book.id))
        assertEquals("阅读进度行不得推进", progressBefore, database.readingProgressDao().getByBook(book.id))
        assertEquals("不得新增或软删书籍行", 1, database.bookDao().countActive())
        assertFalse(
            "本次不得留下 .importing",
            File(epubDir, "${book.id}.importing").exists(),
        )
        assertFalse(
            "本次不得留下 .repairing",
            File(epubDir, "${book.id}.repairing").exists(),
        )
    }

    // ─── 辅助 ────────────────────────────────────────────────────────

    private fun newRepository(bookDao: BookDao = database.bookDao()) = EpubRepository(
        // 关键：交给仓储的是隔离 Context，不是应用 Context ——
        // 于是 filesDir/books/epub 落在 testRoot 下，目标包的书目录不参与。
        context = isolated,
        bookDao = bookDao,
        readingProgressDao = database.readingProgressDao(),
        ioDispatcher = Dispatchers.IO,
        fileOps = DirectFileOps(),
    )

    /**
     * 路径不变量自检：被测代码实际写入的位置必须真的在测试专属根目录之下。
     *
     * 这条断言是对「隔离是否真的生效」的**独立验证** ——
     * 万一 `IsolatedTestContext` 被误改成未覆盖 `filesDir`，
     * target 会落到目标包的书目录，此处立刻失败，而不是让用例静默污染真实数据。
     */
    private fun assertInsideRootSelfCheck(file: File) {
        assertTrue(
            "内部副本路径逃逸出测试专属目录：${file.absolutePath} " +
                "（testRoot=${testRoot.absolutePath}）",
            isolated.assertInsideRoot(file).canonicalPath.startsWith(testRoot.canonicalPath),
        )
    }

    /** 与生产 `DefaultFileOps` 逐字同义的转发实现（生产类为 private，测试无法引用）。 */
    private class DirectFileOps : FileOps {
        override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)

        override fun copyTo(source: File, dest: File) {
            source.copyTo(dest, overwrite = true)
        }
    }

    /**
     * 「替换已落盘、后置写库」那一刻的故障注入 DAO。
     * 其余方法全部经类委托转发真实 DAO；只有 `update` 在 [failUpdates] 置位后抛 IOException。
     */
    private class FailingUpdateBookDao(private val real: BookDao) : BookDao by real {
        @Volatile
        var failUpdates: Boolean = false

        override suspend fun update(book: BookEntity): Int {
            if (failUpdates) throw IOException("注入：后置数据库写入失败")
            return real.update(book)
        }
    }

    /**
     * 断言本次替换操作没有留下任何残留文件：
     * 生产代码对某 bookId 只可能产生 `.epub` / `.importing` / `.repairing` / `.backup`
     * 四种文件名；其中正式副本 `.epub` 必须存在，其余三种必须全部消失。
     */
    private fun assertNoResidue(bookId: String) {
        val residue = listOf("$bookId.importing", "$bookId.repairing", "$bookId.backup")
            .map { File(epubDir, it) }
            .filter { it.exists() }
            .map { it.name }
        assertTrue("不得留下本次操作产生的残留文件，实际=$residue", residue.isEmpty())
        assertTrue("正式内部副本必须仍然存在", File(epubDir, "$bookId.epub").isFile)
    }

    /** 独立于 `EpubRepository.computeFileHash` 计算全量 MD5（小写十六进制），做交叉核对。 */
    private fun md5Of(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * 合成一个结构合法、能被真实 `EpubParser` 解析的最小 EPUB。
     * 结构与 `EpubParserFreshSourceTest` / `EpubParserInstrumentedTest` 已验证的形态一致。
     */
    private fun writeEpub(dest: File, title: String, bodyMarker: String) {
        ZipOutputStream(dest.outputStream().buffered()).use { zos ->
            zos.setLevel(Deflater.NO_COMPRESSION)
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray(Charsets.US_ASCII))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title>
                    <dc:identifier id="BookId">s04-room-isolated</dc:identifier>
                  </metadata>
                  <manifest>
                    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="ch1"/></spine>
                </package>
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("OEBPS/ch1.xhtml"))
            zos.write(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                  <head><title>$title</title></head>
                  <body><p>$bodyMarker</p></body>
                </html>
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zos.closeEntry()
        }
    }
}
