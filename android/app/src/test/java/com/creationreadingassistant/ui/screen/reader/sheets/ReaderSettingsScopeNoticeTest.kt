package com.creationreadingassistant.ui.screen.reader.sheets

import com.creationreadingassistant.data.settings.ReaderSettingsScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-2-B：阅读设置**页级作用域说明**的纯映射测试。
 *
 * 背景：`pagerEngineMode` / `epubPagerEngineMode` 在 D-2-A 已成为真正的本书覆盖，
 * 但设置页里另有 26 个**设备 / 交互**字段（翻页、显示、护眼）本来就应该保持全局语义。
 * 用户在「只改本书」作用域下改它们时，界面此前没有任何说明 —— 真机验收记为 P3-1。
 *
 * 本轮（产品裁决：页级规则句）用**一条页级说明**告知影响范围：
 * - BOOK：按「例外清单」表述（除 X、Y、Z 外其余都是全局）→ 新增**全局**字段时句子仍正确；
 * - GLOBAL：统一说明「保存为全局默认」，并**明确不会清除其他书已有的单书设置**
 *   （修改全局默认不会抹掉别人的 per-book 覆盖，旧文案「影响所有书」是不准确的承诺）。
 *
 * 本文件只测纯函数，不依赖 Compose、不读 DataStore、不修改任何设置。
 */
class ReaderSettingsScopeNoticeTest {

    private fun notice(page: ReaderSettingsPage, scope: ReaderSettingsScope): String? =
        readerSettingsScopeNotice(page, scope)

    private fun bookNotice(page: ReaderSettingsPage): String =
        requireNotNull(notice(page, ReaderSettingsScope.BOOK)) {
            "$page 在 BOOK 作用域下必须有页级说明"
        }

    private fun globalNotice(page: ReaderSettingsPage): String =
        requireNotNull(notice(page, ReaderSettingsScope.GLOBAL)) {
            "$page 在 GLOBAL 作用域下必须有页级说明"
        }

    // ── 1. ROOT 不显示页级说明（根页已有作用域胶囊） ────────────────────

    @Test
    fun `the root page has no notice under the book scope`() {
        // 根页顶部已有「只改本书 / 改全局默认」胶囊，再叠一条说明是重复噪音。
        assertNull(notice(ReaderSettingsPage.ROOT, ReaderSettingsScope.BOOK))
    }

    @Test
    fun `the root page has no notice under the global scope`() {
        assertNull(notice(ReaderSettingsPage.ROOT, ReaderSettingsScope.GLOBAL))
    }

    // ── 2. BOOK：整页本书可覆盖的两页 ─────────────────────────────────

    @Test
    fun `typography under the book scope says only this book`() {
        assertEquals("本页设置只对当前书籍生效", bookNotice(ReaderSettingsPage.TYPOGRAPHY))
    }

    @Test
    fun `advanced compatibility under the book scope says only this book`() {
        // 高级兼容页是 D-2-A 的两个引擎键（真 per-book 覆盖）→ 必须说「只对当前书籍」。
        assertEquals("本页设置只对当前书籍生效", bookNotice(ReaderSettingsPage.ADVANCED))
    }

    @Test
    fun `the advanced compatibility copy must not claim global scope`() {
        // 反向钉子：把 D-2-A 的真·本书设置标成「全局」是方向相反的错误（真机 P3-1 的另一面）。
        val copy = bookNotice(ReaderSettingsPage.ADVANCED)
        assertFalse("高级兼容页文案不得出现「全局」：$copy", copy.contains("全局"))
        assertFalse("高级兼容页文案不得出现「所有书籍」：$copy", copy.contains("所有书籍"))
    }

    // ── 3. BOOK：例外式清单（混排页） ─────────────────────────────────

    @Test
    fun `paging under the book scope names exactly its three book level exceptions`() {
        val copy = bookNotice(ReaderSettingsPage.PAGING)

        // 该页的本书可覆盖项恰好是这三项（readerMode / pageTurnEffect / screenOrientation）。
        listOf("阅读模式", "翻页效果", "屏幕方向").forEach { exception ->
            assertTrue("翻页页文案必须点名例外项「$exception」：$copy", copy.contains(exception))
        }
        assertTrue("翻页页文案必须说明其余项对所有书籍生效：$copy", copy.contains("所有书籍生效"))
    }

