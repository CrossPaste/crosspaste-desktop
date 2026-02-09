package com.crosspaste.share

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.ui.base.UISupport

class DesktopAppShareService(
    private val copywriter: GlobalCopywriter,
    notificationManager: NotificationManager,
    pasteboardService: PasteboardService,
    private val uiSupport: UISupport,
) : AppShareService {
    override val appSharePlatformList: List<AppSharePlatform> =
        listOf(
            X(uiSupport),
            Weibo(uiSupport),
            Facebook(notificationManager, pasteboardService, uiSupport),
            Reddit(uiSupport),
            LinkedIn(notificationManager, pasteboardService, uiSupport),
            Telegram(uiSupport),
            Mail(notificationManager, pasteboardService, uiSupport),
            Clipboard(notificationManager, pasteboardService),
        )
    override val shareContentKey: String = "share_content"

    override val shareTitleKey: String = "share_title"

    override fun getShareText(): String =
        "${copywriter.getText(shareContentKey)}\n${uiSupport.getCrossPasteWebUrl("download")}"

    override fun getShareTitle(): String = copywriter.getText(shareTitleKey)

    override fun getShareContent(): String = copywriter.getText(shareContentKey)

    override fun getShareUrl(): String = uiSupport.getCrossPasteWebUrl("download")
}
