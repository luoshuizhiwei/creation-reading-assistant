package com.creationreadingassistant.feature.reader.layout.android

import android.icu.text.BreakIterator
import com.creationreadingassistant.feature.reader.layout.BreakOracle

/**
 * 基于 `android.icu.text.BreakIterator` 的断点来源。
 *
 * `android.icu` 自 API 24 起可用，本项目 minSdk 正好是 24。
 *
 * **认知修正**：先前以为「LineBreak.Strictness 在 minSdk 24 上空转，所以中文避头尾
 * 必须全部自研」——这个论证是错的。Strictness 只管 CJK 小书写符号的松紧档，
 * 而基本禁则来自 UAX #14，minikin 自 Android 5 起就通过 ICU 免费提供。
 * 实测三万字纯中文，行首禁则标点出现 0 次。
 *
 * 所以这里的定位是：**ICU 给候选断点，自研禁则表在其上做减法**。
 * 西文分词、数字串、URL、破折号省略号这些复杂情形直接白嫖 ICU，不自己写。
 *
 * `BreakIterator` 非线程安全，用 ThreadLocal 隔离：排版可能在多个后台协程里并发跑。
 */
class IcuBreakOracle : BreakOracle {

    private val iterator = ThreadLocal.withInitial { BreakIterator.getLineInstance() }

    override fun breaks(text: String): BooleanArray {
        val out = BooleanArray(text.length + 1)
        if (text.isEmpty()) {
            out[0] = true
            return out
        }
        val it = iterator.get()!!
        it.setText(text)
        var p = it.first()
        while (p != BreakIterator.DONE) {
            if (p in out.indices) out[p] = true
            p = it.next()
        }
        out[0] = true
        out[text.length] = true
        return out
    }
}
