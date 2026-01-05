package com.crosspaste.ui.paste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import com.crosspaste.app.AppFileChooser
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.paste.PasteImportParamFactory
import com.crosspaste.paste.PasteImportService
import com.crosspaste.ui.base.AlertCard
import com.crosspaste.ui.base.InnerScaffold
import com.crosspaste.ui.theme.AppUISize.enormous
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.tiny4XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xLargeRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import okio.Path
import org.koin.compose.koinInject

@Composable
fun PasteImportContentView() {
    val appFileChooser = koinInject<AppFileChooser>()
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteImportParamFactory = koinInject<PasteImportParamFactory>()
    val pasteImportService = koinInject<PasteImportService>()

    var importPath by remember { mutableStateOf<Path?>(null) }

    var progressing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    InnerScaffold(
        bottomBar = {
            Column {
                if (progressing) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LinearProgressIndicator(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(tiny3X)
                                    .padding(horizontal = tiny)
                                    .clip(tiny4XRoundedCornerShape),
                            progress = { progress },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !progressing,
                        onClick = {
                            importPath?.let {
                                val importParam = pasteImportParamFactory.createPasteImportParam(it)
                                progress = 0f
                                progressing = true

                                pasteImportService.import(importParam) { currentProgress ->
                                    progress = currentProgress
                                    if (progress == 1f || progress < 0f) {
                                        progressing = false
                                        progress = 0f
                                    }
                                }
                            }
                        },
                    ) {
                        Text(
                            if (progressing) {
                                "${(progress * 100).toInt()}%"
                            } else {
                                copywriter.getText("import")
                            },
                            style =
                                if (progressing) {
                                    MaterialTheme.typography.bodyMedium
                                        .copy(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                        )
                    }
                }
            }
        },
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom =
                            if (!progressing) {
                                huge
                            } else {
                                huge + medium
                            },
                    ),
            verticalArrangement = Arrangement.spacedBy(tiny),
        ) {
            item {
                val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .drawWithContent {
                                drawContent()

                                val strokeWidth = tiny4X.toPx()
                                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                val borderColor = borderColor

                                val halfStroke = strokeWidth / 2
                                drawRoundRect(
                                    color = borderColor,
                                    topLeft = Offset(halfStroke, halfStroke),
                                    size =
                                        Size(
                                            width = size.width - strokeWidth,
                                            height = size.height - strokeWidth,
                                        ),
                                    style =
                                        Stroke(
                                            width = strokeWidth,
                                            pathEffect = dashEffect,
                                        ),
                                    cornerRadius = CornerRadius(xLarge.toPx()),
                                )
                            },
                    shape = xLargeRoundedCornerShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(medium),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            modifier = Modifier.size(enormous),
                            shape = RoundedCornerShape(xxLarge),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.padding(medium),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Spacer(modifier = Modifier.height(medium))
                        importPath?.let {
                            Text(
                                text = it.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 5,
                                textAlign = TextAlign.Center,
                            )
                        } ?: run {
                            Text(copywriter.getText("select_import_file"), style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(modifier = Modifier.height(tiny))
                        Button(onClick = {
                            appFileChooser.openFileChooserToImport { path ->
                                importPath = path as? Path
                            }
                        }) {
                            Text(copywriter.getText("browse_files"))
                        }
                    }
                }
            }

            item {
                AlertCard(
                    title = copywriter.getText("import_data_merge_notice"),
                    messageType = MessageType.Info,
                )
            }
        }
    }
}
