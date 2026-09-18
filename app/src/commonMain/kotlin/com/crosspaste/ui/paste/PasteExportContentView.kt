package com.crosspaste.ui.paste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Sell
import com.composables.icons.materialsymbols.rounded.Storage
import com.crosspaste.app.AppFileChooser
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteExportParamFactory
import com.crosspaste.paste.PasteExportService
import com.crosspaste.paste.PasteType
import com.crosspaste.paste.getIconData
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.InnerScaffold
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.settings.SettingListItem
import com.crosspaste.ui.settings.SettingListSwitchItem
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
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

    var selectedTypes by remember { mutableStateOf(PasteType.TYPES.toSet()) }

    // State for additional filters
    var taggedSelected by remember { mutableStateOf(false) }
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
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !progressing && selectedTypes.isNotEmpty(),
                    onClick = {
                        handleExportClick(
                            appFileChooser = appFileChooser,
                            types = selectedTypes.map { it.type.toLong() }.toSet(),
                            taggedSelected = taggedSelected,
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
        },
    ) { paddingValues ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(tiny),
        ) {
            item {
                SectionHeader("select_export_type")
            }

            item {
                SettingSectionCard {
                    FlowRow(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = medium, vertical = small2X),
                        horizontalArrangement = Arrangement.spacedBy(tiny),
                        verticalArrangement = Arrangement.spacedBy(tiny),
                    ) {
                        PasteType.TYPES.forEach { type ->
                            ExportTypeChip(
                                type = type,
                                label = copywriter.getText(type.name),
                                selected = type in selectedTypes,
                                enabled = !progressing,
                                onToggle = {
                                    selectedTypes =
                                        if (type in selectedTypes) {
                                            selectedTypes - type
                                        } else {
                                            selectedTypes + type
                                        }
                                },
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader("advanced_filtering", topPadding = medium)
            }

            item {
                SettingSectionCard {
                    SettingListSwitchItem(
                        title = "export_tagged_only",
                        icon = IconData(MaterialSymbols.Rounded.Sell, themeExt.greenIconColor),
                        checked = taggedSelected,
                        enabled = !progressing,
                        onCheckedChange = { taggedSelected = it },
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                    SettingListSwitchItem(
                        title = "max_back_up_file_size",
                        icon = IconData(MaterialSymbols.Rounded.Storage, themeExt.amberIconColor),
                        checked = sizeFilterSelected,
                        enabled = !progressing,
                        onCheckedChange = { sizeFilterSelected = it },
                    )
                    if (sizeFilterSelected) {
                        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                        SettingListItem(
                            title = "max_back_up_file_size",
                            icon = IconData(MaterialSymbols.Rounded.Storage, themeExt.amberIconColor),
                            trailingContent = {
                                Counter(
                                    defaultValue = maxFileSize,
                                    unit = "MB",
                                    rule = { it >= 0 },
                                    enabled = !progressing,
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
}

/**
 * A type filter chip tinted with the paste type's own icon palette when selected,
 * so the row reads the same way the type icons do everywhere else in the app.
 */
@Composable
private fun ExportTypeChip(
    type: PasteType,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val iconData = type.getIconData()
    FilterChip(
        modifier = Modifier.height(xxLarge),
        selected = selected,
        onClick = onToggle,
        enabled = enabled,
        shape = CircleShape,
        elevation = null,
        leadingIcon = {
            Icon(
                imageVector = iconData.imageVector,
                contentDescription = null,
                modifier = Modifier.size(large),
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        },
        border =
            if (selected) {
                null
            } else {
                FilterChipDefaults.filterChipBorder(enabled = enabled, selected = false)
            },
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurface,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = iconData.bgColor,
                selectedLabelColor = iconData.color,
                selectedLeadingIconColor = iconData.color,
            ),
    )
}

/**
 * Handle export button click and directory selection
 */
private fun handleExportClick(
    appFileChooser: AppFileChooser,
    types: Set<Long>,
    taggedSelected: Boolean,
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
                onlyTagged = taggedSelected,
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
