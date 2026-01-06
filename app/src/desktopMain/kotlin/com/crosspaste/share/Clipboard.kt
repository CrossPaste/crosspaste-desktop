package com.crosspaste.share

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.TextPasteItem.Companion.createTextPasteItem
import com.crosspaste.ui.theme.AppUISize.xxLarge

class Clipboard(
    private val notificationManager: NotificationManager,
    private val pasteboardService: PasteboardService,
) : AppSharePlatform {
    override val platformName: String = "Clipboard"

    @Composable
    override fun ButtonPlatform() {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = "clipboard",
            modifier = Modifier.size(xxLarge),
        )
    }

    override suspend fun action(appShareService: AppShareService) {
        pasteboardService.tryWritePasteboard(
            pasteItem = createTextPasteItem(text = appShareService.getShareText()),
            localOnly = true,
        )
        notificationManager.sendNotification(
            title = { it.getText("copy_successful") },
            messageType = MessageType.Success,
        )
    }
}
