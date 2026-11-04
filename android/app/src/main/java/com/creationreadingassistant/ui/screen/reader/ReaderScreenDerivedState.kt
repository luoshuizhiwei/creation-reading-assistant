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
 * 章节元数据：各章起始偏移 + 各章标题。
 *
 * 两者必须出自**同一个章节源**：偏移用来把全书字符位置反推成章号（进度落库、
 * 跳章、TTS 章基址），标题用来渲染同一批章号。分头各选一次源，一旦某条回退链
 * 走岔，就会出现「目录第 5 章、进度卡第 2 章」这类无法自证一致的状态。
 */
internal data class ChapterMetadata(
    val startOffsets: List<Int>,
    val titles: List<String>,
)

private fun chapterMetadataOf(chapters: List<DocChapter>): ChapterMetadata =
    ChapterMetadata(
        startOffsets = chapters.map { it.startOffset },
        titles = chapters.map { it.title },
    )

/**
 * 章节元数据的源选择。优先级即语义，勿调换：
 *
 * 1. [bookIndex] —— EPUB 全书索引（估算量纲，与历史 locator 同源）；
 * 2. [markdownChapters] —— Markdown 文档的规范文本章表；
 * 3. [txtStreamingChapters] —— 流式（大文件）TXT 章表；
 * 4. [smallTxtChapters] —— 小 TXT 滚动路径的章节识别结果（`plainContent` 字符量纲）；
 * 5. 空 —— 以上源都不在场（章节识别失败的小 TXT 如实降级为空表）。
 *
 * 前 3 档按**源是否在场**裁决（`null` 才继续下探），不是按列表是否为空：
 * 文档在场但章表为空是解析器的诚实结论，回退到别的源会掺进不同量纲的偏移。
 *
 * 第 4 档是本文件的修复点。此前回退链止于 `emptyList()`，而小 TXT 的章表要等到
 * [rememberReaderDerivedState] 里的 `txtChapters` 才算出来 —— 于是进度保存用空表
 * 调 [com.creationreadingassistant.feature.reader.locator.EpubLocatorMapping.toChapterOffset]，
 * 该函数对空表恒返回 `ci = 0`，书架进度卡因此恒显示「第 1 章」。
 * 正文侧的 `readingUnits` 早已按真实逻辑章切块，量纲与 `smallTxtChapters` 一致，
 * 补上这一档不会引入新的坐标系。
 */
internal fun selectChapterMetadata(
    bookIndex: BookIndex?,
    markdownChapters: List<DocChapter>?,
    txtStreamingChapters: List<DocChapter>?,
    smallTxtChapters: List<DocChapter>,
): ChapterMetadata = when {
    bookIndex != null -> ChapterMetadata(bookIndex.chapterStartOffsets, bookIndex.chapterTitles)
    markdownChapters != null -> chapterMetadataOf(markdownChapters)
    txtStreamingChapters != null -> chapterMetadataOf(txtStreamingChapters)
    else -> chapterMetadataOf(smallTxtChapters)
}

/**
 * Phase 7 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 抽出的「纯计算派生状态」
 * 逻辑（原 L365-402 的 chapterStartOffsets / chapterTitles / readingUnits 块）。
 *
 * 包含：chapterStartOffsets / chapterTitles（由 [selectChapterMetadata] 一次性选定同源的
 * 偏移与标题）、readingUnits（身份绑定裁决，只读，不再写回 document）。
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
    // 章节元数据（跳章 / 命中映射 / 进度章号反推 / 目录标题；EPUB 侧只暴露偏移与标题，
    // 全本文本改为搜索时逐章流式抽取，见 computeEpubSearch）。必须在 txtChapters 之后选取
    // —— 小 TXT 的章表是那里才算出来的第四档源。
    // remember 不是为了省算力：chapterStartOffsets 同时是 LaunchedEffect / remember 的 key
    // （ReaderProgressEffects 的防抖落库、ReaderEyeCareFocusState），每次重组现算会给出新
    // 列表实例。三份 document 的 chapters 都是实例内 val，按实例身份缓存不会读到旧章表。
    val chapterMetadata = remember(bookIndex, markdownDocument, txtStreamingDocument, txtChapters) {
        selectChapterMetadata(
            bookIndex = bookIndex,
            markdownChapters = markdownDocument?.chapters,
            txtStreamingChapters = txtStreamingDocument?.chapters,
            smallTxtChapters = txtChapters,
        )
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
        chapterStartOffsets = chapterMetadata.startOffsets,
        chapterTitles = chapterMetadata.titles,
        readingUnits = readingUnits,
        txtChapters = txtChapters,
    )
}
