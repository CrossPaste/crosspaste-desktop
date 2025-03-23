package com.crosspaste.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppWindowManagerTest {

    @Test
    fun testMockTestAppWindowManager() {
        val mockOS = MockOS()
        val testAppWindowManager = TestWindowManager(DesktopAppSize, mockOS)
        assertNull(runBlocking { testAppWindowManager.getPrevAppName().first() })
        runBlocking { testAppWindowManager.toPaste() }
        assertEquals(1, testAppWindowManager.pasterId)
        assertNull(testAppWindowManager.getCurrentActiveAppName())
        runBlocking { testAppWindowManager.activeMainWindow() }
        assertEquals("CrossPaste", testAppWindowManager.getCurrentActiveAppName())
        runBlocking { testAppWindowManager.unActiveMainWindow() }
        assertNull(testAppWindowManager.getCurrentActiveAppName())
        mockOS.currentApp = "Chrome"
        runBlocking { testAppWindowManager.activeSearchWindow() }
        assertEquals("CrossPaste", testAppWindowManager.getCurrentActiveAppName())
        assertEquals("Chrome", runBlocking { testAppWindowManager.getPrevAppName().first() })
        runBlocking { testAppWindowManager.unActiveSearchWindow(preparePaste = { true }) }
        assertEquals(2, testAppWindowManager.pasterId)
        assertEquals("Chrome", testAppWindowManager.getCurrentActiveAppName())
        runBlocking { testAppWindowManager.activeMainWindow() }
        runBlocking { testAppWindowManager.activeSearchWindow() }
        assertEquals("CrossPaste", testAppWindowManager.getCurrentActiveAppName())
        assertEquals("Chrome", runBlocking { testAppWindowManager.getPrevAppName().first() })
        runBlocking { testAppWindowManager.unActiveSearchWindow(preparePaste = { false }) }
        assertEquals(2, testAppWindowManager.pasterId)
        runBlocking { testAppWindowManager.toPaste() }
        assertEquals(3, testAppWindowManager.pasterId)
    }
}
