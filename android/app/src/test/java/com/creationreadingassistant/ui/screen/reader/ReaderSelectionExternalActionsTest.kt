package com.creationreadingassistant.ui.screen.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ===================== 外部动作提示：结果确定后只发一条（S07-F4.1） =====================
    //
    // 旧形态（F1 报告 Q-3 提出、F4 误记为「已固化」）：先无条件提示「正在打开浏览器…」，
    // 拿到 NO_HANDLER 后再补一条失败 —— 一次什么都没发生的操作给出两条互相矛盾的提示。
    // 在线词典失败还复用「未找到可用浏览器」，把用户引向错误的排查方向。

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
    fun `executeSelectionBrowser reports one success notice and passes the query through`() {
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

        assertEquals(listOf(NOTICE_BROWSER_OPENED), notices)
        assertEquals("测试查询", passedText)
        assertEquals("https://example.com/search?q={q}", passedTemplate)
    }

    @Test
    fun `executeSelectionBrowser never announces success before the result is known`() {
        // 失败路径只允许出现**一条**提示，且不得包含任何成功/进行中文案。
        val notices = mutableListOf<String>()

        executeSelectionBrowser(
            selectedText = "测试查询",
            template = "https://example.com/search?q={q}",
            showNotice = { notices += it },
            launcher = { _, _ -> SelectionExternalLaunchResult.NO_HANDLER },
        )

        assertEquals(listOf(NOTICE_BROWSER_UNAVAILABLE), notices)
        assertFalse(
            "失败时不得预报成功或「正在打开」",
            notices.any { it.contains("正在打开") || it == NOTICE_BROWSER_OPENED },
        )
    }

    @Test
    fun `the browser result notice is a total function of the launch result`() {
        // 三种返回各自对应一条确定文案；成功与失败必须可区分。
        assertEquals(NOTICE_BROWSER_OPENED, selectionBrowserResultNotice(SelectionExternalLaunchResult.OPENED))
        assertEquals(
            NOTICE_BROWSER_OPENED,
            selectionBrowserResultNotice(SelectionExternalLaunchResult.OPENED_WEB_FALLBACK),
        )
        assertEquals(
            NOTICE_BROWSER_UNAVAILABLE,
            selectionBrowserResultNotice(SelectionExternalLaunchResult.NO_HANDLER),
        )
        assertFalse(
            selectionBrowserResultNotice(SelectionExternalLaunchResult.OPENED) ==
                selectionBrowserResultNotice(SelectionExternalLaunchResult.NO_HANDLER),
        )
    }

    @Test
    fun `system dictionary mode reports the web fallback as a fallback`() {
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

        assertEquals(listOf(NOTICE_DICT_FALLBACK), notices)
    }

    @Test
    fun `system dictionary mode reports a plain success without a fallback claim`() {
        val notices = mutableListOf<String>()

        executeSelectionDictionary(
            selectedText = "测试词语",
            mode = com.creationreadingassistant.data.settings.SelectionActions.MODE_SYSTEM,
            template = "https://example.com/dict?word={q}",
            onOpenOffline = { error("离线分支不应被调用") },
            showNotice = { notices += it },
            systemLauncher = { _, _ -> SelectionExternalLaunchResult.OPENED },
            onlineLauncher = { _, _ -> error("在线独立分支不应被调用") },
        )

        assertEquals(listOf(NOTICE_SYSTEM_DICT_OPENED), notices)
    }

    @Test
    fun `system dictionary mode says both are unavailable only when nothing worked`() {
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

        assertEquals(listOf(NOTICE_DICT_OR_BROWSER_UNAVAILABLE), notices)
    }

    @Test
    fun `online dictionary mode reports one success notice`() {
        val notices = mutableListOf<String>()
        executeSelectionDictionary(
            selectedText = "测试词语",
            mode = com.creationreadingassistant.data.settings.SelectionActions.MODE_ONLINE,
            template = "https://example.com/dict?word={q}",
            onOpenOffline = { error("离线分支不应被调用") },
            showNotice = { notices += it },
            systemLauncher = { _, _ -> error("系统分支不应被调用") },
            onlineLauncher = { _, _ -> SelectionExternalLaunchResult.OPENED_WEB_FALLBACK },
        )
        assertEquals(listOf(NOTICE_ONLINE_DICT_OPENED), notices)
    }

    @Test
    fun `online dictionary failure never blames the browser`() {
        // 这是 F4 账本 L7 的原始缺陷：在线词典失败复用「未找到可用浏览器」。
        val notices = mutableListOf<String>()
        executeSelectionDictionary(
            selectedText = "测试词语",
            mode = com.creationreadingassistant.data.settings.SelectionActions.MODE_ONLINE,
            template = "https://example.com/dict?word={q}",
            onOpenOffline = { error("离线分支不应被调用") },
            showNotice = { notices += it },
            systemLauncher = { _, _ -> error("系统分支不应被调用") },
            onlineLauncher = { _, _ -> SelectionExternalLaunchResult.NO_HANDLER },
        )

        assertEquals(listOf(NOTICE_ONLINE_DICT_UNAVAILABLE), notices)
        assertFalse(
            "在线词典失败不得只说「未找到可用浏览器」",
            notices.any { it == NOTICE_BROWSER_UNAVAILABLE },
        )
        assertFalse("失败时不得出现「正在打开」", notices.any { it.contains("正在打开") })
    }

    @Test
    fun `both dictionary modes intercept a blank selection before any launcher runs`() {
        listOf(
            com.creationreadingassistant.data.settings.SelectionActions.MODE_SYSTEM,
            com.creationreadingassistant.data.settings.SelectionActions.MODE_ONLINE,
        ).forEach { mode ->
            val notices = mutableListOf<String>()
            executeSelectionDictionary(
                selectedText = " \n ",
                mode = mode,
                template = "https://example.com/dict?word={q}",
                onOpenOffline = { error("离线分支不应被调用") },
                showNotice = { notices += it },
                systemLauncher = { _, _ -> error("空选区不得调用系统词典") },
                onlineLauncher = { _, _ -> error("空选区不得调用在线词典") },
            )
            assertEquals("$mode 的空选区提示", listOf(NOTICE_BLANK_SELECTION), notices)
        }
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
