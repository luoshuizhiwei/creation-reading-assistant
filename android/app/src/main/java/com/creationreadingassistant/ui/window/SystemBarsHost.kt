package com.creationreadingassistant.ui.window

import java.util.ArrayDeque

/**
 * 系统栏策略宿主（纯逻辑，JVM 可测）。
 *
 * 单一写入点：所有 WindowInsetsControllerCompat 写入都经 [apply] 回调执行，
 * Compose 侧 [SystemBarsEffect] 只负责把策略注册进宿主。
 *
 * 防闪条语义：
 * - [setBase]（全局 normal）在覆盖激活时只保存、不写入 —— 阅读器内切主题不闪条；
 * - [updateTop]（覆盖策略变化，如切纸色）原位替换、不经过 base —— 不产生中间态；
 * - [pop] 回退到上一个覆盖或 base（即 normal），绝不「show all」。
 */
internal class SystemBarsHost(
    private val apply: (SystemBarsPolicy) -> Unit,
) {
    private val stack = ArrayDeque<SystemBarsPolicy>()
    private var base: SystemBarsPolicy = SystemBarsPolicy.normal(appDark = false)

    /** 设置全局基础策略；仅在无覆盖时立即写入。 */
    fun setBase(policy: SystemBarsPolicy) {
        base = policy
        if (stack.isEmpty()) apply(policy)
    }

    /** 覆盖策略入栈并立即生效（阅读器进入）。 */
    fun push(policy: SystemBarsPolicy) {
        stack.addLast(policy)
        apply(policy)
    }

    /** 原位替换栈顶覆盖（阅读器内切纸色/沉浸开关）；栈空时忽略（首帧注册由 push 负责）。 */
    fun updateTop(policy: SystemBarsPolicy) {
        if (stack.isNotEmpty()) {
            stack.removeLast()
            stack.addLast(policy)
            apply(policy)
        }
    }

    /** 移除最近一次覆盖，回退到上一个覆盖或 base。 */
    fun pop() {
        if (stack.isNotEmpty()) stack.removeLast()
        apply(stack.lastOrNull() ?: base)
    }

    /** 当前生效策略。 */
    fun effective(): SystemBarsPolicy = stack.lastOrNull() ?: base

    /** 重申当前策略（ON_RESUME / OEM 临时显示后恢复）。 */
    fun reassert() {
        apply(effective())
    }
}
