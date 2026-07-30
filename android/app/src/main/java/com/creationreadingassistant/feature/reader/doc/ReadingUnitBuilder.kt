package com.creationreadingassistant.feature.reader.doc

/**
 * 将用户章节列表切分为有界的读取单元。
 *
 * 规则：
 * - charCount <= [MAX_UNIT_CHARS] 的章节 → 1:1 映射
 * - charCount > [MAX_UNIT_CHARS] 的章节 → 按 MAX_UNIT_CHARS 切分为多个虚拟单元
 * - 流式模式（fileIndex 非 null）：使用 checkpoints 二分查找精确字节边界
 * - 小文件模式（fileIndex 为 null）：byteStart/byteLength 为 null，按字符偏移切分
 */
object ReadingUnitBuilder {

    /** 单个读取单元的最大字符数。50K 字符 ≈ 100 KB UTF-8，足够填满一屏。 */
    const val MAX_UNIT_CHARS = 50_000

    /**
     * 从用户章节列表和可选的文件索引构建读取单元列表。
     *
     * @param chapters 用户可见的章节列表（来自 PlainTextDocument.chapters 或 TxtChapterDetector）
     * @param fileIndex 流式扫描索引（非 null 时提供精确字节定位）
     * @return 有序的读取单元列表，覆盖全书
     */
    fun buildUnits(
        chapters: List<DocChapter>,
        fileIndex: TxtFileIndex?,
    ): List<ReadingUnit> {
        if (chapters.isEmpty()) return emptyList()

        val units = ArrayList<ReadingUnit>()
        var unitIndex = 0
        val checkpoints = fileIndex?.checkpoints

        for (chapter in chapters) {
            if (chapter.charCount <= MAX_UNIT_CHARS) {
                // 正常章节：1:1 映射
                val entry = fileIndex?.chapters?.getOrNull(chapter.index)
                units.add(
                    ReadingUnit(
                        unitIndex = unitIndex++,
                        chapterIndex = chapter.index,
                        title = chapter.title,
                        charStart = chapter.startOffset,
                        charCount = chapter.charCount,
                        byteStart = entry?.byteStart,
                        byteLength = entry?.byteLength,
                    )
                )
            } else {
                /*
                 * 超长章节必须按字符数硬切分。旧实现只在换行 checkpoint 处分割，
                 * 遇到一整行数十万字的 TXT 时会产生一个巨型读取单元；随后
                 * readWindow 的 100K 安全上限会截断正文，表现为书能打开但内容缺失。
                 *
                 * 子单元不携带“伪精确”的字节范围，PlainTextDocument 会用 checkpoint
                 * 从字符偏移做有界读取。这样长行、混合宽度 UTF-8 和 UTF-16 都走同一
                 * 条精确路径，同时保证每个 UI 单元最多 MAX_UNIT_CHARS。
                 */
                var charsConsumed = 0L
                while (charsConsumed < chapter.charCount.toLong()) {
                    val unitStart = chapter.startOffset.toLong() + charsConsumed
                    val chapterEnd = chapter.startOffset.toLong() + chapter.charCount
                    val hardEnd = minOf(unitStart + MAX_UNIT_CHARS, chapterEnd)
                    val safeFloor = checkpoints
                        ?.let { floorCheckpoint(it, hardEnd) }
                        ?.charOffset
                        ?.takeIf { it > unitStart }
                    val nearCeiling = checkpoints
                        ?.let { ceilingCheckpoint(it, unitStart + 1) }
                        ?.charOffset
                        ?.takeIf {
                            it <= hardEnd + 1_000 ||
                                fileIndex?.encoding?.startsWith("UTF-16") == true
                        }
                    val unitEnd = when {
                        hardEnd == chapterEnd -> chapterEnd
                        safeFloor != null -> safeFloor
                        nearCeiling != null -> nearCeiling
                        else -> hardEnd
                    }
                    val unitChars = (unitEnd - unitStart).toInt()
                    val entry = fileIndex?.chapters?.getOrNull(chapter.index)
                    val exactStartByte = when {
                        entry == null -> null
                        unitStart == chapter.startOffset.toLong() -> entry.byteStart
                        else -> checkpoints
                            ?.let { exactCheckpoint(it, unitStart) }
                            ?.byteOffset
                    }
                    val exactEndByte = when {
                        entry == null -> null
                        unitEnd == chapterEnd -> entry.byteStart + entry.byteLength
                        else -> checkpoints
                            ?.let { exactCheckpoint(it, unitEnd) }
                            ?.byteOffset
                    }
                    units.add(
                        ReadingUnit(
                            unitIndex = unitIndex++,
                            chapterIndex = chapter.index,
                            title = chapter.title,
                            charStart = unitStart.toInt(),
                            charCount = unitChars,
                            byteStart = if (exactStartByte != null && exactEndByte != null) {
                                exactStartByte
                            } else {
                                null
                            },
                            byteLength = if (exactStartByte != null && exactEndByte != null) {
                                (exactEndByte - exactStartByte).toInt().coerceAtLeast(0)
                            } else {
                                null
                            },
                        ),
                    )
                    charsConsumed += unitChars
                }
            }
        }

        return units
    }

    private fun floorCheckpoint(
        checkpoints: List<CharByteCheckpoint>,
        charOffset: Long,
    ): CharByteCheckpoint? {
        var low = 0
        var high = checkpoints.lastIndex
        var result: CharByteCheckpoint? = null
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (checkpoints[mid].charOffset <= charOffset) {
                result = checkpoints[mid]
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    private fun exactCheckpoint(
        checkpoints: List<CharByteCheckpoint>,
        charOffset: Long,
    ): CharByteCheckpoint? {
        val candidate = floorCheckpoint(checkpoints, charOffset)
        return candidate?.takeIf { it.charOffset == charOffset }
    }

    private fun ceilingCheckpoint(
        checkpoints: List<CharByteCheckpoint>,
        charOffset: Long,
    ): CharByteCheckpoint? {
        var low = 0
        var high = checkpoints.lastIndex
        var result: CharByteCheckpoint? = null
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (checkpoints[mid].charOffset >= charOffset) {
                result = checkpoints[mid]
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return result
    }

    /**
     * 从纯文本分块列表构建读取单元（小文件路径）。
     * 每个 PlainTextChunk 对应一个 ReadingUnit。
     */
    fun buildUnitsFromChunks(chunks: List<Pair<Int, String>>): List<ReadingUnit> {
        return chunks.mapIndexed { i, (startOffset, text) ->
            ReadingUnit(
                unitIndex = i,
                chapterIndex = 0,
                title = "全文",
                charStart = startOffset,
                charCount = text.length,
                byteStart = null,
                byteLength = null,
            )
        }
    }
}
