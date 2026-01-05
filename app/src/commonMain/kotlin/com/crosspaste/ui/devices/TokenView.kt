package com.crosspaste.ui.devices

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.AppTokenApi
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

@Composable
fun TokenView(intOffset: IntOffset) {
    val appTokenApi = koinInject<AppTokenApi>()
    val copywriter = koinInject<GlobalCopywriter>()
    val appSizeValue = LocalAppSizeValueState.current
    val showToken by appTokenApi.showToken.collectAsState()

    if (showToken) {
        Popup(
            alignment = Alignment.TopCenter,
            offset = intOffset,
            properties = PopupProperties(clippingEnabled = false),
        ) {
            Surface(
                modifier =
                    Modifier
                        .width(appSizeValue.tokenViewWidth)
                        .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = small,
                shadowElevation = small,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = small2X),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = small, start = small2X, end = tiny),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = copywriter.getText("token"),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        // Close Button aligned to the end
                        IconButton(
                            onClick = { appTokenApi.stopRefresh(hideToken = true) },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(medium),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Token Display Section
                    OTPCodeBox()
                }
            }
        }
    }
}

@Composable
private fun OTPCodeBox() {
    val appTokenApi = koinInject<AppTokenApi>()
    val progress by appTokenApi.refreshProgress.collectAsState()
    val token by appTokenApi.token.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = small2X),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(small),
            modifier = Modifier.padding(vertical = small2X),
        ) {
            token.forEach { char ->
                TokenDisplayBox(char = char.toString())
            }
        }

        Spacer(modifier = Modifier.height(small))

        LinearProgressIndicator(
            progress = { progress },
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .height(tiny2X)
                    .clip(MaterialTheme.shapes.extraLarge),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun TokenDisplayBox(
    char: String,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium

    Surface(
        modifier =
            modifier
                .size(width = xxxLarge, height = xxxxLarge)
                .border(
                    width = tiny5X,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    shape = shape,
                ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = zero,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = char,
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                    ),
            )
        }
    }
}
