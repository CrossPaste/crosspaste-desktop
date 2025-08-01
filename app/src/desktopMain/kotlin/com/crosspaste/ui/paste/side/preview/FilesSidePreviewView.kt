package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.db.paste.PasteData
import com.crosspaste.image.coil.FileExtItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.FileIcon
import com.crosspaste.ui.base.FileSlashIcon
import com.crosspaste.ui.base.FolderIcon
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.chevronLeft
import com.crosspaste.ui.base.chevronRight
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.compose.koinInject

@Composable
fun FilesSidePreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteFiles::class)?.let { pasteFiles ->
        if (pasteFiles.count > 0) {
            val imageLoaders = koinInject<ImageLoaders>()
            val platformContext = koinInject<PlatformContext>()
            val userDataPathProvider = koinInject<UserDataPathProvider>()

            val showFileCount = pasteFiles.getFilePaths(userDataPathProvider).size

            val fileUtils = getFileUtils()

            var index by remember(pasteData.id) { mutableStateOf(0) }

            val mutex by remember(pasteData.id) { mutableStateOf(Mutex()) }

            val coroutineScope = rememberCoroutineScope()

            val filePath = pasteFiles.getFilePaths(userDataPathProvider)[index]
            val existFile by remember(filePath) {
                mutableStateOf(fileUtils.existFile(filePath))
            }
            val isFile by remember(filePath) {
                mutableStateOf(pasteFiles.fileInfoTreeMap[filePath.name]!!.isFile())
            }

            var hover by remember { mutableStateOf(false) }

            SidePasteLayoutView(
                pasteData = pasteData,
                pasteBottomContent = {
                    BottomGradient(filePath.name)
                },
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = xxxLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    SubcomposeAsyncImage(
                        modifier = Modifier.size(gigantic),
                        model =
                            ImageRequest
                                .Builder(platformContext)
                                .data(FileExtItem(filePath))
                                .crossfade(true)
                                .build(),
                        imageLoader = imageLoaders.fileExtImageLoader,
                        contentDescription = "fileType",
                        content = {
                            when (
                                this.painter.state
                                    .collectAsState()
                                    .value
                            ) {
                                is AsyncImagePainter.State.Loading,
                                is AsyncImagePainter.State.Error,
                                -> {
                                    val modifier = Modifier.size(gigantic)
                                    if (existFile) {
                                        if (isFile) {
                                            FileIcon(modifier)
                                        } else {
                                            FolderIcon(modifier)
                                        }
                                    } else {
                                        FileSlashIcon(modifier)
                                    }
                                }
                                else -> {
                                    SubcomposeAsyncImageContent()
                                }
                            }
                        },
                    )

                    if (showFileCount > 1 && hover) {
                        Row(modifier = Modifier.fillMaxWidth().height(xxLarge).padding(horizontal = small3X)) {
                            PasteIconButton(
                                size = large,
                                onClick = {
                                    coroutineScope.launch {
                                        mutex.withLock {
                                            val currentIndex = index - 1
                                            index =
                                                if (currentIndex < 0) {
                                                    pasteFiles.count.toInt() - 1
                                                } else {
                                                    currentIndex
                                                }
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .background(Color.Transparent, CircleShape),
                            ) {
                                Icon(
                                    modifier = Modifier.size(large),
                                    painter = chevronLeft(),
                                    contentDescription = "chevronLeft",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            PasteIconButton(
                                size = large,
                                onClick = {
                                    coroutineScope.launch {
                                        mutex.withLock {
                                            val currentIndex = index + 1
                                            index =
                                                if (currentIndex >= pasteFiles.count) {
                                                    0
                                                } else {
                                                    currentIndex
                                                }
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .background(Color.Transparent, CircleShape),
                            ) {
                                Icon(
                                    modifier = Modifier.size(large),
                                    painter = chevronRight(),
                                    contentDescription = "chevronRight",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
