package com.crosspaste.paste.plugin.type

import com.crosspaste.image.ImageHandler
import com.crosspaste.paste.DesktopWriteTransferableBuilder
import com.crosspaste.paste.PasteWriteScope
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopImageTypePluginTest {

    // Guard against PasteItem/JsonUtils circular class initialization
    private val jsonUtils = getJsonUtils()

    private val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

    private val imageHandler: ImageHandler<BufferedImage> =
        mockk {
            coEvery { readImage(any<okio.Path>()) } returns image
        }

    private val userDataPathProvider: UserDataPathProvider =
        mockk {
            every { resolve(any(), any<String>(), any(), any()) } answers {
                firstArg<okio.Path>() / secondArg<String>()
            }
        }

    private val plugin =
        DesktopImageTypePlugin(
            appInfo = mockk(relaxed = true),
            imageHandler = imageHandler,
            platform = mockk<Platform>(relaxed = true),
            userDataPathProvider = userDataPathProvider,
        )

    private val singleImage =
        ImagesPasteItem(
            identifiers = listOf("image"),
            count = 1,
            hash = "image-hash",
            size = 100,
            basePath = System.getProperty("java.io.tmpdir").toPath().toString(),
            fileInfoTreeMap = emptyMap(),
            relativePathList = listOf("image.png"),
        )

    @Test
    fun `building the transferable does not decode the image`() =
        runTest {
            val transferable =
                DesktopWriteTransferableBuilder().add(plugin, singleImage, PasteWriteScope.PRIMARY_CATEGORY).build()

            assertTrue(transferable.isDataFlavorSupported(DataFlavor.imageFlavor))
            coVerify(exactly = 0) { imageHandler.readImage(any<okio.Path>()) }

            assertSame(image, transferable.getTransferData(DataFlavor.imageFlavor))
            assertSame(image, transferable.getTransferData(DataFlavor.imageFlavor))
            coVerify(exactly = 1) { imageHandler.readImage(any<okio.Path>()) }
        }

    @Test
    fun `an unreadable image fails only when it is asked for`() =
        runTest {
            coEvery { imageHandler.readImage(any<okio.Path>()) } returns null
            val transferable =
                DesktopWriteTransferableBuilder().add(plugin, singleImage, PasteWriteScope.PRIMARY_CATEGORY).build()

            assertTrue(transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
            assertFailsWith<IOException> { transferable.getTransferData(DataFlavor.imageFlavor) }
        }
}
