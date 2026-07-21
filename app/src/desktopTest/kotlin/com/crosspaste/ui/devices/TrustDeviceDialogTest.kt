package com.crosspaste.ui.devices

import com.crosspaste.sync.PairingCredentialType
import com.crosspaste.sync.QrBearerToken
import com.crosspaste.sync.SasCode
import com.crosspaste.sync.SyncManager
import com.crosspaste.sync.SyncTestFixtures.createUnverifiedSyncRuntimeInfo
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

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
}
