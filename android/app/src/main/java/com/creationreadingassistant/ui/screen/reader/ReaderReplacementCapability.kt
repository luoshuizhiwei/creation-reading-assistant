package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.pager.PagedReplacementAvailability

/** 当前阅读路径能否安全管理并应用正文替换规则。 */
internal sealed interface ReaderReplacementCapability {
    /**
     * 「替换净化」tab 可用（规则可查看、增删改）；[bodyNotice] 仅描述正文投影的降级状态。
     *
     * 正文是否真的走精确投影由 [PagedReplacementAvailability] 区分。它与启动 Snackbar
     * 使用同一份 availability 解释，避免 Snackbar 已消失后规则页又让用户误以为正文一定已被替换。
     */
    data class Available(val bodyNotice: String? = null) : ReaderReplacementCapability

    data class Unavailable(val message: String) : ReaderReplacementCapability
}

/**
 * 直接消费 [PagedReplacementAvailability]（由 PagerEngineState 已根据真实 source 能力裁决），
 * 不再自行猜测 isTxt / pagerEngineOn 的组合。RulesSheet 据此显式展示文案或启用 UI。
 */
internal fun readerReplacementCapability(
    availability: PagedReplacementAvailability,
): ReaderReplacementCapability = when (availability) {
    PagedReplacementAvailability.APPLIED -> ReaderReplacementCapability.Available()
    // source 已经具备精确投影能力，只是当前还没有规则；必须保留替换 tab，
    // 否则 UI 会提示“可新增”却把唯一的新增入口隐藏掉。
    PagedReplacementAvailability.NO_EFFECTIVE_RULES -> ReaderReplacementCapability.Available()
    // 降级作用域：正文（部分或全部）保留原文，但规则本身仍可查看与编辑——
    // 隐藏 tab 会让用户以为规则丢了。「有没有替换到正文」由启动提示明确说明。
    PagedReplacementAvailability.PARTIALLY_APPLIED -> ReaderReplacementCapability.Available(
        bodyNotice = readerReplacementAvailabilityNotice(availability),
    )
    PagedReplacementAvailability.ALL_SCOPES_OVERSIZED -> ReaderReplacementCapability.Available(
        bodyNotice = readerReplacementAvailabilityNotice(availability),
    )
    // EPUB 装配期未能验证全部候选章：规则仍可管理，正文逐章载入时精确裁决。
    PagedReplacementAvailability.UNVERIFIED_CHAPTER_LENGTHS -> ReaderReplacementCapability.Available(
        bodyNotice = readerReplacementAvailabilityNotice(availability),
    )
    PagedReplacementAvailability.PAGER_ENGINE_DISABLED -> ReaderReplacementCapability.Unavailable(
        "当前阅读模式未启用新分页引擎，正文替换净化仅在可精确投影的翻页正文中生效，正文将保留原文。",
    )
    PagedReplacementAvailability.ESTIMATED_COORDINATES -> ReaderReplacementCapability.Unavailable(
        "当前文档使用估算章节坐标，暂不支持正文替换净化，正文将保留原文。",
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
private fun readerReplacementAvailabilityNotice(
    availability: PagedReplacementAvailability,
): String? = when (availability) {
    // APPLIED 表示当前实际渲染路径已经使用精确投影；不应根据是否分页引擎反向否定它。
    PagedReplacementAvailability.APPLIED -> null
    PagedReplacementAvailability.ALL_SCOPES_OVERSIZED ->
        "本书正文没有可投影的作用域（整书超出替换净化可处理的长度上限），正文已保留原文；替换规则仍可继续管理，但不会对正文生效。"
    PagedReplacementAvailability.PARTIALLY_APPLIED ->
        "部分章节超出替换净化可处理的长度上限，这些章节已保留原文，其余章节按规则替换。"
    PagedReplacementAvailability.UNVERIFIED_CHAPTER_LENGTHS ->
        "本书部分章节较长，是否替换需等载入对应章节后确认；确认超限的章节将保留原文，其余章节按规则替换。"
    PagedReplacementAvailability.ESTIMATED_COORDINATES ->
        "当前文档格式暂不支持正文替换净化，正文已保留原文。"
    PagedReplacementAvailability.NON_SOURCE_COORDINATES ->
        "当前 Markdown 正文尚未形成完整 source 定位契约，正文已保留原文。"
    PagedReplacementAvailability.INCOMPLETE_SCOPE ->
        "当前章节视图暂不支持正文替换净化，正文已保留原文。"
    else -> null
}

internal fun readerReplacementStartupNotice(
    availability: PagedReplacementAvailability,
    @Suppress("UNUSED_PARAMETER") pagerEngineOn: Boolean,
): String? = readerReplacementAvailabilityNotice(availability)

/** 规则管理页只在「替换净化」tab 内持续展示可管理但正文降级的说明。 */
internal fun replacementRulesTabBodyNotice(
    capability: ReaderReplacementCapability,
): String? = (capability as? ReaderReplacementCapability.Available)?.bodyNotice

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

// 已删除的历史兼容重载：`readerReplacementCapability(isTxt, hasStreamingDocument, readerMode,
// pagerEngineOn, replaceProjectionScopeIsComplete)`。
// 它按 UI 侧标志（isTxt / readerMode / 引擎开关）**反推**能力，且只能表达 4 种结果，
// 无法表达 SOURCE_UNAVAILABLE / NON_SOURCE_COORDINATES / PARTIALLY_APPLIED /
// ALL_SCOPES_OVERSIZED / OVERSIZED_CURRENT_CHAPTER / UNVERIFIED_CHAPTER_LENGTHS，
// 正是「只因开关打开就宣称可替换」的来源。生产路径一律走单参数
// [readerReplacementCapability]（消费真实 [PagedReplacementAvailability]）。
