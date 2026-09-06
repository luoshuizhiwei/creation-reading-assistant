package com.creationreadingassistant.feature.reader.rules

/**
 * 替换净化的纯文本投影（slice 1）。
 *
 * 职责边界：
 * - [project] 只复用 [RuleEngine.applyReplace]，绝不重新实现正则替换；
 * - sourceText 是持久化权威坐标；displayText 只用于渲染 / 搜索；
 * - [displaySliceForSourceRange] 把任意 source 子范围映射到 display 局部文本与
 *   局部↔全局偏移映射，供后续 unit / window 渲染直接消费。
 *
 * 切片语义（与 [TextOffsetMapTest] 的 floor map 一致）：
 * - 边界一律经 offsetMap 的 toDisplay，不依赖任何 substring 猜偏移；
 * - 删除区间坍缩为空文本；插入尾随字符按 map floor 归属到后续 source 范围；
 * - 源区间划分窗口时，对应 display 区间恰好平铺整个 displayText（无重无漏）。
 */
class ReplaceProjection private constructor(
    /** 持久化权威原文；所有 source 坐标均以此为准。 */
    val sourceText: String,
    /** 渲染 / 搜索用显示文本；仅派生视图，不是权威坐标。 */
    val displayText: String,
    /** display↔source 双向偏移映射（floor 语义，见 [TextOffsetMap]）。 */
    val offsetMap: TextOffsetMap,
    /** 全部生效规则的总命中数。 */
    val hitCount: Int,
    /** 本次投影的规则身份（[ReplaceProfile.key]）。 */
    val profileKey: String,
) {

    /**
     * 把 source 半开区间 [sourceStart, sourceEnd) 投影为 display 局部切片。
     *
     * @param sourceStart 全局 source 起点（含）；负值按 0，超出全长按全长。
     * @param sourceEnd 全局 source 终点（不含）；负值按 0，超出全长按全长。
     * @return [ReplaceSlice]；sourceStart >= sourceEnd（含反转区间）按空范围处理，
     *   锚定在 clamp 后的 sourceEnd 处。
     */
    fun displaySliceForSourceRange(sourceStart: Int, sourceEnd: Int): ReplaceSlice {
        val len = offsetMap.sourceLength
        val s0 = sourceStart.coerceIn(0, len)
        val s1 = sourceEnd.coerceIn(0, len)
        if (s0 >= s1) {
            val d = offsetMap.toDisplay(s1)
            return ReplaceSlice(offsetMap, displayText, s1, s1, d, d)
        }
        return ReplaceSlice(
            offsetMap,
            displayText,
            s0,
            s1,
            offsetMap.toDisplay(s0),
            offsetMap.toDisplay(s1),
        )
    }

    companion object {
        /**
         * 构造投影：复用 [RuleEngine.applyReplace] 并携带 [ReplaceProfile.key]。
         *
         * @param bookId 持久化书身份；当前 [ReplaceRule] 不携带 bookId，必须显式传入。
         */
        fun project(sourceText: String, rules: List<ReplaceRule>, bookId: String): ReplaceProjection {
            val profileKey = ReplaceProfile.key(bookId, rules)
            val result = RuleEngine.applyReplace(sourceText, rules)
            return ReplaceProjection(
                sourceText = sourceText,
                displayText = result.displayText,
                offsetMap = result.offsetMap,
                hitCount = result.hitCount,
                profileKey = profileKey,
            )
        }

        /**
         * 内部装配入口：块级投影器（如 EPUB 的 [ChapterBlockOffsetMap]）已各自完成
         * 正则投影与映射构建，这里只做值组装，不重新跑正则。仅限同模块 seam 使用。
         */
        internal fun ofParts(
            sourceText: String,
            displayText: String,
            offsetMap: TextOffsetMap,
            hitCount: Int,
            profileKey: String,
        ): ReplaceProjection = ReplaceProjection(
            sourceText = sourceText,
            displayText = displayText,
            offsetMap = offsetMap,
            hitCount = hitCount,
            profileKey = profileKey,
        )
    }
}

/**
 * 一个 source 子范围在 display 中的局部切片（纯值对象，无 I/O、无 Compose）。
 *
 * 坐标约定：
 * - [sourceStart]/[sourceEnd] 是全局 source 偏移（持久化权威），
 *   [displayStart]/[displayEnd] 是全局 display 偏移；
 * - [displayToSource] 输入局部 display 偏移，输出全局 source 偏移；
 * - [sourceToDisplay] 输入局部 source 偏移，输出全局 display 偏移；
 * - 局部↔全局换算使用 [sourceBase]/[displayBase] 加减即可。
 *
 * 不变量（全部由 [TextOffsetMap] 的 floor 语义继承）：
 * 两个方向的局部映射单调不减、有界；round trip floor：
 * `sourceToDisplay(displayToSource(x) - sourceBase) - displayBase <= x` 且
 * `displayToSource(sourceToDisplay(y) - displayBase) <= sourceBase + y`。
 * 删除坍缩点的 displayToSource 按 floor 回到删除起点，可能小于本切片名义
 * sourceStart，这是 map 的既有语义，不是越界。
 */
class ReplaceSlice internal constructor(
    private val offsetMap: TextOffsetMap,
    private val displayText: String,
    /** 全局 source 起点（已 clamp）。 */
    val sourceStart: Int,
    /** 全局 source 终点（不含，已 clamp）。 */
    val sourceEnd: Int,
    /** 全局 display 起点（已经 offset map 推导，非猜偏移）。 */
    val displayStart: Int,
    /** 全局 display 终点（不含，已经 offset map 推导）。 */
    val displayEnd: Int,
) {
    /** 局部 display 文本；空范围 / 删除坍缩时为 ""。 */
    val text: String get() = displayText.substring(displayStart, displayEnd)

    /** 全局 source base（= [sourceStart]）。 */
    val sourceBase: Int get() = sourceStart

    /** 全局 display base（= [displayStart]）。 */
    val displayBase: Int get() = displayStart

    /** 局部 source 长度（全局 source 宽度）。 */
    val sourceLength: Int get() = sourceEnd - sourceStart

    /** 局部 display 长度（= [text].length）。 */
    val displayLength: Int get() = displayEnd - displayStart

    /**
     * 局部 display 偏移 → 全局 source 偏移。局部偏移 clamp 到
     * [0, displayLength]，结果有界于 [0, offsetMap.sourceLength]。
     */
    fun displayToSource(localDisplayOffset: Int): Int {
        val local = localDisplayOffset.coerceIn(0, displayLength)
        return offsetMap.toSource(displayBase + local)
    }

    /**
     * 局部 source 偏移 → 全局 display 偏移。局部偏移 clamp 到
     * [0, sourceLength]，结果有界于 [0, offsetMap.displayLength]。
     */
    fun sourceToDisplay(localSourceOffset: Int): Int {
        val local = localSourceOffset.coerceIn(0, sourceLength)
        return offsetMap.toDisplay(sourceBase + local)
    }
}
