package com.crosspaste.ui.contextmenu

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression coverage for the submenu position math. Off-by-one in the flip-left or flip-up
 * branches will silently render the submenu in the wrong corner or with hover backgrounds that
 * don't line up across the popup boundary — the whole point of canceling [menuTopPadding] /
 * [menuBottomPadding] is to make that hover band continuous.
 */
class NestedDropdownPositionProviderTest {

    private val windowMarginPx = 8
    private val menuTopPaddingPx = 12
    private val menuBottomPaddingPx = 12

    private val provider =
        NestedDropdownPositionProvider(
            windowMarginPx = windowMarginPx,
            menuTopPaddingPx = menuTopPaddingPx,
            menuBottomPaddingPx = menuBottomPaddingPx,
        )

    @Test
    fun `places popup to the right of the anchor when there is room`() {
        val anchor = IntRect(left = 400, top = 300, right = 500, bottom = 348)
        val popup = IntSize(width = 200, height = 300)

        val offset =
            provider.calculatePosition(
                anchorBounds = anchor,
                windowSize = IntSize(1000, 800),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popup,
            )

        assertEquals(
            IntOffset(anchor.right, anchor.top - menuTopPaddingPx),
            offset,
        )
    }

    @Test
    fun `flips to the left when the right edge would overflow the window`() {
        val anchor = IntRect(left = 400, top = 100, right = 500, bottom = 148)
        val popup = IntSize(width = 300, height = 200)

        val offset =
            provider.calculatePosition(
                anchorBounds = anchor,
                windowSize = IntSize(600, 800),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popup,
            )

        assertEquals(
            IntOffset(anchor.left - popup.width, anchor.top - menuTopPaddingPx),
            offset,
        )
    }

    @Test
    fun `flips upward when the bottom edge would overflow the window`() {
        val anchor = IntRect(left = 200, top = 600, right = 300, bottom = 648)
        val popup = IntSize(width = 200, height = 300)

        val offset =
            provider.calculatePosition(
                anchorBounds = anchor,
                windowSize = IntSize(1000, 800),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popup,
            )

        assertEquals(
            IntOffset(anchor.right, anchor.bottom - popup.height + menuBottomPaddingPx),
            offset,
        )
    }

    @Test
    fun `flips both axes when the popup would overflow both edges`() {
        val anchor = IntRect(left = 400, top = 600, right = 500, bottom = 648)
        val popup = IntSize(width = 300, height = 300)

        val offset =
            provider.calculatePosition(
                anchorBounds = anchor,
                windowSize = IntSize(600, 800),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popup,
            )

        assertEquals(
            IntOffset(
                anchor.left - popup.width,
                anchor.bottom - popup.height + menuBottomPaddingPx,
            ),
            offset,
        )
    }

    @Test
    fun `clamps to window margin when neither side of the anchor fits`() {
        // Popup wider/taller than the window — both flips still leave the offset negative,
        // so the window-margin clamp at the end of calculatePosition must save us.
        val anchor = IntRect(left = 10, top = 10, right = 20, bottom = 20)
        val popup = IntSize(width = 200, height = 200)

        val offset =
            provider.calculatePosition(
                anchorBounds = anchor,
                windowSize = IntSize(100, 100),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popup,
            )

        assertEquals(IntOffset(windowMarginPx, windowMarginPx), offset)
    }

    @Test
    fun `ignores layout direction so submenu always opens to the right when room exists`() {
        val anchor = IntRect(left = 400, top = 300, right = 500, bottom = 348)
        val popup = IntSize(width = 200, height = 300)
        val window = IntSize(1000, 800)

        val ltr =
            provider.calculatePosition(
                anchor,
                window,
                LayoutDirection.Ltr,
                popup,
            )
        val rtl =
            provider.calculatePosition(
                anchor,
                window,
                LayoutDirection.Rtl,
                popup,
            )

        assertEquals(ltr, rtl)
    }

    @Test
    fun `handles anchor wider than popup without changing the math`() {
        val anchor = IntRect(left = 100, top = 100, right = 700, bottom = 148)
        val popup = IntSize(width = 200, height = 300)

        val offset =
            provider.calculatePosition(
                anchorBounds = anchor,
                windowSize = IntSize(1000, 800),
                layoutDirection = LayoutDirection.Ltr,
                popupContentSize = popup,
            )

        assertEquals(
            IntOffset(anchor.right, anchor.top - menuTopPaddingPx),
            offset,
        )
    }
}
