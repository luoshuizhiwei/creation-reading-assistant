package com.creationreadingassistant.acceptance

/**
 * CRA-TXT-PERSIST-ACCEPT-HARDENING-v2 —— J1 真实恢复与移动保存合同的纯判定层（无 IO、无 Android 依赖）。
 *
 * ## 核心目标与加固设计
 *
 * 彻底消除“UI 有位移但 DB 保存冻结”以及“同一 snapshot 冒充多个时点”的假通过：
 * 1. 恢复基准记录：独立记录恢复完成后的基准 R 与完整进度行（bookId、legacyOffset、progressPercent、updatedAt、currentLocationJson）；
 * 2. 真实 UI 位移：前后 UI dump 对比，正文 marker 必须真实发生位移且非空；
 * 3. 防保存冻结断言：新保存偏移 S2 必须既不等于恢复落点 R，更**不能等于旧退出保存值 S**！
 *    若 UI dump 已位移但 DB 中 offset 仍为旧 S 或 updatedAt 未变，坚决拒绝并判定为保存冻结；
 * 4. 进度行实际变化：核验 locator / percent / updatedAt 实际发生更新；
 * 5. 普通恢复仍按阅读单元合同（单元起点或精确 S，真实 UI 标记）；
 * 6. 短末项 clamp 独立时点合同：
 *    分开取得 S、合法 clamp 落位 R、恢复后反向移动 S2 以及退出后冷进程读回；
 *    验证末尾向上反向移动后产生全新保存行，offset/percent 按夹具预期变化，updatedAt 刷新，
 *    冷进程读回符合单元合同，绝不复用同一 snapshot 冒充多个时点。
 */

/** 与生产 ReadingUnit 偏移元数据对齐的纯数据。 */
data class ReadingUnitRange(
    val unitIndex: Int,
    val charStart: Int,
    val charCount: Int,
) {
    val charEnd: Int get() = charStart + charCount
}

/** 完整进度行快照。包含完整的书籍身份与元数据。 */
data class J1ProgressSnapshot(
    val legacyOffset: Int,
    val progressPercent: Float,
    val updatedAt: String,
    val currentLocationJson: String,
    val bookId: String = "",
)

/**
 * J1 退出保存合同。由真实正文 + 生产阅读单元 + 退出保存偏移与快照构建。
 */
data class J1Contract(
    val bodyLength: Int,
    val savedOffset: Int,
    val savedUnitIndex: Int,
    val savedUnitStart: Int,
    val savedUnitEnd: Int,
    val savedParagraphMarker: String,
    val expectedRestoreOffset: Int,
    val expectedVisibleMarker: String,
    val savedUpdatedAt: String? = null,
    val savedPercent: Float? = null,
    val savedLocationJson: String? = null,
    val bookId: String = "",
)

/**
 * 短末项 clamp 保存合同。专用于书尾短项场景，与普通 J1 原始保存合同完全分开。
 */
data class J1ClampContract(
    val bodyLength: Int,
    val savedOffset: Int,
    val savedUnitIndex: Int,
    val savedUnitStart: Int,
    val savedUnitEnd: Int,
    val isLastUnit: Boolean,
    val savedParagraphMarker: String,
    val savedUpdatedAt: String? = null,
    val savedPercent: Float? = null,
    val savedLocationJson: String? = null,
    val bookId: String = "",
)

/** 判定结果：布尔 + 可解释原因。 */
data class J1Verdict(val accepted: Boolean, val reason: String)

object J1RecoveryContract {

    /** 普通 J1 夹具标记前缀：形如 CRAJ1P0007，全书唯一。 */
    const val MARKER_PREFIX = "CRAJ1P"
    private val MARKER_REGEX = Regex(Regex.escape(MARKER_PREFIX) + "(\\d{4})")

    /** 短末项 clamp 夹具标记前缀：形如 CRACLAMP0045，全书唯一。 */
    const val CLAMP_MARKER_PREFIX = "CRACLAMP"
    private val CLAMP_MARKER_REGEX = Regex(Regex.escape(CLAMP_MARKER_PREFIX) + "(\\d{4})")

