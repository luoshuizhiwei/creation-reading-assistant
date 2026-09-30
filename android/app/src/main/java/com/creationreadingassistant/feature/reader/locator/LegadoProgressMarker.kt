package com.creationreadingassistant.feature.reader.locator

/**
 * 开源阅读（Legado）进度的无损暂存标记。
 *
 * Legado 的 TXT 进度是「目录章号 + 章内字符偏移」，而本应用纯文本读者保存的是
 * 全书绝对偏移。两者不能在未加载本地正文及目录规则前直接互换；否则会把章内
 * 偏移误当作全书偏移。导入时先以本标记持久化，ReaderDocumentLoader 在取得本地
 * 目录后再转换为绝对偏移。EPUB 不使用本标记，因其本身支持章节坐标。
 */
data class LegadoChapterPosition(
    val chapterIndex: Int,
    val charOffset: Int,
)

object LegadoProgressMarker {
    private val chapterRegex = Regex("""\"legado_position_v1\"\s*:\s*\{[^{}]*\"ci\"\s*:\s*(\d+)""")
    private val offsetRegex = Regex("""\"legado_position_v1\"\s*:\s*\{[^{}]*\"co\"\s*:\s*(\d+)""")

    fun encode(chapterIndex: Int, charOffset: Int): String =
        """{"legado_position_v1":{"ci":${chapterIndex.coerceAtLeast(0)},"co":${charOffset.coerceAtLeast(0)}}}"""

    fun decode(json: String?): LegadoChapterPosition? {
        if (json.isNullOrBlank()) return null
        val chapter = chapterRegex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val offset = offsetRegex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (chapter != null && offset != null) {
            LegadoChapterPosition(chapter.coerceAtLeast(0), offset.coerceAtLeast(0))
        } else {
            null
        }
    }
}
