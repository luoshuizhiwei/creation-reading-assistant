package com.creationreadingassistant.feature.annotations

import com.creationreadingassistant.data.local.entity.HighlightEntity
import com.creationreadingassistant.data.local.entity.NoteEntity

/**
 * 类型化回源定位目标：把「我的 → 阅读笔记」的 typed stableId 与阅读器 SE4 的
 * pendingHighlightId 解析口径统一起来，避免同一 rawId 跨表（highlights / notes）
 * 被「先高亮后笔记」猜中错误记录。
 *
 * 稳定 ID 约定：`highlight:{id}` / `note:{id}` / `bookmark:{id}`（类型前缀 + 原表主键）。
 * 前缀小写与 [AnnotationModels] 中 [stableId] 的 `type.name.lowercase()` 完全一致。
 */

/** 类型化回源目标：标记了类型的原表记录。 */
data class AnnotationNavigationTarget(
    val type: AnnotationType,
    val rawId: String,
)

private const val SEPARATOR = ":"

private fun typePrefix(type: AnnotationType): String = type.name.lowercase()

/**
 * 构造纯文本 typed stableId（未经 URL 编码）：`highlight:{id}` / `note:{id}` / `bookmark:{id}`。
 * 用于书内回源（直接写入 pendingHighlightId，不经导航组件解码）。
 */
fun navigationTargetId(type: AnnotationType, rawId: String): String =
    "${typePrefix(type)}$SEPARATOR$rawId"

/**
 * 「我的 → 阅读笔记」跳转携带的定位目标：有 locator 才携带 typed stableId，
 * 无 locator 的历史条目返回 null（只打开书籍，不伪造偏移）。URL 编码由调用方负责。
 */
fun AnnotationEntry.navigationTargetOrNull(): String? =
    if (hasLocator) stableId else null

/**
 * 解析路由参数为类型化目标；返回 null 表示「无类型前缀」的裸 rawId（旧入口）或
 * 前缀无法识别 —— 二者都按「旧兼容语义」处理，不当作 typed 目标。
 */
fun parseAnnotationNavigationTarget(raw: String?): AnnotationNavigationTarget? {
    if (raw.isNullOrBlank()) return null
    val idx = raw.indexOf(SEPARATOR)
    if (idx <= 0) return null
    val prefix = raw.substring(0, idx)
    val id = raw.substring(idx + 1).trim()
    if (id.isEmpty()) return null
    val type = when (prefix) {
        "highlight" -> AnnotationType.HIGHLIGHT
        "note" -> AnnotationType.NOTE
        "bookmark" -> AnnotationType.BOOKMARK
        else -> return null
    }
    return AnnotationNavigationTarget(type, id)
}

/**
 * 匹配类型化目标对应的实体：
 * - highlight:{id} 只匹配 [HighlightEntity]；
 * - note:{id} 只匹配 kind != "bookmark" 的 [NoteEntity]；
 * - bookmark:{id} 只匹配 kind == "bookmark" 的 [NoteEntity]。
 * 类型不符或记录不存在返回 null，**不回退另一张表猜测命中**。
 * 裸 rawId（[typed] == null）保留旧「先高亮、后笔记」兼容语义。
 */
fun resolveAnnotationTarget(
    typed: AnnotationNavigationTarget?,
    rawParam: String,
    highlights: List<HighlightEntity>,
    notes: List<NoteEntity>,
): Any? = when {
    typed == null -> highlights.firstOrNull { it.id == rawParam }
        ?: notes.firstOrNull { it.id == rawParam }
    typed.type == AnnotationType.HIGHLIGHT -> highlights.firstOrNull { it.id == typed.rawId }
    typed.type == AnnotationType.NOTE -> notes.firstOrNull { it.id == typed.rawId && it.kind != "bookmark" }
    typed.type == AnnotationType.BOOKMARK -> notes.firstOrNull { it.id == typed.rawId && it.kind == "bookmark" }
    else -> null
}