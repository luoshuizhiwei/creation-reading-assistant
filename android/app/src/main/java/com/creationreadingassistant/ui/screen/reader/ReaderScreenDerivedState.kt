package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex

/**
 * Phase 7 结构拆分：[rememberReaderDerivedState] 的返回值。
 *
 * 仅暴露下游 [com.creationreadingassistant.ui.screen.ReaderScreen] 仍需消费的纯派生值
 * （chapterStartOffsets / chapterTitles / readingUnits）。
 * `plainChunks` 为兼容遗留字段，当前未消费，保留在原位不迁移。
 */
internal data class ReaderDerivedState(
    val chapterStartOffsets: List<Int>,
    val chapterTitles: List<String>,
    val readingUnits: List<ReadingUnit>,
)

/**
 * Phase 7 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 抽出的「纯计算派生状态」
 * 逻辑（原 L365-402 的 chapterStartOffsets / chapterTitles / readingUnits 块）。
 *
 * 包含：chapterStartOffsets / chapterTitles（val 派生）、readingUnits（身份绑定裁决，
 * 只读，不再写回 document）。
 *
 * ## readingUnits 的单一真相
 *
 * 流式 TXT 的 units 由文档构造层（[PlainTextDocument.fromFileIndex]）在构造时构建，
 * 文档到达组合层即已就绪（首帧可用）。组合层通过 [ReadingUnitsResolver] 只读裁决：
 * - 文档在场 → 使用文档自有 units（同帧 TxtChapterSource / 搜索 / 滚动读到同一份）；
 * - 文档缺席（小文件 plainContent）→ 使用 chunkPlainText 派生的 units。
 *
 * 原实现（remember 计算块内 `txtStreamingDocument?.readingUnits = readingUnits`）在
 * composition 期间修改可变文档状态，已移除：首帧就绪不再依赖该写副作用，文档切换 /
 * units 替换由文档实例身份天然绑定，不会读旧快照。派生 remember 的 key（plainContent /
 * txtStreamingDocument / txtStreamingFileIndex）保持原样。
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
    // 读取单元：惰性加载的元数据列表，不持有文本。
    // 流式 TXT 的单一真相在文档构造层（fromFileIndex 构造时构建），组合层只读查询，
    // 不在 composition 期间写回 document；小文件路径由 chunkPlainText 派生
    // （remember key 保持原样：plainContent / txtStreamingDocument / txtStreamingFileIndex）。
    val externallyDerivedUnits = remember(plainContent, txtStreamingDocument, txtStreamingFileIndex) {
        if (txtStreamingDocument == null && plainContent.isNotEmpty()) {
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
        } else {
            emptyList()
        }
    }
    val readingUnits: List<ReadingUnit> = ReadingUnitsResolver.resolve(
        document = txtStreamingDocument,
        documentOwnedUnits = txtStreamingDocument?.readingUnits ?: emptyList(),
        externallyDerivedUnits = externallyDerivedUnits,
    )
    return ReaderDerivedState(
        chapterStartOffsets = chapterStartOffsets,
        chapterTitles = chapterTitles,
        readingUnits = readingUnits,
    )
}
