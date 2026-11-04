package com.creationreadingassistant.ui.viewmodel

/**
 * 任务93-v1-A：灵感轻量分类的纯策略层。
 *
 * 分类目录来自 `TaxonomyRepository.observeCategories()` 的活动字典；灵感只把分类 id
 * 存进 `payload.categoryIds`，界面按 id 显示名称。灵感分类与书籍分类（BookCategory
 * 关联表）无关，这里不做任何书籍分类写入。
 *
 * 本文件刻意不依赖 Android/Compose/Room，让 ViewModel、界面和 JVM 测试共用同一判定。
 */

/** 编辑器里的一枚分类胶囊；available=false 表示旧 id 已不在活动目录，只提示、不静默删除。 */
data class InspirationCategoryOption(
    val id: String,
    val name: String,
    val available: Boolean,
)

/** 列表筛选胶囊：带该分类下的条目数。 */
data class InspirationCategoryFilterOption(
    val id: String,
    val name: String,
    val count: Int,
    val available: Boolean,
)

internal fun unknownCategoryLabel(id: String): String = "不可用分类（${id.take(8)}）"

/**
 * 编辑保存时的分类合并：
 * - draftIds 为 null = 用户没有改过分类，原样保留旧值（即使草稿默认是空列表）；
 * - draftIds 非 null = 采用用户明确选择；空列表表示明确清空。
 */
internal fun resolveDraftCategoryIds(
    draftIds: List<String>?,
    previousIds: List<String>,
): List<String> = draftIds?.distinct() ?: previousIds

/** 列表分类筛选谓词：all 表示不过滤；其余要求条目包含该分类 id。 */
internal fun matchesCategoryFilter(categoryIds: List<String>, filterId: String): Boolean =
    filterId == "all" || filterId in categoryIds

/**
 * 编辑器胶囊：已选分类按选择顺序排列；活动目录内显示真实名称，
 * 旧 id 保留并标记不可用，用户仍可点击移除。
 */
internal fun inspirationCategoryOptions(
    selectedIds: List<String>,
    available: List<Pair<String, String>>,
): List<InspirationCategoryOption> {
    val names = available.toMap()
    return selectedIds.distinct().map { id ->
        val name = names[id]
        InspirationCategoryOption(
            id = id,
            name = name ?: unknownCategoryLabel(id),
            available = name != null,
        )
    }
}

/**
 * 列表筛选可选项：只统计条目里真实出现过的分类，按首次出现顺序；
 * 名称优先取活动目录，旧 id 以不可用标签保留。
 */
internal fun inspirationCategoryFilterOptions(
    itemCategoryIds: List<List<String>>,
    available: List<Pair<String, String>>,
): List<InspirationCategoryFilterOption> {
    val names = available.toMap()
    val counts = LinkedHashMap<String, Int>()
    itemCategoryIds.forEach { ids ->
        ids.distinct().forEach { id -> counts[id] = (counts[id] ?: 0) + 1 }
    }
    return counts.map { (id, count) ->
        val name = names[id]
        InspirationCategoryFilterOption(
            id = id,
            name = name ?: unknownCategoryLabel(id),
            count = count,
            available = name != null,
        )
    }
}

/** 详情/卡片显示用的分类名称投影；未知 id 显示不可用标签，不隐藏。 */
internal fun inspirationCategoryNames(
    ids: List<String>,
    available: List<Pair<String, String>>,
): List<String> {
    val names = available.toMap()
    return ids.distinct().map { names[it] ?: unknownCategoryLabel(it) }
}
