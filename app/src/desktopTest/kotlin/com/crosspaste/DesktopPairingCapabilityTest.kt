package com.crosspaste

import com.crosspaste.app.AppEnv
import com.crosspaste.config.developmentPairingV3InteropEnabled
import com.crosspaste.pairing.v3.PakeException
import com.crosspaste.pairing.v3.TestPakeProvider
import com.crosspaste.pairing.v3.UnavailablePakeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class DesktopPairingCapabilityTest {

    private val loadedProvider = TestPakeProvider()

    private val failingLoader: () -> Nothing = {
        throw PakeException("injected libcrypto load failure")
    }

    private val forbiddenLoader: () -> Nothing = {
        fail("PAKE provider must not be loaded on this path")
    }

    @Test
    fun developmentConfigurationDefaultsToV3AndSupportsExplicitV2Override() {
        assertTrue(developmentPairingV3InteropEnabled(null))
        assertFalse(developmentPairingV3InteropEnabled("false"))
        assertFalse(developmentPairingV3InteropEnabled("invalid"))
    }

    @Test
    fun developmentInteropOptInEnablesV3() {
        val backend =
            resolveDesktopPairingBackend(
                appEnv = AppEnv.DEVELOPMENT,
                developmentV3InteropEnabled = true,
                loadPakeProvider = { loadedProvider },
            )

        assertEquals(3, backend.capabilityFlag.advertisedPairingVersion)
        assertTrue(backend.capabilityFlag.isPairingV3Enabled)
        assertSame(loadedProvider, backend.pakeProvider)
    }

    @Test
    fun developmentBackendLoadFailureFallsBackToAdvertisingV2() {
        // Capability and provider must stay consistent: a broken libcrypto
        // means we advertise v2 so peers negotiate v2 instead of selecting a
        // V3_PIN flow that is doomed to fail at the PAKE provider.
        val backend =
            resolveDesktopPairingBackend(
                appEnv = AppEnv.DEVELOPMENT,
                developmentV3InteropEnabled = true,
                loadPakeProvider = failingLoader,
            )

        assertEquals(2, backend.capabilityFlag.advertisedPairingVersion)
        assertFalse(backend.capabilityFlag.isPairingV3Enabled)
        assertSame(UnavailablePakeProvider, backend.pakeProvider)
    }

    @Test
    fun developmentInteropOptOutUsesV2WithoutTouchingTheBackend() {
        val backend =
            resolveDesktopPairingBackend(
                appEnv = AppEnv.DEVELOPMENT,
                developmentV3InteropEnabled = false,
                loadPakeProvider = forbiddenLoader,
            )

        assertEquals(2, backend.capabilityFlag.advertisedPairingVersion)
        assertFalse(backend.capabilityFlag.isPairingV3Enabled)
        assertSame(UnavailablePakeProvider, backend.pakeProvider)
    }

    @Test
    fun nonDevelopmentBuildsIgnoreInteropOptIn() {
        listOf(AppEnv.PRODUCTION, AppEnv.BETA, AppEnv.TEST).forEach { appEnv ->
            val backend =
                resolveDesktopPairingBackend(
                    appEnv = appEnv,
                    developmentV3InteropEnabled = true,
                    loadPakeProvider = forbiddenLoader,
                )

            assertEquals(2, backend.capabilityFlag.advertisedPairingVersion)
            assertFalse(backend.capabilityFlag.isPairingV3Enabled)
            assertSame(UnavailablePakeProvider, backend.pakeProvider)
        }
    }
}
