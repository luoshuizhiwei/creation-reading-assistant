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

    override val chapters: List<DocChapter> = buildList {
        var offset = 0
        book.chapters.forEachIndexed { i, c ->
            val len = c.estimatedTextLength.coerceAtLeast(1)
            add(
                DocChapter(
                    index = i,
                    title = c.title,
                    startOffset = offset,
                    charCount = len,
                    charCountIsEstimated = true,
                ),
            )
            offset += len
        }
    }

    override val totalChars: Int = chapters.sumOf { it.charCount }

    override fun blocks(chapterIndex: Int): List<DocBlock> {
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return emptyList()
        return cache.get(chapterIndex) {
            EpubParser.loadChapterBlocks(
                chapter.cachedEpubPath,
                chapter.entryPath,
                chapter.chapterDir,
            ).map { it.toDocBlock() }
        }
    }

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
