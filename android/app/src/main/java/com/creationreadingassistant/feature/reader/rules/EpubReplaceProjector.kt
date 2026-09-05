package com.creationreadingassistant.feature.reader.rules

import com.creationreadingassistant.feature.reader.doc.DocBlock

/**
 * EPUB 结构保真替换投影器（净化覆盖 EPUB 的第一块可复用核心）。
 *
 * 与 TXT 整章投影（[BoundedReplaceProjector]）的本质差异：EPUB 章节块序列含
 * [DocBlock.Image]（不占字符、挂锚点）与标题标记，整章投影后重切段落会丢结构——
 * 架构文档因此禁止对 EPUB 做近似包装。本投影器改为**逐 Text 块独立投影**：
 *
 * - 块结构 / 图片锚点 / 标题标记原样保留（结构保真）；
 * - 跨块匹配（含跨块间 "\\n" 分隔符）**不生效**——块内正则匹配天然无重无漏；
 *   典型净化目标（段内广告、错字）不受影响；
 * - 每个 Text 块独立走 [ReplaceProjection.project]，得到块内 display 文本与
 *   [TextOffsetMap]；章级映射由 [ChapterBlockOffsetMap] 按块段拼接（块间 "\\n"
 *   分隔符两侧恒等映射）。
 *
 * 坐标口径（与 EPUB 分页引擎一致）：章内 source 偏移 = chapterTextOf(blocks) 的
 * 下标（Text 块按 "\\n" 连接，图片不占字符）；display 偏移同理基于投影后块序。
 * 调用方（分页宿主）负责章内 ↔ 全书（估算章基址）的加法。
 *
 * 纯函数、JVM 可测；单块超过 [maxBlockLength] 时该块保留原文（不产生部分替换）。
 */
object EpubReplaceProjector {

    /** 单块 source 上限，与 [BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS] 同量级。 */
    const val DEFAULT_MAX_BLOCK_CHARS: Int = BoundedReplaceProjector.DEFAULT_MAX_SOURCE_CHARS

    /** 单个 Text 块的投影结果；[offsetMap] 为块内局部映射。 */
    data class BlockProjection(
        val sourceText: String,
        val displayText: String,
        val offsetMap: TextOffsetMap,
    )

    data class Result(
        /** 投影后的块序：Text 块替换为 display 文本（isHeading 保留），Image 原样透传。 */
        val displayBlocks: List<DocBlock>,
        /** 与 displayBlocks 中 Text 块一一对应的块内投影（顺序 = Text 块出现顺序）。 */
        val textBlockProjections: List<BlockProjection>,
        /** 全部生效规则的总命中数。 */
        val hitCount: Int,
    ) {
        fun chapterOffsetMap(): ChapterBlockOffsetMap = ChapterBlockOffsetMap(textBlockProjections)
    }

    /**
     * 逐块投影。[rules] 语义与 [RuleEngine.applyReplace] 一致；空规则时原样返回
     * （恒等映射），调用方可据此走无替换快路径。
     */
    fun project(
        blocks: List<DocBlock>,
        rules: List<ReplaceRule>,
        bookId: String,
        maxBlockLength: Int = DEFAULT_MAX_BLOCK_CHARS,
    ): Result {
        require(rules.isNotEmpty()) { "空规则请走无替换路径，不要进入投影器" }
        val displayBlocks = ArrayList<DocBlock>(blocks.size)
        val projections = ArrayList<BlockProjection>(blocks.size)
        var hitCount = 0
        blocks.forEach { block ->
            when (block) {
                is DocBlock.Text -> {
                    if (block.text.length > maxBlockLength) {
                        // 单块超限：该块保留原文（恒等映射），不产生部分替换
                        displayBlocks += block
                        projections += identityProjection(block.text)
                    } else {
                        val projection = ReplaceProjection.project(block.text, rules, bookId)
                        displayBlocks += DocBlock.Text(projection.displayText, block.isHeading)
                        projections += BlockProjection(
                            sourceText = block.text,
                            displayText = projection.displayText,
                            offsetMap = projection.offsetMap,
                        )
                        hitCount += projection.hitCount
                    }
                }

                is DocBlock.Image -> displayBlocks += block
                is DocBlock.Markdown -> throw IllegalStateException("Markdown blocks not supported in EPUB")
            }
        }
        return Result(displayBlocks, projections, hitCount)
    }

    private fun identityProjection(sourceText: String): BlockProjection =
        BlockProjection(sourceText, sourceText, TextOffsetMap.identity(sourceText.length))
}

/**
 * 章级偏移映射：把逐块局部映射按「块在章文本中的段区间」拼接成
 * 章内 source ↔ display 的双向映射（实现 [TextOffsetMap]）。
 *
 * 段区间口径与 EpubPageSource.chapterTextOf / layoutBlocksOf 一致：
 * Text 块按出现顺序连接，块间插一个 "\n"（1 字符分隔位）；图片不占字符。
 * 分隔位两侧恒等映射（display 与 source 的分隔符位置同步平移）。
 */
class ChapterBlockOffsetMap(
    private val blocks: List<EpubReplaceProjector.BlockProjection>,
) : TextOffsetMap {

    private data class Segment(
        val sourceStart: Int,
        val displayStart: Int,
        val sourceLen: Int,
        val displayLen: Int,
        val map: TextOffsetMap,
    )

    private val segments: List<Segment>
    override val sourceLength: Int
    override val displayLength: Int

    init {
        val list = ArrayList<Segment>(blocks.size)
        var src = 0
        var dis = 0
        blocks.forEachIndexed { i, block ->
            list += Segment(
                sourceStart = src,
                displayStart = dis,
                sourceLen = block.sourceText.length,
                displayLen = block.displayText.length,
                map = block.offsetMap,
            )
            src += block.sourceText.length
            dis += block.displayText.length
            if (i < blocks.lastIndex) {
                // 块间 "\n" 分隔位：source/display 同步 +1
                src += 1
                dis += 1
            }
        }
        segments = list
        sourceLength = src
        displayLength = dis
    }


    override fun toSource(displayOffset: Int): Int {
        val local = displayOffset.coerceIn(0, displayLength)
        val (seg, idx) = locate(local, preferDisplay = true)
        return seg.sourceStart + seg.map.toSource(idx)
    }

    override fun toDisplay(sourceOffset: Int): Int {
        val local = sourceOffset.coerceIn(0, sourceLength)
        val (seg, idx) = locate(local, preferDisplay = false)
        return seg.displayStart + seg.map.toDisplay(idx)
    }

    /**
     * 定位偏移所在块段。偏移落在块间分隔位（或段尾）时归属前段（端点），
     * 保证映射结果落在该段边界内（floor 语义，与 [LinearTextOffsetMap] 一致）。
     */
    private fun locate(offset: Int, preferDisplay: Boolean): Pair<Segment, Int> {
        var low = 0
        var high = segments.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            val seg = segments[mid]
            val start = if (preferDisplay) seg.displayStart else seg.sourceStart
            if (start <= offset) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        val seg = segments[result]
        val start = if (preferDisplay) seg.displayStart else seg.sourceStart
        val len = if (preferDisplay) seg.displayLen else seg.sourceLen
        // 段尾（含分隔位）：clamp 到段长，让局部 map 自己做 floor
        return seg to (offset - start).coerceIn(0, len)
    }
}
