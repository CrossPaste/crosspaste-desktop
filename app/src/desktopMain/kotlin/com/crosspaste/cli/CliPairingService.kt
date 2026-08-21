package com.crosspaste.cli

import com.crosspaste.app.AppTokenApi
import com.crosspaste.db.sync.SyncRuntimeInfo
import com.crosspaste.db.sync.SyncState
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.PasteBonjourService
import com.crosspaste.pairing.v3.PairingCapabilityFlag
import com.crosspaste.sync.NearbyDeviceManager
import com.crosspaste.sync.PairingCredentialRefreshResult
import com.crosspaste.sync.PairingCredentialType
import com.crosspaste.sync.SasCode
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.V3Pin
import com.crosspaste.sync.selectPairingCredentialType
import com.crosspaste.ui.devices.PairingV3Recovery
import com.crosspaste.ui.devices.PairingV3UiController
import com.crosspaste.ui.devices.PairingV3UiError
import com.crosspaste.ui.devices.PairingV3UiResult
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.StripedMutex
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Initiator-side pairing driver for the /cli/pair routes (P6.2, D-P6-3).
 *
 * The CLI is a one-shot process, so pairing state between `initiate` and
 * `submit` lives here as a short-lived in-memory session. Both credential
 * flows reuse the exact call sequences the desktop dialogs use:
 *
 * - v2 SAS: persist the discovered peer ([SyncManager.updateSyncInfo]), wait
 *   for the resolver to reach UNVERIFIED with a resolved host, warm up via
 *   [SyncManager.exchangeKeysForPairing] (makes the peer display its SAS), then
 *   confirm with [SyncManager.trustBySasCode].
 * - v3 PIN: [PairingV3UiController.startPairing] / submitPin / recover, which
 *   also makes the peer surface its pairing UI (showPairingCode) so a closed
 *   acceptance window opens the same way it does for a GUI initiator.
 *
 * Abandoned sessions are the peer's responsibility to expire (v2 pending
 * exchange TTL, v3 session TTL); the local [SESSION_TTL] only bounds this map.
 * Pairing codes are never logged.
 */
