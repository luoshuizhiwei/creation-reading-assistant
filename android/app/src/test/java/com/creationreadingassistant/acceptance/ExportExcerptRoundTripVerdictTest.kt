package com.creationreadingassistant.acceptance

import com.creationreadingassistant.data.local.entity.InspirationEntity
import com.creationreadingassistant.ui.screen.reader.sheets.buildNotesExportMarkdown
import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import com.creationreadingassistant.ui.viewmodel.InspirationSourceInfo
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * CRA-EXPORT-CONTENT-80-v2-A —— [ExportExcerptRoundTripVerdict] 的离线定向回归。
 *
 * 这些用例直接调用仪器**实际使用**的判定对象（仪器只做采集：保存前的工具条选区语义、
 * Room 里的 payload、导出的文档字节），因此不存在「测试另写一套规则、仪器仍用旧逻辑」的分叉；
 * 最后一个用例用**字节一致性守卫**证明 test 源集的规则文件与 androidTest 源集仪器所用的
 * 那份逐字节相同（与既有 ReaderClampRemeasureVerdict 同一做法）。
 *
 * 真实设备值全部内嵌并按 SHA / 长度 / 行数锁定：
 *  - 555 字节设备导出原文（SHA256 见 [DEVICE_EXPORT_SHA256]）；
 *  - 58 字真实摘录（第3段）；
 *  - 360 字/18 行**错误页面探针**（80-v1 的旧 oracle，取自同一 run 的 mat_30_save.json）；
 *  - 1822 字本轮导入正文（用于「书内另一段」与辅助子串检查）。
 */
class ExportExcerptRoundTripVerdictTest {

    private val exportedAt = LocalDateTime.of(2026, 10, 20, 23, 15)

    // ── 真实设备值（不做任何手工改写）───────────────────────────────────────

    /** 设备 555 字节导出原文（exec-out 取回，非 CRLF 传输副本）。 */
    private val deviceExport: String = """# 《取材测试-MAT65V3-9f76e6》阅读笔记

> 导出于 2026-10-20 23:15
> 汇总统计：1 条灵感

---

## 灵感（共 1 条）

### MAT65V3-9f76e6

个人想法：这条摘录来自本轮真实阅读，标记 MAT65V3-9f76e6。

来源：《取材测试-MAT65V3-9f76e6》 · 第1章 取材真实接缝
> 摘录：第3段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。
位置：{"v":2,"offset":132,"ci":0,"co":132,"fp":"582e00512a224aa9"}

"""

    /** 本轮真实持久化并导出的摘录（58 字，第3段）。 */
    private val realExcerpt: String = "第3段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。"

    /** 80-v1 用的「整页正文探针」：360 字 / 18 行，带排版硬换行、按视口截断。 */
    private val renderedPageProbe: String = """第1章 取材真实接缝
第1段：这是任务65-v3 使用的中性测试正
文，用于真实长按摘录、个人想法、真实检索与
回源，不包含任何正式书库内容。
第2段：这是任务65-v3 使用的中性测试正
文，用于真实长按摘录、个人想法、真实检索与
回源，不包含任何正式书库内容。
第3段：这是任务65-v3 使用的中性测试正
文，用于真实长按摘录、个人想法、真实检索与
回源，不包含任何正式书库内容。
第4段：这是任务65-v3 使用的中性测试正
文，用于真实长按摘录、个人想法、真实检索与
回源，不包含任何正式书库内容。
第5段：这是任务65-v3 使用的中性测试正
文，用于真实长按摘录、个人想法、真实检索与
回源，不包含任何正式书库内容。
第6段：这是任务65-v3 使用的中性测试正
文，用于真实长按摘录、个人想法、真实检索与"""

    /** 本轮导入正文（1822 字），用于「书内另一段」与辅助子串检查。 */
    private val bookText: String = """第1章 取材真实接缝

第1段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第2段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第3段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第4段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第5段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第6段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第7段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第8段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第9段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第10段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第11段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第12段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第13段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第14段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第15段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第16段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第17段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第18段：这是任务65-v3 使用的中性测试正文，用于真实长按摘录、个人想法、真实检索与回源，不包含任何正式书库内容。

第2章 后续正文

第2章第1段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第2段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第3段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第4段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第5段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第6段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第7段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第8段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第9段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第10段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第11段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第12段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第13段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第14段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第15段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第16段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第17段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

第2章第18段：继续提供可滚动、可定位的中性文本，供真实阅读与回源验收使用。

"""

