package com.creationreadingassistant.ui.screen.reader

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import com.creationreadingassistant.feature.reader.doc.DocBlock
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.lazy.LazyListState
import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.pager.ScrollUnitContentState

/**
 * 针对短末项 clamp 重进落位与保存的结构化运行态诊断打点（CRA-JOINT-31-FINAL-BOUND-ACCEPT-v1）。
 *
 * 保证单调自增序号、高精度时间戳，不记录正式书库，纯中性标识。
 */
internal object ReaderClampDiagnostics {
    private val seq = AtomicInteger(0)

    fun log(event: String, vararg pairs: Pair<String, Any?>) {
        val s = seq.incrementAndGet()
        val elapsed = SystemClock.elapsedRealtimeNanos()
        val details = pairs.joinToString(", ") { "${it.first}=${it.second}" }
        val line = "seq=$s ts=$elapsed event=$event $details"
        Log.i("CRA_CLAMP_DIAG", line)
    }
}

/**
 * TXT 滚动列表「初始恢复目标」的纯函数裁决（CRA-TXT-REENTRY-FIX-v1）。
 *
 * 设备缺陷（J1）：恢复 effect 在 LazyColumn 完成首次布局前执行
 * `scrollToItem(unitIndexForOffset(...))` —— 对尚未挂载 items 的列表该调用被静默
 * 忽略（或 clamp 到 0），随后 effect 又把 `pendingInitialPosition` 置 false，
 * 于是退出保存读到 (0,0) 并把数据库里的非零进度覆盖为 0；用户重进回到开头。
 *
 * 修复合同：目标可恢复必须同时满足
 * ① 存在已保存目标（offset>0 或 percent>0，仅 percent 时需 totalChars>0）；
 * ② 列表已就绪：`layoutReady`（LazyColumn 首次测量完成，`totalItemsCount>0`）
 *    且 `firstVisibleItemIndex` 命中 units 范围 —— 布局未就绪时**保持 pending=true**
 *    并返回 null，让 effect 随下一帧重组重试（`pendingInitialPositionState.value`
 *    是它的 key，保持 true 不重启会卡死；保持 true + layout 状态变化驱动重跑）。
 *
 * 返回 null = 本次不滚动、不清 pending（交由调用方重试）；返回偶对 = 目标
 * (itemIndex, targetOffset)，调用方执行滚动、锚定并清 pending。
 * 纯函数：不触碰 Compose 状态、DB 或任何副作用，便于 JVM 直接回归。
 */
internal data class PlainRestoreTarget(val itemIndex: Int, val targetOffset: Int)

internal fun plainRestoreTarget(
    savedPlainOffset: Int,
    savedPlainPercent: Float,
    totalChars: Int,
    units: List<ReadingUnit>,
    pagedAbsOffset: Int,
    layoutReady: Boolean,
): PlainRestoreTarget? {
    if (units.isEmpty()) return null
    val savedTarget = when {
        pagedAbsOffset >= 0 -> pagedAbsOffset
        savedPlainOffset > 0 -> savedPlainOffset.coerceAtMost(totalChars.coerceAtLeast(0))
        savedPlainPercent > 0f ->
            (savedPlainPercent.coerceIn(0f, 100f) / 100f * totalChars).toInt()
        else -> 0
    }
    // 没有任何已保存目标：本来就是书首打开，立即放行（0 位置、新书、损坏 locator
    // 全部收敛到 0），不得卡住 pending。
    if (savedTarget <= 0) return PlainRestoreTarget(itemIndex = 0, targetOffset = 0)
    // 有非零目标但列表还没完成首次测量：调用方（生产 effect）挂起等待布局就绪后再取目标，
    // 期间 pending 保持 true 持续拦截保存。
    if (!layoutReady) return null
    val index = unitIndexForOffset(units, savedTarget).coerceIn(0, units.lastIndex)
    return PlainRestoreTarget(itemIndex = index, targetOffset = savedTarget)
}

