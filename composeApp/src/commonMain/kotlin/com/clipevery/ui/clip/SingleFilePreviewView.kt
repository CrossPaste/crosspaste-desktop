package com.clipevery.ui.clip

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.SketchBackground
import com.clipevery.ui.base.file
import com.clipevery.ui.base.folder
import com.clipevery.utils.FileExtUtils
import com.clipevery.utils.FileUtils
import io.ktor.util.*
import java.awt.Desktop
import java.nio.file.Path

@Composable
fun SingleFilePreviewView(filePath: Path) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val fileUtils = current.koin.get<FileUtils>()

    val optionPainter: Painter? = FileExtUtils.getExtPreviewImagePainter(filePath.extension)

    val isFile: Boolean = remember(filePath) {
        filePath.toFile().isFile
    }

    val fileSize = remember(filePath) {
        fileUtils.formatBytes(fileUtils.getFileSize(filePath))
    }

    Box(modifier = Modifier.width(100.dp)
        .height(100.dp)
        .clickable {
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                desktop.browseFileDirectory(filePath.toFile())
            }
        }
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
            text = "${copywriter.getText("File_Name")}: ${filePath.fileName}",
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