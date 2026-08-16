package com.creationreadingassistant.ui.screen.inspiration

import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.viewmodel.InspirationItemsState

/**
 * 详情页实体解析结果：
 * - [Loading]：列表尚未首次 emission，或刚保存的草稿 id 尚未被列表 flow 观察到（短暂未解析）；
 * - [Found]：持久 id 已在列表中命中；
 * - [NotFound]：列表已加载但 id 不存在且无 pending，Route 可安全返回列表/提示错误。
 */
internal sealed interface DetailResolution {
    data object Loading : DetailResolution
    data class Found(val entity: InspirationEntity) : DetailResolution
    data object NotFound : DetailResolution

    /** 顶部动作（编辑/更多）仅在持久 id 命中后可用；Loading/NotFound 一律禁用。 */
    val actionsEnabled: Boolean
        get() = this is Found
}

/** 详情页对 [inspirationId] 的纯解析：Route 不再用空 Box + 自行猜 firstLoad。 */
internal fun resolveDetail(
    inspirationId: String,
    itemsState: InspirationItemsState,
    pendingSavedIds: Set<String>,
): DetailResolution = when (itemsState) {
    is InspirationItemsState.Loading -> DetailResolution.Loading
    is InspirationItemsState.Loaded -> {
        val found = itemsState.items.firstOrNull { it.id == inspirationId }
        when {
            found != null -> DetailResolution.Found(found)
            inspirationId in pendingSavedIds -> DetailResolution.Loading
            else -> DetailResolution.NotFound
        }
    }
}
