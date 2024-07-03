package com.crosspaste.app

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TestAppWindowManager {

    @Test
    fun testMockTestAppWindowManager() {
        val mockOS = MockOS()
        val testAppWindowManager = TestWindowManager(mockOS)
        assertNull(testAppWindowManager.getPrevAppName())
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
        assertEquals("Chrome", testAppWindowManager.getPrevAppName())
        runBlocking { testAppWindowManager.unActiveSearchWindow(preparePaste = { true }) }
        assertEquals(2, testAppWindowManager.pasterId)
        assertEquals("Chrome", testAppWindowManager.getCurrentActiveAppName())
        runBlocking { testAppWindowManager.activeMainWindow() }
        runBlocking { testAppWindowManager.activeSearchWindow() }
        assertEquals("CrossPaste", testAppWindowManager.getCurrentActiveAppName())
        assertEquals("Chrome", testAppWindowManager.getPrevAppName())
        runBlocking { testAppWindowManager.unActiveSearchWindow(preparePaste = { false }) }
        assertEquals(2, testAppWindowManager.pasterId)
        runBlocking { testAppWindowManager.toPaste() }
        assertEquals(3, testAppWindowManager.pasterId)
    }
}
