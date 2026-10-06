package com.crosspaste.paste.plugin.process

import com.crosspaste.config.AppConfig
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.paste.item.CreatePasteItemHelper.createFilesPasteItem
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.path.PlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.SingleFileInfoTree
import com.crosspaste.utils.getJsonUtils
import io.mockk.every
import io.mockk.mockk
import okio.Path.Companion.toOkioPath
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilesToImagesPluginTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    @Test
    fun `a failed move puts the files already moved back`(
        @TempDir storage: File,
    ) {
        val appConfig = mockk<AppConfig>()
        every { appConfig.useDefaultStoragePath } returns true
        val configManager = mockk<CommonConfigManager>()
        every { configManager.getCurrentConfig() } returns appConfig
        val platformProvider = mockk<PlatformUserDataPathProvider>()
        every { platformProvider.getUserDefaultStoragePath() } returns storage.toOkioPath()
        val plugin = FilesToImagesPlugin(UserDataPathProvider(configManager, platformProvider))

        val first = "app/2026-10-06/7/a.png"
        val missing = "app/2026-10-06/7/b.png"
        val firstFile =
            File(storage, "files/$first").also {
                it.parentFile.mkdirs()
                it.writeText("a")
            }
        val item =
            createFilesPasteItem(
                relativePathList = listOf(first, missing),
                fileInfoTreeMap =
                    mapOf(
                        "a.png" to SingleFileInfoTree(1L, "h1"),
                        "b.png" to SingleFileInfoTree(1L, "h2"),
                    ),
            )

        assertFailsWith<IllegalStateException> {
            plugin.process(PasteCoordinate(7L, "app"), listOf(item), null)
        }

        assertTrue(firstFile.exists(), "the item stays a files item, so its file must stay where it points")
        assertFalse(File(storage, "images/$first").exists())
    }
}
