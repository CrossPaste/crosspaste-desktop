package com.crosspaste.image

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import com.crosspaste.ui.paste.PasteTypeIconBaseView
import okio.Path
import org.xml.sax.InputSource
import java.io.InputStream

actual fun getImageDataLoader(): ImageDataLoader {
    return DesktopImageDataLoader
}

object DesktopImageDataLoader : ImageDataLoader {

    override fun loadImageData(
        path: Path,
        density: Density,
        thumbnailLoader: ThumbnailLoader?,
    ): LoadStateData {
        return try {
            when (path.name.substringAfterLast(".")) {
                "svg" -> SvgData(path, readSvgPainter(path, density))
                "xml" -> ImageVectorData(path, readImageVector(path, density))
                else -> {
                    thumbnailLoader?.let {
                        it.load(path)?.let { thumbnailPath ->
                            ImageBitmapData(path, readImageBitmap(thumbnailPath))
                        }
                    } ?: ImageBitmapData(path, readImageBitmap(path))
                }
            }
        } catch (e: Exception) {
            ErrorStateData(e)
        }
    }

    @Composable
    override fun loadPasteType(pasteType: Int): ImageData<Painter> {
        return SvgData(
            pasteType,
            PasteTypeIconBaseView(pasteType),
        )
    }

    override fun loadIconData(
        isFile: Boolean?,
        density: Density,
    ): LoadStateData {
        try {
            when (isFile) {
                true -> {
                    return SvgData(
                        "file",
                        readSvgPainter("icon/paste/file.svg", density),
                    )
                }
                false -> {
                    return SvgData(
                        "dir",
                        readSvgPainter("icon/paste/folder.svg", density),
                    )
                }
                else -> {
                    return SvgData(
                        "file-slash",
                        readSvgPainter("icon/paste/file-slash.svg", density),
                    )
                }
            }
        } catch (e: Exception) {
            return ErrorStateData(e)
        }
    }

    fun readImageBitmap(key: Any): ImageBitmap {
        return inputStream(key).buffered().use {
            it.use(::loadImageBitmap)
        }
    }

    fun readSvgPainter(
        key: Any,
        density: Density,
    ): Painter {
        return inputStream(key).buffered().use {
            loadSvgPainter(it, density)
        }
    }

    fun readImageVector(
        key: Any,
        density: Density,
    ): ImageVector {
        return inputStream(key).buffered().use {
            loadXmlImageVector(InputSource(it), density)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun inputStream(key: Any): InputStream {
    return when (key) {
        is Path -> {
            key.toFile().inputStream()
        }

        is String -> {
            ResourceLoader.Default.load(key)
        }

        else -> {
            throw IllegalArgumentException("Unknown key type")
        }
    }
}
