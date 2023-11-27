package com.clipevery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.clipevery.clip.item.ClipItem
import com.clipevery.clip.item.TextClipItem
import compose.icons.TablerIcons
import compose.icons.tablericons.FileText

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClipPreviewItemUI(clipItem: ClipItem) {

    var hover by remember { mutableStateOf(false) }
    val backgroundColor = if (hover) Color(138, 238, 238) else Color(238, 238, 238)

    Row(modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
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
        .background(backgroundColor)) {

        ClipPreviewItemDetailUI(clipItem)
    }
}

@Composable
fun ClipPreviewItemDetailUI(clipItem: ClipItem) {
    if (clipItem is TextClipItem) {
        TextClipItemUI(clipItem)
    }
}

@Composable
fun TextClipItemUI(textClipItem: TextClipItem) {
    Icon(
        imageVector = TablerIcons.FileText,
        contentDescription = "clip text",
        tint = Color.Gray)
    Text(text = textClipItem.text)
}