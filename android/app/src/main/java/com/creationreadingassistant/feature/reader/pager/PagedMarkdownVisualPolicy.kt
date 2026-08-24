package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.layout.BlockRole

/** 分页 Markdown 块的纯视觉策略，保持 Canvas 绘制判断可在 JVM 中验证。 */
internal object PagedMarkdownVisualPolicy {

    data class Style(
        val drawPanel: Boolean = false,
        val monospace: Boolean = false,
        val emphasized: Boolean = false,
    )

    fun styleFor(role: BlockRole): Style = when (role) {
        BlockRole.TABLE_HEADER -> Style(
            drawPanel = true,
            monospace = true,
            emphasized = true,
        )
        BlockRole.TABLE_ROW -> Style(
            drawPanel = true,
            monospace = true,
        )
        else -> Style()
    }
}
