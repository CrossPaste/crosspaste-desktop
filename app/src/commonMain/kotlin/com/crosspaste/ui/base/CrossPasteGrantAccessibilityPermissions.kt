package com.crosspaste.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.crosspaste.app.AppLock
import com.crosspaste.app.AppRestartService
import com.crosspaste.app.ExitMode
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalExitApplication
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import com.crosspaste.ui.theme.CrossPasteTheme.Theme
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun CrossPasteGrantAccessibilityPermissions(
    checkAccessibilityPermissionsFun: () -> Boolean,
    setOnTop: (Boolean) -> Unit,
) {
    val exitApplication = LocalExitApplication.current
    val copywriter = koinInject<GlobalCopywriter>()
    val appLock = koinInject<AppLock>()
    val appRestartService = koinInject<AppRestartService>()
    val uiSupport = koinInject<UISupport>()

    var toRestart by remember { mutableStateOf(false) }

    var restarting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            if (checkAccessibilityPermissionsFun()) {
                setOnTop(true)
                toRestart = true
                break
            } else {
                delay(500)
            }
        }
    }

    Theme {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = medium, vertical = tiny),
        ) {
            Spacer(modifier = Modifier.height(xLarge))
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .wrapContentHeight(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CrossPasteLogoView(
                    size = xxxLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(tiny))
                Text(
                    text = copywriter.getText("permission"),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(tiny))
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .wrapContentHeight(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = copywriter.getText("absolutely_no_personal_information_is_collected_or_stored"),
                    textAlign = TextAlign.Center,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(tiny3X))
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = small2X),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .wrapContentHeight()
                            .clip(tiny3XRoundedCornerShape)
                            .background(MaterialTheme.colorScheme.surface),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(modifier = Modifier.height(tiny3X))
                    Text(
                        text = copywriter.getText("accessibility"),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(tiny))
                    Text(
                        text = copywriter.getText("crosspaste_needs_your_permission_to_support_global_shortcuts"),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(small2X))
                    if (!toRestart) {
                        Button(
                            modifier = Modifier.height(xxxLarge),
                            onClick = {
                                setOnTop(false)
                                uiSupport.jumpPrivacyAccessibility()
                            },
                            shape = tiny3XRoundedCornerShape,
                            border = BorderStroke(tiny5X, MaterialTheme.colorScheme.tertiaryContainer),
                            contentPadding = PaddingValues(horizontal = tiny, vertical = zero),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            elevation =
                                ButtonDefaults.elevatedButtonElevation(
                                    defaultElevation = zero,
                                    pressedElevation = zero,
                                    hoveredElevation = zero,
                                    focusedElevation = zero,
                                ),
                        ) {
                            Text(
                                text = copywriter.getText("grant_permissions"),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    } else {
                        if (restarting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(xxLarge),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            )
                        } else {
                            Button(
                                modifier = Modifier.height(xxxLarge),
                                onClick = {
                                    restarting = true
                                    appLock.resetFirstLaunchFlag()
                                    appRestartService.restart {
                                        exitApplication(ExitMode.RESTART)
                                    }
                                },
                                shape = tiny3XRoundedCornerShape,
                                border = BorderStroke(tiny5X, MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = tiny, vertical = zero),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                elevation =
                                    ButtonDefaults.elevatedButtonElevation(
                                        defaultElevation = zero,
                                        pressedElevation = zero,
                                        hoveredElevation = zero,
                                        focusedElevation = zero,
                                    ),
                            ) {
                                Text(
                                    text = copywriter.getText("restart_application"),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(tiny))
                }
            }
        }
    }
}
