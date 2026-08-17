package com.creationreadingassistant.feature.reader.layout

import java.text.BreakIterator

/**
 * 合法断点来源。
 *
 * **重要认知修正**：先前以为「`LineBreak.Strictness` 在 minSdk 24 上空转，所以中文避头尾
 * 必须全部自研」—— 这个论证是错的。基本禁则来自 UAX #14，ICU 的 line BreakIterator 自
 * API 24 起就免费提供，实测三万字纯中文行首禁则标点出现 0 次。
 *
 * 自研禁则表的真正作用是**覆写**而非重造：
 * - ICU 不管中文标点的宽度挤压与两端对齐，那需要逐簇 x 坐标；
 * - 简体弯引号 `”` 在 UCD 里是 QU 类，中文语境下 UAX #14 的 LB19a 四条规则都带
 *   `[^EastAsian]` 前提而不适用，理论上会跑到行首（真机是否复现待验证）。
 *
 * 所以策略是：ICU 给出候选断点，[CharClass] 的禁则表在其上做减法。
 * 西文分词、数字串、URL 这些复杂情形直接白嫖 ICU，不自己写。
 */
interface BreakOracle {
    /**
     * 返回长度为 `text.length + 1` 的位图，`true` 表示该字符位置允许断行。
     */
    fun breaks(text: String): BooleanArray
}

/**
 * 基于 JDK/ICU `BreakIterator` 的实现。纯 JVM，可在单测中直接使用 ——
 * Android 的 `android.icu.text.BreakIterator` 行为等价，平台实现只是换个类名。
 */
class JdkBreakOracle : BreakOracle {

    private val iterator = ThreadLocal.withInitial { BreakIterator.getLineInstance() }

    override fun breaks(text: String): BooleanArray {
        val out = BooleanArray(text.length + 1)
        if (text.isEmpty()) {
            out[0] = true
            return out
        }
        // ThreadLocal.withInitial 保证 get() 永不为 null，构造期断言固化成非空局部
        val it = requireNotNull(iterator.get()) { "ThreadLocal.withInitial 保证非空" }
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

/**
 * 兜底实现：任意位置均可断。
 *
 * 用于单测中隔离 ICU 行为，验证「禁则表本身」是否正确 ——
 * 若用真 ICU，测试失败时分不清是禁则表错了还是 ICU 的判断不同。
 */
class AnywhereBreakOracle : BreakOracle {
    override fun breaks(text: String) = BooleanArray(text.length + 1) { true }
}
