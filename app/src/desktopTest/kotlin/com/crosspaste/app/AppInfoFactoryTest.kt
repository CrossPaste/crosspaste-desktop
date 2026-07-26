package com.crosspaste.app

import com.crosspaste.config.AppMetadataRepository
import com.crosspaste.pairing.v3.PairingCapabilityFlag
import io.mockk.every
import io.mockk.mockk
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class AppInfoFactoryTest {

    @Test
    fun createAppInfo_usesRuntimePairingCapability() {
        val appMetadataRepository = mockk<AppMetadataRepository>()
        every { appMetadataRepository.appInstanceId } returns "test-instance"

        val appInfo =
            DesktopAppInfoFactory(
                appMetadataRepository,
                PairingCapabilityFlag(advertisedPairingVersion = 3),
            ).createAppInfo()

        assertEquals(3, appInfo.pairingVersion)
    }

    @Test
    fun testAppVersion() {
        val version =
            DesktopAppInfoFactory.getVersion(
                AppEnv.PRODUCTION,
                properties =
                    run {
                        val properties = Properties()
                        properties.setProperty("version", "1.0.0")
                        properties
                    },
            )

        assertEquals("1.0.0", version)

        val versionWithBeta =
            DesktopAppInfoFactory.getVersion(
                AppEnv.PRODUCTION,
                properties =
                    run {
                        val properties = Properties()
                        properties.setProperty("version", "1.0.0")
                        properties.setProperty("prerelease", "beta.0")
                        properties
                    },
            )

        assertEquals("1.0.0-beta.0", versionWithBeta)

        val versionWithBeta2 =
            DesktopAppInfoFactory.getVersion(
                AppEnv.PRODUCTION,
                properties =
                    run {
                        val properties = Properties()
                        properties.setProperty("version", "1.0.0")
                        properties.setProperty("prerelease", "beta.2")
                        properties
                    },
            )

        assertEquals("1.0.0-beta.2", versionWithBeta2)

        val versionWithBetaNull =
            DesktopAppInfoFactory.getVersion(
                AppEnv.PRODUCTION,
                properties =
                    run {
                        val properties = Properties()
                        properties.setProperty("version", "99.99.99")
                        properties
                    },
            )

        assertEquals("99.99.99", versionWithBetaNull)

        val unknownVersion =
            DesktopAppInfoFactory.getVersion(
                AppEnv.PRODUCTION,
                properties =
                    run {
                        null
                    },
            )

        assertEquals("Unknown", unknownVersion)
    }
}
