package com.creationreadingassistant.ui.screen.inspiration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.creationreadingassistant.data.local.entity.InspirationEntity
import androidx.compose.material3.SnackbarHostState

/**
 * 替代旧的 "list" / "detail" / "editor" 字符串。
 * 当编辑器有修改时，Editor 的 state 会携带脏状态（用于返回时判断是否弹确认）。
 */
internal sealed interface InspirationPage {
    data object List : InspirationPage
    data class Detail(val inspirationId: String) : InspirationPage
    data class Editor(
        /** 新建灵感为 null，编辑已存在灵感为其 id。 */
        val inspirationId: String? = null,
    ) : InspirationPage
}

/** 编辑器脏状态：替代散落的 Boolean (editorDirty, showUnsavedDialog)。 */
internal sealed interface EditorDirtyState {
    /** 与初始值一致。 */
    data object Clean : EditorDirtyState

    /** 字段有变化，但没有向用户请求确认。 */
    data object Dirty : EditorDirtyState

    /** 正处于「放弃未保存修改？」确认弹层中。 */
    data object ConfirmDiscard : EditorDirtyState
}

/** 删除确认：替代 pendingDeleteId (String?) + 多个 Boolean 弹层。 */
internal sealed interface ConfirmDeleteState {
    data object None : ConfirmDeleteState
    data class Pending(val inspirationId: String) : ConfirmDeleteState
}

/** 弹层可见性：替代 sortOpen / actionItemId 等 Boolean 与 String?。 */
internal sealed interface InspirationSheet {
    data object None : InspirationSheet

    /** 列表排序选择弹层。 */
    data object Sort : InspirationSheet

    /** 某条灵感的更多操作弹层。 */
    data class ItemActions(val inspirationId: String) : InspirationSheet
}

/**
 * 集中化 UI 状态：所有屏幕状态收敛到一个 data class，
 * 避免 Screen 中散列的 mutableStateOf Boolean 导致的返回顺序/状态错误。
 */
internal data class InspirationUiState(
    val page: InspirationPage = InspirationPage.List,
    val sheet: InspirationSheet = InspirationSheet.None,
    val confirmDelete: ConfirmDeleteState = ConfirmDeleteState.None,
    val editorDirty: EditorDirtyState = EditorDirtyState.Clean,

    val searchOpen: Boolean = false,
    val query: String = "",
    val typeFilter: String = "all",
    val statusFilter: String = "all",
) {
    /** 返回顺序检查：弹层 → 编辑确认 → 详情 → 列表。（true = 还有可消费的"回"动作） */
    val canConsumeBack: Boolean
        get() = sheet !is InspirationSheet.None ||
            editorDirty is EditorDirtyState.ConfirmDiscard ||
            confirmDelete !is ConfirmDeleteState.None ||
            page !is InspirationPage.List
}

/**
 * 类型化的用户动作（单向数据流）。
 * Screen 与 List/Detail/Editor 子组件只发送 ShelfAction，
 * 不直接持有状态或调用 ViewModel。
 */
internal sealed interface InspirationAction {
    /* 页面导航（Route 消费） */
    data object Back : InspirationAction
    data class OpenDetail(val inspirationId: String) : InspirationAction
    data class OpenEditor(val inspirationId: String? = null) : InspirationAction
    data object NavigateToList : InspirationAction

    /* 弹层 */
    data object OpenSortSheet : InspirationAction
    data class OpenItemActions(val inspirationId: String) : InspirationAction
    data object CloseSheet : InspirationAction

    /* 搜索 / 筛选 */
    data object ToggleSearch : InspirationAction
    data object CloseSearch : InspirationAction
    data class UpdateQuery(val query: String) : InspirationAction
    data class UpdateTypeFilter(val type: String) : InspirationAction
    data class UpdateStatusFilter(val status: String) : InspirationAction
    data object ResetFilter : InspirationAction

    /* 排序 */
    data class ChangeSort(val value: String) : InspirationAction

    /* 更多动作（从 ItemActions 弹层触发） */
    data class EditItem(val inspirationId: String) : InspirationAction
    data object CopyCurrentItem : InspirationAction
    data object OpenCurrentSource : InspirationAction
    data class RequestDelete(val inspirationId: String) : InspirationAction

    /* R5-I2：来源精确定位（临时查阅，坐标由 payload 的 locator JSON 承载） */
    data class InspectSourceLocator(
        val bookId: String,
        val locatorJson: String,
    ) : InspirationAction

    /* R5-I2：多摘录素材卡合并（列表多选后触发） */
    data class MergeToMaterialCard(val ids: List<String>) : InspirationAction

    /* 删除确认弹层 */
    data object ConfirmDeleteNow : InspirationAction
    data object CancelDelete : InspirationAction

