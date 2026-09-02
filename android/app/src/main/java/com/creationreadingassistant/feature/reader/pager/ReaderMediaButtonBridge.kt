package com.creationreadingassistant.feature.reader.pager

import android.content.Context
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.core.content.ContextCompat

/**
 * 非 TTS 状态下的阅读器媒体按钮桥：将蓝牙翻页器、耳机线控的 Media NEXT/PREVIOUS
 * 按钮映射为阅读器翻页动作。
 *
 * 设计要点：
 * 1. 与 TTS 独立使用各自 MediaSession，Android 媒体按钮派发天然按「最近激活 session」
 *    竞争 —— TTS 激活时其 session（带 ongoing notification + STATE_PLAYING）优先级
 *    更高，自动接管；本 Bridge session 在非 TTS 时生效；
 * 2. 通过 [ReaderHardwareKeys.triggerDirection] 复用音量键翻页的防抖与 handler，
 *    不重复实现翻页逻辑；
 * 3. 阅读器退出（onCleared）必须 [release]，避免 MediaSession 泄漏。
 *
 * 映射关系：
 * - KEYCODE_MEDIA_NEXT / onSkipToNext → 下一页 (direction=1)
 * - KEYCODE_MEDIA_PREVIOUS / onSkipToPrevious → 上一页 (direction=-1)
 * - KEYCODE_MEDIA_PLAY_PAUSE / onPlay / onPause → 预留（当前不做动作，返回 true
 *   以阻止系统把焦点转给其他音乐 App）
 */
class ReaderMediaButtonBridge(context: Context) {

    private val appContext = context.applicationContext
    private var mediaSession: MediaSession? = null

    /**
     * 激活 MediaSession 开始监听媒体按钮。
     * 可重复调用（阅读器在前台后重新声明焦点）。
     */
    fun activate() {
        val session = mediaSession ?: run {
            val s = MediaSession(appContext, TAG).apply {
                @Suppress("DEPRECATION")
                setFlags(
                    MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                            or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                setCallback(object : MediaSession.Callback() {
                    override fun onSkipToNext() {
                        ReaderHardwareKeys.triggerDirection(1)
                    }

                    override fun onSkipToPrevious() {
                        ReaderHardwareKeys.triggerDirection(-1)
                    }

                    override fun onPlay() {
                        // 预留：防止系统将焦点转走，不触发翻页
                    }

                    override fun onPause() {
                        // 预留：同上
                    }
                })
                // 声明可用动作：只有 SKIP_TO_NEXT / SKIP_TO_PREVIOUS 真正做翻页；
                // PLAY_PAUSE 留作焦点锚点，避免其他 App 抢走媒体按钮。
                val actions =
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_PLAY_PAUSE
                setPlaybackState(
                    PlaybackState.Builder()
                        .setState(PlaybackState.STATE_NONE, 0L, 0f)
                        .setActions(actions)
                        .build()
                )
            }
            mediaSession = s
            s
        }
        try {
            // 重新声明为活跃 session（TTS 停止后需要重新抢焦点）
            session.isActive = true
        } catch (_: Exception) {
        }
    }

    /** 临时失活（例如 TTS 激活时主动让出，避免两个 session 竞争）。 */
    fun deactivate() {
        try {
            mediaSession?.isActive = false
        } catch (_: Exception) {
        }
    }

    /** 阅读器退出时释放，必须调用以避免 MediaSession 泄漏。 */
    fun release() {
        try {
            mediaSession?.release()
        } catch (_: Exception) {
        }
        mediaSession = null
    }

    companion object {
        private const val TAG = "ReaderMediaButton"
    }
}
