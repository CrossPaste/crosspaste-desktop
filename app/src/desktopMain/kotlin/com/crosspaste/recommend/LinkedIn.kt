package com.crosspaste.recommend

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

class LinkedIn(
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
    private val uiSupport: UISupport,
) : RecommendationPlatform {
    override val platformName: String = "LinkedIn"

    @Composable
    override fun ButtonPlatform(onClick: () -> Unit) {
        ButtonContentView(onClick) {
            Image(
                painter = linkedin(),
                contentDescription = "LinkedIn",
                modifier = Modifier.size(xxLarge),
            )
        }
    }

    override suspend fun action(recommendationService: RecommendationService) {
        pasteboardService.tryWritePasteboard(
            pasteItem = createTextPasteItem(text = recommendationService.getRecommendText()),
            localOnly = true,
        )
        var url = "https://www.linkedin.com/sharing/share-offsite/?mini=true"

        val appUrl = recommendationService.getRecommendUrl()
        val encodedUrl = URLEncoder.encode(appUrl, "UTF-8")
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
