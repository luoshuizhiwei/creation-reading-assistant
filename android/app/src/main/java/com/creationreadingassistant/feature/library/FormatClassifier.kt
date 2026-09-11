package com.creationreadingassistant.feature.library

/**
 * 导入 / 同步共用的「格式真源」分类器（纯 JVM，无 Android 依赖）。
 *
 * 判定只依赖三样输入，绝不为了识别格式去读取完整文件：
 *  - [claimedFormat]：声明格式（同步时来自 payload 的 `format` 字段；导入没有独立声明，传 null），可为空；
 *  - [fileName]：文件名（[claimedFormat] 缺失时按扩展名兜底），可为空；
 *  - [firstBytes]：正文前 4 个字节（不足 4 字节按实际长度判断）。
 *
 * 规则（导入与同步下载共用同一套，禁止分叉）：
 *  - 真实 ZIP 字节（[EPUB_MAGIC]=`PK\x03\x04`）优先：正文是 EPUB 时，无论声明/扩展名声称 txt/md 都识别为 epub；
 *  - `markdown` 归一为 `md`（大小写不敏感）；
 *  - 非 ZIP 且声明/扩展名为 epub → 拒绝，不伪装成 EPUB；
 *  - 非 ZIP 的 txt/md 维持规范化识别；格式与文件名都缺失时拒绝。
 */
object FormatClassifier {

    /** ZIP local file header 魔数：`PK\x03\x04`。 */
    val EPUB_MAGIC: ByteArray = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /** 判定结果。 */
    sealed interface Verdict {
        /** 声明与内容一致，可按 [format]（`epub`/`txt`/`md`）安全落库与解析。 */
        data class Accepted(val format: String) : Verdict

        /** 拒绝：声称 epub 但正文非 ZIP，或无法确定可用格式。 */
        data class Rejected(
            val reason: String,
            /** 被声明的规范化格式；无法识别时为 null（用于区分「不支持」与「格式错配」）。 */
            val claimedFormat: String?,
        ) : Verdict
    }

    /** 纯入口：给定声明格式、文件名、前 4 字节，返回是否可安全按某格式处理。 */
    fun classify(claimedFormat: String?, fileName: String?, firstBytes: ByteArray): Verdict {
        // 真实 ZIP 字节优先：正文是 EPUB 时，无论声明/扩展名声称 txt/md，都按 epub 处理。
        if (isEpubZip(firstBytes)) {
            return Verdict.Accepted("epub")
        }
        val declared = canonical(claimedFormat)
        val viaExtension = canonical(extensionOf(fileName))
        // 优先采纳声明格式；缺失时才按扩展名兜底（不盲猜 epub）
        val format = declared ?: viaExtension
        if (format == null) {
            return Verdict.Rejected("无法识别的格式", null)
        }
        if (format == "epub") {
            return Verdict.Rejected("声明为 EPUB 但正文不是有效的 ZIP 文件", "epub")
        }
        return Verdict.Accepted(format)
    }

    /** 是否以 ZIP 本地文件头开头（=`PK\x03\x04`）。 */
    fun isEpubZip(firstBytes: ByteArray): Boolean {
        if (firstBytes.size < EPUB_MAGIC.size) return false
        for (i in EPUB_MAGIC.indices) {
            if (firstBytes[i] != EPUB_MAGIC[i]) return false
        }
        return true
    }

    /** 将声明格式归一（`markdown`→`md`），非支持格式返回 null。 */
    private fun canonical(raw: String?): String? {
        val s = raw?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return when (s) {
            "markdown" -> "md"
            "epub", "txt", "md" -> s
            else -> null
        }
    }

    /** 提取文件扩展名（不含点、小写）；无扩展名返回 null。 */
    private fun extensionOf(fileName: String?): String? {
        val name = fileName?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return null
        return name.substring(dot + 1).lowercase()
    }
}