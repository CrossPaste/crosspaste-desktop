package com.crosspaste.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.mail
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import kotlinx.coroutines.delay

class Mail(
    private val notificationManager: NotificationManager,
    private val uiSupport: UISupport,
) : AppSharePlatform {
    override val platformName: String = "Mail"

    @Composable
    override fun ButtonPlatform() {
        Box(
            modifier =
                Modifier
                    .size(huge)
                    .background(
                        Color(0xFFE8F0FE),
                        mediumRoundedCornerShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = mail(),
                contentDescription = "mail",
                modifier = Modifier.size(xxLarge),
                tint = Color(0xFF4285F4),
            )
        }
    }

    override suspend fun action(appShareService: AppShareService) {
        notificationManager.sendNotification(
            title = { it.getText("copy_successful") },
            messageType = MessageType.Success,
        )
        delay(2000)
        uiSupport.openEmailClient(null)
    }
}
