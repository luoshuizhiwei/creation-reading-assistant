package com.creationreadingassistant.data.repository

/** 索引失败的 WorkManager 处置，独立于 Android Worker 以便 JVM 覆盖。 */
internal enum class SearchIndexWorkerFailureAction {
    RETRY,
    FAIL,
}

private const val SEARCH_INDEX_MAX_ATTEMPTS = 3

/** [runAttemptCount] 从 0 开始，单次调度最多执行三次。 */
internal fun searchIndexWorkerFailureAction(runAttemptCount: Int): SearchIndexWorkerFailureAction =
    if (runAttemptCount + 1 < SEARCH_INDEX_MAX_ATTEMPTS) {
        SearchIndexWorkerFailureAction.RETRY
    } else {
        SearchIndexWorkerFailureAction.FAIL
    }

/**
 * 一次扫描的推进量 → [IndexSweepResult]。
 *
 * 纯函数，独立于 DAO / Worker 以便 JVM 覆盖。判定的关键是「本次是否至少推进了一本」：
 * 只有推进过才值得重新入队；一本都没推进说明**单本书本身**装不进一次作业窗口，
 * 再重试也只是空转（需章节级续建才能继续，见 [IndexSweepResult.STALLED]）。
 *
 * **章节级续建（§6.1）**：启用章节级断点后，一本书可能在一次窗口里只建完部分章节就
 * 让出，此时书级下标**没有前进**（仍停在该书），但章节确实推进了。这种情况必须判为
 * [IndexSweepResult.PROGRESSED] 续建，否则会被误判成 `STALLED`（单章超窗、重试无望）
 * 而提前放弃 —— 那正是章节级续建要消灭的失败模式。
 *
 * @param reached [SearchIndexRepository.processChunked] 实际推进到的下标。
 * @param from 本次起始下标。
 * @param until 书架快照末尾下标。
 * @param advancedWithinBook 停在 [reached] 这本书上时，书内章节是否有推进（章节级续建）。
 */
internal fun indexSweepResult(
    reached: Int,
    from: Int,
    until: Int,
    advancedWithinBook: Boolean = false,
): IndexSweepResult = when {
    reached >= until -> IndexSweepResult.COMPLETED
    reached > from -> IndexSweepResult.PROGRESSED
    advancedWithinBook -> IndexSweepResult.PROGRESSED
    else -> IndexSweepResult.STALLED
}

/** 一本书续建后的章节计数：[indexed] 已建章节数、[total] 全书章节数。 */
internal data class ChapterCounts(
    val indexed: Int,
    val total: Int,
)

/**
 * 续建后的章节计数（§6.8）：**[indexed] 是累加量，[total] 不是。**
 *
 * 这条不变量看着 trivial，真机上踩过一次很贵的坑：
 *
 * - `indexed`（建了多少章）要累加历史基线 —— 本轮只建断点之后的章节，必须把上一轮
 *   已建的数量加回来，否则覆盖率会倒退；
 * - `total`（全书有多少章）**绝不能**累加基线 —— 分母一旦相加就虚高近一倍（真机实测
 *   3023 vs 全书 1774），后果是**整本建完 `indexed/total` 仍 < 1，覆盖率被永久误判为
 *   PARTIAL**，UI 一直提示「索引不完整」，将来若接「按未 FULL 重扫」还会被反复重扫。
 *   更糟的是脏分母写进库后**不会自愈**（`maxOf(run, base)` 只会更大）。
 *
 * [total] 由调用方的分章结果直接给出（全书口径，与续建起点无关），这里原样透传，
 * 历史基线 [baseTotal] 只作为文档性入参保留、不参与运算。
 *
 * @param runIndexed 本轮实际建了的章节数。
 * @param runTotal 全书章节数（分章结果长度，与本轮从哪开始无关）。
 * @param baseIndexed 上一轮已落的已建章节数（续建基线），整本重建时为 0。
 */
internal fun resumeChapterCounts(
    runIndexed: Int,
    runTotal: Int,
    baseIndexed: Int,
    @Suppress("UNUSED_PARAMETER") baseTotal: Int = 0,
): ChapterCounts = ChapterCounts(
    indexed = (runIndexed + baseIndexed).coerceAtLeast(0),
    total = runTotal.coerceAtLeast(0),
)