    // ── 1. 真实值锁定 ────────────────────────────────────────────────────────

    @Test
    fun `embedded real values are pinned to the device originals`() {
        assertEquals(555, deviceExport.toByteArray(Charsets.UTF_8).size)
        assertEquals(DEVICE_EXPORT_SHA256, sha256(deviceExport.toByteArray(Charsets.UTF_8)))
        assertEquals(58, realExcerpt.length)
        assertEquals(360, renderedPageProbe.length)
        assertEquals(18, renderedPageProbe.count { it == '\n' } + 1)
        assertEquals(1822, bookText.length)
        assertEquals(BOOK_TEXT_SHA256, sha256(bookText.toByteArray(Charsets.UTF_8)))
        assertTrue("真实摘录必须存在于设备导出原文中", deviceExport.contains(realExcerpt))
        assertTrue("真实摘录必须是本轮导入正文的逐字子串", bookText.contains(realExcerpt))
    }

    // ── 2. 红：旧 oracle 必然失败 ────────────────────────────────────────────

    @Test
    fun `the old rendered page probe was never the excerpt and never in the export`() {
        // 80-v1 的旧断言形态就是 text.contains(capturedExcerpt)，此处离线复现它必然 FAIL。
        assertFalse(
            "整页正文探针不可能逐字出现在导出文档里（它带排版硬换行且按视口截断）",
            deviceExport.contains(renderedPageProbe),
        )
        assertFalse(
            "整页正文探针也不是书中正文（含「正\n文」这类排版硬换行伪影）",
            bookText.contains(renderedPageProbe),
        )
        assertFalse(
            "把整页探针当摘录时，导出完整性判定必须为 false",
            ExportExcerptRoundTripVerdict.exportContainsExcerpt(deviceExport, renderedPageProbe),
        )
    }

    // ── 3. 绿：正确选区通过 ──────────────────────────────────────────────────

    @Test
    fun `real device export preserves the real persisted excerpt`() {
        assertTrue(
            "真实 555 字节导出必须完整保留 58 字真实摘录",
            ExportExcerptRoundTripVerdict.exportContainsExcerpt(deviceExport, realExcerpt),
        )
    }

    // ── 4. 书内另一段必须失败 ────────────────────────────────────────────────

    @Test
    fun `another paragraph from the same book is rejected`() {
        val otherParagraph = realExcerpt.replaceFirst("第3段", "第4段")
        assertTrue("另一段必须确实存在于书中，否则本用例没有意义", bookText.contains(otherParagraph))
        assertFalse(
            "导出里只有第3段；用第4段作预期必须失败",
            ExportExcerptRoundTripVerdict.exportContainsExcerpt(deviceExport, otherParagraph),
        )
        assertFalse(
            "选区身份校验：payload 里是第3段时，第4段不得被认作已落库",
            ExportExcerptRoundTripVerdict.selectionIsPersisted(otherParagraph, listOf(realExcerpt)),
        )
    }

    // ── 5. 截断 / 缺字 / 追加 必须失败 ───────────────────────────────────────

    @Test
    fun `a truncated excerpt is rejected`() {
        val truncated = realExcerpt.dropLast(1)
        assertFalse(
            ExportExcerptRoundTripVerdict.exportContainsExcerpt(deviceExport, truncated),
        )
    }

    @Test
    fun `a missing character in the middle is rejected`() {
        val holed = realExcerpt.removeRange(10, 11)
        assertTrue("构造的缺字串必须与原串不同", holed != realExcerpt)
        assertFalse(
            ExportExcerptRoundTripVerdict.exportContainsExcerpt(deviceExport, holed),
        )
    }

    @Test
    fun `an appended extra line is rejected`() {
        val extended = realExcerpt + "\n第4段：多出来的一行"
        assertFalse(
            ExportExcerptRoundTripVerdict.exportContainsExcerpt(deviceExport, extended),
        )
    }

    // ── 6. 多行：真实导出器渲染 → 往返 / 乱序 / 丢行 / 散落 ─────────────────

    private val multiLine = "第一行：真实多行摘录\n第二行：跨段继续\n第三行：到此结束"

