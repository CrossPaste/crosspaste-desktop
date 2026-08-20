package com.crosspaste.rendering

import com.crosspaste.config.TestAppConfig
import com.crosspaste.config.TestConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.image.GenerateImageService
import com.crosspaste.image.ImageHandler
import com.crosspaste.net.ClientResponse
import com.crosspaste.net.ResourcesClient
import com.crosspaste.paste.PasteCollection
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.PasteState
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.item.CreatePasteItemHelper.createUrlPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.UrlPasteItem
import com.crosspaste.paste.item.getLegacyRenderingFilePath
import com.crosspaste.paste.item.getRenderingFilePath
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import io.ktor.http.ContentType
import io.ktor.utils.io.ByteReadChannel
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OpenGraphServiceTest {

    @Test
    fun `render skips network fetch when url preview is disabled`() =
        runTest {
            val resourcesClient = mockk<ResourcesClient>()
            val service =
                OpenGraphService<Any>(
                    configManager = TestConfigManager(TestAppConfig(enableUrlPreview = false)),
                    generateImageService = mockk(),
                    imageHandler = mockk(),
                    resourcesClient = resourcesClient,
                    updatePasteItemHelper = mockk(),
                    userDataPathProvider = mockk(),
                    pasteDao = mockk(),
                )

            service.render(mockk<PasteData>())

            verify { resourcesClient wasNot Called }
        }

    @Test
    fun `preview path is keyed by url hash`() {
        val pathProvider = createPathProvider()
        val coordinate = PasteCoordinate(7L, "instance", 123L)
        val first = createUrlPasteItem(url = "https://first.example.com")
        val second = createUrlPasteItem(url = "https://second.example.com")

        val firstPath = first.getRenderingFilePath(coordinate, pathProvider)
        val secondPath = second.getRenderingFilePath(coordinate, pathProvider)

        assertNotEquals(firstPath, secondPath)
        assertTrue(firstPath.name.endsWith("-${first.hash}.png"))
        assertTrue(secondPath.name.endsWith("-${second.hash}.png"))
    }

    @Test
    fun `preview path falls back to a legacy cache for an unchanged url`() {
        val pathProvider = createPathProvider()
        val coordinate = PasteCoordinate(7001L, "legacy-instance", 123L)
        val item = createUrlPasteItem(url = "https://legacy.example.com")
        val legacyPath = item.getLegacyRenderingFilePath(coordinate, pathProvider)
        val fileUtils = getFileUtils()

        try {
            fileUtils.createDir(legacyPath.parent!!).getOrThrow()
            fileUtils.createFile(legacyPath).getOrThrow()

            assertEquals(legacyPath, item.getRenderingFilePath(coordinate, pathProvider))
        } finally {
            fileUtils.deleteFile(legacyPath)
        }
    }

    @Test
    fun `late preview task is not published after url changes`() =
        runTest {
            val oldItem = createUrlPasteItem(url = "https://old.example.com")
            val newItem = createUrlPasteItem(url = "https://new.example.com")
            val oldPasteData = createPasteData(9L, oldItem)
            val currentPasteData = createPasteData(9L, newItem)
            val imageUrl = "https://cdn.example.com/preview.png"
            val pageResponse = mockk<ClientResponse>()
            val imageResponse = mockk<ClientResponse>()
            val resourcesClient = mockk<ResourcesClient>()
            val imageHandler = mockk<ImageHandler<Any>>()
            val generateImageService = mockk<GenerateImageService>(relaxed = true)
            val pasteDao = mockk<PasteDao>()
            val writtenPath = slot<Path>()

            every { pageResponse.getContentType() } returns ContentType.Text.Html
            coEvery { pageResponse.getBody() } returns
                ByteReadChannel("""<meta property="og:image" content="$imageUrl">""")
            coEvery { imageResponse.getBody() } returns ByteReadChannel(byteArrayOf(1, 2, 3))
            coEvery { resourcesClient.request(oldItem.url) } returns Result.success(pageResponse)
            coEvery { resourcesClient.request(imageUrl) } returns Result.success(imageResponse)
            coEvery { imageHandler.readImage(any<ByteReadChannel>()) } returns Any()
            coEvery { imageHandler.writeImage(any(), "png", capture(writtenPath)) } returns true
            coEvery { pasteDao.getNoDeletePasteData(oldPasteData.id) } returns currentPasteData

            val service =
                OpenGraphService(
                    configManager = TestConfigManager(TestAppConfig(enableUrlPreview = true)),
                    generateImageService = generateImageService,
                    imageHandler = imageHandler,
                    resourcesClient = resourcesClient,
                    updatePasteItemHelper = mockk(relaxed = true),
                    userDataPathProvider = createPathProvider(),
                    pasteDao = pasteDao,
                )

            service.render(oldPasteData)

            assertTrue(writtenPath.captured.name.endsWith("-${oldItem.hash}.png"))
            coVerify(exactly = 0) { generateImageService.markGenerationComplete(any()) }
        }

    @Test
    fun `JSON_LD_IMAGE_PATTERN matches image in JSON-LD`() {
        val pattern = """"image"\s*:\s*"([^"]+)"""".toRegex()
        val json = """{"@type":"Article","image":"https://example.com/image.jpg","name":"Test"}"""
        val match = pattern.find(json)
        assertEquals("https://example.com/image.jpg", match?.groupValues?.get(1))
    }

    @Test
    fun `JSON_LD_IMAGE_PATTERN matches image with spaces`() {
        val pattern = """"image"\s*:\s*"([^"]+)"""".toRegex()
        val json = """{"image" : "https://example.com/photo.png"}"""
        val match = pattern.find(json)
        assertEquals("https://example.com/photo.png", match?.groupValues?.get(1))
    }

    @Test
    fun `JSON_LD_IMAGE_PATTERN does not match without quotes`() {
        val pattern = """"image"\s*:\s*"([^"]+)"""".toRegex()
        val json = """{"image": null}"""
        val match = pattern.find(json)
        assertEquals(null, match)
    }

    @Test
    fun `JSON_LD_IMAGE_PATTERN matches first image in multiple`() {
        val pattern = """"image"\s*:\s*"([^"]+)"""".toRegex()
        val json = """{"image":"https://first.jpg","other":"data","image":"https://second.jpg"}"""
        val match = pattern.find(json)
        assertEquals("https://first.jpg", match?.groupValues?.get(1))
    }

    @Test
    fun `image URL filtering logic excludes tracking pixels`() {
        val src = "https://example.com/pixel.gif"
        val isFiltered =
            src.contains("pixel") ||
                src.contains("tracking") ||
                src.contains("1x1") ||
                src.endsWith(".gif")
        assertEquals(true, isFiltered)
    }

    @Test
    fun `image URL filtering excludes 1x1 images`() {
        val src = "https://example.com/1x1.png"
        val isFiltered = src.contains("1x1")
        assertEquals(true, isFiltered)
    }

    @Test
    fun `image URL filtering allows normal images`() {
        val src = "https://example.com/hero-banner.jpg"
        val isFiltered =
            src.contains("pixel") ||
                src.contains("tracking") ||
                src.contains("1x1") ||
                src.endsWith(".gif")
        assertEquals(false, isFiltered)
    }

    private fun createPathProvider(): UserDataPathProvider =
        UserDataPathProvider(
            TestConfigManager(TestAppConfig()),
            object : PlatformUserDataPathProvider {
                override fun getUserDefaultStoragePath() = "/tmp/crosspaste-open-graph-test".toPath()
            },
        )

    private fun createPasteData(
        id: Long,
        item: UrlPasteItem,
    ): PasteData =
        PasteData(
            id = id,
            appInstanceId = "instance",
            pasteAppearItem = item,
            pasteCollection = PasteCollection(emptyList()),
            pasteType = PasteType.URL_TYPE.type,
            size = item.size,
            hash = item.hash,
            createTime = 123L,
            pasteState = PasteState.LOADED,
        )
}
