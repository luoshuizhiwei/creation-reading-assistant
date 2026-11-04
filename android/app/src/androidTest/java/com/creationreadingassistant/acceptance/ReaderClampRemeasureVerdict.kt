package com.creationreadingassistant.acceptance

/**
 * CRA-REMEASURE-PROBE-REPAIR-v3 —— D 重测量探针的**纯判定规则**（零 Android 依赖）。
 *
 * 之所以独立成纯规则对象：探针里最容易写错的正是「12 列是否真的没变」「防抖裁决是否真的
 * 由本次窗口变化引起」这两类判定。把它们抽成可离线执行、可逐条反例的纯函数，探针只负责
 * 采集原始事件与行快照，判定只有一份实现（本文件在 androidTest 与 test 两个源集中
 * 逐字节相同，由 test 侧的字节一致性守卫保证不会悄悄分叉）。
 *
 * 本对象不调用 guard、不伪造事件、不注入手势；它只对**已采集的真实生产日志与 Room 行**做判定。
 *
 * ── v3 相对 v2 只修**证据边界**，判定口径不变（保留完整 12 列比较）──
 *
 * 1. **时间边界三分**（[WindowChangeBoundary]）：操作**请求**时间 ≠ 命令**完成/回读**时间 ≠
 *    实际 Compose root **变化**时间。v2 用 `tChange`（请求时刻）当"变化后"起点，于是"请求后、
 *    真实变化前"的 observe 会混进变化后集合。v3 只允许 `rootChangedNanos`（实测到 root 边界
 *    变化的时刻）作为变化后起点；无法核实该时刻时整条链保持 inconclusive。
 * 2. **坐标必须存在且合法**：v2 用 `int("index") != anchorIndex || int("offsetPx") != anchorOffsetPx`，
 *    字段缺失得到 null，与 anchor 不等即被当成有效位移。v3 要求 index/offsetPx 都能解析为 Int
 *    且 index 非负，否则该观测是**无效**（记录到 [Verdict.invalidCoordinateObserves]，不参与判定）。
 * 3. **身份与作用域分离**：PID 采集过滤仍然有效（保留），但 `scopeEvents` 允许缺 `bid` 的事件
 *    通过，这只代表"没被排除"，**不代表已绑定到本书**。生产 `session_guard_observe` 目前不带
 *    `bid`/`sid`（见 [REQUIRED_PRODUCTION_SEAM]），因此缺身份时一律不签保护结论，只保留原始观测
 *    与缺失原因。本任务不抢写 main（该文件由任务48 拥有），只登记最小接缝。
 */
internal object ReaderClampRemeasureVerdict {

    /** 生产防抖链中，会话守卫对某次坐标变化的两种真实裁决事件名。 */
    const val DEBOUNCE_BLOCKED_BY_SESSION_GUARD = "debounce_save_blocked_by_session_guard"
    const val DEBOUNCE_SAVE_DISPATCH = "debounce_save_dispatch"

    /** 生产守卫的即时观测 / 锚定事件名。 */
    const val OBSERVE = "session_guard_observe"
    const val ANCHOR = "session_guard_anchor"

    /**
     * v3 认定的**最小生产诊断接缝**（不在本任务内实施，交 Root/任务48 决定）。
     *
     * 生产 `ReaderProgressEffects.kt` 的 `session_guard_observe` 当前只输出
     * `index/offsetPx/armed/moved`，**没有 bid 也没有会话 id**；而 `session_guard_anchor`、
     * `debounce_save_blocked_by_session_guard`、`debounce_save_dispatch`、
     * `persist_current_progress_dispatch` 都带 `bid`。也就是说：观测这一环的身份链是断的。
     * 在补上该字段之前，任何"这条观测属于本书"的说法都无法核实，判定必须保守。
     */
    val REQUIRED_PRODUCTION_SEAM: List<String> = listOf(
        "session_guard_observe: bid=<当前书籍 id>（或 sid=<会话 id>，且 session_guard_anchor 同时带 sid）",
    )

