package com.crosspaste.image.coil

import androidx.compose.ui.unit.Density
import coil3.key.Keyer
import coil3.request.Options
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.realm.paste.PasteData
import okio.Path

data class Html2ImageItem(val path: Path, val preview: Boolean, val density: Density)

class Html2ImageKeyer : Keyer<Html2ImageItem> {
    override fun key(
        data: Html2ImageItem,
        options: Options,
    ): String {
        return "${data.path}_${data.preview}"
    }
}

data class PasteDataItem(val pasteData: PasteData)

class PasteDataKeyer : Keyer<PasteDataItem> {
    override fun key(
        data: PasteDataItem,
        options: Options,
    ): String {
        return data.pasteData.id.toHexString()
    }
}

data class FileExtItem(val path: Path)

class FileExtKeyer : Keyer<FileExtItem> {
    override fun key(
        data: FileExtItem,
        options: Options,
    ): String {
        return "${data.path}"
    }
}

data class ImageItem(
    val pasteFileCoordinate: PasteFileCoordinate,
    val useThumbnail: Boolean,
)

class ImageKeyer : Keyer<ImageItem> {
    override fun key(
        data: ImageItem,
        options: Options,
    ): String {
        return "${data.pasteFileCoordinate.pasteId}_${data.pasteFileCoordinate.filePath}_${data.useThumbnail}"
    }
}
