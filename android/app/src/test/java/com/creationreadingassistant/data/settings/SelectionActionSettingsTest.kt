package com.creationreadingassistant.data.settings

import androidx.datastore.preferences.core.stringSetPreferencesKey
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
        // 声明顺序即渲染顺序：copy 先于 highlight 声明，所以先渲染 copy。
        val settings = SelectionActionSettings(enabledPrimary = setOf("copy", "highlight"))

        assertEquals(
            listOf("copy", "highlight"),
            SelectionActions.effectivePrimary(settings).map { it.id },
        )
    }

    @Test
    fun `default primary row renders copy browser replace then dictionary`() {
        assertEquals(
            listOf("copy", "browser", "replace", "dictionary"),
            SelectionActions.effectivePrimary(SelectionActionSettings()).map { it.id },
        )
    }

    @Test
    fun `edit is dormant until the chapter editor is wired`() {
        // S3a 接线前「编辑」不在任何默认集合里 —— 不放点了没反应的死按钮。
        val settings = SelectionActionSettings()

        assertFalse("edit" in settings.enabledPrimary)
        assertFalse("edit" in settings.enabledMore)
        assertFalse(SelectionActions.effectiveMore(settings).any { it.id == "edit" })
    }

    @Test
    fun `dormant edit is neither configurable nor renderable`() {
        // 缺陷 A：只从默认集合移除是不够的 —— 用户仍能在设置页把它勾出来，
        // 而工具栏分发没有 edit 分支，点下去毫无反应。可配置清单本身必须排除它。
        assertFalse(
            "edit 不该出现在设置页的可配置清单里",
            SelectionActions.pickableDefs().any { it.id == "edit" },
        )

        // 即使用户配置或旧磁盘数据把它塞进两个集合，也不得渲染。
        val forced = SelectionActionSettings(
            enabledPrimary = setOf("copy", "edit"),
            enabledMore = setOf("edit", "note"),
        )
        assertFalse("高频槽不得渲染 edit", SelectionActions.effectivePrimary(forced).any { it.id == "edit" })
        assertFalse("溢出菜单不得渲染 edit", SelectionActions.effectiveMore(forced).any { it.id == "edit" })
    }

    @Test
    fun `sanitize drops a stored dormant action id`() {
        // 旧版本或手改的磁盘数据里可能已经存了 edit；读路径必须收敛掉它，
        // 否则它会在工具栏上复活成一个点了没反应的槽位。
        val cleaned = SelectionActions.sanitize(
            SelectionActionSettings(
                enabledPrimary = setOf("copy", "edit"),
                enabledMore = setOf("edit", "note"),
            ),
        )

        assertFalse("edit" in cleaned.enabledPrimary)
        assertFalse("edit" in cleaned.enabledMore)
        assertTrue("copy" in cleaned.enabledPrimary)
        assertTrue("note" in cleaned.enabledMore)
    }

    @Test
    fun `edit keeps its declared landing slot for the chapter editor slice`() {
        // S3a 接线后「编辑」应落在「替换」与「字典」之间。这里只锁**声明顺序**这一事实，
        // 不假装它当前可渲染 —— 可渲染性由 `dormant edit is neither configurable...` 锁定。
        val declared = SelectionActions.ALL.map { it.id }
        val editAt = declared.indexOf("edit")

        assertTrue("edit 定义必须保留给 S08 章节编辑器", editAt >= 0)
        assertEquals("replace", declared[editAt - 1])
        assertEquals("dictionary", declared[editAt + 1])
    }

    @Test
    fun `a legacy untouched primary row is upgraded to the new default`() {
        assertEquals(
            SelectionActions.DEFAULT_PRIMARY,
            SelectionActions.resolveStoredPrimary(SelectionActions.LEGACY_DEFAULT_PRIMARY),
        )
    }

    @Test
    fun `a customised primary row is never rewritten`() {
        // 自定义过就原样保留，哪怕只有 3 个 —— 不替用户凭空补动作。
        val custom = setOf("highlight", "copy")

        assertEquals(custom, SelectionActions.resolveStoredPrimary(custom))
    }

    @Test
    fun `a missing primary row falls back to the new default`() {
        assertEquals(SelectionActions.DEFAULT_PRIMARY, SelectionActions.resolveStoredPrimary(null))
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
        // 6 个 > 上限 5：截断到上限（截断顺序由集合自身迭代序决定，渲染序仍以声明序为准）。
        val cleaned = SelectionActions.sanitize(
            SelectionActionSettings(
                enabledPrimary = setOf("highlight", "browser", "copy", "search", "ai", "note"),
            ),
        )

        assertEquals(SelectionActions.MAX_PRIMARY_SLOTS, cleaned.enabledPrimary.size)
        assertEquals(SelectionActions.MAX_PRIMARY_SLOTS, SelectionActions.effectivePrimary(cleaned).size)
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
    private lateinit var settingsStore: SettingsStore
    private lateinit var store: SelectionActionStore

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher())
        settingsStore = SettingsStore(testDataStoreContext(), scope)
        store = SelectionActionStore(settingsStore)
        runBlocking { store.resetToDefaults() }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /**
     * 读**落盘的原始集合**，绕过 [SelectionActions.sanitize]。
     *
     * 写侧拒绝必须在这一层验证：读侧 sanitize 会把 dormant id 过滤掉，只看 `store.settings`
     * 的话，即使写侧真的把 `edit` 写进了磁盘也观察不到 —— 测试会被读侧兜底「救绿」。
     */
    private suspend fun rawPersisted(key: String): Set<String> =
        settingsStore.preferencesDataStore.data.first()[stringSetPreferencesKey(key)] ?: emptySet()

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
        // 先把高频槽真实收敛到只剩「高亮」（每一步都保证不空），再试图关掉最后一个。
        // 不能用 copy() 造一个从未落盘的配置 —— 那断言的是内存对象，不是持久化行为。
        store.setActionEnabled(
            store.settings.first(),
            "highlight",
            enabled = true,
            target = SelectionActionGroup.PRIMARY,
        )
        listOf("copy", "browser", "replace", "dictionary").forEach { id ->
            store.setActionEnabled(store.settings.first(), id, enabled = false, target = SelectionActionGroup.PRIMARY)
        }
        val onlyHighlight = store.settings.first()
        assertEquals(setOf("highlight"), onlyHighlight.enabledPrimary)

        store.setActionEnabled(onlyHighlight, "highlight", enabled = false)

        assertTrue("高亮不能被关掉", "highlight" in store.settings.first().enabledPrimary)
    }

    @Test
    fun `fixed actions cannot be disabled`() = runTest {
        val before = store.settings.first()

        store.setActionEnabled(before, "cancel", enabled = false)

        assertEquals(before, store.settings.first())
    }

    @Test
    fun `a dormant action is refused on the write side`() = runTest {
        // S07-F4.1：读侧 pickableDefs() 早已把 dormant 的「编辑」挡在可配置清单外，
        // 但写侧从未拒绝 —— 一个旧版本写下的 edit、或者绕过设置页的直接调用，
        // 就能把它塞进持久化集合，等将来接线时突然出现在工具条上。
        // 这里钉的是写侧也必须拒绝：dormant 是**双向**封闭的。
        //
        // 断言必须打在**落盘原始集合**上：读侧 sanitize 会兜底过滤，只看 store.settings
        // 的话写侧漏了也看不出来（会被读侧救绿）。
        val dormantId = SelectionActions.DORMANT_IDS.single()
        val rawMoreBefore = rawPersisted("selection_actions_more")

        store.setActionEnabled(
            store.settings.first(),
            dormantId,
            enabled = true,
            target = SelectionActionGroup.MORE,
        )
        store.setActionEnabled(
            store.settings.first(),
            dormantId,
            enabled = true,
            target = SelectionActionGroup.PRIMARY,
        )

        assertFalse(
            "dormant 动作不得被写入溢出菜单（原始集合）",
            dormantId in rawPersisted("selection_actions_more"),
        )
        assertFalse(
            "dormant 动作不得被写入高频槽（原始集合）",
            dormantId in rawPersisted("selection_actions_primary"),
        )
        assertEquals(
            "拒绝写入时溢出菜单必须原样保留",
            rawMoreBefore,
            rawPersisted("selection_actions_more"),
        )
        assertFalse(
            "dormant 动作不得出现在任何可渲染集合里",
            SelectionActions.effectiveMore(store.settings.first()).any { it.id == dormantId } ||
                SelectionActions.effectivePrimary(store.settings.first()).any { it.id == dormantId },
        )
    }

    @Test
    fun `a dormant action can never be removed either`() = runTest {
        // 反向：禁用路径同样拒绝。dormant 的语义是「完全不参与配置」，
        // 不是「只能加不能减」—— 否则一次 enabled=false 也会写盘。
        val dormantId = SelectionActions.DORMANT_IDS.single()
        val rawBefore = rawPersisted("selection_actions_more")

        store.setActionEnabled(
            store.settings.first(),
            dormantId,
            enabled = false,
            target = SelectionActionGroup.MORE,
        )

        assertEquals(rawBefore, rawPersisted("selection_actions_more"))
    }

    @Test
    fun `a non dormant action still writes to the raw set`() = runTest {
        // 对照组：证明上面的「原始集合」断言不是恒真 —— 一个正常动作确实会写盘。
        val rawBefore = rawPersisted("selection_actions_more")

        store.setActionEnabled(
            store.settings.first(),
            "ai",
            enabled = false,
            target = SelectionActionGroup.MORE,
        )

        val rawAfter = rawPersisted("selection_actions_more")
        assertTrue("对照动作必须真的写盘", rawAfter != rawBefore)
        assertFalse("ai" in rawAfter)
    }

    @Test
    fun `re-enabling a more action brings it back`() = runTest {
        // 用出厂就在溢出菜单的「添加批注」；不要用已经挪上第一屏的动作来测溢出菜单。
        val before = store.settings.first()
        store.setActionEnabled(before, "note", enabled = false)
        assertFalse("note" in store.settings.first().enabledMore)

        store.setActionEnabled(store.settings.first(), "note", enabled = true)

        assertTrue("note" in store.settings.first().enabledMore)
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
        // 默认第一屏是 复制/浏览器/替换/字典（4 槽，上限 5）；先腾一个再放「书内搜索」
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
        // 真实补到 5 槽（上限）后再放第 6 个：应被拒绝（塞进去会把胶囊挤到不可点）。
        store.setActionEnabled(
            store.settings.first(),
            "highlight",
            enabled = true,
            target = SelectionActionGroup.PRIMARY,
        )
        val full = store.settings.first()
        assertEquals(SelectionActions.MAX_PRIMARY_SLOTS, full.enabledPrimary.size)

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
