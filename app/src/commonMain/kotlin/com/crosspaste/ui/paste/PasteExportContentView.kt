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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import com.crosspaste.app.AppFileChooser
import com.crosspaste.config.ConfigManager
import com.crosspaste.db.paste.PasteType.Companion.COLOR_TYPE
import com.crosspaste.db.paste.PasteType.Companion.FILE_TYPE
import com.crosspaste.db.paste.PasteType.Companion.HTML_TYPE
import com.crosspaste.db.paste.PasteType.Companion.IMAGE_TYPE
import com.crosspaste.db.paste.PasteType.Companion.RTF_TYPE
import com.crosspaste.db.paste.PasteType.Companion.TEXT_TYPE
import com.crosspaste.db.paste.PasteType.Companion.URL_TYPE
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteExportParamFactory
import com.crosspaste.paste.PasteExportService
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.color
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.htmlOrRtf
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.text
import com.crosspaste.ui.settings.SettingsText
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import com.crosspaste.utils.MB
import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PasteExportContentView() {
    val appFileChooser = koinInject<AppFileChooser>()
    val configManager = koinInject<ConfigManager>()
    val pasteExportService = koinInject<PasteExportService>()
    val pasteExportParamFactory = koinInject<PasteExportParamFactory>()
    val fileUtils = getFileUtils()

    // State for type filters
    var allTypesSelected by remember { mutableStateOf(true) }
    var textTypeSelected by remember { mutableStateOf(true) }
    var urlTypeSelected by remember { mutableStateOf(true) }
    var htmlTypeSelected by remember { mutableStateOf(true) }
    var fileTypeSelected by remember { mutableStateOf(true) }
    var imageTypeSelected by remember { mutableStateOf(true) }
    var rtfTypeSelected by remember { mutableStateOf(true) }
    var colorTypeSelected by remember { mutableStateOf(true) }

    // State for additional filters
    var favoritesSelected by remember { mutableStateOf(false) }
    var sizeFilterSelected by remember { mutableStateOf(false) }

    val config by configManager.config.collectAsState()

    var maxFileSize by remember { mutableStateOf(config.maxSyncFileSize) }

    // Export progress state
    var progressing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(medium)
                .clip(tinyRoundedCornerShape)
                .background(AppUIColors.generalBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(medium),
            verticalArrangement = Arrangement.spacedBy(tiny),
        ) {
            // Top section with checkboxes
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(small2X),
            ) {
                // Type filters section
                TypeFiltersSection(
                    allTypesSelected = allTypesSelected,
                    onAllTypesSelectedChange = { allTypesSelected = it },
                    textTypeSelected = textTypeSelected,
                    onTextTypeSelectedChange = { textTypeSelected = it },
                    urlTypeSelected = urlTypeSelected,
                    onUrlTypeSelectedChange = { urlTypeSelected = it },
                    htmlTypeSelected = htmlTypeSelected,
                    onHtmlTypeSelectedChange = { htmlTypeSelected = it },
                    fileTypeSelected = fileTypeSelected,
                    onFileTypeSelectedChange = { fileTypeSelected = it },
                    imageTypeSelected = imageTypeSelected,
                    onImageTypeSelectedChange = { imageTypeSelected = it },
                    rtfTypeSelected = rtfTypeSelected,
                    onRtfTypeSelectedChange = { rtfTypeSelected = it },
                    colorTypeSelected = colorTypeSelected,
                    onColorTypeSelectedChange = { colorTypeSelected = it },
                )

                // Additional filters section
                AdditionalFiltersSection(
                    favoritesSelected = favoritesSelected,
                    onFavoritesSelectedChange = { favoritesSelected = it },
                    sizeFilterSelected = sizeFilterSelected,
                    onSizeFilterSelectedChange = { sizeFilterSelected = it },
                    maxFileSize = maxFileSize,
                    onMaxFileSizeChange = { maxFileSize = it },
                )
            }

            // Progress indicator or divider
            ProgressSection(progressing, progress)

            // Export button
            ExportButtonSection(
                progressing = progressing,
                progress = progress,
                enabled = !progressing,
                onClick = {
                    handleExportClick(
                        appFileChooser = appFileChooser,
                        types =
                            collectSelectedTypes(
                                allTypesSelected,
                                textTypeSelected,
                                urlTypeSelected,
                                htmlTypeSelected,
                                fileTypeSelected,
                                imageTypeSelected,
                                rtfTypeSelected,
                                colorTypeSelected,
                            ),
                        favoritesSelected = favoritesSelected,
                        sizeFilterSelected = sizeFilterSelected,
                        maxFileSize = maxFileSize,
                        fileUtils = fileUtils,
                        pasteExportService = pasteExportService,
                        pasteExportParamFactory = pasteExportParamFactory,
                        onProgressChange = {
                            progress = it
                            // 1f means export finished
                            // < 0f means export failed
                            if (progress == 1f || progress < 0f) {
                                progressing = false
                                progress = 0f
                            }
                        },
                        onExportStart = {
                            progress = 0f
                            progressing = true
                        },
                    )
                },
            )
        }
    }
}

