package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.crosspaste.paste.PasteMenuService
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.utils.extension
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun FilesPreviewView(
    pasteData: PasteData,
    onDoubleClick: () -> Unit,
) {
    pasteData.getPasteItem()?.let {
        val pasteMenuService = koinInject<PasteMenuService>()
        val userDataPathProvider = koinInject<UserDataPathProvider>()
        val pasteFilePaths = (it as PasteFiles).getFilePaths(userDataPathProvider)

        val fileUtils = getFileUtils()

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                LazyRow(
                    modifier =
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        onDoubleClick()
                                    },
                                )
                            },
                ) {
                    items(pasteFilePaths.size) { index ->
                        val filepath = pasteFilePaths[index]
                        val isImage by remember(filepath) { mutableStateOf(fileUtils.canPreviewImage(filepath.extension)) }

                        PasteContextMenuView(
                            items =
                                pasteMenuService.fileMenuItemsProvider(
                                    pasteData = pasteData,
                                    pasteItem = it,
                                    index = index,
                                ),
                        ) {
                            if (isImage) {
                                val pasteFileCoordinate =
                                    PasteFileCoordinate(
                                        pasteData.getPasteCoordinate(),
                                        filepath,
                                    )
                                SingleImagePreviewView(pasteFileCoordinate)
                            } else {
                                SingleFilePreviewView(filepath)
                            }
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
