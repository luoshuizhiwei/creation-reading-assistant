package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.ReaderDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 音量键翻页（从 ReaderActions.kt 按领域拆出）：
 * [readerVolumeTurnPlan] 为纯决策（JVM 可测，见 ReaderVolumeKeyTurnPolicyTest），
 * [readerVolumeKeyTurn] 负责把决策落到分页/滚动/跨章动作上。
 */

/**
 * B3：音量键翻页统一处理（原 ReaderScreen 的 onVolumeUp / onVolumeDown 两个 lambda 体
 * 逐字合并搬运，direction：-1=音量上/向前，1=音量下/向后）。返回值语义不变：
 * true=已消费，false=交回系统。
 */
internal sealed interface ReaderVolumeTurnPlan {
    data object UseSystemVolume : ReaderVolumeTurnPlan
    data class Paged(val direction: Int) : ReaderVolumeTurnPlan
    data class Scroll(val direction: Int) : ReaderVolumeTurnPlan
    data class Chapter(val targetIndex: Int) : ReaderVolumeTurnPlan
}

/**
 * Decides whether a hardware volume press has an actionable reader operation.
 * At a boundary, returning system-volume avoids swallowing a key that cannot turn a page.
 */
@Suppress("LongParameterList")
internal fun readerVolumeTurnPlan(
    direction: Int,
    volumeKeyPaging: Boolean,
    allowDuringTts: Boolean,
    ttsVisible: Boolean,
    pagerEngineOn: Boolean,
    scrollLayoutReady: Boolean,
    canScroll: Boolean,
    chapterIndex: Int,
    chapterCount: Int,
): ReaderVolumeTurnPlan {
    if (direction !in setOf(-1, 1) || !volumeKeyPaging || (ttsVisible && !allowDuringTts)) {
        return ReaderVolumeTurnPlan.UseSystemVolume
    }
    if (pagerEngineOn) return ReaderVolumeTurnPlan.Paged(direction)
    if (!scrollLayoutReady) return ReaderVolumeTurnPlan.UseSystemVolume
    if (canScroll) return ReaderVolumeTurnPlan.Scroll(direction)
    val targetIndex = chapterIndex + direction
    return if (targetIndex in 0 until chapterCount) {
        ReaderVolumeTurnPlan.Chapter(targetIndex)
    } else {
        ReaderVolumeTurnPlan.UseSystemVolume
    }
}

@Suppress("LongParameterList")
internal fun readerVolumeKeyTurn(
    direction: Int,
    readerSettings: ReaderSettings,
    showTts: Boolean,
    pagerEngineOn: Boolean,
    pagedHardwareTurnRequest: MutableState<Int?>,
    epubBook: EpubBook?,
    markdownDocument: ReaderDocument?,
    chapterIndex: Int,
    goToChapter: (Int) -> Unit,
    scope: CoroutineScope,
    plainListState: LazyListState,
    epubListState: LazyListState? = null,
): Boolean {
    val activeScrollState = if (epubBook != null || markdownDocument != null) {
        epubListState ?: plainListState
    } else {
        plainListState
    }
    val plan = readerVolumeTurnPlan(
        direction = direction,
        volumeKeyPaging = readerSettings.volumeKeyPaging,
        allowDuringTts = readerSettings.volumeKeyPagingDuringTts,
        ttsVisible = showTts,
        pagerEngineOn = pagerEngineOn,
        scrollLayoutReady = activeScrollState.layoutInfo.viewportSize.height > 0,
        canScroll = if (direction > 0) activeScrollState.canScrollForward else activeScrollState.canScrollBackward,
        chapterIndex = chapterIndex,
        chapterCount = epubBook?.chapters?.size ?: markdownDocument?.chapters?.size ?: 0,
    )
    when (plan) {
        is ReaderVolumeTurnPlan.Paged -> pagedHardwareTurnRequest.value = plan.direction
        is ReaderVolumeTurnPlan.Scroll -> scope.launch {
            val amount = activeScrollState.layoutInfo.viewportSize.height * 0.88f * plan.direction
            activeScrollState.animateScrollBy(amount)
        }
        is ReaderVolumeTurnPlan.Chapter -> goToChapter(plan.targetIndex)
        ReaderVolumeTurnPlan.UseSystemVolume -> return false
    }
    return true
}
