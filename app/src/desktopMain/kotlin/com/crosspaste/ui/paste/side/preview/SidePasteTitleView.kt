package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.app.AppControl
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.DesktopIconColorExtractor
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.UpdatePasteItemHelper
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.LocalSearchWindowInfoState
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.PasteTooltipIconView
import com.crosspaste.ui.base.SidePasteTypeIconView
import com.crosspaste.ui.base.darkSideBarColors
import com.crosspaste.ui.base.favorite
import com.crosspaste.ui.base.lightSideBarColors
import com.crosspaste.ui.base.noFavorite
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.DesktopAppUIFont
import com.crosspaste.utils.ColorUtils.getBestTextColor
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.RelativeTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.SidePasteTitleView() {
    val appControl = koinInject<AppControl>()
    val copywriter = koinInject<GlobalCopywriter>()
    val desktopIconColorExtractor = koinInject<DesktopIconColorExtractor>()
    val pasteDao = koinInject<PasteDao>()
    val updatePasteItemHelper = koinInject<UpdatePasteItemHelper>()

    val pasteItem = getPasteItem(PasteItem::class)

    val sideTitleHeight = LocalDesktopAppSizeValueState.current.sideTitleHeight
    val showWindow = LocalSearchWindowInfoState.current.show
    val isCurrentThemeDark = LocalThemeState.current.isCurrentThemeDark

    val scope = rememberCoroutineScope()

    val type by remember(pasteData.id) { mutableStateOf(pasteData.getType()) }
    var background by remember(type, isCurrentThemeDark) {
        mutableStateOf(
            if (isCurrentThemeDark) {
                darkSideBarColors.getColor(type)
            } else {
                lightSideBarColors.getColor(type)
            },
        )
    }

    var favorite by remember(pasteData.id) {
        mutableStateOf(pasteData.favorite)
    }

    val onBackground by remember(background) {
        mutableStateOf(background.getBestTextColor())
    }

    var relativeTime by remember(pasteData.id) {
        mutableStateOf<RelativeTime?>(null)
    }

    var pasteboardName by remember(pasteData.id) {
        mutableStateOf(pasteItem.getUserEditName() ?: copywriter.getText(pasteData.getTypeName()))
    }

    var editedTextValue by remember { mutableStateOf(TextFieldValue(pasteboardName)) }

    var isEditing by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isCurrentThemeDark, pasteData.source) {
        pasteData.source?.let {
            desktopIconColorExtractor.getBackgroundColor(it)?.let { color -> background = color }
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

                    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                        BasicTextField(
                            value = editedTextValue,
                            onValueChange = { editedTextValue = it },
                            modifier =
                                Modifier
                                    .focusRequester(focusRequester)
                                    .weight(1f, fill = false),
                            textStyle =
                                DesktopAppUIFont.sidePasteTitleTextStyle.copy(
                                    color = onBackground,
                                ),
                            cursorBrush = SolidColor(onBackground),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        if (editedTextValue.text.isBlank()) {
                                            // Revert to the original name if empty
                                            editedTextValue =
                                                TextFieldValue(
                                                    text = pasteboardName,
                                                    selection = TextRange(pasteboardName.length),
                                                )
                                            isEditing = false
                                        } else {
                                            // Proceed with update if not empty
                                            scope.launch {
                                                updatePasteItemHelper.updateName(
                                                    pasteData,
                                                    editedTextValue.text,
                                                    pasteItem,
                                                )
                                                isEditing = false
                                            }
                                        }
                                    },
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
                    )
                }
                Spacer(Modifier.width(tiny4X))
                PasteTooltipIconView(
                    painter = if (favorite) favorite() else noFavorite(),
                    contentDescription = "Favorite",
                    tint = onBackground,
                    hover = background.copy(alpha = 0.3f),
                    text = copywriter.getText(if (favorite) "remove_from_favorites" else "favorite"),
                ) {
                    if (appControl.isFavoriteEnabled()) {
                        scope.launch {
                            pasteDao.setFavorite(pasteData.id, !favorite)
                            favorite = !favorite
                        }
                    }
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
            tint = onBackground,
        )
    }
}
