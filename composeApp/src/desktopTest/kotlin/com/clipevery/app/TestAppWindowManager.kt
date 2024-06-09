package com.clipevery.app

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestAppWindowManager {

    companion object {

        var testAppWindowManager: AppWindowManager =
            DesktopAppWindowManager(
                lazy { throw NotImplementedError("not invoke") },
                -1,
            )

        @BeforeEach
        fun setUp() {
            TestWindowManager.init()
        }
    }

    @Test
    fun testMockTestAppWindowManager() {
        assertNull(testAppWindowManager.getPrevAppName())
        runBlocking { testAppWindowManager.toPaste() }
        assertEquals(1, TestWindowManager.pasterId)
        assertNull(testAppWindowManager.getCurrentActiveAppName())
        testAppWindowManager.activeMainWindow()
        assertEquals("Clipevery", testAppWindowManager.getCurrentActiveAppName())
        testAppWindowManager.unActiveMainWindow()
        assertNull(testAppWindowManager.getCurrentActiveAppName())
        TestWindowManager.activeApp("Chrome")
        runBlocking { testAppWindowManager.activeSearchWindow() }
        assertEquals("Clipevery", testAppWindowManager.getCurrentActiveAppName())
        assertEquals("Chrome", testAppWindowManager.getPrevAppName())
        runBlocking { testAppWindowManager.unActiveSearchWindow(preparePaste = { true }) }
        assertEquals(2, TestWindowManager.pasterId)
        assertEquals("Chrome", testAppWindowManager.getCurrentActiveAppName())
        testAppWindowManager.activeMainWindow()
        runBlocking { testAppWindowManager.activeSearchWindow() }
        assertEquals("Clipevery", testAppWindowManager.getCurrentActiveAppName())
        assertEquals("Chrome", testAppWindowManager.getPrevAppName())
        runBlocking { testAppWindowManager.unActiveSearchWindow(preparePaste = { false }) }
        assertEquals(2, TestWindowManager.pasterId)
        runBlocking { testAppWindowManager.toPaste() }
        assertEquals(3, TestWindowManager.pasterId)
    }
}
