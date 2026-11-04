package com.creationreadingassistant.ui.screen.reader

/**
 * SE4「书内高亮/笔记跳转」的分派决策（CRA-MD-ANNOTATION-RETURN-FIX-v1）。
 *
 * 缺陷（journey v2 J3 实测 + 调用链定位）：原 effect 的分派只覆盖 `epubBook != null`
 * 与 plain（TXT）两支；Markdown 书的 `plainContent` 为空（ReaderScreen 对 Markdown
 * 模式即传空串），两支皆不命中——`pendingHighlightId` 被静默清除、无位移、无提示。
 *
 * 修复：分派决策提取为纯函数 [annotationNavigationPlan]，SE4 effect 实际 `when` 消费，
 * Markdown 动作面由 [applyMarkdownAnnotationPlan] 执行（两者都被生产代码调用，非平行镜像）：
 * - Markdown + 可解码 locator → [AnnotationNavigationPlan.MarkdownSourceNavigationDeferred]：
 *   移交 SE3 canonical 定位通道（`pendingSourceLocatorJsonState`）——该通道具备既有
 *   **跨章延迟机制**（goToChapter 后由 requestedSourceNavigationChapterState 等待目标章
 *   加载，完成后按带书/章身份的一次性 focus 应用渲染单元偏移；locator 原文即待定位
 *   偏移的保留载体，绝不丢失）；SE3 内部走 canonical 全局偏移（preferGlobalOffset=true）
 *   并按既有合同兼容 legacy chapter/offset 形态；
 * - Markdown 无 locator 有进度 → [AnnotationNavigationPlan.MarkdownProgressJump]：按
 *   `totalChars` 线性换算 canonical 全局偏移，走既有 `jumpToMarkdownOffset`（同章精确；
 *   跨章沿用其保守语义 = 切章，与 ReaderActions 既有行为一致，不伪造偏移重放）；
 * - Markdown 两者皆无 → [AnnotationNavigationPlan.MarkdownFailed]：明确反馈，禁止静默清除；
 * - EPUB / plain：既有内部逻辑逐字保留（effect 在本模块外处理，[applyMarkdownAnnotationPlan]
 *   对两分支返回未消费、零回调——守卫既有 TXT/EPUB 路由）；
 * - 无任何可用通道（原退化形态）→ [AnnotationNavigationPlan.SilentClear]：维持原行为。
 */
internal sealed interface AnnotationNavigationPlan {
    /** EPUB：沿用既有 locator 锚点 / 章节标题+进度兜底内部逻辑 */
    data object EpubBranch : AnnotationNavigationPlan

    /** TXT：沿用既有 plainContent / txtStreamingDocument 内部逻辑 */
    data object PlainBranch : AnnotationNavigationPlan

    /** Markdown + 可解码 locator：移交 SE3 canonical 章节定位通道（跨章延迟、偏移保留） */
    data class MarkdownSourceNavigationDeferred(val locatorJson: String) : AnnotationNavigationPlan

    /** Markdown 无 locator 的进度兜底：canonical 全局偏移 */
    data class MarkdownProgressJump(val globalOffset: Int) : AnnotationNavigationPlan

    /** Markdown 完全无位置信息：明确失败反馈，不得静默清除 */
    data class MarkdownFailed(val message: String) : AnnotationNavigationPlan

    /** 无任一可用通道：维持原行为（清除请求，无反馈） */
    data object SilentClear : AnnotationNavigationPlan
}

internal fun annotationNavigationPlan(
    epubBookPresent: Boolean,
    markdownDocumentPresent: Boolean,
    plainUsable: Boolean,
    decodedLocatorPresent: Boolean,
    locatorJson: String?,
    targetProgress: Float?,
    markdownTotalChars: Int,
): AnnotationNavigationPlan = when {
    // 与既有分派优先级一致：EPUB 最先，其次 Markdown（本轮新增支），再次 plain。
    epubBookPresent -> AnnotationNavigationPlan.EpubBranch
    markdownDocumentPresent -> when {
        decodedLocatorPresent && !locatorJson.isNullOrBlank() ->
            AnnotationNavigationPlan.MarkdownSourceNavigationDeferred(locatorJson)
        targetProgress != null && markdownTotalChars > 0 ->
            AnnotationNavigationPlan.MarkdownProgressJump(offsetFromProgress(targetProgress, markdownTotalChars))
        else -> AnnotationNavigationPlan.MarkdownFailed("无法定位该阅读笔记的正文位置")
    }
    plainUsable -> AnnotationNavigationPlan.PlainBranch
    else -> AnnotationNavigationPlan.SilentClear
}

/**
 * 消费 [plan] 的 Markdown 动作面（SE4 effect 调用）：
 * - `EpubBranch` / `PlainBranch` → 返回 false（未消费）：effect 走各自既有内部逻辑，
 *   零回调（TXT/EPUB 路由守卫的机器可检查形态）；
 * - `MarkdownSourceNavigationDeferred` → [handOffToSourceNavigation]（移交 SE3，偏移
 *   由 locator 原文承载，等待目标章加载后应用）；
 * - `MarkdownProgressJump` → [markdownJump]（canonical 全局偏移，经生产 jumpToMarkdownOffset）；
 * - `MarkdownFailed` → [notice]（明确反馈）；
 * - `SilentClear` → 无任何回调（维持原「静默清除」行为）。
 * 返回 true 表示计划已被本函数消费（effect 随后清除 pending 请求）。
 */
internal fun applyMarkdownAnnotationPlan(
    plan: AnnotationNavigationPlan,
    handOffToSourceNavigation: (String) -> Unit,
    markdownJump: (Int) -> Unit,
    plainJump: (Int) -> Unit,
    notice: (String) -> Unit,
): Boolean = when (plan) {
    AnnotationNavigationPlan.EpubBranch,
    AnnotationNavigationPlan.PlainBranch,
    -> false

    is AnnotationNavigationPlan.MarkdownSourceNavigationDeferred -> {
        handOffToSourceNavigation(plan.locatorJson)
        true
    }

    is AnnotationNavigationPlan.MarkdownProgressJump -> {
        markdownJump(plan.globalOffset)
        true
    }

    is AnnotationNavigationPlan.MarkdownFailed -> {
        notice(plan.message)
        true
    }

    AnnotationNavigationPlan.SilentClear -> true
}

/** 进度 → 全书偏移换算（与 TXT 兜底同一合同：percent 钳 0..100，线性换算，结果钳界）。 */
internal fun offsetFromProgress(progressPercent: Float, totalChars: Int): Int =
    (progressPercent.coerceIn(0f, 100f) / 100f * totalChars).toInt().coerceIn(0, totalChars.coerceAtLeast(0))
