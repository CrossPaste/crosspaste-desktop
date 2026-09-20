package com.crosspaste.app

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.crosspaste.ui.theme.AppUISize.medium
import kotlin.test.Test
import kotlin.test.assertEquals

class PastePanelPlacementTest {

    private val screen = DpRect(left = 0.dp, top = 25.dp, right = 1440.dp, bottom = 900.dp)
    private val panel = DpSize(300.dp, 420.dp)

    private fun button(
        x: Int,
        y: Int,
    ) = DpRect(origin = DpOffset(x.dp, y.dp), size = DpSize(48.dp, 48.dp))

    @Test
    fun `panel opens to the left of the button, top aligned`() {
        val position = pastePanelPositionBeside(button(1376, 200), panel, screen, medium)
        assertEquals(DpOffset(1376.dp - medium - 300.dp, 200.dp), position)
    }

    @Test
    fun `panel opens to the right when the left would leave the screen`() {
        val position = pastePanelPositionBeside(button(100, 200), panel, screen, medium)
        assertEquals(DpOffset(148.dp + medium, 200.dp), position)
    }

    @Test
    fun `panel is pushed up so it stays on screen near the bottom`() {
        val position = pastePanelPositionBeside(button(1376, 800), panel, screen, medium)
        assertEquals(480.dp, position.y)
    }

    @Test
    fun `panel is pushed down below the menu bar near the top`() {
        val position = pastePanelPositionBeside(button(1376, 0), panel, screen, medium)
        assertEquals(25.dp, position.y)
    }
}
