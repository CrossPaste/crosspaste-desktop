package com.clipevery.ui.clip

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.item.ClipFile
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.file
import com.clipevery.ui.base.folder
import com.clipevery.utils.FileExtUtils.getExtImage
import com.clipevery.utils.FileUtils

@Composable
fun FilePreviewView(clipData: ClipData) {
    clipData.getClipItem(ClipFile::class)?.let {
        val current = LocalKoinApplication.current
        val copywriter = current.koin.get<GlobalCopywriter>()
        val fileUtils = current.koin.get<FileUtils>()

        val imageBitmap: ImageBitmap? = remember(it.getExtension()) {
            getExtImage(it.getExtension())
        }

        val isFile: Boolean = remember(it) {
            it.isFile
        }

        val fileSize = remember(it) {
            fileUtils.formatBytes(fileUtils.getFileSize(it.getFilePath()))
        }

        Row(modifier = Modifier.fillMaxSize()
            .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                imageBitmap?.let {
                    Image(
                        modifier = Modifier.size(100.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        bitmap = it,
                        contentDescription = "fileType"
                    )
                } ?: run {
                    Icon(
                        modifier = Modifier.size(100.dp),
                        imageVector = if (isFile) file() else folder(),
                        contentDescription = "fileType",
                        tint = MaterialTheme.colors.onBackground
                    )
                }

                Column(
                    modifier = Modifier.fillMaxHeight()
                        .wrapContentWidth()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = "${copywriter.getText("File_Name")}: ${fileUtils.getFileNameFromRelativePath(it.getFilePath())}",
                        color = MaterialTheme.colors.onBackground,
                        style = TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 10.sp
                        )
                    )

                    Text(
                        text = "${copywriter.getText("Size")}: $fileSize",
                        color = MaterialTheme.colors.onBackground,
                        style = TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.wrapContentWidth()
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    file(),
                    contentDescription = "File",
                    modifier = Modifier.padding(3.dp).size(14.dp),
                    tint = MaterialTheme.colors.onBackground
                )

                Text(
                    text = copywriter.getText("File"),
                    fontFamily = FontFamily.SansSerif,
                    style = TextStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }

}