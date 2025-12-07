package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.ImagesPreviewView() {
    getPasteItem(ImagesPasteItem::class).let { pasteFiles ->
        val pasteMenuService = koinInject<DesktopPasteMenuService>()
        val userDataPathProvider = koinInject<UserDataPathProvider>()
        val imagePaths = remember(pasteData.id) { pasteFiles.getFilePaths(userDataPathProvider) }
        val pasteCoordinate = remember(pasteData.id) { pasteData.getPasteCoordinate() }

        val appSizeValue = LocalDesktopAppSizeValueState.current

        ComplexPreviewContentView {
            items(imagePaths.size) { index ->
                val itemWidthSize =
                    if (imagePaths.size >
                        1
                    ) {
                        appSizeValue.mainPasteSize.width / 2
                    } else {
                        appSizeValue.mainPasteSize.width
                    }
                val pasteFileCoordinate = PasteFileCoordinate(pasteCoordinate, imagePaths[index])
                PasteContextMenuView(
                    items =
                        pasteMenuService.fileMenuItemsProvider(
                            pasteData = pasteData,
                            pasteItem = pasteFiles,
                            index = index,
                        ),
                ) {
                    SingleImagePreviewView(pasteFileCoordinate, itemWidthSize)
                }
                if (index != imagePaths.size - 1) {
                    Spacer(modifier = Modifier.size(tiny))
                }
            }
        }
    }
}
