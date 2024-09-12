package com.crosspaste.image

import androidx.compose.ui.unit.Density
import com.crosspaste.config.DefaultConfigManager
import com.crosspaste.path.DevelopmentPlatformUserDataPathProvider
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.presist.OneFilePersist
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class ImageDataLoaderTest {

    private val density = Density(1f, 1f)

    @Test
    fun testLoadImage() {
        val configDirPath = Files.createTempDirectory("configDir").toOkioPath()
        configDirPath.toFile().deleteOnExit()
        val configPath = configDirPath.resolve("appConfig.json")

        val imageDataLoader =
            DesktopImageDataLoader(
                DesktopThumbnailLoader(
                    UserDataPathProvider(
                        DefaultConfigManager(
                            OneFilePersist(configPath),
                        ),
                        DevelopmentPlatformUserDataPathProvider(),
                    ),
                ),
            )

        val crosspasteIconPath =
            this::class.java.classLoader
                .getResource("crosspaste_icon.png")?.path?.toPath()!!

        val crosspasteIconData = imageDataLoader.loadImageData(crosspasteIconPath, density)

        assertTrue(crosspasteIconData.isSuccess() && crosspasteIconData is ImageData<*>)

        assertTrue(!crosspasteIconData.isIcon)

        val fileSvgPath =
            this::class.java.classLoader
                .getResource("icon/paste/file.svg")?.path?.toPath()!!

        val fileSvgData = imageDataLoader.loadImageData(fileSvgPath, density)

        assertTrue(fileSvgData.isSuccess() && fileSvgData is ImageData<*>)

        assertTrue(fileSvgData.isIcon)
    }
}