    /** 一条生产诊断事件（字段为字符串，与 logcat 采集一致）。 */
    data class Evt(val seq: Int, val tsNanos: Long, val event: String, val fields: Map<String, String>) {
        fun int(key: String): Int? = fields[key]?.trim()?.toIntOrNull()
        fun str(key: String): String? = fields[key]?.trim()?.takeIf { it.isNotEmpty() }
        /** 时间戳必须是有效正值，否则无法参与任何"变化前/后"的比较。 */
        fun hasValidTs(): Boolean = tsNanos > 0L
    }

    /** 一次守卫观测的列表坐标。index 是列表项下标（必为非负）；offsetPx 是像素偏移（符号无限制）。 */
    data class Coord(val index: Int, val offsetPx: Int)

    /**
     * 事件与目标书籍的**身份绑定**结果。
     *
     * [IdentityBinding.UNBOUND] 表示**无法核实**（缺字段），不是"已证明属于别的书"；
     * [IdentityBinding.OTHER_BOOK] 表示字段明确指向另一本书。
     */
    enum class IdentityBinding { TARGET_BOOK, OTHER_BOOK, UNBOUND }

    /**
     * 一次窗口/视口变化的**三类时间边界**（v3 的核心修复）。
     *
     * - [requestNanos]：操作**请求**时刻（下发 `wm size` / 设置 `requestedOrientation` 之前记录）。
     *   它只证明"我们请求过"，**不证明**窗口已经变化。
     * - [commandDoneNanos]：命令**完成/回读**时刻（`wm size` 回读成功，或 orientation 已生效回读）。
     *   它只证明命令被系统接受，**不证明** Compose 已完成重新测量。
     * - [rootChangedNanos]：探针**实测**到 Compose root 边界发生变化的时刻。
     *   只有它才是"变化后"的可核实起点；为 null 即本轮无法核实真实变化时刻。
     */
    data class WindowChangeBoundary(
        val requestNanos: Long,
        val commandDoneNanos: Long?,
        val rootChangedNanos: Long?,
        val rootBoundsChanged: Boolean,
    ) {
        val rootChangeVerified: Boolean
            get() = rootBoundsChanged && rootChangedNanos != null && rootChangedNanos > 0L

        /** 可核实的"变化后"起点；尚未核实则整条"变化后"证据链不成立。 */
        val postChangeFromNanos: Long?
            get() = if (rootChangeVerified) rootChangedNanos else null
    }

    /** reading_progress 完整 12 列（可比较形式）。 */
    data class Row12(
        val book_id: String?,
        val progress_percent: String?,
        val last_read_at: String?,
        val total_reading_time_ms: String?,
        val completion_state: String?,
        val current_location_json: String?,
        val payload: String?,
        val revision: String?,
        val device_id: String?,
        val updated_at: String?,
        val completed_at: String?,
        val deleted_at: String?,
    )

    val COLUMN_NAMES: List<String> = listOf(
        "book_id", "progress_percent", "last_read_at", "total_reading_time_ms", "completion_state",
        "current_location_json", "payload", "revision", "device_id", "updated_at", "completed_at", "deleted_at",
    )

    fun isDebounceEvent(event: String): Boolean =
        event.startsWith("debounce_save_") || event == "persist_current_progress_dispatch"

    fun isSessionGuardVerdict(event: String): Boolean =
        event == DEBOUNCE_BLOCKED_BY_SESSION_GUARD || event == DEBOUNCE_SAVE_DISPATCH

    /**
     * 本 run / 本书范围内的事件。PID 已在 logcat 采集层用 `--pid` 限定；这里再按 run 起点
     * （seq 与时间）与 book id 过滤 —— 别的书（或别的 run）的事件不得混入本次判定。
     *
     * **注意（v3）：作用域裁剪 ≠ 身份绑定。** 缺 `bid` 的事件在这里被"保留"，只是因为它
     * 没被排除，不代表它已被证明属于本书。身份判定见 [identityBindingOf]，保护结论必须
     * 另行要求 [IdentityBinding.TARGET_BOOK]。
     */
    fun scopeEvents(events: List<Evt>, runStartSeq: Int, runStartNanos: Long, bookId: String): List<Evt> =
        events.filter {
            it.seq > runStartSeq && it.tsNanos >= runStartNanos &&
                (it.fields["bid"] == null || it.fields["bid"] == bookId)
        }

