package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.CountBadgeAuto
import com.crosspaste.ui.base.MultiFileIcon
import com.crosspaste.ui.base.SingleFileIcon
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.FilesSidePreviewView() {
    val userDataPathProvider = koinInject<UserDataPathProvider>()

    val filesPasteItem = getPasteItem(FilesPasteItem::class)

    val fileCount by remember(pasteData.id) { mutableStateOf(filesPasteItem.getDirectChildrenCount()) }

    val firstFilePath = filesPasteItem.getFilePaths(userDataPathProvider)[0]

    SidePasteLayoutView(
        pasteBottomContent = {
            BottomGradient(firstFilePath.name)
        },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = xxxLarge),
            contentAlignment = Alignment.Center,
        ) {
            if (fileCount == 1L) {
                SingleFileIcon(
                    filePath = firstFilePath,
                    size = AppUISize.massive,
                )
            } else if (fileCount > 1L) {
                MultiFileIcon(
                    fileList = filesPasteItem.getFilePaths(userDataPathProvider),
                    size = AppUISize.massive,
                )
            }

            if (fileCount > 1L) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = medium, end = medium),
                ) {
                    CountBadgeAuto(count = fileCount)
                }
            }
        }
    }
}
