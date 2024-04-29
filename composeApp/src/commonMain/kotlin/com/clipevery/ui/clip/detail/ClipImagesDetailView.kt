package com.clipevery.ui.clip.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.clipevery.clip.item.ClipFiles
import com.clipevery.ui.base.AsyncView
import com.clipevery.ui.base.LoadImageData
import com.clipevery.ui.base.image
import com.clipevery.ui.base.imageSlash
import com.clipevery.ui.base.loadImage
import kotlinx.coroutines.delay

@Composable
fun ClipImagesDetailView(clipFiles: ClipFiles) {
    if (clipFiles.count > 0) {
        val density = LocalDensity.current
        var index by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(2000)
                val nextIndex = index + 1
                if (nextIndex < clipFiles.count) {
                    delay(1000)
                    index = nextIndex
                } else {
                    index = 0
                    break
                }
            }
        }

        val imagePath = clipFiles.getFilePaths()[index]
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncView(
                key = imagePath,
                load = {
                    LoadImageData(imagePath, loadImage(imagePath, density, thumbnail = false))
                },
                loadFor = { loadImageView ->
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        if (loadImageView.isSuccess()) {
                            val painter = (loadImageView as LoadImageData).toPainterImage.toPainter()
                            val intrinsicSize = painter.intrinsicSize
                            val isLongScreenshot = intrinsicSize.width * 180 < intrinsicSize.height * 100

                            val contentScale =
                                if (isLongScreenshot) {
                                    ContentScale.FillWidth
                                } else {
                                    ContentScale.Fit
                                }

                            val modifier =
                                if (isLongScreenshot) {
                                    val scrollState = rememberScrollState()
                                    Modifier.fillMaxSize().verticalScroll(scrollState)
                                } else {
                                    Modifier.fillMaxSize()
                                }
                            Image(
                                modifier = modifier,
                                painter = painter,
                                contentDescription = "${imagePath.fileName}",
                                contentScale = contentScale,
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
        }
    }
}
