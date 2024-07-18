package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.icon.FileExtIconLoader
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.paste.item.PasteImages
import com.crosspaste.utils.FileExtUtils.canPreviewImage
import com.crosspaste.utils.extension
import okio.Path

@Composable
fun FilesPreviewView(pasteData: PasteData) {
    pasteData.getPasteItem()?.let {
        val current = LocalKoinApplication.current
        val fileExtIconLoader = current.koin.get<FileExtIconLoader>()
        val pasteFilePaths = (it as PasteFiles).getFilePaths()

        val pasteImages: List<Path>? =
            (
                pasteData.getPasteAppearItems()
                    .firstOrNull { images -> images is PasteImages } as? PasteImages
            )
                ?.getFilePaths()

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                LazyRow(modifier = Modifier.fillMaxSize()) {
                    items(pasteFilePaths.size) { index ->
                        val filepath = pasteFilePaths[index]
                        if (canPreviewImage(filepath.extension)) {
                            SingleImagePreviewView(filepath)
                        } else {
                            SingleFilePreviewView(
                                filepath,
                                getImagePath(index, pasteFilePaths, pasteImages, fileExtIconLoader),
                            )
                        }
                        if (index != pasteFilePaths.size - 1) {
                            Spacer(modifier = Modifier.size(10.dp))
                        }
                    }
                }
            },
            pasteRightInfo = { toShow ->
                PasteMenuView(pasteData = pasteData, toShow = toShow)
            },
        )
    }
}

fun getImagePath(
    index: Int,
    pasteFilePaths: List<Path>,
    pasteImages: List<Path>?,
    fileExtIconLoader: FileExtIconLoader,
): Path? {
    return pasteFilePaths[index].extension.let {
        fileExtIconLoader.load(it)
    }
}
