package com.crosspaste.ui

import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class PastePanelMenuPlacementTest {

    private val screen = Rectangle(0, 25, 1440, 875)
    private val gap = 4
    private val menuWidth = 180
    private val menuHeight = 200

    private fun button(
        x: Int,
        y: Int,
        width: Int = 48,
        height: Int = 48,
    ) = Rectangle(x, y, width, height)

    @Test
    fun `menu opens to the left of the button when right exceeds screen`() {
        val position =
            pastePanelMenuPositionBeside(
                anchor = button(1376, 200),
                menuWidth = menuWidth,
                menuHeight = menuHeight,
                usableScreen = screen,
                gap = gap,
            )
        // 1376 - 4 - 180 = 1192
        assertEquals(Point(1192, 200), position)
    }

    @Test
    fun `menu opens to the right of the button when space permits`() {
        val position =
            pastePanelMenuPositionBeside(
                anchor = button(100, 200),
                menuWidth = menuWidth,
                menuHeight = menuHeight,
                usableScreen = screen,
                gap = gap,
            )
        // 100 + 48 + 4 = 152
        assertEquals(Point(152, 200), position)
    }

    @Test
    fun `menu is pushed up so it stays on screen near the bottom`() {
        val position =
            pastePanelMenuPositionBeside(
                anchor = button(1376, 800),
                menuWidth = menuWidth,
                menuHeight = menuHeight,
                usableScreen = screen,
                gap = gap,
            )
        // screen bottom = 25 + 875 = 900. Max Y = 900 - 200 = 700.
        assertEquals(700, position.y)
    }

    @Test
    fun `menu is pushed down below the top inset near the top`() {
        val position =
            pastePanelMenuPositionBeside(
                anchor = button(1376, 0),
                menuWidth = menuWidth,
                menuHeight = menuHeight,
                usableScreen = screen,
                gap = gap,
            )
        // screen top = 25
        assertEquals(25, position.y)
    }

    @Test
    fun `menu stays on secondary monitor with positive offset`() {
        val secondaryScreen = Rectangle(1920, 0, 1920, 1080)
        // Button on right edge of secondary monitor: 1920 + 1920 - 48 - 16 = 3776
        val position =
            pastePanelMenuPositionBeside(
                anchor = button(3776, 300),
                menuWidth = menuWidth,
                menuHeight = menuHeight,
                usableScreen = secondaryScreen,
                gap = gap,
            )
        // 3776 - 4 - 180 = 3592 (stays on secondary screen)
        assertEquals(Point(3592, 300), position)
    }

    @Test
    fun `menu stays on secondary monitor with negative offset`() {
        val secondaryScreen = Rectangle(-1920, 0, 1920, 1080)
        // Button on right edge of left secondary monitor: -48 - 16 = -64
        val position =
            pastePanelMenuPositionBeside(
                anchor = button(-64, 300),
                menuWidth = menuWidth,
                menuHeight = menuHeight,
                usableScreen = secondaryScreen,
                gap = gap,
            )
        // -64 - 4 - 180 = -248 (stays on left secondary screen)
        assertEquals(Point(-248, 300), position)
    }

    @Test
    fun `menu is clamped when neither side fits completely`() {
        val narrowScreen = Rectangle(0, 0, 150, 600)
        val position =
            pastePanelMenuPositionBeside(
                anchor = button(50, 100),
                menuWidth = menuWidth,
                menuHeight = menuHeight,
                usableScreen = narrowScreen,
                gap = gap,
            )
        assertEquals(0, position.x)
    }
}
