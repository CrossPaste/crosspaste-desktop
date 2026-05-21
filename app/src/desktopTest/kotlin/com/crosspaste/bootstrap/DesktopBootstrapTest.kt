package com.crosspaste.bootstrap

import com.crosspaste.net.LanBypassProxySelector
import com.crosspaste.ui.AwtExceptionHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.ProxySelector
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopBootstrapTest {

    private var originalProxySelector: ProxySelector? = null
    private var originalAwtHandlerProperty: String? = null
    private var originalUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    @BeforeTest
    fun snapshotProcessState() {
        originalProxySelector = ProxySelector.getDefault()
        originalAwtHandlerProperty = System.getProperty("sun.awt.exception.handler")
        originalUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @AfterTest
    fun restoreProcessState() {
        ProxySelector.setDefault(originalProxySelector)
        originalAwtHandlerProperty?.let { System.setProperty("sun.awt.exception.handler", it) }
            ?: System.clearProperty("sun.awt.exception.handler")
        Thread.setDefaultUncaughtExceptionHandler(originalUncaughtHandler)
    }

    @Test
    fun `preStart installs LanBypassProxySelector wrapper`() {
        DesktopBootstrap.preStart(KotlinLogging.logger {})

        val selector = ProxySelector.getDefault()
        assertNotNull(selector, "ProxySelector.getDefault() should not be null after preStart")
        assertTrue(
            selector is LanBypassProxySelector,
            "Expected LanBypassProxySelector, got ${selector::class.java.name}",
        )
    }

    @Test
    fun `preStart sets AWT EDT exception handler system property`() {
        System.clearProperty("sun.awt.exception.handler")

        DesktopBootstrap.preStart(KotlinLogging.logger {})

        assertEquals(
            AwtExceptionHandler::class.java.name,
            System.getProperty("sun.awt.exception.handler"),
        )
    }

    @Test
    fun `preStart installs a default uncaught exception handler`() {
        Thread.setDefaultUncaughtExceptionHandler(null)

        DesktopBootstrap.preStart(KotlinLogging.logger {})

        assertNotNull(
            Thread.getDefaultUncaughtExceptionHandler(),
            "Default uncaught exception handler should be installed",
        )
    }

    @Test
    fun `preStart is idempotent for ProxySelector wrapping`() {
        DesktopBootstrap.preStart(KotlinLogging.logger {})
        val first = ProxySelector.getDefault()

        DesktopBootstrap.preStart(KotlinLogging.logger {})
        val second = ProxySelector.getDefault()

        // LanBypassProxySelector.install() is documented as idempotent —
        // the wrapper should not be nested on a second call.
        assertEquals(
            first,
            second,
            "Repeated preStart calls should not stack additional ProxySelector wrappers",
        )
    }
}
