package com.crosspaste.ui.paste.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Redo
import com.composables.icons.materialsymbols.rounded.Save
import com.composables.icons.materialsymbols.rounded.Undo
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.TextPasteItem
import com.crosspaste.paste.item.UpdatePasteItemHelper
import com.crosspaste.platform.Platform
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.InnerScaffold
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUIFont.pasteTextStyle
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val MAX_HISTORY_SIZE = 50

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PasteDataScope.PasteTextEditContentView() {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()
    val updatePasteItemHelper = koinInject<UpdatePasteItemHelper>()
    val platform = koinInject<Platform>()

    val scope = rememberCoroutineScope()
    val isMac = remember { platform.isMacos() }

    val textPasteItem = getPasteItem(TextPasteItem::class)
    val originalText = remember(pasteData.id, pasteData.hash) { textPasteItem.text }
    var textValue by remember(pasteData.id, pasteData.hash) { mutableStateOf(originalText) }
    var history by remember(pasteData.id, pasteData.hash) { mutableStateOf(listOf(originalText)) }
    var historyIndex by remember(pasteData.id, pasteData.hash) { mutableStateOf(0) }

    val canUndo = historyIndex > 0
    val canRedo = historyIndex < history.size - 1
    val hasChanges = textValue != originalText && textValue.isNotEmpty()

    fun updateTextWithHistory(newText: String) {
        if (newText == textValue) return
        val newHistory = history.subList(0, historyIndex + 1).toMutableList()
        newHistory.add(newText)
        if (newHistory.size > MAX_HISTORY_SIZE) {
            newHistory.removeAt(0)
        }
        history = newHistory
        historyIndex = newHistory.size - 1
        textValue = newText
    }

    fun undo() {
        if (canUndo) {
            historyIndex -= 1
            textValue = history[historyIndex]
        }
    }

    fun redo() {
        if (canRedo) {
            historyIndex += 1
            textValue = history[historyIndex]
        }
    }

    fun save() {
        if (hasChanges) {
            scope.launch {
                updatePasteItemHelper
                    .updateText(pasteData, textValue, textPasteItem)
                    .onSuccess {
                        notificationManager.sendNotification(
                            title = { copywriter.getText("save_successful") },
                            messageType = MessageType.Success,
                        )
                        appWindowManager.hideBubbleWindow()
                    }.onFailure { error ->
                        notificationManager.sendNotification(
                            title = { copywriter.getText("save_failed") },
                            message = { error.message ?: "" },
                            messageType = MessageType.Error,
                        )
                    }
            }
        }
    }

    InnerScaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(tinyRoundedCornerShape)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        keyEvent.key == Key.S &&
                        (if (isMac) keyEvent.isMetaPressed else keyEvent.isCtrlPressed)
                    ) {
                        save()
                        true
                    } else {
                        false
                    }
                },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            HorizontalFloatingToolbar(
                modifier = Modifier.offset(y = 20.dp),
                expanded = true,
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { save() },
                        containerColor =
                            if (hasChanges) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        contentColor =
                            if (hasChanges) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Save,
                            contentDescription = copywriter.getText("save"),
                        )
                    }
                },
                colors =
                    FloatingToolbarDefaults.standardFloatingToolbarColors(
                        toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
            ) {
                IconButton(
                    onClick = { undo() },
                    enabled = canUndo,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Undo,
                        contentDescription = "undo",
                    )
                }

                IconButton(
                    onClick = { redo() },
                    enabled = canRedo,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Redo,
                        contentDescription = "redo",
                    )
                }

                IconButton(
                    onClick = { appWindowManager.hideBubbleWindow() },
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = "cancel",
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = small2X)
                    .padding(top = small2X, bottom = 85.dp)
                    .clip(mediumRoundedCornerShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            CustomTextField(
                modifier =
                    Modifier
                        .fillMaxSize(),
                shape = RoundedCornerShape(tiny),
                value = textValue,
                onValueChange = { updateTextWithHistory(it) },
                textStyle = pasteTextStyle,
            )
        }
    }
}
