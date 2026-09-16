package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.DesktopIconColorExtractor
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.UpdatePasteItemHelper
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.LocalSearchWindowInfoState
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.PasteTooltipAreaView
import com.crosspaste.ui.base.SidePasteTypeIconView
import com.crosspaste.ui.base.darkSideBarColors
import com.crosspaste.ui.base.lightSideBarColors
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.DesktopAppUIFont
import com.crosspaste.utils.ColorAccessibility.getBestTextColor
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.RelativeTime
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PasteDataScope.SidePasteTitleView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val desktopIconColorExtractor = koinInject<DesktopIconColorExtractor>()
    val updatePasteItemHelper = koinInject<UpdatePasteItemHelper>()
    val notificationManager = koinInject<NotificationManager>()

    val pasteItem = getPasteItem(PasteItem::class)

    // updateName CAS-compares the complete stored item (extraInfo included), and
    // same-hash metadata writes do happen behind the editor — OpenGraphService writes
    // extraInfo[TITLE] on URL pastes. The committer must therefore read the latest
    // data at commit time instead of closing over a snapshot.
    val currentPasteData by rememberUpdatedState(pasteData)
    val currentPasteItem by rememberUpdatedState(pasteItem)

    val sideTitleHeight = LocalDesktopAppSizeValueState.current.sideTitleHeight
    val showWindow = LocalSearchWindowInfoState.current.show
    val isCurrentThemeDark = LocalThemeState.current.isCurrentThemeDark

    val scope = rememberCoroutineScope()

    val type = remember(pasteData.id) { pasteData.getType() }
    var background by remember(type, isCurrentThemeDark) {
        mutableStateOf(
            if (isCurrentThemeDark) {
                darkSideBarColors.getColor(type)
            } else {
                lightSideBarColors.getColor(type)
            },
        )
    }

    val onBackground =
        remember(background) {
            background.getBestTextColor()
        }

    var relativeTime by remember(pasteData.id) {
        mutableStateOf<RelativeTime?>(null)
    }

    val initialName = pasteItem.getUserEditName() ?: copywriter.getText(pasteData.getTypeName())
    var pasteboardName by remember(pasteData.id, initialName) {
        mutableStateOf(initialName)
    }

    var isEditing by remember(pasteData.id) { mutableStateOf(false) }

    var editedTextValue by remember(pasteData.id) {
        mutableStateOf(TextFieldValue(pasteboardName))
    }

    // While not editing, the field mirrors the stored name so a rename elsewhere or a
    // locale change shows through. Once editing starts the user's typing owns the field
    // until the edit commits or reverts.
    LaunchedEffect(pasteboardName, isEditing) {
        if (!isEditing) {
            editedTextValue =
                TextFieldValue(
                    text = pasteboardName,
                    selection = TextRange(pasteboardName.length),
                )
        }
    }

    var titleOverflowed by remember(pasteData.id) { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isCurrentThemeDark, pasteData.source) {
        pasteData.source?.let {
            desktopIconColorExtractor.getBackgroundColor(it, pasteData.appInstanceId)?.let { color ->
                background = color
            }
        }
    }

    LaunchedEffect(pasteData.id, showWindow) {
        relativeTime =
            if (showWindow) {
                DateUtils.getRelativeTime(pasteData.createTime)
            } else {
                null
            }
    }

    PasteTooltipAreaView(
        text = editedTextValue.text,
        enabled = titleOverflowed && !isEditing,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(sideTitleHeight)
                    .background(background)
                    .padding(start = medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier.wrapContentSize().width(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    if (isEditing) {
                        val customTextSelectionColors =
                            remember(onBackground) {
                                TextSelectionColors(
                                    handleColor = onBackground,
                                    backgroundColor = onBackground.copy(alpha = 0.3f),
                                )
                            }

                        var hadFocus by remember(pasteData.id) { mutableStateOf(false) }

                        val committer =
                            remember(pasteData.id) {
                                TitleEditCommitter(scope) { name ->
                                    updatePasteItemHelper
                                        .updateName(currentPasteData, name, currentPasteItem)
                                        .map { }
                                }
                            }

                        val completeEditing: () -> Unit = {
                            committer.commit(
                                text = editedTextValue.text,
                                onRevert = {
                                    // Revert to the original name if empty
                                    editedTextValue =
                                        TextFieldValue(
                                            text = pasteboardName,
                                            selection = TextRange(pasteboardName.length),
                                        )
                                    isEditing = false
                                },
                                onSaved = { savedName ->
                                    pasteboardName = savedName
                                    editedTextValue =
                                        TextFieldValue(
                                            text = savedName,
                                            selection = TextRange(savedName.length),
                                        )
                                    isEditing = false
                                },
                                onFailure = {
                                    notificationManager.sendNotification(
                                        title = { copywriter.getText("save_failed") },
                                        messageType = MessageType.Error,
                                    )
                                },
                            )
                        }

                        CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                            BasicTextField(
                                value = editedTextValue,
                                onValueChange = { editedTextValue = it },
                                modifier =
                                    Modifier
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { state ->
                                            if (state.isFocused) {
                                                hadFocus = true
                                            } else if (hadFocus) {
                                                hadFocus = false
                                                // Losing focus (e.g. clicking elsewhere) commits the edit
                                                completeEditing()
                                            }
                                        }.weight(1f, fill = false),
                                textStyle =
                                    DesktopAppUIFont.sidePasteTitleTextStyle.copy(
                                        color = onBackground,
                                    ),
                                cursorBrush = SolidColor(onBackground),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions =
                                    KeyboardActions(
                                        onDone = { completeEditing() },
                                    ),
                            )
                        }

                        // Autofocus and select all when entering edit mode
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                            // Set selection to cover the entire text length
                            editedTextValue =
                                editedTextValue.copy(
                                    selection = TextRange(0, editedTextValue.text.length),
                                )
                        }
                    } else {
                        Text(
                            modifier =
                                Modifier.clickable {
                                    isEditing = true
                                },
                            text = editedTextValue.text,
                            style =
                                DesktopAppUIFont.sidePasteTitleTextStyle.copy(
                                    color = onBackground,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { titleOverflowed = it.hasVisualOverflow },
                        )
                    }
                }

                relativeTime?.let {
                    Text(
                        text = copywriter.getText(it.unit, it.value?.toString() ?: ""),
                        style =
                            DesktopAppUIFont.sidePasteTimeTextStyle.copy(
                                color = onBackground,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SidePasteTypeIconView(
                modifier = Modifier.fillMaxHeight().wrapContentWidth(),
            )
        }
    }
}
