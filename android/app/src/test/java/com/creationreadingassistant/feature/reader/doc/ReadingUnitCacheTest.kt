package com.creationreadingassistant.feature.reader.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for ReadingUnitCache LRU behavior.
 */
class ReadingUnitCacheTest {

    @Test
    fun `getOrLoad loads and caches value`() {
        val cache = ReadingUnitCache(maxEntries = 3)
        var loadCount = 0
        val loader: (Int) -> String = { idx ->
            loadCount++
            "text_$idx"
        }

        // First call: should invoke loader
        assertEquals("text_0", cache.getOrLoad(0, loader))
        assertEquals(1, loadCount)
        assertEquals(1, cache.size())

        // Second call: should return cached value, not invoke loader
        assertEquals("text_0", cache.getOrLoad(0, loader))
        assertEquals(1, loadCount) // still 1
        assertEquals(1, cache.size())
    }

    @Test
    fun `getOrLoad caches multiple entries`() {
        val cache = ReadingUnitCache(maxEntries = 3)
        val loader: (Int) -> String = { "text_$it" }

        cache.getOrLoad(0, loader)
        cache.getOrLoad(1, loader)
        cache.getOrLoad(2, loader)
        assertEquals(3, cache.size())

        // All should be cached
        assertEquals("text_0", cache.getOrLoad(0, loader))
        assertEquals("text_1", cache.getOrLoad(1, loader))
        assertEquals("text_2", cache.getOrLoad(2, loader))
    }

    @Test
    fun `LRU eviction removes oldest accessed entry`() {
        val cache = ReadingUnitCache(maxEntries = 3)
        val loader: (Int) -> String = { "text_$it" }

        // Fill cache to capacity
        cache.getOrLoad(0, loader)
        cache.getOrLoad(1, loader)
        cache.getOrLoad(2, loader)
        assertEquals(3, cache.size())

        // Access entry 0 to make it recently used
        cache.getOrLoad(0, loader)

        // Add entry 3: should evict entry 1 (least recently used)
        cache.getOrLoad(3, loader)
        assertEquals(3, cache.size())

        // Entry 0 should still be cached (was recently accessed)
        var loadCount = 0
        val countingLoader: (Int) -> String = { loadCount++; "text_$it" }
        assertEquals("text_0", cache.getOrLoad(0, countingLoader))
        assertEquals(0, loadCount) // should be cached

        // Entry 1 should have been evicted, so loader should be called
        assertEquals("text_1", cache.getOrLoad(1, countingLoader))
        assertEquals(1, loadCount) // loader was called
    }

    @Test
    fun `evict removes specific entry`() {
        val cache = ReadingUnitCache(maxEntries = 5)
        val loader: (Int) -> String = { "text_$it" }

        cache.getOrLoad(0, loader)
        cache.getOrLoad(1, loader)
        assertEquals(2, cache.size())

        cache.evict(0)
        assertEquals(1, cache.size())

        // Entry 0 should be re-loaded
        var loadCount = 0
        val countingLoader: (Int) -> String = { loadCount++; "text_$it" }
        assertEquals("text_0", cache.getOrLoad(0, countingLoader))
        assertEquals(1, loadCount)
    }

