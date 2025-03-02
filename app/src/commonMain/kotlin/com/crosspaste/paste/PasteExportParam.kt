package com.crosspaste.paste

import com.crosspaste.db.paste.PasteData
import com.crosspaste.db.paste.PasteType
import okio.Path

data class PasteExportParam(
    val textType: Boolean,
    val linkType: Boolean,
    val htmlType: Boolean,
    val fileType: Boolean,
    val imageType: Boolean,
    val rtfType: Boolean,
    val colorType: Boolean,
    val onlyFavorite: Boolean,
    val maxFileSize: Long,
    val exportPath: Path,
) {
    fun filterPasteData(pasteData: PasteData): Boolean {
        if (onlyFavorite && !pasteData.favorite) {
            return false
        }
        return when (PasteType.fromType(pasteData.pasteType)) {
            PasteType.TEXT_TYPE -> textType
            PasteType.URL_TYPE -> linkType
            PasteType.HTML_TYPE -> htmlType
            PasteType.FILE_TYPE -> fileType
            PasteType.IMAGE_TYPE -> imageType
            PasteType.RTF_TYPE -> rtfType
            PasteType.COLOR_TYPE -> colorType
            else -> false
        }
    }
}
