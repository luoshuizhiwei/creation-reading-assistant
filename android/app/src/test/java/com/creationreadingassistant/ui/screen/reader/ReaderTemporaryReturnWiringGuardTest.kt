package com.creationreadingassistant.ui.screen.reader

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRA-TASK48-v4：临时返回保存守卫的**生产接线源码级结构守卫**。
 *
 * 为什么需要它：守卫对象（[TemporaryReturnSaveGuard]）与退出位置函数
 * （[temporaryReturnLeaveSourcePosition]）的单点语义都有纯 JVM 用例覆盖，
 * 但「resolveTemporaryReturnRoute 是否真的先武装」「组合期是否真的采纳会话
 * 凭证」「persistOnLeave 是否真的用实时位置裁决」**以及它是否携带所有权**
 * 都活在 Composable 里，JVM 执行不到。删掉/改坏下列任何一处接线，单点
 * 用例依然全绿，A2 合同却静默失效（这正是任务48-v2、48-v3 被 Codex 复核
 * 实证的两处缺口形态：v3 只把所有权落实在 release，没有落实到 observe 等
 * 其他修改入口）。
 *
 * 本类不是 Compose 运行时行为测试：它证明接线形状，运行时语义由
 * [ReaderTemporaryReturnLifecycleSequenceTest] 的生产序列回放与设备
 * 验收共同覆盖；每条断言先 fail-loud 检查文件与块存在，工作目录变化
 * 时不会静默通过。
 */
class ReaderTemporaryReturnWiringGuardTest {

    private val routeSource =
        File("src/main/java/com/creationreadingassistant/ui/navigation/ReaderTemporaryRoute.kt")
    private val screenSource =
        File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderScreen.kt")
    private val effectsSource =
        File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderProgressEffects.kt")

    private fun source(file: File): String {
        assertTrue(
            "源码路径必须存在（工作目录应为 app 模块根）：${file.absolutePath}",
            file.exists(),
        )
        return file.readText()
    }

    // ── 块定位工具（与 ReadingUnitDisposeWiringGuardTest 同款）─────────

