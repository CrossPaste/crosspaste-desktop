package com.crosspaste.app

import kotlinx.coroutines.flow.StateFlow
import okio.Path

interface AppFileChooser {

    val showFileDialog: StateFlow<Boolean>

    fun openFileChooser(
        fileSelectionMode: FileSelectionMode,
        title: String? = null,
        initPath: Path? = null,
        cancel: (() -> Unit)? = null,
        action: (Any) -> Unit,
    )

    fun openFileChooserToExport(
        initPath: Path? = null,
        cancel: (() -> Unit)? = null,
        action: (Any) -> Unit,
    )

    fun openFileChooserToImport(
        initPath: Path? = null,
        cancel: (() -> Unit)? = null,
        action: (Any) -> Unit,
    )
}

enum class FileSelectionMode {
    FILE_ONLY,
    DIRECTORY_ONLY,
}
