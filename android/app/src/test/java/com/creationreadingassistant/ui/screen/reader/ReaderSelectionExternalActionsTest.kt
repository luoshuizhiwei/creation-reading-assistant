package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSelectionExternalActionsTest {

    @Test
    fun `browser query is trimmed collapsed bounded and encoded`() {
        val input = "  Kotlin\n  Compose  " + "字".repeat(600)

        val normalized = normalizedSelectionQuery(input, 500)
        val url = selectionBrowserUrl(input)

        assertEquals(500, normalized.length)
        assertTrue(normalized.startsWith("Kotlin Compose"))
        assertTrue(url!!.startsWith("https://www.bing.com/search?q=Kotlin+Compose"))
    }

    @Test
    fun `dictionary lookup uses a shorter encoded query`() {
        val url = selectionDictionaryUrl("  一期一会 / once  ")

        assertEquals(
            "https://dict.youdao.com/result?word=%E4%B8%80%E6%9C%9F%E4%B8%80%E4%BC%9A+%2F+once&lang=auto",
            url,
        )
    }

    @Test
    fun `blank selection produces no external url`() {
        assertNull(selectionBrowserUrl(" \n "))
        assertNull(selectionDictionaryUrl(" \t "))
    }

    // ===================== R3-X1 缺陷收口：空选区 / 代理对截断 / 空选区提示 =====================

    @Test
    fun `a blank selection gets an understandable notice before any intent is built`() {
        // 空选区走的是与「没有可用处理器」同一条分支，调用方若只看返回值就会误报
        // 「未找到可用浏览器」——把用户引向完全错误的排查方向。
        assertEquals("请先选择要查询的文字", blankSelectionNotice(""))
        assertEquals("请先选择要查询的文字", blankSelectionNotice("   \n\t "))
        assertNull(blankSelectionNotice("字"))
        assertNull(blankSelectionNotice(" 一期一会 "))
    }

    @Test
    fun `truncation never splits a surrogate pair`() {
        // 第 500 个 UTF-16 码元正好是 emoji 的高代理：截断必须回退一位，
        // 否则 URLEncoder 会把非法序列静默替换成 '?'，查询词与选中文字不一致。
        val splitting = "a".repeat(499) + "\uD83D\uDE00" + "b"

        val bounded = normalizedSelectionQuery(splitting, 500)

        assertEquals(499, bounded.length)
        assertTrue(bounded.all { it == 'a' })
    }

    @Test
    fun `truncation keeps a full surrogate pair when the boundary is clean`() {
        val clean = "a".repeat(500) + "\uD83D\uDE00"

        assertEquals(500, normalizedSelectionQuery(clean, 500).length)
    }

    @Test
    fun `a non positive bound yields an empty query instead of throwing`() {
        assertEquals("", normalizedSelectionQuery("字", 0))
        assertEquals("", normalizedSelectionQuery("字", -3))
    }

    // ===================== 外部动作轻量提示与调度器测试 =====================

    @Test
    fun `executeSelectionBrowser intercepts blank selection without invoking launcher`() {
        val notices = mutableListOf<String>()
        var launcherCalled = false

        executeSelectionBrowser(
            selectedText = "   ",
            template = "https://example.com/search?q={q}",
            showNotice = { notices += it },
            launcher = { _, _ ->
                launcherCalled = true
                SelectionExternalLaunchResult.OPENED
            },
        )

        assertEquals(listOf(NOTICE_BLANK_SELECTION), notices)
        assertEquals(false, launcherCalled)
    }

    @Test
    fun `executeSelectionBrowser shows opening prompt and no error when opened successfully`() {
        val notices = mutableListOf<String>()
        var passedText = ""
        var passedTemplate = ""

        executeSelectionBrowser(
            selectedText = "测试查询",
            template = "https://example.com/search?q={q}",
            showNotice = { notices += it },
            launcher = { text, tmpl ->
                passedText = text
                passedTemplate = tmpl
                SelectionExternalLaunchResult.OPENED
            },
        )

        assertEquals(listOf(NOTICE_OPENING_BROWSER), notices)
        assertEquals("测试查询", passedText)
        assertEquals("https://example.com/search?q={q}", passedTemplate)
    }

    @Test
    fun `executeSelectionBrowser shows opening prompt then error notice when no handler available`() {
        val notices = mutableListOf<String>()

        executeSelectionBrowser(
            selectedText = "测试查询",
            template = "https://example.com/search?q={q}",
            showNotice = { notices += it },
            launcher = { _, _ -> SelectionExternalLaunchResult.NO_HANDLER },
        )

        assertEquals(listOf(NOTICE_OPENING_BROWSER, NOTICE_NO_BROWSER), notices)
    }

    @Test
    fun `executeSelectionDictionary system mode shows opening prompt and fallback notice when web fallback used`() {
        val notices = mutableListOf<String>()

        executeSelectionDictionary(
            selectedText = "测试词语",
            mode = com.creationreadingassistant.data.settings.SelectionActions.MODE_SYSTEM,
            template = "https://example.com/dict?word={q}",
            onOpenOffline = { error("离线分支不应被调用") },
            showNotice = { notices += it },
            systemLauncher = { _, _ -> SelectionExternalLaunchResult.OPENED_WEB_FALLBACK },
            onlineLauncher = { _, _ -> error("在线独立分支不应被调用") },
        )

        assertEquals(listOf(NOTICE_OPENING_SYSTEM_DICT, NOTICE_DICT_FALLBACK), notices)
    }

    @Test
    fun `executeSelectionDictionary system mode shows no handler error when completely unavailable`() {
        val notices = mutableListOf<String>()

        executeSelectionDictionary(
            selectedText = "测试词语",
            mode = com.creationreadingassistant.data.settings.SelectionActions.MODE_SYSTEM,
            template = "https://example.com/dict?word={q}",
            onOpenOffline = { error("离线分支不应被调用") },
            showNotice = { notices += it },
            systemLauncher = { _, _ -> SelectionExternalLaunchResult.NO_HANDLER },
            onlineLauncher = { _, _ -> error("在线独立分支不应被调用") },
        )

        assertEquals(listOf(NOTICE_OPENING_SYSTEM_DICT, NOTICE_NO_DICT_OR_BROWSER), notices)
    }

    @Test
    fun `executeSelectionDictionary online mode shows opening prompt and handles failure`() {
        val successNotices = mutableListOf<String>()
        executeSelectionDictionary(
            selectedText = "测试词语",
            mode = com.creationreadingassistant.data.settings.SelectionActions.MODE_ONLINE,
            template = "https://example.com/dict?word={q}",
            onOpenOffline = { error("离线分支不应被调用") },
            showNotice = { successNotices += it },
            systemLauncher = { _, _ -> error("系统分支不应被调用") },
            onlineLauncher = { _, _ -> SelectionExternalLaunchResult.OPENED_WEB_FALLBACK },
        )
        assertEquals(listOf(NOTICE_OPENING_ONLINE_DICT), successNotices)

        val failNotices = mutableListOf<String>()
        executeSelectionDictionary(
            selectedText = "测试词语",
            mode = com.creationreadingassistant.data.settings.SelectionActions.MODE_ONLINE,
            template = "https://example.com/dict?word={q}",
            onOpenOffline = { error("离线分支不应被调用") },
            showNotice = { failNotices += it },
            systemLauncher = { _, _ -> error("系统分支不应被调用") },
            onlineLauncher = { _, _ -> SelectionExternalLaunchResult.NO_HANDLER },
        )
        assertEquals(listOf(NOTICE_OPENING_ONLINE_DICT, NOTICE_NO_BROWSER), failNotices)
    }

    @Test
    fun `executeSelectionDictionary offline mode delegates to offline callback directly`() {
        val notices = mutableListOf<String>()
        var offlineWord = ""

        executeSelectionDictionary(
            selectedText = "测试词语",
            mode = com.creationreadingassistant.data.settings.SelectionActions.MODE_OFFLINE,
            template = "https://example.com/dict?word={q}",
            onOpenOffline = { offlineWord = it },
            showNotice = { notices += it },
            systemLauncher = { _, _ -> error("不应调用外部系统词典") },
            onlineLauncher = { _, _ -> error("不应调用外部在线词典") },
        )

        assertEquals("测试词语", offlineWord)
        assertTrue(notices.isEmpty())
    }
}
