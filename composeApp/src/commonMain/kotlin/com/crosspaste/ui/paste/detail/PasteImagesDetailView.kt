package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.dao.paste.PasteData
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteFiles
import com.crosspaste.ui.base.AsyncView
import com.crosspaste.ui.base.LoadImageData
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.chevronLeft
import com.crosspaste.ui.base.chevronRight
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.imageCompress
import com.crosspaste.ui.base.imageExpand
import com.crosspaste.ui.base.imageSlash
import com.crosspaste.ui.base.loadImageData
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PasteImagesDetailView(
    pasteData: PasteData,
    pasteFiles: PasteFiles,
) {
    if (pasteFiles.count > 0) {
        val current = LocalKoinApplication.current
        val density = LocalDensity.current
        val copywriter = current.koin.get<GlobalCopywriter>()

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
                        if (nextIndex < pasteFiles.count) {
                            nextIndex
                        } else {
                            autoRoll = false
                            0
                        }
                }
            }
        }

        val imagePath = pasteFiles.getFilePaths()[index]

        var hover by remember { mutableStateOf(false) }

        var showMode by remember(pasteData.id) { mutableStateOf(false) }

        var forceMode by remember(pasteData.id) { mutableStateOf(false) }

        var imageSize by remember { mutableStateOf<Size?>(null) }

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
                            ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncView(
                            key = imagePath,
                            load = {
                                loadImageData(imagePath, density)
                            },
                            loadFor = { loadImageView ->
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    if (loadImageView.isSuccess()) {
                                        val painter = (loadImageView as LoadImageData).toPainterImage.toPainter()
                                        val intrinsicSize = painter.intrinsicSize

                                        imageSize = intrinsicSize

                                        if (!forceMode) {
                                            showMode = intrinsicSize.width * 180 < intrinsicSize.height * 100
                                        }

                                        val imageShowMode =
                                            if (showMode) {
                                                val scrollState = rememberScrollState()
                                                ImageShowMode(Modifier.fillMaxSize().verticalScroll(scrollState), ContentScale.FillWidth)
                                            } else {
                                                ImageShowMode(Modifier.fillMaxSize(), ContentScale.Fit)
                                            }

                                        Image(
                                            modifier = imageShowMode.modifier,
                                            painter = painter,
                                            contentDescription = imagePath.name,
                                            contentScale = imageShowMode.contentScale,
                                        )
                                    } else if (loadImageView.isLoading()) {
                                        Icon(
                                            painter = image(),
                                            contentDescription = imagePath.name,
                                            modifier = Modifier.fillMaxSize(),
                                            tint = MaterialTheme.colors.onBackground,
                                        )
                                    } else {
                                        Icon(
                                            painter = imageSlash(),
                                            contentDescription = imagePath.name,
                                            modifier = Modifier.fillMaxSize(),
                                            tint = MaterialTheme.colors.onBackground,
                                        )
                                    }
                                }
                            },
                        )

                        if (pasteFiles.count > 1 && hover) {
                            Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
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
                                        tint = MaterialTheme.colors.onBackground,
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
                                        tint = MaterialTheme.colors.onBackground,
                                    )
                                }
                            }
                        }

                        if (hover) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Spacer(modifier = Modifier.weight(1f))
                                Column(modifier = Modifier.width(30.dp).fillMaxHeight()) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Box(
                                        modifier =
                                            Modifier.size(30.dp).background(
                                                color = MaterialTheme.colors.background.copy(alpha = 0.5f),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            modifier =
                                                Modifier.size(20.dp).clickable {
                                                    forceMode = true
                                                    showMode = !showMode
                                                },
                                            painter = if (showMode) imageCompress() else imageExpand(),
                                            contentDescription = "expand or compress image",
                                            tint = MaterialTheme.colors.onBackground,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            detailInfoView = {
                PasteDetailInfoView(
                    indexInfo = if (pasteFiles.count <= 1) null else "(${index + 1}/${pasteFiles.count})",
                    pasteData = pasteData,
                    items =
                        detailInfoItems(
                            imagePath,
                            copywriter,
                            fileUtils,
                            dateUtils,
                            pasteData,
                            imageSize,
                        ),
                )
            },
        )
    }
}

fun detailInfoItems(
    imagePath: Path,
    copywriter: GlobalCopywriter,
    fileUtils: FileUtils,
    dateUtils: DateUtils,
    pasteData: PasteData,
    size: Size?,
): List<PasteDetailInfoItem> {
    val details =
        mutableListOf(
            PasteDetailInfoItem("file_Name", imagePath.name),
            PasteDetailInfoItem("type", copywriter.getText("image")),
            PasteDetailInfoItem("size", fileUtils.formatBytes(pasteData.size)),
            PasteDetailInfoItem("remote", copywriter.getText(if (pasteData.remote) "yes" else "no")),
            PasteDetailInfoItem(
                "date",
                copywriter.getDate(
                    dateUtils.convertRealmInstantToLocalDateTime(pasteData.createTime),
                    true,
                ),
            ),
        )

    size?.let {
        details.add(2, PasteDetailInfoItem("dimensions", "${it.width.toInt()} x ${it.height.toInt()}"))
    }

    return details
}

private data class ImageShowMode(val modifier: Modifier, val contentScale: ContentScale)
