package com.creationreadingassistant.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.creationreadingassistant.data.local.dao.BookContentDao
import com.creationreadingassistant.data.local.dao.ReadingProgressDao
import com.creationreadingassistant.data.local.dao.ReaderTextRuleDao
import com.creationreadingassistant.data.local.entity.BookEntity
import com.creationreadingassistant.data.local.entity.ReadingProgressEntity
import com.creationreadingassistant.data.local.entity.ReaderTextRuleEntity
import com.creationreadingassistant.data.repository.BookRepository
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.reader.EpubRepository
import com.creationreadingassistant.feature.reader.locator.LegadoChapterPosition
import com.creationreadingassistant.feature.reader.locator.LegadoProgressMarker
import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.feature.reader.rules.FakeReaderCorrectionDao
import com.creationreadingassistant.feature.reader.rules.RulesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
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
import org.junit.Before
import org.junit.Test

/**
 * S12-P2-2：流式大 TXT 导入 Legado 章节位置后，首次打开不得静默回书首。
 *
 * 章节边界真源 = TxtFileIndexCache 按 profile 全文扫描的完整逻辑章节表
 * （TxtFileScanner 与阅读器目录/阅读单元同源），不是 ReadingUnit 窗口。
 * 门控：规则身份匹配（detectedRuleId == profile.key）且真实检测出多章（≥2）。
 * 「全文」单章 / 章号越界 / 无边界来源 → 回书首且 marker 原样保留（不伪装成功）；
 * 原生 locator 优先于 marker 换算；marker 换算结果经一次原生保存后跨重启可恢复。
 */
class ReaderDocumentLoaderStreamingLegadoTest {

    private lateinit var context: Context
    private lateinit var bookRepository: BookRepository
    private lateinit var readingProgressDao: ReadingProgressDao
    private lateinit var loader: ReaderDocumentLoader
    private lateinit var tempRoot: File

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tempRoot = Files.createTempDirectory("reader-loader-stream-legado-").toFile()
        context = mockk(relaxed = true)
        every { context.cacheDir } returns File(tempRoot, "cache").apply { mkdirs() }
        bookRepository = mockk(relaxed = true)
        readingProgressDao = mockk(relaxed = true)
        val settingsStore = mockk<SettingsStore>(relaxed = true)
        // 迁移链不是本组用例的关注点：marker 已置位即跳过 DataStore 迁移。
        coEvery { settingsStore.isTxtTocRuleMigrated(any()) } returns true
        loader = ReaderDocumentLoader(
            context = context,
            epubRepository = mockk(relaxed = true),
            bookRepository = bookRepository,
            bookContentDao = mockk(relaxed = true),
            readingProgressDao = readingProgressDao,
            settingsStore = settingsStore,
            rulesRepository = RulesRepository(InMemoryRuleDao(), FakeReaderCorrectionDao()),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempRoot.deleteRecursively()
    }

