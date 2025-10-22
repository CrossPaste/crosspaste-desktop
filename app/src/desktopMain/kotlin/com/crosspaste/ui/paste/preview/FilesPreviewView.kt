package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.crosspaste.app.AppSize
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.PasteFileInfoTreeCoordinate
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.utils.extension
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.FilesPreviewView() {
    val filesPasteItem = getPasteItem(FilesPasteItem::class)
    val appSize = koinInject<AppSize>()
    val pasteMenuService = koinInject<DesktopPasteMenuService>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()
    val pasteFilePaths = filesPasteItem.getFilePaths(userDataPathProvider)
    val fileInfoTreeMap = filesPasteItem.fileInfoTreeMap
    val fileUtils = getFileUtils()

    ComplexPreviewContentView {
        items(pasteFilePaths.size) { index ->
            val itemWidthSize =
                if (pasteFilePaths.size > 1) {
                    appSize.mainPasteSize.width / 2
                } else {
                    appSize.mainPasteSize.width
                }
            val filepath = pasteFilePaths[index]
            val fileInfoTree = fileInfoTreeMap[filepath.name]!!
            val isImage by remember(filepath) {
                mutableStateOf(fileUtils.canPreviewImage(filepath.extension))
            }

            PasteContextMenuView(
                items =
                    pasteMenuService.fileMenuItemsProvider(
                        pasteData = pasteData,
                        pasteItem = filesPasteItem,
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
                Spacer(modifier = Modifier.size(tiny))
            }
        }
    }
}
