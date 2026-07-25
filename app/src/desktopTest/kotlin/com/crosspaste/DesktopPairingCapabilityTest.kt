package com.crosspaste

import com.crosspaste.app.AppEnv
import com.crosspaste.pairing.v3.PairingCapabilityFlag
import com.crosspaste.pairing.v3.Spake2PakeProvider
import com.crosspaste.pairing.v3.UnavailablePakeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopPairingCapabilityTest {

    @Test
    fun developmentInteropOptInEnablesV3() {
        val capability =
            createDesktopPairingCapabilityFlag(
                appEnv = AppEnv.DEVELOPMENT,
                developmentV3InteropEnabled = true,
            )

        assertEquals(3, capability.advertisedPairingVersion)
        assertTrue(capability.isPairingV3Enabled)
        assertIs<Spake2PakeProvider>(
            createDesktopPakeProvider(AppEnv.DEVELOPMENT, capability),
        )
    }

    @Test
    fun developmentDefaultsToV2() {
        val capability =
            createDesktopPairingCapabilityFlag(
                appEnv = AppEnv.DEVELOPMENT,
                developmentV3InteropEnabled = false,
            )

        assertEquals(2, capability.advertisedPairingVersion)
        assertFalse(capability.isPairingV3Enabled)
        assertSame(
            UnavailablePakeProvider,
            createDesktopPakeProvider(AppEnv.DEVELOPMENT, capability),
        )
    }

    @Test
    fun nonDevelopmentBuildsIgnoreInteropOptIn() {
        listOf(AppEnv.PRODUCTION, AppEnv.BETA, AppEnv.TEST).forEach { appEnv ->
            val capability =
                createDesktopPairingCapabilityFlag(
                    appEnv = appEnv,
                    developmentV3InteropEnabled = true,
                )

            assertEquals(2, capability.advertisedPairingVersion)
            assertFalse(capability.isPairingV3Enabled)
            assertSame(
                UnavailablePakeProvider,
                createDesktopPakeProvider(appEnv, capability),
            )
        }
    }

    @Test
    fun providerStillFailsClosedForMisinjectedProductionCapability() {
        val capability = PairingCapabilityFlag(advertisedPairingVersion = 3)

        assertSame(
            UnavailablePakeProvider,
            createDesktopPakeProvider(AppEnv.PRODUCTION, capability),
        )
    }
}
