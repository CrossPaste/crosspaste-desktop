package com.clipevery.ui.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

@Composable
fun ClipPreviewView(clipData: ClipData, clipContent: @Composable ClipAppearItem.() -> Unit) {
    clipData.getClipItem()?.let {
        Column(modifier = Modifier.fillMaxWidth()
            .height(150.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()
                .height(120.dp)
            ) {
                it.clipContent()
                Column(modifier = Modifier.fillMaxHeight()) {
                    Row() {

                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()
                .background(color = MaterialTheme.colors.surface)
                .height(30.dp)
            ) {

            }
        }
    }
}

@Composable
fun ClipPreview(clipData: ClipData) {
    if (clipData.preCreate) {
        // todo preCreate
    } else {
        when(clipData.clipType) {
            ClipType.TEXT -> TextPreviewView(clipData)
        }
    }
}