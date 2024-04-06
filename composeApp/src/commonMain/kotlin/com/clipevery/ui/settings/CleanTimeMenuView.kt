package com.clipevery.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clipevery.LocalKoinApplication
import com.clipevery.clean.CleanTime
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.MenuItem
import com.clipevery.ui.base.getMenWidth

@Composable
fun CleanTimeMenuView(selectIndex: Int, closeMenu: (Int) -> Unit) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()

    Box(
        modifier = Modifier
            .wrapContentSize()
            .background(Color.Transparent)
            .shadow(15.dp)
    ) {

        val cleanTimeMenuTexts = CleanTime.entries.map { cleanTime ->
            "${cleanTime.quantity} ${copywriter.getText(cleanTime.unit)}"
        }.toTypedArray()

        val maxWidth = getMenWidth(cleanTimeMenuTexts)

        Column(
            modifier = Modifier
                .width(maxWidth + 20.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colors.surface)
        ) {

            cleanTimeMenuTexts.forEachIndexed { index, text ->
                val menuText = if (index == selectIndex) {
                    "$text ✔️"
                } else {
                    text
                }
                MenuItem(menuText) {
                    closeMenu(index)
                }
            }
        }
    }
}