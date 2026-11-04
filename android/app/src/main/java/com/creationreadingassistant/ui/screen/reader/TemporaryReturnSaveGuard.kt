package com.creationreadingassistant.ui.screen.reader

/**
 * CRA-TASK48-v4：从临时查阅返回普通阅读时的保存保护守卫（纯 JVM 可测）。
 *
 * 背景（A2 合同）：
 * 用户在普通阅读处已保存了稳定基线（freeze_point）。经「阅读笔记」等入口进入临时查阅，
 * 随后点击「返回阅读处」（或系统 Back）回到普通阅读。在用户于普通阅读处发生真实位移之前，
 * 全部 12 列（含 last_read_at 与 updated_at）必须严格保持冻结；一旦发生真实位移（翻页、
 * 滚动、章节跳转），立即解除保护并正常保存最新进度。
 *
 * 机制：
 * 1. 【明确的会话所有权（v3）】：仅在 LIFO 临时查阅返回普通阅读的瞬间（resolveTemporaryReturnRoute
 *    发现已清空临时栈且目标有效时）武装，传入真实恢复基线（[ReturnBaseline]）并发放唯一
 *    会话凭证 [Session]。任何页面只能释放**自己领到的**会话：页面进入组合时经 [session]
 *    领取该书当前武装会话，onDispose 经 [release] 凭该凭证解除。旧临时页面在武装**之前**
 *    进入组合、领到的是 null，其迟到的 onDispose 不可能清掉 resolveTemporaryReturnRoute
 *    随后为新普通页面武装的会话；新普通页面在导航之后进入组合，稳定领到该会话。
 *    普通从书架/首页打开书籍恒不武装，绝不把全局"同位置不更新"泛化到普通阅读。
 * 2. 【以实际恢复基线为判定准绳】：不将 500ms 防抖后的首个合法样本误认作恢复点（用户在 500ms 内
 *    快速翻页/移动时，首个输出即为新位置，不得丢弃）；而是直接比对当前坐标与实际恢复基线。
 *    坐标相等时抑制保存；坐标不等时（用户已移动）立即解除武装并放行保存。
 * 3. 【退出实时裁决（v3）】：裁决入口不只服务 500ms 防抖消费者，也是退出落库
 *    （persistOnLeave）的裁决：它直接以**实时权威位置**（见
 *    [temporaryReturnLeaveSourcePosition]，与四个防抖落库路径同口径）比对返回基线，
 *    不假设防抖已经运行 —— 用户返回后移动并在 500ms 内退出时，退出保存照常放行；
 *    未移动（或位置尚未就绪）时完整 12 列保持冻结。
 * 4. 【所有权覆盖**所有**生产修改入口（v4）】：v3 只把所有权落实在 [release]，
 *    而 [observe] 在位置不等时也会 `armed.remove(bookId)` —— 那是一个会改写守卫状态的
 *    **修改**入口。`ReaderScreen.persistOnLeave` 先算位置并裁决，之后才用
 *    `!temporaryInspection` 决定是否真正保存，所以旧同书临时页面的 ON_PAUSE / 迟到离开
 *    回调（它持有自己的**查阅**位置）只要位置与新返回基线不等，就能在「并未真正保存」
 *    的情况下解除新普通会话的保护。v4 把**所有生产**裁决入口统一收进 [observeOwned]：
 *    凭证不等于**当前**武装会话的调用（旧 route / 旧会话 / 无所有权回调）一律不读不写、
 *    不判定也不抑制；只有持有当前会话凭证的页面（新普通页面的退出落库与三条防抖
 *    消费者）才能裁决并解除。
 *
 * 判定逻辑保持 v3 语义（[observe] 的裁决不变，供 v3 已验收的守卫语义用例继续覆盖）；
 * 变化只在**调用侧所有权**：生产代码不再调用无所有权的 [observe]。
 */
internal object TemporaryReturnSaveGuard {

    internal data class ReturnBaseline(
        val offset: Int,
        val chapterIndex: Int? = null,
    )

    /**
     * 武装会话的所有权凭证（任务48-v3/v4）：由 [arm] 随基线一并发放。
     * 只有持有**当前**武装会话凭证的页面才能裁决（[observeOwned]）或解除保护（[release]）
     * —— 凭证按会话身份比较，旧页面 / 旧代际 / 无凭证的调用对新会话一律无效。
     */
    internal class Session internal constructor(private val id: Long) {
        override fun equals(other: Any?): Boolean = (other is Session) && other.id == id
        override fun hashCode(): Int = id.hashCode()
        override fun toString(): String = "TemporaryReturnSession($id)"
    }