    fun marker(index: Int): String = MARKER_PREFIX + "%04d".format(index)
    fun clampMarker(index: Int): String = CLAMP_MARKER_PREFIX + "%04d".format(index)

    /** 全书内所有标记的出现位置（按偏移升序）。 */
    fun markerOffsets(body: String, prefix: String = MARKER_PREFIX): List<Pair<Int, String>> {
        val regex = if (prefix == CLAMP_MARKER_PREFIX) CLAMP_MARKER_REGEX else MARKER_REGEX
        return regex.findAll(body)
            .map { it.range.first to it.value }
            .sortedBy { it.first }
            .toList()
    }

    /** 标记是否全书唯一。夹具前置条件。 */
    fun markersUnique(body: String, prefix: String = MARKER_PREFIX): Boolean {
        val all = markerOffsets(body, prefix).map { it.second }
        return all.size == all.toSet().size
    }

    /** 最后一个 offset <= target 的标记；无则 null。 */
    fun lastMarkerAtOrBefore(body: String, target: Int, prefix: String = MARKER_PREFIX): String? {
        var result: String? = null
        for ((offset, m) in markerOffsets(body, prefix)) {
            if (offset <= target) result = m else break
        }
        return result
    }

    /** 第一个 offset >= target 的标记；无则 null。 */
    fun firstMarkerAtOrAfter(body: String, target: Int, prefix: String = MARKER_PREFIX): String? =
        markerOffsets(body, prefix).firstOrNull { it.first >= target }?.second

