package com.creationreadingassistant.feature.reader.pager

import kotlin.math.ceil
import kotlin.math.max

/**
 * 全书页码模型（优化清单 A1 的纯核心，借鉴 legado-with-MD3 的 `pageestimate/`）。
 *
 * 解决的问题：阅读器目前只有**章内** X/Y 页码，全书 % 走字符偏移
 * （ReaderProgressActions 的 `absoluteOffset*100/totalChars`），两者口径都对不上
 * 用户眼睛看到的「页」。本模型把每章页数聚合成全书口径：
 * - **已排版章**用精确页数（来自 [PageIndexStore] 的 pageStarts.size）；
 * - **未排版章**用估页：`ceil(章字符数 / 平均页容量)`，平均页容量由已排版章
 *   反推（Σ字数 / Σ页数），没有任何已排版章时用调用方给的兜底值；
 * - 前缀和 `prefix[i]` = 第 i 章首页的全局页号，任何一章的页数变化只做一次
 *   O(n) 重算（n 为章数，典型 ≤2000，可忽略）。
 *
 * 定位与 [PageIndexStore] 一致：**纯缓存口径**，估算错了只影响 % 显示精度，
 * 绝不参与跳转落点计算（跳转仍以字符偏移为准，排完版自然校准）。
 *
 * 线程约定：非线程安全，调用方（ViewModel 主线程）串行访问。
 *
 * @param chapterCharCounts 每章字符数；0 字符的章也占 1 页（章标题/空白页）。
 * @param defaultCharsPerPage 无任何精确样本时的兜底页容量（约一页能排的字数）。
 */
