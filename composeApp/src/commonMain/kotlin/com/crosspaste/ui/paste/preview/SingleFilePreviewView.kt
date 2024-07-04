package com.crosspaste.ui.paste.preview

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.AsyncView
import com.crosspaste.ui.base.LoadIconData
import com.crosspaste.ui.base.LoadImageData
import com.crosspaste.ui.base.SketchBackground
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.base.fileSlash
import com.crosspaste.ui.base.loadIconData
import com.crosspaste.ui.base.loadImageData
import com.crosspaste.utils.getFileUtils
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

    val existFile by remember { mutableStateOf(filePath.toFile().exists()) }

    Row(
        modifier =
            Modifier.onClick {
                uiSupport.browseFile(filePath)
            },
    ) {
        Box(modifier = Modifier.size(100.dp)) {
            SketchBackground(100.dp, 100.dp, 5.dp, MaterialTheme.colors.primary)
            AsyncView(
                key = filePath,
                load = {
                    if (imagePath != null) {
                        loadImageData(imagePath, density)
                    } else {
                        loadIconData(filePath, existFile, density)
                    }
                },
                loadFor = { loadStateData ->
                    if (loadStateData.isSuccess()) {
                        if (loadStateData is LoadImageData) {
                            Image(
                                modifier =
                                    Modifier.size(100.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                painter = loadStateData.toPainterImage.toPainter(),
                                contentDescription = "fileType",
                            )
                        } else if (loadStateData is LoadIconData) {
                            Icon(
                                modifier = Modifier.size(100.dp),
                                painter = loadStateData.toPainterImage.toPainter(),
                                contentDescription = "fileType",
                                tint = MaterialTheme.colors.onBackground,
                            )
                        }
                    } else if (loadStateData.isError()) {
                        Icon(
                            modifier = Modifier.size(100.dp),
                            painter = fileSlash(),
                            contentDescription = "fileType",
                            tint = MaterialTheme.colors.onBackground,
                        )
                    }
                },
            )
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
