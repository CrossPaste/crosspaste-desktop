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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppSize
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.PasteFileInfoTreeCoordinate
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
    pasteData.getPasteItem(FilesPasteItem::class)?.let {
        val appSize = koinInject<AppSize>()
        val pasteMenuService = koinInject<DesktopPasteMenuService>()
        val userDataPathProvider = koinInject<UserDataPathProvider>()
        val pasteFilePaths = it.getFilePaths(userDataPathProvider)
        val fileInfoTree = it.getFileInfoTreeMap()
        val fileUtils = getFileUtils()

        PasteSpecificPreviewContentView(
            backgroundColor = Color.Transparent,
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
                        val itemWidthSize = if (pasteFilePaths.size > 1) appSize.mainPasteSize.width / 2 else appSize.mainPasteSize.width
                        val filepath = pasteFilePaths[index]
                        val fileInfoTree = fileInfoTree[filepath.name]!!
                        val isImage by remember(filepath) { mutableStateOf(fileUtils.canPreviewImage(filepath.extension)) }

                        PasteContextMenuView(
                            items =
                                pasteMenuService.fileMenuItemsProvider(
                                    pasteData = pasteData,
                                    pasteItem = it,
                                    index = index,
                                ),
                        ) {
                            val pasteFileInfoTreeCoordinate =
                                PasteFileInfoTreeCoordinate(
                                    pasteData.getPasteCoordinate(),
                                    filepath,
                                    fileInfoTree,
                                )
                            if (isImage) {
                                SingleImagePreviewView(pasteFileInfoTreeCoordinate, itemWidthSize)
                            } else {
                                SingleFilePreviewView(pasteFileInfoTreeCoordinate, itemWidthSize)
                            }
                        }
                        if (index != pasteFilePaths.size - 1) {
                            Spacer(modifier = Modifier.size(8.dp))
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
