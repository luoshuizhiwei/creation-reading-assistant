package com.creationreadingassistant.feature.sync.legado

import java.security.MessageDigest

/**
 * Legado 导入身份计算（契约 §2/附录 A）。
 *
 * 会话身份按 MD3 固定提交 `4efe2496` 的 `stableFingerprint` 七原始字段**重算**：
 * deviceId、bookName、bookAuthor、bookUrl、startTime、endTime、words，原顺序；
 * 不读上游导出的计算属性、不依赖其 `\u0001` 分隔符串，改用「类型标记 + 4 字节
 * 大端长度 + UTF-8 字节」的无歧义编码后整体 SHA-256，来源库作为外层命名空间。
 * 日期/数字字段用类型标记区分（'L' 长整 / 'S' 字符串），杜绝拼接歧义。
 */
object LegadoFingerprint {

    private const val DOMAIN = "legado-fp-v1"

    private fun MessageDigest.feed(tag: Char, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        update(tag.code.toByte())
        update(bytes.size.let { byteArrayOf((it ushr 24).toByte(), (it ushr 16).toByte(), (it ushr 8).toByte(), it.toByte()) })
        update(bytes)
    }

    private fun MessageDigest.feedLong(tag: Char, value: Long) {
        update(tag.code.toByte())
        update(
            byteArrayOf(
                (value ushr 56).toByte(), (value ushr 48).toByte(),
                (value ushr 40).toByte(), (value ushr 32).toByte(),
                (value ushr 24).toByte(), (value ushr 16).toByte(),
                (value ushr 8).toByte(), value.toByte(),
            ),
        )
    }

    private fun digest(vararg fields: Pair<Char, Any>): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.feed('D', DOMAIN)
        fields.forEach { (tag, value) ->
            when (value) {
                is String -> md.feed(tag, value)
                is Long -> md.feedLong(tag, value)
                else -> throw IllegalArgumentException("unsupported field type")
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** 上游七元组的会话指纹（原值保序；空串/0 都是合法原始值）。 */
    fun sessionFingerprint(
        deviceId: String,
        bookName: String,
        bookAuthor: String,
        bookUrl: String,
        startTime: Long,
        endTime: Long,
        words: Long?,
    ): String = digest(
        'a' to deviceId,
        'b' to bookName,
        'c' to bookAuthor,
        'd' to bookUrl,
        'e' to startTime,
        'f' to endTime,
        'g' to (words ?: Long.MIN_VALUE),
        'h' to (if (words == null) "missing" else "present"),
    )

    /** 来源库为外层命名空间：同指纹在不同来源库是不同行。 */
    fun sessionRowId(sourceId: String, fingerprint: String): String =
        digest('s' to sourceId, 'f' to fingerprint)

    /** 按日快照键：来源库 + 设备 + 作品身份 + 原始日期。 */
    fun daySnapshotId(sourceId: String, deviceId: String, bookName: String, bookAuthor: String, date: String): String =
        digest(
            'k' to "day",
            's' to sourceId,
            'a' to deviceId,
            'b' to bookName,
            'c' to bookAuthor,
            'd' to date,
        )

    /** 书级快照键：来源库 + 设备 + 作品身份。 */
    fun bookSnapshotId(sourceId: String, deviceId: String, bookName: String, bookAuthor: String): String =
        digest(
            'k' to "book",
            's' to sourceId,
            'a' to deviceId,
            'b' to bookName,
            'c' to bookAuthor,
        )

    /** 主体键：来源库 + kind + 书名 + 作者 + bookUrl（作品级 bookUrl 为空串）。 */
    fun subjectId(sourceId: String, kind: String, bookName: String, bookAuthor: String, bookUrl: String): String =
        digest(
            'k' to kind,
            's' to sourceId,
            'b' to bookName,
            'c' to bookAuthor,
            'd' to bookUrl,
        )

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** 本地文档身份：总字符数 + 章起点表，正文版本变更即失配（契约 §6 重验依据）。 */
    fun docIdentity(totalChars: Int, chapterStartOffsets: List<Int>): String =
        digest(
            't' to totalChars.toString(),
            'n' to chapterStartOffsets.size.toString(),
            'o' to chapterStartOffsets.joinToString(","),
        )

    /** 撤销用「导入后内容指纹」：任一字段变化即判定为被后续活动修改。 */
    fun contentFingerprint(vararg values: String): String = digest(*values.map { 'v' to it }.toTypedArray())
}
