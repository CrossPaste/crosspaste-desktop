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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Key
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.pairing.v3.PairingSessionState
import com.crosspaste.pairing.v3.PairingSessionUiState
import com.crosspaste.pairing.v3.PakeRole
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.V3Pin
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.base.DialogActionButton
import com.crosspaste.ui.base.DialogButtonType
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val PAIRING_PIN_LENGTH = 6

@Composable
fun DeviceScope.PairingV3TrustDeviceDialog() {
    val controller = koinInject<PairingV3UiController>()
    val copywriter = koinInject<GlobalCopywriter>()
    val syncManager = koinInject<SyncManager>()
    val coroutineScope = rememberCoroutineScope()
    val appInstanceId = syncRuntimeInfo.appInstanceId
    val sessions by controller.sessions.collectAsState()
    val session = sessions.latestInitiatorSession(appInstanceId)
    val dialogState =
        remember(appInstanceId, controller, syncManager, coroutineScope) {
            PairingV3DialogState(
                appInstanceId = appInstanceId,
                controller = controller,
                syncManager = syncManager,
                coroutineScope = coroutineScope,
            )
        }
    val tokens =
        remember(appInstanceId, session?.sessionId, session?.tokenGeneration) {
            mutableStateListOf(*Array(PAIRING_PIN_LENGTH) { "" })
        }
    val focusRequesters =
        remember(appInstanceId, session?.sessionId, session?.tokenGeneration) {
            List(PAIRING_PIN_LENGTH) { FocusRequester() }
        }

    PairingV3DialogLifecycle(
        appInstanceId = appInstanceId,
        session = session,
        controller = controller,
        dialogState = dialogState,
        resetInput = {
            tokens.indices.forEach { index -> tokens[index] = "" }
        },
    )

    PairingV3TrustDeviceDialogContent(
        copywriter = copywriter,
        model =
            PairingV3DialogModel(
                deviceDisplayName = syncRuntimeInfo.getDeviceDisplayName(),
                peerKeyFingerprint = session?.peerKeyFingerprintDisplay,
                sessionState = session?.state,
                isLoading = dialogState.isLoading,
                uiError = dialogState.uiError,
                recovery = dialogState.recovery,
                pinComplete = tokens.all { it.length == 1 },
            ),
        tokens = tokens,
        focusRequesters = focusRequesters,
        dialogWidth = LocalAppSizeValueState.current.dialogWidth,
        onSubmit = {
            session?.let {
                dialogState.submitPin(it.sessionId, tokens.joinToString(""))
            }
        },
        onRetry = {
            dialogState.retry(session?.sessionId)
        },
        onCancel = {
            dialogState.cancel(session)
        },
    )
}

@Composable
private fun PairingV3DialogLifecycle(
    appInstanceId: String,
    session: PairingSessionUiState?,
    controller: PairingV3UiController,
    dialogState: PairingV3DialogState,
    resetInput: () -> Unit,
) {
    LaunchedEffect(appInstanceId) {
        if (session == null || session.state.isTerminal) {
            dialogState.startPairing()
        }
    }

    LaunchedEffect(session?.sessionId, session?.tokenGeneration) {
        resetInput()
        dialogState.resetFeedback()
    }

    DisposableEffect(appInstanceId) {
        onDispose {
            controller.sessions.value
                .latestActiveInitiatorSession(appInstanceId)
                ?.let { controller.cancelDetached(it.sessionId) }
        }
    }
}

@Composable
private fun DeviceScope.PairingV3TrustDeviceDialogContent(
    copywriter: GlobalCopywriter,
    model: PairingV3DialogModel,
    tokens: MutableList<String>,
    focusRequesters: List<FocusRequester>,
    dialogWidth: Dp,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.width(dialogWidth),
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !model.isLoading,
                dismissOnClickOutside = !model.isLoading,
            ),
        onDismissRequest = onCancel,
        title = {
            PairingV3DialogTitle(copywriter)
        },
        text = {
            PairingV3DialogBody(
                copywriter = copywriter,
                model = model,
                tokens = tokens,
                focusRequesters = focusRequesters,
                onSubmit = onSubmit,
                onCancel = onCancel,
            )
        },
        confirmButton = {
            PairingV3ConfirmAction(
                copywriter = copywriter,
                model = model,
                onSubmit = onSubmit,
                onRetry = onRetry,
            )
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !model.isLoading,
            ) {
                Text(copywriter.getText(if (model.isTerminal) "pairing_v3_dismiss" else "cancel"))
            }
        },
    )
}

