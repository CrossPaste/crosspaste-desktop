package com.crosspaste.app

import com.crosspaste.utils.GlobalCoroutineScope.ioCoroutineDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okio.Path
import okio.Path.Companion.toOkioPath

class DesktopAppFileChooser(
    private val desktopAppWindowManager: DesktopAppWindowManager,
) : AppFileChooser {

    private val logger = KotlinLogging.logger {}

    private val _showFileDialog: MutableStateFlow<Boolean> = MutableStateFlow(false)

    override val showFileDialog: StateFlow<Boolean> = _showFileDialog

    override fun openFileChooser(
        fileSelectionMode: FileSelectionMode,
        initPath: Path?,
        cancel: (() -> Unit)?,
        action: (Any) -> Unit,
    ) {
        showDialog(cancel, action) {
            when (fileSelectionMode) {
                FileSelectionMode.FILE_ONLY -> {
                    FileKit.openFilePicker(directory = initPath?.toPlatformFile())
                }
                FileSelectionMode.DIRECTORY_ONLY -> {
                    FileKit.openDirectoryPicker(directory = initPath?.toPlatformFile())
                }
            }
        }
    }

    /**
     * Picks an application on disk (filtered to [extensions]) for the clipboard
     * source list. [action] runs on the IO dispatcher with the chosen path.
     */
    fun openAppChooser(
        extensions: Set<String>,
        initPath: Path?,
        action: (Path) -> Unit,
    ) {
        showDialog(cancel = null, action = action) {
            FileKit.openFilePicker(
                type = FileKitType.File(extensions),
                directory = initPath?.toPlatformFile(),
            )
        }
    }

    override fun openFileChooserToExport(
        initPath: Path?,
        cancel: (() -> Unit)?,
        action: (Any) -> Unit,
    ) {
        openFileChooser(
            fileSelectionMode = FileSelectionMode.DIRECTORY_ONLY,
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
        showDialog(cancel, action) {
            FileKit.openFilePicker(
                type = FileKitType.File(IMPORT_FILE_EXTENSION),
                directory = initPath?.toPlatformFile(),
            )
        }
    }

    /**
     * Runs [pick] on the IO dispatcher while [showFileDialog] is raised, then hands
     * the chosen path to [action] or calls [cancel] when nothing was chosen or the
     * dialog failed.
     */
    private fun showDialog(
        cancel: (() -> Unit)?,
        action: (Path) -> Unit,
        pick: suspend () -> PlatformFile?,
    ) {
        desktopAppWindowManager.mainComposeWindow ?: return
        _showFileDialog.value = true
        ioCoroutineDispatcher.launch {
            try {
                pick()?.let { platformFile ->
                    action(platformFile.file.toOkioPath(true))
                } ?: cancel?.invoke()
            } catch (e: Exception) {
                logger.error(e) { "Exception when open file chooser dialog" }
                cancel?.invoke()
            } finally {
                _showFileDialog.value = false
            }
        }
    }

    private fun Path.toPlatformFile(): PlatformFile = PlatformFile(toFile())

    companion object {
        /** Extension of the archives written by PasteExportService. */
        const val IMPORT_FILE_EXTENSION = "data"
    }
}
