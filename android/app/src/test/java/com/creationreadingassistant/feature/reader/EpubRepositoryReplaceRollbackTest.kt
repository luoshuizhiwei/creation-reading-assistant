package com.creationreadingassistant.feature.reader

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.dao.BookDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.domain.model.EpubBook
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 重新定位 EPUB 时 [EpubRepository.replaceStoredEpub] 的文件替换/回滚阶段语义
 * （CRA-RESELECT-ROLLBACK-FIX-v1）。
 *
 * ## 缺陷
 *
 * 暂存复制（`staging.outputStream()`）发生在旧目标备份（`target.renameTo(backup)`）**之前**，
 * 而 catch 分支**无条件** `target.delete()`。于是「复制阶段就失败」时：
 * 旧目标原地未动、backup 尚未创建，却被删掉且无从恢复 —— 旧 EPUB 副本永久丢失。
 *
 * ## 注入方式
 *
 * 让 staging 路径（`books/epub/<id>.repairing`）成为一个**非空目录**：
 * `staging.delete()` 删不掉非空目录，随后 `staging.outputStream()` 抛
 * FileNotFoundException —— 即「暂存无法写入」。这与真实磁盘/权限故障走同一条 catch。
 * 不新增生产接缝、不使用反射。
 *
 * 解析侧用 `mockkStatic(EpubParser::class)` 桩掉，使 `replaceStoredEpub` 拿到一个
 * 真实存在且非空的本地副本，从而把失败点精确落在暂存复制上。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpubRepositoryReplaceRollbackTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var bookDao: BookDao
    private lateinit var progressDao: ReadingProgressDao
    private lateinit var repository: EpubRepository

    @Before
    fun setUp() {
        // EpubParser 是 Kotlin object（单例实例方法）→ 必须用 mockkObject，mockkStatic 不会拦截。
        mockkObject(EpubParser)
        // Uri.fromFile 在 JVM 单测里是 android.jar 桩；不桩会返回 null，
        // 使 replaceStoredEpub 在 stored.copy(local_uri = ...) 处抛 NPE ——
        // 那会让「DB 失败」用例因错误原因通过（NPE 也走同一条回滚）。
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } returns mockk(relaxed = true)
        context = mockk(relaxed = true) { every { filesDir } returns tempFolder.root }
        bookDao = mockk(relaxed = true)
        progressDao = mockk(relaxed = true)
        // 合并接缝（CRA-ROLLBACK-WIP-RECONCILE-v1）：生产构造新增 fileOps 注入；默认注入与
        // java.io.File 逐字同义的委托实现，既有用例的文件系统断言全部保持原语义。
        repository = EpubRepository(context, bookDao, progressDao, Dispatchers.Unconfined, RealFileOps())
    }

    @After
    fun tearDown() {
        unmockkObject(EpubParser)
        unmockkStatic(Uri::class)
    }

    private fun epubDir(): File = File(tempFolder.root, "books/epub").also { it.mkdirs() }

    private fun seedOldEpub(id: String, bytes: ByteArray): File =
        File(epubDir(), "$id.epub").also { it.writeBytes(bytes) }

    private fun storedBook(id: String, size: Int) = BookEntity(
        id = id,
        title = "旧书名",
        format = "epub",
        original_file_name = "old.epub",
        size = size,
        local_uri = "content://books/old.epub",
        local_content_path = File(epubDir(), "$id.epub").absolutePath,
        content_status = "available",
        imported_at = "2026-01-01T00:00:00Z",
        revision = 3,
        updated_at = "2026-01-01T00:00:00Z",
    )

    private fun stubParsedNew(id: String, bytes: ByteArray) {
        val parsedNew = File(tempFolder.root, "new-$id.epub").also { it.writeBytes(bytes) }
        val book = EpubBook(
            id = id,
            title = "新书名",
            author = null,
            chapters = emptyList(),
            localUri = "content://books/new.epub",
            cachedEpubPath = parsedNew.absolutePath,
        )
        // 既有 2/4 参 parse 调用，以及 CRA-ROLLBACK-WIP-RECONCILE-v2 引入的
        // requireFreshSource=true（5 参）调用，都必须被同一桩覆盖。
        coEvery { EpubParser.parseUnderSwapGate(any(), any(), any(), any(), any()) } returns book
    }

    @Test
    fun `replaceStoredEpub keeps old copy when staging cannot be written before backup`() = runTest {
        val id = "epub1"
        val oldBytes = "旧EPUB字节-必须保留".toByteArray()
        val oldTarget = seedOldEpub(id, oldBytes)

        // 暂存路径被非空目录占用 → staging.delete() 删不掉 → staging.outputStream() 抛错
        val staging = File(epubDir(), "$id.repairing").also { it.mkdirs() }
        File(staging, "occupied").writeText("x")

        stubParsedNew(id, "新EPUB字节".toByteArray())

        val result = runCatching {
            repository.replaceStoredEpub(storedBook(id, oldBytes.size), mockk<Uri>(relaxed = true), "new.epub")
        }

        assertTrue("暂存写入失败必须上抛", result.isFailure)
        assertTrue("旧 EPUB 副本必须仍然存在", oldTarget.isFile)
        assertArrayEquals("尚未触碰旧目标时失败不得删除它", oldBytes, oldTarget.readBytes())
    }

    @Test
    fun `replaceStoredEpub restores old copy when the database write fails after the swap`() = runTest {
        val id = "epub2"
        val oldBytes = "旧EPUB字节-必须保留".toByteArray()
        val oldTarget = seedOldEpub(id, oldBytes)
        stubParsedNew(id, "新EPUB字节".toByteArray())
        // 文件换入成功之后才失败：DB 更新抛错
        coEvery { bookDao.update(any()) } throws IllegalStateException("注入：DB 写入失败")

        val result = runCatching {
            repository.replaceStoredEpub(storedBook(id, oldBytes.size), mockk<Uri>(relaxed = true), "new.epub")
        }

        assertTrue("DB 写入失败必须上抛", result.isFailure)
        assertArrayEquals("新目标已安装后失败必须恢复旧字节", oldBytes, oldTarget.readBytes())
    }

    @Test
    fun `replaceStoredEpub installs new copy and leaves no residue on the happy path`() = runTest {
        val id = "epub3"
        val oldBytes = "旧EPUB字节".toByteArray()
        val oldTarget = seedOldEpub(id, oldBytes)
        val newBytes = "新EPUB字节".toByteArray()
        stubParsedNew(id, newBytes)

        repository.replaceStoredEpub(storedBook(id, oldBytes.size), mockk<Uri>(relaxed = true), "new.epub")

        assertEquals("正常成功路径必须换成新副本字节数", newBytes.size.toLong(), oldTarget.length())
        assertArrayEquals("正常成功路径必须换成新副本字节", newBytes, oldTarget.readBytes())
        assertFalse("成功后不得留下 backup 残留", File(epubDir(), "$id.backup").exists())
        assertFalse("成功后不得留下 staging 残留", File(epubDir(), "$id.repairing").exists())
    }

    // ===== 既有 backup 残留保护（CRA-RESELECT-ROLLBACK-FIX-v2） =====
    // 上次中断后 target 可能缺失，`<id>.backup` 可能是唯一保存旧副本的文件；
    // 修复前入口无条件 backup.delete()，再次执行会先毁掉它。

    @Test
    fun `replaceStoredEpub refuses replacement when a leftover backup is the only copy of the old bytes`() = runTest {
        val id = "epub4"
        val oldBytes = "唯一旧EPUB字节".toByteArray()
        val target = File(epubDir(), "$id.epub")
        val backup = File(epubDir(), "$id.backup").also { it.writeBytes(oldBytes) }
        assertFalse("前置：target 必须缺失", target.exists())
        stubParsedNew(id, "新EPUB字节".toByteArray())

        val result = runCatching {
            repository.replaceStoredEpub(storedBook(id, oldBytes.size), mockk<Uri>(relaxed = true), "new.epub")
        }

        assertTrue("必须明确失败", result.isFailure)
        assertTrue(
            "错误必须说明检测到既有备份：${result.exceptionOrNull()?.message}",
            result.exceptionOrNull()?.message?.contains("备份") == true,
        )
        assertTrue("残留 backup 必须保留", backup.isFile)
        assertArrayEquals("backup 是唯一旧副本，字节必须原样保留", oldBytes, backup.readBytes())
        assertFalse("拒绝替换时不得写出目标", target.exists())
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
    }

    @Test
    fun `replaceStoredEpub refuses when both the target and a leftover backup exist`() = runTest {
        val id = "epub5"
        val oldBytes = "当前EPUB字节".toByteArray()
        val target = seedOldEpub(id, oldBytes)
        val backup = File(epubDir(), "$id.backup").also { it.writeBytes("更旧的EPUB字节".toByteArray()) }
        val backupBytes = backup.readBytes()
        stubParsedNew(id, "新EPUB字节".toByteArray())

        val result = runCatching {
            repository.replaceStoredEpub(storedBook(id, oldBytes.size), mockk<Uri>(relaxed = true), "new.epub")
        }

        assertTrue("必须明确失败", result.isFailure)
        assertArrayEquals("target 不得被覆盖", oldBytes, target.readBytes())
        assertTrue("残留 backup 不得被删除", backup.isFile)
        assertArrayEquals("残留 backup 字节不得被改写", backupBytes, backup.readBytes())
        coVerify(exactly = 0) { bookDao.update(any()) }
    }

    /**
     * N1—CRA-RESELECT-ROLLBACK-FIX-v3：backup 位被**非空目录**（含哨兵文件）占住时，
     * 入口守卫 `backup.isFile` 会漏判 → 拒绝替换不得发生，用户只能看到裸文件系统异常。
     * 修复后：存在任何同名 backup 残留即拒绝；target/backup 目录内字节不变、DB 未写。
     */
    @Test
    fun `replaceStoredEpub refuses when the leftover backup is a non-empty directory`() = runTest {
        val id = "epub9"
        val oldBytes = "当前EPUB字节-目录残留场景".toByteArray()
        val target = seedOldEpub(id, oldBytes)
        val backupDir = File(epubDir(), "$id.backup").also { it.mkdirs() }
        val sentinel = File(backupDir, "sentinel-old-bytes").also { it.writeText("目录内旧字节哨兵") }
        stubParsedNew(id, "新EPUB字节".toByteArray())

        val result = runCatching {
            repository.replaceStoredEpub(storedBook(id, oldBytes.size), mockk<Uri>(relaxed = true), "new.epub")
        }

        assertTrue("backup 为目录时必须明确拒绝替换", result.isFailure)
        assertTrue(
            "错误必须可观察（说明是备份残留挡住替换）：${result.exceptionOrNull()?.message}",
            result.exceptionOrNull()?.message?.contains("备份") == true,
        )
        assertArrayEquals("target 不得被覆盖", oldBytes, target.readBytes())
        assertTrue("backup 目录必须原样保留", backupDir.isDirectory)
        assertEquals("哨兵文件必须原样保留", "目录内旧字节哨兵", sentinel.readText())
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
    }

    @Test
    fun `replaceStoredEpub refuses when only the backup directory remains and no target exists`() = runTest {
        val id = "epub10"
        val backupDir = File(epubDir(), "$id.backup").also { it.mkdirs() }
        val sentinel = File(backupDir, "sentinel-only-copy").also { it.writeText("目录内唯一旧副本哨兵") }
        val target = File(epubDir(), "$id.epub")
        assertFalse("前置：target 必须缺失", target.exists())
        stubParsedNew(id, "新EPUB字节".toByteArray())

        val result = runCatching {
            repository.replaceStoredEpub(storedBook(id, 0), mockk<Uri>(relaxed = true), "new.epub")
        }

        assertTrue("backup 目录残留时必须拒绝替换（可能是唯一旧副本所在）", result.isFailure)
        assertTrue(
            "错误必须可观察：${result.exceptionOrNull()?.message}",
            result.exceptionOrNull()?.message?.contains("备份") == true,
        )
        assertFalse("拒绝替换时不得写出目标", target.exists())
        assertTrue("backup 目录必须原样保留", backupDir.isDirectory)
        assertEquals("哨兵文件必须原样保留", "目录内唯一旧副本哨兵", sentinel.readText())
        coVerify(exactly = 0) { bookDao.update(any()) }
        coVerify(exactly = 0) { bookDao.upsert(any()) }
    }

    @Test
    fun `replaceStoredEpub keeps the backup and attaches the rollback failure when the restore fails`() = runTest {
        val id = "epub6"
        val oldBytes = "旧EPUB字节-必须保留".toByteArray()
        val target = seedOldEpub(id, oldBytes)
        val backup = File(epubDir(), "$id.backup")
        stubParsedNew(id, "新EPUB字节".toByteArray())
        coEvery { bookDao.update(any()) } answers {
            // 注入：新副本已装上、回滚之前，目标位被非空目录占住 → 既删不掉也复位不回去
            target.delete()
            target.mkdirs()
            File(target, "blocked").writeText("x")
            throw IllegalStateException("注入：DB 写入失败")
        }

        val result = runCatching {
            repository.replaceStoredEpub(storedBook(id, oldBytes.size), mockk<Uri>(relaxed = true), "new.epub")
        }

        val error = result.exceptionOrNull()
        assertEquals("必须保留主异常，不得被恢复异常顶替", "注入：DB 写入失败", error?.message)
        assertTrue(
            "恢复失败必须作为 suppressed 挂在主异常上：${error?.suppressed?.toList()}",
            error?.suppressed?.isNotEmpty() == true,
        )
        assertTrue("恢复失败必须保留 backup", backup.isFile)
        assertArrayEquals("恢复失败不得丢掉 backup 里的旧字节", oldBytes, backup.readBytes())
    }

    @Test
    fun `replaceStoredEpub installs the new copy when no previous target exists`() = runTest {
        val id = "epub7"
        val target = File(epubDir(), "$id.epub")
        assertFalse("前置：target 必须缺失", target.exists())
        val newBytes = "新EPUB字节".toByteArray()
        stubParsedNew(id, newBytes)

        repository.replaceStoredEpub(storedBook(id, 0), mockk<Uri>(relaxed = true), "new.epub")

        assertArrayEquals("首次安装必须写入目标（保护逻辑不得误伤）", newBytes, target.readBytes())
        assertFalse("不得留下 backup 残留", File(epubDir(), "$id.backup").exists())
        assertFalse("不得留下 staging 残留", File(epubDir(), "$id.repairing").exists())
    }

    @Test
    fun `replaceStoredEpub removes only the newly written target when the first install fails`() = runTest {
        val id = "epub8"
        val target = File(epubDir(), "$id.epub")
        assertFalse("前置：target 必须缺失", target.exists())
        stubParsedNew(id, "新EPUB字节".toByteArray())
        coEvery { bookDao.update(any()) } throws IllegalStateException("注入：DB 写入失败")

        val result = runCatching {
            repository.replaceStoredEpub(storedBook(id, 0), mockk<Uri>(relaxed = true), "new.epub")
        }

        val error = result.exceptionOrNull()
        assertEquals("必须保留主异常", "注入：DB 写入失败", error?.message)
        assertTrue(
            "旧目标本来就不存在时不得伪造回滚失败：${error?.suppressed?.toList()}",
            error?.suppressed?.isEmpty() == true,
        )
        assertFalse("首次安装失败后不得留下目标残留", target.exists())
    }
    // ===== WIP 接缝回归（CRA-ROLLBACK-WIP-RECONCILE-v1） =====
    // renameTo 失败退回覆盖复制的分支在直接文件操作下 JVM 不可达（同目录 rename 几乎必然
    // 成功）；fileOps 注入后可确定性覆盖：兜底复制必须完整落盘、清理 backup，替换照常完成。

    /** 与生产 [DefaultFileOps] 逐字同义的真实委托（生产实现为 private，这里按同义重建）。 */
    private class RealFileOps : FileOps {
        override fun renameTo(source: File, dest: File): Boolean = source.renameTo(dest)

        override fun copyTo(source: File, dest: File) {
            source.copyTo(dest, overwrite = true)
        }
    }

    @Test
    fun `replaceStoredEpub completes via copyTo fallback when renameTo fails`() = runTest {
        val id = "epub11"
        val oldBytes = "旧EPUB字节-rename失败场景".toByteArray()
        val target = seedOldEpub(id, oldBytes)
        val newBytes = "新EPUB字节-经兜底复制安装".toByteArray()
        stubParsedNew(id, newBytes)
        repository = EpubRepository(context, bookDao, progressDao, Dispatchers.Unconfined, object : FileOps {
            override fun renameTo(source: File, dest: File): Boolean = false
            override fun copyTo(source: File, dest: File) {
                source.copyTo(dest, overwrite = true)
            }
        })

        repository.replaceStoredEpub(storedBook(id, oldBytes.size), mockk<Uri>(relaxed = true), "new.epub")

        assertArrayEquals("兜底复制后新副本必须完整落位", newBytes, target.readBytes())
        assertFalse("兜底成功后不得留下 backup 残留", File(epubDir(), "$id.backup").exists())
        assertFalse("不得留下 staging 残留", File(epubDir(), "$id.repairing").exists())
    }
}
