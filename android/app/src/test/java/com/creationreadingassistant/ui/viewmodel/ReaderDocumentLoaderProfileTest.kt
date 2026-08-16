package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.rules.BuiltinTocRules
import com.creationreadingassistant.feature.reader.rules.RuleKind
import com.creationreadingassistant.feature.reader.rules.RuleScope
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P1-A：ReaderDocumentLoader 打开 TXT 时必须：
 * 1. 一次性迁移旧 DataStore txtTocRuleId 到 Room（marker 防重复覆盖用户管理）；
 * 2. 从 RuleSnapshot.effectiveToc 构造 TxtTocProfile；
 * 3. 用 profile 预检测小文件目录，preDetectedRuleId 落 profile.key。
 */
class ReaderDocumentLoaderProfileTest {

    private lateinit var context: Context
    private lateinit var epubRepository: EpubRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var bookContentDao: BookContentDao
    private lateinit var readingProgressDao: ReadingProgressDao
    private lateinit var settingsStore: SettingsStore
    private lateinit var ruleDao: InMemoryRuleDao
    private lateinit var loader: ReaderDocumentLoader
    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        tempRoot = Files.createTempDirectory("reader-loader-profile-").toFile()
        context = mockk(relaxed = true)
        every { context.cacheDir } returns File(tempRoot, "cache")
        File(tempRoot, "cache").mkdirs()
        epubRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        bookContentDao = mockk(relaxed = true)
        readingProgressDao = mockk(relaxed = true)
        settingsStore = mockk(relaxed = true)
        coEvery { settingsStore.loadTxtTocRule(any()) } returns "num-dot"
        coEvery { settingsStore.isTxtTocRuleMigrated(any()) } returns false
        ruleDao = InMemoryRuleDao()
        loader = ReaderDocumentLoader(
            context = context,
            epubRepository = epubRepository,
            bookRepository = bookRepository,
            bookContentDao = bookContentDao,
            readingProgressDao = readingProgressDao,
            settingsStore = settingsStore,
            rulesRepository = RulesRepository(ruleDao),
        )
    }

    @After
    fun tearDown() {
        tempRoot.deleteRecursively()
    }

    private val body = "测试正文内容。".repeat(70)

    /**
     * JVM 单测中 Uri.parse 返回 null，注入受控 Uri 指向真实临时文件。
     */
    private fun installUriFor(file: File): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "file"
        every { uri.path } returns file.absolutePath
        loader.uriParser = { uri }
        return uri
    }

    private fun txtBook(file: File): BookEntity = BookEntity(
        id = "book-1",
        title = "测试 TXT",
        format = "txt",
        local_uri = file.toURI().toString(),
        size = file.length().toInt(),
        updated_at = "2026-08-09T00:00:00Z",
    )

    /** 超过 TextStreamLoader 默认 5MB 流式阈值（每行约 22 字节）。 */
    private fun largeBody(): String = buildString {
        repeat(250_000) { append("测试正文内容。\n") }
    }

    @Test
    fun `txt load migrates legacy rule once and pre-detects with profile key`() = runTest {
        val file = File(tempRoot, "book.txt").apply {
            writeText("1. 序幕\n$body\n2. 正文\n$body")
        }
        installUriFor(file)
        coEvery { bookRepository.getById("book-1") } returns txtBook(file)
        coEvery { readingProgressDao.getByBook("book-1") } returns null

        val loaded = loader.load("book-1")

        val content = loaded.content as ReaderLoadedContent.Text
        assertEquals("num-dot", content.preDetectedRuleId)
        assertEquals(listOf("1. 序幕", "2. 正文"), content.preDetectedChapters.map { it.title })
        assertEquals("旧值应迁移为按书绑定", true, ruleDao.getById("builtin:num-dot:book-1")?.enabled)
        coVerify(exactly = 1) { settingsStore.markTxtTocRuleMigrated("book-1") }
    }

    @Test
    fun `txt load skips migration when marker already set and keeps user rules`() = runTest {
        val file = File(tempRoot, "book.txt").apply {
            writeText("第1章 正文\n$body")
        }
        installUriFor(file)
        coEvery { settingsStore.isTxtTocRuleMigrated("book-1") } returns true
        // 用户已在规则面板禁用 num-dot：绑定存在但 disabled，重开不得被旧值重新启用
        ruleDao.upsert(
            ReaderTextRuleEntity(
                id = "builtin:num-dot:book-1",
                kind = RuleKind.TOC.name,
                name = "数字+标点",
                pattern = null,
                replacement = "",
                builtin = true,
                enabled = false,
                scope = RuleScope.PER_BOOK.name,
                book_id = "book-1",
                position = 1,
                created_at = 1L,
                updated_at = 1L,
            ),
        )
        coEvery { bookRepository.getById("book-1") } returns txtBook(file)
        coEvery { readingProgressDao.getByBook("book-1") } returns null

        val loaded = loader.load("book-1")

        val content = loaded.content as ReaderLoadedContent.Text
        assertEquals("已迁移后生效身份为标准", BuiltinTocRules.STANDARD_ID, content.preDetectedRuleId)
        assertEquals("禁用绑定必须保持禁用", false, ruleDao.getById("builtin:num-dot:book-1")?.enabled)
        coVerify(exactly = 0) { settingsStore.markTxtTocRuleMigrated("book-1") }
    }

    @Test
    fun `txt load preserves streaming source file without owning direct file`() = runTest {
        val file = File(tempRoot, "book-large.txt").apply { writeText(largeBody()) }
        installUriFor(file)
        coEvery { bookRepository.getById("book-1") } returns txtBook(file)
        coEvery { readingProgressDao.getByBook("book-1") } returns null

        val loaded = loader.load("book-1")
        val content = loaded.content as ReaderLoadedContent.Text

        assertNotNull("直接 file:// 大文件必须走流式", content.streamingDocument)
        assertEquals("可直接重扫的 backing source 必须是原文件", file, content.sourceFile)
        assertNull("直接源文件绝不能被当作 owned 临时文件", content.ownedTempFile)
    }

    @Test
    fun `txt load keeps no rescan source for small file`() = runTest {
        val file = File(tempRoot, "book-small.txt").apply { writeText("第1章 正文\n测试") }
        installUriFor(file)
        coEvery { bookRepository.getById("book-1") } returns txtBook(file)
        coEvery { readingProgressDao.getByBook("book-1") } returns null

        val loaded = loader.load("book-1")
        val content = loaded.content as ReaderLoadedContent.Text

        assertNull(content.streamingDocument)
        assertNull("小文件无需保留可重扫 source", content.sourceFile)
        assertNull(content.ownedTempFile)
    }

    @Test
    fun `txt load release never deletes direct source file`() = runTest {
        val file = File(tempRoot, "book-direct.txt").apply { writeText(largeBody()) }
        installUriFor(file)
        coEvery { bookRepository.getById("book-1") } returns txtBook(file)
        coEvery { readingProgressDao.getByBook("book-1") } returns null

        val loaded = loader.load("book-1")
        val content = loaded.content as ReaderLoadedContent.Text

        content.release()
        assertTrue("release 绝不得删除用户直接源文件", file.exists())
        assertTrue(file.readText().isNotBlank())
    }

    @Test
    fun `txt load owns cache temp copy for content uri streaming and release deletes it`() = runTest {
        val source = File(tempRoot, "book-stream.txt").apply { writeText(largeBody()) }
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "content"
        every { context.contentResolver.openInputStream(any()) } returns FileInputStream(source)
        loader.uriParser = { uri }
        coEvery { bookRepository.getById("book-1") } returns txtBook(source)
        coEvery { readingProgressDao.getByBook("book-1") } returns null

        val loaded = loader.load("book-1")
        val content = loaded.content as ReaderLoadedContent.Text

        assertNotNull(content.streamingDocument)
        val owned = content.ownedTempFile
        assertNotNull("content:// 流式必须拥有 cache 临时副本", owned)
        assertEquals("cache 临时副本既是 source 又是 owned", owned, content.sourceFile)
        assertTrue(owned!!.exists())

        content.release()
        assertFalse("release 必须删除 owned 临时副本", owned.exists())
        assertTrue("原始输入不得被 release 删除", source.exists())
    }

    /** 内存版 [ReaderTextRuleDao]：镜像 SQL 排序与 upsert/update 语义。 */
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
