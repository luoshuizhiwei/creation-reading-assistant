package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TXT 章节识别的增强样式（参考上游 legado txtTocRule.json 改写）。
 *
 * 与 [TxtChapterDetectorTest] 相同的立场：误报用例比正报用例更值得写。
 * 这里的负例大多来自「单位字与下一字成词」（部分/回合/集合/节课/话音/册封）和
 * 「专名被当普通词用」（楔子钉进木头、序幕拉开、卷起千堆雪）两类真实陷阱——
 * 其中「前言不搭后语地说」这类在旧实现上就会误报，是回归用例。
 *
 * 两条既定决策不许动摇，本文件也在守它们：
 * - 不认裸数字开头（「1. xxx」「一、xxx」），大写数字入表后尤须防「壹万块钱」；
 * - 平均章节过短即整体作废，这是负向断言天然不完备时的最后防线。
 */
class TxtChapterDetectorEnhancedTest {

    private fun body(n: Int = 600) = "正文内容。".repeat(n / 5)

    // ── 新样式：应当识别 ────────────────────────────────────────────────

    @Test
    fun `recognizes volume and booklet groupings`() {
        listOf(
            "第三册",
            "第１２册　风云再起",
            "上卷",
            "下册",
            "上部",
            "中部　乱世群像",
            "上卷　潜龙在渊",
            "卷之一",
            "卷之十二　定风波",
            "册一",
            "篇二 旧事",
        ).forEach {
            assertTrue("应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `recognizes named special sections`() {
        listOf(
            "序",
            "序幕",
            "终幕",
            "外传",
            "外传：少年游",
            "番外一",
            "番外篇二　婚后日常",
            "番外　重逢",
            "正文",
            "正文 第一章",
            "正文　第十二章　风波再起",
            "楔子　雪夜惊变",
        ).forEach {
            assertTrue("应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `recognizes uppercase chinese and circle numerals`() {
        listOf(
            "第壹章",
            "第贰拾叁回　夜袭粮仓",
            "第肆拾玖章　围城",
            "第壹仟章",
            "第一○三章",
            "第二○八章　黎明之前",
        ).forEach {
            assertTrue("应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `recognizes hua and chang units when separated or alone`() {
        listOf(
            "第一话",
            "第０３话　转学生",
            "第六话　音讯",
            "第三场",
            "第十二场　法庭风云",
            "第三十话",
        ).forEach {
            assertTrue("应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `recognizes suffix units even when subtitle starts with a guarded char`() {
        // 负向断言只看紧邻字符：分隔符之后再出现排除字必须放行
        listOf(
            "第三部　分道扬镳",
            "第五回　合浦还珠",
            "第八集　和谈",
            "第七篇　张府疑云",
            "第一节　初入江湖",
            "第三节",
            "第四部",
            "第九集",
        ).forEach {
            assertTrue("应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `recognizes bracketed headings`() {
        listOf(
            "【第一章】风起",
            "【第十二回】三打祝家庄",
            "【序章】",
            "【第叁话】初雪",
        ).forEach {
            assertTrue("应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    // ── 误报陷阱：绝不能识别 ────────────────────────────────────────────

    @Test
    fun `rejects narrative lines where unit char merges into a word`() {
        listOf(
            "第三部分内容如下",
            "第三回合结束了",
            "第五集合并播出",
            "第一节课上",
            "第１２节课改到下午",
            "第叁回合较量开始了",
            "第一回去奶奶家的路上",
            "第三回来晚了被罚站",
            "第一回事情办得漂亮",
            "第二集和第三集连播",
            "第四部队友全都阵亡了",
            "第二部赛程刚过半",
            "第五部游戏宣布跳票",
        ).forEach {
            assertFalse("不应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `rejects weak units followed directly by prose`() {
        listOf(
            "第一话我早就看过了",
            "第三话说的是什么来着",
            "第一话音质太差了",
            "第二场比赛输得很惨",
        ).forEach {
            assertFalse("不应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `rejects special names used as ordinary words`() {
        listOf(
            "序幕拉开了",
            "序章节奏太慢了",
            "前言不搭后语地说",
            "楔子钉进了木头缝里",
            "尾声部分被删掉了",
            "外传得沸沸扬扬",
            "终章之后再无更新",
            "后记里他提到了母亲",
            "引子只是个障眼法",
            "大结局提前被剧透了",
            "番外一直没有更新",
            "番外篇幅太短了",
            "正文完",
            "正文里第一章写得最好",
            "正文写得还不如序言",
        ).forEach {
            assertFalse("不应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `rejects grouping words used as ordinary words`() {
        listOf(
            "上册翻开了第一页",
            "中部地区连降暴雨",
            "册封大典开始了",
            "卷起千堆雪",
            "部队开进了城",
            "篇幅有限",
            "一卷卫生纸用完了",
            "这套试卷之一被泄露了",
        ).forEach {
            assertFalse("不应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `rejects ordinal idioms after di`() {
        listOf(
            "第二天他就后悔了",
            "第一时间赶到了现场",
            "第三者才是赢家",
            "第一百货大楼失火了",
            "第一千零一夜的故事",
        ).forEach {
            assertFalse("不应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `still rejects bare numbered lines even with uppercase numerals`() {
        // 既定决策：不认裸数字开头。大写数字入表后这条线不能松。
        listOf(
            "一、开会时间与地点",
            "1. 他说完就走了",
            "二〇二六年七月二十七日",
            "壹万块钱不翼而飞",
        ).forEach {
            assertFalse("不应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    @Test
    fun `rejects bracketed non headings and mid sentence mentions`() {
        listOf(
            "【广告】本书实体版热卖中",
            "【第一名】究竟是谁",
            "他翻到第三章看了起来",
        ).forEach {
            assertFalse("不应识别为章节标题：$it", TxtChapterDetector.isChapterTitle(it))
        }
    }

    // ── 整篇识别 ────────────────────────────────────────────────────────

    @Test
    fun `detects volume grouping mixed with chapters keeping continuous offsets`() {
        val text = buildString {
            append("第一卷 风云乍起\n")
            append("第一章 起\n").append(body()).append("\n")
            append("第二章 承\n").append(body()).append("\n")
            append("第二卷 雨打浮萍\n")
            append("第三章 转\n").append(body())
        }
        val chapters = TxtChapterDetector.detect(text)
        assertEquals(
            listOf("第一卷 风云乍起", "第一章 起", "第二章 承", "第二卷 雨打浮萍", "第三章 转"),
            chapters.map { it.title },
        )
        assertEquals(0, chapters[0].startOffset)
        for (i in 0 until chapters.size - 1) {
            assertEquals(chapters[i].endOffset, chapters[i + 1].startOffset)
        }
        assertEquals(text.length, chapters.last().endOffset)
    }

    @Test
    fun `detects fullwidth numbered chapters end to end`() {
        val text = "第１章　初见\n" + body() + "\n第２章　重逢\n" + body()
        val chapters = TxtChapterDetector.detect(text)
        assertEquals(listOf("第１章　初见", "第２章　重逢"), chapters.map { it.title })
    }

    @Test
    fun `detects special sections as chapters`() {
        val text = buildString {
            append("楔子\n").append(body()).append("\n")
            append("第一章 起\n").append(body()).append("\n")
            append("番外一\n").append(body())
        }
        val chapters = TxtChapterDetector.detect(text)
        assertEquals(listOf("楔子", "第一章 起", "番外一"), chapters.map { it.title })
    }

    @Test
    fun `bare numbered lines never become chapters`() {
        val text = "1、开会时间与地点\n" + body(2000) + "\n2、参会人员名单\n" + body(2000)
        val chapters = TxtChapterDetector.detect(text)
        assertEquals(1, chapters.size)
        assertEquals("全文", chapters[0].title)
    }

    @Test
    fun `sanity check still discards dense pseudo toc of new styles`() {
        // 新样式（弱单位独占整行）如果整片连着出现、几乎没有正文，仍要整体作废
        val toc = (1..30).joinToString("\n") { "第${it}话" }
        val chapters = TxtChapterDetector.detect(toc)
        assertEquals(1, chapters.size)
        assertEquals("全文", chapters[0].title)
    }
}
