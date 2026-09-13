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
}
