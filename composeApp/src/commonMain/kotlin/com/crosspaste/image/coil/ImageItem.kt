package com.crosspaste.image.coil

import androidx.compose.ui.unit.Density
import coil3.key.Keyer
import coil3.request.Options
import com.crosspaste.realm.paste.PasteData
import okio.Path

data class Html2ImageItem(val path: Path, val density: Density)

class Html2ImageKeyer : Keyer<Html2ImageItem> {
    override fun key(
        data: Html2ImageItem,
        options: Options,
    ): String {
        return data.path.toString()
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
