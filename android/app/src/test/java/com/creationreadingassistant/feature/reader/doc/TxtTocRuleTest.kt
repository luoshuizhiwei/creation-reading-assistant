package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtTocRuleTest {
    @Test
    fun `numeric punctuation rule avoids decimals`() {
        assertTrue(TxtChapterDetector.isChapterTitle("1. 序幕", "num-dot"))
        assertTrue(TxtChapterDetector.isChapterTitle("12、重逢", "num-dot"))
        assertFalse(TxtChapterDetector.isChapterTitle("1.5 倍速播放", "num-dot"))
    }

    @Test
    fun `bare chinese bracket and markdown rules stay opt-in`() {
        assertTrue(TxtChapterDetector.isChapterTitle("42", "num-bare"))
        assertFalse(TxtChapterDetector.isChapterTitle("42 是答案", "num-bare"))
        assertTrue(TxtChapterDetector.isChapterTitle("三十五、归途", "cn-num-dot"))
        assertTrue(TxtChapterDetector.isChapterTitle("【第三章】", "bracketed"))
        assertFalse(TxtChapterDetector.isChapterTitle("（笑）", "bracketed"))
        assertTrue(TxtChapterDetector.isChapterTitle("## 楔子", "md-heading"))
        assertFalse(TxtChapterDetector.isChapterTitle("#话题", "md-heading"))
    }

    @Test
    fun `builtin chapter forms remain enabled for every selected rule`() {
        TxtChapterDetector.rules.forEach { rule ->
            assertTrue(rule.id, TxtChapterDetector.isChapterTitle("第12章 夜行", rule.id))
        }
    }
}
