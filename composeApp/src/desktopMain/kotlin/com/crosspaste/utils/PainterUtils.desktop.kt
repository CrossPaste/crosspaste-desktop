package com.crosspaste.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.loadSvgPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import com.crosspaste.ui.base.ImageBitmapToPainter
import com.crosspaste.ui.base.SvgResourceToPainter
import com.crosspaste.ui.base.ToPainterImage
import com.crosspaste.ui.base.XmlResourceToPainter
import okio.Path
import okio.Path.Companion.toOkioPath
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.xml.sax.InputSource
import kotlin.io.path.inputStream
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

actual fun getPainterUtils(): PainterUtils {
    return DesktopPainterUtils
}

object DesktopPainterUtils : PainterUtils {

    @OptIn(ExperimentalComposeUiApi::class)
    private val loader = ResourceLoader.Default

    override fun loadPainter(
        path: Path,
        density: Density,
    ): ToPainterImage {
        val fileName = path.name
        return when (fileName.substringAfterLast(".")) {
            "svg" -> SvgResourceToPainter(fileName, getSvgPainter(path, density))
            "xml" -> XmlResourceToPainter(getXmlImageVector(path, density))
            else -> {
                ImageBitmapToPainter(fileName, getImageBitmap(path))
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun loadResourcePainter(
        fileName: String,
        density: Density,
    ): ToPainterImage {
        val inputStream = loader.load(fileName)
        return when (fileName.substringAfterLast(".")) {
            "svg" ->
                SvgResourceToPainter(
                    fileName,
                    inputStream.buffered().use {
                        loadSvgPainter(it, density)
                    },
                )
            "xml" ->
                XmlResourceToPainter(
                    inputStream.buffered().use {
                        loadXmlImageVector(InputSource(it), density)
                    },
                )
            else -> {
                ImageBitmapToPainter(
                    fileName,
                    inputStream.use {
                        it.buffered().use(::loadImageBitmap)
                    },
                )
            }
        }
    }

    override fun getSvgPainter(
        path: Path,
        density: Density,
    ): Painter {
        return path.toNioPath().inputStream().buffered().use {
            loadSvgPainter(it, density)
        }
    }

    override fun getXmlImageVector(
        path: Path,
        density: Density,
    ): ImageVector {
        return path.toNioPath().inputStream().buffered().use {
            loadXmlImageVector(InputSource(it), density)
        }
    }

    override fun getImageBitmap(path: Path): ImageBitmap {
        return path.toNioPath().inputStream().buffered().use {
            it.use(::loadImageBitmap)
        }
    }

    override fun createThumbnail(path: Path) {
        val originalImage = Image.makeFromEncoded(path.toNioPath().readBytes())

        val originalWidth = originalImage.width
        val originalHeight = originalImage.height

        val thumbnailWidth: Int
        val thumbnailHeight: Int

        if (originalWidth <= originalHeight) {
            // 竖直方向的图片
            thumbnailWidth = 200
            thumbnailHeight = 200 * originalHeight / originalWidth
        } else {
            // 水平方向的图片
            thumbnailWidth = 200 * originalWidth / originalHeight
            thumbnailHeight = 200
        }

        // 创建一个新的Surface用于绘制缩略图
        val surface = Surface.makeRasterN32Premul(thumbnailWidth, thumbnailHeight)
        val canvas = surface.canvas

        // 使用原始图片的宽高比来计算缩放比例
        val scale =
            minOf(
                thumbnailWidth.toFloat() / originalImage.width,
                thumbnailHeight.toFloat() / originalImage.height,
            )

        // 计算绘制的起点，以便图像居中
        val dx = (thumbnailWidth - originalImage.width * scale) / 2
        val dy = (thumbnailHeight - originalImage.height * scale) / 2

        // 绘制缩略图
        canvas.drawImageRect(
            originalImage,
            org.jetbrains.skia.Rect.makeWH(originalImage.width.toFloat(), originalImage.height.toFloat()),
            org.jetbrains.skia.Rect.makeXYWH(dx, dy, originalImage.width * scale, originalImage.height * scale),
            null,
        )

        val thumbnailPath = getThumbnailPath(path)

        // 保存缩略图到文件
        surface.makeImageSnapshot().encodeToData()?.bytes?.let {
            thumbnailPath.toNioPath().writeBytes(it)
        }
    }

    override fun getThumbnailPath(path: Path): Path {
        return path
            .toNioPath()
            .resolveSibling("thumbnail_${path.name}")
            .toOkioPath()
    }
}
