package com.creationreadingassistant.domain.model

import com.creationreadingassistant.feature.reader.EpubParser

/**
 * 一本 EPUB 解析后的内存模型（P2 自包含解析，非 Readium）。
 *
 * 设计权衡：Readium 的 Maven 坐标 / API 版本敏感，当前沙箱无网络无法验证、首编极易炸；
 * 故 P2 用内置 java.util.zip + XmlPullParser 做零外部依赖的原生解析（同样满足「纯原生、无 WebView」），
 * 后续如需更强排版/分页能力，可把 EpubParser 换成 Readium 并保留本模型不动。
 *
 * 内存策略：解析阶段【只】抽取章节目录（标题 + 条目路径），不载入正文。
 * 正文块 [EpubChapter.blocks] 按需懒加载（读哪一章解哪一章），
 * 避免整本大书一次性占满内存触发 OOM。
 */
data class EpubBook(
    val id: String,
    val title: String,
    val author: String?,
    val chapters: List<EpubChapter>,
    val localUri: String,
    /** 解析时落盘的缓存 epub 路径，供按章懒加载正文块。 */
    val cachedEpubPath: String,
    /** 封面图片的 zip 条目路径（OPF meta / cover-image 解析所得），无封面为 null。 */
    val coverEntryPath: String? = null,
)

/**
 * 单个章节的轻量描述。
 * - [title] 在解析时即确定（仅读取章节头部前若干 KB，不载入全文）。
 * - [blocks] 正文块按需解压（读哪一章解哪一章）。**不做跨章缓存**：
 *   阅读器把当前章块存在 [chapterBlocks] 状态里，翻过的旧章块随状态覆盖而可被 GC，
 *   从而避免长会话里每章块都永久驻留、逐渐累积触发 OOM。
 */
class EpubChapter(
    val title: String,
    val entryPath: String,
    val chapterDir: String,
    val cachedEpubPath: String,
    /**
     * ZIP 条目的解压后字节数。用于构建轻量章节偏移和字数估算，
     * 打开书籍时不再为了统计而逐章解压全文。
     */
    val estimatedTextLength: Int = 0,
) {
    /** 按需解压当前章正文块（每次访问重新解压，内存只保留当前章）。 */
    val blocks: List<EpubBlock>
        get() = EpubParser.loadChapterBlocks(cachedEpubPath, entryPath, chapterDir)
}

/**
 * 章节内容块：
 * - [Text] 一个文本段落（isHeading 表示来自 <h1>~<h6> 的标题）。
 * - [Image] 图片已抽取到应用缓存，filePath 为绝对路径，由 Coil 直接加载。
 */
sealed interface EpubBlock {
    data class Text(val text: String, val isHeading: Boolean = false) : EpubBlock
    data class Image(val filePath: String) : EpubBlock
}
