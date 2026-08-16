package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration

/**
 * 同一次轻点手势只允许一条路径触发中央切换的仲裁门。
 *
 * 滚动 TXT 的 readOnly 文本域观察器（子项）在按下时 [claim]；父级中央观察器
 * （[readerCenterTapToToggle]）在抬起时 [consumeClaimIfAny]：若已被子项认领则跳过，
 * 保证"文本域轻点"与"父层边距轻点"互斥单路径，不会一次手势双触发 toggle。
 * 文本域观察器在手势结束（抬起/取消/重组销毁）时 [clear]。
 *
 * 纯 JVM 逻辑，无 Android 依赖，可单测（见 ReaderTapObservationTest）。
 */
internal class ReaderTapToggleGate {
    private var claimed: Boolean = false

    /** 子观察器在按下时调用：声明本次手势由文本域侧负责。 */
    fun claim() {
        claimed = true
    }

    /** 子观察器在手势结束时调用（含取消、重组销毁）。 */
    fun clear() {
        claimed = false
    }

    /** 父观察器在抬起时调用：若被子项认领则返回 true 并复位，本次父层不再触发。 */
    fun consumeClaimIfAny(): Boolean {
        val wasClaimed = claimed
        claimed = false
        return wasClaimed
    }
}

/** 中央区域判定：与三分区 tap 模式一致，仅中间 1/3 横向带视为中央（含边界）。 */
internal fun isCenterTapZone(x: Float, width: Float): Boolean =
    width > 0f && x >= width / 3f && x <= width * 2f / 3f

/**
 * 子（文本域）观察器是否应处理本次轻点：按下或最终抬起被消费（readOnly 文本域认领）
 * 才走子路径；down/up 均未被消费的轻点交给父层 [readerCenterTapToToggle] 统一处理，
 * 保证同一手势只触发一条路径、不会双 toggle。
 */
internal fun shouldChildHandleTap(
    downWasConsumed: Boolean,
    finalUpIsConsumed: Boolean,
): Boolean = downWasConsumed || finalUpIsConsumed

/**
 * 长按判定：手势总时长（最终抬起时刻 - 按下时刻）严格超过 [longPressTimeoutMillis] 即视为长按。
 * 即使 down 与 up 之间没有任何中间事件（没有 move 可更新标志位），也能直接判出长按；超时绝不回调。
 */
internal fun isLongPress(
    downTimeMillis: Long,
    upTimeMillis: Long,
    longPressTimeoutMillis: Long,
): Boolean = upTimeMillis - downTimeMillis > longPressTimeoutMillis

/**
 * 不 consume 的父级中央点击观察：正文滚动/回退分页分支的统一唤出入口。
 *
 * 仲裁规则：本观察器从不调用 [androidx.compose.ui.input.pointer.PointerInputChange.consume]，
 * 因此不会吞掉正文选择、失败重试、滚动或图片点击；只有当整段轻点手势没有被任何子项消费时
 * 才触发 [onCenterTap]。子项（正文选择、重试、翻页分区、readOnly 文本域）先消费 → 本观察器跳过；
 * 空白/间隔/边距等无人认领的轻点 → 中央唤出/隐藏。
 *
 * 拖动（滚动）期间移动事件被 scrollable 消费，[waitForUpOrCancellation] 返回 null，
 * 天然不会把滚动误判为点击。
 *
 * [gate] 非空时（滚动 TXT 分支）：抬起先仲裁子项认领，避免与文本域观察器同手势双触发。
 *
 * 回调经 [rememberUpdatedState] 透传、手势协程以稳定 key（Unit）常驻：
 * 正文每次重组（翻页/进度刷新）不会重启手势协程、不会吞掉进行中的轻点
 * （与 PagedReaderHost 的 rememberUpdatedState 方案同源）。
 */
@Composable
internal fun Modifier.readerCenterTapToToggle(
    onCenterTap: () -> Unit,
    gate: ReaderTapToggleGate? = null,
): Modifier {
    val currentOnCenterTap by rememberUpdatedState(onCenterTap)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.isConsumed) return@awaitEachGesture
            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
            // 子项（readOnly 文本域观察器）已认领本次手势 → 父层跳过，单路径触发。
            if (gate?.consumeClaimIfAny() == true) return@awaitEachGesture
            currentOnCenterTap()
        }
    }
}

