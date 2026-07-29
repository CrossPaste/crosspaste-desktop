package com.crosspaste.ui.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.pairing.v3.PairingAcceptanceWindow
import com.crosspaste.pairing.v3.PairingSessionState
import com.crosspaste.pairing.v3.PairingSessionUiState
import com.crosspaste.pairing.v3.PairingV3
import com.crosspaste.pairing.v3.PakeRole
import com.crosspaste.utils.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PairingV3AcceptanceWindowEffect(controller: PairingV3UiController = koinInject()) {
    // Keep the acceptance window open for the whole time this screen is visible.
    // open() only arms it for PairingAcceptanceWindow.DEFAULT_WINDOW_DURATION, so it
    // is renewed well before expiry; otherwise incoming intents would be silently
    // refused after the window lapsed while the screen was still on screen.
    LaunchedEffect(controller) {
        while (true) {
            controller.openAcceptanceWindow()
            delay(PairingAcceptanceWindow.DEFAULT_WINDOW_DURATION / 2)
        }
    }
    DisposableEffect(controller) {
        onDispose {
            controller.closeAcceptanceWindow()
        }
    }
}

@Composable
fun PairingV3AcceptorTokenCards(
    sessions: List<PairingSessionUiState>,
    controller: PairingV3UiController = koinInject(),
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val coroutineScope = rememberCoroutineScope()

    // v3 pairs per device: once a session reaches TRUSTED that device is connected,
    // so its card closes itself; cards of other devices still pairing are untouched.
    val trustedSessionIds = trustedPairingSessionIds(sessions)
    LaunchedEffect(trustedSessionIds) {
        trustedSessionIds.forEach { sessionId ->
            controller.dismiss(sessionId)
        }
    }

    pairingTokenCardSessions(sessions).forEach { session ->
        key(session.sessionId) {
            PairingV3SessionTokenCard(
                session = session,
                copywriter = copywriter,
                onClose = {
                    coroutineScope.launch {
                        if (session.state.isTerminal) {
                            controller.dismiss(session.sessionId)
                        } else {
                            controller.reject(session.sessionId)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun PairingV3SessionTokenCard(
    session: PairingSessionUiState,
    copywriter: GlobalCopywriter,
    onClose: () -> Unit,
) {
    val secondsRemaining by rememberPairingSecondsRemaining(
        pinExpiresAt = session.pinExpiresAt,
        generation = session.tokenGeneration,
    )
    val progress =
        (secondsRemaining.toFloat() / PairingV3.DEFAULT_PIN_LIFETIME.inWholeSeconds)
            .coerceIn(0f, 1f)
    val hasVisiblePin =
        session.state == PairingSessionState.PIN_AVAILABLE &&
            session.pin != null
    val fingerprint =
        copywriter.getText(
            "pairing_v3_key_fingerprint",
            session.peerKeyFingerprintDisplay,
        )
    val state =
        if (hasVisiblePin) {
            copywriter.getText("pairing_v3_expires_in", secondsRemaining)
        } else {
            copywriter.getText(session.state.copyKey())
        }

    TokenPopupCard(
        title = copywriter.getText("token"),
        token = session.pin?.map(Char::toString).takeIf { hasVisiblePin },
        progress = progress,
        onClose = onClose,
        deviceDisplayName = session.peerDisplayName,
        statusText = fingerprint,
        stateText = state,
        stateColor = session.state.statusColor(),
        closeContentDescription =
            copywriter.getText(
                if (session.state.isTerminal) {
                    "pairing_v3_dismiss"
                } else {
                    "pairing_v3_reject"
                },
            ),
    )
}

internal fun acceptorPairingSessions(sessions: List<PairingSessionUiState>): List<PairingSessionUiState> =
    sessions.filter { it.role == PakeRole.ACCEPTOR }

/** Sessions that still need a visible token card; TRUSTED ones auto-dismiss instead. */
internal fun pairingTokenCardSessions(sessions: List<PairingSessionUiState>): List<PairingSessionUiState> =
    sessions.filterNot { it.state == PairingSessionState.TRUSTED }

internal fun trustedPairingSessionIds(sessions: List<PairingSessionUiState>): List<String> =
    sessions.filter { it.state == PairingSessionState.TRUSTED }.map { it.sessionId }

@Composable
internal fun rememberPairingSecondsRemaining(
    pinExpiresAt: Long,
    generation: Long,
): State<Long> {
    val remaining =
        remember(pinExpiresAt, generation) {
            mutableLongStateOf(secondsUntil(pinExpiresAt, DateUtils.nowEpochMilliseconds()))
        }
    LaunchedEffect(pinExpiresAt, generation) {
        while (true) {
            remaining.longValue = secondsUntil(pinExpiresAt, DateUtils.nowEpochMilliseconds())
            if (remaining.longValue == 0L) break
            delay(250L.milliseconds)
        }
    }
    return remaining
}

internal fun secondsUntil(
    expiresAt: Long,
    now: Long,
): Long = max(0L, ceil((expiresAt - now) / 1000.0).toLong())

@Composable
private fun PairingSessionState.statusColor() =
    when (this) {
        PairingSessionState.TRUSTED -> MaterialTheme.colorScheme.primary
        PairingSessionState.REJECTED,
        PairingSessionState.CANCELLED,
        PairingSessionState.EXPIRED,
        PairingSessionState.FAILED,
        -> MaterialTheme.colorScheme.error

        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
