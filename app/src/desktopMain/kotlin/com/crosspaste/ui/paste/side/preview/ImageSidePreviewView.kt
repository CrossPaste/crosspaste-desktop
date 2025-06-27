package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.db.paste.PasteData
import com.crosspaste.image.ImageInfoBuilder
import com.crosspaste.image.coil.ImageItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.info.PasteInfos.DIMENSIONS
import com.crosspaste.info.createPasteInfoWithoutConverter
import com.crosspaste.paste.item.PasteFileCoordinate
import com.crosspaste.paste.item.PasteImages
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.ImageShowMode
import com.crosspaste.ui.base.PasteIconButton
import com.crosspaste.ui.base.TransparentBackground
import com.crosspaste.ui.base.chevronLeft
import com.crosspaste.ui.base.chevronRight
import com.crosspaste.ui.base.imageCompress
import com.crosspaste.ui.base.imageExpand
import com.crosspaste.ui.base.imageSlash
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxLarge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.compose.koinInject

@Composable
fun ImageSidePreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteImages::class)?.let { pasteImages ->
        if (pasteImages.count > 0) {
            val imageLoaders = koinInject<ImageLoaders>()
            val platformContext = koinInject<PlatformContext>()
            val userDataPathProvider = koinInject<UserDataPathProvider>()

            var index by remember(pasteData.id) { mutableStateOf(0) }

            val mutex by remember(pasteData.id) { mutableStateOf(Mutex()) }

            val imagePath = pasteImages.getFilePaths(userDataPathProvider)[index]

            val pasteFileCoordinate = PasteFileCoordinate(pasteData.getPasteCoordinate(), imagePath)

            var hover by remember { mutableStateOf(false) }

            var showMode by remember(pasteData.id) { mutableStateOf(false) }

            var forceMode by remember(pasteData.id) { mutableStateOf(false) }

            val coroutineScope = rememberCoroutineScope()

            SidePasteLayoutView(
                pasteData = pasteData,
                pasteBottomContent = {},
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = Modifier.wrapContentSize()) {
                        SubcomposeAsyncImage(
                            modifier = Modifier.wrapContentSize(),
                            model =
                                ImageRequest.Builder(platformContext)
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

                                            if (!forceMode) {
                                                showMode =
                                                    intrinsicSize.width * 180 < intrinsicSize.height * 100
                                            }

                                            if (showMode) {
                                                val scrollState = rememberScrollState()
                                                imageShowMode =
                                                    ImageShowMode(
                                                        Modifier.fillMaxSize()
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

                    if (pasteImages.count > 1 && hover) {
                        Row(modifier = Modifier.fillMaxWidth().height(xxLarge)) {
                            PasteIconButton(
                                size = large,
                                onClick = {
                                    coroutineScope.launch {
                                        mutex.withLock {
                                            val currentIndex = index - 1
                                            index =
                                                if (currentIndex < 0) {
                                                    pasteImages.count.toInt() - 1
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
                                                if (currentIndex >= pasteImages.count) {
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
        }
    }
}
