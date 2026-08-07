package com.creationreadingassistant.ui.screen.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.domain.model.EpubBook
import com.creationreadingassistant.feature.reader.doc.DocBlock
import com.creationreadingassistant.feature.reader.eyecare.EyeCareSchedule
import com.creationreadingassistant.ui.screen.reader.tts.TtsController
import com.creationreadingassistant.ui.theme.ReaderPaperPalette

/**
 * B3 结构拆分：从 [com.creationreadingassistant.ui.screen.ReaderScreen] 主函数抽出的
 * 「护眼调度 / 朗读句高亮 / 块全局偏移 / TTS 句定位 / 滚动焦点块」派生状态组。
 *
 * 沿用 [rememberReaderDerivedState] / [rememberReaderProgress] 的 State-holder 模式：
 * remember / 派生表达式逐字搬运，key 与原值一致；[navFocusBlockIndexState] 由主函数
 * 传入（ReaderProgressEffects 写入、此处读取），语义与原 `var by` delegate 一致。
 * [currentMinuteState] 暴露 holder 供 ReaderRuntimeEffects 每分钟写入。
 */
@OptIn(ExperimentalFoundationApi::class)
internal data class ReaderEyeCareFocusState(
    val currentMinuteState: MutableIntState,
    val eyeCareActive: Boolean,
    val eyeFilterColor: Color,
    val sentenceHighlightBg: Color,
    val chapterBase: Int,
    val blockGlobalOffsets: List<Int>,
    val ttsSentenceRangeInChapter: Pair<Int, Int>?,
    val focusBlockIndex: Int?,
    val epubBringRequester: BringIntoViewRequester,
)

@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberReaderEyeCareFocusState(
    readerSettings: ReaderSettings,
    paper: ReaderPaperPalette,
    showTts: Boolean,
    tts: TtsController,
    epubBook: EpubBook?,
    contentText: String,
    chapterStartOffsets: List<Int>,
    chapterIndex: Int,
    chapterBlocks: List<DocBlock>,
    navFocusBlockIndexState: MutableState<Int?>,
): ReaderEyeCareFocusState {
    // 护眼时间 currentMinute 由 ReaderRuntimeEffects 每分钟写入
    val currentMinuteState = remember { mutableIntStateOf(0) }
    var currentMinute by currentMinuteState
    val eyeCareActive = readerSettings.eyeCareFilterEnabled ||
        (readerSettings.eyeCareScheduleEnabled && EyeCareSchedule.isActive(
            currentMinute,
            readerSettings.eyeCareStartMinute,
            readerSettings.eyeCareEndMinute,
        ))
    val eyeRgb = remember(readerSettings.eyeCareTemperature) {
        EyeCareSchedule.rgbForKelvin(readerSettings.eyeCareTemperature)
    }
    val eyeFilterColor = Color(eyeRgb.first, eyeRgb.second, eyeRgb.third)

    // 朗读句高亮背景色（与 TXT 保持一致）：跟随纸张强调色（§4.3 accent @0.22）。
    val sentenceHighlightBg = paper.accent.copy(alpha = 0.22f)

    // T1/T2：当前章节各渲染块在全书文本中的全局偏移；以及 TTS 当前句在章节内的定位
    val chapterBase = chapterStartOffsets.getOrElse(chapterIndex) { 0 }
    val blockGlobalOffsets = remember(epubBook, chapterStartOffsets, chapterIndex, chapterBlocks) {
        if (epubBook != null) computeBlockGlobalOffsets(chapterBlocks, chapterBase) else emptyList()
    }
    val ttsSentenceRangeInChapter = if (showTts && tts.status != "idle" && epubBook != null && contentText.isNotBlank()) {
        tts.currentSentenceRange
    } else null
    val ttsSentenceBlockIndex = remember(blockGlobalOffsets, ttsSentenceRangeInChapter) {
        if (ttsSentenceRangeInChapter != null) {
            val s = ttsSentenceRangeInChapter.first
            var idx = -1
            for (i in blockGlobalOffsets.indices) {
                val o = blockGlobalOffsets[i]
                if (o >= 0 && o <= s) idx = i else if (o > s) break
            }
            idx
        } else null
    }
    // 滚动聚焦块：优先 TTS 当前句，否则导航精准定位（T1 跳转用）
    var navFocusBlockIndex by navFocusBlockIndexState
    val focusBlockIndex = ttsSentenceBlockIndex ?: navFocusBlockIndex
    val epubBringRequester = remember { BringIntoViewRequester() }

    return ReaderEyeCareFocusState(
        currentMinuteState = currentMinuteState,
        eyeCareActive = eyeCareActive,
        eyeFilterColor = eyeFilterColor,
        sentenceHighlightBg = sentenceHighlightBg,
        chapterBase = chapterBase,
        blockGlobalOffsets = blockGlobalOffsets,
        ttsSentenceRangeInChapter = ttsSentenceRangeInChapter,
        focusBlockIndex = focusBlockIndex,
        epubBringRequester = epubBringRequester,
    )
}
