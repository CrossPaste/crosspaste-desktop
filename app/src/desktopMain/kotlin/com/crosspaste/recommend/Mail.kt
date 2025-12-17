package com.crosspaste.recommend

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.mail
import com.crosspaste.ui.theme.AppUISize.xxLarge
import kotlinx.coroutines.delay

class Mail(
    private val notificationManager: NotificationManager,
    private val uiSupport: UISupport,
) : RecommendationPlatform {
    override val platformName: String = "Mail"

    @Composable
    override fun ButtonPlatform(onClick: () -> Unit) {
        ButtonContentView(onClick) {
            Icon(
                painter = mail(),
                contentDescription = "mail",
                modifier = Modifier.size(xxLarge),
            )
        }
    }

    override suspend fun action(recommendationService: RecommendationService) {
        notificationManager.sendNotification(
            title = { it.getText("copy_successful") },
            messageType = MessageType.Success,
        )
        delay(2000)
        uiSupport.openEmailClient(null)
    }
}
