package com.creationreadingassistant.data.settings

import com.creationreadingassistant.testutil.testDataStoreContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * 本书级覆盖的持久化测试（R3-P1，真实 DataStore 落到临时目录）。
 *
 * 注意：`preferencesDataStore` 委托在 JVM 内是进程级单例，临时目录跨用例持久，
 * 因此每个用例用**随机 bookId** 隔离，并采用"先写后读"的收敛式断言。
 * 另：`updateReader` 以 `reader.value` 为基底写回全部键，断言前需先让全局 Flow 回灌实际值
 * （`store.reader.first { ... }`），否则会基于 stale 的初始值做判断。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PerBookSettingsStoreTest {

    private lateinit var scope: CoroutineScope
    private lateinit var store: SettingsStore
    private lateinit var perBook: PerBookSettingsStore

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher())
        store = SettingsStore(testDataStoreContext(), scope)
        perBook = PerBookSettingsStore(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun bookId(): String = "test-" + UUID.randomUUID()

    @Test
    fun `only explicitly overridden keys are stored`() = runTest {
        val book = bookId()

        perBook.setOverride(book, ReaderOverrideKey.FONT_SIZE, "32.0")

        val overrides = perBook.snapshotOverrides(book)
        assertEquals(1, overrides.size)
        assertEquals("32.0", overrides[ReaderOverrideKey.FONT_SIZE])
        assertNull(overrides[ReaderOverrideKey.LINE_HEIGHT])
    }

    @Test
    fun `another book is unaffected`() = runTest {
        val a = bookId()
        val b = bookId()

        perBook.setOverride(a, ReaderOverrideKey.BACKGROUND, "night")

        assertTrue(perBook.snapshotOverrides(b).isEmpty())
        assertEquals("night", perBook.snapshotOverrides(a)[ReaderOverrideKey.BACKGROUND])
    }

    @Test
    fun `global change does not overwrite a book override`() = runTest {
        store.reader.first()
        val book = bookId()
        perBook.setOverride(book, ReaderOverrideKey.FONT_SIZE, "32.0")

        store.updateReader { copy(fontSize = 18f) }
        val globalLineHeight = store.reader.first { it.fontSize == 18f }.lineHeight

        val effective = perBook.effectiveReader(book).first { it.fontSize == 32f }
        assertEquals(32f, effective.fontSize)
        // 未覆盖项仍跟随全局
        assertEquals(globalLineHeight, effective.lineHeight)
    }

    @Test
    fun `clearing one override falls back to the global value`() = runTest {
        store.reader.first()
        val book = bookId()
        store.updateReader { copy(fontSize = 30f) }
        store.reader.first { it.fontSize == 30f }
        perBook.setOverride(book, ReaderOverrideKey.FONT_SIZE, "40.0")
        assertEquals(40f, perBook.effectiveReader(book).first { it.fontSize == 40f }.fontSize)

        perBook.clearOverride(book, ReaderOverrideKey.FONT_SIZE)

        assertTrue(perBook.snapshotOverrides(book).isEmpty())
        assertEquals(30f, perBook.effectiveReader(book).first { it.fontSize == 30f }.fontSize)
    }

    @Test
    fun `clear all overrides removes every key of that book`() = runTest {
        val book = bookId()
        perBook.setOverride(book, ReaderOverrideKey.FONT_SIZE, "31.0")
        perBook.setOverride(book, ReaderOverrideKey.BACKGROUND, "warm")
        assertEquals(2, perBook.snapshotOverrides(book).size)

        perBook.clearAllOverrides(book)

        assertTrue(perBook.snapshotOverrides(book).isEmpty())
    }

    @Test
    fun `illegal values are never persisted`() = runTest {
        val book = bookId()

        perBook.setOverride(book, ReaderOverrideKey.FONT_SIZE, "abc")
        perBook.setOverride(book, ReaderOverrideKey.BACKGROUND, "rainbow")
        perBook.setOverride(book, ReaderOverrideKey.FONT_BOLD, "yes")

        assertTrue(perBook.snapshotOverrides(book).isEmpty())
    }

    @Test
    fun `blank book id is a no-op instead of writing a global-looking key`() = runTest {
        perBook.setOverride("", ReaderOverrideKey.FONT_SIZE, "30.0")
        perBook.setOverride("   ", ReaderOverrideKey.FONT_SIZE, "30.0")

        assertTrue(perBook.snapshotOverrides("").isEmpty())
        assertTrue(perBook.snapshotOverrides("   ").isEmpty())
    }

    @Test
    fun `applying a preset to a book writes only the preset keys`() = runTest {
        val book = bookId()

        perBook.applyPresetToBook(book, ReadingPresets.LARGE_FONT)

        assertEquals(
            setOf(ReaderOverrideKey.FONT_SIZE, ReaderOverrideKey.LINE_HEIGHT),
            perBook.snapshotOverrides(book).keys,
        )
    }

    @Test
    fun `applying the default preset to a book clears its overrides`() = runTest {
        val book = bookId()
        perBook.applyPresetToBook(book, ReadingPresets.EYE_CARE)
        assertEquals(3, perBook.snapshotOverrides(book).size)

        perBook.applyPresetToBook(book, ReadingPresets.DEFAULT)

        assertTrue(perBook.snapshotOverrides(book).isEmpty())
    }

    @Test
    fun `global preset does not touch book overrides`() = runTest {
        store.reader.first()
        val book = bookId()
        perBook.setOverride(book, ReaderOverrideKey.BACKGROUND, "night")

        perBook.applyPresetToGlobal(ReadingPresets.LARGE_FONT)

        assertEquals(32f, store.reader.first { it.fontSize == 32f }.fontSize)
        assertEquals("night", perBook.snapshotOverrides(book)[ReaderOverrideKey.BACKGROUND])
    }

    @Test
    fun `reset global reader defaults keeps device level keys and book overrides`() = runTest {
        store.reader.first()
        val book = bookId()
        store.updateReader { copy(fontSize = 44f, brightness = 70) }
        store.reader.first { it.brightness == 70 }
        perBook.setOverride(book, ReaderOverrideKey.FONT_SIZE, "33.0")

        perBook.resetGlobalReaderToDefaults()

        val global = store.reader.first { it.fontSize == 25f }
        assertEquals(25f, global.fontSize)
        // 亮度属于设备级偏好，恢复默认不应清掉
        assertEquals(70, global.brightness)
        // 本书覆盖不受"恢复全局默认"影响
        assertEquals("33.0", perBook.snapshotOverrides(book)[ReaderOverrideKey.FONT_SIZE])
    }

    // ===================== D-2-A：引擎覆盖的持久化与隔离 =====================

    @Test
    fun `a book engine override is isolated from another book`() = runTest {
        store.reader.first()
        val bookA = bookId()
        val bookB = bookId()

        perBook.setOverride(bookA, ReaderOverrideKey.PAGER_ENGINE_MODE, "off")

        // book-A 生效，book-B 继续读全局
        val effectiveA = perBook.effectiveReader(bookA).first()
        val effectiveB = perBook.effectiveReader(bookB).first()
        assertEquals("book-A 必须读到自己的覆盖", "off", effectiveA.pagerEngineMode)
        assertEquals(
            "book-B 不得被 book-A 的覆盖污染",
            store.reader.first().pagerEngineMode,
            effectiveB.pagerEngineMode,
        )
        assertEquals("book-B 不得出现覆盖键", null, perBook.snapshotOverrides(bookB)[ReaderOverrideKey.PAGER_ENGINE_MODE])
    }

    @Test
    fun `a book engine override never changes the global value`() = runTest {
        store.reader.first()
        val book = bookId()
        val globalBefore = store.reader.first().pagerEngineMode

        perBook.setOverride(book, ReaderOverrideKey.PAGER_ENGINE_MODE, "off")
        perBook.setOverride(book, ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE, "on")

        val globalAfter = store.reader.first()
        assertEquals("全局 TXT 引擎值不得被本书覆盖改动", globalBefore, globalAfter.pagerEngineMode)
        assertEquals(
            "全局 EPUB 引擎值不得被本书覆盖改动",
            ReaderSettings().epubPagerEngineMode,
            globalAfter.epubPagerEngineMode,
        )
    }

    @Test
    fun `a book without an engine override keeps following the global value`() = runTest {
        // 旧数据兼容：没有任何引擎覆盖键的书，读到的就是全局值，
        // 不需要迁移、也不会被自动写入覆盖键。
        store.reader.first()
        val book = bookId()

        store.updateReader { copy(pagerEngineMode = "off") }
        val global = store.reader.first { it.pagerEngineMode == "off" }

        val effective = perBook.effectiveReader(book).first()
        assertEquals("无覆盖时必须跟随全局", global.pagerEngineMode, effective.pagerEngineMode)
        assertTrue(
            "不得自动创建覆盖键",
            perBook.snapshotOverrides(book).isEmpty(),
        )
    }

    @Test
    fun `an illegal engine override is never persisted`() = runTest {
        val book = bookId()

        perBook.setOverride(book, ReaderOverrideKey.PAGER_ENGINE_MODE, "sometimes")

        assertTrue(
            "非法引擎值不得落盘",
            perBook.snapshotOverrides(book)[ReaderOverrideKey.PAGER_ENGINE_MODE] == null,
        )
    }

    @Test
    fun `applying a preset to a book preserves its engine overrides`() = runTest {
        val book = bookId()
        perBook.setOverride(book, ReaderOverrideKey.PAGER_ENGINE_MODE, "off")
        perBook.setOverride(book, ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE, "on")
        perBook.setOverride(book, ReaderOverrideKey.FONT_SIZE, "44.0")

        perBook.applyPresetToBook(book, ReadingPresets.LARGE_FONT)

        val after = perBook.snapshotOverrides(book)
        assertEquals("引擎覆盖不得被预设清掉", "off", after[ReaderOverrideKey.PAGER_ENGINE_MODE])
        assertEquals("引擎覆盖不得被预设清掉", "on", after[ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE])
        assertEquals("预设原本负责的项仍按既有语义写入", "32.0", after[ReaderOverrideKey.FONT_SIZE])
    }

    @Test
    fun `applying the default preset to a book keeps engine overrides`() = runTest {
        // 「默认」预设只清**预设负责的**覆盖键；引擎是兼容性开关，不属于视觉预设。
        val book = bookId()
        perBook.setOverride(book, ReaderOverrideKey.PAGER_ENGINE_MODE, "off")
        perBook.setOverride(book, ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE, "on")
        perBook.setOverride(book, ReaderOverrideKey.BACKGROUND, "night")

        perBook.applyPresetToBook(book, ReadingPresets.DEFAULT)

        val after = perBook.snapshotOverrides(book)
        assertEquals("引擎覆盖必须保留", "off", after[ReaderOverrideKey.PAGER_ENGINE_MODE])
        assertEquals("引擎覆盖必须保留", "on", after[ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE])
        assertTrue("预设负责的项必须被清除", after[ReaderOverrideKey.BACKGROUND] == null)
    }

    @Test
    fun `applying the default preset still clears every preset owned override`() = runTest {
        // 既有契约不得回退：套用「默认」后，预设负责的覆盖必须干净。
        val book = bookId()
        perBook.applyPresetToBook(book, ReadingPresets.EYE_CARE)
        assertTrue(perBook.snapshotOverrides(book).isNotEmpty())

        perBook.applyPresetToBook(book, ReadingPresets.DEFAULT)

        val remaining = perBook.snapshotOverrides(book).keys
        assertTrue(
            "预设负责的覆盖必须全部清掉，实际剩余 $remaining",
            remaining.none { it in ReadingPresets.PRESET_OWNED_KEYS },
        )
    }

    @Test
    fun `clear all overrides still removes the engine overrides`() = runTest {
        // 「全部跟随全局」语义不变：必须清掉**全部**覆盖，含两个引擎键。
        val book = bookId()
        perBook.setOverride(book, ReaderOverrideKey.PAGER_ENGINE_MODE, "off")
        perBook.setOverride(book, ReaderOverrideKey.EPUB_PAGER_ENGINE_MODE, "on")
        perBook.setOverride(book, ReaderOverrideKey.FONT_SIZE, "33.0")

        perBook.clearAllOverrides(book)

        assertTrue("全部跟随全局必须清空所有覆盖", perBook.snapshotOverrides(book).isEmpty())
    }

    @Test
    fun `reset global defaults restores both engine modes and keeps device settings`() = runTest {
        store.reader.first()
        store.updateReader { copy(pagerEngineMode = "off", epubPagerEngineMode = "on", brightness = 70) }
        store.reader.first { it.pagerEngineMode == "off" }

        perBook.resetGlobalReaderToDefaults()

        val global = store.reader.first { it.pagerEngineMode == ReaderSettings().pagerEngineMode }
        assertEquals("TXT 引擎必须恢复结构默认", ReaderSettings().pagerEngineMode, global.pagerEngineMode)
        assertEquals("EPUB 引擎必须恢复结构默认", ReaderSettings().epubPagerEngineMode, global.epubPagerEngineMode)
        assertEquals("设备级亮度不得被恢复默认清掉", 70, global.brightness)
    }
}
