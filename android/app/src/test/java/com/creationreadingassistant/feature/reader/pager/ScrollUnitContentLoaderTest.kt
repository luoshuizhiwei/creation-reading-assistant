package com.creationreadingassistant.feature.reader.pager

import com.creationreadingassistant.feature.reader.doc.ReadingUnit
import com.creationreadingassistant.feature.reader.rules.ScrollUnitProjection
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScrollUnitContentLoaderTest {
    private val sourceA = Any()
    private val sourceB = Any()
    private val ioExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "scroll-projection-io").apply { isDaemon = true }
    }
    private val ioDispatcher = ioExecutor.asCoroutineDispatcher()

    @After
    fun tearDown() {
        ioExecutor.shutdown()
    }

    @Test
    fun `projection load runs off main and publishes coordinate-aware content`() = runTest(timeout = 60.seconds) {
        val loader = ScrollUnitContentLoader(ioDispatcher = ioDispatcher)
        var threadName: String? = null
        val projection = identityProjection("source")

        loader.load(sourceA, 0) {
            threadName = Thread.currentThread().name
            projection
        }

        assertEquals("scroll-projection-io", threadName)
        val loaded = loader.stateFor(sourceA, 0).value as ScrollUnitContentState.Loaded
        assertEquals("source", loaded.content.displayText)
        assertEquals(6, loaded.content.localDisplayToGlobalSource(1))
    }

    @Test
    fun `switch source discards an old in-flight projection`() = runTest(timeout = 60.seconds) {
        val loader = ScrollUnitContentLoader(ioDispatcher = ioDispatcher)
        val oldState = loader.stateFor(sourceA, 0)
        val gate = CompletableDeferred<Unit>()
        val readStarted = CompletableDeferred<Unit>()
        val old = async {
            loader.load(sourceA, 0) {
                readStarted.complete(Unit)
                gate.await()
                identityProjection("old")
            }
        }
        readStarted.await()
        loader.switchSource(sourceB)
        gate.complete(Unit)
        old.await()

        assertEquals(ScrollUnitContentState.Loading, oldState.value)
    }

    @Test
    fun `failed projection stays sticky until explicit retry`() = runTest(timeout = 60.seconds) {
        val loader = ScrollUnitContentLoader(ioDispatcher = ioDispatcher)
        val failedLoad = async { loader.load(sourceA, 0) { throw IOException("failed") } }
        failedLoad.await()
        loader.release(sourceA, 0)
        var automaticReads = 0
        loader.load(sourceA, 0) {
            automaticReads += 1
            identityProjection("ignored")
        }
        assertEquals(0, automaticReads)
        assertTrue(loader.stateFor(sourceA, 0).value is ScrollUnitContentState.Failed)

        val retryLoad = async { loader.retry(sourceA, 0) { identityProjection("retry") } }
        retryLoad.await()
        val loaded = loader.stateFor(sourceA, 0).value as ScrollUnitContentState.Loaded
        assertEquals("retry", loaded.content.displayText)
    }

    private fun identityProjection(text: String): ScrollUnitProjection =
        requireNotNull(
            ScrollUnitProjection.fromSource(
                ReadingUnit(
                    unitIndex = 0,
                    chapterIndex = 0,
                    title = "chapter",
                    charStart = 5,
                    charCount = text.length,
                ),
                text,
            ),
        )
}
