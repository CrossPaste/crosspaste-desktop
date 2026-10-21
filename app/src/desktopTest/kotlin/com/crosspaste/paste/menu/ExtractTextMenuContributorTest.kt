package com.crosspaste.paste.menu

import com.crosspaste.app.AppName
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.OCRModule
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteReleaseService
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteboardService
import com.crosspaste.paste.item.CreatePasteItemHelper.createImagesPasteItem
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getJsonUtils
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractTextMenuContributorTest {

    // Initialize JsonUtils before any PasteItem matcher touches PasteItem's companion
    private val jsonUtils = getJsonUtils()

    private val copywriter =
        mockk<GlobalCopywriter> {
            every { getText("extract_text") } returns "Extract text"
        }

    private val ocrModule = mockk<OCRModule>()
    private val pasteboardService = mockk<PasteboardService>(relaxed = true)
    private val pasteReleaseService =
        mockk<PasteReleaseService> {
            coEvery { createLocalPasteData(any(), any(), any()) } returns 42L
        }
    private val basePath = "/test/dir".toPath()
    private val path1 = "/test/dir/image1.png".toPath()
    private val path2 = "/test/dir/image2.png".toPath()

    private val userDataPathProvider =
        mockk<UserDataPathProvider> {
            every { resolve(basePath, "image1.png", autoCreate = false, isFile = true) } returns path1
            every { resolve(basePath, "image2.png", autoCreate = false, isFile = true) } returns path2
        }

    private val contributor =
        ExtractTextMenuContributor(
            appInfo = mockk(relaxed = true),
            copywriter = copywriter,
            ocrModule = ocrModule,
            pasteboardService = pasteboardService,
            pasteReleaseService = pasteReleaseService,
            userDataPathProvider = userDataPathProvider,
        )

    private fun imagePasteData(imagesItem: ImagesPasteItem?): PasteData =
        mockk<PasteData>(relaxed = true) {
            every { getType() } returns PasteType.IMAGE_TYPE
            every { getPasteItem(ImagesPasteItem::class) } returns imagesItem
        }

    @Test
    fun `contribute returns extract_text action for IMAGE_TYPE`() {
        val pasteData = imagePasteData(null)
        val actions = contributor.contribute(pasteData)

        assertEquals(1, actions.size)
        assertEquals("Extract text", actions.single().label)
    }

    @Test
    fun `contribute returns empty list for non-image paste types`() {
        val pasteData =
            mockk<PasteData>(relaxed = true) {
                every { getType() } returns PasteType.TEXT_TYPE
            }

        assertTrue(contributor.contribute(pasteData).isEmpty())
    }

    @Test
    fun `action onClick records the extracted text as a CrossPaste paste and writes it to pasteboard`() =
        runTest {
            val imagesItem =
                createImagesPasteItem(
                    basePath = "/test/dir",
                    relativePathList = listOf("image1.png", "image2.png"),
                    fileInfoTreeMap = emptyMap(),
                )
            val pasteData = imagePasteData(imagesItem)

            coEvery { ocrModule.extractText(path1) } returns Result.success("Hello")
            coEvery { ocrModule.extractText(path2) } returns Result.success("World")

            val action = contributor.contribute(pasteData).single()
            action.onClick()

            coVerify(exactly = 1) {
                pasteReleaseService.createLocalPasteData(
                    appInstanceId = any(),
                    pasteItem = match { (it as TextPasteItem).text == "Hello\nWorld" },
                    source = AppName,
                )
                pasteboardService.tryWritePasteboard(
                    id = 42L,
                    pasteItem = match { (it as TextPasteItem).text == "Hello\nWorld" },
                    localOnly = true,
                )
            }
        }

    @Test
    fun `action onClick skips writing when OCR returns failure or blank`() =
        runTest {
            val imagesItem =
                createImagesPasteItem(
                    basePath = "/test/dir",
                    relativePathList = listOf("image1.png", "image2.png"),
                    fileInfoTreeMap = emptyMap(),
                )
            val pasteData = imagePasteData(imagesItem)

            coEvery { ocrModule.extractText(path1) } returns Result.failure(RuntimeException("OCR failed"))
            coEvery { ocrModule.extractText(path2) } returns Result.success("   ")

            val action = contributor.contribute(pasteData).single()
            action.onClick()

            coVerify(exactly = 0) {
                pasteReleaseService.createLocalPasteData(
                    appInstanceId = any(),
                    pasteItem = any(),
                    source = any(),
                )
                pasteboardService.tryWritePasteboard(
                    id = any(),
                    pasteItem = any<PasteItem>(),
                    localOnly = any(),
                )
            }
        }

    @Test
    fun `action onClick skips writing when no ImagesPasteItem exists`() =
        runTest {
            val pasteData = imagePasteData(null)

            val action = contributor.contribute(pasteData).single()
            action.onClick()

            coVerify(exactly = 0) {
                pasteReleaseService.createLocalPasteData(
                    appInstanceId = any(),
                    pasteItem = any(),
                    source = any(),
                )
                pasteboardService.tryWritePasteboard(
                    id = any(),
                    pasteItem = any<PasteItem>(),
                    localOnly = any(),
                )
            }
        }

    @Test
    fun `action onClick handles createLocalPasteData failure gracefully`() =
        runTest {
            val imagesItem =
                createImagesPasteItem(
                    basePath = "/test/dir",
                    relativePathList = listOf("image1.png"),
                    fileInfoTreeMap = emptyMap(),
                )
            val pasteData = imagePasteData(imagesItem)

            coEvery { ocrModule.extractText(path1) } returns Result.success("Hello")
            coEvery {
                pasteReleaseService.createLocalPasteData(any(), any(), any())
            } throws RuntimeException("Database error")

            val action = contributor.contribute(pasteData).single()
            action.onClick()

            coVerify(exactly = 0) {
                pasteboardService.tryWritePasteboard(
                    id = any(),
                    pasteItem = any<PasteItem>(),
                    localOnly = any(),
                )
            }
        }
}
