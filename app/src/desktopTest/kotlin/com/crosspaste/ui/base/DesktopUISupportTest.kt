package com.crosspaste.ui.base

import com.crosspaste.app.AppFileType
import com.crosspaste.app.CrossPasteWebService
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        pathProvider: UserDataPathProvider = mockk(relaxed = true),
        operation: ((DesktopUISupport) -> Unit)? = null,
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
                userDataPathProvider = pathProvider,
                appWindowManager = mockk(relaxed = true),
                actionScope = this,
                browserProcessStarter = starter,
            )

        operation?.invoke(support) ?: support.openUrlInBrowser(url)
        coroutineContext.job.children
            .toList()
            .joinAll()
        assertions(notifications)
    }

    private fun withLocalOpener(
        type: String,
        existing: Boolean = true,
        platformName: String = Platform.LINUX,
        starter: (List<String>) -> Process,
        assertions: (NotificationManager, java.io.File) -> Unit,
    ) {
        val directory = createTempDirectory("crosspaste-open-").toFile()
        try {
            val file = directory.resolve("sample space #.$type")
            if (existing) {
                file.writeText("existing content")
            }
            val pathProvider = mockk<UserDataPathProvider>()
            every { pathProvider.resolve(any<String>(), AppFileType.TEMP) } returns file.toOkioPath()
            val pasteData = mockk<PasteData>()
            every { pasteData.id } returns 2L
            val rtf = mockk<PasteRtf>()
            every { rtf.rtf } returns "{\\rtf1 test content}"
            every { pasteData.getPasteItem(PasteRtf::class) } returns rtf
            openBrowser(
                platformName = platformName,
                pathProvider = pathProvider,
                operation = { support ->
                    when (type) {
                        "html" -> support.openHtml(1, "<html><body>test content</body></html>")
                        "rtf" -> support.openRtf(pasteData)
                        else -> support.openFile(file.toOkioPath())
                    }
                },
                starter = starter,
            ) { notifications -> assertions(notifications, file) }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `Linux opens local files without querying AWT support`() {
        mockkStatic(Desktop::class)
        try {
            every { Desktop.isDesktopSupported() } returns false
            for (type in listOf("html", "rtf", "txt")) {
                var command: List<String>? = null
                withLocalOpener(type, starter = {
                    command = it
                    process()
                }) { notifications, file ->
                    val uriArgument = requireNotNull(command)[1]
                    assertEquals(listOf("xdg-open", file.toPath().toUri().toString()), command)
                    assertTrue(uriArgument.startsWith("file:///"))
                    assertTrue(uriArgument.contains("sample%20space%20%23"))
                    assertEquals(file, java.io.File(URI(uriArgument)))
                    assertEquals("existing content", file.readText())
                    verify(exactly = 0) { Desktop.isDesktopSupported() }
                    verify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any()) }
                }
            }
        } finally {
            unmockkStatic(Desktop::class)
        }
    }

    @Test
    fun `Linux creates HTML and RTF before opening their URIs`() {
        for (type in listOf("html", "rtf")) {
            withLocalOpener(type, existing = false, starter = { command ->
                val file = java.io.File(URI(command[1]))
                assertTrue(file.readText().contains("test content"))
                process()
            }) { notifications, file ->
                if (type == "html") {
                    assertTrue(file.readText().contains("UTF-8", ignoreCase = true))
                } else {
                    assertEquals("{\\rtf1 test content}", file.readText())
                }
                verify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any()) }
            }
        }
    }

    @Test
    fun `Linux local open failures preserve operation specific notifications`() {
        val copywriter = mockk<GlobalCopywriter>()
        every { copywriter.getText(any()) } answers { firstArg<String>() }
        for ((type, title) in listOf(
            "html" to "failed_to_open_html_pasteboard",
            "rtf" to "failed_to_open_rtf_pasteboard",
            "txt" to "failed_to_open_file",
        )) {
            for (starter in listOf<(List<String>) -> Process>(
                { throw IOException("xdg-open not found") },
                { process(exitCode = 3) },
            )) {
                withLocalOpener(type, starter = starter) { notifications, _ ->
                    val notificationTitle = slot<(GlobalCopywriter) -> String>()
                    verify(exactly = 1) {
                        notifications.sendNotification(
                            capture(notificationTitle),
                            null,
                            MessageType.Error,
                            any(),
                        )
                    }
                    assertEquals(title, notificationTitle.captured(copywriter))
                }
            }
        }
    }

    @Test
    fun `Linux local openers may keep running without a failure notification`() {
        for (type in listOf("html", "rtf", "txt")) {
            val process = process(finishes = false)
            withLocalOpener(type, starter = { process }) { notifications, _ ->
                verify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any()) }
                verify(exactly = 0) { process.exitValue() }
                verify(exactly = 0) { process.destroy() }
            }
        }
    }

    @Test
    fun `Linux missing file is reported without starting an opener`() {
        val copywriter = mockk<GlobalCopywriter>()
        every { copywriter.getText(any()) } answers { firstArg<String>() }
        withLocalOpener(
            "txt",
            existing = false,
            starter = { error("Must not open a missing file") },
        ) { notifications, _ ->
            val notificationTitle = slot<(GlobalCopywriter) -> String>()
            verify(exactly = 1) {
                notifications.sendNotification(
                    capture(notificationTitle),
                    null,
                    MessageType.Error,
                    any(),
                )
            }
            assertEquals("file_not_found", notificationTitle.captured(copywriter))
        }
    }

    private fun browseLinuxFile(
        existing: Boolean = true,
        starter: (List<String>) -> Process,
        assertions: (NotificationManager, java.io.File) -> Unit,
    ) {
        val directory = createTempDirectory("crosspaste-browse-").toFile()
        try {
            val file = directory.resolve("a, b #.zip")
            if (existing) {
                file.writeText("existing content")
            }
            openBrowser(
                operation = { support -> support.browseFile(file.toOkioPath()) },
                starter = starter,
            ) { notifications -> assertions(notifications, file) }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun browseTitle(notifications: NotificationManager): String {
        val copywriter = mockk<GlobalCopywriter>()
        every { copywriter.getText(any()) } answers { firstArg<String>() }
        val notificationTitle = slot<(GlobalCopywriter) -> String>()
        verify(exactly = 1) {
            notifications.sendNotification(capture(notificationTitle), null, MessageType.Error, any())
        }
        return notificationTitle.captured(copywriter)
    }

    @Test
    fun `Linux shows files through FileManager1 without querying AWT support`() {
        mockkStatic(Desktop::class)
        try {
            every { Desktop.isDesktopSupported() } returns false
            val commands = mutableListOf<List<String>>()
            browseLinuxFile(starter = {
                commands += it
                process()
            }) { notifications, file ->
                val command = commands.single()
                assertEquals("dbus-send", command.first())
                assertTrue("--print-reply" in command)
                assertEquals("org.freedesktop.FileManager1.ShowItems", command[command.size - 3])
                assertEquals("string:", command.last())
                val uri = command[command.size - 2].removePrefix("array:string:")
                assertTrue(uri.startsWith("file:///"))
                assertTrue(',' !in uri)
                assertEquals(file.canonicalFile, java.io.File(URI(uri)).canonicalFile)
                verify(exactly = 0) { Desktop.isDesktopSupported() }
                verify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any()) }
            }
        } finally {
            unmockkStatic(Desktop::class)
        }
    }

    @Test
    fun `Linux falls back to opening the parent directory when FileManager1 fails`() {
        for (dbusFailure in listOf<() -> Process>(
            { throw IOException("dbus-send not found") },
            { process(exitCode = 1) },
        )) {
            val commands = mutableListOf<List<String>>()
            browseLinuxFile(starter = { command ->
                commands += command
                if (command.first() == "dbus-send") dbusFailure() else process()
            }) { notifications, file ->
                assertEquals(2, commands.size)
                val fallback = commands[1]
                assertEquals("xdg-open", fallback[0])
                assertEquals(file.parentFile.canonicalFile, java.io.File(URI(fallback[1])).canonicalFile)
                verify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any()) }
            }
        }
    }

    @Test
    fun `Linux reports a browse failure when no file manager can be opened`() {
        browseLinuxFile(starter = { command ->
            if (command.first() == "dbus-send") process(exitCode = 1) else throw IOException("xdg-open not found")
        }) { notifications, _ ->
            assertEquals("failed_to_browse_file_pasteboard", browseTitle(notifications))
        }
    }

    @Test
    fun `Linux browse of a missing file is reported without starting a process`() {
        browseLinuxFile(existing = false, starter = { error("Must not browse a missing file") }) { notifications, _ ->
            assertEquals("file_not_found", browseTitle(notifications))
        }
    }

    @Test
    fun `other platforms continue opening local files through AWT`() {
        val desktop = mockk<Desktop>(relaxed = true)
        mockkStatic(Desktop::class)
        try {
            every { Desktop.isDesktopSupported() } returns true
            every { Desktop.getDesktop() } returns desktop
            every { desktop.isSupported(any()) } returns true
            for (platform in listOf(Platform.WINDOWS, Platform.MACOS)) {
                for (type in listOf("html", "rtf", "txt")) {
                    withLocalOpener(type, platformName = platform, starter = { error("Must not launch xdg-open") }) {
                        notifications,
                        file,
                        ->
                        if (type == "txt") {
                            verify(exactly = 1) { desktop.open(file) }
                        } else {
                            verify(exactly = 1) { desktop.browse(file.toURI()) }
                        }
                        verify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any()) }
                    }
                }
            }
        } finally {
            unmockkStatic(Desktop::class)
        }
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
