package com.creationreadingassistant.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.creationreadingassistant.feature.dictionary.DictionaryLookupEntry
import com.creationreadingassistant.feature.dictionary.DictionaryLookupOutcome
import com.creationreadingassistant.feature.dictionary.DictionaryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DictionaryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<DictionaryRepository>()
    private lateinit var vm: DictionaryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.installed() } returns emptyList()
        vm = DictionaryViewModel(repository)
    }

    @After
    fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    // Deliberately non-cooperative: cancellation alone cannot finish this repository call.
    private class LookupGate {
        private var pending: Continuation<DictionaryLookupOutcome>? = null
        val isWaiting: Boolean get() = pending != null

        suspend fun await(): DictionaryLookupOutcome = suspendCoroutine { pending = it }

        fun complete(value: DictionaryLookupOutcome) {
            val continuation = checkNotNull(pending)
            pending = null
            continuation.resume(value)
        }

        fun fail(failure: Exception) {
            val continuation = checkNotNull(pending)
            pending = null
            continuation.resumeWithException(failure)
        }
    }

    private fun outcome(word: String) = DictionaryLookupOutcome(
        entries = listOf(DictionaryLookupEntry("测试词库", word, "测试释义", 'm')),
        scannedDictionaries = 1,
    )

    @Test
    fun lateOldSuccessCannotReplaceNewWordAndResults() = runTest(dispatcher) {
        val old = LookupGate()
        coEvery { repository.lookup("A", any()) } coAnswers { old.await() }
        coEvery { repository.lookup("B", any()) } returns outcome("B")

        vm.lookup("A")
        advanceUntilIdle()
        assertTrue(old.isWaiting)
        try {
            vm.lookup("B")
            advanceUntilIdle()
            val newer = vm.state.value
            assertEquals("B", newer.word)
            assertEquals(listOf("B"), newer.results.map { it.word })
            assertFalse(newer.loading)
            old.complete(outcome("A"))
            advanceUntilIdle()
            assertEquals(newer, vm.state.value)
        } finally {
            if (old.isWaiting) old.complete(outcome("A"))
        }
    }

    @Test
    fun lateOldFailureCannotReplaceNewSuccess() = runTest(dispatcher) {
        val old = LookupGate()
        coEvery { repository.lookup("A", any()) } coAnswers { old.await() }
        coEvery { repository.lookup("B", any()) } returns outcome("B")

        vm.lookup("A")
        advanceUntilIdle()
        try {
            vm.lookup("B")
            advanceUntilIdle()
            val newer = vm.state.value
            old.fail(IOException("old read failed"))
            advanceUntilIdle()
            assertEquals(newer, vm.state.value)
            assertNull(vm.state.value.error)
        } finally {
            if (old.isWaiting) old.complete(outcome("A"))
        }
    }

    @Test
    fun cancellationIsNotReportedAsDictionaryIoFailure() = runTest(dispatcher) {
        coEvery { repository.lookup("cancel", any()) } throws CancellationException("cancelled")
        vm.lookup("cancel")
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.searched)
        assertTrue(vm.state.value.results.isEmpty())
        vm.lookup("")
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun realRepositoryFailureIsVisibleToTheUser() = runTest(dispatcher) {
        coEvery { repository.lookup("word", any()) } throws IOException("private path must not leak")
        vm.lookup("word")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("word", state.word)
        assertTrue(state.searched)
        assertFalse(state.loading)
        assertTrue(state.results.isEmpty())
        val error = checkNotNull(state.error)
        assertTrue(error.contains("IOException"))
        assertFalse(error.contains("private path"))
        assertEquals(state.error, dictionaryEmptyNotice(state))
    }

    @Test
    fun blankSelectionInvalidatesAnInFlightLookup() = runTest(dispatcher) {
        val old = LookupGate()
        coEvery { repository.lookup("A", any()) } coAnswers { old.await() }
        vm.lookup("A")
        advanceUntilIdle()
        try {
            vm.lookup("  ")
            val blank = vm.state.value
            assertEquals("", blank.word)
            assertTrue(blank.searched)
            assertFalse(blank.loading)
            old.complete(outcome("A"))
            advanceUntilIdle()
            assertEquals(blank, vm.state.value)
            coVerify(exactly = 0) { repository.lookup("", any()) }
        } finally {
            if (old.isWaiting) old.complete(outcome("A"))
        }
    }

    @Test
    fun cancelledViewModelScopeCannotPublishLateResults() = runTest(dispatcher) {
        val old = LookupGate()
        coEvery { repository.lookup("A", any()) } coAnswers { old.await() }
        vm.lookup("A")
        advanceUntilIdle()
        try {
            val beforeCancellation = vm.state.value
            vm.viewModelScope.cancel()
            old.complete(outcome("A"))
            advanceUntilIdle()
            assertEquals(beforeCancellation, vm.state.value)
            assertNull(vm.state.value.error)
        } finally {
            if (old.isWaiting) old.complete(outcome("A"))
        }
    }
}
