package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.doc.MarkdownParser
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索命中导航执行器（[SearchHitNavigationExecutor]）的公共 seam：
 * 给定文档上下文（章文档 EPUB/Markdown vs 纯文本 TXT）、渲染模式（分页/滚动）与
 * [SearchHitTarget]，稳定 interface 产出并执行正确导航命令；
 * stale target（跨书迟到 / 取块期间改选或换 query / 手动离章）不覆盖新命中。
 *
 * P1 修复：滚动聚焦不再是裸 Int?，而是在 stale guard 通过后发布带身份的一次性
 * [SearchScrollFocusRequest]（书 / 章 / 渲染单元 / 命中下标），本测试断言
 * 发布内容携带完整身份。
 *
 * 这是原 [SearchHitNavigationEffect] 内联分派的可测缝：执行器只依赖
 * [SearchHitNavigationSink] 端口与 [SearchHitNavigator] 会话，不接触 Compose 状态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchHitNavigationTest {

    private fun result(
        index: Int,
        chapterIndex: Int = -1,
        range: IntRange = index * 10 until index * 10 + 2,
    ) = BookSearchResult(
        occurrenceIndex = index,
        snippet = "s$index",
        progressPercent = index * 0.1f,
        chapterIndex = chapterIndex,
        chapterTitle = if (chapterIndex >= 0) "章$chapterIndex" else "全文",
        charOffset = range.first,
        absoluteRange = range,
    )

    /** 真实会话：target 由 [SearchHitNavigator] seam 产出，执行器端到端消费。 */
    private fun sessionWith(
        bookKey: String,
        results: List<BookSearchResult>,
        selected: Int,
    ): BookSearchSession {
        val s = BookSearchSession(bookKey = bookKey)
        s.onQueryChanged("测试")
        val run = s.beginRun()
        s.onSearchCompleted(run, results)
        s.select(selected)
        return s
    }

    /** 记录桩：记录执行器产出的每条导航命令；[loadChapterBlocks] 可挂起模拟阻塞取块。 */
    private class RecordingSink(
        override val bookKey: String = "book-1",
        override var currentChapterIndex: Int = 0,
    ) : SearchHitNavigationSink {
        val chapterSwitches = mutableListOf<Int>()
        val pagedJumps = mutableListOf<Int>()
        val unitScrolls = mutableListOf<Int>()
        val scrollFocusRequests = mutableListOf<SearchScrollFocusRequest>()
        val blockLoads = mutableListOf<Int>()

        var blocksToReturn: List<DocBlock> = emptyList()
        var blockLoadGate: CompletableDeferred<Unit>? = null

        override fun switchToChapter(chapterIndex: Int) {
            chapterSwitches += chapterIndex
            // 模拟阅读器章状态随 switch 收敛（stale 场景由测试显式改回其他章）
            currentChapterIndex = chapterIndex
        }

        override fun jumpToPagedOffset(absoluteOffset: Int) {
            pagedJumps += absoluteOffset
        }

        override suspend fun scrollToUnit(unitIndex: Int) {
            unitScrolls += unitIndex
        }

        override fun publishScrollFocus(request: SearchScrollFocusRequest) {
            scrollFocusRequests += request
        }

        override suspend fun loadChapterBlocks(chapterIndex: Int): List<DocBlock> {
            blockLoads += chapterIndex
            blockLoadGate?.await()
            return blocksToReturn
        }
    }

    private fun executor(
        session: SearchHitNavigator,
        sink: RecordingSink = RecordingSink(),
        chaptered: Boolean = false,
        pagerEngineOn: Boolean = false,
        chapterStartOffsets: List<Int> = emptyList(),
        readingUnits: List<ReadingUnit> = emptyList(),
        markdownBlocksGlobal: Boolean = false,
    ) = SearchHitNavigationExecutor(
        session = session,
        sink = sink,
        chaptered = chaptered,
        pagerEngineOn = pagerEngineOn,
        chapterStartOffsets = chapterStartOffsets,
        readingUnits = readingUnits,
        markdownBlocksGlobal = markdownBlocksGlobal,
    )

    // ── 分派矩阵：章文档（EPUB/Markdown 共用同一 CHAPTERED 分支）──────────────

    @Test
    fun `chaptered paged target switches chapter then jumps to absolute offset`() = runTest {
        val sink = RecordingSink()
        val session = sessionWith("book-1", listOf(result(0, chapterIndex = 2, range = 500 until 503)), selected = 0)
        val nav = executor(
            session = session,
            sink = sink,
            chaptered = true,
            pagerEngineOn = true,
            chapterStartOffsets = listOf(0, 250, 500),
        )

        nav.navigateToCurrentTarget()

        assertEquals(listOf(2), sink.chapterSwitches)
        assertEquals(listOf(500), sink.pagedJumps)
        assertTrue(sink.unitScrolls.isEmpty())
        assertTrue(sink.scrollFocusRequests.isEmpty())
        assertTrue(sink.blockLoads.isEmpty())
    }

    @Test
    fun `chaptered scroll target loads chapter blocks and focuses containing block`() = runTest {
        val sink = RecordingSink().apply {
            blocksToReturn = listOf(
                DocBlock.Text("abc"),
                DocBlock.Image("cover.png", 100, 100),
                DocBlock.Text("def"),
            )
        }
        val session = sessionWith("book-1", listOf(result(0, chapterIndex = 2, range = 505 until 508)), selected = 0)
        val nav = executor(
            session = session,
            sink = sink,
            chaptered = true,
            pagerEngineOn = false,
            chapterStartOffsets = listOf(0, 250, 500),
        )

        nav.navigateToCurrentTarget()

        // 章内偏移 5（505 - 500）落在第三个文本块（跳过图片块）上
        assertEquals(listOf(2), sink.chapterSwitches)
        assertEquals(listOf(2), sink.blockLoads)
        assertEquals(
            listOf(SearchScrollFocusRequest(bookKey = "book-1", chapterIndex = 2, renderUnitIndex = 2, resultIndex = 0)),
            sink.scrollFocusRequests,
        )
        assertTrue(sink.pagedJumps.isEmpty())
    }

    @Test
    fun `chaptered target with negative chapter index coerces to chapter zero`() = runTest {
        val sink = RecordingSink()
        val session = sessionWith("book-1", listOf(result(0, chapterIndex = -1, range = 3 until 6)), selected = 0)
        val nav = executor(
            session = session,
            sink = sink,
            chaptered = true,
            pagerEngineOn = true,
            chapterStartOffsets = listOf(0),
        )

        nav.navigateToCurrentTarget()

        assertEquals(listOf(0), sink.chapterSwitches)
        assertEquals(listOf(3), sink.pagedJumps)
    }

    @Test
    fun `markdown scroll target focuses canonical render unit in non-first chapter`() = runTest {
        // 流式逐章解析：块 canonicalRange 为章内局部坐标，章节起点在全书偏移 200。
        val chapter = MarkdownParser.parse("第一段内容。\n\n第二段出现**搜索词**。")
        val hitStart = chapter.canonicalText.indexOf("搜索词")
        val sink = RecordingSink().apply {
            blocksToReturn = listOf(DocBlock.Markdown(chapter))
        }
        val session = sessionWith(
            "book-1",
            listOf(result(0, chapterIndex = 1, range = 200 + hitStart until 200 + hitStart + 3)),
            selected = 0,
        )
        val nav = executor(
            session = session,
            sink = sink,
            chaptered = true,
            pagerEngineOn = false,
            chapterStartOffsets = listOf(0, 200),
            markdownBlocksGlobal = false,
        )

        nav.navigateToCurrentTarget()

        // 非首章命中：先切章，加载 Markdown 章块，再按 canonical mapping 聚焦
        // 包含命中的第二个段落（渲染单元索引 1，与 Markdown LazyColumn 项顺序一致）。
        assertEquals(listOf(1), sink.chapterSwitches)
        assertEquals(listOf(1), sink.blockLoads)
        assertEquals(
            listOf(SearchScrollFocusRequest(bookKey = "book-1", chapterIndex = 1, renderUnitIndex = 1, resultIndex = 0)),
            sink.scrollFocusRequests,
        )
        assertTrue(sink.pagedJumps.isEmpty())
    }

    @Test
    fun `markdown small-file global blocks focus render unit in non-first chapter`() = runTest {
        // 小文件整本解析：块 canonicalRange 为全书全局坐标，chapterStartOffsets 按 H1 切章。
        val source = "# 第一章\n\n第一章内容。\n\n# 第二章\n\n第二章出现**搜索词**。"
        val chapter = MarkdownParser.parse(source)
        val secondChapterBase = chapter.headings
            .first { it.level == 1 && it.title == "第二章" }
            .canonicalOffset
        val hitStart = chapter.canonicalText.indexOf("搜索词")
        val sink = RecordingSink().apply {
            blocksToReturn = listOf(DocBlock.Markdown(chapter))
        }
        val session = sessionWith(
            "book-1",
            listOf(result(0, chapterIndex = 1, range = hitStart until hitStart + 3)),
            selected = 0,
        )
        val nav = executor(
            session = session,
            sink = sink,
            chaptered = true,
            pagerEngineOn = false,
            chapterStartOffsets = listOf(0, secondChapterBase),
            markdownBlocksGlobal = true,
        )

        nav.navigateToCurrentTarget()

        // 整章渲染单元顺序：H1 第一章 / 第一段 / H1 第二章 / 第二段 → 命中在索引 3。
        assertEquals(listOf(1), sink.chapterSwitches)
        assertEquals(listOf(1), sink.blockLoads)
        assertEquals(
            listOf(SearchScrollFocusRequest(bookKey = "book-1", chapterIndex = 1, renderUnitIndex = 3, resultIndex = 0)),
            sink.scrollFocusRequests,
        )
    }

    @Test
    fun `markdown scroll focus request carries full target identity`() = runTest {
        // 非首章命中 + 非零 resultIndex：发布的一次性请求必须携带
        // 书 / 章 / 渲染单元 / 命中下标的完整身份。
        val chapter = MarkdownParser.parse("第一段内容。\n\n第二段出现**搜索词**。")
        val hitStart = chapter.canonicalText.indexOf("搜索词")
        val sink = RecordingSink().apply {
            blocksToReturn = listOf(DocBlock.Markdown(chapter))
        }
        val session = sessionWith(
            "book-1",
            listOf(
                result(0, chapterIndex = 1, range = 100 until 103),
                result(1, chapterIndex = 1, range = 200 until 203),
                result(2, chapterIndex = 1, range = 200 + hitStart until 200 + hitStart + 3),
            ),
            selected = 2,
        )
        val nav = executor(
            session = session,
            sink = sink,
            chaptered = true,
            pagerEngineOn = false,
            chapterStartOffsets = listOf(0, 200),
            markdownBlocksGlobal = false,
        )

        nav.navigateToCurrentTarget()

        assertEquals(
            listOf(
                SearchScrollFocusRequest(
                    bookKey = "book-1",
                    chapterIndex = 1,
                    renderUnitIndex = 1,
                    resultIndex = 2,
                ),
            ),
            sink.scrollFocusRequests,
        )
        assertEquals(listOf(1), sink.chapterSwitches)
        assertEquals(listOf(1), sink.blockLoads)
    }

    // ── 分派矩阵：纯文本 TXT ────────────────────────────────────────────

    @Test
    fun `txt paged target jumps to absolute offset without chapter switch`() = runTest {
        val sink = RecordingSink()
        val session = sessionWith("book-1", listOf(result(0, chapterIndex = -1, range = 1200 until 1202)), selected = 0)
        val nav = executor(session = session, sink = sink, pagerEngineOn = true)

        nav.navigateToCurrentTarget()

        assertEquals(listOf(1200), sink.pagedJumps)
        assertTrue(sink.chapterSwitches.isEmpty())
        assertTrue(sink.unitScrolls.isEmpty())
    }

    @Test
    fun `txt scroll target scrolls to containing reading unit`() = runTest {
        val sink = RecordingSink()
        val units = listOf(
            ReadingUnit(0, 0, "全文", 0, 500),
            ReadingUnit(1, 0, "全文", 500, 500),
        )
        val session = sessionWith("book-1", listOf(result(0, chapterIndex = -1, range = 505 until 507)), selected = 0)
        val nav = executor(session = session, sink = sink, readingUnits = units)

        nav.navigateToCurrentTarget()

        assertEquals(listOf(1), sink.unitScrolls)
        assertTrue(sink.pagedJumps.isEmpty())
        assertTrue(sink.chapterSwitches.isEmpty())
    }

    @Test
    fun `txt scroll with no reading units is a safe no-op`() = runTest {
        val sink = RecordingSink()
        val session = sessionWith("book-1", listOf(result(0, chapterIndex = -1, range = 505 until 507)), selected = 0)
        val nav = executor(session = session, sink = sink)

        nav.navigateToCurrentTarget()

        assertTrue(sink.unitScrolls.isEmpty())
        assertTrue(sink.pagedJumps.isEmpty())
        assertTrue(sink.chapterSwitches.isEmpty())
        assertTrue(sink.scrollFocusRequests.isEmpty())
    }

    // ── stale guard：旧 target 不得覆盖新命中 ────────────────────────────

    @Test
    fun `cross-book late target is dropped without any command`() = runTest {
        val sink = RecordingSink(bookKey = "current-book")
        val stale = SearchHitTarget(
            bookKey = "old-book",
            chapterIndex = 1,
            absoluteRange = 100 until 103,
            resultIndex = 0,
        )
        val session = sessionWith("old-book", listOf(result(0, chapterIndex = 1, range = 100 until 103)), selected = 0)
        val nav = executor(session = session, sink = sink, chaptered = true, pagerEngineOn = true)

        nav.navigateTo(stale)

        assertTrue(sink.chapterSwitches.isEmpty())
        assertTrue(sink.pagedJumps.isEmpty())
        assertTrue(sink.unitScrolls.isEmpty())
        assertTrue(sink.scrollFocusRequests.isEmpty())
    }

    @Test
    fun `target reselected during block load does not focus old hit`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val sink = RecordingSink().apply {
            blockLoadGate = gate
            blocksToReturn = listOf(DocBlock.Text("abcdef"))
        }
        val session = sessionWith("book-1", listOf(result(0, chapterIndex = 0), result(1, chapterIndex = 0)), selected = 0)
        val nav = executor(
            session = session,
            sink = sink,
            chaptered = true,
            pagerEngineOn = false,
            chapterStartOffsets = listOf(0),
        )

        val job = launch { nav.navigateToCurrentTarget() }
        runCurrent() // 执行到 loadChapterBlocks 挂起（阻塞取块不可中断）
        session.select(1) // 同章连点：currentTarget 已换成新命中
        gate.complete(Unit)
        job.join()

        assertEquals(listOf(0), sink.chapterSwitches)
        assertEquals(listOf(0), sink.blockLoads)
        // 旧命中不得发布 focus 覆盖新命中
        assertTrue(sink.scrollFocusRequests.isEmpty())
    }

    @Test
    fun `query cleared during block load does not focus stale hit`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val sink = RecordingSink().apply {
            blockLoadGate = gate
            blocksToReturn = listOf(DocBlock.Text("abcdef"))
        }
        val session = sessionWith("book-1", listOf(result(0, chapterIndex = 0)), selected = 0)
        val nav = executor(
            session = session,
            sink = sink,
            chaptered = true,
            pagerEngineOn = false,
            chapterStartOffsets = listOf(0),
        )

        val job = launch { nav.navigateToCurrentTarget() }
        runCurrent()
        session.onQueryChanged("") // 清空查询：currentTarget 变 null
        gate.complete(Unit)
        job.join()

        assertTrue(sink.scrollFocusRequests.isEmpty())
    }

    @Test
    fun `chapter left during block load does not focus target chapter`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val sink = RecordingSink().apply {
            blockLoadGate = gate
            blocksToReturn = listOf(DocBlock.Text("abcdef"))
        }
        val session = sessionWith("book-1", listOf(result(0, chapterIndex = 2)), selected = 0)
        val nav = executor(
            session = session,
            sink = sink,
            chaptered = true,
            pagerEngineOn = false,
            chapterStartOffsets = listOf(0, 100, 200),
        )

        val job = launch { nav.navigateToCurrentTarget() }
        runCurrent()
        sink.currentChapterIndex = 5 // 取块期间用户经目录/章节按钮手动跳走
        gate.complete(Unit)
        job.join()

        assertEquals(listOf(2), sink.chapterSwitches)
        assertEquals(listOf(2), sink.blockLoads)
        assertTrue(sink.scrollFocusRequests.isEmpty())
    }

    @Test
    fun `markdown chapter left during block load does not focus stale hit`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val chapter = MarkdownParser.parse("第一段内容。\n\n第二段出现**搜索词**。")
        val sink = RecordingSink().apply {
            blockLoadGate = gate
            blocksToReturn = listOf(DocBlock.Markdown(chapter))
        }
        val session = sessionWith("book-1", listOf(result(0, chapterIndex = 2)), selected = 0)
        val nav = executor(
            session = session,
            sink = sink,
            chaptered = true,
            pagerEngineOn = false,
            chapterStartOffsets = listOf(0, 100, 200),
            markdownBlocksGlobal = false,
        )

        val job = launch { nav.navigateToCurrentTarget() }
        runCurrent() // 执行到 loadChapterBlocks 挂起（阻塞取块不可中断）
        sink.currentChapterIndex = 5 // 取块期间用户经目录/章节按钮手动跳走
        gate.complete(Unit)
        job.join()

        assertEquals(listOf(2), sink.chapterSwitches)
        assertEquals(listOf(2), sink.blockLoads)
        // Markdown 旧命中同样不得发布 focus 覆盖新章
        assertTrue(sink.scrollFocusRequests.isEmpty())
    }

    @Test
    fun `markdown target reselected during block load does not focus old hit`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val chapter = MarkdownParser.parse("第一段内容。\n\n第二段出现**搜索词**。")
        val sink = RecordingSink().apply {
            blockLoadGate = gate
            blocksToReturn = listOf(DocBlock.Markdown(chapter))
        }
        val session = sessionWith(
            "book-1",
            listOf(result(0, chapterIndex = 0), result(1, chapterIndex = 0)),
            selected = 0,
        )
        val nav = executor(
            session = session,
            sink = sink,
            chaptered = true,
            pagerEngineOn = false,
            chapterStartOffsets = listOf(0),
            markdownBlocksGlobal = false,
        )

        val job = launch { nav.navigateToCurrentTarget() }
        runCurrent()
        session.select(1) // 同章连点：currentTarget 已换成新命中
        gate.complete(Unit)
        job.join()

        assertEquals(listOf(0), sink.chapterSwitches)
        assertEquals(listOf(0), sink.blockLoads)
        assertTrue(sink.scrollFocusRequests.isEmpty())
    }

    @Test
    fun `no selection is a safe no-op`() = runTest {
        val sink = RecordingSink()
        val s = BookSearchSession(bookKey = "book-1")
        val nav = executor(session = s, sink = sink, chaptered = true, pagerEngineOn = true)

        nav.navigateToCurrentTarget()

        assertTrue(sink.chapterSwitches.isEmpty())
        assertTrue(sink.pagedJumps.isEmpty())
        assertTrue(sink.unitScrolls.isEmpty())
        assertTrue(sink.scrollFocusRequests.isEmpty())
    }
}
