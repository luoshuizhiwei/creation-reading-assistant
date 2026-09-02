package com.creationreadingassistant.feature.reader.pager

import android.view.MotionEvent

/**
 * P2-6：鼠标滚轮 + 蓝牙手柄摇杆 → 翻页桥接。
 *
 * 与 [ReaderHardwareKeys] 同语义：仅阅读器前台（handler != null）时生效；
 * handler 清空时由 [ReaderHardwareKeys] 负责调用 [reset] 清理内部状态，
 * 避免退出阅读器后摇杆/滚动残留锁影响下次进入。
 *
 * 输入来源与映射：
 * - 鼠标垂直滚轮（AXIS_VSCROLL）：正值（向上滚）→ 上一页；负值 → 下一页
 * - 鼠标水平滚轮（AXIS_HSCROLL）：负值（向左滚）→ 上一页；正值 → 下一页
 * - 摇杆 Y（AXIS_Y）：正值（向下推）→ 下一页；负值 → 上一页
 * - 摇杆 X（AXIS_X）：正值（向右推）→ 下一页；负值 → 上一页
 *
 * 防抖/边界：
 * - 滚轮：每 notch（= 12 axis 单位）触发一次，快速连续 notch 累积时按 notch 数整倍触发。
 * - 摇杆：死区 0.4；超过死区只触发一次（方向锁），必须回到死区内再推才触发第二次，
 *   避免用户一直推着摇杆整本书乱翻。
 */
object ReaderScrollBridge {
    private const val SCROLL_THRESHOLD = 12f // 典型鼠标滚轮 notch = ±12
    private const val JOYSTICK_DEADZONE = 0.4f

    private var vAccum = 0f
    private var hAccum = 0f
    private var joyVerticalLock = 0   // 0=未锁；-1=已触发prev；1=已触发next
    private var joyHorizontalLock = 0

    /** 阅读器退出时由 [ReaderHardwareKeys] 调用，清零所有方向锁与累积器。 */
    fun reset() {
        vAccum = 0f
        hAccum = 0f
        joyVerticalLock = 0
        joyHorizontalLock = 0
    }

    /** Activity seam：在 onGenericMotionEvent 中转发。 */
    fun dispatchMotion(event: MotionEvent): Boolean {
        val handler = ReaderHardwareKeys.handler ?: return false
        var handled = false

        // 1) 垂直滚轮
        val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        if (vScroll != 0f) {
            vAccum += vScroll
            while (vAccum >= SCROLL_THRESHOLD) {
                vAccum -= SCROLL_THRESHOLD
                if (handler(-1)) handled = true
            }
            while (vAccum <= -SCROLL_THRESHOLD) {
                vAccum += SCROLL_THRESHOLD
                if (handler(1)) handled = true
            }
        } else if (event.historySize == 0 && event.action == MotionEvent.ACTION_SCROLL) {
            vAccum = 0f
        }

        // 2) 水平滚轮
        val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
        if (hScroll != 0f) {
            hAccum += hScroll
            while (hAccum <= -SCROLL_THRESHOLD) {
                hAccum += SCROLL_THRESHOLD
                if (handler(-1)) handled = true
            }
            while (hAccum >= SCROLL_THRESHOLD) {
                hAccum -= SCROLL_THRESHOLD
                if (handler(1)) handled = true
            }
        } else if (event.historySize == 0 && event.action == MotionEvent.ACTION_SCROLL) {
            hAccum = 0f
        }

        // 3) 摇杆 Y
        val joyY = event.getAxisValue(MotionEvent.AXIS_Y)
        when {
            joyY >= JOYSTICK_DEADZONE -> {
                if (joyVerticalLock != 1) {
                    if (handler(1)) handled = true
                    joyVerticalLock = 1
                }
            }
            joyY <= -JOYSTICK_DEADZONE -> {
                if (joyVerticalLock != -1) {
                    if (handler(-1)) handled = true
                    joyVerticalLock = -1
                }
            }
            else -> joyVerticalLock = 0
        }

        // 4) 摇杆 X
        val joyX = event.getAxisValue(MotionEvent.AXIS_X)
        when {
            joyX >= JOYSTICK_DEADZONE -> {
                if (joyHorizontalLock != 1) {
                    if (handler(1)) handled = true
                    joyHorizontalLock = 1
                }
            }
            joyX <= -JOYSTICK_DEADZONE -> {
                if (joyHorizontalLock != -1) {
                    if (handler(-1)) handled = true
                    joyHorizontalLock = -1
                }
            }
            else -> joyHorizontalLock = 0
        }

        return handled
    }
}
