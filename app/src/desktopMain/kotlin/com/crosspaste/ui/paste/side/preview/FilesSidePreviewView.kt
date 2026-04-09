package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.FilesPasteItem
import com.crosspaste.paste.item.getFilePaths
import com.crosspaste.paste.item.isInDownloads
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.MultiFileIcon
import com.crosspaste.ui.paste.FileBottomSolid
import com.crosspaste.ui.paste.FileDisplayInfo
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.paste.getFileDisplayInfo
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.FilesSidePreviewView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()

    val filesPasteItem = getPasteItem(FilesPasteItem::class)

    val fileCount = remember(pasteData.id) { filesPasteItem.getDirectChildrenCount() }

    val filePaths = remember(filesPasteItem) { filesPasteItem.getFilePaths(userDataPathProvider) }
    if (filePaths.isEmpty()) {
        return
    }

    val isInDownloads = remember(filesPasteItem) { filesPasteItem.isInDownloads() }

    val fileDisplayInfo by produceState<FileDisplayInfo?>(
        initialValue = null,
        key1 = filePaths,
        key2 = copywriter,
    ) {
        value =
            withContext(ioDispatcher) {
                getFileDisplayInfo(filePaths, copywriter)
            }
    }

    SidePasteLayoutView(
        pasteBottomContent = {
            val subtitle =
                if (isInDownloads) {
                    val inDownloadsText = copywriter.getText("in_downloads")
                    val base = fileDisplayInfo?.subtitle ?: ""
                    if (base.isNotEmpty()) "$inDownloadsText · $base" else inDownloadsText
                } else {
                    fileDisplayInfo?.subtitle ?: ""
                }
            FileBottomSolid(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(huge)
                        .background(AppUIColors.topBackground)
                        .padding(tiny),
                title = fileDisplayInfo?.title,
                subtitle = subtitle,
            )
        },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = huge),
            contentAlignment = Alignment.Center,
        ) {
            MultiFileIcon(
                fileList = filesPasteItem.getFilePaths(userDataPathProvider),
                size = AppUISize.massive,
            )

            if (fileCount > 1L) {
                val label = remember(fileCount) { if (fileCount > 99) "99+" else fileCount.toString() }
                Badge(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = medium, end = medium),
                ) {
                    Text(label)
                }
            }
        }
    }
}
