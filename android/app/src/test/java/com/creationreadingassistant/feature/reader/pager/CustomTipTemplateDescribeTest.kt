package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [CustomTipPlaceholder.describeTemplate] 是设置页折叠行的人话回显。
 *
 * 关键点：它**不吞未知占位符**。回显只负责把认得的 token 换成中文，
 * 拒绝非法模板是 [CustomTipPlaceholder.isValid] 的职责。
 */
class CustomTipTemplateDescribeTest {

    @Test
    fun `single token becomes its chinese label`() {
        assertEquals("章节名", CustomTipPlaceholder.describeTemplate("{ChapterTitle}"))
        assertEquals("书名 · 电量", CustomTipPlaceholder.describeTemplate("{BookName} · {BatteryPercent}"))
    }

    @Test
    fun `multiple tokens and literal text are preserved`() {
        assertEquals(
            "第 3 页：当前页 / 本章总页（本章余页）",
            CustomTipPlaceholder.describeTemplate("第 3 页：{PageIndex} / {PageSize}（{PageRemaining}）"),
        )
    }

    @Test
    fun `unknown placeholder is left verbatim instead of being swallowed`() {
        assertEquals("未知 {Nope} 结尾", CustomTipPlaceholder.describeTemplate("未知 {Nope} 结尾"))
    }

    @Test
    fun `empty and token free templates round trip`() {
        assertEquals("", CustomTipPlaceholder.describeTemplate(""))
        assertEquals("纯文字页眉", CustomTipPlaceholder.describeTemplate("纯文字页眉"))
    }

    @Test
    fun `every placeholder describes to its own label`() {
        for (ph in CustomTipPlaceholder.entries) {
            assertEquals(ph.label, CustomTipPlaceholder.describeTemplate(ph.token))
        }
    }
}
