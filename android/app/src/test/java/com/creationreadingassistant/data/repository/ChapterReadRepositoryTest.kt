package com.creationreadingassistant.data.repository

import com.creationreadingassistant.data.local.dao.ChapterReadDao
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterReadRepositoryTest {

    private val dao = mockk<ChapterReadDao>(relaxed = true)
    private val repository = ChapterReadRepository(dao)

    @Test
    fun `markRead upserts chapter identity with an ISO timestamp`() = runTest {
        repository.markRead("book-1", 3)

        val entity = slot<com.creationreadingassistant.data.local.entity.ChapterReadEntity>()
        coVerify(exactly = 1) { dao.upsert(capture(entity)) }
        assertEquals("book-1", entity.captured.book_id)
        assertEquals(3, entity.captured.chapter_index)
        Instant.parse(entity.captured.read_at)
    }

    @Test
    fun `clearForBook delegates to dao`() = runTest {
        repository.clearForBook("book-1")

        coVerify(exactly = 1) { dao.clearForBook("book-1") }
    }
}