    private fun installUriFor(file: File) {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.scheme } returns "file"
        every { uri.path } returns file.absolutePath
        loader.uriParser = { uri }
    }

    private fun txtBook(file: File): BookEntity = BookEntity(
        id = "book-1",
        title = "测试 TXT",
        format = "txt",
        local_uri = file.toURI().toString(),
        size = file.length().toInt(),
        updated_at = "2026-08-09T00:00:00Z",
    )

    private fun markerProgress(ci: Int, co: Int): ReadingProgressEntity = ReadingProgressEntity(
        book_id = "book-1",
        current_location_json = LegadoProgressMarker.encode(ci, co),
        updated_at = "2026-08-09T00:00:00Z",
    )

    private fun nativeProgress(offset: Int, ci: Int): ReadingProgressEntity = ReadingProgressEntity(
        book_id = "book-1",
        current_location_json = LocatorBuilder.progressJson(
            legacyOffset = offset,
            chapterIndex = ci,
            charOffset = offset,
        ),
        updated_at = "2026-08-09T00:00:00Z",
    )

    /** 小文件（< 5MB 阈值）：两章。 */
    private fun smallChaptersFile(): File {
        val body = "测试正文内容。".repeat(70)
        return File(tempRoot, "small.txt").apply {
            writeText("第1章 甲\n$body\n第2章 乙\n$body")
        }
    }

    /** 大文件（> 5MB 阈值）：12 章，每章约 44 万字节。 */
    private fun largeChaptersFile(name: String): File {
        val chapterBody = "测试正文内容。\n".repeat(20_000)
        return File(tempRoot, name).apply {
            bufferedWriter().use { writer ->
                repeat(12) { i ->
                    writer.write("第${i + 1}章 试炼之地\n")
                    writer.write(chapterBody)
                }
            }
        }
    }

    /** 大文件（> 5MB 阈值）：无任何章节标题（流式目录兜底为「全文」单章）。 */
    private fun largeChapterlessFile(): File =
        File(tempRoot, "chapterless.txt").apply {
            bufferedWriter().use { writer -> repeat(240_000) { writer.write("测试正文内容。\n") } }
        }

    @Test
    fun `small txt keeps imported legado position via pre-detected chapters`() = runTest {
        val file = smallChaptersFile()
        installUriFor(file)
        coEvery { bookRepository.getById("book-1") } returns txtBook(file)
        coEvery { readingProgressDao.getByBook("book-1") } returns markerProgress(ci = 1, co = 5)

        val loaded = loader.load("book-1")
        val content = loaded.content as ReaderLoadedContent.Text

        val secondChapterStart = content.preDetectedChapters[1].startOffset
        assertEquals("非流式路径行为保持不变", secondChapterStart + 5, content.initialAbsoluteOffset)
    }

    @Test
    fun `streaming txt converts legado chapter position from full file index`() = runTest {
        val file = largeChaptersFile("convert.txt")
        installUriFor(file)
        coEvery { bookRepository.getById("book-1") } returns txtBook(file)
        coEvery { readingProgressDao.getByBook("book-1") } returns markerProgress(ci = 9, co = 123)

        val loaded = loader.load("book-1")
        val content = loaded.content as ReaderLoadedContent.Text

        assertNotNull("必须走流式路径", content.streamingDocument)
        val chapters = requireNotNull(content.fileIndex).chapters
        assertEquals("第1章…第12章 全部识别", 12, chapters.size)
        // Legado 第 10 章（ci=9）→ 本阅读器目录同序章的起点 + 章内偏移。
        val expected = chapters[9].charStart + 123
        assertEquals(expected.toInt(), content.initialAbsoluteOffset)
        assertEquals("换算成功时不触发提示接缝", null, content.unconvertedLegadoPosition)
    }

    @Test
    fun `out of range legado chapter falls back to book start without fabricating a position`() = runTest {
        val file = largeChaptersFile("out-of-range.txt")
        installUriFor(file)
        coEvery { bookRepository.getById("book-1") } returns txtBook(file)
        coEvery { readingProgressDao.getByBook("book-1") } returns markerProgress(ci = 999, co = 0)

        val loaded = loader.load("book-1")
        val content = loaded.content as ReaderLoadedContent.Text

        assertEquals("章号越界不得编造绝对位置", 0, content.initialAbsoluteOffset)
        assertEquals(
            "未换算的导入位置经提示接缝暴露，marker 坐标原样保留",
            LegadoChapterPosition(chapterIndex = 999, charOffset = 0),
            content.unconvertedLegadoPosition,
        )
    }

    @Test
    fun `chapterless streaming file never converts against the degenerate full-text entry`() = runTest {
        val file = largeChapterlessFile()
        installUriFor(file)
        coEvery { bookRepository.getById("book-1") } returns txtBook(file)
        coEvery { readingProgressDao.getByBook("book-1") } returns markerProgress(ci = 0, co = 5000)

        val loaded = loader.load("book-1")
        val content = loaded.content as ReaderLoadedContent.Text

        val index = requireNotNull(content.fileIndex)
        assertEquals("兜底形态：仅「全文」单章，无可靠边界", 1, index.chapters.size)
        assertEquals("不得把章内偏移当作全书偏移使用（否则会落到 5000）", 0, content.initialAbsoluteOffset)
        assertEquals(
            "章节来源不完整时经提示接缝暴露，不伪装恢复成功",
            LegadoChapterPosition(chapterIndex = 0, charOffset = 5000),
            content.unconvertedLegadoPosition,
        )
    }

    @Test
    fun `native locator progress is honored directly over marker conversion`() = runTest {
        val file = largeChaptersFile("native.txt")
        installUriFor(file)
        coEvery { bookRepository.getById("book-1") } returns txtBook(file)
        // 原生 v2 locator（本应用上一次会话保存的全书偏移），不是 Legado marker：
        // 读侧必须直接采用它，而不是再做任何章号换算。写侧「导入不覆盖原生进度」
        // 由 LegadoReadingProgressImporterTest 的
        // does not overwrite an existing native plain text position 钉住。
        coEvery { readingProgressDao.getByBook("book-1") } returns nativeProgress(offset = 777_777, ci = 2)

        val loaded = loader.load("book-1")
        val content = loaded.content as ReaderLoadedContent.Text

        assertEquals(777_777, content.initialAbsoluteOffset)
        assertEquals("原生 locator 不存在 Legado 导入位置，接缝恒为 null", null, content.unconvertedLegadoPosition)
    }

    @Test
    fun `converted position survives exit and re-enter via native save round trip`() = runTest {
        val file = largeChaptersFile("roundtrip.txt")
        installUriFor(file)
        coEvery { bookRepository.getById("book-1") } returns txtBook(file)
        coEvery { readingProgressDao.getByBook("book-1") } returns markerProgress(ci = 3, co = 45)

        val firstOpen = loader.load("book-1").content as ReaderLoadedContent.Text
        val converted = firstOpen.initialAbsoluteOffset
        val expectedChapterStart = requireNotNull(firstOpen.fileIndex).chapters[3].charStart + 45
        assertEquals(expectedChapterStart.toInt(), converted)

        // 首开后阅读器防抖/退出保存会把原生 locator 写回（TXT 形态：全局偏移同时入
        // legacyOffset 与 v2）；模拟该落库后的「重进」——不再需要 marker，也必须回到
        // 同一位置。
        coEvery { readingProgressDao.getByBook("book-1") } returns nativeProgress(offset = converted, ci = 3)
        val secondOpen = loader.load("book-1").content as ReaderLoadedContent.Text

        assertEquals("退出重进保持可恢复", converted, secondOpen.initialAbsoluteOffset)
    }

    /** 内存版 [ReaderTextRuleDao]：镜像 SQL 排序与 upsert/update 语义（与 ProfileTest 各自私有）。 */
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
