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
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Key
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.pairing.v3.PairingSessionState
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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DeviceScope.PairingV3TrustDeviceDialog() {
    val controller = koinInject<PairingV3UiController>()
    val copywriter = koinInject<GlobalCopywriter>()
    val syncManager = koinInject<SyncManager>()
    val coroutineScope = rememberCoroutineScope()
    val appSizeValue = LocalAppSizeValueState.current
    val appInstanceId = syncRuntimeInfo.appInstanceId
    val sessions by controller.sessions.collectAsState()
    val session =
        sessions
            .filter { it.role == PakeRole.INITIATOR && it.peerAppInstanceId == appInstanceId }
            .maxByOrNull { it.createdAt }

    val tokenCount = 6
    val tokens =
        remember(appInstanceId, session?.sessionId, session?.tokenGeneration) {
            mutableStateListOf(*Array(tokenCount) { "" })
        }
    val focusRequesters =
        remember(appInstanceId, session?.sessionId, session?.tokenGeneration) {
            List(tokenCount) { FocusRequester() }
        }

    var isLoading by remember(appInstanceId) { mutableStateOf(false) }
    var uiError by remember(appInstanceId) { mutableStateOf<PairingV3UiError?>(null) }
    var recovery by remember(appInstanceId) { mutableStateOf(PairingV3Recovery.NONE) }

    fun applyResult(result: PairingV3UiResult) {
        isLoading = false
        when (result) {
            is PairingV3UiResult.SessionReady -> {
                uiError = null
                recovery = PairingV3Recovery.NONE
            }

            PairingV3UiResult.Paired -> {
                uiError = null
                recovery = PairingV3Recovery.NONE
                syncManager.ignoreVerify(appInstanceId)
                syncManager.refresh(listOf(appInstanceId))
            }

            is PairingV3UiResult.Error -> {
                uiError = result.reason
                recovery = result.recovery
            }
        }
    }

    suspend fun runAction(action: suspend () -> PairingV3UiResult) {
        try {
            applyResult(action())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            applyResult(PairingV3UiResult.Error(PairingV3UiError.FAILED))
        }
    }

    fun launchAction(action: suspend () -> PairingV3UiResult) {
        if (isLoading) return
        isLoading = true
        uiError = null
        coroutineScope.launch {
            runAction(action)
        }
    }

    LaunchedEffect(appInstanceId) {
        // Start a fresh pairing when there is no live initiator session for this peer.
        // A lingering terminal session (a previous cancel/reject/expiry) must not
        // suppress a new attempt; an already-active one must not be duplicated.
        if (session == null || session.state.isTerminal) {
            isLoading = true
            runAction {
                controller.startPairing(appInstanceId)
            }
        }
    }

    LaunchedEffect(session?.sessionId, session?.tokenGeneration) {
        tokens.indices.forEach { index -> tokens[index] = "" }
        uiError = null
        recovery = PairingV3Recovery.NONE
    }

    // Leaving the dialog by navigating away (rather than Cancel/Trust) must still
    // release an in-flight initiator session, so the peer is not blocked from v2
    // fallback for the whole session TTL. A trusted (terminal) session is left alone.
    DisposableEffect(appInstanceId) {
        onDispose {
            controller.sessions.value
                .firstOrNull { it.role == PakeRole.INITIATOR && it.peerAppInstanceId == appInstanceId }
                ?.takeIf { !it.state.isTerminal }
                ?.let { active -> controller.cancelDetached(active.sessionId) }
        }
    }

    val sessionTerminal = session?.state?.isTerminal == true
    val canSubmit =
        session != null &&
            !sessionTerminal &&
            uiError == null &&
            recovery == PairingV3Recovery.NONE &&
            tokens.all { it.length == 1 }

    fun cancelAction() {
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

    AlertDialog(
        modifier = Modifier.width(appSizeValue.dialogWidth),
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !isLoading,
                dismissOnClickOutside = !isLoading,
            ),
        onDismissRequest = ::cancelAction,
        title = {
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
                    text =
                        copywriter.getText(
                            "pairing_v3_enter_pin_desc",
                            syncRuntimeInfo.getDeviceDisplayName(),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                DeviceRowContent(
                    style = tokenDeviceStyle,
                    trailingContent = {
                        session?.let {
                            Text(
                                text = it.peerKeyFingerprintDisplay,
                                style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )

                if (!sessionTerminal) {
                    TokenInputRow(
                        tokens = tokens,
                        isError = uiError != null,
                        isLoading =
                            isLoading ||
                                session == null ||
                                uiError != null ||
                                recovery != PairingV3Recovery.NONE,
                        focusRequesters = focusRequesters,
                        confirmAction = {
                            if (canSubmit) {
                                val sessionId = session.sessionId
                                launchAction {
                                    controller.submitPin(sessionId, V3Pin(tokens.joinToString("")))
                                }
                            }
                        },
                        cancelAction = ::cancelAction,
                    )
                }

                uiError?.let { error ->
                    Text(
                        text = copywriter.getText(error.copyKey()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                session
                    ?.state
                    ?.takeIf { it.isTerminal }
                    ?.let { state ->
                        Text(
                            text = copywriter.getText(state.copyKey()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
            }
        },
        confirmButton = {
            when {
                recovery != PairingV3Recovery.NONE &&
                    (recovery == PairingV3Recovery.RETRY_START || session != null) ->
                    DialogActionButton(
                        text = copywriter.getText("retry"),
                        type = DialogButtonType.FILLED,
                        enabled = !isLoading,
                        isLoading = isLoading,
                    ) {
                        launchAction {
                            when (recovery) {
                                PairingV3Recovery.RETRY_START -> controller.startPairing(appInstanceId)
                                PairingV3Recovery.REFRESH_OFFER,
                                PairingV3Recovery.RETRY_COMMIT,
                                -> {
                                    val sessionId =
                                        session?.sessionId
                                            ?: return@launchAction PairingV3UiResult.Error(
                                                PairingV3UiError.FAILED,
                                            )
                                    controller.recover(sessionId)
                                }

                                PairingV3Recovery.NONE ->
                                    PairingV3UiResult.Error(PairingV3UiError.FAILED)
                            }
                        }
                    }

                // Only render Confirm when the session is actually submittable. An
                // unrecoverable error (recovery == NONE) leaves no retry branch, so
                // showing a permanently-disabled Confirm would look broken — the user
                // acts through Cancel instead.
                !sessionTerminal && uiError == null ->
                    DialogActionButton(
                        text = copywriter.getText("confirm"),
                        type = DialogButtonType.FILLED,
                        enabled = canSubmit,
                        isLoading = isLoading,
                    ) {
                        val sessionId = session?.sessionId ?: return@DialogActionButton
                        launchAction {
                            controller.submitPin(sessionId, V3Pin(tokens.joinToString("")))
                        }
                    }
            }
        },
        dismissButton = {
            TextButton(
                onClick = ::cancelAction,
                enabled = !isLoading,
            ) {
                Text(copywriter.getText(if (sessionTerminal) "pairing_v3_dismiss" else "cancel"))
            }
        },
    )
}

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
