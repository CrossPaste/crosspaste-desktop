package com.crosspaste.paste

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.Path

/**
 * The file currently chosen on the Import page and its import execution state.
 * Lives outside the page so that a file dropped anywhere on the window can be
 * handed to the page, and so that the selection, progress, and outcome survive
 * navigating away and back.
 */
class PasteImportSelection {

    private val _path = MutableStateFlow<Path?>(null)
    val path: StateFlow<Path?> = _path

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _outcome = MutableStateFlow<ImportOutcome?>(null)
    val outcome: StateFlow<ImportOutcome?> = _outcome

    fun select(path: Path) {
        if (_isImporting.value) return
        _path.value = path
        _outcome.value = null
    }

    fun clear() {
        if (_isImporting.value) return
        _path.value = null
    }

    fun dismissOutcome() {
        _outcome.value = null
    }

    fun startImport(
        pasteImportService: PasteImportService,
        pasteImportParamFactory: PasteImportParamFactory<Any>,
    ) {
        val currentPath = _path.value ?: return
        if (_isImporting.value) return

        _isImporting.value = true
        _progress.value = 0f
        _outcome.value = null

        pasteImportService.import(
            pasteImportParam = pasteImportParamFactory.createPasteImportParam(currentPath),
            updateProgress = { currentProgress ->
                val safeProgress = if (currentProgress.isNaN()) 0f else currentProgress.coerceIn(0f, 1f)
                _progress.value = safeProgress
            },
            onResult = { result ->
                _isImporting.value = false
                _progress.value = 0f
                _outcome.value = ImportOutcome(currentPath.name, result)
                _path.value = null
            },
        )
    }
}
