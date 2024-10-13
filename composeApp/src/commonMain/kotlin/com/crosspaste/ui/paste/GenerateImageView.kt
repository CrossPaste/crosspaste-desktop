package com.crosspaste.ui.paste

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.app.AppSize
import com.crosspaste.image.coil.GenerateImageItem
import com.crosspaste.image.coil.ImageLoaders
import okio.Path
import org.koin.compose.koinInject

@Composable
fun GenerateImageView(
    modifier: Modifier = Modifier,
    imagePath: Path,
    text: String,
    preview: Boolean,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val density = LocalDensity.current
    val appSize = koinInject<AppSize>()
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()

    SubcomposeAsyncImage(
        modifier = modifier,
        model =
            ImageRequest.Builder(platformContext)
                .data(GenerateImageItem(imagePath, preview, density))
                .crossfade(true)
                .build(),
        imageLoader = imageLoaders.generateImageLoader,
        alignment = alignment,
        contentScale = contentScale,
        contentDescription = "generate Image to preview",
        content = {
            when (this.painter.state.collectAsState().value) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error,
                -> {
                    Row(
                        modifier =
                            Modifier.size(appSize.searchWindowDetailViewDpSize)
                                .padding(10.dp),
                    ) {
                        Text(
                            text = text,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 4,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            style =
                                TextStyle(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                ),
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
