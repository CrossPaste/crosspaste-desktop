package com.crosspaste.ui.base

import com.crosspaste.app.CrossPasteWebService
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.platform.Platform
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopUISupportTest {

    private fun process(
        exitCode: Int = 0,
        finishes: Boolean = true,
    ): Process =
        mockk<Process>(relaxed = true) {
            every { waitFor(30, TimeUnit.SECONDS) } returns finishes
            every { exitValue() } returns exitCode
        }

    private fun openBrowser(
        url: String = "https://github.com/CrossPaste",
        platformName: String = Platform.LINUX,
        starter: (List<String>) -> Process,
        assertions: (NotificationManager) -> Unit,
    ) = runBlocking {
        val notifications = mockk<NotificationManager>(relaxed = true)
        val webService = mockk<CrossPasteWebService>()
        coEvery { webService.refresh() } returns Unit
        val support =
            DesktopUISupport(
                crossPasteWebService = webService,
                copywriter = mockk(relaxed = true),
                notificationManager = notifications,
                platform = Platform(platformName, "amd64", 64, "test"),
                pasteContentEditor = mockk(relaxed = true),
                userDataPathProvider = mockk(relaxed = true),
                appWindowManager = mockk(relaxed = true),
                actionScope = this,
                browserProcessStarter = starter,
            )

        support.openUrlInBrowser(url)
        coroutineContext.job.children
            .toList()
            .joinAll()
        assertions(notifications)
    }

    @Test
    fun `Linux opens URLs without querying AWT browser support`() {
        val process = process()
        mockkStatic(Desktop::class)
        try {
            every { Desktop.isDesktopSupported() } returns false
            openBrowser(starter = { command ->
                assertEquals(listOf("xdg-open", "https://github.com/CrossPaste"), command)
                process
            }) { notifications ->
                verify(exactly = 0) { Desktop.isDesktopSupported() }
                verify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any()) }
                verify(exactly = 0) { process.destroy() }
            }
        } finally {
            unmockkStatic(Desktop::class)
        }
    }

    @Test
    fun `Linux trims URLs and passes shell metacharacters as one argument`() {
        val url = "https://example.com/path?q='value';echo\$HOME&command=\$(id)"
        openBrowser(url = "  $url\n", starter = { command ->
            assertEquals(listOf("xdg-open", url), command)
            process()
        }) { notifications ->
            verify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `Linux reports failure when xdg-open is missing`() {
        openBrowser(starter = { throw IOException("xdg-open not found") }) { notifications ->
            verify(exactly = 1) { notifications.sendNotification(any(), any(), MessageType.Error, any()) }
        }
    }

    @Test
    fun `Linux reports nonzero exit codes`() {
        val process = process(exitCode = 3)
        openBrowser(starter = { process }) { notifications ->
            verify(exactly = 1) { notifications.sendNotification(any(), any(), MessageType.Error, any()) }
            verify(exactly = 0) { process.destroy() }
        }
    }

    @Test
    fun `Linux leaves a long running opener alive without reporting failure`() {
        val process = process(finishes = false)
        openBrowser(starter = { process }) { notifications ->
            verify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any()) }
            verify(exactly = 0) { process.destroy() }
            verify(exactly = 0) { process.exitValue() }
        }
    }

    @Test
    fun `Linux rejects relative URLs before starting a process`() {
        openBrowser(url = "--help", starter = { error("Must not launch a relative URL") }) { notifications ->
            verify(exactly = 1) { notifications.sendNotification(any(), any(), MessageType.Error, any()) }
        }
    }

    @Test
    fun `Linux cancellation is not reported as a browser error`() {
        openBrowser(starter = { throw CancellationException("Cancelled") }) { notifications ->
            verify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any()) }
        }
    }

    @Test
    fun `other platforms retain the AWT availability check`() {
        mockkStatic(Desktop::class)
        try {
            every { Desktop.isDesktopSupported() } returns false
            for (platform in listOf(Platform.WINDOWS, Platform.MACOS)) {
                openBrowser(platformName = platform, starter = { error("Must not launch xdg-open") }) { notifications ->
                    verify(exactly = 1) { notifications.sendNotification(any(), any(), MessageType.Error, any()) }
                }
            }
            verify(exactly = 2) { Desktop.isDesktopSupported() }
        } finally {
            unmockkStatic(Desktop::class)
        }
    }

    @Test
    fun `other platforms continue opening URLs through AWT`() {
        val desktop = mockk<Desktop>(relaxed = true)
        mockkStatic(Desktop::class)
        try {
            every { Desktop.isDesktopSupported() } returns true
            every { Desktop.getDesktop() } returns desktop
            every { desktop.isSupported(Desktop.Action.BROWSE) } returns true
            for (platform in listOf(Platform.WINDOWS, Platform.MACOS)) {
                openBrowser(
                    url = "  https://github.com/CrossPaste\n",
                    platformName = platform,
                    starter = { error("Must not launch xdg-open") },
                ) { notifications ->
                    verify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any()) }
                }
            }
            verify(exactly = 2) { desktop.browse(URI("https://github.com/CrossPaste")) }
        } finally {
            unmockkStatic(Desktop::class)
        }
    }
}