/**
 * 「恢复已实际落到目标单元」的纯函数确认（CRA-TXT-REENTRY-FIX-v3）。
 *
 * v2 只接受「首项==目标」：书尾短项受 LazyColumn 底部 clamp 影响时，目标可见但
 * 首项可能是上一项——合法落位被永久拒绝（Codex P1）。v3 明确定义合法可见范围：
 *  - 正常落位：首个可见项 == 目标单元；
 *  - 书尾 clamp 落位：目标**就是最后一个阅读单元**（[isLastUnit]），且首项 < 目标、
 *    末个可见项 == 目标（目标在屏内，只是被 clamp 推到视口下部）。
 *    中间项被末可见项贴边不算落位（v4 反例：40 单元 first=36/lastVisible=37/
 *    target=37 必须拒绝——那只是滚到半途）。
 * 目标完全不在可见范围内仍判未落位。
 *
 * [firstVisibleItemIndex]/[lastVisibleItemIndex] 取自滚动后的真实列表状态
 * （layoutInfo.visibleItemsInfo 首末项）；[unitsEmpty] 表达 items 未挂载。
 * [contentReadiness] 表达「这份布局是否由真实正文测得」（见
 * [ScrollRestoreContentReadiness]）：clamp 分支要求 READY，占位/失败帧不得放行。
 * 纯函数，供 JVM 动态回归。
 */
internal fun plainRestoreSettled(
    firstVisibleItemIndex: Int,
    lastVisibleItemIndex: Int,
    targetItemIndex: Int,
    unitsEmpty: Boolean,
    isLastUnit: Boolean,
    contentReadiness: ScrollRestoreContentReadiness = ScrollRestoreContentReadiness.READY,
    canScrollForward: Boolean = false,
    firstVisibleItemScrollOffset: Int = -1,
): Boolean {
    if (unitsEmpty) return false
    if (firstVisibleItemIndex == targetItemIndex) {
        if (isLastUnit && contentReadiness != ScrollRestoreContentReadiness.READY) return false
        return true
    }
    // 书尾 clamp（CRA-TXT-REENTRY-FIX-v4）：只允许目标**就是最后一个阅读单元**。
    // 中间项（target < units.lastIndex）即使末可见项==目标（目标恰好贴在视口底边）
    // 也不算落位——那只是滚到半途，pending 必须保持等待（Codex P1 反例：
    // 40 单元 first=36/lastVisible=37/target=37 曾被 v3 误判为已落位）。
    //
    // CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2：clamp 分支是**由几何反推**「列表已贴底」，
    // 而占位（Loading）帧的几何与正文无关：占位项一屏即可容纳，于是首项=0、末可见项=末单元
    // 也会成立，被误判为落位（设备实测 j1_clamp_20）。因此该分支必须以**正文已就绪**为前提：
    // 未就绪（PENDING）或加载失败（FAILED）都不放行，等真实正文布局到位后重新判定。
    //
    // 贴底物理硬约束（CRA-JOINT-31-FINAL-BOUND-ACCEPT-v1）：
    // 若列表仍能向前滚动（canScrollForward == true），说明尚未滚到底部（如首帧停在 (0, 0)
    // 且单元 0 撑大后末单元未滚入、或首帧占位尚未真正滚动贴底），绝不得判定为 clamp 落位。
    // 若目标是末单元（targetItemIndex > 0），但列表停在首项第 0 像素（firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0），
    // 列表处于初始未滚动帧或占位测量帧，绝对不得将书首初始假状态误判为书尾 clamp 落位。
    if (contentReadiness != ScrollRestoreContentReadiness.READY) return false
    if (canScrollForward) return false
    if (targetItemIndex > 0 && firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) {
        return false
    }
    return isLastUnit &&
        targetItemIndex > firstVisibleItemIndex &&
        lastVisibleItemIndex == targetItemIndex
}

internal fun canPersistPlainScrollPosition(plainScrollPositionRestored: Boolean): Boolean =
    plainScrollPositionRestored

/**
 * 恢复落位判定所依赖的**正文就绪**状态（CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2）。
 *
 * 背景（隔离 MuMu 实测，j1_clamp_20）：短末项 clamp 书重进时，滚动单元仍在异步加载，
 * LazyColumn 用 [com.creationreadingassistant.ui.theme.ShimmerBlock] 占位项测量。占位高度
 * 与正文无关，一屏即可容纳全部占位项，于是 `visibleItemsInfo` 报出「末项就在屏内」，
 * [plainRestoreSettled] 的 clamp 分支据此判为已落位 —— 这是**假落位**：随后真实正文加载、
 * 列表重新测量并把位置拉回书首，500ms 防抖保存按视口顶部把 offset 写成 0，覆盖了原进度。
 *
 * 因此 clamp 落位必须建立在**真实正文布局**上：占位（Loading）不得冒充已就绪，
 * 加载失败（Failed）同样不得冒充；只有相关单元全部 Loaded 时布局才可信。
 */
