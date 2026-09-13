package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.ReaderFontManager
import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 阅读器跨域小工具：字体解析、时长/进度文案、灵感与标注 payload 序列化。
 * Markdown 渲染见 ReaderMarkdownRender.kt；偏移/索引换算见 ReaderTextIndex.kt。
 */

/** 时间戳 ISO-8601 字符串（委托 ui/util 公共实现）。 */
internal fun nowIso(): String = com.creationreadingassistant.ui.util.nowIso()

/**
 * 按自定义字体路径解析正文 [FontFamily]（空 = 系统字体）。
 * 加载失败回退 [FontFamily.Default]，阅读器各渲染路径共用同一个入口。
 */
@Composable
internal fun rememberReaderFontFamily(customFontPath: String): FontFamily = remember(customFontPath) {
    if (customFontPath.isBlank()) FontFamily.Default
    else ReaderFontManager.loadTypeface(customFontPath)?.let { FontFamily(it) } ?: FontFamily.Default
}

/** 按阅读进度百分比推算最接近的章节索引（SE4 兜底定位用）。 */
internal fun progressToChapterIndex(book: EpubBook, progressPercent: Float?): Int {
    val size = book.chapters.size
    if (size <= 1) return 0
    if (progressPercent == null) return 0
    return ((progressPercent / 100f * size - 1).toInt()).coerceIn(0, size - 1)
}

/** 毫秒时长格式化（委托 ui/util 公共实现，输出「天/小时/分钟」统一格式）。 */
internal fun formatDuration(ms: Long): String =
    com.creationreadingassistant.ui.util.formatDuration(ms)

/** 构造标准灵感 payload，确保 InspirationViewModel 能正确解析来源（全字段对齐网页 I5）。 */
internal fun buildInspirationPayload(
    bid: String,
    bookTitle: String,
    chapterTitle: String,
    excerpt: String?,
    progressPercent: Float,
    tags: List<String> = emptyList(),
    categoryIds: List<String> = emptyList(),
    bookAuthor: String? = null,
    locatorJson: String? = null,
): String {
    val payload = InspirationPayloadData(
        tags = tags,
        categoryIds = categoryIds,
        source = InspirationSourceInfo(
            bookId = bid.ifBlank { null },
            bookTitle = bookTitle.ifBlank { null },
            bookAuthor = bookAuthor?.takeIf { it.isNotBlank() },
            chapterTitle = chapterTitle.ifBlank { null },
            locationLabel = null,
            progressPercent = if (progressPercent > 0f) progressPercent else null,
            excerpt = excerpt?.takeIf { it.isNotBlank() },
            // R5-I2：与高亮/笔记同源的 locator JSON，灵感详情据此精确回源定位
            locatorJson = locatorJson?.takeIf { it.isNotBlank() },
        ),
    )
    return Json.encodeToString(InspirationPayloadData.serializer(), payload)
}

/** 从 locator_json 解析全局字符偏移（容错：不依赖完整 JSON 解析）。 */
internal fun parseLocatorOffset(json: String?): Int? = LocatorCodec.decode(json)?.legacyOffset

/** 新高亮记录投影选区对应的 source 长度；旧记录保持空 payload 兼容。 */
internal fun buildHighlightPayload(sourceLength: Int?): String =
    sourceLength?.takeIf { it >= 0 }?.let { length ->
        buildJsonObject { put("source_length", length) }.toString()
    } ?: "{}"

/** 读取 source 长度；旧记录、损坏 payload 或非法值均回退到既有文本长度语义。 */
internal fun highlightSourceLength(payload: String?, fallbackTextLength: Int): Int {
    val stored = runCatching {
        Json.parseToJsonElement(payload.orEmpty())
            .jsonObject["source_length"]
            ?.jsonPrimitive
            ?.intOrNull
    }.getOrNull()
    return stored?.takeIf { it >= 0 } ?: fallbackTextLength
}
