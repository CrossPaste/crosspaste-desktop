package com.crosspaste.ui.base

import com.crosspaste.app.AppFileType
import com.crosspaste.app.CrossPasteWebService
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteContentEditor
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.extension
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getHtmlUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.isVideoFile
import com.crosspaste.utils.namedScope
import com.crosspaste.utils.normalizeUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okio.Path
import java.awt.Color
import java.awt.Desktop
import java.awt.EventQueue
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.swing.JColorChooser

class DesktopUISupport(
    override val crossPasteWebService: CrossPasteWebService,
    private val copywriter: GlobalCopywriter,
    private val notificationManager: NotificationManager,
    private val platform: Platform,
    private val pasteContentEditor: PasteContentEditor,
    private val userDataPathProvider: UserDataPathProvider,
    private val appWindowManager: DesktopAppWindowManager,
    private val actionScope: CoroutineScope = namedScope(ioDispatcher, "DesktopUISupport"),
    private val browserProcessStarter: (List<String>) -> Process = { command ->
        ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
    },
) : UISupport {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    private val htmlUtils = getHtmlUtils()

    init {
        actionScope.launch {
            crossPasteWebService.refresh()
        }
    }

    override fun openUrlInBrowser(url: String) {
        if (platform.isLinux()) {
            launchXdgOpen("failed_to_open_link", url) {
                URI(normalizeUrl(url))
            }
            return
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            runCatching {
                Desktop.getDesktop().browse(URI(normalizeUrl(url)))
            }.onFailure { e ->
                logger.warn(e) { "Failed to open url: $url" }
                notificationManager.sendNotification(
                    title = { it.getText("failed_to_open_link") },
                    message = { url },
                    messageType = MessageType.Error,
                )
            }
        } else {
            notificationManager.sendNotification(
                title = { it.getText("failed_to_open_browser") },
                message = { url },
                messageType = MessageType.Error,
            )
        }
    }

    private fun launchXdgOpen(
        errorTitle: String,
        message: String? = null,
        uriProvider: suspend () -> URI,
    ) {
        actionScope.launch {
            try {
                val uri = withContext(ioDispatcher) { uriProvider() }
                runInterruptible(ioDispatcher) {
                    require(uri.isAbsolute) { "Opening URI must be absolute: $uri" }
                    // AWT's GVfs capability checks do not reflect the desktop's URI handlers.
                    runLauncher(listOf("xdg-open", uri.toString()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to open URI with xdg-open" }
                notificationManager.sendNotification(
                    title = { it.getText(errorTitle) },
                    message = message?.let { text -> { text } },
                    messageType = MessageType.Error,
                )
            }
        }
    }

    private fun runLauncher(command: List<String>) {
        val process = browserProcessStarter(command)
        process.outputStream.close()
        // Some launchers wait for the browser to close; a running process is not a failure.
        if (process.waitFor(30, TimeUnit.SECONDS)) {
            check(process.exitValue() == 0) { "${command[0]} exited with code ${process.exitValue()}" }
        }
    }

    private fun showInLinuxFileManager(filePath: Path) {
        actionScope.launch {
            try {
                runInterruptible(ioDispatcher) {
                    val file = filePath.toNioPath().toAbsolutePath()
                    try {
                        // AWT never supports BROWSE_FILE_DIR on Linux; FileManager1 selects the file
                        // in Nautilus, Dolphin, Nemo and others. dbus-send splits arrays on commas.
                        val uri = file.toUri().toASCIIString().replace(",", "%2C")
                        runLauncher(
                            listOf(
                                "dbus-send",
                                "--session",
                                "--print-reply",
                                "--reply-timeout=5000",
                                "--dest=org.freedesktop.FileManager1",
                                "/org/freedesktop/FileManager1",
                                "org.freedesktop.FileManager1.ShowItems",
                                "array:string:$uri",
                                "string:",
                            ),
                        )
                    } catch (e: IOException) {
                        openParentDirectory(file, e)
                    } catch (e: IllegalStateException) {
                        openParentDirectory(file, e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "Failed to show file in the file manager" }
                notificationManager.sendNotification(
                    title = { it.getText("failed_to_browse_file_pasteboard") },
                    messageType = MessageType.Error,
                )
            }
        }
    }

    private fun openParentDirectory(
        file: java.nio.file.Path,
        cause: Exception,
    ) {
        logger.info(cause) { "FileManager1 unavailable, opening the parent directory instead" }
        val directory = requireNotNull(file.parent) { "File has no parent directory: $file" }
        runLauncher(listOf("xdg-open", directory.toUri().toString()))
    }

    override fun getCrossPasteWebUrl(path: String): String = crossPasteWebService.getWebUrl(copywriter.language(), path)

    override fun openEmailClient(email: String?) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
            email?.let {
                val uriText = "mailto:$email"
                val mailURI = URI(uriText)
                Desktop.getDesktop().mail(mailURI)
            } ?: run {
                Desktop.getDesktop().mail()
            }
        } else {
            notificationManager.sendNotification(
                title = { it.getText("failed_to_open_email_client") },
                message = email?.let { email -> { email } },
                messageType = MessageType.Error,
            )
        }
    }

    override fun openHtml(
        id: Long,
        html: String,
    ) {
        val uriProvider: suspend () -> URI = {
            val fileName = "$id.html"
            val filePath = userDataPathProvider.resolve(fileName, AppFileType.TEMP)
            if (!fileUtils.existFile(filePath)) {
                val utf8Html = htmlUtils.ensureHtmlCharsetUtf8(html)
                fileUtils.writeFile(filePath) { sink ->
                    sink.writeUtf8(utf8Html)
                }
            }
            if (platform.isLinux()) filePath.toNioPath().toUri() else filePath.toFile().toURI()
        }
        if (platform.isLinux()) {
            launchXdgOpen("failed_to_open_html_pasteboard", uriProvider = uriProvider)
        } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            actionScope.launch {
                Desktop.getDesktop().browse(uriProvider())
            }
        } else {
            notificationManager.sendNotification(
                title = { it.getText("failed_to_open_html_pasteboard") },
                messageType = MessageType.Error,
            )
        }
    }

    override fun browseFile(filePath: Path) {
        if (fileUtils.existFile(filePath)) {
            if (platform.isLinux()) {
                showInLinuxFileManager(filePath)
            } else if (Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)
            ) {
                val desktop = Desktop.getDesktop()
                desktop.browseFileDirectory(filePath.toFile())
            } else {
                if (platform.isWindows() && openFileInExplorer(filePath.toFile())) {
                    return
                }
                notificationManager.sendNotification(
                    title = { it.getText("failed_to_browse_file_pasteboard") },
                    messageType = MessageType.Error,
                )
            }
        } else {
            notificationManager.sendNotification(
                title = { it.getText("file_not_found") },
                messageType = MessageType.Error,
            )
        }
    }

    override fun openColorPicker(pasteData: PasteData) {
        pasteData.getPasteItem(ColorPasteItem::class)?.let { pasteItem ->
            val initialColor = Color(pasteItem.color)
            EventQueue.invokeLater {
                val result =
                    JColorChooser.showDialog(
                        null,
                        copywriter.getText("color_picker"),
                        initialColor,
                    )

                if (result != null) {
                    val rgbColor = result.rgb
                    val alpha = result.alpha

                    val newColor =
                        ((alpha.toLong() and 0xFF) shl 24) or
                            ((result.red.toLong() and 0xFF) shl 16) or
                            ((result.green.toLong() and 0xFF) shl 8) or
                            (result.blue.toLong() and 0xFF)

                    logger.info { "Selected color: $rgbColor" }
                    actionScope.launch {
                        val outcome = pasteContentEditor.updateColor(pasteData, newColor.toInt())
                        if (outcome !is PasteContentEditor.EditOutcome.Updated) {
                            notificationManager.sendNotification(
                                title = { copywriter.getText("save_failed") },
                                messageType = MessageType.Error,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openFileInExplorer(file: File): Boolean =
        runCatching {
            val filePath = file.absolutePath
            val command = listOf("explorer.exe", "/select,\"$filePath\"")
            ProcessBuilder(command).start()
            true
        }.onFailure { e ->
            logger.error(e) { "Failed to open file in explorer" }
        }.getOrElse { false }

    override fun openFile(filePath: Path) {
        if (fileUtils.existFile(filePath)) {
            if (platform.isLinux()) {
                launchXdgOpen("failed_to_open_file") { filePath.toNioPath().toUri() }
            } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(filePath.toFile())
            } else {
                notificationManager.sendNotification(
                    title = { it.getText("failed_to_open_file") },
                    messageType = MessageType.Error,
                )
            }
        } else {
            notificationManager.sendNotification(
                title = { it.getText("file_not_found") },
                messageType = MessageType.Error,
            )
        }
    }

    override fun openText(pasteData: PasteData) {
        appWindowManager.showBubbleWindow(pasteData.id)
    }

    override fun openRtf(pasteData: PasteData) {
        val uriProvider: suspend () -> URI = {
            val fileName = "${pasteData.id}.rtf"
            val filePath = userDataPathProvider.resolve(fileName, AppFileType.TEMP)
            if (!fileUtils.existFile(filePath)) {
                pasteData.getPasteItem(PasteRtf::class)?.let {
                    fileUtils.writeFile(filePath) { sink ->
                        sink.writeUtf8(it.rtf)
                    }
                }
            }
            if (platform.isLinux()) filePath.toNioPath().toUri() else filePath.toFile().toURI()
        }
        if (platform.isLinux()) {
            launchXdgOpen("failed_to_open_rtf_pasteboard", uriProvider = uriProvider)
        } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            actionScope.launch {
                Desktop.getDesktop().browse(uriProvider())
            }
        } else {
            notificationManager.sendNotification(
                title = { it.getText("failed_to_open_rtf_pasteboard") },
                messageType = MessageType.Error,
            )
        }
    }

    override fun openPasteData(
        pasteData: PasteData,
        index: Int,
    ) {
        pasteData.pasteAppearItem?.let { item ->
            val pasteType = pasteData.getType()
            when (pasteType) {
                PasteType.TEXT_TYPE -> openText(pasteData)
                PasteType.COLOR_TYPE -> openColorPicker(pasteData)
                PasteType.URL_TYPE -> openUrlInBrowser((item as UrlPasteItem).url)
                PasteType.HTML_TYPE -> appWindowManager.showBubbleWindow(pasteData.id)
                PasteType.RTF_TYPE -> openRtf(pasteData)
                PasteType.FILE_TYPE -> {
                    item as PasteFiles
                    val pathList = item.getFilePaths(userDataPathProvider)
                    if (pathList.isNotEmpty()) {
                        val filepath = pathList[index]
                        if (fileUtils.canPreviewImage(filepath.extension) || filepath.isVideoFile) {
                            openFile(filepath)
                        } else {
                            browseFile(filepath)
                        }
                    }
                }
                PasteType.IMAGE_TYPE -> {
                    item as PasteFiles
                    val pathList = item.getFilePaths(userDataPathProvider)
                    if (pathList.isNotEmpty()) {
                        if (pathList.size == 1) {
                            openFile(pathList[0])
                        } else {
                            browseFile(pathList[index])
                        }
                    }
                }
            }
        }
    }

    override fun jumpPrivacyAccessibility() {
        // Desktop.browse() launches System Settings but drops the pane deep link on
        // macOS 26 (it lands on General), so hand the URL to `open` instead.
        runCatching {
            ProcessBuilder("open", PRIVACY_ACCESSIBILITY_URL).start()
        }.onFailure { e ->
            logger.error(e) { "Failed to open the Accessibility privacy settings" }
        }
    }

    companion object {
        private const val PRIVACY_ACCESSIBILITY_URL =
            "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"
    }
}
