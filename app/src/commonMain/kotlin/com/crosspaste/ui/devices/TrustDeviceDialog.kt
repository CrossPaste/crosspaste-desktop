package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Key
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.PairingCredentialRefreshResult
import com.crosspaste.sync.PairingCredentialType
import com.crosspaste.sync.QrBearerToken
import com.crosspaste.sync.SasCode
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.base.DialogActionButton
import com.crosspaste.ui.base.DialogButtonType
import com.crosspaste.ui.theme.AppUIFont.generalBodyTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun DeviceScope.TrustDeviceDialog() {
    val copywriter = koinInject<GlobalCopywriter>()
    val syncManager = koinInject<SyncManager>()

    val appSizeValue = LocalAppSizeValueState.current
    val appInstanceId = syncRuntimeInfo.appInstanceId
    val pairingCredentialTypes by syncManager.pairingCredentialTypes.collectAsState()
    val pairingCredentialType = pairingCredentialTypes[appInstanceId]
    val isPairingTypeKnown = pairingCredentialType != null

    if (pairingCredentialType == PairingCredentialType.V3_PIN) {
        PairingV3TrustDeviceDialog()
        return
    }

    val tokenCount = 6
    val tokens = remember(appInstanceId, pairingCredentialType) { mutableStateListOf(*Array(tokenCount) { "" }) }
    var isError by remember(appInstanceId, pairingCredentialType) { mutableStateOf(false) }

    var isLoading by remember(appInstanceId, pairingCredentialType) { mutableStateOf(false) }

    val focusRequesters = remember(appInstanceId, pairingCredentialType) { List(tokenCount) { FocusRequester() } }

    val setError = { value: Boolean ->
        isError = value
        if (value) isLoading = false
    }

    val confirmAction = confirm@{
        val credentialType = pairingCredentialType ?: return@confirm
        if (!isLoading) {
            isLoading = true
            isError = false
            confirmToken(
                tokens = tokens,
                tokenCount = tokenCount,
                setError = setError,
                syncManager = syncManager,
                syncRuntimeInfo = syncRuntimeInfo,
                pairingCredentialType = credentialType,
            )
        }
    }

    val cancelAction = {
        if (!isLoading) {
            cancelVerification(syncManager, syncRuntimeInfo)
        }
    }

    LaunchedEffect(appInstanceId) {
        refreshPairingCredentialTypeUntilKnown(syncManager, appInstanceId)
    }

    LaunchedEffect(appInstanceId, pairingCredentialType) {
        val handler = syncManager.getSyncHandlers()[appInstanceId]
        when (pairingCredentialType) {
            PairingCredentialType.SAS_CODE -> syncManager.exchangeKeysForPairing(appInstanceId)
            PairingCredentialType.QR_BEARER_TOKEN -> handler?.showToken()
            PairingCredentialType.V3_PIN -> Unit
            null -> Unit
        }
    }

    // The SAS warm-up exchange above parks one token-refresh count on the
    // responder; if the dialog goes away without a confirm, release it so the
    // responder's SAS overlay auto-closes (#4684). Keyed like the warm-up
    // effect so each dismissal path (cancel button, outside click, device
    // switch, window close) triggers a release. The cancel is generation-safe:
    // it names the exact exchange this dialog owns (skipped after a successful
    // confirm consumes it, or when a reopened dialog's newer exchange
    // supersedes it). The generation is recorded before asynchronous dispatch;
    // if dismissal wins the local race, the queued warm-up observes the
    // consumed generation and sends no request. On the wire the release stays
    // best effort: warm-up and cancel travel as independent requests, so a
    // cancel arriving before its exchange is a responder-side no-op and the
    // leftover entry self-heals on the next exchange.
    DisposableEffect(appInstanceId, pairingCredentialType) {
        onDispose {
            if (pairingCredentialType == PairingCredentialType.SAS_CODE) {
                syncManager.cancelPairing(appInstanceId)
            }
        }
    }

    AlertDialog(
        modifier = Modifier.width(appSizeValue.dialogWidth),
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !isLoading,
                dismissOnClickOutside = !isLoading,
            ),
        onDismissRequest = cancelAction,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = tiny),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Key,
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(medium))
                Text(
                    text = copywriter.getText("do_you_trust_this_device?"),
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
                    text = copywriter.getText("trust_this_device_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f,
                )
                DeviceRowContent(
                    style = tokenDeviceStyle,
                    trailingContent = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(tiny),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = syncRuntimeInfo.connectHostAddress ?: "unknown",
                                style = generalBodyTextStyle,
                            )
                        }
                    },
                )
                TokenInputRow(
                    tokens,
                    isError,
                    isLoading || !isPairingTypeKnown,
                    focusRequesters,
                    confirmAction,
                    cancelAction,
                )
            }
        },
        confirmButton = {
            DialogActionButton(
                text = copywriter.getText("confirm"),
                type = DialogButtonType.FILLED,
                enabled = isPairingTypeKnown,
                isLoading = isLoading,
            ) {
                confirmAction()
            }
        },
        dismissButton = {
            TextButton(
                onClick = cancelAction,
                enabled = !isLoading,
            ) {
                Text(copywriter.getText("cancel"))
            }
        },
    )
}

internal suspend fun refreshPairingCredentialTypeUntilKnown(
    syncManager: SyncManager,
    appInstanceId: String,
    initialBackoff: Duration = 1.seconds,
    maxBackoff: Duration = 30.seconds,
    delayAction: suspend (Duration) -> Unit = { delay(it) },
): PairingCredentialRefreshResult {
    var backoff = initialBackoff
    while (true) {
        val result = syncManager.refreshPairingCredentialType(appInstanceId)
        when (result) {
            is PairingCredentialRefreshResult.Resolved,
            PairingCredentialRefreshResult.IdentityMismatch,
            PairingCredentialRefreshResult.DeviceUnavailable,
            -> return result

            PairingCredentialRefreshResult.RetryableFailure -> {
                delayAction(backoff)
                backoff = (backoff * 2).coerceAtMost(maxBackoff)
            }
        }
    }
}

fun confirmToken(
    tokens: MutableList<String>,
    tokenCount: Int,
    setError: (Boolean) -> Unit,
    syncManager: SyncManager,
    syncRuntimeInfo: SyncRuntimeInfo,
    pairingCredentialType: PairingCredentialType,
) {
    tokens.joinToString("").let { token ->
        if (token.length == tokenCount) {
            val appInstanceId = syncRuntimeInfo.appInstanceId
            val callback = { success: Boolean -> setError(!success) }
            when (pairingCredentialType) {
                PairingCredentialType.SAS_CODE ->
                    syncManager.trustBySasCode(appInstanceId, SasCode(token.toInt()), callback)
                PairingCredentialType.QR_BEARER_TOKEN ->
                    syncManager.trustByBearerToken(appInstanceId, QrBearerToken(token.toInt()), callback)
                PairingCredentialType.V3_PIN -> setError(true)
            }
        } else {
            setError(true)
        }
    }
}

fun cancelVerification(
    syncManager: SyncManager,
    syncRuntimeInfo: SyncRuntimeInfo,
) {
    syncManager.ignoreVerify(syncRuntimeInfo.appInstanceId)
}
