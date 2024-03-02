package com.clipevery.ui.clip

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.resource.ClipResourceLoader
import com.clipevery.utils.FileUtils
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SingleImagePreviewView(imagePath: Path) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val fileUtils = current.koin.get<FileUtils>()
    val fileResourceLoader = current.koin.get<ClipResourceLoader>()

    val painter = painterResource(imagePath.absolutePathString(), fileResourceLoader)


    val imageSize = remember(imagePath) {
        fileUtils.formatBytes(fileUtils.getFileSize(imagePath))
    }

    Row {
        Image(
            modifier = Modifier.size(100.dp)
                .clip(RoundedCornerShape(5.dp)),
            painter = painter,
            contentDescription = "Image",
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.fillMaxHeight()
                .wrapContentWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "${copywriter.getText("File_Name")}: ${fileUtils.getFileNameFromRelativePath(imagePath)}",
                color = MaterialTheme.colors.onBackground,
                style = TextStyle(
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 10.sp
                )
            )

            Text(
                text = "${copywriter.getText("Dimensions")}: ${painter.intrinsicSize.width} x ${painter.intrinsicSize.height}",
                color = MaterialTheme.colors.onBackground,
                style = TextStyle(
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 10.sp
                )
            )

            Text(
                text = "${copywriter.getText("Size")}: $imageSize",
                color = MaterialTheme.colors.onBackground,
                style = TextStyle(
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 10.sp
                )
            )
        }
    }
}