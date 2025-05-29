package com.crosspaste.ui.paste

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.image.coil.GenerateImageItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.rendering.RenderingHelper
import com.crosspaste.ui.paste.PasteboardViewProvider.Companion.previewTextStyle
import com.crosspaste.ui.theme.AppUISize.small3X
import okio.Path
import org.koin.compose.koinInject

@Composable
fun GenerateImageView(
    modifier: Modifier = Modifier,
    imagePath: Path,
    text: String,
    preview: Boolean,
    alignment: Alignment = Alignment.Center,
) {
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()
    val renderingHelper = koinInject<RenderingHelper>()

    val density = LocalDensity.current

    SubcomposeAsyncImage(
        modifier = modifier,
        model =
            ImageRequest.Builder(platformContext)
                .data(GenerateImageItem(imagePath, preview, renderingHelper.scale))
                .crossfade(true)
                .build(),
        imageLoader = imageLoaders.generateImageLoader,
        alignment = alignment,
        contentScale = FixedScale(density.density / renderingHelper.scale.toFloat()),
        contentDescription = "generate Image to preview",
        content = {
            when (this.painter.state.collectAsState().value) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error,
                -> {
                    Row(
                        modifier =
                            Modifier.fillMaxSize()
                                .padding(small3X),
                    ) {
                        Text(
                            text = text,
                            maxLines =
                                if (preview) {
                                    4
                                } else {
                                    Int.MAX_VALUE
                                },
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = previewTextStyle,
                        )
                    }
                }

                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        },
    )
}
