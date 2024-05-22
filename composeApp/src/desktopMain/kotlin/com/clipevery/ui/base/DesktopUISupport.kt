package com.clipevery.ui.base

import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.utils.getFileUtils
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists

class DesktopUISupport(
    private val toastManager: ToastManager,
    private val copywriter: GlobalCopywriter,
) : UISupport {

    private val fileUtils = getFileUtils()

    override fun openUrlInBrowser(url: String) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Error,
                    "${copywriter.getText("Cant_open_browser")} $url",
                ),
            )
        }
    }

    override fun openEmailClient(email: String) {
        val uriText = "mailto:$email"
        val mailURI = URI(uriText)

        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.MAIL)) {
                desktop.mail(mailURI)
            }
        } else {
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Error,
                    "${copywriter.getText("Official email")} $email",
                ),
            )
        }
    }

    override fun openHtml(html: String) {
        if (Desktop.isDesktopSupported()) {
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
                    copywriter.getText("Failed_to_open_Html_pasteboard"),
                ),
            )
        }
    }

    override fun browseFile(filePath: Path) {
        if (filePath.exists()) {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                desktop.browseFileDirectory(filePath.toFile())
            } else {
                toastManager.setToast(
                    Toast(
                        messageType = MessageType.Error,
                        copywriter.getText("Failed_to_browse_File_pasteboard"),
                    ),
                )
            }
        } else {
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Error,
                    copywriter.getText("File_not_found"),
                ),
            )
        }
    }

    override fun openImage(imagePath: Path) {
        if (imagePath.exists()) {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                desktop.open(imagePath.toFile())
            } else {
                toastManager.setToast(
                    Toast(
                        messageType = MessageType.Error,
                        copywriter.getText("Failed_to_open_Image_pasteboard"),
                    ),
                )
            }
        } else {
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Error,
                    copywriter.getText("File_not_found"),
                ),
            )
        }
    }

    override fun openText(text: String) {
        if (Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            fileUtils.createTempFile(
                text.toByteArray(),
                fileUtils.createRandomFileName("txt"),
            )?.let { path ->
                desktop.open(path.toFile())
            }
        } else {
            toastManager.setToast(
                Toast(
                    messageType = MessageType.Error,
                    copywriter.getText("Failed_to_open_Text_pasteboard"),
                ),
            )
        }
    }
}
