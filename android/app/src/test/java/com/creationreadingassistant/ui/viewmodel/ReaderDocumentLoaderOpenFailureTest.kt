package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.entity.BookContentEntity
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.rules.FakeReaderCorrectionDao
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * R6（CRA-TXT-OPEN-FAILURE-PREVIEW-FIX-v1）：打开 TXT 的源读取已真实失败
 * （文件缺失 / provider 权限丢失 / 源内容为空 / 取消）或无源引用时，
 * 导入时缓存的前 20,000 字符预览不得当作整本正文返回成功。
 *
 * 全部用例走真实 ReaderDocumentLoader → TextStreamLoader 生产路径；
 * 失败必须抛出真实原因（不产出 ReaderLoadedBook），成功路径（小文件 / 流式）
 * 行为保持不变。
 */
class ReaderDocumentLoaderOpenFailureTest {

    private lateinit var context: Context
    private lateinit var bookRepository: BookRepository
    private lateinit var bookContentDao: BookContentDao
    private lateinit var loader: ReaderDocumentLoader
    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tempRoot = Files.createTempDirectory("reader-loader-open-failure-").toFile()
        context = mockk(relaxed = true)
        every { context.cacheDir } returns File(tempRoot, "cache").apply { mkdirs() }
        bookRepository = mockk(relaxed = true)
        bookContentDao = mockk(relaxed = true)
        val settingsStore = mockk<SettingsStore>(relaxed = true)
        // 迁移链不是本组用例的关注点：marker 已置位即跳过 DataStore 迁移。
        coEvery { settingsStore.isTxtTocRuleMigrated(any()) } returns true
        loader = ReaderDocumentLoader(
            context = context,
            epubRepository = mockk(relaxed = true),
            bookRepository = bookRepository,
            bookContentDao = bookContentDao,
            readingProgressDao = mockk(relaxed = true),
            settingsStore = settingsStore,
            rulesRepository = RulesRepository(InMemoryRuleDao(), FakeReaderCorrectionDao()),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempRoot.deleteRecursively()
    }

    private val preview = "这是导入时缓存的预览内容，不代表完整正文。".repeat(50)

    private fun txtBook(localUri: String?): BookEntity = BookEntity(
        id = "book-1",
        title = "测试 TXT",
        format = "txt",
        local_uri = localUri,
        size = 0,
        updated_at = "2026-08-09T00:00:00Z",
    )

    private fun stubPreview() {
        coEvery { bookContentDao.getByBook("book-1") } returns
            BookContentEntity(book_id = "book-1", reader_preview = preview)
    }

    private fun fileUriFor(path: String) {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "file"
        every { uri.path } returns path
        loader.uriParser = { uri }
    }

    private fun contentUri() {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        loader.uriParser = { uri }
    }

    @Test
    fun `missing source file with non-empty preview fails with the real reason`() = runTest {
        val missing = File(tempRoot, "deleted-source.txt").absolutePath
        // 生产语义：file:// 引用指向已删除文件时 ContentResolver.openInputStream 抛 ENOENT；
        // JVM 单测里用受控抛出模拟同一打开失败。
        every { context.contentResolver.openInputStream(any()) } throws
            FileNotFoundException("$missing: open failed: ENOENT (No such file or directory)")
        fileUriFor(missing)
        stubPreview()
        coEvery { bookRepository.getById("book-1") } returns txtBook(localUri = missing)

        try {
            loader.load("book-1")
            fail("源文件已缺失且仅剩缓存预览时不得返回成功的 ReaderLoadedBook")
        } catch (expected: FileNotFoundException) {
            assertTrue(
                "必须保留真实失败原因，而不是静默换成预览正文",
                expected.message.orEmpty().contains("deleted-source.txt"),
            )
        }
    }

    @Test
    fun `provider permission failure with non-empty preview fails and keeps the reason`() = runTest {
        every { context.contentResolver.openInputStream(any()) } throws
            SecurityException("权限已被收回，需重新选择文件")
        contentUri()
        stubPreview()
        coEvery { bookRepository.getById("book-1") } returns txtBook(localUri = "content://books/provider/1")

        try {
            loader.load("book-1")
            fail("provider 权限丢失且仅剩缓存预览时不得返回成功的 ReaderLoadedBook")
        } catch (expected: SecurityException) {
            assertEquals("权限已被收回，需重新选择文件", expected.message)
        }
    }

    @Test
    fun `no source reference with legacy preview fails with the existing no-content message`() = runTest {
        stubPreview()
        coEvery { bookRepository.getById("book-1") } returns txtBook(localUri = null)

        try {
            loader.load("book-1")
            fail("无源引用的旧行不得把历史预览宣称全书")
        } catch (expected: IllegalArgumentException) {
            assertEquals("本书暂无可阅读的正文（需重新导入或同步下载）", expected.message)
        }
    }

