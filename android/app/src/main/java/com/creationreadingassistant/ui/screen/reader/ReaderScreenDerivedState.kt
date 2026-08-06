package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.ReadingUnitBuilder
import com.creationreadingassistant.feature.reader.doc.ReadingUnitCache
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex

/**
 * Phase 7 结构拆分：[rememberReaderDerivedState] 的返回值。
 *
 * 仅暴露下游 [com.creationreadingassistant.ui.screen.ReaderScreen] 仍需消费的纯派生值
 * （chapterStartOffsets / chapterTitles / readingUnits / unitCache）。
 * `plainChunks` 为兼容遗留字段，当前未消费，保留在原位不迁移。
 */
internal data class ReaderDerivedState(
    val chapterStartOffsets: List<Int>,
    val chapterTitles: List<String>,
    val readingUnits: List<ReadingUnit>,
    val unitCache: ReadingUnitCache,
)

/**
 * Phase 7 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 抽出的「纯计算派生状态」
 * 逻辑（原 L365-402 的 chapterStartOffsets / chapterTitles / readingUnits / unitCache 块）。
 *
 * 包含：chapterStartOffsets / chapterTitles（val 派生）、readingUnits（remember 计算 +
 * 同步到 PlainTextDocument 的 LaunchedEffect）、unitCache（remember 构造）。
 *
 * ## 行为保真
 *
 * 每个 `remember` / `LaunchedEffect` 逐字搬运自 ReaderScreen，不改任何 key 与参数。本函数是
 * ReaderScreen 组合树内的子组合，effect 组合位置与原文一致，生命周期完全等价。
 *
 * ## 注意
 *
 * `txtStreamingDocument?.readingUnits = readingUnits` 的写入发生在 LaunchedEffect 内（首帧后），
 * 与原位写法语义一致；readingUnits 的 remember key（plainContent / txtStreamingDocument /
 * txtStreamingFileIndex）保持不变。
 */
@Suppress("LongParameterList")
@Composable
internal fun rememberReaderDerivedState(
    bookIndex: BookIndex?,
    markdownDocument: MarkdownDocument?,
    txtStreamingDocument: PlainTextDocument?,
    plainContent: String,
    txtStreamingFileIndex: TxtFileIndex?,
): ReaderDerivedState {
    // 书内搜索：EPUB 不再常驻全本文本（会 OOM），改为搜索时按需逐章流式抽取（见 computeEpubSearch）；
    // 这里只暴露各章偏移与标题，供跳章 / 命中映射使用。
    val chapterStartOffsets = bookIndex?.chapterStartOffsets
        ?: markdownDocument?.chapters?.map { it.startOffset }
        ?: txtStreamingDocument?.chapters?.map { it.startOffset }
        ?: emptyList()
    val chapterTitles = bookIndex?.chapterTitles
        ?: markdownDocument?.chapters?.map { it.title }
        ?: txtStreamingDocument?.chapters?.map { it.title }
        ?: emptyList()
    // 读取单元：惰性加载的元数据列表，不持有文本
    val readingUnits: List<ReadingUnit> = remember(plainContent, txtStreamingDocument, txtStreamingFileIndex) {
        when {
            txtStreamingDocument != null -> {
                ReadingUnitBuilder.buildUnits(txtStreamingDocument.chapters, txtStreamingFileIndex)
            }
            plainContent.isNotEmpty() -> {
                // 小文件：复用现有 chunkPlainText 的结果
                chunkPlainText(plainContent).mapIndexed { i, chunk ->
                    ReadingUnit(
                        unitIndex = i,
                        chapterIndex = 0,
                        title = "全文",
                        charStart = chunk.startOffset,
                        charCount = chunk.text.length,
                    )
                }
            }
            else -> emptyList()
        }
    }
    // 将 readingUnits 同步到 PlainTextDocument，供 unitIndexForOffset 等方法使用
    LaunchedEffect(txtStreamingDocument, readingUnits) {
        txtStreamingDocument?.readingUnits = readingUnits
    }
    // LRU 缓存：流式模式下缓存已解码的 ReadingUnit 文本（最多 5 个）
    val unitCache = remember(txtStreamingDocument) {
        ReadingUnitCache()
    }

    return ReaderDerivedState(
        chapterStartOffsets = chapterStartOffsets,
        chapterTitles = chapterTitles,
        readingUnits = readingUnits,
        unitCache = unitCache,
    )
}
