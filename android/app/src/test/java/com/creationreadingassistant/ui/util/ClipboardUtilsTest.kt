package com.creationreadingassistant.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 复制选区的入口判定（R3-X1 缺陷收口）。
 *
 * 空选区必须被**拒绝**而不是照抄：写空串会清掉用户原有剪贴板内容，
 * 而调用方仍会弹「已复制」——一次静默的数据破坏叠加一次虚假成功。
 */
class ClipboardUtilsTest {

    @Test
    fun `a blank selection is never handed to the clipboard`() {
        assertNull(copyableSelection(""))
        assertNull(copyableSelection("   "))
        assertNull(copyableSelection("\n\t "))
    }

    @Test
    fun `a real selection is passed through untouched`() {
        assertEquals("一期一会", copyableSelection("一期一会"))
        // 首尾空白属于用户选中的内容，这里不做 trim —— 裁剪是查询串的职责，不是复制的
        assertEquals("  一期一会  ", copyableSelection("  一期一会  "))
    }
}
