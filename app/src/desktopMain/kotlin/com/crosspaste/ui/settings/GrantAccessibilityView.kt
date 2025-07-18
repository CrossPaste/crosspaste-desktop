package com.crosspaste.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.crosspaste.app.AppRestartService
import com.crosspaste.app.ExitMode
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.ui.LocalExitApplication
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.accessibility
import com.crosspaste.ui.base.restart
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun GrantAccessibilityView() {
    val appRestartService = koinInject<AppRestartService>()
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()
    var restart by remember { mutableStateOf(false) }

    val exitApplication = LocalExitApplication.current

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

    Box(
        modifier =
            Modifier
                .fillMaxWidth(),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (restart) {
                            appRestartService.restart {
                                exitApplication(ExitMode.RESTART)
                            }
                        } else {
                            uiSupport.jumpPrivacyAccessibility()
                        }
                    },
            shape = small2XRoundedCornerShape,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(horizontal = large, vertical = small2X)
                        .clip(small2XRoundedCornerShape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (restart) {
                    Icon(
                        modifier = Modifier.size(large2X),
                        painter = restart(),
                        contentDescription = "to Restart",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.size(small2X))

                    Text(
                        text = copywriter.getText("restart_now_to_apply_authorization"),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    )
                } else {
                    Icon(
                        modifier = Modifier.size(large2X),
                        painter = accessibility(),
                        contentDescription = "Grant Accessibility",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.size(small2X))

                    Text(
                        text = copywriter.getText("global_shortcut_key_not_working_authorize"),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    )
                }
            }
        }
    }
}
