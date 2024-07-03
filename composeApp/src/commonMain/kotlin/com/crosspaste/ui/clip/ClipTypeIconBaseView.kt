package com.crosspaste.ui.clip

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.dao.clip.ClipType
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.html
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.question
import com.crosspaste.ui.base.text

@Composable
fun ClipTypeIconBaseView(clipType: Int): Painter {
    return when (clipType) {
        ClipType.TEXT -> {
            text()
        }
        ClipType.URL -> {
            link()
        }
        ClipType.HTML -> {
            html()
        }
        ClipType.IMAGE -> {
            image()
        }
        ClipType.FILE -> {
            file()
        }
        else -> {
            question()
        }
    }
}
