package com.creationreadingassistant.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 来源刷新汇总文案的纯函数测试（S04，依据 2026-09-25 EPUB 残留 UI 流向审查 P2）。
 *
 * 锁定的产品口径：
 * 1. **残留场景语义完整**：用户必须读到「本次替换已停止」「残留备份已保留」
 *    「重复重试不会自动恢复该备份」；文案**不得**引导删书、清空备份或宣称能自动恢复
 *    （与 ShelfImporterTest / EpubReplaceRoomIsolatedTest 对守卫文案的钉法同源）。
 * 2. **多书批量失败控制长度**：书名最多展开 3 个、单名与单条原因各自截断。
 * 3. **不误报成功**：0 本成功也要如实写「已同步 0 本」。
 * 4. **不泄露书籍内容**：提示里只出现书名（书卡既有信息）与失败原因，正文内容永不出现。
 */
class ShelfSourceRefreshFeedbackTest {

    // 守卫原文（含 repairFileTyped catch 加上的「修复失败：」前缀）：
    // EPUB 侧见 EpubRepository 残留守卫，TXT/MD 侧见 ShelfImporter 残留守卫，两句式一致。
    private val epubResidueReason =
        "修复失败：检测到未完成的替换残留（d0f1a2b3-4e5f-6789-abcd-ef0123456789.backup），" +
            "本次替换已停止；旧正文若仍存在，则未被本次操作修改，残留备份已保留。" +
            "重复重试不会自动恢复该备份"

    private val txtResidueReason =
        "修复失败：检测到未完成的替换残留（测试书名.backup），本次替换已停止；" +
            "旧正文若仍存在，则未被本次操作修改，残留备份已保留。" +
            "重复重试不会自动恢复该备份"

    // ── summarizeSourceRefreshFailureReason ────────────────────────────

    @Test
    fun `epub residue reason is compacted with all mandatory semantics`() {
        val compact = summarizeSourceRefreshFailureReason(epubResidueReason)

        assertTrue("必须保留「本次替换已停止」", compact.contains("本次替换已停止"))
        assertTrue("必须保留「残留备份已保留」", compact.contains("残留备份已保留"))
        assertTrue("必须保留「重复重试不会自动恢复该备份」", compact.contains("重复重试不会自动恢复该备份"))
        // 备份文件名是机器细节，压缩后不再出现
        assertFalse("不得携带备份文件名", compact.contains(".backup"))
        // 中间从句被压缩掉
        assertFalse(compact.contains("旧正文若仍存在"))
    }

    @Test
    fun `txt residue reason compacts to the same sentence as epub`() {
        assertEquals(
            summarizeSourceRefreshFailureReason(epubResidueReason),
            summarizeSourceRefreshFailureReason(txtResidueReason),
        )
    }

    @Test
    fun `residue compaction never guides destruction or promises recovery`() {
        val compact = summarizeSourceRefreshFailureReason(epubResidueReason)

        // 审查 §2.6 的口径：不得引导删书 / 清备份 / 重下，也不得暗示有自助恢复入口。
        assertFalse("不得出现「删除」", compact.contains("删除"))
        assertFalse("不得出现「清空」", compact.contains("清空"))
        assertFalse("不得出现「清理」", compact.contains("清理"))
        assertFalse("不得出现「重新下载」", compact.contains("重新下载"))
        assertFalse("不得暗示重试可以生效", compact.contains("重试即可"))
        assertFalse("不得宣称能恢复", compact.contains("可以恢复"))
        // 「不会自动恢复」是唯一合法形态：否定式。
        assertTrue(compact.contains("不会自动恢复"))
    }

    @Test
    fun `short non residue reason passes through unchanged`() {
        val reason = "修复失败：无法打开文件"
        assertEquals(reason, summarizeSourceRefreshFailureReason(reason))
    }

    @Test
    fun `long non residue reason is truncated with ellipsis`() {
        val longReason = "修复失败：" + "很".repeat(80)
        val compact = summarizeSourceRefreshFailureReason(longReason)

        assertEquals(SOURCE_REFRESH_REASON_MAX_CHARS + 1, compact.length)
        assertTrue("截断必须以省略号结尾", compact.endsWith("…"))
        assertFalse("截断不得把长原因误判成残留", compact.contains("替换残留"))
    }

    @Test
    fun `blank or whitespace reason becomes explicit unknown`() {
        assertEquals("原因未知", summarizeSourceRefreshFailureReason(""))
        assertEquals("原因未知", summarizeSourceRefreshFailureReason("   "))
    }

