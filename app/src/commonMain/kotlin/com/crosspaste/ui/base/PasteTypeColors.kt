package com.crosspaste.ui.base

import androidx.compose.ui.graphics.Color
import com.crosspaste.db.paste.PasteType

data class PasteTypeColors(
    val colorTypeColor: Color,
    val fileTypeColor: Color,
    val htmlTypeColor: Color,
    val imageTypeColor: Color,
    val rtfTypeColor: Color,
    val textTypeColor: Color,
    val urlTypeColor: Color,
) {
    fun getColor(type: PasteType): Color =
        when (type) {
            PasteType.COLOR_TYPE -> colorTypeColor
            PasteType.FILE_TYPE -> fileTypeColor
            PasteType.HTML_TYPE -> htmlTypeColor
            PasteType.IMAGE_TYPE -> imageTypeColor
            PasteType.RTF_TYPE -> rtfTypeColor
            PasteType.TEXT_TYPE -> textTypeColor
            PasteType.URL_TYPE -> urlTypeColor
            else -> Color.Unspecified
        }
}

val lightSideBarColors =
    PasteTypeColors(
        colorTypeColor = Color(0xFFFFE1E3),
        fileTypeColor = Color(0xFFFBEF92),
        htmlTypeColor = Color(0xFFEFD6FF),
        imageTypeColor = Color(0xFF9FDB91),
        rtfTypeColor = Color(0xFFFFD6C9),
        textTypeColor = Color(0xFFB6DCF4),
        urlTypeColor = Color(0xFFB2DAA8),
    )

val darkSideBarColors =
    PasteTypeColors(
        colorTypeColor = Color(0xFFF9656B),
        fileTypeColor = Color(0xFFE3B220),
        htmlTypeColor = Color(0xFF7158C4),
        imageTypeColor = Color(0xFF005F53),
        rtfTypeColor = Color(0xFFE36A6A),
        textTypeColor = Color(0xFF2C5F9A),
        urlTypeColor = Color(0xFF236952),
    )

val sideIconColors =
    PasteTypeColors(
        colorTypeColor = Color(0xFFFFABAE),
        fileTypeColor = Color(0xFFFDDB72),
        htmlTypeColor = Color(0xFFC68EFD),
        imageTypeColor = Color(0xFF40A578),
        rtfTypeColor = Color(0xFFFF9B9B),
        textTypeColor = Color(0xFF578FCA),
        urlTypeColor = Color(0xFF67AE6E),
    )