class WholeBookPageModel(
    chapterCharCounts: IntArray,
    private val defaultCharsPerPage: Int = DEFAULT_CHARS_PER_PAGE,
) {
    companion object {
        const val DEFAULT_CHARS_PER_PAGE = 800
        private const val NO_EXACT = -1
    }

    private var charCounts: IntArray = chapterCharCounts.copyOf()
    private var exactPages: IntArray = IntArray(chapterCharCounts.size) { NO_EXACT }

    /** size = chapterCount + 1；prefix[i] 为第 i 章的全局起始页号，prefix[n] = 总页数。 */
    private var prefix: IntArray = IntArray(1)
    private var exactCharSum: Long = 0L
    private var exactPageSum: Long = 0L

    /** 每次结构/页数变化 +1：给 Compose 当 remember key 或做失效判断用。 */
    var revision: Long = 0L
        private set

    val chapterCount: Int get() = charCounts.size

    init {
        rebuildPrefix()
    }

    /** 第 chapterIndex 章的页数（精确优先，否则估算）。越界抛异常，属调用方 bug。 */
    fun pagesOf(chapterIndex: Int): Int {
        checkIndex(chapterIndex)
        return if (exactPages[chapterIndex] != NO_EXACT) {
            exactPages[chapterIndex]
        } else {
            estimatedPages(charCounts[chapterIndex])
        }
    }

    fun isExact(chapterIndex: Int): Boolean {
        checkIndex(chapterIndex)
        return exactPages[chapterIndex] != NO_EXACT
    }

    /** 第 chapterIndex 章首页的全局页号（0 基）。 */
    fun pageOffsetOf(chapterIndex: Int): Int {
        checkIndex(chapterIndex)
        return prefix[chapterIndex]
    }

    /** 全书总页数；空书为 0。 */
    val totalPages: Int get() = prefix[chapterCount]

    /** 章内页 → 全书页号（0 基）。章内页码超出该章当前口径时按最后一页夹住。 */
    fun globalPageOf(chapterIndex: Int, pageIndexInChapter: Int): Int {
        checkIndex(chapterIndex)
        val pages = pagesOf(chapterIndex)
        return prefix[chapterIndex] + pageIndexInChapter.coerceIn(0, max(0, pages - 1))
    }

    /**
     * 章内位置 → 全书百分比（0f..100f）。
     *
     * @param pageCountInChapter 调用方当前页视图的总页数；与模型口径不一致时
     *   只用于算章内比例，页数本身仍以模型（精确优先）为准——这样进度条在
     *   「估算 → 精确」校准瞬间只做小幅收敛，不会跳档。
     */
    fun percentOf(
        chapterIndex: Int,
        pageIndexInChapter: Int,
        pageCountInChapter: Int,
    ): Float {
        checkIndex(chapterIndex)
        val total = totalPages
        if (total <= 0) return 0f
        val pages = pagesOf(chapterIndex)
        val frac = if (pageCountInChapter > 0) {
            (pageIndexInChapter.toFloat() / pageCountInChapter).coerceIn(0f, 1f)
        } else {
            0f
        }
        return ((prefix[chapterIndex] + frac * pages) / total * 100f).coerceIn(0f, 100f)
    }

    /**
     * 登记某章的精确页数（排版完成或从 [PageIndexStore] 命中缓存时调用）。
     *
     * @return 页数实际发生变化（含首次登记估算→精确）时返回 true，调用方可据此
     *   刷新 UI；重复登记同值返回 false，翻页热路径可以每次无脑调。
     */
    fun recordExactPages(chapterIndex: Int, pageCount: Int): Boolean {
        checkIndex(chapterIndex)
        val pages = max(1, pageCount)
        if (exactPages[chapterIndex] == pages) return false
        if (exactPages[chapterIndex] == NO_EXACT) {
            exactCharSum += charCounts[chapterIndex]
            exactPageSum += pages
        } else {
            exactPageSum += pages - exactPages[chapterIndex]
        }
        exactPages[chapterIndex] = pages
        rebuildPrefix()
        return true
    }

    /** 撤销某章精确页数（指纹失配、缓存被剪枝时调用），回到估算口径。 */
    fun forgetExactPages(chapterIndex: Int): Boolean {
        checkIndex(chapterIndex)
        if (exactPages[chapterIndex] == NO_EXACT) return false
        exactCharSum -= charCounts[chapterIndex]
        exactPageSum -= exactPages[chapterIndex]
        exactPages[chapterIndex] = NO_EXACT
        rebuildPrefix()
        return true
    }

    /**
     * 整本章节结构变更（TXT 重导 / 章节检测修正）：按「同下标且字符数不变」
     * 保留精确页数，其余章回落到估算。
     *
     * @return 有任何一章的精确数据被丢弃时返回 true，调用方可随后异步重灌缓存。
     */
    fun setChapterCharCounts(newCounts: IntArray): Boolean {
        var droppedExact = false
        val newExact = IntArray(newCounts.size) { NO_EXACT }
        var charSum = 0L
        var pageSum = 0L
        for (i in newCounts.indices) {
            val sameChars = i < charCounts.size && charCounts[i] == newCounts[i]
            val hadExact = i < charCounts.size && exactPages[i] != NO_EXACT
            if (sameChars && hadExact) {
                newExact[i] = exactPages[i]
                charSum += newCounts[i]
                pageSum += newExact[i]
            } else if (hadExact) {
                // 这一章原本有精确页数，但字符数变了：精确数据作废。
                droppedExact = true
            }
        }
        // 被删掉的尾部章若带精确数据，同样算丢弃。
        if (!droppedExact) {
            for (i in newCounts.size until charCounts.size) {
                if (exactPages[i] != NO_EXACT) {
                    droppedExact = true
                    break
                }
            }
        }
        // 结构与字符数完全没变、也没丢精确数据：无事发生。
        if (!droppedExact && newCounts.contentEquals(charCounts)) return false
        charCounts = newCounts.copyOf()
        exactPages = newExact
        exactCharSum = charSum
        exactPageSum = pageSum
        rebuildPrefix()
        return droppedExact
    }

    /** 当前估算用的平均页容量（字数/页），无精确样本时为兜底值。 */
    val effectiveCharsPerPage: Int
        get() = if (exactPageSum > 0L) {
            (exactCharSum / exactPageSum).toInt().coerceAtLeast(1)
        } else {
            max(1, defaultCharsPerPage)
        }

    private fun estimatedPages(chars: Int): Int {
        val capacity = effectiveCharsPerPage
        return max(1, ceil(chars.toFloat() / capacity).toInt())
    }

    private fun rebuildPrefix() {
        val next = IntArray(charCounts.size + 1)
        var acc = 0
        for (i in charCounts.indices) {
            next[i] = acc
            acc += if (exactPages[i] != NO_EXACT) exactPages[i] else estimatedPages(charCounts[i])
        }
        next[charCounts.size] = acc
        prefix = next
        revision += 1
    }

    private fun checkIndex(chapterIndex: Int) {
        require(chapterIndex in 0 until chapterCount) {
            "chapterIndex $chapterIndex out of bounds for $chapterCount chapters"
        }
    }
}
