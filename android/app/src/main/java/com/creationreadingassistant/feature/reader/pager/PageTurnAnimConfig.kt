package com.creationreadingassistant.feature.reader.pager

/**
 * 翻页动画参数。从 [com.creationreadingassistant.data.settings.SettingsStore] 读取后注入 pager。
 *
 * @param effect 翻页效果：`none` 直接切页 / `fade` 交叉淡入 / `slide` 并排滑动 /
 *               `cover` 当前页揭开露出下层（设置层把历史 `curl` 归一为 `cover`）。
 * @param speed  时长系数（ms）：单页满位移时的基础时长。实际时长 = `speed × 剩余位移比例`，
 *               屏越宽/拖得越满，翻页速度越恒定。值越小越快。
 */
data class PageTurnAnimConfig(
    val effect: String = "none",
    val speed: Float = 400f,
)
