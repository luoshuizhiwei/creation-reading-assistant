package com.creationreadingassistant.ui.screen.reader

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSessionTimerTest {

    @Test
    fun `timer only counts effective foreground reading and emits heartbeats`() = runTest {
        val readingActive = mutableStateOf(false)
        val activeReadingMs = mutableLongStateOf(0L)
        var heartbeats = 0

        backgroundScope.launch {
            trackActiveReadingTime(readingActive, activeReadingMs) { heartbeats++ }
        }
        runCurrent()

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0L, activeReadingMs.longValue)

        readingActive.value = true
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1_000L, activeReadingMs.longValue)
        assertEquals(1, heartbeats)

        readingActive.value = false
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1_000L, activeReadingMs.longValue)
        assertEquals(1, heartbeats)

        readingActive.value = true
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2_000L, activeReadingMs.longValue)
        assertEquals(2, heartbeats)
    }
}
