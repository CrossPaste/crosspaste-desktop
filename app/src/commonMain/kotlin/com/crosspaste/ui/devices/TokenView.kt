package com.crosspaste.ui.devices

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.crosspaste.app.AppTokenApi
import com.crosspaste.db.sync.SyncState
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.SyncManager
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

@Composable
fun TokenView(intOffset: IntOffset) {
    val appTokenApi = koinInject<AppTokenApi>()
    val syncManager = koinInject<SyncManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val pairingV3UiController = koinInject<PairingV3UiController>()
    val showToken by appTokenApi.showToken.collectAsState()
    val progress by appTokenApi.refreshProgress.collectAsState()
    val token by appTokenApi.token.collectAsState()
    val pairingSessions by pairingV3UiController.sessions.collectAsState()
    val incomingPairingSessions = acceptorPairingSessions(pairingSessions)

    // Reap pending verifiers whose device left UNVERIFIED without completing the
    // handshake here (went offline / disconnected / trusted out-of-band). This is
    // NOT the success path: a successful trust/confirm removes its verifier and
    // releases its refresh count server-side before the UI can observe it, so
    // `pending` is already empty and this effect never fires (#4684). Each
    // verifier still parked here owns one refresh count (its exchange/showToken
    // never got a matching release), so release one count per reaped verifier —
    // a single decrement would hide the overlay but leave the refresh loop
    // running in the background.
    val pending by appTokenApi.pendingVerifiers.collectAsState()
    val syncRuntimeInfos by syncManager.realTimeSyncRuntimeInfos.collectAsState()

    val allResolved =
        showToken &&
            pending.isNotEmpty() &&
            pending.all { verifierId ->
                val info = syncRuntimeInfos.find { it.appInstanceId == verifierId }
                info != null && info.connectState != SyncState.UNVERIFIED
            }

    LaunchedEffect(allResolved) {
        if (allResolved) {
            pending.forEach {
                appTokenApi.removePendingVerifier(it)
                appTokenApi.stopRefresh(hideToken = true)
            }
        }
    }

    if (showToken || incomingPairingSessions.isNotEmpty()) {
        TokenPopupStack(intOffset = intOffset) {
            if (showToken) {
                TokenPopupCard(
                    title = copywriter.getText("token"),
                    token = token.map(Char::toString),
                    progress = progress,
                    onClose = { appTokenApi.stopRefresh(hideToken = true) },
                )
            }
            PairingV3AcceptorTokenCards(
                sessions = incomingPairingSessions,
                controller = pairingV3UiController,
            )
        }
    }
}

@Composable
private fun TokenPopupStack(
    intOffset: IntOffset,
    content: @Composable () -> Unit,
) {
    val appSizeValue = LocalAppSizeValueState.current

    Popup(
        alignment = Alignment.TopCenter,
        offset = intOffset,
        properties = PopupProperties(clippingEnabled = false),
    ) {
        Column(
            modifier =
                Modifier
                    .heightIn(max = appSizeValue.mainWindowSize.height - small2X)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = tiny),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tiny),
        ) {
            content()
        }
    }
}

@Composable
fun TokenPopup(
    intOffset: IntOffset,
    title: String,
    token: List<String>,
    progress: Float,
    onClose: () -> Unit,
    deviceDisplayName: String? = null,
    statusText: String? = null,
    closeContentDescription: String = "Close",
) {
    TokenPopupStack(intOffset = intOffset) {
        TokenPopupCard(
            title = title,
            token = token,
            progress = progress,
            onClose = onClose,
            deviceDisplayName = deviceDisplayName,
            statusText = statusText,
            closeContentDescription = closeContentDescription,
        )
    }
}

@Composable
fun TokenPopupCard(
    title: String,
    token: List<String>?,
    progress: Float,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    deviceDisplayName: String? = null,
    statusText: String? = null,
    stateText: String? = null,
    stateColor: Color = Color.Unspecified,
    closeContentDescription: String = "Close",
) {
    val appSizeValue = LocalAppSizeValueState.current

    Surface(
        modifier =
            modifier
                .width(appSizeValue.tokenViewWidth)
                .wrapContentHeight(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = small,
        shadowElevation = small,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = small2X),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = small, start = small2X, end = tiny),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = closeContentDescription,
                        modifier = Modifier.size(medium),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            deviceDisplayName?.let { displayName ->
                Text(
                    text = displayName,
                    modifier = Modifier.padding(horizontal = small2X),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            statusText?.let { status ->
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = small2X),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            stateText?.let { state ->
                Text(
                    text = state,
                    modifier = Modifier.padding(horizontal = small2X),
                    style = MaterialTheme.typography.bodySmall,
                    color = stateColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant },
                    textAlign = TextAlign.Center,
                )
            }

            token?.let {
                OTPCodeBox(token = it, progress = progress)
            }
        }
    }
}

@Composable
private fun OTPCodeBox(
    token: List<String>,
    progress: Float,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = small2X),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(small),
            modifier = Modifier.padding(vertical = small2X),
        ) {
            token.forEach { value ->
                TokenDisplayBox(char = value)
            }
        }

        Spacer(modifier = Modifier.height(small))

        LinearProgressIndicator(
            progress = { progress },
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .height(tiny2X)
                    .clip(MaterialTheme.shapes.extraLarge),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun TokenDisplayBox(
    char: String,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium

    Surface(
        modifier =
            modifier
                .size(width = xxxLarge, height = xxxxLarge)
                .border(
                    width = tiny5X,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    shape = shape,
                ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = zero,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = char,
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                    ),
            )
        }
    }
}
