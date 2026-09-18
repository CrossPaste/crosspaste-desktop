package com.crosspaste.ui.extension.ocr

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Drag_indicator
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.i18n.Language
import com.crosspaste.module.DownloadState
import com.crosspaste.module.ModuleDownloadManager
import com.crosspaste.module.ocr.DesktopOCRModule
import com.crosspaste.module.ocr.DesktopOCRModule.Companion.getTrainedDataName
import com.crosspaste.module.ocr.DesktopOCRModule.Companion.splitOcrLanguages
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.AlertCard
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

private val ActionPillShape = RoundedCornerShape(small)
private val RowPadding = PaddingValues(horizontal = medium, vertical = tiny)
private val PillPadding = PaddingValues(horizontal = small, vertical = tiny3X)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OCRContentView() {
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val moduleDownloadManager = koinInject<ModuleDownloadManager>()
    val notificationManager = koinInject<NotificationManager>()
    val ocrModule = koinInject<DesktopOCRModule>()
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

    val loadedLanguages =
        ocrLanguageList.mapNotNull { trainedDataName ->
            allLanguages.find { getTrainedDataName(it.abridge) == trainedDataName }
        }
    val notLoadedLanguages = allLanguages.filter { it !in loadedLanguages }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        if (loadedLanguages.isNotEmpty()) {
            item(key = "ocr_alert_notice") {
                AlertCard(
                    title = copywriter.getText("ocr_language_module_order_notice"),
                    messageType = MessageType.Info,
                )
            }

            item(key = "ocr_loaded_header") {
                SectionHeader("language_module_loaded", topPadding = medium)
            }

            item(key = "ocr_loaded_section") {
                LoadedLanguageSection(
                    languages = loadedLanguages,
                    onRemove = { language ->
                        scope.launch {
                            ocrModule.removeLanguage(language.abridge)
                        }
                    },
                    onReorder = { newOrder ->
                        scope.launch {
                            ocrModule.setLanguages(newOrder.mapNotNull { getTrainedDataName(it.abridge) })
                        }
                    },
                )
            }
        }

        if (notLoadedLanguages.isNotEmpty()) {
            item(key = "ocr_not_loaded_header") {
                SectionHeader(
                    "language_module_not_loaded",
                    topPadding = if (loadedLanguages.isNotEmpty()) medium else zero,
                )
            }

            item(key = "ocr_not_loaded_section") {
                SettingSectionCard {
                    notLoadedLanguages.forEachIndexed { index, language ->
                        key(language.abridge) {
                            if (index > 0) {
                                LanguageRowDivider()
                            }
                            LanguageItem(
                                language = language,
                                state = downloadState.fileStates[language.abridge],
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
}

@Composable
private fun LoadedLanguageSection(
    languages: List<Language>,
    onRemove: (Language) -> Unit,
    onReorder: (List<Language>) -> Unit,
) {
    val density = LocalDensity.current
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember(density) { mutableIntStateOf(0) }
    val dividerPx = with(density) { tiny5X.toPx() }
    val rowStepPx = rowHeightPx + dividerPx

    val source = draggedIndex
    // Keep the dragged row inside the card: the card clips its content, so a row
    // pulled past the first or last slot would simply disappear.
    val clampedOffsetY =
        if (source != null && rowStepPx > 0f && languages.isNotEmpty()) {
            dragOffsetY.coerceIn(-source * rowStepPx, (languages.lastIndex - source) * rowStepPx)
        } else {
            0f
        }
    val target =
        if (source != null && rowStepPx > 0f && languages.isNotEmpty()) {
            val shift = (clampedOffsetY / rowStepPx).roundToInt()
            (source + shift).coerceIn(0, languages.lastIndex)
        } else {
            null
        }

    SettingSectionCard {
        languages.forEachIndexed { index, language ->
            key(language.abridge) {
                if (index > 0) {
                    LanguageRowDivider()
                }

                val translationY =
                    when {
                        source == null -> 0f
                        index == source -> clampedOffsetY
                        target != null && source < target && index in (source + 1)..target -> -rowStepPx
                        target != null && source > target && index in target..(source - 1) -> rowStepPx
                        else -> 0f
                    }

                LoadedLanguageItem(
                    index = index + 1,
                    language = language,
                    translationY = translationY,
                    isDragging = index == source,
                    onMeasured = { measured ->
                        if (measured > 0 && rowHeightPx != measured) rowHeightPx = measured
                    },
                    onDragStart = {
                        draggedIndex = index
                        dragOffsetY = 0f
                    },
                    onDragDelta = { delta ->
                        val from = source ?: index
                        if (rowStepPx > 0f && languages.isNotEmpty()) {
                            val min = -from * rowStepPx
                            val max = (languages.lastIndex - from) * rowStepPx
                            dragOffsetY = (dragOffsetY + delta).coerceIn(min, max)
                        } else {
                            dragOffsetY += delta
                        }
                    },
                    onDragEnd = {
                        val from = source
                        val to = target
                        if (from != null && to != null && from != to) {
                            val newList =
                                languages.toMutableList().apply {
                                    add(to, removeAt(from))
                                }
                            onReorder(newList)
                        }
                        draggedIndex = null
                        dragOffsetY = 0f
                    },
                    onRemoveClick = { onRemove(language) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = medium),
        thickness = tiny5X,
        color = AppUIColors.sectionCardBorder,
    )
}

/**
 * One row inside a grouped language card. Rows are transparent so the card's colour shows
 * through; a row being dragged gets its own opaque background and a small shadow so it
 * reads as lifted while it slides over its neighbours. In dark theme, a hairline border is
 * added so the lifted row stays distinct from the dark card ground.
 */
@Composable
private fun LanguageRow(
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val isDark = LocalThemeState.current.isCurrentThemeDark
    val liftedModifier =
        if (isDragging) {
            Modifier
                .shadow(tiny4X, tinyRoundedCornerShape)
                .then(
                    if (isDark) {
                        Modifier.border(tiny5X, AppUIColors.sectionCardBorder, tinyRoundedCornerShape)
                    } else {
                        Modifier
                    },
                ).background(AppUIColors.sectionCardBackground, tinyRoundedCornerShape)
        } else {
            Modifier
        }
    Row(
        modifier =
            modifier
                .then(liftedModifier)
                .fillMaxWidth()
                .heightIn(min = xxxxLarge)
                .padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content,
    )
}

@Composable
private fun ActionPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ActionPillShape,
        color = containerColor,
    ) {
        Box(
            modifier = Modifier.padding(PillPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
fun LoadedLanguageItem(
    index: Int,
    language: Language,
    translationY: Float = 0f,
    isDragging: Boolean = false,
    onMeasured: (Int) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onRemoveClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    LanguageRow(
        modifier =
            Modifier
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer { this.translationY = translationY }
                .onGloballyPositioned { onMeasured(it.size.height) },
        isDragging = isDragging,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(small2X),
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Drag_indicator,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .size(large)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { latestOnDragStart() },
                                onDragEnd = { latestOnDragEnd() },
                                onDragCancel = { latestOnDragEnd() },
                                onDrag = { _, dragAmount -> latestOnDragDelta(dragAmount.y) },
                            )
                        },
            )
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = language.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        ActionPill(
            text = copywriter.getText("remove"),
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
            contentColor = MaterialTheme.colorScheme.error,
            onClick = onRemoveClick,
        )
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
    val copywriter = koinInject<GlobalCopywriter>()
    LanguageRow {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(small2X),
        ) {
            Text(
                text = language.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state is DownloadState.Downloading) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(end = small2X),
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tiny2X),
        ) {
            when (state) {
                null, is DownloadState.Idle, is DownloadState.Cancelled -> {
                    ActionPill(
                        text = copywriter.getText("download"),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onDownloadClick,
                    )
                }

                is DownloadState.Downloading -> {
                    ActionPill(
                        text = copywriter.getText("cancel"),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = onCancelClick,
                    )
                }

                is DownloadState.Completed -> {
                    ActionPill(
                        text = copywriter.getText("delete"),
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.error,
                        onClick = onDeleteClick,
                    )
                    ActionPill(
                        text = copywriter.getText("load"),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onLoadClick,
                    )
                }

                is DownloadState.Failed -> {
                    ActionPill(
                        text = copywriter.getText("retry"),
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.error,
                        onClick = onDownloadClick,
                    )
                }
            }
        }
    }
}
