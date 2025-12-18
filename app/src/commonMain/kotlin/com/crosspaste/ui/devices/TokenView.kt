package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.AppTokenApi
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.base.close
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.generalTitleTextStyle
import com.crosspaste.ui.theme.AppUIFont.tokenTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny4XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
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
            Box(
                modifier =
                    Modifier
                        .width(appSizeValue.tokenViewWidth)
                        .wrapContentHeight()
                        .background(Color.Transparent)
                        .shadow(small),
            ) {
                Column(
                    modifier =
                        Modifier
                            .wrapContentSize()
                            .clip(tiny3XRoundedCornerShape)
                            .align(Alignment.Center)
                            .background(AppUIColors.topBackground),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .wrapContentHeight()
                                .padding(horizontal = tiny)
                                .padding(top = tiny),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = copywriter.getText("token"),
                            color =
                                MaterialTheme.colorScheme.contentColorFor(
                                    AppUIColors.topBackground,
                                ),
                            style = generalTitleTextStyle,
                        )

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(end = tiny3X),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(medium * 2)
                                        .clip(mediumRoundedCornerShape)
                                        .clickable {
                                            appTokenApi.stopRefresh(hideToken = true)
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = close(),
                                    contentDescription = "Close",
                                    modifier = Modifier.size(medium),
                                    tint =
                                        MaterialTheme.colorScheme.contentColorFor(
                                            AppUIColors.topBackground,
                                        ),
                                )
                            }
                        }
                    }
                    Row(
                        modifier =
                            Modifier
                                .padding(horizontal = tiny3X, vertical = small2X)
                                .wrapContentSize(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        OTPCodeBox()
                    }
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
                .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(small2X)) {
            token.forEach { char ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, tiny3XRoundedCornerShape)
                            .border(tiny5X, AppUIColors.importantColor, tiny3XRoundedCornerShape)
                            .padding(vertical = tiny, horizontal = small2X),
                ) {
                    Text(
                        text = char.toString(),
                        color = AppUIColors.importantColor,
                        style = tokenTextStyle,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(small3X))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = small3X),
        ) {
            LinearProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(tiny3X)
                        .clip(tiny4XRoundedCornerShape),
                progress = { progress },
            )
        }
    }
}
