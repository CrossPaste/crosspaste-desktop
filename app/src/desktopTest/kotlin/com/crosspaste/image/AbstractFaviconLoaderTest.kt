package com.crosspaste.image

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.TestAppConfig
import com.crosspaste.config.TestConfigManager
import com.crosspaste.path.UserDataPathProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AbstractFaviconLoaderTest {

    private class RecordingFaviconLoader(
        configManager: CommonConfigManager,
        userDataPathProvider: UserDataPathProvider,
    ) : AbstractFaviconLoader(configManager, userDataPathProvider) {

        override val logger = KotlinLogging.logger {}

        val requestedUrls = mutableListOf<String>()

        override suspend fun saveIco(
            url: String,
            path: Path,
        ): Path? {
            requestedUrls.add(url)
            return null
        }
    }

    @Test
    fun `load skips local file URLs before resolving the favicon cache`() =
        runTest {
            val pathProvider = mockk<UserDataPathProvider>()
            val loader = RecordingFaviconLoader(TestConfigManager(TestAppConfig()), pathProvider)

            listOf(
                "file:/tmp/example.txt",
                "file:///tmp/example.txt",
                "file://server/shared/example.txt",
            ).forEach { url ->
                assertNull(loader.load(url), url)
            }

            assertEquals(emptyList(), loader.requestedUrls)
            verify { pathProvider wasNot Called }
        }

    @Test
    fun `load skips unsupported schemes and HTTP URLs without a host`() =
        runTest {
            val pathProvider = mockk<UserDataPathProvider>()
            val loader = RecordingFaviconLoader(TestConfigManager(TestAppConfig()), pathProvider)

            listOf(
                "ftp://files.example.com/readme.txt",
                "mailto:user@example.com",
                "data:text/plain,hello",
                "https:///favicon.ico",
                "http://",
                "https://example.com:invalid/page",
                "//example.com/page",
                "/relative/path",
                "",
            ).forEach { url ->
                assertNull(loader.load(url), url)
            }

            assertEquals(emptyList(), loader.requestedUrls)
            verify { pathProvider wasNot Called }
        }

    @Test
    fun `load preserves cached web favicons when url preview is disabled`() =
        runTest {
            val pathProvider = mockk<UserDataPathProvider>()
            val loader =
                RecordingFaviconLoader(
                    TestConfigManager(TestAppConfig(enableUrlPreview = false)),
                    pathProvider,
                )
            val cache =
                createTempFile("crosspaste-favicon-", ".ico")
                    .toString()
                    .toPath()

            try {
                every { pathProvider.resolve("example.com.ico", any()) } returns cache

                assertEquals(cache, loader.load("https://example.com/page"))
                assertEquals(emptyList(), loader.requestedUrls)
            } finally {
                Files.deleteIfExists(cache.toNioPath())
            }
        }

    @Test
    fun `save skips favicon download when url preview is disabled`() =
        runTest {
            val loader =
                RecordingFaviconLoader(
                    TestConfigManager(TestAppConfig(enableUrlPreview = false)),
                    mockk(),
                )

            loader.save("example.com", "https://example.com/page", "example.com.ico".toPath())

            assertEquals(emptyList(), loader.requestedUrls)
        }

    @Test
    fun `save downloads favicon when url preview is enabled`() =
        runTest {
            val loader =
                RecordingFaviconLoader(
                    TestConfigManager(TestAppConfig(enableUrlPreview = true)),
                    mockk(),
                )

            loader.save("example.com", "https://example.com/page", "example.com.ico".toPath())

            assertEquals(
                listOf(
                    "https://example.com/favicon.ico",
                    "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&" +
                        "fallback_opts=TYPE,SIZE,URL&url=http://example.com&size=256",
                ),
                loader.requestedUrls,
            )
        }
}