    @Test
    fun `paging copy does not name exceptions that belong to another page`() {
        // 防止把显示页/护眼页的例外串到翻页页。
        val copy = bookNotice(ReaderSettingsPage.PAGING)
        listOf("沉浸模式", "进度条", "护眼", "色温").forEach { foreign ->
            assertFalse("翻页页文案不得出现其他页的例外项「$foreign」：$copy", copy.contains(foreign))
        }
    }

    @Test
    fun `display under the book scope names exactly its four book level exceptions`() {
        val copy = bookNotice(ReaderSettingsPage.DISPLAY)

        // 该页的本书可覆盖项：immersiveMode / showProgressBar / autoHideSeconds / showReaderInfo。
        // 例外名必须逐字等于该页**行标题原文**（ReaderDisplaySettings.kt:38 / :47 / :68 / :87）。
        // 反例：旧写法借用的是**分组名**而非控件名，会被读成「整个分组都是本书设置」，
        // 而该分组下六个模板选择器实际写全局 —— 方向相反的误解。
        listOf("沉浸模式", "显示进度条", "自动隐藏", "显示安静阅读信息").forEach { exception ->
            assertTrue("显示页文案必须点名例外项「$exception」：$copy", copy.contains(exception))
        }
        assertTrue("显示页文案必须说明其余项对所有书籍生效：$copy", copy.contains("所有书籍生效"))
    }

    @Test
    fun `display copy names no group title instead of a control`() {
        val copy = bookNotice(ReaderSettingsPage.DISPLAY)

        // 「页眉页脚」是分组标题（ReaderDisplaySettings.kt:81），其下六个槽位写全局：
        // 一旦它出现在例外清单里，用户会把整组读成本书设置。
        assertFalse("显示页文案不得把分组名当作例外项：$copy", copy.contains("页眉页脚"))
        // 「菜单隐藏」也是分组名（:64），其下真正的控件叫「自动隐藏」（:68）。
        assertFalse("显示页文案不得用分组名「菜单隐藏」代替控件名：$copy", copy.contains("菜单隐藏"))
    }

    @Test
    fun `display copy does not name exceptions that belong to another page`() {
        val copy = bookNotice(ReaderSettingsPage.DISPLAY)
        listOf("阅读模式", "屏幕方向", "护眼", "色温").forEach { foreign ->
            assertFalse("显示页文案不得出现其他页的例外项「$foreign」：$copy", copy.contains(foreign))
        }
    }

    // ── 4. BOOK：整页全局 ─────────────────────────────────────────────

    @Test
    fun `eye care under the book scope states the whole page is global`() {
        val copy = bookNotice(ReaderSettingsPage.EYE_CARE)

        // 护眼页 10 项全部是全局字段，没有例外清单。
        assertEquals("本页均为全局设置，对所有书籍生效", copy)
    }

    @Test
    fun `eye care copy carries no exception list`() {
        // 反向钉子：护眼页若出现「除…外」就说明有人误以为它混排。
        val copy = bookNotice(ReaderSettingsPage.EYE_CARE)
        assertFalse("护眼页文案不得出现例外清单：$copy", copy.contains("除"))
        assertFalse("护眼页文案不得出现例外清单：$copy", copy.contains("外"))
    }

    // ── 5. GLOBAL：统一修正文案 ───────────────────────────────────────

    @Test
    fun `every sub page under the global scope returns the same corrected copy`() {
        val subPages = listOf(
            ReaderSettingsPage.TYPOGRAPHY,
            ReaderSettingsPage.PAGING,
            ReaderSettingsPage.DISPLAY,
            ReaderSettingsPage.EYE_CARE,
            ReaderSettingsPage.ADVANCED,
        )

        val copies = subPages.map { globalNotice(it) }

        assertEquals("GLOBAL 下五个子页必须使用同一句文案", 1, copies.toSet().size)
        assertEquals(
            "本页修改将保存为全局默认；其他书已有的单书设置不会被清除",
            copies.distinct().single(),
        )
    }

    @Test
    fun `the global copy states both the write target and the non destructive guarantee`() {
        val copy = globalNotice(ReaderSettingsPage.PAGING)

        assertTrue("必须说明保存到全局默认：$copy", copy.contains("保存为全局默认"))
        assertTrue(
            "必须说明不会清除其他书的单书设置：$copy",
            copy.contains("其他书已有的单书设置不会被清除"),
        )
    }

