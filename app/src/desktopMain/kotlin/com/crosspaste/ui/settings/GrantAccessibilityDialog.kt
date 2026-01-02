package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.crosspaste.app.AppRestartService
import com.crosspaste.app.ExitMode
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.LocalExitApplication
import com.crosspaste.ui.base.DialogActionButton
import com.crosspaste.ui.base.DialogButtonType
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun GrantAccessibilityDialog() {
    val appRestartService = koinInject<AppRestartService>()
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()

    val appSizeValue = LocalAppSizeValueState.current
    val exitApplication = LocalExitApplication.current

    var restart by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            if (MacosApi.INSTANCE.checkAccessibilityPermissions()) {
                restart = true
                break
            } else {
                delay(2000)
            }
        }
    }

    AlertDialog(
        modifier = Modifier.width(appSizeValue.dialogWidth),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = {},
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = tiny),
            ) {
                Icon(
                    imageVector = Icons.Default.Approval,
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(medium))
                Text(
                    text = copywriter.getText("accessibility_permission_title"),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = tiny),
                verticalArrangement = Arrangement.spacedBy(xLarge),
            ) {
                Text(
                    text = copywriter.getText("accessibility_permission_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                )
            }
        },
        confirmButton = {
            if (restart) {
                DialogActionButton(
                    text = copywriter.getText("restart"),
                    type = DialogButtonType.FILLED,
                ) {
                    configManager.updateConfig("showGrantAccessibility", false)
                    appRestartService.restart {
                        exitApplication(ExitMode.RESTART)
                    }
                }
            } else {
                DialogActionButton(
                    text = copywriter.getText("accessibility_permission_btn"),
                    type = DialogButtonType.FILLED,
                ) {
                    uiSupport.jumpPrivacyAccessibility()
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    configManager.updateConfig("showGrantAccessibility", false)
                },
            ) {
                Text(copywriter.getText("cancel"))
            }
        },
    )
}
