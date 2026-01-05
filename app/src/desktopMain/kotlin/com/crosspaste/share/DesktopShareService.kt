package com.crosspaste.share

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.ui.base.UISupport

class DesktopShareService(
    private val copywriter: GlobalCopywriter,
    notificationManager: NotificationManager,
    pasteboardService: PasteboardService,
    private val uiSupport: UISupport,
) : ShareService {
    override val sharePlatformList: List<SharePlatform> =
        listOf(
            X(uiSupport),
            Weibo(uiSupport),
            Facebook(notificationManager, pasteboardService, uiSupport),
            Reddit(uiSupport),
            LinkedIn(notificationManager, pasteboardService, uiSupport),
            Telegram(uiSupport),
            Mail(notificationManager, uiSupport),
            Clipboard(notificationManager, pasteboardService),
        )
    override val shareContentKey: String = "recommend_content"

    override val shareTitleKey: String = "recommend_title"

    override fun getShareText(): String =
        "${copywriter.getText(shareContentKey)}\n${uiSupport.getCrossPasteWebUrl("download")}"

    override fun getShareTitle(): String = copywriter.getText(shareTitleKey)

    override fun getShareContent(): String = copywriter.getText(shareContentKey)

    override fun getShareUrl(): String = uiSupport.getCrossPasteWebUrl("download")
}