    @Test
    fun `clear removes all entries`() {
        val cache = ReadingUnitCache(maxEntries = 5)
        val loader: (Int) -> String = { "text_$it" }

        cache.getOrLoad(0, loader)
        cache.getOrLoad(1, loader)
        cache.getOrLoad(2, loader)
        assertEquals(3, cache.size())

        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun `evict non-existent entry is no-op`() {
        val cache = ReadingUnitCache(maxEntries = 5)
        cache.evict(999) // should not throw
        assertEquals(0, cache.size())
    }

    @Test
    fun `default max entries is 5`() {
        val cache = ReadingUnitCache()
        val loader: (Int) -> String = { "text_$it" }

        for (i in 0 until 10) {
            cache.getOrLoad(i, loader)
        }
        assertTrue("Cache should not exceed MAX_ENTRIES", cache.size() <= ReadingUnitCache.MAX_ENTRIES)
        assertEquals(ReadingUnitCache.MAX_ENTRIES, cache.size())
    }

    @Test
    fun `getOrLoad returns correct value after eviction and reload`() {
        val cache = ReadingUnitCache(maxEntries = 2)
        val loader: (Int) -> String = { "v1_$it" }

        cache.getOrLoad(0, loader)
        cache.getOrLoad(1, loader)
        assertEquals(2, cache.size())

        // Evict entry 0
        cache.evict(0)

        // Reload with different loader
        val newLoader: (Int) -> String = { "v2_$it" }
        assertEquals("v2_0", cache.getOrLoad(0, newLoader))
        assertEquals("v1_1", cache.getOrLoad(1, newLoader)) // still cached from old loader
    }

    // ── G5 Category 10: Additional Cache Tests ─────────────────────────

    @Test
    fun `cache hit returns same value with loader called only once`() {
        val cache = ReadingUnitCache(maxEntries = 5)
        var loadCount = 0
        val loader: (Int) -> String = {
            loadCount++
            "loaded_value"
        }

        // First call: loader invoked
        val result1 = cache.getOrLoad(42, loader)
        assertEquals("loaded_value", result1)
        assertEquals(1, loadCount)

        // Second call: same value returned, loader NOT called again
        val result2 = cache.getOrLoad(42, loader)
        assertEquals("loaded_value", result2)
        assertEquals(1, loadCount) // still 1

        // Third call: still cached
        val result3 = cache.getOrLoad(42, loader)
        assertEquals("loaded_value", result3)
        assertEquals(1, loadCount) // still 1
    }

    @Test
    fun `LRU eviction order is correct with multiple accesses`() {
        val cache = ReadingUnitCache(maxEntries = 3)
        val loader: (Int) -> String = { "text_$it" }

        // Fill cache: [0, 1, 2]
        cache.getOrLoad(0, loader)
        cache.getOrLoad(1, loader)
        cache.getOrLoad(2, loader)
        assertEquals(3, cache.size())

        // Access order: 0, 1, 2 -> LRU is 0
        // Access 0 to make it most recent: order becomes [1, 2, 0]
        cache.getOrLoad(0, loader)

        // Add 3: should evict 1 (now LRU)
        cache.getOrLoad(3, loader)
        assertEquals(3, cache.size())

        // First verify the three retained entries. Reloading the evicted entry would itself
        // insert a new value and evict the next LRU entry, so that check must happen last.
        var loadCount = 0
        val countingLoader: (Int) -> String = { loadCount++; "new_$it" }
        cache.getOrLoad(0, countingLoader)
        cache.getOrLoad(2, countingLoader)
        cache.getOrLoad(3, countingLoader)
        assertEquals(0, loadCount)

        // Verify 1 was evicted by checking loader is called for 1.
        cache.getOrLoad(1, countingLoader)
        assertEquals(1, loadCount)
    }

    @Test
    fun `clear resets cache size to zero`() {
        val cache = ReadingUnitCache(maxEntries = 5)
        val loader: (Int) -> String = { "text_$it" }

        // Fill cache with 5 entries
        for (i in 0 until 5) {
            cache.getOrLoad(i, loader)
        }
        assertEquals(5, cache.size())

        // Clear and verify
        cache.clear()
        assertEquals(0, cache.size())

        // Verify all entries are gone by checking loader is called for each
        var loadCount = 0
        val countingLoader: (Int) -> String = { loadCount++; "new_$it" }
        for (i in 0 until 5) {
            cache.getOrLoad(i, countingLoader)
        }
        assertEquals(5, loadCount) // all 5 were re-loaded
    }

    // ── G5 Category 10: Additional Cache Tests ───────────────────────────

    @Test
    fun `cache handles negative unitIndex correctly`() {
        val cache = ReadingUnitCache(maxEntries = 5)
        val loader: (Int) -> String = { "text_$it" }

        // Load with negative index
        val result = cache.getOrLoad(-1, loader)
        assertEquals("text_-1", result)
        assertEquals(1, cache.size())

        // Verify it's cached
        val result2 = cache.getOrLoad(-1, loader)
        assertEquals("text_-1", result2)
        assertEquals(1, cache.size())
    }

    @Test
    fun `cache handles large unitIndex values`() {
        val cache = ReadingUnitCache(maxEntries = 5)
        val loader: (Int) -> String = { "text_$it" }

        // Load with large indices
        cache.getOrLoad(1000, loader)
        cache.getOrLoad(2000, loader)
        cache.getOrLoad(3000, loader)
        assertEquals(3, cache.size())

        // Verify all are cached
        assertEquals("text_1000", cache.getOrLoad(1000, loader))
        assertEquals("text_2000", cache.getOrLoad(2000, loader))
        assertEquals("text_3000", cache.getOrLoad(3000, loader))
    }

    @Test
    fun `evict then reload works correctly`() {
        val cache = ReadingUnitCache(maxEntries = 5)
        var loadCount = 0
        val loader: (Int) -> String = {
            loadCount++
            "v${loadCount}_$it"
        }

        // Load entry
        val result1 = cache.getOrLoad(42, loader)
        assertEquals("v1_42", result1)
        assertEquals(1, loadCount)

        // Evict entry
        cache.evict(42)
        assertEquals(0, cache.size())

        // Reload entry
        val result2 = cache.getOrLoad(42, loader)
        assertEquals("v2_42", result2)
        assertEquals(2, loadCount)
        assertEquals(1, cache.size())
    }

    // ── G5 Category 10: Additional Cache Edge Cases ──────────────────────

    @Test
    fun `cache with maxEntries 1 works correctly`() {
        val cache = ReadingUnitCache(maxEntries = 1)
        var loadCount = 0
        val loader: (Int) -> String = {
            loadCount++
            "text_$it"
        }

        // Load first entry
        cache.getOrLoad(0, loader)
        assertEquals(1, cache.size())
        assertEquals(1, loadCount)

        // Load second entry: should evict first
        cache.getOrLoad(1, loader)
        assertEquals(1, cache.size())
        assertEquals(2, loadCount)

        // First entry should be evicted
        cache.getOrLoad(0, loader)
        assertEquals(3, loadCount) // loader called again
    }

    @Test
    fun `cache handles zero unitIndex`() {
        val cache = ReadingUnitCache(maxEntries = 5)
        var loadCount = 0
        val loader: (Int) -> String = {
            loadCount++
            "text_$it"
        }

        // Load with index 0
        val result = cache.getOrLoad(0, loader)
        assertEquals("text_0", result)
        assertEquals(1, loadCount)
        assertEquals(1, cache.size())

        // Verify it's cached
        val result2 = cache.getOrLoad(0, loader)
        assertEquals("text_0", result2)
        assertEquals(1, loadCount) // still 1
    }

    @Test
    fun `multiple evicts work correctly`() {
        val cache = ReadingUnitCache(maxEntries = 5)
        val loader: (Int) -> String = { "text_$it" }

        // Load multiple entries
        for (i in 0 until 5) {
            cache.getOrLoad(i, loader)
        }
        assertEquals(5, cache.size())

        // Evict multiple entries
        cache.evict(0)
        cache.evict(2)
        cache.evict(4)
        assertEquals(2, cache.size())

        // Verify remaining entries are still cached
        var loadCount = 0
        val countingLoader: (Int) -> String = { loadCount++; "new_$it" }
        cache.getOrLoad(1, countingLoader)
        cache.getOrLoad(3, countingLoader)
        assertEquals(0, loadCount) // both still cached

        // Evicted entries should be re-loaded
        cache.getOrLoad(0, countingLoader)
        cache.getOrLoad(2, countingLoader)
        cache.getOrLoad(4, countingLoader)
        assertEquals(3, loadCount) // all 3 were re-loaded
    }
}
