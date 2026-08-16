package com.creationreadingassistant.feature.reader.pager

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音量键翻页按键序列验收（问题 5）：
 * - 首 DOWN 若 handler 返回 true，则同 key repeat DOWN 和 UP 都消费且不再次翻页、不交系统；
 * - 首 DOWN 返回 false 时整序列交系统；
 * - 非音量键不消费；
 * - 按键交错独立跟踪，后续新序列不残留。
 */
class ReaderHardwareKeysTest {

    private fun down(key: Int, repeat: Int = 0) =
        ReaderHardwareKeys.dispatch(KeyEvent.ACTION_DOWN, key, repeat)

    private fun up(key: Int) =
        ReaderHardwareKeys.dispatch(KeyEvent.ACTION_UP, key, 0)

    @Test
    fun `首 DOWN 消费后 repeat 与 UP 都消费且不再次翻页`() {
        var pages = 0
        ReaderHardwareKeys.handler = { pages += 1; true }
        try {
            assertTrue(down(KeyEvent.KEYCODE_VOLUME_UP))
            assertEquals(1, pages)
            // 自动重复：消费但不再次翻页
            assertTrue(down(KeyEvent.KEYCODE_VOLUME_UP, repeat = 1))
            assertEquals(1, pages)
            // UP 消费，不交系统调音量
            assertTrue(up(KeyEvent.KEYCODE_VOLUME_UP))
            assertEquals(1, pages)
        } finally {
            ReaderHardwareKeys.handler = null
        }
    }

    @Test
    fun `首 DOWN 未消费时整序列交系统`() {
        var invoked = 0
        ReaderHardwareKeys.handler = { invoked += 1; false }
        try {
            assertFalse(down(KeyEvent.KEYCODE_VOLUME_DOWN))
            assertFalse(down(KeyEvent.KEYCODE_VOLUME_DOWN, repeat = 1))
            assertFalse(up(KeyEvent.KEYCODE_VOLUME_DOWN))
            // repeat/UP 不再调 handler
            assertEquals(1, invoked)
        } finally {
            ReaderHardwareKeys.handler = null
        }
    }

    @Test
    fun `非音量键不消费也不调 handler`() {
        var invoked = 0
        ReaderHardwareKeys.handler = { invoked += 1; true }
        try {
            assertFalse(down(KeyEvent.KEYCODE_BACK))
            assertFalse(up(KeyEvent.KEYCODE_BACK))
            assertEquals(0, invoked)
        } finally {
            ReaderHardwareKeys.handler = null
        }
    }

    @Test
    fun `交错按键各自独立消费不互相干扰`() {
        var pages = 0
        ReaderHardwareKeys.handler = { pages += 1; true }
        try {
            assertTrue(down(KeyEvent.KEYCODE_VOLUME_UP))
            assertTrue(down(KeyEvent.KEYCODE_VOLUME_DOWN))
            assertEquals(2, pages)
            assertTrue(up(KeyEvent.KEYCODE_VOLUME_UP))
            assertTrue(up(KeyEvent.KEYCODE_VOLUME_DOWN))
            // 新序列仍可翻页（无 stuck）
            assertTrue(down(KeyEvent.KEYCODE_VOLUME_UP))
            assertEquals(3, pages)
            assertTrue(up(KeyEvent.KEYCODE_VOLUME_UP))
        } finally {
            ReaderHardwareKeys.handler = null
        }
    }

    @Test
    fun `退出阅读器清空 handler 后按键交回系统`() {
        ReaderHardwareKeys.handler = { true }
        assertTrue(down(KeyEvent.KEYCODE_VOLUME_UP))
        // 退出阅读器：handler 清空，残留序列一并释放
        ReaderHardwareKeys.handler = null
        assertFalse(up(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(down(KeyEvent.KEYCODE_VOLUME_UP))
    }
}
