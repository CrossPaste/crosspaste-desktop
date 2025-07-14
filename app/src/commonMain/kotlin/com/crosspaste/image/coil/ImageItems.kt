package com.crosspaste.image.coil

import coil3.key.Keyer
import coil3.request.Options
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteFileCoordinate
import okio.Path

data class GenerateImageItem(
    val path: Path,
    val preview: Boolean,
    val density: Double,
)

class GenerateImageKeyer : Keyer<GenerateImageItem> {
    override fun key(
        data: GenerateImageItem,
        options: Options,
    ): String = "${data.path}_${data.preview}"
}

data class PasteDataItem(
    val pasteData: PasteData,
)

class PasteDataKeyer : Keyer<PasteDataItem> {
    override fun key(
        data: PasteDataItem,
        options: Options,
    ): String = data.pasteData.id.toString()
}

class PasteDataSourceKeyer : Keyer<PasteDataItem> {
    override fun key(
        data: PasteDataItem,
        options: Options,
    ): String = data.pasteData.source ?: ""
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
