package com.crosspaste.ui.devices

import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import com.crosspaste.pairing.v3.PairingProtocolV3Service
import com.crosspaste.pairing.v3.PairingSessionState
import com.crosspaste.pairing.v3.PairingSessionUiState
import com.crosspaste.pairing.v3.PairingV3PinResult
import com.crosspaste.pairing.v3.PairingV3RefreshResult
import com.crosspaste.pairing.v3.PairingV3StartResult
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.V3Pin
import com.crosspaste.utils.HostAndPort
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.namedScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface PairingV3UiController {

    val sessions: StateFlow<List<PairingSessionUiState>>

    val acceptanceOpenUntil: StateFlow<Long>

    fun openAcceptanceWindow()

    fun closeAcceptanceWindow()

    suspend fun startPairing(peerAppInstanceId: String): PairingV3UiResult

    suspend fun submitPin(
        sessionId: String,
        pin: V3Pin,
    ): PairingV3UiResult

    suspend fun recover(sessionId: String): PairingV3UiResult

    suspend fun reject(sessionId: String): Boolean

    suspend fun cancel(sessionId: String): Boolean

    /**
     * Fire-and-forget [cancel] on a scope that outlives the caller's composition.
     * Used when the pairing dialog leaves the tree without an explicit user action,
     * so an abandoned initiator session does not linger and block the peer's v2
     * downgrade for the full session TTL.
     */
    fun cancelDetached(sessionId: String)

    suspend fun dismiss(sessionId: String): Boolean
}

enum class PairingV3UiError {
    PIN_EXPIRED,
    INCORRECT_PIN,
    NETWORK_FAILURE,
    NOT_ACCEPTING,
    REJECTED,
    CANCELLED,
    SESSION_EXPIRED,
    RATE_LIMITED,
    CAPACITY_EXCEEDED,
    UNSUPPORTED,
    IDENTITY_INVALID,
    FAILED,
}

enum class PairingV3Recovery {
    NONE,
    RETRY_START,
    REFRESH_OFFER,
    RETRY_COMMIT,
}

sealed interface PairingV3UiResult {

    data class SessionReady(
        val sessionId: String,
        val tokenGeneration: Long,
        val pinExpiresAt: Long,
        val peerKeyFingerprintDisplay: String,
    ) : PairingV3UiResult

    data object Paired : PairingV3UiResult

    data class Error(
        val reason: PairingV3UiError,
        val recovery: PairingV3Recovery = PairingV3Recovery.NONE,
    ) : PairingV3UiResult
}