internal enum class ScrollRestoreContentReadiness {
    /** 仍有可见单元在加载（或布局尚未产生可见项）：布局由占位高度决定，不可信。 */
    PENDING,

    /** 可见单元全部加载完成：当前布局由真实正文测量得出，clamp 落位可信。 */
    READY,

    /** 至少一个可见单元加载失败：不得当作已就绪（无超时放行，等待重试或后续状态变化）。 */
    FAILED,
}

/**
 * 由「当前可见单元集合 + 其加载状态」裁定正文就绪度（纯函数，供 JVM 直接回归）。
 *
 * - 任一可见单元 Failed → [ScrollRestoreContentReadiness.FAILED]（失败不冒充已就绪）；
 * - 全部可见单元 Loaded → [ScrollRestoreContentReadiness.READY]；
 * - 其余（含可见项为空、任一仍在 Loading）→ [ScrollRestoreContentReadiness.PENDING]。
 */
internal fun scrollRestoreContentReadiness(
    visibleUnitIndices: List<Int>,
    stateOf: (Int) -> ScrollUnitContentState,
): ScrollRestoreContentReadiness {
    if (visibleUnitIndices.isEmpty()) return ScrollRestoreContentReadiness.PENDING
    var allLoaded = true
    for (unitIndex in visibleUnitIndices) {
        when (stateOf(unitIndex)) {
            is ScrollUnitContentState.Loaded -> Unit
            is ScrollUnitContentState.Failed -> return ScrollRestoreContentReadiness.FAILED
            ScrollUnitContentState.Loading -> allLoaded = false
        }
    }
    return if (allLoaded) {
        ScrollRestoreContentReadiness.READY
    } else {
        ScrollRestoreContentReadiness.PENDING
    }
}

/**
 * 恢复循环观察到的「布局 + 正文就绪」身份快照。
 *
 * 它是等待流的比较键：布局三元组相同但正文就绪度变化（Loading → Loaded）时也必须被视为
 * **一次新进展**，否则循环会在占位帧之后永久挂起、pending 永不解除。判定本身仍走
 * [plainRestoreSettled] / `settleSnapshot`，本类型只承载「有没有新进展」。
 */
internal data class PlainRestoreLayoutSnapshot(
    val totalItemsCount: Int,
    val firstVisibleItemIndex: Int,
    val lastVisibleItemIndex: Int,
    val contentReadiness: ScrollRestoreContentReadiness,
    val canScrollForward: Boolean = false,
)

/** [PlainRestoreLayoutSnapshot] 的构造入口（默认按已就绪，供不观察正文状态的调用方使用）。 */
internal fun plainRestoreLayoutSnapshot(
    totalItemsCount: Int,
    firstVisibleItemIndex: Int,
    lastVisibleItemIndex: Int,
    contentReadiness: ScrollRestoreContentReadiness = ScrollRestoreContentReadiness.READY,
    canScrollForward: Boolean = false,
): PlainRestoreLayoutSnapshot = PlainRestoreLayoutSnapshot(
    totalItemsCount = totalItemsCount,
    firstVisibleItemIndex = firstVisibleItemIndex,
    lastVisibleItemIndex = lastVisibleItemIndex,
    contentReadiness = contentReadiness,
    canScrollForward = canScrollForward,
)

/**
 * CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v1：销毁瞬间的重复落库是否应执行。
 *
 * 用户退出阅读器时，生命周期 ON_PAUSE/ON_STOP 已在**权威时机**（列表仍是可见时的真实布局）
 * 落过一次位置；随后组合销毁触发的 onDispose 再落一次时，阅读列表可能已按**过渡中的更窄视口**
 * 重新测量，同一像素位移映射到**不同字符偏移**，于是把正确位置覆盖成错误值（设备实测 5269→4821）。
 *
 * 因此：本次组合**已经由生命周期事件落过库**时，onDispose 的重复落库视为冗余并跳过；
 * 只有**未经生命周期落库**的销毁（同 Activity 内路由离开阅读器、只触发 composable dispose）
 * 才仍由 onDispose 落库——保持该路径的既有保存能力不丢失。
 * 纯函数，供 JVM 直接回归。
 */
