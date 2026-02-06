package com.crosspaste.ui.search.side

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults.iconButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Search
import com.crosspaste.app.DesktopAppLaunch
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.WindowTrigger
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalDesktopAppSizeValueState
import com.crosspaste.ui.LocalSearchWindowInfoState
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.Settings
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.GeneralIconButton
import com.crosspaste.ui.base.TutorialButton
import com.crosspaste.ui.base.settings
import com.crosspaste.ui.model.FocusedElement
import com.crosspaste.ui.model.PasteSearchViewModel
import com.crosspaste.ui.model.PasteSelectionViewModel
import com.crosspaste.ui.model.RequestSearchInputFocus
import com.crosspaste.ui.search.QuickPasteView
import com.crosspaste.ui.search.SearchTrailingIcon
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SideSearchInputView() {
    val appLaunch = koinInject<DesktopAppLaunch>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val navigationManager = koinInject<NavigationManager>()
    val pasteSearchViewModel = koinInject<PasteSearchViewModel>()
    val pasteSelectionViewModel = koinInject<PasteSelectionViewModel>()

    val appSizeValue = LocalDesktopAppSizeValueState.current
    val searchWindowInfo = LocalSearchWindowInfoState.current

    val inputSearch by pasteSearchViewModel.inputSearch.collectAsState()

    val prevAppName by appWindowManager.getPrevAppName().collectAsState(null)

    val config by configManager.config.collectAsState()

    val firstLaunchCompleted by appLaunch.firstLaunchCompleted.collectAsState()

    val searchFocusRequester = remember { FocusRequester() }

    val scope = rememberCoroutineScope()

    LaunchedEffect(searchWindowInfo.show) {
        if (searchWindowInfo.show) {
            pasteSearchViewModel.resetSearch()
        }
    }

    LaunchedEffect(Unit) {
        pasteSelectionViewModel.uiEvent.collect { event ->
            when (event) {
                RequestSearchInputFocus -> {
                    if (searchFocusRequester.requestFocus()) {
                        pasteSelectionViewModel.setFocusedElement(
                            FocusedElement.SEARCH_INPUT,
                        )
                    }
                }
            }
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(appSizeValue.sideSearchTopBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = xxLarge),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            prevAppName?.let {
                Text(
                    text = "${copywriter.getText("paste_to")} $it",
                    style =
                        AppUIFont.tipsTextStyle.copy(
                            color =
                                MaterialTheme.colorScheme.contentColorFor(
                                    AppUIColors.generalBackground,
                                ),
                        ),
                )
                Spacer(modifier = Modifier.width(tiny))
                QuickPasteView()
            }

            if (firstLaunchCompleted && config.showTutorial) {
                if (prevAppName != null) {
                    Spacer(modifier = Modifier.width(large))
                }
                TutorialButton()
            }
        }

        CustomTextField(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = small2X)
                    .widthIn(min = 600.dp, max = 800.dp)
                    .clip(tinyRoundedCornerShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .focusRequester(searchFocusRequester)
                    .onFocusEvent {
                        if (it.isFocused) {
                            pasteSelectionViewModel.setFocusedElement(
                                FocusedElement.SEARCH_INPUT,
                            )
                        }
                    }.onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown) {
                            pasteSelectionViewModel.requestPasteListFocus()
                            true
                        } else {
                            false
                        }
                    },
            value = inputSearch,
            leadingIcon = {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Search,
                    contentDescription = "search",
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingIcon = {
                SearchTrailingIcon()
            },
            onValueChange = { pasteSearchViewModel.updateInputSearch(it) },
            keyboardOptions = KeyboardOptions.Default.copy(autoCorrectEnabled = true),
            visualTransformation = VisualTransformation.None,
            placeholder = {
                Text(
                    modifier = Modifier.wrapContentSize(),
                    text = copywriter.getText("search_pasteboard"),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            isError = false,
            singleLine = true,
            contentPadding = PaddingValues(0.dp),
            colors =
                TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = Color.Transparent,
                    errorTextColor = MaterialTheme.colorScheme.error,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    errorCursorColor = Color.Red,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.secondary,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = MaterialTheme.colorScheme.error,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    disabledPlaceholderColor = Color.Transparent,
                    errorPlaceholderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                ),
            textStyle = MaterialTheme.typography.bodyMedium,
        )

        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = xxLarge),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(small))

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                SearchTagsView()
            }

            Spacer(modifier = Modifier.width(small))

            GeneralIconButton(
                painter = settings(),
                desc = "settings",
                colors =
                    iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                shape = tiny2XRoundedCornerShape,
            ) {
                scope.launch {
                    navigationManager.navigateAndClearStack(Settings)
                    appWindowManager.showMainWindow(WindowTrigger.MENU)
                    appWindowManager.hideSearchWindow()
                }
            }
        }
    }
}