    /* 编辑器脏状态 & 未保存确认 */
    data class EditorSetDirty(val dirty: Boolean) : InspirationAction
    data object RequestDiscardEditor : InspirationAction
    data object DiscardConfirmed : InspirationAction
    data object ContinueEditing : InspirationAction
    data class EditorSaved(val newId: String, val wasNew: Boolean) : InspirationAction

    /* 详情变体操作 */
    data class CopyVariantText(val text: String) : InspirationAction
    data class ApplyVariant(val variantId: String, val inspirationId: String) : InspirationAction
    data class DeleteVariant(val variantId: String) : InspirationAction
    data class RunInspirationAi(val inspirationId: String, val action: String) : InspirationAction

    /* 展示消息（Snackbar 触发） */
    data class ShowMessage(val text: String) : InspirationAction
}

/**
 * 跨 Screen/Route 共享 SnackbarHostState 的 CompositionLocal。
 * Route 提供（w/ SnackbarHostState），Screen 在 AppScreenScaffold 中消费。
 */
internal val LocalInspirationSnackbar: ProvidableCompositionLocal<SnackbarHostState?> =
    compositionLocalOf { null }

/** 与旧文件等价的常量与工具函数，迁移到子包统一维护。 */
internal val TYPE_OPTIONS: List<Pair<String, String>> = listOf(
    "note" to "灵感",
    "plot" to "剧情",
    "character" to "人物",
    "world" to "世界观",
    "scene" to "场景",
    "line" to "对话",
    "trope" to "设定",
    "conflict" to "冲突",
)

internal val STATUS_OPTIONS: List<Pair<String, String>> = listOf(
    "inbox" to "未整理",
    "reviewing" to "待整理",
    "usable" to "可使用",
    "polished" to "已打磨",
    "used" to "已采用",
    "archived" to "已归档",
)

internal val SORT_OPTIONS: List<Pair<String, String>> = listOf(
    "updated" to "最近更新",
    "created" to "创建时间",
    "title" to "标题",
    "source" to "来源书籍",
)

internal fun getTypeLabel(type: String?): String =
    TYPE_OPTIONS.firstOrNull { it.first == type }?.second ?: "灵感"

internal fun getStatusLabel(status: String?): String =
    STATUS_OPTIONS.firstOrNull { it.first == status }?.second ?: "未整理"

internal fun aiActionLabel(kind: String?, fallback: Map<String, String>): String =
    fallback[kind] ?: (kind ?: "候选")

internal fun parseTagInput(value: String): List<String> =
    value.split(Regex("[，,\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }

private fun javaInstant(s: String?): java.time.Instant? = runCatching {
    s?.let { java.time.Instant.parse(it) }
}.getOrNull()

internal fun formatListTime(value: String?): String {
    val instant = javaInstant(value) ?: return "时间未知"
    val zdt = instant.atZone(java.time.ZoneId.systemDefault())
    val now = java.time.LocalDate.now()
    return if (zdt.toLocalDate() == now) {
        zdt.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    } else {
        zdt.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd"))
    }
}

internal fun formatDetailTime(value: String?): String {
    val instant = javaInstant(value) ?: return "时间未知"
    return instant.atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
}

internal data class InspirationEntityRef(val updatedAt: String, val createdAt: String)

internal fun timeMillis(ref: InspirationEntityRef, key: String): Long = runCatching {
    val s = if (key == "updated") ref.updatedAt else ref.createdAt
    java.time.Instant.parse(s).toEpochMilli()
}.getOrDefault(0L)

/** 过滤/排序逻辑 —— 迁移到 InspirationUiState.filtered。 */
internal fun filterAndSortItems(
    items: List<InspirationEntity>,
    needle: String,
    typeFilter: String,
    statusFilter: String,
    sortMode: String,
    sourceOf: (InspirationEntity) -> com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo?,
    tagsOf: (InspirationEntity) -> List<String>,
): List<InspirationEntity> {
    val filtered = items.filter { item ->
        if (typeFilter != "all" && (item.type ?: "note") != typeFilter) return@filter false
        if (statusFilter != "all" && (item.status ?: "inbox") != statusFilter) return@filter false
        if (needle.isBlank()) return@filter true
        val src = sourceOf(item)
        val hay = listOf(
            item.title,
            item.body,
            src?.bookTitle,
            src?.locationLabel,
            src?.excerpt,
        ).plus(tagsOf(item)).filterNotNull().joinToString(" ").lowercase()
        hay.contains(needle)
    }
    return when (sortMode) {
        "updated" -> filtered.sortedByDescending {
            timeMillis(InspirationEntityRef(it.updated_at, it.created_at), "updated")
        }
        "created" -> filtered.sortedByDescending {
            timeMillis(InspirationEntityRef(it.updated_at, it.created_at), "created")
        }
        "title" -> filtered.sortedBy { it.title.lowercase() }
        "source" -> filtered.sortedBy { sourceOf(it)?.bookTitle?.lowercase() ?: "" }
        else -> filtered
    }
}
