package com.crosspaste.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.Path
import okio.Path.Companion.toOkioPath
import javax.swing.JFileChooser

class DesktopAppFileChooser(
    private val desktopAppWindowManager: DesktopAppWindowManager,
) : AppFileChooser {

    private val _showFileDialog: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val showFileDialog: StateFlow<Boolean> = _showFileDialog

    override fun openFileChooser(
        fileSelectionMode: FileSelectionMode,
        title: String?,
        initPath: Path?,
        cancel: (() -> Unit)?,
        action: (Any) -> Unit,
    ) {
        desktopAppWindowManager.mainComposeWindow?.let {
            _showFileDialog.value = true
            JFileChooser().apply {
                this.fileSelectionMode =
                    when (fileSelectionMode) {
                        FileSelectionMode.FILE_ONLY -> JFileChooser.FILES_ONLY
                        FileSelectionMode.DIRECTORY_ONLY -> JFileChooser.DIRECTORIES_ONLY
                        FileSelectionMode.FILES_AND_DIRECTORIES -> JFileChooser.FILES_AND_DIRECTORIES
                    }
                title?.let {
                    dialogTitle = it
                }
                initPath?.let {
                    currentDirectory = it.toFile()
                }
                showOpenDialog(it)
                selectedFile?.let { file ->
                    action(file.toOkioPath(true))
                } ?: run {
                    cancel?.let { it() }
                }
            }
            _showFileDialog.value = false
        }
    }

    override fun openFileChooserToExport(
        initPath: Path?,
        cancel: (() -> Unit)?,
        action: (Any) -> Unit,
    ) {
        openFileChooser(
            fileSelectionMode = FileSelectionMode.DIRECTORY_ONLY,
            null,
            initPath,
            cancel,
            action,
        )
    }

    override fun openFileChooserToImport(
        initPath: Path?,
        cancel: (() -> Unit)?,
        action: (Any) -> Unit,
    ) {
        openFileChooser(
            fileSelectionMode = FileSelectionMode.FILE_ONLY,
            null,
            initPath,
            cancel,
            action,
        )
    }
}
