package com.crosspaste.app

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.crosspaste.app.DesktopAppSize.Companion.clampMainWindowSize
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopAppSizeTest {

    private val designSize = DpSize(width = 600.dp, height = 700.dp)

    @Test
    fun `design size is kept when the usable screen is large enough`() {
        val usable = DpSize(width = 1920.dp, height = 1080.dp)

        assertEquals(designSize, clampMainWindowSize(designSize, usable))
    }

    @Test
    fun `height is clamped when the usable screen is too short`() {
        // e.g. a 1366x768 laptop at 125% scale -> ~614dp usable height
        val usable = DpSize(width = 1092.dp, height = 614.dp)

        val clamped = clampMainWindowSize(designSize, usable)

        assertEquals(designSize.width, clamped.width)
        assertEquals(614.dp - 32.dp, clamped.height)
    }

    @Test
    fun `both dimensions are clamped when the usable screen is smaller than the design size`() {
        // e.g. XWayland misreporting a 2.5x scale on a 1440p screen (#4759)
        val usable = DpSize(width = 1024.dp, height = 576.dp)
        val small = DpSize(width = 1100.dp, height = 700.dp)

        val clamped = clampMainWindowSize(small, usable)

        assertEquals(1024.dp - 32.dp, clamped.width)
        assertEquals(576.dp - 32.dp, clamped.height)
    }

    @Test
    fun `implausibly small usable sizes are ignored`() {
        val usable = DpSize(width = 100.dp, height = 50.dp)

        assertEquals(designSize, clampMainWindowSize(designSize, usable))
    }
}
