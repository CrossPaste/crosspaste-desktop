package com.crosspaste.ui.paste

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Folder_zip
import com.composables.icons.materialsymbols.rounded.Upload_file
import com.crosspaste.app.AppFileChooser
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.paste.PasteImportParamFactory
import com.crosspaste.paste.PasteImportResult
import com.crosspaste.paste.PasteImportSelection
import com.crosspaste.paste.PasteImportService
import com.crosspaste.ui.LocalSmallSettingItemState
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.AlertCard
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.InnerScaffold
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.enormous
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.GlobalCoroutineScope.mainCoroutineDispatcher
import com.crosspaste.utils.getFileUtils
import kotlinx.coroutines.launch
import okio.Path
import org.koin.compose.koinInject

/** What the last import run did, kept on screen until the next file is chosen. */
private data class ImportOutcome(
    val fileName: String,
    val result: PasteImportResult,
)

@Composable
fun PasteImportContentView() {
    val appFileChooser = koinInject<AppFileChooser>()
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteImportParamFactory = koinInject<PasteImportParamFactory<Any>>()
    val pasteImportSelection = koinInject<PasteImportSelection>()
    val pasteImportService = koinInject<PasteImportService>()

    val selectedPath by pasteImportSelection.path.collectAsState()

    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var outcome by remember { mutableStateOf<ImportOutcome?>(null) }

    val browse = {
        appFileChooser.openFileChooserToImport { path ->
            (path as? Path)?.let {
                pasteImportSelection.select(it)
                outcome = null
            }
        }
    }

    val startImport = {
        selectedPath?.let { path ->
            progress = 0f
            importing = true
            outcome = null
            pasteImportService.import(
                pasteImportParam = pasteImportParamFactory.createPasteImportParam(path),
                updateProgress = { currentProgress ->
                    mainCoroutineDispatcher.launch {
                        progress = currentProgress.coerceIn(0f, 1f)
                    }
                },
                onResult = { result ->
                    mainCoroutineDispatcher.launch {
                        importing = false
                        progress = 0f
                        outcome = ImportOutcome(path.name, result)
                        // Drop the file so a second click cannot import it again.
                        pasteImportSelection.clear()
                    }
                },
            )
        }
        Unit
    }

    InnerScaffold(
        bottomBar = {
            ImportBottomBar(
                importing = importing,
                progress = progress,
                canImport = selectedPath != null,
                onImport = startImport,
            )
        },
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (importing) huge + medium else huge),
            verticalArrangement = Arrangement.spacedBy(medium),
        ) {
            item {
                SettingSectionCard {
                    selectedPath?.let { path ->
                        SelectedFileRow(
                            path = path,
                            enabled = !importing,
                            onChange = browse,
                            onRemove = { pasteImportSelection.clear() },
                        )
                    } ?: EmptyFilePicker(onBrowse = browse)
                }
            }

            outcome?.let { current ->
                item {
                    ImportOutcomeCard(
                        outcome = current,
                        onDismiss = { outcome = null },
                    )
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

@Composable
private fun EmptyFilePicker(onBrowse: () -> Unit) {
    val copywriter = koinInject<GlobalCopywriter>()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = xLarge, horizontal = medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(enormous),
            shape = RoundedCornerShape(xxLarge),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Upload_file,
                contentDescription = null,
                modifier = Modifier.padding(medium),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(medium))
        Text(
            text = copywriter.getText("select_import_file"),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(tiny3X))
        Text(
            text = copywriter.getText("import_file_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(medium))
        Button(onClick = onBrowse) {
            Text(copywriter.getText("browse_files"))
        }
    }
}

@Composable
private fun SelectedFileRow(
    path: Path,
    enabled: Boolean,
    onChange: () -> Unit,
    onRemove: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val themeExt = LocalThemeExtState.current
    val isSmallItem = LocalSmallSettingItemState.current
    val fileUtils = getFileUtils()
    val fileSize = remember(path) { fileUtils.formatBytes(fileUtils.getFileSize(path)) }
    val location = path.parent?.toString() ?: ""

    ListItem(
        headlineContent = {
            Text(
                text = path.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        supportingContent = {
            Text(
                text = "$fileSize · $location",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            IconData(MaterialSymbols.Rounded.Folder_zip, themeExt.amberIconColor).IconContent(isSmallItem)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onChange, enabled = enabled) {
                    Text(copywriter.getText("change"))
                }
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = copywriter.getText("remove"),
                        modifier = Modifier.size(large),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ImportOutcomeCard(
    outcome: ImportOutcome,
    onDismiss: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val (title, messageType) =
        when (val result = outcome.result) {
            is PasteImportResult.Failed -> {
                copywriter.getText("import_fail") to MessageType.Error
            }
            is PasteImportResult.Completed -> {
                when {
                    result.totalCount == 0L -> {
                        copywriter.getText("nothing_to_import") to MessageType.Warning
                    }
                    result.successCount == 0L -> {
                        copywriter.getText("import_fail") to MessageType.Error
                    }
                    result.successCount < result.totalCount -> {
                        copywriter.getText(
                            "import_result_partial",
                            result.successCount,
                            result.totalCount,
                        ) to MessageType.Warning
                    }
                    else -> {
                        copywriter.getText("import_result_all", result.successCount) to MessageType.Success
                    }
                }
            }
        }
    AlertCard(
        title = title,
        message = outcome.fileName,
        messageType = messageType,
        onCancel = onDismiss,
    )
}

@Composable
private fun ImportBottomBar(
    importing: Boolean,
    progress: Float,
    canImport: Boolean,
    onImport: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    Column {
        if (importing) {
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
            enabled = canImport && !importing,
            onClick = onImport,
        ) {
            Text(
                text =
                    if (importing) {
                        copywriter.getText("importing_progress", (progress * 100).toInt())
                    } else {
                        copywriter.getText("import")
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
