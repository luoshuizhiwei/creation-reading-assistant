package com.creationreadingassistant.feature.reader.pager

import android.view.KeyEvent

/**
 * Activity 与全屏阅读器之间的进程内按键桥；阅读器退出时 handler 必须清空。
 *
 * 按键序列语义（问题 5）：
 * - 首 DOWN（repeatCount=0）经 handler 决定是否消费；消费后同 key 的 repeat DOWN 与 UP/CANCEL
 *   都消费且不再次翻页；首 DOWN 未消费时整序列交回系统；
 * - 翻页键集合见 [directionOf]（音量 / PAGE_UP/DOWN / SPACE / DPAD 左右），
 *   其余按键一律不消费；
 * - 鼠标滚轮走 [dispatchScroll]：由 Activity 的 `dispatchGenericMotionEvent` 以
 *   `MotionEvent.AXIS_VSCROLL` 轴值调用（滚轮是 MotionEvent，没有 DOWN/UP 序列），
 *   一次滚动一次翻页；
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

    /** 键码 → 翻页方向（-1 上一页 / +1 下一页）；不在表内返回 null（不消费）。 */
    private fun directionOf(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT -> -1
        KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        -> 1
        else -> null
    }

    /** Activity 深 seam：DOWN/UP/CANCEL 全序列都经此分发。 */
    fun dispatch(event: KeyEvent): Boolean =
        dispatch(event.action, event.keyCode, event.repeatCount)

    /** 媒体按钮没有音量键的 DOWN/UP 序列，直接复用当前阅读器方向处理器。 */
    internal fun triggerDirection(direction: Int): Boolean = handler?.invoke(direction) == true

    /**
     * 鼠标滚轮（纯参数版本，JVM 可测）：向下滚 = 下一页。
     * 滚动事件没有「序列」概念，不登记 [consumedKeys]。
     */
    internal fun dispatchScroll(verticalScroll: Float): Boolean = when {
        verticalScroll > 0f -> triggerDirection(1)
        verticalScroll < 0f -> triggerDirection(-1)
        else -> false
    }

    /** 纯参数版本（JVM 可测）：action/keyCode/repeatCount 由 [dispatch] 从 KeyEvent 提取。 */
    internal fun dispatch(action: Int, keyCode: Int, repeatCount: Int): Boolean {
        val direction = directionOf(keyCode) ?: return false
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
