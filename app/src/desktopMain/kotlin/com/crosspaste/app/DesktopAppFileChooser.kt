package com.crosspaste.app

import com.crosspaste.utils.ioDispatcher
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okio.Path
import okio.Path.Companion.toOkioPath

class DesktopAppFileChooser(
    private val desktopAppWindowManager: DesktopAppWindowManager,
) : AppFileChooser {

    private val ioCoroutineDispatcher = CoroutineScope(SupervisorJob() + ioDispatcher)

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
            ioCoroutineDispatcher.launch {
                when (fileSelectionMode) {
                    FileSelectionMode.FILE_ONLY -> {
                        FileKit.openFilePicker(
                            title = title,
                            directory = initPath?.let { path -> PlatformFile(path.toFile()) },
                        )?.let { platformFile ->
                            action(platformFile.file.toOkioPath(true))
                        } ?: run {
                            cancel?.let { cancelAction -> cancelAction() }
                        }
                    }
                    FileSelectionMode.DIRECTORY_ONLY -> {
                        FileKit.openDirectoryPicker(
                            title = title,
                            directory = initPath?.let { path -> PlatformFile(path.toFile()) },
                        )?.let { platformFile ->
                            action(platformFile.file.toOkioPath(true))
                        } ?: run {
                            cancel?.let { cancelAction -> cancelAction() }
                        }
                    }
                }
            }.apply {
                _showFileDialog.value = false
            }
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
