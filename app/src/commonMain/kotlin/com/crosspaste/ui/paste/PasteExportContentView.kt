package com.crosspaste.ui.paste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
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
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Storage
import com.crosspaste.app.AppFileChooser
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteExportParamFactory
import com.crosspaste.paste.PasteExportService
import com.crosspaste.paste.PasteType.Companion.COLOR_TYPE
import com.crosspaste.paste.PasteType.Companion.FILE_TYPE
import com.crosspaste.paste.PasteType.Companion.HTML_TYPE
import com.crosspaste.paste.PasteType.Companion.IMAGE_TYPE
import com.crosspaste.paste.PasteType.Companion.RTF_TYPE
import com.crosspaste.paste.PasteType.Companion.TEXT_TYPE
import com.crosspaste.paste.PasteType.Companion.URL_TYPE
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.InnerScaffold
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.settings.SettingListItem
import com.crosspaste.ui.settings.SettingListSwitchItem
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.utils.FileUtils
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PasteExportContentView() {
    val appFileChooser = koinInject<AppFileChooser>()
    val configManager = koinInject<CommonConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteExportService = koinInject<PasteExportService>()
    val pasteExportParamFactory = koinInject<PasteExportParamFactory<Any>>()
    val fileUtils = getFileUtils()

    // State for type filters
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

    val themeExt = LocalThemeExtState.current

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
                            handleExportClick(
                                appFileChooser = appFileChooser,
                                types =
                                    collectSelectedTypes(
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
                    ) {
                        Text(
                            if (progressing) {
                                "${(progress * 100).toInt()}%"
                            } else {
                                copywriter.getText("export")
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
                    .fillMaxSize()
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
                SectionHeader("select_export_type")
            }

            item {
                SettingSectionCard {
                    SettingListItem(
                        title = "text",
                        icon = themeExt.textTypeIconData,
                        trailingContent = {
                            Checkbox(
                                checked = textTypeSelected,
                                onCheckedChange = { checked ->
                                    textTypeSelected = checked
                                },
                            )
                        },
                        onClick = {
                            textTypeSelected = !textTypeSelected
                        },
                    )
                    HorizontalDivider()
                    SettingListItem(
                        title = "link",
                        icon = themeExt.urlTypeIconData,
                        trailingContent = {
                            Checkbox(
                                checked = urlTypeSelected,
                                onCheckedChange = { checked ->
                                    urlTypeSelected = checked
                                },
                            )
                        },
                        onClick = {
                            urlTypeSelected = !urlTypeSelected
                        },
                    )
                    HorizontalDivider()
                    SettingListItem(
                        title = "html",
                        icon = themeExt.htmlTypeIconData,
                        trailingContent = {
                            Checkbox(
                                checked = htmlTypeSelected,
                                onCheckedChange = { checked ->
                                    htmlTypeSelected = checked
                                },
                            )
                        },
                        onClick = {
                            htmlTypeSelected = !htmlTypeSelected
                        },
                    )
                    HorizontalDivider()
                    SettingListItem(
                        title = "file",
                        icon = themeExt.fileTypeIconData,
                        trailingContent = {
                            Checkbox(
                                checked = fileTypeSelected,
                                onCheckedChange = { checked ->
                                    fileTypeSelected = checked
                                },
                            )
                        },
                        onClick = {
                            fileTypeSelected = !fileTypeSelected
                        },
                    )
                    HorizontalDivider()
                    SettingListItem(
                        title = "image",
                        icon = themeExt.imageTypeIconData,
                        trailingContent = {
                            Checkbox(
                                checked = imageTypeSelected,
                                onCheckedChange = { checked ->
                                    imageTypeSelected = checked
                                },
                            )
                        },
                        onClick = {
                            imageTypeSelected = !imageTypeSelected
                        },
                    )
                    HorizontalDivider()
                    SettingListItem(
                        title = "rtf",
                        icon = themeExt.rtfTypeIconData,
                        trailingContent = {
                            Checkbox(
                                checked = rtfTypeSelected,
                                onCheckedChange = { checked ->
                                    rtfTypeSelected = checked
                                },
                            )
                        },
                        onClick = {
                            rtfTypeSelected = !rtfTypeSelected
                        },
                    )
                    HorizontalDivider()
                    SettingListItem(
                        title = "color",
                        icon = themeExt.colorTypeIconData,
                        trailingContent = {
                            Checkbox(
                                checked = colorTypeSelected,
                                onCheckedChange = { checked ->
                                    colorTypeSelected = checked
                                },
                            )
                        },
                        onClick = {
                            colorTypeSelected = !colorTypeSelected
                        },
                    )
                }
            }

            item {
                SectionHeader("advanced_filtering", topPadding = medium)
            }

            item {
                SettingSectionCard {
                    SettingListSwitchItem(
                        title = "export_favorites_only",
                        checked = favoritesSelected,
                        onCheckedChange = { favoritesSelected = it },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                    SettingListItem(
                        title = "export_favorites_only",
                        icon = IconData(MaterialSymbols.Rounded.Storage, themeExt.amberIconColor),
                        trailingContent = {
                            Counter(
                                defaultValue = maxFileSize,
                                unit = "MB",
                                rule = { it >= 0 },
                            ) {
                                maxFileSize = it
                            }
                        },
                    )
                }
            }
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
    pasteExportParamFactory: PasteExportParamFactory<Any>,
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
    textTypeSelected: Boolean,
    urlTypeSelected: Boolean,
    htmlTypeSelected: Boolean,
    fileTypeSelected: Boolean,
    imageTypeSelected: Boolean,
    rtfTypeSelected: Boolean,
    colorTypeSelected: Boolean,
): MutableSet<Long> {
    val types = mutableSetOf<Long>()

    if (textTypeSelected) {
        types.add(TEXT_TYPE.type.toLong())
    }
    if (urlTypeSelected) {
        types.add(URL_TYPE.type.toLong())
    }
    if (htmlTypeSelected) {
        types.add(HTML_TYPE.type.toLong())
    }
    if (fileTypeSelected) {
        types.add(FILE_TYPE.type.toLong())
    }
    if (imageTypeSelected) {
        types.add(IMAGE_TYPE.type.toLong())
    }
    if (rtfTypeSelected) {
        types.add(RTF_TYPE.type.toLong())
    }
    if (colorTypeSelected) {
        types.add(COLOR_TYPE.type.toLong())
    }

    return types
}
