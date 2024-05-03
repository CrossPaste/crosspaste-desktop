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
import com.clipevery.utils.FileExtUtils.canPreviewImage
import kotlin.io.path.extension

@Composable
fun FilesPreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {
        val clipFiles = it as ClipFiles

        ClipSpecificPreviewContentView({
            val filePaths = clipFiles.getFilePaths()
            LazyRow(modifier = Modifier.fillMaxSize()) {
                items(filePaths.size) { index ->
                    val filepath = filePaths[index]
                    if (canPreviewImage(filepath.extension)) {
                        SingleImagePreviewView(filepath)
                    } else {
                        SingleFilePreviewView(filepath)
                    }
                    if (index != filePaths.size - 1) {
                        Spacer(modifier = Modifier.size(10.dp))
                    }
                }
            }
        }, {
            ClipMenuView(clipData = clipData)
        })
    }
}
