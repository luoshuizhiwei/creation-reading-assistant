package com.creationreadingassistant.data.settings

import androidx.datastore.preferences.core.edit
import com.creationreadingassistant.testutil.testDataStoreContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 翻页动画时长（ReaderSettings.pageTurnSpeed）的默认值、0..800 夹取与一次性迁移。
 *
 * 与 [SettingsStoreTest] 共用同一个 JVM 落盘 DataStore，因此断言全部走「先写后读」，
 * 不假设存储为空；迁移用例显式移除 marker 后再调用正式迁移 seam。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PageTurnSpeedSettingsTest {

    private lateinit var scope: CoroutineScope
    private lateinit var store: SettingsStore

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher())
        store = SettingsStore(testDataStoreContext(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `reader settings default page turn speed is 200ms`() {
        assertEquals(200f, ReaderSettings().pageTurnSpeed, 0f)
    }

    @Test
    fun `read clamps page turn speed to zero through 800`() = runTest {
        store.preferencesDataStore.edit {
            it[KEY_PAGE_TURN_SPEED_DEFAULT_200_MIGRATED] = true
            it[KEY_PAGE_TURN_SPEED] = -40f
        }
        val low = store.reader.first { it.pageTurnSpeed == 0f }
        assertEquals(0f, low.pageTurnSpeed, 0f)

        store.preferencesDataStore.edit { it[KEY_PAGE_TURN_SPEED] = 1200f }
        val high = store.reader.first { it.pageTurnSpeed == 800f }
        assertEquals(800f, high.pageTurnSpeed, 0f)
    }

    @Test
    fun `write clamps page turn speed to zero through 800`() = runTest {
        store.reader.first()

        store.updateReader { copy(pageTurnSpeed = -10f) }
        val storedLow = store.preferencesDataStore.data.first()[KEY_PAGE_TURN_SPEED]
        assertEquals(0f, storedLow ?: -1f, 0f)

        store.updateReader { copy(pageTurnSpeed = 1000f) }
        val storedHigh = store.preferencesDataStore.data.first()[KEY_PAGE_TURN_SPEED]
        assertEquals(800f, storedHigh ?: -1f, 0f)
    }

    @Test
    fun `legacy default 400 is migrated to 200 and marked once`() = runTest {
        store.preferencesDataStore.edit {
            it[KEY_PAGE_TURN_SPEED] = 400f
            it.remove(KEY_PAGE_TURN_SPEED_DEFAULT_200_MIGRATED)
        }

        store.migratePageTurnSpeedDefault200Once()

        val prefs = store.preferencesDataStore.data.first()
        assertEquals(200f, prefs[KEY_PAGE_TURN_SPEED] ?: -1f, 0f)
        assertTrue(prefs[KEY_PAGE_TURN_SPEED_DEFAULT_200_MIGRATED] == true)
    }

    @Test
    fun `non legacy default page turn speed is preserved by migration`() = runTest {
        store.preferencesDataStore.edit {
            it[KEY_PAGE_TURN_SPEED] = 550f
            it.remove(KEY_PAGE_TURN_SPEED_DEFAULT_200_MIGRATED)
        }

        store.migratePageTurnSpeedDefault200Once()

        val prefs = store.preferencesDataStore.data.first()
        assertEquals(550f, prefs[KEY_PAGE_TURN_SPEED] ?: -1f, 0f)
        assertTrue(prefs[KEY_PAGE_TURN_SPEED_DEFAULT_200_MIGRATED] == true)
    }

    @Test
    fun `custom value after migration marker is not overwritten again`() = runTest {
        store.preferencesDataStore.edit {
            it[KEY_PAGE_TURN_SPEED] = 400f
            it.remove(KEY_PAGE_TURN_SPEED_DEFAULT_200_MIGRATED)
        }
        store.migratePageTurnSpeedDefault200Once()

        // 迁移完成后用户主动选择 600ms，这是新的自定义值。
        store.updateReader { copy(pageTurnSpeed = 600f) }
        store.migratePageTurnSpeedDefault200Once()

        val prefs = store.preferencesDataStore.data.first()
        assertEquals(600f, prefs[KEY_PAGE_TURN_SPEED] ?: -1f, 0f)
        assertTrue(prefs[KEY_PAGE_TURN_SPEED_DEFAULT_200_MIGRATED] == true)
    }
}
