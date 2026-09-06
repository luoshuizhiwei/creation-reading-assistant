package com.creationreadingassistant.ui.screen.reader.tts.engine.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sec-MS-GEC 令牌算法契约（与 rany2/edge-tts 社区逆向实现逐位一致）。
 * 期望值由独立实现（Python hashlib）对固定时钟预计算，防止算法漂移。
 */
class EdgeTtsDrmTest {

    @Test
    fun `固定时刻的令牌与独立实现向量一致`() {
        // unix=1700000000，窗口对齐到 1699999800 → ticks=133444734000000000
        assertEquals(
            "42301B335578FEFDAE2637DED1ABD614505D432559EC08032B82048483726AFF",
            EdgeTtsDrm.secMsGecToken(unixSeconds = 1_700_000_000L),
        )
    }

    @Test
    fun `同一窗口内令牌稳定`() {
        // unix=1700000000 与 1700000001+ 落在同一 300s 窗口（对齐到 1699999800）
        assertEquals(
            EdgeTtsDrm.secMsGecToken(1_700_000_000L),
            EdgeTtsDrm.secMsGecToken(1_700_000_099L),
        )
    }

    @Test
    fun `跨窗口令牌变化且始终为64位大写十六进制`() {
        assertNotEquals(
            EdgeTtsDrm.secMsGecToken(1_700_000_000L),
            EdgeTtsDrm.secMsGecToken(1_700_000_300L),
        )
        val token = EdgeTtsDrm.secMsGecToken(EdgeTtsDrm.nowUnixSeconds())
        assertEquals(64, token.length)
        assertTrue(token.all { it.isDigit() || it in 'A'..'F' })
    }

    private fun assertNotEquals(a: Any, b: Any) {
        org.junit.Assert.assertNotEquals(a, b)
    }
}
