package com.creationreadingassistant.ui.screen.reader

import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * readingUnits 身份绑定裁决 seam 的行为测试。
 *
 * 输入：document identity + document 自有 units + 外部派生 units。
 * 输出：当前应使用的 readingUnits。
 *
 * 关键行为：
 * - 首帧可用：文档在场且构造期已携带 units → 直接返回文档自有 units，不依赖
 *   LaunchedEffect 写回，也不许回退到派生的旧值；
 * - 同一文档更新：文档自有 units 被替换 → 跟随新值，不读旧派生快照；
 * - 切换文档：以每次调用传入的身份为准，绝不串文档；
 * - 小文件路径（document == null）→ 使用外部派生 units；
 * - 防御不变式：文档在场但自有 units 为空时如实返回空（分页/搜索/滚动读同一份
 *   document.readingUnits，回退派生会造成分页与滚动/搜索两套列表分叉）。
 */
class ReadingUnitsResolverTest {

    private val docA = Any()
    private val docB = Any()

    private fun unitsOf(prefix: String, count: Int = 2): List<ReadingUnit> =
        (0 until count).map { i ->
            ReadingUnit(
                unitIndex = i,
                chapterIndex = 0,
                title = "$prefix-$i",
                charStart = i * 10,
                charCount = 10,
            )
        }

    @Test
    fun `first frame uses document owned units when document is present`() {
        val documentUnits = unitsOf("doc")
        val derived = unitsOf("derived", count = 3)

        val current = ReadingUnitsResolver.resolve(docA, documentUnits, derived)

        // 首帧可用：文档构造期已携带 units，必须立即使用，而不是等待异步写回或派生值
        assertEquals(documentUnits, current)
    }

    @Test
    fun `same document update follows latest document units not stale derived`() {
        val firstUnits = unitsOf("first")
        val replacedUnits = unitsOf("replaced", count = 3)
        val staleDerived = unitsOf("stale")

        assertEquals(firstUnits, ReadingUnitsResolver.resolve(docA, firstUnits, staleDerived))
        // 同文档 units 被整体替换后（例如重新扫描发布），必须跟随新值
        assertEquals(replacedUnits, ReadingUnitsResolver.resolve(docA, replacedUnits, staleDerived))
    }

    @Test
    fun `switching documents never serves the previous document units`() {
        val unitsA = unitsOf("A")
        val unitsB = unitsOf("B")
        val derivedA = unitsOf("derivedA")

        assertEquals(unitsA, ReadingUnitsResolver.resolve(docA, unitsA, derivedA))
        // 切到 B：不得继续读 A 的 units，也不能被 A 的派生值污染
        assertEquals(unitsB, ReadingUnitsResolver.resolve(docB, unitsB, derivedA))
        // 切回 A：仍以 A 当前自有 units 为准
        assertEquals(unitsA, ReadingUnitsResolver.resolve(docA, unitsA, unitsB))
    }

    @Test
    fun `small file path with no document uses externally derived units`() {
        val derived = unitsOf("chunk")

        assertEquals(derived, ReadingUnitsResolver.resolve(null, emptyList(), derived))
    }

    @Test
    fun `present document with empty owned units stays empty instead of falling back`() {
        // 分页（TxtChapterSource）、搜索、滚动全部读 document.readingUnits；
        // 若此处回退到派生 units，分页与滚动/搜索会读到两套不一致的列表
        assertEquals(emptyList<ReadingUnit>(), ReadingUnitsResolver.resolve(docA, emptyList(), unitsOf("derived")))
    }
}
