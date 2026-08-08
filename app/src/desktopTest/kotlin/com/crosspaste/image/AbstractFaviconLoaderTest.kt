package com.crosspaste.image

import com.crosspaste.config.CommonConfigManager
import com.crosspaste.config.TestAppConfig
import com.crosspaste.config.TestConfigManager
import com.crosspaste.path.UserDataPathProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

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
