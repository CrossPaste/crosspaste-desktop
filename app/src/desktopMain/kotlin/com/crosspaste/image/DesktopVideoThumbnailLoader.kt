package com.crosspaste.image

import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.windows.IconSize
import com.crosspaste.platform.windows.JIconExtract
import com.crosspaste.utils.LoggerExtension.logExecutionTime
import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Path
import javax.imageio.ImageIO

class DesktopVideoThumbnailLoader(
    private val platform: Platform,
    userDataPathProvider: UserDataPathProvider,
) : AbstractVideoThumbnailLoader(userDataPathProvider) {

    override val logger = KotlinLogging.logger {}

    override val thumbnailSize = 200

    override suspend fun save(
        key: String,
        value: PasteFileCoordinate,
        result: Path,
    ) {
        when {
            platform.isMacos() -> {
                logExecutionTime(logger, "Create video thumbnail by mac api for file: ${value.filePath}") {
                    MacAppUtils.createVideoThumbnail(
                        value.filePath.toString(),
                        result.toString(),
                    )
                }
            }
            platform.isWindows() -> {
                logExecutionTime(logger, "Create video thumbnail by windows api for file: ${value.filePath}") {
                    val image =
                        JIconExtract.getThumbnailForFile(
                            value.filePath.toString(),
                            IconSize(thumbnailSize, thumbnailSize),
                        ) ?: return@logExecutionTime
                    ImageIO.write(image, "PNG", result.toNioPath().toFile())
                }
            }
        }
    }
}
