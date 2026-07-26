package com.creationreadingassistant.feature.reader.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对抗性复核（2026-07-27）确认的 12 处缺陷的回归测试。
 *
 * 每个测试都对应一个**当时全绿的测试集没有覆盖到**的失败模式。写在独立文件里，
 * 是为了让「这些断言存在的唯一理由是某个具体的历史缺陷」这件事一眼可见 ——
 * 将来若有人觉得某条断言碍事，能立刻查到它挡住的是什么。
 */
class LayoutRegressionTest {

    private val em = 100f
    private val ruler = FakeTextRuler(fontSizePx = em)
    private val oracle = AnywhereBreakOracle()

    private fun cfg(
        widthEm: Float = 20f,
        justify: Boolean = true,
        indentEm: Float = 2f,
    ) = LayoutConfig(
        contentWidthPx = widthEm * em,
        contentHeightPx = 30 * em,
        fontSizePx = em,
        firstLineIndentEm = indentEm,
        justify = justify,
    )

    private fun layout(
        text: String,
        c: LayoutConfig = cfg(),
        r: TextRuler = ruler,
        o: BreakOracle = oracle,
    ) = LineComposer.layoutParagraph(LayoutParagraph(text), c, r, o)

    private fun clustersOf(text: String, r: TextRuler = ruler) = Clusterizer.of(text, r)

    // ── 1. 逃生分支多塞一簇 ──────────────────────────────────────────────

    @Test
    fun `overlong atomic unit never overflows the line`() {
        // advanceToOverflow 曾恒返回 from+1，于是逃生分支覆盖的每一行都多出一簇。
        // 这些样本都是中文网文里真实存在的写法。
        val c = cfg(indentEm = 0f)
        val samples = listOf(
            "a".repeat(200),
            "见此链接https://example.com/" + "a".repeat(100) + " 后文继续，这里再补一些中文正文。",
            "他愣住了" + "…".repeat(50) + "然后什么也没说，这一段还要再长一些。",
            "她张了张嘴" + "—".repeat(50) + "什么也没说出来，这一段还要再长一些。",
            "账号是 " + "1".repeat(46) + " 请记好，这一段还要再长一些。",
        )
        samples.forEach { text ->
            val lines = layout(text, c)
            assertTrue("应排出多行：$text", lines.size >= 2)
            lines.forEach { line ->
                val width = line.endX - line.startX
                assertTrue(
                    "行宽 $width 超出可用宽度 ${c.contentWidthPx}（超长原子单元不应多塞一簇）：" +
                        text.substring(line.startInText, line.endInText),
                    width <= c.contentWidthPx + LineComposer.EPS,
                )
            }
        }
    }

    // ── 2. phaseB 复制整段数组导致 O(n²) ────────────────────────────────

    @Test
    fun `long paragraph layout stays roughly linear`() {
        // 相位 B 曾在每行每轮 copyOf() 两条「整段长度」的数组。
        // 段落越长，单行成本越高 —— 20 万字的单段会直接 ANR。
        val c = cfg()
        fun timeOf(charCount: Int): Long {
            val text = "这是一段用于测量装行耗时的中文正文内容".repeat(charCount / 18 + 1)
            layout(text, c) // 预热，排除 JIT 与首次类加载
            val t0 = System.nanoTime()
            layout(text, c)
            return System.nanoTime() - t0
        }

        val small = timeOf(20_000).coerceAtLeast(1)
        val large = timeOf(160_000)
        // 字数翻 8 倍。线性应约 8 倍，平方级会是约 64 倍。
        // 阈值放宽到 24 倍，既能挡住平方级，又不会因机器抖动而假红。
        val ratio = large.toDouble() / small.toDouble()
        assertTrue(
            "字数 ×8 后耗时变成 ${"%.1f".format(ratio)} 倍，疑似退化为平方级",
            ratio < 24.0,
        )
    }

    // ── 3. 标点削宽硬减 0.5em，窄标点会被削成零宽或负宽 ─────────────────

