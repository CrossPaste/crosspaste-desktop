package com.crosspaste.paste.plugin.process

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.TestAppConfig
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createImagesPasteItem
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.utils.getJsonUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscardOversizedNonFilePluginTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private val coord = PasteCoordinate(id = 1L, appInstanceId = "test")

    private val maxSizeBytes = 8L * 1024 * 1024

    private fun configManager(enabled: Boolean): CommonConfigManager {
        val configManager = mockk<CommonConfigManager>(relaxed = true)
        every { configManager.getCurrentConfig() } returns
            TestAppConfig(enabledNonFilePasteSizeLimit = enabled, maxNonFilePasteSize = 8)
        return configManager
    }

    private fun oversizedTextItem(): TextPasteItem =
        TextPasteItem(
            identifiers = listOf(),
            hash = "oversized",
            size = maxSizeBytes + 1,
            text = "oversized",
        )

    @Test
    fun `all oversized items are discarded and notification is sent`() {
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        val plugin = DiscardOversizedNonFilePlugin(configManager(enabled = true), notificationManager)

        val result = plugin.process(coord, listOf(oversizedTextItem()), null)

        assertTrue(result.isEmpty())
        verify(exactly = 1) { notificationManager.sendNotification(any(), any(), any(), any()) }
    }

    @Test
    fun `partial oversized items are degraded silently`() {
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        val plugin = DiscardOversizedNonFilePlugin(configManager(enabled = true), notificationManager)

        val withinLimit = createTextPasteItem(text = "small")
        val result = plugin.process(coord, listOf(withinLimit, oversizedTextItem()), null)

        assertEquals(listOf(withinLimit), result)
        verify(exactly = 0) { notificationManager.sendNotification(any(), any(), any(), any()) }
    }

    @Test
    fun `disabled limit passes items through unchanged`() {
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        val plugin = DiscardOversizedNonFilePlugin(configManager(enabled = false), notificationManager)

        val items = listOf(oversizedTextItem())
        val result = plugin.process(coord, items, null)

        assertEquals(items, result)
        verify(exactly = 0) { notificationManager.sendNotification(any(), any(), any(), any()) }
    }

    @Test
    fun `file and image items are exempt regardless of size`() {
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        val plugin = DiscardOversizedNonFilePlugin(configManager(enabled = true), notificationManager)

        val bigTree = SingleFileInfoTree(size = maxSizeBytes * 10, hash = "big")
        val filesItem =
            createFilesPasteItem(
                relativePathList = listOf("big.bin"),
                fileInfoTreeMap = mapOf("big.bin" to bigTree),
            )
        val imagesItem =
            createImagesPasteItem(
                relativePathList = listOf("big.png"),
                fileInfoTreeMap = mapOf("big.png" to bigTree),
            )
        val result = plugin.process(coord, listOf(filesItem, imagesItem), null)

        assertEquals(listOf(filesItem, imagesItem), result)
        verify(exactly = 0) { notificationManager.sendNotification(any(), any(), any(), any()) }
    }

    @Test
    fun `empty list returns empty without notification`() {
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        val plugin = DiscardOversizedNonFilePlugin(configManager(enabled = true), notificationManager)

        val result = plugin.process(coord, emptyList(), null)

        assertTrue(result.isEmpty())
        verify(exactly = 0) { notificationManager.sendNotification(any(), any(), any(), any()) }
    }

    @Test
    fun `item exactly at limit is retained`() {
        val notificationManager = mockk<NotificationManager>(relaxed = true)
        val plugin = DiscardOversizedNonFilePlugin(configManager(enabled = true), notificationManager)

        val atLimit =
            TextPasteItem(
                identifiers = listOf(),
                hash = "at-limit",
                size = maxSizeBytes,
                text = "at-limit",
            )
        val result = plugin.process(coord, listOf(atLimit), null)

        assertEquals(listOf(atLimit), result)
        verify(exactly = 0) { notificationManager.sendNotification(any(), any(), any(), any()) }
    }
}
