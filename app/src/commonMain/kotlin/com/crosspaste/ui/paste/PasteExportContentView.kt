package com.crosspaste.ui.paste

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppWindowManager
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteExportParam
import com.crosspaste.paste.PasteExportService
import com.crosspaste.ui.base.color
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.htmlOrRtf
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.text
import com.crosspaste.ui.settings.SettingsText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PasteExportContentView() {
    val appWindowManager = koinInject<AppWindowManager>()
    val configManager = koinInject<ConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()

    val pasteExportService = koinInject<PasteExportService>()

    var allTypesSelected by remember { mutableStateOf(false) }
    var textTypeSelected by remember { mutableStateOf(true) }
    var urlTypeSelected by remember { mutableStateOf(true) }
    var htmlTypeSelected by remember { mutableStateOf(true) }
    var fileTypeSelected by remember { mutableStateOf(true) }
    var imageTypeSelected by remember { mutableStateOf(true) }
    var rtfTypeSelected by remember { mutableStateOf(true) }
    var colorTypeSelected by remember { mutableStateOf(true) }

    var favoritesSelected by remember { mutableStateOf(true) }
    var sizeFilterSelected by remember { mutableStateOf(false) }

    var maxFileSizeFilter by remember { mutableStateOf(configManager.config.maxSyncFileSize) }

    var progressing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    var coroutine = rememberCoroutineScope()

    Box(
        modifier =
            Modifier.fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top section with checkboxes
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PasteTypeCheckbox(
                    type = "all_types",
                    selected = allTypesSelected,
                ) {
                    allTypesSelected = it
                }

                if (!allTypesSelected) {
                    PasteTypeCheckbox(
                        type = "text",
                        icon = text(),
                        selected = textTypeSelected,
                        startPadding = 48.dp,
                    ) {
                        textTypeSelected = it
                    }

                    PasteTypeCheckbox(
                        type = "link",
                        icon = link(),
                        selected = urlTypeSelected,
                        startPadding = 48.dp,
                    ) {
                        urlTypeSelected = it
                    }

                    PasteTypeCheckbox(
                        type = "html",
                        icon = htmlOrRtf(),
                        selected = htmlTypeSelected,
                        startPadding = 48.dp,
                    ) {
                        htmlTypeSelected = it
                    }

                    PasteTypeCheckbox(
                        type = "file",
                        icon = file(),
                        selected = fileTypeSelected,
                        startPadding = 48.dp,
                    ) {
                        fileTypeSelected = it
                    }

                    PasteTypeCheckbox(
                        type = "image",
                        icon = image(),
                        selected = imageTypeSelected,
                        startPadding = 48.dp,
                    ) {
                        imageTypeSelected = it
                    }

                    PasteTypeCheckbox(
                        type = "rtf",
                        icon = htmlOrRtf(),
                        selected = rtfTypeSelected,
                        startPadding = 48.dp,
                    ) {
                        rtfTypeSelected = it
                    }

                    PasteTypeCheckbox(
                        type = "color",
                        icon = color(),
                        selected = colorTypeSelected,
                        startPadding = 48.dp,
                    ) {
                        colorTypeSelected = it
                    }
                }

                PasteTypeCheckbox(
                    type = "favorite",
                    selected = favoritesSelected,
                ) {
                    favoritesSelected = it
                }

                PasteTypeCheckbox(
                    type = "file_size_filter",
                    selected = sizeFilterSelected,
                ) {
                    sizeFilterSelected = it
                }
            }

            if (progressing) {
                LinearProgressIndicator(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(1.5.dp)),
                    progress = { progress },
                )
            } else {
                HorizontalDivider()
            }

            // Bottom section with export button
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    enabled = !progressing,
                    onClick = {

                        coroutine.launch {
                            appWindowManager.openFileChooser(chooseText, currentStoragePath, { path ->

                            }

                            progress = 0f
                            pasteExportService.export(PasteExportParam(
                                textType = allTypesSelected || textTypeSelected,
                                linkType = allTypesSelected || urlTypeSelected,
                                htmlType = allTypesSelected || htmlTypeSelected,
                                fileType = allTypesSelected || fileTypeSelected,
                                imageType = allTypesSelected || imageTypeSelected,
                                rtfType = allTypesSelected || rtfTypeSelected,
                                colorType = allTypesSelected || colorTypeSelected,
                                onlyFavorite = favoritesSelected,
                                sizeFilter = maxFileSizeFilter,
                            ))
                            progressing = true
                        }

                    },
                ) {
                    if (progressing) {
                        Text("${progress.toInt()}%")
                    } else {
                        Text(copywriter.getText("export"))
                    }
                }
            }
        }
    }
}

@Composable
fun PasteTypeCheckbox(
    type: String,
    icon: Painter? = null,
    selected: Boolean,
    height: Dp = 30.dp,
    startPadding: Dp = 16.dp,
    onSelectedChange: (Boolean) -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()

    val color =
        if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(height)
                .padding(start = startPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            modifier = Modifier.size(14.dp),
            checked = selected,
            onCheckedChange = onSelectedChange,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = Color.White,
                ),
        )
        val padding = icon?.let { 16.dp } ?: 24.dp
        Spacer(modifier = Modifier.width(padding))
        icon?.let {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = icon,
                contentDescription = type,
                tint = color,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        SettingsText(
            text = copywriter.getText(type),
            color = color,
        )
    }
}
