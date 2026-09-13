package com.creationreadingassistant.ui.screen.shelf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「删除本书」自述文案的措辞边界 —— 长按面板与详情面板共用
 * [DELETE_BOOK_SELF_DESCRIPTION]，这里守住它**不得**怎么描述。
 *
 * 事实依据：`BookRepository.applyDeletionLocked`（DELETE_BOOK 唯一写入路径）做的是
 * Room 事务内的软删除与关联清理，**不调用任何 File.delete / deleteRecursively**；
 * 磁盘上的内部正文副本只由「移除正文」（REMOVE_CONTENT）路径回收。
 * 权威文案 `strings_deletion.xml` 的 `deletion_scope_delete_book_body` 也只列资料与阅读数据。
 *
 * 一旦有人把这里改回「同时移除本机正文」，用户对「删书是否回收磁盘空间」的判断就会出错。
 */
class ShelfDeleteCopyConsistencyTest {

    @Test
    fun `delete copy does not claim it removes local content`() {
        val copy = DELETE_BOOK_SELF_DESCRIPTION
        assertFalse("不得声称移除本机正文", copy.contains("移除本机正文"))
        assertFalse("不得声称移除本地正文", copy.contains("移除本地正文"))
        assertFalse("不得声称删除本机正文", copy.contains("删除本机正文"))
        assertFalse("不得回到『同时移除』的混着说表述", copy.contains("同时移除"))
    }

    @Test
    fun `delete copy explicitly states local content is kept`() {
        assertTrue(
            "必须显式说明不删除本地正文文件",
            DELETE_BOOK_SELF_DESCRIPTION.contains("不删除本地正文文件"),
        )
    }

    @Test
    fun `delete copy names what actually gets removed`() {
        assertTrue("必须说明移除的是书籍资料", DELETE_BOOK_SELF_DESCRIPTION.contains("书籍资料"))
        assertTrue("必须说明阅读数据一并移除", DELETE_BOOK_SELF_DESCRIPTION.contains("进度"))
    }

    @Test
    fun `delete copy is a usable sentence rather than a placeholder`() {
        assertTrue(DELETE_BOOK_SELF_DESCRIPTION.isNotBlank())
        assertTrue("长度应足以承载完整语义", DELETE_BOOK_SELF_DESCRIPTION.length >= 10)
    }
}
