package com.creationreadingassistant.ui.screen.reader

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.creationreadingassistant.data.ai.AiClient
import com.creationreadingassistant.data.local.AppDatabase
import com.creationreadingassistant.data.settings.PerBookSettingsStore
import com.creationreadingassistant.data.settings.SelectionActionStore
import com.creationreadingassistant.data.settings.SettingsStore
import com.creationreadingassistant.feature.reader.locator.AnchorCacheStore
import com.creationreadingassistant.feature.reader.pager.PageIndexStore
import com.creationreadingassistant.feature.reader.pager.PagerHealthStore
import com.creationreadingassistant.feature.reader.pager.ReaderPageIndexManager
import com.creationreadingassistant.ui.viewmodel.ReaderUiState
import com.creationreadingassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * 149-r2：真实页面退出保存 drain 的 instrumentation 回归（r1 交付的导航起点/生命周期修复）。
 *
 * r1→r2 修复（Root 实读发现）：
 * 1. r1 的 NavHost 以 home 为起点却从未 navigate——四用例到不了 ReaderScreen。r2 每用例先
 *    **真实导航一次 home→reader**（navController.navigate），并在按 Back 前明确确认：
 *    当前目的地是 reader、reader 节点已出现、pop/销毁计数为 0（无初始 pop）。
 *    不把起点设为 reader（无返回栈）、不手动调用 drain 替代实际 Back。
 * 2. NavBackStackEntry 的 ON_DESTROY 观察保留为**记录**，不再作为断言：组合 onDispose 移除
 *    observer 可能早于 entry ON_DESTROY 而丢事件，且框架不保证 disposed/destroy 的内部顺序。
 *    断言只要求**保存正常完成先于 pop**、pop 后**实际离开**（reader 组合移除 + 当前目的地回到 home），
 *    并等真正达到状态后再读计数。
 * 3. 受控的仍只有 `awaitProgressSaves` 生产 lambda 接缝（ReaderRoute 生产注入
 *    `viewModel::awaitProgressSaves`）的时序源；VM 侧语义引用 ReaderViewModelTest JVM 证据。
 *    证明范围是页面级退出接线 × 受控保存结果 × 真实导航生命周期；不冒充 Room 落盘或进程死亡。
 *
 * 四性质：
 * 1. pending：受控未完成保存时，reader 仍在、pop 0 次、重复 Back 不再派发；
 * 2. drained：保存完成后 pop 恰 1 次；顺序 drain_returned < pop；pop 后 reader 组合实际移除、
 *    当前目的地回到 home（真实导航合同），不假设 disposed/destroy 内部顺序；
 * 3. save failure：不 pop、生产 snackbar 反馈可见；第二次实际重试（代次=2）成功后才 pop 恰 1 次并离开；
 * 4. no pending：drain 立即完成 → pop 恰 1 次并离开。
 *
 * 资源 owner：@Before 单次创建（设置链只读、in-memory Room、测试 scope），@After 释放
 * （db.close + scope.cancel）；不写生产 DB/DataStore/SharedPreferences。
 *
 * UNCOMPILED → 本轮经 :app:compileDebugAndroidTestKotlin 定向编译（结果见 REPORT/raw）。
 */
class ReaderExitSaveDrainDeviceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = ConcurrentLinkedQueue<String>()
    private val popCount = AtomicInteger(0)
    private val dispatchGen = AtomicInteger(0)
    private val entryDestroyed = AtomicInteger(0)
    private val readerDisposed = AtomicInteger(0)

    @Volatile
    private var navController: NavHostController? = null

    private lateinit var appContext: Context
    private lateinit var testScope: CoroutineScope
    private lateinit var db: AppDatabase
    private lateinit var settingsStore: SettingsStore
    private lateinit var settingsViewModel: SettingsViewModel

    @Before
    fun setUpResources() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        settingsStore = SettingsStore(appContext, testScope)
        settingsViewModel = SettingsViewModel(
            settings = settingsStore,
            perBook = PerBookSettingsStore(settingsStore),
            selectionActionStore = SelectionActionStore(settingsStore),
        )
        db = Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDownResources() {
        db.close()
        testScope.cancel()
    }

    private fun record(event: String) {
        events.add(event)
    }

    /** 生产接缝的受控时序源：armNext 开新代次；releaseNow 放行；failure 非空时该代次记录后原样抛出（不吞）。 */
    private inner class ControlledDrain {
        private var release: CompletableDeferred<Unit> = CompletableDeferred()
        private var failure: Throwable? = null

        fun armNext(withFailure: Throwable?) {
            release = CompletableDeferred()
            failure = withFailure
        }

        suspend fun await() {
            val gen = dispatchGen.incrementAndGet()
            record("drain_await_g$gen")
            release.await()
            val f = failure
            if (f != null) {
                record("drain_failed_g$gen")
                throw f
            }
            record("drain_returned_g$gen")
        }

        fun releaseNow() {
            release.complete(Unit)
        }
    }

    private fun buildInputs(): ReaderScreenInputs =
        ReaderScreenInputs(
            bookId = "149-exit-drain-book",
            highlightId = null,
            navigationMode = ReaderNavigationMode.NORMAL,
            documentUiState = ReaderUiState(),
            screenState = ReaderScreenState(controlsVisible = false),
            highlights = emptyList(),
            notes = emptyList(),
            inspirations = emptyList(),
            categories = emptyList(),
            tags = emptyList(),
            sessions = emptyList(),
            readChapters = emptyList(),
            txtTocRuleIdFromVm = "",
            chapterLoadResult = null,
            txtRuleScanResult = null,
        )

    private fun buildCallbacks(onBack: () -> Unit): ReaderScreenCallbacks =
        ReaderScreenCallbacks(
            onLoadChapterBlocks = { _, _ -> emptyList() },
            onExtractChapterText = { _, _ -> "" },
            onAction = {},
            onDocumentAction = {},
            onBack = onBack,
            settingsStore = settingsStore,
            aiClient = AiClient(settingsStore, Dispatchers.IO, appContext),
            pageIndexStore = PageIndexStore(db.readerPageIndexDao()),
            anchorCacheStore = AnchorCacheStore(db.readerAnchorCacheDao()),
            pagerHealthStore = PagerHealthStore(appContext),
            pageIndexManager = ReaderPageIndexManager(),
        )

    @Composable
    private fun TestNavHost(drain: ControlledDrain) {
        val navController = rememberNavController()
        SideEffect { this@ReaderExitSaveDrainDeviceTest.navController = navController }
        NavHost(navController = navController, startDestination = HOME_DESTINATION) {
            composable(HOME_DESTINATION) {
                Text(text = "exit_test_home", modifier = Modifier.testTag(HOME_TAG))
            }
            composable(READER_DESTINATION) {
                // reader destination 内的 LocalLifecycleOwner 即该 NavBackStackEntry；
                // 它的生命周期就是 entry 生命周期（NavBackStackEntry.lifecycle 本体不可直接访问）。
                val entryLifecycleOwner = LocalLifecycleOwner.current
                Box(modifier = Modifier.fillMaxSize().testTag(READER_ALIVE_TAG)) {
                    ReaderScreen(
                        inputs = buildInputs(),
                        callbacks = buildCallbacks(onBack = {
                            record("pop")
                            popCount.incrementAndGet()
                            navController.popBackStack()
                        }),
                        settingsViewModel = settingsViewModel,
                        awaitProgressSaves = { drain.await() },
                    )
                }
                // 组合销毁观察：随 reader destination 真实生灭。
                DisposableEffect(Unit) {
                    onDispose {
                        readerDisposed.incrementAndGet()
                        record("reader_disposed")
                    }
                }
                // entry ON_DESTROY 仅记录（observer 可能在 ON_DESTROY 前随组合移除而丢事件；
                // 框架不保证 disposed/destroy 内部顺序，故不入断言）。
                DisposableEffect(entryLifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_DESTROY) {
                            entryDestroyed.incrementAndGet()
                            record("entry_destroyed")
                        }
                    }
                    entryLifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { entryLifecycleOwner.lifecycle.removeObserver(observer) }
                }
            }
        }
    }

    private fun setNavContent(drain: ControlledDrain) {
        composeRule.setContent { TestNavHost(drain) }
        composeRule.waitForIdle()
    }

    /**
     * 真实一次 home→reader 导航，并按合同确认入场：
     * 当前目的地=reader、reader 节点已出现、pop/组合销毁计数为 0（无初始 pop）。
     */
    private fun navigateHomeToReaderAndWaitReady() {
        val nav = requireNotNull(navController) { "NavHost did not publish navController" }
        composeRule.runOnUiThread { nav.navigate(READER_DESTINATION) { launchSingleTop = true } }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            nav.currentDestination?.route == READER_DESTINATION
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag(READER_ALIVE_TAG).assertExists() }.isSuccess
        }
        assertEquals(READER_DESTINATION, nav.currentDestination?.route)
        assertEquals("no pop before test Back", 0, popCount.get())
        assertEquals("no disposal before test Back", 0, readerDisposed.get())
        composeRule.onNodeWithTag(READER_ALIVE_TAG).assertExists()
    }

    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { activity ->
            (activity as ComponentActivity).onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun awaitEvent(name: String, timeoutMs: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) { events.contains(name) }
    }

    private fun awaitCondition(timeoutMs: Long = 10_000, predicate: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = timeoutMs) { predicate() }
    }

    /** 真正达到"已离开 reader"状态后才返回（pop 回调后不得立刻读异步销毁计数）。 */
    private fun awaitActuallyLeftReader() {
        awaitCondition { popCount.get() == 1 }
        composeRule.waitForIdle()
        awaitCondition { readerDisposed.get() == 1 }
        composeRule.waitForIdle()
        awaitCondition {
            navController?.currentDestination?.route == HOME_DESTINATION
        }
        composeRule.onNodeWithTag(HOME_TAG).assertExists()
    }

    // ── 性质 1：pending 期间 reader 仍在、pop 0 次、重复 Back 不重复派发 ──
    @Test
    fun pendingSave_readerStays_zeroPop_repeatedBackDoesNotRedispatch() {
        val drain = ControlledDrain()
        drain.armNext(withFailure = null)
        setNavContent(drain)
        navigateHomeToReaderAndWaitReady()

        pressBack()
        awaitCondition { dispatchGen.get() == 1 }
        composeRule.waitForIdle()

        assertEquals(0, popCount.get())
        assertEquals(0, readerDisposed.get())
        assertEquals(READER_DESTINATION, navController?.currentDestination?.route)
        composeRule.onNodeWithTag(READER_ALIVE_TAG).assertExists()

        pressBack()
        composeRule.waitForIdle()
        assertEquals("no re-dispatch while pending", 1, dispatchGen.get())
        assertEquals(0, popCount.get())
        assertEquals(0, readerDisposed.get())
        composeRule.onNodeWithTag(READER_ALIVE_TAG).assertExists()

        // 收尾放行：用例结束前完成退出，避免挂起协程跨用例泄漏。
        drain.releaseNow()
        awaitActuallyLeftReader()
    }

    // ── 性质 2：保存完成后 pop 恰 1 次；drain_returned 先于 pop；pop 后实际离开 reader ──
    @Test
    fun drainedSave_popsOnce_completionBeforeLeave() {
        val drain = ControlledDrain()
        drain.armNext(withFailure = null)
        setNavContent(drain)
        navigateHomeToReaderAndWaitReady()

        pressBack()
        awaitCondition { dispatchGen.get() == 1 }
        drain.releaseNow()
        awaitActuallyLeftReader()

        assertEquals(1, popCount.get())
        assertEquals(1, readerDisposed.get())
        val order = events.toList()
        assertTrue(
            "drain must return before pop; order=$order",
            order.indexOf("drain_returned_g1") in 0 until order.indexOf("pop"),
        )
        // reader_disposed 与 entry_destroyed 的内部顺序框架无保证：entry_destroyed 仅记录不强序。
    }

    // ── 性质 3：失败不退出 + 生产 snackbar 可见；第二次实际重试成功后才 pop 恰 1 次并离开 ──
    @Test
    fun saveFailure_noPop_observableFeedback_retrySucceedsThenLeavesOnce() {
        val drain = ControlledDrain()
        drain.armNext(withFailure = java.io.IOException("测试 TXT 保存注入失败"))
        setNavContent(drain)
        navigateHomeToReaderAndWaitReady()

        pressBack()
        awaitCondition { dispatchGen.get() == 1 }
        drain.releaseNow()
        awaitEvent("drain_failed_g1")
        awaitCondition {
            composeRule.onAllNodesWithText(SAVE_FAILED_SNACKBAR_TEXT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        assertEquals(0, popCount.get())
        assertEquals(0, readerDisposed.get())
        assertEquals(READER_DESTINATION, navController?.currentDestination?.route)
        composeRule.onNodeWithTag(READER_ALIVE_TAG).assertExists()
        composeRule.onNodeWithText(SAVE_FAILED_SNACKBAR_TEXT).assertExists()

        // 重试：再次 Back（生产唯一入口）→ 新代次（=2）；本次保存成功。
        drain.armNext(withFailure = null)
        pressBack()
        awaitCondition { dispatchGen.get() == 2 }
        drain.releaseNow()
        awaitActuallyLeftReader()

        assertEquals("exactly one pop after successful retry", 1, popCount.get())
        assertEquals(1, readerDisposed.get())
    }

    // ── 性质 4：无 pending——drain 立即完成，pop 恰 1 次并实际离开 ──
    @Test
    fun noPendingSave_normalExit_popsOnce() {
        val drain = ControlledDrain()
        drain.armNext(withFailure = null)
        drain.releaseNow() // 该代次立即完成 = 无在途保存时 awaitProgressSaves 直接返回
        setNavContent(drain)
        navigateHomeToReaderAndWaitReady()

        pressBack()
        awaitActuallyLeftReader()

        assertEquals(1, popCount.get())
        assertEquals(1, dispatchGen.get())
        assertEquals(1, readerDisposed.get())
    }

    private companion object {
        const val HOME_DESTINATION = "exit_test_home"
        const val READER_DESTINATION = "exit_test_reader"
        const val HOME_TAG = "exit_test_home_alive"
        const val READER_ALIVE_TAG = "exit_test_reader_alive"

        /** 生产文案（ReaderScreen.kt CRA-READER-EXIT-SAVE-DRAIN-v1 失败反馈）。 */
        const val SAVE_FAILED_SNACKBAR_TEXT = "进度保存未完成，已留在当前页"
    }
}
