package com.crosspaste.ui.extension

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.DesktopRadioButton
import com.crosspaste.ui.base.HighlightedCard
import com.crosspaste.ui.settings.SettingsText
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

object ProxyType {
    const val HTTP = "HTTP"
    const val SOCKS = "SOCKS"
}

@Composable
fun ProxyView() {
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()

    val config by configManager.config.collectAsState()

    val appSizeValue = LocalDesktopAppSizeValueState.current

    HighlightedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        shape = tinyRoundedCornerShape,
        colors =
            CardDefaults.cardColors(
                containerColor = AppUIColors.generalBackground,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(bottom = small2X),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(appSizeValue.settingsItemHeight)
                        .padding(horizontal = small2X),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopRadioButton(
                    modifier = Modifier.size(medium),
                    selected = !config.useManualProxy,
                    onClick = {
                        configManager.updateConfig("useManualProxy", false)
                    },
                )
                Spacer(modifier = Modifier.width(small2X))
                SettingsText(
                    modifier =
                        Modifier.clickable {
                            configManager.updateConfig("useManualProxy", false)
                        },
                    text = copywriter.getText("no_proxy"),
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(appSizeValue.settingsItemHeight)
                        .padding(horizontal = small2X),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopRadioButton(
                    modifier = Modifier.size(medium),
                    selected = config.useManualProxy,
                    onClick = {
                        configManager.updateConfig("useManualProxy", true)
                    },
                )
                Spacer(modifier = Modifier.width(small2X))
                SettingsText(
                    modifier =
                        Modifier.clickable {
                            configManager.updateConfig("useManualProxy", true)
                        },
                    text = copywriter.getText("manual_proxy_configuration"),
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(appSizeValue.settingsItemHeight * 3)
                        .padding(start = xxxxLarge, end = small2X),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(appSizeValue.settingsItemHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DesktopRadioButton(
                                modifier = Modifier.size(medium),
                                selected = config.proxyType == ProxyType.HTTP,
                                onClick = {
                                    configManager.updateConfig("proxyType", ProxyType.HTTP)
                                },
                            )
                            Spacer(modifier = Modifier.width(small2X))
                            SettingsText(
                                modifier =
                                    Modifier.clickable {
                                        configManager.updateConfig("proxyType", ProxyType.HTTP)
                                    },
                                text = "HTTP",
                            )
                        }

                        Spacer(Modifier.width(large2X))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DesktopRadioButton(
                                modifier = Modifier.size(medium),
                                selected = config.proxyType == ProxyType.SOCKS,
                                onClick = {
                                    configManager.updateConfig("proxyType", ProxyType.SOCKS)
                                },
                            )
                            Spacer(modifier = Modifier.width(small2X))
                            SettingsText(
                                modifier =
                                    Modifier.clickable {
                                        configManager.updateConfig("proxyType", ProxyType.SOCKS)
                                    },
                                text = "SOCKS",
                            )
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(appSizeValue.settingsItemHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsText(text = copywriter.getText("host"))
                        Spacer(modifier = Modifier.width(small2X))
                        CustomTextField(
                            modifier = Modifier.fillMaxWidth().padding(vertical = tiny3X),
                            value = config.proxyHost,
                            onValueChange = {
                                configManager.updateConfig("proxyHost", it)
                            },
                            singleLine = true,
                            contentPadding = PaddingValues(horizontal = tiny),
                        )
                    }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(appSizeValue.settingsItemHeight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Port number
                        SettingsText(text = copywriter.getText("port"))
                        Spacer(modifier = Modifier.width(small2X))
                        CustomTextField(
                            modifier = Modifier.fillMaxWidth().padding(vertical = tiny3X),
                            value = config.proxyPort,
                            onValueChange = {
                                configManager.updateConfig("proxyPort", it.toIntOrNull()?.toString() ?: "")
                            },
                            singleLine = true,
                            contentPadding = PaddingValues(horizontal = medium, vertical = zero),
                        )
                    }
                }
            }
        }
    }
}
