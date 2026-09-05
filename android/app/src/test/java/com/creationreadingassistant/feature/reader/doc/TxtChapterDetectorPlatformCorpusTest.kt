package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨平台章节标题语料验证：起点 / 晋江 / 刺猬猫（轻小说）/ 番茄 / 飞卢 / 少年梦 /
 * 传统章回体 / 英文原样。标题必须命中内置（builtin）识别；正文形态必须不命中。
 *
 * 这是对「市面上大多数目录规则」的整合性回归防线：每接入一种平台写法就补进语料，
 * 防止后续收紧正则时悄悄丢掉某类平台。
 */
class TxtChapterDetectorPlatformCorpusTest {

    // ── 起点系（第X章 / 卷：章 / 序章 楔子 / 感言）──────────────────────
    private val qidian = listOf(
        "第一章 山边小村",
        "第1章 穿越",
        "第一千三百零五章 大战落幕",
        "第1204章 风起于青萍之末",
        "第001章 起点",
        "第零章 序",
        "序章",
        "序章：十年之前",
        "楔子",
        "第一章：初见",
        "第1章：初见",
        "第一卷 少年游",
        "第一卷：风起",
        "第1卷 第1章 起",
        "【第一章】风起",
        "第一章 标题（修）",
        "上架感言",
        "完本感言",
        "新书感言",
    )

    // ── 晋江 / 番茄 / 飞卢（VIP 后缀 / 求票尾巴 / 全角数字）─────────────
    private val jinjiangFanqie = listOf(
        "第2章(vip)",
        "第3章（VIP）",
        "第12章 相遇（求收藏）",
        "第１章 全角数字也不怕",
        "第两百章 双喜临门",
        "第壹佰章 仿古数字",
    )

    // ── 刺猬猫 / 轻小说系（话 / 最终话 / 间章 / 幕间 / 幕 / 折）──────────
    private val ciweimao = listOf(
        "第1话 转生之后的事",
        "第一话",
        "最终话",
        "最终话 明天的日记",
        "最终章",
        "最終話",
        "间章",
        "间章 某个平常的午后",
        "幕间",
        "幕间 王都的钟声",
        "第2幕 开演",
        "第3折 惊变",
        "番外",
        "番外一",
        "番外：暑期特别篇",
        "尾声",
        "终章",
    )

    // ── 传统章回体 / 武侠（回目对仗 / 卷部篇册省略 / 上中下分组）─────────
    private val chaptered = listOf(
        "第一回 宴桃园豪杰三结义 斩黄巾英雄首立功",
        "第廿三回 小旋风柴进 Door客",
        "卷一 江湖夜雨",
        "卷之十二 大结局前夜",
        "上部 风云际会",
        "中部 暗流涌动",
        "下册 尘埃落定",
        "第四节 最后一课",
    )

    // ── 英文 ────────────────────────────────────────────────────────────
    private val english = listOf(
        "Chapter 1 The Beginning",
        "CHAPTER IV",
        "chapter 12",
    )

    @Test
    fun `qidian style titles are all recognized`() {
        qidian.forEach { title ->
            assertTrue("起点系标题未识别：$title", TxtChapterDetector.isChapterTitle(title))
        }
    }

    @Test
    fun `jinjiang fanqie style titles are all recognized`() {
        jinjiangFanqie.forEach { title ->
            assertTrue("晋江/番茄系标题未识别：$title", TxtChapterDetector.isChapterTitle(title))
        }
    }

    @Test
    fun `ciweimao light novel titles are all recognized`() {
        ciweimao.forEach { title ->
            assertTrue("刺猬猫/轻小说系标题未识别：$title", TxtChapterDetector.isChapterTitle(title))
        }
    }

    @Test
    fun `classic chaptered titles are all recognized`() {
        chaptered.forEach { title ->
            assertTrue("章回体标题未识别：$title", TxtChapterDetector.isChapterTitle(title))
        }
    }

    @Test
    fun `english titles are all recognized`() {
        english.forEach { title ->
            assertTrue("英文标题未识别：$title", TxtChapterDetector.isChapterTitle(title))
        }
    }

    // ── 反向语料：这些正文行绝不能被认成标题 ─────────────────────────────
    private val bodyLines = listOf(
        "楔子钉进了木头缝里",
        "序幕拉开了",
        "最终章节里写到主角黑化了",
        "间章的两种写法都值得学",
        "上架感言写得情真意切",
        "第一节课 已经开始十分钟了",
        "这一部分讲完再回家",
        "部队在凌晨集结",
        "1. 他说今天天气很好",
        "一、那天的雨下了很久",
        "第两百章之后的内容更精彩",
        "话说天下大势，分久必合",
    )

    @Test
    fun `body lines are never recognized as titles`() {
        bodyLines.forEach { line ->
            assertFalse("正文行被误判为标题：$line", TxtChapterDetector.isChapterTitle(line))
        }
    }
}
