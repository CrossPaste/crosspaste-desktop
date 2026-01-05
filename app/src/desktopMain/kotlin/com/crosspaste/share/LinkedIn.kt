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
import com.crosspaste.ui.base.linkedin
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class LinkedIn(
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
    private val uiSupport: UISupport,
) : SharePlatform {
    override val platformName: String = "LinkedIn"

    @Composable
    override fun ButtonPlatform() {
        Image(
            painter = linkedin(),
            contentDescription = "LinkedIn",
            modifier = Modifier.size(xxLarge),
        )
    }

    override suspend fun action(shareService: ShareService) {
        pasteboardService.tryWritePasteboard(
            pasteItem = createTextPasteItem(text = shareService.getShareText()),
            localOnly = true,
        )
        var url = "https://www.linkedin.com/sharing/share-offsite/?mini=true"

        val appUrl = shareService.getShareUrl()
        val encodedUrl =
            withContext(ioDispatcher) {
                URLEncoder.encode(appUrl, "UTF-8")
            }
        url += "&url=$encodedUrl"

        notificationManager.sendNotification(
            title = { it.getText("copy_successful") },
            messageType = MessageType.Success,
        )
        mainCoroutineDispatcher.launch {
            delay(2000)
            uiSupport.openUrlInBrowser(url)
        }
    }
}