internal fun shouldPersistOnDispose(persistedByLifecycle: Boolean): Boolean = !persistedByLifecycle

/**
 * 阅读器会话的「生命周期是否已落过库」状态机（CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2）。
 *
 * [ReaderPlatformEffects] 的生命周期观察器把 ON_PAUSE/ON_STOP 记为**权威落库**、ON_RESUME
 * 复位、组合销毁时据此决定是否再落一次。状态机抽成纯 Kotlin 类，使这三条真实路径可以在
 * JVM 上按事件顺序驱动（而不是只断言一个布尔函数），并钉住「保存标记绝不永久抑制后续保存」：
 * 每次 ON_RESUME 都复位，因此「暂停 → 回到前台 → 同 Activity 内路由离开阅读器」仍会经
 * onDispose 落库。
 */
internal class ReaderLifecyclePersistGate {
    private var persistedByLifecycle = false

    /** 会话（重新）开始，或回到前台：尚无生命周期落库，后续销毁仍需落库。 */
    fun onResume() {
        persistedByLifecycle = false
    }

    /** ON_PAUSE / ON_STOP：已在权威时机落库。 */
    fun onPauseOrStop() {
        persistedByLifecycle = true
    }

    /** CRA-TASK195-r1: 暂停/停止时是否应持久化（false = 权威退出/临时跳转已排空持久化，避免切后台重复覆写时间戳）。 */
    fun shouldPersistOnPauseOrStop(): Boolean = !persistedByLifecycle

    /** 组合销毁时是否仍需落库（false = 本次已由生命周期落过，跳过重复覆盖）。 */
    fun shouldPersistOnDispose(): Boolean = shouldPersistOnDispose(persistedByLifecycle)
}

internal fun canPersistPagedPosition(initialPositionPending: Boolean): Boolean =
    !initialPositionPending

internal fun canPersistLegacyScrollPosition(initialPositionPending: Boolean): Boolean =
    !initialPositionPending

/** Read the current LazyList state at save time rather than a stale composition-time offset. */
internal fun currentPlainListOffset(
    state: LazyListState,
    units: List<ReadingUnit>,
    fallbackOffset: Int,
): Int {
    val index = state.firstVisibleItemIndex
    val unit = units.getOrNull(index) ?: return fallbackOffset.coerceAtLeast(0)
    val visibleItem = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return unit.charStart
    if (visibleItem.size <= 0) return unit.charStart
    val fraction = (-visibleItem.offset).coerceIn(0, visibleItem.size).toFloat() / visibleItem.size
    return (unit.charStart + unit.charCount * fraction).toInt()
        .coerceIn(unit.charStart, unit.charStart + unit.charCount)
}

/**
 * 滚动 TXT 的**持久位置语义**映射（CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2）。
 *
 * 视口物理位置（首可见项 + 像素偏移）不等于持久位置语义：
 * - 列表已无法继续前滚（读到书末 / 短书末项 clamp 在屏内）时，用户停在**正文末尾**，
 *   持久位置取全书字符总数（既有「读到末尾」约定），而不是视口顶部那一项的字符偏移；
 * - 否则按视口顶部项的已消费像素比例映射回字符偏移（[currentPlainListOffset]）。
 *
 * 短末项 clamp 正是靠这一条区分「视口物理位置」与「持久位置」：末项贴底时
 * `firstVisibleItemIndex` 可能仍是前一单元，若直接用视口偏移落库就会把书末位置写成
 * 单元中段的错误偏移。纯函数，供 JVM 直接回归。
 */
internal fun plainScrollPersistOffset(
    canScrollForward: Boolean,
    listState: LazyListState,
    units: List<ReadingUnit>,
    totalChars: Int,
): Int = if (!canScrollForward) {
    totalChars
} else {
    currentPlainListOffset(listState, units, fallbackOffset = 0)
}

