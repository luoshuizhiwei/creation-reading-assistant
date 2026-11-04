package com.creationreadingassistant.acceptance

import com.creationreadingassistant.ui.viewmodel.InspirationPayloadData
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * CRA-EXPORT-CONTENT-80-v2-A —— 「UI 选区 → 持久化 payload → 导出文档」三段独立校验的
 * **纯判定规则**（零 Android 依赖，可在 JVM 直接执行）。
 *
 * 之所以独立成纯规则对象：本任务最容易写错的正是「比对基准到底是不是用户选中的那段」
 * 与「Markdown 引用块里的摘录是否完整」这两类判定。把它们抽成可离线逐条反例的纯函数，
 * 仪器只负责采集真实 UI 语义 / Room 行 / 导出字节，判定只有一份实现
 * （本文件在 androidTest 与 test 两个源集中**逐字节相同**，由 test 侧的字节一致性守卫
 * 保证不会悄悄分叉——与既有 `ReaderClampRemeasureVerdict` 同一做法）。
 *
 * ── 80-v2 相对 80-v1 修的三处 ─────────────────────────────────────────────
 *
 * 1. **选区基准必须来自保存前的 UI**：80-v1 直接取「保存后的 payload」当预期，
 *    于是任何写进 payload 的内容都会自我一致地通过。本对象不提供任何「从 payload
 *    反推预期」的入口；`selectionIsPersisted` 只回答「保存前读到的选区是否原样落库」。
 * 2. **payload 解码失败必须抛出**：80-v1 用 `runCatching{}.getOrNull() ?: emptyList()`
 *    把解码失败变成「没有摘录」，掩盖根因。本对象抛 [PayloadDecodeException] 并保留 cause。
 * 3. **导出比对只允许移除已知引用前缀**：`> ` / `>`（空行）与首行的 `摘录：` 标签，
 *    其余必须逐行、逐字、同序、连续相等；缺字、截断、错段、行乱序、非连续拼接一律 false。
 *
 * 本对象不读数据库、不写文件、不调用产品导出实现；它只对**已采集的真实值**做判定。
 */
internal object ExportExcerptRoundTripVerdict {

    /** 导出器给摘录首行加的已知标签（`> 摘录：…`），见 ReaderNotesExporter 的灵感节渲染。 */
    const val EXCERPT_LABEL = "摘录："

    /**
     * 选区工具条微条上「本轮真实选中文本」的语义标识。
     *
     * 该标识由产品侧最小接缝提供（`ReaderSelectionToolbar` 的微条 Text），
     * 因为现有语义不足以定位它：整份 `ReaderSelectionToolbar.kt` 只有高亮色块的
     * `contentDescription`，微条 Text 既无 contentDescription 也无 testTag，
     * 容器 `ReaderPanelSurface` 也没有标识；而微条是 `maxLines = 1 + Ellipsis`，
     * 结构上永远短于整页正文，任何「取最长文本」都必然落空。
     */
    const val SELECTION_TOOLBAR_EXCERPT_TAG = "selection-toolbar-excerpt"

    /** 引用行：`> 内容`，或单独 `>`（导出器把摘录里的空行渲染成 `>`）。 */
    private val QUOTE_LINE = Regex(""">(?: (.*))?""")

    /** payload 解码口径与 `ReaderNotesExporter` / `InspirationViewModel` 完全一致。 */
    private val PAYLOAD_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** payload 解码失败：必须抛出并保留原始 cause，不得退化成「没有摘录」。 */
    class PayloadDecodeException(message: String, cause: Throwable?) :
        IllegalStateException(message, cause)

    /**
     * 产品实际持久化的来源摘录，顺序与导出器一致（`source` 在前，`excerpts` 顺次在后）。
     *
     * 解码失败 **抛异常**，不返回空列表；空 payload 同样抛异常——「读不到」与
     * 「读到但没有摘录」是两种完全不同的结论，不能合并。
     */
    fun decodePersistedExcerpts(payload: String?): List<String> {
        if (payload.isNullOrBlank()) {
            throw PayloadDecodeException(
                "payload 为空：无法取得持久化摘录（不得把「读不到」当成「没有摘录」）", null,
            )
        }
        val data = try {
            PAYLOAD_JSON.decodeFromString(InspirationPayloadData.serializer(), payload)
        } catch (e: SerializationException) {
            throw PayloadDecodeException("payload 解码失败，原始原因保留：${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw PayloadDecodeException("payload 解码失败，原始原因保留：${e.message}", e)
        }
        val sources = buildList {
            data.source?.let { add(it) }
            addAll(data.excerpts)
        }
        // 渲染侧对空白摘录整段跳过（takeIf { it.isNotBlank() }），这里保持同一口径。
        return sources.mapNotNull { src -> src.excerpt?.takeIf { it.isNotBlank() } }
    }

    /**
     * 保存前读到的 UI 选区是否**原样**落库（逐字相等，不做 trim / 忽略大小写 / 前缀匹配）。
     *
     * 这是三段链里的第二段：只有保存前独立取得的值才能当预期，不能用 payload 反推。
     */
    fun selectionIsPersisted(selected: String, persisted: List<String>): Boolean =
        selected.isNotBlank() && persisted.any { it == selected }

    /**
     * 导出文档是否**完整、有序、连续**地保留了 [excerpt]。
     *
     * 只移除已知格式：引用前缀（`> ` / `>`）与摘录首行的 `摘录：` 标签。
     * 移除后要求整块内容与 `excerpt.lines()` 逐行逐字相等，因此
     * 缺字、截断、错段、行乱序、跨块拼接、散落行都不可能通过。
     *
     * 空白摘录恒为 false：渲染侧不会为它输出任何内容，不能空过。
     */
    fun exportContainsExcerpt(exportText: String, excerpt: String): Boolean {
        if (excerpt.isBlank()) return false
        val expected = excerpt.lines()
        return quoteBlocks(exportText).any { block ->
            if (block.isEmpty() || !block.first().startsWith(EXCERPT_LABEL)) return@any false
            val body = block.mapIndexed { index, line ->
                if (index == 0) line.removePrefix(EXCERPT_LABEL) else line
            }
            body == expected
        }
    }

    /**
     * 把导出文本切成「连续引用块」，每块是去掉引用前缀后的内容行。
     *
     * 相邻的引用行属于同一块；非引用行（标题、`来源：`、`位置：`、空行）是块边界。
     */
    fun quoteBlocks(text: String): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (raw in text.lines()) {
            val match = QUOTE_LINE.matchEntire(raw)
            if (match != null) {
                current.add(match.groupValues[1])
            } else if (current.isNotEmpty()) {
                blocks.add(current)
                current = mutableListOf()
            }
        }
        if (current.isNotEmpty()) blocks.add(current)
        return blocks
    }
}
