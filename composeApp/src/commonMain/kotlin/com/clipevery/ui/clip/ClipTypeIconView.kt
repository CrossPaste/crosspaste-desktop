package com.clipevery.ui.clip

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.clipevery.dao.clip.ClipType
import com.clipevery.ui.base.feed
import com.clipevery.ui.base.file
import com.clipevery.ui.base.html
import com.clipevery.ui.base.image
import com.clipevery.ui.base.link
import com.clipevery.ui.base.question

@Composable
fun ClipTypeIconView(clipType: Int): Painter {
    return when (clipType) {
        ClipType.TEXT -> {
            feed()
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
