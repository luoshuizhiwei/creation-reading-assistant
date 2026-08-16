package com.creationreadingassistant.data.settings

import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import com.creationreadingassistant.data.security.SecurePrefs
import com.creationreadingassistant.testutil.InMemorySharedPreferences
import com.creationreadingassistant.testutil.testDataStoreContext
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * 旧 txt_toc_rule_<bookId> 一次性迁移 marker：标记后不再重复执行迁移，
 * 防止用户后续的规则管理（如禁用宽松内置）被旧值重新覆盖（单一状态源保证）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTxtTocMigrationMarkerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var store: SettingsStore
    private val securePrefsMap = HashMap<String, SharedPreferences>()

    @Before
    fun setUp() {
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } answers {
            securePrefsMap.getOrPut(secondArg()) { InMemorySharedPreferences() }
        }
        scope = CoroutineScope(UnconfinedTestDispatcher())
        store = SettingsStore(testDataStoreContext(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkObject(SecurePrefs)
    }

    @Test
    fun `migration marker defaults false and marks per book`() = runTest {
        // 先用一个既有读接口等待磁盘状态加载，避免基于旧缓存值断言
        // DataStore 测试上下文跨运行持久：用唯一 bookId 保证初始态干净
        val bookId = "book-${UUID.randomUUID()}"
        store.loadTxtTocRule(bookId)

        assertFalse("未迁移的书应返回 false", store.isTxtTocRuleMigrated(bookId))

        store.markTxtTocRuleMigrated(bookId)

        assertTrue("标记后应返回 true", store.isTxtTocRuleMigrated(bookId))
        assertFalse("标记不得泄漏到其他书", store.isTxtTocRuleMigrated("book-2-${UUID.randomUUID()}"))
    }
}
