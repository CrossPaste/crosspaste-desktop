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

@Composable
fun ImagesPreviewView(pasteData: PasteData) {
    pasteData.getPasteItem()?.let {
        val pasteFiles = it as PasteFiles

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                val imagePaths = pasteFiles.getFilePaths()
                LazyRow(modifier = Modifier.fillMaxSize()) {
                    items(imagePaths.size) { index ->
                        SingleImagePreviewView(imagePaths[index])
                        if (index != imagePaths.size - 1) {
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
