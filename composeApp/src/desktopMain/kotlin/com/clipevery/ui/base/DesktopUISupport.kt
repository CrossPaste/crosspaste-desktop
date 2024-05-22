package com.clipevery.ui.base

import com.clipevery.i18n.GlobalCopywriter
import java.awt.Desktop
import java.net.URI

class DesktopUISupport(
    private val toastManager: ToastManager,
    private val copywriter: GlobalCopywriter,
) : UISupport {

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
}
