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
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.utils.FileExtUtils.canPreviewImage
import com.crosspaste.utils.extension
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesPreviewView(
    pasteData: PasteData,
    onDoubleClick: () -> Unit,
) {
    pasteData.getPasteItem()?.let {
        val userDataPathProvider = koinInject<UserDataPathProvider>()
        val pasteFilePaths = (it as PasteFiles).getFilePaths(userDataPathProvider)

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                LazyRow(
                    modifier =
                        Modifier.fillMaxSize()
                            .onClick(
                                onDoubleClick = onDoubleClick,
                                onClick = {},
                            ),
                ) {
                    items(pasteFilePaths.size) { index ->
                        val filepath = pasteFilePaths[index]
                        if (canPreviewImage(filepath.extension)) {
                            val pasteFileCoordinate =
                                PasteFileCoordinate(
                                    pasteData.getPasteCoordinate(),
                                    filepath,
                                )
                            SingleImagePreviewView(pasteFileCoordinate)
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
