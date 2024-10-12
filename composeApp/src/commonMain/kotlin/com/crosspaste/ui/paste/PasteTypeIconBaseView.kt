package com.crosspaste.ui.paste

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.htmlOrRtf
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.question
import com.crosspaste.ui.base.text

@Composable
fun PasteTypeIconBaseView(pasteType: Int): Painter {
    return when (pasteType) {
        PasteType.TEXT -> {
            text()
        }
        PasteType.URL -> {
            link()
        }
        PasteType.HTML,
        PasteType.RTF,
        -> {
            htmlOrRtf()
        }
        PasteType.IMAGE -> {
            image()
        }
        PasteType.FILE -> {
            file()
        }
        else -> {
            question()
        }
    }
}
