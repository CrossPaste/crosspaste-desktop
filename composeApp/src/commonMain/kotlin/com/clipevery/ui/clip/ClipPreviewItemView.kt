package com.clipevery.ui.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipType
import kotlin.reflect.KClass
import kotlin.reflect.cast

fun <T: Any> ClipData.getClipItem(kclass: KClass<T>): T? {
    return ClipContent.getClipItem(this.clipAppearContent)?.let {
        if (kclass.isInstance(it)) {
            kclass.cast(it)
        } else {
            null
        }
    }
}

fun ClipData.getClipItem(): ClipAppearItem? {
    return ClipContent.getClipItem(this.clipAppearContent)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClipPreviewItemView(clipData: ClipData, clipContent: @Composable ClipAppearItem.() -> Unit) {

    clipData.getClipItem()?.let {

        if (it.getClipType() == ClipType.TEXT) {

            var hover by remember { mutableStateOf(false) }
            val backgroundColor = if (hover) MaterialTheme.colors.secondaryVariant else MaterialTheme.colors.background

            Row(modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
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

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .height(150.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .height(120.dp)
                    ) {
                        it.clipContent()
                        Column(modifier = Modifier.fillMaxHeight()) {
                            Row() {

                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(color = MaterialTheme.colors.surface)
                            .height(30.dp)
                    ) {

                    }
                }
            }
        }
    }
}

@Composable
fun ClipSpecificPreviewItemView(clipData: ClipData) {
    if (clipData.preCreate) {
        // todo preCreate
    } else {
        when(clipData.clipType) {
            ClipType.TEXT -> TextPreviewView(clipData)
        }
    }
}
