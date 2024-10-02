package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.coil.FileExtItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.info.PasteInfos.DATE
import com.crosspaste.info.PasteInfos.FILE_NAME
import com.crosspaste.info.PasteInfos.REMOTE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.PasteInfos.TYPE
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.chevronLeft
import com.crosspaste.ui.base.chevronRight
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.fileSlash
import com.crosspaste.ui.base.folder
import com.crosspaste.utils.getCoilUtils
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PasteFilesDetailView(
    pasteData: PasteData,
    pasteFiles: PasteFiles,
    onDoubleClick: () -> Unit,
) {
    val userDataPathProvider = koinInject<UserDataPathProvider>()

    val showFileCount = pasteFiles.getFilePaths(userDataPathProvider).size
    if (showFileCount > 0) {
        val copywriter = koinInject<GlobalCopywriter>()
        val imageLoaders = koinInject<ImageLoaders>()

        val pasteItem = pasteFiles as PasteItem

        val coilUtils = getCoilUtils()
        val dateUtils = getDateUtils()
        val fileUtils = getFileUtils()

        var index by remember(pasteData.id) { mutableStateOf(0) }

        val mutex by remember(pasteData.id) { mutableStateOf(Mutex()) }

        var autoRoll by remember(pasteData.id) { mutableStateOf(true) }

        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(pasteData.id) {
            while (autoRoll) {
                delay(2000)
                mutex.withLock {
                    val nextIndex = index + 1
                    index =
                        if (nextIndex < showFileCount) {
                            nextIndex
                        } else {
                            autoRoll = false
                            0
                        }
                }
            }
        }

        val filePath = pasteFiles.getFilePaths(userDataPathProvider)[index]
        val fileInfoTree = pasteFiles.getFileInfoTreeMap()[filePath.name]!!
        val file = filePath.toFile()
        val existFile = file.exists()
        val isFile = if (existFile) fileInfoTree.isFile() else null

        var hover by remember { mutableStateOf(false) }

        PasteDetailView(
            detailView = {
                Row(
                    modifier =
                        Modifier.fillMaxSize()
                            .onPointerEvent(
                                eventType = PointerEventType.Enter,
                                onEvent = {
                                    hover = true
                                    autoRoll = false
                                },
                            )
                            .onPointerEvent(
                                eventType = PointerEventType.Exit,
                                onEvent = {
                                    hover = false
                                },
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        onDoubleClick()
                                    },
                                )
                            },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SubcomposeAsyncImage(
                            modifier = Modifier.size(150.dp),
                            model =
                                ImageRequest.Builder(coilUtils.getCoilContext())
                                    .data(FileExtItem(filePath))
                                    .crossfade(true)
                                    .build(),
                            imageLoader = imageLoaders.fileExtImageLoader,
                            contentDescription = "fileType",
                            content = {
                                when (this.painter.state.collectAsState().value) {
                                    is AsyncImagePainter.State.Loading,
                                    is AsyncImagePainter.State.Error,
                                    -> {
                                        Icon(
                                            modifier = Modifier.size(150.dp),
                                            painter = isFile?.let { if (it) file() else folder() } ?: fileSlash(),
                                            contentDescription = "fileType",
                                            tint = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                    else -> {
                                        SubcomposeAsyncImageContent()
                                    }
                                }
                            },
                        )

                        if (showFileCount > 1 && hover) {
                            Row(modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 10.dp)) {
                                PasteIconButton(
                                    size = 18.dp,
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
                                        modifier = Modifier.size(18.dp),
                                        painter = chevronLeft(),
                                        contentDescription = "chevronLeft",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                PasteIconButton(
                                    size = 18.dp,
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
                                        modifier = Modifier.size(18.dp),
                                        painter = chevronRight(),
                                        contentDescription = "chevronRight",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            detailInfoView = {
                PasteDetailInfoView(
                    indexInfo = if (showFileCount <= 1) null else "(${index + 1}/$showFileCount)",
                    pasteData = pasteData,
                    items =
                        listOf(
                            PasteDetailInfoItem(FILE_NAME, filePath.name),
                            PasteDetailInfoItem(TYPE, copywriter.getText("file")),
                            PasteDetailInfoItem(SIZE, fileUtils.formatBytes(pasteItem.size)),
                            PasteDetailInfoItem(REMOTE, copywriter.getText(if (pasteData.remote) "yes" else "no")),
                            PasteDetailInfoItem(
                                DATE,
                                copywriter.getDate(
                                    dateUtils.convertRealmInstantToLocalDateTime(pasteData.createTime),
                                    true,
                                ),
                            ),
                        ),
                )
            },
        )
    }
}