    @Test
    fun `narrow punctuation never gets zero or negative advance`() {
        // 真实字体的标点常常不满宽。硬减 0.5em 会削出负 advance，
        // clusterX 随之倒退，绘制与选区全线错位。
        val narrow = FakeTextRuler(fontSizePx = em, fullWidthPunctScale = 0.3f)
        val text = "他说：“你好。”然后走了。（真的）「引用」《书名》，就这样。"
        val c = cfg(indentEm = 0f)
        val lines = layout(text, c, r = narrow)
        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            for (i in 1 until line.clusterX.size) {
                assertTrue(
                    "clusterX 必须单调非递减，第 $i 处出现倒退：" +
                        "${line.clusterX[i - 1]} → ${line.clusterX[i]}",
                    line.clusterX[i] >= line.clusterX[i - 1] - LineComposer.EPS,
                )
            }
        }
    }

    @Test
    fun `blankPx is capped at half the measured width`() {
        // 直接锁住封顶规则本身，避免有人把 coerceAtMost 去掉。
        val measured = 30f // 0.3em @ em=100
        val blank = WidthAdjuster.blankPx(CharClass.END_PUNCT, measured, em)
        assertEquals(15f, blank, 0.001f)
        assertTrue("削减量不得超过实测宽度的一半", blank <= measured / 2f)
        // 非标点没有可削的空白
        assertEquals(0f, WidthAdjuster.blankPx(CharClass.CJK, em, em), 0.001f)
    }

    // ── 4. 两端对齐把原子单元内部撑开 ───────────────────────────────────

    @Test
    fun `justification never stretches inside an atomic unit`() {
        val c = cfg(widthEm = 16f, indentEm = 0f)
        val text = "圆周率是3.14159而日期是2019-08-15还有e-mail地址需要保持完整不被拉开真的很重要"
        val lines = layout(text, c)
        val clusters = clustersOf(text)
        val atom = AtomicUnits.compute(clusters)

        var checked = 0
        lines.forEach { line ->
            for (i in line.startCluster until line.endCluster - 1) {
                if (atom[i] == AtomicUnits.NONE || atom[i] != atom[i + 1]) continue
                val advance = line.clusterX[i + 1 - line.startCluster] -
                    line.clusterX[i - line.startCluster]
                assertEquals(
                    "原子单元内部第 $i 簇的推进被两端对齐拉开了",
                    clusters.advance[i],
                    advance,
                    0.01f,
                )
                checked++
            }
        }
        assertTrue("测试本身要有覆盖到原子单元内部的间隙", checked > 0)
    }

    // ── 5. 溢出行永不压回 ───────────────────────────────────────────────

    @Test
    fun `overflowed lines are compressed back not left alone`() {
        // overflowed 曾被并进 noStretch，于是 slack<0 的压缩分支成了死代码。
        val c = cfg(widthEm = 12f, indentEm = 0f)
        val text = "这是正文" + "」".repeat(6) + "后面还有很多中文正文用来撑出更多的行数继续排下去"
        val lines = layout(text, c)
        lines.forEach { line ->
            val width = line.endX - line.startX
            assertTrue(
                "即便溢出也必须有上界，实际 $width / 可用 ${c.contentWidthPx}",
                width <= c.contentWidthPx * 1.2f,
            )
        }
    }

    // ── 6. 簇→偏移线性插值，含代理对的行整行点错 ────────────────────────

    @Test
    fun `hit test maps clusters to real offsets with surrogate pairs`() {
        // 𠮷 是代理对，一簇两个 char。按簇数等分首末偏移必然错位。
        val text = "开头𠮷字𠮷更多𠮷内容𠮷再来𠮷一些𠮷中文𠮷正文𠮷继续𠮷排版𠮷测试"
        val c = cfg(indentEm = 0f)
        val lines = layout(text, c)
        val clusters = clustersOf(text)

        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            for (i in 0 until line.clusterCount) {
                val expected = clusters.startInText[line.startCluster + i]
                // 取该簇的中心偏左一点，确保落进这一簇而不是下一簇
                val left = line.clusterX[i]
                val right = line.clusterX[i + 1]
                val x = left + (right - left) * 0.2f
                assertEquals(
                    "第 $i 簇的命中偏移不对",
                    expected,
                    PageHitTest.offsetInLine(line, x),
                )
            }
        }
    }

    @Test
    fun `cluster starts are recorded not interpolated`() {
        val text = "𠮷a𠮷bb𠮷"
        val lines = layout(text, cfg(indentEm = 0f))
        val clusters = clustersOf(text)
        lines.forEach { line ->
            for (i in 0..line.clusterCount) {
                assertEquals(
                    clusters.startInText[line.startCluster + i],
                    line.clusterStarts[i],
                )
            }
        }
    }

    // ── 7. 中西文间距被插到「中文标点 ↔ 西文」之间 ──────────────────────

    @Test
    fun `no cjk latin gap around full width punctuation`() {
        // clreq 6.3.3：标点与西文之间不加中西文间距，标点自带的半格空白已经够了。
        val c = cfg()
        val fullWidthPuncts = "。，、；：？！）」》“”（「《"
        fullWidthPuncts.forEach { punct ->
            listOf("A", "1").forEach { latin ->
                // 必须写成 ${}：汉字是 Kotlin 的合法标识符字符，"$latin文" 会被当成变量 latin文
                val text = "中${punct}${latin}文"
                val clusters = clustersOf(text)
                val a = WidthAdjuster.phaseA(clusters, c)
                val pi = 1 // 标点所在簇
                assertEquals(
                    "'$punct' 与 '$latin' 之间不应有中西文间距",
                    0f,
                    a.gapAfter[pi],
                    0.001f,
                )
            }
        }
    }

    @Test
    fun `cjk latin gap still applies between han and latin`() {
        // 收窄判据不能把正常的中西文间距一起收掉。
        val clusters = clustersOf("中A")
        val a = WidthAdjuster.phaseA(clusters, cfg())
        assertEquals(WidthAdjuster.CJK_LATIN_GAP_EM * em, a.gapAfter[0], 0.001f)
    }

    // ── 8 / 12. 禁则表缺项 ──────────────────────────────────────────────

    @Test
    fun `kinsoku tables cover clreq connectors separators and signs`() {
        // 期望表按 clreq 6.1.1 / 6.1.2.2 独立誊抄，刻意不引用 CharClass 内部常量 ——
        // 否则「实现改错了、测试跟着改错」这种同步失效抓不出来。
        // 注意：待测字符里就有 '%'，消息不能走 String.format，否则它自己会当成格式符炸掉
        fun label(ch: Char) = "'" + ch + "' (U+" + ch.code.toString(16).uppercase().padStart(4, '0') + ")"

        val mustNoStart = "。，、；：？！）」》』】〕｝］”’·・‧／/～〜－–‐％%‰℃℉°′″｡､｣"
        mustNoStart.forEach { ch ->
            assertTrue(
                label(ch) + " 应禁首",
                CharClass.isNoStart(CharClass.classify(ch), ch, strict = true),
            )
        }
        val mustNoEnd = "（「《『【〔｛［“‘·・‧／/￥\$£€¥±+−｢"
        mustNoEnd.forEach { ch ->
            assertTrue(
                label(ch) + " 应禁尾",
                CharClass.isNoEnd(CharClass.classify(ch), ch),
            )
        }
        // 反向：普通汉字与字母既不禁首也不禁尾，否则断行会被卡死
        "中文abcABC123".forEach { ch ->
            assertFalse("'$ch' 不该禁首", CharClass.isNoStart(CharClass.classify(ch), ch, true))
            assertFalse("'$ch' 不该禁尾", CharClass.isNoEnd(CharClass.classify(ch), ch))
        }
    }

    @Test
    fun `separator does not start a line in real layout`() {
        // 表里有还不够，得真的在装行里生效（曾经 ICU 允许在 ／ 前断行）。
        val body = "这是一段用来撑满行宽的中文正文内容"
        val text = buildString { repeat(20) { append(body); append("／") } }
        val lines = layout(text, cfg(), o = JdkBreakOracle())
        lines.drop(1).forEach { line ->
            assertNotEquals("／ 不应出现在行首", '／', text[line.startInText])
        }
    }

    // ── 9. 全角字母与全角数字分类不一致 ─────────────────────────────────

    @Test
    fun `full width letters and digits share one class`() {
        assertEquals(CharClass.classify('Ａ'), CharClass.classify('１'))
        assertEquals(CharClass.CJK, CharClass.classify('１'))
        assertEquals(CharClass.CJK, CharClass.classify('Ａ'))
        // 半角的仍各归各类
        assertEquals(CharClass.DIGIT, CharClass.classify('1'))
        assertEquals(CharClass.LATIN, CharClass.classify('A'))
    }

    @Test
    fun `no gap inside a full width run`() {
        val clusters = clustersOf("ＵＳＢ３")
        val a = WidthAdjuster.phaseA(clusters, cfg())
        for (i in 0 until clusters.count - 1) {
            assertEquals("全角串内部第 $i 处不该有中西文间距", 0f, a.gapAfter[i], 0.001f)
        }
    }

    // ── 10. 扩展汉字被判成 OTHER ────────────────────────────────────────

    @Test
    fun `supplementary plane han is classified as cjk`() {
        assertEquals(CharClass.CJK, CharClass.classifyCodePoint(0x20BB7)) // 𠮷
        assertEquals(CharClass.CJK, clustersOf("𠮷").klass[0])
        // 与 BMP 汉字行为一致：中西文间距该有的都有
        val ext = WidthAdjuster.phaseA(clustersOf("𠮷abc"), cfg())
        val bmp = WidthAdjuster.phaseA(clustersOf("吉abc"), cfg())
        assertEquals(bmp.gapAfter[0], ext.gapAfter[0], 0.001f)
    }

    // ── 11. 半角句读被当成全角，行末削半会削掉全部宽度 ───────────────────

    @Test
    fun `half width punctuation keeps its width`() {
        assertEquals(CharClass.HALF_PUNCT, CharClass.classify('｡'))
        assertEquals(CharClass.HALF_PUNCT, CharClass.classify('､'))
        assertFalse(CharClass.isFullWidthPunct(CharClass.classify('｡')))
        assertEquals(0f, CharClass.blankEm(CharClass.HALF_PUNCT), 0.001f)

        // 行末削半不得动它：半角本来就只有半格，削掉就没了
        val clusters = clustersOf("あいうえお｡")
        val c = cfg()
        val a = WidthAdjuster.phaseA(clusters, c)
        val last = clusters.count - 1
        val b = WidthAdjuster.phaseB(clusters, a, 0, clusters.count, c)
        assertEquals(
            "半角句读在行末不应被削宽",
            clusters.advance[last],
            b.advAt(a, last),
            0.001f,
        )
        assertTrue("半角句读的推进必须仍为正", b.advAt(a, last) > 0f)
    }

    @Test
    fun `half width punctuation is still forbidden at line start`() {
        assertTrue(CharClass.isNoStart(CharClass.classify('｡'), '｡', strict = true))
        assertTrue(CharClass.isNoStart(CharClass.classify('｣'), '｣', strict = true))
        assertTrue(CharClass.isNoEnd(CharClass.classify('｢'), '｢'))
    }

    // ── 跨切面：任何输入都不得产生倒退的 clusterX ───────────────────────

    @Test
    fun `cluster x never goes backwards for pathological inputs`() {
        val samples = listOf(
            "。".repeat(60),
            "）（".repeat(40),
            "“‘”’".repeat(30),
            "……".repeat(40),
            "——".repeat(40),
            "｡｡｡".repeat(30),
            "𠮷".repeat(50),
            "Ａ１".repeat(40),
            "a".repeat(150),
            "中A中1中，A中。1中",
        )
        listOf(FakeTextRuler(em), FakeTextRuler(em, fullWidthPunctScale = 0.3f)).forEach { r ->
            listOf(6f, 12f, 20f).forEach { w ->
                samples.forEach { text ->
                    layout(text, cfg(widthEm = w, indentEm = 0f), r = r).forEach { line ->
                        for (i in 1 until line.clusterX.size) {
                            assertTrue(
                                "宽=${w}em ruler=$r 文本=$text 第 $i 处 clusterX 倒退",
                                line.clusterX[i] >= line.clusterX[i - 1] - LineComposer.EPS,
                            )
                        }
                    }
                }
            }
        }
    }
}
