package com.crosspaste.ui.extension.ocr

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.i18n.Language
import com.crosspaste.image.OCRModule
import com.crosspaste.module.DownloadState
import com.crosspaste.module.ModuleDownloadManager
import com.crosspaste.module.ocr.DesktopOCRModule.Companion.getTrainedDataName
import com.crosspaste.module.ocr.DesktopOCRModule.Companion.splitOcrLanguages
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.base.AlertCard
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OCRContentView() {
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val moduleDownloadManager = koinInject<ModuleDownloadManager>()
    val notificationManager = koinInject<NotificationManager>()
    val ocrModule = koinInject<OCRModule>()
    val allLanguages = copywriter.getAllLanguages()

    val config by configManager.config.collectAsState()

    val ocrLanguageList = splitOcrLanguages(config.ocrLanguage)

    val downloadState by moduleDownloadManager.getModuleDownloadState(ocrModule.moduleId).collectAsState()

    val scope = rememberCoroutineScope()

    LaunchedEffect(config.ocrLanguage) {
        if (config.ocrLanguage.isEmpty()) {
            notificationManager.sendNotification(
                title = { it.getText("ocr_no_language_loaded") },
                messageType = MessageType.Warning,
                duration = null,
            )
        }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        if (ocrLanguageList.isNotEmpty()) {
            item {
                AlertCard(
                    title = copywriter.getText("ocr_language_module_order_notice"),
                    messageType = MessageType.Info,
                )
            }

            item {
                SectionHeader("language_module_loaded", topPadding = medium)
            }

            item {
                SettingSectionCard {
                    val languages =
                        ocrLanguageList.mapNotNull { ocrLanguage ->
                            allLanguages.find { getTrainedDataName(it.abridge) == ocrLanguage }
                        }

                    languages
                        .withIndex()
                        .forEach { (index, language) ->
                            LoadedLanguageItem(
                                index = index + 1,
                                language = language,
                            ) {
                                scope.launch {
                                    ocrModule.removeLanguage(language.abridge)
                                }
                            }
                            if (index < ocrLanguageList.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                }
            }
        }

        item {
            SectionHeader("language_module_not_loaded", topPadding = medium)
        }

        item {
            SettingSectionCard {
                val languages =
                    allLanguages.filter { language ->
                        val ocrLanguage = ocrLanguageList.find { it == getTrainedDataName(language.abridge) }
                        ocrLanguage == null
                    }

                languages.withIndex().forEach { (index, language) ->

                    val downloadState = downloadState.fileStates[language.abridge]
                    LanguageItem(
                        language = language,
                        state = downloadState,
                        onDownloadClick = {
                            ocrModule.createDownloadTask(language.abridge)?.let { task ->
                                moduleDownloadManager.downloadFile(task)
                            }
                        },
                        onCancelClick = {
                            moduleDownloadManager.cancelDownload(language.abridge)
                        },
                        onDeleteClick = {
                            moduleDownloadManager.removeDownload(
                                moduleId = "OCR",
                                taskId = language.abridge,
                            )
                        },
                        onLoadClick = {
                            scope.launch {
                                ocrModule.addLanguage(language.abridge)
                            }
                        },
                    )

                    if (index < languages.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun LoadedLanguageItem(
    index: Int,
    language: Language,
    onRemoveClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    ListItem(
        modifier =
            Modifier.height(huge),
        headlineContent = {
            Text(
                text = language.name,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingContent = {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        trailingContent = {
            Button(
                onClick = onRemoveClick,
                colors =
                    buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(copywriter.getText("remove"))
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun LanguageItem(
    language: Language,
    state: DownloadState?,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onLoadClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()

    ListItem(
        modifier =
            Modifier.height(huge),
        headlineContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = medium),
                contentAlignment = Alignment.Center,
            ) {
                if (state is DownloadState.Downloading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LinearProgressIndicator(
                            progress = state.progress.progress,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(large2X),
                            strokeCap = StrokeCap.Round,
                        )
                        Text(
                            text = "${(state.progress.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        leadingContent = {
            Text(
                text = language.name,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tiny),
            ) {
                when (state) {
                    null, is DownloadState.Idle, is DownloadState.Cancelled -> {
                        FilledTonalButton(
                            onClick = onDownloadClick,
                        ) {
                            Text(copywriter.getText("download"))
                        }
                    }

                    is DownloadState.Downloading -> {
                        TextButton(
                            onClick = onCancelClick,
                        ) {
                            Text(copywriter.getText("cancel"))
                        }
                    }

                    is DownloadState.Completed -> {
                        Button(
                            onClick = onDeleteClick,
                            colors =
                                buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                        ) {
                            Text(copywriter.getText("delete"))
                        }
                        Button(
                            onClick = onLoadClick,
                        ) {
                            Text(copywriter.getText("load"))
                        }
                    }

                    is DownloadState.Failed -> {
                        Button(
                            onClick = onDownloadClick,
                            colors =
                                buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                        ) {
                            Text(copywriter.getText("retry"))
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
