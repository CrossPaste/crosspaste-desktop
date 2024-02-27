package com.clipevery.ui.clip

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.item.ClipImage
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.image
import com.clipevery.utils.FileUtils

@Composable
fun ImagePreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {

        val current = LocalKoinApplication.current
        val copywriter = current.koin.get<GlobalCopywriter>()
        val fileUtils = current.koin.get<FileUtils>()

        val clipImage = it as ClipImage

        val imageBitmap: ImageBitmap = remember(clipImage) {
            clipImage.getImage()
        }

        val imageSize = remember(clipImage) {
            fileUtils.formatBytes(fileUtils.getFileSize(clipImage.getImagePath()))
        }

        ClipSpecificPreviewContentView(it, {
            Row {
                Image(
                    modifier = Modifier.size(100.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    bitmap = imageBitmap,
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
                        text = "${copywriter.getText("File_Name")}: ${fileUtils.getFileNameFromRelativePath(it.getImagePath())}",
                        color = MaterialTheme.colors.onBackground,
                        style = TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 10.sp
                        )
                    )

                    Text(
                        text = "${copywriter.getText("Dimensions")}: ${imageBitmap.width} x ${imageBitmap.height}",
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
        }, {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    image(),
                    contentDescription = "Image",
                    modifier = Modifier.padding(3.dp).size(14.dp),
                    tint = MaterialTheme.colors.onBackground
                )
                Spacer(modifier = Modifier.size(3.dp))
                Text(
                    text = copywriter.getText("Image"),
                    fontFamily = FontFamily.SansSerif,
                    style = TextStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 10.sp
                    )
                )
            }
        })
    }
}