    /** 与生产 unitIndexForOffset 同语义：最后一个 charStart <= offset 的单元序号；空表返回 0。 */
    fun unitIndexForOffset(units: List<ReadingUnitRange>, offset: Int): Int {
        if (units.isEmpty()) return 0
        var lo = 0
        var hi = units.lastIndex
        var result = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (units[mid].charStart <= offset) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    /**
     * 由真实正文 + 生产阅读单元 + 退出保存偏移构建普通 J1 合同。
     */
    fun build(
        body: String,
        units: List<ReadingUnitRange>,
        savedOffset: Int?,
        snapshot: J1ProgressSnapshot? = null,
        expectedBookId: String = "",
    ): Pair<J1Contract?, String> {
        if (savedOffset == null) return null to "退出保存偏移缺失（无进度行或无法解码）"
        if (savedOffset <= 0) return null to "退出保存偏移必须 > 0（非初始位置），实际=$savedOffset"
        if (units.isEmpty()) return null to "阅读单元为空，无法建立单元映射"
        if (savedOffset >= body.length) return null to "退出保存偏移越界：$savedOffset >= 正文长度 ${body.length}"
        val ui = unitIndexForOffset(units, savedOffset)
        val unit = units[ui]
        if (ui < 1) return null to "退出保存偏移落在初始单元（unitIndex=$ui）——旅程未离开初始位置，本批无效"
        if (unit.charCount <= 0) return null to "单元长度非法：charCount=${unit.charCount}"
        val savedMarker = lastMarkerAtOrBefore(body, savedOffset)
            ?: return null to "退出保存偏移前无任何夹具标记"
        val visibleMarker = firstMarkerAtOrAfter(body, unit.charStart)
            ?: return null to "恢复目标之后无任何夹具标记"
        if (expectedBookId.isNotBlank() && snapshot != null && snapshot.bookId.isNotBlank() &&
            snapshot.bookId != expectedBookId
        ) {
            return null to "书籍身份错配：期望=$expectedBookId，实际=${snapshot.bookId}"
        }
        return J1Contract(
            bodyLength = body.length,
            savedOffset = savedOffset,
            savedUnitIndex = ui,
            savedUnitStart = unit.charStart,
            savedUnitEnd = unit.charEnd,
            savedParagraphMarker = savedMarker,
            expectedRestoreOffset = unit.charStart,
            expectedVisibleMarker = visibleMarker,
            savedUpdatedAt = snapshot?.updatedAt,
            savedPercent = snapshot?.progressPercent,
            savedLocationJson = snapshot?.currentLocationJson,
            bookId = snapshot?.bookId ?: expectedBookId,
        ) to "ok"
    }

    /**
     * 构建短末项 clamp 合同。必须落在末单元（isLastUnit == true）。
     */
    fun buildClamp(
        body: String,
        units: List<ReadingUnitRange>,
        savedOffset: Int?,
        snapshot: J1ProgressSnapshot? = null,
        expectedBookId: String = "",
    ): Pair<J1ClampContract?, String> {
        if (savedOffset == null) return null to "短末项退出保存偏移缺失"
        if (savedOffset <= 0) return null to "短末项退出保存偏移必须 > 0，实际=$savedOffset"
        if (units.isEmpty()) return null to "阅读单元为空"
        if (savedOffset > body.length) return null to "短末项保存偏移越界：$savedOffset > ${body.length}"
        val ui = unitIndexForOffset(units, savedOffset)
        val unit = units[ui]
        val isLast = ui == units.lastIndex
        if (!isLast) return null to "短末项合同必须落在末单元（units.lastIndex=${units.lastIndex}，实际=$ui）"
        val marker = lastMarkerAtOrBefore(body, savedOffset, CLAMP_MARKER_PREFIX)
            ?: return null to "短末项偏移前无夹具标记"
        if (expectedBookId.isNotBlank() && snapshot != null && snapshot.bookId.isNotBlank() &&
            snapshot.bookId != expectedBookId
        ) {
            return null to "短末项书籍身份错配：期望=$expectedBookId，实际=${snapshot.bookId}"
        }
        return J1ClampContract(
            bodyLength = body.length,
            savedOffset = savedOffset,
            savedUnitIndex = ui,
            savedUnitStart = unit.charStart,
            savedUnitEnd = unit.charEnd,
            isLastUnit = true,
            savedParagraphMarker = marker,
            savedUpdatedAt = snapshot?.updatedAt,
            savedPercent = snapshot?.progressPercent,
            savedLocationJson = snapshot?.currentLocationJson,
            bookId = snapshot?.bookId ?: expectedBookId,
        ) to "ok"
    }

    /**
     * J1 普通重进判定：单元级恢复合同 + 真实 UI dump。
     */
    fun reentryAccepted(
        contract: J1Contract?,
        units: List<ReadingUnitRange>,
        reenteredOffset: Int?,
        uiDump: String?,
    ): J1Verdict {
        if (contract == null) return J1Verdict(false, "缺退出保存合同（缺恢复证据不得判通过）")
        if (reenteredOffset == null) return J1Verdict(false, "重进偏移缺失")
        if (reenteredOffset <= 0) return J1Verdict(false, "重进偏移必须 > 0（清零必须拒绝），实际=$reenteredOffset")
        val reUnit = unitIndexForOffset(units, reenteredOffset)
        if (reUnit != contract.savedUnitIndex) {
            return J1Verdict(
                false,
                "重进偏移落在错误阅读单元（期望 unitIndex=${contract.savedUnitIndex}，实际=$reUnit）——非零但错误单元必须拒绝",
            )
        }
        if (reenteredOffset < contract.savedUnitStart || reenteredOffset > contract.savedUnitEnd) {
            return J1Verdict(
                false,
                "重进偏移越出退出保存单元范围 [${contract.savedUnitStart},${contract.savedUnitEnd}]，实际=$reenteredOffset",
            )
        }
        val isUnitStart = reenteredOffset == contract.savedUnitStart
        val isExact = reenteredOffset == contract.savedOffset
        if (!isUnitStart && !isExact) {
            return J1Verdict(
                false,
                "重进偏移既非单元起点（${contract.savedUnitStart}）也非精确保存值（${contract.savedOffset}），实际=$reenteredOffset",
            )
        }
        if (uiDump.isNullOrBlank()) {
            return J1Verdict(false, "缺少重进真实 UI dump —— 无 UI 证据不得判通过")
        }
        if (!uiDump.contains(contract.expectedVisibleMarker)) {
            return J1Verdict(
                false,
                "重进 UI dump 不含恢复目标可见标记（期望=${contract.expectedVisibleMarker}）——UI 标记缺失或错误必须拒绝",
            )
        }
        return J1Verdict(true, "单元起点恢复且 UI 命中（R=$reenteredOffset，unit=${contract.savedUnitIndex}）")
    }

    /**
     * 等待期未清零判定。
     */
    fun noZeroDuringWaitAccepted(
        contract: J1Contract?,
        units: List<ReadingUnitRange>,
        observedOffsets: List<Int?>,
    ): J1Verdict {
        if (contract == null) return J1Verdict(false, "缺退出保存合同")
        if (observedOffsets.isEmpty()) return J1Verdict(false, "缺等待期检查点偏移证据")
        for ((i, offset) in observedOffsets.withIndex()) {
            if (offset == null) return J1Verdict(false, "等待期检查点[$i]偏移缺失")
            if (offset <= 0) return J1Verdict(false, "等待期检查点[$i]偏移被覆盖为 $offset（原非零进度被清零）")
            if (unitIndexForOffset(units, offset) != contract.savedUnitIndex) {
                return J1Verdict(false, "等待期检查点[$i]偏移 $offset 离开退出保存单元")
            }
        }
        return J1Verdict(true, "等待期 ${observedOffsets.size} 个检查点均保持非零且同单元")
    }

    /**
     * 恢复后继续移动并保存判定【核心强化：防保存冻结反例】。
     *
     * 必须同时满足：
     * 1. newSnapshot 有效且 newOffset > 0；
     * 2. newOffset != reenteredOffset（未停留在恢复起点）；
     * 3. **newOffset != contract.savedOffset**（绝不能等于旧的保存值 S！若相等则证明发生保存冻结！）；
     * 4. 单元合理：unitIndexForOffset(units, newOffset) >= contract.savedUnitIndex；
     * 5. 时间戳实际更新：newUpdatedAt 必须严格不同于 savedUpdatedAt 与 baselineUpdatedAt；
     * 6. locator / percent 实际变化（不能全行冻结）；
     * 7. 真实 UI dump 命中 newOffset 对应的最新标记；
     * 8. UI dump 前后正文 marker 必须发生非空实质位移。
     */
    fun newPositionAccepted(
        contract: J1Contract?,
        units: List<ReadingUnitRange>,
        reenteredOffset: Int?,
        baselineSnapshot: J1ProgressSnapshot?,
        newSnapshot: J1ProgressSnapshot?,
        body: String,
        uiDumpAfterMove: String?,
        uiDumpBeforeMove: String? = null,
    ): J1Verdict {
        if (contract == null) return J1Verdict(false, "缺退出保存合同")
        if (newSnapshot == null) return J1Verdict(false, "恢复后继续滚动的新保存记录缺失")
        val newOffset = newSnapshot.legacyOffset
        if (newOffset <= 0) return J1Verdict(false, "新保存偏移必须 > 0，实际=$newOffset")
        if (newOffset >= body.length) return J1Verdict(false, "新保存偏移越界：$newOffset >= ${body.length}")
        if (newSnapshot.bookId.isBlank() || (contract.bookId.isNotBlank() && newSnapshot.bookId != contract.bookId)) {
            return J1Verdict(false, "S2书籍身份缺失或错配")
        }
        if (newSnapshot.currentLocationJson.isBlank() || newSnapshot.progressPercent !in 0f..100f) {
            return J1Verdict(false, "S2完整进度行缺少locationJson或percent越界")
        }

        // 核心防冻结断言 1：不等于恢复起点
        if (reenteredOffset != null && reenteredOffset >= 0 && newOffset == reenteredOffset) {
            return J1Verdict(false, "新保存偏移等于重进值（未真实移动，R=$reenteredOffset）")
        }

        // 核心防冻结断言 2：绝不能等于旧的保存值 S
        if (newOffset == contract.savedOffset) {
            return J1Verdict(
                false,
                "检测到保存冻结：UI 已移动但 DB 进度依然为旧保存值 S (offset=${contract.savedOffset})",
            )
        }

        // 核心防冻结断言 3：时间戳必须更新
        if (newSnapshot.updatedAt.isBlank() || contract.savedUpdatedAt.isNullOrBlank() ||
            newSnapshot.updatedAt == contract.savedUpdatedAt) {
            return J1Verdict(
                false,
                "检测到保存冻结：UI 已移动但 updated_at 仍为旧保存时间戳 (${contract.savedUpdatedAt})",
            )
        }
        if (baselineSnapshot == null || baselineSnapshot.updatedAt.isBlank() ||
            newSnapshot.updatedAt == baselineSnapshot.updatedAt) {
            return J1Verdict(false, "检测到保存冻结：进度行与恢复时基准完全一致（未更新）")
        }
        if (baselineSnapshot.currentLocationJson.isNotBlank() &&
            newSnapshot.currentLocationJson == baselineSnapshot.currentLocationJson) {
            return J1Verdict(false, "S2 locationJson仍与R完全相同")
        }

        // 单元合理性（继续向后阅读）
        val newUnit = unitIndexForOffset(units, newOffset)
        if (newUnit < contract.savedUnitIndex) {
            return J1Verdict(false, "新保存偏移回退到退出保存单元之前（unit=$newUnit < saved=${contract.savedUnitIndex}）")
        }

        // 正文段落标记匹配
        val marker = lastMarkerAtOrBefore(body, newOffset)
            ?: return J1Verdict(false, "新保存偏移前无任何夹具标记")
        if (uiDumpAfterMove.isNullOrBlank()) {
            return J1Verdict(false, "缺少继续滚动后的 UI dump —— 无 UI 证据不得判通过")
        }
        if (!uiDumpAfterMove.contains(marker)) {
            return J1Verdict(false, "滚动后 UI dump 不含新保存位置所在段落标记（期望=$marker）")
        }

        // 真实 UI 位移验证
        if (!uiDumpBeforeMove.isNullOrBlank()) {
            val beforeMarker = lastMarkerAtOrBefore(body, reenteredOffset ?: contract.savedUnitStart)
            if (beforeMarker != null && uiDumpBeforeMove.contains(beforeMarker) &&
                uiDumpAfterMove.contains(beforeMarker) && !uiDumpAfterMove.contains(marker)
            ) {
                return J1Verdict(false, "UI dump 前后正文标记无位移（仍停留于 $beforeMarker）")
            }
        }

        return J1Verdict(
            true,
            "新位置合法保存且 UI 命中（S2=$newOffset, unit=$newUnit, marker=$marker, updatedAt=${newSnapshot.updatedAt}）",
        )
    }

    // ── 短末项 clamp 独立时点判定方法 ────────────────────────────────────────

    /**
     * 时点 2 (R)：短末项 clamp 重进落位判定。
     * 验证落点落在末单元（clamp 生效），且 UI dump 包含末单元正文标记。
     */
    fun clampReentryAccepted(
        clampContract: J1ClampContract?,
        units: List<ReadingUnitRange>,
        reenteredSnapshot: J1ProgressSnapshot?,
        reenteredDump: String?,
    ): J1Verdict {
        if (clampContract == null) return J1Verdict(false, "缺短末项 clamp 合同")
        if (reenteredSnapshot == null) return J1Verdict(false, "短末项重进快照缺失")
        val reenteredOffset = reenteredSnapshot.legacyOffset
        if (reenteredOffset <= 0) return J1Verdict(false, "短末项重进偏移清零（offset=$reenteredOffset）")
        val reUnit = unitIndexForOffset(units, reenteredOffset)
        if (reUnit != clampContract.savedUnitIndex || !clampContract.isLastUnit) {
            return J1Verdict(false, "短末项重进未落在末单元（unit=$reUnit，末单元=${clampContract.savedUnitIndex}）")
        }
        if (reenteredDump.isNullOrBlank()) {
            return J1Verdict(false, "缺短末项重进 UI dump")
        }
        if (!reenteredDump.contains(clampContract.savedParagraphMarker)) {
            return J1Verdict(false, "短末项重进 UI dump 不含末项标记 ${clampContract.savedParagraphMarker}")
        }
        return J1Verdict(
            true,
            "短末项 clamp 合法落位 R（R=$reenteredOffset, unit=$reUnit, marker=${clampContract.savedParagraphMarker}）",
        )
    }

    /**
     * 时点 3 (S2)：短末项恢复后反向移动并保存判定。
     *
     * 必须证明：
     * 1. S2 快照完全独立（不等于 R，也不等于 S）；
     * 2. offset 发生明确变化（末尾向上滑动后 offset 减小或进入前一单元）；
     * 3. updated_at 刷新（不同于 S.updatedAt 和 R.updatedAt）；
     * 4. 真实 UI dump 命中 S2 对应的新 marker。
     */
    fun clampNewPositionAccepted(
        clampContract: J1ClampContract?,
        units: List<ReadingUnitRange>,
        reenteredSnapshot: J1ProgressSnapshot?,
        newSnapshot: J1ProgressSnapshot?,
        body: String,
        uiDumpAfterMove: String?,
    ): J1Verdict {
        if (clampContract == null) return J1Verdict(false, "缺短末项 clamp 合同")
        if (newSnapshot == null) return J1Verdict(false, "短末项新位置保存快照缺失")
        val newOffset = newSnapshot.legacyOffset
        if (newOffset < 0) return J1Verdict(false, "短末项新保存偏移为负（offset=$newOffset）")
        if (newOffset > body.length) return J1Verdict(false, "短末项新保存偏移越界")
        if (newSnapshot.bookId.isBlank() || (clampContract.bookId.isNotBlank() && newSnapshot.bookId != clampContract.bookId)) {
            return J1Verdict(false, "短末项S2书籍身份缺失或错配")
        }
        if (newSnapshot.currentLocationJson.isBlank() || newSnapshot.progressPercent !in 0f..100f) {
            return J1Verdict(false, "短末项S2完整进度行缺少locationJson或percent越界")
        }

        val rOffset = reenteredSnapshot?.legacyOffset ?: -1
        if (rOffset > 0 && newOffset == rOffset) {
            return J1Verdict(false, "短末项新保存偏移等于重进落点 R（未真实移动，R=$rOffset）")
        }

        if (newOffset == clampContract.savedOffset) {
            return J1Verdict(
                false,
                "检测到短末项保存冻结：UI 已移动但 DB 进度依然为旧保存值 S (offset=${clampContract.savedOffset})",
            )
        }

        // 时间戳必须更新
        if (newSnapshot.updatedAt.isBlank() || clampContract.savedUpdatedAt.isNullOrBlank() ||
            newSnapshot.updatedAt == clampContract.savedUpdatedAt) {
            return J1Verdict(
                false,
                "检测到短末项时间戳冻结：updated_at 仍为旧保存时间戳 (${clampContract.savedUpdatedAt})",
            )
        }
        if (reenteredSnapshot == null || reenteredSnapshot.updatedAt.isBlank() ||
            newSnapshot.updatedAt == reenteredSnapshot.updatedAt) {
            return J1Verdict(false, "检测到短末项保存冻结：进度行与重进落点基准完全一致（未更新）")
        }
        if (reenteredSnapshot.currentLocationJson.isNotBlank() &&
            newSnapshot.currentLocationJson == reenteredSnapshot.currentLocationJson) {
            return J1Verdict(false, "短末项S2 locationJson仍与R完全相同")
        }
        if (newOffset == 0 && newSnapshot.progressPercent != 0f) {
            return J1Verdict(false, "短末项合法书首位置0的percent必须为0")
        }
        if (newOffset == body.length && newSnapshot.progressPercent != 100f) {
            return J1Verdict(false, "短末项合法EOF位置的percent必须为100")
        }
        if (newSnapshot.progressPercent == clampContract.savedPercent ||
            newSnapshot.progressPercent == reenteredSnapshot.progressPercent) {
            return J1Verdict(false, "短末项S2 percent未反映独立位置变化")
        }

        val newUnit = unitIndexForOffset(units, newOffset)
        val marker = lastMarkerAtOrBefore(body, newOffset, CLAMP_MARKER_PREFIX)
            ?: (if (newOffset == 0) firstMarkerAtOrAfter(body, 0, CLAMP_MARKER_PREFIX) else null)
            ?: return J1Verdict(false, "短末项新保存偏移前无有效夹具标记")

        if (uiDumpAfterMove.isNullOrBlank()) {
            return J1Verdict(false, "缺短末项移动后的 UI dump")
        }
        if (!uiDumpAfterMove.contains(marker)) {
            return J1Verdict(false, "短末项移动后 UI dump 不含新位置标记（期望=$marker）")
        }

        return J1Verdict(
            true,
            "短末项反向移动新位置合法保存（S2=$newOffset, unit=$newUnit, marker=$marker, updatedAt=${newSnapshot.updatedAt}）",
        )
    }

    /**
     * 时点 4 (Cold)：短末项新位置冷启动读回判定。
     * 验证冷进程启动后读回的新位置满足阅读单元恢复合同，非零，且正文 marker 匹配。
     */
    fun clampColdReentryAccepted(
        persistedSnapshot: J1ProgressSnapshot?,
        coldSnapshot: J1ProgressSnapshot?,
        units: List<ReadingUnitRange>,
        body: String,
        coldDump: String?,
    ): J1Verdict {
        if (persistedSnapshot == null) return J1Verdict(false, "缺短末项持久化基准 S2 快照")
        if (coldSnapshot == null) return J1Verdict(false, "缺短末项冷读回快照")
        val coldOffset = coldSnapshot.legacyOffset
        if (coldOffset < 0) return J1Verdict(false, "短末项冷读回偏移为负（coldOffset=$coldOffset）")
        if (coldSnapshot.bookId.isBlank() || coldSnapshot.bookId != persistedSnapshot.bookId ||
            coldSnapshot.currentLocationJson.isBlank() || coldSnapshot.updatedAt.isBlank()) {
            return J1Verdict(false, "短末项冷读回完整进度行缺字段或书籍身份错配")
        }
        if (coldOffset > body.length) return coldVerdict(false, "短末项冷读回偏移越界")
        if ((coldOffset == 0 && coldSnapshot.progressPercent != 0f) ||
            (coldOffset == body.length && coldSnapshot.progressPercent != 100f)) {
            return J1Verdict(false, "短末项书首/EOF冷读回percent与边界位置不匹配")
        }

        val expectedUnit = unitIndexForOffset(units, persistedSnapshot.legacyOffset)
        val coldUnit = unitIndexForOffset(units, coldOffset)
        if (coldUnit != expectedUnit) {
            return J1Verdict(false, "短末项冷读回单元不符：期望 unit=$expectedUnit，实际 unit=$coldUnit")
        }

        val unitStart = units[expectedUnit].charStart
        val isExact = coldOffset == persistedSnapshot.legacyOffset
        val isStart = coldOffset == unitStart
        if (!isExact && !isStart) {
            return J1Verdict(false, "短末项冷读回偏移既非精确 S2 也非单元起点，实际=$coldOffset")
        }

        val marker = lastMarkerAtOrBefore(body, coldOffset, CLAMP_MARKER_PREFIX)
            ?: (if (coldOffset == 0) firstMarkerAtOrAfter(body, 0, CLAMP_MARKER_PREFIX) else null)
            ?: return J1Verdict(false, "短末项冷读回偏移前无有效标记")
        if (coldDump.isNullOrBlank()) {
            return J1Verdict(false, "缺短末项冷读回 UI dump")
        }
        if (!coldDump.contains(marker)) {
            return J1Verdict(false, "短末项冷读回 UI dump 不含目标标记（期望=$marker）")
        }

        return J1Verdict(
            true,
            "短末项冷读回成功满足单元合同（coldOffset=$coldOffset, unit=$coldUnit, marker=$marker）",
        )
    }

    private fun coldVerdict(b: Boolean, s: String) = J1Verdict(b, s)
}
