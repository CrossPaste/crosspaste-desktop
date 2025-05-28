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
import com.crosspaste.app.AppSize
import com.crosspaste.notification.Message
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.getMessagePainter
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.utils.ColorUtils
import org.koin.compose.koinInject

@Composable
fun ToastView(
    toast: Message,
    onCancelTapped: () -> Unit,
) {
    val appSize = koinInject<AppSize>()

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
                Modifier.background(background, shape = tinyRoundedCornerShape)
                    .padding(tiny)
                    .width(appSize.toastViewWidth),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = small2X),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    modifier = Modifier.size(large2X),
                    painter = getMessagePainter(messageStyle),
                    contentDescription = "toast icon",
                    tint = tint,
                )
                Spacer(Modifier.width(small2X))
                Column(
                    modifier =
                        Modifier.width(appSize.toastViewWidth - 88.dp)
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
                                    .padding(top = small2X, bottom = tiny3X),
                        ) {
                            Text(
                                modifier = Modifier.weight(1f, fill = false),
                                text = message,
                                textAlign = TextAlign.Justify,
                                color = MaterialTheme.colorScheme.contentColorFor(background),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(small2X))
                Icon(
                    modifier =
                        Modifier.size(large2X)
                            .clickable(onClick = onCancelTapped),
                    painter = close(),
                    contentDescription = "Cancel",
                    tint = tint,
                )
            }
        }
    }
}
