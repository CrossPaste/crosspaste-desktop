package com.clipevery.ui.clip.preview

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipData

@Composable
fun ImagesPreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {
        val clipFiles = it as ClipFiles

        ClipSpecificPreviewContentView({
            val imagePaths = clipFiles.getFilePaths()
            LazyRow(modifier = Modifier.fillMaxSize()) {
                items(imagePaths.size) { index ->
                    SingleImagePreviewView(imagePaths[index])
                    if (index != imagePaths.size - 1) {
                        Spacer(modifier = Modifier.size(10.dp))
                    }
                }
            }
        }, {
            ClipMenuView(clipData = clipData)
        })
    }
}
