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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationCenter
import com.crosspaste.ui.base.AlertCard
import com.crosspaste.ui.base.MessageContentCard
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.base.getMessageColor
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.utils.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
fun NotificationCenterContentView() {
    val center = koinInject<NotificationCenter>()
    val copywriter = koinInject<GlobalCopywriter>()
    val notifications by center.notifications.collectAsState()
    val entries = notifications
    val hasEntries = !entries.isNullOrEmpty()
    val newestTimestamp = entries?.firstOrNull()?.createdAt
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var clearFailed by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(DateUtils.nowEpochMilliseconds()) }

    LaunchedEffect(center, loadAttempt) {
        loadFailed = center.load().isFailure
    }

    LaunchedEffect(newestTimestamp) {
        if (newestTimestamp != null) {
            while (true) {
                val currentTime = DateUtils.nowEpochMilliseconds()
                now = currentTime
                val refreshInterval =
                    if (currentTime - newestTimestamp < 1.minutes.inWholeMilliseconds) 1.seconds else 30.seconds
                delay(refreshInterval)
            }
        }
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
                    enabled = hasEntries && !clearing,
                ) {
                    Text(copywriter.getText("notification_center_clear_all"))
                }
            },
        )
        if (entries == null && loadFailed) {
            AlertCard(
                title = copywriter.getText("notification_center_load_failed"),
                messageType = MessageType.Error,
            )
            TextButton(
                onClick = {
                    loadFailed = false
                    loadAttempt++
                },
            ) { Text(copywriter.getText("retry")) }
        }
        if (clearFailed) {
            AlertCard(
                title = copywriter.getText("notification_center_clear_failed"),
                messageType = MessageType.Error,
            )
        }
        when {
            entries == null && !loadFailed -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                        val relative = DateUtils.getRelativeTime(notice.createdAt, now)
                        val timestamp =
                            relative.value?.let { copywriter.getText(relative.unit, it) }
                                ?: copywriter.getText(relative.unit)
                        Text(
                            text = timestamp,
                            modifier = Modifier.padding(start = tiny, end = tiny, bottom = tiny3X),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val (_, iconColor) = notice.messageType.getMessageColor()
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            MessageContentCard(
                                title = copywriter.getText(notice.titleKey),
                                message = notice.localizedMessage(copywriter),
                                messageType = notice.messageType,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                iconColor = iconColor,
                            )
                        }
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
                            clearFailed = center.clearAll().isFailure
                            clearing = false
                        }
                    },
                ) { Text(copywriter.getText("notification_center_clear_all")) }
            },
        )
    }
}
