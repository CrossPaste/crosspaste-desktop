package com.clipevery.ui.clip.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.AsyncView
import com.clipevery.ui.base.ClipIconButton
import com.clipevery.ui.base.LoadIconData
import com.clipevery.ui.base.LoadImageData
import com.clipevery.ui.base.chevronLeft
import com.clipevery.ui.base.chevronRight
import com.clipevery.ui.base.fileSlash
import com.clipevery.ui.base.loadIconData
import com.clipevery.utils.getDateUtils
import com.clipevery.utils.getFileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.path.name

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClipFilesDetailView(
    clipData: ClipData,
    clipFiles: ClipFiles,
) {
    val showFileCount = clipFiles.getFilePaths().size
    if (showFileCount > 0) {
        val current = LocalKoinApplication.current
        val density = LocalDensity.current
        val copywriter = current.koin.get<GlobalCopywriter>()

        val clipAppearItem = clipFiles as ClipAppearItem

        val dateUtils = getDateUtils()
        val fileUtils = getFileUtils()

        var index by remember(clipData.id) { mutableStateOf(0) }

        val mutex by remember(clipData.id) { mutableStateOf(Mutex()) }

        var autoRoll by remember(clipData.id) { mutableStateOf(true) }

        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(clipData.id) {
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

        val filePath = clipFiles.getFilePaths()[index]
        val fileInfoTree = clipFiles.getFileInfoTreeMap()[filePath.fileName.name]!!
        val isFile = fileInfoTree.isFile()

        var hover by remember { mutableStateOf(false) }

        ClipDetailView(
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
                            ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncView(
                            key = filePath,
                            load = {
                                loadIconData(filePath, isFile, density)
                            },
                            loadFor = { loadStateData ->
                                if (loadStateData.isSuccess()) {
                                    if (loadStateData is LoadImageData) {
                                        Image(
                                            modifier = Modifier.size(150.dp),
                                            painter = loadStateData.toPainterImage.toPainter(),
                                            contentDescription = "${filePath.fileName}",
                                        )
                                    } else if (loadStateData is LoadIconData) {
                                        Icon(
                                            modifier = Modifier.size(150.dp),
                                            painter = loadStateData.toPainterImage.toPainter(),
                                            contentDescription = "${filePath.fileName}",
                                            tint = MaterialTheme.colors.onBackground,
                                        )
                                    }
                                } else if (loadStateData.isError()) {
                                    Icon(
                                        modifier = Modifier.size(150.dp),
                                        painter = fileSlash(),
                                        contentDescription = "fileType",
                                        tint = MaterialTheme.colors.onBackground,
                                    )
                                }
                            },
                        )

                        if (showFileCount > 1 && hover) {
                            Row(modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 10.dp)) {
                                ClipIconButton(
                                    size = 18.dp,
                                    onClick = {
                                        coroutineScope.launch {
                                            mutex.withLock {
                                                val currentIndex = index - 1
                                                index =
                                                    if (currentIndex < 0) {
                                                        clipFiles.count.toInt() - 1
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
                                        tint = MaterialTheme.colors.onBackground,
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                ClipIconButton(
                                    size = 18.dp,
                                    onClick = {
                                        coroutineScope.launch {
                                            mutex.withLock {
                                                val currentIndex = index + 1
                                                index =
                                                    if (currentIndex >= clipFiles.count) {
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
                                        tint = MaterialTheme.colors.onBackground,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            detailInfoView = {
                ClipDetailInfoView(
                    indexInfo = if (showFileCount <= 1) null else "(${index + 1}/$showFileCount)",
                    clipData = clipData,
                    items =
                        listOf(
                            ClipDetailInfoItem("File_Name", "${filePath.fileName}"),
                            ClipDetailInfoItem("Type", copywriter.getText("File")),
                            ClipDetailInfoItem("Size", fileUtils.formatBytes(clipAppearItem.size)),
                            ClipDetailInfoItem("Remote", copywriter.getText(if (clipData.remote) "Yes" else "No")),
                            ClipDetailInfoItem(
                                "Date",
                                copywriter.getDate(
                                    dateUtils.convertRealmInstantToLocalDateTime(clipData.createTime),
                                    true,
                                ),
                            ),
                        ),
                )
            },
        )
    }
}