class DefaultPairingV3UiController(
    private val pairingProtocolV3Service: PairingProtocolV3Service,
    private val syncManager: SyncManager,
    private val detachedScope: CoroutineScope = namedScope(ioDispatcher, "PairingV3UiController"),
) : PairingV3UiController {

    override val sessions: StateFlow<List<PairingSessionUiState>> =
        pairingProtocolV3Service.uiSessionsFlow

    override val acceptanceOpenUntil: StateFlow<Long> =
        pairingProtocolV3Service.acceptanceWindow.openUntil

    override fun openAcceptanceWindow() {
        pairingProtocolV3Service.acceptanceWindow.open()
    }

    override fun closeAcceptanceWindow() {
        pairingProtocolV3Service.acceptanceWindow.close()
    }

    // Every entry point runs off the UI thread: PAKE, identity signing, and the
    // HTTP round-trips must not execute on the dialog's main-thread coroutine
    // scope. The legacy v2 trust paths dispatch through the sync manager's
    // ioDispatcher scope for the same reason.
    override suspend fun startPairing(peerAppInstanceId: String): PairingV3UiResult =
        withContext(ioDispatcher) {
            val target =
                resolveTarget(peerAppInstanceId)
                    ?: return@withContext PairingV3UiResult.Error(
                        PairingV3UiError.NETWORK_FAILURE,
                        PairingV3Recovery.RETRY_START,
                    )
            pairingProtocolV3Service
                .startPairing(
                    targetAppInstanceId = peerAppInstanceId,
                    targetDisplayName = target.displayName,
                    toUrl = target.toUrl,
                ).toUiResult()
        }

    override suspend fun submitPin(
        sessionId: String,
        pin: V3Pin,
    ): PairingV3UiResult =
        withContext(ioDispatcher) {
            val session =
                sessions.value.firstOrNull { it.sessionId == sessionId }
                    ?: return@withContext PairingV3UiResult.Error(PairingV3UiError.FAILED)
            val target =
                resolveTarget(session.peerAppInstanceId)
                    ?: return@withContext PairingV3UiResult.Error(
                        PairingV3UiError.NETWORK_FAILURE,
                        PairingV3Recovery.REFRESH_OFFER,
                    )
            val pinChars = pin.value.toCharArray()
            try {
                pairingProtocolV3Service.submitPin(sessionId, pinChars, target.toUrl).toUiResult()
            } finally {
                pinChars.fill('\u0000')
            }
        }

    override suspend fun recover(sessionId: String): PairingV3UiResult =
        withContext(ioDispatcher) {
            val session =
                sessions.value.firstOrNull { it.sessionId == sessionId }
                    ?: return@withContext PairingV3UiResult.Error(PairingV3UiError.FAILED)
            val target =
                resolveTarget(session.peerAppInstanceId)
                    ?: return@withContext PairingV3UiResult.Error(
                        PairingV3UiError.NETWORK_FAILURE,
                        PairingV3Recovery.REFRESH_OFFER,
                    )
            if (session.state == PairingSessionState.COMMITTING) {
                pairingProtocolV3Service.retryCommit(sessionId, target.toUrl).toUiResult()
            } else {
                pairingProtocolV3Service.refreshOffer(sessionId, target.toUrl).toUiResult(sessionId)
            }
        }

    override suspend fun reject(sessionId: String): Boolean =
        withContext(ioDispatcher) { pairingProtocolV3Service.rejectSession(sessionId) }

    override suspend fun cancel(sessionId: String): Boolean =
        withContext(ioDispatcher) {
            val session = sessions.value.firstOrNull { it.sessionId == sessionId }
            val target = session?.let { resolveTarget(it.peerAppInstanceId) }
            pairingProtocolV3Service.cancelSession(sessionId, target?.toUrl)
        }

    override fun cancelDetached(sessionId: String) {
        detachedScope.launch { cancel(sessionId) }
    }

    override suspend fun dismiss(sessionId: String): Boolean =
        withContext(ioDispatcher) { pairingProtocolV3Service.dismissSession(sessionId) }

    private suspend fun resolveTarget(peerAppInstanceId: String): PairingTarget? {
        val handler = syncManager.getSyncHandler(peerAppInstanceId) ?: return null
        val host = handler.getConnectHostAddress() ?: return null
        val runtimeInfo = handler.currentSyncRuntimeInfo
        return PairingTarget(
            displayName = runtimeInfo.getDeviceDisplayName(),
            toUrl = {
                buildUrl(HostAndPort(host, runtimeInfo.port))
            },
        )
    }

    private data class PairingTarget(
        val displayName: String,
        val toUrl: io.ktor.http.URLBuilder.() -> Unit,
    )
}

internal fun PairingV3StartResult.toUiResult(): PairingV3UiResult =
    when (this) {
        is PairingV3StartResult.Started ->
            PairingV3UiResult.SessionReady(
                sessionId = sessionId,
                tokenGeneration = tokenGeneration,
                pinExpiresAt = pinExpiresAt,
                peerKeyFingerprintDisplay = peerKeyFingerprintDisplay,
            )

        is PairingV3StartResult.Refused ->
            PairingV3UiResult.Error(
                reason = code.toUiError(),
                recovery = code.startRecovery(),
            )

        is PairingV3StartResult.NetworkError ->
            PairingV3UiResult.Error(
                PairingV3UiError.NETWORK_FAILURE,
                PairingV3Recovery.RETRY_START,
            )
    }

internal fun PairingV3PinResult.toUiResult(): PairingV3UiResult =
    when (this) {
        is PairingV3PinResult.Paired -> PairingV3UiResult.Paired
        is PairingV3PinResult.Refused ->
            PairingV3UiResult.Error(
                reason = code.toUiError(),
                recovery = code.pinRecovery(),
            )

        is PairingV3PinResult.NetworkError ->
            PairingV3UiResult.Error(
                reason = PairingV3UiError.NETWORK_FAILURE,
                recovery =
                    if (commitPending) {
                        PairingV3Recovery.RETRY_COMMIT
                    } else {
                        PairingV3Recovery.REFRESH_OFFER
                    },
            )
    }

