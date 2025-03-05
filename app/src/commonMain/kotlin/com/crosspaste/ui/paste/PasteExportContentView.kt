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
import com.crosspaste.app.FileSelectionMode
import com.crosspaste.config.ConfigManager
import com.crosspaste.db.paste.PasteType.Companion.COLOR_TYPE
import com.crosspaste.db.paste.PasteType.Companion.FILE_TYPE
import com.crosspaste.db.paste.PasteType.Companion.HTML_TYPE
import com.crosspaste.db.paste.PasteType.Companion.IMAGE_TYPE
import com.crosspaste.db.paste.PasteType.Companion.RTF_TYPE
import com.crosspaste.db.paste.PasteType.Companion.TEXT_TYPE
import com.crosspaste.db.paste.PasteType.Companion.URL_TYPE
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteExportParam
import com.crosspaste.paste.PasteExportService
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.color
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.htmlOrRtf
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.text
import com.crosspaste.ui.settings.SettingsText
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.MB
import com.crosspaste.utils.getFileUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun PasteExportContentView() {
    val appWindowManager = koinInject<AppWindowManager>()
    val configManager = koinInject<ConfigManager>()
    val pasteExportService = koinInject<PasteExportService>()
    val fileUtils = getFileUtils()
    val coroutineScope = rememberCoroutineScope()

    // State for type filters
    val typeFilterState = rememberTypeFilterState()

    // State for additional filters
    var favoritesSelected by remember { mutableStateOf(false) }
    var sizeFilterSelected by remember { mutableStateOf(false) }
    var maxFileSize by remember { mutableStateOf(configManager.config.maxSyncFileSize) }

    // Export progress state
    var progressing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
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
                // Type filters section
                TypeFiltersSection(typeFilterState)

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
                        appWindowManager = appWindowManager,
                        typeFilterState = typeFilterState,
                        favoritesSelected = favoritesSelected,
                        sizeFilterSelected = sizeFilterSelected,
                        maxFileSize = maxFileSize,
                        fileUtils = fileUtils,
                        pasteExportService = pasteExportService,
                        coroutineScope = coroutineScope,
                        onProgressChange = {
                            progress = it
                            if (progress == 1f) {
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
 * Data class to hold the state of all paste type filters
 */
private data class TypeFilterState(
    var allTypesSelected: Boolean,
    var textTypeSelected: Boolean,
    var urlTypeSelected: Boolean,
    var htmlTypeSelected: Boolean,
    var fileTypeSelected: Boolean,
    var imageTypeSelected: Boolean,
    var rtfTypeSelected: Boolean,
    var colorTypeSelected: Boolean,
)

/**
 * Remember the state of type filters
 */
@Composable
private fun rememberTypeFilterState(): TypeFilterState {
    var allTypesSelected by remember { mutableStateOf(true) }
    var textTypeSelected by remember { mutableStateOf(true) }
    var urlTypeSelected by remember { mutableStateOf(true) }
    var htmlTypeSelected by remember { mutableStateOf(true) }
    var fileTypeSelected by remember { mutableStateOf(true) }
    var imageTypeSelected by remember { mutableStateOf(true) }
    var rtfTypeSelected by remember { mutableStateOf(true) }
    var colorTypeSelected by remember { mutableStateOf(true) }

    return TypeFilterState(
        allTypesSelected = allTypesSelected,
        textTypeSelected = textTypeSelected,
        urlTypeSelected = urlTypeSelected,
        htmlTypeSelected = htmlTypeSelected,
        fileTypeSelected = fileTypeSelected,
        imageTypeSelected = imageTypeSelected,
        rtfTypeSelected = rtfTypeSelected,
        colorTypeSelected = colorTypeSelected,
    )
}

/**
 * Section displaying all type filter checkboxes
 */
@Composable
private fun TypeFiltersSection(state: TypeFilterState) {
    PasteTypeCheckbox(
        type = "all_types",
        selected = state.allTypesSelected,
        onSelectedChange = { state.allTypesSelected = it },
    )

    if (!state.allTypesSelected) {
        PasteTypeWithIconCheckbox(
            type = "text",
            icon = text(),
            selected = state.textTypeSelected,
            onSelectedChange = { state.textTypeSelected = it },
        )

        PasteTypeWithIconCheckbox(
            type = "link",
            icon = link(),
            selected = state.urlTypeSelected,
            onSelectedChange = { state.urlTypeSelected = it },
        )

        PasteTypeWithIconCheckbox(
            type = "html",
            icon = htmlOrRtf(),
            selected = state.htmlTypeSelected,
            onSelectedChange = { state.htmlTypeSelected = it },
        )

        PasteTypeWithIconCheckbox(
            type = "file",
            icon = file(),
            selected = state.fileTypeSelected,
            onSelectedChange = { state.fileTypeSelected = it },
        )

        PasteTypeWithIconCheckbox(
            type = "image",
            icon = image(),
            selected = state.imageTypeSelected,
            onSelectedChange = { state.imageTypeSelected = it },
        )

        PasteTypeWithIconCheckbox(
            type = "rtf",
            icon = htmlOrRtf(),
            selected = state.rtfTypeSelected,
            onSelectedChange = { state.rtfTypeSelected = it },
        )

        PasteTypeWithIconCheckbox(
            type = "color",
            icon = color(),
            selected = state.colorTypeSelected,
            onSelectedChange = { state.colorTypeSelected = it },
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
        startPadding = 48.dp,
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
                    .height(5.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
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
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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
    appWindowManager: AppWindowManager,
    typeFilterState: TypeFilterState,
    favoritesSelected: Boolean,
    sizeFilterSelected: Boolean,
    maxFileSize: Long,
    fileUtils: FileUtils,
    pasteExportService: PasteExportService,
    coroutineScope: CoroutineScope,
    onProgressChange: (Float) -> Unit,
    onExportStart: () -> Unit,
) {
    appWindowManager.openFileChooser(FileSelectionMode.DIRECTORY_ONLY) { path ->
        val types = collectSelectedTypes(typeFilterState)

        val pasteExportParam =
            PasteExportParam(
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

        coroutineScope.launch(ioDispatcher) {
            withContext(mainDispatcher) {
                onExportStart()
            }
            pasteExportService.export(pasteExportParam) { progress ->
                coroutineScope.launch(mainDispatcher) {
                    onProgressChange(progress)
                }
            }
        }
    }
}

/**
 * Collect all selected paste types
 */
private fun collectSelectedTypes(state: TypeFilterState): MutableSet<Long> {
    val types = mutableSetOf<Long>()

    if (state.allTypesSelected || state.textTypeSelected) {
        types.add(TEXT_TYPE.type.toLong())
    }
    if (state.allTypesSelected || state.urlTypeSelected) {
        types.add(URL_TYPE.type.toLong())
    }
    if (state.allTypesSelected || state.htmlTypeSelected) {
        types.add(HTML_TYPE.type.toLong())
    }
    if (state.allTypesSelected || state.fileTypeSelected) {
        types.add(FILE_TYPE.type.toLong())
    }
    if (state.allTypesSelected || state.imageTypeSelected) {
        types.add(IMAGE_TYPE.type.toLong())
    }
    if (state.allTypesSelected || state.rtfTypeSelected) {
        types.add(RTF_TYPE.type.toLong())
    }
    if (state.allTypesSelected || state.colorTypeSelected) {
        types.add(COLOR_TYPE.type.toLong())
    }

    return types
}

@Composable
fun PasteTypeCheckbox(
    type: String,
    icon: Painter? = null,
    selected: Boolean,
    height: Dp = 30.dp,
    startPadding: Dp = 16.dp,
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
        content?.let {
            Spacer(modifier = Modifier.width(12.dp))
            it()
        }
    }
}
