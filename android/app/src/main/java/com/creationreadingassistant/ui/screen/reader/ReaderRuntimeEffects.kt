@file:OptIn(ExperimentalFoundationApi::class)

package com.creationreadingassistant.ui.screen.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import com.creationreadingassistant.feature.reader.doc.TxtFileIndex
import com.creationreadingassistant.feature.reader.pager.AutoScrollAccumulator
import com.creationreadingassistant.ui.theme.MotionTokens
import com.creationreadingassistant.ui.theme.rememberReducedMotion
import com.creationreadingassistant.ui.viewmodel.TxtRuleScanResult
import kotlinx.coroutines.delay

/**
 * Phase 4 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 抽出的 6 个
 * 「运行时」LaunchedEffect（章节淡入 / 护眼时间调度 / 焦点块滚动 / TXT 规则扫描结果 /
 * 自动翻页帧推进 / 自动翻页触感）。
 *
 * 每个 effect 体逐字搬运自 ReaderScreen，不改任何时序与帧参数。
 *
 * ## State-holder 传参
 *
 * effect 体内被读 / 被写的可变状态以 State-holder 形式传入（读 `.value` 拿当前快照）：
 * - [currentMinuteState]：eyeCare effect 每分钟写；
 * - [pendingTxtRuleAnchorOffsetState]：txtRule effect 读写；
 * - [txtStreamingDocumentState] / [txtStreamingFileIndexState]：txtRule effect 写；
 * - [autoPagingActiveState]：autoPaging effect 的 key + while 循环条件 + 体内写。
 *
 * 既是 key 又在体内被读 / 被写的 [autoPagingActiveState]：用 `.value` 既作 key（组合期
 * 快照读取，变化即重启）又作读写入口，与原 `var by` 行为一致。
 */
@Suppress("LongParameterList")
@Composable
internal fun ReaderRuntimeEffects(
    chapterFadeKey: Int,
    chapterFade: Animatable<Float, AnimationVector1D>,
    readerSettings: ReaderSettings,
    currentMinuteState: MutableIntState,
    focusBlockIndex: Int?,
    epubBringRequester: BringIntoViewRequester,
    txtRuleScanResult: TxtRuleScanResult?,
    pendingTxtRuleAnchorOffsetState: MutableIntState,
    pagedJumpRequest: MutableState<Int?>,
    txtStreamingDocumentState: MutableState<PlainTextDocument?>,
    txtStreamingFileIndexState: MutableState<TxtFileIndex?>,
    autoPagingActiveState: MutableState<Boolean>,
    autoPagingPaused: Boolean,
    epubListState: LazyListState,
    plainListState: LazyListState,
    epubBook: EpubBook?,
    showNotice: (String) -> Unit,
    haptic: (HapticFeedbackType) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()

    // C6：滚动模式切章时正文轻淡入（翻页模式由 PagedEpubView 的 contentAlpha 负责）；尊重「减少动态效果」
    LaunchedEffect(chapterFadeKey) {
        if (reducedMotion) {
            chapterFade.snapTo(1f)
            return@LaunchedEffect
        }
        chapterFade.snapTo(0.45f)
        chapterFade.animateTo(1f, tween(durationMillis = MotionTokens.Fast))
    }

    // 护眼时间调度：每分钟刷新 currentMinute
    LaunchedEffect(readerSettings.eyeCareScheduleEnabled) {
        while (true) {
            val calendar = java.util.Calendar.getInstance()
            currentMinuteState.value = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                calendar.get(java.util.Calendar.MINUTE)
            delay(60_000)
        }
    }

    // 滚动聚焦块：触发 BringIntoView
    LaunchedEffect(focusBlockIndex) {
        if (focusBlockIndex != null) epubBringRequester.bringIntoView()
    }

    // R6：观察 TXT 规则扫描结果（放在 showNotice / pagedJumpRequest 之后）
    LaunchedEffect(txtRuleScanResult) {
        val r = txtRuleScanResult ?: return@LaunchedEffect
        if (r.error != null) {
            showNotice(r.error)
        } else {
            r.document?.let { txtStreamingDocumentState.value = it }
            r.fileIndex?.let { txtStreamingFileIndexState.value = it }
            // 重建完成后再跳转，确保 readingUnits 已是新数据
            if (pendingTxtRuleAnchorOffsetState.value >= 0) {
                pagedJumpRequest.value = pendingTxtRuleAnchorOffsetState.value
                pendingTxtRuleAnchorOffsetState.value = -1
            }
        }
    }

    // 滚动模式按帧匀速推进；弹层、选区、TTS 或切后台时保留"运行中"状态但暂停计时。
    LaunchedEffect(
        autoPagingActiveState.value,
        autoPagingPaused,
        readerSettings.readerMode,
        readerSettings.autoPageSpeed,
        epubBook,
    ) {
        if (!autoPagingActiveState.value || autoPagingPaused || readerSettings.readerMode != "scroll") {
            return@LaunchedEffect
        }
        val state = if (epubBook != null) epubListState else plainListState
        val accumulator = AutoScrollAccumulator()
        var previousFrame = withFrameNanos { it }
        while (autoPagingActiveState.value) {
            val frame = withFrameNanos { it }
            val elapsed = frame - previousFrame
            previousFrame = frame
            val pixels = accumulator.consume(
                speed = readerSettings.autoPageSpeed,
                viewportHeightPx = state.layoutInfo.viewportSize.height,
                elapsedNanos = elapsed,
            )
            if (pixels <= 0) continue
            state.scrollBy(pixels.toFloat())
            if (!state.canScrollForward) {
                autoPagingActiveState.value = false
                showNotice("已读到书末")
                break
            }
        }
    }

    // 自动翻页开启时给一次确认感触感（尊重系统「减少动态效果」）
    LaunchedEffect(autoPagingActiveState.value) {
        if (autoPagingActiveState.value) haptic(HapticFeedbackType.LongPress)
    }
}
