package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.net.VersionRelation
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.theme.AppUISize.enormous
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xLargeRoundedCornerShape
import org.koin.compose.koinInject

@Composable
fun DeviceScope.IncompatibleSection() {
    val copywriter = koinInject<GlobalCopywriter>()
    val syncManager = koinInject<SyncManager>()
    val versionRelation by syncManager
        .getSyncHandler(syncRuntimeInfo.appInstanceId)
        ?.versionRelation
        ?.collectAsState() ?: remember { mutableStateOf(null) }

    if (versionRelation != null && versionRelation != VersionRelation.EQUAL_TO) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = xLargeRoundedCornerShape,
        ) {
            Column(
                modifier = Modifier.padding(xLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Use a warning icon to alert the user
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(enormous),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )

                Spacer(modifier = Modifier.height(medium))

                Text(
                    text = copywriter.getText("incompatible_with_device"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(tiny))

                val detailText =
                    when (versionRelation) {
                        VersionRelation.LOWER_THAN -> copywriter.getText("local_version_low_desc")
                        VersionRelation.HIGHER_THAN -> copywriter.getText("remote_version_low_desc")
                        else -> null
                    }

                detailText?.let {
                    Text(
                        text = detailText,
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                lineBreak = LineBreak.Heading,
                            ),
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
