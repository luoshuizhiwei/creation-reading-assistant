package com.creationreadingassistant.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShelfSavedViewsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun multidimensionalFilterSurvivesCompleteStoreRestartAndDeletionPersists() = runBlocking {
        withTimeout(10_000) {
            // A fresh test-only file; no Context singleton or application preferences are used.
            val file = File(temporaryFolder.root, "saved-views.preferences_pb")
            val selected = SavedShelfFilter(
                name = "测试多维筛选",
                statusFilter = "READING",
                formatFilter = "epub",
                selectedShelfId = "test-shelf",
                selectedCategoryId = "test-category",
                selectedTagIds = listOf("test-tag-1", "test-tag-2"),
                searchQuery = "测试关键词",
                createdAt = "2026-10-24T00:00:00Z",
            )
            val retained = SavedShelfFilter(
                name = "测试保留筛选",
                statusFilter = "UNREAD",
                formatFilter = "txt",
                searchQuery = "测试保留关键词",
                createdAt = "2026-10-24T00:01:00Z",
            )

            val writeJob = SupervisorJob()
            val writeScope = CoroutineScope(Dispatchers.IO + writeJob)
            try {
                val dataStore = PreferenceDataStoreFactory.create(scope = writeScope) { file }
                val store = ShelfSavedViewsStore(dataStore, writeScope)
                assertTrue(store.save(retained))
                assertTrue(store.save(selected))
                assertEquals(
                    listOf(selected, retained),
                    store.savedFilters.first { it.size == 2 },
                )
                assertTrue(file.isFile)
                assertTrue(file.length() > 0L)
            } finally {
                // Includes the DataStore actor and the store's stateIn collector.
                withContext(NonCancellable) { writeJob.cancelAndJoin() }
            }
            assertTrue(writeJob.isCompleted)

            val readDeleteJob = SupervisorJob()
            val readDeleteScope = CoroutineScope(Dispatchers.IO + readDeleteJob)
            try {
                val dataStore = PreferenceDataStoreFactory.create(scope = readDeleteScope) { file }
                val reopened = ShelfSavedViewsStore(dataStore, readDeleteScope)
                assertEquals(
                    listOf(selected, retained),
                    reopened.savedFilters.first { it.size == 2 },
                )
                reopened.delete(selected.name)
                assertEquals(
                    listOf(retained),
                    reopened.savedFilters.first { it == listOf(retained) },
                )
            } finally {
                withContext(NonCancellable) { readDeleteJob.cancelAndJoin() }
            }
            assertTrue(readDeleteJob.isCompleted)

            val verifyJob = SupervisorJob()
            val verifyScope = CoroutineScope(Dispatchers.IO + verifyJob)
            try {
                val dataStore = PreferenceDataStoreFactory.create(scope = verifyScope) { file }
                val reopenedAfterDeletion = ShelfSavedViewsStore(dataStore, verifyScope)
                // A nonempty retained control avoids accepting StateFlow's synthetic empty initial value.
                assertEquals(
                    listOf(retained),
                    reopenedAfterDeletion.savedFilters.first { it.isNotEmpty() },
                )
            } finally {
                withContext(NonCancellable) { verifyJob.cancelAndJoin() }
            }
            assertTrue(verifyJob.isCompleted)
        }
    }
}
