package com.creationreadingassistant.data.local.entity

import androidx.room.Entity

/**
 * 分页索引缓存。
 *
 * 只存 `page_starts`（每页首字符在章内的偏移，IntArray 序列化成 BLOB），
 * **不存整章的行对象**。一章 60 页仅 240 字节，而整章行对象常驻要 0.5 MB/章；
 * 页按需重排约 1 ms，完全够用。
 *
 * 落库的价值只有一个：冷启动不必重排整章就能算出「第 37 / 62 页」与百分比。
 *
 * [fingerprint] 是排版指纹（字号/行距/边距/视口/字体/引擎版本）。它进主键，
 * 于是不同配置下的索引天然共存、互不覆盖，改回原字号还能命中旧缓存。
 *
 * 缓存性质：丢了只是要重排一次，绝不影响正确性。
 */
@Entity(
    tableName = "reader_page_index",
    primaryKeys = ["content_key", "chapter_index", "fingerprint"],
)
data class ReaderPageIndexEntity(
    /** 内容标识。EPUB 用缓存文件路径的 hash，TXT 用书籍 id。 */
    val content_key: String,
    val chapter_index: Int,
    val fingerprint: Int,
    /** IntArray 的小端序列化 */
    val page_starts: ByteArray,
    val char_count: Int,
    val created_at: Long,
) {
    // ByteArray 在 data class 里默认用引用比较，必须手写
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReaderPageIndexEntity) return false
        return content_key == other.content_key &&
            chapter_index == other.chapter_index &&
            fingerprint == other.fingerprint &&
            page_starts.contentEquals(other.page_starts) &&
            char_count == other.char_count &&
            created_at == other.created_at
    }

    override fun hashCode(): Int {
        var r = content_key.hashCode()
        r = 31 * r + chapter_index
        r = 31 * r + fingerprint
        r = 31 * r + page_starts.contentHashCode()
        r = 31 * r + char_count
        return r
    }
}
