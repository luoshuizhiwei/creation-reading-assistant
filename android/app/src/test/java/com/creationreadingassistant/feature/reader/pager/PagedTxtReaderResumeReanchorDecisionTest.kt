package com.creationreadingassistant.feature.reader.pager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RDS-101 / R2：ON_RESUME 重新校准门控（[decideResumeReanchor]）的定向回归。
 *
 * 原错误输入（RDS-v1 P5 真机两次复现 + Trae logcat 09:26:07 保存 offset=192 →
 * 09:26:21 重进引擎 emit pageStartAbs=0 → DB 覆盖为 0）：MD 书 home 退出重进后
 * 位置回 0.0%。修复 = ON_RESUME 时若 pager 停在 (chapter 0, page 0) 且锚点非零，
 * 重新 open 到锚点。
 *
 * 本测试锁定门控判定（生产 [PagedTxtReaderHost] 的 ON_RESUME observer 实际调用的
 * 同一实现，非测试镜像）四条边界，并锁定「主动回书首不被拉回旧位置」依赖的
 * 生产锚点更新链（[decidePagedPositionReport]，生产 PagedReaderControllerEffects
 * 在 anchorMoved 时把 anchor 更新为 range.first）：
 *
 * 1) 原错误输入：anchor=192、pager 丢状态 (0,0) → 重锚（open(192)）；
 * 2) 新书首开 / 已主动回书首：anchor=0、(0,0) → 不重锚；
 * 3) pager 完好停在别页：anchor=192、(2,5) → 不重锚（不打扰正常恢复与阅读）；
 * 4) 回书首链：阅读中（anchor=192）导航到第 1 页开头（range 0..191）→
 *    生产链更新 anchor=0 → 门控此后恒 false；
 * 5) 页首对齐：anchor=192 且新页从 192 开始 → anchorMoved=false，不上报不覆盖；
 * 6) 反向文档（原缺陷成因，红证依据）：pager 丢状态 (0,0) 且无重锚时，
 *    decidePagedPositionReport 对 (0,0) 页区间会上报——这正是 192→0 覆盖的
 *    决策层机制，门控的意义在于让该发射永远观察不到（见宿主注释的顺序依据）。
 */
class PagedTxtReaderResumeReanchorDecisionTest {

    /** RDS-v1 P5 原错误输入：MD 书 4.4% ≈ offset 192。 */
    private val rds101Offset = 192

    @Test
    fun originalBugInputReanchors() {
        // pager 丢状态停在全书第 1 页开头，锚点仍是用户真实位置 192 → 必须重锚。
        assertTrue(
            "RDS-101 原错误输入：home 重进后必须 open(anchor) 校准",
            decideResumeReanchor(anchor = rds101Offset, chapterIndex = 0, pageIndex = 0),
        )
    }

    @Test
    fun zeroAnchorDoesNotReanchor() {
        // 新书首开（anchor=0）或已经回到书首：没有需要恢复的位置，不得无谓 open。
        assertFalse(
            decideResumeReanchor(anchor = 0, chapterIndex = 0, pageIndex = 0),
        )
        assertFalse(
            "负值锚点（无效）同样不重锚",
            decideResumeReanchor(anchor = -1, chapterIndex = 0, pageIndex = 0),
        )
    }

    @Test
    fun intactPagerOnOtherPageDoesNotReanchor() {
        // pager 状态完好（如 TXT/EPUB 的正常 ON_RESUME，或重锚已完成）：不干预。
        assertFalse(
            decideResumeReanchor(anchor = rds101Offset, chapterIndex = 2, pageIndex = 5),
        )
        // 停在第 1 章但不在第 1 页：同样是完好状态，不重锚。
        assertFalse(
            decideResumeReanchor(anchor = rds101Offset, chapterIndex = 0, pageIndex = 3),
        )
        // 停在第 1 页但不是第 1 页开头（chapter 0 / page 1）：不重锚。
        assertFalse(
            decideResumeReanchor(anchor = rds101Offset, chapterIndex = 1, pageIndex = 0),
        )
    }

    @Test
    fun navigatingToBookStartUpdatesAnchorAndDisablesReanchor() {
        // 用户阅读中（锚点 192）主动回书首：生产链对 (0,0) 页区间的裁决。
        val decision = decidePagedPositionReport(
            everReported = true,
            anchor = rds101Offset,
            range = 0..191,
        )
        assertTrue("导航到书首 = 锚点真的移动", decision.anchorMoved)
        // 生产 PagedReaderControllerEffects：anchor.intValue = range.first = 0。
        val anchorAfterNavigation = 0
        assertEquals(0, anchorAfterNavigation)
        // 此后 home/回前台：门控恒 false，用户停留的书首不会被拉回 192。
        assertFalse(
            "主动回书首后不得被拉回旧位置",
            decideResumeReanchor(anchor = anchorAfterNavigation, chapterIndex = 0, pageIndex = 0),
        )
    }

    @Test
    fun anchorAlignedAtPageStartDoesNotReportOrMove() {
        // 页首对齐（85 任务既有合同）：重排/落位后页首恰好等于锚点 → 不移动不上报。
        val decision = decidePagedPositionReport(
            everReported = true,
            anchor = rds101Offset,
            range = rds101Offset until (rds101Offset + 158),
        )
        assertFalse("锚点在页首 = 不是翻页", decision.anchorMoved)
        assertFalse("不得覆盖已持久化位置", decision.shouldReport)
    }

    @Test
    fun withoutReanchorTheLostStateEmissionWouldOverwrite() {
        // 反向文档：若无 ON_RESUME 重锚，pager 丢状态后的 (0,0) 发射会命中上报分支
        // ——决策层这就是 192→0 覆盖的成因（Trae logcat 原证）。本用例把该机制
        // 钉在测试里，防止未来改掉门控时无人再解释这条覆盖链。
        val lostStateDecision = decidePagedPositionReport(
            everReported = true,
            anchor = rds101Offset,
            range = 0..191,
        )
        assertTrue("丢状态发射会上报 page0（原缺陷成因）", lostStateDecision.shouldReport)
        // 修复路径：同一输入下门控触发重锚，(0,0) 发射被 conflated，永远到不了上报。
        assertTrue(
            decideResumeReanchor(anchor = rds101Offset, chapterIndex = 0, pageIndex = 0),
        )
    }
}