@Composable
private fun PairingV3DialogTitle(copywriter: GlobalCopywriter) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = tiny),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Key,
            contentDescription = null,
            modifier = Modifier.size(xLarge),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(medium))
        Text(
            text = copywriter.getText("pairing_v3_enter_pin_title"),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun DeviceScope.PairingV3DialogBody(
    copywriter: GlobalCopywriter,
    model: PairingV3DialogModel,
    tokens: MutableList<String>,
    focusRequesters: List<FocusRequester>,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = tiny),
        verticalArrangement = Arrangement.spacedBy(xLarge),
    ) {
        Text(
            text =
                copywriter.getText(
                    "pairing_v3_enter_pin_desc",
                    model.deviceDisplayName,
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PairingV3PeerIdentity(model.peerKeyFingerprint)
        PairingV3PinInput(
            model = model,
            tokens = tokens,
            focusRequesters = focusRequesters,
            onSubmit = onSubmit,
            onCancel = onCancel,
        )
        PairingV3Feedback(copywriter, model)
    }
}

@Composable
private fun DeviceScope.PairingV3PeerIdentity(peerKeyFingerprint: String?) {
    DeviceRowContent(
        style = tokenDeviceStyle,
        trailingContent = {
            peerKeyFingerprint?.let {
                Text(
                    text = it,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun PairingV3PinInput(
    model: PairingV3DialogModel,
    tokens: MutableList<String>,
    focusRequesters: List<FocusRequester>,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    if (model.isTerminal) return

    TokenInputRow(
        tokens = tokens,
        isError = model.uiError != null,
        isLoading = model.inputLocked,
        focusRequesters = focusRequesters,
        confirmAction = {
            if (model.canSubmit) onSubmit()
        },
        cancelAction = onCancel,
    )
}

@Composable
private fun PairingV3Feedback(
    copywriter: GlobalCopywriter,
    model: PairingV3DialogModel,
) {
    model.uiError?.let { error ->
        Text(
            text = copywriter.getText(error.copyKey()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    model.sessionState
        ?.takeIf { it.isTerminal }
        ?.let { state ->
            Text(
                text = copywriter.getText(state.copyKey()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
}

@Composable
private fun PairingV3ConfirmAction(
    copywriter: GlobalCopywriter,
    model: PairingV3DialogModel,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
) {
    if (model.canRetry) {
        DialogActionButton(
            text = copywriter.getText("retry"),
            type = DialogButtonType.FILLED,
            enabled = !model.isLoading,
            isLoading = model.isLoading,
            onClick = onRetry,
        )
    } else if (model.showConfirm) {
        DialogActionButton(
            text = copywriter.getText("confirm"),
            type = DialogButtonType.FILLED,
            enabled = model.canSubmit,
            isLoading = model.isLoading,
            onClick = onSubmit,
        )
    }
}

internal data class PairingV3DialogModel(
    val deviceDisplayName: String,
    val peerKeyFingerprint: String?,
    val sessionState: PairingSessionState?,
    val isLoading: Boolean,
    val uiError: PairingV3UiError?,
    val recovery: PairingV3Recovery,
    val pinComplete: Boolean,
) {
    val hasSession: Boolean
        get() = sessionState != null

    val isTerminal: Boolean
        get() = sessionState?.isTerminal == true

    val canRetry: Boolean
        get() =
            recovery != PairingV3Recovery.NONE &&
                (recovery == PairingV3Recovery.RETRY_START || hasSession)

    val showConfirm: Boolean
        get() = !isTerminal && uiError == null

    val canSubmit: Boolean
        get() =
            hasSession &&
                !isTerminal &&
                uiError == null &&
                recovery == PairingV3Recovery.NONE &&
                pinComplete

    val inputLocked: Boolean
        get() =
            isLoading ||
                !hasSession ||
                uiError != null ||
                recovery != PairingV3Recovery.NONE
}

private class PairingV3DialogState(
    private val appInstanceId: String,
    private val controller: PairingV3UiController,
    private val syncManager: SyncManager,
    private val coroutineScope: CoroutineScope,
) {
    var isLoading by mutableStateOf(false)
        private set

    var uiError by mutableStateOf<PairingV3UiError?>(null)
        private set

    var recovery by mutableStateOf(PairingV3Recovery.NONE)
        private set

    fun resetFeedback() {
        uiError = null
        recovery = PairingV3Recovery.NONE
    }

    fun startPairing() {
        launchAction {
            controller.startPairing(appInstanceId)
        }
    }

    fun submitPin(
        sessionId: String,
        pin: String,
    ) {
        launchAction {
            controller.submitPin(sessionId, V3Pin(pin))
        }
    }

    fun retry(sessionId: String?) {
        when (recovery) {
            PairingV3Recovery.RETRY_START -> startPairing()
            PairingV3Recovery.REFRESH_OFFER,
            PairingV3Recovery.RETRY_COMMIT,
            -> sessionId?.let { launchAction { controller.recover(it) } }

            PairingV3Recovery.NONE -> Unit
        }
    }

    fun cancel(session: PairingSessionUiState?) {
        if (isLoading) return
        coroutineScope.launch {
            session?.let {
                if (it.state.isTerminal) {
                    controller.dismiss(it.sessionId)
                } else {
                    controller.cancel(it.sessionId)
                }
            }
            syncManager.ignoreVerify(appInstanceId)
        }
    }

    private fun launchAction(action: suspend () -> PairingV3UiResult) {
        if (isLoading) return
        isLoading = true
        uiError = null
        coroutineScope.launch {
            runAction(action)
        }
    }

    private suspend fun runAction(action: suspend () -> PairingV3UiResult) {
        try {
            applyResult(action())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            applyResult(PairingV3UiResult.Error(PairingV3UiError.FAILED))
        }
    }

    private fun applyResult(result: PairingV3UiResult) {
        isLoading = false
        when (result) {
            is PairingV3UiResult.SessionReady -> resetFeedback()
            PairingV3UiResult.Paired -> completePairing()
            is PairingV3UiResult.Error -> {
                uiError = result.reason
                recovery = result.recovery
            }
        }
    }

    private fun completePairing() {
        resetFeedback()
        syncManager.ignoreVerify(appInstanceId)
        syncManager.refresh(listOf(appInstanceId))
    }
}

private fun List<PairingSessionUiState>.latestInitiatorSession(appInstanceId: String): PairingSessionUiState? =
    filter { it.role == PakeRole.INITIATOR && it.peerAppInstanceId == appInstanceId }
        .maxByOrNull { it.createdAt }

private fun List<PairingSessionUiState>.latestActiveInitiatorSession(appInstanceId: String): PairingSessionUiState? =
    filter {
        it.role == PakeRole.INITIATOR &&
            it.peerAppInstanceId == appInstanceId &&
            !it.state.isTerminal
    }.maxByOrNull { it.createdAt }

internal fun PairingV3UiError.copyKey(): String =
    when (this) {
        PairingV3UiError.PIN_EXPIRED -> "pairing_v3_pin_expired"
        PairingV3UiError.INCORRECT_PIN -> "pairing_v3_incorrect_pin"
        PairingV3UiError.NETWORK_FAILURE -> "pairing_v3_network_failure"
        PairingV3UiError.NOT_ACCEPTING -> "pairing_v3_not_accepting"
        PairingV3UiError.REJECTED -> "pairing_v3_rejected"
        PairingV3UiError.CANCELLED -> "pairing_v3_cancelled"
        PairingV3UiError.SESSION_EXPIRED -> "pairing_v3_session_expired"
        PairingV3UiError.RATE_LIMITED -> "pairing_v3_rate_limited"
        PairingV3UiError.CAPACITY_EXCEEDED -> "pairing_v3_capacity_exceeded"
        PairingV3UiError.UNSUPPORTED -> "pairing_v3_unsupported"
        PairingV3UiError.IDENTITY_INVALID -> "pairing_v3_identity_invalid"
        PairingV3UiError.FAILED -> "pairing_v3_failed"
    }

internal fun PairingSessionState.copyKey(): String =
    when (this) {
        PairingSessionState.INTENT_RECEIVED,
        PairingSessionState.PIN_AVAILABLE,
        -> "pairing_v3_waiting"

        PairingSessionState.PAKE_NEGOTIATING -> "pairing_v3_negotiating"
        PairingSessionState.PEER_CONFIRMED,
        PairingSessionState.COMMITTING,
        -> "pairing_v3_confirming"

        PairingSessionState.TRUSTED -> "pairing_v3_trusted"
        PairingSessionState.REJECTED -> "pairing_v3_rejected"
        PairingSessionState.CANCELLED -> "pairing_v3_cancelled"
        PairingSessionState.EXPIRED -> "pairing_v3_session_expired"
        PairingSessionState.FAILED -> "pairing_v3_failed"
    }
