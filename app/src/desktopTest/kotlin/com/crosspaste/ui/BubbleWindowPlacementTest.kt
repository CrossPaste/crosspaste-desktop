package com.crosspaste.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class BubbleWindowPlacementTest {
    @Test
    fun `bubble centers on the item when it fits`() {
        assertEquals(360.dp, bubblePositionX(100.dp, 1280.dp, 480.dp, 500.dp))
    }

    @Test
    fun `bubble clamps to both edges of the search window`() {
        assertEquals(100.dp, bubblePositionX(100.dp, 1280.dp, 480.dp, 0.dp))
        assertEquals(900.dp, bubblePositionX(100.dp, 1280.dp, 480.dp, 1280.dp))
    }

    @Test
    fun `bubble centers in search window without an item`() {
        assertEquals(500.dp, bubblePositionX(100.dp, 1280.dp, 480.dp, null))
    }

    @Test
    fun `oversized bubble aligns left for any item position`() {
        // 480dp at 275 percent scaling is wider than a 1280-unit search window.
        for (center in listOf(null, 0.dp, 640.dp, 1280.dp)) {
            assertEquals(100.dp, bubblePositionX(100.dp, 1280.dp, 1320.dp, center))
            assertEquals((-1280).dp, bubblePositionX((-1280).dp, 1280.dp, 1320.dp, center))
        }
    }

    @Test
    fun `equal or zero available width keeps the clamp range valid`() {
        assertEquals(100.dp, bubblePositionX(100.dp, 480.dp, 480.dp, null))
        assertEquals(100.dp, bubblePositionX(100.dp, 0.dp, 480.dp, null))
    }
}
