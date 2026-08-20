package com.creationreadingassistant.feature.reader.layout

import org.junit.Test
import kotlin.math.roundToLong
import kotlin.system.measureNanoTime

/**
 * 流式排版决策基线：典型 50 万字章节 paginate 全章耗时。
 * 文档阈值：< 200ms 则 backlog 第 5 项"章节流式排版"降级为不做。
 * 运行：./gradlew :app:testDebugUnitTest --tests "*.ChapterPaginatorBenchmark"
 */
class ChapterPaginatorBenchmark {

    private val em = 60f // 接近阅读页真实 18–22sp（≈60px，dpi≈3x）
    private val ruler = FakeTextRuler(fontSizePx = em)
    private val oracle = AnywhereBreakOracle()

    // 真实手机屏：内容宽 360dp - 2*16dp padding = 328dp ≈ 984px；高 720dp - 系统栏 ≈ 2000px
    // 这里用略保守的值（20em 宽 / 28em 高 / em=60px → 1200×1680px）避免分页太少
    private fun realDeviceCfg() = LayoutConfig(
        contentWidthPx = 20f * em,
        contentHeightPx = 28f * em,
        fontSizePx = em,
    )

    /** 造 50 万字符的典型中文段落：章前语 + 1667 段 × 300 字/段（对话+叙述混排）。 */
    private fun buildHalfMillionCharChapter(): List<LayoutParagraph> {
        val sentenceA = "她推开那扇吱呀作响的木门，月光落在青石板上，像撒了一层碾碎的贝壳。远处巷口传来卖馄饨的梆子声，一下一下，敲得人心口发紧。"
        val sentenceB = "「你终于来了。」坐在门槛上的老人抬起头，浑浊的眼睛里看不出喜怒，「我等了你三十年。」"
        val sentenceC = "夜风卷着栀子花香从廊下穿过，檐角的铁马轻鸣，厅堂里挂着的那幅《秋山行旅图》也跟着微微晃动，仿佛山水随时会从画里走出来。"
        val sentenceD = "这是第三百七十一次尝试。他把钥匙插进锁孔，屏住呼吸，顺时针旋转——咔的一声，锁舌弹开的同时，玄关尽头那盏感应灯也亮了起来。"
        val paraTemplate = (sentenceA + sentenceB + sentenceC + sentenceD).repeat(2) // ~300字
        val paragraphs = ArrayList<LayoutParagraph>(1700)
        var offset = 0
        // 标题
        paragraphs.add(
            LayoutParagraph(
                text = "第五章 漫长的归程",
                role = BlockRole.HEADING,
                charOffset = offset,
            ),
        )
        offset += paragraphs.last().text.length
        // 50 万字正文循环生成
        val target = 500_000
        while (offset < target) {
            val body = paraTemplate + if ((offset and 15) == 0) "\n" else "" // 偶有换行
            val cut = if (offset + body.length <= target) body else body.take(target - offset)
            paragraphs.add(
                LayoutParagraph(
                    text = cut,
                    role = BlockRole.BODY,
                    charOffset = offset,
                ),
            )
            offset += cut.length
        }
        return paragraphs
    }

    @Test
    fun `paginate 500k chars single-warmup + median-5-timing`() {
        val paras = buildHalfMillionCharChapter()
        val totalChars = paras.sumOf { it.text.length }
        val cfg = realDeviceCfg()
        println("== ChapterPaginatorBenchmark ==")
        println("章节总字符数: $totalChars")
        println("段落数      : ${paras.size}")
        println("版面 (px)   : ${cfg.contentWidthPx.toInt()} x ${cfg.contentHeightPx.toInt()}")
        println("字号 (px)   : ${cfg.fontSizePx.toInt()}")

        // 预热 1 次（JIT）
        val warmupNs = measureNanoTime { ChapterPaginator.paginate(paras, cfg, ruler, oracle) }
        println("预热耗时    : ${(warmupNs / 1_000_000.0).format(2)} ms")

        // 重复 5 次取中位数（防 GC/调度抖动）
        val runs = LongArray(5) {
            val ns = measureNanoTime { ChapterPaginator.paginate(paras, cfg, ruler, oracle) }
            (ns / 1_000_000.0).roundToLong().also { ms ->
                println("第${it + 1}轮耗时   : $ms ms")
            }
        }
        runs.sort()
        val median = runs[2]
        val mean = runs.average().toLong()
        println("中位数耗时  : $median ms")
        println("平均耗时    : $mean ms")
        println("阈值 200ms  : ${if (median < 200) "✅ 低于阈值 → 流式排版无需做" else "❌ 高于阈值 → 流式排版建议做"}")
        // 输出到 stdout 方便 CI 读；同时也用 assert 打个松的 sanity，避免 CI 绿了但真的极慢
        assert(median < 2000L) { "即使最糟情形也不该 >2s，怀疑基准写错了（median=${median}ms）" }
    }

    private fun Double.format(digits: Int): String = java.lang.String.format("%.${digits}f", this)
}
