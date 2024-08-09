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
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider

@Composable
fun ImagesPreviewView(pasteData: PasteData) {
    pasteData.getPasteItem()?.let {
        val current = LocalKoinApplication.current
        val userDataPathProvider = current.koin.get<UserDataPathProvider>()

        val pasteFiles = it as PasteFiles

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                val imagePaths = pasteFiles.getFilePaths(userDataPathProvider)
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