/**
 * Section displaying all type filter checkboxes
 */
@Composable
private fun TypeFiltersSection(
    allTypesSelected: Boolean,
    onAllTypesSelectedChange: (Boolean) -> Unit,
    textTypeSelected: Boolean,
    onTextTypeSelectedChange: (Boolean) -> Unit,
    urlTypeSelected: Boolean,
    onUrlTypeSelectedChange: (Boolean) -> Unit,
    htmlTypeSelected: Boolean,
    onHtmlTypeSelectedChange: (Boolean) -> Unit,
    fileTypeSelected: Boolean,
    onFileTypeSelectedChange: (Boolean) -> Unit,
    imageTypeSelected: Boolean,
    onImageTypeSelectedChange: (Boolean) -> Unit,
    rtfTypeSelected: Boolean,
    onRtfTypeSelectedChange: (Boolean) -> Unit,
    colorTypeSelected: Boolean,
    onColorTypeSelectedChange: (Boolean) -> Unit,
) {
    PasteTypeCheckbox(
        type = "all_types",
        selected = allTypesSelected,
        onSelectedChange = onAllTypesSelectedChange,
    )

    if (!allTypesSelected) {
        PasteTypeWithIconCheckbox(
            type = "text",
            icon = text(),
            selected = textTypeSelected,
            onSelectedChange = onTextTypeSelectedChange,
        )

        PasteTypeWithIconCheckbox(
            type = "link",
            icon = link(),
            selected = urlTypeSelected,
            onSelectedChange = onUrlTypeSelectedChange,
        )

        PasteTypeWithIconCheckbox(
            type = "html",
            icon = htmlOrRtf(),
            selected = htmlTypeSelected,
            onSelectedChange = onHtmlTypeSelectedChange,
        )

        PasteTypeWithIconCheckbox(
            type = "file",
            icon = file(),
            selected = fileTypeSelected,
            onSelectedChange = onFileTypeSelectedChange,
        )

        PasteTypeWithIconCheckbox(
            type = "image",
            icon = image(),
            selected = imageTypeSelected,
            onSelectedChange = onImageTypeSelectedChange,
        )

        PasteTypeWithIconCheckbox(
            type = "rtf",
            icon = htmlOrRtf(),
            selected = rtfTypeSelected,
            onSelectedChange = onRtfTypeSelectedChange,
        )

        PasteTypeWithIconCheckbox(
            type = "color",
            icon = color(),
            selected = colorTypeSelected,
            onSelectedChange = onColorTypeSelectedChange,
        )
    }
}

/**
 * Convenience function for paste type checkboxes with icons
 */
@Composable
private fun PasteTypeWithIconCheckbox(
    type: String,
    icon: Painter,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    PasteTypeCheckbox(
        type = type,
        icon = icon,
        selected = selected,
        startPadding = xxxxLarge,
        onSelectedChange = onSelectedChange,
    )
}

/**
 * Section displaying additional filters (favorites and file size)
 */
@Composable
private fun AdditionalFiltersSection(
    favoritesSelected: Boolean,
    onFavoritesSelectedChange: (Boolean) -> Unit,
    sizeFilterSelected: Boolean,
    onSizeFilterSelectedChange: (Boolean) -> Unit,
    maxFileSize: Long,
    onMaxFileSizeChange: (Long) -> Unit,
) {
    PasteTypeCheckbox(
        type = "favorite",
        selected = favoritesSelected,
        onSelectedChange = onFavoritesSelectedChange,
    )

    PasteTypeCheckbox(
        type = "file_size_filter",
        selected = sizeFilterSelected,
        onSelectedChange = onSizeFilterSelectedChange,
    ) {
        if (sizeFilterSelected) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Counter(
                    defaultValue = maxFileSize,
                    unit = MB,
                    rule = { it > 0 },
                ) {
                    onMaxFileSizeChange(it)
                }
            }
        }
    }
}

