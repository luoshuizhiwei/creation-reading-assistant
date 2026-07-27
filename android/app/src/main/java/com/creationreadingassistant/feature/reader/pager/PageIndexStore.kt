package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.data.local.dao.ReaderPageIndexDao
import com.creationreadingassistant.data.local.entity.ReaderPageIndexEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分页索引缓存的读写门面。
 *
 * 定位（见 ReaderPageIndexEntity 的注释）：只存每页首字符的章内偏移，
 * 不存行对象。它是**纯缓存** —— 丢了只是多排一次版，绝不影响正确性。
 * 因此这里的纪律是：**任何读写失败都吞掉返回 null / 忽略**，
 * 缓存层的故障绝不能升级成阅读器的故障。
 *
 * 命中条件（三个键 + 一道校验）：
 * - `contentKey`（TXT / EPUB 均为书籍 id）
 * - `chapterIndex`
 * - `fingerprint`（排版指纹：字号/行距/边距/视口/字体/引擎版本，见 LayoutConfig）
 * - 校验 `char_count`：同一本书重新导入后内容可能变了而 id 未变，
 *   章字符数对不上就当未命中，防止旧书的页边界套在新内容上。
 */
@Singleton
class PageIndexStore @Inject constructor(
    private val dao: ReaderPageIndexDao,
) {

    /** @param expectedCharCount 当前章的实际字符数，用于内容一致性校验 */
    suspend fun load(
        contentKey: String,
        chapterIndex: Int,
        fingerprint: Int,
        expectedCharCount: Int,
    ): IntArray? = runCatching {
        val row = dao.get(contentKey, chapterIndex, fingerprint) ?: return@runCatching null
        if (row.char_count != expectedCharCount) return@runCatching null
        PageStartsCodec.decode(row.page_starts)
    }.getOrNull()

    suspend fun save(
        contentKey: String,
        chapterIndex: Int,
        fingerprint: Int,
        pageStarts: IntArray,
        charCount: Int,
    ) {
        runCatching {
            dao.upsert(
                ReaderPageIndexEntity(
                    content_key = contentKey,
                    chapter_index = chapterIndex,
                    fingerprint = fingerprint,
                    page_starts = PageStartsCodec.encode(pageStarts),
                    char_count = charCount,
                    created_at = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** 一本书最多留两个指纹的索引（改回原字号还能命中旧缓存），其余清掉。 */
    suspend fun prune(contentKey: String) {
        runCatching { dao.pruneOldFingerprints(contentKey) }
    }

    suspend fun clearBook(contentKey: String) {
        runCatching { dao.clearBook(contentKey) }
    }

    suspend fun knownCharCounts(contentKey: String): Map<Int, Int> =
        runCatching {
            dao.listRecent(contentKey)
                .distinctBy { it.chapter_index }
                .associate { it.chapter_index to it.char_count }
        }.getOrDefault(emptyMap())
}