class CliPairingService(
    private val appTokenApi: AppTokenApi,
    private val nearbyDeviceManager: NearbyDeviceManager,
    private val pairingCapabilityFlag: PairingCapabilityFlag,
    private val pairingV3UiController: PairingV3UiController,
    private val pasteBonjourService: PasteBonjourService,
    private val syncManager: SyncManager,
) {

    companion object {
        private val SESSION_TTL = 10.minutes
        private val RESOLVE_TIMEOUT = 15.seconds
        private val TRUST_TIMEOUT = 30.seconds
        private val RECOVERY_TIMEOUT = 10.seconds
        private val SCAN_START_TIMEOUT = 2.seconds
        private val SCAN_FINISH_TIMEOUT = 10.seconds

        private val SIX_DIGITS = Regex("\\d{6}")
    }

    private val logger = KotlinLogging.logger {}

    private data class PairSession(
        val sessionId: String,
        val appInstanceId: String,
        val deviceName: String,
        val credentialType: PairingCredentialType,
        val v3SessionId: String?,
        val createdAt: Long,
        val nextV3Action: V3Action = V3Action.SUBMIT_PIN,
        val operationMutex: Mutex = Mutex(),
    )

    private enum class V3Action {
        SUBMIT_PIN,
        RECOVER,
    }

    private val sessions = ConcurrentHashMap<String, PairSession>()
    private val initiateMutex = StripedMutex()

    sealed interface InitiateOutcome {
        data class Started(
            val session: CliPairSessionDto,
        ) : InitiateOutcome

        data class DeviceNotFound(
            val message: String,
        ) : InitiateOutcome

        data class AlreadyPaired(
            val message: String,
        ) : InitiateOutcome

        data class UnsupportedPeer(
            val message: String,
        ) : InitiateOutcome

        data class Unavailable(
            val message: String,
        ) : InitiateOutcome
    }

    sealed interface SubmitOutcome {
        data class Completed(
            val result: CliPairSubmitResultDto,
        ) : SubmitOutcome

        data object SessionNotFound : SubmitOutcome

        data object InvalidCode : SubmitOutcome
    }

    suspend fun nearbyDevices(refresh: Boolean): List<CliNearbyDeviceDto> {
        if (refresh) {
            pasteBonjourService.refreshAll()
            // refreshAll is fire-and-forget; `searching` flips true for the
            // scan and back. Tolerate a scan that finished before we started
            // observing (start-flip timeout) or that never ran at all.
            withTimeoutOrNull(SCAN_START_TIMEOUT) {
                nearbyDeviceManager.searching.first { it }
            }
            withTimeoutOrNull(SCAN_FINISH_TIMEOUT) {
                nearbyDeviceManager.searching.first { !it }
            }
        }
        return nearbyDeviceManager.nearbySyncInfos.value.map { it.toNearbyDto() }
    }

    /**
     * Acceptor-side snapshot of the pairing code display — the same state the
     * desktop token overlay observes. `active` follows [AppTokenApi.showToken]
     * exactly, so the code is exposed only while a peer's exchange (or an
     * explicit show-token request) has it on display; the value is meaningless
     * otherwise. Read-only, and the code itself is never logged.
     */
    fun pairingToken(): CliPairTokenDto {
        val active = appTokenApi.showToken.value
        return CliPairTokenDto(
            active = active,
            token = if (active) appTokenApi.token.value.concatToString() else null,
            requesters =
                appTokenApi.pendingVerifiers.value.map { appInstanceId ->
                    CliPairRequesterDto(
                        appInstanceId = appInstanceId,
                        deviceName = resolveDeviceName(appInstanceId),
                    )
                },
        )
    }

    private fun resolveDeviceName(appInstanceId: String): String? =
        nearbyDeviceManager.nearbySyncInfos.value
            .firstOrNull { it.appInfo.appInstanceId == appInstanceId }
            ?.endpointInfo
            ?.deviceName
            ?: findRuntimeInfo(appInstanceId)?.getDeviceDisplayName()

    suspend fun initiate(appInstanceId: String): InitiateOutcome =
        initiateMutex.withLock(appInstanceId) { initiateLocked(appInstanceId) }

    private suspend fun initiateLocked(appInstanceId: String): InitiateOutcome {
        evictExpiredSessions()
        // A re-initiate for the same device supersedes the previous attempt;
        // cancel it so the peer's pending exchange / v3 session is released
        // instead of lingering until its TTL.
        sessions.values
            .filter { it.appInstanceId == appInstanceId }
            .forEach { cancel(it.sessionId) }

        val nearby =
            nearbyDeviceManager.nearbySyncInfos.value
                .firstOrNull { it.appInfo.appInstanceId == appInstanceId }
        val existing = findRuntimeInfo(appInstanceId)
        when {
            existing?.connectState == SyncState.CONNECTED ->
                return InitiateOutcome.AlreadyPaired(
                    "Device is already paired.",
                )

            nearby != null -> syncManager.updateSyncInfo(nearby)

            existing != null -> syncManager.refresh(listOf(appInstanceId))

            else ->
                return InitiateOutcome.DeviceNotFound(
                    "Device '$appInstanceId' was not found nearby. " +
                        "Make sure CrossPaste is running on it and both are on the same network.",
                )
        }

        val resolved = awaitPairableState(appInstanceId)
        when {
            resolved == null ->
                return InitiateOutcome.Unavailable(
                    "Device did not become reachable within ${RESOLVE_TIMEOUT.inWholeSeconds}s.",
                )

            resolved.connectState == SyncState.CONNECTED ->
                return InitiateOutcome.AlreadyPaired("Device is already paired.")

            resolved.connectState != SyncState.UNVERIFIED ->
                return InitiateOutcome.Unavailable(
                    "Device is in an unexpected state (${connectStateName(resolved.connectState)}); " +
                        "check it in the CrossPaste app and retry.",
                )
        }

        val credentialType =
            when (val result = syncManager.refreshPairingCredentialType(appInstanceId)) {
                is PairingCredentialRefreshResult.Resolved -> result.credentialType
                else ->
                    return InitiateOutcome.Unavailable(
                        "Could not determine the pairing method for the device; retry in a moment.",
                    )
            }

        val deviceName = resolved.getDeviceDisplayName()
        return when (credentialType) {
            PairingCredentialType.QR_BEARER_TOKEN ->
                InitiateOutcome.UnsupportedPeer(
                    "The device only supports QR pairing (app too old for CLI pairing); " +
                        "update CrossPaste on it first.",
                )

            PairingCredentialType.SAS_CODE -> {
                // Warm-up exchange so the peer displays its SAS before the
                // user is asked to type it (same as the desktop dialog).
                syncManager.exchangeKeysForPairing(appInstanceId)
                InitiateOutcome.Started(
                    newSession(appInstanceId, deviceName, credentialType, v3SessionId = null),
                )
            }

            PairingCredentialType.V3_PIN ->
                when (val result = pairingV3UiController.startPairing(appInstanceId)) {
                    is PairingV3UiResult.SessionReady ->
                        InitiateOutcome.Started(
                            newSession(
                                appInstanceId = appInstanceId,
                                deviceName = deviceName,
                                credentialType = credentialType,
                                v3SessionId = result.sessionId,
                                peerFingerprint = result.peerKeyFingerprintDisplay,
                                pinExpiresAt = result.pinExpiresAt,
                            ),
                        )

                    is PairingV3UiResult.Error ->
                        InitiateOutcome.Unavailable(describeV3Error(result.reason))

                    PairingV3UiResult.Paired ->
                        InitiateOutcome.AlreadyPaired("Device is already paired.")
                }
        }
    }

    suspend fun submit(
        sessionId: String,
        code: String,
    ): SubmitOutcome {
        evictExpiredSessions()
        val entry = sessions[sessionId] ?: return SubmitOutcome.SessionNotFound
        return entry.operationMutex.withLock {
            val session = sessions[sessionId] ?: return@withLock SubmitOutcome.SessionNotFound
            if (!SIX_DIGITS.matches(code)) {
                return@withLock SubmitOutcome.InvalidCode
            }
            when (session.credentialType) {
                PairingCredentialType.SAS_CODE -> submitSas(session, code)
                PairingCredentialType.V3_PIN -> submitV3(session, code)
                PairingCredentialType.QR_BEARER_TOKEN ->
                    // Unreachable: initiate refuses QR peers
                    SubmitOutcome.SessionNotFound
            }
        }
    }

    fun cancel(sessionId: String): Boolean {
        // Cancellation wins ownership immediately instead of waiting behind a
        // potentially hung submit. An in-flight submit cannot resurrect the
        // removed entry because every state update uses computeIfPresent.
        val session = sessions.remove(sessionId) ?: return false
        when (session.credentialType) {
            PairingCredentialType.SAS_CODE -> syncManager.cancelPairing(session.appInstanceId)
            PairingCredentialType.V3_PIN ->
                session.v3SessionId?.let { pairingV3UiController.cancelDetached(it) }
            PairingCredentialType.QR_BEARER_TOKEN -> Unit
        }
        logger.info { "Cancelled CLI pairing session for ${session.appInstanceId}" }
        return true
    }

    private suspend fun submitSas(
        session: PairSession,
        code: String,
    ): SubmitOutcome {
        val callbackResult =
            withTimeoutOrNull(TRUST_TIMEOUT) {
                suspendCancellableCoroutine { cont ->
                    syncManager.trustBySasCode(session.appInstanceId, SasCode(code.toInt())) { ok ->
                        cont.resume(ok)
                    }
                }
            }
        // trustBySasCode runs on the sync manager's own scope, so a timeout
        // here does not cancel it — and it also reports false when the device
        // is no longer UNVERIFIED, which includes "already CONNECTED" (e.g. a
        // slow trust that finished after our timeout, then the user retried).
        // Trust the observed state over the callback before reporting failure.
        val trusted =
            callbackResult == true ||
                findRuntimeInfo(session.appInstanceId)?.connectState == SyncState.CONNECTED
        return if (trusted) {
            sessions.remove(session.sessionId)
            SubmitOutcome.Completed(
                CliPairSubmitResultDto(
                    paired = true,
                    retryable = false,
                    message = "Paired with ${session.deviceName}.",
                ),
            )
        } else {
            // trustBySasCode reports one flag for mismatch, state change, and
            // transport failure alike; each retry runs a fresh exchange, so the
            // session stays usable.
            SubmitOutcome.Completed(
                CliPairSubmitResultDto(
                    paired = false,
                    retryable = true,
                    message =
                        "Pairing failed: the code did not match or the device is no longer ready. " +
                            "Check the code shown on ${session.deviceName} and try again.",
                ),
            )
        }
    }

    private suspend fun submitV3(
        session: PairSession,
        code: String,
    ): SubmitOutcome {
        // Symmetric to the SAS TRUST_TIMEOUT: the route must always answer
        // within the CLI's own budget even if a v3 round-trip hangs
        val outcome = withTimeoutOrNull(TRUST_TIMEOUT) { submitV3Steps(session, code) }
        if (outcome != null) {
            return outcome
        }

        // Cancellation can leave the protocol session in PAKE_NEGOTIATING or
        // COMMITTING. Recover on a fresh timeout scope before claiming the CLI
        // session is retryable; if recovery also hangs, the next submit must
        // retry recovery rather than feed another PIN into an unknown state.
        if (!setNextV3Action(session, V3Action.RECOVER)) {
            return SubmitOutcome.SessionNotFound
        }
        val recovered =
            withTimeoutOrNull(RECOVERY_TIMEOUT) {
                pairingV3UiController.recover(checkNotNull(session.v3SessionId))
            }
        return when (recovered) {
            PairingV3UiResult.Paired -> completeV3Pairing(session)
            is PairingV3UiResult.SessionReady -> {
                if (setNextV3Action(session, V3Action.SUBMIT_PIN)) {
                    retryableV3Failure(session, "Pairing timed out; check the connection and try again.")
                } else {
                    SubmitOutcome.SessionNotFound
                }
            }
            is PairingV3UiResult.Error -> finishV3Error(session, recovered)
            null ->
                retryableV3Failure(session, "Pairing timed out; check the connection and try again.")
        }
    }

    private fun retryableV3Failure(
        session: PairSession,
        message: String,
    ): SubmitOutcome =
        if (sessions.containsKey(session.sessionId)) {
            SubmitOutcome.Completed(
                CliPairSubmitResultDto(
                    paired = false,
                    retryable = true,
                    message = message,
                ),
            )
        } else {
            SubmitOutcome.SessionNotFound
        }

    private suspend fun submitV3Steps(
        session: PairSession,
        code: String,
    ): SubmitOutcome {
        val v3SessionId = checkNotNull(session.v3SessionId)
        var submittedPin = session.nextV3Action == V3Action.SUBMIT_PIN
        var result =
            if (submittedPin) {
                pairingV3UiController.submitPin(v3SessionId, V3Pin(code))
            } else {
                pairingV3UiController.recover(v3SessionId)
            }
        if (result is PairingV3UiResult.SessionReady) {
            if (!setNextV3Action(session, V3Action.SUBMIT_PIN)) {
                return SubmitOutcome.SessionNotFound
            }
            result = pairingV3UiController.submitPin(v3SessionId, V3Pin(code))
            submittedPin = true
        }
        if (submittedPin && result is PairingV3UiResult.Error && result.recovery.requiresRecovery()) {
            val originalError = result
            if (!setNextV3Action(session, V3Action.RECOVER)) {
                return SubmitOutcome.SessionNotFound
            }
            result = pairingV3UiController.recover(v3SessionId)
            if (result is PairingV3UiResult.SessionReady) {
                if (!setNextV3Action(session, V3Action.SUBMIT_PIN)) {
                    return SubmitOutcome.SessionNotFound
                }
                return retryableV3Failure(session, describeV3Error(originalError.reason))
            }
        }
        return when (result) {
            PairingV3UiResult.Paired -> completeV3Pairing(session)
            is PairingV3UiResult.Error -> finishV3Error(session, result)

            is PairingV3UiResult.SessionReady ->
                // submitPin never returns SessionReady; treat defensively
                SubmitOutcome.Completed(
                    CliPairSubmitResultDto(
                        paired = false,
                        retryable = true,
                        message = "Pairing is not complete yet; enter the PIN shown on ${session.deviceName}.",
                    ),
                )
        }
    }

    private fun completeV3Pairing(session: PairSession): SubmitOutcome {
        sessions.remove(session.sessionId)
        // Converge the sync state machine to CONNECTED now that the peer key is
        // persisted (same as the dialog's completePairing).
        syncManager.refresh(listOf(session.appInstanceId))
        return SubmitOutcome.Completed(
            CliPairSubmitResultDto(
                paired = true,
                retryable = false,
                message = "Paired with ${session.deviceName}.",
            ),
        )
    }

    private fun finishV3Error(
        session: PairSession,
        error: PairingV3UiResult.Error,
    ): SubmitOutcome {
        val retryable = error.recovery.requiresRecovery()
        if (retryable) {
            if (!setNextV3Action(session, V3Action.RECOVER)) {
                return SubmitOutcome.SessionNotFound
            }
        } else {
            sessions.remove(session.sessionId)
        }
        return SubmitOutcome.Completed(
            CliPairSubmitResultDto(
                paired = false,
                retryable = retryable,
                message = describeV3Error(error.reason),
            ),
        )
    }

    private fun PairingV3Recovery.requiresRecovery(): Boolean =
        this == PairingV3Recovery.REFRESH_OFFER || this == PairingV3Recovery.RETRY_COMMIT

    private suspend fun awaitPairableState(appInstanceId: String): SyncRuntimeInfo? =
        withTimeoutOrNull(RESOLVE_TIMEOUT) {
            syncManager.realTimeSyncRuntimeInfos
                .map { infos -> infos.firstOrNull { it.appInstanceId == appInstanceId } }
                .first { info ->
                    info != null &&
                        when (info.connectState) {
                            SyncState.CONNECTED,
                            SyncState.UNMATCHED,
                            SyncState.INCOMPATIBLE,
                            -> true

                            SyncState.UNVERIFIED -> info.connectHostAddress != null
                            // DISCONNECTED can be a transient probe result while
                            // the resolver walks the host list; keep waiting
                            else -> false
                        }
                }
        }

    private fun findRuntimeInfo(appInstanceId: String): SyncRuntimeInfo? =
        syncManager.realTimeSyncRuntimeInfos.value.firstOrNull { it.appInstanceId == appInstanceId }

    // Always writes through to the map: the caller's PairSession is a snapshot
    // from submit entry and may be stale after an earlier update in the same call.
    private fun setNextV3Action(
        session: PairSession,
        action: V3Action,
    ): Boolean {
        var updated = false
        sessions.computeIfPresent(session.sessionId) { _, current ->
            updated = true
            current.copy(nextV3Action = action)
        }
        return updated
    }

    private fun newSession(
        appInstanceId: String,
        deviceName: String,
        credentialType: PairingCredentialType,
        v3SessionId: String?,
        peerFingerprint: String? = null,
        pinExpiresAt: Long? = null,
    ): CliPairSessionDto {
        val session =
            PairSession(
                sessionId = UUID.randomUUID().toString(),
                appInstanceId = appInstanceId,
                deviceName = deviceName,
                credentialType = credentialType,
                v3SessionId = v3SessionId,
                createdAt = DateUtils.nowEpochMilliseconds(),
            )
        sessions[session.sessionId] = session
        return CliPairSessionDto(
            sessionId = session.sessionId,
            appInstanceId = appInstanceId,
            deviceName = deviceName,
            credentialType = credentialType.name,
            peerFingerprint = peerFingerprint,
            pinExpiresAt = pinExpiresAt,
        )
    }

    private fun evictExpiredSessions() {
        val cutoff = DateUtils.nowEpochMilliseconds() - SESSION_TTL.inWholeMilliseconds
        sessions.values
            .filter { it.createdAt < cutoff }
            .forEach { sessions.remove(it.sessionId) }
    }

    private fun SyncInfo.toNearbyDto(): CliNearbyDeviceDto =
        CliNearbyDeviceDto(
            appInstanceId = appInfo.appInstanceId,
            deviceName = endpointInfo.deviceName,
            platform = endpointInfo.platform.name,
            appVersion = appInfo.appVersion,
            pairingVersion = appInfo.pairingVersion,
            credentialType =
                selectPairingCredentialType(
                    localPairingVersion = pairingCapabilityFlag.advertisedPairingVersion,
                    remotePairingVersion = appInfo.pairingVersion,
                ).name,
        )

    private fun connectStateName(state: Int): String =
        when (state) {
            SyncState.CONNECTED -> "connected"
            SyncState.CONNECTING -> "connecting"
            SyncState.DISCONNECTED -> "disconnected"
            SyncState.UNMATCHED -> "unmatched"
            SyncState.UNVERIFIED -> "unverified"
            SyncState.INCOMPATIBLE -> "incompatible"
            else -> "unknown($state)"
        }

    private fun describeV3Error(reason: PairingV3UiError): String =
        when (reason) {
            PairingV3UiError.NOT_ACCEPTING ->
                "The device is not accepting pairing right now; open CrossPaste on it and retry."

            PairingV3UiError.INCORRECT_PIN ->
                "Incorrect PIN. The device now shows a new PIN; enter that one."

            PairingV3UiError.PIN_EXPIRED ->
                "The PIN expired. The device now shows a new PIN; enter that one."

            PairingV3UiError.REJECTED ->
                "Pairing was rejected on the device."

            PairingV3UiError.CANCELLED ->
                "Pairing was cancelled."

            PairingV3UiError.SESSION_EXPIRED ->
                "The pairing session expired; start pairing again."

            PairingV3UiError.RATE_LIMITED ->
                "Too many attempts; wait a moment and try again."

            PairingV3UiError.CAPACITY_EXCEEDED ->
                "The device has too many pairing sessions in progress; try again shortly."

            PairingV3UiError.UNSUPPORTED ->
                "The device does not support this pairing protocol."

            PairingV3UiError.IDENTITY_INVALID ->
                "Identity verification failed; aborting pairing (possible interference)."

            PairingV3UiError.NETWORK_FAILURE ->
                "Network failure while pairing; check the connection and try again."

            PairingV3UiError.FAILED ->
                "Pairing failed; start pairing again."
        }
}
