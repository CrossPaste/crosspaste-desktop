package com.crosspaste.ui.theme

import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopThemeDetectorTest {

    private fun createDetector(
        isFollowSystemTheme: Boolean = true,
        isDarkTheme: Boolean = false,
    ): Pair<DesktopThemeDetector, CommonConfigManager> {
        val appConfig = mockk<AppConfig>()
        every { appConfig.isFollowSystemTheme } returns isFollowSystemTheme
        every { appConfig.isDarkTheme } returns isDarkTheme

        val configManager = mockk<CommonConfigManager>()
        every { configManager.getCurrentConfig() } returns appConfig
        every { configManager.updateConfig(any<List<String>>(), any<List<Any>>()) } returns Unit

        val detector = DesktopThemeDetector(configManager)
        return detector to configManager
    }

    @Test
    fun `initial config follows persisted values`() {
        val (detector, _) =
            createDetector(
                isFollowSystemTheme = false,
                isDarkTheme = true,
            )

        val config = detector.themeConfig.value
        assertFalse(config.isFollowSystem)
        assertTrue(config.isUserInDark)
        assertEquals(CrossPasteColor, config.themeColor)
    }

    @Test
    fun `resolveIsDark uses system value when following system`() {
        val (detector, _) =
            createDetector(
                isFollowSystemTheme = true,
                isDarkTheme = false,
            )

        val config = detector.themeConfig.value
        assertFalse(config.resolveIsDark(isSystemInDark = false))
        assertTrue(config.resolveIsDark(isSystemInDark = true))
    }

    @Test
    fun `resolveIsDark ignores system value when not following system`() {
        val (detector, _) =
            createDetector(
                isFollowSystemTheme = false,
                isDarkTheme = false,
            )

        val config = detector.themeConfig.value
        assertFalse(config.resolveIsDark(isSystemInDark = true))
    }

    @Test
    fun `setThemeConfig persists to config manager`() {
        val (detector, configManager) = createDetector()

        detector.setThemeConfig(isFollowSystem = false, isUserInDark = true)

        verify {
            configManager.updateConfig(
                listOf("isFollowSystemTheme", "isDarkTheme"),
                listOf(false, true),
            )
        }
    }

    @Test
    fun `setThemeConfig updates config atomically`() {
        val (detector, _) =
            createDetector(
                isFollowSystemTheme = true,
                isDarkTheme = false,
            )

        detector.setThemeConfig(isFollowSystem = false, isUserInDark = true)

        val config = detector.themeConfig.value
        assertFalse(config.isFollowSystem)
        assertTrue(config.isUserInDark)
        assertTrue(config.resolveIsDark(isSystemInDark = false))
    }

    @Test
    fun `switching from follow-system to manual preserves correct theme`() {
        val (detector, _) =
            createDetector(
                isFollowSystemTheme = true,
                isDarkTheme = false,
            )

        // Following system while system is dark → dark
        assertTrue(detector.themeConfig.value.resolveIsDark(isSystemInDark = true))

        // Switch to manual light mode - should be light despite system being dark
        detector.setThemeConfig(isFollowSystem = false, isUserInDark = false)
        assertFalse(detector.themeConfig.value.resolveIsDark(isSystemInDark = true))
    }

    @Test
    fun `createThemeState resolves color scheme from config and system value`() {
        val (detector, _) =
            createDetector(
                isFollowSystemTheme = true,
                isDarkTheme = false,
            )

        val config = detector.themeConfig.value

        val darkState = ThemeState.createThemeState(config, isSystemInDark = true)
        assertTrue(darkState.isCurrentThemeDark)
        assertEquals(CrossPasteColor.darkColorScheme, darkState.colorScheme)

        val lightState = ThemeState.createThemeState(config, isSystemInDark = false)
        assertFalse(lightState.isCurrentThemeDark)
        assertEquals(CrossPasteColor.lightColorScheme, lightState.colorScheme)
    }
}
