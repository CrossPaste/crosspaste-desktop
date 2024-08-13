package com.crosspaste.ui.base

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppUrls
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.dao.paste.PasteType
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.i18n.GlobalCopywriterImpl.Companion.ZH
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.currentPlatform
import com.crosspaste.ui.paste.preview.getPasteItem
import com.google.common.io.Files
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.mongodb.kbson.ObjectId
import java.awt.Desktop
import java.io.File
import java.net.URI

class DesktopUISupport(
    private val appUrls: AppUrls,
    private val copywriter: GlobalCopywriter,
    private val notificationManager: NotificationManager,
    private val userDataPathProvider: UserDataPathProvider,
) : UISupport {

    private val logger = KotlinLogging.logger {}

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

    override fun openCrossPasteWebInBrowser(path: String) {
        val webPath =
            when (val language = copywriter.language()) {
                ZH -> path
                else -> "$language/$path"
            }
        openUrlInBrowser("${appUrls.homeUrl}/$webPath")
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

    override fun openHtml(
        objectId: ObjectId,
        html: String,
    ) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            val fileName = "${objectId.toHexString()}.html"
            val filePath = userDataPathProvider.resolve(fileName, AppFileType.TEMP)
            val file = filePath.toFile()
            if (!file.exists()) {
                Files.write(html.toByteArray(), file)
            }
            Desktop.getDesktop().browse(file.toURI())
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

    override fun openText(
        objectId: ObjectId,
        text: String,
    ) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            val fileName = "${objectId.toHexString()}.txt"
            val filePath = userDataPathProvider.resolve(fileName, AppFileType.TEMP)
            val file = filePath.toFile()
            if (!file.exists()) {
                Files.write(text.toByteArray(), file)
            }
            Desktop.getDesktop().open(file)
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
                PasteType.TEXT -> openText(pasteData.id, (item as TextPasteItem).text)
                PasteType.URL -> openUrlInBrowser((item as UrlPasteItem).url)
                PasteType.HTML -> openHtml(pasteData.id, (item as HtmlPasteItem).html)
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
