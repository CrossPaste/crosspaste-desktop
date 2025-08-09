package com.crosspaste.ui.paste

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.PasteType.Companion.COLOR_TYPE
import com.crosspaste.paste.PasteType.Companion.FILE_TYPE
import com.crosspaste.paste.PasteType.Companion.HTML_TYPE
import com.crosspaste.paste.PasteType.Companion.IMAGE_TYPE
import com.crosspaste.paste.PasteType.Companion.RTF_TYPE
import com.crosspaste.paste.PasteType.Companion.TEXT_TYPE
import com.crosspaste.paste.PasteType.Companion.URL_TYPE
import com.crosspaste.ui.base.color
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.html
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.question
import com.crosspaste.ui.base.rtf
import com.crosspaste.ui.base.text

@Composable
fun PasteTypeIconPainter(pasteType: PasteType): Painter =
    when (pasteType) {
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
