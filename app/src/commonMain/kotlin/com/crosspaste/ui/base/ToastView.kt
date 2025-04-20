package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.crosspaste.notification.Message
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.getMessagePainter
import com.crosspaste.utils.ColorUtils

@Composable
fun ToastView(
    toast: Message,
    onCancelTapped: () -> Unit,
) {
    val messageStyle by remember {
        mutableStateOf(toast.messageType.getMessageStyle())
    }

    Box(
        modifier =
            Modifier
                .wrapContentSize()
                .background(Color.Transparent)
                .shadow(15.dp),
    ) {
        val background =
            if (toast.messageType == MessageType.Error) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }

        val tint =
            ColorUtils.getAdaptiveColor(
                background,
                messageStyle.baseColor.targetHue,
            )
        Column(
            modifier =
                Modifier.background(background, shape = RoundedCornerShape(8.dp))
                    .padding(all = 8.dp)
                    .width(280.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .width(280.dp)
                        .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = getMessagePainter(messageStyle),
                    contentDescription = "toast icon",
                    tint = tint,
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier =
                        Modifier.width(192.dp)
                            .wrapContentHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .wrapContentHeight(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = toast.title,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.contentColorFor(background),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    lineHeight = TextUnit.Unspecified,
                                ),
                        )
                    }
                    toast.message?.let { message ->
                        Row(
                            modifier =
                                Modifier
                                    .padding(top = 12.dp, bottom = 4.dp),
                        ) {
                            Text(
                                modifier = Modifier.weight(1f, fill = false),
                                text = message,
                                textAlign = TextAlign.Start,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Icon(
                    modifier =
                        Modifier.size(20.dp)
                            .clickable(onClick = onCancelTapped),
                    painter = close(),
                    contentDescription = "Cancel",
                    tint = tint,
                )
            }
        }
    }
}
