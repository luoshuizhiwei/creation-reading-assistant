package com.creationreadingassistant.feature.reader.doc

/**
 * 小型 LRU 缓存，用于缓存已解码的 ReadingUnit 文本。
 *
 * 线程安全：所有方法均加 [Synchronized]。
 * 默认最多缓存 [MAX_ENTRIES] 个条目，超出时淘汰最久未使用的条目。
 *
 * @param maxEntries 缓存最大条目数，默认 5
 */
class ReadingUnitCache(private val maxEntries: Int = MAX_ENTRIES) {

    companion object {
        /** 默认最大缓存条目数。 */
        const val MAX_ENTRIES = 5
    }

    /**
     * LRU LinkedHashMap：accessOrder=true 使最近访问的条目移到末尾，
     * 最久未使用的条目在头部。
     */
    private val cache = LinkedHashMap<Int, String>(maxEntries + 1, 0.75f, true)

    /**
     * 获取指定 [unitIndex] 的缓存文本，如果未命中则通过 [loader] 加载并缓存。
     *
     * @param unitIndex 读取单元索引
     * @param loader 缓存未命中时的加载函数，接收 unitIndex 返回文本
     * @return 缓存或新加载的文本
     */
    @Synchronized
    fun getOrLoad(unitIndex: Int, loader: (Int) -> String): String {
        cache[unitIndex]?.let { return it }
        val value = loader(unitIndex)
        cache[unitIndex] = value
        // 如果超出容量，移除最久未使用的条目（LRU 头部）
        if (cache.size > maxEntries) {
            val eldest = cache.entries.first()
            cache.remove(eldest.key)
        }
        return value
    }

    /** 主动驱逐指定 [unitIndex] 的缓存条目。 */
    @Synchronized
    fun evict(unitIndex: Int) {
        cache.remove(unitIndex)
    }

    /** 清空所有缓存。 */
    @Synchronized
    fun clear() {
        cache.clear()
    }

    /** 当前缓存中的条目数。 */
    @Synchronized
    fun size(): Int = cache.size
}
