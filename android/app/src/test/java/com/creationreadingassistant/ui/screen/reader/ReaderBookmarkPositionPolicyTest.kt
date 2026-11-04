package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.locator.LocatorCodec
import com.creationreadingassistant.feature.reader.pager.ReaderPagePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S09-A-F1：创建书签时，只有**当前这本书**的分页位置才能作为精确坐标与 locator 的来源。
 *
 * 背景（P1）：`ReaderPageIndexManager.position` 是进程内单例状态，只在分页阅读分支被写入，
 * 且 `clear()` 全仓没有调用点。于是「先读 A 书（分页）→ 再打开 B 书（滚动 / 新书首帧未上报）」
 * 时，`ReaderSheetHost` 的 `onAddBookmark` 会把 **A 书的 absStart / chapterIndex / charOffset /
 * locator** 写进 B 书的书签 —— 跨书残留坐标被持久化，回跳时落到错误位置。
 *
 * 本片只做止血：坐标与 locator 一律经 `bookmarkPositionFields` 的当前书校验。
 * **不**接入 clear()、**不**给滚动模式新增精确 locator、**不**改百分比回退语义。
 *
 * 这些用例调用的是**生产纯函数本身**（`bookmarkPositionFields`），
 * 并用真实的 `LocatorCodec.decode` 反解 locator，断言的是可观察结果，
 * 不是源码字符串，也不在测试里复制一份判断逻辑。
 */
class ReaderBookmarkPositionPolicyTest {

    private fun positionOf(bookId: String, absStart: Int) = ReaderPagePosition(
        bookId = bookId,
        chapterIndex = 3,
        pageIndex = 7,
        pageCount = 12,
        absStart = absStart,
        absEnd = absStart + 200,
        percent = 42f,
    )

    // ── 1. 当前书的 position：精确坐标与 locator 必须照常可用 ──────────────

    @Test
    fun `a position belonging to the current book keeps its exact coordinates`() {
        val fields = bookmarkPositionFields(
            position = positionOf("bk-a", absStart = 1234),
            bookId = "bk-a",
            isEpub = false,
            chapterStartOffsets = listOf(0, 1000, 2000),
        )

        assertEquals(1234, fields.absOffset)
        assertEquals(0, fields.chapterIndex)
        assertEquals(1234, fields.charOffset)
        assertNotNull("当前书必须仍然产出 locator", fields.locatorJson)

        val decoded = LocatorCodec.decode(fields.locatorJson)
        assertEquals(1234, decoded?.legacyOffset)
    }

    @Test
    fun `an epub position of the current book is still mapped through chapter starts`() {
        val fields = bookmarkPositionFields(
            position = positionOf("bk-a", absStart = 1050),
            bookId = "bk-a",
            isEpub = true,
            chapterStartOffsets = listOf(0, 1000, 2000),
        )

        assertEquals(1050, fields.absOffset)
        assertEquals("EPUB 精确位置仍要解析出所属章", 1, fields.chapterIndex)
        assertEquals("章内偏移 = 绝对偏移 - 章起点", 50, fields.charOffset)

        val decoded = LocatorCodec.decode(fields.locatorJson)
        assertEquals(1050, decoded?.legacyOffset)
        assertEquals(1, decoded?.chapterIndex)
        assertEquals(50, decoded?.charOffset)
    }

    // ── 2. 其他书的 position：一个字段都不许泄漏 ─────────────────────────

    @Test
    fun `a position from another book never contributes coordinates`() {
        val fields = bookmarkPositionFields(
            position = positionOf("bk-a", absStart = 1234),
            bookId = "bk-b",
            isEpub = false,
            chapterStartOffsets = listOf(0, 1000, 2000),
        )

        assertEquals("别书坐标不得写入本书", -1, fields.absOffset)
        assertEquals(0, fields.chapterIndex)
        assertEquals(-1, fields.charOffset)
        assertNull("别书 position 不得产出 locator", fields.locatorJson)
    }

    @Test
    fun `an epub bookmark of another book does not even run the chapter mapping`() {
        // 若仍走 EpubLocatorMapping，absStart=1234 会被解析成 chapterIndex=1 / charOffset=234。
        // 期望：整条精确链被当前书校验挡在门外，落到与 null position 相同的回退形态。
        val fields = bookmarkPositionFields(
            position = positionOf("bk-a", absStart = 1234),
            bookId = "bk-b",
            isEpub = true,
            chapterStartOffsets = listOf(0, 1000, 2000),
        )

        assertEquals(-1, fields.absOffset)
        assertEquals(0, fields.chapterIndex)
        assertEquals(-1, fields.charOffset)
        assertNull(fields.locatorJson)
    }

    // ── 3. null position：保持既有百分比回退，不伪造精确位置 ───────────────

    @Test
    fun `a null position falls back without faking exact fields`() {
        val fields = bookmarkPositionFields(
            position = null,
            bookId = "bk-a",
            isEpub = false,
            chapterStartOffsets = listOf(0, 1000, 2000),
        )

        assertEquals(-1, fields.absOffset)
        assertEquals(0, fields.chapterIndex)
        assertEquals(-1, fields.charOffset)
        assertNull("无精确位置时不得编造 locator", fields.locatorJson)
    }

    @Test
    fun `a null position in an epub does not fabricate a chapter anchor`() {
        val fields = bookmarkPositionFields(
            position = null,
            bookId = "bk-a",
            isEpub = true,
            chapterStartOffsets = listOf(0, 1000, 2000),
        )

        assertEquals(-1, fields.absOffset)
        assertEquals(0, fields.chapterIndex)
        assertEquals(-1, fields.charOffset)
        assertNull(fields.locatorJson)
    }

    @Test
    fun `the mismatched and null shapes are identical so the fallback cannot branch`() {
        // 书签创建链对「无可用精确位置」只应有一种形态；
        // 若两者分叉，说明某条路径仍在偷偷使用残留 position。
        val fromNull = bookmarkPositionFields(null, "bk-b", isEpub = true, listOf(0, 1000))
        val fromOtherBook = bookmarkPositionFields(positionOf("bk-a", 1234), "bk-b", isEpub = true, listOf(0, 1000))

        assertEquals(fromNull, fromOtherBook)
    }

    // ── 4. 校验口径本身 ────────────────────────────────────────────────

    @Test
    fun `book id comparison is exact so a prefix does not pass as the same book`() {
        val fields = bookmarkPositionFields(
            position = positionOf("bk-a", absStart = 1234),
            bookId = "bk-a-extra",
            isEpub = false,
            chapterStartOffsets = listOf(0),
        )

        assertEquals(-1, fields.absOffset)
        assertNull(fields.locatorJson)
    }
}