/**
 * Progress indicator or divider based on export state
 */
@Composable
private fun ProgressSection(
    progressing: Boolean,
    progress: Float,
) {
    if (progressing) {
        LinearProgressIndicator(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(tiny3X)
                    .clip(tiny4XRoundedCornerShape),
            progress = { progress },
        )
    } else {
        HorizontalDivider()
    }
}

/**
 * Export button section
 */
@Composable
private fun ExportButtonSection(
    progressing: Boolean,
    progress: Float,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = medium),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(
            enabled = enabled,
            onClick = onClick,
        ) {
            Text(
                if (progressing) {
                    "${(progress * 100).toInt()}%"
                } else {
                    copywriter.getText("export")
                },
            )
        }
    }
}

/**
 * Handle export button click and directory selection
 */
private fun handleExportClick(
    appFileChooser: AppFileChooser,
    types: Set<Long>,
    favoritesSelected: Boolean,
    sizeFilterSelected: Boolean,
    maxFileSize: Long,
    fileUtils: FileUtils,
    pasteExportService: PasteExportService,
    pasteExportParamFactory: PasteExportParamFactory,
    onProgressChange: (Float) -> Unit,
    onExportStart: () -> Unit,
) {
    appFileChooser.openFileChooserToExport { path ->
        val pasteExportParam =
            pasteExportParamFactory.createPasteExportParam(
                types = types,
                onlyFavorite = favoritesSelected,
                maxFileSize =
                    if (sizeFilterSelected) {
                        fileUtils.bytesSize(maxFileSize)
                    } else {
                        null
                    },
                exportPath = path,
            )

        onExportStart()

        pasteExportService.export(pasteExportParam) { progress ->
            mainCoroutineDispatcher.launch {
                onProgressChange(progress)
            }
        }
    }
}

/**
 * Collect all selected paste types
 */
private fun collectSelectedTypes(
    allTypesSelected: Boolean,
    textTypeSelected: Boolean,
    urlTypeSelected: Boolean,
    htmlTypeSelected: Boolean,
    fileTypeSelected: Boolean,
    imageTypeSelected: Boolean,
    rtfTypeSelected: Boolean,
    colorTypeSelected: Boolean,
): MutableSet<Long> {
    val types = mutableSetOf<Long>()

    if (allTypesSelected || textTypeSelected) {
        types.add(TEXT_TYPE.type.toLong())
    }
    if (allTypesSelected || urlTypeSelected) {
        types.add(URL_TYPE.type.toLong())
    }
    if (allTypesSelected || htmlTypeSelected) {
        types.add(HTML_TYPE.type.toLong())
    }
    if (allTypesSelected || fileTypeSelected) {
        types.add(FILE_TYPE.type.toLong())
    }
    if (allTypesSelected || imageTypeSelected) {
        types.add(IMAGE_TYPE.type.toLong())
    }
    if (allTypesSelected || rtfTypeSelected) {
        types.add(RTF_TYPE.type.toLong())
    }
    if (allTypesSelected || colorTypeSelected) {
        types.add(COLOR_TYPE.type.toLong())
    }

    return types
}

@Composable
fun PasteTypeCheckbox(
    type: String,
    icon: Painter? = null,
    selected: Boolean,
    height: Dp = xxLarge,
    startPadding: Dp = medium,
    onSelectedChange: (Boolean) -> Unit,
    content: (@Composable () -> Unit)? = null,
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
            modifier = Modifier.size(small),
            checked = selected,
            onCheckedChange = onSelectedChange,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = Color.White,
                ),
        )
        val padding = icon?.let { medium } ?: xLarge
        Spacer(modifier = Modifier.width(padding))
        icon?.let {
            Icon(
                modifier = Modifier.size(small),
                painter = icon,
                contentDescription = type,
                tint = color,
            )
            Spacer(modifier = Modifier.width(small2X))
        }
        SettingsText(
            text = copywriter.getText(type),
            color = color,
        )
        content?.let {
            Spacer(modifier = Modifier.width(small2X))
            it()
        }
    }
}