    @Test
    fun `a multi line excerpt rendered by the real exporter round trips`() {
        val rendered = render(multiLine)
        // 先确认真实导出器确实把它渲染成引用块（不是我们自造的期望形态）。
        assertTrue("真实导出器必须渲染出首行标签", rendered.contains("> 摘录：第一行：真实多行摘录"))
        assertTrue("真实导出器必须渲染出后续行", rendered.contains("> 第二行：跨段继续"))
        assertTrue(
            "多行摘录必须能通过完整性判定（这是「只移除已知引用前缀」的意义）",
            ExportExcerptRoundTripVerdict.exportContainsExcerpt(rendered, multiLine),
        )
    }

    @Test
    fun `a multi line excerpt with reordered lines is rejected`() {
        val rendered = render(multiLine)
        val reordered = "第一行：真实多行摘录\n第三行：到此结束\n第二行：跨段继续"
        assertFalse(
            "行乱序不得通过（逐行同序要求）",
            ExportExcerptRoundTripVerdict.exportContainsExcerpt(rendered, reordered),
        )
    }

    @Test
    fun `a multi line excerpt with a dropped middle line is rejected`() {
        val rendered = render(multiLine)
        val droppedMiddle = "第一行：真实多行摘录\n第三行：到此结束"
        assertFalse(
            "缺中间行不得通过（逐行完整要求）",
            ExportExcerptRoundTripVerdict.exportContainsExcerpt(rendered, droppedMiddle),
        )
    }

    @Test
    fun `excerpt lines scattered outside one quote block are rejected`() {
        val rendered = render(multiLine)
        // 把第二行踢出引用块：三行不再同属一个连续块，无法拼成完整摘录。
        val scattered = rendered.lines().joinToString("\n") { line ->
            if (line == "> 第二行：跨段继续") "第二行：脱离引用块" else line
        }
        assertTrue("构造后必须真的失去该引用行", !scattered.contains("> 第二行：跨段继续"))
        assertFalse(
            "非连续拼接不得通过",
            ExportExcerptRoundTripVerdict.exportContainsExcerpt(scattered, multiLine),
        )
    }

    @Test
    fun `a blank excerpt never passes vacuously`() {
        assertFalse(ExportExcerptRoundTripVerdict.exportContainsExcerpt(deviceExport, ""))
        assertFalse(ExportExcerptRoundTripVerdict.exportContainsExcerpt(deviceExport, "   "))
        assertFalse(ExportExcerptRoundTripVerdict.exportContainsExcerpt(deviceExport, "\n"))
    }

    // ── 7. payload 解码：不得吞异常 ─────────────────────────────────────────

    @Test
    fun `payload decode failure throws and keeps the original cause`() {
        try {
            ExportExcerptRoundTripVerdict.decodePersistedExcerpts("{not-json")
            fail("损坏 payload 必须抛出 PayloadDecodeException，而不是返回空列表")
        } catch (e: ExportExcerptRoundTripVerdict.PayloadDecodeException) {
            assertNotNull("必须保留原始 cause，便于定位解码根因", e.cause)
        }
    }

    @Test
    fun `an empty payload is not silently treated as no excerpts`() {
        try {
            ExportExcerptRoundTripVerdict.decodePersistedExcerpts("")
            fail("空 payload 必须抛出，不得伪装成「没有摘录」")
        } catch (e: ExportExcerptRoundTripVerdict.PayloadDecodeException) {
            assertEquals("空 payload 没有底层 cause，必须显式说明", null, e.cause)
        }
    }

    @Test
    fun `persisted excerpts decode in exporter order`() {
        val payload = Json.encodeToString(
            InspirationPayloadData.serializer(),
            InspirationPayloadData(
                source = InspirationSourceInfo(bookTitle = "书", excerpt = "第一段摘录"),
                excerpts = listOf(
                    InspirationSourceInfo(bookTitle = "书", excerpt = "第二段摘录"),
                    InspirationSourceInfo(bookTitle = "书", excerpt = "  "),
                    InspirationSourceInfo(bookTitle = "书", excerpt = null),
                ),
            ),
        )
        assertEquals(
            "顺序必须与导出器一致（source 在前，excerpts 顺次在后），空白摘录按渲染口径跳过",
            listOf("第一段摘录", "第二段摘录"),
            ExportExcerptRoundTripVerdict.decodePersistedExcerpts(payload),
        )
    }

    // ── 8. 选区身份：逐字相等，不做 trim / 前缀匹配 ─────────────────────────

