package com.crosspaste.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import com.crosspaste.config.DesktopAppConfig
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.utils.getPlatformUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppWindowManagerTest {

    private fun createConfigManager(
        appConfig: DesktopAppConfig = DesktopAppConfig(language = "en"),
    ): DesktopConfigManager =
        mockk<DesktopConfigManager> {
            every { config } returns MutableStateFlow(appConfig)
        }

    private fun createWindowManager(): TestWindowManager {
        val platform = getPlatformUtils().platform
        val appSize =
            spyk(DesktopAppSize(platform, createConfigManager())) {
                every { getSearchWindowState(any()) } returns WindowState()
            }
        return TestWindowManager(appSize, MockOS())
    }

    @Test
    fun testMockTestAppWindowManager() {
        val mockOS = MockOS()

        val platform = getPlatformUtils().platform

        val mockDesktopAppSize =
            spyk(DesktopAppSize(platform, createConfigManager())) {
                every { getSearchWindowState(any()) } returns WindowState()
            }

        val testAppWindowManager =
            TestWindowManager(
                mockDesktopAppSize,
                mockOS,
            )
        assertNull(runBlocking { testAppWindowManager.getPrevAppName().first() })
        runBlocking { testAppWindowManager.toPaste() }
        assertEquals(1, testAppWindowManager.pasterId)
        assertNull(testAppWindowManager.getCurrentActiveAppName())
        runBlocking {
            testAppWindowManager.showMainWindow(WindowTrigger.SYSTEM)
            testAppWindowManager.saveActiveAppInfo("CrossPaste")
        }
        assertEquals("CrossPaste", testAppWindowManager.getCurrentActiveAppName())
        runBlocking {
            testAppWindowManager.hideMainWindowAndPaste()
        }
        assertNull(testAppWindowManager.getCurrentActiveAppName())
        runBlocking {
            testAppWindowManager.saveActiveAppInfo("Chrome")
            testAppWindowManager.showMainWindow(WindowTrigger.SYSTEM)
            testAppWindowManager.saveActiveAppInfo("CrossPaste")
        }
        assertEquals("CrossPaste", testAppWindowManager.getCurrentActiveAppName())
        assertEquals("Chrome", runBlocking { testAppWindowManager.getPrevAppName().first() })
        runBlocking { testAppWindowManager.hideSearchWindowAndPaste(size = 1, preparePaste = { true }) }
        assertEquals(2, testAppWindowManager.pasterId)
        assertEquals("Chrome", testAppWindowManager.getCurrentActiveAppName())
        runBlocking {
            testAppWindowManager.showMainWindow(WindowTrigger.SYSTEM)
            testAppWindowManager.saveActiveAppInfo("CrossPaste")
        }
        runBlocking {
            testAppWindowManager.showMainWindow(WindowTrigger.SYSTEM)
            testAppWindowManager.saveActiveAppInfo("CrossPaste")
        }
        assertEquals("CrossPaste", testAppWindowManager.getCurrentActiveAppName())
        assertEquals("Chrome", runBlocking { testAppWindowManager.getPrevAppName().first() })
        runBlocking { testAppWindowManager.hideSearchWindowAndPaste(size = 1, preparePaste = { false }) }
        assertEquals(2, testAppWindowManager.pasterId)
        runBlocking { testAppWindowManager.toPaste() }
        assertEquals(3, testAppWindowManager.pasterId)
    }

    @Test
    fun `preview does not take ownership of an interactive search window`() {
        val windowManager = createWindowManager()

        windowManager.showSearchWindow(WindowTrigger.SHORTCUT)
        windowManager.showSearchWindowPreview()
        windowManager.hideSearchWindowPreview()

        val windowInfo = windowManager.getCurrentSearchWindowInfo()
        assertTrue(windowInfo.show)
        assertEquals(WindowTrigger.SHORTCUT, windowInfo.trigger)
    }

    @Test
    fun `hiding an owned preview does not close the bubble window`() {
        val windowManager = createWindowManager()

        windowManager.showBubbleWindow(1L)
        windowManager.showSearchWindowPreview()
        assertEquals(WindowTrigger.PREVIEW, windowManager.getCurrentSearchWindowInfo().trigger)

        windowManager.hideSearchWindowPreview()

        assertFalse(windowManager.getCurrentSearchWindowInfo().show)
        assertTrue(windowManager.isBubbleWindowVisible())
    }

    @Test
    fun `interactive trigger claims a visible preview`() {
        val windowManager = createWindowManager()

        windowManager.showSearchWindowPreview()
        windowManager.showSearchWindow(WindowTrigger.TRAY_ICON)
        windowManager.hideSearchWindowPreview()

        val windowInfo = windowManager.getCurrentSearchWindowInfo()
        assertTrue(windowInfo.show)
        assertEquals(WindowTrigger.TRAY_ICON, windowInfo.trigger)
    }

    @Test
    fun `search window height is clamped when config is applied`() {
        val platform = getPlatformUtils().platform
        val oversized =
            DesktopAppSize(
                platform,
                createConfigManager(DesktopAppConfig(language = "en", searchWindowHeight = Int.MAX_VALUE)),
            )
        val undersized =
            DesktopAppSize(
                platform,
                createConfigManager(DesktopAppConfig(language = "en", searchWindowHeight = Int.MIN_VALUE)),
            )

        assertEquals(DesktopAppSize.MAX_SEARCH_WINDOW_HEIGHT.dp, oversized.appSizeValue.value.sideSearchWindowHeight)
        assertEquals(DesktopAppSize.MIN_SEARCH_WINDOW_HEIGHT.dp, undersized.appSizeValue.value.sideSearchWindowHeight)
    }
}