/**
 * LazyColumn 阅读位置的统一估算 seam。
 *
 * [itemStartOffsets] 与 [itemLengths] 必须处于同一个全书字符空间；图片或没有文本
 * 的渲染项长度传 0。这里只做坐标推导，不触碰 Compose 状态、数据库或滚动副作用。
 */
internal fun visibleScrollOffset(
    firstVisibleItemIndex: Int,
    firstVisibleItemOffset: Int,
    firstVisibleItemSize: Int,
    itemStartOffsets: List<Int>,
    itemLengths: List<Int>,
    endReached: Boolean,
    endOffset: Int,
): Int {
    if (endReached && endOffset >= 0) return endOffset
    val index = firstVisibleItemIndex.coerceAtLeast(0)
    val start = itemStartOffsets.getOrNull(index) ?: return 0
    val length = itemLengths.getOrNull(index)?.coerceAtLeast(0) ?: 0
    if (length == 0 || firstVisibleItemSize <= 0) return start
    val consumedPx = (-firstVisibleItemOffset).coerceIn(0, firstVisibleItemSize)
    val fraction = consumedPx.toFloat() / firstVisibleItemSize
    return (start + length * fraction).toInt().coerceIn(start, start + length)
}

/**
 * EPUB 章节的最后一个可定位文本坐标。
 *
 * [blockGlobalOffsets] 会在相邻文本块之间保留换行符，因此不能以各块纯文本长度简单求和；
 * 否则章节末尾的保存坐标会随着段落数而逐渐向前偏移。
 */
internal fun epubChapterEndOffset(
    blocks: List<DocBlock>,
    blockGlobalOffsets: List<Int>,
    chapterBase: Int,
): Int {
    var endOffset = chapterBase
    blocks.forEachIndexed { index, block ->
        val text = (block as? DocBlock.Text)?.text ?: return@forEachIndexed
        val start = blockGlobalOffsets.getOrNull(index) ?: return@forEachIndexed
        if (start >= chapterBase) endOffset = maxOf(endOffset, start + text.length)
    }
    return endOffset
}

internal fun scrollProgressPercent(
    offset: Int,
    totalChars: Int,
    endReached: Boolean,
): Float {
    if (totalChars <= 0) return 0f
    if (endReached) return 100f
    return (offset * 100f / totalChars).coerceIn(0f, 100f)
}

internal fun chapterProgressPercent(
    offset: Int,
    chapterStart: Int,
    chapterLength: Int,
    endReached: Boolean,
): Float {
    if (chapterLength <= 0) return if (endReached) 100f else 0f
    if (endReached) return 100f
    return ((offset - chapterStart) * 100f / chapterLength).coerceIn(0f, 100f)
}

internal data class ReaderChapterNavigation(
    val currentIndex: Int,
    val chapterCount: Int,
) {
    val canGoPrevious: Boolean get() = currentIndex > 0
    val canGoNext: Boolean get() = currentIndex >= 0 && currentIndex < chapterCount - 1
}

internal fun readerChapterNavigation(currentIndex: Int, chapterCount: Int): ReaderChapterNavigation =
    ReaderChapterNavigation(
        currentIndex = currentIndex.coerceIn(0, (chapterCount - 1).coerceAtLeast(0)),
        chapterCount = chapterCount.coerceAtLeast(0),
    )

