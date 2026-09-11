package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.log.AppLog
import com.creationreadingassistant.feature.reader.doc.DocChapter
import com.creationreadingassistant.feature.reader.doc.MarkdownDocument
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.feature.reader.doc.TxtTocProfile
import com.creationreadingassistant.ui.viewmodel.ReaderLoadedContent

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
    /** TXT 章节识别结果（R1-S1 上提）：滚动正文与分页/目录共用同一份。 */
    val txtChapters: List<DocChapter>,
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
    epubBook: EpubBook?,
    tocProfile: TxtTocProfile,
    textContent: ReaderLoadedContent.Text?,
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
    // 不在 composition 期间写回 document；小文件路径按章节对齐切块派生
    // （remember key 保持原样：plainContent / txtStreamingDocument / txtStreamingFileIndex，
    // 另加 txtChapters：R1-S1 章对齐切块的身份输入）。
    // TXT 章节识别（自 ReaderPagerEngineState 上提，逻辑逐字保留）：此前 TXT 完全没有
    // 章节概念，目录永远是「暂未识别到目录」。P0 优化：优先使用 ReaderDocumentLoader
    // 在 IO 线程预检测的结果，避免阻塞主线程。P1-A：身份改为 TxtTocProfile.key ——
    // 规则集合 / 顺序 / 内容变化后 key 变化，旧预检测不匹配即回退同步检测（极少触发）。
    // 上提原因：小文件滚动 readingUnits 必须按真实逻辑章对齐（否则整本书坍缩为单一
    // 投影作用域，超限即全书降级原文），而章节边界是切块的输入，必须先于 units 就绪。
    val txtChapters = remember(plainContent, tocProfile.key, txtStreamingDocument, textContent, epubBook) {
        val streamDoc = txtStreamingDocument
        if (epubBook == null && streamDoc != null) {
            streamDoc.chapters
        } else if (epubBook == null && plainContent.isNotBlank()) {
            val preDetected = textContent?.preDetectedChapters
            val preRule = textContent?.preDetectedRuleId
            if (!preDetected.isNullOrEmpty() && preRule == tocProfile.key) {
                // 快速路径：使用 IO 线程预检测结果，不阻塞主线程
                preDetected
            } else {
                // Fallback：规则切换或预检测缺失，同步检测（极少触发）
                AppLog.debug("TxtPerfSubTrace", "TxtChapterDetect: fallback=true, key=${tocProfile.key}, preRule=$preRule")
                PlainTextDocument(plainContent, tocProfile).chapters
            }
        } else {
            emptyList()
        }
    }
    val externallyDerivedUnits = remember(plainContent, txtChapters, txtStreamingDocument, txtStreamingFileIndex) {
        if (txtStreamingDocument == null && plainContent.isNotEmpty()) {
            // 小文件：按真实逻辑章对齐切块（章节识别失败时回退全文切块，诚实降级）
            buildChapterAlignedPlainUnits(plainContent, txtChapters)
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
        txtChapters = txtChapters,
    )
}
