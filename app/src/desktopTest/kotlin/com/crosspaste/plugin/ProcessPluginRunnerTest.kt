package com.crosspaste.plugin

import com.crosspaste.app.AppName
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessPluginRunnerTest {

    // Initialize JsonUtils before any PasteItem class (circular static init)
    private val jsonUtils = getJsonUtils()

    private val environment =
        PluginEnvironment(Platform(Platform.MACOS, "aarch64", 64, "15"), "2.3.0", setOf("process"))

    private val executor = mockk<ProcessPluginExecutor>()
    private val notificationManager = mockk<NotificationManager>(relaxed = true)
    private val pasteboardService = mockk<PasteboardService>(relaxed = true)
    private val pasteReleaseService = mockk<PasteReleaseService>(relaxed = true)

    private val runner =
        ProcessPluginRunner(
            appInfo = mockk(relaxed = true),
            copywriter =
                mockk(relaxed = true) {
                    every { language() } returns "en"
                    every { getText(any(), *anyVararg()) } answers { firstArg() }
                },
            environment = environment,
            executor = executor,
            notificationManager = notificationManager,
            pasteboardService = pasteboardService,
            pasteItemReader =
                mockk<PasteItemReader> {
                    every { getSummary(any()) } returns "hello"
                    every { getRaw(any()) } returns "hello"
                },
            pasteReleaseService = pasteReleaseService,
            pasteTagDao = mockk(relaxed = true),
            uiSupport = mockk(relaxed = true),
            userDataPathProvider = mockk(relaxed = true),
        )

    private val pasteData =
        createTextPasteItem(text = "hello").let { item ->
            PasteData(
                id = 7,
                appInstanceId = "app",
                pasteAppearItem = item,
                pasteCollection = PasteCollection(listOf()),
                pasteType = PasteType.TEXT_TYPE.type,
                source = null,
                size = item.size,
                hash = item.hash,
                pasteState = PasteState.LOADED,
                createTime = 0,
            )
        }

    private fun plugin(output: String) =
        LocalPlugin(
            directory = "/plugins/upper".toPath(),
            manifest =
                PluginManifest(
                    id = "com.example.upper",
                    version = "1.0.0",
                    name = mapOf("en" to "Upper"),
                    pasteTypes = listOf("text"),
                    runtime = "process",
                    platforms = mapOf("macos" to PluginPlatform(command = listOf("/bin/sh", "run.sh"))),
                    output = output,
                ),
            problem = null,
            missingRequirements = emptyList(),
            enabled = true,
        )

    @Test
    fun `replaceClipboard writes trimmed stdout and passes the paste as CLI JSON`() =
        runTest {
            val input = slot<String>()
            coEvery { executor.execute(any(), listOf("/bin/sh", "run.sh"), capture(input)) } returns
                PluginProcessResult(0, "HELLO\n\n", "")
            coEvery { pasteReleaseService.createLocalPasteData(any(), any(), any()) } returns 42L
            val written = slot<TextPasteItem>()
            coEvery { pasteboardService.tryWritePasteboard(42L, capture(written), true, false) } returns
                Result.success(null)

            runner.run(plugin("replaceClipboard"), pasteData)

            assertEquals("HELLO", written.captured.text)
            coVerify { pasteReleaseService.createLocalPasteData(any(), any(), AppName) }
            assertTrue(input.captured.contains("\"id\":7"), input.captured)
            assertTrue(input.captured.contains("\"typeName\":\"text\""), input.captured)
            assertTrue(input.captured.contains("\"rawContent\":\"hello\""), input.captured)
        }

    @Test
    fun `the chosen action reaches the executor`() =
        runTest {
            coEvery { executor.execute(any(), any(), any(), "zh-CN") } returns PluginProcessResult(0, "你好", "")

            runner.run(plugin("replaceClipboard"), pasteData, "zh-CN")

            coVerify { executor.execute(any(), any(), any(), "zh-CN") }
        }

    @Test
    fun `newPaste records a history entry attributed to CrossPaste and notifies success`() =
        runTest {
            coEvery { executor.execute(any(), any(), any()) } returns PluginProcessResult(0, "HELLO", "")

            runner.run(plugin("newPaste"), pasteData)

            coVerify { pasteReleaseService.createLocalPasteData(any(), any(), AppName) }
            coVerify(exactly = 0) { pasteboardService.tryWritePasteboard(any(), any<TextPasteItem>(), any(), any()) }
            verify { notificationManager.sendNotification(any(), any(), MessageType.Success, any()) }
        }

    @Test
    fun `failures notify and leave the clipboard alone`() =
        runTest {
            coEvery { executor.execute(any(), any(), any()) } returns PluginProcessResult(2, "partial", "boom")

            runner.run(plugin("replaceClipboard"), pasteData)

            coVerify(exactly = 0) { pasteboardService.tryWritePasteboard(any(), any<TextPasteItem>(), any(), any()) }
            verify { notificationManager.sendNotification(any(), any(), MessageType.Error, any()) }
        }

    @Test
    fun `replaceClipboard notifies failure when tryWritePasteboard fails`() =
        runTest {
            coEvery { executor.execute(any(), any(), any()) } returns PluginProcessResult(0, "HELLO", "")
            coEvery { pasteboardService.tryWritePasteboard(any(), any<TextPasteItem>(), true, false) } returns
                Result.failure(RuntimeException("locked"))

            runner.run(plugin("replaceClipboard"), pasteData)

            verify { notificationManager.sendNotification(any(), any(), MessageType.Error, any()) }
        }

    @Test
    fun `applyOutput exception is caught and notified as failure`() =
        runTest {
            coEvery { executor.execute(any(), any(), any()) } returns PluginProcessResult(0, "HELLO", "")
            coEvery { pasteReleaseService.createLocalPasteData(any(), any(), any()) } throws
                IllegalStateException("disk error")

            runner.run(plugin("newPaste"), pasteData)

            verify { notificationManager.sendNotification(any(), any(), MessageType.Error, any()) }
        }

    @Test
    fun `oversized output is reported instead of applied`() =
        runTest {
            coEvery { executor.execute(any(), any(), any()) } returns
                PluginProcessResult(0, "HELLO", "", stdoutTruncated = true)

            runner.run(plugin("replaceClipboard"), pasteData)

            coVerify(exactly = 0) { pasteboardService.tryWritePasteboard(any(), any<TextPasteItem>(), any(), any()) }
            verify { notificationManager.sendNotification(any(), any(), MessageType.Error, any()) }
        }
}