    @Test
    fun `selection identity accepts the exact persisted value`() {
        assertTrue(
            ExportExcerptRoundTripVerdict.selectionIsPersisted(realExcerpt, listOf(realExcerpt)),
        )
    }

    @Test
    fun `selection identity rejects trimmed blank and different values`() {
        assertFalse(
            "trim 后相等不算原样落库",
            ExportExcerptRoundTripVerdict.selectionIsPersisted(" $realExcerpt ", listOf(realExcerpt)),
        )
        assertFalse(
            ExportExcerptRoundTripVerdict.selectionIsPersisted("", listOf(realExcerpt)),
        )
        assertFalse(
            "前缀匹配不算身份校验",
            ExportExcerptRoundTripVerdict.selectionIsPersisted(realExcerpt.take(20), listOf(realExcerpt)),
        )
        assertFalse(
            ExportExcerptRoundTripVerdict.selectionIsPersisted(realExcerpt, emptyList()),
        )
    }

    // ── 9. 源文件一致性守卫 ─────────────────────────────────────────────────

    @Test
    fun `verdict rules file is byte identical in test and androidTest source sets`() {
        val moduleRoot = locateModuleRoot()
        val testCopy = File(moduleRoot, TEST_RULES_REL)
        val androidTestCopy = File(moduleRoot, ANDROID_TEST_RULES_REL)
        assertTrue("test 源集规则文件缺失：$testCopy", testCopy.isFile)
        assertTrue("androidTest 源集规则文件缺失：$androidTestCopy", androidTestCopy.isFile)
        assertEquals(
            "判定规则必须只有一份实现：仪器所用的 androidTest 副本与离线测试所用的 test 副本必须逐字节相同",
            sha256(testCopy.readBytes()), sha256(androidTestCopy.readBytes()),
        )
    }

    @Test
    fun `selection semantic tag literal matches the product seam source`() {
        val seam = File(
            locateModuleRoot(),
            "src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderSelectionToolbar.kt",
        )
        assertTrue(
            "产品侧最小语义接缝文件缺失：$seam（仪器需要它才能在保存前取到真实选区）",
            seam.isFile,
        )
        assertTrue(
            "产品侧必须定义同名标识常量，否则仪器与规则对象的字面量会悄悄分叉",
            seam.readText(Charsets.UTF_8).contains(
                "internal const val SELECTION_TOOLBAR_EXCERPT_TAG = " +
                    "\"" + ExportExcerptRoundTripVerdict.SELECTION_TOOLBAR_EXCERPT_TAG + "\"",
            ),
        )
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    /** 用**真实生产导出实现**渲染一条只含该摘录的灵感，作为多行往返的 ground truth。 */
    private fun render(excerpt: String): String {
        val payload = Json.encodeToString(
            InspirationPayloadData.serializer(),
            InspirationPayloadData(
                source = InspirationSourceInfo(
                    bookId = "b1", bookTitle = "书", chapterTitle = "第1章", excerpt = excerpt,
                ),
            ),
        )
        val entity = InspirationEntity(
            id = "i1", title = "t", body = "个人想法", payload = payload,
            created_at = "2026-10-20T00:00:00Z", updated_at = "2026-10-20T00:00:00Z",
        )
        return buildNotesExportMarkdown("书", emptyList(), emptyList(), listOf(entity), exportedAt)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun locateModuleRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, TEST_RULES_REL).isFile && File(dir, ANDROID_TEST_RULES_REL).isFile) return dir
            dir = dir.parentFile
        }
        throw AssertionError("无法定位 app 模块根目录（从 user.dir 向上未找到两个源集的规则文件）")
    }

    companion object {
        private const val DEVICE_EXPORT_SHA256 =
            "1a7b0fefbe9c5a24770fd64e904eaa33571b9611247c0036417e1f6c3926c4f0"
        private const val BOOK_TEXT_SHA256 =
            "5b5b2c98b076a00e3cbf8336862a7db7aa8cff50c09b5bda52baf1d2bc5529a8"
        private const val TEST_RULES_REL =
            "src/test/java/com/creationreadingassistant/acceptance/ExportExcerptRoundTripVerdict.kt"
        private const val ANDROID_TEST_RULES_REL =
            "src/androidTest/java/com/creationreadingassistant/acceptance/ExportExcerptRoundTripVerdict.kt"
    }
}
