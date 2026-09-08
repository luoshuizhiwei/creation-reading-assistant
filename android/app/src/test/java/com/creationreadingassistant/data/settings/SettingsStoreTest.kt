package com.creationreadingassistant.data.settings

import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import com.creationreadingassistant.data.security.SecurePrefs
import com.creationreadingassistant.testutil.InMemorySharedPreferences
import com.creationreadingassistant.testutil.testDataStoreContext
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * SettingsStore 持久化读写往返测试（真实 DataStore 落盘到临时目录）。
 *
 * 注意：preferencesDataStore 委托在 JVM 内全局缓存同一实例，且临时目录跨运行持久，
 * 因此所有断言采用「先写后读」的收敛式写法，不假设初始为空。
 * AI API Key 的加密存储用 mockkObject(SecurePrefs) 拦截到内存实现，
 * 避开 JVM 上不可靠的 EncryptedSharedPreferences。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {

    private lateinit var scope: CoroutineScope
    private lateinit var store: SettingsStore
    private val securePrefsMap = HashMap<String, SharedPreferences>()

    @Before
    fun setUp() {
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } answers {
            securePrefsMap.getOrPut(secondArg()) { InMemorySharedPreferences() }
        }
        scope = CoroutineScope(UnconfinedTestDispatcher())
        store = SettingsStore(testDataStoreContext(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkObject(SecurePrefs)
    }

    @Test
    fun `appearance update persists and reads back`() = runTest {
        store.appearance.first() // 等待磁盘状态加载，避免基于旧缓存值更新
        store.updateAppearance {
            copy(themeMode = "dark", colorPalette = "green_tea", paperTexture = true)
        }

        val loaded = store.appearance.first { it.themeMode == "dark" }
        assertEquals("dark", loaded.themeMode)
        assertEquals("green_tea", loaded.colorPalette)
        assertTrue(loaded.paperTexture)
    }

    @Test
    fun `reader update persists typography and header footer`() = runTest {
        store.reader.first()
        store.updateReader {
            copy(
                readerMode = "scroll",
                fontSize = 30f,
                lineHeight = 2f,
                background = "warm",
                headerLeft = HeaderFooterItem.BOOK_NAME,
                footerRight = HeaderFooterItem.TIME,
            )
        }

        val loaded = store.reader.first { it.readerMode == "scroll" }
        assertEquals("scroll", loaded.readerMode)
        assertEquals(30f, loaded.fontSize)
        assertEquals(2f, loaded.lineHeight)
        assertEquals("warm", loaded.background)
        assertEquals(HeaderFooterItem.BOOK_NAME, loaded.headerLeft)
        assertEquals(HeaderFooterItem.TIME, loaded.footerRight)
    }

    @Test
    fun `reader reveal page turn effect persists without compatibility downgrade`() = runTest {
        store.reader.first()
        store.updateReader { copy(pageTurnEffect = "reveal") }

        val loaded = store.reader.first { it.pageTurnEffect == "reveal" }
        assertEquals("reveal", loaded.pageTurnEffect)
    }

    @Test
    fun `custom font path round trip`() = runTest {
        store.reader.first()
        store.updateReader { copy(customFontPath = "/data/files/fonts/LXGWWenKai.otf") }

        val loaded = store.reader.first { it.customFontPath.isNotBlank() }
        assertEquals("/data/files/fonts/LXGWWenKai.otf", loaded.customFontPath)

        // 恢复系统字体（空路径）也能落盘
        store.updateReader { copy(customFontPath = "") }
        val cleared = store.reader.first { it.customFontPath.isEmpty() }
        assertEquals("", cleared.customFontPath)
    }

    @Test
    fun `fixed brightness is recorded as last fixed brightness`() = runTest {
        store.reader.first()
        store.updateReader { copy(brightness = 75) }

        val loaded = store.reader.first { it.brightness == 75 }
        assertEquals(75, loaded.brightness)
        assertEquals(75, loaded.lastFixedBrightness)
    }

    @Test
    fun `legacy defaults migration converts characteristic values via store seam`() = runTest {
        val ctx = testDataStoreContext()
        val fresh = SettingsStore(ctx, scope)
        fresh.reader.first()
        // 预置升级设备残留的旧默认特征值并清除 marker，随后经正式迁移 seam 收敛
        fresh.preferencesDataStore.edit {
            it[KEY_READER_BRIGHTNESS] = 100
            it[KEY_FONT_SIZE] = 18f
            it[KEY_IMMERSIVE] = false
            it.remove(KEY_LEGACY_DEFAULTS_MIGRATED)
        }
        fresh.migrateLegacyReaderDefaultsOnce()

        val loaded = fresh.reader.first { it.brightness == -1 }
        assertEquals(-1, loaded.brightness)
        assertEquals(25f, loaded.fontSize)
        assertEquals(true, loaded.immersiveMode)
    }

    @Test
    fun `legacy defaults migration is idempotent and preserves later custom values`() = runTest {
        store.reader.first()
        store.updateReader { copy(brightness = 42, fontSize = 20f, immersiveMode = false) }
        // 已迁移（marker）后再跑一次不得覆盖用户后来的自定义值
        store.migrateLegacyReaderDefaultsOnce()

        val loaded = store.reader.first { it.brightness == 42 }
        assertEquals(42, loaded.brightness)
        assertEquals(20f, loaded.fontSize)
        assertEquals(false, loaded.immersiveMode)
    }

    @Test
    fun `inspiration sort round trip and rejects invalid values`() = runTest {
        store.setInspirationSort("title")
        assertEquals("title", store.inspirationSort.first { it == "title" })

        // 非法值被忽略，保持上一次合法值
        store.setInspirationSort("bogus")
        assertEquals("title", store.inspirationSort.first())
    }

    @Test
    fun `tts resume round trip`() = runTest {
        val bookId = "book-${UUID.randomUUID()}"
        store.saveTtsResume(bookId, chapterIndex = 3, offset = 42)

        val loaded = store.loadTtsResume()
        assertEquals(bookId, loaded?.bookId)
        assertEquals(3, loaded?.chapterIndex)
        assertEquals(42, loaded?.offset)
    }

    @Test
    fun `txt toc rule round trip and rejects unknown rule`() = runTest {
        val bookId = "book-${UUID.randomUUID()}"

        store.saveTxtTocRule(bookId, "num-dot")
        assertEquals("num-dot", store.loadTxtTocRule(bookId))

        // 非法 ruleId 不落盘，仍读回上一次合法值
        store.saveTxtTocRule(bookId, "evil-rule")
        assertEquals("num-dot", store.loadTxtTocRule(bookId))

        // 从未保存过的书回落到 builtin
        assertEquals("builtin", store.loadTxtTocRule("book-${UUID.randomUUID()}"))
    }

    @Test
    fun `ai settings round trip with api key in encrypted store`() = runTest {
        // SecurePrefs 已被拦截到内存实现，往返语义与加密存储一致
        store.ai.first()
        store.updateAi {
            copy(enabled = true, baseUrl = "https://ai.example.com", model = "m1", temperature = 0.3f, apiKey = "sk-roundtrip", prompt = "提示词")
        }

        // 用新实例重新从落盘存储读取，验证真实持久化往返（含加密 Key）
        val reloaded = SettingsStore(testDataStoreContext(), scope)
        val loaded = reloaded.ai.first { it.apiKey == "sk-roundtrip" }
        assertTrue(loaded.enabled)
        assertEquals("https://ai.example.com", loaded.baseUrl)
        assertEquals("m1", loaded.model)
        assertEquals(0.3f, loaded.temperature)
        assertEquals("sk-roundtrip", loaded.apiKey)
        assertEquals("提示词", loaded.prompt)

        // 恢复为关闭态，避免污染其它依赖 ai 状态的测试观察（同 JVM 共享落盘）
        store.updateAi { copy(enabled = false, apiKey = "") }
    }
}
