package com.crosspaste.ui.paste.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Format_bold
import com.composables.icons.materialsymbols.rounded.Format_italic
import com.composables.icons.materialsymbols.rounded.Format_strikethrough
import com.composables.icons.materialsymbols.rounded.Format_underlined
import com.composables.icons.materialsymbols.rounded.Redo
import com.composables.icons.materialsymbols.rounded.Save
import com.composables.icons.materialsymbols.rounded.Undo
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.UpdatePasteItemHelper
import com.crosspaste.platform.Platform
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.InnerScaffold
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.utils.getColorUtils
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val MAX_UNDO_STACK_SIZE = 50

@OptIn(ExperimentalMaterial3ExpressiveApi::class, FlowPreview::class)
@Composable
fun PasteDataScope.PasteHtmlEditContentView() {
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val notificationManager = koinInject<NotificationManager>()
    val updatePasteItemHelper = koinInject<UpdatePasteItemHelper>()
    val platform = koinInject<Platform>()

    val scope = rememberCoroutineScope()
    val isMac = remember { platform.isMacos() }

    val htmlPasteItem = getPasteItem(HtmlPasteItem::class)
    val originalHtml = remember(pasteData.id, pasteData.hash) { htmlPasteItem.html }
    val richTextState = rememberRichTextState()

    val colorUtils = getColorUtils()
    val backgroundColor by remember(pasteData.id) {
        mutableStateOf(Color(htmlPasteItem.getBackgroundColor()))
    }
    val htmlBackground =
        if (backgroundColor == Color.Transparent) {
            MaterialTheme.colorScheme.background
        } else {
            backgroundColor
        }
    val isDark by remember(pasteData.id) { mutableStateOf(colorUtils.isDarkColor(htmlBackground)) }
    val richTextColor =
        if (isDark == LocalThemeState.current.isCurrentThemeDark) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.background
        }

    // Undo/Redo stacks
    val undoStack = remember(pasteData.id, pasteData.hash) { mutableStateListOf<String>() }
    val redoStack = remember(pasteData.id, pasteData.hash) { mutableStateListOf<String>() }
    var isUndoRedoAction by remember { mutableStateOf(false) }
    var currentHtml by remember(pasteData.id, pasteData.hash) { mutableStateOf(originalHtml) }
    var hasChanges by remember(pasteData.id, pasteData.hash) { mutableStateOf(false) }

    val canUndo = undoStack.isNotEmpty()
    val canRedo = redoStack.isNotEmpty()

    // Load HTML into the rich text editor and scroll to the top
    LaunchedEffect(pasteData.id, pasteData.hash) {
        richTextState.setHtml(originalHtml)
        richTextState.selection = TextRange.Zero
        currentHtml = originalHtml
    }

    // Track changes via snapshotFlow on the rich text state
    LaunchedEffect(pasteData.id, pasteData.hash) {
        snapshotFlow { richTextState.toHtml() }
            .debounce(300)
            .distinctUntilChanged()
            .collect { newHtml ->
                if (isUndoRedoAction) {
                    isUndoRedoAction = false
                    return@collect
                }
                if (newHtml != currentHtml) {
                    undoStack.add(currentHtml)
                    if (undoStack.size > MAX_UNDO_STACK_SIZE) {
                        undoStack.removeAt(0)
                    }
                    redoStack.clear()
                    currentHtml = newHtml
                    hasChanges = newHtml != originalHtml
                }
            }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            isUndoRedoAction = true
            val previous = undoStack.removeLast()
            redoStack.add(currentHtml)
            currentHtml = previous
            hasChanges = previous != originalHtml
            scope.launch {
                richTextState.setHtml(previous)
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            isUndoRedoAction = true
            val next = redoStack.removeLast()
            undoStack.add(currentHtml)
            currentHtml = next
            hasChanges = next != originalHtml
            scope.launch {
                richTextState.setHtml(next)
            }
        }
    }

    fun save() {
        if (hasChanges) {
            scope.launch {
                val newHtml = richTextState.toHtml()
                updatePasteItemHelper
                    .updateHtml(
                        pasteData,
                        newHtml,
                        htmlPasteItem.getBackgroundColor(),
                        htmlPasteItem,
                    ).onSuccess {
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
                    onClick = {
                        richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    },
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Format_bold,
                        contentDescription = "bold",
                    )
                }

                IconButton(
                    onClick = {
                        richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    },
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Format_italic,
                        contentDescription = "italic",
                    )
                }

                IconButton(
                    onClick = {
                        richTextState.toggleSpanStyle(
                            SpanStyle(textDecoration = TextDecoration.Underline),
                        )
                    },
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Format_underlined,
                        contentDescription = "underline",
                    )
                }

                IconButton(
                    onClick = {
                        richTextState.toggleSpanStyle(
                            SpanStyle(textDecoration = TextDecoration.LineThrough),
                        )
                    },
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Format_strikethrough,
                        contentDescription = "strikethrough",
                    )
                }

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
        RichTextEditor(
            state = richTextState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(htmlBackground),
            textStyle = TextStyle(color = richTextColor),
            contentPadding = PaddingValues(bottom = huge),
        )
    }
}
