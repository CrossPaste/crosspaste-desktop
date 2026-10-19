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
        // First arm is a genuine local gesture (screen entered): it resets the
        // proof-failure budget and clears any lock. Subsequent arms only renew the
        // window; renewing must NOT reset the budget, or keeping this screen open
        // would let an attacker's guess budget refill every half-window.
        controller.openAcceptanceWindow()
        while (true) {
            delay(PairingAcceptanceWindow.DEFAULT_WINDOW_DURATION / 2)
            controller.renewAcceptanceWindow()
        }
    }
    DisposableEffect(controller) {
        onDispose {
            controller.closeAcceptanceWindow()
        }
    }
}

/**
 * Closes the cards of sessions that reached a self-dismissing state. Kept apart
 * from [PairingV3AcceptorTokenCards] so it keeps running while the popup is not
 * mounted (no visible card and no legacy token).
 */
@Composable
internal fun PairingV3SelfDismissEffect(
    sessions: List<PairingSessionUiState>,
    controller: PairingV3UiController = koinInject(),
) {
    // v3 pairs per device. A session that reaches a self-dismissing state closes
    // its own card: TRUSTED (the device connected) and CANCELLED (the peer
    // abandoned the handshake) are both non-actionable, so lingering on screen is
    // just noise. REJECTED is hidden without being dismissed (see
    // [pairingTokenCardSessions]); EXPIRED/FAILED stay so the user sees why it
    // failed. Cards of other devices still pairing are untouched.
    val selfDismissingIds = selfDismissingSessionIds(sessions)
    LaunchedEffect(selfDismissingIds) {
        selfDismissingIds.forEach { sessionId ->
            controller.dismiss(sessionId)
        }
    }
}

/** Renders one card per session in [sessions], already filtered by [pairingTokenCardSessions]. */
@Composable
internal fun PairingV3AcceptorTokenCards(
    sessions: List<PairingSessionUiState>,
    controller: PairingV3UiController = koinInject(),
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val coroutineScope = rememberCoroutineScope()

    sessions.forEach { session ->
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
        title = copywriter.getText("pairing_code"),
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

/**
 * TRUSTED (connected) and CANCELLED (peer abandoned the handshake) close their own
 * card — both are non-actionable, so there is nothing for the user to read or act
 * on. EXPIRED/FAILED are surfaced so the user learns why pairing failed.
 */
private fun PairingSessionState.isSelfDismissing(): Boolean =
    this == PairingSessionState.TRUSTED || this == PairingSessionState.CANCELLED

/**
 * Sessions that still need a visible token card.
 *
 * Self-dismissing states auto-close instead. REJECTED is hidden too: on the acceptor
 * side it only ever results from the user's own close click, so echoing "pairing
 * rejected" back at them is noise. Unlike the self-dismissing states it is NOT
 * removed from the store — the initiator's next request must still be answered
 * with PAIRING_REJECTED rather than SESSION_NOT_FOUND; terminal retention prunes it.
 */
internal fun pairingTokenCardSessions(sessions: List<PairingSessionUiState>): List<PairingSessionUiState> =
    sessions.filterNot { it.state.isSelfDismissing() || it.state == PairingSessionState.REJECTED }

/**
 * The token popup is mounted only while it has a card to show. Mounting it for
 * sessions that render no card (e.g. REJECTED, retained for minutes after the
 * user's close click) leaves an empty, zero-width popup window behind, which on
 * Android keeps showing the last frame of the closed card, shifted off-center.
 */
internal fun isTokenPopupVisible(
    showToken: Boolean,
    visiblePairingSessions: List<PairingSessionUiState>,
): Boolean = showToken || visiblePairingSessions.isNotEmpty()

internal fun selfDismissingSessionIds(sessions: List<PairingSessionUiState>): List<String> =
    sessions.filter { it.state.isSelfDismissing() }.map { it.sessionId }

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