    // ── buildSourceRefreshSummary ──────────────────────────────────────

    @Test
    fun `all success summary has no failure section`() {
        val message = buildSourceRefreshSummary(succeeded = 2, unlocatable = 0, failures = emptyList())
        assertEquals("已同步 2 本书的源文件更新", message)
    }

    @Test
    fun `zero success is reported honestly`() {
        val message = buildSourceRefreshSummary(
            succeeded = 0,
            unlocatable = 0,
            failures = listOf(SourceRefreshFailureUi("测试书", epubResidueReason)),
        )
        assertTrue("0 本成功必须如实显示", message.contains("已同步 0 本书的源文件更新"))
        assertFalse("0 本成功不得写成其他数字", message.contains("已同步 1 本"))
    }

    @Test
    fun `unlocatable section keeps the reselect guidance`() {
        val message = buildSourceRefreshSummary(succeeded = 1, unlocatable = 2, failures = emptyList())
        assertEquals(
            "已同步 1 本书的源文件更新；2 本无法定位来源，请长按选择「重新定位文件」",
            message,
        )
    }

    @Test
    fun `single failure carries title and residue reason`() {
        val message = buildSourceRefreshSummary(
            succeeded = 0,
            unlocatable = 0,
            failures = listOf(SourceRefreshFailureUi("测试书", epubResidueReason)),
        )

        assertTrue(message.contains("1 本刷新失败：测试书"))
        assertTrue("失败必须带原因", message.contains("原因："))
        assertTrue("残留语义必须在汇总里", message.contains("本次替换已停止"))
        assertTrue("重试无效必须在汇总里", message.contains("重复重试不会自动恢复该备份"))
        // 压缩后的原因不携带备份文件名
        assertFalse(message.contains(".backup"))
        assertFalse("不得引导删书", message.contains("删除"))
        assertFalse("不得引导清备份", message.contains("清空"))
    }

    @Test
    fun `many failures fold titles beyond the cap`() {
        val failures = (1..5).map { SourceRefreshFailureUi("书$it", epubResidueReason) }
        val message = buildSourceRefreshSummary(succeeded = 0, unlocatable = 0, failures = failures)

        assertTrue(message.contains("5 本刷新失败："))
        assertTrue("前 3 个书名应展开", message.contains("书1、书2、书3"))
        assertFalse("第 4 个书名不得展开", message.contains("书4、"))
        assertTrue("折叠计数必须出现", message.contains("等 5 本"))
        // 同一原因只展开一次
        assertEquals(1, message.split("本次替换已停止").size - 1)
        assertFalse("原因只有一种时不得提示查看导入记录", message.contains("详见导入记录"))
    }

    @Test
    fun `distinct failure reasons point to the import history instead of listing all`() {
        val failures = listOf(
            SourceRefreshFailureUi("书1", epubResidueReason),
            SourceRefreshFailureUi("书2", "修复失败：无法打开文件"),
        )
        val message = buildSourceRefreshSummary(succeeded = 0, unlocatable = 0, failures = failures)

        assertTrue("只展开首条原因", message.contains("本次替换已停止"))
        assertFalse("第二条原因不得逐条铺开", message.contains("无法打开文件"))
        assertTrue("原因不止一种时指向导入记录", message.contains("其余失败原因详见导入记录"))
    }

    @Test
    fun `overlong titles are individually truncated`() {
        val longTitle = "超".repeat(60)
        val message = buildSourceRefreshSummary(
            succeeded = 0,
            unlocatable = 0,
            failures = listOf(SourceRefreshFailureUi(longTitle, epubResidueReason)),
        )

        assertFalse("书名超限必须截断", message.contains(longTitle))
        assertTrue(message.contains("超".repeat(SOURCE_REFRESH_TITLE_MAX_CHARS) + "…"))
    }

    @Test
    fun `worst case summary stays bounded`() {
        val longTitle = "长".repeat(80)
        val failures = (1..10).map { SourceRefreshFailureUi("$longTitle$it", epubResidueReason) }
        val message = buildSourceRefreshSummary(succeeded = 0, unlocatable = 3, failures = failures)

        // 各段都有独立上限：3×31 书名 + 61 原因 + 计数与固定句式，总量必须有界。
        assertTrue("最坏情况总长必须受控（实际 ${message.length}）", message.length <= 260)
    }
}