    /** 从 [openIndex] 处的 `{` 起配平到对应 `}`，返回含两端花括号的整块文本。 */
    private fun matchBraces(text: String, openIndex: Int): String? {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(openIndex, i + 1)
                }
            }
        }
        return null
    }

    /** 定位 `[header] { ... }` 块：先找 [header]，再取其后的首个 `{` 配平。 */
    private fun blockAfter(text: String, header: String, fromIndex: Int = 0): String? {
        val headerIndex = text.indexOf(header, fromIndex)
        if (headerIndex < 0) return null
        val searchFrom = headerIndex + header.length - 1
        val openIndex = text.indexOf('{', searchFrom)
        if (openIndex < 0) return null
        return matchBraces(text, openIndex)
    }

    /** fail-loud 版 [blockAfter]：定位不到就带着原因直接失败。 */
    private fun requireBlock(text: String, header: String, fromIndex: Int = 0): String =
        blockAfter(text, header, fromIndex)
            ?: throw AssertionError("源码中定位不到 `$header { ... }` 块（接线被改或删除？）")

    // ── 1. resolveTemporaryReturnRoute 在导航前武装新返回会话 ─────

    @Test
    fun `temporary return route arms the guard before navigating back to normal reading`() {
        val resolverBlock = requireBlock(source(routeSource), "fun resolveTemporaryReturnRoute(")

        assertTrue(
            "resolveTemporaryReturnRoute 必须在最后返回普通 route 之前武装返回守卫",
            resolverBlock.contains("TemporaryReturnSaveGuard.arm("),
        )
        val armIndex = resolverBlock.indexOf("TemporaryReturnSaveGuard.arm(")
        val returnIndex = resolverBlock.indexOf("return if (stillTemporary)")
        assertTrue(
            "武装必须先于导航（arm 在 return 之前）",
            armIndex in 0 until returnIndex,
        )
        assertTrue(
            "武装只允许发生在最后一层返回（stillTemporary 为假时）",
            resolverBlock.contains("if (!stillTemporary)"),
        )
    }

    // ── 2. 组合期采纳会话凭证，且释放与退出裁决共用同一凭证 ─────────

    private fun temporaryReturnDisposeEffectBlock(screen: String): String =
        requireBlock(screen, "DisposableEffect(bid, temporaryInspection)")

    @Test
    fun `the screen adopts the armed session once at composition entry`() {
        val screen = source(screenSource)

        assertTrue(
            "ReaderScreen 必须在组合期采纳该书当前武装的会话凭证",
            screen.contains("TemporaryReturnSaveGuard.session(bid)"),
        )
        assertTrue(
            "凭证必须按书身份一次性采纳（remember(bid) 缓存），不得每次重组重新采纳",
            screen.contains("val ownedTemporaryReturnSession = remember(bid, temporaryInspection)") &&
                screen.contains("val owner = if (temporaryInspection) null else TemporaryReturnSaveGuard.session(bid)"),
        )
    }

    @Test
    fun `the dispose effect releases only the adopted session inside onDispose`() {
        val effect = temporaryReturnDisposeEffectBlock(source(screenSource))
        val onDispose = requireBlock(effect, "onDispose")

        assertTrue(
            "onDispose 必须按本页领到的会话凭证释放（release）",
            onDispose.contains("TemporaryReturnSaveGuard.release("),
        )
        assertTrue(
            "onDispose 释放的必须是组合期采纳的凭证",
            onDispose.contains("TemporaryReturnSaveGuard.release(bid, ownedTemporaryReturnSession)"),
        )
        assertFalse(
            "onDispose 不得再按 bookId 无条件 disarm —— 旧同书 route 迟到 dispose 会清掉新会话（任务48-v3 修复的缺口）",
            onDispose.contains("disarm("),
        )
    }

    @Test
    fun `the adopted credential is shared by the dispose release and the leave adjudication`() {
        val screen = source(screenSource)

        val adoptIndex = screen.indexOf("val ownedTemporaryReturnSession = remember(bid, temporaryInspection)")
        val releaseIndex = screen.indexOf("TemporaryReturnSaveGuard.release(bid, ownedTemporaryReturnSession)")
        val leaveIndex = screen.indexOf("val temporaryReturnFrozen = TemporaryReturnSaveGuard.observe(")

        assertTrue("采纳语句必须存在", adoptIndex >= 0)
        assertTrue("onDispose 必须复用采纳的凭证", releaseIndex >= 0)
        assertTrue("persistOnLeave 必须复用采纳的凭证", leaveIndex >= 0)
        assertTrue(
            "onDispose 释放与退出裁决必须使用**同一个**采纳的凭证变量 —— 否则二者会对不同会话说话",
            adoptIndex < releaseIndex && adoptIndex < leaveIndex,
        )
    }

    // ── 3. persistOnLeave 用实时权威位置裁决，并携带会话所有权 ─────────

    private fun persistOnLeaveBlock(screen: String): String =
        requireBlock(screen, "val persistOnLeave: () -> Unit =")

    @Test
    fun `persistOnLeave adjudicates the guard with the live authoritative position`() {
        val leave = persistOnLeaveBlock(source(screenSource))

        assertTrue(
            "persistOnLeave 必须先算实时权威位置（temporaryReturnLeaveSourcePosition）",
            leave.contains("temporaryReturnLeaveSourcePosition("),
        )
        assertTrue(
            "persistOnLeave 必须用该实时位置做守卫裁决，而不是只读 isArmed",
            leave.contains("TemporaryReturnSaveGuard.observe("),
        )
        assertFalse(
            "persistOnLeave 不得退回「只读 isArmed」的 v2 形态 —— 500ms 内移动退出会被误冻结",
            leave.contains("TemporaryReturnSaveGuard.isArmed(bid)"),
        )
    }

    /**
     * 定位 `[header]` 之后的那次调用，返回含两端括号的**实参列表**（含函数体）。
     * 用括号配平而不是花括号 —— 调用是 `(...)` 结构，不是 `{ ... }` 块。
     */
    private fun callWithArgs(text: String, header: String): String? {
        return allCallArgs(text, header).firstOrNull()
    }

    /** 收集 [text] 中 `[header]` 的**每一次**调用的实参列表（括号配平）。 */
    private fun allCallArgs(text: String, header: String): List<String> {
        val result = mutableListOf<String>()
        var from = 0
        while (true) {
            val headerIndex = text.indexOf(header, from)
            if (headerIndex < 0) break
            val openIndex = text.indexOf('(', headerIndex)
            if (openIndex < 0) break
            var depth = 0
            var end = -1
            for (i in openIndex until text.length) {
                when (text[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            end = i
                            break
                        }
                    }
                }
            }
            if (end < 0) break
            result.add(text.substring(openIndex, end + 1))
            from = end + 1
        }
        return result
    }

    @Test
    fun `persistOnLeave adjudicates under the session ownership instead of bare observe`() {
        // 任务48-v4：persistOnLeave 先裁决、后判断是否临时查阅。若裁决不带会话所有权
        // （裸 observe），旧同书临时页面用自己的查阅位置就能在「并未保存」时解除新
        // 普通会话。必须显式携带 owned 凭证。
        val leave = persistOnLeaveBlock(source(screenSource))

        val adjudication = callWithArgs(leave, "TemporaryReturnSaveGuard.observe(")
        assertNotNull("persistOnLeave 必须调用带所有权的裁决入口 observe(...)", adjudication)
        assertTrue(
            "退出裁决调用必须显式传入本页领到的凭证实参（owned = ownedTemporaryReturnSession）",
            adjudication!!.contains("owned = ownedTemporaryReturnSession"),
        )
        assertTrue(
            "退出裁决必须以实时权威位置作为坐标来源",
            adjudication.contains("temporaryReturnPosition"),
        )
    }

    @Test
    fun `the guard adjudication happens before the temporaryInspection gate in persistOnLeave`() {
        // 任务48-v4 的缺口正在这个顺序上：裁决（可能改写守卫）必须先于门控判断，
        // 因此裁决必须自带所有权；反过来若把临时门控放在裁决之前，就等于用
        // 「临时页面不保存」掩盖「临时页面仍能解除新会话」。
        val leave = persistOnLeaveBlock(source(screenSource))

        val adjudicateIndex = leave.indexOf("TemporaryReturnSaveGuard.observe(")
        // 只匹配实际门控语句，避免命中注释里提到的 `!temporaryInspection`。
        val gateIndex = leave.indexOf("if (!temporaryInspection")
        assertTrue("裁决与门控都必须存在", adjudicateIndex >= 0 && gateIndex >= 0)
        assertTrue(
            "persistOnLeave 必须**先裁决、后判断临时模式**（生产实际顺序）",
            adjudicateIndex < gateIndex,
        )
    }

    // ── 4. 三条防抖落库路径都以会话所有权裁决，且不再有裸 observe ────

    @Test
    fun `the debounce consumers adjudicate under the session ownership at each persist path`() {
        val effects = source(effectsSource)

        val calls = allCallArgs(effects, "TemporaryReturnSaveGuard.observe(")
        assertEquals(
            "Markdown / 纯文本滚动 / 分页三条防抖落库路径都必须保留会话裁决",
            3,
            calls.size,
        )
        calls.forEach { args ->
            assertTrue(
                "防抖裁决必须携带本页领到的会话凭证（owned =），不得是无所有权调用 —— 该入口会改写守卫状态",
                args.contains("owned = ownedTemporaryReturnSession"),
            )
        }
    }

    // ── 5. 生产源码不再有「无所有权的修改入口」调用 ──────────────────

    @Test
    fun `no production code calls a state-mutating guard entry without session ownership`() {
        // 任务48-v4 的核心不变量：所有会修改守卫状态的生产入口都必须按会话凭证限定。
        val mainDir = File("src/main/java/com/creationreadingassistant")
        assertTrue("main 源码目录必须存在", mainDir.exists())

        val ktFiles = mainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        var bareObserve = 0
        var bareClear = 0
        var armCalls = 0
        val offenders = mutableListOf<String>()
        ktFiles.forEach { f ->
            val text = f.readText()
            allCallArgs(text, "TemporaryReturnSaveGuard.observe(").forEach { args ->
                if (!args.contains("owned =")) {
                    bareObserve++
                    offenders.add("${f.path}: observe(...) 缺少 owned 凭证")
                }
            }
            Regex("TemporaryReturnSaveGuard\\.clear\\(").findAll(text).forEach {
                bareClear++
                offenders.add("${f.path}: 无条件 clear()")
            }
            Regex("TemporaryReturnSaveGuard\\.arm\\(").findAll(text).forEach { armCalls++ }
        }

        assertEquals(
            "生产代码不得调用无所有权的裁决入口 observe(...)（它会改写守卫状态）\n$offenders",
            0,
            bareObserve,
        )
        assertEquals(
            "生产代码不得存在按 bookId 无条件重置守卫状态的 clear() 调用\n$offenders",
            0,
            bareClear,
        )
        assertEquals(
            "武装只允许发生在两个合同入口：resolveTemporaryReturnRoute（LIFO 返回栈空回普通阅读）与 " +
                "restoreCommittedReaderSessionRoute（207-r2 未提交 route 还原已提交会话且回普通阅读）\n$offenders",
            2,
            armCalls,
        )
        // 207-r2 的第二个武装位必须结构性落在 restoreCommittedReaderSessionRoute 的
        // 「栈已空（回普通阅读）」分支内，不得出现在无条件路径上（否则未提交 route 的
        // temporary 还原也会改写守卫状态）。该函数在 ReaderRoute.kt（screen/reader）。
        val routeDispatchSource =
            File("src/main/java/com/creationreadingassistant/ui/screen/reader/ReaderRoute.kt")
        val restoreFunIndex = source(routeDispatchSource).indexOf("private fun restoreCommittedReaderSessionRoute(")
        assertTrue("restoreCommittedReaderSessionRoute 必须存在（207-r2 未提交还原接缝）", restoreFunIndex >= 0)
        val restoreBlock = requireBlock(source(routeDispatchSource), "private fun restoreCommittedReaderSessionRoute(")
        val stillTemporaryGuard = restoreBlock.indexOf("TemporaryReturnSaveGuard.arm(")
        assertTrue(
            "restoreCommittedReaderSessionRoute 必须武装返回守卫（还原到普通阅读与 LIFO 末层同合同）",
            stillTemporaryGuard >= 0,
        )
        val stillTemporaryIndex = restoreBlock.indexOf("stillTemporary")
        assertTrue(
            "restore 的武装必须以 stillTemporary 为条件（栈非空时保持 temporary 语义，不得武装）",
            stillTemporaryIndex in 0 until stillTemporaryGuard,
        )
    }

    // ── 6. 会话位置守卫的诊断观测携带书籍身份（任务50 采纳的接缝）──────

    @Test
    fun `session guard observe diagnostics carry the book identity`() {
        // 只读诊断接缝：session_guard_observe 是整条身份链上唯一没有书籍身份的一环，
        // 缺它时「这条位移观测属于哪本书」无法核实，保护结论无法签出。
        // 此处只做源码级结构断言 —— 诊断 log 依赖 android SystemClock，JVM 跑不动。
        val effects = source(effectsSource)

        val eventIndex = effects.indexOf("\"session_guard_observe\"")
        assertTrue("session_guard_observe 诊断打点必须存在", eventIndex >= 0)
        val observeIndex = effects.indexOf("sessionPositionGuard.observe(", eventIndex)
        assertTrue(
            "诊断打点必须紧邻真实守卫观测调用（不是游离的日志）",
            observeIndex > eventIndex,
        )
        val logFields = effects.substring(eventIndex, observeIndex)
        assertTrue(
            "session_guard_observe 必须带上书籍身份（\"bid\" to bid），否则该观测无法绑定目标书",
            logFields.contains("\"bid\" to bid"),
        )
        // 接缝只允许加字段：守卫观测本身与既有字段不得被改动。
        assertTrue("既有 armed 字段必须保留", logFields.contains("\"armed\" to sessionPositionGuard.isArmed()"))
        assertTrue("既有 moved 字段必须保留", logFields.contains("\"moved\" to sessionPositionGuard.hasUserMoved()"))
        assertTrue("既有坐标字段必须保留", logFields.contains("\"index\" to index"))
        assertTrue("既有坐标字段必须保留", logFields.contains("\"offsetPx\" to offsetPx"))
    }
}