/**
 * 附着在 readOnly BasicTextField 自身的中部短按观察器（非消费式）。
 *
 * 解决的问题：滚动 TXT 的 readOnly 文本域会消费按下事件，父级 [readerCenterTapToToggle]
 * 在 `down.isConsumed` 处提前返回，正文短按中央唤不出工具栏。本观察器挂在文本域自身，
 * 负责文本域内的中央短按；父层仍负责边距/空白。
 *
 * 规格：
 * - 从不调用 [PointerInputChange.consume]：不吞掉长按选字、拖动滚动、链接/已有点击；
 * - 只有单指（第二根手指按下即放弃）；
 * - 未超过 touch slop（任何被消费的移动——滚动/选字拖动——也直接放弃）；
 * - 非长按（超过 longPressTimeout 不触发，长按选字留给文本域自身处理）；
 * - 在文本域中央 1/3 横向带内（[isCenterTapZone]，边缘区留给翻页分区语义）；
 * - 最终抬起时触发（[changedToUpIgnoreConsumed]，即使 up 被字段消费也可见）。
 *
 * 双触发防护：按下即 [gate.claim]，父层抬起时 [ReaderTapToggleGate.consumeClaimIfAny]
 * 认领并跳过；本观察器在 finally 中 [ReaderTapToggleGate.clear]，覆盖抬起与取消路径。
 * 消费路由：进入手势立刻快照 down 的消费状态，最终抬起后仅当
 * [shouldChildHandleTap]（downWasConsumed || finalUp.isConsumed）成立才触发；若
 * BasicTextField 完全不消费 down/up，子观察器不触发，由父层处理，避免同一手势双 toggle。
 * 长按判定在最终抬起时用 `finalUp.uptimeMillis - downTime` 直接计算（[isLongPress]），
 * 覆盖 down 与 up 之间没有任何中间事件的长按（旧实现只在循环内更新 longPress 标志，
 * 无中间 move 时会在抬起分支先 break，导致长按漏判）。
 *
 * 需真机复核：readOnly BasicTextField 的按下/抬起消费行为、长按选字与手柄拖动、
 * 文本域轻点与父层边距轻点的互斥（单路径）是否符合预期。
 */
@Composable
internal fun Modifier.readerTextFieldCenterTapToToggle(
    onCenterTap: () -> Unit,
    gate: ReaderTapToggleGate,
): Modifier {
    val currentOnCenterTap by rememberUpdatedState(onCenterTap)
    val viewConfiguration = LocalViewConfiguration.current
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // 进入手势立刻快照按下消费状态：PointerInputChange 对象会在后续事件中被复用/改写，
            // 不能在最终抬起后再读取共享 down 状态（此时可能已不是按下那一刻的值）。
            val downWasConsumed = down.isConsumed
            gate.claim()
            try {
                val downId = down.id
                val downPosition = down.position
                val downTime = down.uptimeMillis
                var slopExceeded = false
                var up: PointerInputChange? = null
                while (up == null) {
                    val event = awaitPointerEvent()
                    // 第二根手指按下 → 放弃（只有单指）。
                    if (event.changes.any { it.id != downId && it.changedToDownIgnoreConsumed() }) break
                    val change = event.changes.firstOrNull { it.id == downId } ?: break
                    // 最终抬起（不关心消费，字段自身也会消费 up）。
                    if (change.changedToUpIgnoreConsumed()) {
                        up = change
                        break
                    }
                    // 任何被消费的事件（滚动/选区拖动等）→ 取消观察，避免拖动误判为点击。
                    if (event.changes.any { it.isConsumed }) break
                    if ((change.position - downPosition).getDistance() > viewConfiguration.touchSlop) {
                        slopExceeded = true
                    }
                }
                val finalUp = up ?: return@awaitEachGesture
                if (slopExceeded) return@awaitEachGesture
                // 完全不消费的轻点交给父层 readerCenterTapToToggle，避免同一手势双 toggle。
                if (!shouldChildHandleTap(downWasConsumed, finalUp.isConsumed)) return@awaitEachGesture
                // 长按判定直接取最终抬起时刻，覆盖 down→up 之间无中间事件的长按；超时绝不回调。
                if (isLongPress(downTime, finalUp.uptimeMillis, viewConfiguration.longPressTimeoutMillis)) {
                    return@awaitEachGesture
                }
                if (!isCenterTapZone(finalUp.position.x, size.width.toFloat())) return@awaitEachGesture
                currentOnCenterTap()
            } finally {
                gate.clear()
            }
        }
    }
}
