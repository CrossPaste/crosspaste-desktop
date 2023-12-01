package com.clipevery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.GlobalCopywriter

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SettingsItemUI(title: String,
                   content: @Composable () -> Unit) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    var hover by remember { mutableStateOf(false) }
    val backgroundColor = if (hover) MaterialTheme.colors.secondaryVariant else MaterialTheme.colors.background

    var openSettings by remember { mutableStateOf(false) }

    val languageArrow: ImageVector = if (openSettings) {
        arrowDown()
    } else {
        arrowLeft()
    }

    Column(modifier = Modifier.fillMaxWidth()
        .wrapContentHeight()) {
        Row(modifier = Modifier.fillMaxWidth()
            .background(backgroundColor)
            .padding(0.dp, 10.dp, 0.dp, 10.dp)
            .combinedClickable(interactionSource = MutableInteractionSource(),
                indication = null,
                onClick = {
                    openSettings = !openSettings
                }
            )
            .onPointerEvent(
                eventType = PointerEventType.Enter,
                onEvent = {
                    hover = true
                }
            )
            .onPointerEvent(
                eventType = PointerEventType.Exit,
                onEvent = {
                    hover = false
                }
            )
            ,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier
                    .padding(5.dp, 0.dp, 5.dp, 0.dp)
                    .size(15.dp),
                imageVector = languageArrow,
                contentDescription = null,
                tint = MaterialTheme.colors.onBackground
                )
            Text(text = copywriter.getText(title),
                color = MaterialTheme.colors.onBackground,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                style = TextStyle(fontWeight = FontWeight.Light)
            )
        }

        if (openSettings) {
            content()
        }
    }
}