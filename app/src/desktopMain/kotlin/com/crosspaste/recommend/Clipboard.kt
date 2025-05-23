package com.crosspaste.recommend

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.TextPasteItem.Companion.createTextPasteItem
import com.crosspaste.ui.base.clipboard

class Clipboard(
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
) : RecommendationPlatform {
    override val platformName: String = "Clipboard"

    @Composable
    override fun ButtonPlatform(onClick: () -> Unit) {
        ButtonContentView(onClick) {
            Icon(
                painter = clipboard(),
                contentDescription = "clipboard",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }

    override fun action(recommendationService: RecommendationService) {
        pasteboardService.tryWritePasteboard(
            pasteItem = createTextPasteItem(text = recommendationService.getRecommendText()),
            localOnly = true,
        )
        notificationManager.sendNotification(
            title = { it.getText("copy_successful") },
            messageType = MessageType.Success,
        )
    }
}
