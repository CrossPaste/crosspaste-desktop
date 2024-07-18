package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.utils.FileExtUtils.canPreviewImage
import com.crosspaste.utils.extension

@Composable
fun FilesPreviewView(pasteData: PasteData) {
    pasteData.getPasteItem()?.let {
        val pasteFilePaths = (it as PasteFiles).getFilePaths()

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                LazyRow(modifier = Modifier.fillMaxSize()) {
                    items(pasteFilePaths.size) { index ->
                        val filepath = pasteFilePaths[index]
                        if (canPreviewImage(filepath.extension)) {
                            SingleImagePreviewView(filepath)
                        } else {
                            SingleFilePreviewView(filepath)
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
