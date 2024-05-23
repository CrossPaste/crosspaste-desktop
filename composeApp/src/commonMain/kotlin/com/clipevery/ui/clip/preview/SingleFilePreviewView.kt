package com.clipevery.ui.clip.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.SketchBackground
import com.clipevery.ui.base.UISupport
import com.clipevery.ui.base.file
import com.clipevery.ui.base.fileSlash
import com.clipevery.ui.base.folder
import com.clipevery.utils.FileExtUtils
import com.clipevery.utils.getFileUtils
import com.clipevery.utils.getResourceUtils
import io.ktor.util.*
import java.nio.file.Path

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleFilePreviewView(
    filePath: Path,
    imagePath: Path?,
) {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val uiSupport = current.koin.get<UISupport>()

    val fileUtils = getFileUtils()

    val resourceUtils = getResourceUtils()

    val existFile by remember { mutableStateOf(filePath.toFile().exists()) }

    val optionPainter: Painter? =
        if (imagePath != null) {
            resourceUtils.loadPainter(imagePath, density).toPainter()
        } else if (existFile) {
            FileExtUtils.getExtPreviewImagePainter(filePath.extension)
        } else {
            fileSlash()
        }

    Row(
        modifier =
            Modifier.onClick {
                uiSupport.browseFile(filePath)
            },
    ) {
        Box(modifier = Modifier.size(100.dp)) {
            SketchBackground(100.dp, 100.dp, 5.dp, MaterialTheme.colors.primary)
            optionPainter?.let { painter ->
                Image(
                    modifier =
                        Modifier.size(100.dp)
                            .clip(RoundedCornerShape(5.dp)),
                    painter = painter,
                    contentDescription = "fileType",
                )
            } ?: run {
                val isFile: Boolean =
                    remember(filePath) {
                        filePath.toFile().isFile
                    }
                Icon(
                    modifier = Modifier.size(100.dp),
                    painter = if (isFile) file() else folder(),
                    contentDescription = "fileType",
                    tint = MaterialTheme.colors.onBackground,
                )
            }
        }

        Column(
            modifier =
                Modifier.fillMaxHeight()
                    .wrapContentWidth()
                    .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = "${copywriter.getText("File_Name")}: ${filePath.fileName}",
                color = MaterialTheme.colors.onBackground,
                style =
                    TextStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 10.sp,
                    ),
            )

            if (existFile) {
                val fileSize =
                    remember(filePath) {
                        fileUtils.formatBytes(fileUtils.getFileSize(filePath))
                    }

                Text(
                    text = "${copywriter.getText("Size")}: $fileSize",
                    color = MaterialTheme.colors.onBackground,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 10.sp,
                        ),
                )
            } else {
                Text(
                    text = copywriter.getText("Missing_File"),
                    color = MaterialTheme.colors.error,
                    style =
                        TextStyle(
                            fontWeight = FontWeight.Normal,
                            fontSize = 10.sp,
                        ),
                )
            }
        }
    }
}