internal fun PairingV3RefreshResult.toUiResult(sessionId: String): PairingV3UiResult =
    when (this) {
        is PairingV3RefreshResult.Refreshed ->
            PairingV3UiResult.SessionReady(
                sessionId = sessionId,
                tokenGeneration = tokenGeneration,
                pinExpiresAt = pinExpiresAt,
                peerKeyFingerprintDisplay = "",
            )

        is PairingV3RefreshResult.Refused ->
            PairingV3UiResult.Error(
                reason = code.toUiError(),
                recovery = code.pinRecovery(),
            )

        is PairingV3RefreshResult.NetworkError ->
            PairingV3UiResult.Error(
                PairingV3UiError.NETWORK_FAILURE,
                PairingV3Recovery.REFRESH_OFFER,
            )
    }

private fun PairingV3ErrorCode.toUiError(): PairingV3UiError =
    when (this) {
        PairingV3ErrorCode.PAIRING_PIN_EXPIRED -> PairingV3UiError.PIN_EXPIRED
        PairingV3ErrorCode.PAIRING_PROOF_INVALID -> PairingV3UiError.INCORRECT_PIN
        PairingV3ErrorCode.PAIRING_REJECTED -> PairingV3UiError.REJECTED
        PairingV3ErrorCode.PAIRING_CANCELLED -> PairingV3UiError.CANCELLED
        PairingV3ErrorCode.PAIRING_SESSION_EXPIRED,
        PairingV3ErrorCode.PAIRING_SESSION_CONSUMED,
        PairingV3ErrorCode.PAIRING_SESSION_NOT_FOUND,
        -> PairingV3UiError.SESSION_EXPIRED

        PairingV3ErrorCode.PAIRING_RATE_LIMITED -> PairingV3UiError.RATE_LIMITED
        PairingV3ErrorCode.PAIRING_CAPACITY_EXCEEDED -> PairingV3UiError.CAPACITY_EXCEEDED
        PairingV3ErrorCode.PAIRING_DISABLED -> PairingV3UiError.NOT_ACCEPTING
        PairingV3ErrorCode.PAIRING_VERSION_UNSUPPORTED,
        PairingV3ErrorCode.PAIRING_CIPHERSUITE_UNSUPPORTED,
        -> PairingV3UiError.UNSUPPORTED

        PairingV3ErrorCode.PAIRING_IDENTITY_INVALID,
        PairingV3ErrorCode.PAIRING_TRANSCRIPT_MISMATCH,
        -> PairingV3UiError.IDENTITY_INVALID

        PairingV3ErrorCode.PAIRING_INVALID_STATE -> PairingV3UiError.FAILED
    }

private fun PairingV3ErrorCode.startRecovery(): PairingV3Recovery =
    when (this) {
        // The acceptor is not accepting yet, is over capacity, or is rate limiting:
        // all clear on their own, so the user can simply retry the intent.
        PairingV3ErrorCode.PAIRING_CAPACITY_EXCEEDED,
        PairingV3ErrorCode.PAIRING_RATE_LIMITED,
        PairingV3ErrorCode.PAIRING_DISABLED,
        -> PairingV3Recovery.RETRY_START

        else -> PairingV3Recovery.NONE
    }

private fun PairingV3ErrorCode.pinRecovery(): PairingV3Recovery =
    when (this) {
        // A wrong or expired PIN invalidated the generation, and a rate-limited
        // proof did not consume it: re-fetch the current offer and let the user try
        // the (possibly rotated) PIN again.
        PairingV3ErrorCode.PAIRING_PIN_EXPIRED,
        PairingV3ErrorCode.PAIRING_PROOF_INVALID,
        PairingV3ErrorCode.PAIRING_RATE_LIMITED,
        -> PairingV3Recovery.REFRESH_OFFER

        else -> PairingV3Recovery.NONE
    }
