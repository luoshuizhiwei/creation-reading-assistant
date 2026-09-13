package com.creationreadingassistant.feature.reader.rules

/**
 * 替换净化的有界 source scope 投影 seam（slice 2）。
 *
 * 职责：调用方把「完整且 <= 上限的 source scope」（整本小 TXT 或按章节索引读出的
 * 完整章节）一次性交给本投影，精确复用 [ReplaceProjection.project]；返回
 * [BoundedReplaceResult.Exact]（display + 偏移映射 + 全书 source 起点）或显式 typed
 * 拒绝结果，绝不返回「部分成功」。
 *
 * 为什么这是可证明正确的 seam：
 * - 任意正则（包括跨 ReadingUnit / 段落边界、`\n`、`(?s)` 多行、变长 / 变短 / 删除）
 *   只对完整 scope 应用一次，天然无重无漏；调用方无需理解任何窗口切分细节。
 * - scope 超过 [maxSourceLength] 时整体拒绝：没有读取窗口外字符就永远无法知道是否
 *   存在超长匹配，因此任何「固定 overlap 窗口 + missedOverlongMatches 统计」都是
 *   misleading 的，本模块明确不做。
 *
 * 后续流式接线策略（调用方契约）：
 * 1. 调用方先按章节索引读取完整章节文本；章节长度 <= [maxSourceLength] 时才投影。
 *    跨段边界由完整章节一次投影保证，禁止按段落 / ReadingUnit 拆开分别投影再拼接。
 * 2. 超大章节、或当前无章节索引可用的 scope：必须向用户提示「该 scope 过大，暂不
 *    支持净化」，不得退化为固定 overlap 的近似结果。
 * 3. 未来若要支持超大 scope，只能引入显式 maxSpan 规则模型（规则声明最大匹配跨度，
 *    超限正则保存时拒绝），或由调用方提高上限；两者都不是本模块现有职责。
 *
 * 长度单位：UTF-16 code units，即 [String.length]（与正则 find 的索引单位一致），
 * 不是字节数。[DEFAULT_MAX_SOURCE_CHARS] 是保守默认，调用方可按章节规模覆盖。
 *
 * 隐私：所有拒绝结果只携带整数长度 / 上限；[BoundedReplaceResult.Exact.toString]
 * 不含正文、规则原文或 [ReplaceProfile.key]（key 同样禁止写入日志）。
 */
object BoundedReplaceProjector {

    /**
     * 保守默认上限：256K chars（UTF-16 code units）。
     *
     * 约等于 512KB 的 ASCII 文本；对 JVM 正则 + 组合偏移映射是保守的内存 / 时间预算，
     * 且不硬绑任何 UI。调用方可在 [project] 用 [maxSourceLength] 显式覆盖。
     */
    const val DEFAULT_MAX_SOURCE_CHARS: Int = 256 * 1024

    /**
     * 有界投影：scope 完整且 <= 上限时精确投影，否则显式拒绝。
     *
     * @param scopeSource 完整 source scope（整本小 TXT 或完整章节）；source 是持久化
     *   权威坐标，display 仅派生。
     * @param rules 生效 / 完整替换规则列表；语义与 [RuleEngine.applyReplace] 一致
     *   （按 position 升序、过滤 disabled），规则须先经
     *   [RuleEngine.validateReplaceRule] 校验，本模块不做保存前校验。
     * @param bookId 持久化书身份，参与 [ReplaceProfile.key]；禁止用空串代替。
     * @param scopeSourceBase 本 scope 在全书 source 中的起点（[Exact.scopeSourceBase]）；
     *   必须 >= 0，负值按调用方契约错误直接拒绝。
     * @param maxSourceLength 上限（chars）；<= 0 视为调用方配置错误，返回
     *   [BoundedReplaceResult.InvalidLimit]，不投影。
     * @return [BoundedReplaceResult.Exact]（scope <= 上限）、
     *   [BoundedReplaceResult.UnsupportedTooLarge]（scope 超限，不含正文 / 规则）或
     *   [BoundedReplaceResult.InvalidLimit]（maxSourceLength <= 0）。
     */
    fun project(
        scopeSource: String,
        rules: List<ReplaceRule>,
        bookId: String,
        scopeSourceBase: Int = 0,
        maxSourceLength: Int = DEFAULT_MAX_SOURCE_CHARS,
    ): BoundedReplaceResult {
        require(scopeSourceBase >= 0) {
            "scopeSourceBase 必须是全书 source 的非负起点，收到 $scopeSourceBase"
        }
        if (maxSourceLength <= 0) return BoundedReplaceResult.InvalidLimit(maxSourceLength)
        if (scopeSource.length > maxSourceLength) {
            // 先判上限再投影：超限 scope 不做任何（部分）替换。
            return BoundedReplaceResult.UnsupportedTooLarge(scopeSource.length, maxSourceLength)
        }
        val projection = ReplaceProjection.projectScoped(
            sourceText = scopeSource,
            rules = rules,
            bookId = bookId,
            scopeSourceBase = scopeSourceBase,
        )
        return BoundedReplaceResult.Exact(projection, scopeSourceBase)
    }
}

