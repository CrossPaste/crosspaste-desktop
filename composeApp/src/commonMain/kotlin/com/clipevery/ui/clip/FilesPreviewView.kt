package com.clipevery.ui.clip

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.SketchBackground
import com.clipevery.ui.base.file
import com.clipevery.ui.base.folder
import com.clipevery.utils.FileExtUtils.getExtImagePainter
import com.clipevery.utils.FileUtils
import io.ktor.util.*
import java.nio.file.Path

@Composable
fun FilesPreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {
        val current = LocalKoinApplication.current
        val copywriter = current.koin.get<GlobalCopywriter>()
        val clipFiles = it as ClipFiles

        ClipSpecificPreviewContentView(it, {
            val filePaths = clipFiles.getFilePaths()
            LazyRow(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                items(filePaths.size) { index ->
                    FilePreviewView(filePaths[index])
                }
            }
        }, {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    file(),
                    contentDescription = "File",
                    modifier = Modifier.padding(3.dp).size(14.dp),
                    tint = MaterialTheme.colors.onBackground
                )
                Spacer(modifier = Modifier.size(3.dp))
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
        })
    }
}

@Composable
fun FilePreviewView(filePath: Path) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val fileUtils = current.koin.get<FileUtils>()

    val optionPainter: Painter? = getExtImagePainter(filePath.extension)

    val isFile: Boolean = remember(filePath) {
        filePath.toFile().isFile
    }

    val fileSize = remember(filePath) {
        fileUtils.formatBytes(fileUtils.getFileSize(filePath))
    }

    Box(modifier = Modifier.width(100.dp)
        .height(100.dp)
    ) {
        SketchBackground(100.dp, 100.dp, 5.dp, MaterialTheme.colors.primary)
        optionPainter?.let { painter ->
            Image(
                modifier = Modifier.size(100.dp)
                    .clip(RoundedCornerShape(5.dp)),
                painter = painter,
                contentDescription = "fileType"
            )
        } ?: run {
            Icon(
                modifier = Modifier.size(100.dp),
                painter = if (isFile) file() else folder(),
                contentDescription = "fileType",
                tint = MaterialTheme.colors.onBackground
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxHeight()
            .wrapContentWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Text(
            text = "${copywriter.getText("File_Name")}: ${fileUtils.getFileNameFromRelativePath(filePath)}",
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