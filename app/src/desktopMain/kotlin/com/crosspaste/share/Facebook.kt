package com.crosspaste.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.TextPasteItem.Companion.createTextPasteItem
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.facebook
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class Facebook(
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
    private val uiSupport: UISupport,
) : AppSharePlatform {
    override val platformName: String = "Facebook"

    @Composable
    override fun ButtonPlatform() {
        Image(
            painter = facebook(),
            contentDescription = "facebook",
            modifier = Modifier.size(xxLarge),
        )
    }

    override suspend fun action(appShareService: AppShareService) {
        pasteboardService.tryWritePasteboard(
            pasteItem = createTextPasteItem(text = appShareService.getShareText()),
            localOnly = true,
        )
        val appUrl = appShareService.getShareUrl()
        val encodedUrl =
            withContext(ioDispatcher) {
                URLEncoder.encode(appUrl, "UTF-8")
            }
        val facebookUrl = "https://www.facebook.com/sharer/sharer.php?u=$encodedUrl"
        notificationManager.sendNotification(
            title = { it.getText("copy_successful") },
            messageType = MessageType.Success,
        )
        delay(2000)
        uiSupport.openUrlInBrowser(facebookUrl)
    }
}
