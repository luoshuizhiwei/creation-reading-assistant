package com.creationreadingassistant.ui.viewmodel

import com.creationreadingassistant.data.settings.AISettings
import com.creationreadingassistant.data.settings.AppearanceSettings
import com.creationreadingassistant.data.settings.ReaderSettings
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
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    private lateinit var settings: SettingsStore
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = SettingsViewModel(settings)

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
}
