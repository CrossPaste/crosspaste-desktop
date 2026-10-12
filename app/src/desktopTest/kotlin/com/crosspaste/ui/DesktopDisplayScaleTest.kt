package com.crosspaste.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopDisplayScaleTest {
    @Test
    fun plasmaFractionalDpiOverridesUnscaledAwt() {
        val dpi = DesktopDisplayScale.parseDpi("Xcursor.size:\t36\nXft.dpi:\t144\nXft.antialias:\t1\n")
        assertEquals(1.5f, DesktopDisplayScale.correctionFor(dpi, 1f))
    }

    @Test
    fun integerAwtScalingIsCompensatedWithoutDoubleScaling() {
        assertEquals(0.75f, DesktopDisplayScale.correctionFor(144f, 2f))
        assertEquals(1f, DesktopDisplayScale.correctionFor(192f, 2f))
        assertEquals(1f, DesktopDisplayScale.correctionFor(96f, 1f))
        assertEquals(1.25f, DesktopDisplayScale.correctionFor(120f, 1f))
    }

    @Test
    fun absentOrInvalidDpiLeavesRuntimeDensityAlone() {
        for (resource in listOf(
            "",
            "Xft.dpi: invalid",
            "Xft.dpi: NaN",
            "Xft.dpi: Infinity",
            "Xft.dpi: 0",
            "Xft.dpi: -96",
        )) {
            val dpi = DesktopDisplayScale.parseDpi(resource)
            assertNull(dpi)
            assertEquals(1f, DesktopDisplayScale.correctionFor(dpi, 2f))
        }
    }
}
