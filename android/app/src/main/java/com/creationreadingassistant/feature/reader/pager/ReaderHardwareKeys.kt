package com.creationreadingassistant.feature.reader.pager

import android.view.KeyEvent

/**
 * Activity 与全屏阅读器之间的进程内按键桥；阅读器退出时 handler 必须清空。
 *
 * 按键序列语义（问题 5）：
 * - 首 DOWN（repeatCount=0）经 handler 决定是否消费；消费后同 key 的 repeat DOWN 与 UP/CANCEL
 *   都消费且不再次翻页；首 DOWN 未消费时整序列交回系统；
 * - 非音量键一律不消费；
 * - 按键交错 / CANCEL 按 key 独立跟踪，UP/CANCEL 释放，避免序列泄漏 stuck。
 */
object ReaderHardwareKeys {
    var handler: ((direction: Int) -> Boolean)? = null
        set(value) {
            field = value
            // 退出阅读器（handler 清空）时释放残留按键序列，避免 stuck。
            if (value == null) consumedKeys.clear()
        }

    /** 已消费 DOWN 的按键（keyCode）；UP/CANCEL 消费后移除。 */
    private val consumedKeys = mutableSetOf<Int>()

    /** Activity 深 seam：DOWN/UP/CANCEL 全序列都经此分发。 */
    fun dispatch(event: KeyEvent): Boolean =
        dispatch(event.action, event.keyCode, event.repeatCount)

    /** 纯参数版本（JVM 可测）：action/keyCode/repeatCount 由 [dispatch] 从 KeyEvent 提取。 */
    internal fun dispatch(action: Int, keyCode: Int, repeatCount: Int): Boolean {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> -1
            KeyEvent.KEYCODE_VOLUME_DOWN -> 1
            else -> return false
        }
        return when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (repeatCount > 0) {
                    // 自动重复：已消费的按键序列继续消费，但不再次翻页
                    keyCode in consumedKeys
                } else {
                    val handled = handler?.invoke(direction) == true
                    if (handled) consumedKeys += keyCode
                    handled
                }
            }
            // 系统取消按键以带 FLAG_CANCELED 的 ACTION_UP 到达，与普通 UP 同样消费并释放。
            KeyEvent.ACTION_UP -> consumedKeys.remove(keyCode)
            else -> false
        }
    }
}