    @Test
    fun `readable empty source with non-empty preview fails instead of showing preview`() = runTest {
        val empty = File(tempRoot, "empty.txt").apply { writeText("") }
        fileUriFor(empty.absolutePath)
        stubPreview()
        coEvery { bookRepository.getById("book-1") } returns txtBook(localUri = empty.toURI().toString())

        try {
            loader.load("book-1")
            fail("源内容为空且仅剩缓存预览时不得返回成功的 ReaderLoadedBook")
        } catch (expected: IllegalArgumentException) {
            assertEquals("文件内容为空", expected.message)
        }
    }

    @Test
    fun `cancellation from the open path is never converted to a preview success`() = runTest {
        every { context.contentResolver.openInputStream(any()) } throws
            CancellationException("打开已被取消")
        contentUri()
        stubPreview()
        coEvery { bookRepository.getById("book-1") } returns txtBook(localUri = "content://books/provider/1")

        try {
            loader.load("book-1")
            fail("取消不得落入预览降级成功")
        } catch (expected: CancellationException) {
            assertEquals("打开已被取消", expected.message)
        }
    }

    @Test
    fun `small txt with readable source still loads real content when preview exists`() = runTest {
        val file = File(tempRoot, "book-small.txt").apply { writeText("第1章 正文\n真实正文内容") }
        fileUriFor(file.absolutePath)
        stubPreview()
        coEvery { bookRepository.getById("book-1") } returns txtBook(localUri = file.toURI().toString())

        val loaded = loader.load("book-1")
        val content = loaded.content as ReaderLoadedContent.Text

        assertEquals("成功路径必须以实际读到的正文为准", file.readText(), content.fullText)
        assertEquals(null, content.streamingDocument)
    }

    @Test
    fun `streaming txt with cached preview keeps existing preview fallback behavior`() = runTest {
        val file = File(tempRoot, "book-large.txt").apply {
            bufferedWriter().use { writer -> repeat(250_000) { writer.write("测试正文内容。\n") } }
        }
        fileUriFor(file.absolutePath)
        stubPreview()
        coEvery { bookRepository.getById("book-1") } returns txtBook(localUri = file.toURI().toString())

        val loaded = loader.load("book-1")
        val content = loaded.content as ReaderLoadedContent.Text

        assertNotNull("大文件必须走流式路径", content.streamingDocument)
        assertEquals(
            "流式成功保留既有语义：fullText 落预览兜底，进度/正文消费走流式文档",
            preview,
            content.fullText,
        )
    }

    /** 内存版 [ReaderTextRuleDao]：镜像 SQL 排序与 upsert/update 语义（与既有 Loader 测试同构）。 */
    private class InMemoryRuleDao : ReaderTextRuleDao {
        private val state = MutableStateFlow<List<ReaderTextRuleEntity>>(emptyList())

        override fun observeAll(): Flow<List<ReaderTextRuleEntity>> = state

        override fun observeByKind(kind: String): Flow<List<ReaderTextRuleEntity>> =
            state.map { rows -> rows.filter { it.kind == kind } }

        override fun observeById(id: String): Flow<ReaderTextRuleEntity?> =
            state.map { rows -> rows.firstOrNull { it.id == id } }

        override suspend fun getById(id: String): ReaderTextRuleEntity? =
            state.value.firstOrNull { it.id == id }

        override suspend fun upsert(entity: ReaderTextRuleEntity) {
            state.value = (state.value.filterNot { it.id == entity.id } + entity).sortedByPosition()
        }

        override suspend fun deleteCustom(id: String): Int {
            val existing = state.value.firstOrNull { it.id == id } ?: return 0
            if (existing.builtin) return 0
            state.value = state.value.filterNot { it.id == id }
            return 1
        }

        override suspend fun updateEnabled(id: String, enabled: Boolean, updatedAt: Long): Int {
            val idx = state.value.indexOfFirst { it.id == id }
            if (idx < 0) return 0
            val rows = state.value.toMutableList()
            rows[idx] = rows[idx].copy(enabled = enabled, updated_at = updatedAt)
            state.value = rows.sortedByPosition()
            return 1
        }

        override suspend fun updatePosition(id: String, position: Int, updatedAt: Long): Int {
            val idx = state.value.indexOfFirst { it.id == id }
            if (idx < 0) return 0
            val rows = state.value.toMutableList()
            rows[idx] = rows[idx].copy(position = position, updated_at = updatedAt)
            state.value = rows.sortedByPosition()
            return 1
        }

        private fun List<ReaderTextRuleEntity>.sortedByPosition(): List<ReaderTextRuleEntity> =
            sortedWith(compareBy<ReaderTextRuleEntity> { it.position }.thenBy { it.id })
    }
}
