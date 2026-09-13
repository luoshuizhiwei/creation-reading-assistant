package com.creationreadingassistant.data.settings

import com.creationreadingassistant.testutil.testDataStoreContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 选区工具条动作配置（R3-X1）的纯函数测试：渲染清单、脏值收敛、模板校验。
 */
class SelectionActionsTest {

    @Test
    fun `action ids are unique and groups cover every definition`() {
        val ids = SelectionActions.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            SelectionActions.ALL.size,
            SelectionActions.primaryDefs().size +
                SelectionActions.moreDefs().size +
                SelectionActions.ALL.count { it.group == SelectionActionGroup.FIXED },
        )
    }

    @Test
    fun `cancel is the only fixed action so it cannot be hidden`() {
        val fixed = SelectionActions.ALL.filter { it.group == SelectionActionGroup.FIXED }.map { it.id }

        assertEquals(listOf("cancel"), fixed)
    }

    @Test
    fun `effectivePrimary follows the enabled set in definition order`() {
        val settings = SelectionActionSettings(enabledPrimary = setOf("copy", "highlight"))

        assertEquals(
            listOf("highlight", "copy"),
            SelectionActions.effectivePrimary(settings).map { it.id },
        )
    }

    @Test
    fun `effectivePrimary never renders an empty toolbar`() {
        val settings = SelectionActionSettings(enabledPrimary = emptySet())

        assertEquals(SelectionActions.MIN_PRIMARY_SLOTS, SelectionActions.effectivePrimary(settings).size)
    }

    @Test
    fun `effectiveMore keeps only enabled actions and always ends with cancel`() {
        val settings = SelectionActionSettings(enabledMore = setOf("search", "ai"))

        val ids = SelectionActions.effectiveMore(settings).map { it.id }

        assertEquals(listOf("search", "ai", "cancel"), ids)
    }

    @Test
    fun `sanitize drops unknown ids and refills an empty primary set`() {
        val cleaned = SelectionActions.sanitize(
            SelectionActionSettings(
                enabledPrimary = setOf("highlight", "no-such-action"),
                enabledMore = setOf("note", "ghost"),
            ),
        )

        assertEquals(setOf("highlight"), cleaned.enabledPrimary)
        assertEquals(setOf("note"), cleaned.enabledMore)

        val emptyPrimary = SelectionActions.sanitize(SelectionActionSettings(enabledPrimary = emptySet()))
        assertEquals(SelectionActions.DEFAULT_PRIMARY, emptyPrimary.enabledPrimary)
    }

    @Test
    fun `sanitize rejects templates without a query placeholder`() {
        val cleaned = SelectionActions.sanitize(
            SelectionActionSettings(
                browserUrlTemplate = "https://example.com/search",
                dictionaryUrlTemplate = "not a url {q}",
            ),
        )

        assertEquals(SelectionActions.DEFAULT_BROWSER_TEMPLATE, cleaned.browserUrlTemplate)
        assertEquals(SelectionActions.DEFAULT_DICTIONARY_TEMPLATE, cleaned.dictionaryUrlTemplate)
    }

    @Test
    fun `sanitize rejects an unknown dictionary mode`() {
        val cleaned = SelectionActions.sanitize(SelectionActionSettings(dictionaryMode = "telepathy"))

        assertEquals(SelectionActions.MODE_OFFLINE, cleaned.dictionaryMode)
    }

    @Test
    fun `sanitize keeps a valid custom configuration untouched`() {
        val custom = SelectionActionSettings(
            enabledPrimary = setOf("highlight"),
            enabledMore = setOf("dictionary"),
            browserUrlTemplate = "https://duckduckgo.com/?q={q}",
            dictionaryUrlTemplate = "https://zdic.net/hans/{q}",
            dictionaryMode = SelectionActions.MODE_ONLINE,
        )

        assertEquals(custom, SelectionActions.sanitize(custom))
    }

    @Test
    fun `sanitize truncates an oversized primary set`() {
        val cleaned = SelectionActions.sanitize(
            SelectionActionSettings(enabledPrimary = setOf("highlight", "browser", "copy", "search")),
        )

        assertEquals(SelectionActions.MAX_PRIMARY_SLOTS, cleaned.enabledPrimary.size)
        assertEquals(
            listOf("highlight", "browser", "copy"),
            SelectionActions.effectivePrimary(cleaned).map { it.id },
        )
    }

    @Test
    fun `buildUrl substitutes the encoded query`() {
        assertEquals(
            "https://duckduckgo.com/?q=%E8%8B%B9%E6%9E%9C",
            SelectionActions.buildUrl("https://duckduckgo.com/?q={q}", "%E8%8B%B9%E6%9E%9C"),
        )
    }

    @Test
    fun `buildUrl refuses an invalid template`() {
        assertNull(SelectionActions.buildUrl("https://example.com/x", "q"))
        assertNull(SelectionActions.buildUrl("", "q"))
        assertNull(SelectionActions.buildUrl("javascript:alert(1)?q={q}", "q"))
    }

    @Test
    fun `isValidTemplate requires http and a placeholder`() {
        assertTrue(SelectionActions.isValidTemplate("https://a.com/?q={q}"))
        assertTrue(SelectionActions.isValidTemplate("http://a.com/?q={q}"))
        assertFalse(SelectionActions.isValidTemplate("https://a.com/"))
        assertFalse(SelectionActions.isValidTemplate("file:///{q}"))
    }

    @Test
    fun `isValidTemplate rejects a malformed scheme that merely starts with http`() {
        // 模板最终会进 Intent.ACTION_VIEW；前缀必须落到具体 scheme，
        // 不能让 "httpfoo://" 这种畸形前缀通过校验。
        assertFalse(SelectionActions.isValidTemplate("httpfoo://a.com/?q={q}"))
        assertFalse(SelectionActions.isValidTemplate("httpsx://a.com/?q={q}"))
        assertFalse(SelectionActions.isValidTemplate("javascript:alert(1)?q={q}"))
    }

    @Test
    fun `isValidTemplate accepts an upper case scheme`() {
        // URL scheme 本身大小写不敏感；拒绝它只会让用户粘贴的模板被静默换回默认值。
        assertTrue(SelectionActions.isValidTemplate("HTTPS://a.com/?q={q}"))
        assertTrue(SelectionActions.isValidTemplate("Http://a.com/?q={q}"))
    }

    @Test
    fun `sanitize falls back to the default when the scheme is malformed`() {
        val cleaned = SelectionActions.sanitize(
            SelectionActionSettings(
                browserUrlTemplate = "httpfoo://a.com/?q={q}",
                dictionaryUrlTemplate = "httpsx://a.com/?q={q}",
            ),
        )

        assertEquals(SelectionActions.DEFAULT_BROWSER_TEMPLATE, cleaned.browserUrlTemplate)
        assertEquals(SelectionActions.DEFAULT_DICTIONARY_TEMPLATE, cleaned.dictionaryUrlTemplate)
    }
}

