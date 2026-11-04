package com.creationreadingassistant.ui.screen.reader

import androidx.compose.ui.text.TextRange
import java.text.BreakIterator

/**
 * 选区粒度收敛（S2）。
 *
 * 背景：readOnly 的 [androidx.compose.foundation.text.BasicTextField] 在长按时会自行
 * 给出一个初始选区。中文没有空格，这个初始选区经常把一整句甚至一整段都吞进去，
 * 观感就是"一长按就全选了"。
 *
 * 这里不跟 BasicTextField 抢手势（抢不赢，也容易把手柄拖动一起弄坏），而是：
 * 用一个**不消费事件的旁听观察者**记下长按位置（见 [readerTextFieldLongPressAnchor]），
 * 等 BasicTextField 把它的初始选区报上来之后，再把这次**首次选区**收敛到长按位置所在的词。
 *
 * 纯 JVM 逻辑（只用 java.text.BreakIterator），无 Android 依赖，可单测：
 * 见 ReaderSelectionGranularityTest。
 */

/** 单个词允许的最大字符数。超过就退化为单字 —— 宁小勿大，用户拖一下就能扩选。 */
internal const val MAX_WORD_CHARS = 8

/**
 * 求 [offset] 所在词的区间（半开 [start, end)）。
 *
 * 退化策略（任一命中即退化为 offset 处的**单字**）：
 * - 文本为空 / offset 越界；
 * - BreakIterator 给出非法区间（含 [BreakIterator.DONE]）；
 * - 切出来的是纯空白；
 * - 词长超过 [maxWordLength] —— 中文被判成一整句时必然命中这一条。
 *
 * 退化到单字是刻意的：选小了用户可以拖手柄扩大，选大了才是"全选"的糟糕体验。
 */
internal fun wordRangeAt(
    text: String,
    offset: Int,
    maxWordLength: Int = MAX_WORD_CHARS,
): IntRange {
    if (text.isEmpty()) return 0..0
    val safe = offset.coerceIn(0, text.length - 1)
    val fallback = safe..(safe + 1).coerceAtMost(text.length)
    return try {
        val iterator = BreakIterator.getWordInstance()
        iterator.setText(text)
        // 常见写法：following 拿到词尾边界，previous 回退到词首边界。
        val end = iterator.following(safe)
        val start = iterator.previous()
        if (end < 0 || start < 0 || end <= start || end > text.length) return fallback
        val word = text.substring(start, end)
        if (word.isBlank() || word.length > maxWordLength) return fallback
        start..end
    } catch (t: Throwable) {
        // 任何分词异常都不能把"选个字"这件小事变成崩溃。
        fallback
    }
}

/**
 * 把一次**首次**选区收敛到 [anchorOffset] 所在的词。
 *
 * [anchorOffset] 为 null（没监听到长按，比如用户直接拖选）时原样返回，
 * 绝不猜、绝不改 —— 拿不到锚点就维持 BasicTextField 自己的行为。
 */
internal fun clampedInitialSelection(
    text: String,
    selection: TextRange,
    anchorOffset: Int?,
    maxWordLength: Int = MAX_WORD_CHARS,
): TextRange {
    if (anchorOffset == null) return selection
    val range = wordRangeAt(text, anchorOffset, maxWordLength)
    return TextRange(range.first, range.last)
}
