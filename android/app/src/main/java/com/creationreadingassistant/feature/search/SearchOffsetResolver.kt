package com.creationreadingassistant.feature.search

import com.creationreadingassistant.feature.reader.rules.ReplaceProjection
import com.creationreadingassistant.feature.reader.rules.ReplaceRule

/**
 * 命中 → 全书原文偏移的**纯决策**（零 Android / Room / 磁盘依赖，可直接 JVM 单测）。
 *
 * 为什么单独拆出来：[com.creationreadingassistant.data.repository.SearchIndexRepository.resolveLegacyOffset]
 * 的取数（book / 预览串 / 命中所在章原文 / 生效替换规则）依赖 Room 与磁盘，无法 JVM 直测；
 * 但它内部的**坐标判定**一旦算错**不会崩溃**，只会让用户静默跳到错位置（S1.4/S1.6 反复
 * 强调的「跳错位置不会崩溃」风险）。把分支抽成纯函数后，逐条断言可钉死这份契约。
 *
 * 口径（勿改，与阅读器批注/书签一致）：
 *  - 预览命中（索引章号 0）的偏移**本身**就是全书偏移（preview 即正文前 2 万字），不再加章起始；
 *  - 逐章命中的偏移是**章内**偏移，需叠加全书原文起始偏移；
 *  - 显示文（[SearchTextBasis.DISPLAY]）命中先对**同一段原文**重放同一组生效规则，
 *    用 `offsetMap.toSource` 反查回原文章内偏移，再叠加章起始；
 *  - 任何一步取不到 / 规则正则失效 → 返回 null（调用方降级为只打开书，绝不伪造偏移）。
 */
internal object SearchOffsetResolver {

    /**
     * @param charOffset 命中在其所在文本空间的偏移（预览命中=全书偏移；逐章命中=章内偏移）
     * @param isPreviewHit 索引章号为 0 的预览/元数据降级命中
     * @param isTxtLike 该书是否 TXT/MD（EPUB 预览不参与精确定位）
     * @param isDisplayBasis 命中是否来自替换显示文通道
     * @param readerChapterIndex 阅读器章序号（逐章命中必填；预览命中忽略）
     * @param sourceText 命中所在**原文**文本（预览=预览串；逐章=该章原文）；取不到传 null
     * @param chapterStart 命中所在章的**全书原文起始偏移**（逐章命中用；预览命中传 0）
     * @param effectiveRules 该书生效替换规则（已过滤 enabled；原文基准传空即可）。
     *   R4：生效列表可能含**锚定单处纠错**（[ReplaceRule.anchor] 非空），与索引侧
     *   同一投影口径回放；纠错应用条件依赖章节基址，与 [chapterStart] 必须同源。
     * @return 全书原文偏移；null = 无法精确换算（调用方降级为只打开书）
     */
    fun resolve(
        charOffset: Int?,
        isPreviewHit: Boolean,
        isTxtLike: Boolean,
        isDisplayBasis: Boolean,
        readerChapterIndex: Int?,
        sourceText: String?,
        chapterStart: Int,
        effectiveRules: List<ReplaceRule>,
    ): Int? {
        val offset = charOffset ?: return null
        if (offset < 0) return null

        if (isPreviewHit) {
            // EPUB 导入不写 reader_preview；非 TXT/MD 一律不精确到达。
            if (!isTxtLike) return null
            val text = sourceText ?: return null
            return if (isDisplayBasis) mapToSource(text, offset, effectiveRules, chapterStart = 0) else offset
        }

        val chapter = readerChapterIndex ?: return null
        if (chapter < 0) return null
        val body = sourceText ?: return null
        val local = if (isDisplayBasis) {
            mapToSource(body, offset, effectiveRules, chapterStart) ?: return null
        } else {
            offset
        }
        return chapterStart + local
    }

    /**
     * 显示文章内偏移 → 原文章内偏移（重放生效规则与纠错）。规则为空按 identity；
     * 代理失效（正则失效 / 纠错内容漂移导致映射不闭合）→ null。
     *
     * [chapterStart] 是该段文本的全书 source 起点：与索引侧 [IndexUnit.chapterSourceStart]
     * 同口径，单处纠错锚点据此局部化（与 resolveLegacyOffset 的取数注释一致）。
     */
    private fun mapToSource(
        text: String,
        displayOffset: Int,
        rules: List<ReplaceRule>,
        chapterStart: Int,
    ): Int? = runCatching {
        if (rules.isEmpty()) {
            displayOffset
        } else {
            // resolve 不消费 profile key，bookId 传空串；key 仅用于缓存身份且禁止入日志。
            ReplaceProjection.projectScoped(
                sourceText = text,
                rules = rules,
                bookId = "",
                scopeSourceBase = chapterStart,
            ).offsetMap.toSource(displayOffset)
        }
    }.getOrNull()
}