/**
 * 配置的持久化测试（真实 DataStore 落到临时目录）。
 *
 * 注意：JVM 内 DataStore 是**进程级单例**，用例之间会互相看见对方写入，
 * 因此每个用例先 `resetToDefaults()` 建立基线，再断言「写后读」的收敛结果。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SelectionActionStoreTest {

    private lateinit var scope: CoroutineScope
    private lateinit var store: SelectionActionStore

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher())
        store = SelectionActionStore(SettingsStore(testDataStoreContext(), scope))
        runBlocking { store.resetToDefaults() }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `empty store yields the documented defaults`() = runTest {
        val settings = store.settings.first()

        assertEquals(SelectionActions.DEFAULT_PRIMARY, settings.enabledPrimary)
        assertEquals(SelectionActions.DEFAULT_MORE, settings.enabledMore)
        assertEquals(SelectionActions.DEFAULT_BROWSER_TEMPLATE, settings.browserUrlTemplate)
        assertEquals(SelectionActions.DEFAULT_DICTIONARY_TEMPLATE, settings.dictionaryUrlTemplate)
        assertEquals(SelectionActions.MODE_OFFLINE, settings.dictionaryMode)
    }

    @Test
    fun `disabling a more action persists and removes it from the render list`() = runTest {
        val before = store.settings.first()

        store.setActionEnabled(before, "ai", enabled = false)

        val after = store.settings.first()
        assertFalse("ai" in after.enabledMore)
        assertFalse(SelectionActions.effectiveMore(after).any { it.id == "ai" })
        assertTrue("cancel 必须仍在", SelectionActions.effectiveMore(after).any { it.id == "cancel" })
    }

    @Test
    fun `disabling the last primary action is refused`() = runTest {
        val onlyHighlight = store.settings.first().copy(enabledPrimary = setOf("highlight"))
        store.setActionEnabled(onlyHighlight, "highlight", enabled = false)

        val after = store.settings.first()

        assertTrue("高亮不能被关掉", "highlight" in after.enabledPrimary)
    }

    @Test
    fun `fixed actions cannot be disabled`() = runTest {
        val before = store.settings.first()

        store.setActionEnabled(before, "cancel", enabled = false)

        assertEquals(before, store.settings.first())
    }

    @Test
    fun `re-enabling a more action brings it back`() = runTest {
        val before = store.settings.first()
        store.setActionEnabled(before, "replace", enabled = false)
        store.setActionEnabled(store.settings.first(), "replace", enabled = true)

        assertTrue("replace" in store.settings.first().enabledMore)
    }

    @Test
    fun `an invalid browser template is never persisted`() = runTest {
        val before = store.settings.first()

        store.setBrowserUrlTemplate("https://example.com/search")

        assertEquals(before.browserUrlTemplate, store.settings.first().browserUrlTemplate)
    }

    @Test
    fun `a valid browser template is persisted after trimming`() = runTest {
        store.setBrowserUrlTemplate("  https://duckduckgo.com/?q={q}  ")

        assertEquals("https://duckduckgo.com/?q={q}", store.settings.first().browserUrlTemplate)
    }

    @Test
    fun `an unknown dictionary mode is never persisted`() = runTest {
        val before = store.settings.first()

        store.setDictionaryMode("telepathy")

        assertEquals(before.dictionaryMode, store.settings.first().dictionaryMode)
        store.setDictionaryMode(SelectionActions.MODE_SYSTEM)
        assertEquals(SelectionActions.MODE_SYSTEM, store.settings.first().dictionaryMode)
    }

    @Test
    fun `resetToDefaults clears every customisation`() = runTest {
        val before = store.settings.first()
        store.setActionEnabled(before, "ai", enabled = false)
        store.setBrowserUrlTemplate("https://duckduckgo.com/?q={q}")
        store.setDictionaryMode(SelectionActions.MODE_ONLINE)

        store.resetToDefaults()

        val after = store.settings.first()
        assertEquals(SelectionActions.DEFAULT_MORE, after.enabledMore)
        assertEquals(SelectionActions.DEFAULT_BROWSER_TEMPLATE, after.browserUrlTemplate)
        assertEquals(SelectionActions.MODE_OFFLINE, after.dictionaryMode)
    }

    // ============================== R3-X1：高频槽自由落位 ==============================

    @Test
    fun `promoting a more action into the primary row persists`() = runTest {
        // 默认第一屏已满（高亮/浏览器/复制），先腾一个槽位再放「书内搜索」
        store.setActionEnabled(store.settings.first(), "copy", enabled = false, target = SelectionActionGroup.PRIMARY)
        store.setActionEnabled(store.settings.first(), "search", enabled = true, target = SelectionActionGroup.PRIMARY)

        val after = store.settings.first()
        assertTrue("search" in after.enabledPrimary)
        assertFalse("上了第一屏就不该再在溢出菜单重复出现", SelectionActions.effectiveMore(after).any { it.id == "search" })
    }

    @Test
    fun `demoting a primary action back to the more menu persists`() = runTest {
        store.setActionEnabled(store.settings.first(), "copy", enabled = false, target = SelectionActionGroup.PRIMARY)

        val mid = store.settings.first()
        assertFalse("copy" in mid.enabledPrimary)

        store.setActionEnabled(mid, "copy", enabled = true, target = SelectionActionGroup.MORE)

        assertTrue("copy" in store.settings.first().enabledMore)
    }

    @Test
    fun `an over-full primary row is refused`() = runTest {
        val full = store.settings.first().copy(enabledPrimary = setOf("highlight", "browser", "copy"))

        store.setActionEnabled(full, "search", enabled = true, target = SelectionActionGroup.PRIMARY)

        assertEquals(full.enabledPrimary, store.settings.first().enabledPrimary)
    }

    @Test
    fun `cancel can never be pinned into the primary row`() = runTest {
        val before = store.settings.first()

        store.setActionEnabled(before, "cancel", enabled = true, target = SelectionActionGroup.PRIMARY)

        assertEquals(before.enabledPrimary, store.settings.first().enabledPrimary)
    }
}
