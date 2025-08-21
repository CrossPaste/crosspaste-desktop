package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.ImageInfo
import com.crosspaste.image.ImageInfoBuilder
import com.crosspaste.image.coil.ImageItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.info.PasteInfos.DATE
import com.crosspaste.info.PasteInfos.DIMENSIONS
import com.crosspaste.info.PasteInfos.FILE_NAME
import com.crosspaste.info.PasteInfos.REMOTE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.PasteInfos.TYPE
import com.crosspaste.info.createPasteInfoWithoutConverter
import com.crosspaste.paste.item.ImagesPasteItem
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.ImageShowMode
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.TransparentBackground
import com.crosspaste.ui.base.chevronLeft
import com.crosspaste.ui.base.chevronRight
import com.crosspaste.ui.base.imageCompress
import com.crosspaste.ui.base.imageExpand
import com.crosspaste.ui.base.imageSlash
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PasteDataScope.PasteImagesDetailView(onDoubleClick: () -> Unit) {
    val imagesPasteItem = getPasteItem(ImagesPasteItem::class)
    if (imagesPasteItem.count > 0) {
        val copywriter = koinInject<GlobalCopywriter>()
        val imageLoaders = koinInject<ImageLoaders>()
        val platformContext = koinInject<PlatformContext>()
        val userDataPathProvider = koinInject<UserDataPathProvider>()

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
                        if (nextIndex < imagesPasteItem.count) {
                            nextIndex
                        } else {
                            autoRoll = false
                            0
                        }
                }
            }
        }

        val imagePath = imagesPasteItem.getFilePaths(userDataPathProvider)[index]

        val pasteFileCoordinate = PasteFileCoordinate(pasteData.getPasteCoordinate(), imagePath)

        var hover by remember { mutableStateOf(false) }

        var showMode by remember(pasteData.id) { mutableStateOf(false) }

        var forceMode by remember(pasteData.id) { mutableStateOf(false) }

        var imageInfo by remember(pasteData.id) { mutableStateOf<ImageInfo?>(null) }

        PasteDetailView(
            detailView = {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .onPointerEvent(
                                eventType = PointerEventType.Enter,
                                onEvent = {
                                    hover = true
                                    autoRoll = false
                                },
                            ).onPointerEvent(
                                eventType = PointerEventType.Exit,
                                onEvent = {
                                    hover = false
                                },
                            ).pointerInput(Unit) {
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
                        Box(modifier = Modifier.wrapContentSize()) {
                            SubcomposeAsyncImage(
                                modifier = Modifier.wrapContentSize(),
                                model =
                                    ImageRequest
                                        .Builder(platformContext)
                                        .data(ImageItem(pasteFileCoordinate, false))
                                        .crossfade(true)
                                        .build(),
                                imageLoader = imageLoaders.userImageLoader,
                                contentDescription = "imageType",
                                contentScale = ContentScale.Crop,
                                content = {
                                    val state = painter.state.collectAsState().value
                                    when (state) {
                                        is AsyncImagePainter.State.Loading -> {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(gigantic),
                                                    strokeWidth = tiny,
                                                )
                                            }
                                        }

                                        is AsyncImagePainter.State.Error -> {
                                            Icon(
                                                painter = imageSlash(),
                                                contentDescription = imagePath.name,
                                                modifier = Modifier.size(gigantic),
                                                tint = MaterialTheme.colorScheme.onBackground,
                                            )
                                        }

                                        else -> {
                                            val intrinsicSize = painter.intrinsicSize
                                            val fileName = imagePath.name
                                            val isSvg = fileName.substringAfterLast(".") == "svg"
                                            var imageShowMode =
                                                ImageShowMode(
                                                    Modifier.wrapContentSize(),
                                                    ContentScale.Fit,
                                                )
                                            if (!isSvg && intrinsicSize.isSpecified) {
                                                val builder = ImageInfoBuilder()
                                                builder.add(
                                                    createPasteInfoWithoutConverter(
                                                        DIMENSIONS,
                                                        "${intrinsicSize.width.toInt()} x ${intrinsicSize.height.toInt()}",
                                                    ),
                                                )

                                                imageInfo = builder.build()

                                                if (!forceMode) {
                                                    showMode = intrinsicSize.width * 180 < intrinsicSize.height * 100
                                                }

                                                if (showMode) {
                                                    val scrollState = rememberScrollState()
                                                    imageShowMode =
                                                        ImageShowMode(
                                                            Modifier
                                                                .fillMaxSize()
                                                                .verticalScroll(scrollState),
                                                            ContentScale.FillWidth,
                                                        )
                                                }
                                            }

                                            TransparentBackground(modifier = Modifier.matchParentSize())

                                            SubcomposeAsyncImageContent(
                                                modifier = imageShowMode.modifier,
                                                contentScale = imageShowMode.contentScale,
                                            )
                                        }
                                    }
                                },
                            )
                        }

                        if (imagesPasteItem.count > 1 && hover) {
                            Row(modifier = Modifier.fillMaxWidth().height(xxLarge)) {
                                PasteIconButton(
                                    size = large,
                                    onClick = {
                                        coroutineScope.launch {
                                            mutex.withLock {
                                                val currentIndex = index - 1
                                                index =
                                                    if (currentIndex < 0) {
                                                        imagesPasteItem.count.toInt() - 1
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
                                                    if (currentIndex >= imagesPasteItem.count) {
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

                        if (hover) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Spacer(modifier = Modifier.weight(1f))
                                Column(modifier = Modifier.width(xxLarge).fillMaxHeight()) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Box(
                                        modifier =
                                            Modifier.size(xxLarge).background(
                                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            modifier =
                                                Modifier.size(large2X).clickable {
                                                    forceMode = true
                                                    showMode = !showMode
                                                },
                                            painter = if (showMode) imageCompress() else imageExpand(),
                                            contentDescription = "expand or compress image",
                                            tint = MaterialTheme.colorScheme.onBackground,
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
                    indexInfo = if (imagesPasteItem.count <= 1) null else "(${index + 1}/${imagesPasteItem.count})",
                    items =
                        DetailInfoItems(
                            imageInfo,
                            imagePath,
                            copywriter,
                            fileUtils,
                        ),
                )
            },
        )
    }
}

@Composable
fun PasteDataScope.DetailInfoItems(
    imageInfo: ImageInfo? = null,
    imagePath: Path,
    copywriter: GlobalCopywriter,
    fileUtils: FileUtils,
): List<PasteDetailInfoItem> {
    val details =
        mutableListOf(
            PasteDetailInfoItem(FILE_NAME, imagePath.name),
            PasteDetailInfoItem(TYPE, copywriter.getText("image")),
            PasteDetailInfoItem(SIZE, fileUtils.formatBytes(pasteData.size)),
            PasteDetailInfoItem(REMOTE, copywriter.getText(if (pasteData.remote) "yes" else "no")),
            PasteDetailInfoItem(
                DATE,
                copywriter.getDate(
                    DateUtils.epochMillisecondsToLocalDateTime(pasteData.createTime),
                ),
            ),
        )

    imageInfo?.let {
        it.map[DIMENSIONS]?.let { pasteInfo ->
            details.add(2, PasteDetailInfoItem(DIMENSIONS, pasteInfo.getTextByCopyWriter(copywriter)))
        }
    }

    return details
}
