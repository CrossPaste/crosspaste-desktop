package com.clipevery.ui.base

import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

fun createThumbnail(path: Path) {
    val originalImage = Image.makeFromEncoded(path.readBytes())

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
    val scale = minOf(
        thumbnailWidth.toFloat() / originalImage.width,
        thumbnailHeight.toFloat() / originalImage.height
    )

    // 计算绘制的起点，以便图像居中
    val dx = (thumbnailWidth - originalImage.width * scale) / 2
    val dy = (thumbnailHeight - originalImage.height * scale) / 2

    // 绘制缩略图
    canvas.drawImageRect(
        originalImage,
        org.jetbrains.skia.Rect.makeWH(originalImage.width.toFloat(), originalImage.height.toFloat()),
        org.jetbrains.skia.Rect.makeXYWH(dx, dy, originalImage.width * scale, originalImage.height * scale),
        null
    )

    val thumbnailPath = getThumbnailPath(path)

    // 保存缩略图到文件
    surface.makeImageSnapshot().encodeToData()?.bytes?.let {
        thumbnailPath.writeBytes(it)
    }
}

fun getThumbnailPath(path: Path): Path {
    return path.resolveSibling("thumbnail_${path.fileName}")
}
