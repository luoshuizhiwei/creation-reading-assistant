package com.creationreadingassistant.ui.viewmodel

import kotlinx.serialization.Serializable

/**
 * R5-I1/I2：素材（灵感）的采用去向与多来源聚合的**纯数据操作**。
 *
 * 全部走 `InspirationEntity.payload`（JSON），不新增 Room 表：
 * - payload 在 SyncRepository 的同步信封里是**不透明字符串**原样透传，新字段天然同步兼容；
 * - 反序列化用 `ignoreUnknownKeys`，旧版本 App 读到新字段会忽略，前向兼容；
 * - 原书删除时 `source_book_id` 外键 SET_NULL、摘录文本/书名快照仍在 payload 里，
 *   「原书缺失时摘录仍可查看」由此保证。
 *
 * 纯函数、零 Android 依赖，可 JVM 直测。
 */

/**
 * 采用去向记录（契约 8：采用记录与原文摘录/用户想法/AI 内容分开）。
 * [kind] 仅支持 `text`（文字去向）与 `link`（链接去向）。
 */
@Serializable
data class InspirationAdoptionRecord(
    val kind: String,
    val value: String,
    val note: String? = null,
    val createdAt: String,
) {
    companion object {
        const val KIND_TEXT = "text"
        const val KIND_LINK = "link"
    }
}

/**
 * 把一条采用去向追加进 payload；首次采用会把状态推进为「已采用」。
 * 返回 null 表示校验失败（value 空白 / kind 非法），调用方不得落库。
 */
fun payloadWithAdoption(
    payload: InspirationPayloadData,
    record: InspirationAdoptionRecord,
    nowStatus: String,
): Pair<InspirationPayloadData, String>? {
    if (record.value.isBlank()) return null
    if (record.kind != InspirationAdoptionRecord.KIND_TEXT &&
        record.kind != InspirationAdoptionRecord.KIND_LINK
    ) {
        return null
    }
    val nextStatus = if (nowStatus != STATUS_USED) STATUS_USED else nowStatus
    return payload.copy(adoptions = payload.adoptions + record) to nextStatus
}

/** 移除一条采用去向；状态不自动回退（是否仍是「已采用」由用户决定）。 */
fun payloadWithoutAdoption(
    payload: InspirationPayloadData,
    record: InspirationAdoptionRecord,
): InspirationPayloadData = payload.copy(adoptions = payload.adoptions - record)

/**
 * 把多条素材聚合成一张**多摘录素材卡**的 payload（契约 8：素材可聚合多个来源）。
 *
 * - 每条来源的摘录文本 + 来源快照 + locator 原样保留（I2 摘录来源关联）；
 * - 只有具备摘录文本或定位信息的条目才进入聚合，纯想法条目只保留正文；
 * - 聚合卡片自身的 tags/categoryIds 取各条的并集（保序去重）。
 */
fun mergedMaterialPayload(
    sources: List<InspirationPayloadData>,
    tags: List<String>,
    categoryIds: List<String>,
): InspirationPayloadData {
    val excerpts = sources.mapNotNull { src ->
        val s = src.source ?: return@mapNotNull null
        val hasExcerpt = !s.excerpt.isNullOrBlank()
        if (hasExcerpt || !s.locatorJson.isNullOrBlank()) s else null
    }
    return InspirationPayloadData(
        tags = tags.union(sources.flatMap { it.tags }).toList(),
        categoryIds = categoryIds.union(sources.flatMap { it.categoryIds }).toList(),
        excerpts = excerpts,
    )
}

/** 素材回顾的四个管线阶段（契约 I1：待整理 / 已整理 / 已采用）。 */
const val PIPELINE_ORGANIZED = "organized"

/** 「已采用」状态字面量（与 InspirationPage.STATUS_OPTIONS 的 used 对齐）。 */
const val STATUS_USED = "used"

internal val PIPELINE_STAGES: List<Pair<String, String>> = listOf(
    "inbox" to "未整理",
    "reviewing" to "待整理",
    PIPELINE_ORGANIZED to "已整理",
    "used" to "已采用",
)

/**
 * 状态是否匹配某个回顾阶段筛选。
 * `organized` 是**聚合筛选**：同时覆盖「可使用」与「已打磨」两个既有状态；
 * 其余阶段与原始状态一一对应，未知筛选不匹配任何状态。
 */
fun statusMatchesPipeline(status: String, filterKey: String): Boolean = when (filterKey) {
    "inbox", "reviewing", "used" -> status == filterKey
    PIPELINE_ORGANIZED -> status == "usable" || status == "polished"
    else -> false
}

/** 各回顾阶段的条目计数（供列表页快捷筛选胶囊展示）。 */
fun pipelineCounts(items: List<Pair<String, Int>>): Map<String, Int> {
    // 入参为 (status, count) 对：由调用方 groupingBy 得到，这里只做阶段归并。
    val result = PIPELINE_STAGES.associate { it.first to 0 }.toMutableMap()
    for ((status, count) in items) {
        for ((key, _) in PIPELINE_STAGES) {
            if (statusMatchesPipeline(status, key)) result[key] = (result[key] ?: 0) + count
        }
    }
    return result
}
