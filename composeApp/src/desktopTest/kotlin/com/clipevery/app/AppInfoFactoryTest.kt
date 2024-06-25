package com.clipevery.app

import java.io.IOException
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class AppInfoFactoryTest {

    @Test
    fun testAppVersion() {
        val version =
            DesktopAppInfoFactory.getVersion {
                val properties = Properties()
                properties.setProperty("version", "1.0.0")
                properties
            }

        assertEquals("1.0.0", version)

        val versionWithBeta =
            DesktopAppInfoFactory.getVersion {
                val properties = Properties()
                properties.setProperty("version", "1.0.0")
                properties.setProperty("beta", "0")
                properties
            }

        assertEquals("1.0.0-beta", versionWithBeta)

        val versionWithBeta2 =
            DesktopAppInfoFactory.getVersion {
                val properties = Properties()
                properties.setProperty("version", "1.0.0")
                properties.setProperty("beta", "2")
                properties
            }

        assertEquals("1.0.0-beta2", versionWithBeta2)

        val versionWithBetaNull =
            DesktopAppInfoFactory.getVersion {
                val properties = Properties()
                properties.setProperty("version", "99.99.99")
                properties
            }

        assertEquals("99.99.99", versionWithBetaNull)

        val unknownVersion =
            DesktopAppInfoFactory.getVersion {
                throw IOException("Failed to load version")
            }

        assertEquals("Unknown", unknownVersion)
    }
}
