package com.crosspaste.ui.base

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppUrls
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.ZH
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.paste.item.UpdatePasteItemHelper
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.extension
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getHtmlUtils
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path
import java.awt.Color
import java.awt.Desktop
import java.awt.EventQueue
import java.io.File
import java.net.URI
import javax.swing.JColorChooser

class DesktopUISupport(
    private val appUrls: AppUrls,
    private val copywriter: GlobalCopywriter,
    private val notificationManager: NotificationManager,
    private val platform: Platform,
    private val updatePasteItemHelper: UpdatePasteItemHelper,
    private val userDataPathProvider: UserDataPathProvider,
    private val appWindowManager: DesktopAppWindowManager,
    private val actionScope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob()),
) : UISupport {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    private val htmlUtils = getHtmlUtils()

    override fun openUrlInBrowser(url: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            notificationManager.sendNotification(
                title = { it.getText("failed_to_open_browser") },
                message = { url },
                messageType = MessageType.Error,
            )
        }
    }

    override fun getCrossPasteWebUrl(path: String): String {
        val webPath =
            when (val language = copywriter.language()) {
                ZH -> path
                else -> "$language/$path"
            }
        return "${appUrls.homeUrl}/$webPath"
    }

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
                title = { it.getText("cant_open_email_client") },
                message = email?.let { email -> { email } },
                messageType = MessageType.Error,
            )
        }
    }

    override fun openHtml(
        id: Long,
        html: String,
    ) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            actionScope.launch {
                val fileName = "$id.html"
                val filePath = userDataPathProvider.resolve(fileName, AppFileType.TEMP)
                if (!fileUtils.existFile(filePath)) {
                    val utf8Html = htmlUtils.ensureHtmlCharsetUtf8(html)
                    fileUtils.writeFile(filePath) { sink ->
                        sink.writeUtf8(utf8Html)
                    }
                }
                Desktop.getDesktop().browse(filePath.toFile().toURI())
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
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
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
                        updatePasteItemHelper.updateColor(
                            pasteData,
                            newColor,
                            pasteItem,
                        )
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

    override fun openImage(imagePath: Path) {
        if (fileUtils.existFile(imagePath)) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(imagePath.toFile())
            } else {
                notificationManager.sendNotification(
                    title = { it.getText("failed_to_open_image_pasteboard") },
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
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            actionScope.launch {
                val fileName = "${pasteData.id}.rtf"
                val filePath = userDataPathProvider.resolve(fileName, AppFileType.TEMP)
                if (!fileUtils.existFile(filePath)) {
                    pasteData.getPasteItem(PasteRtf::class)?.let {
                        fileUtils.writeFile(filePath) { sink ->
                            sink.writeUtf8(it.rtf)
                        }
                    }
                }
                Desktop.getDesktop().browse(filePath.toFile().toURI())
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
                PasteType.HTML_TYPE -> openHtml(pasteData.id, (item as HtmlPasteItem).html)
                PasteType.RTF_TYPE -> openRtf(pasteData)
                PasteType.FILE_TYPE -> {
                    item as PasteFiles
                    val pathList = item.getFilePaths(userDataPathProvider)
                    if (pathList.isNotEmpty()) {
                        val filepath = pathList[index]
                        if (fileUtils.canPreviewImage(filepath.extension)) {
                            openImage(filepath)
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
                            openImage(pathList[0])
                        } else {
                            browseFile(pathList[index])
                        }
                    }
                }
            }
        }
    }

    override fun jumpPrivacyAccessibility() {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop
                .getDesktop()
                .browse(URI("x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"))
        }
    }
}
