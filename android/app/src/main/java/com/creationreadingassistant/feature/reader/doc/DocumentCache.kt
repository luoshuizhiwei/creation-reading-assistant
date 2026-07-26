package com.creationreadingassistant.feature.reader.doc

import java.util.concurrent.ConcurrentHashMap

/**
 * 章节内容缓存：access-order LRU + 单飞。
 *
 * 解决两个真实存在的浪费：
 *
 * 1. **零缓存。** `EpubChapter.blocks` 是 property getter，每次访问都重开 ZipFile 全量重解析。
 *    翻页、搜索、TTS 会对同一章反复取值，同一章能被解压好几次。
 * 2. **重复解析。** 预取协程和前台翻页可能同时要同一章，没有单飞就会解两遍，
 *    既费 CPU 又可能把内存峰值顶到两倍。
 *
 * 容量上限同时限章数与字符数：只限章数挡不住「整本书压在一个 XHTML 里」的超长单章。
 * 淘汰时**优先保留当前章**，否则前台正在读的那一章可能刚解完就被预取挤掉。
 */
class DocumentCache(
    private val maxChapters: Int = 4,
    private val maxChars: Int = 2_000_000,
) {
    private val stateLock = Any()

    /** accessOrder = true：get 会把条目移到队尾，从而实现 LRU。 */
    private val cache = LinkedHashMap<Int, List<DocBlock>>(8, 0.75f, true)
    private var cachedChars = 0

    /** 每章一把锁，保证同一章只解析一次，不同章之间不互相阻塞。 */
    private val chapterLocks = ConcurrentHashMap<Int, Any>()

    /**
     * 取一章内容；未命中时用 [loader] 解析并放入缓存。
     *
     * @param keepIndex 淘汰时优先保留的章号，通常传当前正在读的那一章。
     */
    fun get(index: Int, keepIndex: Int = index, loader: (Int) -> List<DocBlock>): List<DocBlock> {
        synchronized(stateLock) { cache[index] }?.let { return it }

        val lock = chapterLocks.computeIfAbsent(index) { Any() }
        synchronized(lock) {
            // 双重检查：等锁期间别的线程可能已经解好了
            synchronized(stateLock) { cache[index] }?.let { return it }

            val blocks = loader(index)
            synchronized(stateLock) {
                cache[index] = blocks
                cachedChars += blocks.charCount()
                trim(keepIndex)
            }
            return blocks
        }
    }

    fun clear() {
        synchronized(stateLock) {
            cache.clear()
            cachedChars = 0
        }
        chapterLocks.clear()
    }

    /** 仅供测试与诊断。 */
    fun cachedChapterCount(): Int = synchronized(stateLock) { cache.size }

    private fun trim(keepIndex: Int) {
        while (cache.size > maxChapters || cachedChars > maxChars) {
            // LinkedHashMap 的迭代顺序即 LRU 顺序，队首是最久未用的
            val victim = cache.keys.firstOrNull { it != keepIndex } ?: break
            val removed = cache.remove(victim) ?: break
            cachedChars -= removed.charCount()
            if (cache.size <= 1) break // 至少留住当前章
        }
        if (cachedChars < 0) cachedChars = 0
    }

    private fun List<DocBlock>.charCount(): Int =
        sumOf { if (it is DocBlock.Text) it.text.length else 0 }
}
