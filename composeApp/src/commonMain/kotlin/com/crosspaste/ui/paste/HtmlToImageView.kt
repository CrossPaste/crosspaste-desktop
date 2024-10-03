package com.crosspaste.ui.paste

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
import androidx.compose.ui.unit.sp
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.crosspaste.image.coil.Html2ImageItem
import com.crosspaste.image.coil.ImageLoaders
import okio.Path
import org.koin.compose.koinInject

@Composable
fun HtmlToImageView(
    modifier: Modifier = Modifier,
    html2ImagePath: Path,
    htmlText: String,
    preview: Boolean,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val density = LocalDensity.current
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()

    SubcomposeAsyncImage(
        modifier = modifier,
        model =
            ImageRequest.Builder(platformContext)
                .data(Html2ImageItem(html2ImagePath, preview, density))
                .crossfade(true)
                .build(),
        imageLoader = imageLoaders.html2ImageLoader,
        alignment = alignment,
        contentScale = contentScale,
        contentDescription = "Html 2 Image",
        content = {
            when (this.painter.state.collectAsState().value) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error,
                -> {
                    Text(
                        text = htmlText,
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

                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        },
    )
}