    /**
     * 观测事件的**有效坐标**；缺字段、不可解析、下标为负一律返回 null（v3：
     * "null 与 anchor 不等" 不得被当成位移）。
     */
    fun observeCoordinates(evt: Evt): Coord? {
        val index = evt.int("index") ?: return null
        val offsetPx = evt.int("offsetPx") ?: return null
        if (index < 0) return null
        return Coord(index, offsetPx)
    }

    /** anchor 坐标同样必须合法，否则"是否与 anchor 不同"这个问题本身无定义。 */
    fun anchorCoordinates(index: Int?, offsetPx: Int?): Coord? {
        val i = index ?: return null
        val o = offsetPx ?: return null
        if (i < 0) return null
        return Coord(i, o)
    }

    /** 由 anchor 事件（生产带 `bid`）建立的「会话 id → 书籍 id」绑定表。 */
    fun sessionBindings(events: List<Evt>): Map<String, String> =
        events.filter { it.event == ANCHOR }.mapNotNull { e ->
            val sid = e.str("sid") ?: return@mapNotNull null
            val bid = e.str("bid") ?: return@mapNotNull null
            sid to bid
        }.toMap()

    /** 单条事件相对目标书籍的身份绑定结果。 */
    fun identityBindingOf(
        evt: Evt,
        targetBookId: String,
        sessionToBook: Map<String, String>,
    ): IdentityBinding {
        val bid = evt.str("bid")
        if (bid != null) return if (bid == targetBookId) IdentityBinding.TARGET_BOOK else IdentityBinding.OTHER_BOOK
        val sid = evt.str("sid") ?: return IdentityBinding.UNBOUND
        return when (val bound = sessionToBook[sid]) {
            null -> IdentityBinding.UNBOUND
            targetBookId -> IdentityBinding.TARGET_BOOK
            else -> IdentityBinding.OTHER_BOOK
        }
    }

    /**
     * 完整 **12 列**逐列比较，返回**发生变化的列名**；空列表 = 整行未变。
     * 任一列（含 last_read_at / progress_percent / total_reading_time_ms / completion_state 等）
     * 不同都必须体现出来，不能只看 locator 与 updated_at。
     */
    fun changedColumns(before: Row12, after: Row12): List<String> {
        val diff = ArrayList<String>(12)
        if (before.book_id != after.book_id) diff.add("book_id")
        if (before.progress_percent != after.progress_percent) diff.add("progress_percent")
        if (before.last_read_at != after.last_read_at) diff.add("last_read_at")
        if (before.total_reading_time_ms != after.total_reading_time_ms) diff.add("total_reading_time_ms")
        if (before.completion_state != after.completion_state) diff.add("completion_state")
        if (before.current_location_json != after.current_location_json) diff.add("current_location_json")
        if (before.payload != after.payload) diff.add("payload")
        if (before.revision != after.revision) diff.add("revision")
        if (before.device_id != after.device_id) diff.add("device_id")
        if (before.updated_at != after.updated_at) diff.add("updated_at")
        if (before.completed_at != after.completed_at) diff.add("completed_at")
        if (before.deleted_at != after.deleted_at) diff.add("deleted_at")
        return diff
    }

    /** 判定结果。语义必须自洽：remeasureObserved=false 或身份不足时不得出现"保护通过"。 */
    data class Verdict(
        val windowBoundaryKnown: Boolean,
        val postChangeFromNanos: Long?,
        val remeasureObserved: Boolean,
        val firstChangedObserveSeq: Int?,
        val identitySufficient: Boolean,
        val identityBinding: IdentityBinding?,
        val identityMissingFields: List<String>,
        val rawPostChangeObserves: List<Evt>,
        val invalidCoordinateObserves: List<Evt>,
        val changedObserves: List<Evt>,
        val unboundChangedObserves: List<Evt>,
        val causalVerdictEvents: List<Evt>,
        val guardVerdict: String,
        val stage: String,
        val rowChangedColumns: List<String>,
        val rowUnchangedAfterRemeasure: Boolean,
        val protectionClaim: String,
        val inconclusiveReason: String?,
    )

