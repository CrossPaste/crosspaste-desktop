package com.crosspaste.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationCenter
import com.crosspaste.ui.base.AlertCard
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.utils.DateUtils
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun NotificationCenterContentView() {
    val center = koinInject<NotificationCenter>()
    val copywriter = koinInject<GlobalCopywriter>()
    val notifications by center.notifications.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var errorKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(center) {
        if (center.load().isFailure) errorKey = "notification_center_load_failed"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        SectionHeader(
            text = NotificationCenterRoute.NAME,
            topPadding = medium,
            trailingContent = {
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = !notifications.isNullOrEmpty() && !clearing,
                ) {
                    Text(copywriter.getText("notification_center_clear_all"))
                }
            },
        )
        errorKey?.let {
            AlertCard(title = copywriter.getText(it), messageType = MessageType.Error)
        }
        val entries = notifications
        when {
            entries == null && errorKey == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            entries != null && entries.isEmpty() -> {
                Text(
                    text = copywriter.getText("notification_center_empty"),
                    modifier = Modifier.padding(tiny),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entries != null -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(tiny)) {
                    items(entries, key = { it.id }) { notice ->
                        val date = DateUtils.epochMillisecondsToLocalDateTime(notice.createdAt)
                        Text(
                            text = copywriter.getDate(date),
                            modifier = Modifier.padding(horizontal = tiny),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AlertCard(
                            title = copywriter.getText(notice.titleKey),
                            message = notice.messageKey?.let { copywriter.getText(it) },
                            messageType = notice.messageType,
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(copywriter.getText("notification_center_clear_all")) },
            text = { Text(copywriter.getText("notification_center_clear_confirm")) },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(copywriter.getText("cancel")) }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        clearing = true
                        scope.launch {
                            errorKey = if (center.clearAll().isFailure) "notification_center_clear_failed" else null
                            clearing = false
                        }
                    },
                ) { Text(copywriter.getText("clear_now")) }
            },
        )
    }
}
