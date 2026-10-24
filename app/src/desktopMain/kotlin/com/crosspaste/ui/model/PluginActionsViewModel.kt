package com.crosspaste.ui.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteData
import com.crosspaste.plugin.LocalPlugin
import com.crosspaste.plugin.PluginRepository
import com.crosspaste.plugin.ProcessPluginRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One row of the search window's plugin panel: a plugin without actions, or one
 * of a plugin's actions. [group] is the plugin's name for action rows, shown
 * once above them.
 */
data class PluginActionItem(
    val plugin: LocalPlugin,
    val actionId: String?,
    val group: String?,
    val label: String,
)

data class PluginActionsPanel(
    val pasteData: PasteData,
    val items: List<PluginActionItem>,
    val selectedIndex: Int = 0,
    val running: Boolean = false,
)

/**
 * The keyboard way to run a plugin on the selected search result. A plugin that
 * replaces the clipboard pastes its result straight into the previous app.
 */
class PluginActionsViewModel(
    appWindowManager: DesktopAppWindowManager,
    private val copywriter: GlobalCopywriter,
    private val pasteSelectionViewModel: PasteSelectionViewModel,
    private val pluginRepository: PluginRepository,
    private val processPluginRunner: ProcessPluginRunner,
) : ViewModel() {

    private val _panel = MutableStateFlow<PluginActionsPanel?>(null)
    val panel: StateFlow<PluginActionsPanel?> = _panel.asStateFlow()

    val hasRunnablePlugins: StateFlow<Boolean> =
        pluginRepository.plugins
            .map { plugins -> plugins.any { it.runnable } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var runJob: Job? = null

    init {
        // The window content stops composing while hidden, so close from here
        viewModelScope.launch {
            appWindowManager.searchWindowInfo.collect { if (!it.show) close() }
        }
    }

    fun toggle() {
        if (_panel.value != null) {
            close()
            return
        }
        val pasteData = pasteSelectionViewModel.currentPasteDataList.value.firstOrNull() ?: return
        _panel.value = PluginActionsPanel(pasteData, itemsFor(pasteData))
    }

    fun close() {
        runJob?.cancel()
        runJob = null
        _panel.value = null
    }

    fun selectPrev() = moveSelection(-1)

    fun selectNext() = moveSelection(1)

    fun select(index: Int) {
        _panel.update { panel ->
            if (panel == null ||
                panel.running ||
                index !in panel.items.indices
            ) {
                panel
            } else {
                panel.copy(selectedIndex = index)
            }
        }
    }

    fun runSelected() {
        val panel = _panel.value ?: return
        if (panel.running) return
        val item = panel.items.getOrNull(panel.selectedIndex) ?: return
        _panel.value = panel.copy(running = true)
        val job =
            viewModelScope.launch {
                processPluginRunner.runAndPaste(item.plugin, panel.pasteData, item.actionId)
            }
        runJob = job
        job.invokeOnCompletion {
            if (runJob === job) {
                runJob = null
                _panel.value = null
            }
        }
    }

    private fun moveSelection(delta: Int) {
        _panel.update { panel ->
            if (panel == null || panel.running || panel.items.isEmpty()) {
                panel
            } else {
                panel.copy(selectedIndex = (panel.selectedIndex + delta).coerceIn(0, panel.items.lastIndex))
            }
        }
    }

    private fun itemsFor(pasteData: PasteData): List<PluginActionItem> {
        val language = copywriter.language()
        return pluginRepository.runnablePlugins(pasteData.getType()).flatMap { plugin ->
            val manifest = plugin.manifest ?: return@flatMap emptyList()
            val name = manifest.displayName(language)
            if (manifest.actions.isEmpty()) {
                listOf(PluginActionItem(plugin, actionId = null, group = null, label = name))
            } else {
                manifest.actions.map { action ->
                    PluginActionItem(plugin, action.id, group = name, label = action.displayName(language))
                }
            }
        }
    }
}
