package com.creationreadingassistant.acceptance

/**
 * 任务69-v1-A：真实阅读位移的严格判定（仪器与离线验证共用同一实现）。
 *
 * ## 缺陷
 *
 * 68-v1 的等待谓词为：
 * `now != null && (offsetBefore == null || now > offsetBefore + MIN_DELTA_CHARS)`
 *
 * 首次打开尚无进度行时 `offsetBefore == null`，随后任何非 null 值（包括 0）都会让
 * 等待**提前成功**；紧接着的严格断言又以 `offsetBefore ?: 0` 为基准拒绝 delta=0。
 * 两者口径矛盾：等待提前结束、下一章兜底被跳过，最终在严格断言处失败。
 *
 * ## 统一口径
 *
 * - 起点 null 视作 0（首次打开没有历史进度）；
 * - 必须 `now - base > minDeltaChars`（严格大于；1200 门槛不降低）；
 * - `now == null` 一律未达门槛；
 * - 不以「进度行出现」「非零」或「出现进度行」当作位移。
 */
internal fun strictDisplacementExceeded(
    offsetBefore: Int?,
    offsetNow: Int?,
    minDeltaChars: Int,
): Boolean {
    val now = offsetNow ?: return false
    val base = offsetBefore ?: 0
    return now - base > minDeltaChars
}

/**
 * 位移失败归类：区分「未发生阅读位移」与「已位移但保存未形成」。
 *
 * 仅用于失败证据落盘，不改变断言结论；UI 位置不可观察时明确标注，
 * 不把不可观察当作「未位移」或「已位移」。
 */
internal fun classifyDisplacementFailure(
    uiBeforeOffset: Int?,
    uiAfterOffset: Int?,
    persistedDelta: Int?,
    minDeltaChars: Int,
): String {
    if (uiBeforeOffset == null || uiAfterOffset == null) return "ui-position-unobservable"
    val uiDelta = uiAfterOffset - uiBeforeOffset
    val persisted = persistedDelta ?: 0
    return when {
        uiDelta > minDeltaChars && persisted > minDeltaChars -> "observed-after-wait-window"
        uiDelta > minDeltaChars -> "ui-moved-persist-missing"
        persisted > minDeltaChars -> "persisted-advanced-without-ui-proof"
        else -> "no-reading-displacement"
    }
}