    private data class ArmedReturn(
        val baseline: ReturnBaseline,
        val session: Session,
    )

    private val armed = HashMap<String, ArmedReturn>()
    private var nextSessionId = 0L

    /**
     * 武装一次新的临时返回会话（替换该书既有武装），返回新会话的所有权凭证。
     * 组合不必保管返回值：进入组合时经 [session] 领取、退出时凭领取的凭证 [release]，
     * 裁决时凭同一凭证 [observeOwned]。
     */
    @Synchronized
    fun arm(bookId: String, returnOffset: Int?, chapterIndex: Int? = null): Session? {
        if (bookId.isNotBlank() && returnOffset != null && returnOffset >= 0) {
            val session = Session(++nextSessionId)
            armed[bookId] = ArmedReturn(ReturnBaseline(returnOffset, chapterIndex), session)
            return session
        }
        return null
    }

    /** 该书当前武装会话的凭证；未武装返回 null。页面进入组合时领取。 */
    @Synchronized
    fun session(bookId: String): Session? = armed[bookId]?.session

    @Synchronized
    fun isArmed(bookId: String): Boolean = armed.containsKey(bookId)

    /**
     * 会话所有权裁决（任务48-v4）：**唯一**修改守卫状态的裁决入口。
     *
     * 仅在 [owned] 仍是该书**当前**武装会话的凭证时才读取基线并裁决（位置不等时解除武装）。
     * 旧 route（武装前进入组合 → 凭证为 null）、旧代际凭证、或任何其他调用者一律不触碰守卫状态。
     *
     * @param owned 本页面进入组合时经 [session] 领取的凭证；**缺省 null 表示调用者没有
     *              当前会话所有权**（旧 route / 迟到回调 / 临时查阅页），此时本函数
     *              既不冻结也不解除，恒返回 false。生产调用点必须显式传入凭证 ——
     *              `ReaderTemporaryReturnWiringGuardTest` 会逐条核对这一点。
     * @return true = 仍处于返回基线（或位置未就绪），继续抑制保存；
     *         false = 用户已发生真实位移（已解除），或该书未武装，或调用者无当前会话
     *         所有权（此时**不冻结也不解除**）
     */
    @Synchronized
    fun observe(bookId: String, currentOffset: Int?, currentChapterIndex: Int? = null, owned: Session? = null): Boolean {
        val entry = armed[bookId] ?: return false
        if (entry.session != owned) {
            // 旧 route / 旧会话 / 无所有权回调：不得改写守卫状态，也不判定冻结。
            return false
        }
        return adjudicate(entry.baseline, currentOffset, currentChapterIndex) { armed.remove(bookId) }
    }

    /**
     * 裁决内核（任务48-v4）：以**实际恢复基线**为判定准绳。位置未就绪 → 抑制；
     * 与基线相等 → 抑制；不等 → 调用 [disarm] 解除武装并放行。
     * 所有权检查发生在调用本函数**之前**（[observeOwned]），因此本函数本身不认调用者。
     */
    private fun adjudicate(
        baseline: ReturnBaseline,
        currentOffset: Int?,
        currentChapterIndex: Int?,
        disarm: () -> Unit,
    ): Boolean {
        if (currentOffset == null || currentOffset < 0) {
            // 尚未解析或未准备好，维持抑制
            return true
        }
        val sameOffset = currentOffset == baseline.offset
        val sameChapter = baseline.chapterIndex == null || currentChapterIndex == null || currentChapterIndex == baseline.chapterIndex
        if (sameOffset && sameChapter) {
            // 仍处于返回基线位置：抑制保存
            return true
        }
        // 用户已经移动（偏移改变或章节改变）：解除武装，放行保存
        disarm()
        return false
    }

    /**
     * 会话所有权释放（任务48-v3/v4）：仅当 [session] 仍是该书**当前**武装会话的
     * 凭证时才解除保护。
     *
     * 旧页面（武装前进入组合 → 凭证为 null）、迟到 dispose、或已被替换的代际
     * 凭证均为空操作 —— 任何页面只能释放自己领到的会话，不能释放新会话的状态。
     */
    @Synchronized
    fun release(bookId: String, session: Session?) {
        val entry = armed[bookId] ?: return
        if (session != null && session == entry.session) {
            armed.remove(bookId)
        }
    }

    /**
     * 清空全部武装（**测试专用**）：无生产调用点。
     * 守卫的所有修改入口都必须按会话凭证限定（[observeOwned] / [release]），
     * 因此不存在生产可用的「按 bookId 无条件重置」路径。
     */
    @Synchronized
    fun clear() {
        armed.clear()
    }
}
