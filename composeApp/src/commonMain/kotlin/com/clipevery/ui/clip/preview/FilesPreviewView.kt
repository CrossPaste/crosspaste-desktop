package com.clipevery.ui.clip.preview

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clipevery.clip.item.ClipFiles
import com.clipevery.clip.item.ClipImages
import com.clipevery.dao.clip.ClipData
import com.clipevery.utils.FileExtUtils.canPreviewImage
import java.nio.file.Path
import kotlin.io.path.extension

@Composable
fun FilesPreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {
        val clipFilePaths = (it as ClipFiles).getFilePaths()

        val clipImages: List<Path>? =
            (
                clipData.getClipAppearItems()
                    .firstOrNull { images -> images is ClipImages } as? ClipImages
            )
                ?.getFilePaths()

        ClipSpecificPreviewContentView({
            LazyRow(modifier = Modifier.fillMaxSize()) {
                items(clipFilePaths.size) { index ->
                    val filepath = clipFilePaths[index]
                    if (canPreviewImage(filepath.extension)) {
                        SingleImagePreviewView(filepath)
                    } else {
                        SingleFilePreviewView(filepath, getImagePath(index, clipFilePaths, clipImages))
                    }
                    if (index != clipFilePaths.size - 1) {
                        Spacer(modifier = Modifier.size(10.dp))
                    }
                }
            }
        }, { hover ->
            ClipMenuView(clipData = clipData, hover)
        })
    }
}

fun getImagePath(
    index: Int,
    clipFilePaths: List<Path>,
    clipImages: List<Path>?,
): Path? {
    return clipImages?.let {
        if (clipFilePaths.size == clipImages.size) {
            return clipImages[index]
        } else {
            null
        }
    }
}
