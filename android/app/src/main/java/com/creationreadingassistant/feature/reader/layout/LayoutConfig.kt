package com.creationreadingassistant.feature.reader.layout

/**
 * 排版配置与**排版指纹**。
 *
 * 指纹是分页缓存的失效依据。少算一个因子，就会出现「换了字体/换了设备，
 * 缓存的页边界还按旧字号算」的错位。特别注意两个容易漏的：
 *
 * - [typefaceKey]：项目没有内置字体，全走系统 CJK 回退。Android 12+ 用户
 *   换系统字体会在运行中改变排版，缓存必须跟着失效。
 * - [engineVersion]：排版算法本身改动后，旧缓存的页边界不再等价。
 *   改算法时必须手动 +1，否则用户会看到半新半旧的分页。
 */
data class LayoutConfig(
    /** 正文可用宽度（px），已扣除左右边距 */
    val contentWidthPx: Float,
    /** 正文可用高度（px），已扣除上下边距 */
    val contentHeightPx: Float,
    val fontSizePx: Float,
    /** 行高倍数。中文正文 1.6~1.8 才透气，拉丁文的 1.5 偏挤 */
    val lineHeightMultiplier: Float = 1.7f,
    /** 段间距，单位 em */
    val paragraphSpacingEm: Float = 0.4f,
    /** 首行缩进，单位 em。中文标准是 2 字 */
    val firstLineIndentEm: Float = 2f,
    /** 标题字号相对正文的倍数 */
    val headingScale: Float = 1.25f,

    // ── 两端对齐 ────────────────────────────────────────────────────────
    val justify: Boolean = true,
    /** 单个间隙最多拉伸多少 em。clreq 6.3.3 给的是 1/3 */
    val maxStretchPerGapEm: Float = 1f / 3f,
    /** 整行 slack 超过这个量就整行放弃拉伸，避免稀疏得难看 */
    val maxSlackEm: Float = 2f,
    /** 单个间隙最多压缩多少 em（负值） */
    val minCompressPerGapEm: Float = -0.06f,
    /** 少于这么多簇的行不做两端对齐 */
    val minJustifyClusters: Int = 10,

    // ── 禁则 ────────────────────────────────────────────────────────────
    /** 一行至少要保留的簇数，防止回退把行掏空 */
    val minLineClusters: Int = 1,
    /** STRICT 档：破折号与省略号也禁首 */
    val strictKinsoku: Boolean = true,

    val typefaceKey: String = "system",
    val engineVersion: Int = ENGINE_VERSION,
) {
    val em: Float get() = fontSizePx
    val lineHeightPx: Float get() = fontSizePx * lineHeightMultiplier
    val firstLineIndentPx: Float get() = fontSizePx * firstLineIndentEm
    val paragraphSpacingPx: Float get() = fontSizePx * paragraphSpacingEm

    /**
     * 排版指纹。同一指纹下重排必然得到逐字段相同的结果，
     * 这正是「只缓存 pageStarts、页按需重排」策略成立的前提。
     */
    val fingerprint: Int
        get() {
            var r = contentWidthPx.toBits()
            r = 31 * r + contentHeightPx.toBits()
            r = 31 * r + fontSizePx.toBits()
            r = 31 * r + lineHeightMultiplier.toBits()
            r = 31 * r + paragraphSpacingEm.toBits()
            r = 31 * r + firstLineIndentEm.toBits()
            r = 31 * r + headingScale.toBits()
            r = 31 * r + if (justify) 1 else 0
            r = 31 * r + maxStretchPerGapEm.toBits()
            r = 31 * r + maxSlackEm.toBits()
            r = 31 * r + minJustifyClusters
            r = 31 * r + minLineClusters
            r = 31 * r + if (strictKinsoku) 1 else 0
            r = 31 * r + typefaceKey.hashCode()
            r = 31 * r + engineVersion
            return r
        }

    companion object {
        /** 改动排版算法时必须 +1，否则旧缓存的页边界会与新算法混用。 */
        const val ENGINE_VERSION = 1
    }
}
