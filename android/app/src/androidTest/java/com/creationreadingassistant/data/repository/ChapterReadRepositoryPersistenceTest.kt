package com.creationreadingassistant.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.creationreadingassistant.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterReadRepositoryPersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "chapter-read-repository-persistence-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun repeatedArrivalPersistsAsOneRowAndManualClearSurvivesReopen() = runTest {
        var database = openDatabase()
        ChapterReadRepository(database.chapterReadDao()).apply {
            markRead("book-1", 4)
            markRead("book-1", 4)
        }
        database.close()

        database = openDatabase()
        val repository = ChapterReadRepository(database.chapterReadDao())
        assertEquals(1, database.chapterReadDao().readCount("book-1"))
        assertEquals(listOf(4), repository.observeReadChapters("book-1").first())

        repository.clearForBook("book-1")
        database.close()

        database = openDatabase()
        assertEquals(0, database.chapterReadDao().readCount("book-1"))
        database.close()
    }

    private fun openDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
}
