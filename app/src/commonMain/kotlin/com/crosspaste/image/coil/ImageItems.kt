package com.crosspaste.image.coil

import coil3.key.Keyer
import coil3.request.Options
import com.crosspaste.paste.item.PasteCoordinate
import com.crosspaste.paste.item.PasteFileCoordinate
import okio.Path

data class GenerateImageItem(
    val path: Path,
)

class GenerateImageKeyer : Keyer<GenerateImageItem> {
    override fun key(
        data: GenerateImageItem,
        options: Options,
    ): String = data.path.toString()
}

data class AppSourceItem(
    val source: String?,
)

data class UrlItem(
    val url: String,
    val pasteCoordinate: PasteCoordinate,
)

class AppSourceKeyer : Keyer<AppSourceItem> {
    override fun key(
        data: AppSourceItem,
        options: Options,
    ): String = data.source ?: ""
}

class UrlKeyer : Keyer<UrlItem> {
    override fun key(
        data: UrlItem,
        options: Options,
    ): String = data.url
}

data class FileExtItem(
    val path: Path,
)

class FileExtKeyer : Keyer<FileExtItem> {
    override fun key(
        data: FileExtItem,
        options: Options,
    ): String = "${data.path}"
}

data class ImageItem(
    val pasteFileCoordinate: PasteFileCoordinate,
    val useThumbnail: Boolean,
)

class ImageKeyer : Keyer<ImageItem> {
    override fun key(
        data: ImageItem,
        options: Options,
    ): String = "${data.pasteFileCoordinate.id}_${data.pasteFileCoordinate.filePath}_${data.useThumbnail}"
}