/**
 * TXT 滚动恢复的生产等待/滚动/确认控制流（CRA-TXT-REENTRY-FIX-v3）。
 *
 * v2 的循环在 effect 体内每轮**重新订阅** snapshotFlow：布局已就绪时
 * `filter(nonnull).first()` 立即返回同一目标，落位确认失败就会连续重复
 * scrollToItem，没有任何「等待新布局进展」的挂起（Codex P1）。v3 把整条控制流
 * 收进本生产函数，effect 与测试调用**同一实现**，并修复重试语义：
 *
 * - 每轮滚动后若未落位，挂起等待布局快照**发生变化**（[PlainRestoreLayoutSnapshot]：
 *   totalItemsCount / first / last / 正文就绪度）再重试——同状态不会重复滚动，无忙循环；
 *   正文就绪度也在比较键内（CRA-JOINT-31-MERGE-FREEZE-ACCEPT-v2），因为占位帧与真实
 *   正文帧的布局三元组可能完全相同，若只比较布局就会在占位帧之后永久挂起；
 * - **观察/等待顺序修正（CRA-JOINT-31-ACCEPT-CLOSURE-v3）**：滚动请求的布局效果在
 *   下一帧测量后才出现，`scrollTo` 返回后**立即采样**会读到滚动前的旧布局；旧实现
 *   在等到一次真实布局更新后**直接回到循环顶部再次滚动**，没有在新布局上**重新判定
 *   落位**——于是「本次滚动已经真正落到目标」的合法更新被当成「需要重滚的变化」而
 *   未被消费，既产生一次多余滚动，也可能在布局不再变化时永久挂起、pending 永不解除。
 *   现在每次等到真实布局更新后，**先用同一 `settleSnapshot` 在新布局上重新判定落位**，
 *   已落位即返回；未落位才继续滚动。判定标准仍为 [plainRestoreSettled]（未实质落位
 *   不放行、同状态不重复滚动、不设固定超时）。
 * - 落位判定经 [plainRestoreSettled]（含书尾 clamp 合法范围，且 clamp 分支要求正文已就绪）；
 * - 协程取消（书籍/列表/模式身份随 effect key 失效）即整体退出，
 *   挂起点被取消后不再执行任何滚动；
 * - [settleSnapshot] / [layoutChanges] / [scrollTo] / [contentReadiness] 为纯接缝：
 *   生产传真实 LazyListState 与 ScrollUnitContentLoader 派生值，测试传受控序列——
 *   两侧跑的是同一条控制流。
 *
 * @return 是否已确认落位（true = 调用方可以锚定并释放 pending）。
 */
