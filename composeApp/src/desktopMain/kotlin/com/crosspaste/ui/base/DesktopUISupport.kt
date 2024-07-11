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
    private val toastManager: ToastManager,
    private val copywriter: GlobalCopywriter,
) : UISupport {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    override fun openUrlInBrowser(url: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Error,
                    "${copywriter.getText("cant_open_browser")} $url",
                ),
            )
        }
    }

    override fun openEmailClient(email: String) {
        val uriText = "mailto:$email"
        val mailURI = URI(uriText)

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
            Desktop.getDesktop().mail(mailURI)
        } else {
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Error,
                    "${copywriter.getText("official_email")} $email",
                ),
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
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Error,
                    copywriter.getText("failed_to_open_Html_pasteboard"),
                ),
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

                toastManager.setToast(
                    Toast(
                        messageType = MessageType.Error,
                        copywriter.getText("failed_to_browse_File_pasteboard"),
                    ),
                )
            }
        } else {
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Error,
                    copywriter.getText("file_not_found"),
                ),
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
                toastManager.setToast(
                    Toast(
                        messageType = MessageType.Error,
                        copywriter.getText("failed_to_open_Image_pasteboard"),
                    ),
                )
            }
        } else {
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Error,
                    copywriter.getText("file_not_found"),
                ),
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
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Error,
                    copywriter.getText("failed_to_open_Text_pasteboard"),
                ),
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
