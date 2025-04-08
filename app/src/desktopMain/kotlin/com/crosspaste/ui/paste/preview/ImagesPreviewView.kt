package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppSize
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.path.UserDataPathProvider
import org.koin.compose.koinInject

@Composable
fun ImagesPreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(ImagesPasteItem::class)?.let { pasteFiles ->
        val appSize = koinInject<AppSize>()
        val pasteMenuService = koinInject<DesktopPasteMenuService>()
        val userDataPathProvider = koinInject<UserDataPathProvider>()
        val imagePaths = pasteFiles.getFilePaths(userDataPathProvider)
        val pasteCoordinate = pasteData.getPasteCoordinate()

        ComplexPreviewContentView(pasteData) {
            items(imagePaths.size) { index ->
                val itemWidthSize = if (imagePaths.size > 1) appSize.mainPasteSize.width / 2 else appSize.mainPasteSize.width
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
                    Spacer(modifier = Modifier.size(8.dp))
                }
            }
        }
    }
}
