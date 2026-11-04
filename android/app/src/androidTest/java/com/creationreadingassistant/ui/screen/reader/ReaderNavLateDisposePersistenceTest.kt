package com.creationreadingassistant.ui.screen.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the real navigation-compose NavHost and production ReaderPlatformEffects lifecycle. */
@RunWith(AndroidJUnit4::class)
class ReaderNavLateDisposePersistenceTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController
    private val readerEntries = mutableStateListOf<String>()
    private val events = mutableStateListOf<String>()

    @Test
    fun poppedReaderDisposeDoesNotPersistAfterRapidSameBookReentrySaved() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val controller = rememberNavController()
            SideEffect { nav = controller }
            val entries = remember { readerEntries }
            val trace = remember { events }
            NavHost(controller, startDestination = "shelf") {
                composable("shelf") { Text("shelf") }
                composable("next") { Text("next") }
                composable("reader/{bookId}") { entry ->
                    val entryId = entry.id
                    SideEffect {
                        if (entryId !in entries) entries.add(entryId)
                    }
                    ReaderPlatformEffects(
                        keepAwake = false,
                        immersiveMode = false,
                        controlsVisible = false,
                        paperIsLight = true,
                        appDark = false,
                        readerBrightness = -1,
                        paperBgColor = Color.White,
                        volumeKeyPaging = false,
                        onVolumeUp = { false },
                        onVolumeDown = { false },
                        screenOrientation = "system",
                        onReaderResumed = {},
                        onPersistProgress = { trace.add("persist:$entryId") },
                        // CRA-READER-EXIT-SAVE-DRAIN-v1：本测试只验证 dispose 时序，
                        // 每个入口用自己的防重复门实例即可（不与生产退出边界共享）。
                        persistGate = ReaderLifecyclePersistGate(),
                        onReaderSessionEnded = { trace.add("dispose:$entryId") },
                        controlsVisibleForAutoHide = false,
                        autoHideSeconds = 0,
                        sheetOpenGuard = false,
                        autoHideInteractionRevision = 0,
                        onAutoHide = {},
                    )
                    Text("reader-$entryId")
                }
            }
        }
        compose.waitForIdle()

        compose.runOnIdle { nav.navigate("reader/book-a") }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.waitUntil(5_000) { readerEntries.isNotEmpty() }
        compose.mainClock.advanceTimeBy(1_000L)
        compose.waitForIdle()
        val oldEntry = readerEntries.first()

        // Start a real pop transition, then re-enter the same book before advancing Compose time.
        compose.runOnIdle { nav.popBackStack() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(100L)
        compose.waitForIdle()
        assertFalse("popped route must still be in its real exit Crossfade; events=$events",
            events.contains("dispose:$oldEntry"))
        compose.runOnIdle { nav.navigate("reader/book-a") }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.waitUntil(5_000) { readerEntries.any { it != oldEntry } }
        val newEntry = readerEntries.first { it != oldEntry }

        assertTrue("the popped reader must remain composed during the frozen NavHost Crossfade; events=$events",
            events.none { it == "dispose:$oldEntry" })
        assertTrue("old reader must lifecycle-save during the pop; events=$events",
            events.any { it == "persist:$oldEntry" })

        // Navigating away from the new reader exercises its real NavBackStackEntry lifecycle callback.
        compose.runOnIdle { nav.navigate("next") }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val firstNewSave = events.indexOf("persist:$newEntry")
        assertTrue("new reader must lifecycle-save after entry; events=$events", firstNewSave >= 0)
        assertTrue("every old reader lifecycle save must precede the new reader save; events=$events",
            events.withIndex().filter { it.value == "persist:$oldEntry" }.all { it.index < firstNewSave })
        assertFalse("old route must not have disposed before the transition clock advances; events=$events",
            events.contains("dispose:$oldEntry"))

        val savesBeforeOldDispose = events.filter { it.startsWith("persist:") }.toList()
        compose.mainClock.advanceTimeBy(1_000L)
        compose.waitForIdle()
        assertTrue("old route must eventually dispose after NavHost transition completion; events=$events",
            events.contains("dispose:$oldEntry"))
        assertEquals("disposing old route must not append a late save after the new reader save; events=$events",
            savesBeforeOldDispose, events.filter { it.startsWith("persist:") })
        println("F_LIFECYCLE_TRACE old=$oldEntry new=$newEntry events=${events.joinToString(",")}")
    }
}