    @Test
    fun `the global copy makes no promise it cannot keep`() {
        // 修改全局默认**不会**清除其他书已有的 per-book 覆盖 —— 旧文案是不准确的承诺。
        val copy = globalNotice(ReaderSettingsPage.DISPLAY)

        assertFalse("不得声称影响所有书：$copy", copy.contains("影响所有书"))
        assertFalse("不得声称清除所有书：$copy", copy.contains("清除所有书"))
        assertFalse("不得声称会清除其他书设置：$copy", copy.contains("清除其他书"))
    }

    // ── 6. 通用文案约束 ───────────────────────────────────────────────

    @Test
    fun `no notice uses markdown emphasis`() {
        // 真机验收 P3-2 已证实：Markdown 星号会被原样渲染给用户。
        ReaderSettingsPage.entries.forEach { page ->
            ReaderSettingsScope.entries.forEach { scope ->
                notice(page, scope)?.let { copy ->
                    assertFalse("$page/$scope 文案不得含 Markdown 星号：$copy", copy.contains("**"))
                }
            }
        }
    }

    @Test
    fun `no notice exposes implementation terms`() {
        // 面向普通用户，不得出现技术词。
        val forbidden = listOf("override", "DataStore", "scope", "router", "覆盖项", "默认值来源")
        ReaderSettingsPage.entries.forEach { page ->
            ReaderSettingsScope.entries.forEach { scope ->
                notice(page, scope)?.let { copy ->
                    forbidden.forEach { term ->
                        assertFalse("$page/$scope 文案不得含技术词「$term」：$copy", copy.contains(term))
                    }
                }
            }
        }
    }

    @Test
    fun `no notice describes the app brightness setting as a system setting`() {
        // `brightness` 是阅读窗口亮度，不是系统设置；文案不得暗示会改手机系统。
        ReaderSettingsPage.entries.forEach { page ->
            ReaderSettingsScope.entries.forEach { scope ->
                notice(page, scope)?.let { copy ->
                    assertFalse("文案不得含「系统设置」：$copy", copy.contains("系统设置"))
                }
            }
        }
    }

    @Test
    fun `no notice ends with a full stop`() {
        // 页级说明是短语式提示，统一不带末尾句号（避免测试只靠标点区分）。
        ReaderSettingsPage.entries.forEach { page ->
            ReaderSettingsScope.entries.forEach { scope ->
                notice(page, scope)?.let { copy ->
                    assertFalse("$page/$scope 文案不得以句号结尾：$copy", copy.endsWith("。"))
                }
            }
        }
    }

    // ── 7. 覆盖完整性：枚举新增成员必须被迫处理 ────────────────────────

    @Test
    fun `every page is explicitly answered for both scopes`() {
        // 与「when 无 else」配合：新增 ReaderSettingsPage 成员时 when 会编译失败；
        // 本用例再从行为面确认每个成员都有明确答案（非 ROOT 必须有文案，ROOT 必须为 null）。
        ReaderSettingsPage.entries.forEach { page ->
            ReaderSettingsScope.entries.forEach { scope ->
                val copy = notice(page, scope)
                if (page == ReaderSettingsPage.ROOT) {
                    assertNull("ROOT/$scope 必须是 null", copy)
                } else {
                    assertNotNull("$page/$scope 必须有页级说明", copy)
                    assertTrue("$page/$scope 文案不得为空", copy!!.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `the notice function has no catch all else branch`() {
        // 结构守卫：`when` 必须穷尽 ReaderSettingsPage，不得用 `else ->` 吞掉未来新增页面。
        // 只在本函数体内检查，不做全文件扫描。
        val source = java.io.File(
            "src/main/java/com/creationreadingassistant/ui/screen/reader/sheets/ReaderSettingsSheet.kt",
        )
        assertTrue("源码路径必须存在（工作目录应为 app 模块根）：${source.absolutePath}", source.exists())
        val text = source.readText()

        val headerIndex = text.indexOf("internal fun readerSettingsScopeNotice(")
        assertTrue("必须存在 readerSettingsScopeNotice 定义", headerIndex >= 0)

        // 取函数体：从定义处向后配平花括号。
        val openIndex = text.indexOf('{', text.indexOf(')', headerIndex))
        var depth = 0
        var endIndex = -1
        for (i in openIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) { endIndex = i; break }
                }
            }
        }
        assertTrue("readerSettingsScopeNotice 函数体必须闭合", endIndex > openIndex)
        val body = text.substring(openIndex, endIndex + 1)

        assertFalse("不得使用 `else ->` 吞掉未来新增页面：$body", body.contains("else ->"))
        assertTrue("必须显式列出 ROOT 分支", body.contains("ReaderSettingsPage.ROOT"))
    }
}