internal suspend fun runPlainRestoreLoop(
    savedPlainOffset: Int,
    savedPlainPercent: Float,
    totalChars: Int,
    units: List<ReadingUnit>,
    pagedAbsOffset: Int,
    scrollTo: suspend (Int) -> Unit,
    settleSnapshot: suspend () -> Triple<Int, Int, Boolean>, // (first, last, unitsEmpty)
    layoutChanges: kotlinx.coroutines.flow.Flow<PlainRestoreLayoutSnapshot>, // 布局 + 正文就绪度
    /**
     * 当前正文就绪度。默认 READY 供不观察加载状态的调用方使用；生产必须接入真实
     * [com.creationreadingassistant.feature.reader.pager.ScrollUnitContentLoader] 状态。
     */
    contentReadiness: suspend () -> ScrollRestoreContentReadiness = {
        ScrollRestoreContentReadiness.READY
    },
    /**
     * 当前列表是否仍可继续向前滚动。用于严密防护「末项 clamp 假落位」：未真正贴底不得放行。
     */
    canScrollForward: suspend () -> Boolean = { false },
    firstVisibleItemScrollOffset: suspend () -> Int = { -1 },
): Boolean {
    val target = plainRestoreTarget(
        savedPlainOffset = savedPlainOffset,
        savedPlainPercent = savedPlainPercent,
        totalChars = totalChars,
        units = units,
        pagedAbsOffset = pagedAbsOffset,
        layoutReady = true,
    )?.takeIf { it.targetOffset > 0 }

    ReaderClampDiagnostics.log(
        "restore_loop_enter",
        "savedPlainOffset" to savedPlainOffset,
        "savedPlainPercent" to savedPlainPercent,
        "totalChars" to totalChars,
        "unitsCount" to units.size,
        "pagedAbsOffset" to pagedAbsOffset,
        "targetIndex" to target?.itemIndex,
        "targetOffset" to target?.targetOffset,
    )

    if (target == null) return true // 0 目标：书首直通，无恢复义务

    // 在给定布局上判定是否已落位（判定标准不变，与 [plainRestoreSettled] 同源）。
    suspend fun landed(): Boolean {
        val (f, l, empty) = settleSnapshot()
        val readiness = contentReadiness()
        val canFwd = canScrollForward()
        val fviOffset = firstVisibleItemScrollOffset()
        val settled = plainRestoreSettled(
            firstVisibleItemIndex = f,
            lastVisibleItemIndex = l,
            targetItemIndex = target.itemIndex,
            unitsEmpty = empty,
            isLastUnit = target.itemIndex == units.lastIndex,
            contentReadiness = readiness,
            canScrollForward = canFwd,
            firstVisibleItemScrollOffset = fviOffset,
        )
        val unitStart = units.getOrNull(target.itemIndex)?.charStart ?: 0
        val hasIntraUnitTarget = target.targetOffset > unitStart
        val finalVerdict = if (!settled) {
            false
        } else if (hasIntraUnitTarget && fviOffset == 0 && canFwd) {
            false
        } else {
            true
        }
        ReaderClampDiagnostics.log(
            "restore_landed_eval",
            "first" to f,
            "last" to l,
            "fviOffset" to fviOffset,
            "empty" to empty,
            "target" to target.itemIndex,
            "isLastUnit" to (target.itemIndex == units.lastIndex),
            "readiness" to readiness,
            "canScrollForward" to canFwd,
            "hasIntraUnitTarget" to hasIntraUnitTarget,
            "verdict" to finalVerdict,
        )
        return finalVerdict
    }

    // 滚动前的布局快照（用于「等待真正变化」的过滤基准）。
    var settleBefore = layoutChanges.first()
    while (true) {
        // 布局未就绪：挂起等待一次真正不同的快照。
        if (settleBefore.totalItemsCount <= 0) {
            ReaderClampDiagnostics.log("restore_layout_empty_wait")
            settleBefore = layoutChanges.first { it != settleBefore }
            continue
        }
        ReaderClampDiagnostics.log("restore_scroll_request", "targetIndex" to target.itemIndex)
        scrollTo(target.itemIndex)
        if (landed()) return true
        // 让出调度让 Compose/测试调度器推进排版后再采一次，避免采样微时差。
        kotlinx.coroutines.yield()
        if (landed()) return true
        // 未落位：挂起等待一次真正不同的布局快照（StateFlow 会立即重放当前值，
        // 必须过滤掉与滚动前相同的快照；同状态 → 挂起，无忙循环；取消即整体退出）。
        val before = settleBefore
        settleBefore = layoutChanges.first { it != before }
        ReaderClampDiagnostics.log(
            "restore_layout_changed",
            "totalItems" to settleBefore.totalItemsCount,
            "first" to settleBefore.firstVisibleItemIndex,
            "last" to settleBefore.lastVisibleItemIndex,
            "readiness" to settleBefore.contentReadiness,
            "canForward" to settleBefore.canScrollForward,
        )
        if (landed()) return true
    }
}

/**
 * 读端会话位置保护守卫（CRA-READER-SESSION-PROGRESS-GUARD-FIX-v1）。
 *
 * 背景：短末项 clamp 或从已有有效进度重进阅读器时，若用户未发生任何滚动或翻页直接退出（无操作退出），
 * 退出落库不得因视口测量瞬态、像素比例取整漂移或初始占位帧而将已持久化的合法进度（如 S2=1775）覆盖为 0 或偏移漂移值。
 *
 * 会话级实例（不再是进程级 bookId→Map 全局缓存）：每次进入阅读器组合持有自己的守卫，
 * 离开/切书由 [disarm] 释放自身状态，互不串扰——旧会话的取消或释放绝不会清掉新会话的状态，
 * 也不再靠永久 bookId 全局条目维持合同。临时查阅会话（同书 locator）根本不参与本守卫，
 * 因此不会污染普通阅读进度，也不会绕过临时模式自身的禁止保存门控。
 *
 * 恢复由 arm/anchor 绑定权威位置。坐标变化只作观测，不产生用户意图。
 * 真实拖动开始时记录当时坐标，位移与本次输入起点比较；释放同步读取末坐标，
 * 真实移动后继续保留标记供惯性与快速退出保存。明确阅读导航由 recordNavigation 标记。
 * 没有主动移动时，即使视口重测量改变了列表坐标，退出仍使用权威初始位置。
 * disarm/重新arm 清理会话输入状态；临时阅读仍不参与。
 */