/**
 * 有界投影的 sealed 结果：成功（[Exact]）或两类显式拒绝（[UnsupportedTooLarge] /
 * [InvalidLimit]）。不存在「部分成功」形态。
 */
sealed interface BoundedReplaceResult {

    /**
     * scope 完整且 <= 上限：精确投影成功。
     *
     * 坐标约定：scope 内投影坐标均为局部；[scopeSourceBase] + 局部 source 偏移 =
     * 全书全局 source 偏移。display 只用于渲染 / 搜索，source raw 是权威。
     * 删除坍缩与插入平铺的 floor 语义继承自 [TextOffsetMap]（与 slice 1 一致）。
     */
    class Exact internal constructor(
        /** slice 1 的完整投影；其中 sourceText/displayText/offsetMap 均为 scope 局部。 */
        val projection: ReplaceProjection,
        /** 全书 source 起点；本 scope 局部 source 偏移 + 该值 = 全局 source 偏移。 */
        val scopeSourceBase: Int,
    ) : BoundedReplaceResult {

        /** scope 的 source 长度（UTF-16 chars）。 */
        val sourceLength: Int get() = projection.sourceText.length

        /** scope 的 display 长度（派生）。 */
        val displayLength: Int get() = projection.displayText.length

        /** 全部生效规则的总命中数。 */
        val hitCount: Int get() = projection.hitCount

        /** 规则执行身份（[ReplaceProfile.key] 透传）；禁止写入日志。 */
        val profileKey: String get() = projection.profileKey

        /**
         * 局部 display 偏移 → 全局 source 偏移。
         *
         * 局部偏移 clamp 到 [0, displayLength]（负值按 0、越界按全长），结果有界于
         * [scopeSourceBase, scopeSourceBase + sourceLength]。删除坍缩点按 floor 回到
         * 删除起点（可能小于名义 source 起点，这是 map 既有语义，不是越界）。
         */
        fun localDisplayToGlobalSource(localDisplayOffset: Int): Int {
            val local = localDisplayOffset.coerceIn(0, displayLength)
            return scopeSourceBase + projection.offsetMap.toSource(local)
        }

        /**
         * 全局 source 偏移 → 局部 display 偏移。
         *
         * 全局偏移 clamp 到 [scopeSourceBase, scopeSourceBase + sourceLength]，结果
         * 有界于 [0, displayLength]。被删除的 source 区间折叠到删除起点（floor）。
         */
        fun globalSourceToLocalDisplay(globalSourceOffset: Int): Int {
            val local = (globalSourceOffset - scopeSourceBase).coerceIn(0, sourceLength)
            return projection.offsetMap.toDisplay(local)
        }

        /** 只描述结果规模，不含正文、规则原文或 profileKey（隐私）。 */
        override fun toString(): String =
            "BoundedReplaceResult.Exact(" +
                "scopeSourceBase=$scopeSourceBase, sourceLength=$sourceLength, " +
                "displayLength=$displayLength, hitCount=$hitCount)"
    }

    /**
     * scope 超过 [maxSourceLength]：不做任何部分替换。
     *
     * 只携带实际长度与上限（均为 Int），不包含正文或规则内容，toString 同样安全；
     * 调用方 UI 可据此提示「scope 过大，暂不支持净化」。
     */
    data class UnsupportedTooLarge(
        /** 实际 source 长度（UTF-16 chars）。 */
        val actualSourceLength: Int,
        /** 本次投影使用的上限。 */
        val maxSourceLength: Int,
    ) : BoundedReplaceResult

    /**
     * maxSourceLength <= 0：调用方配置错误（typed invalid，不抛异常）。
     *
     * 与 [UnsupportedTooLarge] 一样只携带整数，不包含正文或规则内容。
     */
    data class InvalidLimit(
        /** 本次收到的非法上限。 */
        val maxSourceLength: Int,
    ) : BoundedReplaceResult
}
