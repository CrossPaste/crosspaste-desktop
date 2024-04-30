package com.clipevery.ui.clip.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipData
import com.clipevery.ui.base.AsyncView
import com.clipevery.ui.base.LoadIconData
import com.clipevery.ui.base.LoadImageData
import com.clipevery.ui.base.loadIconData
import kotlinx.coroutines.delay
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
        val density = LocalDensity.current
        var index by remember(clipData.id) { mutableStateOf(0) }

        val mutex by remember(clipData.id) { mutableStateOf(Mutex()) }

        var autoRoll by remember(clipData.id) { mutableStateOf(true) }

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

        Row(
            modifier =
                Modifier.fillMaxSize()
                    .onPointerEvent(
                        eventType = PointerEventType.Enter,
                        onEvent = {
                            hover = true
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
                        }
                    },
                )
            }
        }
    }
}
