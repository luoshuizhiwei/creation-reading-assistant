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
        fun project(sourceText: String, rules: List<ReplaceRule>, bookId: String): ReplaceProjection =
            projectScoped(sourceText, rules, bookId, scopeSourceBase = 0)

        /**
         * 作用域感知投影：与 [project] 相同，另接受锚定纠错的**全书 source 基址**。
         *
         * [scopeSourceBase] 是 `sourceText` 在全书 source 中的起点：整本小 TXT 传 0；
         * 分章 TXT 传章起点；EPUB 单块传 `章估算基址 + 块章内偏移`。锚点区间先按基址
         * 局部化，未完整落入本作用域的纠错在本作用域诚实跳过（不产生部分替换）。
         *
         * 应用顺序（E2 契约）：
         * 1. 普通正则规则按既有链应用（[RuleEngine.applyReplace]）；
         * 2. 锚定纠错按顺序叠加：锚点 source 区间经**当前累积映射**换算到 display 位置，
         *    校验该处 display 文本仍等于 [CorrectionAnchor.findText]（用户所见），相等才
         *    拼接 [ReplaceRule.replacement]；任何漂移（文件变更/删除坍缩/已被其他纠错
         *    覆盖）都跳过该条，绝不近似替换。
         * 纠错在规则之后应用：用户在「已替换显示文」上选字纠错，语义与所见一致；
         * source 坐标始终权威，display 只是派生视图。
         */
        fun projectScoped(
            sourceText: String,
            rules: List<ReplaceRule>,
            bookId: String,
            scopeSourceBase: Int,
        ): ReplaceProjection {
            require(scopeSourceBase >= 0) { "scopeSourceBase 必须非负，收到 $scopeSourceBase" }
            val profileKey = ReplaceProfile.key(bookId, rules)
            val base = RuleEngine.applyReplace(sourceText, rules)
            val anchored = rules
                .filter { it.enabled && it.anchor != null }
                .sortedBy { it.position }
            if (anchored.isEmpty()) {
                return ReplaceProjection(
                    sourceText = sourceText,
                    displayText = base.displayText,
                    offsetMap = base.offsetMap,
                    hitCount = base.hitCount,
                    profileKey = profileKey,
                )
            }
            return overlayAnchored(
                sourceText = sourceText,
                baseDisplay = base.displayText,
                baseMap = base.offsetMap,
                baseHitCount = base.hitCount,
                corrections = anchored.mapNotNull { rule ->
                    rule.anchor?.let { CorrectionSplice(rule.id, it, rule.replacement) }
                },
                scopeSourceBase = scopeSourceBase,
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

        /** 一条待叠加的锚定纠错（id 仅用于将来回执/诊断，不参与映射）。 */
        private data class CorrectionSplice(
            val ruleId: String,
            val anchor: CorrectionAnchor,
            val replacement: String,
        )

        /**
         * floor 映射歧义回查窗口（chars）。变长规则（插入/删除/变长替换）使锚点
         * 区间的 floor 端点存在 ±(累计长度差) 的边界歧义；窗口内按 findText 精确
         * 匹配就近定位，内容校验始终严格。
         */
        private const val CORRECTION_LOCATE_WINDOW_CHARS = 16

        /**
         * 顺序叠加锚定纠错（纯函数）。
         *
         * 每条纠错都基于**当前累积态**（text + maps）判定与应用：
         * - source 区间按基址局部化，越界/空区间跳过；
         * - 区间经累积映射换算到当前 display（floor 语义）：floor 端点文本逐字等于
         *   findText 时直接在该区间拼接；
         * - floor 未命中（先前的变长规则使插入/删除边界产生 floor 歧义）时，在
         *   [CORRECTION_LOCATE_WINDOW_CHARS] 有界窗口内按 findText **精确匹配**、
         *   取距 floor 位置最近的出现——内容校验始终严格，窗口只消除映射边界歧义；
         * - 窗口内也无精确匹配（文件变更/已被覆盖/内容漂移）则诚实跳过，绝不近似替换。
         */
        private fun overlayAnchored(
            sourceText: String,
            baseDisplay: String,
            baseMap: TextOffsetMap,
            baseHitCount: Int,
            corrections: List<CorrectionSplice>,
            scopeSourceBase: Int,
            profileKey: String,
        ): ReplaceProjection {
            val maps = ArrayList<TextOffsetMap>(corrections.size + 1)
            maps += baseMap
            var currentDisplay = baseDisplay
            var applied = 0
            val sourceLength = sourceText.length
            for (splice in corrections) {
                val anchor = splice.anchor
                val ls = anchor.sourceStart - scopeSourceBase
                val le = anchor.sourceEnd - scopeSourceBase
                if (ls < 0 || le > sourceLength || ls >= le) continue
                val current = ChainedTextOffsetMap(maps)
                val d0 = current.toDisplay(ls)
                val d1 = current.toDisplay(le)
                val findText = anchor.findText
                var target = -1
                if (d1 > d0 &&
                    d1 <= currentDisplay.length &&
                    currentDisplay.substring(d0, d1) == findText
                ) {
                    target = d0
                } else {
                    // floor 歧义回查：在有界窗口内找最近的一次精确出现
                    val searchStart = (d0 - CORRECTION_LOCATE_WINDOW_CHARS).coerceAtLeast(0)
                    val searchEnd = (d1 + CORRECTION_LOCATE_WINDOW_CHARS).coerceAtMost(currentDisplay.length)
                    var idx = currentDisplay.indexOf(findText, searchStart)
                    var bestDist = Int.MAX_VALUE
                    while (idx >= 0 && idx + findText.length <= searchEnd) {
                        val dist = kotlin.math.abs(idx - d0)
                        if (dist < bestDist) {
                            bestDist = dist
                            target = idx
                        }
                        idx = currentDisplay.indexOf(findText, idx + 1)
                    }
                }
                if (target < 0) continue
                val dEnd = target + findText.length
                val replacement = splice.replacement
                val newDisplay = buildString(currentDisplay.length + replacement.length) {
                    append(currentDisplay, 0, target)
                    append(replacement)
                    append(currentDisplay, dEnd, currentDisplay.length)
                }
                // 拼接段映射：currentDisplay(累积链 display 空间) → newDisplay。
                // 控制点与 [RuleEngine.applySingle] 同构（对齐前缀对角 + 尾部跳变），
                // 保证删除坍缩 / 插入平铺 / 等长替换的 floor 语义与既有映射一致。
                val removed = dEnd - target
                val aligned = minOf(replacement.length, removed)
                val points = ArrayList<Pair<Int, Int>>(3)
                points += target to target
                if (replacement.isNotEmpty()) {
                    points += (target + aligned) to (target + aligned)
                }
                if (replacement.length != removed) {
                    points += (target + replacement.length) to dEnd
                }
                maps += TextOffsetMap.of(
                    points = points,
                    displayLength = newDisplay.length,
                    sourceLength = currentDisplay.length,
                )
                currentDisplay = newDisplay
                applied++
            }
            return ReplaceProjection(
                sourceText = sourceText,
                displayText = currentDisplay,
                offsetMap = ChainedTextOffsetMap(maps),
                hitCount = baseHitCount + applied,
                profileKey = profileKey,
            )
        }
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
