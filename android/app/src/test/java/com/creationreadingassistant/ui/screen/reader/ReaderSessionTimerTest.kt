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
    fun `timer observes loading and error changes after it starts`() = runTest {
        val isLoading = mutableStateOf(true)
        val error = mutableStateOf<String?>(null)
        val activeReadingMs = mutableLongStateOf(0L)

        backgroundScope.launch {
            trackActiveReadingTime(isLoading, error, activeReadingMs)
        }
        runCurrent()

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0L, activeReadingMs.longValue)

        isLoading.value = false
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1_000L, activeReadingMs.longValue)

        error.value = "load failed"
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1_000L, activeReadingMs.longValue)

        error.value = null
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2_000L, activeReadingMs.longValue)
    }
}
