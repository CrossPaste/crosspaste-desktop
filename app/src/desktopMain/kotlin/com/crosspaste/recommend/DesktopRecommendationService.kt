package com.crosspaste.recommend

import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.ui.base.UISupport

class DesktopRecommendationService(
    private val copywriter: GlobalCopywriter,
    notificationManager: NotificationManager,
    pasteboardService: PasteboardService,
    private val uiSupport: UISupport,
) : RecommendationService {
    override val recommendPlatformList: List<RecommendationPlatform> =
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
    override val recommendContentKey: String = "recommend_content"

    override val recommendTitleKey: String = "recommend_title"

    override fun getRecommendText(): String {
        return "${copywriter.getText(recommendContentKey)}\n${uiSupport.getCrossPasteWebUrl("download")}"
    }

    override fun getRecommendTitle(): String {
        return copywriter.getText(recommendTitleKey)
    }

    override fun getRecommendContent(): String {
        return copywriter.getText(recommendContentKey)
    }

    override fun getRecommendUrl(): String {
        return uiSupport.getCrossPasteWebUrl("download")
    }
}
