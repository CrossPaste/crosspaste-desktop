package com.crosspaste.ui.paste.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.plugin.type.TextTypePlugin
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.refresh
import com.crosspaste.ui.base.save
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.pasteTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.PasteTextEditContentView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()
    val pasteDao = koinInject<PasteDao>()
    val textUpdater = koinInject<TextTypePlugin>()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AppUIColors.appBackground)
                .padding(horizontal = medium)
                .padding(bottom = medium),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
        ) {
            val textPasteItem = getPasteItem(TextPasteItem::class)
            var originText by remember { mutableStateOf(textPasteItem.text) }
            var text by remember { mutableStateOf(textPasteItem.text) }

            val hasChanges by remember(originText, text) {
                mutableStateOf(text != originText && text.isNotEmpty())
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PasteTooltipIconView(
                        painter = refresh(),
                        text = copywriter.getText("reset"),
                        contentDescription = "reset text",
                        tint =
                            if (text != originText && text.isNotEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                    ) {
                        if (hasChanges) {
                            text = originText
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PasteTooltipIconView(
                        painter = save(),
                        text = copywriter.getText("save"),
                        contentDescription = "save text",
                        tint =
                            if (hasChanges) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                    ) {
                        if (hasChanges) {
                            textUpdater
                                .updateText(pasteData, text, textPasteItem, pasteDao)
                                .onSuccess {
                                    originText = text
                                    notificationManager.sendNotification(
                                        title = { copywriter.getText("save_successful") },
                                        messageType = MessageType.Success,
                                    )
                                }.onFailure { error ->
                                    notificationManager.sendNotification(
                                        title = { copywriter.getText("save_failed") },
                                        message = { error.message ?: "" },
                                        messageType = MessageType.Error,
                                    )
                                }
                        }
                    }
                }
            }

            CustomTextField(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp, 0.dp, 8.dp, 8.dp),
                value = text,
                onValueChange = {
                    text = it
                },
                textStyle = pasteTextStyle,
            )
        }
    }
}
