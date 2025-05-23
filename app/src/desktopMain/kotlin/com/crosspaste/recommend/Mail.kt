package com.crosspaste.recommend

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.mail
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }

    override fun action(recommendationService: RecommendationService) {
        notificationManager.sendNotification(
            title = { it.getText("copy_successful") },
            messageType = MessageType.Success,
        )
        mainCoroutineDispatcher.launch {
            delay(2000)
            uiSupport.openEmailClient(null)
        }
    }
}
