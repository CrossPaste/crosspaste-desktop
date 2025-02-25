package com.crosspaste.db.paste

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.ui.base.color
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.htmlOrRtf
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.question
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
            HTML_TYPE,
            RTF_TYPE,
            -> {
                htmlOrRtf()
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
}