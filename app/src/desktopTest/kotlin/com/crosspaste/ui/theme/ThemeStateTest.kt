package com.crosspaste.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeStateTest {

    @Test
    fun `isCurrentThemeDark follows system dark when followSystem is true`() {
        val state =
            ThemeState.createThemeState(
                themeColor = CrossPasteColor,
                isFollowSystem = true,
                isUserInDark = false,
                isSystemInDark = true,
            )
        assertTrue(state.isCurrentThemeDark, "Should follow system dark mode")
    }

    @Test
    fun `isCurrentThemeDark follows user preference when followSystem is false`() {
        val state =
            ThemeState.createThemeState(
                themeColor = CrossPasteColor,
                isFollowSystem = false,
                isUserInDark = true,
                isSystemInDark = false,
            )
        assertTrue(state.isCurrentThemeDark, "Should follow user dark mode preference")
    }

    @Test
    fun `isCurrentThemeDark ignores system when followSystem is false`() {
        val state =
            ThemeState.createThemeState(
                themeColor = CrossPasteColor,
                isFollowSystem = false,
                isUserInDark = false,
                isSystemInDark = true,
            )
        assertFalse(state.isCurrentThemeDark, "Should ignore system dark when not following system")
    }

    @Test
    fun `isCurrentThemeDark ignores user preference when followSystem is true`() {
        val state =
            ThemeState.createThemeState(
                themeColor = CrossPasteColor,
                isFollowSystem = true,
                isUserInDark = true,
                isSystemInDark = false,
            )
        assertFalse(state.isCurrentThemeDark, "Should ignore user preference when following system")
    }

    @Test
    fun `createThemeState selects dark color scheme when dark`() {
        val state =
            ThemeState.createThemeState(
                themeColor = CrossPasteColor,
                isFollowSystem = false,
                isUserInDark = true,
                isSystemInDark = false,
            )
        assertEquals(CrossPasteColor.darkColorScheme, state.colorScheme)
    }

    @Test
    fun `createThemeState selects light color scheme when not dark`() {
        val state =
            ThemeState.createThemeState(
                themeColor = CrossPasteColor,
                isFollowSystem = false,
                isUserInDark = false,
                isSystemInDark = false,
            )
        assertEquals(CrossPasteColor.lightColorScheme, state.colorScheme)
    }

    @Test
    fun `createThemeState preserves all input parameters`() {
        val state =
            ThemeState.createThemeState(
                themeColor = CrossPasteColor,
                isFollowSystem = true,
                isUserInDark = true,
                isSystemInDark = false,
            )
        assertEquals(CrossPasteColor, state.themeColor)
        assertTrue(state.isFollowSystem)
        assertTrue(state.isUserInDark)
        assertFalse(state.isSystemInDark)
    }
}
