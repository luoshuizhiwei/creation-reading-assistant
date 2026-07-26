package com.creationreadingassistant.feature.reader.pager

/**
 * `pageStarts: IntArray` ↔ `ByteArray` 的序列化，小端 4 字节一个值。
 *
 * 数据库列是 BLOB（见 ReaderPageIndexEntity.page_starts），格式在这里定死：
 * 一旦有缓存落盘，这个编码就成了磁盘契约，**不能再改**（改了旧缓存全部解不出来，
 * 虽然只是缓存、丢了无害，但会静默让所有历史缓存失效一次）。
 *
 * 纯 JVM、零依赖，配套锁定测试。
 */
object PageStartsCodec {

    fun encode(starts: IntArray): ByteArray {
        val out = ByteArray(starts.size * 4)
        for (i in starts.indices) {
            val v = starts[i]
            val base = i * 4
            out[base] = (v and 0xFF).toByte()
            out[base + 1] = ((v ushr 8) and 0xFF).toByte()
            out[base + 2] = ((v ushr 16) and 0xFF).toByte()
            out[base + 3] = ((v ushr 24) and 0xFF).toByte()
        }
        return out
    }

    /** 长度不是 4 的倍数说明数据损坏，返回 null 当缓存未命中处理。 */
    fun decode(bytes: ByteArray): IntArray? {
        if (bytes.size % 4 != 0) return null
        val out = IntArray(bytes.size / 4)
        for (i in out.indices) {
            val base = i * 4
            out[i] = (bytes[base].toInt() and 0xFF) or
                ((bytes[base + 1].toInt() and 0xFF) shl 8) or
                ((bytes[base + 2].toInt() and 0xFF) shl 16) or
                ((bytes[base + 3].toInt() and 0xFF) shl 24)
        }
        return out
    }
}
