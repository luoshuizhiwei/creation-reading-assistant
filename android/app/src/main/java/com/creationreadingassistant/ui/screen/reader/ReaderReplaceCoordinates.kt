package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.pager.PagedChapterSource
import com.creationreadingassistant.feature.reader.pager.ProjectedChapterSource

/**
 * EPUB 替换净化接入后的 TTS 句偏移坐标换算。
 *
 * 契约（与分页引擎混合空间一致）：
 * - 朗读文本：投影活跃时是 display 文本（净化后），否则是 source 文本；
 * - 句偏移在播放文本空间（章内局部）；持久化（续读）与跟读高亮一律用 source 口径；
 * - 无投影（无规则/超大章/Markdown/旧 source）时全部恒等，行为与接入前逐字节一致。
 *
 * 所有换算经 [ProjectedChapterSource.projectionForChapter]（LRU 缓存内查表），
 * `scopeSourceBase` 即估算章基址，因此「局部↔全书」换算与分页引擎的
 * `chapterStartAbs + local` 既有公式同源。
 */

/** 播放文本（display）空间的全书 source 偏移区间；句高亮/跟读跳转用。 */
internal fun ttsSentenceGlobalSourceRange(
    pagedSource: PagedChapterSource?,
    chapterStartOffsets: List<Int>,
    chapterIndex: Int,
    localRange: Pair<Int, Int>,
): Pair<Int, Int> {
    val projection = (pagedSource as? ProjectedChapterSource)?.projectionForChapter(chapterIndex)
    return if (projection != null) {
        projection.localDisplayToGlobalSource(localRange.first) to
            projection.localDisplayToGlobalSource(localRange.second)
    } else {
        val base = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
        (base + localRange.first) to (base + localRange.second)
    }
}

/** 播放文本（display）章内局部偏移 → 持久化 source 章内局部偏移（续读保存用）。 */
internal fun ttsDisplayLocalToSourceLocal(
    pagedSource: PagedChapterSource?,
    chapterIndex: Int,
    displayLocal: Int,
): Int {
    val projection = (pagedSource as? ProjectedChapterSource)?.projectionForChapter(chapterIndex)
        ?: return displayLocal
    return projection.projection.offsetMap.toSource(displayLocal)
}

/** 持久化 source 章内局部偏移 → 播放文本（display）章内局部偏移（续读恢复用）。 */
internal fun ttsSourceLocalToDisplayLocal(
    pagedSource: PagedChapterSource?,
    chapterIndex: Int,
    sourceLocal: Int,
): Int {
    val projection = (pagedSource as? ProjectedChapterSource)?.projectionForChapter(chapterIndex)
        ?: return sourceLocal
    return projection.projection.offsetMap.toDisplay(sourceLocal)
}
