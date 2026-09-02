package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.pager.PagedReplacementAvailability

/** 当前阅读路径能否安全管理并应用正文替换规则。 */
internal sealed interface ReaderReplacementCapability {
    data object Available : ReaderReplacementCapability

    data class Unavailable(val message: String) : ReaderReplacementCapability
}

/**
 * 直接消费 [PagedReplacementAvailability]（由 PagerEngineState 已根据真实 source 能力裁决），
 * 不再自行猜测 isTxt / pagerEngineOn 的组合。RulesSheet 据此显式展示文案或启用 UI。
 */
internal fun readerReplacementCapability(
    availability: PagedReplacementAvailability,
): ReaderReplacementCapability = when (availability) {
    PagedReplacementAvailability.APPLIED -> ReaderReplacementCapability.Available
    // source 已经具备精确投影能力，只是当前还没有规则；必须保留替换 tab，
    // 否则 UI 会提示“可新增”却把唯一的新增入口隐藏掉。
    PagedReplacementAvailability.NO_EFFECTIVE_RULES -> ReaderReplacementCapability.Available
    PagedReplacementAvailability.PAGER_ENGINE_DISABLED -> ReaderReplacementCapability.Unavailable(
        "当前阅读模式未启用新分页引擎，正文替换净化仅在可精确投影的翻页正文中生效，正文将保留原文。",
    )
    PagedReplacementAvailability.ESTIMATED_COORDINATES -> ReaderReplacementCapability.Unavailable(
        "当前文档格式（EPUB/估算坐标）暂不支持正文替换净化，正文将保留原文。",
    )
    PagedReplacementAvailability.NON_SOURCE_COORDINATES -> ReaderReplacementCapability.Unavailable(
        "当前 Markdown 正文使用渲染坐标，尚未形成完整 source 定位契约，正文将保留原文。",
    )
    PagedReplacementAvailability.INCOMPLETE_SCOPE -> ReaderReplacementCapability.Unavailable(
        "当前章节视图无法提供完整可投影作用域，正文将保留原文。",
    )
    PagedReplacementAvailability.OVERSIZED_CURRENT_CHAPTER -> ReaderReplacementCapability.Unavailable(
        "当前章节过大，已保留原文，暂不执行替换净化。",
    )
    PagedReplacementAvailability.SOURCE_UNAVAILABLE -> ReaderReplacementCapability.Unavailable(
        "正文 source 尚未构建或章节为空，暂不支持替换净化。",
    )
}

/**
 * 阅读器首次进入时的轻量能力提示。
 *
 * 这里必须按真实 capability 描述，不能把 INCOMPLETE_SCOPE 一律猜成“流式大文件”：
 * Markdown 等章文档也可能因为当前投影作用域不完整而落入该状态。
 */
internal fun readerReplacementStartupNotice(
    availability: PagedReplacementAvailability,
    @Suppress("UNUSED_PARAMETER") pagerEngineOn: Boolean,
): String? = when (availability) {
    // APPLIED 表示当前实际渲染路径已经使用精确投影；不应根据是否分页引擎反向否定它。
    PagedReplacementAvailability.APPLIED -> null
    PagedReplacementAvailability.ESTIMATED_COORDINATES ->
        "当前文档格式暂不支持正文替换净化，正文已保留原文。"
    PagedReplacementAvailability.NON_SOURCE_COORDINATES ->
        "当前 Markdown 正文尚未形成完整 source 定位契约，正文已保留原文。"
    PagedReplacementAvailability.INCOMPLETE_SCOPE ->
        "当前章节视图暂不支持正文替换净化，正文已保留原文。"
    else -> null
}

/** 旋转会恢复 alreadyShown；同一阅读会话内不重复遮挡正文。 */
internal fun readerReplacementStartupNoticeIfNeeded(
    availability: PagedReplacementAvailability,
    pagerEngineOn: Boolean,
    alreadyShown: Boolean,
): String? = if (alreadyShown) {
    null
} else {
    readerReplacementStartupNotice(availability, pagerEngineOn)
}

/**
 * 兼容旧调用点（单元测试还没有 PagerEngineState 的场景）：
 * 显式把「source 尚不可用 / isTxt=false / pagerEngineOff」情形转成对应 availability，
 * 避免逐处 if/else 蔓延。真实生产路径一律使用单参数 availability 版本。
 */
internal fun readerReplacementCapability(
    isTxt: Boolean,
    hasStreamingDocument: Boolean,
    readerMode: String,
    pagerEngineOn: Boolean,
    replaceProjectionScopeIsComplete: Boolean = false,
): ReaderReplacementCapability {
    if (!isTxt) return readerReplacementCapability(PagedReplacementAvailability.ESTIMATED_COORDINATES)
    if (readerMode != "paged" || !pagerEngineOn) {
        return readerReplacementCapability(PagedReplacementAvailability.PAGER_ENGINE_DISABLED)
    }
    if (!replaceProjectionScopeIsComplete) return readerReplacementCapability(PagedReplacementAvailability.INCOMPLETE_SCOPE)
    return readerReplacementCapability(PagedReplacementAvailability.APPLIED)
}
