package com.creationreadingassistant.feature.reader.doc

import android.graphics.BitmapFactory
import com.creationreadingassistant.domain.model.EpubBlock
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.EpubParser

/**
 * EPUB 的 [ReaderDocument] 实现。
 *
 * 相比直接用 [EpubParser] 多做三件事：
 *
 * 1. **章节缓存**（[DocumentCache]）。此前 `EpubChapter.blocks` 是 property getter，
 *    每次访问都重开 ZipFile 全量重解析，翻页/搜索/TTS 会把同一章解压好几遍。
 * 2. **图片 intrinsic 尺寸**。排版层要知道图片多大才能决定占几行、是否整块下移到次页；
 *    在排版时才去读文件头等于把 IO 放进热路径。这里解析阶段一次性读出来（只读文件头，
 *    `inJustDecodeBounds` 不会把像素载入内存）。
 * 3. **统一偏移语义**，让搜索、目录、进度都走同一套。
 *
 * 已知局限：章节偏移仍沿用 `estimatedTextLength`（ZIP 解压后字节数），中文书会比真实
 * 字符数大约 3 倍。这是为了与既有的 `buildBookIndex` 保持逐值一致，不破坏历史定位数据。
 * 按 SIDECAR-ZH 的 P5，真实字符数会随阅读逐章回填。
 */
class EpubDocument(private val book: EpubBook) : ReaderDocument {

    private val cache = DocumentCache()

    // 偏移必须走 LegacyOffsetCodec，不能自己再累加一遍：旧公式每章多加 1（章间分隔位），
    // 漏掉它会让第 N 章之后的偏移全部少 N，历史高亮/笔记整体前移。
    private val estimatedLengths = book.chapters.map { it.estimatedTextLength }

    /**
     * P5 逐章实测回填：章节首次解析时记录真实字符数（与 EpubPageSource.chapterTextOf
     * 同一口径：Text 块按 "\n" 连接）。存储坐标系不变，仅供进度校准
     * （EpubProgressCalibration）使用；读得越多模型越准。
     */
    private val measuredRealChars = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    override val chapters: List<DocChapter> =
        LegacyOffsetCodec.chapterStartOffsets(estimatedLengths).mapIndexed { i, start ->
            DocChapter(
                index = i,
                title = book.chapters[i].title,
                startOffset = start,
                charCount = estimatedLengths[i].coerceAtLeast(1),
                charCountIsEstimated = true,
            )
        }

    override val totalChars: Int = LegacyOffsetCodec.totalChars(estimatedLengths)

    override fun blocks(chapterIndex: Int): List<DocBlock> {
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return emptyList()
        return cache.get(chapterIndex) { index ->
            EpubParser.loadChapterBlocks(
                chapter.cachedEpubPath,
                chapter.entryPath,
                chapter.chapterDir,
            ).map { it.toDocBlock() }
                .also { blocks ->
                    // 只在解析路径回填（缓存命中不走这里），O(章长) 一次
                    var chars = 0
                    var textBlocks = 0
                    blocks.forEach { block ->
                        if (block is DocBlock.Text) {
                            chars += block.text.length
                            textBlocks++
                        }
                    }
                    if (textBlocks > 0) chars += textBlocks - 1
                    measuredRealChars[index] = chars
                }
        }
    }

    /** 已实测章的真实字符数快照（章索引 → 真实字符数），供进度校准。 */
    fun measuredChapterCharsSnapshot(): Map<Int, Int> = measuredRealChars.toMap()

    /** 估算章长原始值（ZIP 条目字节数口径），供进度校准。 */
    fun estimatedChapterLengths(): List<Int> = estimatedLengths

    override fun close() = cache.clear()

    private fun EpubBlock.toDocBlock(): DocBlock = when (this) {
        is EpubBlock.Text -> DocBlock.Text(text, isHeading)
        is EpubBlock.Image -> {
            val (w, h) = readImageBounds(filePath)
            DocBlock.Image(filePath, w, h)
        }
    }

    /** 只读图片文件头拿宽高，不解码像素。失败返回 0×0，由排版层按占位处理。 */
    private fun readImageBounds(path: String): Pair<Int, Int> = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        (opts.outWidth.coerceAtLeast(0)) to (opts.outHeight.coerceAtLeast(0))
    } catch (_: Throwable) {
        0 to 0
    }
}
