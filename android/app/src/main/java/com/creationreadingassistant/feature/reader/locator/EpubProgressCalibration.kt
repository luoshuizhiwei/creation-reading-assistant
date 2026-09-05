package com.creationreadingassistant.feature.reader.locator

import com.creationreadingassistant.feature.reader.doc.LegacyOffsetCodec

/**
 * EPUB 进度校准（P5「真实字符数随阅读逐章回填」的纯策略层）。
 *
 * 背景：EPUB 章节偏移沿用 `estimatedTextLength`（ZIP 条目字节数），中文书约为真实
 * 字符数的 3 倍。历史定位数据（高亮/笔记/进度 locator）全部以该估算空间编码，不可
 * 更换坐标系；但进度百分比的分子里章内偏移是真实字符——分母估算、分子真实导致
 * **EPUB 书读到末章进度条也到不了 100%**。
 *
 * 校准思路：存储坐标系不变（locator/分页跳转仍用估算章基址 + 真实章内偏移的混合
 * 空间），只为「百分比」这一展示/统计口径建立真实字符模型：
 * - 已读到的章：用实测字符数（EpubDocument 在章节解析时回填）；
 * - 未读到的章：用估算长度 × 校准比 r（已测章节 Σ真实/Σ估算，随阅读收敛）。
 * 全部章节实测后百分比精确；仅实测部分准确时随阅读单调逼近精确值。
 *
 * 纯函数、JVM 可测；无实测数据时 r=1，行为与旧公式逐值一致（安全降级）。
 */
object EpubProgressCalibration {

    /** 校准输入快照：估算章长 + 已实测章的真实字符数（章索引 → 真实字符数）。 */
    data class Model(
        val estimatedLengths: List<Int>,
        val measuredRealChars: Map<Int, Int>,
    )

    /**
     * 校准比：已实测章节的 Σ真实 / Σ估算。中文书约 0.3；英文书接近 1。
     * 无有效样本或比值异常（空数据/负值）时回退 1（= 旧公式口径）。
     */
    fun calibrationRatio(model: Model): Float {
        var real = 0L
        var est = 0L
        model.measuredRealChars.forEach { (index, realChars) ->
            val estimated = model.estimatedLengths.getOrNull(index)?.takeIf { it > 0 } ?: return@forEach
            if (realChars <= 0) return@forEach
            real += realChars
            est += estimated
        }
        if (est <= 0L || real <= 0L) return 1f
        return (real.toFloat() / est).coerceIn(0.05f, 1f)
    }

    /** 第 [index] 章参与百分比计算的有效长度：实测优先，否则估算 × 校准比。 */
    private fun effectiveChars(model: Model, index: Int, ratio: Float): Float {
        model.measuredRealChars[index]?.takeIf { it > 0 }?.let { return it.toFloat() }
        val estimated = model.estimatedLengths.getOrNull(index) ?: return 0f
        return (estimated.coerceAtLeast(0)) * ratio
    }

    /**
     * 当前阅读位置的真实口径百分比（0..100）。
     *
     * [chapterIndex] 当前章；[offsetInChapter] 章内真实字符偏移（与
     * SaveEpubProgress.offsetInChapter 同口径）。越界一律夹紧，不抛异常。
     */
    fun percentFor(model: Model, chapterIndex: Int, offsetInChapter: Int): Float {
        val ratio = calibrationRatio(model)
        if (model.estimatedLengths.isEmpty()) return 0f
        val current = chapterIndex.coerceIn(0, model.estimatedLengths.lastIndex)
        var consumed = 0f
        repeat(current) { i -> consumed += effectiveChars(model, i, ratio) }
        consumed += offsetInChapter
            .coerceAtLeast(0)
            .coerceAtMost(effectiveChars(model, current, ratio).toInt())
        var total = 0f
        model.estimatedLengths.indices.forEach { i -> total += effectiveChars(model, i, ratio) }
        if (total <= 0f) return 0f
        return (consumed / total * 100f).coerceIn(0f, 100f)
    }

    /**
     * 百分比 → 分页引擎混合空间的全书偏移（估算章基址 + 章内真实偏移），
     * 与 [com.creationreadingassistant.ui.screen.reader] 的 pagedJumpRequest 同一口径。
     * 空书返回 null。
     */
    fun mixedGlobalOffsetForPercent(model: Model, percent: Float): Int? {
        val ratio = calibrationRatio(model)
        if (model.estimatedLengths.isEmpty()) return null
        val chapterStarts = LegacyOffsetCodec.chapterStartOffsets(model.estimatedLengths)
        var target = (percent.coerceIn(0f, 100f) / 100f) * model.estimatedLengths.indices
            .sumOf { effectiveChars(model, it, ratio).toDouble() }
        model.estimatedLengths.indices.forEach { i ->
            val len = effectiveChars(model, i, ratio)
            if (target < len || i == model.estimatedLengths.lastIndex) {
                val inChapter = target.coerceAtLeast(0.0).coerceAtMost(len.toDouble()).toInt()
                return chapterStarts.getOrElse(i) { 0 } + inChapter
            }
            target -= len
        }
        return null
    }
}
