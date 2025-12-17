package com.crosspaste.app

import androidx.compose.ui.window.WindowState
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.platform.DesktopPlatformProvider
import com.crosspaste.presist.OneFilePersist
import com.crosspaste.utils.DesktopDeviceUtils
import com.crosspaste.utils.DesktopLocaleUtils
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppWindowManagerTest {

    @Test
    fun testMockTestAppWindowManager() {
        val mockOS = MockOS()

        val configDirPath = Files.createTempDirectory("configDir").toOkioPath()
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("appConfig.json")

        val platform = DesktopPlatformProvider().getPlatform()

        val desktopConfigManager =
            DesktopConfigManager(
                OneFilePersist(configPath),
                DesktopDeviceUtils(platform),
                DesktopLocaleUtils,
            )

        val mockDesktopAppSize =
            spyk(DesktopAppSize(platform)) {
                every { getSearchWindowState() } returns WindowState()
            }

        val testAppWindowManager =
            TestWindowManager(
                mockDesktopAppSize,
                desktopConfigManager,
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
}
