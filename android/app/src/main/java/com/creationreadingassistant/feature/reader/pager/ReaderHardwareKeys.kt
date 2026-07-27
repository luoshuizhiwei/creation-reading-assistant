package com.creationreadingassistant.feature.reader.pager

import android.view.KeyEvent

/** Activity 与全屏阅读器之间的进程内按键桥；阅读器退出时 handler 必须清空。 */
object ReaderHardwareKeys {
    var handler: ((direction: Int) -> Boolean)? = null
    private var lastHandledAt = 0L

    fun dispatch(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> -1
            KeyEvent.KEYCODE_VOLUME_DOWN -> 1
            else -> return false
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastHandledAt < 600L) return true
        val handled = handler?.invoke(direction) == true
        if (handled) lastHandledAt = now
        return handled
    }
}
