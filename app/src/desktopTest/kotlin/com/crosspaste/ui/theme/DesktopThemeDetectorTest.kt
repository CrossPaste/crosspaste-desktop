package com.crosspaste.ui.theme

import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopThemeDetectorTest {

    private fun createDetector(
        isFollowSystemTheme: Boolean = true,
        isDarkTheme: Boolean = false,
        testScope: TestScope,
    ): Pair<DesktopThemeDetector, CommonConfigManager> {
        val appConfig = mockk<AppConfig>()
        every { appConfig.isFollowSystemTheme } returns isFollowSystemTheme
        every { appConfig.isDarkTheme } returns isDarkTheme

        val configManager = mockk<CommonConfigManager>()
        every { configManager.getCurrentConfig() } returns appConfig
        every { configManager.updateConfig(any<List<String>>(), any<List<Any>>()) } returns Unit

        // Use backgroundScope so the Eagerly-started combine coroutine gets cancelled
        // automatically when the test finishes, preventing UncompletedCoroutinesError.
        val detector = DesktopThemeDetector(configManager, testScope.backgroundScope)
        return detector to configManager
    }

    @Test
    fun `initial state follows config values`() =
        runTest(UnconfinedTestDispatcher()) {
            val (detector, _) =
                createDetector(
                    isFollowSystemTheme = false,
                    isDarkTheme = true,
                    testScope = this,
                )
            advanceUntilIdle()

            val state = detector.themeState.value
            assertFalse(state.isFollowSystem)
            assertTrue(state.isUserInDark)
            // Since followSystem=false and userInDark=true → dark theme
            assertTrue(state.isCurrentThemeDark)
            assertEquals(CrossPasteColor.darkColorScheme, state.colorScheme)
        }

    @Test
    fun `setSystemInDark updates theme when following system`() =
        runTest(UnconfinedTestDispatcher()) {
            val (detector, _) =
                createDetector(
                    isFollowSystemTheme = true,
                    isDarkTheme = false,
                    testScope = this,
                )
            advanceUntilIdle()

            // Initially system dark is false
            assertFalse(detector.themeState.value.isCurrentThemeDark)

            // System switches to dark
            detector.setSystemInDark(true)
            advanceUntilIdle()

            assertTrue(detector.themeState.value.isCurrentThemeDark)
            assertEquals(CrossPasteColor.darkColorScheme, detector.themeState.value.colorScheme)
        }

    @Test
    fun `setSystemInDark has no effect when not following system`() =
        runTest(UnconfinedTestDispatcher()) {
            val (detector, _) =
                createDetector(
                    isFollowSystemTheme = false,
                    isDarkTheme = false,
                    testScope = this,
                )
            advanceUntilIdle()

            detector.setSystemInDark(true)
            advanceUntilIdle()

            // Still light because user preference is false and not following system
            assertFalse(detector.themeState.value.isCurrentThemeDark)
        }

    @Test
    fun `setThemeConfig persists to config manager`() =
        runTest(UnconfinedTestDispatcher()) {
            val (detector, configManager) =
                createDetector(testScope = this)
            advanceUntilIdle()

            detector.setThemeConfig(isFollowSystem = false, isUserInDark = true)

            verify {
                configManager.updateConfig(
                    listOf("isFollowSystemTheme", "isDarkTheme"),
                    listOf(false, true),
                )
            }
        }

    @Test
    fun `setThemeConfig updates theme state atomically`() =
        runTest(UnconfinedTestDispatcher()) {
            val (detector, _) =
                createDetector(
                    isFollowSystemTheme = true,
                    isDarkTheme = false,
                    testScope = this,
                )
            advanceUntilIdle()

            // Switch from follow-system to manual dark mode
            detector.setThemeConfig(isFollowSystem = false, isUserInDark = true)
            advanceUntilIdle()

            val state = detector.themeState.value
            assertFalse(state.isFollowSystem)
            assertTrue(state.isUserInDark)
            assertTrue(state.isCurrentThemeDark)
        }

    @Test
    fun `switching from follow-system to manual preserves correct theme`() =
        runTest(UnconfinedTestDispatcher()) {
            val (detector, _) =
                createDetector(
                    isFollowSystemTheme = true,
                    isDarkTheme = false,
                    testScope = this,
                )
            advanceUntilIdle()

            // System is in dark mode
            detector.setSystemInDark(true)
            advanceUntilIdle()
            assertTrue(detector.themeState.value.isCurrentThemeDark)

            // Switch to manual light mode - should become light despite system being dark
            detector.setThemeConfig(isFollowSystem = false, isUserInDark = false)
            advanceUntilIdle()

            assertFalse(detector.themeState.value.isCurrentThemeDark)
            assertEquals(CrossPasteColor.lightColorScheme, detector.themeState.value.colorScheme)
        }
}
