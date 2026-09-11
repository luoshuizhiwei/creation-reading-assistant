package com.creationreadingassistant.feature.reader.navigation

import com.creationreadingassistant.feature.reader.locator.LocatorBuilder
import com.creationreadingassistant.feature.reader.locator.ReaderLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceNavigationContractTest {

    @Test
    fun `nested v2 locator wins over chapter local legacy offset`() {
        val progress = LocatorBuilder.progressJson(
            legacyOffset = 7,
            chapterIndex = 4,
            charOffset = 2,
            globalOffset = 900,
            legacyChapter = 4,
        )

        val target = SourceNavigationContract.targetFromStoredLocation("book-1", progress)

        requireNotNull(target)
        assertEquals("book-1", target.bookId)
        assertEquals(900, target.locator.legacyOffset)
        assertEquals(4, target.locator.chapterIndex)
        assertEquals(2, target.locator.charOffset)
    }

    @Test
    fun `standalone locator is accepted and incomplete coordinates are normalized away`() {
        val target = SourceNavigationContract.target(
            bookId = " book-2 ",
            locator = ReaderLocator(legacyOffset = 12, chapterIndex = 1, charOffset = null, excerptFingerprint = null),
        )

        requireNotNull(target)
        assertEquals("book-2", target.bookId)
        assertEquals(12, target.locator.legacyOffset)
        assertNull(target.locator.chapterIndex)
        assertNull(target.locator.charOffset)
    }

    @Test
    fun `missing source coordinates and blank book ids are rejected`() {
        assertNull(SourceNavigationContract.targetFromStoredLocation("book-3", "{}"))
        assertNull(SourceNavigationContract.target(" ", ReaderLocator(0, null, null, null)))
        assertNull(SourceNavigationContract.target("book-3", ReaderLocator(-1, null, null, null)))
        assertNull(SourceNavigationContract.target("book-3", ReaderLocator(null, -1, 3, null)))
    }

    @Test
    fun `plain navigation preserves only the source absolute offset`() {
        val target = target("book-1", 321)

        assertEquals(
            SourceNavigationPosition(absoluteOffset = 321),
            SourceNavigationContract.resolvePlainPosition(target),
        )
    }

    @Test
    fun `chaptered navigation derives global position from a complete chapter coordinate`() {
        val target = requireNotNull(
            SourceNavigationContract.target("book-1", ReaderLocator(null, 1, 20, null)),
        )

        assertEquals(
            SourceNavigationPosition(absoluteOffset = 120, chapterIndex = 1, chapterOffset = 20),
            SourceNavigationContract.resolveChapteredPosition(target, chapterStartOffsets = listOf(0, 100)),
        )
    }

    @Test
    fun `chaptered navigation rejects conflicting global and chapter coordinates`() {
        val target = target("book-1", offset = 10).copy(
            locator = ReaderLocator(legacyOffset = 10, chapterIndex = 1, charOffset = 20, excerptFingerprint = null),
        )

        assertNull(SourceNavigationContract.resolveChapteredPosition(target, chapterStartOffsets = listOf(0, 100)))
    }

    @Test
    fun `chaptered navigation can prefer a canonical global offset over legacy chapter zero metadata`() {
        val target = requireNotNull(
            SourceNavigationContract.target(
                "book-1",
                ReaderLocator(legacyOffset = 140, chapterIndex = 0, charOffset = 140, excerptFingerprint = null),
            ),
        )

        assertEquals(
            SourceNavigationPosition(absoluteOffset = 140, chapterIndex = 1, chapterOffset = 40),
            SourceNavigationContract.resolveChapteredPosition(
                target,
                chapterStartOffsets = listOf(0, 100),
                preferGlobalOffset = true,
            ),
        )
    }

    @Test
    fun `ordinary progress is durable and does not create a temporary return path`() {
        val normal = target("book-1", 100)

        val state = SourceNavigationContract.recordNormalReading(SourceNavigationState(), normal)

        assertEquals(normal, state.active)
        assertEquals(normal, state.normalReading)
        assertEquals(emptyList<SourceNavigationTarget>(), state.temporaryReturnStack)
    }

    @Test
    fun `temporary inspection returns in LIFO order without replacing normal reading`() {
        val normal = target("book-1", 100)
        val firstInspection = target("book-2", 200)
        val secondInspection = target("book-3", 300)
        val normalState = SourceNavigationContract.recordNormalReading(SourceNavigationState(), normal)

        val nested = SourceNavigationContract.beginTemporaryInspection(
            SourceNavigationContract.beginTemporaryInspection(normalState, firstInspection),
            secondInspection,
        )
        val afterFirstReturn = SourceNavigationContract.returnFromTemporaryInspection(nested)
        val afterSecondReturn = SourceNavigationContract.returnFromTemporaryInspection(afterFirstReturn)

        assertEquals(normal, nested.normalReading)
        assertEquals(listOf(normal, firstInspection), nested.temporaryReturnStack)
        assertEquals(firstInspection, afterFirstReturn.active)
        assertEquals(listOf(normal), afterFirstReturn.temporaryReturnStack)
        assertEquals(normal, afterSecondReturn.active)
        assertEquals(emptyList<SourceNavigationTarget>(), afterSecondReturn.temporaryReturnStack)
    }

    @Test
    fun `restart discards temporary inspection and restores only normal reading`() {
        val normal = target("book-1", 100)
        val temporary = target("book-2", 200)
        val state = SourceNavigationContract.beginTemporaryInspection(
            SourceNavigationContract.recordNormalReading(SourceNavigationState(), normal),
            temporary,
        )

        val restored = SourceNavigationContract.restoreAfterRestart(state)

        assertEquals(normal, restored.active)
        assertEquals(normal, restored.normalReading)
        assertEquals(emptyList<SourceNavigationTarget>(), restored.temporaryReturnStack)
    }

    @Test
    fun `temporary history is bounded to recent inspection origins`() {
        val normal = target("book-1", 10)
        val first = target("book-2", 20)
        val second = target("book-3", 30)
        val third = target("book-4", 40)
        val state = SourceNavigationContract.recordNormalReading(SourceNavigationState(), normal)

        val bounded = SourceNavigationContract.beginTemporaryInspection(
            SourceNavigationContract.beginTemporaryInspection(
                SourceNavigationContract.beginTemporaryInspection(state, first, maxTemporaryHistory = 2),
                second,
                maxTemporaryHistory = 2,
            ),
            third,
            maxTemporaryHistory = 2,
        )

        assertEquals(listOf(first, second), bounded.temporaryReturnStack)
    }

    private fun target(bookId: String, offset: Int): SourceNavigationTarget =
        requireNotNull(SourceNavigationContract.target(bookId, ReaderLocator(offset, 0, offset, null)))
}
