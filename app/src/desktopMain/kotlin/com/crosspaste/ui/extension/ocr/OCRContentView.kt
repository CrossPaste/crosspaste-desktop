package com.crosspaste.ui.extension.ocr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import com.crosspaste.app.AppSize
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.i18n.Language
import com.crosspaste.image.OCRModule
import com.crosspaste.module.DownloadState
import com.crosspaste.module.ModuleDownloadManager
import com.crosspaste.module.ocr.DesktopOCRModule.Companion.getTrainedDataName
import com.crosspaste.module.ocr.DesktopOCRModule.Companion.splitOcrLanguages
import com.crosspaste.ui.base.SettingButton
import com.crosspaste.ui.base.SettingOutlineButton
import com.crosspaste.ui.settings.SettingItemsTitleView
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun OCRContentView() {
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val moduleDownloadManager = koinInject<ModuleDownloadManager>()
    val ocrModule = koinInject<OCRModule>()
    val languages = copywriter.getAllLanguages()

    val config by configManager.config.collectAsState()

    val ocrLanguageList = splitOcrLanguages(config.ocrLanguage)

    val downloadState by moduleDownloadManager.getModuleDownloadState(ocrModule.moduleId).collectAsState()

    val scope = rememberCoroutineScope()

    Column(
        modifier =
            Modifier
                .fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .wrapContentSize()
                    .clip(tinyRoundedCornerShape),
        ) {
            if (ocrLanguageList.isNotEmpty()) {
                SettingItemsTitleView("language_module_loaded")
            }
            LazyColumn {
                items(ocrLanguageList.size) { index ->
                    val ocrLanguage = ocrLanguageList[index]
                    val language = languages.find { getTrainedDataName(it.abridge) == ocrLanguage }
                    if (language != null) {
                        LoadedLanguageItem(
                            index = index + 1,
                            language = language,
                        ) {
                            scope.launch {
                                ocrModule.removeLanguage(language.abridge)
                            }
                        }
                    }
                }
            }
        }

        if (ocrLanguageList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(medium))
        }

        Column(
            modifier =
                Modifier
                    .wrapContentSize()
                    .clip(tinyRoundedCornerShape),
        ) {
            SettingItemsTitleView("language_module_not_loaded")

            LazyColumn {
                items(languages.size) { index ->
                    val language = languages[index]
                    val ocrLanguage = ocrLanguageList.find { it == getTrainedDataName(language.abridge) }
                    if (ocrLanguage == null) {
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
    val appSize = koinInject<AppSize>()
    val copywriter = koinInject<GlobalCopywriter>()

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(appSize.settingsItemHeight),
        shape = RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = tiny4X),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(tiny))
            Text(
                text = language.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.weight(1f))
            SettingOutlineButton(
                onClick = onRemoveClick,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(copywriter.getText("remove"))
            }
        }
    }
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
    val appSize = koinInject<AppSize>()
    val copywriter = koinInject<GlobalCopywriter>()
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(appSize.settingsItemHeight),
        shape = RectangleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = tiny4X),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.name,
                style = MaterialTheme.typography.bodyLarge,
            )

            Box(
                modifier =
                    Modifier
                        .weight(1f)
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(tiny),
            ) {
                when (state) {
                    null, is DownloadState.Idle, is DownloadState.Cancelled -> {
                        SettingButton(
                            onClick = onDownloadClick,
                        ) {
                            Text(copywriter.getText("download"))
                        }
                    }

                    is DownloadState.Downloading -> {
                        SettingOutlineButton(
                            onClick = onCancelClick,
                        ) {
                            Text(copywriter.getText("cancel"))
                        }
                    }

                    is DownloadState.Completed -> {
                        SettingOutlineButton(
                            onClick = onDeleteClick,
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                        ) {
                            Text(copywriter.getText("delete"))
                        }
                        SettingOutlineButton(
                            onClick = onLoadClick,
                        ) {
                            Text(copywriter.getText("load"))
                        }
                    }

                    is DownloadState.Failed -> {
                        SettingOutlineButton(
                            onClick = onDownloadClick,
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                        ) {
                            Text(copywriter.getText("retry"))
                        }
                    }
                }
            }
        }
    }
}
