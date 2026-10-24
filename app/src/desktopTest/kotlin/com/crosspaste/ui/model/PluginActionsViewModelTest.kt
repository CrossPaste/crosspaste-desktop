package com.crosspaste.ui.model

import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.WindowInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.plugin.LocalPlugin
import com.crosspaste.plugin.PluginAction
import com.crosspaste.plugin.PluginManifest
import com.crosspaste.plugin.PluginRepository
import com.crosspaste.plugin.ProcessPluginRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PluginActionsViewModelTest {

    private val searchWindowInfo = MutableStateFlow(windowInfo(show = true))

    private val appWindowManager =
        mockk<DesktopAppWindowManager>(relaxed = true) {
            every { searchWindowInfo } returns this@PluginActionsViewModelTest.searchWindowInfo
        }

    private val pasteData =
        mockk<PasteData>(relaxed = true) {
            every { id } returns 7L
            every { getType() } returns PasteType.TEXT_TYPE
        }

    private val translate =
        plugin("translate", "Translate", listOf(PluginAction("en", mapOf("en" to "English")), PluginAction("ja")))
    private val upper = plugin("upper", "Upper")

    private val pluginRepository =
        mockk<PluginRepository>(relaxed = true) {
            every { plugins } returns MutableStateFlow(listOf(translate, upper))
            every { runnablePlugins(PasteType.TEXT_TYPE) } returns listOf(translate, upper)
        }

    private val pasteSelectionViewModel =
        mockk<PasteSelectionViewModel> {
            every { currentPasteDataList } returns MutableStateFlow(listOf(pasteData))
        }

    private val runner = mockk<ProcessPluginRunner>()

    private fun createVm() =
        PluginActionsViewModel(
            appWindowManager = appWindowManager,
            copywriter = mockk<GlobalCopywriter> { every { language() } returns "en" },
            pasteSelectionViewModel = pasteSelectionViewModel,
            pluginRepository = pluginRepository,
            processPluginRunner = runner,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggle lists each action under its plugin and plain plugins on their own`() {
        val vm = createVm()

        vm.toggle()

        val items = vm.panel.value!!.items
        assertEquals(listOf("English", "ja", "Upper"), items.map { it.label })
        assertEquals(listOf("Translate", "Translate", null), items.map { it.group })
        assertEquals(listOf("en", "ja", null), items.map { it.actionId })
        assertTrue(vm.hasRunnablePlugins.value)
    }

    @Test
    fun `selection stays within the list`() {
        val vm = createVm()
        vm.toggle()

        vm.selectPrev()
        assertEquals(0, vm.panel.value!!.selectedIndex)
        repeat(5) { vm.selectNext() }
        assertEquals(2, vm.panel.value!!.selectedIndex)
    }

    @Test
    fun `running the selected action pastes through the runner and closes the panel`() {
        val done = CompletableDeferred<Unit>()
        coEvery { runner.runAndPaste(translate, pasteData, "ja") } coAnswers { done.await() }
        val vm = createVm()
        vm.toggle()
        vm.selectNext()

        vm.runSelected()

        assertTrue(vm.panel.value!!.running)
        vm.selectNext()
        assertEquals(1, vm.panel.value!!.selectedIndex)
        done.complete(Unit)
        assertNull(vm.panel.value)
        coVerify(exactly = 1) { runner.runAndPaste(translate, pasteData, "ja") }
    }

    @Test
    fun `hiding the search window closes the panel and cancels the run`() {
        val cancelled = CompletableDeferred<Unit>()
        coEvery { runner.runAndPaste(any(), any(), any()) } coAnswers {
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        val vm = createVm()
        vm.toggle()
        vm.runSelected()

        searchWindowInfo.value = windowInfo(show = false)

        assertNull(vm.panel.value)
        assertTrue(cancelled.isCompleted)
    }

    private fun windowInfo(show: Boolean) = mockk<WindowInfo> { every { this@mockk.show } returns show }

    private fun plugin(
        id: String,
        name: String,
        actions: List<PluginAction> = emptyList(),
    ) = LocalPlugin(
        directory = "/plugins/$id".toPath(),
        manifest =
            PluginManifest(
                id = "com.example.$id",
                version = "1.0.0",
                name = mapOf("en" to name),
                pasteTypes = listOf("text"),
                runtime = "process",
                output = "replaceClipboard",
                actions = actions,
            ),
        problem = null,
        missingRequirements = emptyList(),
        enabled = true,
    )
}
