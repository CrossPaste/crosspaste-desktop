package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.onClick
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImagesPreviewView(
    pasteData: PasteData,
    onDoubleClick: () -> Unit,
) {
    pasteData.getPasteItem()?.let {
        val current = LocalKoinApplication.current
        val userDataPathProvider = current.koin.get<UserDataPathProvider>()

        val pasteFiles = it as PasteFiles

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                val imagePaths = pasteFiles.getFilePaths(userDataPathProvider)
                LazyRow(
                    modifier =
                        Modifier.fillMaxSize()
                            .onClick(
                                onDoubleClick = onDoubleClick,
                                onClick = {},
                            ),
                ) {
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