    /**
     * 由**已采集的真实证据**做判定。
     *
     * @param events 已按 PID/run 采集的生产诊断事件
     * @param anchorSeq 恢复 anchor 事件的 seq
     * @param boundary 本次窗口变化的三类时间边界（请求 / 命令回读 / 实测 root 变化）
     * @param targetBookId 本轮目标书籍 id；身份不足时不会签出保护结论
     */
    fun decide(
        events: List<Evt>,
        anchorSeq: Int,
        boundary: WindowChangeBoundary,
        anchorIndex: Int?,
        anchorOffsetPx: Int?,
        targetBookId: String,
        actualBoundsChanged: Boolean,
        markerStillVisible: Boolean,
        beforeRow: Row12?,
        afterRow: Row12?,
    ): Verdict {
        val relevant = events.filter { it.seq >= anchorSeq && it.hasValidTs() }
        val sessionToBook = sessionBindings(relevant)
        val anchorCoord = anchorCoordinates(anchorIndex, anchorOffsetPx)

        // ── 变化后边界：只认**实测**的 root 变化时刻，不认操作请求时刻 ──────────────
        val boundaryFrom = if (actualBoundsChanged) boundary.postChangeFromNanos else null
        val windowBoundaryKnown = boundaryFrom != null
        val rawPostChange = if (windowBoundaryKnown) {
            relevant.filter { it.event == OBSERVE && it.tsNanos >= boundaryFrom!! }
        } else {
            emptyList()
        }

        // 变化后**有效**观测：坐标存在且合法，且确实与本轮 anchor 不同。
        val parsed = rawPostChange.map { it to observeCoordinates(it) }
        val invalidCoordinateObserves = parsed.filter { it.second == null }.map { it.first }
        val validObserves = parsed.mapNotNull { (e, c) -> c?.let { e to it } }
        val changedObserves = if (anchorCoord == null) {
            emptyList()
        } else {
            validObserves.filter { it.second != anchorCoord }.map { it.first }
        }
        val remeasureObserved = actualBoundsChanged && windowBoundaryKnown &&
            anchorCoord != null && changedObserves.isNotEmpty()
        val firstChanged = changedObserves.minByOrNull { it.seq }
        val firstChangedSeq = firstChanged?.seq
        val firstChangedTs = firstChanged?.tsNanos

        /**
         * 因果关联：裁决事件必须**采样于可核实的变化后边界内**、**晚于该观测的采样时刻**，
         * 且 **seq 更晚**。三者缺一不可 —— "变更前的采样、变更后才落日志"的事件（ts 早于
         * 边界但 seq 更大）在这里被排除；单凭 seq 更大或日志晚到不足以主张因果。
         */
        val causalVerdicts = if (firstChangedSeq != null && firstChangedTs != null) {
            relevant.filter {
                isDebounceEvent(it.event) && it.tsNanos >= firstChangedTs && it.seq > firstChangedSeq
            }
        } else {
            emptyList()
        }

        // ── 身份：作用域裁剪不等于身份绑定，保护结论必须另行要求 TARGET_BOOK ────────
        val bindOf = { e: Evt -> identityBindingOf(e, targetBookId, sessionToBook) }
        val firstChangedBinding = firstChanged?.let(bindOf)
        val boundCausal = causalVerdicts.filter { bindOf(it) == IdentityBinding.TARGET_BOOK }
        val unboundChangedObserves = changedObserves.filter { bindOf(it) != IdentityBinding.TARGET_BOOK }

        val identitySufficient = remeasureObserved &&
            firstChangedBinding == IdentityBinding.TARGET_BOOK &&
            boundCausal.isNotEmpty()

        val guardVerdictEvents = boundCausal.filter { isSessionGuardVerdict(it.event) }
        val blocked = guardVerdictEvents.any { it.event == DEBOUNCE_BLOCKED_BY_SESSION_GUARD }
        val dispatched = guardVerdictEvents.any { it.event == DEBOUNCE_SAVE_DISPATCH }
        val guardVerdict = when {
            !remeasureObserved -> "no_remeasure_observed"
            causalVerdicts.isEmpty() -> "no_production_verdict"
            boundCausal.isEmpty() -> "verdict_identity_unbound"
            dispatched -> "guard_allowed_dispatch"
            blocked -> "guard_blocked_no_dispatch"
            else -> "no_production_verdict"
        }

        val rowColumns = if (beforeRow != null && afterRow != null) {
            changedColumns(beforeRow, afterRow)
        } else {
            listOf("__row_missing__")
        }
        val rowUnchanged = beforeRow != null && afterRow != null && rowColumns.isEmpty()

        val hasCausalVerdict = guardVerdictEvents.isNotEmpty()
        val stage = if (
            actualBoundsChanged && markerStillVisible && remeasureObserved &&
            hasCausalVerdict && identitySufficient
        ) {
            "observed"
        } else {
            "inconclusive"
        }

        val protectionClaim = if (
            guardVerdict == "guard_blocked_no_dispatch" && remeasureObserved && identitySufficient &&
            markerStillVisible && actualBoundsChanged && rowUnchanged
        ) {
            "guard_blocked_and_row_unchanged"
        } else {
            "not_claimed"
        }

        val identityMissingFields = ArrayList<String>()
        if (firstChanged != null && firstChangedBinding != IdentityBinding.TARGET_BOOK) {
            identityMissingFields.add("session_guard_observe.bid|sid")
        }
        if (causalVerdicts.isNotEmpty() && boundCausal.isEmpty()) {
            identityMissingFields.add("debounce_verdict.bid")
        }

        val reason = when {
            !actualBoundsChanged -> "实际 Compose root 尺寸未变化，未发生真实重测量"
            !windowBoundaryKnown -> "无法核实真实 Compose root 变化时刻（只有操作请求时间），请求后的日志不能当作变化后采样"
            anchorCoord == null -> "真实 anchor 的 index/offsetPx 缺失或非法，「坐标是否变化」无定义"
            changedObserves.isEmpty() -> "可核实的变化后边界内无有效坐标变化观测（无本次重测量的因果观测）"
            !markerStillVisible -> "窗口变化后同会话 fixture 正文 marker 不可见"
            causalVerdicts.isEmpty() -> "可核实的变化后边界内未观察到与本次观测因果关联的生产防抖裁决事件"
            boundCausal.isEmpty() -> "生产防抖裁决缺目标书籍身份（需 bid），不能归因到本书：" +
                REQUIRED_PRODUCTION_SEAM.joinToString("；")
            !identitySufficient -> "变化后的有效观测缺目标书籍身份（需 bid 或可回溯的 sid），不能归因到本书：" +
                REQUIRED_PRODUCTION_SEAM.joinToString("；")
            else -> null
        }

        return Verdict(
            windowBoundaryKnown = windowBoundaryKnown,
            postChangeFromNanos = boundaryFrom,
            remeasureObserved = remeasureObserved,
            firstChangedObserveSeq = firstChangedSeq,
            identitySufficient = identitySufficient,
            identityBinding = firstChangedBinding,
            identityMissingFields = identityMissingFields,
            rawPostChangeObserves = rawPostChange,
            invalidCoordinateObserves = invalidCoordinateObserves,
            changedObserves = changedObserves,
            unboundChangedObserves = unboundChangedObserves,
            causalVerdictEvents = boundCausal,
            guardVerdict = guardVerdict,
            stage = stage,
            rowChangedColumns = rowColumns,
            rowUnchangedAfterRemeasure = rowUnchanged,
            protectionClaim = protectionClaim,
            inconclusiveReason = reason,
        )
    }
}
