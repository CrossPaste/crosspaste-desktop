package com.crosspaste.ui.base

import com.crosspaste.app.AppFileType
import com.crosspaste.app.AppUrls
import com.crosspaste.app.AppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.i18n.GlobalCopywriterImpl.Companion.ZH
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.ui.ScreenType
import com.crosspaste.utils.extension
import com.crosspaste.utils.getFileUtils
import com.google.common.io.Files
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import org.mongodb.kbson.ObjectId
import java.awt.Desktop
import java.io.File
import java.net.URI

class DesktopUISupport(
    private val appUrls: AppUrls,
    private val appWindowManager: AppWindowManager,
    private val copywriter: GlobalCopywriter,
    private val notificationManager: NotificationManager,
    private val userDataPathProvider: UserDataPathProvider,
) : UISupport {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override fun openUrlInBrowser(url: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            notificationManager.sendNotification(
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
            notificationManager.sendNotification(
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
            notificationManager.sendNotification(
                message = copywriter.getText("failed_to_open_html_pasteboard"),
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
                if (getPlatform().isWindows() && openFileInExplorer(filePath.toFile())) {
                    return
                }
                notificationManager.sendNotification(
                    message = copywriter.getText("failed_to_browse_file_pasteboard"),
                    messageType = MessageType.Error,
                )
            }
        } else {
            notificationManager.sendNotification(
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
        if (fileUtils.existFile(imagePath)) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(imagePath.toFile())
            } else {
                notificationManager.sendNotification(
                    message = copywriter.getText("failed_to_open_image_pasteboard"),
                    messageType = MessageType.Error,
                )
            }
        } else {
            notificationManager.sendNotification(
                message = copywriter.getText("file_not_found"),
                messageType = MessageType.Error,
            )
        }
    }

    override fun openText(pasteData: PasteData) {
        appWindowManager.toScreen(ScreenType.PASTE_TEXT_EDIT, pasteData)
    }

    override fun openRtf(pasteData: PasteData) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            val fileName = "${pasteData.id.toHexString()}.rtf"
            val filePath = userDataPathProvider.resolve(fileName, AppFileType.TEMP)
            val file = filePath.toFile()
            if (!file.exists()) {
                pasteData.getPasteItem(PasteRtf::class)?.let {
                    Files.write(it.rtf.toByteArray(), file)
                }
            }
            Desktop.getDesktop().browse(file.toURI())
        } else {
            notificationManager.sendNotification(
                message = copywriter.getText("failed_to_open_rtf_pasteboard"),
                messageType = MessageType.Error,
            )
        }
    }

    override fun openPasteData(
        pasteData: PasteData,
        index: Int,
    ) {
        pasteData.getPasteItem()?.let { item ->
            when (pasteData.pasteType) {
                PasteType.TEXT -> openText(pasteData)
                PasteType.URL -> openUrlInBrowser((item as UrlPasteItem).url)
                PasteType.HTML -> openHtml(pasteData.id, (item as HtmlPasteItem).html)
                PasteType.RTF -> openRtf(pasteData)
                PasteType.FILE -> {
                    item as PasteFiles
                    val pasteFiles = item.getPasteFiles(userDataPathProvider)
                    if (pasteFiles.isNotEmpty()) {
                        val filepath = pasteFiles[index].getFilePath()
                        if (fileUtils.canPreviewImage(filepath.extension)) {
                            openImage(filepath)
                        } else {
                            browseFile(filepath)
                        }
                    }
                }
                PasteType.IMAGE -> {
                    item as PasteFiles
                    val pasteFiles = item.getPasteFiles(userDataPathProvider)
                    if (pasteFiles.isNotEmpty()) {
                        if (pasteFiles.size == 1) {
                            openImage(pasteFiles[0].getFilePath())
                        } else {
                            browseFile(pasteFiles[index].getFilePath())
                        }
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
