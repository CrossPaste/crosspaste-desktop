package com.clipevery.ui.clip

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.AsyncView
import com.clipevery.ui.base.LoadImageData
import com.clipevery.ui.base.createThumbnail
import com.clipevery.ui.base.getThumbnailPath
import com.clipevery.ui.base.image
import com.clipevery.ui.base.imageSlash
import com.clipevery.ui.base.loadImage
import com.clipevery.utils.FileUtils
import java.awt.Desktop
import java.nio.file.Path
import kotlin.io.path.exists

@Composable
fun SingleImagePreviewView(imagePath: Path) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val fileUtils = current.koin.get<FileUtils>()

    val existFile by remember { mutableStateOf(imagePath.exists()) }

    Row(modifier = Modifier.clickable {
        if (Desktop.isDesktopSupported() && existFile) {
            val desktop = Desktop.getDesktop()
            desktop.open(imagePath.toFile())
        }
    }) {
        AsyncView(
            load = {
                val thumbnailPath = getThumbnailPath(imagePath)
                if (!thumbnailPath.exists()) {
                    createThumbnail(imagePath)
                }
                LoadImageData(imagePath, loadImage(thumbnailPath, density))
            },
            loadFor = { loadImageView ->
                if (loadImageView.isSuccess()) {
                    ShowImageView(
                        painter = (loadImageView as LoadImageData).toPainterImage.toPainter(),
                        contentDescription = "${imagePath.fileName}"
                    )
                } else if (loadImageView.isLoading()) {
                    ShowImageView(painter = image(),
                        contentDescription = "${imagePath.fileName}"
                    )
                } else {
                    ShowImageView(painter = imageSlash(),
                        contentDescription = "${imagePath.fileName}"
                    )
                }

                Column(
                    modifier = Modifier.fillMaxHeight()
                        .wrapContentWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = "${copywriter.getText("File_Name")}: ${imagePath.fileName}",
                        color = MaterialTheme.colors.onBackground,
                        style = TextStyle(
                            fontWeight = FontWeight.Light,
                            fontSize = 10.sp
                        )
                    )

                    if (loadImageView is LoadImageData) {
                        val painter = loadImageView.toPainterImage.toPainter()
                        Text(
                            text = "${copywriter.getText("Dimensions")}: " +
                                    "${painter.intrinsicSize.width} x ${painter.intrinsicSize.height}",
                            color = MaterialTheme.colors.onBackground,
                            style = TextStyle(
                                fontWeight = FontWeight.Light,
                                fontSize = 10.sp
                            )
                        )
                    }

                    if (existFile) {
                        Text(
                            text = "${copywriter.getText("Size")}: ${fileUtils.getFileSize(imagePath)}",
                            color = MaterialTheme.colors.onBackground,
                            style = TextStyle(
                                fontWeight = FontWeight.Light,
                                fontSize = 10.sp
                            )
                        )
                    } else {
                        Text(
                            text = copywriter.getText("Missing_File"),
                            color = MaterialTheme.colors.error,
                            style = TextStyle(
                                fontWeight = FontWeight.Normal,
                                color = Color.Red,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun ShowImageView(painter: Painter,
                  contentDescription: String) {
    Image(painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(100.dp)
            .clip(RoundedCornerShape(5.dp))
    )
}
