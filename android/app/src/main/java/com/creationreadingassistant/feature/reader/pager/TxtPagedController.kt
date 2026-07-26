package com.creationreadingassistant.feature.reader.pager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.layout.BreakOracle
import com.creationreadingassistant.feature.reader.layout.ChapterPaginator
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import com.creationreadingassistant.feature.reader.layout.TextRuler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TXT 左右翻页的状态机：持有「当前章的整章排版 + 当前页号」。
 *
 * 职责边界：
 * - 排版跑在 [Dispatchers.Default]，UI 线程只读状态
 * - 跨章翻页：本章末页再往后 → 排下一章、停在其首页；本章首页再往前 → 排上一章、停在其**末页**
 * - 位置的真源是**全书字符偏移**（[currentPageStartAbs]），页号只是它的展示形式。
 *   改字号/换视口后用旧偏移调 [open]，就会停在同一句 —— 这正是 P2 的验收项。
 * - [PageIndexStore] 是写通缓存：每次整章排版后把 pageStarts 落库。
 *   冷启动读它可以在排版完成前就报出「第 x / N 页」（此期间页面内容仍需等排版）。
 *
 * 章边界语义：[DocChapter.startOffset] 是章内偏移 0 的位置，
 * 排版产物里的一切偏移都是章内的，对外统一换算成全书偏移。
 */
class TxtPagedController(
    private val fullText: String,
    private val chapters: List<DocChapter>,
    private val cfg: LayoutConfig,
    private val ruler: TextRuler,
    private val oracle: BreakOracle,
    private val scope: CoroutineScope,
    private val store: PageIndexStore? = null,
    private val contentKey: String = "",
) {

    var chapterIndex by mutableIntStateOf(0)
        private set
    var pageIndex by mutableIntStateOf(0)
        private set
    var layout by mutableStateOf<ChapterPaginator.ChapterLayout?>(null)
        private set

    /** 排版进行中（切章/首开时短暂为 true；章内翻页恒为 false）。 */
    var isLayingOut by mutableStateOf(false)
        private set

    val pageCount: Int get() = layout?.pages?.size ?: 0
    val currentPage: ChapterPaginator.Page? get() = layout?.pages?.getOrNull(pageIndex)

    /** 当前章文本（绘制层按 lineParaOffsets 从中切簇）。 */
    var chapterText: String = ""
        private set

    /** 当前页首字符的全书偏移。进度持久化以它为准。 */
    val currentPageStartAbs: Int
        get() {
            val ch = chapters.getOrNull(chapterIndex) ?: return 0
            val l = layout ?: return ch.startOffset
            return ch.startOffset + (l.pageStarts.getOrNull(pageIndex) ?: 0)
        }

    /**
     * 当前页覆盖的全书偏移区间 [start, end)。排版未完成时为 null。
     *
     * 锚点纪律要用它：改字号/转屏引发的重排结束后，若阅读锚点仍落在本区间内，
     * **绝不能把锚点改成新的页首** —— 页首只会往前挪，连续几次重排锚点就会
     * 一路往回漂（真机上表现为连点几次 A+ 后倒退了两三页）。
     */
    val currentPageRangeAbs: IntRange?
        get() {
            val ch = chapters.getOrNull(chapterIndex) ?: return null
            val page = currentPage ?: return null
            val start = ch.startOffset + page.startCharOffset
            val end = ch.startOffset + page.endCharOffset
            return start until (if (end > start) end else start + 1)
        }

    /** 当前章起始的全书偏移（选区/TTS 的章内↔全书换算用）。 */
    val currentChapterStartAbs: Int
        get() = chapters.getOrNull(chapterIndex)?.startOffset ?: 0

    val canGoPrev: Boolean get() = pageIndex > 0 || chapterIndex > 0
    val canGoNext: Boolean
        get() = pageIndex < pageCount - 1 || chapterIndex < chapters.size - 1

    /** 全书进度百分比。末章末页视为 100。 */
    val progressPercent: Float
        get() {
            if (fullText.isEmpty()) return 0f
            if (chapterIndex == chapters.size - 1 && pageCount > 0 && pageIndex == pageCount - 1) return 100f
            return (currentPageStartAbs * 100f / fullText.length).coerceIn(0f, 100f)
        }

    private var layoutJob: Job? = null

    /** 打开到全书偏移 [absOffset] 所在的页。幂等，可反复调。 */
    fun open(absOffset: Int) {
        val target = absOffset.coerceIn(0, (fullText.length - 1).coerceAtLeast(0))
        val chIdx = chapterIndexFor(target)
        loadChapter(chIdx) { l ->
            val inChapter = target - chapters[chIdx].startOffset
            l.pageIndexFor(inChapter)
        }
    }

    fun nextPage() {
        val l = layout ?: return
        when {
            pageIndex < l.pages.size - 1 -> pageIndex++
            chapterIndex < chapters.size - 1 -> loadChapter(chapterIndex + 1) { 0 }
        }
    }

    fun prevPage() {
        when {
            pageIndex > 0 -> pageIndex--
            chapterIndex > 0 -> loadChapter(chapterIndex - 1) { it.pages.size - 1 }
        }
    }

    private fun chapterIndexFor(absOffset: Int): Int {
        if (chapters.isEmpty()) return 0
        var lo = 0
        var hi = chapters.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (chapters[mid].startOffset <= absOffset) lo = mid else hi = mid - 1
        }
        return lo
    }

    /**
     * 排版 [chIdx] 章，完成后用 [pickPage] 决定停在哪页。
     * 并发纪律：新请求取消旧请求（快速连续翻章只保留最后一次）。
     */
    private fun loadChapter(chIdx: Int, pickPage: (ChapterPaginator.ChapterLayout) -> Int) {
        val chapter = chapters.getOrNull(chIdx) ?: return
        layoutJob?.cancel()
        isLayingOut = true
        layoutJob = scope.launch {
            val (text, l) = withContext(Dispatchers.Default) {
                val t = TxtPageSource.chapterTextOf(fullText, chapter)
                t to ChapterPaginator.paginate(
                    TxtPageSource.paragraphsOf(t, chapter.title),
                    cfg, ruler, oracle,
                )
            }
            chapterText = text
            chapterIndex = chIdx
            layout = l
            pageIndex = pickPage(l).coerceIn(0, (l.pages.size - 1).coerceAtLeast(0))
            isLayingOut = false

            // 写通缓存：pageStarts 落库（失败无所谓，见 PageIndexStore 的纪律）
            val s = store
            if (s != null && contentKey.isNotBlank()) {
                launch(Dispatchers.IO) {
                    s.save(contentKey, chIdx, cfg.fingerprint, l.pageStarts, l.charCount)
                    s.prune(contentKey)
                }
            }
        }
    }
}
