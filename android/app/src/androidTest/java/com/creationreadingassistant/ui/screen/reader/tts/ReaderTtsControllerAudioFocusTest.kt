package com.creationreadingassistant.ui.screen.reader.tts

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.PlaybackState
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 98-v2: instantiate the production Controller on Android's main Looper.
 * Only framework audio, speech output and MediaSession boundaries are mocked.
 * This proves Controller wiring with controlled callbacks, not system arbitration or audible output.
 * Uses the project's existing AndroidJUnitRunner and mockk-android dependency.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class ReaderTtsControllerAudioFocusTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val audio = mockk<AudioManager>(relaxed = true)
    private val speech = mockk<TextToSpeech>(relaxed = true)
    private val media = mockk<TtsMediaSession>(relaxed = true)
    private val request = slot<AudioFocusRequest>()
    private val utterance = slot<UtteranceProgressListener>()
    private lateinit var init: TextToSpeech.OnInitListener
    private lateinit var focusChanged: (Int) -> Unit
    private lateinit var controller: TtsController
    private var focusResult = AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    private var sentenceEvents = 0
    private var finishedEvents = 0

    @Before
    fun setUp() {
        every { audio.requestAudioFocus(capture(request)) } answers { focusResult }
        every { speech.setLanguage(any()) } returns TextToSpeech.LANG_AVAILABLE
        every { speech.voices } returns emptySet()
        every { speech.setOnUtteranceProgressListener(capture(utterance)) } returns TextToSpeech.SUCCESS
        every { speech.speak(any<CharSequence>(), any(), isNull(), any()) } returns TextToSpeech.SUCCESS
        onMain {
            controller = TtsController(
                context = instrumentation.targetContext,
                audioManager = audio,
                createTts = { _, listener -> init = listener; speech },
                createMedia = { _, _, _, _, _, _ -> media },
                createFocusListener = { callback ->
                    focusChanged = callback
                    AudioManager.OnAudioFocusChangeListener { callback(it) }
                },
            )
            controller.onSentence = { _, _ -> sentenceEvents++ }
            controller.onFinished = { finishedEvents++ }
            init.onInit(TextToSpeech.SUCCESS)
        }
        drain()
        assertEquals(TtsAvailability.Ready, controller.availability)
        assertTrue(utterance.isCaptured)
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) onMain { controller.release() }
        drain()
    }

    @Test
    fun failedPlayAndResumeRejectWithoutSpeechOrPlayingAndCanRetry() {
        focusResult = AudioManager.AUDIOFOCUS_REQUEST_FAILED
        var rejected: TtsPlayResult? = null
        onMain { rejected = controller.play(TEXT) }
        assertTrue(rejected is TtsPlayResult.Rejected)
        assertTrue((rejected as TtsPlayResult.Rejected).reason.contains("音频焦点"))
        assertEquals(TtsPlayback.Idle, controller.playback)
        assertEquals(TtsAvailability.Ready, controller.availability)
        assertNoSpeechOrPlaying()

        onMain { controller.resume() }
        assertEquals(TtsPlayback.Idle, controller.playback)
        assertEquals(1, controller.errorSeq)
        assertTrue(controller.consumeError()!!.message.contains("音频焦点"))
        assertEquals(null, controller.consumeError())
        assertNoSpeechOrPlaying()

        focusResult = AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        onMain { controller.resume() }
        verify(exactly = 1) { speech.speak(any<CharSequence>(), any(), isNull(), any()) }
        verify(exactly = 1) { media.updateState(PlaybackState.STATE_PLAYING) }
        assertEquals(TtsAvailability.Ready, controller.availability)
    }

    @Test
    fun delayedResultIsRejectedAndLaterGainDoesNotStartPlayback() {
        focusResult = AudioManager.AUDIOFOCUS_REQUEST_DELAYED
        var result: TtsPlayResult? = null
        onMain { result = controller.play(TEXT) }
        assertTrue(result is TtsPlayResult.Rejected)
        assertFalse(request.captured.acceptsDelayedFocusGain())
        onMain { focusChanged(AudioManager.AUDIOFOCUS_GAIN) }
        drain()
        assertNoSpeechOrPlaying()
        assertEquals(TtsPlayback.Idle, controller.playback)
    }

    @Test
    fun grantedPlayUsesRealSpeechPathAndEngineStartMarksPlaying() {
        var result: TtsPlayResult? = null
        onMain { result = controller.play(TEXT) }
        assertEquals(TtsPlayResult.Accepted, result)
        verify(exactly = 1) { speech.speak("测试一句。", TextToSpeech.QUEUE_FLUSH, isNull(), "tts-0") }
        verify(exactly = 1) { media.updateState(PlaybackState.STATE_PLAYING) }
        assertEquals(1, sentenceEvents)
        onMain { utterance.captured.onStart("tts-0") }
        drain()
        assertEquals(TtsPlayback.Playing, controller.playback)
        assertNotNull(controller.currentSentenceRange)
    }

    @Test
    fun transientLossThenGainResumesThroughRealRequestAndSpeak() {
        startPlaying()
        onMain { focusChanged(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) }
        assertEquals(TtsPlayback.Paused, controller.playback)
        onMain { focusChanged(AudioManager.AUDIOFOCUS_GAIN) }
        verify(exactly = 2) { audio.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 2) { speech.speak(any<CharSequence>(), any(), isNull(), any()) }
        onMain { utterance.captured.onStart("tts-0") }
        drain()
        assertEquals(TtsPlayback.Playing, controller.playback)
    }

    @Test
    fun stopAfterTransientLossCancelsAutomaticGainResume() {
        startPlaying()
        onMain {
            focusChanged(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            controller.stop()
            focusChanged(AudioManager.AUDIOFOCUS_GAIN)
        }
        drain()
        assertEquals(TtsPlayback.Idle, controller.playback)
        verify(exactly = 1) { audio.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 1) { speech.speak(any<CharSequence>(), any(), isNull(), any()) }
        verify(atLeast = 1) { audio.abandonAudioFocusRequest(any()) }
    }

    @Test
    fun failedFocusCannotBeBypassedByNextOrPrevious() {
        focusResult = AudioManager.AUDIOFOCUS_REQUEST_FAILED
        onMain {
            controller.play(TEXT)
            controller.next()
            controller.prev()
            controller.updateSpeechRate(1.5f)
            controller.updatePitch(1.5f)
            controller.updateVoiceId("test")
        }
        assertNoSpeechOrPlaying()
        assertEquals(0, sentenceEvents)
        assertEquals(0, finishedEvents)
    }

    @Test
    fun queuedStartAndDoneAfterLossDoNotMarkPlayingOrAdvanceSentence() {
        startPlaying()
        // Both callbacks post to the real Handler; LOSS occurs before those posted blocks execute.
        onMain {
            utterance.captured.onStart("tts-0")
            utterance.captured.onDone("tts-0")
            focusChanged(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        }
        drain()
        assertEquals(TtsPlayback.Paused, controller.playback)
        assertEquals(1, sentenceEvents)
        assertEquals(0, finishedEvents)
        onMain { controller.next(); controller.prev() }
        verify(exactly = 1) { speech.speak(any<CharSequence>(), any(), isNull(), any()) }
        // play() and the first valid onStart(), never the queued stale onStart().
        verify(exactly = 2) { media.updateState(PlaybackState.STATE_PLAYING) }
    }

    @Test
    fun queuedCallbacksAfterStopDoNotAdvanceOrFinishLastSentence() {
        onMain { controller.play("测试一句。") }
        onMain {
            utterance.captured.onStart("tts-0")
            utterance.captured.onDone("tts-0")
            controller.stop()
            focusChanged(AudioManager.AUDIOFOCUS_GAIN)
        }
        drain()
        assertEquals(TtsPlayback.Idle, controller.playback)
        assertEquals(1, sentenceEvents)
        assertEquals(0, finishedEvents)
        verify(exactly = 1) { speech.speak(any<CharSequence>(), any(), isNull(), any()) }
        verify(exactly = 1) { media.updateState(PlaybackState.STATE_PLAYING) }
    }

    @Test
    fun timedStopLifecycleOnRealController() {
        // 102-v1：真实 Controller + 真实 Handler 的定时停止状态机（外部 TTS/Audio 仍为 mock）。
        // 到期后的淡出/stop 语义由纯 JVM 生产协调器测试覆盖；本用例核 Controller 的设置、
        // 关闭、暂停、恢复、释放对计时器的真实接线，不等待真实分钟。
        onMain { controller.setTimedStop(15) }
        startPlaying()
        assertEquals(15, controller.timedStopMinutes)
        assertTrue(
            "15 分钟定时必须在真实 Controller 上武装，实际剩余 ${controller.timedStopRemainingSeconds}",
            controller.timedStopRemainingSeconds in 850..900,
        )

        onMain { controller.setTimedStop(0) }
        drain()
        assertEquals(0, controller.timedStopMinutes)
        assertEquals("关闭定时不得停止当前播放", TtsPlayback.Playing, controller.playback)
        assertEquals(0, controller.timedStopRemainingSeconds)

        onMain { controller.setTimedStop(15) }
        drain()
        assertTrue(controller.timedStopRemainingSeconds in 850..900)

        onMain { controller.pause() }
        drain()
        assertEquals(TtsPlayback.Paused, controller.playback)
        assertEquals("暂停必须清除计时", 0, controller.timedStopRemainingSeconds)

        onMain { controller.resume() }
        // 与 play 相同：mock 的 TTS 不会自行回调，需显式投递一次 onStart，
        // 模拟真实引擎在 resume 后异步进入播放态；不使用 sleep 猜测时序。
        onMain { utterance.captured.onStart("tts-0") }
        drain()
        assertEquals(TtsPlayback.Playing, controller.playback)
        assertTrue(
            "恢复按既有语义重新武装完整时长，实际 ${controller.timedStopRemainingSeconds}",
            controller.timedStopRemainingSeconds in 850..900,
        )

        onMain { controller.release() }
        drain()
        assertEquals("释放后不得保留剩余计时", 0, controller.timedStopRemainingSeconds)
    }

    private fun startPlaying() {
        onMain { controller.play(TEXT) }
        onMain { utterance.captured.onStart("tts-0") }
        drain()
        assertEquals(TtsPlayback.Playing, controller.playback)
    }

    private fun assertNoSpeechOrPlaying() {
        verify(exactly = 0) { speech.speak(any<CharSequence>(), any(), isNull(), any()) }
        verify(exactly = 0) { media.updateState(PlaybackState.STATE_PLAYING) }
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync { action() }
    private fun drain() = instrumentation.waitForIdleSync()

    companion object {
        private const val TEXT = "测试一句。测试二句。"
    }
}
