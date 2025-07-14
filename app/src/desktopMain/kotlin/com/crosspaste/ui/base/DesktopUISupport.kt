package com.crosspaste.ui.base

import androidx.compose.ui.awt.ComposeWindow
import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppUrls
import com.crosspaste.app.AppWindowManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteType
import com.crosspaste.i18n.DesktopGlobalCopywriter.Companion.ZH
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteColor
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.plugin.type.ColorTypePlugin
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.ui.PasteTextEdit
import com.crosspaste.utils.extension
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.getHtmlUtils
import com.google.common.io.Files
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import java.awt.Color
import java.awt.Desktop
import java.awt.EventQueue
import java.io.File
import java.net.URI
import javax.swing.JColorChooser

class DesktopUISupport(
    private val appUrls: AppUrls,
    private val appWindowManager: AppWindowManager,
    private val colorTypePlugin: ColorTypePlugin,
    private val copywriter: GlobalCopywriter,
    private val notificationManager: NotificationManager,
    private val pasteDao: PasteDao,
    private val platform: Platform,
    private val userDataPathProvider: UserDataPathProvider,
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
                message = email?.let { email -> { it -> email } },
                messageType = MessageType.Error,
            )
        }
    }

    override fun openHtml(
        id: Long,
        html: String,
    ) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            val fileName = "$id.html"
            val filePath = userDataPathProvider.resolve(fileName, AppFileType.TEMP)
            val file = filePath.toFile()
            if (!file.exists()) {
                val utf8Html = htmlUtils.ensureHtmlCharsetUtf8(html)
                Files.write(utf8Html.encodeToByteArray(), file)
            }
            Desktop.getDesktop().browse(file.toURI())
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
        pasteData.getPasteItem(PasteColor::class)?.let { pasteColor ->
            val initialColor = Color(pasteColor.color.toInt())
            EventQueue.invokeLater {
                JColorChooser(initialColor)
                val result =
                    JColorChooser.showDialog(
                        ComposeWindow(),
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
                    colorTypePlugin.updateColor(
                        pasteData,
                        newColor,
                        (pasteColor as ColorPasteItem),
                        pasteDao,
                    )
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
        appWindowManager.toScreen(PasteTextEdit, pasteData)
    }

    override fun openRtf(pasteData: PasteData) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            val fileName = "${pasteData.id}.rtf"
            val filePath = userDataPathProvider.resolve(fileName, AppFileType.TEMP)
            val file = filePath.toFile()
            if (!file.exists()) {
                pasteData.getPasteItem(PasteRtf::class)?.let {
                    Files.write(it.rtf.encodeToByteArray(), file)
                }
            }
            Desktop.getDesktop().browse(file.toURI())
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
