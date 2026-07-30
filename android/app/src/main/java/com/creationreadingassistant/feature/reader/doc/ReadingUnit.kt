package com.creationreadingassistant.feature.reader.doc

/**
 * 内部读取单元 —— 惰性加载的最小单位。
 *
 * 与用户可见的「章节」(DocChapter) 是不同层次：
 * - 用户目录显示真实章节（DocChapter）
 * - 滚动/渲染使用读取单元（ReadingUnit）
 * - 正常章节 1:1 映射为一个 ReadingUnit
 * - 超长章节（> [ReadingUnitBuilder.MAX_UNIT_CHARS]）被切分为多个虚拟 ReadingUnit
 *
 * 内存模型：ReadingUnit 只持有偏移元数据（~100 字节/实例），不持有文本。
 * 文本通过 PlainTextDocument.readUnit() 按需加载。
 */
data class ReadingUnit(
    /** 全局读取单元序号（从 0 开始，跨章节连续编号）。 */
    val unitIndex: Int,
    /** 所属用户章节索引（对应 DocChapter.index）。 */
    val chapterIndex: Int,
    /** 用户章节标题（来自 DocChapter.title）。 */
    val title: String,
    /** 本单元起点的全书字符偏移。 */
    val charStart: Int,
    /** 本单元的字符数。 */
    val charCount: Int,
    /** 流式模式：本单元在文件中的字节起始偏移。小文件模式为 null。 */
    val byteStart: Long? = null,
    /** 流式模式：本单元在文件中的字节长度。小文件模式为 null。 */
    val byteLength: Int? = null,
)
