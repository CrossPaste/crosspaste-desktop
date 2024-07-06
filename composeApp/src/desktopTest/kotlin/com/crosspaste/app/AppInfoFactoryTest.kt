package com.crosspaste.app

import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class AppInfoFactoryTest {

    @Test
    fun testAppVersion() {
        val version =
            DesktopAppInfoFactory.getVersion(
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
                properties =
                    run {
                        val properties = Properties()
                        properties.setProperty("version", "1.0.0")
                        properties.setProperty("beta", "0")
                        properties
                    },
            )

        assertEquals("1.0.0-beta", versionWithBeta)

        val versionWithBeta2 =
            DesktopAppInfoFactory.getVersion(
                properties =
                    run {
                        val properties = Properties()
                        properties.setProperty("version", "1.0.0")
                        properties.setProperty("beta", "2")
                        properties
                    },
            )

        assertEquals("1.0.0-beta2", versionWithBeta2)

        val versionWithBetaNull =
            DesktopAppInfoFactory.getVersion(
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
                properties =
                    run {
                        null
                    },
            )

        assertEquals("Unknown", unknownVersion)
    }
}
