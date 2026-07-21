package com.crosspaste.ui.devices

import com.crosspaste.sync.PairingCredentialRefreshResult
import com.crosspaste.sync.PairingCredentialType
import com.crosspaste.sync.QrBearerToken
import com.crosspaste.sync.SasCode
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.SyncTestFixtures.createUnverifiedSyncRuntimeInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class TrustDeviceDialogTest {

    private val tokens = mutableListOf("1", "2", "3", "4", "5", "6")

    @Test
    fun confirmToken_sasPeer_routesBySessionCredentialType() {
        val syncManager = mockk<SyncManager>(relaxed = true)
        val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

        confirmToken(tokens, 6, {}, syncManager, syncRuntimeInfo, PairingCredentialType.SAS_CODE)

        verify {
            syncManager.trustBySasCode(
                syncRuntimeInfo.appInstanceId,
                SasCode(123456),
                any(),
            )
        }
        verify(exactly = 0) {
            syncManager.trustByBearerToken(any(), any(), any())
        }
    }

    @Test
    fun confirmToken_legacyPeer_routesBySessionCredentialType() {
        val syncManager = mockk<SyncManager>(relaxed = true)
        val syncRuntimeInfo = createUnverifiedSyncRuntimeInfo()

        confirmToken(tokens, 6, {}, syncManager, syncRuntimeInfo, PairingCredentialType.QR_BEARER_TOKEN)

        verify {
            syncManager.trustByBearerToken(
                syncRuntimeInfo.appInstanceId,
                QrBearerToken(123456),
                any(),
            )
        }
        verify(exactly = 0) {
            syncManager.trustBySasCode(any(), any(), any())
        }
    }

    @Test
    fun refreshPairingCredentialTypeUntilKnown_retriesAfterEachCompletedFailure() =
        runTest {
            val syncManager = mockk<SyncManager>(relaxed = true)
            val appInstanceId = "test-app"
            val delays = mutableListOf<kotlin.time.Duration>()
            coEvery { syncManager.refreshPairingCredentialType(appInstanceId) } returnsMany
                listOf(
                    PairingCredentialRefreshResult.RetryableFailure,
                    PairingCredentialRefreshResult.RetryableFailure,
                    PairingCredentialRefreshResult.Resolved(PairingCredentialType.SAS_CODE),
                )

            val result =
                refreshPairingCredentialTypeUntilKnown(
                    syncManager = syncManager,
                    appInstanceId = appInstanceId,
                    initialBackoff = 1.seconds,
                    maxBackoff = 2.seconds,
                    delayAction = { delays += it },
                )

            coVerify(exactly = 3) { syncManager.refreshPairingCredentialType(appInstanceId) }
            assertEquals(PairingCredentialRefreshResult.Resolved(PairingCredentialType.SAS_CODE), result)
            assertEquals(listOf(1.seconds, 2.seconds), delays)
        }

    @Test
    fun refreshPairingCredentialTypeUntilKnown_identityMismatchStopsWithoutFallback() =
        runTest {
            val syncManager = mockk<SyncManager>(relaxed = true)
            val appInstanceId = "test-app"
            val delays = mutableListOf<kotlin.time.Duration>()
            coEvery { syncManager.refreshPairingCredentialType(appInstanceId) } returns
                PairingCredentialRefreshResult.IdentityMismatch

            val result =
                refreshPairingCredentialTypeUntilKnown(
                    syncManager = syncManager,
                    appInstanceId = appInstanceId,
                    delayAction = { delays += it },
                )

            coVerify(exactly = 1) { syncManager.refreshPairingCredentialType(appInstanceId) }
            assertEquals(PairingCredentialRefreshResult.IdentityMismatch, result)
            assertEquals(emptyList(), delays)
        }
}
