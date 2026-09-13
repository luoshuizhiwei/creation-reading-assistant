package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.settings.AISettings
import com.creationreadingassistant.data.settings.AppearanceSettings
import com.creationreadingassistant.data.settings.PerBookSettingsStore
import com.creationreadingassistant.data.settings.ReaderOverrideKey
import com.creationreadingassistant.data.settings.ReaderSettings
import com.creationreadingassistant.data.settings.SelectionActionSettings
import com.creationreadingassistant.data.settings.SelectionActionStore
import com.creationreadingassistant.data.settings.SettingsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * SettingsViewModel 设置状态暴露与更新委托测试（浅测：无状态转发层）。
 *
 * R3-P1 追加：本书覆盖的读写委托，以及「全局 reader 与本书有效设置」的分工 ——
 * 阅读器必须拿 [SettingsViewModel.effectiveReader]，直接拿 `reader` 会让本书覆盖失效。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var settings: SettingsStore
    private lateinit var perBook: PerBookSettingsStore
    private lateinit var selectionActions: SelectionActionStore
    private lateinit var appearanceFlow: MutableStateFlow<AppearanceSettings>
    private lateinit var readerFlow: MutableStateFlow<ReaderSettings>
    private lateinit var aiFlow: MutableStateFlow<AISettings>

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        appearanceFlow = MutableStateFlow(AppearanceSettings())
        readerFlow = MutableStateFlow(ReaderSettings())
        aiFlow = MutableStateFlow(AISettings())
        settings = mockk {
            every { appearance } returns appearanceFlow
            every { reader } returns readerFlow
            every { ai } returns aiFlow
        }
        perBook = mockk(relaxed = true)
        selectionActions = mockk(relaxed = true) {
            every { settings } returns MutableStateFlow(SelectionActionSettings())
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = SettingsViewModel(settings, perBook, selectionActions)

    @Test
    fun `exposes settings state flows directly`() {
        val vm = createVm()

        assertEquals(AppearanceSettings(), vm.appearance.value)
        assertEquals(ReaderSettings(), vm.reader.value)
        assertEquals(AISettings(), vm.ai.value)
    }

    @Test
    fun `updateAppearance delegates block to store`() = runTest(mainDispatcher.scheduler) {
        coEvery { settings.updateAppearance(any()) } returns Unit
        val vm = createVm()

        vm.updateAppearance { copy(themeMode = "dark") }
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { settings.updateAppearance(any()) }
    }

    @Test
    fun `updateReader delegates block to store`() = runTest(mainDispatcher.scheduler) {
        coEvery { settings.updateReader(any()) } returns Unit
        val vm = createVm()

        vm.updateReader { copy(fontSize = 30f) }
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { settings.updateReader(any()) }
    }

    @Test
    fun `updateAi delegates block to store`() = runTest(mainDispatcher.scheduler) {
        coEvery { settings.updateAi(any()) } returns Unit
        val vm = createVm()

        vm.updateAi { copy(model = "gpt-4o") }
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { settings.updateAi(any()) }
    }

    // ── R3-P1：两级设置 ────────────────────────────────────────────────

    @Test
    fun `effectiveReader falls back to the global flow when there is no book`() {
        val vm = createVm()

        assertEquals(readerFlow, vm.effectiveReader(""))
        assertEquals(readerFlow, vm.effectiveReader(null))
    }

    @Test
    fun `effectiveReader delegates to the per book store for a real book`() {
        every { perBook.effectiveReader("book-1") } returns readerFlow
        val vm = createVm()

        assertEquals(readerFlow, vm.effectiveReader("book-1"))
    }

    @Test
    fun `setOverride and clearOverride delegate to the per book store`() =
        runTest(mainDispatcher.scheduler) {
            val vm = createVm()

            vm.setOverride("book-1", ReaderOverrideKey.FONT_SIZE, "32.0")
            vm.clearOverride("book-1", ReaderOverrideKey.FONT_SIZE)
            testScheduler.advanceUntilIdle()

            coVerify(exactly = 1) { perBook.setOverride("book-1", ReaderOverrideKey.FONT_SIZE, "32.0") }
            coVerify(exactly = 1) { perBook.clearOverride("book-1", ReaderOverrideKey.FONT_SIZE) }
        }

    @Test
    fun `blank book id is a no-op for override writes`() = runTest(mainDispatcher.scheduler) {
        val vm = createVm()

        vm.setOverride(null, ReaderOverrideKey.FONT_SIZE, "32.0")
        vm.clearAllOverrides("")
        vm.clearOverride(null, ReaderOverrideKey.FONT_SIZE)
        vm.applyPresetToBook("", com.creationreadingassistant.data.settings.ReadingPresets.DEFAULT)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { perBook.setOverride(any(), any(), any()) }
        coVerify(exactly = 0) { perBook.clearAllOverrides(any()) }
        coVerify(exactly = 0) { perBook.clearOverride(any(), any()) }
        coVerify(exactly = 0) { perBook.applyPresetToBook(any(), any()) }
    }
}
