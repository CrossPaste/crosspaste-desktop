package com.crosspaste.paste

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

        fun fromType(type: Int): PasteType = MAP_TYPES[type] ?: INVALID_TYPE
    }

    fun isInValid(): Boolean = this == INVALID_TYPE

    fun isText(): Boolean = this == TEXT_TYPE

    fun isUrl(): Boolean = this == URL_TYPE

    fun isHtml(): Boolean = this == HTML_TYPE

    fun isFile(): Boolean = this == FILE_TYPE

    fun isImage(): Boolean = this == IMAGE_TYPE

    fun isRtf(): Boolean = this == RTF_TYPE

    fun isColor(): Boolean = this == COLOR_TYPE
}
