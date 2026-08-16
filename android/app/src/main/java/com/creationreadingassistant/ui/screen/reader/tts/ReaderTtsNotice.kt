package com.creationreadingassistant.ui.screen.reader.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.creationreadingassistant.feature.log.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * TTS 错误 → 现有 Snackbar notice 的 Android UI 适配器，并提供「打开系统文字转语音设置」动作。
 *
 * 只持有 applicationContext（不持有 Activity）：设置页以 NEW_TASK 从应用上下文启动，
 * 适配器生命周期与组合一致，不构成 Activity 泄漏。
 */
internal class TtsNoticeAdapter(
    private val scope: CoroutineScope,
    private val snackbarHost: SnackbarHostState,
    appContext: Context,
) {
    private val appContext = appContext.applicationContext
    private val settingsIntent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 把一次 TTS 错误显示为现有 notice；用户点「去设置」时打开系统 TTS 设置。 */
    fun showError(message: String) {
        scope.launch {
            val result = snackbarHost.showSnackbar(
                message = message,
                actionLabel = "去设置",
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) openSystemTtsSettings()
        }
    }

    /** 打开系统「文字转语音」设置页。 */
    fun openSystemTtsSettings() {
        runCatching { appContext.startActivity(settingsIntent) }
            .onFailure { AppLog.w("Tts", "open TTS settings failed: ${it.message}") }
    }
}

/**
 * 监听 TTS 错误序列：每次引擎错误（[TtsStatus.errorSeq] 递增）消费一次并显示为现有 notice。
 * 消费语义与 [TtsStatus.consumeError] 一致：每个错误事件只展示一次。
 */
@Composable
internal fun TtsErrorNoticeEffect(tts: TtsStatus, adapter: TtsNoticeAdapter) {
    val errorSeq = tts.errorSeq
    LaunchedEffect(errorSeq) {
        if (errorSeq > 0) {
            val failure = tts.consumeError() ?: return@LaunchedEffect
            adapter.showError(failure.message)
        }
    }
}
