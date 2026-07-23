package com.crosspaste.ui.devices

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Devices
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.pairing.v3.PairingAcceptanceWindow
import com.crosspaste.pairing.v3.PairingSessionState
import com.crosspaste.pairing.v3.PairingSessionUiState
import com.crosspaste.pairing.v3.PairingV3
import com.crosspaste.pairing.v3.PakeRole
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.DateUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PairingV3AcceptorSessionList(
    modifier: Modifier = Modifier,
    controller: PairingV3UiController = koinInject(),
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val sessions by controller.sessions.collectAsState()
    val incomingSessions = sessions.filter { it.role == PakeRole.ACCEPTOR }

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

    if (incomingSessions.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        Text(
            text = copywriter.getText("pairing_v3_requests"),
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
        )
        Text(
            text = copywriter.getText("pairing_v3_requests_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(tiny3X))
        incomingSessions.forEach { session ->
            PairingV3SessionCard(
                session = session,
                copywriter = copywriter,
                onReject = { controller.reject(session.sessionId) },
                onDismiss = { controller.dismiss(session.sessionId) },
            )
        }
    }
}

@Composable
private fun PairingV3SessionCard(
    session: PairingSessionUiState,
    copywriter: GlobalCopywriter,
    onReject: suspend () -> Unit,
    onDismiss: suspend () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val secondsRemaining by rememberPairingSecondsRemaining(
        pinExpiresAt = session.pinExpiresAt,
        generation = session.tokenGeneration,
    )
    val progress =
        (secondsRemaining.toFloat() / PairingV3.DEFAULT_PIN_LIFETIME.inWholeSeconds)
            .coerceIn(0f, 1f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(medium),
            verticalArrangement = Arrangement.spacedBy(tiny),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(xxLarge)
                            .border(
                                width = tiny5X,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.medium,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(medium),
                    )
                }
                Spacer(modifier = Modifier.width(tiny))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.peerDisplayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        text =
                            copywriter.getText(
                                "pairing_v3_key_fingerprint",
                                session.peerKeyFingerprintDisplay,
                            ),
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = copywriter.getText(session.state.copyKey()),
                    style = MaterialTheme.typography.labelMedium,
                    color = session.state.statusColor(),
                )
                Spacer(modifier = Modifier.width(tiny3X))
                if (session.state.isTerminal) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                onDismiss()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = copywriter.getText("pairing_v3_dismiss"),
                        )
                    }
                } else {
                    FilledTonalIconButton(
                        onClick = {
                            coroutineScope.launch {
                                onReject()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Close,
                            contentDescription = copywriter.getText("pairing_v3_reject"),
                        )
                    }
                }
            }

            if (session.state == PairingSessionState.PIN_AVAILABLE && session.pin != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tiny3X, Alignment.CenterHorizontally),
                ) {
                    session.pin.forEach { digit ->
                        Surface(
                            modifier = Modifier.size(width = small2X, height = xxLarge),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = digit.toString(),
                                    style =
                                        MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                )
                            }
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(tiny2X),
                )
                Text(
                    text = copywriter.getText("pairing_v3_expires_in", secondsRemaining),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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
