package com.crosspaste.ui.base

import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.platform.currentPlatform
import com.crosspaste.ui.paste.preview.getPasteItem
import com.crosspaste.utils.getFileUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.awt.Desktop
import java.io.File
import java.net.URI

class DesktopUISupport(
    private val notificationManager: NotificationManager,
    private val copywriter: GlobalCopywriter,
) : UISupport {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override fun openUrlInBrowser(url: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            notificationManager.addNotification(
                message = "${copywriter.getText("cant_open_browser")}  $url",
                messageType = MessageType.Error,
            )
        }
    }

    override fun openEmailClient(email: String) {
        val uriText = "mailto:$email"
        val mailURI = URI(uriText)

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
            Desktop.getDesktop().mail(mailURI)
        } else {
            notificationManager.addNotification(
                message = "${copywriter.getText("cant_open_email_client")} $email",
                messageType = MessageType.Error,
            )
        }
    }

    override fun openHtml(html: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            fileUtils.createTempFile(
                html.toByteArray(),
                fileUtils.createRandomFileName("html"),
            )?.let { path ->
                val desktop = Desktop.getDesktop()
                desktop.browse(path.toFile().toURI())
            }
        } else {
            notificationManager.addNotification(
                message = copywriter.getText("failed_to_open_Html_pasteboard"),
                messageType = MessageType.Error,
            )
        }
    }

    override fun browseFile(filePath: Path) {
        if (FileSystem.SYSTEM.exists(filePath)) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                val desktop = Desktop.getDesktop()
                desktop.browseFileDirectory(filePath.toFile())
            } else {
                if (currentPlatform().isWindows() && openFileInExplorer(filePath.toFile())) {
                    return
                }
                notificationManager.addNotification(
                    message = copywriter.getText("failed_to_browse_File_pasteboard"),
                    messageType = MessageType.Error,
                )
            }
        } else {
            notificationManager.addNotification(
                message = copywriter.getText("file_not_found"),
                messageType = MessageType.Error,
            )
        }
    }

    private fun openFileInExplorer(file: File): Boolean {
        try {
            val filePath = file.absolutePath
            val command = "explorer.exe /select,\"$filePath\""
            Runtime.getRuntime().exec(command)
            return true
        } catch (e: Exception) {
            logger.error(e) { "Failed to open file in explorer" }
            return false
        }
    }

    override fun openImage(imagePath: Path) {
        if (FileSystem.SYSTEM.exists(imagePath)) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(imagePath.toFile())
            } else {
                notificationManager.addNotification(
                    message = copywriter.getText("failed_to_open_Image_pasteboard"),
                    messageType = MessageType.Error,
                )
            }
        } else {
            notificationManager.addNotification(
                message = copywriter.getText("file_not_found"),
                messageType = MessageType.Error,
            )
        }
    }

    override fun openText(text: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            fileUtils.createTempFile(
                text.toByteArray(),
                fileUtils.createRandomFileName("txt"),
            )?.let { path ->
                Desktop.getDesktop().open(path.toFile())
            }
        } else {
            notificationManager.addNotification(
                message = copywriter.getText("failed_to_open_Text_pasteboard"),
                messageType = MessageType.Error,
            )
        }
    }

    override fun openPasteData(pasteData: PasteData) {
        pasteData.getPasteItem()?.let { item ->
            when (pasteData.pasteType) {
                PasteType.TEXT -> openText((item as TextPasteItem).text)
                PasteType.URL -> openUrlInBrowser((item as UrlPasteItem).url)
                PasteType.HTML -> openHtml((item as HtmlPasteItem).html)
                PasteType.FILE -> {
                    val relativePathList = (item as FilesPasteItem).relativePathList
                    if (relativePathList.size > 0) {
                        browseFile(relativePathList[0].toPath())
                    }
                }
                PasteType.IMAGE -> {
                    val relativePathList = (item as ImagesPasteItem).relativePathList
                    if (relativePathList.size > 0) {
                        browseFile(relativePathList[0].toPath())
                    }
                }
            }
        }
    }

    override fun jumpPrivacyAccessibility() {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop()
                .browse(URI("x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"))
        }
    }
}
