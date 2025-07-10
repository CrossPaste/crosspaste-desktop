package com.crosspaste.db.paste

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.ui.base.color
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.html
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.question
import com.crosspaste.ui.base.rtf
import com.crosspaste.ui.base.text

data class PasteType(
    val type: Int,
    val priority: Int,
    val name: String,
) {

    companion object {
        private const val INVALID = -1
        private const val TEXT = 0
        private const val URL = 1
        private const val HTML = 2
        private const val FILE = 3
        private const val IMAGE = 4
        private const val RTF = 5
        private const val COLOR = 6

        const val ALL_TYPES = "all_types"

        val INVALID_TYPE = PasteType(INVALID, -1, "invalid")

        val TEXT_TYPE = PasteType(TEXT, 0, "text")

        val URL_TYPE = PasteType(URL, 1, "link")

        val IMAGE_TYPE = PasteType(IMAGE, 2, "image")

        val RTF_TYPE = PasteType(RTF, 3, "rtf")

        val HTML_TYPE = PasteType(HTML, 4, "html")

        val COLOR_TYPE = PasteType(COLOR, 5, "color")

        val FILE_TYPE = PasteType(FILE, 6, "file")

        val TYPES =
            listOf(
                TEXT_TYPE,
                URL_TYPE,
                HTML_TYPE,
                FILE_TYPE,
                IMAGE_TYPE,
                RTF_TYPE,
                COLOR_TYPE,
            )

        val MAP_TYPES =
            mapOf<Int, PasteType>(
                TEXT to TEXT_TYPE,
                URL to URL_TYPE,
                HTML to HTML_TYPE,
                FILE to FILE_TYPE,
                IMAGE to IMAGE_TYPE,
                RTF to RTF_TYPE,
                COLOR to COLOR_TYPE,
            )

        fun fromType(type: Int): PasteType {
            return MAP_TYPES[type] ?: INVALID_TYPE
        }
    }

    fun isInValid(): Boolean {
        return this == INVALID_TYPE
    }

    fun isText(): Boolean {
        return this == TEXT_TYPE
    }

    fun isUrl(): Boolean {
        return this == URL_TYPE
    }

    fun isHtml(): Boolean {
        return this == HTML_TYPE
    }

    fun isFile(): Boolean {
        return this == FILE_TYPE
    }

    fun isImage(): Boolean {
        return this == IMAGE_TYPE
    }

    fun isRtf(): Boolean {
        return this == RTF_TYPE
    }

    fun isColor(): Boolean {
        return this == COLOR_TYPE
    }

    @Composable
    fun IconPainter(): Painter {
        return when (this) {
            TEXT_TYPE -> {
                text()
            }
            URL_TYPE -> {
                link()
            }
            HTML_TYPE -> {
                html()
            }
            RTF_TYPE -> {
                rtf()
            }
            IMAGE_TYPE -> {
                image()
            }
            FILE_TYPE -> {
                file()
            }
            COLOR_TYPE -> {
                color()
            }
            else -> {
                question()
            }
        }
    }

    fun getIconBackgroundColor(): Color {
        return when (this) {
            TEXT_TYPE -> Color(0xFF578FCA)
            URL_TYPE -> Color(0xFF67AE6E)
            HTML_TYPE -> Color(0xFFC68EFD)
            RTF_TYPE -> Color(0xFFFF9B9B)
            IMAGE_TYPE -> Color(0xFF40A578)
            FILE_TYPE -> Color(0xFFFDDB72)
            COLOR_TYPE -> Color(0xFFFFABAE)
            else -> Color(0xFFCFD8DC)
        }
    }

    fun getTitleBackgroundColor(isDarkTheme: Boolean): Color {
        return when (this) {
            TEXT_TYPE  -> if (isDarkTheme) Color(0xFF2C5F9A) else Color(0xFFB6DCF4)
            URL_TYPE   -> if (isDarkTheme) Color(0xFF236952) else Color(0xFFB2DAA8)
            HTML_TYPE  -> if (isDarkTheme) Color(0xFF7158C4) else Color(0xFFEFD6FF)
            RTF_TYPE   -> if (isDarkTheme) Color(0xFFE36A6A) else Color(0xFFFFD6C9)
            IMAGE_TYPE -> if (isDarkTheme) Color(0xFF005F53) else Color(0xFF9FDB91)
            FILE_TYPE  -> if (isDarkTheme) Color(0xFFE3B220) else Color(0xFFFFFBDB)
            COLOR_TYPE -> if (isDarkTheme) Color(0xFFF9656B) else Color(0xFFFFE1E3)
            else       -> if (isDarkTheme) Color(0xFF3C4448) else Color(0xFFFFFFFF)
        }
    }

}