package com.crosspaste

import com.crosspaste.app.AppEnv
import com.crosspaste.config.developmentPairingV3InteropEnabled
import com.crosspaste.pairing.v3.LibCryptoResolution
import com.crosspaste.pairing.v3.PakeException
import com.crosspaste.pairing.v3.TestPakeProvider
import com.crosspaste.pairing.v3.UnavailablePakeProvider
import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toPath
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

    @Test
    fun bundledBuildsProbeTheBackendButStayClampedToV2() {
        listOf(AppEnv.PRODUCTION, AppEnv.BETA).forEach { appEnv ->
            var probes = 0
            val backend =
                resolveDesktopPairingBackend(
                    appEnv = appEnv,
                    developmentV3InteropEnabled = false,
                    libcryptoResolution =
                        LibCryptoResolution.Bundled("/opt/crosspaste/runtime/lib/libcrypto.so.3"),
                    loadPakeProvider = {
                        probes++
                        loadedProvider
                    },
                )

            // The probe exists for packaging diagnostics only: even a working
            // backend must not advertise v3 before the rollout PR (#4667).
            assertEquals(1, probes)
            assertEquals(2, backend.capabilityFlag.advertisedPairingVersion)
            assertFalse(backend.capabilityFlag.isPairingV3Enabled)
            assertSame(UnavailablePakeProvider, backend.pakeProvider)
        }
    }

    @Test
    fun bundledProbeFailureIsToleratedAndStaysClamped() {
        val backend =
            resolveDesktopPairingBackend(
                appEnv = AppEnv.PRODUCTION,
                developmentV3InteropEnabled = false,
                libcryptoResolution = LibCryptoResolution.Bundled("/missing/libcrypto.so.3"),
                loadPakeProvider = failingLoader,
            )

        assertEquals(2, backend.capabilityFlag.advertisedPairingVersion)
        assertSame(UnavailablePakeProvider, backend.pakeProvider)
    }

    @Test
    fun environmentResolutionDoesNotProbeOutsideDevelopmentInterop() {
        listOf(AppEnv.PRODUCTION, AppEnv.BETA, AppEnv.TEST).forEach { appEnv ->
            val backend =
                resolveDesktopPairingBackend(
                    appEnv = appEnv,
                    developmentV3InteropEnabled = false,
                    libcryptoResolution = LibCryptoResolution.Environment,
                    loadPakeProvider = forbiddenLoader,
                )

            assertEquals(2, backend.capabilityFlag.advertisedPairingVersion)
            assertSame(UnavailablePakeProvider, backend.pakeProvider)
        }
    }

    @Test
    fun bundledResolutionResolvesPerPlatformUnderTheJvmLibDirectory() {
        val appPathProvider =
            mockk<AppPathProvider> {
                every { pasteAppExePath } returns "/opt/crosspaste/runtime/lib".toPath()
            }

        listOf(
            Platform(Platform.MACOS, "aarch64", 64, "15") to "libcrypto.3.dylib",
            Platform(Platform.WINDOWS, "x86_64", 64, "11") to "libcrypto-3-x64.dll",
            Platform(Platform.LINUX, "x86_64", 64, "6") to "libcrypto.so.3",
        ).forEach { (platform, libName) ->
            listOf(AppEnv.PRODUCTION, AppEnv.BETA).forEach { appEnv ->
                assertEquals(
                    LibCryptoResolution.Bundled("/opt/crosspaste/runtime/lib/$libName"),
                    desktopLibcryptoResolution(appEnv, platform, appPathProvider),
                )
            }
        }
    }

    @Test
    fun developmentAndTestBuildsResolveFromTheEnvironment() {
        // Unstubbed mock: resolving any path in DEVELOPMENT/TEST would throw.
        val untouchedPathProvider = mockk<AppPathProvider>()
        val platform = Platform(Platform.MACOS, "aarch64", 64, "15")

        listOf(AppEnv.DEVELOPMENT, AppEnv.TEST).forEach { appEnv ->
            assertEquals(
                LibCryptoResolution.Environment,
                desktopLibcryptoResolution(appEnv, platform, untouchedPathProvider),
            )
        }
    }
}
