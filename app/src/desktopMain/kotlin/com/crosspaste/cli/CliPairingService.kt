package com.crosspaste.cli

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
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
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
        /**
         * v3 only: the PIN was verified but the commit round-trip failed, so
         * the next submit must drive retryCommit (via recover) instead of
         * submitPin — a second submitPin against a COMMITTING session fails
         * with PAIRING_INVALID_STATE and would force a full re-initiate.
         */
        val commitPending: Boolean = false,
    )

    private val sessions = ConcurrentHashMap<String, PairSession>()

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

    suspend fun initiate(appInstanceId: String): InitiateOutcome {
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
        val session = sessions[sessionId] ?: return SubmitOutcome.SessionNotFound
        if (!SIX_DIGITS.matches(code)) {
            return SubmitOutcome.InvalidCode
        }
        return when (session.credentialType) {
            PairingCredentialType.SAS_CODE -> submitSas(session, code)
            PairingCredentialType.V3_PIN -> submitV3(session, code)
            PairingCredentialType.QR_BEARER_TOKEN ->
                // Unreachable: initiate refuses QR peers
                SubmitOutcome.SessionNotFound
        }
    }

    fun cancel(sessionId: String): Boolean {
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
    ): SubmitOutcome =
        // Symmetric to the SAS TRUST_TIMEOUT: the route must always answer
        // within the CLI's own budget even if a v3 round-trip hangs
        withTimeoutOrNull(TRUST_TIMEOUT) { submitV3Steps(session, code) }
            ?: SubmitOutcome.Completed(
                CliPairSubmitResultDto(
                    paired = false,
                    retryable = true,
                    message = "Pairing timed out; check the connection and try again.",
                ),
            )

    private suspend fun submitV3Steps(
        session: PairSession,
        code: String,
    ): SubmitOutcome {
        val v3SessionId = checkNotNull(session.v3SessionId)
        var result =
            if (session.commitPending) {
                // The previous attempt left the v3 session COMMITTING; recover
                // drives retryCommit — a submitPin here would fail with
                // PAIRING_INVALID_STATE and kill the session
                pairingV3UiController.recover(v3SessionId)
            } else {
                pairingV3UiController.submitPin(v3SessionId, V3Pin(code))
            }
        if (result is PairingV3UiResult.SessionReady) {
            // recover found the session back on a fresh offer instead of
            // COMMITTING; the code the user just typed applies to that offer
            setCommitPending(session, false)
            result = pairingV3UiController.submitPin(v3SessionId, V3Pin(code))
        }
        if (result is PairingV3UiResult.Error && result.recovery == PairingV3Recovery.RETRY_COMMIT) {
            // The PIN was verified but the commit round-trip failed; retryCommit
            // can finish the pairing without asking for a new PIN.
            result = pairingV3UiController.recover(v3SessionId)
        }
        return when (result) {
            PairingV3UiResult.Paired -> {
                sessions.remove(session.sessionId)
                // Converge the sync state machine to CONNECTED now that the
                // peer key is persisted (same as the dialog's completePairing)
                syncManager.refresh(listOf(session.appInstanceId))
                SubmitOutcome.Completed(
                    CliPairSubmitResultDto(
                        paired = true,
                        retryable = false,
                        message = "Paired with ${session.deviceName}.",
                    ),
                )
            }

            is PairingV3UiResult.Error -> {
                val retryable =
                    when (result.reason) {
                        PairingV3UiError.INCORRECT_PIN,
                        PairingV3UiError.PIN_EXPIRED,
                        PairingV3UiError.RATE_LIMITED,
                        PairingV3UiError.NETWORK_FAILURE,
                        -> true

                        else -> false
                    }
                setCommitPending(session, retryable && result.recovery == PairingV3Recovery.RETRY_COMMIT)
                if (retryable && result.recovery == PairingV3Recovery.REFRESH_OFFER) {
                    // The failed generation is dead; fetch the next offer so the
                    // retry matches the (possibly rotated) PIN on the peer.
                    pairingV3UiController.recover(v3SessionId)
                }
                if (!retryable) {
                    sessions.remove(session.sessionId)
                }
                SubmitOutcome.Completed(
                    CliPairSubmitResultDto(
                        paired = false,
                        retryable = retryable,
                        message = describeV3Error(result.reason),
                    ),
                )
            }

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
    // from submit entry and may be stale after an earlier update in the same call
    private fun setCommitPending(
        session: PairSession,
        commitPending: Boolean,
    ) {
        sessions.computeIfPresent(session.sessionId) { _, current ->
            current.copy(commitPending = commitPending)
        }
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
