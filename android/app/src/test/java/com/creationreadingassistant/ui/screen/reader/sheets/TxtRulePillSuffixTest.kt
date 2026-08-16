package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.ui.viewmodel.TxtRuleScanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 规则 pill 后缀纯函数：扫描中显示「扫描中…」而非 0 章，完成显示真实章数，
 * 取消/失败可解释；未扫描且无预览时隐藏章数，禁止误显示「0章」。
 */
class TxtRulePillSuffixTest {

    @Test
    fun `running rule shows scanning label instead of zero chapters`() {
        assertEquals(
            "扫描中…",
            txtRulePillSuffix(TxtRuleScanStatus.Running("book-1", "rule-1", 0.4f), "rule-1", 0),
        )
    }

    @Test
    fun `completed rule shows real chapter count`() {
        assertEquals(
            "12章",
            txtRulePillSuffix(TxtRuleScanStatus.Completed("book-1", "rule-1", 12), "rule-1", 0),
        )
    }

    @Test
    fun `cancelled rule shows cancelled label`() {
        assertEquals(
            "已取消",
            txtRulePillSuffix(TxtRuleScanStatus.Cancelled("book-1", "rule-1"), "rule-1", 0),
        )
    }

    @Test
    fun `failed rule shows failure label`() {
        assertEquals(
            "扫描失败",
            txtRulePillSuffix(TxtRuleScanStatus.Failed("book-1", "rule-1", "磁盘错误"), "rule-1", 0),
        )
    }

    @Test
    fun `other rules fall back to preview count when available`() {
        assertEquals(
            "3章",
            txtRulePillSuffix(TxtRuleScanStatus.Running("book-1", "rule-1", null), "rule-2", 3),
        )
    }

    @Test
    fun `no status and no preview hides the count instead of showing zero`() {
        assertNull(txtRulePillSuffix(null, "rule-1", 0))
        assertNull(txtRulePillSuffix(TxtRuleScanStatus.Idle, "rule-1", 0))
    }

    @Test
    fun `idle with preview count shows preview chapters`() {
        assertEquals("5章", txtRulePillSuffix(TxtRuleScanStatus.Idle, "rule-1", 5))
    }
}
