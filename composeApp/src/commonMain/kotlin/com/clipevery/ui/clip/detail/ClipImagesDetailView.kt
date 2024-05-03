package com.clipevery.ui.clip.detail

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.AsyncView
import com.clipevery.ui.base.ClipIconButton
import com.clipevery.ui.base.LoadImageData
import com.clipevery.ui.base.chevronLeft
import com.clipevery.ui.base.chevronRight
import com.clipevery.ui.base.image
import com.clipevery.ui.base.imageCompress
import com.clipevery.ui.base.imageExpand
import com.clipevery.ui.base.imageSlash
import com.clipevery.ui.base.loadImageData
import com.clipevery.utils.getDateUtils
import com.clipevery.utils.getFileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClipImagesDetailView(
    clipData: ClipData,
    clipFiles: ClipFiles,
) {
    if (clipFiles.count > 0) {
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
                        if (nextIndex < clipFiles.count) {
                            nextIndex
                        } else {
                            autoRoll = false
                            0
                        }
                }
            }
        }

        val imagePath = clipFiles.getFilePaths()[index]

        var hover by remember { mutableStateOf(false) }

        var showMode by remember(clipData.id) { mutableStateOf(false) }

        var forceMode by remember(clipData.id) { mutableStateOf(false) }

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
                            key = imagePath,
                            load = {
                                loadImageData(imagePath, density, thumbnail = false)
                            },
                            loadFor = { loadImageView ->
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    if (loadImageView.isSuccess()) {
                                        val painter = (loadImageView as LoadImageData).toPainterImage.toPainter()
                                        val intrinsicSize = painter.intrinsicSize

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
                                            contentDescription = "${imagePath.fileName}",
                                            contentScale = imageShowMode.contentScale,
                                        )
                                    } else if (loadImageView.isLoading()) {
                                        Icon(
                                            painter = image(),
                                            contentDescription = "${imagePath.fileName}",
                                            modifier = Modifier.fillMaxSize(),
                                            tint = MaterialTheme.colors.onBackground,
                                        )
                                    } else {
                                        Icon(
                                            painter = imageSlash(),
                                            contentDescription = "${imagePath.fileName}",
                                            modifier = Modifier.fillMaxSize(),
                                            tint = MaterialTheme.colors.onBackground,
                                        )
                                    }
                                }
                            },
                        )

                        if (clipFiles.count > 1 && hover) {
                            Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                                ClipIconButton(
                                    radius = 18.dp,
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
                                        painter = chevronLeft(),
                                        contentDescription = "chevronLeft",
                                        tint = MaterialTheme.colors.onBackground,
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                ClipIconButton(
                                    radius = 18.dp,
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
                ClipDetailInfoView(
                    indexInfo = if (clipFiles.count <= 1) null else "(${index + 1}/${clipFiles.count})",
                    clipData = clipData,
                    items =
                        listOf(
                            ClipDetailInfoItem("File_Name", "${imagePath.fileName}"),
                            ClipDetailInfoItem("Type", copywriter.getText("Image")),
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

private data class ImageShowMode(val modifier: Modifier, val contentScale: ContentScale)
