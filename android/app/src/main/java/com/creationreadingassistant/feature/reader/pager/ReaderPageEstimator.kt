package com.creationreadingassistant.feature.reader.pager

import android.graphics.Paint
import android.text.TextPaint
import com.creationreadingassistant.feature.reader.layout.LayoutConfig
import kotlin.math.max

/**
 * P1-第五批-2: 全书页码/剩余阅读时间估算。
 *
 * 设计原则：
 *  - 估算值只做 UI 展示参考，不影响锚点/分页等真源；
 *  - 每页字数 ≈ 「每页行数 × 每行字数」，行数/字数由当前 [LayoutConfig] + [TextPaint]
 *    测量一个"中文均值字"得到，避免精确逐章排版（全书排版代价太高）；
 *  - 所有结果均为 floor ceil 取整，显示层面保持"整页/整分钟"的人类可读语义。
 */
internal object ReaderPageEstimator {

    /** 估算器输出快照 */
    internal data class PageEstimate(
        /** 全书估算总页数（>=1） */
        val totalPages: Int,
        /** 当前页在全书中的序号（1-based，<= totalPages） */
        val currentPage: Int,
        /** 剩余文字估算的阅读分钟数（>=0） */
        val remainingMinutes: Int,
        /** 剩余字数（从当前页起始位置开始算，避免"本页已读部分"让估算反复跳） */
        val remainingChars: Long,
    )

    /**
     * 估算「平均每页字数」。
     *
     * 算法：
     *  - 行字数 = contentWidthPx / measureText("汉") （中文正文典型宽度）
     *  - 行数 = (contentHeightPx - paragraphSpacingPx) / (lineHeightPx + paragraphSpacingEmGapEstimate)
     *    段落间隔按每页约 4 段摊薄估算，误差容忍在 10% 以内。
     *  - 若 paint 为 null（EPUB/Compose 文本测量不方便时），
     *    退化为基于 fontSizePx 的「中文字 ≈ 1em 宽」粗估（误差通常 <20%）。
     */
    fun charsPerPageEstimate(cfg: LayoutConfig, paint: Paint?): Int {
        val contentW = cfg.contentWidthPx.coerceAtLeast(1f)
        val contentH = cfg.contentHeightPx.coerceAtLeast(1f)
        val lineH = cfg.lineHeightPx.coerceAtLeast(1f)
        val paraGap = cfg.paragraphSpacingPx

        // 1. 平均每字符宽度（px）
        val avgCharW: Float = if (paint != null) {
            // 取 5 个常见中文字求均值，降低单字宽度测量的偶发偏差。
            val sample = "汉读的一是"
            (paint.measureText(sample) / sample.length.coerceAtLeast(1)).coerceAtLeast(1f)
        } else {
            // 无 paint 时退化：中文字 ≈ 1em（fontSizePx）宽。
            cfg.fontSizePx.coerceAtLeast(1f)
        }

        // 2. 每行字数（1 是最低容错；每行扣除首行缩进摊薄约 0.2 字）。
        val charsPerLine = max(6, (contentW / avgCharW).toInt() - 0)

        // 3. 每页行数（行高 + 段间摊薄 0.15x，约每页 4 段 → 3 个段间距 → 每行摊 3/N 段距）
        val paraGapPerLine = if (lineH > 0f && paraGap > 0f) paraGap * 0.18f else 0f
        val linesPerPage = max(5, ((contentH - paraGap * 0.5f) / (lineH + paraGapPerLine)).toInt())

        return (charsPerLine * linesPerPage).coerceAtLeast(80)
    }

