package com.creationreadingassistant.ui.screen.reader.tts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.reader.doc.PlainTextDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * TTS 设置同步：首次将持久化的音调/音量/音色/定时停止载入控制器（仅一次，避免播放中回灌导致重读）。
 */
@Composable
internal fun TtsSettingsSyncEffect(
    tts: TtsController,
    readerSettings: ReaderSettings,
) {
    var ttsSynced by remember { mutableStateOf(false) }
    LaunchedEffect(readerSettings) {
        if (!ttsSynced) {
            tts.pitch = readerSettings.ttsPitch
            tts.volume = readerSettings.ttsVolume
            tts.voiceId = readerSettings.ttsVoiceId
            tts.setTimedStop(readerSettings.ttsTimedStopMinutes)
            ttsSynced = true
        }
    }
}

/**
 * TTS 跨会话续读：加载本书上次朗读句偏移；并把句变化持久化（含本会话续读偏移）。
 */
@Composable
internal fun TtsResumeEffect(
    tts: TtsController,
    bookId: String,
    settingsStore: SettingsStore,
    isEpub: Boolean,
    chapterIndex: Int,
    onResumeOffsetChanged: (Int) -> Unit,
    onResumeChapterChanged: (Int) -> Unit,
) {
    val bid = bookId
    LaunchedEffect(bid) {
        val r = runCatching { settingsStore.loadTtsResume() }.getOrNull()
        onResumeOffsetChanged(if (r?.bookId == bid) r.offset else 0)
        onResumeChapterChanged(if (r?.bookId == bid) r.chapterIndex else -1)
        tts.onSentence = { start, _ ->
            onResumeOffsetChanged(start)
            val resumeChapter = if (isEpub) chapterIndex else -1
            onResumeChapterChanged(resumeChapter)
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching { settingsStore.saveTtsResume(bid, resumeChapter, start) }
            }
        }
    }
}

/**
 * TTS 朗读同步：朗读时把正文跟到当前句（TXT 与 EPUB 分页），与网页 ttsSyncToReader 对齐。
 */
@Composable
internal fun TtsReaderSyncEffect(
    tts: TtsController,
    showTts: Boolean,
    isTxt: Boolean,
    plainContent: String,
    txtStreamingDocument: PlainTextDocument?,
    visiblePlainOffset: Int,
    jumpToPlainOffset: (Int) -> Unit,
    pagerEngineOn: Boolean,
    isEpub: Boolean,
    pagedJumpTo: (Int) -> Unit,
    chapterStartOffsets: List<Int>,
    chapterIndex: Int,
) {
    LaunchedEffect(tts.status, tts.currentSentenceRange) {
        if (showTts && tts.status != "idle") {
            val start = tts.currentSentenceRange.first
            if (isTxt && (plainContent.isNotEmpty() || txtStreamingDocument != null)) {
                val ttsTotalLen = txtStreamingDocument?.totalChars ?: plainContent.length
                // 流式 TXT：contentText 是 readWindowAround(visiblePlainOffset, 0, 8000) 的窗口
                // currentSentenceRange 是窗口内偏移，加 visiblePlainOffset 转全书偏移
                val ttsBase = if (txtStreamingDocument != null) {
                    visiblePlainOffset
                } else 0
                val globalStart = ttsBase + start
                if (globalStart in 0 until ttsTotalLen) {
                    jumpToPlainOffset(globalStart)
                }
            } else if (pagerEngineOn && isEpub) {
                pagedJumpTo(chapterStartOffsets.getOrElse(chapterIndex) { 0 } + start)
            }
        }
    }
}
