package com.creationreadingassistant.ui.screen.profile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 检查更新的纯逻辑层：releases 仓库同时承载桌面端与 Android 发布，
 * Android 用 `android-v1.2.3` 前缀 tag 区分。这里从 releases 列表 JSON 中
 * 过滤出最新的 Android 发布并做语义化版本比较，与网络和 UI 解耦，便于 JVM 单测锁定行为。
 */
internal object UpdateCheck {
    private val json = Json { ignoreUnknownKeys = true }

    /** Android 发布 tag 前缀（桌面端发布用无前缀 tag，`/releases/latest` 会命中桌面端）。 */
    const val ANDROID_TAG_PREFIX = "android-v"

    data class AndroidReleaseInfo(
        val tag: String,
        val version: String,
        val pageUrl: String?,
        val notes: String?,
    )

    /** 解析 GitHub `GET /releases` 列表 JSON，返回版本号最高的 Android 发布；无命中或格式非法返回 null。 */
    fun latestAndroidRelease(releasesJson: String): AndroidReleaseInfo? {
        val releases: JsonArray = runCatching { json.parseToJsonElement(releasesJson).jsonArray }
            .getOrNull() ?: return null
        return releases
            .mapNotNull { element -> runCatching { element.jsonObject }.getOrNull() }
            .filterNot { obj -> obj.optBoolean("draft") }
            .mapNotNull { obj ->
                val tag = obj.optString("tag_name") ?: return@mapNotNull null
                if (!tag.startsWith(ANDROID_TAG_PREFIX, ignoreCase = true)) return@mapNotNull null
                val version = tag.substring(ANDROID_TAG_PREFIX.length).trim()
                if (version.isBlank()) return@mapNotNull null
                AndroidReleaseInfo(
                    tag = tag,
                    version = version,
                    pageUrl = obj.optString("html_url"),
                    notes = obj.optString("body")?.trim()?.takeIf { it.isNotBlank() },
                )
            }
            .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
    }

    /**
     * 语义化版本比较：按 `.` 分段逐段比数字，缺段视为 0。
     * 本地版本名可能带 `-p4` 之类构建后缀，只取每段开头的数字部分，
     * 因此后缀不参与比较（`0.4.0-p4` 视同 `0.4.0`）。
     * 返回负数 / 0 / 正数分别表示 a 小于 / 等于 / 大于 b。
     */
    fun compareVersions(a: String, b: String): Int {
        val va = parseVersion(a)
        val vb = parseVersion(b)
        for (i in 0 until maxOf(va.size, vb.size)) {
            val diff = va.getOrElse(i) { 0 }.compareTo(vb.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private fun parseVersion(version: String): List<Int> =
        version.trim().trimStart('v', 'V')
            .split('.')
            .map { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    private fun JsonObject.optString(key: String): String? =
        this[key]?.let { value -> runCatching { value.jsonPrimitive.content }.getOrNull() }

    private fun JsonObject.optBoolean(key: String): Boolean =
        this[key]?.let { value -> runCatching { value.jsonPrimitive.content.toBooleanStrict() }.getOrNull() } == true
}