internal class ReaderSessionPositionGuard {
    private var initialOffset: Int = 0
    private var initialPercent: Float = 0f
    private var anchorIndex: Int? = null
    private var anchorOffsetPx: Int? = null
    private var userMoved: Boolean = false
    private var userScrollActive: Boolean = false
    private var inputIndex: Int = 0
    private var inputOffsetPx: Int = 0
    private var armed: Boolean = false

    @Synchronized
    fun arm(initialOffset: Int, initialPercent: Float) {
        this.initialOffset = initialOffset
        this.initialPercent = initialPercent
        anchorIndex = null
        anchorOffsetPx = null
        userMoved = false
        userScrollActive = false
        armed = true
    }

    @Synchronized
    fun anchor(index: Int, offsetPx: Int) {
        if (anchorIndex == null) {
            anchorIndex = index
            anchorOffsetPx = offsetPx
        }
    }

    @Synchronized
    fun observe(index: Int, offsetPx: Int) {
        if (userScrollActive && (index != inputIndex || offsetPx != inputOffsetPx)) {
            userMoved = true
        }
    }

    @Synchronized
    fun beginUserScroll(index: Int, offsetPx: Int) {
        inputIndex = index
        inputOffsetPx = offsetPx
        userScrollActive = armed && anchorIndex != null
    }

    @Synchronized
    fun endUserScroll(index: Int, offsetPx: Int) {
        observe(index, offsetPx)
        userScrollActive = false
    }

    @Synchronized
    fun recordNavigation() {
        if (armed && anchorIndex != null) userMoved = true
    }

    @Synchronized
    fun hasUserMoved(): Boolean = userMoved

    @Synchronized
    fun isArmed(): Boolean = armed

    @Synchronized
    fun resolveNoMovePosition(currentIndex: Int, currentOffsetPx: Int): Pair<Int, Float>? {
        observe(currentIndex, currentOffsetPx)
        if (!armed || userMoved || initialOffset <= 0 || anchorIndex == null) return null
        return initialOffset to initialPercent
    }

    @Synchronized
    fun disarm() {
        armed = false
        initialOffset = 0
        initialPercent = 0f
        anchorIndex = null
        anchorOffsetPx = null
        userMoved = false
        userScrollActive = false
    }
}

/**
 * CRA-READER-SESSION-PROGRESS-GUARD-FIX-v1：会话位置守卫是否参与本次阅读会话的纯判定 seam。
 *
 * 临时查阅（同书 locator）会话**不参与**守卫：既不武装、不观测、不锚定，退出落库也不做会话裁决。
 * 这样临时查阅的位置变化永远不会污染普通阅读的会话状态，也不会绕过临时模式自身的禁止保存门控
 *（[canPersistNormalReadingProgress] 与 ReaderScreen.persistOnLeave 的 temporaryInspection 早退）。
 * 抽成纯函数以便 JVM 直接回归「临时不参与」这一生产接线合同。
 */
internal fun sessionPositionGuardParticipates(temporaryInspection: Boolean): Boolean = !temporaryInspection

/**
 * 197-r1：分页会话「无位移退出不重复落库」的纯判定（与滚动 TXT 的
 * [ReaderSessionPositionGuard.resolveNoMovePosition] 同一对称合同面）。
 *
 * 会话锚 = 恢复落位后（pendingInitialPosition 解除）引擎上报的首个有效分页页首——该位置
 * 在恢复时已随持久行落库（S0 采样），因此锚与当前页首一致意味着本次退出**没有新进度可写**：
 * 跳过退出落库，保持 S0 采样在临时转换边界（普通 S0 → 临时 T1）的 12 列冻结顺序。
 * 翻页 / 跳章后 current != anchor → 照常落库，用户真实移动后的最新合法进度不丢失。
 * 任一侧无效（<0，含锚尚未建立）一律不跳（fail-closed：缺锚照常落库）。
 *
 * 生产调用点：ReaderScreen.persistOnLeave 的分页分支（默认 readerMode="paged" +
 * pagerEngineMode="on"）。滚动 TXT 分支继续走 [ReaderSessionPositionGuard.resolveNoMovePosition]。
 */
internal fun skipPagedExitPersistOnUnmovedSession(
    anchorAbsOffset: Int,
    currentAbsOffset: Int,
): Boolean = anchorAbsOffset >= 0 && currentAbsOffset >= 0 && currentAbsOffset == anchorAbsOffset