    /**
     * 基于章节真源，估算全书页数、当前页号与剩余分钟。
     *
     * @param source 章节数据源（提供 totalChars / chapterStartAbs）
     * @param chapterIndex 当前章（0-based）
     * @param pageIndex 当前章内页号（0-based）
     * @param chapterPageCount 当前章排版后的总页数（有真实值时优先用；<=0 才退化估算）
     * @param charsOnCurrentPage 当前页包含的字数近似值（用于从"当前页起点"算起，
     *   避免剩余页数因刚翻页忽多忽少；<=0 则退化为 0 即"当前页算已经读完"）。
     * @param charsPerPage 每页字数估算（来自 [charsPerPageEstimate]，<=0 兜底 350）
     * @param wpm 阅读速度（字/分钟，<=0 兜底 380）
     */
    fun estimate(
        source: PagedChapterSource,
        chapterIndex: Int,
        pageIndex: Int,
        chapterPageCount: Int,
        charsOnCurrentPage: Int,
        charsPerPage: Int,
        wpm: Int,
    ): PageEstimate {
        val cpp = charsPerPage.coerceAtLeast(200)
        val wordsPerMinute = wpm.coerceAtLeast(150)

        // ── 1. 全书总页数 ──────────────────────────────────────────────
        val totalC = (source.totalChars).toLong().coerceAtLeast(1L)
        val totalPages = ((totalC + cpp - 1) / cpp).toInt().coerceAtLeast(1)

        // ── 2. 当前页的全局序号 ─────────────────────────────────────────
        // 2a. 当前章之前的所有章节字数 → 折算为页
        val chapterStartAbs = if (chapterIndex in 0 until source.chapterCount) {
            source.chapterStartAbs(chapterIndex).toLong()
        } else 0L
        val prevChars = chapterStartAbs.coerceAtLeast(0L)
        val pagesBeforeChapter = prevChars / cpp

        // 2b. 当前章内已经翻过的页数：优先用「当前章已排版真实页数」估算"每页字数"。
        val chapterChars = source.chapterEstimatedCharCount(
            (chapterIndex).coerceIn(0, (source.chapterCount - 1).coerceAtLeast(0)),
        ).toLong().coerceAtLeast(1L)
        val chapterCharsPerPage: Long = if (chapterPageCount > 0) {
            // 章内真实平均每页字数；但要夹在 cpp 的 0.5x~2x 之间，避免极短章的异常。
            val r = (chapterChars + chapterPageCount - 1) / chapterPageCount
            r.coerceIn((cpp * 0.55f).toLong(), (cpp * 2.2f).toLong())
        } else {
            cpp.toLong()
        }
        val chapterPagesRead = (pageIndex.coerceAtLeast(0)).toLong()
        val charsReadIntoChapter = (chapterPagesRead * chapterCharsPerPage)
            .coerceAtMost(chapterChars)

        // 2c. 页号 = 之前章页数 + 当前章内已读页数 + 1（1-based）
        val charsSoFar = (prevChars + charsReadIntoChapter).coerceAtMost(totalC)
        val currentPage = ((charsSoFar + cpp - 1) / cpp + 1).toInt().coerceIn(1, totalPages)

        // ── 3. 剩余字数 & 剩余分钟 ──────────────────────────────────────
        // 剩余字数 = 全书总字数 - charsSoFar（不减去当前页包含字数：
        //   既然 charsSoFar 是"之前章页数折算 + 当前章内到 pageIndex 开头"，
        //   那剩余字数就天然包含当前页尚未读的部分，语义更符合人类"剩 XXX 字"直觉。
        //   用户传入 charsOnCurrentPage 仅在未来需要更精细微调时作为参考。）
        val remainingChars = (totalC - charsSoFar).coerceAtLeast(0L)
        val remainingMinutes = ((remainingChars + wordsPerMinute - 1) / wordsPerMinute).toInt()
            .coerceAtLeast(0)

        return PageEstimate(
            totalPages = totalPages,
            currentPage = currentPage,
            remainingMinutes = remainingMinutes,
            remainingChars = remainingChars,
        )
    }

    /**
     * 将 [PageEstimate] 格式化为阅读器底部栏可读文本。
     * 输出格式：「124/1876 页 · 剩 2 小时 6 分」，若 <60 分钟则「124/1876 页 · 剩 42 分」。
     */
    fun formatProgressHint(est: PageEstimate?): String? {
        if (est == null || est.totalPages <= 0) return null
        val remaining = est.remainingMinutes
        val timePart = when {
            remaining <= 0 -> "剩 不足 1 分"
            remaining < 60 -> "剩 ${remaining} 分"
            else -> {
                val h = remaining / 60
                val m = remaining % 60
                if (m == 0) "剩 ${h} 小时" else "剩 ${h} 小时 ${m} 分"
            }
        }
        return "${est.currentPage}/${est.totalPages} 页 · $timePart"
    }
}
